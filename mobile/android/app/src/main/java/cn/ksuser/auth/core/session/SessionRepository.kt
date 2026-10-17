package cn.ksuser.auth.core.session

import cn.ksuser.auth.core.network.ApiException
import cn.ksuser.auth.core.network.CookieStore
import cn.ksuser.auth.core.network.KsuserApiService
import cn.ksuser.auth.core.network.executeEnvelope
import cn.ksuser.auth.core.network.requireCode
import cn.ksuser.auth.data.model.UserProfile
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.Cookie

/** Authentication and refresh receive isolated cookie jars until their result can be committed. */
class AuthenticationSession(
    val api: KsuserApiService,
    val storage: MemorySessionStorage,
    val cookies: CookieStore,
    internal val generation: Long,
)

class SessionRepository(
    private val apiBaseUrl: HttpUrl,
    private val sessionStorage: SessionTokenStore,
    private val cookieStore: CookieStore,
    private val gson: Gson,
    private val createApi: (SessionTokenStore, CookieStore) -> KsuserApiService,
) {
    private val refreshLock = Any()
    private val stateLock = Any()
    private var generation = 0L
    private val _cachedProfile = MutableStateFlow(sessionStorage.getProfile())
    val cachedProfile = _cachedProfile.asStateFlow()

    fun cachedUser(): UserProfile? = _cachedProfile.value
    fun currentGeneration(): Long = synchronized(stateLock) { generation }
    fun acceptResponseCookies(expectedGeneration: Long, url: HttpUrl, values: List<Cookie>): Boolean = synchronized(stateLock) {
        if (expectedGeneration != generation) return@synchronized false
        if (cn.ksuser.auth.core.network.sameOrigin(url, apiBaseUrl)) cookieStore.saveFromResponse(url, values)
        true
    }
    fun hasCredentials(): Boolean = !currentAccessToken().isNullOrBlank() || hasRefreshToken()
    fun cacheUser(user: UserProfile, expectedGeneration: Long = currentGeneration()): Boolean = synchronized(stateLock) {
        if (expectedGeneration != generation || !hasCredentials()) return@synchronized false
        sessionStorage.setProfile(user)
        _cachedProfile.value = user
        true
    }

    fun stage(copyCurrent: Boolean = false): AuthenticationSession = synchronized(stateLock) {
        val storage = MemorySessionStorage()
        val cookies = CookieStore.inMemory(gson)
        if (copyCurrent) {
            sessionStorage.getAccessToken()?.let(storage::setAccessToken)
            cookies.replaceAll(cookieStore.snapshot())
        }
        AuthenticationSession(createApi(storage, cookies), storage, cookies, generation)
    }

    fun commit(stage: AuthenticationSession, user: UserProfile) = synchronized(stateLock) {
        check(stage.generation == generation) { "Authentication was cancelled or the account changed" }
        val token = stage.storage.getAccessToken()?.takeIf { it.isNotBlank() }
            ?: error("Missing access token")
        replaceCredentials(token, stage.cookies, user)
        generation++
    }

    private fun replaceCredentials(token: String, cookies: CookieStore, user: UserProfile?) {
        val snapshot = cookies.snapshot()
        if (sessionStorage is SecureSessionStorage) {
            sessionStorage.replace(token, gson.toJson(snapshot), user)
        } else {
            sessionStorage.setAccessToken(token)
            user?.let(sessionStorage::setProfile)
        }
        cookieStore.replaceAll(snapshot)
        _cachedProfile.value = user
    }

    suspend fun bootstrap(): String? = withContext(Dispatchers.IO) {
        val existing = currentAccessToken()
        if (!existing.isNullOrBlank() && !AccessTokenInspector.isExpiredOrBlank(existing)) return@withContext existing
        if (!hasRefreshToken()) {
            clearSession()
            return@withContext null
        }
        refreshAccessTokenBlocking(existing)
    }

    suspend fun ensureCsrfToken() {
        if (cookieStore.hasCookie(apiBaseUrl, "XSRF-TOKEN")) return
        val staged = stage(copyCurrent = true)
        requireCode(executeEnvelope(gson) { staged.api.getCsrfToken() }, 200)
        synchronized(stateLock) {
            if (generation == staged.generation) cookieStore.replaceAll(staged.cookies.snapshot())
        }
    }

    fun refreshAccessTokenBlocking(requestToken: String?, expectedGeneration: Long = currentGeneration()): String? = synchronized(refreshLock) {
        val staged = synchronized(stateLock) {
            if (expectedGeneration != generation) return null
            val latest = currentAccessToken()
            if (!requestToken.isNullOrBlank() && !latest.isNullOrBlank() && requestToken != latest &&
                !AccessTokenInspector.isExpiredOrBlank(latest)) return latest
            if (!hasRefreshToken()) return null
            stage(copyCurrent = true)
        }
        runBlocking {
            try {
                val response = executeEnvelope(gson) { staged.api.refresh() }
                requireCode(response, 200)
                val token = response.data?.accessToken?.takeIf { it.isNotBlank() }
                    ?: throw ApiException(500, "Invalid refresh response", "INVALID_RESPONSE")
                synchronized(stateLock) {
                    if (generation != staged.generation) return@synchronized null
                    replaceCredentials(token, staged.cookies, cachedUser())
                    token
                }
            } catch (error: ApiException) {
                if (error.statusCode == 401) synchronized(stateLock) {
                    if (generation == staged.generation) clearSession()
                }
                null
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // A temporary network/CSRF/service failure is not evidence that credentials were revoked.
                null
            }
        }
    }

    fun currentAccessToken(): String? = sessionStorage.getAccessToken()
    fun hasRefreshToken(): Boolean = cookieStore.hasCookie(apiBaseUrl, "refreshToken")
    fun clearSession() = synchronized(stateLock) {
        generation++
        sessionStorage.clear()
        cookieStore.clear()
        _cachedProfile.value = null
    }
    fun clearSessionIfGeneration(expected: Long): Boolean = synchronized(stateLock) {
        if (generation != expected) return@synchronized false
        clearSession()
        true
    }
}
