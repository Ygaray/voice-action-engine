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
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

private const val CHAT_PATH = "chat/completions"
private const val HEADER_AUTHORIZATION = "Authorization"
private const val HEADER_CONTENT_TYPE = "content-type"
private const val CONTENT_TYPE_JSON = "application/json"
private const val BEARER_PREFIX = "Bearer "

// No usable credential means no request. A key OkHttp would refuse as a header value (a pasted trailing newline, a
// non-ASCII character) is a wrong key: it is answered as Auth here, because OkHttp's own refusal quotes the whole
// value of the header in its message.
private fun refusalFor(vendor: ChatVendor, credential: Credential?): FailureReason? = when {
    credential == null || credential.provider != vendor.providerId -> FailureReason.NotConfigured(vendor.providerId)
    !isHeaderSafe(credential.apiKey) -> FailureReason.Auth()
    else -> null
}

/**
 * One logical call against a Chat Completions endpoint: vet the key, encode, POST, await, decode or map the error. It
 * holds no per-call state, so concurrent calls are independent.
 */
internal class ChatTransport(
    val client: OkHttpClient,
    val baseUrl: HttpUrl,
    val vendor: ChatVendor,
    private val ioDispatcher: CoroutineDispatcher,
) {
    suspend fun send(call: ProviderRequest): ModelResult {
        val credential = call.credential
        val refusal = refusalFor(vendor, credential)
        if (refusal != null || credential == null) {
            return ModelResult.Failure(refusal ?: FailureReason.NotConfigured(vendor.providerId))
        }
        return withContext(ioDispatcher) { interpret(client.newCall(request(call, credential)).await(), call) }
    }

    private fun request(call: ProviderRequest, credential: Credential): Request =
        Request.Builder()
            .url(baseUrl.newBuilder().addPathSegments(CHAT_PATH).build())
            .header(HEADER_AUTHORIZATION, BEARER_PREFIX + credential.apiKey)
            .header(HEADER_CONTENT_TYPE, CONTENT_TYPE_JSON)
            .post(OneShotJsonBody(encodeChatRequest(call, vendor)))
            .build()

    private fun interpret(reply: HttpReply, call: ProviderRequest): ModelResult {
        val requestId = vendor.requestIdHeader?.let { reply.headers[it] }
        if (reply.isSuccessful) {
            val toolRequired = call.request.toolChoice is ToolChoice.Required
            return decodeChatResponse(reply.body, requestId, call.model, vendor, toolRequired).result
        }
        val info = parseChatError(reply.code, requestId, reply.body, vendor.requestIdInBody)
        return ModelResult.Failure(info.reason(), info.details())
    }
}
