package cn.ksuser.auth.core.network

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

private const val COOKIE_PREFS_NAME = "ksuser_cookie_store"
private const val COOKIE_PREFS_KEY = "cookies"

data class SerializableCookie(
    val name: String,
    val value: String,
    val expiresAt: Long,
    val domain: String,
    val path: String,
    val secure: Boolean,
    val httpOnly: Boolean,
    val persistent: Boolean,
    val hostOnly: Boolean,
) {
    fun toCookie(): Cookie {
        val builder = Cookie.Builder()
            .name(name)
            .value(value)
            .path(path)
            .expiresAt(expiresAt)

        if (hostOnly) {
            builder.hostOnlyDomain(domain)
        } else {
            builder.domain(domain)
        }
        if (secure) builder.secure()
        if (httpOnly) builder.httpOnly()

        return builder.build()
    }

    companion object {
        fun fromCookie(cookie: Cookie): SerializableCookie = SerializableCookie(
            name = cookie.name,
            value = cookie.value,
            expiresAt = cookie.expiresAt,
            domain = cookie.domain,
            path = cookie.path,
            secure = cookie.secure,
            httpOnly = cookie.httpOnly,
            persistent = cookie.persistent,
            hostOnly = cookie.hostOnly,
        )
    }
}

class CookieStore private constructor(
    private val gson: Gson,
    private val prefs: SharedPreferences?,
) {
    constructor(context: Context, gson: Gson, preferences: SharedPreferences? = null) : this(gson,
        preferences ?: EncryptedSharedPreferences.create(context, COOKIE_PREFS_NAME,
            MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM))
    companion object { fun inMemory(gson: Gson) = CookieStore(gson, null) }
    private var memoryCookies: List<SerializableCookie> = emptyList()
    private val lock = Any()

    fun snapshot(): List<SerializableCookie> = synchronized(lock) { readCookies().filterNot(::isExpired) }
    fun replaceAll(cookies: List<SerializableCookie>) = synchronized(lock) { writeCookies(cookies.filterNot(::isExpired)) }

    fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        synchronized(lock) {
            val current = readCookies()
                .filterNot { existing ->
                    cookies.any {
                        it.name == existing.name &&
                            it.domain == existing.domain &&
                            it.path == existing.path
                    }
                }
                .filter { !isExpired(it) }
                .toMutableList()

            current.addAll(
                cookies
                    .map(SerializableCookie::fromCookie)
                    .filterNot(::isExpired),
            )

            writeCookies(current)
        }
    }

    fun loadForRequest(url: HttpUrl): List<Cookie> = synchronized(lock) {
        val current = readCookies()
        val valid = current.filterNot(::isExpired)
        if (valid.size != current.size) {
            writeCookies(valid)
        }
        valid.map(SerializableCookie::toCookie).filter { it.matches(url) }
    }

    fun findCookieValue(url: HttpUrl, name: String): String? {
        return loadForRequest(url).firstOrNull { it.name == name }?.value
    }

    fun hasCookie(url: HttpUrl, name: String): Boolean {
        return loadForRequest(url).any { it.name == name }
    }

    fun hasCookie(name: String): Boolean = synchronized(lock) {
        val current = readCookies()
        val valid = current.filterNot(::isExpired)
        if (valid.size != current.size) {
            writeCookies(valid)
        }
        valid.any { it.name == name }
    }

    fun clear() {
        synchronized(lock) {
            memoryCookies = emptyList()
            prefs?.edit()?.remove(COOKIE_PREFS_KEY)?.commit()
        }
    }

    private fun readCookies(): List<SerializableCookie> {
        val storage = prefs ?: return memoryCookies
        val raw = runCatching { storage.getString(COOKIE_PREFS_KEY, null) }.getOrElse {
            // Recover a damaged encrypted value together with its paired token/profile.
            storage.edit().remove(COOKIE_PREFS_KEY).remove("access_token").remove("profile").commit()
            null
        } ?: return emptyList()
        val type = object : TypeToken<List<SerializableCookie>>() {}.type
        return runCatching { gson.fromJson<List<SerializableCookie>>(raw, type) }.getOrNull() ?: emptyList()
    }

    private fun writeCookies(cookies: List<SerializableCookie>) {
        memoryCookies = cookies.toList()
        prefs?.edit()?.putString(COOKIE_PREFS_KEY, gson.toJson(cookies))?.commit()
    }

    private fun isExpired(cookie: SerializableCookie): Boolean = cookie.expiresAt <= System.currentTimeMillis()
}

class PersistentCookieJar(
    private val cookieStore: CookieStore,
) : CookieJar {
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        cookieStore.saveFromResponse(url, cookies)
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> = cookieStore.loadForRequest(url)
}
