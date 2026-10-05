---
phase: 07-singleshot-strategy
plan: 01
subsystem: core-contract
tags: [model-request, trace-code, command-session, fake-provider, single-tool-call]
status: complete

requires:
  - phase: 03-transcript-types-providerrouter-on-device-gate
    provides: ModelRequest, BoundModel routing, ProviderRequest seam
  - phase: 02-core-contract-pipeline-commit-seam
    provides: RunRecorder, TraceCode, PipelineEvent.EngineCode
provides:
  - "ModelRequest.singleToolCall neutral flag (seven-argument primary constructor)"
  - "TraceCode.EXTRA_TOOL_CALLS_DROPPED (extra_tool_calls_dropped)"
  - "internal CommandSession.recordCode(code), implemented by RunSession"
  - "FakeAiProvider.refusal(usage) and FakeAiProvider.toolCalls(usage, vararg calls)"
affects: [07-02, 07-03, 07-04, 07-05, 07-06]

plan_head_before: 1b5b6cd548006c0afd0d75eb36bb6bba9696c173
commits: 3

actuals:
  tokens: 3500
  tasks: 3
  commits: 3

tech-stack:
  added: []
  patterns:
    - "Growing a public class by promoting the largest constructor to primary and keeping every older shape as a secondary constructor (no default arguments)"
    - "Engine-internal session hook (internal abstract on CommandSession) so strategies record codes without a public API addition"

key-files:
  created:
    - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/SingleShotPlumbingTest.kt
  modified:
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/transcript/ModelRequest.kt
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/telemetry/TraceCode.kt
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/CommandSession.kt
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/pipeline/RunSession.kt
    - core/src/testFixtures/kotlin/io/github/ygaray/voiceactionengine/core/testing/FakeAiProvider.kt
    - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/TranscriptTypesTest.kt
    - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/FakeAiProviderTest.kt

key-decisions:
  - "Flag name singleToolCall and its position (seventh, after cache) are the public shape to confirm in the 07-03 seam sign-off; it is frozen at the v1.0.0 API dump"
  - "recordCode stays internal on CommandSession; it accepts only a TraceCode so no app text can enter the trace"

requirements-completed: [SHOT-02]

coverage:
  - id: D1
    description: "A request built with singleToolCall = true travels from a pipeline tier through session.model().complete to the provider unchanged; the six-argument form arrives false"
    requirement: SHOT-02
    verification:
      - kind: integration
        ref: "core SingleShotPlumbingTest#aSingleToolCallRequestReachesTheProviderWithTheFlagSet and #aSixArgumentRequestReachesTheProviderWithTheFlagClear"
        status: pass
    human_judgment: false
  - id: D2
    description: "ModelRequest keeps its 3-, 4- and 6-argument public constructors and gains the 7-argument one; toString prints the flag and no content"
    requirement: SHOT-02
    verification:
      - kind: unit
        ref: "core TranscriptTypesTest#modelRequestKeepsItsPublicConstructorShapes, #toStringShowsTheFlagAndStillHidesTheSystemText"
        status: pass
      - kind: unit
        ref: "./gradlew :providers:compileTestKotlin --offline (existing callers compile)"
        status: pass
    human_judgment: false
  - id: D3
    description: "A strategy can record extra_tool_calls_dropped into the run trace and the listener receives one EngineCode event with the run id"
    requirement: SHOT-02
    verification:
      - kind: integration
        ref: "core SingleShotPlumbingTest#aRecordedCodeLandsInTheTraceExactlyOnce, #aRecordedCodeReachesTheListenerOnceWithTheRunId, #twoRecordedCodesAppearTwiceInCallOrder"
        status: pass
    human_judgment: false
  - id: D4
    description: "FakeAiProvider can script a refusal and a multi-call answer"
    requirement: SHOT-02
    verification:
      - kind: unit
        ref: "core FakeAiProviderTest#refusalBuilderYieldsASuccessWithRefusalStopReasonAndNoParts, #toolCallsBuilderYieldsAToolUseTurnWithTheCallsInOrder, #toolCallsBuilderRejectsAnEmptyCallList, #aFakeScriptedWithToolCallsReturnsThatResult"
        status: pass
    human_judgment: false

duration: 25min
completed: 2026-10-01
---

# Phase 7 Plan 01: Single-tool-call plumbing Summary

**Neutral `singleToolCall` flag on `ModelRequest`, an `extra_tool_calls_dropped` trace code with an internal `recordCode` session hook, and `refusal`/`toolCalls` scripted-result helpers on `FakeAiProvider`, each proven through a real routed pipeline.**

## Accomplishments

- `ModelRequest` now has a seven-argument primary constructor `(system, messages, tools, toolChoice, maxTokens, cache, singleToolCall)`. The 3-, 4- and 6-argument public constructors are unchanged, with the same parameter names and order, and delegate with `false`. No default arguments. `toString` ends with `singleToolCall=<value>` and still prints no prompt or message content.
- Tracer proof: a tier built a request with the flag set, sent it through `session.model().complete`, and the fake provider recorded it with `singleToolCall` true and `maxTokens == TierPolicy.DEFAULT.maxTokensPerTurn`. The six-argument form arrives false.
- `TraceCode.EXTRA_TOOL_CALLS_DROPPED` (wire value `extra_tool_calls_dropped`). `CommandSession.recordCode(code)` is `internal abstract suspend`, implemented in `RunSession` by forwarding to `scope.recorder.recordCode`, so the code lands in `outcome.trace.codes` and one `PipelineEvent.EngineCode` reaches the listener with the run id. No public member was added.
- `FakeAiProvider.refusal(usage)` (success, `StopReason.REFUSAL`, no parts) and `FakeAiProvider.toolCalls(usage, vararg calls)` (success, `StopReason.TOOL_USE`, calls in order, empty list rejected).

## Final ModelRequest constructor list

1. `ModelRequest(system: String, messages: List<Message>, maxTokens: Int)` (secondary, unchanged)
2. `ModelRequest(system: String, messages: List<Message>, tools: List<ToolSpec>, maxTokens: Int)` (secondary, unchanged)
3. `ModelRequest(system, messages, tools, toolChoice: ToolChoice, maxTokens: Int, cache: CacheDirective)` (secondary, unchanged shape; delegates with `false`)
4. `ModelRequest(system, messages, tools, toolChoice, maxTokens, cache, singleToolCall: Boolean)` (primary, new)

## Task Commits

1. Task 1 (tracer): `bc4c0ce` feat(07-01): add single-tool-call flag to ModelRequest
2. Task 2: `2d4a9f9` feat(07-01): add extra_tool_calls_dropped trace code and internal recordCode hook
3. Task 3: `c17644f` feat(07-01): add refusal and toolCalls helpers to FakeAiProvider

plan_head_before: `1b5b6cd548006c0afd0d75eb36bb6bba9696c173`; measured commit count 3 (`git rev-list --count 1b5b6cd..HEAD`).

## Verification

- `./gradlew :core:check --offline -q` exit 0 (ApiShapeTest, NoHardCodedConstantsTest, detekt, scanners).
- `./gradlew :providers:compileTestKotlin --offline -q` exit 0 (existing `ModelRequest` callers compile unchanged).
- `./gradlew check --offline -q` exit 0 across all modules, including the OkHttp matrix legs.
- No file under `providers/` was touched; no `api.txt`, no tag, no change to the contract or ledger, STATE.md or ROADMAP.md.

## Deviations from Plan

None. Rules 1-4 were not triggered. In Task 3 the fixture helpers and their tests were written in the same edit rather than with a separate failing-test run first; the tests were then run green and the full gate passed.

## Deferred Issues

None.

## Self-Check: PASSED

- Created and modified files all exist and appear in `git diff --name-only 1b5b6cd..HEAD`.
- Commits `bc4c0ce`, `2d4a9f9`, `c17644f` exist on `worktree-agent-a6b4682b37d9881ea`.
