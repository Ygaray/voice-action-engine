package io.github.ygaray.voiceactionengine.core.failure

/**
 * Transport facts that accompany a failure. Never a response body: bodies can echo prompts, keys or user content.
 *
 * @property httpStatus the HTTP status code, or null when there was no HTTP response.
 * @property providerErrorType the provider's own error type string, or null.
 * @property requestId the provider's request id for support tickets, or null.
 */
public class FailureDetails(
    public val httpStatus: Int?,
    public val providerErrorType: String?,
    public val requestId: String?,
) {
    override fun equals(other: Any?): Boolean =
        other is FailureDetails &&
            httpStatus == other.httpStatus &&
            providerErrorType == other.providerErrorType &&
            requestId == other.requestId

    override fun hashCode(): Int {
        var result = httpStatus ?: 0
        result = mixHash(result, providerErrorType?.hashCode() ?: 0)
        result = mixHash(result, requestId?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String =
        "FailureDetails(httpStatus=$httpStatus, providerErrorType=$providerErrorType, requestId=$requestId)"
}
