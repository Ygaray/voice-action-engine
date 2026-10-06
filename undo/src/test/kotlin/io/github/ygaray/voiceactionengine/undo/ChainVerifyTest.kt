package io.github.ygaray.voiceactionengine.undo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Several writes of one entity inside one command must form an unbroken chain, or the undo refuses. */
class ChainVerifyTest {

    @Test
    fun twoEditsOfOneEntityRestoreOnceToTheEarliestValue() {
        val rig = Rig()
        rig.store.put("a", "v0")
        rig.edit("cmd", 0, "a", "v1")
        rig.edit("cmd", 1, "a", "v2")

        val result = rig.undoAll("cmd")

        assertTrue(result.toString(), result is UndoResult.Complete)
        assertEquals("v0", rig.store.get("a")?.value)
        assertEquals(1, rig.adapter.restoreCalls())
        assertEquals(
            listOf(EntryRef("cmd", 1, "edit"), EntryRef("cmd", 0, "edit")),
            (result as UndoResult.Complete).restored,
        )
    }

    @Test
    fun anExternalEditBetweenTwoOfTheCommandsOwnWritesBreaksTheChain() {
        val rig = Rig()
        rig.store.put("a", "v0")
        rig.edit("cmd", 0, "a", "v1")
        rig.store.put("a", "external")
        rig.edit("cmd", 1, "a", "v2")
        val before = rig.store.dump()

        val result = rig.undoAll("cmd")

        assertTrue(result.toString(), result is UndoResult.Refused)
        val blocker = (result as UndoResult.Refused).blockers.single()
        assertEquals(UndoReason.CHAIN_BROKEN, blocker.reason)
        assertEquals(EntryRef("cmd", 1, "edit"), blocker.entry)
        assertEquals(EntityKey("item", "a"), blocker.entity)
        assertEquals(before, rig.store.dump())
        assertEquals(0, rig.adapter.restoreCalls())
    }

    @Test
    fun anEntryAfterAnUnsettledFailedWriteCannotBeChainVerified() {
        val rig = Rig()
        rig.store.put("a", "v0")
        runSuspending {
            val ticket = rig.journal.newTicket()
            ticket.capture("item", "a")
            rig.store.put("a", "v1")
            rig.journal.record("cmd", null, EntryRef("cmd", 0, "edit"), true, ticket)
        }
        rig.edit("cmd", 1, "a", "v2")

        val result = rig.undoAll("cmd")

        assertTrue(result.toString(), result is UndoResult.Refused)
        val blocker = (result as UndoResult.Refused).blockers.single()
        assertEquals(UndoReason.UNVERIFIABLE, blocker.reason)
        assertEquals(EntryRef("cmd", 0, "edit"), blocker.entry)
        assertEquals("v2", rig.store.get("a")?.value)
        assertEquals(0, rig.adapter.restoreCalls())
    }
}
