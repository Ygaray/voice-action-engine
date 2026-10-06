package io.github.ygaray.voiceactionengine.undo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections

/** Every kind of change an adapter has to put back: created, edited, deleted with children, and across two types. */
class AdapterRoundTripTest {

    @Test
    fun aCreatedEntityIsDeleted() {
        val rig = Rig()
        runSuspending {
            val ticket = rig.journal.newTicket()
            rig.store.put("n", "new")
            ticket.created("item", "n")
            rig.journal.record("cmd", null, EntryRef("cmd", 0, "add"), false, ticket)
        }

        val result = rig.undoAll("cmd")

        assertTrue(result.toString(), result is UndoResult.Complete)
        assertNull(rig.store.get("n"))
        assertEquals(listOf("restoreIf:n"), rig.adapter.log.filter { it.startsWith("restoreIf") })
    }

    @Test
    fun anEditedEntityIsWrittenBackWithEveryField() {
        val rig = Rig()
        rig.store.put("a", "v0", "label-0")
        runSuspending {
            val ticket = rig.journal.newTicket()
            ticket.capture("item", "a")
            rig.store.put("a", "v1", "label-1")
            ticket.settle("item", "a")
            rig.journal.record("cmd", null, EntryRef("cmd", 0, "edit"), false, ticket)
        }

        assertTrue(rig.undoAll("cmd") is UndoResult.Complete)

        assertEquals("v0", rig.store.get("a")?.value)
        assertEquals("label-0", rig.store.get("a")?.label)
    }

    @Test
    fun aDeletedParentComesBackWithItsIdAndItsChildren() {
        val rig = Rig()
        rig.store.put("c1", "first")
        rig.store.put("c2", "second")
        rig.store.put("p", "parent", "pl", listOf("c1", "c2"))
        runSuspending {
            val ticket = rig.journal.newTicket()
            ticket.capture("item", "p")
            ticket.touches("item", "c1")
            ticket.touches("item", "c2")
            rig.store.remove("p")
            ticket.settle("item", "p")
            rig.journal.record("cmd", null, EntryRef("cmd", 0, "delete"), false, ticket)
        }
        assertNull(rig.store.get("p"))
        assertNull(rig.store.get("c1"))

        val result = rig.undoAll("cmd")

        assertTrue(result.toString(), result is UndoResult.Complete)
        val parent = assertNotNull(rig.store.get("p"))
        assertEquals("parent", parent.value)
        assertEquals(listOf("c1", "c2"), parent.children)
        assertEquals("first", rig.store.get("c1")?.value)
        assertEquals("second", rig.store.get("c2")?.value)
    }

    @Test
    fun twoAdaptersRestoreInReverseJournalOrderEachThroughItsOwnAdapter() {
        val log = Collections.synchronizedList(ArrayList<String>())
        val folders = TestEntityStore()
        val items = TestEntityStore()
        val folderAdapter = TestAdapter(folders, "folder", log)
        val itemAdapter = TestAdapter(items, "item", log)
        val journal = UndoJournal {
            adapter(folderAdapter)
            adapter(itemAdapter)
        }
        runSuspending {
            val first = journal.newTicket()
            folders.put("f", "folder")
            first.created("folder", "f")
            journal.record("cmd", null, EntryRef("cmd", 0, "add_folder"), false, first)
            val second = journal.newTicket()
            items.put("i", "item-in-f")
            second.created("item", "i")
            journal.record("cmd", null, EntryRef("cmd", 1, "add_item"), false, second)
        }

        val result = runSuspending { journal.undoAll("cmd") }

        assertTrue(result.toString(), result is UndoResult.Complete)
        val restores = log.filter { it.startsWith("restoreIf") }
        assertEquals(listOf("restoreIf:i", "restoreIf:f"), restores)
        assertNull(folders.get("f"))
        assertNull(items.get("i"))
        assertEquals(
            listOf(EntryRef("cmd", 1, "add_item"), EntryRef("cmd", 0, "add_folder")),
            (result as UndoResult.Complete).restored,
        )
    }

    private fun <T : Any> assertNotNull(value: T?): T {
        org.junit.Assert.assertNotNull(value)
        return checkNotNull(value)
    }
}
