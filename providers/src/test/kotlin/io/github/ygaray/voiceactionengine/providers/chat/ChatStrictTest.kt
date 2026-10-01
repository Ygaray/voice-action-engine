package io.github.ygaray.voiceactionengine.providers.chat

import io.github.ygaray.voiceactionengine.core.strategy.ToolSpec
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** When the engine may add strict mode for Chat Completions, who decides, and what the strict copy loses. */
class ChatStrictTest {

    private fun schema(text: String): JsonObject = Json.parseToJsonElement(text).jsonObject

    // Shaped like a food-logging tool: every property required, the nullable ones included.
    private val logFood = schema(
        """
        {"type":"object","additionalProperties":false,"required":["items","target_date"],"properties":{
          "items":{"type":"array","items":{"type":"object","additionalProperties":false,
            "required":["name","quantity","unit","confidence"],"properties":{
              "name":{"type":"string"},"quantity":{"type":"number"},
              "unit":{"type":["string","null"]},"confidence":{"type":"number"}}}},
          "target_date":{"type":["string","null"]}}}
        """.trimIndent(),
    )

    // Shaped like an edit tool: a root optional and optionals inside the array items.
    private val editShaped = schema(
        """
        {"type":"object","additionalProperties":false,"required":["card_id"],"properties":{
          "card_id":{"type":"string"},"title":{"type":"string"},
          "items":{"type":"array","items":{"type":"object","additionalProperties":false,
            "required":["text"],"properties":{
              "text":{"type":"string"},"item_id":{"type":"string"},"completed_at":{"type":"string"}}}}}}
        """.trimIndent(),
    )

    // The only optional property sits in an anyOf branch.
    private val anyOfOptional = schema(
        """
        {"type":"object","additionalProperties":false,"required":["target"],"properties":{
          "target":{"anyOf":[{"type":"null"},{"type":"object","additionalProperties":false,
            "required":[],"properties":{"id":{"type":"string"}}}]}}}
        """.trimIndent(),
    )

    private fun tool(inputSchema: JsonObject, strict: Boolean?): ToolSpec =
        ToolSpec("t", "a tool", inputSchema, strict = strict)

    @Test
    fun aLogFoodShapedSchemaWithNullableRequiredPropertiesIsEligible() {
        assertTrue(isChatStrictEligible(logFood))
    }

    @Test
    fun anEditShapedSchemaWithRootAndNestedOptionalsIsNotEligible() {
        assertFalse(isChatStrictEligible(editShaped))
    }

    @Test
    fun anOptionalHiddenInAnAnyOfBranchMakesTheSchemaNotEligible() {
        assertFalse(isChatStrictEligible(anyOfOptional))
    }

    @Test
    fun anAppsStrictTrueCannotForceStrictOntoASchemaWithAnOptional() {
        assertFalse(effectiveChatStrict(tool(editShaped, strict = true)))
        assertFalse(effectiveChatStrict(tool(anyOfOptional, strict = true)))
    }

    @Test
    fun strictIsOnByDefaultForAnEligibleSchemaAndAnAppCanOptOut() {
        assertTrue(effectiveChatStrict(tool(logFood, strict = null)))
        assertTrue(effectiveChatStrict(tool(logFood, strict = true)))
        assertFalse(effectiveChatStrict(tool(logFood, strict = false)))
    }
}
