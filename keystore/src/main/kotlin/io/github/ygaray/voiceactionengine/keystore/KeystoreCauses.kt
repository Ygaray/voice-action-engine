package io.github.ygaray.voiceactionengine.keystore

import io.github.ygaray.voiceactionengine.core.provider.CredentialLookup

/**
 * Maps the public cause codes to the internal state values. The only place the code strings are spelled is the public
 * [KeystoreCauseCodes]; this object reads them for every [KeyState.Unreadable], the missing-key lookup and the
 * vocabulary, and adds no string of its own.
 */
internal object KeystoreCauses {
    val keystoreUnavailable: KeyState.Unreadable = KeyState.Unreadable(KeystoreCauseCodes.KEYSTORE_UNAVAILABLE)
    val decryptFailed: KeyState.Unreadable = KeyState.Unreadable(KeystoreCauseCodes.DECRYPT_FAILED)
    val storedValueMalformed: KeyState.Unreadable = KeyState.Unreadable(KeystoreCauseCodes.STORED_VALUE_MALFORMED)
    val storageUnreadable: KeyState.Unreadable = KeyState.Unreadable(KeystoreCauseCodes.STORAGE_UNREADABLE)

    /** What a credential lookup reports when the device key is gone. */
    val keyMissingLookup: CredentialLookup.Unreadable = CredentialLookup.Unreadable(KeystoreCauseCodes.KEY_MISSING)

    /** Every code this library can report. */
    val vocabulary: Set<String> = setOf(
        KeystoreCauseCodes.KEY_MISSING,
        KeystoreCauseCodes.KEYSTORE_UNAVAILABLE,
        KeystoreCauseCodes.DECRYPT_FAILED,
        KeystoreCauseCodes.STORED_VALUE_MALFORMED,
        KeystoreCauseCodes.STORAGE_UNREADABLE,
    )
}
