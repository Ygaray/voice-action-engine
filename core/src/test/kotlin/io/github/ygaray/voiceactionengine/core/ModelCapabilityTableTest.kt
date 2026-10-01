package io.github.ygaray.voiceactionengine.core

import io.github.ygaray.voiceactionengine.core.provider.CachingMode
import io.github.ygaray.voiceactionengine.core.provider.ModelCapabilities
import io.github.ygaray.voiceactionengine.core.provider.ModelCapabilityTable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun anIdWithNoOverrideOrDefaultFallsBackToUnknown() {
        val table = table((ProviderId.ANTHROPIC to "model-known") to { supportsTools = false })

        assertEquals(ModelCapabilities.UNKNOWN, table.lookup(ProviderId.ANTHROPIC, "model-other"))
    }

    @Test
    fun anOverrideDoesNotReachTheSameIdUnderAnotherProvider() {
        val table = table((ProviderId.ANTHROPIC to "model-known") to { supportsTools = false })

        assertTrue(table.lookup(ProviderId.OPENAI, "model-known").supportsTools)
    }
}
