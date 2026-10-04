package io.github.ygaray.voiceactionengine.core.commit

/**
 * The app's decision point before any change is written.
 *
 * Only an [GateDecision.Admit] lets a change be applied. If this throws, nothing is applied and the engine records the
 * trace code `gate_error`. A gate fault is an error, never a hold: it is reported as an is_error action with code
 * gate_error, and there is nothing to commitHeld (nor is it ever reported as committed). The strategy is told the
 * call failed with a fixed internal-error notice, unlike the held notice a real [GateDecision.Hold] produces. In
 * suspend mode an implementation waits for the user inside [admit]; in defer mode it returns [GateDecision.Hold] at
 * once and the change is committed later.
 */
public fun interface PreApplyGate {
    /** Decides about [proposal]. */
    public suspend fun admit(proposal: CommitProposal): GateDecision
}

/** The gate's answer. The set is closed: admit or hold. */
public sealed class GateDecision {
    /**
     * Apply the changes.
     *
     * @param amended replacement changes to apply instead of the proposed ones, or null to apply the proposal as is.
     * Copied on construction. An empty list is refused with [IllegalArgumentException] (a gate that throws holds), as
     * a change must have at least one mutation.
     */
    public class Admit(amended: List<PendingMutation>?) : GateDecision() {
        /** The replacement changes, or null to apply the proposal as is. Never empty. */
        public val amended: List<PendingMutation>? = amended?.toList()

        init {
            require(this.amended == null || this.amended.isNotEmpty()) {
                "Admit: amended must be null or hold at least one mutation"
            }
        }

        /** Apply the proposal as is. */
        public constructor() : this(null)

        override fun toString(): String = "Admit(amended=${amended?.size})"
    }

    /**
     * Do not apply now.
     *
     * @property reason an opaque app object saying why, kept on the held proposal; the engine never inspects it.
     * @property appOutcomeToken the app's own outcome string for the held actions, or null.
     */
    public class Hold(
        public val reason: Any?,
        public val appOutcomeToken: String?,
    ) : GateDecision() {
        /** Hold with no reason and no token. */
        public constructor() : this(null, null)

        /** Hold with [reason] and no token. */
        public constructor(reason: Any?) : this(reason, null)

        override fun toString(): String {
            val token = if (appOutcomeToken == null) "absent" else "present"
            return "Hold(reason=${reason?.let { it::class.simpleName }}, token=$token)"
        }
    }
}

/**
 * The changes a strategy submitted, put to the gate.
 *
 * @property runId the id of the run.
 * @property parentRunId the id of the earlier run this command answers, or null.
 * @property mutations the pending changes in submission order.
 */
public class CommitProposal internal constructor(
    public val runId: String,
    public val parentRunId: String?,
    public val mutations: List<PendingMutation>,
) {
    override fun toString(): String =
        "CommitProposal(runId=$runId, mutations=${mutations.size}, tools=${mutations.map { it.toolName }})"
}
