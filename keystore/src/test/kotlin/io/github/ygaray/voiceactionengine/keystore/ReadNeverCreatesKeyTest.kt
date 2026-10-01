package io.github.ygaray.voiceactionengine.keystore

import io.github.ygaray.voiceactionengine.core.ProviderId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

private const val ALIAS = "secondbrain_anthropic_api_key_v1"
private const val FAKE_KEY = "anthropic-secret-wxyz" // secret-scan: allow (fake canary)

/** A read looks keys up and never creates one, never writes, and reports a lost device key as KeyMissing. */
class ReadNeverCreatesKeyTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val keys = SoftwareKeyAccess()
    private var prefs: TempPreferences? = null
    private var recording: RecordingDataStore? = null

    @After
    fun close() {
        prefs?.close()
    }

    private fun store(): ApiKeyStore {
        val temp = TempPreferences(folder).also { prefs = it }
        val recorder = RecordingDataStore(temp.dataStore).also { recording = it }
        return ApiKeyStore(recorder, sbSlots(), Dispatchers.IO, keys)
    }

    @Test
    fun aRestoredBackupWithoutItsDeviceKeyReadsKeyMissingAndTouchesNothing() = runTest {
        val store = store()
        store.save(ProviderId.ANTHROPIC, FAKE_KEY)
        keys.lose(ALIAS)
        val creates = keys.getOrCreateCalls.get()
        val created = keys.createdKeys.get()
        val writes = recording!!.updates.get()
        val before = prefs!!.snapshot()

        val state = store.read(ProviderId.ANTHROPIC)
        val secret = store.readSecret(ProviderId.ANTHROPIC)

        assertEquals(KeyState.KeyMissing(), state)
        assertEquals(KeyState.KeyMissing(), secret.state)
        assertNull(secret.plaintext)
        assertEquals(creates, keys.getOrCreateCalls.get())
        assertEquals(created, keys.createdKeys.get())
        assertEquals(writes, recording!!.updates.get())
        assertEquals(before, prefs!!.snapshot())
        assertNull(deviceKeyOrNull())
    }

    @Test
    fun aReadyKeyReadsBackWithItsPlaintextAndStillCreatesNothing() = runTest {
        val store = store()
        store.save(ProviderId.ANTHROPIC, FAKE_KEY)
        val creates = keys.getOrCreateCalls.get()

        val secret = store.readSecret(ProviderId.ANTHROPIC)

        assertEquals(KeyState.Ready("wxyz"), secret.state)
        assertEquals(FAKE_KEY, secret.plaintext)
        assertNotNull(keys.keyFor(ALIAS))
        assertEquals(creates, keys.getOrCreateCalls.get())
    }

    @Test
    fun aNeverSavedProviderReadsNotConfiguredWithoutALookupOrACreate() = runTest {
        val store = store()

        val configured = store.read(ProviderId.ANTHROPIC)
        val unmapped = store.read(ProviderId.OPENAI)

        assertEquals(KeyState.NotConfigured(), configured)
        assertEquals(KeyState.NotConfigured(), unmapped)
        assertEquals(0, keys.lookups.get())
        assertEquals(0, keys.getOrCreateCalls.get())
        assertEquals(0, keys.createdKeys.get())
        assertEquals(0, recording!!.updates.get())
    }

    private fun deviceKeyOrNull(): Any? = try {
        keys.keyFor(ALIAS)
    } catch (ignored: IllegalStateException) {
        null
    }
}
