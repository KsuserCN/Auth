package cn.ksuser.auth.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import cn.ksuser.auth.core.settings.L10n
import cn.ksuser.auth.data.AppContainer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AboutSettingsDialog(container: AppContainer, onDismiss: () -> Unit) {
    Dialog(onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BackHandler(onBack = onDismiss)
        Scaffold(Modifier.fillMaxSize(), topBar = {
            TopAppBar(title = { Text(L10n.text("关于与设置")) }, navigationIcon = {
                TextButton(onClick = onDismiss) { Text(L10n.text("返回")) }
            })
        }) { padding -> androidx.compose.foundation.layout.Box(Modifier.padding(padding)) { AboutScreen(container) } }
    }
}
