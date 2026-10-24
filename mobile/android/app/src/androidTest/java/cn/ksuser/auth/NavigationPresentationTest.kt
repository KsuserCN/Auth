package cn.ksuser.auth

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.test.platform.app.InstrumentationRegistry
import cn.ksuser.auth.core.settings.L10n
import cn.ksuser.auth.data.model.UserProfile
import cn.ksuser.auth.ui.MainDestination
import cn.ksuser.auth.ui.MainNavigationFrame
import cn.ksuser.auth.ui.ProfileScreen
import cn.ksuser.auth.ui.navigateToMainDestination
import cn.ksuser.auth.ui.theme.KsuserAuthAndroidTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class NavigationPresentationTest {
    @get:Rule val compose = createComposeRule()
    private var previousLanguage = "zh-Hans"

    @Before fun useChinese() {
        previousLanguage = L10n.language
        L10n.initialize(InstrumentationRegistry.getInstrumentation().targetContext)
        L10n.language = "zh-Hans"
    }

    @After fun restoreLanguage() { L10n.language = previousLanguage }

    @Test fun allFiveTabsAreSelectableAndRetainTheirSavedState() {
        compose.setContent { KsuserAuthAndroidTheme { NavigationHarness() } }
        MainDestination.entries.forEach { destination ->
            compose.onNodeWithTag("nav_${destination.route}").performClick().assertIsSelected()
            compose.onNodeWithTag("increment_${destination.route}").performClick()
        }
        MainDestination.entries.forEach { destination ->
            compose.onNodeWithTag("nav_${destination.route}").performClick().assertIsSelected()
            compose.onNodeWithTag("count_${destination.route}").assertTextEquals("1")
        }
    }

    @Test fun profileEditorHidesNavigationAndBackRestoresProfileTab() {
        compose.setContent { KsuserAuthAndroidTheme { NavigationHarness(actualProfile = true) } }
        compose.onNodeWithTag("nav_profile").performClick().assertIsSelected()
        compose.onNodeWithText("真实姓名").performScrollTo().performClick()
        compose.onNodeWithTag("profile_editor").assertIsDisplayed()
        compose.onNodeWithTag("main_navigation").assertDoesNotExist()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithTag("main_navigation").assertIsDisplayed()
        compose.onNodeWithTag("nav_profile").assertIsSelected()
        compose.onNodeWithText("真实姓名").assertIsDisplayed()
    }

    @Test fun settingsShortcutIsAvailableFromEveryMainTabAndBackRetainsTabState() {
        compose.setContent { KsuserAuthAndroidTheme { NavigationHarness() } }
        compose.onNodeWithText("Ksuser安全").assertIsDisplayed()
        val scannerBounds = compose.onNodeWithTag("open_scanner").fetchSemanticsNode().boundsInRoot
        val settingsBounds = compose.onNodeWithTag("open_settings").fetchSemanticsNode().boundsInRoot
        assertTrue("Settings must be the trailing toolbar action, after the scanner", settingsBounds.left >= scannerBounds.right)
        assertEquals("Toolbar icons must share the same touch-target width", scannerBounds.width, settingsBounds.width, 0.5f)
        MainDestination.entries.forEach { destination ->
            compose.onNodeWithTag("nav_${destination.route}").performClick()
            compose.onNodeWithTag("increment_${destination.route}").performClick()
            compose.onNodeWithText("设置").assertDoesNotExist()
            compose.onNodeWithTag("open_settings").assertContentDescriptionEquals("打开设置")
            compose.onNodeWithContentDescription("打开设置").assertIsDisplayed().performClick()
            compose.onNodeWithTag("settings_title").assertTextEquals("设置")
            compose.onNodeWithTag("settings_content").assertIsDisplayed()
            compose.onNodeWithTag("main_navigation").assertDoesNotExist()
            compose.onNodeWithTag("open_settings").assertDoesNotExist()
            compose.onNodeWithContentDescription("返回").performClick()
            compose.onNodeWithTag("nav_${destination.route}").assertIsSelected()
            compose.onNodeWithTag("count_${destination.route}").assertTextEquals("1")
            compose.onNodeWithTag("open_settings").assertIsDisplayed()
        }
        MainDestination.entries.forEach { destination ->
            compose.onNodeWithTag("nav_${destination.route}").performClick()
            compose.onNodeWithTag("count_${destination.route}").assertTextEquals("1")
        }
    }

    @Composable
    private fun NavigationHarness(actualProfile: Boolean = false) {
        val nav = rememberNavController()
        val entry by nav.currentBackStackEntryAsState()
        MainNavigationFrame(
            currentRoute = entry?.destination?.route.orEmpty(),
            snackbarHostState = remember { SnackbarHostState() },
            onBack = { nav.popBackStack() },
            onScan = {},
            onNavigate = { navigateToMainDestination(nav, it) },
            onOpenSettings = { nav.navigate("profile/about") { launchSingleTop = true } },
        ) { padding ->
            NavHost(nav, startDestination = MainDestination.HOME.route, modifier = Modifier.fillMaxSize().padding(padding)) {
                MainDestination.entries.forEach { destination ->
                    composable(destination.route) {
                        if (actualProfile && destination == MainDestination.PROFILE) {
                            ProfileScreen(
                                currentUser = UserProfile("navigation-test", "Review User", "review@example.test"),
                                onNavigateToEdit = { nav.navigate("profile/edit/$it") },
                                onNavigateToAbout = {},
                                onNavigateToAuthorizedApps = {},
                            )
                        } else {
                            var count by rememberSaveable { mutableIntStateOf(0) }
                            Column {
                                Text(count.toString(), Modifier.testTag("count_${destination.route}"))
                                Button(onClick = { count++ }, modifier = Modifier.testTag("increment_${destination.route}")) { Text("Increment") }
                            }
                        }
                    }
                }
                composable("profile/edit/{fieldKey}") {
                    Text("Profile editor", Modifier.testTag("profile_editor"))
                }
                composable("profile/about") {
                    Text("Settings", Modifier.testTag("settings_content"))
                }
            }
        }
    }
}
