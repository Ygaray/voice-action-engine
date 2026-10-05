package io.github.ygaray.voiceactionengine.core

import io.github.ygaray.voiceactionengine.core.failure.EscalationReason
import io.github.ygaray.voiceactionengine.core.failure.FailureReason
import io.github.ygaray.voiceactionengine.core.pipeline.CommandOutcome
import io.github.ygaray.voiceactionengine.core.pipeline.TierPolicy
import io.github.ygaray.voiceactionengine.core.pipeline.TierPolicySource
import io.github.ygaray.voiceactionengine.core.pipeline.commandPipeline
import io.github.ygaray.voiceactionengine.core.strategy.StrategyOutcome
import io.github.ygaray.voiceactionengine.core.telemetry.PipelineEvent
import io.github.ygaray.voiceactionengine.core.telemetry.TurnRecord
import io.github.ygaray.voiceactionengine.core.telemetry.Usage
import io.github.ygaray.voiceactionengine.core.testing.NoNetworkGuard
import io.github.ygaray.voiceactionengine.core.testing.RecordingCommitSink
import io.github.ygaray.voiceactionengine.core.testing.RecordingEventListener
import io.github.ygaray.voiceactionengine.core.testing.ScriptedGate
import io.github.ygaray.voiceactionengine.core.testing.ScriptedStrategy
import io.github.ygaray.voiceactionengine.core.testing.StrategyStep
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.coroutines.cancellation.CancellationException

/** A tier cut off by the engine deadline or the caller still reaches the trace, with its turns and tokens. */
class InFlightTierTraceTest {
    private val paid = Usage(inputUncached = 100, cacheRead = 20, cacheWrite = 5, output = 30)
    private val paidTotal = 155L

    private val hangAfterOneTurn = { started: CompletableDeferred<Unit>? ->
        val step: StrategyStep = { _, session ->
            session.recordTurn(TurnRecord(null, "m", "tool_use", emptyList(), paid, TURN_LATENCY))
            started?.complete(Unit)
            awaitCancellation()
        }
        step
    }

    @Test
    fun aTierCutOffByTheEngineDeadlineIsInTheTraceWithItsTokens() = runTest {
        NoNetworkGuard.during {
            val listener = RecordingEventListener()
            val pipeline = commandPipeline {
                tier(ScriptedStrategy(StrategyId("slow"), hangAfterOneTurn(null)))
                gate = ScriptedGate.admitAll()
                commitSink = RecordingCommitSink()
                policy = TierPolicySource.fixed(TierPolicy { commandTimeoutMillis = DEADLINE_MILLIS })
                this.listener = listener
            }

            val outcome = pipeline.execute(CommandInput("hi"))

            assertTrue((outcome as CommandOutcome.Failed).reason is FailureReason.Timeout)
            val attempt = outcome.trace.attempts.single()
            assertEquals("timeout", attempt.outcome)
            assertEquals(StrategyId("slow"), attempt.strategy)
            assertTrue(attempt.failure is FailureReason.Timeout)
            assertEquals(1, attempt.turns.size)
            assertEquals(paidTotal, outcome.trace.usage.total)
            val finished = listener.events.filterIsInstance<PipelineEvent.TierFinished>().single()
            assertEquals("timeout", finished.attempt.outcome)
        }
    }

    @Test
    fun aCancelledTierReachesTheTraceTheSinkSeesOnClose() = runTest {
        NoNetworkGuard.during {
            val started = CompletableDeferred<Unit>()
            val sink = RecordingCommitSink()
            val pipeline = commandPipeline {
                tier(ScriptedStrategy(StrategyId("slow"), hangAfterOneTurn(started)))
                gate = ScriptedGate.admitAll()
                commitSink = sink
            }

            val call = async { pipeline.execute(CommandInput("hi")) }
            started.await()
            call.cancel()
            try {
                call.await()
            } catch (expected: CancellationException) {
                assertTrue(call.isCancelled)
            }

            val trace = sink.closes.single().trace
            assertEquals("cancelled", trace.attempts.single().outcome)
            assertEquals(paidTotal, trace.usage.total)
        }
    }

    private fun escalatingWithCarry() = ScriptedStrategy(
        StrategyId("first"),
        { _, _ -> StrategyOutcome.Escalate(EscalationReason.NoToolCall(), Any()) },
    )

    @Test
    fun aTierCutOffByTheDeadlineReportsWhetherItReceivedACarry() = runTest {
        NoNetworkGuard.during {
            val withCarry = commandPipeline {
                tier(escalatingWithCarry())
                tier(ScriptedStrategy(StrategyId("slow"), hangAfterOneTurn(null)))
                gate = ScriptedGate.admitAll()
                commitSink = RecordingCommitSink()
                policy = TierPolicySource.fixed(TierPolicy { commandTimeoutMillis = DEADLINE_MILLIS })
            }.execute(CommandInput("hi"))
            assertEquals(listOf("escalated", "timeout"), withCarry.trace.attempts.map { it.outcome })
            assertEquals(listOf(false, true), withCarry.trace.attempts.map { it.carryIn })

            val withoutCarry = commandPipeline {
                tier(ScriptedStrategy(StrategyId("slow"), hangAfterOneTurn(null)))
                gate = ScriptedGate.admitAll()
                commitSink = RecordingCommitSink()
                policy = TierPolicySource.fixed(TierPolicy { commandTimeoutMillis = DEADLINE_MILLIS })
            }.execute(CommandInput("hi"))
            assertEquals("timeout", withoutCarry.trace.attempts.single().outcome)
            assertEquals(false, withoutCarry.trace.attempts.single().carryIn)
        }
    }

    @Test
    fun aCancelledTierThatReceivedACarryReportsIt() = runTest {
        NoNetworkGuard.during {
            val started = CompletableDeferred<Unit>()
            val sink = RecordingCommitSink()
            val pipeline = commandPipeline {
                tier(escalatingWithCarry())
                tier(ScriptedStrategy(StrategyId("slow"), hangAfterOneTurn(started)))
                gate = ScriptedGate.admitAll()
                commitSink = sink
            }

            val call = async { pipeline.execute(CommandInput("hi")) }
            started.await()
            call.cancel()
            try {
                call.await()
            } catch (expected: CancellationException) {
                assertTrue(call.isCancelled)
            }

            val attempts = sink.closes.single().trace.attempts
            assertEquals(listOf("escalated", "cancelled"), attempts.map { it.outcome })
            assertEquals(listOf(false, true), attempts.map { it.carryIn })
        }
    }

    private companion object {
        const val DEADLINE_MILLIS = 100L
        const val TURN_LATENCY = 5L
    }
}
