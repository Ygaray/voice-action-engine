package io.github.ygaray.voiceactionengine.core.strategy

import io.github.ygaray.voiceactionengine.core.StrategyId
import io.github.ygaray.voiceactionengine.core.commit.DispatchResult
import io.github.ygaray.voiceactionengine.core.commit.ToolStep
import io.github.ygaray.voiceactionengine.core.pipeline.TierPolicy
import io.github.ygaray.voiceactionengine.core.provider.BoundModel
import io.github.ygaray.voiceactionengine.core.telemetry.TraceCode
import io.github.ygaray.voiceactionengine.core.telemetry.TurnRecord

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

    /**
     * Like [submit], and the actions the step records carry [providerCallId], the provider's id for the tool call that
     * produced the step. Used by the engine's own strategies; the public [submit] records null.
     */
    internal abstract suspend fun submit(step: ToolStep, providerCallId: String?): DispatchResult

    /**
     * The tokens used so far in this run: the total of every turn's usage reported with [recordTurn], across tiers.
     * A strategy that loops compares it with `policy.tokenCeiling`; it also enforces `policy.maxIterations` and
     * `policy.maxTokensPerTurn` itself.
     */
    public abstract val tokensUsed: Long

    /**
     * Reports one model round trip. The engine attaches it to this tier's trace attempt, adds its tokens to
     * [tokensUsed] and tells the event listener. The record must carry ids, codes, counts and tool names only.
     *
     * Turns made through [model] are recorded by the engine, so call this only for model calls made outside that
     * handle.
     */
    public abstract suspend fun recordTurn(turn: TurnRecord)

    /** Records an engine code in this run's trace and tells the event listener. Used by the engine's own strategies. */
    internal abstract suspend fun recordCode(code: TraceCode)

    /**
     * The model this tier uses for this command. The engine asks the app's provider selection once, the first time
     * this is called, and every later call (from any coroutine of this tier) returns the same handle, so the provider
     * and model never change in the middle of a command; a change of selection applies from the next command.
     *
     * The handle never exposes the API key. When no model could be bound (nothing selected, a key missing, the provider
     * not allowed, on-device inference not available) the returned handle is refused: its `refusal` says why and every
     * `complete` answers a failure with no provider call, so turn it into a failed outcome.
     */
    public abstract suspend fun model(): BoundModel

    /** Prints ids and the carry's class name only. */
    final override fun toString(): String =
        "CommandSession(runId=$runId, strategy=$strategy, carry=${carry?.let { it::class.simpleName }})"
}
