package io.github.ygaray.voiceactionengine.undo

/**
 * The outcome of an undo. This is a closed set: a `when` over it needs no `else`, and it is frozen once released.
 * Reasons stay open, see [UndoReason].
 *
 * @property code the stable wire code.
 */
public sealed class UndoResult {
    public abstract val code: String

    /**
     * Every action in scope was restored.
     *
     * @property restored the restored actions, newest first.
     */
    public class Complete internal constructor(restored: List<EntryRef>) : UndoResult() {
        public val restored: List<EntryRef> = restored.toList()

        override val code: String
            get() = "complete"

        override fun toString(): String = "Complete(restored=${restored.size})"
    }

    /**
     * Nothing was written, because something could not be undone safely, or the group cannot be undone at all.
     *
     * @property blockers why, in a stable order: newest action first, then entity type and id. Never empty.
     */
    public class Refused internal constructor(blockers: List<Blocker>) : UndoResult() {
        public val blockers: List<Blocker> = blockers.toList()

        init {
            require(this.blockers.isNotEmpty()) { "a refusal needs at least one blocker" }
        }

        override val code: String
            get() = "refused"

        override fun toString(): String = "Refused(blockers=${blockers.size})"
    }

    /**
     * The undo went ahead (every check passed) and at least one item was not restored. It is not "some was restored":
     * when the very first step fails, nothing was written and [restored] is empty.
     *
     * @property restored the actions that were fully restored; may be empty.
     * @property notRestored exactly what was not, with the reason. Never empty.
     */
    public class Partial internal constructor(
        restored: List<EntryRef>,
        notRestored: List<NotRestored>,
    ) : UndoResult() {
        public val restored: List<EntryRef> = restored.toList()
        public val notRestored: List<NotRestored> = notRestored.toList()

        init {
            require(this.notRestored.isNotEmpty()) { "a partial result needs at least one unrestored item" }
        }

        override val code: String
            get() = "partial"

        override fun toString(): String = "Partial(restored=${restored.size}, notRestored=${notRestored.size})"
    }

    /** There was nothing left to undo. */
    public class AlreadyUndone internal constructor() : UndoResult() {
        override val code: String
            get() = "already_undone"

        override fun toString(): String = "AlreadyUndone"
    }
}

/**
 * One reason an undo was refused.
 *
 * @property entry the action involved, or null for a refusal of the whole group.
 * @property entity the entity involved, or null when the reason is not about one entity.
 * @property reason why.
 */
public class Blocker internal constructor(
    public val entry: EntryRef?,
    public val entity: EntityKey?,
    public val reason: UndoReason,
) {
    override fun toString(): String =
        "Blocker(entry=${entry != null}, entity=${entity != null}, reason=$reason)"
}

/**
 * One thing an undo could not restore.
 *
 * @property entry the action it belongs to.
 * @property entity the entity, or null for an out-of-database effect.
 * @property compensator the compensator kind, or null for an entity.
 * @property reason why it was not restored.
 * @property errorClass the class name of the fault, never its message, or null when nothing was thrown.
 */
public class NotRestored internal constructor(
    public val entry: EntryRef,
    public val entity: EntityKey?,
    public val compensator: String?,
    public val reason: UndoReason,
    public val errorClass: String?,
) {
    override fun toString(): String =
        "NotRestored(entity=${entity != null}, compensator=${compensator != null}, reason=$reason, " +
            "errorClass=$errorClass)"
}
