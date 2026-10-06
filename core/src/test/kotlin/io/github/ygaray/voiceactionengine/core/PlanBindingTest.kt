package io.github.ygaray.voiceactionengine.core

import io.github.ygaray.voiceactionengine.core.commit.ActionKind
import io.github.ygaray.voiceactionengine.core.commit.ExecutedAction
import io.github.ygaray.voiceactionengine.core.strategy.plan.bindArguments
import io.github.ygaray.voiceactionengine.core.strategy.plan.isStepId
import io.github.ygaray.voiceactionengine.core.strategy.plan.mergeTargets
import io.github.ygaray.voiceactionengine.core.strategy.plan.referencedStepIds
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The reference grammar, whole-value binding and committed-target merge of the plan tier. */
class PlanBindingTest {

    private fun action(targets: Map<String, String>): ExecutedAction =
        ExecutedAction(0, ActionKind.COMMITTED, true, "tok", "create_item", targets, null, null)

    private fun refsOf(text: String): List<String> = referencedStepIds(buildJsonObject { put("k", text) })

    @Test
    fun stepIdsStartWithALetterAndUseLettersDigitsUnderscoreAndDash() {
        listOf("s1", "a", "step_2", "a-b").forEach { assertTrue(it, isStepId(it)) }
        listOf("", "1s", "_s", "s 1", "s.1", "\$s", "s\$").forEach { assertFalse(it, isStepId(it)) }
    }

    @Test
    fun aWholeValueReferenceNamesTheStepAndKeepsTheRestAsTheKey() {
        assertEquals(listOf("s1"), refsOf("\$s1.item_id"))
        assertEquals(listOf("a-b_2"), refsOf("\$a-b_2.x.y"))
    }

    @Test
    fun everyLiteralFormIsNeverAReference() {
        listOf(
            "\$5.00", "\$", "\$s1", "\$s1.", "\$1a.b", "s1.item_id", "price \$s1.item_id", " \$s1.item_id",
            "\$s1.item_id ", "\$s1.item id", "\$\$s1.x",
        ).forEach { assertEquals(it, emptyList<String>(), refsOf(it)) }
        val others = buildJsonObject {
            put("n", 5)
            put("b", true)
            put("z", JsonNull)
        }
        assertEquals(emptyList<String>(), referencedStepIds(others))
    }

    @Test
    fun referencesAreFoundDepthFirstInDocumentOrderWithDuplicatesAndKeysAreNeverRead() {
        val arguments = buildJsonObject {
            put("a", "\$s1.x")
            putJsonArray("list") {
                add(JsonPrimitive("\$s2.y"))
                add(buildJsonObject { put("deep", "\$s1.z") })
            }
            putJsonObject("nest") { put("\$s9.k", "plain") }
            put("last", "\$s3.w")
        }
        assertEquals(listOf("s1", "s2", "s1", "s3"), referencedStepIds(arguments))
    }

    @Test
    fun bindingReplacesWholeValueReferencesAndKeepsKeyOrder() {
        val arguments = buildJsonObject {
            put("item_id", "\$s1.item_id")
            put("tag", "red")
        }
        val bound = bindArguments(arguments, mapOf("s1" to mapOf("item_id" to "id-1")))
        assertEquals(buildJsonObject {
            put("item_id", "id-1")
            put("tag", "red")
        }, bound)
        assertEquals(listOf("item_id", "tag"), bound!!.keys.toList())
    }

    @Test
    fun bindingReachesNestedArraysAndObjects() {
        val arguments = buildJsonObject {
            putJsonArray("ids") {
                add(JsonPrimitive("\$s1.item_id"))
                add(buildJsonArray { add(JsonPrimitive("\$s1.item_id")) })
            }
            putJsonObject("inner") { put("ref", "\$s1.item_id") }
        }
        val bound = bindArguments(arguments, mapOf("s1" to mapOf("item_id" to "id-1")))
        val expected = buildJsonObject {
            putJsonArray("ids") {
                add(JsonPrimitive("id-1"))
                add(buildJsonArray { add(JsonPrimitive("id-1")) })
            }
            putJsonObject("inner") { put("ref", "id-1") }
        }
        assertEquals(expected, bound)
    }

    @Test
    fun literalsNumbersBooleansNullAndObjectKeysComeBackUnchanged() {
        val arguments: JsonObject = buildJsonObject {
            put("price", "\$5.00")
            put("text", "price \$s1.item_id")
            put("count", 5)
            put("flag", false)
            put("nothing", JsonNull)
            put("\$s1.item_id", "value")
        }
        val bound = bindArguments(arguments, mapOf("s1" to mapOf("item_id" to "id-1")))
        assertEquals(arguments, bound)
    }

    @Test
    fun anUnresolvedReferenceBindsNothing() {
        val arguments = buildJsonObject {
            put("a", "\$s1.item_id")
            put("b", "\$s1.other")
        }
        assertNull(bindArguments(arguments, emptyMap()))
        assertNull(bindArguments(arguments, mapOf("s1" to mapOf("item_id" to "id-1"))))
        assertNull(bindArguments(arguments, mapOf("s2" to mapOf("item_id" to "id-1", "other" to "o"))))
    }

    @Test
    fun targetsMergeAsAUnionAndAConflictedKeyIsDroppedForGood() {
        val union = mergeTargets(
            listOf(action(mapOf("item_id" to "a", "x" to "1")), action(mapOf("item_id" to "a", "y" to "2"))),
        )
        assertEquals(mapOf("item_id" to "a", "x" to "1", "y" to "2"), union)
        val conflicted = mergeTargets(
            listOf(
                action(mapOf("item_id" to "a")),
                action(mapOf("item_id" to "b")),
                action(mapOf("item_id" to "a")),
            ),
        )
        assertEquals(emptyMap<String, String>(), conflicted)
    }
}
