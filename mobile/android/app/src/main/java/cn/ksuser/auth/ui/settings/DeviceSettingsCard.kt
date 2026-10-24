package cn.ksuser.auth.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cn.ksuser.auth.core.settings.AppPreferencesState
import cn.ksuser.auth.core.settings.L10n
import cn.ksuser.auth.data.AppContainer
import cn.ksuser.auth.ui.components.SectionCard

@Composable
internal fun DeviceSettingsCard(container: AppContainer) {
    DeviceSettingsContent(
        preferences = LocalAppPreferences.current,
        onLanguageChange = container.preferences::setLanguage,
        onThemeChange = container.preferences::setTheme,
    )
}

@Composable
internal fun DeviceSettingsContent(
    preferences: AppPreferencesState,
    onLanguageChange: (String) -> Unit,
    onThemeChange: (String) -> Unit,
) {
    SectionCard(Modifier.fillMaxWidth(), contentSpacing = 20.dp) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Translate, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            }
            Text(L10n.text("语言与外观"), style = MaterialTheme.typography.titleLarge)
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(L10n.text("语言"), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .selectableGroup(),
            ) {
                val languages = listOf("system" to L10n.text("跟随系统"), "zh-Hans" to "简体中文", "en" to "English")
                languages.forEachIndexed { index, (value, label) ->
                    val selected = preferences.language == value
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f) else Color.Transparent)
                            .selectable(selected = selected, role = Role.RadioButton, onClick = { onLanguageChange(value) })
                            .heightIn(min = 56.dp)
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            label,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        )
                        RadioButton(selected = selected, onClick = null, modifier = Modifier.size(24.dp))
                    }
                    if (index < languages.lastIndex) {
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 14.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(L10n.text("外观"), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min).selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf("system" to "跟随系统", "light" to "浅色", "dark" to "深色").forEach { (value, label) ->
                    ThemeChoice(
                        value = value,
                        label = L10n.text(label),
                        selected = preferences.theme == value,
                        onClick = { onThemeChange(value) },
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
            }
        }
    }
}

@Composable
private fun ThemeChoice(
    value: String,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f) else MaterialTheme.colorScheme.surface)
            .border(BorderStroke(if (selected) 1.5.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant), shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .heightIn(min = 118.dp)
            .padding(horizontal = 8.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(contentAlignment = Alignment.TopEnd) {
            ThemePreview(value)
            if (selected) {
                Icon(
                    Icons.Outlined.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp).background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(8.dp)),
                )
            }
        }
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ThemePreview(value: String) {
    val dark = Color(0xFF22251F)
    val light = Color(0xFFF9F9F5)
    Box(
        modifier = Modifier
            .size(width = 42.dp, height = 52.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)),
    ) {
        Row(Modifier.matchParentSize()) {
            Box(Modifier.weight(1f).fillMaxHeight().background(if (value == "dark") dark else light))
            Box(Modifier.weight(1f).fillMaxHeight().background(if (value == "light") light else dark))
        }
        Column(Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(Modifier.size(width = 14.dp, height = 4.dp).background(Color(0xFFF1C66E), RoundedCornerShape(2.dp)))
            Box(Modifier.fillMaxWidth().height(12.dp).background(Color(0xFFB5BAAC).copy(alpha = 0.35f), RoundedCornerShape(3.dp)))
            Box(Modifier.fillMaxWidth().height(8.dp).background(Color(0xFFB5BAAC).copy(alpha = 0.25f), RoundedCornerShape(2.dp)))
        }
    }
}
