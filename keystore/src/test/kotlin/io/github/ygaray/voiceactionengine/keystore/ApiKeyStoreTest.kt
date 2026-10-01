package io.github.ygaray.voiceactionengine.keystore

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.ygaray.voiceactionengine.core.ProviderId
import io.github.ygaray.voiceactionengine.core.testing.NoNetworkGuard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec

private const val ALIAS = "test_anthropic_alias"
private const val CT_KEY = "anthropic_api_key_ct"
private const val IV_KEY = "anthropic_api_key_iv"
private const val IV_BYTES = 12
private const val TAG_BYTES = 16
private const val TAG_BITS = 128

/** The store's whole save-then-read path on the JVM: production cipher code, software keys, a real DataStore file. */
class ApiKeyStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private var prefs: TempPreferences? = null
    private val keys = SoftwareKeyAccess()

    @After
    fun close() {
        prefs?.close()
    }

    private fun store(): ApiKeyStore = storeOver(listOf(KeySlot(ProviderId.ANTHROPIC, ALIAS, CT_KEY, IV_KEY)))

    private fun storeOver(slots: List<KeySlot>): ApiKeyStore {
        val temp = TempPreferences(folder).also { prefs = it }
        return ApiKeyStore(temp.dataStore, slots, Dispatchers.Unconfined, keys)
    }

    @Test
    fun aSavedKeyReadsBackReadyThroughTheRealLayout() = runTest {
        NoNetworkGuard.during {
            val plain = "sk-ant-test-0123456789abcd" // secret-scan: allow (fake canary)
            val store = store()

            store.save(ProviderId.ANTHROPIC, plain)

            assertEquals(KeyState.Ready("abcd"), store.read(ProviderId.ANTHROPIC))
            val stored = prefs!!.snapshot()
            assertEquals(setOf(CT_KEY, IV_KEY), stored.keys)
            val pattern = Regex("^[A-Za-z0-9+/]+={0,2}$")
            val ciphertext = stored.getValue(CT_KEY) as String
            val iv = stored.getValue(IV_KEY) as String
            assertTrue(pattern.matches(ciphertext))
            assertTrue(pattern.matches(iv))
            val ivBytes = Base64.getDecoder().decode(iv)
            val ctBytes = Base64.getDecoder().decode(ciphertext)
            assertEquals(IV_BYTES, ivBytes.size)
            assertEquals(plain.toByteArray(Charsets.UTF_8).size + TAG_BYTES, ctBytes.size)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, keys.keyFor(ALIAS), GCMParameterSpec(TAG_BITS, ivBytes))
            assertEquals(plain, String(cipher.doFinal(ctBytes), Charsets.UTF_8))

            assertEquals(1, keys.getOrCreateCalls.get())
            store.read(ProviderId.ANTHROPIC)
            assertEquals(1, keys.getOrCreateCalls.get())
        }
    }

    @Test
    fun aNeverSavedAndAnUnmappedProviderAreNotConfigured() = runTest {
        val store = store()

        assertEquals(KeyState.NotConfigured(), store.read(ProviderId.ANTHROPIC))
        assertEquals(KeyState.NotConfigured(), store.read(ProviderId.OPENAI))
    }

    @Test
    fun aKeyOfFourCharactersRevealsNothing() = runTest {
        val store = store()

        store.save(ProviderId.ANTHROPIC, "abcd")

        assertEquals(KeyState.Ready(""), store.read(ProviderId.ANTHROPIC))
    }

    @Test
    fun aThreeProviderStoreKeepsEachKeySeparateAcrossSaveReplaceAndDelete() = runTest {
        val store = storeOver(ctSlots())
        val temp = prefs!!
        temp.dataStore.edit {
            it[stringPreferencesKey("theme_mode")] = "dark"
            it[intPreferencesKey("launch_count")] = 7
        }
        val unrelated = mapOf("theme_mode" to "dark", "launch_count" to 7)
        val names = mapOf(
            ProviderId.ANTHROPIC to setOf("anthropic_api_key_ct", "anthropic_api_key_iv"),
            ProviderId.OPENAI to setOf("openai_api_key_ct", "openai_api_key_iv"),
            ProviderId.OPENROUTER to setOf("openrouter_api_key_ct", "openrouter_api_key_iv"),
        )

        suspend fun assertStored(saved: List<ProviderId>) {
            val snapshot = temp.snapshot()
            assertEquals(unrelated.keys + saved.flatMap { names.getValue(it) }, snapshot.keys)
            unrelated.forEach { (name, value) -> assertEquals(value, snapshot[name]) }
        }

        assertStored(emptyList())
        store.save(ProviderId.ANTHROPIC, "anthropic-secret-aaaa") // secret-scan: allow (fake canary)
        store.save(ProviderId.OPENAI, "openai-secret-bbbb") // secret-scan: allow (fake canary)
        store.save(ProviderId.OPENROUTER, "openrouter-secret-cccc") // secret-scan: allow (fake canary)
        assertEquals(KeyState.Ready("aaaa"), store.read(ProviderId.ANTHROPIC))
        assertEquals(KeyState.Ready("bbbb"), store.read(ProviderId.OPENAI))
        assertEquals(KeyState.Ready("cccc"), store.read(ProviderId.OPENROUTER))
        assertStored(listOf(ProviderId.ANTHROPIC, ProviderId.OPENAI, ProviderId.OPENROUTER))

        store.save(ProviderId.OPENAI, "openai-secret-dddd") // secret-scan: allow (fake canary)
        assertEquals(KeyState.Ready("aaaa"), store.read(ProviderId.ANTHROPIC))
        assertEquals(KeyState.Ready("dddd"), store.read(ProviderId.OPENAI))
        assertEquals(KeyState.Ready("cccc"), store.read(ProviderId.OPENROUTER))
        assertStored(listOf(ProviderId.ANTHROPIC, ProviderId.OPENAI, ProviderId.OPENROUTER))

        store.delete(ProviderId.OPENAI)
        assertEquals(KeyState.NotConfigured(), store.read(ProviderId.OPENAI))
        assertEquals(KeyState.Ready("aaaa"), store.read(ProviderId.ANTHROPIC))
        assertEquals(KeyState.Ready("cccc"), store.read(ProviderId.OPENROUTER))
        assertNotNull(keys.keyFor("caltracker_openai_api_key_v1"))
        assertStored(listOf(ProviderId.ANTHROPIC, ProviderId.OPENROUTER))
    }
}
