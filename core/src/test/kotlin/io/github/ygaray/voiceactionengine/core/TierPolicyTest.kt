package io.github.ygaray.voiceactionengine.core

import io.github.ygaray.voiceactionengine.core.pipeline.TierPolicy
import io.github.ygaray.voiceactionengine.core.pipeline.TierPolicySource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TierPolicyTest {

    @Test
    fun defaultPolicyHasTheDocumentedLimits() {
        val policy = TierPolicy.DEFAULT
        assertFalse(policy.offlineOnly)
        assertNull(policy.maxTier)
        assertNull(policy.allowedProviders)
        assertEquals(SIX, policy.maxIterations)
        assertEquals(SIXTY_THOUSAND, policy.tokenCeiling)
        assertEquals(FOUR_K, policy.maxTokensPerTurn)
        assertNull(policy.commandTimeoutMillis)
    }

    @Test
    fun builderOverridesOnlyWhatIsSet() {
        val policy = TierPolicy {
            maxIterations = EIGHT
            maxTier = StrategyId("cloud")
            allowedProviders = setOf(ProviderId.ANTHROPIC)
        }
        assertEquals(EIGHT, policy.maxIterations)
        assertEquals(StrategyId("cloud"), policy.maxTier)
        assertEquals(setOf(ProviderId.ANTHROPIC), policy.allowedProviders)
        assertFalse(policy.offlineOnly)
        assertEquals(SIXTY_THOUSAND, policy.tokenCeiling)
        assertEquals(FOUR_K, policy.maxTokensPerTurn)
        assertNull(policy.commandTimeoutMillis)
    }

    @Test
    fun emptyBlockEqualsDefaults() {
        val policy = TierPolicy { }
        assertEquals(TierPolicy.DEFAULT.toString(), policy.toString())
    }

    @Test
    fun fewerThanTwoIterationsIsRejected() {
        val failure = assertThrows(IllegalArgumentException::class.java) { TierPolicy { maxIterations = 1 } }
        assertTrue(failure.message.orEmpty(), failure.message.orEmpty().contains("maxIterations"))
    }

    @Test
    fun nonPositiveLimitsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { TierPolicy { tokenCeiling = 0 } }
        assertThrows(IllegalArgumentException::class.java) { TierPolicy { maxTokensPerTurn = 0 } }
        assertThrows(IllegalArgumentException::class.java) { TierPolicy { commandTimeoutMillis = 0 } }
        assertEquals(1L, TierPolicy { commandTimeoutMillis = 1 }.commandTimeoutMillis)
    }

    @Test
    fun allowedProvidersIsDefensivelyCopied() {
        val mutable = mutableSetOf(ProviderId.OPENAI)
        val policy = TierPolicy { allowedProviders = mutable }
        mutable.add(ProviderId.ANTHROPIC)
        assertEquals(setOf(ProviderId.OPENAI), policy.allowedProviders)
    }

    @Test
    fun fixedSourceReturnsTheSameInstanceEveryCall() = runTest {
        val policy = TierPolicy { maxIterations = 3 }
        val source = TierPolicySource.fixed(policy)
        assertSame(policy, source.current())
        assertSame(policy, source.current())
    }

    @Test
    fun toStringNamesEveryField() {
        val text = TierPolicy.DEFAULT.toString()
        listOf(
            "offlineOnly", "maxTier", "allowedProviders", "maxIterations", "tokenCeiling", "maxTokensPerTurn",
            "commandTimeoutMillis",
        ).forEach { assertTrue("$it missing from $text", text.contains(it)) }
    }

    private companion object {
        const val SIX = 6
        const val EIGHT = 8
        const val FOUR_K = 4_096
        const val SIXTY_THOUSAND = 60_000L
    }
}
