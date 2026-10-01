package io.github.ygaray.voiceactionengine.keystore

import android.content.Context
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.ygaray.voiceactionengine.core.ProviderId
import io.github.ygaray.voiceactionengine.core.provider.CredentialLookup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.KeyStore
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

private const val SB_ALIAS = "secondbrain_anthropic_api_key_v1"
private const val CT_ANTHROPIC_ALIAS = "caltracker_api_key_v1"
private const val CT_OPENAI_ALIAS = "caltracker_openai_api_key_v1"
private const val CT_OPENROUTER_ALIAS = "caltracker_openrouter_api_key_v1"
private const val TEST_ALIAS_PREFIX = "vae_device_test_"

private const val SB_KEY = "sk-ant-device-sb-AB12" // secret-scan: allow (fake canary)
private const val CT_ANTHROPIC_KEY = "sk-ant-device-ct-CD34" // secret-scan: allow (fake canary)
private const val CT_OPENAI_KEY = "sk-device-openai-EF56" // secret-scan: allow (fake canary)
private const val CT_OPENROUTER_KEY = "sk-or-device-GH78" // secret-scan: allow (fake canary)
private const val STORE_KEY = "sk-ant-device-store-JK90" // secret-scan: allow (fake canary)

private const val RACERS = 8
private const val RACE_TIMEOUT_SECONDS = 60L
private const val MAX_ENCODED_LENGTH = 40
private const val FILL_MULTIPLIER = 37
private const val FILL_OFFSET = 11

/**
 * The real AndroidKeyStore proof, run on the tester device. It uses the library's public constructor and the
 * credential adapter, and stands the two existing apps up through verbatim copies of their crypto. The test package
 * owns its own key store namespace, so the literal legacy aliases below never touch a real app's keys; every alias a
 * test touches is deleted before and after each test.
 */
@RunWith(AndroidJUnit4::class)
class KeystoreDeviceTest {
    private val harness = DeviceHarness(InstrumentationRegistry.getInstrumentation().targetContext)

    @Before
    fun cleanBefore() {
        harness.deleteDeviceKeys()
    }

    @After
    fun cleanAfter() {
        harness.close()
    }

    @Test
    fun secondBrainLegacyBlobReadsBack() = runBlocking {
        val dataStore = harness.newDataStore()
        val (ct, iv) = SbLegacyCrypto.encryptToPair(SB_ALIAS, SB_KEY)
        dataStore.put(SB_SLOT, ct, iv)
        val store = ApiKeyStore(dataStore, listOf(SB_SLOT))

        assertEquals(KeyState.Ready("AB12"), store.read(ProviderId.ANTHROPIC))
        assertEquals(SB_KEY, presentKey(store, ProviderId.ANTHROPIC))
    }

    @Test
    fun calTrackerLegacyBlobsReadBack() = runBlocking {
        val dataStore = harness.newDataStore()
        val keys = mapOf(
            CT_ANTHROPIC_SLOT to CT_ANTHROPIC_KEY,
            CT_OPENAI_SLOT to CT_OPENAI_KEY,
            CT_OPENROUTER_SLOT to CT_OPENROUTER_KEY,
        )
        keys.forEach { (slot, key) ->
            val (ct, iv) = CtLegacyCrypto.encryptToPair(slot.alias, key)
            dataStore.put(slot, ct, iv)
        }
        val store = ApiKeyStore(dataStore, CT_SLOTS)

        keys.forEach { (slot, key) ->
            assertEquals(KeyState.Ready(key.takeLast(LAST_CHARS)), store.read(slot.provider))
            assertEquals(key, presentKey(store, slot.provider))
        }
    }

    @Test
    fun storeWritesLegacyCodeReads() = runBlocking {
        val sbData = harness.newDataStore()
        ApiKeyStore(sbData, listOf(SB_SLOT)).save(ProviderId.ANTHROPIC, SB_KEY)
        val (sbCt, sbIv) = sbData.pair(SB_SLOT)
        assertEquals(SB_KEY, SbLegacyCrypto.decryptPair(SB_ALIAS, sbCt, sbIv))

        val ctData = harness.newDataStore()
        val ctStore = ApiKeyStore(ctData, CT_SLOTS)
        val keys = mapOf(
            CT_ANTHROPIC_SLOT to CT_ANTHROPIC_KEY,
            CT_OPENAI_SLOT to CT_OPENAI_KEY,
            CT_OPENROUTER_SLOT to CT_OPENROUTER_KEY,
        )
        keys.forEach { (slot, key) -> ctStore.save(slot.provider, key) }
        keys.forEach { (slot, key) ->
            val (ct, iv) = ctData.pair(slot)
            assertEquals(key, CtLegacyCrypto.decryptPair(slot.alias, ct, iv))
        }
    }

    @Test
    fun frameworkBase64MatchesJavaBase64() {
        val samples = (0..MAX_ENCODED_LENGTH).map { length ->
            ByteArray(length) { index -> (index * FILL_MULTIPLIER + FILL_OFFSET).toByte() }
        } + listOf(
            byteArrayOf(0xFB.toByte(), 0xFF.toByte(), 0xFE.toByte()),
            byteArrayOf(0xFB.toByte(), 0xFF.toByte()),
            byteArrayOf(0xFB.toByte()),
        )
        val javaEncoder = java.util.Base64.getEncoder()

        samples.forEach { bytes ->
            val encoded = Base64.encodeToString(bytes, Base64.NO_WRAP)
            assertEquals("encoding of ${bytes.size} bytes", javaEncoder.encodeToString(bytes), encoded)
            assertTrue("decoding of ${bytes.size} bytes", bytes.contentEquals(Base64.decode(encoded, Base64.NO_WRAP)))
        }
    }

    @Test
    fun deletedDeviceKeyReadsKeyMissingAndCreatesNothing() = runBlocking {
        val store = ApiKeyStore(harness.newDataStore(), listOf(SB_SLOT))
        store.save(ProviderId.ANTHROPIC, STORE_KEY)
        harness.deviceKeyStore().deleteEntry(SB_ALIAS)

        assertEquals(KeyState.KeyMissing(), store.read(ProviderId.ANTHROPIC))
        assertNull(harness.deviceKeyStore().getKey(SB_ALIAS, null))
        assertEquals(
            CredentialLookup.Unreadable("key_missing"),
            KeystoreCredentialSource(store).credential(ProviderId.ANTHROPIC),
        )
        assertNull(harness.deviceKeyStore().getKey(SB_ALIAS, null))
    }

    @Test
    fun concurrentFirstUseKeepsOneKey() {
        val alias = harness.freshAlias()
        val slot = KeySlot(ProviderId.ANTHROPIC, alias, SB_SLOT.ciphertextKey, SB_SLOT.ivKey)
        val stores = (0 until RACERS).map { ApiKeyStore(harness.newDataStore(), listOf(slot)) }
        val barrier = CyclicBarrier(RACERS)
        val executor = Executors.newFixedThreadPool(RACERS)
        try {
            stores.mapIndexed { index, store ->
                executor.submit(
                    Callable {
                        barrier.await(RACE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                        runBlocking { store.save(ProviderId.ANTHROPIC, racerKey(index)) }
                    },
                )
            }.forEach { it.get(RACE_TIMEOUT_SECONDS, TimeUnit.SECONDS) }
        } finally {
            executor.shutdownNow()
        }

        runBlocking {
            stores.forEachIndexed { index, store ->
                assertEquals(KeyState.Ready(racerKey(index).takeLast(LAST_CHARS)), store.read(ProviderId.ANTHROPIC))
            }
        }
    }

    @Test
    fun tamperedCiphertextReadsDecryptFailed() = runBlocking {
        val dataStore = harness.newDataStore()
        val store = ApiKeyStore(dataStore, listOf(SB_SLOT))
        store.save(ProviderId.ANTHROPIC, STORE_KEY)
        val ctKey = stringPreferencesKey(SB_SLOT.ciphertextKey)
        dataStore.edit { prefs ->
            val bytes = Base64.decode(checkNotNull(prefs[ctKey]), Base64.NO_WRAP)
            bytes[0] = (bytes[0].toInt() xor 1).toByte()
            prefs[ctKey] = Base64.encodeToString(bytes, Base64.NO_WRAP)
        }

        assertEquals(KeyState.Unreadable("decrypt_failed"), store.read(ProviderId.ANTHROPIC))
    }
}

/** Owns everything one test creates on the device and on disk, and removes it again. */
private class DeviceHarness(private val context: Context) {
    private val scopes = CopyOnWriteArrayList<CoroutineScope>()
    private val files = CopyOnWriteArrayList<File>()
    private val aliases = CopyOnWriteArrayList(
        listOf(SB_ALIAS, CT_ANTHROPIC_ALIAS, CT_OPENAI_ALIAS, CT_OPENROUTER_ALIAS),
    )

    fun freshAlias(): String = (TEST_ALIAS_PREFIX + UUID.randomUUID()).also { aliases += it }

    fun newDataStore(): DataStore<Preferences> {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO).also { scopes += it }
        val file = File(context.cacheDir, "vae_device_test_${UUID.randomUUID()}.preferences_pb").also { files += it }
        return PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
    }

    fun deviceKeyStore(): KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    fun deleteDeviceKeys() {
        val keyStore = deviceKeyStore()
        aliases.filter { keyStore.getKey(it, null) != null }.forEach { keyStore.deleteEntry(it) }
    }

    fun close() {
        scopes.forEach { it.cancel() }
        files.forEach { it.delete() }
        deleteDeviceKeys()
    }
}

private fun racerKey(index: Int): String = "sk-ant-device-race-%04d".format(index) // secret-scan: allow (fake canary)

private suspend fun DataStore<Preferences>.put(slot: KeySlot, ciphertext: String, iv: String) {
    edit { prefs ->
        prefs[stringPreferencesKey(slot.ciphertextKey)] = ciphertext
        prefs[stringPreferencesKey(slot.ivKey)] = iv
    }
}

private suspend fun DataStore<Preferences>.pair(slot: KeySlot): Pair<String, String> {
    val prefs = data.first()
    val ciphertext = checkNotNull(prefs[stringPreferencesKey(slot.ciphertextKey)]) { "no stored ciphertext" }
    val iv = checkNotNull(prefs[stringPreferencesKey(slot.ivKey)]) { "no stored iv" }
    return ciphertext to iv
}

private suspend fun presentKey(store: ApiKeyStore, provider: ProviderId): String {
    val lookup = KeystoreCredentialSource(store).credential(provider)
    return (lookup as CredentialLookup.Present).credential.apiKey
}

private val SB_SLOT = KeySlot(ProviderId.ANTHROPIC, SB_ALIAS, "anthropic_api_key_ct", "anthropic_api_key_iv")
private val CT_ANTHROPIC_SLOT =
    KeySlot(ProviderId.ANTHROPIC, CT_ANTHROPIC_ALIAS, "anthropic_api_key_ct", "anthropic_api_key_iv")
private val CT_OPENAI_SLOT = KeySlot(ProviderId.OPENAI, CT_OPENAI_ALIAS, "openai_api_key_ct", "openai_api_key_iv")
private val CT_OPENROUTER_SLOT =
    KeySlot(ProviderId.OPENROUTER, CT_OPENROUTER_ALIAS, "openrouter_api_key_ct", "openrouter_api_key_iv")
private val CT_SLOTS = listOf(CT_ANTHROPIC_SLOT, CT_OPENAI_SLOT, CT_OPENROUTER_SLOT)
private const val LAST_CHARS = 4
