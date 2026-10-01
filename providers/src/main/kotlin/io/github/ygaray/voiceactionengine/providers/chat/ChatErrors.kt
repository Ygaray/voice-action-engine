package io.github.ygaray.voiceactionengine.providers.chat

import io.github.ygaray.voiceactionengine.core.failure.FailureDetails
import io.github.ygaray.voiceactionengine.core.failure.FailureReason
import io.github.ygaray.voiceactionengine.providers.http.isTransientStatus
import io.github.ygaray.voiceactionengine.providers.http.safeRequestId
import io.github.ygaray.voiceactionengine.providers.http.safeToken
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

private const val STATUS_OK = 200
private const val STATUS_BAD_REQUEST = 400
private const val STATUS_UNAUTHORIZED = 401
private const val STATUS_PAYMENT_REQUIRED = 402
private const val STATUS_FORBIDDEN = 403
private const val STATUS_NOT_FOUND = 404
private const val STATUS_REQUEST_TIMEOUT = 408
private const val STATUS_TOO_MANY_REQUESTS = 429
private const val STATUS_SERVICE_UNAVAILABLE = 503
private const val STATUS_GATEWAY_TIMEOUT = 504
private const val STATUS_ORIGIN_TIMEOUT = 524
private const val STATUS_OVERLOADED = 529
private const val STATUS_MIN = 100
private const val STATUS_MAX = 599

private const val KEY_ERROR = "error"
private const val KEY_ID = "id"
private const val KEY_CODE = "code"
private const val KEY_TYPE = "type"
private const val KEY_METADATA = "metadata"
private const val KEY_ERROR_TYPE = "error_type"
private const val KEY_MESSAGE = "message"
private const val KEY_REASONS = "reasons"

private const val QUOTA_MARKER = "insufficient_quota"
private const val CONTEXT_LENGTH_CODE = "context_length_exceeded"
// No leading slash, so both "use /v1/responses" and "only supported in v1/responses and not in v1/chat/completions"
// match; the 400/404 status guard keeps it from reading other answers.
private const val RESPONSES_ENDPOINT_MARKER = "v1/responses"
// Both live OpenRouter shapes ("... that support ...", "... that can handle the requested parameters"); the unknown
// model answer "No endpoints found for <model>" does not contain it and stays ModelNotFound.
private const val NO_ENDPOINTS_MARKER = "No endpoints found that"

// The status decides the reason once refine() has had its say; any status not listed (500, 502, 524, 418...) is
// HttpError.
private val STATUS_REASONS: Map<Int, () -> FailureReason> = mapOf(
    STATUS_UNAUTHORIZED to { FailureReason.Auth() },
    STATUS_FORBIDDEN to { FailureReason.Auth() },
    STATUS_PAYMENT_REQUIRED to { FailureReason.Billing() },
    STATUS_NOT_FOUND to { FailureReason.ModelNotFound() },
    STATUS_REQUEST_TIMEOUT to { FailureReason.Timeout() },
    STATUS_GATEWAY_TIMEOUT to { FailureReason.Timeout() },
    STATUS_TOO_MANY_REQUESTS to { FailureReason.RateLimited() },
    STATUS_SERVICE_UNAVAILABLE to { FailureReason.Overloaded() },
    STATUS_OVERLOADED to { FailureReason.Overloaded() },
)

/**
 * What is kept from a Chat Completions error answer: the status, two safe identifiers, a refined reason when the error
 * text called for one, and whether asking again can help. The body and the error message are never stored, so nothing
 * the server echoed can reach a failure.
 *
 * @property status the HTTP status, or the HTTP-like code an error object carried inside a 2xx answer.
 * @property errorType the provider's error code or type, when it is a safe identifier.
 * @property requestId the provider's request id, when it is a safe identifier.
 * @property refined a reason that wins over the status table, or null.
 * @property transient true when one more attempt a moment later can succeed.
 */
internal class ChatErrorInfo(
    val status: Int,
    val errorType: String?,
    val requestId: String?,
    val refined: FailureReason?,
    val transient: Boolean,
) {
    override fun toString(): String = "ChatErrorInfo(status=$status, errorType=$errorType, requestId=$requestId)"
}

/**
 * Reads a non-2xx answer into a [ChatErrorInfo] and drops the body. It tolerates a missing, non-JSON or oddly shaped
 * body, and a hostile error type or request id, by leaving that field null; the status always survives. The request id
 * comes from [requestIdHeader] when it is valid, else, only when [requestIdInBody] is true, from the body's `id`.
 */
internal fun parseChatError(
    status: Int,
    requestIdHeader: String?,
    body: String?,
    requestIdInBody: Boolean,
): ChatErrorInfo = buildInfo(status, parseObject(body), requestIdHeader, requestIdInBody)

/**
 * Reads the `error` object a 2xx answer can carry in place of choices, or returns null when [root] has none. The status
 * is the numeric `error.code` when it is an HTTP-like code (100 to 599), else 200. Only ids are kept, as for
 * [parseChatError].
 */
internal fun chatEnvelopeError(root: JsonObject, requestIdHeader: String?, requestIdInBody: Boolean): ChatErrorInfo? {
    val error = root[KEY_ERROR] as? JsonObject ?: return null
    // OpenRouter reports its HTTP-like status as a number in error.code; OpenAI's code is a string.
    val code = (error[KEY_CODE] as? JsonPrimitive)?.takeUnless { it.isString }
    val status = code?.content?.toIntOrNull()?.takeIf { it in STATUS_MIN..STATUS_MAX } ?: STATUS_OK
    return buildInfo(status, root, requestIdHeader, requestIdInBody)
}

/** The failure reason for this answer: the refinement when there is one, else the status table. */
internal fun ChatErrorInfo.reason(): FailureReason =
    refined ?: STATUS_REASONS[status]?.invoke() ?: FailureReason.HttpError()

/** The transport facts for this answer: status, error type and request id, nothing else. */
internal fun ChatErrorInfo.details(): FailureDetails = FailureDetails(status, errorType, requestId)

private fun buildInfo(
    status: Int,
    root: JsonObject?,
    requestIdHeader: String?,
    requestIdInBody: Boolean,
): ChatErrorInfo {
    val error = root?.get(KEY_ERROR) as? JsonObject
    val bodyId = if (requestIdInBody) safeRequestId(textField(root, KEY_ID)) else null
    val quota = isQuotaExhausted(error)
    return ChatErrorInfo(
        status = status,
        // The code names the failure best (OpenAI's string code), then the type, then OpenRouter's metadata error_type.
        errorType = safeToken(textField(error, KEY_CODE))
            ?: safeToken(textField(error, KEY_TYPE))
            ?: safeToken(textField(error?.get(KEY_METADATA) as? JsonObject, KEY_ERROR_TYPE)),
        requestId = safeRequestId(requestIdHeader) ?: bodyId,
        refined = refine(status, error, quota),
        // An exhausted quota arrives as a 429 yet never clears by waiting, so it is final.
        transient = (isTransientStatus(status) || status == STATUS_ORIGIN_TIMEOUT) && !quota,
    )
}

// Refinements read the error text into locals only; they win over the status table, in this order.
private fun refine(status: Int, error: JsonObject?, quota: Boolean): FailureReason? = when {
    quota -> FailureReason.Billing()
    textField(error, KEY_CODE) == CONTEXT_LENGTH_CODE -> FailureReason.ContextWindowExceeded()
    isUnsupportedEndpoint(status, textField(error, KEY_MESSAGE)) -> FailureReason.ModelUnsupported()
    status == STATUS_FORBIDDEN && (error?.get(KEY_METADATA) as? JsonObject)?.get(KEY_REASONS) is JsonArray ->
        FailureReason.Refusal()
    else -> null
}

private fun isQuotaExhausted(error: JsonObject?): Boolean =
    textField(error, KEY_CODE) == QUOTA_MARKER || textField(error, KEY_TYPE) == QUOTA_MARKER

// A model that cannot take the request on this endpoint: OpenAI points reasoning-with-tools models at the Responses
// API, and OpenRouter has no route for a parameter the model does not support.
private fun isUnsupportedEndpoint(status: Int, message: String?): Boolean {
    val responsesApi = status == STATUS_BAD_REQUEST || status == STATUS_NOT_FOUND
    return message != null &&
        ((responsesApi && message.contains(RESPONSES_ENDPOINT_MARKER)) ||
            (status == STATUS_NOT_FOUND && message.contains(NO_ENDPOINTS_MARKER)))
}

private fun parseObject(body: String?): JsonObject? {
    if (body.isNullOrBlank()) return null
    return try {
        Json.parseToJsonElement(body) as? JsonObject
    } catch (expected: IllegalArgumentException) {
        // A non-JSON body (SerializationException is a subclass) is a normal error answer from a proxy or gateway.
        null
    }
}

private fun textField(from: JsonObject?, name: String): String? {
    val element: JsonElement? = from?.get(name)
    return (element as? JsonPrimitive)?.takeIf { it.isString }?.content
}
