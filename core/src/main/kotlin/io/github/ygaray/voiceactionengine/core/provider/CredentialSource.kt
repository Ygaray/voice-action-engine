package io.github.ygaray.voiceactionengine.core.provider

import io.github.ygaray.voiceactionengine.core.Credential
import io.github.ygaray.voiceactionengine.core.ProviderId
import io.github.ygaray.voiceactionengine.core.failure.isStableCode
import io.github.ygaray.voiceactionengine.core.failure.mixHash

/**
 * Where the engine gets an API key from. The app implements it over whatever store it uses; the engine never reads
 * storage itself.
 *
 * The engine asks only for the provider it is about to call, and it refuses a [Credential] stamped for any other
 * provider. A lookup that cannot produce a key answers [CredentialLookup.Missing] or [CredentialLookup.Unreadable]
 * instead of throwing, so the two situations stay distinct for the user.
 */
public fun interface CredentialSource {
    /** Looks up the key for [provider]. */
    public suspend fun credential(provider: ProviderId): CredentialLookup
}

/**
 * The answer to a [CredentialSource] lookup. The set is open by design: later engine versions and adapters can add
 * leaves, so a consumer must always keep an `else` branch when it switches on a result.
 */
public abstract class CredentialLookup internal constructor() {
    /** A key was found; [credential] holds it. */
    public class Present(public val credential: Credential) : CredentialLookup() {
        /** Prints the provider only: never the key. */
        override fun toString(): String = "CredentialLookup.Present(provider=${credential.provider})"
    }

    /** No key is stored for the provider, so it is not configured. */
    public class Missing : CredentialLookup() {
        override fun equals(other: Any?): Boolean = other is Missing
        override fun hashCode(): Int = Missing::class.java.name.hashCode()
        override fun toString(): String = "CredentialLookup.Missing"
    }

    /**
     * A key exists but cannot be read, for example because the device key that protected it was lost. The user should
     * be asked to re-enter the key, which is different from not being configured at all.
     *
     * @property cause a stable lower snake case code (`[a-z0-9_]+`, never a message); anything else is refused with
     * [IllegalArgumentException], so free text can never reach `toString`.
     */
    public class Unreadable(public val cause: String) : CredentialLookup() {
        init {
            require(isStableCode(cause)) { "Unreadable cause must be a stable code" }
        }

        override fun equals(other: Any?): Boolean = other is Unreadable && cause == other.cause
        override fun hashCode(): Int = mixHash(Unreadable::class.java.name.hashCode(), cause.hashCode())
        override fun toString(): String = "CredentialLookup.Unreadable(cause=$cause)"
    }
}
