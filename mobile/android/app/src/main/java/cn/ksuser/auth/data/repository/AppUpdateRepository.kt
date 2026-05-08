package cn.ksuser.auth.data.repository

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import cn.ksuser.auth.core.app.AppIdentityProvider
import cn.ksuser.auth.core.env.KsuserEnvironment
import cn.ksuser.auth.data.model.AppUpdateInfo
import cn.ksuser.auth.data.model.AppUpdateManifest
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class AppUpdateRepository(
    private val context: Context,
    private val environment: KsuserEnvironment,
    private val client: OkHttpClient,
    private val gson: Gson,
) {
    suspend fun checkForUpdate(): AppUpdateInfo = withContext(Dispatchers.IO) {
        val currentVersionCode = AppIdentityProvider.current(context).versionCode
        val request = Request.Builder()
            .url(environment.updateManifestUrl)
            .header("Accept", "application/json")
            .header("User-Agent", AppIdentityProvider.userAgent())
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("检查更新失败: HTTP ${response.code}")
            }
            val body = response.body?.string().orEmpty()
            val manifest = gson.fromJson(body, AppUpdateManifest::class.java)
            AppUpdateInfo(
                latestVersionCode = manifest.versionCode,
                latestVersionName = manifest.versionName,
                apkUrl = manifest.apkUrl,
                sha256 = manifest.sha256,
                releaseNotes = manifest.releaseNotes,
                forceUpdate = manifest.forceUpdate,
                isUpdateAvailable = manifest.versionCode > currentVersionCode,
            )
        }
    }

    fun enqueueDownload(update: AppUpdateInfo): Long {
        val downloadManager = context.getSystemService(DownloadManager::class.java)
        val fileName = "ksuser-auth-${update.latestVersionName}.apk"
        val request = DownloadManager.Request(Uri.parse(update.apkUrl))
            .setTitle("Ksuser Auth ${update.latestVersionName}")
            .setDescription("正在下载新版安装包")
            .setMimeType("application/vnd.android.package-archive")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)

        return downloadManager.enqueue(request)
    }
}
