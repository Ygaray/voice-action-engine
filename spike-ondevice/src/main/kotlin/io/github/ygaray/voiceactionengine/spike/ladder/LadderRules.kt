package io.github.ygaray.voiceactionengine.spike.ladder

import io.github.ygaray.voiceactionengine.spike.evidence.BackendKind
import io.github.ygaray.voiceactionengine.spike.evidence.Cell
import io.github.ygaray.voiceactionengine.spike.evidence.Envelope
import io.github.ygaray.voiceactionengine.spike.evidence.ModelKey
import io.github.ygaray.voiceactionengine.spike.evidence.Route
import io.github.ygaray.voiceactionengine.spike.evidence.Shape
import io.github.ygaray.voiceactionengine.spike.gold.GoldItem
import io.github.ygaray.voiceactionengine.spike.gold.GoldSet
import io.github.ygaray.voiceactionengine.spike.verdict.Thresholds
import kotlin.math.floor
import kotlin.random.Random

private const val MILLIS_PER_SECOND = 1000.0
private const val MILLIS_PER_SECOND_LONG = 1000L
private const val SB_PREFILL_FACTOR = 2
private const val REUSE_AT_MOST = 0.5
private const val SAME_LOW = 0.8
private const val SAME_HIGH = 1.2

/**
 * The pure rules of the ladder: no clock, no file, no device. The early exits, the dispositions and the planning live here
 * so a TESTER window can be reasoned about, and unit tested, without one.
 */
internal object LadderRules {
    /** The engine context for the small envelope (prefix, tools and output with margin). */
    const val SMALL_MAX_TOKENS = 4096

    /** The engine context for the SB-sized envelope (the prefix is about 7k tokens, Pitfall 2). */
    const val SB_MAX_TOKENS = 8192

    /** Items per screen cell (13-THRESHOLDS `screen_n`). */
    const val SCREEN_N = Thresholds.screenN

    const val INIT_FAILED_ALL = "init_failed_all"
    const val SB_PREFILL_BOUND = "sb_prefill_bound"

    const val REUSED = "reused"
    const val NOT_REUSED = "not_reused"
    const val UNOBSERVABLE = "unobservable"

    const val ENFORCED = "enforced"
    const val IGNORED = "ignored"
    const val UNPROVEN = "unproven"
    const val NATIVE_ERROR = "native_error"

    /** E2B failing to initialize on every backend ends the run: nothing else can be measured. Null when one backend works. */
    fun initEarlyExit(e2bCpuOk: Boolean, e2bGpuOk: Boolean): String? =
        if (!e2bCpuOk && !e2bGpuOk) INIT_FAILED_ALL else null

    /**
     * `sb_prefill_bound` when the SB prefix, prefilled at the best measured speed, takes strictly more than 2 x the warm p95
     * bar and the prefix is not reused between conversations (so every call would pay it). Null otherwise, including when the
     * speed is unknown or KV reuse could not be observed: only a measured `not_reused` is allowed to end the envelope early.
     */
    fun sbPrefillBound(prefixTokens: Int, bestPrefillTps: Double, kvReuse: String): String? {
        if (kvReuse != NOT_REUSED || bestPrefillTps <= 0.0 || prefixTokens <= 0) return null
        val prefillMs = prefixTokens / bestPrefillTps * MILLIS_PER_SECOND
        return if (prefillMs > SB_PREFILL_FACTOR * Thresholds.warmP95Ms) SB_PREFILL_BOUND else null
    }

    /**
     * Whether the second conversation avoided re-prefilling the identical preface. `reused` when the second call's prefill
     * tokens or time to first token are at most half of the first call's; `not_reused` when every measured pair is within
     * plus or minus 20%; `unobservable` when no pair was measured, or the pairs sit between those two.
     */
    fun kvReuseDisposition(firstPrefillTokens: Int?, secondPrefillTokens: Int?, firstTtftMs: Long?, secondTtftMs: Long?): String {
        val ratios = listOfNotNull(
            ratio(firstPrefillTokens?.toDouble(), secondPrefillTokens?.toDouble()),
            ratio(firstTtftMs?.toDouble(), secondTtftMs?.toDouble()),
        )
        return when {
            ratios.isEmpty() -> UNOBSERVABLE
            ratios.any { it <= REUSE_AT_MOST } -> REUSED
            ratios.all { it in SAME_LOW..SAME_HIGH } -> NOT_REUSED
            else -> UNOBSERVABLE
        }
    }

    private fun ratio(first: Double?, second: Double?): Double? =
        if (first == null || second == null || first <= 0.0) null else second / first

    /**
     * The ResponseFormat disposition of one feature, from its ON and OFF arms: `native_error` when the ON arm threw,
     * `ignored` when ON still produced an invalid answer, `enforced` when ON was clean and OFF was not, `unproven` when both
     * were clean (the prompts did not tempt a violation, so nothing is shown).
     */
    fun rfDisposition(onNativeError: Boolean, onInvalid: Int, offInvalid: Int): String = when {
        onNativeError -> NATIVE_ERROR
        onInvalid > 0 -> IGNORED
        offInvalid > 0 -> ENFORCED
        else -> UNPROVEN
    }

    /** The fixed seed of the screen's item order, so every cell and every run sees the same items. */
    const val SCREEN_SEED = 13_013L

    /** The most the in-app cooldown waits for the device to cool to `light` between cells. */
    const val COOLDOWN_CAP_MS = 300_000L

    /** How often the cooldown re-reads the thermal status. */
    const val COOLDOWN_POLL_MS = 5_000L

    /** A THERMAL line is written after every this many trials (a block). */
    const val THERMAL_BLOCK_TRIALS = 10

    /** A sustained run is cut off at this many trials or this much time even if it is not done (it then reads incomplete). */
    const val SUSTAINED_MAX_TRIALS = 1000
    const val SUSTAINED_MAX_MS = 900_000L

    /**
     * The screen cells of [env] (13-THRESHOLDS (l)): E2B on every backend and both routes in the model-chooses shape; the
     * small envelope also screens Gemma 3 1B when it is [g3Present]. A failed GPU init ([gpuOk] false) removes the GPU cells.
     */
    fun screenCells(env: Envelope, g3Present: Boolean, gpuOk: Boolean): List<Cell> {
        val models = if (env == Envelope.SMALL && g3Present) listOf(ModelKey.E2B, ModelKey.G3_1B) else listOf(ModelKey.E2B)
        val backends = if (gpuOk) listOf(BackendKind.CPU, BackendKind.GPU) else listOf(BackendKind.CPU)
        return models.flatMap { model ->
            backends.flatMap { backend -> Route.entries.map { route -> Cell(model, backend, route, Shape.AUTO) } }
        }
    }

    /**
     * [n] distinct items from [items], proportional across the kind and language buckets (largest remainder, every non-empty
     * bucket at least once when [n] allows), picked and ordered by a fixed seed. The same call always gives the same items,
     * so every cell screens the same ones. Never more than the gold has.
     */
    fun seededScreenItems(items: List<GoldItem>, n: Int = SCREEN_N): List<GoldItem> {
        if (n >= items.size) return items.shuffled(Random(SCREEN_SEED))
        val groups = items.groupBy { it.kind to it.lang }.toList()
            .sortedWith(compareBy({ it.first.first.ordinal }, { it.first.second.ordinal }))
        val quotas = groups.map { n.toDouble() * it.second.size / items.size }
        val alloc = quotas.map { floor(it).toInt() }.toMutableList()
        val left = n - alloc.sum()
        quotas.indices.sortedWith(compareByDescending<Int> { quotas[it] - alloc[it] }.thenBy { it }).take(left).forEach { alloc[it]++ }
        if (n >= groups.size) {
            for (i in alloc.indices.filter { alloc[it] == 0 }) {
                val donor = alloc.indices.maxByOrNull { alloc[it] } ?: break
                if (alloc[donor] > 1) {
                    alloc[donor]--
                    alloc[i]++
                }
            }
        }
        val picked = groups.mapIndexed { i, group -> group.second.shuffled(Random(SCREEN_SEED + i)).take(minOf(alloc[i], group.second.size)) }
        return picked.flatten().shuffled(Random(SCREEN_SEED))
    }

    /** What the confirm stage runs: every distinct item once in the model-chooses shape, then the forced subset (informational). */
    class ConfirmPlan(val auto: List<GoldItem>, val forced: List<GoldItem>) {
        val planned: Int get() = auto.size + forced.size
    }

    /** The confirm plan of [gold]: each distinct item once (never repeated to replace a failure), plus its forced subset. */
    fun confirmPlan(gold: GoldSet): ConfirmPlan = ConfirmPlan(gold.items, gold.forcedSubset)

    /** A sustained run is done once it has at least 60 trials and at least 120 s (13-THRESHOLDS (h)). */
    fun sustainedDone(trials: Int, elapsedMs: Long): Boolean =
        trials >= Thresholds.sustainedMinTrials && elapsedMs >= Thresholds.sustainedMinSeconds * MILLIS_PER_SECOND_LONG

    /** True when a sustained run must stop: done, or cut off by the trial or time cap so it cannot run away. */
    fun sustainedStop(trials: Int, elapsedMs: Long): Boolean =
        sustainedDone(trials, elapsedMs) || trials >= SUSTAINED_MAX_TRIALS || elapsedMs >= SUSTAINED_MAX_MS
}
