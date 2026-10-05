package io.github.ygaray.voiceactionengine.core.pipeline

import io.github.ygaray.voiceactionengine.core.commit.CommitCoordinator
import io.github.ygaray.voiceactionengine.core.commit.CommitSink
import io.github.ygaray.voiceactionengine.core.commit.HeldProposal
import io.github.ygaray.voiceactionengine.core.commit.PendingMutation
import io.github.ygaray.voiceactionengine.core.commit.PreApplyGate
import io.github.ygaray.voiceactionengine.core.failure.FailureReason
import io.github.ygaray.voiceactionengine.core.internal.EngineFault
import io.github.ygaray.voiceactionengine.core.internal.toReason
import io.github.ygaray.voiceactionengine.core.internal.guarded
import io.github.ygaray.voiceactionengine.core.telemetry.PipelineEventListener
import io.github.ygaray.voiceactionengine.core.telemetry.RunRecorder
import io.github.ygaray.voiceactionengine.core.telemetry.TraceCode
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

private const val CANCELLED_CODE = "commit_held_cancelled"
private const val ERROR_CLASS = "Error"

/**
 * Commits held changes later, as a new run linked to the run that held them. The gate is never asked: the user already
 * decided. Exactly one caller per proposal gets to apply; every other caller waits for and returns that caller's
 * outcome, so a double tap can never write twice.
 */
internal class HeldCommit(
    private val gate: PreApplyGate,
    private val sink: CommitSink,
    private val clock: () -> Long,
    private val runIds: () -> String,
    private val listener: PipelineEventListener?,
) {
    /**
     * Commits [mutations] for [held] if nobody has yet, otherwise returns what the first caller got. An empty list is
     * refused before the proposal is claimed, so a bad argument never uses the proposal up; the list is copied so the
     * caller cannot change it after the call.
     */
    suspend fun resolve(held: HeldProposal, changes: List<PendingMutation>): CommandOutcome {
        require(changes.isNotEmpty()) { "commitHeld: at least one mutation is required" }
        val mutations = changes.toList()
        if (!held.claimed.compareAndSet(false, true)) return held.result.await()
        // The claim is taken, so whatever happens next the proposal's result must be completed or every other caller
        // would wait for ever.
        return try {
            var failure: EngineFault? = null
            val child = guarded<ChildRun?>(onFault = { failure = it; null }) { open(held) }
            if (child != null) run(child, mutations) else unstarted(held, failure?.errorClass)
        } finally {
            if (!held.result.isCompleted) held.result.complete(unstartedFailure(held.runId, null, 0, ERROR_CLASS))
        }
    }

    /** The outcome when the child run could not begin (a throwing id maker or clock); the sink is not told. */
    private fun unstarted(held: HeldProposal, errorClass: String?): CommandOutcome =
        unstartedFailure(held.runId, null, 0, errorClass ?: ERROR_CLASS).also { held.result.complete(it) }

    private fun open(held: HeldProposal): ChildRun {
        val runId = runIds()
        val recorder = RunRecorder(runId, held.runId, null, 0, clock, listener)
        return ChildRun(runId, held, CommitCoordinator(runId, held.runId, gate, sink, recorder), recorder)
    }

    private suspend fun run(child: ChildRun, mutations: List<PendingMutation>): CommandOutcome {
        val recorder = child.recorder
        var outcome: CommandOutcome? = null
        var cancelled = false
        try {
            recorder.commandStarted()
            outcome = guarded(onFault = { child.failed(it) }) {
                child.coordinator.applyWithoutGate(mutations, child.held.providerCallId)
                CommandOutcome.Completed(child.effects(), null, null, partial = false)
            }
            return outcome
        } catch (e: CancellationException) {
            cancelled = true
            throw e
        } finally {
            child.coordinator.close()
            settle(child, outcome, cancelled)
        }
    }

    /** Closes the child run once, then hands the outcome to everyone waiting on this proposal. */
    private suspend fun settle(child: ChildRun, outcome: CommandOutcome?, cancelled: Boolean) {
        withContext(NonCancellable) {
            if (cancelled) child.recorder.recordCode(TraceCode.COMMIT_HELD_CANCELLED)
            val effects = child.effects()
            try {
                closeRun(sink, child.runId, child.recorder, terminationOf(outcome, cancelled, effects))
            } finally {
                child.held.result.complete(outcome ?: fallback(effects, cancelled))
            }
        }
    }

    /** What later callers see when the first caller produced no outcome: it was cancelled, or an error escaped. */
    private fun fallback(effects: RunEffects, cancelled: Boolean): CommandOutcome {
        val reason = if (cancelled) FailureReason.Other(CANCELLED_CODE) else FailureReason.Unexpected(ERROR_CLASS)
        return CommandOutcome.Failed(effects, reason, null)
    }
}

/** One commit-held run: its identity, the proposal it resolves, and the write path and trace it records into. */
private class ChildRun(
    val runId: String,
    val held: HeldProposal,
    val coordinator: CommitCoordinator,
    val recorder: RunRecorder,
) {
    /** What the child has done so far; its parent is the run that held the changes. */
    fun effects(): RunEffects = snapshotEffects(runId, held.runId, coordinator, recorder)

    /** A fault that reached the top of the child run: a leaked timeout is a timeout, anything else is unexpected. */
    fun failed(fault: EngineFault): CommandOutcome {
        val reason = fault.toReason()
        return CommandOutcome.Failed(effects(), reason, null)
    }
}
