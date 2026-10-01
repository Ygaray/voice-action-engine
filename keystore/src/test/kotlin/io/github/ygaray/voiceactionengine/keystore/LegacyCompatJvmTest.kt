package io.github.ygaray.voiceactionengine.keystore

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.ygaray.voiceactionengine.core.ProviderId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

private const val AES_256_BYTES = 32
private const val SB_ALIAS = "secondbrain_anthropic_api_key_v1"
private const val SB_KEY = "sk-ant-api03-A1b2C3d4E5f6G7h8I9j0K1l2M3n4O5p6Q7r8QzT9" // secret-scan: allow (fake canary)

/**
 * Pairs written by an independent replica of the two apps' writers, under the apps' literal aliases and preference
 * names, read back through the store with no copy or migration step.
 */
class LegacyCompatJvmTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val keys = SoftwareKeyAccess()
    private var prefs: TempPreferences? = null

    @After
    fun close() {
        prefs?.close()
    }

    private fun temp(): TempPreferences = TempPreferences(folder).also { prefs = it }

    private fun store(temp: TempPreferences, slots: List<KeySlot>): ApiKeyStore =
        ApiKeyStore(temp.dataStore, slots, Dispatchers.IO, keys)

    private fun keyBytes(seed: Int): ByteArray = ByteArray(AES_256_BYTES) { (it + seed).toByte() }

    private suspend fun writeLegacy(temp: TempPreferences, ctName: String, ivName: String, pair: Pair<String, String>) {
        temp.dataStore.edit { stored ->
            stored[stringPreferencesKey(ctName)] = pair.first
            stored[stringPreferencesKey(ivName)] = pair.second
        }
    }

    @Test
    fun aSecondBrainPairWrittenByTheLegacyFormatReadsBackUnchanged() = runTest {
        keys.install(SB_ALIAS, keyBytes(seed = 1))
        val temp = temp()
        writeLegacy(temp, "anthropic_api_key_ct", "anthropic_api_key_iv", LegacyWriters.seal(keys.keyFor(SB_ALIAS), SB_KEY))
        val store = store(temp, sbSlots())

        assertEquals(KeyState.Ready("QzT9"), store.read(ProviderId.ANTHROPIC))
        assertEquals(SB_KEY, store.readSecret(ProviderId.ANTHROPIC).plaintext)
        assertEquals(0, keys.getOrCreateCalls.get())
        assertEquals(0, keys.createdKeys.get())
    }
}
