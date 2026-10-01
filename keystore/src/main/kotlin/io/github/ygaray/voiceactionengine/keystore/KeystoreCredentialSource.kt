package io.github.ygaray.voiceactionengine.keystore

import io.github.ygaray.voiceactionengine.core.Credential
import io.github.ygaray.voiceactionengine.core.ProviderId
import io.github.ygaray.voiceactionengine.core.provider.CredentialLookup
import io.github.ygaray.voiceactionengine.core.provider.CredentialSource

/**
 * Hands the keys of an [ApiKeyStore] to the engine: pass it as the credential source of a command pipeline.
 *
 * It is the only public path by which a stored key leaves this module, and the key leaves inside the credential of a
 * present lookup, stamped with the provider that was asked for. What each answer means to the user:
 * - missing: no key was ever stored for the provider, so it is not configured;
 * - unreadable: a key is stored but cannot be read (for example the device key was lost after a backup restore), so
 *   the user should be asked to re-enter the key; the cause is one of the stable codes of the store.
 *
 * Every failure of the device key store, the stored value or the storage I/O becomes an answer. It throws only for the
 * cancellation of the calling coroutine, or for a misuse of the app's DataStore (such as a second DataStore on the same
 * file), which the engine reports as a fault of the credential source.
 */
public class KeystoreCredentialSource(private val store: ApiKeyStore) : CredentialSource {
    override suspend fun credential(provider: ProviderId): CredentialLookup =
        when (val read = store.readSecret(provider)) {
            is SecretRead.Plain -> CredentialLookup.Present(Credential(provider, read.text))
            is SecretRead.Failed -> failedLookup(read.state)
        }

    private fun failedLookup(state: KeyState): CredentialLookup = when (state) {
        is KeyState.KeyMissing -> KeystoreCauses.keyMissingLookup
        is KeyState.Unreadable -> CredentialLookup.Unreadable(state.cause)
        else -> CredentialLookup.Missing()
    }

    /** Prints the type only: never a key or a name of storage. */
    override fun toString(): String = "KeystoreCredentialSource"
}
