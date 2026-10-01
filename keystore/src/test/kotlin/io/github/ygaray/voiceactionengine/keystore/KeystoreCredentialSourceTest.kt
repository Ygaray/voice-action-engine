package io.github.ygaray.voiceactionengine.keystore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.ygaray.voiceactionengine.core.ProviderId
import io.github.ygaray.voiceactionengine.core.provider.CredentialLookup
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
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
import java.security.UnrecoverableKeyException
import java.util.Base64

private const val ALIAS = "secondbrain_anthropic_api_key_v1"
private const val CT_KEY = "anthropic_api_key_ct"
private const val IV_KEY = "anthropic_api_key_iv"
private const val FAKE_KEY = "anthropic-secret-wxyz" // secret-scan: allow (fake canary)

/** Every store state reaches the engine as the right lookup, and the adapter never throws except on cancellation. */
class KeystoreCredentialSourceTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val keys = SoftwareKeyAccess()
    private var prefs: TempPreferences? = null

    @After
    fun close() {
        prefs?.close()
    }

    private fun store(): ApiKeyStore {
        val temp = TempPreferences(folder).also { prefs = it }
        return ApiKeyStore(temp.dataStore, sbSlots(), Dispatchers.IO, keys)
    }

    private fun sourceOver(dataStore: DataStore<Preferences>): KeystoreCredentialSource =
        KeystoreCredentialSource(ApiKeyStore(dataStore, sbSlots(), Dispatchers.IO, keys))

    private suspend fun savedSource(): KeystoreCredentialSource {
        val store = store()
        store.save(ProviderId.ANTHROPIC, FAKE_KEY)
        return KeystoreCredentialSource(store)
    }

    private fun encode(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

    @Test
    fun aSavedKeyIsPresentAndStampedWithTheAskedProvider() = runTest {
        val lookup = savedSource().credential(ProviderId.ANTHROPIC)

        val credential = (lookup as CredentialLookup.Present).credential
        assertEquals(ProviderId.ANTHROPIC, credential.provider)
        assertEquals(FAKE_KEY, credential.apiKey)
    }

    @Test
    fun aNeverStoredKeyAndAnUnmappedProviderAreMissing() = runTest {
        val empty = KeystoreCredentialSource(store())
        assertEquals(CredentialLookup.Missing(), empty.credential(ProviderId.ANTHROPIC))
        assertEquals(CredentialLookup.Missing(), empty.credential(ProviderId.OPENAI))
    }

    @Test
    fun anUnmappedProviderIsMissingEvenWhenAnotherProviderHasAKey() = runTest {
        assertEquals(CredentialLookup.Missing(), savedSource().credential(ProviderId.OPENAI))
    }

    @Test
    fun aLostDeviceKeyIsUnreadableKeyMissing() = runTest {
        val source = savedSource()
        keys.lose(ALIAS)

        assertEquals(CredentialLookup.Unreadable("key_missing"), source.credential(ProviderId.ANTHROPIC))
    }

    @Test
    fun aFlippedCiphertextBitIsUnreadableDecryptFailed() = runTest {
        val store = store()
        store.save(ProviderId.ANTHROPIC, FAKE_KEY)
        val snapshot = prefs!!.snapshot()
        val bytes = Base64.getDecoder().decode(snapshot.getValue(CT_KEY) as String)
        bytes[0] = (bytes[0].toInt() xor 1).toByte()
        prefs!!.dataStore.edit { it[stringPreferencesKey(CT_KEY)] = encode(bytes) }

        val lookup = KeystoreCredentialSource(store).credential(ProviderId.ANTHROPIC)

        assertEquals(CredentialLookup.Unreadable("decrypt_failed"), lookup)
    }

    @Test
    fun aKeyLookupThatThrowsIsUnreadableKeystoreUnavailable() = runTest {
        val source = savedSource()
        keys.failLookupWith = UnrecoverableKeyException("x")

        assertEquals(CredentialLookup.Unreadable("keystore_unavailable"), source.credential(ProviderId.ANTHROPIC))
    }

    @Test
    fun aDataStoreThatFailsToReadIsUnreadableStorageUnreadable() = runTest {
        val source = sourceOver(ThrowingDataStore(IOException("disk")))

        assertEquals(CredentialLookup.Unreadable("storage_unreadable"), source.credential(ProviderId.ANTHROPIC))
    }

    @Test
    fun aBlankDecryptedValueIsUnreadableStoredValueMalformedAndDoesNotThrow() = runTest {
        val source = savedSource()
        val sealed = AesGcm.seal(keys.keyFor(ALIAS), "   ".toByteArray(Charsets.UTF_8))
        prefs!!.dataStore.edit {
            it[stringPreferencesKey(CT_KEY)] = encode(sealed.ciphertext)
            it[stringPreferencesKey(IV_KEY)] = encode(sealed.iv)
        }

        assertEquals(CredentialLookup.Unreadable("stored_value_malformed"), source.credential(ProviderId.ANTHROPIC))
    }

    @Test
    fun aLookupCancelledFromOutsideEndsCancelled() = runTest {
        val source = sourceOver(NeverEmittingDataStore)
        var result: CredentialLookup? = null
        val job = launch { result = source.credential(ProviderId.ANTHROPIC) }
        runCurrent()

        job.cancelAndJoin()

        assertTrue(job.isCancelled)
        assertNull(result)
    }

    @Test
    fun aCancellationFromTheDataStoreIsNotMapped() = runTest {
        val source = sourceOver(ThrowingDataStore(CancellationException("stop")))

        val thrown = try {
            source.credential(ProviderId.ANTHROPIC)
            null
        } catch (cancelled: CancellationException) {
            cancelled
        }

        assertEquals("stop", thrown?.message)
    }

    @Test
    fun noLookupEverCreatesAKey() = runTest {
        val source = savedSource()
        val creates = keys.getOrCreateCalls.get()

        source.credential(ProviderId.ANTHROPIC)
        keys.lose(ALIAS)
        source.credential(ProviderId.ANTHROPIC)
        source.credential(ProviderId.OPENAI)

        assertEquals(creates, keys.getOrCreateCalls.get())
    }
}
