package io.github.ygaray.voiceactionengine.providers.chat

import io.github.ygaray.voiceactionengine.core.ProviderId

private const val OPENAI_BASE_URL = "https://api.openai.com/v1/"
private const val OPENROUTER_BASE_URL = "https://openrouter.ai/api/v1/"
private const val OPENAI_REQUEST_ID_HEADER = "x-request-id"

/**
 * Everything that differs between the vendors speaking the Chat Completions dialect, as plain values. A new vendor is
 * one more instance with different values, not a subclass: the encoder, decoder and transport read these fields and
 * never ask which vendor they have.
 *
 * Base URLs end with a slash because the transport appends `chat/completions` to them.
 *
 * @property providerId the identity reported on the provider and in failures.
 * @property productionBaseUrl where requests go unless the app injects another base URL.
 * @property requestIdHeader the response header carrying the vendor's request id, or null when it sends none.
 * @property requestIdInBody true when the request id is the `id` field of the response body instead.
 * @property routedModelIds true when model ids are `vendor/model` strings the vendor routes onward.
 * @property requireParametersOnForced true when a forced tool call also asks the router to pick only endpoints that
 * honour every parameter sent.
 * @property parallelToolCallsFalseOnForced true when a forced tool call also switches parallel tool calls off.
 */
internal class ChatVendor(
    val providerId: ProviderId,
    val productionBaseUrl: String,
    val requestIdHeader: String?,
    val requestIdInBody: Boolean,
    val routedModelIds: Boolean,
    val requireParametersOnForced: Boolean,
    val parallelToolCallsFalseOnForced: Boolean,
) {
    override fun toString(): String = "ChatVendor($providerId)"

    companion object {
        /** OpenAI's own endpoint: ids are plain model names and the request id comes from a header. */
        val OPENAI: ChatVendor = ChatVendor(
            providerId = ProviderId.OPENAI,
            productionBaseUrl = OPENAI_BASE_URL,
            requestIdHeader = OPENAI_REQUEST_ID_HEADER,
            requestIdInBody = false,
            routedModelIds = false,
            requireParametersOnForced = false,
            parallelToolCallsFalseOnForced = true,
        )

        /** OpenRouter: ids are routed as `vendor/model` and the request id is the body `id`. */
        val OPENROUTER: ChatVendor = ChatVendor(
            providerId = ProviderId.OPENROUTER,
            productionBaseUrl = OPENROUTER_BASE_URL,
            requestIdHeader = null,
            requestIdInBody = true,
            routedModelIds = true,
            requireParametersOnForced = true,
            parallelToolCallsFalseOnForced = false,
        )
    }
}
