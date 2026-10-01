package io.github.ygaray.voiceactionengine.core.strategy

import io.github.ygaray.voiceactionengine.core.CommandInput
import io.github.ygaray.voiceactionengine.core.commit.FinishedKind
import io.github.ygaray.voiceactionengine.core.commit.ToolStep

/**
 * The app's step for one tool call the model made during a multi-turn run.
 *
 * The executor validates the arguments itself, because the engine passes them through untouched. It must never write:
 * every write goes through the gate after the executor returns, as a [ToolStep.Mutation]. A read tool returns a
 * [ToolStep.Finished] of kind [FinishedKind.READ]. A mutating tool may also return a [ToolStep.Finished] of kind
 * [FinishedKind.PREVIEW] or [FinishedKind.ERROR] when it has nothing to apply, for example when its arguments are
 * invalid.
 *
 * A [ToolStep.Mutation] returned for a tool whose [ToolSpec.mutating] is false is rejected by the engine before the
 * gate, and the model is told the call failed. When the executor throws, the model gets an error result with a fixed
 * notice, never the exception text. A cancellation always propagates.
 *
 * The engine calls the executor one call at a time, in the order the model emitted the calls, and never for a tool name
 * the tier did not offer.
 */
public fun interface ToolExecutor {
    /** Prepares [call], one tool call the model made, for the command described by [input]. */
    public suspend fun prepare(call: Extraction, input: CommandInput): ToolStep
}
