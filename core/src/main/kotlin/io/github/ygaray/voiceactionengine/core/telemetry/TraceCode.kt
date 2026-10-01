package io.github.ygaray.voiceactionengine.core.telemetry

/**
 * A short code the engine records in a run's trace to say what happened, never why in free text.
 *
 * This is an open set: later versions may add codes, so always keep an `else` branch when switching over it.
 *
 * @property value the stable wire value, lower snake case.
 */
@JvmInline
public value class TraceCode internal constructor(public val value: String) {
    /** The wire value. */
    override fun toString(): String = value

    /** The codes the engine records today. */
    public companion object {
        /** The pre-apply gate threw; the change was not applied. */
        public val GATE_ERROR: TraceCode = TraceCode("gate_error")

        /** Applying a change threw. */
        public val APPLY_ERROR: TraceCode = TraceCode("apply_error")

        /** The run was cancelled while a change was being applied. */
        public val APPLY_CANCELLED: TraceCode = TraceCode("apply_cancelled")

        /** The commit sink threw while receiving an action. */
        public val SINK_ERROR: TraceCode = TraceCode("sink_error")

        /** The event listener threw. */
        public val LISTENER_ERROR: TraceCode = TraceCode("listener_error")

        /** The tier policy source threw. */
        public val POLICY_SOURCE_ERROR: TraceCode = TraceCode("policy_source_error")

        /** A strategy threw instead of returning an outcome. */
        public val STRATEGY_ERROR: TraceCode = TraceCode("strategy_error")

        /** The engine's own deadline expired. */
        public val ENGINE_TIMEOUT: TraceCode = TraceCode("engine_timeout")

        /** Escalation was blocked because a tier had already committed or held a change. */
        public val ESCALATION_SUPPRESSED: TraceCode = TraceCode("escalation_suppressed")

        /** A tier was skipped because the tier policy does not allow it. */
        public val TIER_SKIPPED_POLICY: TraceCode = TraceCode("tier_skipped_policy")

        /** The policy's maximum tier is not on the ladder. */
        public val MAX_TIER_UNKNOWN: TraceCode = TraceCode("max_tier_unknown")

        /** A tier was skipped because the device is offline. */
        public val OFFLINE_UNAVAILABLE: TraceCode = TraceCode("offline_unavailable")

        /** A tier was skipped because on-device inference is not available. */
        public val ON_DEVICE_UNAVAILABLE: TraceCode = TraceCode("on_device_unavailable")

        /** The on-device availability check threw, so on-device inference was treated as unavailable. */
        public val ON_DEVICE_PROBE_ERROR: TraceCode = TraceCode("on_device_probe_error")

        /** The run was cancelled while a held change was being committed. */
        public val COMMIT_HELD_CANCELLED: TraceCode = TraceCode("commit_held_cancelled")
    }
}
