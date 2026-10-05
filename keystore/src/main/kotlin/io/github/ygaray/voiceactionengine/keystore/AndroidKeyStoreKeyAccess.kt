package io.github.ygaray.voiceactionengine.keystore

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.security.UnrecoverableKeyException
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * Device key custody on the platform key store.
 *
 * A lookup uses `getKey`, which answers null only when no key exists under the alias and throws for every other
 * problem. The entry-based lookup and the alias-presence query are deliberately not used: they can report "absent"
 * when the platform key store failed transiently, and a caller that creates a key on "absent" would then replace the
 * user's key and orphan everything encrypted under it. Here a failed lookup always propagates, so a read reports the
 * key store as unavailable and a save fails loudly; neither ever generates a replacement.
 *
 * Creation is serialised by one lock that belongs to this object, so it is shared by every store of this library in
 * the process: two such stores cannot both generate a key for one alias. The lock does not cover code outside the
 * library (an app's older key code serialises on its own monitor) or other processes, so an alias must have exactly one
 * writer at a time: either the older code or this library, never both concurrently.
 *
 * The key specification (AES, 256 bits, GCM, no padding, encrypt and decrypt, randomized encryption, no
 * user-authentication binding, no hardware-module request) is the one the existing adopter apps already use,
 * so keys created by either side are interchangeable. This is the only main source file that touches
 * `android.security.keystore`.
 */
@OptIn(DelicateKeyAccess::class)
internal object AndroidKeyStoreKeyAccess : KeyAccess {
    private const val PROVIDER = "AndroidKeyStore"
    private const val KEY_SIZE_BITS = 256

    private val creationLock = Any()

    override fun existingKey(alias: String): SecretKey? {
        val keyStore = KeyStore.getInstance(PROVIDER).apply { load(null) }
        return when (val key = keyStore.getKey(alias, null)) {
            null -> null
            is SecretKey -> key
            else -> throw UnrecoverableKeyException("Device key has an unexpected type")
        }
    }

    override fun getOrCreateKey(alias: String): SecretKey =
        synchronized(creationLock) { existingKey(alias) ?: generate(alias) }

    private fun generate(alias: String): SecretKey {
        val spec = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(KEY_SIZE_BITS)
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
            .apply { init(spec) }
            .generateKey()
    }
}
