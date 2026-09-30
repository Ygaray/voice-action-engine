package io.github.ygaray.voiceactionengine.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.lang.reflect.Modifier
import java.util.zip.ZipFile

/** Directory-wide public-surface lint over every compiled main class; per-type shape checks live in each type test. */
class ApiShapeTest {

    /** Binary class names under the root package; main may be on the test classpath as a directory or a jar. */
    private fun mainClassNames(): List<String> {
        val location = File(CommandInput::class.java.protectionDomain.codeSource.location.toURI())
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
        val loader = CommandInput::class.java.classLoader
        return mainClassNames().map { Class.forName(it, false, loader) }
    }

    @Test
    fun sweepIsNotVacuous() {
        val classes = allMainClasses()
        assertTrue("CommandInput must be among the swept classes", classes.any { it == CommandInput::class.java })
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
    fun providerIdConstantsHaveTheWireValues() {
        assertEquals("anthropic", ProviderId.ANTHROPIC.value)
        assertEquals("openai", ProviderId.OPENAI.value)
        assertEquals("openrouter", ProviderId.OPENROUTER.value)
        assertEquals("on_device", ProviderId.ON_DEVICE.value)
        val all = setOf(ProviderId.ANTHROPIC, ProviderId.OPENAI, ProviderId.OPENROUTER, ProviderId.ON_DEVICE)
        assertEquals(FOUR, all.size)
        assertNotEquals(ProviderId.OPENAI, ProviderId.OPENROUTER)
    }

    @Test
    fun providerIdCompanionDeclaresExactlyFourPublicGetters() {
        // Kotlin mangles value-class getter names (getANTHROPIC-<hash>), so count by the "get" prefix.
        val getters = ProviderId.Companion::class.java.declaredMethods
            .filter { Modifier.isPublic(it.modifiers) && it.name.startsWith("get") }
        assertEquals(getters.map { it.name }.toString(), FOUR, getters.size)
    }

    private companion object {
        const val ROOT_PACKAGE = "io.github.ygaray.voiceactionengine.core"
        const val FOUR = 4
    }
}
