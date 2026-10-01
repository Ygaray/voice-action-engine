---
status: complete
result: all_pass
gate: 1
phase: 09-agentic-loop-strategy
source: [ROADMAP Phase 9 success criteria 1-4]
device: none (headless JVM; pure :core + :providers library phase, no adb, no device, no emulator, no live provider call made by this run)
apk: n/a (no artifact built or installed; HEAD b9c776f2bc6180818c6cb69563436372be54ffb4)
run: 2026-10-01T19:21:00Z @ b9c776f2bc6180818c6cb69563436372be54ffb4
---

# Self-UAT Log: Phase 9 (Agentic Loop Strategy), one log for the phase

**Target:** headless (Gradle JVM test harness: scripted fake providers and executors in `:core`, the real Anthropic and Chat encoders/decoders over a local `MockWebServer` in `:providers`, plus the CLN-02 source scanner). No adb, no device, no emulator. This run made no live provider call and read no key. The project config defers on-device proof to Phase 10 (VER-03), so this is the same headless handling as Phases 1-8 (see `03-SELF-UAT.md`, `08-SELF-UAT.md`).
**Build identity:** HEAD `b9c776f2bc6180818c6cb69563436372be54ffb4`. `git status --short core providers keystore config scripts gradle` is empty before and after the run (the planted-violation probe in SC4 was created and removed).
**Pre-flight:** JDK 17.0.19, Gradle wrapper 9.4.1. Stale `core/build/test-results/test` and `providers/build/test-results` removed first, then `./gradlew :core:test :providers:test --offline --rerun-tasks --console=plain` in the foreground -> `BUILD SUCCESSFUL in 1m 7s`, exit 0, `11 actionable tasks: 11 executed`. Every count below was produced by this run.
**Unit suite (JUnit XML, parsed with a script):**

| Result dir | Suites | Tests | Failures | Errors | Skipped |
|---|---|---|---|---|---|
| `core/build/test-results/test` | 67 | 646 | 0 | 0 | 0 |
| `providers/build/test-results/test` (OkHttp 4.12.0 floor) | 49 | 587 | 0 | 0 | 0 |
| `providers/build/test-results/testOkhttp521` (OkHttp 5.2.1) | 49 | 587 | 0 | 0 | 0 |
| `providers/build/test-results/testOkhttp550` (OkHttp 5.5.0) | 49 | 587 | 0 | 0 | 0 |
| `keystore/build/test-results/testDebugUnitTest` | 15 | 96 | 0 | 0 | 0 |

**Wider gate:** `./gradlew check --offline` (no `--rerun-tasks`; 179 actionable tasks, 32 executed, 2 from cache, 145 up to date) -> `BUILD SUCCESSFUL`, exit 0. Honesty note: in that `check`, `testOkhttp521` and `testOkhttp550` were restored `FROM-CACHE` (identical inputs to a run made earlier today), so I forced them with `./gradlew :providers:testOkhttp521 --rerun :providers:testOkhttp550 --rerun --offline --no-build-cache` -> `BUILD SUCCESSFUL in 36s`, `2 executed`, XML timestamps 2026-10-01T19:20Z. The 587/0/0/0 figures for both legs are from that forced run. `scripts/review-api-surface.sh --expect-sealed-complete` -> `API SURFACE OK sealed=AssistantPart,CommandOutcome,GateDecision,Message,RunTermination,StrategyOutcome,ToolStep classes=181`; `scripts/verify-repo-hygiene.sh` -> `HYGIENE OK`.
**Seed/fixture integrity:** fixtures are in-tree test doubles (`FakeAiProvider`, `ScriptedToolExecutor`, `ScriptedGate`, `RecordingCommitSink`, `FakeMutation`, `NoNetworkGuard`, local `MockWebServer`). Nothing external, nothing to restore.
**Prior verdicts audited, not relied on:** `09-VERIFICATION.md` (status passed, 4/4), `09-SECURITY.md` and `evidence/phase-gate.txt`. Coverage below is re-derived from the ROADMAP text by listing the test names in the fresh JUnit XML and reading the load-bearing assertions in `AgenticLoopGateTest`, `AgenticLoopExitPathsTest`, `AgenticLoopLimitsTest`, `AgenticLoopProviderNeutralityTest` and `core/gradle/invariants.gradle.kts`. No contradiction found.

**Per-class counts (fresh XML, 0 failures / 0 errors / 0 skipped everywhere):** core: `AgenticLoopDispatchTest` 11, `AgenticLoopGateTest` 11, `AgenticLoopGuardsTest` 14, `AgenticLoopStopLeavesTest` 15, `AgenticLoopLimitsTest` 14, `AgenticLoopTerminalTest` 8, `AgenticLoopExitPathsTest` 11, `AgenticLoopCarryTest` 5, `AgenticLoopProviderNeutralityTest` 2, `AgenticLoopUserTurnTest` 9, `BatchCancellationTest` 3, `ToolExecutorSeamTest` 6, `RedactionCanaryTest` 5, `TraceTest` 13, `NoHardCodedConstantsTest` 12 (total 139 of the 646). providers: `AgenticLoopWireTest` 15 on each of the floor, 5.2.1 and 5.5.0 legs (45 executions).

## Criteria

### 1. SC1: an `AgenticLoopStrategy` tier runs over the app's `ToolSpecProvider` and two-phase `ToolExecutor` (`prepare` -> `Finished | Mutation`) on Anthropic, OpenAI and OpenRouter; every `Mutation` passes through the gate; a held step feeds the model `{"applied":false,"status":"held_for_confirmation"}`
result: passed
- **Rung:** 1 (unit and wire tests; no visual claim exists)
- **Target:** headless JVM harness (fake provider in `:core`, real encoders over `MockWebServer` in `:providers`)
- **Expected:** one loop, driven only through the two app seams, behaves identically whichever cloud provider id serves it; every `ToolStep.Mutation` reaches the gate and the sink before the next model call; a `Hold` writes nothing and the model receives the exact fixed notice bytes on each dialect's wire.
- **Arranged (seeded):** scripted provider turns, `ScriptedToolExecutor` sequences of `Finished` and `Mutation` steps, `ScriptedGate.admitAll/holdAll/sequence`, `RecordingCommitSink`, tool snapshots of 1, 2 and 25 tools; for the wire half, `MockWebServer` responses per dialect.
- **Did (drove):** forced re-run; listed test names from the XML; read the load-bearing assertions.
- **Observed:**
  - Seams: `AgenticLoopDispatchTest.theBuilderRequiresToolingAndExecutor`, `theLoopRunsWithOneTwoOrTwentyFiveTools`, `aToolTurnThenProseCommitsThroughTheGateAndCompletes`, `callsAreDispatchedOneAtATimeInCallOrder`, `aMultiToolTurnSendsOneResultsMessageInCallOrder`; `ToolExecutorSeamTest` 6/6 (`anExecutorMutationComposesWithTheGateAndTheSink`, `aReadStepIsNeverRecorded`, `aHoldingGateLeavesTheExecutorMutationUnapplied`).
  - Every Mutation is gated: `AgenticLoopGateTest.aMutationPassesTheGateAndReachesTheSinkBeforeTheNextTurn` logs `provider:1`, then asserts the `gate`, `apply:<tool>` and `sink:action:` events all occur before `provider:2`. Negative controls: `aReadToolReturningAMutationNeverReachesTheGate` (gate calls 0, apply count 0, executed empty, error content `NOT_A_MUTATING_TOOL`, trace `READ_TOOL_MUTATION_REJECTED`), `aRejectedMutatingCallNeverReachesTheGate`, `aPreviewIsReportedWithoutTheGate`, `aSuspendingGateKeepsDispatchSequential`.
  - Held: `aHeldCallFeedsTheModelTheFixedNoticeAndTheLoopContinues` asserts the result content equals `{"applied":false,"status":"held_for_confirmation"}` byte for byte, `isError` false, `applyCount == 0`, outcome `Completed` with `held.size == 1`, and the sink saw exactly one `ActionKind.HELD`. Wire: `AgenticLoopWireTest.anthropicHeldCallSendsTheFixedNoticeBytes` and `chatHeldCallSendsTheFixedNoticeBytes` (the Chat path serves OpenAI and OpenRouter), each 15/15 on all three OkHttp legs.
  - Three providers: `AgenticLoopProviderNeutralityTest.theSameScriptGivesTheSameOutcomeOnAnthropicOpenAiAndOpenRouter` runs one script (commit, read, held) on `ProviderId.ANTHROPIC`, `OPENAI` and `OPENROUTER` and asserts the whole digest (outcome, reply, action kinds, tool names, held count, provider calls, message-kind sequence) is equal; `theAgenticSourcesNeverNameAProvider` scans the loop sources for provider constants. Real wire per dialect: `anthropicTwoTurnRunEchoesTheTurnAndAnswersEveryCall`, `anthropicParallelToolUseIsAnsweredInOneMessageInOrder`, `openAiTwoTurnRunAnswersEveryCallWithAToolMessage`, `aStopFinishReasonWithToolCallsDispatchesOnTheWire`, `chatToolChoiceIsAutoOnEveryTurn`, `anthropicSystemAndToolsBytesAreIdenticalOnEveryTurn`, `chatSystemAndToolsAreIdenticalOnEveryTurn`, `{anthropic,openAi,openRouter}FirstUserMessageIsTheSbShapedTextExactly`, `openRouterNeverSendsParallelToolCalls`, `openAiStrictEligibleToolTurnsParallelCallsOff`, `openAiNonStrictToolsLeaveParallelCallsOn`. `AgenticLoopCarryTest.everyRequestOnEveryPathUsesAutomaticChoice`, `theAssistantTurnIsAppendedWithItsNativeReplayUntouched`, `theLoopNeverHandsUpAConversation`; `AgenticLoopUserTurnTest` 9/9 (the Phase 7 renderer drives the user turn; `aSnapshotNamingASingleShotToolIsStillAutomatic`).
- **Evidence:** `core/build/test-results/test/TEST-...core.{AgenticLoopDispatchTest,AgenticLoopGateTest,AgenticLoopProviderNeutralityTest,AgenticLoopCarryTest,AgenticLoopUserTurnTest,ToolExecutorSeamTest}.xml`; `providers/build/test-results/{test,testOkhttp521,testOkhttp550}/TEST-...providers.AgenticLoopWireTest.xml`.

### 2. SC2: SB's guards pass as named tests (whole-turn validation, token-ceiling check before dispatch, final-iteration guard, sequential dispatch, 2-strike tool-failure abort, unknown tool -> `is_error`, bounds from `TierPolicy`)
result: passed
- **Rung:** 1
- **Target:** headless JVM harness
- **Expected:** each named guard exists as at least one passing test whose assertion would fail if the guard were removed, and the guards fire in SB's order.
- **Arranged (seeded):** scripted multi-turn provider results (duplicate ids inside one turn, a `tool_use` stop with no calls, usage totals sitting below, on and above the ceiling, a repeating failing tool), `TierPolicy { tokenCeiling = ...; maxIterations = ... }` overrides.
- **Did (drove):** forced re-run; read `aTokenBudgetStopCarriesTheCommitsSoFar`, `aToolTurnLandingExactlyOnTheCeilingIsStillDispatched` and the guard test names.
- **Observed (all passing):**
  - Whole-turn validation: `AgenticLoopGuardsTest.aToolCallIdRepeatedWithinATurnRejectsTheWholeTurn`, `aRejectedTurnNeverReachesTheResultsMessageConstructor`, `aToolUseStopWithoutCallsIsMalformed`, and `aToolCallIdReusedFromAnEarlierTurnIsAccepted` (the signed-off cross-turn allowance).
  - Token ceiling before dispatch: `theTokenCeilingStopsTheLoopBeforeDispatchingATool`; `AgenticLoopLimitsTest.aToolTurnThatCrossesTheCeilingFailsBeforeTheExecutorOrAnyWrite`, `theCeilingIsCheckedBeforeEveryIteration`, `atTheDefaultCeilingTheTierRefusesBeforeAnyProviderCall`, `oneTokenBelowTheDefaultCeilingTheCallIsMade`, `anEndOfTurnAnswerOverTheCeilingStillCompletes`.
  - Final-iteration guard: `theFinalIterationGuardNeverDispatchesTheLastPermittedTurn`; `AgenticLoopLimitsTest.theFinalIterationGuardHoldsAtMinimumIterations` and `...AtDefaultIterations`; `AgenticLoopTerminalTest.aFinalIterationTurnWithACallBeforeTheTerminalCallStillFailsTheGuard` and `aTerminalTurnOnTheFinalIterationCompletesInsteadOfBudgetExceeded`.
  - Sequential dispatch: `AgenticLoopDispatchTest.callsAreDispatchedOneAtATimeInCallOrder`, `AgenticLoopGateTest.aSuspendingGateKeepsDispatchSequential`, `aCancelBetweenTwoCallsOfOneTurnStopsBeforeThePrepareOfTheSecond`.
  - 2-strike abort: `repeatedFailureOfTheSameToolAbortsButTheCommittedSiblingIsStillRecorded`, `strikesCountPerToolNameAcrossTurns`, `twoDifferentToolsFailingOnceEachDoNotAbort` (so it is per tool name, not any two failures), `aHeldCallIsNotAStrike`, `aPrepareFaultAndAnApplyErrorCountAsStrikes`, `AgenticLoopTerminalTest.aStrikeAbortBeatsATerminalCallInTheSameTurn`, `AgenticLoopStopLeavesTest.aRepeatedToolErrorIsToolFailure`.
  - Unknown tool -> `is_error`: `AgenticLoopDispatchTest.anUnknownToolIsAnsweredWithAnErrorAndTheExecutorIsNotCalled`, `AgenticLoopGuardsTest.anUnknownToolCountsAsAStrike`.
  - Bounds from `TierPolicy`: `AgenticLoopLimitsTest.defaultsAreTheContractLimits`, `aCustomCeilingIsReadFromTheSessionPolicy`, `customPerTurnLimitIsSentUnchanged`, `defaultPolicySendsTheDefaultPerTurnTokenLimit`, `everyRequestOfAMultiTurnRunCarriesThePerTurnLimit`; `NoHardCodedConstantsTest.limitConstantsAreDeclaredOnlyByTheirOwners` keeps the numbers out of the loop.
  - Order and precedence: `AgenticLoopGuardsTest.theGuardsRunInSbOrder`, `whenTheTokenCeilingAndTheIterationCapTripOnTheSameTurnTokensWins`.
  - Every stop reason maps to a specific typed leaf with no collapsing: `AgenticLoopStopLeavesTest` 15/15 (`noLeafIsCollapsed`, `aMaxTokensStopIsMaxTokensEvenWithToolCalls`, `aPauseTurnStopIsPauseTurn`, `aRefusalStopIsRefusal`, `aContextWindowStopIsContextWindowExceeded`, `anUnknownStopWithoutCallsIsUnknownStop`, `aMalformedProviderResultIsMalformedResponse`, `anHttpErrorKeepsItsStatus`, `aNetworkFailureIsNetwork`).
  - **Known boundary (WR-01, documented and signed off, not a defect):** the pre-call check `ceilingReached` is `>=` while the post-tool-turn check `ceilingCrossed` is `>`. A tool turn that ends exactly on the ceiling still dispatches its calls (pinned by `aToolTurnLandingExactlyOnTheCeilingIsStillDispatched`: executor called once, one apply, one `COMMITTED` action) and the next model call is then refused with `BudgetExceeded(TOKENS)`; the model does not see those results. This is seam sign-off item 9 / Q10 (SB parity), disposition "intentional, not changed" in `09-REVIEW.md`, KDoc only in `c89169e`. Recorded here so a reader who tests the exact-equality case is not surprised.
- **Evidence:** `TEST-...core.{AgenticLoopGuardsTest,AgenticLoopLimitsTest,AgenticLoopDispatchTest,AgenticLoopGateTest,AgenticLoopTerminalTest,AgenticLoopStopLeavesTest,NoHardCodedConstantsTest}.xml`; `.planning/phases/09-agentic-loop-strategy/evidence/agenticloop-limits-tests.txt`.

### 3. SC3: on every exit path (done, budget, cancel, error) the outcome lists the executed actions and commits so far, and `CommitSink` has already been told about each commit
result: passed
- **Rung:** 1
- **Target:** headless JVM harness
- **Expected:** for each exit, `outcome.executed` and `outcome.commits` hold what ran before it; the sink received each action, in order, before the single close; and the close carries the same effects as the outcome. A cancel propagates (never swallowed) and a cancel between batch items stops the rest.
- **Arranged (seeded):** scripted turns that commit and then hit a budget, provider failure, malformed turn, strike abort or cancel; a `Rig` with a recording sink, `CompletableDeferred` hooks to cancel at a precise suspension point.
- **Did (drove):** forced re-run; read the exit-path assertions and the helper `assertActionsThenOneClose` / `assertSameEffects`.
- **Observed:**
  - Done: `AgenticLoopExitPathsTest.doneReportsEveryActionAndClosesOnce`.
  - Budget: `aTokenBudgetStopCarriesTheCommitsSoFar` (`Failed(BudgetExceeded(TOKENS))`, `commits == [SAVE_TOOL]`, actions-then-one-close, close effects equal the outcome's), `anIterationBudgetStopCarriesCommitsHeldErroredAndPreviews`, `AgenticLoopTerminalTest.aBudgetStopAfterACommitIsFailedNotPartial`.
  - Cancel: `aCancelDuringTheProviderCallPropagatesAndTheRunClosesCancelled` (a `RunTermination.Cancelled` with the commit and `ActionKind.COMMITTED`), `aCancelDuringApplyRecordsAnAppliedErrorAndPropagates` (a cancel mid-apply records `ActionKind.IS_ERROR` with `applied == true`, trace `APPLY_CANCELLED`, apply count 1), `aCancelWhileTheGateIsSuspendedLeavesNoRecord` (nothing applied, so nothing recorded), `AgenticLoopGateTest.aCancelBetweenTwoCallsOfOneTurnStopsBeforeThePrepareOfTheSecond`; `BatchCancellationTest` 3/3 (`aCancelBetweenBatchItemsStopsTheRestAndKeepsTheCommittedOne`, `aCancelBetweenHeldBatchItemsStopsTheRestAndKeepsTheCommittedOne`, `anUncancelledBatchStillAppliesEveryItem`; this is the O-1 per-item cancellation fix in `CommitCoordinator.applyAll`).
  - Error: `aProviderFailureAfterACommitStillReportsTheCommit` (`Failed(Network)`, commit kept), `aMalformedTurnAfterACommitStillReportsTheCommit`, `aStrikeAbortCarriesEveryExecutedAction`, `aTerminalExitReportsTheCommitsBeforeIt`, `heldErroredAndPreviewedCallsKeepDistinctKinds`; `AgenticLoopStopLeavesTest.everyLeafAfterACommitStillReportsTheCommit`; `AgenticLoopTerminalTest.aTerminalCallAfterACommittingCallCarriesTheCommit`, `aTerminalCallAlongsideAHeldCallCarriesTheHold`, `callsAfterATerminalCallAreDroppedTracedAndMarkPartial`; `AgenticLoopCarryTest.partialIsSetOnlyWhenCallsAfterATerminalCallWereDropped`.
  - Nothing leaks: `RedactionCanaryTest.noCanaryLeaksFromAnAgenticRun` and `noCanaryAppearsInAnythingTheEngineReturnsDeliversOrPrints` (5/5); `TraceTest` 13/13 includes `theAgenticLoopCodesHaveTheirSnakeCaseWireValues`.
- **Evidence:** `TEST-...core.{AgenticLoopExitPathsTest,AgenticLoopTerminalTest,AgenticLoopStopLeavesTest,AgenticLoopCarryTest,BatchCancellationTest,RedactionCanaryTest,TraceTest}.xml`.

### 4. SC4 (CLN-02): library code in `:core`, `:providers` and `:keystore` contains no app-domain types or prompts (`LogFood*`, `log_food`, SB `SYSTEM_PROMPT`, SB tool names, `MutationTier`) and hard-codes no tool count
result: passed
- **Rung:** 3 (build-level source scanner plus a planted-violation proof; headless data check, no visual claim)
- **Target:** headless Gradle scanner tasks over main and testFixtures sources
- **Expected:** `scanBannedConstructs` is green on all three library modules, the scanner is demonstrably not vacuous (it fails on a planted `log_food`, its negative-control fixtures all produce exactly the expected rule set), and a tree-wide grep agrees.
- **Arranged (seeded):** a temporary file `core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/ZzPlantedSelfUatProbe.kt` containing `internal const val PLANTED_PROBE = "log_food"`, deleted right after.
- **Did (drove):**
  1. `./gradlew :core:scanBannedConstructs :providers:scanBannedConstructs :keystore:scanBannedConstructs :core:verifyInvariantScannerControls --rerun --offline` -> all four tasks executed, `BUILD SUCCESSFUL`.
  2. Planted the probe, ran `./gradlew :core:scanBannedConstructs --offline` -> `Task :core:scanBannedConstructs FAILED`, `Banned constructs in :core: src/main/kotlin/.../core/ZzPlantedSelfUatProbe.kt:3 [app-domain name in source] 'log_food'`, `BUILD FAILED`, exit 1.
  3. Deleted the file, re-ran -> `BUILD SUCCESSFUL`. `git status --short core providers keystore config scripts gradle` -> empty.
  4. Independent grep: `grep -rnE "LogFood|log_food|MutationTier|SYSTEM_PROMPT|TAG_DISAMBIGUATION|find_tags|create_tag|edit_list_card|edit_text_card|SecondBrain|CalTracker" core/src/main providers/src/main keystore/src/main core/src/testFixtures` -> no hits; `grep -rnE "\b1[78]\s+tools?\b|TOOL_COUNT|toolCount"` over the same main trees -> no hits.
- **Observed:** the scanner (`gradle/invariants.gradle.kts`) matches the app-domain deny-list and the hard-coded tool-count patterns against the UNMODIFIED file text (so a name hidden in a string or KDoc is still caught) over `main/**` and `testFixtures/**`, and `check` depends on it. `verifyInvariantScannerControls` (also run inside `check`) feeds the `config/negative-controls/*.kt.txt` fixtures through the same `scanText` and fails on any mismatch or on an empty set, so a weakened rule would break the build. In-test counterparts: `NoHardCodedConstantsTest` 12/12 (`scanIsNotVacuous`, `scanFlagsAViolatingSyntheticFileAndPassesACleanOne`, `limitConstantsAreDeclaredOnlyByTheirOwners`, `tierPolicyDefaultValuesAppearOnlyInTierPolicy`), and the loop is shown to work with 1, 2 and 25 tools (`AgenticLoopDispatchTest.theLoopRunsWithOneTwoOrTwentyFiveTools`), so no tool count is baked in; `AgenticLoopProviderNeutralityTest.theAgenticSourcesNeverNameAProvider`.
- **Scope caveat (as the scanner documents itself):** tests under `src/test` are deliberately outside the raw rules (the providers tests use `log_food` as a neutral fixture name); the tool-count rule is pattern-based and "does not try to be a type checker: a count held in another name still gets through". The criterion is about library code, which is what is scanned.
- **Evidence:** this run's Gradle output for the four tasks and the planted-violation failure message; `gradle/invariants.gradle.kts`; `config/negative-controls/`.

## Summary

total: 4
passed: 4
partial: 0
failed: 0
infra: 0

## Notes / anomalies (for the Gate-2 reviewer)

- **Not proven here (belongs to Phase 10, VER-02 / VER-03):** everything that needs a real provider or a device. This run exercised the loop only against scripted fake providers and a local `MockWebServer`; no live Anthropic, OpenAI or OpenRouter call was made, no key was read, and nothing ran on the TESTER. On-device agentic behaviour, the cloud agentic path with a real prompt-cache hit across iterations, and real-model tool-calling behaviour (including whether a real model emits parallel calls that exercise the sequential-dispatch and whole-turn validation paths) are Phase 10 obligations.
- **Known equality boundary (WR-01):** documented and signed off, described under SC2. Not a failure and not routed to gap-closure.
- **Matrix legs:** the `check` run restored `testOkhttp521`/`testOkhttp550` from the build cache; I forced both and report those fresh numbers (see Wider gate). The Gradle build-cache path itself is not a defect.
- **Scanner scope:** SC4 is a deny-list plus pattern rule. It proves the named app-domain tokens and tool-count patterns are absent from library main sources; it cannot prove the absence of an app-domain concept under a name not on the list. Accepted design, stated in the scanner header.
- **Counts vs the earlier gate:** core 646, providers 587 per leg, keystore 96, matching the expected figures for HEAD.
- Highest rung used: 1 for SC1-SC3, 3 for SC4. No visual rung applies; the phase has no UI. No device was touched.
- No source edits, no tags, no commits. The only filesystem change to the source tree was the temporary SC4 probe file, created and removed within the run. `api.txt` is still not committed; it is created at the v1.0.0 cut in Phase 11. Pre-existing dirty files under `.planning/` (graphs, config.json, v1.0-MILESTONE-RUN.md, state.json, stage markers, `.gsd/`, intel) belong to the orchestrator and were not touched.

## Findings routed to gap-closure (if any)

None.

## Verdict

All 4 criteria PASS on the headless JVM harness. Gate-1 (headless) complete; Gate-2 registered as a fragment (`.planning/uat-pending/09-agentic-loop-strategy.md`). Nothing physical or device-bound is deferred from this phase; the real-provider and on-device halves are carried by Phase 10 (VER-02 / VER-03).
