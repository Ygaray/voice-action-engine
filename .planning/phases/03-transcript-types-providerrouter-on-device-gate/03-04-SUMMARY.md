---
phase: 03-transcript-types-providerrouter-on-device-gate
plan: 04
subsystem: core
status: complete
tags: [kotlin, telemetry, trace, fallback, trace-codes, prov-02, prov-10]

requires:
  - phase: 02-core-contract-pipeline-commit-seam
    provides: "TurnRecord, TierAttempt, CommandSession.recordTurn, ProviderCall event, TraceCode value class"
provides:
  - "TurnRecord.fallbackFrom (new 7-argument primary constructor, 6-argument constructor preserved)"
  - "TierAttempt.fallbackFrom derived from the last reported turn, printed in toString"
  - "Thirteen router TraceCode constants (PROV-02 refusals, D-06/D-07 fallback visibility)"
affects: [03-06, 03-07, 03-08]

plan_head_before: f1f1fd5e2a8109b8ed747c65c43bf9c4ca69f976

commits: 2

actuals:
  tokens: 3200   # chars/4 over the realized diff (about 12,900 chars)
  tasks: 2
  commits: 2

tech-stack:
  added: []
  patterns:
    - "Constructor growth without default arguments: new primary with the extra parameter, old signature kept as a delegating secondary constructor"

key-files:
  created: []
  modified:
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/telemetry/TurnRecord.kt
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/telemetry/CommandTrace.kt
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/telemetry/TraceCode.kt
    - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/TraceTest.kt

key-decisions:
  - "TierAttempt.fallbackFrom is derived from the last reported turn (same rule as provider and model), so no new constructor parameter and no visibility change on TierAttempt or CommandTrace"
  - "fallbackFrom was inserted in TierAttempt.toString before usage, after model"

requirements-completed: [PROV-10, PROV-02]

coverage:
  - id: D1
    description: "A turn reported with fallbackFrom = ON_DEVICE appears on the tier attempt, in the ProviderCall event and in toString; a 6-argument turn or a tier with no turns reports null"
    requirement: "PROV-10"
    verification:
      - kind: unit
        ref: "core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/TraceTest.kt#aFallbackTurnFlowsToTheAttemptTheEventAndTheToString"
        status: pass
      - kind: unit
        ref: "core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/TraceTest.kt#aSixArgumentTurnHasNoFallbackAndATierWithNoTurnsHasNone"
        status: pass
    human_judgment: false
  - id: D2
    description: "Thirteen router trace codes exist with lower snake case wire values; all 28 codes are distinct and match [a-z0-9_]+; on_device_unavailable is unchanged"
    requirement: "PROV-02"
    verification:
      - kind: unit
        ref: "core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/TraceTest.kt#everyTraceCodeIsDistinctAndLowerSnakeCase"
        status: pass
      - kind: unit
        ref: "core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/TraceTest.kt#theRouterCodesHaveTheirSnakeCaseWireValues"
        status: pass
      - kind: command
        ref: "scripts/review-api-surface.sh (API SURFACE OK)"
        status: pass
    human_judgment: false
---

# Phase 3 Plan 04: Trace Fallback Visibility and Router Trace Codes Summary

**TurnRecord and TierAttempt now say which provider a turn fell back from (additively, 6-argument constructor kept), and TraceCode gains the thirteen documented refusal and fallback codes the router will record.**

## Accomplishments

- `TurnRecord(provider, model, stopReason, toolNames, usage, latencyMillis, fallbackFrom: ProviderId?)` is the new primary constructor (no default value); the original six-argument constructor remains and delegates with null. Every Phase 2 call site (EventsTest, RedactionCanaryTest, TraceTest) compiles unchanged.
- `TierAttempt.fallbackFrom` reads `turns.lastOrNull()?.fallbackFrom`; it prints as `fallbackFrom=on_device` in `TierAttempt.toString` and `TurnRecord.toString`.
- TraceTest proves the flow end to end: strategy -> `session.recordTurn` -> recorder -> `TierAttempt.fallbackFrom` and the `ProviderCall` event's turn.
- Thirteen new `TraceCode` values, plus the updated `ON_DEVICE_UNAVAILABLE` KDoc (value unchanged).

## The Thirteen Codes

| Constant | Wire value |
|----------|-----------|
| PROVIDER_NOT_SELECTED | provider_not_selected |
| SELECTION_SOURCE_ERROR | selection_source_error |
| PROVIDER_NOT_ALLOWED | provider_not_allowed |
| PROVIDER_NOT_REGISTERED | provider_not_registered |
| CREDENTIAL_MISSING | credential_missing |
| CREDENTIAL_UNREADABLE | credential_unreadable |
| CREDENTIAL_SOURCE_ERROR | credential_source_error |
| CREDENTIAL_MISMATCH | credential_mismatch |
| CAPABILITY_LOOKUP_ERROR | capability_lookup_error |
| CAPABILITY_REFUSED | capability_refused |
| PROVIDER_ERROR | provider_error |
| PROVIDER_FALLBACK | provider_fallback |
| FALLBACK_REFUSED | fallback_refused |

## Task Commits

1. **Task 1 (tracer): fallbackFrom per turn and per tier attempt** - `330dcd6` (feat)
2. **Task 2: thirteen router trace codes** - `f7900c6` (feat)

## Deviations from Plan

**1. [Process] Task 2 RED not committed separately.** The test was written first (it would not compile without the new constants) but committed together with the implementation, as in 03-03.

**2. [Rule 3 - Blocking, minor] Line-length wraps** to stay under the 120-column detekt limit in TraceTest and the `TierAttempt.fallbackFrom` KDoc; no suppression.

**Total deviations:** 2 process/format notes. **Impact:** none on scope or public API.

## Verification

- `./gradlew :core:test :core:detekt :core:scanBannedConstructs -q`: pass (full :core suite, every existing 6-argument call site unchanged)
- `./gradlew :core:test --tests '*TraceTest' --tests '*ApiShapeTest' -q`: pass
- `scripts/review-api-surface.sh`: `API SURFACE OK` (classes=155, sealed set unchanged)
- `grep -c 'TraceCode("'` on TraceCode.kt: 28
- `./gradlew check` (whole repo): BUILD SUCCESSFUL
- No api.txt, no build/config change, no new `@Suppress`, no new dependency, no planning id in source.

## Next Plan Readiness

03-06, 03-07 and 03-08 can record the thirteen codes and report `TurnRecord(..., fallbackFrom = ProviderId.ON_DEVICE)` when the declared fallback answers.

## Self-Check: PASSED

- Files: all four modified files exist; commits `330dcd6` and `f7900c6` are in `git log`; `git rev-list --count` from `plan_head_before` is 2.
