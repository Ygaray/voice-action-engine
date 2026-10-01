package io.github.ygaray.voiceactionengine.providers.chat

import io.github.ygaray.voiceactionengine.core.failure.FailureDetails
import io.github.ygaray.voiceactionengine.core.failure.FailureReason
import io.github.ygaray.voiceactionengine.providers.http.isTransientStatus
import io.github.ygaray.voiceactionengine.providers.http.safeRequestId
import io.github.ygaray.voiceactionengine.providers.http.safeToken
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

private const val STATUS_OK = 200
private const val STATUS_UNAUTHORIZED = 401
private const val STATUS_TOO_MANY_REQUESTS = 429
private const val STATUS_MIN = 100
private const val STATUS_MAX = 599

private const val KEY_ERROR = "error"
private const val KEY_ID = "id"
private const val KEY_CODE = "code"
private const val KEY_TYPE = "type"
private const val KEY_METADATA = "metadata"
private const val KEY_ERROR_TYPE = "error_type"

private val STATUS_REASONS: Map<Int, () -> FailureReason> = mapOf(
    STATUS_UNAUTHORIZED to { FailureReason.Auth() },
    STATUS_TOO_MANY_REQUESTS to { FailureReason.RateLimited() },
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
    val status = numericCode(error) ?: STATUS_OK
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
    return ChatErrorInfo(
        status = status,
        errorType = errorTypeOf(error),
        requestId = safeRequestId(requestIdHeader) ?: bodyId,
        refined = null,
        transient = isTransientStatus(status),
    )
}

private fun errorTypeOf(error: JsonObject?): String? =
    safeToken(textField(error, KEY_CODE))
        ?: safeToken(textField(error, KEY_TYPE))
        ?: safeToken(textField(error?.get(KEY_METADATA) as? JsonObject, KEY_ERROR_TYPE))

// OpenRouter reports its HTTP-like status as a number in error.code; OpenAI's code is a string.
private fun numericCode(error: JsonObject): Int? {
    val primitive = (error[KEY_CODE] as? JsonPrimitive)?.takeUnless { it.isString }
    return primitive?.content?.toIntOrNull()?.takeIf { it in STATUS_MIN..STATUS_MAX }
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
