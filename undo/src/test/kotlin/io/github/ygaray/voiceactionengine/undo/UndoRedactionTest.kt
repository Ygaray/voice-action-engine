package io.github.ygaray.voiceactionengine.undo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** No user data reaches a toString, a result field or an exception message of `:undo`. */
class UndoRedactionTest {

    private class CanarySnapshot(val value: String) {
        override fun toString(): String = "CANARY-snapshot-$value"
    }

    /** An adapter whose ids, snapshots, fingerprints and fault messages all carry the canary. */
    private class CanaryAdapter : EntityAdapter {
        override val entityType: String = "note"
        val live = HashMap<String, String>()
        var failRestore = false

        override suspend fun read(id: String): Any? = live[id]?.let { CanarySnapshot(it) }

        override suspend fun fingerprint(id: String): String? = live[id]?.let { "CANARY-fingerprint-$it" }

        override suspend fun restoreIf(id: String, expectedFingerprint: String?, snapshot: Any?): Boolean {
            check(!failRestore) { "CANARY-adapter-fault" }
            if (fingerprint(id) != expectedFingerprint) return false
            if (snapshot == null) live.remove(id) else live[id] = (snapshot as CanarySnapshot).value
            return true
        }
    }

    private class CanaryStore : JournalStore {
        override suspend fun save(group: UndoGroup) = error("CANARY-store-fault")

        override suspend fun delete(groupKey: String) = error("CANARY-store-fault")
    }

    private val adapter = CanaryAdapter()
    private var compensatorFails = false
    private val journal = UndoJournal {
        adapter(adapter)
        compensator("alarm") { payload ->
            check(!compensatorFails) { "CANARY-compensator-fault $payload" }
        }
        store = CanaryStore()
    }
    private val seen = ArrayList<String>()

    private fun note(label: String, text: String) {
        seen.add("$label=$text")
        assertFalse("$label leaked: $text", text.contains("CANARY"))
    }

    private fun apply(group: String, position: Int, id: String, value: String, payload: String? = null) =
        runSuspending {
            val ticket = journal.newTicket()
            ticket.capture("note", id)
            adapter.live[id] = value
            ticket.settle("note", id)
            if (payload != null) ticket.compensate("alarm", payload)
            note("ticket", ticket.toString())
            journal.record(group, "CANARY-parent", EntryRef("run", position, "edit"), false, ticket)
        }

    private fun describe(result: UndoResult) {
        note("result", result.toString())
        note("code", result.code)
        when (result) {
            is UndoResult.Complete -> result.restored.forEach { note("entry", it.toString()) }
            is UndoResult.Refused -> result.blockers.forEach {
                note("blocker", it.toString())
                note("blocker.reason", it.reason.toString())
                it.entity?.let { key -> note("blocker.entity", key.toString()) }
                it.entry?.let { entry -> note("blocker.entry", entry.toString()) }
            }
            is UndoResult.Partial -> {
                result.restored.forEach { note("entry", it.toString()) }
                result.notRestored.forEach {
                    note("notRestored", it.toString())
                    note("notRestored.entry", it.entry.toString())
                    it.entity?.let { key -> note("notRestored.entity", key.toString()) }
                    it.errorClass?.let { name ->
                        note("errorClass", name)
                        assertTrue("not a simple class name: $name", name.matches(Regex("[A-Za-z0-9_$.-]+")))
                    }
                }
            }
            is UndoResult.AlreadyUndone -> Unit
        }
    }

    private fun undoAll(group: String): UndoResult = runSuspending { journal.undoAll(group) }

    @Test
    fun noPublicStringResultOrMessageCarriesTheCanary() {
        // Complete, with an effect outside the database.
        adapter.live["CANARY-id-1"] = "CANARY-value-0"
        apply("CANARY-group-1", 0, "CANARY-id-1", "CANARY-value-1", payload = "CANARY-payload")
        note("group", checkNotNull(runSuspending { journal.group("CANARY-group-1") }).toString())
        describe(undoAll("CANARY-group-1"))
        describe(undoAll("CANARY-group-1"))

        // Refused: the entity moved on.
        adapter.live["CANARY-id-2"] = "CANARY-value-0"
        apply("CANARY-group-2", 0, "CANARY-id-2", "CANARY-value-1")
        adapter.live["CANARY-id-2"] = "CANARY-value-later"
        describe(undoAll("CANARY-group-2"))

        // Partial: the adapter throws, and then a compensator throws.
        adapter.live["CANARY-id-3"] = "CANARY-value-0"
        apply("CANARY-group-3", 0, "CANARY-id-3", "CANARY-value-1", payload = "CANARY-payload")
        adapter.failRestore = true
        describe(undoAll("CANARY-group-3"))
        adapter.failRestore = false
        compensatorFails = true
        describe(undoAll("CANARY-group-3"))
        compensatorFails = false

        // Refused for the whole group, and for one action.
        runSuspending { journal.withhold("CANARY-group-4") }
        describe(undoAll("CANARY-group-4"))
        describe(undoAll("CANARY-unknown"))
        describe(runSuspending { journal.undoEntry("CANARY-group-3", EntryRef("run", 9, "edit")) })

        note("journal", journal.toString())
        assertTrue("the store threw on every call", journal.storeFaults > 0)
        assertTrue(seen.size > 20)
    }

    private fun messageOf(block: () -> Unit): String {
        try {
            block()
        } catch (e: IllegalArgumentException) {
            return e.message.orEmpty()
        }
        throw AssertionError("an invalid value must be rejected")
    }

    @Test
    fun anOverLongKeyIsRejectedWithoutEchoingIt() {
        val long = "CANARY-" + "x".repeat(300)
        listOf(
            messageOf { runSuspending { journal.undoAll(long) } },
            messageOf { runSuspending { journal.group(long) } },
            messageOf { runSuspending { journal.withhold(long) } },
            messageOf { runSuspending { journal.runClosed("g", long, emptySet()) } },
            messageOf { runSuspending { journal.undoEntry(long, EntryRef("g", 0, "t")) } },
            messageOf { EntryRef(long, 0, "t") },
            messageOf { EntryRef("g", 0, long) },
            messageOf { runSuspending { journal.newTicket().capture("note", long) } },
            messageOf { runSuspending { journal.newTicket().capture(long, "id") } },
            messageOf { runSuspending { journal.newTicket().compensate(long, "payload") } },
            messageOf { runSuspending { journal.record(long, null, EntryRef("g", 0, "t"), false, null) } },
            messageOf { runSuspending { journal.record("g", long, EntryRef("g", 0, "t"), false, null) } },
        ).forEach { message ->
            assertFalse("an exception message echoed a key: $message", message.contains("CANARY"))
            assertTrue(message.isNotBlank())
        }
    }

    @Test
    fun aRecordedTicketRejectsFurtherCallsWithoutEchoingAnything() {
        val ticket = journal.newTicket().also { it.nothingWritten() }
        runSuspending { journal.record("CANARY-group", null, EntryRef("g", 0, "t"), false, ticket) }

        try {
            runSuspending { ticket.capture("note", "CANARY-id") }
            throw AssertionError("a recorded ticket must be closed")
        } catch (expected: IllegalStateException) {
            assertFalse(expected.message.orEmpty().contains("CANARY"))
        }
        assertEquals("UndoTicket(entities=0, touches=0, compensations=0, sealed=true)", ticket.toString())
    }
}
