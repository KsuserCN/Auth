package cn.ksuser.auth.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import cn.ksuser.auth.data.AppContainer
import cn.ksuser.auth.core.settings.L10n

private const val PROFILE_EDIT_ROUTE = "profile/edit/{fieldKey}"
private const val ABOUT_ROUTE = "profile/about"
private const val AUTHORIZED_APPS_ROUTE = "profile/authorized-apps"
private fun profileEditRoute(fieldKey: String): String = "profile/edit/$fieldKey"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MainShell(
    container: AppContainer,
    state: AppUiState,
    viewModel: AppViewModel,
    snackbarHostState: SnackbarHostState,
    onMessage: (String) -> Unit,
) {
    val navController = rememberNavController()
    val currentBackStack by navController.currentBackStackEntryAsState()
    val currentDestination = currentBackStack?.destination
    val context = LocalContext.current
    var showQrScanner by rememberSaveable { mutableStateOf(false) }
    var showLogoutAllConfirmation by rememberSaveable { mutableStateOf(false) }
    var requestedLogEventId by rememberSaveable { mutableStateOf<Long?>(null) }
    val navigationRequest by container.navigationRequest.collectAsState()
    var showCameraPermissionReason by rememberSaveable { mutableStateOf(false) }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            showQrScanner = true
        } else {
            onMessage(L10n.text("相机权限被拒绝，无法扫码"))
        }
    }
    fun openQrScanner() {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            showQrScanner = true
        } else {
            showCameraPermissionReason = true
        }
    }

    LaunchedEffect(navigationRequest) {
        val request = navigationRequest ?: return@LaunchedEffect
        when (request) {
            "scan" -> openQrScanner()
            "security" -> navController.navigate(MainDestination.SECURITY.route) { launchSingleTop = true }
            "sessions" -> navController.navigate(MainDestination.SESSIONS.route) { launchSingleTop = true }
            "profile" -> navController.navigate(MainDestination.PROFILE.route) { launchSingleTop = true }
        }
        container.consumeNavigation()
    }

    MainNavigationFrame(
        currentRoute = currentDestination?.route.orEmpty(),
        snackbarHostState = snackbarHostState,
        onBack = { navController.popBackStack() },
        onScan = ::openQrScanner,
        onNavigate = { destination -> navigateToMainDestination(navController, destination) },
        onOpenSettings = { navController.navigate(ABOUT_ROUTE) { launchSingleTop = true } },
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.TopCenter) {
        NavHost(
            navController = navController,
            startDestination = MainDestination.HOME.route,
            modifier = Modifier.widthIn(max = 720.dp).fillMaxSize(),
        ) {
            composable(MainDestination.HOME.route) {
                HomeScreen(
                    user = state.currentUser,
                    onRefresh = { viewModel.refreshCurrentUser() },
                    onOpenProfile = { navController.navigate(MainDestination.PROFILE.route) },
                    onOpenSecurity = { navController.navigate(MainDestination.SECURITY.route) },
                    onOpenSessions = { navController.navigate(MainDestination.SESSIONS.route) },
                    onOpenSettings = { navController.navigate(ABOUT_ROUTE) { launchSingleTop = true } },
                )
            }
            composable(MainDestination.PROFILE.route) {
                ProfileScreen(
                    currentUser = state.currentUser,
                    onNavigateToEdit = { fieldKey -> navController.navigate(profileEditRoute(fieldKey)) },
                    onNavigateToAbout = { navController.navigate(ABOUT_ROUTE) },
                    onNavigateToAuthorizedApps = { navController.navigate(AUTHORIZED_APPS_ROUTE) },
                    onRefresh = { viewModel.refreshCurrentUser() },
                    onLogout = { viewModel.logout() },
                )
            }
            composable(
                route = PROFILE_EDIT_ROUTE,
                arguments = listOf(navArgument("fieldKey") { type = NavType.StringType }),
            ) { backStackEntry ->
                val fieldKey = backStackEntry.arguments?.getString("fieldKey").orEmpty()
                ProfileEditScreen(
                    container = container,
                    currentUser = state.currentUser,
                    fieldKey = fieldKey,
                    onBack = { navController.popBackStack() },
                    onProfileUpdated = { viewModel.refreshCurrentUser() },
                    onMessage = onMessage,
                )
            }
            composable(MainDestination.SECURITY.route) {
                SecurityScreen(
                    container = container,
                    user = state.currentUser,
                    onUserRefresh = { viewModel.refreshCurrentUser() },
                    onLogoutAll = { showLogoutAllConfirmation = true },
                    onLogoutCurrent = { viewModel.logout() },
                    onAccountDeleted = { viewModel.accountDeleted() },
                    onOpenQrScanner = ::openQrScanner,
                    onMessage = onMessage,
                )
            }
            composable(MainDestination.SESSIONS.route) {
                SessionsScreen(
                    container = container,
                    onLogoutAll = { showLogoutAllConfirmation = true },
                    onMessage = onMessage,
                )
            }
            composable(MainDestination.LOGS.route) {
                LogsScreen(container = container, eventId = requestedLogEventId, onEventOpened = { requestedLogEventId = null })
            }
            composable(ABOUT_ROUTE) {
                AboutScreen(container = container)
            }
            composable(AUTHORIZED_APPS_ROUTE) {
                AuthorizedAppsScreen(container, onMessage)
            }
        }
        }
    }

    if (showLogoutAllConfirmation) LogoutAllDevicesDialog(container,
        onDismiss = { showLogoutAllConfirmation = false },
        onConfirmed = { viewModel.logoutAll() }, onMessage = onMessage)

    if (showQrScanner) {
        QrScannerDialog(
            onDismiss = { showQrScanner = false },
            onDetected = { rawContent ->
                showQrScanner = false
                viewModel.handleScannedQr(rawContent)
            },
            onMessage = onMessage,
        )
    }

    if (showCameraPermissionReason) {
        CameraPermissionReasonDialog(
            onDismiss = { showCameraPermissionReason = false },
            onConfirm = {
                showCameraPermissionReason = false
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            },
        )
    }
}

@Composable
private fun CameraPermissionReasonDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(L10n.text("需要相机权限")) },
        text = {
            Text(
                L10n.text("扫码授权时，Ksuser 需要使用相机实时读取取景框中的二维码内容，用来确认登录、账号安全或敏感操作请求。相机画面仅用于本次扫码识别，不会用于拍照保存。"),
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(L10n.text("继续授权")) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(L10n.text("取消")) }
        },
    )
}
