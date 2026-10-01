package io.github.ygaray.voiceactionengine.providers.http

// Same character class and limits the core failure types enforce; replicated here because core's helpers are internal.
private val SAFE_IDENTIFIER = Regex("[A-Za-z0-9_.:-]+")
private const val TOKEN_MAX_LENGTH = 64
private const val REQUEST_ID_MAX_LENGTH = 128

/**
 * Returns [value] when it is a short provider identifier (1 to 64 characters of letters, digits and `_ . : -`), else
 * null. A server string such as an error type is untrusted, and handing a non-conforming one to the failure types
 * would throw and hide the real failure, so the transport drops it instead.
 */
internal fun safeToken(value: String?): String? =
    value?.takeIf { it.length <= TOKEN_MAX_LENGTH && SAFE_IDENTIFIER.matches(it) }

/** Like [safeToken] for a provider request id, which may be up to 128 characters. */
internal fun safeRequestId(value: String?): String? =
    value?.takeIf { it.length <= REQUEST_ID_MAX_LENGTH && SAFE_IDENTIFIER.matches(it) }

/**
 * True when [value] can be sent as an HTTP header value: visible ASCII, space and tab. OkHttp's own refusal of a bad
 * header value quotes the whole value, so a pasted newline would put an API key in an exception message; a transport
 * runs this on the key before it builds any request and answers a failure with a plain auth reason instead. An empty
 * value passes: whether a credential may be empty is the credential type's concern, not the header's.
 */
internal fun isHeaderSafe(value: String): Boolean = value.all { it == '\t' || it in ' '..'~' }
