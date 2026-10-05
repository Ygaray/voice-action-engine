package io.github.ygaray.voiceactionengine.providers.chat

private const val OPENAI_MIN_CACHEABLE_PREFIX_TOKENS = 1_024
private const val EFFORT_NONE = "none"
private const val EFFORT_LOW = "low"
private const val MAX_COMPLETION_TOKENS = "max_completion_tokens"
private const val MAX_TOKENS = "max_tokens"
private const val NO_MIN_TOKENS = 1
private const val OPENROUTER_MIN_MAX_TOKENS = 16

// The first gpt-5.N generation that needs reasoning_effort none to take tools on Chat Completions.
private const val FIRST_NONE_EFFORT_MINOR = 4

// The two models that answer only on the Responses endpoint. The name must end or continue with a dash, so
// "gpt-6-astral" is a different model, and the pattern is anchored so a prefixed id is not matched.
private val RESPONSES_ONLY = Regex("""^gpt-6(?:-astra|\.1-sol)(?:-.*)?$""")
// The -pro and -codex models of the gpt-5 and gpt-6 generations take neither Chat Completions with tools nor effort
// none; the optional dash suffix covers dated ids, and the anchors keep gpt-5.5 and gpt-6-sol out.
private val PRO_OR_CODEX = Regex("""^gpt-[56](?:\.\d+)?-(?:pro|codex)(?:-.*)?$""")
private val GPT_PREFIX = Regex("""^gpt-.*$""")
private val O_SERIES = Regex("""^o\d.*$""")

// Rule patterns, anchored so a longer or prefixed id never matches by accident. A dated id matches its family.
private val GPT_6_FAMILY = Regex("""^gpt-6(?:[.-].*)?$""")
private val GPT_5_MINOR = Regex("""^gpt-5\.(\d+)(?:[-.].*)?$""")
private val GPT_5_BASE = Regex("""^gpt-5(?:-.*)?$""")
private val GPT_4_FAMILY = Regex("""^gpt-4(?:[-.o].*)?$""")
private val GPT_3_5_FAMILY = Regex("""^gpt-3\.5(?:-.*)?$""")

/**
 * The wire parameters a model family takes on Chat Completions. These stay out of the public capabilities on purpose:
 * the public type is frozen at the tag, and these are details of one dialect.
 *
 * @property reasoningEffortWithTools the `reasoning_effort` value to send when the request carries tools, or null to
 * send none. It is never sent without tools.
 * @property tokenParam the name of the output-limit parameter, `max_completion_tokens` or `max_tokens`.
 * @property minTokens the smallest output limit the endpoint accepts; a smaller request value is raised to it.
 * @property acceptsParallelToolCalls false when the model rejects the parallel tool-call switch outright (the
 * o-series reasoning models answer 400 for it); the encoder then leaves the switch out and the strategy's first-call
 * rule bounds the answer.
 */
internal class ChatWireRules(
    val reasoningEffortWithTools: String?,
    val tokenParam: String,
    val minTokens: Int,
    val acceptsParallelToolCalls: Boolean,
) {
    override fun toString(): String =
        "ChatWireRules(reasoningEffortWithTools=$reasoningEffortWithTools, tokenParam=$tokenParam, " +
            "minTokens=$minTokens, acceptsParallelToolCalls=$acceptsParallelToolCalls)"
}

/**
 * What the engine believes about OpenAI model families on the Chat Completions endpoint, matched by pattern because
 * OpenAI ids carry date suffixes. The facts come from OpenAI's latest-model and function-calling guides, checked on
 * 2026-10-05.
 *
 * Every value is only a default: the app patches any capability for an exact model id through
 * `PipelineBuilder.capabilities`, and the patch wins over this table.
 */
internal object OpenAiModelRules {
    /**
     * False when [id] cannot take tools on Chat Completions. The Astra and 6.1 Sol family accepts tools only on the
     * Responses endpoint, and OpenAI documents no Chat Completions support for the -pro and -codex models, so on
     * OpenAI itself a request with tools to any of them is refused before any call. Through a router ([viaRouter]
     * true) the router advertises tools and translates, so the answer is true.
     */
    fun toolsOnChat(id: String, viaRouter: Boolean): Boolean =
        viaRouter || !(RESPONSES_ONLY.matches(id) || PRO_OR_CODEX.matches(id))

    /** The shortest prefix OpenAI caches automatically, or null for an id outside the GPT and o-series families. */
    fun minCacheablePrefixTokens(id: String): Int? =
        if (GPT_PREFIX.matches(id) || O_SERIES.matches(id)) OPENAI_MIN_CACHEABLE_PREFIX_TOKENS else null

    /**
     * The wire rules for [id], matched in this fixed order: the Astra and 6.1 Sol family through a router (takes
     * tools at effort low); the same family on OpenAI itself (no effort, which the endpoint answers with its own
     * pointer to the Responses endpoint); other gpt-6 ids and gpt-5.N from the fourth minor on (effort none, so the
     * endpoint accepts tools), except direct -pro and -codex ids, which reject none and fall to the next rows; the
     * original gpt-5 family, the earlier gpt-5.N and the o-series (no effort, reasoning is allowed with tools);
     * gpt-4 and gpt-3.5 (no effort, the legacy `max_tokens`); anything else (no effort, `max_completion_tokens`,
     * which OpenAI prefers over the deprecated name).
     *
     * A router enforces a floor of 16 on the legacy `max_tokens`, so the minimum rises only for that pair. Every row
     * takes the parallel tool-call switch except the o-series, which rejects it.
     */
    fun wireRules(id: String, viaRouter: Boolean): ChatWireRules {
        val parallel = !O_SERIES.matches(id)
        val completion = rules(null, MAX_COMPLETION_TOKENS, NO_MIN_TOKENS, parallel)
        return when {
            viaRouter && RESPONSES_ONLY.matches(id) ->
                rules(EFFORT_LOW, MAX_COMPLETION_TOKENS, NO_MIN_TOKENS, parallel)
            RESPONSES_ONLY.matches(id) -> completion
            takesEffortNone(id, viaRouter) ->
                rules(EFFORT_NONE, MAX_COMPLETION_TOKENS, NO_MIN_TOKENS, parallel)
            GPT_5_BASE.matches(id) || GPT_5_MINOR.matches(id) || O_SERIES.matches(id) -> completion
            GPT_4_FAMILY.matches(id) || GPT_3_5_FAMILY.matches(id) ->
                rules(null, MAX_TOKENS, if (viaRouter) OPENROUTER_MIN_MAX_TOKENS else NO_MIN_TOKENS, parallel)
            else -> completion
        }
    }

    /** The rules for a routed model that is not OpenAI's: no reasoning parameter, the legacy token name, floor 16. */
    fun routedDefaultRules(): ChatWireRules = rules(null, MAX_TOKENS, OPENROUTER_MIN_MAX_TOKENS, true)

    private fun rules(effort: String?, tokenParam: String, minTokens: Int, parallel: Boolean) =
        ChatWireRules(effort, tokenParam, minTokens, parallel)

    // The later generations need effort none to take tools, except a direct -pro or -codex id, which rejects it.
    private fun takesEffortNone(id: String, viaRouter: Boolean): Boolean =
        (GPT_6_FAMILY.matches(id) || isLaterGpt5(id)) && (viaRouter || !PRO_OR_CODEX.matches(id))

    private fun isLaterGpt5(id: String): Boolean {
        val minor = GPT_5_MINOR.matchEntire(id)?.groupValues?.get(1)?.toIntOrNull() ?: return false
        return minor >= FIRST_NONE_EFFORT_MINOR
    }
}
