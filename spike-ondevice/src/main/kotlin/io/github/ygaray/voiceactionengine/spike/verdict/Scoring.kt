package io.github.ygaray.voiceactionengine.spike.verdict

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.ceil
import kotlin.math.sqrt

private const val RATE_DECIMALS = 3
private const val RATIO_DECIMALS = 2
private const val RANK_EPSILON = 1e-9

/**
 * The Wilson score interval's lower bound for [successes] out of [n] (closed form). D-06 scores semantic accuracy with
 * this at z = 1.959964 (95%): at N=50 it needs 48 of 50 for 0.85 and 46 of 50 for 0.80.
 */
internal fun wilsonLowerBound(successes: Int, n: Int, z: Double = Thresholds.wilsonZ): Double {
    require(n > 0) { "n must be positive" }
    require(successes in 0..n) { "successes must be within 0..n" }
    val p = successes.toDouble() / n
    val d = 1.0 + z * z / n
    val c = p + z * z / (2.0 * n)
    val a = z * sqrt(p * (1.0 - p) / n + z * z / (4.0 * n * n))
    return (c - a) / d
}

/**
 * The nearest-rank percentile: the value at rank ceil(p * n) of the sorted [values], for p in (0, 1]. Requires a
 * non-empty list; a percentile of nothing is never invented.
 */
internal fun percentileNearestRank(values: List<Long>, p: Double): Long {
    require(values.isNotEmpty()) { "percentile of an empty list" }
    require(p > 0.0 && p <= 1.0) { "p must be in (0, 1]" }
    val sorted = values.sorted()
    val rank = ceil(p * sorted.size - RANK_EPSILON).toInt().coerceIn(1, sorted.size)
    return sorted[rank - 1]
}

/** A rate or lower bound to 3 decimals, half up, in plain notation, so rendering is deterministic. */
internal fun round3(value: Double): String =
    BigDecimal(value).setScale(RATE_DECIMALS, RoundingMode.HALF_UP).toPlainString()

/** A ratio to 2 decimals, half up, in plain notation. */
internal fun round2(value: Double): String =
    BigDecimal(value).setScale(RATIO_DECIMALS, RoundingMode.HALF_UP).toPlainString()
