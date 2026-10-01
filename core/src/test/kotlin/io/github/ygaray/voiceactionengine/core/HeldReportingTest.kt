package io.github.ygaray.voiceactionengine.core

import io.github.ygaray.voiceactionengine.core.commit.ActionKind
import io.github.ygaray.voiceactionengine.core.commit.DispatchResult
import io.github.ygaray.voiceactionengine.core.commit.GateDecision
import io.github.ygaray.voiceactionengine.core.commit.StepResult
import io.github.ygaray.voiceactionengine.core.commit.ToolStep
import io.github.ygaray.voiceactionengine.core.commit.heldForConfirmationContent
import io.github.ygaray.voiceactionengine.core.pipeline.CommandOutcome
import io.github.ygaray.voiceactionengine.core.pipeline.commandPipeline
import io.github.ygaray.voiceactionengine.core.strategy.StrategyOutcome
import io.github.ygaray.voiceactionengine.core.telemetry.TraceCode
import io.github.ygaray.voiceactionengine.core.testing.FakeMutation
import io.github.ygaray.voiceactionengine.core.testing.NoNetworkGuard
import io.github.ygaray.voiceactionengine.core.testing.RecordingCommitSink
import io.github.ygaray.voiceactionengine.core.testing.ScriptedGate
import io.github.ygaray.voiceactionengine.core.testing.ScriptedStrategy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** A hold writes nothing, is reported once per mutation, and fails closed when the gate itself fails. */
class HeldReportingTest {
    private val tierId = StrategyId("tier-one")
    private val runId = "run-held"
    private val heldJson = """{"applied":false,"status":"held_for_confirmation"}"""

    private class HoldReason

    @Test
    fun aHoldReportsTheMutationAndHandsTheStrategyTheExactHeldBytes() = runTest {
        NoNetworkGuard.during {
            val reason = HoldReason()
            val context = Any()
            val write = FakeMutation("write_note", { StepResult("saved") }, mapOf("id" to "n1"), context)
            var seen: DispatchResult? = null
            val sink = RecordingCommitSink()
            val pipeline = commandPipeline {
                tier(
                    ScriptedStrategy(tierId, { _, session ->
                        seen = session.submit(ToolStep.Mutation(write))
                        StrategyOutcome.Completed("asked")
                    }),
                )
                gate = ScriptedGate.holdAll(reason, "held")
                commitSink = sink
                runIds = { runId }
            }

            val outcome = pipeline.execute(CommandInput("add a note"))

            val dispatched = seen!!
            assertTrue(dispatched.held)
            assertFalse(dispatched.isError)
            assertEquals(heldJson, dispatched.contentForModel)
            assertEquals(0, write.applyCount)
            val action = sink.actions.single().action
            assertEquals(0, action.position)
            assertEquals(ActionKind.HELD, action.kind)
            assertFalse(action.applied)
            assertEquals("held", action.appOutcomeToken)
            assertSame(context, action.context)
            assertEquals(mapOf("id" to "n1"), action.targetIds)
            val proposal = outcome.held.single()
            assertSame(reason, proposal.reason)
            assertEquals("held", proposal.appOutcomeToken)
            assertEquals(listOf<Any>(write), proposal.mutations)
            assertEquals(runId, proposal.runId)
            assertTrue(outcome is CommandOutcome.Completed)
            assertEquals(listOf(action), outcome.executed)
            assertTrue(outcome.commits.isEmpty())
        }
    }

    @Test
    fun theHeldContentFunctionReturnsTheExactLiteral() {
        assertEquals(heldJson, heldForConfirmationContent())
    }

    @Test
    fun aGateThatThrowsFailsClosedWithATraceCodeAndNoInventedReason() = runTest {
        NoNetworkGuard.during {
            val write = FakeMutation("write_note", StepResult("saved"))
            var seen: DispatchResult? = null
            val sink = RecordingCommitSink()
            val pipeline = commandPipeline {
                tier(
                    ScriptedStrategy(tierId, { _, session ->
                        seen = session.submit(ToolStep.Mutation(write))
                        StrategyOutcome.Completed("done")
                    }),
                )
                gate = ScriptedGate { error("gate exploded") }
                commitSink = sink
                runIds = { runId }
            }

            val outcome = pipeline.execute(CommandInput("add a note"))

            assertEquals(0, write.applyCount)
            assertEquals(heldJson, seen!!.contentForModel)
            assertTrue(seen!!.held)
            val action = sink.actions.single().action
            assertEquals(ActionKind.HELD, action.kind)
            assertFalse(action.applied)
            assertNull(action.appOutcomeToken)
            val proposal = outcome.held.single()
            assertNull(proposal.reason)
            assertNull(proposal.appOutcomeToken)
            assertTrue(outcome.trace.codes.contains(TraceCode.GATE_ERROR))
            assertTrue(outcome is CommandOutcome.Completed)
            assertTrue(outcome.commits.isEmpty())
        }
    }

    @Test
    fun aTwoMutationHoldReportsTwoActionsAndOneProposal() = runTest {
        NoNetworkGuard.during {
            val first = FakeMutation("first", StepResult("a"))
            val second = FakeMutation("second", StepResult("b"))
            val sink = RecordingCommitSink()
            val pipeline = commandPipeline {
                tier(
                    ScriptedStrategy(tierId, { _, session ->
                        session.submit(ToolStep.Mutation(listOf(first, second)))
                        StrategyOutcome.Completed("done")
                    }),
                )
                gate = ScriptedGate { GateDecision.Hold("why", "tok") }
                commitSink = sink
                runIds = { runId }
            }

            val outcome = pipeline.execute(CommandInput("two"))

            assertEquals(listOf(0, 1), sink.actions.map { it.action.position })
            assertEquals(listOf("first", "second"), sink.actions.map { it.action.toolName })
            assertEquals(listOf("tok", "tok"), sink.actions.map { it.action.appOutcomeToken })
            assertEquals(listOf(0, 0), listOf(first.applyCount, second.applyCount))
            assertEquals(1, outcome.held.size)
            assertEquals(listOf<Any>(first, second), outcome.held.single().mutations)
            assertEquals(2, outcome.executed.size)
            assertTrue(outcome.commits.isEmpty())
        }
    }
}
