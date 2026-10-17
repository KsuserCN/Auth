package cn.ksuser.auth.core

import cn.ksuser.auth.core.security.ConfirmedSensitiveAction
import cn.ksuser.auth.data.model.SensitiveVerificationStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class ConfirmedSensitiveActionTest {
    @Test fun unconfirmedExpiredAndUnverifiedRequestsCannotExecute() = runTest {
        var status = SensitiveVerificationStatus(true, 60)
        val action = ConfirmedSensitiveAction { status }
        assertFalse(action.consumeIfVerified())
        action.confirm()
        status = SensitiveVerificationStatus(false, 60, methods = listOf("unknown"))
        assertFalse(action.consumeIfVerified())
        status = SensitiveVerificationStatus(true, 0)
        assertFalse(action.consumeIfVerified())
        status = SensitiveVerificationStatus(true, 60)
        assertTrue(action.consumeIfVerified())
        assertFalse(action.consumeIfVerified())
    }
    @Test fun cancellingWhileStatusIsLoadingDiscardsTheContinuation() = runTest {
        val requestStarted = CompletableDeferred<Unit>()
        val status = CompletableDeferred<SensitiveVerificationStatus>()
        val action = ConfirmedSensitiveAction { requestStarted.complete(Unit); status.await() }
        action.confirm()
        val pending = async { action.consumeIfVerified() }
        requestStarted.await()
        action.cancel()
        status.complete(SensitiveVerificationStatus(true, 60))
        assertFalse(pending.await())
        assertFalse(action.isPending())
    }
    @Test fun parallelValidationCanConsumeConfirmationOnlyOnce() = runTest {
        val status = CompletableDeferred<SensitiveVerificationStatus>()
        val action = ConfirmedSensitiveAction { status.await() }
        action.confirm()
        val first = async { action.consumeIfVerified() }
        val second = async { action.consumeIfVerified() }
        status.complete(SensitiveVerificationStatus(true, 60))
        assertEquals(1, listOf(first.await(), second.await()).count { it })
    }
}
