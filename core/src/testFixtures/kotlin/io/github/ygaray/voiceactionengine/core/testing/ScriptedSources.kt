package io.github.ygaray.voiceactionengine.core.testing

import io.github.ygaray.voiceactionengine.core.Credential
import io.github.ygaray.voiceactionengine.core.ProviderId
import io.github.ygaray.voiceactionengine.core.provider.CredentialLookup
import io.github.ygaray.voiceactionengine.core.provider.CredentialSource
import io.github.ygaray.voiceactionengine.core.provider.ProviderSelection
import io.github.ygaray.voiceactionengine.core.provider.ProviderSelectionSource
import io.github.ygaray.voiceactionengine.core.provider.SelectionRequest
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

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

/**
 * A [ProviderSelectionSource] that answers from a script, in order, and records every [SelectionRequest] it receives.
 * When the script runs dry the call fails with an [AssertionError]: an Error escapes the engine's guarded collapse, so
 * a test that makes an unplanned selection fails loudly instead of turning into an unexpected outcome. A null answer
 * in the script means "not configured".
 */
public class ScriptedSelectionSource private constructor(
    answers: List<ProviderSelection?>,
    private val forever: Boolean,
) : ProviderSelectionSource {
    private val script: List<ProviderSelection?> = answers.toList()
    private val next = AtomicInteger()
    private val seen = CopyOnWriteArrayList<SelectionRequest>()

    /** Answers each ask with the next entry of [answers]. */
    public constructor(answers: List<ProviderSelection?>) : this(answers, forever = false)

    /** Answers each ask with the next of [answers], in order. */
    public constructor(vararg answers: ProviderSelection?) : this(answers.toList(), forever = false)

    /** Immutable snapshot of every request received so far in arrival order, including one that ran the script dry. */
    public val requests: List<SelectionRequest>
        get() = seen.toList()

    /** How many times [select] has been called. */
    public val calls: Int
        get() = seen.size

    override suspend fun select(request: SelectionRequest): ProviderSelection? {
        seen.add(request)
        if (forever) return script.single()
        val index = next.getAndIncrement()
        if (index >= script.size) {
            throw AssertionError("script exhausted: all ${script.size} scripted selections were already used")
        }
        return script[index]
    }

    /** Ways to create a source. */
    public companion object {
        /** A source that answers [selection] on every call, however many there are. */
        public fun fixed(selection: ProviderSelection?): ScriptedSelectionSource =
            ScriptedSelectionSource(listOf(selection), forever = true)
    }
}
