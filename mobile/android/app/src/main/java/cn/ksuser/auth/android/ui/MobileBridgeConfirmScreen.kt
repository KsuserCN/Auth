package cn.ksuser.auth.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cn.ksuser.auth.android.data.model.UserProfile
import cn.ksuser.auth.android.ui.components.AppPagePadding
import cn.ksuser.auth.android.ui.components.AppRadius
import cn.ksuser.auth.android.ui.components.AppSpacing
import cn.ksuser.auth.android.ui.components.GradientPrimaryButton
import cn.ksuser.auth.android.ui.components.SectionCard
import cn.ksuser.auth.android.ui.theme.BrandButtonGradientEnd
import cn.ksuser.auth.android.ui.theme.BrandButtonGradientStart
import cn.ksuser.auth.android.ui.theme.rememberAppBackgroundBrush

@Composable
internal fun MobileBridgeConfirmScreen(
    pending: PendingMobileBridgeConfirmation,
    currentUser: UserProfile?,
    isBusy: Boolean,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    onContinueToLogin: () -> Unit,
) {
    val backgroundBrush = rememberAppBackgroundBrush()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundBrush)
            .safeDrawingPadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = AppPagePadding, vertical = AppSpacing.S12),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.S16),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onCancel) {
                    Icon(Icons.Outlined.Close, contentDescription = "关闭")
                }

                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "网页登录确认",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = if (pending.requiresLogin) "需要先登录 App" else "确认在浏览器中登录",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                Spacer(modifier = Modifier.width(48.dp))
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                shape = RoundedCornerShape(30.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                tonalElevation = 4.dp,
                shadowElevation = 10.dp,
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = Brush.linearGradient(
                        listOf(
                            BrandButtonGradientStart.copy(alpha = 0.45f),
                            BrandButtonGradientEnd.copy(alpha = 0.20f),
                        ),
                    ),
                ),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.S16),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(AppSpacing.S16),
                    ) {
                        MobileBridgeHero(
                            requiresLogin = pending.requiresLogin,
                            returnOrigin = pending.returnOrigin,
                        )

                        SectionCard(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(AppSpacing.S8),
                            ) {
                                Icon(
                                    imageVector = if (pending.requiresLogin) {
                                        Icons.Outlined.PhoneAndroid
                                    } else {
                                        Icons.Outlined.Language
                                    },
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                Text(
                                    text = if (pending.requiresLogin) {
                                        "需要先在当前 App 内登录，再重新从浏览器发起一次登录请求。"
                                    } else {
                                        "浏览器已发起网页登录请求，确认后网页会自动建立自己的新会话。"
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        SectionCard(modifier = Modifier.fillMaxWidth()) {
                            Text("目标站点", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(AppSpacing.S8),
                            ) {
                                Icon(
                                    Icons.Outlined.Public,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                Text(
                                    text = pending.returnOrigin ?: "当前 Ksuser 网页",
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                            }
                        }

                        if (!pending.requiresLogin && currentUser != null) {
                            SectionCard(modifier = Modifier.fillMaxWidth()) {
                                Text("当前 App 账号", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                Text(currentUser.username, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    currentUser.email,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.S8)) {
                        if (pending.requiresLogin) {
                            GradientPrimaryButton(
                                text = "去登录",
                                onClick = onContinueToLogin,
                                enabled = !isBusy,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } else {
                            GradientPrimaryButton(
                                text = if (isBusy) "确认中..." else "确认网页登录",
                                onClick = onConfirm,
                                enabled = !isBusy,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }

                        OutlinedButton(
                            onClick = onCancel,
                            enabled = !isBusy,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(AppRadius.R12),
                        ) {
                            Text(if (pending.requiresLogin) "关闭" else "取消请求")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MobileBridgeHero(
    requiresLogin: Boolean,
    returnOrigin: String?,
) {
    val icon = if (requiresLogin) Icons.Outlined.PhoneAndroid else Icons.Outlined.Language
    val accent = MaterialTheme.colorScheme.primary
    val title = if (requiresLogin) "需要先登录 App" else "确认网页登录请求"
    val subtitle = if (requiresLogin) {
        "请先在当前 App 完成账号登录，然后回到浏览器重新发起请求。"
    } else {
        "确认后网页端会创建新会话，不会影响当前 App 内的登录状态。"
    }
    val operationLabel = if (requiresLogin) "登录后继续" else "网页登录"

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = Color.Transparent,
    ) {
        Box(
            modifier = Modifier
                .background(
                    Brush.linearGradient(
                        listOf(
                            accent.copy(alpha = 0.14f),
                            MaterialTheme.colorScheme.surface,
                        ),
                    ),
                    shape = RoundedCornerShape(24.dp),
                )
                .padding(horizontal = 18.dp, vertical = 20.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(AppSpacing.S8),
            ) {
                Surface(
                    modifier = Modifier.size(64.dp),
                    shape = CircleShape,
                    color = accent.copy(alpha = 0.14f),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(30.dp),
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = accent.copy(alpha = 0.12f),
                ) {
                    Text(
                        text = operationLabel,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = accent,
                    )
                }

                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = returnOrigin ?: "来源于当前 Ksuser 网页",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
