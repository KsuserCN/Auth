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
        compose.onNodeWithText("同意并继续").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(selected); assertTrue(confirmed) }
    }

    @Test fun expiredApplicationConsentCannotBeApproved() {
        compose.setContent { KsuserAuthAndroidTheme {
            ApplicationConsentScreen(request().copy(expiresAtMillis = 0), UserProfile("test-user", "Test", null), false,
                onConfirm = { fail("Expired consent must be disabled") }, onCancel = {}, onOptionsChanged = { _, _, _ -> })
        } }
        compose.onNodeWithText("同意并继续").performScrollTo().assertIsNotEnabled()
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
