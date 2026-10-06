package io.github.ygaray.voiceactionengine.core

import io.github.ygaray.voiceactionengine.core.pipeline.CommandOutcome
import io.github.ygaray.voiceactionengine.core.pipeline.TierSelector
import io.github.ygaray.voiceactionengine.core.provider.ProviderSelection
import io.github.ygaray.voiceactionengine.core.strategy.StrategyCapabilities
import io.github.ygaray.voiceactionengine.core.strategy.StrategyOutcome
import io.github.ygaray.voiceactionengine.core.telemetry.PipelineEvent
import io.github.ygaray.voiceactionengine.core.telemetry.TraceCode
import io.github.ygaray.voiceactionengine.core.telemetry.Usage
import io.github.ygaray.voiceactionengine.core.testing.FakeAiProvider
import io.github.ygaray.voiceactionengine.core.testing.NoNetworkGuard
import io.github.ygaray.voiceactionengine.core.testing.RecordingEventListener
import io.github.ygaray.voiceactionengine.core.testing.ScriptedPicker
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StartTierPickerTest {
    private val input = CommandInput("turn it on", "en", null)

    @Test
    fun aCustomPickerChoosesWhereTheLlmWalkStarts() = runTest {
        NoNetworkGuard.during {
            val grammar = zeroCallTier("grammar", StrategyOutcome.NoMatch())
            val single = llmTier("single") { _, _ -> StrategyOutcome.Completed("s") }
            var tokensSeenByAgentic = -1L
            val agentic = llmTier("agentic") { _, session ->
                tokensSeenByAgentic = session.tokensUsed
                StrategyOutcome.Completed("a")
            }
            val picker = ScriptedPicker({ _, _, ctx ->
                ctx.model().complete(pickTurnRequest())
                StrategyId("agentic")
            })
            val fake = FakeAiProvider(ProviderId.ANTHROPIC, FakeAiProvider.reply("p", Usage(4, 0, 0, 1)))

            val outcome = startTierPipeline(listOf(grammar, single, agentic), TierSelector.Custom(picker), fake)
                .execute(input)

            assertEquals("a", (outcome as CommandOutcome.Completed).reply)
            assertEquals(1, picker.calls)
            assertEquals(idsOf("single", "agentic"), picker.eligibleSeen.single())
            assertEquals(input.transcript, picker.inputsSeen.single().transcript)
            assertEquals(listOf(1, 0, 1), listOf(grammar, single, agentic).map { it.executions })
            assertEquals(listOf("grammar", "agentic"), outcome.trace.attempts.map { it.strategy.value })
            assertFalse(outcome.trace.attempts.last().carryIn)
            assertEquals(5L, tokensSeenByAgentic)
            assertEquals(1, fake.callCount)
            assertFalse(outcome.trace.codes.contains(TraceCode.ROUTER_FALLBACK))
            val selection = outcome.trace.selection!!
            assertEquals(StrategyId("start_tier_picker"), selection.picker)
            assertEquals("picked", selection.outcome)
            assertEquals(StrategyId("agentic"), selection.picked)
            assertEquals(idsOf("single", "agentic"), selection.eligible)
            assertEquals(1, selection.tiersBypassed)
            assertEquals(1, selection.turns.size)
            assertEquals(ProviderId.ANTHROPIC, selection.turns.single().provider)
            assertEquals(5L, selection.usage.total)
            assertTrue(selection.latencyMillis >= 0)
            assertEquals(5L, outcome.trace.usage.total)
        }
    }

    @Test
    fun pickingTheFirstLlmTierBypassesNothing() = runTest {
        NoNetworkGuard.during {
            val single = llmTier("single") { _, _ -> StrategyOutcome.Completed("s") }
            val agentic = llmTier("agentic") { _, _ -> StrategyOutcome.Completed("a") }
            val picker = ScriptedPicker({ _, _, _ -> StrategyId("single") })
            val fake = FakeAiProvider(ProviderId.ANTHROPIC, FakeAiProvider.reply("p", Usage(4, 0, 0, 1)))

            val outcome = startTierPipeline(listOf(single, agentic), TierSelector.Custom(picker), fake).execute(input)

            assertEquals("s", (outcome as CommandOutcome.Completed).reply)
            val selection = outcome.trace.selection!!
            assertEquals(0, selection.tiersBypassed)
            assertEquals(StrategyId("single"), selection.picked)
            assertEquals("picked", selection.outcome)
            assertEquals(listOf(1, 0), listOf(single, agentic).map { it.executions })
        }
    }

    @Test
    fun aNullPickIsRecordedAsAFallbackSelection() = runTest {
        NoNetworkGuard.during {
            val single = llmTier("single") { _, _ -> StrategyOutcome.Completed("s") }
            val agentic = llmTier("agentic") { _, _ -> StrategyOutcome.Completed("a") }
            val picker = ScriptedPicker({ _, _, _ -> null })
            val fake = FakeAiProvider(ProviderId.ANTHROPIC, FakeAiProvider.reply("p", Usage(4, 0, 0, 1)))

            val outcome = startTierPipeline(listOf(single, agentic), TierSelector.Custom(picker), fake).execute(input)

            assertEquals("s", (outcome as CommandOutcome.Completed).reply)
            val selection = outcome.trace.selection!!
            assertEquals("router_fallback", selection.outcome)
            assertNull(selection.picked)
            assertEquals(0, selection.tiersBypassed)
            assertTrue(outcome.trace.codes.contains(TraceCode.ROUTER_FALLBACK))
            assertEquals(listOf(1, 0), listOf(single, agentic).map { it.executions })
        }
    }

    @Test
    fun thePickerTurnIsInTheSelectionNotInAnyAttempt() = runTest {
        NoNetworkGuard.during {
            val grammar = zeroCallTier("grammar", StrategyOutcome.NoMatch())
            val agentic = llmTier("agentic") { _, _ -> StrategyOutcome.Completed("a") }
            val picker = ScriptedPicker({ _, _, ctx ->
                ctx.model().complete(pickTurnRequest())
                StrategyId("agentic")
            })
            val fake = FakeAiProvider(ProviderId.ANTHROPIC, FakeAiProvider.reply("p", Usage(4, 0, 0, 1)))
            val listener = RecordingEventListener()

            val outcome = startTierPipeline(listOf(grammar, agentic), TierSelector.Custom(picker), fake, listener)
                .execute(input)

            val trace = outcome.trace
            assertEquals(listOf(0, 0), trace.attempts.map { it.turns.size })
            assertEquals(0L, trace.attempts.sumOf { it.usage.total })
            assertEquals(5L, trace.selection!!.usage.total)
            assertEquals(5L, trace.usage.total)
            assertEquals(1, listener.events.count { it is PipelineEvent.ProviderCall })
            assertTrue(trace.attempts.none { it.strategy == StrategyId("start_tier_picker") })
        }
    }

    @Test
    fun anAppNamesItsPickerAndTheSelectionSeamBindsItsModel() = runTest {
        NoNetworkGuard.during {
            val grammar = zeroCallTier("grammar", StrategyOutcome.NoMatch())
            val single = llmTier("single") { _, _ -> StrategyOutcome.Completed("s") }
            val agentic = llmTier("agentic") { _, session ->
                session.model().complete(pickTurnRequest())
                StrategyOutcome.Completed("a")
            }
            val picker = ScriptedPicker({ _, _, ctx ->
                ctx.model().complete(pickTurnRequest())
                StrategyId("agentic")
            })
            val selector = TierSelector.Custom(picker) {
                id = StrategyId("app_picker")
                capabilities = StrategyCapabilities(setOf(ProviderId.ANTHROPIC))
            }
            val selection = MappedSelection(
                mapOf("app_picker" to ProviderSelection(ProviderId.ANTHROPIC, "picker-model")),
                ProviderSelection(ProviderId.ANTHROPIC, "test-model"),
            )
            val fake = FakeAiProvider(ProviderId.ANTHROPIC, FakeAiProvider.reply("p", Usage(4, 0, 0, 1)),
                FakeAiProvider.reply("q", Usage(4, 0, 0, 1)))

            val outcome = startTierPipeline(listOf(grammar, single, agentic), selector, fake, selection = selection)
                .execute(input)

            assertEquals("a", (outcome as CommandOutcome.Completed).reply)
            assertEquals(1, selection.requested.count { it == StrategyId("app_picker") })
            assertEquals(idsOf("app_picker", "agentic"), selection.requested)
            val chosen = outcome.trace.selection!!
            assertEquals(StrategyId("app_picker"), chosen.picker)
            assertEquals("picker-model", chosen.turns.single().model)
            assertEquals("picker-model", fake.calls.first().model)
        }
    }

    @Test
    fun anUnmappedPickerIsLoudButNeverFailsTheCommand() = runTest {
        NoNetworkGuard.during {
            val single = llmTier("single") { _, session ->
                session.model().complete(pickTurnRequest())
                StrategyOutcome.Completed("s")
            }
            val agentic = llmTier("agentic") { _, _ -> StrategyOutcome.Completed("a") }
            val picker = ScriptedPicker({ _, _, ctx ->
                if (ctx.model().refusal != null) null else StrategyId("agentic")
            })
            val selection = MappedSelection(
                mapOf("start_tier_picker" to null),
                ProviderSelection(ProviderId.ANTHROPIC, "test-model"),
            )
            val fake = FakeAiProvider(ProviderId.ANTHROPIC, FakeAiProvider.reply("p", Usage(4, 0, 0, 1)))

            val outcome = startTierPipeline(
                listOf(single, agentic),
                TierSelector.Custom(picker),
                fake,
                selection = selection,
            ).execute(input)

            assertEquals("s", (outcome as CommandOutcome.Completed).reply)
            val codes = outcome.trace.codes
            val refused = codes.indexOf(TraceCode.PROVIDER_NOT_SELECTED)
            assertTrue(codes.toString(), refused in 0 until codes.indexOf(TraceCode.ROUTER_FALLBACK))
            assertEquals("router_fallback", outcome.trace.selection!!.outcome)
            assertEquals(listOf(1, 0), listOf(single, agentic).map { it.executions })
            assertEquals(1, fake.callCount)
        }
    }

    private fun kinds(listener: RecordingEventListener) = listener.events.map { it::class }

    @Test
    fun theSelectionIsDeliveredAsAnEventBetweenThePickAndThePickedTier() = runTest {
        NoNetworkGuard.during {
            val grammar = zeroCallTier("grammar", StrategyOutcome.NoMatch())
            val agentic = llmTier("agentic") { _, _ -> StrategyOutcome.Completed("a") }
            val picker = ScriptedPicker({ _, _, ctx ->
                ctx.model().complete(pickTurnRequest())
                StrategyId("agentic")
            })
            val fake = FakeAiProvider(ProviderId.ANTHROPIC, FakeAiProvider.reply("p", Usage(4, 0, 0, 1)))
            val listener = RecordingEventListener()

            val outcome = startTierPipeline(listOf(grammar, agentic), TierSelector.Custom(picker), fake, listener)
                .execute(input)

            assertEquals(
                listOf(
                    PipelineEvent.CommandStarted::class,
                    PipelineEvent.TierStarted::class,
                    PipelineEvent.TierFinished::class,
                    PipelineEvent.ProviderCall::class,
                    PipelineEvent.StartTierSelected::class,
                    PipelineEvent.TierStarted::class,
                    PipelineEvent.TierFinished::class,
                    PipelineEvent.RunClosed::class,
                ),
                kinds(listener),
            )
            val event = listener.events.filterIsInstance<PipelineEvent.StartTierSelected>().single()
            val chosen = outcome.trace.selection!!
            assertEquals(chosen.outcome, event.selection.outcome)
            assertEquals(chosen.picked, event.selection.picked)
            assertEquals(chosen.tiersBypassed, event.selection.tiersBypassed)
            assertFalse(event.toString().contains(input.transcript))
        }
    }

    @Test
    fun aFallbackAfterAPickDeliversTheCodeThenTheSelection() = runTest {
        NoNetworkGuard.during {
            val single = llmTier("single") { _, _ -> StrategyOutcome.Completed("s") }
            val picker = ScriptedPicker({ _, _, ctx ->
                ctx.model().complete(pickTurnRequest())
                StrategyId("nowhere")
            })
            val fake = FakeAiProvider(ProviderId.ANTHROPIC, FakeAiProvider.reply("p", Usage(4, 0, 0, 1)))
            val listener = RecordingEventListener()

            val outcome = startTierPipeline(listOf(single), TierSelector.Custom(picker), fake, listener).execute(input)

            val order = kinds(listener)
            val call = order.indexOf(PipelineEvent.ProviderCall::class)
            val code = order.indexOf(PipelineEvent.EngineCode::class)
            val selected = order.indexOf(PipelineEvent.StartTierSelected::class)
            assertTrue(order.toString(), call in 0 until code && code in 0 until selected)
            assertEquals("router_fallback", outcome.trace.selection!!.outcome)
        }
    }

    @Test
    fun aLinearRunDeliversNoStartTierSelectedEvent() = runTest {
        NoNetworkGuard.during {
            val single = llmTier("single") { _, _ -> StrategyOutcome.Completed("s") }
            val fake = FakeAiProvider(ProviderId.ANTHROPIC, FakeAiProvider.reply("p", Usage(4, 0, 0, 1)))
            val listener = RecordingEventListener()

            val outcome = startTierPipeline(listOf(single), null, fake, listener).execute(input)

            assertTrue(listener.events.none { it is PipelineEvent.StartTierSelected })
            assertNull(outcome.trace.selection)
        }
    }
}
