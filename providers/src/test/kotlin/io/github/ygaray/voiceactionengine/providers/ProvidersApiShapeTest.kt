package io.github.ygaray.voiceactionengine.providers

import io.github.ygaray.voiceactionengine.providers.anthropic.AnthropicAttempt
import io.github.ygaray.voiceactionengine.providers.anthropic.AnthropicAttemptKind
import io.github.ygaray.voiceactionengine.providers.anthropic.AnthropicAttemptObserver
import io.github.ygaray.voiceactionengine.providers.anthropic.AnthropicProvider
import io.github.ygaray.voiceactionengine.providers.chat.ChatCompletionsAttempt
import io.github.ygaray.voiceactionengine.providers.chat.ChatCompletionsAttemptKind
import io.github.ygaray.voiceactionengine.providers.chat.ChatCompletionsAttemptObserver
import io.github.ygaray.voiceactionengine.providers.chat.ChatCompletionsProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.lang.reflect.Modifier
import java.util.zip.ZipFile

/** Public-shape lint over every compiled :providers main class; the rules mirror :core's ApiShapeTest. */
class ProvidersApiShapeTest {

    /** Binary class names under the root package; main may be on the test classpath as a directory or a jar. */
    private fun mainClassNames(): List<String> {
        val location = File(AnthropicProvider::class.java.protectionDomain.codeSource.location.toURI())
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
        val loader = AnthropicProvider::class.java.classLoader
        return mainClassNames().map { Class.forName(it, false, loader) }
    }

    private fun isDataShaped(cls: Class<*>): Boolean =
        cls.declaredMethods.any { it.name == "copy" || COMPONENT.matches(it.name) }

    private fun leakedStaticFields(cls: Class<*>): List<String> = cls.declaredFields
        .filter { Modifier.isPublic(it.modifiers) && Modifier.isStatic(it.modifiers) }
        .filter { it.name != "INSTANCE" && it.name != "Companion" }
        .map { "${cls.name}.${it.name}" }

    /**
     * True when [cls] is public and declares a default-argument constructor stub: a synthetic constructor ending in int
     * and DefaultConstructorMarker whose leading parameters equal those of a real constructor. Such a class cannot grow
     * a parameter without removing a constructor. A value class stub ends in the marker alone and is not flagged.
     */
    private fun hasPublicDefaultArgumentStub(cls: Class<*>): Boolean {
        if (!Modifier.isPublic(cls.modifiers)) return false
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

    // Synthetic classes that each break exactly one rule, to prove the rules can fire.
    private enum class SyntheticEnum { ONE }

    private data class SyntheticData(val first: Int)

    class SyntheticStaticField(val name: String) {
        companion object {
            @JvmField
            val LEAKED: Any = Any()
        }
    }

    class SyntheticDefaultArgument(val first: Int, val second: String = "x")

    @Test
    fun sweepIsNotVacuous() {
        val classes = allMainClasses()
        assertTrue("swept only ${classes.size} classes", classes.size >= MIN_INSPECTED)
        val names = classes.map { it.name }
        listOf(
            AnthropicProvider::class.java,
            AnthropicAttemptObserver::class.java,
            AnthropicAttempt::class.java,
            AnthropicAttemptKind::class.java,
            ChatCompletionsProvider::class.java,
            ChatCompletionsAttemptObserver::class.java,
            ChatCompletionsAttempt::class.java,
            ChatCompletionsAttemptKind::class.java,
        ).forEach { assertTrue("${it.name} must be among the swept classes", it.name in names) }
    }

    @Test
    fun noMainClassIsAnEnum() {
        val enums = allMainClasses().filter { it.isEnum }.map { it.name }
        assertTrue("enums freeze the set of constants into the public API: $enums", enums.isEmpty())
    }

    @Test
    fun noMainClassIsDataShaped() {
        val dataShaped = allMainClasses().filter { isDataShaped(it) }.map { it.name }
        assertTrue("copy/componentN classes freeze their constructor shape: $dataShaped", dataShaped.isEmpty())
    }

    @Test
    fun noMainClassLeaksAPublicStaticFieldBesidesInstanceAndCompanion() {
        val leaks = allMainClasses().flatMap { leakedStaticFields(it) }
        assertTrue("public static fields are frozen into the API: $leaks", leaks.isEmpty())
    }

    @Test
    fun noPublicMainClassDeclaresADefaultArgumentConstructorStub() {
        val flagged = allMainClasses().filter { hasPublicDefaultArgumentStub(it) }.map { it.name }
        assertTrue("default-argument stubs freeze the constructor shape: $flagged", flagged.isEmpty())
    }

    @Test
    fun everyRuleFiresOnItsSyntheticClass() {
        assertTrue(SyntheticEnum::class.java.isEnum)
        assertTrue(isDataShaped(SyntheticData::class.java))
        val leaked = leakedStaticFields(SyntheticStaticField::class.java)
        assertEquals(listOf("${SyntheticStaticField::class.java.name}.LEAKED"), leaked)
        assertTrue(hasPublicDefaultArgumentStub(SyntheticDefaultArgument::class.java))
    }

    @Test
    fun theRulesStayQuietOnTheShippedShapes() {
        assertTrue(!isDataShaped(AnthropicAttempt::class.java))
        assertTrue(leakedStaticFields(AnthropicProvider::class.java).isEmpty())
        assertTrue(!hasPublicDefaultArgumentStub(AnthropicProvider::class.java))
        assertTrue(!hasPublicDefaultArgumentStub(AnthropicAttemptKind::class.java))
        assertTrue(!AnthropicAttemptKind::class.java.isEnum)
        assertTrue(!isDataShaped(ChatCompletionsAttempt::class.java))
        assertTrue(leakedStaticFields(ChatCompletionsProvider::class.java).isEmpty())
        assertTrue(!hasPublicDefaultArgumentStub(ChatCompletionsProvider::class.java))
        assertTrue(!hasPublicDefaultArgumentStub(ChatCompletionsAttemptKind::class.java))
        assertTrue(!ChatCompletionsAttemptKind::class.java.isEnum)
    }

    private companion object {
        const val ROOT_PACKAGE = "io.github.ygaray.voiceactionengine.providers"
        const val DEFAULT_MARKER = "kotlin.jvm.internal.DefaultConstructorMarker"
        const val MIN_INSPECTED = 10
        val COMPONENT = Regex("component\\d+")
    }
}
