package io.github.ygaray.voiceactionengine.sample.verdict

import io.github.ygaray.voiceactionengine.core.telemetry.TurnRecord
import kotlin.math.abs

/** The prompt-cache write the 35,464-byte LE-1 prefix is expected to produce on turn 1, in tokens (D-03). */
internal const val CACHE_WRITE_ANCHOR = 7016L

/** How far turn 1's write may sit from the anchor, as a fraction (D-03). */
internal const val CACHE_WRITE_BAND = 0.05

/** How far each later cache read may sit from turn 1's write, as a fraction (D-03). */
internal const val CACHE_READ_PARITY = 0.01

private const val HTTP_OK = 200
private const val MIN_CALLS = 2

/**
 * What the cache classification measured. [turn1Write] and [minRead] are null when there was nothing to measure.
 *
 * @property minRead the smallest cache read among the turns after turn 1.
 */
internal class CacheResult(val verdict: Verdict, val turn1Write: Long?, val minRead: Long?, val calls: Int) {
    /** The measured numbers for the verdict line, in a fixed order; absent numbers are left out. */
    fun extras(): Map<String, Long> {
        val out = LinkedHashMap<String, Long>()
        if (turn1Write != null) out["turn1_write"] = turn1Write
        if (minRead != null) out["min_read"] = minRead
        out["calls"] = calls.toLong()
        return out
    }

    override fun toString(): String = "CacheResult(verdict=$verdict, turn1Write=$turn1Write, minRead=$minRead, calls=$calls)"
}

/**
 * The VER-02 rule: an Anthropic agentic run proves the prompt cache when turn 1 writes the cache and every later turn
 * reads what was written (D-03).
 */
internal object CacheVerdict {
    /**
     * Classifies one run, in this order:
     * 1. [VerdictKind.WARM]: turn 1 already read from the cache, so the run was not cold. An infrastructure re-run,
     *    never a pass or a fail.
     * 2. FAIL on any attempt that was not HTTP 200, or on an outcome that is not a completed one.
     * 3. FAIL `single_turn`: fewer than two provider calls.
     * 4. FAIL `cache_not_engaged`: turn 1 wrote nothing.
     * 5. FAIL `no_cache_read`: a later turn read nothing.
     * 6. FAIL `read_write_parity`: a later read is outside [parity] of turn 1's write.
     * 7. [VerdictKind.OUT_OF_BAND]: turn 1's write is outside [band] of [anchor]. An escalation carrying the measured
     *    value; never a pass, and the band is never widened here to make a measurement fit.
     * 8. PASS.
     *
     * The band's edges are computed from [anchor] and [band], never written as literals.
     */
    fun classify(
        turns: List<TurnRecord>,
        attempts: List<AttemptRecord>,
        outcome: OutcomeSummary,
        anchor: Long = CACHE_WRITE_ANCHOR,
        band: Double = CACHE_WRITE_BAND,
        parity: Double = CACHE_READ_PARITY,
    ): CacheResult {
        val calls = turns.size
        val turn1Write = turns.firstOrNull()?.usage?.cacheWrite
        val laterReads = turns.drop(1).map { it.usage.cacheRead }
        val minRead = laterReads.minOrNull()

        fun result(verdict: Verdict): CacheResult = CacheResult(verdict, turn1Write, minRead, calls)

        val verdict = when {
            (turns.firstOrNull()?.usage?.cacheRead ?: 0L) > 0L -> Verdict(VerdictKind.WARM, "turn1_cache_read")
            else -> failureOrNull(attempts, outcome, calls, turn1Write, laterReads, parity)
                ?: outOfBandOrPass(requireNotNull(turn1Write), anchor, band)
        }
        return result(verdict)
    }

    private fun failureOrNull(
        attempts: List<AttemptRecord>,
        outcome: OutcomeSummary,
        calls: Int,
        turn1Write: Long?,
        laterReads: List<Long>,
        parity: Double,
    ): Verdict? {
        val badAttempt = attempts.firstOrNull { it.httpStatus != HTTP_OK }
        val write = turn1Write ?: 0L
        return when {
            badAttempt != null -> Verdict.fail("http_${badAttempt.httpStatus ?: "none"}")
            !outcome.isCompleted -> Verdict.fail(requireNotNull(outcome.failureCode()))
            calls < MIN_CALLS -> Verdict.fail("single_turn")
            write == 0L -> Verdict.fail("cache_not_engaged")
            laterReads.any { it == 0L } -> Verdict.fail("no_cache_read")
            laterReads.any { abs((it - write).toDouble()) > write * parity } -> Verdict.fail("read_write_parity")
            else -> null
        }
    }

    private fun outOfBandOrPass(write: Long, anchor: Long, band: Double): Verdict {
        val inBand = write >= anchor * (1.0 - band) && write <= anchor * (1.0 + band)
        return if (inBand) Verdict.pass() else Verdict(VerdictKind.OUT_OF_BAND, "turn1_write_out_of_band")
    }
}
