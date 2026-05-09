package cn.ksuser.auth.core.qq

import android.app.Activity
import android.content.Context
import android.content.Intent
import cn.ksuser.auth.core.env.KsuserEnvironment
import com.tencent.connect.UnionInfo
import com.tencent.connect.common.Constants
import com.tencent.tauth.IUiListener
import com.tencent.tauth.Tencent
import com.tencent.tauth.UiError
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject

class QqLoginManager(
    private val environment: KsuserEnvironment,
) {
    private var tencent: Tencent? = null
    private var pendingListener: IUiListener? = null

    val isConfigured: Boolean
        get() = environment.qqMobileAppId.isNotBlank()

    suspend fun login(activity: Activity): QqLoginCredential {
        val appId = environment.qqMobileAppId.trim()
        require(appId.isNotBlank()) { "QQ 登录未配置，请先设置 QQ_MOBILE_APP_ID" }

        Tencent.setIsPermissionGranted(true)
        val client = tencent ?: Tencent.createInstance(appId, activity.applicationContext).also {
            tencent = it
        }
        if (client.isSessionValid) {
            client.logout(activity.applicationContext)
        }

        val credential = suspendCancellableCoroutine<QqLoginCredential> { continuation ->
            val listener = object : IUiListener {
                override fun onComplete(response: Any?) {
                    pendingListener = null
                    val json = response as? JSONObject
                    if (json == null) {
                        continuation.resumeWithException(IllegalStateException("QQ 登录响应为空"))
                        return
                    }

                    val accessToken = json.optString(Constants.PARAM_ACCESS_TOKEN).trim()
                    val openid = json.optString(Constants.PARAM_OPEN_ID).trim()
                    val expiresIn = json.optString(Constants.PARAM_EXPIRES_IN).trim().ifBlank { null }
                    if (accessToken.isBlank() || openid.isBlank()) {
                        continuation.resumeWithException(IllegalStateException("QQ 登录响应缺少 access_token 或 openid"))
                        return
                    }

                    client.setAccessToken(accessToken, expiresIn ?: "0")
                    client.openId = openid
                    continuation.resume(
                        QqLoginCredential(
                            appId = appId,
                            accessToken = accessToken,
                            openid = openid,
                            expiresIn = expiresIn,
                        ),
                    )
                }

                override fun onError(error: UiError?) {
                    pendingListener = null
                    val message = error?.errorMessage?.takeIf { it.isNotBlank() }
                        ?: error?.errorDetail?.takeIf { it.isNotBlank() }
                        ?: "QQ 登录失败"
                    continuation.resumeWithException(IllegalStateException(message))
                }

                override fun onCancel() {
                    pendingListener = null
                    continuation.resumeWithException(IllegalStateException("已取消 QQ 登录"))
                }

                override fun onWarning(code: Int) = Unit
            }

            pendingListener = listener
            continuation.invokeOnCancellation { pendingListener = null }
            client.login(activity, QQ_LOGIN_SCOPE, listener)
        }
        val unionid = getUnionid(activity.applicationContext, client)
        return credential.copy(unionid = unionid)
    }

    private suspend fun getUnionid(context: Context, client: Tencent): String {
        return suspendCancellableCoroutine { continuation ->
            UnionInfo(context, client.qqToken).getUnionId(
                object : IUiListener {
                    override fun onComplete(response: Any?) {
                        val json = response as? JSONObject
                        val unionid = json?.optString("unionid")?.trim().orEmpty()
                        if (unionid.isBlank()) {
                            continuation.resumeWithException(IllegalStateException("QQ 登录响应缺少 unionid"))
                            return
                        }
                        continuation.resume(unionid)
                    }

                    override fun onError(error: UiError?) {
                        val message = error?.errorMessage?.takeIf { it.isNotBlank() }
                            ?: error?.errorDetail?.takeIf { it.isNotBlank() }
                            ?: "获取 QQ unionid 失败"
                        continuation.resumeWithException(IllegalStateException(message))
                    }

                    override fun onCancel() {
                        continuation.resumeWithException(IllegalStateException("已取消 QQ unionid 获取"))
                    }

                    override fun onWarning(code: Int) = Unit
                },
            )
        }
    }

    fun handleActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        val listener = pendingListener ?: return false
        if (requestCode == Constants.REQUEST_LOGIN || requestCode == Tencent.REQUEST_LOGIN) {
            Tencent.onActivityResultData(requestCode, resultCode, data, listener)
            return true
        }
        return false
    }

    fun handleNewIntent(intent: Intent?) {
        val listener = pendingListener ?: return
        val data = intent ?: return
        Tencent.handleResultData(data, listener)
    }

    private companion object {
        const val QQ_LOGIN_SCOPE = "get_user_info"
    }
}

data class QqLoginCredential(
    val appId: String,
    val accessToken: String,
    val openid: String,
    val expiresIn: String?,
    val unionid: String = "",
)
