package cn.ksuser.demo.auth

import cn.ksuser.auth.sdk.KsuserAuthConfiguration
import cn.ksuser.auth.sdk.KsuserAuthorizationMode

/** Edit DemoConfiguration.properties; Gradle properties can override these public values. */
object DemoConfiguration {
    val redirectUri: String get() = BuildConfig.REDIRECT_URI
    val apiBaseUrl: String get() = BuildConfig.API_BASE_URL
    val webBaseUrl: String get() = BuildConfig.WEB_BASE_URL
    val officialIssuer: String get() = BuildConfig.OFFICIAL_ISSUER

    fun clientId(mode: KsuserAuthorizationMode): String = when (mode) {
        KsuserAuthorizationMode.OAUTH -> BuildConfig.OAUTH_CLIENT_ID
        KsuserAuthorizationMode.OFFICIAL -> BuildConfig.OFFICIAL_CLIENT_ID
    }.trim()

    fun scopes(mode: KsuserAuthorizationMode): List<String> = when (mode) {
        KsuserAuthorizationMode.OAUTH -> listOf("profile")
        KsuserAuthorizationMode.OFFICIAL -> listOf("openid", "profile")
    }

    fun isConfigured(mode: KsuserAuthorizationMode): Boolean {
        val id = clientId(mode)
        return id.isNotEmpty() && !id.startsWith("REPLACE_", ignoreCase = true)
            && !id.startsWith("your-", ignoreCase = true)
    }

    fun sdkConfiguration(mode: KsuserAuthorizationMode) = KsuserAuthConfiguration(
        clientId = clientId(mode),
        mode = mode,
        redirectUri = redirectUri,
        apiBaseUrl = apiBaseUrl,
        webBaseUrl = webBaseUrl,
        officialIssuer = officialIssuer,
        trustedProviderCertificateSha256 = BuildConfig.PROVIDER_CERTIFICATES
            .split(',').map(String::trim).filter(String::isNotEmpty),
    )
}
