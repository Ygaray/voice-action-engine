package io.github.ygaray.voiceactionengine.keystore

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private const val ANDROID_KEYSTORE = "AndroidKeyStore"
private const val TRANSFORMATION = "AES/GCM/NoPadding"
private const val TAG_LENGTH_BITS = 128
private const val KEY_SIZE_BITS = 256

/** What the two apps' crypto returns from an encrypt: the nonce and the ciphertext with its tag appended. */
private class Encrypted(val iv: ByteArray, val ciphertext: ByteArray)

/**
 * Stand-ins for the two apps that already store keys, so the device run can prove the library reads what they wrote and
 * writes what they can read. The crypto in both objects is a verbatim copy, taken on 2026-10-01, of
 * `KeystoreCrypto.kt` in the SecondBrain app (`core/agent`) and of `KeystoreCrypto.kt` in the CalTracker app
 * (`data/security`), minus their dependency-injection annotations and their planning comments. In particular the key
 * lookup is kept exactly as the apps wrote it, because these objects play the old code and must behave like it.
 *
 * The only code that is not copied is the encoding of the stored strings, which in the apps lives in their key
 * repositories: SecondBrain uses the standard `java.util.Base64`, CalTracker the framework `Base64` with `NO_WRAP`.
 *
 * Test keys only; nothing here logs.
 */
internal object SbLegacyCrypto {
    private val encoder = java.util.Base64.getEncoder()
    private val decoder = java.util.Base64.getDecoder()

    /** Encrypts [plaintext] under [alias] and returns the ciphertext and the nonce, each as standard Base64. */
    fun encryptToPair(alias: String, plaintext: String): Pair<String, String> {
        val encrypted = encrypt(plaintext.toByteArray(Charsets.UTF_8), alias)
        return encoder.encodeToString(encrypted.ciphertext) to encoder.encodeToString(encrypted.iv)
    }

    /** Decrypts a stored pair the way SecondBrain does. */
    fun decryptPair(alias: String, ctB64: String, ivB64: String): String =
        decrypt(decoder.decode(ivB64), decoder.decode(ctB64), alias).toString(Charsets.UTF_8)

    private fun encrypt(plaintext: ByteArray, alias: String): Encrypted {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        // No IV spec — randomized encryption is required, so the provider generates a fresh nonce.
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey(alias))
        val ciphertext = cipher.doFinal(plaintext) // includes the 16-byte GCM auth tag
        return Encrypted(iv = cipher.iv, ciphertext = ciphertext) // cipher.iv == the 12-byte nonce
    }

    private fun decrypt(iv: ByteArray, ciphertext: ByteArray, alias: String): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(alias), GCMParameterSpec(TAG_LENGTH_BITS, iv))
        return cipher.doFinal(ciphertext) // throws AEADBadTagException on corrupt ct/tag
    }

    @Synchronized
    private fun getOrCreateKey(alias: String): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val spec = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(KEY_SIZE_BITS)
            // Intentionally NO setUserAuthenticationRequired(...) — non-auth-bound.
            // setRandomizedEncryptionRequired defaults to true → provider generates the IV.
            .build()

        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            .apply { init(spec) }
            .generateKey()
    }
}

/** The CalTracker stand-in; see [SbLegacyCrypto] for what is copied and from where. */
internal object CtLegacyCrypto {
    /** Encrypts [plaintext] under [alias]; the ciphertext and the nonce come back as framework `NO_WRAP` Base64. */
    fun encryptToPair(alias: String, plaintext: String): Pair<String, String> {
        val encrypted = encrypt(plaintext.toByteArray(Charsets.UTF_8), alias)
        return Base64.encodeToString(encrypted.ciphertext, Base64.NO_WRAP) to
            Base64.encodeToString(encrypted.iv, Base64.NO_WRAP)
    }

    /** Decrypts a stored pair the way CalTracker does. */
    fun decryptPair(alias: String, ctB64: String, ivB64: String): String =
        decrypt(Base64.decode(ivB64, Base64.NO_WRAP), Base64.decode(ctB64, Base64.NO_WRAP), alias)
            .toString(Charsets.UTF_8)

    private fun encrypt(plaintext: ByteArray, alias: String): Encrypted {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        // No IV spec — randomized encryption is required, so the provider generates a fresh nonce.
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey(alias))
        val ciphertext = cipher.doFinal(plaintext) // includes the 16-byte GCM auth tag
        return Encrypted(iv = cipher.iv, ciphertext = ciphertext) // cipher.iv == the 12-byte nonce
    }

    private fun decrypt(iv: ByteArray, ciphertext: ByteArray, alias: String): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(alias), GCMParameterSpec(TAG_LENGTH_BITS, iv))
        return cipher.doFinal(ciphertext) // throws AEADBadTagException on corrupt ct/tag
    }

    @Synchronized
    private fun getOrCreateKey(alias: String): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val spec = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(KEY_SIZE_BITS)
            // Intentionally NO setUserAuthenticationRequired(...) — non-auth-bound.
            // setRandomizedEncryptionRequired defaults to true → provider generates the IV.
            .build()

        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            .apply { init(spec) }
            .generateKey()
    }
}
