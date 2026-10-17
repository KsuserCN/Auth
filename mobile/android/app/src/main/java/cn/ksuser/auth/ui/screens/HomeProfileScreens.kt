package cn.ksuser.auth.ui

import android.app.DatePickerDialog
import android.graphics.Bitmap
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.painterResource
import coil.compose.AsyncImage
import cn.ksuser.auth.R
import cn.ksuser.auth.core.app.AppIdentityProvider
import cn.ksuser.auth.core.env.EnvironmentProvider
import cn.ksuser.auth.data.AppContainer
import cn.ksuser.auth.data.model.AppUpdateInfo
import cn.ksuser.auth.data.model.UserProfile
import cn.ksuser.auth.ui.components.AppOutlinedField
import cn.ksuser.auth.ui.components.AppPagePadding
import cn.ksuser.auth.ui.components.AppRadius
import cn.ksuser.auth.ui.components.AppSpacing
import cn.ksuser.auth.ui.components.BrandHeroCard
import cn.ksuser.auth.ui.components.GradientPrimaryButton
import cn.ksuser.auth.ui.components.LoadingButton
import cn.ksuser.auth.ui.components.LoadingOutlinedButton
import cn.ksuser.auth.ui.components.SectionCard
import kotlinx.coroutines.launch
import cn.ksuser.auth.core.settings.L10n
import cn.ksuser.auth.ui.settings.DeviceSettingsCard
import java.time.LocalDate

@Composable
internal fun HomeScreen(
    user: UserProfile?,
    onRefresh: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenSecurity: () -> Unit,
    onOpenSessions: () -> Unit,
) {
    val displayRealName = user?.realName.takeMeaningfulProfileText()
    val displayUsername = user?.username.takeMeaningfulProfileText()
    val displayRegion = user?.region.takeMeaningfulProfileText()
    val displayBio = user?.bio.takeMeaningfulProfileText()
    val hasAvatar = !user?.avatarUrl.isNullOrBlank()
    val completionItems = listOf(
        displayUsername != null,
        displayRealName != null,
        displayRegion != null,
        displayBio != null,
        hasAvatar,
    )
    val completedCount = completionItems.count { it }
    val completionPercent = if (completionItems.isEmpty()) 0 else completedCount * 100 / completionItems.size
    val securitySummary = when {
        user?.settings?.mfaEnabled == true && user.settings.detectUnusualLogin -> L10n.text("账号保护较完整")
        user?.settings?.mfaEnabled == true -> L10n.text("双重验证已开启")
        else -> L10n.text("建议继续完善安全设置")
    }
    val greetingName = displayUsername
        ?: displayRealName
        ?: L10n.text("你好")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(AppPagePadding),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.S12),
    ) {
        AccountHeroCard(
            user = user,
            greetingName = greetingName,
            securitySummary = securitySummary,
            onRefresh = onRefresh,
            onOpenProfile = onOpenProfile,
        )
        SecurityOverviewCard(
            user = user,
            onOpenSecurity = onOpenSecurity,
        )
        QuickActionsCard(
            onOpenProfile = onOpenProfile,
            onOpenSecurity = onOpenSecurity,
            onOpenSessions = onOpenSessions,
        )
        ProfileCompletionCard(
            user = user,
            completionPercent = completionPercent,
            completedCount = completedCount,
            totalCount = completionItems.size,
            onOpenProfile = onOpenProfile,
        )
    }
}

@Composable
private fun AccountHeroCard(
    user: UserProfile?,
    greetingName: String,
    securitySummary: String,
    onRefresh: () -> Unit,
    onOpenProfile: () -> Unit,
) {
    BrandHeroCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.S12)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.S12),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AvatarBadge(
                    avatarUrl = user?.avatarUrl,
                    label = greetingName.take(1),
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(L10n.format("欢迎回来，{0}", greetingName), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(
                        user?.email.orEmpty().ifBlank { L10n.text("当前账号未绑定邮箱") },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        securitySummary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.S8),
            ) {
                Button(
                    onClick = onOpenProfile,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(AppRadius.R12),
                ) {
                    Text(L10n.text("查看资料"))
                }
                OutlinedButton(
                    onClick = onRefresh,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(AppRadius.R12),
                ) {
                    Icon(Icons.Outlined.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(AppSpacing.S8))
                    Text(L10n.text("刷新状态"))
                }
            }
        }
    }
}

@Composable
private fun SecurityOverviewCard(
    user: UserProfile?,
    onOpenSecurity: () -> Unit,
) {
    val settings = user?.settings
    val mfaStatus = if (settings?.mfaEnabled == true) L10n.text("已开启") else L10n.text("未开启")
    val unusualLoginStatus = if (settings?.detectUnusualLogin == true) L10n.text("已开启") else L10n.text("未开启")
    val notifyStatus = if (settings?.notifySensitiveActionEmail == true) L10n.text("已开启") else L10n.text("未开启")

    SectionCard(modifier = Modifier.fillMaxWidth()) {
        Text(L10n.text("账号安全"), style = MaterialTheme.typography.titleLarge)
        Text(
            if (settings?.mfaEnabled == true) {
                L10n.text("你的账号已启用额外验证，安全性更高。")
            } else {
                L10n.text("建议开启双重验证，避免密码泄露后被直接登录。")
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AppSpacing.S8)) {
            StatusChip(title = L10n.text("双重验证"), value = mfaStatus, modifier = Modifier.weight(1f))
            StatusChip(title = L10n.text("异常登录提醒"), value = unusualLoginStatus, modifier = Modifier.weight(1f))
        }
        StatusChip(title = L10n.text("敏感操作邮件提醒"), value = notifyStatus, modifier = Modifier.fillMaxWidth())
        OutlinedButton(
            onClick = onOpenSecurity,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(AppRadius.R12),
        ) {
            Icon(Icons.Outlined.AdminPanelSettings, contentDescription = null)
            Spacer(modifier = Modifier.width(AppSpacing.S8))
            Text(L10n.text("前往安全设置"))
        }
    }
}

@Composable
private fun QuickActionsCard(
    onOpenProfile: () -> Unit,
    onOpenSecurity: () -> Unit,
    onOpenSessions: () -> Unit,
) {
    SectionCard(modifier = Modifier.fillMaxWidth()) {
        Text(L10n.text("常用功能"), style = MaterialTheme.typography.titleLarge)
        Text(L10n.text("这些入口用于处理资料完善、账号保护和设备管理。"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        QuickActionRow(
            icon = Icons.Outlined.Person,
            title = L10n.text("编辑资料"),
            subtitle = L10n.text("修改头像、昵称、地区和简介"),
            onClick = onOpenProfile,
        )
        QuickActionRow(
            icon = Icons.Outlined.AdminPanelSettings,
            title = L10n.text("安全设置"),
            subtitle = L10n.text("管理双重验证和敏感操作保护"),
            onClick = onOpenSecurity,
        )
        QuickActionRow(
            icon = Icons.Outlined.Devices,
            title = L10n.text("设备会话"),
            subtitle = L10n.text("查看当前登录设备并处理异常登录"),
            onClick = onOpenSessions,
        )
    }
}

@Composable
private fun ProfileCompletionCard(
    user: UserProfile?,
    completionPercent: Int,
    completedCount: Int,
    totalCount: Int,
    onOpenProfile: () -> Unit,
) {
    val displayUsername = user?.username.takeMeaningfulProfileText()
    val displayRealName = user?.realName.takeMeaningfulProfileText()
    val displayRegion = user?.region.takeMeaningfulProfileText()
    val displayBio = user?.bio.takeMeaningfulProfileText()
    val hasAvatar = !user?.avatarUrl.isNullOrBlank()

    SectionCard(modifier = Modifier.fillMaxWidth()) {
        Text(L10n.text("资料完善度"), style = MaterialTheme.typography.titleLarge)
        Text(
            L10n.format("已完成 {0}/{1} 项，当前完善度 {2}%。资料越完整，账号展示越清晰。", completedCount, totalCount, completionPercent),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        CompletionItem(label = L10n.text("用户名"), done = displayUsername != null)
        CompletionItem(label = L10n.text("头像"), done = hasAvatar)
        CompletionItem(label = L10n.text("真实姓名"), done = displayRealName != null)
        CompletionItem(label = L10n.text("地区"), done = displayRegion != null)
        CompletionItem(label = L10n.text("个人简介"), done = displayBio != null)
        OutlinedButton(
            onClick = onOpenProfile,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(AppRadius.R12),
        ) {
            Text(L10n.text("继续完善资料"))
        }
    }
}

private fun String?.takeMeaningfulProfileText(): String? {
    val normalized = this?.trim().orEmpty()
    if (normalized.isBlank()) return null
    if (normalized in setOf("无", "未设置", "暂无", "null", "NULL", "-")) return null
    return normalized
}


@Composable
private fun AvatarBadge(
    avatarUrl: String?,
    label: String,
) {
    Box(
        modifier = Modifier
            .size(60.dp)
            .clip(RoundedCornerShape(AppRadius.R20))
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        if (!avatarUrl.isNullOrBlank()) {
            AsyncImage(
                model = avatarUrl,
                contentDescription = L10n.text("头像"),
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                text = label.ifBlank { L10n.text("我") },
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun StatusChip(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(AppRadius.R12),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(AppSpacing.S12),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun QuickActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppRadius.R12))
            .clickable(onClick = onClick)
            .padding(vertical = AppSpacing.S8),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.S12),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(AppRadius.R12))
                .background(MaterialTheme.colorScheme.surfaceContainerLow),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(L10n.text("进入"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun CompletionItem(
    label: String,
    done: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Text(
            if (done) L10n.text("已完成") else L10n.text("待完善"),
            style = MaterialTheme.typography.labelMedium,
            color = if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun OverviewCard(
    title: String,
    subtitle: String,
    body: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    SectionCard(modifier = Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        Text(body, style = MaterialTheme.typography.bodyMedium)
        if (actionLabel != null && onAction != null) {
            LoadingButton(text = actionLabel, onClick = { onAction() })
        }
    }
}

@Composable
internal fun ProfileScreen(
    currentUser: UserProfile?,
    onNavigateToEdit: (String) -> Unit,
    onNavigateToAbout: () -> Unit,
    onNavigateToAuthorizedApps: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(AppPagePadding),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.S12),
    ) {
        ProfileHeaderCard(
            avatarUrl = currentUser?.avatarUrl,
            username = currentUser?.username.orEmpty().ifBlank { L10n.text("未设置") },
            onAvatarClick = { onNavigateToEdit("avatar") },
            onUsernameClick = { onNavigateToEdit("username") },
        )
        ProfileInfoCard(
            title = L10n.text("真实姓名"),
            value = currentUser?.realName.orEmpty().ifBlank { L10n.text("未设置") },
            onClick = { onNavigateToEdit("realName") },
        )
        ProfileInfoCard(
            title = L10n.text("性别"),
            value = genderLabel(currentUser?.gender),
            onClick = { onNavigateToEdit("gender") },
        )
        ProfileInfoCard(
            title = L10n.text("生日"),
            value = currentUser?.birthDate.orEmpty().ifBlank { L10n.text("未设置") },
            onClick = { onNavigateToEdit("birthDate") },
        )
        ProfileInfoCard(
            title = L10n.text("已授权应用"),
            value = L10n.text("查看和撤销第三方及官方应用授权"),
            onClick = onNavigateToAuthorizedApps,
        )
        ProfileInfoCard(
            title = L10n.text("地区"),
            value = currentUser?.region.orEmpty().ifBlank { L10n.text("未设置") },
            onClick = { onNavigateToEdit("region") },
        )
        ProfileInfoCard(
            title = L10n.text("个人简介"),
            value = currentUser?.bio.orEmpty().ifBlank { L10n.text("未设置") },
            onClick = { onNavigateToEdit("bio") },
        )
        OverviewCard(
            title = L10n.text("账号标识"),
            subtitle = currentUser?.email ?: L10n.text("暂无邮箱"),
            body = "UUID: ${currentUser?.uuid.orEmpty()}",
        )
        ProfileInfoCard(
            title = L10n.text("关于应用"),
            value = L10n.text("查看软件名称、版本、备案号与协议文档"),
            onClick = onNavigateToAbout,
        )
    }
}

@Composable
internal fun AboutScreen(container: AppContainer) {
    val context = LocalContext.current
    val appIdentity = remember(context) { AppIdentityProvider.current(context) }
    val passkeyAvailabilityMessage = remember(container) { container.passkeyManager.availabilityMessage() }
    var updateInfo by remember { mutableStateOf<AppUpdateInfo?>(null) }
    var updateMessage by remember { mutableStateOf(L10n.text("正在检查更新...")) }

    suspend fun checkForUpdate(showFreshMessage: Boolean = false) {
        if (showFreshMessage) {
            updateMessage = L10n.text("正在检查更新...")
        }
        runCatching { container.appUpdateRepository.checkForUpdate() }
            .onSuccess { info ->
                updateInfo = info
                updateMessage = if (info.isUpdateAvailable) {
                    L10n.format("发现新版 {0} ({1})", info.latestVersionName, info.latestVersionCode)
                } else {
                    L10n.text("当前已是最新版本")
                }
            }
            .onFailure { throwable ->
                updateMessage = throwable.message ?: L10n.text("检查更新失败，请稍后再试")
            }
    }

    LaunchedEffect(container) {
        checkForUpdate()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(AppPagePadding),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.S12),
    ) {
        DeviceSettingsCard(container)
        SectionCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.S8),
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(AppRadius.R16))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ksuser_launcher_icon),
                        contentDescription = L10n.text("应用图标"),
                        modifier = Modifier.size(40.dp),
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Ksuser Auth Android", style = MaterialTheme.typography.titleLarge)
                    Text(
                        L10n.format("版本 {0}", appIdentity.versionName),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        SectionCard(modifier = Modifier.fillMaxWidth()) {
            AboutInfoRow(title = L10n.text("软件名称"), value = "Ksuser Auth Android")
            AboutInfoRow(title = L10n.text("版本号"), value = "${appIdentity.versionName} (${appIdentity.versionCode})")
            AboutInfoRow(title = L10n.text("备案号"), value = L10n.text("沪ICP备2025144703号-3A"))
        }
        SectionCard(modifier = Modifier.fillMaxWidth()) {
            Text(L10n.text("应用更新"), style = MaterialTheme.typography.titleLarge)
            Text(updateMessage, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            updateInfo?.takeIf { it.isUpdateAvailable }?.let { info ->
                if (!info.releaseNotes.isNullOrBlank()) {
                    Text(info.releaseNotes, style = MaterialTheme.typography.bodyMedium)
                }
                if (!info.sha256.isNullOrBlank()) {
                    Text(
                        "SHA-256: ${info.sha256}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                GradientPrimaryButton(
                    text = if (info.forceUpdate) L10n.text("下载必要更新") else L10n.text("下载新版安装包"),
                    onClick = {
                        runCatching { container.appUpdateRepository.enqueueDownload(info) }
                            .onSuccess { updateMessage = L10n.text("下载完成后返回应用继续安装") }
                            .onFailure { updateMessage = it.message ?: L10n.text("下载失败，请重试") }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (container.appUpdateRepository.hasVerifiedUpdate()) {
                LoadingOutlinedButton(
                    text = L10n.text("继续安装已验证更新"),
                    onClick = {
                        (context as? android.app.Activity)?.let { activity ->
                            container.appUpdateRepository.retryVerifiedInstall(activity)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            LoadingOutlinedButton(
                text = L10n.text("重新检查"),
                onClick = { checkForUpdate(showFreshMessage = true) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
//        OverviewCard(
//            title = "环境与 Passkey",
//            subtitle = passkeyAvailabilityMessage,
//            body = buildString {
//                appendLine("API 前缀: ${EnvironmentProvider.current.apiBaseUrl}")
//                appendLine("RP ID: ${EnvironmentProvider.current.passkeyRpId}")
//                appendLine("Origin Hint: ${EnvironmentProvider.current.passkeyOriginHint}")
//                appendLine("Asset Links: ${EnvironmentProvider.current.passkeyOriginHint.trimEnd('/')}/.well-known/assetlinks.json")
//                appendLine("系统: Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
//                appendLine("UA: ${AppIdentityProvider.userAgent()}")
//                appendLine("包名: ${appIdentity.packageName}")
//                appendLine("版本: ${appIdentity.versionName} (${appIdentity.versionCode})")
//                append("签名 SHA-256: ${appIdentity.signingSha256.joinToString(" / ").ifBlank { "未知" }}")
//            },
//        )
        SectionCard(modifier = Modifier.fillMaxWidth()) {
            AboutLinkRow(L10n.text("反馈与帮助"), "https://github.com/KsuserCN/Auth/issues", onClick = {
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/KsuserCN/Auth/issues"))) }
            })
            AboutLinkRow(L10n.text("账号恢复"), "https://auth.ksuser.cn/forgot-password", onClick = {
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://auth.ksuser.cn/forgot-password"))) }
            })
            AboutLinkRow(
                title = L10n.text("服务条款"),
                url = L10n.documentationUrl("agreement/user"),
                onClick = {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(L10n.documentationUrl("agreement/user"))),
                    )
                },
            )
            AboutLinkRow(
                title = L10n.text("隐私协议"),
                url = L10n.documentationUrl("agreement/privacy"),
                onClick = {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(L10n.documentationUrl("agreement/privacy"))),
                    )
                },
            )
            AboutLinkRow(
                title = L10n.text("第三方信息共享清单"),
                url = L10n.documentationUrl("agreement/third-party-information-sharing"),
                onClick = {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(L10n.documentationUrl("agreement/third-party-information-sharing"))),
                    )
                },
            )
        }
    }
}

@Composable
internal fun ProfileEditScreen(
    container: AppContainer,
    currentUser: UserProfile?,
    fieldKey: String,
    onBack: () -> Unit,
    onProfileUpdated: () -> Unit,
    onMessage: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isBusy by remember { mutableStateOf(false) }
    var showAvatarPickerReason by rememberSaveable { mutableStateOf(false) }
    var cropBitmap by remember { mutableStateOf<Bitmap?>(null) }
    val singleLine = fieldKey != "bio"
    val title = when (fieldKey) {
        "username" -> L10n.text("编辑用户名")
        "realName" -> L10n.text("编辑真实姓名")
        "gender" -> L10n.text("编辑性别")
        "birthDate" -> L10n.text("编辑生日")
        "region" -> L10n.text("编辑地区")
        "bio" -> L10n.text("编辑个人简介")
        "avatar" -> L10n.text("编辑头像")
        else -> L10n.text("编辑资料")
    }
    var value by rememberSaveable(fieldKey, currentUser?.username, currentUser?.realName, currentUser?.region, currentUser?.bio) {
        mutableStateOf(
            when (fieldKey) {
                "username" -> currentUser?.username.orEmpty()
                "realName" -> currentUser?.realName.orEmpty()
                "gender" -> currentUser?.gender.orEmpty().ifBlank { "secret" }
                "birthDate" -> currentUser?.birthDate.orEmpty()
                "region" -> currentUser?.region.orEmpty()
                "bio" -> currentUser?.bio.orEmpty()
                else -> ""
            },
        )
    }
    val pickAvatarLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            isBusy = true
            runCatching { container.profileRepository.prepareAvatar(context.contentResolver, uri) }
                .onSuccess { cropBitmap = it }
                .onFailure { onMessage(it.message ?: L10n.text("头像上传失败")) }
            isBusy = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(AppPagePadding),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.S12),
    ) {
        SectionCard(modifier = Modifier.fillMaxWidth()) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            if (fieldKey == "avatar") {
                currentUser?.avatarUrl?.takeIf { it.isNotBlank() }?.let { avatarUrl ->
                    AsyncImage(
                        model = avatarUrl,
                        contentDescription = L10n.text("头像"),
                        modifier = Modifier
                            .size(88.dp)
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(AppRadius.R20)),
                    )
                }
                OutlinedButton(
                    onClick = { showAvatarPickerReason = true },
                    enabled = !isBusy,
                ) { Text(L10n.text("选择并上传头像")) }
            } else {
                when (fieldKey) {
                    "gender" -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("male", "female", "secret").forEach { option ->
                            FilterChip(selected = value == option, onClick = { value = option }, label = { Text(genderLabel(option)) })
                        }
                    }
                    "birthDate" -> {
                        Text(value.ifBlank { L10n.text("未设置") })
                        OutlinedButton(onClick = {
                            val initial = runCatching { LocalDate.parse(value) }.getOrDefault(LocalDate.now())
                            DatePickerDialog(context, { _, year, month, day ->
                                value = LocalDate.of(year, month + 1, day).toString()
                            }, initial.year, initial.monthValue - 1, initial.dayOfMonth).apply {
                                datePicker.maxDate = System.currentTimeMillis()
                            }.show()
                        }) { Text(L10n.text("选择日期")) }
                    }
                    else -> AppOutlinedField(value = value, onValueChange = { value = it }, singleLine = singleLine)
                }
                GradientPrimaryButton(
                    text = if (isBusy) L10n.text("保存中...") else L10n.text("保存"),
                    onClick = {
                        scope.launch {
                            isBusy = true
                            runCatching { container.profileRepository.updateField(fieldKey, value) }
                                .onSuccess {
                                    onProfileUpdated()
                                    onMessage(L10n.text("资料已更新"))
                                    onBack()
                                }
                                .onFailure { onMessage(it.message ?: L10n.text("更新失败")) }
                            isBusy = false
                        }
                    },
                    enabled = !isBusy,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    cropBitmap?.let { bitmap ->
        AvatarCropDialog(bitmap = bitmap,
            onDismiss = { cropBitmap = null },
            onSave = { cropped ->
                runCatching { container.profileRepository.uploadAvatar(cropped) }
                    .onSuccess { cropBitmap = null; onProfileUpdated(); onMessage(L10n.text("头像已上传")); onBack() }
                    .onFailure { onMessage(it.message ?: L10n.text("头像上传失败")) }
            })
    }

    if (showAvatarPickerReason) {
        AlertDialog(
            onDismissRequest = { showAvatarPickerReason = false },
            title = { Text(L10n.text("需要选择头像图片")) },
            text = {
                Text(
                    L10n.text("修改头像时，Ksuser 会打开系统图片选择器，并只读取你主动选择的那一张图片，用来上传并更新账号头像。应用不会浏览、扫描或上传你的整个图库。"),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showAvatarPickerReason = false
                        pickAvatarLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    },
                ) { Text(L10n.text("选择图片")) }
            },
            dismissButton = {
                TextButton(onClick = { showAvatarPickerReason = false }) { Text(L10n.text("取消")) }
            },
        )
    }
}

@Composable
private fun ProfileHeaderCard(
    avatarUrl: String?,
    username: String,
    onAvatarClick: () -> Unit,
    onUsernameClick: () -> Unit,
) {
    SectionCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.S12),
        ) {
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(AppRadius.R20))
                    .clickable(onClick = onAvatarClick),
            ) {
                if (!avatarUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = avatarUrl,
                        contentDescription = L10n.text("头像"),
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(AppRadius.R20))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            L10n.text("头像"),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onUsernameClick),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(L10n.text("用户名"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(username, style = MaterialTheme.typography.titleMedium)
                Text(L10n.text("点击头像修改头像"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                L10n.text("编辑"),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable(onClick = onUsernameClick),
            )
        }
    }
}

@Composable
private fun ProfileInfoCard(
    title: String,
    value: String,
    onClick: () -> Unit,
) {
    SectionCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                L10n.text("编辑"),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun AboutInfoRow(
    title: String,
    value: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun AboutLinkRow(
    title: String,
    url: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(AppRadius.R12))
            .clickable(onClick = onClick)
            .padding(vertical = AppSpacing.S8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(url, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
        Spacer(modifier = Modifier.width(AppSpacing.S8))
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun genderLabel(value: String?): String = L10n.text(when (value) {
    "male" -> L10n.text("男")
    "female" -> L10n.text("女")
    "secret" -> L10n.text("保密")
    else -> L10n.text("未设置")
})
