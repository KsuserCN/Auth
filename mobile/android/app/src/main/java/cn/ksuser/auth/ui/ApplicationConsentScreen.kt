package cn.ksuser.auth.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cn.ksuser.auth.core.settings.L10n
import cn.ksuser.auth.data.authorization.AuthorizationGrantMode
import cn.ksuser.auth.data.authorization.MobileAuthorizationLinks
import cn.ksuser.auth.data.model.UserProfile
import cn.ksuser.auth.ui.components.SectionCard
import coil.compose.AsyncImage
import kotlinx.coroutines.delay

@Composable
internal fun requestSecondsRemaining(expiresAtMillis: Long): Long {
    val remaining by produceState(initialValue = (expiresAtMillis - System.currentTimeMillis()).coerceAtLeast(0) / 1000, expiresAtMillis) {
        while (true) {
            value = (expiresAtMillis - System.currentTimeMillis()).coerceAtLeast(0) / 1000
            if (value == 0L) break
            delay(500)
        }
    }
    return remaining
}

@Composable
internal fun ApplicationConsentScreen(
    pending: PendingQrConfirmation,
    currentUser: UserProfile?,
    isBusy: Boolean,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    onOptionsChanged: (AuthorizationGrantMode, Int, Boolean) -> Unit,
) {
    val context = pending.authorization
    val preview = pending.authorizationPreview
    val official = context?.officialApplication == true || pending.type == QrConfirmationType.APPROVE_OFFICIAL
    val console = context?.consoleApplication == true || preview?.consoleApplication == true || pending.type == QrConfirmationType.APPROVE_CONSOLE_LOGIN
    val appName = context?.appName ?: preview?.targetName ?: if (console) "Ksuser Console" else copy("官方应用", "Official application")
    val scopes = context?.requestedScopes ?: preview?.requestedScopes.orEmpty()
    val remaining = requestSecondsRemaining(pending.expiresAtMillis)
    BackHandler { if (!isBusy) onCancel() }
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState())
                .padding(20.dp).widthIn(max = 840.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(copy(if (console) "管理中心登录" else "应用授权", if (console) "Console sign-in" else "Application authorization"), style = MaterialTheme.typography.titleLarge)
            AsyncImage(model = context?.logoUrl ?: preview?.targetLogoUrl, contentDescription = null, modifier = Modifier.size(64.dp).align(Alignment.CenterHorizontally))
            Text(appName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(when {
                console -> copy("仅超级管理员可登录管理中心，权限由服务器校验。", "Only super administrators can sign in to Console. The server checks your access.")
                official -> copy("官方应用授权 · 可在已授权应用中撤销", "Official application · Revoke access in Authorized applications")
                else -> context?.subjectName?.takeIf { it.isNotBlank() } ?: copy("开发者信息未提供", "Developer information unavailable")
            }, color = MaterialTheme.colorScheme.primary)
            SectionCard(Modifier.fillMaxWidth()) {
                Text(copy("应用信息", "Application details"), fontWeight = FontWeight.SemiBold)
                Detail(copy("应用名称", "Application"), appName)
                if (!official && !console) {
                    Detail(copy("开发者", "Developer"), context?.subjectName ?: copy("未提供", "Not provided"))
                    context?.subjectVerificationType?.let { Detail(copy("认证类型", "Verification"), when (it) {
                        "personal" -> copy("个人认证", "Individual")
                        "enterprise" -> copy("企业认证", "Enterprise")
                        "admin" -> copy("管理员", "Administrator")
                        else -> it
                    }) }
                    Detail(copy("联系方式", "Contact"), context?.contactInfo?.takeIf { it.isNotBlank() } ?: copy("未提供", "Not provided"))
                }
                Detail(copy("返回网站", "Return website"), context?.redirectUri?.let { MobileAuthorizationLinks.origin(it) } ?: preview?.targetOrigin ?: copy("未提供", "Not provided"))
            }
            SectionCard(Modifier.fillMaxWidth()) {
                Text(copy("授权范围", "Permissions requested"), fontWeight = FontWeight.SemiBold)
                if (scopes.isEmpty()) Text(copy("仅确认本次登录请求", "Confirm this sign-in request only"))
                scopes.forEach { scope ->
                    Text(scopeTitle(scope), style = MaterialTheme.typography.bodyLarge)
                    Text(scope, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(copy("你的密码不会提供给该应用。", "Your password is never shared with this application."), style = MaterialTheme.typography.bodySmall)
            }
            SectionCard(Modifier.fillMaxWidth()) {
                Text(copy("使用账号", "Account"), fontWeight = FontWeight.SemiBold)
                Text(currentUser?.username.orEmpty())
                Text(currentUser?.email.orEmpty(), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (context != null && !official) {
                SectionCard(Modifier.fillMaxWidth()) {
                    Text(copy("授权有效期", "Authorization duration"), fontWeight = FontWeight.SemiBold)
                    AuthorizationGrantMode.entries.forEach { mode ->
                        FilterChip(selected = pending.grantMode == mode, enabled = !isBusy, onClick = {
                            onOptionsChanged(mode, pending.grantTtlSeconds, pending.loginWeb)
                        }, label = { Text(when (mode) {
                            AuthorizationGrantMode.PERSISTENT -> copy("长期，直到撤销", "Persistent, until revoked")
                            AuthorizationGrantMode.ONE_TIME -> copy("一次性，仅本次有效", "One time, for this request")
                            AuthorizationGrantMode.TIME_LIMITED -> copy("限时", "Time limited")
                        }) })
                    }
                    if (pending.grantMode == AuthorizationGrantMode.TIME_LIMITED) {
                        listOf(300 to copy("5 分钟", "5 minutes"), 3600 to copy("1 小时", "1 hour"),
                            86400 to copy("24 小时", "24 hours"), 604800 to copy("7 天", "7 days")).forEach { (seconds, label) ->
                            FilterChip(selected = pending.grantTtlSeconds == seconds, enabled = !isBusy,
                                onClick = { onOptionsChanged(pending.grantMode, seconds, pending.loginWeb) }, label = { Text(label) })
                        }
                    }
                }
            }
            if (context?.qr == true) {
                SectionCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(copy("同时登录通行证网页端", "Also sign in to Passport in the browser"), modifier = Modifier.weight(1f))
                        Switch(checked = pending.loginWeb, enabled = !isBusy, onCheckedChange = {
                            onOptionsChanged(pending.grantMode, pending.grantTtlSeconds, it)
                        })
                    }
                    context.webOrigin?.let { Detail(copy("网页登录网站", "Browser website"), it) }
                    Text(copy("默认仅授权此应用。开启后，发起请求的浏览器也会登录当前账号。", "By default, only the application is authorized. Enabling this also signs the requesting browser in to your account."), style = MaterialTheme.typography.bodySmall)
                }
            }
            Text(if (remaining > 0) copy("请求将在 $remaining 秒后过期", "Request expires in $remaining seconds") else copy("请求已过期，请重新发起", "Request expired. Start again."), color = if (remaining == 0L) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = onConfirm, enabled = !isBusy && remaining > 0 && currentUser != null, modifier = Modifier.fillMaxWidth()) {
                Text(if (isBusy) copy("正在提交…", "Submitting…") else if (console) copy("确认登录管理中心", "Sign in to Console") else copy("同意并继续", "Approve and continue"))
            }
            OutlinedButton(onClick = onCancel, enabled = !isBusy, modifier = Modifier.fillMaxWidth()) {
                Text(if (remaining == 0L || pending.type == QrConfirmationType.APPROVE_CONSOLE_LOGIN) copy("关闭", "Close") else copy("拒绝授权", "Deny authorization"))
            }
            Text(if (context?.qr != false) copy("完成后请返回发起扫码请求的设备", "After confirming, return to the device that displayed the code") else copy("完成后将返回浏览器", "You will return to the browser when finished"), style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.padding(4.dp))
        }
    }
}

@Composable
private fun Detail(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun copy(chinese: String, english: String) = if (L10n.language == "en") english else chinese

private fun scopeTitle(scope: String): String = when (scope) {
    "openid" -> copy("确认账号身份", "Verify account identity")
    "profile" -> copy("读取基本资料", "Read basic profile")
    "email" -> copy("读取邮箱地址", "Read email address")
    "offline_access" -> copy("离线访问授权", "Offline access")
    "phone" -> copy("读取手机号码", "Read phone number")
    "address" -> copy("读取地址", "Read address")
    else -> scope
}
