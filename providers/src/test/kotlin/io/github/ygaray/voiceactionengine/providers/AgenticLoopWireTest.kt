package io.github.ygaray.voiceactionengine.providers

import io.github.ygaray.voiceactionengine.core.CommandInput
import io.github.ygaray.voiceactionengine.core.ProviderId
import io.github.ygaray.voiceactionengine.core.StrategyId
import io.github.ygaray.voiceactionengine.core.commit.ActionKind
import io.github.ygaray.voiceactionengine.core.commit.StepResult
import io.github.ygaray.voiceactionengine.core.commit.ToolStep
import io.github.ygaray.voiceactionengine.core.pipeline.CommandOutcome
import io.github.ygaray.voiceactionengine.core.pipeline.commandPipeline
import io.github.ygaray.voiceactionengine.core.provider.AiProvider
import io.github.ygaray.voiceactionengine.core.provider.ProviderSelection
import io.github.ygaray.voiceactionengine.core.strategy.ToolSpec
import io.github.ygaray.voiceactionengine.core.strategy.ToolSpecProvider
import io.github.ygaray.voiceactionengine.core.strategy.ToolingSnapshot
import io.github.ygaray.voiceactionengine.core.strategy.UserTurnRenderer
import io.github.ygaray.voiceactionengine.core.strategy.agentic.AgenticLoopStrategy
import io.github.ygaray.voiceactionengine.core.testing.FakeMutation
import io.github.ygaray.voiceactionengine.core.testing.RecordingCommitSink
import io.github.ygaray.voiceactionengine.core.testing.ScriptedCredentialSource
import io.github.ygaray.voiceactionengine.core.testing.ScriptedGate
import io.github.ygaray.voiceactionengine.core.testing.ScriptedSelectionSource
import io.github.ygaray.voiceactionengine.core.testing.ScriptedToolExecutor
import io.github.ygaray.voiceactionengine.providers.anthropic.AnthropicProvider
import io.github.ygaray.voiceactionengine.providers.anthropic.successBody
import io.github.ygaray.voiceactionengine.providers.anthropic.textBlock
import io.github.ygaray.voiceactionengine.providers.anthropic.toolUseBlock
import io.github.ygaray.voiceactionengine.providers.chat.ChatCompletionsProvider
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.HttpUrl
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

private const val SAVE_TOOL = "save_entry"
private const val FAKE_KEY = "sk-test-key"
private const val REQUEST_WAIT_SECONDS = 5L
private const val TRANSCRIPT = "add two things"
private const val RESULT_CONTENT = "saved entry"
private const val FINAL_TEXT = "done"
private const val HELD_BYTES = """{"applied":false,"status":"held_for_confirmation"}"""
private const val SB_FIRST_TURN = "Current local date-time: 2026-10-01T16:30:12 (UTC)\n\nVoice command: add two things"
private const val ROLE = "role"
private const val CONTENT = "content"
private const val TYPE = "type"
private const val TOOLS = "tools"
private const val MESSAGES = "messages"
private const val TOOL_CHOICE = "tool_choice"

/** The three wire dialects the loop is proven over, with the model each wire test already uses. */
private enum class LoopDialect(val providerId: ProviderId, val model: String) {
    ANTHROPIC(ProviderId.ANTHROPIC, "claude-opus-5-5"),
    OPENAI(ProviderId.OPENAI, "gpt-5.4-mini"),
    OPENROUTER(ProviderId.OPENROUTER, "openai/gpt-5.4-mini"),
    ;

    fun provider(url: HttpUrl): AiProvider = when (this) {
        ANTHROPIC -> AnthropicProvider { baseUrl = url }
        OPENAI -> ChatCompletionsProvider.openAi { baseUrl = url }
        OPENROUTER -> ChatCompletionsProvider.openRouter { baseUrl = url }
    }
}

/** One scenario: the dialect, the answers the server gives in order, the gate and the tools offered. */
private class LoopScenario(
    val dialect: LoopDialect,
    val answers: List<String>,
    val gate: ScriptedGate = ScriptedGate.admitAll(),
    val tools: List<ToolSpec> = listOf(saveTool()),
)

/** What one scenario did: the outcome, every request body the server saw and the doubles it touched. */
private class LoopExchange(
    val outcome: CommandOutcome,
    val bodies: List<JsonObject>,
    val bodyTexts: List<String>,
    val gate: ScriptedGate,
    val sink: RecordingCommitSink,
) {
    val committed: List<ActionKind> get() = sink.actions.map { it.action.kind }
}

private fun saveSchema(withOptional: Boolean): JsonObject = buildJsonObject {
    put("type", "object")
    putJsonObject("properties") {
        putJsonObject("entry") { put("type", "string") }
        if (withOptional) putJsonObject("note") { put("type", "string") }
    }
    putJsonArray("required") { add("entry") }
    put("additionalProperties", false)
}

/** A mutating tool whose schema fits strict mode, or, with [withOptional], does not. */
private fun saveTool(withOptional: Boolean = false): ToolSpec =
    ToolSpec(SAVE_TOOL, "Saves one entry.", saveSchema(withOptional), true)

private fun entry(value: String): JsonObject = buildJsonObject { put("entry", value) }

/** The SB-shaped user turn: local date-time to the second, the zone id, a blank line, then the transcript. */
private val sbRenderer = UserTurnRenderer { context ->
    "Current local date-time: " + context.dateTime.toLocalDateTime().truncatedTo(ChronoUnit.SECONDS) +
        " (" + context.dateTime.zone.id + ")\n\nVoice command: " + context.input.transcript
}

private fun agenticTier(tools: List<ToolSpec>): AgenticLoopStrategy =
    AgenticLoopStrategy(StrategyId("agentic")) {
        tooling = ToolSpecProvider.fixed(ToolingSnapshot("fixed system", tools, null))
        executor = ScriptedToolExecutor(null) { call, _ ->
            ToolStep.Mutation(FakeMutation(call.toolName, StepResult(RESULT_CONTENT, false, "ok", emptyMap())))
        }
        userTurn = sbRenderer
        clock = Clock.fixed(Instant.parse("2026-10-01T16:30:12Z"), ZoneId.of("UTC"))
    }

/** Runs "add two things" through the agentic tier and the real provider against the scripted server answers. */
private fun driveLoop(scenario: LoopScenario): LoopExchange = runBlocking {
    MockWebServer().use { server ->
        scenario.answers.forEach { server.enqueue(MockResponse().setResponseCode(200).setBody(it)) }
        server.start()
        val sink = RecordingCommitSink()
        val pipeline = commandPipeline {
            tier(agenticTier(scenario.tools))
            provider(scenario.dialect.provider(server.url("/")))
            providerSelection = ScriptedSelectionSource.fixed(
                ProviderSelection(scenario.dialect.providerId, scenario.dialect.model),
            )
            credentials = ScriptedCredentialSource.keys(scenario.dialect.providerId to FAKE_KEY)
            gate = scenario.gate
            commitSink = sink
        }

        val outcome = pipeline.execute(CommandInput(TRANSCRIPT, "en", null))

        val texts = (1..server.requestCount).map {
            server.takeRequest(REQUEST_WAIT_SECONDS, TimeUnit.SECONDS)?.body?.readUtf8().orEmpty()
        }
        LoopExchange(outcome, texts.map { Json.parseToJsonElement(it).jsonObject }, texts, scenario.gate, sink)
    }
}

private fun JsonObject.messages(): JsonArray = getValue(MESSAGES).jsonArray

private fun JsonObject.role(): String = getValue(ROLE).jsonPrimitive.content

/** The text of the first user message, read from whichever shape the dialect puts it in. */
private fun JsonObject.firstUserText(): String {
    val first = messages().map { it.jsonObject }.first { it.role() == "user" }
    val content: JsonElement = first.getValue(CONTENT)
    return if (content is JsonArray) {
        content.first().jsonObject.getValue("text").jsonPrimitive.content
    } else {
        content.jsonPrimitive.content
    }
}

/** What the provider can cache: the system text and the tools, as the dialect carries them. */
private fun JsonObject.cachedPrefix(dialect: LoopDialect): String =
    if (dialect == LoopDialect.ANTHROPIC) {
        getValue("system").toString() + getValue(TOOLS).toString()
    } else {
        messages().first().toString() + getValue(TOOLS).toString()
    }

private fun toolUseAnswer(vararg calls: Pair<String, String>): String = successBody(
    calls.map { (id, value) -> toolUseBlock(id, SAVE_TOOL, entry(value)) },
    "tool_use",
)

private fun finalAnswer(): String = successBody(listOf(textBlock(FINAL_TEXT)), "end_turn")

/** The agentic loop over the real Anthropic transport, exactly as a consumer app wires it. */
class AgenticLoopWireTest {

    private fun throughAnthropic(answers: List<String>, gate: ScriptedGate = ScriptedGate.admitAll()): LoopExchange =
        driveLoop(LoopScenario(LoopDialect.ANTHROPIC, answers, gate))

    private fun JsonObject.toolResults(): JsonArray = messages().last().jsonObject.getValue(CONTENT).jsonArray

    @Test(timeout = 30_000)
    fun anthropicTwoTurnRunEchoesTheTurnAndAnswersEveryCall() {
        val exchange = throughAnthropic(listOf(toolUseAnswer("toolu_1" to "milk"), finalAnswer()))

        assertEquals(2, exchange.bodies.size)
        val second = exchange.bodies[1].messages()
        assertEquals(listOf("user", "assistant", "user"), second.map { it.jsonObject.role() })
        val assistant = second[1].jsonObject.getValue(CONTENT).jsonArray.single().jsonObject
        assertEquals("tool_use", assistant.getValue(TYPE).jsonPrimitive.content)
        assertEquals("toolu_1", assistant.getValue("id").jsonPrimitive.content)
        val result = exchange.bodies[1].toolResults().single().jsonObject
        assertEquals("tool_result", result.getValue(TYPE).jsonPrimitive.content)
        assertEquals("toolu_1", result.getValue("tool_use_id").jsonPrimitive.content)
        assertEquals(RESULT_CONTENT, result.getValue(CONTENT).jsonPrimitive.content)
        exchange.bodies.forEach {
            assertEquals(buildJsonObject { put(TYPE, "auto") }, it[TOOL_CHOICE])
        }
        exchange.bodyTexts.forEach { assertFalse(it.contains("disable_parallel_tool_use")) }
        val outcome = exchange.outcome
        assertTrue(outcome.toString(), outcome is CommandOutcome.Completed)
        assertEquals(FINAL_TEXT, (outcome as CommandOutcome.Completed).reply)
        assertEquals(listOf(ActionKind.COMMITTED), exchange.committed)
    }

    @Test(timeout = 30_000)
    fun anthropicFirstUserMessageIsTheSbShapedTextExactly() {
        val exchange = throughAnthropic(listOf(toolUseAnswer("toolu_1" to "milk"), finalAnswer()))

        assertEquals(2, exchange.bodies.size)
        exchange.bodies.forEach { assertEquals(SB_FIRST_TURN, it.firstUserText()) }
    }

    @Test(timeout = 30_000)
    fun anthropicSystemAndToolsBytesAreIdenticalOnEveryTurn() {
        val exchange = throughAnthropic(listOf(toolUseAnswer("toolu_1" to "milk"), finalAnswer()))

        assertEquals(2, exchange.bodies.size)
        val (first, second) = exchange.bodies.map { it.cachedPrefix(LoopDialect.ANTHROPIC) }
        assertEquals(first, second)
    }

    @Test(timeout = 30_000)
    fun anthropicHeldCallSendsTheFixedNoticeBytes() {
        val exchange = throughAnthropic(
            listOf(toolUseAnswer("toolu_1" to "milk"), finalAnswer()),
            ScriptedGate.holdAll(),
        )

        assertEquals(2, exchange.bodies.size)
        val result = exchange.bodies[1].toolResults().single().jsonObject
        assertEquals(HELD_BYTES, result.getValue(CONTENT).jsonPrimitive.content)
        assertNull(result["is_error"])
        assertEquals(1, exchange.outcome.held.size)
        assertFalse(ActionKind.COMMITTED in exchange.committed)
    }

    @Test(timeout = 30_000)
    fun anthropicParallelToolUseIsAnsweredInOneMessageInOrder() {
        val exchange = throughAnthropic(
            listOf(toolUseAnswer("toolu_a" to "milk", "toolu_b" to "eggs"), finalAnswer()),
        )

        assertEquals(2, exchange.bodies.size)
        val second = exchange.bodies[1].messages()
        assertEquals(listOf("user", "assistant", "user"), second.map { it.jsonObject.role() })
        val ids = exchange.bodies[1].toolResults().map { it.jsonObject.getValue("tool_use_id").jsonPrimitive.content }
        assertEquals(listOf("toolu_a", "toolu_b"), ids)
        assertEquals(listOf(ActionKind.COMMITTED, ActionKind.COMMITTED), exchange.committed)
    }
}
