package io.github.ygaray.voiceactionengine.undo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** The result set is closed (four members, frozen at the release); reasons are open. */
class ResultShapeTest {

    private val ref = EntryRef("run", 0, "tool")
    private val key = EntityKey("item", "secret-id")

    @Test
    fun theFourMembersAreTheOnlyNestedSubclasses() {
        val base = UndoResult::class.java
        val members = base.declaredClasses.filter { base.isAssignableFrom(it) && it != base }.map { it.simpleName }
        assertEquals(setOf("Complete", "Refused", "Partial", "AlreadyUndone"), members.toSet())
        assertEquals(4, members.size)
    }

    // No `else`: this stops compiling the day a fifth member appears.
    private fun codeOf(result: UndoResult): String = when (result) {
        is UndoResult.Complete -> "complete"
        is UndoResult.Refused -> "refused"
        is UndoResult.Partial -> "partial"
        is UndoResult.AlreadyUndone -> "already_undone"
    }

    @Test
    fun anExhaustiveWhenMapsEachMemberToItsCode() {
        val blocker = Blocker(ref, key, UndoReason.CHANGED_SINCE)
        val notRestored = NotRestored(ref, key, null, UndoReason.RESTORE_FAILED, "IllegalStateException")
        val all = listOf(
            UndoResult.Complete(listOf(ref)),
            UndoResult.Refused(listOf(blocker)),
            UndoResult.Partial(emptyList(), listOf(notRestored)),
            UndoResult.AlreadyUndone(),
        )
        assertEquals(listOf("complete", "refused", "partial", "already_undone"), all.map { codeOf(it) })
        assertEquals(all.map { codeOf(it) }, all.map { it.code })
    }

    @Test
    fun theTwelveReasonsKeepTheirWireValues() {
        val expected = mapOf(
            UndoReason.CHANGED_SINCE to "changed_since",
            UndoReason.CHAIN_BROKEN to "chain_broken",
            UndoReason.UNVERIFIABLE to "unverifiable",
            UndoReason.ENTANGLED to "entangled",
            UndoReason.JOURNAL_WITHHELD to "journal_withheld",
            UndoReason.UNKNOWN_GROUP to "unknown_group",
            UndoReason.UNKNOWN_ENTRY to "unknown_entry",
            UndoReason.IN_PROGRESS to "in_progress",
            UndoReason.NO_ADAPTER to "no_adapter",
            UndoReason.RESTORE_FAILED to "restore_failed",
            UndoReason.COMPENSATOR_FAILED to "compensator_failed",
            UndoReason.SKIPPED_AFTER_FAILURE to "skipped_after_failure",
        )
        assertEquals(12, expected.size)
        expected.forEach { (reason, wire) -> assertEquals(wire, reason.value) }
    }

    // A reason set is open, so a `when` over it must keep an `else` to compile.
    private fun isStale(reason: UndoReason): Boolean = when (reason) {
        UndoReason.CHANGED_SINCE, UndoReason.CHAIN_BROKEN -> true
        else -> false
    }

    @Test
    fun aWhenOverReasonsNeedsAnElseBranch() {
        assertTrue(isStale(UndoReason.CHANGED_SINCE))
        assertFalse(isStale(UndoReason.NO_ADAPTER))
    }

    @Test
    fun refusedAndPartialRejectEmptyLists() {
        assertThrows(IllegalArgumentException::class.java) { UndoResult.Refused(emptyList()) }
        assertThrows(IllegalArgumentException::class.java) { UndoResult.Partial(listOf(ref), emptyList()) }
    }

    @Test
    fun resultsCopyTheirListsSoALaterChangeCannotLeakIn() {
        val restored = mutableListOf(ref)
        val result = UndoResult.Complete(restored)
        restored.clear()
        assertEquals(listOf(ref), result.restored)
    }

    @Test
    fun everyPublicTypePrintsCountsAndNamesOnly() {
        val blocker = Blocker(ref, key, UndoReason.CHANGED_SINCE)
        val notRestored = NotRestored(ref, key, "alarm", UndoReason.COMPENSATOR_FAILED, "IllegalStateException")
        val printed = listOf(
            key.toString(),
            blocker.toString(),
            notRestored.toString(),
            UndoResult.Complete(listOf(ref)).toString(),
            UndoResult.Refused(listOf(blocker)).toString(),
            UndoResult.Partial(listOf(ref), listOf(notRestored)).toString(),
            UndoResult.AlreadyUndone().toString(),
        )
        printed.forEach { assertFalse(it, it.contains("secret-id")) }
        assertEquals("EntityKey(type=item, idLength=9)", key.toString())
        assertEquals("Refused(blockers=1)", UndoResult.Refused(listOf(blocker)).toString())
        assertEquals(
            "Partial(restored=1, notRestored=1)",
            UndoResult.Partial(listOf(ref), listOf(notRestored)).toString(),
        )
        assertEquals("Complete(restored=1)", UndoResult.Complete(listOf(ref)).toString())
    }

    @Test
    fun anEntryRefIsEqualByRunAndPositionAndRejectsBadInput() {
        assertEquals(EntryRef("run", 1, "a"), EntryRef("run", 1, "b"))
        assertEquals(EntryRef("run", 1, "a").hashCode(), EntryRef("run", 1, "b").hashCode())
        assertFalse(EntryRef("run", 1, "a") == EntryRef("run", 2, "a"))
        assertThrows(IllegalArgumentException::class.java) { EntryRef(" ", 0, "a") }
        assertThrows(IllegalArgumentException::class.java) { EntryRef("run", -1, "a") }
        assertThrows(IllegalArgumentException::class.java) { EntryRef("run", 0, "x".repeat(257)) }
        assertEquals("EntryRef(runId=run, position=1, toolName=a)", EntryRef("run", 1, "a").toString())
    }
}
