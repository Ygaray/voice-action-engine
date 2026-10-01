package io.github.ygaray.voiceactionengine.core.strategy

import io.github.ygaray.voiceactionengine.core.ProviderId

/**
 * Which model providers a tier may use, declared up front so the pipeline can apply the app's policy before any tier
 * runs. A declaration is static: it describes what the tier could use, not what it will use on a given command.
 *
 * @property providers the providers the tier may call. An empty set means the tier uses no provider at all.
 */
public class StrategyCapabilities(providers: Set<ProviderId>) {
    public val providers: Set<ProviderId> = providers.toSet()

    internal val onDeviceOnly: Boolean
        get() = providers.size == 1 && ProviderId.ON_DEVICE in providers

    override fun equals(other: Any?): Boolean = other is StrategyCapabilities && providers == other.providers

    override fun hashCode(): Int = providers.hashCode()

    override fun toString(): String = "StrategyCapabilities(providers=$providers)"

    /** The two declarations most tiers need. */
    public companion object {
        /** Any of the four known providers; what a tier that declares nothing is assumed to use. */
        public val ANY_PROVIDER: StrategyCapabilities = StrategyCapabilities(
            setOf(ProviderId.ANTHROPIC, ProviderId.OPENAI, ProviderId.OPENROUTER, ProviderId.ON_DEVICE),
        )

        /** No provider at all, for example a local grammar. Such a tier can run offline. */
        public val NO_PROVIDER: StrategyCapabilities = StrategyCapabilities(emptySet())
    }
}
