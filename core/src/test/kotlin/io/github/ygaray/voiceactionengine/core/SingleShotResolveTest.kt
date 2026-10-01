package io.github.ygaray.voiceactionengine.core

import io.github.ygaray.voiceactionengine.core.commit.ActionKind
import io.github.ygaray.voiceactionengine.core.commit.FinishedKind
import io.github.ygaray.voiceactionengine.core.commit.StepResult
import io.github.ygaray.voiceactionengine.core.commit.ToolStep
import io.github.ygaray.voiceactionengine.core.pipeline.CommandOutcome
import io.github.ygaray.voiceactionengine.core.strategy.Resolution
import io.github.ygaray.voiceactionengine.core.telemetry.Usage
import io.github.ygaray.voiceactionengine.core.testing.FakeAiProvider
import io.github.ygaray.voiceactionengine.core.testing.FakeMutation
import io.github.ygaray.voiceactionengine.core.testing.NoNetworkGuard
import io.github.ygaray.voiceactionengine.core.testing.RecordingCommitSink
import io.github.ygaray.voiceactionengine.core.testing.ScriptedGate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

private const val ARGUMENT_MARKER = "target-2026-10-02"

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
}
