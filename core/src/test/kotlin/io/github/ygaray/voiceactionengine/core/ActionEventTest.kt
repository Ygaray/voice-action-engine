package io.github.ygaray.voiceactionengine.core

import io.github.ygaray.voiceactionengine.core.commit.ActionKind
import io.github.ygaray.voiceactionengine.core.commit.DispatchResult
import io.github.ygaray.voiceactionengine.core.commit.FinishedKind
import io.github.ygaray.voiceactionengine.core.commit.StepResult
import io.github.ygaray.voiceactionengine.core.commit.ToolStep
import io.github.ygaray.voiceactionengine.core.pipeline.CommandOutcome
import io.github.ygaray.voiceactionengine.core.pipeline.CommandPipeline
import io.github.ygaray.voiceactionengine.core.pipeline.commandPipeline
import io.github.ygaray.voiceactionengine.core.strategy.StrategyOutcome
import io.github.ygaray.voiceactionengine.core.testing.FakeMutation
import io.github.ygaray.voiceactionengine.core.testing.NoNetworkGuard
import io.github.ygaray.voiceactionengine.core.testing.RecordingCommitSink
import io.github.ygaray.voiceactionengine.core.testing.RecordingSink
import io.github.ygaray.voiceactionengine.core.testing.ScriptedGate
import io.github.ygaray.voiceactionengine.core.testing.ScriptedStrategy
import io.github.ygaray.voiceactionengine.core.testing.StrategyStep
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** What reaches the commit sink for each kind of step, with the exact fields, tokens and positions. */
class ActionEventTest {
    private val tierId = StrategyId("tier-one")
    private val runId = "run-events"

    private fun pipelineOf(gate: ScriptedGate, sink: RecordingCommitSink, vararg steps: StrategyStep): CommandPipeline =
        commandPipeline {
            tier(ScriptedStrategy(tierId, *steps))
            this.gate = gate
            commitSink = sink
            runIds = { runId }
        }

    @Test
    fun aCommittedEventCarriesEveryFieldAndTheTokenByteForByte() = runTest {
        NoNetworkGuard.during {
            val token = "PREVIEW_ delete ✓ 42"
            val context = Any()
            val write = FakeMutation(
                toolName = "delete_items",
                behavior = { StepResult("deleted", false, token, mapOf("id" to "r2")) },
                targetIds = mapOf("id" to "m1", "folder" to "f"),
                context = context,
            )
            val sink = RecordingCommitSink()
            val pipeline = pipelineOf(ScriptedGate.admitAll(), sink, { _, session ->
                session.submit(ToolStep.Mutation(write))
                StrategyOutcome.Completed("ok")
            })

            pipeline.execute(CommandInput("delete", parentRunId = "parent-9"))

            val event = sink.actions.single()
            assertEquals(runId, event.runId)
            assertEquals("parent-9", event.parentRunId)
            val action = event.action
            assertEquals(0, action.position)
            assertEquals(ActionKind.COMMITTED, action.kind)
            assertTrue(action.applied)
            assertTrue(action.mutating)
            assertEquals("delete_items", action.toolName)
            assertEquals(token, action.appOutcomeToken)
            assertEquals(mapOf("id" to "r2", "folder" to "f"), action.targetIds)
            assertSame(context, action.context)
        }
    }

    @Test
    fun aFinishedPreviewIsReportedWithoutTheGate() = runTest {
        NoNetworkGuard.during {
            val context = Any()
            val gate = ScriptedGate.admitAll()
            val sink = RecordingCommitSink()
            var seen: DispatchResult? = null
            val pipeline = pipelineOf(gate, sink, { _, session ->
                val result = StepResult("would delete 42", false, "PREVIEW_delete_42_items", mapOf("n" to "42"))
                seen = session.submit(ToolStep.Finished("delete_items", FinishedKind.PREVIEW, result, context))
                StrategyOutcome.Completed("shown")
            })

            val outcome = pipeline.execute(CommandInput("delete"))

            assertEquals(0, gate.calls)
            val action = sink.actions.single().action
            assertEquals(ActionKind.PREVIEW, action.kind)
            assertFalse(action.applied)
            assertFalse(action.mutating)
            assertEquals("PREVIEW_delete_42_items", action.appOutcomeToken)
            assertEquals(mapOf("n" to "42"), action.targetIds)
            assertSame(context, action.context)
            assertEquals("would delete 42", seen!!.contentForModel)
            assertFalse(seen!!.isError)
            assertFalse(seen!!.held)
            assertEquals(listOf(action), outcome.executed)
            assertTrue(outcome.commits.isEmpty())
        }
    }

    @Test
    fun aFinishedErrorIsReportedAsAnUnappliedErrorWithoutTheGate() = runTest {
        NoNetworkGuard.during {
            val gate = ScriptedGate.admitAll()
            val sink = RecordingCommitSink()
            var seen: DispatchResult? = null
            val pipeline = pipelineOf(gate, sink, { _, session ->
                val result = StepResult("not allowed", false, "REJECTED_x", emptyMap())
                seen = session.submit(ToolStep.Finished("delete_items", FinishedKind.ERROR, result))
                StrategyOutcome.Completed("no")
            })

            val outcome = pipeline.execute(CommandInput("delete"))

            assertEquals(0, gate.calls)
            val action = sink.actions.single().action
            assertEquals(ActionKind.IS_ERROR, action.kind)
            assertFalse(action.applied)
            assertEquals("REJECTED_x", action.appOutcomeToken)
            assertTrue(seen!!.isError)
            assertEquals("not allowed", seen!!.contentForModel)
            assertTrue(outcome.commits.isEmpty())
        }
    }

    @Test
    fun aFinishedReadIsNeverReported() = runTest {
        NoNetworkGuard.during {
            val gate = ScriptedGate.admitAll()
            val sink = RecordingCommitSink()
            var seen: DispatchResult? = null
            val pipeline = pipelineOf(gate, sink, { _, session ->
                seen = session.submit(ToolStep.Finished("search", FinishedKind.READ, StepResult("3 notes")))
                StrategyOutcome.Completed("found")
            })

            val outcome = pipeline.execute(CommandInput("find"))

            assertEquals(0, gate.calls)
            assertTrue(sink.actions.isEmpty())
            assertTrue(outcome.executed.isEmpty())
            assertEquals("3 notes", seen!!.contentForModel)
            assertTrue(seen!!.actions.isEmpty())
            assertTrue(outcome is CommandOutcome.Completed)
        }
    }

    @Test
    fun positionsRiseAcrossTurnsAndEventsArriveAsEachActionHappens() = runTest {
        NoNetworkGuard.during {
            val log = RecordingSink<String>()
            val sink = RecordingCommitSink(log)
            val first = FakeMutation("one", StepResult("1"), log)
            val second = FakeMutation("two", StepResult("2"), log)
            val third = FakeMutation("three", StepResult("3"), log)
            val pipeline = pipelineOf(ScriptedGate.admitAll(), sink, { _, session ->
                session.submit(ToolStep.Mutation(first))
                log.record("strategy:between")
                session.submit(ToolStep.Mutation(listOf(second, third)))
                StrategyOutcome.Completed("ok")
            })

            val outcome = pipeline.execute(CommandInput("three things"))

            assertEquals(listOf(0, 1, 2), sink.actions.map { it.action.position })
            assertEquals(listOf(0, 1, 2), outcome.executed.map { it.position })
            assertEquals(
                listOf(
                    "apply:one",
                    "sink:action:0:committed",
                    "strategy:between",
                    "apply:two",
                    "sink:action:1:committed",
                    "apply:three",
                    "sink:action:2:committed",
                ),
                log.events.filterNot { it == "gate" || it.startsWith("sink:closed") },
            )
            assertNull(outcome.executed.first().context)
        }
    }
}
