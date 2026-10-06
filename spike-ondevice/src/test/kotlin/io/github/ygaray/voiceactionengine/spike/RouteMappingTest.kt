package io.github.ygaray.voiceactionengine.spike

import io.github.ygaray.voiceactionengine.core.ProviderId
import io.github.ygaray.voiceactionengine.core.failure.FailureReason
import io.github.ygaray.voiceactionengine.core.provider.ModelCapabilities
import io.github.ygaray.voiceactionengine.core.provider.ModelResult
import io.github.ygaray.voiceactionengine.core.provider.ProviderRequest
import io.github.ygaray.voiceactionengine.core.strategy.ToolSpec
import io.github.ygaray.voiceactionengine.core.transcript.AssistantPart
import io.github.ygaray.voiceactionengine.core.transcript.CacheDirective
import io.github.ygaray.voiceactionengine.core.transcript.ModelRequest
import io.github.ygaray.voiceactionengine.core.transcript.StopReason
import io.github.ygaray.voiceactionengine.core.transcript.ToolChoice
import io.github.ygaray.voiceactionengine.core.transcript.UserMessage
import io.github.ygaray.voiceactionengine.spike.backend.BackendMode
import io.github.ygaray.voiceactionengine.spike.backend.RawToolCall
import io.github.ygaray.voiceactionengine.spike.provider.ProviderRoute
import io.github.ygaray.voiceactionengine.spike.provider.SpikeOnDeviceProvider
import io.github.ygaray.voiceactionengine.spike.provider.failure
import io.github.ygaray.voiceactionengine.spike.provider.jsonToMap
import io.github.ygaray.voiceactionengine.spike.provider.mapToJson
import io.github.ygaray.voiceactionengine.spike.provider.SchemaTypeMismatch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

private const val CREATE = "create_x"
private const val FIND = "find_x"
private const val ASK = "ask_x"
private const val SYSTEM = "system-text"
private const val USER = "user-text"
private const val MAX_TOKENS = 321

/** Both routes and both shapes map the neutral request and answer, and every failure is a stable code. */
class RouteMappingTest {
    private fun obj(field: String, type: String): JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") { putJsonObject(field) { put("type", type) } }
    }

    private val create = ToolSpec(CREATE, "Creates.", obj("count", "integer"), mutating = true)
    private val find = ToolSpec(FIND, "Finds.", obj("query", "string"))
    private val ask = ToolSpec.clarification(ASK)
    private val tools = listOf(create, find, ask)

    private fun call(choice: ToolChoice, offered: List<ToolSpec> = tools) = ProviderRequest(
        "e2b",
        ModelRequest(
            SYSTEM, listOf(UserMessage(USER)), offered, choice, MAX_TOKENS, CacheDirective(true), true,
        ),
        null,
        ModelCapabilities.UNKNOWN,
    )

    private fun provider(backend: FakeLlmBackend, route: ProviderRoute) =
        SpikeOnDeviceProvider(backend, route, Dispatchers.Unconfined)

    private fun success(result: ModelResult) = (result as ModelResult.Success).response

    private fun code(result: ModelResult): String? {
        val reason = (result as ModelResult.Failure).reason as FailureReason.ProviderUnavailable
        assertEquals(ProviderId.ON_DEVICE, reason.provider)
        return reason.cause
    }

    // Route A, model chooses

    @Test
    fun theAutoWrapperOffersTheToolNamesPlusNoneAndRequiresBothFields() = runTest {
        val backend = FakeLlmBackend(FakeLlmBackend.text("""{"tool":"find_x","arguments":{"query":"a"}}"""))

        val response = success(provider(backend, ProviderRoute.CONSTRAINED_JSON).complete(call(ToolChoice.Auto())))

        val schema = (backend.requests.single().mode as BackendMode.Constrained).schema
        val properties = schema["properties"] as JsonObject
        val tool = properties["tool"] as JsonObject
        assertEquals(JsonPrimitive("string"), tool["type"])
        assertEquals(listOf(CREATE, FIND, ASK, "none"), (tool["enum"] as JsonArray).map { (it as JsonPrimitive).content })
        assertEquals(JsonPrimitive("object"), (properties["arguments"] as JsonObject)["type"])
        assertEquals(listOf("tool", "arguments"), (schema["required"] as JsonArray).map { (it as JsonPrimitive).content })
        val picked = response.message.toolCalls.single()
        assertEquals(FIND, picked.name)
        assertEquals(buildJsonObject { put("query", "a") }, picked.arguments)
        assertEquals(StopReason.TOOL_USE, response.stopReason)
    }

    @Test
    fun noneDeclinesAsATextOnlyAnswerWithEndTurn() = runTest {
        val backend = FakeLlmBackend(FakeLlmBackend.text("""{"tool":"none","arguments":{}}"""))

        val response = success(provider(backend, ProviderRoute.CONSTRAINED_JSON).complete(call(ToolChoice.Auto())))

        assertTrue(response.message.toolCalls.isEmpty())
        assertTrue(response.message.parts.all { it is AssistantPart.Text })
        assertEquals(StopReason.END_TURN, response.stopReason)
    }

    @Test
    fun aToolThatWasNotOfferedIsMalformedOutput() = runTest {
        val backend = FakeLlmBackend(FakeLlmBackend.text("""{"tool":"delete_everything","arguments":{}}"""))

        val result = provider(backend, ProviderRoute.CONSTRAINED_JSON).complete(call(ToolChoice.Auto()))

        assertEquals("malformed_output", code(result))
    }

    @Test
    fun aWrapperWithoutArgumentsOrNotJsonIsMalformedOutput() = runTest {
        val backend = FakeLlmBackend(
            FakeLlmBackend.text("""{"tool":"find_x"}"""),
            FakeLlmBackend.text("not json at all"),
            FakeLlmBackend.text("""["tool","find_x"]"""),
        )
        val provider = provider(backend, ProviderRoute.CONSTRAINED_JSON)

        assertEquals("malformed_output", code(provider.complete(call(ToolChoice.Auto()))))
        assertEquals("malformed_output", code(provider.complete(call(ToolChoice.Auto()))))
        assertEquals("malformed_output", code(provider.complete(call(ToolChoice.Auto()))))
    }

    // Route A, forced

    @Test
    fun theForcedShapeUsesTheToolsOwnSchemaAndTheArgumentsObjectIsTheAnswer() = runTest {
        val backend = FakeLlmBackend(FakeLlmBackend.text("""{"count":2}"""))

        val response = success(provider(backend, ProviderRoute.CONSTRAINED_JSON).complete(call(ToolChoice.Required(CREATE))))

        assertEquals(create.inputSchema, (backend.requests.single().mode as BackendMode.Constrained).schema)
        assertEquals(CREATE, response.message.toolCalls.single().name)
        assertEquals(StopReason.TOOL_USE, response.stopReason)
        assertEquals(120L, response.usage.inputUncached)
        assertEquals(9L, response.usage.output)
    }

    // Route B

    @Test
    fun nativeToolsCarryEveryOfferedToolAndAnIntegralDoubleBecomesAnInteger() = runTest {
        val backend = FakeLlmBackend(FakeLlmBackend.tools("", RawToolCall(CREATE, mapOf("count" to 2.0))))

        val response = success(provider(backend, ProviderRoute.NATIVE_TOOLS).complete(call(ToolChoice.Auto())))

        val mode = backend.requests.single().mode as BackendMode.NativeTools
        assertEquals(listOf(CREATE, FIND, ASK), mode.tools.map { it.name })
        val picked = response.message.toolCalls.single()
        assertEquals(CREATE, picked.name)
        assertEquals(buildJsonObject { put("count", 2) }, picked.arguments)
        assertEquals(StopReason.TOOL_USE, response.stopReason)
    }

    @Test
    fun aFractionForAnIntegerFieldIsSchemaTypeMismatchAndNotAHarnessError() = runTest {
        val backend = FakeLlmBackend(
            FakeLlmBackend.tools("", RawToolCall(CREATE, mapOf("count" to 2.5))),
            FakeLlmBackend.tools("", RawToolCall(CREATE, mapOf("count" to "two"))),
        )
        val provider = provider(backend, ProviderRoute.NATIVE_TOOLS)

        assertEquals("schema_type_mismatch", code(provider.complete(call(ToolChoice.Required(CREATE)))))
        assertEquals("schema_type_mismatch", code(provider.complete(call(ToolChoice.Required(CREATE)))))
    }

    @Test
    fun noToolCallsWithTextIsATextOnlyEndTurn() = runTest {
        val backend = FakeLlmBackend(FakeLlmBackend.tools("no tool applies"))

        val response = success(provider(backend, ProviderRoute.NATIVE_TOOLS).complete(call(ToolChoice.Auto())))

        assertTrue(response.message.toolCalls.isEmpty())
        assertEquals(StopReason.END_TURN, response.stopReason)
    }

    @Test
    fun onlyTheFirstNativeCallIsTakenAndAnUnofferedNameIsMalformedOutput() = runTest {
        val backend = FakeLlmBackend(
            FakeLlmBackend.tools("", RawToolCall(FIND, mapOf("query" to "a")), RawToolCall(CREATE, mapOf("count" to 1.0))),
            FakeLlmBackend.tools("", RawToolCall("ghost_x", emptyMap())),
        )
        val provider = provider(backend, ProviderRoute.NATIVE_TOOLS)

        val first = success(provider.complete(call(ToolChoice.Auto())))
        assertEquals(listOf(FIND), first.message.toolCalls.map { it.name })
        assertEquals("malformed_output", code(provider.complete(call(ToolChoice.Auto()))))
    }

    // Failure map

    @Test
    fun everyBackendCodeSurfacesAsTheSameStableCause() = runTest {
        val codes = listOf(
            "model_missing", "abi_unsupported", "init_failed", "gpu_init_failed", "insufficient_memory",
            "native_error", "context_overflow",
        )
        val backend = FakeLlmBackend(*codes.map { FakeLlmBackend.fail(it) }.toTypedArray())
        val provider = provider(backend, ProviderRoute.CONSTRAINED_JSON)

        codes.forEach { expected -> assertEquals(expected, code(provider.complete(call(ToolChoice.Required(CREATE))))) }
    }

    @Test
    fun anUnexpectedExceptionBecomesNativeErrorWithoutItsMessage() = runTest {
        val backend = FakeLlmBackend(FakeLlmBackend.Step.Boom(IllegalStateException("secret-message-canary")))

        val result = provider(backend, ProviderRoute.CONSTRAINED_JSON).complete(call(ToolChoice.Required(CREATE)))

        assertEquals("native_error", code(result))
        assertFalse(result.toString().contains("canary"))
    }

    @Test
    fun aCodeThatIsNotStableIsReplacedByNativeError() {
        assertEquals("native_error", code(failure("Bad Code: with text")))
        assertEquals("native_error", code(failure("")))
        assertEquals("context_overflow", code(failure("context_overflow")))
    }

    @Test
    fun cancellationIsRethrownNeverCollapsedToAFailure() {
        val backend = FakeLlmBackend(FakeLlmBackend.Step.Boom(CancellationException("stop")))

        assertThrows(CancellationException::class.java) {
            runBlocking { provider(backend, ProviderRoute.CONSTRAINED_JSON).complete(call(ToolChoice.Required(CREATE))) }
        }
    }

    // Both routes differ only in mode

    @Test
    fun routeAAndRouteBRequestsDifferOnlyInMode() = runTest {
        val a = FakeLlmBackend(FakeLlmBackend.text("""{"tool":"none","arguments":{}}"""))
        val b = FakeLlmBackend(FakeLlmBackend.tools("x"))

        provider(a, ProviderRoute.CONSTRAINED_JSON).complete(call(ToolChoice.Auto()))
        provider(b, ProviderRoute.NATIVE_TOOLS).complete(call(ToolChoice.Auto()))

        val ra = a.requests.single()
        val rb = b.requests.single()
        assertEquals(SYSTEM, ra.system)
        assertEquals(ra.system, rb.system)
        assertEquals(USER, ra.user)
        assertEquals(ra.user, rb.user)
        assertEquals(MAX_TOKENS, ra.maxOutputTokens)
        assertEquals(ra.maxOutputTokens, rb.maxOutputTokens)
        assertTrue(ra.mode is BackendMode.Constrained)
        assertTrue(rb.mode is BackendMode.NativeTools)
    }

    // SchemaMap

    @Test
    fun aSchemaIsHandedOverAsPlainMapsListsAndLongOrDoubleNumbers() {
        val schema = buildJsonObject {
            put("type", "object")
            put("minimum", 1)
            put("maximum", 2.5)
            put("additionalProperties", false)
            putJsonArray("enum") { add(JsonPrimitive("a")) }
        }

        val map = jsonToMap(schema)

        assertEquals("object", map["type"])
        assertEquals(1L, map["minimum"])
        assertEquals(2.5, map["maximum"])
        assertEquals(false, map["additionalProperties"])
        assertEquals(listOf("a"), map["enum"])
    }

    @Test
    fun nestedArgumentsAreCheckedAgainstTheirOwnPropertySchemas() {
        val schema = buildJsonObject {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("items") {
                    put("type", "array")
                    putJsonObject("items") { put("type", "integer") }
                }
            }
        }

        val ok = mapToJson(mapOf("items" to listOf(1.0, 2.0)), schema)
        assertNotNull(ok["items"])
        assertEquals(JsonArray(listOf(JsonPrimitive(1), JsonPrimitive(2))), ok["items"])
        assertThrows(SchemaTypeMismatch::class.java) { mapToJson(mapOf("items" to listOf(1.5)), schema) }
        assertThrows(SchemaTypeMismatch::class.java) { mapToJson(mapOf("items" to "one"), schema) }
    }
}
