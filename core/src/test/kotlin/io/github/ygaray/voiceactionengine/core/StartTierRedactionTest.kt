package io.github.ygaray.voiceactionengine.core

import io.github.ygaray.voiceactionengine.core.pipeline.CommandOutcome
import io.github.ygaray.voiceactionengine.core.pipeline.PickContext
import io.github.ygaray.voiceactionengine.core.pipeline.StartTierPicker
import io.github.ygaray.voiceactionengine.core.pipeline.TierSelector
import io.github.ygaray.voiceactionengine.core.provider.ProviderSelection
import io.github.ygaray.voiceactionengine.core.strategy.StrategyOutcome
import io.github.ygaray.voiceactionengine.core.telemetry.Usage
import io.github.ygaray.voiceactionengine.core.testing.FakeAiProvider
import io.github.ygaray.voiceactionengine.core.testing.NoNetworkGuard
import io.github.ygaray.voiceactionengine.core.testing.RecordingEventListener
import io.github.ygaray.voiceactionengine.core.transcript.UserMessage
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val CANARY = "CANARY"
private const val CANARY_TRANSCRIPT = "$CANARY-TRANSCRIPT"
private const val CANARY_DESCRIPTION = "$CANARY-DESC"
private const val CANARY_THROW = "$CANARY-THROW"

/** An app context object whose own text is a canary: printing the object instead of its class name leaks it. */
private class CanaryContext {
    override fun toString(): String = "$CANARY-CONTEXT"
}

/**
 * The new start-tier surfaces (the Router, a custom picker that throws, the selection record, its event and the
 * selectors) never print a transcript, an app context, a tier description or an exception message. The transcript and
 * the descriptions reach only the router's own provider request, which is the positive control.
 */
class StartTierRedactionTest {
    private val input = CommandInput(CANARY_TRANSCRIPT, "en", CanaryContext())

    /** Every string a trace, selection, event, code or selector renders for [outcome]. */
    private fun rendered(
        outcome: CommandOutcome,
        listener: RecordingEventListener,
        selector: TierSelector,
    ): List<String> {
        val trace = outcome.trace
        val strings = mutableListOf(outcome.toString(), trace.toString(), selector.toString())
        trace.selection?.let { selection ->
            strings.add(selection.toString())
            strings.add(selection.picker.toString())
            strings.add(selection.outcome)
            strings.add(selection.eligible.toString())
            selection.turns.forEach { strings.add(it.toString()) }
        }
        trace.attempts.forEach { attempt ->
            strings.add(attempt.toString())
            attempt.turns.forEach { strings.add(it.toString()) }
        }
        trace.codes.forEach { strings.add(it.value) }
        listener.events.forEach { strings.add(it.toString()) }
        return strings
    }

    private fun assertNoCanary(strings: List<String>) {
        assertTrue(strings.isNotEmpty())
        strings.forEach { assertFalse(it, it.contains(CANARY)) }
    }

    @Test
    fun aRouterRunLeaksNoTranscriptContextOrDescription() = runTest {
        NoNetworkGuard.during {
            val head = zeroCallTier("grammar", StrategyOutcome.NoMatch())
            val single = llmTier("single") { _, _ -> StrategyOutcome.Completed("s") }
            val plan = llmTier("plan") { _, _ -> StrategyOutcome.Completed("p") }
            val answer = buildJsonObject { put("tier", "plan") }
            val fake = FakeAiProvider(
                ProviderId.ANTHROPIC,
                FakeAiProvider.toolCall("r1", "pick_start_tier", answer, Usage(4, 0, 0, 1)),
            )
            val selection = MappedSelection(
                mapOf("start_tier_router" to ProviderSelection(ProviderId.ANTHROPIC, "router-model")),
                ProviderSelection(ProviderId.ANTHROPIC, "test-model"),
            )
            val selector = TierSelector.Router {
                tierDescriptions = mapOf(
                    StrategyId("single") to CANARY_DESCRIPTION,
                    StrategyId("plan") to "$CANARY-PLAN",
                )
            }
            val listener = RecordingEventListener()

            val outcome = startTierPipeline(listOf(head, single, plan), selector, fake, listener, selection)
                .execute(input)

            assertEquals("p", (outcome as CommandOutcome.Completed).reply)
            assertEquals(StrategyId("plan"), outcome.trace.selection!!.picked)
            assertNoCanary(rendered(outcome, listener, selector))
            // Positive control: the sweep would see these strings if they leaked; they reach only the router request.
            val userText = (fake.calls.single().request.messages.single() as UserMessage).text
            assertTrue(userText, userText.contains(CANARY_TRANSCRIPT))
            assertTrue(userText, userText.contains(CANARY_DESCRIPTION))
        }
    }

    @Test
    fun aThrowingPickerLeaksNeitherItsMessageNorTheContext() = runTest {
        NoNetworkGuard.during {
            val single = llmTier("single") { _, _ -> StrategyOutcome.Completed("s") }
            val plan = llmTier("plan") { _, _ -> StrategyOutcome.Completed("p") }
            var seenContext = ""
            val picker = StartTierPicker { pickInput: CommandInput, _: List<StrategyId>, ctx: PickContext ->
                seenContext = ctx.toString() + pickInput.toString()
                throw IllegalStateException(CANARY_THROW)
            }
            val selector = TierSelector.Custom(picker)
            val fake = FakeAiProvider(ProviderId.ANTHROPIC)
            val listener = RecordingEventListener()

            val outcome = startTierPipeline(listOf(single, plan), selector, fake, listener).execute(input)

            assertEquals("s", (outcome as CommandOutcome.Completed).reply)
            assertEquals("router_fallback", outcome.trace.selection!!.outcome)
            assertTrue(seenContext, seenContext.isNotEmpty())
            assertNoCanary(rendered(outcome, listener, selector) + seenContext)
        }
    }
}
