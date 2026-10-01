package io.github.ygaray.voiceactionengine.core

import io.github.ygaray.voiceactionengine.core.commit.ActionKind
import io.github.ygaray.voiceactionengine.core.commit.CommitSink
import io.github.ygaray.voiceactionengine.core.commit.DispatchResult
import io.github.ygaray.voiceactionengine.core.commit.GateDecision
import io.github.ygaray.voiceactionengine.core.commit.PendingMutation
import io.github.ygaray.voiceactionengine.core.commit.RunTermination
import io.github.ygaray.voiceactionengine.core.commit.StepResult
import io.github.ygaray.voiceactionengine.core.commit.ToolStep
import io.github.ygaray.voiceactionengine.core.commit.applyErrorContent
import io.github.ygaray.voiceactionengine.core.pipeline.CommandPipeline
import io.github.ygaray.voiceactionengine.core.pipeline.commandPipeline
import io.github.ygaray.voiceactionengine.core.strategy.CommandSession
import io.github.ygaray.voiceactionengine.core.strategy.StrategyOutcome
import io.github.ygaray.voiceactionengine.core.telemetry.TraceCode
import io.github.ygaray.voiceactionengine.core.testing.FakeMutation
import io.github.ygaray.voiceactionengine.core.testing.NoNetworkGuard
import io.github.ygaray.voiceactionengine.core.testing.RecordingCommitSink
import io.github.ygaray.voiceactionengine.core.testing.RecordingSink
import io.github.ygaray.voiceactionengine.core.testing.ScriptedGate
import io.github.ygaray.voiceactionengine.core.testing.ScriptedStrategy
import io.github.ygaray.voiceactionengine.core.testing.StrategyStep
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.coroutines.Continuation

/** The single write path under stress: failing applies, cancellation around a write, a failing sink, amendments. */
class CommitPathTest {
    private val tierId = StrategyId("tier-one")
    private val runId = "run-path"

    private fun pipelineOf(gate: ScriptedGate, sink: CommitSink, vararg steps: StrategyStep): CommandPipeline =
        commandPipeline {
            tier(ScriptedStrategy(tierId, *steps))
            this.gate = gate
            commitSink = sink
            runIds = { runId }
        }

    @Test
    fun anApplyThatReturnsAnErrorIsAnAppliedErrorNeverACommit() = runTest {
        NoNetworkGuard.during {
            val write = FakeMutation("write", StepResult("no such note", true, "NOT_FOUND", mapOf("id" to "n9")))
            val sink = RecordingCommitSink()
            var seen: DispatchResult? = null
            val pipeline = pipelineOf(ScriptedGate.admitAll(), sink, { _, session ->
                seen = session.submit(ToolStep.Mutation(write))
                StrategyOutcome.Completed("could not")
            })

            val outcome = pipeline.execute(CommandInput("write"))

            val action = sink.actions.single().action
            assertEquals(ActionKind.IS_ERROR, action.kind)
            assertTrue(action.applied)
            assertEquals("NOT_FOUND", action.appOutcomeToken)
            assertEquals(mapOf("id" to "n9"), action.targetIds)
            assertTrue(outcome.commits.isEmpty())
            assertEquals(listOf(action), outcome.executed)
            assertTrue(seen!!.isError)
            assertEquals("no such note", seen!!.contentForModel)
        }
    }

    @Test
    fun anApplyThatThrowsIsAnAppliedErrorWithTheFixedNotice() = runTest {
        NoNetworkGuard.during {
            val write = FakeMutation("write", { error("disk on fire: CANARY") })
            val sink = RecordingCommitSink()
            var seen: DispatchResult? = null
            val pipeline = pipelineOf(ScriptedGate.admitAll(), sink, { _, session ->
                seen = session.submit(ToolStep.Mutation(write))
                StrategyOutcome.Completed("could not")
            })

            val outcome = pipeline.execute(CommandInput("write"))

            val action = sink.actions.single().action
            assertEquals(ActionKind.IS_ERROR, action.kind)
            assertTrue(action.applied)
            assertNull(action.appOutcomeToken)
            assertTrue(outcome.trace.codes.contains(TraceCode.APPLY_ERROR))
            assertTrue(seen!!.isError)
            assertEquals(applyErrorContent(), seen!!.contentForModel)
            assertEquals("""{"status":"error"}""", seen!!.contentForModel)
            assertTrue(outcome.commits.isEmpty())
            assertFalse(outcome.toString().contains("CANARY"))
        }
    }

    @Test
    fun aCancelDuringAnApplyIsJournaledBeforeTheCancellationContinues() = runTest {
        NoNetworkGuard.during {
            val log = RecordingSink<String>()
            val started = CompletableDeferred<Unit>()
            val write = FakeMutation("write", { started.complete(Unit); awaitCancellation() }, log = log)
            val sink = RecordingCommitSink(log)
            val pipeline = pipelineOf(ScriptedGate.admitAll(log), sink, { _, session ->
                session.submit(ToolStep.Mutation(write))
                StrategyOutcome.Completed("never")
            })

            val job = launch { pipeline.execute(CommandInput("write")) }
            started.await()
            job.cancel()
            job.join()

            assertTrue(job.isCancelled)
            assertEquals(
                listOf("gate", "apply:write", "sink:action:0:is_error", "sink:closed:$runId:cancelled"),
                log.events,
            )
            val action = sink.actions.single().action
            assertEquals(ActionKind.IS_ERROR, action.kind)
            assertTrue(action.applied)
            assertNull(action.appOutcomeToken)
            val closed = sink.closes.single()
            assertTrue(closed is RunTermination.Cancelled)
            assertEquals(listOf(action), closed.executed)
            assertTrue(closed.trace.codes.contains(TraceCode.APPLY_CANCELLED))
            assertTrue(closed.commits.isEmpty())
        }
    }

    @Test
    fun aSinkThatThrowsAfterASuccessfulApplyNeverCausesASecondApply() = runTest {
        NoNetworkGuard.during {
            var calls = 0
            val sink = RecordingCommitSink(onActionHook = { if (calls++ == 0) error("journal down") })
            val first = FakeMutation("first", StepResult("1"))
            val second = FakeMutation("second", StepResult("2"))
            val pipeline = pipelineOf(ScriptedGate.admitAll(), sink, { _, session ->
                session.submit(ToolStep.Mutation(first))
                session.submit(ToolStep.Mutation(second))
                StrategyOutcome.Completed("ok")
            })

            val outcome = pipeline.execute(CommandInput("two"))

            assertEquals(1, first.applyCount)
            assertEquals(1, second.applyCount)
            assertTrue(outcome.trace.codes.contains(TraceCode.SINK_ERROR))
            assertEquals(listOf(ActionKind.COMMITTED, ActionKind.COMMITTED), outcome.executed.map { it.kind })
            assertEquals(2, outcome.commits.size)
        }
    }

    @Test
    fun anAmendedAdmitAppliesTheAmendedChangeAndReportsItsContext() = runTest {
        NoNetworkGuard.during {
            val originalContext = Any()
            val amendedContext = Any()
            val original = FakeMutation("original", { StepResult("o") }, context = originalContext)
            val amended = FakeMutation("amended", { StepResult("a") }, context = amendedContext)
            val gate = ScriptedGate { GateDecision.Admit(listOf<PendingMutation>(amended)) }
            val sink = RecordingCommitSink()
            val pipeline = pipelineOf(gate, sink, { _, session ->
                session.submit(ToolStep.Mutation(original))
                StrategyOutcome.Completed("ok")
            })

            pipeline.execute(CommandInput("amend"))

            assertEquals(0, original.applyCount)
            assertEquals(1, amended.applyCount)
            assertEquals(listOf<Any>(original), gate.proposals.single().mutations)
            val action = sink.actions.single().action
            assertEquals("amended", action.toolName)
            assertSame(amendedContext, action.context)
        }
    }

    @Test
    fun aGateCancelledWhileSuspendedRecordsAndDeliversNothing() = runTest {
        NoNetworkGuard.during {
            val started = CompletableDeferred<Unit>()
            val write = FakeMutation("write", StepResult("w"))
            val sink = RecordingCommitSink()
            val gate = ScriptedGate { started.complete(Unit); awaitCancellation() }
            val pipeline = pipelineOf(gate, sink, { _, session ->
                session.submit(ToolStep.Mutation(write))
                StrategyOutcome.Completed("never")
            })

            val job = launch { pipeline.execute(CommandInput("write")) }
            started.await()
            job.cancel()
            job.join()

            assertTrue(job.isCancelled)
            assertEquals(0, write.applyCount)
            assertTrue(sink.actions.isEmpty())
            val closed = sink.closes.single()
            assertTrue(closed is RunTermination.Cancelled)
            assertTrue(closed.executed.isEmpty())
            assertTrue(closed.held.isEmpty())
            assertFalse(closed.trace.codes.contains(TraceCode.GATE_ERROR))
        }
    }

    @Test
    fun theStrategyHasNoOtherWritePathThanSubmitOfOneToolStep() {
        val methods = CommandSession::class.java.methods.filter { it.declaringClass == CommandSession::class.java }
        val submits = methods.filter { it.name == "submit" }
        assertEquals(1, submits.size)
        val params = submits.single().parameterTypes.filter { it != Continuation::class.java }
        assertEquals(listOf<Class<*>>(ToolStep::class.java), params)
        val forbidden = setOf<Class<*>>(PendingMutation::class.java, CommitSink::class.java, List::class.java)
        for (method in methods) {
            for (type in method.parameterTypes) {
                assertFalse("${method.name} takes $type", type in forbidden)
            }
        }
    }
}
