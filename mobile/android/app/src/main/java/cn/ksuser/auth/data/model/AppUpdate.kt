package cn.ksuser.auth.data.model

import com.google.gson.annotations.SerializedName

data class AppUpdateManifest(
    @SerializedName("versionCode")
    val versionCode: Long,
    @SerializedName("versionName")
    val versionName: String,
    @SerializedName("apkUrl")
    val apkUrl: String,
    @SerializedName("sha256")
    val sha256: String? = null,
    @SerializedName("releaseNotes")
    val releaseNotes: String? = null,
    @SerializedName("forceUpdate")
    val forceUpdate: Boolean = false,
)

data class AppUpdateInfo(
    val latestVersionCode: Long,
    val latestVersionName: String,
    val apkUrl: String,
    val sha256: String?,
    val releaseNotes: String?,
    val forceUpdate: Boolean,
    val isUpdateAvailable: Boolean,
)
