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
import io.github.ygaray.voiceactionengine.core.strategy.plan.PlanThenExecuteStrategy
import io.github.ygaray.voiceactionengine.core.testing.FakeMutation
import io.github.ygaray.voiceactionengine.core.testing.RecordingCommitSink
import io.github.ygaray.voiceactionengine.core.testing.ScriptedCredentialSource
import io.github.ygaray.voiceactionengine.core.testing.ScriptedGate
import io.github.ygaray.voiceactionengine.core.testing.ScriptedSelectionSource
import io.github.ygaray.voiceactionengine.core.testing.ScriptedToolExecutor
import io.github.ygaray.voiceactionengine.providers.anthropic.AnthropicProvider
import io.github.ygaray.voiceactionengine.providers.anthropic.successBody
import io.github.ygaray.voiceactionengine.providers.anthropic.toolUseBlock
import io.github.ygaray.voiceactionengine.providers.chat.ChatCompletionsProvider
import io.github.ygaray.voiceactionengine.providers.chat.chatBody
import io.github.ygaray.voiceactionengine.providers.chat.chatMessage
import io.github.ygaray.voiceactionengine.providers.chat.chatToolCall
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
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
import java.util.concurrent.TimeUnit

private const val PLAN_FAKE_KEY = "sk-test-key"
private const val PLAN_REQUEST_WAIT_SECONDS = 5L
private const val CREATE_TOOL = "create_item"
private const val TAG_TOOL = "tag_item"
private const val ID_FIELD = "item_id"
private const val FIRST_CALL_ID = "plan_1"
private const val SECOND_CALL_ID = "plan_2"
private const val PLAN_TOOL = "submit_plan"
private const val PLAN_TOOLS = "tools"
private const val PLAN_MESSAGES = "messages"
private const val PLAN_TOOL_CHOICE = "tool_choice"
private const val PLAN_ROLE = "role"
private const val PLAN_CONTENT = "content"
private const val PLAN_TYPE = "type"
private const val DIGEST = """{"status":"plan_rejected","reason":"bad_reference","step_index":1}"""

/** The three wires the replan is proven over, with the model each wire test already uses. */
private enum class PlanDialect(val providerId: ProviderId, val model: String) {
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

/** What one run did: the outcome and every request body the server saw. */
private class PlanExchange(val outcome: CommandOutcome, val bodies: List<JsonObject>, val committed: List<ActionKind>)

private fun schemaOf(field: String): JsonObject = buildJsonObject {
    put(PLAN_TYPE, "object")
    putJsonObject("properties") { putJsonObject(field) { put(PLAN_TYPE, "string") } }
}

private fun createTool(): ToolSpec =
    ToolSpec(CREATE_TOOL, "Creates one item and returns item_id.", schemaOf("name"), true)

private fun tagTool(): ToolSpec =
    ToolSpec(TAG_TOOL, "Adds a tag to an item, given its item_id.", schemaOf(ID_FIELD), true)

private fun step(id: String, tool: String, argument: Pair<String, String>): JsonObject = buildJsonObject {
    put("id", id)
    put("tool", tool)
    put("arguments", buildJsonObject { put(argument.first, argument.second) })
}

// Step 2 names a step nobody declared: the whole plan is rejected with bad_reference at index 1.
private fun rejectedPlan(): JsonObject = twoStepPlan("\$s9.item_id")

private fun validPlan(): JsonObject = twoStepPlan("\$s1.item_id")

private fun twoStepPlan(reference: String): JsonObject =
    planOf(step("s1", CREATE_TOOL, "name" to "a"), step("s2", TAG_TOOL, ID_FIELD to reference))

private fun planOf(vararg steps: JsonObject): JsonObject = buildJsonObject {
    put("steps", buildJsonArray { steps.forEach { add(it) } })
}

private fun answerOf(dialect: PlanDialect, callId: String, plan: JsonObject): String =
    if (dialect == PlanDialect.ANTHROPIC) {
        successBody(listOf(toolUseBlock(callId, PLAN_TOOL, plan)), "tool_use")
    } else {
        chatBody(chatMessage(null, listOf(chatToolCall(callId, PLAN_TOOL, plan.toString()))), "tool_calls")
    }

private fun planTier(): PlanThenExecuteStrategy =
    PlanThenExecuteStrategy(StrategyId("plan")) {
        tooling = ToolSpecProvider.fixed(ToolingSnapshot("fixed system", listOf(createTool(), tagTool()), null))
        executor = ScriptedToolExecutor(null) { call, _ ->
            ToolStep.Mutation(
                FakeMutation(call.toolName, StepResult("ok", false, "tok", mapOf(ID_FIELD to "id-1"))),
            )
        }
        clock = Clock.fixed(Instant.parse("2026-10-01T16:30:12Z"), ZoneId.of("UTC"))
    }

/**
 * Runs one command through the real plan tier and provider: the server first answers a plan that is statically
 * rejected, then a valid two-step plan. [forced] says whether the model is declared to accept a forced tool choice.
 */
private fun drivePlan(dialect: PlanDialect, forced: Boolean): PlanExchange = runBlocking {
    MockWebServer().use { server ->
        server.enqueue(MockResponse().setResponseCode(200).setBody(answerOf(dialect, FIRST_CALL_ID, rejectedPlan())))
        server.enqueue(MockResponse().setResponseCode(200).setBody(answerOf(dialect, SECOND_CALL_ID, validPlan())))
        server.start()
        val sink = RecordingCommitSink()
        val pipeline = commandPipeline {
            tier(planTier())
            provider(dialect.provider(server.url("/")))
            providerSelection = ScriptedSelectionSource.fixed(ProviderSelection(dialect.providerId, dialect.model))
            credentials = ScriptedCredentialSource.keys(dialect.providerId to PLAN_FAKE_KEY)
            gate = ScriptedGate.admitAll()
            commitSink = sink
            if (forced) {
                capabilities(dialect.providerId, dialect.model) { supportsForcedToolChoice = true }
            } else {
                capabilities(dialect.providerId, dialect.model) { supportsForcedToolChoice = false }
            }
        }

        val outcome = pipeline.execute(CommandInput("make a and tag it", "en", null))

        val texts = (1..server.requestCount).map {
            server.takeRequest(PLAN_REQUEST_WAIT_SECONDS, TimeUnit.SECONDS)?.body?.readUtf8().orEmpty()
        }
        PlanExchange(outcome, texts.map { Json.parseToJsonElement(it).jsonObject }, sink.actions.map { it.action.kind })
    }
}

private fun JsonObject.messages(): JsonArray = getValue(PLAN_MESSAGES).jsonArray

private fun JsonObject.role(): String = getValue(PLAN_ROLE).jsonPrimitive.content

/** What the provider can cache: the system text and the tools, as the dialect carries them. */
private fun JsonObject.cachedPrefix(dialect: PlanDialect): String =
    if (dialect == PlanDialect.ANTHROPIC) {
        getValue("system").toString() + getValue(PLAN_TOOLS).toString()
    } else {
        messages().first().toString() + getValue(PLAN_TOOLS).toString()
    }

/** The result the second request carries for the plan call: its call id and its content bytes. */
private fun JsonObject.planCallResult(dialect: PlanDialect): Pair<String, String> =
    if (dialect == PlanDialect.ANTHROPIC) {
        val blocks = messages().last().jsonObject.getValue(PLAN_CONTENT).jsonArray.map { it.jsonObject }
        val result = blocks.first { it.getValue(PLAN_TYPE).jsonPrimitive.content == "tool_result" }
        assertEquals("true", result.getValue("is_error").jsonPrimitive.content)
        result.getValue("tool_use_id").jsonPrimitive.content to result.getValue(PLAN_CONTENT).jsonPrimitive.content
    } else {
        val tool = messages().map { it.jsonObject }.last { it.role() == "tool" }
        tool.getValue("tool_call_id").jsonPrimitive.content to tool.getValue(PLAN_CONTENT).jsonPrimitive.content
    }

// The Anthropic wire carries the digest bytes with is_error true; the chat wires carry no error flag, so the encoder
// wraps an error result as {"error":<content as a JSON string>}. Either way the model reads the engine's digest.
private fun expectedResultContent(dialect: PlanDialect): String =
    if (dialect == PlanDialect.ANTHROPIC) DIGEST else buildJsonObject { put("error", DIGEST) }.toString()

private fun assertCompletedWithTwoCommits(label: String, exchange: PlanExchange) {
    assertTrue(label + exchange.outcome, exchange.outcome is CommandOutcome.Completed)
    assertEquals(label, listOf(ActionKind.COMMITTED, ActionKind.COMMITTED), exchange.committed)
}

/** The plan and the replan request over the real wires: one cached prefix, one tool choice, the plan call answered. */
class PlanThenExecuteWireTest {

    @Test(timeout = 30_000)
    fun theCachedPrefixAndToolChoiceBytesAreIdenticalAcrossPlanAndReplanOnEveryWire() {
        PlanDialect.entries.forEach { dialect ->
            val exchange = drivePlan(dialect, forced = true)

            val label = dialect.toString()
            assertEquals(label, 2, exchange.bodies.size)
            assertCompletedWithTwoCommits(label, exchange)
            val (first, second) = exchange.bodies
            assertEquals(label, first.cachedPrefix(dialect), second.cachedPrefix(dialect))
            val choice = first.getValue(PLAN_TOOL_CHOICE).toString()
            assertEquals(label, choice, second.getValue(PLAN_TOOL_CHOICE).toString())
            assertTrue(label, choice.contains(PLAN_TOOL))
        }
    }

    @Test(timeout = 30_000)
    fun theSecondRequestAnswersThePlanCallWithTheFixedDigestOnEveryWire() {
        PlanDialect.entries.forEach { dialect ->
            val exchange = drivePlan(dialect, forced = true)

            val label = dialect.toString()
            assertEquals(label, 2, exchange.bodies.size)
            val (callId, content) = exchange.bodies[1].planCallResult(dialect)
            assertEquals(label, FIRST_CALL_ID, callId)
            assertEquals(label, expectedResultContent(dialect), content)
        }
    }

    @Test(timeout = 30_000)
    fun theSubmitPlanToolIsNeverSentStrictOnEitherRequest() {
        PlanDialect.entries.forEach { dialect ->
            val exchange = drivePlan(dialect, forced = true)

            exchange.bodies.forEach { body ->
                val entries = body.getValue(PLAN_TOOLS).jsonArray.map { it.toString() }
                val plan = entries.single { it.contains("\"$PLAN_TOOL\"") }
                assertFalse(dialect.toString(), plan.contains("\"strict\":true"))
            }
        }
    }

    @Test(timeout = 30_000)
    fun anthropicReshapeModeKeepsSystemToolsAndToolChoiceBytesIdentical() {
        val exchange = drivePlan(PlanDialect.ANTHROPIC, forced = false)

        assertEquals(2, exchange.bodies.size)
        assertCompletedWithTwoCommits("reshape", exchange)
        val (first, second) = exchange.bodies
        // The instruction line moves to the last user-role message, so the messages are not compared here.
        assertEquals(first.cachedPrefix(PlanDialect.ANTHROPIC), second.cachedPrefix(PlanDialect.ANTHROPIC))
        assertEquals(first.getValue(PLAN_TOOL_CHOICE).toString(), second.getValue(PLAN_TOOL_CHOICE).toString())
        assertEquals("auto", first.getValue(PLAN_TOOL_CHOICE).jsonObject.getValue(PLAN_TYPE).jsonPrimitive.content)
        val (callId, content) = second.planCallResult(PlanDialect.ANTHROPIC)
        assertEquals(FIRST_CALL_ID, callId)
        assertEquals(DIGEST, content)
    }
}
