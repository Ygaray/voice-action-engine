package io.github.ygaray.voiceactionengine.providers.http

import okhttp3.Authenticator
import okhttp3.CookieJar
import okhttp3.EventListener
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

private const val CONNECT_TIMEOUT_MILLIS = 10_000L

/**
 * Derives the client every provider call goes through from the app's client (or a fresh one when the app gave none).
 *
 * The derived client shares the app's connection pool and dispatcher but carries none of its hooks, because the API key
 * travels in a request header and every hook below would get to see it:
 * - application and network interceptors are cleared (they see and can rewrite the headers);
 * - the event listener is replaced by the no-op one (it observes the call and its connections);
 * - the authenticator is replaced by the no-op one (it can re-send the request with other credentials);
 * - the proxy authenticator is replaced by the no-op one too (it receives the 407 response, whose request carries the
 *   key header), so a proxy that demands a login is reported as a failure rather than answered;
 * - the cookie jar stores nothing;
 * - redirects are not followed, because OkHttp strips only `Authorization` on a cross-host redirect and a custom key
 *   header would be forwarded to the other host.
 *
 * The app's own client is never mutated. `retryOnConnectionFailure` stays as the app set it: the one-shot request body,
 * not that flag, is what stops a replay after the bytes were sent, and the flag keeps pre-send route failover working.
 */
internal fun cleanClient(app: OkHttpClient?, callTimeoutMillis: Long, readTimeoutMillis: Long): OkHttpClient {
    val builder = (app ?: OkHttpClient()).newBuilder()
    builder.interceptors().clear()
    builder.networkInterceptors().clear()
    return builder
        .eventListener(EventListener.NONE)
        .authenticator(Authenticator.NONE)
        .proxyAuthenticator(Authenticator.NONE)
        .cookieJar(CookieJar.NO_COOKIES)
        .followRedirects(false)
        .followSslRedirects(false)
        .connectTimeout(CONNECT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
        .readTimeout(readTimeoutMillis, TimeUnit.MILLISECONDS)
        .callTimeout(callTimeoutMillis, TimeUnit.MILLISECONDS)
        .build()
}
