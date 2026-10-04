package io.github.ygaray.voiceactionengine.keystore

/**
 * The stable cause codes this library reports for a stored key it cannot read, each with the user experience it calls
 * for: re-enter the key, or treat it as transient and retry. A `CredentialLookup.Unreadable` can carry any of the five.
 * A [KeyState.Unreadable] carries only the other four: a gone device key ([KEY_MISSING]) is reported by the store as
 * the separate [KeyState.KeyMissing] state, and only the credential source turns it into
 * `CredentialLookup.Unreadable(KEY_MISSING)`.
 *
 * These are values read from the library at run time, never inlined into the app, and they work as `when` branch
 * conditions (`when (cause) { KeystoreCauseCodes.KEY_MISSING -> ... }`). The set is open: later versions may add
 * codes, so treat an unknown code as "re-enter the key". The strings are lower snake case and frozen once tagged.
 */
public object KeystoreCauseCodes {
    /** Re-enter the key: the device key that protected the stored value is gone, for example after a backup restore. */
    public val KEY_MISSING: String get() = "key_missing"

    /** Re-enter the key: the stored value did not decrypt, because it was altered or sealed under another key. */
    public val DECRYPT_FAILED: String get() = "decrypt_failed"

    /** Re-enter the key: the stored value is not in the shape this library writes. */
    public val STORED_VALUE_MALFORMED: String get() = "stored_value_malformed"

    /** Transient, retry: the device key store could not be queried right now; the stored value may be fine. */
    public val KEYSTORE_UNAVAILABLE: String get() = "keystore_unavailable"

    /** Transient, retry: the app's preferences could not be read at all. */
    public val STORAGE_UNREADABLE: String get() = "storage_unreadable"
}
