package io.github.ygaray.voiceactionengine.undo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** The journal works on its own: no voice engine is on this classpath, and a plain store is journaled and undone. */
class UndoStandaloneTest {

    @Test
    fun theEngineIsNotOnTheClasspath() {
        assertThrows(ClassNotFoundException::class.java) {
            Class.forName("io.github.ygaray.voiceactionengine.core.CommandInput")
        }
    }

    @Test
    fun oneEditIsUndoneThroughATicket() {
        val store = TestEntityStore().apply { put("a", "v0") }
        val adapter = TestAdapter(store)
        val journal = UndoJournal { adapter(adapter) }
        val ref = EntryRef("cmd-1", 0, "rename")

        val result = runSuspending {
            val ticket = journal.newTicket()
            ticket.capture("item", "a")
            store.put("a", "v1")
            ticket.settle("item", "a")
            journal.record("cmd-1", null, ref, false, ticket)
            journal.undoAll("cmd-1")
        }

        assertTrue(result.toString(), result is UndoResult.Complete)
        assertEquals(listOf(ref), (result as UndoResult.Complete).restored)
        assertEquals("v0", store.get("a")?.value)
        assertEquals(1, adapter.restoreCalls())
    }

    @Test
    fun aSecondUndoIsAlreadyUndoneAndWritesNothing() {
        val rig = Rig()
        rig.store.put("a", "v0")
        rig.edit("cmd-1", 0, "a", "v1")
        assertTrue(rig.undoAll("cmd-1") is UndoResult.Complete)
        val callsAfterFirst = rig.adapter.restoreCalls()
        val before = rig.store.dump()

        val second = rig.undoAll("cmd-1")

        assertTrue(second.toString(), second is UndoResult.AlreadyUndone)
        assertEquals("already_undone", second.code)
        assertEquals(callsAfterFirst, rig.adapter.restoreCalls())
        assertEquals(before, rig.store.dump())
    }

    @Test
    fun anUnknownGroupIsRefusedWithAGroupLevelBlocker() {
        val result = Rig().undoAll("never")

        assertTrue(result.toString(), result is UndoResult.Refused)
        val blocker = (result as UndoResult.Refused).blockers.single()
        assertNull(blocker.entry)
        assertNull(blocker.entity)
        assertEquals(UndoReason.UNKNOWN_GROUP, blocker.reason)
    }
}
