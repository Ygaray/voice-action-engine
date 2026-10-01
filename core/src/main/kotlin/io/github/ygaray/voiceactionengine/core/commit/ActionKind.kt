package io.github.ygaray.voiceactionengine.core.commit

/**
 * What happened to one action the engine reported to the commit sink.
 *
 * This is an open set: later versions may add kinds, so always keep an `else` branch when switching over it.
 *
 * @property value the stable wire value.
 */
@JvmInline
public value class ActionKind internal constructor(public val value: String) {
    /** The wire value. */
    override fun toString(): String = value

    /** The kinds the engine reports today. */
    public companion object {
        /** The change was applied. */
        public val COMMITTED: ActionKind = ActionKind("committed")

        /** The change was not applied and is waiting for the user to confirm it. */
        public val HELD: ActionKind = ActionKind("held")

        /** The change was shown as a preview and not applied. */
        public val PREVIEW: ActionKind = ActionKind("preview")

        /** The change was attempted and failed or was rejected. */
        public val IS_ERROR: ActionKind = ActionKind("is_error")
    }
}
