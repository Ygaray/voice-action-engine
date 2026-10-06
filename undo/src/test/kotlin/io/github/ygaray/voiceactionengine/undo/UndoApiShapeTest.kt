package io.github.ygaray.voiceactionengine.undo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.lang.reflect.Modifier
import java.util.zip.ZipFile

/** Directory-wide public-surface lint over every compiled `:undo` main class, mirroring the engine's own sweep. */
class UndoApiShapeTest {

    /** Binary class names under the root package; main may be on the test classpath as a directory or a jar. */
    private fun mainClassNames(): List<String> {
        val location = File(UndoJournal::class.java.protectionDomain.codeSource.location.toURI())
        val relative = if (location.isDirectory) {
            location.walkTopDown()
                .filter { it.isFile }
                .map { it.relativeTo(location).path.replace(File.separatorChar, '/') }
                .toList()
        } else {
            ZipFile(location).use { zip -> zip.entries().toList().filter { !it.isDirectory }.map { it.name } }
        }
        return relative
            .filter { it.endsWith(".class") }
            .map { it.removeSuffix(".class").replace('/', '.') }
            .filter { it.startsWith(ROOT_PACKAGE) }
    }

    private fun allMainClasses(): List<Class<*>> {
        val loader = UndoJournal::class.java.classLoader
        return mainClassNames().map { Class.forName(it, false, loader) }
    }

    @Test
    fun sweepIsNotVacuous() {
        val classes = allMainClasses()
        assertTrue("UndoJournal must be among the swept classes", classes.any { it == UndoJournal::class.java })
        assertTrue("UndoResult must be among the swept classes", classes.any { it == UndoResult::class.java })
        val sources = File("src/main/kotlin")
        if (sources.isDirectory) {
            val ktFiles = sources.walkTopDown().count { it.isFile && it.extension == "kt" }
            assertTrue("swept ${classes.size} classes for $ktFiles source files", classes.size >= ktFiles)
        }
    }

    @Test
    fun noMainClassIsAnEnum() {
        val enums = allMainClasses().filter { it.isEnum }.map { it.name }
        assertTrue("enums freeze the set of constants into the public API: $enums", enums.isEmpty())
    }

    @Test
    fun noMainClassIsDataShaped() {
        val dataShaped = allMainClasses().filter { c ->
            val names = c.declaredMethods.map { it.name }
            "copy" in names && "component1" in names
        }.map { it.name }
        assertTrue("copy/componentN classes freeze their constructor shape: $dataShaped", dataShaped.isEmpty())
    }

    @Test
    fun noMainClassLeaksAPublicStaticFieldBesidesInstanceAndCompanion() {
        val leaks = allMainClasses().flatMap { c ->
            c.declaredFields
                .filter { Modifier.isPublic(it.modifiers) && Modifier.isStatic(it.modifiers) }
                .filter { it.name != "INSTANCE" && it.name != "Companion" }
                .map { "${c.name}.${it.name}" }
        }
        assertTrue("public static fields are frozen into the API: $leaks", leaks.isEmpty())
    }

    @Test
    fun noMainClassDeclaresADefaultArgumentConstructorStub() {
        val inspected = allMainClasses()
        assertTrue("inspected only ${inspected.size} classes", inspected.size >= MIN_INSPECTED)
        val flagged = inspected.filter { hasDefaultArgumentStub(it) }.map { it.name }
        assertTrue("default-argument stubs freeze the constructor shape: $flagged", flagged.isEmpty())
    }

    @Test
    fun theStubPredicateFlagsADefaultArgumentClass() {
        assertTrue(hasDefaultArgumentStub(WithDefaultArgument::class.java))
        assertTrue(!hasDefaultArgumentStub(UndoReason::class.java))
        assertTrue(!hasDefaultArgumentStub(EntryRef::class.java))
    }

    @Test
    fun theTicketAndAdapterMethodSetsAreExactlyThePlannedOnes() {
        val ticket = UndoTicket::class.java.declaredMethods
            .filter { Modifier.isPublic(it.modifiers) && !it.isSynthetic && '$' !in it.name } // `$` marks internal
            .map { it.name }
            .toSet()
        assertEquals(
            setOf("capture", "settle", "created", "touches", "compensate", "nothingWritten", "toString"),
            ticket,
        )
        val adapter = EntityAdapter::class.java.declaredMethods.map { it.name }.toSet()
        assertEquals(setOf("getEntityType", "read", "fingerprint", "restoreIf"), adapter)
    }

    /**
     * True when [cls] declares a default-argument constructor stub: a synthetic constructor ending in int and
     * DefaultConstructorMarker whose leading parameters equal those of a real constructor. Such a class cannot grow a
     * parameter without removing a constructor. A value class stub ends in the marker alone and is not flagged.
     */
    private fun hasDefaultArgumentStub(cls: Class<*>): Boolean {
        val constructors = cls.declaredConstructors
        val real = constructors.filter { !it.isSynthetic }.map { it.parameterTypes.toList() }
        return constructors.filter { it.isSynthetic }.any { stub ->
            val types = stub.parameterTypes.toList()
            types.size >= 2 &&
                types[types.size - 2] == Int::class.javaPrimitiveType &&
                types.last().name == DEFAULT_MARKER &&
                types.dropLast(2) in real
        }
    }

    private class WithDefaultArgument(val first: Int, val second: String = "x")

    private companion object {
        const val DEFAULT_MARKER = "kotlin.jvm.internal.DefaultConstructorMarker"
        const val ROOT_PACKAGE = "io.github.ygaray.voiceactionengine.undo"
        const val MIN_INSPECTED = 10
    }
}
