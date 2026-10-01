package io.github.ygaray.voiceactionengine.core

import io.github.ygaray.voiceactionengine.core.commit.ActionKind
import io.github.ygaray.voiceactionengine.core.commit.StepResult
import io.github.ygaray.voiceactionengine.core.commit.ToolStep
import io.github.ygaray.voiceactionengine.core.failure.EscalationReason
import io.github.ygaray.voiceactionengine.core.failure.FailureReason
import io.github.ygaray.voiceactionengine.core.pipeline.CommandOutcome
import io.github.ygaray.voiceactionengine.core.pipeline.commandPipeline
import io.github.ygaray.voiceactionengine.core.provider.ProviderSelection
import io.github.ygaray.voiceactionengine.core.strategy.Extraction
import io.github.ygaray.voiceactionengine.core.strategy.OutcomeResolver
import io.github.ygaray.voiceactionengine.core.strategy.Resolution
import io.github.ygaray.voiceactionengine.core.strategy.StrategyOutcome
import io.github.ygaray.voiceactionengine.core.strategy.ToolSpec
import io.github.ygaray.voiceactionengine.core.strategy.ToolSpecProvider
import io.github.ygaray.voiceactionengine.core.strategy.ToolingSnapshot
import io.github.ygaray.voiceactionengine.core.telemetry.Usage
import io.github.ygaray.voiceactionengine.core.testing.FakeAiProvider
import io.github.ygaray.voiceactionengine.core.testing.FakeMutation
import io.github.ygaray.voiceactionengine.core.testing.NoNetworkGuard
import io.github.ygaray.voiceactionengine.core.testing.RecordingCommitSink
import io.github.ygaray.voiceactionengine.core.testing.ScriptedCredentialSource
import io.github.ygaray.voiceactionengine.core.testing.ScriptedGate
import io.github.ygaray.voiceactionengine.core.testing.ScriptedSelectionSource
import io.github.ygaray.voiceactionengine.core.testing.ScriptedStrategy
import io.github.ygaray.voiceactionengine.core.testing.StrategyStep
import io.github.ygaray.voiceactionengine.core.transcript.CacheDirective
import io.github.ygaray.voiceactionengine.core.transcript.ModelRequest
import io.github.ygaray.voiceactionengine.core.provider.ModelResult
import io.github.ygaray.voiceactionengine.core.transcript.ToolChoice
import io.github.ygaray.voiceactionengine.core.transcript.UserMessage
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

private const val TOOL_NAME = "record_entries"
private const val SYSTEM_MARKER = "SYSTEM-MARKER-4471"
private const val ARGUMENT_MARKER = "ARGUMENT-MARKER-4471"
private const val REPLY_MARKER = "REPLY-MARKER-4471"

/** The app seams a single-shot tier is built on compose with the shipped write path before any strategy exists. */
class SingleShotSeamTypesTest {

    private class Run(val outcome: CommandOutcome, val sink: RecordingCommitSink, val gate: ScriptedGate)

    private fun entriesTool(): ToolSpec =
        ToolSpec(TOOL_NAME, "Records entries.", buildJsonObject { put("type", "object") }, mutating = true)

    private fun arguments(): JsonObject = buildJsonObject { put("text", ARGUMENT_MARKER) }

    private fun answeringWithTheTool(): FakeAiProvider =
        FakeAiProvider(
            ProviderId.ANTHROPIC,
            FakeAiProvider.toolCall("call-1", TOOL_NAME, arguments(), Usage(1, 0, 0, 1)),
        )

    /** Does by hand what the single-shot strategy will do, using only the new seams and the shipped session. */
    private suspend fun runByHand(gate: ScriptedGate, write: FakeMutation): Run {
        val sink = RecordingCommitSink()
        val resolver = OutcomeResolver { _, _ -> Resolution.Steps(listOf(ToolStep.Mutation(write))) }
        val tooling = ToolSpecProvider.fixed(ToolingSnapshot("fixed system", listOf(entriesTool()), TOOL_NAME))
        val step: StrategyStep = { input, session ->
            val snapshot = tooling.tooling(input)
            val request = ModelRequest(
                snapshot.system,
                listOf(UserMessage(input.transcript)),
                snapshot.tools,
                ToolChoice.Required(snapshot.singleShotTool!!),
                session.policy.maxTokensPerTurn,
                CacheDirective(true),
                true,
            )
            val result = session.model().complete(request) as ModelResult.Success
            val call = result.response.message.toolCalls.first()
            val resolution = resolver.resolve(Extraction(call.name, call.arguments), input)
            (resolution as Resolution.Steps).steps.forEach { session.submit(it) }
            StrategyOutcome.Completed(null)
        }
        val pipeline = commandPipeline {
            tier(ScriptedStrategy(StrategyId("seams"), step))
            provider(answeringWithTheTool())
            providerSelection = ScriptedSelectionSource.fixed(ProviderSelection(ProviderId.ANTHROPIC, "test-model"))
            credentials = ScriptedCredentialSource.keys(ProviderId.ANTHROPIC to "test-key")
            this.gate = gate
            commitSink = sink
        }
        return Run(pipeline.execute(CommandInput("add two things", "en", null)), sink, gate)
    }

    @Test
    fun theSeamsComposeWithAnAdmittingGateAndTheSinkSeesOneCommit() = runTest {
        NoNetworkGuard.during {
            val write = FakeMutation(TOOL_NAME, StepResult("saved", false, "ok", emptyMap()))

            val run = runByHand(ScriptedGate.admitAll(), write)

            assertTrue(run.outcome.toString(), run.outcome is CommandOutcome.Completed)
            assertEquals(1, run.gate.calls)
            assertEquals(1, write.applyCount)
            assertEquals(listOf(ActionKind.COMMITTED), run.sink.actions.map { it.action.kind })
            assertEquals(1, run.outcome.commits.size)
        }
    }

    @Test
    fun theSeamsComposeWithAHoldingGateAndNothingIsWritten() = runTest {
        NoNetworkGuard.during {
            val write = FakeMutation(TOOL_NAME, StepResult("saved", false, "ok", emptyMap()))

            val run = runByHand(ScriptedGate.holdAll("confirm"), write)

            assertEquals(1, run.gate.calls)
            assertEquals(0, write.applyCount)
            assertEquals(1, run.outcome.held.size)
            assertTrue(run.outcome.commits.isEmpty())
        }
    }

    @Test
    fun aFixedProviderReturnsTheSameSnapshotForEveryCommand() = runTest {
        val snapshot = ToolingSnapshot("system", listOf(entriesTool()), TOOL_NAME)
        val provider = ToolSpecProvider.fixed(snapshot)

        assertSame(snapshot, provider.tooling(CommandInput("one")))
        assertSame(snapshot, provider.tooling(CommandInput("two")))
    }

    @Test
    fun aSnapshotRejectsAForcedToolThatIsNotOffered() {
        assertThrows(IllegalArgumentException::class.java) {
            ToolingSnapshot("system", listOf(entriesTool()), "not_offered")
        }
    }

    @Test
    fun aSnapshotAcceptsNoForcedTool() {
        assertNull(ToolingSnapshot("system", listOf(entriesTool()), null).singleShotTool)
    }

    @Test
    fun aSnapshotKeepsACopyOfItsTools() {
        val tools = mutableListOf(entriesTool())
        val snapshot = ToolingSnapshot("system", tools, TOOL_NAME)

        tools.clear()

        assertEquals(1, snapshot.tools.size)
    }

    @Test
    fun anExtractionRejectsABlankToolName() {
        assertThrows(IllegalArgumentException::class.java) { Extraction("  ", arguments()) }
    }

    @Test
    fun stepsRejectAnEmptyList() {
        assertThrows(IllegalArgumentException::class.java) { Resolution.Steps(emptyList()) }
    }

    @Test
    fun stepsKeepACopyOfTheirList() {
        val list = mutableListOf<ToolStep>(ToolStep.Mutation(FakeMutation("w", StepResult("ok"))))
        val steps = Resolution.Steps(list, "done")

        list.clear()

        assertEquals(1, steps.steps.size)
        assertEquals("done", steps.reply)
        assertNull(Resolution.Steps(steps.steps).reply)
        assertNotSame(list, steps.steps)
    }

    @Test
    fun noToStringPrintsArgumentValuesSystemTextOrReplyText() {
        val snapshot = ToolingSnapshot(SYSTEM_MARKER, listOf(entriesTool()), TOOL_NAME)
        val extraction = Extraction(TOOL_NAME, arguments())
        val steps = Resolution.Steps(listOf(ToolStep.Mutation(FakeMutation("w", StepResult("ok")))), REPLY_MARKER)
        val escalate = Resolution.Escalate(EscalationReason.NoToolCall(), ReplyCarry(REPLY_MARKER))
        val printed = listOf(
            snapshot.toString(),
            extraction.toString(),
            steps.toString(),
            Resolution.NoMatch().toString(),
            escalate.toString(),
            Resolution.Failed(FailureReason.Refusal(), null).toString(),
        )

        printed.forEach { text ->
            assertFalse(text, text.contains(SYSTEM_MARKER))
            assertFalse(text, text.contains(ARGUMENT_MARKER))
            assertFalse(text, text.contains(REPLY_MARKER))
        }
        assertTrue(snapshot.toString(), snapshot.toString().contains("systemLength=${SYSTEM_MARKER.length}"))
        assertTrue(extraction.toString(), extraction.toString().contains("argumentCount=1"))
        assertTrue(steps.toString(), steps.toString().contains("replyLength=${REPLY_MARKER.length}"))
        assertTrue(escalate.toString(), escalate.toString().contains("ReplyCarry"))
    }

    private class ReplyCarry(val text: String) {
        override fun toString(): String = text
    }
}
