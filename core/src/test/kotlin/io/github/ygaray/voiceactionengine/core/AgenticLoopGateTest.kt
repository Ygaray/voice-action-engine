package io.github.ygaray.voiceactionengine.core

import io.github.ygaray.voiceactionengine.core.commit.ActionKind
import io.github.ygaray.voiceactionengine.core.commit.FinishedKind
import io.github.ygaray.voiceactionengine.core.commit.GateDecision
import io.github.ygaray.voiceactionengine.core.commit.StepResult
import io.github.ygaray.voiceactionengine.core.commit.ToolStep
import io.github.ygaray.voiceactionengine.core.failure.FailureReason
import io.github.ygaray.voiceactionengine.core.pipeline.CommandOutcome
import io.github.ygaray.voiceactionengine.core.provider.ModelCapabilities
import io.github.ygaray.voiceactionengine.core.provider.ModelResult
import io.github.ygaray.voiceactionengine.core.strategy.agentic.AgenticLoopStrategy
import io.github.ygaray.voiceactionengine.core.telemetry.TraceCode
import io.github.ygaray.voiceactionengine.core.testing.FakeAiProvider
import io.github.ygaray.voiceactionengine.core.testing.FakeMutation
import io.github.ygaray.voiceactionengine.core.testing.NoNetworkGuard
import io.github.ygaray.voiceactionengine.core.testing.ProviderStep
import io.github.ygaray.voiceactionengine.core.testing.RecordingCommitSink
import io.github.ygaray.voiceactionengine.core.testing.RecordingSink
import io.github.ygaray.voiceactionengine.core.testing.ScriptedGate
import io.github.ygaray.voiceactionengine.core.testing.ScriptedToolExecutor
import io.github.ygaray.voiceactionengine.core.transcript.ToolResultsMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

private const val FINAL_REPLY = "done"
private const val NOT_A_MUTATING_TOOL = """{"status":"error","reason":"not_a_mutating_tool"}"""
private const val HELD_NOTICE = """{"applied":false,"status":"held_for_confirmation"}"""
private const val TOOL_ERROR = """{"status":"error","reason":"tool_error"}"""
private const val APPLY_ERROR = """{"status":"error"}"""
private const val PREPARE_CANARY = "PREPARE-CANARY-7"
private const val GATE_CANARY = "GATE-CANARY-9"
private const val GATE_FAULT_ERROR = """{"status":"error","reason":"internal_error"}"""

/** The loop's gate path: read tools can never write; held, rejected, previewed and faulty calls answer the model. */
class AgenticLoopGateTest {

    private fun mutation(name: String = SAVE_TOOL, log: RecordingSink<String>? = null): ToolStep.Mutation =
        ToolStep.Mutation(FakeMutation(name, StepResult("saved:$name"), log = log))

    private fun resultsAt(fake: FakeAiProvider, request: Int): ToolResultsMessage =
        fake.calls[request].request.messages.last() as ToolResultsMessage

    private fun oneCallThenProse(name: String = SAVE_TOOL): FakeAiProvider =
        FakeAiProvider(
            ProviderId.ANTHROPIC,
            toolTurn(1, callOf("c1", name, loopArguments())),
            FakeAiProvider.reply(FINAL_REPLY, usage(1)),
        )

    private suspend fun run(
        fake: FakeAiProvider,
        strategy: AgenticLoopStrategy,
        gate: ScriptedGate = ScriptedGate.admitAll(),
        sink: RecordingCommitSink = RecordingCommitSink(),
    ): CommandOutcome =
        loopPipeline(listOf(strategy), fake, gate, sink).execute(CommandInput("add two things", "en", null))

    @Test
    fun aReadToolReturningAMutationNeverReachesTheGate() = runTest {
        NoNetworkGuard.during {
            val write = FakeMutation(FIND_TOOL, StepResult("smuggled"))
            val executor = ScriptedToolExecutor.sequence(null, ToolStep.Mutation(write))
            val gate = ScriptedGate.admitAll()
            val sink = RecordingCommitSink()
            val fake = oneCallThenProse(FIND_TOOL)

            val outcome = run(fake, agenticLoop(executor, loopSnapshotOf(writeTool(), readTool())), gate, sink)

            assertEquals(0, gate.calls)
            assertEquals(0, write.applyCount)
            assertTrue(outcome.executed.isEmpty())
            assertTrue(sink.actions.isEmpty())
            val result = resultsAt(fake, 1).results.single()
            assertTrue(result.isError)
            assertEquals(NOT_A_MUTATING_TOOL, result.content)
            assertTrue(outcome.trace.codes.contains(TraceCode.READ_TOOL_MUTATION_REJECTED))
            assertTrue(outcome.toString(), outcome is CommandOutcome.Completed)
            assertEquals(FINAL_REPLY, (outcome as CommandOutcome.Completed).reply)
        }
    }

    @Test
    fun aMutationPassesTheGateAndReachesTheSinkBeforeTheNextTurn() = runTest {
        NoNetworkGuard.during {
            val log = RecordingSink<String>()
            val steps: List<ProviderStep> = listOf(
                { log.record("provider:1"); toolTurn(1, callOf("c1", SAVE_TOOL, loopArguments())) },
                { log.record("provider:2"); FakeAiProvider.reply(FINAL_REPLY, usage(1)) },
            )
            val fake = FakeAiProvider(ProviderId.ANTHROPIC, steps, ModelCapabilities.UNKNOWN)
            val executor = ScriptedToolExecutor.sequence(log, mutation(SAVE_TOOL, log))

            val strategy = agenticLoop(executor, loopSnapshotOf(writeTool()))
            run(fake, strategy, ScriptedGate.admitAll(log), RecordingCommitSink(log))

            val events = log.events
            val second = events.indexOf("provider:2")
            assertTrue(events.toString(), events.indexOf("gate") in 0 until second)
            assertTrue(events.toString(), events.indexOf("apply:$SAVE_TOOL") in 0 until second)
            assertTrue(events.toString(), events.indexOfFirst { it.startsWith("sink:action:") } in 0 until second)
        }
    }

    @Test
    fun aHeldCallFeedsTheModelTheFixedNoticeAndTheLoopContinues() = runTest {
        NoNetworkGuard.during {
            val write = FakeMutation(SAVE_TOOL, StepResult("saved"))
            val executor = ScriptedToolExecutor.sequence(null, ToolStep.Mutation(write))
            val sink = RecordingCommitSink()
            val fake = oneCallThenProse()

            val outcome = run(fake, agenticLoop(executor, loopSnapshotOf(writeTool())), ScriptedGate.holdAll(), sink)

            val result = resultsAt(fake, 1).results.single()
            assertEquals(HELD_NOTICE, result.content)
            assertFalse(result.isError)
            assertEquals(0, write.applyCount)
            assertTrue(outcome.toString(), outcome is CommandOutcome.Completed)
            assertEquals(1, outcome.held.size)
            assertEquals(listOf(ActionKind.HELD), sink.actions.map { it.action.kind })
        }
    }

    // XR-171-03: a gate that throws fails closed, and the model hears an error, not the held notice.
    private fun faultingGate(faults: Int = Int.MAX_VALUE): ScriptedGate {
        val asked = AtomicInteger()
        return ScriptedGate {
            if (asked.incrementAndGet() <= faults) throw IllegalStateException(GATE_CANARY)
            GateDecision.Admit()
        }
    }

    private fun saveTurnsThenProse(vararg ids: String): Array<ModelResult> =
        (ids.map { toolTurn(1, callOf(it, SAVE_TOOL, loopArguments())) } + FakeAiProvider.reply(FINAL_REPLY, usage(1)))
            .toTypedArray()

    @Test
    fun aGateFaultIsAnErrorActionNeverAHoldAndTheModelHearsTheFixedInternalError() = runTest {
        NoNetworkGuard.during {
            val write = FakeMutation(SAVE_TOOL, StepResult("saved"))
            val executor = ScriptedToolExecutor.sequence(null, ToolStep.Mutation(write))
            val sink = RecordingCommitSink()
            val fake = oneCallThenProse()

            val outcome = run(fake, agenticLoop(executor, loopSnapshotOf(writeTool())), faultingGate(), sink)

            val result = resultsAt(fake, 1).results.single()
            assertEquals(GATE_FAULT_ERROR, result.content)
            assertTrue(result.isError)
            assertEquals(0, write.applyCount)
            assertTrue(outcome.trace.codes.contains(TraceCode.GATE_ERROR))
            val everything = outcome.toString() + outcome.trace + outcome.executed + result.content
            assertFalse(everything, everything.contains(GATE_CANARY))
            // XR-171-03 (ruling a): an error, never a hold. No proposal, no HELD action, one is_error action.
            assertTrue(outcome.held.isEmpty())
            assertEquals(listOf(ActionKind.IS_ERROR), outcome.executed.map { it.kind })
            // A fault is never a success: not in commits, and no COMMITTED action (so no populated result) for it.
            assertTrue(outcome.commits.isEmpty())
            assertTrue(outcome.executed.none { it.kind == ActionKind.COMMITTED })
            assertTrue(sink.actions.none { it.action.kind == ActionKind.COMMITTED })
            assertFalse(outcome.executed.single().applied)
            assertNull(outcome.executed.single().appOutcomeToken)
            assertEquals(listOf(ActionKind.IS_ERROR), sink.actions.map { it.action.kind })
            assertEquals(1, outcome.trace.codes.count { it == TraceCode.GATE_ERROR })
            val closed = sink.closes.single()
            assertTrue(closed.held.isEmpty())
            assertEquals(listOf(ActionKind.IS_ERROR), closed.executed.map { it.kind })
        }
    }

    @Test
    fun twoGateFaultsOnTheSameToolEndFailedToolFailureAfterTwoRequests() = runTest {
        NoNetworkGuard.during {
            val executor = ScriptedToolExecutor.sequence(null, mutation(), mutation(), mutation())
            val fake = FakeAiProvider(
                ProviderId.ANTHROPIC,
                *saveTurnsThenProse("c1", "c2", "c3"),
            )

            val outcome = run(fake, agenticLoop(executor, loopSnapshotOf(writeTool())), faultingGate())

            assertTrue(outcome.toString(), outcome is CommandOutcome.Failed)
            assertEquals(FailureReason.ToolFailure(), (outcome as CommandOutcome.Failed).reason)
            assertEquals(2, fake.callCount)
            // Each fault is an is_error action; neither is a hold.
            assertTrue(outcome.held.isEmpty())
            assertEquals(List(2) { ActionKind.IS_ERROR }, outcome.executed.map { it.kind })
            assertTrue(outcome.commits.isEmpty())
        }
    }

    @Test
    fun aRealHoldKeepsTheNoticeBytesIsNotAnErrorAndNeverStrikes() = runTest {
        NoNetworkGuard.during {
            // A bare Hold() has the same shape the engine's own fail-closed hold has, but it is the app's decision.
            val holds = listOf(ScriptedGate { GateDecision.Hold() }, ScriptedGate.holdAll("why", "tok"))
            holds.forEach { gate ->
                val sink = RecordingCommitSink()
                val executor = ScriptedToolExecutor.sequence(null, mutation(), mutation(), mutation())
                val fake = FakeAiProvider(
                    ProviderId.ANTHROPIC,
                    *saveTurnsThenProse("c1", "c2", "c3"),
                )

                val outcome = run(fake, agenticLoop(executor, loopSnapshotOf(writeTool())), gate, sink)

                assertTrue(outcome.toString(), outcome is CommandOutcome.Completed)
                assertEquals(4, fake.callCount)
                (1..3).forEach { request ->
                    val result = resultsAt(fake, request).results.single()
                    assertEquals(HELD_NOTICE, result.content)
                    assertFalse(result.isError)
                }
                assertFalse(outcome.trace.codes.contains(TraceCode.GATE_ERROR))
                // A real hold is recorded as before: one proposal and one HELD action per call, none is_error.
                assertEquals(3, outcome.held.size)
                assertEquals(List(3) { ActionKind.HELD }, outcome.executed.map { it.kind })
                assertEquals(List(3) { ActionKind.HELD }, sink.actions.map { it.action.kind })
            }
        }
    }

    @Test
    fun oneGateFaultFollowedBySuccessCompletes() = runTest {
        NoNetworkGuard.during {
            val second = FakeMutation(SAVE_TOOL, StepResult("saved"))
            val executor = ScriptedToolExecutor.sequence(null, mutation(), ToolStep.Mutation(second))
            val fake = FakeAiProvider(
                ProviderId.ANTHROPIC,
                *saveTurnsThenProse("c1", "c2"),
            )

            val outcome = run(fake, agenticLoop(executor, loopSnapshotOf(writeTool())), faultingGate(faults = 1))

            assertTrue(outcome.toString(), outcome is CommandOutcome.Completed)
            assertEquals(FINAL_REPLY, (outcome as CommandOutcome.Completed).reply)
            assertEquals(3, fake.callCount)
            assertEquals(1, second.applyCount)
            assertEquals(GATE_FAULT_ERROR, resultsAt(fake, 1).results.single().content)
            assertTrue(resultsAt(fake, 1).results.single().isError)
            assertFalse(resultsAt(fake, 2).results.single().isError)
        }
    }

    @Test
    fun aRejectedMutatingCallNeverReachesTheGate() = runTest {
        NoNetworkGuard.during {
            val rejection = "rejected:bad arguments"
            val step = ToolStep.Finished(SAVE_TOOL, FinishedKind.ERROR, StepResult(rejection, true))
            val executor = ScriptedToolExecutor.sequence(null, step)
            val gate = ScriptedGate.admitAll()
            val sink = RecordingCommitSink()
            val fake = oneCallThenProse()

            run(fake, agenticLoop(executor, loopSnapshotOf(writeTool())), gate, sink)

            assertEquals(0, gate.calls)
            val action = sink.actions.single().action
            assertEquals(ActionKind.IS_ERROR, action.kind)
            assertFalse(action.applied)
            val result = resultsAt(fake, 1).results.single()
            assertEquals(rejection, result.content)
            assertTrue(result.isError)
        }
    }

    @Test
    fun aPreviewIsReportedWithoutTheGate() = runTest {
        NoNetworkGuard.during {
            val step = ToolStep.Finished(SAVE_TOOL, FinishedKind.PREVIEW, StepResult("would save"))
            val executor = ScriptedToolExecutor.sequence(null, step)
            val gate = ScriptedGate.admitAll()
            val sink = RecordingCommitSink()
            val fake = oneCallThenProse()

            run(fake, agenticLoop(executor, loopSnapshotOf(writeTool())), gate, sink)

            assertEquals(0, gate.calls)
            val action = sink.actions.single().action
            assertEquals(ActionKind.PREVIEW, action.kind)
            assertFalse(action.applied)
            assertFalse(resultsAt(fake, 1).results.single().isError)
        }
    }

    @Test
    fun aReadToolReportingAPreviewLeavesNoActionAndTheModelStillGetsItsContent() = runTest {
        NoNetworkGuard.during {
            val step = ToolStep.Finished(FIND_TOOL, FinishedKind.PREVIEW, StepResult("would find"))
            val executor = ScriptedToolExecutor.sequence(null, step)
            val sink = RecordingCommitSink()
            val fake = oneCallThenProse(FIND_TOOL)

            val outcome = run(fake, agenticLoop(executor, loopSnapshotOf(writeTool(), readTool())), sink = sink)

            assertTrue(outcome.executed.isEmpty())
            assertTrue(sink.actions.isEmpty())
            val result = resultsAt(fake, 1).results.single()
            assertEquals("would find", result.content)
            assertFalse(result.isError)
        }
    }

    @Test
    fun aReadToolReportingAnErrorLeavesNoActionStaysAnErrorAndStillStrikes() = runTest {
        NoNetworkGuard.during {
            val rejection = ToolStep.Finished(FIND_TOOL, FinishedKind.ERROR, StepResult("no such entry", false))
            val executor = ScriptedToolExecutor.sequence(null, rejection, rejection)
            val sink = RecordingCommitSink()
            val fake = FakeAiProvider(
                ProviderId.ANTHROPIC,
                toolTurn(1, callOf("c1", FIND_TOOL, loopArguments())),
                toolTurn(1, callOf("c2", FIND_TOOL, loopArguments())),
                FakeAiProvider.reply(FINAL_REPLY, usage(1)),
            )

            val outcome = run(fake, agenticLoop(executor, loopSnapshotOf(writeTool(), readTool())), sink = sink)

            assertTrue(outcome.executed.isEmpty())
            assertTrue(sink.actions.isEmpty())
            val result = resultsAt(fake, 1).results.single()
            assertEquals("no such entry", result.content)
            assertTrue(result.isError)
            assertTrue(outcome.toString(), outcome is CommandOutcome.Failed)
            assertEquals(2, fake.calls.size)
        }
    }

    @Test
    fun aCancelBetweenTwoCallsOfOneTurnStopsBeforeThePrepareOfTheSecond() = runTest {
        NoNetworkGuard.during {
            val read = ToolStep.Finished(FIND_TOOL, FinishedKind.READ, StepResult("found"))
            val executor = ScriptedToolExecutor.sequence(null, mutation(SAVE_TOOL), read)
            var caller: Job? = null
            val sink = RecordingCommitSink(onActionHook = { caller?.cancel() })
            val fake = FakeAiProvider(
                ProviderId.ANTHROPIC,
                toolTurn(1, callOf("c1", SAVE_TOOL, loopArguments()), callOf("c2", FIND_TOOL, loopArguments())),
            )
            val strategy = agenticLoop(executor, loopSnapshotOf(writeTool(), readTool()))

            val running = async { run(fake, strategy, sink = sink) }
            caller = running
            val failure = runCatching { running.await() }.exceptionOrNull()

            assertTrue(failure.toString(), failure is CancellationException)
            assertEquals(listOf(SAVE_TOOL), executor.calls.map { it.toolName })
            assertEquals(1, sink.actions.size)
        }
    }

    @Test
    fun aThrowingPrepareIsAFixedErrorNoticeAndNeverTheExceptionText() = runTest {
        NoNetworkGuard.during {
            val saveFake = oneCallThenProse()
            val findFake = oneCallThenProse(FIND_TOOL)
            val saveSink = RecordingCommitSink()
            val findSink = RecordingCommitSink()
            val thrower = { ScriptedToolExecutor(null) { _, _ -> throw IllegalStateException(PREPARE_CANARY) } }
            val snapshot = loopSnapshotOf(writeTool(), readTool())

            val saveOutcome = run(saveFake, agenticLoop(thrower(), snapshot), sink = saveSink)
            val findOutcome = run(findFake, agenticLoop(thrower(), snapshot), sink = findSink)

            assertEquals(listOf(ActionKind.IS_ERROR), saveSink.actions.map { it.action.kind })
            assertFalse(saveSink.actions.single().action.applied)
            assertTrue(findSink.actions.isEmpty())
            listOf(saveFake to saveOutcome, findFake to findOutcome).forEach { (fake, outcome) ->
                val result = resultsAt(fake, 1).results.single()
                assertEquals(TOOL_ERROR, result.content)
                assertTrue(result.isError)
                assertTrue(outcome.trace.codes.contains(TraceCode.TOOL_PREPARE_ERROR))
                val everything = outcome.toString() + outcome.trace + outcome.executed +
                    fake.calls.flatMap { call ->
                        call.request.messages.filterIsInstance<ToolResultsMessage>().flatMap { it.results }
                            .map { it.content }
                    }
                assertFalse(everything, everything.contains(PREPARE_CANARY))
            }
            assertFalse(saveSink.actions.toString().contains(PREPARE_CANARY))
        }
    }

    @Test
    fun anApplyErrorIsAnsweredWithTheCoordinatorsErrorContent() = runTest {
        NoNetworkGuard.during {
            val failing = FakeMutation(SAVE_TOOL, { throw IllegalStateException("apply failed") })
            val executor = ScriptedToolExecutor.sequence(null, ToolStep.Mutation(failing))
            val sink = RecordingCommitSink()
            val fake = oneCallThenProse()

            run(fake, agenticLoop(executor, loopSnapshotOf(writeTool())), sink = sink)

            val action = sink.actions.single().action
            assertEquals(ActionKind.IS_ERROR, action.kind)
            assertTrue(action.applied)
            val result = resultsAt(fake, 1).results.single()
            assertEquals(APPLY_ERROR, result.content)
            assertTrue(result.isError)
        }
    }

    @Test
    fun aSuspendingGateKeepsDispatchSequential() = runTest {
        NoNetworkGuard.during {
            val release = CompletableDeferred<Unit>()
            val asked = AtomicInteger()
            val gate = ScriptedGate { _ ->
                if (asked.incrementAndGet() == 1) release.await()
                GateDecision.Admit()
            }
            val log = RecordingSink<String>()
            val executor = ScriptedToolExecutor.sequence(log, mutation(SAVE_TOOL, log), mutation(SAVE_TOOL, log))
            val fake = FakeAiProvider(
                ProviderId.ANTHROPIC,
                toolTurn(
                    1,
                    callOf("a", SAVE_TOOL, loopArguments()),
                    callOf("b", SAVE_TOOL, loopArguments()),
                ),
                FakeAiProvider.reply(FINAL_REPLY, usage(1)),
            )
            val strategy = agenticLoop(executor, loopSnapshotOf(writeTool()))

            val pending = async { run(fake, strategy, gate) }
            testScheduler.runCurrent()

            assertEquals(1, executor.callCount)
            assertEquals(1, asked.get())
            release.complete(Unit)
            val outcome = pending.await()

            assertEquals(2, executor.callCount)
            assertTrue(outcome.toString(), outcome is CommandOutcome.Completed)
            assertEquals(listOf("a", "b"), resultsAt(fake, 1).results.map { it.callId })
            val expected = listOf("prepare:$SAVE_TOOL", "apply:$SAVE_TOOL", "prepare:$SAVE_TOOL", "apply:$SAVE_TOOL")
            assertEquals(expected, log.events)
        }
    }
}
