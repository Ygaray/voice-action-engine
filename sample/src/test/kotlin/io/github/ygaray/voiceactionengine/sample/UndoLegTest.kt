package io.github.ygaray.voiceactionengine.sample

import io.github.ygaray.voiceactionengine.core.testing.NoNetworkGuard
import io.github.ygaray.voiceactionengine.sample.evidence.ALLOW_PATTERN
import io.github.ygaray.voiceactionengine.sample.evidence.LegId
import io.github.ygaray.voiceactionengine.core.commit.CommitProposal
import io.github.ygaray.voiceactionengine.core.commit.GateDecision
import io.github.ygaray.voiceactionengine.core.commit.PreApplyGate
import io.github.ygaray.voiceactionengine.sample.legs.AdmitAllGate
import io.github.ygaray.voiceactionengine.sample.legs.CaseCounts
import io.github.ygaray.voiceactionengine.sample.legs.HoldNthGate
import io.github.ygaray.voiceactionengine.sample.legs.UndoLeg
import io.github.ygaray.voiceactionengine.sample.legs.UndoPlans
import io.github.ygaray.voiceactionengine.sample.legs.UndoStep
import io.github.ygaray.voiceactionengine.sample.legs.UndoVerdicts
import io.github.ygaray.voiceactionengine.sample.legs.UndoWorld
import io.github.ygaray.voiceactionengine.sample.verdict.Verdict
import io.github.ygaray.voiceactionengine.sample.verdict.VerdictKind
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** The offline `undo_all` leg run through the real runner: no provider, no key, no budget, no network. */
class UndoLegTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val allow = Regex(ALLOW_PATTERN)

    // The undo leg has its own engine, so the rig needs no fake provider at all.
    private fun rig() = legRig(folder.newFolder()) { emptyList() }

    @Test
    fun theFirstPressCountsThreeAppliedActionsAndWaitsWithoutAVerdict() = runTest {
        NoNetworkGuard.during {
            val rig = rig()

            val result = rig.runner.run(LegId.UNDO_ALL)

            assertNotNull(result.undo)
            assertEquals(3, result.undo?.n)
            assertEquals(0, result.undo?.pending)
            assertTrue(rig.sink.starting("VAE_VERDICT ").isEmpty())
            val lines = rig.sink.starting("VAE_UNDO ")
            assertEquals(lines.toString(), 1, lines.size)
            assertEquals(
                "VAE_UNDO leg=undo_all case=1 phase=counted n=3 committed=3 pending=0 withheld=false partial=false " +
                    "remaining=0 result=none restored=none blockers=none reason=none store_ok=none",
                lines.single(),
            )
            // The leg needs no key and no budget: nothing was recorded against the spend guard.
            assertEquals(0, rig.budget.runsOf("undo_all"))
            assertEquals(0, rig.budget.snapshot().core)
        }
    }

    @Test
    fun theUndoPressRestoresTheStoreAndPasses() = runTest {
        NoNetworkGuard.during {
            val rig = rig()
            rig.runner.run(LegId.UNDO_ALL)

            val result = rig.runner.undoAll()

            assertEquals(result.toString(), VerdictKind.PASS, result.verdict.kind)
            assertNull(result.undo)
            val undone = rig.sink.starting("VAE_UNDO ").first { it.contains(" phase=undone ") }
            assertTrue(
                undone,
                undone.endsWith(" result=complete restored=3 blockers=none reason=none store_ok=true"),
            )
            val verdicts = rig.sink.starting("VAE_VERDICT ")
            assertEquals(verdicts.toString(), 1, verdicts.size)
            assertTrue(verdicts.single(), verdicts.single().startsWith("VAE_VERDICT leg=undo_all verdict=PASS n=3 restored=3 "))
        }
    }

    @Test
    fun anUndoPressWithNothingWaitingIsRefused() = runTest {
        NoNetworkGuard.during {
            val rig = rig()

            val result = rig.runner.undoAll()

            assertEquals(VerdictKind.REFUSED, result.verdict.kind)
            assertEquals("not_awaiting_undo", result.verdict.reason)
            assertEquals(
                "VAE_VERDICT leg=undo_all verdict=REFUSED reason=not_awaiting_undo trigger=ui",
                rig.sink.starting("VAE_VERDICT ").single(),
            )
            assertTrue(rig.sink.starting("VAE_UNDO ").isEmpty())
        }
    }

    @Test
    fun theSecondUndoPressAfterAFinishedUndoIsRefused() = runTest {
        NoNetworkGuard.during {
            val rig = rig()
            rig.runner.run(LegId.UNDO_ALL)
            rig.runner.undoAll()

            val again = rig.runner.undoAll()

            assertEquals(VerdictKind.REFUSED, again.verdict.kind)
            assertEquals("not_awaiting_undo", again.verdict.reason)
        }
    }

    @Test
    fun aLaterWriteBetweenThePressesRefusesTheUndoAndFailsTheLeg() = runTest {
        NoNetworkGuard.during {
            val rig = rig()
            val session = UndoLeg.start(rig.sink, LegId.UNDO_ALL)
            session.whole.editSeededItem()

            val finished = session.finish(rig.sink)

            assertEquals(VerdictKind.FAIL, finished.verdict.kind)
            assertEquals("undo_incomplete", finished.verdict.reason)
            val undone = rig.sink.starting("VAE_UNDO ").first { it.contains(" phase=undone ") }
            assertTrue(undone, undone.contains(" result=refused "))
            assertTrue(undone, undone.contains(" reason=changed_since "))
        }
    }

    @Test
    fun noLineCarriesAnItemIdATitleOrAReference() = runTest {
        NoNetworkGuard.during {
            val rig = rig()
            rig.runner.run(LegId.UNDO_ALL)
            rig.runner.undoAll()

            val text = rig.sink.rendered.joinToString("\n").lowercase()
            for (leaked in listOf("item-", "seeded", "first", "second", "renamed", "stepone", "\$", "undo_call")) {
                assertFalse("$leaked in $text", text.contains(leaked))
            }
            for (line in rig.sink.rendered) assertTrue(line, allow.matches(line))
        }
    }

    @Test
    fun aLaterEditRefusesTheUndoWithChangedSinceAndTheStoreIsUntouched() = runTest {
        NoNetworkGuard.during {
            val rig = rig()
            rig.runner.run(LegId.UNDO_ALL)

            rig.runner.undoAll()

            val refused = rig.sink.starting("VAE_UNDO ").single { it.contains(" case=2 ") }
            assertEquals(
                "VAE_UNDO leg=undo_all case=2 phase=refused n=1 committed=1 pending=0 withheld=false partial=false " +
                    "remaining=0 result=refused restored=none blockers=1 reason=changed_since store_ok=true",
                refused,
            )
        }
    }

    @Test
    fun aPartialPlanCountsTwoAppliedAndOneHeldApartAndItsCommitsAreUndone() = runTest {
        NoNetworkGuard.during {
            val rig = rig()
            rig.runner.run(LegId.UNDO_ALL)

            rig.runner.undoAll()

            val partial = rig.sink.starting("VAE_UNDO ").filter { it.contains(" case=3 ") }
            assertEquals(partial.toString(), 2, partial.size)
            assertEquals(
                "VAE_UNDO leg=undo_all case=3 phase=partial n=2 committed=2 pending=1 withheld=false partial=true " +
                    "remaining=1 result=none restored=none blockers=none reason=none store_ok=none",
                partial[0],
            )
            assertEquals(
                "VAE_UNDO leg=undo_all case=3 phase=undone n=2 committed=2 pending=1 withheld=false partial=true " +
                    "remaining=1 result=complete restored=2 blockers=none reason=none store_ok=true",
                partial[1],
            )
        }
    }

    @Test
    fun theWholePressWritesExactlyOneVerdictAfterAllThreeCases() = runTest {
        NoNetworkGuard.during {
            val rig = rig()
            rig.runner.run(LegId.UNDO_ALL)
            assertTrue(rig.sink.starting("VAE_VERDICT ").isEmpty())

            val result = rig.runner.undoAll()

            assertEquals(result.toString(), VerdictKind.PASS, result.verdict.kind)
            assertEquals(1, rig.sink.starting("VAE_VERDICT ").size)
            assertEquals(5, rig.sink.starting("VAE_UNDO ").size)
            // The verdict line is the last one: it is written only once every case has been judged.
            assertTrue(rig.sink.rendered.last().startsWith("VAE_VERDICT leg=undo_all verdict=PASS "))
        }
    }

    // The three cases as they look when everything works; each FAIL test breaks exactly one thing.
    private val goodWhole = CaseCounts(n = 3, pending = 0, committed = 3, held = 0, partial = false, remaining = 0)
    private val goodWholeUndo = UndoStep("complete", 3, null, null, true)
    private val goodRefusal = UndoStep("refused", null, 1, "changed_since", true)
    private val goodPartial = CaseCounts(n = 2, pending = 1, committed = 2, held = 1, partial = true, remaining = 1)
    private val goodPartialUndo = UndoStep("complete", 2, null, null, true)

    private fun judge(
        whole: CaseCounts = goodWhole,
        wholeUndo: UndoStep = goodWholeUndo,
        refusal: UndoStep = goodRefusal,
        partial: CaseCounts = goodPartial,
        partialUndo: UndoStep = goodPartialUndo,
    ) = UndoVerdicts.judge(whole, wholeUndo, refusal, partial, partialUndo)

    private fun assertFails(code: String, verdict: Verdict) {
        assertEquals(verdict.toString(), VerdictKind.FAIL, verdict.kind)
        assertEquals(code, verdict.reason)
    }

    @Test
    fun theVerdictPassesOnlyWhenAllThreeCasesHold() {
        assertEquals(VerdictKind.PASS, judge().kind)
    }

    @Test
    fun anUndoThatDidNotRestoreEverythingFailsUndoIncomplete() {
        assertFails("undo_incomplete", judge(wholeUndo = UndoStep("refused", null, 1, "changed_since", true)))
        assertFails("undo_incomplete", judge(wholeUndo = UndoStep("complete", 3, null, null, false)))
        assertFails("undo_incomplete", judge(partialUndo = UndoStep("partial", 1, null, "restore_failed", true)))
        assertFails("undo_incomplete", judge(partialUndo = UndoStep("complete", 2, null, null, false)))
    }

    @Test
    fun aMissingRefusalFailsRefusalMissing() {
        assertFails("refusal_missing", judge(refusal = UndoStep("complete", 1, null, null, true)))
    }

    @Test
    fun aRefusalForTheWrongReasonOrOverATouchedStoreFailsWrongRefusalReason() {
        assertFails("wrong_refusal_reason", judge(refusal = UndoStep("refused", null, 1, "entangled", true)))
        assertFails("wrong_refusal_reason", judge(refusal = UndoStep("refused", null, 2, "changed_since", true)))
        assertFails("wrong_refusal_reason", judge(refusal = UndoStep("refused", null, 1, "changed_since", false)))
    }

    @Test
    fun aPlanThatDidNotEndPartialFailsNotPartial() {
        assertFails("not_partial", judge(partial = CaseCounts(2, 1, 2, 1, partial = false, remaining = 1)))
        assertFails("not_partial", judge(partial = CaseCounts(2, 1, 2, 1, partial = true, remaining = 0)))
        assertFails("not_partial", judge(partial = CaseCounts(2, 1, 3, 0, partial = true, remaining = 1)))
    }

    @Test
    fun aHeldProposalCountedInNOrAWrongNFailsWrongCount() {
        // N of three for the partial plan would mean the held proposal was counted as applied.
        assertFails("wrong_count", judge(partial = CaseCounts(3, 0, 2, 1, partial = true, remaining = 1)))
        assertFails("wrong_count", judge(partial = CaseCounts(2, 0, 2, 1, partial = true, remaining = 1)))
        assertFails("wrong_count", judge(whole = CaseCounts(2, 0, 3, 0, partial = false, remaining = 0)))
    }

    @Test
    fun theFirstFailingCodeInTheFixedOrderIsTheOneReported() {
        val verdict = judge(
            wholeUndo = UndoStep("refused", null, 1, "changed_since", true),
            refusal = UndoStep("complete", 1, null, null, true),
            partial = CaseCounts(1, 0, 1, 0, partial = false, remaining = 0),
        )
        assertFails("undo_incomplete", verdict)
        assertFails("refusal_missing", judge(refusal = UndoStep("complete", 1, null, null, true), partial = CaseCounts(1, 0, 1, 0, false, 0)))
    }

    // A gate that refuses the nth proposal before anything is applied, the way a gate fault does (the engine records gate_error).
    private class ThrowingNthGate(private val nth: Int) : PreApplyGate {
        private var asked = 0

        override suspend fun admit(proposal: CommitProposal): GateDecision {
            asked++
            check(asked != nth) { "gate refused" }
            return GateDecision.Admit()
        }

        override fun toString(): String = "ThrowingNthGate"
    }

    @Test
    fun nCountsAnAppliedErrorBecauseItWasAppliedAndWroteNothing() = runTest {
        NoNetworkGuard.during {
            val world = UndoWorld()

            val run = world.run(UndoPlans.appliedError(), "Make one, then one under a missing parent.", AdmitAllGate)

            // One committed create and one create under a parent that does not exist: both were applied, so N is 2.
            assertEquals(1, run.committed)
            assertEquals(2, run.n)
            assertEquals(0, run.pending)
            assertEquals(1, world.world.store.snapshot().size - world.seeded.size)
        }
    }

    @Test
    fun nDoesNotCountAProposalTheGateRefusedBeforeItWasApplied() = runTest {
        NoNetworkGuard.during {
            val world = UndoWorld()

            val run = world.run(UndoPlans.whole(), "Make a list, make one under it, rename.", ThrowingNthGate(2))

            // The first create was applied; the second proposal never was, so it is not in N and not pending.
            assertEquals(1, run.committed)
            assertEquals(1, run.n)
            assertEquals(0, run.pending)
        }
    }

    @Test
    fun aHeldProposalIsPendingAndNeverPartOfN() = runTest {
        NoNetworkGuard.during {
            val world = UndoWorld()

            val run = world.run(UndoPlans.whole(), "Make a list, make one under it, rename.", HoldNthGate(2))

            assertEquals(1, run.n)
            assertEquals(1, run.pending)
            assertEquals(1, run.held)
            assertFalse(run.withheld)
        }
    }
}
