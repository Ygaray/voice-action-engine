package io.github.ygaray.voiceactionengine.undo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import kotlin.coroutines.cancellation.CancellationException

/** A cancelled undo reaches the caller, strands nothing, and a retry finishes exactly what is left. */
class CancelMidUndoTest {

    private fun entry(position: Int) = EntryRef("cmd", position, "edit")

    private fun assertCancelled(rig: Rig) {
        try {
            rig.undoAll("cmd")
            fail("the cancellation was swallowed")
        } catch (expected: CancellationException) {
            assertEquals(CANARY, expected.message)
        }
    }

    /** Three independent actions on x, y and z, restored in the order z, y, x. */
    private fun threeActions(): Rig {
        val rig = Rig()
        listOf("x", "y", "z").forEachIndexed { position, id ->
            rig.store.put(id, "${id}0")
            rig.edit("cmd", position, id, "${id}1")
        }
        return rig
    }

    @Test
    fun aCancellationPropagatesAndTheRetryRestoresTheRemainder() {
        val rig = threeActions()
        rig.adapter.cancelOnRestore.add("y")

        assertCancelled(rig)

        assertEquals("z0", rig.store.get("z")?.value)
        assertEquals("y1", rig.store.get("y")?.value)
        rig.adapter.cancelOnRestore.clear()

        val retry = rig.undoAll("cmd")

        assertTrue(retry.toString(), retry is UndoResult.Complete)
        assertEquals(listOf(entry(1), entry(0)), (retry as UndoResult.Complete).restored)
        assertEquals(1, rig.adapter.restoreCalls("z"))
        assertEquals("y0", rig.store.get("y")?.value)
        assertEquals("x0", rig.store.get("x")?.value)
        assertTrue(rig.undoAll("cmd") is UndoResult.AlreadyUndone)
    }

    @Test
    fun aRestoreThatAlreadyWroteCountsAsRestoredOnRetryWithNoSecondWrite() {
        val rig = threeActions()
        rig.adapter.cancelAfterWrite.add("y")

        assertCancelled(rig)

        assertEquals("y0", rig.store.get("y")?.value)
        rig.adapter.cancelAfterWrite.clear()

        val retry = rig.undoAll("cmd")

        assertTrue(retry.toString(), retry is UndoResult.Complete)
        assertEquals(listOf(entry(1), entry(0)), (retry as UndoResult.Complete).restored)
        assertEquals(1, rig.adapter.restoreCalls("y"))
        assertEquals(1, rig.adapter.restoreCalls("z"))
        assertEquals(1, rig.adapter.restoreCalls("x"))
    }

    @Test
    fun aCancelledCompensatorPropagatesAndIsNotMarkedDone() {
        val rig = Rig()
        rig.store.put("a", "a0")
        rig.editAll("cmd", 0, listOf("a"), "a1", listOf("alarm" to "p1"))
        rig.compensator.cancelOn.add("p1")

        assertCancelled(rig)

        assertEquals("a0", rig.store.get("a")?.value)
        assertEquals(emptySet<String>(), rig.compensator.applied)
        rig.compensator.cancelOn.clear()

        val retry = rig.undoAll("cmd")

        assertTrue(retry.toString(), retry is UndoResult.Complete)
        assertEquals(listOf(entry(0)), (retry as UndoResult.Complete).restored)
        assertEquals(2, rig.compensator.calls("p1"))
        assertEquals(1, rig.adapter.restoreCalls("a"))
        assertEquals(setOf("p1"), rig.compensator.applied)
    }
}
