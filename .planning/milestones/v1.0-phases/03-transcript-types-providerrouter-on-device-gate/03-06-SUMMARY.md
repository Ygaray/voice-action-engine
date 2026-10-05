---
phase: 03-transcript-types-providerrouter-on-device-gate
plan: 06
subsystem: core
status: complete
tags: [kotlin, model-router, bound-model, credential-isolation, on-device-gate, prov-02, prov-10]

requires:
  - phase: 03-transcript-types-providerrouter-on-device-gate
    provides: "ModelRequest/ModelResponse (03-01), ModelCapabilityTable (03-02), CredentialSource/ProviderSelectionSource/CredentialUnreadable (03-03), TraceCode refusal codes (03-04), AiProvider/ProviderCall/ModelResult/FakeAiProvider (03-05)"
provides:
  - "BoundModel: public credential-hiding handle (provider, model, capabilities, fallbackFrom, refusal, complete)"
  - "ModelRouter (internal): bind(strategy, declared, policy, recorder) resolving selection, gate, on-device probe, registration, credential, capabilities"
  - "providerGate(provider, declared, policy): FailureReason? - the single tier/policy gate the fallback reuses"
affects: [03-07, 03-08, 03-09]

plan_head_before: e37c9239fd8b72b1f30b39669dd2ebb5ea3e92f9

commits: 3

actuals:
  tokens: 10400   # chars/4 over the three new files (about 41,500 chars)
  tasks: 3
  commits: 3

tech-stack:
  added: []
  patterns:
    - "Resolution as a private Step<Go|Stop> chain with an inline then(): each step either carries a value or stops with (trace code, typed reason); bind records the single stop code once"
    - "Refusal is a handle, not an exception: RefusedModel answers Failure(refusal) with no call, no turn, no further code"
    - "Binding held privately by the routed handle; the public BoundModel has no credential member"

key-files:
  created:
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/provider/BoundModel.kt
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/provider/ModelRouter.kt
    - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/ModelRouterTest.kt
  modified: []

key-decisions:
  - "providerGate returns FailureReason? (null = allowed) rather than a Step, so 03-08 can map its refusal to FALLBACK_REFUSED while the primary path maps it to PROVIDER_NOT_ALLOWED"
  - "The on-device probe runs before the registration check; a ready probe with no registered ON_DEVICE provider still refuses with on_device_unavailable (never provider_not_registered)"
  - "A credential-source fault always yields CredentialUnreadable(p, source_error), even for a leaked timeout; only selection and provider faults map a leaked timeout to Timeout"
  - "Pure helpers (notSelected, present, missing, gate) are file-level private functions to keep ModelRouter under detekt TooManyFunctions (12) with room for 03-08"

requirements-completed: [PROV-02, PROV-10]

coverage:
  - id: D1
    description: "bind resolves the selected provider and model from app seams and complete reaches the registered provider once with the right model, request and that provider's key; the turn lands in the trace on the router clock; the handle prints ids only"
    requirement: "PROV-02"
    verification:
      - kind: unit
        ref: "core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/ModelRouterTest.kt#bindResolvesTheModelAndACompleteCallReachesTheProviderAndTheTrace"
        status: pass
      - kind: unit
        ref: "core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/ModelRouterTest.kt#theHandlePrintsIdsOnlyAndNeverAKeyOrContent"
        status: pass
    human_judgment: false
  - id: D2
    description: "Every bind refusal (no or null selection, selection fault or leaked timeout, undeclared, policy-forbidden, offline-only, unregistered, missing key, unreadable key, credential source fault, key for another provider) gives a typed reason, one trace code and zero provider calls; a foreign key is never sent"
    requirement: "PROV-02"
    verification:
      - kind: unit
        ref: "core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/ModelRouterTest.kt (14 refusal tests: noSelectionSource..., aCredentialStampedForAnotherProvider...)"
        status: pass
    human_judgment: false
  - id: D3
    description: "An on-device selection that is not ready, has no registered provider or whose probe throws fails with ProviderUnavailable(ON_DEVICE, on_device_unavailable), zero calls and no credential lookup; a ready one binds with no key asked"
    requirement: "PROV-10"
    verification:
      - kind: unit
        ref: "core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/ModelRouterTest.kt#anOnDeviceSelectionThatIsNotReadyFailsLoudlyWithoutTouchingAKey"
        status: pass
      - kind: unit
        ref: "core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/ModelRouterTest.kt#aReadyOnDeviceSelectionBindsWithNoKeyAsked"
        status: pass
    human_judgment: false
  - id: D4
    description: "The routed handle refuses tools on a no-tools model before any call, types provider faults and leaked timeouts, passes provider failures through, honors caller cancellation, serves keyless providers and records exactly one turn per concurrent complete"
    requirement: "PROV-02"
    verification:
      - kind: unit
        ref: "core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/ModelRouterTest.kt (11 call-path tests)"
        status: pass
    human_judgment: false
---

# Phase 3 Plan 06: ModelRouter and BoundModel Summary

**An internal ModelRouter resolves selection, tier/policy gate, on-device probe, registration, per-provider credential and capabilities into a credential-hiding BoundModel, refusing every failure with a typed reason, one trace code and zero provider calls.**

## Accomplishments

- `BoundModel` (public, internal constructor) exposes ids, capabilities, `fallbackFrom`, `refusal` and `complete`; it has no credential member and a final ids-only `toString`.
- `ModelRouter.bind` follows the fixed resolution order. A key is asked for the selected provider only, and a credential stamped for another provider is refused and never placed in any `ProviderCall`.
- On-device without a ready probe (or without a registered on-device provider, or with a throwing probe) gives `ProviderUnavailable(ON_DEVICE, on_device_unavailable)` before any credential lookup.
- The routed handle checks `supportsTools` before any call, wraps the provider call in `guarded`, and records a `TurnRecord` (zero usage on faults and failures) through `RunRecorder.turnRecorded` on the router's clock.
- 27 tests in `ModelRouterTest`, built directly on a real `RunRecorder`, `FakeClock`, `FakeAiProvider` and the scripted sources.

## Interfaces for 03-07, 03-08, 03-09

```kotlin
// provider/ModelRouter.kt
internal class ModelRouter(
    providers: Map<ProviderId, AiProvider>,
    selection: ProviderSelectionSource?,
    credentials: CredentialSource?,
    table: ModelCapabilityTable,
    clock: () -> Long,
    onDeviceProbe: suspend () -> Boolean,
) {
    suspend fun bind(strategy: StrategyId, declared: Set<ProviderId>, policy: TierPolicy, recorder: RunRecorder): BoundModel
}
internal fun providerGate(provider: ProviderId, declared: Set<ProviderId>, policy: TierPolicy): FailureReason?  // null = allowed

// provider/BoundModel.kt
public abstract class BoundModel internal constructor() { provider, model, capabilities, fallbackFrom, refusal, suspend complete(request) }
internal class Binding(provider: AiProvider, model: String, credential: Credential?, capabilities: ModelCapabilities, fallbackFrom: ProviderId?)
internal class RoutedModel(binding, strategy, recorder, clock)   // 4 parameters
internal class RefusedModel(refusal)
```

- Constructor sizes stay under detekt's 8-parameter limit: ModelRouter has 6, the resolved facts travel in the 5-field `Binding`, `RoutedModel` has 4.
- 03-08 inserts the declared-fallback branch in `ModelRouter.onDevice` (the step that currently refuses) and builds its `Binding` with `fallbackFrom = ON_DEVICE`; it reuses `providerGate` and maps its reason to `FALLBACK_REFUSED`.
- 03-09 hooks the cache detector in `RoutedModel.complete`, right after `recorder.turnRecorded(...)` for a `Success`.
- Stable cause strings are private consts in `ModelRouter.kt`: `provider_not_declared`, `policy_forbids_provider`, `on_device_unavailable`, `source_error`.

## Task Commits

1. **Task 1 (tracer): ModelRouter.bind, routed BoundModel, guarded call and recorded turn** - `f09bffc` (feat)
2. **Task 2: every bind refusal pinned** - `67da34d` (test)
3. **Task 3: routed call path pinned** - `deefa72` (test)

## Deviations from Plan

**1. [Process] Tasks 2 and 3 are test-only commits.** Task 1 wrote every router and handle function in its final shape (as the plan instructed), so the Task 2 and 3 tests passed on first run against the existing main code. No RED commit exists; no main file changed after Task 1.

**2. [Rule 3 - Blocking] detekt findings fixed without suppression.** `ReturnCount` on the credential step (split into `credential` and `lookUp`), `TooManyFunctions` at 12 (moved four pure helpers to file level), a `MaxLineLength` doc line and one `UseCheckOrError` in the test (`error(...)` instead of `throw IllegalStateException`).

**Total deviations:** 2 (1 process note, 1 auto-fixed lint). **Impact:** none on scope or public shape.

## Issues Encountered

None. HEAD stayed on `main` (repo uses `branching_strategy: none`).

## Verification

- `./gradlew :core:test --tests '*ModelRouterTest'`: 27 tests, 0 failures
- `./gradlew :core:check` and `./gradlew check` (whole repo): BUILD SUCCESSFUL
- `scripts/review-api-surface.sh`: `API SURFACE OK` (classes=161, sealed set unchanged)
- Exactly one `@Suppress` in `core/src/main`; no Phase 2 file edited; no `api.txt`, no new dependency, no model-id literal in main.
- Acceptance greps: `abstract class BoundModel internal constructor` and `internal class ModelRouter` match one line each; `guarded` appears 5 times in ModelRouter.kt and 2 in BoundModel.kt; the four stable cause strings appear 4 times in ModelRouter.kt.

## Next Plan Readiness

03-07 can build `ModelRouter` in `PipelineBuilder` (pipeline clock, shared on-device probe) and bind lazily per tier from `RunSession`/`CommandSession.model()`.

## Self-Check: PASSED

- Files: BoundModel.kt, ModelRouter.kt and ModelRouterTest.kt exist.
- Commits `f09bffc`, `67da34d`, `deefa72` are in `git log`; `git rev-list --count` from `plan_head_before` is 3 before this SUMMARY commit.
