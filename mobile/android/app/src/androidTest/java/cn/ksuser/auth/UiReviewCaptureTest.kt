package cn.ksuser.auth

import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performSemanticsAction
import org.junit.Assert.assertEquals
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import cn.ksuser.auth.core.settings.L10n
import cn.ksuser.auth.core.settings.AppPreferencesState
import cn.ksuser.auth.data.authorization.AuthorizationGrantMode
import cn.ksuser.auth.data.authorization.MobileAuthorizationContext
import cn.ksuser.auth.data.model.AdaptiveAuthStatus
import cn.ksuser.auth.data.model.SensitiveLogItem
import cn.ksuser.auth.data.model.SessionItem
import cn.ksuser.auth.data.model.UserProfile
import cn.ksuser.auth.data.model.UserSettings
import cn.ksuser.auth.ui.AdaptiveSecurityStatusContent
import cn.ksuser.auth.ui.ApplicationConsentScreen
import cn.ksuser.auth.ui.HomeScreen
import cn.ksuser.auth.ui.LogFilterValues
import cn.ksuser.auth.ui.LogsContent
import cn.ksuser.auth.ui.MainDestination
import cn.ksuser.auth.ui.MainNavigationFrame
import cn.ksuser.auth.ui.PendingQrConfirmation
import cn.ksuser.auth.ui.ProfileScreen
import cn.ksuser.auth.ui.QrConfirmationType
import cn.ksuser.auth.ui.SessionsContent
import cn.ksuser.auth.ui.components.SectionCard
import cn.ksuser.auth.ui.theme.KsuserAuthAndroidTheme
import cn.ksuser.auth.ui.settings.DeviceSettingsContent
import java.io.File
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Reproducible visual review fixtures; never signs in or contacts a service. */
class UiReviewCaptureTest {
    @get:Rule val compose = createComposeRule()
    private var previousLanguage = "zh-Hans"
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    @Before fun initializeLocalization() {
        previousLanguage = L10n.language
        L10n.initialize(instrumentation.targetContext)
    }

    @After fun restoreLanguage() { L10n.language = previousLanguage }

    @Test fun captureMainTabsAndConsentAcrossReviewConfigurations() {
        val pages = MainDestination.entries.map { it.route } + listOf("settings", "consent")
        val cases = pages.map { ReviewCase(it, 412, false) } +
            pages.map { ReviewCase(it, 360, true) } +
            listOf("home", "logs", "settings", "consent").map { ReviewCase(it, 360, false, "en", 1.3f) }
        var current by mutableStateOf(cases.first())
        var renderedBackground = Color.Unspecified
        val physicalWidth = instrumentation.targetContext.resources.displayMetrics.widthPixels
        compose.setContent {
            key(current.name) {
                CompositionLocalProvider(LocalDensity provides Density(physicalWidth.toFloat() / current.width, current.fontScale)) {
                    KsuserAuthAndroidTheme(darkTheme = current.dark, dynamicColor = false) {
                        renderedBackground = MaterialTheme.colorScheme.background
                        ReviewPage(current.page)
                    }
                }
            }
        }
        cases.forEach { case ->
            compose.runOnIdle { L10n.language = case.language; current = case }
            compose.waitForIdle()
            capture(case.name)
            if (case.page == "consent") {
                compose.onNodeWithText(if (case.language == "en") "Approve and continue" else "同意并继续").assertIsDisplayed().assertIsEnabled()
                compose.onNodeWithText(if (case.language == "en") "Deny authorization" else "拒绝授权").assertIsDisplayed()
                assertReadableHeading(if (case.language == "en") "Application authorization" else "应用授权", renderedBackground)
                assertReadableHeading("Campus Portal", renderedBackground)
            } else if (case.page == "settings") {
                compose.onNodeWithTag("settings_title").assertIsDisplayed()
                compose.onNodeWithTag("main_navigation").assertDoesNotExist()
                compose.onNodeWithText(L10n.text("语言与外观")).assertIsDisplayed()
            } else {
                compose.onNodeWithTag("main_navigation").assertIsDisplayed()
                compose.onNodeWithTag("nav_${case.page}").assertIsDisplayed()
                if (case.language == "en") MainDestination.entries.forEach { destination ->
                    val layouts = mutableListOf<TextLayoutResult>()
                    compose.onNode(hasText(L10n.text(destination.label)) and hasAnyAncestor(hasTestTag("main_navigation")), useUnmergedTree = true)
                        .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                    assertEquals("Navigation labels must stay on one line", 1, layouts.single().lineCount)
                    val layout = layouts.single()
                    assertTrue("Navigation labels must not be truncated: ${destination.route}", !layout.isLineEllipsized(0))
                    assertTrue("Navigation text must fit its bounds: ${destination.route}",
                        layout.getLineLeft(0) >= -1f && layout.getLineRight(0) <= layout.size.width + 1f)
                }
            }
            when (case.page) {
                "sessions" -> {
                    compose.onNodeWithTag("sessions-list").performScrollToNode(hasText(L10n.text("退出所有设备")))
                    compose.onNodeWithText(L10n.text("退出所有设备")).assertIsDisplayed()
                    capture("${case.name}_footer")
                }
                "logs" -> {
                    compose.onNodeWithTag("logs-list").performScrollToNode(hasText(L10n.text("下一页")))
                    compose.onNodeWithText(L10n.text("下一页")).assertIsDisplayed()
                    capture("${case.name}_footer")
                }
            }
        }
    }

    private fun assertReadableHeading(text: String, background: Color) {
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(text).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val luminance = layouts.single().layoutInput.style.color.luminance()
        val backdrop = background.luminance()
        val contrast = (maxOf(luminance, backdrop) + 0.05f) / (minOf(luminance, backdrop) + 0.05f)
        assertTrue("Heading must remain readable on its background: $text ($contrast)", contrast >= 4.5f)
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val directory = requireNotNull(instrumentation.targetContext.getExternalFilesDir("ui-review"))
        assertTrue(directory.isDirectory || directory.mkdirs())
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(directory, "$name.png").outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
    }

    @Composable private fun ReviewPage(page: String) {
        if (page == "consent") {
            val pending = remember { consentRequest() }
            ApplicationConsentScreen(pending, reviewUser, false, {}, {}, { _, _, _ -> })
            return
        }
        MainNavigationFrame(if (page == "settings") "profile/about" else page, remember { SnackbarHostState() }, {}, {}, {}) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                when (page) {
                    "home" -> HomeScreen(reviewUser, {}, {}, {}, {})
                    "profile" -> ProfileScreen(reviewUser, {}, {}, {})
                    "settings" -> Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
                    ) {
                        var preferences by remember { mutableStateOf(AppPreferencesState()) }
                        DeviceSettingsContent(preferences, { preferences = preferences.copy(language = it) }, { preferences = preferences.copy(theme = it) })
                    }
                    "security" -> Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(24.dp),
                    ) {
                        SectionCard(Modifier.fillMaxWidth()) {
                            AdaptiveSecurityStatusContent(
                                AdaptiveAuthStatus(trusted = true, sensitiveVerified = true, recommendedActionCode = "ADAPTIVE_ACTION_VERIFIED"),
                                false, false, {}, {}, {}, {},
                            )
                        }
                        SectionCard(Modifier.fillMaxWidth()) {
                            Text(L10n.text("TOTP 验证器"), style = MaterialTheme.typography.titleMedium)
                            Text(L10n.format("已启用，剩余恢复码 {0} 个", 8), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    "sessions" -> SessionsContent(reviewSessions, false, null, {}, {}, {})
                    "logs" -> LogsContent(reviewLogs, 1, 3, 48, LogFilterValues(), false, null, {}, {}, {}, {}, {}, {})
                }
            }
        }
    }

    private data class ReviewCase(
        val page: String,
        val width: Int,
        val dark: Boolean,
        val language: String = "zh-Hans",
        val fontScale: Float = 1f,
    ) {
        val name get() = "${if (page == "security") "security_summary" else page}_${if (dark) "dark" else "light"}_${width}dp_${language}_font${fontScale}"
    }

    private fun consentRequest() = PendingQrConfirmation(
        type = QrConfirmationType.APPROVE_APPLICATION,
        code = "synthetic-review",
        expiresAtMillis = System.currentTimeMillis() + 3_600_000,
        authorization = MobileAuthorizationContext(
            ticket = "a".repeat(32), appName = "Campus Portal", redirectUri = "https://example.test/callback",
            requestedScopes = listOf("openid", "profile", "email"), expiresInSeconds = 3600, qr = true,
            subjectName = "Campus Team", contactInfo = "team@example.test", subjectVerificationType = "enterprise",
            webOrigin = "https://example.test",
        ),
        grantMode = AuthorizationGrantMode.PERSISTENT,
    )

    private val reviewUser = UserProfile(
        uuid = "review-user-2026", username = "Lin", email = "lin@example.test", realName = "林同学",
        gender = "female", region = "上海", bio = "保持好奇，持续学习。",
        settings = UserSettings(mfaEnabled = true, preferredMfaMethod = "totp", preferredSensitiveMethod = "password"),
    )
    private val reviewSessions = listOf(
        SessionItem(1, "192.0.2.10", "上海", "Ksuser Android", "Ksuser Android", "Android", "2026-10-20T09:00:00", "2026-10-20T10:30:00", "2026-11-20T09:00:00", null, true, true),
        SessionItem(2, "192.0.2.20", "上海", "Chrome", "Chrome", "Windows", "2026-10-19T09:00:00", "2026-10-20T09:45:00", "2026-11-19T09:00:00", null, false, false),
    )
    private val reviewLogs = listOf(
        SensitiveLogItem(1, "LOGIN", ipAddress = "192.0.2.10", ipLocation = "上海", browser = "Ksuser Android", deviceType = "Android", result = "SUCCESS", createdAt = "2026-10-20T10:30:00"),
        SensitiveLogItem(2, "SENSITIVE_VERIFY", ipAddress = "192.0.2.10", ipLocation = "上海", result = "SUCCESS", createdAt = "2026-10-20T09:50:00"),
        SensitiveLogItem(3, "LOGIN", ipAddress = "192.0.2.20", ipLocation = "北京", result = "FAILURE", riskScore = 65, createdAt = "2026-10-19T20:30:00"),
    )
}
