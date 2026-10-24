package cn.ksuser.auth

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import cn.ksuser.auth.data.model.UserProfile
import cn.ksuser.auth.data.model.UserSettings
import cn.ksuser.auth.ui.HomeScreen
import cn.ksuser.auth.ui.MainDestination
import cn.ksuser.auth.ui.MainNavigationFrame
import cn.ksuser.auth.ui.OverviewCard
import cn.ksuser.auth.ui.theme.KsuserAuthAndroidTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Before
import org.junit.After
import cn.ksuser.auth.core.settings.L10n
import org.junit.Test

class UiSmokeTest {

    private var previousLanguage = "zh-Hans"
    @Before fun useChinese() { previousLanguage = L10n.language; L10n.language = "zh-Hans" }
    @After fun restoreLanguage() { L10n.language = previousLanguage }

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun homeScreen_rendersCoreSections() {
        composeRule.setContent {
            KsuserAuthAndroidTheme {
                HomeScreen(
                    user = UserProfile(
                        uuid = "u-1",
                        username = "tester",
                        email = "tester@example.com",
                        settings = UserSettings(
                            mfaEnabled = true,
                            preferredMfaMethod = "totp",
                            preferredSensitiveMethod = "password",
                        ),
                    ),
                    onRefresh = {},
                    onOpenProfile = {},
                    onOpenSecurity = {},
                    onOpenSessions = {},
                )
            }
        }

        composeRule.onNodeWithText("tester@example.com").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("刷新状态").assertIsDisplayed()
        composeRule.onNodeWithText("前往安全设置").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun homeSettingsShortcuts_areReachableOnCompactScreenWithLargeText() {
        var opened = 0
        val displayMetrics = InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics
        val compactDensity = displayMetrics.widthPixels / 360f
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(compactDensity, 1.3f)) {
                KsuserAuthAndroidTheme {
                    MainNavigationFrame(
                        currentRoute = MainDestination.HOME.route,
                        snackbarHostState = remember { SnackbarHostState() },
                        onBack = {},
                        onScan = {},
                        onNavigate = {},
                        onOpenSettings = { opened++ },
                    ) { padding ->
                        Box(Modifier.fillMaxSize().padding(padding)) {
                            HomeScreen(
                                user = UserProfile("settings-test", "测试账号", "settings@example.test", settings = UserSettings(mfaEnabled = true)),
                                onRefresh = {},
                                onOpenProfile = {},
                                onOpenSecurity = {},
                                onOpenSessions = {},
                                onOpenSettings = { opened++ },
                            )
                        }
                    }
                }
            }
        }

        composeRule.onNodeWithTag("open_settings").assertIsDisplayed().performClick()
        assertEquals(1, opened)
        composeRule.onNodeWithTag("home_settings").performScrollTo().assertIsDisplayed().performClick()
        assertEquals(2, opened)
    }

    @Test
    fun overviewCard_actionCallbackInvoked() {
        var clicked = 0
        composeRule.setContent {
            KsuserAuthAndroidTheme {
                OverviewCard(
                    title = "Title",
                    subtitle = "Sub",
                    body = "Body",
                    actionLabel = "执行操作",
                    onAction = { clicked++ },
                )
            }
        }

        composeRule.onNodeWithText("执行操作").performClick()
        assertEquals(1, clicked)
    }
}
