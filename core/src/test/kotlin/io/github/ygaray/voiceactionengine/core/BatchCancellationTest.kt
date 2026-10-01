package io.github.ygaray.voiceactionengine.core

import io.github.ygaray.voiceactionengine.core.commit.ActionKind
import io.github.ygaray.voiceactionengine.core.commit.RunTermination
import io.github.ygaray.voiceactionengine.core.commit.StepResult
import io.github.ygaray.voiceactionengine.core.commit.ToolStep
import io.github.ygaray.voiceactionengine.core.pipeline.CommandOutcome
import io.github.ygaray.voiceactionengine.core.pipeline.commandPipeline
import io.github.ygaray.voiceactionengine.core.strategy.StrategyOutcome
import io.github.ygaray.voiceactionengine.core.telemetry.TraceCode
import io.github.ygaray.voiceactionengine.core.testing.FakeMutation
import io.github.ygaray.voiceactionengine.core.testing.NoNetworkGuard
import io.github.ygaray.voiceactionengine.core.testing.RecordingCommitSink
import io.github.ygaray.voiceactionengine.core.testing.ScriptedGate
import io.github.ygaray.voiceactionengine.core.testing.ScriptedStrategy
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** A caller cancelled between the items of one batch stops the batch: the started item stays, the rest never run. */
class BatchCancellationTest {
    private val runId = "run-cancel"

    private fun ok(name: String) = FakeMutation(name, StepResult("done $name", false, "tok-$name", emptyMap()))

    @Test
    fun aCancelBetweenBatchItemsStopsTheRestAndKeepsTheCommittedOne() = runTest {
        NoNetworkGuard.during {
            val itemA = ok("a")
            val itemB = ok("b")
            val running = CompletableDeferred<Job>()
            val sink = RecordingCommitSink(onActionHook = { event ->
                if (event.action.kind == ActionKind.COMMITTED) running.await().cancel()
            })
            val pipeline = commandPipeline {
                tier(
                    ScriptedStrategy(StrategyId("tier-one"), { _, session ->
                        session.submit(ToolStep.Mutation(listOf(itemA, itemB)))
                        StrategyOutcome.Completed("batch done")
                    }),
                )
                gate = ScriptedGate.admitAll()
                commitSink = sink
                runIds = { runId }
            }

            val job = launch { pipeline.execute(CommandInput("write two")) }
            running.complete(job)
            job.join()

            assertTrue(job.isCancelled)
            assertEquals(1, itemA.applyCount)
            assertEquals(0, itemB.applyCount)
            assertEquals(listOf("a"), sink.actions.map { it.action.toolName })
            assertEquals(listOf(ActionKind.COMMITTED), sink.actions.map { it.action.kind })
            val closed = sink.closes.single()
            assertTrue(closed is RunTermination.Cancelled)
            assertEquals(listOf("a"), closed.commits.map { it.toolName })
            assertEquals(listOf("a"), closed.executed.map { it.toolName })
        }
    }

    @Test
    fun aCancelBetweenHeldBatchItemsStopsTheRestAndKeepsTheCommittedOne() = runTest {
        NoNetworkGuard.during {
            val itemA = ok("a")
            val itemB = ok("b")
            val running = CompletableDeferred<Job>()
            val sink = RecordingCommitSink(onActionHook = { event ->
                if (event.action.kind == ActionKind.COMMITTED) running.await().cancel()
            })
            val pipeline = commandPipeline {
                tier(
                    ScriptedStrategy(StrategyId("tier-one"), { _, session ->
                        session.submit(ToolStep.Mutation(listOf(itemA, itemB)))
                        StrategyOutcome.Completed("asked")
                    }),
                )
                gate = ScriptedGate.holdAll("confirm")
                commitSink = sink
                runIds = { runId }
            }
            val held = pipeline.execute(CommandInput("write two")).held.single()
            assertEquals(0, itemA.applyCount)

            val job = launch { pipeline.commitHeld(held) }
            running.complete(job)
            job.join()

            assertTrue(job.isCancelled)
            assertEquals(1, itemA.applyCount)
            assertEquals(0, itemB.applyCount)
            val closed = sink.closes.last()
            assertTrue(closed is RunTermination.Cancelled)
            assertEquals(listOf("a"), closed.commits.map { it.toolName })
            assertTrue(closed.trace.codes.contains(TraceCode.COMMIT_HELD_CANCELLED))
            assertEquals(2, sink.closes.size)
        }
    }

    @Test
    fun anUncancelledBatchStillAppliesEveryItem() = runTest {
        NoNetworkGuard.during {
            val itemA = ok("a")
            val itemB = ok("b")
            val sink = RecordingCommitSink()
            val pipeline = commandPipeline {
                tier(
                    ScriptedStrategy(StrategyId("tier-one"), { _, session ->
                        session.submit(ToolStep.Mutation(listOf(itemA, itemB)))
                        StrategyOutcome.Completed("batch done")
                    }),
                )
                gate = ScriptedGate.admitAll()
                commitSink = sink
                runIds = { runId }
            }

            val outcome = pipeline.execute(CommandInput("write two"))

            assertTrue(outcome is CommandOutcome.Completed)
            assertEquals(1, itemA.applyCount)
            assertEquals(1, itemB.applyCount)
            assertEquals(listOf("a", "b"), sink.actions.map { it.action.toolName })
            assertTrue(sink.closes.single() is RunTermination.Done)
        }
    }
}
