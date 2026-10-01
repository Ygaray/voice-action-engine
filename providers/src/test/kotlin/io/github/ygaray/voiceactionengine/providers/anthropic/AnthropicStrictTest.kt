package io.github.ygaray.voiceactionengine.providers.anthropic

import io.github.ygaray.voiceactionengine.core.provider.ModelResult
import io.github.ygaray.voiceactionengine.core.strategy.ToolSpec
import io.github.ygaray.voiceactionengine.core.transcript.CacheDirective
import io.github.ygaray.voiceactionengine.core.transcript.ModelRequest
import io.github.ygaray.voiceactionengine.core.transcript.ToolChoice
import io.github.ygaray.voiceactionengine.core.transcript.UserMessage
import io.github.ygaray.voiceactionengine.providers.schema.hasOptionalProperties
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
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
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** When the engine may add strict mode, and that an omitted optional parameter arrives absent. */
class AnthropicStrictTest {

    private val bothRequired = listOf("title", "notes")

    // A flat object with two string properties; [extra] adds top-level keywords to it.
    private fun flat(
        required: List<String> = listOf("title"),
        closed: Boolean? = false,
        extra: Map<String, JsonElement> = emptyMap(),
    ): JsonObject {
        val base = buildJsonObject {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("title") { put("type", "string") }
                putJsonObject("notes") { put("type", "string") }
            }
            putJsonArray("required") { required.forEach { add(it) } }
            if (closed != null) put("additionalProperties", closed)
        }
        return JsonObject(base + extra)
    }

    private fun eligible(schema: JsonObject): Boolean = isAnthropicStrictEligible(schema)

    private fun json(text: String): JsonElement = Json.parseToJsonElement(text)

    @Test
    fun theClarificationSchemaAndAFlatAllRequiredObjectAreEligible() {
        assertTrue(eligible(ToolSpec.clarification("ask").inputSchema))
        assertTrue(eligible(flat(bothRequired)))
    }

    @Test
    fun anOptionalPropertyAtTheRootOrNestedMakesTheSchemaIneligible() {
        assertFalse(eligible(flat(required = listOf("title"))))
        val nested = buildJsonObject {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("inner") {
                    put("type", "object")
                    putJsonObject("properties") { putJsonObject("a") { put("type", "string") } }
                    putJsonArray("required") { }
                    put("additionalProperties", false)
                }
            }
            putJsonArray("required") { add("inner") }
            put("additionalProperties", false)
        }
        assertFalse(eligible(nested))
    }

    @Test
    fun aMissingOrOpenAdditionalPropertiesMakesTheSchemaIneligible() {
        assertFalse(eligible(flat(bothRequired, closed = null)))
        assertFalse(eligible(flat(bothRequired, closed = true)))
    }

    private fun nestedProperty(keyword: String, value: JsonElement): JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            put("title", JsonObject(mapOf("type" to json("\"string\""), keyword to value)))
        }
        putJsonArray("required") { add("title") }
        put("additionalProperties", false)
    }

    @Test
    fun unsupportedKeywordsMakeTheSchemaIneligibleAtTheRootAndNested() {
        val offenders = mapOf(
            "\$ref" to json("\"#/\$defs/x\""),
            "anyOf" to json("[{\"type\":\"string\"}]"),
            "oneOf" to json("[{\"type\":\"string\"}]"),
            "allOf" to json("[{\"type\":\"string\"}]"),
            "definitions" to json("{}"),
            "\$defs" to json("{}"),
            "minimum" to json("0"),
            "maximum" to json("9"),
            "exclusiveMinimum" to json("0"),
            "exclusiveMaximum" to json("9"),
            "multipleOf" to json("2"),
            "minLength" to json("1"),
            "maxLength" to json("9"),
        )
        for ((keyword, value) in offenders) {
            assertFalse(keyword, eligible(flat(bothRequired, extra = mapOf(keyword to value))))
            assertFalse("$keyword nested", eligible(nestedProperty(keyword, value)))
        }
    }

    private fun withMinItems(count: Int?): JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("tags") {
                put("type", "array")
                putJsonObject("items") { put("type", "string") }
                if (count != null) put("minItems", count)
            }
        }
        putJsonArray("required") { add("tags") }
        put("additionalProperties", false)
    }

    @Test
    fun minItemsAboveOneMakesTheSchemaIneligibleAndAbsentZeroOrOneDoNot() {
        assertTrue(eligible(withMinItems(null)))
        assertTrue(eligible(withMinItems(0)))
        assertTrue(eligible(withMinItems(1)))
        assertFalse(eligible(withMinItems(2)))
    }

    @Test
    fun aRootThatIsNotAnObjectIsIneligible() {
        assertFalse(eligible(buildJsonObject { put("type", "array") }))
        assertFalse(eligible(buildJsonObject { }))
    }

    @Test
    fun anOptionalPropertyInsideArrayItemsIsFoundAndTheClarificationSchemaHasNone() {
        val schema = buildJsonObject {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("rows") {
                    put("type", "array")
                    putJsonObject("items") {
                        put("type", "object")
                        putJsonObject("properties") { putJsonObject("a") { put("type", "string") } }
                        putJsonArray("required") { }
                    }
                }
            }
            putJsonArray("required") { add("rows") }
        }

        assertTrue(hasOptionalProperties(schema))
        assertFalse(hasOptionalProperties(ToolSpec.clarification("ask").inputSchema))
    }

    private fun requestOf(vararg tools: ToolSpec): ModelRequest = ModelRequest(
        "sys",
        listOf(UserMessage("do it")),
        tools.toList(),
        ToolChoice.Required(tools.first().name),
        256,
        CacheDirective(true),
    )

    // The same decision the transport makes: the table says whether the model refuses forcing.
    private fun toolsSent(model: String, request: ModelRequest): List<JsonObject> {
        val reshape = !AnthropicModels.capabilities(model).supportsForcedToolChoice
        val bytes = encodeAnthropicRequest(anthropicRequest(model, request, "k"), reshape)
        val body = Json.parseToJsonElement(bytes.toString(Charsets.UTF_8)).jsonObject
        return body["tools"]!!.jsonArray.map { it.jsonObject }
    }

    private fun strictOf(tool: JsonObject): String? = tool["strict"]?.jsonPrimitive?.content

    @Test
    fun theReshapeMarksAnEligibleUndecidedToolAndLeavesAnOptionalOneAlone() {
        val tight = ToolSpec("a_tight", "d", flat(bothRequired))
        val loose = ToolSpec("b_loose", "d", flat(listOf("title")))

        val tools = toolsSent("claude-opus-5-5", requestOf(tight, loose))

        assertEquals("true", strictOf(tools[0]))
        assertNull(strictOf(tools[1]))
        assertEquals(loose.inputSchema, tools[1]["input_schema"])
    }

    @Test
    fun anExplicitFalseIsNeverStrictAndAnExplicitTrueIsAlwaysSent() {
        val off = ToolSpec("a_off", "d", flat(bothRequired), strict = false)
        val on = ToolSpec("b_on", "d", flat(listOf("title")), strict = true)

        val reshaped = toolsSent("claude-opus-5-5", requestOf(off, on))
        val forced = toolsSent("claude-haiku-4-5", requestOf(off, on))

        assertNull(strictOf(reshaped[0]))
        assertEquals("true", strictOf(reshaped[1]))
        assertNull(strictOf(forced[0]))
        assertEquals("true", strictOf(forced[1]))
    }

    @Test
    fun noStrictIsAddedWhenTheRequestIsNotReshaped() {
        val tight = ToolSpec("a_tight", "d", flat(bothRequired))

        val forced = toolsSent("claude-haiku-4-5", requestOf(tight))

        assertNull(strictOf(forced[0]))
    }

    @Test
    fun twentyOneEligibleUndecidedToolsGiveExactlyTwentyStrictOnes() {
        val tools = (1..21).map { ToolSpec("tool_%02d".format(it), "d", flat(bothRequired)) }

        val sent = toolsSent("claude-opus-5-5", requestOf(*tools.toTypedArray()))

        assertEquals(21, sent.size)
        assertEquals(20, sent.count { strictOf(it) == "true" })
        assertNull(strictOf(sent.last()))
    }

    @Test(timeout = 30_000)
    fun anOmittedOptionalParameterIsSentUnchangedAndArrivesAbsent() = runBlocking {
        val schema = flat(listOf("title"))
        val request = requestOf(ToolSpec("add_note", "d", schema))
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse().setResponseCode(200).setBody(
                    successBody(
                        listOf(toolUseBlock("toolu_1", "add_note", buildJsonObject { put("title", "x") })),
                        "tool_use",
                    ),
                ),
            )
            server.start()
            val provider = AnthropicProvider { baseUrl = server.url("/") }

            val result = provider.complete(anthropicRequest("claude-opus-5-5", request, "sk-test-key"))

            val sent = Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
            val sentTool = sent["tools"]!!.jsonArray.single().jsonObject
            assertEquals(schema.toString(), sentTool["input_schema"].toString())
            assertNull(strictOf(sentTool))
            val arguments = (result as ModelResult.Success).response.message.toolCalls.single().arguments
            assertEquals(setOf("title"), arguments.keys)
        }
    }
}
