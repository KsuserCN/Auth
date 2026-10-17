package cn.ksuser.auth.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.Image
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import cn.ksuser.auth.R
import cn.ksuser.auth.core.settings.L10n
import cn.ksuser.auth.data.AppContainer
import cn.ksuser.auth.data.model.PasskeyAvailability
import cn.ksuser.auth.ui.components.AppOutlinedField
import cn.ksuser.auth.ui.components.AppPagePadding
import cn.ksuser.auth.ui.components.AppRadius
import cn.ksuser.auth.ui.components.AppSpacing
import cn.ksuser.auth.ui.components.GradientPrimaryButton
import cn.ksuser.auth.ui.components.LoadingButtonContent
import cn.ksuser.auth.ui.components.SectionCard
import cn.ksuser.auth.ui.theme.BrandButtonGradientStart
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
    var showAgreementConfirm by rememberSaveable { mutableStateOf(false) }
    var pendingAgreementAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val agreementPrefs = remember(context) {
        context.getSharedPreferences(AGREEMENT_PREFS_NAME, Context.MODE_PRIVATE)
    }
    var agreementAccepted by rememberSaveable {
        mutableStateOf(agreementPrefs.getBoolean(AGREEMENT_ACCEPTED_KEY, false))
    }
    val acceptAgreement = {
        agreementAccepted = true
        agreementPrefs.edit().putBoolean(AGREEMENT_ACCEPTED_KEY, true).apply()
    }
    val runWithAgreement: (() -> Unit) -> Unit = { action ->
        if (agreementAccepted) {
            action()
        } else {
            pendingAgreementAction = action
            showAgreementConfirm = true
        }
    }
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
    val pendingOAuthBind = state.pendingOAuthBind
    val showRegisterFlow = authMode == 1
    LaunchedEffect(pendingOAuthBind?.bindToken) { if (pendingOAuthBind != null) authMode = 1 }
    val passkeyAvailability = remember(container) { container.passkeyManager.availability() }
    val passkeyAvailabilityMessage = remember(container, L10n.language) { container.passkeyManager.availabilityMessage() }
    val qqLoginConfigured = remember(container) { container.qqLoginManager.isConfigured }
    val securityKeyAvailable = activity != null && container.securityKeyManager.available(activity)
    BackHandler(enabled = state.pendingMfa != null) { viewModel.cancelAuthentication() }
    val backgroundBrush = rememberAppBackgroundBrush()
    val density = LocalDensity.current
    val isImeVisible = WindowInsets.ime.getBottom(density) > 0
    val openQrScanner = {
        runWithAgreement {
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
                .padding(AppPagePadding),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.S20),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.S8),
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { showAboutSettings = true }) { Text(L10n.text("关于与设置")) }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.S12)) {
                        Image(
                            painter = painterResource(id = R.drawable.ksuser_launcher_icon),
                            contentDescription = "Ksuser Logo",
                            modifier = Modifier
                                .size(34.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.surface,
                                    shape = RoundedCornerShape(8.dp),
                                )
                                .padding(2.dp),
                        )
                        Text(
                            L10n.text("登录"),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    OutlinedButton(
                        onClick = openQrScanner,
                        enabled = !state.isBusy,
                        shape = RoundedCornerShape(AppRadius.R12),
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier.size(42.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                    ) {
                        Icon(Icons.Outlined.QrCodeScanner, contentDescription = L10n.text("扫码登录/授权"))
                    }
                }
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

            if (pendingOAuthBind != null && state.pendingMfa == null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { authMode = 0 }) { Text(L10n.text("登录已有账号并绑定 QQ")) }
                    TextButton(onClick = { authMode = 1 }) { Text(L10n.text("注册新账号")) }
                    TextButton(onClick = { viewModel.clearPendingOAuthBind() }) { Text(L10n.text("取消绑定")) }
                }
            }
            if (state.pendingMfa != null) {
                SectionCard(modifier = Modifier.fillMaxWidth()) {
                    val challenge = state.pendingMfa!!
                    Text(L10n.text("完成验证"), style = MaterialTheme.typography.titleMedium)
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
                                if (activity == null) {
                                    Toast.makeText(context, L10n.text("当前上下文不支持 Passkey"), Toast.LENGTH_SHORT).show()
                                } else {
                                    runWithAgreement { viewModel.verifyPasskeyMfa(activity) }
                                }
                            },
                            enabled = !state.isBusy,
                            modifier = Modifier.fillMaxWidth(),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(AppRadius.R12),
                        ) {
                            Text(L10n.text("使用 Passkey 完成验证"))
                        }
                    }
                    if ("passkey" in challenge.methods) {
                        OutlinedButton(onClick = { activity?.let { runWithAgreement { viewModel.verifySecurityKeyMfa(it) } } }, enabled = !state.isBusy && securityKeyAvailable, modifier = Modifier.fillMaxWidth()) {
                            Text(L10n.text("使用 USB / NFC 安全密钥"))
                        }
                        if (!securityKeyAvailable) Text(L10n.text("当前设备没有可用的硬件安全密钥提供方"), style = MaterialTheme.typography.bodySmall)
                    }
                    if ("totp" in challenge.methods) {
                    AppOutlinedField(
                        value = mfaCode,
                        onValueChange = { mfaCode = it },
                        label = { Text(if (useRecoveryCode) L10n.text("恢复码") else L10n.text("6 位验证码")) },
                    )
                    GradientPrimaryButton(
                        text = if (state.isBusy) L10n.text("处理中...") else L10n.text("完成验证"),
                        onClick = {
                            if (useRecoveryCode) {
                                viewModel.verifyTotpMfa(recoveryCode = mfaCode.trim().uppercase())
                            } else {
                                viewModel.verifyTotpMfa(code = mfaCode.trim())
                            }
                        },
                        enabled = !state.isBusy && mfaCode.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    }
                    TextButton(onClick = { viewModel.cancelAuthentication() }) { Text(L10n.text("取消")) }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.S16),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(AppSpacing.S12),
                    ) {
                        SectionCard(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                if (pendingOAuthBind != null) L10n.text("注册并绑定 QQ") else L10n.text("账号入口"),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.S8)) {
                                FilterChip(
                                    selected = !showRegisterFlow,
                                    onClick = {
                                        authMode = 0
                                    },
                                    label = { Text(L10n.text("登录")) },
                                )
                                FilterChip(
                                    selected = showRegisterFlow,
                                    onClick = { authMode = 1 },
                                    label = { Text(L10n.text("注册")) },
                                )
                            }
                        }

                        if (!showRegisterFlow) {
                            SectionCard(modifier = Modifier.fillMaxWidth()) {
                                Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.S8)) {
                                    FilterChip(
                                        selected = loginMethod == 0,
                                        onClick = { loginMethod = 0 },
                                        label = { Text(L10n.text("密码登录")) },
                                    )
                                    FilterChip(
                                        selected = loginMethod == 1,
                                        onClick = { loginMethod = 1 },
                                        label = { Text(L10n.text("验证码登录")) },
                                    )
                                }
                                LoginLineField(
                                    value = email,
                                    onValueChange = { email = it },
                                    label = L10n.text("邮箱"),
                                )
                                when (loginMethod) {
                                    0 -> {
                                        LoginLineField(
                                            value = password,
                                            onValueChange = { password = it },
                                            label = L10n.text("密码"),
                                            isPassword = true,
                                        )
                                        SolidPrimaryButton(
                                            text = L10n.text("继续"),
                                            onClick = {
                                                runWithAgreement {
                                                    viewModel.passwordLogin(email.trim(), password)
                                                }
                                            },
                                            enabled = !state.isBusy && email.isNotBlank() && password.isNotBlank(),
                                            isLoading = state.isBusy,
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                        LoginAgreementNotice()
                                    }

                                    else -> {
                                        Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.S8)) {
                                            LoginLineField(
                                                value = code,
                                                onValueChange = { code = it },
                                                label = L10n.text("验证码"),
                                                modifier = Modifier.weight(1f),
                                            )
                                            OutlinedButton(
                                                onClick = {
                                                    runWithAgreement {
                                                        viewModel.sendLoginCode(email.trim())
                                                    }
                                                },
                                                enabled = !state.isBusy && email.isNotBlank(),
                                                modifier = Modifier.height(56.dp),
                                                shape = RoundedCornerShape(AppRadius.R12),
                                            ) {
                                                LoadingButtonContent(text = L10n.text("发送"), isLoading = state.isBusy)
                                            }
                                        }
                                        SolidPrimaryButton(
                                            text = L10n.text("继续"),
                                            onClick = {
                                                runWithAgreement {
                                                    viewModel.loginWithCode(email.trim(), code.trim())
                                                }
                                            },
                                            enabled = !state.isBusy && email.isNotBlank() && code.isNotBlank(),
                                            isLoading = state.isBusy,
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                        LoginAgreementNotice()
                                    }
                                }
                            }
                        } else {
                            SectionCard(modifier = Modifier.fillMaxWidth()) {
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
                                            label = L10n.text("用户名"),
                                        )
                                        RegisterStepActions(
                                            primaryText = L10n.text("下一步"),
                                            onPrimary = {
                                                runWithAgreement {
                                                    viewModel.checkUsernameAndThen(registerUsername.trim()) {
                                                        registerStep = RegisterStep.Password.name
                                                    }
                                                }
                                            },
                                            primaryEnabled = !state.isBusy && isValidRegisterUsername(registerUsername),
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
                                            label = L10n.text("邮箱"),
                                        )
                                        Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.S8)) {
                                            OutlinedButton(
                                                onClick = { registerStep = RegisterStep.PasswordConfirm.name },
                                                enabled = !state.isBusy,
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(46.dp),
                                                shape = RoundedCornerShape(AppRadius.R12),
                                            ) {
                                                Text(L10n.text("返回"))
                                            }
                                            Button(
                                                onClick = {
                                                    runWithAgreement {
                                                        viewModel.sendRegisterCode(registerEmail.trim()) {
                                                            registerStep = RegisterStep.Code.name
                                                        }
                                                    }
                                                },
                                                enabled = !state.isBusy && isValidEmail(registerEmail),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(46.dp),
                                                shape = RoundedCornerShape(AppRadius.R12),
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = BrandButtonGradientStart,
                                                    contentColor = MaterialTheme.colorScheme.onPrimary,
                                                ),
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
                                                modifier = Modifier.weight(1f),
                                            )
                                            OutlinedButton(
                                                onClick = {
                                                    runWithAgreement {
                                                        viewModel.sendRegisterCode(registerEmail.trim())
                                                    }
                                                },
                                                enabled = !state.isBusy && isValidEmail(registerEmail),
                                                modifier = Modifier.height(56.dp),
                                                shape = RoundedCornerShape(AppRadius.R12),
                                            ) {
                                                LoadingButtonContent(text = L10n.text("重发"), isLoading = state.isBusy)
                                            }
                                        }
                                        RegisterStepActions(
                                            primaryText = if (pendingOAuthBind != null) L10n.text("注册并绑定 QQ") else L10n.text("创建账号"),
                                            onPrimary = {
                                                runWithAgreement {
                                                    viewModel.register(
                                                        username = registerUsername.trim(),
                                                        email = registerEmail.trim(),
                                                        password = registerPassword,
                                                        code = registerCode.trim(),
                                                    )
                                                }
                                            },
                                            primaryEnabled = !state.isBusy &&
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

                                LoginAgreementNotice()
                            }
                        }
                    }

                    if (!showRegisterFlow && !isImeVisible) {
                        SectionCard(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                L10n.text("快捷登录"),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            OutlinedButton(
                                onClick = {
                                    if (activity == null) {
                                        Toast.makeText(context, L10n.text("当前上下文不支持 QQ 登录"), Toast.LENGTH_SHORT).show()
                                    } else {
                                        runWithAgreement { viewModel.loginWithQq(activity) }
                                    }
                                },
                                enabled = !state.isBusy && qqLoginConfigured,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(AppRadius.R12),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                ) {
                                    if (state.isBusy) {
                                        androidx.compose.material3.CircularProgressIndicator(
                                            modifier = Modifier.size(14.dp),
                                            strokeWidth = 2.dp,
                                        )
                                        Spacer(modifier = Modifier.width(AppSpacing.S8))
                                    }
                                    Image(
                                        painter = painterResource(id = R.drawable.qq_symbol),
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(modifier = Modifier.width(AppSpacing.S8))
                                    Text(L10n.text("使用 QQ 登录"))
                                }
                            }
                            if (!qqLoginConfigured) {
                                Text(
                                    L10n.text("QQ 登录未配置"),
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            Text(
                                L10n.text("使用通行密钥(Passkey)登录"),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            if (passkeyAvailability != PasskeyAvailability.Available) {
                                Text(
                                    passkeyAvailabilityMessage,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            OutlinedButton(
                                onClick = {
                                    if (activity == null) {
                                        Toast.makeText(context, L10n.text("当前上下文不支持 Passkey"), Toast.LENGTH_SHORT).show()
                                    } else {
                                        runWithAgreement { viewModel.loginWithPasskey(activity) }
                                    }
                                },
                                enabled = !state.isBusy && passkeyAvailability == PasskeyAvailability.Available,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(AppRadius.R12),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                ) {
                                    if (state.isBusy) {
                                        androidx.compose.material3.CircularProgressIndicator(
                                            modifier = Modifier.size(14.dp),
                                            strokeWidth = 2.dp,
                                        )
                                        Spacer(modifier = Modifier.width(AppSpacing.S8))
                                    }
                                    Icon(Icons.Outlined.Key, contentDescription = null)
                                    Spacer(modifier = Modifier.width(AppSpacing.S8))
                                    Text(L10n.text("使用通行密钥(Passkey)登录"))
                                }
                            }
                            OutlinedButton(onClick = { activity?.let { runWithAgreement { viewModel.loginWithSecurityKey(it) } } }, enabled = !state.isBusy && securityKeyAvailable, modifier = Modifier.fillMaxWidth()) {
                                Text(L10n.text("使用 USB / NFC 安全密钥"))
                            }
                            if (!securityKeyAvailable) Text(L10n.text("当前设备没有可用的硬件安全密钥提供方"), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }

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

    if (showAgreementConfirm) {
        AgreementConfirmDialog(
            onDismiss = {
                showAgreementConfirm = false
                pendingAgreementAction = null
            },
            onConfirm = {
                showAgreementConfirm = false
                acceptAgreement()
                pendingAgreementAction?.invoke()
                pendingAgreementAction = null
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
private fun AgreementConfirmDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(L10n.text("请先阅读并同意")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.S8)) {
                Text(L10n.text("登录或注册即代表您同意服务协议与隐私政策"))
                Text(L10n.text("使用第三方登录，即代表您已阅读并同意第三方信息共享清单"))
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(L10n.text("同意并继续")) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(L10n.text("取消")) }
        },
    )
}

@Composable
private fun LoginAgreementNotice(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val linkColor = MaterialTheme.colorScheme.primary
    val agreementText = remember(linkColor, L10n.language) {
        buildAgreementText(
            linkColor = linkColor,
        )
    }
    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    Text(
        text = agreementText,
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(context, textLayoutResult) {
                detectTapGestures { offset ->
                    val layoutResult = textLayoutResult ?: return@detectTapGestures
                    val position = layoutResult.getOffsetForPosition(offset)
                    val annotation = agreementText
                        .getStringAnnotations(AGREEMENT_LINK_TAG, position, position)
                        .firstOrNull()
                        ?: return@detectTapGestures
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(annotation.item)))
                }
            },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        onTextLayout = { textLayoutResult = it },
    )
}

private fun buildAgreementText(
    linkColor: Color,
): AnnotatedString {
    val linkStyle = SpanStyle(
        color = linkColor,
        fontWeight = FontWeight.SemiBold,
        textDecoration = TextDecoration.None,
    )
    return buildAnnotatedString {
        append(L10n.text("使用登录、注册或扫码等功能即代表您同意")); append(" ")
        appendAgreementLink(L10n.text("服务协议"), L10n.documentationUrl("agreement/user"), linkStyle)
        append(" "); append(L10n.text("与")); append(" ")
        appendAgreementLink(L10n.text("隐私政策"), L10n.documentationUrl("agreement/privacy"), linkStyle)
        append(L10n.text("\n使用第三方登录，即代表您已阅读并同意")); append(" ")
        appendAgreementLink(L10n.text("第三方信息共享清单"), L10n.documentationUrl("agreement/third-party-information-sharing"), linkStyle)
    }
}

private fun AnnotatedString.Builder.appendAgreementLink(
    text: String,
    url: String,
    style: SpanStyle,
) {
    val start = length
    pushStringAnnotation(AGREEMENT_LINK_TAG, url)
    withStyle(style) {
        append(text)
    }
    pop()
    addStyle(style, start, length)
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
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
    )
    Text(
        "$stepIndex / ${RegisterStep.entries.size}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
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
                    .height(46.dp),
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
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
        singleLine = true,
        label = { Text(label) },
        visualTransformation = if (isPassword) {
            PasswordVisualTransformation()
        } else {
            VisualTransformation.None
        },
        shape = RoundedCornerShape(0.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent,
            focusedIndicatorColor = MaterialTheme.colorScheme.primary,
            unfocusedIndicatorColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
        ),
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
        modifier = modifier.height(46.dp),
        shape = RoundedCornerShape(AppRadius.R12),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp,
            focusedElevation = 0.dp,
            hoveredElevation = 0.dp,
            disabledElevation = 0.dp,
        ),
        colors = ButtonDefaults.buttonColors(
            containerColor = BrandButtonGradientStart,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            disabledContainerColor = BrandButtonGradientStart.copy(alpha = 0.45f),
            disabledContentColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.65f),
        ),
    ) {
        LoadingButtonContent(text = text, isLoading = isLoading)
    }
}

private const val USER_AGREEMENT_URL = "https://docs.ksuser.cn/agreement/user"
private const val PRIVACY_POLICY_URL = "https://docs.ksuser.cn/agreement/privacy"
private const val THIRD_PARTY_INFORMATION_SHARING_URL =
    "https://docs.ksuser.cn/agreement/third-party-information-sharing"
private const val AGREEMENT_PREFS_NAME = "login_agreement"
private const val AGREEMENT_ACCEPTED_KEY = "accepted"
private const val AGREEMENT_LINK_TAG = "agreement_url"
