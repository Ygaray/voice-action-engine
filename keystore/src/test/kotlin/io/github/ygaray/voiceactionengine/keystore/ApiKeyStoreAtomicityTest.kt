package io.github.ygaray.voiceactionengine.keystore

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.ygaray.voiceactionengine.core.ProviderId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.security.UnrecoverableKeyException
import java.util.Base64
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

private const val ALIAS = "caltracker_openai_api_key_v1"
private const val CT_KEY = "openai_api_key_ct"
private const val IV_KEY = "openai_api_key_iv"
private const val CONCURRENT_SAVES = 32
private const val FAKE_KEY = "openai-secret-wxyz" // secret-scan: allow (fake canary)
private const val FAKE_PADDED = "  openai-secret-wxyz  " // secret-scan: allow (fake canary)
private const val FAKE_NEW = "openai-secret-new-abcd" // secret-scan: allow (fake canary)
private const val FAKE_ROUTER = "router-secret-new-efgh" // secret-scan: allow (fake canary)
private const val FAKE_UNMAPPED = "unmapped-secret-9999" // secret-scan: allow (fake canary)
private const val FAKE_PREFIX = "openai-secret-" // secret-scan: allow (fake canary)

/** What a save, a refused save and a delete do to storage: one update, trimmed, serialized, and nothing on failure. */
class ApiKeyStoreAtomicityTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val keys = SoftwareKeyAccess()
    private var prefs: TempPreferences? = null
    private var recording: RecordingDataStore? = null
    private val observer = CoroutineScope(Dispatchers.Default)

    @After
    fun close() {
        observer.cancel()
        prefs?.close()
    }

    private fun store(): ApiKeyStore {
        val temp = TempPreferences(folder).also { prefs = it }
        val recorder = RecordingDataStore(temp.dataStore).also { recording = it }
        return ApiKeyStore(recorder, ctSlots(), Dispatchers.IO, keys)
    }

    private fun updates(): Int = recording!!.updates.get()

    private suspend fun storedPair(): Pair<ByteArray, ByteArray> {
        val snapshot = prefs!!.snapshot()
        val ct = Base64.getDecoder().decode(snapshot.getValue(CT_KEY) as String)
        val iv = Base64.getDecoder().decode(snapshot.getValue(IV_KEY) as String)
        return ct to iv
    }

    @Test
    fun aKeyIsTrimmedBeforeItIsEncrypted() = runTest {
        val store = store()

        store.save(ProviderId.OPENAI, FAKE_PADDED)

        assertEquals(KeyState.Ready("wxyz"), store.read(ProviderId.OPENAI))
        val (ct, iv) = storedPair()
        assertEquals("openai-secret-wxyz", String(AesGcm.open(keys.keyFor(ALIAS), iv, ct), Charsets.UTF_8))
    }

    @Test
    fun aBlankKeyIsRefusedWithoutEchoAndTheOldPairSurvives() = runTest {
        val store = store()
        store.save(ProviderId.OPENAI, FAKE_KEY)
        val before = prefs!!.snapshot()
        val writes = updates()

        listOf("", "   \t ").forEach { blank ->
            val failure = refusalOf { store.save(ProviderId.OPENAI, blank) }
            assertFalse(failure.message.orEmpty().contains("   \t "))
        }

        assertEquals(writes, updates())
        assertEquals(before, prefs!!.snapshot())
        assertEquals(KeyState.Ready("wxyz"), store.read(ProviderId.OPENAI))
    }

    @Test
    fun saveAndDeleteForAnUnmappedProviderNameTheProviderAndNotTheKey() = runTest {
        val store = TempPreferences(folder).also { prefs = it }.let {
            ApiKeyStore(it.dataStore, sbSlots(), Dispatchers.IO, keys)
        }
        val secret = FAKE_UNMAPPED

        val onSave = refusalOf { store.save(ProviderId.OPENROUTER, secret) }
        val onDelete = refusalOf { store.delete(ProviderId.OPENROUTER) }

        listOf(onSave, onDelete).forEach {
            assertTrue(it.message.orEmpty().contains("openrouter"))
            assertFalse(it.message.orEmpty().contains(secret))
        }
        assertEquals(KeyState.NotConfigured(), store.read(ProviderId.OPENROUTER))
    }

    @Test
    fun oneSaveIsOneUpdateThatSetsBothKeys() = runTest {
        val store = store()

        store.save(ProviderId.OPENAI, FAKE_KEY)

        assertEquals(1, updates())
        assertEquals(setOf(CT_KEY, IV_KEY), prefs!!.snapshot().keys)
    }

    @Test
    fun anObserverNeverSeesHalfAPair() = runTest {
        val store = store()
        val seen = CopyOnWriteArrayList<Int>()
        val running = AtomicBoolean(true)
        val firstSnapshot = CompletableDeferred<Unit>()
        val job = observer.launch {
            recording!!.data.takeWhile { running.get() }.collect { snapshot ->
                seen.add(pairKeysIn(snapshot))
                firstSnapshot.complete(Unit)
            }
        }
        firstSnapshot.await()

        repeat(REPLACEMENTS) { store.save(ProviderId.OPENAI, "$FAKE_PREFIX$it-wxyz") }
        store.delete(ProviderId.OPENAI)
        running.set(false)
        store.save(ProviderId.OPENAI, "${FAKE_PREFIX}final-wxyz")
        job.join()

        assertTrue(seen.isNotEmpty())
        assertTrue(seen.toString(), seen.all { it == 0 || it == 2 })
    }

    @Test
    fun concurrentSavesLeaveOneWholePairThatDecryptsToOneOfThem() = runTest {
        val store = store()
        val inputs = (0 until CONCURRENT_SAVES).map { "$FAKE_PREFIX$it-wxyz" }

        withContext(Dispatchers.Default) {
            inputs.map { async { store.save(ProviderId.OPENAI, it) } }.awaitAll()
        }

        assertEquals(CONCURRENT_SAVES, updates())
        val (ct, iv) = storedPair()
        val plain = String(AesGcm.open(keys.keyFor(ALIAS), iv, ct), Charsets.UTF_8)
        assertTrue(plain, plain in inputs)
        assertEquals(1, keys.createdKeys.get())
    }

    @Test
    fun aFailedKeyLookupPropagatesCreatesNoKeyAndChangesNothing() = runTest {
        val store = store()
        store.save(ProviderId.OPENAI, FAKE_KEY)
        val before = prefs!!.snapshot()
        val created = keys.createdKeys.get()
        val writes = updates()
        val failure = UnrecoverableKeyException("lookup failed")
        keys.failLookupWith = failure

        val thrown = keyFailureOf { store.save(ProviderId.OPENAI, FAKE_NEW) }
        val thrownForNewProvider = keyFailureOf { store.save(ProviderId.OPENROUTER, FAKE_ROUTER) }
        keys.failLookupWith = null

        // The coroutine machinery may hand back a copy of the exception, so compare what it says.
        assertEquals(failure.message, thrown.message)
        assertEquals(failure.message, thrownForNewProvider.message)
        assertEquals(created, keys.createdKeys.get())
        assertEquals(writes, updates())
        assertEquals(before, prefs!!.snapshot())
        assertEquals(KeyState.Ready("wxyz"), store.read(ProviderId.OPENAI))
    }

    @Test
    fun oneDeleteIsOneUpdateThatRemovesBothKeys() = runTest {
        val store = store()
        store.save(ProviderId.OPENAI, FAKE_KEY)
        val writes = updates()

        store.delete(ProviderId.OPENAI)

        assertEquals(writes + 1, updates())
        assertEquals(emptySet<String>(), prefs!!.snapshot().keys)
        assertEquals(KeyState.NotConfigured(), store.read(ProviderId.OPENAI))
    }

    private suspend fun refusalOf(block: suspend () -> Unit): IllegalArgumentException {
        try {
            block()
        } catch (refused: IllegalArgumentException) {
            return refused
        }
        throw AssertionError("expected IllegalArgumentException")
    }

    private suspend fun keyFailureOf(block: suspend () -> Unit): UnrecoverableKeyException {
        try {
            block()
        } catch (failed: UnrecoverableKeyException) {
            return failed
        }
        throw AssertionError("expected UnrecoverableKeyException")
    }

    private fun pairKeysIn(snapshot: Preferences): Int =
        listOf(CT_KEY, IV_KEY).count { snapshot.contains(stringPreferencesKey(it)) }

    private companion object {
        const val REPLACEMENTS = 20
    }
}
