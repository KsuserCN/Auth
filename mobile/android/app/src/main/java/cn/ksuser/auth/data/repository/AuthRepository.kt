package cn.ksuser.auth.data.repository

import cn.ksuser.auth.core.network.ApiEnvelope
import cn.ksuser.auth.core.network.ApiException
import cn.ksuser.auth.core.network.ApiResultCodeResolver
import cn.ksuser.auth.core.network.KsuserApiService
import cn.ksuser.auth.core.network.executeEnvelope
import cn.ksuser.auth.core.network.requireCode
import cn.ksuser.auth.core.session.SessionRepository
import cn.ksuser.auth.core.session.AuthenticationSession
import cn.ksuser.auth.data.model.AuthResult
import cn.ksuser.auth.data.model.AuthSource
import cn.ksuser.auth.data.model.LoginWithCodeRequest
import cn.ksuser.auth.data.model.MobileBridgeApproveRequest
import cn.ksuser.auth.data.model.MobileBridgeApproveResponse
import cn.ksuser.auth.data.model.MobileBridgeStatusPayload
import cn.ksuser.auth.data.model.OAuthBindCallbackResponse
import cn.ksuser.auth.data.model.OAuthBindPendingRequest
import cn.ksuser.auth.data.model.PasskeyAuthenticationPayload
import cn.ksuser.auth.data.model.PasskeyAuthenticationVerifyRequest
import cn.ksuser.auth.data.model.PasswordLoginRequest
import cn.ksuser.auth.data.model.QrApproveRequest
import cn.ksuser.auth.data.model.QrScanPreview
import cn.ksuser.auth.data.model.QqMobileLoginRequest
import cn.ksuser.auth.data.model.PasswordRequirement
import cn.ksuser.auth.data.model.RegisterRequest
import cn.ksuser.auth.data.model.RegisterResponse
import cn.ksuser.auth.data.model.SendCodeRequest
import cn.ksuser.auth.data.model.SessionTransferExchangeRequest
import cn.ksuser.auth.data.model.TotpMfaVerifyRequest
import cn.ksuser.auth.data.model.UserProfile
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AuthRepository(
    private val api: KsuserApiService,
    private val sessionRepository: SessionRepository,
    private val gson: Gson,
) {
    private val stageLock = Any()
    @Volatile private var authenticationSession: AuthenticationSession? = null
    private fun newStage(): AuthenticationSession = synchronized(stageLock) {
        sessionRepository.stage().also { authenticationSession = it }
    }
    private fun currentStage(): AuthenticationSession = authenticationSession ?: newStage()
    private fun requireStage(): AuthenticationSession = authenticationSession ?: error("Authentication was cancelled")
    fun cancelAuthentication() = synchronized(stageLock) { authenticationSession = null }
    private fun storeToken(stage: AuthenticationSession, token: String): String = synchronized(stageLock) {
        check(authenticationSession === stage) { "Authentication was cancelled" }
        require(token.isNotBlank()) { "Missing access token" }
        stage.storage.setAccessToken(token)
        token
    }

    suspend fun getPasswordRequirement(): PasswordRequirement {
        val envelope = executeEnvelope(gson) { api.getPasswordRequirement() }
        requireCode(envelope, 200)
        return envelope.data ?: error("缺少密码策略")
    }

    suspend fun checkUsernameAvailable(username: String): Boolean {
        val envelope = executeEnvelope(gson) { api.checkUsername(username) }
        requireCode(envelope, 200)
        return envelope.data?.exists == false
    }

    suspend fun sendLoginCode(email: String) {
        val envelope = executeEnvelope(gson) { api.sendCode(SendCodeRequest(email = email, type = "login")) }
        requireCode(envelope, 200)
    }

    suspend fun sendRegisterCode(email: String) {
        val envelope = executeEnvelope(gson) { api.sendCode(SendCodeRequest(email = email, type = "register")) }
        requireCode(envelope, 200)
    }

    suspend fun approveQrChallenge(approveCode: String, proofId: String? = null) {
        val envelope = executeEnvelope(gson) { api.approveQrChallenge(QrApproveRequest(approveCode.trim(), proofId)) }
        requireCode(envelope, 200)
    }

    suspend fun getMobileBridgeStatus(challengeId: String): MobileBridgeStatusPayload {
        val envelope = executeEnvelope(gson) { api.getMobileBridgeStatus(challengeId.trim()) }
        requireCode(envelope, 200)
        return envelope.data ?: error("网页登录挑战状态为空")
    }

    suspend fun approveMobileBridge(challengeId: String): MobileBridgeApproveResponse {
        val envelope = executeEnvelope(gson) {
            api.approveMobileBridge(MobileBridgeApproveRequest(challengeId.trim()))
        }
        requireCode(envelope, 200)
        return envelope.data ?: error("网页登录确认结果为空")
    }

    suspend fun cancelMobileBridge(challengeId: String) {
        val envelope = executeEnvelope(gson) {
            api.cancelMobileBridge(cn.ksuser.auth.data.model.MobileBridgeCancelRequest(challengeId.trim()))
        }
        requireCode(envelope, 200)
    }

    suspend fun getQrScanPreview(
        approveCode: String? = null,
        transferCode: String? = null,
    ): QrScanPreview {
        val envelope = executeEnvelope(gson) {
            api.getQrScanPreview(
                approveCode = approveCode?.trim()?.takeIf { it.isNotBlank() },
                transferCode = transferCode?.trim()?.takeIf { it.isNotBlank() },
            )
        }
        requireCode(envelope, 200)
        return envelope.data ?: error("二维码预览信息为空")
    }

    suspend fun exchangeSessionTransferForMobile(transferCode: String): String {
        val stage = newStage()
        val envelope = executeEnvelope(gson) {
            stage.api.exchangeSessionTransfer(
                SessionTransferExchangeRequest(
                    transferCode = transferCode.trim(),
                    target = "mobile",
                ),
            )
        }
        requireCode(envelope, 200)
        return envelope.data?.accessToken
            ?.also { storeToken(stage, it) }
            ?: error("跨端登录 AccessToken 缺失")
    }

    suspend fun passwordLogin(email: String, password: String): AuthResult {
        val stage = newStage()
        val envelope = executeEnvelope(gson) { stage.api.login(PasswordLoginRequest(email, password)) }
        return parseAuthEnvelope(envelope, AuthSource.PASSWORD, stage)
    }

    suspend fun loginWithCode(email: String, code: String): AuthResult {
        val stage = newStage()
        val envelope = executeEnvelope(gson) { stage.api.loginWithCode(LoginWithCodeRequest(email, code)) }
        return parseAuthEnvelope(envelope, AuthSource.EMAIL_CODE, stage)
    }

    suspend fun loginWithQqMobile(
        appId: String,
        accessToken: String,
        openid: String,
        unionid: String,
        expiresIn: String?,
    ): AuthResult {
        val stage = newStage()
        val envelope = executeEnvelope(gson) {
            stage.api.loginWithQqMobile(
                QqMobileLoginRequest(
                    appId = appId,
                    accessToken = accessToken,
                    openid = openid,
                    unionid = unionid,
                    expiresIn = expiresIn,
                ),
            )
        }
        return parseAuthEnvelope(envelope, AuthSource.QQ, stage)
    }

    suspend fun bindPendingQqAccount(bindToken: String): OAuthBindCallbackResponse {
        val envelope = executeEnvelope(gson) {
            api.bindPendingQqAccount(
                OAuthBindPendingRequest(
                    provider = "qq",
                    bindToken = bindToken.trim(),
                ),
            )
        }
        requireCode(envelope, 200)
        return (envelope.data ?: OAuthBindCallbackResponse(bound = true, provider = "qq"))
            .copy(message = envelope.resultMessage)
    }

    suspend fun register(
        username: String,
        email: String,
        password: String,
        code: String,
    ): RegisterResponse {
        val stage = newStage()
        val envelope = executeEnvelope(gson) { stage.api.register(RegisterRequest(username, email, password, code)) }
        requireCode(envelope, 200)
        val response = envelope.data ?: error("注册响应为空")
        storeToken(stage, response.accessToken)
        return response
    }

    suspend fun getCurrentUser(type: String = "details"): UserProfile {
        val expectedGeneration = sessionRepository.currentGeneration()
        val envelope = executeEnvelope(gson) { api.getUserInfo(type) }
        requireCode(envelope, 200)
        val user = envelope.data ?: error("用户信息为空")
        check(sessionRepository.cacheUser(user, expectedGeneration)) { "The account changed while loading" }
        return user
    }

    suspend fun completeAuthentication(): UserProfile {
        val stage = requireStage()
        try {
            check(!stage.storage.getAccessToken().isNullOrBlank()) { "Authentication has not completed" }
            val envelope = executeEnvelope(gson) { stage.api.getUserInfo("details") }
            requireCode(envelope, 200)
            val user = envelope.data ?: error("用户信息为空")
            synchronized(stageLock) {
                check(authenticationSession === stage) { "Authentication was cancelled" }
                sessionRepository.commit(stage, user)
                authenticationSession = null
            }
            return user
        } catch (error: Exception) {
            synchronized(stageLock) { if (authenticationSession === stage) authenticationSession = null }
            throw error
        }
    }

    suspend fun logout() {
        cancelAuthentication()
        val previous = sessionRepository.stage(copyCurrent = true)
        // Revoke using the old installation snapshot; its response cannot mutate a later login.
        sessionRepository.clearSessionIfGeneration(previous.generation)
        val envelope = executeEnvelope(gson) { previous.api.logout() }
        requireCode(envelope, 200)
    }

    suspend fun logoutAll() {
        cancelAuthentication()
        val previous = sessionRepository.stage(copyCurrent = true)
        val envelope = try {
            executeEnvelope(gson) { previous.api.logoutAll() }
        } catch (error: ApiException) {
            if (error.statusCode != 401) throw error
            check(sessionRepository.currentGeneration() == previous.generation) { "The account changed while signing out" }
            // Share the normal refresh lock and retain rotated credentials if revocation fails.
            withContext(Dispatchers.IO) {
                sessionRepository.refreshAccessTokenBlocking(previous.storage.getAccessToken(), previous.generation)
            } ?: throw error
            val refreshed = sessionRepository.stage(copyCurrent = true)
            check(refreshed.generation == previous.generation) { "The account changed while signing out" }
            executeEnvelope(gson) { refreshed.api.logoutAll() }
        }
        requireCode(envelope, 200)
        check(sessionRepository.clearSessionIfGeneration(previous.generation)) { "The account changed while signing out" }
    }

    suspend fun verifyTotpMfa(
        challengeId: String,
        code: String? = null,
        recoveryCode: String? = null,
    ): String {
        val stage = requireStage()
        val envelope = executeEnvelope(gson) {
            stage.api.verifyTotpMfa(TotpMfaVerifyRequest(challengeId, code, recoveryCode))
        }
        requireCode(envelope, 200)
        return envelope.data?.accessToken
            ?.also { storeToken(stage, it) }
            ?: error("MFA AccessToken 缺失")
    }

    suspend fun getPasskeyAuthenticationOptions(): cn.ksuser.auth.data.model.PasskeyAuthenticationOptions {
        val stage = currentStage()
        val envelope = executeEnvelope(gson) { stage.api.getPasskeyAuthenticationOptions() }
        requireCode(envelope, 200)
        return envelope.data ?: error("缺少 Passkey 认证选项")
    }

    suspend fun verifyPasskeyLogin(
        challengeId: String,
        payload: PasskeyAuthenticationPayload,
    ): AuthResult {
        val stage = requireStage()
        val envelope = executeEnvelope(gson) {
            stage.api.verifyPasskeyAuthentication(
                challengeId = challengeId,
                request = PasskeyAuthenticationVerifyRequest(
                    credentialRawId = payload.credentialRawId,
                    clientDataJSON = payload.clientDataJSON,
                    authenticatorData = payload.authenticatorData,
                    signature = payload.signature,
                ),
            )
        }
        return parseAuthEnvelope(envelope, AuthSource.PASSKEY, stage)
    }

    suspend fun verifyPasskeyMfa(
        mfaChallengeId: String,
        passkeyChallengeId: String,
        payload: PasskeyAuthenticationPayload,
    ): String {
        val stage = requireStage()
        val envelope = executeEnvelope(gson) {
            stage.api.verifyPasskeyMfa(
                cn.ksuser.auth.data.model.PasskeyMfaVerifyRequest(
                    mfaChallengeId = mfaChallengeId,
                    passkeyChallengeId = passkeyChallengeId,
                    credentialRawId = payload.credentialRawId,
                    clientDataJSON = payload.clientDataJSON,
                    authenticatorData = payload.authenticatorData,
                    signature = payload.signature,
                ),
            )
        }
        requireCode(envelope, 200)
        return envelope.data?.accessToken
            ?.also { storeToken(stage, it) }
            ?: error("Passkey MFA AccessToken 缺失")
    }

    private fun parseAuthEnvelope(
        envelope: ApiEnvelope<JsonObject>,
        source: AuthSource,
        stage: AuthenticationSession,
    ): AuthResult {
        val code = envelope.code ?: invalidAuthResponse()
        val payload = envelope.data ?: JsonObject()
        if (code == 201) {
            val challengeId = payload.get("challengeId")?.takeIf { it.isJsonPrimitive }?.asString
                ?.takeIf { it.isNotBlank() } ?: invalidAuthResponse()
            val method = payload.get("method")?.takeIf { it.isJsonPrimitive }?.asString
                ?.takeIf { it.isNotBlank() } ?: "totp"
            val methods = payload.getAsJsonArray("methods").toStringList()
            return AuthResult.NeedsMfa(
                challengeId = challengeId,
                method = method,
                methods = methods.ifEmpty { listOf(method) },
                source = source,
            )
        }
        if (code == 200 && payload.has("accessToken")) {
            val token = payload.get("accessToken").asString
            storeToken(stage, token)
            return AuthResult.Success(token)
        }
        if (code == 202 && payload.get("needBind")?.asBoolean == true) {
            val provider = payload.get("provider")?.asString?.takeIf { it.isNotBlank() } ?: "qq"
            val bindToken = payload.get("oauthBindToken")?.asString?.takeIf { it.isNotBlank() }
                ?: invalidAuthResponse()
            return AuthResult.NeedsOAuthBind(
                provider = provider,
                bindToken = bindToken,
                openid = payload.get("openid")?.asString?.takeIf { it.isNotBlank() },
                message = envelope.resultMessage,
            )
        }
        if (code == 200 || code == 202) invalidAuthResponse()
        throw ApiException(code, envelope.resultMessage, envelope.errorCode)
    }

    private fun invalidAuthResponse(): Nothing = throw ApiException(
        500,
        ApiResultCodeResolver.message("INVALID_RESPONSE"),
        "INVALID_RESPONSE",
    )
}

private fun JsonArray?.toStringList(): List<String> {
    if (this == null) return emptyList()
    return mapNotNull { element -> element?.takeIf { !it.isJsonNull }?.asString }
}
