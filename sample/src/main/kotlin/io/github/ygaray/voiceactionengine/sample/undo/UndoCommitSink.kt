package io.github.ygaray.voiceactionengine.sample.undo

import io.github.ygaray.voiceactionengine.core.commit.ActionEvent
import io.github.ygaray.voiceactionengine.core.commit.ActionKind
import io.github.ygaray.voiceactionengine.core.commit.CommitSink
import io.github.ygaray.voiceactionengine.core.commit.RunTermination
import io.github.ygaray.voiceactionengine.undo.EntryRef
import io.github.ygaray.voiceactionengine.undo.UndoJournal
import io.github.ygaray.voiceactionengine.undo.UndoTicket
import java.util.concurrent.ConcurrentHashMap

// undo-bridge:start
// Feeds the undo journal from the pipeline. List it FIRST in compositeSink(...), so the journal already holds an
// action when the app's own sink reacts to it. The number in "Undo all (N)" is journal.group(key)?.count; held
// proposals that are not confirmed yet are shown apart (pendingHeld) and are never part of N. The three maps are
// small and keyed by run id; an app that runs for days should prune them with the journal's own limits.
class UndoCommitSink(private val journal: UndoJournal) : CommitSink {
    private val groups = ConcurrentHashMap<String, String>() // run id -> group key
    private val parents = ConcurrentHashMap<String, String>() // run id -> the run it answers
    private val pending = ConcurrentHashMap<String, Int>() // group key -> held proposals not yet confirmed

    fun groupOf(runId: String): String = groups[runId] ?: runId
    fun pendingHeld(groupKey: String): Int = pending[groupKey] ?: 0
    fun discarded(groupKey: String) {
        pending.computeIfPresent(groupKey) { _, count -> if (count <= 1) null else count - 1 }
    }

    override suspend fun onAction(event: ActionEvent) {
        val action = event.action
        val confirmed = event.heldRunId != null
        // A change confirmed later joins the command that held it; every other run is its own group.
        val group = event.heldRunId ?: event.runId
        // The first event of a confirmed child run settles one held proposal.
        if (groups.putIfAbsent(event.runId, group) == null && confirmed) discarded(group)
        event.parentRunId?.let { parents.putIfAbsent(event.runId, it) }
        if (!action.applied) return
        // A reply keeps the group it continues, so a combined undo stays possible; a confirmed child continues what
        // the held run continued, never the held run itself.
        val parent = (if (confirmed) parents[group] else event.parentRunId)?.let(::groupOf)
        try {
            val entry = EntryRef(event.runId, action.position, action.toolName)
            journal.record(group, parent, entry, action.kind == ActionKind.IS_ERROR, action.context as? UndoTicket)
        } catch (_: IllegalArgumentException) {
            // An action the journal cannot take must withhold "Undo all", never shrink it. A group key the journal
            // rejects (blank, over 256 characters) cannot be withheld either: no group exists to offer "Undo all" for.
            try {
                journal.withhold(group)
            } catch (_: IllegalArgumentException) {
                // nothing to withhold
            }
        }
    }

    override suspend fun onRunClosed(runId: String, termination: RunTermination) {
        val group = groupOf(runId)
        if (termination.held.isNotEmpty()) pending.merge(group, termination.held.size, Int::plus)
        val applied = termination.executed.filter { it.applied }.map { it.position }.toSet()
        if (applied.isNotEmpty()) journal.runClosed(group, runId, applied)
    }
}
// undo-bridge:end
