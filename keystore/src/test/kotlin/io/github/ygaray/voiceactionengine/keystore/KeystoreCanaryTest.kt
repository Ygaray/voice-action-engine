package io.github.ygaray.voiceactionengine.keystore

import io.github.ygaray.voiceactionengine.core.ProviderId
import io.github.ygaray.voiceactionengine.core.provider.CredentialLookup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.security.GeneralSecurityException
import java.security.UnrecoverableKeyException
import java.util.UUID

private const val ALIAS = "secondbrain_anthropic_api_key_v1"
private const val TAIL_CHARS = 12

/** A saved key appears in no string a type prints, no exception message, and not in the bytes on disk. */
class KeystoreCanaryTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val keys = SoftwareKeyAccess()
    private var prefs: TempPreferences? = null

    // Built at run time so no literal looks like a key; the prefix still mimics a real one.
    private val canary: String = "sk-ant-CANARY-" + UUID.randomUUID()
    private val tail: String = canary.takeLast(TAIL_CHARS)

    @After
    fun close() {
        prefs?.close()
    }

    private fun store(): ApiKeyStore {
        val temp = TempPreferences(folder).also { prefs = it }
        return ApiKeyStore(temp.dataStore, sbSlots(), Dispatchers.IO, keys)
    }

    private fun assertClean(what: String, text: String?) {
        val shown = text.orEmpty()
        assertTrue("$what contains the key", !shown.contains(canary))
        assertTrue("$what contains the tail of the key", !shown.contains(tail))
    }

    private fun assertChainClean(what: String, failure: Throwable) {
        generateSequence(failure) { it.cause }.take(CHAIN_LIMIT).forEachIndexed { depth, link ->
            assertClean("$what message at depth $depth", link.message)
            assertClean("$what toString at depth $depth", link.toString())
        }
    }

    private suspend fun failureOf(block: suspend () -> Unit): Throwable {
        try {
            block()
        } catch (failure: IllegalArgumentException) {
            return failure
        } catch (failure: GeneralSecurityException) {
            return failure
        }
        throw AssertionError("expected a failure")
    }

    @Test
    fun noTypeThePublicApiHandsOutPrintsTheKey() = runTest {
        val store = store()
        store.save(ProviderId.ANTHROPIC, canary)
        val source = KeystoreCredentialSource(store)
        val present = source.credential(ProviderId.ANTHROPIC) as CredentialLookup.Present

        val ready = store.read(ProviderId.ANTHROPIC)
        keys.failLookupWith = UnrecoverableKeyException("x")
        val unreadable = store.read(ProviderId.ANTHROPIC)
        keys.failLookupWith = null
        keys.lose(ALIAS)
        val missing = store.read(ProviderId.ANTHROPIC)
        val notConfigured = store.read(ProviderId.OPENAI)

        assertTrue(ready is KeyState.Ready)
        assertTrue(unreadable is KeyState.Unreadable)
        assertTrue(missing is KeyState.KeyMissing)
        assertTrue(notConfigured is KeyState.NotConfigured)
        listOf(
            "Ready" to ready,
            "Unreadable" to unreadable,
            "KeyMissing" to missing,
            "NotConfigured" to notConfigured,
            "KeySlot" to sbSlots().single(),
            "ApiKeyStore" to store,
            "KeystoreCredentialSource" to source,
            "Present" to present,
            "Credential" to present.credential,
        ).forEach { (name, value) -> assertClean("$name.toString()", value.toString()) }
    }

    @Test
    fun noFailurePathPutsTheKeyInAnExceptionMessage() = runTest {
        val store = store()
        val failures = mutableListOf<Pair<String, Throwable>>()

        failures += "save for an unmapped provider" to failureOf { store.save(ProviderId.OPENAI, canary) }
        failures += "delete for an unmapped provider" to failureOf { store.delete(ProviderId.OPENAI) }
        keys.failLookupWith = UnrecoverableKeyException("keystore down")
        failures += "save while the key lookup fails" to failureOf { store.save(ProviderId.ANTHROPIC, canary) }
        keys.failLookupWith = null
        failures += "a blank key" to failureOf { store.save(ProviderId.ANTHROPIC, "   ") }
        failures += "a table with a blank alias" to failureOf {
            KeySlot(ProviderId.ANTHROPIC, " ", "ct", "iv")
        }
        failures += "a table with equal preference names" to failureOf {
            KeySlot(ProviderId.ANTHROPIC, ALIAS, "same", "same")
        }
        failures += "an empty table" to failureOf { ApiKeyStore(prefs!!.dataStore, emptyList(), Dispatchers.IO, keys) }
        failures += "Ready with a last4 built from the key" to failureOf { KeyState.Ready(canary) }
        failures += "Unreadable with the key as the cause" to failureOf { KeyState.Unreadable(canary) }
        failures += "Unreadable lookup with the key as the cause" to failureOf { CredentialLookup.Unreadable(canary) }

        failures.forEach { (what, failure) -> assertChainClean(what, failure) }
    }

    @Test
    fun theFileOnDiskHoldsNeitherTheKeyNorItsTail() = runTest {
        val store = store()
        store.save(ProviderId.ANTHROPIC, canary)

        val bytes = prefs!!.file.readBytes()
        val asText = String(bytes, Charsets.ISO_8859_1)

        assertTrue("the file must not be empty", bytes.isNotEmpty())
        assertTrue("the file holds the key", !asText.contains(canary))
        assertTrue("the file holds the tail of the key", !asText.contains(tail))
        val lookup = KeystoreCredentialSource(store).credential(ProviderId.ANTHROPIC) as CredentialLookup.Present
        assertEquals(canary, lookup.credential.apiKey)
    }

    private companion object {
        const val CHAIN_LIMIT = 8
    }
}
