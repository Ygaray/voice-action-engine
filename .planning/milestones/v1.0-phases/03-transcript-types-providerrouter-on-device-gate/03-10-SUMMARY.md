---
phase: 03-transcript-types-providerrouter-on-device-gate
plan: 10
subsystem: core
status: complete
tags: [kotlin, phase-gate, redaction-canary, api-surface, constructor-audit, prov-01, prov-02, prov-03, prov-10, tel-03, cln-03, cln-04]

requires:
  - phase: 03-transcript-types-providerrouter-on-device-gate
    provides: "all nine earlier plans merged on main: transcript types, capability table, seams, trace growth, provider contract and FakeAiProvider, ModelRouter, pipeline wiring, on-device gate and fallback, cache detector"
provides:
  - "RedactionCanaryTest routed-path test: one canary-laden command through selection, credential, frozen handle, FakeAiProvider, tool call with replay, tool result, on-device-then-cloud fallback and two credential refusals leaves no canary and no key in any outcome, trace, attempt, turn, event, recorded ProviderCall, SelectionRequest, BoundModel or Phase 3 toString"
  - "evidence/phase-gate.txt: full-build, forced uncached :core run (per-class counts), Phase 1 scripts, suppression/planning-id/api.txt checks and the constructor audit"
  - "evidence/api-surface-review.txt: isolated-copy Metalava dump of :core with exactly the seven sealed types"
  - "Decision / edge-probe / prohibition trace and hand-off notes for Phases 4-6, 10 and 11 (this file)"
affects: [04, 05, 06, 10, 11]

plan_head_before: 5fb61697b50f18bb4981715e4579c749448b33ef

commits: 3

actuals:
  tokens: 3400   # chars/4 over the realized :core diff (13,483 chars); evidence files are captured tool output and not counted
  tasks: 3
  commits: 3

tech-stack:
  added: []
  patterns:
    - "Canary sweep driven through the real pipeline with the public scripted fixtures only (FakeAiProvider, ScriptedCredentialSource, ScriptedSelectionSource, RecordingEventListener)"
    - "Constructor audit by parsing the saved Metalava dump for both 'ctor public' and 'ctor @KotlinOnly public' lines"

key-files:
  created:
    - .planning/phases/03-transcript-types-providerrouter-on-device-gate/evidence/phase-gate.txt
    - .planning/phases/03-transcript-types-providerrouter-on-device-gate/evidence/api-surface-review.txt
  modified:
    - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/RedactionCanaryTest.kt

key-decisions:
  - "No main source was edited: every gate was green on the first run on the merged tree, and the constructor audit found no extra public-constructor owner"
  - "The routed canary test lives in the existing RedactionCanaryTest class and reuses its see() sweep; the two pre-existing tests are untouched"
  - "The evidence dump is named api-surface-review.txt (not *api.txt) so the hygiene gate that forbids a pre-cut api.txt stays satisfied"

requirements-completed: [PROV-01, PROV-02, PROV-03, PROV-10, TEL-03, CLN-03, CLN-04]

coverage:
  - id: D1
    description: "The routed path (selection, credential, frozen handle, provider call, replay, tool result, fallback, refusals) leaks no canary and no API key into any outcome, trace, event, recorded call or Phase 3 toString"
    requirement: "PROV-02"
    verification:
      - kind: unit
        ref: "core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/RedactionCanaryTest.kt#noCanaryOrKeyAppearsAnywhereOnTheRoutedPathIncludingFallbackAndRefusals"
        status: pass
    human_judgment: false
  - id: D2
    description: "The merged tree passes ./gradlew check and a forced uncached :core run with zero failures for every Phase 3 test class, every extended class and the TierPolicyTest and EventsTest regressions (406 tests in 44 classes)"
    requirement: "PROV-01"
    verification:
      - kind: other
        ref: ".planning/phases/03-transcript-types-providerrouter-on-device-gate/evidence/phase-gate.txt steps [1] and [1b]/[2]"
        status: pass
    human_judgment: false
  - id: D3
    description: "The Phase 1 gates did not regress: negative controls, repo hygiene and api-dump proofs pass on the merged tree"
    requirement: "CLN-03"
    verification:
      - kind: other
        ref: "scripts/verify-negative-controls.sh && scripts/verify-repo-hygiene.sh && scripts/verify-api-dump.sh (phase-gate.txt steps [3]-[5])"
        status: pass
    human_judgment: false
  - id: D4
    description: "The :core public surface has exactly the seven allowed sealed types, no copy/componentN, no enum and no stray public static field; saved as evidence with no module api.txt anywhere"
    requirement: "PROV-01"
    verification:
      - kind: other
        ref: "scripts/review-api-surface.sh --expect-sealed-complete --out .../evidence/api-surface-review.txt (API SURFACE OK, 7 'sealed exhaustive')"
        status: pass
    human_judgment: false
  - id: D5
    description: "Public constructors exist only on app- or provider-constructed types; engine-produced types (SelectionRequest, BoundModel, ModelCapabilities and its Builder, ModelCapabilityTable, CachingMode, every open base class) have none"
    requirement: "PROV-02"
    verification:
      - kind: other
        ref: "phase-gate.txt step [8] constructor audit verdict PASSED (79 distinct owners, 0 extra)"
        status: pass
    human_judgment: false
  - id: D6
    description: "Whether the written decision and edge-probe trace below is a faithful reading of the plans is a reviewer judgment; every row cites a named test or source file"
    verification: []
    human_judgment: true
    rationale: "The trace is prose mapping; the verifier should spot-check a few rows against the cited tests"

duration: 20 min
completed: 2026-09-30
---

# Phase 3 Plan 10: Phase gate, routed-path redaction canary and constructor audit Summary

**Phase 3 closes green on the merged tree: a routed-path canary proves no content or key escapes through the new carriers, `./gradlew check` and a forced 406-test `:core` run pass, the Phase 1 gates hold, the :core API has exactly the seven allowed sealed types, and only app- or provider-constructed types have public constructors.**

## Performance

- **Duration:** about 20 min
- **Tasks:** 3 (Task 1 tracer, Task 2 gate, Task 3 audit and trace)
- **Files:** 1 test extended, 2 evidence files, this SUMMARY

## Accomplishments

- **Routed-path canary (Task 1).** `RedactionCanaryTest` gained `noCanaryOrKeyAppearsAnywhereOnTheRoutedPathIncludingFallbackAndRefusals`. One tier declares `ON_DEVICE` and `ANTHROPIC`; the app selection is on-device with an Anthropic fallback; the default gate answers `Unavailable("not_implemented")`, so the declared fallback answers. The strategy makes two routed turns through `session.model()`: turn 1 carries a canary system prompt, a canary `UserMessage`, and a `ToolSpec` with a canary description and schema; `FakeAiProvider` answers a canary `Text`, a `ToolCall` with a canary argument, and a `NativeReplay` whose raw JSON holds canary thinking text and signature. Turn 2 sends the conversation extended with that assistant message and a `ToolResultsMessage` whose `ToolResult` content is a canary. Two more runs exercise `CredentialLookup.Unreadable("key_invalidated")` and a `Present` credential stamped for `OPENAI` (holding a second canary key) when `ANTHROPIC` is asked for. The sweep covers outcomes, traces, every attempt and turn, every event, all recorded `ProviderCall`s (request, tools, messages, credential, capabilities), recorded `SelectionRequest`s, the `BoundModel` each strategy obtained, and the `toString` of every Phase 3 type the test builds. It also asserts the run really took the routed shape (two provider calls, both turns stamped `fallbackFrom = ON_DEVICE`, the replay stamp readable on turn 2, the key sent on the call but never printed, zero calls on both refusals, `credential_mismatch` in the trace). The two pre-existing tests are unchanged.
- **Phase gate (Task 2).** All seven steps exited 0, captured in `evidence/phase-gate.txt`. `./gradlew check` was green; the forced run (`:core:cleanTest :core:test --no-build-cache`) produced 44 JUnit result files, 406 tests, 0 failures, 0 errors. The per-class counts for the 18 classes named in the plan's second truth are recorded in the evidence file (for example ProviderRouterTest 18, ModelRouterTest 27, CacheNotEngagedTest 31, OnDeviceGateTest 13, KeyIsolationTest 5, RedactionCanaryTest 3).
- **Surface review.** `scripts/review-api-surface.sh --expect-sealed-complete` printed `API SURFACE OK sealed=AssistantPart,CommandOutcome,GateDecision,Message,RunTermination,StrategyOutcome,ToolStep classes=161`. The saved dump starts with `// Signature format: 4.0` and contains seven `sealed exhaustive` declarations. There is no `api.txt` in `core/`, `providers/` or `keystore/` (the v1.0.0 dump is Phase 11's).
- **Single suppression, no planning ids.** Exactly one `@Suppress` in `core/src/main` (`internal/Guarded.kt:43`, `TooGenericExceptionCaught`); no `D-xx`, `PROV-xx`, `TEL-xx`, `CLN-xx`, `CORE-xx`, `GATE-xx`, `T-xx-xx` or `WR-xx` in `core/src/main` or `core/src/testFixtures`.
- **Constructor audit (Task 3).** The saved dump has 103 `ctor public` / `ctor @KotlinOnly public` lines over 79 distinct owners. All 79 are in the allowed list (the Phase 2 owners plus the Phase 3 owners from the plan). `SelectionRequest`, `BoundModel`, `ModelCapabilities`, `ModelCapabilities.Builder`, `ModelCapabilityTable`, `CachingMode` and every open/abstract/sealed base class have none (the dump holds 14 abstract class declarations, so the check is not vacuous). Verdict **PASSED**, appended to `evidence/phase-gate.txt` as step [8]. No blocker to record.

## Decision trace

### (a) D-01..D-11, Runtime Decisions, orchestrator planning decisions 1-8

| Decision | Proven by |
|----------|-----------|
| D-01 replay | `NativeReplayTest` (exact provider+model stamp returns the same raw instance; anything else returns null; raw never printed; parts stay Text and ToolCall); `TranscriptTypesTest`; routed replay stamp asserted in `RedactionCanaryTest` |
| D-02 credential | `SeamTypesTest` (`CredentialLookup` leaves, stable-code `Unreadable`); `ModelRouterTest.anUnreadableKeyAndAThrowingCredentialSourceAreDistinctFromMissing`; `RedactionCanaryTest` unreadable run; source `provider/CredentialSource.kt` |
| D-03 snapshot | `ProviderRouterTest.threeTurnsInOneTierAskTheSelectionOnceAndAllGoToTheSameModel`, `aSelectionThatChangesMidCommandDoesNotMoveTheCommandButTheNextCommandUsesIt`, `eachTierAsksOnceNamingItself...` |
| D-04 no default model | `NoHardCodedConstantsTest.noModelIdAppearsInCodeKdocOrComments`; `ProviderSelection` requires a non-blank model (`SeamTypesTest`); README hand-off in (d) below |
| D-05 capabilities | `ModelCapabilityTableTest`; `ProviderRouterTest.anOverridePatchesOnlyItsExactPairAndKeepsTheOtherFields`; `ModelRouterTest.aToolRequestOnAModelWithoutToolsIsRefusedBeforeAnyCall...` |
| D-06 on-device gate | `OnDeviceGateTest` (default gate `not_implemented`, Downloadable and Downloading count as unavailable, fallback path, fallbackFrom in trace via `TraceTest`); source `provider/OnDeviceCapability.kt`, `provider/ModelRouter.kt` |
| D-07 loud on-device failure | `OnDeviceGateTest.noFallbackAndTheDefaultGateFailsLoudlyWithZeroCallsAndNoKeyAsked`, `anOnDeviceOnlyTierWithTheDefaultGateFailsBeforeTheStrategyRunsAndNeverAsksTheSelection`; `ProviderRouterTest.anOnDeviceSelectionWithoutAFallbackEndsLoudlyAndAsksForNoKey` |
| D-08 cache detect | `CacheNotEngagedTest.aLargeStaticPrefixThatTheCacheIgnoredRaisesExactlyOneEventAndNothingElse` and the silent-on-unknown cases |
| D-09 cache estimate | `CacheNotEngagedTest` character-path and prompt-total-cap boundary tests, `aSmallerDivisorFiresOnFewerCharacters`; `ModelCapabilityTableTest.invalidDivisorsAreRejectedNamingTheField` |
| D-10 cache mode | `CacheNotEngagedTest` explicit and automatic matrices (`automaticNeverFiresOnTheFirstTurn`, `automaticFiresOnTheSecondTurnWithNoCacheRead`, `automaticIgnoresWrites...`) |
| D-11 ToolSpec.terminal | `ToolSpecClarificationTest.terminalAndMutatingTogetherFailAtConstruction` and `...StillFailWhenStrictIsSet` (regression only) |
| Runtime: credential | `CredentialSource` is additive and `Credential` is unchanged; `KeyIsolationTest`; `RedactionCanaryTest.theCredentialKeyIsNotInItsOwnText` |
| Runtime: ondevice | one gate through the internal hook: `OnDeviceGateTest.aCountingGateIsReadOncePerCheckByThePreCheckAndTheRouter`, `ProviderRouterTest.thePreCheckAndTheRouterReadOneOnDeviceProbeInstanceTwicePerCommand`, `TierPolicyTest` regression |
| Runtime: cache-detect | only `RunRecorder.cacheNotEngaged` is called: `CacheNotEngagedTest.anExplicitProviderThatNeverCachesRaisesOneEventPerResponseAndNoEngineCode`; `EventsTest` regression |
| Runtime: FakeAiProvider | `FakeAiProviderTest`; `core/src/testFixtures/.../FakeAiProvider.kt` (public, loud on script exhaustion) |
| Orchestrator 1: Phase 2 semantics final | no Phase 2 test edited except extended (`RedactionCanaryTest`, `ApiShapeTest`, `FailureTaxonomyTest`, `ToolSpecClarificationTest`); `TierPolicyTest`, `EventsTest` green in the forced run |
| Orchestrator 2: seal Message and AssistantPart | seven sealed types in `evidence/api-surface-review.txt`; `scripts/review-api-surface.sh` allow-list |
| Orchestrator 3: one cause code `on_device_unavailable` | `OnDeviceGateTest`, `ModelRouterTest.anOnDeviceSelectionThatIsNotReadyFailsLoudlyWithoutTouchingAKey`, `ProviderRouterTest` on-device refusals |
| Orchestrator 4: D-10 as locked, prefix drift undetected | `CacheNotEngagedTest.explicitPrefixDriftWithAWriteIsAKnownSilentCase` |
| Orchestrator 5: divisor 4.0 overridable, capped by prompt tokens | `ModelCapabilityTableTest.unknownAndEmptyBuilderShareTheDocumentedDefaults`; `CacheNotEngagedTest.thePromptTotalCapFiresAtTheMinimumAndNotOneTokenBelow`, `theCapCountsAllThreePromptBuckets` |
| Orchestrator 6: `ToolSpec.strict` only, additive | `ApiShapeTest.toolSpecKeepsItsFiveArgumentConstructorAndAddsTheOtherShapes`; `ToolSpecClarificationTest.strictDefaultsToNullAndKeepsAnExplicitValue` |
| Orchestrator 7: D-11 regression only | `ToolSpecClarificationTest` (above) |
| Orchestrator 8: FakeAiProvider public, loud on exhaustion | `FakeAiProviderTest`; `FakeAiProvider.kt` throws `AssertionError` on a dry script |

### (b) Edge-probe items (5 explicit truths, 5 flagged assumptions resolved by explicit truths)

| # | Req | Disposition | Proven by |
|---|-----|-------------|-----------|
| 1 | PROV-01 | flagged assumption, explicit truths authored | `TranscriptTypesTest` (empty and duplicate lists, blank ids, defensive copies), `NativeReplayTest`, `RedactionCanaryTest` |
| 2 | PROV-02 concurrency | resolved, explicit | `ProviderRouterTest.twoConcurrentModelCallsInOneTierResolveOnceAndShareTheSameHandle`, `aCancelledModelCallFreezesNothing...`, `twoCommandsRunningAtOnceOnOnePipelineResolveIndependently`; `ModelRouterTest.twoConcurrentCompletesOnOneHandleRecordExactlyTwoTurns`, `manyCompletesFromRealThreadsLoseNoTurn` |
| 3 | PROV-03 | flagged assumption, explicit truth authored | `ProviderRouterTest.aSelectionThatChangesMidCommandDoesNotMoveTheCommandButTheNextCommandUsesIt` |
| 4 | PROV-10 | flagged assumption, explicit truth authored | `OnDeviceGateTest.aGateThatFlipsFromAvailableToUnavailableLetsTheRouterReadGovernAndRefusesAnUndeclaredFallback`, `aCountingGateIsReadOncePerCheckByThePreCheckAndTheRouter` |
| 5 | TEL-03 boundary | resolved, explicit | `CacheNotEngagedTest.explicitFiresAtTheMinimumAndNotOneTokenBelow`, `theCharacterPathFiresAtFourTimesTheMinimumAndNotOneCharacterBelow`, `thePromptTotalCapFiresAtTheMinimumAndNotOneTokenBelow` |
| 6 | TEL-03 empty | resolved, explicit | `CacheNotEngagedTest.anEmptyPrefixEstimatesZeroAndIsSilent`, `anUnknownMinimumIsSilent`, `aProviderFailureRaisesNothing...`, `aRefusedBindingRaisesNothing...` |
| 7 | TEL-03 encoding | resolved, explicit | `CacheNotEngagedTest.prefixCharsCountsUtf16CodeUnitsSoASupplementaryCharacterCountsTwo` |
| 8 | TEL-03 precision | resolved, explicit | `CacheNotEngagedTest.theEstimateTruncatesInsteadOfRounding`, `maximumUsageInEveryBucketDoesNotOverflow` |
| 9 | CLN-03 | flagged assumption, explicit truths authored | `NoHardCodedConstantsTest` (`noModelIdAppearsInCodeKdocOrComments`, `limitConstantsAreDeclaredOnlyByTheirOwners`, `tierPolicyDefaultValuesAppearOnlyInTierPolicy`, matcher self-tests, `scanIsNotVacuous`) |
| 10 | CLN-04 | flagged assumption, explicit truths authored | `NoHardCodedConstantsTest.noFileEnvironmentPropertyOrPreferenceAccess`, `noOnDeviceImplementationCode`; `SeamTypesTest` |

### (c) Prohibitions P1-P8

| # | Prohibition | Proven by |
|---|-------------|-----------|
| P1 | a provider's key never goes to another provider, including via fallback or a hostile source | `KeyIsolationTest` (all five tests, notably `aHostileSourceHandingAnotherProvidersKeyIsRefusedAndTheKeyReachesNoCall`, `theFallbackPathAsksForTheFallbackProvidersKeyAloneAndSendsOnlyThatKey`); `ModelRouterTest.aCredentialStampedForAnotherProviderIsRefusedAndNeverSent`; `RedactionCanaryTest` mismatch run |
| P2 | an offlineOnly command's transcript never reaches a network provider, including through a fallback | `OnDeviceGateTest.offlineOnlyWithAFlippingGateRefusesTheCloudFallbackAndAsksNoCloudKey`, `offlineOnlyWithATierThatAlsoDeclaresTheCloudIsSkippedByThePreCheck`; `ModelRouterTest.anOfflineOnlyPolicyRefusesACloudProviderEvenWhenTheTierDeclaresIt` |
| P3 | an unavailable on-device selection never silently climbs to a cloud provider | `OnDeviceGateTest.noFallbackAndTheDefaultGateFailsLoudly...`, `aFallbackThePolicyForbidsIsRefusedWithZeroCallsAndNoKeyAsked`; `ModelRouterTest.anOnDeviceSelectionThatIsNotReadyFailsLoudlyWithoutTouchingAKey`; `ProviderRouterTest.anOnDeviceSelectionWithoutAFallbackEndsLoudlyAndAsksForNoKey` |
| P4 | no model id or default model in library code | `NoHardCodedConstantsTest.noModelIdAppearsInCodeKdocOrComments` (re-run on the merged tree, step [1b]) |
| P5 | no reads of settings, files, environment or properties | `NoHardCodedConstantsTest.noFileEnvironmentPropertyOrPreferenceAccess` |
| P6 | no key, transcript, prompt, argument, result or replay text in any toString, trace, event or failure | `RedactionCanaryTest` (both the Phase 2 sweep and the new routed sweep); per-type toString tests in `TranscriptTypesTest`, `NativeReplayTest.printingNeverRevealsTheRawTurn`, `ModelRouterTest.theHandlePrintsIdsOnlyAndNeverAKeyOrContent` |
| P7 | a running command never switches provider or model mid-command | `ProviderRouterTest.aSelectionThatChangesMidCommandDoesNotMoveTheCommandButTheNextCommandUsesIt`, `threeTurnsInOneTierAskTheSelectionOnce...` |
| P8 | the cache diagnostic never fires when unsure and never adds a second event kind | `CacheNotEngagedTest` silent-on-unknown cases and `anExplicitProviderThatNeverCachesRaisesOneEventPerResponseAndNoEngineCode` |

## Hand-off notes

### (d) For the Phase 10 README

- The engine has **no default model id**. The app names the model in its `ProviderSelection`; `ProviderSelection` rejects a blank model.
- A tier that wants on-device-then-cloud declares **both providers** in its `StrategyCapabilities` and puts the cloud selection in `ProviderSelection.fallback` (only an `ON_DEVICE` selection may carry one; one level, no chains). The fallback is subject to the same offline-only and allowed-providers policy as any selection. Offline commands never reach the fallback.
- A strategy turns an **on-device refusal into a failed outcome**, never an escalation (`session.model().refusal` / `ModelResult.Failure`). The command never climbs to a tier the app did not declare as its fallback.
- The **cache diagnostic** (`PipelineEvent.CacheNotEngaged`) errs silent. It does not see prefix drift when the provider also reports a cache write (a known limitation, pinned by `explicitPrefixDriftWithAWriteIsAKnownSilentCase`). The estimate uses a 4.0 chars-per-token divisor that apps may override per model through `ModelCapabilities.charsPerToken`, and it is capped by the response's own prompt tokens.
- Routed turns are **recorded by the engine**. A strategy calls `recordTurn` only for calls it makes outside the handle, or the turn is counted twice.

### (e) For Phases 4-6

- **AiProvider contract (Phases 4-5):** return `ModelResult.Failure`, never throw; contribute `capabilities(model)` defaults including the unknown-id default; a keyless provider reports `requiresCredential = false`. The engine hands the provider a `ProviderCall` whose `capabilities` already carry the app's overrides, so the transport follows the cache and tool facts the engine decided on.
- `ToolSpec.strict == null` means the **engine/transport decides** per provider and model; `true`/`false` is an explicit app choice.
- Unknown provider stop reasons map to `StopReason.OTHER`; the raw provider value lives only inside `NativeReplay.raw`, never in a neutral field.
- Emit `NativeReplay` only with an **exact provider-and-model stamp**; the mapper reads it through `AssistantMessage.nativeFor(provider, model)` and rebuilds from the neutral parts when it returns null.
- `KeystoreCredentialSource` (Phase 6) implements `CredentialSource` and answers `Present` / `Missing` / `Unreadable(stableCode)`. `Unreadable` surfaces as `FailureReason.CredentialUnreadable` ("re-enter your key"), distinct from `NotConfigured`.
- The test fixtures (`FakeAiProvider`, `ScriptedCredentialSource`, `ScriptedSelectionSource`) are public and use only the public engine API; reuse them for transport-level contract tests.

### (f) For Phase 11

- Phase 2's `CommandInput` public constructor uses default arguments **without `@JvmOverloads`** (the dump shows one `optional` constructor). Any post-tag growth of it must therefore be a **new secondary constructor**, not a new defaulted parameter, or the frozen JVM signature breaks.
- `api.txt` per module is still to be generated at the cut; this plan only produced the isolated-copy `:core` review dump as evidence.

## Task Commits

1. **Task 1 (tracer): routed-path redaction canary** - `38adaec` (test)
2. **Task 2: phase gate on the merged tree with surface review evidence** - `61c00e1` (test)
3. **Task 3: constructor audit appended, decision trace and hand-off notes (this SUMMARY)** - committed together with the audit evidence in the plan-close docs commit

## Deviations from Plan

None - plan executed exactly as written. Two small notes, neither a deviation from the plan's intent: detekt flagged `ReturnCount` and `MaxLineLength` in the first draft of the new test, which were fixed in the test file before its commit (no suppression, no baseline); and one Gradle daemon crash during a combined detekt+test run on Task 1 was a transient daemon loss, cleared by rerunning the same command.

**Total deviations:** 0.
**Impact:** none; no main source was touched.

## Issues Encountered

None. The gate was green on the first run on the merged tree. `03-VALIDATION.md` was not edited and stays `status: draft`, `nyquist_compliant: false` (finalizer-owned).

## Authentication Gates

None.

## Next Phase Readiness

Phase 3 is complete on the merged tree and ready for orchestrator verification (the orchestrator runs `phase.complete`). Phases 4-5 can build the provider transports against the `AiProvider` contract, and Phase 6 `:keystore` can implement `CredentialSource`.

## Self-Check: PASSED

- Created files exist: `evidence/phase-gate.txt`, `evidence/api-surface-review.txt`, this SUMMARY.
- Commits `38adaec` and `61c00e1` exist on `main`; the Task 3 / SUMMARY commit follows this check.
- Task acceptance criteria re-checked: the routed test uses `FakeAiProvider`, `ScriptedCredentialSource` and a fallback `ProviderSelection`, passes, and the two earlier tests still pass (3 tests, 0 failures); `phase-gate.txt` has one section per command in order, each `exit=0`; `api-surface-review.txt` starts with `// Signature format: 4.0` and holds 7 `sealed exhaustive`; the constructor-audit section is present with verdict PASSED.
