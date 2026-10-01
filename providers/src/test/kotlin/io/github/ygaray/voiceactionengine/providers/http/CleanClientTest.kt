package io.github.ygaray.voiceactionengine.providers.http

import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.atomic.AtomicInteger

class CleanClientTest {

    @Test
    fun oneShotPostThroughDerivedClientNeverTouchesAppHooks() = runBlocking {
        val interceptorCalls = AtomicInteger()
        val networkInterceptorCalls = AtomicInteger()
        val listenerCalls = AtomicInteger()
        val app = OkHttpClient.Builder()
            .addInterceptor { chain ->
                interceptorCalls.incrementAndGet()
                chain.proceed(chain.request().newBuilder().header("x-app-hook", "1").build())
            }
            .addNetworkInterceptor { chain ->
                networkInterceptorCalls.incrementAndGet()
                chain.proceed(chain.request())
            }
            .eventListener(
                object : EventListener() {
                    override fun callStart(call: Call) {
                        listenerCalls.incrementAndGet()
                    }
                },
            )
            .build()
        val derived = cleanClient(app, 60_000, 60_000)

        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("ok"))
            server.start()
            val request = Request.Builder()
                .url(server.url("/v1/messages"))
                .post(OneShotJsonBody("{\"a\":1}".toByteArray()))
                .build()

            val reply = derived.newCall(request).await()

            assertEquals(200, reply.code)
            assertEquals("ok", reply.body)
            assertTrue(reply.isSuccessful)
            val seen = server.takeRequest()
            assertEquals("POST", seen.method)
            assertNull(seen.getHeader("x-app-hook"))
            assertEquals("{\"a\":1}", seen.body.readUtf8())
        }

        assertEquals(0, interceptorCalls.get())
        assertEquals(0, networkInterceptorCalls.get())
        assertEquals(0, listenerCalls.get())
        assertSame(app.connectionPool, derived.connectionPool)
        assertSame(app.dispatcher, derived.dispatcher)
        assertEquals(1, app.interceptors.size)
        assertEquals(60_000L, derived.callTimeoutMillis.toLong())
        assertEquals(60_000L, derived.readTimeoutMillis.toLong())
        assertEquals(10_000L, derived.connectTimeoutMillis.toLong())
    }

    @Test
    fun redirectsAreReturnedUnfollowedAndTheOtherHostSeesNothing() = runBlocking {
        val app = OkHttpClient.Builder().followRedirects(true).followSslRedirects(true).build()
        val derived = cleanClient(app, 60_000, 60_000)
        listOf(301, 302, 303, 307, 308).forEach { status ->
            MockWebServer().use { a ->
                MockWebServer().use { b ->
                    b.start()
                    a.enqueue(MockResponse().setResponseCode(status).setHeader("Location", b.url("/stolen").toString()))
                    a.start()
                    val request = Request.Builder()
                        .url(a.url("/v1/messages"))
                        .header("x-api-key", "sk-test")
                        .post(OneShotJsonBody("{}".toByteArray()))
                        .build()

                    val reply = derived.newCall(request).await()

                    assertEquals(status, reply.code)
                    assertEquals("redirect $status reached the other host", 0, b.requestCount)
                    assertEquals(1, a.requestCount)
                }
            }
        }
    }

    @Test
    fun timeoutsComeFromTheArguments() {
        val withApp = cleanClient(OkHttpClient(), 1_234, 5_678)
        assertEquals(1_234L, withApp.callTimeoutMillis.toLong())
        assertEquals(5_678L, withApp.readTimeoutMillis.toLong())
        assertEquals(10_000L, withApp.connectTimeoutMillis.toLong())

        val withoutApp = cleanClient(null, 1_234, 5_678)
        assertEquals(1_234L, withoutApp.callTimeoutMillis.toLong())
        assertEquals(5_678L, withoutApp.readTimeoutMillis.toLong())
        assertFalse(withoutApp.followRedirects)
        assertFalse(withoutApp.followSslRedirects)
    }

    @Test
    fun appAuthenticatorNeverRunsForA401() = runBlocking {
        val authenticatorCalls = AtomicInteger()
        val app = OkHttpClient.Builder()
            .authenticator(
                Authenticator { _, _ ->
                    authenticatorCalls.incrementAndGet()
                    null
                },
            )
            .build()
        val derived = cleanClient(app, 60_000, 60_000)

        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(401).setBody("no"))
            server.start()

            val reply = derived.newCall(Request.Builder().url(server.url("/")).build()).await()

            assertEquals(401, reply.code)
            assertFalse(reply.isSuccessful)
            assertEquals(0, authenticatorCalls.get())
            assertEquals(1, server.requestCount)
        }
    }

    @Test
    fun appProxyAuthenticatorNeverRunsForA407() = runBlocking {
        val proxyAuthenticatorCalls = AtomicInteger()
        MockWebServer().use { proxy ->
            proxy.enqueue(MockResponse().setResponseCode(407).setHeader("Proxy-Authenticate", "Basic realm=\"p\""))
            proxy.start()
            val app = OkHttpClient.Builder()
                .proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress(proxy.hostName, proxy.port)))
                .proxyAuthenticator(
                    Authenticator { _, response ->
                        proxyAuthenticatorCalls.incrementAndGet()
                        // What a real one would do: re-send the request, which carries the API key header.
                        response.request.newBuilder().header("Proxy-Authorization", "Basic abc").build()
                    },
                )
                .build()
            val derived = cleanClient(app, 60_000, 60_000)

            val reply = derived.newCall(
                Request.Builder().url("http://api.invalid/v1/messages").header("x-api-key", "sk-canary").build(),
            ).await()

            assertEquals(407, reply.code)
            assertEquals(0, proxyAuthenticatorCalls.get())
            assertEquals(1, proxy.requestCount)
        }
    }
}
