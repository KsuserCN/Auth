package cn.ksuser.auth.ui.theme

import android.os.Build
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.ui.graphics.luminance
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import cn.ksuser.auth.ui.settings.LocalAppPreferences

private val DarkColorScheme = darkColorScheme(
    primary = GoldPrimaryDark,
    onPrimary = GoldOnPrimaryDark,
    surfaceTint = GoldPrimaryDark,
    primaryContainer = GoldPrimaryContainerDark,
    onPrimaryContainer = GoldOnPrimaryContainerDark,
    secondary = GoldSecondaryDark,
    onSecondary = GoldOnSecondaryDark,
    secondaryContainer = GoldSecondaryContainerDark,
    onSecondaryContainer = GoldOnSecondaryContainerDark,
    tertiary = GoldTertiaryDark,
    onTertiary = GoldOnTertiaryDark,
    background = GoldBackgroundDark,
    onBackground = GoldOnSurfaceDark,
    surface = GoldSurfaceDark,
    surfaceDim = GoldSurfaceDimDark,
    surfaceBright = GoldSurfaceBrightDark,
    surfaceContainerLowest = GoldSurfaceContainerLowestDark,
    surfaceContainerLow = GoldSurfaceContainerLowDark,
    surfaceContainer = GoldSurfaceContainerDark,
    surfaceContainerHigh = GoldSurfaceContainerHighDark,
    surfaceContainerHighest = GoldSurfaceContainerHighestDark,
    onSurface = GoldOnSurfaceDark,
    surfaceVariant = GoldSurfaceVariantDark,
    onSurfaceVariant = GoldOnSurfaceVariantDark,
    outline = GoldOutlineDark,
    outlineVariant = GoldOutlineVariantDark,
    error = GoldErrorDark,
)

private val LightColorScheme = lightColorScheme(
    primary = GoldPrimaryLight,
    onPrimary = GoldOnPrimaryLight,
    surfaceTint = GoldPrimaryLight,
    primaryContainer = GoldPrimaryContainerLight,
    onPrimaryContainer = GoldOnPrimaryContainerLight,
    secondary = GoldSecondaryLight,
    onSecondary = GoldOnSecondaryLight,
    secondaryContainer = GoldSecondaryContainerLight,
    onSecondaryContainer = GoldOnSecondaryContainerLight,
    tertiary = GoldTertiaryLight,
    onTertiary = GoldOnTertiaryLight,
    background = GoldBackgroundLight,
    onBackground = GoldOnSurfaceLight,
    surface = GoldSurfaceLight,
    surfaceDim = GoldSurfaceDimLight,
    surfaceBright = GoldSurfaceBrightLight,
    surfaceContainerLowest = GoldSurfaceContainerLowestLight,
    surfaceContainerLow = GoldSurfaceContainerLowLight,
    surfaceContainer = GoldSurfaceContainerLight,
    surfaceContainerHigh = GoldSurfaceContainerHighLight,
    surfaceContainerHighest = GoldSurfaceContainerHighestLight,
    onSurface = GoldOnSurfaceLight,
    surfaceVariant = GoldSurfaceVariantLight,
    onSurfaceVariant = GoldOnSurfaceVariantLight,
    outline = GoldOutlineLight,
    outlineVariant = GoldOutlineVariantLight,
    error = GoldErrorLight,
)

@Composable
fun KsuserAuthAndroidTheme(
    darkTheme: Boolean = when (LocalAppPreferences.current.theme) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    },
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    val activity = remember(view) { view.context.themeActivity() }
    if (!view.isInEditMode && activity != null) {
        SideEffect {
            val lightBars = colorScheme.background.luminance() > 0.5f
            WindowCompat.getInsetsController(activity.window, view).apply {
                isAppearanceLightStatusBars = lightBars
                isAppearanceLightNavigationBars = lightBars
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}

@Composable
fun rememberAppBackgroundBrush(): Brush {
    val colorScheme = MaterialTheme.colorScheme
    return remember(colorScheme.background) {
        SolidColor(colorScheme.background)
    }
}

private tailrec fun Context.themeActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.takeIf { it !== this }?.themeActivity()
    else -> null
}
