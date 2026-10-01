package io.github.ygaray.voiceactionengine.keystore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.ygaray.voiceactionengine.core.ProviderId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.Base64

/**
 * Stores one bring-your-own API key per provider, encrypted with a device key, in the app's own preferences DataStore.
 *
 * The app owns and injects the [DataStore]; this library never creates one, because a second DataStore on the same file
 * throws at runtime. The app also supplies the [KeySlot] table that says which names each provider's key lives under.
 * The plaintext key never leaves through a public member.
 *
 * To adopt it, inject the app's existing DataStore (for SecondBrain its hoisted `app_preferences` singleton) and a
 * [KeySlot] table that names the app's existing alias and preference names verbatim. No stored key is copied or
 * migrated: values already written by the app read back as they are, and values this store writes can be read by the
 * app's older code.
 */
public class ApiKeyStore internal constructor(
    private val dataStore: DataStore<Preferences>,
    slots: List<KeySlot>,
    private val ioDispatcher: CoroutineDispatcher,
    private val keyAccess: KeyAccess,
) {
    private val slotsByProvider: Map<ProviderId, KeySlot> = indexValidated(slots)
    private val writeMutex = Mutex()
    private val encoder: Base64.Encoder = Base64.getEncoder()
    private val reader = SecretReader(keyAccess)

    /** Builds a store over the app's [dataStore] and [slots], doing its blocking work on [Dispatchers.IO]. */
    public constructor(dataStore: DataStore<Preferences>, slots: List<KeySlot>) :
        this(dataStore, slots, Dispatchers.IO, AndroidKeyStoreKeyAccess)

    /** As the two-argument constructor, but doing its blocking work on [ioDispatcher]. */
    public constructor(dataStore: DataStore<Preferences>, slots: List<KeySlot>, ioDispatcher: CoroutineDispatcher) :
        this(dataStore, slots, ioDispatcher, AndroidKeyStoreKeyAccess)

    /**
     * Encrypts [apiKey] (trimmed) and stores it for [provider], replacing any previous one.
     *
     * Saving fails loudly rather than storing a key it cannot protect, so a caller should surface "could not store the
     * key" to the user. No message contains the key.
     *
     * @throws IllegalArgumentException when [provider] has no slot or the trimmed key is empty.
     * @throws java.security.GeneralSecurityException when the device key or the cipher cannot be used.
     * @throws java.security.ProviderException when the platform key store fails while looking up, creating or using the
     * device key.
     * @throws java.io.IOException when the preferences cannot be written.
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
     * Removes the stored key of [provider]: its ciphertext and initialisation vector go in one update. Deleting when
     * nothing is stored is a no-op. The device key under the slot's alias is left in place and a later save reuses it,
     * because a store must never destroy a key over an error that might be transient.
     *
     * @throws IllegalArgumentException when [provider] has no slot. The message names the provider only.
     */
    public suspend fun delete(provider: ProviderId) {
        val slot = requireNotNull(slotsByProvider[provider]) { "No key slot for provider $provider" }
        writeMutex.withLock {
            dataStore.edit { prefs ->
                prefs.remove(stringPreferencesKey(slot.ciphertextKey))
                prefs.remove(stringPreferencesKey(slot.ivKey))
            }
        }
    }

    /**
     * Reads what is stored for [provider]. A provider without a slot, or without a stored pair, is
     * [KeyState.NotConfigured]. Reading never creates a key and never changes storage. A failure to read the
     * preferences (an I/O failure or a corrupt file) is [KeyState.Unreadable]; a misuse of the injected DataStore, such
     * as a second DataStore on the same file, is thrown rather than reported as a state.
     */
    public suspend fun read(provider: ProviderId): KeyState = readSecret(provider).state

    /**
     * Follows the state of [provider] as its stored pair changes: it emits the current state first, then again after
     * every save and delete, including a replacement that ends in the same last four characters, but not for a write to
     * other preferences of the app. A corrupt or
     * unreadable value is emitted as a state and the stream carries on; only a failure to read the preferences at all
     * ends it, with one final [KeyState.Unreadable]. The flow is cold, read-only and never creates a key.
     */
    public fun observe(provider: ProviderId): Flow<KeyState> =
        slotsByProvider[provider]?.let { observeSlot(it) } ?: flowOf(KeyState.NotConfigured())

    // The DataStore is the app's whole preferences file, so it also emits for unrelated writes. Only a change of this
    // slot's stored pair matters; every save writes a fresh initialisation vector, so a replacement always changes it.
    private fun observeSlot(slot: KeySlot): Flow<KeyState> = dataStore.data
        .distinctUntilChangedBy { prefs ->
            prefs[stringPreferencesKey(slot.ciphertextKey)] to prefs[stringPreferencesKey(slot.ivKey)]
        }
        .map { prefs -> reader.open(slot, prefs).state }
        .catch { failure -> if (failure is IOException) emit(KeystoreCauses.storageUnreadable) else throw failure }
        .flowOn(ioDispatcher)

    internal suspend fun readSecret(provider: ProviderId): SecretRead =
        slotsByProvider[provider]?.let { openSlot(it) } ?: SecretRead.Failed(KeyState.NotConfigured())

    private suspend fun openSlot(slot: KeySlot): SecretRead {
        val prefs = storedPreferences() ?: return SecretRead.Failed(KeystoreCauses.storageUnreadable)
        return withContext(ioDispatcher) { reader.open(slot, prefs) }
    }

    // Null means the preferences could not be read: an I/O failure, which includes a corrupt file. Cancellation is
    // never mistaken for that, and neither is a programming error such as a second DataStore on the same file:
    // those propagate, as they do from observe.
    private suspend fun storedPreferences(): Preferences? = try {
        dataStore.data.first()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (ignored: IOException) {
        null
    }

    /** Prints the providers only: never a key or a name of storage. */
    override fun toString(): String = "ApiKeyStore(providers=${slotsByProvider.keys.toList()})"
}

/**
 * Copies the app's table and refuses a shape that would let one provider's save overwrite another's key: no rows, a
 * repeated provider, a repeated alias, or a preference name used twice anywhere in the table. Messages carry names
 * only.
 */
private fun indexValidated(slots: List<KeySlot>): Map<ProviderId, KeySlot> {
    val copy = slots.toList()
    require(copy.isNotEmpty()) { "Key slot table must not be empty" }
    requireDistinct(copy.map { it.provider.value }, "provider")
    requireDistinct(copy.map { it.alias }, "alias")
    requireDistinct(copy.flatMap { listOf(it.ciphertextKey, it.ivKey) }, "preference key")
    return copy.associateBy { it.provider }
}

private fun requireDistinct(names: List<String>, what: String) {
    val repeated = names.groupingBy { it }.eachCount().entries.firstOrNull { it.value > 1 }?.key
    require(repeated == null) { "Key slot table repeats $what $repeated" }
}
