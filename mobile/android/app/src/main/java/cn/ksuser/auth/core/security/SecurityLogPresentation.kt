package cn.ksuser.auth.core.security

import cn.ksuser.auth.core.network.ApiResultCodeResolver
import cn.ksuser.auth.core.network.GeneratedApiResultCodes
import cn.ksuser.auth.data.model.SensitiveLogItem
import java.util.Locale
import cn.ksuser.auth.core.settings.L10n

object SecurityLogPresentation {
    fun text(key: String): String = GeneratedSecurityLogCatalog.messages.getValue(L10n.language)[key] ?: key
    private fun normalize(value: String): String = value.trim().replace("\"", "").replace("'", "")
        .uppercase(Locale.ROOT).replace('-', '_').replace(' ', '_')
    fun parseMethods(raw: String?, methods: List<String> = emptyList()): List<String> =
        (methods.ifEmpty { listOf(raw.orEmpty()) }).flatMap { it.trim().removeSurrounding("[", "]").split(',') }
            .map(::normalize).filter { it.isNotEmpty() && it != "NULL" }
            .flatMap { if (it.endsWith("_MFA")) listOf(it.removeSuffix("_MFA"), "MFA") else listOf(it) }
            .map { when (it) { "EMAIL" -> "EMAIL_CODE"; "WEIXIN" -> "WECHAT"; else -> it } }.distinct()
    fun operation(value: String): String = GeneratedSecurityLogCatalog.messages.getValue(L10n.language)["operation.${normalize(value)}"] ?: text("ui.unknownOperation")
    fun action(value: String?): String = GeneratedSecurityLogCatalog.messages.getValue(L10n.language)["action.${normalize(value.orEmpty())}"] ?: text("ui.unknownAction")
    fun title(log: SensitiveLogItem): String = if (normalize(log.operationType) == "ADAPTIVE_POLICY") action(log.actionTaken) else operation(log.operationType)
    fun methods(log: SensitiveLogItem): List<String> {
        if (normalize(log.operationType) !in listOf("LOGIN", "SENSITIVE_VERIFY", "BIND_OAUTH", "UNBIND_OAUTH")) return emptyList()
        return parseMethods(log.loginMethod, log.loginMethods).map { GeneratedSecurityLogCatalog.messages.getValue(L10n.language)["method.$it"] ?: text("ui.unknownMethod") }.distinct()
    }
    fun reason(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        if (GeneratedApiResultCodes.messages.containsKey(raw)) return ApiResultCodeResolver.message(raw, statusCode = 400)
        val historical = raw.replace(Regex("^risk=\\d+;?\\s*", RegexOption.IGNORE_CASE), "").trim()
        if (historical.isEmpty()) return null
        return GeneratedSecurityLogCatalog.messages.getValue(L10n.language)["reason.${normalize(historical).lowercase(Locale.ROOT)}"] ?: historical
    }
}
