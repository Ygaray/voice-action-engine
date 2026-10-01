package io.github.ygaray.voiceactionengine.keystore

import javax.crypto.SecretKey

/**
 * How the store reaches the device keys. Production uses the platform key store; tests use software keys, and both feed
 * the same cipher code.
 */
internal interface KeyAccess {
    /**
     * The key stored under [alias], or null when none exists. Null is the only "absent" signal and this call never
     * creates a key. Any other lookup problem throws, so a transient failure can never be mistaken for a missing key.
     */
    fun existingKey(alias: String): SecretKey?

    /**
     * The key stored under [alias], created first when none exists. This is the only creator; it returns the existing
     * key when there is one and is atomic among the users of this library in the process (not against outside code or
     * other processes).
     */
    fun getOrCreateKey(alias: String): SecretKey
}
