package io.github.ygaray.voiceactionengine.providers.http

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import okio.ForwardingSource
import okio.Source
import okio.Timeout
import okio.buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.system.measureNanoTime

class CallAwaitTest {

    @Test
    fun cancellingTheAwaiterCancelsTheCallExactlyOnce() = runBlocking {
        val call = FakeCall()
        val job = launch(start = CoroutineStart.UNDISPATCHED) { call.await() }
        assertNotNull(call.callback)
        assertEquals(0, call.cancelCount.get())

        job.cancelAndJoin()

        assertEquals(1, call.cancelCount.get())
    }

    @Test
    fun lateResponseAfterCancellationIsClosedUnreadAndResumesNothing() = runBlocking {
        val call = FakeCall()
        val job = launch(start = CoroutineStart.UNDISPATCHED) { call.await() }
        job.cancelAndJoin()
        val source = TrackedSource("late")

        call.callback!!.onResponse(call, call.responseWith(source))

        assertTrue(source.closed)
        assertEquals(0, source.readCalls)
        assertTrue(job.isCancelled)
    }

    @Test
    fun normalResponseResumesWithTheReadBodyAndClosesIt() = runBlocking {
        val call = FakeCall()
        val reply = async(start = CoroutineStart.UNDISPATCHED) { call.await() }
        val source = TrackedSource("hello")

        call.callback!!.onResponse(call, call.responseWith(source))

        val result = reply.await()
        assertEquals(200, result.code)
        assertEquals("hello", result.body)
        assertTrue(result.isSuccessful)
        assertEquals("HttpReply(code=200)", result.toString())
        assertTrue(source.closed)
    }

    @Test
    fun responseWithoutBodyResumesWithNullBody() = runBlocking {
        val call = FakeCall()
        val reply = async(start = CoroutineStart.UNDISPATCHED) { call.await() }

        call.callback!!.onResponse(call, call.responseWith(null))

        val result = reply.await()
        assertEquals(200, result.code)
        // OkHttp 4.12 hands back a null body here; later versions hand back an empty one. Both mean "no text".
        assertTrue(result.body.isNullOrEmpty())
    }

    @Test
    fun failureResumesTheAwaiterAndIsIgnoredAfterCancellation() = runBlocking {
        val failing = FakeCall()
        val outcome = async(start = CoroutineStart.UNDISPATCHED) {
            try {
                failing.await()
                null
            } catch (e: IOException) {
                e
            }
        }
        val failure = IOException("boom")

        failing.callback!!.onFailure(failing, failure)

        // Coroutine stack-trace recovery may hand back a copy of the exception, so compare type and message.
        val received = outcome.await()
        assertNotNull(received)
        assertEquals(failure.javaClass, received!!.javaClass)
        assertEquals("boom", received.message)

        val cancelled = FakeCall()
        val job = launch(start = CoroutineStart.UNDISPATCHED) { cancelled.await() }
        job.cancelAndJoin()

        cancelled.callback!!.onFailure(cancelled, IOException("late"))

        assertTrue(job.isCancelled)
    }

    @Test
    fun cancellingAgainstARealSlowServerStopsTheCallPromptly() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("slow").setHeadersDelay(SLOW_SECONDS, TimeUnit.SECONDS))
            server.start()
            val client = OkHttpClient()
            val call = client.newCall(Request.Builder().url(server.url("/")).build())
            val job = launch(start = CoroutineStart.UNDISPATCHED) { call.await() }
            assertNotNull(server.takeRequest(TAKE_SECONDS, TimeUnit.SECONDS))

            val cancelNanos = measureNanoTime { job.cancelAndJoin() }

            assertTrue("cancel took ${cancelNanos / NANOS_PER_MILLI} ms", cancelNanos < ONE_SECOND_NANOS)
            assertTrue(call.isCanceled())
            var waitedMillis = 0L
            while (client.dispatcher.runningCallsCount() > 0 && waitedMillis < ONE_SECOND_MILLIS) {
                delay(POLL_MILLIS)
                waitedMillis += POLL_MILLIS
            }
            assertEquals(0, client.dispatcher.runningCallsCount())
            assertFalse(job.isActive)
        }
    }

    private class TrackedSource(text: String) : ForwardingSource(Buffer().writeUtf8(text)) {
        var closed = false
        var readCalls = 0

        override fun read(sink: Buffer, byteCount: Long): Long {
            readCalls++
            return super.read(sink, byteCount)
        }

        override fun close() {
            closed = true
            super.close()
        }
    }

    /** Implements only the members the 4.12 floor declares; nothing else is ever invoked by `await`. */
    private class FakeCall : Call {
        private val request = Request.Builder().url("http://localhost/").build()
        var callback: Callback? = null
        val cancelCount = AtomicInteger()

        fun responseWith(source: Source?): Response {
            val builder = Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
            if (source != null) builder.body(source.buffer().asResponseBody(null, -1L))
            return builder.build()
        }

        override fun request(): Request = request

        override fun execute(): Response = throw UnsupportedOperationException()

        override fun enqueue(responseCallback: Callback) {
            callback = responseCallback
        }

        override fun cancel() {
            cancelCount.incrementAndGet()
        }

        override fun isExecuted(): Boolean = callback != null

        override fun isCanceled(): Boolean = cancelCount.get() > 0

        override fun timeout(): Timeout = Timeout.NONE

        override fun clone(): Call = FakeCall()
    }

    private companion object {
        const val SLOW_SECONDS = 5L
        const val TAKE_SECONDS = 5L
        const val POLL_MILLIS = 25L
        const val ONE_SECOND_MILLIS = 1_000L
        const val NANOS_PER_MILLI = 1_000_000L
        const val ONE_SECOND_NANOS = 1_000_000_000L
    }
}
