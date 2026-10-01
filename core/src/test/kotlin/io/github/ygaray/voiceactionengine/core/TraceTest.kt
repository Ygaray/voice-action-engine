package io.github.ygaray.voiceactionengine.core

import io.github.ygaray.voiceactionengine.core.pipeline.CommandOutcome
import io.github.ygaray.voiceactionengine.core.pipeline.commandPipeline
import io.github.ygaray.voiceactionengine.core.strategy.StrategyOutcome
import io.github.ygaray.voiceactionengine.core.telemetry.PipelineEvent
import io.github.ygaray.voiceactionengine.core.telemetry.TurnRecord
import io.github.ygaray.voiceactionengine.core.telemetry.Usage
import io.github.ygaray.voiceactionengine.core.testing.FakeClock
import io.github.ygaray.voiceactionengine.core.testing.NoNetworkGuard
import io.github.ygaray.voiceactionengine.core.testing.RecordingCommitSink
import io.github.ygaray.voiceactionengine.core.testing.RecordingEventListener
import io.github.ygaray.voiceactionengine.core.testing.ScriptedGate
import io.github.ygaray.voiceactionengine.core.testing.ScriptedStrategy
import io.github.ygaray.voiceactionengine.core.testing.StrategyStep
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** The trace attributes every model turn to its tier, and a listener sees the run as it happens. */
class TraceTest {

    private fun tier(id: String, step: StrategyStep) = ScriptedStrategy(StrategyId(id), step)

    private fun turn(usage: Usage = Usage(INPUT, CACHE_READ, 0, OUTPUT)) =
        TurnRecord(ProviderId.ANTHROPIC, "claude-x", "tool_use", listOf("add_item"), usage, TURN_LATENCY)

    @Test
    fun aReportedTurnLandsInTheAttemptTheTraceAndTheSessionTotal() = runTest {
        NoNetworkGuard.during {
            var tokensAfterTurn = -1L
            val reported = turn()
            val strategy = tier("a") { _, session ->
                session.recordTurn(reported)
                tokensAfterTurn = session.tokensUsed
                StrategyOutcome.Completed("ok")
            }

            val outcome = commandPipeline {
                tier(strategy)
                gate = ScriptedGate.admitAll()
                commitSink = RecordingCommitSink()
            }.execute(CommandInput("add milk"))

            assertTrue(outcome is CommandOutcome.Completed)
            val attempt = outcome.trace.attempts.single()
            assertEquals(ProviderId.ANTHROPIC, attempt.provider)
            assertEquals("claude-x", attempt.model)
            assertEquals(1, attempt.turns.size)
            assertSame(reported, attempt.turns.single())
            assertEquals(TOTAL, attempt.usage.total)
            assertEquals(TOTAL, outcome.trace.usage.total)
            assertEquals(TOTAL, tokensAfterTurn)
        }
    }

    @Test
    fun aTierThatReportsNoTurnHasNoProviderModelOrUsage() = runTest {
        NoNetworkGuard.during {
            val outcome = commandPipeline {
                tier(tier("a") { _, _ -> StrategyOutcome.Completed("ok") })
                gate = ScriptedGate.admitAll()
                commitSink = RecordingCommitSink()
            }.execute(CommandInput("add milk"))

            val attempt = outcome.trace.attempts.single()
            assertNull(attempt.provider)
            assertNull(attempt.model)
            assertTrue(attempt.turns.isEmpty())
            assertEquals(0L, attempt.usage.total)
            assertEquals(0L, outcome.trace.usage.total)
        }
    }

    @Test
    fun theClockDrivesLatencyAndTheRunIdIsTheCommandId() = runTest {
        NoNetworkGuard.during {
            val fakeClock = FakeClock(START)
            val strategy = tier("a") { _, _ ->
                fakeClock.advanceBy(ADVANCE)
                StrategyOutcome.Completed("ok")
            }

            val outcome = commandPipeline {
                tier(strategy)
                gate = ScriptedGate.admitAll()
                commitSink = RecordingCommitSink()
                clock = fakeClock
                runIds = { "run-fixed" }
            }.execute(CommandInput("add milk"))

            assertEquals("run-fixed", outcome.runId)
            assertEquals(outcome.runId, outcome.trace.runId)
            assertEquals(ADVANCE, outcome.trace.attempts.single().latencyMillis)
            assertEquals(START, outcome.trace.startedAtMillis)
            assertEquals(ADVANCE, outcome.trace.durationMillis)
        }
    }

    @Test
    fun aListenerSeesTheRunLiveInOrder() = runTest {
        NoNetworkGuard.during {
            val listener = RecordingEventListener()
            val reported = turn()
            val strategy = tier("a") { _, session ->
                session.recordTurn(reported)
                StrategyOutcome.Completed("ok")
            }

            val outcome = commandPipeline {
                tier(strategy)
                gate = ScriptedGate.admitAll()
                commitSink = RecordingCommitSink()
                this.listener = listener
            }.execute(CommandInput("add milk"))

            val events = listener.events
            assertEquals(
                listOf(
                    PipelineEvent.CommandStarted::class,
                    PipelineEvent.TierStarted::class,
                    PipelineEvent.ProviderCall::class,
                    PipelineEvent.TierFinished::class,
                    PipelineEvent.RunClosed::class,
                ),
                events.map { it::class },
            )
            assertTrue(events.all { it.runId == outcome.runId })
            assertSame(reported, (events[PROVIDER_CALL_INDEX] as PipelineEvent.ProviderCall).turn)
            assertSame(outcome.trace.attempts.single(), (events[FINISHED_INDEX] as PipelineEvent.TierFinished).attempt)
            val closed = events.last() as PipelineEvent.RunClosed
            assertEquals("done", closed.terminationCode)
            assertEquals(0, closed.executedCount)
            assertEquals(0, closed.committedCount)
        }
    }

    private companion object {
        const val INPUT = 100L
        const val CACHE_READ = 900L
        const val OUTPUT = 50L
        const val TOTAL = 1050L
        const val TURN_LATENCY = 120L
        const val START = 1_000L
        const val ADVANCE = 40L
        const val PROVIDER_CALL_INDEX = 2
        const val FINISHED_INDEX = 3
    }
}
