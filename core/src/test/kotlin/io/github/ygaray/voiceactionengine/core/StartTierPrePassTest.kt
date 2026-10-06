package io.github.ygaray.voiceactionengine.core

import io.github.ygaray.voiceactionengine.core.commit.StepResult
import io.github.ygaray.voiceactionengine.core.commit.ToolStep
import io.github.ygaray.voiceactionengine.core.failure.EscalationReason
import io.github.ygaray.voiceactionengine.core.failure.FailureReason
import io.github.ygaray.voiceactionengine.core.pipeline.CommandOutcome
import io.github.ygaray.voiceactionengine.core.pipeline.TierSelector
import io.github.ygaray.voiceactionengine.core.strategy.Resolution
import io.github.ygaray.voiceactionengine.core.strategy.StrategyCapabilities
import io.github.ygaray.voiceactionengine.core.strategy.StrategyOutcome
import io.github.ygaray.voiceactionengine.core.strategy.grammar.GrammarPack
import io.github.ygaray.voiceactionengine.core.strategy.grammar.LocalGrammarStrategy
import io.github.ygaray.voiceactionengine.core.telemetry.TraceCode
import io.github.ygaray.voiceactionengine.core.testing.FakeAiProvider
import io.github.ygaray.voiceactionengine.core.testing.FakeMutation
import io.github.ygaray.voiceactionengine.core.testing.NoNetworkGuard
import io.github.ygaray.voiceactionengine.core.testing.ScriptedPicker
import io.github.ygaray.voiceactionengine.core.testing.ScriptedStrategy
import io.github.ygaray.voiceactionengine.core.testing.StrategyStep
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

private const val LIGHT_ON = "light_on"

/**
 * The tiers that make no model call run first, as a free pre-pass, through the same walk as Linear: their carry and
 * their write-suppression behave exactly as in a Linear run, and the picker only ever sees the model tiers.
 */
class StartTierPrePassTest {
    private val input = CommandInput("turn it on", "en", null)
    private val fake = { FakeAiProvider(ProviderId.ANTHROPIC) }

    private fun noProvider(id: String, step: StrategyStep) =
        ScriptedStrategy(StrategyId(id), StrategyCapabilities.NO_PROVIDER, step)

    private fun write(name: String) = FakeMutation(name, StepResult("done", false, "ok", emptyMap()))

    private fun assertPickerSawOnly(picker: ScriptedPicker, vararg zeroCall: String) {
        val seen = picker.eligibleSeen.flatten().map { it.value }
        zeroCall.forEach { assertFalse("$it must never be offered to the picker", it in seen) }
    }

    @Test
    fun aHeadThatCompletesNeverCallsThePicker() = runTest {
        NoNetworkGuard.during {
            val head = zeroCallTier("grammar", StrategyOutcome.Completed("h"))
            val single = llmTier("single") { _, _ -> StrategyOutcome.Completed("s") }
            val picker = ScriptedPicker(emptyList())

            val outcome = startTierPipeline(listOf(head, single), TierSelector.Custom(picker), fake()).execute(input)

            assertEquals("h", (outcome as CommandOutcome.Completed).reply)
            assertEquals(0, picker.calls)
            assertEquals(0, single.executions)
            assertFalse(TraceCode.ROUTER_FALLBACK in outcome.trace.codes)
            assertNull(outcome.trace.selection)
        }
    }

    @Test
    fun aHeadThatFailsEndsTheCommandWithItsReasonAndNeverCallsThePicker() = runTest {
        NoNetworkGuard.during {
            val reason = FailureReason.NotConfigured(ProviderId.OPENAI)
            val head = zeroCallTier("grammar", StrategyOutcome.Failed(reason))
            val single = llmTier("single") { _, _ -> StrategyOutcome.Completed("s") }
            val picker = ScriptedPicker(emptyList())

            val outcome = startTierPipeline(listOf(head, single), TierSelector.Custom(picker), fake()).execute(input)

            assertEquals(reason, (outcome as CommandOutcome.Failed).reason)
            assertEquals(0, picker.calls)
            assertEquals(0, single.executions)
            assertFalse(TraceCode.ROUTER_FALLBACK in outcome.trace.codes)
            assertNull(outcome.trace.selection)
        }
    }

    @Test
    fun theHeadsEscalationCarryReachesThePickedTierByIdentity() = runTest {
        NoNetworkGuard.during {
            val carry = Any()
            val head = zeroCallTier("grammar", StrategyOutcome.Escalate(EscalationReason.NoToolCall(), carry))
            val single = llmTier("single") { _, _ -> StrategyOutcome.Completed("s") }
            val agentic = llmTier("agentic") { _, _ -> StrategyOutcome.Completed("a") }
            val picker = ScriptedPicker({ _, _, _ -> StrategyId("agentic") })

            val outcome = startTierPipeline(listOf(head, single, agentic), TierSelector.Custom(picker), fake())
                .execute(input)

            assertEquals("a", (outcome as CommandOutcome.Completed).reply)
            assertSame(carry, agentic.receivedCarries.single())
            assertTrue(outcome.trace.attempts.last().carryIn)
            assertEquals(0, single.executions)
            assertEquals(1, outcome.trace.selection!!.tiersBypassed)
            assertPickerSawOnly(picker, "grammar")
        }
    }

    @Test
    fun everyZeroCallHeadTierRunsBeforeThePickerAndTheLastCarryIsPassedOn() = runTest {
        NoNetworkGuard.during {
            val carry = Any()
            val grammar = zeroCallTier("grammar", StrategyOutcome.NoMatch())
            val rules = zeroCallTier("local_rules", StrategyOutcome.Escalate(EscalationReason.NoToolCall(), carry))
            val single = llmTier("single") { _, _ -> StrategyOutcome.Completed("s") }
            val agentic = llmTier("agentic") { _, _ -> StrategyOutcome.Completed("a") }
            val picker = ScriptedPicker({ _, _, _ -> StrategyId("agentic") })

            val outcome = startTierPipeline(
                listOf(grammar, rules, single, agentic),
                TierSelector.Custom(picker),
                fake(),
            ).execute(input)

            assertEquals("a", (outcome as CommandOutcome.Completed).reply)
            assertEquals(
                listOf("grammar", "local_rules", "agentic"),
                outcome.trace.attempts.map { it.strategy.value },
            )
            assertEquals(idsOf("single", "agentic"), picker.eligibleSeen.single())
            assertSame(carry, agentic.receivedCarries.single())
            assertEquals(1, grammar.executions)
            assertEquals(1, rules.executions)
            assertPickerSawOnly(picker, "grammar", "local_rules")
        }
    }

    @Test
    fun aHeadThatWroteThenEscalatesIsSuppressedAndThePickerIsNeverCalled() = runTest {
        NoNetworkGuard.during {
            val mutation = write("save")
            val head = noProvider("grammar") { _, session ->
                session.submit(ToolStep.Mutation(mutation))
                StrategyOutcome.Escalate(EscalationReason.NoToolCall(), "carry")
            }
            val single = llmTier("single") { _, _ -> StrategyOutcome.Completed("s") }
            val picker = ScriptedPicker(emptyList())

            val outcome = startTierPipeline(listOf(head, single), TierSelector.Custom(picker), fake()).execute(input)

            val completed = outcome as CommandOutcome.Completed
            assertTrue(completed.partial)
            assertNull(completed.reply)
            assertEquals(1, mutation.applyCount)
            assertEquals("escalation_suppressed", outcome.trace.attempts.single().outcome)
            assertTrue(TraceCode.ESCALATION_SUPPRESSED in outcome.trace.codes)
            assertEquals(0, picker.calls)
            assertEquals(0, single.executions)
            assertNull(outcome.trace.selection)
        }
    }

    @Test
    fun aPickedTierThatWroteThenEscalatesIsSuppressedAndNoLaterTierRuns() = runTest {
        NoNetworkGuard.during {
            val mutation = write("save")
            val head = zeroCallTier("grammar", StrategyOutcome.NoMatch())
            val single = llmTier("single") { _, session ->
                session.submit(ToolStep.Mutation(mutation))
                StrategyOutcome.Escalate(EscalationReason.ModelDeclined(), "carry")
            }
            val agentic = llmTier("agentic") { _, _ -> StrategyOutcome.Completed("a") }
            val picker = ScriptedPicker({ _, _, _ -> StrategyId("single") })

            val outcome = startTierPipeline(listOf(head, single, agentic), TierSelector.Custom(picker), fake())
                .execute(input)

            assertTrue((outcome as CommandOutcome.Completed).partial)
            assertEquals(1, mutation.applyCount)
            assertEquals(0, agentic.executions)
            assertEquals("escalation_suppressed", outcome.trace.attempts.last().outcome)
            assertTrue(TraceCode.ESCALATION_SUPPRESSED in outcome.trace.codes)
            assertEquals(1, picker.calls)
        }
    }

    @Test
    fun aMidLadderZeroCallTierIsNeverOffered() = runTest {
        NoNetworkGuard.during {
            val grammar = zeroCallTier("grammar", StrategyOutcome.NoMatch())
            val single = llmTier("single") { _, _ -> StrategyOutcome.Completed("s") }
            val local = zeroCallTier("local", StrategyOutcome.NoMatch())
            val agentic = llmTier("agentic") { _, _ -> StrategyOutcome.Completed("a") }
            val picker = ScriptedPicker({ _, _, _ -> StrategyId("agentic") })

            val outcome = startTierPipeline(
                listOf(grammar, single, local, agentic),
                TierSelector.Custom(picker),
                fake(),
            ).execute(input)

            assertEquals(idsOf("single", "agentic"), picker.eligibleSeen.single())
            assertPickerSawOnly(picker, "grammar", "local")
            assertEquals("a", (outcome as CommandOutcome.Completed).reply)
        }
    }

    private fun grammarHead(): LocalGrammarStrategy {
        val pack = GrammarPack {
            intent(LIGHT_ON) {
                en("turn on the light")
                es("enciende la luz")
            }
        }
        val resolver = RecordingResolver { extraction, _ ->
            Resolution.Steps(listOf(ToolStep.Mutation(FakeMutation(extraction.toolName, StepResult("saved")))))
        }
        return LocalGrammarStrategy(StrategyId("grammar")) {
            this.pack = pack
            this.resolver = resolver
        }
    }

    @Test
    fun aMatchingPhraseOnTheRealGrammarHeadCompletesWithNoPickerAndNoProviderCall() = runTest {
        NoNetworkGuard.during {
            val single = llmTier("single") { _, _ -> StrategyOutcome.Completed("s") }
            val agentic = llmTier("agentic") { _, _ -> StrategyOutcome.Completed("a") }
            val picker = ScriptedPicker(emptyList())
            val provider = fake()

            val outcome = startTierPipeline(
                listOf(grammarHead(), single, agentic),
                TierSelector.Custom(picker),
                provider,
            ).execute(CommandInput("turn on the light", "en", null))

            assertTrue(outcome.toString(), outcome is CommandOutcome.Completed)
            assertEquals(0, picker.calls)
            assertEquals(0, provider.callCount)
            assertEquals(0, single.executions + agentic.executions)
            assertNull(outcome.trace.selection)
            assertFalse(TraceCode.ROUTER_FALLBACK in outcome.trace.codes)
        }
    }

    @Test
    fun aPhraseTheRealGrammarHeadDoesNotKnowCallsThePickerWithOnlyTheModelTierIds() = runTest {
        NoNetworkGuard.during {
            val single = llmTier("single") { _, _ -> StrategyOutcome.Completed("s") }
            val agentic = llmTier("agentic") { _, _ -> StrategyOutcome.Completed("a") }
            val picker = ScriptedPicker({ _, _, _ -> StrategyId("agentic") })

            val outcome = startTierPipeline(
                listOf(grammarHead(), single, agentic),
                TierSelector.Custom(picker),
                fake(),
            ).execute(CommandInput("make it brighter", "en", null))

            assertEquals("a", (outcome as CommandOutcome.Completed).reply)
            assertEquals("no_match", outcome.trace.attempts.first().outcome)
            assertEquals("grammar", outcome.trace.attempts.first().strategy.value)
            assertEquals(1, picker.calls)
            assertEquals(idsOf("single", "agentic"), picker.eligibleSeen.single())
            assertPickerSawOnly(picker, "grammar")
            assertEquals(0, single.executions)
        }
    }
}
