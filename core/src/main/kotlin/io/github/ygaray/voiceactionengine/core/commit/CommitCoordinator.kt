package io.github.ygaray.voiceactionengine.core.commit

import io.github.ygaray.voiceactionengine.core.telemetry.RunRecorder
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

// The bytes a strategy receives for a held change; apps compare against this exact string.
private const val HELD_FOR_CONFIRMATION = """{"applied":false,"status":"held_for_confirmation"}"""
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
    sink: CommitSink,
    recorder: RunRecorder,
) {
    private val mutex = Mutex()

    @Volatile
    private var closed = false
    private val ledger = ActionLedger()
    private val gateStep = GateStep(gate, recorder)
    private val delivery = ActionDelivery(runId, parentRunId, sink, recorder)
    private val applyStep = ApplyStep(ledger, delivery, recorder)

    /**
     * Applies, in order, whatever [step] asks for and the gate allows. Throws [IllegalStateException] once the run is
     * closed, before anything is recorded, asked of the gate, applied or delivered.
     */
    suspend fun submit(step: ToolStep): DispatchResult = mutex.withLock {
        check(!closed) { "run $runId is closed" }
        when (step) {
            is ToolStep.Mutation -> submitMutation(step)
            is ToolStep.Finished -> finished(step)
        }
    }

    /**
     * Applies [mutations] one at a time without asking the gate, with the same recording and delivery as an admitted
     * change. Used to commit held changes later; throws [IllegalStateException] once the run is closed.
     */
    suspend fun applyWithoutGate(mutations: List<PendingMutation>): DispatchResult = mutex.withLock {
        check(!closed) { "run $runId is closed" }
        applyAll(mutations)
    }

    /** Ends the run's write path. Called once by the pipeline before the sink hears the run closed. */
    fun close() {
        closed = true
    }

    /** Every action recorded so far, in position order. */
    fun executed(): List<ExecutedAction> = ledger.executed()

    /** Changes the gate held so far. */
    fun held(): List<HeldProposal> = ledger.held()

    /** How many actions had their apply run, including errored and cancelled applies. */
    val appliedCount: Int
        get() = ledger.appliedCount

    /** How many held proposals exist. */
    val heldCount: Int
        get() = ledger.heldCount

    /**
     * A call the strategy already finished. A read is never reported. A preview is reported as a preview and a
     * rejection as an error, both with `applied` false and without asking the gate, because nothing can be written.
     */
    private suspend fun finished(step: ToolStep.Finished): DispatchResult {
        val result = step.result
        val kind = when (step.kind) {
            FinishedKind.PREVIEW -> ActionKind.PREVIEW
            FinishedKind.ERROR -> ActionKind.IS_ERROR
            else -> null
        }
        if (kind == null) return DispatchResult(result.contentForModel, result.isError, false, emptyList())
        val details = ActionDetails(step.toolName, result.appOutcomeToken, result.targetIds, step.context, false)
        val action = ledger.record(kind, applied = false, details = details)
        delivery.deliver(action)
        val isError = result.isError || kind == ActionKind.IS_ERROR
        return DispatchResult(result.contentForModel, isError, false, listOf(action))
    }

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
            ledger.record(ActionKind.HELD, applied = false, details = details).also { delivery.deliver(it) }
        }
        return DispatchResult(heldForConfirmationContent(), false, true, actions)
    }

    /** Applies the items one at a time; an item that fails never stops or undoes its siblings. */
    private suspend fun applyAll(mutations: List<PendingMutation>): DispatchResult {
        val changes = mutations.map { applyStep.run(it) }
        return DispatchResult(
            contentForModel = changes.joinToString(CONTENT_SEPARATOR) { it.content },
            isError = changes.any { it.action.kind == ActionKind.IS_ERROR },
            held = false,
            actions = changes.map { it.action },
        )
    }
}
