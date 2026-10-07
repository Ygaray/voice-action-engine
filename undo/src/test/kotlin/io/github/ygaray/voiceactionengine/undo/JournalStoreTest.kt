package io.github.ygaray.voiceactionengine.undo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.lang.reflect.Modifier
import java.util.zip.ZipFile
import kotlin.coroutines.cancellation.CancellationException

/** The optional store hears every change and every drop, and can never change what the journal does. */
class JournalStoreTest {

    private class RecordingStore : JournalStore {
        val saved = ArrayList<UndoGroup>()
        val deleted = ArrayList<String>()
        var saveFault: Throwable? = null
        var deleteFault: Throwable? = null

        override suspend fun save(group: UndoGroup) {
            saved.add(group)
            saveFault?.let { throw it }
        }

        override suspend fun delete(groupKey: String) {
            deleted.add(groupKey)
            deleteFault?.let { throw it }
        }
    }

    private fun rigWith(store: RecordingStore, configure: UndoJournal.Builder.() -> Unit = {}) =
        Rig(configure = {
            this.store = store
            configure()
        })

    private fun record(rig: Rig, group: String, position: Int = 0) = runSuspending {
        val ticket = rig.journal.newTicket().also { it.nothingWritten() }
        rig.journal.record(group, null, EntryRef(group, position, "t"), false, ticket)
    }

    @Test
    fun theStoreIsToldAfterEveryChangeWithRisingRevisions() {
        val store = RecordingStore()
        val rig = rigWith(store)
        rig.store.put("a", "v0")

        rig.edit("g", 0, "a", "v1")
        assertEquals(1, store.saved.size)
        val afterRecord = store.saved.last()
        assertEquals(1, afterRecord.count)

        runSuspending { rig.journal.runClosed("g", "g", setOf(0)) }
        assertEquals(2, store.saved.size)
        assertTrue(store.saved.last().revision > afterRecord.revision)

        assertTrue(rig.undoAll("g") is UndoResult.Complete)
        assertEquals(3, store.saved.size)
        assertEquals(0, store.saved.last().count)

        runSuspending { rig.journal.withhold("h") }
        assertEquals(4, store.saved.size)
        assertTrue(store.saved.last().withheld)
        assertEquals("h", store.saved.last().groupKey)
        assertEquals(0, rig.journal.storeFaults)
    }

    @Test
    fun aRefusedUndoWritesNothingSoTheStoreHearsNothing() {
        val store = RecordingStore()
        val rig = rigWith(store)
        rig.store.put("a", "v0")
        rig.edit("g", 0, "a", "v1")
        rig.store.put("a", "later")
        val before = store.saved.size

        assertTrue(rig.undoAll("g") is UndoResult.Refused)

        assertEquals(before, store.saved.size)
    }

    @Test
    fun theStoreIsToldWhenTheCountDropsAGroup() {
        val store = RecordingStore()
        val rig = rigWith(store) { maxGroups = 1 }
        record(rig, "g1")
        record(rig, "g2")

        assertEquals(listOf("g1"), store.deleted)
    }

    @Test
    fun theStoreIsToldWhenTheAgeDropsAGroup() {
        val store = RecordingStore()
        var now = 0L
        val rig = rigWith(store) {
            maxAgeMillis = 1000
            clock = { now }
        }
        record(rig, "g")

        now = 1001
        assertEquals(null, rig.group("g"))

        assertEquals(listOf("g"), store.deleted)
    }

    @Test
    fun aCancelledUndoStillTellsTheStoreWhatItAlreadyRestored() {
        val store = RecordingStore()
        val rig = rigWith(store)
        listOf("x", "y").forEachIndexed { position, id ->
            rig.store.put(id, "${id}0")
            rig.edit("g", position, id, "${id}1")
        }
        val before = store.saved.last()
        assertEquals(2, before.count)
        rig.adapter.cancelOnRestore.add("x")

        try {
            rig.undoAll("g")
            throw AssertionError("the cancellation must propagate")
        } catch (expected: CancellationException) {
            assertNotNull(expected)
        }

        val after = store.saved.last()
        assertEquals(1, after.count)
        assertTrue(after.revision > before.revision)
        assertEquals(0, rig.journal.storeFaults)
    }

    @Test
    fun aStoreThatThrowsChangesNothingAndEveryFaultIsCounted() {
        val store = RecordingStore().apply {
            saveFault = IllegalStateException(CANARY)
            deleteFault = IllegalStateException(CANARY)
        }
        val rig = rigWith(store) { maxGroups = 1 }
        rig.store.put("a", "v0")

        rig.edit("g", 0, "a", "v1")
        assertEquals(1, rig.journal.storeFaults)
        assertEquals(1, rig.groupOf("g").count)

        val result = rig.undoAll("g")
        assertTrue(result.toString(), result is UndoResult.Complete)
        assertEquals("v0", rig.store.get("a")?.value)
        assertEquals(2, rig.journal.storeFaults)

        record(rig, "other")
        assertEquals(4, rig.journal.storeFaults)
        assertFalse(rig.journal.toString().contains("canary"))
    }

    @Test
    fun aCancellationFromTheStoreReachesTheCallerAndIsNotAFault() {
        val store = RecordingStore().apply { saveFault = CancellationException(CANARY) }
        val rig = rigWith(store)

        try {
            record(rig, "g")
            throw AssertionError("the cancellation must propagate")
        } catch (expected: CancellationException) {
            assertNotNull(expected)
        }

        assertEquals(0, rig.journal.storeFaults)
        assertEquals(1, rig.groupOf("g").count)
    }

    @Test
    fun aCancellationFromADeleteReachesTheCallerToo() {
        val store = RecordingStore().apply { deleteFault = CancellationException(CANARY) }
        val rig = rigWith(store) { maxGroups = 1 }
        record(rig, "g1")

        try {
            record(rig, "g2")
            throw AssertionError("the cancellation must propagate")
        } catch (expected: CancellationException) {
            assertNotNull(expected)
        }

        assertEquals(0, rig.journal.storeFaults)
    }

    @Test
    fun withoutAStoreNothingIsCalledAndThereAreNoFaults() {
        val rig = Rig()
        record(rig, "g")
        rig.undoAll("g")

        assertEquals(0, rig.journal.storeFaults)
    }

    @Test
    fun theStoreSeamHasNoWayBackInAndTheViewCarriesNoUserData() {
        val seam = JournalStore::class.java.declaredMethods.map { it.name }.toSet()
        assertEquals(setOf("save", "delete"), seam)

        val allowed = setOf(String::class.java, Long::class.javaPrimitiveType, Boolean::class.javaPrimitiveType,
            Int::class.javaPrimitiveType, List::class.java)
        val surprising = UndoGroup::class.java.declaredMethods
            .filter { Modifier.isPublic(it.modifiers) && !it.isSynthetic && it.name != "toString" }
            .filter { it.returnType !in allowed }
            .map { it.name }
        assertTrue("members that could carry user data: $surprising", surprising.isEmpty())
    }

    // Phase 17 OI-1 ruling: the store is optional, and with none supplied the engine writes nothing to disk.
    @Test
    fun withNoStoreSuppliedTheEngineWritesNothingToDisk() {
        val workDir = File(System.getProperty("user.dir"))
        val scratch = java.nio.file.Files.createTempDirectory("undo-no-store").toFile()
        try {
            val before = treeOf(workDir) + treeOf(scratch)

            val rig = Rig()
            rig.store.put("a", "v0")
            rig.edit("g", 0, "a", "v1")
            runSuspending { rig.journal.runClosed("g", "g", setOf(0)) }
            assertTrue(rig.undoAll("g") is UndoResult.Complete)
            runSuspending { rig.journal.withhold("h") }

            assertEquals(before, treeOf(workDir) + treeOf(scratch))
            assertEquals(0, rig.journal.storeFaults)
            assertEquals(emptyList<String>(), mainClassesNamingFileIo())
        } finally {
            scratch.deleteRecursively()
        }
    }

    /** Every file under [root] with its size and time, except the build output and the Gradle cache. */
    private fun treeOf(root: File): Map<String, String> =
        root.walkTopDown()
            .onEnter { it.name != "build" && it.name != ".gradle" }
            .filter { it.isFile }
            .associate { it.relativeTo(root).path to "${it.length()}@${it.lastModified()}" }

    /** The compiled `:undo` main classes whose constant pool names a file-system class: none may. */
    private fun mainClassesNamingFileIo(): List<String> {
        val location = File(UndoJournal::class.java.protectionDomain.codeSource.location.toURI())
        val classes: Map<String, ByteArray> = if (location.isDirectory) {
            location.walkTopDown().filter { it.isFile && it.extension == "class" }
                .associate { it.relativeTo(location).path to it.readBytes() }
        } else {
            ZipFile(location).use { zip ->
                zip.entries().toList().filter { it.name.endsWith(".class") }
                    .associate { it.name to zip.getInputStream(it).readBytes() }
            }
        }
        assertTrue("no main classes found", classes.isNotEmpty())
        val needles = listOf("java/io/File", "java/nio/file/", "java/io/FileOutputStream", "java/io/RandomAccessFile")
        return classes.filter { (_, bytes) ->
            val text = String(bytes, Charsets.ISO_8859_1)
            needles.any { text.contains(it) }
        }.keys.sorted()
    }
}
