package io.github.ygaray.voiceactionengine.sample.keys

import io.github.ygaray.voiceactionengine.core.ProviderId
import io.github.ygaray.voiceactionengine.keystore.ApiKeyStore
import io.github.ygaray.voiceactionengine.keystore.KeyState

/**
 * The sample's seam over the key store, so host tests can swap in an in-memory vault. Every key the sample uses is saved
 * and read through this seam, and in the app it is always backed by the keystore library.
 */
internal interface KeyVault {
    /** Encrypts and stores [key] for [provider]. The key is never logged or echoed. */
    suspend fun save(provider: ProviderId, key: String)

    /** What is stored for [provider]. */
    suspend fun read(provider: ProviderId): KeyState

    /** Removes the stored key of [provider]. */
    suspend fun delete(provider: ProviderId)
}

/** The real vault: delegates one to one to the keystore library's [ApiKeyStore]. */
internal class ApiKeyStoreVault(private val store: ApiKeyStore) : KeyVault {
    override suspend fun save(provider: ProviderId, key: String) = store.save(provider, key)

    override suspend fun read(provider: ProviderId): KeyState = store.read(provider)

    override suspend fun delete(provider: ProviderId) = store.delete(provider)

    /** Prints the type only. */
    override fun toString(): String = "ApiKeyStoreVault"
}

/** Moves keys placed on the device for testing into the vault. Only debug builds can supply one. */
internal interface KeyImport {
    /** Imports every provider that has a key file waiting, in provider order, and reports one entry per provider. */
    suspend fun importAll(): List<ImportReport>
}

/**
 * What happened to one provider's pushed key. Carries state words and booleans only: never the key, any part of it, or
 * any name of storage.
 *
 * @property state one of [Ready], [NotConfigured], [KeyMissing], [Unreadable] (the vault's answer after saving),
 * [ABSENT_FILE], [REJECTED] (the file held a blank value) or [SAVE_FAILED].
 * @property cause the stable cause code when [state] is [Unreadable], or the failure type when [state] is
 * [SAVE_FAILED]; null otherwise.
 * @property plaintextFileDeleted true when the plaintext file is gone after the import, or was never there.
 * @property plaintextInDatastore whether the plaintext was found in the app's DataStore directory; null when no scan ran.
 */
internal class ImportReport(
    val provider: ProviderId,
    val state: String,
    val cause: String?,
    val plaintextFileDeleted: Boolean,
    val plaintextInDatastore: Boolean?,
) {
    /** Prints exactly the report fields. */
    override fun toString(): String =
        "ImportReport(provider=$provider, state=$state, cause=$cause, " +
            "plaintextFileDeleted=$plaintextFileDeleted, plaintextInDatastore=$plaintextInDatastore)"

    companion object {
        const val READY = "Ready"
        const val NOT_CONFIGURED = "NotConfigured"
        const val KEY_MISSING = "KeyMissing"
        const val UNREADABLE = "Unreadable"
        const val ABSENT_FILE = "absent_file"
        const val REJECTED = "rejected"
        const val SAVE_FAILED = "save_failed"

        /** The report word for a vault answer. An unknown future leaf is named, never hidden. */
        fun stateWord(state: KeyState): String = when (state) {
            is KeyState.Ready -> READY
            is KeyState.NotConfigured -> NOT_CONFIGURED
            is KeyState.KeyMissing -> KEY_MISSING
            is KeyState.Unreadable -> UNREADABLE
            else -> "Unknown"
        }
    }
}
