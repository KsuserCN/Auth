package cn.ksuser.auth.core.update

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import java.io.File
import java.security.MessageDigest

class ApkDownloadVerifier(private val context: Context) {
    @Suppress("DEPRECATION")
    fun verify(file: File, sha256: String, versionCode: Long) {
        require(file.isFile && file.length() > 0) { "Downloaded update is missing" }
        ApkUpdatePolicy.verifyHash(file, sha256)
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val installed = context.packageManager.getPackageInfo(context.packageName, flags)
        val candidate = context.packageManager.getPackageArchiveInfo(file.absolutePath, flags)
            ?: error("Downloaded file is not a valid APK")
        ApkUpdatePolicy.verifyIdentity(identity(installed), identity(candidate), versionCode, Build.VERSION.SDK_INT >= 28)
    }

    @Suppress("DEPRECATION")
    private fun identity(info: PackageInfo): ApkIdentity {
        val current = if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners.orEmpty() else info.signatures.orEmpty()
        val history = if (Build.VERSION.SDK_INT >= 28 && info.signingInfo?.hasMultipleSigners() == false) {
            info.signingInfo?.signingCertificateHistory.orEmpty()
        } else current
        return ApkIdentity(
            packageName = info.packageName,
            versionCode = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong(),
            currentSigners = current.map { fingerprint(it.toByteArray()) }.toSet(),
            signerHistory = history.map { fingerprint(it.toByteArray()) }.toSet(),
        )
    }

    private fun fingerprint(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }
}
