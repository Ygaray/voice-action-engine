package io.github.ygaray.voiceactionengine.spike

import io.github.ygaray.voiceactionengine.spike.evidence.BackendKind
import io.github.ygaray.voiceactionengine.spike.evidence.Envelope
import io.github.ygaray.voiceactionengine.spike.evidence.ItemKind
import io.github.ygaray.voiceactionengine.spike.evidence.Lang
import io.github.ygaray.voiceactionengine.spike.evidence.ModelKey
import io.github.ygaray.voiceactionengine.spike.evidence.Route
import io.github.ygaray.voiceactionengine.spike.evidence.Shape
import io.github.ygaray.voiceactionengine.spike.gold.GoldItem
import io.github.ygaray.voiceactionengine.spike.gold.GoldSet
import io.github.ygaray.voiceactionengine.spike.ladder.LadderRules
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    // ---- planning (Task 3) ----------------------------------------------------------------------------------------

    private fun goldOf(posEn: Int, posEs: Int, negEn: Int, negEs: Int, forced: Int = 0): GoldSet {
        var n = 0
        fun item(lang: Lang, kind: ItemKind, forcedFlag: Boolean) = GoldItem(
            "g_%03d".format(++n), lang, kind, "t$n", if (kind == ItemKind.POS) "create_item" else null, JsonObject(emptyMap()), forcedFlag,
        )
        val items = List(posEn) { item(Lang.EN, ItemKind.POS, it < forced) } +
            List(posEs) { item(Lang.ES, ItemKind.POS, false) } +
            List(negEn) { item(Lang.EN, ItemKind.NEG, false) } +
            List(negEs) { item(Lang.ES, ItemKind.NEG, false) }
        return GoldSet(Envelope.SMALL, null, items)
    }

    @Test
    fun theSmallEnvelopeScreensEightCellsWhenTheControlIsPlacedAndTheGpuWorks() {
        val cells = LadderRules.screenCells(Envelope.SMALL, g3Present = true, gpuOk = true)

        assertEquals(8, cells.size)
        assertTrue(cells.all { it.shape == Shape.AUTO })
        assertEquals(setOf(ModelKey.E2B, ModelKey.G3_1B), cells.map { it.model }.toSet())
        assertEquals(setOf(BackendKind.CPU, BackendKind.GPU), cells.map { it.backend }.toSet())
        assertEquals(setOf(Route.A, Route.B), cells.map { it.route }.toSet())
        assertEquals(8, cells.toSet().size)
    }

    @Test
    fun theSbEnvelopeScreensFourE2bCellsOnlyAndAFailedGpuRemovesTheGpuCells() {
        val sb = LadderRules.screenCells(Envelope.SB, g3Present = true, gpuOk = true)
        assertEquals(4, sb.size)
        assertTrue(sb.all { it.model == ModelKey.E2B })

        val noGpu = LadderRules.screenCells(Envelope.SMALL, g3Present = true, gpuOk = false)
        assertEquals(4, noGpu.size)
        assertTrue(noGpu.none { it.backend == BackendKind.GPU })
        assertEquals(2, LadderRules.screenCells(Envelope.SMALL, g3Present = false, gpuOk = false).size)
    }

    @Test
    fun theScreenPicksTwentyDistinctItemsCoveringEveryBucketProportionally() {
        val gold = goldOf(posEn = 55, posEs = 55, negEn = 17, negEs = 17)

        val picked = LadderRules.seededScreenItems(gold.items)

        assertEquals(20, picked.size)
        assertEquals(20, picked.map { it.id }.toSet().size)
        assertEquals(8, picked.count { it.kind == ItemKind.POS && it.lang == Lang.EN })
        assertEquals(8, picked.count { it.kind == ItemKind.POS && it.lang == Lang.ES })
        assertEquals(2, picked.count { it.kind == ItemKind.NEG && it.lang == Lang.EN })
        assertEquals(2, picked.count { it.kind == ItemKind.NEG && it.lang == Lang.ES })
    }

    @Test
    fun theScreenSelectionIsTheSameEveryTimeAndNeverMoreThanTheGoldHas() {
        val gold = goldOf(posEn = 55, posEs = 55, negEn = 17, negEs = 17)

        assertEquals(LadderRules.seededScreenItems(gold.items).map { it.id }, LadderRules.seededScreenItems(gold.items).map { it.id })
        assertEquals(5, LadderRules.seededScreenItems(goldOf(2, 1, 1, 1).items, 20).size)
    }

    @Test
    fun confirmRunsEveryDistinctItemOnceThenTheForcedSubset() {
        val gold = goldOf(posEn = 30, posEs = 30, negEn = 15, negEs = 15, forced = 20)

        val plan = LadderRules.confirmPlan(gold)

        assertEquals(90, plan.auto.size)
        assertEquals(plan.auto.size, plan.auto.map { it.id }.toSet().size)
        assertEquals(20, plan.forced.size)
        assertTrue(plan.forced.all { it.forcedSubset && it.kind == ItemKind.POS })
        assertEquals(110, plan.planned)
    }

    @Test
    fun sustainedNeedsSixtyTrialsAndOneHundredTwentySeconds() {
        assertFalse(LadderRules.sustainedDone(59, 200_000L))
        assertFalse(LadderRules.sustainedDone(60, 119_999L))
        assertTrue(LadderRules.sustainedDone(60, 120_000L))
        assertTrue(LadderRules.sustainedDone(500, 300_000L))
    }
}
