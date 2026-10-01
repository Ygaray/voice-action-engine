package io.github.ygaray.voiceactionengine.core.commit

import io.github.ygaray.voiceactionengine.core.telemetry.RunRecorder
import java.util.concurrent.CopyOnWriteArrayList

/**
 * What an action says about the change it reports, apart from its kind and whether the apply ran.
 *
 * @property toolName the tool's name.
 * @property appOutcomeToken the app's own outcome string, passed on byte for byte.
 * @property targetIds the ids of the things touched.
 * @property context the app's opaque object, passed on by identity.
 * @property mutating false for an action that only previews or rejects a change.
 */
internal class ActionDetails(
    val toolName: String,
    val appOutcomeToken: String?,
    val targetIds: Map<String, String>,
    val context: Any?,
    val mutating: Boolean = true,
)

/**
 * Everything one run has done to the app: the ordered actions of all four kinds, the proposals the gate held, and
 * the counts the escalation guard reads.
 *
 * Positions are handed out here, rising from 0 in the order actions are recorded. Writers are serialized by the
 * coordinator's mutex; readers (the outcome snapshot, the run close) may run at any time and see a consistent copy.
 */
internal class ActionLedger(private val recorder: RunRecorder) {
    private val lock = Any()
    private val actions = CopyOnWriteArrayList<ExecutedAction>()
    private val proposals = CopyOnWriteArrayList<HeldProposal>()

    /**
     * Records an action of [kind] and returns it, with the next position. This is the only place an action is
     * appended, so it is also the only place the run announces one.
     */
    suspend fun record(kind: ActionKind, applied: Boolean, details: ActionDetails): ExecutedAction {
        val action = synchronized(lock) {
            val made = ExecutedAction(
                position = actions.size,
                kind = kind,
                applied = applied,
                appOutcomeToken = details.appOutcomeToken,
                toolName = details.toolName,
                targetIds = details.targetIds,
                context = details.context,
                mutating = details.mutating,
            )
            actions.add(made)
            made
        }
        recorder.actionRecorded(action)
        return action
    }

    /** Remembers a change the gate held. */
    fun addHeld(proposal: HeldProposal) {
        proposals.add(proposal)
    }

    /** Every action recorded so far, in position order. */
    fun executed(): List<ExecutedAction> = actions.toList()

    /** Changes the gate held so far. */
    fun held(): List<HeldProposal> = proposals.toList()

    /** How many actions had their apply run, including errored and cancelled applies. */
    val appliedCount: Int
        get() = actions.count { it.applied }

    /** How many held proposals exist. */
    val heldCount: Int
        get() = proposals.size
}
