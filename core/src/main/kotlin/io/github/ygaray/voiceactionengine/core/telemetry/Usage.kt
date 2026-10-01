package io.github.ygaray.voiceactionengine.core.telemetry

/**
 * Token usage normalized so every provider reports the same four buckets.
 *
 * Mapping from a provider response:
 * - Anthropic: `input_tokens` already excludes cached tokens, so `inputUncached = input_tokens`,
 *   `cacheRead = cache_read_input_tokens`, `cacheWrite = cache_creation_input_tokens`, `output = output_tokens`.
 * - OpenAI-style: `prompt_tokens` includes cached tokens, so `inputUncached = prompt_tokens - cached_tokens`,
 *   `cacheRead = cached_tokens`, `cacheWrite = 0`, `output = completion_tokens`.
 *
 * The same work reported either way therefore has the same [total].
 *
 * @property inputUncached input tokens that were not served from or written to a cache.
 * @property cacheRead input tokens served from a cache.
 * @property cacheWrite input tokens written to a cache.
 * @property output tokens the model generated.
 * @throws IllegalArgumentException when any field is negative.
 */
public class Usage(
    public val inputUncached: Long,
    public val cacheRead: Long,
    public val cacheWrite: Long,
    public val output: Long,
) {
    init {
        require(inputUncached >= 0 && cacheRead >= 0 && cacheWrite >= 0 && output >= 0) {
            "token counts must not be negative"
        }
    }

    /** All four buckets summed. */
    public val total: Long get() = inputUncached + cacheRead + cacheWrite + output

    /** The bucket-wise sum of this usage and [other]. */
    public operator fun plus(other: Usage): Usage = Usage(
        inputUncached + other.inputUncached,
        cacheRead + other.cacheRead,
        cacheWrite + other.cacheWrite,
        output + other.output,
    )

    override fun equals(other: Any?): Boolean =
        other is Usage &&
            inputUncached == other.inputUncached &&
            cacheRead == other.cacheRead &&
            cacheWrite == other.cacheWrite &&
            output == other.output

    override fun hashCode(): Int {
        var result = inputUncached.hashCode()
        result = HASH_PRIME * result + cacheRead.hashCode()
        result = HASH_PRIME * result + cacheWrite.hashCode()
        result = HASH_PRIME * result + output.hashCode()
        return result
    }

    override fun toString(): String =
        "Usage(inputUncached=$inputUncached, cacheRead=$cacheRead, cacheWrite=$cacheWrite, " +
            "output=$output, total=$total)"

    /** Shared values. */
    public companion object {
        /** No tokens used. */
        public val ZERO: Usage = Usage(0, 0, 0, 0)
    }
}

private const val HASH_PRIME = 31
