package cn.ksuser.auth.sdk

import java.net.URI
import java.net.URLDecoder
import java.security.MessageDigest
import java.security.SecureRandom
import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.toByteString

internal object Pkce {
    fun random(): String = ByteArray(32).also(SecureRandom()::nextBytes).toByteString().base64Url().trimEnd('=')
    fun challenge(verifier: String): String = MessageDigest.getInstance("SHA-256")
        .digest(verifier.toByteArray(Charsets.US_ASCII)).toByteString().base64Url().trimEnd('=')
}

internal fun decodeBase64Url(value: String): ByteArray {
    if (!Regex("[A-Za-z0-9_-]+").matches(value) || value.length % 4 == 1) invalidToken()
    val decoded = value.decodeBase64() ?: invalidToken()
    if (decoded.base64Url().trimEnd('=') != value) invalidToken()
    return decoded.toByteArray()
}

internal fun invalidResponse(): Nothing = throw KsuserAuthException(KsuserAuthErrorCode.INVALID_RESPONSE)
internal fun invalidToken(): Nothing = throw KsuserAuthException(KsuserAuthErrorCode.INVALID_ID_TOKEN)

internal object CallbackParser {
    data class Outcome(val code: String? = null, val error: String? = null)

    fun parse(value: String, redirect: String, state: String): Outcome? = try {
        val actual = URI(value)
        val expected = URI(redirect)
        if (!Urls.sameEndpoint(expected, actual)) null
        else {
            val fields = query(actual)
            if (fields.size != 2 || fields.count { it.first == "state" && it.second == state } != 1) null
            else if (fields.count { it.first == "code" && it.second.isNotBlank() } == 1) Outcome(code = fields.single { it.first == "code" }.second)
            else if (fields.count { it.first == "error" && it.second.isNotBlank() } == 1) Outcome(error = fields.single { it.first == "error" }.second)
            else null
        }
    } catch (_: Exception) { null }

    fun query(uri: URI): List<Pair<String, String>> {
        val query = uri.rawQuery ?: return emptyList()
        return query.split('&').map {
            val parts = it.split('=', limit = 2)
            require(parts.size == 2)
            URLDecoder.decode(parts[0], "UTF-8") to URLDecoder.decode(parts[1], "UTF-8")
        }
    }
}

internal object CreatedValidator {
    fun validate(created: NativeCreated, configuration: KsuserAuthConfiguration) {
        try {
            require(Regex("[A-Za-z0-9_-]{32}").matches(created.ticket))
            require(Regex("[A-Za-z0-9_-]{32}").matches(created.cancelSecret))
            require(created.expiresInSeconds in 1..600)
            val web = URI(configuration.webBaseUrl)
            for ((link, path) in listOf(created.appLink to "/app/sdk-authorize", created.webAuthorizationUrl to "/app/sdk-authorize/web")) {
                val actual = URI(link)
                val expected = URI(web.scheme, null, web.host, web.port, path, null, null)
                require(Urls.sameEndpoint(expected, actual))
                require(CallbackParser.query(actual) == listOf("ticket" to created.ticket))
            }
        } catch (_: Exception) { invalidResponse() }
    }
}
