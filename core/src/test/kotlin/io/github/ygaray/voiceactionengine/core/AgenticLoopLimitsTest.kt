package io.github.ygaray.voiceactionengine.core

import io.github.ygaray.voiceactionengine.core.commit.ActionKind
import io.github.ygaray.voiceactionengine.core.commit.StepResult
import io.github.ygaray.voiceactionengine.core.commit.ToolStep
import io.github.ygaray.voiceactionengine.core.failure.BudgetBound
import io.github.ygaray.voiceactionengine.core.failure.EscalationReason
import io.github.ygaray.voiceactionengine.core.failure.FailureReason
import io.github.ygaray.voiceactionengine.core.pipeline.CommandOutcome
import io.github.ygaray.voiceactionengine.core.pipeline.TierPolicy
import io.github.ygaray.voiceactionengine.core.provider.ModelResult
import io.github.ygaray.voiceactionengine.core.strategy.CommandStrategy
import io.github.ygaray.voiceactionengine.core.strategy.StrategyOutcome
import io.github.ygaray.voiceactionengine.core.telemetry.TurnRecord
import io.github.ygaray.voiceactionengine.core.telemetry.Usage
import io.github.ygaray.voiceactionengine.core.testing.FakeAiProvider
import io.github.ygaray.voiceactionengine.core.testing.FakeMutation
import io.github.ygaray.voiceactionengine.core.testing.NoNetworkGuard
import io.github.ygaray.voiceactionengine.core.testing.RecordingCommitSink
import io.github.ygaray.voiceactionengine.core.testing.ScriptedGate
import io.github.ygaray.voiceactionengine.core.testing.ScriptedStrategy
import io.github.ygaray.voiceactionengine.core.testing.ScriptedToolExecutor
import io.github.ygaray.voiceactionengine.core.transcript.ToolResultsMessage
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

private const val DEFAULT_ITERATIONS = 6
private const val DEFAULT_CEILING = 60_000L
private const val DEFAULT_TURN_LIMIT = 4_096
private const val MIN_ITERATIONS = 2
private const val CUSTOM_CEILING = 50L
private const val CUSTOM_TURN_LIMIT = 777
private const val SMALL_CEILING = 100L
private const val ROUTED_TURNS = 3
private const val ROUTED_TOKENS = 7L
private const val FINAL_REPLY = "done"

/** The 6 / 60000 / 4096 limits as the agentic loop obeys them, read from the session policy and the session only. */
class AgenticLoopLimitsTest {

    private class Run(
        val outcome: CommandOutcome,
        val fake: FakeAiProvider,
        val executor: ScriptedToolExecutor,
        val mutations: List<FakeMutation>,
        val gate: ScriptedGate,
        val sink: RecordingCommitSink,
    ) {
        val applies: Int get() = mutations.sumOf { it.applyCount }
    }

    private fun spendingTier(tokens: Long): CommandStrategy =
        ScriptedStrategy(
            StrategyId("earlier"),
            { _, session ->
                session.recordTurn(TurnRecord(null, null, null, emptyList(), Usage(0, 0, 0, tokens), 1L))
                StrategyOutcome.Escalate(EscalationReason.NoToolCall())
            },
        )

    private fun saveTurn(index: Int, tokens: Long = 1L): ModelResult =
        toolTurn(tokens, callOf("c$index", SAVE_TOOL, loopArguments()))

    private fun prose(tokens: Long = 1L): ModelResult = FakeAiProvider.reply(FINAL_REPLY, usage(tokens))

    private fun fakeOf(vararg results: ModelResult): FakeAiProvider = FakeAiProvider(ProviderId.ANTHROPIC, *results)

    // Every call to the executor gets a fresh committed save, so a test can count the applies that really happened.
    private suspend fun runLoop(
        fake: FakeAiProvider,
        policy: TierPolicy = TierPolicy.DEFAULT,
        priorTokens: Long? = null,
    ): Run {
        val mutations = CopyOnWriteArrayList<FakeMutation>()
        val executor = ScriptedToolExecutor(null) { call, _ ->
            val mutation = FakeMutation(call.toolName, StepResult("saved:${call.toolName}"))
            mutations.add(mutation)
            ToolStep.Mutation(mutation)
        }
        val gate = ScriptedGate.admitAll()
        val sink = RecordingCommitSink()
        val loop = agenticLoop(executor, loopSnapshotOf(writeTool(), readTool()))
        val tiers = listOfNotNull(priorTokens?.let { spendingTier(it) }, loop)
        val pipeline = loopPipeline(tiers, fake, gate, sink, policy = policy)
        val outcome = pipeline.execute(CommandInput("add two things", "en", null))
        return Run(outcome, fake, executor, mutations, gate, sink)
    }

    private fun assertFailedWith(bound: BudgetBound, outcome: CommandOutcome) {
        assertTrue(outcome.toString(), outcome is CommandOutcome.Failed)
        assertEquals(FailureReason.BudgetExceeded(bound), (outcome as CommandOutcome.Failed).reason)
    }

    @Test
    fun atTheDefaultCeilingTheTierRefusesBeforeAnyProviderCall() = runTest {
        NoNetworkGuard.during {
            val run = runLoop(fakeOf(), priorTokens = TierPolicy.DEFAULT.tokenCeiling)

            assertFailedWith(BudgetBound.TOKENS, run.outcome)
            assertEquals(0, run.fake.callCount)
            assertEquals(0, run.executor.callCount)
            assertEquals(0, run.gate.calls)
        }
    }

    @Test
    fun oneTokenBelowTheDefaultCeilingTheCallIsMade() = runTest {
        NoNetworkGuard.during {
            val run = runLoop(fakeOf(prose()), priorTokens = TierPolicy.DEFAULT.tokenCeiling - 1)

            assertEquals(1, run.fake.callCount)
        }
    }

    @Test
    fun aCustomCeilingIsReadFromTheSessionPolicy() = runTest {
        NoNetworkGuard.during {
            val policy = TierPolicy { tokenCeiling = CUSTOM_CEILING }

            val atCeiling = runLoop(fakeOf(), policy, priorTokens = CUSTOM_CEILING)
            val below = runLoop(fakeOf(prose()), policy, priorTokens = CUSTOM_CEILING - 1)

            assertFailedWith(BudgetBound.TOKENS, atCeiling.outcome)
            assertEquals(0, atCeiling.fake.callCount)
            assertEquals(1, below.fake.callCount)
        }
    }

    @Test
    fun defaultsAreTheContractLimits() {
        assertEquals(DEFAULT_ITERATIONS, TierPolicy.DEFAULT.maxIterations)
        assertEquals(DEFAULT_CEILING, TierPolicy.DEFAULT.tokenCeiling)
        assertEquals(DEFAULT_TURN_LIMIT, TierPolicy.DEFAULT.maxTokensPerTurn)
    }

    @Test
    fun defaultPolicySendsTheDefaultPerTurnTokenLimit() = runTest {
        NoNetworkGuard.during {
            val run = runLoop(fakeOf(prose()))

            assertEquals(DEFAULT_TURN_LIMIT, run.fake.calls.single().request.maxTokens)
            assertEquals(TierPolicy.DEFAULT.maxTokensPerTurn, run.fake.calls.single().request.maxTokens)
        }
    }

    @Test
    fun customPerTurnLimitIsSentUnchanged() = runTest {
        NoNetworkGuard.during {
            val run = runLoop(fakeOf(prose()), TierPolicy { maxTokensPerTurn = CUSTOM_TURN_LIMIT })

            assertEquals(CUSTOM_TURN_LIMIT, run.fake.calls.single().request.maxTokens)
        }
    }

    @Test
    fun aToolTurnThatCrossesTheCeilingFailsBeforeTheExecutorOrAnyWrite() = runTest {
        NoNetworkGuard.during {
            val policy = TierPolicy { tokenCeiling = SMALL_CEILING }

            val run = runLoop(fakeOf(saveTurn(0, SMALL_CEILING + 1)), policy)

            assertFailedWith(BudgetBound.TOKENS, run.outcome)
            assertEquals(1, run.fake.callCount)
            assertEquals(0, run.executor.callCount)
            assertEquals(0, run.gate.calls)
            assertTrue(run.sink.actions.isEmpty())
        }
    }

    @Test
    fun aToolTurnLandingExactlyOnTheCeilingIsStillDispatched() = runTest {
        NoNetworkGuard.during {
            val policy = TierPolicy { tokenCeiling = SMALL_CEILING }

            val run = runLoop(fakeOf(saveTurn(0, SMALL_CEILING)), policy)

            assertEquals(1, run.executor.callCount)
            assertEquals(1, run.applies)
            assertEquals(listOf(ActionKind.COMMITTED), run.sink.actions.map { it.action.kind })
        }
    }

    @Test
    fun theFinalIterationGuardHoldsAtMinimumIterations() = runTest {
        NoNetworkGuard.during {
            val policy = TierPolicy { maxIterations = MIN_ITERATIONS }

            val run = runLoop(fakeOf(saveTurn(0), saveTurn(1)), policy)

            assertFailedWith(BudgetBound.ITERATIONS, run.outcome)
            assertEquals(MIN_ITERATIONS, run.fake.callCount)
            assertEquals(1, run.applies)
        }
    }

    @Test
    fun theFinalIterationGuardHoldsAtDefaultIterations() = runTest {
        NoNetworkGuard.during {
            val turns = (0 until DEFAULT_ITERATIONS).map { saveTurn(it) }

            val run = runLoop(fakeOf(*turns.toTypedArray()))

            assertFailedWith(BudgetBound.ITERATIONS, run.outcome)
            assertEquals(DEFAULT_ITERATIONS, run.fake.callCount)
            assertEquals(DEFAULT_ITERATIONS - 1, run.applies)
            val replies = run.fake.calls.last().request.messages.filterIsInstance<ToolResultsMessage>()
            val answered = replies.flatMap { message -> message.results.map { it.callId } }
            assertEquals((0 until DEFAULT_ITERATIONS - 1).map { "c$it" }, answered)
        }
    }

    @Test
    fun everyRequestOfAMultiTurnRunCarriesThePerTurnLimit() = runTest {
        NoNetworkGuard.during {
            val policy = TierPolicy { maxTokensPerTurn = CUSTOM_TURN_LIMIT }

            val run = runLoop(fakeOf(saveTurn(0), saveTurn(1), prose()), policy)

            assertEquals(ROUTED_TURNS, run.fake.callCount)
            assertEquals(List(ROUTED_TURNS) { CUSTOM_TURN_LIMIT }, run.fake.calls.map { it.request.maxTokens })
        }
    }

    @Test
    fun theCeilingIsCheckedBeforeEveryIteration() = runTest {
        NoNetworkGuard.during {
            val policy = TierPolicy { tokenCeiling = SMALL_CEILING }

            val run = runLoop(fakeOf(saveTurn(0, SMALL_CEILING)), policy)

            assertFailedWith(BudgetBound.TOKENS, run.outcome)
            assertEquals(1, run.fake.callCount)
            assertEquals(listOf(SAVE_TOOL), run.outcome.commits.map { it.toolName })
        }
    }

    @Test
    fun anEndOfTurnAnswerOverTheCeilingStillCompletes() = runTest {
        NoNetworkGuard.during {
            val policy = TierPolicy { tokenCeiling = SMALL_CEILING }

            val run = runLoop(fakeOf(prose(SMALL_CEILING + SMALL_CEILING / 2)), policy)

            assertTrue(run.outcome.toString(), run.outcome is CommandOutcome.Completed)
            assertEquals(FINAL_REPLY, (run.outcome as CommandOutcome.Completed).reply)
        }
    }

    @Test
    fun eachRoutedTurnIsCountedOnce() = runTest {
        NoNetworkGuard.during {
            val run = runLoop(fakeOf(saveTurn(0, ROUTED_TOKENS), saveTurn(1, ROUTED_TOKENS), prose(ROUTED_TOKENS)))

            assertTrue(run.outcome.toString(), run.outcome is CommandOutcome.Completed)
            val attempt = run.outcome.trace.attempts.single { it.strategy == StrategyId("agentic") }
            assertEquals(run.fake.callCount, attempt.turns.size)
            assertEquals(ROUTED_TURNS * ROUTED_TOKENS, run.outcome.trace.usage.total)
        }
    }
}
