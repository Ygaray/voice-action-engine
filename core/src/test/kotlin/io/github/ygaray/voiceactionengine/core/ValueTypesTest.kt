package io.github.ygaray.voiceactionengine.core

import io.github.ygaray.voiceactionengine.core.commit.ActionKind
import io.github.ygaray.voiceactionengine.core.commit.FinishedKind
import io.github.ygaray.voiceactionengine.core.telemetry.TraceCode
import io.github.ygaray.voiceactionengine.core.telemetry.Usage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ValueTypesTest {

    @Test
    fun actionKindWireValues() {
        assertEquals("committed", ActionKind.COMMITTED.value)
        assertEquals("held", ActionKind.HELD.value)
        assertEquals("preview", ActionKind.PREVIEW.value)
        assertEquals("is_error", ActionKind.IS_ERROR.value)
        val all = listOf(ActionKind.COMMITTED, ActionKind.HELD, ActionKind.PREVIEW, ActionKind.IS_ERROR)
        assertEquals(all.size, all.toSet().size)
        assertEquals("is_error", ActionKind.IS_ERROR.toString())
    }

    @Test
    fun finishedKindWireValues() {
        assertEquals("read", FinishedKind.READ.value)
        assertEquals("preview", FinishedKind.PREVIEW.value)
        assertEquals("error", FinishedKind.ERROR.value)
        assertEquals(THREE, setOf(FinishedKind.READ, FinishedKind.PREVIEW, FinishedKind.ERROR).size)
        assertEquals("error", FinishedKind.ERROR.toString())
    }

    @Test
    fun usageSumsStopAtLongMaxInsteadOfWrappingNegative() {
        val huge = Usage(Long.MAX_VALUE, Long.MAX_VALUE, 1, 1)
        assertEquals(Long.MAX_VALUE, huge.total)
        val doubled = huge + huge
        assertEquals(Long.MAX_VALUE, doubled.inputUncached)
        assertEquals(Long.MAX_VALUE, doubled.total)
        assertEquals(2L, doubled.cacheWrite)
    }

    @Test
    fun usageTotalIsTheFourWaySum() {
        assertEquals(HUNDRED, Usage(TEN, TWENTY, THIRTY, FORTY).total)
        assertEquals(0L, Usage.ZERO.total)
    }

    @Test
    fun usageAddsBucketWise() {
        val sum = Usage(1, 2, THREE.toLong(), 4) + Usage(TEN, TWENTY, THIRTY, FORTY)
        assertEquals(Usage(11, 22, 33, 44), sum)
        assertEquals(Usage(11, 22, 33, 44).hashCode(), sum.hashCode())
        assertNotEquals(Usage(11, 22, 33, 45), sum)
    }

    @Test
    fun usageRejectsNegativeFields() {
        val negatives = listOf(Triple(-1L, 0L, 0L), Triple(0L, -1L, 0L), Triple(0L, 0L, -1L))
        for ((a, b, c) in negatives) {
            try {
                Usage(a, b, c, 0)
                fail("expected IllegalArgumentException")
            } catch (e: IllegalArgumentException) {
                assertTrue(e.message.orEmpty().isNotEmpty())
            }
        }
        try {
            Usage(0, 0, 0, -1)
            fail("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message.orEmpty().isNotEmpty())
        }
    }

    @Test
    fun anthropicAndOpenAiMappingsOfTheSameWorkAgree() {
        // Anthropic: input_tokens excludes cached tokens.
        val anthropic = Usage(inputUncached = 100, cacheRead = 900, cacheWrite = 0, output = 50)
        // OpenAI: prompt_tokens (1000) includes the 900 cached tokens, so uncached = prompt - cached.
        val promptTokens = 1000L
        val cachedTokens = 900L
        val openAi = Usage(
            inputUncached = promptTokens - cachedTokens,
            cacheRead = cachedTokens,
            cacheWrite = 0,
            output = 50,
        )
        assertEquals(anthropic, openAi)
        assertEquals(TOTAL_1050, anthropic.total)
        assertEquals(TOTAL_1050, openAi.total)
    }

    @Test
    fun usageToStringShowsCountsAndTotal() {
        assertEquals(
            "Usage(inputUncached=10, cacheRead=20, cacheWrite=30, output=40, total=100)",
            Usage(TEN, TWENTY, THIRTY, FORTY).toString(),
        )
    }

    @Test
    fun traceCodesAreFourteenUniqueLowerSnakeValues() {
        val codes = listOf(
            TraceCode.GATE_ERROR to "gate_error",
            TraceCode.APPLY_ERROR to "apply_error",
            TraceCode.APPLY_CANCELLED to "apply_cancelled",
            TraceCode.SINK_ERROR to "sink_error",
            TraceCode.LISTENER_ERROR to "listener_error",
            TraceCode.POLICY_SOURCE_ERROR to "policy_source_error",
            TraceCode.STRATEGY_ERROR to "strategy_error",
            TraceCode.ENGINE_TIMEOUT to "engine_timeout",
            TraceCode.ESCALATION_SUPPRESSED to "escalation_suppressed",
            TraceCode.TIER_SKIPPED_POLICY to "tier_skipped_policy",
            TraceCode.MAX_TIER_UNKNOWN to "max_tier_unknown",
            TraceCode.OFFLINE_UNAVAILABLE to "offline_unavailable",
            TraceCode.ON_DEVICE_UNAVAILABLE to "on_device_unavailable",
            TraceCode.COMMIT_HELD_CANCELLED to "commit_held_cancelled",
        )
        assertEquals(FOURTEEN, codes.size)
        for ((code, wire) in codes) {
            assertEquals(wire, code.value)
            assertEquals(wire, code.toString())
        }
        assertEquals(FOURTEEN, codes.map { it.first }.toSet().size)
    }

    private companion object {
        const val THREE = 3
        const val TEN = 10L
        const val TWENTY = 20L
        const val THIRTY = 30L
        const val FORTY = 40L
        const val HUNDRED = 100L
        const val TOTAL_1050 = 1050L
        const val FOURTEEN = 14
    }
}
