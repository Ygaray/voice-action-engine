package io.github.ygaray.voiceactionengine.keystore

import javax.crypto.SecretKey

/**
 * How the store reaches the device keys. Production uses the platform key store; an app supplies its own only in tests,
 * through the opt-in [ApiKeyStore] constructor, and both feed the same cipher code.
 *
 * A fake must keep the contract: [existingKey] never creates a key and returns null only when the key is absent;
 * [getOrCreateKey] is the only creator. It is a plain interface because it has two members.
 */
@DelicateKeyAccess
public interface KeyAccess {
    /**
     * The key stored under [alias], or null when none exists. Null is the only "absent" signal and this call never
     * creates a key. Any other lookup problem throws, so a transient failure can never be mistaken for a missing key.
     */
    public fun existingKey(alias: String): SecretKey?

    /**
     * The key stored under [alias], created first when none exists. This is the only creator; it returns the existing
     * key when there is one and is atomic among the users of this library in the process (not against outside code or
     * other processes).
     */
    public fun getOrCreateKey(alias: String): SecretKey
}
