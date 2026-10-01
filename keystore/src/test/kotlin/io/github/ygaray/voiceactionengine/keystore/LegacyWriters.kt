package io.github.ygaray.voiceactionengine.keystore

import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private const val TRANSFORMATION = "AES/GCM/NoPadding"
private const val TAG_LENGTH_BITS = 128

/**
 * An independent replica of how the two existing apps write and read a stored key, written from their source and not
 * from this library: plain JCE AES/GCM/NoPadding with a 128-bit tag, the provider-generated nonce taken from the cipher
 * after an encrypt-mode init with no parameter spec, and the standard Base64 encoder (padded, `+` and `/`, no line
 * break). It deliberately shares no code with the production cipher or reader, so a format change in the library cannot
 * make the compatibility tests agree with themselves.
 */
internal object LegacyWriters {
    /** Encrypts [plaintext] the way the apps do and returns the ciphertext and the nonce, each as standard Base64. */
    fun seal(key: SecretKey, plaintext: String): Pair<String, String> {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val encoder = Base64.getEncoder()
        return encoder.encodeToString(ciphertext) to encoder.encodeToString(cipher.iv)
    }

    /**
     * As [seal] but with a caller-chosen nonce, which the default provider allows. Only a test reproducing a fixed
     * vector may do this; production never supplies a nonce.
     */
    fun sealWithIv(key: SecretKey, iv: ByteArray, plaintext: String): Pair<String, String> {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_LENGTH_BITS, iv))
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val encoder = Base64.getEncoder()
        return encoder.encodeToString(ciphertext) to encoder.encodeToString(iv)
    }

    /** Decrypts a stored pair the way the apps do. Fails on a wrong key, a changed byte or a malformed string. */
    fun open(key: SecretKey, ctB64: String, ivB64: String): String {
        val decoder = Base64.getDecoder()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_LENGTH_BITS, decoder.decode(ivB64)))
        return String(cipher.doFinal(decoder.decode(ctB64)), Charsets.UTF_8)
    }
}
