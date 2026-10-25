package cn.ksuser.auth.data

import android.content.Context
import androidx.credentials.CredentialManager
import cn.ksuser.auth.core.env.EnvironmentProvider
import cn.ksuser.auth.core.network.*
import cn.ksuser.auth.core.passkey.PasskeyManager
import cn.ksuser.auth.core.qq.QqLoginManager
import cn.ksuser.auth.core.session.SecureSessionStorage
import cn.ksuser.auth.core.session.SessionRepository
import cn.ksuser.auth.core.session.SessionTokenStore
import cn.ksuser.auth.core.settings.AppPreferences
import cn.ksuser.auth.core.settings.L10n
import cn.ksuser.auth.data.authorization.MobileAuthorizationApi
import cn.ksuser.auth.data.authorization.MobileAuthorizationRepository
import cn.ksuser.auth.data.authorization.NativeAuthorizationApi
import cn.ksuser.auth.data.authorization.NativeAuthorizationRepository
import cn.ksuser.auth.data.authorization.NativeAuthorizationStateStore
import cn.ksuser.auth.data.repository.*
import com.google.gson.Gson
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val environment = EnvironmentProvider.current
    private val gson = Gson()
    val preferences = AppPreferences(appContext)
    val serviceAvailability = ServiceAvailabilityMonitor()
    private val navigation = MutableStateFlow<String?>(null)
    val navigationRequest = navigation.asStateFlow()
    fun requestNavigation(destination: String) { if (destination in setOf("scan", "security", "sessions", "profile")) navigation.value = destination }
    fun consumeNavigation() { navigation.value = null }
    private val origin = environment.apiBaseUrl.toHttpUrl()
    private val sessionStorage = SecureSessionStorage(appContext)
    private val cookieStore = CookieStore(appContext, gson, sessionStorage.preferences)

    private fun client(storage: SessionTokenStore, cookies: CookieStore, retry: Boolean = false): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .cookieJar(object : CookieJar {
                override fun loadForRequest(url: HttpUrl): List<Cookie> =
                    if (sameOrigin(url, origin)) cookies.loadForRequest(url) else emptyList()
                override fun saveFromResponse(url: HttpUrl, values: List<Cookie>) {
                    // Live cookies are committed by the network boundary under the account epoch lock.
                    if (!retry && sameOrigin(url, origin)) cookies.saveFromResponse(url, values)
                }
            })
            .connectTimeout(20, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor { chain -> chain.proceed(chain.request().newBuilder()
                .header("Accept-Language", L10n.language).build()) }
            .addInterceptor(ServiceAvailabilityInterceptor(serviceAvailability))
            .addInterceptor(CsrfInterceptor(cookies))
            .addInterceptor(AccessTokenInterceptor(storage, origin))
        if (retry) {
            builder.interceptors().add(0, SessionGenerationInterceptor(sessionRepository))
            builder.addInterceptor(UnauthorizedRetryInterceptor(sessionRepository))
        }
        builder.addNetworkInterceptor(CredentialBoundaryInterceptor(origin, if (retry) sessionRepository else null))
        if (environment.enableHttpLogging) builder.addInterceptor { chain ->
            val request = chain.request()
            chain.proceed(request).also { response ->
                // Paths only: query strings contain one-time approval and authorization tickets.
                android.util.Log.d("KsuserNetwork", "${request.method} ${request.url.encodedPath} ${response.code}")
            }
        }
        return builder.build()
    }

    val sessionRepository: SessionRepository by lazy {
        SessionRepository(origin, sessionStorage, cookieStore, gson) { storage, cookies ->
            retrofit(client(storage, cookies)).create(KsuserApiService::class.java)
        }
    }
    private val mainClient by lazy { client(sessionStorage, cookieStore, retry = true) }
    private val mainRetrofit by lazy { retrofit(mainClient) }
    fun <T> createService(serviceClass: Class<T>): T = mainRetrofit.create(serviceClass)
    private val mainApi by lazy { createService(KsuserApiService::class.java) }
    val authRepository by lazy { AuthRepository(mainApi, sessionRepository, gson) }
    val profileRepository by lazy { ProfileRepository(mainApi, gson) }
    val securityRepository by lazy { SecurityRepository(mainApi, gson) }
    val sessionsRepository by lazy { SessionsRepository(mainApi, gson) }
    val logsRepository by lazy { LogsRepository(mainApi, gson) }
    val accountRepository by lazy { AccountRepository(createService(AccountApiService::class.java), gson) }
    val mobileAuthorizationRepository by lazy { MobileAuthorizationRepository(createService(MobileAuthorizationApi::class.java), gson) }
    val nativeAuthorizationRepository by lazy { NativeAuthorizationRepository(createService(NativeAuthorizationApi::class.java), gson) }
    val nativeAuthorizationStateStore by lazy { NativeAuthorizationStateStore(
        readValue = { sessionStorage.preferences.getString("native_authorization", null) },
        writeValue = { value -> check(sessionStorage.preferences.edit().putString("native_authorization", value).commit()) { "Unable to save authorization" } },
    ) }
    val appUpdateRepository by lazy {
        // Public metadata and APK traffic must never carry API credentials, cookies or CSRF state.
        AppUpdateRepository(appContext, environment, OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build(), gson)
    }
    val passkeyManager by lazy { PasskeyManager(CredentialManager.create(appContext), gson, environment, appContext) }
    val securityKeyManager by lazy { cn.ksuser.auth.core.passkey.SecurityKeyManager(environment, passkeyManager) }
    val qqLoginManager by lazy { QqLoginManager(environment) }
    private fun retrofit(client: OkHttpClient) = Retrofit.Builder()
        .baseUrl("${environment.apiBaseUrl}/").client(client).addConverterFactory(GsonConverterFactory.create(gson)).build()
}
