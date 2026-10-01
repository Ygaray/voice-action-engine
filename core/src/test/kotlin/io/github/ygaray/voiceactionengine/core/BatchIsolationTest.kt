package io.github.ygaray.voiceactionengine.core

import io.github.ygaray.voiceactionengine.core.commit.ActionKind
import io.github.ygaray.voiceactionengine.core.commit.DispatchResult
import io.github.ygaray.voiceactionengine.core.commit.GateDecision
import io.github.ygaray.voiceactionengine.core.commit.PendingMutation
import io.github.ygaray.voiceactionengine.core.commit.StepResult
import io.github.ygaray.voiceactionengine.core.commit.ToolStep
import io.github.ygaray.voiceactionengine.core.pipeline.CommandOutcome
import io.github.ygaray.voiceactionengine.core.pipeline.commandPipeline
import io.github.ygaray.voiceactionengine.core.strategy.StrategyOutcome
import io.github.ygaray.voiceactionengine.core.testing.FakeMutation
import io.github.ygaray.voiceactionengine.core.testing.NoNetworkGuard
import io.github.ygaray.voiceactionengine.core.testing.RecordingCommitSink
import io.github.ygaray.voiceactionengine.core.testing.RecordingSink
import io.github.ygaray.voiceactionengine.core.testing.ScriptedGate
import io.github.ygaray.voiceactionengine.core.testing.ScriptedStrategy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** A batch applies one item at a time and a failing item never stops, undoes or corrupts its siblings. */
class BatchIsolationTest {
    private val runId = "run-batch"

    private class Run(val outcome: CommandOutcome, val dispatched: DispatchResult?, val sink: RecordingCommitSink)

    private suspend fun runBatch(
        log: RecordingSink<String>,
        gate: ScriptedGate,
        mutations: List<PendingMutation>,
    ): Run {
        val sink = RecordingCommitSink(log)
        var dispatched: DispatchResult? = null
        val pipeline = commandPipeline {
            tier(
                ScriptedStrategy(StrategyId("tier-one"), { _, session ->
                    dispatched = session.submit(ToolStep.Mutation(mutations))
                    StrategyOutcome.Completed("batch done")
                }),
            )
            this.gate = gate
            commitSink = sink
            runIds = { runId }
        }
        return Run(pipeline.execute(CommandInput("log three things")), dispatched, sink)
    }

    @Test
    fun aThrowingMiddleItemIsAnErrorEventAndBothSiblingsApplyOnce() = runTest {
        NoNetworkGuard.during {
            val log = RecordingSink<String>()
            val a = FakeMutation("a", StepResult("A", false, "tok-a", emptyMap()), log)
            val b = FakeMutation("b", { error("b broke") }, log = log)
            val c = FakeMutation("c", StepResult("C", false, "tok-c", emptyMap()), log)

            val run = runBatch(log, ScriptedGate.admitAll(), listOf(a, b, c))

            assertEquals(listOf(1, 1, 1), listOf(a.applyCount, b.applyCount, c.applyCount))
            assertEquals(
                listOf(
                    "apply:a",
                    "sink:action:0:committed",
                    "apply:b",
                    "sink:action:1:is_error",
                    "apply:c",
                    "sink:action:2:committed",
                ),
                log.events.filterNot { it.startsWith("sink:closed") },
            )
            assertEquals(
                listOf(ActionKind.COMMITTED, ActionKind.IS_ERROR, ActionKind.COMMITTED),
                run.sink.actions.map { it.action.kind },
            )
            assertEquals(1, run.outcome.executed.count { it.kind == ActionKind.IS_ERROR })
            assertEquals(2, run.outcome.commits.size)
            assertEquals(listOf("tok-a", null, "tok-c"), run.outcome.executed.map { it.appOutcomeToken })
            assertTrue(run.dispatched!!.isError)
        }
    }

    @Test
    fun aMiddleItemReturningAnErrorHasTheSameShapeAndKeepsItsToken() = runTest {
        NoNetworkGuard.during {
            val log = RecordingSink<String>()
            val a = FakeMutation("a", StepResult("A", false, "tok-a", emptyMap()), log)
            val b = FakeMutation("b", StepResult("B failed", true, "tok-b", emptyMap()), log)
            val c = FakeMutation("c", StepResult("C", false, "tok-c", emptyMap()), log)

            val run = runBatch(log, ScriptedGate.admitAll(), listOf(a, b, c))

            assertEquals(listOf(1, 1, 1), listOf(a.applyCount, b.applyCount, c.applyCount))
            assertEquals(
                listOf(ActionKind.COMMITTED, ActionKind.IS_ERROR, ActionKind.COMMITTED),
                run.sink.actions.map { it.action.kind },
            )
            assertEquals(listOf(0, 1, 2), run.sink.actions.map { it.action.position })
            assertEquals("tok-b", run.sink.actions[1].action.appOutcomeToken)
            assertEquals(1, run.outcome.executed.count { it.kind == ActionKind.IS_ERROR })
            assertEquals("A\nB failed\nC", run.dispatched!!.contentForModel)
        }
    }

    @Test
    fun anAmendedAdmitOfTwoOfThreeItemsAppliesExactlyTwo() = runTest {
        NoNetworkGuard.during {
            val log = RecordingSink<String>()
            val a = FakeMutation("a", StepResult("A"), log)
            val b = FakeMutation("b", StepResult("B"), log)
            val c = FakeMutation("c", StepResult("C"), log)
            val gate = ScriptedGate(log) { GateDecision.Admit(listOf<PendingMutation>(a, c)) }

            val run = runBatch(log, gate, listOf(a, b, c))

            assertEquals(listOf(1, 0, 1), listOf(a.applyCount, b.applyCount, c.applyCount))
            assertEquals(2, run.sink.actions.size)
            assertEquals(listOf("a", "c"), run.sink.actions.map { it.action.toolName })
        }
    }
}
