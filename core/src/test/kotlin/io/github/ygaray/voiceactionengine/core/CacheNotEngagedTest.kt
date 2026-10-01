package io.github.ygaray.voiceactionengine.core

import io.github.ygaray.voiceactionengine.core.pipeline.CommandOutcome
import io.github.ygaray.voiceactionengine.core.pipeline.CommandPipeline
import io.github.ygaray.voiceactionengine.core.pipeline.commandPipeline
import io.github.ygaray.voiceactionengine.core.provider.CachingMode
import io.github.ygaray.voiceactionengine.core.provider.ModelCapabilities
import io.github.ygaray.voiceactionengine.core.provider.ModelResult
import io.github.ygaray.voiceactionengine.core.provider.ProviderSelection
import io.github.ygaray.voiceactionengine.core.provider.estimatedPrefixTokens
import io.github.ygaray.voiceactionengine.core.provider.prefixChars
import io.github.ygaray.voiceactionengine.core.provider.shouldFlagCacheMiss
import io.github.ygaray.voiceactionengine.core.strategy.StrategyOutcome
import io.github.ygaray.voiceactionengine.core.strategy.ToolSpec
import io.github.ygaray.voiceactionengine.core.telemetry.PipelineEvent
import io.github.ygaray.voiceactionengine.core.telemetry.Usage
import io.github.ygaray.voiceactionengine.core.testing.FakeAiProvider
import io.github.ygaray.voiceactionengine.core.testing.FakeClock
import io.github.ygaray.voiceactionengine.core.testing.NoNetworkGuard
import io.github.ygaray.voiceactionengine.core.testing.RecordingCommitSink
import io.github.ygaray.voiceactionengine.core.testing.RecordingEventListener
import io.github.ygaray.voiceactionengine.core.testing.ScriptedCredentialSource
import io.github.ygaray.voiceactionengine.core.testing.ScriptedGate
import io.github.ygaray.voiceactionengine.core.testing.ScriptedSelectionSource
import io.github.ygaray.voiceactionengine.core.testing.ScriptedStrategy
import io.github.ygaray.voiceactionengine.core.testing.StrategyStep
import io.github.ygaray.voiceactionengine.core.transcript.CacheDirective
import io.github.ygaray.voiceactionengine.core.transcript.ModelRequest
import io.github.ygaray.voiceactionengine.core.transcript.ToolChoice
import io.github.ygaray.voiceactionengine.core.transcript.UserMessage
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The cache diagnostic: when a caching model should have used its prompt cache and did not. */
class CacheNotEngagedTest {
    private val clock = FakeClock()
    private val listener = RecordingEventListener()
    private val key = "sk-canary-key-1"
    private val tierId = StrategyId("tier-one")

    private val explicitCaps = ModelCapabilities {
        caching = CachingMode.EXPLICIT_BREAKPOINTS
        minCacheablePrefixTokens = MINIMUM
    }

    private fun textOf(count: Int): String = "x".repeat(count)

    /** A tier that sends [system] with [directive] once and completes with whatever came back. */
    private fun sendOnce(system: String, directive: CacheDirective = CacheDirective(true)): StrategyStep =
        { input, session ->
            val request = ModelRequest(
                system,
                listOf(UserMessage(input.transcript)),
                emptyList(),
                ToolChoice.Auto(),
                session.policy.maxTokensPerTurn,
                directive,
            )
            val result = session.model().complete(request)
            when (result) {
                is ModelResult.Failure -> StrategyOutcome.Failed(result.reason)
                else -> StrategyOutcome.Completed("ok")
            }
        }

    private fun pipelineOf(fake: FakeAiProvider, step: StrategyStep): CommandPipeline = commandPipeline {
        tier(ScriptedStrategy(tierId, step))
        provider(fake)
        providerSelection = ScriptedSelectionSource.fixed(ProviderSelection(ProviderId.ANTHROPIC, "model-a"))
        credentials = ScriptedCredentialSource.keys(ProviderId.ANTHROPIC to key)
        clock = this@CacheNotEngagedTest.clock
        listener = this@CacheNotEngagedTest.listener
        gate = ScriptedGate.admitAll()
        commitSink = RecordingCommitSink()
    }

    private fun anthropicFake(capabilities: ModelCapabilities, usage: Usage) =
        FakeAiProvider(ProviderId.ANTHROPIC, capabilities, { _ -> FakeAiProvider.reply("ok", usage) })

    @Test
    fun aLargeStaticPrefixThatTheCacheIgnoredRaisesExactlyOneEventAndNothingElse() = runTest {
        NoNetworkGuard.during {
            val fake = anthropicFake(explicitCaps, Usage(UNCACHED_PROMPT, 0, 0, 10))

            val outcome = pipelineOf(fake, sendOnce(textOf(LARGE_SYSTEM))).execute(CommandInput("hi"))

            assertTrue(outcome is CommandOutcome.Completed)
            val events = listener.events.filterIsInstance<PipelineEvent.CacheNotEngaged>()
            val event = events.single()
            assertEquals(tierId, event.strategy)
            assertEquals(ProviderId.ANTHROPIC, event.provider)
            assertEquals("model-a", event.model)
            assertEquals(0, listener.events.filterIsInstance<PipelineEvent.EngineCode>().size)
            assertEquals(emptyList<Any>(), outcome.trace.codes)
        }
    }

    @Test
    fun aPrefixBelowTheMinimumStaysSilent() = runTest {
        NoNetworkGuard.during {
            val fake = anthropicFake(explicitCaps, Usage(UNCACHED_PROMPT, 0, 0, 10))

            pipelineOf(fake, sendOnce(textOf(SMALL_SYSTEM))).execute(CommandInput("hi"))

            assertEquals(0, listener.events.filterIsInstance<PipelineEvent.CacheNotEngaged>().size)
        }
    }

    // ---- the firing rule, driven directly ----

    private fun capsOf(
        mode: CachingMode,
        minimum: Int? = MINIMUM,
        divisor: Double = DIVISOR,
    ): ModelCapabilities = ModelCapabilities {
        caching = mode
        minCacheablePrefixTokens = minimum
        charsPerToken = divisor
    }

    private fun promptOf(uncached: Long, read: Long = 0, write: Long = 0): Usage = Usage(uncached, read, write, 10)

    private fun fires(
        mode: CachingMode,
        usage: Usage,
        turn: Int = 1,
        chars: Long = HUGE_PREFIX,
        minimum: Int? = MINIMUM,
        divisor: Double = DIVISOR,
        staticPrefix: Boolean = true,
    ): Boolean = shouldFlagCacheMiss(CacheDirective(staticPrefix), capsOf(mode, minimum, divisor), usage, turn, chars)

    private fun requestWith(system: String, tools: List<ToolSpec> = emptyList()): ModelRequest =
        ModelRequest(system, listOf(UserMessage("hi")), tools, 100)

    private fun toolOf(index: Int): ToolSpec = ToolSpec(
        "tool_$index",
        "d".repeat(TOOL_DESCRIPTION),
        buildJsonObject {
            put("type", "object")
            putJsonObject("properties") {
                repeat(TOOL_FIELDS) { putJsonObject("field_$it") { put("type", "string") } }
            }
        },
    )

    private fun requestOfAbout(systemLength: Int, target: Long): ModelRequest {
        val tools = mutableListOf<ToolSpec>()
        while (prefixChars(requestWith(textOf(systemLength), tools)) < target) tools.add(toolOf(tools.size))
        return requestWith(textOf(systemLength), tools)
    }

    @Test
    fun explicitFiresAtTheMinimumAndNotOneTokenBelow() {
        assertTrue(fires(CachingMode.EXPLICIT_BREAKPOINTS, promptOf(MINIMUM.toLong())))
        assertFalse(fires(CachingMode.EXPLICIT_BREAKPOINTS, promptOf(MINIMUM - 1L)))
    }

    @Test
    fun explicitIsSilentWhenTheCacheWasReadOrWritten() {
        assertFalse(fires(CachingMode.EXPLICIT_BREAKPOINTS, promptOf(2000, read = 1)))
        assertFalse(fires(CachingMode.EXPLICIT_BREAKPOINTS, promptOf(2000, write = 1)))
        assertFalse(fires(CachingMode.EXPLICIT_BREAKPOINTS, promptOf(2000, write = 1), turn = 3))
    }

    @Test
    fun explicitPrefixDriftWithAWriteIsAKnownSilentCase() {
        // Known limitation: a prefix that drifts so the cache is rewritten every turn is not flagged here; the
        // Phase 10 Gate-1 run asserts cache_read > 0 separately.
        assertFalse(fires(CachingMode.EXPLICIT_BREAKPOINTS, promptOf(500, read = 0, write = 2000), turn = 2))
    }

    @Test
    fun anUnknownMinimumIsSilent() {
        assertFalse(fires(CachingMode.EXPLICIT_BREAKPOINTS, promptOf(5000), minimum = null))
    }

    @Test
    fun aModelThatDoesNotCacheIsSilent() {
        assertFalse(fires(CachingMode.NONE, promptOf(5000)))
    }

    @Test
    fun anUnknownCachingModeIsSilent() {
        assertFalse(fires(CachingMode("router_managed"), promptOf(5000), turn = 3))
    }

    @Test
    fun aRequestThatDidNotAskForTheStaticPrefixToBeCachedIsSilent() {
        assertFalse(fires(CachingMode.EXPLICIT_BREAKPOINTS, promptOf(5000), staticPrefix = false))
    }

    @Test
    fun automaticNeverFiresOnTheFirstTurn() {
        assertFalse(fires(CachingMode.AUTOMATIC, promptOf(5000), turn = 1))
    }

    @Test
    fun automaticFiresOnTheSecondTurnWithNoCacheRead() {
        assertTrue(fires(CachingMode.AUTOMATIC, promptOf(5000), turn = 2))
    }

    @Test
    fun automaticIsSilentOnTheSecondTurnWhenTheCacheWasRead() {
        assertFalse(fires(CachingMode.AUTOMATIC, promptOf(5000, read = 4000), turn = 2))
    }

    @Test
    fun automaticIgnoresWritesAndStillFiresWithNoRead() {
        assertTrue(fires(CachingMode.AUTOMATIC, promptOf(5000, write = 100), turn = 2))
    }

    @Test
    fun theCharacterPathFiresAtFourTimesTheMinimumAndNotOneCharacterBelow() {
        val ample = promptOf(AMPLE_PROMPT)
        assertTrue(fires(CachingMode.EXPLICIT_BREAKPOINTS, ample, chars = MINIMUM * DIVISOR_CHARS))
        assertFalse(fires(CachingMode.EXPLICIT_BREAKPOINTS, ample, chars = MINIMUM * DIVISOR_CHARS - 1))
    }

    @Test
    fun aSmallerDivisorFiresOnFewerCharacters() {
        val ample = promptOf(AMPLE_PROMPT)
        assertTrue(fires(CachingMode.EXPLICIT_BREAKPOINTS, ample, chars = 3L * MINIMUM, divisor = 3.0))
        assertFalse(fires(CachingMode.EXPLICIT_BREAKPOINTS, ample, chars = 3L * MINIMUM - 1, divisor = 3.0))
    }

    @Test
    fun thePromptTotalCapFiresAtTheMinimumAndNotOneTokenBelow() {
        assertTrue(fires(CachingMode.AUTOMATIC, promptOf(MINIMUM.toLong()), turn = 2, chars = HUGE_PREFIX))
        assertFalse(fires(CachingMode.AUTOMATIC, promptOf(MINIMUM - 1L), turn = 2, chars = HUGE_PREFIX))
        assertEquals(MINIMUM - 1L, estimatedPrefixTokens(HUGE_PREFIX, DIVISOR, promptOf(MINIMUM - 1L)))
    }

    @Test
    fun theCapCountsAllThreePromptBuckets() {
        val usage = promptOf(uncached = 100, read = 200, write = 300)
        assertEquals(600L, estimatedPrefixTokens(HUGE_PREFIX, DIVISOR, usage))
    }

    @Test
    fun anEmptyPrefixEstimatesZeroAndIsSilent() {
        val empty = requestWith("")
        assertEquals(0L, prefixChars(empty))
        assertEquals(0L, estimatedPrefixTokens(prefixChars(empty), DIVISOR, promptOf(5000)))
        assertFalse(fires(CachingMode.EXPLICIT_BREAKPOINTS, promptOf(5000), chars = prefixChars(empty)))
    }

    @Test
    fun zeroUsageIsSilent() {
        assertFalse(fires(CachingMode.EXPLICIT_BREAKPOINTS, Usage.ZERO))
        assertFalse(fires(CachingMode.AUTOMATIC, Usage.ZERO, turn = 2))
    }

    @Test
    fun prefixCharsCountsTheSystemPromptAndEachToolNameDescriptionAndSchema() {
        val tool = toolOf(1)
        val expected = 10L + tool.name.length + tool.description.length + tool.inputSchema.toString().length
        assertEquals(expected, prefixChars(requestWith(textOf(10), listOf(tool))))
        assertEquals(expected + expected - 10L, prefixChars(requestWith(textOf(10), listOf(tool, toolOf(2)))))
    }

    @Test
    fun prefixCharsCountsUtf16CodeUnitsSoASupplementaryCharacterCountsTwo() {
        val surrogatePair = "\uD83D\uDE00"
        assertEquals(2L * SUPPLEMENTARY_COUNT, prefixChars(requestWith(surrogatePair.repeat(SUPPLEMENTARY_COUNT))))
    }

    @Test
    fun maximumUsageInEveryBucketDoesNotOverflow() {
        val maximum = Usage(Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE)
        assertEquals(MINIMUM.toLong(), estimatedPrefixTokens(MINIMUM * DIVISOR_CHARS, DIVISOR, maximum))
        assertTrue(fires(CachingMode.AUTOMATIC, Usage(Long.MAX_VALUE, 0, Long.MAX_VALUE, 0), turn = 2))
    }

    @Test
    fun theEstimateTruncatesInsteadOfRounding() {
        assertEquals(1023L, estimatedPrefixTokens(4095, DIVISOR, promptOf(AMPLE_PROMPT)))
        assertEquals(1024L, estimatedPrefixTokens(4099, DIVISOR, promptOf(AMPLE_PROMPT)))
    }

    @Test
    fun anAgenticSizedPrefixFiresAgainstAHighMinimumAndASmallOneStaysSilent() {
        val caps = capsOf(CachingMode.EXPLICIT_BREAKPOINTS, minimum = HIGH_MINIMUM)
        val agentic = requestOfAbout(AGENTIC_SYSTEM, AGENTIC_PREFIX)
        val chars = prefixChars(agentic)
        assertTrue("prefix was $chars", chars in AGENTIC_PREFIX..AGENTIC_PREFIX + AGENTIC_SLACK)
        assertTrue(shouldFlagCacheMiss(CacheDirective(true), caps, promptOf(AGENTIC_PROMPT), 1, chars))

        val small = requestOfAbout(AGENTIC_SYSTEM, SMALL_PREFIX)
        assertFalse(shouldFlagCacheMiss(CacheDirective(true), caps, promptOf(AGENTIC_PROMPT), 1, prefixChars(small)))
    }

    private companion object {
        const val MINIMUM = 1024
        const val DIVISOR = 4.0
        const val DIVISOR_CHARS = 4L
        const val HUGE_PREFIX = 100_000_000L
        const val AMPLE_PROMPT = 1_000_000L
        const val TOOL_DESCRIPTION = 90
        const val TOOL_FIELDS = 6
        const val SUPPLEMENTARY_COUNT = 7
        const val HIGH_MINIMUM = 4096
        const val AGENTIC_SYSTEM = 1600
        const val AGENTIC_PREFIX = 21_000L
        const val AGENTIC_SLACK = 400L
        const val AGENTIC_PROMPT = 7016L
        const val SMALL_PREFIX = 6000L
        const val LARGE_SYSTEM = 8000
        const val SMALL_SYSTEM = 2000
        const val UNCACHED_PROMPT = 2100L
    }
}
