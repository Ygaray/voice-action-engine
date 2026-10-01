package io.github.ygaray.voiceactionengine.core

import io.github.ygaray.voiceactionengine.core.commit.StepResult
import io.github.ygaray.voiceactionengine.core.commit.ToolStep
import io.github.ygaray.voiceactionengine.core.failure.BudgetBound
import io.github.ygaray.voiceactionengine.core.failure.EscalationReason
import io.github.ygaray.voiceactionengine.core.failure.FailureReason
import io.github.ygaray.voiceactionengine.core.pipeline.CommandOutcome
import io.github.ygaray.voiceactionengine.core.pipeline.TierPolicy
import io.github.ygaray.voiceactionengine.core.provider.ModelResult
import io.github.ygaray.voiceactionengine.core.strategy.CommandStrategy
import io.github.ygaray.voiceactionengine.core.strategy.Resolution
import io.github.ygaray.voiceactionengine.core.strategy.StrategyOutcome
import io.github.ygaray.voiceactionengine.core.strategy.ToolingSnapshot
import io.github.ygaray.voiceactionengine.core.telemetry.TurnRecord
import io.github.ygaray.voiceactionengine.core.telemetry.Usage
import io.github.ygaray.voiceactionengine.core.testing.FakeAiProvider
import io.github.ygaray.voiceactionengine.core.testing.FakeMutation
import io.github.ygaray.voiceactionengine.core.testing.NoNetworkGuard
import io.github.ygaray.voiceactionengine.core.testing.RecordingCommitSink
import io.github.ygaray.voiceactionengine.core.testing.ScriptedGate
import io.github.ygaray.voiceactionengine.core.testing.ScriptedStrategy
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val DEFAULT_ITERATIONS = 6
private const val DEFAULT_CEILING = 60_000L
private const val DEFAULT_TURN_LIMIT = 4_096
private const val MIN_ITERATIONS = 2
private const val CUSTOM_CEILING = 50L
private const val CUSTOM_TURN_LIMIT = 777
private const val SMALL_CEILING = 100L

/** The 6 / 60000 / 4096 limits as SingleShot obeys them, read from the session policy only. */
class SingleShotLimitsTest {

    private class Run(
        val outcome: CommandOutcome,
        val fake: FakeAiProvider,
        val resolver: RecordingResolver,
        val gate: ScriptedGate,
        val sink: RecordingCommitSink,
    )

    private fun usage(total: Long): Usage = Usage(0, 0, 0, total)

    private fun entriesAnswer(total: Long = 1L): ModelResult =
        FakeAiProvider.toolCall("call-1", ENTRIES_TOOL, entriesArguments("a"), usage(total))

    private fun spendingTier(tokens: Long): CommandStrategy =
        ScriptedStrategy(
            StrategyId("earlier"),
            { _, session ->
                session.recordTurn(TurnRecord(null, null, null, emptyList(), Usage(0, 0, 0, tokens), 1L))
                StrategyOutcome.Escalate(EscalationReason.NoToolCall())
            },
        )

    private fun savingResolver(): RecordingResolver =
        RecordingResolver { _, _ ->
            Resolution.Steps(listOf(ToolStep.Mutation(FakeMutation(ENTRIES_TOOL, StepResult("saved")))))
        }

    private suspend fun runShot(
        fake: FakeAiProvider,
        policy: TierPolicy = TierPolicy.DEFAULT,
        priorTokens: Long? = null,
        snapshot: ToolingSnapshot = snapshotOf(entriesTool(), askTool()),
    ): Run {
        val resolver = savingResolver()
        val gate = ScriptedGate.admitAll()
        val sink = RecordingCommitSink()
        val shot = singleShot(resolver, snapshot)
        val tiers = listOfNotNull(priorTokens?.let { spendingTier(it) }, shot)
        val pipeline = pipelineOf(tiers, fake, gate, sink, policy = policy)
        return Run(pipeline.execute(CommandInput("add two things", "en", null)), fake, resolver, gate, sink)
    }

    private fun assertTokenBudgetFailure(outcome: CommandOutcome) {
        assertTrue(outcome.toString(), outcome is CommandOutcome.Failed)
        assertEquals(FailureReason.BudgetExceeded(BudgetBound.TOKENS), (outcome as CommandOutcome.Failed).reason)
    }

    @Test
    fun atTheDefaultCeilingTheTierRefusesBeforeAnyProviderCall() = runTest {
        NoNetworkGuard.during {
            val run = runShot(FakeAiProvider(ProviderId.ANTHROPIC), priorTokens = TierPolicy.DEFAULT.tokenCeiling)

            assertTokenBudgetFailure(run.outcome)
            assertEquals(0, run.fake.callCount)
            assertEquals(0, run.resolver.invocations)
            assertEquals(0, run.gate.calls)
        }
    }

    @Test
    fun oneTokenBelowTheDefaultCeilingTheCallIsMade() = runTest {
        NoNetworkGuard.during {
            val fake = FakeAiProvider(ProviderId.ANTHROPIC, entriesAnswer())

            val run = runShot(fake, priorTokens = TierPolicy.DEFAULT.tokenCeiling - 1)

            assertEquals(1, run.fake.callCount)
        }
    }

    @Test
    fun aCustomCeilingIsReadFromTheSessionPolicy() = runTest {
        NoNetworkGuard.during {
            val policy = TierPolicy { tokenCeiling = CUSTOM_CEILING }

            val atCeiling = runShot(FakeAiProvider(ProviderId.ANTHROPIC), policy, priorTokens = CUSTOM_CEILING)
            val below = runShot(
                FakeAiProvider(ProviderId.ANTHROPIC, entriesAnswer()),
                policy,
                priorTokens = CUSTOM_CEILING - 1,
            )

            assertTokenBudgetFailure(atCeiling.outcome)
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
            val run = runShot(FakeAiProvider(ProviderId.ANTHROPIC, entriesAnswer()))

            assertEquals(DEFAULT_TURN_LIMIT, run.fake.calls.single().request.maxTokens)
            assertEquals(TierPolicy.DEFAULT.maxTokensPerTurn, run.fake.calls.single().request.maxTokens)
        }
    }

    @Test
    fun customPerTurnLimitIsSentUnchanged() = runTest {
        NoNetworkGuard.during {
            val policy = TierPolicy { maxTokensPerTurn = CUSTOM_TURN_LIMIT }

            val run = runShot(FakeAiProvider(ProviderId.ANTHROPIC, entriesAnswer()), policy)

            assertEquals(CUSTOM_TURN_LIMIT, run.fake.calls.single().request.maxTokens)
        }
    }

    @Test
    fun aResponseThatCrossesTheCeilingFailsBeforeTheResolverOrAnyWrite() = runTest {
        NoNetworkGuard.during {
            val policy = TierPolicy { tokenCeiling = SMALL_CEILING }

            val run = runShot(FakeAiProvider(ProviderId.ANTHROPIC, entriesAnswer(SMALL_CEILING + 1)), policy)

            assertTokenBudgetFailure(run.outcome)
            assertEquals(1, run.fake.callCount)
            assertEquals(0, run.resolver.invocations)
            assertEquals(0, run.gate.calls)
            assertTrue(run.sink.actions.isEmpty())
        }
    }

    @Test
    fun aResponseLandingExactlyOnTheCeilingIsStillResolved() = runTest {
        NoNetworkGuard.during {
            val policy = TierPolicy { tokenCeiling = SMALL_CEILING }

            val run = runShot(FakeAiProvider(ProviderId.ANTHROPIC, entriesAnswer(SMALL_CEILING)), policy)

            assertTrue(run.outcome.toString(), run.outcome is CommandOutcome.Completed)
            assertEquals(1, run.resolver.invocations)
        }
    }

    private suspend fun assertOneCallAndOneTurn(policy: TierPolicy, answer: ModelResult) {
        val run = runShot(FakeAiProvider(ProviderId.ANTHROPIC, answer), policy)

        assertEquals(1, run.fake.callCount)
        val attempt = run.outcome.trace.attempts.single { it.strategy == StrategyId("single_shot") }
        assertEquals(1, attempt.turns.size)
    }

    private fun threeAnswers(): List<ModelResult> =
        listOf(
            ModelResult.Failure(FailureReason.NoToolCall()),
            FakeAiProvider.reply("no tool needed", usage(1L)),
            entriesAnswer(),
        )

    @Test
    fun exactlyOneProviderCallAtMinimumIterations() = runTest {
        NoNetworkGuard.during {
            val policy = TierPolicy { maxIterations = MIN_ITERATIONS }

            threeAnswers().forEach { assertOneCallAndOneTurn(policy, it) }
        }
    }

    @Test
    fun exactlyOneProviderCallAtDefaultIterations() = runTest {
        NoNetworkGuard.during {
            val refusal = FakeAiProvider.refusal(usage(1L))
            val terminal = FakeAiProvider.toolCall("call-2", ASK_TOOL, JsonObject(emptyMap()), usage(1L))

            (threeAnswers() + refusal + terminal).forEach { assertOneCallAndOneTurn(TierPolicy.DEFAULT, it) }
        }
    }
}
