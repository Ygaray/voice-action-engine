package io.github.ygaray.voiceactionengine.providers.http

private const val STATUS_REQUEST_TIMEOUT = 408
private const val STATUS_TOO_MANY_REQUESTS = 429
private const val STATUS_INTERNAL_ERROR = 500
private const val STATUS_BAD_GATEWAY = 502
private const val STATUS_SERVICE_UNAVAILABLE = 503
private const val STATUS_GATEWAY_TIMEOUT = 504
private const val STATUS_OVERLOADED = 529
private const val MILLIS_PER_SECOND = 1_000L

private val TRANSIENT_STATUSES = setOf(
    STATUS_REQUEST_TIMEOUT,
    STATUS_TOO_MANY_REQUESTS,
    STATUS_INTERNAL_ERROR,
    STATUS_BAD_GATEWAY,
    STATUS_SERVICE_UNAVAILABLE,
    STATUS_GATEWAY_TIMEOUT,
    STATUS_OVERLOADED,
)

private val DELAY_SECONDS = Regex("[0-9]+")

/**
 * True for the statuses a provider answers with when asking again a moment later can succeed: request timeout, rate
 * limit, internal error, bad gateway, unavailable, gateway timeout and the overloaded status some providers add.
 * Whether a particular answer is worth retrying can still be refined by the caller (a spend cap is a 429 that never
 * clears by waiting).
 */
internal fun isTransientStatus(code: Int): Boolean = code in TRANSIENT_STATUSES

/**
 * The `Retry-After` header as whole seconds, or null when it is missing or not a plain non-negative integer. The
 * HTTP-date form is deliberately not understood: it would need a clock, and a caller that gets null falls back to its
 * own backoff. A number too large for a long is returned as the largest long so it is treated as "far too long".
 */
internal fun retryAfterSeconds(header: String?): Long? {
    val text = header?.trim()
    return when {
        text == null || !DELAY_SECONDS.matches(text) -> null
        else -> text.toLongOrNull() ?: Long.MAX_VALUE
    }
}

/**
 * How long to wait before the one transient retry, or null when the retry should not happen.
 *
 * The server's own [retryAfterSeconds] wins when it is present and fits within [capMillis]; a longer ask means the
 * server wants more patience than a voice command can spend, so the call ends with the failure it already has. With
 * no usable header the wait is [backoffMillis].
 */
internal fun transientWaitMillis(retryAfterSeconds: Long?, capMillis: Long, backoffMillis: Long): Long? {
    return when {
        retryAfterSeconds == null -> backoffMillis
        retryAfterSeconds > capMillis / MILLIS_PER_SECOND -> null
        else -> retryAfterSeconds * MILLIS_PER_SECOND
    }
}
