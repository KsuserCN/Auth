package cn.ksuser.auth.ui

import android.app.DatePickerDialog
import android.graphics.Bitmap
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.automirrored.outlined.Logout
import cn.ksuser.auth.ui.components.AppSettingsRow
import cn.ksuser.auth.ui.components.AppSectionTitle
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
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
    onOpenSettings: () -> Unit = {},
) {
    val completedCount = listOf(
        user?.username.takeMeaningfulProfileText() != null,
        user?.realName.takeMeaningfulProfileText() != null,
        user?.region.takeMeaningfulProfileText() != null,
        user?.bio.takeMeaningfulProfileText() != null,
        !user?.avatarUrl.isNullOrBlank(),
    ).count { it }
    val name = user?.username.takeMeaningfulProfileText() ?: user?.realName.takeMeaningfulProfileText() ?: L10n.text("你好")
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(AppPagePadding),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.S24),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(L10n.text("欢迎回来"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(name, style = MaterialTheme.typography.headlineLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(user?.email ?: L10n.text("当前账号未绑定邮箱"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Surface(onClick = onOpenProfile, modifier = Modifier.semantics { contentDescription = L10n.text("编辑资料") }, shape = RoundedCornerShape(AppRadius.R20), color = MaterialTheme.colorScheme.surface) {
                AvatarBadge(user?.avatarUrl, name.take(1))
            }
        }
        HomeSecurityCard(user, onRefresh, onOpenSecurity)
        Column {
            AppSectionTitle(L10n.text("常用功能"))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    HomeQuickAction(Icons.Outlined.Settings, L10n.text("设置"), L10n.text("语言、外观与更新"), onOpenSettings, Modifier.weight(1f).testTag("home_settings"))
                    HomeQuickAction(Icons.Outlined.Devices, L10n.text("设备会话"), L10n.text("管理登录设备"), onOpenSessions, Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    HomeQuickAction(Icons.Outlined.AdminPanelSettings, L10n.text("安全设置"), L10n.text("验证与账号保护"), onOpenSecurity, Modifier.weight(1f))
                    HomeQuickAction(Icons.Outlined.Person, L10n.text("编辑资料"), L10n.text("个人信息与头像"), onOpenProfile, Modifier.weight(1f))
                }
            }
        }
        Surface(onClick = onOpenProfile, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(AppRadius.R20), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(L10n.text("资料完善度"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    Text("${completedCount * 20}%", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                LinearProgressIndicator(progress = { completedCount / 5f }, modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.primaryContainer)
                Text(L10n.format("已完成 {0}/{1} 项", completedCount, 5), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun HomeSecurityCard(user: UserProfile?, onRefresh: () -> Unit, onOpenSecurity: () -> Unit) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val ink = if (dark) MaterialTheme.colorScheme.surfaceContainerHigh else Color(0xFF26342E)
    val foreground = Color(0xFFF7F6F0)
    val accent = cn.ksuser.auth.ui.theme.BrandGold
    val settings = user?.settings
    Surface(shape = RoundedCornerShape(AppRadius.R24), color = ink, contentColor = foreground) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(accent.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.AdminPanelSettings, null, Modifier.size(21.dp), tint = accent)
                }
                Text(L10n.text("账号安全"), Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = foreground.copy(alpha = 0.8f))
                IconButton(onClick = onRefresh, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Outlined.Refresh, L10n.text("刷新状态"), Modifier.size(20.dp), tint = foreground.copy(alpha = 0.8f))
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(when {
                    settings == null -> L10n.text("正在加载安全状态")
                    settings.mfaEnabled -> L10n.text("双重验证已开启")
                    else -> L10n.text("建议继续完善安全设置")
                }, style = MaterialTheme.typography.headlineSmall, color = foreground)
                Text(L10n.text("管理验证方式与登录保护"), style = MaterialTheme.typography.bodyMedium, color = foreground.copy(alpha = 0.65f))
            }
            if (settings != null) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HomeSecurityStatus(L10n.text("双重验证"), settings.mfaEnabled, foreground, accent)
                    HomeSecurityStatus(L10n.text("异常登录提醒"), settings.detectUnusualLogin, foreground, accent)
                    HomeSecurityStatus(L10n.text("敏感操作邮件提醒"), settings.notifySensitiveActionEmail, foreground, accent)
                }
            }
            Button(onClick = onOpenSecurity, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = cn.ksuser.auth.ui.theme.BrandOnGold)) {
                Text(L10n.text("前往安全设置"), Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun HomeSecurityStatus(title: String, enabled: Boolean, foreground: Color, accent: Color) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(if (enabled) Icons.Outlined.CheckCircle else Icons.Outlined.Info, null, Modifier.size(15.dp), tint = if (enabled) accent else foreground.copy(alpha = 0.55f))
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = foreground.copy(alpha = 0.8f))
        Text(L10n.text(if (enabled) "已开启" else "未开启"), style = MaterialTheme.typography.labelSmall, color = if (enabled) accent else foreground.copy(alpha = 0.65f))
    }
}

@Composable
private fun HomeQuickAction(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(AppRadius.R20), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().heightIn(min = 112.dp).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)), contentAlignment = Alignment.Center) {
                    Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.weight(1f))
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.outline)
            }
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
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
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
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
    onRefresh: () -> Unit = {},
    onLogout: () -> Unit = {},
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(AppPagePadding),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.S24),
    ) {
        ProfileHeaderCard(currentUser?.avatarUrl, currentUser?.username.orEmpty().ifBlank { L10n.text("未设置") },
            onAvatarClick = { onNavigateToEdit("avatar") }, onUsernameClick = { onNavigateToEdit("username") }, onRefresh = onRefresh)
        SectionCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp), contentSpacing = 0.dp) {
            AppSettingsRow(L10n.text("设置"), value = L10n.text("语言、外观与更新"), icon = Icons.Outlined.Settings, onClick = onNavigateToAbout)
            HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            AppSettingsRow(L10n.text("已授权应用"), value = L10n.text("查看和撤销第三方及官方应用授权"), icon = Icons.Outlined.Apps, onClick = onNavigateToAuthorizedApps)
        }
        Column {
            AppSectionTitle(L10n.text("个人资料"))
            SectionCard(Modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp), contentSpacing = 0.dp) {
                val fields = listOf(
                    Triple("realName", L10n.text("真实姓名"), currentUser?.realName),
                    Triple("gender", L10n.text("性别"), genderLabel(currentUser?.gender)),
                    Triple("birthDate", L10n.text("生日"), currentUser?.birthDate),
                    Triple("region", L10n.text("地区"), currentUser?.region),
                    Triple("bio", L10n.text("个人简介"), currentUser?.bio),
                )
                fields.forEachIndexed { index, (key, title, value) ->
                    if (index > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    AppSettingsRow(title, value = value?.takeMeaningfulProfileText() ?: L10n.text("未设置"), onClick = { onNavigateToEdit(key) })
                }
            }
        }
        Column {
            AppSectionTitle(L10n.text("账号与授权"))
            SectionCard(Modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp), contentSpacing = 0.dp) {
                AppSettingsRow(L10n.text("邮箱"), value = currentUser?.email ?: L10n.text("暂无邮箱"))
                HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                AppSettingsRow(L10n.text("账号标识"), value = currentUser?.uuid.orEmpty())
            }
        }
        TextButton(onClick = onLogout, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
            Icon(Icons.AutoMirrored.Outlined.Logout, null)
            Spacer(Modifier.width(8.dp))
            Text(L10n.text("退出登录"))
        }
    }
}

@Composable
internal fun AboutScreen(container: AppContainer) {
    val context = LocalContext.current
    val appIdentity = remember(context) { AppIdentityProvider.current(context) }
    val passkeyAvailabilityMessage = remember(container) { container.passkeyManager.availabilityMessage() }
    var updateInfo by remember { mutableStateOf<AppUpdateInfo?>(null) }
    var updateState by remember { mutableStateOf("checking") }
    var updateFailure by remember { mutableStateOf<String?>(null) }
    val updateMessage = when (updateState) {
        "available" -> updateInfo?.let { L10n.format("发现新版 {0} ({1})", it.latestVersionName, it.latestVersionCode) }.orEmpty()
        "latest" -> L10n.text("当前已是最新版本")
        "downloaded" -> L10n.text("下载完成后返回应用继续安装")
        "failed" -> L10n.text(updateFailure ?: "检查更新失败，请稍后再试")
        else -> L10n.text("正在检查更新...")
    }

    suspend fun checkForUpdate(showFreshMessage: Boolean = false) {
        if (showFreshMessage) {
            updateState = "checking"
        }
        runCatching { container.appUpdateRepository.checkForUpdate() }
            .onSuccess { info ->
                updateInfo = info
                updateState = if (info.isUpdateAvailable) "available" else "latest"
            }
            .onFailure { throwable ->
                updateState = "failed"
                updateFailure = throwable.message ?: "检查更新失败，请稍后再试"
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
        verticalArrangement = Arrangement.spacedBy(AppSpacing.S24),
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
                    Text(L10n.text("Ksuser安全"), style = MaterialTheme.typography.titleLarge)
                    Text(
                        L10n.format("版本 {0}", appIdentity.versionName),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        SectionCard(modifier = Modifier.fillMaxWidth()) {
            AboutInfoRow(title = L10n.text("软件名称"), value = L10n.text("Ksuser安全"))
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
                            .onSuccess { updateState = "downloaded" }
                            .onFailure { updateState = "failed"; updateFailure = it.message ?: "下载失败，请重试" }
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
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(AppPagePadding),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.S24),
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
                    modifier = Modifier.heightIn(min = 52.dp),
                ) { Text(L10n.text("选择并上传头像")) }
            } else {
                when (fieldKey) {
                    "gender" -> androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
    onRefresh: () -> Unit,
) {
    Surface(shape = RoundedCornerShape(AppRadius.R24), color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.clip(RoundedCornerShape(16.dp)).clickable(role = Role.Button, onClickLabel = L10n.text("修改头像"), onClick = onAvatarClick)) { AvatarBadge(avatarUrl, username.take(1)) }
            Column(Modifier.weight(1f).heightIn(min = 64.dp).clickable(onClick = onUsernameClick), verticalArrangement = Arrangement.Center) {
                Text(username, style = MaterialTheme.typography.headlineMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(L10n.text("编辑用户名"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onRefresh, modifier = Modifier.size(48.dp)) { Icon(Icons.Outlined.Refresh, L10n.text("刷新用户信息")) }
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
