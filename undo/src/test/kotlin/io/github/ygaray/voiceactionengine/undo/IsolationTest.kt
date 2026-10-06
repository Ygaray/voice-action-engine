package io.github.ygaray.voiceactionengine.undo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** An action that shares no entity with another gets its own undo; an entangled one goes through "undo all". */
class IsolationTest {

    private fun ref(position: Int) = EntryRef("g", position, "edit")

    private fun blockers(result: UndoResult): List<Blocker> {
        assertTrue(result.toString(), result is UndoResult.Refused)
        return (result as UndoResult.Refused).blockers
    }

    @Test
    fun separateEntitiesAreAllIsolatedAndOneCanBeUndoneAlone() {
        val rig = Rig()
        listOf("a", "b", "c").forEach { rig.store.put(it, "v0") }
        rig.edit("g", 0, "a", "v1")
        rig.edit("g", 1, "b", "v1")
        rig.edit("g", 2, "c", "v1")
        assertEquals(listOf(ref(0), ref(1), ref(2)), rig.groupOf("g").isolated)

        val result = rig.undoEntry("g", ref(1))

        assertTrue(result.toString(), result is UndoResult.Complete)
        assertEquals(listOf(ref(1)), (result as UndoResult.Complete).restored)
        assertEquals("v0", rig.store.get("b")?.value)
        assertEquals("v1", rig.store.get("a")?.value)
        assertEquals("v1", rig.store.get("c")?.value)
        val view = rig.groupOf("g")
        assertEquals(2, view.count)
        assertEquals(listOf(ref(0), ref(2)), view.pending)
        assertEquals(listOf(ref(0), ref(2)), view.isolated)
    }

    @Test
    fun anEntangledActionIsRefusedAloneAndUndoAllStillCoversIt() {
        val rig = Rig()
        rig.store.put("a", "v0")
        rig.store.put("b", "v0")
        rig.edit("g", 0, "a", "v1")
        rig.editAll("g", 1, listOf("a", "b"), "v2")
        assertTrue(rig.groupOf("g").isolated.isEmpty())
        val before = rig.store.dump()

        val blocker = blockers(rig.undoEntry("g", ref(0))).single()

        assertEquals(ref(0), blocker.entry)
        assertEquals(null, blocker.entity)
        assertEquals(UndoReason.ENTANGLED, blocker.reason)
        assertEquals(before, rig.store.dump())
        assertEquals(0, rig.adapter.restoreCalls())

        val all = rig.undoAll("g")
        assertTrue(all.toString(), all is UndoResult.Complete)
        assertEquals(2, (all as UndoResult.Complete).restored.size)
        assertEquals("v0", rig.store.get("a")?.value)
        assertEquals("v0", rig.store.get("b")?.value)
    }

    @Test
    fun aSharedTouchesOnlyKeyEntanglesToo() {
        val rig = Rig()
        rig.store.put("a", "v0")
        rig.store.put("b", "v0")
        rig.edit("g", 0, "a", "v1")
        runSuspending {
            val ticket = rig.journal.newTicket()
            ticket.capture("item", "b")
            rig.store.put("b", "v1")
            ticket.settle("item", "b")
            ticket.touches("item", "a")
            rig.journal.record("g", null, ref(1), false, ticket)
        }

        assertTrue(rig.groupOf("g").isolated.isEmpty())
        assertEquals(UndoReason.ENTANGLED, blockers(rig.undoEntry("g", ref(1))).single().reason)
        assertEquals(0, rig.adapter.restoreCalls())
    }

    @Test
    fun sharingIsTransitiveAndAnUnrelatedActionStaysIsolated() {
        val rig = Rig()
        listOf("a", "b", "c", "d", "z").forEach { rig.store.put(it, "v0") }
        rig.editAll("g", 0, listOf("a", "b"), "v1")
        rig.editAll("g", 1, listOf("b", "c"), "v2")
        rig.editAll("g", 2, listOf("c", "d"), "v3")
        rig.edit("g", 3, "z", "v1")

        assertEquals(listOf(ref(3)), rig.groupOf("g").isolated)
    }

    @Test
    fun aSingleUndoChecksOnlyItsOwnFootprint() {
        val rig = Rig()
        rig.store.put("a", "v0")
        rig.store.put("b", "v0")
        rig.edit("g", 0, "a", "v1")
        rig.edit("g", 1, "b", "v1")
        rig.store.put("b", "changed later")

        val alone = rig.undoEntry("g", ref(0))

        assertTrue(alone.toString(), alone is UndoResult.Complete)
        assertEquals("v0", rig.store.get("a")?.value)
        assertEquals("changed later", rig.store.get("b")?.value)
        assertEquals(UndoReason.CHANGED_SINCE, blockers(rig.undoEntry("g", ref(1))).single().reason)
    }

    @Test
    fun anUnknownEntryAnUnknownGroupAndAnUndoneEntryHaveTheirOwnAnswers() {
        val rig = Rig()
        rig.store.put("a", "v0")
        rig.edit("g", 0, "a", "v1")

        val unknownEntry = blockers(rig.undoEntry("g", ref(7))).single()
        assertEquals(ref(7), unknownEntry.entry)
        assertEquals(UndoReason.UNKNOWN_ENTRY, unknownEntry.reason)

        val unknownGroup = blockers(rig.undoEntry("nope", ref(0))).single()
        assertEquals(UndoReason.UNKNOWN_GROUP, unknownGroup.reason)

        assertTrue(rig.undoAll("g") is UndoResult.Complete)
        assertTrue(rig.undoEntry("g", ref(0)) is UndoResult.AlreadyUndone)
        assertEquals(1, rig.adapter.restoreCalls())
    }

    @Test
    fun aWithheldGroupRefusesSingleUndoToo() {
        val rig = Rig()
        rig.store.put("a", "v0")
        rig.edit("g", 0, "a", "v1")
        runSuspending { rig.journal.withhold("g") }

        assertTrue(rig.groupOf("g").isolated.isEmpty())
        assertEquals(UndoReason.JOURNAL_WITHHELD, blockers(rig.undoEntry("g", ref(0))).single().reason)
        assertEquals(0, rig.adapter.restoreCalls())
    }

    @Test
    fun aSingleUndoSharesTheGroupsInProgressFlag() {
        val rig = Rig()
        rig.store.put("a", "v0")
        rig.store.put("b", "v0")
        rig.edit("g", 0, "a", "v1")
        rig.edit("g", 1, "b", "v1")
        rig.adapter.suspendOnRestore.add("b")

        val first = launch { rig.journal.undoAll("g") }
        assertFalse(first.finished)

        assertEquals(UndoReason.IN_PROGRESS, blockers(rig.undoEntry("g", ref(0))).single().reason)
        rig.resume("b")
        assertTrue(first.value() is UndoResult.Complete)
    }
}
