package cn.ksuser.auth.data.authorization

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

/** Owns one complete credential flow, including the profile commit and deferred-request continuation. */
internal class AuthenticationFlowRunner(
    private val scope: CoroutineScope,
    private val discardStage: () -> Unit,
) {
    private var epoch = 0L
    private var job: Job? = null
    val isRunning: Boolean get() = job?.isActive == true

    inner class Attempt internal constructor(private val id: Long) {
        suspend fun checkpoint() {
            currentCoroutineContext().ensureActive()
            if (id != epoch) throw CancellationException("Authentication was cancelled")
        }
    }

    fun <T> launch(action: suspend Attempt.() -> T, success: suspend (T) -> Unit,
                   failure: (Throwable) -> Unit): Boolean {
        if (isRunning) return false
        val id = ++epoch
        job = scope.launch(start = CoroutineStart.LAZY) {
            val attempt = Attempt(id)
            try {
                val result = attempt.action()
                attempt.checkpoint()
                success(result)
            } catch (cancelled: CancellationException) {
                if (epoch == id) discardStage()
                throw cancelled
            } catch (error: Throwable) {
                if (epoch == id) failure(error)
            } finally {
                if (epoch == id) job = null
            }
        }.also(Job::start)
        return true
    }

    fun cancel() {
        epoch++
        job?.cancel()
        job = null
        discardStage()
    }
}
