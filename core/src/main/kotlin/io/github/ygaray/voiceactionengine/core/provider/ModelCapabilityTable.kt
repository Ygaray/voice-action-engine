package io.github.ygaray.voiceactionengine.core.provider

import io.github.ygaray.voiceactionengine.core.ProviderId

/**
 * Answers what a model can do. Public so a model picker can mark or filter models that cannot take tools.
 *
 * Precedence, highest first: an app override declared for the exact (provider, model id) pair, then the provider's
 * own default for that id, then the provider's default for ids it does not know. An override patches only the fields
 * its block sets; every other field keeps the provider default's value. Keys are exact ids, never prefixes or model
 * families, so an override for one id never reaches a similarly named one.
 */
public class ModelCapabilityTable internal constructor(
    private val providerDefaults: (ProviderId, String) -> ModelCapabilities,
    private val overrides: Map<Pair<ProviderId, String>, ModelCapabilities.Builder.() -> Unit>,
) {
    /** The capabilities of [model] under [provider]; throws [IllegalArgumentException] when [model] is blank. */
    public fun lookup(provider: ProviderId, model: String): ModelCapabilities {
        require(model.isNotBlank()) { "model must not be blank" }
        val default = providerDefaults(provider, model)
        val patch = overrides[provider to model] ?: return default
        return default.toBuilder().apply(patch).build()
    }

    override fun toString(): String = "ModelCapabilityTable(overrides=${overrides.size})"
}
