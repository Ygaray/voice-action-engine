package io.github.ygaray.voiceactionengine.core

import io.github.ygaray.voiceactionengine.core.commit.ActionKind
import io.github.ygaray.voiceactionengine.core.commit.FinishedKind
import io.github.ygaray.voiceactionengine.core.commit.StepResult
import io.github.ygaray.voiceactionengine.core.commit.ToolStep
import io.github.ygaray.voiceactionengine.core.failure.BudgetBound
import io.github.ygaray.voiceactionengine.core.failure.FailureReason
import io.github.ygaray.voiceactionengine.core.pipeline.CommandOutcome
import io.github.ygaray.voiceactionengine.core.pipeline.TierPolicy
import io.github.ygaray.voiceactionengine.core.provider.ModelResult
import io.github.ygaray.voiceactionengine.core.strategy.agentic.AgenticLoopStrategy
import io.github.ygaray.voiceactionengine.core.telemetry.TraceCode
import io.github.ygaray.voiceactionengine.core.testing.FakeAiProvider
import io.github.ygaray.voiceactionengine.core.testing.FakeMutation
import io.github.ygaray.voiceactionengine.core.testing.NoNetworkGuard
import io.github.ygaray.voiceactionengine.core.testing.RecordingCommitSink
import io.github.ygaray.voiceactionengine.core.testing.RecordingSink
import io.github.ygaray.voiceactionengine.core.testing.ScriptedGate
import io.github.ygaray.voiceactionengine.core.testing.ScriptedToolExecutor
import io.github.ygaray.voiceactionengine.core.transcript.AssistantPart
import io.github.ygaray.voiceactionengine.core.transcript.StopReason
import io.github.ygaray.voiceactionengine.core.transcript.ToolResultsMessage
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

private const val LOG_TOOL = "log_entry"
private const val FINAL_REPLY = "done"
private const val SMALL_CEILING = 100L
private const val MIN_ITERATIONS = 2
private const val REUSED_ID = "call_0"
private const val FIRST_TURN_TOKENS = 60L

/** The loop's guards: the per-tool-name strike counter, whole-turn validation and the budget guards in SB's order. */
class AgenticLoopGuardsTest {

    private fun rejected(name: String = SAVE_TOOL): ToolStep =
        ToolStep.Finished(name, FinishedKind.ERROR, StepResult("rejected:$name", true))

    private fun committed(name: String = SAVE_TOOL, log: RecordingSink<String>? = null): ToolStep =
        ToolStep.Mutation(FakeMutation(name, StepResult("saved:$name"), log = log))

    private fun callTurn(vararg names: String): ModelResult =
        toolTurn(1, *names.mapIndexed { index, name -> callOf("c$index-$name", name, loopArguments()) }.toTypedArray())

    private fun prose(): ModelResult = FakeAiProvider.reply(FINAL_REPLY, usage(1))

    private fun snapshot() = loopSnapshotOf(writeTool(), writeTool(LOG_TOOL), readTool())

    private suspend fun run(
        fake: FakeAiProvider,
        strategy: AgenticLoopStrategy,
        gate: ScriptedGate = ScriptedGate.admitAll(),
        sink: RecordingCommitSink = RecordingCommitSink(),
    ): CommandOutcome =
        loopPipeline(listOf(strategy), fake, gate, sink).execute(CommandInput("add two things", "en", null))

    private fun duplicateIdTurn(tokens: Long = 1): ModelResult =
        toolTurn(
            tokens,
            callOf("x", SAVE_TOOL, loopArguments()),
            callOf("x", LOG_TOOL, loopArguments()),
        )

    private fun assertFailedWith(reason: FailureReason, outcome: CommandOutcome) {
        assertTrue(outcome.toString(), outcome is CommandOutcome.Failed)
        assertEquals(reason, (outcome as CommandOutcome.Failed).reason)
    }

    private suspend fun runWith(
        fake: FakeAiProvider,
        executor: ScriptedToolExecutor,
        policy: TierPolicy,
    ): CommandOutcome =
        loopPipeline(
            listOf(agenticLoop(executor, snapshot())),
            fake,
            ScriptedGate.admitAll(),
            RecordingCommitSink(),
            policy = policy,
        ).execute(CommandInput("add two things", "en", null))

    private fun assertToolFailure(outcome: CommandOutcome) {
        assertTrue(outcome.toString(), outcome is CommandOutcome.Failed)
        assertEquals(FailureReason.ToolFailure(), (outcome as CommandOutcome.Failed).reason)
    }

    @Test
    fun repeatedFailureOfTheSameToolAbortsButTheCommittedSiblingIsStillRecorded() = runTest {
        NoNetworkGuard.during {
            val log = RecordingSink<String>()
            val fake = FakeAiProvider(
                ProviderId.ANTHROPIC,
                callTurn(SAVE_TOOL),
                callTurn(SAVE_TOOL, LOG_TOOL),
            )
            val executor = ScriptedToolExecutor.sequence(null, rejected(), rejected(), committed(LOG_TOOL))
            val sink = RecordingCommitSink(log)

            val outcome = run(fake, agenticLoop(executor, snapshot()), sink = sink)

            assertToolFailure(outcome)
            assertEquals(2, fake.callCount)
            assertEquals(3, executor.callCount)
            assertEquals(
                listOf(ActionKind.IS_ERROR, ActionKind.IS_ERROR, ActionKind.COMMITTED),
                outcome.executed.map { it.kind },
            )
            assertEquals(listOf(LOG_TOOL), outcome.commits.map { it.toolName })
            assertEquals(3, sink.actions.size)
            val closedAt = log.events.indexOfFirst { it.startsWith("sink:closed:") }
            assertEquals(log.events.toString(), 3, log.events.take(closedAt).count { it.startsWith("sink:action:") })
        }
    }

    @Test
    fun strikesCountPerToolNameAcrossTurns() = runTest {
        NoNetworkGuard.during {
            val fake = FakeAiProvider(
                ProviderId.ANTHROPIC,
                callTurn(SAVE_TOOL),
                callTurn(SAVE_TOOL),
                callTurn(SAVE_TOOL),
            )
            val executor = ScriptedToolExecutor.sequence(null, rejected(), committed(), rejected())

            val outcome = run(fake, agenticLoop(executor, snapshot()))

            assertToolFailure(outcome)
            assertEquals(3, fake.callCount)
        }
    }

    @Test
    fun twoDifferentToolsFailingOnceEachDoNotAbort() = runTest {
        NoNetworkGuard.during {
            val fake = FakeAiProvider(ProviderId.ANTHROPIC, callTurn(SAVE_TOOL, LOG_TOOL), prose())
            val executor = ScriptedToolExecutor.sequence(null, rejected(), rejected(LOG_TOOL))

            val outcome = run(fake, agenticLoop(executor, snapshot()))

            assertTrue(outcome.toString(), outcome is CommandOutcome.Completed)
            assertEquals(FINAL_REPLY, (outcome as CommandOutcome.Completed).reply)
            assertEquals(2, fake.callCount)
        }
    }

    @Test
    fun anUnknownToolCountsAsAStrike() = runTest {
        NoNetworkGuard.during {
            val fake = FakeAiProvider(ProviderId.ANTHROPIC, callTurn("ghost_tool"), callTurn("ghost_tool"))
            val executor = ScriptedToolExecutor.sequence(null)

            val outcome = run(fake, agenticLoop(executor, snapshot()))

            assertToolFailure(outcome)
            assertEquals(2, fake.callCount)
            assertEquals(0, executor.callCount)
        }
    }

    @Test
    fun aPrepareFaultAndAnApplyErrorCountAsStrikes() = runTest {
        NoNetworkGuard.during {
            val fake = FakeAiProvider(ProviderId.ANTHROPIC, callTurn(SAVE_TOOL), callTurn(SAVE_TOOL))
            val asked = AtomicInteger()
            val failingApply = FakeMutation(SAVE_TOOL, { throw IllegalStateException("apply failed") })
            val executor = ScriptedToolExecutor(null) { _, _ ->
                if (asked.incrementAndGet() == 1) error("prepare failed")
                ToolStep.Mutation(failingApply)
            }

            val outcome = run(fake, agenticLoop(executor, snapshot()))

            assertToolFailure(outcome)
            assertEquals(2, fake.callCount)
            assertEquals(listOf(ActionKind.IS_ERROR, ActionKind.IS_ERROR), outcome.executed.map { it.kind })
        }
    }

    @Test
    fun aHeldCallIsNotAStrike() = runTest {
        NoNetworkGuard.during {
            val fake = FakeAiProvider(
                ProviderId.ANTHROPIC,
                callTurn(SAVE_TOOL),
                callTurn(SAVE_TOOL),
                callTurn(SAVE_TOOL),
                prose(),
            )
            val executor = ScriptedToolExecutor.sequence(null, committed(), committed(), committed())

            val outcome = run(fake, agenticLoop(executor, snapshot()), ScriptedGate.holdAll())

            assertTrue(outcome.toString(), outcome is CommandOutcome.Completed)
            assertEquals(3, outcome.held.size)
            assertEquals(4, fake.callCount)
        }
    }

    @Test
    fun aToolCallIdReusedFromAnEarlierTurnIsAccepted() = runTest {
        NoNetworkGuard.during {
            val fake = FakeAiProvider(
                ProviderId.ANTHROPIC,
                toolTurn(1, callOf(REUSED_ID, SAVE_TOOL, loopArguments())),
                toolTurn(1, callOf(REUSED_ID, LOG_TOOL, loopArguments())),
                prose(),
            )
            val executor = ScriptedToolExecutor.sequence(null, committed(SAVE_TOOL), committed(LOG_TOOL))
            val sink = RecordingCommitSink()

            val outcome = run(fake, agenticLoop(executor, snapshot()), sink = sink)

            assertTrue(outcome.toString(), outcome is CommandOutcome.Completed)
            assertEquals(FINAL_REPLY, (outcome as CommandOutcome.Completed).reply)
            assertFalse(outcome.trace.codes.contains(TraceCode.STRATEGY_ERROR))
            assertEquals(listOf(SAVE_TOOL, LOG_TOOL), executor.calls.map { it.toolName })
            assertEquals(listOf(ActionKind.COMMITTED, ActionKind.COMMITTED), sink.actions.map { it.action.kind })
            val afterFirst = (fake.calls[1].request.messages.last() as ToolResultsMessage).results.single()
            val afterSecond = (fake.calls[2].request.messages.last() as ToolResultsMessage).results.single()
            assertEquals(REUSED_ID, afterFirst.callId)
            assertEquals("saved:$SAVE_TOOL", afterFirst.content)
            assertEquals(REUSED_ID, afterSecond.callId)
            assertEquals("saved:$LOG_TOOL", afterSecond.content)
        }
    }

    @Test
    fun aToolCallIdRepeatedWithinATurnRejectsTheWholeTurn() = runTest {
        NoNetworkGuard.during {
            val aloneExecutor = ScriptedToolExecutor.sequence(null)
            val aloneGate = ScriptedGate.admitAll()
            val aloneSink = RecordingCommitSink()

            val first = run(
                FakeAiProvider(ProviderId.ANTHROPIC, duplicateIdTurn()),
                agenticLoop(aloneExecutor, snapshot()),
                aloneGate,
                aloneSink,
            )

            assertFailedWith(FailureReason.MalformedResponse(), first)
            assertEquals(0, aloneExecutor.callCount)
            assertEquals(0, aloneGate.calls)
            assertTrue(aloneSink.actions.isEmpty())

            val after = FakeAiProvider(
                ProviderId.ANTHROPIC,
                toolTurn(1, callOf("call_1", SAVE_TOOL, loopArguments())),
                duplicateIdTurn(),
            )
            val afterExecutor = ScriptedToolExecutor.sequence(null, committed(SAVE_TOOL))
            val afterGate = ScriptedGate.admitAll()
            val afterSink = RecordingCommitSink()

            val second = run(after, agenticLoop(afterExecutor, snapshot()), afterGate, afterSink)

            assertFailedWith(FailureReason.MalformedResponse(), second)
            assertEquals(2, after.callCount)
            assertEquals(listOf(SAVE_TOOL), afterExecutor.calls.map { it.toolName })
            assertEquals(1, afterGate.calls)
            assertEquals(listOf(ActionKind.COMMITTED), afterSink.actions.map { it.action.kind })
            assertEquals(listOf(SAVE_TOOL), second.commits.map { it.toolName })
        }
    }

    private fun toolUseWithoutCalls(): ModelResult = answerOf(StopReason.TOOL_USE, AssistantPart.Text("calling"))

    @Test
    fun aToolUseStopWithoutCallsIsMalformed() = runTest {
        NoNetworkGuard.during {
            val executor = ScriptedToolExecutor.sequence(null)

            val outcome = run(
                FakeAiProvider(ProviderId.ANTHROPIC, toolUseWithoutCalls()),
                agenticLoop(executor, snapshot()),
            )

            assertFailedWith(FailureReason.MalformedResponse(), outcome)
            assertEquals(0, executor.callCount)
        }
    }

    @Test
    fun aRejectedTurnNeverReachesTheResultsMessageConstructor() = runTest {
        NoNetworkGuard.during {
            val duplicate = run(
                FakeAiProvider(ProviderId.ANTHROPIC, duplicateIdTurn()),
                agenticLoop(ScriptedToolExecutor.sequence(null), snapshot()),
            )
            val noCalls = run(
                FakeAiProvider(ProviderId.ANTHROPIC, toolUseWithoutCalls()),
                agenticLoop(ScriptedToolExecutor.sequence(null), snapshot()),
            )

            listOf(duplicate, noCalls).forEach { outcome ->
                assertFailedWith(FailureReason.MalformedResponse(), outcome)
                assertFalse(outcome.trace.codes.contains(TraceCode.STRATEGY_ERROR))
            }
        }
    }

    @Test
    fun theTokenCeilingStopsTheLoopBeforeDispatchingATool() = runTest {
        NoNetworkGuard.during {
            val fake = FakeAiProvider(
                ProviderId.ANTHROPIC,
                toolTurn(SMALL_CEILING + 1, callOf("c0", SAVE_TOOL, loopArguments())),
            )
            val executor = ScriptedToolExecutor.sequence(null)
            val gate = ScriptedGate.admitAll()
            val sink = RecordingCommitSink()
            val policy = TierPolicy { tokenCeiling = SMALL_CEILING }

            val outcome = loopPipeline(listOf(agenticLoop(executor, snapshot())), fake, gate, sink, policy = policy)
                .execute(CommandInput("add two things", "en", null))

            assertFailedWith(FailureReason.BudgetExceeded(BudgetBound.TOKENS), outcome)
            assertEquals(0, executor.callCount)
            assertEquals(0, gate.calls)
            assertTrue(sink.actions.isEmpty())
        }
    }

    @Test
    fun theFinalIterationGuardNeverDispatchesTheLastPermittedTurn() = runTest {
        NoNetworkGuard.during {
            val turns = (0 until TierPolicy.DEFAULT.maxIterations).map { callOf("c$it", SAVE_TOOL, loopArguments()) }
            val fake = FakeAiProvider(ProviderId.ANTHROPIC, *turns.map { toolTurn(1, it) }.toTypedArray())
            val executor = ScriptedToolExecutor.sequence(null, *Array(turns.size - 1) { committed(SAVE_TOOL) })

            val outcome = run(fake, agenticLoop(executor, snapshot()))

            assertFailedWith(FailureReason.BudgetExceeded(BudgetBound.ITERATIONS), outcome)
            assertEquals(turns.size, fake.callCount)
            assertEquals(turns.size - 1, executor.callCount)
        }
    }

    @Test
    fun theGuardsRunInSbOrder() = runTest {
        NoNetworkGuard.during {
            val overCeiling = runWith(
                FakeAiProvider(ProviderId.ANTHROPIC, duplicateIdTurn(SMALL_CEILING + 1)),
                ScriptedToolExecutor.sequence(null),
                TierPolicy { tokenCeiling = SMALL_CEILING },
            )

            assertFailedWith(FailureReason.BudgetExceeded(BudgetBound.TOKENS), overCeiling)

            val onFinalIteration = runWith(
                FakeAiProvider(
                    ProviderId.ANTHROPIC,
                    toolTurn(1, callOf("c0", SAVE_TOOL, loopArguments())),
                    duplicateIdTurn(),
                ),
                ScriptedToolExecutor.sequence(null, committed(SAVE_TOOL)),
                TierPolicy { maxIterations = MIN_ITERATIONS },
            )

            assertFailedWith(FailureReason.BudgetExceeded(BudgetBound.ITERATIONS), onFinalIteration)
        }
    }

    // Turn 1 spends FIRST_TURN_TOKENS and commits; turn 2 is the final permitted iteration and spends secondTurnTokens.
    private suspend fun tieRun(secondTurnTokens: Long): CommandOutcome {
        val fake = FakeAiProvider(
            ProviderId.ANTHROPIC,
            toolTurn(FIRST_TURN_TOKENS, callOf("c0", SAVE_TOOL, loopArguments())),
            toolTurn(secondTurnTokens, callOf("c1", LOG_TOOL, loopArguments())),
        )
        val executor = ScriptedToolExecutor.sequence(null, committed(SAVE_TOOL), committed(LOG_TOOL))
        val policy = TierPolicy {
            tokenCeiling = SMALL_CEILING
            maxIterations = MIN_ITERATIONS
        }
        val outcome = runWith(fake, executor, policy)
        assertEquals(MIN_ITERATIONS, fake.callCount)
        assertEquals(listOf(SAVE_TOOL), executor.calls.map { it.toolName })
        assertEquals(listOf(SAVE_TOOL), outcome.commits.map { it.toolName })
        return outcome
    }

    @Test
    fun whenTheTokenCeilingAndTheIterationCapTripOnTheSameTurnTokensWins() = runTest {
        NoNetworkGuard.during {
            val both = tieRun(SMALL_CEILING - FIRST_TURN_TOKENS + 1)
            val capOnly = tieRun(SMALL_CEILING - FIRST_TURN_TOKENS)

            assertFailedWith(FailureReason.BudgetExceeded(BudgetBound.TOKENS), both)
            assertFailedWith(FailureReason.BudgetExceeded(BudgetBound.ITERATIONS), capOnly)
        }
    }
}
