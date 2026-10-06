package io.github.ygaray.voiceactionengine.spike.verdict

import io.github.ygaray.voiceactionengine.spike.evidence.Envelope
import io.github.ygaray.voiceactionengine.spike.evidence.SpikeKind
import io.github.ygaray.voiceactionengine.spike.evidence.SpikeLine

private const val NA = "na"
private const val NONE = "none"

/** The rendered verdict: one `SPIKE_VERDICT` line per envelope, the `SPIKE_CONTROL` line and the `SPIKE_VERDICT_META` line. */
internal class VerdictReport(private val lines: List<String>) {
    /** The lines in fixed order: small, sb, control, meta. */
    fun render(): List<String> = lines
}

/**
 * The per-envelope verdict as a pure function of the evidence lines and [Thresholds] (D-07). Nothing is read from a clock,
 * a file or the environment, so the same lines always give the same report. A gating metric without evidence is red with
 * reason `unmeasured:<metric>`; it is never green and never skipped.
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

    /** The metrics that gate [envelope]: the common ones, plus `kv_reuse` for sb only. */
    fun gatingMetrics(envelope: Envelope): List<String> =
        if (envelope == Envelope.SB) metricOrder + "kv_reuse" else metricOrder

    /** Evaluates [lines] (every evidence line of the run, in file order). */
    fun evaluate(lines: List<SpikeLine>): VerdictReport {
        val toolchainRed = lines.any {
            it.kind == SpikeKind.TOOLCHAIN && it["kind"] == "verdict_input" && it["result"] == "red"
        }
        val rendered = ArrayList<String>()
        for (envelope in Envelope.entries) {
            val reasons = ArrayList<String>()
            if (toolchainRed) reasons += "toolchain"
            for (metric in gatingMetrics(envelope)) reasons += "unmeasured:$metric"
            rendered += renderVerdict(envelope, reasons)
        }
        rendered += "SPIKE_CONTROL envelope=small model=g3_1b status=skipped_gated cell=$NONE schema_valid=$NA " +
            "p50_ms=$NA peak_pss_mb=$NA"
        val thresholdsSha = lines.firstOrNull { it.kind == SpikeKind.ENV }?.get("thresholds_sha") ?: NONE
        val harnessSha = lines.firstOrNull { it.kind == SpikeKind.ENV }?.get("head") ?: NONE
        val pin = lines.firstOrNull {
            it.kind == SpikeKind.TOOLCHAIN && it["kind"] == "compile" && it["result"] == "ok"
        }?.get("pin") ?: NONE
        rendered += "SPIKE_VERDICT_META thresholds_sha=$thresholdsSha harness_sha=$harnessSha " +
            "toolchain=${if (toolchainRed) "red" else "ok"} pin=$pin"
        return VerdictReport(rendered)
    }

    private fun renderVerdict(envelope: Envelope, reasons: List<String>): String {
        val verdict = if (reasons.isEmpty()) "green" else "red"
        val reasonText = if (reasons.isEmpty()) NONE else reasons.joinToString(",")
        val unmeasured = listOf(
            "warm_p50_ms", "warm_p95_ms", "cold_ms", "sustained_ratio", "thermal_max", "peak_pss_mb", "deaths",
            "schema_valid", "en", "en_lb", "es", "es_lb", "false_writes", "kv_reuse", "rf_enforced", "rf_enum_nested",
            "gpu_adreno730", "route_ab",
        ).joinToString(" ") { "$it=$NA" }
        return "SPIKE_VERDICT envelope=${envelope.wire} verdict=$verdict reasons=$reasonText cell=$NONE $unmeasured"
    }
}
