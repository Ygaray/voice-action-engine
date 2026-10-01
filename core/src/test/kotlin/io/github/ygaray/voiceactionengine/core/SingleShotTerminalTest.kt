package io.github.ygaray.voiceactionengine.core

import io.github.ygaray.voiceactionengine.core.commit.StepResult
import io.github.ygaray.voiceactionengine.core.commit.ToolStep
import io.github.ygaray.voiceactionengine.core.pipeline.CommandOutcome
import io.github.ygaray.voiceactionengine.core.provider.ModelResult
import io.github.ygaray.voiceactionengine.core.strategy.Resolution
import io.github.ygaray.voiceactionengine.core.strategy.ToolingSnapshot
import io.github.ygaray.voiceactionengine.core.strategy.singleshot.SingleShotStrategy
import io.github.ygaray.voiceactionengine.core.telemetry.TraceCode
import io.github.ygaray.voiceactionengine.core.testing.FakeAiProvider
import io.github.ygaray.voiceactionengine.core.testing.FakeMutation
import io.github.ygaray.voiceactionengine.core.testing.NoNetworkGuard
import io.github.ygaray.voiceactionengine.core.testing.RecordingCommitSink
import io.github.ygaray.voiceactionengine.core.testing.ScriptedGate
import io.github.ygaray.voiceactionengine.core.transcript.StopReason
import io.github.ygaray.voiceactionengine.core.transcript.ToolChoice
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val QUESTION = "Which list do you mean?"

/** A terminal tool call ends the tier as a completed outcome carrying the call, without the resolver or the gate. */
class SingleShotTerminalTest {

    private class Run(
        val outcome: CommandOutcome,
        val resolver: RecordingResolver,
        val gate: ScriptedGate,
        val fake: FakeAiProvider,
    ) {
        val completed: CommandOutcome.Completed get() = outcome as CommandOutcome.Completed
    }

    private fun clarificationArguments(): JsonObject = buildJsonObject {
        put(
            "options",
            buildJsonArray {
                add(
                    buildJsonObject {
                        put("id", "a")
                        put("label", "Groceries")
                    },
                )
            },
        )
        put("question", QUESTION)
    }

    private suspend fun runWith(
        result: ModelResult,
        snapshot: ToolingSnapshot,
        configure: SingleShotStrategy.Builder.() -> Unit = {},
    ): Run {
        val resolver = RecordingResolver { _, _ ->
            Resolution.Steps(listOf(ToolStep.Mutation(FakeMutation(ENTRIES_TOOL, StepResult("saved")))))
        }
        val fake = FakeAiProvider(ProviderId.ANTHROPIC, result)
        val gate = ScriptedGate.admitAll()
        val tier = singleShot(resolver, snapshot, configure = configure)
        val pipeline = pipelineOf(listOf(tier), fake, gate, RecordingCommitSink())
        return Run(pipeline.execute(CommandInput("which one", "en", null)), resolver, gate, fake)
    }

    private fun askCall(): ModelResult =
        answerOf(StopReason.TOOL_USE, callOf("call-ask", ASK_TOOL, clarificationArguments()))

    private fun assertEndedWithTheClarification(run: Run) {
        val completed = run.completed
        assertNull(completed.reply)
        val terminal = checkNotNull(completed.terminalCall)
        assertEquals(ASK_TOOL, terminal.toolName)
        assertEquals(clarificationArguments(), terminal.arguments)
        assertEquals(QUESTION, terminal.asClarification()?.question)
        assertEquals(0, run.resolver.invocations)
        assertEquals(0, run.gate.calls)
    }

    @Test
    fun aForcedTierThatTheModelAnswersWithTheClarificationToolEndsWithTheTerminalCall() = runTest {
        NoNetworkGuard.during {
            val run = runWith(askCall(), snapshotOf(entriesTool(), askTool()))

            assertEndedWithTheClarification(run)
            assertEquals(ToolChoice.Required(ENTRIES_TOOL), run.fake.calls.single().request.toolChoice)
        }
    }

    @Test
    fun aTierThatLetsTheModelChooseEndsTheSameWayWithAutoToolChoice() = runTest {
        NoNetworkGuard.during {
            val run = runWith(askCall(), snapshotOf(entriesTool(), askTool())) { forceTool = false }

            assertEndedWithTheClarification(run)
            assertEquals(ToolChoice.Auto(), run.fake.calls.single().request.toolChoice)
        }
    }

    @Test
    fun aTerminalFirstCallFollowedByAnExtraCallStillRecordsTheDroppedCallsCode() = runTest {
        NoNetworkGuard.during {
            val result = answerOf(
                StopReason.TOOL_USE,
                callOf("call-ask", ASK_TOOL, clarificationArguments()),
                callOf("call-extra", ENTRIES_TOOL, entriesArguments("extra")),
            )

            val run = runWith(result, snapshotOf(entriesTool(), askTool()))

            assertEndedWithTheClarification(run)
            assertEquals(1, run.outcome.trace.codes.count { it == TraceCode.EXTRA_TOOL_CALLS_DROPPED })
            assertTrue((run.outcome as CommandOutcome.Completed).partial)
        }
    }

    @Test
    fun aTerminalToolThatIsTheForcedToolAlsoEndsTheTierWithoutTheResolver() = runTest {
        NoNetworkGuard.during {
            val run = runWith(askCall(), snapshotOf(entriesTool(), askTool(), forced = ASK_TOOL))

            assertEndedWithTheClarification(run)
            assertEquals(ToolChoice.Required(ASK_TOOL), run.fake.calls.single().request.toolChoice)
        }
    }

    @Test
    fun aTerminalOutcomeCommitsNothing() = runTest {
        NoNetworkGuard.during {
            val run = runWith(askCall(), snapshotOf(entriesTool(), askTool()))

            assertTrue(run.outcome.commits.isEmpty())
            assertTrue(run.outcome.held.isEmpty())
        }
    }
}
