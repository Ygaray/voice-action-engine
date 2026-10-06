---
phase: 16-start-tier-selection
plan: 02
subsystem: pipeline
tags: [start-tier, picker, tier-selector, trace, telemetry]

requires:
  - phase: 16-start-tier-selection
    provides: TierWalkLinearCharacterizationTest (whole-walk pin of Linear/Fixed), RT-01 step id cap
provides:
  - StartTierPicker seam and PickContext (public, no submit, no carry)
  - TierSelector.Custom(picker) with an internal picking hook; TierWalk.walkPicked (zero-call head, picker over LLM-only ids)
  - CommandTrace.selection (StartTierSelection) with the picker's turns, folded into trace.usage
  - TraceCode.ROUTER_FALLBACK, ScriptedPicker fixture, start-tier test rig
affects: [16-03, 16-04, 16-05, 16-06, 16-07]

actuals:
  tokens: 9000
  tasks: 2
  commits: 2

plan_head_before: 99a7b6af391364983f080f4ee2a84f0eda4a3684
commits: 2

tech-stack:
  added: []
  patterns:
    - "Internal picking hook on the public selector base class (no internal intermediate supertype), Linear/Fixed keep the unchanged climb path"
    - "Picker turns routed by picker id into a SelectionBook outside RunRecorder, so RunRecorder keeps 11 member functions"

key-files:
  created:
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/pipeline/StartTierPicker.kt
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/pipeline/StartTierPicking.kt
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/telemetry/StartTierSelection.kt
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/telemetry/SelectionBook.kt
    - core/src/testFixtures/kotlin/io/github/ygaray/voiceactionengine/core/testing/ScriptedPicker.kt
    - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/StartTierTestSupport.kt
    - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/StartTierPickerTest.kt
  modified:
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/pipeline/TierSelector.kt
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/pipeline/TierWalk.kt
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/telemetry/TraceCode.kt
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/telemetry/RunRecorder.kt
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/telemetry/CommandTrace.kt
    - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/TierWalkLinearCharacterizationTest.kt
    - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/TraceTest.kt

key-decisions:
  - "Exposed-supertype assumption A1 confirmed: the picking hook is an internal open member of the public TierSelector; it compiled first time"
  - "Detekt TooManyFunctions assumption A2 held: RunRecorder has 11 member functions, TierWalk 10, detekt green"
  - "ScriptedPicker throws AssertionError on a dry script (ScriptedResponses alone throws IllegalStateException, which guarded would swallow into a silent fallback)"

requirements-completed: [ROUT-01, ROUT-02]

duration: 35min
completed: 2026-10-06
status: complete
---

# Phase 16 Plan 02: Custom start-tier picker and its trace record Summary

**TierSelector.Custom(picker) runs the zero-call head, offers the picker only the eligible LLM tier ids, starts the walk where it points, and records the picker's turns in CommandTrace.selection so trace.usage matches what the run paid.**

## Accomplishments

- Task 1 (tracer): `StartTierPicker` (fun interface, one abstract method) and `PickContext` (internal ctor; runId, policy, tokensUsed, model(), recordTurn; no submit, no carry). `TierWalk.run` keeps the Linear/Fixed expression `climb(ladder.tiers.drop(start), input)` and branches on `ladder.selector.picking` into a private `walkPicked` (head via `climb`, then `climb` from the picked index). `StartTierPicking.startIn` filters to model tiers, calls the picker under `guarded`, accepts only an id in the eligible list, else records `router_fallback`. The picker's model is bound through `ModelRouter.bind` under its own `start_tier_picker` id, so its call counts in `tokensUsed` (the agentic tier saw 5 tokens).
- Task 2: `StartTierSelection` (picker, outcome, picked, eligible, tiersBypassed, latencyMillis, turns, usage) and `SelectionBook` route the picker's turns by id away from `trace.attempts`. `CommandTrace.selection` is the last defaulted constructor parameter; `usage` folds attempts plus selection; `toString` only mentions the selection when non-null. `tiersBypassed` is the picked tier's index in the eligible LLM list. Every characterization case now asserts `selection == null`.
- Wave-end gate (`:core:test :core:detekt :core:scanBannedConstructs :core:metalavaCheckCompatibility`) exit 0. `core/api.txt` not regenerated.

## Task Commits

1. **Task 1: Custom picker, head pre-pass, LLM-only eligible ids, budget counted (tracer)** - `b3bf82a`
2. **Task 2: CommandTrace.selection with the picker's turns, usage fold, tiersBypassed** - `4e7a39e`

## Deviations from Plan

**1. [Rule 3 - Blocking] TraceTest code-inventory guard**
- **Found during:** Task 2 verification (`*TraceTest*` is in the plan's verify list)
- **Issue:** `TraceTest.theAgenticLoopCodesHaveTheirSnakeCaseWireValues` compares every declared `TraceCode` constant against a hand-kept inventory; the new `ROUTER_FALLBACK` made it red.
- **Fix:** Added a `startTierCodes` map, a wire-value test, and included it in the inventory union. Additive only.
- **Files modified:** core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/TraceTest.kt
- **Commit:** 4e7a39e

**2. [Plan sequencing] Task 1 left selection recording out**
- The SelectionBook calls in `StartTierPicking` land in Task 2 (the class did not exist yet), as the plan splits them. The test-support default-selection line and one SelectionBook line were wrapped for the 120-character detekt limit.

The `ScriptedPicker` dry-script behavior follows the plan (AssertionError); it needed an explicit check because `ScriptedResponses.next()` throws IllegalStateException.

The plan's last Task 2 acceptance command writes to `${TMPDIR:-/tmp}`, which is not writable in this sandbox. The same check was run directly: `git diff c41e123 -- TierWalkLinearCharacterizationTest.kt` has 12 added lines and 0 removed lines.

## Verification

- Acceptance greps: TierWalk 10 functions, RunRecorder 11 own functions, `router.bind(` once, no `submit` in StartTierPicker.kt, `climb(ladder.tiers.drop(start), input)` once.
- Compile confirmed assumption A1 (exposed supertype) and detekt confirmed A2 (`>=` threshold; no TooManyFunctions finding).

## Issues Encountered

None. No Gradle run was killed by earlyoom.

## Threat Flags

None beyond the plan's register (T-16-04 to T-16-09 mitigated as written: no submit/carry on PickContext, `choice in llm` validation, picker turns in `selection`, id-only toStrings, `guarded` pick, router bind under the picker's own id).

## Self-Check: PASSED

- FOUND: StartTierPicker.kt, StartTierPicking.kt, StartTierSelection.kt, SelectionBook.kt, ScriptedPicker.kt, StartTierTestSupport.kt, StartTierPickerTest.kt
- FOUND commits: b3bf82a, 4e7a39e
