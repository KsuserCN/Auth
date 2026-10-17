package cn.ksuser.auth.core.update

import java.io.File
import java.net.URI
import java.security.MessageDigest

data class ApkIdentity(
    val packageName: String,
    val versionCode: Long,
    val currentSigners: Set<String>,
    val signerHistory: Set<String> = currentSigners,
)

/** Pure update trust checks, shared by the downloader and local unit tests. */
object ApkUpdatePolicy {
    fun requireHttpsUrl(value: String): String {
        val uri = URI(value)
        require(uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank() &&
            uri.userInfo == null && uri.fragment == null) { "Update URL must be an HTTPS URL without credentials" }
        return value
    }

    fun requireSha256(value: String?): String {
        require(value != null && value.matches(Regex("[0-9a-fA-F]{64}"))) { "Update SHA-256 is missing or invalid" }
        return value.lowercase(java.util.Locale.ROOT)
    }

    fun verifyHash(file: File, expected: String) {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        require(MessageDigest.isEqual(actual.toByteArray(), requireSha256(expected).toByteArray())) { "Update SHA-256 mismatch" }
    }

    fun verifyIdentity(installed: ApkIdentity, candidate: ApkIdentity, expectedVersionCode: Long, supportsRotation: Boolean) {
        require(candidate.packageName == installed.packageName) { "Update package name does not match" }
        require(candidate.versionCode == expectedVersionCode && candidate.versionCode > installed.versionCode) { "Update version does not match" }
        require(installed.currentSigners.isNotEmpty() && candidate.currentSigners.isNotEmpty()) { "Update signing certificate is missing" }
        val compatible = if (!supportsRotation || installed.currentSigners.size > 1 || candidate.currentSigners.size > 1) {
            candidate.currentSigners == installed.currentSigners
        } else {
            candidate.signerHistory.containsAll(installed.currentSigners)
        }
        require(compatible) { "Update signing certificate does not match" }
    }
}
