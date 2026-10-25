package cn.ksuser.demo.auth

import cn.ksuser.auth.sdk.KsuserAuthErrorCode
import cn.ksuser.auth.sdk.KsuserAuthorizationMode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DemoLoginControllerTest {
    private val oauthResult = DemoAuthorizationOutcome(KsuserAuthorizationMode.OAUTH, "app-openid", "developer-unionid", 3600)
    private val officialResult = DemoAuthorizationOutcome(KsuserAuthorizationMode.OFFICIAL, "verified-subject", expiresIn = 1800)

    private class FakeSession : DemoAuthorizationSession<Unit> {
        var authorizations = 0
        var resumes = 0
        var webContinuations = 0
        var cancellations = 0
        var acceptCallback = true
        var requestedScopes: List<String>? = null
        val authorizationResult = CompletableDeferred<DemoAuthorizationOutcome>()
        var resumeResult: DemoAuthorizationOutcome? = null
        var resumeFailure: Exception? = null
        var cancelGate: CompletableDeferred<Unit>? = null
        var webGate: CompletableDeferred<Unit>? = null
        override suspend fun authorize(host: Unit, scopes: List<String>): DemoAuthorizationOutcome {
            authorizations++
            requestedScopes = scopes
            return authorizationResult.await()
        }
        override suspend fun resume(host: Unit, useWebFallback: Boolean): DemoAuthorizationOutcome? {
            resumes++
            if (useWebFallback) {
                webContinuations++
                webGate?.await()
            }
            resumeFailure?.let { throw it }
            return resumeResult
        }
        override fun handleCallback(url: String): Boolean = acceptCallback
        override suspend fun cancel() { cancellations++; cancelGate?.await() }
    }

    private fun TestScope.controller(
        session: FakeSession,
        configured: Boolean = true,
        initialMode: KsuserAuthorizationMode = KsuserAuthorizationMode.OAUTH,
        factory: (KsuserAuthorizationMode) -> DemoAuthorizationSession<Unit> = { session },
        persist: (KsuserAuthorizationMode) -> Unit = {},
    ) = DemoLoginController(backgroundScope, initialMode, { configured },
        { mode -> if (mode == KsuserAuthorizationMode.OAUTH) listOf("profile") else listOf("openid", "profile") }, factory, persist)

    @Test fun unconfiguredModeCannotCreateClientOrStartAnyAuthorization() = runTest {
        val session = FakeSession()
        var created = 0
        val controller = controller(session, configured = false, factory = { created++; session })
        controller.authorize(Unit)
        controller.resume(Unit)
        controller.resume(Unit, web = true)
        controller.cancel()
        assertFalse(controller.callback("https://example.test/callback", Unit))
        runCurrent()
        assertEquals(0, created)
        assertEquals(0, session.authorizations)
        assertEquals(0, session.resumes)
        assertEquals(0, session.cancellations)
        assertFalse(controller.state.value.canAuthorize)
        assertFalse(controller.state.value.canContinueWeb)
        assertEquals(DemoStatus.NEEDS_CONFIGURATION, controller.state.value.status)
    }

    @Test fun waitingGuardsDuplicateActionsAndHotCallbackKeepsOriginalWaiter() = runTest {
        val session = FakeSession()
        val controller = controller(session)
        controller.authorize(Unit)
        // Calls arriving before the coroutine starts must also be guarded.
        controller.authorize(Unit)
        controller.resume(Unit)
        controller.selectMode(KsuserAuthorizationMode.OFFICIAL)
        assertTrue(controller.state.value.waiting)
        assertFalse(controller.state.value.canSelectMode)
        assertFalse(controller.state.value.canAuthorize)
        assertFalse(controller.state.value.canResume)
        assertTrue(controller.state.value.canContinueWeb)
        runCurrent()
        assertEquals(1, session.authorizations)
        assertEquals(listOf("profile"), session.requestedScopes)
        assertTrue(controller.callback("accepted-hot-callback", Unit))
        assertTrue(controller.callback("duplicate-hot-callback", Unit))
        runCurrent()
        assertEquals(0, session.resumes)
        assertEquals(KsuserAuthorizationMode.OAUTH, controller.state.value.mode)
        session.authorizationResult.complete(oauthResult)
        runCurrent()
        assertFalse(controller.state.value.waiting)
        assertEquals(DemoStatus.OAUTH_SUCCESS, controller.state.value.status)
        assertEquals("app-openid", controller.state.value.identity)
        assertEquals("developer-unionid", controller.state.value.unionId)
        assertEquals(3600L, controller.state.value.expiresIn)
    }

    @Test fun coldCallbackStartsOneResumeAndRejectedCallbackDoesNothing() = runTest {
        val session = FakeSession().apply { resumeResult = officialResult }
        val controller = controller(session, initialMode = KsuserAuthorizationMode.OFFICIAL)
        session.acceptCallback = false
        assertFalse(controller.callback("unrelated-url", Unit))
        assertEquals(0, session.resumes)
        session.acceptCallback = true
        assertTrue(controller.callback("accepted-cold-callback", Unit))
        assertTrue(controller.callback("duplicate-before-resume-starts", Unit))
        runCurrent()
        assertEquals(1, session.resumes)
        assertEquals(0, session.authorizations)
        assertEquals(DemoStatus.OFFICIAL_SUCCESS, controller.state.value.status)
        assertEquals("verified-subject", controller.state.value.identity)
        assertNull(controller.state.value.unionId)
    }

    @Test fun manualWebContinuationKeepsOriginalResultOwnerAndPreventsDoubleLaunch() = runTest {
        val session = FakeSession().apply {
            // Even if an adapter unexpectedly provides a result, the existing waiter remains authoritative.
            resumeResult = oauthResult.copy(identity = "must-not-replace-original-result")
            webGate = CompletableDeferred()
        }
        val controller = controller(session)
        controller.authorize(Unit)
        runCurrent()
        controller.resume(Unit, web = true)
        controller.resume(Unit, web = true)
        assertTrue(controller.state.value.continuingWeb)
        assertFalse(controller.state.value.canContinueWeb)
        runCurrent()
        assertEquals(1, session.webContinuations)
        assertTrue(controller.state.value.waiting)
        session.webGate!!.complete(Unit)
        runCurrent()
        assertNull(controller.state.value.identity)
        assertTrue(controller.state.value.waiting)
        assertFalse(controller.state.value.continuingWeb)
        session.authorizationResult.complete(oauthResult)
        runCurrent()
        assertEquals("app-openid", controller.state.value.identity)
        assertEquals(1, session.authorizations)
        assertEquals(1, session.resumes)
    }

    @Test fun cancelIsSerializedAndLateOriginalResultCannotRestoreSuccess() = runTest {
        val session = FakeSession().apply { cancelGate = CompletableDeferred() }
        val controller = controller(session)
        controller.authorize(Unit)
        runCurrent()
        controller.cancel()
        controller.cancel()
        controller.authorize(Unit)
        controller.resume(Unit, web = true)
        controller.selectMode(KsuserAuthorizationMode.OFFICIAL)
        assertTrue(controller.state.value.cancelling)
        assertFalse(controller.state.value.canCancel)
        runCurrent()
        assertEquals(1, session.cancellations)
        // Simulate a callback winning the network race after the user has already cancelled locally.
        session.authorizationResult.complete(oauthResult)
        runCurrent()
        assertNull(controller.state.value.identity)
        assertTrue(controller.state.value.cancelling)
        session.cancelGate!!.complete(Unit)
        runCurrent()
        assertEquals(DemoStatus.CANCELLED, controller.state.value.status)
        assertFalse(controller.state.value.waiting)
        assertFalse(controller.state.value.cancelling)
        assertNull(controller.state.value.identity)
        assertNull(controller.state.value.unionId)
        assertNull(controller.state.value.expiresIn)
        assertEquals(KsuserAuthorizationMode.OAUTH, controller.state.value.mode)
    }

    @Test fun modeSwitchCancelsOldConfigurationBeforeReplacingAndPersistingIt() = runTest {
        val old = FakeSession().apply { cancelGate = CompletableDeferred() }
        val next = FakeSession().apply { resumeResult = officialResult }
        val created = mutableListOf<KsuserAuthorizationMode>()
        val persisted = mutableListOf<KsuserAuthorizationMode>()
        val controller = controller(old, factory = { mode ->
            created += mode
            if (mode == KsuserAuthorizationMode.OAUTH) old else next
        }, persist = { persisted += it })
        controller.selectMode(KsuserAuthorizationMode.OFFICIAL)
        controller.selectMode(KsuserAuthorizationMode.OFFICIAL)
        runCurrent()
        assertTrue(controller.state.value.cancelling)
        assertEquals(KsuserAuthorizationMode.OAUTH, controller.state.value.mode)
        assertEquals(listOf(KsuserAuthorizationMode.OAUTH), created)
        assertTrue(persisted.isEmpty())
        old.cancelGate!!.complete(Unit)
        runCurrent()
        assertEquals(1, old.cancellations)
        assertEquals(listOf(KsuserAuthorizationMode.OAUTH, KsuserAuthorizationMode.OFFICIAL), created)
        assertEquals(listOf(KsuserAuthorizationMode.OFFICIAL), persisted)
        assertEquals(KsuserAuthorizationMode.OFFICIAL, controller.state.value.mode)
        assertFalse(controller.state.value.cancelling)
        assertNull(controller.state.value.identity)
        controller.resume(Unit)
        runCurrent()
        assertEquals("verified-subject", controller.state.value.identity)
    }

    @Test fun noPendingAndUnexpectedFailureUseSafeStructuredState() = runTest {
        val session = FakeSession()
        val controller = controller(session)
        controller.resume(Unit)
        runCurrent()
        assertEquals(DemoStatus.NO_PENDING, controller.state.value.status)
        assertFalse(controller.state.value.waiting)
        session.resumeFailure = IllegalStateException("access_token=do-not-display; https://callback/?code=secret")
        controller.resume(Unit)
        runCurrent()
        assertEquals(DemoFailureKind.UNEXPECTED, controller.state.value.failure!!.kind)
        assertFalse(controller.state.value.toString().contains("do-not-display"))
        assertFalse(controller.state.value.toString().contains("code=secret"))
        assertTrue(controller.state.value.canResume)
    }

    @Test fun stableSdkErrorsAreMappedWithoutRawDiagnostics() {
        assertEquals(DemoFailureKind.ACCESS_DENIED, demoFailure(KsuserAuthErrorCode.SERVER, 403, "access_denied").kind)
        assertEquals(DemoFailureKind.NETWORK, demoFailure(KsuserAuthErrorCode.NETWORK, null, null).kind)
        assertEquals(DemoFailureKind.STORAGE, demoFailure(KsuserAuthErrorCode.STORAGE, null, null).kind)
        assertEquals(DemoFailureKind.ID_TOKEN, demoFailure(KsuserAuthErrorCode.INVALID_ID_TOKEN, null, null).kind)
        val safe = demoFailure(KsuserAuthErrorCode.SERVER, 503, "NATIVE_AUTHORIZATION_UNAVAILABLE")
        assertEquals(503, safe.httpStatus)
        assertEquals("NATIVE_AUTHORIZATION_UNAVAILABLE", safe.serverCode)
        assertNull(demoFailure(KsuserAuthErrorCode.SERVER, -1, "https://callback/?code=secret").serverCode)
        assertNull(demoFailure(KsuserAuthErrorCode.SERVER, -1, "https://callback/?code=secret").httpStatus)
    }
}
