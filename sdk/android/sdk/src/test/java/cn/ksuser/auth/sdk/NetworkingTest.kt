package cn.ksuser.auth.sdk

import com.google.gson.Gson
import com.google.gson.JsonParser
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.withContext
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Assert.*
import org.junit.Test

class NetworkingTest {
    private val gson = Gson()
    private fun configuration(server: MockWebServer, mode: KsuserAuthorizationMode = KsuserAuthorizationMode.OAUTH) =
        KsuserAuthConfiguration("client", mode, "https://client.example/callback", apiBaseUrl = server.url("/").toString())
    private fun created() = NativeCreated("t".repeat(32), "c".repeat(32),
        "https://auth.ksuser.cn/app/sdk-authorize?ticket=" + "t".repeat(32),
        "https://auth.ksuser.cn/app/sdk-authorize/web?ticket=" + "t".repeat(32), 300)
    private fun transaction(config: KsuserAuthConfiguration) = PendingTransaction(config.identity, config.redirectUri,
        "v".repeat(43), "s".repeat(43), null, created(), System.currentTimeMillis() + 300_000, code = "one-time-code")

    @Test fun createUsesAndroidPlatformAndPublicPkceForBothModes() = runBlocking {
        for (mode in KsuserAuthorizationMode.entries) {
            MockWebServer().use { server ->
                server.enqueue(MockResponse().setBody(gson.toJson(mapOf("code" to 200, "data" to created()))))
                val nonce = if (mode == KsuserAuthorizationMode.OFFICIAL) "nonce" else null
                SdkHttp(configuration(server, mode)).create(listOf("profile"), "state", nonce, "v".repeat(43))
                val request = server.takeRequest()
                assertEquals("/auth/native-authorization/create", request.path)
                val body = JsonParser.parseString(request.body.readUtf8()).asJsonObject
                assertEquals("android", body.get("platform").asString)
                assertEquals(mode.name.lowercase(), body.get("mode").asString)
                val auth = body.getAsJsonObject("authorization")
                assertEquals(Pkce.challenge("v".repeat(43)), auth.get("codeChallenge").asString)
                assertEquals("S256", auth.get("codeChallengeMethod").asString)
                assertEquals(nonce, auth.get("nonce")?.asString)
                assertFalse(body.toString().contains("code_verifier"))
                assertNull(request.getHeader("Authorization"))
                assertNull(request.getHeader("Cookie"))
            }
        }
    }

    @Test fun tokenExchangeNeverUsesSecretPassportSessionOrResponseCookies() = runBlocking {
        MockWebServer().use { server ->
            val config = configuration(server)
            val http = SdkHttp(config)
            server.enqueue(MockResponse().setHeader("Set-Cookie", "passport=secret; Path=/")
                .setBody(gson.toJson(mapOf("code" to 200, "data" to created()))))
            http.create(listOf("profile"), "state", null, "v".repeat(43))
            server.takeRequest()
            server.enqueue(MockResponse().setBody("""{"access_token":"app-token","token_type":"Bearer","expires_in":300,"openid":"app-openid"}"""))
            val tokens = http.exchange(transaction(config))
            assertEquals("app-openid", tokens.openId)
            assertNull(tokens.unionId)
            val request = server.takeRequest()
            assertEquals("/oauth2/token", request.path)
            assertNull(request.getHeader("Cookie"))
            assertNull(request.getHeader("Authorization"))
            val body = request.body.readUtf8()
            assertTrue(body.contains("code_verifier=" + "v".repeat(43)))
            assertTrue(body.contains("client_id=client"))
            assertFalse(body.contains("client_secret"))
        }
    }

    @Test fun rejectsRedirectsAndDoesNotExposeRawFailureBody() = runBlocking {
        MockWebServer().use { server ->
            val http = SdkHttp(configuration(server))
            server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", server.url("/steal")))
            val error = runCatching { http.create(listOf("profile"), "state", null, "v".repeat(43)) }.exceptionOrNull() as KsuserAuthException
            assertEquals(302, error.httpStatus)
            assertEquals(1, server.requestCount)
            server.enqueue(MockResponse().setResponseCode(503).setBody("""{"errorCode":"UNAVAILABLE","code":"SECRET_CODE","ticket":"SECRET_TICKET"}"""))
            val unavailable = runCatching { http.exchange(transaction(configuration(server))) }.exceptionOrNull() as KsuserAuthException
            assertEquals("UNAVAILABLE", unavailable.serverErrorCode)
            assertTrue(unavailable.recoverable)
            assertFalse(unavailable.toString().contains("SECRET"))
        }
    }

    @Test(timeout = 5_000) fun cancellingSuspendedHttpDoesNotWaitForTheNetworkTimeout() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            val request = async { SdkHttp(configuration(server)).create(listOf("profile"), "state", null, "v".repeat(43)) }
            withContext(Dispatchers.IO) { server.takeRequest() }
            request.cancelAndJoin()
            assertTrue(request.isCancelled)
        }
    }
}
