package io.github.ygaray.voiceactionengine.core.commit

import io.github.ygaray.voiceactionengine.core.internal.guarded
import io.github.ygaray.voiceactionengine.core.internal.guardedUncancellable
import io.github.ygaray.voiceactionengine.core.telemetry.RunRecorder
import io.github.ygaray.voiceactionengine.core.telemetry.TraceCode
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

// What a strategy receives when an apply threw: a fixed notice, never the exception's text.
private const val APPLY_ERROR_CONTENT = """{"status":"error"}"""

/** The bytes a strategy receives when applying a change threw. */
internal fun applyErrorContent(): String = APPLY_ERROR_CONTENT

// The name reported when the app's own descriptor getters threw; a fixed word, never anything the app produced.
private const val UNKNOWN_TOOL = "unknown"

/**
 * What a change says about itself, read once and copied, so one misbehaving getter can only cost its own descriptors.
 * The values are read before the change is applied, which is also the moment [PendingMutation.context] documents.
 */
internal class MutationFacts(
    val toolName: String,
    val targetIds: Map<String, String>,
    val context: Any?,
)

/**
 * Reads [mutation]'s descriptors under a guard. A getter that throws (they are app code, and may compute lazily) gives
 * fixed placeholders and the `apply_error` code; it never stops the change or its record. The getters cannot
 * suspend, so a cancellation they throw is foreign and is a fault too.
 */
internal suspend fun factsOf(mutation: PendingMutation, recorder: RunRecorder): MutationFacts =
    guardedUncancellable(onFault = {
        recorder.recordCode(TraceCode.APPLY_ERROR)
        MutationFacts(UNKNOWN_TOOL, emptyMap(), null)
    }) { MutationFacts(mutation.toolName, mutation.targetIds.toMap(), mutation.context) }

/** One applied change: the action that was recorded and what the model is told about it. */
internal class AppliedChange(
    val action: ExecutedAction,
    val content: String,
)

/**
 * Hands recorded actions to the app's sink. Delivery runs to completion even when the run is being cancelled, so the
 * undo journal hears about every write. A sink that throws is recorded as a trace code; nothing is retried.
 */
internal class ActionDelivery(
    private val runId: String,
    private val parentRunId: String?,
    private val sink: CommitSink,
    private val recorder: RunRecorder,
) {
    /** Tells the sink about [action] and waits for it to finish. */
    suspend fun deliver(action: ExecutedAction) {
        withContext(NonCancellable) {
            guardedUncancellable(onFault = { recorder.recordCode(TraceCode.SINK_ERROR) }) {
                sink.onAction(ActionEvent(runId, parentRunId, action))
            }
        }
    }
}

/**
 * Applies one admitted change and reports exactly one action for it.
 *
 * - Success: a committed action, or an is_error action when the app's result says it failed; both with `applied` true.
 * - A throw from apply: an is_error action with `applied` true (it may have written), no token, and the fixed error
 *   notice for the model, plus the `apply_error` code.
 * - Cancellation while applying: the same is_error action is recorded and delivered before the cancellation
 *   continues, with the `apply_cancelled` code.
 *
 * A failing sink never causes a second apply.
 */
internal class ApplyStep(
    private val ledger: ActionLedger,
    private val delivery: ActionDelivery,
    private val recorder: RunRecorder,
) {
    /** Applies [mutation], records and delivers its action, and returns both. */
    suspend fun run(mutation: PendingMutation): AppliedChange {
        val facts = factsOf(mutation, recorder)
        val result = try {
            attempt(mutation)
        } catch (e: CancellationException) {
            journalCancelled(facts)
            throw e
        }
        val change = recordOutcome(facts, result)
        delivery.deliver(change.action)
        return change
    }

    private suspend fun attempt(mutation: PendingMutation): StepResult? = guarded(onFault = {
        recorder.recordCode(TraceCode.APPLY_ERROR)
        null
    }) { mutation.apply() }

    private suspend fun recordOutcome(facts: MutationFacts, result: StepResult?): AppliedChange {
        val failed = result == null || result.isError
        val details = ActionDetails(
            toolName = facts.toolName,
            appOutcomeToken = result?.appOutcomeToken,
            targetIds = facts.targetIds + (result?.targetIds ?: emptyMap()),
            context = facts.context,
        )
        val kind = if (failed) ActionKind.IS_ERROR else ActionKind.COMMITTED
        val action = ledger.record(kind, applied = true, details = details)
        return AppliedChange(action, result?.contentForModel ?: APPLY_ERROR_CONTENT)
    }

    private suspend fun journalCancelled(facts: MutationFacts) {
        withContext(NonCancellable) {
            recorder.recordCode(TraceCode.APPLY_CANCELLED)
            val details = ActionDetails(facts.toolName, null, facts.targetIds, facts.context)
            delivery.deliver(ledger.record(ActionKind.IS_ERROR, applied = true, details = details))
        }
    }
}
