package io.github.ygaray.voiceactionengine.core

import io.github.ygaray.voiceactionengine.core.strategy.ToolSpec
import io.github.ygaray.voiceactionengine.core.strategy.plan.PlanVerdict
import io.github.ygaray.voiceactionengine.core.strategy.plan.parsePlan
import io.github.ygaray.voiceactionengine.core.transcript.AssistantPart
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Whole-plan validation: one documented order, every code a fixed token, the lookup verdict of its own. */
class PlanParseTest {
    private val snapshot = planSnapshot(createTool(), tagTool(), findTool(), askTool())

    private fun askTool(): ToolSpec =
        ToolSpec("ask_user", "Asks the user.", buildJsonObject { put("type", "object") }, terminal = true)

    private fun call(arguments: JsonObject, name: String = "submit_plan") =
        AssistantPart.ToolCall("c1", name, arguments)

    private fun parse(arguments: JsonObject, maxSteps: Int = 8): PlanVerdict =
        parsePlan(listOf(call(arguments)), snapshot, maxSteps)

    private fun create(id: String = "s1") = planStep(id, PLAN_CREATE_TOOL, buildJsonObject { put("name", "a") })

    private fun tagOf(id: String, reference: String) =
        planStep(id, PLAN_TAG_TOOL, buildJsonObject { put("item_id", reference) })

    private fun assertRejected(code: String, index: Int?, verdict: PlanVerdict) {
        assertTrue(verdict.toString(), verdict is PlanVerdict.Rejected)
        verdict as PlanVerdict.Rejected
        assertEquals(code, verdict.code)
        assertEquals(index, verdict.stepIndex)
    }

    @Test
    fun aValidTwoStepPlanWithAReferenceKeepsBothStepsInOrder() {
        val verdict = parse(planArguments(create(), tagOf("s2", "\$s1.item_id")))
        assertTrue(verdict.toString(), verdict is PlanVerdict.Valid)
        verdict as PlanVerdict.Valid
        assertEquals(listOf("s1", "s2"), verdict.plan.steps.map { it.id })
        assertEquals(listOf(PLAN_CREATE_TOOL, PLAN_TAG_TOOL), verdict.plan.steps.map { it.tool })
    }

    @Test
    fun aDirectAppToolCallIsAMalformedPlanNeverDispatched() {
        val direct = call(buildJsonObject { put("name", "a") }, PLAN_CREATE_TOOL)
        assertRejected("malformed", null, parsePlan(listOf(direct), snapshot, 8))
    }

    @Test
    fun extraCallsAfterAValidSubmitPlanDoNotRejectThePlan() {
        val calls = listOf(call(planArguments(create())), call(buildJsonObject {}, PLAN_TAG_TOOL))
        assertTrue(parsePlan(calls, snapshot, 8) is PlanVerdict.Valid)
    }

    @Test
    fun aNeedsLookupThatIsNotABooleanOrAMissingStepsArrayIsMalformed() {
        val notBoolean = buildJsonObject {
            put("needs_lookup", "yes")
            put("steps", JsonArray(emptyList()))
        }
        assertRejected("malformed", null, parse(notBoolean))
        assertRejected("malformed", null, parse(buildJsonObject { put("needs_lookup", false) }))
        assertRejected("malformed", null, parse(buildJsonObject {}))
        val notAnArray = buildJsonObject { put("steps", "none") }
        assertRejected("malformed", null, parse(notAnArray))
    }

    @Test
    fun needsLookupTrueIsALookupWhateverTheStepsHold() {
        assertEquals(PlanVerdict.NeedsLookup, parse(planArguments(needsLookup = true)))
        assertEquals(PlanVerdict.NeedsLookup, parse(planArguments(create(), needsLookup = true)))
        val noSteps = buildJsonObject { put("needs_lookup", true) }
        assertEquals(PlanVerdict.NeedsLookup, parse(noSteps))
        val garbage = buildJsonObject {
            put("needs_lookup", true)
            put("steps", buildJsonArray { add(JsonPrimitive(5)) })
        }
        assertEquals(PlanVerdict.NeedsLookup, parse(garbage))
    }

    @Test
    fun theSizeCapIsCheckedBeforeAnyStepCheck() {
        val steps = (1..9).map { planStep("s$it", "nope", buildJsonObject {}) }
        assertRejected("too_many_steps", null, parse(planArguments(*steps.toTypedArray())))
        assertTrue(parse(planArguments(create()), 1) is PlanVerdict.Valid)
    }

    @Test
    fun aMalformedStepIsRejectedWithItsIndex() {
        val notObject = buildJsonObject { put("steps", buildJsonArray { add(JsonPrimitive("x")) }) }
        assertRejected("malformed", 0, parse(notObject))
        val noArguments = buildJsonObject {
            put("id", "s2")
            put("tool", PLAN_CREATE_TOOL)
        }
        assertRejected("malformed", 1, parse(planArguments(create(), noArguments)))
        val numericId = buildJsonObject {
            put("id", 2)
            put("tool", PLAN_CREATE_TOOL)
            put("arguments", buildJsonObject {})
        }
        assertRejected("malformed", 1, parse(planArguments(create(), numericId)))
        val arrayArguments = buildJsonObject {
            put("id", "s2")
            put("tool", PLAN_CREATE_TOOL)
            put("arguments", JsonArray(emptyList()))
        }
        assertRejected("malformed", 1, parse(planArguments(create(), arrayArguments)))
    }

    @Test
    fun aReadToolStepIsALookupEvenWhenAnotherStepIsInvalid() {
        val find = planStep("s2", PLAN_FIND_TOOL, buildJsonObject { put("name", "a") })
        assertEquals(PlanVerdict.NeedsLookup, parse(planArguments(create(), find)))
        val invalidFirst = planStep("1a", PLAN_CREATE_TOOL, buildJsonObject {})
        assertEquals(PlanVerdict.NeedsLookup, parse(planArguments(invalidFirst, find)))
    }

    @Test
    fun emptyStepsWithoutALookupIsRejected() {
        assertRejected("empty", null, parse(planArguments()))
        assertRejected("empty", null, parse(planArguments(needsLookup = false)))
    }

    @Test
    fun perStepChecksRunInTheDocumentedOrder() {
        assertRejected("unknown_tool", 1, parse(planArguments(create(), planStep("s2", "nope", buildJsonObject {}))))
        val terminal = planStep("s2", "ask_user", buildJsonObject {})
        assertRejected("terminal_tool", 1, parse(planArguments(create(), terminal)))
        assertRejected("bad_id", 1, parse(planArguments(create(), create("1a"))))
        assertRejected("duplicate_id", 1, parse(planArguments(create(), create("s1"))))
        // an unknown tool wins over a bad id on the same step
        assertRejected("unknown_tool", 0, parse(planArguments(planStep("1a", "nope", buildJsonObject {}))))
        // a bad id wins over a duplicate and a bad reference
        assertRejected("bad_id", 1, parse(planArguments(create(), tagOf("1a", "\$s9.x"))))
    }

    @Test
    fun aStepIdLongerThanSixtyFourCharactersIsABadId() {
        val atCap = "s" + "a".repeat(63)
        val overCap = "s" + "a".repeat(64)
        assertEquals(64, atCap.length)
        assertEquals(65, overCap.length)
        assertTrue(parse(planArguments(create(atCap))) is PlanVerdict.Valid)
        assertRejected("bad_id", 0, parse(planArguments(create(overCap))))
        assertRejected("bad_id", 1, parse(planArguments(create(), create(overCap))))
    }

    @Test
    fun aReferenceToAnUndeclaredLaterOrOwnStepIsRejected() {
        assertRejected("bad_reference", 0, parse(planArguments(tagOf("s1", "\$s9.item_id"))))
        assertRejected("bad_reference", 0, parse(planArguments(tagOf("s1", "\$s1.item_id"))))
        assertRejected("bad_reference", 0, parse(planArguments(tagOf("s1", "\$s2.item_id"), create("s2"))))
        assertNull((parse(planArguments(create(), tagOf("s2", "\$s1.item_id"))) as? PlanVerdict.Rejected))
    }

    @Test
    fun aLiteralThatLooksLikeAnAmountIsNotAReference() {
        assertTrue(parse(planArguments(tagOf("s1", "\$5.00"))) is PlanVerdict.Valid)
    }
}
