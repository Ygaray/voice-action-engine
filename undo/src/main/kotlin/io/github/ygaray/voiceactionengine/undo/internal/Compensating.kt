package io.github.ygaray.voiceactionengine.undo.internal

import io.github.ygaray.voiceactionengine.undo.Compensator
import io.github.ygaray.voiceactionengine.undo.EntityKey
import io.github.ygaray.voiceactionengine.undo.NotRestored
import io.github.ygaray.voiceactionengine.undo.UndoReason

/**
 * Reverses the effects outside the database, after the entity restores. Actions go newest first, and the effects of
 * one action in the reverse of the order they were declared. An action whose entities were not all restored has its
 * effects reported as skipped and never runs them. One effect failing does not stop the others, because each is
 * independent of the rest.
 *
 * An effect is marked done only after its compensator returned, so a failed or cancelled one runs again on a retry
 * and a finished one never does.
 */
internal class Compensating(
    private val compensators: Map<String, Compensator>,
    private val state: JournalState,
) {

    /** [restored] is every entity that is as it was after the restores, including the ones of an earlier pass. */
    suspend fun run(pending: List<PendingEntry>, restored: Set<EntityKey>): List<NotRestored> {
        val notRestored = ArrayList<NotRestored>()
        for (item in pending.sortedByDescending { it.entry.sequence }) {
            val ready = restored.containsAll(item.entry.keys)
            for ((index, compensation) in item.pendingCompensations().asReversed()) {
                val failure =
                    if (ready) attempt(item.entry, index, compensation) else skipped(item.entry, compensation)
                if (failure != null) notRestored.add(failure)
            }
        }
        return notRestored
    }

    private suspend fun attempt(entry: Entry, index: Int, compensation: Compensation): NotRestored? {
        val compensator = compensators[compensation.kind]
            ?: return failure(entry, compensation, UndoReason.NO_ADAPTER, null)
        val failure = guardedCall(onFault = { failure(entry, compensation, UndoReason.COMPENSATOR_FAILED, it) }) {
            compensator.compensate(compensation.payload)
            null
        }
        if (failure == null) state.markCompensated(entry, index)
        return failure
    }

    private fun skipped(entry: Entry, compensation: Compensation): NotRestored =
        failure(entry, compensation, UndoReason.SKIPPED_AFTER_FAILURE, null)

    private fun failure(entry: Entry, compensation: Compensation, reason: UndoReason, errorClass: String?) =
        NotRestored(entry.ref, null, compensation.kind, reason, errorClass)
}
