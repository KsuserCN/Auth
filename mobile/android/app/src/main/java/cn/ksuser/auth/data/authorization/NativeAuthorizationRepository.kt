package cn.ksuser.auth.data.authorization

import cn.ksuser.auth.core.network.ApiEnvelope
import cn.ksuser.auth.core.network.executeEnvelope
import cn.ksuser.auth.core.network.requireCode
import com.google.gson.Gson
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface NativeAuthorizationApi {
    @GET("auth/native-authorization/context")
    suspend fun context(@Query("ticket") ticket: String): Response<ApiEnvelope<NativeAuthorizationContext>>
    @POST("auth/native-authorization/decide")
    suspend fun decide(@Body decision: NativeAuthorizationDecision): Response<ApiEnvelope<NativeAuthorizationReturn>>
}

data class NativeAuthorizationContext(
    val ticket: String,
    val appName: String,
    val logoUrl: String? = null,
    val contactInfo: String? = null,
    val redirectUri: String,
    val requestedScopes: List<String> = emptyList(),
    val alreadyAuthorized: Boolean = false,
    val existingGrantMode: String? = null,
    val expiresInSeconds: Long = 0,
    val officialApplication: Boolean = false,
    val consoleApplication: Boolean = false,
    val subjectName: String? = null,
    val subjectVerificationType: String? = null,
    val nativeAuthorization: Boolean = false,
) {
    fun forConsent() = MobileAuthorizationContext(ticket, appName, logoUrl, contactInfo, redirectUri,
        requestedScopes, alreadyAuthorized, existingGrantMode, expiresInSeconds,
        officialApplication = officialApplication, consoleApplication = consoleApplication,
        subjectName = subjectName, subjectVerificationType = subjectVerificationType, nativeAuthorization = true)
}

data class NativeAuthorizationDecision(val ticket: String, val approve: Boolean,
    val grantMode: String = AuthorizationGrantMode.PERSISTENT.name, val grantTtlSeconds: Int? = null)
data class NativeAuthorizationReturn(val returnUrl: String? = null, val completed: Boolean = false)

class NativeAuthorizationRepository(private val api: NativeAuthorizationApi, private val gson: Gson) {
    suspend fun context(ticket: String): NativeAuthorizationContext {
        require(MobileAuthorizationLinks.isTicket(ticket)) { "授权票据无效" }
        val response = executeEnvelope(gson) { api.context(ticket) }
        requireCode(response, 200)
        return requireNotNull(response.data) { "应用授权数据缺失" }.also {
            require(it.nativeAuthorization && it.ticket == ticket && it.appName.isNotBlank()
                && it.expiresInSeconds in 1..300 && NativeAuthorizationLinks.isCallback(it.redirectUri)) { "授权已过期或数据无效" }
        }
    }

    suspend fun decide(context: MobileAuthorizationContext, approve: Boolean,
                       mode: AuthorizationGrantMode, ttl: Int): String {
        require(context.nativeAuthorization && !context.qr) { "原生授权类型不匹配" }
        val effectiveMode = if (context.officialApplication) AuthorizationGrantMode.PERSISTENT else mode
        val response = executeEnvelope(gson) { api.decide(NativeAuthorizationDecision(context.ticket, approve,
            effectiveMode.name, ttl.takeIf { approve && effectiveMode == AuthorizationGrantMode.TIME_LIMITED })) }
        requireCode(response, 200)
        val result = requireNotNull(response.data) { "授权响应缺失" }
        require(result.completed) { "授权尚未完成" }
        return requireNotNull(NativeAuthorizationLinks.returnUrl(result.returnUrl, context.redirectUri, approve)) { "应用授权返回地址无效" }
    }
}
