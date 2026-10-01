package io.github.ygaray.voiceactionengine.providers.parity

import io.github.ygaray.voiceactionengine.core.CommandInput
import io.github.ygaray.voiceactionengine.core.ProviderId
import io.github.ygaray.voiceactionengine.core.StrategyId
import io.github.ygaray.voiceactionengine.core.pipeline.CommandOutcome
import io.github.ygaray.voiceactionengine.core.pipeline.TierPolicy
import io.github.ygaray.voiceactionengine.core.pipeline.TierPolicySource
import io.github.ygaray.voiceactionengine.core.pipeline.commandPipeline
import io.github.ygaray.voiceactionengine.core.provider.AiProvider
import io.github.ygaray.voiceactionengine.core.provider.ModelResult
import io.github.ygaray.voiceactionengine.core.provider.ProviderSelection
import io.github.ygaray.voiceactionengine.core.strategy.StrategyOutcome
import io.github.ygaray.voiceactionengine.core.strategy.ToolSpec
import io.github.ygaray.voiceactionengine.core.telemetry.Usage
import io.github.ygaray.voiceactionengine.core.testing.RecordingCommitSink
import io.github.ygaray.voiceactionengine.core.testing.ScriptedCredentialSource
import io.github.ygaray.voiceactionengine.core.testing.ScriptedGate
import io.github.ygaray.voiceactionengine.core.testing.ScriptedSelectionSource
import io.github.ygaray.voiceactionengine.core.testing.ScriptedStrategy
import io.github.ygaray.voiceactionengine.core.testing.StrategyStep
import io.github.ygaray.voiceactionengine.core.transcript.ModelRequest
import io.github.ygaray.voiceactionengine.core.transcript.UserMessage
import io.github.ygaray.voiceactionengine.providers.anthropic.AnthropicProvider
import io.github.ygaray.voiceactionengine.providers.anthropic.successBody
import io.github.ygaray.voiceactionengine.providers.anthropic.toolUseBlock
import io.github.ygaray.voiceactionengine.providers.anthropic.usageJson
import io.github.ygaray.voiceactionengine.providers.chat.ChatCompletionsProvider
import io.github.ygaray.voiceactionengine.providers.chat.chatBody
import io.github.ygaray.voiceactionengine.providers.chat.chatMessage
import io.github.ygaray.voiceactionengine.providers.chat.chatToolCall
import io.github.ygaray.voiceactionengine.providers.chat.chatUsage
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * One logical model call, written in each vendor's wire format, counts the same on Anthropic, OpenAI and OpenRouter:
 * the same four usage buckets, the same `tokensUsed`, and the same answer to "is this run over its token ceiling".
 *
 * Anthropic reports `input_tokens` without the cached tokens; OpenAI and OpenRouter report `prompt_tokens` with them.
 * A wrong mapping on one side would trip the ceiling early or never trip it on that provider only.
 *
 * Assumption for the cache-write fixtures: the OpenAI-shaped `prompt_tokens` is taken to include `cache_write_tokens`
 * as well as `cached_tokens`. The live capture did not observe a non-zero cache write, so this is unverified against
 * the real services; the Phase 10 smoke should pin a real cache-write response.
 */
class TokenParityTest {

    private val addItem = ToolSpec("add_item", "Adds one item.", buildJsonObject { put("type", "object") })

    private fun request(): ModelRequest =
        ModelRequest("You are a test system.", listOf(UserMessage("add milk")), listOf(addItem), 256)

    /** A provider under test: how to build it against a server, which model it is asked for, how it words usage. */
    private class Case(
        val name: String,
        val providerId: ProviderId,
        val model: String,
        val build: (MockWebServer) -> AiProvider,
        val body: (Shape) -> String,
    )

    /** The logical call: uncached input, cache reads, cache writes and output, in tokens. */
    private class Shape(val uncached: Long, val read: Long, val write: Long, val output: Long) {
        val expected: Usage get() = Usage(uncached, read, write, output)
        val total: Long get() = uncached + read + write + output
    }

    /** What the strategy and the trace saw for one run. */
    private class Observed(
        val response: Usage,
        val tokensUsed: Long,
        val overCeiling: Boolean,
        val trace: Usage,
    )

    private val cases = listOf(
        Case(
            "anthropic",
            ProviderId.ANTHROPIC,
            "claude-haiku-4-5",
            { server -> AnthropicProvider { baseUrl = server.url("/") } },
        ) { shape ->
            successBody(
                listOf(toolUseBlock("toolu_1", "add_item", buildJsonObject { put("item", "milk") })),
                "tool_use",
                usageJson(shape.uncached, shape.output, cacheCreation = shape.write, cacheRead = shape.read),
            )
        },
        Case(
            "openai",
            ProviderId.OPENAI,
            "gpt-5.4-mini",
            { server -> ChatCompletionsProvider.openAi { baseUrl = server.url("/") } },
        ) { shape -> chatToolBody(chatShapedUsage(shape), "chatcmpl-GOLDEN") },
        Case(
            "openrouter",
            ProviderId.OPENROUTER,
            "openai/gpt-5.4-mini",
            { server -> ChatCompletionsProvider.openRouter { baseUrl = server.url("/") } },
        ) { shape -> chatToolBody(chatShapedUsage(shape), "gen-GOLDEN") },
    )

    private fun chatToolBody(usage: JsonObject, id: String): String = chatBody(
        chatMessage(null, listOf(chatToolCall("call_1", "add_item", """{"item":"milk"}"""))),
        "tool_calls",
        usage,
        id,
    )

    /** OpenAI wording: `prompt_tokens` holds every input token, the cache counts are details inside it. */
    private fun chatShapedUsage(shape: Shape): JsonObject = chatUsage(
        prompt = shape.uncached + shape.read + shape.write,
        completion = shape.output,
        cached = shape.read,
        cacheWrite = shape.write.takeIf { it > 0 },
    )

    /** The same OpenRouter usage plus the vendor's extra fields, which must change nothing. */
    private fun openRouterWithExtras(shape: Shape): String {
        val base = chatShapedUsage(shape)
        val withExtras = buildJsonObject {
            base.forEach { (key, value) -> put(key, value) }
            put("cost", 0.0012)
            putJsonObject("cost_details") {
                put("upstream_inference_cost", 0.0011)
            }
            putJsonObject("completion_tokens_details") {
                put("reasoning_tokens", REASONING_TOKENS)
            }
        }
        return chatToolBody(withExtras, "gen-GOLDEN")
    }

    private fun run(case: Case, body: String, ceiling: Long?): Observed = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(200).setBody(body))
            server.start()
            var response: Usage? = null
            var tokensUsed = -1L
            var overCeiling = false
            val step: StrategyStep = { _, session ->
                val result = session.model().complete(request()) as ModelResult.Success
                response = result.response.usage
                tokensUsed = session.tokensUsed
                overCeiling = session.tokensUsed > session.policy.tokenCeiling
                StrategyOutcome.Completed(null)
            }
            val pipeline = commandPipeline {
                tier(ScriptedStrategy(StrategyId("probe"), step))
                provider(case.build(server))
                providerSelection = ScriptedSelectionSource.fixed(ProviderSelection(case.providerId, case.model))
                credentials = ScriptedCredentialSource.keys(case.providerId to "sk-test-key")
                gate = ScriptedGate.admitAll()
                commitSink = RecordingCommitSink()
                if (ceiling != null) policy = TierPolicySource.fixed(TierPolicy { tokenCeiling = ceiling })
            }

            val outcome = pipeline.execute(CommandInput("add milk", "en", null))

            assertTrue("${case.name}: $outcome", outcome is CommandOutcome.Completed)
            assertEquals("${case.name}: one request", 1, server.requestCount)
            val answered = checkNotNull(response) { "${case.name}: no response" }
            Observed(answered, tokensUsed, overCeiling, outcome.trace.usage)
        }
    }

    private fun assertSameEverywhere(shape: Shape, bodyFor: (Case) -> String) {
        val seen = cases.map { it.name to run(it, bodyFor(it), null) }
        for ((name, observed) in seen) {
            assertEquals("$name response usage", shape.expected, observed.response)
            assertEquals("$name trace usage", shape.expected, observed.trace)
            assertEquals("$name tokensUsed", shape.total, observed.tokensUsed)
        }
        assertEquals(1, seen.map { it.second.tokensUsed }.toSet().size)
    }

    @Test(timeout = 60_000)
    fun theSameCallCostsTheSameTokensOnAllThreeProviders() {
        val shape = Shape(uncached = 120, read = 1_800, write = 0, output = 55)
        assertEquals(Usage(120, 1_800, 0, 55), shape.expected)
        assertEquals(1_975L, shape.total)
        assertSameEverywhere(shape) { it.body(shape) }
    }

    @Test(timeout = 60_000)
    fun aCacheWriteCountsOnceOnAllThreeProviders() {
        val shape = Shape(uncached = 120, read = 1_800, write = 500, output = 55)
        assertEquals(Usage(120, 1_800, 500, 55), shape.expected)
        assertEquals(2_475L, shape.total)
        assertSameEverywhere(shape) { it.body(shape) }
    }

    @Test(timeout = 60_000)
    fun openRouterExtraUsageFieldsChangeNothing() {
        val shape = Shape(uncached = 120, read = 1_800, write = 0, output = 55)
        val cacheWrite = Shape(uncached = 120, read = 1_800, write = 500, output = 55)
        val openRouter = cases.single { it.providerId == ProviderId.OPENROUTER }
        for (variant in listOf(shape, cacheWrite)) {
            val plain = run(openRouter, openRouter.body(variant), null)
            val extras = run(openRouter, openRouterWithExtras(variant), null)
            assertEquals(variant.expected, extras.response)
            assertEquals(plain.response, extras.response)
            assertEquals(variant.total, extras.tokensUsed)
            assertEquals(plain.trace, extras.trace)
        }
    }

    @Test(timeout = 120_000)
    fun theTokenCeilingTripsAtTheSamePointOnEveryProvider() {
        val shapes = listOf(
            Shape(uncached = 120, read = 1_800, write = 0, output = 55),
            Shape(uncached = 120, read = 1_800, write = 500, output = 55),
        )
        for (shape in shapes) {
            // tokensUsed > tokenCeiling: over just below the total, not over at or above it.
            val ceilings = listOf(shape.total - 1, shape.total, shape.total + 1)
            for (case in cases) {
                val trips = ceilings.map { run(case, case.body(shape), it).overCeiling }
                assertEquals("${case.name} total=${shape.total}", listOf(true, false, false), trips)
            }
        }
    }

    private companion object {
        const val REASONING_TOKENS = 20L
    }
}
