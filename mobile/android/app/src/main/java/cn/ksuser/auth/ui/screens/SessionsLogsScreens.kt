package cn.ksuser.auth.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DesktopWindows
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.LaptopChromebook
import androidx.compose.material.icons.outlined.LaptopMac
import androidx.compose.material.icons.outlined.LaptopWindows
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.PhoneIphone
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import cn.ksuser.auth.core.security.SecurityLogPresentation
import cn.ksuser.auth.core.settings.DisplayTime
import cn.ksuser.auth.core.settings.L10n
import cn.ksuser.auth.data.AppContainer
import cn.ksuser.auth.data.model.SensitiveLogItem
import cn.ksuser.auth.data.model.SessionItem
import cn.ksuser.auth.ui.components.AppPagePadding
import cn.ksuser.auth.ui.components.AppRadius
import cn.ksuser.auth.ui.components.AppSpacing
import cn.ksuser.auth.ui.components.SectionCard
import java.time.LocalDate
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

    SessionsContent(
        sessions = sessions,
        loading = loading,
        error = sessionError,
        onRefresh = { scope.launch { reload() } },
        onRequestRevoke = { pendingRevokeSession = it },
        onLogoutAll = onLogoutAll,
    )

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

    val appliedFilters = LogFilterValues(appliedOperationType, appliedResult, appliedStartDate.orEmpty(), appliedEndDate.orEmpty())
    fun clearFilters() {
        page = 1
        appliedOperationType = null
        appliedResult = null
        appliedStartDate = null
        appliedEndDate = null
        queryEpoch++
    }

    LogsContent(
        logs = logs,
        page = page,
        totalPages = totalPages,
        total = total,
        filters = appliedFilters,
        loading = busy,
        error = loadError,
        onRefresh = { scope.launch { reload() } },
        onOpenFilters = { filterExpanded = true },
        onOpenLog = { log -> scope.launch { openDetail(log.id) } },
        onPreviousPage = { if (page > 1) page-- },
        onNextPage = { if (page < totalPages) page++ },
        onClearFilters = ::clearFilters,
    )
    if (filterExpanded) {
        LogFilterSheet(
            initial = appliedFilters,
            onDismiss = { filterExpanded = false },
            onApply = { filters ->
                page = 1
                appliedOperationType = filters.operationType
                appliedResult = filters.result
                appliedStartDate = filters.startDate.ifBlank { null }
                appliedEndDate = filters.endDate.ifBlank { null }
                queryEpoch++
                filterExpanded = false
            },
            onReset = { clearFilters(); filterExpanded = false },
        )
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

/** Presentation only: repository calls and confirmation ownership stay in SessionsScreen. */
@Composable
internal fun SessionsContent(
    sessions: List<SessionItem>,
    loading: Boolean,
    error: String?,
    onRefresh: () -> Unit,
    onRequestRevoke: (SessionItem) -> Unit,
    onLogoutAll: () -> Unit,
) {
    val current = sessions.filter { it.current }
    val others = sessions.filterNot { it.current }
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("sessions-list"),
        contentPadding = PaddingValues(AppPagePadding),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.S12),
    ) {
        item {
            ListPageHeader(L10n.text("设备与登录"), L10n.text("查看和管理您已连接的设备"), loading, onRefresh)
        }
        if (loading && sessions.isNotEmpty()) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        if (error != null) item { ListErrorMessage(error, onRefresh) }
        if (loading && sessions.isEmpty()) items(3) { ListSkeleton() }
        if (!loading && error == null && sessions.isEmpty()) item {
            ListEmptyMessage(L10n.text("暂无设备"), L10n.text("当前没有活跃会话记录"))
        }
        if (current.isNotEmpty()) {
            item { ListSectionLabel(L10n.text("当前设备")) }
            items(current, key = { "current:${it.id}" }) { session -> SessionRow(session, onRequestRevoke) }
        }
        if (others.isNotEmpty()) {
            item { ListSectionLabel(L10n.text("其他设备")) }
            items(others, key = { "other:${it.id}" }) { session -> SessionRow(session, onRequestRevoke) }
        }
        item {
            Column(
                Modifier.fillMaxWidth().padding(top = AppSpacing.S12),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.S12),
            ) {
                OutlinedButton(onClick = onLogoutAll, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                    Text(L10n.text("退出所有设备"), color = MaterialTheme.colorScheme.error)
                }
                Text(
                    L10n.text("从所有设备退出登录将撤销所有会话（包括当前设备），需要重新登录。"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
internal fun SessionRow(session: SessionItem, onRequestRevoke: (SessionItem) -> Unit) {
    SectionCard(Modifier.fillMaxWidth().testTag("session-${session.id}")) {
        Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.S12), verticalAlignment = Alignment.Top) {
            SessionLogo(session)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(getClientDisplayName(session), style = MaterialTheme.typography.titleMedium)
                Text(
                    "${getSystemDisplayName(session)} · ${session.ipLocation ?: L10n.text("未知位置")}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(session.ipAddress, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(AppSpacing.S8), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (session.current) StateLabel(L10n.text("当前"), positive = true)
                    StateLabel(if (session.online) L10n.text("在线") else L10n.text("离线"), positive = session.online)
                }
            }
            if (!session.current) {
                IconButton(onClick = { onRequestRevoke(session) }, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Outlined.DeleteOutline, contentDescription = L10n.text("撤销会话"), tint = MaterialTheme.colorScheme.error)
                }
            }
        }
        Text(
            L10n.format("最近活动：{0}", DisplayTime.format(session.lastSeenAt)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SessionLogo(session: SessionItem) {
    Box(
        Modifier.size(48.dp).clip(RoundedCornerShape(AppRadius.R12)).background(MaterialTheme.colorScheme.surfaceContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(resolveSessionIcon(session), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

internal data class LogFilterValues(
    val operationType: String? = null,
    val result: String? = null,
    val startDate: String = "",
    val endDate: String = "",
) {
    val isActive: Boolean get() = operationType != null || result != null || startDate.isNotBlank() || endDate.isNotBlank()
}

/** Lists remain visible during refresh and recoverable failures. */
@Composable
internal fun LogsContent(
    logs: List<SensitiveLogItem>,
    page: Int,
    totalPages: Int,
    total: Long,
    filters: LogFilterValues,
    loading: Boolean,
    error: String?,
    onRefresh: () -> Unit,
    onOpenFilters: () -> Unit,
    onOpenLog: (SensitiveLogItem) -> Unit,
    onPreviousPage: () -> Unit,
    onNextPage: () -> Unit,
    onClearFilters: () -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize().testTag("logs-list"),
        contentPadding = PaddingValues(AppPagePadding),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.S12),
    ) {
        item { ListPageHeader(L10n.text("近期敏感操作"), L10n.format("共 {0} 条", total), loading, onRefresh) }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.S8)) {
                OutlinedButton(onClick = onOpenFilters, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("log-open-filters")) {
                    Icon(Icons.Outlined.FilterList, contentDescription = null, modifier = Modifier.padding(end = AppSpacing.S8))
                    Text(L10n.text("筛选条件"))
                }
                Text(
                    logFilterSummary(filters),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("log-applied-filters"),
                )
            }
        }
        if (loading && logs.isNotEmpty()) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        if (error != null) item { ListErrorMessage(error, onRefresh) }
        if (loading && logs.isEmpty()) items(3) { ListSkeleton() }
        if (!loading && error == null && logs.isEmpty()) item {
            ListEmptyMessage(L10n.text("暂无记录"), L10n.text("当前筛选条件下没有匹配的敏感操作记录"))
            if (filters.isActive) TextButton(onClick = onClearFilters, modifier = Modifier.heightIn(min = 48.dp)) { Text(L10n.text("清除筛选")) }
        }
        logs.groupBy { DisplayTime.day(it.createdAt) }.forEach { (day, entries) ->
            item(key = "day:$day") { ListSectionLabel(day) }
            items(entries, key = { "log:${it.id}" }) { log -> LogRow(log, onOpenLog) }
        }
        if (logs.isNotEmpty() || totalPages > 1) item {
            FlowRow(
                Modifier.fillMaxWidth().padding(top = AppSpacing.S8),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.S12),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.S8),
            ) {
                OutlinedButton(onClick = onPreviousPage, enabled = page > 1 && !loading, modifier = Modifier.heightIn(min = 48.dp)) { Text(L10n.text("上一页")) }
                OutlinedButton(onClick = onNextPage, enabled = page < totalPages && !loading, modifier = Modifier.heightIn(min = 48.dp)) { Text(L10n.text("下一页")) }
                Text(
                    L10n.format("第 {0} / {1} 页", page, totalPages.coerceAtLeast(1)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterVertically),
                )
            }
        }
    }
}

@Composable
internal fun LogRow(log: SensitiveLogItem, onOpenLog: (SensitiveLogItem) -> Unit) {
    SectionCard(Modifier.fillMaxWidth().testTag("log-${log.id}").clickable { onOpenLog(log) }) {
        Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.S12), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AppSpacing.S8)) {
                Text(SecurityLogPresentation.title(log), style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(AppSpacing.S8), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    StateLabel(
                        if (log.result.equals("SUCCESS", true)) {
                            if (log.operationType == "ADAPTIVE_POLICY") L10n.text("已执行") else L10n.text("成功")
                        } else L10n.text("失败"),
                        positive = log.result.equals("SUCCESS", true),
                        failed = !log.result.equals("SUCCESS", true),
                    )
                    Text(DisplayTime.format(log.createdAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(log.ipLocation ?: log.ipAddress, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = L10n.text("安全日志详情"), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
    }
}

private fun logFilterSummary(filters: LogFilterValues): String {
    val operation = operationTypeOptions().firstOrNull { it.value == filters.operationType }?.label ?: L10n.text("全部操作")
    val result = resultOptions().firstOrNull { it.value == filters.result }?.label ?: L10n.text("全部结果")
    val dates = listOfNotNull(filters.startDate.takeIf { it.isNotBlank() }?.let { "${L10n.text("开始日期")} $it" }, filters.endDate.takeIf { it.isNotBlank() }?.let { "${L10n.text("结束日期")} $it" })
    return (listOf(operation, result) + dates).joinToString(" · ")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LogFilterSheet(
    initial: LogFilterValues,
    onDismiss: () -> Unit,
    onApply: (LogFilterValues) -> Unit,
    onReset: () -> Unit,
) {
    var operation by rememberSaveable(initial) { mutableStateOf(initial.operationType) }
    var result by rememberSaveable(initial) { mutableStateOf(initial.result) }
    var start by rememberSaveable(initial) { mutableStateOf(initial.startDate) }
    var end by rememberSaveable(initial) { mutableStateOf(initial.endDate) }
    var error by remember { mutableStateOf<String?>(null) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        LogFilterPanel(
            values = LogFilterValues(operation, result, start, end),
            error = error,
            onChange = { operation = it.operationType; result = it.result; start = it.startDate; end = it.endDate; error = null },
            onApply = {
                if (start.isNotBlank() && end.isNotBlank() && start > end) error = L10n.text("请输入有效的日期范围")
                else onApply(LogFilterValues(operation, result, start, end))
            },
            onReset = onReset,
            onCancel = onDismiss,
        )
    }
}

@Composable
internal fun LogFilterPanel(
    values: LogFilterValues,
    error: String?,
    onChange: (LogFilterValues) -> Unit,
    onApply: () -> Unit,
    onReset: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = AppPagePadding).navigationBarsPadding().padding(bottom = AppSpacing.S24),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.S16),
    ) {
        Text(L10n.text("筛选条件"), style = MaterialTheme.typography.titleLarge)
        FilterSelector(L10n.text("操作类型"), values.operationType, operationTypeOptions(), "log-operation", { onChange(values.copy(operationType = it)) })
        FilterSelector(L10n.text("结果"), values.result, resultOptions(), "log-result", { onChange(values.copy(result = it)) })
        LogDateField(L10n.text("开始日期"), values.startDate, "log-start-date", { onChange(values.copy(startDate = it)) })
        LogDateField(L10n.text("结束日期"), values.endDate, "log-end-date", { onChange(values.copy(endDate = it)) })
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
        Button(onClick = onApply, colors = cn.ksuser.auth.ui.components.appPrimaryButtonColors(), modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("log-apply-filters")) { Text(L10n.text("查询")) }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(AppSpacing.S12), verticalArrangement = Arrangement.spacedBy(AppSpacing.S8)) {
            TextButton(onClick = onReset, modifier = Modifier.heightIn(min = 48.dp).testTag("log-reset-filters")) { Text(L10n.text("重置")) }
            TextButton(onClick = onCancel, modifier = Modifier.heightIn(min = 48.dp).testTag("log-cancel-filters")) { Text(L10n.text("取消")) }
        }
    }
}

@Composable
private fun FilterSelector(label: String, selected: String?, options: List<FilterOption>, tag: String, onChange: (String?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.S8)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag(tag)) {
                Text(options.firstOrNull { it.value == selected }?.label.orEmpty(), modifier = Modifier.weight(1f))
                Icon(Icons.Outlined.ExpandMore, contentDescription = null)
            }
            DropdownMenu(expanded, onDismissRequest = { expanded = false }, modifier = Modifier.heightIn(max = 320.dp)) {
                options.forEach { option -> DropdownMenuItem(text = { Text(option.label) }, onClick = { onChange(option.value); expanded = false }) }
            }
        }
    }
}

@Composable
private fun LogDateField(label: String, value: String, tag: String, onChange: (String) -> Unit) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.S8)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppSpacing.S8)) {
            OutlinedButton(onClick = {
                val date = runCatching { LocalDate.parse(value) }.getOrElse { LocalDate.now() }
                DatePickerDialog(context, { _, year, month, day -> onChange(LocalDate.of(year, month + 1, day).toString()) }, date.year, date.monthValue - 1, date.dayOfMonth).show()
            }, modifier = Modifier.weight(1f).heightIn(min = 52.dp).testTag(tag)) {
                Icon(Icons.Outlined.CalendarMonth, contentDescription = null, modifier = Modifier.padding(end = AppSpacing.S8))
                Text(value.ifBlank { L10n.text("不限日期") })
            }
            if (value.isNotBlank()) IconButton(onClick = { onChange("") }, modifier = Modifier.size(48.dp).testTag("$tag-clear")) {
                Icon(Icons.Outlined.Close, contentDescription = L10n.format("清除{0}", label))
            }
        }
    }
}

@Composable
private fun ListPageHeader(title: String, subtitle: String, loading: Boolean, onRefresh: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AppSpacing.S12), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = onRefresh, enabled = !loading, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Outlined.Refresh, contentDescription = L10n.text("刷新"))
        }
    }
}

@Composable
private fun ListSectionLabel(label: String) {
    Text(label, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = AppSpacing.S12))
}

@Composable
private fun StateLabel(text: String, positive: Boolean, failed: Boolean = false) {
    val color = when { failed -> MaterialTheme.colorScheme.error; positive -> MaterialTheme.colorScheme.tertiary; else -> MaterialTheme.colorScheme.onSurfaceVariant }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(when { failed -> Icons.Outlined.ErrorOutline; positive -> Icons.Outlined.CheckCircle; else -> Icons.Outlined.RadioButtonUnchecked }, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        Text(text, color = color, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ListErrorMessage(message: String, onRetry: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = AppSpacing.S8), verticalArrangement = Arrangement.spacedBy(AppSpacing.S8)) {
        Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.S8), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        }
        TextButton(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp)) { Text(L10n.text("重试")) }
    }
}

@Composable
private fun ListEmptyMessage(title: String, subtitle: String) {
    SectionCard(Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ListSkeleton() {
    SectionCard(Modifier.fillMaxWidth().testTag("list-skeleton")) {
        listOf(0.7f, 0.9f, 0.45f).forEach { fraction ->
            Box(Modifier.fillMaxWidth(fraction).height(16.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest))
        }
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

private fun operationTypeOptions() = listOf(FilterOption(null, L10n.text("全部操作"))) +
    listOf("LOGIN", "REGISTER", "SENSITIVE_VERIFY", "ADAPTIVE_POLICY", "CHANGE_PASSWORD", "CHANGE_EMAIL",
        "ADD_PASSKEY", "DELETE_PASSKEY", "ENABLE_TOTP", "DISABLE_TOTP", "BIND_OAUTH", "UNBIND_OAUTH", "UPDATE_PROFILE", "DELETE_ACCOUNT")
        .map { FilterOption(it, SecurityLogPresentation.operation(it)) }

private fun resultOptions() = listOf(
    FilterOption(null, L10n.text("全部结果")),
    FilterOption("SUCCESS", L10n.text("成功")),
    FilterOption("FAILURE", L10n.text("失败")),
)

private data class FilterOption(val value: String?, val label: String)
