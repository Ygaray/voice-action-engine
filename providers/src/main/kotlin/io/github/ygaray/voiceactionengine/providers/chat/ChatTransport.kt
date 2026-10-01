package io.github.ygaray.voiceactionengine.providers.chat

import io.github.ygaray.voiceactionengine.core.Credential
import io.github.ygaray.voiceactionengine.core.failure.FailureReason
import io.github.ygaray.voiceactionengine.core.provider.ModelResult
import io.github.ygaray.voiceactionengine.core.provider.ProviderRequest
import io.github.ygaray.voiceactionengine.core.transcript.ToolChoice
import io.github.ygaray.voiceactionengine.providers.http.HttpReply
import io.github.ygaray.voiceactionengine.providers.http.OneShotJsonBody
import io.github.ygaray.voiceactionengine.providers.http.await
import io.github.ygaray.voiceactionengine.providers.http.isHeaderSafe
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

private const val CHAT_PATH = "chat/completions"
private const val HEADER_AUTHORIZATION = "Authorization"
private const val HEADER_CONTENT_TYPE = "content-type"
private const val HEADER_RETRY_AFTER = "retry-after"
private const val CONTENT_TYPE_JSON = "application/json"
private const val BEARER_PREFIX = "Bearer "

// The most HTTP requests one logical call may send: the first and one transient retry.
private const val MAX_REQUESTS = 2

// No usable credential means no request. A key OkHttp would refuse as a header value (a pasted trailing newline, a
// non-ASCII character) is a wrong key: it is answered as Auth here, because OkHttp's own refusal quotes the whole
// value of the header in its message.
private fun refusalFor(vendor: ChatVendor, credential: Credential?): FailureReason? = when {
    credential == null || credential.provider != vendor.providerId -> FailureReason.NotConfigured(vendor.providerId)
    !isHeaderSafe(credential.apiKey) -> FailureReason.Auth()
    else -> null
}

/**
 * How the one transient retry waits: [sleep] is a suspend call, so cancelling the command ends the wait; a server's
 * `retry-after` is honoured up to [retryAfterCapMillis] (a longer ask ends the call), otherwise the wait is
 * [transientBackoffMillis].
 */
internal class ChatRetryTiming(
    val sleep: suspend (Long) -> Unit,
    val retryAfterCapMillis: Long,
    val transientBackoffMillis: Long,
) {
    override fun toString(): String =
        "ChatRetryTiming(retryAfterCapMillis=$retryAfterCapMillis, transientBackoffMillis=$transientBackoffMillis)"
}

/**
 * One logical call against a Chat Completions endpoint: vet the key, encode, POST, await, decode or map the error, and
 * at most one more POST when the first one failed in a way that can clear on its own (a transient status, an error the
 * vendor wrapped in a 200, a timeout or a lost connection). A quota error, a client error and a malformed success are
 * final. It holds no per-call state, so concurrent calls are independent.
 *
 * A timeout counts as a failure that can clear on its own, so it gets the one retry. The worst case for a server that
 * hangs is therefore two full call timeouts plus the backoff, and the first request may still be running, and billed,
 * on the server side. Apps that cannot afford that lower the call timeout.
 *
 * The repeat happens here, below the provider seam, and only repeats the HTTP request. The OkHttp body is one-shot, so
 * this loop is the only place a request is ever sent twice.
 */
internal class ChatTransport(
    val client: OkHttpClient,
    val baseUrl: HttpUrl,
    val vendor: ChatVendor,
    private val ioDispatcher: CoroutineDispatcher,
    private val timing: ChatRetryTiming,
    private val observer: ChatCompletionsAttemptObserver?,
) {
    /** What one HTTP attempt produced, its status when it got one, and whether asking again could help. */
    private class Attempted(
        val result: ModelResult,
        val status: Int?,
        val transient: Boolean,
        val retryAfterSeconds: Long?,
        val finishReason: String?,
        val toolCalls: Int,
    )

    suspend fun send(call: ProviderRequest): ModelResult {
        val credential = call.credential
        val refusal = refusalFor(vendor, credential)
        if (refusal != null || credential == null) {
            return ModelResult.Failure(refusal ?: FailureReason.NotConfigured(vendor.providerId))
        }
        return withContext(ioDispatcher) { sendWithRetry(call, credential, 1) }
    }

    // Recursion depth is bounded by MAX_REQUESTS; the wait is a suspend call, so cancelling the command ends it.
    private suspend fun sendWithRetry(call: ProviderRequest, credential: Credential, number: Int): ModelResult {
        val attempted = attempt(call, credential)
        val kind = if (number == 1) ChatCompletionsAttemptKind.INITIAL else ChatCompletionsAttemptKind.TRANSIENT_RETRY
        notify(ChatCompletionsAttempt(number, kind, attempted.status, attempted.finishReason, attempted.toolCalls))
        val wait = if (number < MAX_REQUESTS) retryWait(attempted) else null
        if (wait == null) return attempted.result
        timing.sleep(wait)
        return sendWithRetry(call, credential, number + 1)
    }

    // The observer is optional diagnostics supplied by the app: whatever it throws must never change the call's outcome
    // (a billed, decoded answer would be lost), and its exception, which could carry any text, is dropped unread. The
    // only function here that catches this broadly, for that one reason; it is not a suspend function, so no
    // cancellation signal can pass through it.
    private fun notify(attempt: ChatCompletionsAttempt) {
        try {
            observer?.onAttempt(attempt)
        } catch (ignored: Exception) {
            // Intentionally empty: see above.
        }
    }

    private fun retryWait(attempted: Attempted): Long? =
        if (attempted.transient) {
            transientWaitMillis(attempted.retryAfterSeconds, timing.retryAfterCapMillis, timing.transientBackoffMillis)
        } else {
            null
        }

    private suspend fun attempt(call: ProviderRequest, credential: Credential): Attempted =
        try {
            interpret(client.newCall(request(call, credential)).await(), call)
        } catch (ignored: InterruptedIOException) {
            // Call and read timeouts surface as this type (SocketTimeoutException is a subclass); no text is read.
            ioFailure(FailureReason.Timeout())
        } catch (ignored: IOException) {
            ioFailure(FailureReason.Network())
        }

    private fun request(call: ProviderRequest, credential: Credential): Request =
        Request.Builder()
            .url(baseUrl.newBuilder().addPathSegments(CHAT_PATH).build())
            .header(HEADER_AUTHORIZATION, BEARER_PREFIX + credential.apiKey)
            .header(HEADER_CONTENT_TYPE, CONTENT_TYPE_JSON)
            .post(OneShotJsonBody(encodeChatRequest(call, vendor)))
            .build()

    private fun interpret(reply: HttpReply, call: ProviderRequest): Attempted {
        val requestId = vendor.requestIdHeader?.let { reply.headers[it] }
        if (reply.isSuccessful) {
            val toolRequired = call.request.toolChoice is ToolChoice.Required
            val decoded = decodeChatResponse(reply.body, requestId, call.model, vendor, toolRequired)
            return Attempted(
                decoded.result,
                reply.code,
                decoded.transient,
                null,
                decoded.finishReason,
                decoded.toolCalls,
            )
        }
        val info = parseChatError(reply.code, requestId, reply.body, vendor.requestIdInBody)
        return Attempted(
            ModelResult.Failure(info.reason(), info.details()),
            reply.code,
            info.transient,
            retryAfterSeconds(reply.headers[HEADER_RETRY_AFTER]),
            null,
            0,
        )
    }

    // A cancelled command can also surface as an IOException ("Canceled"); cancellation must win over a failure.
    // A timeout or a dropped connection may clear on its own, so it is worth the one retry.
    private suspend fun ioFailure(reason: FailureReason): Attempted {
        currentCoroutineContext().ensureActive()
        return Attempted(ModelResult.Failure(reason), null, true, null, null, 0)
    }
}
