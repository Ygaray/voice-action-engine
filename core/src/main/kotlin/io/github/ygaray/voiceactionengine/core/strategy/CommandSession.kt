package io.github.ygaray.voiceactionengine.core.strategy

import io.github.ygaray.voiceactionengine.core.StrategyId
import io.github.ygaray.voiceactionengine.core.commit.DispatchResult
import io.github.ygaray.voiceactionengine.core.commit.ToolStep
import io.github.ygaray.voiceactionengine.core.pipeline.TierPolicy

/**
 * What the engine hands a strategy for one run: the run's identity, the limits in force, whatever the previous tier
 * passed along, and the only way to write.
 */
public abstract class CommandSession internal constructor() {
    /** The id of this command's run. */
    public abstract val runId: String

    /** The id of the earlier run this command answers, or null. */
    public abstract val parentRunId: String?

    /** The tier this session belongs to. */
    public abstract val strategy: StrategyId

    /** The limits this command runs under. */
    public abstract val policy: TierPolicy

    /** The opaque object the previous tier escalated with, or null for the first tier. */
    public abstract val carry: Any?

    /**
     * Sends [step] down the one write path: the gate decides, an admitted mutation is applied, and the commit sink
     * hears about it before this call returns. The result is what to tell the model.
     */
    public abstract suspend fun submit(step: ToolStep): DispatchResult

    /** Prints ids and the carry's class name only. */
    final override fun toString(): String =
        "CommandSession(runId=$runId, strategy=$strategy, carry=${carry?.let { it::class.simpleName }})"
}
