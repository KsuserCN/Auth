package cn.ksuser.auth

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cn.ksuser.auth.ui.KsuserAuthApp
import cn.ksuser.auth.ui.settings.ProvideAppPreferences
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import cn.ksuser.auth.core.settings.L10n
import cn.ksuser.auth.ui.theme.KsuserAuthAndroidTheme

class MainActivity : ComponentActivity() {
    private var pendingDeepLink by mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingDeepLink = intent?.data
        handleAppIntent(intent)
        configureShortcuts()
        enableEdgeToEdge()
        setContent {
            ProvideAppPreferences(container.preferences) {
              LaunchedEffect(L10n.language) { configureShortcuts() }
              KsuserAuthAndroidTheme {
                KsuserAuthApp(
                    incomingDeepLink = pendingDeepLink,
                    onDeepLinkConsumed = {
                        // Native requests have encrypted recovery state. Do not resurrect a completed
                        // authorization from the task's original Intent after a later process restart.
                        if (pendingDeepLink?.path == "/app/sdk-authorize" && intent.data == pendingDeepLink) intent.data = null
                        pendingDeepLink = null
                    },
                    onExitApp = { finishAffinity() },
                    onRegisterActivityResultHandler = { handler ->
                        qqActivityResultHandler = handler
                    },
                    onRegisterNewIntentHandler = { handler ->
                        qqNewIntentHandler = handler
                    },
                )
              }
            }
        }
    }

    private val container get() = (application as KsuserAuthApplication).appContainer

    override fun onResume() {
        super.onResume()
        container.preferences.refreshSystemLanguage()
        container.appUpdateRepository.resumePendingInstall(this)
    }

    private fun handleAppIntent(intent: Intent?) {
        intent?.getStringExtra("destination")?.let(container::requestNavigation)
    }

    private fun configureShortcuts() {
        val entries = listOf("scan" to "扫码", "security" to "安全", "sessions" to "会话", "profile" to "资料")
        ShortcutManagerCompat.setDynamicShortcuts(this, entries.map { (id, title) ->
            ShortcutInfoCompat.Builder(this, id).setShortLabel(L10n.text(title))
                .setIcon(IconCompat.createWithResource(this, R.drawable.ic_launcher_foreground))
                .setIntent(Intent(this, MainActivity::class.java).setAction(Intent.ACTION_VIEW).putExtra("destination", id)).build()
        })
    }

    private var qqActivityResultHandler: ((Int, Int, Intent?) -> Boolean)? = null
    private var qqNewIntentHandler: ((Intent) -> Unit)? = null

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAppIntent(intent)
        container.appUpdateRepository.resumePendingInstall(this)
        qqNewIntentHandler?.invoke(intent)
        pendingDeepLink = intent.data
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (qqActivityResultHandler?.invoke(requestCode, resultCode, data) == true) {
            return
        }
        super.onActivityResult(requestCode, resultCode, data)
    }
}
