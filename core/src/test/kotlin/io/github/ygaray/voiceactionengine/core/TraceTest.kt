package io.github.ygaray.voiceactionengine.core

import io.github.ygaray.voiceactionengine.core.commit.StepResult
import io.github.ygaray.voiceactionengine.core.commit.ToolStep
import io.github.ygaray.voiceactionengine.core.failure.EscalationReason
import io.github.ygaray.voiceactionengine.core.pipeline.CommandOutcome
import io.github.ygaray.voiceactionengine.core.pipeline.commandPipeline
import io.github.ygaray.voiceactionengine.core.strategy.StrategyOutcome
import io.github.ygaray.voiceactionengine.core.telemetry.PipelineEvent
import io.github.ygaray.voiceactionengine.core.telemetry.TraceCode
import io.github.ygaray.voiceactionengine.core.telemetry.TurnRecord
import io.github.ygaray.voiceactionengine.core.telemetry.Usage
import io.github.ygaray.voiceactionengine.core.testing.FakeClock
import io.github.ygaray.voiceactionengine.core.testing.FakeMutation
import io.github.ygaray.voiceactionengine.core.testing.NoNetworkGuard
import io.github.ygaray.voiceactionengine.core.testing.RecordingCommitSink
import io.github.ygaray.voiceactionengine.core.testing.RecordingEventListener
import io.github.ygaray.voiceactionengine.core.testing.ScriptedGate
import io.github.ygaray.voiceactionengine.core.testing.ScriptedStrategy
import io.github.ygaray.voiceactionengine.core.testing.StrategyStep
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** The trace attributes every model turn to its tier, and a listener sees the run as it happens. */
class TraceTest {

    private fun tier(id: String, step: StrategyStep) = ScriptedStrategy(StrategyId(id), step)

    private fun turn(usage: Usage = Usage(INPUT, CACHE_READ, 0, OUTPUT)) =
        TurnRecord(ProviderId.ANTHROPIC, "claude-x", "tool_use", listOf("add_item"), usage, TURN_LATENCY)

    @Test
    fun aReportedTurnLandsInTheAttemptTheTraceAndTheSessionTotal() = runTest {
        NoNetworkGuard.during {
            var tokensAfterTurn = -1L
            val reported = turn()
            val strategy = tier("a") { _, session ->
                session.recordTurn(reported)
                tokensAfterTurn = session.tokensUsed
                StrategyOutcome.Completed("ok")
            }

            val outcome = commandPipeline {
                tier(strategy)
                gate = ScriptedGate.admitAll()
                commitSink = RecordingCommitSink()
            }.execute(CommandInput("add milk"))

            assertTrue(outcome is CommandOutcome.Completed)
            val attempt = outcome.trace.attempts.single()
            assertEquals(ProviderId.ANTHROPIC, attempt.provider)
            assertEquals("claude-x", attempt.model)
            assertEquals(1, attempt.turns.size)
            assertSame(reported, attempt.turns.single())
            assertEquals(TOTAL, attempt.usage.total)
            assertEquals(TOTAL, outcome.trace.usage.total)
            assertEquals(TOTAL, tokensAfterTurn)
        }
    }

    @Test
    fun aTierThatReportsNoTurnHasNoProviderModelOrUsage() = runTest {
        NoNetworkGuard.during {
            val outcome = commandPipeline {
                tier(tier("a") { _, _ -> StrategyOutcome.Completed("ok") })
                gate = ScriptedGate.admitAll()
                commitSink = RecordingCommitSink()
            }.execute(CommandInput("add milk"))

            val attempt = outcome.trace.attempts.single()
            assertNull(attempt.provider)
            assertNull(attempt.model)
            assertTrue(attempt.turns.isEmpty())
            assertEquals(0L, attempt.usage.total)
            assertEquals(0L, outcome.trace.usage.total)
        }
    }

    @Test
    fun theClockDrivesLatencyAndTheRunIdIsTheCommandId() = runTest {
        NoNetworkGuard.during {
            val fakeClock = FakeClock(START)
            val strategy = tier("a") { _, _ ->
                fakeClock.advanceBy(ADVANCE)
                StrategyOutcome.Completed("ok")
            }

            val outcome = commandPipeline {
                tier(strategy)
                gate = ScriptedGate.admitAll()
                commitSink = RecordingCommitSink()
                clock = fakeClock
                runIds = { "run-fixed" }
            }.execute(CommandInput("add milk"))

            assertEquals("run-fixed", outcome.runId)
            assertEquals(outcome.runId, outcome.trace.runId)
            assertEquals(ADVANCE, outcome.trace.attempts.single().latencyMillis)
            assertEquals(START, outcome.trace.startedAtMillis)
            assertEquals(ADVANCE, outcome.trace.durationMillis)
        }
    }

    @Test
    fun aListenerSeesTheRunLiveInOrder() = runTest {
        NoNetworkGuard.during {
            val listener = RecordingEventListener()
            val reported = turn()
            val strategy = tier("a") { _, session ->
                session.recordTurn(reported)
                StrategyOutcome.Completed("ok")
            }

            val outcome = commandPipeline {
                tier(strategy)
                gate = ScriptedGate.admitAll()
                commitSink = RecordingCommitSink()
                this.listener = listener
            }.execute(CommandInput("add milk"))

            val events = listener.events
            assertEquals(
                listOf(
                    PipelineEvent.CommandStarted::class,
                    PipelineEvent.TierStarted::class,
                    PipelineEvent.ProviderCall::class,
                    PipelineEvent.TierFinished::class,
                    PipelineEvent.RunClosed::class,
                ),
                events.map { it::class },
            )
            assertTrue(events.all { it.runId == outcome.runId })
            assertSame(reported, (events[PROVIDER_CALL_INDEX] as PipelineEvent.ProviderCall).turn)
            assertSame(outcome.trace.attempts.single(), (events[FINISHED_INDEX] as PipelineEvent.TierFinished).attempt)
            val closed = events.last() as PipelineEvent.RunClosed
            assertEquals("done", closed.terminationCode)
            assertEquals(0, closed.executedCount)
            assertEquals(0, closed.committedCount)
        }
    }

    @Test
    fun aFallbackTurnFlowsToTheAttemptTheEventAndTheToString() = runTest {
        NoNetworkGuard.during {
            val listener = RecordingEventListener()
            val usage = Usage(INPUT, CACHE_READ, 0, OUTPUT)
            val strategy = tier("a") { _, session ->
                val fellBack = TurnRecord(
                    ProviderId.ANTHROPIC, "model-a", "end_turn", emptyList(), usage, TURN_LATENCY, ProviderId.ON_DEVICE,
                )
                session.recordTurn(fellBack)
                StrategyOutcome.Completed("ok")
            }

            val outcome = commandPipeline {
                tier(strategy)
                gate = ScriptedGate.admitAll()
                commitSink = RecordingCommitSink()
                this.listener = listener
            }.execute(CommandInput("add milk"))

            val attempt = outcome.trace.attempts.single()
            assertEquals(ProviderId.ON_DEVICE, attempt.fallbackFrom)
            assertEquals(ProviderId.ON_DEVICE, attempt.turns.single().fallbackFrom)
            val call = listener.events.filterIsInstance<PipelineEvent.ProviderCall>().single()
            assertEquals(ProviderId.ON_DEVICE, call.turn.fallbackFrom)
            assertTrue(attempt.toString().contains("fallbackFrom=on_device"))
            assertTrue(attempt.turns.single().toString().contains("fallbackFrom=on_device"))
        }
    }

    @Test
    fun aSixArgumentTurnHasNoFallbackAndATierWithNoTurnsHasNone() = runTest {
        NoNetworkGuard.during {
            assertNull(turn().fallbackFrom)

            val reporting = tier("a") { _, session ->
                session.recordTurn(turn())
                StrategyOutcome.Completed("ok")
            }
            val withTurn = commandPipeline {
                tier(reporting)
                gate = ScriptedGate.admitAll()
                commitSink = RecordingCommitSink()
            }.execute(CommandInput("add milk"))
            assertNull(withTurn.trace.attempts.single().fallbackFrom)

            val silent = commandPipeline {
                tier(tier("a") { _, _ -> StrategyOutcome.Completed("ok") })
                gate = ScriptedGate.admitAll()
                commitSink = RecordingCommitSink()
            }.execute(CommandInput("add milk"))
            assertNull(silent.trace.attempts.single().fallbackFrom)
        }
    }

    private val phaseTwoCodes = listOf(
        TraceCode.GATE_ERROR, TraceCode.APPLY_ERROR, TraceCode.APPLY_CANCELLED, TraceCode.SINK_ERROR,
        TraceCode.LISTENER_ERROR, TraceCode.POLICY_SOURCE_ERROR, TraceCode.STRATEGY_ERROR, TraceCode.ENGINE_TIMEOUT,
        TraceCode.ESCALATION_SUPPRESSED, TraceCode.TIER_SKIPPED_POLICY, TraceCode.MAX_TIER_UNKNOWN,
        TraceCode.OFFLINE_UNAVAILABLE, TraceCode.ON_DEVICE_UNAVAILABLE, TraceCode.ON_DEVICE_PROBE_ERROR,
        TraceCode.COMMIT_HELD_CANCELLED,
    )

    private val routerCodes = mapOf(
        TraceCode.PROVIDER_NOT_SELECTED to "provider_not_selected",
        TraceCode.SELECTION_SOURCE_ERROR to "selection_source_error",
        TraceCode.PROVIDER_NOT_ALLOWED to "provider_not_allowed",
        TraceCode.PROVIDER_NOT_REGISTERED to "provider_not_registered",
        TraceCode.CREDENTIAL_MISSING to "credential_missing",
        TraceCode.CREDENTIAL_UNREADABLE to "credential_unreadable",
        TraceCode.CREDENTIAL_SOURCE_ERROR to "credential_source_error",
        TraceCode.CREDENTIAL_MISMATCH to "credential_mismatch",
        TraceCode.CAPABILITY_LOOKUP_ERROR to "capability_lookup_error",
        TraceCode.CAPABILITY_REFUSED to "capability_refused",
        TraceCode.PROVIDER_ERROR to "provider_error",
        TraceCode.PROVIDER_FALLBACK to "provider_fallback",
        TraceCode.FALLBACK_REFUSED to "fallback_refused",
    )

    @Test
    fun theRouterCodesHaveTheirSnakeCaseWireValues() {
        assertEquals(THIRTEEN, routerCodes.size)
        routerCodes.forEach { (code, wire) -> assertEquals(wire, code.value) }
    }

    private val agenticLoopCodes = mapOf(
        TraceCode.UNKNOWN_TOOL to "unknown_tool",
        TraceCode.TOOL_PREPARE_ERROR to "tool_prepare_error",
        TraceCode.READ_TOOL_MUTATION_REJECTED to "read_tool_mutation_rejected",
    )

    @Test
    fun theAgenticLoopCodesHaveTheirSnakeCaseWireValues() {
        assertEquals(THREE, agenticLoopCodes.size)
        agenticLoopCodes.forEach { (code, wire) -> assertEquals(wire, code.value) }
        val existing = phaseTwoCodes + routerCodes.keys + TraceCode.EXTRA_TOOL_CALLS_DROPPED
        val all = existing + agenticLoopCodes.keys
        val wireValues = all.map { it.value }
        assertEquals(wireValues.size, wireValues.toSet().size)
        assertEquals(declaredWireValues(), wireValues.toSet())
    }

    // Every code the companion declares, read from the type itself, so a code added without a row here fails this test
    // instead of slipping past a hand-kept total. A value class getter returns the wire string under a mangled name.
    private fun declaredWireValues(): Set<String> =
        TraceCode.Companion::class.java.declaredMethods
            .filter { method ->
                val name = method.name.removePrefix("get").substringBefore('-')
                method.parameterCount == 0 && method.name.startsWith("get") && name.isNotEmpty() &&
                    name.all { it.isUpperCase() || it.isDigit() || it == '_' }
            }
            .map { it.invoke(TraceCode.Companion) as String }
            .toSet()

    @Test
    fun everyTraceCodeIsDistinctAndLowerSnakeCase() {
        val all = phaseTwoCodes + routerCodes.keys
        assertEquals(TWENTY_EIGHT, all.size)
        assertEquals(TWENTY_EIGHT, all.map { it.value }.toSet().size)
        val shape = Regex("[a-z0-9_]+")
        all.forEach { assertTrue(it.value, shape.matches(it.value)) }
    }

    @Test
    fun theOnDeviceUnavailableWireValueIsUnchanged() {
        assertEquals("on_device_unavailable", TraceCode.ON_DEVICE_UNAVAILABLE.value)
    }

    /** Maps an Anthropic response (its input excludes cached tokens) to the normalized usage. */
    private fun anthropicUsage(input: Long, cacheRead: Long, cacheWrite: Long, output: Long) =
        Usage(input, cacheRead, cacheWrite, output)

    /** Maps an OpenAI-style response (its prompt tokens include cached tokens) to the normalized usage. */
    private fun openAiUsage(prompt: Long, cached: Long, completion: Long) =
        Usage(prompt - cached, cached, 0, completion)

    private suspend fun usageOf(usage: Usage, provider: ProviderId): Usage {
        val strategy = tier("a") { _, session ->
            session.recordTurn(TurnRecord(provider, "model", "stop", emptyList(), usage, TURN_LATENCY))
            StrategyOutcome.Completed("ok")
        }
        val outcome = commandPipeline {
            tier(strategy)
            gate = ScriptedGate.admitAll()
            commitSink = RecordingCommitSink()
        }.execute(CommandInput("add milk"))
        return outcome.trace.attempts.single().usage
    }

    @Test
    fun anAnthropicTurnAndAnOpenAiTurnForTheSameWorkHaveEqualAttemptUsage() = runTest {
        NoNetworkGuard.during {
            val anthropic = usageOf(anthropicUsage(INPUT, CACHE_READ, 0, OUTPUT), ProviderId.ANTHROPIC)
            val openAi = usageOf(openAiUsage(INPUT + CACHE_READ, CACHE_READ, OUTPUT), ProviderId.OPENAI)

            assertEquals(TOTAL, anthropic.total)
            assertEquals(TOTAL, openAi.total)
            assertEquals(anthropic, openAi)
        }
    }

    @Test
    fun anEscalatedTierRecordsItsReasonAndItsClockLatency() = runTest {
        NoNetworkGuard.during {
            val fakeClock = FakeClock(START)
            val reason = EscalationReason.ModelDeclined()
            val tierA = tier("a") { _, _ ->
                fakeClock.advanceBy(ADVANCE)
                StrategyOutcome.Escalate(reason, null)
            }
            val tierB = tier("b") { _, _ ->
                fakeClock.advanceBy(ADVANCE * 2)
                StrategyOutcome.Completed("ok")
            }

            val outcome = commandPipeline {
                tier(tierA)
                tier(tierB)
                gate = ScriptedGate.admitAll()
                commitSink = RecordingCommitSink()
                clock = fakeClock
            }.execute(CommandInput("add milk"))

            val (escalated, completed) = outcome.trace.attempts
            assertEquals("escalated", escalated.outcome)
            assertSame(reason, escalated.escalationReason)
            assertNull(escalated.suppressedEscalation)
            assertEquals(ADVANCE, escalated.latencyMillis)
            assertEquals("completed", completed.outcome)
            assertEquals(ADVANCE * 2, completed.latencyMillis)
            assertEquals(ADVANCE * 3, outcome.trace.durationMillis)
        }
    }

    @Test
    fun aSuppressedEscalationRecordsItsReasonAndItsClockLatency() = runTest {
        NoNetworkGuard.during {
            val fakeClock = FakeClock(START)
            val reason = EscalationReason.ModelDeclined()
            val write = FakeMutation("write", StepResult("done", false, "ok", emptyMap()))
            val tierA = tier("a") { _, session ->
                session.submit(ToolStep.Mutation(write))
                fakeClock.advanceBy(ADVANCE)
                StrategyOutcome.Escalate(reason, null)
            }

            val outcome = commandPipeline {
                tier(tierA)
                tier(tier("b") { _, _ -> StrategyOutcome.Completed("never") })
                gate = ScriptedGate.admitAll()
                commitSink = RecordingCommitSink()
                clock = fakeClock
            }.execute(CommandInput("add milk"))

            val attempt = outcome.trace.attempts.single()
            assertEquals("escalation_suppressed", attempt.outcome)
            assertNull(attempt.escalationReason)
            assertSame(reason, attempt.suppressedEscalation)
            assertEquals(ADVANCE, attempt.latencyMillis)
        }
    }

    private companion object {
        const val INPUT = 100L
        const val CACHE_READ = 900L
        const val OUTPUT = 50L
        const val TOTAL = 1050L
        const val TURN_LATENCY = 120L
        const val START = 1_000L
        const val ADVANCE = 40L
        const val PROVIDER_CALL_INDEX = 2
        const val FINISHED_INDEX = 3
        const val THIRTEEN = 13
        const val TWENTY_EIGHT = 28
        const val THREE = 3
    }
}
