package cn.ksuser.auth.sdk

import com.google.gson.annotations.SerializedName
import java.net.URI

public enum class KsuserAuthorizationMode { OAUTH, OFFICIAL }

/** Configure the calling application's own, precisely registered HTTPS callback. */
public data class KsuserAuthConfiguration @JvmOverloads constructor(
    val clientId: String,
    val mode: KsuserAuthorizationMode,
    val redirectUri: String,
    val apiBaseUrl: String = "https://api.ksuser.cn",
    val webBaseUrl: String = "https://auth.ksuser.cn",
    val officialIssuer: String = "https://api.ksuser.cn/official",
    val trustedProviderCertificateSha256: List<String> = emptyList(),
) {
    internal val identity: String
        get() = listOf(apiBaseUrl, webBaseUrl, officialIssuer, mode.name, clientId, redirectUri,
            providerPins.sorted().joinToString(",")).joinToString("|")
    internal val providerPins: Set<String>
        get() = if (trustedProviderCertificateSha256.isNotEmpty()) trustedProviderCertificateSha256.map(ProviderTrust::normalizeFingerprint).toSet()
            else if (URI(webBaseUrl).host.equals("auth.ksuser.cn", ignoreCase = true)) ProviderTrust.productionPins
            else emptySet()
    internal val defaultScopes: List<String>
        get() = if (mode == KsuserAuthorizationMode.OFFICIAL) listOf("openid", "profile") else listOf("profile")

    internal fun validate() {
        try {
            require(clientId.isNotBlank() && clientId.length <= 128 && !clientId.contains('|'))
            val callback = URI(redirectUri)
            require(Urls.isHttps(callback) && callback.port in listOf(-1, 443))
            require(callback.rawQuery == null && callback.rawPath.startsWith("/") && callback.normalize() == callback)
            require(!callback.rawPath.contains(';') && !callback.path.contains('\\'))
            require(!Regex("(?i)%(2f|5c|3f|23|3b)").containsMatchIn(callback.rawPath))
            require(!callback.path.any { it.isISOControl() } && !callback.path.split('/').any { it == "." || it == ".." })
            val api = URI(apiBaseUrl)
            val web = URI(webBaseUrl)
            val issuer = URI(officialIssuer)
            require(listOf(api, web, issuer).all { Urls.isHttps(it) && it.rawQuery == null && it.normalize() == it })
            require(web.rawPath in listOf("", "/") && web.port in listOf(-1, 443))
            require(!callback.host.equals(web.host, ignoreCase = true))
            require(trustedProviderCertificateSha256.size <= 10)
            trustedProviderCertificateSha256.forEach(ProviderTrust::normalizeFingerprint)
        } catch (error: Exception) {
            throw KsuserAuthException(KsuserAuthErrorCode.INVALID_CONFIGURATION)
        }
    }
}

/** Tokens belong to the calling application. They are deliberately redacted in toString(). */
public class KsuserTokenSet internal constructor(
    @SerializedName("access_token") public val accessToken: String,
    @SerializedName("token_type") public val tokenType: String,
    @SerializedName("expires_in") public val expiresIn: Long,
    public val scope: String? = null,
    @SerializedName("id_token") public val idToken: String? = null,
    @SerializedName("openid") public val openId: String? = null,
    @SerializedName("unionid") public val unionId: String? = null,
) {
    override fun toString(): String = "KsuserTokenSet([REDACTED])"
}

public sealed class KsuserAuthorizationResult {
    public class OAuth(public val tokens: KsuserTokenSet) : KsuserAuthorizationResult() {
        override fun toString(): String = "KsuserAuthorizationResult.OAuth([REDACTED])"
    }
    public class Official(public val tokens: KsuserTokenSet, public val subject: String) : KsuserAuthorizationResult() {
        override fun toString(): String = "KsuserAuthorizationResult.Official([REDACTED])"
    }
}

public enum class KsuserAuthErrorCode {
    INVALID_CONFIGURATION, AUTHORIZATION_IN_PROGRESS, CANCELLED, EXPIRED,
    INVALID_RESPONSE, INVALID_CALLBACK, INVALID_ID_TOKEN, STORAGE, NETWORK, SERVER, LAUNCH_FAILED,
}

/** Safe diagnostics; neither message nor serverErrorCode contains a raw response or callback. */
public class KsuserAuthException internal constructor(
    public val code: KsuserAuthErrorCode,
    public val httpStatus: Int? = null,
    public val serverErrorCode: String? = null,
    cause: Throwable? = null,
) : Exception(code.name, cause) {
    internal val recoverable: Boolean
        get() = code == KsuserAuthErrorCode.NETWORK || code == KsuserAuthErrorCode.LAUNCH_FAILED ||
            httpStatus == 429 || httpStatus in 500..599
}

public interface KsuserAuthCallback<T> {
    public fun onSuccess(result: T)
    public fun onFailure(error: KsuserAuthException)
}

internal class NativeCreated(
    val ticket: String,
    val cancelSecret: String,
    val appLink: String,
    val webAuthorizationUrl: String,
    val expiresInSeconds: Long,
)

internal class PendingTransaction(
    val configurationId: String,
    val redirectUri: String,
    val verifier: String,
    val state: String,
    val nonce: String?,
    val created: NativeCreated,
    val expiresAtMillis: Long,
    var code: String? = null,
    var callbackError: String? = null,
    var tokens: KsuserTokenSet? = null,
) {
    val hasResult: Boolean get() = code != null || callbackError != null || tokens != null
}

internal object Urls {
    fun isHttps(uri: URI): Boolean = uri.scheme == "https" && !uri.host.isNullOrBlank() &&
        uri.rawUserInfo == null && uri.rawFragment == null

    fun sameEndpoint(expected: URI, actual: URI): Boolean = isHttps(actual) &&
        expected.scheme == actual.scheme && expected.host.equals(actual.host, ignoreCase = true) &&
        expected.port == actual.port && expected.rawPath == actual.rawPath
}
