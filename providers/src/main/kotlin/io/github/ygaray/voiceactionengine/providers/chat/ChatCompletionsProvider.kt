package io.github.ygaray.voiceactionengine.providers.chat

import io.github.ygaray.voiceactionengine.core.ProviderId
import io.github.ygaray.voiceactionengine.core.provider.AiProvider
import io.github.ygaray.voiceactionengine.core.provider.ModelCapabilities
import io.github.ygaray.voiceactionengine.core.provider.ModelResult
import io.github.ygaray.voiceactionengine.core.provider.ProviderRequest
import io.github.ygaray.voiceactionengine.providers.http.cleanClient
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

private const val DEFAULT_TIMEOUT_MILLIS = 60_000L
private const val DEFAULT_RETRY_AFTER_CAP_MILLIS = 5_000L
private const val DEFAULT_TRANSIENT_BACKOFF_MILLIS = 500L
private val LOOPBACK_HOSTS = setOf("localhost", "127.0.0.1", "::1")

/**
 * The Chat Completions API, as spoken by OpenAI and by OpenRouter, as an [AiProvider].
 *
 * Build it with [openAi] or [openRouter] and register it on a pipeline: it serves every command routed to
 * [ProviderId.OPENAI] or [ProviderId.OPENROUTER] respectively, one POST per model call to `chat/completions`,
 * authenticated with `Authorization: Bearer <key>` where the key is the one of the credential the router resolved for
 * that call. A credential for another provider is never sent. Tool encoding, strict mode, caching and response decoding
 * follow the model facts carried by each request, so an app overrides them through the pipeline's capability table
 * rather than here.
 *
 * Usage, in prose: build the provider with the builder block, optionally handing it the app's shared `OkHttpClient`,
 * and register it with `commandPipeline { provider(ChatCompletionsProvider.openAi { httpClient = shared }) }`.
 *
 * The provider never logs and never puts a key, transcript, tool argument or response body in an exception or in
 * [toString]. It keeps no per-call state, so one instance serves any number of concurrent commands. The app's
 * `OkHttpClient` is used through a derived copy that shares its connection pool and dispatcher but drops its
 * interceptors, event listener, authenticator, proxy authenticator, cookie jar and redirect following, so nothing the
 * app installed can see the key or the traffic. A proxy that demands a login therefore comes back as a failure.
 *
 * A tool name must use letters, digits, underscore and dash only, at most 64 characters; the vendors reject anything
 * else, and the rejection comes back as an HTTP 400 failure.
 */
public class ChatCompletionsProvider internal constructor(
    internal val transport: ChatTransport,
    private val callTimeoutMillis: Long,
    private val readTimeoutMillis: Long,
) : AiProvider {
    override val id: ProviderId = transport.vendor.providerId

    override fun capabilities(model: String): ModelCapabilities = ChatModels.capabilities(transport.vendor, model)

    override suspend fun complete(call: ProviderRequest): ModelResult = transport.send(call)

    override fun toString(): String =
        "ChatCompletionsProvider(provider=$id, callTimeoutMillis=$callTimeoutMillis, " +
            "readTimeoutMillis=$readTimeoutMillis)"

    /** Settings for [ChatCompletionsProvider]; every field has a working default. */
    public class Builder internal constructor(internal val vendor: ChatVendor) {
        /**
         * The app's client, or null for a fresh one. The provider derives its own copy and never changes this one;
         * sharing it only shares the connection pool and dispatcher.
         */
        public var httpClient: OkHttpClient? = null

        /** Upper bound for one whole HTTP call in milliseconds; must be positive. Default 60 000. */
        public var callTimeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS

        /** Upper bound for waiting on the server between reads in milliseconds; must be positive. Default 60 000. */
        public var readTimeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS

        /**
         * Hears about every HTTP attempt of every call, or null for none. It is called on the provider's I/O dispatcher
         * after the status is known, with a null status when the attempt had no HTTP answer, and receives ids and
         * counts only: no text, headers or body. It must return quickly; an exception it throws is ignored and never
         * changes the call's result.
         */
        public var attemptObserver: ChatCompletionsAttemptObserver? = null

        internal var baseUrl: HttpUrl = vendor.productionBaseUrl.toHttpUrl()

        internal var ioDispatcher: CoroutineDispatcher = Dispatchers.IO

        // The wait before the one transient retry; a suspend call, so cancelling the command ends it.
        internal var sleep: suspend (Long) -> Unit = { delay(it) }

        internal var retryAfterCapMillis: Long = DEFAULT_RETRY_AFTER_CAP_MILLIS

        internal var transientBackoffMillis: Long = DEFAULT_TRANSIENT_BACKOFF_MILLIS

        internal fun build(): ChatCompletionsProvider {
            require(callTimeoutMillis > 0) { "callTimeoutMillis must be positive" }
            require(readTimeoutMillis > 0) { "readTimeoutMillis must be positive" }
            require(retryAfterCapMillis >= 0) { "retryAfterCapMillis must not be negative" }
            require(transientBackoffMillis >= 0) { "transientBackoffMillis must not be negative" }
            require(baseUrl.isHttps || baseUrl.host in LOOPBACK_HOSTS) { "the base URL must use https" }
            val client = cleanClient(httpClient, callTimeoutMillis, readTimeoutMillis)
            val timing = ChatRetryTiming(sleep, retryAfterCapMillis, transientBackoffMillis)
            val transport = ChatTransport(client, baseUrl, vendor, ioDispatcher, timing, attemptObserver)
            return ChatCompletionsProvider(transport, callTimeoutMillis, readTimeoutMillis)
        }
    }

    public companion object {
        /**
         * Builds the provider for OpenAI's own endpoint from [block]; throws [IllegalArgumentException] naming a
         * timeout that is not positive.
         */
        public fun openAi(block: Builder.() -> Unit): ChatCompletionsProvider =
            Builder(ChatVendor.OPENAI).apply(block).build()

        /**
         * Builds the provider for OpenRouter from [block]; throws [IllegalArgumentException] naming a timeout that is
         * not positive.
         */
        public fun openRouter(block: Builder.() -> Unit): ChatCompletionsProvider =
            Builder(ChatVendor.OPENROUTER).apply(block).build()
    }
}
