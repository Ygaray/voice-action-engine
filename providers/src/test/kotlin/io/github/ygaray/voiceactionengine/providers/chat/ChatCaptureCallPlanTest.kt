package io.github.ygaray.voiceactionengine.providers.chat

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

private const val CALL_COUNT = 11
private const val OPENAI_CALLS = 5
private const val ROUTED_CALLS = 6
private const val MIN_LONG_SYSTEM = 6_000

/**
 * Key-free checks of the live capture's call plan: the plan stays bounded and on the three allowed models, and every
 * planned request encodes with the production encoder, so a mistake in the plan cannot first show up under a live key.
 */
class ChatCaptureCallPlanTest {

    private fun call(code: String): PlannedCall = CapturePlan.all.single { it.code == code }

    private fun body(planned: PlannedCall): JsonObject {
        val bytes = encodeChatRequest(chatCall(planned.vendor, planned.model, planned.request), planned.vendor)
        return Json.parseToJsonElement(bytes.toString(Charsets.UTF_8)) as JsonObject
    }

    private fun bytes(code: String): String {
        val planned = call(code)
        return encodeChatRequest(chatCall(planned.vendor, planned.model, planned.request), planned.vendor)
            .toString(Charsets.UTF_8)
    }

    @Test
    fun thePlanIsElevenCallsWithinTheCeilings() {
        assertEquals(CALL_COUNT, CapturePlan.all.size)
        assertEquals(OPENAI_CALLS, CapturePlan.all.count { it.vendorName == VENDOR_OPENAI })
        assertEquals(ROUTED_CALLS, CapturePlan.all.count { it.vendorName == VENDOR_OPENROUTER })
        assertEquals(emptyList<String>(), CapturePlan.violations(CapturePlan.all))
    }

    @Test
    fun anOverfullOrDuplicatedPlanIsReported() {
        val doubled = CapturePlan.all + CapturePlan.all
        val messages = CapturePlan.violations(doubled)
        assertTrue(messages.toString(), messages.any { it.contains("not unique") })
        assertTrue(messages.toString(), messages.any { it.contains("over $MAX_HTTP_REQUESTS") })
        assertTrue(messages.toString(), messages.any { it.contains("over $MAX_REQUESTS_PER_VENDOR") })
    }

    @Test
    fun onlyTheThreeCaptureModelsAreEverCalled() {
        val allowed = setOf(OPENAI_CAPTURE_MODEL, ROUTED_CAPTURE_MODEL, HAIKU_CAPTURE_MODEL)
        assertEquals(allowed, CapturePlan.all.map { it.model }.toSet())
        assertTrue(CapturePlan.all.filter { it.vendorName == VENDOR_OPENAI }.all { it.model == OPENAI_CAPTURE_MODEL })
    }

    @Test
    fun theLabelFilterSelectsOrRejects() {
        assertEquals(CALL_COUNT, CapturePlan.selected(null).size)
        assertEquals(CALL_COUNT, CapturePlan.selected(" ").size)
        assertEquals(listOf("C5", "R4"), CapturePlan.selected("r4, c5").map { it.code })
        assertThrows(IllegalArgumentException::class.java) { CapturePlan.selected("C1,X9") }
    }

    @Test
    fun everyPlannedRequestEncodesWithItsModelAndTools() {
        CapturePlan.all.forEach { planned ->
            val encoded = body(planned)
            assertEquals(planned.code, planned.model, (encoded["model"] as JsonPrimitive).content)
            assertTrue(planned.code, encoded.containsKey("tools"))
        }
    }

    @Test
    fun theRepeatedCallsSendTheSameBytesAndTheLogFoodSystemIsLong() {
        assertEquals(bytes("C1"), bytes("C2"))
        assertEquals(bytes("R1"), bytes("R3"))
        val system = call("C1").request.system
        assertTrue("system text is ${system.length} characters", system.length >= MIN_LONG_SYSTEM)
        assertTrue(system.startsWith(FIXED_SYSTEM))
    }

    @Test
    fun theParallelProbeDiffersFromR1OnlyByTheParallelToolCallsFlag() {
        val first = body(call("R1"))
        val probe = body(call("R5"))
        assertFalse(first.containsKey("parallel_tool_calls"))
        assertFalse((probe["parallel_tool_calls"] as JsonPrimitive).boolean)
        assertEquals(first.keys + "parallel_tool_calls", probe.keys)
        assertNotEquals(bytes("R1"), bytes("R5"))
    }

    @Test
    fun theEditAndHaikuCallsSendNoStrictFlagAndTheOpenAiLogFoodCallDoes() {
        assertFalse(bytes("C3").contains("\"strict\""))
        assertFalse(bytes("R2").contains("\"strict\""))
        assertFalse(bytes("R6").contains("\"strict\""))
        assertTrue(bytes("C1").contains("\"strict\":true"))
    }

    @Test
    fun theInvalidKeyProbesAreTheOnlyOnesWithoutARealKey() {
        assertEquals(listOf("C5", "R4"), CapturePlan.all.filter { it.invalidKey }.map { it.code })
        assertTrue(CapturePlan.all.filter { it.invalidKey }.all { it.intended?.expected == "failure:auth" })
    }
}
