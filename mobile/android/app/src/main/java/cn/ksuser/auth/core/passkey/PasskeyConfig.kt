package cn.ksuser.auth.core.passkey

import cn.ksuser.auth.core.env.KsuserEnvironment
import cn.ksuser.auth.data.model.PasskeyAvailability
import java.net.URI
import cn.ksuser.auth.core.settings.L10n

internal fun passkeyDeviceAvailability(sdkInt: Int, hasGoogleProvider: Boolean): PasskeyAvailability = when {
    sdkInt < 28 -> PasskeyAvailability.UnsupportedDevice
    sdkInt < 34 && !hasGoogleProvider -> PasskeyAvailability.MissingProvider
    else -> PasskeyAvailability.Available
}

internal fun passkeyAvailability(environment: KsuserEnvironment): PasskeyAvailability {
    val rpId = environment.passkeyRpId.trim()
    if (rpId.isBlank()) {
        return PasskeyAvailability.RpMismatch
    }

    val origin = parsePasskeyOrigin(environment.passkeyOriginHint) ?: return PasskeyAvailability.DomainNotReady
    if (origin.scheme != "https" || origin.host.isNullOrBlank()) {
        return PasskeyAvailability.DomainNotReady
    }

    return if (hostMatchesRpId(origin.host, rpId)) {
        PasskeyAvailability.Available
    } else {
        PasskeyAvailability.RpMismatch
    }
}

internal fun passkeyAvailabilityMessage(environment: KsuserEnvironment): String {
    return when (passkeyAvailability(environment)) {
        PasskeyAvailability.Available -> L10n.format("设备支持原生 Passkey，当前 RP ID 为 {0}。", environment.passkeyRpId.trim())
        PasskeyAvailability.UnsupportedDevice ->
            L10n.text("当前设备不支持原生 Passkey。")
        PasskeyAvailability.MissingProvider ->
            L10n.text("当前设备没有可用的凭据提供方，请确认已启用 Google Password Manager。")
        PasskeyAvailability.RpMismatch ->
            L10n.format("Passkey RP 配置不一致。PASSKEY_ORIGIN_HINT 必须是 https 域名，且其主机名要能匹配 RP ID {0}。", environment.passkeyRpId.trim())
        PasskeyAvailability.DomainNotReady ->
            L10n.text("当前 Passkey 域未就绪。Android 原生 Passkey 需要一个已配置 Digital Asset Links 的 https 域名，localhost 不能直接作为原生 RP 使用。")
    }
}

internal fun requirePasskeyRpMatch(
    environment: KsuserEnvironment,
    backendRpId: String?,
) {
    val availability = passkeyAvailability(environment)
    if (availability != PasskeyAvailability.Available) {
        error(passkeyAvailabilityMessage(environment))
    }

    val expectedRpId = environment.passkeyRpId.trim()
    val actualRpId = backendRpId?.trim().orEmpty().ifBlank { expectedRpId }
    if (!hostMatchesRpId(actualRpId, expectedRpId) && !hostMatchesRpId(expectedRpId, actualRpId)) {
        error(L10n.format("Passkey RP 配置不一致：应用期望 {0}，但后端返回 {1}。", expectedRpId, actualRpId))
    }
}

internal fun passkeyAssetLinksUrl(originHint: String): String? {
    val origin = parsePasskeyOrigin(originHint) ?: return null
    if (origin.scheme != "https" || origin.host.isNullOrBlank()) {
        return null
    }
    return "${origin.scheme}://${origin.host}/.well-known/assetlinks.json"
}

private fun parsePasskeyOrigin(originHint: String): URI? {
    return runCatching { URI(originHint.trim()) }.getOrNull()
}

private fun hostMatchesRpId(host: String, rpId: String): Boolean {
    return host == rpId || host.endsWith(".$rpId")
}
