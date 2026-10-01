package io.github.ygaray.voiceactionengine.keystore

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test
import java.security.GeneralSecurityException
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

private const val IV_BYTES = 12
private const val TAG_BYTES = 16
private const val KEY_BYTES = 32

/** The shared cipher path, driven directly with software keys. */
class AesGcmTest {
    private fun key(fill: Int): SecretKey = SecretKeySpec(ByteArray(KEY_BYTES) { fill.toByte() }, "AES")

    private val plain = "fake-plaintext-for-the-cipher".toByteArray(Charsets.UTF_8)

    @Test
    fun twoSealsOfTheSamePlaintextUseDifferentIvsAndGiveDifferentCiphertexts() {
        val key = key(1)

        val first = AesGcm.seal(key, plain)
        val second = AesGcm.seal(key, plain)

        assertEquals(IV_BYTES, first.iv.size)
        assertEquals(IV_BYTES, second.iv.size)
        assertFalse(first.iv.contentEquals(second.iv))
        assertFalse(first.ciphertext.contentEquals(second.ciphertext))
    }

    @Test
    fun theCiphertextIsThePlaintextPlusTheSixteenByteTag() {
        val sealed = AesGcm.seal(key(1), plain)

        assertEquals(plain.size + TAG_BYTES, sealed.ciphertext.size)
    }

    @Test
    fun openReturnsWhatSealEncrypted() {
        val key = key(1)
        val sealed = AesGcm.seal(key, plain)

        assertArrayEquals(plain, AesGcm.open(key, sealed.iv, sealed.ciphertext))
    }

    @Test
    fun openUnderADifferentKeyThrows() {
        val sealed = AesGcm.seal(key(1), plain)

        assertThrows(GeneralSecurityException::class.java) { AesGcm.open(key(2), sealed.iv, sealed.ciphertext) }
    }

    @Test
    fun openOfACiphertextWithOneFlippedBitThrows() {
        val key = key(1)
        val sealed = AesGcm.seal(key, plain)
        val altered = sealed.ciphertext.copyOf()
        altered[0] = (altered[0].toInt() xor 1).toByte()

        assertThrows(GeneralSecurityException::class.java) { AesGcm.open(key, sealed.iv, altered) }
    }
}
