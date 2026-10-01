package io.github.ygaray.voiceactionengine.core

import io.github.ygaray.voiceactionengine.core.commit.ActionKind
import io.github.ygaray.voiceactionengine.core.commit.StepResult
import io.github.ygaray.voiceactionengine.core.commit.ToolStep
import io.github.ygaray.voiceactionengine.core.pipeline.CommandOutcome
import io.github.ygaray.voiceactionengine.core.provider.ModelResult
import io.github.ygaray.voiceactionengine.core.testing.FakeAiProvider
import io.github.ygaray.voiceactionengine.core.testing.FakeMutation
import io.github.ygaray.voiceactionengine.core.testing.NoNetworkGuard
import io.github.ygaray.voiceactionengine.core.testing.RecordingCommitSink
import io.github.ygaray.voiceactionengine.core.testing.RecordingSink
import io.github.ygaray.voiceactionengine.core.testing.ScriptedGate
import io.github.ygaray.voiceactionengine.core.testing.ScriptedToolExecutor
import io.github.ygaray.voiceactionengine.core.transcript.ToolResultsMessage
import io.github.ygaray.voiceactionengine.core.transcript.UserMessage
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

private const val CALL_ONE = "call_1"
private const val COORDINATOR_CONTENT = """{"saved":1}"""
private const val FINAL_REPLY = "done"

/** The loop's dispatch path: a tool turn runs through the app's executor, the gate and the sink, and the run ends. */
class AgenticLoopDispatchTest {

    @Test
    fun aToolTurnThenProseCommitsThroughTheGateAndCompletes() = runTest {
        NoNetworkGuard.during {
            val log = RecordingSink<String>()
            val write = FakeMutation(SAVE_TOOL, StepResult(COORDINATOR_CONTENT), log = log)
            val executor = ScriptedToolExecutor.sequence(log, ToolStep.Mutation(write))
            val gate = ScriptedGate.admitAll(log)
            val sink = RecordingCommitSink(log)
            val firstTurn = toolTurn(1, callOf(CALL_ONE, SAVE_TOOL, loopArguments()))
            val fake = FakeAiProvider(
                ProviderId.ANTHROPIC,
                firstTurn,
                FakeAiProvider.reply(FINAL_REPLY, usage(1)),
            )
            val strategy = agenticLoop(executor, loopSnapshotOf(writeTool(), readTool()))

            val outcome = loopPipeline(listOf(strategy), fake, gate, sink)
                .execute(CommandInput("add two things", "en", null))

            assertTrue(outcome.toString(), outcome is CommandOutcome.Completed)
            outcome as CommandOutcome.Completed
            assertEquals(FINAL_REPLY, outcome.reply)
            assertFalse(outcome.partial)
            assertEquals(1, write.applyCount)
            assertEquals(listOf(ActionKind.COMMITTED), sink.actions.map { it.action.kind })
            val committedAt = log.events.indexOfFirst { it.startsWith("sink:action:") }
            val closedAt = log.events.indexOfFirst { it.startsWith("sink:closed:") }
            assertTrue(log.events.toString(), committedAt in 0 until closedAt)
            assertEquals(2, fake.callCount)

            val first = fake.calls[0].request.messages
            val second = fake.calls[1].request.messages
            assertEquals(3, second.size)
            assertSame(first[0], second[0])
            assertTrue(second[0] is UserMessage)
            assertSame((firstTurn as ModelResult.Success).response.message, second[1])
            val results = (second[2] as ToolResultsMessage).results.single()
            assertEquals(CALL_ONE, results.callId)
            assertEquals(COORDINATOR_CONTENT, results.content)
            assertFalse(results.isError)
        }
    }
}
