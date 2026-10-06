---
phase: 17-run-level-undo
plan: 02
subsystem: core-seams
tags: [kotlin, commit-sink, held-run, composite-sink, additive-api]

requires:
  - phase: 17-01
    provides: ":undo scaffold (serialization of Gradle-running plans only; no code used)"
provides:
  - "ActionEvent.heldRunId: set only on actions applied by commitHeld, equal to the held proposal's runId"
  - "compositeSink(vararg CommitSink): ordered, awaited fan-out with child fault isolation"
  - "HeldRunIdTest and CompositeSinkTest matrices; API.md rows"
affects: [17-03, 17-04, 17-05, 17-06, 17-07, 17-08, 17-09, 17-10, 18, 20]

actuals:
  tokens: 5800
  tasks: 3
  commits: 3
plan_head_before: e12f3718a40b31c68daf59148cd1bf4b6df72682
commits: 3

tech-stack:
  added: []
  patterns:
    - "Internal constructor parameters threaded explicitly, never defaulted (ApiShapeTest sweeps internal classes)"
    - "Fan-out that reuses the single guarded helper: no new catch, no second suppression"

key-files:
  created:
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/commit/CompositeSink.kt
    - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/HeldRunIdTest.kt
    - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/CompositeSinkTest.kt
  modified:
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/commit/CommitSink.kt
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/commit/ApplyStep.kt
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/commit/CommitCoordinator.kt
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/pipeline/HeldCommit.kt
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/pipeline/CommandPipeline.kt
    - API.md

key-decisions:
  - "compositeSink fault policy: run every child, then throw one fixed-text counts-only exception, so the pipeline records sink_error while the outcome is unchanged (accepted by the orchestrator)"
  - "A child's own exception or message is never forwarded or attached as a cause; guarded reduces a fault to its class name anyway"

patterns-established:
  - "Group a command's actions by heldRunId ?: runId; a clarification reply is its own group and keeps parentRunId"

requirements-completed: [UNDO-04]

coverage:
  - id: D1
    description: "Every ActionEvent of a commitHeld child (both overloads, every proposal of a multi-hold run, a hold inside a reply) carries heldRunId equal to the held run's id; every other event carries null"
    requirement: UNDO-04
    verification:
      - kind: integration
        ref: "core HeldRunIdTest (8 tests) through the real commandPipeline"
        status: pass
    human_judgment: false
  - id: D2
    description: "compositeSink calls children in order, awaited; a throwing child (first, last, all, close-only, foreign cancellation) never stops a sibling and never changes the run; sink_error stays visible; caller cancellation still propagates; failure text is counts only"
    requirement: UNDO-04
    verification:
      - kind: integration
        ref: "core CompositeSinkTest (10 tests)"
        status: pass
    human_judgment: false
  - id: D3
    description: ":core stays additive and edge-free: Metalava compat vs v1.0.1, dependency allowlist, banned constructs, detekt, ApiShapeTest, docs coverage"
    requirement: UNDO-04
    verification:
      - kind: other
        ref: ":core:test :core:detekt :core:scanBannedConstructs :core:metalavaCheckCompatibility :core:verifyCoreDependencyAllowlist (exit 0); scripts/verify-docs-coverage.sh -> DOC COVERAGE OK checks=25"
        status: pass
    human_judgment: false

duration: 20min
completed: 2026-10-06
status: complete
---

# Phase 17 Plan 02: heldRunId and compositeSink Summary

**`ActionEvent.heldRunId` (set only by commitHeld, so a held child groups under the command that held it) and `compositeSink(...)` (journal beside the app sink, each child isolated from its siblings and from the run), both purely additive to `:core`.**

## Performance

- **Duration:** about 20 min
- **Completed:** 2026-10-06
- **Tasks:** 3
- **Files modified:** 9 (3 created, 6 modified)

## Accomplishments
- `heldRunId` is threaded explicitly through `CommitCoordinator` and `ActionDelivery` (no defaults). `HeldCommit.open` passes `held.runId`; `startRun` passes null. `ActionEvent.toString` prints `heldRunId=set|null`, never the value.
- Matrix pinned end to end: both `commitHeld` overloads, two proposals of one run, a clarification reply (keeps `parentRunId`, null `heldRunId`), a reply that itself holds then commits (groups under the reply), a throwing apply inside the child, a mixed hold/admit run.
- `compositeSink` copies its varargs, rejects an empty call, and routes each child call through `guarded` (not the uncancellable variant), so under the pipeline's NonCancellable delivery a child's foreign cancellation is a fault, while a real caller cancellation outside the pipeline still propagates and later children are skipped.
- API.md names `compositeSink` (packages block, public-functions sentence), `heldRunId` (ActionEvent row), the combine hint on the CommitSink row, and the grouping rule in the write-path section.

## Task Commits

1. **Task 1: tracer, heldRunId through the real pipeline** - `b1ae09e` (feat)
2. **Task 2: heldRunId matrix** - `42a7d14` (test)
3. **Task 3: compositeSink, tests, API.md** - `487b4c2` (feat)

**Plan metadata:** committed separately (docs: complete plan)

## Decisions Made
- Fault policy as the plan recorded it (for the 17-09 surface review): all children run, then one fixed-text counts-only `IllegalStateException` subtype (private to the file) so `sink_error` stays visible in the trace. No original exception is rethrown, because that would need a second catch, which the repository forbids.

## Deviations from Plan

None to the implementation. Two small notes:
- Task 1's commit carries only the tracer test and Task 2's commit adds the rest of the matrix; the full file was verified green in one Gradle run before the split.
- The close-only throwing child test asserts `sink_error` on the listener's engine-code events rather than `outcome.trace.codes`: the outcome's trace is taken before the close, so a code raised during the close reaches the event stream only (same pattern as `ForeignCancellationTest`). This is a test-expectation correction, not a product change.

## Issues Encountered
None.

## Verification
- `:core:test` (full), `:core:detekt`, `:core:scanBannedConstructs`, `:core:metalavaCheckCompatibility`, `:core:verifyCoreDependencyAllowlist` exit 0 in one low-memory Gradle invocation; `:core:verifyModuleGraph` also green earlier in the plan.
- `scripts/verify-docs-coverage.sh` -> `DOC COVERAGE OK checks=25 types=107`.
- Acceptance greps: one `public val heldRunId`, one `public fun compositeSink(vararg sinks: CommitSink): CommitSink`, zero `catch` in `CompositeSink.kt`, one file with `@file:Suppress` in `core/src/main`.
- `core/api.txt` byte-identical to 147a959.

## Known Stubs
None.

## Threat Flags
None. T-17-05 through T-17-08 are covered by `CompositeSinkTest`, T-17-07 by `HeldRunIdTest`, T-17-02 by the allowlist and module-graph gates.

## Self-Check: PASSED
