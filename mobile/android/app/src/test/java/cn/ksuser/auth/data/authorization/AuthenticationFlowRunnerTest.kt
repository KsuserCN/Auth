package cn.ksuser.auth.data.authorization

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AuthenticationFlowRunnerTest {
    @Test fun cancelledCredentialProviderCannotVerifyOrReplaceNewerUiState() = runTest {
        var discarded = 0
        val runner = AuthenticationFlowRunner(this) { discarded++ }
        val provider = CompletableDeferred<String>()
        var verified = false
        val results = mutableListOf<String>()
        val errors = mutableListOf<Throwable>()
        runner.launch(action = {
            // Model a platform provider whose callback still arrives after its coroutine was cancelled.
            val credential = withContext(NonCancellable) { provider.await() }
            checkpoint()
            verified = true
            credential
        }, success = { results += it }, failure = errors::add)
        runCurrent()
        runner.cancel()
        runner.launch(action = { "new-account" }, success = { results += it }, failure = errors::add)
        runCurrent()
        provider.complete("cancelled-account")
        runCurrent()
        assertFalse(verified)
        assertEquals(listOf("new-account"), results)
        assertTrue(errors.isEmpty())
        assertEquals(1, discarded)
    }

    @Test fun duplicateCredentialActionCannotStartWhileFirstFlowAwaitsProfile() = runTest {
        val profile = CompletableDeferred<String>()
        val runner = AuthenticationFlowRunner(this) {}
        val results = mutableListOf<String>()
        assertTrue(runner.launch(action = { profile.await() }, success = { results += it }, failure = { throw it }))
        runCurrent()
        assertFalse(runner.launch(action = { "duplicate" }, success = { results += it }, failure = { throw it }))
        profile.complete("verified-account")
        runCurrent()
        assertEquals(listOf("verified-account"), results)
        assertFalse(runner.isRunning)
        assertTrue(runner.launch(action = { "next" }, success = { results += it }, failure = { throw it }))
        runCurrent()
        assertEquals(listOf("verified-account", "next"), results)
    }
}
