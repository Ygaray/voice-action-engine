package io.github.ygaray.voiceactionengine.core

import io.github.ygaray.voiceactionengine.core.commit.GateDecision
import io.github.ygaray.voiceactionengine.core.commit.StepResult
import io.github.ygaray.voiceactionengine.core.commit.ToolStep
import io.github.ygaray.voiceactionengine.core.pipeline.commandPipeline
import io.github.ygaray.voiceactionengine.core.strategy.StrategyOutcome
import io.github.ygaray.voiceactionengine.core.testing.FakeMutation
import io.github.ygaray.voiceactionengine.core.testing.NoNetworkGuard
import io.github.ygaray.voiceactionengine.core.testing.RecordingCommitSink
import io.github.ygaray.voiceactionengine.core.testing.ScriptedGate
import io.github.ygaray.voiceactionengine.core.testing.ScriptedStrategy
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.coroutines.cancellation.CancellationException

/** No write starts once the caller is cancelled or the run is closed, even when the gate or a strategy lags behind. */
class WriteGuardsTest {
    private fun write() = FakeMutation("write", StepResult("saved", false, "ok", emptyMap()))

    @Test
    fun aStrategyThatSwallowsItsCancellationCannotKeepWriting() = runTest {
        NoNetworkGuard.during {
            val late = write()
            val started = CompletableDeferred<Unit>()
            val sink = RecordingCommitSink()
            val pipeline = commandPipeline {
                tier(
                    ScriptedStrategy(StrategyId("t"), { _, session ->
                        started.complete(Unit)
                        try {
                            awaitCancellation()
                        } catch (cancelled: CancellationException) {
                            runCatching { session.submit(ToolStep.Mutation(late)) }
                            throw cancelled
                        }
                    }),
                )
                gate = ScriptedGate.admitAll()
                commitSink = sink
            }

            val job = launch { pipeline.execute(CommandInput("write")) }
            started.await()
            job.cancel()
            job.join()

            assertEquals(0, late.applyCount)
            assertTrue(sink.actions.isEmpty())
            assertEquals(1, sink.closes.size)
        }
    }

    @Test
    fun aChangeAdmittedWhileTheRunClosesIsNeverAppliedOrDelivered() = runTest {
        NoNetworkGuard.during {
            val late = write()
            val gateEntered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val sink = RecordingCommitSink()
            var leaked: Result<*>? = null
            // A scope outside the run, standing in for a coroutine a strategy leaked past the end of its tier.
            val leakScope = CoroutineScope(StandardTestDispatcher(testScheduler))
            val pipeline = commandPipeline {
                tier(
                    ScriptedStrategy(StrategyId("t"), { _, session ->
                        leakScope.launch {
                            leaked = runCatching { session.submit(ToolStep.Mutation(late)) }
                        }
                        gateEntered.await()
                        StrategyOutcome.Completed("done")
                    }),
                )
                gate = ScriptedGate {
                    gateEntered.complete(Unit)
                    release.await()
                    GateDecision.Admit()
                }
                commitSink = sink
            }

            val outcome = pipeline.execute(CommandInput("write"))
            release.complete(Unit)
            testScheduler.advanceUntilIdle()

            assertTrue(outcome.executed.isEmpty())
            assertEquals(0, late.applyCount)
            assertTrue(sink.actions.isEmpty())
            assertTrue("leaked=$leaked", leaked?.exceptionOrNull() is IllegalStateException)
            leakScope.cancel()
        }
    }
}
