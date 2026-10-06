package io.github.ygaray.voiceactionengine.undo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Effects outside the database are reversed after the data, newest first, once, and every failure is named. */
class CompensatorTest {

    private fun entry(position: Int) = EntryRef("cmd", position, "edit")

    private fun alarm(payload: String) = listOf("alarm" to payload)

    /** Two actions: entry 0 edits a and declares alarm p1, entry 1 edits b and declares alarm p2. */
    private fun twoActions(): Rig {
        val rig = Rig()
        rig.store.put("a", "a0")
        rig.store.put("b", "b0")
        rig.editAll("cmd", 0, listOf("a"), "a1", alarm("p1"))
        rig.editAll("cmd", 1, listOf("b"), "b1", alarm("p2"))
        return rig
    }

    @Test
    fun compensatorsRunAfterEveryRestoreNewestFirstAndOnce() {
        val rig = twoActions()

        val result = rig.undoAll("cmd")

        assertTrue(result.toString(), result is UndoResult.Complete)
        assertEquals(listOf(entry(1), entry(0)), (result as UndoResult.Complete).restored)
        assertEquals(
            listOf("restoreIf:b", "restoreIf:a", "compensate:p2", "compensate:p1"),
            rig.adapter.log.writes(),
        )
        assertEquals(1, rig.compensator.calls("p1"))
        assertEquals(1, rig.compensator.calls("p2"))
        assertEquals(setOf("p1", "p2"), rig.compensator.applied)
    }

    @Test
    fun aSecondUndoIsAlreadyUndoneAndCallsNoCompensator() {
        val rig = twoActions()
        rig.undoAll("cmd")

        val again = rig.undoAll("cmd")

        assertTrue(again.toString(), again is UndoResult.AlreadyUndone)
        assertEquals(1, rig.compensator.calls("p1"))
        assertEquals(1, rig.compensator.calls("p2"))
    }

    @Test
    fun aCompensatorFaultIsPartialAndARetryRerunsOnlyThatCompensator() {
        val rig = twoActions()
        rig.compensator.throwOn.add("p2")

        val partial = rig.undoAll("cmd")

        assertTrue(partial.toString(), partial is UndoResult.Partial)
        partial as UndoResult.Partial
        assertEquals(listOf(entry(0)), partial.restored)
        val item = partial.notRestored.single()
        assertEquals(entry(1), item.entry)
        assertNull(item.entity)
        assertEquals("alarm", item.compensator)
        assertEquals(UndoReason.COMPENSATOR_FAILED, item.reason)
        assertEquals("IllegalStateException", item.errorClass)
        assertEquals("a0", rig.store.get("a")?.value)
        assertEquals("b0", rig.store.get("b")?.value)

        val firstPass = rig.adapter.log.writes().size
        rig.compensator.throwOn.clear()
        val retry = rig.undoAll("cmd")

        assertTrue(retry.toString(), retry is UndoResult.Complete)
        assertEquals(listOf(entry(1)), (retry as UndoResult.Complete).restored)
        assertEquals(listOf("compensate:p2"), rig.adapter.log.writes().drop(firstPass))
        assertEquals(1, rig.compensator.calls("p1"))
        assertEquals(2, rig.compensator.calls("p2"))
        assertEquals(1, rig.adapter.restoreCalls("a"))
        assertEquals(1, rig.adapter.restoreCalls("b"))
        assertEquals(setOf("p1", "p2"), rig.compensator.applied)
    }

    @Test
    fun aCompensatorCalledTwiceForOnePayloadLeavesTheSameState() {
        val rig = Rig()

        runSuspending {
            rig.compensator.compensate("p1")
            val once = rig.compensator.applied.toSet()
            rig.compensator.compensate("p1")
            assertEquals(once, rig.compensator.applied)
        }

        assertEquals(setOf("p1"), rig.compensator.applied)
    }

    @Test
    fun aRefusedUndoRunsNoCompensator() {
        val rig = twoActions()
        rig.store.put("b", "edited later")

        val result = rig.undoAll("cmd")

        assertTrue(result.toString(), result is UndoResult.Refused)
        assertEquals(emptyList<String>(), rig.adapter.log.writes())
        assertEquals(0, rig.compensator.calls("p1"))
        assertEquals(0, rig.compensator.calls("p2"))
    }

    @Test
    fun aFailedRestoreSkipsItsCompensatorAndOthersStillRun() {
        val rig = twoActions()
        rig.adapter.throwOnRestore.add("b")

        val result = rig.undoAll("cmd")

        assertTrue(result.toString(), result is UndoResult.Partial)
        result as UndoResult.Partial
        assertEquals(listOf(entry(0)), result.restored)
        val (restore, skipped) = result.notRestored
        assertEquals(EntityKey("item", "b"), restore.entity)
        assertEquals(UndoReason.RESTORE_FAILED, restore.reason)
        assertEquals(entry(1), skipped.entry)
        assertNull(skipped.entity)
        assertEquals("alarm", skipped.compensator)
        assertEquals(UndoReason.SKIPPED_AFTER_FAILURE, skipped.reason)
        assertNull(skipped.errorClass)
        assertEquals(0, rig.compensator.calls("p2"))
        assertEquals(1, rig.compensator.calls("p1"))
    }

    @Test
    fun anUnregisteredCompensationKindRefusesBeforeAnythingIsWritten() {
        val rig = Rig()
        rig.store.put("a", "a0")
        rig.editAll("cmd", 0, listOf("a"), "a1", listOf("unknown" to "p1"))

        val result = rig.undoAll("cmd")

        assertTrue(result.toString(), result is UndoResult.Refused)
        val blocker = (result as UndoResult.Refused).blockers.single()
        assertEquals(entry(0), blocker.entry)
        assertNull(blocker.entity)
        assertEquals(UndoReason.NO_ADAPTER, blocker.reason)
        assertEquals(emptyList<String>(), rig.adapter.log.writes())
        assertEquals("a1", rig.store.get("a")?.value)
    }

    @Test
    fun anActionWithAnEffectButNoEntityStillRunsItsCompensator() {
        val rig = Rig()
        runSuspending {
            val ticket = rig.journal.newTicket()
            ticket.compensate("alarm", "p1")
            rig.journal.record("cmd", null, entry(0), false, ticket)
        }

        val result = rig.undoAll("cmd")

        assertTrue(result.toString(), result is UndoResult.Complete)
        assertEquals(listOf(entry(0)), (result as UndoResult.Complete).restored)
        assertEquals(listOf("compensate:p1"), rig.adapter.log.writes())
        assertTrue(rig.undoAll("cmd") is UndoResult.AlreadyUndone)
    }
}
