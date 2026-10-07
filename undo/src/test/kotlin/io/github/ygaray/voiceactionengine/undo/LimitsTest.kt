package io.github.ygaray.voiceactionengine.undo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The journal is bounded by group count and age, never while a group is being undone, and never silently. */
class LimitsTest {

    private fun reason(result: UndoResult): UndoReason {
        assertTrue(result.toString(), result is UndoResult.Refused)
        return (result as UndoResult.Refused).blockers.single().reason
    }

    private fun record(rig: Rig, group: String, position: Int = 0) = runSuspending {
        val ticket = rig.journal.newTicket().also { it.nothingWritten() }
        rig.journal.record(group, null, EntryRef(group, position, "t"), false, ticket)
    }

    @Test
    fun theDefaultsAreFiftyGroupsAndOneHour() {
        val builder = UndoJournal.Builder()
        assertEquals(50, builder.maxGroups)
        assertEquals(3_600_000L, builder.maxAgeMillis)
    }

    @Test
    fun theBuilderRejectsLimitsBelowOne() {
        listOf<UndoJournal.Builder.() -> Unit>({ maxGroups = 0 }, { maxAgeMillis = 0 }, { maxGroups = -3 }).forEach {
            try {
                UndoJournal(it)
                throw AssertionError("a limit below one must be rejected")
            } catch (expected: IllegalArgumentException) {
                assertTrue(expected.message.orEmpty().contains("at least 1"))
            }
        }
    }

    @Test
    fun theLeastRecentlyActiveGroupIsEvictedWhenTheCountIsExceeded() {
        val rig = Rig(configure = { maxGroups = 2 })
        record(rig, "g1", 0)
        record(rig, "g2", 0)
        record(rig, "g1", 1)
        record(rig, "g3", 0)

        assertNull(rig.group("g2"))
        assertEquals(UndoReason.UNKNOWN_GROUP, reason(rig.undoAll("g2")))
        assertNotNull(rig.group("g1"))
        assertNotNull(rig.group("g3"))
    }

    @Test
    fun aGroupIdleForMoreThanTheAgeIsEvictedByTheNextOperation() {
        var now = 0L
        val rig = Rig(configure = {
            maxAgeMillis = 1000
            clock = { now }
        })
        record(rig, "g")

        now = 1000
        assertNotNull(rig.group("g"))
        now = 1001
        assertNull(rig.group("g"))
        assertEquals(UndoReason.UNKNOWN_GROUP, reason(rig.undoAll("g")))
    }

    @Test
    fun aFailedFirstClockReadingDoesNotAgeTheGroupAtTheNextGoodOne() {
        var reading: Long? = null
        val rig = Rig(configure = {
            maxAgeMillis = 3_600_000
            clock = { checkNotNull(reading) { CANARY } }
        })
        record(rig, "g")

        reading = 1_700_000_000_000
        assertNotNull(rig.group("g"))

        reading = 1_700_000_000_000 + 3_600_001
        assertNull(rig.group("g"))
    }

    @Test
    fun aGroupBeingUndoneIsNotEvictedByEitherBound() {
        var now = 0L
        val rig = Rig(configure = {
            maxGroups = 1
            maxAgeMillis = 1000
            clock = { now }
        })
        rig.store.put("x", "x0")
        rig.edit("g1", 0, "x", "x1")
        rig.adapter.suspendOnRestore.add("x")
        val first = launch { rig.journal.undoAll("g1") }
        assertFalse(first.finished)

        now = 5000
        record(rig, "g2")
        record(rig, "g3")

        assertNotNull(rig.group("g1"))
        rig.resume("x")
        assertTrue(first.value().toString(), first.value() is UndoResult.Complete)
        assertEquals("x0", rig.store.get("x")?.value)
    }

    @Test
    fun aRecordIntoAnEvictedKeyCreatesAWithheldGroup() {
        val rig = Rig(configure = { maxGroups = 1 })
        record(rig, "g1", 0)
        record(rig, "g2", 0)
        assertNull(rig.group("g1"))

        record(rig, "g1", 1)

        val view = rig.groupOf("g1")
        assertTrue(view.withheld)
        assertEquals(1, view.entries.size)
        assertEquals(UndoReason.JOURNAL_WITHHELD, reason(rig.undoAll("g1")))
    }

    @Test
    fun withholdAndRunClosedIntoAnEvictedKeyAreWithheldToo() {
        val rig = Rig(configure = { maxGroups = 1 })
        record(rig, "g1", 0)
        record(rig, "g2", 0)
        runSuspending { rig.journal.runClosed("g1", "g1", setOf(0)) }

        assertTrue(rig.groupOf("g1").withheld)
    }

    @Test
    fun theTombstonesAreBoundedSoOnlyTheMostRecentEvictionsStayRemembered() {
        val rig = Rig(configure = { maxGroups = 1 })
        record(rig, "g1")
        record(rig, "g2")
        record(rig, "g3")

        record(rig, "g2", 1)

        assertTrue(rig.groupOf("g2").withheld)
    }

    @Test
    fun aKeyDroppedLongAgoStaysWithheldBeyondTheGroupCount() {
        val rig = Rig(configure = { maxGroups = 1 })
        for (index in 0..30) record(rig, "g$index")

        record(rig, "g0", 1)

        assertTrue(rig.groupOf("g0").withheld)
    }

    @Test
    fun theTombstoneMemoryIsBoundedSoAnAncientKeyIsForgotten() {
        val rig = Rig(configure = { maxGroups = 1 })
        for (index in 0..1002) record(rig, "g$index")

        record(rig, "g0", 1)
        assertFalse(rig.groupOf("g0").withheld)

        record(rig, "g1001", 1)
        assertTrue(rig.groupOf("g1001").withheld)
    }

    @Test
    fun anUndoneEntrysGroupStillAnswersAlreadyUndoneUntilItIsEvicted() {
        val rig = Rig(configure = { maxGroups = 2 })
        rig.store.put("a", "v0")
        rig.edit("g", 0, "a", "v1")
        assertTrue(rig.undoAll("g") is UndoResult.Complete)
        assertTrue(rig.undoAll("g") is UndoResult.AlreadyUndone)
        assertEquals(0, rig.groupOf("g").count)
    }
}
