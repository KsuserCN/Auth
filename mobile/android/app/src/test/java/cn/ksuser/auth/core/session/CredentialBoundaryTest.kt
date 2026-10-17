package cn.ksuser.auth.core.session

import cn.ksuser.auth.core.network.*
import com.google.gson.Gson
import okhttp3.*
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.Base64

class CredentialBoundaryTest {
    @Test fun apiCredentialsNeverLeaveConfiguredOrigin() {
        MockWebServer().use { trusted -> MockWebServer().use { other ->
            trusted.start(); other.start()
            val storage = MemorySessionStorage().apply { setAccessToken("private-access-token") }
            val client = OkHttpClient.Builder().addInterceptor(AccessTokenInterceptor(storage, trusted.url("/"))).build()
            other.enqueue(MockResponse().setBody("public metadata"))
            client.newCall(Request.Builder().url(other.url("/android.json"))
                .header("User-Agent", "test").header("Authorization", "Bearer accidental-token")
                .header("Cookie", "refreshToken=private-cookie").header("X-XSRF-TOKEN", "private-csrf").build()).execute().close()
            val publicRequest = other.takeRequest()
            assertNull(publicRequest.getHeader("Authorization"))
            assertNull(publicRequest.getHeader("Cookie"))
            assertNull(publicRequest.getHeader("X-XSRF-TOKEN"))
            trusted.enqueue(MockResponse().setBody("OK"))
            client.newCall(Request.Builder().url(trusted.url("/auth/userinfo")).header("User-Agent", "test").build()).execute().close()
            assertEquals("Bearer private-access-token", trusted.takeRequest().getHeader("Authorization"))
        } }
    }

    @Test fun unauthorizedRequestIsReplayedOnlyOnceEvenIfReplayIsUnauthorized() {
        MockWebServer().use { server ->
            server.start()
            val gson = Gson()
            val storage = MemorySessionStorage().apply { setAccessToken(jwt("old")) }
            val cookies = CookieStore.inMemory(gson)
            cookies.saveFromResponse(server.url("/"), listOf(Cookie.Builder().name("refreshToken").value("refresh")
                .hostOnlyDomain(server.hostName).path("/").expiresAt(System.currentTimeMillis()+600_000).build()))
            fun client(store: SessionTokenStore, jar: CookieStore) = OkHttpClient.Builder()
                .cookieJar(PersistentCookieJar(jar)).addInterceptor { chain -> chain.proceed(chain.request().newBuilder().header("User-Agent", "test").build()) }
                .addInterceptor(AccessTokenInterceptor(store, server.url("/")))
            val session = SessionRepository(server.url("/"), storage, cookies, gson) { tokenStore, cookieStore ->
                Retrofit.Builder().baseUrl(server.url("/")).client(client(tokenStore,cookieStore).build())
                    .addConverterFactory(GsonConverterFactory.create(gson)).build().create(KsuserApiService::class.java)
            }
            val main = client(storage,cookies).addInterceptor(UnauthorizedRetryInterceptor(session)).build()
            server.enqueue(MockResponse().setResponseCode(401))
            server.enqueue(MockResponse().setHeader("Content-Type", "application/json")
                .setBody("""{"code":200,"data":{"accessToken":"${jwt("new")}"}}"""))
            server.enqueue(MockResponse().setResponseCode(401))
            main.newCall(Request.Builder().url(server.url("/auth/userinfo")).build()).execute().use { assertEquals(401,it.code) }
            assertEquals("/auth/userinfo",server.takeRequest().path)
            assertEquals("/auth/refresh",server.takeRequest().path)
            val replay=server.takeRequest()
            assertEquals("Bearer ${jwt("new")}",replay.getHeader("Authorization"))
            assertEquals("1",replay.getHeader("X-KSUSER-REFRESH-RETRIED"))
            assertEquals(3,server.requestCount)
        }
    }
    @Test fun crossOriginRedirectCannotForwardCsrfOrCredentials() {
        MockWebServer().use { api -> MockWebServer().use { public ->
            api.start(); public.start()
            api.enqueue(MockResponse().setResponseCode(308).setHeader("Location", public.url("/metadata")))
            public.enqueue(MockResponse().setBody("public"))
            val client = OkHttpClient.Builder().addNetworkInterceptor(CredentialBoundaryInterceptor(api.url("/"))).build()
            client.newCall(Request.Builder().url(api.url("/auth/example"))
                .post("{}".toRequestBody()).header("Authorization", "Bearer secret")
                .header("Cookie", "refreshToken=secret").header("X-XSRF-TOKEN", "secret").build()).execute().close()
            val request = public.takeRequest()
            assertNull(request.getHeader("Authorization")); assertNull(request.getHeader("Cookie")); assertNull(request.getHeader("X-XSRF-TOKEN"))
        } }
    }
    private fun jwt(value: String) = "e30." + Base64.getUrlEncoder().withoutPadding()
        .encodeToString("""{"sub":"$value","exp":4102444800}""".toByteArray()) + ".signature"
}
