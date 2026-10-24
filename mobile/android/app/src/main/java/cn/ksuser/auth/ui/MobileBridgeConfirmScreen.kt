package cn.ksuser.auth.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.activity.compose.BackHandler
import cn.ksuser.auth.core.settings.L10n
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cn.ksuser.auth.data.model.UserProfile
import cn.ksuser.auth.ui.components.AppRadius
import cn.ksuser.auth.ui.components.GradientPrimaryButton
import cn.ksuser.auth.ui.components.SectionCard

@Composable
internal fun MobileBridgeConfirmScreen(
    pending: PendingMobileBridgeConfirmation,
    currentUser: UserProfile?,
    isBusy: Boolean,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    onContinueToLogin: () -> Unit,
) {
    val remaining = requestSecondsRemaining(pending.expiresAtMillis)
    BackHandler { if (!isBusy) onCancel() }
    ConfirmationScreenLayout(
        title = L10n.text("网页登录确认"),
        isBusy = isBusy,
        onCancel = onCancel,
        actions = {
            Text(
                if (remaining > 0) L10n.text("有效期") + ": ${remaining}s" else L10n.text("请求已过期，请重新发起"),
                style = MaterialTheme.typography.bodySmall,
                color = if (remaining > 0) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
            )
            GradientPrimaryButton(
                text = when {
                    pending.requiresLogin -> L10n.text("去登录")
                    isBusy -> L10n.text("确认中...")
                    else -> L10n.text("确认网页登录")
                },
                onClick = if (pending.requiresLogin) onContinueToLogin else onConfirm,
                enabled = !isBusy && remaining > 0,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedButton(
                onClick = onCancel,
                enabled = !isBusy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                shape = RoundedCornerShape(AppRadius.R12),
            ) { Text(if (pending.requiresLogin) L10n.text("关闭") else L10n.text("取消请求")) }
        },
    ) {
        MobileBridgeHero(pending.requiresLogin, pending.returnOrigin)
        if (!pending.requiresLogin && currentUser != null) {
            SectionCard(Modifier.fillMaxWidth()) {
                Text(L10n.text("当前 App 账号"), style = MaterialTheme.typography.titleMedium)
                Text(currentUser.username, style = MaterialTheme.typography.bodyLarge)
                Text(currentUser.email ?: L10n.text("未绑定邮箱"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        SectionCard(Modifier.fillMaxWidth()) {
            Text(L10n.text("目标站点"), style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Outlined.Public, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(pending.returnOrigin ?: L10n.text("当前 Ksuser 网页"), style = MaterialTheme.typography.bodyLarge)
            }
        }
        SectionCard(Modifier.fillMaxWidth()) {
            Text(L10n.text("安全提醒"), style = MaterialTheme.typography.titleMedium)
            Text(
                if (pending.requiresLogin) L10n.text("请先在当前 App 内登录，完成后将继续原网页登录请求。")
                else L10n.text("浏览器已发起网页登录请求，确认后网页会自动建立自己的新会话。"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MobileBridgeHero(requiresLogin: Boolean, returnOrigin: String?) {
    val accent = MaterialTheme.colorScheme.primary
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(56.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(if (requiresLogin) Icons.Outlined.PhoneAndroid else Icons.Outlined.Language, contentDescription = null, tint = accent, modifier = Modifier.size(28.dp))
                }
            }
            Text(if (requiresLogin) L10n.text("登录后继续") else L10n.text("网页登录"), style = MaterialTheme.typography.titleMedium, color = accent)
        }
        Text(if (requiresLogin) L10n.text("需要先登录 App") else L10n.text("确认网页登录请求"), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(returnOrigin ?: L10n.text("来源于当前 Ksuser 网页"), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            if (requiresLogin) L10n.text("请先在当前 App 完成账号登录，完成后将继续原请求。")
            else L10n.text("确认后网页端会创建新会话，不会影响当前 App 内的登录状态。"),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
