package io.github.ygaray.voiceactionengine.keystore

/**
 * What the store knows about the key of one provider. The set is open by design: later versions can add leaves, so a
 * consumer must always keep an `else` branch when it switches on a result.
 */
public abstract class KeyState internal constructor() {
    /** No key was ever stored for the provider. */
    public class NotConfigured : KeyState() {
        override fun equals(other: Any?): Boolean = other is NotConfigured
        override fun hashCode(): Int = NotConfigured::class.java.name.hashCode()
        override fun toString(): String = "KeyState.NotConfigured"
    }

    /**
     * A key is stored and readable.
     *
     * @property last4 the last four characters of the key, shown to the user as a fingerprint. Empty when the key has
     * four characters or fewer, so a short key is never revealed.
     */
    public class Ready(public val last4: String) : KeyState() {
        init {
            require(last4.length <= LAST_CHARS) { "Ready last4 must have at most $LAST_CHARS characters" }
        }

        override fun equals(other: Any?): Boolean = other is Ready && last4 == other.last4
        override fun hashCode(): Int = mix(Ready::class.java.name.hashCode(), last4.hashCode())

        /** Prints the state only: never any part of the key. */
        override fun toString(): String = "KeyState.Ready"
    }

    /** A key is stored but the device key that protected it is gone, so the user should re-enter the key. */
    public class KeyMissing : KeyState() {
        override fun equals(other: Any?): Boolean = other is KeyMissing
        override fun hashCode(): Int = KeyMissing::class.java.name.hashCode()
        override fun toString(): String = "KeyState.KeyMissing"
    }

    /**
     * A key is stored but cannot be read, so the user should re-enter the key.
     *
     * @property cause a stable lower snake case code (`[a-z0-9_]+`, never a message); anything else is refused with
     * [IllegalArgumentException], so free text can never reach `toString`.
     */
    public class Unreadable(public val cause: String) : KeyState() {
        init {
            require(STABLE_CODE.matches(cause)) { "Unreadable cause must be a stable code" }
        }

        override fun equals(other: Any?): Boolean = other is Unreadable && cause == other.cause
        override fun hashCode(): Int = mix(Unreadable::class.java.name.hashCode(), cause.hashCode())
        override fun toString(): String = "KeyState.Unreadable(cause=$cause)"
    }
}

private const val LAST_CHARS = 4
private const val HASH_PRIME = 31
private val STABLE_CODE = Regex("[a-z0-9_]+")

private fun mix(acc: Int, next: Int): Int = acc * HASH_PRIME + next
