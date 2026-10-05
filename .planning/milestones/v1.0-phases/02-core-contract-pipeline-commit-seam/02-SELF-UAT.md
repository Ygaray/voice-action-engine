---
status: complete
result: all_pass
gate: 1
phase: 02-core-contract-pipeline-commit-seam
source: [02-ROADMAP success criteria 1-5]
device: none (pure :core JVM library phase; target surface is the JVM test/fixture harness, no adb, no device, no emulator)
apk: n/a (artifact: core/build/libs/core.jar md5 81a4651c80e81317de42a112551a07ba @ baf6fcc)
run: 2026-10-01T01:38:46Z
---

<!--
Gate-1 self-UAT for a headless pure-JVM library phase. Driver: no project AGENT-*-TESTING.md exists and the phase has
no UI; the target is the Gradle JVM test harness over scripted fake strategies (no LLM, no network). Same handling as
the Phase 1 precedent (01-SELF-UAT.md): no device playbook applies.
-->

# Self-UAT Log: Phase 2 (Core Contract, Pipeline & Commit Seam), one log for the phase

**Target:** headless (repo CLI, JVM test harness over scripted fakes, `NoNetworkGuard`). No adb, no device, no emulator.
**Build identity:** HEAD `baf6fcc09632953900562b6794621ae2cf50d52a`; `git diff --stat HEAD -- core config scripts` empty (no uncommitted change to library, config or scripts). `core.jar` md5 `81a4651c80e81317de42a112551a07ba`.
**Pre-flight:** JDK 17, Gradle wrapper 9.4.1. Stale results removed first (`rm -rf core/build/test-results/test`), then tests forced to re-execute with `--rerun-tasks`, so every count below was produced by this run, not by a cache.
**Unit suite:** `./gradlew :core:test --rerun-tasks --console=plain` (foreground) -> `BUILD SUCCESSFUL in 41s`, exit 0. JUnit XML in `core/build/test-results/test/`: 33 classes, **233 tests, 0 failures, 0 errors, 0 skipped**.
**Wider gates:** `./gradlew check` -> `BUILD SUCCESSFUL`, 137 actionable tasks (detekt, explicit-API, bytecode, module-graph, DI, OkHttp floor verifiers all green); `scripts/verify-repo-hygiene.sh` -> `HYGIENE OK`; `scripts/review-api-surface.sh` -> `API SURFACE OK sealed=CommandOutcome,GateDecision,RunTermination,StrategyOutcome,ToolStep classes=117`.
**Coverage/Nyquist:** `02-VALIDATION.md` and `02-VERIFICATION.md` (status passed, 5/5) exist; treated as claims. Coverage re-derived below by reading test names and assertions against the ROADMAP text, not against the SUMMARYs.
**Seed/fixture integrity:** fixtures are in-tree test fixtures (`ScriptedStrategy`, `ScriptedGate`, `ScriptedResponses`, `FakeMutation`, `FakeClock`, `RecordingCommitSink`, `RecordingEventListener`, `NoNetworkGuard`); no external state, nothing to restore.
**Prior verdict audited:** `02-VERIFICATION.md` and `evidence/phase-gate.txt`. Every claim re-observed this run; no contradiction found. Spot-read the assertions of the load-bearing tests to confirm they are not vacuous (details per criterion).

## Criteria

### 1. SC1: DSL-composed ladder climbs on Escalate/NoMatch with carry, stops on Completed/Failed, starts mid-ladder under TierSelector.Fixed
result: passed
- **Rung:** 1 (unit tests over the scripted-strategy harness; no visual claim exists for this phase)
- **Target:** headless JVM harness
- **Expected:** `commandPipeline { tier(...); selector = Linear; policy = ... }` invoked with `CommandInput(transcript, "en"|"es"|null, context)`; Escalate/NoMatch climb with `carry` handed on; Completed/Failed stop; `Fixed(tier)` starts mid-ladder.
- **Arranged (seeded):** scripted fake strategies (`ScriptedStrategy`) per tier, supplied by each test; no LLM, no network (`NoNetworkGuard.during { }`).
- **Did (drove):** `./gradlew :core:test --rerun-tasks`; then parsed the XML for the owning classes.
- **Observed (all 0 failures/skips):** `TierWalkTest` 9/9 including `escalateClimbsAndHandsTheCarryOverByIdentity`, `noMatchClimbsWithANullCarry`, `noMatchDropsACarryFromAnEarlierTier`, `completedStopsTheWalk`, `failedStopsTheWalkAndCarriesItsDetails`, `exhaustedLadderEndsUnhandledWithTheLastTiersReasonInstance`, `aFinalNoMatchEndsUnhandledWithNoReason`; `TierSelectorTest` 4/4 including `fixedStartsMidLadderAndHandsTheCarryOnByIdentity` and `fixedNamingAnUnknownTierIsRejectedAtBuild`, `linearIsTheDefaultAndStartsAtTheFirstTier`; `PipelineBuilderTest` 11/11 (DSL build-time validation); `PipelineSpineTest` 5/5 (DSL -> execute -> strategy -> gate -> apply -> sink end to end); `IdentityTypesTest` 11/11 (`CommandInput` keeps every field, all-null defaults, null context handled). Carry is asserted by identity, which would fail if the engine copied or dropped it.
- **Evidence:** `core/build/test-results/test/TEST-io.github.ygaray.voiceactionengine.core.{TierWalkTest,TierSelectorTest,PipelineBuilderTest,PipelineSpineTest,IdentityTypesTest}.xml`.

### 2. SC2: TierPolicy read per call; maxTier/allowedProviders/6-60,000-4,096 defaults; maxIterations<2 rejected; offlineOnly with no on-device provider is a loud Failed after zero provider calls
result: passed
- **Rung:** 1
- **Target:** headless JVM harness
- **Expected:** policy source consulted on every call; the three limits default to 6 / 60,000 / 4,096; `maxIterations < 2` rejected; `offlineOnly` with no ON_DEVICE provider returns `Failed` before any provider runs.
- **Arranged (seeded):** scripted strategies declaring `StrategyCapabilities`; a throwing and a counting `TierPolicySource`; on-device availability hook toggled per test.
- **Did (drove):** `:core:test --rerun-tasks`; read `TierPolicyTest.defaultPolicyHasTheDocumentedLimits` and the source constants.
- **Observed:** `TierPolicyTest` 24/24. Source constants independently confirmed in `TierPolicy.kt:6-8` (`DEFAULT_MAX_ITERATIONS = 6`, `DEFAULT_TOKEN_CEILING = 60_000L`, `DEFAULT_MAX_TOKENS_PER_TURN = 4_096`) and the test asserts all three plus `maxTier == null`, `allowedProviders == null`, `offlineOnly == false`. Named cases: `fewerThanTwoIterationsIsRejected`, `nonPositiveLimitsAreRejected`, `policyIsReadOncePerExecuteBeforeTheFirstStrategyRuns`, `aThrowingPolicySourceFailsWithPolicyUnavailableAndRunsNothing`, `maxTierCutsTheLadderAfterThatTier`, `maxTierNotOnTheLadderIsALoudNoEligibleTier`, `allowedProvidersSkipsTiersThatNeedAnotherProvider`, `allowedProvidersIsDefensivelyCopied`, `offlineOnlyWithOnlyCloudTiersFailsLoudlyWithZeroExecutions`, `anUnavailableOnDeviceOnlyTierNeverClimbsToTheCloud`, `offlineOnlyRunsAnAvailableOnDeviceTierAndRefusesAnUnavailableOne`, `theOnDeviceHookDefaultsToUnavailable`, `aThrowingOnDeviceHookCountsAsUnavailable`. The zero-provider-call claim is asserted by `...WithZeroExecutions` plus the shared `NoNetworkGuard`.
- **Evidence:** `core/build/test-results/test/TEST-...TierPolicyTest.xml`; `core/src/main/kotlin/.../pipeline/TierPolicy.kt`.

### 3. SC3: never-throw collapse, CancellationException propagates, TIMEOUT not NETWORK, open FailureReason taxonomy with request id, regular (non-data) public classes, ProviderId value class with four constants
result: passed
- **Rung:** 1/3 (unit tests plus reflection-based API-shape sweep and the Metalava-derived surface review script)
- **Target:** headless JVM harness
- **Expected:** no exception escapes; every throw becomes a typed outcome through the single `guarded` helper; cancellation always propagates; engine deadline reports TIMEOUT; `FailureReason` open taxonomy (auth ... provider-unavailable + `Other`) with provider request id; growing public types not data classes; `ProviderId` is a value class with exactly four constants.
- **Arranged (seeded):** strategies, sinks, listeners and descriptors that throw (including `AssertionError`, `LinkageError`, a leaked inner timeout, and a foreign `CancellationException` from a sink/listener); a `FakeClock`.
- **Did (drove):** `:core:test --rerun-tasks`; `scripts/review-api-surface.sh`; `./gradlew check` (detekt `TooGenericExceptionCaught` stays on, the single justified suppression lives in `internal/Guarded.kt`).
- **Observed:** `NeverThrowTest` 7/7 (`aThrowingStrategyBecomesFailedUnexpectedAndTheRunClosesOnce`, `aLinkageErrorIsCollapsedLikeAnException`, `aLeakedInnerTimeoutIsATimeoutNotAnEscape`, `theEngineDeadlineIsATimeoutNeverANetworkFailureAndKeepsEarlierCommits`, `cancellingTheCallerPropagatesAndClosesOnceAsCancelledEvenWhenTheSinkSuspends`, `aStrategyThrowingCancellationWhileActivePropagatesAndClosesOnceAsCancelled`, `anAssertionErrorPropagatesAndTheRunStillClosesOnceAsUnexpectedError`); `ForeignCancellationTest` 3/3; `GuardedTest` 12/12; `ThrowingDescriptorTest` 3/3; `RunSetupGuardTest` 2/2; `FailureTaxonomyTest` 11/11 (`timeoutAndNetworkAreDistinct`, `theRequiredCodesArePresent`, `everyFailureLeafHasAUniqueCode`, `blankOtherIsRejected`, `consumersMustKeepAnElseBranch`, `aProviderUnavailableCauseMustBeAStableCodeNotAMessage`); `IdentityTypesTest.failureDetailsRoundTripsItsThreeFields` and `RunClosedPathsTest.providerErrorExitClosesOnce` carry status/type/request id (`FailureDetails(HTTP_UNAUTHORIZED, "authentication_error", "req_1")`); `ApiShapeTest` 6/6 (`noMainClassIsDataShaped`, `noMainClassIsAnEnum`, `providerIdCompanionDeclaresExactlyFourPublicGetters`, `providerIdConstantsHaveTheWireValues`, and `sweepIsNotVacuous` which guards against the sweep checking nothing). `review-api-surface.sh` -> `API SURFACE OK ... classes=117`. Note the deadline test mixes a prior commit with a later timeout and asserts the commit survives, so it would fail if the timeout collapsed to NETWORK or dropped earlier work.
- **Evidence:** XML for the classes above; `.planning/phases/02-core-contract-pipeline-commit-seam/evidence/api-surface-review.txt`; fresh script output this run.

### 4. SC4: every write prepare -> PreApplyGate.admit -> CommitSink; suspend mode (AwaitingConfirmGate, 120 s, fail-closed) and defer mode (Hold, commitHeld, amended); held never success and yields the exact held JSON; CommitSink hears each commit with runId and an ExecutedToolCall-shaped payload; onRunClosed exactly once on each of five exit paths
result: passed
- **Rung:** 1
- **Target:** headless JVM harness
- **Expected:** a single engine-owned gate -> commit path; both gate modes; held output literal `{"applied":false,"status":"held_for_confirmation"}`; sink events with `runId`; one test per exit path asserting exactly one close.
- **Arranged (seeded):** `ScriptedGate` (admit/hold/throw/amend/suspend), `FakeMutation` writes, `RecordingCommitSink` with an ordered `RecordingSink` log, virtual-time `runTest` for the 120 s window.
- **Did (drove):** `:core:test --rerun-tasks`; confirmed in source `CommitCoordinator.kt:10` (`HELD_FOR_CONFIRMATION` literal) and `:13` (`DEFAULT_CONFIRM_TIMEOUT_MILLIS = 120_000L`).
- **Observed:**
  - Path: `CommitPathTest` 7/7 incl. `theStrategyHasNoOtherWritePathThanSubmitOfOneToolStep`, `aSinkThatThrowsAfterASuccessfulApplyNeverCausesASecondApply`, `anApplyThatReturnsAnErrorIsAnAppliedErrorNeverACommit`, `aCancelDuringAnApplyIsJournaledBeforeTheCancellationContinues`; `ActionEventTest` 5/5; `PipelineSpineTest.oneMutationFlowsThroughGateApplySinkAndClosesOnce` and `twoMutationsApplyInOrderWithASinkEventBetweenThem`; `WriteGuardsTest` 2/2; `BatchIsolationTest` 3/3.
  - Suspend mode: `AwaitingConfirmGateTest` 20/20 incl. `noAnswerHoldsOnlyAfterTheDefaultWindowAndCarriesTheSubjectAndToken`, `aConfirmationResolvedAfter119SecondsStillAdmitsBecauseTheEngineAddsNoTimeout`, `aDeclineAppliesNothingAndHoldsWithTheSubjectAsTheReason`, `anAnswerAfterTheTimeoutReturnsFalseAndTheGateHolds`, `aThrowingAmendHookFailsClosedThroughThePipeline`, `aThrowingPolicyPropagatesAtUnitLevelAndFailsClosedThroughThePipeline`, `cancellingWhilePendingPropagatesClearsPendingAndRejectsTheStaleId`; `HeldReportingTest.aGateThatThrowsFailsClosedWithATraceCodeAndNoInventedReason`.
  - Defer mode: `DeferModeTest` 7/7 (`commitHeldSkipsTheGateAndRunsAsALinkedChildWithItsOwnClose`, `commitHeldWithAnAmendedListAppliesTheAmendedMutationAndNeverTheHeldOne`, `twoConcurrentCommitHeldCallsApplyOnceAndReturnTheSameOutcome`, `aSecondCommitHeldReturnsTheFirstOutcomeWithNoApplyAndNoEvent`, `aCommitHeldCancelledMidApplyConsumesTheProposalAndNeverAppliesAgain`); `HeldCommitGuardsTest` 4/4; `ParentRunIdTest` 3/3.
  - Held honesty: `HeldReportingTest.theHeldContentFunctionReturnsTheExactLiteral` plus `aHoldReportsTheMutationAndHandsTheStrategyTheExactHeldBytes`, `aTwoMutationHoldReportsTwoActionsAndOneProposal`; no held case reaches `Completed` as an applied commit.
  - Five exit paths: `RunClosedPathsTest` 7/7 has one named test for each of `doneExitClosesOnce`, `budgetExceededExitClosesOnce`, `providerErrorExitClosesOnce`, `escalationExhaustedExitClosesOnce`, `cancelledExitClosesOnce`, each asserting `sink.closes.size == 1` and the matching `RunTermination` subtype (spot-read: budget path asserts `RunTermination.Failed` with `BudgetExceeded`, commits `["write"]`, and that actions precede the close; exhausted path asserts `RunTermination.Exhausted` with `lastReason` identity). Extras: `aSessionKeptPastTheCloseCanNeverWriteOrDeliver`, `aSinkWhoseCloseHookThrowsStillLetsExecuteReturnAfterOneAttempt`.
- **Evidence:** XML for the classes above; `evidence/phase-gate.txt`.

### 5. SC5: no escalation after commit/hold and no repeated write; every outcome carries executed list, commits, held proposals and a CommandTrace; optional typed callback gets the same events live
result: passed
- **Rung:** 1
- **Target:** headless JVM harness
- **Expected:** a tier that committed >=1 action then asks to escalate never reaches the next tier (result `Completed(partial)`); outcomes carry the ordered executed list, commits, held proposals and a `CommandTrace` with per-tier attempts, escalation reasons, provider/model, normalized tokens, latency; a listener receives the same events live.
- **Arranged (seeded):** two-tier ladders where tier 1 commits/holds/errors then escalates and tier 2 is a tripwire; `FakeClock`; scripted `TurnRecord`s from an Anthropic-shaped and an OpenAI-shaped turn; a listener that throws.
- **Did (drove):** `:core:test --rerun-tasks`; read the test names against the criterion; confirmed `RedactionCanaryTest`.
- **Observed:** `EscalationSafetyTest` 10/10 (`aCommitThenEscalateEndsPartialAndTierTwoNeverRuns`, `aHoldThenEscalateEndsPartialListsTheHeldProposalAndTierTwoNeverRuns`, `anErroredApplyCountsAsCommittedForTheGuard`, `anApplyThatThrowsCountsAsCommittedForTheGuard`, `aCommitThenNoMatchEndsPartialWithNoSuppressedReason`, `aBudgetFailureAfterACommitStaysFailedAndCarriesTheCommit`, and the negative controls `aPreviewOnlyTierStillEscalates`, `aRejectedOnlyTierStillEscalates`, `aNormalCompletionIsNotPartial`); `ExecutedListTest` 4/4 (cumulative across tiers with monotonic positions, carried on Failed and Unhandled); `TraceTest` 7/7 (`anEscalatedTierRecordsItsReasonAndItsClockLatency`, `aSuppressedEscalationRecordsItsReasonAndItsClockLatency`, `anAnthropicTurnAndAnOpenAiTurnForTheSameWorkHaveEqualAttemptUsage`, `aReportedTurnLandsInTheAttemptTheTraceAndTheSessionTotal`, `aTierThatReportsNoTurnHasNoProviderModelOrUsage`); `InFlightTierTraceTest` 2/2; `EventsTest` 7/7 (`eventsEqualTheTraceAndTheExecutedList`, `aTwoTierRunDeliversTheTenEventsInOrder`, `aRunWithoutAListenerBehavesTheSame`, `aListenerThatThrowsOnEveryEventChangesNothing`, `aPolicySkippedTierYieldsTierSkippedWithThePolicyCode`); `RedactionCanaryTest` 2/2 and `PipelineSpineTest.noCanaryReachesAnyToStringOfWhatTheEngineProduces` (secrets never reach `toString()`/outcomes/events). The guard counts a thrown or errored apply as committed (conservative), and negative controls prove previews and rejected-only tiers still escalate, so the guard is neither absent nor over-eager.
- **Evidence:** XML for the classes above.

## Summary

total: 5
passed: 5
partial: 0
failed: 0
infra: 0

## Notes / anomalies (for the Gate-2 reviewer)

- Highest rung used: 1 for SC1, SC2, SC4, SC5; 1/3 for SC3. No visual rung (rung 5) applies: the phase has no UI. No device was touched.
- Honest scope limit: the harness proves the engine against scripted fake strategies, which is exactly what the ROADMAP goal states ("proven with scripted fake strategies, no LLM"). Real-provider transport, prompt cache and on-device behaviour belong to Phases 3 to 8 and the Phase 11 tag gate, not here.
- Test-compile emits non-fatal Kotlin warnings (`ExperimentalCoroutinesApi` opt-in in `AwaitingConfirmGateTest.kt`, unnecessary `!!` in `CommitPathTest.kt:69,92` and `HeldReportingTest.kt:106`). These are test-source warnings only, not behavior defects and not under the detekt gate; noted for a future cleanup, not routed to gap-closure.
- Phase 1 carry-over: SC5 of Phase 1 owed a `FakeAiProvider` from Phase 3; unaffected by this phase.
- Pre-existing dirty files under `.planning/` (graphs, config.json, milestone lock, state.json, stage markers, `.gsd/`) belong to the orchestrator and were not touched. No source edits, no tags, no pushes.

## Findings routed to gap-closure (if any)

None.

## Verdict

All 5 criteria PASS. Gate-1 complete; Gate-2 registered as a ledger-completeness fragment (`.planning/uat-pending/02-core-contract-pipeline-commit-seam.md`). Nothing physical or device-bound is deferred.
