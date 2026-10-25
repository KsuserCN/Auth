package cn.ksuser.demo.auth

import cn.ksuser.auth.sdk.KsuserAuthorizationMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Only identity and lifetime cross into demo state; the adapter never exposes tokens here. */
internal data class DemoAuthorizationOutcome(val mode: KsuserAuthorizationMode, val identity: String?,
    val unionId: String? = null, val expiresIn: Long)
internal enum class DemoStatus { NEEDS_CONFIGURATION, READY, MODE_CHANGED, WAITING, RESUMING, NO_PENDING, CANCELLING, CANCELLED, OAUTH_SUCCESS, OFFICIAL_SUCCESS, FAILED }
internal enum class DemoFailureKind { CONFIGURATION, IN_PROGRESS, CANCELLED, EXPIRED, CALLBACK, RESPONSE, ID_TOKEN, STORAGE, NETWORK, LAUNCH, ACCESS_DENIED, SERVICE, UNEXPECTED }
internal data class DemoFailureDetails(val kind: DemoFailureKind, val httpStatus: Int? = null, val serverCode: String? = null)
internal class DemoAuthorizationException(val details: DemoFailureDetails) : Exception(details.kind.name)

internal data class DemoLoginState(
    val mode: KsuserAuthorizationMode,
    val configured: Boolean = false,
    val waiting: Boolean = false,
    val cancelling: Boolean = false,
    val continuingWeb: Boolean = false,
    val status: DemoStatus = DemoStatus.NEEDS_CONFIGURATION,
    val failure: DemoFailureDetails? = null,
    val identity: String? = null,
    val unionId: String? = null,
    val expiresIn: Long? = null,
) {
    val canAuthorize get() = configured && !waiting && !cancelling && !continuingWeb
    val canResume get() = canAuthorize
    val canContinueWeb get() = configured && !cancelling && !continuingWeb
    val canCancel get() = configured && !cancelling
    val canSelectMode get() = !waiting && !cancelling && !continuingWeb
}

/** Host is weak in Android and a plain value in JVM tests. No Activity or URL is retained. */
internal interface DemoAuthorizationSession<Host> {
    suspend fun authorize(host: Host, scopes: List<String>): DemoAuthorizationOutcome
    suspend fun resume(host: Host, useWebFallback: Boolean): DemoAuthorizationOutcome?
    fun handleCallback(url: String): Boolean
    suspend fun cancel()
}

/** Owns exactly one SDK waiter across Activity recreation and ignores stale completions. */
internal class DemoLoginController<Host>(
    private val scope: CoroutineScope,
    initialMode: KsuserAuthorizationMode,
    private val configuredForMode: (KsuserAuthorizationMode) -> Boolean,
    private val scopesForMode: (KsuserAuthorizationMode) -> List<String>,
    private val sessionFactory: (KsuserAuthorizationMode) -> DemoAuthorizationSession<Host>,
    private val persistMode: (KsuserAuthorizationMode) -> Unit = {},
) {
    private val mutableState = MutableStateFlow(DemoLoginState(initialMode))
    val state = mutableState.asStateFlow()
    private var session: DemoAuthorizationSession<Host>? = null
    private var operation: Job? = null
    private var webOperation: Job? = null
    private var generation = 0L

    init { configure(initialMode, DemoStatus.READY) }

    private fun configure(mode: KsuserAuthorizationMode, status: DemoStatus) {
        session = null
        mutableState.value = mutableState.value.copy(mode = mode, configured = false, failure = null,
            identity = null, unionId = null, expiresIn = null, status = DemoStatus.NEEDS_CONFIGURATION)
        if (!configuredForMode(mode)) return
        try {
            session = sessionFactory(mode)
            mutableState.value = mutableState.value.copy(configured = true, status = status)
        } catch (error: Exception) {
            mutableState.value = mutableState.value.copy(status = DemoStatus.FAILED, failure = failure(error))
        }
    }

    fun selectMode(selected: KsuserAuthorizationMode) {
        val current = mutableState.value
        if (selected == current.mode || !current.canSelectMode) return
        val previous = session
        val attempt = ++generation
        mutableState.value = current.copy(cancelling = true, status = DemoStatus.CANCELLING, failure = null)
        scope.launch {
            try {
                // End the old configuration's transaction before installing another SDK instance.
                previous?.cancel()
                operation?.cancelAndJoin()
                if (attempt != generation) return@launch
                persistMode(selected)
                configure(selected, DemoStatus.MODE_CHANGED)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                if (attempt == generation) mutableState.value = mutableState.value.copy(status = DemoStatus.FAILED, failure = failure(error))
            } finally {
                if (attempt == generation) mutableState.value = mutableState.value.copy(cancelling = false)
            }
        }
    }

    fun authorize(host: Host) {
        val current = mutableState.value
        val client = session ?: return
        if (!current.canAuthorize) return
        val attempt = ++generation
        mutableState.value = current.copy(waiting = true, status = DemoStatus.WAITING, failure = null,
            identity = null, unionId = null, expiresIn = null)
        operation = scope.launch {
            try { show(attempt, client.authorize(host, scopesForMode(current.mode))) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { report(attempt, error) }
            finally { finishWaiting(attempt) }
        }
    }

    fun resume(host: Host, web: Boolean = false) {
        val current = mutableState.value
        val client = session ?: return
        if (!current.configured || current.cancelling || current.continuingWeb) return
        if (current.waiting) {
            if (!web) return
            val attempt = generation
            mutableState.value = current.copy(continuingWeb = true, failure = null)
            webOperation = scope.launch {
                try {
                    // The original operation remains the sole recipient of the authorization result.
                    client.resume(host, true)
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (error: Exception) {
                    if (attempt == generation) mutableState.value = mutableState.value.copy(failure = failure(error))
                } finally {
                    if (attempt == generation) mutableState.value = mutableState.value.copy(continuingWeb = false)
                }
            }
            return
        }
        val attempt = ++generation
        mutableState.value = current.copy(waiting = true, status = DemoStatus.RESUMING, failure = null)
        operation = scope.launch {
            try {
                val result = client.resume(host, web)
                if (result != null) show(attempt, result)
                else if (attempt == generation) mutableState.value = mutableState.value.copy(status = DemoStatus.NO_PENDING)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { report(attempt, error) }
            finally { finishWaiting(attempt) }
        }
    }

    fun callback(url: String, host: Host): Boolean {
        val current = mutableState.value
        val client = session ?: return false
        if (!current.configured || current.cancelling) return false
        val accepted = try { client.handleCallback(url) }
        catch (error: Exception) {
            mutableState.value = mutableState.value.copy(failure = failure(error))
            false
        }
        // Cold callbacks resume once; hot callbacks only wake the existing SDK waiter.
        if (accepted && !mutableState.value.waiting) resume(host)
        return accepted
    }

    fun cancel() {
        val current = mutableState.value
        val client = session ?: return
        if (!current.canCancel) return
        val attempt = ++generation
        val previous = operation
        webOperation?.cancel()
        mutableState.value = current.copy(waiting = false, cancelling = true, continuingWeb = false,
            status = DemoStatus.CANCELLING, failure = null, identity = null, unionId = null, expiresIn = null)
        scope.launch {
            try {
                client.cancel()
                previous?.cancelAndJoin()
                if (attempt == generation) mutableState.value = mutableState.value.copy(status = DemoStatus.CANCELLED)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { report(attempt, error) }
            finally {
                previous?.cancel()
                if (attempt == generation) mutableState.value = mutableState.value.copy(cancelling = false, waiting = false)
            }
        }
    }

    private fun show(attempt: Long, outcome: DemoAuthorizationOutcome) {
        if (attempt != generation) return
        if (outcome.mode != mutableState.value.mode) {
            mutableState.value = mutableState.value.copy(status = DemoStatus.FAILED, failure = DemoFailureDetails(DemoFailureKind.RESPONSE))
            return
        }
        mutableState.value = mutableState.value.copy(
            status = if (outcome.mode == KsuserAuthorizationMode.OAUTH) DemoStatus.OAUTH_SUCCESS else DemoStatus.OFFICIAL_SUCCESS,
            failure = null, identity = outcome.identity,
            unionId = outcome.unionId.takeIf { outcome.mode == KsuserAuthorizationMode.OAUTH }, expiresIn = outcome.expiresIn,
        )
    }
    private fun report(attempt: Long, error: Exception) {
        if (attempt == generation) mutableState.value = mutableState.value.copy(status = DemoStatus.FAILED, failure = failure(error))
    }
    private fun finishWaiting(attempt: Long) {
        if (attempt == generation) mutableState.value = mutableState.value.copy(waiting = false)
    }
    private fun failure(error: Exception) = (error as? DemoAuthorizationException)?.details ?: DemoFailureDetails(DemoFailureKind.UNEXPECTED)
}
