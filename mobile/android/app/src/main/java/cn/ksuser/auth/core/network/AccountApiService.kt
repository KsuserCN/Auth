package cn.ksuser.auth.core.network

import cn.ksuser.auth.data.model.AccountLinkStatus
import cn.ksuser.auth.data.model.AppleLinkStatus
import cn.ksuser.auth.data.model.AuthorizedApplication
import cn.ksuser.auth.data.model.OfficialAuthorizedApplication
import cn.ksuser.auth.data.model.QqMobileLoginRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface AccountApiService {
    @GET("/oauth/accounts/status")
    suspend fun accountStatus(): Response<ApiEnvelope<List<AccountLinkStatus>>>
    @GET("/oauth/apple/status")
    suspend fun appleStatus(): Response<ApiEnvelope<AppleLinkStatus>>
    @POST("/oauth/qq/mobile-bind")
    suspend fun bindQq(@Body credential: QqMobileLoginRequest): Response<ApiEnvelope<Unit>>
    @POST("/oauth/qq/unbind")
    suspend fun unbindQq(): Response<ApiEnvelope<Unit>>
    @POST("/oauth/apple/unbind")
    suspend fun unbindApple(): Response<ApiEnvelope<Unit>>
    @GET("/oauth2/authorizations")
    suspend fun thirdPartyApps(): Response<ApiEnvelope<List<AuthorizedApplication>>>
    @GET("/official/authorizations")
    suspend fun officialApps(): Response<ApiEnvelope<List<OfficialAuthorizedApplication>>>
    @DELETE("/oauth2/authorizations/{appId}")
    suspend fun revokeThirdParty(@Path("appId") appId: String): Response<ApiEnvelope<Unit>>
    @DELETE("/official/authorizations/{clientId}")
    suspend fun revokeOfficial(@Path("clientId") clientId: String): Response<ApiEnvelope<Unit>>
}

