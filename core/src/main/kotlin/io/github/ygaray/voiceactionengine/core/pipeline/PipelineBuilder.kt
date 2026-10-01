package io.github.ygaray.voiceactionengine.core.pipeline

import io.github.ygaray.voiceactionengine.core.commit.CommitSink
import io.github.ygaray.voiceactionengine.core.commit.PreApplyGate
import io.github.ygaray.voiceactionengine.core.provider.AiProvider
import io.github.ygaray.voiceactionengine.core.provider.CredentialSource
import io.github.ygaray.voiceactionengine.core.provider.ModelCapabilities
import io.github.ygaray.voiceactionengine.core.provider.ModelCapabilityTable
import io.github.ygaray.voiceactionengine.core.provider.ModelRouter
import io.github.ygaray.voiceactionengine.core.provider.ProviderSelectionSource
import io.github.ygaray.voiceactionengine.core.strategy.CommandStrategy
import io.github.ygaray.voiceactionengine.core.telemetry.PipelineEventListener
import java.util.UUID

private const val NANOS_PER_MILLI = 1_000_000L

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
     * Whether on-device inference can run right now. Defaults to unavailable; the on-device tier plugs in here, so a
     * ladder with an on-device-only tier fails loudly instead of climbing silently to the cloud.
     */
    internal var onDeviceAvailability: suspend () -> Boolean = { false }

    /** Adds [strategy] above the tiers already added. The first tier added is tried first. */
    public fun tier(strategy: CommandStrategy) {
        strategies.add(strategy)
    }

    /** Registers [provider] so a tier can be routed to it. Each provider id may be registered once. */
    public fun provider(provider: AiProvider) {
        providers.add(provider)
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
        return CommandPipeline(
            wiring(registered),
            finalGate,
            finalSink,
            policy,
            clock,
            runIds,
            listener,
        )
    }

    /** One on-device probe instance goes to both the pre-check and the router, so the two never disagree. */
    private fun wiring(registered: List<AiProvider>): PipelineWiring {
        val probe = onDeviceAvailability
        val byId = registered.associateBy { it.id }
        val table = ModelCapabilityTable(
            { provider, model -> byId[provider]?.capabilities(model) ?: ModelCapabilities.UNKNOWN },
            emptyMap(),
        )
        val router = ModelRouter(byId, providerSelection, credentials, table, clock, probe)
        return PipelineWiring(PolicyPreCheck(strategies.toList(), selector, probe), router)
    }
}

/**
 * Composes a [CommandPipeline]. Throws [IllegalArgumentException] if the pipeline is misconfigured. `execute` throws
 * only for the caller's own cancellation or a JVM `Error`; see [CommandPipeline.execute].
 */
public fun commandPipeline(block: PipelineBuilder.() -> Unit): CommandPipeline =
    PipelineBuilder().apply(block).build()
