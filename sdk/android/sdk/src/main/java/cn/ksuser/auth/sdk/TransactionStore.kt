package cn.ksuser.auth.sdk

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import com.google.gson.Gson
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal interface TransactionStore {
    fun load(): PendingTransaction?
    fun save(transaction: PendingTransaction)
    fun clear()
}

/** No plaintext transaction, process-wide shared preferences, or backup-restorable verifier. */
internal class EncryptedTransactionStore(context: Context) : TransactionStore {
    private val file = AtomicFile(File(context.noBackupFilesDir, "ksuser-auth-pending-v1"))
    private val gson = Gson()
    private val aad = (context.packageName + ":ksuser-auth-pending:v1").toByteArray(Charsets.UTF_8)

    override fun load(): PendingTransaction? = storage {
        if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) return@storage null
        val bytes = file.openRead().use { stream ->
            val data = ByteArray(MAX_BYTES + 1)
            var count = 0
            while (count < data.size) {
                val read = stream.read(data, count, data.size - count)
                if (read < 0) break
                count += read
            }
            require(count <= MAX_BYTES)
            data.copyOf(count)
        }
        require(bytes.size > 29 && bytes[0] == 1.toByte())
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(1, 13)))
        cipher.updateAAD(aad)
        val plain = cipher.doFinal(bytes.copyOfRange(13, bytes.size))
        try {
            gson.fromJson(String(plain, Charsets.UTF_8), PendingTransaction::class.java)
                ?: throw IllegalStateException("Invalid transaction")
        } finally { plain.fill(0) }
    }

    override fun save(transaction: PendingTransaction): Unit = storage {
        val plain = gson.toJson(transaction).toByteArray(Charsets.UTF_8)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        cipher.updateAAD(aad)
        val encrypted = try { cipher.doFinal(plain) } finally { plain.fill(0) }
        require(cipher.iv.size == 12 && encrypted.size + 13 <= MAX_BYTES)
        val stream = file.startWrite()
        try {
            stream.write(byteArrayOf(1) + cipher.iv + encrypted)
            file.finishWrite(stream)
        } catch (error: Exception) {
            file.failWrite(stream)
            throw error
        }
    }

    override fun clear(): Unit = storage {
        file.delete()
        check(!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists())
        // This key protects only this temporary record. Removing it also recovers invalidated aliases.
        KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry(KEY_ALIAS) }
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true).build())
            generateKey()
        }
    }

    private fun <T> storage(block: () -> T): T = try { block() }
    catch (_: Exception) { throw KsuserAuthException(KsuserAuthErrorCode.STORAGE) }

    companion object {
        private const val KEY_ALIAS = "cn.ksuser.auth.sdk.pending.v1"
        private const val MAX_BYTES = 256 * 1024
    }
}
