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
import io.github.ygaray.voiceactionengine.core.strategy.UserTurnContext
import io.github.ygaray.voiceactionengine.core.strategy.UserTurnRenderer
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
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

private const val TOOL_NAME = "record_entries"
private const val SYSTEM_MARKER = "SYSTEM-MARKER-4471"
private const val ARGUMENT_MARKER = "ARGUMENT-MARKER-4471"
private const val REPLY_MARKER = "REPLY-MARKER-4471"
private const val TRANSCRIPT_MARKER = "TRANSCRIPT-MARKER-4471"
private const val LOS_ANGELES = "America/Los_Angeles"
private const val INSTANT = "2026-10-01T16:30:12.345Z"

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

    private fun dateTimeIn(zone: String): ZonedDateTime =
        ZonedDateTime.ofInstant(Instant.parse(INSTANT), ZoneId.of(zone))

    @Test
    fun theStandardRendererFramesTheTranscriptWithTheLocalDateTimeInLosAngeles() = runTest {
        val input = CommandInput("add two things", "en", null)
        val context = UserTurnContext(input, dateTimeIn(LOS_ANGELES), null)

        val text = UserTurnRenderer.standard().render(context)

        assertEquals("Current local date-time: 2026-10-01T09:30:12-07:00 (America/Los_Angeles)\n\nadd two things", text)
    }

    @Test
    fun theStandardRendererFramesTheTranscriptWithTheLocalDateTimeInUtc() = runTest {
        val context = UserTurnContext(CommandInput("add two things", "en", null), dateTimeIn("UTC"), null)

        val text = UserTurnRenderer.standard().render(context)

        assertEquals("Current local date-time: 2026-10-01T16:30:12Z (UTC)\n\nadd two things", text)
    }

    @Test
    fun theStandardRendererLeavesOutTheLanguageAndTheContext() = runTest {
        val input = CommandInput("add two things", "es", ReplyCarry("CONTEXT-MARKER-4471"))
        val context = UserTurnContext(input, dateTimeIn("UTC"), ReplyCarry("CARRY-MARKER-4471"))

        val text = UserTurnRenderer.standard().render(context)

        assertEquals("Current local date-time: 2026-10-01T16:30:12Z (UTC)\n\nadd two things", text)
    }

    @Test
    fun aLambdaRendererIsCalledWithTheCommand() = runTest {
        val renderer = UserTurnRenderer { "FRAMED[" + it.input.transcript + "]" }
        val context = UserTurnContext(CommandInput("add two things", "en", null), dateTimeIn("UTC"), null)

        assertEquals("FRAMED[add two things]", renderer.render(context))
    }

    @Test
    fun aContextExposesItsPartsUnchangedAndPrintsNoContent() {
        val input = CommandInput(TRANSCRIPT_MARKER, "en", ReplyCarry("CONTEXT-MARKER-4471"))
        val carry = ReplyCarry("CARRY-MARKER-4471")
        val dateTime = dateTimeIn("UTC")

        val context = UserTurnContext(input, dateTime, carry)

        assertSame(input, context.input)
        assertSame(dateTime, context.dateTime)
        assertSame(carry, context.carry)
        val printed = context.toString()
        assertFalse(printed, printed.contains(TRANSCRIPT_MARKER))
        assertFalse(printed, printed.contains("CONTEXT-MARKER-4471"))
        assertFalse(printed, printed.contains("CARRY-MARKER-4471"))
        assertTrue(printed, printed.contains("transcriptLength=${TRANSCRIPT_MARKER.length}"))
        assertTrue(printed, printed.contains("carry=ReplyCarry"))
    }

    private class ReplyCarry(val text: String) {
        override fun toString(): String = text
    }
}
