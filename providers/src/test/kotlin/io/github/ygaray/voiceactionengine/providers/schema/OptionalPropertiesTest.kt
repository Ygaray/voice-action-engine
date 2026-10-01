package io.github.ygaray.voiceactionengine.providers.schema

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The shared detector finds an optional property in every position a schema can hold a sub-schema. */
class OptionalPropertiesTest {

    private val defs = "\$defs"

    private fun schema(text: String): JsonObject = Json.parseToJsonElement(text).jsonObject

    // A one-property object whose property is optional (not named in required).
    private val optionalObject = """{"type":"object","properties":{"a":{"type":"string"}},"required":[]}"""

    // The same object with its property required.
    private val requiredObject = """{"type":"object","properties":{"a":{"type":"string"}},"required":["a"]}"""

    // A root with no properties of its own that holds [sub] under [keyword].
    private fun holding(keyword: String, sub: String): JsonObject = schema("""{"type":"object","$keyword":$sub}""")

    @Test
    fun anOptionalInsideAnAnyOfBranchIsFoundAndRequiringItClearsIt() {
        val hidden = """{"type":"object","properties":{"id":{"type":"string"}},"required":["id"],""" +
            """"anyOf":[{"type":"null"},$optionalObject]}"""
        assertTrue(hasOptionalProperties(schema(hidden)))
        val fixed = """{"type":"object","properties":{"id":{"type":"string"}},"required":["id"],""" +
            """"anyOf":[{"type":"null"},$requiredObject]}"""
        assertFalse(hasOptionalProperties(schema(fixed)))
    }

    @Test
    fun anOptionalInsideAOneOfEntryIsFound() {
        assertTrue(hasOptionalProperties(holding("oneOf", "[$optionalObject]")))
        assertFalse(hasOptionalProperties(holding("oneOf", "[$requiredObject]")))
    }

    @Test
    fun anOptionalInsideAnAllOfEntryIsFound() {
        assertTrue(hasOptionalProperties(holding("allOf", "[$optionalObject]")))
        assertFalse(hasOptionalProperties(holding("allOf", "[$requiredObject]")))
    }

    @Test
    fun anOptionalInsideADefsValueIsFound() {
        assertTrue(hasOptionalProperties(holding(defs, """{"x":$optionalObject}""")))
        assertFalse(hasOptionalProperties(holding(defs, """{"x":$requiredObject}""")))
    }

    @Test
    fun anOptionalInsideADefinitionsValueIsFound() {
        assertTrue(hasOptionalProperties(holding("definitions", """{"x":$optionalObject}""")))
        assertFalse(hasOptionalProperties(holding("definitions", """{"x":$requiredObject}""")))
    }

    @Test
    fun aDefinitionNothingReferencesIsStillWalked() {
        val unreferenced = """{"type":"object","properties":{"a":{"type":"string"}},"required":["a"],""" +
            """"$defs":{"unused":$optionalObject}}"""
        assertTrue(hasOptionalProperties(schema(unreferenced)))
    }

    @Test
    fun anOptionalInsideASchemaValuedAdditionalPropertiesIsFound() {
        assertTrue(hasOptionalProperties(holding("additionalProperties", optionalObject)))
        assertFalse(hasOptionalProperties(holding("additionalProperties", requiredObject)))
    }

    @Test
    fun anOptionalInsideAPrefixItemsEntryIsFound() {
        assertTrue(hasOptionalProperties(holding("prefixItems", "[$optionalObject]")))
        assertFalse(hasOptionalProperties(holding("prefixItems", "[$requiredObject]")))
    }

    @Test
    fun anOptionalInsideAnArrayFormItemsEntryIsFound() {
        assertTrue(hasOptionalProperties(holding("items", "[$optionalObject]")))
        assertFalse(hasOptionalProperties(holding("items", "[$requiredObject]")))
    }

    @Test
    fun anOptionalInsideObjectFormItemsIsStillFound() {
        assertTrue(hasOptionalProperties(holding("items", optionalObject)))
        assertFalse(hasOptionalProperties(holding("items", requiredObject)))
    }

    @Test
    fun anOptionalThreeLevelsDownThroughAnyOfIsFound() {
        val deep = holding(
            "anyOf",
            """[{"type":"object","anyOf":[{"type":"object","anyOf":[$optionalObject]}]}]""",
        )
        assertTrue(hasOptionalProperties(deep))
    }

    @Test
    fun aRequiredPropertyWithANullableTypeIsNotOptional() {
        val nullableType = """{"type":"object","properties":{"unit":{"type":["string","null"]}},"required":["unit"]}"""
        assertFalse(hasOptionalProperties(schema(nullableType)))
    }

    @Test
    fun aRequiredPropertyWhoseAnyOfHasANullBranchIsNotOptional() {
        val nullableUnion = """{"type":"object","properties":{"unit":""" +
            """{"anyOf":[{"type":"string"},{"type":"null"}]}},"required":["unit"]}"""
        assertFalse(hasOptionalProperties(schema(nullableUnion)))
    }

    @Test
    fun aBooleanAdditionalPropertiesIsNotWalkedAndDoesNotThrow() {
        assertFalse(hasOptionalProperties(holding("additionalProperties", "false")))
        assertFalse(hasOptionalProperties(holding("additionalProperties", "true")))
    }

    @Test
    fun aNonObjectEntryInASchemaListOrMapIsSkipped() {
        assertFalse(hasOptionalProperties(holding("anyOf", """[true,"x",null,1]""")))
        assertFalse(hasOptionalProperties(holding(defs, """{"x":true,"y":"z"}""")))
        val boolProperty = """{"type":"object","properties":{"x":true},"required":["x"]}"""
        assertFalse(hasOptionalProperties(schema(boolProperty)))
    }
}
