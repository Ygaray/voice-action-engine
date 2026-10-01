package io.github.ygaray.voiceactionengine.providers.anthropic

import io.github.ygaray.voiceactionengine.core.Credential
import io.github.ygaray.voiceactionengine.core.ProviderId
import io.github.ygaray.voiceactionengine.core.failure.FailureReason
import io.github.ygaray.voiceactionengine.core.provider.ModelResult
import io.github.ygaray.voiceactionengine.core.provider.ProviderRequest
import io.github.ygaray.voiceactionengine.providers.http.HttpReply
import io.github.ygaray.voiceactionengine.providers.http.OneShotJsonBody
import io.github.ygaray.voiceactionengine.providers.http.await
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

private const val MESSAGES_PATH = "v1/messages"
private const val API_VERSION = "2023-06-01"
private const val HEADER_API_KEY = "x-api-key"
private const val HEADER_VERSION = "anthropic-version"
private const val HEADER_CONTENT_TYPE = "content-type"
private const val HEADER_REQUEST_ID = "request-id"
private const val CONTENT_TYPE_JSON = "application/json"

/**
 * One attempt against the Messages endpoint: encode, POST, await, decode. It holds no per-call state, so concurrent
 * calls are independent.
 */
internal class AnthropicTransport(
    val client: OkHttpClient,
    val baseUrl: HttpUrl,
    private val ioDispatcher: CoroutineDispatcher,
) {
    suspend fun send(call: ProviderRequest): ModelResult {
        val credential = call.credential
        if (credential == null || credential.provider != ProviderId.ANTHROPIC) {
            return ModelResult.Failure(FailureReason.NotConfigured(ProviderId.ANTHROPIC))
        }
        return withContext(ioDispatcher) { attempt(call, credential) }
    }

    private suspend fun attempt(call: ProviderRequest, credential: Credential): ModelResult {
        val request = Request.Builder()
            .url(baseUrl.newBuilder().addPathSegments(MESSAGES_PATH).build())
            .header(HEADER_API_KEY, credential.apiKey)
            .header(HEADER_VERSION, API_VERSION)
            .header(HEADER_CONTENT_TYPE, CONTENT_TYPE_JSON)
            .post(OneShotJsonBody(encodeAnthropicRequest(call)))
            .build()
        return interpret(client.newCall(request).await(), call.model)
    }

    private fun interpret(reply: HttpReply, model: String): ModelResult {
        val requestId = reply.headers[HEADER_REQUEST_ID]
        if (reply.isSuccessful) return decodeAnthropicResponse(reply.body, requestId, model)
        val info = parseAnthropicError(reply.code, requestId, reply.body)
        return ModelResult.Failure(info.reason(), info.details())
    }
}
