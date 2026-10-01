package io.github.ygaray.voiceactionengine.core.provider

import io.github.ygaray.voiceactionengine.core.Credential
import io.github.ygaray.voiceactionengine.core.transcript.ModelRequest

/**
 * Everything the engine hands a provider for one round trip.
 *
 * @property model the model id the router bound for this call; never blank.
 * @property request the neutral request to send.
 * @property credential the credential for this provider only, or null for a provider whose
 * [AiProvider.requiresCredential] is false. It is never printed by [toString].
 * @property capabilities what the model can do after the app's overrides, so the transport can follow the cache and
 * tool facts the engine decided on.
 * @throws IllegalArgumentException when [model] is blank.
 */
public class ProviderRequest(
    public val model: String,
    public val request: ModelRequest,
    public val credential: Credential?,
    public val capabilities: ModelCapabilities,
) {
    init {
        require(model.isNotBlank()) { "a provider call needs a model id" }
    }

    /** Prints the model, the message and tool counts and the credential's provider only, never any content or key. */
    override fun toString(): String =
        "ProviderRequest(model=$model, messages=${request.messages.size}, tools=${request.tools.size}, " +
            "credential=${credential?.provider ?: "none"})"
}
