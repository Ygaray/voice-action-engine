package io.github.ygaray.voiceactionengine.core

import io.github.ygaray.voiceactionengine.core.commit.StepResult
import io.github.ygaray.voiceactionengine.core.commit.ToolStep
import io.github.ygaray.voiceactionengine.core.failure.EscalationReason
import io.github.ygaray.voiceactionengine.core.failure.FailureReason
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
import io.github.ygaray.voiceactionengine.core.testing.FakeMutation
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
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

        val skips: List<Pair<String, String>>
            get() = listener.events.filterIsInstance<PipelineEvent.TierSkipped>()
                .map { it.strategy.value to it.code.value }
    }

    /** A walk that never asked a picker has no selection record. */
    private fun assertNoSelection(outcome: CommandOutcome) = assertNull(outcome.trace.selection)

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
            assertNoSelection(result.outcome)

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

    private fun completing(id: String, caps: StrategyCapabilities = StrategyCapabilities.ANY_PROVIDER) =
        ScriptedStrategy(StrategyId(id), caps, { _, _ -> StrategyOutcome.Completed("ok") })

    @Test
    fun aCtShapedSingleTierCompletes() = runTest {
        NoNetworkGuard.during {
            val only = ScriptedStrategy(
                StrategyId("only"),
                { _, session ->
                    callModel(session)
                    StrategyOutcome.Completed("ok")
                },
            )

            val result = run(listOf(only))
            assertNoSelection(result.outcome)

            assertEquals("ok", (result.outcome as CommandOutcome.Completed).reply)
            assertEquals(listOf(Triple("only", "completed", false)), result.triples)
            assertEquals(emptyList<String>(), result.codes)
            assertEquals(5L, result.outcome.trace.usage.total)
            assertEquals(1, result.fake.callCount)
            assertEquals(
                listOf(
                    PipelineEvent.CommandStarted::class,
                    PipelineEvent.TierStarted::class,
                    PipelineEvent.ProviderCall::class,
                    PipelineEvent.TierFinished::class,
                    PipelineEvent.RunClosed::class,
                ),
                result.events,
            )
        }
    }

    @Test
    fun aZeroCallHeadThatHandlesTheCommandEndsTheWalk() = runTest {
        NoNetworkGuard.during {
            val grammar = head(StrategyOutcome.Completed("g"))
            val single = single(null)
            val agentic = completing("agentic")

            val result = run(listOf(grammar, single, agentic))
            assertNoSelection(result.outcome)

            assertEquals("g", (result.outcome as CommandOutcome.Completed).reply)
            assertEquals(listOf(Triple("grammar", "completed", false)), result.triples)
            assertEquals(emptyList<String>(), result.codes)
            assertEquals(listOf(1, 0, 0), listOf(grammar, single, agentic).map { it.executions })
            assertEquals(0, result.fake.callCount)
            assertEquals(
                listOf(
                    PipelineEvent.CommandStarted::class,
                    PipelineEvent.TierStarted::class,
                    PipelineEvent.TierFinished::class,
                    PipelineEvent.RunClosed::class,
                ),
                result.events,
            )
        }
    }

    @Test
    fun fixedStartsMidLadderAndClimbs() = runTest {
        NoNetworkGuard.during {
            val carry = Any()
            val grammar = head()
            val single = single(carry)
            val agentic = completing("agentic")

            val result = run(listOf(grammar, single, agentic), selector = TierSelector.Fixed(StrategyId("single")))
            assertNoSelection(result.outcome)

            assertEquals("ok", (result.outcome as CommandOutcome.Completed).reply)
            assertEquals(
                listOf(Triple("single", "escalated", false), Triple("agentic", "completed", true)),
                result.triples,
            )
            assertEquals(emptyList<String>(), result.codes)
            assertEquals(listOf(0, 1, 1), listOf(grammar, single, agentic).map { it.executions })
            assertSame(carry, agentic.receivedCarries.single())
            assertEquals(1, result.fake.callCount)
            assertEquals(
                listOf(
                    PipelineEvent.CommandStarted::class,
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

    @Test
    fun offlineOnlyDropsTheCloudTiersAndIsCappedUnhandled() = runTest {
        NoNetworkGuard.during {
            val grammar = head()
            val single = single(null)
            val agentic = completing("agentic")

            val result = run(listOf(grammar, single, agentic), policy = TierPolicy { offlineOnly = true })
            assertNoSelection(result.outcome)

            val outcome = result.outcome as CommandOutcome.Unhandled
            assertEquals(true, outcome.cappedByPolicy)
            assertEquals(null, outcome.lastReason)
            assertEquals(listOf("tier_skipped_policy", "tier_skipped_policy"), result.codes)
            assertEquals(
                listOf("single" to "tier_skipped_policy", "agentic" to "tier_skipped_policy"),
                result.skips,
            )
            assertEquals(listOf(Triple("grammar", "no_match", false)), result.triples)
            assertEquals(listOf(1, 0, 0), listOf(grammar, single, agentic).map { it.executions })
            assertEquals(0, result.fake.callCount)
            assertEquals(
                listOf(
                    PipelineEvent.CommandStarted::class,
                    PipelineEvent.TierSkipped::class,
                    PipelineEvent.TierSkipped::class,
                    PipelineEvent.TierStarted::class,
                    PipelineEvent.TierFinished::class,
                    PipelineEvent.RunClosed::class,
                ),
                result.events,
            )
        }
    }

    @Test
    fun maxTierCapsTheLadder() = runTest {
        NoNetworkGuard.during {
            val grammar = head()
            val single = single(null)
            val agentic = completing("agentic")

            val result = run(listOf(grammar, single, agentic), policy = TierPolicy { maxTier = StrategyId("single") })
            assertNoSelection(result.outcome)

            val outcome = result.outcome as CommandOutcome.Unhandled
            assertEquals(true, outcome.cappedByPolicy)
            assertTrue(outcome.lastReason is EscalationReason.NoToolCall)
            assertEquals(listOf("tier_skipped_policy"), result.codes)
            assertEquals(listOf("agentic" to "tier_skipped_policy"), result.skips)
            assertEquals(
                listOf(Triple("grammar", "no_match", false), Triple("single", "escalated", false)),
                result.triples,
            )
            assertEquals(listOf(1, 1, 0), listOf(grammar, single, agentic).map { it.executions })
            assertEquals(1, result.fake.callCount)
            assertEquals(
                listOf(
                    PipelineEvent.CommandStarted::class,
                    PipelineEvent.TierSkipped::class,
                    PipelineEvent.TierStarted::class,
                    PipelineEvent.TierFinished::class,
                    PipelineEvent.TierStarted::class,
                    PipelineEvent.ProviderCall::class,
                    PipelineEvent.TierFinished::class,
                    PipelineEvent.RunClosed::class,
                ),
                result.events,
            )
        }
    }

    @Test
    fun aNoMatchStartsTheNextTierFresh() = runTest {
        NoNetworkGuard.during {
            val carry = Any()
            val single = single(carry)
            val mid = ScriptedStrategy(StrategyId("mid"), { _, _ -> StrategyOutcome.NoMatch() })
            val agentic = completing("agentic")

            val result = run(listOf(single, mid, agentic))
            assertNoSelection(result.outcome)

            assertEquals("ok", (result.outcome as CommandOutcome.Completed).reply)
            assertSame(carry, mid.receivedCarries.single())
            assertEquals(null, agentic.receivedCarries.single())
            assertEquals(
                listOf(
                    Triple("single", "escalated", false),
                    Triple("mid", "no_match", true),
                    Triple("agentic", "completed", false),
                ),
                result.triples,
            )
            assertEquals(emptyList<String>(), result.codes)
            assertEquals(
                listOf(
                    PipelineEvent.CommandStarted::class,
                    PipelineEvent.TierStarted::class,
                    PipelineEvent.ProviderCall::class,
                    PipelineEvent.TierFinished::class,
                    PipelineEvent.TierStarted::class,
                    PipelineEvent.TierFinished::class,
                    PipelineEvent.TierStarted::class,
                    PipelineEvent.TierFinished::class,
                    PipelineEvent.RunClosed::class,
                ),
                result.events,
            )
        }
    }

    @Test
    fun anEscalationAfterAWriteIsSuppressed() = runTest {
        NoNetworkGuard.during {
            val write = FakeMutation("write_tool", StepResult("done", false, "ok", emptyMap()))
            val grammar = head()
            val writer = ScriptedStrategy(
                StrategyId("single"),
                { _, session ->
                    session.submit(ToolStep.Mutation(write))
                    StrategyOutcome.Escalate(EscalationReason.NoToolCall(), null)
                },
            )
            val agentic = completing("agentic")

            val result = run(listOf(grammar, writer, agentic))
            assertNoSelection(result.outcome)

            val outcome = result.outcome as CommandOutcome.Completed
            assertEquals(true, outcome.partial)
            assertEquals(null, outcome.reply)
            assertEquals(listOf("escalation_suppressed"), result.codes)
            assertEquals(
                listOf(Triple("grammar", "no_match", false), Triple("single", "escalation_suppressed", false)),
                result.triples,
            )
            assertTrue(outcome.trace.attempts.last().suppressedEscalation is EscalationReason.NoToolCall)
            assertEquals(1, write.applyCount)
            assertEquals(listOf(1, 1, 0), listOf(grammar, writer, agentic).map { it.executions })
            assertEquals(
                listOf(
                    PipelineEvent.CommandStarted::class,
                    PipelineEvent.TierStarted::class,
                    PipelineEvent.TierFinished::class,
                    PipelineEvent.TierStarted::class,
                    PipelineEvent.ActionRecorded::class,
                    PipelineEvent.EngineCode::class,
                    PipelineEvent.TierFinished::class,
                    PipelineEvent.RunClosed::class,
                ),
                result.events,
            )
        }
    }

    @Test
    fun aWholeLadderPolicyRefusalFailsBeforeAnyTier() = runTest {
        NoNetworkGuard.during {
            val single = single(null)
            val agentic = completing("agentic")

            val result = run(listOf(single, agentic), policy = TierPolicy { offlineOnly = true })
            assertNoSelection(result.outcome)

            val outcome = result.outcome as CommandOutcome.Failed
            assertEquals(FailureReason.ProviderUnavailable(ProviderId.ON_DEVICE, "offline_unavailable"), outcome.reason)
            assertEquals(
                listOf("tier_skipped_policy", "tier_skipped_policy", "offline_unavailable"),
                result.codes,
            )
            assertEquals(emptyList<Triple<String, String, Boolean>>(), result.triples)
            assertEquals(listOf(0, 0), listOf(single, agentic).map { it.executions })
            assertEquals(0, result.fake.callCount)
            assertEquals(
                listOf(
                    PipelineEvent.CommandStarted::class,
                    PipelineEvent.TierSkipped::class,
                    PipelineEvent.TierSkipped::class,
                    PipelineEvent.EngineCode::class,
                    PipelineEvent.RunClosed::class,
                ),
                result.events,
            )
        }
    }

    @Test
    fun linearAndFixedStartOnlyAtAnEligibleTier() = runTest {
        NoNetworkGuard.during {
            val policy = TierPolicy { allowedProviders = setOf(ProviderId.ANTHROPIC) }
            val dropped = completing("dropped", StrategyCapabilities(setOf(ProviderId.OPENAI)))
            val kept = completing("kept")
            val last = completing("last")

            val linear = run(listOf(dropped, kept, last), policy = policy)
            assertNoSelection(linear.outcome)

            assertEquals("ok", (linear.outcome as CommandOutcome.Completed).reply)
            assertEquals(listOf(Triple("kept", "completed", false)), linear.triples)
            assertEquals(listOf("tier_skipped_policy"), linear.codes)
            assertEquals(listOf(0, 1, 0), listOf(dropped, kept, last).map { it.executions })

            val fixed = run(
                listOf(dropped, kept, last),
                selector = TierSelector.Fixed(StrategyId("dropped")),
                policy = policy,
            )
            assertNoSelection(fixed.outcome)

            assertEquals(FailureReason.NoEligibleTier(), (fixed.outcome as CommandOutcome.Failed).reason)
            assertEquals(emptyList<Triple<String, String, Boolean>>(), fixed.triples)
            assertEquals(listOf("tier_skipped_policy"), fixed.codes)
            assertEquals(listOf(0, 1, 0), listOf(dropped, kept, last).map { it.executions })
            assertEquals(
                listOf(
                    PipelineEvent.CommandStarted::class,
                    PipelineEvent.TierSkipped::class,
                    PipelineEvent.RunClosed::class,
                ),
                fixed.events,
            )
        }
    }
}
