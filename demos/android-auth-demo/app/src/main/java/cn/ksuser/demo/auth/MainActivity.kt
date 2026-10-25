package cn.ksuser.demo.auth

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import cn.ksuser.auth.sdk.KsuserAuthorizationMode
import kotlinx.coroutines.launch

/** Native equivalent of the iOS Demo's configuration, actions and result form. */
class MainActivity : ComponentActivity() {
    private lateinit var model: DemoLoginModel
    private lateinit var oauth: RadioButton
    private lateinit var official: RadioButton
    private lateinit var modes: RadioGroup
    private lateinit var clientId: TextView
    private lateinit var callback: TextView
    private lateinit var scopes: TextView
    private lateinit var configurationHint: TextView
    private lateinit var authorize: Button
    private lateinit var resume: Button
    private lateinit var web: Button
    private lateinit var cancel: Button
    private lateinit var progress: LinearLayout
    private lateinit var progressText: TextView
    private lateinit var message: TextView
    private lateinit var error: TextView
    private lateinit var identity: LinearLayout
    private lateinit var identityValue: TextView
    private lateinit var union: LinearLayout
    private lateinit var unionValue: TextView
    private lateinit var unionHint: TextView
    private lateinit var expiry: LinearLayout
    private lateinit var expiryValue: TextView
    private var rendering = false

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_KsuserAuthDemo)
        super.onCreate(savedInstanceState)
        model = ViewModelProvider(this)[DemoLoginModel::class.java]
        setContentView(form())
        render(model.state.value)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) { model.state.collect(::render) }
        }
        handleCallback(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleCallback(intent)
    }

    private fun handleCallback(incoming: Intent?) {
        val uri = incoming?.data ?: return
        model.callback(uri, this)
        // The SDK securely saves an accepted result. Do not retain its query in the task Intent.
        incoming.data = null
    }

    private fun form(): View {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(28))
        }
        content.addView(text(getString(R.string.demo_title), 26f).apply { setTypeface(typeface, Typeface.BOLD) })
        content.addView(text(getString(R.string.demo_subtitle), 14f, muted = true))
        val config = section(content, R.string.section_configuration)
        modes = RadioGroup(this).apply { orientation = RadioGroup.HORIZONTAL }
        oauth = RadioButton(this).apply { id = View.generateViewId(); text = getString(R.string.mode_oauth); textSize = 14f; minHeight = dp(48) }
        official = RadioButton(this).apply { id = View.generateViewId(); text = getString(R.string.mode_official); textSize = 14f; minHeight = dp(48) }
        modes.addView(oauth, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        modes.addView(official, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        config.addView(modes)
        modes.setOnCheckedChangeListener { _, id ->
            if (!rendering) model.selectMode(if (id == oauth.id) KsuserAuthorizationMode.OAUTH else KsuserAuthorizationMode.OFFICIAL)
        }
        clientId = field(config, R.string.field_client_id).second.apply { typeface = Typeface.MONOSPACE }
        callback = field(config, R.string.field_callback).second
        scopes = field(config, R.string.field_scopes).second
        configurationHint = text("", 13f, muted = true).also(config::addView)
        config.addView(text(getString(R.string.local_configuration_only), 12f, muted = true))

        val actions = section(content, R.string.section_actions)
        authorize = action(actions, R.string.action_authorize, primary = true) { model.authorize(this) }
        resume = action(actions, R.string.action_resume) { model.resume(this) }
        web = action(actions, R.string.action_web) { model.resume(this, web = true) }
        cancel = action(actions, R.string.action_cancel) { model.cancel() }

        val result = section(content, R.string.section_result)
        progress = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        progress.addView(ProgressBar(this).apply { isIndeterminate = true }, LinearLayout.LayoutParams(dp(22), dp(22)).apply { marginEnd = dp(12) })
        progressText = text("", 14f).also(progress::addView)
        result.addView(progress)
        message = text("", 15f).apply { setTextIsSelectable(true) }.also(result::addView)
        error = text("", 14f).apply { setTextColor(getColor(R.color.demo_error)); setTextIsSelectable(true) }.also(result::addView)
        field(result, R.string.field_identity).also { identity = it.first; identityValue = it.second }
        field(result, R.string.field_union_id).also { union = it.first; unionValue = it.second }
        unionHint = text(getString(R.string.union_id_unavailable), 13f, muted = true).also(result::addView)
        field(result, R.string.field_expiry).also { expiry = it.first; expiryValue = it.second }
        content.addView(text(getString(R.string.no_tokens_notice), 12f, muted = true))

        return ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(getColor(R.color.demo_background))
            addView(content)
            setOnApplyWindowInsetsListener { view, insets ->
                if (Build.VERSION.SDK_INT >= 30) {
                    val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                    view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                } else {
                    @Suppress("DEPRECATION")
                    view.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop, insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
                }
                insets
            }
        }
    }

    private fun render(state: DemoLoginState) {
        rendering = true
        modes.check(if (state.mode == KsuserAuthorizationMode.OAUTH) oauth.id else official.id)
        oauth.isEnabled = state.canSelectMode
        official.isEnabled = state.canSelectMode
        rendering = false
        clientId.text = DemoConfiguration.clientId(state.mode)
        callback.text = DemoConfiguration.redirectUri
        scopes.text = DemoConfiguration.scopes(state.mode).joinToString(" ")
        configurationHint.text = getString(if (state.configured) R.string.configuration_ready else R.string.configuration_missing)
        authorize.isEnabled = state.canAuthorize
        resume.isEnabled = state.canResume
        web.isEnabled = state.canContinueWeb
        cancel.isEnabled = state.canCancel
        progress.visibility = if (state.waiting || state.cancelling || state.continuingWeb) View.VISIBLE else View.GONE
        progressText.setText(when {
            state.cancelling -> R.string.progress_cancelling
            state.continuingWeb -> R.string.progress_web
            else -> R.string.progress_waiting
        })
        message.setText(when (state.status) {
            DemoStatus.NEEDS_CONFIGURATION -> R.string.status_configuration
            DemoStatus.READY -> R.string.status_ready
            DemoStatus.MODE_CHANGED -> R.string.status_mode_changed
            DemoStatus.WAITING -> R.string.status_waiting
            DemoStatus.RESUMING -> R.string.status_resuming
            DemoStatus.NO_PENDING -> R.string.status_no_pending
            DemoStatus.CANCELLING -> R.string.progress_cancelling
            DemoStatus.CANCELLED -> R.string.status_cancelled
            DemoStatus.OAUTH_SUCCESS -> R.string.status_oauth_success
            DemoStatus.OFFICIAL_SUCCESS -> R.string.status_official_success
            DemoStatus.FAILED -> R.string.status_incomplete
        })
        error.visibility = if (state.failure == null) View.GONE else View.VISIBLE
        error.text = state.failure?.let(::describeFailure).orEmpty()
        identity.visibility = if (state.identity.isNullOrBlank()) View.GONE else View.VISIBLE
        identityValue.text = state.identity.orEmpty()
        val oauthResult = state.mode == KsuserAuthorizationMode.OAUTH && !state.identity.isNullOrBlank()
        union.visibility = if (oauthResult && !state.unionId.isNullOrBlank()) View.VISIBLE else View.GONE
        unionValue.text = state.unionId.orEmpty()
        unionHint.visibility = if (oauthResult && state.unionId.isNullOrBlank()) View.VISIBLE else View.GONE
        expiry.visibility = if (state.expiresIn == null) View.GONE else View.VISIBLE
        expiryValue.text = state.expiresIn?.let {
            resources.getQuantityString(R.plurals.expiry_seconds, it.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), it)
        }.orEmpty()
    }

    private fun describeFailure(details: DemoFailureDetails): String {
        val description = getString(when (details.kind) {
            DemoFailureKind.CONFIGURATION -> R.string.error_configuration
            DemoFailureKind.IN_PROGRESS -> R.string.error_in_progress
            DemoFailureKind.CANCELLED -> R.string.status_cancelled
            DemoFailureKind.EXPIRED -> R.string.error_expired
            DemoFailureKind.CALLBACK -> R.string.error_callback
            DemoFailureKind.RESPONSE -> R.string.error_response
            DemoFailureKind.ID_TOKEN -> R.string.error_id_token
            DemoFailureKind.STORAGE -> R.string.error_storage
            DemoFailureKind.NETWORK -> R.string.error_network
            DemoFailureKind.LAUNCH -> R.string.error_launch
            DemoFailureKind.ACCESS_DENIED -> R.string.error_denied
            DemoFailureKind.SERVICE -> R.string.error_service
            DemoFailureKind.UNEXPECTED -> R.string.error_unexpected
        })
        val diagnostic = listOfNotNull(details.httpStatus?.let { "HTTP $it" }, details.serverCode).joinToString(" · ")
        return if (diagnostic.isEmpty()) description else "$description\n$diagnostic"
    }

    private fun section(parent: LinearLayout, title: Int): LinearLayout {
        parent.addView(text(getString(title), 14f, muted = true).apply {
            setPadding(dp(4), dp(24), 0, dp(8)); setTypeface(typeface, Typeface.BOLD)
        })
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
            background = GradientDrawable().apply { setColor(getColor(R.color.demo_card)); cornerRadius = dp(16).toFloat() }
            parent.addView(this, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
    }
    private fun field(parent: LinearLayout, title: Int): Pair<LinearLayout, TextView> {
        val row = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(8), 0, dp(8)) }
        row.addView(text(getString(title), 12f, muted = true))
        val value = text("", 14f).apply { setTextIsSelectable(true) }
        row.addView(value)
        parent.addView(row)
        return row to value
    }
    private fun text(value: String, size: Float, muted: Boolean = false) = TextView(this).apply {
        text = value; textSize = size
        setTextColor(getColor(if (muted) R.color.demo_secondary else R.color.demo_text))
        setPadding(0, dp(4), 0, dp(6))
    }
    private fun action(parent: LinearLayout, title: Int, primary: Boolean = false, action: () -> Unit): Button = Button(this).apply {
        setText(title); isAllCaps = false; minHeight = dp(48)
        if (primary) {
            backgroundTintList = ColorStateList(arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()),
                intArrayOf(getColor(R.color.demo_disabled), getColor(R.color.demo_accent)))
            setTextColor(ColorStateList(arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()),
                intArrayOf(getColor(R.color.demo_secondary), getColor(R.color.demo_on_accent))))
        }
        setOnClickListener { action() }
        parent.addView(this, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
