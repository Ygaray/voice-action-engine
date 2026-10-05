package io.github.ygaray.voiceactionengine.providers.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenAiModelRulesTest {

    private fun assertRules(id: String, viaRouter: Boolean, effort: String?, tokenParam: String, minTokens: Int) {
        val rules = OpenAiModelRules.wireRules(id, viaRouter)
        assertEquals("$id effort", effort, rules.reasoningEffortWithTools)
        assertEquals("$id token parameter", tokenParam, rules.tokenParam)
        assertEquals("$id minimum tokens", minTokens, rules.minTokens)
    }

    @Test
    fun laterGenerationsTakeReasoningEffortNoneWithTools() {
        val ids = listOf(
            "gpt-5.4-mini", "gpt-5.6-sol", "gpt-5.10", "gpt-6-sol", "gpt-6-luna", "gpt-6", "gpt-6-sol-2026-08-01",
        )
        for (id in ids) assertRules(id, viaRouter = false, "none", "max_completion_tokens", 1)
    }

    @Test
    fun earlierReasoningModelsTakeNoReasoningEffort() {
        val ids = listOf("gpt-5", "gpt-5-mini", "gpt-5-nano", "gpt-5.1", "gpt-5.3-codex", "o3", "o4-mini")
        for (id in ids) assertRules(id, viaRouter = false, null, "max_completion_tokens", 1)
    }

    @Test
    fun theGptFourAndThreeFamiliesKeepMaxTokensAndNoReasoningEffort() {
        for (id in listOf("gpt-4o-mini", "gpt-4.1", "gpt-3.5-turbo")) {
            assertRules(id, viaRouter = false, null, "max_tokens", 1)
        }
    }

    @Test
    fun anyOtherOpenAiIdGetsMaxCompletionTokensAndNoReasoningEffort() {
        for (id in listOf("davinci-002", "text-model-x")) {
            assertRules(id, viaRouter = false, null, "max_completion_tokens", 1)
        }
    }

    @Test
    fun theResponsesOnlyPairTakesEffortLowOnlyThroughARouter() {
        for (id in listOf("gpt-6-astra", "gpt-6.1-sol", "gpt-6-astra-2026-09-01")) {
            assertRules(id, viaRouter = true, "low", "max_completion_tokens", 1)
            assertFalse(id, OpenAiModelRules.toolsOnChat(id, viaRouter = false))
            assertTrue(id, OpenAiModelRules.toolsOnChat(id, viaRouter = true))
        }
    }

    @Test
    fun everyDirectIdThatRejectsEffortNoneSendsNoEffort() {
        val ids = listOf(
            "gpt-6-astra", "gpt-6-astra-2026-09-01", "gpt-6.1-sol", "gpt-5.5-pro", "gpt-5.4-pro", "gpt-5.2-pro",
            "gpt-5-pro", "gpt-6-pro", "gpt-5.3-codex", "gpt-5.2-codex",
        )
        for (id in ids) assertRules(id, viaRouter = false, null, "max_completion_tokens", 1)
    }

    @Test
    fun everyIdThatWorkedBeforeKeepsEffortNone() {
        val ids = listOf("gpt-6", "gpt-6-sol", "gpt-6-luna", "gpt-5.4", "gpt-5.5", "gpt-5.4-mini", "gpt-6-astral")
        for (id in ids) assertRules(id, viaRouter = false, "none", "max_completion_tokens", 1)
    }

    @Test
    fun aBlankOrUnknownIdKeepsTheDefaultRules() {
        for (id in listOf("", " ", "example-model", "openai/gpt-6-astra")) {
            assertRules(id, viaRouter = false, null, "max_completion_tokens", 1)
        }
    }

    @Test
    fun aRoutedProIdKeepsTodaysRules() {
        assertRules("gpt-5.5-pro", viaRouter = true, "none", "max_completion_tokens", 1)
        assertRules("gpt-6.1-sol", viaRouter = true, "low", "max_completion_tokens", 1)
    }

    @Test
    fun directProAndCodexIdsAreRefusedForToolsBeforeAnyCall() {
        for (id in listOf("gpt-5.5-pro", "gpt-5.3-codex", "gpt-6-astra", "gpt-6.1-sol")) {
            assertFalse(id, OpenAiModelRules.toolsOnChat(id, viaRouter = false))
            assertTrue("$id routed", OpenAiModelRules.toolsOnChat(id, viaRouter = true))
        }
        for (id in listOf("gpt-5.5", "gpt-6-sol", "gpt-5.5-professor")) {
            assertTrue(id, OpenAiModelRules.toolsOnChat(id, viaRouter = false))
        }
    }

    @Test
    fun theRouterRaisesTheFloorOnlyForTheLegacyTokenParameter() {
        assertRules("gpt-4o-mini", viaRouter = true, null, "max_tokens", 16)
        assertRules("gpt-3.5-turbo", viaRouter = true, null, "max_tokens", 16)
        assertRules("gpt-5.4-mini", viaRouter = true, "none", "max_completion_tokens", 1)
        assertRules("o3", viaRouter = true, null, "max_completion_tokens", 1)
    }

    @Test
    fun theFamilyMatchIsAnchored() {
        // gpt-50 is not gpt-5.N, so it falls to the generic row rather than the later-generation one.
        assertRules("gpt-50", viaRouter = false, null, "max_completion_tokens", 1)
        assertRules("gpt-5.4x", viaRouter = false, null, "max_completion_tokens", 1)
        assertTrue(OpenAiModelRules.toolsOnChat("xgpt-6-astra", viaRouter = false))
        assertTrue(OpenAiModelRules.toolsOnChat("gpt-6-astral", viaRouter = false))
        assertTrue(OpenAiModelRules.toolsOnChat("gpt-6.1-solar", viaRouter = false))
        assertRules("xgpt-6-astra", viaRouter = true, null, "max_completion_tokens", 1)
        assertRules("gpt-6-astral", viaRouter = true, "none", "max_completion_tokens", 1)
    }

    @Test
    fun cacheMinimumIsOneThousandTwentyFourForGptAndOSeriesOnly() {
        for (id in listOf("gpt-5.4-mini", "gpt-4o-mini", "o3", "o4-mini", "gpt-6-astra")) {
            assertEquals(id, 1_024, OpenAiModelRules.minCacheablePrefixTokens(id))
        }
        for (id in listOf("davinci-002", "omni", "text-model-x")) {
            assertNull(id, OpenAiModelRules.minCacheablePrefixTokens(id))
        }
    }

    @Test
    fun theOSeriesRejectsTheParallelSwitchAndEveryOtherFamilyTakesIt() {
        for (id in listOf("o1", "o3", "o3-mini", "o4-mini")) {
            assertFalse(id, OpenAiModelRules.wireRules(id, viaRouter = false).acceptsParallelToolCalls)
            assertFalse("$id routed", OpenAiModelRules.wireRules(id, viaRouter = true).acceptsParallelToolCalls)
        }
        for (id in listOf("gpt-5.4-mini", "gpt-4o-mini", "gpt-5", "gpt-6", "example-unknown-model")) {
            assertTrue(id, OpenAiModelRules.wireRules(id, viaRouter = false).acceptsParallelToolCalls)
            assertTrue("$id routed", OpenAiModelRules.wireRules(id, viaRouter = true).acceptsParallelToolCalls)
        }
        assertTrue(OpenAiModelRules.routedDefaultRules().acceptsParallelToolCalls)
    }

    @Test
    fun theWireRulesPrintTheirFourFields() {
        assertEquals(
            "ChatWireRules(reasoningEffortWithTools=none, tokenParam=max_completion_tokens, minTokens=1, " +
                "acceptsParallelToolCalls=true)",
            OpenAiModelRules.wireRules("gpt-6", viaRouter = false).toString(),
        )
    }
}
