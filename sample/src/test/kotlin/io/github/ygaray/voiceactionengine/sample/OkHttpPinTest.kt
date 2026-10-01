package io.github.ygaray.voiceactionengine.sample

import io.github.ygaray.voiceactionengine.sample.net.OkHttpRuntime
import org.junit.Assert.assertEquals
import org.junit.Test

private const val PINNED = "5.2.1"

/** The 4.12-compiled engine must run on the pinned OkHttp 5.2.1, proven at runtime and not at compile time. */
class OkHttpPinTest {

    @Test
    fun theRuntimeOkHttpIs521() {
        assertEquals(PINNED, OkHttpRuntime.version())
    }

    @Test
    fun theReflectiveReadMatchesTheClassField() {
        val field = Class.forName("okhttp3.OkHttp").getField("VERSION").get(null)
        assertEquals(PINNED, field)
    }

    @Test
    fun aMissingClassIsReportedNotThrown() {
        // A loader with only the bootstrap loader as parent cannot see the application classpath, so OkHttp is absent.
        val bare = object : ClassLoader(null) {}
        assertEquals("unknown", OkHttpRuntime.version(bare))
    }
}
