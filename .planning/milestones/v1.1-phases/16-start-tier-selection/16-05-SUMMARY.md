---
phase: 16-start-tier-selection
plan: 05
subsystem: pipeline
tags: [start-tier, picker, policy, offline-only, pre-pass, grammar]

requires:
  - phase: 16-start-tier-selection
    provides: StartTierPicking, pickerTimeoutMillis, router_fallback matrix (plans 16-02 to 16-04)
provides:
  - tierPermitted, the one static "may run" rule shared by the tiers and the start-tier picker
  - Ladder.onDeviceAvailable readable
  - StartTierPicking refuses to call a picker the policy forbids (router_fallback, Linear walk)
  - StartTierPolicyTest (9 tests) and StartTierPrePassTest (9 tests)
affects: [16-06, 16-07]

actuals:
  tokens: 16000
  tasks: 3
  commits: 3

plan_head_before: 4bb52356472cf5c4af99f39f80678b4bab342803
commits: 3

key-files:
  modified:
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/pipeline/PolicyPreCheck.kt
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/pipeline/StartTierPicking.kt
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/pipeline/TierWalk.kt
  created:
    - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/StartTierPolicyTest.kt
    - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/StartTierPrePassTest.kt

key-decisions:
  - "The picker is held to the tiers' own static rule (tierPermitted) instead of a second definition; folded into the existing no-model-tier branch so startIn still has two returns"
  - "An unavailable on-device-only tier is not filtered out of eligible (Open Q4): runTier fails loudly for it, as in Linear"

requirements-completed: []

duration: 25min
completed: 2026-10-06
status: complete
---

# Phase 16 Plan 05: Picker obeys policy, head pre-pass pinned Summary

**An offline-only command never pays for a start-tier picker call: the picker is checked against the same `tierPermitted` rule as the tiers before it is called, and the zero-call head (the real P14 grammar tier included) is proven to run first as a free pre-pass with Linear-identical carry and suppression.**

## Accomplishments

- Task 1 (tracer): `PolicyPreCheck.permits` became the top-level `internal fun tierPermitted(capabilities, policy, onDevice)` (pre-check behavior unchanged); `Ladder.onDeviceAvailable` is now a readable `val`; `StartTierPicking(scope, ladder)` records `router_fallback` and starts at 0 without calling the picker when `tierPermitted(spec.capabilities, ...)` is false. `TierWalk.walkPicked` passes `ladder`. Tracer: offline-only with a ready fake ON_DEVICE-only tier and a default (ANY_PROVIDER) picker gives picker 0 calls, provider 0 calls, codes exactly `[tier_skipped_policy, router_fallback]`, selection null, and `local_model` completes.
- Task 2: policy matrix in `StartTierPolicyTest`: offlineOnly with no on-device tier, `allowedProviders = emptySet()` and `maxTier` at the head each leave no model tier (picker and provider never called, `router_fallback`, Unhandled with `cappedByPolicy == true`); a whole-ladder offline refusal is a plain `Failed(ProviderUnavailable(ON_DEVICE, offline_unavailable))` with no `router_fallback`; a head that completes or fails ends the walk with no picker, no fallback and no selection; a picker declaring a provider the allowed set excludes is not called; an ON_DEVICE-declaring picker runs offline but the bind-time `providerGate` still refuses the cloud model the app mapped (`provider_not_allowed`); a Custom picker is still called once with a single eligible tier (D-06).
- Task 3: `StartTierPrePassTest`: head Completed/Failed never call the picker; the head's escalation carry reaches the picked tier by `assertSame` identity with `carryIn` true; a two-tier zero-call head runs fully before the picker; write-then-escalate is suppressed for the head and for the picked tier alike; a mid-ladder zero-call tier is never offered; with the real `LocalGrammarStrategy` head a matching phrase completes with zero picker and zero provider calls, and a non-matching phrase gives grammar `no_match` then one picker call with only `[single, agentic]`.
- Wave-end gate `:core:test :core:detekt :core:scanBannedConstructs :core:metalavaCheckCompatibility` exit 0. `TierWalkLinearCharacterizationTest` unchanged and green.

## Deviations

- Rule 3 (formatting): the `TierWalk.walkPicked` return line exceeded the 120-column detekt limit once `ladder` was added, so it was wrapped in a `run { }` block (same behavior). The `StartTierPicking(scope, ladder)` literal stays on one line as the plan's acceptance grep requires. The fix is in the Task 2 commit.
- Task 3's "head that suppresses" bullet from Task 2's behavior list is covered in `StartTierPrePassTest` (write-then-escalate), not duplicated in `StartTierPolicyTest`.
- No gap found in `StartTierPicking` beyond the planned permit check; no production change in Tasks 2 and 3 other than the formatting above.
- Red-first on Task 1: the production change and the tracer test were written together, so the red run was not separately observed (git stash is prohibited); the test's assertions (picker 0 calls under an ANY_PROVIDER picker offline) would fail on the pre-change code.

## Notes

- ROUT-02 and ROUT-04 are also carried by plan 16-07 (and ROUT-05 completes in 16-06), so no requirement is marked complete here.
- JVM-only; no device, no live spend, no docs touched.

## Self-Check: PASSED

- Commits 8bc9065, 9f414e4, 7f1b74c exist on gsd/phase-16-start-tier-selection; `git rev-list --count` from the ledger base gives 3.
- All acceptance greps matched (tierPermitted 1, private permits 0, tierPermitted(spec.capabilities 1, StartTierPicking(scope, ladder) 1, 9 @Test in each new test class, cappedByPolicy 3, provider_not_allowed 1, emptySet() 1, LocalGrammarStrategy 3, assertSame 3, escalation_suppressed 2, domain words 0).
