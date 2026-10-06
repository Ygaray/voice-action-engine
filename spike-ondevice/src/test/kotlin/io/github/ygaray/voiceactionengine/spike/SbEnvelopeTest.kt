package io.github.ygaray.voiceactionengine.spike

import io.github.ygaray.voiceactionengine.spike.envelope.SbEnvelope
import io.github.ygaray.voiceactionengine.spike.envelope.SbState
import io.github.ygaray.voiceactionengine.spike.evidence.Envelope
import io.github.ygaray.voiceactionengine.spike.gold.Minima
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.security.MessageDigest

private val NO_MINIMA = Minima(0, 0, 0, 0)

/**
 * The SB-sized envelope loader, proven against a synthetic stand-in fixture generated here (three invented tools, nothing
 * from SB): the real fixture and labels are private and never needed by a host test.
 */
class SbEnvelopeTest {
    @get:Rule
    val temp = TemporaryFolder()

    private fun sha(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun fixture(alphaSchemaExtra: String = ""): ByteArray =
        ("""{"system":"You are a stand-in assistant.","tools":[""" +
            """{"name":"alpha_create","description":"Creates an alpha.","input_schema":{"type":"object","properties":""" +
            """{"title":{"type":"string","minLength":1,"maxLength":50,"default":"x"},"count":{"type":"integer","minimum":0,"maximum":9}$alphaSchemaExtra},"required":["title"]}},""" +
            """{"name":"alpha_find","description":"Finds alphas.","input_schema":{"type":"object","properties":""" +
            """{"query":{"type":"string"}},"required":["query"]}},""" +
            """{"name":"alpha_ask","description":"Asks.","input_schema":{"type":"object","properties":""" +
            """{"question":{"type":"string"}},"required":["question"]}}]}""")
            .toByteArray()

    private fun gold(
        fixtureSha: String,
        meta: String = """{"alpha_create":"mutating","alpha_find":"read","alpha_ask":"terminal"}""",
        envelope: String = "sb",
    ): ByteArray =
        ("""{"version":1,"envelope":"$envelope","fixture_sha256":"$fixtureSha","tool_meta":$meta,"items":[""" +
            """{"id":"b_en_001","lang":"en","kind":"pos","transcript":"make an alpha called one","expect":{"tool":"alpha_create","args":{"title":"one"}}},""" +
            """{"id":"b_es_001","lang":"es","kind":"pos","transcript":"busca alfa","expect":{"tool":"alpha_find","args":{"query":"alfa"}}},""" +
            """{"id":"b_neg_001","lang":"en","kind":"neg","transcript":"never mind","expect":{"non_mutating":true}}]}""")
            .toByteArray()

    @Test
    fun aMatchingFixtureAndGoldLoadWithTheToolCountReported() {
        val fx = fixture()

        val state = SbEnvelope.load(fx, gold(sha(fx)), NO_MINIMA)

        assertTrue(state.toString(), state is SbState.Loaded)
        state as SbState.Loaded
        assertEquals(3, state.toolCount)
        assertEquals(Envelope.SB, state.envelope.env)
        assertEquals(3, state.gold.items.size)
        assertEquals(listOf(true, false, false), state.envelope.tools.map { it.mutating })
        assertTrue(state.envelope.tools.last().terminal)
    }

    @Test
    fun aGoldPinnedToAnotherFixtureIsRejectedAsAMismatch() {
        val fx = fixture()

        val state = SbEnvelope.load(fx, gold(sha(fixture(""","extra":{"type":"string"}"""))), NO_MINIMA)

        assertEquals("Invalid(code=fixture_mismatch)", state.toString())
    }

    @Test
    fun aGoldWithoutADigestPinIsRejectedAsAMismatch() {
        val fx = fixture()

        val state = SbEnvelope.load(fx, String(gold("")).replace("\"fixture_sha256\":\"\",", "").toByteArray(), NO_MINIMA)

        assertEquals("Invalid(code=fixture_mismatch)", state.toString())
    }

    @Test
    fun anUnsupportedSchemaKeywordFailsLoudlyNamingTheKeyword() {
        val fx = fixture(""","pick":{"anyOf":[{"type":"string"},{"type":"integer"}]}""")

        val state = SbEnvelope.load(fx, gold(sha(fx)), NO_MINIMA)

        assertEquals("Invalid(code=unknown_keyword:anyOf)", state.toString())
    }

    @Test
    fun aToolWithoutAMetaEntryIsRejected() {
        val fx = fixture()

        val state = SbEnvelope.load(fx, gold(sha(fx), meta = """{"alpha_create":"mutating"}"""), NO_MINIMA)

        assertEquals("Invalid(code=fixture_bad_tool)", state.toString())
    }

    @Test
    fun aGoldForTheSmallEnvelopeIsRejected() {
        val fx = fixture()

        val state = SbEnvelope.load(fx, gold(sha(fx), envelope = "small"), NO_MINIMA)

        assertEquals("Invalid(code=bad_envelope)", state.toString())
    }

    @Test
    fun theDefaultMinimaApplyToTheSbGoldToo() {
        val fx = fixture()

        val state = SbEnvelope.load(fx, gold(sha(fx)))

        assertEquals("Invalid(code=below_minimum:pos_en)", state.toString())
    }

    @Test
    fun aLabelThatBreaksItsToolSchemaIsRejected() {
        val fx = fixture()
        val bad = String(gold(sha(fx))).replace("\"title\":\"one\"", "\"title\":\"\"").toByteArray()

        val state = SbEnvelope.load(fx, bad, NO_MINIMA)

        assertEquals("Invalid(code=args_invalid)", state.toString())
    }

    @Test
    fun notJsonIsRejectedWithACodeAndNoFileText() {
        val state = SbEnvelope.load("canary-private-text".toByteArray(), "{}".toByteArray(), NO_MINIMA)

        assertEquals("Invalid(code=fixture_not_json)", state.toString())
    }

    @Test
    fun theStateNeverShowsToolNamesLabelsOrMoreThanAnEightHexDigest() {
        val fx = fixture()

        val text = SbEnvelope.load(fx, gold(sha(fx)), NO_MINIMA).toString()

        assertFalse(text.contains("alpha"))
        assertFalse(text.contains("one"))
        assertTrue(text, text.contains("fixture=${sha(fx).take(8)},"))
        assertFalse(text.contains(sha(fx).take(9)))
        assertFalse((SbEnvelope.load(fx, gold(sha(fx)), NO_MINIMA) as SbState.Loaded).envelope.toString().contains("alpha"))
    }

    @Test
    fun anAbsentFixtureIsReportedAbsentNeverSubstituted() {
        val dir = temp.newFolder("private")

        assertEquals(SbState.Absent, SbEnvelope.fromPrivateDir(dir, NO_MINIMA))
        File(dir, SbEnvelope.FIXTURE_FILE).writeBytes(fixture())
        assertEquals(SbState.Absent, SbEnvelope.fromPrivateDir(dir, NO_MINIMA))
        assertEquals(SbState.Absent, SbEnvelope.fromPrivateDir(File(dir, "missing"), NO_MINIMA))
    }

    @Test
    fun bothFilesInThePrivateDirectoryLoad() {
        val dir = temp.newFolder("private")
        val fx = fixture()
        File(dir, SbEnvelope.FIXTURE_FILE).writeBytes(fx)
        File(dir, SbEnvelope.GOLD_FILE).writeBytes(gold(sha(fx)))

        assertTrue(SbEnvelope.fromPrivateDir(dir, NO_MINIMA) is SbState.Loaded)
    }

    @Test
    fun theRepositoryAndTheAssetsHoldNoSbFile() {
        val assets = File("src/main/assets")
        val found = assets.walkTopDown().filter { it.isFile }.map { it.name.lowercase() }
            .filter { it.contains("sb-") || it.contains("fixture") }.toList()

        assertTrue(found.toString(), found.isEmpty())
    }
}
