package io.github.ygaray.voiceactionengine.undo

import io.github.ygaray.voiceactionengine.undo.internal.requireToken

/**
 * One recorded action.
 *
 * In a pipeline, [runId] and [position] are the engine's, and [toolName] is the action's tool. Two references are
 * equal when their run and position are.
 *
 * @property runId the run that applied the action.
 * @property position the action's position inside that run, from zero.
 * @property toolName the tool that applied it.
 * @throws IllegalArgumentException when [runId] or [toolName] is blank or longer than 256 characters, or [position]
 *   is negative.
 */
public class EntryRef(
    public val runId: String,
    public val position: Int,
    public val toolName: String,
) {
    init {
        requireToken("runId", runId)
        requireToken("toolName", toolName)
        require(position >= 0) { "position must not be negative" }
    }

    override fun equals(other: Any?): Boolean =
        other is EntryRef && other.runId == runId && other.position == position

    override fun hashCode(): Int = 31 * runId.hashCode() + position

    override fun toString(): String = "EntryRef(runId=$runId, position=$position, toolName=$toolName)"
}

/**
 * One entity an action touched: the entity type an adapter is registered under, and the entity's id.
 *
 * The id is user data, so it is never printed: only its length is.
 *
 * @property type the entity type.
 * @property id the entity id, as the app's adapter understands it.
 */
public class EntityKey internal constructor(
    public val type: String,
    public val id: String,
) {
    override fun equals(other: Any?): Boolean = other is EntityKey && other.type == type && other.id == id

    override fun hashCode(): Int = 31 * type.hashCode() + id.hashCode()

    override fun toString(): String = "EntityKey(type=$type, idLength=${id.length})"
}
