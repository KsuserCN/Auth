package cn.ksuser.auth.ui

import android.app.Activity
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import cn.ksuser.auth.core.settings.L10n
import cn.ksuser.auth.core.network.ApiException
import cn.ksuser.auth.core.env.EnvironmentProvider
import cn.ksuser.auth.data.authorization.AuthenticationFlowRunner
import cn.ksuser.auth.data.authorization.AuthorizationGrantMode
import cn.ksuser.auth.data.authorization.AuthorizationQrPreview
import cn.ksuser.auth.data.authorization.MobileAuthorizationContext
import cn.ksuser.auth.data.authorization.MobileAuthorizationLinks
import cn.ksuser.auth.data.authorization.PendingNativeAuthorization
import cn.ksuser.auth.data.AppContainer
import cn.ksuser.auth.data.model.AuthResult
import cn.ksuser.auth.data.model.AuthSource
import cn.ksuser.auth.data.model.PasswordRequirement
import cn.ksuser.auth.data.model.QrScanPreview
import cn.ksuser.auth.data.model.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

data class AppUiState(
    val isBootstrapping: Boolean = true,
    val isBusy: Boolean = false,
    val isAuthenticated: Boolean = false,
    val currentUser: UserProfile? = null,
    val passwordRequirement: PasswordRequirement? = null,
    val pendingMfa: AuthResult.NeedsMfa? = null,
    val pendingOAuthBind: PendingOAuthBind? = null,
    val pendingMobileBridgeConfirmation: PendingMobileBridgeConfirmation? = null,
    val mobileBridgeReturnUrl: String? = null,
    val pendingQrConfirmation: PendingQrConfirmation? = null,
    val nativeAuthorizationReturn: PendingNativeAuthorization? = null,
    val nativeReturnOpenRequested: Boolean = false,
    val message: String? = null,
    val error: String? = null,
    val errorDescriptor: ApiException? = null,
) {
    val localizedError: String? get() = if (error == null) null else errorDescriptor?.message ?: L10n.text(error)
}

data class PendingOAuthBind(
    val provider: String,
    val bindToken: String,
    val openid: String? = null,
    val message: String? = null,
)

data class PendingMobileBridgeConfirmation(
    val challengeId: String,
    val returnUrl: String? = null,
    val returnOrigin: String? = null,
    val requiresLogin: Boolean = false,
    val expiresAtMillis: Long = Long.MAX_VALUE,
)

data class PendingQrConfirmation(
    val type: QrConfirmationType,
    val code: String,
    val preview: QrScanPreview? = null,
    val operationHint: String? = null,
    val authorization: MobileAuthorizationContext? = null,
    val authorizationPreview: AuthorizationQrPreview? = null,
    val expiresAtMillis: Long = Long.MAX_VALUE,
    val grantMode: AuthorizationGrantMode = AuthorizationGrantMode.PERSISTENT,
    val grantTtlSeconds: Int = 3600,
    val loginWeb: Boolean = false,
)

enum class QrConfirmationType {
    APPROVE_LOGIN,
    APPROVE_MFA,
    APPROVE_SENSITIVE,
    APPROVE_RECOVERY,
    APPROVE_APPLICATION,
    APPROVE_OFFICIAL,
    APPROVE_CONSOLE_LOGIN,
    LOGIN_THIS_PHONE,
    SWITCH_AND_LOGIN_THIS_PHONE,
}

class AppViewModel(
    private val container: AppContainer,
) : ViewModel() {
    private val _uiState = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()
    private val webOrigin = EnvironmentProvider.current.passkeyOriginHint
    private var pendingLoginRequest: MobileAuthorizationLinks.Request? = null
    private var decisionInFlight = false
    private var requestGeneration = 0L
    private val authenticationFlow = AuthenticationFlowRunner(viewModelScope, container.authRepository::cancelAuthentication)

    init {
        bootstrap()
    }

    fun bootstrap() {
        if (authenticationFlow.isRunning || decisionInFlight || _uiState.value.isBusy) return
        viewModelScope.launch {
            _uiState.update { it.copy(isBootstrapping = true, errorDescriptor = null, error = null) }
            val requirement = runCatching { container.authRepository.getPasswordRequirement() }.getOrNull()
            runCatching {
                val token = container.sessionRepository.bootstrap()
                val user = if (!token.isNullOrBlank()) container.authRepository.getCurrentUser()
                    else container.sessionRepository.cachedUser().takeIf { container.sessionRepository.hasCredentials() }
                _uiState.update { it.copy(isBootstrapping = false, isAuthenticated = user != null,
                    currentUser = user, passwordRequirement = requirement ?: it.passwordRequirement) }
                restoreNativeAuthorization(user)
                if (user != null) resumePendingRequest()
            }.onFailure { throwable ->
                val cached = container.sessionRepository.cachedUser().takeIf { container.sessionRepository.hasCredentials() }
                _uiState.update { it.copy(isBootstrapping = false, isAuthenticated = cached != null,
                    currentUser = cached, passwordRequirement = requirement ?: it.passwordRequirement,
                    errorDescriptor = throwable as? ApiException, error = throwable.toReadableMessage()) }
                restoreNativeAuthorization(cached)
            }
        }
    }

    fun clearTransientMessages() {
        _uiState.update { it.copy(message = null, errorDescriptor = null, error = null) }
    }

    fun synchronizeStoredSession() {
        if (!_uiState.value.isAuthenticated || container.sessionRepository.hasCredentials()) return
        authenticationFlow.cancel()
        // Session expiry requires sign-in again, while the undecided native ticket remains valid.
        // Explicit logout clears the saved request before invalidating the session.
        val native = container.nativeAuthorizationStateStore.read()?.takeIf { it.returnUrl == null }
        pendingLoginRequest = native?.let { MobileAuthorizationLinks.Request.Native(it.ticket) }
        if (native == null) container.nativeAuthorizationStateStore.clear()
        requestGeneration++
        _uiState.update { AppUiState(isBootstrapping = false, passwordRequirement = it.passwordRequirement,
            message = if (native != null) L10n.text("请先登录，登录后将继续确认原请求") else null) }
    }

    fun consumeMobileBridgeReturnUrl() {
        _uiState.update { it.copy(mobileBridgeReturnUrl = null) }
    }

    fun dismissMobileBridgeForLogin() {
        val pending = _uiState.value.pendingMobileBridgeConfirmation ?: return
        pendingLoginRequest = MobileAuthorizationLinks.Request.Bridge(pending.challengeId)
        _uiState.update { it.copy(pendingMobileBridgeConfirmation = null, message = L10n.text("请先登录，完成后将继续原网页登录请求")) }
    }

    fun refreshCurrentUser() {
        viewModelScope.launch {
            runCatching {
                val user = container.authRepository.getCurrentUser()
                _uiState.update { state -> state.copy(isAuthenticated = true, currentUser = user) }
            }.onFailure { throwable ->
                _uiState.update { it.copy(errorDescriptor = throwable as? ApiException, error = throwable.toReadableMessage()) }
            }
        }
    }

    fun handleIncomingDeepLink(uri: Uri) {
        val request = MobileAuthorizationLinks.deepLink(uri.toString(), webOrigin)
        if (request == null) {
            _uiState.update { it.copy(errorDescriptor = null, error = L10n.text("链接无效或不受信任")) }
            return
        }
        dispatchIncomingRequest(request)
    }

    private fun dispatchIncomingRequest(request: MobileAuthorizationLinks.Request, continuingAfterLogin: Boolean = false) {
        val visible = _uiState.value
        if (request is MobileAuthorizationLinks.Request.Native &&
            ((!continuingAfterLogin && pendingLoginRequest == request)
                || visible.isBusy && container.nativeAuthorizationStateStore.read()?.ticket == request.ticket
                || visible.pendingQrConfirmation?.authorization?.let { it.nativeAuthorization && it.ticket == request.ticket } == true
                || visible.nativeAuthorizationReturn?.ticket == request.ticket)) return
        if (decisionInFlight || (authenticationFlow.isRunning && !continuingAfterLogin) || visible.isBusy || visible.pendingMfa != null || visible.pendingQrConfirmation != null || visible.pendingMobileBridgeConfirmation != null || visible.nativeAuthorizationReturn != null) {
            _uiState.update { it.copy(errorDescriptor = null, error = L10n.text("请先完成当前请求")) }
            return
        }
        if (pendingLoginRequest != null && pendingLoginRequest != request) {
            _uiState.update { it.copy(errorDescriptor = null, error = L10n.text("请先完成登录后待处理的请求")) }
            return
        }
        when (request) {
            is MobileAuthorizationLinks.Request.Bridge -> previewMobileBridge(request.challengeId)
            is MobileAuthorizationLinks.Request.Application -> {
                if (!visible.isAuthenticated) deferUntilLogin(request) else previewApplication(request.ticket)
            }
            is MobileAuthorizationLinks.Request.Native -> {
                if (!visible.isAuthenticated) deferUntilLogin(request) else previewNativeApplication(request.ticket)
            }
            is MobileAuthorizationLinks.Request.Approval -> {
                if (!visible.isAuthenticated) deferUntilLogin(request) else previewApproveQr(request.code)
            }
            is MobileAuthorizationLinks.Request.Transfer -> previewTransferQr(request.code)
        }
    }

    private fun deferUntilLogin(request: MobileAuthorizationLinks.Request) {
        if (request is MobileAuthorizationLinks.Request.Native && container.nativeAuthorizationStateStore.read()?.ticket != request.ticket) {
            container.nativeAuthorizationStateStore.save(PendingNativeAuthorization(request.ticket, System.currentTimeMillis() + 300_000))
        }
        pendingLoginRequest = request
        _uiState.update { it.copy(message = L10n.text("请先登录，登录后将继续确认原请求")) }
    }

    private fun resumePendingRequest() {
        val request = pendingLoginRequest ?: return
        val state = _uiState.value
        if (state.isBusy || state.pendingMfa != null || state.pendingQrConfirmation != null || state.pendingMobileBridgeConfirmation != null) return
        pendingLoginRequest = null
        dispatchIncomingRequest(request, continuingAfterLogin = true)
    }

    private fun restoreNativeAuthorization(user: UserProfile?) {
        if (_uiState.value.pendingQrConfirmation != null || _uiState.value.nativeAuthorizationReturn != null) return
        val saved = container.nativeAuthorizationStateStore.read() ?: return
        if (saved.returnUrl != null) {
            // A decided callback belongs to the account which approved it, including after process recreation.
            if (pendingLoginRequest is MobileAuthorizationLinks.Request.Native) pendingLoginRequest = null
            if (saved.accountUuid == user?.uuid) _uiState.update { it.copy(nativeAuthorizationReturn = saved) }
            else container.nativeAuthorizationStateStore.clear()
        } else if (pendingLoginRequest == null) {
            pendingLoginRequest = MobileAuthorizationLinks.Request.Native(saved.ticket)
            if (user == null) _uiState.update { it.copy(message = L10n.text("请先登录，登录后将继续确认原请求")) }
        }
    }

    fun requestNativeReturn() {
        val saved = _uiState.value.nativeAuthorizationReturn ?: return
        if (saved.expiresAtMillis <= System.currentTimeMillis()) {
            closeNativeReturn()
            _uiState.update { it.copy(errorDescriptor = null, error = L10n.text("应用授权返回已过期，请重新发起")) }
            return
        }
        _uiState.update { it.copy(nativeReturnOpenRequested = true, errorDescriptor = null, error = null) }
    }

    fun nativeReturnAttempted(opened: Boolean) {
        _uiState.update { it.copy(nativeReturnOpenRequested = false,
            error = if (opened) null else L10n.text("无法返回调用应用，请检查链接关联后重试"), errorDescriptor = null) }
    }

    fun closeNativeReturn() {
        container.nativeAuthorizationStateStore.clear()
        _uiState.update { it.copy(nativeAuthorizationReturn = null, nativeReturnOpenRequested = false, error = null, errorDescriptor = null) }
    }

    private fun previewMobileBridge(challengeId: String) {
        val generation = ++requestGeneration
        _uiState.update { it.copy(isBusy = true, errorDescriptor = null, error = null, message = null) }
        viewModelScope.launch {
            runCatching {
                val authenticated = resolveBridgeAuthentication()
                val status = container.authRepository.getMobileBridgeStatus(challengeId)
                require(status.expiresInSeconds > 0) { L10n.text("网页登录请求已过期") }
                when (status.status.lowercase()) {
                    "pending" -> PendingMobileBridgeConfirmation(challengeId,
                        MobileAuthorizationLinks.bridgeReturn(status.returnUrl, webOrigin), status.returnOrigin,
                        !authenticated, System.currentTimeMillis() + status.expiresInSeconds * 1000)
                    "approved" -> {
                        val returnUrl = MobileAuthorizationLinks.bridgeReturn(status.returnUrl, webOrigin)
                        requireNotNull(returnUrl) { L10n.text("网页登录返回地址无效") }
                        if (generation == requestGeneration) _uiState.update { it.copy(mobileBridgeReturnUrl = returnUrl) }
                        null
                    }
                    "cancelled" -> error(L10n.text("当前网页登录请求已取消"))
                    else -> error(L10n.text("当前网页登录请求已过期，请回到浏览器重试"))
                }
            }.onSuccess { pending ->
                if (generation == requestGeneration) _uiState.update { it.copy(isBusy = false, pendingMobileBridgeConfirmation = pending) }
            }.onFailure { error ->
                if (generation == requestGeneration) _uiState.update { it.copy(isBusy = false, errorDescriptor = error as? ApiException, error = error.toReadableMessage()) }
            }
        }
    }

    private suspend fun resolveBridgeAuthentication(): Boolean {
        val snapshot = _uiState.value
        if (snapshot.isAuthenticated && snapshot.currentUser != null) {
            return true
        }

        runCatching { container.authRepository.getCurrentUser() }
            .onSuccess { user ->
                _uiState.update {
                    it.copy(
                        isAuthenticated = true,
                        currentUser = user,
                    )
                }
                return true
            }

        val token = runCatching { container.sessionRepository.bootstrap() }.getOrNull()
        if (token.isNullOrBlank()) {
            return false
        }

        val user = runCatching { container.authRepository.getCurrentUser() }.getOrNull() ?: return false
        _uiState.update {
            it.copy(
                isAuthenticated = true,
                currentUser = user,
            )
        }
        return true
    }

    fun confirmMobileBridgeAction() {
        val pending = _uiState.value.pendingMobileBridgeConfirmation ?: return
        if (pending.requiresLogin) { dismissMobileBridgeForLogin(); return }
        if (decisionInFlight || _uiState.value.isBusy) return
        if (pending.expiresAtMillis <= System.currentTimeMillis()) {
            _uiState.update { it.copy(pendingMobileBridgeConfirmation = null, errorDescriptor = null, error = L10n.text("网页登录请求已过期")) }
            return
        }
        decisionInFlight = true
        _uiState.update { it.copy(isBusy = true, errorDescriptor = null, error = null, message = null) }
        viewModelScope.launch {
            runCatching {
                val response = container.authRepository.approveMobileBridge(pending.challengeId)
                requireNotNull(MobileAuthorizationLinks.bridgeReturn(response.returnUrl, webOrigin)) { L10n.text("网页登录返回地址无效") }
            }.onSuccess { returnUrl ->
                _uiState.update { it.copy(isBusy = false, pendingMobileBridgeConfirmation = null,
                    mobileBridgeReturnUrl = returnUrl, message = L10n.text("网页登录已确认，正在返回浏览器")) }
            }.onFailure { error -> _uiState.update { it.copy(isBusy = false, errorDescriptor = error as? ApiException, error = error.toReadableMessage()) } }
            decisionInFlight = false
        }
    }

    fun cancelMobileBridgeAction() {
        val pending = _uiState.value.pendingMobileBridgeConfirmation ?: return
        if (decisionInFlight || _uiState.value.isBusy) return
        pendingLoginRequest = null
        if (pending.requiresLogin || pending.expiresAtMillis <= System.currentTimeMillis()) {
            _uiState.update { it.copy(pendingMobileBridgeConfirmation = null) }
            return
        }
        decisionInFlight = true
        _uiState.update { it.copy(isBusy = true, errorDescriptor = null, error = null, message = null) }
        viewModelScope.launch {
            runCatching { container.authRepository.cancelMobileBridge(pending.challengeId) }
                .onSuccess { _uiState.update { it.copy(isBusy = false, pendingMobileBridgeConfirmation = null, message = L10n.text("网页登录请求已取消")) } }
                .onFailure { error -> _uiState.update { it.copy(isBusy = false, errorDescriptor = error as? ApiException, error = error.toReadableMessage()) } }
            decisionInFlight = false
        }
    }

    fun passwordLogin(email: String, password: String) {
        runAuthAction {
            container.authRepository.passwordLogin(email, password)
        }
    }

    fun sendLoginCode(email: String) {
        runBusyAction(successMessage = L10n.text("验证码已发送")) {
            container.authRepository.sendLoginCode(email)
        }
    }

    fun loginWithCode(email: String, code: String) {
        runAuthAction {
            container.authRepository.loginWithCode(email, code)
        }
    }

    fun sendRegisterCode(email: String) {
        runBusyAction(successMessage = L10n.text("注册验证码已发送")) {
            container.authRepository.sendRegisterCode(email)
        }
    }

    fun sendRegisterCode(
        email: String,
        onSent: () -> Unit,
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, errorDescriptor = null, error = null, message = null) }
            runCatching { container.authRepository.sendRegisterCode(email) }
                .onSuccess {
                    onSent()
                    _uiState.update { it.copy(isBusy = false, message = L10n.text("注册验证码已发送")) }
                }
                .onFailure { throwable ->
                    _uiState.update { it.copy(isBusy = false, errorDescriptor = throwable as? ApiException, error = throwable.toReadableMessage()) }
                }
        }
    }

    fun checkUsernameAndThen(
        username: String,
        onAvailable: () -> Unit,
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, errorDescriptor = null, error = null, message = null) }
            runCatching { container.authRepository.checkUsernameAvailable(username) }
                .onSuccess { available ->
                    if (available) {
                        onAvailable()
                        _uiState.update { it.copy(isBusy = false, message = L10n.text("用户名可用")) }
                    } else {
                        _uiState.update { it.copy(isBusy = false, errorDescriptor = null, error = L10n.text("用户名已被使用")) }
                    }
                }
                .onFailure { throwable ->
                    _uiState.update { it.copy(isBusy = false, errorDescriptor = throwable as? ApiException, error = throwable.toReadableMessage()) }
                }
        }
    }

    fun register(username: String, email: String, password: String, code: String) {
        launchAuthentication(action = {
            container.authRepository.register(username, email, password, code)
            checkpoint()
            container.authRepository.completeAuthentication()
        }, success = { finishAuthentication(it, L10n.text("注册成功")) })
    }

    fun loginWithPasskey(activity: Activity) {
        launchAuthentication(action = {
            val options = container.authRepository.getPasskeyAuthenticationOptions()
            checkpoint()
            val payload = container.passkeyManager.getForAuthentication(activity, options)
            checkpoint()
            container.authRepository.verifyPasskeyLogin(options.challengeId, payload)
        }, success = { completeAuthResult(it, L10n.text("Passkey 登录成功")) })
    }

    fun loginWithSecurityKey(activity: Activity) {
        launchAuthentication(action = {
            check(container.securityKeyManager.available(activity)) { L10n.text("当前设备没有可用的安全密钥提供方") }
            val options = container.authRepository.getPasskeyAuthenticationOptions()
            checkpoint()
            val payload = container.securityKeyManager.getForAuthentication(activity, options)
            checkpoint()
            container.authRepository.verifyPasskeyLogin(options.challengeId, payload)
        }, success = { completeAuthResult(it, L10n.text("安全密钥登录成功")) })
    }

    fun loginWithQq(activity: Activity) {
        launchAuthentication(action = {
            val credential = container.qqLoginManager.login(activity)
            checkpoint()
            container.authRepository.loginWithQqMobile(credential.appId, credential.accessToken,
                credential.openid, credential.unionid, credential.expiresIn)
        }, success = { completeAuthResult(it, L10n.text("QQ 登录成功")) })
    }

    fun verifyTotpMfa(code: String? = null, recoveryCode: String? = null) {
        val pending = _uiState.value.pendingMfa ?: return
        launchAuthentication(action = {
            container.authRepository.verifyTotpMfa(pending.challengeId, code, recoveryCode)
            checkpoint()
            container.authRepository.completeAuthentication()
        }, success = { finishAuthentication(it, L10n.text("MFA 验证成功")) })
    }

    fun verifyPasskeyMfa(activity: Activity) = verifyCredentialMfa(activity, false)
    fun verifySecurityKeyMfa(activity: Activity) = verifyCredentialMfa(activity, true)

    private fun verifyCredentialMfa(activity: Activity, useSecurityKey: Boolean) {
        val pending = _uiState.value.pendingMfa ?: return
        launchAuthentication(action = {
            val options = container.authRepository.getPasskeyAuthenticationOptions()
            checkpoint()
            val payload = if (useSecurityKey) {
                check(container.securityKeyManager.available(activity)) { L10n.text("当前设备没有可用的安全密钥提供方") }
                container.securityKeyManager.getForAuthentication(activity, options)
            } else container.passkeyManager.getForAuthentication(activity, options)
            checkpoint()
            container.authRepository.verifyPasskeyMfa(pending.challengeId, options.challengeId, payload)
            checkpoint()
            container.authRepository.completeAuthentication()
        }, success = { finishAuthentication(it, L10n.text("MFA 验证成功")) })
    }

    fun logout() {
        authenticationFlow.cancel()
        if (_uiState.value.isBusy) return
        _uiState.update { it.copy(isBusy = true) }
        pendingLoginRequest = null
        container.nativeAuthorizationStateStore.clear()
        requestGeneration++
        viewModelScope.launch {
            runCatching { container.authRepository.logout() }
            val current = _uiState.value
            if (!container.sessionRepository.hasCredentials() && !authenticationFlow.isRunning &&
                current.pendingMfa == null && current.pendingOAuthBind == null) {
                _uiState.update { AppUiState(isBootstrapping = false, passwordRequirement = it.passwordRequirement, message = L10n.text("已退出登录")) }
            }
        }
    }

    fun logoutAll() {
        if (_uiState.value.isBusy) return
        authenticationFlow.cancel()
        _uiState.update { it.copy(isBusy = true, message = null, errorDescriptor = null, error = null) }
        viewModelScope.launch {
            runCatching { container.authRepository.logoutAll() }
                .onSuccess {
                    pendingLoginRequest = null
                    container.nativeAuthorizationStateStore.clear()
                    requestGeneration++
                    _uiState.update { AppUiState(isBootstrapping = false, passwordRequirement = it.passwordRequirement, message = L10n.text("已从所有设备退出")) }
                }
                .onFailure { error -> _uiState.update { it.copy(isBusy = false, errorDescriptor = error as? ApiException, error = error.toReadableMessage()) } }
        }
    }

    fun cancelAuthentication() {
        authenticationFlow.cancel()
        pendingLoginRequest = null
        container.nativeAuthorizationStateStore.clear()
        _uiState.update { it.copy(pendingMfa = null, pendingOAuthBind = null, isBusy = false, message = null, errorDescriptor = null, error = null) }
    }

    fun accountDeleted() {
        authenticationFlow.cancel()
        container.sessionRepository.clearSession()
        pendingLoginRequest = null
        container.nativeAuthorizationStateStore.clear()
        requestGeneration++
        _uiState.update { AppUiState(isBootstrapping = false, passwordRequirement = it.passwordRequirement,
            message = L10n.text("账号已注销")) }
    }

    fun handleScannedQr(rawContent: String) {
        val request = MobileAuthorizationLinks.qr(rawContent, webOrigin)
        if (request == null) {
            _uiState.update { it.copy(errorDescriptor = null, error = L10n.text("未识别到受信任的有效二维码，请重试")) }
            return
        }
        dispatchIncomingRequest(request)
    }

    fun updateAuthorizationOptions(mode: AuthorizationGrantMode, ttl: Int, loginWeb: Boolean) {
        val pending = _uiState.value.pendingQrConfirmation ?: return
        if (_uiState.value.isBusy || pending.authorization == null) return
        if (ttl !in setOf(300, 3600, 86400, 604800)) return
        _uiState.update { it.copy(pendingQrConfirmation = pending.copy(grantMode = mode, grantTtlSeconds = ttl, loginWeb = loginWeb)) }
    }

    fun confirmQrAction(proofId: String? = null) {
        val pending = _uiState.value.pendingQrConfirmation ?: return
        if (_uiState.value.isBusy || decisionInFlight) return
        if (pending.expiresAtMillis <= System.currentTimeMillis()) {
            _uiState.update { it.copy(pendingQrConfirmation = null, errorDescriptor = null, error = L10n.text("二维码或授权已过期，请重新发起")) }
            return
        }
        when (pending.type) {
            QrConfirmationType.APPROVE_APPLICATION -> decideApplication(pending, true)
            QrConfirmationType.APPROVE_OFFICIAL -> decideOfficial(pending, true)
            QrConfirmationType.APPROVE_SENSITIVE, QrConfirmationType.APPROVE_RECOVERY -> {
                if (proofId.isNullOrBlank()) {
                    _uiState.update { it.copy(errorDescriptor = null, error = L10n.text("请先完成身份验证")) }
                    return
                }
                approveQrChallenge(pending.code, proofId)
            }
            QrConfirmationType.APPROVE_LOGIN, QrConfirmationType.APPROVE_MFA,
            QrConfirmationType.APPROVE_CONSOLE_LOGIN -> approveQrChallenge(pending.code)
            QrConfirmationType.LOGIN_THIS_PHONE, QrConfirmationType.SWITCH_AND_LOGIN_THIS_PHONE -> exchangeSessionTransferAndLogin(pending.code)
        }
    }

    fun cancelQrAction() {
        val pending = _uiState.value.pendingQrConfirmation ?: return
        if (_uiState.value.isBusy || decisionInFlight) return
        if (pending.expiresAtMillis > System.currentTimeMillis()) {
            when (pending.type) {
                QrConfirmationType.APPROVE_APPLICATION -> { decideApplication(pending, false); return }
                QrConfirmationType.APPROVE_OFFICIAL -> { decideOfficial(pending, false); return }
                else -> Unit
            }
        }
        _uiState.update { it.copy(pendingQrConfirmation = null) }
        if (pending.authorization?.nativeAuthorization == true) container.nativeAuthorizationStateStore.clear()
    }

    private fun previewApproveQr(approveCode: String) {
        val generation = ++requestGeneration
        _uiState.update { it.copy(isBusy = true, message = null, errorDescriptor = null, error = null) }
        viewModelScope.launch {
            runCatching {
                val preview = container.mobileAuthorizationRepository.preview(approveCode = approveCode)
                require(preview.expiresInSeconds > 0) { L10n.text("二维码已过期") }
                val type = when (preview.codeType) {
                    "approve_login" -> QrConfirmationType.APPROVE_LOGIN
                    "approve_mfa" -> QrConfirmationType.APPROVE_MFA
                    "approve_sensitive" -> QrConfirmationType.APPROVE_SENSITIVE
                    "approve_recovery" -> QrConfirmationType.APPROVE_RECOVERY
                    "approve_application" -> QrConfirmationType.APPROVE_APPLICATION
                    "approve_official" -> QrConfirmationType.APPROVE_OFFICIAL
                    "approve_console_login" -> QrConfirmationType.APPROVE_CONSOLE_LOGIN
                    else -> error(L10n.text("不支持的二维码类型，请更新 App"))
                }
                val context = if (type == QrConfirmationType.APPROVE_APPLICATION) {
                    val ticket = requireNotNull(preview.authorizationTicket) { L10n.text("缺少应用授权票据") }
                    container.mobileAuthorizationRepository.context(ticket).also { require(it.qr) { L10n.text("二维码授权类型不匹配") } }
                } else null
                PendingQrConfirmation(type, approveCode, preview.legacyPreview(), authorization = context,
                    authorizationPreview = preview, expiresAtMillis = System.currentTimeMillis() +
                        minOf(preview.expiresInSeconds, context?.expiresInSeconds ?: Long.MAX_VALUE) * 1000)
            }.onSuccess { pending ->
                if (generation == requestGeneration) _uiState.update { it.copy(isBusy = false, pendingQrConfirmation = pending) }
            }.onFailure { error ->
                if (generation == requestGeneration) _uiState.update { it.copy(isBusy = false, errorDescriptor = error as? ApiException, error = error.toReadableMessage()) }
            }
        }
    }

    private fun previewApplication(ticket: String) {
        val generation = ++requestGeneration
        _uiState.update { it.copy(isBusy = true, message = null, errorDescriptor = null, error = null) }
        viewModelScope.launch {
            runCatching {
                val context = container.mobileAuthorizationRepository.context(ticket)
                require(!context.qr) { L10n.text("请扫描原授权二维码继续") }
                PendingQrConfirmation(QrConfirmationType.APPROVE_APPLICATION, ticket, authorization = context,
                    expiresAtMillis = System.currentTimeMillis() + context.expiresInSeconds * 1000)
            }.onSuccess { pending ->
                if (generation == requestGeneration) _uiState.update { it.copy(isBusy = false, pendingQrConfirmation = pending) }
            }.onFailure { error ->
                if (generation == requestGeneration) _uiState.update { it.copy(isBusy = false, errorDescriptor = error as? ApiException, error = error.toReadableMessage()) }
            }
        }
    }

    private fun previewNativeApplication(ticket: String) {
        val generation = ++requestGeneration
        _uiState.update { it.copy(isBusy = true, message = null, errorDescriptor = null, error = null) }
        viewModelScope.launch {
            runCatching {
                val old = container.nativeAuthorizationStateStore.read()
                // Save before network access so process death can continue this same request.
                if (old?.ticket != ticket) container.nativeAuthorizationStateStore.save(
                    PendingNativeAuthorization(ticket, System.currentTimeMillis() + 300_000))
                val context = container.nativeAuthorizationRepository.context(ticket)
                val expiresAt = minOf(container.nativeAuthorizationStateStore.read()?.expiresAtMillis ?: Long.MAX_VALUE,
                    System.currentTimeMillis() + context.expiresInSeconds * 1000)
                container.nativeAuthorizationStateStore.save(PendingNativeAuthorization(ticket, expiresAt))
                PendingQrConfirmation(QrConfirmationType.APPROVE_APPLICATION, ticket,
                    authorization = context.forConsent(), expiresAtMillis = expiresAt)
            }.onSuccess { pending ->
                if (generation == requestGeneration) _uiState.update { it.copy(isBusy = false, pendingQrConfirmation = pending) }
            }.onFailure { error ->
                if (generation == requestGeneration) _uiState.update { it.copy(isBusy = false, errorDescriptor = error as? ApiException, error = error.toReadableMessage()) }
            }
        }
    }

    private fun previewTransferQr(transferCode: String) {
        val generation = ++requestGeneration
        _uiState.update { it.copy(isBusy = true, message = null, errorDescriptor = null, error = null) }
        viewModelScope.launch {
            runCatching {
                val preview = container.mobileAuthorizationRepository.preview(transferCode = transferCode)
                require(preview.codeType == "transfer" && preview.expiresInSeconds > 0) { L10n.text("登录二维码类型无效或已过期") }
                val type = if (_uiState.value.isAuthenticated) QrConfirmationType.SWITCH_AND_LOGIN_THIS_PHONE else QrConfirmationType.LOGIN_THIS_PHONE
                PendingQrConfirmation(type, transferCode, preview.legacyPreview(), authorizationPreview = preview,
                    expiresAtMillis = System.currentTimeMillis() + preview.expiresInSeconds * 1000)
            }.onSuccess { pending ->
                if (generation == requestGeneration) _uiState.update { it.copy(isBusy = false, pendingQrConfirmation = pending) }
            }.onFailure { error ->
                if (generation == requestGeneration) _uiState.update { it.copy(isBusy = false, errorDescriptor = error as? ApiException, error = error.toReadableMessage()) }
            }
        }
    }

    private fun decideApplication(pending: PendingQrConfirmation, approve: Boolean) {
        val context = pending.authorization ?: return
        if (context.nativeAuthorization) { decideNativeApplication(pending, approve); return }
        decisionInFlight = true
        _uiState.update { it.copy(isBusy = true, errorDescriptor = null, error = null, message = null) }
        viewModelScope.launch {
            runCatching {
                val result = container.mobileAuthorizationRepository.decide(context, approve, pending.grantMode, pending.grantTtlSeconds, pending.loginWeb)
                if (context.qr) null else requireNotNull(MobileAuthorizationLinks.applicationReturn(result.returnUrl, webOrigin)) { L10n.text("应用授权返回地址无效") }
            }.onSuccess { returnUrl ->
                _uiState.update { it.copy(isBusy = false, pendingQrConfirmation = null, mobileBridgeReturnUrl = returnUrl,
                    message = if (approve) L10n.text("应用授权已完成") else L10n.text("已拒绝应用授权")) }
            }.onFailure { error -> _uiState.update { it.copy(isBusy = false, errorDescriptor = error as? ApiException, error = error.toReadableMessage()) } }
            decisionInFlight = false
        }
    }

    private fun decideNativeApplication(pending: PendingQrConfirmation, approve: Boolean) {
        val context = pending.authorization ?: return
        val accountUuid = _uiState.value.currentUser?.uuid ?: return
        decisionInFlight = true
        _uiState.update { it.copy(isBusy = true, errorDescriptor = null, error = null, message = null) }
        viewModelScope.launch {
            runCatching {
                val returnUrl = container.nativeAuthorizationRepository.decide(context, approve, pending.grantMode, pending.grantTtlSeconds)
                require(_uiState.value.currentUser?.uuid == accountUuid) { L10n.text("登录账号已变化，请重新发起授权") }
                PendingNativeAuthorization(context.ticket, pending.expiresAtMillis, accountUuid,
                    context.appName, context.redirectUri, returnUrl, approve).also(container.nativeAuthorizationStateStore::save)
            }.onSuccess { result ->
                _uiState.update { it.copy(isBusy = false, pendingQrConfirmation = null,
                    nativeAuthorizationReturn = result, nativeReturnOpenRequested = true) }
            }.onFailure { error -> _uiState.update { it.copy(isBusy = false, errorDescriptor = error as? ApiException, error = error.toReadableMessage()) } }
            decisionInFlight = false
        }
    }

    private fun decideOfficial(pending: PendingQrConfirmation, approve: Boolean) {
        decisionInFlight = true
        _uiState.update { it.copy(isBusy = true, errorDescriptor = null, error = null, message = null) }
        viewModelScope.launch {
            runCatching { container.mobileAuthorizationRepository.decideOfficial(pending.code, approve) }
                .onSuccess { _uiState.update { it.copy(isBusy = false, pendingQrConfirmation = null, message = if (approve) L10n.text("官方应用授权成功") else L10n.text("已拒绝官方应用授权")) } }
                .onFailure { error -> _uiState.update { it.copy(isBusy = false, errorDescriptor = error as? ApiException, error = error.toReadableMessage()) } }
            decisionInFlight = false
        }
    }

    private fun approveQrChallenge(approveCode: String, proofId: String? = null) {
        decisionInFlight = true
        _uiState.update { it.copy(isBusy = true, errorDescriptor = null, error = null, message = null) }
        viewModelScope.launch {
            runCatching { container.authRepository.approveQrChallenge(approveCode, proofId) }
                .onSuccess { _uiState.update { it.copy(isBusy = false, pendingQrConfirmation = null, message = L10n.text("扫码确认成功")) } }
                .onFailure { error -> _uiState.update { it.copy(isBusy = false, errorDescriptor = error as? ApiException, error = error.toReadableMessage()) } }
            decisionInFlight = false
        }
    }

    private fun exchangeSessionTransferAndLogin(transferCode: String) {
        launchAuthentication(action = {
            container.authRepository.exchangeSessionTransferForMobile(transferCode)
            checkpoint()
            container.authRepository.completeAuthentication()
        }, success = { finishAuthentication(it, L10n.text("扫码登录成功")) })
    }

    private fun <T> launchAuthentication(action: suspend AuthenticationFlowRunner.Attempt.() -> T,
                                         success: suspend (T) -> Unit) {
        if (_uiState.value.isBusy || decisionInFlight || authenticationFlow.isRunning) return
        _uiState.update { it.copy(isBusy = true, errorDescriptor = null, error = null, message = null) }
        authenticationFlow.launch(action, success, failure = { error ->
            _uiState.update { it.copy(isBusy = false, errorDescriptor = error as? ApiException, error = error.toReadableMessage()) }
        })
    }

    private fun runAuthAction(action: suspend () -> AuthResult) {
        launchAuthentication(action = { action() }, success = { completeAuthResult(it, L10n.text("登录成功")) })
    }

    private suspend fun completeAuthResult(result: AuthResult, successMessage: String) {
        when (result) {
            is AuthResult.Success -> finishAuthentication(container.authRepository.completeAuthentication(), successMessage)
            is AuthResult.NeedsMfa -> {
                val sourceLabel = when (result.source) {
                    AuthSource.PASSWORD -> L10n.text("密码")
                    AuthSource.EMAIL_CODE -> L10n.text("验证码")
                    AuthSource.PASSKEY -> "Passkey"
                    AuthSource.QQ -> "QQ"
                }
                _uiState.update { it.copy(isBusy = false, pendingMfa = result, pendingMobileBridgeConfirmation = null,
                    mobileBridgeReturnUrl = null, pendingQrConfirmation = null,
                    message = L10n.format("{0} 验证通过，请完成 MFA", sourceLabel)) }
            }
            is AuthResult.NeedsOAuthBind -> {
                val providerLabel = if (result.provider.lowercase() == "qq") "QQ" else L10n.text("第三方")
                _uiState.update { it.copy(isBusy = false, isAuthenticated = false,
                    pendingOAuthBind = PendingOAuthBind(result.provider, result.bindToken, result.openid, result.message),
                    pendingMfa = null, pendingMobileBridgeConfirmation = null, mobileBridgeReturnUrl = null, pendingQrConfirmation = null,
                    message = result.message ?: L10n.format("该 {0} 账号尚未绑定，请注册新账号后继续", providerLabel)) }
            }
        }
    }

    private suspend fun finishAuthentication(user: UserProfile, successMessage: String) {
        currentCoroutineContext().ensureActive()
        // completeAuthentication has committed this account; reflect it before an optional QQ network call.
        _uiState.update { it.copy(isAuthenticated = true, currentUser = user, pendingMfa = null) }
        val bindResult = try { Result.success(bindPendingOAuthIfNeeded()) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Throwable) { Result.failure(error) }
        currentCoroutineContext().ensureActive()
        _uiState.update { it.copy(isBusy = false, isAuthenticated = true, currentUser = user,
            pendingMfa = null, pendingMobileBridgeConfirmation = null, mobileBridgeReturnUrl = null,
            pendingQrConfirmation = null, message = bindResult.getOrNull() ?: successMessage,
            errorDescriptor = bindResult.exceptionOrNull() as? ApiException,
            error = bindResult.exceptionOrNull()?.toReadableMessage()) }
        resumePendingRequest()
    }

    fun clearPendingOAuthBind() {
        _uiState.update { it.copy(pendingOAuthBind = null, message = null, errorDescriptor = null, error = null) }
    }

    private suspend fun bindPendingOAuthIfNeeded(): String? {
        val pending = _uiState.value.pendingOAuthBind ?: return null
        if (pending.provider.lowercase() != "qq") {
            return null
        }
        val result = container.authRepository.bindPendingQqAccount(pending.bindToken)
        _uiState.update { it.copy(pendingOAuthBind = null) }
        return result.message?.takeIf { it.isNotBlank() } ?: L10n.text("注册成功，QQ 账号已绑定")
    }

    override fun onCleared() {
        authenticationFlow.cancel()
        super.onCleared()
    }

    private fun runBusyAction(
        successMessage: String,
        action: suspend () -> Unit,
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, errorDescriptor = null, error = null, message = null) }
            runCatching { action() }
                .onSuccess {
                    _uiState.update { it.copy(isBusy = false, message = successMessage) }
                }
                .onFailure { throwable ->
                    _uiState.update { it.copy(isBusy = false, errorDescriptor = throwable as? ApiException, error = throwable.toReadableMessage()) }
                }
        }
    }
}

class AppViewModelFactory(
    private val container: AppContainer,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return AppViewModel(container) as T
    }
}

private fun Throwable.toReadableMessage(): String {
    return when (this) {
        is ApiException -> message
        else -> message ?: L10n.text("操作失败")
    }
}
