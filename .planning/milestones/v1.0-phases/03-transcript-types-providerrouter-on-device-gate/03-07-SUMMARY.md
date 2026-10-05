---
phase: 03-transcript-types-providerrouter-on-device-gate
plan: 07
subsystem: core
status: complete
tags: [kotlin, pipeline, model-router, frozen-handle, capability-overrides, on-device-gate, prov-02, prov-03, prov-10, cln-04]

requires:
  - phase: 03-transcript-types-providerrouter-on-device-gate
    provides: "ModelCapabilityTable (03-02), CredentialSource/ProviderSelectionSource and scripted fakes (03-03), AiProvider/FakeAiProvider (03-05), ModelRouter/BoundModel (03-06)"
provides:
  - "CommandSession.model(): BoundModel - bound once per command per tier, lazily, Mutex-guarded and frozen"
  - "commandPipeline DSL: provider(), providerSelection, credentials, capabilities(provider, model) { } with build-time validation"
  - "CommandPipeline.capabilityTable (public) for model pickers"
  - "One onDeviceAvailability instance shared by PolicyPreCheck and ModelRouter"
affects: [03-08, 03-09, 03-10]

plan_head_before: b386423bbf14ef5d05b00f99754d28f1b91f5414

commits: 3

actuals:
  tokens: 10800   # chars/4 over the realized core diff (about 43,100 chars)
  tasks: 3
  commits: 3

tech-stack:
  added: []
  patterns:
    - "Lazy frozen slot: @Volatile field read first, then a Mutex.withLock re-check; the slot is assigned only after bind returns, so a cancelled bind freezes nothing"
    - "Per-command RunScope shared by every tier session; per-tier state (the slot and the declared providers) lives in RunSession"
    - "Build-time validation by running each override block once against ModelCapabilities.UNKNOWN"

key-files:
  created:
    - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/ProviderRouterTest.kt
  modified:
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/CommandSession.kt
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/pipeline/RunSession.kt
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/pipeline/TierWalk.kt
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/pipeline/PipelineBuilder.kt
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/pipeline/CommandPipeline.kt

key-decisions:
  - "The handle slot is per RunSession (one per tier-run), not per command: a two-tier ladder asks the selection once per tier, and a refusal is frozen too (a refused handle is a value, so a tier that was refused stays refused for that command)"
  - "PipelineWiring(preCheck, router, table) is the internal holder that keeps CommandPipeline's constructor at seven parameters; the public capabilityTable reads from it"
  - "A cancelled model() call leaves the slot empty because the assignment happens after bind returns; the Mutex is released by withLock on cancellation"
  - "Overrides are stored as declared and validated in build() (blank model, duplicate exact pair, block run once against UNKNOWN); capabilities() itself never throws"

requirements-completed: [PROV-02, PROV-03, PROV-10, CLN-04]

coverage:
  - id: D1
    description: "A strategy calls session.model().complete(request) inside commandPipeline { provider(...); providerSelection; credentials }; the registered provider is called once with the selected model, the right key and policy.maxTokensPerTurn; the turn lands in the tier's trace attempt (provider, model, usage, latency on the pipeline clock) and in session.tokensUsed; the strategy never sees the Credential"
    requirement: "PROV-02"
    verification:
      - kind: unit
        ref: "core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/ProviderRouterTest.kt#aStrategyCallsTheSelectedProviderThroughTheSessionAndTheTurnLandsInTheTrace"
        status: pass
      - kind: unit
        ref: "core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/ProviderRouterTest.kt#buildRejectsADuplicateProviderId"
        status: pass
    human_judgment: false
  - id: D2
    description: "Selection is resolved once per command per tier and frozen: three turns ask once; a mid-command change does not move later turns while the next command uses it; two tiers ask once each naming their tier; concurrent model() calls resolve once and share the handle; a cancelled call freezes nothing; concurrent commands resolve independently"
    requirement: "PROV-03"
    verification:
      - kind: unit
        ref: "core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/ProviderRouterTest.kt (threeTurns..., aSelectionThatChangesMidCommand..., eachTierAsksOnce..., twoConcurrentModelCalls..., aCancelledModelCall..., twoCommandsRunningAtOnce...)"
        status: pass
    human_judgment: false
  - id: D3
    description: "capabilities(provider, model) { } patches only its exact pair; build rejects a duplicate pair, a blank model and an invalid block; capabilityTable is public; a tool request on an overridden tool-incapable model ends ModelUnsupported with capability_refused and zero calls"
    requirement: "CLN-04"
    verification:
      - kind: unit
        ref: "core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/ProviderRouterTest.kt (withoutOverrides..., anOverridePatchesOnlyItsExactPair..., aRoutedRequestWithTools..., buildRejects...x3)"
        status: pass
    human_judgment: false
  - id: D4
    description: "The pre-check and the router read one on-device probe instance (exactly two reads per command); refusals end to end (no selection, missing key, on-device without fallback) are typed, traced, call-free and equal session.model().refusal"
    requirement: "PROV-10"
    verification:
      - kind: unit
        ref: "core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/ProviderRouterTest.kt#thePreCheckAndTheRouterReadOneOnDeviceProbeInstanceTwicePerCommand"
        status: pass
      - kind: unit
        ref: "core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/ProviderRouterTest.kt (noProviderSelection..., aMissingKey..., anOnDeviceSelectionWithoutAFallback...)"
        status: pass
    human_judgment: false
---

# Phase 3 Plan 07: Router Wired Into the Pipeline Summary

**`session.model()` returns a per-tier, lazily bound, Mutex-guarded frozen handle resolved through the 03-06 router from seams declared in `commandPipeline { provider(...); providerSelection; credentials; capabilities(...) }`, with build-time override validation and one on-device probe shared by the pre-check and the router.**

## Accomplishments

- `CommandSession.model()` is the only new session member; the strategy never gets the router or the Credential.
- The selection is asked once per command per tier, on the tier's first `model()` call, and the resulting handle is frozen for that tier-run. A change in the selection mid-command does not apply; the next command sees it.
- Concurrent `model()` calls resolve once and return the same instance; a call cancelled while the source suspends propagates the cancellation and leaves the slot empty.
- `capabilities(provider, model) { }` patches the exact pair; `build()` rejects duplicates, a blank model and an invalid block before any command runs. `CommandPipeline.capabilityTable` is public.
- `PolicyPreCheck` and `ModelRouter` receive the identical `onDeviceAvailability` function instance (counted: exactly two reads per command).
- 18 tests in `ProviderRouterTest`, all passing; `./gradlew check` green; one `@Suppress` in `core/src/main`; `API SURFACE OK`.

## Internals for 03-08, 03-09

- **RunScope** (RunSession.kt, internal): `runId`, `parentRunId`, `policy`, `coordinator`, `recorder`, `router`. `TierWalk` builds it once per command and hands it to every tier's `RunSession(scope, strategy id, declared providers read once, carry)`.
- **PipelineWiring** (CommandPipeline.kt, internal): `preCheck`, `router`, `table`. `CommandPipeline`'s internal constructor stays at seven parameters (`wiring, gate, sink, policySource, clock, runIds, listener`). `TierWalk` takes the router as its seventh parameter.
- **Probe sharing** (PipelineBuilder.kt `wiring()`): `val probe = onDeviceAvailability` is read once and passed to both `PolicyPreCheck` and `ModelRouter`. 03-08 changes only the default of the internal `onDeviceAvailability` property (and adds the public gate behind it).
- **Capability table**: provider defaults call the registered provider's `capabilities(model)` (UNKNOWN for an unregistered id); overrides come from `validatedOverrides()`.
- **Test helpers 03-09 can reuse** (private in `ProviderRouterTest`): `pipelineOf(tiers, providers, selection, credentials, extra)`, `modelStep(turns)` / `runTurns`, `tierOf`, `echoFake(id, calls)` (replies with the called model id), `refusedRun(...)`. The multi-turn run is `tierOf("t", modelStep(3))`; the two-tier run uses an escalating `ScriptedStrategy` then `tierOf("second")` with a `ProviderSelectionSource` keyed on `request.strategy`.

## Task Commits

1. **Task 1 (tracer): session.model() end to end** - `faba5f0` (feat)
2. **Task 2: capabilities overrides, public capabilityTable, shared probe** - `543006b` (feat)
3. **Task 3: snapshot, concurrency, cancellation and refusal tests** - `ab4e176` (test)

## Deviations from Plan

**1. [Process] Task 3 is a test-only commit.** The lazy slot written in Task 1 already satisfied every Task 3 behavior (including the concurrency and cancellation cases), so `RunSession.kt` was not touched again and no RED commit exists.

**2. [Rule 3 - Blocking] Detekt line-length and end-of-file findings in the new test file** fixed in place (no suppression, no config change).

**Total deviations:** 2 (1 process note, 1 auto-fixed lint). **Impact:** none on scope or public shape.

## Issues Encountered

None. No blocker found in `ModelRouter.kt` or `BoundModel.kt`. No Phase 2 test was edited; `git show --name-only` for each plan commit lists only `ProviderRouterTest.kt` among tests.

## Verification

- `./gradlew :core:test --tests '*ProviderRouterTest' --tests '*ModelRouterTest' --tests '*ApiShapeTest' --tests '*TierPolicyTest'`: pass
- `./gradlew :core:detekt :core:scanBannedConstructs`: pass
- `./gradlew check` (whole repo): BUILD SUCCESSFUL
- `scripts/review-api-surface.sh`: `API SURFACE OK` (classes=161, sealed set unchanged)
- Acceptance greps: `abstract suspend fun model(): BoundModel` (1 line), `Mutex` in RunSession.kt, `var providerSelection` / `var credentials` (1 line each), `val capabilityTable` (1 line), `fun capabilities(` (1 line)

## Next Plan Readiness

03-08 can add the public on-device gate behind the internal `onDeviceAvailability` hook and the declared-fallback branch in `ModelRouter.onDevice`; 03-09 can hook the cache diagnostic in `RoutedModel.complete` and drive it through this wiring.

## Self-Check: PASSED

- Files: ProviderRouterTest.kt, RunSession.kt, TierWalk.kt, PipelineBuilder.kt, CommandPipeline.kt, CommandSession.kt exist.
- Commits `faba5f0`, `543006b`, `ab4e176` are in `git log`; `git rev-list --count` from `plan_head_before` is 3 before this SUMMARY commit.
