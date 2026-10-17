package cn.ksuser.auth.core.settings

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AppPreferencesState(val language: String = "system", val theme: String = "system")

/** Device preferences intentionally survive sign out and never recreate the activity. */
class AppPreferences(context: Context) {
    private val storage = context.getSharedPreferences("app_preferences", Context.MODE_PRIVATE)
    private val mutableState = MutableStateFlow(AppPreferencesState(
        storage.getString("language", "system")?.takeIf { it in setOf("system", "zh-Hans", "en") } ?: "system",
        storage.getString("theme", "system")?.takeIf { it in setOf("system", "light", "dark") } ?: "system",
    ))
    val state = mutableState.asStateFlow()

    init {
        L10n.initialize(context)
        refreshSystemLanguage()
    }

    fun setLanguage(value: String) {
        require(value in setOf("system", "zh-Hans", "en"))
        storage.edit().putString("language", value).apply()
        mutableState.value = mutableState.value.copy(language = value)
        refreshSystemLanguage()
    }

    fun setTheme(value: String) {
        require(value in setOf("system", "light", "dark"))
        storage.edit().putString("theme", value).apply()
        mutableState.value = mutableState.value.copy(theme = value)
    }

    fun refreshSystemLanguage() {
        L10n.language = resolveLanguage(mutableState.value.language, Locale.getDefault().toLanguageTag())
    }

    companion object {
        fun resolveLanguage(preference: String, system: String): String = when (preference) {
            "zh-Hans", "en" -> preference
            else -> if (system.lowercase(Locale.ROOT).startsWith("zh")) "zh-Hans" else "en"
        }
    }
}

object L10n {
    var language by mutableStateOf("zh-Hans")
        internal set
    private var english: Map<String, String> = emptyMap()

    internal fun initialize(context: Context) {
        if (english.isNotEmpty()) return
        val data = context.assets.open("ui-en.json").bufferedReader().use { JSONObject(it.readText()) }
        english = data.keys().asSequence().associateWith { data.getString(it) }
    }

    fun text(value: String): String {
        return translatedLiteral(value, language, english)
    }

    fun format(template: String, vararg values: Any?): String = Regex("\\{(\\d+)\\}").replace(text(template)) { match ->
        values.getOrNull(match.groupValues[1].toInt())?.toString().orEmpty()
    }

    fun documentationUrl(path: String): String =
        "https://docs.ksuser.cn/${if (language == "en") "en/" else ""}${path.trimStart('/')}"
}

/** Text translates only explicit catalog keys; variable data belongs in format arguments. */
internal fun translatedLiteral(value: String, language: String, messages: Map<String, String>): String =
    if (language == "en") messages[value] ?: value else value
