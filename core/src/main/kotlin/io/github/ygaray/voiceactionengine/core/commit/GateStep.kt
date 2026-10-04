package io.github.ygaray.voiceactionengine.core.commit

import io.github.ygaray.voiceactionengine.core.internal.guarded
import io.github.ygaray.voiceactionengine.core.telemetry.RunRecorder
import io.github.ygaray.voiceactionengine.core.telemetry.TraceCode

/** What asking the gate produced: the app's own decision, or a fault the engine closed over. */
internal sealed interface GateAnswer {
    /** The gate answered. A [GateDecision.Hold] here is the app's choice, even a bare one with no reason or token. */
    class Decided(val decision: GateDecision) : GateAnswer

    /** The gate threw. Nothing may be applied, and the model must hear an error, not a hold. */
    data object Faulted : GateAnswer
}

/**
 * Asks the app's gate about a proposal and fails closed.
 *
 * If the gate throws, nothing is applied: the answer is [GateAnswer.Faulted], which the coordinator records as an
 * errored action and not as a hold: there is no held proposal, and the engine says why only through the `gate_error`
 * trace code. The engine never invents a reason object; that belongs to the app. The fault is a distinct answer
 * rather than a bare [GateDecision.Hold] because a gate may legitimately return a bare hold, and only the fault is an
 * error for the model. Cancellation of the caller
 * is not a fault and propagates before anything is recorded.
 */
internal class GateStep(
    private val gate: PreApplyGate,
    private val recorder: RunRecorder,
) {
    /** The gate's decision on [proposal], or [GateAnswer.Faulted] when the gate failed. */
    suspend fun decide(proposal: CommitProposal): GateAnswer = guarded<GateAnswer>(onFault = {
        recorder.recordCode(TraceCode.GATE_ERROR)
        GateAnswer.Faulted
    }) { GateAnswer.Decided(gate.admit(proposal)) }
}
