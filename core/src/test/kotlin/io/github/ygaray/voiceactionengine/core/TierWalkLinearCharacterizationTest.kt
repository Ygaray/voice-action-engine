package io.github.ygaray.voiceactionengine.core

import io.github.ygaray.voiceactionengine.core.failure.EscalationReason
import io.github.ygaray.voiceactionengine.core.pipeline.CommandOutcome
import io.github.ygaray.voiceactionengine.core.pipeline.TierPolicy
import io.github.ygaray.voiceactionengine.core.pipeline.TierPolicySource
import io.github.ygaray.voiceactionengine.core.pipeline.TierSelector
import io.github.ygaray.voiceactionengine.core.pipeline.commandPipeline
import io.github.ygaray.voiceactionengine.core.provider.ProviderSelection
import io.github.ygaray.voiceactionengine.core.strategy.CommandSession
import io.github.ygaray.voiceactionengine.core.strategy.CommandStrategy
import io.github.ygaray.voiceactionengine.core.strategy.StrategyCapabilities
import io.github.ygaray.voiceactionengine.core.strategy.StrategyOutcome
import io.github.ygaray.voiceactionengine.core.telemetry.PipelineEvent
import io.github.ygaray.voiceactionengine.core.telemetry.Usage
import io.github.ygaray.voiceactionengine.core.testing.FakeAiProvider
import io.github.ygaray.voiceactionengine.core.testing.NoNetworkGuard
import io.github.ygaray.voiceactionengine.core.testing.RecordingCommitSink
import io.github.ygaray.voiceactionengine.core.testing.RecordingEventListener
import io.github.ygaray.voiceactionengine.core.testing.ScriptedGate
import io.github.ygaray.voiceactionengine.core.testing.ScriptedSelectionSource
import io.github.ygaray.voiceactionengine.core.testing.ScriptedStrategy
import io.github.ygaray.voiceactionengine.core.transcript.ModelRequest
import io.github.ygaray.voiceactionengine.core.transcript.UserMessage
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import kotlin.reflect.KClass

/**
 * Pins the v1.0.1 Linear and Fixed walk whole: every attempt (strategy, outcome, carry), the exact code list, the exact
 * event class sequence, the usage total, the provider call count and the identity of a handed-on carry.
 *
 * An app that never sets a selector walks the default Linear ladder, and ROUT-05 promises it keeps walking exactly as
 * before. Any diff here after a selector change is a regression for every app that never opts in. Later plans may only
 * ADD assertions to this file, never change an expected value.
 */
class TierWalkLinearCharacterizationTest {

    private val input = CommandInput("turn it on", "en", null)

    /** What one run produced, with the doubles a test inspects. */
    private class Run(
        val outcome: CommandOutcome,
        val fake: FakeAiProvider,
        val listener: RecordingEventListener,
    ) {
        val triples: List<Triple<String, String, Boolean>>
            get() = outcome.trace.attempts.map { Triple(it.strategy.value, it.outcome, it.carryIn) }

        val codes: List<String> get() = outcome.trace.codes.map { it.value }

        val events: List<KClass<out PipelineEvent>> get() = listener.events.map { it::class }
    }

    private fun fakeProvider(): FakeAiProvider =
        FakeAiProvider(ProviderId.ANTHROPIC, FakeAiProvider.reply("r", Usage(3, 0, 0, 2)))

    private suspend fun run(
        tiers: List<CommandStrategy>,
        selector: TierSelector? = null,
        policy: TierPolicy = TierPolicy.DEFAULT,
    ): Run {
        val fake = fakeProvider()
        val listener = RecordingEventListener()
        val outcome = commandPipeline {
            tiers.forEach { tier(it) }
            provider(fake)
            providerSelection = ScriptedSelectionSource.fixed(ProviderSelection(ProviderId.ANTHROPIC, "test-model"))
            credentials = testKey()
            this.policy = TierPolicySource.fixed(policy)
            if (selector != null) this.selector = selector
            gate = ScriptedGate.admitAll()
            commitSink = RecordingCommitSink()
            this.listener = listener
        }.execute(input)
        return Run(outcome, fake, listener)
    }

    private suspend fun callModel(session: CommandSession) {
        session.model().complete(ModelRequest("sys", listOf(UserMessage(input.transcript)), 16))
    }

    private fun head(outcome: StrategyOutcome = StrategyOutcome.NoMatch()): ScriptedStrategy =
        ScriptedStrategy(StrategyId("grammar"), StrategyCapabilities.NO_PROVIDER, { _, _ -> outcome })

    private fun single(carry: Any?): ScriptedStrategy = ScriptedStrategy(
        StrategyId("single"),
        { _, session ->
            callModel(session)
            StrategyOutcome.Escalate(EscalationReason.NoToolCall(), carry)
        },
    )

    @Test
    fun anSbShapedLinearLadderIsPinnedWhole() = runTest {
        NoNetworkGuard.during {
            val carry = Any()
            val grammar = head()
            val single = single(carry)
            var tokensSeenByAgentic = -1L
            val agentic = ScriptedStrategy(
                StrategyId("agentic"),
                { _, session ->
                    tokensSeenByAgentic = session.tokensUsed
                    StrategyOutcome.Completed("done")
                },
            )

            val result = run(listOf(grammar, single, agentic))

            val outcome = result.outcome as CommandOutcome.Completed
            assertEquals("done", outcome.reply)
            assertEquals(false, outcome.partial)
            assertEquals(
                listOf(
                    Triple("grammar", "no_match", false),
                    Triple("single", "escalated", false),
                    Triple("agentic", "completed", true),
                ),
                result.triples,
            )
            assertEquals(listOf(0, 1, 0), outcome.trace.attempts.map { it.turns.size })
            assertEquals(emptyList<String>(), result.codes)
            assertEquals(5L, outcome.trace.usage.total)
            assertEquals(1, result.fake.callCount)
            assertEquals(5L, tokensSeenByAgentic)
            assertSame(carry, agentic.receivedCarries.single())
            assertEquals(listOf(1, 1, 1), listOf(grammar, single, agentic).map { it.executions })
            assertEquals(
                listOf(
                    PipelineEvent.CommandStarted::class,
                    PipelineEvent.TierStarted::class,
                    PipelineEvent.TierFinished::class,
                    PipelineEvent.TierStarted::class,
                    PipelineEvent.ProviderCall::class,
                    PipelineEvent.TierFinished::class,
                    PipelineEvent.TierStarted::class,
                    PipelineEvent.TierFinished::class,
                    PipelineEvent.RunClosed::class,
                ),
                result.events,
            )
        }
    }
}
