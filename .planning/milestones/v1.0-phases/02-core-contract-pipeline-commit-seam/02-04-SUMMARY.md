---
phase: 02-core-contract-pipeline-commit-seam
plan: 04
subsystem: core
status: complete
tags: [kotlin, pipeline, tier-selector, tier-policy, never-throw, cancellation, engine-deadline, detekt]

requires:
  - phase: 02-core-contract-pipeline-commit-seam
    provides: "02-03 spine (commandPipeline, TierWalk, CommitCoordinator, RunRecorder, run close); 02-02 guarded helper and TraceCode set; 02-01 TierPolicy, FailureReason, StrategyId, ProviderId"
provides:
  - "TierSelector (non-sealed abstract class, internal ctor) with Linear and Fixed(tier); PipelineBuilder.selector"
  - "StrategyCapabilities (ANY_PROVIDER, NO_PROVIDER) and CommandStrategy.capabilities (default body)"
  - "internal PolicyPreCheck: maxTier, allowedProviders, offlineOnly and the ON_DEVICE rule, applied once per execute before any strategy"
  - "never-throw execute(): strategy, policy-source and engine faults collapse through guarded; engine deadline via withTimeoutOrNull; cancellation-correct run close"
  - "ScriptedStrategy fixture overloads taking capabilities"
affects: [02-05, 02-06, 02-07, 02-08, 02-09, Phase 3, Phase 4, Phase 5, Phase 6]

plan_head_before: eeecf6e11afdbe0f7de22e7f63d1a2fdd89f495a

actuals:
  tokens: 15000   # chars/4 over the realized diff of core/ (61,049 chars incl. diff headers)
  tasks: 3
  commits: 3

tech-stack:
  added: []
  patterns:
    - "Policy is applied from static capability declarations before any strategy runs; a refusal is a specific Failed with zero executions"
    - "Ladder (internal) carries the eligible tiers, the selector and the ON_DEVICE availability read once per execute, so TierWalk stays under the constructor-parameter threshold"
    - "Collapse outside, deadline inside: guarded wraps the whole body, withTimeoutOrNull wraps pre-check plus walk"
    - "Constants are file-private (a companion const on an internal class still leaks a public static field)"

key-files:
  created:
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/pipeline/TierSelector.kt
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/pipeline/PolicyPreCheck.kt
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/StrategyCapabilities.kt
    - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/TierSelectorTest.kt
    - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/TierWalkTest.kt
    - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/NeverThrowTest.kt
  modified:
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/pipeline/CommandPipeline.kt
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/pipeline/PipelineBuilder.kt
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/pipeline/TierWalk.kt
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/CommandStrategy.kt
    - core/src/testFixtures/kotlin/io/github/ygaray/voiceactionengine/core/testing/ScriptedStrategy.kt
    - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/TierPolicyTest.kt
    - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/PipelineBuilderTest.kt

key-decisions:
  - "The internal ON_DEVICE availability hook is PipelineBuilder.onDeviceAvailability: suspend () -> Boolean, default { false }. Phase 3's ON_DEVICE gate assigns it; no public type exists. It is read at most once per execute, only when some tier declares ON_DEVICE, and a throwing hook counts as unavailable."
  - "Under offlineOnly a tier runs only if it declares no provider, or declares exactly {ON_DEVICE} and the hook says available. A tier declaring ON_DEVICE plus a cloud provider is refused offline (stricter than 'ON_DEVICE declared'), because it could reach the network and the requirement is zero HTTP calls."
  - "Escaping Errors that guarded does not collapse (AssertionError, OutOfMemoryError) propagate, and the run closes as RunTermination.Failed(Unexpected(\"Error\"), null). Cancellation (plain, or a timeout while the caller is cancelled) closes as Cancelled. A produced outcome maps as before."
  - "Engine-level faults caught by the top guarded record no trace code (TraceCode has no engine-error code and the file is frozen for this plan); the failure carries Unexpected(errorClass) instead."

requirements-completed: [CORE-01, CORE-02, CORE-03, CORE-04]
requirements-partial: [CORE-05]

duration: ~40min
completed: 2026-09-30
---

# Phase 2 Plan 04: Tier selector, policy pre-check and never-throw execute Summary

**The ladder now starts where `TierSelector` says, drops tiers per `TierPolicy` from static capability declarations before any strategy runs, refuses loudly and specifically when nothing may run, and `execute()` returns a typed outcome for every non-cancellation throw while closing each run exactly once.**

## Performance

- **Tasks:** 3 of 3 (Task 1 tracer, Tasks 2 and 3 TDD)
- **Commits:** 8ced741 (task 1), d3aa1f2 (task 2), 13e1f9e (task 3)
- **Files:** 6 created (3 main, 3 test classes), 7 modified

## Accomplishments

- `TierSelector.Linear` (default) and `TierSelector.Fixed(id)`: Fixed starts mid-ladder and climbs, carry reaches the next tier by identity (assertSame), an unknown Fixed id throws at build ("commandPipeline: selector names unknown tier <id>"), and a Fixed tier removed at run time by `maxTier` ends `Failed(NoEligibleTier)`.
- Policy pre-check, once per execute and before any strategy: `maxTier` cuts the ladder (tiers after it recorded `tier_skipped_policy`; an id not on the ladder is `Failed(NoEligibleTier)` with `max_tier_unknown`), `allowedProviders` drops tiers whose declared providers do not intersect (`{}` lets only a NO_PROVIDER tier run), `offlineOnly` with nothing eligible is `Failed(ProviderUnavailable(ON_DEVICE, "offline_unavailable"))` with zero executions inside `NoNetworkGuard.during`.
- An ON_DEVICE-only tier reached while on-device is unavailable ends `Failed(ProviderUnavailable(ON_DEVICE, "on_device_unavailable"))`; the cloud tier above it runs zero times (T-02-16).
- A throwing `TierPolicySource` is `Failed(PolicyUnavailable)`, zero executions, `policy_source_error`.
- Never-throw: a throwing strategy is `Failed(Unexpected(<class>))` with `strategy_error`; a `NoSuchMethodError` collapses the same way; a leaked `withTimeout` is `Failed(Timeout)`; `commandTimeoutMillis` expiry is `Failed(Timeout)` with `engine_timeout`, earlier commits retained in `executed`, and never `Network`.
- Cancellation: cancelling the caller makes the awaiting side see `CancellationException`, and the sink's suspending `onRunClosed` hook (it `delay`s) still completes inside `NonCancellable` and fires once as `Cancelled`. A strategy throwing a plain `CancellationException` propagates and closes once as `Cancelled`. An `AssertionError` propagates and closes once as `Failed(Unexpected("Error"))`.

## Internal signatures later plans build on

```
PipelineBuilder.onDeviceAvailability: suspend () -> Boolean   // internal var, default { false } (Phase 3 plugs in here)
PolicyPreCheck(strategies, selector, onDeviceAvailability).check(policy, recorder): Ladder   // internal
Ladder(tiers, selector, onDeviceAvailable, refusal: FailureReason?)   // internal; blockedOnDevice(tier), onDeviceFailure()
TierWalk(ladder, policy, coordinator, recorder, runId, parentRunId).run(input): CommandOutcome
CommandPipeline internal constructor(preCheck, gate, sink, policySource, clock, runIds)
StrategyCapabilities.onDeviceOnly: Boolean   // internal
```

## Detekt rules that shaped the code

- `ReturnCount` (limit 2) forced `TierWalk.run` into `run` plus `climb`, `PolicyPreCheck.check` into `check` plus `nothingMayRun`, and `CommandPipeline` into `execute`, `runCommand`, `withDeadline`, `walk`.
- `LongParameterList` (constructor threshold 8) is why `PolicyPreCheck` owns the strategies, selector and ON_DEVICE hook and `Ladder` carries selector and availability into `TierWalk`: passing them separately would have given `CommandPipeline` and `TierWalk` 8 parameters.
- `MaxLineLength` forced two reflows in main and two in tests.

## Verification evidence

- `./gradlew :core:check :providers:check`: BUILD SUCCESSFUL, exit 0 (detekt zero issues, `scanBannedConstructs`, structural gates, `verifyNoTestFixturesPublished`, providers OkHttp 4.12.0 / 5.2.1 / 5.5.0 legs).
- `:core` tests: 122 total, 0 failures, 0 skipped (new or extended: TierSelectorTest 4, TierWalkTest 9, TierPolicyTest 24 of which 16 new, NeverThrowTest 7, PipelineBuilderTest +2).
- `scripts/review-api-surface.sh --expect-sealed-complete`: `API SURFACE OK sealed=CommandOutcome,GateDecision,RunTermination,StrategyOutcome,ToolStep classes=101`.
- `@Suppress` count in `core/src/main`: 1 (Guarded.kt only). No `api.txt` in any module directory.
- Acceptance greps: TierSelector.kt has `public abstract class TierSelector internal constructor() {`, `object Linear`, `class Fixed(`; PipelineBuilder.kt has `selector` and the unknown-tier message and an internal `onDeviceAvailability`; PolicyPreCheck.kt has `offlineOnly`, `allowedProviders`, `maxTier`, `ON_DEVICE`, `offline_unavailable`, `on_device_unavailable`; CommandPipeline.kt has `withTimeoutOrNull`, `commandTimeoutMillis` (via policy), `catch (e: CancellationException)` and `NonCancellable`.
- Untouched per the prohibitions: everything under `core/commit/`, `RunSession.kt`, `RunRecorder.kt`, `Guarded.kt`, `TraceCode.kt`, and the ScriptedGate, RecordingCommitSink, FakeMutation fixtures.

## Deviations from Plan

**1. [Rule 1 - Bug] A companion `const val` on an internal class failed the surface lint**
- `PolicyPreCheck` first declared `private companion object { const val UNKNOWN = -1 }`; the const compiles to a public static field on the outer class and `ApiShapeTest` rejected it (same finding as plans 02 and 03). Fix: a file-private top-level `const val NOT_FOUND`. Folded into the Task 2 commit by amending before anything was pushed.

**2. [Rule 2 - Missing critical] offlineOnly refuses mixed ON_DEVICE-plus-cloud tiers**
- The plan text allows a tier "with ON_DEVICE declared, available and allowed". A tier declaring ON_DEVICE and a cloud provider could still reach the network, which would violate the zero-HTTP requirement (T-02-15). The pre-check requires the declared set to be exactly `{ON_DEVICE}` under offlineOnly. No test in the plan covers the mixed case; behavior for the plan's stated cases is unchanged.

**3. Internal structure beyond the plan text**
- `Ladder` (internal) and moving `strategies`/`selector`/`onDeviceAvailability` into `PolicyPreCheck` were added to stay under the constructor-parameter threshold without tuning detekt. `CommandPipeline`'s internal constructor changed shape accordingly (not public API).

## Requirements bookkeeping

CORE-01 (DSL with selector and policy), CORE-02 (ladder climb, stop, carry), CORE-03 and CORE-04 are fully delivered by plans 01 to 04 and marked complete. CORE-05 stays Pending: strategy, policy-source and engine faults, timeouts and cancellation are done here, but gate-throw and apply-throw collapse and cancellation mid-apply are plan 05's, so the requirement is only partly proven.

## Known stubs / deferred

- Interim from plan 03 still open for plan 05: Hold, Finished classification, apply cancellation. Escalation guard after commit or hold is plan 06's (`TierWalk` still escalates regardless of `appliedCount`/`heldCount`).
- Engine-level faults caught by the top guard have no dedicated trace code; adding one is an additive `TraceCode` change for a later plan.
- Pre-existing unrelated working-tree changes (.planning/graphs, config.json, milestone files, graphify-out, .gsd) were left untouched and unstaged.

## Self-Check: PASSED

- All 6 created files exist and the 7 modified files are in commits 8ced741, d3aa1f2 and 13e1f9e (`git log`); `git rev-list --count eeecf6e..HEAD` = 3 at the last task commit.
