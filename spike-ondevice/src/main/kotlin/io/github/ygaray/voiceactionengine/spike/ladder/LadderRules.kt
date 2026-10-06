package io.github.ygaray.voiceactionengine.spike.ladder

import io.github.ygaray.voiceactionengine.spike.evidence.Cell
import io.github.ygaray.voiceactionengine.spike.evidence.Envelope
import io.github.ygaray.voiceactionengine.spike.gold.GoldItem
import io.github.ygaray.voiceactionengine.spike.gold.GoldSet
import io.github.ygaray.voiceactionengine.spike.verdict.Thresholds

private const val MILLIS_PER_SECOND = 1000.0
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

    // RED stubs: the planning rules are written in the GREEN commit.
    const val SCREEN_SEED = 13_013L

    fun screenCells(env: Envelope, g3Present: Boolean, gpuOk: Boolean): List<Cell> = emptyList()

    fun seededScreenItems(items: List<GoldItem>, n: Int = SCREEN_N): List<GoldItem> = emptyList()

    /** What the confirm stage runs: every distinct item once in the model-chooses shape, then the forced subset. */
    class ConfirmPlan(val auto: List<GoldItem>, val forced: List<GoldItem>) {
        val planned: Int get() = 0
    }

    fun confirmPlan(gold: GoldSet): ConfirmPlan = ConfirmPlan(emptyList(), emptyList())

    fun sustainedDone(trials: Int, elapsedMs: Long): Boolean = false
}
