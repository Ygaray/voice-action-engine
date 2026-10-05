package io.github.ygaray.voiceactionengine.core

import io.github.ygaray.voiceactionengine.core.commit.StepResult
import io.github.ygaray.voiceactionengine.core.commit.ToolStep
import io.github.ygaray.voiceactionengine.core.failure.EscalationReason
import io.github.ygaray.voiceactionengine.core.failure.FailureDetails
import io.github.ygaray.voiceactionengine.core.failure.FailureReason
import io.github.ygaray.voiceactionengine.core.failure.BudgetBound
import io.github.ygaray.voiceactionengine.core.pipeline.CommandOutcome
import io.github.ygaray.voiceactionengine.core.pipeline.TierPolicy
import io.github.ygaray.voiceactionengine.core.provider.ModelResult
import io.github.ygaray.voiceactionengine.core.strategy.CommandStrategy
import io.github.ygaray.voiceactionengine.core.strategy.Extraction
import io.github.ygaray.voiceactionengine.core.strategy.Resolution
import io.github.ygaray.voiceactionengine.core.strategy.StrategyOutcome
import io.github.ygaray.voiceactionengine.core.strategy.singleshot.SingleShotStrategy
import io.github.ygaray.voiceactionengine.core.telemetry.TraceCode
import io.github.ygaray.voiceactionengine.core.telemetry.TurnRecord
import io.github.ygaray.voiceactionengine.core.telemetry.Usage
import io.github.ygaray.voiceactionengine.core.testing.FakeAiProvider
import io.github.ygaray.voiceactionengine.core.testing.FakeMutation
import io.github.ygaray.voiceactionengine.core.testing.NoNetworkGuard
import io.github.ygaray.voiceactionengine.core.testing.ProviderStep
import io.github.ygaray.voiceactionengine.core.testing.RecordingCommitSink
import io.github.ygaray.voiceactionengine.core.testing.ScriptedCredentialSource
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
private const val HTTP_BAD_REQUEST = 400
private const val CEILING = 100L

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

    // ---- onFailed: the else arm of the provider-failure mapping, and nothing else

    private fun saving(): suspend (Extraction, CommandInput) -> Resolution = { _, _ ->
        Resolution.Steps(listOf(ToolStep.Mutation(FakeMutation(ENTRIES_TOOL, StepResult("saved")))))
    }

    private fun httpError(): ModelResult =
        ModelResult.Failure(FailureReason.HttpError(), FailureDetails(HTTP_BAD_REQUEST, "invalid_request", null))

    @Test
    fun anOnFailedHookTurnsAProviderHttpErrorIntoEscalateAndTheNextTierRuns() = runTest {
        NoNetworkGuard.during {
            val next = nextTier()

            val run = ladderOf(httpError(), next = listOf(next)) {
                onFailed = { _, _ -> StrategyOutcome.Escalate(EscalationReason.ModelDeclined()) }
            }

            assertEquals(1, next.executions)
            assertTrue(run.outcome.toString(), run.outcome is CommandOutcome.Completed)
        }
    }

    @Test
    fun theOnFailedHookReceivesTheExactReasonAndDetailsOfTheFailure() = runTest {
        NoNetworkGuard.during {
            val reason = FailureReason.HttpError()
            val details = FailureDetails(HTTP_BAD_REQUEST, "invalid_request", "req-9")
            var seenReason: FailureReason? = null
            var seenDetails: FailureDetails? = null

            ladderOf(ModelResult.Failure(reason, details)) {
                onFailed = { r, d ->
                    seenReason = r
                    seenDetails = d
                    StrategyOutcome.Failed(r, d)
                }
            }

            assertSame(reason, seenReason)
            assertSame(details, seenDetails)
        }
    }

    @Test
    fun withoutAnOnFailedHookTheSameFailureStillEndsFailedWithReasonAndDetails() = runTest {
        NoNetworkGuard.during {
            val details = FailureDetails(HTTP_BAD_REQUEST, "invalid_request", null)

            val run = ladderOf(ModelResult.Failure(FailureReason.HttpError(), details))

            assertEquals(FailureReason.HttpError(), failedReason(run))
            assertSame(details, (run.outcome as CommandOutcome.Failed).details)
        }
    }

    @Test
    fun theOnFailedHookIsNotCalledForNoToolCallOrRefusalFailuresOrAnswers() = runTest {
        NoNetworkGuard.during {
            var calls = 0
            val counting: SingleShotStrategy.Builder.() -> Unit = {
                onFailed = { r, d ->
                    calls++
                    StrategyOutcome.Failed(r, d)
                }
            }

            ladderOf(ModelResult.Failure(FailureReason.NoToolCall()), configure = counting)
            ladderOf(ModelResult.Failure(FailureReason.Refusal()), configure = counting)
            ladderOf(FakeAiProvider.refusal(usage()), configure = counting)
            ladderOf(FakeAiProvider.reply("prose", usage()), configure = counting)
            ladderOf(answerOf(StopReason.MAX_TOKENS, toolCall()), configure = counting)

            assertEquals(0, calls)
        }
    }

    @Test
    fun theOnFailedHookIsNotCalledForAMissingCredentialAndTheNextTierNeverRuns() = runTest {
        NoNetworkGuard.during {
            var calls = 0
            val next = nextTier()
            val tier = singleShot(RecordingResolver(noMatch()), snapshotOf(entriesTool())) {
                onFailed = { _, _ ->
                    calls++
                    StrategyOutcome.Escalate(EscalationReason.ModelDeclined())
                }
            }
            val fake = FakeAiProvider(ProviderId.ANTHROPIC)
            val pipeline = pipelineOf(
                listOf(tier, next),
                fake,
                ScriptedGate.admitAll(),
                RecordingCommitSink(),
                credentials = ScriptedCredentialSource.keys(),
            )

            val outcome = pipeline.execute(CommandInput("add two things", "en", null))

            assertTrue(outcome.toString(), outcome is CommandOutcome.Failed)
            assertEquals(0, calls)
            assertEquals(0, next.executions)
            assertEquals(0, fake.callCount)
        }
    }

    @Test
    fun theOnFailedHookIsNotCalledWhenTheTokenCeilingStopsTheTier() = runTest {
        NoNetworkGuard.during {
            var calls = 0
            val hook: suspend (FailureReason, FailureDetails?) -> StrategyOutcome = { r, d ->
                calls++
                StrategyOutcome.Failed(r, d)
            }
            val policy = TierPolicy { tokenCeiling = CEILING }
            val tier = singleShot(RecordingResolver(saving()), snapshotOf(entriesTool())) { onFailed = hook }
            val crossing = FakeAiProvider(
                ProviderId.ANTHROPIC,
                FakeAiProvider.toolCall("call-1", ENTRIES_TOOL, entriesArguments("a"), Usage(0, 0, 0, CEILING + 1)),
            )
            val crossedPipeline =
                pipelineOf(listOf(tier), crossing, ScriptedGate.admitAll(), RecordingCommitSink(), policy = policy)
            val crossed = crossedPipeline.execute(CommandInput("add two things", "en", null))
            val reached = pipelineOf(
                listOf(
                    ScriptedStrategy(
                        StrategyId("earlier"),
                        { _, session ->
                            session.recordTurn(
                                TurnRecord(null, null, null, emptyList(), Usage(0, 0, 0, CEILING), 1L),
                            )
                            StrategyOutcome.Escalate(EscalationReason.NoToolCall())
                        },
                    ),
                    tier,
                ),
                FakeAiProvider(ProviderId.ANTHROPIC),
                ScriptedGate.admitAll(),
                RecordingCommitSink(),
                policy = policy,
            ).execute(CommandInput("add two things", "en", null))

            assertEquals(FailureReason.BudgetExceeded(BudgetBound.TOKENS), (crossed as CommandOutcome.Failed).reason)
            assertEquals(FailureReason.BudgetExceeded(BudgetBound.TOKENS), (reached as CommandOutcome.Failed).reason)
            assertEquals(0, calls)
        }
    }

    @Test
    fun theOnFailedHookIsNotCalledForAGateHoldOrAThrowingResolver() = runTest {
        NoNetworkGuard.during {
            var calls = 0
            val counting: SingleShotStrategy.Builder.() -> Unit = {
                onFailed = { r, d ->
                    calls++
                    StrategyOutcome.Failed(r, d)
                }
            }
            val held = FakeAiProvider(ProviderId.ANTHROPIC, oneCall())
            val holdingTier = singleShot(RecordingResolver(saving()), snapshotOf(entriesTool()), configure = counting)
            val holdPipeline =
                pipelineOf(listOf(holdingTier), held, ScriptedGate.holdAll("confirm"), RecordingCommitSink())
            holdPipeline.execute(CommandInput("add two things", "en", null))

            val threw = ladderOf(oneCall(), answer = { _, _ -> error("resolver blew up") }, configure = counting)

            assertEquals(0, calls)
            assertTrue(threw.outcome.trace.codes.contains(TraceCode.STRATEGY_ERROR))
        }
    }

    @Test
    fun anOnFailedHookThatThrowsEndsTheTierAsAStrategyError() = runTest {
        NoNetworkGuard.during {
            val run = ladderOf(httpError()) {
                onFailed = { _, _ -> error("hook blew up") }
            }

            assertTrue(failedReason(run).toString(), failedReason(run) is FailureReason.Unexpected)
            assertTrue(run.outcome.trace.codes.contains(TraceCode.STRATEGY_ERROR))
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
