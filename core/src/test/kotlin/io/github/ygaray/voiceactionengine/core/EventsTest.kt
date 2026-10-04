package io.github.ygaray.voiceactionengine.core

import io.github.ygaray.voiceactionengine.core.commit.ActionKind
import io.github.ygaray.voiceactionengine.core.commit.FinishedKind
import io.github.ygaray.voiceactionengine.core.commit.GateDecision
import io.github.ygaray.voiceactionengine.core.commit.StepResult
import io.github.ygaray.voiceactionengine.core.commit.ToolStep
import io.github.ygaray.voiceactionengine.core.failure.EscalationReason
import io.github.ygaray.voiceactionengine.core.pipeline.CommandOutcome
import io.github.ygaray.voiceactionengine.core.pipeline.TierPolicy
import io.github.ygaray.voiceactionengine.core.pipeline.TierPolicySource
import io.github.ygaray.voiceactionengine.core.pipeline.commandPipeline
import io.github.ygaray.voiceactionengine.core.strategy.StrategyOutcome
import io.github.ygaray.voiceactionengine.core.telemetry.PipelineEvent
import io.github.ygaray.voiceactionengine.core.telemetry.PipelineEventListener
import io.github.ygaray.voiceactionengine.core.telemetry.RunRecorder
import io.github.ygaray.voiceactionengine.core.telemetry.TraceCode
import io.github.ygaray.voiceactionengine.core.telemetry.TurnRecord
import io.github.ygaray.voiceactionengine.core.telemetry.Usage
import io.github.ygaray.voiceactionengine.core.testing.FakeMutation
import io.github.ygaray.voiceactionengine.core.testing.NoNetworkGuard
import io.github.ygaray.voiceactionengine.core.testing.RecordingCommitSink
import io.github.ygaray.voiceactionengine.core.testing.RecordingEventListener
import io.github.ygaray.voiceactionengine.core.testing.ScriptedGate
import io.github.ygaray.voiceactionengine.core.testing.ScriptedStrategy
import io.github.ygaray.voiceactionengine.core.testing.StrategyStep
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.reflect.KClass

/** Every event category reaches the listener live and in order, agrees with the trace, and cannot disturb the run. */
class EventsTest {

    private val first = StrategyId("a")
    private val second = StrategyId("b")

    private fun tier(id: StrategyId, step: StrategyStep) = ScriptedStrategy(id, step)

    private fun ok(name: String) = FakeMutation(name, StepResult("done", false, "ok", emptyMap()))

    private fun turn() =
        TurnRecord(ProviderId.ANTHROPIC, "claude-x", "tool_use", listOf("add_item"), Usage(1, 2, 0, 3), 5L)

    /** What a two-tier scenario produced, with the pieces a test compares. */
    private class Result(
        val outcome: CommandOutcome,
        val sink: RecordingCommitSink,
        val committed: FakeMutation,
        val held: FakeMutation,
        val turn: TurnRecord,
    )

    /** Tier a previews then escalates; tier b reports a turn, commits one change, holds one, and completes. */
    private suspend fun twoTierRun(listener: PipelineEventListener?): Result {
        val committed = ok("commit_tool")
        val held = ok("held_tool")
        val reported = turn()
        val tierA = tier(first) { _, session ->
            val preview = StepResult("preview", false, "p", emptyMap())
            session.submit(ToolStep.Finished("preview_tool", FinishedKind.PREVIEW, preview))
            StrategyOutcome.Escalate(EscalationReason.ModelDeclined(), null)
        }
        val tierB = tier(second) { _, session ->
            session.recordTurn(reported)
            session.submit(ToolStep.Mutation(committed))
            session.submit(ToolStep.Mutation(held))
            StrategyOutcome.Completed("all done")
        }
        val sink = RecordingCommitSink()
        val outcome = commandPipeline {
            tier(tierA)
            tier(tierB)
            gate = ScriptedGate.sequence(null, GateDecision.Admit(), GateDecision.Hold())
            commitSink = sink
            this.listener = listener
        }.execute(CommandInput("add milk"))
        return Result(outcome, sink, committed, held, reported)
    }

    private fun assertSameEffects(expected: Result, actual: Result) {
        assertEquals(expected.outcome::class, actual.outcome::class)
        assertEquals(shape(expected.outcome), shape(actual.outcome))
        assertEquals(expected.outcome.held.size, actual.outcome.held.size)
        val expectedSink = expected.sink.actions.map { it.action.position to it.action.kind }
        assertEquals(expectedSink, actual.sink.actions.map { it.action.position to it.action.kind })
        assertEquals(expected.sink.closes.map { it.code }, actual.sink.closes.map { it.code })
        assertEquals(expected.committed.applyCount, actual.committed.applyCount)
        assertEquals(expected.held.applyCount, actual.held.applyCount)
    }

    private fun shape(outcome: CommandOutcome) = outcome.executed.map { it.position to it.kind }

    private fun kinds(events: List<PipelineEvent>): List<KClass<out PipelineEvent>> = events.map { it::class }

    @Test
    fun aTwoTierRunDeliversTheTenEventsInOrder() = runTest {
        NoNetworkGuard.during {
            val listener = RecordingEventListener()
            val result = twoTierRun(listener)

            assertTrue(result.outcome is CommandOutcome.Completed)
            val events = listener.events
            assertEquals(
                listOf(
                    PipelineEvent.CommandStarted::class,
                    PipelineEvent.TierStarted::class,
                    PipelineEvent.ActionRecorded::class,
                    PipelineEvent.TierFinished::class,
                    PipelineEvent.TierStarted::class,
                    PipelineEvent.ProviderCall::class,
                    PipelineEvent.ActionRecorded::class,
                    PipelineEvent.ActionRecorded::class,
                    PipelineEvent.TierFinished::class,
                    PipelineEvent.RunClosed::class,
                ),
                kinds(events),
            )
            assertEquals(first, (events[1] as PipelineEvent.TierStarted).strategy)
            assertEquals(second, (events[4] as PipelineEvent.TierStarted).strategy)
            val recorded = events.filterIsInstance<PipelineEvent.ActionRecorded>()
            assertEquals(listOf(0, 1, 2), recorded.map { it.position })
            assertEquals(listOf(ActionKind.PREVIEW, ActionKind.COMMITTED, ActionKind.HELD), recorded.map { it.kind })
            assertEquals(listOf("preview_tool", "commit_tool", "held_tool"), recorded.map { it.toolName })
            assertEquals(listOf(false, true, false), recorded.map { it.applied })
            val closed = events.last() as PipelineEvent.RunClosed
            assertEquals("done", closed.terminationCode)
            assertEquals(3, closed.executedCount)
            assertEquals(1, closed.committedCount)
            assertTrue(events.all { it.runId == result.outcome.runId })
        }
    }

    @Test
    fun eventsEqualTheTraceAndTheExecutedList() = runTest {
        NoNetworkGuard.during {
            val listener = RecordingEventListener()
            val result = twoTierRun(listener)
            val outcome = result.outcome
            val events = listener.events

            val finished = events.filterIsInstance<PipelineEvent.TierFinished>().map { it.attempt }
            assertEquals(outcome.trace.attempts.size, finished.size)
            finished.zip(outcome.trace.attempts).forEach { (event, traced) -> assertSame(traced, event) }

            val codes = events.mapNotNull {
                when (it) {
                    is PipelineEvent.EngineCode -> it.code
                    is PipelineEvent.TierSkipped -> it.code
                    else -> null
                }
            }
            assertEquals(outcome.trace.codes, codes)

            val recorded = events.filterIsInstance<PipelineEvent.ActionRecorded>()
            assertEquals(outcome.executed.map { it.position }, recorded.map { it.position })
            assertEquals(outcome.executed.map { it.kind }, recorded.map { it.kind })
            assertEquals(outcome.executed.map { it.toolName }, recorded.map { it.toolName })

            val turns = events.filterIsInstance<PipelineEvent.ProviderCall>().map { it.turn }
            assertEquals(outcome.trace.attempts.flatMap { it.turns }, turns)
            assertSame(result.turn, turns.single())
        }
    }

    @Test
    fun aPolicySkippedTierYieldsTierSkippedWithThePolicyCode() = runTest {
        NoNetworkGuard.during {
            val listener = RecordingEventListener()
            val skipped = tier(second) { _, _ -> StrategyOutcome.Completed("never") }
            val outcome = commandPipeline {
                tier(tier(first) { _, _ -> StrategyOutcome.Completed("a") })
                tier(skipped)
                gate = ScriptedGate.admitAll()
                commitSink = RecordingCommitSink()
                policy = TierPolicySource.fixed(TierPolicy { maxTier = first })
                this.listener = listener
            }.execute(CommandInput("hi"))

            val event = listener.events.filterIsInstance<PipelineEvent.TierSkipped>().single()
            assertEquals(second, event.strategy)
            assertEquals(TraceCode.TIER_SKIPPED_POLICY, event.code)
            assertEquals("tier_skipped_policy", event.code.value)
            assertEquals(0, skipped.executions)
            assertEquals(listOf(TraceCode.TIER_SKIPPED_POLICY), outcome.trace.codes)
        }
    }

    @Test
    fun aGateThrowYieldsAnEngineCodeEvent() = runTest {
        NoNetworkGuard.during {
            val listener = RecordingEventListener()
            val write = ok("write")
            val strategy = tier(first) { _, session ->
                session.submit(ToolStep.Mutation(write))
                StrategyOutcome.Completed("done")
            }
            val outcome = commandPipeline {
                tier(strategy)
                gate = ScriptedGate { throw IllegalStateException("gate broke") }
                commitSink = RecordingCommitSink()
                this.listener = listener
            }.execute(CommandInput("hi"))

            val codes = listener.events.filterIsInstance<PipelineEvent.EngineCode>()
            assertEquals(listOf(TraceCode.GATE_ERROR), codes.map { it.code })
            assertEquals("gate_error", codes.single().code.value)
            assertEquals(0, write.applyCount)
            assertEquals(listOf(TraceCode.GATE_ERROR), outcome.trace.codes)
            // XR-171-03 (ruling a): the recorded action is an error, never a hold.
            val recorded = listener.events.filterIsInstance<PipelineEvent.ActionRecorded>()
            assertEquals(listOf(ActionKind.IS_ERROR), recorded.map { it.kind })
            assertTrue(outcome.held.isEmpty())
            val kinds = kinds(listener.events)
            val codeAt = kinds.indexOf(PipelineEvent.EngineCode::class)
            assertTrue(codeAt in 0 until kinds.indexOf(PipelineEvent.ActionRecorded::class))
        }
    }

    @Test
    fun theInternalCacheHookYieldsCacheNotEngagedWithProviderAndModel() = runTest {
        val listener = RecordingEventListener()
        val recorder = RunRecorder("run-1", null, null, 0, { 0L }, listener)

        recorder.cacheNotEngaged(first, ProviderId.ANTHROPIC, "claude-x")

        val event = listener.events.single() as PipelineEvent.CacheNotEngaged
        assertEquals("run-1", event.runId)
        assertEquals(first, event.strategy)
        assertEquals(ProviderId.ANTHROPIC, event.provider)
        assertEquals("claude-x", event.model)
    }

    @Test
    fun aListenerThatThrowsOnEveryEventChangesNothing() = runTest {
        NoNetworkGuard.during {
            val reference = RecordingEventListener()
            val baseline = twoTierRun(reference)
            val withoutListener = twoTierRun(null)
            val throwing = RecordingEventListener(throwing = true)
            val disturbed = twoTierRun(throwing)

            for (other in listOf(withoutListener, disturbed)) {
                assertSameEffects(baseline, other)
            }
            assertEquals(
                (baseline.outcome as CommandOutcome.Completed).reply,
                (disturbed.outcome as CommandOutcome.Completed).reply,
            )
            assertEquals(1, disturbed.outcome.trace.codes.count { it == TraceCode.LISTENER_ERROR })
            assertFalse(TraceCode.LISTENER_ERROR in baseline.outcome.trace.codes)
            assertFalse(TraceCode.LISTENER_ERROR in withoutListener.outcome.trace.codes)
            assertEquals(reference.events.size, throwing.events.size)
            assertEquals(kinds(reference.events), kinds(throwing.events))
        }
    }

    @Test
    fun aRunWithoutAListenerBehavesTheSame() = runTest {
        NoNetworkGuard.during {
            val result = twoTierRun(null)

            assertTrue(result.outcome is CommandOutcome.Completed)
            assertEquals(3, result.outcome.executed.size)
            assertEquals(1, result.committed.applyCount)
            assertEquals(0, result.held.applyCount)
        }
    }
}
