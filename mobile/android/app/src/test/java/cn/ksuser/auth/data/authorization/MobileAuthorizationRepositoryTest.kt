package cn.ksuser.auth.data.authorization

import cn.ksuser.auth.core.network.ApiEnvelope
import com.google.gson.Gson
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Response

class MobileAuthorizationRepositoryTest {
    private val ticket = "a".repeat(32)
    private class FakeApi : MobileAuthorizationApi {
        var decision: MobileAuthorizationDecision? = null
        var officialApproved: Boolean? = null
        override suspend fun preview(approveCode: String?, transferCode: String?) = Response.success(ApiEnvelope(200, data = AuthorizationQrPreview("approve_application")))
        override suspend fun context(ticket: String) = Response.success(ApiEnvelope(200, data = MobileAuthorizationContext(ticket, "Application", redirectUri = "https://app.invalid/callback", expiresInSeconds = 120)))
        override suspend fun decide(decision: MobileAuthorizationDecision): Response<ApiEnvelope<MobileAuthorizationReturn>> {
            this.decision = decision
            return Response.success(ApiEnvelope(200, data = MobileAuthorizationReturn(completed = true)))
        }
        override suspend fun approveOfficial(body: Map<String, String>): Response<ApiEnvelope<Map<String, Any>>> {
            officialApproved = true
            return Response.success(ApiEnvelope(200, data = mapOf("verified" to true)))
        }
        override suspend fun rejectOfficial(body: Map<String, String>): Response<ApiEnvelope<Map<String, Any>>> {
            officialApproved = false
            return Response.success(ApiEnvelope(200, data = mapOf("verified" to false)))
        }
    }

    @Test fun denialNeverCreatesBrowserLoginAndNonQrCannotOptIn() = runTest {
        val api = FakeApi()
        val repository = MobileAuthorizationRepository(api, Gson())
        val context = MobileAuthorizationContext(ticket, "Application", redirectUri = "https://app.invalid/callback", qr = true)
        repository.decide(context, false, AuthorizationGrantMode.TIME_LIMITED, 3600, true)
        assertFalse(api.decision!!.loginWeb)
        repository.decide(context.copy(qr = false), true, AuthorizationGrantMode.ONE_TIME, 3600, true)
        assertFalse(api.decision!!.loginWeb)
        assertNull(api.decision!!.grantTtlSeconds)
        repository.decide(context, true, AuthorizationGrantMode.TIME_LIMITED, 300, true)
        assertTrue(api.decision!!.loginWeb)
        assertEquals(300, api.decision!!.grantTtlSeconds)
    }

    @Test fun officialGrantsKeepExistingContractAndUseDedicatedRejectEndpoint() = runTest {
        val api = FakeApi()
        val repository = MobileAuthorizationRepository(api, Gson())
        val context = MobileAuthorizationContext(ticket, "Official", redirectUri = "https://app.invalid/callback", officialApplication = true)
        repository.decide(context, true, AuthorizationGrantMode.ONE_TIME, 300, false)
        assertEquals("PERSISTENT", api.decision!!.grantMode)
        repository.decideOfficial(ticket, true)
        assertEquals(true, api.officialApproved)
        repository.decideOfficial(ticket, false)
        assertEquals(false, api.officialApproved)
    }
}
