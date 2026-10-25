package cn.ksuser.auth.data.authorization

import cn.ksuser.auth.core.network.ApiEnvelope
import com.google.gson.Gson
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Response

class NativeAuthorizationRepositoryTest {
    private val ticket = "t".repeat(32)
    private val state = "s".repeat(32)
    private val callback = "https://app.example/ksuser/callback"
    private inner class FakeApi : NativeAuthorizationApi {
        var context = NativeAuthorizationContext(ticket, "Application", redirectUri = callback,
            expiresInSeconds = 120, nativeAuthorization = true)
        var decision: NativeAuthorizationDecision? = null
        var response: NativeAuthorizationReturn? = null
        override suspend fun context(ticket: String) = Response.success(ApiEnvelope(200, data = context))
        override suspend fun decide(decision: NativeAuthorizationDecision): Response<ApiEnvelope<NativeAuthorizationReturn>> {
            this.decision = decision
            val result = if (decision.approve) "code=once" else "error=access_denied"
            return Response.success(ApiEnvelope(200, data = response ?: NativeAuthorizationReturn("$callback?$result&state=$state", true)))
        }
    }

    @Test fun nativeContextCannotBeConfusedWithLegacyOrExpiredContexts() = runTest {
        val api = FakeApi()
        val repository = NativeAuthorizationRepository(api, Gson())
        assertTrue(repository.context(ticket).forConsent().nativeAuthorization)
        for (invalid in listOf(api.context.copy(nativeAuthorization = false), api.context.copy(ticket = "x".repeat(32)),
            api.context.copy(expiresInSeconds = 0), api.context.copy(expiresInSeconds = 301),
            api.context.copy(redirectUri = "javascript:alert(1)"))) {
            api.context = invalid
            assertTrue(runCatching { repository.context(ticket) }.isFailure)
        }
    }

    @Test fun decisionsPreserveGrantSemanticsAndValidateServerReturn() = runTest {
        val api = FakeApi()
        val repository = NativeAuthorizationRepository(api, Gson())
        val context = api.context.forConsent()
        repository.decide(context, true, AuthorizationGrantMode.TIME_LIMITED, 3600)
        assertEquals("TIME_LIMITED", api.decision!!.grantMode)
        assertEquals(3600, api.decision!!.grantTtlSeconds)
        repository.decide(context.copy(officialApplication = true), true, AuthorizationGrantMode.ONE_TIME, 300)
        assertEquals("PERSISTENT", api.decision!!.grantMode)
        assertNull(api.decision!!.grantTtlSeconds)
        repository.decide(context, false, AuthorizationGrantMode.TIME_LIMITED, 3600)
        assertFalse(api.decision!!.approve)
        assertNull(api.decision!!.grantTtlSeconds)
        for (invalid in listOf(NativeAuthorizationReturn("https://evil.invalid/?code=x&state=$state", true),
            NativeAuthorizationReturn("$callback?code=x&state=$state", false),
            NativeAuthorizationReturn("$callback?code=x&state=$state&code=y", true))) {
            api.response = invalid
            assertTrue(runCatching { repository.decide(context, true, AuthorizationGrantMode.PERSISTENT, 3600) }.isFailure)
        }
    }
}
