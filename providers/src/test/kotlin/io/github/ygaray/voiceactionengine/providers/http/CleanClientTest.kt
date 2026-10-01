package io.github.ygaray.voiceactionengine.providers.http

import kotlinx.coroutines.runBlocking
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
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
}
