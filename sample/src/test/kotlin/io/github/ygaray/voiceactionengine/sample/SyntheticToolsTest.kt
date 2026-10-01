package io.github.ygaray.voiceactionengine.sample

import io.github.ygaray.voiceactionengine.core.strategy.ToolSpec
import io.github.ygaray.voiceactionengine.sample.tools.SyntheticTools
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The committed synthetic tool set: shapes the smokes and the docs rely on. */
class SyntheticToolsTest {

    private fun required(spec: ToolSpec): Set<String> =
        spec.inputSchema.getValue("required").jsonArray.map { it.jsonPrimitive.content }.toSet()

    private fun properties(spec: ToolSpec): Set<String> = spec.inputSchema.getValue("properties").jsonObject.keys

    private fun optional(spec: ToolSpec): Set<String> = properties(spec) - required(spec)

    @Test
    fun theEditToolHasOneRequiredAndThreeOptionalFields() {
        val spec = SyntheticTools.editItem
        assertEquals(setOf("id"), required(spec))
        assertEquals(setOf("title", "body", "tags"), optional(spec))
        assertFalse(spec.inputSchema.getValue("additionalProperties").jsonPrimitive.boolean)
        assertNull(spec.strict)
        assertTrue(spec.mutating)
    }

    @Test
    fun theCreateToolRequiresOnlyTheTitle() {
        val spec = SyntheticTools.createItem
        assertEquals(setOf("title"), required(spec))
        assertEquals(setOf("body", "tags"), optional(spec))
        assertTrue(spec.mutating)
    }

    @Test
    fun theReadToolRequiresOnlyTheQuery() {
        val spec = SyntheticTools.findItems
        assertEquals(setOf("query"), required(spec))
        assertTrue(optional(spec).isEmpty())
        assertFalse(spec.mutating)
    }

    @Test
    fun theClarificationToolIsTerminalAndReadOnly() {
        val spec = SyntheticTools.askUser
        assertEquals("ask_user", spec.name)
        assertTrue(spec.terminal)
        assertFalse(spec.mutating)
    }

    @Test
    fun noSpecIsStrictAndEverySchemaIsAClosedObject() {
        SyntheticTools.all.forEach { spec ->
            assertNull(spec.name, spec.strict)
            val schema: JsonObject = spec.inputSchema
            assertEquals(spec.name, JsonPrimitive("object"), schema.getValue("type"))
            assertFalse(spec.name, schema.getValue("additionalProperties").jsonPrimitive.boolean)
            assertTrue(spec.name, schema.getValue("required") is JsonArray)
        }
    }

    @Test
    fun aSnapshotCanForceOneTool() {
        val forced = SyntheticTools.snapshot("edit_item")
        val open = SyntheticTools.snapshot(null)
        assertEquals("edit_item", forced.singleShotTool)
        assertNull(open.singleShotTool)
        val order = listOf("find_items", "create_item", "edit_item", "ask_user")
        assertEquals(order, forced.tools.map { it.name })
        assertEquals(order, open.tools.map { it.name })
        assertEquals(forced.system, open.system)
    }
}
