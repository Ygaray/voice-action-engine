package io.github.ygaray.voiceactionengine.spike

import io.github.ygaray.voiceactionengine.spike.ladder.LadderRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The pure ladder rules: early exits, the KV-reuse and ResponseFormat dispositions. */
class LadderRulesTest {
    // ---- init early exit ------------------------------------------------------------------------------------------

    @Test
    fun e2bFailingOnCpuAndGpuIsInitFailedAll() {
        assertEquals("init_failed_all", LadderRules.initEarlyExit(e2bCpuOk = false, e2bGpuOk = false))
    }

    @Test
    fun oneWorkingBackendIsNotAnEarlyExit() {
        assertNull(LadderRules.initEarlyExit(e2bCpuOk = true, e2bGpuOk = false))
        assertNull(LadderRules.initEarlyExit(e2bCpuOk = false, e2bGpuOk = true))
        assertNull(LadderRules.initEarlyExit(e2bCpuOk = true, e2bGpuOk = true))
    }

    // ---- sb prefill bound -----------------------------------------------------------------------------------------

    @Test
    fun aSevenThousandTokenPrefixAtFiveHundredTokensPerSecondIsOverTwiceTheBarWithoutReuse() {
        // 7000 / 500 = 14 s, which is over 2 x the 5 s warm p95 bar.
        assertEquals("sb_prefill_bound", LadderRules.sbPrefillBound(7000, 500.0, "not_reused"))
    }

    @Test
    fun reuseOrAFastBackendOrAnUnknownSpeedIsNotBound() {
        assertNull(LadderRules.sbPrefillBound(7000, 500.0, "reused"))
        assertNull(LadderRules.sbPrefillBound(7000, 2000.0, "not_reused"))
        assertNull(LadderRules.sbPrefillBound(7000, 0.0, "not_reused"))
        assertNull(LadderRules.sbPrefillBound(7000, 500.0, "unobservable"))
    }

    @Test
    fun exactlyTwiceTheBarIsNotOverIt() {
        // 7000 / 700 = 10 s, which is exactly 2 x 5 s: the bound is strictly over.
        assertNull(LadderRules.sbPrefillBound(7000, 700.0, "not_reused"))
        assertEquals("sb_prefill_bound", LadderRules.sbPrefillBound(7000, 699.0, "not_reused"))
    }

    // ---- kv reuse -------------------------------------------------------------------------------------------------

    @Test
    fun aSecondPrefillOfAtMostHalfTheTokensIsReuse() {
        assertEquals("reused", LadderRules.kvReuseDisposition(7000, 300, 7800L, 900L))
        assertEquals("reused", LadderRules.kvReuseDisposition(7000, 3500, null, null))
    }

    @Test
    fun aSecondTimeToFirstTokenOfAtMostHalfIsReuse() {
        assertEquals("reused", LadderRules.kvReuseDisposition(7000, 7000, 7800L, 3000L))
    }

    @Test
    fun bothWithinTwentyPercentIsNotReused() {
        assertEquals("not_reused", LadderRules.kvReuseDisposition(7000, 7000, 7800L, 7700L))
        assertEquals("not_reused", LadderRules.kvReuseDisposition(7000, 6000, 7800L, 9000L))
    }

    @Test
    fun missingBenchFactsAreUnobservableAndSoIsTheAmbiguousMiddle() {
        assertEquals("unobservable", LadderRules.kvReuseDisposition(null, null, null, null))
        assertEquals("unobservable", LadderRules.kvReuseDisposition(7000, 5000, 7800L, 6000L))
    }

    // ---- ResponseFormat enforcement --------------------------------------------------------------------------------

    @Test
    fun aNativeErrorOnTheOnArmIsNativeError() {
        assertEquals("native_error", LadderRules.rfDisposition(onNativeError = true, onInvalid = 3, offInvalid = 3))
    }

    @Test
    fun invalidAnswersWithTheConstraintOnMeanItWasIgnored() {
        assertEquals("ignored", LadderRules.rfDisposition(onNativeError = false, onInvalid = 1, offInvalid = 4))
    }

    @Test
    fun cleanOnAndDirtyOffMeansEnforcedAndCleanBothMeansUnproven() {
        assertEquals("enforced", LadderRules.rfDisposition(onNativeError = false, onInvalid = 0, offInvalid = 2))
        assertEquals("unproven", LadderRules.rfDisposition(onNativeError = false, onInvalid = 0, offInvalid = 0))
    }
}
