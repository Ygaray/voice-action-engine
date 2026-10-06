package io.github.ygaray.voiceactionengine.core

import io.github.ygaray.voiceactionengine.core.strategy.grammar.GrammarPack
import java.math.BigDecimal
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val FIXTURES = "/grammar/stt-fixtures.tsv"
private const val SLOT = "n"
private const val LARGEST = 999_999L
private const val REJECT = "reject"
private const val COLUMNS = 6

// Rows whose recognized text the strict grammar does not read, each with the one-line reason it stays strict (D-08).
// A carrier-word mishear is not a number finding: the recognizer heard a different command, so no number table can
// help. A number-form entry is a deliberate refusal, recorded in grammar/README.md.
private val KNOWN_REJECTS: Map<String, String> = mapOf(
    "en-18" to "recognizer heard `a hundred` as `counter 200`: the carrier `to` is missing and the value is wrong",
    "en-23" to "recognizer heard `a thousand` as `counter 2000`: the carrier `to` is missing and the value is wrong",
    "en-27" to "`20 000` is two digit tokens (a space group); adjacent digit tokens stay refused, like `dos 1`",
    "en-28" to "carrier misheard as `Builder page`; the `15 000` space group is refused as for en-27",
    "es-01" to "carrier misheard (`con ... en serio`): `pon` became `con` and `cero` became `serio`",
    "es-02" to "carrier misheard: `pon` became `un`",
    "es-03" to "carrier misheard: `pon` became `con`",
    "es-04" to "carrier misheard: `pon` became `Ponle`",
    "es-05" to "carrier misheard: `ve a` became `Mira`",
    "es-07" to "carrier misheard: `pon` became `con`",
    "es-08" to "carrier misheard: `pon` became `con`",
    "es-11" to "carrier misheard: `pon` became `con`",
    "es-12" to "carrier misheard: `pon` became `con`",
    "es-13" to "carrier misheard: `pon` became `con`",
    "es-15" to "carrier misheard: `ve a` became `Mira`",
    "es-17" to "carrier misheard: `pon` became `con`",
    "es-18" to "carrier misheard: `pon` became `con`",
    "es-19" to "carrier misheard: `pon` became `con` and the value was dropped",
    "es-22" to "carrier misheard: `ve a` became `miren`",
    "es-23" to "carrier misheard: `pon` became `con` and the value was dropped",
    "es-24" to "carrier misheard: `pon` became `con`",
    "es-25" to "carrier misheard: `pon` became `con`",
    "es-26" to "carrier misheard: `pon` became `Ponle`",
    "es-27" to "`21.000` in Spanish is a single-dot thousands group, which could be 21.0: stays ambiguous, so refused",
    "es-28" to "`100 mil` mixes digits and a word; the strict table never mixes them",
    "es-29" to "carrier misheard: `pon` became `con`",
    "es-30" to "carrier misheard: `pon` became `con`",
    "es-34" to "carrier misheard: `pon` became `con`",
    "es-36" to "carrier misheard: `pon` became `con`",
    "es-38" to "carrier misheard: `ve a` became `miren`",
    "es-40" to "carrier misheard and the value truncated to `116`: `miren la pagina 116`",
    "es-41" to "carrier misheard: `pon` became `con`",
)

// Reject rows whose recognized text is a different, valid phrase: the recognizer corrected the near-miss itself, so
// the grammar reads what was recognized. The stimulus text is still refused (checked separately). id -> value read.
private val RECOGNIZER_NORMALIZED: Map<String, String> = mapOf(
    "en-38" to "14",
    "en-40" to "1000",
    "en-41" to "1000",
    "es-45" to "105",
)

private data class FixtureRow(
    val id: String,
    val lang: String,
    val text: String,
    val expect: String,
    val recognized: String,
    val provenance: String,
)

/**
 * D-12 fold-back: every captured recognizer transcript from the TESTER window (plan 14-09) is matched against a neutral
 * pack whose carriers mirror the capture prompts, under its own language label. A recognized form is read as exactly
 * the value spoken, or sits in [KNOWN_REJECTS] with a reason; it never reads as another value.
 */
class GrammarSttFixturesTest {

    private fun carriers(spec: GrammarPack.IntentBuilder.() -> Unit): GrammarPack = GrammarPack {
        intent("set_counter") {
            spec()
            en("set the counter to {$SLOT}")
            es("pon el contador en {$SLOT}")
        }
        intent("set_timer") {
            spec()
            en("set the timer to {$SLOT}")
            es("pon el temporizador en {$SLOT}")
        }
        intent("set_level") {
            spec()
            en("set the level to {$SLOT}")
            es("pon el nivel en {$SLOT}")
        }
        intent("go_page") {
            spec()
            en("go to page {$SLOT}")
            es("ve a la pagina {$SLOT}")
        }
        intent("set_volume") {
            spec()
            en("set the volume to {$SLOT}")
            es("pon el volumen en {$SLOT}")
        }
        intent("set_light") {
            spec()
            en("set the light to {$SLOT}")
            es("pon la luz en {$SLOT}")
        }
    }

    private val integerPack = carriers { integer(SLOT, 0, LARGEST) }
    private val decimalPack = carriers { decimal(SLOT, 0.0, LARGEST.toDouble()) }

    private fun rows(): List<FixtureRow> {
        val text = requireNotNull(javaClass.getResource(FIXTURES)) { "missing $FIXTURES" }.readText()
        return text.lines().drop(1).filter { it.isNotBlank() }.map { line ->
            val cells = line.split('\t')
            assertEquals("fixture row shape: $line", COLUMNS, cells.size)
            FixtureRow(cells[0], cells[1], cells[2], cells[3], cells[4], cells[5])
        }
    }

    // The value a text reads as under a language label, from both slot kinds; null when neither pack matches it.
    private fun read(text: String, lang: String, decimal: Boolean): BigDecimal? {
        val pack = if (decimal) decimalPack else integerPack
        val value = pack.match(text, lang)?.arguments?.get(SLOT) as? JsonPrimitive
        return value?.content?.let(::BigDecimal)
    }

    private fun readAny(text: String, lang: String): BigDecimal? = read(text, lang, false) ?: read(text, lang, true)

    private fun sameValue(left: BigDecimal?, right: String): Boolean =
        left != null && left.compareTo(BigDecimal(right)) == 0

    @Test
    fun everyCapturedRowReadsAsRecordedOrIsAKnownRefusal() {
        val data = rows()
        assertTrue("the fixture file must hold rows", data.isNotEmpty())
        val failures = mutableListOf<String>()
        var asserted = 0
        for (row in data) {
            asserted++
            assertEquals("synthetic-tts", row.provenance)
            if (row.expect == REJECT) checkReject(row, failures) else checkValue(row, failures)
        }
        assertEquals("every data row is asserted", data.size, asserted)
        assertTrue("grammar disagrees with the capture:\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    private fun checkValue(row: FixtureRow, failures: MutableList<String>) {
        val decimal = '.' in row.expect
        // The spoken stimulus must read as the value: this proves the neutral carriers, not the recognizer.
        val stimulus = read(row.text, row.lang, decimal)
        if (!sameValue(stimulus, row.expect)) {
            failures += "${row.id}: stimulus `${row.text}` read $stimulus, want ${row.expect}"
        }
        val recognized = read(row.recognized, row.lang, decimal)
        val shown = "${row.id}: recognized `${row.recognized}`"
        when {
            row.id in KNOWN_REJECTS -> if (recognized != null) failures += "$shown now reads $recognized"
            recognized == null -> failures += "$shown is not read, want ${row.expect}"
            !sameValue(recognized, row.expect) -> failures += "$shown read $recognized, want ${row.expect}"
        }
    }

    private fun checkReject(row: FixtureRow, failures: MutableList<String>) {
        val stimulus = readAny(row.text, row.lang)
        if (stimulus != null) failures += "${row.id}: near-miss stimulus `${row.text}` read $stimulus"
        val recognized = readAny(row.recognized, row.lang)
        val normalized = RECOGNIZER_NORMALIZED[row.id]
        val shown = "${row.id}: recognized `${row.recognized}`"
        when {
            normalized == null -> if (recognized != null) failures += "$shown read $recognized"
            !sameValue(recognized, normalized) -> failures += "$shown read $recognized, want $normalized"
        }
    }

    @Test
    fun theFileIsNotVacuousAndEveryRefusalHasAReason() {
        val ids = rows().map { it.id }.toSet()
        assertTrue(ids.size >= 1)
        for ((id, reason) in KNOWN_REJECTS + RECOGNIZER_NORMALIZED.mapValues { "normalized" }) {
            assertTrue("$id is not a fixture row", id in ids)
            assertTrue("$id needs a reason", reason.isNotBlank())
        }
    }
}
