package io.github.ygaray.voiceactionengine.core.pipeline

import io.github.ygaray.voiceactionengine.core.commit.CommitSink
import io.github.ygaray.voiceactionengine.core.commit.PreApplyGate
import io.github.ygaray.voiceactionengine.core.strategy.CommandStrategy
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

    /** The approval step in front of every change. Required. */
    public var gate: PreApplyGate? = null

    /** Where actions and run endings are reported. Required. */
    public var commitSink: CommitSink? = null

    /** The limits each command runs under; the default is [TierPolicy.DEFAULT] for every command. */
    public var policy: TierPolicySource = TierPolicySource.fixed(TierPolicy.DEFAULT)

    /** Milliseconds on a monotonic clock, used for the trace; replace it in tests. */
    public var clock: () -> Long = { System.nanoTime() / NANOS_PER_MILLI }

    /** Makes the id of each run; replace it in tests. */
    public var runIds: () -> String = { UUID.randomUUID().toString() }

    /** Adds [strategy] above the tiers already added. The first tier added is tried first. */
    public fun tier(strategy: CommandStrategy) {
        strategies.add(strategy)
    }

    internal fun build(): CommandPipeline {
        val finalGate = requireNotNull(gate)
        val finalSink = requireNotNull(commitSink)
        return CommandPipeline(strategies.toList(), finalGate, finalSink, policy, clock, runIds)
    }
}

/**
 * Composes a [CommandPipeline]. Throws [IllegalArgumentException] if the pipeline is misconfigured; `execute` itself
 * never throws.
 */
public fun commandPipeline(block: PipelineBuilder.() -> Unit): CommandPipeline =
    PipelineBuilder().apply(block).build()
