package cn.ksuser.auth.sdk

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import java.lang.ref.WeakReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Retain a client, forward the app's HTTPS callback, then resume after a cold start. */
public class KsuserAuthClient @Throws(KsuserAuthException::class) constructor(
    context: Context,
    public val configuration: KsuserAuthConfiguration,
) {
    private val application = context.applicationContext
    private val callbacks = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val engine: AuthorizationEngine

    init {
        configuration.validate()
        // A single process owns the private transaction and in-memory lease; remote services must not instantiate it.
        ProcessGuard.requireDefaultProcess(application)
        engine = AuthorizationEngine(configuration, EncryptedTransactionStore(application), SdkHttp(configuration),
            AuthorizationRuntime.forApplication(application.packageName))
    }

    @JvmOverloads
    public suspend fun authorize(activity: Activity, scopes: List<String>? = null): KsuserAuthorizationResult =
        engine.authorize(scopes, launcher(activity))

    /** Returns false for URLs that do not belong to this pending transaction. */
    public fun handleCallback(uri: Uri): Boolean = engine.handleCallback(uri.toString())

    /** Web continuation always reuses the existing ticket. An active original call receives its result. */
    @JvmOverloads
    public suspend fun resumePendingAuthorization(activity: Activity, useWebFallback: Boolean = false): KsuserAuthorizationResult? =
        engine.resume(launcher(activity), useWebFallback)

    /** Ends local waiting immediately; server cancellation is best effort and does not revoke grants. */
    public suspend fun cancel(): Unit = engine.cancel()

    public fun authorize(activity: Activity, scopes: List<String>?, callback: KsuserAuthCallback<KsuserAuthorizationResult>) {
        deliver(callback) { authorize(activity, scopes) }
    }

    public fun resumePendingAuthorization(activity: Activity, useWebFallback: Boolean,
                                         callback: KsuserAuthCallback<KsuserAuthorizationResult?>) {
        deliver(callback) { resumePendingAuthorization(activity, useWebFallback) }
    }

    public fun cancel(callback: KsuserAuthCallback<Unit>) { deliver(callback) { cancel() } }

    private fun <T> deliver(callback: KsuserAuthCallback<T>, action: suspend () -> T) {
        callbacks.launch {
            val result = try { Result.success(action()) }
            catch (error: KsuserAuthException) { Result.failure(error) }
            catch (_: CancellationException) { Result.failure(KsuserAuthException(KsuserAuthErrorCode.CANCELLED)) }
            catch (_: Exception) { Result.failure(KsuserAuthException(KsuserAuthErrorCode.INVALID_RESPONSE)) }
            result.fold(callback::onSuccess) { callback.onFailure(it as KsuserAuthException) }
        }
    }

    private fun launcher(activity: Activity): AuthorizationLauncher {
        val weakActivity = WeakReference(activity)
        return AuthorizationLauncher { transaction, useWebFallback ->
            withContext(Dispatchers.Main.immediate) {
                val target = weakActivity.get()?.takeUnless { it.isFinishing || it.isDestroyed }
                    ?: throw KsuserAuthException(KsuserAuthErrorCode.LAUNCH_FAILED)
                if (!useWebFallback && ProviderTrust.canLaunch(application, configuration)) {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(transaction.created.appLink))
                        .addCategory(Intent.CATEGORY_BROWSABLE).setPackage("cn.ksuser.auth")
                    val opened = try { target.startActivity(intent); true }
                    catch (_: ActivityNotFoundException) { false }
                    catch (_: SecurityException) { false }
                    if (opened) return@withContext
                }
                val uri = Uri.parse(transaction.created.webAuthorizationUrl)
                try {
                    CustomTabsIntent.Builder().build().launchUrl(target, uri)
                } catch (_: ActivityNotFoundException) {
                    try { target.startActivity(Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE)) }
                    catch (_: Exception) { throw KsuserAuthException(KsuserAuthErrorCode.LAUNCH_FAILED) }
                } catch (_: SecurityException) { throw KsuserAuthException(KsuserAuthErrorCode.LAUNCH_FAILED) }
            }
        }
    }
}
