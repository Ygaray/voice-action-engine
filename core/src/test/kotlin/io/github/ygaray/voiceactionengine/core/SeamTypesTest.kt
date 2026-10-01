package io.github.ygaray.voiceactionengine.core

import io.github.ygaray.voiceactionengine.core.provider.CredentialLookup
import io.github.ygaray.voiceactionengine.core.provider.CredentialSource
import io.github.ygaray.voiceactionengine.core.testing.ScriptedCredentialSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
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
}
