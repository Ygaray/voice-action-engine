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
import io.github.ygaray.voiceactionengine.providers.http.isHeaderSafe
import io.github.ygaray.voiceactionengine.providers.http.isTransientStatus
import io.github.ygaray.voiceactionengine.providers.http.notifyQuietly
import io.github.ygaray.voiceactionengine.providers.http.retryAfterSeconds
import io.github.ygaray.voiceactionengine.providers.http.transientWaitMillis
import io.github.ygaray.voiceactionengine.providers.transcript.conversationRefusal
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
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
private const val STATUS_BAD_REQUEST = 400

// No usable credential means no request. A key OkHttp would refuse as a header value (a pasted trailing newline, a
// non-ASCII character) is a wrong key: it is answered as Auth here, because OkHttp's own refusal quotes the whole
// value of the header in its message.
private fun refusalFor(credential: Credential?): FailureReason? = when {
    credential == null || credential.provider != ProviderId.ANTHROPIC ->
        FailureReason.NotConfigured(ProviderId.ANTHROPIC)
    !isHeaderSafe(credential.apiKey) -> FailureReason.Auth()
    else -> null
}

/**
 * One logical call against the Messages endpoint: encode, POST, await, decode, and at most two more POSTs: one when a
 * request failed in a way that can clear on its own, and one when the model refused a forced tool choice. The two share
 * a ceiling of [MAX_REQUESTS] requests per call, in either order. It holds no per-call state, so concurrent calls are
 * independent.
 *
 * A timeout counts as a failure that can clear on its own, so it gets the one transient retry. The worst case for a
 * server that hangs is therefore two full call timeouts plus the backoff (about two minutes with the 60 s default), and
 * the first request may still be running, and billed, on the server side. Apps that cannot afford that lower the call
 * timeout.
 *
 * Both repeats happen here, below the provider seam, and only repeat the HTTP request. The OkHttp body is one-shot, so
 * this loop is the only place a request is ever sent twice.
 *
 * A call that requires a tool is sent forced when the model accepts that, and otherwise reshaped (tool choice `auto`,
 * strict mode where it is safe, and a closing instruction). A model the capabilities do not describe is tried forced
 * first; if it answers with the specific 400 about `tool_choice`, the same call is re-sent once reshaped. Nothing is
 * remembered between calls.
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
        val mentionsToolChoice: Boolean = false,
    )

    /** Where a call stands: which request is next, whether the transient retry is spent, and how it is encoded. */
    private class Progress(
        val requestsSent: Int,
        val retried: Boolean,
        val reshape: Boolean,
        val kind: AnthropicAttemptKind,
    )

    /** The next request to send, and how long to wait before sending it. */
    private class Resend(val waitMillis: Long?, val progress: Progress)

    suspend fun send(call: ProviderRequest): ModelResult {
        val credential = call.credential
        val refusal = preflightRefusal(call)
        if (refusal != null || credential == null) {
            return refusal ?: ModelResult.Failure(FailureReason.NotConfigured(ProviderId.ANTHROPIC))
        }
        val first = Progress(1, retried = false, reshape = needsReshape(call), kind = AnthropicAttemptKind.INITIAL)
        return withContext(ioDispatcher) { sendWithRetry(call, credential, first) }
    }

    // Checked once per logical call, before the first request is built: never per retry or reshape.
    private fun preflightRefusal(call: ProviderRequest): ModelResult.Failure? =
        refusalFor(call.credential)?.let { ModelResult.Failure(it) }
            ?: conversationRefusal(call, ProviderId.ANTHROPIC) { it is JsonArray }

    // The capabilities already say whether this model takes a forced tool choice (the table, then any app override).
    private fun needsReshape(call: ProviderRequest): Boolean =
        call.request.toolChoice is ToolChoice.Required && !call.capabilities.supportsForcedToolChoice

    // Recursion depth is bounded by MAX_REQUESTS; the wait is a suspend call, so cancelling the command ends it.
    private suspend fun sendWithRetry(call: ProviderRequest, credential: Credential, progress: Progress): ModelResult {
        val attempted = attempt(call, credential, progress.reshape)
        notify(AnthropicAttempt(progress.requestsSent, progress.kind, attempted.status))
        val resend = planResend(call, attempted, progress) ?: return attempted.result
        resend.waitMillis?.let { sleep(it) }
        return sendWithRetry(call, credential, resend.progress)
    }

    // The observer is optional diagnostics; notifyQuietly keeps whatever it throws from changing the call's outcome.
    private fun notify(attempt: AnthropicAttempt) {
        notifyQuietly { observer?.onAttempt(attempt) }
    }

    // A forced request that got the tool_choice 400 is re-sent at once, reshaped; any other failure that can clear on
    // its own gets the single transient retry after its wait. Neither goes past the shared request ceiling.
    private fun planResend(call: ProviderRequest, attempted: Attempted, progress: Progress): Resend? {
        val next = progress.requestsSent + 1
        return when {
            progress.requestsSent >= MAX_REQUESTS -> null
            rejectedForcedTool(call, attempted, progress) ->
                Resend(null, Progress(next, progress.retried, true, AnthropicAttemptKind.FORCED_TOOL_RESHAPE))
            progress.retried -> null
            else -> retryWait(attempted)?.let {
                Resend(it, Progress(next, true, progress.reshape, AnthropicAttemptKind.TRANSIENT_RETRY))
            }
        }
    }

    // All three must hold: the request was forced, the answer is a 400, and the error text names tool_choice.
    private fun rejectedForcedTool(call: ProviderRequest, attempted: Attempted, progress: Progress): Boolean =
        call.request.toolChoice is ToolChoice.Required &&
            !progress.reshape &&
            attempted.status == STATUS_BAD_REQUEST &&
            attempted.mentionsToolChoice

    private fun retryWait(attempted: Attempted): Long? =
        if (attempted.transient) {
            transientWaitMillis(attempted.retryAfterSeconds, retryAfterCapMillis, transientBackoffMillis)
        } else {
            null
        }

    private suspend fun attempt(call: ProviderRequest, credential: Credential, reshape: Boolean): Attempted {
        val request = Request.Builder()
            .url(baseUrl.newBuilder().addPathSegments(MESSAGES_PATH).build())
            .header(HEADER_API_KEY, credential.apiKey)
            .header(HEADER_VERSION, API_VERSION)
            .header(HEADER_CONTENT_TYPE, CONTENT_TYPE_JSON)
            .post(OneShotJsonBody(encodeAnthropicRequest(call, reshape)))
            .build()
        return try {
            interpret(client.newCall(request).await(), call)
        } catch (ignored: InterruptedIOException) {
            // Call and read timeouts surface as this type (SocketTimeoutException is a subclass); no text is read.
            ioFailure(FailureReason.Timeout())
        } catch (ignored: IOException) {
            ioFailure(FailureReason.Network())
        }
    }

    private fun interpret(reply: HttpReply, call: ProviderRequest): Attempted {
        val requestId = reply.headers[HEADER_REQUEST_ID]
        if (reply.isSuccessful) {
            val toolRequired = call.request.toolChoice is ToolChoice.Required
            val decoded = decodeAnthropicResponse(reply.body, requestId, call.model, toolRequired)
            return Attempted(decoded, reply.code, false, null)
        }
        val info = parseAnthropicError(reply.code, requestId, reply.body)
        // A spend cap arrives as a 429 but never clears by waiting, so it is final like any other billing failure.
        val transient = isTransientStatus(info.status) && !info.spendCapReached && !info.userSpendLimit
        return Attempted(
            ModelResult.Failure(info.reason(), info.details()),
            reply.code,
            transient,
            retryAfterSeconds(reply.headers[HEADER_RETRY_AFTER]),
            info.mentionsToolChoice,
        )
    }

    // A cancelled command can also surface as an IOException ("Canceled"); cancellation must win over a failure.
    // A timeout or a dropped connection may clear on its own, so it is worth the one retry.
    private suspend fun ioFailure(reason: FailureReason): Attempted {
        currentCoroutineContext().ensureActive()
        return Attempted(ModelResult.Failure(reason), status = null, transient = true, retryAfterSeconds = null)
    }
}
