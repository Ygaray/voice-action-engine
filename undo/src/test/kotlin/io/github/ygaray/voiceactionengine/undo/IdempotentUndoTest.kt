package io.github.ygaray.voiceactionengine.undo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.coroutines.resume

/** A double tap is safe: the second call writes nothing, and once the first has finished the group is undone. */
class IdempotentUndoTest {

    private fun resume(rig: Rig, id: String) {
        rig.adapter.suspendOnRestore.remove(id)
        checkNotNull(rig.adapter.gates.remove(id)) { "the restore of $id is not suspended" }.resume(Unit)
    }

    @Test
    fun aSecondCallWhileTheFirstIsRunningIsRefusedInProgressAndWritesNothing() {
        val rig = Rig()
        rig.store.put("x", "x0")
        rig.edit("cmd", 0, "x", "x1")
        rig.adapter.suspendOnRestore.add("x")

        val first = launch { rig.journal.undoAll("cmd") }
        assertFalse(first.finished)
        val logBefore = rig.adapter.log.size

        val second = rig.undoAll("cmd")

        assertTrue(second.toString(), second is UndoResult.Refused)
        val blocker = (second as UndoResult.Refused).blockers.single()
        assertNull(blocker.entry)
        assertNull(blocker.entity)
        assertEquals(UndoReason.IN_PROGRESS, blocker.reason)
        assertEquals(logBefore, rig.adapter.log.size)
        assertEquals(1, rig.adapter.restoreCalls("x"))

        resume(rig, "x")

        assertTrue(first.finished)
        assertTrue(first.value().toString(), first.value() is UndoResult.Complete)
        assertEquals("x0", rig.store.get("x")?.value)
        assertTrue(rig.undoAll("cmd") is UndoResult.AlreadyUndone)
        assertEquals(1, rig.adapter.restoreCalls("x"))
    }

    @Test
    fun anotherGroupUndoesWhileTheFirstIsSuspended() {
        val rig = Rig()
        rig.store.put("x", "x0")
        rig.store.put("y", "y0")
        rig.edit("one", 0, "x", "x1")
        rig.edit("two", 0, "y", "y1")
        rig.adapter.suspendOnRestore.add("x")
        val first = launch { rig.journal.undoAll("one") }
        assertFalse(first.finished)

        val other = rig.undoAll("two")

        assertTrue(other.toString(), other is UndoResult.Complete)
        assertEquals("y0", rig.store.get("y")?.value)
        resume(rig, "x")
        assertTrue(first.value() is UndoResult.Complete)
    }

    @Test
    fun aFinishedUndoIsAlreadyUndoneAndAFailedOneIsNotStuck() {
        val rig = Rig()
        rig.store.put("x", "x0")
        rig.edit("cmd", 0, "x", "x1")
        rig.adapter.throwOnRestore.add("x")

        val failed = rig.undoAll("cmd")
        assertTrue(failed.toString(), failed is UndoResult.Partial)
        rig.adapter.throwOnRestore.clear()

        assertTrue(rig.undoAll("cmd") is UndoResult.Complete)
        assertTrue(rig.undoAll("cmd") is UndoResult.AlreadyUndone)
    }
}
