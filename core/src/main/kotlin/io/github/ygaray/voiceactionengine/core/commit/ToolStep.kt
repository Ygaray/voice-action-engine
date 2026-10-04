package io.github.ygaray.voiceactionengine.core.commit

/** One thing a strategy asks the engine to do with a tool call. The set is closed: finished, or a mutation to gate. */
public sealed class ToolStep {
    /**
     * A tool call the strategy already carried out because it needs no gate (a read, a preview, or a rejection).
     *
     * @property toolName the tool's name.
     * @property kind how the call was classified.
     * @property result what the call produced.
     * @property context an opaque app object reported back on the action, or null.
     */
    public class Finished(
        public val toolName: String,
        public val kind: FinishedKind,
        public val result: StepResult,
        public val context: Any?,
    ) : ToolStep() {
        /** A finished call with no context. */
        public constructor(toolName: String, kind: FinishedKind, result: StepResult) :
            this(toolName, kind, result, null)

        override fun toString(): String = "Finished(toolName=$toolName, kind=$kind, result=$result)"
    }

    /**
     * Changes the app wants made, in order. Nothing is applied until the gate admits them.
     *
     * @property mutations the pending changes, at least one. A defensive copy is kept.
     */
    public class Mutation(mutations: List<PendingMutation>) : ToolStep() {
        /** The pending changes in the order they will be applied. */
        public val mutations: List<PendingMutation> = mutations.toList()

        init {
            require(this.mutations.isNotEmpty()) { "Mutation needs at least one pending mutation" }
        }

        /** A step with a single pending change. */
        public constructor(mutation: PendingMutation) : this(listOf(mutation))

        override fun toString(): String =
            "Mutation(mutations=${mutations.size}, tools=${mutations.map { it.toolName }})"
    }
}

/** A change waiting for the gate's decision. */
public interface PendingMutation {
    /** The tool's name, reported on the action. */
    public val toolName: String

    /** Ids of the things this change touches, reported on the action. Empty by default. */
    public val targetIds: Map<String, String>
        get() = emptyMap()

    /**
     * An opaque app object reported back on the action (for example a snapshot taken before the change). The engine
     * never inspects it. Null by default.
     */
    public val context: Any?
        get() = null

    /**
     * Makes the change. The engine calls this only after the gate admits. A held change may be applied later against
     * state that has moved on, so implementations must re-validate before writing.
     */
    public suspend fun apply(): StepResult
}

/**
 * What a tool call produced.
 *
 * @property contentForModel the text to give the model.
 * @property isError true when the call failed.
 * @property appOutcomeToken the app's own outcome string; it reaches the commit sink byte for byte.
 * @property targetIds ids of the things the call touched; they are merged over the mutation's own ids.
 */
public class StepResult(
    public val contentForModel: String,
    public val isError: Boolean,
    public val appOutcomeToken: String?,
    targetIds: Map<String, String>,
) {
    /** A copy of the ids the call touched. */
    public val targetIds: Map<String, String> = targetIds.toMap()

    /** A successful result with no token and no ids. */
    public constructor(contentForModel: String) : this(contentForModel, false, null, emptyMap())

    /** A result with no token and no ids. */
    public constructor(contentForModel: String, isError: Boolean) : this(contentForModel, isError, null, emptyMap())

    /** Prints the content length, never the content. */
    override fun toString(): String =
        "StepResult(contentLength=${contentForModel.length}, isError=$isError, " +
            "token=${if (appOutcomeToken == null) "absent" else "present"}, targetIds=${targetIds.size})"
}

/**
 * What the engine tells a strategy after a step.
 *
 * @property contentForModel the text to give the model. For one mutation this is that mutation's content; for
 * several it is their contents joined with a newline in the order applied; for a held step it is the held notice;
 * when the gate itself failed it is a fixed internal-error notice, never the exception text.
 * @property isError true when any applied change reported an error, or when the gate failed (nothing was applied and
 * nothing is held; the failure is an error for the model, and the model may retry).
 * @property held true when nothing was done because the change is waiting for confirmation. The model must not retry.
 * A gate failure is not a hold: it is reported with [isError] true and [held] false.
 * @property actions the actions this step recorded, in order.
 */
public class DispatchResult internal constructor(
    public val contentForModel: String,
    public val isError: Boolean,
    public val held: Boolean,
    public val actions: List<ExecutedAction>,
) {
    /** Prints the content length, never the content. */
    override fun toString(): String =
        "DispatchResult(contentLength=${contentForModel.length}, isError=$isError, held=$held, actions=${actions.size})"
}
