package io.github.ygaray.voiceactionengine.core

import io.github.ygaray.voiceactionengine.core.commit.ActionKind
import io.github.ygaray.voiceactionengine.core.commit.FinishedKind
import io.github.ygaray.voiceactionengine.core.commit.GateDecision
import io.github.ygaray.voiceactionengine.core.commit.RunTermination
import io.github.ygaray.voiceactionengine.core.commit.StepResult
import io.github.ygaray.voiceactionengine.core.commit.ToolStep
import io.github.ygaray.voiceactionengine.core.failure.BudgetBound
import io.github.ygaray.voiceactionengine.core.failure.FailureReason
import io.github.ygaray.voiceactionengine.core.pipeline.CommandOutcome
import io.github.ygaray.voiceactionengine.core.pipeline.TierPolicy
import io.github.ygaray.voiceactionengine.core.provider.ModelCapabilities
import io.github.ygaray.voiceactionengine.core.provider.ModelResult
import io.github.ygaray.voiceactionengine.core.strategy.ToolSpec
import io.github.ygaray.voiceactionengine.core.strategy.ToolingSnapshot
import io.github.ygaray.voiceactionengine.core.telemetry.TraceCode
import io.github.ygaray.voiceactionengine.core.testing.FakeAiProvider
import io.github.ygaray.voiceactionengine.core.testing.FakeMutation
import io.github.ygaray.voiceactionengine.core.testing.NoNetworkGuard
import io.github.ygaray.voiceactionengine.core.testing.ProviderStep
import io.github.ygaray.voiceactionengine.core.testing.RecordingCommitSink
import io.github.ygaray.voiceactionengine.core.testing.RecordingSink
import io.github.ygaray.voiceactionengine.core.testing.ScriptedGate
import io.github.ygaray.voiceactionengine.core.testing.ScriptedToolExecutor
import io.github.ygaray.voiceactionengine.core.transcript.AssistantPart
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.cancellation.CancellationException

private const val EXIT_LOG_TOOL = "log_entry"
private const val EXIT_REPLY = "done"
private const val EXIT_CEILING = 100L
private const val EXIT_FIRST_TURN_TOKENS = 60L
private const val EXIT_TWO_ITERATIONS = 2
private const val EXIT_FOUR_ITERATIONS = 4

/**
 * Every way an agentic run can end reports what it executed and committed so far, the sink heard each action before
 * the run closed, and the run closed exactly once.
 */
class AgenticLoopExitPathsTest {

    private class Rig {
        val log = RecordingSink<String>()
        val sink = RecordingCommitSink(log)
    }

    private fun snapshot(): ToolingSnapshot =
        loopSnapshotOf(writeTool(), writeTool(EXIT_LOG_TOOL), readTool(), ToolSpec.clarification(ASK_TOOL))

    private fun committed(name: String = SAVE_TOOL): ToolStep =
        ToolStep.Mutation(FakeMutation(name, StepResult("saved:$name")))

    private fun rejected(name: String = SAVE_TOOL): ToolStep =
        ToolStep.Finished(name, FinishedKind.ERROR, StepResult("rejected:$name", true))

    private fun previewed(name: String = SAVE_TOOL): ToolStep =
        ToolStep.Finished(name, FinishedKind.PREVIEW, StepResult("would save:$name"))

    private fun call(id: String, name: String = SAVE_TOOL): AssistantPart.ToolCall =
        callOf(id, name, loopArguments())

    private fun turn(tokens: Long, vararg calls: AssistantPart.ToolCall): ModelResult = toolTurn(tokens, *calls)

    private fun prose(): ModelResult = FakeAiProvider.reply(EXIT_REPLY, usage(1))

    private suspend fun run(
        rig: Rig,
        fake: FakeAiProvider,
        executor: ScriptedToolExecutor,
        gate: ScriptedGate = ScriptedGate.admitAll(),
        policy: TierPolicy = TierPolicy.DEFAULT,
    ): CommandOutcome =
        loopPipeline(listOf(agenticLoop(executor, snapshot())), fake, gate, rig.sink, policy = policy)
            .execute(CommandInput("add two things", "en", null))

    // Every sink action precedes the one close entry, and the sink heard exactly what the outcome lists.
    private fun assertActionsThenOneClose(rig: Rig, executedKinds: List<ActionKind>) {
        val entries = rig.log.events
        val closedAt = entries.indexOfFirst { it.startsWith("sink:closed:") }
        assertTrue("the close must be logged: $entries", closedAt >= 0)
        assertEquals("exactly one close: $entries", 1, entries.count { it.startsWith("sink:closed:") })
        assertEquals(1, rig.sink.closes.size)
        val after = entries.drop(closedAt + 1)
        assertTrue("an action followed the close: $entries", after.none { it.startsWith("sink:action") })
        assertEquals(executedKinds, rig.sink.actions.map { it.action.kind })
        assertEquals(executedKinds.size, entries.take(closedAt).count { it.startsWith("sink:action:") })
    }

    // The outcome and the termination the sink got list the same actions, commits and held proposals.
    private fun assertSameEffects(outcome: CommandOutcome, termination: RunTermination) {
        assertEquals(outcome.executed.map { it.position }, termination.executed.map { it.position })
        assertEquals(outcome.executed.map { it.kind }, termination.executed.map { it.kind })
        assertEquals(outcome.commits.map { it.position }, termination.commits.map { it.position })
        assertEquals(outcome.held.size, termination.held.size)
        assertEquals(outcome.runId, termination.runId)
    }

    private fun assertFailedWith(reason: FailureReason, outcome: CommandOutcome) {
        assertTrue(outcome.toString(), outcome is CommandOutcome.Failed)
        assertEquals(reason, (outcome as CommandOutcome.Failed).reason)
    }

    @Test
    fun doneReportsEveryActionAndClosesOnce() = runTest {
        NoNetworkGuard.during {
            val rig = Rig()
            val fake = FakeAiProvider(
                ProviderId.ANTHROPIC,
                turn(1, call("c1"), call("c2", FIND_TOOL)),
                turn(1, call("c3", EXIT_LOG_TOOL)),
                prose(),
            )
            val read = ToolStep.Finished(FIND_TOOL, FinishedKind.READ, StepResult("found"))
            val executor = ScriptedToolExecutor.sequence(null, committed(), read, committed(EXIT_LOG_TOOL))
            val gate = ScriptedGate.sequence(rig.log, GateDecision.Admit(), GateDecision.Hold())

            val outcome = run(rig, fake, executor, gate)

            assertTrue(outcome.toString(), outcome is CommandOutcome.Completed)
            assertEquals(EXIT_REPLY, (outcome as CommandOutcome.Completed).reply)
            assertEquals(listOf(ActionKind.COMMITTED, ActionKind.HELD), outcome.executed.map { it.kind })
            assertEquals(listOf(SAVE_TOOL), outcome.commits.map { it.toolName })
            assertEquals(listOf(EXIT_LOG_TOOL), outcome.held.single().mutations.map { it.toolName })
            assertActionsThenOneClose(rig, listOf(ActionKind.COMMITTED, ActionKind.HELD))
            val done = rig.sink.closes.single() as RunTermination.Done
            assertFalse(done.partial)
            assertSameEffects(outcome, done)
        }
    }

    @Test
    fun heldErroredAndPreviewedCallsKeepDistinctKinds() = runTest {
        NoNetworkGuard.during {
            val rig = Rig()
            val fake = FakeAiProvider(
                ProviderId.ANTHROPIC,
                turn(1, call("a")),
                turn(1, call("b")),
                turn(1, call("c")),
                turn(1, call("d")),
                prose(),
            )
            val executor = ScriptedToolExecutor.sequence(null, committed(), committed(), previewed(), rejected())
            val gate = ScriptedGate.sequence(rig.log, GateDecision.Admit(), GateDecision.Hold())

            val outcome = run(rig, fake, executor, gate)

            val expected = listOf(ActionKind.COMMITTED, ActionKind.HELD, ActionKind.PREVIEW, ActionKind.IS_ERROR)
            assertTrue(outcome.toString(), outcome is CommandOutcome.Completed)
            assertEquals(expected, outcome.executed.map { it.kind })
            val (heldAction, previewAction, errorAction) = outcome.executed.drop(1)
            assertEquals(3, setOf(heldAction.kind, previewAction.kind, errorAction.kind).size)
            assertFalse(heldAction.applied)
            assertFalse(previewAction.applied)
            assertFalse(errorAction.applied)
            assertFalse(previewAction.mutating)
            assertFalse(errorAction.mutating)
            assertNotEquals(previewAction.kind, errorAction.kind)
            assertEquals(1, outcome.held.size)
            assertEquals(1, outcome.commits.size)
            assertActionsThenOneClose(rig, expected)
            val done = rig.sink.closes.single() as RunTermination.Done
            assertEquals(expected, done.executed.map { it.kind })
            assertSameEffects(outcome, done)
        }
    }

    @Test
    fun anIterationBudgetStopCarriesCommitsHeldErroredAndPreviews() = runTest {
        NoNetworkGuard.during {
            val rig = Rig()
            val fake = FakeAiProvider(
                ProviderId.ANTHROPIC,
                turn(1, call("a")),
                turn(1, call("b")),
                turn(1, call("c"), call("d")),
                turn(1, call("e")),
            )
            val executor = ScriptedToolExecutor.sequence(null, committed(), committed(), previewed(), rejected())
            val gate = ScriptedGate.sequence(rig.log, GateDecision.Admit(), GateDecision.Hold())
            val policy = TierPolicy { maxIterations = EXIT_FOUR_ITERATIONS }

            val outcome = run(rig, fake, executor, gate, policy)

            val expected = listOf(ActionKind.COMMITTED, ActionKind.HELD, ActionKind.PREVIEW, ActionKind.IS_ERROR)
            assertFailedWith(FailureReason.BudgetExceeded(BudgetBound.ITERATIONS), outcome)
            assertEquals(EXIT_FOUR_ITERATIONS, fake.callCount)
            assertEquals(expected, outcome.executed.map { it.kind })
            assertActionsThenOneClose(rig, expected)
            val failed = rig.sink.closes.single() as RunTermination.Failed
            assertEquals(FailureReason.BudgetExceeded(BudgetBound.ITERATIONS), failed.reason)
            assertSameEffects(outcome, failed)
        }
    }

    @Test
    fun aTokenBudgetStopCarriesTheCommitsSoFar() = runTest {
        NoNetworkGuard.during {
            val rig = Rig()
            val fake = FakeAiProvider(
                ProviderId.ANTHROPIC,
                turn(EXIT_FIRST_TURN_TOKENS, call("a")),
                turn(EXIT_FIRST_TURN_TOKENS, call("b")),
            )
            val policy = TierPolicy { tokenCeiling = EXIT_CEILING }

            val outcome = run(rig, fake, ScriptedToolExecutor.sequence(null, committed()), policy = policy)

            assertFailedWith(FailureReason.BudgetExceeded(BudgetBound.TOKENS), outcome)
            assertEquals(listOf(SAVE_TOOL), outcome.commits.map { it.toolName })
            assertActionsThenOneClose(rig, listOf(ActionKind.COMMITTED))
            val failed = rig.sink.closes.single() as RunTermination.Failed
            assertSameEffects(outcome, failed)
            assertSameTurnTieReportsTokens()
        }
    }

    // The signed-off precedence: the ceiling and the iteration cap trip on the same final turn and TOKENS wins.
    private suspend fun assertSameTurnTieReportsTokens() {
        val rig = Rig()
        val fake = FakeAiProvider(
            ProviderId.ANTHROPIC,
            turn(EXIT_FIRST_TURN_TOKENS, call("a")),
            turn(EXIT_CEILING - EXIT_FIRST_TURN_TOKENS + 1, call("b", EXIT_LOG_TOOL)),
        )
        val executor = ScriptedToolExecutor.sequence(null, committed(), committed(EXIT_LOG_TOOL))
        val policy = TierPolicy {
            tokenCeiling = EXIT_CEILING
            maxIterations = EXIT_TWO_ITERATIONS
        }

        val outcome = run(rig, fake, executor, policy = policy)

        assertFailedWith(FailureReason.BudgetExceeded(BudgetBound.TOKENS), outcome)
        assertEquals(listOf(SAVE_TOOL), outcome.commits.map { it.toolName })
        assertEquals(1, executor.callCount)
        assertActionsThenOneClose(rig, listOf(ActionKind.COMMITTED))
    }

    @Test
    fun aStrikeAbortCarriesEveryExecutedAction() = runTest {
        NoNetworkGuard.during {
            val rig = Rig()
            val fake = FakeAiProvider(
                ProviderId.ANTHROPIC,
                turn(1, call("a")),
                turn(1, call("b"), call("c", EXIT_LOG_TOOL)),
            )
            val executor = ScriptedToolExecutor.sequence(null, rejected(), rejected(), committed(EXIT_LOG_TOOL))

            val outcome = run(rig, fake, executor)

            val expected = listOf(ActionKind.IS_ERROR, ActionKind.IS_ERROR, ActionKind.COMMITTED)
            assertFailedWith(FailureReason.ToolFailure(), outcome)
            assertEquals(expected, outcome.executed.map { it.kind })
            assertEquals(listOf(EXIT_LOG_TOOL), outcome.commits.map { it.toolName })
            assertActionsThenOneClose(rig, expected)
            assertSameEffects(outcome, rig.sink.closes.single())
        }
    }

    @Test
    fun aProviderFailureAfterACommitStillReportsTheCommit() = runTest {
        NoNetworkGuard.during {
            val rig = Rig()
            val fake = FakeAiProvider(
                ProviderId.ANTHROPIC,
                turn(1, call("a")),
                ModelResult.Failure(FailureReason.Network()),
            )

            val outcome = run(rig, fake, ScriptedToolExecutor.sequence(null, committed()))

            assertFailedWith(FailureReason.Network(), outcome)
            assertEquals(listOf(SAVE_TOOL), outcome.commits.map { it.toolName })
            assertActionsThenOneClose(rig, listOf(ActionKind.COMMITTED))
            assertSameEffects(outcome, rig.sink.closes.single())
        }
    }

    @Test
    fun aMalformedTurnAfterACommitStillReportsTheCommit() = runTest {
        NoNetworkGuard.during {
            val rig = Rig()
            val fake = FakeAiProvider(
                ProviderId.ANTHROPIC,
                turn(1, call("a")),
                turn(1, call("same"), call("same", EXIT_LOG_TOOL)),
            )

            val outcome = run(rig, fake, ScriptedToolExecutor.sequence(null, committed()))

            assertFailedWith(FailureReason.MalformedResponse(), outcome)
            assertEquals(listOf(SAVE_TOOL), outcome.commits.map { it.toolName })
            assertActionsThenOneClose(rig, listOf(ActionKind.COMMITTED))
            assertSameEffects(outcome, rig.sink.closes.single())
        }
    }

    @Test
    fun aTerminalExitReportsTheCommitsBeforeIt() = runTest {
        NoNetworkGuard.during {
            val rig = Rig()
            val fake = FakeAiProvider(ProviderId.ANTHROPIC, turn(1, call("a"), call("ask", ASK_TOOL)))

            val outcome = run(rig, fake, ScriptedToolExecutor.sequence(null, committed()))

            assertTrue(outcome.toString(), outcome is CommandOutcome.Completed)
            assertEquals(ASK_TOOL, checkNotNull((outcome as CommandOutcome.Completed).terminalCall).toolName)
            assertEquals(listOf(SAVE_TOOL), outcome.commits.map { it.toolName })
            assertActionsThenOneClose(rig, listOf(ActionKind.COMMITTED))
            val done = rig.sink.closes.single() as RunTermination.Done
            assertSameEffects(outcome, done)
        }
    }

    // Starts [block] as a job, waits until it signals that it is suspended, cancels it and returns once it ended.
    private suspend fun CoroutineScope.cancelOnceSuspended(
        suspended: CompletableDeferred<Unit>,
        block: suspend () -> CommandOutcome,
    ) {
        val job: Deferred<CommandOutcome> = async { block() }
        suspended.await()
        job.cancel()
        try {
            job.await()
            fail("a cancelled run must not return an outcome")
        } catch (expected: CancellationException) {
            assertTrue(expected.toString(), job.isCancelled)
        }
    }

    @Test
    fun aCancelDuringTheProviderCallPropagatesAndTheRunClosesCancelled() = runTest {
        NoNetworkGuard.during {
            val rig = Rig()
            val suspended = CompletableDeferred<Unit>()
            val first: ProviderStep = { turn(1, call("a")) }
            val hang: ProviderStep = { suspended.complete(Unit); awaitCancellation() }
            val fake = FakeAiProvider(ProviderId.ANTHROPIC, ModelCapabilities.UNKNOWN, first, hang)
            val executor = ScriptedToolExecutor.sequence(null, committed())

            cancelOnceSuspended(suspended) { run(rig, fake, executor) }

            val cancelled = rig.sink.closes.single() as RunTermination.Cancelled
            assertEquals(listOf(SAVE_TOOL), cancelled.commits.map { it.toolName })
            assertEquals(listOf(ActionKind.COMMITTED), cancelled.executed.map { it.kind })
            assertActionsThenOneClose(rig, listOf(ActionKind.COMMITTED))
        }
    }

    @Test
    fun aCancelWhileTheGateIsSuspendedLeavesNoRecord() = runTest {
        NoNetworkGuard.during {
            val rig = Rig()
            val suspended = CompletableDeferred<Unit>()
            val asked = AtomicInteger()
            val gate = ScriptedGate(rig.log) {
                if (asked.incrementAndGet() == 1) {
                    GateDecision.Admit()
                } else {
                    suspended.complete(Unit)
                    awaitCancellation()
                }
            }
            val second = FakeMutation(SAVE_TOOL, StepResult("never"))
            val fake = FakeAiProvider(ProviderId.ANTHROPIC, turn(1, call("a")), turn(1, call("b")))
            val executor = ScriptedToolExecutor.sequence(null, committed(), ToolStep.Mutation(second))

            cancelOnceSuspended(suspended) { run(rig, fake, executor, gate) }

            val cancelled = rig.sink.closes.single() as RunTermination.Cancelled
            assertEquals(listOf(ActionKind.COMMITTED), cancelled.executed.map { it.kind })
            assertTrue(cancelled.held.isEmpty())
            assertEquals(0, second.applyCount)
            assertEquals(2, gate.calls)
            assertActionsThenOneClose(rig, listOf(ActionKind.COMMITTED))
        }
    }

    @Test
    fun aCancelDuringApplyRecordsAnAppliedErrorAndPropagates() = runTest {
        NoNetworkGuard.during {
            val rig = Rig()
            val suspended = CompletableDeferred<Unit>()
            val mutation = FakeMutation(SAVE_TOOL, { suspended.complete(Unit); awaitCancellation() })
            val fake = FakeAiProvider(ProviderId.ANTHROPIC, turn(1, call("a")))
            val executor = ScriptedToolExecutor.sequence(null, ToolStep.Mutation(mutation))

            cancelOnceSuspended(suspended) { run(rig, fake, executor) }

            val cancelled = rig.sink.closes.single() as RunTermination.Cancelled
            val action = cancelled.executed.single()
            assertEquals(ActionKind.IS_ERROR, action.kind)
            assertTrue(action.applied)
            assertTrue(cancelled.trace.codes.contains(TraceCode.APPLY_CANCELLED))
            assertEquals(1, mutation.applyCount)
            assertActionsThenOneClose(rig, listOf(ActionKind.IS_ERROR))
        }
    }
}
