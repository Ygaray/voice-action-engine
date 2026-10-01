package io.github.ygaray.voiceactionengine.providers.chat

import io.github.ygaray.voiceactionengine.core.strategy.ToolSpec
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
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

    private val droppedKeywords = listOf(
        "uniqueItems", "minProperties", "maxProperties", "contains", "minContains", "maxContains", "propertyNames",
        "contentEncoding", "contentMediaType",
    )

    // One schema carrying every dropped keyword at the root and inside property, items, anyOf and $defs schemas.
    private fun carrying(inner: String): String = droppedKeywords.joinToString(",") { """"$it":$inner""" }

    @Test
    fun everyValidationOnlyKeywordIsDroppedAtEveryNode() {
        val loaded = schema(
            """
            {"type":"object",${carrying("1")},"properties":{
               "p":{"type":"string",${carrying("1")}},
               "list":{"type":"array","items":{"type":"object",${carrying("1")}}},
               "pick":{"anyOf":[{"type":"string",${carrying("1")}}]}},
             "${'$'}defs":{"d":{"type":"string",${carrying("1")}}}}
            """.trimIndent(),
        )
        val stripped = stripForChatStrict(loaded).toString()
        for (keyword in droppedKeywords) {
            assertFalse("$keyword survived", stripped.contains("\"$keyword\""))
        }
    }

    @Test
    fun rootSchemaAndIdAreDroppedButANestedIdIsLeftAlone() {
        val loaded = schema(
            """
            {"${'$'}schema":"x","${'$'}id":"root","type":"object","${'$'}defs":{"d":{"${'$'}id":"inner","type":"string"}}}
            """.trimIndent(),
        )
        val stripped = stripForChatStrict(loaded)
        assertFalse(stripped.containsKey("\$schema"))
        assertFalse(stripped.containsKey("\$id"))
        assertEquals(schema("""{"type":"object","${'$'}defs":{"d":{"${'$'}id":"inner","type":"string"}}}"""), stripped)
    }

    @Test
    fun onlyTheNineSupportedFormatsSurvive() {
        val kept = listOf("date", "date-time", "time", "duration", "email", "hostname", "ipv4", "ipv6", "uuid")
        for (name in kept) {
            val node = schema("""{"type":"string","format":"$name"}""")
            assertEquals("$name kept", node, stripForChatStrict(node))
        }
        for (name in listOf("uri", "regex")) {
            val stripped = stripForChatStrict(schema("""{"type":"string","format":"$name"}"""))
            assertEquals("$name dropped", schema("""{"type":"string"}"""), stripped)
        }
        val numeric = stripForChatStrict(schema("""{"type":"string","format":3}"""))
        assertEquals(schema("""{"type":"string"}"""), numeric)
    }

    @Test
    fun everyOtherKeywordSurvivesUntouched() {
        val kept = schema(
            """
            {"type":"object","description":"d","additionalProperties":false,"required":["s","n","l","u"],
             "properties":{
               "s":{"type":"string","minLength":1,"maxLength":9,"pattern":"^a","default":"x","enum":["x","y"],"const":"x"},
               "n":{"type":"number","minimum":0,"maximum":9,"exclusiveMinimum":0,"exclusiveMaximum":9,"multipleOf":2},
               "l":{"type":"array","minItems":1,"maxItems":3,"items":{"${'$'}ref":"#/${'$'}defs/u"}},
               "u":{"anyOf":[{"type":"string"},{"type":"null"}]}},
             "${'$'}defs":{"u":{"type":"string"}}}
            """.trimIndent(),
        )
        assertEquals(kept, stripForChatStrict(kept))
    }

    @Test
    fun propertiesNamedLikeKeywordsKeepTheirNamesAndSchemas() {
        val named = schema(
            """
            {"type":"object","required":["format","pattern","contains","uniqueItems"],"properties":{
              "format":{"type":"string","format":"uri"},"pattern":{"type":"string","pattern":"^a"},
              "contains":{"type":"string","uniqueItems":true},"uniqueItems":{"type":"boolean"}}}
            """.trimIndent(),
        )
        val properties = stripForChatStrict(named)["properties"] as JsonObject
        assertEquals(setOf("format", "pattern", "contains", "uniqueItems"), properties.keys)
        assertEquals(schema("""{"type":"string"}"""), properties["format"])
        assertEquals(schema("""{"type":"string"}"""), properties["contains"])
        assertEquals(schema("""{"type":"string","pattern":"^a"}"""), properties["pattern"])
    }

    @Test
    fun enumConstAndDefaultValuesAreKeptByteForByte() {
        val values = schema(
            """
            {"type":"object","properties":{
              "a":{"type":"string","enum":["uniqueItems","format"],"const":"format"},
              "b":{"type":"object","default":{"contains":1,"format":"uri"}}}}
            """.trimIndent(),
        )
        assertEquals(values, stripForChatStrict(values))
    }

    @Test
    fun theInputIsUnchangedAndKeyOrderOfKeptKeysIsPreserved() {
        val input = schema(
            """{"type":"object","uniqueItems":true,"description":"d","properties":{"z":{"type":"string"},""" +
                """"a":{"type":"string","contains":1}},"required":["z","a"]}""",
        )
        val before = schema(input.toString())
        val stripped = stripForChatStrict(input)
        assertEquals(before, input)
        assertEquals(listOf("type", "description", "properties", "required"), stripped.keys.toList())
        assertEquals(listOf("z", "a"), (stripped["properties"] as JsonObject).keys.toList())
    }

    @Test
    fun strippingIsIdempotentAndASchemaWithNothingToStripComesBackEqual() {
        val loaded = schema(
            """{"type":"object","${'$'}schema":"x",""" +
                """"properties":{"a":{"type":"string","format":"uri","uniqueItems":1}}}""",
        )
        val once = stripForChatStrict(loaded)
        assertEquals(once, stripForChatStrict(once))
        assertEquals(logFood, stripForChatStrict(logFood))
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
