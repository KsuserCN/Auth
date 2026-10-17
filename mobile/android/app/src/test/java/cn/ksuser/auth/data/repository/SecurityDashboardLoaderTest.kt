package cn.ksuser.auth.data.repository

import cn.ksuser.auth.core.network.runCatchingCancellable
import cn.ksuser.auth.data.model.AdaptiveAuthStatus
import cn.ksuser.auth.data.model.SensitiveVerificationStatus
import cn.ksuser.auth.data.model.TotpStatus
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class SecurityDashboardLoaderTest {
    @Test fun oneFailedReadDoesNotSuppressOtherSecuritySections() = runTest {
        val called = mutableSetOf<String>()
        val loader = SecurityDashboardLoader(
            totp = { called += "totp"; throw IOException("offline") },
            passkeys = { called += "passkeys"; emptyList() },
            sensitive = { called += "sensitive"; SensitiveVerificationStatus(false, 0) },
            adaptive = { called += "adaptive"; AdaptiveAuthStatus(trusted = true) },
            qq = { called += "qq"; false },
            apple = { called += "apple"; true },
        )
        val snapshot = loader.load()
        assertTrue(snapshot.totp.isFailure)
        assertEquals(setOf("totp", "passkeys", "sensitive", "adaptive", "qq", "apple"), called)
        assertTrue(snapshot.passkeys.isSuccess)
        assertTrue(snapshot.adaptive.getOrThrow().trusted)
        assertEquals(true, snapshot.apple.getOrThrow())
    }
    @Test fun securityRequestsStartIndependentlyInsteadOfWaitingForPriorEndpoints() = runTest {
        val firstReleased = CompletableDeferred<Unit>()
        val lastStarted = CompletableDeferred<Unit>()
        val loader = SecurityDashboardLoader(
            totp = { firstReleased.await(); TotpStatus(false, 0) },
            passkeys = { emptyList() }, sensitive = { SensitiveVerificationStatus(false, 0) },
            adaptive = { AdaptiveAuthStatus() }, qq = { false },
            apple = { lastStarted.complete(Unit); true },
        )
        val request = async { loader.load() }
        lastStarted.await()
        assertFalse(request.isCompleted)
        firstReleased.complete(Unit)
        assertTrue(request.await().apple.getOrThrow())
    }
    @Test fun leavingAccountCancelsDashboardRatherThanReturningAVisibleError() = runTest {
        val loader = SecurityDashboardLoader(
            totp = { throw CancellationException("account changed") },
            passkeys = { emptyList() }, sensitive = { SensitiveVerificationStatus(false, 0) },
            adaptive = { AdaptiveAuthStatus() }, qq = { false }, apple = { false },
        )
        try { loader.load(); fail("Expected cancellation") }
        catch (expected: CancellationException) { assertEquals("account changed", expected.message) }
        try { runCatchingCancellable { throw CancellationException("dismissed") }; fail("Expected cancellation") }
        catch (expected: CancellationException) { assertEquals("dismissed", expected.message) }
    }
}
