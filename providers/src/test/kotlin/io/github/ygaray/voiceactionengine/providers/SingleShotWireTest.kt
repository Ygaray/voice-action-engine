package io.github.ygaray.voiceactionengine.providers

import io.github.ygaray.voiceactionengine.core.CommandInput
import io.github.ygaray.voiceactionengine.core.ProviderId
import io.github.ygaray.voiceactionengine.core.StrategyId
import io.github.ygaray.voiceactionengine.core.commit.ActionKind
import io.github.ygaray.voiceactionengine.core.commit.StepResult
import io.github.ygaray.voiceactionengine.core.commit.ToolStep
import io.github.ygaray.voiceactionengine.core.failure.EscalationReason
import io.github.ygaray.voiceactionengine.core.failure.FailureReason
import io.github.ygaray.voiceactionengine.core.pipeline.CommandOutcome
import io.github.ygaray.voiceactionengine.core.pipeline.commandPipeline
import io.github.ygaray.voiceactionengine.core.provider.AiProvider
import io.github.ygaray.voiceactionengine.core.provider.ProviderSelection
import io.github.ygaray.voiceactionengine.core.strategy.CommandStrategy
import io.github.ygaray.voiceactionengine.core.strategy.Extraction
import io.github.ygaray.voiceactionengine.core.strategy.OutcomeResolver
import io.github.ygaray.voiceactionengine.core.strategy.Resolution
import io.github.ygaray.voiceactionengine.core.strategy.StrategyOutcome
import io.github.ygaray.voiceactionengine.core.strategy.ToolSpec
import io.github.ygaray.voiceactionengine.core.strategy.ToolSpecProvider
import io.github.ygaray.voiceactionengine.core.strategy.ToolingSnapshot
import io.github.ygaray.voiceactionengine.core.strategy.singleshot.SingleShotStrategy
import io.github.ygaray.voiceactionengine.core.testing.FakeMutation
import io.github.ygaray.voiceactionengine.core.testing.RecordingCommitSink
import io.github.ygaray.voiceactionengine.core.testing.ScriptedCredentialSource
import io.github.ygaray.voiceactionengine.core.testing.ScriptedGate
import io.github.ygaray.voiceactionengine.core.testing.ScriptedSelectionSource
import io.github.ygaray.voiceactionengine.core.testing.ScriptedStrategy
import io.github.ygaray.voiceactionengine.core.telemetry.TraceCode
import io.github.ygaray.voiceactionengine.providers.anthropic.AnthropicProvider
import io.github.ygaray.voiceactionengine.providers.anthropic.successBody
import io.github.ygaray.voiceactionengine.providers.anthropic.textBlock
import io.github.ygaray.voiceactionengine.providers.anthropic.toolUseBlock
import io.github.ygaray.voiceactionengine.providers.chat.ChatCompletionsProvider
import io.github.ygaray.voiceactionengine.providers.chat.chatBody
import io.github.ygaray.voiceactionengine.providers.chat.chatMessage
import io.github.ygaray.voiceactionengine.providers.chat.chatToolCall
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
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
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

private const val ENTRIES_TOOL = "record_entries"
private const val FAKE_KEY = "sk-test-key"
private const val REQUEST_WAIT_SECONDS = 5L

/** An app resolver that records what it is asked and prepares one committed change per call. */
private class EntryResolver : OutcomeResolver {
    private val seen = CopyOnWriteArrayList<Extraction>()

    /** Every extraction received, in order. */
    val extractions: List<Extraction> get() = seen.toList()

    override suspend fun resolve(extraction: Extraction, input: CommandInput): Resolution {
        seen.add(extraction)
        val mutation = FakeMutation(ENTRIES_TOOL, StepResult("saved", false, "ok", emptyMap()))
        return Resolution.Steps(listOf(ToolStep.Mutation(mutation)))
    }
}

/** What one scenario did: the outcome, the wire facts and the test doubles it touched. */
private class Exchange(
    val outcome: CommandOutcome,
    val requestCount: Int,
    val bodyText: String,
    val resolver: EntryResolver,
    val gate: ScriptedGate,
    val sink: RecordingCommitSink,
) {
    val body: JsonObject get() = Json.parseToJsonElement(bodyText).jsonObject
}

/** One scenario: the provider under test, the model it serves and the single answer the server gives. */
private class Scenario(
    val provider: (HttpUrl) -> AiProvider,
    val providerId: ProviderId,
    val model: String,
    val answer: String,
    val forcingOverride: Boolean = false,
    val fallback: CommandStrategy? = null,
)

private fun entrySchema(): JsonObject = buildJsonObject {
    put("type", "object")
    putJsonObject("properties") { putJsonObject("entry") { put("type", "string") } }
    putJsonArray("required") { add("entry") }
    put("additionalProperties", false)
}

private fun entryArguments(entry: String): JsonObject = buildJsonObject { put("entry", entry) }

private fun singleShotTier(resolver: OutcomeResolver): SingleShotStrategy {
    val tool = ToolSpec(ENTRIES_TOOL, "Records one or more entries.", entrySchema(), true)
    return SingleShotStrategy(StrategyId("single_shot")) {
        tooling = ToolSpecProvider.fixed(ToolingSnapshot("fixed system", listOf(tool), ENTRIES_TOOL))
        this.resolver = resolver
        clock = Clock.fixed(Instant.parse("2026-10-01T16:30:12Z"), ZoneId.of("UTC"))
    }
}

/** Runs "add two things" through SingleShot and the real provider against one local server answer. */
private fun drive(scenario: Scenario): Exchange = runBlocking {
    MockWebServer().use { server ->
        server.enqueue(MockResponse().setResponseCode(200).setBody(scenario.answer))
        server.start()
        val resolver = EntryResolver()
        val gate = ScriptedGate.admitAll()
        val sink = RecordingCommitSink()
        val pipeline = commandPipeline {
            tier(singleShotTier(resolver))
            scenario.fallback?.let { tier(it) }
            provider(scenario.provider(server.url("/")))
            providerSelection = ScriptedSelectionSource.fixed(ProviderSelection(scenario.providerId, scenario.model))
            credentials = ScriptedCredentialSource.keys(scenario.providerId to FAKE_KEY)
            this.gate = gate
            commitSink = sink
            if (scenario.forcingOverride) {
                capabilities(scenario.providerId, scenario.model) { supportsForcedToolChoice = true }
            }
        }

        val outcome = pipeline.execute(CommandInput("add two things", "en", null))

        val sent = server.takeRequest(REQUEST_WAIT_SECONDS, TimeUnit.SECONDS)
        Exchange(outcome, server.requestCount, sent?.body?.readUtf8().orEmpty(), resolver, gate, sink)
    }
}

/** SingleShot over the real transports: the request bytes each vendor accepts and the outcome the answer maps to. */
class SingleShotWireTest {

    private fun throughAnthropic(model: String, forcingOverride: Boolean, answer: String): Exchange = drive(
        Scenario(
            { url -> AnthropicProvider { baseUrl = url } },
            ProviderId.ANTHROPIC,
            model,
            answer,
            forcingOverride,
        ),
    )

    private fun toolUseAnswer(): String =
        successBody(listOf(toolUseBlock("toolu_1", ENTRIES_TOOL, entryArguments("milk"))), "tool_use")

    private fun assertCommittedOnce(exchange: Exchange) {
        assertEquals(exchange.outcome.toString(), 1, exchange.requestCount)
        assertTrue(exchange.outcome.toString(), exchange.outcome is CommandOutcome.Completed)
        assertEquals(listOf(ENTRIES_TOOL), exchange.resolver.extractions.map { it.toolName })
        assertEquals(1, exchange.gate.calls)
        assertEquals(listOf(ActionKind.COMMITTED), exchange.sink.actions.map { it.action.kind })
    }

    @Test(timeout = 30_000)
    fun anthropicForcedBodyAsksForOneToolAndCommits() {
        val exchange = throughAnthropic("claude-opus-5-5", forcingOverride = true, answer = toolUseAnswer())

        assertEquals(
            buildJsonObject {
                put("type", "tool")
                put("name", ENTRIES_TOOL)
                put("disable_parallel_tool_use", true)
            },
            exchange.body["tool_choice"],
        )
        assertCommittedOnce(exchange)
    }

    @Test(timeout = 30_000)
    fun anthropicReshapedBodyAsksForOneToolAndCommits() {
        val exchange = throughAnthropic("claude-opus-5-5", forcingOverride = false, answer = toolUseAnswer())

        assertEquals(
            buildJsonObject {
                put("type", "auto")
                put("disable_parallel_tool_use", true)
            },
            exchange.body["tool_choice"],
        )
        val lastBlock = exchange.body["messages"]!!.jsonArray.last().jsonObject["content"]!!.jsonArray.last().jsonObject
        assertEquals("text", lastBlock["type"]!!.jsonPrimitive.content)
        assertEquals("Call the $ENTRIES_TOOL tool with your result.", lastBlock["text"]!!.jsonPrimitive.content)
        assertCommittedOnce(exchange)
    }

    private fun throughChat(openRouter: Boolean, model: String, answer: String, fallback: CommandStrategy? = null) =
        drive(
            Scenario(
                { url ->
                    if (openRouter) {
                        ChatCompletionsProvider.openRouter { baseUrl = url }
                    } else {
                        ChatCompletionsProvider.openAi { baseUrl = url }
                    }
                },
                if (openRouter) ProviderId.OPENROUTER else ProviderId.OPENAI,
                model,
                answer,
                fallback = fallback,
            ),
        )

    private fun chatCallAnswer(vararg entries: String): String = chatBody(
        chatMessage(
            null,
            entries.mapIndexed { index, entry ->
                chatToolCall("call_$index", ENTRIES_TOOL, entryArguments(entry).toString())
            },
        ),
        "tool_calls",
    )

    @Test(timeout = 30_000)
    fun openAiBodySendsParallelToolCallsFalseAndCommits() {
        val exchange = throughChat(openRouter = false, model = "gpt-5.4-mini", answer = chatCallAnswer("milk"))

        assertEquals(
            buildJsonObject {
                put("type", "function")
                putJsonObject("function") { put("name", ENTRIES_TOOL) }
            },
            exchange.body["tool_choice"],
        )
        assertEquals(false, exchange.body["parallel_tool_calls"]!!.jsonPrimitive.content.toBooleanStrict())
        assertCommittedOnce(exchange)
    }

    @Test(timeout = 30_000)
    fun openRouterBodyOmitsParallelToolCallsAndUsesTheFirstCall() {
        val exchange = throughChat(
            openRouter = true,
            model = "openai/gpt-5.4-mini",
            answer = chatCallAnswer("milk", "eggs"),
        )

        assertFalse(exchange.bodyText.contains("parallel_tool_calls"))
        assertEquals(
            buildJsonObject { put("require_parameters", true) },
            exchange.body["provider"],
        )
        assertEquals(listOf(entryArguments("milk")), exchange.resolver.extractions.map { it.arguments })
        assertTrue(TraceCode.EXTRA_TOOL_CALLS_DROPPED in exchange.outcome.trace.codes)
        assertTrue((exchange.outcome as CommandOutcome.Completed).partial)
        assertCommittedOnce(exchange)
    }

    @Test(timeout = 30_000)
    fun openAiProseAnswerEscalatesToTheNextTier() {
        val next = ScriptedStrategy(StrategyId("next"), { _, _ -> StrategyOutcome.Completed("fallback") })
        val prose = chatBody(chatMessage("I added them.", emptyList()), "stop")

        val exchange = throughChat(openRouter = false, model = "gpt-5.4-mini", answer = prose, fallback = next)

        val outcome = exchange.outcome
        assertTrue(outcome.toString(), outcome is CommandOutcome.Completed)
        assertEquals("fallback", (outcome as CommandOutcome.Completed).reply)
        assertTrue(outcome.trace.attempts.first().escalationReason is EscalationReason.NoToolCall)
        assertEquals(1, next.executions)
        assertEquals(1, exchange.requestCount)
        assertEquals(0, exchange.resolver.extractions.size)
    }

    @Test(timeout = 30_000)
    fun anthropicRefusalFailsWithRefusal() {
        val refusal = successBody(listOf(textBlock("I can't help with that.")), "refusal")

        val exchange = throughAnthropic("claude-opus-5-5", forcingOverride = true, answer = refusal)

        val outcome = exchange.outcome
        assertTrue(outcome.toString(), outcome is CommandOutcome.Failed)
        assertTrue((outcome as CommandOutcome.Failed).reason is FailureReason.Refusal)
        assertEquals(0, exchange.gate.calls)
        assertEquals(1, exchange.requestCount)
        assertEquals(0, exchange.resolver.extractions.size)
    }
}
