package cn.ksuser.auth

import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.captureToImage
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import cn.ksuser.auth.core.settings.L10n
import cn.ksuser.auth.data.model.UserProfile
import cn.ksuser.auth.data.model.UserSettings
import cn.ksuser.auth.ui.HomeScreen
import cn.ksuser.auth.ui.MainDestination
import cn.ksuser.auth.ui.MainNavigationFrame
import cn.ksuser.auth.ui.theme.KsuserAuthAndroidTheme
import java.io.File
import org.junit.Rule
import org.junit.Test

/** Captures the synthetic account inside the actual signed-in navigation frame. */
class HomeComparisonCaptureTest {
    @get:Rule val compose = createComposeRule()

    @Test fun captureSyntheticHome() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val previousLanguage = L10n.language
        L10n.initialize(instrumentation.targetContext)
        L10n.language = "zh-Hans"
        try {
            val density = instrumentation.targetContext.resources.displayMetrics.widthPixels / 412f
            compose.setContent {
                CompositionLocalProvider(LocalDensity provides Density(density, 1f)) {
                    KsuserAuthAndroidTheme(darkTheme = false) {
                        MainNavigationFrame(
                            currentRoute = MainDestination.HOME.route,
                            snackbarHostState = remember { SnackbarHostState() },
                            onBack = {},
                            onScan = {},
                            onNavigate = {},
                            onOpenSettings = {},
                        ) { padding ->
                            Box(Modifier.fillMaxSize().padding(padding)) {
                                HomeScreen(
                                    UserProfile("review-user", "林小满", "xiaoman@example.test", region = "上海", bio = "保持好奇，认真生活。",
                                        settings = UserSettings(mfaEnabled = true)),
                                    onRefresh = {}, onOpenProfile = {}, onOpenSecurity = {}, onOpenSessions = {},
                                    onOpenSettings = {},
                                )
                            }
                        }
                    }
                }
            }
            compose.waitForIdle()
            val phase = InstrumentationRegistry.getArguments().getString("reviewPhase", "after")
            val destination = File(instrumentation.targetContext.getExternalFilesDir("ui-review"), "home-$phase.png")
            destination.parentFile?.mkdirs()
            val screenshot = compose.onRoot().captureToImage().asAndroidBitmap()
            destination.outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally { L10n.language = previousLanguage }
    }
}
