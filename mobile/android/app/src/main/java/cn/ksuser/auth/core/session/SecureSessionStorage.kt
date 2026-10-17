package cn.ksuser.auth.core.session

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

import cn.ksuser.auth.data.model.UserProfile
import com.google.gson.Gson

/** Credentials share one encrypted preferences transaction when an account changes. */
class SecureSessionStorage(context: Context) : SessionTokenStore {
    internal val preferences: SharedPreferences = openRecoverably(context)
    private val gson = Gson()

    override fun getAccessToken(): String? = runCatching { preferences.getString("access_token", null) }
        .getOrElse { clear(); null }
    override fun setAccessToken(token: String) { preferences.edit().putString("access_token", token).commit() }
    override fun getProfile(): UserProfile? = runCatching {
        preferences.getString("profile", null)?.let { gson.fromJson(it, UserProfile::class.java) }
    }.getOrNull()
    override fun setProfile(profile: UserProfile) { preferences.edit().putString("profile", gson.toJson(profile)).commit() }
    fun replace(token: String, cookies: String, profile: UserProfile?) {
        check(preferences.edit().putString("access_token", token).putString("cookies", cookies)
            .putBoolean("cookies_migrated", true)
            .putString("profile", profile?.let { gson.toJson(it) }).commit()) { "Unable to save session" }
    }
    override fun clear() { preferences.edit().remove("access_token").remove("profile").remove("cookies").commit() }

    private fun openRecoverably(context: Context): SharedPreferences {
        fun open(name: String) = EncryptedSharedPreferences.create(context, name,
            MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
        val prefs = try { open("ksuser_secure_session") } catch (_: Exception) {
            context.deleteSharedPreferences("ksuser_secure_session")
            context.deleteSharedPreferences("ksuser_cookie_store")
            open("ksuser_secure_session")
        }
        if (!prefs.contains("cookies_migrated")) {
            val cookies = runCatching { open("ksuser_cookie_store").getString("cookies", null) }.getOrNull()
            check(prefs.edit().putString("cookies", cookies).putBoolean("cookies_migrated", true).commit())
            context.deleteSharedPreferences("ksuser_cookie_store")
        }
        return prefs
    }
}
