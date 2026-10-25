package cn.ksuser.auth.sdk

import android.content.Context
import android.net.Uri
import android.content.pm.PackageManager
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EncryptedTransactionStoreTest {
    private lateinit var context: Context
    private lateinit var storage: EncryptedTransactionStore
    private lateinit var file: File
    private val configuration = KsuserAuthConfiguration("client", KsuserAuthorizationMode.OAUTH,
        "https://client.example/callback")

    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext()
        storage = EncryptedTransactionStore(context)
        file = File(context.noBackupFilesDir, "ksuser-auth-pending-v1")
        storage.clear()
    }

    @After fun cleanup() { storage.clear() }

    private fun transaction(): PendingTransaction = PendingTransaction(configuration.identity, configuration.redirectUri,
        "private-verifier".padEnd(43, 'v'), "s".repeat(43), "nonce-secret",
        NativeCreated("t".repeat(32), "c".repeat(32),
            "https://auth.ksuser.cn/app/sdk-authorize?ticket=" + "t".repeat(32),
            "https://auth.ksuser.cn/app/sdk-authorize/web?ticket=" + "t".repeat(32), 300),
        System.currentTimeMillis() + 300_000,
        tokens = KsuserTokenSet("private-access-token", "Bearer", 300))

    @Test fun encryptedAtomicStorageSurvivesNewStoreAndIsExcludedFromBackup() {
        val expected = transaction()
        storage.save(expected)
        assertTrue(file.exists())
        assertEquals(context.noBackupFilesDir.canonicalPath, file.parentFile!!.canonicalPath)
        assertFalse(File(context.filesDir, file.name).exists())
        val disk = file.readBytes().toString(Charsets.ISO_8859_1)
        assertFalse(disk.contains(expected.verifier))
        assertFalse(disk.contains(expected.state))
        assertFalse(disk.contains(expected.created.cancelSecret))
        assertFalse(disk.contains("private-access-token"))
        val recovered = EncryptedTransactionStore(context).load()!!
        assertEquals(expected.verifier, recovered.verifier)
        assertEquals(expected.configurationId, recovered.configurationId)
        assertEquals("private-access-token", recovered.tokens!!.accessToken)
        storage.clear()
        assertNull(EncryptedTransactionStore(context).load())
        assertFalse(file.exists())
    }

    @Test fun encryptionUsesFreshNonceAndRejectsTampering() {
        storage.save(transaction())
        val first = file.readBytes()
        storage.save(transaction())
        val second = file.readBytes()
        assertFalse(first.copyOfRange(1, 13).contentEquals(second.copyOfRange(1, 13)))
        second[second.lastIndex] = (second.last().toInt() xor 1).toByte()
        file.writeBytes(second)
        val error = runCatching { storage.load() }.exceptionOrNull() as KsuserAuthException
        assertEquals(KsuserAuthErrorCode.STORAGE, error.code)
        assertEquals("STORAGE", error.message)
        storage.clear()
        assertNull(storage.load())
    }

    @Test fun separateClientCannotConsumeAnotherConfigurationOrUnrelatedAndroidUri() {
        storage.save(transaction())
        val wrong = KsuserAuthClient(context, configuration.copy(clientId = "different-client"))
        val matching = KsuserAuthClient(context, configuration)
        val callback = Uri.parse(configuration.redirectUri + "?code=once&state=" + "s".repeat(43))
        assertFalse(wrong.handleCallback(callback))
        assertFalse(matching.handleCallback(Uri.parse("https://evil.example/callback?code=once&state=" + "s".repeat(43))))
        // Existing saved tokens are not replaced by even a correctly routed duplicate callback.
        assertTrue(matching.handleCallback(callback))
        assertEquals("private-access-token", storage.load()!!.tokens!!.accessToken)
        assertNull(storage.load()!!.code)
    }

    @Test fun applicationDefaultProcessIsRequired() {
        ProcessGuard.requireDefaultProcess(context)
        val error = runCatching {
            ProcessGuard.requireMatch(context.applicationInfo.processName, context.applicationInfo.processName + ":remote")
        }.exceptionOrNull() as KsuserAuthException
        assertEquals(KsuserAuthErrorCode.INVALID_CONFIGURATION, error.code)
    }

    @Suppress("DEPRECATION")
    @Test fun packageTrustChecksTheActualInstalledSigningCertificate() {
        val info = context.packageManager.getPackageInfo(context.packageName,
            if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES)
        val signatures = if (Build.VERSION.SDK_INT >= 28) info.signingInfo!!.apkContentsSigners else info.signatures!!
        val actualPins = signatures.map { ProviderTrust.fingerprint(it.toByteArray()) }.toSet()
        assertTrue(ProviderTrust.isTrustedPackage(context, context.packageName, actualPins))
        assertFalse(ProviderTrust.isTrustedPackage(context, context.packageName, setOf("00".repeat(32))))
        assertFalse(ProviderTrust.isTrustedPackage(context, "does.not.exist.ksuser", actualPins))
    }
}
