package io.github.ygaray.voiceactionengine.core.testing

import io.github.ygaray.voiceactionengine.core.Credential
import io.github.ygaray.voiceactionengine.core.ProviderId
import io.github.ygaray.voiceactionengine.core.provider.CredentialLookup
import io.github.ygaray.voiceactionengine.core.provider.CredentialSource
import java.util.concurrent.CopyOnWriteArrayList

/**
 * A [CredentialSource] that answers from a fixed provider-to-lookup map and records every provider it was asked for.
 * A provider with no entry answers [CredentialLookup.Missing], like an app that never stored a key for it.
 */
public class ScriptedCredentialSource(lookups: Map<ProviderId, CredentialLookup>) : CredentialSource {
    private val answers: Map<ProviderId, CredentialLookup> = lookups.toMap()
    private val asked = CopyOnWriteArrayList<ProviderId>()

    /** Immutable snapshot of every provider id asked for so far, in the order the asks arrived. */
    public val requested: List<ProviderId>
        get() = asked.toList()

    override suspend fun credential(provider: ProviderId): CredentialLookup {
        asked.add(provider)
        return answers[provider] ?: CredentialLookup.Missing()
    }

    /** Ways to create a source. */
    public companion object {
        /** A source holding one present key per pair, given as provider to key; every other provider is missing. */
        public fun keys(vararg keys: Pair<ProviderId, String>): ScriptedCredentialSource =
            ScriptedCredentialSource(
                keys.associate { (provider, key) -> provider to CredentialLookup.Present(Credential(provider, key)) },
            )
    }
}
