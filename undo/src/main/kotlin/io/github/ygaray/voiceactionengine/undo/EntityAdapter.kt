package io.github.ygaray.voiceactionengine.undo

/**
 * The app's window onto one kind of entity: how to read it, tell whether it changed, and put it back.
 *
 * One adapter is registered per entity type. The journal never touches storage itself, so the app owns the
 * transaction around every restore.
 */
public interface EntityAdapter {
    /** The entity type this adapter serves; it is the key tickets and results use. */
    public val entityType: String

    /**
     * The entity's current state as an opaque snapshot, or null when it does not exist.
     *
     * The snapshot is handed back to [restoreIf] unchanged, so it must carry everything needed to rebuild the entity
     * (for a parent, its children too) and must not change after it is returned.
     */
    public suspend fun read(id: String): Any?

    /**
     * An opaque hash of the entity's current content and version, or null when it does not exist.
     *
     * Two reads of an unchanged entity give equal values; any change gives a different one.
     */
    public suspend fun fingerprint(id: String): String?

    /**
     * Puts the entity back, atomically, inside the app's own transaction.
     *
     * When the live fingerprint equals [expectedFingerprint] (null means the entity must be absent), make the entity
     * equal [snapshot] and return true:
     * - a null [snapshot] means delete;
     * - an absent entity is re-inserted with its original id and its children.
     *
     * When the live state differs, write nothing and return false. Throw only for a real failure.
     */
    public suspend fun restoreIf(id: String, expectedFingerprint: String?, snapshot: Any?): Boolean
}

/**
 * Reverses one effect that lives outside the database (an alarm, a notification, a file).
 *
 * A compensator runs only after the entity restores of its action, and it may be retried, so it must be idempotent.
 */
public fun interface Compensator {
    /** Reverses the effect described by [payload], the string the app gave to the ticket. */
    public suspend fun compensate(payload: String)
}
