---
phase: 16-start-tier-selection
plan: 03
subsystem: pipeline
tags: [start-tier, picker, tier-selector, telemetry, api-docs]

requires:
  - phase: 16-start-tier-selection
    provides: TierSelector.Custom, StartTierPicker, SelectionBook, CommandTrace.selection (plan 16-02)
provides:
  - TierSelector.Custom.Builder (id, capabilities) and Custom.Companion.invoke(picker, block); Custom(picker) stays callable
  - Build-time rejection of a picker id equal to a tier id
  - PipelineEvent.StartTierSelected(runId, selection), delivered live when a picker ran
  - API.md rows for StartTierPicker, PickContext, StartTierSelection and router_fallback
affects: [16-04, 16-05, 16-06, 16-07]

actuals:
  tokens: 12000
  tasks: 3
  commits: 3

plan_head_before: 44bfddcfb38ceb3e271fba8070f1ba2f2e9f0dae
commits: 3

key-files:
  modified:
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/pipeline/TierSelector.kt
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/pipeline/PipelineBuilder.kt
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/telemetry/PipelineEvent.kt
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/telemetry/SelectionBook.kt
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/telemetry/RunRecorder.kt
    - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/StartTierTestSupport.kt
    - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/StartTierPickerTest.kt
    - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/TierSelectorTest.kt
    - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/PipelineBuilderTest.kt
    - API.md

key-decisions:
  - "Custom keeps a public constructor(picker) over an internal (picker, Builder) primary; the builder form is a companion invoke, so no default-argument constructor exists"
  - "A picker's model comes only from the app's ProviderSelectionSource for the picker's own id; an unmapped picker is a refused handle, provider_not_selected then router_fallback, Linear walk"
  - "SelectionBook sends StartTierSelected after releasing the lock; RunRecorder gained no member function (still 11)"

requirements-completed: []

duration: 25min
completed: 2026-10-06
status: complete
---

# Phase 16 Plan 03: Named picker, collision check, StartTierSelected event Summary

**An app can name its start-tier picker and its providers; the engine binds the picker's model from the app's own selection mapping for that id, rejects a picker id that shadows a tier at build time, and delivers the selection as a live `StartTierSelected` event.**

## Accomplishments

- Task 1 (tracer): `TierSelector.Custom` is now `Custom internal constructor(picker, settings: Builder)` with a public `constructor(picker)`, public `id`, nested `Builder` (`id`, `capabilities`) and `Companion.invoke(picker, block)`. The tracer test maps `app_picker` to `picker-model` through the new `MappedSelection` fixture and asserts the seam was asked for `app_picker` before `agentic`, the picker turn and the provider call carry `picker-model`, and the trace names `app_picker`.
- Task 2: `PipelineBuilder.build` requires the picker id to differ from every tier id ("picker id b collides with a tier id"). An unmapped picker records `provider_not_selected` then `router_fallback`, the selection outcome is `router_fallback`, and the first model tier runs Linear to a completed outcome.
- Task 3: `PipelineEvent.StartTierSelected` is sent by `SelectionBook.finished` (now takes `runId` and `dispatch`). The tracer run's event order is exactly CommandStarted, TierStarted, TierFinished, ProviderCall, StartTierSelected, TierStarted, TierFinished, RunClosed; a fallback shows ProviderCall, EngineCode, StartTierSelected; a Linear run has none. API.md gained the package-block entries, three type rows, the `TierSelector` row, the build-time failure, the event, `CommandTrace.selection`, `router_fallback` and the extension-point row, with no new code fence.
- Wave-end gate `:core:test :core:detekt :core:scanBannedConstructs :core:metalavaCheckCompatibility` exit 0, and `scripts/verify-docs-coverage.sh --only C20,C21,C10` printed `DOC COVERAGE OK`.

## Task Commits

1. **Task 1: named picker and providers, selection seam binds the picker model (tracer)** - `81e0b04`
2. **Task 2: picker-id collision check and the loud-but-harmless unmapped picker** - `b8247f9`
3. **Task 3: StartTierSelected event and API.md rows** - `97cdc68`

## Deviations from Plan

**1. [Already satisfied] `router_fallback` wire pin**
- Plan 16-02 already added `startTierCodes` and `theStartTierCodesHaveTheirSnakeCaseWireValues` to TraceTest, which pins `ROUTER_FALLBACK` to `router_fallback`. A second one-entry map would duplicate it, so TraceTest was left unchanged.

**2. [Added coverage] Linear run test**
- Added `aLinearRunDeliversNoStartTierSelectedEvent` in StartTierPickerTest instead of editing the characterization file (which may only gain assertions in plan 16-01's contract).

**3. [Plan sequencing] Task 2 KDoc**
- The PipelineBuilder `selector` KDoc now lists the two build-time selector failures (done in Task 2's commit).

## Verification

- Acceptance greps for all three tasks pass (Custom ctor and invoke signatures, `CUSTOM_PICKER_ID`, `picker-model` count, `collides with a tier id`, event constructor, API.md names and no added fence, RunRecorder 11 own functions).
- No Gradle run was killed by earlyoom.

## Requirements

ROUT-01 and ROUT-05 are not marked complete: plan 16-07 also carries ROUT-01 and plan 16-06 completes ROUT-05.

## Threat Flags

None beyond the register: T-16-10 (collision require), T-16-11 (no model named in main; NoHardCodedConstantsTest green), T-16-12 (event carries id/count-only record via EventDispatch), T-16-13 (unmapped picker never fails the command) are mitigated as written.

## Self-Check: PASSED

- FOUND commits: 81e0b04, b8247f9, 97cdc68
- `git rev-list --count 44bfddc..HEAD` = 3
