package cn.ksuser.auth.data.repository

import cn.ksuser.auth.core.network.AccountApiService
import cn.ksuser.auth.core.network.ApiEnvelope
import cn.ksuser.auth.core.network.LogsDetailApiService
import cn.ksuser.auth.core.network.KsuserApiService
import cn.ksuser.auth.data.model.AccountLinkStatus
import cn.ksuser.auth.data.model.AuthorizedApplication
import cn.ksuser.auth.data.model.OfficialAuthorizedApplication
import cn.ksuser.auth.data.model.SensitiveLogItem
import com.google.gson.Gson
import java.lang.reflect.Proxy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Response

class AccountRepositoryTest {
    private fun api(handler: (String, Array<out Any?>?) -> Any?): AccountApiService =
        Proxy.newProxyInstance(AccountApiService::class.java.classLoader, arrayOf(AccountApiService::class.java)) { _, method, args -> handler(method.name, args) } as AccountApiService

    @Test fun loadsBothAuthorizationKindsAndKeepsGrantMetadata() = runTest {
        val repository = AccountRepository(api { method, _ -> when (method) {
            "thirdPartyApps" -> Response.success(ApiEnvelope(200, data = listOf(AuthorizedApplication("calendar", "Calendar", scopes = listOf("email"), grantMode = "TIME_LIMITED", expiresAt = "2026-11-01T00:00:00Z"))))
            "officialApps" -> Response.success(ApiEnvelope(200, data = listOf(OfficialAuthorizedApplication("console", "Console", scopes = listOf("openid")))))
            else -> error("Unexpected API call")
        } }, Gson())
        val apps = repository.authorizedApps()
        assertEquals("calendar", apps.thirdParty.single().appId)
        assertEquals("TIME_LIMITED", apps.thirdParty.single().grantMode)
        assertEquals("console", apps.official.single().clientId)
    }
    @Test fun failedAuthorizationReadNeverBecomesAnEmptySuccessfulList() = runTest {
        val repository = AccountRepository(api { method, _ -> when (method) {
            "thirdPartyApps" -> Response.success(ApiEnvelope(500, data = emptyList<AuthorizedApplication>()))
            "officialApps" -> Response.success(ApiEnvelope(200, data = emptyList<OfficialAuthorizedApplication>()))
            else -> error("Unexpected API call")
        } }, Gson())
        try { repository.authorizedApps(); fail("Expected failure") }
        catch (expected: cn.ksuser.auth.core.network.ApiException) { assertEquals(500, expected.statusCode) }
    }
    @Test fun failedRevokeStaysFailedAndBindingStatusDoesNotGuessUnknownProviders() = runTest {
        val repository = AccountRepository(api { method, _ -> when (method) {
            "revokeThirdParty" -> Response.success(ApiEnvelope<Unit>(409))
            "accountStatus" -> Response.success(ApiEnvelope(200, data = listOf(AccountLinkStatus("apple", true))))
            else -> error("Unexpected API call")
        } }, Gson())
        try { repository.revokeThirdParty("calendar"); fail("Expected revoke failure") }
        catch (expected: cn.ksuser.auth.core.network.ApiException) { assertEquals(409, expected.statusCode) }
        try { repository.qqBound(); fail("Unknown QQ status must not be interpreted as unbound") }
        catch (expected: IllegalStateException) { assertEquals("QQ 状态为空", expected.message) }
    }
}
