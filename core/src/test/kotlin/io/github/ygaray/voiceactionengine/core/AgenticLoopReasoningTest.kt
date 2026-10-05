package io.github.ygaray.voiceactionengine.core

import io.github.ygaray.voiceactionengine.core.commit.StepResult
import io.github.ygaray.voiceactionengine.core.commit.ToolStep
import io.github.ygaray.voiceactionengine.core.pipeline.CommandOutcome
import io.github.ygaray.voiceactionengine.core.strategy.agentic.AgenticLoopStrategy
import io.github.ygaray.voiceactionengine.core.telemetry.Usage
import io.github.ygaray.voiceactionengine.core.testing.FakeAiProvider
import io.github.ygaray.voiceactionengine.core.testing.FakeMutation
import io.github.ygaray.voiceactionengine.core.testing.NoNetworkGuard
import io.github.ygaray.voiceactionengine.core.testing.RecordingCommitSink
import io.github.ygaray.voiceactionengine.core.testing.ScriptedGate
import io.github.ygaray.voiceactionengine.core.testing.ScriptedToolExecutor
import io.github.ygaray.voiceactionengine.core.transcript.ReasoningMode
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val TURN_TOKENS = 1L

/** The reasoning knob of the agentic loop: every turn's request carries the builder's mode. */
class AgenticLoopReasoningTest {

    private suspend fun twoTurnRun(configure: AgenticLoopStrategy.Builder.() -> Unit): FakeAiProvider {
        val fake = FakeAiProvider(
            ProviderId.ANTHROPIC,
            toolTurn(TURN_TOKENS, callOf("c1", SAVE_TOOL, loopArguments())),
            FakeAiProvider.reply("done", Usage(0, 0, 0, TURN_TOKENS)),
        )
        val executor = ScriptedToolExecutor(null) { call, _ ->
            ToolStep.Mutation(FakeMutation(call.toolName, StepResult("saved")))
        }
        val loop = agenticLoop(executor, loopSnapshotOf(writeTool(), readTool()), configure = configure)
        val pipeline = loopPipeline(listOf(loop), fake, ScriptedGate.admitAll(), RecordingCommitSink())
        val outcome = pipeline.execute(CommandInput("add a thing", "en", null))
        assertTrue(outcome.toString(), outcome is CommandOutcome.Completed)
        return fake
    }

    @Test
    fun withoutReasoningEveryTurnSendsOff() = runTest {
        NoNetworkGuard.during {
            val fake = twoTurnRun {}

            assertEquals(2, fake.callCount)
            assertEquals(listOf(ReasoningMode.OFF, ReasoningMode.OFF), fake.calls.map { it.request.reasoning })
        }
    }

    @Test
    fun theBuildersReasoningIsOnEveryTurn() = runTest {
        NoNetworkGuard.during {
            val fake = twoTurnRun { reasoning = ReasoningMode.PROVIDER_DEFAULT }

            assertEquals(2, fake.callCount)
            assertEquals(
                listOf(ReasoningMode.PROVIDER_DEFAULT, ReasoningMode.PROVIDER_DEFAULT),
                fake.calls.map { it.request.reasoning },
            )
        }
    }
}
