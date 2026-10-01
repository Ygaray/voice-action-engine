---
phase: 03-transcript-types-providerrouter-on-device-gate
plan: 08
subsystem: core
status: complete
tags: [kotlin, on-device-gate, declared-fallback, key-isolation, model-router, prov-02, prov-10]

requires:
  - phase: 03-transcript-types-providerrouter-on-device-gate
    provides: "ModelRouter and providerGate (03-06), PipelineBuilder wiring with one shared on-device hook (03-07), scripted fakes (03-03, 03-05)"
provides:
  - "PipelineBuilder.onDevice: public OnDeviceCapability gate, default Unavailable(not_implemented)"
  - "ModelRouter declared-fallback branch: gated, credentialed and bound like a primary selection, fallbackFrom = ON_DEVICE"
  - "OnDeviceGateTest (13 tests) and KeyIsolationTest (5 tests)"
affects: [03-09, 03-10]

plan_head_before: 85f1c7fbd0183be121996bea9ffdaa44b7153792

commits: 3

actuals:
  tokens: 7900   # chars/4 over the realized core diff (about 31,565 added chars)
  tasks: 3
  commits: 3

tech-stack:
  added: []
  patterns:
    - "One gate: the public onDevice property is read at call time by the single internal hook the pre-check and the router share"
    - "Fallback as a branch of the same Step chain: providerGate(fallback) then bound(fallback, ON_DEVICE), so the fallback is never a second path"

key-files:
  created:
    - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/OnDeviceGateTest.kt
    - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/KeyIsolationTest.kt
  modified:
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/provider/ModelRouter.kt
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/pipeline/PipelineBuilder.kt

key-decisions:
  - "A permitted fallback records provider_fallback only (not on_device_unavailable): the fallback is a served path, not a failure; a refusal records on_device_unavailable then fallback_refused"
  - "Registration, key lookup and capability lookup were extracted into bound(selection, fallbackFrom) so the primary and fallback selections share one code path"
  - "If a later step refuses the fallback (missing key, unregistered), that step's own reason for the fallback provider is the failure, recorded after provider_fallback"

requirements-completed: [PROV-10, PROV-02]

coverage:
  - id: D1
    description: "On a device with no on-device support (default gate), a selection that declares a fallback is served by that fallback provider and model with that provider's own key; the trace records provider_fallback and fallbackFrom = ON_DEVICE; the strategy's handle reports the fallback provider"
    requirement: "PROV-10"
    verification:
      - kind: unit
        ref: "core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/OnDeviceGateTest.kt#anUnavailableOnDeviceSelectionIsServedByTheDeclaredFallbackWithItsOwnKey"
        status: pass
      - kind: unit
        ref: "core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/OnDeviceGateTest.kt#theDefaultGateReportsNotImplementedBecauseVersionOneShipsNoOnDeviceCode"
        status: pass
    human_judgment: false
  - id: D2
    description: "No fallback, a policy-forbidden fallback, an undeclared fallback, a flipping gate, offline-only (router and pre-check) and a fallback without a key each end in one typed failure with the expected trace codes, zero provider calls and no cloud key asked (or only the fallback provider's key)"
    requirement: "PROV-10"
    verification:
      - kind: unit
        ref: "core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/OnDeviceGateTest.kt (9 refusal and gate tests)"
        status: pass
    human_judgment: false
  - id: D3
    description: "The pre-check and the router read one gate (two reads per command); a throwing read records on_device_probe_error and counts as unavailable; Downloadable and Downloading count as unavailable; an Available gate with a keyless on-device provider calls it with a null credential and no fallback"
    requirement: "PROV-10"
    verification:
      - kind: unit
        ref: "core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/OnDeviceGateTest.kt#aCountingGateIsReadOncePerCheckByThePreCheckAndTheRouter"
        status: pass
      - kind: unit
        ref: "core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/OnDeviceGateTest.kt#anAvailableGateWithAKeylessOnDeviceProviderCallsItWithNoCredentialAndNoFallback"
        status: pass
    human_judgment: false
  - id: D4
    description: "A key is asked for and sent only to its own provider across a two-tier ladder, a strict source, a hostile source, the fallback path and a provider switch between commands; no key appears in another provider's calls"
    requirement: "PROV-02"
    verification:
      - kind: unit
        ref: "core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/KeyIsolationTest.kt (5 tests)"
        status: pass
    human_judgment: false
---

# Phase 3 Plan 08: On-Device Gate and Declared Fallback Summary

**`PipelineBuilder.onDevice` is the one public on-device gate (default `Unavailable("not_implemented")`) feeding the shared internal hook; the router serves an unusable on-device selection only from its declared, policy-permitted fallback with that provider's own key, and fails loudly with zero calls otherwise.**

## Accomplishments

- `PipelineBuilder.onDevice: OnDeviceCapability` added; the internal `onDeviceAvailability` keeps its type and visibility and now asks `onDevice` at call time, accepting only `Available`. Phase 2 tests that assign the hook are unchanged and green.
- `ModelRouter.resolve` is now `select -> gate -> usable? bound : viaFallback`. `bound(selection, fallbackFrom)` is shared by the primary and fallback paths, so the fallback is gated by `providerGate`, looked up by its own provider id and bound with `fallbackFrom = ON_DEVICE`.
- Fallback contract implemented as written: no fallback gives `on_device_unavailable`; a forbidden or undeclared fallback gives `on_device_unavailable` then `fallback_refused` with the same on-device failure and no key lookup; a permitted fallback records `provider_fallback` and any later refusal is that step's own reason.
- 13 tests in `OnDeviceGateTest` (one per behavior bullet plus the default-gate assertion) and 5 in `KeyIsolationTest`, each asserting on the recorded calls of every fake.

## Evidence for SC3: no Nano/AICore code

`NoHardCodedConstantsTest` (12 tests, 0 failures, including the on-device implementation token rule and its positive control from 03-02) passed on the tree carrying this plan's `PipelineBuilder.kt` and `ModelRouter.kt` changes, via `./gradlew :core:test --tests '*NoHardCodedConstantsTest'` (and again inside the full `./gradlew check`, exit 0). The new KDoc deliberately avoids the vendor tokens.

## Phase 10 README rule (Pitfall 6)

A tier that wants on-device-then-cloud must declare both providers in its `StrategyCapabilities`, and the app's selection must declare the fallback. If the tier declares only on-device, or the policy forbids the cloud provider, the fallback is refused and the command fails with `ProviderUnavailable(ON_DEVICE, "on_device_unavailable")`. Offline-only commands never reach the fallback: a tier that also declares a cloud provider is skipped by the pre-check (`offline_unavailable`), and a fallback to a cloud provider is refused at the router.

## Task Commits

1. **Task 1 (tracer): public onDevice gate behind the shared hook and the declared-fallback branch** - `c37b957` (feat)
2. **Task 2: forbidden and missing fallbacks stay loud, offline, probe faults, disagreement, counting** - `5958028` (test)
3. **Task 3: KeyIsolationTest** - `783c4bd` (test)

## Deviations from Plan

**1. [Process] Tasks 2 and 3 are test-only commits.** Task 1 had to implement the full fallback contract (the gate reuse for the fallback provider is part of case 3 and the refusal branches share the same function), so the Task 2 behaviors passed on first run and no RED commit exists. No main file changed after Task 1.

**2. [Rule 3 - Blocking] Detekt findings in the new tests fixed without suppression:** `MaxLineLength` (twice), two unused private properties in the Task 1 version of the test (re-added when used in Task 2), and `NestedBlockDepth` in the key-isolation helper (flattened with a precomputed foreign-key set).

**3. [Process] KDoc wording.** The first KDoc named the `not_implemented` code literally, which made the acceptance grep match two lines; reworded to "not implemented" so the constant is the single occurrence.

**Total deviations:** 3 (2 process notes, 1 auto-fixed lint). **Impact:** none on scope or public shape beyond the one planned public property.

## Issues Encountered

None. KeyIsolationTest found no blocker in `ModelRouter.kt` or `BoundModel.kt`; no file outside the plan's list was edited, and no Phase 2 test was touched.

## Verification

- `./gradlew :core:test --tests '*OnDeviceGateTest' --tests '*TierPolicyTest' --tests '*ProviderRouterTest' --tests '*ModelRouterTest'`: pass
- `./gradlew :core:test --tests '*KeyIsolationTest'`: 5 tests, 0 failures
- `./gradlew :core:detekt :core:scanBannedConstructs`: pass
- `./gradlew check` (whole repo): exit 0
- `scripts/review-api-surface.sh`: `API SURFACE OK` (classes=161, sealed set unchanged)
- Acceptance greps: `var onDevice: OnDeviceCapability` (1 line), `internal var onDeviceAvailability` (1), `not_implemented` (1 line in PipelineBuilder.kt), `fallback` in ModelRouter.kt 8 occurrences (>= 3). One `@Suppress` in `core/src/main` (the pre-existing one in Guarded.kt).

## Next Plan Readiness

03-09 owns `BoundModel.kt`, `CacheDetector.kt` and `RunRecorder.kt` and can hook the cache diagnostic in `RoutedModel.complete`; nothing here touches those files. 03-10 can document the Pitfall 6 rule above.

## Self-Check: PASSED

- Files: OnDeviceGateTest.kt, KeyIsolationTest.kt, ModelRouter.kt and PipelineBuilder.kt exist.
- Commits `c37b957`, `5958028`, `783c4bd` are in `git log`; `git rev-list --count` from `plan_head_before` is 3 before this SUMMARY commit.
