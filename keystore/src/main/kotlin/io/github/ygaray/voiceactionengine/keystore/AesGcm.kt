package io.github.ygaray.voiceactionengine.keystore

import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** An encrypted value: the initialisation vector and the ciphertext with the 16-byte authentication tag appended. */
internal class Sealed(val iv: ByteArray, val ciphertext: ByteArray) {
    /** Prints the two lengths only. */
    override fun toString(): String = "Sealed(ivBytes=${iv.size}, ciphertextBytes=${ciphertext.size})"
}

/**
 * The one AES/GCM code path, shared by production keys and test keys. The layout is byte-compatible with the values
 * the existing adopter apps already store: a provider-generated 12-byte nonce and the encrypted bytes with
 * the tag appended.
 */
internal object AesGcm {
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val TAG_LENGTH_BITS = 128

    /**
     * Encrypts [plaintext] with [key]. There is deliberately no IV parameter: the provider generates the nonce and the
     * caller reads it back, because the platform key store refuses a caller-supplied one.
     */
    fun seal(key: SecretKey, plaintext: ByteArray): Sealed {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val ciphertext = cipher.doFinal(plaintext)
        return Sealed(cipher.iv, ciphertext)
    }

    /** Decrypts [ciphertext] (with its tag) under [key] and [iv]; a wrong key or altered bytes throw. */
    fun open(key: SecretKey, iv: ByteArray, ciphertext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_LENGTH_BITS, iv))
        return cipher.doFinal(ciphertext)
    }
}
