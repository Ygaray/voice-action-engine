package io.github.ygaray.voiceactionengine.core

import io.github.ygaray.voiceactionengine.core.commit.ActionKind
import io.github.ygaray.voiceactionengine.core.commit.GateDecision
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
import org.junit.Assert.assertFalse
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

    @Test
    fun theAmendedOverloadCarriesTheHeldRunId() = runTest {
        NoNetworkGuard.during {
            val sink = RecordingCommitSink()
            val holding = pipeline(sink, ScriptedGate.holdAll("later"), write("a"))
            val original = holding.execute(CommandInput("save it"))

            val child = holding.commitHeld(original.held.single(), listOf(write("x"), write("y")))

            val childEvents = sink.actions.filter { it.runId == child.runId }
            assertEquals(listOf("x", "y"), childEvents.map { it.action.toolName })
            assertTrue(childEvents.all { it.heldRunId == original.runId })
            assertTrue(childEvents.all { it.parentRunId == original.runId })
        }
    }

    @Test
    fun twoProposalsOfOneRunResolveUnderTheSameHeldRunId() = runTest {
        NoNetworkGuard.during {
            val sink = RecordingCommitSink()
            val holding = pipeline(sink, ScriptedGate.holdAll("later"), write("a"), write("b"))
            val original = holding.execute(CommandInput("two things"))
            assertEquals(2, original.held.size)

            val first = holding.commitHeld(original.held[0])
            val second = holding.commitHeld(original.held[1])

            assertTrue(first.runId != second.runId)
            val childEvents = sink.actions.filter { it.runId == first.runId || it.runId == second.runId }
            assertEquals(2, childEvents.size)
            assertTrue(childEvents.all { it.heldRunId == original.runId })
            assertEquals(setOf(first.runId, second.runId), childEvents.map { it.runId }.toSet())
        }
    }

    @Test
    fun aClarificationReplyKeepsItsParentAndHasNoHeldRunId() = runTest {
        NoNetworkGuard.during {
            val sink = RecordingCommitSink()
            val replying = pipeline(sink, ScriptedGate.admitAll(), write("a"))

            replying.execute(CommandInput("reply", parentRunId = "p"))

            assertTrue(sink.actions.isNotEmpty())
            assertTrue(sink.actions.all { it.parentRunId == "p" })
            assertTrue(sink.actions.all { it.heldRunId == null })
        }
    }

    @Test
    fun aHeldChildOfAReplyGroupsUnderTheReply() = runTest {
        NoNetworkGuard.during {
            val sink = RecordingCommitSink()
            val holding = pipeline(sink, ScriptedGate.holdAll("later"), write("a"))
            val reply = holding.execute(CommandInput("reply", parentRunId = "p"))
            assertEquals("p", sink.actions.single().parentRunId)
            assertNull(sink.actions.single().heldRunId)

            val child = holding.commitHeld(reply.held.single())

            val childEvent = sink.actions.single { it.runId == child.runId }
            assertEquals(reply.runId, childEvent.heldRunId)
            assertEquals(reply.runId, childEvent.parentRunId)
        }
    }

    @Test
    fun aThrowingApplyInsideTheChildIsAnErrorWithTheHeldRunId() = runTest {
        NoNetworkGuard.during {
            val sink = RecordingCommitSink()
            val throwing = FakeMutation("boom", { error("apply exploded") })
            val holding = pipeline(sink, ScriptedGate.holdAll("later"), throwing)
            val original = holding.execute(CommandInput("save it"))

            val child = holding.commitHeld(original.held.single())

            val childEvent = sink.actions.single { it.runId == child.runId }
            assertEquals(ActionKind.IS_ERROR, childEvent.action.kind)
            assertTrue(childEvent.action.applied)
            assertEquals(original.runId, childEvent.heldRunId)
        }
    }

    @Test
    fun aGateThatLaterAdmitsStillLeavesTheFirstRunWithoutAHeldRunId() = runTest {
        NoNetworkGuard.during {
            val sink = RecordingCommitSink()
            val gate = ScriptedGate.sequence(null, GateDecision.Hold("later"), GateDecision.Admit())
            val mixed = pipeline(sink, gate, write("a"), write("b"))

            mixed.execute(CommandInput("one held, one applied"))

            assertEquals(listOf(ActionKind.HELD, ActionKind.COMMITTED), sink.actions.map { it.action.kind })
            assertTrue(sink.actions.all { it.heldRunId == null })
        }
    }

    @Test
    fun theEventPrintsOnlyWhetherAHeldRunIdIsSet() = runTest {
        NoNetworkGuard.during {
            val sink = RecordingCommitSink()
            val holding = pipeline(sink, ScriptedGate.holdAll("later"), write("a"))
            val original = holding.execute(CommandInput("save it"))
            val child = holding.commitHeld(original.held.single())

            val childText = sink.actions.single { it.runId == child.runId }.toString()
            val heldText = sink.actions.single { it.runId == original.runId }.toString()

            assertTrue(childText, childText.contains("heldRunId=set"))
            assertFalse(childText, childText.contains("heldRunId=${original.runId}"))
            assertTrue(heldText, heldText.contains("heldRunId=null"))
        }
    }
}
