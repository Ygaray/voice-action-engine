package io.github.ygaray.voiceactionengine.providers.chat

private const val OPENAI_MIN_CACHEABLE_PREFIX_TOKENS = 1_024

// The two models that answer only on the Responses endpoint. The name must end or continue with a dash, so
// "gpt-6-astral" is a different model, and the pattern is anchored so a prefixed id is not matched.
private val RESPONSES_ONLY = Regex("""^gpt-6(?:-astra|\.1-sol)(?:-.*)?$""")
private val GPT_PREFIX = Regex("""^gpt-.*$""")
private val O_SERIES = Regex("""^o\d.*$""")

/**
 * What the engine believes about OpenAI model families on the Chat Completions endpoint, matched by pattern because
 * OpenAI ids carry date suffixes. The facts come from OpenAI's latest-model and function-calling guides, checked on
 * 2026-10-01.
 *
 * Every value is only a default: the app patches any capability for an exact model id through
 * `PipelineBuilder.capabilities`, and the patch wins over this table.
 */
internal object OpenAiModelRules {
    /**
     * False when [id] cannot take tools on Chat Completions. The Astra and 6.1 Sol family accepts tools only on the
     * Responses endpoint, so on OpenAI itself a request with tools is refused before any call. Through a router
     * ([viaRouter] true) the router advertises tools for both and translates, so the answer is true.
     */
    fun toolsOnChat(id: String, viaRouter: Boolean): Boolean = viaRouter || !RESPONSES_ONLY.matches(id)

    /** The shortest prefix OpenAI caches automatically, or null for an id outside the GPT and o-series families. */
    fun minCacheablePrefixTokens(id: String): Int? =
        if (GPT_PREFIX.matches(id) || O_SERIES.matches(id)) OPENAI_MIN_CACHEABLE_PREFIX_TOKENS else null
}
