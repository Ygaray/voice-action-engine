package io.github.ygaray.voiceactionengine.core

import io.github.ygaray.voiceactionengine.core.internal.EngineFault
import io.github.ygaray.voiceactionengine.core.internal.guarded
import io.github.ygaray.voiceactionengine.core.internal.guardedUncancellable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class GuardedTest {

    private val faults = mutableListOf<EngineFault>()

    private fun onFault(fault: EngineFault): String {
        faults.add(fault)
        return FAULT
    }

    @Test
    fun ordinaryExceptionBecomesAClassNamedFault() = runTest {
        val result = guarded<String>(::onFault) { throw IllegalStateException("secret message") }
        assertEquals(FAULT, result)
        assertEquals("IllegalStateException", faults.single().errorClass)
        assertFalse(faults.single().timeoutLeak)
    }

    @Test
    fun linkageErrorBecomesAFault() = runTest {
        val result = guarded<String>(::onFault) { throw NoSuchMethodError("okhttp3.Foo.bar") }
        assertEquals(FAULT, result)
        assertEquals("NoSuchMethodError", faults.single().errorClass)
        assertFalse(faults.single().timeoutLeak)
    }

    @Test
    fun leakedInnerTimeoutWhileActiveIsATimeoutFault() = runTest {
        val result = guarded<String>(::onFault) { withTimeout(1) { awaitCancellation() } }
        assertEquals(FAULT, result)
        assertEquals("TimeoutCancellationException", faults.single().errorClass)
        assertTrue(faults.single().timeoutLeak)
    }

    @Test
    fun outerTimeoutPropagatesAndNeverReachesOnFault() = runTest {
        val result = withTimeoutOrNull(OUTER_TIMEOUT_MS) {
            guarded<String>(::onFault) {
                delay(LONG_DELAY_MS)
                "finished"
            }
        }
        assertNull(result)
        assertTrue(faults.isEmpty())
    }

    @Test
    fun cancellingTheJobPropagatesAndNeverReachesOnFault() = runTest {
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            guarded<String>(::onFault) { awaitCancellation() }
        }
        job.cancel()
        job.join()
        assertTrue(job.isCancelled)
        assertTrue(faults.isEmpty())
    }

    @Test
    fun plainCancellationExceptionWhileActiveIsAFault() = runTest {
        val result = guarded<String>(::onFault) { throw CancellationException("foreign") }
        assertEquals(FAULT, result)
        assertEquals("CancellationException", faults.single().errorClass)
        assertFalse(faults.single().timeoutLeak)
    }

    @Test
    fun plainCancellationExceptionWhileTheCallerIsCancelledPropagates() = runTest {
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            guarded<String>(::onFault) {
                currentCoroutineContext().cancel()
                throw CancellationException("x")
            }
        }
        job.join()
        assertTrue(job.isCancelled)
        assertTrue(faults.isEmpty())
    }

    @Test
    fun uncancellableVariantTurnsAForeignCancellationIntoAFault() = runTest {
        val result = guardedUncancellable<String>(::onFault) { throw CancellationException("foreign") }
        assertEquals(FAULT, result)
        assertEquals("CancellationException", faults.single().errorClass)
        assertFalse(faults.single().timeoutLeak)
    }

    @Test
    fun uncancellableVariantTurnsALeakedTimeoutIntoAFault() = runTest {
        val result = guardedUncancellable<String>(::onFault) { withTimeout(1) { awaitCancellation() } }
        assertEquals(FAULT, result)
        assertTrue(faults.single().timeoutLeak)
    }

    @Test
    fun uncancellableVariantStillPassesValuesAndOrdinaryFaultsLikeGuarded() = runTest {
        assertEquals("value", guardedUncancellable<String>(::onFault) { "value" })
        assertTrue(faults.isEmpty())
        assertEquals(FAULT, guardedUncancellable<String>(::onFault) { throw IllegalStateException("x") })
        assertEquals("IllegalStateException", faults.single().errorClass)
    }

    @Test
    fun assertionErrorIsNotCaught() = runTest {
        try {
            guarded<String>(::onFault) { throw AssertionError("boom") }
            fail("expected AssertionError")
        } catch (e: AssertionError) {
            assertEquals("boom", e.message)
        }
        assertTrue(faults.isEmpty())
    }

    @Test
    fun normalReturnPassesTheValueThroughWithoutFault() = runTest {
        val result = guarded<String>(::onFault) {
            delay(1)
            "value"
        }
        assertEquals("value", result)
        assertTrue(faults.isEmpty())
    }

    @Test
    fun anonymousExceptionClassFallsBackToTheJvmNameSegment() = runTest {
        guarded<String>(::onFault) { throw object : RuntimeException() {} }
        val name = faults.single().errorClass
        assertTrue(name, name.isNotEmpty())
        assertTrue(name, name.startsWith("GuardedTest"))
    }

    private companion object {
        const val FAULT = "fault"
        const val OUTER_TIMEOUT_MS = 10L
        const val LONG_DELAY_MS = 1_000L
    }
}
