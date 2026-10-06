package io.github.ygaray.voiceactionengine.spike.verdict

import io.github.ygaray.voiceactionengine.spike.evidence.Cell
import io.github.ygaray.voiceactionengine.spike.evidence.ItemKind
import io.github.ygaray.voiceactionengine.spike.evidence.ModelKey
import io.github.ygaray.voiceactionengine.spike.evidence.Shape
import io.github.ygaray.voiceactionengine.spike.evidence.SpikeKind
import io.github.ygaray.voiceactionengine.spike.evidence.SpikeLine
import io.github.ygaray.voiceactionengine.spike.evidence.TrialRecord

private const val P50 = 0.50
private const val P95 = 0.95

/** What the screen stage measured for one cell. */
internal class CellStats(val cell: Cell, val trials: List<TrialRecord>, memLines: List<SpikeLine>) {
    // Warm means every trial after the first in a process; fall back to all trials if that leaves none.
    private val warm: List<TrialRecord> = trials.filter { !it.firstInProcess }.ifEmpty { trials }
    val p50: Long = percentileNearestRank(warm.map { it.latencyMs }, P50)
    val p95: Long = percentileNearestRank(warm.map { it.latencyMs }, P95)
    val schemaValid: Int = trials.count { it.schemaValid }

    /** Correct positives over positives (0 with none). */
    val semantic: Double = trials.filter { it.kind == ItemKind.POS }.let { pos ->
        if (pos.isEmpty()) 0.0 else pos.count { it.correct }.toDouble() / pos.size
    }

    /** The cell's peak PSS in MB over the MEM lines that name it, or null when none does. */
    val peakPssMb: Int? = memLines
        .filter { it.kind == SpikeKind.MEM && it["cell"] == cell.wire }
        .mapNotNull { it["peak_pss_mb"]?.toIntOrNull() }
        .maxOrNull()
}

/** The winning-cell rule of 13-THRESHOLDS.md (l). */
internal object CellSelector {
    /**
     * The winner of the screen stage, or null when no E2B cell has a trial. Only E2B cells in the gating (auto) shape are
     * eligible (D-01, definition (k)). Eligible means p95 at or under the warm p95 bar and a known peak PSS at or under
     * the PSS bar. Among eligible cells pick the most semantic-correct, then the lowest p50, then the smallest cell wire;
     * if none is eligible apply the same order over every E2B cell (it goes red at confirm).
     */
    fun select(screenTrials: List<TrialRecord>, memLines: List<SpikeLine>): Cell? {
        val stats = screenTrials
            .filter { it.cell.model == ModelKey.E2B && it.cell.shape == Shape.AUTO }
            .groupBy { it.cell }
            .map { (cell, trials) -> CellStats(cell, trials, memLines) }
        if (stats.isEmpty()) return null
        val eligible = stats.filter { stat ->
            stat.p95 <= Thresholds.warmP95Ms && (stat.peakPssMb ?: Int.MAX_VALUE) <= Thresholds.peakPssMbMax
        }
        val order = compareByDescending<CellStats> { it.semantic }.thenBy { it.p50 }.thenBy { it.cell.wire }
        return (eligible.ifEmpty { stats }).sortedWith(order).first().cell
    }
}
