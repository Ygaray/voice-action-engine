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
import io.github.ygaray.voiceactionengine.core.telemetry.TurnRecord
import io.github.ygaray.voiceactionengine.core.telemetry.Usage
import io.github.ygaray.voiceactionengine.core.testing.FakeAiProvider
import io.github.ygaray.voiceactionengine.core.testing.FakeMutation
import io.github.ygaray.voiceactionengine.core.testing.NoNetworkGuard
import io.github.ygaray.voiceactionengine.core.testing.RecordingCommitSink
import io.github.ygaray.voiceactionengine.core.testing.ScriptedGate
import io.github.ygaray.voiceactionengine.core.testing.ScriptedStrategy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val CUSTOM_CEILING = 50L

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
    ): Run {
        val resolver = savingResolver()
        val gate = ScriptedGate.admitAll()
        val sink = RecordingCommitSink()
        val shot = singleShot(resolver, snapshotOf(entriesTool()))
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
}
