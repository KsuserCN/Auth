package cn.ksuser.auth.android.ui

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import cn.ksuser.auth.android.R
import cn.ksuser.auth.android.data.AppContainer
import cn.ksuser.auth.android.data.model.PasskeyAvailability
import cn.ksuser.auth.android.ui.components.AppOutlinedField
import cn.ksuser.auth.android.ui.components.AppPagePadding
import cn.ksuser.auth.android.ui.components.AppRadius
import cn.ksuser.auth.android.ui.components.AppSpacing
import cn.ksuser.auth.android.ui.components.GradientPrimaryButton
import cn.ksuser.auth.android.ui.components.LoadingButtonContent
import cn.ksuser.auth.android.ui.components.SectionCard
import cn.ksuser.auth.android.ui.theme.BrandButtonGradientStart
import cn.ksuser.auth.android.ui.theme.rememberAppBackgroundBrush
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
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            showQrScanner = true
        } else {
            Toast.makeText(context, "相机权限被拒绝，无法扫码", Toast.LENGTH_SHORT).show()
        }
    }
    var loginMethod by rememberSaveable { mutableStateOf(0) }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    var mfaCode by rememberSaveable { mutableStateOf("") }
    var useRecoveryCode by rememberSaveable { mutableStateOf(false) }
    val passkeyAvailability = remember(container) { container.passkeyManager.availability() }
    val passkeyAvailabilityMessage = remember(container) { container.passkeyManager.availabilityMessage() }
    val backgroundBrush = rememberAppBackgroundBrush()
    val density = LocalDensity.current
    val isImeVisible = WindowInsets.ime.getBottom(density) > 0
    val openQrScanner = {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            showQrScanner = true
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
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
                Spacer(modifier = Modifier.height(18.dp))
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
                            "登录",
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
                        Icon(Icons.Outlined.QrCodeScanner, contentDescription = "扫码登录/授权")
                    }
                }
                Text(
                    if (state.pendingMfa != null) {
                        "继续完成账号验证"
                    } else {
                        "使用你的 Ksuser 账号继续。手机端当前仅开放登录，不提供注册入口。"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (state.pendingMfa != null) {
                SectionCard(modifier = Modifier.fillMaxWidth()) {
                    val challenge = state.pendingMfa!!
                    Text("完成验证", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "第一因子已通过，请完成额外验证后继续登录。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text("可用方式: ${challenge.methods.joinToString()}", style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.S8)) {
                        FilterChip(
                            selected = !useRecoveryCode,
                            onClick = { useRecoveryCode = false },
                            label = { Text("TOTP") },
                        )
                        FilterChip(
                            selected = useRecoveryCode,
                            onClick = { useRecoveryCode = true },
                            label = { Text("恢复码") },
                        )
                    }
                    if (challenge.methods.contains("passkey")) {
                        OutlinedButton(
                            onClick = {
                                if (activity == null) {
                                    Toast.makeText(context, "当前上下文不支持 Passkey", Toast.LENGTH_SHORT).show()
                                } else {
                                    viewModel.verifyPasskeyMfa(activity)
                                }
                            },
                            enabled = !state.isBusy,
                            modifier = Modifier.fillMaxWidth(),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(AppRadius.R12),
                        ) {
                            Text("使用 Passkey 完成验证")
                        }
                    }
                    AppOutlinedField(
                        value = mfaCode,
                        onValueChange = { mfaCode = it },
                        label = { Text(if (useRecoveryCode) "恢复码" else "6 位验证码") },
                    )
                    GradientPrimaryButton(
                        text = if (state.isBusy) "处理中..." else "完成验证",
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
                                "账号登录",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.S8)) {
                                FilterChip(
                                    selected = loginMethod == 0,
                                    onClick = { loginMethod = 0 },
                                    label = { Text("密码登录") },
                                )
                                FilterChip(
                                    selected = loginMethod == 1,
                                    onClick = { loginMethod = 1 },
                                    label = { Text("验证码登录") },
                                )
                            }
                        }

                        SectionCard(modifier = Modifier.fillMaxWidth()) {
                            LoginLineField(
                                value = email,
                                onValueChange = { email = it },
                                label = "邮箱",
                            )
                            when (loginMethod) {
                                0 -> {
                                    LoginLineField(
                                        value = password,
                                        onValueChange = { password = it },
                                        label = "密码",
                                    )
                                SolidPrimaryButton(
                                    text = "继续",
                                    onClick = { viewModel.passwordLogin(email.trim(), password) },
                                    enabled = !state.isBusy && email.isNotBlank() && password.isNotBlank(),
                                    isLoading = state.isBusy,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                }

                                else -> {
                                    Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.S8)) {
                                        LoginLineField(
                                            value = code,
                                            onValueChange = { code = it },
                                            label = "验证码",
                                            modifier = Modifier.weight(1f),
                                        )
                                        OutlinedButton(
                                            onClick = { viewModel.sendLoginCode(email.trim()) },
                                            enabled = !state.isBusy && email.isNotBlank(),
                                            modifier = Modifier.height(56.dp),
                                            shape = RoundedCornerShape(AppRadius.R12),
                                        ) {
                                            LoadingButtonContent(text = "发送", isLoading = state.isBusy)
                                        }
                                    }
                                SolidPrimaryButton(
                                    text = "继续",
                                    onClick = { viewModel.loginWithCode(email.trim(), code.trim()) },
                                    enabled = !state.isBusy && email.isNotBlank() && code.isNotBlank(),
                                    isLoading = state.isBusy,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                }
                            }
                        }
                    }

                    if (!isImeVisible) {
                        SectionCard(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                "使用通行密钥(Passkey)登录",
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
                                        Toast.makeText(context, "当前上下文不支持 Passkey", Toast.LENGTH_SHORT).show()
                                    } else {
                                        viewModel.loginWithPasskey(activity)
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
                                Text("使用通行密钥(Passkey)登录")
                            }
                        }
                    }
                }
            }
            }
        }
    }

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
}

@Composable
private fun LoginLineField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
        singleLine = true,
        label = { Text(label) },
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
