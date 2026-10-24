package cn.ksuser.auth.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.key
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cn.ksuser.auth.core.settings.DisplayTime
import cn.ksuser.auth.core.network.runCatchingCancellable
import cn.ksuser.auth.core.session.AccountRequestGuard
import cn.ksuser.auth.core.settings.L10n
import cn.ksuser.auth.data.AppContainer
import cn.ksuser.auth.ui.components.ApplicationLogo
import cn.ksuser.auth.ui.components.AppSettingsRow
import cn.ksuser.auth.ui.components.AppPagePadding
import cn.ksuser.auth.ui.components.LoadingOutlinedButton
import cn.ksuser.auth.ui.components.LoadingTextButton
import cn.ksuser.auth.ui.components.SectionCard
import kotlinx.coroutines.launch

private data class AppGrant(
    val id: String, val name: String, val official: Boolean, val scopes: List<String>,
    val authorizedAt: String?, val lastAuthorizedAt: String?, val grantMode: String,
    val expiresAt: String? = null, val creator: String? = null, val verification: String? = null,
    val contact: String? = null, val redirect: String? = null, val logoUrl: String? = null,
)

@Composable
internal fun AuthorizedAppsScreen(container: AppContainer, onMessage: (String) -> Unit) {
    val account by container.sessionRepository.cachedProfile.collectAsState()
    val accountId = account?.uuid
    key(accountId) {
        if (accountId != null) AuthorizedAppsContent(container, accountId, onMessage)
    }
}

@Composable
private fun AuthorizedAppsContent(container: AppContainer, accountId: String, onMessage: (String) -> Unit) {
    var grants by remember { mutableStateOf(emptyList<AppGrant>()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<Throwable?>(null) }
    var selected by remember { mutableStateOf<AppGrant?>(null) }
    var revoke by remember { mutableStateOf<AppGrant?>(null) }
    val scope = rememberCoroutineScope()
    val refreshGuard = remember(container) { AccountRequestGuard { container.sessionRepository.cachedUser()?.uuid } }
    val revokeGuard = remember(container) { AccountRequestGuard { container.sessionRepository.cachedUser()?.uuid } }
    suspend fun reload() {
        val request = refreshGuard.begin(accountId) ?: return
        loading = true
        error = null
        runCatchingCancellable { container.accountRepository.authorizedApps() }.onSuccess { snapshot ->
            if (!refreshGuard.isCurrent(request)) return@onSuccess
            grants = snapshot.official.map {
                AppGrant(it.clientId, it.clientName, true, it.scopes, it.authorizedAt, it.lastAuthorizedAt, "OFFICIAL", logoUrl = it.logoUrl)
            } + snapshot.thirdParty.map {
                AppGrant(it.appId, it.appName, false, it.scopes, it.authorizedAt, it.lastAuthorizedAt,
                    it.grantMode, it.expiresAt, it.creatorName, it.creatorVerificationType, it.contactInfo, it.redirectUri, it.logoUrl)
            }
            selected = selected?.let { old -> grants.firstOrNull { it.id == old.id && it.official == old.official } }
            revoke = revoke?.let { old -> grants.firstOrNull { it.id == old.id && it.official == old.official } }
        }.onFailure { if (refreshGuard.isCurrent(request)) error = it }
        if (refreshGuard.isCurrent(request)) loading = false
    }
    LaunchedEffect(container, accountId) { reload() }
    LazyColumn(Modifier.fillMaxSize().padding(AppPagePadding), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { LoadingOutlinedButton(L10n.text("刷新"), onClick = { reload() }, Modifier.fillMaxWidth(), enabled = !loading) }
        if (loading && grants.isEmpty()) item { CircularProgressIndicator() }
        error?.let { failure -> item { SectionCard { Text(failure.message ?: L10n.text("加载失败"), color = MaterialTheme.colorScheme.error); TextButton(enabled = !loading, onClick = { scope.launch { reload() } }) { Text(L10n.text("重试")) } } } }
        if (!loading && error == null && grants.isEmpty()) item { SectionCard(Modifier.fillMaxWidth()) { Text(L10n.text("暂无已授权应用"), style = MaterialTheme.typography.titleMedium) } }
        items(grants, key = { "${it.official}:${it.id}" }) { grant ->
            SectionCard(Modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                AppSettingsRow(
                    grant.name,
                    value = "${L10n.text(if (grant.official) "官方应用" else "第三方应用")} · ${grantModeLabel(grant.grantMode)}\n${L10n.text("最近授权")}：${DisplayTime.format(grant.lastAuthorizedAt)}",
                    leadingContent = { ApplicationLogo(grant.name, grant.logoUrl) },
                    onClick = { selected = grant },
                )
            }
        }
    }
    selected?.let { grant ->
        AlertDialog(onDismissRequest = { selected = null }, title = { Text(grant.name) }, text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                GrantDetail(L10n.text("应用类型"), L10n.text(if (grant.official) "官方应用" else "第三方应用"))
                GrantDetail("ID", grant.id)
                grant.creator?.let { GrantDetail(L10n.text("开发者"), it) }
                grant.verification?.let { GrantDetail(L10n.text("开发者认证"), it) }
                grant.contact?.let { GrantDetail(L10n.text("联系方式"), it) }
                grant.redirect?.let { GrantDetail(L10n.text("回调网站"), it) }
                GrantDetail(L10n.text("授权权限"), grant.scopes.joinToString("\n") { scopeLabel(it) })
                GrantDetail(L10n.text("授权方式"), grantModeLabel(grant.grantMode))
                GrantDetail(L10n.text("首次授权"), DisplayTime.format(grant.authorizedAt))
                GrantDetail(L10n.text("最近授权"), DisplayTime.format(grant.lastAuthorizedAt))
                grant.expiresAt?.let { GrantDetail(L10n.text("有效期至"), DisplayTime.format(it)) }
            }
        }, confirmButton = { TextButton(onClick = { revoke = grant }) { Text(L10n.text("撤销授权"), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { selected = null }) { Text(L10n.text("关闭")) } })
    }
    revoke?.let { grant ->
        AlertDialog(onDismissRequest = { revoke = null }, title = { Text(L10n.text("确认撤销授权")) },
            text = { Text("${grant.name}\n${L10n.text("撤销后，该应用需要重新获取你的授权才能访问账号信息。")}") },
            confirmButton = { LoadingTextButton(L10n.text("撤销授权"), onClick = {
                val request = revokeGuard.begin(accountId) ?: return@LoadingTextButton
                runCatchingCancellable {
                    if (grant.official) container.accountRepository.revokeOfficial(grant.id)
                    else container.accountRepository.revokeThirdParty(grant.id)
                }.onSuccess {
                    if (!revokeGuard.isCurrent(request)) return@onSuccess
                    grants = grants.filterNot { it.id == grant.id && it.official == grant.official }
                    revoke = null; selected = null
                    onMessage(L10n.text("授权已撤销"))
                    reload()
                }.onFailure { if (revokeGuard.isCurrent(request)) onMessage(it.message ?: L10n.text("撤销授权失败")) }
            }) }, dismissButton = { TextButton(onClick = { revoke = null }) { Text(L10n.text("取消")) } })
    }
}

@Composable
private fun GrantDetail(label: String, value: String) {
    Column { Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(value) }
}

internal fun grantModeLabel(value: String): String = L10n.text(when (value.uppercase()) {
    "PERSISTENT" -> "长期授权"
    "ONCE", "ONE_TIME" -> "一次性授权"
    "TIME_LIMITED" -> "限时授权"
    "OFFICIAL" -> "官方授权"
    else -> value
})

internal fun scopeLabel(scope: String): String = L10n.text(when (scope) {
    "openid" -> "账号标识"
    "profile" -> "基本资料"
    "email" -> "邮箱地址"
    "real_name" -> "真实姓名"
    "gender" -> "性别"
    "birth_date" -> "出生日期"
    "region" -> "地区"
    else -> scope
})
