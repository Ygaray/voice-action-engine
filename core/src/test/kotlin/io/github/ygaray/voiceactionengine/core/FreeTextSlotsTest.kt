package io.github.ygaray.voiceactionengine.core

import io.github.ygaray.voiceactionengine.core.failure.FailureDetails
import io.github.ygaray.voiceactionengine.core.failure.FailureReason
import io.github.ygaray.voiceactionengine.core.internal.errorClassOf
import io.github.ygaray.voiceactionengine.core.telemetry.Usage
import io.github.ygaray.voiceactionengine.core.transcript.AssistantMessage
import io.github.ygaray.voiceactionengine.core.transcript.ModelResponse
import io.github.ygaray.voiceactionengine.core.transcript.StopReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Server- or app-supplied strings that reach `toString`, the trace or events must be short identifiers, not text. */
class FreeTextSlotsTest {
    private val hostile = listOf("sk-CANARY\nsecret", "two words", "key=\"abc\"", "a".repeat(LONG), "")

    private fun refused(block: () -> Any) {
        val failure = assertThrows(IllegalArgumentException::class.java) { block() }
        assertTrue(failure.message.orEmpty(), !failure.message.orEmpty().contains("CANARY"))
    }

    @Test
    fun otherRefusesTextThatIsNotAShortIdentifier() {
        hostile.forEach { text -> refused { FailureReason.Other(text) } }
        assertEquals("commit_held_cancelled", FailureReason.Other("commit_held_cancelled").code)
        assertEquals("vendor:Code-1.2", FailureReason.Other("vendor:Code-1.2").code)
    }

    @Test
    fun unexpectedRefusesTextThatIsNotAClassName() {
        hostile.forEach { text -> refused { FailureReason.Unexpected(text) } }
        assertEquals("Outer\$Inner", FailureReason.Unexpected("Outer\$Inner").errorClass)
    }

    @Test
    fun failureDetailsRefuseHostileErrorTypesAndRequestIds() {
        hostile.forEach { text ->
            refused { FailureDetails(HTTP_ERROR, text, null) }
            refused { FailureDetails(HTTP_ERROR, null, text) }
        }
        val fine = FailureDetails(HTTP_ERROR, "rate_limit_error", "req_011CS-abc")
        assertEquals("req_011CS-abc", fine.requestId)
        assertEquals(null, FailureDetails(null, null, null).requestId)
    }

    @Test
    fun aModelResponseRefusesAHostileRequestId() {
        val message = AssistantMessage(emptyList(), null)
        hostile.forEach { text -> refused { ModelResponse(message, StopReason.END_TURN, Usage.ZERO, text) } }
        assertEquals("req_1", ModelResponse(message, StopReason.END_TURN, Usage.ZERO, "req_1").requestId)
    }

    @Test
    fun theEnginesOwnClassNamesAlwaysFitUnexpected() {
        class `bad name!` : RuntimeException()
        val names = listOf(
            errorClassOf(`bad name!`()),
            errorClassOf(IllegalStateException("x")),
            errorClassOf(object : RuntimeException() {}),
            errorClassOf(Exception("é")),
        )
        names.forEach { assertEquals(it, FailureReason.Unexpected(it).errorClass) }
    }

    private companion object {
        const val LONG = 200
        const val HTTP_ERROR = 500
    }
}
