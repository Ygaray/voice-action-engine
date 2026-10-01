package io.github.ygaray.voiceactionengine.core

import io.github.ygaray.voiceactionengine.core.commit.RunTermination
import io.github.ygaray.voiceactionengine.core.commit.StepResult
import io.github.ygaray.voiceactionengine.core.commit.ToolStep
import io.github.ygaray.voiceactionengine.core.failure.BudgetBound
import io.github.ygaray.voiceactionengine.core.failure.EscalationReason
import io.github.ygaray.voiceactionengine.core.failure.FailureDetails
import io.github.ygaray.voiceactionengine.core.failure.FailureReason
import io.github.ygaray.voiceactionengine.core.pipeline.CommandOutcome
import io.github.ygaray.voiceactionengine.core.pipeline.CommandPipeline
import io.github.ygaray.voiceactionengine.core.pipeline.commandPipeline
import io.github.ygaray.voiceactionengine.core.strategy.CommandSession
import io.github.ygaray.voiceactionengine.core.strategy.StrategyOutcome
import io.github.ygaray.voiceactionengine.core.testing.FakeMutation
import io.github.ygaray.voiceactionengine.core.testing.NoNetworkGuard
import io.github.ygaray.voiceactionengine.core.testing.RecordingCommitSink
import io.github.ygaray.voiceactionengine.core.testing.RecordingSink
import io.github.ygaray.voiceactionengine.core.testing.ScriptedGate
import io.github.ygaray.voiceactionengine.core.testing.ScriptedStrategy
import io.github.ygaray.voiceactionengine.core.testing.StrategyStep
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import kotlin.coroutines.cancellation.CancellationException

/** The run closes exactly once on each of the five exit paths, after every action and before nothing else. */
class RunClosedPathsTest {

    private fun write() = FakeMutation("write", StepResult("saved", false, "ok", emptyMap()))

    private fun pipeline(sink: RecordingCommitSink, vararg steps: StrategyStep): CommandPipeline = commandPipeline {
        steps.forEachIndexed { index, step -> tier(ScriptedStrategy(StrategyId("tier$index"), step)) }
        gate = ScriptedGate.admitAll()
        commitSink = sink
    }

    private fun assertActionsPrecedeTheClose(log: RecordingSink<String>) {
        val entries = log.events
        val closedAt = entries.indexOfFirst { it.startsWith("sink:closed:") }
        assertTrue("the close must be logged: $entries", closedAt >= 0)
        assertEquals("exactly one close: $entries", 1, entries.count { it.startsWith("sink:closed:") })
        val after = entries.drop(closedAt + 1)
        assertTrue("an action followed the close: $entries", after.none { it.startsWith("sink:action") })
    }

    @Test
    fun doneExitClosesOnce() = runTest {
        NoNetworkGuard.during {
            val log = RecordingSink<String>()
            val sink = RecordingCommitSink(log)
            val outcome = pipeline(sink, { _, session ->
                session.submit(ToolStep.Mutation(write()))
                StrategyOutcome.Completed("saved")
            }).execute(CommandInput("save it"))

            assertTrue(outcome is CommandOutcome.Completed)
            assertEquals(1, sink.closes.size)
            val done = sink.closes.single() as RunTermination.Done
            assertFalse(done.partial)
            assertEquals(1, done.executed.size)
            assertEquals(listOf("write"), done.commits.map { it.toolName })
            assertEquals(outcome.runId, sink.closedRunIds.single())
            assertActionsPrecedeTheClose(log)
        }
    }

    @Test
    fun cancelledExitClosesOnce() = runTest {
        NoNetworkGuard.during {
            val log = RecordingSink<String>()
            var hookFinished = false
            val sink = RecordingCommitSink(
                log,
                onRunClosedHook = { _, _ ->
                    delay(HOOK_DELAY_MILLIS)
                    hookFinished = true
                },
            )
            val committed = CompletableDeferred<Unit>()
            val call = async {
                pipeline(sink, { _, session ->
                    session.submit(ToolStep.Mutation(write()))
                    committed.complete(Unit)
                    awaitCancellation()
                }).execute(CommandInput("save it"))
            }
            committed.await()
            call.cancel()
            try {
                call.await()
                fail("a cancelled call must not return an outcome")
            } catch (expected: CancellationException) {
                assertTrue(call.isCancelled)
            }

            assertEquals(1, sink.closes.size)
            val cancelled = sink.closes.single() as RunTermination.Cancelled
            assertEquals(listOf("write"), cancelled.commits.map { it.toolName })
            assertEquals(1, cancelled.executed.size)
            assertTrue("the suspending close hook must run to completion", hookFinished)
            assertActionsPrecedeTheClose(log)
        }
    }

    @Test
    fun budgetExceededExitClosesOnce() = runTest {
        NoNetworkGuard.during {
            val log = RecordingSink<String>()
            val sink = RecordingCommitSink(log)
            val outcome = pipeline(sink, { _, session ->
                session.submit(ToolStep.Mutation(write()))
                StrategyOutcome.Failed(FailureReason.BudgetExceeded(BudgetBound.ITERATIONS))
            }).execute(CommandInput("save it"))

            assertTrue(outcome is CommandOutcome.Failed)
            assertEquals(1, sink.closes.size)
            val failed = sink.closes.single() as RunTermination.Failed
            assertTrue(failed.reason is FailureReason.BudgetExceeded)
            assertEquals(listOf("write"), failed.commits.map { it.toolName })
            assertEquals(1, failed.executed.size)
            assertActionsPrecedeTheClose(log)
        }
    }

    @Test
    fun providerErrorExitClosesOnce() = runTest {
        NoNetworkGuard.during {
            val log = RecordingSink<String>()
            val sink = RecordingCommitSink(log)
            val details = FailureDetails(HTTP_UNAUTHORIZED, "authentication_error", "req_1")
            val outcome = pipeline(sink, { _, _ ->
                StrategyOutcome.Failed(FailureReason.Auth(), details)
            }).execute(CommandInput("save it"))

            val failedOutcome = outcome as CommandOutcome.Failed
            assertEquals("req_1", failedOutcome.details?.requestId)
            assertEquals(1, sink.closes.size)
            val failed = sink.closes.single() as RunTermination.Failed
            assertTrue(failed.reason is FailureReason.Auth)
            assertEquals(details, failed.details)
            assertTrue(failed.executed.isEmpty())
            assertActionsPrecedeTheClose(log)
        }
    }

    @Test
    fun escalationExhaustedExitClosesOnce() = runTest {
        NoNetworkGuard.during {
            val log = RecordingSink<String>()
            val sink = RecordingCommitSink(log)
            val last = EscalationReason.NoToolCall()
            val outcome = pipeline(
                sink,
                { _, _ -> StrategyOutcome.Escalate(EscalationReason.ModelDeclined()) },
                { _, _ -> StrategyOutcome.Escalate(last) },
            ).execute(CommandInput("save it"))

            assertTrue(outcome is CommandOutcome.Unhandled)
            assertEquals(1, sink.closes.size)
            val exhausted = sink.closes.single() as RunTermination.Exhausted
            assertTrue(exhausted.lastReason === last)
            assertTrue(exhausted.executed.isEmpty())
            assertActionsPrecedeTheClose(log)
        }
    }

    @Test
    fun aSessionKeptPastTheCloseCanNeverWriteOrDeliver() = runTest {
        NoNetworkGuard.during {
            val sink = RecordingCommitSink()
            var kept: CommandSession? = null
            pipeline(sink, { _, session ->
                kept = session
                StrategyOutcome.Completed("done")
            }).execute(CommandInput("save it"))
            val late = write()

            try {
                checkNotNull(kept).submit(ToolStep.Mutation(late))
                fail("a closed run must refuse a late submit")
            } catch (expected: IllegalStateException) {
                assertTrue(expected.message.orEmpty().contains("closed"))
            }

            assertEquals(0, late.applyCount)
            assertTrue(sink.actions.isEmpty())
            assertEquals(1, sink.closes.size)
        }
    }

    @Test
    fun aSinkWhoseCloseHookThrowsStillLetsExecuteReturnAfterOneAttempt() = runTest {
        NoNetworkGuard.during {
            val sink = RecordingCommitSink(onRunClosedHook = { _, _ -> throw IllegalStateException("journal is down") })
            val outcome = pipeline(sink, { _, _ -> StrategyOutcome.Completed("done") }).execute(CommandInput("hi"))

            assertTrue(outcome is CommandOutcome.Completed)
            assertEquals(1, sink.closes.size)
            assertNull((outcome as CommandOutcome.Completed).terminalCall)
            assertEquals("done", outcome.reply)
        }
    }

    private companion object {
        const val HOOK_DELAY_MILLIS = 100L
        const val HTTP_UNAUTHORIZED = 401
    }
}
