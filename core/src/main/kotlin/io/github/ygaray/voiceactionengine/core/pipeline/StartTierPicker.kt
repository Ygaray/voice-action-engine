package io.github.ygaray.voiceactionengine.core.pipeline

import io.github.ygaray.voiceactionengine.core.CommandInput
import io.github.ygaray.voiceactionengine.core.StrategyId
import io.github.ygaray.voiceactionengine.core.provider.BoundModel
import io.github.ygaray.voiceactionengine.core.telemetry.TurnRecord

/**
 * Chooses which model tier a command starts at. The engine first runs the tiers that need no model, in ladder order;
 * when none of them handles the command it asks the picker.
 *
 * The picker sees the command and the eligible tiers that call a model, in ladder order, and never a tier that makes
 * no model call. It returns one of those ids, or null for "start at the first one".
 *
 * An id that is not in the list, a null, a throw or a timeout starts the walk at the first model tier and records
 * `router_fallback` in the trace. That is never a failure of the command.
 *
 * This interface will never gain a second abstract method.
 */
public fun interface StartTierPicker {
    /**
     * Picks the tier to start at from [eligible], or null to start at the first one. [ctx] is the picker's own run
     * context: use it to make a model call that counts toward the run's token budget.
     */
    public suspend fun pick(input: CommandInput, eligible: List<StrategyId>, ctx: PickContext): StrategyId?
}

/**
 * What the engine hands a [StartTierPicker] for one pick: the run's identity, the limits in force, and the model.
 *
 * There is no write path: a picker can never write or propose a change.
 *
 * [model] asks the app's provider selection for the picker's own id, binds once and returns a refused handle when
 * nothing is mapped. Turns made through it are recorded by the engine, and they count toward [tokensUsed] and the
 * run's trace. A context is valid only while the pick runs.
 */
public abstract class PickContext internal constructor() {
    /** The id of this command's run. */
    public abstract val runId: String

    /** The limits this command runs under. */
    public abstract val policy: TierPolicy

    /** The tokens used so far in this run, picker turns included. */
    public abstract val tokensUsed: Long

    /**
     * The model the picker uses. The engine asks the app's provider selection once, the first time this is called, and
     * every later call returns the same handle. The handle never exposes the API key; when no model could be bound it
     * is refused, and every `complete` answers a failure with no provider call.
     */
    public abstract suspend fun model(): BoundModel

    /**
     * Reports one model round trip made outside [model]. The engine attaches it to the pick's trace record and adds
     * its tokens to [tokensUsed]. The record must carry ids, codes, counts and tool names only.
     */
    public abstract suspend fun recordTurn(turn: TurnRecord)

    /** Prints the run id only. */
    final override fun toString(): String = "PickContext(runId=$runId)"
}
