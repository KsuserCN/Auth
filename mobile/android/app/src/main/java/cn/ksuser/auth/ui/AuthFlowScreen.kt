package cn.ksuser.auth.ui

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.Image
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withLink
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.core.content.ContextCompat
import cn.ksuser.auth.R
import cn.ksuser.auth.core.settings.L10n
import cn.ksuser.auth.data.AppContainer
import cn.ksuser.auth.data.model.PasskeyAvailability
import cn.ksuser.auth.ui.components.AppOutlinedField
import cn.ksuser.auth.ui.components.appPrimaryButtonColors
import cn.ksuser.auth.ui.components.AppPagePadding
import cn.ksuser.auth.ui.components.AppRadius
import cn.ksuser.auth.ui.components.AppSpacing
import cn.ksuser.auth.ui.components.GradientPrimaryButton
import cn.ksuser.auth.ui.components.LoadingButtonContent
import cn.ksuser.auth.ui.theme.rememberAppBackgroundBrush
import kotlinx.coroutines.launch

@Composable
internal fun AuthFlowScreen(
    state: AppUiState,
    container: AppContainer,
    viewModel: AppViewModel,
    snackbarHostState: SnackbarHostState,
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()
    var showQrScanner by rememberSaveable { mutableStateOf(false) }
    var showCameraPermissionReason by rememberSaveable { mutableStateOf(false) }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            showQrScanner = true
        } else {
            Toast.makeText(context, L10n.text("相机权限被拒绝，无法扫码"), Toast.LENGTH_SHORT).show()
        }
    }
    var showAboutSettings by rememberSaveable { mutableStateOf(false) }
    val passkeyAvailability = remember(container) { container.passkeyManager.availability() }
    val passkeyAvailabilityMessage = remember(container, L10n.language) { container.passkeyManager.availabilityMessage() }
    val qqLoginConfigured = remember(container) { container.qqLoginManager.isConfigured }
    val securityKeyAvailable = activity != null && container.securityKeyManager.available(activity)
    val openQrScanner = {
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
    AuthFlowContent(
        state = state,
        snackbarHostState = snackbarHostState,
        passkeyAvailability = passkeyAvailability,
        passkeyAvailabilityMessage = passkeyAvailabilityMessage,
        qqLoginConfigured = qqLoginConfigured,
        securityKeyAvailable = securityKeyAvailable,
        onOpenQrScanner = openQrScanner,
        onOpenSettings = { showAboutSettings = true },
        actions = AuthFlowActions(
            passwordLogin = viewModel::passwordLogin,
            sendLoginCode = viewModel::sendLoginCode,
            loginWithCode = viewModel::loginWithCode,
            checkUsernameAndThen = viewModel::checkUsernameAndThen,
            sendRegisterCode = { viewModel.sendRegisterCode(it) },
            sendRegisterCodeAndThen = { email, onSent -> viewModel.sendRegisterCode(email, onSent) },
            register = viewModel::register,
            verifyTotp = { viewModel.verifyTotpMfa(code = it) },
            verifyRecoveryCode = { viewModel.verifyTotpMfa(recoveryCode = it) },
            verifyPasskeyMfa = {
                if (activity == null) Toast.makeText(context, L10n.text("当前上下文不支持 Passkey"), Toast.LENGTH_SHORT).show()
                else viewModel.verifyPasskeyMfa(activity)
            },
            verifySecurityKeyMfa = { activity?.let(viewModel::verifySecurityKeyMfa) },
            loginWithQq = {
                if (activity == null) Toast.makeText(context, L10n.text("当前上下文不支持 QQ 登录"), Toast.LENGTH_SHORT).show()
                else viewModel.loginWithQq(activity)
            },
            loginWithPasskey = {
                if (activity == null) Toast.makeText(context, L10n.text("当前上下文不支持 Passkey"), Toast.LENGTH_SHORT).show()
                else viewModel.loginWithPasskey(activity)
            },
            loginWithSecurityKey = { activity?.let(viewModel::loginWithSecurityKey) },
            cancelAuthentication = viewModel::cancelAuthentication,
            clearPendingOAuthBind = viewModel::clearPendingOAuthBind,
        ),
    )

    if (showAboutSettings) AboutSettingsDialog(container) { showAboutSettings = false }

    if (showQrScanner) {
        QrScannerDialog(
            onDismiss = { showQrScanner = false },
            onDetected = { rawContent ->
                showQrScanner = false
                viewModel.handleScannedQr(rawContent)
            },
            onMessage = { message ->
                scope.launch { snackbarHostState.showSnackbar(message) }
            },
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

/** Callbacks for the auth form; native providers and protocol consent stay in AuthFlowScreen. */
internal class AuthFlowActions(
    val passwordLogin: (String, String) -> Unit = { _, _ -> },
    val sendLoginCode: (String) -> Unit = {},
    val loginWithCode: (String, String) -> Unit = { _, _ -> },
    val checkUsernameAndThen: (String, () -> Unit) -> Unit = { _, _ -> },
    val sendRegisterCode: (String) -> Unit = {},
    val sendRegisterCodeAndThen: (String, () -> Unit) -> Unit = { _, _ -> },
    val register: (String, String, String, String) -> Unit = { _, _, _, _ -> },
    val verifyTotp: (String) -> Unit = {},
    val verifyRecoveryCode: (String) -> Unit = {},
    val verifyPasskeyMfa: () -> Unit = {},
    val verifySecurityKeyMfa: () -> Unit = {},
    val loginWithQq: () -> Unit = {},
    val loginWithPasskey: () -> Unit = {},
    val loginWithSecurityKey: () -> Unit = {},
    val cancelAuthentication: () -> Unit = {},
    val clearPendingOAuthBind: () -> Unit = {},
)

/** Actual login/register/MFA presentation, usable with local state and callbacks only. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AuthFlowContent(
    state: AppUiState,
    snackbarHostState: SnackbarHostState,
    actions: AuthFlowActions = AuthFlowActions(),
    passkeyAvailability: PasskeyAvailability = PasskeyAvailability.Available,
    passkeyAvailabilityMessage: String = "",
    qqLoginConfigured: Boolean = true,
    securityKeyAvailable: Boolean = true,
    onOpenQrScanner: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
) {
    var authMode by rememberSaveable { mutableStateOf(0) }
    var loginMethod by rememberSaveable { mutableStateOf(0) }
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var registerUsername by rememberSaveable { mutableStateOf("") }
    var registerEmail by rememberSaveable { mutableStateOf("") }
    var registerPassword by remember { mutableStateOf("") }
    var registerPasswordConfirm by remember { mutableStateOf("") }
    var registerCode by remember { mutableStateOf("") }
    var registerStep by rememberSaveable { mutableStateOf(RegisterStep.Username.name) }
    var mfaCode by remember { mutableStateOf("") }
    var useRecoveryCode by rememberSaveable { mutableStateOf(false) }
    var agreementAccepted by rememberSaveable { mutableStateOf(false) }
    var showPasskeyProviders by remember { mutableStateOf(false) }
    val devicePasskeyAvailable = passkeyAvailability == PasskeyAvailability.Available
    val anyPasskeyAvailable = devicePasskeyAvailable || securityKeyAvailable
    val canAuthenticate = agreementAccepted && !state.isBusy
    val runWithAgreement: (() -> Unit) -> Unit = { action ->
        if (canAuthenticate) action()
    }
    val pendingOAuthBind = state.pendingOAuthBind
    val showRegisterFlow = authMode == 1
    LaunchedEffect(pendingOAuthBind?.bindToken) { if (pendingOAuthBind != null) authMode = 1 }
    BackHandler(enabled = state.pendingMfa != null) { actions.cancelAuthentication() }
    val backgroundBrush = rememberAppBackgroundBrush()
    val authScrollState = rememberScrollState()
    LaunchedEffect(authMode, state.pendingMfa != null) { authScrollState.scrollTo(0) }
    LaunchedEffect(canAuthenticate, showRegisterFlow, state.pendingMfa != null) {
        if (!canAuthenticate || showRegisterFlow || state.pendingMfa != null) showPasskeyProviders = false
    }
    LaunchedEffect(showRegisterFlow) {
        if (showRegisterFlow && registerStep.isBlank()) {
            registerStep = RegisterStep.Username.name
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(backgroundBrush)
                .imePadding()
                .verticalScroll(authScrollState)
                .padding(AppPagePadding),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.S20),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.S24),
            ) {
                AuthToolbar(
                    scannerEnabled = canAuthenticate && state.pendingMfa == null,
                    onOpenSettings = onOpenSettings,
                    onOpenQrScanner = { runWithAgreement(onOpenQrScanner) },
                )
                Column(
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        L10n.text(when {
                            state.pendingMfa != null -> "完成验证"
                            showRegisterFlow -> "创建账号"
                            else -> "欢迎回来"
                        }),
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        if (state.pendingMfa != null) {
                            L10n.text("继续完成账号验证")
                        } else if (pendingOAuthBind != null) {
                            L10n.text("该 QQ 账号尚未绑定。登录已有账号或注册新账号后继续绑定。")
                        } else if (showRegisterFlow) {
                            L10n.text("创建 Ksuser 账号，完成邮箱验证后即可登录。")
                        } else {
                            L10n.text("使用你的 Ksuser 账号继续，或创建一个新账号。")
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (pendingOAuthBind != null && state.pendingMfa == null) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { authMode = 0 }) { Text(L10n.text("登录已有账号并绑定 QQ")) }
                    TextButton(onClick = { authMode = 1 }) { Text(L10n.text("注册新账号")) }
                    TextButton(onClick = { actions.clearPendingOAuthBind() }) { Text(L10n.text("取消绑定")) }
                }
            }
            if (state.pendingMfa != null) {
                AuthFormCard {
                    val challenge = state.pendingMfa!!
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Icon(Icons.Outlined.Shield, contentDescription = null, modifier = Modifier.padding(14.dp).size(26.dp))
                    }
                    Text(L10n.text("完成验证"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(
                        L10n.text("第一因子已通过，请完成额外验证后继续登录。"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(L10n.format("可用方式: {0}", challenge.methods.joinToString()), style = MaterialTheme.typography.bodyMedium)
                    if ("totp" in challenge.methods) Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.S8)) {
                        FilterChip(
                            selected = !useRecoveryCode,
                            onClick = { useRecoveryCode = false },
                            label = { Text("TOTP") },
                        )
                        FilterChip(
                            selected = useRecoveryCode,
                            onClick = { useRecoveryCode = true },
                            label = { Text(L10n.text("恢复码")) },
                        )
                    }
                    if (challenge.methods.contains("passkey")) {
                        OutlinedButton(
                            onClick = {
                                actions.verifyPasskeyMfa()
                            },
                            enabled = !state.isBusy,
                            modifier = Modifier.fillMaxWidth(),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(AppRadius.R12),
                        ) {
                            Text(L10n.text("使用 Passkey 完成验证"))
                        }
                    }
                    if ("passkey" in challenge.methods) {
                        OutlinedButton(onClick = { actions.verifySecurityKeyMfa() }, enabled = !state.isBusy && securityKeyAvailable, modifier = Modifier.fillMaxWidth()) {
                            Text(L10n.text("使用 USB / NFC 安全密钥"))
                        }
                        if (!securityKeyAvailable) Text(L10n.text("当前设备没有可用的硬件安全密钥提供方"), style = MaterialTheme.typography.bodySmall)
                    }
                    if ("totp" in challenge.methods) {
                    AppOutlinedField(
                        value = mfaCode,
                        onValueChange = { mfaCode = it },
                        modifier = Modifier.testTag("auth-mfa-code"),
                        label = { Text(if (useRecoveryCode) L10n.text("恢复码") else L10n.text("6 位验证码")) },
                    )
                    GradientPrimaryButton(
                        text = if (state.isBusy) L10n.text("处理中...") else L10n.text("完成验证"),
                        onClick = {
                            if (useRecoveryCode) {
                                actions.verifyRecoveryCode(mfaCode.trim().uppercase())
                            } else {
                                actions.verifyTotp(mfaCode.trim())
                            }
                        },
                        enabled = !state.isBusy && mfaCode.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    }
                    TextButton(onClick = { actions.cancelAuthentication() }) { Text(L10n.text("取消")) }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.S24),
                ) {
                    AuthFormCard {
                        if (!showRegisterFlow) {
                            Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.S16)) {
                                AuthLoginMethodTabs(
                                    selectedMethod = loginMethod,
                                    enabled = !state.isBusy,
                                    onSelectMethod = { loginMethod = it },
                                )
                                LoginLineField(
                                    value = email,
                                    onValueChange = { email = it },
                                    modifier = Modifier.testTag("auth-login-email"),
                                    label = L10n.text("邮箱"),
                                )
                                when (loginMethod) {
                                    0 -> {
                                        LoginLineField(
                                            value = password,
                                            onValueChange = { password = it },
                                            modifier = Modifier.testTag("auth-login-password"),
                                            label = L10n.text("密码"),
                                            isPassword = true,
                                        )
                                        SolidPrimaryButton(
                                            text = L10n.text("继续"),
                                            onClick = {
                                                runWithAgreement {
                                                    actions.passwordLogin(email.trim(), password)
                                                }
                                            },
                                            enabled = canAuthenticate && email.isNotBlank() && password.isNotBlank(),
                                            isLoading = state.isBusy,
                                            modifier = Modifier.fillMaxWidth().testTag("auth-login-primary"),
                                        )
                                        LoginAgreementRow(
                                            accepted = agreementAccepted,
                                            onAcceptedChange = { agreementAccepted = it },
                                            enabled = !state.isBusy,
                                        )
                                    }

                                    else -> {
                                        Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.S8)) {
                                            LoginLineField(
                                                value = code,
                                                onValueChange = { code = it },
                                                label = L10n.text("验证码"),
                                                modifier = Modifier.weight(1f).testTag("auth-login-code"),
                                            )
                                            OutlinedButton(
                                                onClick = {
                                                    runWithAgreement {
                                                        actions.sendLoginCode(email.trim())
                                                    }
                                                },
                                                enabled = canAuthenticate && email.isNotBlank(),
                                                modifier = Modifier.heightIn(min = 56.dp).testTag("auth-login-send-code"),
                                                shape = RoundedCornerShape(AppRadius.R12),
                                            ) {
                                                LoadingButtonContent(text = L10n.text("发送"), isLoading = state.isBusy)
                                            }
                                        }
                                        SolidPrimaryButton(
                                            text = L10n.text("继续"),
                                            onClick = {
                                                runWithAgreement {
                                                    actions.loginWithCode(email.trim(), code.trim())
                                                }
                                            },
                                            enabled = canAuthenticate && email.isNotBlank() && code.isNotBlank(),
                                            isLoading = state.isBusy,
                                            modifier = Modifier.fillMaxWidth().testTag("auth-login-primary"),
                                        )
                                        LoginAgreementRow(
                                            accepted = agreementAccepted,
                                            onAcceptedChange = { agreementAccepted = it },
                                            enabled = !state.isBusy,
                                        )
                                    }
                                }
                            }
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.S16)) {
                                val currentRegisterStep = parseRegisterStep(registerStep)
                                if (pendingOAuthBind != null) {
                                    Text(
                                        pendingOAuthBind.message
                                            ?: L10n.text("QQ 授权已完成，创建账号后会自动绑定。"),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }

                                RegisterStepHeader(currentRegisterStep)

                                when (currentRegisterStep) {
                                    RegisterStep.Username -> {
                                        Text(
                                            L10n.text("3-20 个字符，支持中文、字母、数字、下划线和连字符"),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        LoginLineField(
                                            value = registerUsername,
                                            onValueChange = { registerUsername = it },
                                            modifier = Modifier.testTag("auth-register-username"),
                                            label = L10n.text("用户名"),
                                        )
                                        RegisterStepActions(
                                            primaryText = L10n.text("下一步"),
                                            onPrimary = {
                                                runWithAgreement {
                                                    actions.checkUsernameAndThen(registerUsername.trim()) {
                                                        registerStep = RegisterStep.Password.name
                                                    }
                                                }
                                            },
                                            primaryEnabled = canAuthenticate && isValidRegisterUsername(registerUsername),
                                            isBusy = state.isBusy,
                                            onLogin = {
                                                authMode = 0
                                            },
                                        )
                                    }

                                    RegisterStep.Password -> {
                                        Text(
                                            state.passwordRequirement?.requirementMessage
                                                ?: L10n.text("请设置符合安全策略的密码。"),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        LoginLineField(
                                            value = registerPassword,
                                            onValueChange = { registerPassword = it },
                                            modifier = Modifier.testTag("auth-register-password"),
                                            label = L10n.text("密码"),
                                            isPassword = true,
                                        )
                                        RegisterStepActions(
                                            primaryText = L10n.text("下一步"),
                                            onPrimary = { registerStep = RegisterStep.PasswordConfirm.name },
                                            primaryEnabled = !state.isBusy &&
                                                isPasswordAllowed(registerPassword, state.passwordRequirement),
                                            isBusy = state.isBusy,
                                            secondaryText = L10n.text("返回"),
                                            onSecondary = { registerStep = RegisterStep.Username.name },
                                            onLogin = {
                                                authMode = 0
                                            },
                                        )
                                    }

                                    RegisterStep.PasswordConfirm -> {
                                        Text(
                                            L10n.text("请再次输入密码以确认"),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        LoginLineField(
                                            value = registerPasswordConfirm,
                                            onValueChange = { registerPasswordConfirm = it },
                                            modifier = Modifier.testTag("auth-register-password-confirm"),
                                            label = L10n.text("确认密码"),
                                            isPassword = true,
                                        )
                                        if (
                                            registerPasswordConfirm.isNotBlank() &&
                                            registerPassword != registerPasswordConfirm
                                        ) {
                                            Text(
                                                L10n.text("两次输入的密码不一致"),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.error,
                                            )
                                        }
                                        RegisterStepActions(
                                            primaryText = L10n.text("下一步"),
                                            onPrimary = { registerStep = RegisterStep.Email.name },
                                            primaryEnabled = !state.isBusy &&
                                                registerPasswordConfirm.isNotBlank() &&
                                                registerPassword == registerPasswordConfirm,
                                            isBusy = state.isBusy,
                                            secondaryText = L10n.text("返回"),
                                            onSecondary = { registerStep = RegisterStep.Password.name },
                                            onLogin = {
                                                authMode = 0
                                            },
                                        )
                                    }

                                    RegisterStep.Email -> {
                                        Text(
                                            L10n.text("用于接收验证码和账号邮件"),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        LoginLineField(
                                            value = registerEmail,
                                            onValueChange = { registerEmail = it },
                                            modifier = Modifier.testTag("auth-register-email"),
                                            label = L10n.text("邮箱"),
                                        )
                                        Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.S8)) {
                                            OutlinedButton(
                                                onClick = { registerStep = RegisterStep.PasswordConfirm.name },
                                                enabled = !state.isBusy,
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .heightIn(min = 52.dp),
                                                shape = RoundedCornerShape(AppRadius.R12),
                                            ) {
                                                Text(L10n.text("返回"))
                                            }
                                            Button(
                                                onClick = {
                                                    runWithAgreement {
                                                        actions.sendRegisterCodeAndThen(registerEmail.trim()) {
                                                            registerStep = RegisterStep.Code.name
                                                        }
                                                    }
                                                },
                                                enabled = canAuthenticate && isValidEmail(registerEmail),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .heightIn(min = 52.dp),
                                                shape = RoundedCornerShape(AppRadius.R12),
                                                colors = appPrimaryButtonColors(),
                                            ) {
                                                LoadingButtonContent(text = L10n.text("发送验证码"), isLoading = state.isBusy)
                                            }
                                        }
                                        TextButton(
                                            onClick = {
                                                authMode = 0
                                            },
                                            enabled = !state.isBusy,
                                            modifier = Modifier.align(Alignment.CenterHorizontally),
                                        ) {
                                            Text(L10n.text("已有账号，返回登录"))
                                        }
                                    }

                                    RegisterStep.Code -> {
                                        Text(
                                            L10n.format("验证码已发送至 {0}", registerEmail.ifBlank { L10n.text("你的邮箱") }),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text(
                                                registerEmail,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                            TextButton(
                                                onClick = {
                                                    registerCode = ""
                                                    registerStep = RegisterStep.Email.name
                                                },
                                                enabled = !state.isBusy,
                                            ) {
                                                Text(L10n.text("修改邮箱"))
                                            }
                                        }
                                        Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.S8)) {
                                            LoginLineField(
                                                value = registerCode,
                                                onValueChange = { registerCode = it.filter(Char::isDigit).take(6) },
                                                label = L10n.text("验证码"),
                                                modifier = Modifier.weight(1f).testTag("auth-register-code"),
                                            )
                                            OutlinedButton(
                                                onClick = {
                                                    runWithAgreement {
                                                        actions.sendRegisterCode(registerEmail.trim())
                                                    }
                                                },
                                                enabled = canAuthenticate && isValidEmail(registerEmail),
                                                modifier = Modifier.heightIn(min = 56.dp),
                                                shape = RoundedCornerShape(AppRadius.R12),
                                            ) {
                                                LoadingButtonContent(text = L10n.text("重发"), isLoading = state.isBusy)
                                            }
                                        }
                                        RegisterStepActions(
                                            primaryText = if (pendingOAuthBind != null) L10n.text("注册并绑定 QQ") else L10n.text("创建账号"),
                                            onPrimary = {
                                                runWithAgreement {
                                                    actions.register(
                                                        registerUsername.trim(),
                                                        registerEmail.trim(),
                                                        registerPassword,
                                                        registerCode.trim(),
                                                    )
                                                }
                                            },
                                            primaryEnabled = canAuthenticate &&
                                                registerUsername.isNotBlank() &&
                                                isValidEmail(registerEmail) &&
                                                registerPassword.isNotBlank() &&
                                                registerPassword == registerPasswordConfirm &&
                                                registerCode.length == 6,
                                            isBusy = state.isBusy,
                                            secondaryText = L10n.text("返回"),
                                            onSecondary = { registerStep = RegisterStep.Email.name },
                                            onLogin = {
                                                authMode = 0
                                            },
                                        )
                                    }
                                }

                                LoginAgreementRow(
                                    accepted = agreementAccepted,
                                    onAcceptedChange = { agreementAccepted = it },
                                    enabled = !state.isBusy,
                                )
                            }
                        }
                    }

                    if (!showRegisterFlow) {
                        TextButton(
                            onClick = { authMode = 1 },
                            enabled = !state.isBusy,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(L10n.text("注册新账号")) }
                        Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.S16)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(AppSpacing.S12),
                            ) {
                                HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
                                Text(
                                    L10n.text("快捷登录"),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.S12)) {
                                AuthProviderButton(
                                    text = L10n.text("QQ 登录"),
                                    onClick = {
                                        runWithAgreement { actions.loginWithQq() }
                                    },
                                    enabled = canAuthenticate && qqLoginConfigured,
                                    modifier = Modifier.fillMaxWidth().testTag("auth-login-qq"),
                                ) {
                                    Image(painterResource(R.drawable.qq_symbol), contentDescription = null, modifier = Modifier.size(24.dp))
                                }
                                AuthProviderButton(
                                    text = L10n.text("Passkey 登录"),
                                    onClick = {
                                        runWithAgreement {
                                            when {
                                                devicePasskeyAvailable && securityKeyAvailable -> showPasskeyProviders = true
                                                devicePasskeyAvailable -> actions.loginWithPasskey()
                                                securityKeyAvailable -> actions.loginWithSecurityKey()
                                            }
                                        }
                                    },
                                    enabled = canAuthenticate && anyPasskeyAvailable,
                                    modifier = Modifier.fillMaxWidth().testTag("auth-login-passkey"),
                                ) {
                                    Icon(Icons.Outlined.Fingerprint, contentDescription = null, modifier = Modifier.size(24.dp))
                                }
                            }
                            if (!qqLoginConfigured) Text(L10n.text("QQ 登录未配置"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (!anyPasskeyAvailable) Text(passkeyAvailabilityMessage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                }
            }
        }
    }

    if (showPasskeyProviders && canAuthenticate && !showRegisterFlow && state.pendingMfa == null) {
        AlertDialog(
            onDismissRequest = { showPasskeyProviders = false },
            title = { Text(L10n.text("Passkey 登录")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.S12)) {
                    AuthProviderButton(
                        text = L10n.text("设备或密码管理器"),
                        enabled = devicePasskeyAvailable,
                        modifier = Modifier.fillMaxWidth().testTag("auth-passkey-device"),
                        onClick = {
                            showPasskeyProviders = false
                            runWithAgreement { if (devicePasskeyAvailable) actions.loginWithPasskey() }
                        },
                    ) {
                        Icon(Icons.Outlined.Fingerprint, contentDescription = null, modifier = Modifier.size(22.dp))
                    }
                    AuthProviderButton(
                        text = L10n.text("USB / NFC 安全密钥"),
                        enabled = securityKeyAvailable,
                        modifier = Modifier.fillMaxWidth().testTag("auth-passkey-hardware"),
                        onClick = {
                            showPasskeyProviders = false
                            runWithAgreement { if (securityKeyAvailable) actions.loginWithSecurityKey() }
                        },
                    ) {
                        Icon(Icons.Outlined.Key, contentDescription = null, modifier = Modifier.size(22.dp))
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showPasskeyProviders = false }) { Text(L10n.text("取消")) }
            },
        )
    }
}

@Composable
private fun AuthToolbar(
    scannerEnabled: Boolean,
    onOpenSettings: () -> Unit,
    onOpenQrScanner: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(id = R.drawable.ksuser_launcher_icon),
                contentDescription = "Ksuser Logo",
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(2.dp),
            )
            Text(
                L10n.text("Ksuser安全"),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                modifier = Modifier.testTag("auth-brand"),
            )
        }
        IconButton(
            onClick = onOpenQrScanner,
            enabled = scannerEnabled,
            modifier = Modifier.size(44.dp).testTag("auth-scan"),
        ) {
            Icon(Icons.Outlined.QrCodeScanner, contentDescription = L10n.text("扫码登录/授权"), modifier = Modifier.size(22.dp))
        }
        IconButton(
            onClick = onOpenSettings,
            modifier = Modifier.size(44.dp).testTag("auth-settings"),
        ) {
            Icon(Icons.Outlined.Settings, contentDescription = L10n.text("关于与设置"), modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun AuthFormCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(AppRadius.R24),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)),
    ) {
        Column(
            modifier = Modifier.padding(AppSpacing.S20),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.S16),
            content = content,
        )
    }
}

@Composable
private fun AuthLoginMethodTabs(
    selectedMethod: Int,
    enabled: Boolean,
    onSelectMethod: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.S16),
    ) {
        listOf("密码登录", "验证码登录").forEachIndexed { index, label ->
            val selected = selectedMethod == index
            Column(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .selectable(
                        selected = selected,
                        enabled = enabled,
                        role = Role.Tab,
                        onClick = { onSelectMethod(index) },
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    L10n.text(label),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = when {
                        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        selected -> MaterialTheme.colorScheme.onSurface
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 12.dp),
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                        ),
                )
            }
        }
    }
}

@Composable
private fun AuthProviderButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 56.dp),
        shape = RoundedCornerShape(14.dp),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            disabledContainerColor = MaterialTheme.colorScheme.surface,
            disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f)),
    ) {
        icon()
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center)
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
                L10n.text("扫码登录或授权时，Ksuser 需要使用相机实时读取取景框中的二维码内容，用来确认网页端、桌面端或敏感操作请求。相机画面仅用于本次扫码识别，不会用于拍照保存。"),
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

@Composable
private fun LoginAgreementRow(
    accepted: Boolean,
    onAcceptedChange: (Boolean) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val linkColor = MaterialTheme.colorScheme.primary
    val agreementText = remember(linkColor, L10n.language) {
        buildAgreementText(linkColor = linkColor)
    }
    val checkboxLabel = L10n.text("我已阅读并同意相关条款")
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Checkbox(
            checked = accepted,
            onCheckedChange = { if (enabled) onAcceptedChange(it) },
            enabled = enabled,
            modifier = Modifier
                .size(40.dp)
                .testTag("auth-agreement-checkbox")
                .semantics { contentDescription = checkboxLabel },
        )
        Text(
            text = agreementText,
            modifier = Modifier.weight(1f).padding(top = 8.dp).testTag("auth-agreement-links"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun buildAgreementText(
    linkColor: Color,
): AnnotatedString {
    val linkStyle = SpanStyle(
        color = linkColor,
        fontWeight = FontWeight.SemiBold,
        textDecoration = TextDecoration.None,
    )
    val links = listOf(
        L10n.text("服务协议") to L10n.documentationUrl("agreement/user"),
        L10n.text("隐私政策") to L10n.documentationUrl("agreement/privacy"),
        L10n.text("第三方信息共享清单") to L10n.documentationUrl("agreement/third-party-information-sharing"),
    )
    val template = L10n.text("我已阅读并同意 {0} 和 {1}，以及 {2}。")
    return buildAnnotatedString {
        var previousEnd = 0
        Regex("\\{([012])\\}").findAll(template).forEach { match ->
            append(template.substring(previousEnd, match.range.first))
            val (title, url) = links[match.groupValues[1].toInt()]
            appendAgreementLink(title, url, linkStyle)
            previousEnd = match.range.last + 1
        }
        append(template.substring(previousEnd))
    }
}

private fun AnnotatedString.Builder.appendAgreementLink(
    text: String,
    url: String,
    style: SpanStyle,
) {
    withLink(LinkAnnotation.Url(url, styles = TextLinkStyles(style = style))) {
        // Keep short Chinese policy names together without restricting English word wrapping.
        append(if (text.all { it in '\u4E00'..'\u9FFF' }) text.toList().joinToString("\u2060") else text)
    }
}

private enum class RegisterStep {
    Username,
    Password,
    PasswordConfirm,
    Email,
    Code,
}

@Composable
private fun RegisterStepHeader(step: RegisterStep) {
    val title = when (step) {
        RegisterStep.Username -> L10n.text("选择用户名")
        RegisterStep.Password -> L10n.text("设置密码")
        RegisterStep.PasswordConfirm -> L10n.text("确认密码")
        RegisterStep.Email -> L10n.text("输入邮箱")
        RegisterStep.Code -> L10n.text("输入验证码")
    }
    val stepIndex = RegisterStep.entries.indexOf(step) + 1
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.S12),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(8.dp),
            ) {
                Text(
                    "$stepIndex / ${RegisterStep.entries.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(RegisterStep.entries.size) { index ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            if (index < stepIndex) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                        ),
                )
            }
        }
    }
}

@Composable
private fun RegisterStepActions(
    primaryText: String,
    onPrimary: () -> Unit,
    primaryEnabled: Boolean,
    isBusy: Boolean,
    secondaryText: String? = null,
    onSecondary: (() -> Unit)? = null,
    onLogin: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.S8)) {
        if (secondaryText != null && onSecondary != null) {
            OutlinedButton(
                onClick = onSecondary,
                enabled = !isBusy,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 52.dp),
                shape = RoundedCornerShape(AppRadius.R12),
            ) {
                Text(secondaryText)
            }
        }
        SolidPrimaryButton(
            text = primaryText,
            onClick = onPrimary,
            enabled = primaryEnabled,
            isLoading = isBusy,
            modifier = Modifier.weight(1f),
        )
    }
    TextButton(
        onClick = onLogin,
        enabled = !isBusy,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(L10n.text("已有账号，返回登录"))
    }
}

private fun isValidRegisterUsername(username: String): Boolean {
    val trimmed = username.trim()
    return trimmed.length in 3..20 && trimmed.all { char ->
        char == '_' ||
            char == '-' ||
            char.isLetterOrDigit() ||
            Character.UnicodeScript.of(char.code) == Character.UnicodeScript.HAN
    }
}

private fun isValidEmail(email: String): Boolean {
    return Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$").matches(email.trim())
}

private fun parseRegisterStep(raw: String): RegisterStep {
    return RegisterStep.entries.firstOrNull { it.name == raw } ?: RegisterStep.Username
}

private fun isPasswordAllowed(
    password: String,
    requirement: cn.ksuser.auth.data.model.PasswordRequirement?,
): Boolean {
    val req = requirement ?: return password.isNotBlank()
    if (password.length !in req.minLength..req.maxLength) return false
    if (req.requireUppercase && password.none(Char::isUpperCase)) return false
    if (req.requireLowercase && password.none(Char::isLowerCase)) return false
    if (req.requireDigits && password.none(Char::isDigit)) return false
    if (req.requireSpecialChars && password.none { it in "!@#$%^&*(),.?\":{}|<>" }) return false
    return true
}

@Composable
private fun LoginLineField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    isPassword: Boolean = false,
) {
    AppOutlinedField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.heightIn(min = 56.dp),
        singleLine = true,
        label = { Text(label) },
        visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
    )
}

@Composable
private fun SolidPrimaryButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean,
    isLoading: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 52.dp),
        shape = RoundedCornerShape(AppRadius.R12),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp,
            focusedElevation = 0.dp,
            hoveredElevation = 0.dp,
            disabledElevation = 0.dp,
        ),
        colors = appPrimaryButtonColors(),
    ) {
        LoadingButtonContent(text = text, isLoading = isLoading)
    }
}
