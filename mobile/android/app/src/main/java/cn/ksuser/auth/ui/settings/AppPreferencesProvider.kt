package cn.ksuser.auth.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import cn.ksuser.auth.core.settings.AppPreferences
import cn.ksuser.auth.core.settings.AppPreferencesState

val LocalAppPreferences = staticCompositionLocalOf { AppPreferencesState() }

@Composable
fun ProvideAppPreferences(preferences: AppPreferences, content: @Composable () -> Unit) {
    val state by preferences.state.collectAsState()
    CompositionLocalProvider(LocalAppPreferences provides state, content = content)
}
