package io.github.ygaray.voiceactionengine.core

import io.github.ygaray.voiceactionengine.core.commit.ActionKind
import io.github.ygaray.voiceactionengine.core.commit.RunTermination
import io.github.ygaray.voiceactionengine.core.commit.StepResult
import io.github.ygaray.voiceactionengine.core.commit.ToolStep
import io.github.ygaray.voiceactionengine.core.pipeline.CommandOutcome
import io.github.ygaray.voiceactionengine.core.pipeline.commandPipeline
import io.github.ygaray.voiceactionengine.core.strategy.CommandSession
import io.github.ygaray.voiceactionengine.core.strategy.StrategyOutcome
import io.github.ygaray.voiceactionengine.core.strategy.TerminalCall
import io.github.ygaray.voiceactionengine.core.testing.FakeMutation
import io.github.ygaray.voiceactionengine.core.testing.NoNetworkGuard
import io.github.ygaray.voiceactionengine.core.testing.RecordingCommitSink
import io.github.ygaray.voiceactionengine.core.testing.RecordingSink
import io.github.ygaray.voiceactionengine.core.testing.ScriptedGate
import io.github.ygaray.voiceactionengine.core.testing.ScriptedStrategy
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** The whole spine in one place: DSL, execute, strategy, session, gate, apply, sink, outcome and run close. */
class PipelineSpineTest {

    private class SecretContext {
        override fun toString(): String = "CANARY-CONTEXT"
    }

    private val tierId = StrategyId("tier-one")
    private val runId = "run-fixed"

    private fun mutation(name: String, log: RecordingSink<String>? = null, result: StepResult = StepResult("ok")) =
        FakeMutation(name, result, log)

    @Test
    fun oneMutationFlowsThroughGateApplySinkAndClosesOnce() = runTest {
        NoNetworkGuard.during {
            val log = RecordingSink<String>()
            val gate = ScriptedGate.admitAll(log)
            val sink = RecordingCommitSink(log)
            val context = Any()
            val write = FakeMutation(
                toolName = "write_note",
                behavior = { StepResult("saved", false, "OUTCOME-VERBATIM", mapOf("id" to "n2")) },
                targetIds = mapOf("id" to "n1", "folder" to "f"),
                context = context,
                log = log,
            )
            val strategy = ScriptedStrategy(
                tierId,
                { _, session ->
                    session.submit(ToolStep.Mutation(write))
                    StrategyOutcome.Completed("all done")
                },
            )
            val pipeline = commandPipeline {
                tier(strategy)
                this.gate = gate
                commitSink = sink
                runIds = { runId }
            }

            val outcome = pipeline.execute(CommandInput("add a note"))

            val completed = outcome as CommandOutcome.Completed
            assertEquals("all done", completed.reply)
            assertFalse(completed.partial)
            assertNull(completed.terminalCall)
            assertEquals(runId, outcome.runId)
            val action = outcome.executed.single()
            assertEquals(0, action.position)
            assertEquals(ActionKind.COMMITTED, action.kind)
            assertTrue(action.applied)
            assertEquals("OUTCOME-VERBATIM", action.appOutcomeToken)
            assertEquals("write_note", action.toolName)
            assertEquals(mapOf("id" to "n2", "folder" to "f"), action.targetIds)
            assertSame(context, action.context)
            assertEquals(outcome.executed, outcome.commits)
            assertTrue(outcome.held.isEmpty())
            assertEquals(runId, outcome.trace.runId)
            assertEquals(
                listOf("gate", "apply:write_note", "sink:action:0:committed", "sink:closed:$runId:done"),
                log.events,
            )
            assertEquals(1, gate.calls)
            assertEquals(listOf<Any>(write), gate.proposals.single().mutations)
            assertEquals(1, sink.closes.size)
            assertEquals(listOf(runId), sink.closedRunIds)
            assertTrue(sink.closes.single() is RunTermination.Done)
            assertSame(action, sink.actions.single().action)
        }
    }

    @Test
    fun twoMutationsApplyInOrderWithASinkEventBetweenThem() = runTest {
        NoNetworkGuard.during {
            val log = RecordingSink<String>()
            val sink = RecordingCommitSink(log)
            val first = mutation("first", log)
            val second = mutation("second", log, StepResult("two", true))
            var dispatched: List<String> = emptyList()
            val strategy = ScriptedStrategy(
                tierId,
                { _, session ->
                    val result = session.submit(ToolStep.Mutation(listOf(first, second)))
                    dispatched = listOf(result.contentForModel, result.isError.toString())
                    StrategyOutcome.Completed("done")
                },
            )
            val pipeline = commandPipeline {
                tier(strategy)
                gate = ScriptedGate.admitAll(log)
                commitSink = sink
                runIds = { runId }
            }

            val outcome = pipeline.execute(CommandInput("two things"))

            assertEquals(
                listOf(
                    "gate",
                    "apply:first",
                    "sink:action:0:committed",
                    "apply:second",
                    "sink:action:1:is_error",
                    "sink:closed:$runId:done",
                ),
                log.events,
            )
            assertEquals(listOf(0, 1), outcome.executed.map { it.position })
            assertEquals(listOf("ok\ntwo", "true"), dispatched)
            assertEquals(1, outcome.commits.size)
        }
    }

    @Test
    fun terminalCallPassesThroughAndReplyAndCallAreExclusive() = runTest {
        NoNetworkGuard.during {
            val call = TerminalCall("finish", buildJsonObject {})
            val strategy = ScriptedStrategy(tierId, { _, _ -> StrategyOutcome.Completed(null, call) })
            val pipeline = commandPipeline {
                tier(strategy)
                gate = ScriptedGate.admitAll()
                commitSink = RecordingCommitSink()
            }

            val outcome = pipeline.execute(CommandInput("hello")) as CommandOutcome.Completed

            assertSame(call, outcome.terminalCall)
            assertNull(outcome.reply)
            assertThrows(IllegalArgumentException::class.java) { StrategyOutcome.Completed("x", call) }
        }
    }

    @Test
    fun parentRunIdReachesEveryPlaceThatReportsIt() = runTest {
        NoNetworkGuard.during {
            val gate = ScriptedGate.admitAll()
            val sink = RecordingCommitSink()
            val strategy = ScriptedStrategy(
                tierId,
                { _, session ->
                    session.submit(ToolStep.Mutation(mutation("m")))
                    StrategyOutcome.Completed("ok")
                },
            )
            val pipeline = commandPipeline {
                tier(strategy)
                this.gate = gate
                commitSink = sink
            }

            val outcome = pipeline.execute(CommandInput("answer", parentRunId = "parent-1"))

            assertEquals("parent-1", outcome.parentRunId)
            assertEquals("parent-1", outcome.trace.parentRunId)
            assertEquals("parent-1", gate.proposals.single().parentRunId)
            assertEquals("parent-1", sink.actions.single().parentRunId)
            assertEquals("parent-1", sink.closes.single().parentRunId)
            assertEquals("parent-1", sink.closes.single().trace.parentRunId)
        }
    }

    @Test
    fun noCanaryReachesAnyToStringOfWhatTheEngineProduces() = runTest {
        NoNetworkGuard.during {
            val sink = RecordingCommitSink()
            var session: CommandSession? = null
            val write = FakeMutation(
                toolName = "write",
                behavior = { StepResult("CANARY-CONTENT", false, "CANARY-TOKEN", emptyMap()) },
                context = SecretContext(),
            )
            val strategy = ScriptedStrategy(
                tierId,
                { _, s ->
                    session = s
                    s.submit(ToolStep.Mutation(write))
                    StrategyOutcome.Completed("CANARY-REPLY")
                },
            )
            val pipeline = commandPipeline {
                tier(strategy)
                gate = ScriptedGate.admitAll()
                commitSink = sink
            }

            val outcome = pipeline.execute(CommandInput("CANARY-TRANSCRIPT", context = SecretContext()))

            val rendered = listOf(
                outcome.toString(),
                outcome.executed.single().toString(),
                sink.actions.single().toString(),
                sink.closes.single().toString(),
                outcome.trace.toString(),
                session.toString(),
                pipeline.toString(),
            )
            for (text in rendered) {
                assertFalse(text, text.contains("CANARY"))
            }
        }
    }
}
