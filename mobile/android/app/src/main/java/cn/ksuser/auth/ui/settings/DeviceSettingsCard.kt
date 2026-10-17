package cn.ksuser.auth.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cn.ksuser.auth.core.settings.L10n
import cn.ksuser.auth.data.AppContainer
import cn.ksuser.auth.ui.components.SectionCard

@Composable
internal fun DeviceSettingsCard(container: AppContainer) {
    val preferences = LocalAppPreferences.current
    SectionCard(Modifier.fillMaxWidth()) {
        Text(L10n.text("语言与外观"), style = MaterialTheme.typography.titleLarge)
        Text(L10n.text("语言"))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("system" to L10n.text("跟随系统"), "zh-Hans" to "简体中文", "en" to "English").forEach { (value, label) ->
                FilterChip(selected = preferences.language == value,
                    onClick = { container.preferences.setLanguage(value) }, label = { Text(label) })
            }
        }
        Text(L10n.text("外观"))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("system" to "跟随系统", "light" to "浅色", "dark" to "深色").forEach { (value, label) ->
                FilterChip(selected = preferences.theme == value,
                    onClick = { container.preferences.setTheme(value) }, label = { Text(L10n.text(label)) })
            }
        }
    }
}
