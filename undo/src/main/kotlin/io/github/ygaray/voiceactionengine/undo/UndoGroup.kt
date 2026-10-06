package io.github.ygaray.voiceactionengine.undo

/**
 * A view of one command's group in the journal, for an app to show "Undo all (N)" from.
 *
 * This is a snapshot taken when [UndoJournal.group] was called. A held change confirmed later can still add actions
 * to the group, so ask again when the screen refreshes. The view carries keys, references, flags and counts only:
 * never an entity's content, and it prints counts only.
 *
 * @property groupKey the key the group was recorded under.
 * @property parentGroupKey the group this one continues (the first one given decides), or null.
 * @property revision rises on every change of the group, so a copy of the view can tell whether it is stale.
 * @property withheld true when the journal missed or rejected an action of the command. The group then cannot be
 *   undone ([UndoJournal.undoAll] refuses with [UndoReason.JOURNAL_WITHHELD]), and [count] must not be offered as
 *   "all".
 * @property entries every recorded action, in the order they were recorded.
 * @property pending the recorded actions not yet undone, in the same order.
 * @property isolated the pending actions whose declared entities are shared with no other pending action. Each can be
 *   undone alone with [UndoJournal.undoEntry]. Empty for a withheld group.
 */
public class UndoGroup internal constructor(
    public val groupKey: String,
    public val parentGroupKey: String?,
    public val revision: Long,
    public val withheld: Boolean,
    entries: List<EntryRef>,
    pending: List<EntryRef>,
    isolated: List<EntryRef>,
) {
    public val entries: List<EntryRef> = entries.toList()
    public val pending: List<EntryRef> = pending.toList()
    public val isolated: List<EntryRef> = isolated.toList()

    /**
     * The N of "Undo all (N)": the applied actions not yet undone. An action that was applied but reported an error,
     * and one that wrote nothing, are counted. Check [withheld] first.
     */
    public val count: Int
        get() = pending.size

    override fun toString(): String =
        "UndoGroup(count=$count, entries=${entries.size}, withheld=$withheld, revision=$revision)"
}
