---
phase: 02-core-contract-pipeline-commit-seam
plan: 09
subsystem: core
status: complete
tags: [kotlin, phase-gate, api-surface, metalava, evidence, hand-off]

requires:
  - phase: 02-core-contract-pipeline-commit-seam
    provides: "plans 02-01..02-08: the whole :core contract, pipeline, commit seam, telemetry and the review-api-surface.sh gate"
provides:
  - "evidence/phase-gate.txt: captured results of every phase-gate command on the final tree"
  - "evidence/api-surface-review.txt: the isolated-copy Metalava dump of :core (1113 lines, five sealed types)"
  - "Decision trace D-01..D-23, Phase 10 README hand-off notes, planner assumption list, Phase 3 hooks (below)"
affects: [Phase 3, Phase 4, Phase 5, Phase 7, Phase 9, Phase 10, Phase 11]

plan_head_before: 528a3f2d44291bd8e6d7e5d8812d15fa5dd88bd5

actuals:
  tokens: 18000   # chars/4 over the realized diff (71,828 chars: two evidence files, one KDoc line)
  tasks: 2
  commits: 2

tech-stack:
  added: []
  patterns:
    - "Phase gate = full build + Phase 1 regression scripts + isolated-copy API dump reviewed and saved, never a committed api.txt"

key-files:
  created:
    - .planning/phases/02-core-contract-pipeline-commit-seam/evidence/phase-gate.txt
    - .planning/phases/02-core-contract-pipeline-commit-seam/evidence/api-surface-review.txt
  modified:
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/commit/HeldProposal.kt

key-decisions:
  - "The constructor audit matches both 'ctor public' and 'ctor @KotlinOnly public' (Metalava emits both; the plan's narrow pattern sees 55 of 66 lines)"
  - "No source change beyond one KDoc sentence: the audit found no engine-produced type with a public constructor"

requirements-completed: [CORE-01, CORE-02, CORE-03, CORE-04, CORE-05, CORE-06, CORE-07, CORE-08, CORE-09, GATE-01, GATE-02, GATE-03, GATE-04, GATE-05, GATE-06, GATE-07, TEL-01, TEL-02]

coverage:
  - id: D1
    description: "Full ./gradlew check is green on the final tree: detekt zero baseline, all :core tests, :providers matrix legs, :keystore, :sample"
    verification:
      - kind: other
        ref: "./gradlew check (exit 0); recorded in evidence/phase-gate.txt section [1]"
        status: pass
    human_judgment: false
  - id: D2
    description: "All 21 Phase 2 test classes named in the validation map have JUnit results with zero failures (209 tests across 27 :core classes)"
    verification:
      - kind: unit
        ref: "./gradlew :core:cleanTest :core:test --no-build-cache; evidence/phase-gate.txt section [2]"
        status: pass
    human_judgment: false
  - id: D3
    description: "Phase 1 gates did not regress: negative controls (69 ok, 0 FAIL), repo hygiene, api-dump proof"
    verification:
      - kind: other
        ref: "scripts/verify-negative-controls.sh && scripts/verify-repo-hygiene.sh && scripts/verify-api-dump.sh"
        status: pass
    human_judgment: false
  - id: D4
    description: "Real :core Metalava dump has exactly the five sealed types, no copy/componentN, no enum, no stray static field; saved as evidence; no api.txt in any module"
    requirement: "CORE-07"
    verification:
      - kind: other
        ref: "scripts/review-api-surface.sh --expect-sealed-complete --out evidence/api-surface-review.txt -> API SURFACE OK"
        status: pass
    human_judgment: false
  - id: D5
    description: "Public-constructor audit, single @Suppress, no planning ids in main/testFixtures sources"
    verification:
      - kind: other
        ref: "evidence/phase-gate.txt sections [7] and 'constructor audit'"
        status: pass
    human_judgment: false

duration: 25min
completed: 2026-10-01
---

# Phase 2 Plan 9: Phase Gate, Surface Audit and Hand-off Summary

**The merged Phase 2 tree passes the full build and every Phase 1 regression gate; the real `:core` Metalava dump has exactly the five sealed types and no engine-produced class with a public constructor; decisions D-01..D-23 and the hand-off notes for Phases 3-11 are traced here.**

## Performance

- **Duration:** about 25 min (2026-10-01 00:45Z to 01:10Z)
- **Tasks:** 2 (a tracer gate run, then the audit and hand-off)
- **Files:** 2 evidence files created, 1 KDoc line changed

## Accomplishments

- `./gradlew check` exits 0 on the final tree. The `:core` tests were also forced to execute for real (`:core:cleanTest :core:test --no-build-cache`), not restored from cache: 27 classes, 209 tests, 0 failures, 0 errors. All 21 classes named in the validation map have results (counts are in `evidence/phase-gate.txt` section [2]).
- Phase 1 gates hold with the Phase 2 code present: `verify-negative-controls.sh` 69 ok lines and 0 FAIL, `verify-repo-hygiene.sh` HYGIENE OK, `verify-api-dump.sh` API DUMP PROOF OK.
- `review-api-surface.sh --expect-sealed-complete` printed `API SURFACE OK sealed=CommandOutcome,GateDecision,RunTermination,StrategyOutcome,ToolStep classes=117`. The dump (1113 lines, starts with the `Signature format` line, five `sealed exhaustive` declarations) is saved as evidence. No `api.txt` exists in `core/`, `providers/` or `keystore/`.
- Exactly one `@Suppress` in `core/src/main` (`internal/Guarded.kt`). Zero planning-id hits in `core/src/main` and `core/src/testFixtures`.
- Constructor audit: 66 public constructor lines in the dump, 54 distinct owners, all in the allowed list. None of the engine-produced types (CommandOutcome and its leaves, RunTermination, ActionEvent, ExecutedAction, CommitProposal, HeldProposal, DispatchResult, CommandTrace, TierAttempt, PipelineEvent leaves, PendingConfirmation, TierPolicy, CommandSession, CommandPipeline, PipelineBuilder) has a public constructor.

## Task Commits

1. **Task 1: phase gate evidence and API surface review** - `54d521c` (test)
2. **Task 2: constructor audit, hand-off KDoc check** - `ed7b66a` (docs)

## Decision trace D-01..D-23

| Decision | Proven by |
|---|---|
| D-01 sealed CommandOutcome with ordered executed list, commits, held, trace; regular-class leaves | `ExecutedListTest`, `PipelineSpineTest`, `ApiShapeTest` (no data-shaped class, no copy/componentN), dump review (sealed set) |
| D-02 closed RunTermination fired from finally under NonCancellable | `RunClosedPathsTest` (five exit paths, exactly one close each) |
| D-03 open non-sealed taxonomies, no bodies, engine timeout is TIMEOUT | `FailureTaxonomyTest` (timeout vs network distinct), `NeverThrowTest.theEngineDeadlineIsATimeoutNeverANetworkFailureAndKeepsEarlierCommits`, KDoc `else` notes |
| D-04 no generics, opaque context/snapshot/reason | `ApiShapeTest`, `PipelineSpineTest` (opaque context canary), dump review |
| D-05 apply() writes after Admit; CommitSink is notification only | `CommitPathTest`, `PipelineSpineTest.oneMutationFlowsThroughGateApplySinkAndClosesOnce` |
| D-06 gate fun interface, sealed GateDecision, throw becomes Hold with a trace code | `HeldReportingTest.aGateThatThrowsFailsClosedWithATraceCodeAndNoInventedReason`, `CommitPathTest` |
| D-07 AwaitingConfirmGate with post-confirm amend hook | `AwaitingConfirmGateTest` (17 tests incl. amend), `BatchIsolationTest.anAmendedAdmitOfTwoOfThreeItemsAppliesExactlyTwo` |
| D-08 sequential batch, per-item isolation, is_error event | `BatchIsolationTest` |
| D-09 commitHeld skips the gate, idempotent | `DeferModeTest.commitHeldSkipsTheGateAndRunsAsALinkedChildWithItsOwnClose`, `HeldProposal` KDoc |
| D-10 commitHeld opens a new runId linked by parentRunId with its own onRunClosed | `DeferModeTest`, `ParentRunIdTest` |
| D-11 ActionEvent payload: verbatim token, kind, targetIds, context, monotonic position | `ActionEventTest` |
| D-12 ToolStep.Finished carries an explicit kind (preview and is_error streamed) | `ActionEventTest.aFinishedPreviewIsReportedWithoutTheGate`, `CommitPathTest` |
| D-13 suspend, in-order delivery, onRunClosed under NonCancellable | `RunClosedPathsTest.cancelledExitClosesOnce`, `CommitPathTest` |
| D-14 committed includes errored applies, enforced from coordinator counts | `EscalationSafetyTest.anErroredApplyCountsAsCommittedForTheGuard` |
| D-15 suppressed escalation gives Completed(partial = true) | `EscalationSafetyTest`; `CommandOutcome.Completed` KDoc ("did X, couldn't finish") |
| D-16 a HOLD makes the tier terminal for escalation | `EscalationSafetyTest`, `HeldReportingTest` |
| D-17 required gate and sink, build-time misconfiguration throws, execute() never throws | `PipelineBuilderTest` (missing gate, missing sink, zero tiers, duplicate id), `NeverThrowTest` |
| D-18 pre-check with static capabilities; offlineOnly runs zero provider tiers | `TierPolicyTest` (offlineOnly cases, ON_DEVICE hook, PolicyUnavailable) |
| D-19 Usage {inputUncached, cacheRead, cacheWrite, output}, trace types, runId = command id | `TraceTest` (incl. Anthropic vs OpenAI usage parity) |
| D-20 non-suspending listener, throws caught and harmless, ids/codes/counts only | `EventsTest` (ten-event order, throwing listener), `RedactionCanaryTest` |
| D-21 A17 payload clarifications are contract text | `ActionEventTest`, `ExecutedListTest` (source: contract text, no code of its own) |
| D-22 Completed.terminalCall, typed Clarification, ToolSpec.clarification, CommandInput.parentRunId | `TerminalCallTest`, `ToolSpecClarificationTest`, `ParentRunIdTest` |
| D-23 no engine timeout around admit in suspend mode | `AwaitingConfirmGateTest.aConfirmationResolvedAfter119SecondsStillAdmitsBecauseTheEngineAddsNoTimeout` |

No decision is left unmapped.

## Hand-off notes for the Phase 10 README

All are in source KDoc (grep-verified in `evidence/phase-gate.txt`).

1. Partial renders as "did X, couldn't finish", never as full success (`CommandOutcome.Completed.partial`; `commits` and `held` say what was done).
2. Open taxonomies need an `else`: `FailureReason`, `EscalationReason` and `PipelineEvent` can grow after the tag.
3. `PendingMutation.apply` must re-validate: held changes may be committed later and the world may have moved on.
4. `held_for_confirmation` means not done: the model must not retry it (`ToolStep.Finished.held`, `CommitCoordinator`).
5. Dispatch is sequential under `AwaitingConfirmGate`: the gate holds a lock for the whole confirmation window.
6. `HeldProposal` is in-memory only and does not survive the process.
7. The engine adds no timeout around `PreApplyGate.admit` unless the app sets `commandTimeoutMillis`; the app's own gate timeout (SB: 120 s) governs.

## Planner-resolved assumptions (from 02-01-PLAN.md), implemented

For the orchestrator to confirm at R2.

- O1 minimal `ToolSpec` (name, description, inputSchema, mutating, terminal; terminal plus mutating rejected) plus `ToolSpec.clarification(...)`.
- O2 `onAction` delivered under `NonCancellable`; a cancel during `apply()` records an `is_error`, applied action then rethrows; a cancel while the gate is suspended records nothing.
- O3 real cancellation propagates; a leaked `TimeoutCancellationException` while the run is active becomes `Failed(Timeout)`; a plain `CancellationException` from app code propagates (`GuardedTest`, `NeverThrowTest`).
- O4 `CommandOutcome` leaf constructors are internal (confirmed by the constructor audit); consumers build outcomes in tests by running a pipeline with a custom strategy.
- O5 the gate amends through `Admit(amended)`; `ActionEvent` exposes the amended mutation's context.
- A2 a held-then-escalating tier ends `Completed(partial = true)` listing the held proposals.
- A3 a sink that throws after a successful apply is recorded as trace code `sink_error`; the apply is never repeated.
- A4 a second `commitHeld` returns the first call's outcome with no apply and no events; a cancelled first call gives later callers `Failed(Other("commit_held_cancelled"))`.
- A5 `maxIterations < 2` fails `TierPolicy` construction; a throwing `TierPolicySource` becomes `Failed(PolicyUnavailable)` with zero strategy executions.
- A6 `GateDecision.Hold` carries an optional `appOutcomeToken` (SB's verbatim `"held"`); engine fail-closed holds carry null.
- A7 `TerminalCall.asClarification()` returns null (never throws) for non-conforming arguments; empty options allowed.
- A8 `clock` and `runIds` are public optional DSL properties so other modules' tests are deterministic.
- A9 no engine cap on `CommitSink` delay.

## Phase 3 hooks

- Internal ON_DEVICE availability hook: `PipelineBuilder.onDeviceAvailability: suspend () -> Boolean`, default `{ false }`, read at most once per `execute` and only when some tier declares ON_DEVICE; a throwing hook counts as unavailable. No public type exists.
- Internal `RunRecorder.cacheNotEngaged(strategy, provider, model)` emits `CacheNotEngaged`; already tested in `EventsTest`; Phase 3 calls it from the in-`:core` cache detection.
- `ToolSpec` grows additively in Phase 3 (strict and cache members); `terminal` is already settled.
- `TurnRecord` and `CommandSession.recordTurn` are how strategies (Phases 3-9) report model turns and enforce the token ceiling.

## Deviations from Plan

**1. [Rule 1 - Bug] The hand-off note for HeldProposal was worded "In memory only", which the plan's literal check (`in-memory`) does not match**
- **Found during:** Task 1 and Task 2 hand-off KDoc check
- **Fix:** `HeldProposal.kt` KDoc now reads "Held in-memory only: it does not survive the process." (KDoc only, no signature change)
- **Verification:** all hand-off greps PRESENT; `./gradlew check` exit 0; `review-api-surface.sh` still `API SURFACE OK`
- **Commits:** `54d521c`, `ed7b66a` (the first pass used a capital "In-memory", which still failed the case-sensitive grep, so a second pass was needed)

**2. [Rule 1 - Verification gap] The plan's `ctor public` grep undercounts constructors**
- **Issue:** Metalava writes `ctor @KotlinOnly public X(...)` for Kotlin constructors, so `grep -c 'ctor public'` sees 55 of 66 lines and would have missed owners such as `Credential`, `ProviderId` and `FailureReason.BudgetExceeded`.
- **Fix:** the audit matches both forms; the narrow plan grep is still green (55 greater than 0).

**Total deviations:** 2 (both Rule 1, documentation or verification only). **Impact:** none on the public surface; no gate was weakened and no baseline or suppression was added.

## Issues Encountered

None. `02-VALIDATION.md` is untouched (status draft, nyquist_compliant false; finalizer-owned). No tags, no api.txt, `CROSS-REPO-SCOPE-CONTRACT.md` and the section-11 ledger untouched. Nothing under `.planning/graphs/` or `graphify-out/` was staged.

## Next Phase Readiness

Phase 2 is gate-complete on the merged tree. The orchestrator owns phase verification and the tail gates; this plan did not mark the phase complete. Phases 3-5 can start: the transports plug into `StrategyOutcome`/`FailureReason`, the ON_DEVICE hook and `cacheNotEngaged` already exist.

## Self-Check: PASSED

- evidence/phase-gate.txt and evidence/api-surface-review.txt exist; commits 54d521c and ed7b66a found in git log.
- All plan acceptance criteria re-run: phase-gate sections have exit 0, dump starts with `Signature format` with five `sealed exhaustive` declarations, all 21 test classes have zero failures.
