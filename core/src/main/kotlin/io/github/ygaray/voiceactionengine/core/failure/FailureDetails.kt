package io.github.ygaray.voiceactionengine.core.failure

/**
 * Transport facts that accompany a failure. Never a response body: bodies can echo prompts, keys or user content.
 *
 * @property httpStatus the HTTP status code, or null when there was no HTTP response.
 * @property providerErrorType the provider's own error type string, or null.
 * @property requestId the provider's request id for support tickets, or null.
 * @throws IllegalArgumentException when [providerErrorType] is not a short identifier (letters, digits and `_ . : -`,
 * 1 to 64 characters) or [requestId] is not one of 1 to 128 such characters. Both come from the server, so they are
 * checked like any other text that could reach `toString`; a transport drops a value that does not fit.
 */
public class FailureDetails(
    public val httpStatus: Int?,
    public val providerErrorType: String?,
    public val requestId: String?,
) {
    init {
        require(providerErrorType == null || isSafeToken(providerErrorType)) {
            "providerErrorType must be a short identifier"
        }
        require(requestId == null || isRequestId(requestId)) { "requestId must be a short identifier" }
    }

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
