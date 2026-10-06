package io.github.ygaray.voiceactionengine.spike

import io.github.ygaray.voiceactionengine.spike.verdict.SchemaResult
import io.github.ygaray.voiceactionengine.spike.verdict.SchemaSubset
import io.github.ygaray.voiceactionengine.spike.verdict.UnknownKeyword
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SchemaSubsetTest {
    private fun obj(text: String): JsonObject = Json.parseToJsonElement(text) as JsonObject

    private val titleSchema = obj(
        """{"type":"object","required":["title"],"properties":{"title":{"type":"string"}}}""",
    )

    private fun check(schema: JsonObject, value: String): SchemaResult =
        SchemaSubset.validate(schema, Json.parseToJsonElement(value))

    @Test
    fun acceptsAnObjectWithTheRequiredKey() {
        assertEquals(SchemaResult.Valid, check(titleSchema, """{"title":"a"}"""))
    }

    @Test
    fun rejectsAMissingRequiredKey() {
        assertEquals(SchemaResult.Invalid("missing_required"), check(titleSchema, """{}"""))
    }

    @Test
    fun rejectsAWrongType() {
        assertEquals(SchemaResult.Invalid("type_mismatch"), check(titleSchema, """{"title":7}"""))
        assertEquals(SchemaResult.Invalid("type_mismatch"), check(titleSchema, """["title"]"""))
    }

    @Test
    fun rejectsAValueOutsideTheEnum() {
        val schema = obj("""{"type":"object","properties":{"kind":{"type":"string","enum":["a","b"]}}}""")
        assertEquals(SchemaResult.Valid, check(schema, """{"kind":"a"}"""))
        assertEquals(SchemaResult.Invalid("not_in_enum"), check(schema, """{"kind":"c"}"""))
    }

    @Test
    fun rejectsAnExtraKeyWhenAdditionalPropertiesIsFalse() {
        val schema = obj(
            """{"type":"object","properties":{"a":{"type":"string"}},"additionalProperties":false}""",
        )
        assertEquals(SchemaResult.Valid, check(schema, """{"a":"x"}"""))
        assertEquals(SchemaResult.Invalid("extra_property"), check(schema, """{"a":"x","b":1}"""))
    }

    @Test
    fun rejectsAWrongArrayItemType() {
        val schema = obj("""{"type":"array","items":{"type":"string"}}""")
        assertEquals(SchemaResult.Valid, check(schema, """["a","b"]"""))
        assertEquals(SchemaResult.Invalid("item_mismatch"), check(schema, """["a",1]"""))
    }

    @Test
    fun numberAndIntegerAndBooleanAreDistinguished() {
        val schema = obj(
            """{"type":"object","properties":{"n":{"type":"number"},"i":{"type":"integer"},"b":{"type":"boolean"}}}""",
        )
        assertEquals(SchemaResult.Valid, check(schema, """{"n":1.5,"i":3,"b":true}"""))
        assertEquals(SchemaResult.Invalid("type_mismatch"), check(schema, """{"i":1.5}"""))
        assertEquals(SchemaResult.Invalid("type_mismatch"), check(schema, """{"b":"true"}"""))
        assertEquals(SchemaResult.Invalid("type_mismatch"), check(schema, """{"n":"1"}"""))
    }

    @Test
    fun nestedObjectsAreValidatedRecursively() {
        val schema = obj(
            """{"type":"object","properties":{"inner":{"type":"object","required":["x"],"properties":{"x":{"type":"integer"}}}}}""",
        )
        assertEquals(SchemaResult.Valid, check(schema, """{"inner":{"x":1}}"""))
        assertEquals(SchemaResult.Invalid("missing_required"), check(schema, """{"inner":{}}"""))
    }

    @Test
    fun descriptionAndTitleAreAllowedAnnotations() {
        val schema = obj("""{"type":"string","description":"d","title":"t"}""")
        assertEquals(SchemaResult.Valid, check(schema, "\"v\""))
        assertTrue(SchemaSubset.keywordsIn(schema).unknown.isEmpty())
    }

    @Test
    fun keywordsInFlagsAnyOfAsUnknown() {
        val schema = obj("""{"type":"object","properties":{"v":{"anyOf":[{"type":"string"},{"type":"integer"}]}}}""")
        val scan = SchemaSubset.keywordsIn(schema)
        assertEquals(listOf(UnknownKeyword("anyOf")), scan.unknown)
        assertTrue("properties" in scan.used)
    }

    @Test
    fun validateNeverAcceptsAnUnknownKeywordSilently() {
        val schema = obj("""{"type":"object","minProperties":1}""")
        assertEquals(UnknownKeyword("minProperties"), check(schema, """{"a":1}"""))
    }

    @Test
    fun anUnsupportedTypeNameIsUnknownToo() {
        val schema = obj("""{"type":"null"}""")
        assertTrue(check(schema, "null") is UnknownKeyword)
    }
}
