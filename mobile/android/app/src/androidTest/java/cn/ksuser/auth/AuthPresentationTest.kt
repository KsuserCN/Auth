package cn.ksuser.auth

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.captureToImage
import android.graphics.Bitmap
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import cn.ksuser.auth.core.settings.AppPreferences
import cn.ksuser.auth.core.settings.L10n
import cn.ksuser.auth.data.model.AuthResult
import cn.ksuser.auth.data.model.AuthSource
import cn.ksuser.auth.data.model.PasskeyAvailability
import cn.ksuser.auth.ui.AppUiState
import cn.ksuser.auth.ui.AuthFlowActions
import cn.ksuser.auth.ui.AuthFlowContent
import cn.ksuser.auth.ui.settings.ProvideAppPreferences
import cn.ksuser.auth.ui.theme.KsuserAuthAndroidTheme
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Exercises the production form without AppContainer, a ViewModel, credentials or network. */
class AuthPresentationTest {
    @get:Rule val compose = createComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private var previousLanguage = "zh-Hans"
    private lateinit var preferenceContext: Context

    @Before fun initializeIsolatedPreferences() {
        previousLanguage = L10n.language
        preferenceContext = object : ContextWrapper(instrumentation.targetContext) {
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
                super.getSharedPreferences("auth_presentation_test_$name", mode)
        }
        preferenceContext.getSharedPreferences("app_preferences", Context.MODE_PRIVATE).edit().clear().commit()
        L10n.initialize(instrumentation.targetContext)
        L10n.language = "zh-Hans"
    }

    @After fun restoreLanguageAndRemoveTestPreferences() {
        preferenceContext.getSharedPreferences("app_preferences", Context.MODE_PRIVATE).edit().clear().commit()
        L10n.language = previousLanguage
    }

    @Test fun explicitAgreementControlsPasswordProvidersAndScanner() {
        var passwordLogins = 0
        var qqLogins = 0
        var deviceLogins = 0
        var hardwareLogins = 0
        var scans = 0
        compose.setContent {
            KsuserAuthAndroidTheme {
                AuthFlowContent(
                    state = AppUiState(isBootstrapping = false),
                    snackbarHostState = remember { SnackbarHostState() },
                    actions = AuthFlowActions(
                        passwordLogin = { _, _ -> passwordLogins++ },
                        loginWithQq = { qqLogins++ },
                        loginWithPasskey = { deviceLogins++ },
                        loginWithSecurityKey = { hardwareLogins++ },
                    ),
                    onOpenQrScanner = { scans++ },
                )
            }
        }
        compose.onNodeWithTag("auth-brand").assert(hasText("Ksuser安全"))
        compose.onNodeWithText("关于与设置").assertDoesNotExist()
        val scannerBounds = compose.onNodeWithTag("auth-scan").fetchSemanticsNode().boundsInRoot
        val settingsBounds = compose.onNodeWithTag("auth-settings").fetchSemanticsNode().boundsInRoot
        assertTrue("Settings must be the rightmost toolbar action", settingsBounds.left >= scannerBounds.right)
        assertEquals(scannerBounds.width, settingsBounds.width, 0.5f)
        compose.onNodeWithTag("auth-login-email").performTextInput("consent@example.test")
        compose.onNodeWithTag("auth-login-password").performTextInput("SyntheticPassword-2026")
        compose.onNodeWithTag("auth-agreement-checkbox").performScrollTo().assertIsOff()
        val agreementText = compose.onNodeWithTag("auth-agreement-links").fetchSemanticsNode().config[SemanticsProperties.Text].single()
        val agreementLinks = agreementText.getLinkAnnotations(0, agreementText.length).map { (it.item as LinkAnnotation.Url).url }
        assertEquals(listOf(
            "https://docs.ksuser.cn/agreement/user",
            "https://docs.ksuser.cn/agreement/privacy",
            "https://docs.ksuser.cn/agreement/third-party-information-sharing",
        ), agreementLinks)
        listOf("auth-login-primary", "auth-login-qq", "auth-login-passkey", "auth-scan").forEach(::tryDisabledControl)
        compose.onNodeWithTag("auth-passkey-device").assertDoesNotExist()
        compose.runOnIdle { assertEquals(listOf(0, 0, 0, 0, 0), listOf(passwordLogins, qqLogins, deviceLogins, hardwareLogins, scans)) }

        setAgreement(true)
        compose.onNodeWithTag("auth-login-primary").performScrollTo().assertIsEnabled().performClick()
        compose.onNodeWithTag("auth-login-qq").performScrollTo().assertIsEnabled().performClick()
        compose.onNodeWithTag("auth-login-passkey").performScrollTo().assertIsEnabled()
        compose.onNodeWithTag("auth-scan").performScrollTo().assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(listOf(1, 1, 0, 0, 1), listOf(passwordLogins, qqLogins, deviceLogins, hardwareLogins, scans)) }

        setAgreement(false)
        listOf("auth-login-primary", "auth-login-qq", "auth-login-passkey", "auth-scan").forEach(::tryDisabledControl)
        compose.runOnIdle { assertEquals(listOf(1, 1, 0, 0, 1), listOf(passwordLogins, qqLogins, deviceLogins, hardwareLogins, scans)) }
    }

    @Test fun agreementControlsEmailCodeRequestsAndLogin() {
        var requestedEmail: String? = null
        var submitted: Pair<String, String>? = null
        compose.setContent {
            KsuserAuthAndroidTheme {
                AuthFlowContent(
                    state = AppUiState(isBootstrapping = false),
                    snackbarHostState = remember { SnackbarHostState() },
                    actions = AuthFlowActions(
                        sendLoginCode = { requestedEmail = it },
                        loginWithCode = { email, code -> submitted = email to code },
                    ),
                )
            }
        }
        compose.onNodeWithText("验证码登录").performScrollTo().performClick()
        compose.onNodeWithTag("auth-login-email").performTextInput("code@example.test")
        compose.onNodeWithTag("auth-login-code").performTextInput("123456")
        tryDisabledControl("auth-login-send-code")
        tryDisabledControl("auth-login-primary")
        compose.runOnIdle { assertEquals(null, requestedEmail); assertEquals(null, submitted) }

        setAgreement(true)
        compose.onNodeWithTag("auth-login-send-code").performScrollTo().assertIsEnabled().performClick()
        compose.onNodeWithTag("auth-login-primary").performScrollTo().assertIsEnabled().performClick()
        compose.runOnIdle {
            assertEquals("code@example.test", requestedEmail)
            assertEquals("code@example.test" to "123456", submitted)
            requestedEmail = null
            submitted = null
        }
        setAgreement(false)
        tryDisabledControl("auth-login-send-code")
        tryDisabledControl("auth-login-primary")
        compose.runOnIdle { assertEquals(null, requestedEmail); assertEquals(null, submitted) }
    }

    @Test fun unifiedPasskeyRequiresAnExplicitProviderChoiceWhenBothAreAvailable() {
        var deviceLogins = 0
        var hardwareLogins = 0
        compose.setContent {
            KsuserAuthAndroidTheme {
                AuthFlowContent(
                    state = AppUiState(isBootstrapping = false),
                    snackbarHostState = remember { SnackbarHostState() },
                    actions = AuthFlowActions(loginWithPasskey = { deviceLogins++ }, loginWithSecurityKey = { hardwareLogins++ }),
                )
            }
        }
        setAgreement(true)
        compose.onNodeWithText("安全密钥").assertDoesNotExist()
        compose.onNodeWithTag("auth-login-passkey").performScrollTo().performClick()
        compose.onNodeWithTag("auth-passkey-device").assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithTag("auth-passkey-hardware").assertIsDisplayed().assertIsEnabled()
        compose.runOnIdle { assertEquals(0, deviceLogins); assertEquals(0, hardwareLogins) }
        compose.onNodeWithText("取消").performClick()
        compose.onNodeWithTag("auth-passkey-device").assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, deviceLogins); assertEquals(0, hardwareLogins) }

        compose.onNodeWithTag("auth-login-passkey").performScrollTo().performClick()
        compose.onNodeWithTag("auth-passkey-device").performClick()
        compose.onNodeWithTag("auth-passkey-hardware").assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, deviceLogins); assertEquals(0, hardwareLogins) }
        compose.onNodeWithTag("auth-login-passkey").performScrollTo().performClick()
        compose.onNodeWithTag("auth-passkey-hardware").performClick()
        compose.runOnIdle { assertEquals(1, deviceLogins); assertEquals(1, hardwareLogins) }
        setAgreement(false)
        tryDisabledControl("auth-login-passkey")
        compose.onNodeWithTag("auth-passkey-device").assertDoesNotExist()
    }

    @Test fun unifiedPasskeyRetainsNativeOnlyAndHardwareOnlyDevices() {
        var availability by mutableStateOf(0)
        var deviceLogins = 0
        var hardwareLogins = 0
        compose.setContent {
            key(availability) {
                KsuserAuthAndroidTheme {
                    AuthFlowContent(
                        state = AppUiState(isBootstrapping = false),
                        snackbarHostState = remember { SnackbarHostState() },
                        passkeyAvailability = if (availability == 0) PasskeyAvailability.Available else PasskeyAvailability.MissingProvider,
                        securityKeyAvailable = availability == 1,
                        actions = AuthFlowActions(loginWithPasskey = { deviceLogins++ }, loginWithSecurityKey = { hardwareLogins++ }),
                    )
                }
            }
        }
        setAgreement(true)
        compose.onNodeWithTag("auth-login-passkey").performScrollTo().assertIsEnabled().performClick()
        compose.onNodeWithTag("auth-passkey-device").assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, deviceLogins); assertEquals(0, hardwareLogins); availability = 1 }
        compose.onNodeWithTag("auth-agreement-checkbox").performScrollTo().assertIsOff()
        setAgreement(true)
        compose.onNodeWithTag("auth-login-passkey").performScrollTo().assertIsEnabled().performClick()
        compose.onNodeWithTag("auth-passkey-hardware").assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, deviceLogins); assertEquals(1, hardwareLogins); availability = 2 }
        setAgreement(true)
        tryDisabledControl("auth-login-passkey")
        compose.runOnIdle { assertEquals(1, deviceLogins); assertEquals(1, hardwareLogins) }
    }

    @Test fun interruptedPasskeyChoiceDoesNotReopenAfterBusyStateEnds() {
        var busy by mutableStateOf(false)
        var providerLogins = 0
        compose.setContent {
            KsuserAuthAndroidTheme {
                AuthFlowContent(
                    state = AppUiState(isBootstrapping = false, isBusy = busy),
                    snackbarHostState = remember { SnackbarHostState() },
                    actions = AuthFlowActions(loginWithPasskey = { providerLogins++ }, loginWithSecurityKey = { providerLogins++ }),
                )
            }
        }
        setAgreement(true)
        compose.onNodeWithTag("auth-login-passkey").performScrollTo().performClick()
        compose.onNodeWithTag("auth-passkey-device").assertIsDisplayed()
        compose.runOnIdle { busy = true }
        compose.onNodeWithTag("auth-passkey-device").assertDoesNotExist()
        compose.runOnIdle { busy = false }
        compose.onNodeWithTag("auth-passkey-device").assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, providerLogins) }
        compose.onNodeWithTag("auth-login-passkey").performScrollTo().performClick()
        compose.onNodeWithTag("auth-passkey-hardware").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, providerLogins) }
    }

    @Test fun actualLoginAndRegisterInputsSurviveLanguageThemeAndMethodChanges() {
        val preferences = AppPreferences(preferenceContext)
        preferences.setLanguage("zh-Hans")
        var submitted: Pair<String, String>? = null
        compose.setContent {
            ProvideAppPreferences(preferences) {
                KsuserAuthAndroidTheme {
                    AuthFlowContent(
                        state = AppUiState(isBootstrapping = false),
                        snackbarHostState = remember { SnackbarHostState() },
                        actions = AuthFlowActions(passwordLogin = { email, password -> submitted = email to password }),
                    )
                }
            }
        }
        compose.onNodeWithTag("auth-login-email").performTextInput("keep@example.test")
        compose.onNodeWithTag("auth-login-password").performTextInput("SyntheticPassword-2026")
        setAgreement(true)
        compose.onNodeWithText("注册新账号").performScrollTo().performClick()
        compose.onNodeWithTag("auth-register-username").performTextInput("local_tester")
        compose.runOnIdle {
            preferences.setLanguage("en")
            preferences.setTheme("dark")
        }
        compose.onNodeWithTag("auth-register-username").assert(hasText("local_tester"))
        compose.onNodeWithText(L10n.text("已有账号，返回登录")).performScrollTo().performClick()
        compose.onNodeWithTag("auth-login-email").assert(hasText("keep@example.test"))
        compose.onNodeWithTag("auth-agreement-checkbox").performScrollTo().assertIsOn()
        compose.onNodeWithText(L10n.text("验证码登录")).performScrollTo().performClick()
        compose.onNodeWithText(L10n.text("密码登录")).performScrollTo().performClick()
        compose.onNodeWithTag("auth-login-passkey").performScrollTo().performClick()
        compose.onNodeWithText(L10n.text("取消")).performClick()
        compose.onNodeWithText(L10n.text("继续")).performScrollTo().assertIsEnabled().performClick()
        compose.runOnIdle {
            assertEquals("keep@example.test" to "SyntheticPassword-2026", submitted)
            assertEquals("en", preferences.state.value.language)
            assertEquals("dark", preferences.state.value.theme)
        }
    }

    @Test fun captureActualLoginRegisterAndMfaWithLocalStates() {
        val pages = listOf("login", "register", "mfa")
        val cases = pages.map { AuthReviewCase(it, 412, false) } +
            pages.map { AuthReviewCase(it, 360, true) } +
            pages.map { AuthReviewCase(it, 360, false, "en", 1.3f) }
        var current by mutableStateOf(cases.first())
        val physicalWidth = instrumentation.targetContext.resources.displayMetrics.widthPixels
        compose.setContent {
            key(current.name) {
                CompositionLocalProvider(LocalDensity provides Density(physicalWidth.toFloat() / current.width, current.fontScale)) {
                    KsuserAuthAndroidTheme(darkTheme = current.dark, dynamicColor = false) {
                        AuthFlowContent(
                            state = if (current.page == "mfa") mfaState() else AppUiState(isBootstrapping = false),
                            snackbarHostState = remember { SnackbarHostState() },
                        )
                    }
                }
            }
        }
        cases.forEach { case ->
            compose.runOnIdle { L10n.language = case.language; current = case }
            compose.waitForIdle()
            if (case.page == "register") {
                compose.onNodeWithText(L10n.text("注册新账号")).performScrollTo().performClick()
                compose.onNodeWithTag("auth-register-username").assertIsDisplayed()
            }
            capture(case.name)
            when (case.page) {
                "login" -> {
                    compose.onNodeWithTag("auth-login-primary").performScrollTo().assertIsDisplayed().assertIsNotEnabled()
                    setAgreement(true)
                    compose.onNodeWithTag("auth-login-passkey").performScrollTo().assertIsDisplayed().assertIsEnabled()
                    compose.onNodeWithTag("auth-login-qq").assertIsDisplayed().assertIsEnabled()
                    compose.onNodeWithText(L10n.text("安全密钥")).assertDoesNotExist()
                    val qqBounds = compose.onNodeWithTag("auth-login-qq").fetchSemanticsNode().boundsInRoot
                    val passkeyBounds = compose.onNodeWithTag("auth-login-passkey").fetchSemanticsNode().boundsInRoot
                    assertEquals(qqBounds.left, passkeyBounds.left, 0.5f)
                    assertEquals(qqBounds.width, passkeyBounds.width, 0.5f)
                    assertTrue("Provider buttons must span the content width", passkeyBounds.width > physicalWidth * 0.75f)
                    assertTrue("QQ and Passkey must be stacked", passkeyBounds.top >= qqBounds.bottom)
                    capture("${case.name}_providers")
                }
                "register" -> compose.onNodeWithText(L10n.text("下一步")).performScrollTo().assertIsDisplayed()
                "mfa" -> {
                    compose.onNodeWithTag("auth-mfa-code").performScrollTo().assertIsDisplayed()
                    capture("${case.name}_form")
                }
            }
        }
    }

    private fun setAgreement(accepted: Boolean) {
        val checkbox = compose.onNodeWithTag("auth-agreement-checkbox").performScrollTo()
        if (accepted) checkbox.assertIsOff().performClick().assertIsOn()
        else checkbox.assertIsOn().performClick().assertIsOff()
    }

    private fun tryDisabledControl(tag: String) {
        compose.onNodeWithTag(tag).performScrollTo().assertIsNotEnabled().performTouchInput { click() }
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val directory = requireNotNull(instrumentation.targetContext.getExternalFilesDir("ui-review"))
        assertTrue(directory.isDirectory || directory.mkdirs())
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(directory, "$name.png").outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
    }

    private fun mfaState() = AppUiState(
        isBootstrapping = false,
        pendingMfa = AuthResult.NeedsMfa("local-challenge", "totp", listOf("totp", "passkey"), AuthSource.PASSWORD),
    )

    private data class AuthReviewCase(
        val page: String,
        val width: Int,
        val dark: Boolean,
        val language: String = "zh-Hans",
        val fontScale: Float = 1f,
    ) {
        val name get() = "auth_${page}_${if (dark) "dark" else "light"}_${width}dp_${language}_font${fontScale}"
    }
}
