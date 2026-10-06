package io.github.ygaray.voiceactionengine.spike

import io.github.ygaray.voiceactionengine.spike.gold.GoldMatcher
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private fun obj(text: String): JsonObject = Json.parseToJsonElement(text) as JsonObject

/** The gold comparison rules (D-06): normalized strings, numeric numbers, arrays as sets, extras tolerated. */
class GoldMatcherTest {
    @Test
    fun stringsAreTrimmedCaseFoldedAndWhitespaceCollapsed() {
        assertTrue(GoldMatcher.argsMatch(obj("""{"title":"milk"}"""), obj("""{"title":"  Milk "}""")))
        assertTrue(GoldMatcher.argsMatch(obj("""{"title":"buy  fresh milk"}"""), obj("""{"title":"Buy fresh\tMilk"}""")))
        assertEquals("buy fresh milk", GoldMatcher.normalize("  Buy   FRESH\nmilk "))
    }

    @Test
    fun aDecomposedAccentEqualsTheComposedOne() {
        val nfd = "café"
        val nfc = "café"
        assertFalse(nfd == nfc)
        assertTrue(GoldMatcher.argsMatch(obj("""{"title":"$nfc"}"""), obj("""{"title":"$nfd"}""")))
        assertEquals(GoldMatcher.normalize(nfd), GoldMatcher.normalize(nfc))
    }

    @Test
    fun lowerCasingUsesTheRootLocaleSoTurkishIDoesNotDiverge() {
        assertEquals("i", GoldMatcher.normalize("I"))
    }

    @Test
    fun anIntegralNumberEqualsItsDoubleForm() {
        assertTrue(GoldMatcher.argsMatch(obj("""{"count":2}"""), obj("""{"count":2.0}""")))
        assertTrue(GoldMatcher.argsMatch(obj("""{"count":2.0}"""), obj("""{"count":2}""")))
        assertFalse(GoldMatcher.argsMatch(obj("""{"count":2}"""), obj("""{"count":3}""")))
    }

    @Test
    fun aNumberIsNotItsStringForm() {
        assertFalse(GoldMatcher.argsMatch(obj("""{"count":2}"""), obj("""{"count":"2"}""")))
    }

    @Test
    fun arraysMatchAsSetsSoOrderDoesNotMatter() {
        assertTrue(GoldMatcher.argsMatch(obj("""{"tags":["a","b"]}"""), obj("""{"tags":["b","a"]}""")))
        assertTrue(GoldMatcher.argsMatch(obj("""{"tags":["a","b"]}"""), obj("""{"tags":["B "," a"]}""")))
        assertFalse(GoldMatcher.argsMatch(obj("""{"tags":["a","b"]}"""), obj("""{"tags":["a"]}""")))
        assertFalse(GoldMatcher.argsMatch(obj("""{"tags":["a","b"]}"""), obj("""{"tags":["a","b","c"]}""")))
    }

    @Test
    fun anExtraPredictedArgumentDoesNotBreakAMatch() {
        assertTrue(GoldMatcher.argsMatch(obj("""{"title":"milk"}"""), obj("""{"title":"milk","body":"2 litres"}""")))
    }

    @Test
    fun aMissingExpectedArgumentBreaksTheMatch() {
        assertFalse(GoldMatcher.argsMatch(obj("""{"title":"milk","body":"x"}"""), obj("""{"title":"milk"}""")))
        assertFalse(GoldMatcher.argsMatch(obj("""{"title":"milk"}"""), obj("""{}""")))
    }

    @Test
    fun aDifferentValueBreaksTheMatch() {
        assertFalse(GoldMatcher.argsMatch(obj("""{"title":"milk"}"""), obj("""{"title":"bread"}""")))
    }

    @Test
    fun booleansAndNestedValuesCompareStructurally() {
        assertTrue(GoldMatcher.argsMatch(obj("""{"flag":true}"""), obj("""{"flag":true}""")))
        assertFalse(GoldMatcher.argsMatch(obj("""{"flag":true}"""), obj("""{"flag":false}""")))
        assertTrue(GoldMatcher.argsMatch(obj("""{"o":{"a":" X ","n":1}}"""), obj("""{"o":{"n":1.0,"a":"x"}}""")))
    }

    @Test
    fun toolMatchIsExactAndNeedsAnExpectedTool() {
        assertTrue(GoldMatcher.toolMatch("create_item", "create_item"))
        assertFalse(GoldMatcher.toolMatch("create_item", "edit_item"))
        assertFalse(GoldMatcher.toolMatch("create_item", null))
        assertFalse(GoldMatcher.toolMatch(null, null))
    }

    @Test
    fun anExtraKeyInsideAListItemObjectDoesNotBreakTheMatch() {
        val expected = obj("""{"items":[{"text":"milk"},{"text":"eggs"}]}""")

        assertTrue(GoldMatcher.argsMatch(expected, obj("""{"items":[{"text":"Eggs","done":false},{"text":"milk","done":false}]}""")))
        assertFalse(GoldMatcher.argsMatch(expected, obj("""{"items":[{"text":"milk"},{"text":"bread"}]}""")))
        assertFalse(GoldMatcher.argsMatch(expected, obj("""{"items":[{"text":"milk"}]}""")))
    }

    @Test
    fun aRepeatedPredictedElementCannotStandInForTwoExpectedOnes() {
        assertFalse(GoldMatcher.argsMatch(obj("""{"tags":["a","b"]}"""), obj("""{"tags":["a","a"]}""")))
    }
}
