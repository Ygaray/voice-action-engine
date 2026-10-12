---
phase: 12-wave-1-seams-w04-fix
plan: 03
subsystem: core
tags: [trace, tier-attempt, carry-in, unhandled, capped-by-policy, additive-api]
requires: []
provides:
  - TierAttempt.carryIn (presence only), derived from the walk's own carry at tier start
  - CommandOutcome.Unhandled.cappedByPolicy, true when PolicyPreCheck skipped any tier (offline-only included) and no tier handled the command
affects: [14, 16]
tech-stack:
  added: []
  patterns: [engine-derived trace facts computed from the walk and pre-check, never from a strategy's claim]
key-files:
  created: []
  modified:
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/telemetry/CommandTrace.kt
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/telemetry/RunRecorder.kt
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/pipeline/TierWalk.kt
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/pipeline/PolicyPreCheck.kt
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/pipeline/CommandOutcome.kt
    - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/TierWalkTest.kt
    - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/ModelRouterTest.kt
    - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/CacheNotEngagedTest.kt
    - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/InFlightTierTraceTest.kt
    - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/TraceTest.kt
    - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/TierPolicyTest.kt
key-decisions:
  - "carryIn means the walk's carry was non-null when the tier started; an escalation carrying null, a no-match and the first tier all give false"
  - "cappedByPolicy is eligible.size < strategies.size from PolicyPreCheck, so both tier_skipped_policy sites count; a refused ladder passes false because it never reaches Unhandled"
requirements-completed: [SEAM-04, SEAM-05]
status: complete
plan_head_before: 73f69880bbab865d574dbed16a74b848ef8c2953
commits: 3
metrics:
  completed: 2026-10-05
actuals:
  tokens: 7000
  tasks: 3
  commits: 3
---

# Phase 12 Plan 03: carryIn and cappedByPolicy Summary

Every outcome's trace now says per tier whether it received a carry (`TierAttempt.carryIn`), and every `Unhandled` says whether policy capped the ladder (`Unhandled.cappedByPolicy`). Both are strictly additive: internal constructors only, no `api.txt` change.

## What changed

- **carryIn**: `RunRecorder.tierStarted(strategy, carryIn)` takes the flag (no default); `TierBook` stores it and both `tierFinished` and `flushInFlight` build the attempt with it, so deadline-cut and cancelled tiers report it too. `TierWalk.runTier` passes `carry != null`, the same value handed to `RunSession`. `TierAttempt.carryIn` sits before the trailing `turns` default, so `STUB_EXCEPTIONS` is untouched. `toString` prints `carryIn=true/false` only; the carry object appears nowhere.
- **cappedByPolicy**: `Ladder` gets a fifth constructor value, `eligible.size < strategies.size` for a non-empty eligible list (true whenever either `tier_skipped_policy` site fired) and false for a refused ladder. `TierWalk.run` passes it into `Unhandled`. The KDoc states it covers every `tier_skipped_policy` case, offline-only included, and that an all-skipped ladder fails with `NoEligibleTier` or `ProviderUnavailable` instead.
- **Tests**: `TierWalkTest` (4 carryIn cases), `InFlightTierTraceTest` (deadline and cancel with and without carry), `TraceTest` (canary carry never appears in attempt, trace, outcome or event strings), `TierPolicyTest` (8 cases: maxTier, provider restriction, offline-only, nothing skipped, all-skipped, later completion, selector start, toString).

## Verification

`:core:test :core:detekt :core:scanBannedConstructs :core:apiCheck` green; `git diff --exit-code v1.0.1 -- core/api.txt` clean. No behavioral or device verification was performed; none is in this plan.

## Deviations from Plan

**1. [Rule 1 - Bug] Over-long lines flagged by detekt**
- A test fixture line in `InFlightTierTraceTest` and the `Unhandled(...)` return in `TierWalk.run` exceeded the line limit; wrapped, behavior unchanged.

Otherwise the plan was executed as written. The Task 2 tests passed on first run, confirming Task 1's recorder plumbing already covered flushed attempts.

## Self-Check: PASSED

All modified files exist; commits 68b2c29, 29d1f86 and fa6a710 exist on main.
