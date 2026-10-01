package io.github.ygaray.voiceactionengine.core

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
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/** Committing held changes never throws or hangs on a bad argument or a throwing app hook. */
class HeldCommitGuardsTest {
    private fun ok(name: String) = FakeMutation(name, StepResult("saved", false, "ok", emptyMap()))

    private fun pipeline(sink: RecordingCommitSink, write: FakeMutation, runIds: () -> String): CommandPipeline =
        commandPipeline {
            tier(
                ScriptedStrategy(StrategyId("only"), { _, session ->
                    session.submit(ToolStep.Mutation(write))
                    StrategyOutcome.Completed("asked")
                }),
            )
            gate = ScriptedGate.holdAll("confirm")
            commitSink = sink
            this.runIds = runIds
        }

    @Test
    fun aThrowingRunIdMakerFailsTheCommitAndNeverLeavesTheProposalClaimedForEver() = runTest {
        NoNetworkGuard.during {
            val calls = AtomicInteger()
            val sink = RecordingCommitSink()
            val write = ok("write")
            val pipeline = pipeline(sink, write) {
                check(calls.incrementAndGet() == 1) { "id maker broke" }
                "run-1"
            }
            val held = pipeline.execute(CommandInput("save it")).held.single()

            val first = pipeline.commitHeld(held)
            val second = withTimeout(WAIT_MILLIS) { async { pipeline.commitHeld(held) }.await() }

            val failed = first as CommandOutcome.Failed
            assertEquals(FailureReason.Unexpected("IllegalStateException"), failed.reason)
            assertSame(first, second)
            assertEquals(0, write.applyCount)
            assertEquals(listOf("run-1"), sink.closedRunIds)
        }
    }

    private companion object {
        const val WAIT_MILLIS = 1_000L
    }
}
