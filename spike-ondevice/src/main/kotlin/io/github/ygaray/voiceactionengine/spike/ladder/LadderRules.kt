package io.github.ygaray.voiceactionengine.spike.ladder

import io.github.ygaray.voiceactionengine.spike.verdict.Thresholds

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

    // RED stubs: the rules are written in the GREEN commit.
    fun initEarlyExit(e2bCpuOk: Boolean, e2bGpuOk: Boolean): String? = null

    fun sbPrefillBound(prefixTokens: Int, bestPrefillTps: Double, kvReuse: String): String? = null

    fun kvReuseDisposition(firstPrefillTokens: Int?, secondPrefillTokens: Int?, firstTtftMs: Long?, secondTtftMs: Long?): String = ""

    fun rfDisposition(onNativeError: Boolean, onInvalid: Int, offInvalid: Int): String = ""
}
