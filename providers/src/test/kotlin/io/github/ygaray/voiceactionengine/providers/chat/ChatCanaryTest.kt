package io.github.ygaray.voiceactionengine.providers.chat

import io.github.ygaray.voiceactionengine.core.CommandInput
import io.github.ygaray.voiceactionengine.core.StrategyId
import io.github.ygaray.voiceactionengine.core.failure.FailureDetails
import io.github.ygaray.voiceactionengine.core.failure.FailureReason
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
import io.github.ygaray.voiceactionengine.core.transcript.StopReason
import io.github.ygaray.voiceactionengine.core.transcript.ToolResult
import io.github.ygaray.voiceactionengine.core.transcript.ToolResultsMessage
import io.github.ygaray.voiceactionengine.core.transcript.UserMessage
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.reflect.KClass

private const val CANARY = "CANARY"
private const val KEY = "sk-CANARY-KEY-BODY"
private const val CALL_ID = "call_canary_1"
private const val MAX_TOKENS = 256
private const val MIN_DISTINCT = 25
private const val TEST_TIMEOUT_MILLIS = 60_000L
private const val HTTP_OK = 200
private const val HTTP_BAD_REQUEST = 400
private const val HTTP_UNAUTHORIZED = 401
private const val HTTP_PAYMENT_REQUIRED = 402
private const val HTTP_RATE_LIMITED = 429
private const val HTTP_SERVER_ERROR = 500
private const val HTTP_BAD_GATEWAY = 502
private const val OPENAI_LABEL = "openai"
private const val REQUEST_ID_HEADER_VALUE = "req_canary_h"
private const val ROUTER_REQUEST_ID = "gen-canary-req"
private const val ROUTER_ERROR_TYPE = "upstream_error"
private const val OPENAI_ERROR_TYPE = "invalid_request_error"

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
    val attempts: List<ChatCompletionsAttempt>,
    val provider: ChatCompletionsProvider,
    val builder: ChatCompletionsProvider.Builder,
)

/** Answers every request the same way, so a transient retry meets the same failure as the first attempt. */
private class AlwaysDispatcher(private val respond: () -> MockResponse) : Dispatcher() {
    override fun dispatch(request: RecordedRequest): MockResponse = respond()
}

/**
 * A command runs through the pipeline and the real Chat Completions transport, on both vendors, with a canary in every
 * secret slot. The server must see them (positive controls), and nothing the engine returns, delivers or prints may
 * contain them.
 */
@RunWith(Parameterized::class)
class ChatCanaryTest(private val label: String, private val model: String) {

    private val vendor: ChatVendor = if (label == OPENAI_LABEL) ChatVendor.OPENAI else ChatVendor.OPENROUTER

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
        val bound = session.model()
        see(bound)
        see(bound.capabilities)
        val first = bound.complete(canaryRequest(input, emptyList()))
        see(first)
        if (first is ModelResult.Success) secondTurn(input, bound, first, session) else failedWith(first)
    }

    private suspend fun secondTurn(
        input: CommandInput,
        bound: BoundModel,
        first: ModelResult.Success,
        session: CommandSession,
    ): StrategyOutcome {
        see(session)
        val assistant = first.response.message
        sweepAssistant(assistant)
        val results = ToolResultsMessage(listOf(ToolResult(CALL_ID, "$CANARY-TOOL-RESULT")))
        see(results)
        results.results.forEach { see(it) }
        val second = bound.complete(canaryRequest(input, listOf(assistant, results)))
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
    private fun routedRun(server: MockWebServer, step: StrategyStep): CanaryRun = runBlocking {
        val attempts = CopyOnWriteArrayList<ChatCompletionsAttempt>()
        val builder = ChatCompletionsProvider.Builder(vendor).apply {
            baseUrl = server.url("/")
            // No real waiting: a transient leg must not slow the suite.
            sleep = { }
            attemptObserver = ChatCompletionsAttemptObserver { attempts.add(it) }
        }
        val provider = builder.build()
        val recording = RecordingProvider(provider)
        val listener = RecordingEventListener()
        val sink = RecordingCommitSink()
        val pipeline = commandPipeline {
            tier(ScriptedStrategy(StrategyId("canary"), step))
            provider(recording)
            providerSelection = ScriptedSelectionSource.fixed(ProviderSelection(vendor.providerId, model))
            credentials = ScriptedCredentialSource.keys(vendor.providerId to KEY)
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
        println("CANARY_SWEPT vendor=$label distinct=$distinct")
        assertTrue("swept only $distinct distinct values", distinct >= MIN_DISTINCT)
    }

    // ---- answers the fake server gives ------------------------------------------------------------------------

    private fun answerId(): String = if (vendor == ChatVendor.OPENAI) "chatcmpl-GOLDEN" else "gen-GOLDEN"

    /** Adds the reasoning carriers each vendor can send next to the message, all holding the canary. */
    private fun withReasoning(message: JsonObject): JsonObject = JsonObject(
        message + mapOf(
            "reasoning" to JsonPrimitive("$CANARY-REASONING $KEY"),
            "reasoning_content" to JsonPrimitive("$CANARY-REASONING-CONTENT"),
            "reasoning_details" to buildJsonArray {
                add(
                    buildJsonObject {
                        put("type", "reasoning.text")
                        put("text", "$CANARY-REASONING-DETAIL")
                    },
                )
            },
        ),
    )

    private fun toolUseAnswer(): MockResponse = MockResponse().setResponseCode(HTTP_OK).setBody(
        chatBody(
            withReasoning(
                chatMessage(
                    "$CANARY-ASSISTANT-TEXT",
                    listOf(
                        chatToolCall(CALL_ID, "lookup", """{"query":"$CANARY-TOOL-ARGUMENT"}"""),
                    ),
                ),
            ),
            "tool_calls",
            chatUsage(120, 40),
            id = answerId(),
        ),
    )

    private fun finalAnswer(): MockResponse = MockResponse().setResponseCode(HTTP_OK).setBody(
        chatBody(chatMessage("$CANARY-FINAL"), "stop", chatUsage(150, 20), id = answerId()),
    )

    // OpenRouter's error object, with the router's request id in the body and the upstream's raw text in metadata.
    private fun routerError(code: Int, id: String, type: String): String = buildJsonObject {
        put("id", id)
        putJsonObject("error") {
            put("code", code)
            put("message", "$CANARY-ECHO $KEY")
            putJsonObject("metadata") {
                put("raw", "$CANARY-RAW $KEY")
                put("error_type", type)
                put("provider_name", "Example")
            }
        }
    }.toString()

    /** An error answer for [status] that echoes the canary and the key in every text slot the vendor has. */
    private fun echoingError(status: Int): MockResponse = if (vendor == ChatVendor.OPENAI) {
        MockResponse().setResponseCode(status).setHeader("x-request-id", REQUEST_ID_HEADER_VALUE)
            .setBody(openAiErrorBody(OPENAI_ERROR_TYPE, null, "$CANARY-ECHO $KEY"))
    } else {
        MockResponse().setResponseCode(status).setBody(routerError(status, ROUTER_REQUEST_ID, ROUTER_ERROR_TYPE))
    }

    /** What an echoing answer for [status] may leave in the failure: the status, a safe type and a safe request id. */
    private fun echoDetails(status: Int): FailureDetails = if (vendor == ChatVendor.OPENAI) {
        FailureDetails(status, OPENAI_ERROR_TYPE, REQUEST_ID_HEADER_VALUE)
    } else {
        FailureDetails(status, ROUTER_ERROR_TYPE, ROUTER_REQUEST_ID)
    }

    // ---- running and asserting --------------------------------------------------------------------------------

    /** One routed model call; a failure becomes the tier's failure with its details, a success completes the run. */
    private fun singleCallStep(): StrategyStep = { input, session ->
        val result = session.model().complete(canaryRequest(input, emptyList()))
        see(result)
        if (result is ModelResult.Success) StrategyOutcome.Completed(null) else failedWith(result)
    }

    private fun serving(respond: () -> MockResponse): CanaryRun = MockWebServer().use { server ->
        server.dispatcher = AlwaysDispatcher(respond)
        server.start()
        routedRun(server, singleCallStep())
    }

    private fun assertFailed(run: CanaryRun, reason: KClass<out FailureReason>, details: FailureDetails?) {
        val outcome = run.outcome
        assertTrue(outcome.toString(), outcome is CommandOutcome.Failed)
        outcome as CommandOutcome.Failed
        assertEquals(reason, outcome.reason::class)
        assertEquals(details, outcome.details)
        assertTrue(run.attempts.isNotEmpty())
    }

    // ---- the happy path ---------------------------------------------------------------------------------------

    @Test(timeout = TEST_TIMEOUT_MILLIS)
    fun noSecretReachesAnythingTheEngineReturnsDeliversOrPrintsOnARoutedTwoTurnCommand() {
        MockWebServer().use { server ->
            server.enqueue(toolUseAnswer())
            server.enqueue(finalAnswer())
            server.start()

            val run = routedRun(server, twoTurnStep())

            // Positive controls: the server really received every secret, so an empty sweep cannot pass by accident.
            val first = server.takeRequest()
            assertEquals("Bearer $KEY", first.getHeader("Authorization"))
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

    // ---- failure legs: what the server echoes, hostile fields, malformed answers ------------------------------

    @Test(timeout = TEST_TIMEOUT_MILLIS)
    fun anErrorBodyThatEchoesTheCanaryAndTheKeyLeavesOnlyTheStatusTheTypeAndTheRequestId() {
        val cases = listOf(
            HTTP_BAD_REQUEST to FailureReason.HttpError::class,
            HTTP_UNAUTHORIZED to FailureReason.Auth::class,
            HTTP_RATE_LIMITED to FailureReason.RateLimited::class,
            HTTP_SERVER_ERROR to FailureReason.HttpError::class,
        )
        cases.forEach { (status, reason) ->
            val run = serving { echoingError(status) }

            assertFailed(run, reason, echoDetails(status))
        }
        assertNothingLeaked()
    }

    @Test(timeout = TEST_TIMEOUT_MILLIS)
    fun aTwoHundredEnvelopeThatEchoesTheCanaryAndTheKeyLeavesOnlyTheStatusTheTypeAndTheRequestId() {
        if (vendor == ChatVendor.OPENAI) {
            // OpenAI-shaped error object inside a 200 answer.
            val body = openAiErrorBody(OPENAI_ERROR_TYPE, null, "$CANARY-ECHO $KEY")
            val run = serving {
                MockResponse().setResponseCode(HTTP_OK).setHeader("x-request-id", REQUEST_ID_HEADER_VALUE)
                    .setBody(body)
            }

            assertFailed(run, FailureReason.HttpError::class, echoDetails(HTTP_OK))
        } else {
            val body = routerError(HTTP_BAD_GATEWAY, ROUTER_REQUEST_ID, ROUTER_ERROR_TYPE)
            val run = serving { MockResponse().setResponseCode(HTTP_OK).setBody(body) }

            assertFailed(
                run,
                FailureReason.HttpError::class,
                FailureDetails(HTTP_BAD_GATEWAY, ROUTER_ERROR_TYPE, ROUTER_REQUEST_ID),
            )
        }
        assertNothingLeaked()
    }

    @Test(timeout = TEST_TIMEOUT_MILLIS)
    fun anOutOfCreditsAnswerThatEchoesTheCanaryAndTheKeyLeavesOnlyTheStatusTheTypeAndTheRequestId() {
        val run = serving { echoingError(HTTP_PAYMENT_REQUIRED) }

        assertFailed(run, FailureReason.Billing::class, echoDetails(HTTP_PAYMENT_REQUIRED))
        assertNothingLeaked()
    }

    @Test(timeout = TEST_TIMEOUT_MILLIS)
    fun aRefusalTextHoldingTheCanaryYieldsARefusalAndIsNeverPrinted() {
        val body = chatBody(
            chatMessage(null, refusal = "$CANARY-REFUSAL $KEY"),
            "stop",
            chatUsage(120, 40),
            id = answerId(),
        )
        val run = serving { MockResponse().setResponseCode(HTTP_OK).setBody(body) }

        val result = run.recording.results.single() as ModelResult.Success
        assertEquals(StopReason.REFUSAL, result.response.stopReason)
        assertTrue(run.outcome.toString(), run.outcome is CommandOutcome.Completed)
        assertNothingLeaked()
    }

    @Test(timeout = TEST_TIMEOUT_MILLIS)
    fun aHostileErrorTypeAndRequestIdAreDroppedAndNothingLeaks() {
        val hostile = "$CANARY TYPE"
        val run = if (vendor == ChatVendor.OPENAI) {
            val body = openAiErrorBody(hostile, null, "$CANARY-ECHO $KEY")
            serving {
                MockResponse().setResponseCode(HTTP_BAD_REQUEST).setHeader("x-request-id", "$CANARY id").setBody(body)
            }
        } else {
            val body = routerError(HTTP_BAD_REQUEST, "$CANARY id", hostile)
            serving { MockResponse().setResponseCode(HTTP_BAD_REQUEST).setBody(body) }
        }

        assertFailed(run, FailureReason.HttpError::class, FailureDetails(HTTP_BAD_REQUEST, null, null))
        assertNothingLeaked()
    }

    @Test(timeout = TEST_TIMEOUT_MILLIS)
    fun aSuccessBodyThatIsNotJsonIsMalformedAndNothingLeaks() {
        val run = serving { MockResponse().setResponseCode(HTTP_OK).setBody("not json $CANARY-BODY $KEY") }

        assertFailed(run, FailureReason.MalformedResponse::class, null)
        assertNothingLeaked()
    }

    @Test(timeout = TEST_TIMEOUT_MILLIS)
    fun aNonPositiveTimeoutThrowsAMessageWithoutTheCanaryOrTheKeyEvenWithAClientOnTheBuilder() {
        val configure: ChatCompletionsProvider.Builder.() -> Unit = {
            httpClient = OkHttpClient()
            callTimeoutMillis = -1
        }
        val thrown = runCatching {
            if (vendor == ChatVendor.OPENAI) {
                ChatCompletionsProvider.openAi(configure)
            } else {
                ChatCompletionsProvider.openRouter(configure)
            }
        }.exceptionOrNull()

        assertTrue(thrown.toString(), thrown is IllegalArgumentException)
        see(thrown!!.message)
        see(thrown)
        assertTrue(thrown.message!!.contains("callTimeoutMillis"))
        val leaks = printed.filter { CANARY in it || KEY in it }
        assertTrue("leaked: $leaks", leaks.isEmpty())
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun vendors(): List<Array<Any>> = listOf(
            arrayOf(OPENAI_LABEL, "gpt-5.4-mini"),
            arrayOf("openrouter", "openai/gpt-5.4-mini"),
        )
    }
}
