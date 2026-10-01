package io.github.ygaray.voiceactionengine.keystore

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.ygaray.voiceactionengine.core.ProviderId
import io.github.ygaray.voiceactionengine.core.provider.CredentialLookup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.security.GeneralSecurityException

private const val FAKE_KEY = "sk-ant-jvm-platform-9999" // secret-scan: allow (fake canary)
private const val SEEDED_KEY = "sk-ant-seeded-before-8888" // secret-scan: allow (fake canary)
private const val KEYSTORE_UNAVAILABLE = "keystore_unavailable"

/**
 * The public constructor wires the platform key access. A plain JVM has no AndroidKeyStore provider, so every lookup
 * fails there; the failure must surface as "unreadable" on a read and as a thrown error on a save, and must never fall
 * through to creating a key.
 */
class PlatformKeyAccessJvmTest {
    @get:Rule
    val folder = TemporaryFolder()

    private var prefs: TempPreferences? = null

    @After
    fun close() {
        prefs?.close()
    }

    private suspend fun seededStore(): ApiKeyStore {
        val temp = TempPreferences(folder).also { prefs = it }
        val slot = sbSlots().single()
        val sealed = LegacyWriters.seal(SoftwareKeyAccess().getOrCreateKey(slot.alias), SEEDED_KEY)
        temp.dataStore.edit {
            it[stringPreferencesKey(slot.ciphertextKey)] = sealed.first
            it[stringPreferencesKey(slot.ivKey)] = sealed.second
        }
        return ApiKeyStore(temp.dataStore, sbSlots(), Dispatchers.Unconfined)
    }

    @Test
    fun aStoredPairReadsUnreadableBecauseTheKeyStoreCannotBeQueried() = runTest {
        val store = seededStore()

        assertEquals(KeyState.Unreadable(KEYSTORE_UNAVAILABLE), store.read(ProviderId.ANTHROPIC))
        assertEquals(
            CredentialLookup.Unreadable(KEYSTORE_UNAVAILABLE),
            KeystoreCredentialSource(store).credential(ProviderId.ANTHROPIC),
        )
    }

    @Test
    fun aSaveFailsLoudlyWithoutTheKeyAndLeavesStorageUnchanged() = runTest {
        val store = seededStore()
        val before = prefs!!.snapshot()

        val failure = thrownSecurityFailure { store.save(ProviderId.ANTHROPIC, FAKE_KEY) }

        assertNotNull("a save must fail when the platform lookup fails", failure)
        generateSequence<Throwable>(failure) { it.cause }.forEach { link ->
            assertFalse("a message carries the key", link.message.orEmpty().contains(FAKE_KEY))
        }
        assertEquals(before, prefs!!.snapshot())
    }

    @Test
    fun aNeverStoredProviderStillReadsNotConfigured() = runTest {
        val temp = TempPreferences(folder).also { prefs = it }
        val store = ApiKeyStore(temp.dataStore, ctSlots(), Dispatchers.Unconfined)

        assertEquals(KeyState.NotConfigured(), store.read(ProviderId.OPENAI))
    }

    // Returns the general-security failure a save threw, or null when it did not throw one.
    private suspend fun thrownSecurityFailure(block: suspend () -> Unit): GeneralSecurityException? = try {
        block()
        null
    } catch (failure: GeneralSecurityException) {
        failure
    }
}
