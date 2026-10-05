package io.github.ygaray.voiceactionengine.core

import io.github.ygaray.voiceactionengine.core.commit.ActionKind
import io.github.ygaray.voiceactionengine.core.commit.FinishedKind
import io.github.ygaray.voiceactionengine.core.commit.GateDecision
import io.github.ygaray.voiceactionengine.core.commit.StepResult
import io.github.ygaray.voiceactionengine.core.commit.ToolStep
import io.github.ygaray.voiceactionengine.core.pipeline.CommandOutcome
import io.github.ygaray.voiceactionengine.core.pipeline.CommandPipeline
import io.github.ygaray.voiceactionengine.core.strategy.Resolution
import io.github.ygaray.voiceactionengine.core.telemetry.TraceCode
import io.github.ygaray.voiceactionengine.core.telemetry.Usage
import io.github.ygaray.voiceactionengine.core.testing.FakeAiProvider
import io.github.ygaray.voiceactionengine.core.testing.FakeMutation
import io.github.ygaray.voiceactionengine.core.testing.NoNetworkGuard
import io.github.ygaray.voiceactionengine.core.testing.RecordingCommitSink
import io.github.ygaray.voiceactionengine.core.testing.ScriptedGate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

private const val ARGUMENT_MARKER = "target-2026-10-02"
private const val CALL_ID = "call_3"

/** What the strategy does with the app resolver's answer: it submits, the gate decides, nothing is written directly. */
class SingleShotResolveTest {

    private class Run(
        val outcome: CommandOutcome,
        val resolver: RecordingResolver,
        val gate: ScriptedGate,
        val sink: RecordingCommitSink,
        val input: CommandInput,
    )

    private fun write(name: String = ENTRIES_TOOL): FakeMutation =
        FakeMutation(name, StepResult("saved", false, "ok", emptyMap()))

    private suspend fun run(gate: ScriptedGate, answer: suspend () -> Resolution): Run {
        val resolver = RecordingResolver { _, _ -> answer() }
        val fake = FakeAiProvider(
            ProviderId.ANTHROPIC,
            FakeAiProvider.toolCall("call-1", ENTRIES_TOOL, entriesArguments(ARGUMENT_MARKER), Usage(1, 0, 0, 1)),
        )
        val sink = RecordingCommitSink()
        val input = CommandInput("add two things", "en", null)
        val pipeline = pipelineOf(listOf(singleShot(resolver, snapshotOf(entriesTool()))), fake, gate, sink)
        return Run(pipeline.execute(input), resolver, gate, sink, input)
    }

    @Test
    fun theResolverReceivesTheFirstCallAndTheSameInput() = runTest {
        NoNetworkGuard.during {
            val run = run(ScriptedGate.admitAll()) { Resolution.Steps(listOf(ToolStep.Mutation(write()))) }

            assertEquals(1, run.resolver.invocations)
            val extraction = run.resolver.extractions.single()
            assertEquals(ENTRIES_TOOL, extraction.toolName)
            assertEquals(entriesArguments(ARGUMENT_MARKER), extraction.arguments)
            assertSame(run.input, run.resolver.inputs.single())
        }
    }

    @Test
    fun aFinishedStepIsSubmittedBeforeTheMutationAndOnlyTheMutationReachesTheGate() = runTest {
        NoNetworkGuard.during {
            val finished = ToolStep.Finished("preview_entries", FinishedKind.PREVIEW, StepResult("preview"))
            val run = run(ScriptedGate.admitAll()) {
                Resolution.Steps(listOf(ToolStep.Mutation(write()), finished))
            }

            assertEquals(1, run.gate.calls)
            assertEquals(
                listOf(ActionKind.PREVIEW, ActionKind.COMMITTED),
                run.sink.actions.map { it.action.kind },
            )
            assertEquals(
                listOf("preview_entries", ENTRIES_TOOL),
                run.sink.actions.map { it.action.toolName },
            )
        }
    }

    @Test
    fun twoMutationStepsGoOutAsOneProposalHoldingAllThreeInOrder() = runTest {
        NoNetworkGuard.during {
            val first = write("first")
            val second = write("second")
            val third = write("third")
            val run = run(ScriptedGate.admitAll()) {
                Resolution.Steps(listOf(ToolStep.Mutation(listOf(first, second)), ToolStep.Mutation(third)))
            }

            assertEquals(1, run.gate.calls)
            val mutations = run.gate.proposals.single().mutations
            assertEquals(3, mutations.size)
            assertSame(first, mutations[0])
            assertSame(second, mutations[1])
            assertSame(third, mutations[2])
        }
    }

    @Test
    fun finishedOnlyStepsNeverAskTheGate() = runTest {
        NoNetworkGuard.during {
            val finished = ToolStep.Finished("preview_entries", FinishedKind.PREVIEW, StepResult("preview"))
            val run = run(ScriptedGate.admitAll()) { Resolution.Steps(listOf(finished)) }

            assertEquals(0, run.gate.calls)
            assertTrue(run.outcome.toString(), run.outcome is CommandOutcome.Completed)
        }
    }

    @Test
    fun aHoldingGateLeavesEveryMutationUnappliedAndHoldsOneProposal() = runTest {
        NoNetworkGuard.during {
            val first = write("first")
            val second = write("second")
            val run = run(ScriptedGate.holdAll("confirm")) {
                Resolution.Steps(listOf(ToolStep.Mutation(first), ToolStep.Mutation(second)))
            }

            assertEquals(0, first.applyCount)
            assertEquals(0, second.applyCount)
            assertEquals(1, run.outcome.held.size)
            assertEquals(2, run.outcome.held.single().mutations.size)
            assertTrue(run.outcome.toString(), run.outcome is CommandOutcome.Completed)
        }
    }

    @Test
    fun theStepsReplyBecomesTheCompletedReplyWithNoTerminalCall() = runTest {
        NoNetworkGuard.during {
            val run = run(ScriptedGate.admitAll()) { Resolution.Steps(listOf(ToolStep.Mutation(write())), "done") }

            val completed = run.outcome as CommandOutcome.Completed
            assertEquals("done", completed.reply)
            assertNull(completed.terminalCall)
        }
    }

    @Test
    fun aHeldProposalKeepsTheReplyBecauseTheOutcomeCarriesTheHold() = runTest {
        NoNetworkGuard.during {
            val run = run(ScriptedGate.holdAll("confirm")) {
                Resolution.Steps(listOf(ToolStep.Mutation(write())), "done")
            }

            val completed = run.outcome as CommandOutcome.Completed
            assertEquals("done", completed.reply)
            assertEquals(1, completed.held.size)
        }
    }

    @Test
    fun aFailedApplyWithholdsTheReply() = runTest {
        NoNetworkGuard.during {
            val failing = FakeMutation(ENTRIES_TOOL, StepResult("could not save", true))
            val run = run(ScriptedGate.admitAll()) {
                Resolution.Steps(listOf(ToolStep.Mutation(failing)), "done")
            }

            val completed = run.outcome as CommandOutcome.Completed
            assertNull(completed.reply)
            assertEquals(listOf(ActionKind.IS_ERROR), completed.executed.map { it.kind })
        }
    }

    // XR-171-03 (ruling a): the faulting gate settles as an error, never a hold, so the reply is withheld, exactly as
    // for a failed apply. No held proposal exists; an is_error action and the gate_error trace code are reported, and
    // nothing was written.
    @Test
    fun aThrowingGateWithholdsTheReplyAndReportsAnErrorActionNeverAHold() = runTest {
        NoNetworkGuard.during {
            val mutation = write()
            val run = run(ScriptedGate { throw IllegalStateException("gate broke") }) {
                Resolution.Steps(listOf(ToolStep.Mutation(mutation)), "done")
            }

            val completed = run.outcome as CommandOutcome.Completed
            assertNull(completed.reply)
            assertEquals(0, mutation.applyCount)
            assertTrue(completed.held.isEmpty())
            assertEquals(listOf(ActionKind.IS_ERROR), completed.executed.map { it.kind })
            // A fault is never a success: not in commits, and no COMMITTED action (so no populated result) for it.
            assertTrue(completed.commits.isEmpty())
            assertTrue(run.sink.actions.none { it.action.kind == ActionKind.COMMITTED })
            assertTrue(run.sink.closes.single().commits.isEmpty())
            assertNull(completed.executed.single().appOutcomeToken)
            assertEquals(listOf(ActionKind.IS_ERROR), run.sink.actions.map { it.action.kind })
            assertTrue(run.sink.closes.single().held.isEmpty())
            assertTrue(completed.trace.codes.contains(TraceCode.GATE_ERROR))
        }
    }

    @Test
    fun aBareHoldReturnedByTheGateIsNotAFaultAndKeepsTheReply() = runTest {
        NoNetworkGuard.during {
            val run = run(ScriptedGate { GateDecision.Hold() }) {
                Resolution.Steps(listOf(ToolStep.Mutation(write())), "done")
            }

            val completed = run.outcome as CommandOutcome.Completed
            assertEquals("done", completed.reply)
            assertEquals(1, completed.held.size)
            assertTrue(!completed.trace.codes.contains(TraceCode.GATE_ERROR))
        }
    }

    @Test
    fun aRejectionStepAlongsideAnAppliedMutationKeepsTheReply() = runTest {
        NoNetworkGuard.during {
            val rejected = ToolStep.Finished("record_missing", FinishedKind.ERROR, StepResult("no such item", true))
            val run = run(ScriptedGate.admitAll()) {
                Resolution.Steps(listOf(rejected, ToolStep.Mutation(write())), "saved one, one not found")
            }

            val completed = run.outcome as CommandOutcome.Completed
            assertEquals("saved one, one not found", completed.reply)
        }
    }

    @Test
    fun anAdmittingGateCommitsEveryMutationAndTheSinkSeesOneActionEach() = runTest {
        NoNetworkGuard.during {
            val mutations = listOf(write("first"), write("second"), write("third"))
            val run = run(ScriptedGate.admitAll()) {
                Resolution.Steps(listOf(ToolStep.Mutation(mutations.take(2)), ToolStep.Mutation(mutations[2])))
            }

            mutations.forEach { assertEquals(1, it.applyCount) }
            assertEquals(List(3) { ActionKind.COMMITTED }, run.sink.actions.map { it.action.kind })
            assertEquals(3, run.outcome.commits.size)
        }
    }

    private fun callIdPipeline(gate: ScriptedGate, sink: RecordingCommitSink, answer: () -> Resolution): CommandPipeline {
        val fake = FakeAiProvider(
            ProviderId.ANTHROPIC,
            FakeAiProvider.toolCall(CALL_ID, ENTRIES_TOOL, entriesArguments(ARGUMENT_MARKER), Usage(1, 0, 0, 1)),
        )
        val resolver = RecordingResolver { _, _ -> answer() }
        return pipelineOf(listOf(singleShot(resolver, snapshotOf(entriesTool()))), fake, gate, sink)
    }

    private val command = CommandInput("add two things", "en", null)

    @Test
    fun oneCallStampsItsIdOnEveryPreviewErrorAndCommittedAction() = runTest {
        NoNetworkGuard.during {
            val preview = ToolStep.Finished("preview_entries", FinishedKind.PREVIEW, StepResult("preview"))
            val rejected = ToolStep.Finished("reject_entries", FinishedKind.ERROR, StepResult("no", true))
            val sink = RecordingCommitSink()
            val pipeline = callIdPipeline(ScriptedGate.admitAll(), sink) {
                Resolution.Steps(listOf(preview, rejected, ToolStep.Mutation(write("a")), ToolStep.Mutation(write("b"))))
            }

            val outcome = pipeline.execute(command)

            assertEquals(
                listOf(ActionKind.PREVIEW, ActionKind.IS_ERROR, ActionKind.COMMITTED, ActionKind.COMMITTED),
                outcome.executed.map { it.kind },
            )
            assertEquals(List(4) { CALL_ID }, outcome.executed.map { it.providerCallId })
            assertEquals(List(4) { CALL_ID }, sink.actions.map { it.action.providerCallId })
        }
    }

    @Test
    fun aHeldChangeKeepsTheIdThroughCommitHeldWithAndWithoutAmendedChanges() = runTest {
        NoNetworkGuard.during {
            val pipeline = callIdPipeline(ScriptedGate.holdAll("confirm"), RecordingCommitSink()) {
                Resolution.Steps(listOf(ToolStep.Mutation(write("a")), ToolStep.Mutation(write("b"))))
            }

            val outcome = pipeline.execute(command)
            val held = outcome.held.single()

            assertEquals(List(2) { ActionKind.HELD }, outcome.executed.map { it.kind })
            assertEquals(List(2) { CALL_ID }, outcome.executed.map { it.providerCallId })
            val plain = pipeline.commitHeld(held)
            assertEquals(List(2) { ActionKind.COMMITTED }, plain.executed.map { it.kind })
            assertEquals(List(2) { CALL_ID }, plain.executed.map { it.providerCallId })

            val second = callIdPipeline(ScriptedGate.holdAll("confirm"), RecordingCommitSink()) {
                Resolution.Steps(listOf(ToolStep.Mutation(write("a"))))
            }
            val amended = second.commitHeld(second.execute(command).held.single(), listOf(write("changed")))
            assertEquals(ActionKind.COMMITTED, amended.executed.single().kind)
            assertEquals(CALL_ID, amended.executed.single().providerCallId)
        }
    }

    @Test
    fun aGateFaultStampsTheIdOnEveryErrorAction() = runTest {
        NoNetworkGuard.during {
            val gate = ScriptedGate { error("gate down") }
            val pipeline = callIdPipeline(gate, RecordingCommitSink()) {
                Resolution.Steps(listOf(ToolStep.Mutation(write("a")), ToolStep.Mutation(write("b"))))
            }

            val outcome = pipeline.execute(command)

            assertEquals(List(2) { ActionKind.IS_ERROR }, outcome.executed.map { it.kind })
            assertEquals(List(2) { CALL_ID }, outcome.executed.map { it.providerCallId })
        }
    }

    @Test
    fun aCancelledApplyJournalsItsErrorActionWithTheId() = runTest {
        NoNetworkGuard.during {
            val started = CompletableDeferred<Unit>()
            val stuck = FakeMutation(ENTRIES_TOOL, { started.complete(Unit); awaitCancellation() })
            val sink = RecordingCommitSink()
            val pipeline = callIdPipeline(ScriptedGate.admitAll(), sink) {
                Resolution.Steps(listOf(ToolStep.Mutation(stuck)))
            }

            val job = launch { pipeline.execute(command) }
            started.await()
            job.cancel()
            job.join()

            val action = sink.actions.single().action
            assertEquals(ActionKind.IS_ERROR, action.kind)
            assertEquals(CALL_ID, action.providerCallId)
        }
    }
}
