package io.github.ygaray.voiceactionengine.core

import io.github.ygaray.voiceactionengine.core.commit.ActionKind
import io.github.ygaray.voiceactionengine.core.commit.FinishedKind
import io.github.ygaray.voiceactionengine.core.commit.StepResult
import io.github.ygaray.voiceactionengine.core.commit.ToolStep
import io.github.ygaray.voiceactionengine.core.failure.BudgetBound
import io.github.ygaray.voiceactionengine.core.failure.FailureReason
import io.github.ygaray.voiceactionengine.core.pipeline.CommandOutcome
import io.github.ygaray.voiceactionengine.core.pipeline.TierPolicy
import io.github.ygaray.voiceactionengine.core.strategy.ToolSpec
import io.github.ygaray.voiceactionengine.core.telemetry.TraceCode
import io.github.ygaray.voiceactionengine.core.testing.FakeAiProvider
import io.github.ygaray.voiceactionengine.core.testing.FakeMutation
import io.github.ygaray.voiceactionengine.core.testing.NoNetworkGuard
import io.github.ygaray.voiceactionengine.core.testing.RecordingCommitSink
import io.github.ygaray.voiceactionengine.core.testing.ScriptedGate
import io.github.ygaray.voiceactionengine.core.testing.ScriptedToolExecutor
import io.github.ygaray.voiceactionengine.core.transcript.AssistantPart
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val TWO_ITERATIONS = 2

/** A terminal tool ends the agentic run at once: earlier calls ran, later calls are dropped, nothing is hidden. */
class AgenticLoopTerminalTest {

    private fun askArguments(): JsonObject = loopArguments("which one")

    private fun ask(id: String = "ask"): AssistantPart.ToolCall =
        callOf(id, ASK_TOOL, askArguments())

    private fun save(id: String = "save"): AssistantPart.ToolCall =
        callOf(id, SAVE_TOOL, loopArguments())

    private fun committed(): ToolStep = ToolStep.Mutation(FakeMutation(SAVE_TOOL, StepResult("saved")))

    private fun rejected(): ToolStep =
        ToolStep.Finished(SAVE_TOOL, FinishedKind.ERROR, StepResult("rejected", true))

    private fun snapshot() = loopSnapshotOf(writeTool(), readTool(), ToolSpec.clarification(ASK_TOOL))

    private fun turn(vararg calls: AssistantPart.ToolCall) =
        toolTurn(1, *calls)

    private suspend fun run(
        fake: FakeAiProvider,
        executor: ScriptedToolExecutor,
        gate: ScriptedGate = ScriptedGate.admitAll(),
        policy: TierPolicy = TierPolicy.DEFAULT,
    ): CommandOutcome =
        loopPipeline(
            listOf(agenticLoop(executor, snapshot())),
            fake,
            gate,
            RecordingCommitSink(),
            policy = policy,
        ).execute(CommandInput("add two things", "en", null))

    private fun assertEndedWithTheTerminalCall(outcome: CommandOutcome): CommandOutcome.Completed {
        assertTrue(outcome.toString(), outcome is CommandOutcome.Completed)
        outcome as CommandOutcome.Completed
        assertNull(outcome.reply)
        val terminal = checkNotNull(outcome.terminalCall)
        assertEquals(ASK_TOOL, terminal.toolName)
        assertEquals(askArguments(), terminal.arguments)
        return outcome
    }

    @Test
    fun aTerminalOnlyTurnCompletesWithTheTerminalCall() = runTest {
        NoNetworkGuard.during {
            val fake = FakeAiProvider(ProviderId.ANTHROPIC, turn(ask()))
            val executor = ScriptedToolExecutor.sequence(null)

            val outcome = run(fake, executor)

            val completed = assertEndedWithTheTerminalCall(outcome)
            assertFalse(completed.partial)
            assertEquals(0, executor.callCount)
            assertEquals(1, fake.callCount)
        }
    }

    @Test
    fun aTerminalCallAfterACommittingCallCarriesTheCommit() = runTest {
        NoNetworkGuard.during {
            val fake = FakeAiProvider(ProviderId.ANTHROPIC, turn(save(), ask()))
            val executor = ScriptedToolExecutor.sequence(null, committed())

            val outcome = run(fake, executor)

            val completed = assertEndedWithTheTerminalCall(outcome)
            assertFalse(completed.partial)
            assertEquals(listOf(SAVE_TOOL), outcome.commits.map { it.toolName })
            assertEquals(1, executor.callCount)
            assertEquals(1, fake.callCount)
        }
    }

    @Test
    fun aTerminalCallAlongsideAHeldCallCarriesTheHold() = runTest {
        NoNetworkGuard.during {
            val write = FakeMutation(SAVE_TOOL, StepResult("saved"))
            val fake = FakeAiProvider(ProviderId.ANTHROPIC, turn(save(), ask()))
            val executor = ScriptedToolExecutor.sequence(null, ToolStep.Mutation(write))

            val outcome = run(fake, executor, ScriptedGate.holdAll())

            assertEndedWithTheTerminalCall(outcome)
            assertEquals(1, outcome.held.size)
            assertEquals(0, write.applyCount)
            assertEquals(1, fake.callCount)
        }
    }

    @Test
    fun callsAfterATerminalCallAreDroppedTracedAndMarkPartial() = runTest {
        NoNetworkGuard.during {
            val fake = FakeAiProvider(ProviderId.ANTHROPIC, turn(ask(), save("s1"), save("s2")))
            val executor = ScriptedToolExecutor.sequence(null)

            val outcome = run(fake, executor)

            val completed = assertEndedWithTheTerminalCall(outcome)
            assertTrue(completed.partial)
            assertEquals(0, executor.callCount)
            assertEquals(1, outcome.trace.codes.count { it == TraceCode.EXTRA_TOOL_CALLS_DROPPED })
            assertTrue(outcome.executed.isEmpty())
        }
    }

    @Test
    fun aTerminalTurnOnTheFinalIterationCompletesInsteadOfBudgetExceeded() = runTest {
        NoNetworkGuard.during {
            val fake = FakeAiProvider(ProviderId.ANTHROPIC, turn(save()), turn(ask()))
            val executor = ScriptedToolExecutor.sequence(null, committed())
            val policy = TierPolicy { maxIterations = TWO_ITERATIONS }

            val outcome = run(fake, executor, policy = policy)

            assertEndedWithTheTerminalCall(outcome)
            assertEquals(listOf(SAVE_TOOL), outcome.commits.map { it.toolName })
            assertEquals(TWO_ITERATIONS, fake.callCount)
        }
    }

    @Test
    fun aFinalIterationTurnWithACallBeforeTheTerminalCallStillFailsTheGuard() = runTest {
        NoNetworkGuard.during {
            val fake = FakeAiProvider(ProviderId.ANTHROPIC, turn(save("s1")), turn(save("s2"), ask()))
            val executor = ScriptedToolExecutor.sequence(null, committed())
            val policy = TierPolicy { maxIterations = TWO_ITERATIONS }

            val outcome = run(fake, executor, policy = policy)

            assertTrue(outcome.toString(), outcome is CommandOutcome.Failed)
            assertEquals(
                FailureReason.BudgetExceeded(BudgetBound.ITERATIONS),
                (outcome as CommandOutcome.Failed).reason,
            )
            assertEquals(1, executor.callCount)
        }
    }

    @Test
    fun aStrikeAbortBeatsATerminalCallInTheSameTurn() = runTest {
        NoNetworkGuard.during {
            val fake = FakeAiProvider(ProviderId.ANTHROPIC, turn(save("s1")), turn(save("s2"), ask()))
            val executor = ScriptedToolExecutor.sequence(null, rejected(), rejected())

            val outcome = run(fake, executor)

            assertTrue(outcome.toString(), outcome is CommandOutcome.Failed)
            assertEquals(FailureReason.ToolFailure(), (outcome as CommandOutcome.Failed).reason)
            assertEquals(2, fake.callCount)
            assertEquals(2, executor.callCount)
        }
    }

    @Test
    fun aBudgetStopAfterACommitIsFailedNotPartial() = runTest {
        NoNetworkGuard.during {
            val fake = FakeAiProvider(ProviderId.ANTHROPIC, turn(save("s1")), turn(save("s2")))
            val executor = ScriptedToolExecutor.sequence(null, committed())
            val policy = TierPolicy { maxIterations = TWO_ITERATIONS }

            val outcome = run(fake, executor, policy = policy)

            assertTrue(outcome.toString(), outcome is CommandOutcome.Failed)
            assertEquals(
                FailureReason.BudgetExceeded(BudgetBound.ITERATIONS),
                (outcome as CommandOutcome.Failed).reason,
            )
            assertEquals(listOf(ActionKind.COMMITTED), outcome.executed.map { it.kind })
            assertEquals(1, outcome.commits.size)
        }
    }
}
