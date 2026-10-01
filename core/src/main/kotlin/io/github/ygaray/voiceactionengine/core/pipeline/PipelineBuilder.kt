package io.github.ygaray.voiceactionengine.core.pipeline

import io.github.ygaray.voiceactionengine.core.ProviderId
import io.github.ygaray.voiceactionengine.core.commit.CommitSink
import io.github.ygaray.voiceactionengine.core.commit.PreApplyGate
import io.github.ygaray.voiceactionengine.core.provider.AiProvider
import io.github.ygaray.voiceactionengine.core.provider.CredentialSource
import io.github.ygaray.voiceactionengine.core.provider.ModelCapabilities
import io.github.ygaray.voiceactionengine.core.provider.ModelCapabilityTable
import io.github.ygaray.voiceactionengine.core.provider.ModelRouter
import io.github.ygaray.voiceactionengine.core.provider.OnDeviceAvailability
import io.github.ygaray.voiceactionengine.core.provider.OnDeviceCapability
import io.github.ygaray.voiceactionengine.core.provider.ProviderSelectionSource
import io.github.ygaray.voiceactionengine.core.strategy.CommandStrategy
import io.github.ygaray.voiceactionengine.core.telemetry.PipelineEventListener
import java.util.UUID

private const val NANOS_PER_MILLI = 1_000_000L
private const val NOT_IMPLEMENTED = "not_implemented"

/** Marks the pipeline builder so a nested block cannot reach the outer builder's members by accident. */
@DslMarker
public annotation class PipelineDsl

/**
 * Collects an app's tiers, gate, sink and limits. Use it through [commandPipeline].
 *
 * There is no default gate and no default sink: an app must choose how changes are approved and where they are
 * reported, so nothing is ever committed by accident.
 */
@PipelineDsl
public class PipelineBuilder internal constructor() {
    private val strategies = mutableListOf<CommandStrategy>()
    private val providers = mutableListOf<AiProvider>()
    private val overrides = mutableListOf<CapabilityOverride>()

    /** The approval step in front of every change. Required. */
    public var gate: PreApplyGate? = null

    /** Where actions and run endings are reported. Required. */
    public var commitSink: CommitSink? = null

    /** Which tier a command starts at; the default [TierSelector.Linear] starts at the first tier that may run. */
    public var selector: TierSelector = TierSelector.Linear

    /** The limits each command runs under; the default is [TierPolicy.DEFAULT] for every command. */
    public var policy: TierPolicySource = TierPolicySource.fixed(TierPolicy.DEFAULT)

    /**
     * Receives events as each command runs, or null for none (the default). It cannot suspend; if it throws, the
     * command carries on and the trace records `listener_error`.
     */
    public var listener: PipelineEventListener? = null

    /**
     * Where the engine learns which provider and model each tier uses, asked once per command per tier. Required for
     * any tier that calls a model; without it the tier's model is refused as not configured.
     */
    public var providerSelection: ProviderSelectionSource? = null

    /**
     * Where the engine gets the API key for the provider it is about to call. Required for any provider that needs a
     * key; without it that provider is refused as not configured. A strategy never sees the key.
     */
    public var credentials: CredentialSource? = null

    /** Milliseconds on a monotonic clock, used for the trace; replace it in tests. */
    public var clock: () -> Long = { System.nanoTime() / NANOS_PER_MILLI }

    /** Makes the id of each run; replace it in tests. */
    public var runIds: () -> String = { UUID.randomUUID().toString() }

    /**
     * Tells the engine whether on-device inference can run right now. Version 1.0 has no on-device implementation, so
     * the default reports [OnDeviceAvailability.Unavailable] with a "not implemented" code. Only
     * [OnDeviceAvailability.Available] is usable; every other status counts as unavailable.
     *
     * A tier whose only provider is on-device fails loudly when it is unavailable and never climbs to the cloud. To
     * fall back instead, declare both providers on the tier and a fallback on the app's selection; the fallback is
     * still subject to the command's policy, so an offline-only command never reaches it.
     */
    public var onDevice: OnDeviceCapability = OnDeviceCapability { OnDeviceAvailability.Unavailable(NOT_IMPLEMENTED) }

    /** Whether [onDevice] reports available, read at call time; the pre-check and the router share this one hook. */
    internal var onDeviceAvailability: suspend () -> Boolean = {
        onDevice.availability() is OnDeviceAvailability.Available
    }

    /** Adds [strategy] above the tiers already added. The first tier added is tried first. */
    public fun tier(strategy: CommandStrategy) {
        strategies.add(strategy)
    }

    /** Registers [provider] so a tier can be routed to it. Each provider id may be registered once. */
    public fun provider(provider: AiProvider) {
        providers.add(provider)
    }

    /**
     * Patches what the engine believes about one model: the fields [block] sets replace the provider's default for that
     * exact [provider] and [model] pair, and every other field keeps the provider's value. Keys are exact ids, never
     * prefixes or families. Declaring the same pair twice, a blank [model], or a block that sets an invalid value makes
     * the pipeline fail to build.
     */
    public fun capabilities(provider: ProviderId, model: String, block: ModelCapabilities.Builder.() -> Unit) {
        overrides.add(CapabilityOverride(provider, model, block))
    }

    internal fun build(): CommandPipeline {
        require(strategies.isNotEmpty()) { "commandPipeline: at least one tier is required" }
        val duplicate = strategies.map { it.id }.groupingBy { it }.eachCount().entries.firstOrNull { it.value > 1 }
        require(duplicate == null) { "commandPipeline: duplicate tier id ${duplicate?.key}" }
        val fixed = (selector as? TierSelector.Fixed)?.tier
        require(fixed == null || strategies.any { it.id == fixed }) {
            "commandPipeline: selector names unknown tier $fixed"
        }
        val finalGate = requireNotNull(gate) { "commandPipeline: gate is required (no auto-commit default)" }
        val finalSink = requireNotNull(commitSink) { "commandPipeline: commitSink is required" }
        val registered = providers.toList()
        val dupProvider = registered.map { it.id }.groupingBy { it }.eachCount().entries.firstOrNull { it.value > 1 }
        require(dupProvider == null) { "commandPipeline: duplicate provider id ${dupProvider?.key}" }
        val patches = validatedOverrides()
        return CommandPipeline(
            wiring(registered, patches),
            finalGate,
            finalSink,
            policy,
            clock,
            runIds,
            listener,
        )
    }

    /**
     * The override blocks by exact pair. Each is run once against the unknown-model defaults, so a duplicate pair, a
     * blank model or an out-of-range value fails here and never at the first command.
     */
    private fun validatedOverrides(): Map<Pair<ProviderId, String>, ModelCapabilities.Builder.() -> Unit> {
        val patches = LinkedHashMap<Pair<ProviderId, String>, ModelCapabilities.Builder.() -> Unit>()
        for (patch in overrides) {
            require(patch.model.isNotBlank()) { "commandPipeline: capability override model must not be blank" }
            val key = patch.provider to patch.model
            require(key !in patches) {
                "commandPipeline: duplicate capability override for ${patch.provider} / ${patch.model}"
            }
            ModelCapabilities.UNKNOWN.toBuilder().apply(patch.block).build()
            patches[key] = patch.block
        }
        return patches
    }

    /** One on-device probe instance goes to both the pre-check and the router, so the two never disagree. */
    private fun wiring(
        registered: List<AiProvider>,
        patches: Map<Pair<ProviderId, String>, ModelCapabilities.Builder.() -> Unit>,
    ): PipelineWiring {
        val probe = onDeviceAvailability
        val byId = registered.associateBy { it.id }
        val table = ModelCapabilityTable(
            { provider, model -> byId[provider]?.capabilities(model) ?: ModelCapabilities.UNKNOWN },
            patches,
        )
        val router = ModelRouter(byId, providerSelection, credentials, table, probe)
        return PipelineWiring(PolicyPreCheck(strategies.toList(), selector, probe), router, table)
    }
}

/** One app capability override as declared, before validation. */
private class CapabilityOverride(
    val provider: ProviderId,
    val model: String,
    val block: ModelCapabilities.Builder.() -> Unit,
)

/**
 * Composes a [CommandPipeline]. Throws [IllegalArgumentException] if the pipeline is misconfigured. `execute` throws
 * only for the caller's own cancellation or a JVM `Error`; see [CommandPipeline.execute].
 */
public fun commandPipeline(block: PipelineBuilder.() -> Unit): CommandPipeline =
    PipelineBuilder().apply(block).build()
