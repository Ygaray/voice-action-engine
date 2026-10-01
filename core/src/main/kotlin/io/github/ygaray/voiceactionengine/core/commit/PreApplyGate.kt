package io.github.ygaray.voiceactionengine.core.commit

/**
 * The app's decision point before any change is written.
 *
 * Only an [GateDecision.Admit] lets a change be applied. If this throws, the engine treats it as a hold and records
 * a trace code. In suspend mode an implementation waits for the user inside [admit]; in defer mode it returns
 * [GateDecision.Hold] at once and the change is committed later.
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
     * @property amended replacement changes to apply instead of the proposed ones, or null to apply the proposal as is.
     */
    public class Admit(public val amended: List<PendingMutation>?) : GateDecision() {
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
