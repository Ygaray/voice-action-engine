package io.github.ygaray.voiceactionengine.keystore

import io.github.ygaray.voiceactionengine.core.ProviderId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** A wrong table is refused when it is built, and the refusal names the bad entry and nothing secret. */
class KeySlotValidationTest {
    @get:Rule
    val folder = TemporaryFolder()

    private var prefs: TempPreferences? = null

    @After
    fun close() {
        prefs?.close()
    }

    private fun build(slots: List<KeySlot>): ApiKeyStore {
        val temp = TempPreferences(folder).also { prefs = it }
        return ApiKeyStore(temp.dataStore, slots, Dispatchers.Unconfined, SoftwareKeyAccess())
    }

    private fun row(provider: ProviderId, alias: String, ct: String, iv: String) = KeySlot(provider, alias, ct, iv)

    private fun refusal(block: () -> Unit): String {
        try {
            block()
        } catch (expected: IllegalArgumentException) {
            return expected.message.orEmpty()
        }
        throw AssertionError("expected IllegalArgumentException")
    }

    @Test
    fun aBlankAliasIsRefusedAndNamed() {
        listOf("", "   ").forEach { blank ->
            val message = refusal { KeySlot(ProviderId.OPENAI, blank, "ct", "iv") }
            assertTrue(message, message.contains("alias"))
        }
    }

    @Test
    fun aBlankCiphertextKeyIsRefusedAndNamed() {
        listOf("", " \t ").forEach { blank ->
            val message = refusal { KeySlot(ProviderId.OPENAI, "alias", blank, "iv") }
            assertTrue(message, message.contains("ciphertextKey"))
        }
    }

    @Test
    fun aBlankIvKeyIsRefusedAndNamed() {
        listOf("", "  ").forEach { blank ->
            val message = refusal { KeySlot(ProviderId.OPENAI, "alias", "ct", blank) }
            assertTrue(message, message.contains("ivKey"))
        }
    }

    @Test
    fun aRowWhoseCiphertextKeyEqualsItsIvKeyIsRefusedAndNamed() {
        val message = refusal { KeySlot(ProviderId.OPENAI, "alias", "same_name", "same_name") }

        assertTrue(message, message.contains("same_name"))
    }

    @Test
    fun anEmptyTableIsRefused() {
        refusal { build(emptyList()) }
    }

    @Test
    fun twoRowsForOneProviderAreRefusedAndTheProviderIsNamed() {
        val message = refusal {
            build(
                listOf(
                    row(ProviderId.OPENAI, "alias_one", "ct_one", "iv_one"),
                    row(ProviderId.OPENAI, "alias_two", "ct_two", "iv_two"),
                ),
            )
        }

        assertTrue(message, message.contains("openai"))
    }

    @Test
    fun twoRowsSharingAnAliasAreRefusedAndTheAliasIsNamed() {
        val message = refusal {
            build(
                listOf(
                    row(ProviderId.OPENAI, "shared_alias", "ct_one", "iv_one"),
                    row(ProviderId.OPENROUTER, "shared_alias", "ct_two", "iv_two"),
                ),
            )
        }

        assertTrue(message, message.contains("shared_alias"))
    }

    @Test
    fun aCiphertextKeyReusedAsAnotherRowsCiphertextKeyIsRefusedAndNamed() {
        val message = refusal {
            build(
                listOf(
                    row(ProviderId.OPENAI, "alias_one", "reused_name", "iv_one"),
                    row(ProviderId.OPENROUTER, "alias_two", "reused_name", "iv_two"),
                ),
            )
        }

        assertTrue(message, message.contains("reused_name"))
    }

    @Test
    fun aCiphertextKeyReusedAsAnotherRowsIvKeyIsRefusedAndNamed() {
        val message = refusal {
            build(
                listOf(
                    row(ProviderId.OPENAI, "alias_one", "reused_name", "iv_one"),
                    row(ProviderId.OPENROUTER, "alias_two", "ct_two", "reused_name"),
                ),
            )
        }

        assertTrue(message, message.contains("reused_name"))
    }

    @Test
    fun anIvKeyReusedAsAnotherRowsIvKeyIsRefusedAndNamed() {
        val message = refusal {
            build(
                listOf(
                    row(ProviderId.OPENAI, "alias_one", "ct_one", "reused_name"),
                    row(ProviderId.OPENROUTER, "alias_two", "ct_two", "reused_name"),
                ),
            )
        }

        assertTrue(message, message.contains("reused_name"))
    }

    @Test
    fun theSecondBrainTableAndTheCalTrackerTableBuild() {
        build(sbSlots())
        build(ctSlots())
    }

    @Test
    fun mutatingTheCallersListAfterConstructionChangesNothing() = runTest {
        val slots = ctSlots().toMutableList()
        val store = build(slots)

        slots.clear()
        slots.add(row(ProviderId.ANTHROPIC, "other_alias", "other_ct", "other_iv"))
        store.save(ProviderId.OPENAI, "openai-secret-wxyz") // secret-scan: allow (fake canary)

        assertEquals(KeyState.Ready("wxyz"), store.read(ProviderId.OPENAI))
        assertEquals(setOf("openai_api_key_ct", "openai_api_key_iv"), prefs!!.snapshot().keys)
    }

    @Test
    fun equalityCoversAllFourFields() {
        val base = row(ProviderId.OPENAI, "alias", "ct", "iv")

        assertEquals(base, row(ProviderId.OPENAI, "alias", "ct", "iv"))
        assertEquals(base.hashCode(), row(ProviderId.OPENAI, "alias", "ct", "iv").hashCode())
        assertNotEquals(base, row(ProviderId.OPENROUTER, "alias", "ct", "iv"))
        assertNotEquals(base, row(ProviderId.OPENAI, "other", "ct", "iv"))
        assertNotEquals(base, row(ProviderId.OPENAI, "alias", "other", "iv"))
        assertNotEquals(base, row(ProviderId.OPENAI, "alias", "ct", "other"))
    }

    @Test
    fun toStringNamesTheProviderTheAliasAndBothPrefKeys() {
        val text = row(ProviderId.OPENAI, "the_alias", "the_ct", "the_iv").toString()

        listOf("openai", "the_alias", "the_ct", "the_iv").forEach { assertTrue(text, text.contains(it)) }
    }
}
