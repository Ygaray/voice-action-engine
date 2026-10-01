package io.github.ygaray.voiceactionengine.core

import io.github.ygaray.voiceactionengine.core.failure.FailureReason
import io.github.ygaray.voiceactionengine.core.pipeline.CommandOutcome
import io.github.ygaray.voiceactionengine.core.pipeline.commandPipeline
import io.github.ygaray.voiceactionengine.core.strategy.StrategyOutcome
import io.github.ygaray.voiceactionengine.core.testing.NoNetworkGuard
import io.github.ygaray.voiceactionengine.core.testing.RecordingCommitSink
import io.github.ygaray.voiceactionengine.core.testing.ScriptedGate
import io.github.ygaray.voiceactionengine.core.testing.ScriptedStrategy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** A throwing run id maker or clock is a typed failure, never an exception, and no sink hears of it. */
class RunSetupGuardTest {
    private fun result(sink: RecordingCommitSink, ids: () -> String, clock: () -> Long) = commandPipeline {
        tier(ScriptedStrategy(StrategyId("t"), { _, _ -> StrategyOutcome.Completed("done") }))
        gate = ScriptedGate.admitAll()
        commitSink = sink
        runIds = ids
        this.clock = clock
    }

    @Test
    fun aThrowingRunIdMakerGivesAFailedOutcomeAndNoSinkCall() = runTest {
        NoNetworkGuard.during {
            val sink = RecordingCommitSink()
            val pipeline = result(sink, { error("id maker broke") }, { 0L })
            val outcome = pipeline.execute(CommandInput("hi", parentRunId = "p"))

            val failed = outcome as CommandOutcome.Failed
            assertEquals(FailureReason.Unexpected("IllegalStateException"), failed.reason)
            assertTrue(failed.executed.isEmpty())
            assertEquals("p", failed.parentRunId)
            assertTrue(sink.closes.isEmpty())
        }
    }

    @Test
    fun aThrowingClockGivesAFailedOutcomeAndNoSinkCall() = runTest {
        NoNetworkGuard.during {
            val sink = RecordingCommitSink()
            val outcome = result(sink, { "run-1" }, { error("clock broke") }).execute(CommandInput("hi"))

            assertEquals(FailureReason.Unexpected("IllegalStateException"), (outcome as CommandOutcome.Failed).reason)
            assertTrue(sink.closes.isEmpty())
        }
    }
}
