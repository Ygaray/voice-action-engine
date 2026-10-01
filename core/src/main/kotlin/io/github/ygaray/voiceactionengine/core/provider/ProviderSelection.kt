package io.github.ygaray.voiceactionengine.core.provider

import io.github.ygaray.voiceactionengine.core.ProviderId
import io.github.ygaray.voiceactionengine.core.StrategyId
import java.util.Objects

/**
 * Where the engine learns which provider and model a tier should use. The app implements it over its own settings;
 * the library never reads settings itself and never names a model.
 *
 * The engine asks once per command per tier, lazily, on the tier's first model use, and keeps the answer for the rest
 * of that command, so a settings change in the middle of a command does not apply. Returning null means the app has
 * nothing configured for that tier, which the engine reports as not configured.
 */
public fun interface ProviderSelectionSource {
    /** Returns the provider and model for the tier named by [request], or null when none is configured. */
    public suspend fun select(request: SelectionRequest): ProviderSelection?
}

/**
 * What the engine tells a [ProviderSelectionSource] about the question being asked. Later versions add facts as new
 * properties.
 *
 * @property strategy the tier asking, so an app can pick a cheap model for one tier and a strong one for another.
 */
public class SelectionRequest internal constructor(public val strategy: StrategyId) {
    override fun toString(): String = "SelectionRequest(strategy=$strategy)"
}

/**
 * The provider and model an app chose for a tier. The model is required: the engine never invents a default model, so
 * model ids stay the app's to name and to change.
 *
 * A selection may declare one [fallback], and only when its own provider is [ProviderId.ON_DEVICE]. The fallback is
 * used only when the on-device gate does not report the model available. It is itself subject to the command's policy
 * (offline-only, allowed providers) and to the tier's declared providers, so a tier that wants on-device-then-cloud
 * declares both providers. The fallback must not be on-device and cannot declare a fallback of its own: one level, no
 * chains. Any other combination is refused with [IllegalArgumentException], so a misplaced fallback is loud rather than
 * silently ignored.
 *
 * @property provider the provider to call.
 * @property model the provider's own id for the model; must not be blank.
 * @property fallback the cloud selection to use when an on-device selection is not available, or null.
 */
public class ProviderSelection(
    public val provider: ProviderId,
    public val model: String,
    public val fallback: ProviderSelection?,
) {
    init {
        require(model.isNotBlank()) { "ProviderSelection model must not be blank" }
        if (fallback != null) {
            require(provider == ProviderId.ON_DEVICE) { "only an on-device selection may declare a fallback" }
            require(fallback.provider != ProviderId.ON_DEVICE) { "a fallback must not itself be on-device" }
        }
    }

    /** A selection with no fallback. */
    public constructor(provider: ProviderId, model: String) : this(provider, model, null)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ProviderSelection) return false
        return provider == other.provider && model == other.model && fallback == other.fallback
    }

    override fun hashCode(): Int = Objects.hash(provider, model, fallback)

    /** Prints provider and model ids and the fallback's provider and model; no key is ever involved. */
    override fun toString(): String {
        val next = if (fallback == null) "none" else "${fallback.provider}/${fallback.model}"
        return "ProviderSelection(provider=$provider, model=$model, fallback=$next)"
    }
}
