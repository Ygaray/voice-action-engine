package io.github.ygaray.voiceactionengine.keystore

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.ygaray.voiceactionengine.core.ProviderId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException
import java.util.Base64

private const val CT_KEY = "anthropic_api_key_ct"
private const val FIRST_KEY = "anthropic-secret-wxyz" // secret-scan: allow (fake canary)
private const val SECOND_KEY = "anthropic-other-abcd" // secret-scan: allow (fake canary)
private const val SAME_TAIL_KEY = "anthropic-third-wxyz" // secret-scan: allow (fake canary)

/** The live view of one provider's state: it follows saves and deletes, survives corruption and never writes. */
class ApiKeyStoreObserveTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val keys = SoftwareKeyAccess()
    private val collector = CoroutineScope(Dispatchers.Default)
    private var prefs: TempPreferences? = null
    private var recording: RecordingDataStore? = null

    @After
    fun close() {
        collector.cancel()
        prefs?.close()
    }

    private fun store(): ApiKeyStore {
        val temp = TempPreferences(folder).also { prefs = it }
        val recorder = RecordingDataStore(temp.dataStore).also { recording = it }
        return ApiKeyStore(recorder, sbSlots(), Dispatchers.IO, keys)
    }

    /** Emissions of [flow], delivered one at a time so a test can act after each. */
    private fun emissionsOf(flow: Flow<KeyState>): Channel<KeyState> {
        val channel = Channel<KeyState>(Channel.UNLIMITED)
        collector.launch { flow.collect { channel.send(it) } }
        return channel
    }

    @Test
    fun itFollowsASaveAReplaceAndADelete() = runTest {
        val store = store()
        val seen = emissionsOf(store.observe(ProviderId.ANTHROPIC))

        assertEquals(KeyState.NotConfigured(), seen.receive())
        store.save(ProviderId.ANTHROPIC, FIRST_KEY)
        assertEquals(KeyState.Ready("wxyz"), seen.receive())
        store.save(ProviderId.ANTHROPIC, SECOND_KEY)
        assertEquals(KeyState.Ready("abcd"), seen.receive())
        store.delete(ProviderId.ANTHROPIC)
        assertEquals(KeyState.NotConfigured(), seen.receive())
    }

    @Test
    fun aReplacedKeyWithTheSameLastFourStillEmits() = runTest {
        val store = store()
        val seen = emissionsOf(store.observe(ProviderId.ANTHROPIC))
        assertEquals(KeyState.NotConfigured(), seen.receive())

        store.save(ProviderId.ANTHROPIC, FIRST_KEY)
        assertEquals(KeyState.Ready("wxyz"), seen.receive())
        store.save(ProviderId.ANTHROPIC, SAME_TAIL_KEY)
        assertEquals(KeyState.Ready("wxyz"), seen.receive())
    }

    @Test
    fun aCorruptValueIsReportedAndTheStreamKeepsGoing() = runTest {
        val store = store()
        store.save(ProviderId.ANTHROPIC, FIRST_KEY)
        val seen = emissionsOf(store.observe(ProviderId.ANTHROPIC))
        assertEquals(KeyState.Ready("wxyz"), seen.receive())

        val key = stringPreferencesKey(CT_KEY)
        val bytes = Base64.getDecoder().decode(prefs!!.snapshot().getValue(CT_KEY) as String)
        bytes[0] = (bytes[0].toInt() xor 1).toByte()
        prefs!!.dataStore.edit { it[key] = Base64.getEncoder().encodeToString(bytes) }
        assertEquals(KeyState.Unreadable("decrypt_failed"), seen.receive())

        store.save(ProviderId.ANTHROPIC, SECOND_KEY)
        assertEquals(KeyState.Ready("abcd"), seen.receive())
    }

    @Test
    fun aStorageFailureEmitsStorageUnreadableAndCompletes() = runTest {
        val store = ApiKeyStore(ThrowingDataStore(IOException("disk")), sbSlots(), Dispatchers.IO, keys)

        val all = store.observe(ProviderId.ANTHROPIC).toList()

        assertEquals(listOf<KeyState>(KeyState.Unreadable("storage_unreadable")), all)
    }

    @Test
    fun aCancellationFromStorageIsRethrown() = runTest {
        val store = ApiKeyStore(ThrowingDataStore(CancellationException("stop")), sbSlots(), Dispatchers.IO, keys)

        val thrown = cancellationOf { store.observe(ProviderId.ANTHROPIC).toList() }

        assertEquals("stop", thrown.message)
    }

    @Test
    fun anUnmappedProviderIsNotConfiguredAndNothingIsCreatedOrWritten() = runTest {
        val store = store()

        val state = store.observe(ProviderId.OPENROUTER).first()
        store.observe(ProviderId.ANTHROPIC).first()

        assertEquals(KeyState.NotConfigured(), state)
        assertEquals(0, keys.getOrCreateCalls.get())
        assertEquals(0, recording!!.updates.get())
    }

    @Test
    fun observingNeverCreatesAKeyOrWrites() = runTest {
        val store = store()
        store.save(ProviderId.ANTHROPIC, FIRST_KEY)
        val creates = keys.getOrCreateCalls.get()
        val writes = recording!!.updates.get()
        keys.lose("secondbrain_anthropic_api_key_v1")

        val state = store.observe(ProviderId.ANTHROPIC).first()

        assertEquals(KeyState.KeyMissing(), state)
        assertEquals(creates, keys.getOrCreateCalls.get())
        assertEquals(writes, recording!!.updates.get())
    }

    private suspend fun cancellationOf(block: suspend () -> Unit): CancellationException {
        try {
            block()
        } catch (cancelled: CancellationException) {
            return cancelled
        }
        throw AssertionError("expected CancellationException")
    }
}
