package io.github.ygaray.voiceactionengine.providers.anthropic

import io.github.ygaray.voiceactionengine.core.Credential
import io.github.ygaray.voiceactionengine.core.ProviderId
import io.github.ygaray.voiceactionengine.core.failure.FailureReason
import io.github.ygaray.voiceactionengine.core.provider.ModelResult
import io.github.ygaray.voiceactionengine.core.provider.ProviderRequest
import io.github.ygaray.voiceactionengine.core.transcript.ToolChoice
import io.github.ygaray.voiceactionengine.providers.http.HttpReply
import io.github.ygaray.voiceactionengine.providers.http.OneShotJsonBody
import io.github.ygaray.voiceactionengine.providers.http.await
import io.github.ygaray.voiceactionengine.providers.http.isTransientStatus
import io.github.ygaray.voiceactionengine.providers.http.retryAfterSeconds
import io.github.ygaray.voiceactionengine.providers.http.transientWaitMillis
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.io.InterruptedIOException

private const val MESSAGES_PATH = "v1/messages"
private const val API_VERSION = "2023-06-01"
private const val HEADER_API_KEY = "x-api-key"
private const val HEADER_VERSION = "anthropic-version"
private const val HEADER_CONTENT_TYPE = "content-type"
private const val HEADER_REQUEST_ID = "request-id"
private const val HEADER_RETRY_AFTER = "retry-after"
private const val CONTENT_TYPE_JSON = "application/json"

// The most HTTP requests one logical call may send: the first, one transient retry and one reshaped request.
private const val MAX_REQUESTS = 3

/**
 * One logical call against the Messages endpoint: encode, POST, await, decode, and at most one more POST when the first
 * failed in a way that can clear on its own. It holds no per-call state, so concurrent calls are independent.
 *
 * The retry happens here, below the provider seam, and only repeats the HTTP request. The OkHttp body is one-shot, so
 * this loop is the only place a request is ever sent twice.
 */
internal class AnthropicTransport(
    val client: OkHttpClient,
    val baseUrl: HttpUrl,
    private val ioDispatcher: CoroutineDispatcher,
    private val sleep: suspend (Long) -> Unit,
    private val retryAfterCapMillis: Long,
    private val transientBackoffMillis: Long,
    private val observer: AnthropicAttemptObserver?,
) {
    /** What one HTTP attempt produced (and its status, when it got one) and whether asking again could help. */
    private class Attempted(
        val result: ModelResult,
        val status: Int?,
        val transient: Boolean,
        val retryAfterSeconds: Long?,
    )

    suspend fun send(call: ProviderRequest): ModelResult {
        val credential = call.credential
        if (credential == null || credential.provider != ProviderId.ANTHROPIC) {
            return ModelResult.Failure(FailureReason.NotConfigured(ProviderId.ANTHROPIC))
        }
        return withContext(ioDispatcher) { sendWithRetry(call, credential, requestsSent = 1, retried = false) }
    }

    // Recursion depth is bounded by MAX_REQUESTS; the wait is a suspend call, so cancelling the command ends it.
    private suspend fun sendWithRetry(
        call: ProviderRequest,
        credential: Credential,
        requestsSent: Int,
        retried: Boolean,
    ): ModelResult {
        val attempted = attempt(call, credential)
        val kind = if (retried) AnthropicAttemptKind.TRANSIENT_RETRY else AnthropicAttemptKind.INITIAL
        observer?.onAttempt(AnthropicAttempt(requestsSent, kind, attempted.status))
        val wait = if (retried || requestsSent >= MAX_REQUESTS) null else retryWait(attempted)
        if (wait == null) return attempted.result
        sleep(wait)
        return sendWithRetry(call, credential, requestsSent + 1, retried = true)
    }

    // The capabilities already say whether this model takes a forced tool choice (the table, then any app override).
    private fun needsReshape(call: ProviderRequest): Boolean =
        call.request.toolChoice is ToolChoice.Required && !call.capabilities.supportsForcedToolChoice

    private fun retryWait(attempted: Attempted): Long? =
        if (attempted.transient) {
            transientWaitMillis(attempted.retryAfterSeconds, retryAfterCapMillis, transientBackoffMillis)
        } else {
            null
        }

    private suspend fun attempt(call: ProviderRequest, credential: Credential): Attempted {
        val request = Request.Builder()
            .url(baseUrl.newBuilder().addPathSegments(MESSAGES_PATH).build())
            .header(HEADER_API_KEY, credential.apiKey)
            .header(HEADER_VERSION, API_VERSION)
            .header(HEADER_CONTENT_TYPE, CONTENT_TYPE_JSON)
            .post(OneShotJsonBody(encodeAnthropicRequest(call, needsReshape(call))))
            .build()
        return try {
            interpret(client.newCall(request).await(), call.model)
        } catch (ignored: InterruptedIOException) {
            // Call and read timeouts surface as this type (SocketTimeoutException is a subclass); no text is read.
            ioFailure(FailureReason.Timeout())
        } catch (ignored: IOException) {
            ioFailure(FailureReason.Network())
        }
    }

    private fun interpret(reply: HttpReply, model: String): Attempted {
        val requestId = reply.headers[HEADER_REQUEST_ID]
        if (reply.isSuccessful) {
            return Attempted(decodeAnthropicResponse(reply.body, requestId, model), reply.code, false, null)
        }
        val info = parseAnthropicError(reply.code, requestId, reply.body)
        // A spend cap arrives as a 429 but never clears by waiting, so it is final like any other billing failure.
        val transient = isTransientStatus(info.status) && !info.spendCapReached && !info.userSpendLimit
        return Attempted(
            ModelResult.Failure(info.reason(), info.details()),
            reply.code,
            transient,
            retryAfterSeconds(reply.headers[HEADER_RETRY_AFTER]),
        )
    }

    // A cancelled command can also surface as an IOException ("Canceled"); cancellation must win over a failure.
    // A timeout or a dropped connection may clear on its own, so it is worth the one retry.
    private suspend fun ioFailure(reason: FailureReason): Attempted {
        currentCoroutineContext().ensureActive()
        return Attempted(ModelResult.Failure(reason), status = null, transient = true, retryAfterSeconds = null)
    }
}
