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
private const val LAST_FOUR = 4
private const val CT_ANTHROPIC_KEY = "sk-ant-api03-ctAAAA" // secret-scan: allow (fake canary)
private const val CT_OPENAI_KEY = "sk-proj-ctBBBB" // secret-scan: allow (fake canary)
private const val CT_OPENROUTER_KEY = "sk-or-v1-ctCCCC" // secret-scan: allow (fake canary)
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
        val pair = LegacyWriters.seal(keys.keyFor(SB_ALIAS), SB_KEY)
        writeLegacy(temp, "anthropic_api_key_ct", "anthropic_api_key_iv", pair)
        val store = store(temp, sbSlots())

        assertEquals(KeyState.Ready("QzT9"), store.read(ProviderId.ANTHROPIC))
        assertEquals(SB_KEY, store.readSecret(ProviderId.ANTHROPIC).plaintext)
        assertEquals(0, keys.getOrCreateCalls.get())
        assertEquals(0, keys.createdKeys.get())
    }

    @Test
    fun theThreeCalTrackerProviderPairsEachReadBackTheirOwnKeyOnly() = runTest {
        val originals = mapOf(
            Triple(ProviderId.ANTHROPIC, "caltracker_api_key_v1", "anthropic_api_key") to CT_ANTHROPIC_KEY,
            Triple(ProviderId.OPENAI, "caltracker_openai_api_key_v1", "openai_api_key") to CT_OPENAI_KEY,
            Triple(ProviderId.OPENROUTER, "caltracker_openrouter_api_key_v1", "openrouter_api_key") to
                CT_OPENROUTER_KEY,
        )
        val temp = temp()
        originals.entries.forEachIndexed { index, (slot, plaintext) ->
            val (_, alias, prefix) = slot
            keys.install(alias, keyBytes(seed = index + 10))
            writeLegacy(temp, "${prefix}_ct", "${prefix}_iv", LegacyWriters.seal(keys.keyFor(alias), plaintext))
        }
        val store = store(temp, ctSlots())

        originals.forEach { (slot, plaintext) ->
            assertEquals(KeyState.Ready(plaintext.takeLast(LAST_FOUR)), store.read(slot.first))
            assertEquals(plaintext, store.readSecret(slot.first).plaintext)
        }
        assertEquals(0, keys.getOrCreateCalls.get())
    }

    @Test
    fun aPairTheAppKeepsForItselfIsNeverTouchedBySaveReadOrDelete() = runTest {
        val temp = temp()
        val mcpAlias = "caltracker_mcp_token_v1"
        keys.install(mcpAlias, keyBytes(seed = 40))
        writeLegacy(temp, "mcp_token_ct", "mcp_token_iv", LegacyWriters.seal(keys.keyFor(mcpAlias), "mcp-token-value"))
        val store = store(temp, ctSlots())
        val before = temp.snapshot().filterKeys { it.startsWith("mcp_token_") }

        listOf(ProviderId.ANTHROPIC, ProviderId.OPENAI, ProviderId.OPENROUTER).forEach { provider ->
            store.save(provider, "key-for-${provider.value}-1234")
            store.read(provider)
            store.delete(provider)
        }

        assertEquals(2, before.size)
        assertEquals(before, temp.snapshot().filterKeys { it.startsWith("mcp_token_") })
    }

    @Test
    fun aPairTheStoreWritesOpensWithTheLegacyReaderInTheSecondBrainLayout() = runTest {
        assertEveryProviderOpensWithTheLegacyReader(sbSlots())
    }

    @Test
    fun aPairTheStoreWritesOpensWithTheLegacyReaderInTheCalTrackerLayout() = runTest {
        assertEveryProviderOpensWithTheLegacyReader(ctSlots())
    }

    private suspend fun assertEveryProviderOpensWithTheLegacyReader(slots: List<KeySlot>) {
        val temp = temp()
        val store = ApiKeyStore(temp.dataStore, slots, Dispatchers.IO, keys)
        slots.forEach { slot ->
            store.save(slot.provider, "  key-${slot.provider.value}-rollback  ")
            val stored = temp.snapshot()

            val opened = LegacyWriters.open(
                keys.keyFor(slot.alias),
                stored.getValue(slot.ciphertextKey) as String,
                stored.getValue(slot.ivKey) as String,
            )

            assertEquals("key-${slot.provider.value}-rollback", opened)
        }
    }
}
