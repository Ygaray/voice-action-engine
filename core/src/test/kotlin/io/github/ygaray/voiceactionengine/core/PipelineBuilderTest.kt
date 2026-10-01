package io.github.ygaray.voiceactionengine.core

import io.github.ygaray.voiceactionengine.core.failure.EscalationReason
import io.github.ygaray.voiceactionengine.core.failure.FailureReason
import io.github.ygaray.voiceactionengine.core.pipeline.CommandOutcome
import io.github.ygaray.voiceactionengine.core.pipeline.CommandPipeline
import io.github.ygaray.voiceactionengine.core.pipeline.PipelineBuilder
import io.github.ygaray.voiceactionengine.core.pipeline.TierPolicy
import io.github.ygaray.voiceactionengine.core.pipeline.TierPolicySource
import io.github.ygaray.voiceactionengine.core.pipeline.commandPipeline
import io.github.ygaray.voiceactionengine.core.strategy.CommandSession
import io.github.ygaray.voiceactionengine.core.strategy.StrategyOutcome
import io.github.ygaray.voiceactionengine.core.testing.NoNetworkGuard
import io.github.ygaray.voiceactionengine.core.testing.RecordingCommitSink
import io.github.ygaray.voiceactionengine.core.testing.ScriptedGate
import io.github.ygaray.voiceactionengine.core.testing.ScriptedStrategy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PipelineBuilderTest {

    private fun strategy(id: String, outcome: StrategyOutcome = StrategyOutcome.NoMatch()) =
        ScriptedStrategy(StrategyId(id), { _, _ -> outcome })

    private fun buildFailure(block: PipelineBuilder.() -> Unit): String =
        assertThrows(IllegalArgumentException::class.java) { commandPipeline(block) }.message.orEmpty()

    @Test
    fun zeroTiersIsRejected() {
        val message = buildFailure {
            gate = ScriptedGate.admitAll()
            commitSink = RecordingCommitSink()
        }
        assertTrue(message, message.contains("at least one tier"))
    }

    @Test
    fun duplicateTierIdIsRejectedAndNamed() {
        val message = buildFailure {
            tier(strategy("same"))
            tier(strategy("other"))
            tier(strategy("same"))
            gate = ScriptedGate.admitAll()
            commitSink = RecordingCommitSink()
        }
        assertEquals("commandPipeline: duplicate tier id same", message)
    }

    @Test
    fun missingGateIsRejectedWithNoAutoCommitDefault() {
        val message = buildFailure {
            tier(strategy("a"))
            commitSink = RecordingCommitSink()
        }
        assertEquals("commandPipeline: gate is required (no auto-commit default)", message)
    }

    @Test
    fun missingCommitSinkIsRejected() {
        val message = buildFailure {
            tier(strategy("a"))
            gate = ScriptedGate.admitAll()
        }
        assertEquals("commandPipeline: commitSink is required", message)
    }

    @Test
    fun validBuildExposesTiersInDeclarationOrder() {
        val pipeline = commandPipeline {
            tier(strategy("c"))
            tier(strategy("a"))
            tier(strategy("b"))
            gate = ScriptedGate.admitAll()
            commitSink = RecordingCommitSink()
        }
        assertEquals(listOf(StrategyId("c"), StrategyId("a"), StrategyId("b")), pipeline.tiers)
        assertEquals("CommandPipeline(tiers=[c, a, b])", pipeline.toString())
    }

    private fun pipelineSeeing(
        seen: MutableList<CommandSession>,
        source: TierPolicySource? = null,
    ): CommandPipeline = commandPipeline {
        tier(
            ScriptedStrategy(
                StrategyId("only"),
                { _, session ->
                    seen.add(session)
                    StrategyOutcome.NoMatch()
                },
            ),
        )
        gate = ScriptedGate.admitAll()
        commitSink = RecordingCommitSink()
        if (source != null) policy = source
    }

    @Test
    fun defaultPolicyReachesTheStrategyFieldByField() = runTest {
        NoNetworkGuard.during {
            val seen = mutableListOf<CommandSession>()
            pipelineSeeing(seen).execute(CommandInput("hi"))
            val policy = seen.single().policy
            val expected = TierPolicy.DEFAULT
            assertEquals(expected.offlineOnly, policy.offlineOnly)
            assertEquals(expected.maxTier, policy.maxTier)
            assertEquals(expected.allowedProviders, policy.allowedProviders)
            assertEquals(expected.maxIterations, policy.maxIterations)
            assertEquals(expected.tokenCeiling, policy.tokenCeiling)
            assertEquals(expected.maxTokensPerTurn, policy.maxTokensPerTurn)
            assertEquals(expected.commandTimeoutMillis, policy.commandTimeoutMillis)
        }
    }

    @Test
    fun customPolicySourceIsWhatTheStrategySees() = runTest {
        NoNetworkGuard.during {
            val custom = TierPolicy { maxIterations = CUSTOM_ITERATIONS }
            val seen = mutableListOf<CommandSession>()
            pipelineSeeing(seen, TierPolicySource.fixed(custom)).execute(CommandInput("hi"))
            assertSame(custom, seen.single().policy)
        }
    }

    @Test
    fun injectedRunIdsAndClockAreUsed() = runTest {
        NoNetworkGuard.during {
            val pipeline = commandPipeline {
                tier(strategy("a", StrategyOutcome.Completed("x")))
                gate = ScriptedGate.admitAll()
                commitSink = RecordingCommitSink()
                runIds = { "injected-id" }
                clock = { CLOCK_VALUE }
            }
            val outcome = pipeline.execute(CommandInput("hi"))
            assertEquals("injected-id", outcome.runId)
            assertEquals(CLOCK_VALUE, outcome.trace.startedAtMillis)
            assertEquals(0L, outcome.trace.durationMillis)
        }
    }

    @Test
    fun executeNeverThrowsAndMapsEachStrategyOutcomeVariant() = runTest {
        NoNetworkGuard.during {
            val escalation = EscalationReason.NoToolCall()
            val failure = FailureReason.Timeout()
            val outcomes = listOf(
                StrategyOutcome.Completed("done"),
                StrategyOutcome.Escalate(escalation),
                StrategyOutcome.NoMatch(),
                StrategyOutcome.Failed(failure),
            ).map { scripted ->
                commandPipeline {
                    tier(strategy("only", scripted))
                    gate = ScriptedGate.admitAll()
                    commitSink = RecordingCommitSink()
                }.execute(CommandInput("hi"))
            }
            assertTrue(outcomes[0] is CommandOutcome.Completed)
            assertEquals(escalation, (outcomes[1] as CommandOutcome.Unhandled).lastReason)
            assertNull((outcomes[2] as CommandOutcome.Unhandled).lastReason)
            assertEquals(failure, (outcomes[3] as CommandOutcome.Failed).reason)
        }
    }

    private companion object {
        const val CUSTOM_ITERATIONS = 9
        const val CLOCK_VALUE = 1_234L
    }
}
