package cn.ksuser.auth.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.NavGraph.Companion.findStartDestination
import cn.ksuser.auth.core.settings.L10n

internal fun navigateToMainDestination(navController: NavHostController, destination: MainDestination) {
    navController.navigate(destination.route) {
        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/** Presentation only: authentication and navigation state remain in MainShell. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MainNavigationFrame(
    currentRoute: String,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onScan: () -> Unit,
    onNavigate: (MainDestination) -> Unit,
    onOpenSettings: () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    val destination = MainDestination.entries.firstOrNull { it.route == currentRoute }
    val title = destination?.label ?: when {
        currentRoute.startsWith("profile/edit") -> "编辑资料"
        currentRoute == "profile/about" -> "设置"
        currentRoute == "profile/authorized-apps" -> "已授权应用"
        else -> "Ksuser安全"
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        if (destination != null) Text(
                            L10n.text("Ksuser安全"),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 0.sp,
                        )
                        Text(
                            L10n.text(title),
                            modifier = if (currentRoute == "profile/about") Modifier.testTag("settings_title") else Modifier,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                navigationIcon = {
                    if (destination == null) IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, L10n.text("返回"))
                    }
                },
                actions = {
                    if (destination != null) Row(
                        modifier = Modifier.padding(end = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        IconButton(onClick = onScan, modifier = Modifier.testTag("open_scanner")) {
                            Icon(
                                Icons.Outlined.QrCodeScanner,
                                L10n.text("扫码授权"),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                        IconButton(
                            onClick = onOpenSettings,
                            modifier = Modifier.testTag("open_settings")
                                .semantics { contentDescription = L10n.text("打开设置") },
                        ) {
                            Icon(
                                Icons.Outlined.Settings,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            if (destination != null) Surface(
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                shadowElevation = 6.dp,
                tonalElevation = 0.dp,
            ) {
                BoxWithConstraints {
                    val compactLabels = maxWidth < 400.dp && LocalDensity.current.fontScale > 1.15f
                    NavigationBar(
                        modifier = Modifier.testTag("main_navigation"),
                        containerColor = Color.Transparent,
                        tonalElevation = 0.dp,
                    ) {
                        MainDestination.entries.forEach { item ->
                            val selected = item == destination
                            NavigationBarItem(
                                modifier = Modifier.testTag("nav_${item.route}"),
                                selected = selected,
                                onClick = { onNavigate(item) },
                                icon = {
                                    Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                                        Icon(item.icon, contentDescription = null, modifier = Modifier.size(22.dp))
                                    }
                                },
                                label = {
                                    Text(
                                        L10n.text(item.label),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = if (compactLabels) 11.sp else MaterialTheme.typography.labelSmall.fontSize,
                                        letterSpacing = 0.sp,
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                            )
                        }
                    }
                }
            }
        },
        content = content,
    )
}
