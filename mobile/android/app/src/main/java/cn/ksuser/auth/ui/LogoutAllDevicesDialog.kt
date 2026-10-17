package cn.ksuser.auth.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import cn.ksuser.auth.core.network.runCatchingCancellable
import cn.ksuser.auth.core.security.ConfirmedSensitiveAction
import cn.ksuser.auth.core.settings.L10n
import cn.ksuser.auth.data.AppContainer
import cn.ksuser.auth.ui.components.LoadingTextButton
import kotlinx.coroutines.launch

@Composable
internal fun LogoutAllDevicesDialog(container: AppContainer, onDismiss: () -> Unit, onConfirmed: () -> Unit, onMessage: (String) -> Unit) {
    val authorization = remember(container) { ConfirmedSensitiveAction { container.securityRepository.getSensitiveVerificationStatus() } }
    var verifying by remember { mutableStateOf(false) }
    var completing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    DisposableEffect(authorization) { onDispose { authorization.cancel() } }

    suspend fun continueConfirmedAction() {
        if (completing) return
        completing = true
        try {
            runCatchingCancellable { authorization.consumeIfVerified() }.onSuccess { allowed ->
                if (allowed) { onDismiss(); onConfirmed() }
                else if (authorization.isPending()) verifying = true
            }.onFailure { onMessage(it.message ?: L10n.text("读取敏感验证状态失败")) }
        } finally { completing = false }
    }

    if (verifying) SensitiveVerificationDialog(container = container,
        onDismiss = { authorization.cancel(); onDismiss() },
        onVerified = { scope.launch { continueConfirmedAction() } }, onMessage = onMessage)
    else AlertDialog(onDismissRequest = { authorization.cancel(); onDismiss() },
        title = { Text(L10n.text("退出全部设备")) },
        text = { Text(L10n.text("从所有设备退出登录将撤销所有会话（包括当前设备），需要重新登录。")) },
        confirmButton = { LoadingTextButton(L10n.text("确认退出全部设备"), onClick = { authorization.confirm(); continueConfirmedAction() }) },
        dismissButton = { TextButton(onClick = { authorization.cancel(); onDismiss() }) { Text(L10n.text("取消")) } })
}
