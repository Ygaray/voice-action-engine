package io.github.ygaray.voiceactionengine.sample

import io.github.ygaray.voiceactionengine.core.testing.NoNetworkGuard
import io.github.ygaray.voiceactionengine.sample.evidence.ALLOW_PATTERN
import io.github.ygaray.voiceactionengine.sample.evidence.LegId
import io.github.ygaray.voiceactionengine.sample.legs.UndoLeg
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
}
