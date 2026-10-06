package io.github.ygaray.voiceactionengine.core

import io.github.ygaray.voiceactionengine.core.failure.FailureReason
import io.github.ygaray.voiceactionengine.core.pipeline.CommandOutcome
import io.github.ygaray.voiceactionengine.core.pipeline.PickContext
import io.github.ygaray.voiceactionengine.core.pipeline.TierPolicy
import io.github.ygaray.voiceactionengine.core.pipeline.TierSelector
import io.github.ygaray.voiceactionengine.core.strategy.StrategyOutcome
import io.github.ygaray.voiceactionengine.core.telemetry.PipelineEvent
import io.github.ygaray.voiceactionengine.core.telemetry.TraceCode
import io.github.ygaray.voiceactionengine.core.telemetry.TurnRecord
import io.github.ygaray.voiceactionengine.core.telemetry.Usage
import io.github.ygaray.voiceactionengine.core.testing.FakeAiProvider
import io.github.ygaray.voiceactionengine.core.testing.NoNetworkGuard
import io.github.ygaray.voiceactionengine.core.testing.RecordingCommitSink
import io.github.ygaray.voiceactionengine.core.testing.RecordingEventListener
import io.github.ygaray.voiceactionengine.core.testing.ScriptedPicker
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.coroutines.cancellation.CancellationException

/** Every picker mistake is a Linear walk with one `router_fallback`; the picker can never fail or hang a command. */
class StartTierFallbackTest {
    private val input = CommandInput("turn it on", "en", null)
    private val fake = { FakeAiProvider(ProviderId.ANTHROPIC, FakeAiProvider.reply("p", Usage(4, 0, 0, 1))) }

    @Test
    fun aHungPickerUnderTheDefaultPolicyTimesOutToLinear() = runTest {
        NoNetworkGuard.during {
            val grammar = zeroCallTier("grammar", StrategyOutcome.NoMatch())
            val single = llmTier("single") { _, _ -> StrategyOutcome.Completed("s") }
            val agentic = llmTier("agentic") { _, _ -> StrategyOutcome.Completed("a") }
            val picker = ScriptedPicker({ _, _, _ ->
                delay(HANG_MILLIS)
                StrategyId("agentic")
            })

            val outcome = startTierPipeline(listOf(grammar, single, agentic), TierSelector.Custom(picker), fake())
                .execute(input)

            assertEquals("s", (outcome as CommandOutcome.Completed).reply)
            assertEquals(DEFAULT_PICKER_TIMEOUT, currentTime)
            assertEquals(1, outcome.trace.codes.count { it == TraceCode.ROUTER_FALLBACK })
            val selection = outcome.trace.selection!!
            assertEquals("router_fallback", selection.outcome)
            assertNull(selection.picked)
            assertEquals(0, selection.tiersBypassed)
            assertEquals(0, agentic.executions)
        }
    }

    @Test
    fun aPickerWithinItsTimeoutIsUsed() = runTest {
        NoNetworkGuard.during {
            val single = llmTier("single") { _, _ -> StrategyOutcome.Completed("s") }
            val agentic = llmTier("agentic") { _, _ -> StrategyOutcome.Completed("a") }
            val picker = ScriptedPicker({ _, _, _ ->
                delay(WITHIN_MILLIS)
                StrategyId("agentic")
            })
            val policy = TierPolicy { pickerTimeoutMillis = SHORT_TIMEOUT }

            val outcome = startTierPipeline(
                listOf(single, agentic),
                TierSelector.Custom(picker),
                fake(),
                policy = policy,
            ).execute(input)

            assertEquals("a", (outcome as CommandOutcome.Completed).reply)
            assertEquals("picked", outcome.trace.selection!!.outcome)
            assertEquals(1, agentic.executions)
            assertEquals(0, single.executions)
        }
    }

    private class Ladder {
        val grammar = zeroCallTier("grammar", StrategyOutcome.NoMatch())
        val single = llmTier("single") { _, _ -> StrategyOutcome.Completed("s") }
        val local = zeroCallTier("local", StrategyOutcome.NoMatch())
        val agentic = llmTier("agentic") { _, _ -> StrategyOutcome.Completed("a") }
        val tiers = listOf(grammar, single, local, agentic)
    }

    private suspend fun runWith(picker: ScriptedPicker, listener: RecordingEventListener = RecordingEventListener()):
        Pair<Ladder, CommandOutcome> {
        val ladder = Ladder()
        val outcome = startTierPipeline(ladder.tiers, TierSelector.Custom(picker), fake(), listener).execute(input)
        return ladder to outcome
    }

    private fun assertNoPickerFault(outcome: CommandOutcome) {
        assertFalse("a picker mistake must not fail the command", outcome is CommandOutcome.Failed)
        assertFalse(outcome.trace.codes.contains(TraceCode.STRATEGY_ERROR))
    }

    private fun assertFallback(outcome: CommandOutcome, expectedFirstTier: String) {
        assertEquals(1, outcome.trace.codes.count { it == TraceCode.ROUTER_FALLBACK })
        val selection = outcome.trace.selection!!
        assertEquals("router_fallback", selection.outcome)
        assertNull(selection.picked)
        assertEquals(0, selection.tiersBypassed)
        assertEquals(expectedFirstTier, outcome.trace.attempts[1].strategy.value)
    }

    private suspend fun assertUnusableAnswer(answer: StrategyId?) {
        val picker = ScriptedPicker({ _, _, _ -> answer })
        val (ladder, outcome) = runWith(picker)
        assertEquals("s", (outcome as CommandOutcome.Completed).reply)
        assertNoPickerFault(outcome)
        assertFallback(outcome, "single")
        assertEquals(1, picker.calls)
        assertEquals(idsOf("single", "agentic"), picker.eligibleSeen.single())
        assertEquals(0, ladder.agentic.executions)
    }

    @Test
    fun aNullAnswerFallsBackToLinear() = runTest { NoNetworkGuard.during { assertUnusableAnswer(null) } }

    @Test
    fun anUnknownIdFallsBackToLinear() = runTest { NoNetworkGuard.during { assertUnusableAnswer(StrategyId("nope")) } }

    @Test
    fun theZeroCallHeadIdFallsBackToLinear() = runTest {
        NoNetworkGuard.during { assertUnusableAnswer(StrategyId("grammar")) }
    }

    @Test
    fun aMidLadderZeroCallIdFallsBackToLinear() = runTest {
        NoNetworkGuard.during { assertUnusableAnswer(StrategyId("local")) }
    }

    @Test
    fun aMidLadderZeroCallTierIsBypassedWhenThePickLandsPastIt() = runTest {
        NoNetworkGuard.during {
            val picker = ScriptedPicker({ _, _, _ -> StrategyId("agentic") })

            val (ladder, outcome) = runWith(picker)

            assertEquals("a", (outcome as CommandOutcome.Completed).reply)
            assertEquals(0, ladder.local.executions)
            assertEquals(0, ladder.single.executions)
            assertEquals(1, ladder.agentic.executions)
            assertEquals(listOf("grammar", "agentic"), outcome.trace.attempts.map { it.strategy.value })
            assertEquals(1, outcome.trace.selection!!.tiersBypassed)
        }
    }

    @Test
    fun aPickerThatThrowsFallsBackToLinearAndLeaksNothing() = runTest {
        NoNetworkGuard.during {
            val listener = RecordingEventListener()
            val picker = ScriptedPicker({ _, _, _ -> throw IllegalStateException(CANARY) })

            val (_, outcome) = runWith(picker, listener)

            assertEquals("s", (outcome as CommandOutcome.Completed).reply)
            assertNoPickerFault(outcome)
            assertFallback(outcome, "single")
            assertEquals(1, picker.calls)
            val seen = listOf(outcome.trace, outcome.trace.selection, listener.events, outcome)
            val everything = seen.joinToString { "$it" }
            assertFalse(everything.contains(CANARY))
        }
    }

    @Test
    fun aForeignCancellationWhileTheCallerIsActiveFallsBackToLinear() = runTest {
        NoNetworkGuard.during {
            val picker = ScriptedPicker({ _, _, _ -> throw CancellationException("foreign") })

            val (_, outcome) = runWith(picker)

            assertEquals("s", (outcome as CommandOutcome.Completed).reply)
            assertNoPickerFault(outcome)
            assertFallback(outcome, "single")
        }
    }

    @Test
    fun aLeakedInnerTimeoutFallsBackToLinear() = runTest {
        NoNetworkGuard.during {
            val picker = ScriptedPicker({ _, _, _ ->
                withTimeout(1) { delay(LEAK_DELAY) }
                StrategyId("agentic")
            })

            val (ladder, outcome) = runWith(picker)

            assertEquals("s", (outcome as CommandOutcome.Completed).reply)
            assertNoPickerFault(outcome)
            assertFallback(outcome, "single")
            assertEquals(0, ladder.agentic.executions)
        }
    }

    @Test
    fun aHeadWithNoModelTierLeftNeverCallsThePicker() = runTest {
        NoNetworkGuard.during {
            val grammar = zeroCallTier("grammar", StrategyOutcome.NoMatch())
            val picker = ScriptedPicker(emptyList())

            val outcome = startTierPipeline(listOf(grammar), TierSelector.Custom(picker), fake()).execute(input)

            assertTrue(outcome is CommandOutcome.Unhandled)
            assertEquals(0, picker.calls)
            assertEquals(1, outcome.trace.codes.count { it == TraceCode.ROUTER_FALLBACK })
            assertNull(outcome.trace.selection)
        }
    }

    private val paid = Usage(inputUncached = 100, cacheRead = 20, cacheWrite = 5, output = 30)

    /** A picker that records one paid turn, tells [started], then hangs until it is cancelled. */
    private fun hangingPicker(started: CompletableDeferred<Unit>? = null) = ScriptedPicker({ _, _, ctx ->
        ctx.recordTurn(TurnRecord(null, "m", "tool_use", emptyList(), paid, TURN_LATENCY))
        started?.complete(Unit)
        awaitCancellation()
    })

    private fun twoModelTiers() = listOf(
        llmTier("single") { _, _ -> StrategyOutcome.Completed("s") },
        llmTier("agentic") { _, _ -> StrategyOutcome.Completed("a") },
    )

    @Test
    fun theCommandDeadlineMidPickIsATimeoutWithThePickOnTheTrace() = runTest {
        NoNetworkGuard.during {
            val listener = RecordingEventListener()
            val policy = TierPolicy { commandTimeoutMillis = DEADLINE_MILLIS }

            val outcome = startTierPipeline(
                twoModelTiers(),
                TierSelector.Custom(hangingPicker()),
                fake(),
                listener,
                policy = policy,
            ).execute(input)

            assertTrue((outcome as CommandOutcome.Failed).reason is FailureReason.Timeout)
            val selection = outcome.trace.selection!!
            assertEquals("timeout", selection.outcome)
            assertEquals(1, selection.turns.size)
            assertNull(selection.picked)
            assertEquals(PAID_TOTAL, outcome.trace.usage.total)
            assertEquals("timeout", listener.events.filterIsInstance<PipelineEvent.StartTierSelected>().single()
                .selection.outcome)
            assertFalse(outcome.trace.codes.contains(TraceCode.ROUTER_FALLBACK))
        }
    }

    @Test
    fun aCallerCancellingMidPickGetsTheCancellationAndATraceWithThePick() = runTest {
        NoNetworkGuard.during {
            val started = CompletableDeferred<Unit>()
            val sink = RecordingCommitSink()
            val pipeline = startTierPipeline(
                twoModelTiers(),
                TierSelector.Custom(hangingPicker(started)),
                fake(),
                sink = sink,
            )

            val call = async { pipeline.execute(input) }
            started.await()
            call.cancel()
            try {
                call.await()
            } catch (expected: CancellationException) {
                assertTrue(call.isCancelled)
            }

            val trace = sink.closes.single().trace
            assertEquals("cancelled", trace.selection!!.outcome)
            assertEquals(1, trace.selection!!.turns.size)
            assertEquals(PAID_TOTAL, trace.usage.total)
            assertFalse(trace.codes.contains(TraceCode.ROUTER_FALLBACK))
        }
    }

    @Test
    fun theCommandDeadlineWinsWhenItIsEarlierThanThePickerTimeout() = runTest {
        NoNetworkGuard.during {
            val policy = TierPolicy {
                commandTimeoutMillis = DEADLINE_MILLIS
                pickerTimeoutMillis = LATE_MILLIS
            }

            val outcome = startTierPipeline(
                twoModelTiers(),
                TierSelector.Custom(hangingPicker()),
                fake(),
                policy = policy,
            ).execute(input)

            assertTrue((outcome as CommandOutcome.Failed).reason is FailureReason.Timeout)
            assertEquals(DEADLINE_MILLIS, currentTime)
        }
    }

    @Test
    fun thePickerTimeoutWinsWhenItIsEarlierThanTheCommandDeadline() = runTest {
        NoNetworkGuard.during {
            val policy = TierPolicy {
                commandTimeoutMillis = LATE_MILLIS
                pickerTimeoutMillis = DEADLINE_MILLIS
            }

            val outcome = startTierPipeline(
                twoModelTiers(),
                TierSelector.Custom(hangingPicker()),
                fake(),
                policy = policy,
            ).execute(input)

            assertEquals("s", (outcome as CommandOutcome.Completed).reply)
            assertEquals("router_fallback", outcome.trace.selection!!.outcome)
            assertEquals(1, outcome.trace.codes.count { it == TraceCode.ROUTER_FALLBACK })
            assertEquals(DEADLINE_MILLIS, currentTime)
        }
    }

    @Test
    fun aPickerTurnRecordedAfterThePickClosedCountsInTheTotalButInNoAttemptOrSelection() = runTest {
        NoNetworkGuard.during {
            var captured: PickContext? = null
            val picker = ScriptedPicker({ _, _, ctx ->
                captured = ctx
                StrategyId("agentic")
            })
            val single = llmTier("single") { _, _ -> StrategyOutcome.Completed("s") }
            val agentic = llmTier("agentic") { _, _ ->
                captured!!.recordTurn(TurnRecord(null, "m", "end_turn", emptyList(), paid, TURN_LATENCY))
                StrategyOutcome.Completed("a")
            }

            val outcome = startTierPipeline(listOf(single, agentic), TierSelector.Custom(picker), fake())
                .execute(input)

            assertEquals("a", (outcome as CommandOutcome.Completed).reply)
            assertEquals("picked", outcome.trace.selection!!.outcome)
            assertEquals(0, outcome.trace.selection!!.turns.size)
            assertEquals(listOf(0), outcome.trace.attempts.map { it.turns.size })
            assertEquals(PAID_TOTAL, captured!!.tokensUsed)
        }
    }

    private companion object {
        const val DEADLINE_MILLIS = 100L
        const val LATE_MILLIS = 5_000L
        const val TURN_LATENCY = 5L
        const val PAID_TOTAL = 155L
        const val CANARY = "canary-picker-secret-9f3"
        const val LEAK_DELAY = 10L
        const val HANG_MILLIS = 60_000L
        const val DEFAULT_PICKER_TIMEOUT = 2_000L
        const val SHORT_TIMEOUT = 500L
        const val WITHIN_MILLIS = 400L
    }
}
