package io.github.ygaray.voiceactionengine.spike

import io.github.ygaray.voiceactionengine.spike.envelope.SmallEnvelope
import io.github.ygaray.voiceactionengine.spike.evidence.Envelope
import io.github.ygaray.voiceactionengine.spike.evidence.ItemKind
import io.github.ygaray.voiceactionengine.spike.evidence.Lang
import io.github.ygaray.voiceactionengine.spike.gold.GoldFormatException
import io.github.ygaray.voiceactionengine.spike.gold.GoldSet
import io.github.ygaray.voiceactionengine.spike.gold.Minima
import io.github.ygaray.voiceactionengine.spike.verdict.SchemaResult
import io.github.ygaray.voiceactionengine.spike.verdict.SchemaSubset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

private const val CANARY = "canary-gold-transcript-q7"
private val NO_MINIMA = Minima(0, 0, 0, 0)

/** The committed small gold set and the load-time validation that keeps N honest (distinct items, known tools). */
class GoldSetTest {
    private val tools = SmallEnvelope.tools

    private fun committed(): String = File("src/main/assets/gold/small-gold.json").readText()

    private fun item(id: String, lang: String = "en", kind: String = "pos", transcript: String = CANARY, expect: String =
        """{"tool":"create_item","args":{"title":"milk"}}""", extra: String = ""): String =
        """{"id":"$id","lang":"$lang","kind":"$kind","transcript":"$transcript","expect":$expect$extra}"""

    private fun file(vararg items: String): String =
        """{"version":1,"envelope":"small","items":[${items.joinToString(",")}]}"""

    private fun codeOf(json: String, minima: Minima = NO_MINIMA): String {
        try {
            GoldSet.load(json, tools, minima)
        } catch (e: GoldFormatException) {
            assertFalse("a code, never item text: ${e.message}", e.message!!.contains("canary"))
            return e.message!!
        }
        fail("expected a GoldFormatException")
        return ""
    }

    @Test
    fun theCommittedSmallGoldMeetsTheMinimaWithDistinctItems() {
        val gold = GoldSet.load(committed(), tools)

        assertEquals(Envelope.SMALL, gold.envelope)
        val posEn = gold.items.filter { it.kind == ItemKind.POS && it.lang == Lang.EN }
        val posEs = gold.items.filter { it.kind == ItemKind.POS && it.lang == Lang.ES }
        val neg = gold.items.filter { it.kind == ItemKind.NEG }
        assertTrue("pos en ${posEn.size}", posEn.size >= 50)
        assertTrue("pos es ${posEs.size}", posEs.size >= 50)
        assertTrue("neg ${neg.size}", neg.size >= 30)
        assertEquals(20, gold.items.count { it.forcedSubset })
        assertTrue(gold.items.filter { it.forcedSubset }.all { it.kind == ItemKind.POS })
        assertEquals(gold.items.size, gold.items.map { it.id }.toSet().size)
        assertEquals(gold.items.size, gold.items.map { it.transcript.lowercase() }.toSet().size)
        assertTrue(gold.items.all { Regex("s_(en|es|neg)_[0-9]{3}").matches(it.id) })
    }

    @Test
    fun theCommittedSmallGoldCoversBothLanguagesInTheNegativesAndEveryTool() {
        val gold = GoldSet.load(committed(), tools)

        assertTrue(gold.items.count { it.kind == ItemKind.NEG && it.lang == Lang.EN } >= 15)
        assertTrue(gold.items.count { it.kind == ItemKind.NEG && it.lang == Lang.ES } >= 15)
        val used = gold.items.mapNotNull { it.expectTool }.toSet()
        assertTrue(used.containsAll(listOf("create_item", "edit_item", "find_items")))
    }

    @Test
    fun everyCommittedPositiveLabelIsValidAgainstItsToolSchema() {
        val gold = GoldSet.load(committed(), tools)

        for (item in gold.items.filter { it.kind == ItemKind.POS }) {
            val tool = tools.first { it.name == item.expectTool }
            assertEquals(item.id, SchemaResult.Valid, SchemaSubset.validate(tool.inputSchema, item.expectArgs))
        }
    }

    @Test
    fun aDuplicateIdIsRejected() {
        assertEquals("dup_id", codeOf(file(item("s_en_001"), item("s_en_001", transcript = "other"))))
    }

    @Test
    fun aRepeatedTranscriptUnderAnotherIdIsRejectedSoNCannotBeInflated() {
        assertEquals("dup_transcript", codeOf(file(item("s_en_001"), item("s_en_002"))))
    }

    @Test
    fun anUnknownLanguageIsRejected() {
        assertEquals("bad_lang", codeOf(file(item("s_en_001", lang = "fr"))))
    }

    @Test
    fun anExpectedToolOutsideTheEnvelopeIsRejected() {
        assertEquals("unknown_tool", codeOf(file(item("s_en_001", expect = """{"tool":"delete_everything","args":{}}"""))))
    }

    @Test
    fun anExpectedArgumentKeyOutsideTheToolPropertiesIsRejected() {
        assertEquals("unknown_arg", codeOf(file(item("s_en_001", expect = """{"tool":"create_item","args":{"title":"x","colour":"red"}}"""))))
    }

    @Test
    fun aPositiveWithoutAnExpectedToolIsRejected() {
        assertEquals("missing_expect", codeOf(file(item("s_en_001", expect = """{"args":{}}"""))))
    }

    @Test
    fun aPositiveWhoseArgumentsBreakTheToolSchemaIsRejected() {
        assertEquals("args_invalid", codeOf(file(item("s_en_001", expect = """{"tool":"create_item","args":{"title":5}}"""))))
        assertEquals("args_invalid", codeOf(file(item("s_en_002", expect = """{"tool":"create_item","args":{}}"""))))
    }

    @Test
    fun aNegativeThatNamesAToolIsRejected() {
        assertEquals("bad_expect", codeOf(file(item("s_neg_001", kind = "neg", expect = """{"tool":"create_item","args":{}}"""))))
    }

    @Test
    fun anIdOutsideTheEvidenceTokenAlphabetIsRejected() {
        assertEquals("bad_id", codeOf(file(item("has space"))))
    }

    @Test
    fun countsBelowTheMinimaAreRejectedByBucket() {
        val one = file(item("s_en_001"))

        assertEquals("below_minimum:pos_en", codeOf(one, Minima(2, 0, 0, 0)))
        assertEquals("below_minimum:pos_es", codeOf(one, Minima(0, 1, 0, 0)))
        assertEquals("below_minimum:neg", codeOf(one, Minima(0, 0, 1, 0)))
        assertEquals("below_minimum:forced", codeOf(one, Minima(0, 0, 0, 1)))
    }

    @Test
    fun aForcedSubsetOfTheWrongSizeIsRejected() {
        val json = file(
            item("s_en_001", extra = ""","forced_subset":true"""),
            item("s_en_002", transcript = "second", extra = ""","forced_subset":true"""),
        )

        assertEquals("bad_forced_subset", codeOf(json, Minima(0, 0, 0, 1)))
    }

    @Test
    fun aNegativeInTheForcedSubsetIsRejected() {
        val json = file(item("s_neg_001", kind = "neg", expect = """{"non_mutating":true}""", extra = ""","forced_subset":true"""))

        assertEquals("bad_forced_subset", codeOf(json))
    }

    @Test
    fun theEnvelopeAndDigestFieldsAreRead() {
        val json = """{"version":1,"envelope":"small","fixture_sha256":"abc123","items":[${item("s_en_001")}]}"""

        val gold = GoldSet.load(json, tools, NO_MINIMA)

        assertEquals("abc123", gold.fixtureSha256)
        assertEquals(1, gold.items.size)
    }

    @Test
    fun anUnknownEnvelopeIsRejected() {
        assertEquals("bad_envelope", codeOf("""{"version":1,"envelope":"huge","items":[]}"""))
    }

    @Test
    fun textThatIsNotJsonIsRejectedWithACode() {
        assertEquals("not_json", codeOf("this is not json $CANARY"))
    }

    @Test
    fun theSmallEnvelopeSchemasUseOnlySupportedKeywords() {
        for (tool in tools) {
            val scan = SchemaSubset.keywordsIn(tool.inputSchema)
            assertTrue("${tool.name}: ${scan.unknown}", scan.unknown.isEmpty())
        }
        assertEquals(listOf("find_items", "create_item", "edit_item", "ask_user"), tools.map { it.name })
        assertEquals(listOf(false, true, true, false), tools.map { it.mutating })
        assertTrue(tools.last().terminal)
    }
}
