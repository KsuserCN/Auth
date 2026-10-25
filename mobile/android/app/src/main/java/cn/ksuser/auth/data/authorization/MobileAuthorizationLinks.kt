package cn.ksuser.auth.data.authorization

import java.net.URI
import java.net.URLDecoder

/** Strict, JVM-testable link parsing. Never interprets an arbitrary URL's `code` as an approval. */
object MobileAuthorizationLinks {
    sealed interface Request {
        data class Bridge(val challengeId: String) : Request
        data class Application(val ticket: String) : Request
        data class Native(val ticket: String) : Request
        data class Approval(val code: String) : Request
        data class Transfer(val code: String) : Request
    }

    fun isTicket(value: String) = Regex("^[A-Za-z0-9_-]{32}$").matches(value)
    private fun isCode(value: String) = Regex("^[A-Za-z0-9_-]{16,512}$").matches(value)

    fun deepLink(value: String, webOrigin: String): Request? = runCatching {
        NativeAuthorizationLinks.ticket(value, webOrigin)?.let { return Request.Native(it) }
        val uri = URI(value)
        val web = URI(webOrigin)
        if (uri.rawUserInfo != null || uri.rawFragment != null) return null
        val custom = uri.scheme == "ksuserauth" && uri.port == -1 && uri.rawPath.orEmpty().isEmpty()
        val https = web.scheme == "https" && !web.host.isNullOrBlank() && uri.scheme == "https" && uri.host.equals(web.host, true) && effectivePort(uri) == effectivePort(web)
        val action = when {
            custom -> uri.host
            https && uri.rawPath == "/app/bridge-login" -> "bridge-login"
            https && uri.rawPath == "/app/authorize" -> "authorize"
            else -> return null
        }
        val key = if (action == "bridge-login") "challengeId" else if (action == "authorize") "ticket" else return null
        val parameters = parameters(uri.rawQuery)
        val ticket = parameters[key]?.singleOrNull()?.takeIf(::isTicket) ?: return null
        if (action == "bridge-login") Request.Bridge(ticket) else Request.Application(ticket)
    }.getOrNull()

    fun qr(value: String, webOrigin: String): Request? {
        val raw = value.trim()
        val approvalPrefix = "KSUSER-AUTH-QR:"
        val transferPrefix = "KSUSER-AUTH-XFER:v1:"
        if (raw.startsWith(approvalPrefix)) return raw.removePrefix(approvalPrefix).takeIf(::isCode)?.let(Request::Approval)
        if (raw.startsWith(transferPrefix)) return raw.removePrefix(transferPrefix).takeIf(::isCode)?.let(Request::Transfer)
        deepLink(raw, webOrigin)?.let { return it }
        return runCatching {
            val uri = URI(raw)
            val web = URI(webOrigin)
            if (uri.scheme != "https" || !uri.host.equals(web.host, true) || effectivePort(uri) != effectivePort(web)
                || uri.rawUserInfo != null || uri.rawFragment != null || uri.rawPath !in setOf("/login", "/app/scan")) return null
            val params = parameters(uri.rawQuery)
            val approval = params["approveCode"]?.singleOrNull()?.takeIf(::isCode)
            val transfer = params["transferCode"]?.singleOrNull()?.takeIf(::isCode)
            if (approval != null && transfer == null) Request.Approval(approval)
            else if (transfer != null && approval == null) Request.Transfer(transfer) else null
        }.getOrNull()
    }

    /** Application returns must be the server's browser result page, including its private secret. */
    fun applicationReturn(value: String?, webOrigin: String): String? = runCatching {
        val uri = URI(value ?: return null)
        if (!trustedReturnOrigin(uri, URI(webOrigin)) || uri.rawPath != "/app/authorize-return" || uri.rawQuery != null) return null
        val fragment = parameters(uri.rawFragment)
        if (!listOf("ticket", "secret").all { fragment[it]?.singleOrNull()?.let(::isTicket) == true }) return null
        value
    }.getOrNull()

    /** Bridge returns may target any path of the configured passport origin, never another application. */
    fun bridgeReturn(value: String?, webOrigin: String): String? = runCatching {
        val uri = URI(value ?: return null)
        value.takeIf { trustedReturnOrigin(uri, URI(webOrigin)) }
    }.getOrNull()

    fun origin(value: String): String? = runCatching {
        val uri = URI(value)
        if (uri.scheme !in setOf("https", "http") || uri.host.isNullOrBlank() || uri.rawUserInfo != null) return null
        "${uri.scheme}://${uri.host}" + if (uri.port == -1) "" else ":${uri.port}"
    }.getOrNull()

    private fun trustedReturnOrigin(uri: URI, web: URI): Boolean {
        if (web.scheme !in setOf("http", "https") || web.host.isNullOrBlank() || uri.host.isNullOrBlank()) return false
        val hosts = if (web.host.equals("auth.ksuser.cn", true)) setOf("auth.ksuser.cn", "www.ksuser.cn") else setOf(web.host)
        return uri.scheme == web.scheme && uri.host?.lowercase() in hosts && effectivePort(uri) == effectivePort(web)
            && uri.rawUserInfo == null
    }

    private fun effectivePort(uri: URI) = if (uri.port != -1) uri.port else if (uri.scheme == "https") 443 else 80
    private fun parameters(raw: String?): Map<String, List<String>> = raw.orEmpty().split('&').filter { it.isNotBlank() }
        .map { entry -> entry.split('=', limit = 2).let { decode(it[0]) to decode(it.getOrElse(1) { "" }) } }
        .groupBy({ it.first }, { it.second })
    private fun decode(value: String) = URLDecoder.decode(value, "UTF-8")
}
