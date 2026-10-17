package cn.ksuser.auth.data.model

data class AccountLinkStatus(val provider: String, val bound: Boolean)
data class AppleLinkStatus(val bound: Boolean)
data class AuthorizedApplication(
    val appId: String,
    val appName: String,
    val logoUrl: String? = null,
    val creatorName: String? = null,
    val creatorVerificationType: String? = null,
    val contactInfo: String? = null,
    val redirectUri: String? = null,
    val scopes: List<String> = emptyList(),
    val authorizedAt: String? = null,
    val lastAuthorizedAt: String? = null,
    val grantMode: String = "PERSISTENT",
    val expiresAt: String? = null,
)
data class OfficialAuthorizedApplication(
    val clientId: String,
    val clientName: String,
    val logoUrl: String? = null,
    val scopes: List<String> = emptyList(),
    val authorizedAt: String? = null,
    val lastAuthorizedAt: String? = null,
)
data class AuthorizedApplications(
    val thirdParty: List<AuthorizedApplication>,
    val official: List<OfficialAuthorizedApplication>,
)
