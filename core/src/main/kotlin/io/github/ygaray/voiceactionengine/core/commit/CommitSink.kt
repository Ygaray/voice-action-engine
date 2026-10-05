package io.github.ygaray.voiceactionengine.core.commit

/**
 * Where the app hears about every change as it happens, for example to keep an undo journal.
 *
 * [onAction] is called once per action, in order, and awaited before the next change is applied. [onRunClosed] is
 * called exactly once per run on every exit path, including cancellation. A throw from either is caught and recorded;
 * it never causes a change to be applied again.
 *
 * A gate fault is an error, never a hold: it is reported as an is_error action with code gate_error, and there is
 * nothing to commitHeld. It is never reported as committed either: it is not in [RunTermination.commits], and no
 * action of kind [ActionKind.COMMITTED] stands for it.
 */
public interface CommitSink {
    /** Called after [event]'s action was recorded. */
    public suspend fun onAction(event: ActionEvent)

    /** Called once when the run ends, with a [RunTermination] saying how it ended. */
    public suspend fun onRunClosed(runId: String, termination: RunTermination)
}

/**
 * One action reaching the sink.
 *
 * @property runId the id of the run.
 * @property parentRunId the id of the earlier run this command answers, or null.
 * @property action what happened.
 */
public class ActionEvent internal constructor(
    public val runId: String,
    public val parentRunId: String?,
    public val action: ExecutedAction,
) {
    override fun toString(): String = "ActionEvent(runId=$runId, parentRunId=$parentRunId, action=$action)"
}

/**
 * One action the engine recorded.
 *
 * @property position the identity of the action: assigned by the engine, rising within a run in dispatch order, and
 * never a provider's tool-use id.
 * @property kind what happened to the change.
 * @property applied true when the app's apply ran (a committed change and an errored apply), false for held,
 * preview and rejected changes.
 * @property appOutcomeToken the app's own outcome string, byte for byte, or null.
 * @property toolName the tool's name.
 * @property targetIds ids of the things touched.
 * @property context the app's opaque object from the pending mutation, by identity.
 * @property providerCallId the provider's tool-call id of the call that produced this action, byte for byte, or null
 * for a tier that made no provider tool call. It says which model call asked for the change; [position] is still the
 * action's identity.
 * @property mutating false for an action that only previews or rejects a change.
 */
public class ExecutedAction internal constructor(
    public val position: Int,
    public val kind: ActionKind,
    public val applied: Boolean,
    public val appOutcomeToken: String?,
    public val toolName: String,
    public val targetIds: Map<String, String>,
    public val context: Any?,
    public val providerCallId: String?,
    public val mutating: Boolean = true,
) {
    /** Prints position, kind, tool name and the context's class name; never tokens or content. */
    override fun toString(): String =
        "ExecutedAction(position=$position, kind=$kind, applied=$applied, toolName=$toolName, " +
            "targetIds=${targetIds.size}, context=${context?.let { it::class.simpleName }}, mutating=$mutating)"
}
