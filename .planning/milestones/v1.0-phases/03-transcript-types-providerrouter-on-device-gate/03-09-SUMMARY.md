---
phase: 03-transcript-types-providerrouter-on-device-gate
plan: 09
subsystem: core
status: complete
tags: [kotlin, cache-detector, cache-not-engaged, telemetry, tel-03]

requires:
  - phase: 03-transcript-types-providerrouter-on-device-gate
    provides: "routed handle in BoundModel.kt and ModelRouter (03-06), session.model() and commandPipeline provider wiring (03-07), RunRecorder.cacheNotEngaged and CacheNotEngaged event (Phase 2)"
provides:
  - "CacheDetector.kt: internal prefixChars, estimatedPrefixTokens, shouldFlagCacheMiss (D-08..D-10)"
  - "RoutedModel counts its own successful responses and raises one CacheNotEngaged per response that should have hit the cache"
affects: [03-10]

plan_head_before: f98fcfa3a6eeb852c8ea2d575be3f4e412621161

commits: 3

actuals:
  tokens: 7400   # chars/4 over the realized core diff (about 29,700 chars)
  tasks: 3
  commits: 3

tech-stack:
  added: []
  patterns:
    - "Pure internal decision functions in their own file; the stateful handle only owns the per-handle atomic counter"
    - "Estimate errs silent: floor(chars / divisor) capped by the response's own billed prompt tokens"

key-files:
  created:
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/provider/CacheDetector.kt
    - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/CacheNotEngagedTest.kt
  modified:
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/provider/BoundModel.kt
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/telemetry/RunRecorder.kt

key-decisions:
  - "Detection lives in RoutedModel.complete after the turn is recorded, only for ModelResult.Success; failures, refusals and capability-refused requests never reach it"
  - "Only RunRecorder.cacheNotEngaged is called: no EngineCode event, no trace code, no new event kind"
  - "Divisor default stays the 4.0 already owned by ModelCapabilities (overridable per model); no ratio or minimum lives in the detector"
  - "The first-automatic-turn constant is named FIRST_AUTOMATIC_CHECK_TURN so the hard-coded-constants scan keeps its owner rule"

requirements-completed: [TEL-03]

coverage:
  - id: D1
    description: "An explicit-caching model with a known minimum that returns zero cache read and write on a large static prefix raises exactly one CacheNotEngaged (tier, provider, model) and no EngineCode; the same on a small prefix is silent"
    requirement: "TEL-03"
    verification:
      - kind: unit
        ref: "core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/CacheNotEngagedTest.kt#aLargeStaticPrefixThatTheCacheIgnoredRaisesExactlyOneEventAndNothingElse"
        status: pass
      - kind: unit
        ref: "core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/CacheNotEngagedTest.kt#aPrefixBelowTheMinimumStaysSilent"
        status: pass
    human_judgment: false
  - id: D2
    description: "The firing rule is exactly D-08..D-10: every silent-on-unknown case, the explicit and automatic matrices, and the at-minimum and one-below boundaries on both the character path and the prompt-total cap path"
    requirement: "TEL-03"
    verification:
      - kind: unit
        ref: "core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/CacheNotEngagedTest.kt (explicit*, automatic*, theCharacterPath*, thePromptTotalCap*)"
        status: pass
    human_judgment: false
  - id: D3
    description: "Empty prefix, zero usage, UTF-16 length, Long.MAX_VALUE usage and floor truncation are pinned; the explicit prefix-drift case is pinned as a known silent case"
    requirement: "TEL-03"
    verification:
      - kind: unit
        ref: "core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/CacheNotEngagedTest.kt#explicitPrefixDriftWithAWriteIsAKnownSilentCase"
        status: pass
      - kind: unit
        ref: "core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/CacheNotEngagedTest.kt#maximumUsageInEveryBucketDoesNotOverflow"
        status: pass
    human_judgment: false
  - id: D4
    description: "Per-handle turn counting (new command restarts at one, each tier counts its own), failures and refusals never counted or inspected, one event per response, the event names the handle's own provider and model"
    requirement: "TEL-03"
    verification:
      - kind: unit
        ref: "core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/CacheNotEngagedTest.kt (automaticCountsTurnsPerHandle..., eachTierCountsItsOwnTurns..., aProviderFailure..., aRefusedBinding..., aCapabilityRefusedRequest..., anExplicitProviderThatNeverCaches..., theEventNamesTheProviderAndModel...)"
        status: pass
    human_judgment: false
---

# Phase 3 Plan 09: Cache-not-engaged detection Summary

**After every successful routed response the handle decides, from a conservative prefix estimate capped by the billed prompt, whether a caching model should have hit its cache, and raises the existing `CacheNotEngaged` event (ids only) when it is sure.**

## Accomplishments

- `CacheDetector.kt` (internal, pure): `prefixChars` (UTF-16 length of system plus each tool's name, description and compact schema, as `Long`), `estimatedPrefixTokens` (`floor(chars / charsPerToken)` capped by `inputUncached + cacheRead + cacheWrite`, saturating) and `shouldFlagCacheMiss` (exactly the plan's Rule).
- `RoutedModel` gained a private `AtomicInteger` of successful responses. After a Success has been recorded as a turn it increments, evaluates the rule and calls `recorder.cacheNotEngaged(strategy, provider, model)` once when it fires. No `recordCode`, no second event kind.
- `RunRecorder.cacheNotEngaged` KDoc now says the engine calls it after a routed response; no signature change. `PipelineEvent` and the `CacheNotEngaged` constructor are untouched.
- `CacheNotEngagedTest`: 31 tests (tracer end to end through `commandPipeline`, direct rule matrix and edge probes, handle-level behavior).

## Known limitation (for the orchestrator and the Phase 10 README)

An EXPLICIT_BREAKPOINTS turn 2 or later with `cacheRead == 0` and `cacheWrite > 0` (prefix drift, the cache rewritten every turn) is not flagged, because `read + write > 0`. Pinned by `explicitPrefixDriftWithAWriteIsAKnownSilentCase`. Phase 10 Gate-1 must assert `cache_read > 0` on the real device run separately.

## Divisor choice

The estimate uses the `charsPerToken` of the bound model's `ModelCapabilities`, which defaults to 4.0 and is overridable per model. 4.0 sits above the measured 3.01 for tool-schema JSON, so it under-counts tokens and errs toward silence. The second guard is the cap by the response's own billed prompt tokens, which makes an over-count harmless. No minimum or ratio is a library constant in the detector.

## Deviations from Plan

None - plan executed exactly as written. The tracer was verified once by its own `<verify>` run; auto mode re-run was not needed since the same commands ran green again as part of Task 2 and 3 verification.

**Total deviations:** 0. **Impact:** none.

## Authentication Gates

None.

## Verification

- `./gradlew :core:test --tests '*CacheNotEngagedTest' --tests '*EventsTest' --tests '*ModelRouterTest' --tests '*ProviderRouterTest'` green (CacheNotEngagedTest 31/31).
- `./gradlew :core:detekt :core:scanBannedConstructs` green, no baseline, no new `@Suppress` in main.
- `./gradlew check` (all modules) BUILD SUCCESSFUL.
- Acceptance greps: `internal fun shouldFlagCacheMiss` found once; `cacheNotEngaged` present in BoundModel.kt; `only tests reach it` count in RunRecorder.kt is 0.

## Task Commits

1. Task 1 (tracer): b391f2d feat(03-09)
2. Task 2: 13a603b test(03-09)
3. Task 3: 058e784 test(03-09)

## Next Phase Readiness

Ready for 03-10. 03-08 owned `ModelRouter.kt` and `PipelineBuilder.kt`; this plan touched neither.

## Self-Check: PASSED

- CacheDetector.kt and CacheNotEngagedTest.kt exist; commits b391f2d, 13a603b and 058e784 exist on main.
