package cn.ksuser.demo.auth

import android.app.Activity
import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cn.ksuser.auth.sdk.KsuserAuthClient
import cn.ksuser.auth.sdk.KsuserAuthErrorCode
import cn.ksuser.auth.sdk.KsuserAuthException
import cn.ksuser.auth.sdk.KsuserAuthorizationMode
import cn.ksuser.auth.sdk.KsuserAuthorizationResult
import java.lang.ref.WeakReference
import kotlinx.coroutines.CancellationException

/** A client and waiter survive rotations; the retained model only holds weak Activity references. */
class DemoLoginModel(application: Application) : AndroidViewModel(application) {
    private val preferences = application.getSharedPreferences("demo_configuration", 0)
    private val initialMode = runCatching {
        KsuserAuthorizationMode.valueOf(preferences.getString("mode", KsuserAuthorizationMode.OAUTH.name)!!)
    }.getOrDefault(KsuserAuthorizationMode.OAUTH)
    private val controller = DemoLoginController<WeakReference<Activity>>(
        viewModelScope, initialMode, DemoConfiguration::isConfigured, DemoConfiguration::scopes,
        sessionFactory = { mode ->
            try { SdkDemoSession(KsuserAuthClient(application, DemoConfiguration.sdkConfiguration(mode))) }
            catch (error: KsuserAuthException) { throw DemoAuthorizationException(demoFailure(error.code, error.httpStatus, error.serverErrorCode)) }
        },
        persistMode = { mode -> check(preferences.edit().putString("mode", mode.name).commit()) },
    )
    internal val state = controller.state
    fun selectMode(mode: KsuserAuthorizationMode) = controller.selectMode(mode)
    fun authorize(activity: Activity) = controller.authorize(WeakReference(activity))
    fun resume(activity: Activity, web: Boolean = false) = controller.resume(WeakReference(activity), web)
    fun cancel() = controller.cancel()
    fun callback(uri: Uri, activity: Activity): Boolean = controller.callback(uri.toString(), WeakReference(activity))
}

private class SdkDemoSession(private val client: KsuserAuthClient) : DemoAuthorizationSession<WeakReference<Activity>> {
    override suspend fun authorize(host: WeakReference<Activity>, scopes: List<String>) = sdk {
        client.authorize(activity(host), scopes).outcome()
    }
    override suspend fun resume(host: WeakReference<Activity>, useWebFallback: Boolean) = sdk {
        client.resumePendingAuthorization(activity(host), useWebFallback)?.outcome()
    }
    override fun handleCallback(url: String): Boolean = client.handleCallback(Uri.parse(url))
    override suspend fun cancel() = sdk { client.cancel() }
    private fun activity(host: WeakReference<Activity>): Activity = host.get()?.takeUnless { it.isFinishing || it.isDestroyed }
        ?: throw DemoAuthorizationException(DemoFailureDetails(DemoFailureKind.LAUNCH))
    private suspend fun <T> sdk(action: suspend () -> T): T = try { action() }
    catch (cancelled: CancellationException) { throw cancelled }
    catch (error: KsuserAuthException) { throw DemoAuthorizationException(demoFailure(error.code, error.httpStatus, error.serverErrorCode)) }
}

private fun KsuserAuthorizationResult.outcome(): DemoAuthorizationOutcome = when (this) {
    is KsuserAuthorizationResult.OAuth -> DemoAuthorizationOutcome(KsuserAuthorizationMode.OAUTH,
        tokens.openId, tokens.unionId, tokens.expiresIn)
    is KsuserAuthorizationResult.Official -> DemoAuthorizationOutcome(KsuserAuthorizationMode.OFFICIAL,
        subject, expiresIn = tokens.expiresIn)
}

/** No exception messages, causes, response bodies, callback queries or tokens enter UI state. */
internal fun demoFailure(code: KsuserAuthErrorCode, httpStatus: Int?, serverCode: String?): DemoFailureDetails {
    val safeCode = serverCode?.takeIf { Regex("[A-Z][A-Z0-9_]{1,127}|[a-z]+(?:_[a-z]+)+").matches(it) && it.length <= 128 }
    val kind = when {
        safeCode == "access_denied" -> DemoFailureKind.ACCESS_DENIED
        code == KsuserAuthErrorCode.INVALID_CONFIGURATION -> DemoFailureKind.CONFIGURATION
        code == KsuserAuthErrorCode.AUTHORIZATION_IN_PROGRESS -> DemoFailureKind.IN_PROGRESS
        code == KsuserAuthErrorCode.CANCELLED -> DemoFailureKind.CANCELLED
        code == KsuserAuthErrorCode.EXPIRED -> DemoFailureKind.EXPIRED
        code == KsuserAuthErrorCode.INVALID_CALLBACK -> DemoFailureKind.CALLBACK
        code == KsuserAuthErrorCode.INVALID_RESPONSE -> DemoFailureKind.RESPONSE
        code == KsuserAuthErrorCode.INVALID_ID_TOKEN -> DemoFailureKind.ID_TOKEN
        code == KsuserAuthErrorCode.STORAGE -> DemoFailureKind.STORAGE
        code == KsuserAuthErrorCode.NETWORK -> DemoFailureKind.NETWORK
        code == KsuserAuthErrorCode.LAUNCH_FAILED -> DemoFailureKind.LAUNCH
        code == KsuserAuthErrorCode.SERVER -> DemoFailureKind.SERVICE
        else -> DemoFailureKind.UNEXPECTED
    }
    return DemoFailureDetails(kind, httpStatus?.takeIf { it in 400..599 }, safeCode)
}
