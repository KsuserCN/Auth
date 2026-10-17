package cn.ksuser.auth.core.passkey

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import android.util.Base64
import cn.ksuser.auth.core.env.KsuserEnvironment
import cn.ksuser.auth.core.settings.L10n
import cn.ksuser.auth.data.model.*
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.fido.Fido
import com.google.android.gms.fido.common.Transport
import com.google.android.gms.fido.fido2.api.common.*
import com.google.android.gms.tasks.Task
import com.google.gson.JsonParser
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Roaming USB/NFC authenticators use the FIDO2 platform UI; no keys enter app storage. */
class SecurityKeyManager(private val environment: KsuserEnvironment, private val passkeys: PasskeyManager) {
    private var pending: CancellableContinuation<PublicKeyCredential>? = null
    fun available(activity: Activity): Boolean = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(activity) == 0

    suspend fun createForRegistration(activity: Activity, options: PasskeyRegistrationOptions): PasskeyRegistrationPayload {
        check(available(activity)) { L10n.text("当前设备没有可用的安全密钥提供方") }
        val request = JsonParser.parseString(passkeys.buildRegistrationRequestJson(options)).asJsonObject
        request.getAsJsonObject("authenticatorSelection").addProperty("authenticatorAttachment", "cross-platform")
        val response = launch(activity, Fido.getFido2ApiClient(activity)
            .getRegisterPendingIntent(PublicKeyCredentialCreationOptions(request.toString())))
        return passkeys.parseRegistrationResponse(response.toJson())
    }

    suspend fun getForAuthentication(activity: Activity, options: PasskeyAuthenticationOptions): PasskeyAuthenticationPayload {
        check(available(activity)) { L10n.text("当前设备没有可用的安全密钥提供方") }
        requirePasskeyRpMatch(environment, options.rpId)
        val builder = PublicKeyCredentialRequestOptions.Builder()
            .setRpId(options.rpId.ifBlank { environment.passkeyRpId })
            .setChallenge(decode(options.challenge))
            .setTimeoutSeconds((options.timeout.toDoubleOrNull() ?: 300_000.0) / 1000)
        options.allowCredentials?.takeIf(String::isNotBlank)?.let { serialized ->
            val list = JsonParser.parseString(serialized).asJsonArray.map { item ->
                val value = item.asJsonObject
                PublicKeyCredentialDescriptor("public-key", decode(value.get("id").asString), listOf(Transport.USB, Transport.NFC))
            }
            builder.setAllowList(list)
        }
        val response = launch(activity, Fido.getFido2ApiClient(activity).getSignPendingIntent(builder.build()))
        return passkeys.parseAuthenticationResponse(response.toJson())
    }

    private suspend fun launch(activity: Activity, task: Task<PendingIntent>): PublicKeyCredential =
        suspendCancellableCoroutine { continuation ->
            check(pending == null) { "A security key operation is already running" }
            pending = continuation
            continuation.invokeOnCancellation { if (pending === continuation) pending = null }
            task.addOnSuccessListener { intent ->
                if (!continuation.isActive || pending !== continuation) return@addOnSuccessListener
                runCatching { activity.startIntentSenderForResult(intent.intentSender, REQUEST_CODE, null, 0, 0, 0) }
                    .onFailure { pending = null; continuation.resumeWithException(it) }
            }.addOnFailureListener { error ->
                if (continuation.isActive && pending === continuation) { pending = null; continuation.resumeWithException(error) }
            }
        }

    @Suppress("DEPRECATION")
    fun handleActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        if (requestCode != REQUEST_CODE) return false
        val continuation = pending ?: return true
        pending = null
        if (!continuation.isActive) return true
        val response = runCatching {
            check(resultCode == Activity.RESULT_OK && data != null) { L10n.text("已取消安全密钥操作") }
            val raw = data!!.extras?.get(Fido.FIDO2_KEY_CREDENTIAL_EXTRA)
            val credential = when (raw) {
                is PublicKeyCredential -> raw
                is ByteArray -> PublicKeyCredential.deserializeFromBytes(raw)
                else -> {
                    val errorBytes = data.getByteArrayExtra(Fido.FIDO2_KEY_ERROR_EXTRA)
                    val error = errorBytes?.let(AuthenticatorErrorResponse::deserializeFromBytes)
                    throw IllegalStateException(error?.errorMessage ?: L10n.text("安全密钥未返回有效凭据"))
                }
            }
            val error = credential.response as? AuthenticatorErrorResponse
            check(error == null) { error?.errorMessage.orEmpty() }
            credential
        }
        response.onSuccess(continuation::resume).onFailure(continuation::resumeWithException)
        return true
    }
    private fun decode(value: String): ByteArray = Base64.decode(value, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    private companion object { const val REQUEST_CODE = 28419 }
}
