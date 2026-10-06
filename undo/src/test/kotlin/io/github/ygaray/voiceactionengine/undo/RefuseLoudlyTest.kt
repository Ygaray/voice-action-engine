package io.github.ygaray.voiceactionengine.undo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** An undo that cannot be done safely refuses, names why, and writes nothing, even for entities that were fine. */
class RefuseLoudlyTest {

    /** Wraps an adapter so a test can make its reads or fingerprints throw, like a database that went away. */
    private class FaultyAdapter(private val inner: TestAdapter) : EntityAdapter {
        @Volatile var failRead = false

        @Volatile var failFingerprint = false
        override val entityType: String get() = inner.entityType

        override suspend fun read(id: String): Any? {
            check(!failRead) { "secret read failure text" }
            return inner.read(id)
        }

        override suspend fun fingerprint(id: String): String? {
            check(!failFingerprint) { "secret fingerprint failure text" }
            return inner.fingerprint(id)
        }

        override suspend fun restoreIf(id: String, expectedFingerprint: String?, snapshot: Any?): Boolean =
            inner.restoreIf(id, expectedFingerprint, snapshot)
    }

    private fun refused(result: UndoResult): List<Blocker> {
        assertTrue(result.toString(), result is UndoResult.Refused)
        return (result as UndoResult.Refused).blockers
    }

    private fun item(id: String) = EntityKey("item", id)

    @Test
    fun anEntityEditedSinceIsRefusedAndNothingIsWritten() {
        var compensated = 0
        val store = TestEntityStore().apply { put("a", "v0") }
        val adapter = TestAdapter(store)
        val journal = UndoJournal {
            adapter(adapter)
            compensator("alarm") { compensated++ }
        }
        runSuspending {
            val ticket = journal.newTicket()
            ticket.capture("item", "a")
            store.put("a", "v1")
            ticket.settle("item", "a")
            ticket.compensate("alarm", "payload")
            journal.record("cmd", null, EntryRef("cmd", 0, "edit"), false, ticket)
        }
        store.put("a", "edited later")
        val before = store.dump()

        val blockers = refused(runSuspending { journal.undoAll("cmd") })

        val blocker = blockers.single()
        assertEquals(EntryRef("cmd", 0, "edit"), blocker.entry)
        assertEquals(item("a"), blocker.entity)
        assertEquals(UndoReason.CHANGED_SINCE, blocker.reason)
        assertEquals(before, store.dump())
        assertEquals(0, adapter.restoreCalls())
        assertEquals(0, compensated)
    }

    @Test
    fun anEntityDeletedSinceIsRefused() {
        val rig = Rig()
        rig.store.put("a", "v0")
        rig.edit("cmd", 0, "a", "v1")
        rig.store.remove("a")
        val before = rig.store.dump()

        val blocker = refused(rig.undoAll("cmd")).single()

        assertEquals(UndoReason.CHANGED_SINCE, blocker.reason)
        assertEquals(item("a"), blocker.entity)
        assertEquals(before, rig.store.dump())
        assertEquals(0, rig.adapter.restoreCalls())
    }

    @Test
    fun anEntityRecreatedSinceIsRefused() {
        val rig = Rig()
        rig.store.put("a", "v0")
        rig.edit("cmd", 0, "a", "v1")
        rig.store.remove("a")
        rig.store.put("a", "v1")
        val before = rig.store.dump()

        val blocker = refused(rig.undoAll("cmd")).single()

        assertEquals(UndoReason.CHANGED_SINCE, blocker.reason)
        assertEquals(before, rig.store.dump())
        assertEquals(0, rig.adapter.restoreCalls())
    }

    @Test
    fun oneMovedOnEntityStopsTheWholeGroupSoTheOtherIsNotRestored() {
        val rig = Rig()
        rig.store.put("a", "a0")
        rig.store.put("b", "b0")
        rig.edit("cmd", 0, "a", "a1")
        rig.edit("cmd", 1, "b", "b1")
        rig.store.put("b", "b-later")
        val before = rig.store.dump()

        val blocker = refused(rig.undoAll("cmd")).single()

        assertEquals(item("b"), blocker.entity)
        assertEquals("a1", rig.store.get("a")?.value)
        assertEquals(before, rig.store.dump())
        assertEquals(0, rig.adapter.restoreCalls())
    }

    @Test
    fun aFailedEntryThatNeverSettledIsUnverifiableUnlessTheEntityIsStillAsItWas() {
        val untouched = Rig()
        untouched.store.put("a", "v0")
        runSuspending {
            val ticket = untouched.journal.newTicket()
            ticket.capture("item", "a")
            untouched.journal.record("cmd", null, EntryRef("cmd", 0, "edit"), true, ticket)
        }
        val ok = untouched.undoAll("cmd")
        assertTrue(ok.toString(), ok is UndoResult.Complete)
        assertEquals(0, untouched.adapter.restoreCalls())

        val written = Rig()
        written.store.put("a", "v0")
        runSuspending {
            val ticket = written.journal.newTicket()
            ticket.capture("item", "a")
            written.store.put("a", "half written")
            written.journal.record("cmd", null, EntryRef("cmd", 0, "edit"), true, ticket)
        }
        val blocker = refused(written.undoAll("cmd")).single()
        assertEquals(UndoReason.UNVERIFIABLE, blocker.reason)
        assertEquals(item("a"), blocker.entity)
        assertEquals("half written", written.store.get("a")?.value)
        assertEquals(0, written.adapter.restoreCalls())
    }

    @Test
    fun aFailedEntryThatCapturedNothingIsUnverifiableUnlessItSaidNothingWasWritten() {
        val rig = Rig()
        runSuspending {
            rig.journal.record("cmd", null, EntryRef("cmd", 0, "edit"), true, rig.journal.newTicket())
        }
        val blocker = refused(rig.undoAll("cmd")).single()
        assertEquals(UndoReason.UNVERIFIABLE, blocker.reason)
        assertEquals(EntryRef("cmd", 0, "edit"), blocker.entry)
        assertNull(blocker.entity)

        val declared = Rig()
        runSuspending {
            val ticket = declared.journal.newTicket()
            ticket.nothingWritten()
            declared.journal.record("cmd", null, EntryRef("cmd", 0, "edit"), true, ticket)
        }
        assertTrue(declared.undoAll("cmd") is UndoResult.Complete)
    }

    @Test
    fun anAdapterThatThrewDuringCaptureLeavesAnUnverifiableEntity() {
        val store = TestEntityStore().apply { put("a", "v0") }
        val faulty = FaultyAdapter(TestAdapter(store))
        val journal = UndoJournal { adapter(faulty) }
        runSuspending {
            val ticket = journal.newTicket()
            faulty.failRead = true
            ticket.capture("item", "a")
            faulty.failRead = false
            store.put("a", "v1")
            ticket.settle("item", "a")
            journal.record("cmd", null, EntryRef("cmd", 0, "edit"), false, ticket)
        }

        val blocker = refused(runSuspending { journal.undoAll("cmd") }).single()

        assertEquals(UndoReason.UNVERIFIABLE, blocker.reason)
        assertEquals(item("a"), blocker.entity)
        assertEquals("v1", store.get("a")?.value)
    }

    @Test
    fun aLiveStateThatCannotBeReadIsUnverifiableAndNothingIsWritten() {
        val store = TestEntityStore().apply { put("a", "v0") }
        val faulty = FaultyAdapter(TestAdapter(store))
        val journal = UndoJournal { adapter(faulty) }
        runSuspending {
            val ticket = journal.newTicket()
            ticket.capture("item", "a")
            store.put("a", "v1")
            ticket.settle("item", "a")
            journal.record("cmd", null, EntryRef("cmd", 0, "edit"), false, ticket)
        }
        faulty.failFingerprint = true

        val blocker = refused(runSuspending { journal.undoAll("cmd") }).single()

        assertEquals(UndoReason.UNVERIFIABLE, blocker.reason)
        assertFalse(blocker.toString().contains("secret"))
        assertEquals("v1", store.get("a")?.value)
    }

    @Test
    fun aCapturedTypeWithNoAdapterIsRefusedNamingTheEntity() {
        val rig = Rig()
        runSuspending {
            val ticket = rig.journal.newTicket()
            ticket.capture("ghost", "x")
            ticket.settle("ghost", "x")
            rig.journal.record("cmd", null, EntryRef("cmd", 0, "edit"), false, ticket)
        }

        val blocker = refused(rig.undoAll("cmd")).single()

        assertEquals(UndoReason.NO_ADAPTER, blocker.reason)
        assertEquals(EntityKey("ghost", "x"), blocker.entity)
    }

    @Test
    fun blockersComeNewestEntryFirstThenEntityTypeAndId() {
        val rig = Rig()
        rig.store.put("a", "a0")
        rig.store.put("b", "b0")
        rig.store.put("c", "c0")
        runSuspending {
            val first = rig.journal.newTicket()
            first.capture("item", "c")
            first.capture("item", "b")
            rig.store.put("c", "c1")
            rig.store.put("b", "b1")
            first.settle("item", "c")
            first.settle("item", "b")
            rig.journal.record("cmd", null, EntryRef("cmd", 0, "first"), false, first)
        }
        rig.edit("cmd", 1, "a", "a1")
        rig.store.put("a", "later")
        rig.store.put("b", "later")
        rig.store.put("c", "later")

        val blockers = refused(rig.undoAll("cmd"))

        assertEquals(listOf(item("a"), item("b"), item("c")), blockers.map { it.entity })
        assertEquals(listOf(1, 0, 0), blockers.map { it.entry?.position })
        val again = refused(rig.undoAll("cmd"))
        assertEquals(blockers.map { it.entity }, again.map { it.entity })
    }

    @Test
    fun aWriteThatLeftTheFingerprintUnchangedIsAHarmlessNoOp() {
        val rig = Rig()
        rig.store.put("a", "v0")
        runSuspending {
            val ticket = rig.journal.newTicket()
            ticket.capture("item", "a")
            ticket.settle("item", "a")
            rig.journal.record("cmd", null, EntryRef("cmd", 0, "edit"), false, ticket)
        }

        val result = rig.undoAll("cmd")

        assertTrue(result.toString(), result is UndoResult.Complete)
        assertEquals("v0", rig.store.get("a")?.value)
        assertEquals(0, rig.adapter.restoreCalls())
    }

    @Test
    fun anEntityCreatedAndDeletedWithinOneCommandNeedsNoWrite() {
        val rig = Rig()
        runSuspending {
            val ticket = rig.journal.newTicket()
            ticket.capture("item", "t")
            rig.store.put("t", "temp")
            rig.store.remove("t")
            ticket.settle("item", "t")
            rig.journal.record("cmd", null, EntryRef("cmd", 0, "edit"), false, ticket)
        }

        val result = rig.undoAll("cmd")

        assertTrue(result.toString(), result is UndoResult.Complete)
        assertNull(rig.store.get("t"))
        assertEquals(0, rig.adapter.restoreCalls())
    }

    @Test
    fun anEntryThatDeclaredNothingIsCompleteWithoutTouchingAnAdapter() {
        val rig = Rig()
        runSuspending {
            rig.journal.record("cmd", null, EntryRef("cmd", 0, "noop"), false, rig.journal.newTicket())
        }

        val result = rig.undoAll("cmd")

        assertTrue(result.toString(), result is UndoResult.Complete)
        assertEquals(listOf(EntryRef("cmd", 0, "noop")), (result as UndoResult.Complete).restored)
        assertTrue(rig.adapter.log.isEmpty())
    }
}
