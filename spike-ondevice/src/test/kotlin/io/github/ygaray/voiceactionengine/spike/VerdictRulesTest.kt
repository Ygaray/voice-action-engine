package io.github.ygaray.voiceactionengine.spike

import io.github.ygaray.voiceactionengine.spike.evidence.BackendKind
import io.github.ygaray.voiceactionengine.spike.evidence.Cell
import io.github.ygaray.voiceactionengine.spike.evidence.Envelope
import io.github.ygaray.voiceactionengine.spike.evidence.ItemKind
import io.github.ygaray.voiceactionengine.spike.evidence.Lang
import io.github.ygaray.voiceactionengine.spike.evidence.ModelKey
import io.github.ygaray.voiceactionengine.spike.evidence.Route
import io.github.ygaray.voiceactionengine.spike.evidence.Shape
import io.github.ygaray.voiceactionengine.spike.evidence.SpikeKind
import io.github.ygaray.voiceactionengine.spike.evidence.SpikeLine
import io.github.ygaray.voiceactionengine.spike.evidence.Stage
import io.github.ygaray.voiceactionengine.spike.evidence.TrialRecord
import io.github.ygaray.voiceactionengine.spike.verdict.CellSelector
import io.github.ygaray.voiceactionengine.spike.verdict.VerdictRules
import kotlin.math.ceil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The D-07 truth table. Every case mutates the committed synthetic green fixture in memory (never with extra files) and
 * asserts the exact reasons per envelope.
 */
class VerdictRulesTest {
    private val smallWinner = "e2b.gpu.a.auto"
    private val sbWinner = "e2b.cpu.b.auto"

    private val fixture: List<SpikeLine> by lazy {
        val text = checkNotNull(javaClass.getResourceAsStream("/evidence/green/evidence.txt")) { "green fixture missing" }
            .bufferedReader().readText()
        SpikeLine.parseAll(text)
    }

    // ---- helpers -------------------------------------------------------------------------------------------------

    private fun render(lines: List<SpikeLine>): List<String> = VerdictRules.evaluate(lines).render()

    private fun verdictLine(lines: List<SpikeLine>, envelope: Envelope): String =
        render(lines).first { it.startsWith("SPIKE_VERDICT envelope=${envelope.wire} ") }

    private fun field(line: String, key: String): String =
        line.split(' ').first { it.startsWith("$key=") }.removePrefix("$key=")

    private fun reasons(lines: List<SpikeLine>, envelope: Envelope): List<String> {
        val text = field(verdictLine(lines, envelope), "reasons")
        return if (text == "none") emptyList() else text.split(',')
    }

    private fun assertReasons(lines: List<SpikeLine>, small: List<String>, sb: List<String>) {
        assertEquals("small reasons", small, reasons(lines, Envelope.SMALL))
        assertEquals("sb reasons", sb, reasons(lines, Envelope.SB))
        for ((envelope, expected) in listOf(Envelope.SMALL to small, Envelope.SB to sb)) {
            val verdict = field(verdictLine(lines, envelope), "verdict")
            assertEquals(if (expected.isEmpty()) "green" else "red", verdict)
        }
    }

    /** The mutation hits the small envelope only. */
    private fun assertSmallOnly(lines: List<SpikeLine>, reason: String) =
        assertReasons(lines, listOf(reason), emptyList())

    private fun List<SpikeLine>.mapLines(transform: (SpikeLine) -> SpikeLine?): List<SpikeLine> =
        mapNotNull(transform)

    private fun SpikeLine.with(key: String, value: String): SpikeLine =
        SpikeLine.of(kind, *fields.map { if (it.first == key) key to value else it }.toTypedArray())

    private fun SpikeLine.isStage(stage: Stage): Boolean = kind == SpikeKind.STAGE && this["stage"] == stage.wire

    /** Rewrites the trials of [stage] on [cell] (every cell when null); [transform] gets the index among the matches. */
    private fun List<SpikeLine>.mapTrials(
        envelope: Envelope,
        stage: Stage,
        cell: String?,
        transform: (Int, TrialRecord) -> TrialRecord?,
    ): List<SpikeLine> {
        var index = 0
        return mapNotNull { line ->
            val trial = TrialRecord.fromLine(line)
            if (trial == null || trial.env != envelope || trial.stage != stage || (cell != null && trial.cell.wire != cell)) {
                line
            } else {
                transform(index++, trial)?.toLine()
            }
        }
    }

    private fun smallConfirm(transform: (Int, TrialRecord) -> TrialRecord?): List<SpikeLine> =
        fixture.mapTrials(Envelope.SMALL, Stage.CONFIRM_SMALL, smallWinner, transform)

    private fun stageLine(vararg fields: Pair<String, String>): SpikeLine = SpikeLine.of(SpikeKind.STAGE, *fields)

    // ---- the green fixture ---------------------------------------------------------------------------------------

    @Test
    fun greenFixtureIsGreenOnBothEnvelopes() {
        assertReasons(fixture, emptyList(), emptyList())
        for (envelope in Envelope.entries) {
            assertTrue(verdictLine(fixture, envelope).contains(" verdict=green reasons=none "))
        }
    }

    @Test
    fun greenFixtureRendersEveryNamedField() {
        val small = verdictLine(fixture, Envelope.SMALL)
        val expectedKeys = listOf(
            "envelope", "verdict", "reasons", "cell", "warm_p50_ms", "warm_p95_ms", "cold_ms", "sustained_ratio",
            "thermal_max", "peak_pss_mb", "deaths", "schema_valid", "en", "en_lb", "es", "es_lb", "false_writes",
            "kv_reuse", "rf_enforced", "rf_enum_nested", "gpu_adreno730", "route_ab",
        )
        assertEquals(expectedKeys, small.split(' ').drop(1).map { it.substringBefore('=') })
        assertEquals(smallWinner, field(small, "cell"))
        assertEquals("130/130", field(small, "schema_valid"))
        assertEquals("50/50", field(small, "en"))
        assertEquals("0.929", field(small, "en_lb"))
        assertEquals("50/50", field(small, "es"))
        assertEquals("0.929", field(small, "es_lb"))
        assertEquals("0/30", field(small, "false_writes"))
        assertEquals("0", field(small, "deaths"))
        assertEquals("7200", field(small, "cold_ms"))
        assertEquals("moderate", field(small, "thermal_max"))
        assertEquals("1750", field(small, "peak_pss_mb"))
        assertEquals("reused", field(small, "kv_reuse"))
        assertEquals("enforced", field(small, "rf_enforced"))
        assertEquals("enforced", field(small, "rf_enum_nested"))
        assertEquals("gpu_ok", field(small, "gpu_adreno730"))
        assertEquals("a:10/10:840,b:10/10:1040", field(small, "route_ab"))
        assertEquals(sbWinner, field(verdictLine(fixture, Envelope.SB), "cell"))
    }

    @Test
    fun warmPercentilesAreNearestRankOverWarmConfirmTrials() {
        val warm = fixture.mapNotNull(TrialRecord::fromLine)
            .filter { it.env == Envelope.SMALL && it.stage == Stage.CONFIRM_SMALL && !it.firstInProcess }
            .map { it.latencyMs }.sorted()
        val p50 = warm[ceil(0.50 * warm.size).toInt() - 1]
        val p95 = warm[ceil(0.95 * warm.size).toInt() - 1]
        val line = verdictLine(fixture, Envelope.SMALL)
        assertEquals(p50.toString(), field(line, "warm_p50_ms"))
        assertEquals(p95.toString(), field(line, "warm_p95_ms"))
    }

    @Test
    fun repeatedItemsNeverCountTowardN() {
        val repeats = fixture.mapNotNull(TrialRecord::fromLine)
            .filter { it.env == Envelope.SMALL && it.stage == Stage.CONFIRM_SMALL && it.lang == Lang.EN && !it.firstInProcess }
            .map { it.toLine() }
        val lines = fixture + repeats
        assertReasons(lines, emptyList(), emptyList())
        assertEquals("50/50", field(verdictLine(lines, Envelope.SMALL), "en"))
        assertEquals("130/130", field(verdictLine(lines, Envelope.SMALL), "schema_valid"))
    }

    // ---- one reason per metric -----------------------------------------------------------------------------------

    @Test
    fun warmP50AboveBarFails() {
        val lines = smallConfirm { index, trial -> if (index == 0) trial else trial.copy(latencyMs = 4000) }
        assertSmallOnly(lines, "fail:warm_p50")
    }

    @Test
    fun warmP95AboveBarFails() {
        val lines = smallConfirm { index, trial -> if (index in 1..10) trial.copy(latencyMs = 6000) else trial }
        assertSmallOnly(lines, "fail:warm_p95")
    }

    @Test
    fun coldAboveBarFails() {
        val lines = fixture.mapLines { line ->
            if (line.kind == SpikeKind.INIT && line["stage"] == Stage.CONFIRM_SMALL.wire) line.with("init_ms", "25000") else line
        }
        assertSmallOnly(lines, "fail:cold")
    }

    @Test
    fun sustainedRatioAboveBarFails() {
        val lines = fixture.mapTrials(Envelope.SMALL, Stage.SUSTAINED_SMALL, null) { _, trial -> trial.copy(latencyMs = 3000) }
        assertSmallOnly(lines, "fail:sustained")
        assertTrue(field(verdictLine(lines, Envelope.SMALL), "sustained_ratio").toDouble() > 1.5)
    }

    @Test
    fun sustainedShorterThanTheMinimumIsUnmeasured() {
        val lines = fixture.mapLines { line ->
            if (line.isStage(Stage.SUSTAINED_SMALL)) line.with("seconds", "100") else line
        }
        assertSmallOnly(lines, "unmeasured:sustained")
        assertEquals("na", field(verdictLine(lines, Envelope.SMALL), "sustained_ratio"))
    }

    @Test
    fun severeThermalFails() {
        val lines = fixture + SpikeLine.of(
            SpikeKind.THERMAL,
            "stage" to Stage.CONFIRM_SMALL.wire,
            "status" to "severe",
        )
        assertSmallOnly(lines, "fail:thermal")
        assertEquals("severe", field(verdictLine(lines, Envelope.SMALL), "thermal_max"))
    }

    @Test
    fun peakPssAboveBarFails() {
        val lines = fixture.mapLines { line ->
            if (line.kind == SpikeKind.MEM && line["stage"] == Stage.CONFIRM_SMALL.wire) line.with("peak_pss_mb", "2100") else line
        }
        assertSmallOnly(lines, "fail:peak_pss")
    }

    @Test
    fun aNonWinningCellAboveThePssBarDoesNotCount() {
        // The fixture already has e2b.gpu.b.auto at 2100 MB in the screen; it is not the winner, so the verdict is green.
        assertReasons(fixture, emptyList(), emptyList())
        assertEquals("1750", field(verdictLine(fixture, Envelope.SMALL), "peak_pss_mb"))
    }

    @Test
    fun aProcessDeathFailsBothEnvelopes() {
        val lines = fixture + SpikeLine.of(SpikeKind.EXIT, "reason" to "crash_native", "count" to "1")
        assertReasons(lines, listOf("fail:process_deaths"), listOf("fail:process_deaths"))
        assertEquals("1", field(verdictLine(lines, Envelope.SMALL), "deaths"))
    }

    @Test
    fun missingExitReasonsStageIsUnmeasuredNotZero() {
        val lines = fixture.filterNot { it.isStage(Stage.EXIT_REASONS) }
        assertReasons(lines, listOf("unmeasured:process_deaths"), listOf("unmeasured:process_deaths"))
    }

    @Test
    fun schemaValidBelowTheBarFails() {
        var flipped = 0
        val lines = smallConfirm { _, trial ->
            if (trial.kind == ItemKind.NEG && flipped < 4) {
                flipped++
                trial.copy(schemaValid = false)
            } else {
                trial
            }
        }
        assertSmallOnly(lines, "fail:schema_valid")
        assertEquals("126/130", field(verdictLine(lines, Envelope.SMALL), "schema_valid"))
    }

    @Test
    fun schemaValidOverFewerThan100TrialsIsUnmeasured() {
        // 99 trials cannot also hold 50 EN + 50 ES + 30 negatives, so schema_valid never stands alone: the missing
        // Spanish items are reported too. 50 EN + 19 ES + 30 negatives = 99.
        var es = 0
        val lines = smallConfirm { _, trial ->
            if (trial.lang == Lang.ES && ++es > 19) null else trial
        }
        assertReasons(lines, listOf("unmeasured:schema_valid", "unmeasured:semantic_es"), emptyList())
        assertEquals("na", field(verdictLine(lines, Envelope.SMALL), "schema_valid"))
    }

    @Test
    fun englishSemanticLowerBoundBelowBarFails() {
        var broken = 0
        val lines = smallConfirm { _, trial ->
            if (trial.lang == Lang.EN && broken < 3) {
                broken++
                trial.copy(toolMatch = false)
            } else {
                trial
            }
        }
        assertSmallOnly(lines, "fail:semantic_en")
        assertEquals("47/50", field(verdictLine(lines, Envelope.SMALL), "en"))
    }

    @Test
    fun spanishSemanticLowerBoundBelowBarFails() {
        var broken = 0
        val lines = smallConfirm { _, trial ->
            if (trial.lang == Lang.ES && broken < 5) {
                broken++
                trial.copy(argsMatch = false)
            } else {
                trial
            }
        }
        assertSmallOnly(lines, "fail:semantic_es")
        assertEquals("45/50", field(verdictLine(lines, Envelope.SMALL), "es"))
    }

    @Test
    fun spanishAt46Of50Passes() {
        var broken = 0
        val lines = smallConfirm { _, trial ->
            if (trial.lang == Lang.ES && broken < 4) {
                broken++
                trial.copy(argsMatch = false)
            } else {
                trial
            }
        }
        assertReasons(lines, emptyList(), emptyList())
    }

    @Test
    fun aFalseWriteFails() {
        var done = false
        val lines = smallConfirm { _, trial ->
            if (trial.kind == ItemKind.NEG && !done) {
                done = true
                trial.copy(falseWrite = true)
            } else {
                trial
            }
        }
        assertSmallOnly(lines, "fail:false_writes")
        assertEquals("1/30", field(verdictLine(lines, Envelope.SMALL), "false_writes"))
    }

    @Test
    fun fewerThan30NegativesIsUnmeasured() {
        var dropped = false
        val lines = smallConfirm { _, trial ->
            if (trial.kind == ItemKind.NEG && !dropped) {
                dropped = true
                null
            } else {
                trial
            }
        }
        assertSmallOnly(lines, "unmeasured:false_writes")
    }

    @Test
    fun missingKvReuseGatesTheSbEnvelopeOnly() {
        val lines = fixture.filterNot { it.kind == SpikeKind.KVREUSE }
        assertReasons(lines, emptyList(), listOf("unmeasured:kv_reuse"))
        assertEquals("na", field(verdictLine(lines, Envelope.SMALL), "kv_reuse"))
    }

    @Test
    fun sbPrefillBoundEarlyExitIsRedForSbOnly() {
        val lines = fixture.mapLines { line ->
            if (line.isStage(Stage.CONFIRM_SB)) {
                stageLine("stage" to "confirm_sb", "result" to "early_exit", "reason" to "sb_prefill_bound", "trials" to "0", "planned" to "0")
            } else {
                line
            }
        }
        assertReasons(lines, emptyList(), listOf("early_exit:sb_prefill_bound"))
    }

    @Test
    fun aSharedStageEarlyExitReachesBothEnvelopes() {
        val lines = fixture.mapLines { line ->
            if (line.isStage(Stage.INIT)) {
                stageLine("stage" to "init", "result" to "early_exit", "reason" to "init_failed_all", "trials" to "0", "planned" to "0")
            } else {
                line
            }
        }
        assertReasons(lines, listOf("early_exit:init_failed_all"), listOf("early_exit:init_failed_all"))
    }

    @Test
    fun anEnvelopeEarlyExitReasonIsListedOnce() {
        val lines = fixture.mapLines { line ->
            if (line.isStage(Stage.CONFIRM_SB) || line.isStage(Stage.SUSTAINED_SB)) {
                stageLine("stage" to line["stage"]!!, "result" to "early_exit", "reason" to "sb_fixture_absent", "trials" to "0", "planned" to "0")
            } else {
                line
            }
        }
        assertEquals(listOf("early_exit:sb_fixture_absent"), reasons(lines, Envelope.SB))
    }

    @Test
    fun aStageShortOfItsPlannedTrialsIsIncomplete() {
        val lines = fixture.mapLines { line ->
            if (line.isStage(Stage.CONFIRM_SMALL)) line.with("trials", "100") else line
        }
        assertSmallOnly(lines, "fail:incomplete_stage")
    }

    @Test
    fun aStageThatErroredIsIncomplete() {
        val lines = fixture.mapLines { line ->
            if (line.isStage(Stage.SUSTAINED_SMALL)) line.with("result", "error") else line
        }
        assertSmallOnly(lines, "fail:incomplete_stage")
    }

    @Test
    fun aWinnerThatDiffersFromTheRecomputedOneIsInconsistent() {
        val lines = fixture.mapLines { line ->
            if (line.isStage(Stage.SCREEN_SMALL)) line.with("winner", sbWinner) else line
        }
        assertSmallOnly(lines, "inconsistent:winner")
        assertEquals(smallWinner, field(verdictLine(lines, Envelope.SMALL), "cell"))
    }

    @Test
    fun missingResponseFormatRowsAreUnmeasured() {
        val noFlat = fixture.filterNot { it.kind == SpikeKind.SCHEMAPROBE && it["feature"] == "flat" }
        assertReasons(noFlat, listOf("unmeasured:rf_enforced"), listOf("unmeasured:rf_enforced"))
        val noNested = fixture.filterNot { it.kind == SpikeKind.SCHEMAPROBE && it["feature"] == "nested" }
        assertReasons(noNested, listOf("unmeasured:rf_enum_nested"), listOf("unmeasured:rf_enum_nested"))
    }

    @Test
    fun rfEnumNestedReportsTheWorstDisposition() {
        val lines = fixture.mapLines { line ->
            if (line.kind == SpikeKind.SCHEMAPROBE && line["feature"] == "enum" && line["constraint"] == "on") {
                line.with("disposition", "ignored")
            } else {
                line
            }
        }
        assertReasons(lines, emptyList(), emptyList())
        assertEquals("ignored", field(verdictLine(lines, Envelope.SMALL), "rf_enum_nested"))
    }

    @Test
    fun gpuRowIsTheLastGpuLineAndMissingMeansUnmeasured() {
        val none = fixture.filterNot { it.kind == SpikeKind.GPU }
        assertReasons(none, listOf("unmeasured:gpu_adreno730"), listOf("unmeasured:gpu_adreno730"))
        val slower = fixture + SpikeLine.of(SpikeKind.GPU, "disposition" to "gpu_slower")
        assertEquals("gpu_slower", field(verdictLine(slower, Envelope.SB), "gpu_adreno730"))
        val failed = fixture + SpikeLine.of(SpikeKind.GPU, "disposition" to "gpu_init_failed", "code" to "no_opencl")
        assertEquals("gpu_init_failed:no_opencl", field(verdictLine(failed, Envelope.SB), "gpu_adreno730"))
    }

    @Test
    fun routeAbNeedsScreenTrialsOnBothRoutes() {
        val lines = fixture.mapNotNull { line ->
            val trial = TrialRecord.fromLine(line)
            if (trial != null && trial.env == Envelope.SMALL && trial.stage == Stage.SCREEN_SMALL && trial.cell.route == Route.B) null else line
        }
        assertSmallOnly(lines, "unmeasured:route_ab")
    }

    // ---- toolchain, control, meta, ordering ----------------------------------------------------------------------

    @Test
    fun aRedToolchainIsFirstInBothEnvelopes() {
        val lines = fixture + SpikeLine.of(SpikeKind.TOOLCHAIN, "kind" to "verdict_input", "result" to "red")
        assertReasons(lines, listOf("toolchain"), listOf("toolchain"))
        assertTrue(render(lines).last().contains(" toolchain=red "))
    }

    @Test
    fun everyPinFailingToCompileIsARedToolchain() {
        val lines = fixture.mapLines { line ->
            if (line.kind == SpikeKind.TOOLCHAIN && line["kind"] == "compile") line.with("result", "red") else line
        }
        assertReasons(lines, listOf("toolchain"), listOf("toolchain"))
    }

    @Test
    fun withNoMeasurementEveryGatingMetricIsUnmeasuredInFixedOrder() {
        val bare = fixture.filter { it.kind == SpikeKind.ENV || it.kind == SpikeKind.TOOLCHAIN }
        val common = VerdictRules.metricOrder.map { "unmeasured:$it" }
        assertReasons(bare, common, common + "unmeasured:kv_reuse")
        assertEquals(
            listOf("warm_p50", "warm_p95", "cold", "sustained", "thermal", "peak_pss", "process_deaths", "schema_valid",
                "semantic_en", "semantic_es", "false_writes", "rf_enforced", "rf_enum_nested", "gpu_adreno730", "route_ab"),
            VerdictRules.metricOrder,
        )
    }

    @Test
    fun emptyEvidenceIsRedWithNoShaOrPin() {
        val lines = render(emptyList())
        assertTrue(lines.last().startsWith("SPIKE_VERDICT_META thresholds_sha=none harness_sha=none toolchain=ok pin=none"))
        assertEquals(4, lines.size)
        assertEquals("red", field(lines[0], "verdict"))
    }

    @Test
    fun reportHasTheFixedLineOrderAndMeta() {
        val lines = render(fixture)
        assertEquals(
            listOf("SPIKE_VERDICT envelope=small", "SPIKE_VERDICT envelope=sb", "SPIKE_CONTROL", "SPIKE_VERDICT_META"),
            lines.map { it.split(' ').take(if (it.startsWith("SPIKE_VERDICT ")) 2 else 1).joinToString(" ") },
        )
        assertEquals(
            "SPIKE_VERDICT_META thresholds_sha=00000000 harness_sha=0000000000 toolchain=ok pin=0.17.1",
            lines.last(),
        )
    }

    @Test
    fun theOneBillionParameterControlNeverWinsAndAppearsOnlyAsControl() {
        val line = verdictLine(fixture, Envelope.SMALL)
        assertEquals(smallWinner, field(line, "cell"))
        assertTrue(render(fixture).none { it.startsWith("SPIKE_VERDICT ") && it.contains("g3_1b") })
        val control = render(fixture).first { it.startsWith("SPIKE_CONTROL ") }
        assertEquals(
            "SPIKE_CONTROL envelope=small model=g3_1b status=measured cell=g3_1b.cpu.a.auto schema_valid=5/5 p50_ms=120 peak_pss_mb=800",
            control,
        )
    }

    @Test
    fun noControlLinesMeansSkippedGated() {
        val lines = fixture.filterNot { line ->
            line["cell"]?.startsWith("g3_1b") == true
        }
        val control = render(lines).first { it.startsWith("SPIKE_CONTROL ") }
        assertEquals(
            "SPIKE_CONTROL envelope=small model=g3_1b status=skipped_gated cell=none schema_valid=na p50_ms=na peak_pss_mb=na",
            control,
        )
        assertReasons(lines, emptyList(), emptyList())
    }

    // ---- cell selection ------------------------------------------------------------------------------------------

    private fun cell(model: ModelKey, backend: BackendKind, route: Route) = Cell(model, backend, route, Shape.AUTO)

    private fun trials(cell: Cell, correctPos: Int, latency: Long): List<TrialRecord> =
        (1..4).map { index ->
            TrialRecord(
                env = Envelope.SMALL, stage = Stage.SCREEN_SMALL, cell = cell, item = "x$index",
                lang = if (index <= 2) Lang.EN else Lang.ES, kind = ItemKind.POS, schemaValid = true,
                toolMatch = index <= correctPos, argsMatch = true, falseWrite = false, latencyMs = latency + index,
                ttftMs = null, prefillTokens = 1, decodeTokens = 1, firstInProcess = false, outcome = "ok",
            )
        }

    private fun mem(cell: Cell, mb: Int): SpikeLine = SpikeLine.of(
        SpikeKind.MEM, "stage" to "screen_small", "cell" to cell.wire, "peak_pss_mb" to mb.toString(),
        "interval_ms" to "250", "samples" to "10",
    )

    private val cpuA = cell(ModelKey.E2B, BackendKind.CPU, Route.A)
    private val cpuB = cell(ModelKey.E2B, BackendKind.CPU, Route.B)
    private val gpuA = cell(ModelKey.E2B, BackendKind.GPU, Route.A)

    @Test
    fun selectorPrefersTheMostCorrectThenTheLowestP50AmongEligibleCells() {
        val screen = trials(cpuA, 3, 900) + trials(cpuB, 4, 1500) + trials(gpuA, 4, 800)
        val mems = listOf(mem(cpuA, 1400), mem(cpuB, 1500), mem(gpuA, 1600))
        assertEquals(gpuA, CellSelector.select(screen, mems))
    }

    @Test
    fun selectorSkipsACellOverThePssBar() {
        val screen = trials(cpuB, 4, 1500) + trials(gpuA, 4, 800)
        val mems = listOf(mem(cpuB, 1500), mem(gpuA, 2100))
        assertEquals(cpuB, CellSelector.select(screen, mems))
    }

    @Test
    fun selectorSkipsACellOverTheP95Bar() {
        val screen = trials(cpuB, 4, 1500) + trials(gpuA, 4, 6000)
        val mems = listOf(mem(cpuB, 1500), mem(gpuA, 1600))
        assertEquals(cpuB, CellSelector.select(screen, mems))
    }

    @Test
    fun selectorFallsBackToTheLowestP50AmongTheMostCorrectWhenNoneIsEligible() {
        val screen = trials(cpuA, 4, 7000) + trials(cpuB, 4, 6000) + trials(gpuA, 2, 100)
        val mems = listOf(mem(cpuA, 1500), mem(cpuB, 1500), mem(gpuA, 2100))
        assertEquals(cpuB, CellSelector.select(screen, mems))
    }

    @Test
    fun selectorBreaksARemainingTieByTheSmallestWire() {
        val screen = trials(cpuB, 4, 1000) + trials(cpuA, 4, 1000)
        val mems = listOf(mem(cpuA, 1500), mem(cpuB, 1500))
        assertEquals(cpuA, CellSelector.select(screen, mems))
    }

    @Test
    fun selectorNeverPicksTheOneBillionModel() {
        val g3 = cell(ModelKey.G3_1B, BackendKind.CPU, Route.A)
        assertEquals(cpuA, CellSelector.select(trials(g3, 4, 10) + trials(cpuA, 1, 4000), listOf(mem(g3, 500), mem(cpuA, 1500))))
        assertNull(CellSelector.select(trials(g3, 4, 10), listOf(mem(g3, 500))))
    }

    @Test
    fun selectorIgnoresTheForcedShape() {
        val forced = Cell(ModelKey.E2B, BackendKind.CPU, Route.A, Shape.FORCED)
        assertEquals(cpuB, CellSelector.select(trials(forced, 4, 10) + trials(cpuB, 1, 1000), listOf(mem(forced, 1500), mem(cpuB, 1500))))
    }
}
