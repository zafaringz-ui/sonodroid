package dev.sonora.secure

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypted key/value storage for provider credentials.
 *
 * Design follows the plan's security section:
 *  * AES-256-GCM key generated in the Android Keystore, never exported;
 *  * one sealed blob on disk (`filesDir/vault/secrets.bin`), written atomically;
 *  * no `allowBackup` (see the manifest), so the blob cannot leave the device;
 *  * StrongBox is used when the device advertises it, falling back silently.
 *
 * The whole map is re-encrypted on each write. There are at most a handful of
 * entries (one ARL, one Google cookie jar per account), so this is cheap and keeps
 * the format simple enough to audit.
 */
class SecretsVault(context: Context) {

    private val appContext = context.applicationContext
    private val vaultDir = File(appContext.filesDir, "vault")
    private val file = File(vaultDir, "secrets.bin")

    fun put(key: String, value: String) = synchronized(LOCK) {
        val entries = read()
        entries[key] = value
        write(entries)
    }

    fun get(key: String): String? = synchronized(LOCK) { read()[key] }

    fun remove(key: String) = synchronized(LOCK) {
        val entries = read()
        if (entries.remove(key) != null) write(entries)
    }

    fun clear() = synchronized(LOCK) {
        if (file.exists() && !file.delete()) {
            file.writeBytes(ByteArray(0))
        }
    }

    fun keys(): Set<String> = synchronized(LOCK) { read().keys.toSet() }

    private fun read(): MutableMap<String, String> {
        if (!file.exists() || file.length() == 0L) return mutableMapOf()
        return runCatching {
            val plaintext = decrypt(file.readBytes())
            val json = JSONObject(String(plaintext, Charsets.UTF_8))
            buildMap {
                json.keys().forEach { name -> put(name, json.getString(name)) }
            }.toMutableMap()
        }.getOrElse { failure ->
            // A restored backup or a deleted Keystore key lands here. Losing
            // credentials is acceptable; crashing the app on launch is not.
            Log.w(TAG, "Vault unreadable, starting empty: ${failure.javaClass.simpleName}")
            mutableMapOf()
        }
    }

    private fun write(entries: Map<String, String>) {
        if (!vaultDir.exists() && !vaultDir.mkdirs()) {
            throw IllegalStateException("Cannot create vault directory")
        }
        val json = JSONObject()
        entries.forEach { (key, value) -> json.put(key, value) }
        val sealed = encrypt(json.toString().toByteArray(Charsets.UTF_8))

        val temp = File(vaultDir, "secrets.bin.tmp")
        temp.writeBytes(sealed)
        if (!temp.renameTo(file)) {
            file.writeBytes(sealed)
            temp.delete()
        }
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(false)
            .apply {
                // StrongBox is a nice-to-have; not every OEM ships it.
                runCatching { setIsStrongBoxBacked(true) }
            }
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    private fun encrypt(plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext)
        return Base64.encodeToString(iv, Base64.NO_WRAP).toByteArray() + SEPARATOR +
            Base64.encodeToString(ciphertext, Base64.NO_WRAP).toByteArray()
    }

    private fun decrypt(sealed: ByteArray): ByteArray {
        val separator = sealed.indexOf(SEPARATOR)
        require(separator > 0) { "Malformed vault blob" }
        val iv = Base64.decode(sealed.copyOfRange(0, separator), Base64.NO_WRAP)
        val ciphertext = Base64.decode(sealed.copyOfRange(separator + 1, sealed.size), Base64.NO_WRAP)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_BITS, iv))
        return cipher.doFinal(ciphertext)
    }

    private fun ByteArray.indexOf(needle: Byte): Int {
        for (index in indices) if (this[index] == needle) return index
        return -1
    }

    private companion object {
        const val TAG = "SecretsVault"
        const val KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "sonora-vault-v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_BITS = 128
        const val SEPARATOR = ':'.code.toByte()
        val LOCK = Any()
    }
}
