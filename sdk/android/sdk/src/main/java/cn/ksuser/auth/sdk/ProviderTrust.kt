package cn.ksuser.auth.sdk

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.security.MessageDigest

/** Package names alone are not an authentication boundary for app-to-app sign-in. */
internal object ProviderTrust {
    val productionPins: Set<String> = setOf(
        "92:4D:95:F0:A6:40:57:32:91:E9:A8:83:91:ED:85:52:E4:2A:4A:B5:98:43:12:F7:FA:75:53:70:5F:34:0F:AB",
        "D8:FE:BE:29:A8:8C:67:97:FD:EA:B6:42:6F:C2:BC:A5:EF:B0:76:C2:01:66:2C:FC:92:83:F0:70:B4:FA:A7:10",
    ).map(::normalizeFingerprint).toSet()

    fun normalizeFingerprint(value: String): String {
        val trimmed = value.trim()
        if (!Regex("(?:[A-Fa-f0-9]{64}|(?:[A-Fa-f0-9]{2}:){31}[A-Fa-f0-9]{2})").matches(trimmed)) {
            throw KsuserAuthException(KsuserAuthErrorCode.INVALID_CONFIGURATION)
        }
        return trimmed.replace(":", "").uppercase(java.util.Locale.ROOT)
    }

    fun fingerprint(certificate: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(certificate).joinToString("") { "%02X".format(it) }

    fun matches(pins: Set<String>, signatures: List<String>, multipleSigners: Boolean): Boolean {
        if (pins.isEmpty() || signatures.isEmpty()) return false
        // For an OS-verified rotation lineage a trusted historical signer is sufficient.
        // Concurrent signers (including legacy GET_SIGNATURES) must all be trusted.
        return if (multipleSigners) signatures.all { it in pins } else signatures.any { it in pins }
    }

    fun canLaunch(context: Context, configuration: KsuserAuthConfiguration): Boolean =
        isTrustedPackage(context, "cn.ksuser.auth", configuration.providerPins)

    @Suppress("DEPRECATION")
    fun isTrustedPackage(context: Context, packageName: String, pins: Set<String>): Boolean {
        if (pins.isEmpty()) return false
        return try {
            if (Build.VERSION.SDK_INT >= 28) {
                val info = context.packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo ?: return false
                val multiple = info.hasMultipleSigners()
                val signatures = if (multiple) info.apkContentsSigners else info.signingCertificateHistory
                matches(pins, signatures?.map { fingerprint(it.toByteArray()) } ?: emptyList(), multiple)
            } else {
                val signatures = context.packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES).signatures
                matches(pins, signatures?.map { fingerprint(it.toByteArray()) } ?: emptyList(), true)
            }
        } catch (_: Exception) { false }
    }
}
