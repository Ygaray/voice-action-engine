package io.github.ygaray.voiceactionengine.core.provider

import io.github.ygaray.voiceactionengine.core.ProviderId

/**
 * A model provider: it turns one neutral [ProviderRequest] into a [ModelResult].
 *
 * Implement it by mapping the neutral request to the provider's wire format and the wire answer back to the neutral
 * response. Rules for every implementation:
 * - Return [ModelResult.Failure] for every expected failure, with a status, an error type and a request id only,
 *   never a response body.
 * - Never log, and never put a key, prompt or message text into a failure.
 * - Keep no state between calls; the engine may call from several coroutines.
 *
 * The engine still guards every call, so a provider that throws is collapsed to a failure rather than crashing the
 * run. Later versions add members to this interface only with default implementations.
 */
public interface AiProvider {
    /** The provider's id, which the engine matches against credentials and the tier ladder. */
    public val id: ProviderId

    /**
     * Whether this provider needs an API key. A provider that runs on the device returns false and receives a null
     * credential.
     */
    public val requiresCredential: Boolean
        get() = true

    /**
     * What this provider says about [model], including its default for a model id it does not know. The app's
     * overrides are applied on top before the call, and the result arrives as [ProviderRequest.capabilities].
     */
    public fun capabilities(model: String): ModelCapabilities = ModelCapabilities.UNKNOWN

    /** Sends [call] and returns the answer or the failure. */
    public suspend fun complete(call: ProviderRequest): ModelResult
}
