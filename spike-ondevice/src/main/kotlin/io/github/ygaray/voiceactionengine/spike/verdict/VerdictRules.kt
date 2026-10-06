package io.github.ygaray.voiceactionengine.spike.verdict

import io.github.ygaray.voiceactionengine.spike.evidence.Cell
import io.github.ygaray.voiceactionengine.spike.evidence.Envelope
import io.github.ygaray.voiceactionengine.spike.evidence.ItemKind
import io.github.ygaray.voiceactionengine.spike.evidence.Lang
import io.github.ygaray.voiceactionengine.spike.evidence.ModelKey
import io.github.ygaray.voiceactionengine.spike.evidence.Route
import io.github.ygaray.voiceactionengine.spike.evidence.SpikeKind
import io.github.ygaray.voiceactionengine.spike.evidence.SpikeLine
import io.github.ygaray.voiceactionengine.spike.evidence.Stage
import io.github.ygaray.voiceactionengine.spike.evidence.StagePhase
import io.github.ygaray.voiceactionengine.spike.evidence.TrialRecord

private const val NA = "na"
private const val NONE = "none"
private const val P50 = 0.50
private const val P95 = 0.95

/** The rendered verdict: one `SPIKE_VERDICT` line per envelope, the `SPIKE_CONTROL` line and the `SPIKE_VERDICT_META` line. */
internal class VerdictReport(private val lines: List<String>) {
    /** The lines in fixed order: small, sb, control, meta. */
    fun render(): List<String> = lines
}

/** One envelope's reasons and rendered fields, before they are joined into a line. */
private class EnvelopeResult(val envelope: Envelope, val reasons: List<String>, val fields: List<Pair<String, String>>) {
    fun render(): String {
        val verdict = if (reasons.isEmpty()) "green" else "red"
        val head = listOf(
            "envelope" to envelope.wire,
            "verdict" to verdict,
            "reasons" to if (reasons.isEmpty()) NONE else reasons.joinToString(","),
        )
        return "SPIKE_VERDICT " + (head + fields).joinToString(" ") { (key, value) -> "$key=$value" }
    }
}

/** Collects, per metric, whether it failed or has no evidence. */
private class Findings {
    val failed = HashSet<String>()
    val missing = HashSet<String>()
}

/**
 * The per-envelope verdict as a pure function of the evidence lines and [Thresholds] (D-07). Nothing is read from a clock,
 * a file or the environment, so the same lines always give the same report. A gating metric without evidence is red with
 * reason `unmeasured:<metric>`; it is never green and never skipped. Only Gemma 4 E2B cells decide an envelope (D-01).
 *
 * The line shapes read here are the contract with the on-device ladder:
 * TRIAL (see [TrialRecord]); STAGE `stage result trials planned [winner reason seconds]`; INIT `stage cold init_ms pss_mb
 * result`; MEM `stage [cell] peak_pss_mb`; THERMAL `stage status`; EXIT `reason count`; SCHEMAPROBE `feature constraint
 * [disposition]`; GPU `disposition [code]`; KVREUSE `disposition`; TOOLCHAIN `kind result pin`; ENV `thresholds_sha head`.
 */
internal object VerdictRules {
    /** The gating metrics, in the fixed order of the reasons list. */
    val metricOrder: List<String> = listOf(
        "warm_p50",
        "warm_p95",
        "cold",
        "sustained",
        "thermal",
        "peak_pss",
        "process_deaths",
        "schema_valid",
        "semantic_en",
        "semantic_es",
        "false_writes",
        "rf_enforced",
        "rf_enum_nested",
        "gpu_adreno730",
        "route_ab",
    )

    private val deathReasons = setOf(
        "crash", "crash_native", "anr", "low_memory", "signaled", "initialization_failure",
        "excessive_resource_usage", "dependency_died",
    )
    private val thermalOrder = listOf("none", "light", "moderate", "severe", "critical", "emergency", "shutdown")
    private val rfOrder = listOf("native_error", "ignored", "unproven", "enforced")

    /** The metrics that gate [envelope]: the common ones, plus `kv_reuse` for sb only. */
    fun gatingMetrics(envelope: Envelope): List<String> =
        if (envelope == Envelope.SB) metricOrder + "kv_reuse" else metricOrder

    /** Evaluates [lines] (every evidence line of the run, in file order). */
    fun evaluate(lines: List<SpikeLine>): VerdictReport {
        val toolchainRed = isToolchainRed(lines)
        val rendered = ArrayList<String>()
        for (envelope in Envelope.entries) rendered += evaluateEnvelope(envelope, lines, toolchainRed).render()
        rendered += controlLine(lines)
        rendered += metaLine(lines, toolchainRed)
        return VerdictReport(rendered)
    }

    // ---- line helpers --------------------------------------------------------------------------------------------

    private fun SpikeLine.envelopeOf(): Envelope? =
        Envelope.fromWire(this["env"]) ?: Stage.fromWire(this["stage"])?.envelope

    private fun SpikeLine.isStage(stage: Stage): Boolean = kind == SpikeKind.STAGE && this["stage"] == stage.wire

    // A shared stage (init, prefill, ...) concerns both envelopes; an envelope stage only its own.
    private fun SpikeLine.appliesTo(envelope: Envelope): Boolean = envelopeOf().let { it == null || it == envelope }

    private fun isToolchainRed(lines: List<SpikeLine>): Boolean {
        val toolchain = lines.filter { it.kind == SpikeKind.TOOLCHAIN }
        val inputRed = toolchain.any { it["kind"] == "verdict_input" && it["result"] == "red" }
        val compiles = toolchain.filter { it["kind"] == "compile" }
        return inputRed || (compiles.isNotEmpty() && compiles.none { it["result"] == "ok" })
    }

    private fun metaLine(lines: List<SpikeLine>, toolchainRed: Boolean): String {
        val env = lines.firstOrNull { it.kind == SpikeKind.ENV }
        val pin = lines.firstOrNull { it.kind == SpikeKind.TOOLCHAIN && it["kind"] == "compile" && it["result"] == "ok" }
        return "SPIKE_VERDICT_META thresholds_sha=${env?.get("thresholds_sha") ?: NONE} harness_sha=${env?.get("head") ?: NONE} " +
            "toolchain=${if (toolchainRed) "red" else "ok"} pin=${pin?.get("pin") ?: NONE}"
    }

    // ---- the Gemma 3 1B control (never decides an envelope) ------------------------------------------------------

    private fun controlLine(lines: List<SpikeLine>): String {
        val trials = lines.mapNotNull(TrialRecord::fromLine).filter {
            it.env == Envelope.SMALL && it.stage == Stage.SCREEN_SMALL && it.cell.model == ModelKey.G3_1B
        }
        val head = "SPIKE_CONTROL envelope=small model=g3_1b"
        if (trials.isEmpty()) return "$head status=skipped_gated cell=$NONE schema_valid=$NA p50_ms=$NA peak_pss_mb=$NA"
        val mem = lines.filter { it.kind == SpikeKind.MEM && it["stage"] == Stage.SCREEN_SMALL.wire }
        val best = trials.groupBy { it.cell }.map { (cell, group) -> CellStats(cell, group, mem) }
            .sortedWith(compareByDescending<CellStats> { it.schemaValid.toDouble() / it.trials.size }.thenBy { it.p50 }.thenBy { it.cell.wire })
            .first()
        return "$head status=measured cell=${best.cell.wire} schema_valid=${best.schemaValid}/${best.trials.size} " +
            "p50_ms=${best.p50} peak_pss_mb=${best.peakPssMb ?: NA}"
    }

    // ---- one envelope --------------------------------------------------------------------------------------------

    private fun evaluateEnvelope(envelope: Envelope, lines: List<SpikeLine>, toolchainRed: Boolean): EnvelopeResult {
        val findings = Findings()
        val trials = lines.mapNotNull(TrialRecord::fromLine).filter { it.env == envelope }
        val screenStage = Stage.of(envelope, StagePhase.SCREEN)
        val confirmStage = Stage.of(envelope, StagePhase.CONFIRM)
        val sustainedStage = Stage.of(envelope, StagePhase.SUSTAINED)

        val winner = CellSelector.select(
            trials.filter { it.stage == screenStage },
            lines.filter { it.kind == SpikeKind.MEM && it["stage"] == screenStage.wire },
        )
        val claimed = lines.firstOrNull { it.isStage(screenStage) }?.get("winner")
        val inconsistent = claimed != null && claimed != (winner?.wire ?: NONE)

        // Every distinct gold item counts once (definition (c)); the forced shape is another cell and never reaches here.
        val confirm = trials.filter { it.stage == confirmStage && it.cell == winner }.distinctBy { it.item }
        val fields = LinkedHashMap<String, String>()
        fields["cell"] = winner?.wire ?: NONE

        val warmP50 = scoreWarm(confirm, findings, fields)
        scoreCold(lines, confirmStage, confirm, findings, fields)
        scoreSustained(lines, trials.filter { it.stage == sustainedStage && it.cell == winner }, sustainedStage, warmP50, findings, fields)
        scoreThermal(lines, envelope, findings, fields)
        scorePss(lines, envelope, winner, findings, fields)
        scoreDeaths(lines, findings, fields)
        scoreSchema(confirm, findings, fields)
        scoreSemantic(confirm, Lang.EN, "en", "semantic_en", Thresholds.semanticNEnMin, Thresholds.semanticLbEnMin, findings, fields)
        scoreSemantic(confirm, Lang.ES, "es", "semantic_es", Thresholds.semanticNEsMin, Thresholds.semanticLbEsMin, findings, fields)
        scoreFalseWrites(confirm, findings, fields)
        scoreRows(lines, trials.filter { it.stage == screenStage }, findings, fields)

        val reasons = ArrayList<String>()
        if (toolchainRed) reasons += "toolchain"
        reasons += stageReasons(lines, envelope, inconsistent)
        for (metric in gatingMetrics(envelope)) {
            if (metric in findings.failed) {
                reasons += "fail:$metric"
            } else if (metric in findings.missing) {
                reasons += "unmeasured:$metric"
            }
        }
        val order = listOf(
            "cell", "warm_p50_ms", "warm_p95_ms", "cold_ms", "sustained_ratio", "thermal_max", "peak_pss_mb", "deaths",
            "schema_valid", "en", "en_lb", "es", "es_lb", "false_writes", "kv_reuse", "rf_enforced", "rf_enum_nested",
            "gpu_adreno730", "route_ab",
        )
        return EnvelopeResult(envelope, reasons, order.map { it to (fields[it] ?: NA) })
    }

    /** Early exits, incomplete stages and a winner that does not recompute, in that order. */
    private fun stageReasons(lines: List<SpikeLine>, envelope: Envelope, inconsistent: Boolean): List<String> {
        val stages = lines.filter { it.kind == SpikeKind.STAGE && it.appliesTo(envelope) }
        val reasons = ArrayList<String>()
        for (line in stages.filter { it["result"] == "early_exit" }) {
            val reason = "early_exit:${line["reason"] ?: "unspecified"}"
            if (reason !in reasons) reasons += reason
        }
        val incomplete = stages.any { line ->
            val result = line["result"]
            val trials = line["trials"]?.toIntOrNull()
            val planned = line["planned"]?.toIntOrNull()
            result != "early_exit" && result != "skipped" &&
                (result == "error" || (trials != null && planned != null && trials < planned))
        }
        if (incomplete) reasons += "fail:incomplete_stage"
        if (inconsistent) reasons += "inconsistent:winner"
        return reasons
    }

    // ---- metrics -------------------------------------------------------------------------------------------------

    private fun scoreWarm(confirm: List<TrialRecord>, findings: Findings, fields: MutableMap<String, String>): Long? {
        val warm = confirm.filter { !it.firstInProcess }.map { it.latencyMs }
        if (warm.isEmpty()) {
            findings.missing += "warm_p50"
            findings.missing += "warm_p95"
            return null
        }
        val p50 = percentileNearestRank(warm, P50)
        val p95 = percentileNearestRank(warm, P95)
        fields["warm_p50_ms"] = p50.toString()
        fields["warm_p95_ms"] = p95.toString()
        if (p50 > Thresholds.warmP50Ms) findings.failed += "warm_p50"
        if (p95 > Thresholds.warmP95Ms) findings.failed += "warm_p95"
        return p50
    }

    private fun scoreCold(
        lines: List<SpikeLine>,
        confirmStage: Stage,
        confirm: List<TrialRecord>,
        findings: Findings,
        fields: MutableMap<String, String>,
    ) {
        val init = lines.firstOrNull {
            it.kind == SpikeKind.INIT && it["stage"] == confirmStage.wire && it["cold"] == "process" &&
                (it["result"] == null || it["result"] == "ok")
        }
        val initMs = init?.get("init_ms")?.toLongOrNull()
        val first = confirm.firstOrNull { it.firstInProcess }
        if (initMs == null || first == null) {
            findings.missing += "cold"
            return
        }
        val cold = initMs + first.latencyMs
        fields["cold_ms"] = cold.toString()
        if (cold > Thresholds.coldMs) findings.failed += "cold"
    }

    private fun scoreSustained(
        lines: List<SpikeLine>,
        sustained: List<TrialRecord>,
        sustainedStage: Stage,
        warmP50: Long?,
        findings: Findings,
        fields: MutableMap<String, String>,
    ) {
        val seconds = lines.firstOrNull { it.isStage(sustainedStage) }?.get("seconds")?.toLongOrNull()
        val enough = sustained.size >= Thresholds.sustainedMinTrials && seconds != null && seconds >= Thresholds.sustainedMinSeconds
        if (!enough || warmP50 == null || warmP50 <= 0L) {
            findings.missing += "sustained"
            return
        }
        val ratio = percentileNearestRank(sustained.map { it.latencyMs }, P50).toDouble() / warmP50
        fields["sustained_ratio"] = round2(ratio)
        if (ratio > Thresholds.sustainedP50RatioMax) findings.failed += "sustained"
    }

    private fun scoreThermal(lines: List<SpikeLine>, envelope: Envelope, findings: Findings, fields: MutableMap<String, String>) {
        val statuses = lines.filter { it.kind == SpikeKind.THERMAL && it.envelopeOf() == envelope }.mapNotNull { it["status"] }
        if (statuses.isEmpty()) {
            findings.missing += "thermal"
            return
        }
        // A word this code does not know ranks above every known one, so it can only turn the envelope red.
        fun rank(status: String): Int = thermalOrder.indexOf(status).let { if (it < 0) thermalOrder.size else it }
        val worst = statuses.maxBy(::rank)
        fields["thermal_max"] = if (rank(worst) >= thermalOrder.size) "unknown" else worst
        if (rank(worst) >= thermalOrder.indexOf(Thresholds.thermalRedAt)) findings.failed += "thermal"
    }

    private fun scorePss(
        lines: List<SpikeLine>,
        envelope: Envelope,
        winner: Cell?,
        findings: Findings,
        fields: MutableMap<String, String>,
    ) {
        // The envelope's stages on the winning cell: a losing screen cell above the bar does not decide the envelope.
        val peaks = lines.filter { it.envelopeOf() == envelope && (it["cell"] == null || it["cell"] == winner?.wire) }
            .mapNotNull { line ->
                when (line.kind) {
                    SpikeKind.MEM -> line["peak_pss_mb"]?.toIntOrNull()
                    SpikeKind.INIT -> line["pss_mb"]?.toIntOrNull()
                    else -> null
                }
            }
        val peak = peaks.maxOrNull()
        if (peak == null) {
            findings.missing += "peak_pss"
            return
        }
        fields["peak_pss_mb"] = peak.toString()
        if (peak > Thresholds.peakPssMbMax) findings.failed += "peak_pss"
    }

    private fun scoreDeaths(lines: List<SpikeLine>, findings: Findings, fields: MutableMap<String, String>) {
        val stage = lines.firstOrNull { it.isStage(Stage.EXIT_REASONS) }
        if (stage == null || stage["result"] != "done") {
            findings.missing += "process_deaths"
            return
        }
        val deaths = lines.filter { it.kind == SpikeKind.EXIT && it["reason"] in deathReasons }
            .sumOf { it["count"]?.toIntOrNull() ?: 1 }
        fields["deaths"] = deaths.toString()
        if (deaths > Thresholds.processDeathsMax) findings.failed += "process_deaths"
    }

    private fun scoreSchema(confirm: List<TrialRecord>, findings: Findings, fields: MutableMap<String, String>) {
        val n = confirm.size
        if (n < Thresholds.schemaValidNMin) {
            findings.missing += "schema_valid"
            return
        }
        val k = confirm.count { it.schemaValid }
        fields["schema_valid"] = "$k/$n"
        if (k.toDouble() / n < Thresholds.schemaValidMin) findings.failed += "schema_valid"
    }

    private fun scoreSemantic(
        confirm: List<TrialRecord>,
        lang: Lang,
        key: String,
        metric: String,
        nMin: Int,
        lbMin: Double,
        findings: Findings,
        fields: MutableMap<String, String>,
    ) {
        val items = confirm.filter { it.kind == ItemKind.POS && it.lang == lang }
        if (items.size < nMin) {
            findings.missing += metric
            return
        }
        val k = items.count { it.correct }
        val lowerBound = wilsonLowerBound(k, items.size)
        fields[key] = "$k/${items.size}"
        fields["${key}_lb"] = round3(lowerBound)
        if (lowerBound < lbMin) findings.failed += metric
    }

    private fun scoreFalseWrites(confirm: List<TrialRecord>, findings: Findings, fields: MutableMap<String, String>) {
        val negatives = confirm.filter { it.kind == ItemKind.NEG }
        if (negatives.size < Thresholds.negativesNMin) {
            findings.missing += "false_writes"
            return
        }
        val writes = negatives.count { it.falseWrite }
        fields["false_writes"] = "$writes/${negatives.size}"
        if (writes > Thresholds.falseWritesMax) findings.failed += "false_writes"
    }

    // ---- the D-10 rows -------------------------------------------------------------------------------------------

    private fun scoreRows(
        lines: List<SpikeLine>,
        screenTrials: List<TrialRecord>,
        findings: Findings,
        fields: MutableMap<String, String>,
    ) {
        fun probe(feature: String): String? = lines.lastOrNull {
            it.kind == SpikeKind.SCHEMAPROBE && it["feature"] == feature && it["constraint"] == "on" && it["disposition"] != null
        }?.get("disposition")

        val flat = probe("flat")
        if (flat == null) findings.missing += "rf_enforced" else fields["rf_enforced"] = flat

        val enumNested = listOf(probe("enum"), probe("nested"))
        if (enumNested.any { it == null }) {
            findings.missing += "rf_enum_nested"
        } else {
            fields["rf_enum_nested"] = enumNested.filterNotNull().minBy { rfOrder.indexOf(it) }
        }

        val gpu = lines.lastOrNull { it.kind == SpikeKind.GPU && it["disposition"] != null }
        if (gpu == null) {
            findings.missing += "gpu_adreno730"
        } else {
            val code = gpu["code"]
            fields["gpu_adreno730"] = gpu["disposition"] + if (gpu["disposition"] == "gpu_init_failed" && code != null) ":$code" else ""
        }

        val e2b = screenTrials.filter { it.cell.model == ModelKey.E2B }
        val byRoute = Route.entries.associateWith { route -> e2b.filter { it.cell.route == route } }
        if (byRoute.values.any { it.isEmpty() }) {
            findings.missing += "route_ab"
        } else {
            fields["route_ab"] = Route.entries.joinToString(",") { route ->
                val group = byRoute.getValue(route)
                "${route.wire}:${group.count { it.schemaValid }}/${group.size}:${percentileNearestRank(group.map { it.latencyMs }, P50)}"
            }
        }

        val dispositions = lines.filter { it.kind == SpikeKind.KVREUSE }.mapNotNull { it["disposition"] }
        if (dispositions.isEmpty()) {
            findings.missing += "kv_reuse"
        } else {
            fields["kv_reuse"] = listOf("reused", "not_reused").firstOrNull { it in dispositions } ?: "unobservable"
        }
    }
}
