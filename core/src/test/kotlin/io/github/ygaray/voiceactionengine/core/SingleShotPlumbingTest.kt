package io.github.ygaray.voiceactionengine.core

import io.github.ygaray.voiceactionengine.core.pipeline.CommandOutcome
import io.github.ygaray.voiceactionengine.core.pipeline.TierPolicy
import io.github.ygaray.voiceactionengine.core.pipeline.commandPipeline
import io.github.ygaray.voiceactionengine.core.provider.ProviderSelection
import io.github.ygaray.voiceactionengine.core.strategy.StrategyOutcome
import io.github.ygaray.voiceactionengine.core.strategy.ToolSpec
import io.github.ygaray.voiceactionengine.core.telemetry.PipelineEvent
import io.github.ygaray.voiceactionengine.core.telemetry.TraceCode
import io.github.ygaray.voiceactionengine.core.telemetry.Usage
import io.github.ygaray.voiceactionengine.core.testing.FakeAiProvider
import io.github.ygaray.voiceactionengine.core.testing.NoNetworkGuard
import io.github.ygaray.voiceactionengine.core.testing.RecordingCommitSink
import io.github.ygaray.voiceactionengine.core.testing.RecordingEventListener
import io.github.ygaray.voiceactionengine.core.testing.ScriptedCredentialSource
import io.github.ygaray.voiceactionengine.core.testing.ScriptedGate
import io.github.ygaray.voiceactionengine.core.testing.ScriptedSelectionSource
import io.github.ygaray.voiceactionengine.core.testing.ScriptedStrategy
import io.github.ygaray.voiceactionengine.core.testing.StrategyStep
import io.github.ygaray.voiceactionengine.core.transcript.CacheDirective
import io.github.ygaray.voiceactionengine.core.transcript.ModelRequest
import io.github.ygaray.voiceactionengine.core.transcript.ToolChoice
import io.github.ygaray.voiceactionengine.core.transcript.UserMessage
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val TOOL_NAME = "record_entries"

/** The single-tool-call flag and the dropped-calls trace code travel from a strategy through the routed pipeline. */
class SingleShotPlumbingTest {

    private class Run(val outcome: CommandOutcome, val fake: FakeAiProvider, val listener: RecordingEventListener)

    private fun entriesTool(): ToolSpec =
        ToolSpec(TOOL_NAME, "Records entries.", buildJsonObject { put("type", "object") })

    private suspend fun runRouted(fake: FakeAiProvider, step: StrategyStep): Run {
        val listener = RecordingEventListener()
        val strategy = ScriptedStrategy(StrategyId("probe"), step)
        val pipeline = commandPipeline {
            tier(strategy)
            provider(fake)
            providerSelection = ScriptedSelectionSource.fixed(ProviderSelection(ProviderId.ANTHROPIC, "test-model"))
            credentials = ScriptedCredentialSource.keys(ProviderId.ANTHROPIC to "test-key")
            this.listener = listener
            gate = ScriptedGate.admitAll()
            commitSink = RecordingCommitSink()
        }
        val outcome = pipeline.execute(CommandInput("add two things", "en", null))
        return Run(outcome, fake, listener)
    }

    private fun answeringOk(): FakeAiProvider =
        FakeAiProvider(ProviderId.ANTHROPIC, FakeAiProvider.reply("ok", Usage(1, 0, 0, 1)))

    @Test
    fun aSingleToolCallRequestReachesTheProviderWithTheFlagSet() = runTest {
        NoNetworkGuard.during {
            val step: StrategyStep = { _, session ->
                val request = ModelRequest(
                    "fixed system",
                    listOf(UserMessage("add two things")),
                    listOf(entriesTool()),
                    ToolChoice.Required(TOOL_NAME),
                    session.policy.maxTokensPerTurn,
                    CacheDirective(true),
                    true,
                )
                session.model().complete(request)
                StrategyOutcome.Completed(null)
            }

            val run = runRouted(answeringOk(), step)

            assertTrue(run.outcome.toString(), run.outcome is CommandOutcome.Completed)
            val sent = run.fake.calls.single().request
            assertTrue(sent.singleToolCall)
            assertEquals(TierPolicy.DEFAULT.maxTokensPerTurn, sent.maxTokens)
        }
    }

    @Test
    fun aSixArgumentRequestReachesTheProviderWithTheFlagClear() = runTest {
        NoNetworkGuard.during {
            val step: StrategyStep = { _, session ->
                val request = ModelRequest(
                    "fixed system",
                    listOf(UserMessage("add two things")),
                    listOf(entriesTool()),
                    ToolChoice.Required(TOOL_NAME),
                    session.policy.maxTokensPerTurn,
                    CacheDirective(true),
                )
                session.model().complete(request)
                StrategyOutcome.Completed(null)
            }

            val run = runRouted(answeringOk(), step)

            assertTrue(run.outcome.toString(), run.outcome is CommandOutcome.Completed)
            assertFalse(run.fake.calls.single().request.singleToolCall)
        }
    }

    @Test
    fun theDroppedCallsCodeHasItsStableWireValue() {
        assertEquals("extra_tool_calls_dropped", TraceCode.EXTRA_TOOL_CALLS_DROPPED.value)
        assertEquals("extra_tool_calls_dropped", TraceCode.EXTRA_TOOL_CALLS_DROPPED.toString())
    }

    @Test
    fun aRecordedCodeLandsInTheTraceExactlyOnce() = runTest {
        NoNetworkGuard.during {
            val step: StrategyStep = { _, session ->
                session.recordCode(TraceCode.EXTRA_TOOL_CALLS_DROPPED)
                StrategyOutcome.Completed(null)
            }

            val run = runRouted(FakeAiProvider(ProviderId.ANTHROPIC), step)

            assertEquals(listOf(TraceCode.EXTRA_TOOL_CALLS_DROPPED), run.outcome.trace.codes)
        }
    }

    @Test
    fun aRecordedCodeReachesTheListenerOnceWithTheRunId() = runTest {
        NoNetworkGuard.during {
            val step: StrategyStep = { _, session ->
                session.recordCode(TraceCode.EXTRA_TOOL_CALLS_DROPPED)
                StrategyOutcome.Completed(null)
            }

            val run = runRouted(FakeAiProvider(ProviderId.ANTHROPIC), step)

            val events = run.listener.events.filterIsInstance<PipelineEvent.EngineCode>()
            val event = events.single()
            assertEquals(TraceCode.EXTRA_TOOL_CALLS_DROPPED, event.code)
            assertEquals(run.outcome.runId, event.runId)
        }
    }

    @Test
    fun twoRecordedCodesAppearTwiceInCallOrder() = runTest {
        NoNetworkGuard.during {
            val step: StrategyStep = { _, session ->
                session.recordCode(TraceCode.EXTRA_TOOL_CALLS_DROPPED)
                session.recordCode(TraceCode.EXTRA_TOOL_CALLS_DROPPED)
                StrategyOutcome.Completed(null)
            }

            val run = runRouted(FakeAiProvider(ProviderId.ANTHROPIC), step)

            val expected = listOf(TraceCode.EXTRA_TOOL_CALLS_DROPPED, TraceCode.EXTRA_TOOL_CALLS_DROPPED)
            assertEquals(expected, run.outcome.trace.codes)
        }
    }
}
