package io.github.ygaray.voiceactionengine.keystore

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CancellationException
import java.security.GeneralSecurityException
import java.util.Base64
import javax.crypto.SecretKey

/**
 * What one read produced: the [state] to show, and the decrypted key only when the state is [KeyState.Ready].
 */
internal class SecretRead(val state: KeyState, val plaintext: String?) {
    /** Prints the state only: never the key. */
    override fun toString(): String = "SecretRead(state=$state)"
}

/**
 * The one decode-and-decrypt path behind every read. It only looks device keys up and never creates one, so reading a
 * pair whose device key is gone (for example after a backup restore) reports a missing key instead of silently
 * replacing it.
 */
internal class SecretReader(private val keyAccess: KeyAccess) {
    private val decoder: Base64.Decoder = Base64.getDecoder()

    /** Blocking: touches the device key store and the cipher, so callers run it off the main thread. */
    fun open(slot: KeySlot, prefs: Preferences): SecretRead {
        val stored = storedPair(slot, prefs) ?: return SecretRead(KeyState.NotConfigured(), null)
        return lookUp(slot.alias).then { key -> decode(stored).then { sealed -> decrypt(key, sealed) } }.finish(::ready)
    }

    private fun storedPair(slot: KeySlot, prefs: Preferences): StoredPair? {
        val ciphertext = prefs[stringPreferencesKey(slot.ciphertextKey)]
        val iv = prefs[stringPreferencesKey(slot.ivKey)]
        return if (ciphertext == null || iv == null) null else StoredPair(iv, ciphertext)
    }

    // Only a lookup that answers "no such key" is a missing key; a lookup that throws says nothing about the key.
    private fun lookUp(alias: String): Stage<SecretKey> = try {
        val key = keyAccess.existingKey(alias)
        if (key == null) failed(KeyState.KeyMissing()) else proceed(key)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (ignored: GeneralSecurityException) {
        failed(KeystoreCauses.keystoreUnavailable)
    } catch (ignored: RuntimeException) {
        failed(KeystoreCauses.keystoreUnavailable)
    }

    // The decoder is strict: a line break or a character outside the standard alphabet is a malformed value.
    private fun decode(stored: StoredPair): Stage<Sealed> = try {
        sizeChecked(Sealed(decoder.decode(stored.iv), decoder.decode(stored.ciphertext)))
    } catch (ignored: IllegalArgumentException) {
        failed(KeystoreCauses.storedValueMalformed)
    }

    private fun sizeChecked(sealed: Sealed): Stage<Sealed> =
        if (sealed.iv.size == IV_BYTES && sealed.ciphertext.size >= TAG_BYTES) {
            proceed(sealed)
        } else {
            failed(KeystoreCauses.storedValueMalformed)
        }

    private fun decrypt(key: SecretKey, sealed: Sealed): Stage<ByteArray> = try {
        proceed(AesGcm.open(key, sealed.iv, sealed.ciphertext))
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (ignored: GeneralSecurityException) {
        failed(KeystoreCauses.decryptFailed)
    } catch (ignored: RuntimeException) {
        failed(KeystoreCauses.decryptFailed)
    }

    private fun ready(plain: ByteArray): SecretRead {
        val text = String(plain, Charsets.UTF_8)
        return when {
            text.isBlank() -> SecretRead(KeystoreCauses.storedValueMalformed, null)
            text.length > LAST_CHARS -> SecretRead(KeyState.Ready(text.takeLast(LAST_CHARS)), text)
            else -> SecretRead(KeyState.Ready(""), text)
        }
    }
}

/** The two stored halves, still encoded. */
private class StoredPair(val iv: String, val ciphertext: String)

/** One step of a read: either a value to carry on with, or the finished read that ends it. */
private class Stage<out T : Any>(private val value: T?, private val stop: SecretRead?) {
    fun <R : Any> then(step: (T) -> Stage<R>): Stage<R> =
        if (stop == null) step(checkNotNull(value)) else Stage(null, stop)

    fun finish(done: (T) -> SecretRead): SecretRead = stop ?: done(checkNotNull(value))
}

private fun <T : Any> proceed(value: T): Stage<T> = Stage(value, null)

private fun halt(read: SecretRead): Stage<Nothing> = Stage(null, read)

private fun failed(state: KeyState): Stage<Nothing> = halt(SecretRead(state, null))

private const val LAST_CHARS = 4
private const val IV_BYTES = 12
private const val TAG_BYTES = 16
