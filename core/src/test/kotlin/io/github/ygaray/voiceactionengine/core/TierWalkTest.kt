package io.github.ygaray.voiceactionengine.core

import io.github.ygaray.voiceactionengine.core.failure.EscalationReason
import io.github.ygaray.voiceactionengine.core.failure.FailureDetails
import io.github.ygaray.voiceactionengine.core.failure.FailureReason
import io.github.ygaray.voiceactionengine.core.pipeline.CommandOutcome
import io.github.ygaray.voiceactionengine.core.pipeline.commandPipeline
import io.github.ygaray.voiceactionengine.core.strategy.StrategyOutcome
import io.github.ygaray.voiceactionengine.core.testing.NoNetworkGuard
import io.github.ygaray.voiceactionengine.core.testing.RecordingCommitSink
import io.github.ygaray.voiceactionengine.core.testing.ScriptedGate
import io.github.ygaray.voiceactionengine.core.testing.ScriptedStrategy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class TierWalkTest {

    private fun scripted(id: String, outcome: StrategyOutcome) =
        ScriptedStrategy(StrategyId(id), { _, _ -> outcome })

    private suspend fun run(vararg tiers: ScriptedStrategy): CommandOutcome =
        commandPipeline {
            tiers.forEach { tier(it) }
            gate = ScriptedGate.admitAll()
            commitSink = RecordingCommitSink()
        }.execute(CommandInput("hi"))

    @Test
    fun escalateClimbsAndHandsTheCarryOverByIdentity() = runTest {
        NoNetworkGuard.during {
            val carry = Any()
            val a = scripted("a", StrategyOutcome.Escalate(EscalationReason.NoToolCall(), carry))
            val b = scripted("b", StrategyOutcome.Completed("b"))
            val outcome = run(a, b)
            assertTrue(outcome is CommandOutcome.Completed)
            assertSame(carry, b.receivedCarries.single())
            assertNull(a.receivedCarries.single())
        }
    }

    @Test
    fun noMatchClimbsWithANullCarry() = runTest {
        NoNetworkGuard.during {
            val a = scripted("a", StrategyOutcome.NoMatch())
            val b = scripted("b", StrategyOutcome.Completed("b"))
            val outcome = run(a, b)
            assertTrue(outcome is CommandOutcome.Completed)
            assertNull(b.receivedCarries.single())
        }
    }

    @Test
    fun noMatchDropsACarryFromAnEarlierTier() = runTest {
        NoNetworkGuard.during {
            val a = scripted("a", StrategyOutcome.Escalate(EscalationReason.NoToolCall(), Any()))
            val b = scripted("b", StrategyOutcome.NoMatch())
            val c = scripted("c", StrategyOutcome.Completed("c"))
            run(a, b, c)
            assertNull(c.receivedCarries.single())
        }
    }

    @Test
    fun completedStopsTheWalk() = runTest {
        NoNetworkGuard.during {
            val a = scripted("a", StrategyOutcome.Completed("a"))
            val b = scripted("b", StrategyOutcome.Completed("b"))
            val outcome = run(a, b)
            assertEquals("a", (outcome as CommandOutcome.Completed).reply)
            assertEquals(0, b.executions)
        }
    }

    @Test
    fun failedStopsTheWalkAndCarriesItsDetails() = runTest {
        NoNetworkGuard.during {
            val reason = FailureReason.RateLimited()
            val details = FailureDetails(HTTP_RATE_LIMITED, "rate_limit_error", "req-1")
            val a = scripted("a", StrategyOutcome.Failed(reason, details))
            val b = scripted("b", StrategyOutcome.Completed("b"))
            val outcome = run(a, b) as CommandOutcome.Failed
            assertSame(reason, outcome.reason)
            assertSame(details, outcome.details)
            assertEquals(0, b.executions)
        }
    }

    @Test
    fun exhaustedLadderEndsUnhandledWithTheLastTiersReasonInstance() = runTest {
        NoNetworkGuard.during {
            val first = EscalationReason.NoToolCall()
            val last = EscalationReason.ModelDeclined()
            val a = scripted("a", StrategyOutcome.Escalate(first))
            val b = scripted("b", StrategyOutcome.Escalate(last))
            val outcome = run(a, b) as CommandOutcome.Unhandled
            assertSame(last, outcome.lastReason)
        }
    }

    @Test
    fun aFinalNoMatchEndsUnhandledWithNoReason() = runTest {
        NoNetworkGuard.during {
            val a = scripted("a", StrategyOutcome.Escalate(EscalationReason.NoToolCall()))
            val b = scripted("b", StrategyOutcome.NoMatch())
            val outcome = run(a, b) as CommandOutcome.Unhandled
            assertNull(outcome.lastReason)
        }
    }

    @Test
    fun traceListsTheTiersThatRanWithTheirOutcomeCodes() = runTest {
        NoNetworkGuard.during {
            val reason = EscalationReason.ModelDeclined()
            val a = scripted("a", StrategyOutcome.Escalate(reason))
            val b = scripted("b", StrategyOutcome.NoMatch())
            val c = scripted("c", StrategyOutcome.Failed(FailureReason.Refusal()))
            val d = scripted("d", StrategyOutcome.Completed("d"))
            val attempts = run(a, b, c, d).trace.attempts
            assertEquals(listOf("a", "b", "c"), attempts.map { it.strategy.value })
            assertEquals(listOf("escalated", "no_match", "failed"), attempts.map { it.outcome })
            assertSame(reason, attempts[0].escalationReason)
            assertEquals(FailureReason.Refusal(), attempts[2].failure)
        }
    }

    @Test
    fun aCompletedTierIsTracedAsCompleted() = runTest {
        NoNetworkGuard.during {
            val attempts = run(scripted("a", StrategyOutcome.Completed("a"))).trace.attempts
            assertEquals(listOf("completed"), attempts.map { it.outcome })
        }
    }

    private companion object {
        const val HTTP_RATE_LIMITED = 429
    }
}
