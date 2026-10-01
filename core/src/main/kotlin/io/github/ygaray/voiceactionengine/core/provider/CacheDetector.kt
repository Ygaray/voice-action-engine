package io.github.ygaray.voiceactionengine.core.provider

import io.github.ygaray.voiceactionengine.core.telemetry.Usage
import io.github.ygaray.voiceactionengine.core.telemetry.saturatedAdd
import io.github.ygaray.voiceactionengine.core.transcript.CacheDirective
import io.github.ygaray.voiceactionengine.core.transcript.ModelRequest

// Automatic caching needs one earlier request to have written the entry, so the first successful turn is never judged.
private const val FIRST_AUTOMATIC_CHECK_TURN = 2

/**
 * The size of the static part of [request] (system prompt plus every tool's name, description and compact JSON
 * schema), counted in `String` length, which is UTF-16 code units: a supplementary character counts 2. That can only
 * over-count, and [estimatedPrefixTokens] caps the result by what the provider billed. The sum is a `Long` so a huge
 * request cannot wrap.
 */
internal fun prefixChars(request: ModelRequest): Long {
    var total = request.system.length.toLong()
    for (tool in request.tools) {
        total += tool.name.length.toLong() + tool.description.length.toLong() + tool.inputSchema.toString().length
    }
    return total
}

/**
 * A deliberately low estimate of how many tokens the static prefix took: `floor(prefixChars / charsPerToken)`, never
 * more than the prompt total the response itself reports (`inputUncached + cacheRead + cacheWrite`, which stops at
 * [Long.MAX_VALUE] instead of wrapping). The cap makes the estimate a number the provider has already confirmed was
 * at least that large, so a prefix the engine over-counted cannot raise a false alarm.
 */
internal fun estimatedPrefixTokens(prefixChars: Long, charsPerToken: Double, usage: Usage): Long {
    val fromCharacters = (prefixChars / charsPerToken).toLong()
    val promptTotal = saturatedAdd(saturatedAdd(usage.inputUncached, usage.cacheRead), usage.cacheWrite)
    return minOf(fromCharacters, promptTotal)
}

/**
 * True when a model that caches prompt prefixes should have used the cache on this response and did not. It stays
 * silent whenever the engine is not sure: the request did not ask for the static prefix to be cached, the caching mode
 * or the minimum cacheable prefix is unknown, or the estimated prefix is below that minimum.
 *
 * - Explicit breakpoints: fires on any turn when nothing was read from or written to the cache.
 * - Automatic caching: fires only from the second successful turn of a handle, when nothing was read; writes are
 *   ignored.
 *
 * [successfulTurn] is 1 for the first successful response of the handle. A prefix that drifts so the cache is written
 * again every turn (explicit mode, writes above zero) is not flagged.
 */
internal fun shouldFlagCacheMiss(
    directive: CacheDirective,
    capabilities: ModelCapabilities,
    usage: Usage,
    successfulTurn: Int,
    prefixChars: Long,
): Boolean {
    val minimum = capabilities.minCacheablePrefixTokens
    val sure = directive.staticPrefix && minimum != null &&
        estimatedPrefixTokens(prefixChars, capabilities.charsPerToken, usage) >= minimum
    return sure && when (capabilities.caching) {
        CachingMode.EXPLICIT_BREAKPOINTS -> usage.cacheRead == 0L && usage.cacheWrite == 0L
        CachingMode.AUTOMATIC -> successfulTurn >= FIRST_AUTOMATIC_CHECK_TURN && usage.cacheRead == 0L
        else -> false
    }
}
