package cn.ksuser.auth.core.update

import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import cn.ksuser.auth.BuildConfig
import cn.ksuser.auth.R
import cn.ksuser.auth.data.model.AppUpdateInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.lang.ref.WeakReference
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/** Persists one requested download so process death cannot bypass verification. */
class AppUpdateDownloads(private val context: Context) {
    private val preferences = context.getSharedPreferences("app_update_download", Context.MODE_PRIVATE)
    private val downloads = context.getSystemService(DownloadManager::class.java)

    fun enqueue(update: AppUpdateInfo): Long = synchronized(lock) {
        ApkUpdatePolicy.requireHttpsUrl(update.apkUrl)
        val hash = ApkUpdatePolicy.requireSha256(update.sha256)
        require(update.isUpdateAvailable && update.latestVersionCode > 0 && update.latestVersionName.isNotBlank()) { "No valid update is available" }
        val old = pending()
        if (old != null && old.version == update.latestVersionCode && old.sha256 == hash) {
            if (old.status == DOWNLOADING) return@synchronized old.id
            if (old.status in retryableStatuses && verifiedFile(old.name).isFile) {
                preferences.edit().putString("status", VERIFIED).commit()
                continueInForeground()
                return@synchronized old.id
            }
        }
        val name = "${UUID.randomUUID()}.apk"
        val target = downloadFile(name)
        target.parentFile?.mkdirs()
        val request = DownloadManager.Request(Uri.parse(update.apkUrl))
            .setTitle("Ksuser Auth ${update.latestVersionName}")
            .setDescription(context.getString(R.string.update_downloading))
            .setMimeType("application/vnd.android.package-archive")
            // The raw, unverified download must never expose an installer action.
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            .setDestinationUri(Uri.fromFile(target))
            .setAllowedOverMetered(true).setAllowedOverRoaming(false)
        val id = downloads.enqueue(request)
        val saved = preferences.edit().clear().putLong("id", id).putString("name", name)
            .putString("sha256", hash).putLong("version", update.latestVersionCode).putString("status", DOWNLOADING).commit()
        if (!saved) { downloads.remove(id); target.delete(); error("Unable to persist update download") }
        old?.let { downloads.remove(it.id); deleteFiles(it.name) }
        id
    }

    fun processCompletion(id: Long) = synchronized(lock) {
        val current = pending() ?: return@synchronized
        if (current.id != id || current.status != DOWNLOADING) return@synchronized
        downloads.query(DownloadManager.Query().setFilterById(id)).use { cursor ->
            if (cursor == null || !cursor.moveToFirst()) { fail(current); return@synchronized }
            when (cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))) {
                DownloadManager.STATUS_SUCCESSFUL -> {
                    try {
                        // Move into private storage before verification to avoid mutable shared-storage input.
                        val source = downloadFile(current.name)
                        val privateFile = verifiedFile(current.name)
                        privateFile.parentFile?.mkdirs()
                        source.copyTo(privateFile, overwrite = true)
                        ApkDownloadVerifier(context).verify(privateFile, current.sha256, current.version)
                        source.delete()
                        preferences.edit().putString("status", VERIFIED).commit()
                        continueInForeground()
                    } catch (_: Exception) { fail(current) }
                }
                DownloadManager.STATUS_FAILED -> fail(current)
                else -> Unit
            }
        }
    }

    fun resumePendingInstall(activity: Activity) {
        foregroundActivity = WeakReference(activity)
        if (!resuming.compareAndSet(false, true)) return
        scope.launch {
            try {
                val current = withContext(Dispatchers.IO) {
                    pending()?.also { if (it.status == DOWNLOADING) processCompletion(it.id) }
                    pending()
                } ?: return@launch
                if (current.version <= BuildConfig.VERSION_CODE.toLong()) {
                    deleteFiles(current.name)
                    preferences.edit().clear().apply()
                    return@launch
                }
                if (current.status == FAILED) {
                    Toast.makeText(activity, R.string.update_failed_body, Toast.LENGTH_LONG).show()
                    preferences.edit().clear().apply()
                    return@launch
                }
                if (current.status != VERIFIED && current.status != WAITING_PERMISSION) return@launch
                if (activity.isFinishing || activity.isDestroyed) return@launch
                if (Build.VERSION.SDK_INT >= 26 && !context.packageManager.canRequestPackageInstalls()) {
                    if (current.status == WAITING_PERMISSION) {
                        preferences.edit().putString("status", DECLINED).apply()
                        Toast.makeText(activity, R.string.update_retry_install, Toast.LENGTH_LONG).show()
                        return@launch
                    }
                    preferences.edit().putString("status", WAITING_PERMISSION).apply()
                    Toast.makeText(activity, R.string.update_allow_install, Toast.LENGTH_LONG).show()
                    activity.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")))
                    return@launch
                }
                // Recheck the final private file on every attempt, including after returning from Settings.
                withContext(Dispatchers.IO) { ApkDownloadVerifier(context).verify(verifiedFile(current.name), current.sha256, current.version) }
                if (pending()?.id != current.id) return@launch
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", verifiedFile(current.name))
                val install = Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    .putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, false)
                preferences.edit().putString("status", OPENED).apply()
                activity.startActivity(install)
            } catch (_: Exception) {
                pending()?.let { fail(it) }
                Toast.makeText(activity, R.string.update_install_unavailable, Toast.LENGTH_LONG).show()
            } finally { resuming.set(false) }
        }
    }

    fun hasVerifiedUpdate(): Boolean = synchronized(lock) {
        val current = pending() ?: return@synchronized false
        current.version > BuildConfig.VERSION_CODE.toLong() && current.status in retryableStatuses && verifiedFile(current.name).isFile
    }

    /** Explicit user action after installer cancellation or unknown-source permission denial. */
    fun retryVerifiedInstall(activity: Activity): Boolean {
        val canRetry = synchronized(lock) {
            if (!hasVerifiedUpdate()) return@synchronized false
            preferences.edit().putString("status", VERIFIED).commit()
        }
        if (canRetry) resumePendingInstall(activity)
        return canRetry
    }

    private fun continueInForeground() {
        scope.launch {
            val activity = foregroundActivity.get() ?: return@launch
            val lifecycle = (activity as? LifecycleOwner)?.lifecycle ?: return@launch
            if (!activity.isFinishing && !activity.isDestroyed && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                resumePendingInstall(activity)
            }
        }
    }

    private fun fail(current: PendingDownload) = synchronized(lock) {
        if (pending()?.id != current.id) return@synchronized
        deleteFiles(current.name)
        preferences.edit().putString("status", FAILED).commit()
    }

    private fun pending(): PendingDownload? {
        val id = preferences.getLong("id", -1)
        val name = preferences.getString("name", null) ?: return null
        if (id < 0 || !name.matches(Regex("[a-fA-F0-9-]{36}\\.apk"))) return null
        val hash = preferences.getString("sha256", null) ?: return null
        return PendingDownload(id, name, hash, preferences.getLong("version", -1), preferences.getString("status", DOWNLOADING) ?: DOWNLOADING)
    }

    private fun downloadFile(name: String): File = File(
        context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: error("Update storage unavailable"), "updates/$name")
    private fun verifiedFile(name: String): File = File(context.filesDir, "updates/$name")
    private fun deleteFiles(name: String) { runCatching { downloadFile(name).delete() }; verifiedFile(name).delete() }

    private data class PendingDownload(val id: Long, val name: String, val sha256: String, val version: Long, val status: String)
    companion object {
        private val lock = Any()
        private val resuming = AtomicBoolean(false)
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        private var foregroundActivity = WeakReference<Activity>(null)
        private const val DOWNLOADING = "DOWNLOADING"
        private const val VERIFIED = "VERIFIED"
        private const val WAITING_PERMISSION = "WAITING_PERMISSION"
        private const val OPENED = "OPENED"
        private const val DECLINED = "DECLINED"
        private const val FAILED = "FAILED"
        private val retryableStatuses = setOf(VERIFIED, WAITING_PERMISSION, OPENED, DECLINED)
    }
}
