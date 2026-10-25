package cn.ksuser.auth.sdk

import com.google.gson.Gson
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthorizationEngineTest {
    private val configuration = KsuserAuthConfiguration("client", KsuserAuthorizationMode.OAUTH, "https://client.example/callback")

    private class MemoryStore : TransactionStore {
        private var json: String? = null
        private val gson = Gson()
        var failSave = false
        var corrupt = false
        override fun load(): PendingTransaction? {
            if (corrupt) throw KsuserAuthException(KsuserAuthErrorCode.STORAGE)
            return json?.let { gson.fromJson(it, PendingTransaction::class.java) }
        }
        override fun save(transaction: PendingTransaction) {
            if (failSave) throw KsuserAuthException(KsuserAuthErrorCode.STORAGE)
            json = gson.toJson(transaction)
        }
        override fun clear() { json = null; corrupt = false }
    }

    private class FakeHttp : AuthorizationHttp {
        var creates = 0
        var exchanges = 0
        var cancelled = 0
        var officialChecks = 0
        var requestedScopes: List<String>? = null
        var createdNonce: String? = null
        var createGate: CompletableDeferred<Unit>? = null
        var exchangeGate: CompletableDeferred<Unit>? = null
        var exchangeError: KsuserAuthException? = null
        var verifyError: KsuserAuthException? = null
        override suspend fun create(scopes: List<String>, state: String, nonce: String?, verifier: String): NativeCreated {
            creates++
            requestedScopes = scopes
            createdNonce = nonce
            createGate?.await()
            return NativeCreated("t".repeat(32), "c".repeat(32),
                "https://auth.ksuser.cn/app/sdk-authorize?ticket=" + "t".repeat(32),
                "https://auth.ksuser.cn/app/sdk-authorize/web?ticket=" + "t".repeat(32), 300)
        }
        override suspend fun cancel(transaction: PendingTransaction) { cancelled++ }
        override suspend fun exchange(transaction: PendingTransaction): KsuserTokenSet {
            exchanges++
            exchangeGate?.await()
            exchangeError?.let { throw it }
            return KsuserTokenSet("app-token", "Bearer", 3600, openId = "app-openid", idToken = "official-jwt")
        }
        override suspend fun verifyOfficial(tokens: KsuserTokenSet, nonce: String): String {
            officialChecks++
            verifyError?.let { throw it }
            return "verified-subject"
        }
    }

    private fun callback(store: MemoryStore, code: String = "once"): String = store.load()!!.let {
        it.redirectUri + "?code=" + code + "&state=" + it.state
    }
    private fun error(result: Result<*>): KsuserAuthErrorCode = (result.exceptionOrNull() as KsuserAuthException).code

    @Test fun callbackCompletesOnlyOnceAndClearsTransaction() = runTest {
        val store = MemoryStore()
        val http = FakeHttp()
        val engine = AuthorizationEngine(configuration, store, http, AuthorizationRuntime())
        val result = async { engine.authorize(null) { _, _ -> } }
        runCurrent()
        val url = callback(store)
        assertFalse(engine.handleCallback(url.replace("state=", "state=wrong")))
        assertTrue(engine.handleCallback(url))
        assertTrue(engine.handleCallback(url))
        val tokens = (result.await() as KsuserAuthorizationResult.OAuth).tokens
        assertEquals("app-openid", tokens.openId)
        assertNull(tokens.unionId)
        assertEquals(listOf("profile"), http.requestedScopes)
        assertEquals(1, http.exchanges)
        assertNull(store.load())
        assertFalse(engine.handleCallback(url))
    }

    @Test fun instancesAndConfigurationsCannotOverwriteAnOutstandingTransaction() = runTest {
        val store = MemoryStore()
        val http = FakeHttp()
        val runtime = AuthorizationRuntime()
        val first = AuthorizationEngine(configuration, store, http, runtime)
        val other = AuthorizationEngine(configuration.copy(clientId = "other"), store, http, runtime)
        val active = async { runCatching { first.authorize(null) { _, _ -> } } }
        runCurrent()
        assertEquals(KsuserAuthErrorCode.AUTHORIZATION_IN_PROGRESS, error(runCatching { other.authorize(null) { _, _ -> } }))
        assertEquals(KsuserAuthErrorCode.AUTHORIZATION_IN_PROGRESS, error(runCatching { first.authorize(null) { _, _ -> } }))
        other.cancel()
        assertNotNull(store.load())
        first.cancel()
        assertEquals(KsuserAuthErrorCode.CANCELLED, error(active.await()))
        assertNull(store.load())
        assertEquals(1, http.cancelled)
    }

    @Test fun explicitCancelDuringCreateAndExchangeEndsLocalWaiting() = runTest {
        for (duringCreate in listOf(true, false)) {
            val store = MemoryStore()
            val http = FakeHttp()
            if (duringCreate) http.createGate = CompletableDeferred() else http.exchangeGate = CompletableDeferred()
            val engine = AuthorizationEngine(configuration, store, http, AuthorizationRuntime())
            val result = async { runCatching { engine.authorize(null) { _, _ -> } } }
            runCurrent()
            if (!duringCreate) { assertTrue(engine.handleCallback(callback(store))); runCurrent() }
            engine.cancel()
            runCurrent()
            assertTrue(result.isCompleted)
            assertEquals(KsuserAuthErrorCode.CANCELLED, error(result.await()))
            assertNull(store.load())
        }
    }

    @Test fun webContinuationUsesSameTicketAndOriginalWaiter() = runTest {
        val store = MemoryStore()
        val http = FakeHttp()
        val engine = AuthorizationEngine(configuration, store, http, AuthorizationRuntime())
        val opened = mutableListOf<Pair<String, Boolean>>()
        val launcher = AuthorizationLauncher { tx, web -> opened += tx.created.ticket to web }
        val result = async { engine.authorize(null, launcher) }
        runCurrent()
        assertNull(engine.resume(launcher, true))
        assertEquals(2, opened.size)
        assertEquals(opened[0].first, opened[1].first)
        assertFalse(opened[0].second)
        assertTrue(opened[1].second)
        engine.handleCallback(callback(store))
        result.await()
        assertEquals(1, http.creates)
    }

    @Test fun coldCallbackPersistsAndResumeUsesExistingRequest() = runTest {
        val store = MemoryStore()
        val http = FakeHttp()
        val original = AuthorizationEngine(configuration, store, http, AuthorizationRuntime())
        val interrupted = async { runCatching { original.authorize(null) { _, _ -> throw KsuserAuthException(KsuserAuthErrorCode.LAUNCH_FAILED) } } }
        runCurrent()
        assertEquals(KsuserAuthErrorCode.LAUNCH_FAILED, error(interrupted.await()))
        val restarted = AuthorizationEngine(configuration, store, http, AuthorizationRuntime())
        assertTrue(restarted.handleCallback(callback(store)))
        val result = restarted.resume({ _, _ -> fail("Callback already received") }, false)
        assertTrue(result is KsuserAuthorizationResult.OAuth)
        assertEquals(1, http.creates)
        assertEquals(1, http.exchanges)
        assertNull(store.load())
    }

    @Test fun jwksOutageRetainsTokensAndNeverRedeemsAgain() = runTest {
        val store = MemoryStore()
        val http = FakeHttp().apply { verifyError = KsuserAuthException(KsuserAuthErrorCode.SERVER, 503) }
        val official = configuration.copy(mode = KsuserAuthorizationMode.OFFICIAL)
        val engine = AuthorizationEngine(official, store, http, AuthorizationRuntime())
        val result = async { runCatching { engine.authorize(null) { _, _ -> } } }
        runCurrent()
        engine.handleCallback(callback(store))
        assertEquals(KsuserAuthErrorCode.SERVER, error(result.await()))
        assertNotNull(store.load()!!.tokens)
        assertNull(store.load()!!.code)
        assertEquals(listOf("openid", "profile"), http.requestedScopes)
        assertTrue(http.createdNonce!!.length >= 22)
        http.verifyError = null
        val restarted = AuthorizationEngine(official, store, http, AuthorizationRuntime())
        assertEquals("verified-subject", (restarted.resume({ _, _ -> }, false) as KsuserAuthorizationResult.Official).subject)
        assertEquals(1, http.exchanges)
        assertEquals(2, http.officialChecks)
    }

    @Test fun transientExchangeFailureCanResumeAndDenialIsTerminal() = runTest {
        val store = MemoryStore()
        val http = FakeHttp().apply { exchangeError = KsuserAuthException(KsuserAuthErrorCode.NETWORK) }
        val engine = AuthorizationEngine(configuration, store, http, AuthorizationRuntime())
        val result = async { runCatching { engine.authorize(null) { _, _ -> } } }
        runCurrent()
        engine.handleCallback(callback(store))
        assertEquals(KsuserAuthErrorCode.NETWORK, error(result.await()))
        assertNotNull(store.load()!!.code)
        http.exchangeError = null
        assertNotNull(engine.resume({ _, _ -> }, false))
        val rejected = async { runCatching { engine.authorize(null) { _, _ -> } } }
        runCurrent()
        val saved = store.load()!!
        engine.handleCallback(saved.redirectUri + "?error=access_denied&state=" + saved.state)
        val failure = rejected.await().exceptionOrNull() as KsuserAuthException
        assertEquals("access_denied", failure.serverErrorCode)
        assertNull(store.load())
    }

    @Test fun timeoutClearsAndOldConfigurationDoesNotBlockAfterExpiry() = runTest {
        val store = MemoryStore()
        val http = FakeHttp()
        val engine = AuthorizationEngine(configuration, store, http, AuthorizationRuntime(), now = { testScheduler.currentTime })
        val result = async { runCatching { engine.authorize(null) { _, _ -> } } }
        runCurrent()
        val oldCallback = callback(store)
        advanceTimeBy(300_001)
        runCurrent()
        assertEquals(KsuserAuthErrorCode.EXPIRED, error(result.await()))
        assertNull(store.load())
        assertFalse(engine.handleCallback(oldCallback))
        assertNull(engine.resume({ _, _ -> }, false))
    }

    @Test fun explicitCancelCanClearUnreadableColdStartStorage() = runTest {
        val store = MemoryStore().apply { corrupt = true }
        val engine = AuthorizationEngine(configuration, store, FakeHttp(), AuthorizationRuntime())
        assertEquals(KsuserAuthErrorCode.STORAGE, error(runCatching { engine.resume({ _, _ -> }, false) }))
        // The diagnostic is retained, but cancel clears the unusable record so a new login is possible.
        assertEquals(KsuserAuthErrorCode.STORAGE, error(runCatching { engine.cancel() }))
        assertNull(store.load())
        assertNull(engine.resume({ _, _ -> }, false))
    }

    @Test fun failureToPersistNeverLaunchesAndCancelsTheCreatedTicket() = runTest {
        val store = MemoryStore().apply { failSave = true }
        val http = FakeHttp()
        val engine = AuthorizationEngine(configuration, store, http, AuthorizationRuntime())
        val outcome = runCatching { engine.authorize(null) { _, _ -> fail("Must persist before launching") } }
        assertEquals(KsuserAuthErrorCode.STORAGE, error(outcome))
        assertEquals(1, http.cancelled)
        assertNull(store.load())
    }
}
