package io.github.ygaray.voiceactionengine.core

import io.github.ygaray.voiceactionengine.core.provider.CredentialLookup
import io.github.ygaray.voiceactionengine.core.provider.CredentialSource
import io.github.ygaray.voiceactionengine.core.provider.OnDeviceAvailability
import io.github.ygaray.voiceactionengine.core.provider.OnDeviceCapability
import io.github.ygaray.voiceactionengine.core.provider.ProviderSelection
import io.github.ygaray.voiceactionengine.core.provider.ProviderSelectionSource
import io.github.ygaray.voiceactionengine.core.provider.SelectionRequest
import io.github.ygaray.voiceactionengine.core.testing.ScriptedCredentialSource
import io.github.ygaray.voiceactionengine.core.testing.ScriptedSelectionSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SeamTypesTest {

    private val key = "sk-CANARY-SEAM"

    @Test
    fun anAppLambdaAnswersPresentForItsProviderAndMissingForOthers() = runTest {
        val source = CredentialSource { provider ->
            if (provider == ProviderId.ANTHROPIC) {
                CredentialLookup.Present(Credential(provider, key))
            } else {
                CredentialLookup.Missing()
            }
        }

        val present = source.credential(ProviderId.ANTHROPIC)
        val missing = source.credential(ProviderId.OPENAI)

        assertTrue(present is CredentialLookup.Present)
        assertEquals(ProviderId.ANTHROPIC, (present as CredentialLookup.Present).credential.provider)
        assertEquals(key, present.credential.apiKey)
        assertEquals(CredentialLookup.Missing(), missing)
    }

    @Test
    fun theScriptedCredentialSourceAnswersPerProviderAndRecordsEveryAskInOrder() = runTest {
        val source = ScriptedCredentialSource.keys(ProviderId.ANTHROPIC to key)

        val first = source.credential(ProviderId.ANTHROPIC)
        val second = source.credential(ProviderId.OPENROUTER)

        assertTrue(first is CredentialLookup.Present)
        assertSame(CredentialLookup.Missing::class.java, second.javaClass)
        assertEquals(listOf(ProviderId.ANTHROPIC, ProviderId.OPENROUTER), source.requested)
    }

    @Test
    fun presentPrintsTheProviderOnlyAndNeverTheKey() {
        val present = CredentialLookup.Present(Credential(ProviderId.ANTHROPIC, key))

        assertEquals("CredentialLookup.Present(provider=anthropic)", present.toString())
        assertFalse(present.toString().contains(key))
    }

    @Test
    fun missingEqualsMissingAndUnreadableComparesByCause() {
        assertEquals(CredentialLookup.Missing(), CredentialLookup.Missing())
        assertEquals(CredentialLookup.Unreadable("a"), CredentialLookup.Unreadable("a"))
        assertEquals(CredentialLookup.Unreadable("a").hashCode(), CredentialLookup.Unreadable("a").hashCode())
        assertNotEquals(CredentialLookup.Unreadable("a"), CredentialLookup.Unreadable("b"))
        assertNotEquals(CredentialLookup.Missing() as Any, CredentialLookup.Unreadable("a"))
    }

    @Test
    fun anUnreadableCauseMustBeAStableCodeNotAMessage() {
        assertThrows(IllegalArgumentException::class.java) { CredentialLookup.Unreadable("Bad Thing") }
        assertThrows(IllegalArgumentException::class.java) { CredentialLookup.Unreadable("") }
        assertEquals("CredentialLookup.Unreadable(cause=key_lost)", CredentialLookup.Unreadable("key_lost").toString())
    }

    private val modelA = ProviderSelection(ProviderId.ANTHROPIC, "model-a")
    private val modelB = ProviderSelection(ProviderId.OPENAI, "model-b")

    @Test
    fun aBlankModelIsRefusedSoTheEngineNeverInventsADefault() {
        assertThrows(IllegalArgumentException::class.java) { ProviderSelection(ProviderId.ANTHROPIC, " ") }
        assertThrows(IllegalArgumentException::class.java) { ProviderSelection(ProviderId.ANTHROPIC, "", null) }
    }

    @Test
    fun aFallbackHangsOffAnOnDeviceSelectionOnly() {
        val onDevice = ProviderSelection(ProviderId.ON_DEVICE, "local-a", modelA)

        assertEquals(ProviderId.ANTHROPIC, onDevice.fallback?.provider)
        assertEquals("model-a", onDevice.fallback?.model)
        assertNull(modelA.fallback)
        assertThrows(IllegalArgumentException::class.java) {
            ProviderSelection(ProviderId.OPENAI, "model-a", ProviderSelection(ProviderId.ANTHROPIC, "model-b"))
        }
    }

    @Test
    fun theFallbackCannotItselfBeOnDeviceOrChain() {
        assertThrows(IllegalArgumentException::class.java) {
            ProviderSelection(ProviderId.ON_DEVICE, "local-a", ProviderSelection(ProviderId.ON_DEVICE, "local-b"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            ProviderSelection(
                ProviderId.ON_DEVICE,
                "local-a",
                ProviderSelection(ProviderId.ON_DEVICE, "local-b", ProviderSelection(ProviderId.ANTHROPIC, "model-a")),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            ProviderSelection(
                ProviderId.ON_DEVICE,
                "local-a",
                ProviderSelection(ProviderId.ANTHROPIC, "model-a", ProviderSelection(ProviderId.OPENAI, "model-b")),
            )
        }
    }

    @Test
    fun selectionsCompareByProviderModelAndFallbackAndPrintThem() {
        val withFallback = ProviderSelection(ProviderId.ON_DEVICE, "local-a", modelA)

        assertEquals(modelA, ProviderSelection(ProviderId.ANTHROPIC, "model-a", null))
        assertEquals(modelA.hashCode(), ProviderSelection(ProviderId.ANTHROPIC, "model-a").hashCode())
        assertNotEquals(modelA, ProviderSelection(ProviderId.ANTHROPIC, "model-z"))
        assertNotEquals(modelA, modelB)
        assertEquals(withFallback, ProviderSelection(ProviderId.ON_DEVICE, "local-a", modelA))
        assertNotEquals(withFallback, ProviderSelection(ProviderId.ON_DEVICE, "local-a"))
        assertEquals("ProviderSelection(provider=anthropic, model=model-a, fallback=none)", modelA.toString())
        assertEquals(
            "ProviderSelection(provider=on_device, model=local-a, fallback=anthropic/model-a)",
            withFallback.toString(),
        )
    }

    @Test
    fun aSelectionRequestCarriesTheAskingTier() {
        val request = SelectionRequest(StrategyId("tier-a"))

        assertEquals(StrategyId("tier-a"), request.strategy)
        assertEquals("SelectionRequest(strategy=tier-a)", request.toString())
    }

    @Test
    fun onDeviceAvailabilityMirrorsTheMlKitStatuses() {
        assertEquals(
            OnDeviceAvailability.Unavailable("not_implemented"),
            OnDeviceAvailability.Unavailable("not_implemented"),
        )
        assertEquals(
            OnDeviceAvailability.Unavailable("not_implemented").hashCode(),
            OnDeviceAvailability.Unavailable("not_implemented").hashCode(),
        )
        assertNotEquals(OnDeviceAvailability.Unavailable("a"), OnDeviceAvailability.Unavailable("b"))
        assertThrows(IllegalArgumentException::class.java) { OnDeviceAvailability.Unavailable("Not available") }
        assertEquals(OnDeviceAvailability.Available(), OnDeviceAvailability.Available())
        assertNotEquals(OnDeviceAvailability.Available() as Any, OnDeviceAvailability.Downloadable())
        assertNotEquals(OnDeviceAvailability.Downloadable() as Any, OnDeviceAvailability.Downloading())
        assertEquals("OnDeviceAvailability.Available", OnDeviceAvailability.Available().toString())
        assertEquals("OnDeviceAvailability.Downloadable", OnDeviceAvailability.Downloadable().toString())
        assertEquals("OnDeviceAvailability.Downloading", OnDeviceAvailability.Downloading().toString())
        assertEquals(
            "OnDeviceAvailability.Unavailable(code=not_implemented)",
            OnDeviceAvailability.Unavailable("not_implemented").toString(),
        )
    }

    @Test
    fun anAppLambdaReportsOnDeviceAvailability() = runTest {
        val capability = OnDeviceCapability { OnDeviceAvailability.Downloading() }

        assertEquals(OnDeviceAvailability.Downloading(), capability.availability())
    }

    @Test
    fun anAppLambdaNamesProviderAndModelPerTier() = runTest {
        val source = ProviderSelectionSource { request ->
            if (request.strategy == StrategyId("tier-cheap")) modelA else null
        }

        assertEquals(modelA, source.select(SelectionRequest(StrategyId("tier-cheap"))))
        assertNull(source.select(SelectionRequest(StrategyId("tier-strong"))))
    }

    @Test
    fun theScriptedSelectionSourceAnswersInOrderRecordsRequestsAndFailsLoudlyWhenExhausted() = runTest {
        val source = ScriptedSelectionSource(modelA, modelB)

        val first = source.select(SelectionRequest(StrategyId("tier-a")))
        val second = source.select(SelectionRequest(StrategyId("tier-b")))
        val error = runCatching { source.select(SelectionRequest(StrategyId("tier-c"))) }.exceptionOrNull()

        assertEquals(modelA, first)
        assertEquals(modelB, second)
        assertTrue(error is AssertionError)
        assertTrue(error?.message.orEmpty().contains("exhausted"))
        assertEquals(
            listOf(StrategyId("tier-a"), StrategyId("tier-b"), StrategyId("tier-c")),
            source.requests.map { it.strategy },
        )
        assertEquals(THREE, source.calls)
    }

    @Test
    fun aFixedScriptedSelectionSourceAnswersForeverAndCountsCalls() = runTest {
        val source = ScriptedSelectionSource.fixed(modelA)

        repeat(THREE) { assertEquals(modelA, source.select(SelectionRequest(StrategyId("tier-a")))) }

        assertEquals(THREE, source.calls)
        assertNull(ScriptedSelectionSource.fixed(null).select(SelectionRequest(StrategyId("tier-a"))))
    }

    private companion object {
        const val THREE = 3
    }
}
