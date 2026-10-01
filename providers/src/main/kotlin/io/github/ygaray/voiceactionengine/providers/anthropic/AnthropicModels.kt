package io.github.ygaray.voiceactionengine.providers.anthropic

import io.github.ygaray.voiceactionengine.core.provider.CachingMode
import io.github.ygaray.voiceactionengine.core.provider.ModelCapabilities

private const val OPUS_5_5 = "claude-opus-5-5"
private const val SONNET_5_5 = "claude-sonnet-5-5"
private const val FABLE_5_1 = "claude-fable-5-1"
private const val MYTHOS_5_1 = "claude-mythos-5-1"
private const val HAIKU_4_5 = "claude-haiku-4-5"

private const val STANDARD_MIN_CACHEABLE_PREFIX_TOKENS = 512
private const val HAIKU_MIN_CACHEABLE_PREFIX_TOKENS = 4096

/**
 * What the engine believes about each Anthropic model, matched on the exact model id. The values come from Anthropic's
 * error reference (the first four models reject a request that forces a tool) and its prompt-caching documentation (the
 * shortest cacheable prefix), checked on 2026-09-30. A dated, suffixed, prefixed or differently cased id is not
 * recognised and gets [capabilities]'s default for an unknown model.
 *
 * Every field is only a default: the app patches any of them for an exact model id through
 * `PipelineBuilder.capabilities`, and the patch wins over this table.
 */
internal object AnthropicModels {
    private val rejectsForcedToolChoice = ModelCapabilities {
        supportsForcedToolChoice = false
        caching = CachingMode.EXPLICIT_BREAKPOINTS
        minCacheablePrefixTokens = STANDARD_MIN_CACHEABLE_PREFIX_TOKENS
    }

    private val haiku = ModelCapabilities {
        caching = CachingMode.EXPLICIT_BREAKPOINTS
        minCacheablePrefixTokens = HAIKU_MIN_CACHEABLE_PREFIX_TOKENS
    }

    // The shared default for an id nobody described: forced tool choice allowed (a rejection is still handled when it
    // happens), caching on because the provider always supports explicit breakpoints, minimum unknown so the cache
    // diagnostic stays silent.
    private val unknownModel = ModelCapabilities { caching = CachingMode.EXPLICIT_BREAKPOINTS }

    /** The capabilities for [model]; an id outside the table gets the unknown-model default. */
    fun capabilities(model: String): ModelCapabilities = when (model) {
        OPUS_5_5, SONNET_5_5, FABLE_5_1, MYTHOS_5_1 -> rejectsForcedToolChoice
        HAIKU_4_5 -> haiku
        else -> unknownModel
    }
}
