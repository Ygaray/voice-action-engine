---
phase: 03-transcript-types-providerrouter-on-device-gate
verified: 2026-10-01T04:45:00Z
status: passed
score: 5/5 must-haves verified
behavior_unverified: 0
overrides_applied: 0
re_verification: false
covered_files:
  - .planning/REQUIREMENTS.md
  - .planning/phases/03-transcript-types-providerrouter-on-device-gate/03-01-PLAN.md
  - .planning/phases/03-transcript-types-providerrouter-on-device-gate/03-01-SUMMARY.md
  - .planning/phases/03-transcript-types-providerrouter-on-device-gate/03-02-PLAN.md
  - .planning/phases/03-transcript-types-providerrouter-on-device-gate/03-02-SUMMARY.md
  - .planning/phases/03-transcript-types-providerrouter-on-device-gate/03-03-PLAN.md
  - .planning/phases/03-transcript-types-providerrouter-on-device-gate/03-03-SUMMARY.md
  - .planning/phases/03-transcript-types-providerrouter-on-device-gate/03-04-PLAN.md
  - .planning/phases/03-transcript-types-providerrouter-on-device-gate/03-04-SUMMARY.md
  - .planning/phases/03-transcript-types-providerrouter-on-device-gate/03-05-PLAN.md
  - .planning/phases/03-transcript-types-providerrouter-on-device-gate/03-05-SUMMARY.md
  - .planning/phases/03-transcript-types-providerrouter-on-device-gate/03-06-PLAN.md
  - .planning/phases/03-transcript-types-providerrouter-on-device-gate/03-06-SUMMARY.md
  - .planning/phases/03-transcript-types-providerrouter-on-device-gate/03-07-PLAN.md
  - .planning/phases/03-transcript-types-providerrouter-on-device-gate/03-07-SUMMARY.md
  - .planning/phases/03-transcript-types-providerrouter-on-device-gate/03-08-PLAN.md
  - .planning/phases/03-transcript-types-providerrouter-on-device-gate/03-08-SUMMARY.md
  - .planning/phases/03-transcript-types-providerrouter-on-device-gate/03-09-PLAN.md
  - .planning/phases/03-transcript-types-providerrouter-on-device-gate/03-09-SUMMARY.md
  - .planning/phases/03-transcript-types-providerrouter-on-device-gate/03-10-PLAN.md
  - .planning/phases/03-transcript-types-providerrouter-on-device-gate/03-10-SUMMARY.md
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/failure/FailureDetails.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/failure/FailureReason.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/failure/ReasonSupport.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/internal/Guarded.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/internal/GuardedClock.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/pipeline/CommandPipeline.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/pipeline/HeldCommit.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/pipeline/PipelineBuilder.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/pipeline/RunSession.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/pipeline/TierWalk.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/provider/AiProvider.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/provider/BoundModel.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/provider/CacheDetector.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/provider/CachingMode.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/provider/CredentialSource.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/provider/ModelCapabilities.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/provider/ModelCapabilityTable.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/provider/ModelResult.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/provider/ModelRouter.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/provider/OnDeviceCapability.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/provider/ProviderRequest.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/provider/ProviderSelection.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/CommandSession.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/ToolSpec.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/telemetry/CommandTrace.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/telemetry/RunRecorder.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/telemetry/TraceCode.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/telemetry/TurnRecord.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/transcript/AssistantPart.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/transcript/Message.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/transcript/ModelRequest.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/transcript/ModelResponse.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/transcript/NativeReplay.kt
  - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/ApiShapeTest.kt
  - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/CacheNotEngagedTest.kt
  - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/FailureTaxonomyTest.kt
  - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/FakeAiProviderTest.kt
  - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/FreeTextSlotsTest.kt
  - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/GuardedClockTest.kt
  - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/GuardedTest.kt
  - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/KeyIsolationTest.kt
  - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/ModelCapabilityTableTest.kt
  - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/ModelRouterTest.kt
  - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/NativeReplayTest.kt
  - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/NeverThrowTest.kt
  - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/NoHardCodedConstantsTest.kt
  - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/OnDeviceGateTest.kt
  - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/ProviderRouterTest.kt
  - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/RedactionCanaryTest.kt
  - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/RunSetupGuardTest.kt
  - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/SeamTypesTest.kt
  - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/ToolSpecClarificationTest.kt
  - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/TraceTest.kt
  - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/TranscriptTypesTest.kt
  - core/src/testFixtures/kotlin/io/github/ygaray/voiceactionengine/core/testing/FakeAiProvider.kt
  - core/src/testFixtures/kotlin/io/github/ygaray/voiceactionengine/core/testing/ScriptedSources.kt
  - scripts/review-api-surface.sh
covered_digest: "v1:sha256:201da974ce3913d5f00c7dc0eed29ec308dda0a63d017e4c991411342c849bf0"
advisory:
  - finding: "ToolSpec keeps Kotlin default arguments (synthetic default-argument constructor stub), so a seventh constructor parameter would be a binary break for Kotlin callers"
    category: architectural
    reason: "Review WR-02 flagged it; the fix pass chose not to remove source-compatible call shapes Phase 2 shipped. It added a stub-lint over every package with a stale-entry check and a KDoc rule that future attributes arrive as with... members. Guarded, not eliminated. Revisit before the v1.0.0 API dump if a seventh ToolSpec attribute is planned."
    evidence_status: "KDoc rule and ApiShapeTest allow-list present; no failing test"
  - finding: "Guarded.kt carries a file-level @file:Suppress(TooGenericExceptionCaught) (moved from a function-level suppress by WR-05)"
    category: other
    reason: "Still the repo's only suppression in core/src/main in one file; detekt maxIssues 0 green. The fixer asked a reviewer to confirm the file-level form is acceptable."
    evidence_status: "grep of core/src/main shows exactly one @Suppress"
  - finding: "WR-08 (Message and AssistantPart sealed) skipped by design"
    category: architectural
    reason: "Locked by decision D-01 and the architecture research; adding a leaf later is a section 10 contract change, not a patch addition."
    evidence_status: "documented in 03-REVIEW-FIX.md"
---

# Phase 3: Transcript Types, ProviderRouter & On-Device Gate - Verification Report

**Phase Goal:** The engine picks the provider, model and key for each command through app seams, holds neutral multi-turn transcript types in `:core`, and offers an `ON_DEVICE` slot that falls back cleanly on devices without on-device support. All pure JVM, no HTTP.
**Verified:** 2026-10-01T04:45:00Z
**Status:** passed
**Re-verification:** No - initial verification

The adversarial starting position was that the SUMMARY claims were unproven. Every truth below was checked against the source in `core/src/main`, the tests that exercise it, and a fresh `./gradlew check` run in this session (exit 0).

## Goal Achievement

### Observable Truths (ROADMAP success criteria)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | `:core` holds the neutral transcript types (messages, tool calls, tool results, system, usage, stop reason, cache directive, verbatim `NativeReplay` on assistant turns); a single-shot request and a multi-turn tool conversation are expressible; `:core` has no HTTP dependency | VERIFIED | `transcript/Message.kt` (sealed `UserMessage`/`AssistantMessage`/`ToolResultsMessage`, `ToolResult`), `AssistantPart.kt` (`Text`/`ToolCall`), `ModelRequest.kt` (system, `ToolChoice`, `CacheDirective`), `ModelResponse.kt` (`StopReason`, `Usage`), `NativeReplay.kt` (nullable, stamped with provider and model, returned only by exact `nativeFor` match, redacted `toString`). `TranscriptTypesTest.singleShotRequestReadsBackFieldByField` and `multiTurnToolConversationIsExpressibleAndReadsBack` pass. `:core` runtimeClasspath resolved in this session is only coroutines-core, serialization-json and kotlin-stdlib; `verify-negative-controls.sh` (phase-gate evidence) proves an HTTP dep goes red. |
| 2 | Router asks the selection seam for provider, model and key once per command; a switch applies next command; a mid-command settings change does not alter the running command; missing key gives `NotConfigured` after zero provider calls; one provider's key is never handed to another | VERIFIED | `RunSession.model()` binds lazily under a `Mutex` and freezes the `BoundModel` (`bound` assigned only after the bind returns, so a cancelled bind freezes nothing). `ModelRouter.bind` resolves selection, gate, on-device probe, registration, credential for that provider id only, then capabilities, and any refusal returns a `RefusedModel` that calls nothing. `present()` refuses a `Credential` stamped for another provider. Tests: `ProviderRouterTest.threeTurnsInOneTierAskTheSelectionOnceAndAllGoToTheSameModel`, `aSelectionThatChangesMidCommandDoesNotMoveTheCommandButTheNextCommandUsesIt`, `twoConcurrentModelCallsInOneTierResolveOnceAndShareTheSameHandle`, `aMissingKeyEndsNotConfiguredForThatProviderWithNoCall`; `KeyIsolationTest` (5 tests incl. hostile source and fallback path); `ModelRouterTest.aCredentialStampedForAnotherProviderIsRefusedAndNeverSent`. |
| 3 | When `ON_DEVICE` reports unavailable the router uses only the app's declared fallback: no key or provider substitution, no Nano/AICore code, loud typed failure when no fallback is declared | VERIFIED | `OnDeviceCapability` default is `Unavailable("not_implemented")`; one probe instance feeds both `PolicyPreCheck` and `ModelRouter` (`PipelineBuilder.wiring`). `ModelRouter.viaFallback`: no fallback gives `Failed(ProviderUnavailable(ON_DEVICE, "on_device_unavailable"))` before any key lookup; a fallback is itself passed through `providerGate` (tier-declared, allowed providers, offline-only) and binds with its own provider's key; `ProviderSelection` rejects a fallback on a non-on-device selection, an on-device fallback and chains at construction. Tests: `OnDeviceGateTest` (13), `ModelRouterTest.anOnDeviceSelectionThatIsNotReadyFailsLoudlyWithoutTouchingAKey`, `KeyIsolationTest.theFallbackPathAsksForTheFallbackProvidersKeyAloneAndSendsOnlyThatKey`. `NoHardCodedConstantsTest.noOnDeviceImplementationCode` scan passes; grep of `core/src/main` for nano/aicore/genai/mlkit tokens returns nothing. |
| 4 | A fake provider that declares caching and returns zero cache read/write on a prefix above the model's minimum raises `CacheNotEngaged`; the same result below the minimum stays silent | VERIFIED | `provider/CacheDetector.kt` (`shouldFlagCacheMiss`, `estimatedPrefixTokens` capped by the billed prompt total, overflow-safe) called from `RoutedModel.reportMissedCache`, which uses the existing `RunRecorder.cacheNotEngaged` event. Silent when the directive does not cache the prefix, the minimum is unknown, the mode is NONE/unknown, or the estimate is below the minimum. `CacheNotEngagedTest` (31 tests) includes `aLargeStaticPrefixThatTheCacheIgnoredRaisesExactlyOneEventAndNothingElse`, `aPrefixBelowTheMinimumStaysSilent`, boundary tests at exactly the minimum and one below, the automatic-mode turn rule, and (post review fix) `twoOverlappingFirstRequestsOnOneHandleAreBothTurnOneAndRaiseNothing`. |
| 5 | Limits and model ids reach the engine only through `TierPolicy` defaults, the selection seam and the app-overridable capability table; no hard-coded model-id or limit constant in library code; no library code reads app settings storage | VERIFIED | `ProviderSelection.model` is required with no default (D-04). `ModelCapabilityTable` precedence is override, then provider default, then provider unknown-id default; overrides are validated at build time. `NoHardCodedConstantsTest` (12 tests, non-vacuous scan check) passes: no model id in code, KDoc or comments; limit constants declared only by their owners; no file/env/property/preference access. Independent grep of `core/src/main` for model-id tokens and settings APIs found none. The remaining `const val`s are the defaults themselves (`TierPolicy` 60_000 / 4_096, the confirm-timeout default, the 4.0 chars-per-token default in `ModelCapabilities`), each overridable by the app. |

**Score:** 5/5 truths verified (0 present-but-behavior-unverified). The behavior-dependent invariants (freeze under cancellation and concurrency, no double-record on a cancelled fallback bind, per-handle turn counting) each have a named test that exercises the transition, not just symbol presence.

### Required Artifacts (Levels 1-4)

| Artifact | Status | Details |
|----------|--------|---------|
| `transcript/*.kt` (5 files) | VERIFIED | Substantive, redacted `toString`, used by `AiProvider`/`ModelRouter`/`BoundModel`; sealed set pinned by `scripts/review-api-surface.sh` (`API SURFACE OK ... classes=161`, run in this session) |
| `provider/AiProvider.kt`, `ProviderRequest.kt`, `ModelResult.kt` | VERIFIED | Wired through `BoundModel.complete` to the registered provider; `FakeAiProvider` (testFixtures) implements the contract |
| `provider/ProviderSelection.kt`, `CredentialSource.kt`, `OnDeviceCapability.kt` | VERIFIED | Public seams; consumed by `ModelRouter` and `PipelineBuilder` |
| `provider/ModelRouter.kt`, `BoundModel.kt` | VERIFIED | Reached via `RunSession.model()` from `TierWalk` via `CommandPipeline`; data flows from the app's source to the provider call and the trace (FLOWING) |
| `provider/ModelCapabilities.kt`, `ModelCapabilityTable.kt`, `CachingMode.kt` | VERIFIED | Table is built in `PipelineBuilder.wiring` from registered providers plus validated overrides and exposed in `PipelineWiring` |
| `provider/CacheDetector.kt` | VERIFIED | Called on every successful routed response |
| `pipeline/PipelineBuilder.kt` additions (`provider`, `providerSelection`, `credentials`, `onDevice`, `capabilities`) | VERIFIED | Duplicate-provider and bad-override validation covered by `ProviderRouterTest` |
| `strategy/CommandSession.model()` | VERIFIED | Public abstract; implemented in `RunSession` |

### Key Link Verification

| From | To | Status | Details |
|------|----|--------|---------|
| `CommandPipeline` | `TierWalk` / `RunScope.router` | WIRED | `TierWalk(..., wiring.router)` |
| `RunSession.model()` | `ModelRouter.bind` | WIRED | lazy, mutex-guarded, frozen |
| `PipelineBuilder` | `PolicyPreCheck` and `ModelRouter` | WIRED | same `probe` instance; `ProviderRouterTest.thePreCheckAndTheRouterReadOneOnDeviceProbeInstanceTwicePerCommand` |
| `RoutedModel.complete` | `RunRecorder.turnRecorded` / `cacheNotEngaged` | WIRED | every call records a turn; the cache event reuses the Phase 2 `PipelineEvent.CacheNotEngaged` |
| `ModelRouter` | `CredentialSource` | WIRED | asked only for the selected (or fallback) provider id, and only when `requiresCredential` |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| Whole build gate (detekt zero baseline, `scanBannedConstructs`, all JVM tests, lint) | `./gradlew check` | exit 0, BUILD SUCCESSFUL | PASS |
| `:core` test results (cached task, results from the last real run after the final fix) | JUnit XML under `core/build/test-results/test` | 422 tests, 0 skipped, 0 failures, 0 errors | PASS |
| Public API surface and sealed allow-list | `bash scripts/review-api-surface.sh` | `API SURFACE OK sealed=AssistantPart,CommandOutcome,GateDecision,Message,RunTermination,StrategyOutcome,ToolStep classes=161` | PASS |
| `:core` runtime classpath has no HTTP/Android/DI | `./gradlew :core:dependencies --configuration runtimeClasspath` | coroutines-core, serialization-json, kotlin-stdlib only | PASS |

### Probe Execution

No phase-declared probe scripts (`scripts/*/tests/probe-*.sh`). The phase gate scripts (`verify-negative-controls.sh`, `verify-repo-hygiene.sh`, `verify-api-dump.sh`) were run by the executor and are recorded in `evidence/phase-gate.txt` (all exit 0); not re-run here beyond `review-api-surface.sh`.

### Requirements Coverage

Union of PLAN frontmatter `requirements` across 03-01..03-10 equals the seven roadmap IDs; none is orphaned, and REQUIREMENTS.md maps no other ID to Phase 3.

| Requirement | Source Plans | Description | Status | Evidence |
|-------------|--------------|-------------|--------|----------|
| PROV-01 | 03-01, 03-05, 03-10 | Neutral multi-turn transcript types with `NativeReplay` in `:core` | SATISFIED | SC1 evidence |
| PROV-02 | 03-03, 03-04, 03-05, 03-06, 03-07, 03-08, 03-10 | Per-call app seam for provider/model/key; switch applies next command; `NotConfigured` before any network call; no cross-provider key | SATISFIED | SC2 evidence; `ModelRouterTest`, `ProviderRouterTest`, `KeyIsolationTest`. Early checkbox in REQUIREMENTS.md is now backed by complete plans |
| PROV-03 | 03-07, 03-10 | Provider/model snapshotted once per command (per tier), never re-read per iteration | SATISFIED | `RunSession.model()` freeze; `threeTurnsInOneTier...`, `aSelectionThatChangesMidCommand...` |
| PROV-10 | 03-02, 03-03, 03-04, 03-06, 03-07, 03-08, 03-10 | `ON_DEVICE` slot with runtime capability gate; S22s fall back only via the declared fallback; no Nano/AICore | SATISFIED | SC3 evidence; `OnDeviceGateTest` |
| TEL-03 | 03-02, 03-09, 03-10 | `CacheNotEngaged` above minimum, silent below | SATISFIED | SC4 evidence |
| CLN-03 | 03-02, 03-10 | Limits and model ids from policy/config defaults | SATISFIED | SC5 evidence |
| CLN-04 | 03-02, 03-03, 03-07, 03-10 | Library never reads app settings storage | SATISFIED | `NoHardCodedConstantsTest.noFileEnvironmentPropertyOrPreferenceAccess` plus grep |

### Anti-Patterns Found

| File | Pattern | Severity | Impact |
|------|---------|----------|--------|
| `core/src/main`, `core/src/testFixtures` | TBD/FIXME/XXX debt markers | none found | Debt-marker gate clear |
| `core/src/main` | placeholder/stub returns | none found | Router, detector and handles are substantive and tested |
| `strategy/ToolSpec.kt` | Kotlin default-argument constructor stub | Info (advisory) | See frontmatter `advisory`; guarded by lint and KDoc rule, no goal impact |
| `internal/Guarded.kt` | file-level `@file:Suppress` | Info (advisory) | Still the single justified suppression |

### Prior-phase regression check

Phase 1 and Phase 2 were `status: passed, 5/5`. Phase 2 surfaces that Phase 3 modified (`ToolSpec`, `Guarded`, `RunRecorder`, `CommandPipeline`, `TierWalk`, `PolicyPreCheck` hook, `FailureReason`) are covered by the same green `./gradlew check` (including `NeverThrowTest`, `GuardedTest`, `ToolSpecClarificationTest`, `TierWalkTest`, `PipelineSpineTest`). Phase 2's on-device-only-tier-never-climbs ruling still holds (`OnDeviceGateTest.anOnDeviceOnlyTierWithTheDefaultGateFailsBeforeTheStrategyRunsAndNeverAsksTheSelection`). The Phase 1 gates (detekt zero baseline, banned constructs, JVM 11, no baseline file) pass in the same run. No regressions found.

### Code review disposition

`03-REVIEW.md`: 0 critical, 8 warnings, 3 info. `03-REVIEW-FIX.md` (status resolved): 10 fixed, WR-08 skipped by documented design. Fix commits 58014e1 through fe4cff3 are on `main`, and the test results and `./gradlew check` verified here include them.

### Human Verification Required

None. Phase 3 is pure JVM with no UI, device or network behavior. The repo's two-gate UAT applies where device-verifiable; nothing here is. The review-fix notes marked WR-01, 03, 04, 05 and 06 "requires human verification" as a semantic-choice confirmation. Each is covered by a named test that fails on the old behavior (WR-03 was confirmed fail-first by stash), so they are recorded as advisories, not blockers.

### Gaps Summary

No gaps. All five roadmap success criteria hold in the code, all seven requirement IDs are accounted for and satisfied, and the final tree builds and tests green. Phase 3 is ready to proceed to Phases 4 and 5.

---

_Verified: 2026-10-01T04:45:00Z_
_Verifier: Claude (gsd-verifier)_
