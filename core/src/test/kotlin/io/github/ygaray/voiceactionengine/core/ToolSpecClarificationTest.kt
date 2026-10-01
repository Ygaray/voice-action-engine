package io.github.ygaray.voiceactionengine.core

import io.github.ygaray.voiceactionengine.core.strategy.TerminalCall
import io.github.ygaray.voiceactionengine.core.strategy.ToolSpec
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ToolSpecClarificationTest {

    private fun option(id: String, label: String): JsonObject = buildJsonObject {
        put("id", id)
        put("label", label)
    }

    private fun call(question: String = "Which one?", options: List<JsonObject> = listOf(option("a", "A"))) =
        TerminalCall(
            CLARIFY,
            buildJsonObject {
                put("question", question)
                put("options", JsonArray(options))
            },
        )

    @Test
    fun terminalAndMutatingTogetherFailAtConstruction() {
        try {
            ToolSpec("x", "d", JsonObject(emptyMap()), mutating = true, terminal = true)
            fail("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message.orEmpty().contains("non-mutating"))
        }
    }

    @Test
    fun terminalAndMutatingTogetherStillFailWhenStrictIsSet() {
        try {
            ToolSpec("x", "d", JsonObject(emptyMap()), mutating = true, terminal = true, strict = true)
            fail("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message.orEmpty().contains("non-mutating"))
        }
    }

    @Test
    fun strictDefaultsToNullAndKeepsAnExplicitValue() {
        assertNull(ToolSpec("t", "d", JsonObject(emptyMap())).strict)
        assertEquals(true, ToolSpec("t", "d", JsonObject(emptyMap()), strict = true).strict)
        assertEquals(false, ToolSpec("t", "d", JsonObject(emptyMap()), strict = false).strict)
    }

    @Test
    fun mutatingAloneAndTerminalAloneConstruct() {
        val mutating = ToolSpec("w", "d", JsonObject(emptyMap()), mutating = true)
        val terminal = ToolSpec("t", "d", JsonObject(emptyMap()), terminal = true)
        assertTrue(mutating.mutating && !mutating.terminal)
        assertTrue(terminal.terminal && !terminal.mutating)
        assertFalse(ToolSpec("r", "d", JsonObject(emptyMap())).let { it.mutating || it.terminal })
    }

    @Test
    fun blankNameIsRejected() {
        try {
            ToolSpec(" ", "d", JsonObject(emptyMap()))
            fail("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertNotNull(e.message)
        }
    }

    @Test
    fun clarificationBuilderIsTerminalNonMutatingWithGoldenSchema() {
        val spec = ToolSpec.clarification(CLARIFY)
        assertEquals(CLARIFY, spec.name)
        assertTrue(spec.terminal)
        assertFalse(spec.mutating)
        assertEquals(GOLDEN_SCHEMA, spec.inputSchema.toString())
        assertEquals(DEFAULT_DESCRIPTION, spec.description)
    }

    @Test
    fun clarificationBuilderTakesACustomDescription() {
        val spec = ToolSpec.clarification(CLARIFY, "pick one")
        assertEquals("pick one", spec.description)
        assertEquals(GOLDEN_SCHEMA, spec.inputSchema.toString())
        assertTrue(spec.terminal && !spec.mutating)
    }

    @Test
    fun conformingCallRoundTripsInOrder() {
        val options = listOf(option("l1", "Groceries"), option("l2", "Work"))
        val clarification = call("Which list?", options).asClarification()
        assertNotNull(clarification)
        assertEquals("Which list?", clarification!!.question)
        assertEquals(listOf("l1", "l2"), clarification.options.map { it.id })
        assertEquals(listOf("Groceries", "Work"), clarification.options.map { it.label })
    }

    @Test
    fun emptyOptionsAreAllowed() {
        val clarification = call(options = emptyList()).asClarification()
        assertNotNull(clarification)
        assertTrue(clarification!!.options.isEmpty())
    }

    @Test
    fun extraKeysAreIgnored() {
        val extra = buildJsonObject {
            put("id", "a")
            put("label", "A")
            put("note", "ignored")
        }
        val args = buildJsonObject {
            put("question", "q")
            put("options", JsonArray(listOf(extra)))
            put("extra", true)
        }
        assertNotNull(TerminalCall(CLARIFY, args).asClarification())
    }

    @Test
    fun nonConformingShapesReturnNullAndNeverThrow() {
        val shapes = mapOf(
            "missing question" to buildJsonObject { putJsonArray("options") {} },
            "non-string question" to buildJsonObject {
                put("question", 1)
                putJsonArray("options") {}
            },
            "missing options" to buildJsonObject { put("question", "q") },
            "options not an array" to buildJsonObject {
                put("question", "q")
                put("options", "nope")
            },
            "numeric id" to optionShape(buildJsonObject {
                put("id", 1)
                put("label", "A")
            }),
            "missing label" to optionShape(buildJsonObject { put("id", "a") }),
            "option not an object" to buildJsonObject {
                put("question", "q")
                put("options", buildJsonArray { add(JsonPrimitive("a")) })
            },
            "null question" to buildJsonObject {
                put("question", JsonPrimitive(null as String?))
                putJsonArray("options") {}
            },
        )
        for ((name, args) in shapes) {
            assertNull(name, TerminalCall(CLARIFY, args).asClarification())
        }
    }

    private fun optionShape(option: JsonObject): JsonObject = buildJsonObject {
        put("question", "q")
        put("options", JsonArray(listOf(option)))
    }

    @Test
    fun toStringNeverContainsContent() {
        val canary = "CANARY-SECRET"
        val tc = call(question = "q $canary", options = listOf(option("id-$canary", "label-$canary")))
        val clarification = tc.asClarification()!!
        val rendered = listOf(tc.toString(), clarification.toString(), clarification.options.single().toString())
        for (text in rendered) {
            assertFalse(text, text.contains(canary))
        }
        assertEquals("TerminalCall(toolName=$CLARIFY, argumentCount=2)", tc.toString())
        assertTrue(clarification.toString().startsWith("Clarification(questionLength="))
        assertTrue(clarification.options.single().toString().startsWith("ClarificationOption(idLength="))
    }

    @Test
    fun toolSpecToStringNeverPrintsSchemaOrDescription() {
        val spec = ToolSpec.clarification(CLARIFY, "desc-CANARY")
        assertEquals("ToolSpec(name=$CLARIFY, mutating=false, terminal=true, strict=null)", spec.toString())
    }

    private companion object {
        const val CLARIFY = "ask_clarification"
        const val DEFAULT_DESCRIPTION = "Ask the user to choose one of the listed options instead of guessing."
        const val GOLDEN_SCHEMA =
            """{"type":"object","properties":{"question":{"type":"string"},"options":{"type":"array","items":""" +
                """{"type":"object","properties":{"id":{"type":"string"},"label":{"type":"string"}},""" +
                """"required":["id","label"],"additionalProperties":false}}},"required":["question","options"],""" +
                """"additionalProperties":false}"""
    }
}
