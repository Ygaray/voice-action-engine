package io.github.ygaray.voiceactionengine.keystore

import io.github.ygaray.voiceactionengine.core.ProviderId
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

private const val TEST_KEY = "sk-test-0123456789wxyz" // secret-scan: allow (fixed non-secret test string)

/** The public opt-in constructor: a caller-supplied software key drives the production cipher and DataStore code. */
class ApiKeyStoreKeyAccessTest {
    @get:Rule
    val folder = TemporaryFolder()

    private var prefs: TempPreferences? = null

    @After
    fun close() {
        prefs?.close()
    }

    @OptIn(DelicateKeyAccess::class)
    private fun storeOver(temp: TempPreferences, keys: KeyAccess): ApiKeyStore =
        ApiKeyStore(temp.dataStore, ctSlots(), keys)

    private fun temp(): TempPreferences = TempPreferences(folder).also { prefs = it }

    @Test
    fun aSavedKeyRoundTripsThroughThePublicConstructor() = runTest {
        val store = storeOver(temp(), SoftwareKeyAccess())

        store.save(ProviderId.ANTHROPIC, TEST_KEY)

        assertEquals(KeyState.Ready("wxyz"), store.read(ProviderId.ANTHROPIC))
    }

    @Test
    fun readingAnAbsentProviderNeverCreatesAKey() = runTest {
        val keys = SoftwareKeyAccess()
        val store = storeOver(temp(), keys)

        assertEquals(KeyState.NotConfigured(), store.read(ProviderId.OPENAI))

        assertEquals(0, keys.getOrCreateCalls.get())
        assertEquals(0, keys.createdKeys.get())
    }

    @Test
    fun aFreshStoreOverTheSameDataStoreAndKeysReadsTheKeyBack() = runTest {
        val temp = temp()
        val keys = SoftwareKeyAccess()
        storeOver(temp, keys).save(ProviderId.ANTHROPIC, TEST_KEY)

        val fresh = storeOver(temp, keys)

        assertEquals(KeyState.Ready("wxyz"), fresh.read(ProviderId.ANTHROPIC))
    }
}
