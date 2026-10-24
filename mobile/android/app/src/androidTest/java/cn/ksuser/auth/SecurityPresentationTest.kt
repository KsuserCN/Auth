package cn.ksuser.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import cn.ksuser.auth.core.settings.L10n
import cn.ksuser.auth.data.model.AdaptiveAuthStatus
import cn.ksuser.auth.data.model.ApiResultDescriptor
import cn.ksuser.auth.ui.AdaptiveSecurityStatusContent
import cn.ksuser.auth.ui.theme.KsuserAuthAndroidTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Exercises security presentation without repositories, accounts, or network requests. */
class SecurityPresentationTest {
    @get:Rule
    val compose = createComposeRule()

    private var previousLanguage = "zh-Hans"

    @Before
    fun useChinese() {
        previousLanguage = L10n.language
        L10n.language = "zh-Hans"
    }

    @After
    fun restoreLanguage() {
        L10n.language = previousLanguage
    }

    @Test
    fun verifiedLowRiskSessionDoesNotRequestAnotherVerification() {
        showStatus(
            AdaptiveAuthStatus(
                trusted = true,
                sensitiveVerified = true,
                sensitiveVerificationRemainingSeconds = 600,
            ),
        )

        compose.onNodeWithText("当前会话可信度稳定").assertIsDisplayed()
        compose.onNodeWithText("低风险 · 低风险放行").assertIsDisplayed()
        compose.onNodeWithText("当前会话风险较低，可继续使用").assertIsDisplayed()
        compose.onNodeWithText("立即验证").assertDoesNotExist()
        compose.onNodeWithText("重新登录").assertDoesNotExist()
        compose.onNodeWithText("策略版本").assertDoesNotExist()
    }

    @Test
    fun stepUpActionStaysAvailableWhileTechnicalDetailsAreCollapsed() {
        var verified = 0
        var refreshed = 0
        showStatus(
            AdaptiveAuthStatus(
                riskScore = 58,
                riskLevel = "medium",
                policyDecision = "STEP_UP",
                policyVersion = "test-policy-3",
                requiresStepUp = true,
                currentLocation = "上海",
                deviceType = "Android",
                recommendedActionCode = "ADAPTIVE_ACTION_STEP_UP",
                reasonCodes = listOf(ApiResultDescriptor("ADAPTIVE_REASON_IP_CHANGED")),
            ),
            onVerify = { verified++ },
            onRefresh = { refreshed++ },
        )

        compose.onNodeWithText("建议立即补做验证").assertIsDisplayed()
        compose.onNodeWithText("中风险 · 强制补验").assertIsDisplayed()
        compose.onNodeWithText("风险分").assertDoesNotExist()
        compose.onNodeWithText("风险信号").assertDoesNotExist()
        compose.onNodeWithText("立即验证").performScrollTo().assertIsEnabled().performClick()
        compose.onNodeWithText("刷新").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(1, verified)
            assertEquals(1, refreshed)
        }

        compose.onNodeWithText("查看风险详情").performScrollTo().performClick()
        compose.onNodeWithText("风险分").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("58").assertIsDisplayed()
        compose.onNodeWithText("test-policy-3").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("上海 / Android").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("• 当前 IP 与会话建立时不一致").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("立即验证").performScrollTo().assertIsEnabled()

        compose.onNodeWithText("收起风险详情").performScrollTo().performClick()
        compose.onNodeWithText("风险分").assertDoesNotExist()
        compose.onNodeWithText("风险信号").assertDoesNotExist()
        compose.onNodeWithText("立即验证").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun frozenSessionKeepsAlertAndReloginActionOutsideTechnicalDetails() {
        var relogins = 0
        showStatus(
            AdaptiveAuthStatus(
                riskScore = 95,
                riskLevel = "high",
                policyDecision = "FREEZE",
                sessionFrozen = true,
                multiEndpointAlert = true,
                alertLevel = "high",
                alertTitleCode = "ADAPTIVE_ALERT_FROZEN_TITLE",
                alertMessageCode = "ADAPTIVE_ALERT_FROZEN_MESSAGE",
                alertRemainingSeconds = 120,
                recommendedActionCode = "ADAPTIVE_ACTION_FROZEN",
            ),
            onRelogin = { relogins++ },
        )

        compose.onNodeWithText("高风险 · 冻结会话").assertIsDisplayed()
        compose.onNodeWithText("当前会话风险极高，系统已冻结会话，请重新登录并完成验证")
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("当前会话已被冻结，请重新登录并完成安全验证")
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("告警剩余有效期 120 秒").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("风险分").assertDoesNotExist()
        compose.onNodeWithText("立即验证").assertDoesNotExist()
        compose.onNodeWithText("重新登录").performScrollTo().assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(1, relogins) }
    }

    @Test
    fun initialLoadingDoesNotDisplayAnInventedRiskDecision() {
        showStatus(status = null, loading = true)

        compose.onNodeWithText("正在读取状态").assertIsDisplayed()
        compose.onNodeWithText("当前会话可信度稳定").assertDoesNotExist()
        compose.onNodeWithText("查看风险详情").assertDoesNotExist()
        compose.onNodeWithText("立即验证").assertDoesNotExist()
    }

    @Test
    fun unavailableStatusCanBeRetriedWithoutShowingVerificationActions() {
        var refreshed = 0
        showStatus(status = null, onRefresh = { refreshed++ })

        compose.onNodeWithText("暂时无法读取会话风险状态").assertIsDisplayed()
        compose.onNodeWithText("立即验证").assertDoesNotExist()
        compose.onNodeWithText("重新登录").assertDoesNotExist()
        compose.onNodeWithText("刷新").performClick()
        compose.runOnIdle { assertEquals(1, refreshed) }
    }

    private fun showStatus(
        status: AdaptiveAuthStatus?,
        loading: Boolean = false,
        onVerify: () -> Unit = {},
        onRelogin: () -> Unit = {},
        onRefresh: () -> Unit = {},
    ) {
        compose.setContent {
            KsuserAuthAndroidTheme {
                var expanded by rememberSaveable { mutableStateOf(false) }
                Column(
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    AdaptiveSecurityStatusContent(
                        status = status,
                        loading = loading,
                        detailsExpanded = expanded,
                        onToggleDetails = { expanded = !expanded },
                        onVerify = onVerify,
                        onRelogin = onRelogin,
                        onRefresh = onRefresh,
                    )
                }
            }
        }
    }
}
