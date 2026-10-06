package io.github.ygaray.voiceactionengine.undo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A restore that fails after verification gives an exact Partial, and a retry finishes only the remainder. */
class PartialRestoreTest {

    private fun item(id: String) = EntityKey("item", id)

    private fun entry(group: String, position: Int) = EntryRef(group, position, "edit")

    private fun partial(result: UndoResult): UndoResult.Partial {
        assertTrue(result.toString(), result is UndoResult.Partial)
        return result as UndoResult.Partial
    }

    private fun assertNotRestored(
        item: NotRestored,
        entry: EntryRef,
        entity: EntityKey?,
        reason: UndoReason,
        errorClass: String?,
    ) {
        assertEquals(entry, item.entry)
        assertEquals(entity, item.entity)
        assertNull(item.compensator)
        assertEquals(reason, item.reason)
        assertEquals(errorClass, item.errorClass)
    }

    @Test
    fun aFailureStopsItsComponentAndSkipsTheKeysAfterIt() {
        val rig = Rig()
        rig.store.put("a", "a0")
        rig.store.put("b", "b0")
        rig.editAll("cmd", 0, listOf("a", "b"), "v1")
        rig.adapter.throwOnRestore.add("b")

        val result = partial(rig.undoAll("cmd"))

        assertEquals(emptyList<EntryRef>(), result.restored)
        assertEquals(2, result.notRestored.size)
        val (first, second) = result.notRestored
        assertNotRestored(first, entry("cmd", 0), item("b"), UndoReason.RESTORE_FAILED, "IllegalStateException")
        assertNotRestored(second, entry("cmd", 0), item("a"), UndoReason.SKIPPED_AFTER_FAILURE, null)
        assertEquals("v1", rig.store.get("a")?.value)
        assertEquals(listOf("restoreIf:b"), rig.adapter.log.filter { it.startsWith("restoreIf") })
    }

    @Test
    fun anIndependentComponentStillRestoresWhenAnotherFails() {
        val rig = Rig()
        rig.store.put("x", "x0")
        rig.store.put("y", "y0")
        rig.edit("cmd", 0, "x", "x1")
        rig.edit("cmd", 1, "y", "y1")
        rig.adapter.throwOnRestore.add("y")

        val result = partial(rig.undoAll("cmd"))

        assertEquals(listOf(entry("cmd", 0)), result.restored)
        val failed = result.notRestored.single()
        assertNotRestored(failed, entry("cmd", 1), item("y"), UndoReason.RESTORE_FAILED, "IllegalStateException")
        assertEquals("x0", rig.store.get("x")?.value)
        assertEquals("y1", rig.store.get("y")?.value)
    }

    @Test
    fun aRestoreThatLosesTheRaceIsChangedSinceAndNothingIsClobbered() {
        val rig = Rig()
        rig.store.put("x", "x0")
        rig.store.put("y", "y0")
        rig.edit("cmd", 0, "x", "x1")
        rig.edit("cmd", 1, "y", "y1")
        rig.adapter.raceOnRestore.add("y")

        val result = partial(rig.undoAll("cmd"))

        assertEquals(listOf(entry("cmd", 0)), result.restored)
        assertNotRestored(result.notRestored.single(), entry("cmd", 1), item("y"), UndoReason.CHANGED_SINCE, null)
        assertEquals("raced", rig.store.get("y")?.value)
        assertEquals("x0", rig.store.get("x")?.value)
    }

    @Test
    fun aRetryRestoresOnlyTheRemainderAndNeverTouchesADoneKey() {
        val rig = Rig()
        rig.store.put("x", "x0")
        rig.store.put("y", "y0")
        rig.edit("cmd", 0, "x", "x1")
        rig.edit("cmd", 1, "y", "y1")
        rig.adapter.throwOnRestore.add("y")
        partial(rig.undoAll("cmd"))
        rig.adapter.throwOnRestore.clear()

        val retry = rig.undoAll("cmd")

        assertTrue(retry.toString(), retry is UndoResult.Complete)
        assertEquals(listOf(entry("cmd", 1)), (retry as UndoResult.Complete).restored)
        assertEquals("y0", rig.store.get("y")?.value)
        assertEquals(1, rig.adapter.restoreCalls("x"))
        assertEquals(2, rig.adapter.restoreCalls("y"))
        assertTrue(rig.undoAll("cmd") is UndoResult.AlreadyUndone)
    }

    @Test
    fun aKeyAlreadyAsItWasAtRetryCountsAsRestoredWithoutACall() {
        val rig = Rig()
        rig.store.put("x", "x0")
        rig.edit("cmd", 0, "x", "x1")
        rig.adapter.cancelAfterWrite.add("x")
        runCatching { rig.undoAll("cmd") }
        rig.adapter.cancelAfterWrite.clear()
        assertEquals("x0", rig.store.get("x")?.value)
        val callsBefore = rig.adapter.restoreCalls("x")

        val retry = rig.undoAll("cmd")

        assertTrue(retry.toString(), retry is UndoResult.Complete)
        assertEquals(listOf(entry("cmd", 0)), (retry as UndoResult.Complete).restored)
        assertEquals(callsBefore, rig.adapter.restoreCalls("x"))
    }

    @Test
    fun theFaultMessageAppearsNowhereInTheResult() {
        val rig = Rig()
        rig.store.put("a", "a0")
        rig.store.put("b", "b0")
        rig.editAll("cmd", 0, listOf("a", "b"), "v1")
        rig.adapter.throwOnRestore.add("b")

        val result = partial(rig.undoAll("cmd"))

        val text = buildString {
            append(result)
            result.notRestored.forEach {
                append(it)
                append(it.errorClass)
                append(it.entity)
                append(it.reason)
            }
        }
        assertFalse(text, text.contains(CANARY))
        assertFalse(text, text.contains("canary"))
        assertEquals("IllegalStateException", result.notRestored.first().errorClass)
    }
}
