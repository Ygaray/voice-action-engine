package io.github.ygaray.voiceactionengine.keystore

import io.github.ygaray.voiceactionengine.core.ProviderId

/**
 * One row of the app's key table: where the key for [provider] lives.
 *
 * Every name is app data copied verbatim from the app's existing storage, so keys saved before the app adopted this
 * library keep working. The engine never derives a name by formula. The names are not secret, so they appear in
 * [toString].
 *
 * @property provider the provider this key belongs to.
 * @property alias the AndroidKeyStore alias of the device key that protects it.
 * @property ciphertextKey the preference name that holds the encrypted key.
 * @property ivKey the preference name that holds the initialisation vector of the encryption.
 */
public class KeySlot(
    public val provider: ProviderId,
    public val alias: String,
    public val ciphertextKey: String,
    public val ivKey: String,
) {
    init {
        require(alias.isNotBlank()) { "KeySlot alias must not be blank (provider $provider)" }
        require(ciphertextKey.isNotBlank()) { "KeySlot ciphertextKey must not be blank (provider $provider)" }
        require(ivKey.isNotBlank()) { "KeySlot ivKey must not be blank (provider $provider)" }
        require(ciphertextKey != ivKey) { "KeySlot ciphertextKey and ivKey must differ, both are $ciphertextKey" }
    }

    override fun equals(other: Any?): Boolean =
        other is KeySlot &&
            provider == other.provider &&
            alias == other.alias &&
            ciphertextKey == other.ciphertextKey &&
            ivKey == other.ivKey

    override fun hashCode(): Int {
        var result = provider.hashCode()
        result = HASH_PRIME * result + alias.hashCode()
        result = HASH_PRIME * result + ciphertextKey.hashCode()
        result = HASH_PRIME * result + ivKey.hashCode()
        return result
    }

    override fun toString(): String =
        "KeySlot(provider=$provider, alias=$alias, ciphertextKey=$ciphertextKey, ivKey=$ivKey)"
}

private const val HASH_PRIME = 31
