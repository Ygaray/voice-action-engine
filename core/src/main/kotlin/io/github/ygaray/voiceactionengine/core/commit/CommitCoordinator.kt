package io.github.ygaray.voiceactionengine.core.commit

import io.github.ygaray.voiceactionengine.core.internal.guarded
import io.github.ygaray.voiceactionengine.core.telemetry.RunRecorder
import io.github.ygaray.voiceactionengine.core.telemetry.TraceCode
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

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
    gate: PreApplyGate,
    private val sink: CommitSink,
    private val recorder: RunRecorder,
) {
    private val mutex = Mutex()
    private val ledger = ActionLedger()
    private val gateStep = GateStep(gate, recorder)

    /** Applies, in order, whatever [step] asks for and the gate allows. */
    suspend fun submit(step: ToolStep): DispatchResult = mutex.withLock {
        when (step) {
            is ToolStep.Mutation -> submitMutation(step)
            is ToolStep.Finished -> DispatchResult(step.result.contentForModel, step.result.isError, false, emptyList())
        }
    }

    /** Every action recorded so far, in position order. */
    fun executed(): List<ExecutedAction> = ledger.executed()

    /** Changes the gate held so far. */
    fun held(): List<HeldProposal> = ledger.held()

    /** How many actions had their apply run, including errored applies. */
    val appliedCount: Int
        get() = ledger.appliedCount

    /** How many held proposals exist. */
    val heldCount: Int
        get() = ledger.heldCount

    private suspend fun submitMutation(step: ToolStep.Mutation): DispatchResult {
        val decision = gateStep.decide(CommitProposal(runId, parentRunId, step.mutations))
        return when (decision) {
            is GateDecision.Admit -> applyAll(decision.amended ?: step.mutations)
            is GateDecision.Hold -> hold(step, decision)
        }
    }

    /**
     * Nothing is written. Every mutation is reported as a held action carrying the gate's own token, one proposal
     * lists them with the gate's reason object, and the strategy gets the fixed not-applied notice. A held change is
     * not done and the model must not retry it.
     */
    private suspend fun hold(step: ToolStep.Mutation, decision: GateDecision.Hold): DispatchResult {
        ledger.addHeld(HeldProposal(runId, parentRunId, step.mutations, decision.reason, decision.appOutcomeToken))
        val actions = step.mutations.map { mutation ->
            val details = ActionDetails(
                mutation.toolName,
                decision.appOutcomeToken,
                mutation.targetIds,
                mutation.context,
            )
            ledger.record(ActionKind.HELD, applied = false, details = details).also { deliver(it) }
        }
        return DispatchResult(heldForConfirmationContent(), false, true, actions)
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
        val kind = if (result.isError) ActionKind.IS_ERROR else ActionKind.COMMITTED
        val details = ActionDetails(
            mutation.toolName,
            result.appOutcomeToken,
            mutation.targetIds + result.targetIds,
            mutation.context,
        )
        return ledger.record(kind, applied = true, details = details)
    }

    private suspend fun deliver(action: ExecutedAction) {
        withContext(NonCancellable) {
            guarded(onFault = { recorder.recordCode(TraceCode.SINK_ERROR) }) {
                sink.onAction(ActionEvent(runId, parentRunId, action))
            }
        }
    }
}
