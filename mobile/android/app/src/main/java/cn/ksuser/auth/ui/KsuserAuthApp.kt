package cn.ksuser.auth.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import cn.ksuser.auth.KsuserAuthApplication
import cn.ksuser.auth.data.model.AppUpdateInfo
import cn.ksuser.auth.ui.components.AppRadius
import cn.ksuser.auth.ui.components.AppSpacing
import cn.ksuser.auth.ui.theme.rememberAppBackgroundBrush
import kotlinx.coroutines.launch

private const val UPDATE_PROMPT_PREFS = "app_update_prompt"
private const val IGNORED_VERSION_CODE_KEY = "ignored_version_code"

@Composable
fun KsuserAuthApp(
    incomingDeepLink: Uri? = null,
    onDeepLinkConsumed: () -> Unit = {},
    onExitApp: () -> Unit = {},
    onRegisterActivityResultHandler: (((Int, Int, Intent?) -> Boolean)?) -> Unit = {},
    onRegisterNewIntentHandler: (((Intent) -> Unit)?) -> Unit = {},
) {
    val context = LocalContext.current
    val container = remember(context) { (context.applicationContext as KsuserAuthApplication).appContainer }
    val viewModel: AppViewModel = viewModel(factory = AppViewModelFactory(container))
    val state by viewModel.uiState.collectAsState()
    val updatePromptPrefs = remember(context) {
        context.getSharedPreferences(UPDATE_PROMPT_PREFS, android.content.Context.MODE_PRIVATE)
    }
    val pendingQrConfirmation = state.pendingQrConfirmation
    val pendingMobileBridgeConfirmation = state.pendingMobileBridgeConfirmation
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var startupUpdateInfo by remember { mutableStateOf<AppUpdateInfo?>(null) }
    var startupUpdateError by remember { mutableStateOf<String?>(null) }
    var startupUpdateDismissedVersionCode by remember { mutableStateOf<Long?>(null) }
    var ignoredUpdateVersionCode by remember {
        mutableStateOf(updatePromptPrefs.getLong(IGNORED_VERSION_CODE_KEY, 0L))
    }
    var downloadStartedVersionCode by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(container) {
        runCatching { container.appUpdateRepository.checkForUpdate() }
            .onSuccess { info ->
                startupUpdateError = null
                startupUpdateInfo = info.takeIf { it.isUpdateAvailable }
            }
            .onFailure { throwable ->
                startupUpdateError = throwable.message ?: "检查更新失败"
            }
    }

    DisposableEffect(container) {
        onRegisterActivityResultHandler { requestCode, resultCode, data ->
            container.qqLoginManager.handleActivityResult(requestCode, resultCode, data)
        }
        onRegisterNewIntentHandler { intent ->
            container.qqLoginManager.handleNewIntent(intent)
        }
        onDispose {
            onRegisterActivityResultHandler(null)
            onRegisterNewIntentHandler(null)
        }
    }

    LaunchedEffect(state.message, state.error) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearTransientMessages()
        }
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearTransientMessages()
        }
    }

    LaunchedEffect(incomingDeepLink?.toString()) {
        val current = incomingDeepLink ?: return@LaunchedEffect
        viewModel.handleIncomingDeepLink(current)
        onDeepLinkConsumed()
    }

    LaunchedEffect(state.mobileBridgeReturnUrl) {
        val returnUrl = state.mobileBridgeReturnUrl ?: return@LaunchedEffect
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(returnUrl)))
        }
        viewModel.consumeMobileBridgeReturnUrl()
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        when {
            state.isBootstrapping -> LoadingScreen()
            pendingMobileBridgeConfirmation != null -> MobileBridgeConfirmScreen(
                pending = pendingMobileBridgeConfirmation,
                currentUser = state.currentUser,
                isBusy = state.isBusy,
                onConfirm = { viewModel.confirmMobileBridgeAction() },
                onCancel = { viewModel.cancelMobileBridgeAction() },
                onContinueToLogin = { viewModel.dismissMobileBridgeForLogin() },
            )
            pendingQrConfirmation != null -> QrLoginConfirmScreen(
                pending = pendingQrConfirmation,
                currentUser = state.currentUser,
                isBusy = state.isBusy,
                onConfirm = { viewModel.confirmQrAction() },
                onCancel = { viewModel.cancelQrAction() },
            )
            state.pendingMfa != null || !state.isAuthenticated -> AuthFlowScreen(
                state = state,
                container = container,
                viewModel = viewModel,
                snackbarHostState = snackbarHostState,
            )

            else -> MainShell(
                container = container,
                state = state,
                viewModel = viewModel,
                snackbarHostState = snackbarHostState,
                onMessage = { message -> scope.launch { snackbarHostState.showSnackbar(message) } },
            )
        }
    }

    val visibleStartupUpdate = startupUpdateInfo
        ?.takeIf {
            it.forceUpdate ||
                (
                    ignoredUpdateVersionCode != it.latestVersionCode &&
                        startupUpdateDismissedVersionCode != it.latestVersionCode
                    )
        }
    if (visibleStartupUpdate != null) {
        StartupUpdateDialog(
            updateInfo = visibleStartupUpdate,
            downloadStarted = downloadStartedVersionCode == visibleStartupUpdate.latestVersionCode,
            onDownload = {
                container.appUpdateRepository.enqueueDownload(visibleStartupUpdate)
                downloadStartedVersionCode = visibleStartupUpdate.latestVersionCode
                scope.launch {
                    snackbarHostState.showSnackbar("新版安装包已开始下载，请在通知栏或下载目录中打开安装")
                }
            },
            onDismiss = {
                if (!visibleStartupUpdate.forceUpdate) {
                    startupUpdateDismissedVersionCode = visibleStartupUpdate.latestVersionCode
                }
            },
            onIgnoreVersion = {
                if (!visibleStartupUpdate.forceUpdate) {
                    ignoredUpdateVersionCode = visibleStartupUpdate.latestVersionCode
                    updatePromptPrefs.edit()
                        .putLong(IGNORED_VERSION_CODE_KEY, visibleStartupUpdate.latestVersionCode)
                        .apply()
                }
            },
            onExitApp = onExitApp,
        )
    }

    LaunchedEffect(startupUpdateError) {
        val error = startupUpdateError ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(error)
        startupUpdateError = null
    }
}

@Composable
private fun StartupUpdateDialog(
    updateInfo: AppUpdateInfo,
    downloadStarted: Boolean,
    onDownload: () -> Unit,
    onDismiss: () -> Unit,
    onIgnoreVersion: () -> Unit,
    onExitApp: () -> Unit,
) {
    Dialog(
        onDismissRequest = {
            if (!updateInfo.forceUpdate) {
                onDismiss()
            }
        },
        properties = DialogProperties(
            dismissOnBackPress = !updateInfo.forceUpdate,
            dismissOnClickOutside = !updateInfo.forceUpdate,
        ),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 360.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            shadowElevation = 16.dp,
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.S16),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.S12),
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(AppRadius.R16),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "新",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = if (updateInfo.forceUpdate) "需要更新" else "发现新版本",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "版本 ${updateInfo.latestVersionName} (${updateInfo.latestVersionCode})",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(AppRadius.R16),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    Column(
                        modifier = Modifier.padding(AppSpacing.S12),
                        verticalArrangement = Arrangement.spacedBy(AppSpacing.S8),
                    ) {
                        Text(
                            text = updateInfo.releaseNotes?.takeIf { it.isNotBlank() }
                                ?: "新版已准备好，建议尽快更新以获得更好的使用体验。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        if (downloadStarted) {
                            Text(
                                text = "下载已开始，请在通知栏或下载目录中打开安装包。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        } else if (updateInfo.forceUpdate) {
                            Text(
                                text = "当前版本已不可继续使用，请更新后再进入应用。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }

                Button(
                    onClick = onDownload,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(AppRadius.R16),
                ) {
                    Text(if (downloadStarted) "重新下载最新版" else "下载最新版")
                }

                if (updateInfo.forceUpdate) {
                    OutlinedButton(
                        onClick = onExitApp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp),
                        shape = RoundedCornerShape(AppRadius.R16),
                    ) {
                        Text("关闭应用")
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text("稍后")
                        }
                        Spacer(modifier = Modifier.width(AppSpacing.S8))
                        TextButton(onClick = onIgnoreVersion) {
                            Text("不再提示")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadingScreen() {
    val backgroundBrush = rememberAppBackgroundBrush()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundBrush),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(AppSpacing.S16))
            Text("正在初始化认证环境", style = MaterialTheme.typography.titleMedium)
        }
    }
}
