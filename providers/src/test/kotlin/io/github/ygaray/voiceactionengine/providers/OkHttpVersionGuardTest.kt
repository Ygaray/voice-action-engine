package io.github.ygaray.voiceactionengine.providers

import io.github.ygaray.voiceactionengine.core.testing.RecordingSink
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Test

class OkHttpVersionGuardTest {

    @Test
    fun runtimeVersionMatchesLeg() {
        val expected = System.getProperty("expected.okhttp") ?: FLOOR_VERSION
        // Reflective on purpose: OkHttp.VERSION is a compile-time constant, so a direct read would be inlined as
        // the 4.12.0 these classes were compiled against and report the floor on every leg.
        val actual = Class.forName("okhttp3.OkHttp").getField("VERSION").get(null) as String
        val jar = OkHttpClient::class.java.protectionDomain.codeSource.location
        println("OKHTTP_RUNTIME=$actual expected=$expected jar=$jar")
        assertEquals(expected, actual)
    }

    @Test
    fun trivialCallRoundTrips() {
        val sink = RecordingSink<String>()
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("hi"))
            server.start()
            val call = OkHttpClient().newCall(Request.Builder().url(server.url("/")).build())
            call.execute().use { response ->
                // body?.string() is the form that compiles at the 4.12 floor; 5.x made body non-null.
                sink.record(response.body?.string().orEmpty())
            }
        }
        assertEquals(listOf("hi"), sink.events)
    }

    private companion object {
        const val FLOOR_VERSION = "4.12.0"
    }
}
