package io.github.ygaray.voiceactionengine.core.commit

/**
 * How a tool call a strategy finished was classified before it reached the commit path.
 *
 * This is an open set: later versions may add kinds, so always keep an `else` branch when switching over it.
 *
 * @property value the stable wire value.
 */
@JvmInline
public value class FinishedKind internal constructor(public val value: String) {
    /** The wire value. */
    override fun toString(): String = value

    /** The kinds the engine classifies today. */
    public companion object {
        /** A read-only call. It is never reported to the commit sink. */
        public val READ: FinishedKind = FinishedKind("read")

        /** A call that only previews a change. It is reported to the sink as a preview action. */
        public val PREVIEW: FinishedKind = FinishedKind("preview")

        /** A mutating call rejected before the gate. It is reported as an error action with `applied` false. */
        public val ERROR: FinishedKind = FinishedKind("error")
    }
}
