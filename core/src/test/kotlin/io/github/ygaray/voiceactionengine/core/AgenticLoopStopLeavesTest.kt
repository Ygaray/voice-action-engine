package io.github.ygaray.voiceactionengine.core

import io.github.ygaray.voiceactionengine.core.commit.FinishedKind
import io.github.ygaray.voiceactionengine.core.commit.StepResult
import io.github.ygaray.voiceactionengine.core.commit.ToolStep
import io.github.ygaray.voiceactionengine.core.failure.FailureDetails
import io.github.ygaray.voiceactionengine.core.failure.FailureReason
import io.github.ygaray.voiceactionengine.core.pipeline.CommandOutcome
import io.github.ygaray.voiceactionengine.core.provider.ModelResult
import io.github.ygaray.voiceactionengine.core.strategy.agentic.AgenticLoopStrategy
import io.github.ygaray.voiceactionengine.core.testing.FakeAiProvider
import io.github.ygaray.voiceactionengine.core.testing.FakeMutation
import io.github.ygaray.voiceactionengine.core.testing.NoNetworkGuard
import io.github.ygaray.voiceactionengine.core.testing.RecordingCommitSink
import io.github.ygaray.voiceactionengine.core.testing.ScriptedGate
import io.github.ygaray.voiceactionengine.core.testing.ScriptedToolExecutor
import io.github.ygaray.voiceactionengine.core.transcript.AssistantPart
import io.github.ygaray.voiceactionengine.core.transcript.StopReason
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val FINAL_REPLY = "done"
private const val LOG_TOOL = "log_entry"
private const val LEAF_MAX_TOKENS = 4
private const val LEAF_REFUSAL = 5
private const val UNAVAILABLE_STATUS = 503
private const val RATE_LIMIT_STATUS = 429

/** Every way a loop run can fail comes back as its own named leaf, and no failure hides an earlier commit. */
class AgenticLoopStopLeavesTest {

    // One failing answer per leaf the model or the provider can produce, each paired with the leaf it must become.
    private class Leaf(val answer: ModelResult, val expected: FailureReason)

    private fun saveCall(): AssistantPart.ToolCall = callOf("c-save", SAVE_TOOL, loopArguments())

    private fun text(): AssistantPart = AssistantPart.Text("words")

    private fun leaves(): List<Leaf> =
        listOf(
            Leaf(
                ModelResult.Failure(FailureReason.HttpError(), FailureDetails(UNAVAILABLE_STATUS, "overloaded", null)),
                FailureReason.HttpError(),
            ),
            Leaf(ModelResult.Failure(FailureReason.Network()), FailureReason.Network()),
            Leaf(ModelResult.Failure(FailureReason.MalformedResponse()), FailureReason.MalformedResponse()),
            Leaf(
                ModelResult.Failure(FailureReason.RateLimited(), FailureDetails(RATE_LIMIT_STATUS, null, null)),
                FailureReason.RateLimited(),
            ),
            Leaf(answerOf(StopReason.MAX_TOKENS, text(), saveCall()), FailureReason.MaxTokens()),
            Leaf(answerOf(StopReason.REFUSAL, text()), FailureReason.Refusal()),
            Leaf(answerOf(StopReason.PAUSE_TURN, text()), FailureReason.PauseTurn()),
            Leaf(answerOf(StopReason.CONTEXT_WINDOW_EXCEEDED, text()), FailureReason.ContextWindowExceeded()),
            Leaf(answerOf(StopReason.OTHER, text()), FailureReason.UnknownStop()),
        )

    private fun snapshot() = loopSnapshotOf(writeTool(), readTool())

    private fun committed(): ToolStep = ToolStep.Mutation(FakeMutation(SAVE_TOOL, StepResult("saved:$SAVE_TOOL")))

    private fun rejected(): ToolStep =
        ToolStep.Finished(SAVE_TOOL, FinishedKind.ERROR, StepResult("rejected:$SAVE_TOOL", true))

    private suspend fun run(fake: FakeAiProvider, executor: ScriptedToolExecutor): CommandOutcome {
        val strategy: AgenticLoopStrategy = agenticLoop(executor, snapshot())
        return loopPipeline(listOf(strategy), fake, ScriptedGate.admitAll(), RecordingCommitSink())
            .execute(CommandInput("add two things", "en", null))
    }

    private suspend fun runOne(answer: ModelResult): Pair<CommandOutcome, ScriptedToolExecutor> {
        val executor = ScriptedToolExecutor.sequence(null)
        return run(FakeAiProvider(ProviderId.ANTHROPIC, answer), executor) to executor
    }

    private suspend fun assertLeaf(answer: ModelResult, expected: FailureReason): CommandOutcome.Failed {
        val (outcome, executor) = runOne(answer)
        assertTrue(outcome.toString(), outcome is CommandOutcome.Failed)
        outcome as CommandOutcome.Failed
        assertEquals(expected, outcome.reason)
        assertEquals(0, executor.callCount)
        return outcome
    }

    @Test
    fun anHttpErrorKeepsItsStatus() = runTest {
        NoNetworkGuard.during {
            val details = FailureDetails(UNAVAILABLE_STATUS, "overloaded", null)

            val outcome = assertLeaf(ModelResult.Failure(FailureReason.HttpError(), details), FailureReason.HttpError())

            assertEquals(UNAVAILABLE_STATUS, outcome.details?.httpStatus)
        }
    }

    @Test
    fun aNetworkFailureIsNetwork() = runTest {
        NoNetworkGuard.during { assertLeaf(ModelResult.Failure(FailureReason.Network()), FailureReason.Network()) }
    }

    @Test
    fun aMalformedProviderResultIsMalformedResponse() = runTest {
        NoNetworkGuard.during {
            assertLeaf(ModelResult.Failure(FailureReason.MalformedResponse()), FailureReason.MalformedResponse())
        }
    }

    @Test
    fun otherProviderFailuresPassThroughWithTheirDetails() = runTest {
        NoNetworkGuard.during {
            val details = FailureDetails(RATE_LIMIT_STATUS, "rate_limit", "req-1")

            val failure = ModelResult.Failure(FailureReason.RateLimited(), details)

            val outcome = assertLeaf(failure, FailureReason.RateLimited())

            assertEquals(details, outcome.details)
            assertEquals(RATE_LIMIT_STATUS, outcome.details?.httpStatus)
        }
    }

    @Test
    fun aMaxTokensStopIsMaxTokensEvenWithToolCalls() = runTest {
        NoNetworkGuard.during {
            assertLeaf(answerOf(StopReason.MAX_TOKENS, text(), saveCall()), FailureReason.MaxTokens())
            assertLeaf(answerOf(StopReason.MAX_TOKENS, text()), FailureReason.MaxTokens())
        }
    }

    @Test
    fun aRefusalStopIsRefusal() = runTest {
        NoNetworkGuard.during {
            assertLeaf(answerOf(StopReason.REFUSAL, text()), FailureReason.Refusal())
            assertLeaf(answerOf(StopReason.REFUSAL, text(), saveCall()), FailureReason.Refusal())
        }
    }

    @Test
    fun aRefusalFailureIsRefusal() = runTest {
        NoNetworkGuard.during { assertLeaf(ModelResult.Failure(FailureReason.Refusal()), FailureReason.Refusal()) }
    }

    @Test
    fun aPauseTurnStopIsPauseTurn() = runTest {
        NoNetworkGuard.during { assertLeaf(answerOf(StopReason.PAUSE_TURN, text()), FailureReason.PauseTurn()) }
    }

    @Test
    fun aContextWindowStopIsContextWindowExceeded() = runTest {
        NoNetworkGuard.during {
            assertLeaf(
                answerOf(StopReason.CONTEXT_WINDOW_EXCEEDED, text()),
                FailureReason.ContextWindowExceeded(),
            )
        }
    }

    @Test
    fun anUnknownStopWithoutCallsIsUnknownStop() = runTest {
        NoNetworkGuard.during { assertLeaf(answerOf(StopReason.OTHER, text()), FailureReason.UnknownStop()) }
    }

    @Test
    fun aRepeatedToolErrorIsToolFailure() = runTest {
        NoNetworkGuard.during {
            val fake = FakeAiProvider(
                ProviderId.ANTHROPIC,
                toolTurn(1, saveCall()),
                toolTurn(1, callOf("c-again", SAVE_TOOL, loopArguments())),
            )
            val executor = ScriptedToolExecutor.sequence(null, rejected(), rejected())

            val outcome = run(fake, executor)

            assertTrue(outcome.toString(), outcome is CommandOutcome.Failed)
            assertEquals(FailureReason.ToolFailure(), (outcome as CommandOutcome.Failed).reason)
            assertEquals(2, executor.callCount)
        }
    }

    private suspend fun assertToolTurnCompletes(stop: StopReason) {
        val fake = FakeAiProvider(
            ProviderId.ANTHROPIC,
            answerOf(stop, saveCall()),
            FakeAiProvider.reply(FINAL_REPLY, usage(1)),
        )
        val executor = ScriptedToolExecutor.sequence(null, committed())

        val outcome = run(fake, executor)

        assertTrue(outcome.toString(), outcome is CommandOutcome.Completed)
        assertEquals(FINAL_REPLY, (outcome as CommandOutcome.Completed).reply)
        assertEquals(1, executor.callCount)
        assertEquals(listOf(SAVE_TOOL), outcome.commits.map { it.toolName })
    }

    @Test
    fun anEndTurnAnswerWithToolCallsIsAToolTurn() = runTest {
        NoNetworkGuard.during { assertToolTurnCompletes(StopReason.END_TURN) }
    }

    @Test
    fun anOtherStopWithToolCallsIsAToolTurn() = runTest {
        NoNetworkGuard.during { assertToolTurnCompletes(StopReason.OTHER) }
    }

    private suspend fun assertLeafAfterACommit(leaf: Leaf) {
        val fake = FakeAiProvider(ProviderId.ANTHROPIC, toolTurn(1, saveCall()), leaf.answer)
        val executor = ScriptedToolExecutor.sequence(null, committed())

        val outcome = run(fake, executor)

        assertTrue(outcome.toString(), outcome is CommandOutcome.Failed)
        assertEquals(leaf.expected, (outcome as CommandOutcome.Failed).reason)
        assertEquals(outcome.toString(), listOf(SAVE_TOOL), outcome.commits.map { it.toolName })
        assertEquals(1, executor.callCount)
    }

    @Test
    fun everyLeafAfterACommitStillReportsTheCommit() = runTest {
        NoNetworkGuard.during {
            leaves().forEach { assertLeafAfterACommit(it) }

            val fake = FakeAiProvider(
                ProviderId.ANTHROPIC,
                toolTurn(1, callOf("c-ok", LOG_TOOL, loopArguments())),
                toolTurn(1, saveCall()),
                toolTurn(1, callOf("c-again", SAVE_TOOL, loopArguments())),
            )
            val logged = ToolStep.Mutation(FakeMutation(LOG_TOOL, StepResult("saved:$LOG_TOOL")))
            val executor = ScriptedToolExecutor.sequence(null, logged, rejected(), rejected())
            val strategy = agenticLoop(executor, loopSnapshotOf(writeTool(), writeTool(LOG_TOOL)))

            val outcome = loopPipeline(listOf(strategy), fake, ScriptedGate.admitAll(), RecordingCommitSink())
                .execute(CommandInput("add two things", "en", null))

            assertTrue(outcome.toString(), outcome is CommandOutcome.Failed)
            assertEquals(FailureReason.ToolFailure(), (outcome as CommandOutcome.Failed).reason)
            assertEquals(listOf(LOG_TOOL), outcome.commits.map { it.toolName })
        }
    }

    @Test
    fun noLeafIsCollapsed() = runTest {
        NoNetworkGuard.during {
            val reasons = leaves().map { leaf ->
                val (outcome, _) = runOne(leaf.answer)
                assertTrue(outcome.toString(), outcome is CommandOutcome.Failed)
                (outcome as CommandOutcome.Failed).reason
            }

            assertEquals(leaves().map { it.expected }, reasons)
            assertEquals(reasons.size, reasons.map { it::class }.toSet().size)
            assertNotEquals(FailureReason.UnknownStop(), reasons[LEAF_MAX_TOKENS])
            assertNotEquals(FailureReason.UnknownStop(), reasons[LEAF_REFUSAL])
        }
    }
}
