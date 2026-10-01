package io.github.ygaray.voiceactionengine.core

import io.github.ygaray.voiceactionengine.core.provider.CachingMode
import io.github.ygaray.voiceactionengine.core.provider.ModelCapabilities
import io.github.ygaray.voiceactionengine.core.provider.ModelCapabilityTable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelCapabilityTableTest {

    private val knownDefault = ModelCapabilities {
        caching = CachingMode.EXPLICIT_BREAKPOINTS
        minCacheablePrefixTokens = 1024
    }

    private val providerDefaults: (ProviderId, String) -> ModelCapabilities = { _, model ->
        if (model == "model-known") knownDefault else ModelCapabilities.UNKNOWN
    }

    private fun table(vararg overrides: Pair<Pair<ProviderId, String>, ModelCapabilities.Builder.() -> Unit>) =
        ModelCapabilityTable(providerDefaults, overrides.toMap())

    @Test
    fun appOverrideForTheExactPairPatchesTheProviderDefault() {
        val table = table((ProviderId.ANTHROPIC to "model-known") to { supportsTools = false })

        val patched = table.lookup(ProviderId.ANTHROPIC, "model-known")

        assertFalse(patched.supportsTools)
        assertEquals(CachingMode.EXPLICIT_BREAKPOINTS, patched.caching)
        assertEquals(1024, patched.minCacheablePrefixTokens)
    }

    @Test
    fun aForcedToolOverridePatchesOnlyThatFieldOfTheProviderDefault() {
        val table = table((ProviderId.ANTHROPIC to "model-known") to { supportsForcedToolChoice = false })

        val patched = table.lookup(ProviderId.ANTHROPIC, "model-known")

        assertFalse(patched.supportsForcedToolChoice)
        assertTrue(patched.supportsTools)
        assertEquals(CachingMode.EXPLICIT_BREAKPOINTS, patched.caching)
        assertEquals(1024, patched.minCacheablePrefixTokens)
        assertEquals(4.0, patched.charsPerToken, 0.0)
        assertTrue(table.lookup(ProviderId.ANTHROPIC, "model-other").supportsForcedToolChoice)
    }

    @Test
    fun capabilitiesDifferingOnlyInTheForcedToolFieldAreUnequalAndShowItInToString() {
        val allowed = ModelCapabilities { supportsForcedToolChoice = true }
        val rejected = ModelCapabilities { supportsForcedToolChoice = false }

        assertNotEquals(allowed, rejected)
        assertTrue(rejected.toString(), rejected.toString().contains("supportsForcedToolChoice=false"))
        assertTrue(allowed.toString(), allowed.toString().contains("supportsForcedToolChoice=true"))
    }

    @Test
    fun anIdWithNoOverrideOrDefaultFallsBackToUnknown() {
        val table = table((ProviderId.ANTHROPIC to "model-known") to { supportsTools = false })

        assertEquals(ModelCapabilities.UNKNOWN, table.lookup(ProviderId.ANTHROPIC, "model-other"))
    }

    @Test
    fun anOverrideDoesNotReachTheSameIdUnderAnotherProvider() {
        val table = table((ProviderId.ANTHROPIC to "model-known") to { supportsTools = false })

        assertTrue(table.lookup(ProviderId.OPENAI, "model-known").supportsTools)
    }

    @Test
    fun unknownAndEmptyBuilderShareTheDocumentedDefaults() {
        val built = ModelCapabilities { }

        for (caps in listOf(ModelCapabilities.UNKNOWN, built)) {
            assertTrue(caps.supportsTools)
            assertTrue(caps.supportsForcedToolChoice)
            assertEquals(CachingMode.NONE, caps.caching)
            assertNull(caps.minCacheablePrefixTokens)
            assertEquals(4.0, caps.charsPerToken, 0.0)
        }
        assertEquals(ModelCapabilities.UNKNOWN, built)
        assertEquals(ModelCapabilities.UNKNOWN.hashCode(), built.hashCode())
        assertNotEquals(ModelCapabilities.UNKNOWN, ModelCapabilities { supportsTools = false })
        assertNotEquals(ModelCapabilities.UNKNOWN, ModelCapabilities { supportsForcedToolChoice = false })
    }

    @Test
    fun invalidDivisorsAreRejectedNamingTheField() {
        val bad = listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY)
        for (value in bad) {
            val e = assertThrows(IllegalArgumentException::class.java) { ModelCapabilities { charsPerToken = value } }
            assertTrue(e.message, e.message!!.contains("charsPerToken"))
        }
    }

    @Test
    fun aNonPositiveMinimumPrefixIsRejectedNamingTheField() {
        for (value in listOf(0, -1)) {
            val e = assertThrows(IllegalArgumentException::class.java) {
                ModelCapabilities { minCacheablePrefixTokens = value }
            }
            assertTrue(e.message, e.message!!.contains("minCacheablePrefixTokens"))
        }
    }

    @Test
    fun overrideKeysAreExactIdsNeverPrefixes() {
        val table = table((ProviderId.ANTHROPIC to "model-a") to { supportsTools = false })

        assertFalse(table.lookup(ProviderId.ANTHROPIC, "model-a").supportsTools)
        assertTrue(table.lookup(ProviderId.ANTHROPIC, "model-a-mini").supportsTools)
        assertTrue(table.lookup(ProviderId.ANTHROPIC, "model").supportsTools)
        assertTrue(table.lookup(ProviderId.OPENROUTER, "model-a").supportsTools)
    }

    @Test
    fun aBlankModelIsRejected() {
        val table = table()

        assertThrows(IllegalArgumentException::class.java) { table.lookup(ProviderId.ANTHROPIC, "") }
        assertThrows(IllegalArgumentException::class.java) { table.lookup(ProviderId.ANTHROPIC, "  ") }
    }

    @Test
    fun cachingModesHaveTheWireValuesAndAreDistinct() {
        assertEquals("explicit_breakpoints", CachingMode.EXPLICIT_BREAKPOINTS.value)
        assertEquals("automatic", CachingMode.AUTOMATIC.value)
        assertEquals("none", CachingMode.NONE.value)
        assertEquals(3, setOf(CachingMode.EXPLICIT_BREAKPOINTS, CachingMode.AUTOMATIC, CachingMode.NONE).size)
        assertEquals("automatic", CachingMode.AUTOMATIC.toString())
    }

    @Test
    fun toStringNamesAllFieldsAndTheTableCountsOverridesOnly() {
        val text = knownDefault.toString()
        for (field in listOf(
            "supportsTools",
            "supportsForcedToolChoice",
            "caching",
            "minCacheablePrefixTokens",
            "charsPerToken",
        )) {
            assertTrue("$field in $text", text.contains(field))
        }
        val table = table((ProviderId.ANTHROPIC to "model-known") to { supportsTools = false })
        assertEquals("ModelCapabilityTable(overrides=1)", table.toString())
    }
}
