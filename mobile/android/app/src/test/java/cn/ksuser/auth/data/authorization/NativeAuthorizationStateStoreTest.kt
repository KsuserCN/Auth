package cn.ksuser.auth.data.authorization

import org.junit.Assert.*
import org.junit.Test

class NativeAuthorizationStateStoreTest {
    private var encoded: String? = null
    private var now = 100_000L
    private fun store() = NativeAuthorizationStateStore({ encoded }, { encoded = it }, { now })
    private val ticket = "t".repeat(32)
    private val callback = "https://app.example/ksuser/callback"

    @Test fun processRecreationRestoresOriginalTicketAndDecidedReturnWithoutIssuance() {
        val request = PendingNativeAuthorization(ticket, now + 60_000)
        store().save(request)
        assertEquals(request, store().read())
        val result = request.copy(accountUuid = "user", appName = "Application", redirectUri = callback,
            returnUrl = "$callback?code=once&state=${"s".repeat(32)}", approved = true)
        store().save(result)
        assertEquals(result, store().read())
        assertEquals(result.returnUrl, store().read()!!.returnUrl)
        now += 60_001
        assertNull(store().read())
        assertNull(encoded)
    }

    @Test fun corruptedOrSubstitutedCallbacksAreCleared() {
        for (raw in listOf("not-json", "{}", "null", "{\"ticket\":null}",
            "{\"ticket\":\"$ticket\",\"expiresAtMillis\":160000,\"accountUuid\":\"user\",\"appName\":\"App\",\"approved\":true,\"redirectUri\":\"$callback\",\"returnUrl\":\"https://evil.invalid/\"}")) {
            encoded = raw
            assertNull(raw, store().read())
            assertNull(encoded)
        }
    }
}
