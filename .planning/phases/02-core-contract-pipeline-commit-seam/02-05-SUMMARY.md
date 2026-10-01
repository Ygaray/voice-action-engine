---
phase: 02-core-contract-pipeline-commit-seam
plan: 05
subsystem: core
status: complete
tags: [kotlin, commit-seam, pre-apply-gate, hold, fail-closed, cancellation, batch-isolation, detekt]

requires:
  - phase: 02-core-contract-pipeline-commit-seam
    provides: "02-03 CommitCoordinator spine and fixtures; 02-02 TraceCode, guarded helper, ActionKind, FinishedKind; 02-04 never-throw execute and run close"
provides:
  - "internal ActionLedger (positions, executed list, committed subset, held proposals, appliedCount, heldCount)"
  - "internal GateStep: guarded gate call, fail-closed Hold with gate_error, reason never constructed"
  - "internal ApplyStep and ActionDelivery: apply outcomes, cancel-mid-apply journaling under NonCancellable, sink isolation"
  - "Hold branch with HeldProposal, Finished PREVIEW/ERROR streaming, per-item batch isolation in CommitCoordinator"
affects: [02-06, 02-07, 02-08, 02-09, Phase 3, Phase 4]

plan_head_before: 632002dcfe6578a7df9c5be4b2316207920c5304

actuals:
  tokens: 14000   # chars/4 over the realized diff of core/ (56,588 chars incl. diff headers)
  tasks: 3
  commits: 3

tech-stack:
  added: []
  patterns:
    - "One step per file (GateStep, ApplyStep) keeps the coordinator under detekt's function and length limits with no suppression"
    - "Every sink delivery runs under withContext(NonCancellable) through guarded; a sink fault is a sink_error code, never a retry"
    - "A cancellation caught around apply is journaled (is_error, applied true) and then rethrown"
    - "Constants stay file-private; the model-facing error bytes are read through internal fun applyErrorContent()"

key-files:
  created:
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/commit/ActionLedger.kt
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/commit/GateStep.kt
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/commit/ApplyStep.kt
    - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/HeldReportingTest.kt
    - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/ActionEventTest.kt
    - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/CommitPathTest.kt
    - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/BatchIsolationTest.kt
    - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/ExecutedListTest.kt
  modified:
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/commit/CommitCoordinator.kt

key-decisions:
  - "APPLY_ERROR_CONTENT is a file-private const in ApplyStep.kt read through internal fun applyErrorContent(); an internal const would be a public static field and fail ApiShapeTest (same finding as 02-02 and 02-03)"
  - "ActionLedger.record takes an ActionDetails holder (toolName, token, targetIds, context, mutating) instead of seven parameters, to stay under detekt's LongParameterList without tuning"
  - "An unknown FinishedKind (the set is open) is treated as a read: content returned, nothing reported"
  - "Held actions and the other mutation-bound actions are mutating=true; Finished PREVIEW and ERROR actions are mutating=false, matching ExecutedAction's KDoc"

patterns-established:
  - "Behavior table in the plan is implemented one-to-one and each row has a named test"

requirements-completed: [GATE-01, GATE-03, GATE-04]
requirements-partial: [GATE-06]

duration: ~40min
completed: 2026-09-30
---

# Phase 2 Plan 05: Commit path completion (hold, apply outcomes, batch isolation) Summary

**The single write path is complete: a hold writes nothing and is reported per mutation with the app's verbatim token, a throwing gate fails closed with a trace code, every apply outcome (ok, isError, throw, cancel) is reported exactly once, and a batch applies item by item with isolation.**

## Performance

- **Tasks:** 3 of 3 (Task 1 tracer, Tasks 2 and 3 TDD)
- **Commits:** 48f6fe8 (task 1), 0747364 (task 2), 012ca31 (task 3)
- **Files:** 8 created, 1 modified (CommitCoordinator.kt), 0 deleted

## Accomplishments

- **Hold (GATE-03):** `Hold` records one `held` action per mutation (applied false, the gate's token verbatim, context by identity), adds one `HeldProposal` carrying the app's reason object by identity, and the strategy receives exactly `{"applied":false,"status":"held_for_confirmation"}` with `held = true`. Nothing is applied; held actions are in `executed`, never in `commits`.
- **Fail closed:** a gate that throws (non-cancellation) becomes a bare `Hold()` plus trace code `gate_error`; the proposal's reason and token are null, so the engine never invents a reason. A gate cancelled while suspended records and delivers nothing and the run closes `Cancelled` with an empty executed list.
- **Apply outcomes:** success is `committed`; a result with `isError` is `is_error` with applied true and is excluded from `commits`; a throw is `is_error`, applied true, token null, code `apply_error`, and the strategy sees `isError` true with `{"status":"error"}`; cancellation mid-apply is recorded as `is_error` applied true with code `apply_cancelled`, delivered under `NonCancellable`, then rethrown.
- **Finished steps (GATE-04):** PREVIEW and ERROR are streamed as `preview` / `is_error` actions with applied false and mutating false, without calling the gate; READ is never reported.
- **Sink isolation:** a sink that throws after an apply is recorded as `sink_error`; the apply is never repeated, the action stays in `executed`, and the next mutation still applies.
- **Amend (GATE-01):** `Admit(amended)` applies the amended list; the original is never applied and the event's context is the amended mutation's context object.
- **Batch isolation:** a three-item batch with a failing middle item produces `committed, is_error, committed` at positions 0, 1, 2; each item applies exactly once (CT's "Logged 2 of 3" is a count of `is_error` events).
- **Executed list (GATE-06, part):** Completed, Failed, Unhandled and cross-tier outcomes carry the ordered, cumulative executed list of all four kinds and the committed subset.

## Behavior table as implemented

| Step | Gate called | Recorded action | applied | Token | Content to strategy | isError |
|------|-------------|-----------------|---------|-------|---------------------|---------|
| Finished READ | no | none | - | - | result content | result.isError |
| Finished PREVIEW | no | preview | false | result token | result content | result.isError |
| Finished ERROR | no | is_error | false | result token | result content | true |
| Mutation, Admit, apply ok | yes | committed | true | result token | result content | false |
| Mutation, Admit, apply isError | yes | is_error | true | result token | result content | true |
| Mutation, Admit, apply throws | yes | is_error + apply_error | true | null | `{"status":"error"}` | true |
| Mutation, Admit, apply cancelled | yes | is_error + apply_cancelled, delivered, rethrown | true | null | (cancellation propagates) | - |
| Mutation, Hold | yes | held per mutation + one HeldProposal | false | Hold's token | held JSON | false |
| Mutation, gate throws | yes | held + gate_error, reason null, token null | false | null | held JSON | false |
| Mutation, gate cancelled | yes | none | - | - | (cancellation propagates) | - |

## Final internal interface (plan 06 reads appliedCount and heldCount)

```
CommitCoordinator(runId, parentRunId, gate: PreApplyGate, sink: CommitSink, recorder: RunRecorder)   // unchanged
  suspend fun submit(step: ToolStep): DispatchResult
  fun executed(): List<ExecutedAction>      fun held(): List<HeldProposal>
  val appliedCount: Int    // actions whose apply ran, including errored and cancelled applies
  val heldCount: Int       // held proposals
  internal fun heldForConfirmationContent(): String      // top-level, in CommitCoordinator.kt

ActionLedger()           record(kind, applied, details: ActionDetails): ExecutedAction; addHeld(HeldProposal)
                         executed(); committed(); held(); appliedCount; heldCount
GateStep(gate, recorder).decide(proposal): GateDecision                       // GATE_ERROR on fault
ActionDelivery(runId, parentRunId, sink, recorder).deliver(action)            // NonCancellable + guarded, SINK_ERROR on fault
ApplyStep(ledger, delivery, recorder).run(mutation): AppliedChange(action, content)
internal fun applyErrorContent(): String                // {"status":"error"}, top-level in ApplyStep.kt
```

## Verification evidence

- `./gradlew :core:check :providers:check` (not piped): exit 0, BUILD SUCCESSFUL. detekt zero issues, `scanBannedConstructs` clean, all three OkHttp matrix legs ran.
- `:core` tests: 145 total, 0 failures, 0 errors, 0 skipped (new: HeldReportingTest 4, ActionEventTest 5, CommitPathTest 7, BatchIsolationTest 3, ExecutedListTest 4).
- `scripts/review-api-surface.sh --expect-sealed-complete`: exit 0, `API SURFACE OK sealed=CommandOutcome,GateDecision,RunTermination,StrategyOutcome,ToolStep classes=101`.
- `@Suppress` count in `core/src/main`: 1 (Guarded.kt only). No `api.txt` created.
- The tracer gate (Task 1) was re-run end to end before Task 2: HeldReportingTest and PipelineSpineTest green, detekt and scanBannedConstructs clean.
- `git rev-list --count 632002d..HEAD` = 3 at the last task commit.

## Deviations from Plan

**1. [Rule 1 - Bug] `internal const val APPLY_ERROR_CONTENT` would fail the surface lint**
- The plan names `internal const val APPLY_ERROR_CONTENT`. A top-level internal const compiles to a public static field, which ApiShapeTest forbids (found already in 02-02 and 02-03).
- Fix: file-private const exposed through `internal fun applyErrorContent()`; the acceptance greps still hold. The same applies to the held bytes, as 02-03 already recorded.

**2. [Rule 3 - Blocking] detekt findings fixed by restructuring, not tuning**
- A `MaxLineLength` hit in `CommitCoordinator.kt` and three in `ExecutedListTest.kt` were reflowed and the test steps pulled into small helpers. No config change, no baseline.

**3. Test log shape in BatchIsolationTest**
- The plan's six-entry log (`apply:a ... sink:action:2:committed`) contains no gate entry, so the batch test uses a gate without a log and filters the final `sink:closed` entry. The exact six-entry order is asserted.

## Requirements bookkeeping

Marked complete: GATE-01 (one engine-owned write path with Admit(amended) and Hold, opaque context surviving to the event), GATE-03 (optional app-typed reason never constructed, held JSON byte-exact, held proposals on every outcome), GATE-04 (per-action notification for committed, held, preview and is_error). GATE-06 is left Pending: the ordered executed list and committed subset are delivered, but plan 06 still owns the no-escalation-after-write guard that the requirement's "every outcome variant" evidence also covers.

## Known stubs / deferred

None in this plan's scope. The escalation guard (plan 06) will read `appliedCount` and `heldCount`; `TierWalk` still escalates regardless until then. Pre-existing unrelated working-tree changes (.planning/graphs, config.json, milestone files, .gsd, state.json) were left untouched and unstaged.

## Self-Check: PASSED

- All 8 created files exist; commits 48f6fe8, 0747364 and 012ca31 are present in `git log`.
