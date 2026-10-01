package io.github.ygaray.voiceactionengine.keystore

import io.github.ygaray.voiceactionengine.core.provider.CredentialLookup

/**
 * The only place the cause codes of an unreadable key are spelled. Each is a stable lower snake case string that apps
 * map to their own wording, so the strings are frozen once the library is tagged.
 *
 * - `key_missing`: the device key that protected the stored value is gone, for example after a backup restore.
 * - `keystore_unavailable`: the device key store could not be queried right now; the stored value may be fine.
 * - `decrypt_failed`: the stored value did not decrypt, because it was altered or sealed under another key.
 * - `stored_value_malformed`: the stored value is not in the shape this library writes.
 * - `storage_unreadable`: the app's preferences could not be read at all.
 */
internal object KeystoreCauses {
    private const val KEY_MISSING = "key_missing"
    private const val KEYSTORE_UNAVAILABLE = "keystore_unavailable"
    private const val DECRYPT_FAILED = "decrypt_failed"
    private const val STORED_VALUE_MALFORMED = "stored_value_malformed"
    private const val STORAGE_UNREADABLE = "storage_unreadable"

    val keystoreUnavailable: KeyState.Unreadable = KeyState.Unreadable(KEYSTORE_UNAVAILABLE)
    val decryptFailed: KeyState.Unreadable = KeyState.Unreadable(DECRYPT_FAILED)
    val storedValueMalformed: KeyState.Unreadable = KeyState.Unreadable(STORED_VALUE_MALFORMED)
    val storageUnreadable: KeyState.Unreadable = KeyState.Unreadable(STORAGE_UNREADABLE)

    /** What a credential lookup reports when the device key is gone. */
    val keyMissingLookup: CredentialLookup.Unreadable = CredentialLookup.Unreadable(KEY_MISSING)

    /** Every code this library can report. */
    val vocabulary: Set<String> = setOf(
        KEY_MISSING,
        KEYSTORE_UNAVAILABLE,
        DECRYPT_FAILED,
        STORED_VALUE_MALFORMED,
        STORAGE_UNREADABLE,
    )
}
