package io.github.ygaray.voiceactionengine.keystore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.ygaray.voiceactionengine.core.ProviderId
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.Base64

/**
 * Stores one bring-your-own API key per provider, encrypted with a device key, in the app's own preferences DataStore.
 *
 * The app owns and injects the [DataStore]; this library never creates one, because a second DataStore on the same file
 * throws at runtime. The app also supplies the [KeySlot] table that says which names each provider's key lives under.
 * The plaintext key never leaves through a public member.
 */
public class ApiKeyStore internal constructor(
    private val dataStore: DataStore<Preferences>,
    slots: List<KeySlot>,
    private val ioDispatcher: CoroutineDispatcher,
    private val keyAccess: KeyAccess,
) {
    private val slotsByProvider: Map<ProviderId, KeySlot> = slots.toList().associateBy { it.provider }
    private val writeMutex = Mutex()
    private val encoder: Base64.Encoder = Base64.getEncoder()
    private val decoder: Base64.Decoder = Base64.getDecoder()

    /**
     * Encrypts [apiKey] (trimmed) and stores it for [provider], replacing any previous one.
     *
     * @throws IllegalArgumentException when [provider] has no slot or the trimmed key is empty. Neither message
     * contains the key.
     */
    public suspend fun save(provider: ProviderId, apiKey: String) {
        val slot = requireNotNull(slotsByProvider[provider]) { "No key slot for provider $provider" }
        val trimmed = apiKey.trim()
        require(trimmed.isNotEmpty()) { "API key must not be blank" }
        writeMutex.withLock {
            val sealed = withContext(ioDispatcher) {
                AesGcm.seal(keyAccess.getOrCreateKey(slot.alias), trimmed.toByteArray(Charsets.UTF_8))
            }
            val ciphertext = encoder.encodeToString(sealed.ciphertext)
            val iv = encoder.encodeToString(sealed.iv)
            dataStore.edit { prefs ->
                prefs[stringPreferencesKey(slot.ciphertextKey)] = ciphertext
                prefs[stringPreferencesKey(slot.ivKey)] = iv
            }
        }
    }

    /**
     * Reads what is stored for [provider]. A provider without a slot, or without a stored pair, is
     * [KeyState.NotConfigured]. Reading never creates a key and never changes storage.
     */
    public suspend fun read(provider: ProviderId): KeyState {
        val slot = slotsByProvider[provider] ?: return KeyState.NotConfigured()
        val prefs = dataStore.data.first()
        val ciphertext = prefs[stringPreferencesKey(slot.ciphertextKey)]
        val iv = prefs[stringPreferencesKey(slot.ivKey)]
        return if (ciphertext == null || iv == null) {
            KeyState.NotConfigured()
        } else {
            withContext(ioDispatcher) { open(slot, iv, ciphertext) }
        }
    }

    private fun open(slot: KeySlot, iv: String, ciphertext: String): KeyState {
        val key = keyAccess.existingKey(slot.alias) ?: return KeyState.KeyMissing()
        val plain = AesGcm.open(key, decoder.decode(iv), decoder.decode(ciphertext))
        return KeyState.Ready(lastFour(String(plain, Charsets.UTF_8)))
    }

    /** Prints the providers only: never a key or a name of storage. */
    override fun toString(): String = "ApiKeyStore(providers=${slotsByProvider.keys.toList()})"

    private fun lastFour(key: String): String = if (key.length > LAST_CHARS) key.takeLast(LAST_CHARS) else ""
}

private const val LAST_CHARS = 4
