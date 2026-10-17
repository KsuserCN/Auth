package cn.ksuser.auth.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.AlternateEmail
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DesktopWindows
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Password
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.SyncAlt
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.activity.compose.BackHandler
import cn.ksuser.auth.core.settings.L10n
import cn.ksuser.auth.data.authorization.AuthorizationGrantMode
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cn.ksuser.auth.data.model.QrScanPreview
import cn.ksuser.auth.data.model.UserProfile
import cn.ksuser.auth.ui.components.AppPagePadding
import cn.ksuser.auth.ui.components.AppRadius
import cn.ksuser.auth.ui.components.AppSpacing
import cn.ksuser.auth.ui.components.GradientPrimaryButton
import cn.ksuser.auth.ui.components.SectionCard
import cn.ksuser.auth.ui.theme.BrandButtonGradientEnd
import cn.ksuser.auth.ui.theme.BrandButtonGradientStart
import cn.ksuser.auth.ui.theme.rememberAppBackgroundBrush

@Composable
internal fun QrLoginConfirmScreen(
    pending: PendingQrConfirmation,
    currentUser: UserProfile?,
    isBusy: Boolean,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    onAuthorizationOptionsChanged: (AuthorizationGrantMode, Int, Boolean) -> Unit = { _, _, _ -> },
) {
    if (pending.type in setOf(QrConfirmationType.APPROVE_APPLICATION, QrConfirmationType.APPROVE_OFFICIAL, QrConfirmationType.APPROVE_CONSOLE_LOGIN)) {
        ApplicationConsentScreen(pending, currentUser, isBusy, onConfirm, onCancel, onAuthorizationOptionsChanged)
        return
    }
    BackHandler { if (!isBusy) onCancel() }
    val remaining = requestSecondsRemaining(pending.expiresAtMillis)
    val visual = rememberConfirmationVisual(pending, currentUser)
    val backgroundBrush = rememberAppBackgroundBrush()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundBrush)
            .safeDrawingPadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AppPagePadding, vertical = AppSpacing.S12),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.S16),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onCancel) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = L10n.text("关闭"),
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = L10n.text("扫码确认"),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = visual.pageTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                Spacer(modifier = Modifier.width(48.dp))
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(30.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                tonalElevation = 4.dp,
                shadowElevation = 10.dp,
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = Brush.linearGradient(
                        listOf(
                            BrandButtonGradientStart.copy(alpha = 0.45f),
                            BrandButtonGradientEnd.copy(alpha = 0.20f),
                        ),
                    ),
                ),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.S16),
                ) {
                    ConfirmationHero(visual = visual)

                    pending.preview?.let { preview ->
                        PreviewDetailsCard(preview = preview)
                    }
                    currentUser?.let { user ->
                        SectionCard(modifier = Modifier.fillMaxWidth()) {
                            Text(L10n.text("使用账号"), fontWeight = FontWeight.SemiBold)
                            Text(user.username)
                            Text(user.email ?: L10n.text("未绑定邮箱"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Text(if (remaining > 0) L10n.text("有效期") + ": ${remaining}s" else L10n.text("请求已过期，请重新发起"))

                    SectionCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = L10n.text("安全提醒"),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = visual.securityHint,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.S8)) {
                        GradientPrimaryButton(
                            text = if (isBusy) visual.loadingLabel else visual.confirmLabel,
                            onClick = onConfirm,
                            enabled = !isBusy && remaining > 0,
                            modifier = Modifier.fillMaxWidth(),
                        )

                        OutlinedButton(
                            onClick = onCancel,
                            enabled = !isBusy,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(AppRadius.R12),
                        ) {
                            Text(visual.cancelLabel)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfirmationHero(
    visual: QrConfirmationVisual,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = Color.Transparent,
    ) {
        Box(
            modifier = Modifier
                .background(
                    Brush.linearGradient(
                        listOf(
                            visual.accentSoft,
                            MaterialTheme.colorScheme.surface,
                        ),
                    ),
                    shape = RoundedCornerShape(24.dp),
                )
                .padding(horizontal = 18.dp, vertical = 20.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(AppSpacing.S8),
            ) {
                Surface(
                    modifier = Modifier.size(68.dp),
                    shape = CircleShape,
                    color = visual.accent.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, visual.accent.copy(alpha = 0.18f)),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = visual.icon,
                            contentDescription = null,
                            tint = visual.accent,
                            modifier = Modifier.size(32.dp),
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = visual.accent.copy(alpha = 0.12f),
                ) {
                    Text(
                        text = visual.operationLabel,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = visual.accent,
                    )
                }

                Text(
                    text = visual.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )

                Text(
                    text = visual.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun PreviewDetailsCard(
    preview: QrScanPreview,
) {
    SectionCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = L10n.text("请求来源"),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        PreviewRow(L10n.text("客户端"), preview.clientName)
        PreviewRow(L10n.text("浏览器"), preview.browser)
        PreviewRow(L10n.text("系统"), preview.system)
        PreviewRow(L10n.text("IP 地址"), preview.ipAddress)
        PreviewRow(L10n.text("IP 属地"), preview.ipLocation)
    }
}

@Composable
private fun PreviewRow(
    label: String,
    value: String?,
) {
    val displayValue = value?.trim().orEmpty().ifBlank { L10n.text("未知") }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = displayValue,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
        )
    }
}

private data class QrConfirmationVisual(
    val pageTitle: String,
    val operationLabel: String,
    val title: String,
    val subtitle: String,
    val securityHint: String,
    val confirmLabel: String,
    val cancelLabel: String,
    val loadingLabel: String,
    val accountLabel: String,
    val icon: ImageVector,
    val accent: Color,
    val accentSoft: Color,
)

@Composable
private fun rememberConfirmationVisual(
    pending: PendingQrConfirmation,
    currentUser: UserProfile?,
): QrConfirmationVisual {
    val username = remember(currentUser) {
        currentUser?.username?.takeIf { it.isNotBlank() } ?: L10n.text("当前账号")
    }
    val operation = pending.operationHint ?: defaultOperationHint(pending.type)
    val loginCopy = remember(pending.preview) { resolveLoginCopy(pending.preview) }

    return when (operation) {
        "CHANGE_PASSWORD" -> buildVisual(
            pageTitle = L10n.text("修改密码确认"),
            operationLabel = L10n.text("修改密码"),
            title = L10n.text("是否允许本次密码修改"),
            subtitle = L10n.format("检测到网页端或桌面端正在尝试修改账号 {0} 的登录密码。", username),
            securityHint = L10n.text("确认前请先核对设备信息。若不是你本人正在修改密码，请立即取消并检查账号安全。"),
            confirmLabel = L10n.text("允许修改"),
            loadingLabel = L10n.text("提交中..."),
            icon = Icons.Outlined.Password,
            accent = MaterialTheme.colorScheme.error,
            accentSoft = MaterialTheme.colorScheme.error.copy(alpha = 0.08f),
            accountLabel = username,
        )

        "CHANGE_EMAIL" -> buildVisual(
            pageTitle = L10n.text("修改邮箱确认"),
            operationLabel = L10n.text("修改邮箱"),
            title = L10n.text("是否允许本次邮箱变更"),
            subtitle = L10n.format("检测到网页端或桌面端正在尝试修改账号 {0} 绑定的邮箱地址。", username),
            securityHint = L10n.text("邮箱会影响验证码接收和账号找回。若不是你本人操作，请不要继续。"),
            confirmLabel = L10n.text("允许修改"),
            loadingLabel = L10n.text("提交中..."),
            icon = Icons.Outlined.AlternateEmail,
            accent = MaterialTheme.colorScheme.secondary,
            accentSoft = MaterialTheme.colorScheme.secondary.copy(alpha = 0.10f),
            accountLabel = username,
        )

        "ADD_PASSKEY" -> buildVisual(
            pageTitle = L10n.text("新增 Passkey 确认"),
            operationLabel = L10n.text("新增 Passkey"),
            title = L10n.text("是否允许新增 Passkey"),
            subtitle = L10n.format("检测到网页端或桌面端正在为账号 {0} 绑定新的 Passkey 凭证。", username),
            securityHint = L10n.text("只有在你本人正在添加新的安全凭证时才应继续，避免他人向账号写入可登录凭证。"),
            confirmLabel = L10n.text("允许添加"),
            loadingLabel = L10n.text("提交中..."),
            icon = Icons.Outlined.Key,
            accent = MaterialTheme.colorScheme.primary,
            accentSoft = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
            accountLabel = username,
        )

        "DELETE_PASSKEY" -> buildVisual(
            pageTitle = L10n.text("删除 Passkey 确认"),
            operationLabel = L10n.text("删除 Passkey"),
            title = L10n.text("是否允许删除 Passkey"),
            subtitle = L10n.format("检测到网页端或桌面端正在删除账号 {0} 的某个 Passkey。", username),
            securityHint = L10n.text("删除后可能影响你后续无密码登录。请确认这是你本人在管理安全凭证。"),
            confirmLabel = L10n.text("允许删除"),
            loadingLabel = L10n.text("提交中..."),
            icon = Icons.Outlined.Key,
            accent = MaterialTheme.colorScheme.tertiary,
            accentSoft = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.10f),
            accountLabel = username,
        )

        "ENABLE_TOTP" -> buildVisual(
            pageTitle = L10n.text("启用 TOTP 确认"),
            operationLabel = L10n.text("启用 TOTP"),
            title = L10n.text("是否允许启用 TOTP"),
            subtitle = L10n.format("检测到网页端或桌面端正在为账号 {0} 开启动态验证码验证。", username),
            securityHint = L10n.text("启用后将提升账号安全性，但请确保这是你本人在配置新的验证方式。"),
            confirmLabel = L10n.text("允许启用"),
            loadingLabel = L10n.text("提交中..."),
            icon = Icons.Outlined.Shield,
            accent = MaterialTheme.colorScheme.primary,
            accentSoft = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
            accountLabel = username,
        )

        "DISABLE_TOTP" -> buildVisual(
            pageTitle = L10n.text("禁用 TOTP 确认"),
            operationLabel = L10n.text("禁用 TOTP"),
            title = L10n.text("是否允许禁用 TOTP"),
            subtitle = L10n.format("检测到网页端或桌面端正在尝试关闭账号 {0} 的动态验证码验证。", username),
            securityHint = L10n.text("关闭二次验证会降低账号安全性。若不是你本人在调整安全设置，请立即取消。"),
            confirmLabel = L10n.text("允许关闭"),
            loadingLabel = L10n.text("提交中..."),
            icon = Icons.Outlined.AdminPanelSettings,
            accent = MaterialTheme.colorScheme.error,
            accentSoft = MaterialTheme.colorScheme.error.copy(alpha = 0.08f),
            accountLabel = username,
        )

        "MFA_VERIFY" -> buildVisual(
            pageTitle = L10n.text("验证确认"),
            operationLabel = L10n.text("二次验证"),
            title = L10n.text("是否确认本次登录验证"),
            subtitle = L10n.format("检测到网页端或桌面端正在进行二次验证。确认后，该设备将继续完成账号 {0} 的登录流程。", username),
            securityHint = L10n.text("仅当这是你本人正在登录时才继续。若不是你发起的操作，请立即取消。"),
            confirmLabel = L10n.text("确认验证"),
            loadingLabel = L10n.text("提交中..."),
            icon = Icons.Outlined.Shield,
            accent = MaterialTheme.colorScheme.secondary,
            accentSoft = MaterialTheme.colorScheme.secondary.copy(alpha = 0.10f),
            accountLabel = username,
        )

        "SENSITIVE_VERIFY" -> buildVisual(
            pageTitle = L10n.text("敏感操作确认"),
            operationLabel = L10n.text("敏感操作"),
            title = L10n.text("是否允许本次敏感操作"),
            subtitle = L10n.text("检测到网页端或桌面端正在执行需要额外验证的账号操作。"),
            securityHint = L10n.text("请确认这是你本人正在执行的设置修改、账号安全或其他敏感操作。若非本人操作，请取消。"),
            confirmLabel = L10n.text("允许操作"),
            loadingLabel = L10n.text("提交中..."),
            icon = Icons.Outlined.Security,
            accent = MaterialTheme.colorScheme.tertiary,
            accentSoft = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.10f),
            accountLabel = username,
        )

        "ACCOUNT_RECOVERY" -> buildVisual(
            pageTitle = L10n.text("账号恢复背书"),
            operationLabel = L10n.text("账号恢复"),
            title = L10n.text("是否为当前恢复请求背书"),
            subtitle = L10n.format("检测到网页端或桌面端正在请求为账号 {0} 发起一次性恢复授权。确认后，对方设备可继续重置密码。", username),
            securityHint = L10n.text("仅当这是你本人正在恢复账号时才继续。恢复成功后，旧会话会被撤销，当前恢复设备会获得新的登录态。"),
            confirmLabel = L10n.text("确认背书"),
            loadingLabel = L10n.text("背书中..."),
            icon = Icons.Outlined.Security,
            accent = MaterialTheme.colorScheme.primary,
            accentSoft = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
            accountLabel = username,
        )

        "LOGIN_THIS_PHONE" -> buildVisual(
            pageTitle = L10n.text("登录此手机"),
            operationLabel = L10n.text("跨端登录"),
            title = L10n.text("是否登录此手机"),
            subtitle = L10n.text("确认后，此手机将使用二维码对应账号完成登录，并进入账号主页。"),
            securityHint = L10n.text("请确认二维码来自你信任的网页登录页或设备，避免在陌生场景下登录他人账号。"),
            confirmLabel = L10n.text("确认登录"),
            loadingLabel = L10n.text("登录中..."),
            icon = Icons.Outlined.PhoneAndroid,
            accent = MaterialTheme.colorScheme.primary,
            accentSoft = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
            accountLabel = L10n.text("将登录到本机"),
        )

        "SWITCH_AND_LOGIN_THIS_PHONE" -> buildVisual(
            pageTitle = L10n.text("切换并登录"),
            operationLabel = L10n.text("切换账号登录"),
            title = L10n.text("是否切换并登录此手机"),
            subtitle = L10n.text("当前手机已登录其他账号。确认后，将切换为二维码对应账号并继续使用此设备。"),
            securityHint = L10n.text("切换后，当前账号会被替换。若这不是你本人操作，请取消，避免账号混用或误登录。"),
            confirmLabel = L10n.text("继续登录"),
            cancelLabel = L10n.text("暂不切换"),
            loadingLabel = L10n.text("切换中..."),
            icon = Icons.Outlined.SyncAlt,
            accent = MaterialTheme.colorScheme.tertiary,
            accentSoft = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.10f),
            accountLabel = username,
        )

        else -> buildVisual(
            pageTitle = loginCopy.pageTitle,
            operationLabel = loginCopy.operationLabel,
            title = loginCopy.title,
            subtitle = loginCopy.subtitlePrefix + L10n.format("确认后，对应设备将使用账号 {0} 登录。", username),
            securityHint = loginCopy.securityHint,
            confirmLabel = L10n.text("允许登录"),
            loadingLabel = L10n.text("提交中..."),
            icon = loginCopy.icon,
            accent = MaterialTheme.colorScheme.primary,
            accentSoft = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
            accountLabel = username,
        )
    }
}

@Composable
private fun buildVisual(
    pageTitle: String,
    operationLabel: String,
    title: String,
    subtitle: String,
    securityHint: String,
    confirmLabel: String,
    loadingLabel: String,
    icon: ImageVector,
    accent: Color,
    accentSoft: Color,
    accountLabel: String,
    cancelLabel: String = L10n.text("取消"),
): QrConfirmationVisual {
    return QrConfirmationVisual(
        pageTitle = pageTitle,
        operationLabel = operationLabel,
        title = title,
        subtitle = subtitle,
        securityHint = securityHint,
        confirmLabel = confirmLabel,
        cancelLabel = cancelLabel,
        loadingLabel = loadingLabel,
        accountLabel = accountLabel,
        icon = icon,
        accent = accent,
        accentSoft = accentSoft,
    )
}

private fun defaultOperationHint(type: QrConfirmationType): String {
    return when (type) {
        QrConfirmationType.APPROVE_LOGIN -> "LOGIN"
        QrConfirmationType.APPROVE_MFA -> "MFA_VERIFY"
        QrConfirmationType.APPROVE_SENSITIVE -> "SENSITIVE_VERIFY"
        QrConfirmationType.APPROVE_RECOVERY -> "ACCOUNT_RECOVERY"
        QrConfirmationType.APPROVE_APPLICATION -> "APPLICATION"
        QrConfirmationType.APPROVE_OFFICIAL -> "OFFICIAL"
        QrConfirmationType.APPROVE_CONSOLE_LOGIN -> "CONSOLE"
        QrConfirmationType.LOGIN_THIS_PHONE -> "LOGIN_THIS_PHONE"
        QrConfirmationType.SWITCH_AND_LOGIN_THIS_PHONE -> "SWITCH_AND_LOGIN_THIS_PHONE"
    }
}

private data class LoginCopy(
    val pageTitle: String,
    val operationLabel: String,
    val title: String,
    val subtitlePrefix: String,
    val securityHint: String,
    val icon: ImageVector,
)

private fun resolveLoginCopy(preview: QrScanPreview?): LoginCopy {
    val clientName = preview?.clientName.orEmpty().trim()
    val browser = preview?.browser.orEmpty().trim()
    val isDesktop = clientName.contains(L10n.text("桌面"), ignoreCase = true) ||
        browser.startsWith("Ksuser Auth Desktop", ignoreCase = true)

    return if (isDesktop) {
        LoginCopy(
            pageTitle = L10n.text("桌面登录确认"),
            operationLabel = L10n.text("桌面端登录"),
            title = L10n.text("确认这次桌面登录吗"),
            subtitlePrefix = L10n.text("检测到一台桌面设备正在请求登录。 "),
            securityHint = L10n.text("请确认这是你本人正在使用的桌面设备。若非本人发起，请立即取消，避免账号被其他电脑登录。"),
            icon = Icons.Outlined.DesktopWindows,
        )
    } else {
        LoginCopy(
            pageTitle = L10n.text("网页登录确认"),
            operationLabel = L10n.text("网页登录"),
            title = L10n.text("确认这次网页登录吗"),
            subtitlePrefix = L10n.text("检测到浏览器发起新的登录请求。 "),
            securityHint = L10n.text("请确认这是你本人正在访问的网页。若非本人发起，请立即取消，避免账号被其他浏览器登录。"),
            icon = Icons.Outlined.Security,
        )
    }
}
