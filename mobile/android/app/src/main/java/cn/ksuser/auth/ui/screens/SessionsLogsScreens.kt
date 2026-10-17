package cn.ksuser.auth.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.DesktopWindows
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.LaptopChromebook
import androidx.compose.material.icons.outlined.LaptopMac
import androidx.compose.material.icons.outlined.LaptopWindows
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.PhoneIphone
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector
import cn.ksuser.auth.data.AppContainer
import cn.ksuser.auth.core.settings.L10n
import cn.ksuser.auth.core.settings.DisplayTime
import cn.ksuser.auth.ui.components.AppOutlinedField
import cn.ksuser.auth.data.model.SensitiveLogItem
import cn.ksuser.auth.core.security.SecurityLogPresentation
import cn.ksuser.auth.data.model.SessionItem
import cn.ksuser.auth.ui.components.AppPagePadding
import cn.ksuser.auth.ui.components.AppRadius
import cn.ksuser.auth.ui.components.AppSpacing
import cn.ksuser.auth.ui.components.LoadingOutlinedButton
import cn.ksuser.auth.ui.components.SectionCard
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

@Composable
internal fun SessionsScreen(
    container: AppContainer,
    onLogoutAll: () -> Unit,
    onMessage: (String) -> Unit,
) {
    var sessions by remember { mutableStateOf(emptyList<SessionItem>()) }
    var loading by remember { mutableStateOf(true) }
    var sessionError by remember { mutableStateOf<String?>(null) }
    var pendingRevokeSession by remember { mutableStateOf<SessionItem?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun reload() {
        loading = true
        sessionError = null
        runCatching { container.sessionsRepository.getSessions() }
            .onSuccess { sessions = it }
            .onFailure { sessionError = it.message ?: L10n.text("加载会话列表失败"); onMessage(sessionError.orEmpty()) }
        loading = false
    }

    LaunchedEffect(Unit) { reload() }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(AppPagePadding),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.S12),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(L10n.text("设备与登录"), style = MaterialTheme.typography.headlineMedium)
                    Text(
                        L10n.text("查看和管理您已连接的设备"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                SmallRefreshButton(onClick = { reload() })
            }
        }

        sessionError?.let { message -> item { SectionCard {
            Text(message, color = MaterialTheme.colorScheme.error)
            LoadingOutlinedButton(L10n.text("重试"), onClick = { reload() })
        } } }
        if (loading && sessions.isEmpty()) {
            items(3) { SessionSkeletonCard() }
        } else if (sessions.isEmpty() && sessionError == null) {
            item {
                SectionCard {
                    Text(L10n.text("暂无设备"), style = MaterialTheme.typography.titleMedium)
                    Text(
                        L10n.text("当前没有活跃会话记录"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        items(sessions, key = { it.id }) { session ->
            SessionCardWithCornerRevoke(
                session = session,
                onRequestRevoke = { pendingRevokeSession = session },
            )
        }

        item {
            SectionCard {
                Text(
                    L10n.text("危险操作区域"),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                Text(
                    L10n.text("从所有设备退出登录将撤销所有会话（包括当前设备），需要重新登录。"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LoadingOutlinedButton(
                    text = L10n.text("退出所有设备"),
                    onClick = { onLogoutAll() },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    pendingRevokeSession?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingRevokeSession = null },
            title = { Text(L10n.text("确认撤销会话")) },
            text = {
                Text(
                    L10n.format("将撤销 {0}（{1}）的登录状态，确定继续吗？", getClientDisplayName(target), target.ipAddress),
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingRevokeSession = null
                        if (target.current) {
                            onMessage(L10n.text("无法撤销当前会话"))
                            return@TextButton
                        }
                        scope.launch {
                            val revoked = runCatching { container.sessionsRepository.revokeSession(target.id) }
                                .onFailure { onMessage(it.message ?: L10n.text("撤销会话失败")) }
                                .isSuccess
                            if (revoked) {
                                reload()
                                onMessage(L10n.text("会话已撤销"))
                            }
                        }
                    },
                ) { Text(L10n.text("确认")) }
            },
            dismissButton = {
                TextButton(onClick = { pendingRevokeSession = null }) { Text(L10n.text("取消")) }
            },
        )
    }
}

@Composable
internal fun LogsScreen(
    container: AppContainer,
    eventId: Long? = null,
    onEventOpened: () -> Unit = {},
) {
    var logs by remember { mutableStateOf(emptyList<SensitiveLogItem>()) }
    var page by rememberSaveable { mutableStateOf(1) }
    var selectedOperationType by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedResult by rememberSaveable { mutableStateOf<String?>(null) }
    var appliedOperationType by rememberSaveable { mutableStateOf<String?>(null) }
    var appliedResult by rememberSaveable { mutableStateOf<String?>(null) }
    var filterExpanded by rememberSaveable { mutableStateOf(false) }
    var totalPages by remember { mutableStateOf(0) }
    var total by remember { mutableStateOf(0L) }
    var queryEpoch by remember { mutableStateOf(0) }
    var busy by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var selectedLog by remember { mutableStateOf<SensitiveLogItem?>(null) }
    var detailError by remember { mutableStateOf<String?>(null) }
    var detailLoading by remember { mutableStateOf(false) }
    var detailId by remember { mutableStateOf<Long?>(null) }
    var startDate by rememberSaveable { mutableStateOf("") }
    var endDate by rememberSaveable { mutableStateOf("") }
    var appliedStartDate by rememberSaveable { mutableStateOf<String?>(null) }
    var appliedEndDate by rememberSaveable { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun openDetail(id: Long, refreshFirstPage: Boolean = false) {
        detailId = id
        selectedLog = null
        detailLoading = true
        detailError = null
        if (refreshFirstPage) {
            runCatching {
                container.logsRepository.getSensitiveLogs(
                    page = 1,
                    operationType = appliedOperationType,
                    result = appliedResult,
                    startDate = appliedStartDate,
                    endDate = appliedEndDate,
                )
            }.onSuccess { result ->
                logs = result.data
                page = 1
                totalPages = result.totalPages
                total = result.total
                if (detailId == id) selectedLog = result.data.firstOrNull { it.id == id }
            }.onFailure {
                if (detailId == id) detailError = it.message ?: L10n.text("日志加载失败")
            }
        } else {
            selectedLog = logs.firstOrNull { it.id == id }
        }
        if (detailId == id && selectedLog == null && detailError == null) {
            detailError = L10n.text("未找到这条安全操作记录，请刷新日志列表")
        }
        if (detailId == id) detailLoading = false
    }
    LaunchedEffect(eventId) {
        if (eventId != null) { openDetail(eventId, refreshFirstPage = true); onEventOpened() }
    }

    suspend fun reload() {
        busy = true
        loadError = null
        runCatching {
            container.logsRepository.getSensitiveLogs(
                page = page,
                operationType = appliedOperationType,
                result = appliedResult,
                startDate = appliedStartDate,
                endDate = appliedEndDate,
            )
        }.onSuccess {
            logs = it.data
            totalPages = it.totalPages
            total = it.total
        }.onFailure { loadError = it.message ?: L10n.text("日志加载失败") }
        busy = false
    }

    LaunchedEffect(page, appliedOperationType, appliedResult, appliedStartDate, appliedEndDate, queryEpoch) { reload() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(AppPagePadding),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.S12),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(L10n.text("近期敏感操作"), style = MaterialTheme.typography.headlineMedium)
                Text(
                    L10n.text("查看安全相关操作记录与风险分数"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            SmallRefreshButton(onClick = { reload() })
        }

        SectionCard(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(L10n.text("筛选条件"), style = MaterialTheme.typography.titleSmall)
                CompactOutlinedButton(
                    text = if (filterExpanded) L10n.text("收起") else L10n.text("展开"),
                    onClick = { filterExpanded = !filterExpanded },
                )
            }
            if (filterExpanded) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppOutlinedField(value = startDate, onValueChange = { startDate = it }, label = { Text(L10n.text("开始日期 YYYY-MM-DD")) }, modifier = Modifier.weight(1f))
                    AppOutlinedField(value = endDate, onValueChange = { endDate = it }, label = { Text(L10n.text("结束日期 YYYY-MM-DD")) }, modifier = Modifier.weight(1f))
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(AppSpacing.S8)) {
                    items(OperationTypeOptions) { option ->
                        FilterChip(
                            selected = selectedOperationType == option.value,
                            onClick = {
                                selectedOperationType = if (selectedOperationType == option.value) null else option.value
                            },
                            label = { Text(option.label) },
                        )
                    }
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(AppSpacing.S8)) {
                    items(ResultOptions) { option ->
                        FilterChip(
                            selected = selectedResult == option.value,
                            onClick = { selectedResult = if (selectedResult == option.value) null else option.value },
                            label = { Text(option.label) },
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.S8)) {
                    CompactPrimaryButton(
                        text = L10n.text("查询"),
                        onClick = {
                            val validStart = startDate.isBlank() || runCatching { java.time.LocalDate.parse(startDate) }.isSuccess
                            val validEnd = endDate.isBlank() || runCatching { java.time.LocalDate.parse(endDate) }.isSuccess
                            if (!validStart || !validEnd || (startDate.isNotBlank() && endDate.isNotBlank() && startDate > endDate)) {
                                loadError = L10n.text("请输入有效的日期范围")
                            } else {
                            page = 1
                            appliedStartDate = startDate.ifBlank { null }
                            appliedEndDate = endDate.ifBlank { null }
                            appliedOperationType = selectedOperationType
                            appliedResult = selectedResult
                            queryEpoch++
                            }
                        },
                    )
                    CompactOutlinedButton(
                        text = L10n.text("重置"),
                        onClick = {
                            page = 1
                            selectedOperationType = null
                            selectedResult = null
                            appliedOperationType = null
                            appliedResult = null
                            startDate = ""; endDate = ""; appliedStartDate = null; appliedEndDate = null
                            queryEpoch++
                        },
                    )
                    AssistChip(onClick = {}, enabled = false, label = { Text(L10n.format("共 {0} 条", total)) })
                }
            } else {
                val operationLabel = OperationTypeOptions.firstOrNull { it.value == selectedOperationType }?.label ?: L10n.text("全部操作")
                val resultLabel = ResultOptions.firstOrNull { it.value == selectedResult }?.label ?: L10n.text("全部结果")
                Text(
                    L10n.format("当前：{0} / {1}", operationLabel, resultLabel),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        loadError?.let { message ->
            SectionCard(Modifier.fillMaxWidth()) {
                Text(message, color = MaterialTheme.colorScheme.error)
                LoadingOutlinedButton(L10n.text("重试"), onClick = { reload() })
            }
        }
        if (busy && logs.isEmpty()) {
            repeat(3) { LogSkeletonCard() }
        } else if (logs.isEmpty() && loadError == null) {
            SectionCard {
                Text(L10n.text("暂无记录"), style = MaterialTheme.typography.titleMedium)
                Text(
                    L10n.text("当前筛选条件下没有匹配的敏感操作记录"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.S12),
        ) {
            logs.groupBy { DisplayTime.day(it.createdAt) }.forEach { (day, entries) ->
            item(key = "day:$day") { Text(day, style = MaterialTheme.typography.titleSmall) }
            items(entries, key = { it.id }) { log ->
                val showFailureReason = log.result.equals("FAILURE", true) && !log.displayFailureReason.isNullOrBlank()
                SectionCard(modifier = Modifier.fillMaxWidth().clickable { scope.launch { openDetail(log.id) } }) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top,
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(SecurityLogPresentation.title(log), style = MaterialTheme.typography.titleMedium)
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    SecurityLogPresentation.methods(log).forEach { tag ->
                                        StatusPill(
                                            text = tag,
                                            background = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                                            content = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                StatusPill(
                                    text = if (log.result.equals("SUCCESS", true)) { if (log.operationType == "ADAPTIVE_POLICY") L10n.text("已执行") else L10n.text("成功") } else L10n.text("失败"),
                                    background = if (log.result.equals("SUCCESS", true)) {
                                        MaterialTheme.colorScheme.tertiaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.errorContainer
                                    },
                                    content = if (log.result.equals("SUCCESS", true)) {
                                        MaterialTheme.colorScheme.tertiary
                                    } else {
                                        MaterialTheme.colorScheme.error
                                    },
                                )
                                Text(
                                    L10n.format("{0} · {1}", log.ipLocation ?: L10n.text("未知位置"), log.ipAddress),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                L10n.format("设备：{0} · 浏览器：{1}", log.deviceType ?: L10n.text("未知设备"), log.browser ?: L10n.text("未知浏览器")),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                L10n.format("时间：{0}", formatAbsoluteTime(log.createdAt)),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        RiskPill(score = log.riskScore)
                    }
                    if (log.operationType == "ADAPTIVE_POLICY") {
                        Text(SecurityLogPresentation.text("ui.system"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    log.actionTaken?.let { action ->
                        Text("${SecurityLogPresentation.text("ui.action")}：${SecurityLogPresentation.action(action)}", style = MaterialTheme.typography.bodySmall)
                    }
                    if (!showFailureReason && !log.displayFailureReason.isNullOrBlank()) {
                        Text(L10n.format("说明：{0}", log.displayFailureReason), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (log.triggeredMultiErrorLock) Text(L10n.text("触发连续错误保护"), style = MaterialTheme.typography.bodySmall)
                    if (log.triggeredRateLimitLock) Text(L10n.text("触发请求频率保护"), style = MaterialTheme.typography.bodySmall)
                    if (showFailureReason) {
                        Text(
                            L10n.format("失败原因：{0}", log.displayFailureReason),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
            }
        }

        if (busy && logs.isNotEmpty()) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp))
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.S8),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CompactOutlinedButton(
                text = L10n.text("上一页"),
                onClick = { if (page > 1) page-- },
                enabled = page > 1,
            )
            Text(
                L10n.format("第 {0} / {1} 页", page, if (totalPages == 0) 1 else totalPages),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            CompactOutlinedButton(
                text = L10n.text("下一页"),
                onClick = { if (page < totalPages) page++ },
                enabled = page < totalPages,
            )
        }
    }
    if (detailId != null) {
        AlertDialog(onDismissRequest = { detailId = null; selectedLog = null },
            title = { Text(L10n.text("安全日志详情")) },
            text = {
                Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (detailLoading) CircularProgressIndicator()
                    detailError?.let { message -> Text(message, color = MaterialTheme.colorScheme.error) }
                    selectedLog?.let { log ->
                        Text(SecurityLogPresentation.title(log), style = MaterialTheme.typography.titleMedium)
                        Text("ID: ${log.id}")
                        Text(L10n.format("{0}：{1}", L10n.text("时间"), DisplayTime.format(log.createdAt)))
                        Text(L10n.format("{0}：{1}", L10n.text("结果"), L10n.text(if (log.result.equals("success", true)) L10n.text("成功") else L10n.text("失败"))))
                        Text(L10n.format("{0}：{1}", L10n.text("验证方式"), SecurityLogPresentation.methods(log).joinToString()))
                        Text("IP: ${log.ipAddress} · ${log.ipLocation.orEmpty()}")
                        Text(L10n.format("{0}：{1} · {2}", L10n.text("设备"), log.deviceType.orEmpty(), log.browser.orEmpty()))
                        Text(L10n.format("{0}：{1}", L10n.text("风险分数"), log.riskScore))
                        Text(L10n.format("{0}：{1}", L10n.text("响应耗时"), log.durationMs?.let { "$it ms" } ?: L10n.text("暂无记录")))
                        log.actionTaken?.let { Text("${SecurityLogPresentation.text("ui.action")}：${SecurityLogPresentation.action(it)}") }
                        log.displayFailureReason?.let { Text(it) }
                        if (log.triggeredMultiErrorLock) Text(L10n.text("触发连续错误保护"))
                        if (log.triggeredRateLimitLock) Text(L10n.text("触发请求频率保护"))
                    }
                }
            }, confirmButton = { TextButton(onClick = { detailId = null; selectedLog = null }) { Text(L10n.text("关闭")) } },
            dismissButton = { if (detailError != null) TextButton(onClick = { detailId?.let { id -> scope.launch { openDetail(id, refreshFirstPage = true) } } }) { Text(L10n.text("刷新日志列表")) } })
    }

}

@Composable
private fun SessionCardWithCornerRevoke(
    session: SessionItem,
    onRequestRevoke: () -> Unit,
) {
    val showAction = !session.current

    Box(modifier = Modifier.fillMaxWidth()) {
        SectionCard(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(AppSpacing.S16),
        ) {
            SessionCardContent(session = session)
        }

        if (showAction) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(44.dp)
                    .clip(
                        RoundedCornerShape(
                            topStart = AppRadius.R12,
                            topEnd = 0.dp,
                            bottomStart = 0.dp,
                            bottomEnd = AppRadius.R20,
                        ),
                    )
                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.78f))
                    .clickable { onRequestRevoke() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.DeleteOutline,
                    contentDescription = L10n.text("撤销会话"),
                    tint = MaterialTheme.colorScheme.onError,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun SessionCardContent(session: SessionItem) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.S12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SessionLogo(session)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    getClientDisplayName(session),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (session.current) {
                    StatusPill(
                        text = L10n.text("当前"),
                        background = MaterialTheme.colorScheme.primaryContainer,
                        content = MaterialTheme.colorScheme.primary,
                    )
                }
                StatusPill(
                    text = if (session.online) L10n.text("在线") else L10n.text("离线"),
                    background = if (session.online) {
                        MaterialTheme.colorScheme.tertiaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    content = if (session.online) {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            Text(
                "${getSystemDisplayName(session)} · ${session.ipAddress}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(session.ipLocation ?: L10n.text("未知位置"), style = MaterialTheme.typography.bodyMedium)
        Text(
            L10n.format("最近活动：{0}", formatAbsoluteTime(session.lastSeenAt)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SessionLogo(session: SessionItem) {
    val (clientBg, clientFg) = when (getClientKind(session)) {
        ClientKind.DesktopApp -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.primary
        ClientKind.MobileApp -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.tertiary
        ClientKind.Web -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
    val icon = resolveSessionIcon(session)
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(clientBg),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = getSystemDisplayName(session),
            tint = clientFg,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun StatusPill(
    text: String,
    background: Color,
    content: Color,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(background)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(text, color = content, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun RiskPill(score: Int) {
    val (bg, fg) = when {
        score >= 70 -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.error
        score >= 40 -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.tertiary
    }
    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(L10n.text("风险"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        StatusPill(text = "${SecurityLogPresentation.text(if (score >= 70) "ui.high" else if (score >= 40) "ui.medium" else "ui.low")} · $score", background = bg, content = fg)
    }
}

@Composable
private fun CompactPrimaryButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.height(38.dp),
        shape = RoundedCornerShape(AppRadius.R12),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun CompactOutlinedButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.height(38.dp),
        shape = RoundedCornerShape(AppRadius.R12),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun SmallRefreshButton(
    onClick: suspend () -> Unit,
) {
    val scope = rememberCoroutineScope()
    OutlinedButton(
        onClick = { scope.launch { onClick() } },
        modifier = Modifier.height(32.dp),
        shape = RoundedCornerShape(AppRadius.R12),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.Refresh,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
        )
        Text(
            L10n.text("刷新"),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

@Composable
private fun SessionSkeletonCard() {
    SectionCard {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(18.dp)
                .clip(RoundedCornerShape(AppRadius.R8))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth(0.75f)
                .height(14.dp)
                .clip(RoundedCornerShape(AppRadius.R8))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth(0.42f)
                .height(12.dp)
                .clip(RoundedCornerShape(AppRadius.R8))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
        )
    }
}

@Composable
private fun LogSkeletonCard() {
    SectionCard {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .height(16.dp)
                .clip(RoundedCornerShape(AppRadius.R8))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .height(12.dp)
                .clip(RoundedCornerShape(AppRadius.R8))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .height(12.dp)
                .clip(RoundedCornerShape(AppRadius.R8))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f)),
        )
    }
}

private enum class ClientKind {
    DesktopApp,
    MobileApp,
    Web,
}

private fun getClientKind(session: SessionItem?): ClientKind {
    val ua = (session?.userAgent ?: "").lowercase(Locale.getDefault())
    return when {
        ua.contains("ksuserauthdesktop") -> ClientKind.DesktopApp
        ua.contains("ksuserauthmobile") -> ClientKind.MobileApp
        else -> ClientKind.Web
    }
}

private fun getClientDisplayName(session: SessionItem): String {
    return when (getClientKind(session)) {
        ClientKind.DesktopApp -> L10n.text("桌面端")
        ClientKind.MobileApp -> L10n.text("移动端")
        ClientKind.Web -> getBrowserDisplayName(session)
    }
}

private fun getBrowserDisplayName(session: SessionItem?): String {
    val raw = (session?.browser ?: session?.userAgent ?: "").lowercase(Locale.getDefault())
    return when {
        raw.contains("edge") || raw.contains("edg/") -> "Edge"
        raw.contains("firefox") -> "Firefox"
        raw.contains("opera") || raw.contains("opr/") -> "Opera"
        raw.contains("chrome") -> "Chrome"
        raw.contains("safari") -> "Safari"
        raw.contains("ie") || raw.contains("trident") -> "Internet Explorer"
        else -> session?.browser ?: L10n.text("未知浏览器")
    }
}

private fun getSystemDisplayName(session: SessionItem?): String {
    val ua = (session?.userAgent ?: session?.deviceType ?: "").lowercase(Locale.getDefault())
    return when {
        ua.contains("android") -> "Android"
        ua.contains("iphone") || ua.contains("ipad") || ua.contains("ios") -> "iOS"
        ua.contains("windows") -> "Windows"
        ua.contains("mac os") || ua.contains("macintosh") || ua.contains("mac") -> "macOS"
        ua.contains("linux") || ua.contains("x11") -> "Linux"
        ua.contains("cros") || ua.contains("chromeos") -> "ChromeOS"
        getClientKind(session) == ClientKind.DesktopApp -> L10n.text("桌面端")
        getClientKind(session) == ClientKind.MobileApp -> L10n.text("移动端")
        else -> session?.deviceType ?: L10n.text("未知设备")
    }
}

private fun resolveSessionIcon(session: SessionItem): ImageVector {
    val system = getSystemDisplayName(session)
    val clientKind = getClientKind(session)
    return when {
        clientKind == ClientKind.DesktopApp -> Icons.Outlined.DesktopWindows
        clientKind == ClientKind.MobileApp && system == "iOS" -> Icons.Outlined.PhoneIphone
        clientKind == ClientKind.MobileApp -> Icons.Outlined.PhoneAndroid
        system == "Windows" -> Icons.Outlined.LaptopWindows
        system == "macOS" -> Icons.Outlined.LaptopMac
        system == "Android" -> Icons.Outlined.PhoneAndroid
        system == "iOS" -> Icons.Outlined.PhoneIphone
        system == "ChromeOS" -> Icons.Outlined.LaptopChromebook
        system == "Linux" -> Icons.Outlined.Computer
        clientKind == ClientKind.Web -> Icons.Outlined.Language
        else -> Icons.Outlined.Computer
    }
}

private val OperationTypeOptions = listOf(FilterOption(null, L10n.text("全部操作"))) +
    listOf("LOGIN", "REGISTER", "SENSITIVE_VERIFY", "ADAPTIVE_POLICY", "CHANGE_PASSWORD", "CHANGE_EMAIL",
        "ADD_PASSKEY", "DELETE_PASSKEY", "ENABLE_TOTP", "DISABLE_TOTP", "BIND_OAUTH", "UNBIND_OAUTH", "UPDATE_PROFILE", "DELETE_ACCOUNT")
        .map { FilterOption(it, SecurityLogPresentation.operation(it)) }

private val ResultOptions = listOf(
    FilterOption(null, L10n.text("全部结果")),
    FilterOption("SUCCESS", L10n.text("成功")),
    FilterOption("FAILURE", L10n.text("失败")),
)

private data class FilterOption(
    val value: String?,
    val label: String,
)

private fun formatAbsoluteTime(value: String?): String = DisplayTime.format(value)
