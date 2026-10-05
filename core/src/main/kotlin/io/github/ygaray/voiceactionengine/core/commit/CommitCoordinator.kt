package io.github.ygaray.voiceactionengine.core.commit

import io.github.ygaray.voiceactionengine.core.telemetry.RunRecorder
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

// The bytes a strategy receives for a held change; apps compare against this exact string.
private const val HELD_FOR_CONFIRMATION = """{"applied":false,"status":"held_for_confirmation"}"""
private const val CONTENT_SEPARATOR = "\n"

/** The bytes a strategy receives when a change is held. */
internal fun heldForConfirmationContent(): String = HELD_FOR_CONFIRMATION

// The bytes a strategy receives when the gate itself failed: a fixed internal-error notice, never the exception text.
private const val GATE_FAULT = """{"status":"error","reason":"internal_error"}"""

/** The bytes a strategy receives when the gate threw. */
internal fun gateFaultContent(): String = GATE_FAULT

/**
 * The engine's single write path: the gate decides, an admitted change is applied by [PendingMutation.apply], and the
 * sink observes each action before the next change starts. A strategy never reaches `apply` any other way.
 *
 * Submissions are serialized by a mutex so positions stay in dispatch order even if a strategy submits concurrently.
 *
 * A submission is refused, before anything is recorded, asked of the gate, applied or delivered, when the calling
 * coroutine has been cancelled (a strategy that swallowed its cancellation cannot keep writing) or the run is closed.
 * The closed flag is read again after the gate answers, so a change admitted while the run closed is not applied.
 * [close] does not wait for an in-flight apply: a coroutine a strategy leaked past the end of its tier may still finish
 * a write it had already started, and that late write is outside the contract and may be missing from the snapshot.
 */
internal class CommitCoordinator(
    private val runId: String,
    private val parentRunId: String?,
    gate: PreApplyGate,
    sink: CommitSink,
    private val recorder: RunRecorder,
) {
    private val mutex = Mutex()

    @Volatile
    private var closed = false
    private val ledger = ActionLedger(recorder)
    private val gateStep = GateStep(gate, recorder)
    private val delivery = ActionDelivery(runId, parentRunId, sink, recorder)
    private val applyStep = ApplyStep(ledger, delivery, recorder)

    /**
     * Applies, in order, whatever [step] asks for and the gate allows. Throws [IllegalStateException] once the run is
     * closed, before anything is recorded, asked of the gate, applied or delivered, and the caller's
     * `CancellationException` when the calling coroutine is cancelled. Every action the step records carries
     * [providerCallId], the provider's id for the tool call that produced the step (null when no provider call did).
     */
    suspend fun submit(step: ToolStep, providerCallId: String?): DispatchResult = mutex.withLock {
        admitCaller()
        when (step) {
            is ToolStep.Mutation -> submitMutation(step, providerCallId)
            is ToolStep.Finished -> finished(step, providerCallId)
        }
    }

    /**
     * Applies [mutations] one at a time without asking the gate, with the same recording and delivery as an admitted
     * change. Used to commit held changes later; throws [IllegalStateException] once the run is closed. Every action
     * carries [providerCallId], the id of the call that produced the held proposal.
     */
    suspend fun applyWithoutGate(mutations: List<PendingMutation>, providerCallId: String?): DispatchResult =
        mutex.withLock {
            admitCaller()
            applyAll(mutations, providerCallId)
        }

    /** Refuses a caller that was cancelled while it waited for the lock, or a run that is closed. */
    private suspend fun admitCaller() {
        currentCoroutineContext().ensureActive()
        check(!closed) { "run $runId is closed" }
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
    private suspend fun finished(step: ToolStep.Finished, providerCallId: String?): DispatchResult {
        val result = step.result
        val kind = when (step.kind) {
            FinishedKind.PREVIEW -> ActionKind.PREVIEW
            FinishedKind.ERROR -> ActionKind.IS_ERROR
            else -> null
        }
        if (kind == null) return DispatchResult(result.contentForModel, result.isError, false, emptyList())
        val details = ActionDetails(
            step.toolName, result.appOutcomeToken, result.targetIds, step.context, providerCallId, false,
        )
        val action = ledger.record(kind, applied = false, details = details)
        delivery.deliver(action)
        val isError = result.isError || kind == ActionKind.IS_ERROR
        return DispatchResult(result.contentForModel, isError, false, listOf(action))
    }

    private suspend fun submitMutation(step: ToolStep.Mutation, providerCallId: String?): DispatchResult {
        val answer = gateStep.decide(CommitProposal(runId, parentRunId, step.mutations))
        // The gate may have waited a long time for the user; the run or the caller may have ended meanwhile.
        admitCaller()
        return when (answer) {
            is GateAnswer.Faulted -> gateFault(step, providerCallId)
            is GateAnswer.Decided -> when (val decision = answer.decision) {
                is GateDecision.Admit -> applyAll(decision.amended ?: step.mutations, providerCallId)
                is GateDecision.Hold -> hold(step, decision, providerCallId)
            }
        }
    }

    /**
     * Nothing is written. Every mutation is reported as a held action carrying the gate's own token, one proposal
     * lists them with the gate's reason object, and the strategy gets the fixed not-applied notice. A held change is
     * not done and the model must not retry it.
     */
    private suspend fun hold(
        step: ToolStep.Mutation,
        decision: GateDecision.Hold,
        providerCallId: String?,
    ): DispatchResult {
        // Read every descriptor before anything is recorded, so a throwing getter cannot leave a half-reported hold.
        val facts = step.mutations.map { factsOf(it, recorder) }
        ledger.addHeld(
            HeldProposal(runId, parentRunId, step.mutations, decision.reason, decision.appOutcomeToken, providerCallId),
        )
        val actions = facts.map { fact ->
            val details = ActionDetails(
                fact.toolName, decision.appOutcomeToken, fact.targetIds, fact.context, providerCallId,
            )
            ledger.record(ActionKind.HELD, applied = false, details = details).also { delivery.deliver(it) }
        }
        return DispatchResult(heldForConfirmationContent(), false, true, actions)
    }

    /**
     * The gate itself failed, so nothing is written and nothing is held: a gate fault is an error, never a hold. Every
     * mutation is reported as an is_error action (not applied, no token, the `gate_error` code is already in the
     * trace), no [HeldProposal] exists to commit later, and the strategy gets the fixed internal-error content with
     * the error flag set, never the exception text. The model may retry, and a loop counts the failure as a strike
     * like any other error.
     */
    private suspend fun gateFault(step: ToolStep.Mutation, providerCallId: String?): DispatchResult {
        // Read every descriptor before anything is recorded, so a throwing getter cannot leave a half-reported fault.
        val facts = step.mutations.map { factsOf(it, recorder) }
        val actions = facts.map { fact ->
            val details = ActionDetails(fact.toolName, null, fact.targetIds, fact.context, providerCallId)
            ledger.record(ActionKind.IS_ERROR, applied = false, details = details).also { delivery.deliver(it) }
        }
        return DispatchResult(gateFaultContent(), true, false, actions)
    }

    /**
     * Applies the items one at a time; an item that fails never stops or undoes its siblings. A cancelled caller stops
     * the batch before the next item: an item that has not started leaves no record and the sink never hears of it,
     * while the items already applied stay recorded and reported.
     */
    private suspend fun applyAll(mutations: List<PendingMutation>, providerCallId: String?): DispatchResult {
        val changes = mutations.map {
            currentCoroutineContext().ensureActive()
            applyStep.run(it, providerCallId)
        }
        return DispatchResult(
            contentForModel = changes.joinToString(CONTENT_SEPARATOR) { it.content },
            isError = changes.any { it.action.kind == ActionKind.IS_ERROR },
            held = false,
            actions = changes.map { it.action },
        )
    }
}
