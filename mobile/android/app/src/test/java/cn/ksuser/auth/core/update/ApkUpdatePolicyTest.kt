package cn.ksuser.auth.core.update

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.security.MessageDigest

class ApkUpdatePolicyTest {
    private val installed = ApkIdentity("cn.ksuser.auth", 12, setOf("release-certificate"))

    @Test fun validatesMetadataAndRejectsInsecureOrCredentialBearingDownloadUrls() {
        assertEquals("https://auth.ksuser.cn/downloads/latest/android.apk", ApkUpdatePolicy.requireHttpsUrl("https://auth.ksuser.cn/downloads/latest/android.apk"))
        for (url in listOf("http://auth.ksuser.cn/android.apk", "https://user:pass@auth.ksuser.cn/android.apk", "file:///android.apk", "https://auth.ksuser.cn/android.apk#fragment")) {
            assertThrows(IllegalArgumentException::class.java) { ApkUpdatePolicy.requireHttpsUrl(url) }
        }
        for (hash in listOf(null, "", "abcd", "g".repeat(64))) {
            assertThrows(IllegalArgumentException::class.java) { ApkUpdatePolicy.requireSha256(hash) }
        }
        assertEquals("a".repeat(64), ApkUpdatePolicy.requireSha256("A".repeat(64)))
    }

    @Test fun fileDigestDetectsCorruptOrReplacedDownloads() {
        val file = File.createTempFile("ksuser-update-", ".apk")
        try {
            file.writeText("final signed APK content")
            val hash = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }
            ApkUpdatePolicy.verifyHash(file, hash)
            file.appendText("tampered")
            assertThrows(IllegalArgumentException::class.java) { ApkUpdatePolicy.verifyHash(file, hash) }
        } finally { file.delete() }
    }

    @Test fun rejectsOtherPackagesDowngradesManifestVersionMismatchAndUnknownSigners() {
        val candidate = installed.copy(versionCode = 13)
        ApkUpdatePolicy.verifyIdentity(installed, candidate, 13, true)
        for (invalid in listOf(candidate.copy(packageName = "another.app"), candidate.copy(versionCode = 12),
            candidate.copy(versionCode = 14), candidate.copy(currentSigners = emptySet(), signerHistory = emptySet()),
            candidate.copy(currentSigners = setOf("untrusted"), signerHistory = setOf("untrusted")))) {
            assertThrows(IllegalArgumentException::class.java) { ApkUpdatePolicy.verifyIdentity(installed, invalid, 13, true) }
        }
    }

    @Test fun honorsVerifiedSignerRotationOnlyOnSupportedDevicesAndRequiresExactMultiSignerMatch() {
        val rotated = ApkIdentity("cn.ksuser.auth", 13, setOf("new-release-certificate"), setOf("release-certificate", "new-release-certificate"))
        ApkUpdatePolicy.verifyIdentity(installed, rotated, 13, true)
        assertThrows(IllegalArgumentException::class.java) { ApkUpdatePolicy.verifyIdentity(installed, rotated, 13, false) }
        val multi = installed.copy(currentSigners = setOf("first", "second"))
        ApkUpdatePolicy.verifyIdentity(multi, multi.copy(versionCode = 13), 13, true)
        assertThrows(IllegalArgumentException::class.java) { ApkUpdatePolicy.verifyIdentity(multi,
            multi.copy(versionCode = 13, currentSigners = setOf("first")), 13, true) }
    }
}
