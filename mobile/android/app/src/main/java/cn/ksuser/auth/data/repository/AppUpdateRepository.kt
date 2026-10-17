package cn.ksuser.auth.data.repository

import android.app.Activity
import android.content.Context
import cn.ksuser.auth.core.app.AppIdentityProvider
import cn.ksuser.auth.core.env.KsuserEnvironment
import cn.ksuser.auth.data.model.AppUpdateInfo
import cn.ksuser.auth.data.model.AppUpdateManifest
import cn.ksuser.auth.core.update.ApkUpdatePolicy
import cn.ksuser.auth.core.update.AppUpdateDownloads
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
            .url(ApkUpdatePolicy.requireHttpsUrl(environment.updateManifestUrl))
            .header("Accept", "application/json")
            .header("User-Agent", AppIdentityProvider.userAgent())
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("检查更新失败: HTTP ${response.code}")
            }
            val source = response.body?.source() ?: error("更新清单为空")
            source.request(64 * 1024L + 1)
            require(source.buffer.size <= 64 * 1024) { "更新清单过大" }
            val manifest = gson.fromJson(source.readUtf8(), AppUpdateManifest::class.java) ?: error("更新清单为空")
            require(manifest.versionCode > 0 && !manifest.versionName.isNullOrBlank()) { "更新版本无效" }
            ApkUpdatePolicy.requireHttpsUrl(manifest.apkUrl)
            val sha256 = ApkUpdatePolicy.requireSha256(manifest.sha256)
            AppUpdateInfo(
                latestVersionCode = manifest.versionCode,
                latestVersionName = manifest.versionName,
                apkUrl = manifest.apkUrl,
                sha256 = sha256,
                releaseNotes = manifest.releaseNotes,
                forceUpdate = manifest.forceUpdate,
                isUpdateAvailable = manifest.versionCode > currentVersionCode,
            )
        }
    }

    fun enqueueDownload(update: AppUpdateInfo): Long = AppUpdateDownloads(context).enqueue(update)

    fun resumePendingInstall(activity: Activity) = AppUpdateDownloads(context).resumePendingInstall(activity)

    fun hasVerifiedUpdate(): Boolean = AppUpdateDownloads(context).hasVerifiedUpdate()

    fun retryVerifiedInstall(activity: Activity): Boolean = AppUpdateDownloads(context).retryVerifiedInstall(activity)
}
