package io.github.ygaray.voiceactionengine.core

/**
 * An API key for one [provider]. The key never appears in [toString], logs or failures. A typed source that supplies
 * credentials to the engine is built around this class in a later module.
 *
 * @property provider the provider the key belongs to.
 * @property apiKey the secret itself; blank keys are rejected at construction.
 */
public class Credential(public val provider: ProviderId, public val apiKey: String) {
    init {
        require(apiKey.isNotBlank()) { "Credential apiKey must not be blank" }
    }

    /** Prints the provider only: never the key and not even its length. */
    override fun toString(): String = "Credential(provider=$provider)"
}
