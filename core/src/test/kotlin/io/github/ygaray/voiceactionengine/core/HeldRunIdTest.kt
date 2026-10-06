package io.github.ygaray.voiceactionengine.core

import io.github.ygaray.voiceactionengine.core.commit.ActionKind
import io.github.ygaray.voiceactionengine.core.commit.StepResult
import io.github.ygaray.voiceactionengine.core.commit.ToolStep
import io.github.ygaray.voiceactionengine.core.pipeline.CommandPipeline
import io.github.ygaray.voiceactionengine.core.pipeline.commandPipeline
import io.github.ygaray.voiceactionengine.core.strategy.StrategyOutcome
import io.github.ygaray.voiceactionengine.core.testing.FakeMutation
import io.github.ygaray.voiceactionengine.core.testing.NoNetworkGuard
import io.github.ygaray.voiceactionengine.core.testing.RecordingCommitSink
import io.github.ygaray.voiceactionengine.core.testing.ScriptedGate
import io.github.ygaray.voiceactionengine.core.testing.ScriptedStrategy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/** Only an action applied by commitHeld names the run that held it. */
class HeldRunIdTest {

    private val ids = AtomicInteger()

    private fun write(name: String) = FakeMutation(name, StepResult("saved", false, "ok", emptyMap()))

    private fun pipeline(
        sink: RecordingCommitSink,
        gate: ScriptedGate,
        vararg writes: FakeMutation,
    ): CommandPipeline = commandPipeline {
        tier(
            ScriptedStrategy(StrategyId("only"), { _, session ->
                writes.forEach { session.submit(ToolStep.Mutation(it)) }
                StrategyOutcome.Completed("done")
            }),
        )
        this.gate = gate
        commitSink = sink
        runIds = { "run-${ids.incrementAndGet()}" }
    }

    @Test
    fun aCommitHeldChildCarriesTheHeldRunsIdAndANormalRunCarriesNone() = runTest {
        NoNetworkGuard.during {
            val sink = RecordingCommitSink()
            val holding = pipeline(sink, ScriptedGate.holdAll("later"), write("a"))

            val original = holding.execute(CommandInput("save it"))
            val held = original.held.single()
            val heldEvent = sink.actions.single()
            assertEquals("run-1", heldEvent.runId)
            assertEquals(ActionKind.HELD, heldEvent.action.kind)
            assertNull(heldEvent.heldRunId)

            val child = holding.commitHeld(held)

            val childEvent = sink.actions.single { it.runId == child.runId }
            assertEquals("run-2", childEvent.runId)
            assertEquals(ActionKind.COMMITTED, childEvent.action.kind)
            assertEquals("run-1", childEvent.parentRunId)
            assertEquals("run-1", childEvent.heldRunId)

            val normalSink = RecordingCommitSink()
            pipeline(normalSink, ScriptedGate.admitAll(), write("b")).execute(CommandInput("just do it"))
            assertEquals(ActionKind.COMMITTED, normalSink.actions.single().action.kind)
            assertNull(normalSink.actions.single().heldRunId)
        }
    }
}
