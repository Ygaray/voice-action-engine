package io.github.ygaray.voiceactionengine.core

/**
 * Stable identity of one strategy (tier) in an app's ladder.
 *
 * The id is something an app may persist, for example as a policy's maximum tier. It is never a ladder index.
 */
@JvmInline
public value class StrategyId(public val value: String) {
    init {
        require(value.isNotBlank()) { "StrategyId value must not be blank" }
    }

    /** The id's string value. */
    override fun toString(): String = value
}
