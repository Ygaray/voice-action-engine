package io.github.ygaray.voiceactionengine.core.commit

import io.github.ygaray.voiceactionengine.core.internal.guarded
import io.github.ygaray.voiceactionengine.core.telemetry.RunRecorder
import io.github.ygaray.voiceactionengine.core.telemetry.TraceCode
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.CopyOnWriteArrayList

// The bytes a strategy receives for a held change; apps compare against this exact string.
private const val HELD_FOR_CONFIRMATION = """{"applied":false,"status":"held_for_confirmation"}"""
private const val APPLY_FAILED_CONTENT = """{"status":"error"}"""
private const val CONTENT_SEPARATOR = "\n"

/** The bytes a strategy receives when a change is held. */
internal fun heldForConfirmationContent(): String = HELD_FOR_CONFIRMATION

/**
 * The engine's single write path: the gate decides, an admitted change is applied by [PendingMutation.apply], and the
 * sink observes each action before the next change starts. A strategy never reaches `apply` any other way.
 *
 * Submissions are serialized by a mutex so positions stay in dispatch order even if a strategy submits concurrently.
 */
internal class CommitCoordinator(
    private val runId: String,
    private val parentRunId: String?,
    private val gate: PreApplyGate,
    private val sink: CommitSink,
    private val recorder: RunRecorder,
) {
    private val mutex = Mutex()
    private val recorded = CopyOnWriteArrayList<ExecutedAction>()
    private val heldProposals = CopyOnWriteArrayList<HeldProposal>()

    /** Applies, in order, whatever [step] asks for and the gate allows. */
    suspend fun submit(step: ToolStep): DispatchResult = mutex.withLock {
        when (step) {
            is ToolStep.Mutation -> submitMutation(step)
            is ToolStep.Finished -> DispatchResult(step.result.contentForModel, step.result.isError, false, emptyList())
        }
    }

    /** Every action recorded so far, in position order. */
    fun executed(): List<ExecutedAction> = recorded.toList()

    /** Changes the gate held so far. */
    fun held(): List<HeldProposal> = heldProposals.toList()

    /** How many actions had their apply run, including errored applies. */
    val appliedCount: Int
        get() = recorded.count { it.applied }

    /** How many held proposals exist. */
    val heldCount: Int
        get() = heldProposals.size

    private suspend fun submitMutation(step: ToolStep.Mutation): DispatchResult {
        val proposal = CommitProposal(runId, parentRunId, step.mutations)
        val decision = guarded(onFault = {
            recorder.recordCode(TraceCode.GATE_ERROR)
            GateDecision.Hold()
        }) { gate.admit(proposal) }
        return when (decision) {
            is GateDecision.Admit -> applyAll(decision.amended ?: step.mutations)
            is GateDecision.Hold -> DispatchResult(heldForConfirmationContent(), false, true, emptyList())
        }
    }

    private suspend fun applyAll(mutations: List<PendingMutation>): DispatchResult {
        val actions = mutableListOf<ExecutedAction>()
        val contents = mutableListOf<String>()
        for (mutation in mutations) {
            val result = applyOne(mutation)
            val action = record(mutation, result)
            actions.add(action)
            contents.add(result.contentForModel)
            deliver(action)
        }
        return DispatchResult(
            contentForModel = contents.joinToString(CONTENT_SEPARATOR),
            isError = actions.any { it.kind == ActionKind.IS_ERROR },
            held = false,
            actions = actions,
        )
    }

    private suspend fun applyOne(mutation: PendingMutation): StepResult = guarded(onFault = {
        recorder.recordCode(TraceCode.APPLY_ERROR)
        StepResult(APPLY_FAILED_CONTENT, true, null, emptyMap())
    }) { mutation.apply() }

    private fun record(mutation: PendingMutation, result: StepResult): ExecutedAction {
        val action = ExecutedAction(
            position = recorded.size,
            kind = if (result.isError) ActionKind.IS_ERROR else ActionKind.COMMITTED,
            applied = true,
            appOutcomeToken = result.appOutcomeToken,
            toolName = mutation.toolName,
            targetIds = mutation.targetIds + result.targetIds,
            context = mutation.context,
        )
        recorded.add(action)
        return action
    }

    private suspend fun deliver(action: ExecutedAction) {
        withContext(NonCancellable) {
            guarded(onFault = { recorder.recordCode(TraceCode.SINK_ERROR) }) {
                sink.onAction(ActionEvent(runId, parentRunId, action))
            }
        }
    }
}
