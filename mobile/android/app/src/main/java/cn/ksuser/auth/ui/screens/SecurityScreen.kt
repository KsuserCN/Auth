package cn.ksuser.auth.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Password
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import cn.ksuser.auth.core.env.EnvironmentProvider
import cn.ksuser.auth.core.network.runCatchingCancellable
import cn.ksuser.auth.data.repository.SecurityDashboardLoader
import cn.ksuser.auth.core.settings.L10n
import cn.ksuser.auth.core.settings.DisplayTime
import cn.ksuser.auth.data.AppContainer
import cn.ksuser.auth.data.model.AdaptiveAuthStatus
import cn.ksuser.auth.data.model.AccountRecoveryTicket
import cn.ksuser.auth.data.model.PasskeyAvailability
import cn.ksuser.auth.data.model.PasskeyListItem
import cn.ksuser.auth.data.model.SensitiveVerificationStatus
import cn.ksuser.auth.data.model.TotpRegistrationOptions
import cn.ksuser.auth.data.model.TotpStatus
import cn.ksuser.auth.data.model.UserProfile
import cn.ksuser.auth.data.model.PasswordRequirement
import android.view.accessibility.AccessibilityManager
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import cn.ksuser.auth.ui.components.AppOutlinedField
import cn.ksuser.auth.ui.components.AppPagePadding
import cn.ksuser.auth.ui.components.AppSpacing
import cn.ksuser.auth.ui.components.LoadingButton
import cn.ksuser.auth.ui.components.LoadingOutlinedButton
import cn.ksuser.auth.ui.components.LoadingTextButton
import cn.ksuser.auth.ui.components.SectionCard
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.sqrt

private const val DELETE_ACCOUNT_CONFIRM_TEXT = "我真的不想要我的号辣"
private const val GYRO_SHAKE_THRESHOLD = 3.2f
private const val ACCEL_SHAKE_THRESHOLD = 14f
private const val GYRO_SHAKE_COOLDOWN_MS = 300L

@Composable
internal fun SecurityScreen(
    container: AppContainer,
    user: UserProfile?,
    onUserRefresh: () -> Unit,
    onLogoutAll: () -> Unit,
    onLogoutCurrent: () -> Unit,
    onAccountDeleted: () -> Unit,
    onOpenQrScanner: () -> Unit,
    onMessage: (String) -> Unit,
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val clipboardManager = LocalClipboardManager.current
    val passkeyAvailability = remember(container) { container.passkeyManager.availability() }
    val passkeyAvailabilityMessage = remember(container, L10n.language) { container.passkeyManager.availabilityMessage() }
    val securityKeyAvailable = activity != null && container.securityKeyManager.available(activity)
    val scope = rememberCoroutineScope()
    val dashboardLoader = remember(container) { SecurityDashboardLoader(container.securityRepository, container.accountRepository) }
    var settings by remember(user?.settings) { mutableStateOf(user?.settings) }
    var totpStatus by remember { mutableStateOf<TotpStatus?>(null) }
    var passkeys by remember { mutableStateOf<List<PasskeyListItem>>(emptyList()) }
    var sensitiveStatus by remember { mutableStateOf<SensitiveVerificationStatus?>(null) }
    var adaptiveAuthStatus by remember { mutableStateOf<AdaptiveAuthStatus?>(null) }
    var recoveryCodes by remember { mutableStateOf<List<String>>(emptyList()) }
    var pendingTotpSetup by remember { mutableStateOf<TotpRegistrationOptions?>(null) }
    var totpCode by rememberSaveable { mutableStateOf("") }
    var showSensitiveDialog by remember { mutableStateOf(false) }
    var showAddPasskeyDialog by remember { mutableStateOf(false) }
    var newPasskeyUseHardware by remember { mutableStateOf(false) }
    var newPasskeyName by rememberSaveable { mutableStateOf("Ksuser Android") }
    var renameTarget by remember { mutableStateOf<PasskeyListItem?>(null) }
    var renameDraft by rememberSaveable { mutableStateOf("") }
    var detailTarget by remember { mutableStateOf<PasskeyListItem?>(null) }
    var showChangeEmailDialog by remember { mutableStateOf(false) }
    var showChangePasswordDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showAccountRecoveryDialog by remember { mutableStateOf(false) }
    var accountRecoveryTicket by remember { mutableStateOf<AccountRecoveryTicket?>(null) }
    var accountRecoveryRemainingSeconds by remember { mutableStateOf(0L) }
    var accountRecoveryBusy by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var pendingAction by remember { mutableStateOf<PendingSecurityAction?>(null) }
    var recoveryCodesSaved by remember(pendingTotpSetup) { mutableStateOf(false) }
    var qqBound by remember(user?.uuid) { mutableStateOf<Boolean?>(null) }
    var appleBound by remember(user?.uuid) { mutableStateOf<Boolean?>(null) }
    var confirmUnbindProvider by remember { mutableStateOf<String?>(null) }
    var loadErrors by remember { mutableStateOf<List<Pair<String, Throwable>>>(emptyList()) }
    var loadGeneration by remember { mutableStateOf(0) }
    var passkeysLoaded by remember { mutableStateOf(false) }
    var riskDetailsExpanded by rememberSaveable(user?.uuid) { mutableStateOf(false) }

    suspend fun issueAccountRecoveryTicket(showCreatedMessage: Boolean = true) {
        val hadExistingTicket = accountRecoveryTicket != null
        accountRecoveryBusy = true
        showAccountRecoveryDialog = true
        runCatchingCancellable { container.securityRepository.issueAccountRecoveryTicket() }
            .onSuccess {
                accountRecoveryTicket = it
                accountRecoveryRemainingSeconds = it.expiresInSeconds
                if (showCreatedMessage) {
                    onMessage(L10n.text("恢复授权已生成"))
                }
            }
            .onFailure {
                if (!hadExistingTicket) {
                    showAccountRecoveryDialog = false
                }
                onMessage(it.message ?: L10n.text("生成恢复授权失败"))
            }
        accountRecoveryBusy = false
    }

    suspend fun reloadAll() {
        val request = ++loadGeneration
        val account = user?.uuid
        busy = true
        try {
            val snapshot = dashboardLoader.load()
            if (request != loadGeneration || account != container.sessionRepository.cachedUser()?.uuid) return
            val failures = mutableListOf<Pair<String, Throwable>>()
            snapshot.totp.onSuccess { totpStatus = it }.onFailure { failures += "TOTP" to it }
            snapshot.passkeys.onSuccess { passkeys = it; passkeysLoaded = true }.onFailure { failures += "Passkey" to it }
            snapshot.sensitive.onSuccess { sensitiveStatus = it }.onFailure { sensitiveStatus = null; failures += "敏感验证" to it }
            snapshot.adaptive.onSuccess { adaptiveAuthStatus = it }.onFailure { failures += "会话风险" to it }
            snapshot.qq.onSuccess { qqBound = it }.onFailure { failures += "QQ" to it }
            snapshot.apple.onSuccess { appleBound = it }.onFailure { failures += "Apple" to it }
            loadErrors = failures
        } finally {
            if (request == loadGeneration) busy = false
        }
    }

    LaunchedEffect(user?.uuid) {
        settings = user?.settings
        reloadAll()
    }

    LaunchedEffect(showAccountRecoveryDialog, accountRecoveryTicket?.recoveryCode) {
        if (!showAccountRecoveryDialog || accountRecoveryRemainingSeconds <= 0) return@LaunchedEffect
        while (showAccountRecoveryDialog && accountRecoveryRemainingSeconds > 0) {
            delay(1000)
            accountRecoveryRemainingSeconds -= 1
        }
    }

    LaunchedEffect(sensitiveStatus?.verified) {
        while (sensitiveStatus?.verified == true && (sensitiveStatus?.remainingSeconds ?: 0) > 0) {
            delay(1000)
            val remaining = ((sensitiveStatus?.remainingSeconds ?: 0) - 1).coerceAtLeast(0)
            sensitiveStatus = sensitiveStatus?.copy(remainingSeconds = remaining, verified = remaining > 0)
        }
        if (sensitiveStatus?.verified != true && !showSensitiveDialog) pendingAction = null
    }

    fun requireSensitiveVerification(action: PendingSecurityAction) {
        if (busy || pendingAction != null) return
        scope.launch {
            busy = true
            runCatchingCancellable { container.securityRepository.getSensitiveVerificationStatus() }
                .onSuccess { status ->
                    sensitiveStatus = status
                    pendingAction = action
                    if (!status.verified || status.remainingSeconds <= 0) showSensitiveDialog = true
                }.onFailure { onMessage(it.message ?: L10n.text("读取敏感验证状态失败")) }
            busy = false
        }
    }

    LaunchedEffect(pendingAction, sensitiveStatus?.verified) {
        val action = pendingAction ?: return@LaunchedEffect
        if (sensitiveStatus?.verified != true || (sensitiveStatus?.remainingSeconds ?: 0) <= 0) return@LaunchedEffect
        when (action) {
            is PendingSecurityAction.UpdateBooleanSetting, is PendingSecurityAction.UpdateStringSetting -> {
                scope.launch {
                    busy = true
                    try {
                        runCatchingCancellable {
                            when (action) {
                                is PendingSecurityAction.UpdateBooleanSetting -> container.securityRepository.updateBooleanSetting(action.field, action.value)
                                is PendingSecurityAction.UpdateStringSetting -> container.securityRepository.updateStringSetting(action.field, action.value)
                                else -> error("Unsupported setting action")
                            }
                        }.onSuccess { updated ->
                            settings = updated
                            onUserRefresh()
                            onMessage(L10n.text("已更新"))
                        }.onFailure { onMessage(it.message ?: L10n.text("更新失败")) }
                    } finally { pendingAction = null; busy = false }
                }
            }
            PendingSecurityAction.BindQq, PendingSecurityAction.UnbindQq, PendingSecurityAction.UnbindApple -> {
                pendingAction = null
                scope.launch {
                    busy = true
                    runCatchingCancellable {
                        when (action) {
                            PendingSecurityAction.BindQq -> {
                                check(activity != null) { L10n.text("当前上下文不支持 QQ") }
                                container.accountRepository.bindQq(container.qqLoginManager.login(activity))
                            }
                            PendingSecurityAction.UnbindQq -> container.accountRepository.unbindQq()
                            else -> container.accountRepository.unbindApple()
                        }
                    }.onSuccess { onUserRefresh(); reloadAll(); onMessage(L10n.text("绑定状态已更新")) }
                        .onFailure { onMessage(it.message ?: L10n.text("更新绑定状态失败")) }
                    busy = false
                }
            }
            PendingSecurityAction.AddPasskey -> {
                showAddPasskeyDialog = true
                pendingAction = null
            }
            PendingSecurityAction.ChangeEmail -> {
                showChangeEmailDialog = true
                pendingAction = null
            }
            PendingSecurityAction.ChangePassword -> {
                showChangePasswordDialog = true
                pendingAction = null
            }
            PendingSecurityAction.IssueAccountRecovery -> {
                scope.launch {
                    issueAccountRecoveryTicket()
                    pendingAction = null
                }
            }
            PendingSecurityAction.ScanRecoveryEndorse -> {
                onOpenQrScanner()
                pendingAction = null
            }
            PendingSecurityAction.DeleteAccount -> {
                showDeleteDialog = true
                pendingAction = null
            }
            is PendingSecurityAction.RenamePasskey -> {
                renameTarget = action.passkey
                renameDraft = action.passkey.name
                pendingAction = null
            }
            is PendingSecurityAction.DeletePasskey -> {
                scope.launch {
                    val deleted = runCatchingCancellable { container.securityRepository.deletePasskey(action.passkey.id) }
                        .onFailure { onMessage(it.message ?: L10n.text("删除失败")) }
                        .isSuccess
                    if (deleted) {
                        reloadAll()
                        onMessage(L10n.text("Passkey 已删除"))
                    }
                    pendingAction = null
                }
            }
            PendingSecurityAction.SetupTotp -> {
                scope.launch {
                    runCatchingCancellable { container.securityRepository.getTotpRegistrationOptions() }
                        .onSuccess { totpCode = ""; pendingTotpSetup = it }
                        .onFailure { onMessage(it.message ?: L10n.text("生成 TOTP 选项失败")) }
                    pendingAction = null
                }
            }
            PendingSecurityAction.DisableTotp -> {
                scope.launch {
                    val disabled = runCatchingCancellable { container.securityRepository.disableTotp() }
                        .onFailure { onMessage(it.message ?: L10n.text("禁用失败")) }
                        .isSuccess
                    if (disabled) {
                        reloadAll()
                        onMessage(L10n.text("TOTP 已禁用"))
                    }
                    pendingAction = null
                }
            }
            PendingSecurityAction.ViewRecoveryCodes -> {
                scope.launch {
                    recoveryCodes = runCatchingCancellable { container.securityRepository.getRecoveryCodes() }
                        .getOrElse {
                            onMessage(it.message ?: L10n.text("获取恢复码失败"))
                            emptyList()
                        }
                    pendingAction = null
                }
            }
            PendingSecurityAction.RegenerateRecoveryCodes -> {
                scope.launch {
                    val newCodes = runCatchingCancellable { container.securityRepository.regenerateRecoveryCodes() }
                        .onFailure { onMessage(it.message ?: L10n.text("重生恢复码失败")) }
                        .getOrNull()
                    if (newCodes != null) {
                        recoveryCodes = newCodes
                        reloadAll()
                        onMessage(L10n.text("恢复码已重新生成"))
                    }
                    pendingAction = null
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(AppPagePadding),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.S24),
    ) {
        SectionCard(modifier = Modifier.fillMaxWidth()) {
            AdaptiveSecurityStatusContent(
                status = adaptiveAuthStatus,
                loading = busy,
                detailsExpanded = riskDetailsExpanded,
                onToggleDetails = { riskDetailsExpanded = !riskDetailsExpanded },
                onVerify = { showSensitiveDialog = true },
                onRelogin = onLogoutCurrent,
                onRefresh = { scope.launch { reloadAll() } },
            )
        }

        if (loadErrors.isNotEmpty()) SectionCard(Modifier.fillMaxWidth()) {
            Text(L10n.text("部分安全信息加载失败"), color = MaterialTheme.colorScheme.error)
            loadErrors.forEach { (section, failure) ->
                Text("${L10n.text(section)}: ${failure.message ?: L10n.text("加载失败")}", style = MaterialTheme.typography.bodySmall)
            }
            LoadingOutlinedButton(L10n.text("重试"), onClick = { reloadAll() }, enabled = !busy)
        }

        SectionCard(modifier = Modifier.fillMaxWidth()) {
            SectionHeader(
                title = L10n.text("TOTP 验证器"),
                subtitle = if (totpStatus?.enabled == true) {
                    L10n.format("已启用，剩余恢复码 {0} 个", totpStatus?.recoveryCodesCount ?: 0)
                } else {
                    L10n.text("尚未启用，可作为常用多因素验证方式")
                },
            )
            SecurityActionCard(
                icon = Icons.Outlined.Security,
                title = if (totpStatus?.enabled == true) L10n.text("TOTP 验证器") else L10n.text("启用 TOTP"),
                subtitle = if (totpStatus?.enabled == true) {
                    L10n.text("使用验证器 App 与恢复码保护账号")
                } else {
                    L10n.text("生成密钥与二维码，在验证器 App 中绑定后启用")
                },
                statusIcon = if (totpStatus?.enabled == true) Icons.Outlined.CheckCircle else null,
                statusAccent = totpStatus?.enabled == true,
                onClick = {
                    if (totpStatus?.enabled != true) {
                        requireSensitiveVerification(PendingSecurityAction.SetupTotp)
                    }
                },
                actionLabel = if (totpStatus?.enabled == true) L10n.text("已启用") else L10n.text("启用"),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(AppSpacing.S8)) {
                if (totpStatus?.enabled == true) {
                    CompactSecurityButton(
                        text = L10n.text("禁用"),
                        onClick = { requireSensitiveVerification(PendingSecurityAction.DisableTotp) },
                        destructive = true,
                    )
                }
                CompactSecurityButton(
                    text = L10n.text("查看恢复码"),
                    onClick = { requireSensitiveVerification(PendingSecurityAction.ViewRecoveryCodes) },
                )
                CompactSecurityButton(
                    text = L10n.text("重新生成"),
                    onClick = { requireSensitiveVerification(PendingSecurityAction.RegenerateRecoveryCodes) },
                )
            }
            if (recoveryCodes.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text(L10n.text("恢复码"), style = MaterialTheme.typography.titleSmall)
                TextButton(onClick = { clipboardManager.setText(AnnotatedString(recoveryCodes.joinToString("\n"))); onMessage(L10n.text("恢复码已复制")) }) {
                    Text(L10n.text("复制恢复码"))
                }
                Text(
                    recoveryCodes.joinToString("  "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        SectionCard(modifier = Modifier.fillMaxWidth()) {
            SectionHeader(
                title = "Passkey",
                subtitle = passkeyAvailabilityMessage,
                actionText = if ((passkeyAvailability == PasskeyAvailability.Available || securityKeyAvailable) && activity != null) L10n.text("新增") else null,
                onAction = { requireSensitiveVerification(PendingSecurityAction.AddPasskey) },
            )
            if (passkeyAvailability != PasskeyAvailability.Available && !securityKeyAvailable) {
                Text(L10n.text("当前环境不满足原生 Passkey 要求，已阻止继续创建。"), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            Text(L10n.text("USB / NFC 安全密钥"), style = MaterialTheme.typography.titleSmall)
            Text(L10n.text(if (securityKeyAvailable) "支持硬件安全密钥，可在新增时选择" else "当前设备没有可用的硬件安全密钥提供方"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            passkeys.forEach { passkey ->
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                PasskeyCard(
                    passkey = passkey,
                    onDetails = { detailTarget = passkey },
                    onRename = { requireSensitiveVerification(PendingSecurityAction.RenamePasskey(passkey)) },
                    onDelete = { requireSensitiveVerification(PendingSecurityAction.DeletePasskey(passkey)) },
                )
            }
            if (passkeys.isEmpty() && passkeysLoaded) {
                Text(L10n.text("还没有添加 Passkey，可用于更快捷的无密码登录。"), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        SectionCard(modifier = Modifier.fillMaxWidth(), contentSpacing = 0.dp) {
            SectionHeader(
                title = L10n.text("账号信息"),
                subtitle = if (sensitiveStatus?.verified == true) {
                    L10n.format("当前已通过验证，剩余 {0} 秒", sensitiveStatus?.remainingSeconds ?: 0)
                } else {
                    L10n.text("在执行修改邮箱、修改密码、删除账号等操作前，会自动要求验证身份")
                },
            )
            SecurityActionCard(
                icon = Icons.Outlined.Badge,
                title = if (user?.email.isNullOrBlank()) L10n.text("绑定邮箱") else L10n.text("修改邮箱"),
                subtitle = user?.email.orEmpty().ifBlank { L10n.text("当前未绑定邮箱") },
                onClick = { requireSensitiveVerification(PendingSecurityAction.ChangeEmail) },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SecurityActionCard(
                icon = Icons.Outlined.Password,
                title = if (user?.hasPassword == false) L10n.text("设置密码") else L10n.text("修改密码"),
                subtitle = L10n.text("更新当前账号密码"),
                onClick = { requireSensitiveVerification(PendingSecurityAction.ChangePassword) },
            )
        }

        SectionCard(modifier = Modifier.fillMaxWidth(), contentSpacing = 0.dp) {
            SectionHeader(L10n.text("第三方账号"), L10n.text("管理 QQ 和 Apple 绑定"))
            SecurityActionCard(Icons.Outlined.Badge, "QQ", when (qqBound) { true -> L10n.text("已绑定"); false -> L10n.text("未绑定"); else -> L10n.text("正在读取状态") },
                onClick = {
                    if (qqBound == true) confirmUnbindProvider = "qq"
                    else if (qqBound == false && activity != null && container.qqLoginManager.isConfigured) requireSensitiveVerification(PendingSecurityAction.BindQq)
                    else onMessage(L10n.text("QQ 绑定暂不可用，请检查配置并刷新状态"))
                }, actionLabel = if (qqBound == true) L10n.text("解绑") else L10n.text("绑定"))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SecurityActionCard(Icons.Outlined.Badge, "Apple", when (appleBound) { true -> L10n.text("已绑定"); false -> L10n.text("未绑定"); else -> L10n.text("正在读取状态") },
                onClick = { if (appleBound == true) confirmUnbindProvider = "apple" }, actionLabel = if (appleBound == true) L10n.text("解绑") else L10n.text("在 iOS 上绑定"))
        }

        SectionCard(modifier = Modifier.fillMaxWidth(), contentSpacing = 0.dp) {
            SectionHeader(
                title = L10n.text("账号恢复"),
                subtitle = L10n.text("手机端既可以直接生成恢复背书，也可以扫码为忘记密码页完成背书"),
            )
            SecurityActionCard(
                icon = Icons.Outlined.Security,
                title = L10n.text("发起恢复背书"),
                subtitle = L10n.text("生成 5 分钟有效的一次性恢复二维码和恢复码"),
                onClick = { requireSensitiveVerification(PendingSecurityAction.IssueAccountRecovery) },
                actionLabel = L10n.text("生成"),
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SecurityActionCard(
                icon = Icons.Outlined.QrCodeScanner,
                title = L10n.text("扫码背书"),
                subtitle = L10n.text("扫描忘记密码页二维码，为当前恢复请求完成背书"),
                onClick = { requireSensitiveVerification(PendingSecurityAction.ScanRecoveryEndorse) },
                actionLabel = L10n.text("扫码"),
            )
        }

        SectionCard(modifier = Modifier.fillMaxWidth()) {
            val preferencesEnabled = !busy && pendingAction == null && !showSensitiveDialog && settings != null
            Text(L10n.text("安全偏好"), style = MaterialTheme.typography.titleMedium)
            SettingSwitchRow(L10n.text("启用 MFA"), settings?.mfaEnabled == true, enabled = preferencesEnabled) { checked ->
                requireSensitiveVerification(PendingSecurityAction.UpdateBooleanSetting("mfaEnabled", checked))
            }
            SettingSwitchRow(L10n.text("异地登录检测"), settings?.detectUnusualLogin == true, enabled = preferencesEnabled) { checked ->
                requireSensitiveVerification(PendingSecurityAction.UpdateBooleanSetting("detectUnusualLogin", checked))
            }
            SettingSwitchRow(L10n.text("敏感操作邮件提醒"), settings?.notifySensitiveActionEmail == true, enabled = preferencesEnabled) { checked ->
                requireSensitiveVerification(PendingSecurityAction.UpdateBooleanSetting("notifySensitiveActionEmail", checked))
            }
            SettingSwitchRow(L10n.text("订阅动态邮件"), settings?.subscribeNewsEmail == true, enabled = preferencesEnabled) { checked ->
                requireSensitiveVerification(PendingSecurityAction.UpdateBooleanSetting("subscribeNewsEmail", checked))
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            PreferenceDropdown(
                title = L10n.text("首选 MFA"),
                options = buildList {
                    if (totpStatus?.enabled == true) add("totp")
                    if ((passkeyAvailability == PasskeyAvailability.Available || securityKeyAvailable) && passkeys.isNotEmpty()) add("passkey")
                },
                selected = settings?.preferredMfaMethod.orEmpty(),
                enabled = preferencesEnabled,
            ) { choice -> requireSensitiveVerification(PendingSecurityAction.UpdateStringSetting("preferredMfaMethod", choice)) }
            PreferenceDropdown(
                title = L10n.text("首选敏感验证"),
                options = buildList {
                    if (user?.hasPassword != false) add("password")
                    if (!user?.email.isNullOrBlank()) add("email-code")
                    if ((passkeyAvailability == PasskeyAvailability.Available || securityKeyAvailable) && passkeys.isNotEmpty()) add("passkey")
                    if (totpStatus?.enabled == true) add("totp")
                },
                selected = settings?.preferredSensitiveMethod.orEmpty(),
                enabled = preferencesEnabled,
            ) { choice -> requireSensitiveVerification(PendingSecurityAction.UpdateStringSetting("preferredSensitiveMethod", choice)) }
        }

        SectionCard(modifier = Modifier.fillMaxWidth()) {
            Text(L10n.text("危险操作"), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
            SecurityActionCard(
                icon = Icons.Outlined.Shield,
                title = L10n.text("删除账号"),
                subtitle = L10n.text("此操作不可撤销，请谨慎处理"),
                destructive = true,
                onClick = { requireSensitiveVerification(PendingSecurityAction.DeleteAccount) },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            LoadingOutlinedButton(text = L10n.text("退出全部设备"), onClick = { onLogoutAll() }, modifier = Modifier.fillMaxWidth())
        }
    }

    if (pendingTotpSetup != null) {
        AlertDialog(
            onDismissRequest = { pendingTotpSetup = null; totpCode = "" },
            title = { Text(L10n.text("TOTP 注册")) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(AppSpacing.S8)) {
                    Text("secret: ${pendingTotpSetup?.secret}")
                    FlowRow {
                        TextButton(onClick = { clipboardManager.setText(AnnotatedString(pendingTotpSetup?.secret.orEmpty())); onMessage(L10n.text("密钥已复制")) }) { Text(L10n.text("复制密钥")) }
                        TextButton(onClick = {
                            val uri = pendingTotpSetup?.qrCodeUrl.orEmpty()
                            if (Uri.parse(uri).scheme == "otpauth") runCatchingCancellable { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri))) }
                                .onFailure { onMessage(L10n.text("请先安装验证器应用")) }
                        }) { Text(L10n.text("打开验证器")) }
                    }
                    pendingTotpSetup?.qrCodeUrl?.takeIf { Uri.parse(it).scheme == "otpauth" }?.let { qrCode ->
                        LocalQrCode(text = qrCode, contentDescription = "TOTP QR", modifier = Modifier.size(180.dp))
                    }
                    Text(L10n.format("恢复码: {0}", pendingTotpSetup?.recoveryCodes?.joinToString(" ")))
                    TextButton(onClick = { clipboardManager.setText(AnnotatedString(pendingTotpSetup?.recoveryCodes.orEmpty().joinToString("\n"))); onMessage(L10n.text("恢复码已复制")) }) { Text(L10n.text("复制恢复码")) }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { recoveryCodesSaved = !recoveryCodesSaved }) {
                        Checkbox(checked = recoveryCodesSaved, onCheckedChange = { recoveryCodesSaved = it })
                        Text(L10n.text("我已保存恢复码"))
                    }
                    AppOutlinedField(
                        value = totpCode,
                        onValueChange = { totpCode = it },
                        label = { Text(L10n.text("输入 6 位验证码")) },
                    )
                }
            },
            confirmButton = {
                LoadingTextButton(text = L10n.text("确认启用"), enabled = recoveryCodesSaved && Regex("[0-9]{6}").matches(totpCode.trim()), onClick = {
                    val setup = pendingTotpSetup ?: return@LoadingTextButton
                    val created = runCatchingCancellable {
                        container.securityRepository.verifyTotpRegistration(totpCode.trim(), setup.recoveryCodes)
                    }.onFailure { onMessage(it.message ?: L10n.text("TOTP 启用失败")) }
                        .isSuccess
                    if (created) {
                        pendingTotpSetup = null
                        totpCode = ""
                        reloadAll()
                        onMessage(L10n.text("TOTP 已启用"))
                    }
                })
            },
            dismissButton = { TextButton(onClick = { pendingTotpSetup = null; totpCode = "" }) { Text(L10n.text("取消")) } },
        )
    }

    if (showAddPasskeyDialog) {
        AlertDialog(
            onDismissRequest = { showAddPasskeyDialog = false },
            title = { Text(L10n.text("新增 Passkey")) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(AppSpacing.S8)) {
                    Text(L10n.text("Passkey 需要 Android 原生凭据提供器与匹配的 RP 域配置。"))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = newPasskeyUseHardware, enabled = securityKeyAvailable, onCheckedChange = { newPasskeyUseHardware = it })
                        Text(L10n.text("使用 USB / NFC 安全密钥"))
                    }
                    AppOutlinedField(
                        value = newPasskeyName,
                        onValueChange = { newPasskeyName = it },
                        label = { Text(L10n.text("Passkey 名称")) },
                    )
                }
            },
            confirmButton = {
                LoadingTextButton(text = L10n.text("创建"), onClick = {
                    if (activity == null) {
                        onMessage(L10n.text("当前上下文不支持 Passkey"))
                        return@LoadingTextButton
                    }
                    val created = runCatchingCancellable {
                        val options = container.securityRepository.getPasskeyRegistrationOptions(newPasskeyName.trim(), if (newPasskeyUseHardware) "cross-platform" else "auto")
                        val payload = if (newPasskeyUseHardware) container.securityKeyManager.createForRegistration(activity, options)
                            else container.passkeyManager.createForRegistration(activity, options)
                        container.securityRepository.verifyPasskeyRegistration(newPasskeyName.trim(), payload)
                    }.onFailure { onMessage(it.message ?: L10n.text("Passkey 创建失败")) }
                        .isSuccess
                    if (created) {
                        showAddPasskeyDialog = false
                        newPasskeyName = "Ksuser Android"
                        reloadAll()
                        onMessage(L10n.text("Passkey 已添加"))
                    }
                })
            },
            dismissButton = { TextButton(onClick = { showAddPasskeyDialog = false }) { Text(L10n.text("取消")) } },
        )
    }

    renameTarget?.let { passkey ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text(L10n.text("重命名 Passkey")) },
            text = {
                AppOutlinedField(
                    value = renameDraft,
                    onValueChange = { renameDraft = it },
                    label = { Text(L10n.text("新名称")) },
                )
            },
            confirmButton = {
                LoadingTextButton(text = L10n.text("保存"), onClick = {
                    val renamed = runCatchingCancellable {
                        container.securityRepository.renamePasskey(passkey.id, renameDraft.trim())
                    }.onFailure { onMessage(it.message ?: L10n.text("重命名失败")) }
                        .isSuccess
                    if (renamed) {
                        renameTarget = null
                        reloadAll()
                        onMessage(L10n.text("Passkey 已重命名"))
                    }
                })
            },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text(L10n.text("取消")) } },
        )
    }

    detailTarget?.let { passkey ->
        AlertDialog(
            onDismissRequest = { detailTarget = null },
            title = { Text(L10n.text("Passkey 详情")) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(AppSpacing.S8)) {
                    Text(L10n.format("名称：{0}", passkey.name), style = MaterialTheme.typography.bodyMedium)
                    Text(L10n.format("创建于：{0}", formatSecurityTime(passkey.createdAt)), style = MaterialTheme.typography.bodySmall)
                    Text(L10n.format("最后使用：{0}", formatSecurityTime(passkey.lastUsedAt)), style = MaterialTheme.typography.bodySmall)
                    Text(L10n.format("连接方式：{0}", passkey.transports.ifBlank { L10n.text("未知") }), style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = { detailTarget = null }) { Text(L10n.text("关闭")) }
            },
        )
    }

    if (showSensitiveDialog) {
        SensitiveVerificationDialog(
            container = container,
            onDismiss = { showSensitiveDialog = false; pendingAction = null },
            onVerified = {
                scope.launch {
                    runCatchingCancellable { container.securityRepository.getSensitiveVerificationStatus() }
                        .onSuccess { verified ->
                            sensitiveStatus = verified
                            showSensitiveDialog = false
                            if (!verified.verified || verified.remainingSeconds <= 0) pendingAction = null
                            else onMessage(L10n.text("敏感验证已通过"))
                        }.onFailure { pendingAction = null; onMessage(it.message ?: L10n.text("读取敏感验证状态失败")) }
                }
            },
            onMessage = onMessage,
        )
    }

    if (showChangeEmailDialog) {
        ChangeEmailDialog(
            container = container,
            onDismiss = { showChangeEmailDialog = false },
            onSuccess = {
                showChangeEmailDialog = false
                onUserRefresh()
                scope.launch { reloadAll() }
                onMessage(L10n.text("邮箱已更新"))
            },
            onMessage = onMessage,
        )
    }

    if (showChangePasswordDialog) {
        ChangePasswordDialog(
            container = container,
            settingInitialPassword = user?.hasPassword == false,
            onDismiss = { showChangePasswordDialog = false },
            onSuccess = {
                showChangePasswordDialog = false
                scope.launch { reloadAll() }
                onUserRefresh()
                onMessage(L10n.text("密码已修改"))
            },
            onMessage = onMessage,
        )
    }

    if (showDeleteDialog) {
        DeleteAccountDialog(
            container = container,
            onDismiss = { showDeleteDialog = false },
            onDeleted = {
                showDeleteDialog = false
                onAccountDeleted()
            },
            onMessage = onMessage,
        )
    }

    confirmUnbindProvider?.let { provider ->
        AlertDialog(onDismissRequest = { confirmUnbindProvider = null }, title = { Text(L10n.text("确认解绑")) },
            text = { Text(L10n.format("{0}：解绑后将无法使用该账号登录。请先确认已配置其他登录方式。", if (provider == "qq") "QQ" else "Apple")) },
            confirmButton = { TextButton(onClick = { confirmUnbindProvider = null; requireSensitiveVerification(if (provider == "qq") PendingSecurityAction.UnbindQq else PendingSecurityAction.UnbindApple) }) { Text(L10n.text("解绑")) } },
            dismissButton = { TextButton(onClick = { confirmUnbindProvider = null }) { Text(L10n.text("取消")) } })
    }

    if (showAccountRecoveryDialog) {
        AccountRecoveryDialog(
            ticket = accountRecoveryTicket,
            remainingSeconds = accountRecoveryRemainingSeconds,
            busy = accountRecoveryBusy,
            onDismiss = {
                showAccountRecoveryDialog = false
                accountRecoveryTicket = null
                accountRecoveryRemainingSeconds = 0L
            },
            onRefresh = { scope.launch { issueAccountRecoveryTicket(showCreatedMessage = false) } },
            onCopyRecoveryCode = {
                val recoveryCode = accountRecoveryTicket?.recoveryCode?.trim().orEmpty()
                if (recoveryCode.isBlank()) {
                    onMessage(L10n.text("恢复码尚未生成"))
                } else {
                    clipboardManager.setText(AnnotatedString(recoveryCode))
                    onMessage(L10n.text("恢复码已复制"))
                }
            },
            onOpenRecoveryPage = {
                val recoveryCode = accountRecoveryTicket?.recoveryCode?.trim().orEmpty()
                if (recoveryCode.isBlank()) {
                    onMessage(L10n.text("恢复码尚未生成"))
                } else {
                    val recoveryUri = buildAccountRecoveryUri(
                        passkeyOrigin = EnvironmentProvider.current.passkeyOriginHint,
                        apiBaseUrl = EnvironmentProvider.current.apiBaseUrl,
                        recoveryCode = recoveryCode,
                    )
                    context.startActivity(Intent(Intent.ACTION_VIEW, recoveryUri))
                }
            },
        )
    }
}

/** Presentation only: security decisions and sensitive action dispatch remain in [SecurityScreen]. */
@Composable
internal fun AdaptiveSecurityStatusContent(
    status: AdaptiveAuthStatus?,
    loading: Boolean,
    detailsExpanded: Boolean,
    onToggleDetails: () -> Unit,
    onVerify: () -> Unit,
    onRelogin: () -> Unit,
    onRefresh: () -> Unit,
) {
    SectionHeader(
        title = L10n.text("会话安全"),
        subtitle = L10n.text("自适应连续认证"),
        actionText = L10n.text("刷新"),
        onAction = onRefresh,
    )
    if (loading && status != null) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    }
    if (status == null) {
        if (loading) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppSpacing.S12)) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                Text(L10n.text("正在读取状态"), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            Text(L10n.text("暂时无法读取会话风险状态"), color = MaterialTheme.colorScheme.error)
        }
        return
    }

    val tone = adaptiveRiskColor(status.riskLevel)
    val decision = status.policyDecision.uppercase()
    val title = when (decision) {
        "FREEZE" -> L10n.text("高风险会话已冻结")
        "STEP_UP" -> L10n.text("建议立即补做验证")
        else -> L10n.text("当前会话可信度稳定")
    }
    val recommendedAction = if (status.recommendedActionCode.isNullOrBlank()) "" else status.recommendedAction
    val subtitle = when (decision) {
        "FREEZE" -> L10n.text("检测到高风险信号，当前会话已冻结，请重新登录并完成验证")
        "STEP_UP" -> recommendedAction.ifBlank { L10n.text("当前会话风险中等，需完成 step-up 验证后继续敏感操作") }
        else -> recommendedAction.ifBlank { L10n.text("当前会话风险较低，可继续使用") }
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = AppSpacing.S8),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.S12),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier.size(44.dp).background(tone.copy(alpha = 0.12f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (decision == "ALLOW") Icons.Outlined.CheckCircle else Icons.Outlined.Shield,
                contentDescription = null,
                tint = tone,
                modifier = Modifier.size(24.dp),
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AppSpacing.S8)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                L10n.format("{0}风险", adaptiveRiskLabel(status.riskLevel)) + " · " + adaptivePolicyLabel(decision),
                style = MaterialTheme.typography.labelLarge,
                color = tone,
            )
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (decision == "FREEZE" && recommendedAction.isNotBlank() && recommendedAction != subtitle) {
                Text(recommendedAction, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    if (status.multiEndpointAlert) {
        val alertTone = adaptiveAlertColor(status.alertLevel)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = alertTone.copy(alpha = 0.10f),
        ) {
            Column(modifier = Modifier.padding(AppSpacing.S16), verticalArrangement = Arrangement.spacedBy(AppSpacing.S8)) {
                Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.S8), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Info, contentDescription = null, tint = alertTone, modifier = Modifier.size(20.dp))
                    Text(status.alertTitle?.ifBlank { L10n.text("风险告警") } ?: L10n.text("风险告警"), style = MaterialTheme.typography.titleSmall)
                }
                Text(
                    status.alertMessage?.ifBlank { L10n.text("检测到风险信号，请尽快处理") } ?: L10n.text("检测到风险信号，请尽快处理"),
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (status.alertRemainingSeconds > 0) {
                    Text(
                        L10n.format("告警剩余有效期 {0} 秒", status.alertRemainingSeconds),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (!status.sensitiveVerified && !status.sessionFrozen) {
        Button(onClick = onVerify, colors = cn.ksuser.auth.ui.components.appPrimaryButtonColors(), modifier = Modifier.heightIn(min = 48.dp)) {
            Text(L10n.text("立即验证"))
        }
    }
    if (status.sessionFrozen) {
        Button(onClick = onRelogin, colors = cn.ksuser.auth.ui.components.appPrimaryButtonColors(), modifier = Modifier.heightIn(min = 48.dp)) {
            Text(L10n.text("重新登录"))
        }
        Text(
            L10n.text("当前会话已被冻结，请重新登录后继续操作"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }

    TextButton(onClick = onToggleDetails, modifier = Modifier.heightIn(min = 48.dp)) {
        Text(L10n.text(if (detailsExpanded) "收起风险详情" else "查看风险详情"))
        Icon(
            imageVector = if (detailsExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
            contentDescription = null,
            modifier = Modifier.padding(start = AppSpacing.S8).size(20.dp),
        )
    }
    if (detailsExpanded) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        RiskDetailRow(L10n.text("风险分"), status.riskScore.toString())
        RiskDetailRow(L10n.text("策略版本"), status.policyVersion.ifBlank { "2.0.0" })
        RiskDetailRow(
            L10n.text("敏感验证"),
            if (status.sensitiveVerified) L10n.format("有效 {0} 秒", status.sensitiveVerificationRemainingSeconds) else L10n.text("未验证"),
        )
        RiskDetailRow(L10n.text("会话年龄"), formatDurationSeconds(status.authAgeSeconds))
        RiskDetailRow(L10n.text("空闲时长"), formatDurationSeconds(status.idleSeconds))
        RiskDetailRow(
            L10n.text("当前环境"),
            listOfNotNull(status.currentLocation, status.deviceType).joinToString(" / ").ifBlank { L10n.text("未知环境") },
        )
        if (status.reasons.isNotEmpty()) {
            Text(L10n.text("风险信号"), style = MaterialTheme.typography.titleSmall)
            status.reasons.forEach { reason ->
                Text("• $reason", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun RiskDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.S16),
    ) {
        Text(label, modifier = Modifier.weight(0.4f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, modifier = Modifier.weight(0.6f), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun SettingSwitchRow(
    label: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.S16),
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun PreferenceDropdown(
    title: String,
    options: List<String>,
    selected: String,
    enabled: Boolean = true,
    onSelected: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.S8)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { if (enabled && options.isNotEmpty()) expanded = !expanded },
        ) {
            OutlinedTextField(
                value = if (selected in options) selected.toDisplayLabel() else L10n.text("未选择可用方式"),
                onValueChange = {},
                readOnly = true,
                enabled = enabled && options.isNotEmpty(),
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth(),
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            )
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
            options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.toDisplayLabel()) },
                        onClick = {
                            expanded = false
                            onSelected(option)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    subtitle: String,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (actionText != null && onAction != null) {
            CompactSecurityButton(text = actionText, onClick = onAction)
        }
    }
}

@Composable
private fun SecurityActionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    statusIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    statusAccent: Boolean = false,
    destructive: Boolean = false,
    onClick: () -> Unit,
    actionLabel: String = L10n.text("前往"),
) {
    val iconTint = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clickable(onClick = onClick)
            .padding(vertical = AppSpacing.S8),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.S12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(40.dp).background(
                if (destructive) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                CircleShape,
            ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(22.dp))
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.S8), verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f, fill = false))
                if (statusIcon != null) {
                    Icon(
                        imageVector = statusIcon,
                        contentDescription = null,
                        tint = if (statusAccent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                actionLabel,
                style = MaterialTheme.typography.labelMedium,
                color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            )
            Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun PasskeyCard(
    passkey: PasskeyListItem,
    onDetails: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(AppSpacing.S8)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.S12),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.75f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.VpnKey, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                }
                Column(verticalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.weight(1f)) {
                    Text(passkey.name, style = MaterialTheme.typography.titleSmall)
                    Text(
                        L10n.format("最近使用 {0}", formatSecurityTime(passkey.lastUsedAt)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDetails, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Outlined.Info, contentDescription = L10n.text("详情"), modifier = Modifier.size(22.dp))
                }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(AppSpacing.S8)) {
            CompactSecurityButton(text = L10n.text("重命名"), onClick = onRename)
            CompactSecurityButton(text = L10n.text("删除"), onClick = onDelete, destructive = true)
        }
    }
}

@Composable
private fun CompactSecurityButton(
    text: String,
    onClick: () -> Unit,
    destructive: Boolean = false,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.heightIn(min = 48.dp),
        shape = RoundedCornerShape(12.dp),
    ) {
        if (destructive) {
            Icon(Icons.Outlined.DeleteOutline, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.error)
        }
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        )
    }
}

private fun buildAccountRecoveryUri(
    passkeyOrigin: String,
    apiBaseUrl: String,
    recoveryCode: String,
): Uri {
    val normalizedOrigin = passkeyOrigin.trim().ifBlank { "https://auth.ksuser.cn" }
    val baseUri = Uri.parse(if (normalizedOrigin.endsWith("/")) normalizedOrigin else "$normalizedOrigin/")
    return baseUri.buildUpon()
        .path("/forgot-password")
        .appendQueryParameter("recoveryCode", recoveryCode.trim())
        .appendQueryParameter("apiBaseUrl", apiBaseUrl.trim())
        .build()
}

@Composable
private fun AccountRecoveryDialog(
    ticket: AccountRecoveryTicket?,
    remainingSeconds: Long,
    busy: Boolean,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit,
    onCopyRecoveryCode: () -> Unit,
    onOpenRecoveryPage: () -> Unit,
) {
    val recoveryCode = ticket?.recoveryCode?.trim().orEmpty()
    val username = ticket?.username?.takeUnless { it.isBlank() } ?: L10n.text("当前账号")
    val maskedEmail = ticket?.maskedEmail?.takeUnless { it.isBlank() } ?: L10n.text("未绑定邮箱")
    val sponsorClientName = ticket?.sponsorClientName?.takeUnless { it.isBlank() } ?: L10n.text("当前设备")
    val sponsorIpLocation = ticket?.sponsorIpLocation?.takeUnless { it.isBlank() } ?: L10n.text("未知位置")

    val recoveryUrl = remember(recoveryCode) {
        recoveryCode.takeIf { it.isNotBlank() }?.let {
            buildAccountRecoveryUri(
                passkeyOrigin = EnvironmentProvider.current.passkeyOriginHint,
                apiBaseUrl = EnvironmentProvider.current.apiBaseUrl,
                recoveryCode = it,
            ).toString()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(L10n.text("账号恢复授权")) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(AppSpacing.S12)) {
                Text(
                    L10n.text("为另一台设备生成一次性恢复二维码与恢复码。恢复成功后，旧会话会被自动撤销。"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (busy && ticket == null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 20.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                } else if (ticket != null) {
                    recoveryUrl?.takeIf { remainingSeconds > 0 }?.let { qrText ->
                        LocalQrCode(
                            text = qrText,
                            contentDescription = L10n.text("账号恢复二维码"),
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .size(220.dp),
                        )
                    }
                    Text(
                        if (remainingSeconds <= 0) L10n.text("恢复授权已过期，请刷新") else L10n.format("恢复码：{0}", recoveryCode.ifBlank { L10n.text("生成中...") }),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        L10n.format("剩余有效期：{0} 秒", remainingSeconds.coerceAtLeast(0)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "$username · $maskedEmail",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        L10n.format("背书设备：{0}", sponsorClientName),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        L10n.format("位置：{0}", sponsorIpLocation),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(AppSpacing.S8)) {
                        CompactSecurityButton(text = L10n.text("复制恢复码"), onClick = onCopyRecoveryCode, enabled = remainingSeconds > 0)
                        CompactSecurityButton(text = L10n.text("打开恢复页"), onClick = onOpenRecoveryPage, enabled = remainingSeconds > 0)
                    }
                }
            }
        },
        confirmButton = {
            LoadingTextButton(
                text = L10n.text("刷新授权"),
                onClick = onRefresh,
                enabled = !busy,
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(L10n.text("关闭")) }
        },
    )
}

private sealed interface PendingSecurityAction {
    data class UpdateBooleanSetting(val field: String, val value: Boolean) : PendingSecurityAction
    data class UpdateStringSetting(val field: String, val value: String) : PendingSecurityAction
    data object BindQq : PendingSecurityAction
    data object UnbindQq : PendingSecurityAction
    data object UnbindApple : PendingSecurityAction
    data object AddPasskey : PendingSecurityAction
    data object ChangeEmail : PendingSecurityAction
    data object ChangePassword : PendingSecurityAction
    data object IssueAccountRecovery : PendingSecurityAction
    data object ScanRecoveryEndorse : PendingSecurityAction
    data object DeleteAccount : PendingSecurityAction
    data object SetupTotp : PendingSecurityAction
    data object DisableTotp : PendingSecurityAction
    data object ViewRecoveryCodes : PendingSecurityAction
    data object RegenerateRecoveryCodes : PendingSecurityAction
    data class RenamePasskey(val passkey: PasskeyListItem) : PendingSecurityAction
    data class DeletePasskey(val passkey: PasskeyListItem) : PendingSecurityAction
}

private fun String.toDisplayLabel(): String {
    return when (this) {
        "totp" -> "TOTP"
        "passkey" -> "Passkey"
        "security-key" -> L10n.text("USB / NFC 安全密钥")
        "password" -> L10n.text("密码")
        "email-code" -> L10n.text("邮箱验证码")
        else -> this
    }
}

private fun adaptiveRiskLabel(level: String): String {
    return when (level.lowercase()) {
        "high" -> L10n.text("高")
        "medium" -> L10n.text("中")
        else -> L10n.text("低")
    }
}

@Composable
private fun adaptiveRiskColor(level: String) = when (level.lowercase()) {
    "high" -> MaterialTheme.colorScheme.error
    "medium" -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.primary
}

private fun adaptivePolicyLabel(decision: String): String {
    return when (decision.uppercase()) {
        "FREEZE" -> L10n.text("冻结会话")
        "STEP_UP" -> L10n.text("强制补验")
        else -> L10n.text("低风险放行")
    }
}

@Composable
private fun adaptiveAlertColor(level: String?) = when ((level ?: "").lowercase()) {
    "high" -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.tertiary
}

private fun formatDurationSeconds(seconds: Long): String {
    if (seconds < 60) return L10n.format("{0} 秒", seconds)
    if (seconds < 3600) return L10n.format("{0} 分钟", seconds / 60)
    if (seconds < 86400) return L10n.format("{0} 小时", seconds / 3600)
    return L10n.format("{0} 天", seconds / 86400)
}

private fun formatSecurityTime(value: String?): String = DisplayTime.format(value)

@Composable
internal fun SensitiveVerificationDialog(
    container: AppContainer,
    onDismiss: () -> Unit,
    onVerified: () -> Unit,
    onMessage: (String) -> Unit,
    qrApproveCode: String? = null,
    onQrVerified: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val activity = context as? Activity
    var status by remember { mutableStateOf<SensitiveVerificationStatus?>(null) }
    var selectedMethod by rememberSaveable { mutableStateOf("password") }
    var password by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var recoveryMode by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        runCatchingCancellable { container.securityRepository.getSensitiveVerificationStatus() }
            .onSuccess {
                val methods = it.methods.filter { method -> method in setOf("password", "email-code", "totp") || (method == "passkey" && activity != null && container.passkeyManager.availability() == PasskeyAvailability.Available) } +
                    if ("passkey" in it.methods && activity != null && container.securityKeyManager.available(activity)) listOf("security-key") else emptyList()
                status = it.copy(methods = methods)
                selectedMethod = it.preferredMethod?.takeIf { method -> method in methods } ?: methods.firstOrNull().orEmpty()
            }
            .onFailure { onMessage(it.message ?: L10n.text("读取敏感验证状态失败")) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(L10n.text(if (qrApproveCode == null) "敏感操作验证" else "验证并批准扫码请求")) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(AppSpacing.S8)) {
                Text(L10n.format("可用方式: {0}", status?.methods?.joinToString().orEmpty()))
                if (status != null && status?.methods.isNullOrEmpty()) Text(L10n.text("当前设备没有可用的验证方式，请在其他设备配置密码、邮箱或 TOTP"), color = MaterialTheme.colorScheme.error)
                PreferenceDropdown(
                    title = L10n.text("验证方式"),
                    options = status?.methods.orEmpty(),
                    selected = selectedMethod,
                    onSelected = { selectedMethod = it },
                )
                when (selectedMethod) {
                    "password" -> {
                        AppOutlinedField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text(L10n.text("当前密码")) },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        )
                    }

                    "email-code" -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.S8), verticalAlignment = Alignment.CenterVertically) {
                            AppOutlinedField(
                                value = code,
                                onValueChange = { code = it },
                                label = { Text(L10n.text("邮箱验证码")) },
                                modifier = Modifier.weight(1f),
                            )
                            LoadingOutlinedButton(text = L10n.text("发送"), onClick = {
                                runCatchingCancellable { container.securityRepository.sendSensitiveCode() }
                                    .onSuccess { onMessage(L10n.text("验证码已发送")) }
                                    .onFailure { onMessage(it.message ?: L10n.text("发送失败")) }
                            })
                        }
                    }

                    "totp" -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.S8)) {
                            FilterChip(selected = !recoveryMode, onClick = { recoveryMode = false }, label = { Text("TOTP") })
                            FilterChip(selected = recoveryMode, onClick = { recoveryMode = true }, label = { Text(L10n.text("恢复码")) })
                        }
                        AppOutlinedField(
                            value = code,
                            onValueChange = { code = it },
                            label = { Text(if (recoveryMode) L10n.text("恢复码") else L10n.text("6 位验证码")) },
                        )
                    }

                    "passkey", "security-key" -> {
                        Text(L10n.text(if (selectedMethod == "security-key") "连接 USB / NFC 安全密钥以完成验证" else "Passkey 将直接唤起 Android 凭据提供器完成验证。"))
                        if (activity == null) {
                            Text(L10n.text("当前上下文不支持 Passkey"), color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        },
        confirmButton = {
            LoadingTextButton(text = L10n.text("验证"), enabled = selectedMethod in status?.methods.orEmpty(), onClick = {
                runCatchingCancellable {
                    require(selectedMethod in status?.methods.orEmpty()) { L10n.text("当前设备没有可用的验证方式，请在其他设备配置密码、邮箱或 TOTP") }
                    if (qrApproveCode != null) {
                        when (selectedMethod) {
                            "password" -> container.securityRepository.verifyQrApproval(qrApproveCode, "password", password = password)
                            "email-code" -> container.securityRepository.verifyQrApproval(qrApproveCode, "email-code", code = code.trim())
                            "totp" -> container.securityRepository.verifyQrApproval(qrApproveCode, "totp",
                                code = if (recoveryMode) null else code.trim(),
                                recoveryCode = if (recoveryMode) code.trim().uppercase() else null)
                            "passkey", "security-key" -> {
                                if (activity == null) error(L10n.text("当前上下文不支持 Passkey"))
                                val options = container.securityRepository.qrApprovalPasskeyOptions(qrApproveCode)
                                val payload = if (selectedMethod == "security-key") container.securityKeyManager.getForAuthentication(activity, options)
                                    else container.passkeyManager.getForAuthentication(activity, options)
                                container.securityRepository.verifyQrApprovalPasskey(qrApproveCode, options.challengeId, payload)
                            }
                            else -> error(L10n.text("不支持的验证方式"))
                        }
                    } else {
                    when (selectedMethod) {
                        "password" -> container.securityRepository.verifySensitivePassword(password)
                        "email-code" -> container.securityRepository.verifySensitiveEmailCode(code.trim())
                        "totp" -> {
                            if (recoveryMode) {
                                container.securityRepository.verifySensitiveTotp(recoveryCode = code.trim().uppercase())
                            } else {
                                container.securityRepository.verifySensitiveTotp(code = code.trim())
                            }
                        }

                        "passkey", "security-key" -> {
                            if (activity == null) error(L10n.text("当前上下文不支持 Passkey"))
                            val options = container.securityRepository.getPasskeySensitiveVerificationOptions()
                            val payload = if (selectedMethod == "security-key") container.securityKeyManager.getForAuthentication(activity, options)
                                else container.passkeyManager.getForAuthentication(activity, options)
                            container.securityRepository.verifySensitivePasskey(options.challengeId, payload)
                        }
                        else -> error(L10n.text("不支持的验证方式"))
                    }
                    null
                    }
                }.onSuccess { proofId ->
                    if (qrApproveCode == null) onVerified() else onQrVerified(requireNotNull(proofId))
                }
                    .onFailure { onMessage(it.message ?: L10n.text("验证失败")) }
            })
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(L10n.text("取消")) } },
    )
}

@Composable
private fun ChangeEmailDialog(
    container: AppContainer,
    onDismiss: () -> Unit,
    onSuccess: () -> Unit,
    onMessage: (String) -> Unit,
) {
    var email by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(L10n.text("修改邮箱")) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(AppSpacing.S8)) {
                AppOutlinedField(value = email, onValueChange = { email = it }, label = { Text(L10n.text("新邮箱")) })
                Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.S8), verticalAlignment = Alignment.CenterVertically) {
                    AppOutlinedField(
                        value = code,
                        onValueChange = { code = it },
                        label = { Text(L10n.text("验证码")) },
                        modifier = Modifier.weight(1f),
                    )
                    LoadingOutlinedButton(text = L10n.text("发送"), onClick = {
                        runCatchingCancellable { container.securityRepository.sendChangeEmailCode(email.trim()) }
                            .onSuccess { onMessage(L10n.text("邮箱验证码已发送")) }
                            .onFailure { onMessage(it.message ?: L10n.text("发送失败")) }
                    })
                }
            }
        },
        confirmButton = {
            LoadingTextButton(text = L10n.text("提交"), onClick = {
                runCatchingCancellable { container.securityRepository.changeEmail(email.trim(), code.trim()) }
                    .onSuccess { onSuccess() }
                    .onFailure { onMessage(it.message ?: L10n.text("修改邮箱失败")) }
            })
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(L10n.text("取消")) } },
    )
}

@Composable
private fun ChangePasswordDialog(
    container: AppContainer,
    settingInitialPassword: Boolean,
    onDismiss: () -> Unit,
    onSuccess: () -> Unit,
    onMessage: (String) -> Unit,
) {
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var requirement by remember { mutableStateOf<PasswordRequirement?>(null) }
    var policyError by remember { mutableStateOf<String?>(null) }
    suspend fun loadPolicy() {
        policyError = null
        runCatchingCancellable { container.authRepository.getPasswordRequirement() }
            .onSuccess { requirement = it }.onFailure { policyError = it.message ?: L10n.text("重试获取密码要求") }
    }
    LaunchedEffect(container) { loadPolicy() }
    val policy = requirement
    val valid = policy != null && password.length in policy.minLength..policy.maxLength &&
        (!policy.requireUppercase || password.any(Char::isUpperCase)) &&
        (!policy.requireLowercase || password.any(Char::isLowerCase)) &&
        (!policy.requireDigits || password.any(Char::isDigit)) &&
        (!policy.requireSpecialChars || password.any { it in "@\$!%*?&" })
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(L10n.text(if (settingInitialPassword) "设置密码" else "修改密码")) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                policy?.let { Text(it.requirementMessage) }
                policyError?.let { Text(it, color = MaterialTheme.colorScheme.error); LoadingOutlinedButton(L10n.text("重试获取密码要求"), onClick = { loadPolicy() }) }
                AppOutlinedField(password, { password = it }, label = { Text(L10n.text("新密码")) },
                    visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                AppOutlinedField(confirmation, { confirmation = it }, label = { Text(L10n.text("确认密码")) },
                    visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                if (confirmation.isNotEmpty() && password != confirmation) Text(L10n.text("两次输入的密码不一致"), color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = { LoadingTextButton(L10n.text("提交"), enabled = valid && password == confirmation, onClick = {
            runCatchingCancellable { container.securityRepository.changePassword(password) }.onSuccess { onSuccess() }
                .onFailure { onMessage(it.message ?: L10n.text("修改密码失败")) }
        }) },
        dismissButton = { TextButton(onClick = onDismiss) { Text(L10n.text("取消")) } })
}

@Composable
private fun DeleteAccountDialog(
    container: AppContainer,
    onDismiss: () -> Unit,
    onDeleted: () -> Unit,
    onMessage: (String) -> Unit,
) {
    val context = LocalContext.current
    var requiredShakes by rememberSaveable { mutableStateOf((6..9).random()) }
    var shakeCount by rememberSaveable { mutableStateOf(0) }
    var currentSpeed by remember { mutableStateOf(0f) }
    var hasMotionSensor by remember { mutableStateOf(true) }
    var sensorLabel by remember { mutableStateOf(L10n.text("运动传感器")) }
    var lastShakeAt by remember { mutableStateOf(0L) }
    val touchExploration = (context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager)?.isTouchExplorationEnabled == true
    var useTextConfirmation by rememberSaveable { mutableStateOf(touchExploration) }
    var confirmation by remember { mutableStateOf("") }
    val verified = if (useTextConfirmation || !hasMotionSensor) confirmation == "DELETE" else shakeCount >= requiredShakes

    fun resetChallenge() {
        requiredShakes = (6..9).random()
        shakeCount = 0
        currentSpeed = 0f
        lastShakeAt = 0L
    }

    DisposableEffect(requiredShakes) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val packageManager = context.packageManager
        val motionSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
            ?: sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE_UNCALIBRATED)
            ?: sensorManager?.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
            ?: sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (sensorManager == null || motionSensor == null) {
            hasMotionSensor = false
            onDispose { }
        } else {
            hasMotionSensor = true
            sensorLabel = when (motionSensor.type) {
                Sensor.TYPE_GYROSCOPE -> L10n.text("陀螺仪")
                Sensor.TYPE_GYROSCOPE_UNCALIBRATED -> L10n.text("未校准陀螺仪")
                Sensor.TYPE_LINEAR_ACCELERATION -> L10n.text("线性加速度计")
                Sensor.TYPE_ACCELEROMETER -> L10n.text("加速度计")
                else -> L10n.text("运动传感器")
            }
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent?) {
                    val values = event?.values ?: return
                    val magnitude = sqrt(
                        (values[0] * values[0] + values[1] * values[1] + values[2] * values[2]).toDouble(),
                    ).toFloat()
                    val normalizedSpeed = if (motionSensor.type == Sensor.TYPE_GYROSCOPE ||
                        motionSensor.type == Sensor.TYPE_GYROSCOPE_UNCALIBRATED
                    ) {
                        magnitude
                    } else {
                        magnitude - SensorManager.GRAVITY_EARTH
                    }
                    currentSpeed = normalizedSpeed.coerceAtLeast(0f)
                    if (verified) return
                    val now = System.currentTimeMillis()
                    val threshold = if (motionSensor.type == Sensor.TYPE_GYROSCOPE ||
                        motionSensor.type == Sensor.TYPE_GYROSCOPE_UNCALIBRATED
                    ) {
                        GYRO_SHAKE_THRESHOLD
                    } else {
                        ACCEL_SHAKE_THRESHOLD
                    }
                    if (currentSpeed >= threshold && now - lastShakeAt >= GYRO_SHAKE_COOLDOWN_MS) {
                        lastShakeAt = now
                        shakeCount = (shakeCount + 1).coerceAtMost(requiredShakes)
                    }
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
            }
            sensorManager.registerListener(listener, motionSensor, SensorManager.SENSOR_DELAY_GAME)
            onDispose { sensorManager.unregisterListener(listener) }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(L10n.text("删除账号")) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(AppSpacing.S8)) {
                Text(L10n.text("此操作不可撤销，请谨慎处理"))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = useTextConfirmation || !hasMotionSensor,
                        enabled = hasMotionSensor, onCheckedChange = { useTextConfirmation = it })
                    Text(L10n.text("使用文字确认"))
                }
                if (useTextConfirmation || !hasMotionSensor) {
                    Text(L10n.text("输入 DELETE 确认删除账号"))
                    AppOutlinedField(confirmation, { confirmation = it }, label = { Text("DELETE") })
                } else if (hasMotionSensor) {
                    Text(L10n.format("已检测传感器：{0}", sensorLabel))
                    LinearProgressIndicator(
                        progress = { shakeCount.toFloat() / requiredShakes.toFloat() },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(L10n.format("晃动进度：{0} / {1}", shakeCount, requiredShakes))
                    Text(L10n.format("当前运动强度：{0}", "%.2f".format(currentSpeed)))
                    if (verified) {
                        Text(L10n.text("验证通过，可以继续删除。"), color = MaterialTheme.colorScheme.primary)
                    } else {
                        Text(L10n.text("手持手机连续晃动完成验证。"))
                    }
                    OutlinedButton(onClick = ::resetChallenge) {
                        Text(L10n.text("重新验证"))
                    }
                } else {
                    Text(L10n.text("当前设备未检测到可用运动传感器，无法完成验证。"))
                }
            }
        },
        confirmButton = {
            LoadingTextButton(text = L10n.text("删除"), enabled = verified, onClick = {
                runCatchingCancellable { container.securityRepository.deleteAccount(DELETE_ACCOUNT_CONFIRM_TEXT) }
                    .onSuccess { onDeleted() }
                    .onFailure { onMessage(it.message ?: L10n.text("删除账号失败")) }
            })
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(L10n.text("取消")) } },
    )
}
