package cn.ksuser.auth.data.repository

import cn.ksuser.auth.core.network.AccountApiService
import cn.ksuser.auth.core.network.executeEnvelope
import cn.ksuser.auth.core.network.requireCode
import cn.ksuser.auth.core.qq.QqLoginCredential
import cn.ksuser.auth.data.model.AuthorizedApplications
import cn.ksuser.auth.data.model.QqMobileLoginRequest
import com.google.gson.Gson
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

class AccountRepository(private val api: AccountApiService, private val gson: Gson) {
    suspend fun qqBound(): Boolean {
        val response = executeEnvelope(gson) { api.accountStatus() }
        requireCode(response, 200)
        return response.data?.firstOrNull { it.provider == "qq" }?.bound ?: error("QQ 状态为空")
    }

    suspend fun appleBound(): Boolean {
        val response = executeEnvelope(gson) { api.appleStatus() }
        requireCode(response, 200)
        return response.data?.bound ?: error("Apple 状态为空")
    }

    suspend fun bindQq(credential: QqLoginCredential) {
        requireCode(executeEnvelope(gson) { api.bindQq(QqMobileLoginRequest(
            credential.appId, credential.accessToken, credential.openid, credential.unionid, credential.expiresIn,
        )) }, 200)
    }

    suspend fun unbindQq() = requireCode(executeEnvelope(gson) { api.unbindQq() }, 200)
    suspend fun unbindApple() = requireCode(executeEnvelope(gson) { api.unbindApple() }, 200)

    suspend fun authorizedApps(): AuthorizedApplications = coroutineScope {
        val thirdParty = async {
            executeEnvelope(gson) { api.thirdPartyApps() }.also { requireCode(it, 200) }.data
                ?: error("第三方授权列表响应为空")
        }
        val official = async {
            executeEnvelope(gson) { api.officialApps() }.also { requireCode(it, 200) }.data
                ?: error("官方授权列表响应为空")
        }
        AuthorizedApplications(thirdParty.await(), official.await())
    }

    suspend fun revokeThirdParty(appId: String) =
        requireCode(executeEnvelope(gson) { api.revokeThirdParty(appId) }, 200)
    suspend fun revokeOfficial(clientId: String) =
        requireCode(executeEnvelope(gson) { api.revokeOfficial(clientId) }, 200)
}
