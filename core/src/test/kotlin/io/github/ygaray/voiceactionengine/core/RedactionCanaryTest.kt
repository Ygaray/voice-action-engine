package io.github.ygaray.voiceactionengine.core

import io.github.ygaray.voiceactionengine.core.commit.DispatchResult
import io.github.ygaray.voiceactionengine.core.commit.FinishedKind
import io.github.ygaray.voiceactionengine.core.commit.GateDecision
import io.github.ygaray.voiceactionengine.core.commit.StepResult
import io.github.ygaray.voiceactionengine.core.commit.ToolStep
import io.github.ygaray.voiceactionengine.core.failure.EscalationReason
import io.github.ygaray.voiceactionengine.core.failure.FailureReason
import io.github.ygaray.voiceactionengine.core.pipeline.CommandOutcome
import io.github.ygaray.voiceactionengine.core.pipeline.commandPipeline
import io.github.ygaray.voiceactionengine.core.provider.BoundModel
import io.github.ygaray.voiceactionengine.core.provider.CredentialLookup
import io.github.ygaray.voiceactionengine.core.provider.CredentialSource
import io.github.ygaray.voiceactionengine.core.provider.ModelCapabilities
import io.github.ygaray.voiceactionengine.core.provider.ModelResult
import io.github.ygaray.voiceactionengine.core.provider.OnDeviceAvailability
import io.github.ygaray.voiceactionengine.core.provider.ProviderRequest
import io.github.ygaray.voiceactionengine.core.provider.ProviderSelection
import io.github.ygaray.voiceactionengine.core.strategy.CommandSession
import io.github.ygaray.voiceactionengine.core.strategy.Extraction
import io.github.ygaray.voiceactionengine.core.strategy.Resolution
import io.github.ygaray.voiceactionengine.core.strategy.StrategyCapabilities
import io.github.ygaray.voiceactionengine.core.strategy.StrategyOutcome
import io.github.ygaray.voiceactionengine.core.strategy.TerminalCall
import io.github.ygaray.voiceactionengine.core.strategy.ToolSpec
import io.github.ygaray.voiceactionengine.core.strategy.ToolingSnapshot
import io.github.ygaray.voiceactionengine.core.strategy.UserTurnContext
import io.github.ygaray.voiceactionengine.core.telemetry.PipelineEvent
import io.github.ygaray.voiceactionengine.core.telemetry.TurnRecord
import io.github.ygaray.voiceactionengine.core.telemetry.Usage
import io.github.ygaray.voiceactionengine.core.testing.FakeAiProvider
import io.github.ygaray.voiceactionengine.core.testing.FakeMutation
import io.github.ygaray.voiceactionengine.core.testing.NoNetworkGuard
import io.github.ygaray.voiceactionengine.core.testing.ProviderStep
import io.github.ygaray.voiceactionengine.core.testing.RecordingCommitSink
import io.github.ygaray.voiceactionengine.core.testing.RecordingEventListener
import io.github.ygaray.voiceactionengine.core.testing.ScriptedCredentialSource
import io.github.ygaray.voiceactionengine.core.testing.ScriptedGate
import io.github.ygaray.voiceactionengine.core.testing.ScriptedSelectionSource
import io.github.ygaray.voiceactionengine.core.testing.ScriptedStrategy
import io.github.ygaray.voiceactionengine.core.testing.StrategyStep
import io.github.ygaray.voiceactionengine.core.transcript.AssistantMessage
import io.github.ygaray.voiceactionengine.core.transcript.AssistantPart
import io.github.ygaray.voiceactionengine.core.transcript.CacheDirective
import io.github.ygaray.voiceactionengine.core.transcript.Message
import io.github.ygaray.voiceactionengine.core.transcript.ModelRequest
import io.github.ygaray.voiceactionengine.core.transcript.ModelResponse
import io.github.ygaray.voiceactionengine.core.transcript.NativeReplay
import io.github.ygaray.voiceactionengine.core.transcript.StopReason
import io.github.ygaray.voiceactionengine.core.transcript.ToolChoice
import io.github.ygaray.voiceactionengine.core.transcript.ToolResult
import io.github.ygaray.voiceactionengine.core.transcript.ToolResultsMessage
import io.github.ygaray.voiceactionengine.core.transcript.UserMessage
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.CopyOnWriteArrayList

/**
 * An app object whose own text is a canary: if the engine ever prints the object itself instead of its class name,
 * the canary shows up in the output.
 */
private class Canary(private val label: String) {
    override fun toString(): String = "$CANARY-$label"
}

private const val CANARY = "CANARY"
private const val KEY = "sk-CANARY-KEY"
private const val OTHER_KEY = "sk-CANARY-OTHER-PROVIDER-KEY"
private const val ROUTED_MODEL = "model-a"
private const val CALL_ID = "call_1"

/** Everything the engine returns, delivers or prints is swept for the canaries planted in every user-content slot. */
class RedactionCanaryTest {

    private val printed = CopyOnWriteArrayList<String>()

    private fun see(value: Any?) {
        printed.add(value.toString())
    }

    private fun canaryResult() = StepResult("$CANARY-RESULT", false, "$CANARY-TOKEN", mapOf("id" to "$CANARY-ID"))

    private fun clarification(): TerminalCall = TerminalCall(
        "ask_user",
        buildJsonObject {
            put("question", "$CANARY-QUESTION")
            val one = option("$CANARY-OPTION-ONE", "$CANARY-LABEL-ONE")
            val two = option("$CANARY-OPTION-TWO", "$CANARY-LABEL-TWO")
            put("options", JsonArray(listOf(one, two)))
        },
    )

    private fun option(id: String, label: String): JsonObject = buildJsonObject {
        put("id", id)
        put("label", label)
    }

    private fun mutation(name: String) = FakeMutation(
        toolName = name,
        behavior = { canaryResult() },
        targetIds = mapOf("target" to "$CANARY-TARGET"),
        context = Canary("SNAPSHOT"),
    )

    private fun sweepOutcome(outcome: CommandOutcome) {
        see(outcome)
        see(outcome.trace)
        outcome.trace.attempts.forEach { attempt ->
            see(attempt)
            attempt.turns.forEach { see(it) }
            see(attempt.usage)
        }
        outcome.executed.forEach { see(it) }
        outcome.held.forEach { see(it) }
        when (outcome) {
            is CommandOutcome.Completed -> see(outcome.terminalCall)
            is CommandOutcome.Failed -> {
                see(outcome.reason)
                see(outcome.details)
            }
            is CommandOutcome.Unhandled -> see(outcome.lastReason)
        }
    }

    private val sessions = CopyOnWriteArrayList<CommandSession>()
    private val results = CopyOnWriteArrayList<DispatchResult>()

    /** Tier one previews, reports a turn and escalates with a carry whose own text is a canary. */
    private fun firstTier() = ScriptedStrategy(
        StrategyId("first"),
        { _, session ->
            sessions.add(session)
            val preview = ToolStep.Finished("lookup", FinishedKind.PREVIEW, canaryResult(), Canary("PREVIEW"))
            see(preview)
            results.add(session.submit(preview))
            val usage = Usage(1, 2, 3, 4)
            session.recordTurn(TurnRecord(ProviderId.ANTHROPIC, "claude-x", "tool_use", listOf("lookup"), usage, 5))
            StrategyOutcome.Escalate(EscalationReason.ModelDeclined(), Canary("CARRY")).also { see(it) }
        },
    )

    /** Tier two commits one change, has one held, and ends with a clarification as the terminal call. */
    private fun secondTier() = ScriptedStrategy(
        StrategyId("second"),
        { _, session ->
            sessions.add(session)
            val commit = ToolStep.Mutation(mutation("commit_tool"))
            val held = ToolStep.Mutation(listOf(mutation("held_tool"), mutation("held_tool_two")))
            see(commit)
            see(held)
            results.add(session.submit(commit))
            results.add(session.submit(held))
            StrategyOutcome.Completed(null, clarification()).also { see(it) }
        },
    )

    private fun sweepDelivered(gate: ScriptedGate, sink: RecordingCommitSink, events: List<PipelineEvent>) {
        gate.proposals.forEach { see(it) }
        results.forEach { see(it) }
        sessions.forEach { see(it) }
        sink.actions.forEach { see(it) }
        sink.closes.forEach { see(it) }
        events.forEach { see(it) }
        assertNotNull(gate.proposals.firstOrNull())
        checkEventFields(events)
        assertTrue(events.last() is PipelineEvent.RunClosed)
    }

    private suspend fun fullScriptedRun(): CommandOutcome {
        val listener = RecordingEventListener()
        val sink = RecordingCommitSink()
        val admit = GateDecision.Admit(listOf(mutation("amended_tool")))
        val hold = GateDecision.Hold(Canary("HOLD"), "$CANARY-HOLD-TOKEN")
        val gate = ScriptedGate.sequence(null, admit, hold)
        val pipeline = commandPipeline {
            tier(firstTier())
            tier(secondTier())
            this.gate = gate
            commitSink = sink
            this.listener = listener
        }
        val input = CommandInput("$CANARY-TRANSCRIPT", "en", Canary("CONTEXT"))
        see(input)
        see(Credential(ProviderId.ANTHROPIC, KEY))
        see(pipeline)
        see(admit)
        see(hold)
        val outcome = pipeline.execute(input)

        sweepOutcome(outcome)
        sweepDelivered(gate, sink, listener.events)
        (outcome as? CommandOutcome.Completed)?.terminalCall?.asClarification()?.let { clarification ->
            see(clarification)
            clarification.options.forEach { see(it) }
        }
        return outcome
    }

    private suspend fun throwingRun(): CommandOutcome {
        val listener = RecordingEventListener()
        val sink = RecordingCommitSink()
        val boom = FakeMutation("boom_tool", { error("$CANARY-APPLY-MESSAGE $KEY") }, context = Canary("BOOM"))
        val strategy = ScriptedStrategy(
            StrategyId("throwing"),
            { _, session ->
                see(session.submit(ToolStep.Mutation(boom)))
                error("$CANARY-EXCEPTION-MESSAGE $KEY")
            },
        )
        val outcome = commandPipeline {
            tier(strategy)
            gate = ScriptedGate.admitAll()
            commitSink = sink
            this.listener = listener
        }.execute(CommandInput("$CANARY-TRANSCRIPT", "es", Canary("CONTEXT")))

        sweepOutcome(outcome)
        sink.actions.forEach { see(it) }
        sink.closes.forEach { see(it) }
        listener.events.forEach { see(it) }
        checkEventFields(listener.events)
        return outcome
    }

    /** Codes and tool names in events match a plain identifier shape: no free text can hide in them. */
    private fun checkEventFields(events: List<PipelineEvent>) {
        val identifier = Regex("[a-z0-9_.-]+")
        events.forEach { event ->
            assertTrue(event.runId, event.runId.isNotEmpty())
            when (event) {
                is PipelineEvent.ActionRecorded -> assertTrue(identifier.matches(event.toolName))
                is PipelineEvent.EngineCode -> assertTrue(identifier.matches(event.code.value))
                is PipelineEvent.TierSkipped -> assertTrue(identifier.matches(event.code.value))
                is PipelineEvent.RunClosed -> assertTrue(identifier.matches(event.terminationCode))
                else -> Unit
            }
        }
    }

    @Test
    fun noCanaryAppearsInAnythingTheEngineReturnsDeliversOrPrints() = runTest {
        NoNetworkGuard.during {
            val full = fullScriptedRun()
            val thrown = throwingRun()

            val leaks = printed.filter { CANARY in it || KEY in it }
            assertTrue("leaked: $leaks", leaks.isEmpty())
            assertTrue("swept only ${printed.toSet().size} distinct values", printed.toSet().size >= MIN_DISTINCT)
            assertTrue(full is CommandOutcome.Completed)
            assertTrue(thrown is CommandOutcome.Failed)
            val reason = (thrown as CommandOutcome.Failed).reason
            assertEquals("IllegalStateException", (reason as FailureReason.Unexpected).errorClass)
            thrown.trace.codes.forEach { assertTrue(it.value, Regex("[a-z_]+").matches(it.value)) }
            full.trace.codes.forEach { assertTrue(it.value, Regex("[a-z_]+").matches(it.value)) }
        }
    }

    @Test
    fun theCredentialKeyIsNotInItsOwnText() {
        val credential = Credential(ProviderId.ANTHROPIC, KEY)

        assertTrue(KEY.startsWith("sk-"))
        assertTrue(KEY !in credential.toString())
        assertEquals("Credential(provider=anthropic)", credential.toString())
    }


    // ---- the routed path: selection, credential, frozen handle, provider, replay, fallback and refusals ----

    private class RoutedRun(
        val outcome: CommandOutcome,
        val fake: FakeAiProvider,
        val events: List<PipelineEvent>,
    )

    private fun canaryTool() = ToolSpec(
        "lookup",
        "$CANARY-TOOL-DESCRIPTION",
        buildJsonObject { put("note", "$CANARY-SCHEMA") },
    )

    private fun canaryRequest(input: CommandInput, extra: List<Message>): ModelRequest {
        val messages = listOf<Message>(UserMessage(input.transcript)) + extra
        return ModelRequest("$CANARY-SYSTEM", messages, listOf(canaryTool()), ROUTED_MAX_TOKENS)
    }

    /** Turn one answers with canary text, a tool call carrying a canary argument, and canary replay thinking text. */
    private fun toolTurn(): ProviderStep = { _ ->
        val raw = buildJsonObject {
            put("thinking", "$CANARY-THINKING")
            put("signature", "$CANARY-SIGNATURE")
        }
        val arguments = buildJsonObject { put("query", "$CANARY-TOOL-ARGUMENT") }
        val parts = listOf(
            AssistantPart.Text("$CANARY-ASSISTANT-TEXT"),
            AssistantPart.ToolCall(CALL_ID, "lookup", arguments),
        )
        val message = AssistantMessage(parts, NativeReplay(ProviderId.ANTHROPIC, ROUTED_MODEL, raw))
        ModelResult.Success(ModelResponse(message, StopReason.TOOL_USE, Usage(1, 2, 3, 4)))
    }

    private fun finalTurn(): ProviderStep = { _ -> FakeAiProvider.reply("$CANARY-FINAL", Usage(5, 6, 7, 8)) }

    private fun failedWith(result: ModelResult): StrategyOutcome =
        StrategyOutcome.Failed((result as ModelResult.Failure).reason)

    /** Two routed turns: the second extends the conversation with the assistant message and a tool result. */
    private suspend fun routedTurns(input: CommandInput, session: CommandSession): StrategyOutcome {
        val model = session.model()
        see(model)
        see(model.capabilities)
        val request = canaryRequest(input, emptyList())
        see(request)
        val first = model.complete(request)
        see(first)
        return if (first is ModelResult.Success) secondTurn(input, model, first) else failedWith(first)
    }

    private suspend fun secondTurn(
        input: CommandInput,
        model: BoundModel,
        first: ModelResult.Success,
    ): StrategyOutcome {
        val assistant = first.response.message
        sweepAssistant(assistant)
        val results = ToolResultsMessage(listOf(ToolResult(CALL_ID, "$CANARY-TOOL-RESULT")))
        see(results)
        results.results.forEach { see(it) }
        val second = model.complete(canaryRequest(input, listOf(assistant, results)))
        see(second)
        if (second !is ModelResult.Success) return failedWith(second)
        see(second.response)
        val text = second.response.message.parts.filterIsInstance<AssistantPart.Text>().single().text
        return StrategyOutcome.Completed(text)
    }

    private fun sweepAssistant(assistant: AssistantMessage) {
        see(assistant)
        assistant.parts.forEach { see(it) }
        see(assistant.nativeReplay)
    }

    private fun sweepCall(call: ProviderRequest) {
        see(call)
        see(call.request)
        see(call.request.cache)
        see(call.request.toolChoice)
        call.request.tools.forEach { see(it) }
        call.request.messages.forEach { see(it) }
        see(call.credential)
        see(call.capabilities)
    }

    private suspend fun routedRun(
        credentials: CredentialSource,
        fake: FakeAiProvider,
        capabilities: StrategyCapabilities,
        selection: ProviderSelection,
    ): RoutedRun {
        val listener = RecordingEventListener()
        val source = ScriptedSelectionSource.fixed(selection)
        val step: StrategyStep = { input, session -> routedTurns(input, session) }
        val strategy = ScriptedStrategy(StrategyId("routed"), capabilities, step)
        val pipeline = commandPipeline {
            tier(strategy)
            provider(fake)
            providerSelection = source
            this.credentials = credentials
            this.listener = listener
            gate = ScriptedGate.admitAll()
            commitSink = RecordingCommitSink()
        }
        see(pipeline)
        val outcome = pipeline.execute(CommandInput("$CANARY-TRANSCRIPT", "en", Canary("CONTEXT")))
        sweepOutcome(outcome)
        listener.events.forEach { see(it) }
        checkEventFields(listener.events)
        fake.calls.forEach { sweepCall(it) }
        source.requests.forEach { see(it) }
        see(selection)
        return RoutedRun(outcome, fake, listener.events)
    }

    /** Instances the engine does not hand back in these runs but an app builds and may print. */
    private fun sweepBuiltTypes() {
        see(CredentialLookup.Present(Credential(ProviderId.ANTHROPIC, KEY)))
        see(CredentialLookup.Missing())
        see(CredentialLookup.Unreadable("key_invalidated"))
        see(FailureReason.CredentialUnreadable(ProviderId.ANTHROPIC, "key_invalidated"))
        see(OnDeviceAvailability.Available())
        see(OnDeviceAvailability.Downloadable())
        see(OnDeviceAvailability.Downloading())
        see(OnDeviceAvailability.Unavailable("not_implemented"))
        see(ModelCapabilities.UNKNOWN)
        see(StopReason.TOOL_USE)
        see(ToolChoice.Auto())
        val required = ModelRequest(
            "$CANARY-SYSTEM",
            listOf(UserMessage("$CANARY-TRANSCRIPT")),
            listOf(canaryTool()),
            ToolChoice.Required("lookup"),
            ROUTED_MAX_TOKENS,
            CacheDirective(true, true),
        )
        see(required)
        see(required.toolChoice)
        sweepSingleShotSeams()
    }

    /** The app seams a single-shot tier is built on, built from canary-carrying values. */
    private fun sweepSingleShotSeams() {
        see(ToolingSnapshot("$CANARY-SYSTEM", listOf(canaryTool()), "lookup"))
        see(Extraction("lookup", buildJsonObject { put("value", "$CANARY-ARGUMENT") }))
        see(Resolution.Steps(listOf(ToolStep.Mutation(mutation("write"))), "$CANARY-REPLY"))
        see(Resolution.NoMatch())
        see(Resolution.Escalate(EscalationReason.NoToolCall(), Canary("CARRY")))
        see(Resolution.Failed(FailureReason.Refusal(), null))
        val input = CommandInput("$CANARY-TRANSCRIPT", "en", Canary("CONTEXT"))
        see(UserTurnContext(input, ZonedDateTime.now(ZoneId.of("UTC")), Canary("CARRY")))
    }

    @Test
    fun noCanaryOrKeyAppearsAnywhereOnTheRoutedPathIncludingFallbackAndRefusals() = runTest {
        NoNetworkGuard.during {
            val both = StrategyCapabilities(setOf(ProviderId.ON_DEVICE, ProviderId.ANTHROPIC))
            val cloud = StrategyCapabilities(setOf(ProviderId.ANTHROPIC))
            val fallback = ProviderSelection(
                ProviderId.ON_DEVICE,
                "local-model",
                ProviderSelection(ProviderId.ANTHROPIC, ROUTED_MODEL),
            )
            val direct = ProviderSelection(ProviderId.ANTHROPIC, ROUTED_MODEL)
            val keys = ScriptedCredentialSource.keys(ProviderId.ANTHROPIC to KEY)
            val unreadable = ScriptedCredentialSource(
                mapOf(ProviderId.ANTHROPIC to CredentialLookup.Unreadable("key_invalidated")),
            )
            val foreign = CredentialSource { CredentialLookup.Present(Credential(ProviderId.OPENAI, OTHER_KEY)) }
            val scripted = FakeAiProvider(ProviderId.ANTHROPIC, ModelCapabilities.UNKNOWN, toolTurn(), finalTurn())

            val routed = routedRun(keys, scripted, both, fallback)
            val refused = routedRun(unreadable, FakeAiProvider(ProviderId.ANTHROPIC, emptyList()), cloud, direct)
            val mismatch = routedRun(foreign, FakeAiProvider(ProviderId.ANTHROPIC, emptyList()), cloud, direct)
            sweepBuiltTypes()

            val leaks = printed.filter { CANARY in it || KEY in it || OTHER_KEY in it }
            assertTrue("leaked: $leaks", leaks.isEmpty())
            val distinct = printed.toSet().size
            assertTrue("swept only $distinct distinct values", distinct >= MIN_ROUTED_DISTINCT)
            assertRoutedShape(routed)
            assertRefusals(refused, mismatch)
        }
    }

    private fun assertRoutedShape(routed: RoutedRun) {
        val outcome = routed.outcome as CommandOutcome.Completed
        assertEquals("$CANARY-FINAL", outcome.reply)
        assertEquals(2, routed.fake.callCount)
        val turns = outcome.trace.attempts.single().turns
        assertEquals(listOf(ProviderId.ON_DEVICE, ProviderId.ON_DEVICE), turns.map { it.fallbackFrom })
        val second = routed.fake.calls.last()
        assertEquals(3, second.request.messages.size)
        val replayed = (second.request.messages[1] as AssistantMessage).nativeFor(ProviderId.ANTHROPIC, ROUTED_MODEL)
        assertNotNull(replayed)
        assertEquals(KEY, second.credential?.apiKey)
        assertTrue(routed.events.any { it is PipelineEvent.ProviderCall })
        assertTrue(routed.events.last() is PipelineEvent.RunClosed)
    }

    private fun assertRefusals(refused: RoutedRun, mismatch: RoutedRun) {
        val unreadable = (refused.outcome as CommandOutcome.Failed).reason
        assertEquals(FailureReason.CredentialUnreadable(ProviderId.ANTHROPIC, "key_invalidated"), unreadable)
        assertEquals(0, refused.fake.callCount)
        assertTrue(mismatch.outcome is CommandOutcome.Failed)
        assertEquals(0, mismatch.fake.callCount)
        assertTrue(mismatch.outcome.trace.codes.any { it.value == "credential_mismatch" })
    }

    private companion object {
        const val MIN_DISTINCT = 25
        const val MIN_ROUTED_DISTINCT = 30
        const val ROUTED_MAX_TOKENS = 100
    }
}
