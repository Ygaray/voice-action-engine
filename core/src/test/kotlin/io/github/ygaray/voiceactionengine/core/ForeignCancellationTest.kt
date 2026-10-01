package io.github.ygaray.voiceactionengine.core

import io.github.ygaray.voiceactionengine.core.commit.ActionKind
import io.github.ygaray.voiceactionengine.core.commit.RunTermination
import io.github.ygaray.voiceactionengine.core.commit.StepResult
import io.github.ygaray.voiceactionengine.core.commit.ToolStep
import io.github.ygaray.voiceactionengine.core.pipeline.CommandOutcome
import io.github.ygaray.voiceactionengine.core.pipeline.commandPipeline
import io.github.ygaray.voiceactionengine.core.strategy.StrategyOutcome
import io.github.ygaray.voiceactionengine.core.telemetry.PipelineEvent
import io.github.ygaray.voiceactionengine.core.telemetry.PipelineEventListener
import io.github.ygaray.voiceactionengine.core.telemetry.TraceCode
import io.github.ygaray.voiceactionengine.core.testing.FakeMutation
import io.github.ygaray.voiceactionengine.core.testing.NoNetworkGuard
import io.github.ygaray.voiceactionengine.core.testing.RecordingCommitSink
import io.github.ygaray.voiceactionengine.core.testing.RecordingEventListener
import io.github.ygaray.voiceactionengine.core.testing.ScriptedGate
import io.github.ygaray.voiceactionengine.core.testing.ScriptedStrategy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.coroutines.cancellation.CancellationException

/**
 * A CancellationException thrown by app code the engine runs under NonCancellable is not the caller's cancellation. It
 * is a fault like any other: the outcome is still returned, every sibling still applies and the close still completes.
 */
class ForeignCancellationTest {
    private fun ok(name: String) = FakeMutation(name, StepResult("done", false, "ok", emptyMap()))

    @Test
    fun aSinkThrowingCancellationOnCloseStillLetsExecuteReturnItsOutcomeAndTheFinalEvent() = runTest {
        NoNetworkGuard.during {
            val listener = RecordingEventListener()
            val sink = RecordingCommitSink(onRunClosedHook = { _, _ -> throw CancellationException("scope closed") })
            val pipeline = commandPipeline {
                tier(ScriptedStrategy(StrategyId("t"), { _, _ -> StrategyOutcome.Completed("done") }))
                gate = ScriptedGate.admitAll()
                commitSink = sink
                this.listener = listener
            }

            val outcome = pipeline.execute(CommandInput("hi"))

            assertTrue(outcome is CommandOutcome.Completed)
            assertEquals(1, sink.closes.size)
            assertTrue("the close must be the last event", listener.events.last() is PipelineEvent.RunClosed)
            val codes = listener.events.filterIsInstance<PipelineEvent.EngineCode>().map { it.code }
            assertTrue(codes.contains(TraceCode.SINK_ERROR))
        }
    }

    @Test
    fun aSinkThrowingCancellationOnAnActionDoesNotAbortTheBatch() = runTest {
        NoNetworkGuard.during {
            val sink = RecordingCommitSink(onActionHook = { throw CancellationException("scope closed") })
            val a = ok("a")
            val b = ok("b")
            val pipeline = commandPipeline {
                tier(
                    ScriptedStrategy(StrategyId("t"), { _, session ->
                        session.submit(ToolStep.Mutation(listOf(a, b)))
                        StrategyOutcome.Completed("done")
                    }),
                )
                gate = ScriptedGate.admitAll()
                commitSink = sink
            }

            val outcome = pipeline.execute(CommandInput("two things"))

            assertTrue(outcome is CommandOutcome.Completed)
            assertEquals(listOf(1, 1), listOf(a.applyCount, b.applyCount))
            assertEquals(listOf(ActionKind.COMMITTED, ActionKind.COMMITTED), outcome.executed.map { it.kind })
            assertTrue(outcome.trace.codes.contains(TraceCode.SINK_ERROR))
            val done = sink.closes.single() as RunTermination.Done
            assertEquals(2, done.executed.size)
        }
    }

    @Test
    fun aListenerThrowingCancellationNeverEscapesAndIsTheListenerErrorCode() = runTest {
        NoNetworkGuard.during {
            val received = mutableListOf<PipelineEvent>()
            val listener = PipelineEventListener {
                received.add(it)
                throw CancellationException("not the caller's")
            }
            val sink = RecordingCommitSink()
            val pipeline = commandPipeline {
                tier(ScriptedStrategy(StrategyId("t"), { _, _ -> StrategyOutcome.Completed("done") }))
                gate = ScriptedGate.admitAll()
                commitSink = sink
                this.listener = listener
            }

            val outcome = pipeline.execute(CommandInput("hi"))

            assertTrue(outcome is CommandOutcome.Completed)
            assertTrue(outcome.trace.codes.contains(TraceCode.LISTENER_ERROR))
            assertEquals(1, sink.closes.size)
            assertTrue(received.last() is PipelineEvent.RunClosed)
        }
    }
}
