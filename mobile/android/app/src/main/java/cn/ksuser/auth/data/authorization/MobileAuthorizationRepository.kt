package cn.ksuser.auth.data.authorization

import cn.ksuser.auth.core.network.ApiEnvelope
import cn.ksuser.auth.core.network.executeEnvelope
import cn.ksuser.auth.core.network.requireCode
import cn.ksuser.auth.data.model.QrScanPreview
import com.google.gson.Gson
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

/** Kept separate from the legacy API so application consent cannot use generic QR approval. */
interface MobileAuthorizationApi {
    @GET("auth/qr/preview")
    suspend fun preview(@Query("approveCode") approveCode: String? = null,
                        @Query("transferCode") transferCode: String? = null): Response<ApiEnvelope<AuthorizationQrPreview>>
    @GET("auth/mobile-authorization/context")
    suspend fun context(@Query("ticket") ticket: String): Response<ApiEnvelope<MobileAuthorizationContext>>
    @POST("auth/mobile-authorization/decide")
    suspend fun decide(@Body decision: MobileAuthorizationDecision): Response<ApiEnvelope<MobileAuthorizationReturn>>
    @POST("auth/qr/official/approve")
    suspend fun approveOfficial(@Body body: Map<String, String>): Response<ApiEnvelope<Map<String, Any>>>
    @POST("auth/qr/official/reject")
    suspend fun rejectOfficial(@Body body: Map<String, String>): Response<ApiEnvelope<Map<String, Any>>>
}

data class AuthorizationQrPreview(
    val codeType: String,
    val clientName: String? = null,
    val browser: String? = null,
    val system: String? = null,
    val ipAddress: String? = null,
    val ipLocation: String? = null,
    val expiresInSeconds: Long = 0,
    val targetName: String? = null,
    val targetOrigin: String? = null,
    val targetLogoUrl: String? = null,
    val requestedScopes: List<String>? = null,
    val consoleApplication: Boolean = false,
    val authorizationTicket: String? = null,
) {
    fun legacyPreview() = QrScanPreview(codeType, clientName, browser, system, ipAddress, ipLocation, expiresInSeconds)
}

data class MobileAuthorizationContext(
    val ticket: String,
    val appName: String,
    val logoUrl: String? = null,
    val contactInfo: String? = null,
    val redirectUri: String,
    val requestedScopes: List<String> = emptyList(),
    val alreadyAuthorized: Boolean = false,
    val existingGrantMode: String? = null,
    val expiresInSeconds: Long = 0,
    val qr: Boolean = false,
    val officialApplication: Boolean = false,
    val consoleApplication: Boolean = false,
    val webOrigin: String? = null,
    val subjectName: String? = null,
    val subjectVerificationType: String? = null,
)

enum class AuthorizationGrantMode { PERSISTENT, ONE_TIME, TIME_LIMITED }

data class MobileAuthorizationDecision(
    val ticket: String,
    val approve: Boolean,
    val grantMode: String = AuthorizationGrantMode.PERSISTENT.name,
    val grantTtlSeconds: Int? = null,
    val loginWeb: Boolean = false,
)

data class MobileAuthorizationReturn(val returnUrl: String? = null, val completed: Boolean = false)

class MobileAuthorizationRepository(private val api: MobileAuthorizationApi, private val gson: Gson) {
    suspend fun preview(approveCode: String? = null, transferCode: String? = null): AuthorizationQrPreview {
        val response = executeEnvelope(gson) { api.preview(approveCode, transferCode) }
        requireCode(response, 200)
        return requireNotNull(response.data) { "二维码预览数据缺失" }
    }

    suspend fun context(ticket: String): MobileAuthorizationContext {
        require(MobileAuthorizationLinks.isTicket(ticket)) { "授权票据无效" }
        val response = executeEnvelope(gson) { api.context(ticket) }
        requireCode(response, 200)
        return requireNotNull(response.data) { "应用授权数据缺失" }.also {
            require(it.ticket == ticket && it.appName.isNotBlank() && it.expiresInSeconds > 0) { "授权已过期或数据无效" }
        }
    }

    suspend fun decide(context: MobileAuthorizationContext, approve: Boolean,
                       mode: AuthorizationGrantMode, ttl: Int, loginWeb: Boolean): MobileAuthorizationReturn {
        val effectiveMode = if (context.officialApplication) AuthorizationGrantMode.PERSISTENT else mode
        val decision = MobileAuthorizationDecision(context.ticket, approve, effectiveMode.name,
            ttl.takeIf { effectiveMode == AuthorizationGrantMode.TIME_LIMITED }, approve && context.qr && loginWeb)
        val response = executeEnvelope(gson) { api.decide(decision) }
        requireCode(response, 200)
        return requireNotNull(response.data) { "授权响应缺失" }
    }

    suspend fun decideOfficial(code: String, approve: Boolean) {
        val response = executeEnvelope(gson) {
            if (approve) api.approveOfficial(mapOf("approveCode" to code)) else api.rejectOfficial(mapOf("approveCode" to code))
        }
        requireCode(response, 200)
    }
}
