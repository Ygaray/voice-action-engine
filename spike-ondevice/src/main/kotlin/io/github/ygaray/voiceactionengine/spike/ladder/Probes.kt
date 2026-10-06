package io.github.ygaray.voiceactionengine.spike.ladder

import io.github.ygaray.voiceactionengine.spike.verdict.Thresholds
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield

/** The device facts the app can read about itself (preflight, source=app). */
internal class DeviceFacts(
    val memTotalMb: Long,
    val memAvailMb: Long,
    val storageFreeMb: Long,
    val abis: List<String>,
    val pageSize: Long,
    val openClPresent: Boolean,
    val batteryPct: Int,
)

/**
 * Everything the ladder reads from the device or the clock, behind one seam so the ladder is JVM-tested with a fake. The
 * Android implementation is `AndroidProbes`. Nothing here returns content: a number, a closed status word or a closed
 * exit-reason word.
 */
internal interface Probes {
    /** The process's total PSS in MB, from `Debug.getMemoryInfo` (13-THRESHOLDS (f)). */
    fun pssMb(): Int

    /** The thermal status word: none, light, moderate, severe, critical, emergency or shutdown. */
    fun thermalStatus(): String

    /** One closed reason word per process exit of this package at or after [sinceEpochMs] (`ApplicationExitInfo`). */
    fun exitReasons(sinceEpochMs: Long): List<String>

    fun deviceFacts(): DeviceFacts

    /** A monotonic clock in milliseconds; every latency and every elapsed time comes from it. */
    fun monotonicMs(): Long

    /** Wall-clock epoch milliseconds (only for the prepare stamp and the exit-reason cutoff). */
    fun epochMs(): Long

    /** Waits [ms] milliseconds; a fake advances its clock instead of sleeping. */
    suspend fun pause(ms: Long)
}

/** The peak PSS and sample count a [PssSampler] saw. */
internal class PssReading(val peakMb: Int, val samples: Int)

/**
 * Samples [Probes.pssMb] every [intervalMs] while a block runs, and once at its start and once at its end, so a block
 * shorter than one interval still has two readings. The peak is the largest reading (13-THRESHOLDS (f)).
 */
internal class PssSampler(private val probes: Probes, private val intervalMs: Long = Thresholds.pssIntervalMs.toLong()) {
    private var peak = 0
    private var count = 0

    @Synchronized
    private fun record() {
        peak = maxOf(peak, probes.pssMb())
        count++
    }

    /** Runs [block] with sampling around it and returns its result together with the reading. */
    suspend fun <T> around(block: suspend () -> T): Pair<T, PssReading> = coroutineScope {
        record()
        val sampler = launch {
            while (isActive) {
                probes.pause(intervalMs)
                record()
                yield()
            }
        }
        try {
            val result = block()
            record()
            result to PssReading(peak, count)
        } finally {
            sampler.cancel()
        }
    }
}
