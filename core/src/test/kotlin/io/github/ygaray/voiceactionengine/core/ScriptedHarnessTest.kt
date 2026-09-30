package io.github.ygaray.voiceactionengine.core

import io.github.ygaray.voiceactionengine.core.testing.NoNetworkGuard
import io.github.ygaray.voiceactionengine.core.testing.RecordingSink
import io.github.ygaray.voiceactionengine.core.testing.ScriptedResponses
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.ProxySelector
import java.net.URI
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class ScriptedHarnessTest {

    /** Test-local stand-in for a tiered pipeline; deliberately not promoted to main (no placeholder types ship). */
    private class StandInPipeline(
        private val replies: ScriptedResponses<String>,
        private val sink: RecordingSink<String>,
    ) {
        suspend fun run(input: String): String {
            sink.record("in:$input")
            val reply = replies.next()
            sink.record("out:$reply")
            return reply
        }
    }

    @Test
    fun scriptedResponsesReturnInOrderAndCountDown() {
        val script = ScriptedResponses(listOf("a", "b", "c"))
        assertEquals(THREE, script.remaining)
        assertEquals("a", script.next())
        assertEquals("b", script.next())
        assertEquals(1, script.remaining)
        assertEquals("c", script.next())
        assertEquals(0, script.remaining)
    }

    @Test
    fun exhaustedScriptFailsLoudly() {
        val script = ScriptedResponses(listOf("only"))
        script.next()
        val failure = assertThrows(IllegalStateException::class.java) { script.next() }
        assertTrue(failure.message.orEmpty().contains("script exhausted"))
    }

    @Test
    fun recordingSinkKeepsOrderSnapshotsAndClears() {
        val sink = RecordingSink<String>()
        sink.record("one")
        sink.record("two")
        val snapshot = sink.events
        assertEquals(listOf("one", "two"), snapshot)
        sink.record("three")
        assertEquals("snapshot must not see later events", 2, snapshot.size)
        sink.clear()
        assertTrue(sink.events.isEmpty())
    }

    @Test
    fun recordingSinkLosesNothingUnderConcurrency() {
        val sink = RecordingSink<Int>()
        val pool = Executors.newFixedThreadPool(THREADS)
        val done = CountDownLatch(THREADS)
        try {
            repeat(THREADS) { t ->
                pool.execute {
                    repeat(PER_THREAD) { sink.record(t * PER_THREAD + it) }
                    done.countDown()
                }
            }
            assertTrue(done.await(TIMEOUT_SECONDS, TimeUnit.SECONDS))
        } finally {
            pool.shutdownNow()
        }
        assertEquals(THREADS * PER_THREAD, sink.events.size)
        assertEquals(THREADS * PER_THREAD, sink.events.toSet().size)
    }

    @Test
    fun noHttpStackOnCoreClasspath() {
        NoNetworkGuard.assertNoHttpStackOnClasspath()
    }

    @Test
    fun duringInstallsTripwireAndRestoresSelectorEvenOnThrow() {
        val before = ProxySelector.getDefault()
        val tripped = assertThrows(AssertionError::class.java) {
            NoNetworkGuard.during {
                ProxySelector.getDefault().select(URI.create("http://example.invalid/"))
            }
        }
        assertTrue(tripped.message.orEmpty().contains("network attempt"))
        assertSame(before, ProxySelector.getDefault())

        assertThrows(IllegalArgumentException::class.java) {
            NoNetworkGuard.during { throw IllegalArgumentException("boom") }
        }
        assertSame(before, ProxySelector.getDefault())
    }

    @Test
    fun standInPipelineWalksScriptedTiersWithNoNetwork() = runTest {
        val sink = RecordingSink<String>()
        val pipeline = StandInPipeline(ScriptedResponses(listOf("tier1", "tier2")), sink)
        NoNetworkGuard.during {
            pipeline.run("hello")
            pipeline.run("again")
        }
        assertEquals(listOf("in:hello", "out:tier1", "in:again", "out:tier2"), sink.events)
    }

    private companion object {
        const val THREE = 3
        const val THREADS = 8
        const val PER_THREAD = 500
        const val TIMEOUT_SECONDS = 30L
    }
}
