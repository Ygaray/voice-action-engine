---
phase: 03-transcript-types-providerrouter-on-device-gate
plan: 03
subsystem: core
status: complete
tags: [kotlin, seams, credential-source, provider-selection, on-device-gate, failure-taxonomy, prov-02, prov-10, cln-04]

requires:
  - phase: 02-core-contract-pipeline-commit-seam
    provides: "Credential, ProviderId, StrategyId, FailureReason taxonomy with isStableCode/mixHash/describe helpers, testFixtures conventions"
  - phase: 03-transcript-types-providerrouter-on-device-gate
    provides: "Plan 02: ModelCapabilityTable and the NoHardCodedConstantsTest source scan these new files must satisfy"
provides:
  - "io.github.ygaray.voiceactionengine.core.provider: CredentialSource, CredentialLookup (Present, Missing, Unreadable), ProviderSelectionSource, ProviderSelection, SelectionRequest, OnDeviceCapability, OnDeviceAvailability (Available, Downloadable, Downloading, Unavailable)"
  - "io.github.ygaray.voiceactionengine.core.failure.FailureReason.CredentialUnreadable (code credential_unreadable)"
  - "testFixtures: ScriptedCredentialSource, ScriptedSelectionSource (ScriptedSources.kt)"
affects: [03-06, 03-07, 03-08, Phase 6 Keystore adapter]

plan_head_before: de7fa2c79e10ce180205af6809875b13eaf3719e

commits: 3

actuals:
  tokens: 9300   # chars/4 over the 7 files touched (new + modified line content, approx 37,000 chars)
  tasks: 3
  commits: 3

tech-stack:
  added: []
  patterns:
    - "Seams are single-abstract-method fun interfaces returning typed, open results (abstract class with internal constructor and public final leaves)"
    - "Free-text causes are rejected at construction by isStableCode, so no seam type or failure leaf can carry a message"
    - "Scripted fakes use only public :core API; the selection fake throws AssertionError on exhaustion so an unplanned step escapes the guarded collapse"

key-files:
  created:
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/provider/CredentialSource.kt
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/provider/ProviderSelection.kt
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/provider/OnDeviceCapability.kt
    - core/src/testFixtures/kotlin/io/github/ygaray/voiceactionengine/core/testing/ScriptedSources.kt
    - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/SeamTypesTest.kt
  modified:
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/failure/FailureReason.kt
    - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/FailureTaxonomyTest.kt

key-decisions:
  - "Fallback validation is exactly two checks: a fallback requires provider == ON_DEVICE, and the fallback's provider must not be ON_DEVICE. A chain is structurally impossible (a chained fallback would itself have to be on-device, which the second check rejects), so no third check exists"
  - "ProviderSelection has no default argument values: primary (provider, model, fallback) plus a secondary (provider, model) constructor"
  - "SelectionRequest has an internal constructor; the router (03-06/03-07) builds it, tests in :core build it directly"
  - "ScriptedSelectionSource records a request before checking exhaustion, so calls counts the failing call too"

requirements-completed: [PROV-02, PROV-10, CLN-04]

coverage:
  - id: D1
    description: "An app-implemented CredentialSource answers a typed lookup for exactly the provider asked; the scripted fake records every ask in order; Present never prints the key"
    requirement: "PROV-02, CLN-04"
    verification:
      - kind: unit
        ref: "core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/SeamTypesTest.kt#theScriptedCredentialSourceAnswersPerProviderAndRecordsEveryAskInOrder"
        status: pass
      - kind: unit
        ref: "core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/SeamTypesTest.kt#presentPrintsTheProviderOnlyAndNeverTheKey"
        status: pass
    human_judgment: false
  - id: D2
    description: "CredentialUnreadable is its own stable-coded failure leaf, distinct from NotConfigured; Unreadable causes must be lower snake case codes; the taxonomy stays code-unique"
    requirement: "PROV-02"
    verification:
      - kind: unit
        ref: "core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/FailureTaxonomyTest.kt#aUnreadableCredentialIsADistinctFailureFromNotConfigured"
        status: pass
      - kind: unit
        ref: "core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/FailureTaxonomyTest.kt#everyFailureLeafHasAUniqueCode"
        status: pass
    human_judgment: false
  - id: D3
    description: "ProviderSelection requires a model, allows a single fallback only on an on-device selection and never on-device or chained; OnDeviceAvailability mirrors the ML Kit statuses; scripted selection fake answers in order and fails loudly when exhausted"
    requirement: "PROV-02, PROV-10, CLN-04"
    verification:
      - kind: unit
        ref: "core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/SeamTypesTest.kt"
        status: pass
      - kind: command
        ref: "scripts/review-api-surface.sh (API SURFACE OK)"
        status: pass
    human_judgment: false
---

# Phase 3 Plan 03: App Seams for Credential, Selection and On-Device Status Summary

**Three app-implemented fun-interface seams (CredentialSource with a typed Present/Missing/Unreadable result, ProviderSelectionSource with a required model and a one-level on-device fallback, OnDeviceCapability with ML-Kit-shaped statuses), a distinct CredentialUnreadable failure leaf, and recording/scripted fakes for the router tests.**

## Performance

- **Duration:** about 15 min (start time was not captured at the top of the run; approximate)
- **Completed:** 2026-10-01
- **Tasks:** 3 (1 tracer, 2 TDD)
- **Files:** 5 created, 2 modified

## Accomplishments

- `CredentialSource` / `CredentialLookup`: a lost key (`Unreadable`) can never be mistaken for `Missing`; `Present` prints the provider only (canary key asserted absent).
- `FailureReason.CredentialUnreadable(provider, cause)`: code `credential_unreadable`, cause validated by `isStableCode`, same equality/hash/toString shape as `ProviderUnavailable`. FailureTaxonomyTest now lists 26 leaves, codes unique.
- `ProviderSelectionSource` / `ProviderSelection` / `SelectionRequest`: blank model refused (the engine never invents a default model); the request carries the asking tier's `StrategyId`.
- `OnDeviceCapability` / `OnDeviceAvailability`: leaves `Available`, `Downloadable`, `Downloading`, `Unavailable(code)`; open set; `Unavailable` code must be a stable code.
- `ScriptedCredentialSource` (map-backed, `keys(...)` factory, `requested` snapshot) and `ScriptedSelectionSource` (script order or `fixed`, `requests`, `calls`, `AssertionError` on exhaustion).

## Final Seam Signatures (03-06, 03-07, 03-08 depend on these)

```kotlin
public fun interface CredentialSource { public suspend fun credential(provider: ProviderId): CredentialLookup }
public abstract class CredentialLookup internal constructor() {
    public class Present(public val credential: Credential)
    public class Missing()
    public class Unreadable(public val cause: String)          // stable code, else IllegalArgumentException
}
public fun interface ProviderSelectionSource { public suspend fun select(request: SelectionRequest): ProviderSelection? }
public class SelectionRequest internal constructor(public val strategy: StrategyId)
public class ProviderSelection(provider: ProviderId, model: String, fallback: ProviderSelection?)  // + (provider, model)
public fun interface OnDeviceCapability { public suspend fun availability(): OnDeviceAvailability }
public abstract class OnDeviceAvailability internal constructor() {
    Available(), Downloadable(), Downloading(), Unavailable(code: String)   // code is a stable code
}
FailureReason.CredentialUnreadable(provider: ProviderId, cause: String)     // code "credential_unreadable"
```

## Fallback Validation Rules

1. `model` must be non-blank (else `IllegalArgumentException`).
2. A non-null `fallback` requires the selection's own `provider == ProviderId.ON_DEVICE`.
3. The fallback's `provider` must not be `ON_DEVICE`.
4. Because of 2 and 3, a fallback can never carry its own fallback (a chained fallback would itself have to be on-device). One level, no chains, structurally.

Consumers (03-08) must still apply the command policy (offlineOnly, allowedProviders) and the tier's declared providers to the fallback; the type only guarantees its shape.

## Task Commits

1. **Task 1 (tracer): credential source seam, typed lookup, recording fake** - `026a66c` (feat)
2. **Task 2: CredentialUnreadable failure leaf with stable-code cause** - `4ae7034` (feat)
3. **Task 3: selection seam, on-device availability, scripted selection fake** - `6284e46` (feat)

## Deviations from Plan

**1. [Rule 3 - Blocking] Fixture file named ScriptedCredentialSource.kt for Task 1, renamed to ScriptedSources.kt in Task 3**
- Found during: Task 1. detekt `MatchingDeclarationName` fails a file whose name does not match its single top-level declaration, and Task 1 holds only `ScriptedCredentialSource`. Suppressing is forbidden by the plan, so the Task 1 commit used `ScriptedCredentialSource.kt`; Task 3 `git mv`'d it to the plan's `ScriptedSources.kt` when the second top-level class arrived. Final layout matches the plan.

**2. [Rule 3 - Blocking] Detekt MaxLineLength wraps** in SeamTypesTest, FailureTaxonomyTest and one fixture KDoc line; no behavior change, no suppression.

**3. [Process] FailureTaxonomyTest count constant** `TWENTY_FIVE` became `TWENTY_SIX` (and the required-codes set gained `credential_unreadable`) because the every-leaf list grew by one; no existing assertion removed.

**4. [Process] Task 3 RED not committed separately.** Tests and implementation for Task 3 were written test-first and verified RED by compile failure locally (Task 2 explicitly), but committed together per task.

**Total deviations:** 4 (2 auto-fixed blocking, 2 process notes). **Impact:** none on scope or public API.

## Issues Encountered

None. HEAD stayed on `main` (repo uses `branching_strategy: none`); 03-02's note about the protected-branch heuristic applies unchanged.

## Verification

- `./gradlew :core:test --tests '*SeamTypesTest'` (15 tests) and `--tests '*FailureTaxonomyTest'` (14 tests): pass
- `./gradlew :core:detekt :core:scanBannedConstructs :core:compileTestFixturesKotlin`: pass
- `./gradlew :core:test --tests '*ApiShapeTest'` (6 tests): pass
- `./gradlew :core:check`: pass (includes NoHardCodedConstantsTest over the new sources)
- `./gradlew check` (whole repo): pass
- `scripts/review-api-surface.sh`: `API SURFACE OK` (sealed set unchanged, 155 classes)
- Acceptance greps (`fun interface CredentialSource`, `abstract class CredentialLookup internal constructor`, `class ScriptedCredentialSource`, `class CredentialUnreadable`, `fun interface ProviderSelectionSource`, `fun interface OnDeviceCapability`, `class SelectionRequest internal constructor`): all match one line each.
- No `api.txt` created; no change to build files, `gradle/`, or detekt config; no new `@Suppress`.

## Next Plan Readiness

03-06/03-07 can build the router against `ProviderSelectionSource` and `CredentialSource` and test it with `ScriptedSelectionSource` / `ScriptedCredentialSource`; map `CredentialLookup.Missing` to `NotConfigured(provider)`, `Unreadable(cause)` to `CredentialUnreadable(provider, cause)`, and refuse a `Present` credential whose provider differs from the one asked. 03-08 wires `OnDeviceCapability` into the existing internal `PipelineBuilder.onDeviceAvailability` hook (only `Available` is usable) with a default of `Unavailable("not_implemented")`.

## Self-Check: PASSED

- Files: all five created files and both modified files exist on disk.
- Commits: `026a66c`, `4ae7034`, `6284e46` present in `git log`; `git rev-list --count` from `plan_head_before` is 3.
