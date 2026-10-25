package cn.ksuser.auth.data.authorization

import com.google.gson.Gson

/** Saved in the app's existing encrypted, backup-excluded session preferences, never in Intent extras. */
data class PendingNativeAuthorization(
    val ticket: String,
    val expiresAtMillis: Long,
    val accountUuid: String? = null,
    val appName: String? = null,
    val redirectUri: String? = null,
    val returnUrl: String? = null,
    val approved: Boolean? = null,
)

class NativeAuthorizationStateStore(
    private val readValue: () -> String?,
    private val writeValue: (String?) -> Unit,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val gson = Gson()
    fun read(): PendingNativeAuthorization? {
        val raw = runCatching(readValue).getOrNull() ?: return null
        val value = runCatching { gson.fromJson(raw, PendingNativeAuthorization::class.java) }.getOrNull()
        val valid = runCatching { value != null && MobileAuthorizationLinks.isTicket(value.ticket)
            && value.expiresAtMillis > now() && value.expiresAtMillis <= now() + 300_000
            && (value.returnUrl == null || value.approved != null && !value.accountUuid.isNullOrBlank()
                && !value.appName.isNullOrBlank() && value.redirectUri != null
                && NativeAuthorizationLinks.returnUrl(value.returnUrl, value.redirectUri, value.approved) != null) }.getOrDefault(false)
        if (!valid) { clear(); return null }
        return value
    }
    fun save(value: PendingNativeAuthorization) {
        require(MobileAuthorizationLinks.isTicket(value.ticket) && value.expiresAtMillis > now()) { "授权已过期或数据无效" }
        writeValue(gson.toJson(value))
    }
    fun clear() = writeValue(null)
}
