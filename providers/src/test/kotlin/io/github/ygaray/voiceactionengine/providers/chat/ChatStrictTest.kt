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

    private val keywords = listOf(
        "allOf", "oneOf", "not", "if", "then", "else", "dependentRequired", "dependentSchemas", "dependencies",
        "prefixItems", "patternProperties", "unevaluatedProperties", "unevaluatedItems", "\$dynamicRef",
        "\$dynamicAnchor",
    )

    // A closed object with one required string property; [extra] lands on the root and [nested] on the property.
    private fun flat(extra: String = "", nested: String = ""): JsonObject = schema(
        """{"type":"object","additionalProperties":false,"required":["a"],""" +
            """"properties":{"a":{"type":"string"$nested}}$extra}""",
    )

    @Test
    fun eachKeywordOutsideTheStrictSubsetMakesTheSchemaIneligibleAtTheRoot() {
        assertTrue(isChatStrictEligible(flat()))
        for (keyword in keywords) {
            assertFalse("$keyword at the root", isChatStrictEligible(flat(extra = ""","$keyword":{}""")))
        }
    }

    @Test
    fun eachKeywordOutsideTheStrictSubsetMakesTheSchemaIneligibleOnANestedProperty() {
        for (keyword in keywords) {
            assertFalse("$keyword nested", isChatStrictEligible(flat(nested = ""","$keyword":{}""")))
        }
    }

    @Test
    fun anyOfWithClosedRequiredBranchesADefsEntryAndANullableRequiredPropertyStayEligible() {
        val supported = schema(
            """
            {"type":"object","additionalProperties":false,"required":["pick","ref","note"],
             "properties":{
               "pick":{"anyOf":[{"type":"string"},{"type":"object","additionalProperties":false,
                 "required":["id"],"properties":{"id":{"type":"string"}}}]},
               "ref":{"${'$'}ref":"#/${'$'}defs/unit"},
               "note":{"type":["string","null"]}},
             "${'$'}defs":{"unit":{"type":"object","additionalProperties":false,
               "required":["u"],"properties":{"u":{"type":"string"}}}}}
            """.trimIndent(),
        )
        assertTrue(isChatStrictEligible(supported))
    }

    @Test
    fun aPropertyNamedLikeAKeywordIsNotAKeyword() {
        val named = schema(
            """
            {"type":"object","additionalProperties":false,"required":["not","if","allOf","oneOf"],
             "properties":{"not":{"type":"string"},"if":{"type":"string"},
               "allOf":{"type":"string"},"oneOf":{"type":"string"}}}
            """.trimIndent(),
        )
        assertTrue(isChatStrictEligible(named))
    }

    @Test
    fun aRootThatIsNotExactlyAnObjectIsIneligible() {
        assertFalse(isChatStrictEligible(schema("""{"type":"array","items":{"type":"string"}}""")))
        assertFalse(isChatStrictEligible(schema("""{"additionalProperties":false,"properties":{}}""")))
        val union = """{"anyOf":[{"type":"object","additionalProperties":false,"properties":{}}]}"""
        assertFalse(isChatStrictEligible(schema(union)))
    }

    @Test
    fun aNestedObjectThatIsNotClosedIsIneligible() {
        val open = schema(
            """
            {"type":"object","additionalProperties":false,"required":["inner"],"properties":{
              "inner":{"type":"object","required":["a"],"properties":{"a":{"type":"string"}}}}}
            """.trimIndent(),
        )
        assertFalse(isChatStrictEligible(open))
    }

    // An object schema with [count] required string properties named p0, p1, and so on.
    private fun withProperties(count: Int): JsonObject {
        val names = (0 until count).map { "p$it" }
        val properties = names.joinToString(",") { """"$it":{"type":"string"}""" }
        val required = names.joinToString(",") { """"$it"""" }
        return schema(
            """{"type":"object","additionalProperties":false,"required":[$required],"properties":{$properties}}""",
        )
    }

    @Test
    fun theTotalPropertyLimitIsFiveThousand() {
        assertTrue(isChatStrictEligible(withProperties(5_000)))
        assertFalse(isChatStrictEligible(withProperties(5_001)))
    }

    // [levels] nested closed objects, the innermost with no properties.
    private fun nestedObjects(levels: Int): JsonObject {
        var inner = """{"type":"object","additionalProperties":false,"properties":{}}"""
        repeat(levels - 1) {
            inner = """{"type":"object","additionalProperties":false,"required":["n"],"properties":{"n":$inner}}"""
        }
        return schema(inner)
    }

    @Test
    fun theNestingLimitIsTenObjectLevels() {
        assertTrue(isChatStrictEligible(nestedObjects(10)))
        assertFalse(isChatStrictEligible(nestedObjects(11)))
    }

    // One required property "a" with an enum of [count] short string values.
    private fun withEnum(count: Int): JsonObject {
        val values = (0 until count).joinToString(",") { "\"v$it\"" }
        return schema(
            """{"type":"object","additionalProperties":false,"required":["a"],""" +
                """"properties":{"a":{"type":"string","enum":[$values]}}}""",
        )
    }

    @Test
    fun theEnumValueLimitIsOneThousand() {
        assertTrue(isChatStrictEligible(withEnum(1_000)))
        assertFalse(isChatStrictEligible(withEnum(1_001)))
    }

    // One required property named "a" whose const string value has [length] characters: 1 + length in total.
    private fun withConst(length: Int): JsonObject {
        val value = "x".repeat(length)
        return schema(
            """{"type":"object","additionalProperties":false,"required":["a"],""" +
                """"properties":{"a":{"type":"string","const":"$value"}}}""",
        )
    }

    @Test
    fun theTextLimitCountsPropertyNamesAndConstValues() {
        assertTrue(isChatStrictEligible(withConst(119_999)))
        assertFalse(isChatStrictEligible(withConst(120_000)))
    }

    @Test
    fun theTextLimitCountsEnumStringValues() {
        // 12 values sharing [total] characters, plus the one-character name "a".
        fun enumOf(total: Int): JsonObject {
            val each = total / 12
            val values = (0 until 12).joinToString(",") { """"${"y".repeat(each)}"""" }
            return schema(
                """{"type":"object","additionalProperties":false,"required":["a"],""" +
                    """"properties":{"a":{"type":"string","enum":[$values]}}}""",
            )
        }
        assertTrue(isChatStrictEligible(enumOf(119_988)))
        assertFalse(isChatStrictEligible(enumOf(120_000)))
    }
}
