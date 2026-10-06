package io.github.ygaray.voiceactionengine.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** One main source file: its name and its lines. */
private class Source(val name: String, val lines: List<String>)

/**
 * The matchers behind [NoHardCodedConstantsTest]. The real scan and the positive controls call the same functions, so
 * a control proves the rule that actually runs. The token lists live only here: src/test is never scanned.
 */
private object ScanRules {
    /** Files that may declare a limit or default constant: the policy and capability owners. */
    val LIMIT_OWNERS: Set<String> =
        setOf("TierPolicy.kt", "ModelCapabilities.kt", "AwaitingConfirmGate.kt", "ToolSpec.kt")

    /** The only file that may carry the TierPolicy default values. */
    val TIER_POLICY_OWNER: Set<String> = setOf("TierPolicy.kt")

    private val modelFamilies = Regex(
        "(?i)\\b(claude|sonnet|opus|haiku|gpt|gemini|gemma|llama|mistral|deepseek|qwen|grok)(?![a-z])",
    )
    private val oSeries = Regex("\\bo[1-9]\\d{0,2}(?![A-Za-z0-9_])")
    private val vendorSlash = Regex(
        "\\b(anthropic|openai|google|meta-llama|mistralai|deepseek|qwen|x-ai|cohere|nvidia|" +
            "microsoft|amazon|perplexity)/[a-z0-9]",
    )

    private val constDeclaration = Regex("\\bconst\\s+val\\s+([A-Za-z0-9_]+)")
    private val limitName = Regex("^(DEFAULT_|MIN_|MAX_)|TOKEN|ITERATION|CEILING")

    private val tierPolicyLiterals = listOf(
        Regex("(?<![\\d_])60_?000(?![\\d_])"),
        Regex("(?<![\\d_])4_?096(?![\\d_])"),
    )

    private val settingsAccess = listOf(
        Regex("java\\.io\\.File"),
        Regex("java\\.nio\\.file"),
        Regex("\\bFile(Input|Output)Stream\\b"),
        Regex("\\bFile(Reader|Writer)\\b"),
        Regex("\\bSystem\\.getenv\\b"),
        Regex("\\bSystem\\.getProperty\\b"),
        Regex("java\\.util\\.prefs"),
        Regex("java\\.util\\.Properties\\b"),
        Regex("\\bSharedPreferences\\b"),
        Regex("\\bDataStore\\b"),
        Regex("^\\s*import\\s+android\\."),
        Regex("(?<![\\w.])android\\.[a-z]"),
    )

    private val onDevice = listOf(
        Regex("(?i)aicore"),
        Regex("(?i)mlkit"),
        Regex("(?i)(?<![A-Za-z0-9])nano(?![A-Za-z0-9])"),
        Regex("Nano(?![a-z])"),
        Regex("(?i)litert"),
        Regex("(?i)mediapipe"),
        Regex("(?i)tflite"),
        Regex("com\\.google\\.ai\\.edge"),
    )

    fun modelIdHit(line: String): Boolean =
        modelFamilies.containsMatchIn(line) || oSeries.containsMatchIn(line) || vendorSlash.containsMatchIn(line)

    /** The limit-shaped constant name declared on [line], or null. */
    fun limitConstant(line: String): String? =
        constDeclaration.find(line)?.groupValues?.get(1)?.takeIf { limitName.containsMatchIn(it) }

    fun tierPolicyLiteralHit(line: String): Boolean = tierPolicyLiterals.any { it.containsMatchIn(line) }

    fun settingsAccessHit(line: String): Boolean = settingsAccess.any { it.containsMatchIn(line) }

    /** Import and code lines only: comment and KDoc lines may explain that v1.0 ships no on-device code. */
    fun onDeviceHit(line: String): Boolean {
        val trimmed = line.trim()
        if (trimmed.startsWith("*") || trimmed.startsWith("/*") || trimmed.startsWith("//")) return false
        return onDevice.any { it.containsMatchIn(line) }
    }

    /** `file:line: text` for every line of every non-exempt [files] entry that [rule] flags. */
    fun scan(files: List<Source>, exempt: Set<String> = emptySet(), rule: (String) -> Boolean): List<String> =
        files.filter { it.name !in exempt }.flatMap { file ->
            file.lines.withIndex().filter { (_, line) -> rule(line) }
                .map { (index, line) -> "${file.name}:${index + 1}: ${line.trim()}" }
        }
}

/** Mechanical guards on the library sources: no model ids, no hidden limits, no settings reads, no on-device code. */
class NoHardCodedConstantsTest {

    private val sources: List<Source> by lazy {
        val root = File("src/main/kotlin")
        assertTrue("src/main/kotlin must exist; cwd is ${root.absolutePath}", root.isDirectory)
        root.walkTopDown().filter { it.isFile && it.extension == "kt" }
            .map { Source(it.name, it.readLines()) }
            .toList()
    }

    private fun assertNoHits(rule: String, hits: List<String>) {
        assertTrue("$rule:\n${hits.joinToString("\n")}", hits.isEmpty())
    }

    @Test
    fun scanIsNotVacuous() {
        assertTrue("scanned only ${sources.size} files", sources.size >= MIN_SCANNED_FILES)
        assertTrue("TierPolicy.kt must be scanned", sources.any { it.name == "TierPolicy.kt" })
    }

    @Test
    fun noModelIdAppearsInCodeKdocOrComments() {
        assertNoHits("model ids belong to the app", ScanRules.scan(sources, rule = ScanRules::modelIdHit))
    }

    @Test
    fun limitConstantsAreDeclaredOnlyByTheirOwners() {
        val hits = ScanRules.scan(sources, ScanRules.LIMIT_OWNERS) { ScanRules.limitConstant(it) != null }
        assertNoHits("limit or default constants belong to ${ScanRules.LIMIT_OWNERS}", hits)
    }

    @Test
    fun tierPolicyDefaultValuesAppearOnlyInTierPolicy() {
        val hits = ScanRules.scan(sources, ScanRules.TIER_POLICY_OWNER, ScanRules::tierPolicyLiteralHit)
        assertNoHits("TierPolicy defaults must not be repeated", hits)
    }

    @Test
    fun noFileEnvironmentPropertyOrPreferenceAccess() {
        val hits = ScanRules.scan(sources, rule = ScanRules::settingsAccessHit)
        assertNoHits("provider, model, key and policy arrive only through app seams", hits)
    }

    @Test
    fun noOnDeviceImplementationCode() {
        val hits = ScanRules.scan(sources, rule = ScanRules::onDeviceHit)
        assertNoHits(":core ships no on-device implementation (SC4)", hits)
    }

    @Test
    fun scanFlagsAViolatingSyntheticFileAndPassesACleanOne() {
        val dirty = Source("Dirty.kt", listOf("package x", "val a = \"claude-x\""))
        val clean = Source("Clean.kt", listOf("package x", "val a = 1"))

        val hits = ScanRules.scan(listOf(dirty, clean), rule = ScanRules::modelIdHit)
        assertEquals(listOf("Dirty.kt:2: val a = \"claude-x\""), hits)
        assertTrue(ScanRules.scan(listOf(dirty), setOf("Dirty.kt"), ScanRules::modelIdHit).isEmpty())
    }

    @Test
    fun modelIdMatcherFlagsIdsAndPassesProviderAndCompanyNames() {
        val flagged = listOf(
            "val m = \"claude-sonnet-5-5\"",
            "// prefers Opus for planning",
            " * the haiku tier",
            "val m = \"gpt-4o\"",
            "val m = \"o3-mini\"",
            "val m = \"gemini-2.5-pro\"",
            "val m = \"llama3\"",
            "val m = \"anthropic/some-id\"",
            "val m = \"meta-llama/some-id\"",
        )
        flagged.forEach { assertTrue("should flag: $it", ScanRules.modelIdHit(it)) }
        val clean = listOf(
            "public val ANTHROPIC: ProviderId = ProviderId(\"anthropic\")",
            "ProviderId(\"openai\") ProviderId(\"openrouter\") ProviderId(\"on_device\")",
            " * Usage as reported by Anthropic and OpenAI.",
            "val total = a + b",
            "val o = 1",
        )
        clean.forEach { assertFalse("should pass: $it", ScanRules.modelIdHit(it)) }
    }

    @Test
    fun limitConstantMatcherFlagsLimitNamesAndPassesOthers() {
        listOf(
            "private const val DEFAULT_RETRY = 3",
            "internal const val MIN_SIZE = 2",
            "const val MAX_DEPTH = 9",
            "private const val STREAM_TOKENS = 1",
            "private const val LOOP_ITERATIONS = 1",
            "private const val BUDGET_CEILING = 1",
        ).forEach { assertNotNull("should flag: $it", ScanRules.limitConstant(it)) }
        listOf(
            "private const val NOT_FOUND = -1",
            "private const val NANOS_PER_MILLI = 1_000_000L",
            "val DEFAULT_LOOKING = 1",
            "val x = 1",
        ).forEach { assertEquals("should pass: $it", null, ScanRules.limitConstant(it)) }
    }

    @Test
    fun tierPolicyLiteralMatcherFlagsTheDefaultsInEitherSpelling() {
        listOf("val a = 60_000L", "val a = 60000", "val b = 4_096", "val b = 4096").forEach {
            assertTrue("should flag: $it", ScanRules.tierPolicyLiteralHit(it))
        }
        listOf("val a = 120_000L", "val a = 160_000", "val b = 14096", "val c = 6").forEach {
            assertFalse("should pass: $it", ScanRules.tierPolicyLiteralHit(it))
        }
    }

    @Test
    fun settingsMatcherFlagsAccessAndPassesPlainCode() {
        listOf(
            "import java.io.File",
            "import java.nio.file.Files",
            "val s = FileInputStream(x)",
            "val r = FileReader(x)",
            "val k = System.getenv(\"KEY\")",
            "val k = System.getProperty(\"k\")",
            "import java.util.prefs.Preferences",
            "val p = java.util.Properties()",
            "val p: SharedPreferences",
            "val d: DataStore<Preferences>",
            "import android.content.Context",
            "val c = android.os.Build.VERSION.SDK_INT",
        ).forEach { assertTrue("should flag: $it", ScanRules.settingsAccessHit(it)) }
        listOf("val f = file.name", "val e = environment", "val a = androidx", "import kotlinx.coroutines.flow.Flow")
            .forEach { assertFalse("should pass: $it", ScanRules.settingsAccessHit(it)) }
    }

    @Test
    fun onDeviceMatcherFlagsImplementationCodeAndPassesTheMonotonicClock() {
        listOf(
            "import com.google.mlkit.genai.prompt.Generation",
            "class AiCoreProvider : AiProvider",
            "val p = GeminiNano()",
            "val p = OnDeviceNano(config)",
            "val m = ModelNano",
            "const val GEMINI_NANO = 1",
            "val nano = 1",
            "import com.google.android.gms.aicore.Foo",
            "import com.google.ai.edge.litertlm.Engine",
            "val e = LiteRtEngine()",
            "import com.google.mediapipe.tasks.genai.llminference.LlmInference",
            "val i = TfLiteInterpreter()",
            "val p = \"litertlm\"",
        ).forEach { assertTrue("should flag: $it", ScanRules.onDeviceHit(it)) }
        listOf(
            "// no AICore code in v1.0",
            " * ML Kit GenAI is not shipped",
            "// LiteRT is not shipped in :core",
            " * no MediaPipe here",
            "/* TFLite */",
            "/* Nano */",
            "val a = banano",
            "val n = nanoseconds",
        ).forEach { assertFalse("should pass: $it", ScanRules.onDeviceHit(it)) }

        val clock = File("src/main/kotlin")
            .walkTopDown()
            .filter { it.isFile && it.name == "PipelineBuilder.kt" }
            .flatMap { it.readLines().asSequence() }
            .firstOrNull { "nanoTime" in it }
        assertNotNull("PipelineBuilder.kt clock line must exist", clock)
        assertTrue(clock!!, "NANOS_PER_MILLI" in clock)
        assertFalse("the monotonic clock is not an on-device model: $clock", ScanRules.onDeviceHit(clock))
        assertFalse(ScanRules.onDeviceHit("private const val NANOS_PER_MILLI = 1_000_000L"))
    }

    private companion object {
        const val MIN_SCANNED_FILES = 40
    }
}
