---
phase: 16-start-tier-selection
plan: 04
subsystem: pipeline
tags: [start-tier, picker, fallback, timeout, cancellation, telemetry]

requires:
  - phase: 16-start-tier-selection
    provides: StartTierPicking, SelectionBook, RunPickContext, TierSelector.Custom (plans 16-02, 16-03)
provides:
  - TierPolicy.pickerTimeoutMillis (public val, Builder var, default 2,000 ms, must be positive, in toString)
  - Engine-enforced picker timeout inside the guarded pick (withTimeoutOrNull)
  - SelectionBook.flush(outcome), called first from RunRecorder.flushInFlight
  - StartTierFallbackTest (fallback, timeout, cancellation and deadline matrix)
affects: [16-05, 16-06, 16-07]

actuals:
  tokens: 14000
  tasks: 3
  commits: 3

plan_head_before: 2d653b59c837f98f4aef169fe398678b5af69e2d
commits: 3

key-files:
  modified:
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/pipeline/TierPolicy.kt
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/pipeline/StartTierPicking.kt
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/telemetry/SelectionBook.kt
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/telemetry/RunRecorder.kt
    - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/TierPolicyTest.kt
  created:
    - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/StartTierFallbackTest.kt

key-decisions:
  - "The picker timeout is withTimeoutOrNull inside guarded: expiry is just another unusable answer (router_fallback), while the caller's or the command deadline's cancellation is still rethrown by guarded"
  - "No new trace code: a timeout, throw, null or ineligible id all record the single router_fallback (D-04)"
  - "A cut-off pick is closed by SelectionBook.flush through the existing flushInFlight, so RunRecorder gained no member (still 11)"

requirements-completed: []

duration: 20min
completed: 2026-10-06
status: complete
---

# Phase 16 Plan 04: Picker fallback, timeout and cancellation Summary

**A stuck or faulty start-tier picker can no longer hang or fail a command: the engine cuts it off at `pickerTimeoutMillis` (default 2,000 ms) and every mistake becomes a Linear walk with exactly one `router_fallback`, while caller cancellation still propagates and a cut-off pick still reaches the trace with its tokens.**

## Accomplishments

- Task 1 (tracer): `TierPolicy.pickerTimeoutMillis` added (constant owned by TierPolicy.kt, positive-only, in `toString`, KDoc lists it as engine-enforced). `StartTierPicking` wraps the pick in `withTimeoutOrNull(scope.policy.pickerTimeoutMillis)` inside `guarded`. A picker that hangs 60 s under the default policy is cut off at exactly 2,000 ms of virtual time, the command completes on the first model tier, one `router_fallback`, selection outcome `router_fallback`, agentic never ran. `TierPolicyTest` extended (default, toString, rejects 0 and -1 naming the field, overridable).
- Task 2: fallback matrix pinned in `StartTierFallbackTest` for a null answer, an unknown id, the head's id, a mid-ladder zero-call id (never offered: eligible is `[single, agentic]`), a throwing picker (canary message absent from trace, selection, events and outcome), a foreign `CancellationException` while the caller is active, a leaked inner `withTimeout`, and a head with no model tier left (picker never called, `router_fallback` recorded, selection null, Unhandled). Never Failed and never `strategy_error`. No production change was needed: the 16-02/03 guarded pick already handled all of them.
- Task 3: `SelectionBook.flush(outcome)` closes an open selection with no pick and `RunRecorder.flushInFlight` calls it first. Command deadline mid-pick gives `Failed(Timeout)` with selection outcome `timeout`, its turn and 155 tokens in `trace.usage`, a `StartTierSelected` event, no `router_fallback`. Caller cancel mid-pick propagates `CancellationException`; the sink's single close carries selection `cancelled` with the turn. The earlier of command deadline and picker timeout wins in both orders (virtual time 100 ms).
- Wave-end gate `:core:test :core:detekt :core:scanBannedConstructs :core:metalavaCheckCompatibility` exit 0.

## Deviations

None. Task 2 needed no production change. During Task 3 a detekt MaxLineLength finding in a Task 1 test line was fixed in the Task 2 commit.

## Notes

- Docs (API.md / INTEGRATION.md mention of `pickerTimeoutMillis`) are deliberately left to plan 16-07 per its must-haves.
- Requirement ROUT-03 is also carried by plan 16-07 (docs and the final gate), so it is not marked complete here.

## Self-Check: PASSED

- Commits 0ee2695, 8ccf2b2, 4a0c003 exist on gsd/phase-16-start-tier-selection.
- StartTierFallbackTest.kt exists with 14 tests; all plan acceptance greps matched (RunRecorder still 11 functions).
