package cn.ksuser.auth.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import androidx.compose.ui.layout.ContentScale

object AppSpacing {
    val S8: Dp = 8.dp
    val S12: Dp = 12.dp
    val S16: Dp = 16.dp
    val S20: Dp = 20.dp
    val S24: Dp = 24.dp
    val S32: Dp = 32.dp
}

object AppRadius {
    val R8: Dp = 8.dp
    val R12: Dp = 12.dp
    val R16: Dp = 16.dp
    val R20: Dp = 20.dp
    val R24: Dp = 24.dp
}

val AppPagePadding = AppSpacing.S20

@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(AppSpacing.S16),
    contentSpacing: Dp = AppSpacing.S12,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(AppRadius.R20),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(contentSpacing),
        ) {
            content()
        }
    }
}

@Composable
fun BrandHeroCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(AppRadius.R24),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(AppSpacing.S24),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.S12),
        ) {
            content()
        }
    }
}

@Composable
fun AppOutlinedField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: @Composable (() -> Unit)? = null,
    singleLine: Boolean = true,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    visualTransformation: androidx.compose.ui.text.input.VisualTransformation = androidx.compose.ui.text.input.VisualTransformation.None,
    keyboardOptions: androidx.compose.foundation.text.KeyboardOptions = androidx.compose.foundation.text.KeyboardOptions.Default,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp),
        label = label,
        singleLine = singleLine,
        shape = RoundedCornerShape(AppRadius.R16),
        textStyle = textStyle,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
            focusedLabelColor = MaterialTheme.colorScheme.primary,
            unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            cursorColor = MaterialTheme.colorScheme.primary,
        ),
    )
}

@Composable
fun LoadingButtonContent(
    text: String,
    isLoading: Boolean,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = androidx.compose.material3.LocalContentColor.current,
                strokeWidth = 2.dp,
            )
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(AppSpacing.S8))
        }
        Text(text)
    }
}

@Composable
fun GradientPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 52.dp),
        shape = RoundedCornerShape(AppRadius.R16),
        colors = appPrimaryButtonColors(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
    ) { Text(text, style = MaterialTheme.typography.labelLarge) }
}

@Composable
fun appPrimaryButtonColors() = ButtonDefaults.buttonColors(
    containerColor = cn.ksuser.auth.ui.theme.BrandGold,
    contentColor = cn.ksuser.auth.ui.theme.BrandOnGold,
    disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
    disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
)

@Composable
fun LoadingButton(
    text: String,
    onClick: suspend () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }
    Button(
        onClick = {
            if (isLoading) return@Button
            scope.launch {
                isLoading = true
                try {
                    onClick()
                } finally {
                    isLoading = false
                }
            }
        },
        enabled = enabled && !isLoading,
        modifier = modifier.heightIn(min = 52.dp),
        shape = RoundedCornerShape(AppRadius.R16),
        colors = appPrimaryButtonColors(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
    ) {
        LoadingButtonContent(text = text, isLoading = isLoading)
    }
}

@Composable
fun LoadingOutlinedButton(
    text: String,
    onClick: suspend () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }
    OutlinedButton(
        onClick = {
            if (isLoading) return@OutlinedButton
            scope.launch {
                isLoading = true
                try {
                    onClick()
                } finally {
                    isLoading = false
                }
            }
        },
        enabled = enabled && !isLoading,
        modifier = modifier.heightIn(min = 52.dp),
        shape = RoundedCornerShape(AppRadius.R16),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
    ) {
        LoadingButtonContent(text = text, isLoading = isLoading)
    }
}

@Composable
fun LoadingTextButton(
    text: String,
    onClick: suspend () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }
    TextButton(
        onClick = {
            if (isLoading) return@TextButton
            scope.launch {
                isLoading = true
                try {
                    onClick()
                } finally {
                    isLoading = false
                }
            }
        },
        enabled = enabled && !isLoading,
        modifier = modifier.heightIn(min = 48.dp),
    ) {
        LoadingButtonContent(text = text, isLoading = isLoading)
    }
}


/** A full-row target keeps grouped settings readable at larger font scales. */
@Composable
fun AppSettingsRow(
    title: String,
    modifier: Modifier = Modifier,
    value: String? = null,
    icon: ImageVector? = null,
    leadingContent: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val target = if (onClick != null) modifier.clickable(role = Role.Button, onClick = onClick) else modifier
    Row(
        target.fillMaxWidth().heightIn(min = 72.dp).padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (leadingContent != null) {
            leadingContent()
        } else if (icon != null) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(AppRadius.R12)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(21.dp))
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (!value.isNullOrBlank()) Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (onClick != null) {
            Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** Shows an application logo when available and a consistent fallback while loading or on failure. */
@Composable
fun ApplicationLogo(
    name: String,
    logoUrl: String?,
    size: Dp = 40.dp,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(AppRadius.R12)
    androidx.compose.material3.Surface(
        modifier = modifier.size(size),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.primary,
    ) {
        if (logoUrl.isNullOrBlank()) {
            ApplicationLogoFallback(size * 0.5f)
        } else {
            SubcomposeAsyncImage(
                model = logoUrl,
                contentDescription = "$name 应用图标",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(5.dp),
                loading = { ApplicationLogoFallback(size * 0.5f) },
                error = { ApplicationLogoFallback(size * 0.5f) },
                success = { SubcomposeAsyncImageContent() },
            )
        }
    }
}

@Composable
private fun ApplicationLogoFallback(iconSize: Dp) {
    Box(contentAlignment = Alignment.Center) {
        Icon(Icons.Outlined.Apps, contentDescription = null, modifier = Modifier.size(iconSize))
    }
}

@Composable
fun AppSectionTitle(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, bottom = 10.dp),
    )
}
