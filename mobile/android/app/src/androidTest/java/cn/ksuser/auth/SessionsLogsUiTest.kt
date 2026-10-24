package cn.ksuser.auth

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import cn.ksuser.auth.core.settings.L10n
import cn.ksuser.auth.data.model.SensitiveLogItem
import cn.ksuser.auth.data.model.SessionItem
import cn.ksuser.auth.ui.LogFilterSheet
import cn.ksuser.auth.ui.LogFilterValues
import cn.ksuser.auth.ui.LogsContent
import cn.ksuser.auth.ui.SessionRow
import cn.ksuser.auth.ui.SessionsContent
import cn.ksuser.auth.ui.theme.KsuserAuthAndroidTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** All fixtures are local. These tests never initialize repositories or operate on real accounts. */
class SessionsLogsUiTest {
    @get:Rule val compose = createComposeRule()
    private var previousLanguage = "zh-Hans"

    @Before fun useChinese() { previousLanguage = L10n.language; L10n.language = "zh-Hans" }
    @After fun restoreLanguage() { L10n.language = previousLanguage }

    @Test fun currentDeviceHasNoRevokeAction_otherDeviceRequestsConfirmation() {
        var session by mutableStateOf(sessionUiFixture(current = true))
        var requestedId: Long? = null
        compose.setContent {
            NarrowTheme { SessionRow(session, onRequestRevoke = { requestedId = it.id }) }
        }
        compose.onNodeWithTag("session-1").assertIsDisplayed()
        compose.onNodeWithContentDescription("撤销会话").assertDoesNotExist()
        compose.runOnIdle { assertEquals(null, requestedId); session = session.copy(current = false) }
        compose.onNodeWithContentDescription("撤销会话").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1L, requestedId) }
    }

    @Test fun sessionsAreGrouped_andRefreshFailureKeepsExistingDevices() {
        var loading by mutableStateOf(true)
        var error by mutableStateOf<String?>(null)
        val devices = listOf(sessionUiFixture(), sessionUiFixture(id = 2, current = false))
        compose.setContent {
            NarrowTheme {
                SessionsContent(devices, loading, error, onRefresh = {}, onRequestRevoke = {}, onLogoutAll = {})
            }
        }
        compose.onNodeWithTag("sessions-list").performScrollToNode(hasText("当前设备"))
        compose.onNodeWithText("当前设备").assertIsDisplayed()
        compose.onNodeWithText("暂无设备").assertDoesNotExist()
        compose.runOnIdle { loading = false; error = "Local refresh failure" }
        compose.onNodeWithTag("sessions-list").performScrollToNode(hasText("Local refresh failure"))
        compose.onNodeWithText("Local refresh failure").assertIsDisplayed()
        compose.onNodeWithTag("sessions-list").performScrollToNode(hasText("其他设备"))
        compose.onNodeWithText("其他设备").assertIsDisplayed()
        compose.onNodeWithTag("sessions-list").performScrollToNode(hasTestTag("session-2"))
        compose.onNodeWithTag("session-2").assertIsDisplayed()
        compose.onNodeWithText("暂无设备").assertDoesNotExist()
    }

    @Test fun cancellingFilterDiscardsDraft_applyEmitsChangedValues_andResetIsExplicit() {
        val original = LogFilterValues(result = "SUCCESS", startDate = "2026-10-01", endDate = "2026-10-20")
        var applied by mutableStateOf(original)
        var visible by mutableStateOf(false)
        var applications = 0
        var resets = 0
        var dismissals = 0
        compose.setContent {
            NarrowTheme {
                Column {
                    Button(onClick = { visible = true }, modifier = Modifier.testTag("test-open-filters")) { Text("Open filters") }
                    if (visible) LogFilterSheet(
                        initial = applied,
                        onDismiss = { dismissals++; visible = false },
                        onApply = { applied = it; applications++; visible = false },
                        onReset = { applied = LogFilterValues(); resets++; visible = false },
                    )
                }
            }
        }
        compose.onNodeWithTag("test-open-filters").performClick()
        chooseFailure()
        compose.runOnIdle { assertEquals(original, applied); assertEquals(0, applications) }
        compose.onNodeWithTag("log-cancel-filters").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(original, applied); assertEquals(1, dismissals); assertEquals(0, applications) }

        compose.onNodeWithTag("test-open-filters").performClick()
        compose.onNodeWithTag("log-result").performScrollTo().assertTextContains("成功")
        chooseFailure()
        compose.onNodeWithTag("log-start-date-clear").performScrollTo().performClick()
        compose.onNodeWithTag("log-apply-filters").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(original.copy(result = "FAILURE", startDate = ""), applied)
            assertEquals(1, applications)
        }

        compose.onNodeWithTag("test-open-filters").performClick()
        compose.onNodeWithTag("log-reset-filters").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(LogFilterValues(), applied); assertEquals(1, resets); assertEquals(1, applications) }
    }

    @Test fun invalidDateRangeCannotApply_butEitherDateCanBeCleared() {
        var result: LogFilterValues? = null
        compose.setContent {
            NarrowTheme {
                LogFilterSheet(
                    initial = LogFilterValues(startDate = "2026-10-20", endDate = "2026-10-01"),
                    onDismiss = {}, onApply = { result = it }, onReset = {},
                )
            }
        }
        compose.onNodeWithTag("log-apply-filters").performScrollTo().performClick()
        compose.onNodeWithText("请输入有效的日期范围").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { assertEquals(null, result) }
        compose.onNodeWithTag("log-end-date-clear").performScrollTo().performClick()
        compose.onNodeWithTag("log-apply-filters").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(LogFilterValues(startDate = "2026-10-20"), result) }
    }

    @Test fun logRefreshAndFailureKeepRecords_andDetailsRemainActionable() {
        var loading by mutableStateOf(true)
        var error by mutableStateOf<String?>(null)
        var openedId: Long? = null
        var retries = 0
        compose.setContent {
            NarrowTheme {
                LogsContent(
                    logs = listOf(logUiFixture()), page = 1, totalPages = 1, total = 1,
                    filters = LogFilterValues(), loading = loading, error = error,
                    onRefresh = { retries++ }, onOpenFilters = {}, onOpenLog = { openedId = it.id },
                    onPreviousPage = {}, onNextPage = {}, onClearFilters = {},
                )
            }
        }
        compose.onNodeWithTag("logs-list").performScrollToNode(hasTestTag("log-1"))
        compose.onNodeWithTag("log-1").assertIsDisplayed()
        compose.onNodeWithText("暂无记录").assertDoesNotExist()
        compose.runOnIdle { loading = false; error = "Local log failure" }
        compose.onNodeWithTag("logs-list").performScrollToNode(hasText("Local log failure"))
        compose.onNodeWithText("Local log failure").assertIsDisplayed()
        compose.onNodeWithText("重试").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, retries) }
        compose.onNodeWithTag("logs-list").performScrollToNode(hasTestTag("log-1"))
        compose.onNodeWithTag("log-1").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1L, openedId) }
        compose.onNodeWithText("暂无记录").assertDoesNotExist()
    }

    @Test fun emptyStateAppearsOnlyAfterSuccessfulLoad_andCanClearActiveFilters() {
        var loading by mutableStateOf(true)
        var error by mutableStateOf<String?>(null)
        var filters by mutableStateOf(LogFilterValues(result = "FAILURE"))
        var clearCount = 0
        compose.setContent {
            NarrowTheme {
                LogsContent(
                    logs = emptyList(), page = 1, totalPages = 0, total = 0,
                    filters = filters, loading = loading, error = error,
                    onRefresh = {}, onOpenFilters = {}, onOpenLog = {}, onPreviousPage = {}, onNextPage = {},
                    onClearFilters = { filters = LogFilterValues(); clearCount++ },
                )
            }
        }
        compose.onAllNodesWithTag("list-skeleton").onFirst().assertIsDisplayed()
        compose.onNodeWithText("暂无记录").assertDoesNotExist()
        compose.runOnIdle { loading = false; error = "Local initial failure" }
        compose.onNodeWithText("Local initial failure").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("暂无记录").assertDoesNotExist()
        compose.runOnIdle { error = null }
        compose.onNodeWithText("暂无记录").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("清除筛选").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, clearCount) }
        compose.onNodeWithText("清除筛选").assertDoesNotExist()
        compose.onNodeWithTag("log-applied-filters").performScrollTo().assertTextContains("全部结果", substring = true)
    }

    private fun chooseFailure() {
        compose.onNodeWithTag("log-result").performScrollTo().performClick()
        compose.onNodeWithText("失败").performClick()
    }
}

@Composable
private fun NarrowTheme(content: @Composable () -> Unit) {
    KsuserAuthAndroidTheme(darkTheme = false) {
        Surface(Modifier.fillMaxSize()) {
            Box(Modifier.widthIn(max = 360.dp).fillMaxSize()) { content() }
        }
    }
}

internal fun sessionUiFixture(id: Long = 1, current: Boolean = true) = SessionItem(
    id = id, ipAddress = "192.0.2.$id", ipLocation = "上海", userAgent = "KsuserAuthMobile Android",
    browser = null, deviceType = "Android", createdAt = "2026-10-20T08:00:00Z", lastSeenAt = "2026-10-20T12:00:00Z",
    expiresAt = "2026-11-20T08:00:00Z", revokedAt = null, online = current, current = current,
)

internal fun logUiFixture(id: Long = 1) = SensitiveLogItem(
    id = id, operationType = "LOGIN", loginMethod = "PASSWORD", ipAddress = "192.0.2.$id", ipLocation = "上海",
    deviceType = "Android", browser = "Chrome", result = "SUCCESS", createdAt = "2026-10-20T12:00:00Z",
)
