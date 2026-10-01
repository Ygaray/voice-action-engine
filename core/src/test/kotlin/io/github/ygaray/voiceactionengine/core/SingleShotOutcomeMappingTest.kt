package io.github.ygaray.voiceactionengine.core

import io.github.ygaray.voiceactionengine.core.commit.StepResult
import io.github.ygaray.voiceactionengine.core.commit.ToolStep
import io.github.ygaray.voiceactionengine.core.failure.EscalationReason
import io.github.ygaray.voiceactionengine.core.failure.FailureDetails
import io.github.ygaray.voiceactionengine.core.failure.FailureReason
import io.github.ygaray.voiceactionengine.core.pipeline.CommandOutcome
import io.github.ygaray.voiceactionengine.core.provider.ModelResult
import io.github.ygaray.voiceactionengine.core.strategy.CommandStrategy
import io.github.ygaray.voiceactionengine.core.strategy.Extraction
import io.github.ygaray.voiceactionengine.core.strategy.Resolution
import io.github.ygaray.voiceactionengine.core.strategy.StrategyOutcome
import io.github.ygaray.voiceactionengine.core.strategy.singleshot.SingleShotStrategy
import io.github.ygaray.voiceactionengine.core.telemetry.TraceCode
import io.github.ygaray.voiceactionengine.core.telemetry.Usage
import io.github.ygaray.voiceactionengine.core.testing.FakeAiProvider
import io.github.ygaray.voiceactionengine.core.testing.FakeMutation
import io.github.ygaray.voiceactionengine.core.testing.NoNetworkGuard
import io.github.ygaray.voiceactionengine.core.testing.ProviderStep
import io.github.ygaray.voiceactionengine.core.testing.RecordingCommitSink
import io.github.ygaray.voiceactionengine.core.testing.ScriptedGate
import io.github.ygaray.voiceactionengine.core.testing.ScriptedStrategy
import io.github.ygaray.voiceactionengine.core.transcript.AssistantPart
import io.github.ygaray.voiceactionengine.core.transcript.ModelResponse
import io.github.ygaray.voiceactionengine.core.transcript.StopReason
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

private const val APP_RULE = "app_rule"

/** Every provider result and every resolution has one specific default outcome, and a tier can override two of them. */
class SingleShotOutcomeMappingTest {

    private class Run(
        val outcome: CommandOutcome,
        val resolver: RecordingResolver,
        val gate: ScriptedGate,
        val fake: FakeAiProvider,
    )

    private fun usage() = Usage(1, 0, 0, 1)

    private fun toolCall(name: String = ENTRIES_TOOL): AssistantPart.ToolCall =
        callOf("call-$name", name, entriesArguments("first"))

    private fun noMatch(): suspend (Extraction, CommandInput) -> Resolution = { _, _ -> Resolution.NoMatch() }

    private fun nextTier(): ScriptedStrategy =
        ScriptedStrategy(StrategyId("next"), { _, _ -> StrategyOutcome.Completed("next") })

    private suspend fun ladder(
        results: List<ProviderStep>,
        answer: suspend (Extraction, CommandInput) -> Resolution = noMatch(),
        next: List<CommandStrategy> = emptyList(),
        configure: SingleShotStrategy.Builder.() -> Unit = {},
    ): Run {
        val resolver = RecordingResolver(answer)
        val fake = FakeAiProvider(ProviderId.ANTHROPIC, results)
        val gate = ScriptedGate.admitAll()
        val tier = singleShot(resolver, snapshotOf(entriesTool(), askTool()), configure = configure)
        val pipeline = pipelineOf(listOf(tier) + next, fake, gate, RecordingCommitSink())
        return Run(pipeline.execute(CommandInput("add two things", "en", null)), resolver, gate, fake)
    }

    private suspend fun ladderOf(
        result: ModelResult,
        answer: suspend (Extraction, CommandInput) -> Resolution = noMatch(),
        next: List<CommandStrategy> = emptyList(),
        configure: SingleShotStrategy.Builder.() -> Unit = {},
    ): Run = ladder(listOf<ProviderStep>({ result }), answer, next, configure)

    private fun failedReason(run: Run): FailureReason = (run.outcome as CommandOutcome.Failed).reason

    private fun assertUntouched(run: Run) {
        assertEquals(0, run.resolver.invocations)
        assertEquals(0, run.gate.calls)
    }

    @Test
    fun noToolCallFailureEscalatesAndTheNextTierRunsOnceWithNoCarry() = runTest {
        NoNetworkGuard.during {
            val next = nextTier()

            val run = ladderOf(ModelResult.Failure(FailureReason.NoToolCall()), next = listOf(next))

            assertTrue(run.outcome.toString(), run.outcome is CommandOutcome.Completed)
            assertEquals(1, next.executions)
            assertEquals(listOf<Any?>(null), next.receivedCarries)
        }
    }

    @Test
    fun noToolCallFailureOnAOneTierLadderIsUnhandledWithTheReason() = runTest {
        NoNetworkGuard.during {
            val run = ladderOf(ModelResult.Failure(FailureReason.NoToolCall()))

            val outcome = run.outcome as CommandOutcome.Unhandled
            assertEquals(EscalationReason.NoToolCall(), outcome.lastReason)
        }
    }

    @Test
    fun refusalFailureIsAFailedRefusalWithNoEscalation() = runTest {
        NoNetworkGuard.during {
            val next = nextTier()

            val run = ladderOf(ModelResult.Failure(FailureReason.Refusal()), next = listOf(next))

            assertEquals(FailureReason.Refusal(), failedReason(run))
            assertEquals(0, next.executions)
        }
    }

    @Test
    fun anyOtherFailurePassesReasonAndDetailsThroughUnchanged() = runTest {
        NoNetworkGuard.during {
            val details = FailureDetails(429, "rate_limit", "req-1")

            val run = ladderOf(ModelResult.Failure(FailureReason.RateLimited(), details))

            assertEquals(FailureReason.RateLimited(), failedReason(run))
            assertSame(details, (run.outcome as CommandOutcome.Failed).details)
        }
    }

    @Test
    fun malformedToolArgsFromTheProviderPassesThroughAndTheResolverIsNotInvoked() = runTest {
        NoNetworkGuard.during {
            val run = ladderOf(ModelResult.Failure(FailureReason.MalformedToolArgs()))

            assertEquals(FailureReason.MalformedToolArgs(), failedReason(run))
            assertUntouched(run)
        }
    }

    @Test
    fun aRefusalStopWithAToolCallPresentFailsBeforeTheCallIsRead() = runTest {
        NoNetworkGuard.during {
            val run = ladderOf(answerOf(StopReason.REFUSAL, toolCall()))

            assertEquals(FailureReason.Refusal(), failedReason(run))
            assertUntouched(run)
        }
    }

    @Test
    fun theFakeProvidersRefusalAnswerFailsWithRefusal() = runTest {
        NoNetworkGuard.during {
            val run = ladderOf(FakeAiProvider.refusal(usage()))

            assertEquals(FailureReason.Refusal(), failedReason(run))
            assertUntouched(run)
        }
    }

    @Test
    fun truncationAndPauseStopsFailBeforeTheResolver() = runTest {
        NoNetworkGuard.during {
            val expected = listOf(
                StopReason.MAX_TOKENS to FailureReason.MaxTokens(),
                StopReason.PAUSE_TURN to FailureReason.PauseTurn(),
                StopReason.CONTEXT_WINDOW_EXCEEDED to FailureReason.ContextWindowExceeded(),
            )

            expected.forEach { (stop, reason) ->
                val run = ladderOf(answerOf(stop, toolCall()))

                assertEquals(stop.toString(), reason, failedReason(run))
                assertUntouched(run)
            }
        }
    }

    @Test
    fun proseThatEndedTheTurnEscalatesWithNoToolCall() = runTest {
        NoNetworkGuard.during {
            val run = ladderOf(FakeAiProvider.reply("sorry", usage()))

            assertEquals(EscalationReason.NoToolCall(), (run.outcome as CommandOutcome.Unhandled).lastReason)
            assertUntouched(run)
        }
    }

    @Test
    fun aToolUseStopWithNoToolCallIsAMalformedResponse() = runTest {
        NoNetworkGuard.during {
            val run = ladderOf(answerOf(StopReason.TOOL_USE))

            assertEquals(FailureReason.MalformedResponse(), failedReason(run))
            assertUntouched(run)
        }
    }

    @Test
    fun anyOtherStopWithNoToolCallIsAnUnknownStop() = runTest {
        NoNetworkGuard.during {
            val run = ladderOf(answerOf(StopReason.OTHER))

            assertEquals(FailureReason.UnknownStop(), failedReason(run))
            assertUntouched(run)
        }
    }

    @Test
    fun aCallToAToolTheSnapshotNeverOfferedEscalatesWithMalformedExtraction() = runTest {
        NoNetworkGuard.during {
            val run = ladderOf(answerOf(StopReason.TOOL_USE, toolCall("not_offered")))

            val outcome = run.outcome as CommandOutcome.Unhandled
            assertEquals(EscalationReason.MalformedExtraction(), outcome.lastReason)
            assertUntouched(run)
        }
    }

    @Test
    fun extraToolCallsAreDroppedAndRecordedOnceWhileTheTurnStillListsAllOfThem() = runTest {
        NoNetworkGuard.during {
            val first = callOf("call-1", ENTRIES_TOOL, entriesArguments("first"))
            val second = callOf("call-2", ENTRIES_TOOL, entriesArguments("second"))

            val run = ladderOf(FakeAiProvider.toolCalls(usage(), first, second))

            assertEquals(1, run.resolver.invocations)
            assertEquals(first.arguments, run.resolver.extractions.single().arguments)
            assertEquals(1, run.outcome.trace.codes.count { it == TraceCode.EXTRA_TOOL_CALLS_DROPPED })
            assertEquals(2, run.outcome.trace.attempts.single().turns.single().toolNames.size)
        }
    }

    @Test
    fun aSingleCallRecordsNoDroppedCallsCode() = runTest {
        NoNetworkGuard.during {
            val run = ladderOf(FakeAiProvider.toolCall("call-1", ENTRIES_TOOL, entriesArguments("a"), usage()))

            assertEquals(1, run.resolver.invocations)
            assertTrue(run.outcome.trace.codes.none { it == TraceCode.EXTRA_TOOL_CALLS_DROPPED })
        }
    }

    private fun oneCall(): ModelResult = FakeAiProvider.toolCall("call-1", ENTRIES_TOOL, entriesArguments("a"), usage())

    @Test
    fun noMatchIsUnhandledOnOneTierAndRunsTheNextTierOnTwo() = runTest {
        NoNetworkGuard.during {
            val alone = ladderOf(oneCall())
            val next = nextTier()
            val withNext = ladderOf(oneCall(), next = listOf(next))

            assertNull((alone.outcome as CommandOutcome.Unhandled).lastReason)
            assertEquals(1, next.executions)
            assertTrue(withNext.outcome.toString(), withNext.outcome is CommandOutcome.Completed)
        }
    }

    @Test
    fun anEscalationFromTheResolverHandsTheSameCarryToTheNextTier() = runTest {
        NoNetworkGuard.during {
            val carry = Any()
            val next = nextTier()

            ladderOf(
                oneCall(),
                answer = { _, _ -> Resolution.Escalate(EscalationReason.ResolverAmbiguous(), carry) },
                next = listOf(next),
            )

            assertSame(carry, next.receivedCarries.single())
        }
    }

    @Test
    fun aFailureFromTheResolverPassesItsReasonAndDetailsThrough() = runTest {
        NoNetworkGuard.during {
            val details = FailureDetails(null, "app_error", null)

            val run = ladderOf(
                oneCall(),
                answer = { _, _ -> Resolution.Failed(FailureReason.Other(APP_RULE), details) },
            )

            assertEquals(FailureReason.Other(APP_RULE), failedReason(run))
            assertSame(details, (run.outcome as CommandOutcome.Failed).details)
        }
    }

    @Test
    fun anUnknownResolutionKindFailsWithUnknownResolution() = runTest {
        NoNetworkGuard.during {
            val run = ladderOf(oneCall(), answer = { _, _ -> object : Resolution() {} })

            assertEquals(FailureReason.Other("unknown_resolution"), failedReason(run))
        }
    }

    @Test
    fun anUnknownProviderResultKindFailsWithUnknownModelResult() = runTest {
        NoNetworkGuard.during {
            val run = ladderOf(object : ModelResult() {})

            assertEquals(FailureReason.Other("unknown_model_result"), failedReason(run))
            assertUntouched(run)
        }
    }

    @Test
    fun aThrowingResolverCollapsesToUnexpectedWithAStrategyErrorCode() = runTest {
        NoNetworkGuard.during {
            val run = ladderOf(oneCall(), answer = { _, _ -> error("resolver blew up") })

            assertTrue(failedReason(run).toString(), failedReason(run) is FailureReason.Unexpected)
            assertTrue(run.outcome.trace.codes.contains(TraceCode.STRATEGY_ERROR))
        }
    }

    @Test
    fun aNoToolCallHookReplacesTheDefaultAndGetsTheResponseOnASuccess() = runTest {
        NoNetworkGuard.during {
            var seen: ModelResponse? = null

            val run = ladderOf(FakeAiProvider.reply("sorry", usage())) {
                onNoToolCall = { response ->
                    seen = response
                    StrategyOutcome.Completed("try again")
                }
            }

            assertEquals("try again", (run.outcome as CommandOutcome.Completed).reply)
            assertNotNull(seen)
        }
    }

    @Test
    fun aNoToolCallHookGetsNullWhenTheProviderReportedAFailure() = runTest {
        NoNetworkGuard.during {
            var called = false
            var seen: ModelResponse? = null

            ladderOf(ModelResult.Failure(FailureReason.NoToolCall())) {
                onNoToolCall = { response ->
                    called = true
                    seen = response
                    StrategyOutcome.Completed("again")
                }
            }

            assertTrue(called)
            assertNull(seen)
        }
    }

    @Test
    fun aRefusalHookReplacesTheDefaultAndTheNextTierRuns() = runTest {
        NoNetworkGuard.during {
            val next = nextTier()
            var seen: ModelResponse? = null

            val run = ladderOf(FakeAiProvider.refusal(usage()), next = listOf(next)) {
                onRefusal = { response ->
                    seen = response
                    StrategyOutcome.Escalate(EscalationReason.ModelDeclined())
                }
            }

            assertEquals(1, next.executions)
            assertTrue(run.outcome.toString(), run.outcome is CommandOutcome.Completed)
            assertNotNull(seen)
        }
    }

    @Test
    fun aRefusalHookGetsNullWhenTheProviderReportedAFailure() = runTest {
        NoNetworkGuard.during {
            var called = false
            var seen: ModelResponse? = null

            ladderOf(ModelResult.Failure(FailureReason.Refusal())) {
                onRefusal = { response ->
                    called = true
                    seen = response
                    StrategyOutcome.Failed(FailureReason.Other(APP_RULE))
                }
            }

            assertTrue(called)
            assertNull(seen)
        }
    }

    @Test
    fun oneTiersOverrideNeverChangesASecondSingleShotTier() = runTest {
        NoNetworkGuard.during {
            val resolver = RecordingResolver(noMatch())
            val fake = FakeAiProvider(
                ProviderId.ANTHROPIC,
                FakeAiProvider.reply("first prose", usage()),
                FakeAiProvider.reply("second prose", usage()),
            )
            val overridden = singleShot(resolver, snapshotOf(entriesTool()), id = "first") {
                onNoToolCall = { StrategyOutcome.Escalate(EscalationReason.ModelDeclined()) }
            }
            val defaults = singleShot(resolver, snapshotOf(entriesTool()), id = "second")
            val tiers = listOf(overridden, defaults)
            val pipeline = pipelineOf(tiers, fake, ScriptedGate.admitAll(), RecordingCommitSink())

            val outcome = pipeline.execute(CommandInput("add two things", "en", null))

            assertEquals(2, fake.callCount)
            assertEquals(EscalationReason.NoToolCall(), (outcome as CommandOutcome.Unhandled).lastReason)
        }
    }

    @Test
    fun aWriteIsNeverMadeOnAnyNonResolutionPath() = runTest {
        NoNetworkGuard.during {
            val write = FakeMutation(ENTRIES_TOOL, StepResult("saved"))
            val results = listOf(
                answerOf(StopReason.REFUSAL, toolCall()),
                answerOf(StopReason.MAX_TOKENS, toolCall()),
                ModelResult.Failure(FailureReason.Timeout()),
            )

            results.forEach { result ->
                val run = ladderOf(result, answer = { _, _ -> Resolution.Steps(listOf(ToolStep.Mutation(write))) })

                assertUntouched(run)
            }
            assertEquals(0, write.applyCount)
        }
    }
}
