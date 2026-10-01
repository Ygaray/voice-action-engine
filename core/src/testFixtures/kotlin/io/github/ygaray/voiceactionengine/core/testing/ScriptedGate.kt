package io.github.ygaray.voiceactionengine.core.testing

import io.github.ygaray.voiceactionengine.core.commit.CommitProposal
import io.github.ygaray.voiceactionengine.core.commit.GateDecision
import io.github.ygaray.voiceactionengine.core.commit.PreApplyGate
import java.util.concurrent.CopyOnWriteArrayList

/**
 * A gate whose answers a test scripts. It records every proposal it is asked about and logs `gate` to [log].
 *
 * @param log an optional shared log, to assert the order of gate, apply and sink events.
 * @param decide produces the decision for each proposal.
 */
public class ScriptedGate(
    private val log: RecordingSink<String>? = null,
    private val decide: suspend (CommitProposal) -> GateDecision,
) : PreApplyGate {
    private val seen = CopyOnWriteArrayList<CommitProposal>()

    /** Every proposal the gate was asked about, in order. */
    public val proposals: List<CommitProposal>
        get() = seen.toList()

    /** How many times the gate was asked. */
    public val calls: Int
        get() = seen.size

    override suspend fun admit(proposal: CommitProposal): GateDecision {
        seen.add(proposal)
        log?.record(GATE_LOG)
        return decide(proposal)
    }

    /** Ready-made gates. */
    public companion object {
        /** A gate that admits every proposal as submitted. */
        public fun admitAll(log: RecordingSink<String>? = null): ScriptedGate =
            ScriptedGate(log) { GateDecision.Admit() }

        /** A gate that holds every proposal with [reason] and [appOutcomeToken]. */
        public fun holdAll(
            reason: Any? = null,
            appOutcomeToken: String? = null,
            log: RecordingSink<String>? = null,
        ): ScriptedGate = ScriptedGate(log) { GateDecision.Hold(reason, appOutcomeToken) }

        /** A gate that answers with [decisions] in order and fails loudly when they run out. */
        public fun sequence(log: RecordingSink<String>?, vararg decisions: GateDecision): ScriptedGate {
            val script = ScriptedResponses(decisions.toList())
            return ScriptedGate(log) { script.next() }
        }
    }
}

private const val GATE_LOG = "gate"
