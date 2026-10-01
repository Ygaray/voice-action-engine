package io.github.ygaray.voiceactionengine.providers.anthropic

import io.github.ygaray.voiceactionengine.core.CommandInput
import io.github.ygaray.voiceactionengine.core.ProviderId
import io.github.ygaray.voiceactionengine.core.StrategyId
import io.github.ygaray.voiceactionengine.core.pipeline.CommandOutcome
import io.github.ygaray.voiceactionengine.core.pipeline.commandPipeline
import io.github.ygaray.voiceactionengine.core.provider.AiProvider
import io.github.ygaray.voiceactionengine.core.provider.BoundModel
import io.github.ygaray.voiceactionengine.core.provider.ModelResult
import io.github.ygaray.voiceactionengine.core.provider.ProviderRequest
import io.github.ygaray.voiceactionengine.core.provider.ProviderSelection
import io.github.ygaray.voiceactionengine.core.strategy.CommandSession
import io.github.ygaray.voiceactionengine.core.strategy.StrategyOutcome
import io.github.ygaray.voiceactionengine.core.strategy.ToolSpec
import io.github.ygaray.voiceactionengine.core.telemetry.PipelineEvent
import io.github.ygaray.voiceactionengine.core.testing.RecordingCommitSink
import io.github.ygaray.voiceactionengine.core.testing.RecordingEventListener
import io.github.ygaray.voiceactionengine.core.testing.ScriptedCredentialSource
import io.github.ygaray.voiceactionengine.core.testing.ScriptedGate
import io.github.ygaray.voiceactionengine.core.testing.ScriptedSelectionSource
import io.github.ygaray.voiceactionengine.core.testing.ScriptedStrategy
import io.github.ygaray.voiceactionengine.core.testing.StrategyStep
import io.github.ygaray.voiceactionengine.core.transcript.AssistantMessage
import io.github.ygaray.voiceactionengine.core.transcript.AssistantPart
import io.github.ygaray.voiceactionengine.core.transcript.Message
import io.github.ygaray.voiceactionengine.core.transcript.ModelRequest
import io.github.ygaray.voiceactionengine.core.transcript.ModelResponse
import io.github.ygaray.voiceactionengine.core.transcript.ToolResult
import io.github.ygaray.voiceactionengine.core.transcript.ToolResultsMessage
import io.github.ygaray.voiceactionengine.core.transcript.UserMessage
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

private const val CANARY = "CANARY"
private const val KEY = "sk-CANARY-KEY"
private const val MODEL = "claude-haiku-4-5"
private const val CALL_ID = "toolu_canary_1"
private const val MAX_TOKENS = 256
private const val MIN_DISTINCT = 25
private const val TEST_TIMEOUT_MILLIS = 60_000L

/** Wraps the real provider and remembers every request it was given and every answer it returned. */
private class RecordingProvider(private val inner: AiProvider) : AiProvider by inner {
    val calls = CopyOnWriteArrayList<ProviderRequest>()
    val results = CopyOnWriteArrayList<ModelResult>()

    override suspend fun complete(call: ProviderRequest): ModelResult {
        calls.add(call)
        return inner.complete(call).also { results.add(it) }
    }
}

/** Everything one routed run produced, kept for the sweep. */
private class CanaryRun(
    val outcome: CommandOutcome,
    val recording: RecordingProvider,
    val events: List<PipelineEvent>,
    val sink: RecordingCommitSink,
    val attempts: List<AnthropicAttempt>,
    val provider: AnthropicProvider,
    val builder: AnthropicProvider.Builder,
)

/**
 * A command runs through the pipeline and the real Anthropic transport with a canary in every secret slot. The server
 * must see them (positive controls), and nothing the engine returns, delivers or prints may contain them.
 */
class AnthropicCanaryTest {

    private val printed = CopyOnWriteArrayList<String>()

    private fun see(value: Any?) {
        printed.add(value.toString())
    }

    private fun lookupTool() = ToolSpec("lookup", "Looks one thing up.", buildJsonObject { put("type", "object") })

    private fun canaryRequest(input: CommandInput, extra: List<Message>): ModelRequest = ModelRequest(
        "$CANARY-SYSTEM",
        listOf<Message>(UserMessage(input.transcript)) + extra,
        listOf(lookupTool()),
        MAX_TOKENS,
    )

    private fun failedWith(result: ModelResult): StrategyOutcome {
        val failure = result as ModelResult.Failure
        return StrategyOutcome.Failed(failure.reason, failure.details)
    }

    /** Two routed turns: the second extends the conversation with the assistant message and a canary tool result. */
    private fun twoTurnStep(): StrategyStep = { input, session ->
        val model = session.model()
        see(model)
        see(model.capabilities)
        val first = model.complete(canaryRequest(input, emptyList()))
        see(first)
        if (first is ModelResult.Success) secondTurn(input, model, first, session) else failedWith(first)
    }

    private suspend fun secondTurn(
        input: CommandInput,
        model: BoundModel,
        first: ModelResult.Success,
        session: CommandSession,
    ): StrategyOutcome {
        see(session)
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

    private fun sweepResponse(response: ModelResponse) {
        see(response)
        sweepAssistant(response.message)
        see(response.usage)
        see(response.stopReason)
    }

    private fun sweepResult(result: ModelResult) {
        see(result)
        when (result) {
            is ModelResult.Success -> sweepResponse(result.response)
            is ModelResult.Failure -> {
                see(result.reason)
                see(result.details)
            }
            else -> Unit
        }
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

    private fun sweepRun(run: CanaryRun) {
        sweepOutcome(run.outcome)
        run.events.forEach { see(it) }
        run.sink.actions.forEach { see(it) }
        run.sink.closes.forEach { see(it) }
        run.recording.calls.forEach { sweepCall(it) }
        run.recording.results.forEach { sweepResult(it) }
        run.attempts.forEach {
            see(it)
            see(it.kind)
        }
        see(run.provider)
        see(run.builder)
    }

    /** Runs [step] as the only tier over the real provider pointed at [server], then sweeps everything it produced. */
    private fun routedRun(server: MockWebServer, model: String, step: StrategyStep): CanaryRun = runBlocking {
        val attempts = CopyOnWriteArrayList<AnthropicAttempt>()
        val builder = AnthropicProvider.Builder().apply {
            baseUrl = server.url("/")
            // No real waiting: a transient leg must not slow the suite.
            sleep = { }
            attemptObserver = AnthropicAttemptObserver { attempts.add(it) }
        }
        val provider = builder.build()
        val recording = RecordingProvider(provider)
        val listener = RecordingEventListener()
        val sink = RecordingCommitSink()
        val pipeline = commandPipeline {
            tier(ScriptedStrategy(StrategyId("canary"), step))
            provider(recording)
            providerSelection = ScriptedSelectionSource.fixed(ProviderSelection(ProviderId.ANTHROPIC, model))
            credentials = ScriptedCredentialSource.keys(ProviderId.ANTHROPIC to KEY)
            this.listener = listener
            gate = ScriptedGate.admitAll()
            commitSink = sink
        }
        see(pipeline)
        val input = CommandInput("$CANARY-TRANSCRIPT", "en", null)
        see(input)
        val outcome = pipeline.execute(input)
        CanaryRun(outcome, recording, listener.events, sink, attempts.toList(), provider, builder).also { sweepRun(it) }
    }

    private fun assertNothingLeaked() {
        val leaks = printed.filter { CANARY in it || KEY in it }
        assertTrue("leaked: $leaks", leaks.isEmpty())
        val distinct = printed.toSet().size
        println("CANARY_SWEPT distinct=$distinct")
        assertTrue("swept only $distinct distinct values", distinct >= MIN_DISTINCT)
    }

    private fun toolUseAnswer(): MockResponse = MockResponse().setResponseCode(HTTP_OK).setBody(
        successBody(
            listOf(
                textBlock("$CANARY-ASSISTANT-TEXT"),
                toolUseBlock(CALL_ID, "lookup", buildJsonObject { put("query", "$CANARY-TOOL-ARGUMENT") }),
            ),
            "tool_use",
        ),
    )

    private fun finalAnswer(): MockResponse = MockResponse().setResponseCode(HTTP_OK).setBody(
        successBody(listOf(textBlock("$CANARY-FINAL")), "end_turn"),
    )

    @Test(timeout = TEST_TIMEOUT_MILLIS)
    fun noSecretReachesAnythingTheEngineReturnsDeliversOrPrintsOnARoutedTwoTurnCommand() {
        MockWebServer().use { server ->
            server.enqueue(toolUseAnswer())
            server.enqueue(finalAnswer())
            server.start()

            val run = routedRun(server, MODEL, twoTurnStep())

            // Positive controls: the server really received every secret, so an empty sweep cannot pass by accident.
            val first = server.takeRequest()
            assertEquals(KEY, first.getHeader("x-api-key"))
            val firstBody = first.body.readUtf8()
            assertTrue("transcript canary missing from the request", "$CANARY-TRANSCRIPT" in firstBody)
            assertTrue("system canary missing from the request", "$CANARY-SYSTEM" in firstBody)
            val secondBody = server.takeRequest().body.readUtf8()
            assertTrue("tool argument canary missing from the replay", "$CANARY-TOOL-ARGUMENT" in secondBody)
            assertTrue("tool result canary missing from the request", "$CANARY-TOOL-RESULT" in secondBody)

            val outcome = run.outcome
            assertTrue(outcome.toString(), outcome is CommandOutcome.Completed)
            assertEquals("$CANARY-FINAL", (outcome as CommandOutcome.Completed).reply)
            assertEquals(2, run.recording.calls.size)
            assertEquals(2, run.attempts.size)
            assertTrue(run.events.last() is PipelineEvent.RunClosed)
            assertNothingLeaked()
        }
    }

    private companion object {
        const val HTTP_OK = 200
    }
}
