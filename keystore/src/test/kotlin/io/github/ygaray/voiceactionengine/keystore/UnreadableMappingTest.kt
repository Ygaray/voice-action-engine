package io.github.ygaray.voiceactionengine.keystore

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.ygaray.voiceactionengine.core.ProviderId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException
import java.security.KeyStoreException
import java.security.ProviderException
import java.security.UnrecoverableKeyException
import java.util.Base64
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

private const val ALIAS = "secondbrain_anthropic_api_key_v1"
private const val CT_KEY = "anthropic_api_key_ct"
private const val IV_KEY = "anthropic_api_key_iv"
private const val FAKE_KEY = "anthropic-secret-wxyz" // secret-scan: allow (fake canary)
private const val GCM_IV_BYTES = 12
private const val GCM_TAG_BYTES = 16
private const val LONG_IV_BYTES = 16
private const val OTHER_KEY_BYTES = 32
private const val BAD_KEY_BYTES = 5

/** A key handle whose material cannot be fetched, as a platform key store busy with a system error behaves. */
private class KeyWhoseEncodingThrows : SecretKey {
    override fun getAlgorithm(): String = "AES"

    override fun getFormat(): String = "RAW"

    override fun getEncoded(): ByteArray = throw ProviderException("key store busy")
}

/** Answers every lookup with [key] and never creates one. */
private class FixedKeyAccess(private val key: SecretKey) : KeyAccess {
    override fun existingKey(alias: String): SecretKey = key

    override fun getOrCreateKey(alias: String): SecretKey = throw AssertionError("a read must never create a key")
}

/** Every way a stored pair can fail to read ends in one specific cause, and nothing is created, written or cleared. */
class UnreadableMappingTest {
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

    private fun storeOver(dataStore: DataStore<Preferences>): ApiKeyStore =
        ApiKeyStore(dataStore, sbSlots(), Dispatchers.IO, keys)

    private fun unreadable(cause: String): KeyState = KeyState.Unreadable(cause)

    private fun encode(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

    private suspend fun write(ciphertext: String?, iv: String?) {
        prefs!!.dataStore.edit {
            ciphertext?.let { value -> it[stringPreferencesKey(CT_KEY)] = value }
            iv?.let { value -> it[stringPreferencesKey(IV_KEY)] = value }
        }
    }

    private suspend fun storedHalves(): Pair<String, String> {
        val snapshot = prefs!!.snapshot()
        return snapshot.getValue(CT_KEY) as String to snapshot.getValue(IV_KEY) as String
    }

    /** Reads [ProviderId.ANTHROPIC] and proves the read changed nothing and created no key. */
    private suspend fun readHarmlessly(store: ApiKeyStore): KeyState {
        val before = prefs!!.snapshot()
        val writes = recording!!.updates.get()
        val creates = keys.getOrCreateCalls.get()
        val state = store.read(ProviderId.ANTHROPIC)
        assertEquals(writes, recording!!.updates.get())
        assertEquals(before, prefs!!.snapshot())
        assertEquals(creates, keys.getOrCreateCalls.get())
        return state
    }

    private suspend fun savedStore(): ApiKeyStore = store().also { it.save(ProviderId.ANTHROPIC, FAKE_KEY) }

    @Test
    fun aKeyLookupThatThrowsReadsKeystoreUnavailable() = runTest {
        val store = savedStore()
        val failures = listOf(
            UnrecoverableKeyException("x"),
            KeyStoreException("x"),
            ProviderException("x"),
        )

        failures.forEach { failure ->
            keys.failLookupWith = failure
            assertEquals(unreadable("keystore_unavailable"), readHarmlessly(store))
        }
        keys.failLookupWith = null
        assertEquals(KeyState.Ready("wxyz"), store.read(ProviderId.ANTHROPIC))
    }

    @Test
    fun aKeyThatTheCipherRejectsReadsKeystoreUnavailableNotDecryptFailed() = runTest {
        val store = savedStore()
        val badLength = SecretKeySpec(ByteArray(BAD_KEY_BYTES), "AES")
        val failures = listOf(badLength, KeyWhoseEncodingThrows())

        failures.forEach { broken ->
            val over = ApiKeyStore(recording!!, sbSlots(), Dispatchers.IO, FixedKeyAccess(broken))
            assertEquals(unreadable("keystore_unavailable"), over.read(ProviderId.ANTHROPIC))
        }
        assertEquals(KeyState.Ready("wxyz"), store.read(ProviderId.ANTHROPIC))
    }

    @Test
    fun aFlippedCiphertextBitReadsDecryptFailed() = runTest {
        val store = savedStore()
        val (ciphertext, iv) = storedHalves()
        val bytes = Base64.getDecoder().decode(ciphertext)
        bytes[0] = (bytes[0].toInt() xor 1).toByte()
        write(encode(bytes), iv)

        assertEquals(unreadable("decrypt_failed"), readHarmlessly(store))
    }

    @Test
    fun aPairSealedUnderAnotherKeyReadsDecryptFailed() = runTest {
        val store = savedStore()
        val other = SecretKeySpec(ByteArray(OTHER_KEY_BYTES) { it.toByte() }, "AES")
        val sealed = AesGcm.seal(other, FAKE_KEY.toByteArray(Charsets.UTF_8))
        write(encode(sealed.ciphertext), encode(sealed.iv))

        assertEquals(unreadable("decrypt_failed"), readHarmlessly(store))
    }

    @Test
    fun aBadlyEncodedHalfReadsStoredValueMalformed() = runTest {
        val store = savedStore()
        val (ciphertext, iv) = storedHalves()

        write("not base64!!", iv)
        assertEquals(unreadable("stored_value_malformed"), readHarmlessly(store))
        write(ciphertext, "not base64!!")
        assertEquals(unreadable("stored_value_malformed"), readHarmlessly(store))
        write(ciphertext.take(2) + "\n" + ciphertext.drop(2), iv)
        assertEquals(unreadable("stored_value_malformed"), readHarmlessly(store))
    }

    @Test
    fun aWronglySizedIvOrCiphertextReadsStoredValueMalformed() = runTest {
        val store = savedStore()
        val (ciphertext, iv) = storedHalves()

        listOf(GCM_IV_BYTES - 1, LONG_IV_BYTES).forEach { size ->
            write(ciphertext, encode(ByteArray(size)))
            assertEquals(unreadable("stored_value_malformed"), readHarmlessly(store))
        }
        write(encode(ByteArray(GCM_TAG_BYTES - 1)), iv)
        assertEquals(unreadable("stored_value_malformed"), readHarmlessly(store))
    }

    @Test
    fun aDecryptedBlankValueReadsStoredValueMalformed() = runTest {
        val store = savedStore()
        val sealed = AesGcm.seal(keys.keyFor(ALIAS), "   ".toByteArray(Charsets.UTF_8))
        write(encode(sealed.ciphertext), encode(sealed.iv))

        assertEquals(unreadable("stored_value_malformed"), readHarmlessly(store))
    }

    @Test
    fun aTornPairReadsNotConfigured() = runTest {
        val store = savedStore()
        val (ciphertext, iv) = storedHalves()

        prefs!!.dataStore.edit { it.clear() }
        write(ciphertext, null)
        assertEquals(KeyState.NotConfigured(), readHarmlessly(store))
        prefs!!.dataStore.edit { it.clear() }
        write(null, iv)
        assertEquals(KeyState.NotConfigured(), readHarmlessly(store))
    }

    @Test
    fun aDataStoreThatFailsToReadReadsStorageUnreadable() = runTest {
        listOf(IOException("disk"), CorruptionException("corrupt")).forEach { failure ->
            val store = storeOver(ThrowingDataStore(failure))

            assertEquals(unreadable("storage_unreadable"), store.read(ProviderId.ANTHROPIC))
            assertEquals(unreadable("storage_unreadable"), store.readSecret(ProviderId.ANTHROPIC).state)
        }
        assertEquals(0, keys.getOrCreateCalls.get())
    }

    @Test
    fun aMisuseOfTheDataStoreIsThrownByReadAndObserveAlike() = runTest {
        val store = storeOver(ThrowingDataStore(IllegalStateException("multiple DataStores")))

        val fromRead = failureOf { store.read(ProviderId.ANTHROPIC) }
        val fromObserve = failureOf { store.observe(ProviderId.ANTHROPIC).toList() }

        assertEquals("multiple DataStores", fromRead.message)
        assertEquals("multiple DataStores", fromObserve.message)
        assertEquals(0, keys.getOrCreateCalls.get())
    }

    @Test
    fun aReadThatIsCancelledEndsCancelledAndNeverBecomesAState() = runTest {
        val store = storeOver(NeverEmittingDataStore)
        var result: KeyState? = null
        val job = launch { result = store.read(ProviderId.ANTHROPIC) }
        runCurrent()

        job.cancelAndJoin()

        assertTrue(job.isCancelled)
        assertNull(result)
    }

    @Test
    fun aCancellationFromTheDataStoreIsNeverMapped() = runTest {
        val store = storeOver(ThrowingDataStore(CancellationException("stop")))

        val thrown = cancellationOf { store.read(ProviderId.ANTHROPIC) }

        assertEquals("stop", thrown.message)
    }

    @Test
    fun theCauseVocabularyIsExactlyTheFiveStableCodes() {
        val expected = setOf(
            "key_missing",
            "keystore_unavailable",
            "decrypt_failed",
            "stored_value_malformed",
            "storage_unreadable",
        )

        assertEquals(expected, KeystoreCauses.vocabulary)
        assertEquals("key_missing", KeystoreCauses.keyMissingLookup.cause)
    }

    private suspend fun failureOf(block: suspend () -> Unit): IllegalStateException {
        try {
            block()
        } catch (failure: IllegalStateException) {
            return failure
        }
        throw AssertionError("expected IllegalStateException")
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
