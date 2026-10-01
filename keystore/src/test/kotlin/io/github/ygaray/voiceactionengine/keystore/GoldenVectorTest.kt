package io.github.ygaray.voiceactionengine.keystore

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.ygaray.voiceactionengine.core.ProviderId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.Base64

private const val AES_256_BYTES = 32
private const val GCM_NONCE_BYTES = 12
private const val IV_FIRST_BYTE = 0xA0
private const val CT_B64_LENGTH = 60
private const val SAVES = 25
private const val ALIAS = "secondbrain_anthropic_api_key_v1"
private const val BASE64_SHAPE = "^[A-Za-z0-9+/]*={0,2}$"

private const val GOLDEN_IV_B64 = "oKGio6Slpqeoqaqr"
private const val GOLDEN_CT_B64 = "lXNRTCu/L94SDLfgKjaFmTHvAFvX7m8b5HdcH5L/ps68EN1xW+Y/wyH8kA=="
private const val GOLDEN_PLAINTEXT = "sk-ant-api03-LEGACYKEY-wxyz" // secret-scan: allow (fake canary)

/**
 * A fixed key, nonce and plaintext pin the byte layout and the Base64 alphabet, so any drift in either fails the build
 * instead of stranding keys already stored on users' phones.
 */
class GoldenVectorTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val keys = SoftwareKeyAccess()
    private var prefs: TempPreferences? = null

    @After
    fun close() {
        prefs?.close()
    }

    private fun goldenKeyBytes(): ByteArray = ByteArray(AES_256_BYTES) { it.toByte() }

    private fun goldenIv(): ByteArray = ByteArray(GCM_NONCE_BYTES) { (IV_FIRST_BYTE + it).toByte() }

    @Test
    fun theReplicaReproducesTheGoldenVectorExactly() {
        keys.install(ALIAS, goldenKeyBytes())

        val (ciphertext, iv) = LegacyWriters.sealWithIv(keys.keyFor(ALIAS), goldenIv(), GOLDEN_PLAINTEXT)

        assertEquals(GOLDEN_CT_B64, ciphertext)
        assertEquals(GOLDEN_IV_B64, iv)
        assertEquals(CT_B64_LENGTH, ciphertext.length)
    }

    @Test
    fun theStoreReadsTheGoldenPairBackAsReady() = runTest {
        keys.install(ALIAS, goldenKeyBytes())
        val temp = TempPreferences(folder).also { prefs = it }
        temp.dataStore.edit { stored ->
            stored[stringPreferencesKey("anthropic_api_key_ct")] = GOLDEN_CT_B64
            stored[stringPreferencesKey("anthropic_api_key_iv")] = GOLDEN_IV_B64
        }
        val store = ApiKeyStore(temp.dataStore, sbSlots(), Dispatchers.IO, keys)

        assertEquals(KeyState.Ready("wxyz"), store.read(ProviderId.ANTHROPIC))
        assertEquals(GOLDEN_PLAINTEXT, store.readSecret(ProviderId.ANTHROPIC).plaintext)
        assertEquals(0, keys.getOrCreateCalls.get())
    }

    @Test
    fun theStandardEncoderUsesPlusAndSlashWithPaddingAndNoLineBreak() {
        val encoder = Base64.getEncoder()

        assertEquals("+//+", encoder.encodeToString(byteArrayOf(0xFB.toByte(), 0xFF.toByte(), 0xFE.toByte())))
        assertEquals("+/8=", encoder.encodeToString(byteArrayOf(0xFB.toByte(), 0xFF.toByte())))
        assertEquals("+w==", encoder.encodeToString(byteArrayOf(0xFB.toByte())))
        assertFalse(encoder.encodeToString(ByteArray(AES_256_BYTES * 4) { 0xFB.toByte() }).contains('\n'))
    }

    @Test
    fun everyStringTheStoreWritesIsStrictStandardBase64() = runTest {
        val temp = TempPreferences(folder).also { prefs = it }
        val store = ApiKeyStore(temp.dataStore, sbSlots(), Dispatchers.IO, keys)
        val shape = Regex(BASE64_SHAPE)

        repeat(SAVES) { attempt ->
            store.save(ProviderId.ANTHROPIC, "$GOLDEN_PLAINTEXT-${"x".repeat(attempt * 7)}")
            val stored = temp.snapshot()
            listOf("anthropic_api_key_ct", "anthropic_api_key_iv").forEach { name ->
                val value = stored.getValue(name) as String
                assertTrue(shape.matches(value))
                assertFalse(value.contains('\n') || value.contains('\r'))
                assertEquals(value, Base64.getEncoder().encodeToString(Base64.getDecoder().decode(value)))
            }
        }
    }
}
