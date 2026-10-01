package io.github.ygaray.voiceactionengine.core

import io.github.ygaray.voiceactionengine.core.commit.ActionKind
import io.github.ygaray.voiceactionengine.core.commit.GateDecision
import io.github.ygaray.voiceactionengine.core.commit.RunTermination
import io.github.ygaray.voiceactionengine.core.commit.StepResult
import io.github.ygaray.voiceactionengine.core.commit.ToolStep
import io.github.ygaray.voiceactionengine.core.failure.FailureReason
import io.github.ygaray.voiceactionengine.core.pipeline.CommandOutcome
import io.github.ygaray.voiceactionengine.core.pipeline.CommandPipeline
import io.github.ygaray.voiceactionengine.core.pipeline.commandPipeline
import io.github.ygaray.voiceactionengine.core.strategy.StrategyOutcome
import io.github.ygaray.voiceactionengine.core.testing.FakeMutation
import io.github.ygaray.voiceactionengine.core.testing.NoNetworkGuard
import io.github.ygaray.voiceactionengine.core.testing.RecordingCommitSink
import io.github.ygaray.voiceactionengine.core.testing.ScriptedGate
import io.github.ygaray.voiceactionengine.core.testing.ScriptedStrategy
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.cancellation.CancellationException

/** Defer mode: the gate holds, and the app commits later through commitHeld as a linked, idempotent child run. */
class DeferModeTest {

    private val ids = AtomicInteger()

    private fun ok(name: String) = FakeMutation(name, StepResult("saved", false, "ok", emptyMap()))

    private fun pipeline(gate: ScriptedGate, sink: RecordingCommitSink, vararg writes: FakeMutation): CommandPipeline =
        commandPipeline {
            tier(
                ScriptedStrategy(StrategyId("only"), { _, session ->
                    writes.forEach { session.submit(ToolStep.Mutation(it)) }
                    StrategyOutcome.Completed("asked")
                }),
            )
            this.gate = gate
            commitSink = sink
            runIds = { "run-${ids.incrementAndGet()}" }
        }

    @Test
    fun commitHeldSkipsTheGateAndRunsAsALinkedChildWithItsOwnClose() = runTest {
        NoNetworkGuard.during {
            val gate = ScriptedGate.holdAll("confirm")
            val sink = RecordingCommitSink()
            val write = ok("write")
            val pipeline = pipeline(gate, sink, write)
            val original = pipeline.execute(CommandInput("save it"))
            val held = original.held.single()
            val callsBefore = gate.calls

            val child = pipeline.commitHeld(held)

            assertEquals("the gate must not be asked again", callsBefore, gate.calls)
            val completed = child as CommandOutcome.Completed
            assertFalse(completed.partial)
            assertNotEquals(original.runId, child.runId)
            assertEquals(original.runId, child.parentRunId)
            assertEquals(1, write.applyCount)
            val event = sink.actions.last()
            assertEquals(child.runId, event.runId)
            assertEquals(0, event.action.position)
            assertEquals(ActionKind.COMMITTED, event.action.kind)
            assertEquals(listOf(original.runId, child.runId), sink.closedRunIds)
            assertTrue(sink.closes.last() is RunTermination.Done)
            assertEquals(1, sink.closedRunIds.count { it == original.runId })
        }
    }

    @Test
    fun commitHeldWithAnAmendedListAppliesTheAmendedMutationAndNeverTheHeldOne() = runTest {
        NoNetworkGuard.during {
            val sink = RecordingCommitSink()
            val write = ok("write")
            val amended = ok("amended")
            val pipeline = pipeline(ScriptedGate.holdAll(), sink, write)
            val held = pipeline.execute(CommandInput("save it")).held.single()

            val child = pipeline.commitHeld(held, listOf(amended))

            assertEquals(0, write.applyCount)
            assertEquals(1, amended.applyCount)
            assertEquals(listOf("amended"), child.commits.map { it.toolName })
        }
    }

    @Test
    fun aSecondCommitHeldReturnsTheFirstOutcomeWithNoApplyAndNoEvent() = runTest {
        NoNetworkGuard.during {
            val sink = RecordingCommitSink()
            val write = ok("write")
            val pipeline = pipeline(ScriptedGate.holdAll(), sink, write)
            val held = pipeline.execute(CommandInput("save it")).held.single()
            val first = pipeline.commitHeld(held)
            val actionsAfterFirst = sink.actions.size
            val closesAfterFirst = sink.closes.size

            val second = pipeline.commitHeld(held)
            val third = pipeline.commitHeld(held, listOf(ok("other")))

            assertSame(first, second)
            assertSame(first, third)
            assertEquals(1, write.applyCount)
            assertEquals(actionsAfterFirst, sink.actions.size)
            assertEquals(closesAfterFirst, sink.closes.size)
        }
    }

    @Test
    fun twoConcurrentCommitHeldCallsApplyOnceAndReturnTheSameOutcome() = runTest {
        NoNetworkGuard.during {
            val sink = RecordingCommitSink()
            val slow = FakeMutation("write", {
                delay(APPLY_MILLIS)
                StepResult("saved")
            })
            val pipeline = pipeline(ScriptedGate.holdAll(), sink, slow)
            val held = pipeline.execute(CommandInput("save it")).held.single()

            val a = async { pipeline.commitHeld(held) }
            val b = async { pipeline.commitHeld(held) }

            assertSame(a.await(), b.await())
            assertEquals(1, slow.applyCount)
            assertEquals(2, sink.closes.size)
        }
    }

    @Test
    fun aCommitHeldCancelledMidApplyConsumesTheProposalAndNeverAppliesAgain() = runTest {
        NoNetworkGuard.during {
            val sink = RecordingCommitSink()
            val started = CompletableDeferred<Unit>()
            val stuck = FakeMutation("write", {
                started.complete(Unit)
                awaitCancellation()
            })
            val pipeline = pipeline(ScriptedGate.holdAll(), sink, stuck)
            val held = pipeline.execute(CommandInput("save it")).held.single()

            val first = async { pipeline.commitHeld(held) }
            started.await()
            first.cancel()
            try {
                first.await()
                fail("a cancelled call must not return an outcome")
            } catch (expected: CancellationException) {
                assertTrue(first.isCancelled)
            }
            val later = pipeline.commitHeld(held)

            val failed = later as CommandOutcome.Failed
            assertEquals(FailureReason.Other("commit_held_cancelled"), failed.reason)
            assertEquals(listOf(ActionKind.IS_ERROR), later.executed.map { it.kind })
            assertTrue(later.trace.codes.map { it.value }.contains("commit_held_cancelled"))
            assertEquals(1, stuck.applyCount)
            assertTrue(sink.closes.last() is RunTermination.Cancelled)
            assertEquals(2, sink.closes.size)
        }
    }

    @Test
    fun admittedItemsCommitInTheOriginalRunAndOnlyTheHeldOneGoesThroughCommitHeld() = runTest {
        NoNetworkGuard.during {
            val sink = RecordingCommitSink()
            val a = ok("a")
            val b = ok("b")
            val gate = ScriptedGate.sequence(null, GateDecision.Admit(), GateDecision.Hold("later"))
            val pipeline = pipeline(gate, sink, a, b)

            val original = pipeline.execute(CommandInput("save both"))

            assertEquals(listOf("a"), original.commits.map { it.toolName })
            val held = original.held.single()
            assertEquals(listOf("b"), held.mutations.map { it.toolName })

            val child = pipeline.commitHeld(held)

            assertEquals(listOf("b"), child.commits.map { it.toolName })
            assertEquals(1, a.applyCount)
            assertEquals(1, b.applyCount)
        }
    }

    @Test
    fun aCommitHeldWhoseApplyThrowsIsAnIsErrorEventInACompletedChild() = runTest {
        NoNetworkGuard.during {
            val sink = RecordingCommitSink()
            val throwing = FakeMutation("write", { throw IllegalStateException("disk full") })
            val pipeline = pipeline(ScriptedGate.holdAll(), sink, throwing)
            val held = pipeline.execute(CommandInput("save it")).held.single()

            val child = pipeline.commitHeld(held)

            assertTrue(child is CommandOutcome.Completed)
            assertEquals(listOf(ActionKind.IS_ERROR), child.executed.map { it.kind })
            assertEquals(ActionKind.IS_ERROR, sink.actions.last().action.kind)
            assertEquals(child.runId, sink.actions.last().runId)
            assertEquals(1, throwing.applyCount)
        }
    }

    private companion object {
        const val APPLY_MILLIS = 100L
    }
}
