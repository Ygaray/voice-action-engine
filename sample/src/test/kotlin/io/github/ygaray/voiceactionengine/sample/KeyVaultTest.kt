package io.github.ygaray.voiceactionengine.sample

import io.github.ygaray.voiceactionengine.core.ProviderId
import io.github.ygaray.voiceactionengine.keystore.KeyState
import io.github.ygaray.voiceactionengine.sample.keys.KeyAction
import io.github.ygaray.voiceactionengine.sample.keys.KeyUx
import io.github.ygaray.voiceactionengine.sample.keys.SampleKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyVaultTest {
    @Test
    fun threeSlotsOneProviderEach() {
        val expected = listOf(ProviderId.ANTHROPIC, ProviderId.OPENAI, ProviderId.OPENROUTER)

        assertEquals(expected, SampleKeys.PROVIDERS)
        assertEquals(expected, SampleKeys.SLOTS.map { it.provider })
    }

    @Test
    fun aliasesAndKeysAreSamplePrefixedAndDistinct() {
        val slots = SampleKeys.SLOTS
        val aliases = slots.map { it.alias }
        val names = slots.flatMap { listOf(it.alias, it.ciphertextKey, it.ivKey) }

        assertTrue(aliases.all { it.startsWith("vae_sample_") })
        assertTrue(slots.all { it.ciphertextKey.startsWith("vae_sample_") && it.ivKey.startsWith("vae_sample_") })
        assertEquals("every alias, ciphertext key and IV key is distinct", names.size, names.toSet().size)
        assertEquals("vae_sample_anthropic", slots.first().alias)
        assertEquals("vae_sample_anthropic_ct", slots.first().ciphertextKey)
        assertEquals("vae_sample_anthropic_iv", slots.first().ivKey)
    }

    @Test
    fun causeCodesMapToTheirUserAction() {
        for (code in listOf("key_missing", "decrypt_failed", "stored_value_malformed")) {
            assertEquals(code, KeyAction.REENTER_KEY, KeyUx.action(code))
        }
        for (code in listOf("keystore_unavailable", "storage_unreadable")) {
            assertEquals(code, KeyAction.TRANSIENT_RETRY, KeyUx.action(code))
        }
        assertEquals("an unknown future cause means re-enter key", KeyAction.REENTER_KEY, KeyUx.action("some_future_code"))
    }

    @Test
    fun stateLabelsAreLoudAndNeverShowTheKey() {
        assertTrue(KeyUx.label(KeyState.Ready("WXYZ")).contains("WXYZ"))
        assertEquals("Not configured", KeyUx.label(KeyState.NotConfigured()))
        assertEquals("Key missing - re-enter key", KeyUx.label(KeyState.KeyMissing()))
        assertTrue(KeyUx.label(KeyState.Unreadable("keystore_unavailable")).contains("transient, retry"))
        assertTrue(KeyUx.label(KeyState.Unreadable("decrypt_failed")).contains("re-enter key"))
        assertTrue(KeyUx.label(KeyState.Unreadable("some_future_code")).contains("re-enter key"))
    }
}
