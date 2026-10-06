package io.github.ygaray.voiceactionengine.core

import io.github.ygaray.voiceactionengine.core.commit.ActionEvent
import io.github.ygaray.voiceactionengine.core.commit.ActionKind
import io.github.ygaray.voiceactionengine.core.commit.CommitSink
import io.github.ygaray.voiceactionengine.core.commit.ExecutedAction
import io.github.ygaray.voiceactionengine.core.commit.StepResult
import io.github.ygaray.voiceactionengine.core.commit.ToolStep
import io.github.ygaray.voiceactionengine.core.commit.compositeSink
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
import io.github.ygaray.voiceactionengine.core.testing.RecordingSink
import io.github.ygaray.voiceactionengine.core.testing.ScriptedGate
import io.github.ygaray.voiceactionengine.core.testing.ScriptedStrategy
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import kotlin.coroutines.cancellation.CancellationException

/** A composite sink calls every child in order and never lets one child break a sibling or the run. */
class CompositeSinkTest {
    private fun write(name: String) = FakeMutation(name, StepResult("saved", false, "ok", emptyMap()))

    private fun event(): ActionEvent = ActionEvent(
        "run-1", null, ExecutedAction(1, ActionKind.COMMITTED, true, null, "tool", emptyMap(), null, null), null,
    )

    private suspend fun runWith(
        sink: CommitSink,
        mutation: FakeMutation,
        eventListener: PipelineEventListener? = null,
    ): CommandOutcome =
        commandPipeline {
            tier(
                ScriptedStrategy(StrategyId("only"), { _, session ->
                    session.submit(ToolStep.Mutation(mutation))
                    StrategyOutcome.Completed("done")
                }),
            )
            gate = ScriptedGate.admitAll()
            commitSink = sink
            if (eventListener != null) listener = eventListener
        }.execute(CommandInput("do it"))

    @Test
    fun everyChildHearsEveryEventInTheGivenOrderAndASlowChildFinishesFirst() = runTest {
        NoNetworkGuard.during {
            val log = RecordingSink<String>()
            val first = RecordingCommitSink(
                onActionHook = { log.record("1:action:start"); delay(SLOW_MILLIS); log.record("1:action:end") },
                onRunClosedHook = { _, _ -> log.record("1:closed") },
            )
            val second = RecordingCommitSink(
                onActionHook = { log.record("2:action") },
                onRunClosedHook = { _, _ -> log.record("2:closed") },
            )

            val outcome = runWith(compositeSink(first, second), write("a"))

            assertTrue(outcome is CommandOutcome.Completed)
            assertEquals(
                listOf("1:action:start", "1:action:end", "2:action", "1:closed", "2:closed"),
                log.events,
            )
        }
    }

    @Test
    fun aThrowingFirstChildNeverStopsItsSiblingAndTheRunIsUnchanged() = runTest {
        NoNetworkGuard.during {
            val recorder = RecordingCommitSink()
            val thrower = RecordingCommitSink(onActionHook = { error("journal exploded") })
            val mutation = write("a")

            val outcome = runWith(compositeSink(thrower, recorder), mutation)

            assertTrue(outcome is CommandOutcome.Completed)
            assertEquals(1, mutation.applyCount)
            assertEquals(1, recorder.actions.size)
            assertEquals(1, recorder.closes.size)
            assertTrue(outcome.trace.codes.contains(TraceCode.SINK_ERROR))
        }
    }

    @Test
    fun aThrowingLastChildStillLetsTheEarlierOnesHearEverything() = runTest {
        NoNetworkGuard.during {
            val recorder = RecordingCommitSink()
            val thrower = RecordingCommitSink(onActionHook = { error("ui exploded") })

            val outcome = runWith(compositeSink(recorder, thrower), write("a"))

            assertTrue(outcome is CommandOutcome.Completed)
            assertEquals(1, recorder.actions.size)
            assertEquals(1, recorder.closes.size)
            assertEquals(1, thrower.closes.size)
            assertTrue(outcome.trace.codes.contains(TraceCode.SINK_ERROR))
        }
    }

    @Test
    fun everyChildThrowingStillLeavesTheRunCompletedWithSinkError() = runTest {
        NoNetworkGuard.during {
            val a = RecordingCommitSink(
                onActionHook = { error("a") },
                onRunClosedHook = { _, _ -> error("a closed") },
            )
            val b = RecordingCommitSink(
                onActionHook = { error("b") },
                onRunClosedHook = { _, _ -> error("b closed") },
            )
            val mutation = write("a")

            val outcome = runWith(compositeSink(a, b), mutation)

            assertTrue(outcome is CommandOutcome.Completed)
            assertEquals(1, mutation.applyCount)
            assertEquals(listOf(1, 1), listOf(a.actions.size, b.actions.size))
            assertEquals(listOf(1, 1), listOf(a.closes.size, b.closes.size))
            assertTrue(outcome.trace.codes.contains(TraceCode.SINK_ERROR))
        }
    }

    @Test
    fun aChildThrowingOnlyOnRunClosedStillLetsItsSiblingHearTheClose() = runTest {
        NoNetworkGuard.during {
            val thrower = RecordingCommitSink(onRunClosedHook = { _, _ -> error("close exploded") })
            val recorder = RecordingCommitSink()
            val listener = RecordingEventListener()

            val outcome = runWith(compositeSink(thrower, recorder), write("a"), listener)

            assertTrue(outcome is CommandOutcome.Completed)
            assertEquals(1, recorder.closes.size)
            assertEquals(outcome.runId, recorder.closedRunIds.single())
            // The close happens after the outcome's trace is taken, so the code reaches the event stream only.
            val codes = listener.events.filterIsInstance<PipelineEvent.EngineCode>().map { it.code }
            assertTrue(codes.contains(TraceCode.SINK_ERROR))
        }
    }

    @Test
    fun aForeignCancellationFromAChildUnderThePipelineIsAFaultNotAnAbort() = runTest {
        NoNetworkGuard.during {
            val foreign = RecordingCommitSink(onActionHook = { throw CancellationException("scope closed") })
            val recorder = RecordingCommitSink()
            val mutation = write("a")

            val outcome = runWith(compositeSink(foreign, recorder), mutation)

            assertTrue(outcome is CommandOutcome.Completed)
            assertEquals(1, mutation.applyCount)
            assertEquals(1, recorder.actions.size)
            assertTrue(outcome.trace.codes.contains(TraceCode.SINK_ERROR))
        }
    }

    @Test
    fun aRealCancellationOfTheCallerPropagatesAndLaterChildrenAreNotCalled() = runTest {
        val started = CompletableDeferred<Unit>()
        val first = RecordingCommitSink(onActionHook = { started.complete(Unit); awaitCancellation() })
        val second = RecordingCommitSink()
        val composite = compositeSink(first, second)

        val job = launch { composite.onAction(event()) }
        started.await()
        job.cancel()
        job.join()

        assertTrue(job.isCancelled)
        assertTrue(second.actions.isEmpty())
    }

    @Test
    fun theThrownFailureNamesOnlyCountsNeverAChildsText() = runTest {
        val canary = "canary-secret-transcript"
        val thrower = RecordingCommitSink(onActionHook = { error(canary) })
        val composite = compositeSink(thrower, RecordingCommitSink())

        try {
            composite.onAction(event())
            fail("the composite must report the failed child")
        } catch (e: IllegalStateException) {
            assertEquals("compositeSink: 1 of 2 child sinks failed", e.message)
            assertFalse(e.toString().contains(canary))
            assertTrue(e.cause == null)
        }
    }

    @Test
    fun noSinkIsRejectedAndTheCompositePrintsOnlyItsChildCount() {
        try {
            compositeSink()
            fail("an empty composite must be rejected")
        } catch (e: IllegalArgumentException) {
            assertNotNull(e.message)
        }
        val composite = compositeSink(RecordingCommitSink(), RecordingCommitSink())
        assertEquals("CompositeSink(children=2)", composite.toString())
    }

    @Test
    fun changingTheSourceArrayAfterTheCallChangesNothing() = runTest {
        val first = RecordingCommitSink()
        val second = RecordingCommitSink()
        val source: Array<CommitSink> = arrayOf(first, second)
        val composite = compositeSink(*source)

        source[1] = first
        composite.onAction(event())

        assertEquals(1, first.actions.size)
        assertEquals(1, second.actions.size)
        assertEquals("CompositeSink(children=2)", composite.toString())
    }

    private companion object {
        const val SLOW_MILLIS = 50L
    }
}
