package cn.ksuser.auth.sdk

import org.junit.Assert.*
import org.junit.Test

class ProtocolTest {
    private val configuration = KsuserAuthConfiguration("client", KsuserAuthorizationMode.OAUTH, "https://client.example/callback")

    @Test fun pkceRfcVectorAndRandomValues() {
        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
            Pkce.challenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"))
        val values = List(100) { Pkce.random() }
        assertEquals(100, values.toSet().size)
        assertTrue(values.all { Regex("[A-Za-z0-9_-]{43}").matches(it) })
    }

    @Test fun callbackRequiresExactEndpointStateAndExactlyOneOutcome() {
        val base = configuration.redirectUri
        assertEquals("once", CallbackParser.parse("$base?code=once&state=s", base, "s")?.code)
        assertEquals("access_denied", CallbackParser.parse("$base?state=s&error=access_denied", base, "s")?.error)
        for (value in listOf(
            "$base?code=c&state=wrong", "$base?code=c&state=s&state=s", "$base?code=c&state=s&error=x",
            "$base?code=&state=s", "$base?code=c&state=s#fragment", "$base/other?code=c&state=s",
            "http://client.example/callback?code=c&state=s", "https://evil.example/callback?code=c&state=s",
            "https://user@client.example/callback?code=c&state=s", "https://client.example:443/callback?code=c&state=s",
            "https://client.example/%63allback?code=c&state=s", "$base?code=c&state=s&",
        )) assertNull(value, CallbackParser.parse(value, base, "s"))
    }

    @Test fun rejectsConfigurationWithUnsafeCallbacksAndOrigins() {
        configuration.validate()
        for (redirect in listOf("http://client.example/callback", "https://client.example/callback?x=y",
            "https://client.example/callback#fragment", "https://auth.ksuser.cn/callback",
            "https://client.example/a/../callback", "https://client.example/%2e%2e/callback",
            "https://client.example/a%2fcallback", "https://u@client.example/callback", "https://client.example:444/callback")) {
            assertEquals(redirect, KsuserAuthErrorCode.INVALID_CONFIGURATION,
                runCatching { configuration.copy(redirectUri = redirect).validate() }.exceptionOrNull().let { (it as? KsuserAuthException)?.code })
        }
    }

    @Test fun createdUrlsCannotSubstituteOriginsPathsOrTicket() {
        val ticket = "t".repeat(32)
        fun created(link: String = "https://auth.ksuser.cn/app/sdk-authorize?ticket=$ticket",
                    web: String = "https://auth.ksuser.cn/app/sdk-authorize/web?ticket=$ticket") =
            NativeCreated(ticket, "c".repeat(32), link, web, 300)
        CreatedValidator.validate(created(), configuration)
        for (link in listOf("https://evil.example/app/sdk-authorize?ticket=$ticket",
            "https://auth.ksuser.cn/app/sdk-authorize/web?ticket=$ticket",
            "https://auth.ksuser.cn/app/sdk-authorize?ticket=$ticket&ticket=$ticket",
            "https://auth.ksuser.cn/app/sdk-authorize?ticket=$ticket#x",
            "https://auth.ksuser.cn/app/%73dk-authorize?ticket=$ticket",
            "https://auth.ksuser.cn/app/sdk-authorize?ticket=wrong")) {
            assertEquals(KsuserAuthErrorCode.INVALID_RESPONSE,
                (runCatching { CreatedValidator.validate(created(link), configuration) }.exceptionOrNull() as KsuserAuthException).code)
        }
    }

    @Test fun secretsAreNeverIncludedInResultToString() {
        val tokens = KsuserTokenSet("access-secret", "Bearer", 300, idToken = "jwt-secret")
        assertFalse(tokens.toString().contains("secret"))
        assertFalse(KsuserAuthorizationResult.Official(tokens, "subject-secret").toString().contains("secret"))
    }

    @Test fun onlyTheApplicationDefaultProcessMayOwnTransactions() {
        ProcessGuard.requireMatch("com.example.app", "com.example.app")
        ProcessGuard.requireMatch("com.example.app:main", "com.example.app:main")
        for ((expected, actual) in listOf("com.example.app" to "com.example.app:remote", "com.example.app" to null, null to "app")) {
            assertEquals(KsuserAuthErrorCode.INVALID_CONFIGURATION,
                (runCatching { ProcessGuard.requireMatch(expected, actual) }.exceptionOrNull() as KsuserAuthException).code)
        }
    }
}
