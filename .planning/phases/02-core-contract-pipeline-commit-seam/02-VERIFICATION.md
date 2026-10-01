---
phase: 02-core-contract-pipeline-commit-seam
verified: 2026-09-30T00:00:00Z
status: passed
score: 5/5 must-haves verified
behavior_unverified: 0
overrides_applied: 0
re_verification: false
covered_files:
  - .planning/REQUIREMENTS.md
  - .planning/phases/02-core-contract-pipeline-commit-seam/02-01-PLAN.md
  - .planning/phases/02-core-contract-pipeline-commit-seam/02-01-SUMMARY.md
  - .planning/phases/02-core-contract-pipeline-commit-seam/02-02-PLAN.md
  - .planning/phases/02-core-contract-pipeline-commit-seam/02-02-SUMMARY.md
  - .planning/phases/02-core-contract-pipeline-commit-seam/02-03-PLAN.md
  - .planning/phases/02-core-contract-pipeline-commit-seam/02-03-SUMMARY.md
  - .planning/phases/02-core-contract-pipeline-commit-seam/02-04-PLAN.md
  - .planning/phases/02-core-contract-pipeline-commit-seam/02-04-SUMMARY.md
  - .planning/phases/02-core-contract-pipeline-commit-seam/02-05-PLAN.md
  - .planning/phases/02-core-contract-pipeline-commit-seam/02-05-SUMMARY.md
  - .planning/phases/02-core-contract-pipeline-commit-seam/02-06-PLAN.md
  - .planning/phases/02-core-contract-pipeline-commit-seam/02-06-SUMMARY.md
  - .planning/phases/02-core-contract-pipeline-commit-seam/02-07-PLAN.md
  - .planning/phases/02-core-contract-pipeline-commit-seam/02-07-SUMMARY.md
  - .planning/phases/02-core-contract-pipeline-commit-seam/02-08-PLAN.md
  - .planning/phases/02-core-contract-pipeline-commit-seam/02-08-SUMMARY.md
  - .planning/phases/02-core-contract-pipeline-commit-seam/02-09-PLAN.md
  - .planning/phases/02-core-contract-pipeline-commit-seam/02-09-SUMMARY.md
  - config/detekt/detekt.yml
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/CommandInput.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/Credential.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/ProviderId.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/StrategyId.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/commit/ActionKind.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/commit/ActionLedger.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/commit/ApplyStep.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/commit/AwaitingConfirmGate.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/commit/CommitCoordinator.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/commit/CommitSink.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/commit/FinishedKind.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/commit/GateStep.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/commit/HeldProposal.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/commit/PreApplyGate.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/commit/RunTermination.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/commit/ToolStep.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/failure/BudgetBound.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/failure/EscalationReason.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/failure/FailureDetails.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/failure/FailureReason.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/failure/ReasonSupport.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/internal/Guarded.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/pipeline/CommandOutcome.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/pipeline/CommandPipeline.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/pipeline/HeldCommit.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/pipeline/PipelineBuilder.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/pipeline/PolicyPreCheck.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/pipeline/RunSession.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/pipeline/TierPolicy.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/pipeline/TierPolicySource.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/pipeline/TierSelector.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/pipeline/TierWalk.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/Clarification.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/CommandSession.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/CommandStrategy.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/StrategyCapabilities.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/StrategyOutcome.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/TerminalCall.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/ToolSpec.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/telemetry/CommandTrace.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/telemetry/EventDispatch.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/telemetry/PipelineEvent.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/telemetry/PipelineEventListener.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/telemetry/RunRecorder.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/telemetry/TraceCode.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/telemetry/TurnRecord.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/telemetry/Usage.kt
  - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/ActionEventTest.kt
  - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/ApiShapeTest.kt
  - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/AwaitingConfirmGateTest.kt
  - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/BatchIsolationTest.kt
  - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/CommitPathTest.kt
  - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/DeferModeTest.kt
  - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/EscalationSafetyTest.kt
  - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/EventsTest.kt
  - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/ExecutedListTest.kt
  - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/FailureTaxonomyTest.kt
  - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/ForeignCancellationTest.kt
  - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/GuardedTest.kt
  - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/HeldCommitGuardsTest.kt
  - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/HeldReportingTest.kt
  - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/IdentityTypesTest.kt
  - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/InFlightTierTraceTest.kt
  - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/NeverThrowTest.kt
  - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/ParentRunIdTest.kt
  - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/PipelineBuilderTest.kt
  - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/PipelineSpineTest.kt
  - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/RedactionCanaryTest.kt
  - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/RunClosedPathsTest.kt
  - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/RunSetupGuardTest.kt
  - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/TerminalCallTest.kt
  - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/ThrowingDescriptorTest.kt
  - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/TierPolicyTest.kt
  - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/TierSelectorTest.kt
  - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/TierWalkTest.kt
  - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/ToolSpecClarificationTest.kt
  - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/TraceTest.kt
  - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/ValueTypesTest.kt
  - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/WriteGuardsTest.kt
  - core/src/testFixtures/kotlin/io/github/ygaray/voiceactionengine/core/testing/FakeClock.kt
  - core/src/testFixtures/kotlin/io/github/ygaray/voiceactionengine/core/testing/FakeMutation.kt
  - core/src/testFixtures/kotlin/io/github/ygaray/voiceactionengine/core/testing/RecordingCommitSink.kt
  - core/src/testFixtures/kotlin/io/github/ygaray/voiceactionengine/core/testing/RecordingEventListener.kt
  - core/src/testFixtures/kotlin/io/github/ygaray/voiceactionengine/core/testing/ScriptedGate.kt
  - core/src/testFixtures/kotlin/io/github/ygaray/voiceactionengine/core/testing/ScriptedStrategy.kt
  - scripts/review-api-surface.sh
covered_digest: "v1:sha256:c56f1275edcfec642958ba302ae14d086112bfd447c64d04c2d11511ddf4cfdc"
---

# Phase 2: Core Contract, Pipeline & Commit Seam Verification Report

**Phase Goal:** A consumer can compose a tier ladder with the DSL and get back a typed outcome that is never thrown, with a full trace attached. Every write goes through one engine-owned gate -> commit path, and held actions are reported honestly. All of it is proven with scripted fake strategies, no LLM.
**Verified:** 2026-09-30
**Status:** passed
**Re-verification:** No, initial verification (final tree, after the 15 code-review fixes)

## Goal Achievement

Verified against the code in `core/src/main`, not the SUMMARYs. The tree was exercised with `./gradlew :core:check --rerun-tasks` (exit 0, fresh run, not cached), plus `scripts/review-api-surface.sh --expect-sealed-complete`, `verify-repo-hygiene.sh`, `verify-negative-controls.sh` and `verify-api-dump.sh` (all exit 0).

### Observable Truths (ROADMAP success criteria)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | Consumer composes `commandPipeline { tier(...); selector; policy }` and calls it with `CommandInput(transcript, "en"/"es"/null, context)`; ladder climbs on `Escalate`/`NoMatch` (carry handed over), stops on `Completed`/`Failed`, starts mid-ladder under `Fixed(tier)` | VERIFIED | `PipelineBuilder.kt` (`commandPipeline`, `tier`, `selector`, `policy`; build-time validation: empty ladder, duplicate ids, unknown Fixed tier, missing gate/sink all rejected). `TierWalk.climb` loops tiers; `handUp` stores `carry`, `startFresh` clears it, `Completed`/`Failed` return. `TierSelector.Linear`/`Fixed.startIndex`. Tests: `TierWalkTest` (carry by identity, NoMatch null carry, completed/failed stop, exhausted = `Unhandled`), `TierSelectorTest` (Fixed mid-ladder), `PipelineBuilderTest`. |
| 2 | `TierPolicy` read per call; enforces `maxTier`, `allowedProviders`, 6 / 60,000 / 4,096 defaults; `maxIterations < 2` rejected; `offlineOnly` with no on-device provider returns loud `Failed` after zero provider calls | VERIFIED | `TierPolicy.kt` (defaults 6 / 60_000 / 4_096; `require(maxIterations >= 2)`). `TierPolicySource.current()` called in `CommandPipeline.runCommand` once per `execute`. `PolicyPreCheck` applies `maxTier` cut (unknown tier = loud `NoEligibleTier`), `allowedProviders`, `offlineOnly` (`ProviderUnavailable(ON_DEVICE, "offline_unavailable")`), and `blockedOnDevice` never climbs to cloud. Tests in `TierPolicyTest` incl. `policyIsReadOncePerExecute...`, `offlineOnlyWithOnlyCloudTiersFailsLoudlyWithZeroExecutions`, `anUnavailableOnDeviceOnlyTierNeverClimbsToTheCloud`, `fewerThanTwoIterationsIsRejected`. Note: the engine itself enforces only offline/maxTier/allowedProviders/deadline; `maxIterations`, `tokenCeiling`, `maxTokensPerTurn` are documented as strategy-enforced (see Advisory). |
| 3 | No exception escapes; every throw collapses through one helper; `CancellationException` always propagates; engine timeout is `TIMEOUT` not `NETWORK`; failures carry a `FailureReason` from the open taxonomy (+ `Other`) and the provider request id; growing public types are regular classes; `ProviderId` is a value class with 4 constants | VERIFIED | `Guarded.kt`: one `guardedCore` with the repo's only `@Suppress` (verified by grep: single `@Suppress` in `core/src/main`); cancellation rethrown before the broad catch; `TimeoutCancellationException` leak handled as fault only when the caller is still active. `CommandPipeline.execute`/`drive` wrap run setup, policy read, strategy, gate, apply, sink and listener. Deadline path returns `FailureReason.Timeout()`. `FailureReason.kt` covers Auth, Billing, RateLimited, Overloaded, Timeout, Network, MalformedResponse, MalformedToolArgs, Refusal, MaxTokens, NoToolCall, ToolFailure, BudgetExceeded, NotConfigured, ProviderUnavailable, Unexpected, Other (+ more); `FailureDetails.requestId`. `ProviderId` is `@JvmInline value class` with ANTHROPIC/OPENAI/OPENROUTER/ON_DEVICE. `ApiShapeTest` sweeps all main classes: no enums, no data-shaped classes, no public static field leaks. Tests: `NeverThrowTest` (7), `GuardedTest`, `ForeignCancellationTest`, `FailureTaxonomyTest`. |
| 4 | Every write goes prepare -> `PreApplyGate.admit` -> `CommitSink`; suspend mode (`AwaitingConfirmGate`, 120 s default, fail-closed on timeout/decline/error) and defer mode (`Hold`, then `commitHeld`, optionally amended) work; a held action is never success and yields `{"applied":false,"status":"held_for_confirmation"}`; `CommitSink` hears each commit live with `runId` and executed-tool-call payload; `onRunClosed` fires exactly once on each of the five exit paths (one test per path) | VERIFIED | Single write path: `RunSession.submit` -> `CommitCoordinator.submit` (mutex, `admitCaller`, `GateStep.decide` fail-closed to `Hold` on throw, `ApplyStep`, `ActionDelivery.deliver` awaited before the next change). Held literal is byte-exact in `CommitCoordinator.kt` and asserted in `HeldReportingTest.theHeldContentFunctionReturnsTheExactLiteral`. `AwaitingConfirmGate`: 120_000 default, decline/timeout produce `Hold`, policy/hook throw propagates and the engine fails closed; `AwaitingConfirmGateTest` (confirm, decline, 119.999 s / 120 s boundary on virtual time, races, cancel). `HeldCommit` + `commitHeld` (idempotent claim, linked child run, amended list): `DeferModeTest` (7). `ExecutedAction` carries position, kind (committed/held/preview/is_error), applied, toolName, targetIds, `context` (pre-mutation snapshot), `mutating`. `RunClosedPathsTest` has exactly one test per path: `doneExitClosesOnce`, `cancelledExitClosesOnce`, `budgetExceededExitClosesOnce`, `providerErrorExitClosesOnce`, `escalationExhaustedExitClosesOnce`, plus a closed-run-cannot-write test. `closeRun` runs under `NonCancellable`. |
| 5 | A tier that committed >=1 action and then escalates never reaches the next tier and no write repeats; every outcome carries ordered executed list, commits, held proposals and a `CommandTrace` (per-tier attempts, escalation reasons, provider/model, normalized tokens, latency); optional typed callback receives the same events live | VERIFIED | `TierWalk.hasWorked()` reads the coordinator's own `appliedCount + heldCount` (never the strategy's claim); Escalate/NoMatch after work -> `suppressed()` -> `Completed(partial = true)` with `ESCALATION_SUPPRESSED` code and the suppressed reason on the attempt; budget failure stays `Failed` and carries commits. `EscalationSafetyTest` (10 tests: commit, errored apply, throwing apply, hold, preview-only still escalates, rejected-only still escalates, budget stays Failed, `partial` public non-defaulted). `CommandOutcome`/`RunTermination` expose `executed`, `commits`, `held`, `trace` on all variants. `CommandTrace`/`TierAttempt`/`TurnRecord`/`Usage` carry attempts, reasons, provider, model, 4-bucket usage, latency. `PipelineEventListener` + `EventsTest` (live order, events equal trace and executed list, throwing listener changes nothing, `CacheNotEngaged` event type exists). |

**Score:** 5/5 roadmap truths verified (0 present-but-behavior-unverified).

Behavior-dependent truths (cancellation, exactly-once close, no duplicate write, race and fail-closed paths) were not accepted on symbol presence. Each is backed by a named test that ran green in the fresh `:core:check` (233 tests, 0 failures, 0 errors, 0 skipped, summed from `core/build/test-results`), including the red-then-green tests added by the review fixes (`ForeignCancellationTest`, `WriteGuardsTest`, `AwaitingConfirmGateTest` race cases, `ThrowingDescriptorTest`, `HeldCommitGuardsTest`, `InFlightTierTraceTest`, `RunSetupGuardTest`).

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `core/.../pipeline/{PipelineBuilder,CommandPipeline,TierWalk,PolicyPreCheck,TierPolicy,TierSelector,HeldCommit,CommandOutcome}.kt` | DSL, spine, policy, selector, held commit, sealed outcome | VERIFIED | Substantive, wired from `commandPipeline` -> `CommandPipeline.execute` -> `TierWalk`. |
| `core/.../commit/{CommitCoordinator,ApplyStep,ActionLedger,GateStep,PreApplyGate,CommitSink,AwaitingConfirmGate,HeldProposal,RunTermination,ToolStep}.kt` | One gate -> commit path, both gate modes | VERIFIED | Only `RunSession.submit` and `HeldCommit` reach `CommitCoordinator`; `PendingMutation.apply` is called only from `ApplyStep`. |
| `core/.../failure/*`, `ProviderId`, `StrategyId`, `Credential`, `CommandInput` | Open taxonomies, identities | VERIFIED | `CommandInput.parentRunId` present (CORE-09). |
| `core/.../strategy/{ToolSpec,TerminalCall,Clarification,StrategyOutcome,CommandSession,CommandStrategy,StrategyCapabilities}.kt` | Strategy seam, terminal and clarification types | VERIFIED | `ToolSpec` rejects terminal+mutating at construction; `ToolSpec.clarification` and `TerminalCall.asClarification` round-trip (`ToolSpecClarificationTest`, `TerminalCallTest`). |
| `core/.../telemetry/{CommandTrace,RunRecorder,Usage,TurnRecord,PipelineEvent,EventDispatch,TraceCode}.kt` | Trace, events, normalized usage | VERIFIED | Wired: `RunRecorder` is created per run in `startRun` and feeds both snapshot and listener. |
| `core/src/testFixtures/.../*` | Scripted fake strategy, gate, sink, listener, clock | VERIFIED | Used by all pipeline tests under `NoNetworkGuard`. |
| `scripts/review-api-surface.sh`, `ApiShapeTest` | Public-surface guard | VERIFIED | Script exit 0, `classes=117`, sealed set complete. |
| `CoreModule` placeholder | Deleted | VERIFIED | No longer in `core/src/main`. |

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| `commandPipeline` | `CommandPipeline` | `PipelineBuilder.build()` | WIRED | Requires gate and sink; no auto-commit default. |
| `CommandPipeline.execute` | `TierWalk` | `withDeadline` -> `walk` -> `PolicyPreCheck.check` | WIRED | Policy read before first strategy. |
| Strategy | write path | `CommandSession.submit` -> `RunSession` -> `CommitCoordinator.submit` | WIRED | Session abstract class has an internal constructor; strategies cannot bypass. |
| `CommitCoordinator` | gate / apply / sink | `GateStep.decide` -> `ApplyStep.run` -> `ActionDelivery.deliver` | WIRED | Gate throw -> bare `Hold`; sink throw -> `SINK_ERROR` code, never re-applies. |
| `drive` finally | `CommitSink.onRunClosed` | `closeRun` under `NonCancellable`, `terminationOf` | WIRED | Called from one place for `execute` and one for `commitHeld`. |
| `TierWalk` | escalation guard | `coordinator.appliedCount + heldCount` | WIRED | Reads engine records only. |
| `RunSession.recordTurn` | `CommandTrace.usage` / `tokensUsed` | `RunRecorder.turnRecorded` | WIRED | Saturating sum. |
| `HeldProposal` | `CommandPipeline.commitHeld` | `HeldCommit.resolve` (CAS claim, child run with `parentRunId = held.runId`) | WIRED | |

### Data-Flow Trace (Level 4)

Not applicable in the UI sense (no rendered data). The equivalent chain was traced: a strategy-reported `TurnRecord.usage` -> `RunRecorder.book.turns` -> `TierAttempt.usage` -> `CommandTrace.usage`, and -> `tokenTotal` -> `CommandSession.tokensUsed`. All sourced from real reported turns, none hardcoded. `executed`/`commits`/`held` flow from `ActionLedger` (the single appender) into `RunEffects` -> both `CommandOutcome` and `RunTermination`.

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| Whole `:core` check (compile, detekt zero-baseline, 233 tests, ApiShapeTest, banned-construct scan) | `./gradlew :core:check --rerun-tasks -q` | exit 0, 233 tests / 0 fail / 0 error / 0 skip | PASS |
| Public API surface (sealed families complete, no ctor widening) | `bash scripts/review-api-surface.sh --expect-sealed-complete` | `API SURFACE OK ... classes=117`, exit 0 | PASS |
| Repo hygiene, negative controls, api-dump proof | `scripts/verify-repo-hygiene.sh`, `verify-negative-controls.sh`, `verify-api-dump.sh` | all exit 0 (negative-control detekt findings in the planted file are the expected red proof) | PASS |
| Banned constructs / debt markers in `core/src/main` and testFixtures | grep for `TBD|FIXME|XXX|TODO|HACK|runCatching|println|printStackTrace` | no matches | PASS |

### Probe Execution

No phase-declared probe scripts (`scripts/*/tests/probe-*.sh`). Step 7c: SKIPPED.

### Requirements Coverage

All 18 phase IDs appear in PLAN frontmatter (02-01..02-09; 02-09 lists all 18) and in REQUIREMENTS.md; none orphaned. Every ID in REQUIREMENTS.md mapped to Phase 2 (CORE-01..09, GATE-01..07, TEL-01, TEL-02) is claimed by at least one plan.

| Requirement | Source Plan(s) | Status | Evidence |
|-------------|----------------|--------|----------|
| CORE-01 | 02-03, 02-04, 02-09 | SATISFIED | DSL + `CommandInput`; `PipelineBuilderTest`, `PipelineSpineTest` |
| CORE-02 | 02-03, 02-04 | SATISFIED | `StrategyOutcome` 4 variants; `TierWalkTest` |
| CORE-03 | 02-04 | SATISFIED | `TierSelector.Linear/Fixed`; `TierSelectorTest` |
| CORE-04 | 02-01, 02-04 | SATISFIED (see Advisory 1) | `TierPolicy` defaults/validation, `StrategyId`-typed `maxTier`, per-call source, offline and ON_DEVICE loud failures |
| CORE-05 | 02-02, 02-04 | SATISFIED | single `guarded` helper; `NeverThrowTest`, `GuardedTest`, `ForeignCancellationTest` |
| CORE-06 | 02-01 | SATISFIED | full open `FailureReason` + `FailureDetails.requestId` |
| CORE-07 | 02-01, 02-02 | SATISFIED | no enums / data classes (`ApiShapeTest`), `ProviderId` value class |
| CORE-08 | 02-02, 02-07 | SATISFIED | terminal tool types, build-time rejection, `Completed.terminalCall`, commits/held carried (`TerminalCallTest`, `ToolSpecClarificationTest`, `PipelineSpineTest.terminalCallPassesThrough...`). The "send no tool_result / start no further turn" half is strategy-loop behavior owned by Phase 9; Phase 2 delivers the engine-side outcome contract. |
| CORE-09 | 02-01, 02-03, 02-06 | SATISFIED | `CommandInput.parentRunId` on outcome, trace, events, proposals, termination (`ParentRunIdTest`) |
| GATE-01 | 02-03, 02-05 | SATISFIED | single path; app-owned `context` survives prepare -> admit -> apply; `Admit(amended)` |
| GATE-02 | 02-06, 02-07 | SATISFIED | `AwaitingConfirmGate` + `commitHeld` |
| GATE-03 | 02-05 | SATISFIED | optional app-typed `Hold.reason`; byte-exact held notice; `outcome.held` |
| GATE-04 | 02-03, 02-05 | SATISFIED | per-action `onAction` with runId; kinds committed/held/preview/is_error |
| GATE-05 | 02-03, 02-06 | SATISFIED | `RunClosedPathsTest` one test per path |
| GATE-06 | 02-03, 02-05, 02-06 | SATISFIED | `ExecutedListTest`, `BatchIsolationTest` |
| GATE-07 | 02-06 | SATISFIED | `EscalationSafetyTest` |
| TEL-01 | 02-02, 02-08 | SATISFIED for this phase's scope (see Advisory 2) | trace attempts / reasons / provider / model / 4-bucket usage / latency; `TraceTest` Anthropic-vs-OpenAI usage equality |
| TEL-02 | 02-08 | SATISFIED | `PipelineEventListener`, events incl. `CacheNotEngaged` type, live and in order (`EventsTest`) |

### Anti-Patterns Found

None blocking. Zero debt markers (`TBD/FIXME/XXX/TODO/HACK`), no `runCatching`, `println`, `printStackTrace` in `core/src/main` or `testFixtures`. detekt runs with zero issues and no baseline (part of the green `:core:check`). The sole `@Suppress` is in `internal/Guarded.kt` as required. No stubs: all main files are substantive and wired.

### Review-Fix Verification (15 findings, final tree)

Each fix was checked in the final source and has a test that ran green:
- CR-01: `guardedUncancellable` used in `ActionDelivery.deliver`, `closeRun`, `factsOf`, `EventDispatch.send` (`ForeignCancellationTest`).
- WR-01: `admitCaller()` under the lock in `submit` / `applyWithoutGate`, re-checked after the gate answers (`WriteGuardsTest`).
- WR-02: `AwaitingConfirmGate.awaitAnswer` settles the deferred as the single source of truth (three race tests).
- WR-03: `factsOf` snapshot before `apply` (`ThrowingDescriptorTest`).
- WR-04/05/07: `HeldCommit` guards setup and always completes `held.result`; empty `amended` rejected before the claim (`HeldCommitGuardsTest`, `RunSetupGuardTest`).
- WR-06: `RunRecorder.flushInFlight` called from `timedOut`, `collapsed` and the `finally` (`InFlightTierTraceTest`).
- IN-01..07: documentation, dead-code removal, `ON_DEVICE_PROBE_ERROR` code, saturating `Usage`, shared `ReasonSupport`, script comment/regex.

The review-fix report's declined sub-parts (WR-05 point 1, WR-01 point 3, IN-01 engine token enforcement) are documented, deliberate, and do not contradict any roadmap criterion.

### Human Verification Required

None required to close the phase. The success criteria are all JVM-testable with scripted fakes and all are covered by passing named tests. (The review-fix report flags CR-01 and WR-01..WR-03 as "worth a human glance before the cut" because they touch cancellation and concurrency; that is a pre-`v1.0.0` code-review courtesy, not a Phase 2 criterion, and the logic is pinned by red-then-green tests.)

### Advisory (non-blocking, for the orchestrator)

1. **Token ceiling / iteration limit are advisory in `:core`.** `TierPolicy` carries the 6 / 60,000 / 4,096 defaults and validation, and `CommandSession.tokensUsed` exposes the normalized running total, but the engine never stops a tier for exceeding them (documented in `TierPolicy` KDoc). Roadmap SC2 for this phase is met as written (defaults, `maxTier`, `allowedProviders`, `< 2` rejection, offline). Enforcement belongs to the strategy loop; make sure Phase 9's SC2 ("token-ceiling check before dispatch") stays in scope, since a strategy that ignores `session.policy` is unbounded until then.
2. **TEL-01 bookkeeping mismatch.** `02-08-SUMMARY.md` records TEL-01 as `requirements-partial` / "left Pending" because the cross-provider parity test against real transports completes in Phases 4-5, but `REQUIREMENTS.md` shows TEL-01 `[x]` / `Complete`, and neither Phase 4 nor Phase 5 lists TEL-01 in its `Requirements:` line. The usage-mapping SCs (Phase 4 SC2, Phase 5 SC3) cover the mapping, but the "token ceiling counts the same work identically on every provider" parity test has no explicit owner. Suggest adding TEL-01 to the Phase 4 or 5 requirement line, or reopening the checkbox, so the transport-level parity test is not dropped. This does not fail Phase 2: its own criterion (every outcome carries a trace with normalized tokens, attempts, reasons, provider/model, latency) is verified, and the Anthropic-shape vs OpenAI-shape arithmetic parity is proven in `TraceTest`.
3. **`commitHeld` has no engine deadline** and returns `Completed` with `is_error` actions when a held apply throws (a locked phase decision, pinned by `DeferModeTest.aCommitHeldWhoseApplyThrowsIsAnIsErrorEventInACompletedChild`). Both are documented on the method; consumers must read `commits`/`executed`.

## Gaps Summary

No gaps. All five roadmap success criteria and all 18 mapped requirement IDs are backed by code in the final tree and by tests that ran green in a fresh, uncached `./gradlew :core:check`. Phase 2 goal is achieved.

---

_Verified: 2026-09-30_
_Verifier: Claude (gsd-verifier)_
