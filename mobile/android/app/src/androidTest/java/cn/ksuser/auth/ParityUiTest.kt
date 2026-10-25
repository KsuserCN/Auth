package cn.ksuser.auth

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import cn.ksuser.auth.core.settings.AppPreferences
import cn.ksuser.auth.core.settings.L10n
import cn.ksuser.auth.data.authorization.AuthorizationGrantMode
import cn.ksuser.auth.data.authorization.MobileAuthorizationContext
import cn.ksuser.auth.data.authorization.PendingNativeAuthorization
import cn.ksuser.auth.data.model.UserProfile
import cn.ksuser.auth.ui.*
import cn.ksuser.auth.ui.settings.ProvideAppPreferences
import cn.ksuser.auth.ui.theme.KsuserAuthAndroidTheme
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Uses local UI state only; no production API or accounts. */
class ParityUiTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var preferences: AppPreferences
    private var language = "system"
    private var theme = "system"
    @Before fun setup() {
        preferences = AppPreferences(InstrumentationRegistry.getInstrumentation().targetContext)
        language = preferences.state.value.language
        theme = preferences.state.value.theme
        preferences.setLanguage("zh-Hans")
    }
    @After fun cleanup() { preferences.setLanguage(language); preferences.setTheme(theme) }

    @Test fun languageAndThemeChangesPreserveCurrentInput() {
        compose.setContent {
            ProvideAppPreferences(preferences) {
                KsuserAuthAndroidTheme {
                    var input by rememberSaveable { mutableStateOf("") }
                    Column {
                        Text(L10n.text("语言"))
                        OutlinedTextField(input, { input = it }, Modifier.testTag("email"))
                        Button(onClick = { preferences.setLanguage("en"); preferences.setTheme("dark") }) { Text("English") }
                    }
                }
            }
        }
        compose.onNodeWithTag("email").performTextInput("keep@example.test")
        compose.onNodeWithText("English").performClick()
        compose.onNodeWithText("Language").assertIsDisplayed()
        compose.onNodeWithTag("email").assertTextEquals("keep@example.test")
        compose.runOnIdle { assertEquals("dark", preferences.state.value.theme) }
    }

    @Test fun qrConsentDoesNotSignBrowserInWithoutExplicitSelection() {
        var selected = false
        var confirmed = false
        compose.setContent {
            KsuserAuthAndroidTheme {
                var pending by remember { mutableStateOf(request()) }
                ApplicationConsentScreen(pending, UserProfile("test-user", "Test User", null), false,
                    onConfirm = { confirmed = pending.loginWeb }, onCancel = {},
                    onOptionsChanged = { mode, ttl, loginWeb ->
                        selected = loginWeb
                        pending = pending.copy(grantMode = mode, grantTtlSeconds = ttl, loginWeb = loginWeb)
                    })
            }
        }
        compose.onNode(isToggleable()).performScrollTo().assertIsOff()
        assertFalse(selected)
        compose.onNode(isToggleable()).performClick().assertIsOn()
        compose.onNodeWithText("同意并继续").assertIsDisplayed().performClick()
        compose.runOnIdle { assertTrue(selected); assertTrue(confirmed) }
    }

    @Test fun expiredApplicationConsentCannotBeApproved() {
        var cancelled = 0
        compose.setContent { KsuserAuthAndroidTheme {
            ApplicationConsentScreen(request().copy(expiresAtMillis = 0), UserProfile("test-user", "Test", null), false,
                onConfirm = { fail("Expired consent must be disabled") }, onCancel = { cancelled++ }, onOptionsChanged = { _, _, _ -> })
        } }
        compose.onNodeWithText("同意并继续").assertIsDisplayed().assertIsNotEnabled()
        compose.onNodeWithText("关闭").assertIsDisplayed().assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(1, cancelled) }
    }

    @Test fun submittingApplicationConsentDisablesActionsAndBrowserOption() {
        compose.setContent { KsuserAuthAndroidTheme {
            ApplicationConsentScreen(request(), UserProfile("test-user", "Test", null), true,
                onConfirm = { fail("A submitting request cannot be submitted again") },
                onCancel = { fail("A submitting request cannot be cancelled") },
                onOptionsChanged = { _, _, _ -> fail("Submitting options cannot change") })
        } }
        compose.onNodeWithText("正在提交…").assertIsDisplayed().assertIsNotEnabled()
        compose.onNodeWithText("拒绝授权").assertIsDisplayed().assertIsNotEnabled()
        compose.onNodeWithContentDescription("关闭").assertIsNotEnabled()
        compose.onNode(isToggleable()).assertIsOff().assertIsNotEnabled()
    }

    @Test fun applicationConsentRequiresSignedInAccount() {
        compose.setContent { KsuserAuthAndroidTheme {
            ApplicationConsentScreen(request(), null, false,
                onConfirm = { fail("An anonymous request cannot be approved") }, onCancel = {}, onOptionsChanged = { _, _, _ -> })
        } }
        compose.onNodeWithText("同意并继续").assertIsDisplayed().assertIsNotEnabled()
        compose.onNodeWithText("拒绝授权").assertIsEnabled()
    }

    @Test fun nativeConsentShowsAppReturnAndNeverOffersBrowserSignIn() {
        compose.setContent { KsuserAuthAndroidTheme {
            val pending = request().let { it.copy(authorization = it.authorization!!.copy(qr = false, nativeAuthorization = true)) }
            ApplicationConsentScreen(pending, UserProfile("test-user", "Test", null), false,
                onConfirm = {}, onCancel = {}, onOptionsChanged = { _, _, _ -> })
        } }
        compose.onNodeWithText("应用回调").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("https://example.test/callback").assertIsDisplayed()
        compose.onNodeWithText("完成后将返回调用应用").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("同时登录通行证网页端").assertDoesNotExist()
        compose.onNode(isToggleable()).assertDoesNotExist()
    }

    @Test fun completedNativeConsentOnlyOffersReturnRetry() {
        var returns = 0
        compose.setContent { KsuserAuthAndroidTheme {
            NativeAuthorizationReturnScreen(PendingNativeAuthorization("a".repeat(32), System.currentTimeMillis() + 60_000,
                "test-user", "Local test app", "https://example.test/callback",
                "https://example.test/callback?code=once&state=${"s".repeat(32)}", true),
                onReturn = { returns++ }, onClose = {}, errorMessage = "App Link unavailable")
        } }
        compose.onNodeWithText("App Link unavailable").assertIsDisplayed()
        compose.onNodeWithText("同意并继续").assertDoesNotExist()
        compose.onNodeWithText("返回应用继续").performClick().performClick()
        compose.runOnIdle { assertEquals(2, returns) }
    }

    private fun request() = PendingQrConfirmation(
        type = QrConfirmationType.APPROVE_APPLICATION,
        code = "test-code",
        expiresAtMillis = System.currentTimeMillis() + 120_000,
        authorization = MobileAuthorizationContext(
            ticket = "a".repeat(32), appName = "Local test app", redirectUri = "https://example.test/callback",
            requestedScopes = listOf("openid", "profile"), expiresInSeconds = 120, qr = true,
        ),
        grantMode = AuthorizationGrantMode.PERSISTENT,
    )
}
