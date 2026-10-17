package cn.ksuser.auth.core.security

import cn.ksuser.auth.data.model.SensitiveVerificationStatus

/** Confirmation is single-use and cancellation invalidates even an in-flight verification read. */
class ConfirmedSensitiveAction(private val status: suspend () -> SensitiveVerificationStatus) {
    private var generation = 0L
    private var confirmed = false

    @Synchronized fun confirm() { generation++; confirmed = true }
    @Synchronized fun cancel() { generation++; confirmed = false }
    @Synchronized fun isPending(): Boolean = confirmed

    suspend fun consumeIfVerified(): Boolean {
        val started = synchronized(this) { if (!confirmed) return false else generation }
        val currentStatus = status()
        return synchronized(this) {
            if (!confirmed || generation != started || !currentStatus.verified || currentStatus.remainingSeconds <= 0) false
            else { confirmed = false; true }
        }
    }
}
