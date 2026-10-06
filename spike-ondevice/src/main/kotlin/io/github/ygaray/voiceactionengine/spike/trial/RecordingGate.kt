package io.github.ygaray.voiceactionengine.spike.trial

import io.github.ygaray.voiceactionengine.core.commit.ActionEvent
import io.github.ygaray.voiceactionengine.core.commit.CommitProposal
import io.github.ygaray.voiceactionengine.core.commit.CommitSink
import io.github.ygaray.voiceactionengine.core.commit.GateDecision
import io.github.ygaray.voiceactionengine.core.commit.PreApplyGate
import io.github.ygaray.voiceactionengine.core.commit.RunTermination

/**
 * The spike's gate: it records the tool names of every proposal and admits. Admitting is safe because the only mutation
 * the resolver ever builds is a no-op, so a "write" is counted here (a false write for a negative item) and never applied.
 * [toString] shows counts only.
 */
internal class RecordingGate : PreApplyGate {
    private val seen = ArrayList<String>()

    /** The tool names of every proposal since [reset], in order. */
    val proposedTools: List<String> get() = seen.toList()

    /** Forgets what was proposed; a trial starts from here. */
    fun reset() {
        seen.clear()
    }

    override suspend fun admit(proposal: CommitProposal): GateDecision {
        proposal.mutations.forEach { seen.add(it.toolName) }
        return GateDecision.Admit()
    }

    override fun toString(): String = "RecordingGate(proposals=${seen.size})"
}

/** A sink that drops every event: the spike scores trials itself and keeps no action ledger. */
internal object DiscardingCommitSink : CommitSink {
    override suspend fun onAction(event: ActionEvent) = Unit

    override suspend fun onRunClosed(runId: String, termination: RunTermination) = Unit

    override fun toString(): String = "DiscardingCommitSink"
}
