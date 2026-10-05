---
phase: 12-wave-1-seams-w04-fix
plan: 02
subsystem: core
tags: [reasoning-mode, model-request, single-shot, agentic-loop, on-failed, additive-api]
requires: []
provides:
  - ReasoningMode open value class (OFF, PROVIDER_DEFAULT) in core.transcript
  - ModelRequest 8-argument primary constructor with reasoning, four v1.0 constructors kept as real public constructors
  - SingleShotStrategy.Builder.reasoning and AgenticLoopStrategy.Builder.reasoning, default OFF
  - SingleShotStrategy.Builder.onFailed, fired from the else arm of the provider-failure mapping only
affects: [15, 16]
tech-stack:
  added: []
  patterns: [value class wrapping a String so the synthetic constructor is not read as a default-argument stub, explicit overloads instead of default arguments]
key-files:
  created:
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/transcript/ReasoningMode.kt
    - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/AgenticLoopReasoningTest.kt
    - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ReasoningWireParityTest.kt
  modified:
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/transcript/ModelRequest.kt
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/singleshot/SingleShotStrategy.kt
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/singleshot/SingleShotOutcomes.kt
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/agentic/AgenticLoopStrategy.kt
    - config/detekt/detekt.yml
    - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/SingleShotRequestTest.kt
    - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/TranscriptTypesTest.kt
    - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/ApiShapeTest.kt
    - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/SingleShotOutcomeMappingTest.kt
key-decisions:
  - "OFF and PROVIDER_DEFAULT are both the v1.0 wire in v1.1; encoders untouched and pinned by byte-parity tests"
  - "onFailed lives on the else arm only, so binding refusals, ceilings, gates, NoToolCall/Refusal and throws never reach it"
  - "detekt LongParameterList.constructorThreshold 8 -> 9 (the 8-argument ModelRequest primary); no baseline, no Suppress"
requirements-completed: [SEAM-01, SEAM-02]
status: complete
plan_head_before: cbffe778165b1b81f37846d29548bb25e89dbef4
commits: 3
metrics:
  completed: 2026-10-05
actuals:
  tokens: 10300
  tasks: 3
  commits: 3
---

# Phase 12 Plan 02: Reasoning knob and onFailed hook Summary

`ReasoningMode` (OFF, PROVIDER_DEFAULT) now rides on `ModelRequest.reasoning` and is set per tier with `Builder.reasoning` on SingleShot and AgenticLoop, and `SingleShotStrategy.Builder.onFailed` lets an app turn a provider failure (for example an HTTP 400) into Escalate without a decorator. No encoder and no api.txt file changed.

## What changed

- **ReasoningMode** is a `@JvmInline` value class with an internal constructor wrapping a lowercase String token (`off`, `provider_default`). KDoc says OFF means no engine-added reasoning request, never "the model does not think", and that the set is open.
- **ModelRequest** has an 8-argument primary (the seven v1.0 arguments plus `reasoning`). The 7-argument shape is now an explicit public secondary, and the 6-, 4- and 3-argument secondaries pass `ReasoningMode.OFF`. No default arguments. `toString` adds only `reasoning=<token>`.
- **Builders**: `reasoning` defaults to OFF on both strategies; AgenticLoop passes it through a new internal `AgenticRun` parameter into every turn.
- **onFailed** is a third `OutcomeHooks` member used by the `else` arm of `failureOutcome`. The default is `Failed(reason, details)`, identical to v1.0. A hook that throws goes through `TierWalk.executeGuarded` and ends as `strategy_error`.
- **detekt**: `LongParameterList.constructorThreshold` 9 with a one-line justification.

## JVM shape (research A2 confirmed)

`javap -p` on `ModelRequest.class` shows a private 8-argument constructor ending in `String`, the four public v1.0 constructors, and one synthetic public constructor ending in `String, DefaultConstructorMarker`. The 7-argument `(String, List, List, ToolChoice, int, CacheDirective, boolean)` constructor is a real public constructor (the only public one ending in `boolean`). `hasDefaultArgumentStub` flags neither `ModelRequest` nor `ReasoningMode`; `STUB_EXCEPTIONS` is unchanged.

## Verification

Full plan gate green: `:core:test :providers:test :core:detekt :providers:detekt :core:scanBannedConstructs :core:apiCheck`. `git diff v1.0.1 -- core/api.txt providers/api.txt` and the two encoder files are empty. `ReasoningWireParityTest` (3 tests) proves OFF, PROVIDER_DEFAULT and the 7-argument constructor encode to identical bytes: OpenAI gpt-5.4-mini equals `forced_log_food_strict`; OpenRouter gpt-5.4-mini, claude-sonnet-5.5 and gpt-6-astra equal `forced_log_food_strict`, `anthropic_reshaped` and `astra_forced`; Anthropic claude-haiku-4-5 and claude-sonnet-5-5 are identical across modes. `SingleShotOutcomeMappingTest` proves onFailed turns an HTTP 400 into Escalate, receives the exact reason and details objects, keeps the v1.0 default, and stays at zero calls for NoToolCall, Refusal (failure and answer), prose, MAX_TOKENS, a missing credential (next tier never runs), token ceiling reached and crossed, a gate hold and a throwing resolver; a throwing hook ends as `strategy_error`.

## Deviations from Plan

**1. [Rule 1 - Bug] ReasoningWireParityTest header line too long for detekt**
- Task 1 only ran `:core:detekt`; the over-long KDoc line in the new providers test surfaced in Task 2's `:providers:detekt` run and was fixed there.

**2. [Rule 3 - Blocking] Existing `toStringShowsTheFlagAndStillHidesTheSystemText` assertion**
- It asserted the string ended with `singleToolCall=true)`; updated to `singleToolCall=true, reasoning=off)` as the plan's `toString` change requires.

**3. Anthropic parity test placed in ReasoningWireParityTest**
- The plan lists parity cases for all providers in one file; the Anthropic case is a third test there (importing `AnthropicModels` and `encodeAnthropicRequest`, both visible inside the module's tests), which also satisfies the 3-or-more `@Test` criterion.

No behavioral or device verification was performed; none is in this plan.

## Self-Check: PASSED

Created files exist (ReasoningMode.kt, AgenticLoopReasoningTest.kt, ReasoningWireParityTest.kt); commits 5cbadb1, fbd61e3, e174937 exist on main.
