package io.github.ygaray.voiceactionengine.core.commit

import io.github.ygaray.voiceactionengine.core.internal.guarded
import io.github.ygaray.voiceactionengine.core.telemetry.RunRecorder
import io.github.ygaray.voiceactionengine.core.telemetry.TraceCode

/**
 * Asks the app's gate about a proposal and fails closed.
 *
 * If the gate throws, nothing is applied: the answer is a hold with no reason and no token, and the engine says why
 * only through a trace code. The engine never invents a reason object; that belongs to the app. Cancellation of the
 * caller is not a fault and propagates before anything is recorded.
 */
internal class GateStep(
    private val gate: PreApplyGate,
    private val recorder: RunRecorder,
) {
    /** The gate's decision on [proposal], or a bare hold when the gate failed. */
    suspend fun decide(proposal: CommitProposal): GateDecision = guarded(onFault = {
        recorder.recordCode(TraceCode.GATE_ERROR)
        GateDecision.Hold()
    }) { gate.admit(proposal) }
}
