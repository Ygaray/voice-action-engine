package io.github.ygaray.voiceactionengine.core.commit

import io.github.ygaray.voiceactionengine.core.pipeline.CommandOutcome
import kotlinx.coroutines.CompletableDeferred
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Changes the gate held instead of applying. Held in-memory only: it does not survive the process.
 *
 * Commit it later with `CommandPipeline.commitHeld`. That runs without asking the gate again, so the app must
 * re-validate inside each change's `apply`: the world may have moved on since the change was held. A proposal is
 * resolved at most once; a second `commitHeld` returns the first call's outcome and writes nothing.
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
    /** Set by the one caller that wins the right to commit this proposal. */
    internal val claimed: AtomicBoolean = AtomicBoolean(false)

    /** The outcome of that one commit, for every other caller to wait on. */
    internal val result: CompletableDeferred<CommandOutcome> = CompletableDeferred()

    override fun toString(): String =
        "HeldProposal(runId=$runId, mutations=${mutations.size}, reason=${reason?.let { it::class.simpleName }})"
}
