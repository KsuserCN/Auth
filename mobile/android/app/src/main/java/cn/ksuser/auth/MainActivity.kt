package cn.ksuser.auth

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cn.ksuser.auth.ui.KsuserAuthApp
import cn.ksuser.auth.ui.theme.KsuserAuthAndroidTheme

class MainActivity : ComponentActivity() {
    private var pendingDeepLink by mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingDeepLink = intent?.data
        enableEdgeToEdge()
        setContent {
            KsuserAuthAndroidTheme {
                KsuserAuthApp(
                    incomingDeepLink = pendingDeepLink,
                    onDeepLinkConsumed = { pendingDeepLink = null },
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

    private var qqActivityResultHandler: ((Int, Int, Intent?) -> Boolean)? = null
    private var qqNewIntentHandler: ((Intent) -> Unit)? = null

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
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
