package io.github.ygaray.voiceactionengine.undo.internal

import io.github.ygaray.voiceactionengine.undo.EntityKey
import io.github.ygaray.voiceactionengine.undo.NotRestored
import io.github.ygaray.voiceactionengine.undo.UndoReason

/** What the entity restores of one pass did: the entities now as they were, and exactly what was not restored. */
internal class RestoreOutcome(val restored: Set<EntityKey>, val notRestored: List<NotRestored>)

/**
 * Carries out the writes of a verified plan, newest action first. A fault or a lost race stops the component of
 * footprints it happened in: the rest of that component is reported as skipped. Other components carry on.
 *
 * An entity is marked restored the moment its restore returned true, and never before, so a pass that is cut short
 * leaves exactly the finished entities marked.
 */
internal class Restorer(private val state: JournalState) {

    suspend fun run(steps: List<Step>, entries: List<Entry>, footprints: Footprints): RestoreOutcome {
        val restored = HashSet<EntityKey>()
        val notRestored = ArrayList<NotRestored>()
        val failed = HashSet<Int>()
        for (step in steps) {
            val component = footprints.componentOf(step.entry)
            val failure =
                if (component in failed) step.failure(UndoReason.SKIPPED_AFTER_FAILURE, null) else attempt(step)
            if (failure == null) {
                state.markRestored(entries, setOf(step.key))
                restored.add(step.key)
            } else {
                failed.add(component)
                notRestored.add(failure)
            }
        }
        return RestoreOutcome(restored, notRestored)
    }

    private suspend fun attempt(step: Step): NotRestored? =
        guardedCall(onFault = { step.failure(UndoReason.RESTORE_FAILED, it) }) {
            if (step.adapter.restoreIf(step.key.id, step.expectedFingerprint, step.snapshot)) {
                null
            } else {
                step.failure(UndoReason.CHANGED_SINCE, null)
            }
        }
}
