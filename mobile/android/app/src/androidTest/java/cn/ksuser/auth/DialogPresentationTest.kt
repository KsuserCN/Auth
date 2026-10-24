package cn.ksuser.auth

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import cn.ksuser.auth.core.settings.L10n
import cn.ksuser.auth.data.model.AppUpdateInfo
import cn.ksuser.auth.ui.AvatarCropDialog
import cn.ksuser.auth.ui.StartupUpdateDialog
import cn.ksuser.auth.ui.theme.KsuserAuthAndroidTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Dialog-only fixtures: callbacks record interactions without saving images or downloading files. */
class DialogPresentationTest {
    @get:Rule val compose = createComposeRule()
    private var previousLanguage = "zh-Hans"

    @Before fun setup() {
        previousLanguage = L10n.language
        L10n.initialize(InstrumentationRegistry.getInstrumentation().targetContext)
        L10n.language = "zh-Hans"
    }
    @After fun restoreLanguage() { L10n.language = previousLanguage }

    @Test fun forceUpdateKeepsActionsVisible_whileLongReleaseNotesScroll() {
        var downloads = 0
        var exits = 0
        var dismissals = 0
        compose.setContent {
            DialogReviewTheme {
                StartupUpdateDialog(
                    updateInfo = dialogUpdateFixture(), downloadStarted = false,
                    onDownload = { downloads++ }, onExitApp = { exits++ },
                    onDismiss = { dismissals++ }, onIgnoreVersion = { dismissals++ },
                )
            }
        }
        compose.onNodeWithText("下载最新版").assertIsDisplayed()
        compose.onNodeWithText("关闭应用").assertIsDisplayed()
        compose.onNodeWithText("当前版本已不可继续使用，请更新后再进入应用。").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("下载最新版").assertIsDisplayed().performClick()
        compose.onNodeWithText("关闭应用").assertIsDisplayed().performClick()
        compose.onNodeWithText("稍后").assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, downloads); assertEquals(1, exits); assertEquals(0, dismissals) }
    }

    @Test fun cropSlidersCanScroll_whileSaveAndCancelRemainVisible() {
        L10n.language = "en"
        val bitmap = Bitmap.createBitmap(480, 320, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(90, 130, 170)) }
        var savedWidth = 0
        var savedHeight = 0
        var cancellations = 0
        compose.setContent {
            DialogReviewTheme {
                AvatarCropDialog(
                    bitmap = bitmap,
                    onDismiss = { cancellations++ },
                    onSave = { savedWidth = it.width; savedHeight = it.height },
                )
            }
        }
        compose.onNodeWithText("Save").assertIsDisplayed()
        compose.onNodeWithText("Cancel").assertIsDisplayed()
        val sliders = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress))
        sliders[0].performScrollTo().assertIsDisplayed().performSemanticsAction(SemanticsActions.SetProgress) { it(2f) }
        sliders[2].performScrollTo().assertIsDisplayed().performSemanticsAction(SemanticsActions.SetProgress) { it(0.75f) }
        compose.onNodeWithText("Your photo will be compressed as JPEG. Maximum image size: 10 MB.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Save").assertIsDisplayed().performClick()
        compose.runOnIdle { assertTrue(savedWidth > 0); assertEquals(savedWidth, savedHeight) }
        compose.onNodeWithText("Cancel").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, cancellations) }
    }
}

@Composable
private fun DialogReviewTheme(content: @Composable () -> Unit) {
    val physicalWidth = InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics.widthPixels
    CompositionLocalProvider(LocalDensity provides Density(physicalWidth / 360f, fontScale = 1.3f)) {
        KsuserAuthAndroidTheme(darkTheme = false, content = content)
    }
}

internal fun dialogUpdateFixture() = AppUpdateInfo(
    latestVersionCode = 99,
    latestVersionName = "2.0.0",
    apkUrl = "https://example.invalid/local-test.apk",
    sha256 = null,
    releaseNotes = (1..32).joinToString("\n") { "更新说明 $it：优化账号管理与安全验证界面。" },
    forceUpdate = true,
    isUpdateAvailable = true,
)
