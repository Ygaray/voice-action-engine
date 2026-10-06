package io.github.ygaray.voiceactionengine.undo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The group view gives "Undo all (N)", and every way the journal could have missed an action withholds the group. */
class WithheldGroupTest {

    private fun ref(run: String, position: Int) = EntryRef(run, position, "t")

    private fun sealedTicket(rig: Rig): UndoTicket = rig.journal.newTicket().also { it.nothingWritten() }

    private fun record(rig: Rig, group: String, entry: EntryRef, ticket: UndoTicket?, parent: String? = null) =
        runSuspending { rig.journal.record(group, parent, entry, false, ticket) }

    /** The reason of a refusal of the whole group: one blocker naming neither an action nor an entity. */
    private fun refusalReason(result: UndoResult): UndoReason {
        assertTrue(result.toString(), result is UndoResult.Refused)
        val blocker = (result as UndoResult.Refused).blockers.single()
        assertNull(blocker.entry)
        assertNull(blocker.entity)
        return blocker.reason
    }

    @Test
    fun threeSealedRecordingsGiveCountThreeAndAfterUndoCountZero() {
        val rig = Rig()
        listOf("a", "b", "c").forEach { rig.store.put(it, "v0") }
        rig.edit("g", 0, "a", "v1")
        rig.edit("g", 1, "b", "v1")
        rig.edit("g", 2, "c", "v1")

        val view = rig.groupOf("g")
        assertEquals(3, view.count)
        assertFalse(view.withheld)
        assertEquals(3, view.entries.size)
        assertEquals(3, view.pending.size)

        assertTrue(rig.undoAll("g") is UndoResult.Complete)
        val after = rig.groupOf("g")
        assertEquals(0, after.count)
        assertEquals(3, after.entries.size)
    }

    @Test
    fun aNullTicketWithholdsTheGroupAndTheEntryIsStillListed() {
        val rig = Rig()
        record(rig, "g", ref("g", 0), null)

        val view = rig.groupOf("g")
        assertTrue(view.withheld)
        assertEquals(listOf(ref("g", 0)), view.entries)
        assertEquals(UndoReason.JOURNAL_WITHHELD, refusalReason(rig.undoAll("g")))
        assertEquals(0, rig.adapter.restoreCalls())
    }

    @Test
    fun aTicketFromAnotherJournalWithholds() {
        val rig = Rig()
        val foreign = Rig().journal.newTicket().also { it.nothingWritten() }
        record(rig, "g", ref("g", 0), foreign)

        assertTrue(rig.groupOf("g").withheld)
        assertEquals(UndoReason.JOURNAL_WITHHELD, refusalReason(rig.undoAll("g")))
    }

    @Test
    fun theSameTicketRecordedTwiceWithholds() {
        val rig = Rig()
        val ticket = sealedTicket(rig)
        record(rig, "g", ref("g", 0), ticket)
        assertFalse(rig.groupOf("g").withheld)
        record(rig, "g", ref("g", 1), ticket)

        assertTrue(rig.groupOf("g").withheld)
        assertEquals(UndoReason.JOURNAL_WITHHELD, refusalReason(rig.undoAll("g")))
    }

    @Test
    fun aRepeatedRunAndPositionWithholdsAndIsListedOnce() {
        val rig = Rig()
        rig.store.put("a", "v0")
        rig.edit("g", 0, "a", "v1")
        rig.edit("g", 0, "a", "v2")

        val view = rig.groupOf("g")
        assertTrue(view.withheld)
        assertEquals(1, view.entries.size)
        assertEquals(UndoReason.JOURNAL_WITHHELD, refusalReason(rig.undoAll("g")))
        assertEquals(0, rig.adapter.restoreCalls())
    }

    @Test
    fun aCommittedActionWhoseTicketNeverSettledWithholds() {
        val rig = Rig()
        rig.store.put("a", "v0")
        runSuspending {
            val ticket = rig.journal.newTicket()
            ticket.capture("item", "a")
            rig.store.put("a", "v1")
            rig.journal.record("g", null, ref("g", 0), false, ticket)
        }

        assertTrue(rig.groupOf("g").withheld)
        assertEquals(UndoReason.JOURNAL_WITHHELD, refusalReason(rig.undoAll("g")))
        assertEquals("v1", rig.store.get("a")?.value)
    }

    @Test
    fun anAppliedActionThatReportedAnErrorIsKeptEvenWhenUnsettled() {
        val rig = Rig()
        rig.store.put("a", "v0")
        runSuspending {
            val ticket = rig.journal.newTicket()
            ticket.capture("item", "a")
            rig.journal.record("g", null, ref("g", 0), true, ticket)
        }

        val view = rig.groupOf("g")
        assertFalse(view.withheld)
        assertEquals(1, view.count)
    }

    @Test
    fun runClosedWithholdsOnlyWhenAnAppliedPositionWasNotRecorded() {
        val missing = Rig()
        missing.store.put("a", "v0")
        missing.store.put("c", "v0")
        missing.edit("g", 0, "a", "v1")
        missing.edit("g", 2, "c", "v1")
        runSuspending { missing.journal.runClosed("g", "g", setOf(0, 1, 2)) }
        assertTrue(missing.groupOf("g").withheld)

        val complete = Rig()
        complete.store.put("a", "v0")
        complete.store.put("b", "v0")
        complete.store.put("c", "v0")
        complete.edit("g", 0, "a", "v1")
        complete.edit("g", 1, "b", "v1")
        complete.edit("g", 2, "c", "v1")
        runSuspending { complete.journal.runClosed("g", "g", setOf(0, 1, 2)) }
        assertFalse(complete.groupOf("g").withheld)
    }

    @Test
    fun runClosedForAnUnknownGroupWithheldsOnlyForANonEmptySet() {
        val rig = Rig()
        runSuspending { rig.journal.runClosed("none", "r", emptySet()) }
        assertNull(rig.group("none"))

        runSuspending { rig.journal.runClosed("lost", "r", setOf(0)) }
        assertTrue(rig.groupOf("lost").withheld)
    }

    @Test
    fun withholdIsStickyAcrossLaterSealedRecords() {
        val rig = Rig()
        runSuspending { rig.journal.withhold("g") }
        assertTrue(rig.groupOf("g").withheld)
        record(rig, "g", ref("g", 0), sealedTicket(rig))

        val view = rig.groupOf("g")
        assertTrue(view.withheld)
        assertEquals(1, view.entries.size)
        assertEquals(UndoReason.JOURNAL_WITHHELD, refusalReason(rig.undoAll("g")))
    }

    @Test
    fun aFailedEntryAndANothingWrittenEntryBothCountAndAreRestored() {
        val rig = Rig()
        rig.store.put("a", "v0")
        rig.store.put("b", "v0")
        rig.edit("g", 0, "a", "v1")
        rig.edit("g", 1, "b", "v1", failed = true)
        record(rig, "g", ref("g", 2), sealedTicket(rig))

        assertEquals(3, rig.groupOf("g").count)
        val result = rig.undoAll("g")

        assertTrue(result.toString(), result is UndoResult.Complete)
        assertEquals(3, (result as UndoResult.Complete).restored.size)
        assertEquals(2, rig.adapter.restoreCalls())
        assertEquals("v0", rig.store.get("a")?.value)
        assertEquals("v0", rig.store.get("b")?.value)
    }

    @Test
    fun aGroupAcceptsAppendsAfterItsRunClosedWithoutCollision() {
        val rig = Rig()
        rig.store.put("a", "v0")
        rig.edit("g", 0, "a", "v1")
        runSuspending { rig.journal.runClosed("g", "g", setOf(0)) }
        record(rig, "g", ref("child", 0), sealedTicket(rig))

        val view = rig.groupOf("g")
        assertFalse(view.withheld)
        assertEquals(2, view.count)
    }

    @Test
    fun theFirstNonNullParentGroupKeyIsKept() {
        val rig = Rig()
        record(rig, "g", ref("g", 0), sealedTicket(rig), parent = null)
        assertNull(rig.groupOf("g").parentGroupKey)
        record(rig, "g", ref("g", 1), sealedTicket(rig), parent = "p")
        assertEquals("p", rig.groupOf("g").parentGroupKey)
        record(rig, "g", ref("g", 2), sealedTicket(rig), parent = "q")
        assertEquals("p", rig.groupOf("g").parentGroupKey)
        record(rig, "g", ref("g", 3), sealedTicket(rig), parent = null)
        assertEquals("p", rig.groupOf("g").parentGroupKey)
    }

    @Test
    fun theRevisionRisesOnEveryChangeAndAnUnknownGroupIsNull() {
        val rig = Rig()
        assertNull(rig.group("never"))
        record(rig, "g", ref("g", 0), sealedTicket(rig))
        val first = rig.groupOf("g").revision
        record(rig, "g", ref("g", 1), sealedTicket(rig))
        val second = rig.groupOf("g").revision
        runSuspending { rig.journal.runClosed("g", "g", setOf(0, 1)) }
        val third = rig.groupOf("g").revision
        rig.undoAll("g")
        val fourth = rig.groupOf("g").revision

        assertTrue("$first $second $third $fourth", first < second && second < third && third < fourth)
    }

    @Test
    fun aBlankGroupKeyIsRejected() {
        val rig = Rig()
        listOf("", "   ").forEach { key ->
            try {
                record(rig, key, ref("g", 0), sealedTicket(rig))
                throw AssertionError("a blank key must be rejected")
            } catch (expected: IllegalArgumentException) {
                assertFalse(expected.message.orEmpty().isEmpty())
            }
        }
    }

    @Test
    fun theViewPrintsCountsOnly() {
        val rig = Rig()
        record(rig, "secret-group-key", ref("run", 0), sealedTicket(rig))

        val view = rig.groupOf("secret-group-key")

        assertEquals("UndoGroup(count=1, entries=1, withheld=false, revision=${view.revision})", view.toString())
    }
}
