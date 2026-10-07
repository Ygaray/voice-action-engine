package io.github.ygaray.voiceactionengine.sample.keys

import io.github.ygaray.voiceactionengine.core.ProviderId
import io.github.ygaray.voiceactionengine.core.provider.CredentialLookup
import io.github.ygaray.voiceactionengine.keystore.ApiKeyStore
import io.github.ygaray.voiceactionengine.keystore.KeyState
import io.github.ygaray.voiceactionengine.keystore.KeystoreCredentialSource

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

    /**
     * A short non-reversible fingerprint of the key stored for [provider], for the screen only; null when there is no
     * readable key or it cannot be computed. Never log it, report it or put it in evidence.
     */
    suspend fun fingerprint(provider: ProviderId): String? = null
}

/** The real vault: delegates one to one to the keystore library's [ApiKeyStore]. */
internal class ApiKeyStoreVault(private val store: ApiKeyStore) : KeyVault {
    override suspend fun save(provider: ProviderId, key: String) = store.save(provider, key)

    override suspend fun read(provider: ProviderId): KeyState = store.read(provider)

    override suspend fun delete(provider: ProviderId) = store.delete(provider)

    // Goes through the library's public credential source; the key lives only for the length of this call.
    override suspend fun fingerprint(provider: ProviderId): String? =
        when (val lookup = KeystoreCredentialSource(store).credential(provider)) {
            is CredentialLookup.Present -> KeyUx.fingerprint(lookup.credential.apiKey)
            else -> null
        }

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

/** What the user should do about a key that cannot be read. */
internal enum class KeyAction {
    /** The stored key is lost or damaged: the user must type it again. */
    REENTER_KEY,

    /** The failure may pass by itself: try again. */
    TRANSIENT_RETRY,
}

/** The sample's wording and actions for the keystore library's states and cause codes. */
internal object KeyUx {
    private const val FINGERPRINT_BYTES = 3

    /**
     * The first six lowercase hex digits of the SHA-256 of [key]. A screen-only 24-bit tag: it identifies which key is
     * stored without revealing any character of it, and it never goes into a log, evidence line or `toString`.
     */
    fun fingerprint(key: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest(key.toByteArray(Charsets.UTF_8))
        return digest.take(FINGERPRINT_BYTES).joinToString("") { "%02x".format(it) }
    }

    /**
     * The action for a cause code of [KeyState.Unreadable]. The codes are an open set, so anything not known means the
     * user must re-enter the key.
     */
    fun action(cause: String): KeyAction = when (cause) {
        "key_missing", "decrypt_failed", "stored_value_malformed" -> KeyAction.REENTER_KEY
        "keystore_unavailable", "storage_unreadable" -> KeyAction.TRANSIENT_RETRY
        else -> KeyAction.REENTER_KEY
    }

    /**
     * The on-screen line for [state]. A ready key shows "Ready - fp" and its [fingerprint] (plain "Ready" when there is
     * none); no character of the key itself is ever shown. The result is for the screen only, so never log or report it.
     */
    fun label(state: KeyState, fingerprint: String? = null): String = when (state) {
        is KeyState.NotConfigured -> "Not configured"
        is KeyState.Ready -> if (fingerprint == null) "Ready" else "Ready - fp $fingerprint"
        is KeyState.KeyMissing -> "Key missing - re-enter key"
        is KeyState.Unreadable -> unreadableLabel(state.cause)
        else -> "Unknown key state - re-enter key"
    }

    private fun unreadableLabel(cause: String): String = when (action(cause)) {
        KeyAction.REENTER_KEY -> "Key unreadable ($cause) - re-enter key"
        KeyAction.TRANSIENT_RETRY -> "Key unreadable ($cause) - transient, retry"
    }
}
