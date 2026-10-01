package io.github.ygaray.voiceactionengine.core.commit

/**
 * Changes the gate held instead of applying. In memory only: it does not survive the process.
 *
 * @property runId the id of the run that held them.
 * @property parentRunId the id of the earlier run that run answered, or null.
 * @property mutations the held changes in submission order.
 * @property reason the gate's opaque reason object, by identity, or null.
 * @property appOutcomeToken the gate's own outcome string, or null.
 */
public class HeldProposal internal constructor(
    public val runId: String,
    public val parentRunId: String?,
    public val mutations: List<PendingMutation>,
    public val reason: Any?,
    public val appOutcomeToken: String?,
) {
    override fun toString(): String =
        "HeldProposal(runId=$runId, mutations=${mutations.size}, reason=${reason?.let { it::class.simpleName }})"
}
