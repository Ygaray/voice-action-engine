---
phase: 07-singleshot-strategy
plan: 02
subsystem: providers-wire
tags: [anthropic, chat-completions, tool-choice, parallel-tool-calls, o-series]
status: complete

requires:
  - phase: 07-singleshot-strategy
    provides: "ModelRequest.singleToolCall neutral flag (07-01)"
provides:
  - "Anthropic tool_choice.disable_parallel_tool_use:true for flagged requests, forced and auto/reshape shapes"
  - "Chat Completions parallel_tool_calls:false for flagged OpenAI requests (also automatic choice with a non-strict tool)"
  - "ChatWireRules.acceptsParallelToolCalls (internal): false for the o-series pattern"
affects: [07-03, 07-04]

plan_head_before: 35e1f34b872ba1348ce36815c10393c7133bc88a
commits: 2

actuals:
  tokens: 5300
  tasks: 2
  commits: 2

tech-stack:
  added: []
  patterns:
    - "Flag-conditional wire key written inside an existing object (tool_choice) so the cached prefix bytes are untouched"
    - "Per-family wire switch carried on the internal ChatWireRules, gated together with the vendor flag"

key-files:
  created: []
  modified:
    - providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/anthropic/AnthropicEncoder.kt
    - providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ChatEncoder.kt
    - providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/chat/OpenAiModelRules.kt
    - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/anthropic/AnthropicEncoderTest.kt
    - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/anthropic/AnthropicForcedToolTest.kt
    - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ChatEncoderTest.kt
    - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/chat/OpenAiModelRulesTest.kt

key-decisions:
  - "OpenRouter stays without parallel_tool_calls (ChatVendor untouched); the strategy's first-call-only rule (07-04) bounds the answer there"
  - "o-series ids drop parallel_tool_calls even when forced or strict, fixing a latent 400 in shipped code"

requirements-completed: [SHOT-02]

coverage:
  - id: D1
    description: "A flagged required-tool request reaches Anthropic with disable_parallel_tool_use inside tool_choice, in both the forced and the reshaped shape, in one HTTP request each"
    requirement: SHOT-02
    verification:
      - kind: integration
        ref: "providers AnthropicForcedToolTest#aSingleToolCallForcedRequestCarriesTheParallelOffSwitchInsideToolChoice, #aSingleToolCallReshapedRequestKeepsTheSwitchAndTheInstructionLine"
        status: pass
      - kind: unit
        ref: "providers AnthropicEncoderTest#aSingleToolCallPutsTheParallelOffSwitchInsideToolChoiceInEveryShape, #withoutTheFlagNoToolChoiceByteChanges, #aSingleToolCallRequestWithoutToolsSendsNoToolChoiceAndNoSwitch, #theSingleToolCallFlagKeepsTheKeyOrderAndTheCachedPrefixUntouched"
        status: pass
    human_judgment: false
  - id: D2
    description: "OpenAI carries parallel_tool_calls:false when forced, strict or flagged; OpenRouter and o-series never carry it; goldens byte-identical"
    requirement: SHOT-02
    verification:
      - kind: unit
        ref: "providers ChatEncoderTest#aSingleToolCallRequestTurnsTheSwitchOffEvenWithAutomaticChoiceAndANonStrictTool, #theOSeriesNeverGetsTheParallelSwitch, #openRouterNeverCarriesTheParallelSwitchAndKeepsRequireParametersWhenForced, #aSingleToolCallRequestWithoutToolsCarriesNoParallelSwitch, #theKeyOrderHoldsWhenTheParallelSwitchIsPresent"
        status: pass
      - kind: unit
        ref: "providers OpenAiModelRulesTest#theOSeriesRejectsTheParallelSwitchAndEveryOtherFamilyTakesIt, #theWireRulesPrintTheirFourFields"
        status: pass
      - kind: integration
        ref: "./gradlew :providers:check --offline (golden replay, 4.12.0 / 5.2.1 / 5.5.0 legs)"
        status: pass
    human_judgment: false

duration: 20min
completed: 2026-10-01
---

# Phase 7 Plan 02: Single-tool-call wire encoding Summary

**The neutral `singleToolCall` flag now reaches the wire: Anthropic gets `disable_parallel_tool_use: true` inside `tool_choice` (forced and reshaped), OpenAI gets `parallel_tool_calls: false`, OpenRouter and the o-series never get the switch.**

Phase 5 follow-up: o-series parallel_tool_calls gate (OpenAiModelRules.acceptsParallelToolCalls) delivered in 07-02. This edits shipped Phase 5 code (`OpenAiModelRules`, `ChatEncoder`); the orchestrator should note it. Confidence is MEDIUM (o3-mini is community-sourced, o1 and o4-mini are assumed).

## Accomplishments

- Anthropic: `encodeToolChoice` writes `disable_parallel_tool_use: true` after `type` (and `name`) when `call.request.singleToolCall`, for both the named shape and the `auto` shape used on forced-unsupported models. Nothing is written when the flag is false, so unflagged bytes are identical. Top-level key order and the `tools` and `system` JSON are unchanged, so the cached prefix is untouched. Tracer proof through a real pipeline and `AnthropicProvider` against MockWebServer: one HTTP request each, exactly `{"type":"tool","name":"add_item","disable_parallel_tool_use":true}` (override) and `{"type":"auto","disable_parallel_tool_use":true}` plus the instruction line (reshape).
- Chat Completions: `ChatWireRules` gained `acceptsParallelToolCalls` (4th constructor parameter, printed last in `toString`), false for ids matching `^o\d.*$` (viaRouter or not), true otherwise and for `routedDefaultRules()`. The encoder sends `parallel_tool_calls:false` when `vendor.parallelToolCallsFalseOnForced && rules.acceptsParallelToolCalls && (required || strict || singleToolCall)`, inside the tools block at the unchanged position after `tool_choice`.
- `ChatVendor.kt` is unchanged (OPENROUTER false, OPENAI true). No golden file changed. No `:core` main source or public API changed (`ChatWireRules` stays internal).

## Task Commits

1. Task 1 (tracer): `bab2f9a` feat(07-02): send disable_parallel_tool_use inside Anthropic tool_choice for single-call requests
2. Task 2: `6a2a32f` feat(07-02): send parallel_tool_calls false for single-call OpenAI requests, never for OpenRouter or o-series

plan_head_before: `35e1f34b872ba1348ce36815c10393c7133bc88a`; measured commit count 2 (`git rev-list --count 35e1f34..HEAD`).

## Verification

- `./gradlew :providers:check --offline -q` exit 0 on all three OkHttp legs. Per-leg totals from the JUnit XML: `test` (4.12.0) 424 tests, 0 failures; `testOkhttp521` 424 tests, 0 failures; `testOkhttp550` 424 tests, 0 failures. detekt and scanBannedConstructs clean.
- `git status --porcelain -- providers/src/test/resources/golden` empty; `git diff 35e1f34 -- ChatVendor.kt providers/src/test/resources/golden core/src/main` empty.

## Deviations from Plan

None. Rules 1-4 were not triggered. One detekt MaxLineLength finding in a new test line was fixed inline before the Task 1 commit.

## Deferred Issues

None. The live Anthropic cache-read check for the new `tool_choice` field is carried to Phase 10 (VER-03), per the plan's accepted threat T-07-08.

## Self-Check: PASSED

- All seven modified files exist and appear in `git diff --name-only 35e1f34..HEAD`.
- Commits `bab2f9a` and `6a2a32f` exist on `worktree-agent-afb077e2cd3c11f7a`.
