package io.github.ygaray.voiceactionengine.core

import io.github.ygaray.voiceactionengine.core.provider.ProviderRequest
import io.github.ygaray.voiceactionengine.core.strategy.ToolSpec
import io.github.ygaray.voiceactionengine.core.transcript.CacheDirective
import io.github.ygaray.voiceactionengine.core.transcript.ModelRequest
import io.github.ygaray.voiceactionengine.core.transcript.ReasoningMode
import io.github.ygaray.voiceactionengine.core.transcript.ToolChoice
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
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

    @Test
    fun toolSpecDeclaresExactlyOnePublicConstructor() {
        val cls = ToolSpec::class.java
        val string = String::class.java
        val schema = JsonObject::class.java
        val bool = Boolean::class.javaPrimitiveType
        val boxed = java.lang.Boolean::class.java
        val publicConstructors = cls.declaredConstructors.filter { !it.isSynthetic && Modifier.isPublic(it.modifiers) }

        assertEquals(1, publicConstructors.size)
        val parameterTypes = publicConstructors.single().parameterTypes.toList()
        assertEquals(listOf(string, string, schema, bool, bool, boxed), parameterTypes)
        assertThrows(NoSuchMethodException::class.java) { cls.getConstructor(string, string, schema) }
        assertThrows(NoSuchMethodException::class.java) { cls.getConstructor(string, string, schema, bool) }
        assertThrows(NoSuchMethodException::class.java) { cls.getConstructor(string, string, schema, bool, bool) }
        assertTrue(hasDefaultArgumentStub(cls))
    }

    @Test
    fun modelRequestKeepsEveryV10ConstructorAsARealPublicConstructor() {
        val string = String::class.java
        val list = List::class.java
        val choice = ToolChoice::class.java
        val int = Int::class.javaPrimitiveType
        val cache = CacheDirective::class.java
        val bool = Boolean::class.javaPrimitiveType
        val real = ModelRequest::class.java.declaredConstructors
            .filter { !it.isSynthetic && Modifier.isPublic(it.modifiers) }
            .map { it.parameterTypes.toList() }

        assertTrue(real.toString(), listOf(string, list, list, choice, int, cache, bool) in real)
        assertTrue(real.toString(), listOf(string, list, list, choice, int, cache) in real)
        assertTrue(real.toString(), listOf(string, list, int) in real)
        assertTrue(real.toString(), listOf(string, list, list, int) in real)
        assertTrue(!hasDefaultArgumentStub(ModelRequest::class.java))
        assertTrue(!hasDefaultArgumentStub(ReasoningMode::class.java))
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

    @Test
    fun noClassOutsideTheDocumentedExceptionsDeclaresADefaultArgumentConstructorStub() {
        val inspected = allMainClasses()
        assertTrue("inspected only ${inspected.size} classes", inspected.size >= MIN_INSPECTED)
        val names = inspected.map { it.name }
        assertTrue(ModelRequest::class.java.name in names)
        assertTrue(ProviderRequest::class.java.name in names)
        assertTrue(ToolSpec::class.java.name in names)
        val flagged = inspected.filter { hasDefaultArgumentStub(it) }.map { it.name }
        val unexpected = flagged - STUB_EXCEPTIONS
        assertTrue("default-argument stubs freeze the constructor shape: $unexpected", unexpected.isEmpty())
    }

    /** Keeps [STUB_EXCEPTIONS] honest: an entry that no longer has a stub must be dropped from the list. */
    @Test
    fun everyDocumentedStubExceptionStillDeclaresAStub() {
        val loader = javaClass.classLoader
        val stale = STUB_EXCEPTIONS.filterNot { hasDefaultArgumentStub(Class.forName(it, false, loader)) }
        assertTrue("stale stub exceptions: $stale", stale.isEmpty())
    }

    @Test
    fun theGrowthRulePredicateFlagsADefaultArgumentClass() {
        assertTrue(hasDefaultArgumentStub(WithDefaultArgument::class.java))
        assertTrue(!hasDefaultArgumentStub(ProviderId::class.java))
    }

    private companion object {
        const val DEFAULT_MARKER = "kotlin.jvm.internal.DefaultConstructorMarker"
        const val MIN_INSPECTED = 10

        /**
         * Classes that keep a Kotlin default-argument constructor stub on purpose.
         *
         * Public constructors: [ToolSpec] and [CommandInput] were shipped with defaults by Phase 2, and call sites
         * that name a single optional argument (`terminal = true`, `parentRunId = ...`) only compile against them, so
         * replacing the defaults with explicit overloads would be a source break. Their constructor shapes are
         * therefore frozen: new optional attributes must arrive as separate members (a `with...` function or a new
         * factory), never as a further constructor parameter.
         *
         * The rest have an internal constructor or are internal classes, so no consumer binds to the stub.
         */
        val STUB_EXCEPTIONS = setOf(
            "$ROOT_PACKAGE.CommandInput",
            "$ROOT_PACKAGE.strategy.ToolSpec",
            "$ROOT_PACKAGE.commit.ActionDetails",
            "$ROOT_PACKAGE.commit.ExecutedAction",
            "$ROOT_PACKAGE.telemetry.CommandTrace",
            "$ROOT_PACKAGE.telemetry.RunRecorder",
            "$ROOT_PACKAGE.telemetry.TierAttempt",
        )
        const val ROOT_PACKAGE = "io.github.ygaray.voiceactionengine.core"
        const val FOUR = 4
    }
}
