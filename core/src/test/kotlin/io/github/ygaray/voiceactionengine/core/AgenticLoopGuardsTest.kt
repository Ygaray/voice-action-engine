package io.github.ygaray.voiceactionengine.core

import io.github.ygaray.voiceactionengine.core.commit.ActionKind
import io.github.ygaray.voiceactionengine.core.commit.FinishedKind
import io.github.ygaray.voiceactionengine.core.commit.StepResult
import io.github.ygaray.voiceactionengine.core.commit.ToolStep
import io.github.ygaray.voiceactionengine.core.failure.FailureReason
import io.github.ygaray.voiceactionengine.core.pipeline.CommandOutcome
import io.github.ygaray.voiceactionengine.core.provider.ModelResult
import io.github.ygaray.voiceactionengine.core.strategy.agentic.AgenticLoopStrategy
import io.github.ygaray.voiceactionengine.core.testing.FakeAiProvider
import io.github.ygaray.voiceactionengine.core.testing.FakeMutation
import io.github.ygaray.voiceactionengine.core.testing.NoNetworkGuard
import io.github.ygaray.voiceactionengine.core.testing.RecordingCommitSink
import io.github.ygaray.voiceactionengine.core.testing.RecordingSink
import io.github.ygaray.voiceactionengine.core.testing.ScriptedGate
import io.github.ygaray.voiceactionengine.core.testing.ScriptedToolExecutor
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

private const val LOG_TOOL = "log_entry"
private const val FINAL_REPLY = "done"

/** The loop's guards: the per-tool-name strike counter and, later, the validation and budget guards. */
class AgenticLoopGuardsTest {

    private fun rejected(name: String = SAVE_TOOL): ToolStep =
        ToolStep.Finished(name, FinishedKind.ERROR, StepResult("rejected:$name", true))

    private fun committed(name: String = SAVE_TOOL, log: RecordingSink<String>? = null): ToolStep =
        ToolStep.Mutation(FakeMutation(name, StepResult("saved:$name"), log = log))

    private fun callTurn(vararg names: String): ModelResult =
        toolTurn(1, *names.mapIndexed { index, name -> callOf("c$index-$name", name, loopArguments()) }.toTypedArray())

    private fun prose(): ModelResult = FakeAiProvider.reply(FINAL_REPLY, usage(1))

    private fun snapshot() = loopSnapshotOf(writeTool(), writeTool(LOG_TOOL), readTool())

    private suspend fun run(
        fake: FakeAiProvider,
        strategy: AgenticLoopStrategy,
        gate: ScriptedGate = ScriptedGate.admitAll(),
        sink: RecordingCommitSink = RecordingCommitSink(),
    ): CommandOutcome =
        loopPipeline(listOf(strategy), fake, gate, sink).execute(CommandInput("add two things", "en", null))

    private fun assertToolFailure(outcome: CommandOutcome) {
        assertTrue(outcome.toString(), outcome is CommandOutcome.Failed)
        assertEquals(FailureReason.ToolFailure(), (outcome as CommandOutcome.Failed).reason)
    }

    @Test
    fun repeatedFailureOfTheSameToolAbortsButTheCommittedSiblingIsStillRecorded() = runTest {
        NoNetworkGuard.during {
            val log = RecordingSink<String>()
            val fake = FakeAiProvider(
                ProviderId.ANTHROPIC,
                callTurn(SAVE_TOOL),
                callTurn(SAVE_TOOL, LOG_TOOL),
            )
            val executor = ScriptedToolExecutor.sequence(null, rejected(), rejected(), committed(LOG_TOOL))
            val sink = RecordingCommitSink(log)

            val outcome = run(fake, agenticLoop(executor, snapshot()), sink = sink)

            assertToolFailure(outcome)
            assertEquals(2, fake.callCount)
            assertEquals(3, executor.callCount)
            assertEquals(
                listOf(ActionKind.IS_ERROR, ActionKind.IS_ERROR, ActionKind.COMMITTED),
                outcome.executed.map { it.kind },
            )
            assertEquals(listOf(LOG_TOOL), outcome.commits.map { it.toolName })
            assertEquals(3, sink.actions.size)
            val closedAt = log.events.indexOfFirst { it.startsWith("sink:closed:") }
            assertEquals(log.events.toString(), 3, log.events.take(closedAt).count { it.startsWith("sink:action:") })
        }
    }

    @Test
    fun strikesCountPerToolNameAcrossTurns() = runTest {
        NoNetworkGuard.during {
            val fake = FakeAiProvider(
                ProviderId.ANTHROPIC,
                callTurn(SAVE_TOOL),
                callTurn(SAVE_TOOL),
                callTurn(SAVE_TOOL),
            )
            val executor = ScriptedToolExecutor.sequence(null, rejected(), committed(), rejected())

            val outcome = run(fake, agenticLoop(executor, snapshot()))

            assertToolFailure(outcome)
            assertEquals(3, fake.callCount)
        }
    }

    @Test
    fun twoDifferentToolsFailingOnceEachDoNotAbort() = runTest {
        NoNetworkGuard.during {
            val fake = FakeAiProvider(ProviderId.ANTHROPIC, callTurn(SAVE_TOOL, LOG_TOOL), prose())
            val executor = ScriptedToolExecutor.sequence(null, rejected(), rejected(LOG_TOOL))

            val outcome = run(fake, agenticLoop(executor, snapshot()))

            assertTrue(outcome.toString(), outcome is CommandOutcome.Completed)
            assertEquals(FINAL_REPLY, (outcome as CommandOutcome.Completed).reply)
            assertEquals(2, fake.callCount)
        }
    }

    @Test
    fun anUnknownToolCountsAsAStrike() = runTest {
        NoNetworkGuard.during {
            val fake = FakeAiProvider(ProviderId.ANTHROPIC, callTurn("ghost_tool"), callTurn("ghost_tool"))
            val executor = ScriptedToolExecutor.sequence(null)

            val outcome = run(fake, agenticLoop(executor, snapshot()))

            assertToolFailure(outcome)
            assertEquals(2, fake.callCount)
            assertEquals(0, executor.callCount)
        }
    }

    @Test
    fun aPrepareFaultAndAnApplyErrorCountAsStrikes() = runTest {
        NoNetworkGuard.during {
            val fake = FakeAiProvider(ProviderId.ANTHROPIC, callTurn(SAVE_TOOL), callTurn(SAVE_TOOL))
            val asked = AtomicInteger()
            val failingApply = FakeMutation(SAVE_TOOL, { throw IllegalStateException("apply failed") })
            val executor = ScriptedToolExecutor(null) { _, _ ->
                if (asked.incrementAndGet() == 1) error("prepare failed")
                ToolStep.Mutation(failingApply)
            }

            val outcome = run(fake, agenticLoop(executor, snapshot()))

            assertToolFailure(outcome)
            assertEquals(2, fake.callCount)
            assertEquals(listOf(ActionKind.IS_ERROR, ActionKind.IS_ERROR), outcome.executed.map { it.kind })
        }
    }

    @Test
    fun aHeldCallIsNotAStrike() = runTest {
        NoNetworkGuard.during {
            val fake = FakeAiProvider(
                ProviderId.ANTHROPIC,
                callTurn(SAVE_TOOL),
                callTurn(SAVE_TOOL),
                callTurn(SAVE_TOOL),
                prose(),
            )
            val executor = ScriptedToolExecutor.sequence(null, committed(), committed(), committed())

            val outcome = run(fake, agenticLoop(executor, snapshot()), ScriptedGate.holdAll())

            assertTrue(outcome.toString(), outcome is CommandOutcome.Completed)
            assertEquals(3, outcome.held.size)
            assertEquals(4, fake.callCount)
        }
    }
}
