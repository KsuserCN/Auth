package cn.ksuser.auth.data.authorization

import java.net.URI
import java.net.URLDecoder

/** Native authorization has its own HTTPS entry and callback policy, independent of browser handoff. */
object NativeAuthorizationLinks {
    fun ticket(value: String, webOrigin: String): String? = runCatching {
        val uri = URI(value)
        val web = URI(webOrigin)
        if (web.scheme != "https" || web.host.isNullOrBlank() || uri.scheme != "https"
            || !uri.host.equals(web.host, true) || port(uri) != port(web)
            || uri.rawUserInfo != null || uri.rawFragment != null || uri.rawPath != "/app/sdk-authorize") return null
        val query = parameters(uri.rawQuery)
        if (query.size != 1 || query.single().first != "ticket") return null
        query.single().second.takeIf(MobileAuthorizationLinks::isTicket)
    }.getOrNull()

    fun isCallback(value: String): Boolean = runCatching {
        val uri = URI(value)
        uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.rawUserInfo == null
            && uri.rawQuery == null && uri.rawFragment == null && uri.port in setOf(-1, 443)
            && !uri.rawPath.isNullOrEmpty() && uri.rawPath.startsWith("/") && uri.normalize() == uri
    }.getOrDefault(false)

    /** Only the server's exact registered HTTPS callback carrying one result and one state is opened. */
    fun returnUrl(value: String?, redirectUri: String, approve: Boolean): String? = runCatching {
        if (!isCallback(redirectUri)) return null
        val actual = URI(value ?: return null)
        val expected = URI(redirectUri)
        if (actual.scheme != expected.scheme || actual.rawAuthority != expected.rawAuthority
            || actual.rawPath != expected.rawPath || actual.rawFragment != null) return null
        val query = parameters(actual.rawQuery)
        val result = if (approve) "code" else "error"
        if (query.size != 2 || query.count { it.first == "state" } != 1 || query.count { it.first == result } != 1) return null
        if (!Regex("[A-Za-z0-9_-]{22,128}").matches(query.single { it.first == "state" }.second)) return null
        val outcome = query.single { it.first == result }.second
        if (outcome.isBlank() || !approve && outcome != "access_denied") return null
        value
    }.getOrNull()

    private fun port(uri: URI) = if (uri.port == -1) 443 else uri.port
    private fun parameters(value: String?): List<Pair<String, String>> {
        require(!value.isNullOrBlank())
        return value.split('&').map {
            val pair = it.split('=', limit = 2)
            require(pair.size == 2 && pair[0].isNotEmpty())
            URLDecoder.decode(pair[0], "UTF-8") to URLDecoder.decode(pair[1], "UTF-8")
        }
    }
}
