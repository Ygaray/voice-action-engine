package io.github.ygaray.voiceactionengine.core

import io.github.ygaray.voiceactionengine.core.commit.RunTermination
import io.github.ygaray.voiceactionengine.core.commit.StepResult
import io.github.ygaray.voiceactionengine.core.commit.ToolStep
import io.github.ygaray.voiceactionengine.core.failure.FailureReason
import io.github.ygaray.voiceactionengine.core.pipeline.CommandOutcome
import io.github.ygaray.voiceactionengine.core.pipeline.CommandPipeline
import io.github.ygaray.voiceactionengine.core.pipeline.TierPolicy
import io.github.ygaray.voiceactionengine.core.pipeline.TierPolicySource
import io.github.ygaray.voiceactionengine.core.pipeline.commandPipeline
import io.github.ygaray.voiceactionengine.core.strategy.StrategyOutcome
import io.github.ygaray.voiceactionengine.core.testing.FakeMutation
import io.github.ygaray.voiceactionengine.core.testing.NoNetworkGuard
import io.github.ygaray.voiceactionengine.core.testing.RecordingCommitSink
import io.github.ygaray.voiceactionengine.core.testing.ScriptedGate
import io.github.ygaray.voiceactionengine.core.testing.ScriptedStrategy
import io.github.ygaray.voiceactionengine.core.testing.StrategyStep
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import kotlin.coroutines.cancellation.CancellationException

class NeverThrowTest {

    private fun pipeline(
        step: StrategyStep,
        sink: RecordingCommitSink,
        policy: TierPolicy = TierPolicy.DEFAULT,
    ): CommandPipeline = commandPipeline {
        tier(ScriptedStrategy(StrategyId("only"), step))
        this.policy = TierPolicySource.fixed(policy)
        gate = ScriptedGate.admitAll()
        commitSink = sink
    }

    @Test
    fun aThrowingStrategyBecomesFailedUnexpectedAndTheRunClosesOnce() = runTest {
        NoNetworkGuard.during {
            val sink = RecordingCommitSink()
            val outcome = pipeline({ _, _ -> throw IllegalStateException("secret transcript text") }, sink)
                .execute(CommandInput("hi"))
            val failed = outcome as CommandOutcome.Failed
            assertEquals(FailureReason.Unexpected("IllegalStateException"), failed.reason)
            assertFalse(failed.toString().contains("secret"))
            assertEquals(listOf("strategy_error"), outcome.trace.codes.map { it.value })
            val closed = sink.closes.single()
            assertTrue(closed is RunTermination.Failed)
        }
    }

    @Test
    fun aLinkageErrorIsCollapsedLikeAnException() = runTest {
        NoNetworkGuard.during {
            val sink = RecordingCommitSink()
            val outcome = pipeline({ _, _ -> throw NoSuchMethodError("okhttp3.Foo.bar") }, sink)
                .execute(CommandInput("hi"))
            assertEquals(FailureReason.Unexpected("NoSuchMethodError"), (outcome as CommandOutcome.Failed).reason)
            assertEquals(1, sink.closes.size)
        }
    }

    @Test
    fun aLeakedInnerTimeoutIsATimeoutNotAnEscape() = runTest {
        NoNetworkGuard.during {
            val sink = RecordingCommitSink()
            val outcome = pipeline(
                { _, _ ->
                    withTimeout(INNER_TIMEOUT_MILLIS) { awaitCancellation() }
                },
                sink,
            ).execute(CommandInput("hi"))
            assertEquals(FailureReason.Timeout(), (outcome as CommandOutcome.Failed).reason)
            assertEquals(1, sink.closes.size)
        }
    }

    @Test
    fun theEngineDeadlineIsATimeoutNeverANetworkFailureAndKeepsEarlierCommits() = runTest {
        NoNetworkGuard.during {
            val sink = RecordingCommitSink()
            val write = FakeMutation("write_note", StepResult("saved"))
            val outcome = pipeline(
                { _, session ->
                    session.submit(ToolStep.Mutation(write))
                    delay(SLOW_MILLIS)
                    StrategyOutcome.Completed("too late")
                },
                sink,
                TierPolicy { commandTimeoutMillis = DEADLINE_MILLIS },
            ).execute(CommandInput("hi"))
            val failed = outcome as CommandOutcome.Failed
            assertEquals(FailureReason.Timeout(), failed.reason)
            assertFalse(failed.reason is FailureReason.Network)
            assertEquals(1, outcome.executed.size)
            assertEquals(1, outcome.commits.size)
            assertTrue(outcome.trace.codes.map { it.value }.contains("engine_timeout"))
            val closed = sink.closes.single()
            assertTrue(closed is RunTermination.Failed)
            assertEquals(1, closed.executed.size)
        }
    }

    @Test
    fun cancellingTheCallerPropagatesAndClosesOnceAsCancelledEvenWhenTheSinkSuspends() = runTest {
        NoNetworkGuard.during {
            val started = CompletableDeferred<Unit>()
            var hookFinished = false
            val sink = RecordingCommitSink(
                onRunClosedHook = { _, _ ->
                    delay(SINK_DELAY_MILLIS)
                    hookFinished = true
                },
            )
            val call = async {
                pipeline(
                    { _, _ ->
                        started.complete(Unit)
                        awaitCancellation()
                    },
                    sink,
                ).execute(CommandInput("hi"))
            }
            started.await()
            call.cancel()
            try {
                call.await()
                fail("the cancelled call must not return an outcome")
            } catch (expected: CancellationException) {
                assertTrue(call.isCancelled)
            }
            assertEquals(1, sink.closes.size)
            assertTrue(sink.closes.single() is RunTermination.Cancelled)
            assertTrue("the suspending close hook must run to completion", hookFinished)
        }
    }

    @Test
    fun aStrategyThrowingAForeignCancellationWhileActiveBecomesFailedAndClosesOnceAsFailed() = runTest {
        NoNetworkGuard.during {
            val sink = RecordingCommitSink()
            val outcome = pipeline({ _, _ -> throw CancellationException("stop") }, sink)
                .execute(CommandInput("hi"))
            val failed = outcome as CommandOutcome.Failed
            assertEquals(FailureReason.Unexpected("CancellationException"), failed.reason)
            assertEquals(1, sink.closes.size)
            assertTrue(sink.closes.single() is RunTermination.Failed)
        }
    }

    @Test
    fun anAssertionErrorPropagatesAndTheRunStillClosesOnceAsUnexpectedError() = runTest {
        NoNetworkGuard.during {
            val sink = RecordingCommitSink()
            val thrown = AssertionError("not collapsed")
            try {
                pipeline({ _, _ -> throw thrown }, sink).execute(CommandInput("hi"))
                fail("an Error other than a LinkageError must propagate")
            } catch (expected: AssertionError) {
                assertSame(thrown, expected)
            }
            val closed = sink.closes.single() as RunTermination.Failed
            assertEquals(FailureReason.Unexpected("Error"), closed.reason)
        }
    }

    private companion object {
        const val INNER_TIMEOUT_MILLIS = 10L
        const val DEADLINE_MILLIS = 1_000L
        const val SLOW_MILLIS = 5_000L
        const val SINK_DELAY_MILLIS = 100L
    }
}
