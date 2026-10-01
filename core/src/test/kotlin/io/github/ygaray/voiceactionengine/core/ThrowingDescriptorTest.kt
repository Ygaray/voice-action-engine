package io.github.ygaray.voiceactionengine.core

import io.github.ygaray.voiceactionengine.core.commit.ActionKind
import io.github.ygaray.voiceactionengine.core.commit.PendingMutation
import io.github.ygaray.voiceactionengine.core.commit.RunTermination
import io.github.ygaray.voiceactionengine.core.commit.StepResult
import io.github.ygaray.voiceactionengine.core.commit.ToolStep
import io.github.ygaray.voiceactionengine.core.pipeline.CommandOutcome
import io.github.ygaray.voiceactionengine.core.pipeline.commandPipeline
import io.github.ygaray.voiceactionengine.core.strategy.StrategyOutcome
import io.github.ygaray.voiceactionengine.core.telemetry.TraceCode
import io.github.ygaray.voiceactionengine.core.testing.NoNetworkGuard
import io.github.ygaray.voiceactionengine.core.testing.RecordingCommitSink
import io.github.ygaray.voiceactionengine.core.testing.ScriptedGate
import io.github.ygaray.voiceactionengine.core.testing.ScriptedStrategy
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/** The descriptor getters of a change are app code; one that throws costs its own descriptor, never the record. */
class ThrowingDescriptorTest {
    private class BadGetters(private val behavior: suspend () -> StepResult) : PendingMutation {
        val applies = AtomicInteger()
        override val toolName: String get() = error("name getter broke")
        override val targetIds: Map<String, String> get() = error("ids getter broke")
        override val context: Any? get() = error("context getter broke")
        override suspend fun apply(): StepResult {
            applies.incrementAndGet()
            return behavior()
        }
    }

    private fun saved() = StepResult("saved", false, "ok", mapOf("id" to "n1"))

    @Test
    fun aChangeWithThrowingGettersIsStillAppliedRecordedAndDelivered() = runTest {
        NoNetworkGuard.during {
            val bad = BadGetters { saved() }
            val sink = RecordingCommitSink()
            val pipeline = commandPipeline {
                tier(
                    ScriptedStrategy(StrategyId("t"), { _, session ->
                        session.submit(ToolStep.Mutation(bad))
                        StrategyOutcome.Completed("done")
                    }),
                )
                gate = ScriptedGate.admitAll()
                commitSink = sink
            }

            val outcome = pipeline.execute(CommandInput("write"))

            assertTrue(outcome is CommandOutcome.Completed)
            assertEquals(1, bad.applies.get())
            val action = sink.actions.single().action
            assertEquals(ActionKind.COMMITTED, action.kind)
            assertEquals("unknown", action.toolName)
            assertEquals(mapOf("id" to "n1"), action.targetIds)
            assertTrue(outcome.trace.codes.contains(TraceCode.APPLY_ERROR))
        }
    }

    @Test
    fun aCancelledApplyIsStillJournaledWhenTheGettersThrow() = runTest {
        NoNetworkGuard.during {
            val started = CompletableDeferred<Unit>()
            val bad = BadGetters {
                started.complete(Unit)
                awaitCancellation()
            }
            val sink = RecordingCommitSink()
            val pipeline = commandPipeline {
                tier(
                    ScriptedStrategy(StrategyId("t"), { _, session ->
                        session.submit(ToolStep.Mutation(bad))
                        StrategyOutcome.Completed("never")
                    }),
                )
                gate = ScriptedGate.admitAll()
                commitSink = sink
            }

            val job = launch { pipeline.execute(CommandInput("write")) }
            started.await()
            job.cancel()
            job.join()

            assertTrue("the caller's cancellation must still be what ends the call", job.isCancelled)
            assertEquals(ActionKind.IS_ERROR, sink.actions.single().action.kind)
            assertTrue(sink.closes.single() is RunTermination.Cancelled)
        }
    }

    @Test
    fun aHeldChangeWithThrowingGettersIsReportedInFull() = runTest {
        NoNetworkGuard.during {
            val bad = BadGetters { saved() }
            val sink = RecordingCommitSink()
            val pipeline = commandPipeline {
                tier(
                    ScriptedStrategy(StrategyId("t"), { _, session ->
                        session.submit(ToolStep.Mutation(bad))
                        StrategyOutcome.Completed("held")
                    }),
                )
                gate = ScriptedGate.holdAll(reason = "needs confirm")
                commitSink = sink
            }

            val outcome = pipeline.execute(CommandInput("write"))

            assertEquals(0, bad.applies.get())
            assertEquals(1, outcome.held.size)
            assertEquals(listOf(ActionKind.HELD), sink.actions.map { it.action.kind })
            assertEquals("unknown", sink.actions.single().action.toolName)
        }
    }
}
