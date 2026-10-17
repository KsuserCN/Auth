package cn.ksuser.auth.core.session

import cn.ksuser.auth.core.network.*
import cn.ksuser.auth.data.model.UserProfile
import cn.ksuser.auth.data.repository.AuthRepository
import com.google.gson.Gson
import kotlinx.coroutines.*
import okhttp3.Cookie
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.Base64
import java.util.concurrent.TimeUnit

class SessionSafetyTest {
    private val server = MockWebServer()
    private val gson = Gson()
    private val storage = MemorySessionStorage()
    private val cookies = CookieStore.inMemory(gson)
    private lateinit var session: SessionRepository
    private lateinit var auth: AuthRepository
    private val oldUser = UserProfile("old-user", "old", "old@example.test")
    private val oldToken = token("old")
    private val newToken = token("new")

    @Before fun setup() {
        server.start()
        storage.setAccessToken(oldToken)
        storage.setProfile(oldUser)
        cookies.saveFromResponse(server.url("/"), listOf(Cookie.Builder().name("refreshToken").value("old-refresh")
            .hostOnlyDomain(server.hostName).path("/").expiresAt(System.currentTimeMillis() + 600_000).build()))
        session = SessionRepository(server.url("/"), storage, cookies, gson, ::api)
        auth = AuthRepository(api(storage, cookies), session, gson)
    }
    @After fun cleanup() { server.shutdown() }
    private fun api(store: SessionTokenStore, jar: CookieStore): KsuserApiService {
        val client = OkHttpClient.Builder().cookieJar(PersistentCookieJar(jar)).addInterceptor { chain ->
            val request = chain.request().newBuilder()
            store.getAccessToken()?.let { request.header("Authorization", "Bearer $it") }
            chain.proceed(request.build())
        }.build()
        return Retrofit.Builder().baseUrl(server.url("/")).client(client).addConverterFactory(GsonConverterFactory.create(gson))
            .build().create(KsuserApiService::class.java)
    }
    private fun response(body: String, status: Int = 200) = MockResponse().setResponseCode(status)
        .setHeader("Content-Type", "application/json").setBody(body)
    private fun loginResponse() = response("""{"code":200,"data":{"accessToken":"$newToken"}}""")
        .addHeader("Set-Cookie", "refreshToken=new-refresh; Path=/; Max-Age=600")
    private fun profileResponse() = response("""{"code":200,"data":{"uuid":"new-user","username":"new","email":null,"hasPassword":false}}""")

    @Test fun failedProfileReadDoesNotReplaceExistingCredentials() = runBlocking {
        server.enqueue(loginResponse())
        auth.passwordLogin("new@example.test", "password")
        assertEquals(oldToken, storage.getAccessToken())
        assertEquals("old-refresh", cookies.findCookieValue(server.url("/"), "refreshToken"))
        server.enqueue(response("""{"code":503,"errorCode":"SERVER_ERROR"}""", 503))
        assertTrue(runCatching { auth.completeAuthentication() }.isFailure)
        assertEquals(oldToken, storage.getAccessToken())
        assertEquals(oldUser, session.cachedUser())
        assertEquals("old-refresh", cookies.findCookieValue(server.url("/"), "refreshToken"))
        server.enqueue(response("""{"code":200,"data":{"uuid":"old-user","username":"old","email":"old@example.test"}}"""))
        assertEquals(oldUser, auth.getCurrentUser())
        server.takeRequest(); server.takeRequest()
        assertEquals("Bearer $oldToken", server.takeRequest().getHeader("Authorization"))
    }
    @Test fun successfulProfileReadCommitsNewTokenCookiesAndProfileTogether() = runBlocking {
        server.enqueue(loginResponse()); auth.passwordLogin("new@example.test", "password")
        server.enqueue(profileResponse()); val profile = auth.completeAuthentication()
        assertNull(profile.email)
        assertEquals(false, profile.hasPassword)
        assertEquals(newToken, storage.getAccessToken())
        assertEquals("new-refresh", cookies.findCookieValue(server.url("/"), "refreshToken"))
        assertEquals(profile, session.cachedUser())
    }
    @Test fun cancellingAuthenticationPreventsLateProfileCommit() = runBlocking {
        server.enqueue(loginResponse()); auth.passwordLogin("new@example.test", "password")
        server.takeRequest()
        server.enqueue(profileResponse().setBodyDelay(200, TimeUnit.MILLISECONDS))
        val result = async(Dispatchers.Default) { runCatching { auth.completeAuthentication() } }
        assertNotNull(server.takeRequest(2, TimeUnit.SECONDS))
        auth.cancelAuthentication()
        assertTrue(result.await().isFailure)
        assertEquals(oldToken, storage.getAccessToken())
        assertEquals(oldUser, session.cachedUser())
    }
    @Test fun failedTransferKeepsOriginalAccountAndRefreshCookie() = runBlocking {
        server.enqueue(loginResponse()); auth.exchangeSessionTransferForMobile("transfer-code")
        server.enqueue(response("""{"code":500,"errorCode":"SERVER_ERROR"}""", 500))
        assertTrue(runCatching { auth.completeAuthentication() }.isFailure)
        assertEquals(oldToken, storage.getAccessToken())
        assertEquals("old-refresh", cookies.findCookieValue(server.url("/"), "refreshToken"))
        assertEquals(oldUser, session.cachedUser())
    }
    @Test fun refreshServerAndCsrfFailuresKeepSession() {
        for (status in listOf(403, 500, 503)) {
            server.enqueue(response("""{"code":$status,"errorCode":"SERVER_ERROR"}""", status))
            assertNull(session.refreshAccessTokenBlocking(oldToken))
            assertEquals(oldToken, storage.getAccessToken())
            assertEquals(oldUser, session.cachedUser())
        }
    }
    @Test fun explicitUnauthorizedRefreshClearsSession() {
        server.enqueue(response("""{"code":401,"errorCode":"UNAUTHENTICATED"}""", 401))
        assertNull(session.refreshAccessTokenBlocking(oldToken))
        assertFalse(session.hasCredentials())
        assertNull(session.cachedUser())
    }
    @Test fun concurrentUnauthorizedRequestsShareSingleRefresh() = runBlocking {
        server.enqueue(loginResponse())
        val results = (1..8).map { async(Dispatchers.IO) { session.refreshAccessTokenBlocking(oldToken) } }.awaitAll()
        assertTrue(results.all { it == newToken })
        assertEquals(1, server.requestCount)
    }
    @Test fun logoutCannotBeUndoneByLateRefreshResponse() = runBlocking {
        server.enqueue(loginResponse().setBodyDelay(200, TimeUnit.MILLISECONDS))
        val request = async(Dispatchers.IO) { session.refreshAccessTokenBlocking(oldToken) }
        assertNotNull(server.takeRequest(2, TimeUnit.SECONDS))
        session.clearSession()
        assertNull(request.await())
        assertFalse(session.hasCredentials())
        assertTrue(cookies.snapshot().isEmpty())
        assertNull(session.cachedUser())
    }
    @Test fun failedLogoutAllDoesNotReportLocalLogout() = runBlocking {
        server.enqueue(response("""{"code":503,"errorCode":"SERVER_ERROR"}""", 503))
        assertTrue(runCatching { auth.logoutAll() }.isFailure)
        assertTrue(session.hasCredentials())
        assertEquals(oldUser, session.cachedUser())
    }
    @Test fun lateUnauthorizedRequestCannotReplayAgainstAnotherAccount() {
        val previousGeneration = session.currentGeneration()
        val replacement = session.stage()
        replacement.storage.setAccessToken(newToken)
        val newUser = UserProfile("new-user", "new", null)
        session.commit(replacement, newUser)
        assertNull(session.refreshAccessTokenBlocking(oldToken, previousGeneration))
        assertEquals(0, server.requestCount)
        assertEquals(newUser, session.cachedUser())
        assertEquals(newToken, session.currentAccessToken())
    }
    @Test fun sameAccountRefreshDoesNotInvalidateProfileRead() {
        val generation = session.currentGeneration()
        server.enqueue(loginResponse())
        assertEquals(newToken, session.refreshAccessTokenBlocking(oldToken, generation))
        assertTrue(session.cacheUser(oldUser, generation))
        assertEquals(oldUser, session.cachedUser())
    }
    @Test fun lateLogoutCannotClearNewlyCommittedAccountOrCookies() = runBlocking {
        server.enqueue(response("""{"code":200}""").addHeader("Set-Cookie", "refreshToken=; Max-Age=0; Path=/")
            .setHeadersDelay(200, TimeUnit.MILLISECONDS))
        val logout = async(Dispatchers.IO) { auth.logout() }
        assertNotNull(server.takeRequest(2, TimeUnit.SECONDS))
        assertFalse(session.hasCredentials())
        val next = session.stage()
        next.storage.setAccessToken(newToken)
        next.cookies.saveFromResponse(server.url("/"), listOf(Cookie.Builder().name("refreshToken").value("new-refresh")
            .hostOnlyDomain(server.hostName).path("/").expiresAt(System.currentTimeMillis()+600_000).build()))
        val newUser = UserProfile("new-user", "new", null)
        session.commit(next, newUser)
        logout.await()
        assertEquals(newToken, session.currentAccessToken())
        assertEquals(newUser, session.cachedUser())
        assertEquals("new-refresh", cookies.findCookieValue(server.url("/"), "refreshToken"))
    }
    @Test fun logoutAllRefreshesExpiredAccessOnceAndThenClearsSession() = runBlocking {
        server.enqueue(response("""{"code":401,"errorCode":"UNAUTHENTICATED"}""",401))
        server.enqueue(loginResponse())
        server.enqueue(response("""{"code":200}"""))
        auth.logoutAll()
        assertFalse(session.hasCredentials())
        assertEquals("/auth/logout/all",server.takeRequest().path)
        assertEquals("/auth/refresh",server.takeRequest().path)
        val replay=server.takeRequest()
        assertEquals("/auth/logout/all",replay.path)
        assertEquals("Bearer $newToken",replay.getHeader("Authorization"))
    }
    @Test fun failedLogoutAllAfterRefreshRetainsRotatedCredentials() = runBlocking {
        server.enqueue(response("""{"code":401,"errorCode":"UNAUTHENTICATED"}""", 401))
        server.enqueue(loginResponse())
        server.enqueue(response("""{"code":503,"errorCode":"SERVER_ERROR"}""", 503))
        assertTrue(runCatching { auth.logoutAll() }.isFailure)
        assertEquals(newToken, session.currentAccessToken())
        assertEquals("new-refresh", cookies.findCookieValue(server.url("/"), "refreshToken"))
        assertEquals(oldUser, session.cachedUser())
        assertEquals(3, server.requestCount)
    }
    @Test fun lateResponseCookiesCannotOverwriteNewAccountCookieJar() {
        val previousGeneration=session.currentGeneration()
        val replacement=session.stage()
        replacement.storage.setAccessToken(newToken)
        val cookie=Cookie.Builder().name("refreshToken").value("new-refresh").hostOnlyDomain(server.hostName)
            .path("/").expiresAt(System.currentTimeMillis()+600_000).build()
        replacement.cookies.saveFromResponse(server.url("/"),listOf(cookie))
        session.commit(replacement,UserProfile("new-user","new",null))
        assertFalse(session.acceptResponseCookies(previousGeneration,server.url("/"),listOf(Cookie.Builder().name("refreshToken").value("old-response").hostOnlyDomain(server.hostName).path("/").build())))
        assertEquals("new-refresh",cookies.findCookieValue(server.url("/"),"refreshToken"))
    }
    private fun token(subject: String): String {
        val content = """{"sub":"$subject","exp":4102444800}"""
        return "e30." + Base64.getUrlEncoder().withoutPadding().encodeToString(content.toByteArray()) + ".signature"
    }
}
