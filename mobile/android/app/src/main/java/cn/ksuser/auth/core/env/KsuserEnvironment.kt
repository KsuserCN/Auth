package cn.ksuser.auth.core.env

import cn.ksuser.auth.BuildConfig

data class KsuserEnvironment(
    val apiBaseUrl: String,
    val passkeyRpId: String,
    val passkeyOriginHint: String,
    val updateManifestUrl: String,
    val appEnv: String,
    val enableHttpLogging: Boolean,
)

object EnvironmentProvider {
    val current = KsuserEnvironment(
        apiBaseUrl = BuildConfig.API_BASE_URL.trimEnd('/'),
        passkeyRpId = BuildConfig.PASSKEY_RP_ID,
        passkeyOriginHint = BuildConfig.PASSKEY_ORIGIN_HINT,
        updateManifestUrl = BuildConfig.UPDATE_MANIFEST_URL,
        appEnv = BuildConfig.APP_ENV,
        enableHttpLogging = BuildConfig.ENABLE_HTTP_LOGGING,
    )
}
