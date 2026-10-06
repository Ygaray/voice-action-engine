package io.github.ygaray.voiceactionengine.undo

/**
 * Why an undo could not restore something.
 *
 * This is an open set: later versions may add reasons, so always keep an `else` branch when switching over it.
 *
 * @property value the stable wire value.
 */
@JvmInline
public value class UndoReason internal constructor(public val value: String) {
    /** The wire value. */
    override fun toString(): String = value

    /** The reasons the engine reports today. */
    public companion object {
        /** The entity changed after the command. */
        public val CHANGED_SINCE: UndoReason = UndoReason("changed_since")

        /** An entity changed between two of the command's own writes. */
        public val CHAIN_BROKEN: UndoReason = UndoReason("chain_broken")

        /** The journal cannot tell what was written. */
        public val UNVERIFIABLE: UndoReason = UndoReason("unverifiable")

        /** The action shares an entity with another action; undo the whole group. */
        public val ENTANGLED: UndoReason = UndoReason("entangled")

        /** The journal missed or rejected an action of the group, so "undo all" is withheld. */
        public val JOURNAL_WITHHELD: UndoReason = UndoReason("journal_withheld")

        /** The group was never journaled, or was dropped by the limits. */
        public val UNKNOWN_GROUP: UndoReason = UndoReason("unknown_group")

        /** The group has no such action. */
        public val UNKNOWN_ENTRY: UndoReason = UndoReason("unknown_entry")

        /** Another undo of this group is running. */
        public val IN_PROGRESS: UndoReason = UndoReason("in_progress")

        /** No adapter or compensator is registered for an entity type or effect kind. */
        public val NO_ADAPTER: UndoReason = UndoReason("no_adapter")

        /** The adapter threw while restoring. */
        public val RESTORE_FAILED: UndoReason = UndoReason("restore_failed")

        /** A compensator threw. */
        public val COMPENSATOR_FAILED: UndoReason = UndoReason("compensator_failed")

        /** Not attempted because an earlier restore in the same chain failed. */
        public val SKIPPED_AFTER_FAILURE: UndoReason = UndoReason("skipped_after_failure")
    }
}
