package io.github.ygaray.voiceactionengine.providers.anthropic

import io.github.ygaray.voiceactionengine.core.failure.FailureDetails
import io.github.ygaray.voiceactionengine.core.failure.FailureReason
import io.github.ygaray.voiceactionengine.providers.http.safeRequestId
import io.github.ygaray.voiceactionengine.providers.http.safeToken
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

private const val STATUS_BAD_REQUEST = 400
private const val STATUS_UNAUTHORIZED = 401
private const val STATUS_PAYMENT_REQUIRED = 402
private const val STATUS_FORBIDDEN = 403
private const val STATUS_NOT_FOUND = 404
private const val STATUS_REQUEST_TIMEOUT = 408
private const val STATUS_TOO_MANY_REQUESTS = 429
private const val STATUS_SERVICE_UNAVAILABLE = 503
private const val STATUS_GATEWAY_TIMEOUT = 504
private const val STATUS_OVERLOADED = 529

private const val SPEND_CAP_ERROR_CODE = "enforced_spend_limit_reached"
private const val USER_SPEND_LIMIT_PREFIX = "You have reached your specified"
private const val TOOL_CHOICE_MARKER = "tool_choice"

// The status decides the reason; the two spend checks in reason() refine a 429 and a 400 before this table is read.
private val STATUS_REASONS: Map<Int, () -> FailureReason> = mapOf(
    STATUS_UNAUTHORIZED to { FailureReason.Auth() },
    STATUS_FORBIDDEN to { FailureReason.Auth() },
    STATUS_PAYMENT_REQUIRED to { FailureReason.Billing() },
    STATUS_TOO_MANY_REQUESTS to { FailureReason.RateLimited() },
    STATUS_NOT_FOUND to { FailureReason.ModelNotFound() },
    STATUS_REQUEST_TIMEOUT to { FailureReason.Timeout() },
    STATUS_GATEWAY_TIMEOUT to { FailureReason.Timeout() },
    STATUS_SERVICE_UNAVAILABLE to { FailureReason.Overloaded() },
    STATUS_OVERLOADED to { FailureReason.Overloaded() },
)

/**
 * What is kept from a non-2xx Messages answer: the status, two safe identifiers and a few facts derived from the error
 * text. The body and the error message are never stored, so nothing the server echoed can reach a failure.
 *
 * @property spendCapReached the account's enforced spend cap was hit (a 429 that is billing, not a rate limit).
 * @property userSpendLimit the user-set spend limit was reached (a 400 that is billing, not a bad request).
 * @property mentionsToolChoice the error message names `tool_choice`.
 */
internal class AnthropicErrorInfo(
    val status: Int,
    val errorType: String?,
    val requestId: String?,
    val spendCapReached: Boolean,
    val userSpendLimit: Boolean,
    val mentionsToolChoice: Boolean,
) {
    // Status and the two identifiers only: the derived flags describe the message and add nothing a log needs.
    override fun toString(): String =
        "AnthropicErrorInfo(status=$status, errorType=$errorType, requestId=$requestId)"
}

/**
 * Reads a non-2xx answer into an [AnthropicErrorInfo] and drops the body. It tolerates a missing, non-JSON or oddly
 * shaped body, and a hostile error type or request id, by leaving that field null; the status always survives.
 */
internal fun parseAnthropicError(status: Int, requestIdHeader: String?, body: String?): AnthropicErrorInfo {
    val root = parseObject(body)
    val error = root?.get("error") as? JsonObject
    val message = textField(error, "message")
    val errorCode = textField(error?.get("details") as? JsonObject, "error_code")
    return AnthropicErrorInfo(
        status = status,
        errorType = safeToken(textField(error, "type")),
        requestId = safeRequestId(requestIdHeader) ?: safeRequestId(textField(root, "request_id")),
        spendCapReached = status == STATUS_TOO_MANY_REQUESTS && errorCode == SPEND_CAP_ERROR_CODE,
        userSpendLimit = status == STATUS_BAD_REQUEST && message?.startsWith(USER_SPEND_LIMIT_PREFIX) == true,
        mentionsToolChoice = message?.contains(TOOL_CHOICE_MARKER) == true,
    )
}

/** The failure reason for this answer, status first; a spend cap or a user spend limit is billing, never a retry. */
internal fun AnthropicErrorInfo.reason(): FailureReason = when {
    spendCapReached || userSpendLimit -> FailureReason.Billing()
    else -> STATUS_REASONS[status]?.invoke() ?: FailureReason.HttpError()
}

/** The transport facts for this answer: status, error type and request id, nothing else. */
internal fun AnthropicErrorInfo.details(): FailureDetails = FailureDetails(status, errorType, requestId)

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
