package io.github.ygaray.voiceactionengine.undo

/**
 * An optional mirror of what the journal holds, for an app that wants to show undo history somewhere else.
 *
 * The journal lives in memory and does not survive process death. A store is told about each group's view after every
 * change ([save]) and when the journal drops a group ([delete]). It receives keys, references, flags and counts only:
 * never an entity's snapshot or fingerprint, so it cannot restore the journal and nothing user-written reaches it.
 *
 * Calls for one group may arrive out of order when the journal is used from several threads, so keep the view with the
 * highest [UndoGroup.revision]. A store that throws never changes what the journal does: the fault is counted in
 * [UndoJournal.storeFaults] and the journal carries on. A cancellation is not a fault, and reaches the caller.
 */
public interface JournalStore {
    /** The group's view after a change. */
    public suspend fun save(group: UndoGroup)

    /** The journal dropped the group, by its limits. */
    public suspend fun delete(groupKey: String)
}
