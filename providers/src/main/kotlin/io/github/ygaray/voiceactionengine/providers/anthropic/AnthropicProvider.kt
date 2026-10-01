package io.github.ygaray.voiceactionengine.providers.anthropic

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

private const val PRODUCTION_BASE_URL = "https://api.anthropic.com/"
private const val DEFAULT_TIMEOUT_MILLIS = 60_000L
private const val DEFAULT_RETRY_AFTER_CAP_MILLIS = 5_000L
private const val DEFAULT_TRANSIENT_BACKOFF_MILLIS = 500L
private val LOOPBACK_HOSTS = setOf("localhost", "127.0.0.1", "::1")

/**
 * The Anthropic Messages API as an [AiProvider].
 *
 * Register it on a pipeline and it serves every command routed to [ProviderId.ANTHROPIC]: one POST per model call,
 * authenticated with the key of the credential the router resolved for that call. Prompt-cache breakpoints, tool
 * encoding and response decoding follow the model facts carried by each request, so an app overrides them through the
 * pipeline's capability table rather than here.
 *
 * Usage, in prose: build the provider with the builder block, optionally handing it the app's shared
 * `OkHttpClient`, and register it with `commandPipeline { provider(AnthropicProvider { httpClient = shared }) }`.
 *
 * The provider never logs and never puts a key, transcript, tool argument or response body in an exception or in
 * [toString]. It keeps no per-call state, so one instance serves any number of concurrent commands. The app's
 * `OkHttpClient` is used through a derived copy that shares its connection pool and dispatcher but drops its
 * interceptors, event listener, authenticator, cookie jar and redirect following, so nothing the app installed can see
 * the key or the traffic.
 */
public class AnthropicProvider internal constructor(
    internal val transport: AnthropicTransport,
    private val callTimeoutMillis: Long,
    private val readTimeoutMillis: Long,
) : AiProvider {
    override val id: ProviderId = ProviderId.ANTHROPIC

    override fun capabilities(model: String): ModelCapabilities = AnthropicModels.capabilities(model)

    override suspend fun complete(call: ProviderRequest): ModelResult = transport.send(call)

    override fun toString(): String =
        "AnthropicProvider(callTimeoutMillis=$callTimeoutMillis, readTimeoutMillis=$readTimeoutMillis)"

    /** Settings for [AnthropicProvider]; every field has a working default. */
    public class Builder internal constructor() {
        /**
         * The app's client, or null for a fresh one. The provider derives its own copy and never changes this one;
         * sharing it only shares the connection pool and dispatcher.
         */
        public var httpClient: OkHttpClient? = null

        /** Upper bound for one whole HTTP call in milliseconds; must be positive. Default 60 000. */
        public var callTimeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS

        /** Upper bound for waiting on the server between reads in milliseconds; must be positive. Default 60 000. */
        public var readTimeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS

        internal var baseUrl: HttpUrl = PRODUCTION_BASE_URL.toHttpUrl()

        internal var ioDispatcher: CoroutineDispatcher = Dispatchers.IO

        // The wait before the one transient retry; a suspend call, so cancelling the command ends it.
        internal var sleep: suspend (Long) -> Unit = { delay(it) }

        internal var retryAfterCapMillis: Long = DEFAULT_RETRY_AFTER_CAP_MILLIS

        internal var transientBackoffMillis: Long = DEFAULT_TRANSIENT_BACKOFF_MILLIS

        internal fun build(): AnthropicProvider {
            require(callTimeoutMillis > 0) { "callTimeoutMillis must be positive" }
            require(readTimeoutMillis > 0) { "readTimeoutMillis must be positive" }
            require(retryAfterCapMillis >= 0) { "retryAfterCapMillis must not be negative" }
            require(transientBackoffMillis >= 0) { "transientBackoffMillis must not be negative" }
            require(baseUrl.isHttps || baseUrl.host in LOOPBACK_HOSTS) { "the base URL must use https" }
            val client = cleanClient(httpClient, callTimeoutMillis, readTimeoutMillis)
            val transport = AnthropicTransport(
                client,
                baseUrl,
                ioDispatcher,
                sleep,
                retryAfterCapMillis,
                transientBackoffMillis,
            )
            return AnthropicProvider(transport, callTimeoutMillis, readTimeoutMillis)
        }
    }

    public companion object {
        /** Builds a provider from [block]; throws [IllegalArgumentException] naming a timeout that is not positive. */
        public operator fun invoke(block: Builder.() -> Unit): AnthropicProvider = Builder().apply(block).build()
    }
}
