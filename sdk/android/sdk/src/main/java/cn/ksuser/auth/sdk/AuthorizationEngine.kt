package cn.ksuser.auth.sdk

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull

internal fun interface AuthorizationLauncher {
    suspend fun open(transaction: PendingTransaction, useWebFallback: Boolean)
}

/** Shared across all SDK instances in this calling application process. */
internal class AuthorizationRuntime {
    var active: Lease? = null
    class Lease(val configurationId: String) {
        val changed = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()
    }

    companion object {
        private val applications = ConcurrentHashMap<String, AuthorizationRuntime>()
        fun forApplication(packageName: String): AuthorizationRuntime = applications.getOrPut(packageName) { AuthorizationRuntime() }
    }
}

internal class AuthorizationEngine(
    private val configuration: KsuserAuthConfiguration,
    private val storage: TransactionStore,
    private val http: AuthorizationHttp,
    private val runtime: AuthorizationRuntime,
    private val now: () -> Long = System::currentTimeMillis,
) {
    suspend fun authorize(scopes: List<String>?, launcher: AuthorizationLauncher): KsuserAuthorizationResult {
        val selected = scopes ?: configuration.defaultScopes
        if (selected.isEmpty() || selected.any { !Regex("[A-Za-z0-9_:.-]+").matches(it) }) {
            throw KsuserAuthException(KsuserAuthErrorCode.INVALID_CONFIGURATION)
        }
        val lease = synchronized(runtime) {
            if (runtime.active != null || load() != null) inProgress()
            AuthorizationRuntime.Lease(configuration.identity).also { runtime.active = it }
        }
        return run(lease) {
            val verifier = Pkce.random()
            val state = Pkce.random()
            val nonce = if (configuration.mode == KsuserAuthorizationMode.OFFICIAL) Pkce.random() else null
            val created = coroutineScope {
                val request = async { http.create(selected.distinct(), state, nonce, verifier) }
                try {
                    select {
                        request.onAwait { it }
                        lease.changed.onAwait { throw KsuserAuthException(KsuserAuthErrorCode.CANCELLED) }
                    }
                } finally { if (!request.isCompleted) request.cancel() }
            }
            CreatedValidator.validate(created, configuration)
            val transaction = PendingTransaction(configuration.identity, configuration.redirectUri, verifier, state, nonce,
                created, now() + created.expiresInSeconds * 1000)
            try {
                synchronized(runtime) { requireActive(lease); storage.save(transaction) }
            } catch (error: Exception) {
                cancelOnServer(transaction)
                throw error
            }
            await(lease, transaction, launcher, false)
        }
    }

    suspend fun resume(launcher: AuthorizationLauncher, useWebFallback: Boolean): KsuserAuthorizationResult? {
        var existingActive = false
        val pair = synchronized(runtime) {
            val active = runtime.active
            if (active != null && (active.configurationId != configuration.identity || !useWebFallback)) inProgress()
            val transaction = load() ?: if (active != null) inProgress() else return null
            if (active != null) {
                existingActive = true
                active to transaction
            } else {
                val lease = AuthorizationRuntime.Lease(configuration.identity)
                runtime.active = lease
                lease to transaction
            }
        }
        val (lease, transaction) = pair
        if (existingActive) {
            if (!transaction.hasResult) launcher.open(transaction, true)
            return null
        }
        return run(lease) { await(lease, transaction, launcher, useWebFallback) }
    }

    fun handleCallback(value: String): Boolean = synchronized(runtime) {
        if (runtime.active?.configurationId?.let { it != configuration.identity } == true) return false
        val transaction = try { load() } catch (_: KsuserAuthException) { return false } ?: return false
        val outcome = CallbackParser.parse(value, transaction.redirectUri, transaction.state) ?: return false
        if (transaction.hasResult) return true
        transaction.code = outcome.code
        transaction.callbackError = outcome.error
        try {
            storage.save(transaction)
            runtime.active?.changed?.complete(Unit)
        } catch (error: KsuserAuthException) {
            runtime.active?.changed?.completeExceptionally(error)
        }
        true
    }

    suspend fun cancel() {
        var storageError: KsuserAuthException? = null
        val transaction = synchronized(runtime) {
            val active = runtime.active
            if (active != null && active.configurationId != configuration.identity) return
            val saved = try { load() } catch (error: KsuserAuthException) {
                if (error.code == KsuserAuthErrorCode.AUTHORIZATION_IN_PROGRESS) return
                storageError = error
                null
            }
            try { storage.clear() } catch (error: KsuserAuthException) { storageError = error }
            runtime.active = null
            active?.cancelled?.completeExceptionally(storageError ?: KsuserAuthException(KsuserAuthErrorCode.CANCELLED))
            saved
        }
        if (transaction != null) cancelOnServer(transaction)
        storageError?.let { throw it }
    }

    private suspend fun await(lease: AuthorizationRuntime.Lease, initial: PendingTransaction,
                              launcher: AuthorizationLauncher, useWebFallback: Boolean): KsuserAuthorizationResult {
        val remaining = initial.expiresAtMillis - now()
        if (remaining <= 0) throw KsuserAuthException(KsuserAuthErrorCode.EXPIRED)
        return try {
            withTimeout(remaining) {
                if (!initial.hasResult) {
                    launcher.open(initial, useWebFallback)
                    lease.changed.await()
                }
                val transaction = synchronized(runtime) {
                    requireActive(lease)
                    load() ?: throw KsuserAuthException(KsuserAuthErrorCode.EXPIRED)
                }
                transaction.callbackError?.let { callbackError ->
                    throw KsuserAuthException(KsuserAuthErrorCode.SERVER,
                        serverErrorCode = callbackError.takeIf { Regex("[A-Za-z0-9_.-]{1,128}").matches(it) })
                }
                val tokens = transaction.tokens ?: http.exchange(transaction).also { received ->
                    synchronized(runtime) {
                        requireActive(lease)
                        val saved = load() ?: throw KsuserAuthException(KsuserAuthErrorCode.EXPIRED)
                        if (saved.created.ticket != transaction.created.ticket) throw KsuserAuthException(KsuserAuthErrorCode.CANCELLED)
                        saved.tokens = received
                        saved.code = null
                        // Persist before JWKS I/O. Recovery never redeems a code for tokens already received.
                        storage.save(saved)
                    }
                }
                val result = if (configuration.mode == KsuserAuthorizationMode.OFFICIAL) {
                    val subject = http.verifyOfficial(tokens, transaction.nonce ?: invalidToken())
                    KsuserAuthorizationResult.Official(tokens, subject)
                } else KsuserAuthorizationResult.OAuth(tokens)
                synchronized(runtime) { requireActive(lease) }
                result
            }
        } catch (_: TimeoutCancellationException) {
            throw KsuserAuthException(KsuserAuthErrorCode.EXPIRED)
        }
    }

    private suspend fun run(lease: AuthorizationRuntime.Lease, action: suspend () -> KsuserAuthorizationResult): KsuserAuthorizationResult {
        try {
            val result = coroutineScope {
                val operation = async { action() }
                try {
                    select {
                        operation.onAwait { it }
                        lease.cancelled.onAwait { throw KsuserAuthException(KsuserAuthErrorCode.CANCELLED) }
                    }
                } finally { if (!operation.isCompleted) operation.cancel() }
            }
            finish(lease, preserve = false)
            return result
        } catch (error: CancellationException) {
            // Cancelling a Kotlin caller has the same local semantics as explicit cancel().
            val saved = synchronized(runtime) {
                if (runtime.active === lease) runCatching { load() }.getOrNull() else null
            }
            finish(lease, preserve = false)
            if (saved != null) cancelOnServer(saved)
            throw error
        } catch (error: KsuserAuthException) {
            finish(lease, preserve = error.recoverable)
            throw error
        } catch (_: Exception) {
            finish(lease, preserve = false)
            invalidResponse()
        }
    }

    private fun finish(lease: AuthorizationRuntime.Lease, preserve: Boolean) = synchronized(runtime) {
        if (runtime.active !== lease) return@synchronized
        try { if (!preserve) storage.clear() } finally { runtime.active = null }
    }

    private fun load(): PendingTransaction? {
        val transaction = storage.load() ?: return null
        try {
            if (transaction.expiresAtMillis <= now()) { storage.clear(); return null }
            if (transaction.configurationId != configuration.identity) inProgress()
            require(transaction.redirectUri == configuration.redirectUri)
            require(Regex("[A-Za-z0-9._~-]{43,128}").matches(transaction.verifier))
            require(Regex("[A-Za-z0-9_-]{22,128}").matches(transaction.state))
            require(configuration.mode != KsuserAuthorizationMode.OFFICIAL || !transaction.nonce.isNullOrEmpty())
            CreatedValidator.validate(transaction.created, configuration)
            return transaction
        } catch (error: KsuserAuthException) { throw error }
        catch (_: Exception) { throw KsuserAuthException(KsuserAuthErrorCode.STORAGE) }
    }

    private fun requireActive(lease: AuthorizationRuntime.Lease) {
        if (runtime.active !== lease) throw KsuserAuthException(KsuserAuthErrorCode.CANCELLED)
    }

    private suspend fun cancelOnServer(transaction: PendingTransaction) {
        withContext(NonCancellable) { withTimeoutOrNull(3_000) { runCatching { http.cancel(transaction) } } }
    }

    private fun inProgress(): Nothing = throw KsuserAuthException(KsuserAuthErrorCode.AUTHORIZATION_IN_PROGRESS)
}
