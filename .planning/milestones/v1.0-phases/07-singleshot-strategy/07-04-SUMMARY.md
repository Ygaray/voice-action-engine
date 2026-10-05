---
phase: 07-singleshot-strategy
plan: 04
subsystem: core-strategy
tags: [single-shot, strategy, outcome-mapping, terminal-call, first-call-only]
status: complete

requires:
  - phase: 07-singleshot-strategy
    provides: "ModelRequest.singleToolCall and TraceCode.EXTRA_TOOL_CALLS_DROPPED (07-01), ToolSpecProvider / OutcomeResolver / UserTurnRenderer seams (07-03), seam sign-off APPROVE"
provides:
  - "SingleShotStrategy: one forced single-call request, local resolution, every write through the gate as one merged proposal"
  - "SingleShotStrategy.Builder with tooling, resolver, userTurn, clock, forceTool, onNoToolCall, onRefusal"
  - "The closed default outcome mapping (internal) with per-tier onNoToolCall / onRefusal overrides"
  - "Terminal-tool routing: Completed(null, TerminalCall) without resolver or gate"
affects: [07-05, 07-06, 07-07]

plan_head_before: e1f243d1afabbfa87a1297b6f4698cd53641c54c
commits: 2

actuals:
  tokens: 15000
  tasks: 2
  commits: 2

tech-stack:
  added: []
  patterns:
    - "execute as a chain of small private stage functions, each at most two returns, so the next plan adds a link without tripping ReturnCount"
    - "Per-run Attempt holder (input, session, snapshot) instead of long parameter lists"
    - "Internal OutcomeHooks bundle so the mapping file stays free of strategy state"

key-files:
  created:
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/singleshot/SingleShotStrategy.kt
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/singleshot/SingleShotOutcomes.kt
    - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/SingleShotTestSupport.kt
    - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/SingleShotRequestTest.kt
    - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/SingleShotResolveTest.kt
    - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/SingleShotOutcomeMappingTest.kt
    - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/SingleShotTerminalTest.kt
  modified: []

key-decisions:
  - "Strategy constructor requireNotNull-checks tooling and resolver and the companion invoke calls it directly, so no separate Builder.build(id) exists; the IllegalArgumentException still names the missing field"
  - "Steps resolution handled by the strategy through a callback passed to the internal resolutionOutcome, keeping the closed mapping in one file while the write path stays in the strategy"

requirements-completed: [SHOT-01, SHOT-02]

coverage:
  - id: D1
    description: "Tracer (SHOT-01): one transcript becomes one forced single-call request (system and tool instances unchanged, Required tool, policy maxTokens, cached prefix, singleToolCall, one UserMessage rendered with the builder clock), the resolver gets Extraction(name, arguments) and the same CommandInput, Steps are submitted and the outcome is Completed with the Steps reply"
    requirement: SHOT-01
    verification:
      - kind: integration
        ref: "core SingleShotRequestTest (8 tests), SingleShotResolveTest#theResolverReceivesTheFirstCallAndTheSameInput, #theStepsReplyBecomesTheCompletedReplyWithNoTerminalCall"
        status: pass
    human_judgment: false
  - id: D2
    description: "Writes only through the gate: Finished steps submitted first in list order, all mutations merged into one Mutation step (gate.calls == 1, proposal holds all three in order), holding gate leaves applyCount 0 with one held proposal, admitting gate gives one COMMITTED action per mutation"
    requirement: SHOT-01
    verification:
      - kind: integration
        ref: "core SingleShotResolveTest (7 tests)"
        status: pass
    human_judgment: false
  - id: D3
    description: "Default outcome mapping: refused handle, NoToolCall / Refusal / other Failure, stop reasons decided before tool calls (REFUSAL, MAX_TOKENS, PAUSE_TURN, CONTEXT_WINDOW_EXCEEDED), no-call END_TURN / TOOL_USE / other, NoMatch / Escalate / Failed resolutions, unknown ModelResult and Resolution kinds, throwing resolver collapse to Unexpected with strategy_error"
    requirement: SHOT-02
    verification:
      - kind: unit
        ref: "core SingleShotOutcomeMappingTest (26 tests)"
        status: pass
    human_judgment: false
  - id: D4
    description: "Per-tier overrides: onNoToolCall / onRefusal replace one tier's defaults only, receive the response for a Success and null for a Failure"
    requirement: SHOT-02
    verification:
      - kind: unit
        ref: "core SingleShotOutcomeMappingTest#aNoToolCallHookReplacesTheDefaultAndGetsTheResponseOnASuccess, #aNoToolCallHookGetsNullWhenTheProviderReportedAFailure, #aRefusalHookReplacesTheDefaultAndTheNextTierRuns, #aRefusalHookGetsNullWhenTheProviderReportedAFailure, #oneTiersOverrideNeverChangesASecondSingleShotTier"
        status: pass
    human_judgment: false
  - id: D5
    description: "First call only and terminal routing: with N >= 2 calls the resolver sees the first, EXTRA_TOOL_CALLS_DROPPED is recorded once and the turn lists all N; a terminal tool (forced or not) ends the tier as Completed(null, TerminalCall) with asClarification readable and neither resolver nor gate touched; a tool missing from the snapshot escalates MalformedExtraction"
    requirement: SHOT-02
    verification:
      - kind: unit
        ref: "core SingleShotTerminalTest (5 tests); SingleShotOutcomeMappingTest#extraToolCallsAreDroppedAndRecordedOnceWhileTheTurnStillListsAllOfThem, #aSingleCallRecordsNoDroppedCallsCode, #aCallToAToolTheSnapshotNeverOfferedEscalatesWithMalformedExtraction"
        status: pass
    human_judgment: false
  - id: D6
    description: "Shape gates: no catch / runCatching / suppression / sealed / data class / enum / default argument in main; only SingleShotStrategy, its Builder and companion invoke are public; ApiShapeTest, detekt, scanBannedConstructs, verifyExplicitApiStrict and the API surface review stay green"
    requirement: SHOT-01
    verification:
      - kind: integration
        ref: "./gradlew check --offline (all modules, OkHttp legs) exit 0; scripts/review-api-surface.sh --expect-sealed-complete -> API SURFACE OK sealed=AssistantPart,CommandOutcome,GateDecision,Message,RunTermination,StrategyOutcome,ToolStep classes=177"
        status: pass
    human_judgment: false

duration: 35min
completed: 2026-10-01
---

# Phase 7 Plan 04: SingleShotStrategy Summary

**`SingleShotStrategy` is the engine's first real tier: one forced single-call extraction, local resolution through the app's resolver, every write through the gate as one merged proposal, a closed default outcome mapping with two per-tier overrides, first-call-only and terminal-tool routing.**

## Accomplishments

- Tracer: a pipeline whose only tier is `SingleShotStrategy(StrategyId("single_shot")) { tooling = ...; resolver = ... }` sends exactly one provider call. The request carries the snapshot's system text and tool instances unchanged, `ToolChoice.Required(singleShotTool)` (or `Auto` with `forceTool = false`), `maxTokens = session.policy.maxTokensPerTurn`, `CacheDirective(true)`, `singleToolCall = true` and one `UserMessage` rendered by the renderer from the builder clock. The resolver receives `Extraction(name, arguments)` and the same `CommandInput`.
- Write path (D-01/D-02/D-05, RESEARCH A3 as signed off): the strategy only submits steps. Finished steps go first in list order; every `ToolStep.Mutation`'s mutations, in order, go out as ONE `ToolStep.Mutation`, so `gate.calls == 1` and the proposal holds all of them. A holding gate leaves every `applyCount` at 0 with one held proposal. The turn is not reported a second time (the routed model already records it).
- Outcome mapping in `SingleShotOutcomes.kt` (all `internal`): stop reason decided before any tool call is read, so a refused or truncated answer is never resolved into a write. NoToolCall and Refusal failures and stops route to the tier's hooks; every other failure passes `reason` and `details` through; no-call answers map by stop reason (END_TURN to the hook, TOOL_USE to MalformedResponse, others to UnknownStop); `Resolution.NoMatch/Escalate/Failed` map field for field; unknown `ModelResult` and `Resolution` kinds give `Failed(Other("unknown_model_result" | "unknown_resolution"))`.
- First call only: with two or more calls `EXTRA_TOOL_CALLS_DROPPED` is recorded once while the turn still lists all names. A tool missing from the snapshot escalates `MalformedExtraction`; a terminal tool ends the tier as `Completed(null, TerminalCall(...))` with neither resolver nor gate involved.
- No exception handler anywhere: a throwing resolver propagates to TierWalk's collapse and surfaces as `Failed(Unexpected)` with trace code `strategy_error` (tested).

## Sign-off and substitutions

- Seam sign-off precondition: `evidence/seam-signoff.txt` ends `SIGNOFF: APPROVE`.
- Substitutions applied from the sign-off: none. The Phase 9 carry (the agentic loop must accept the same `userTurn: UserTurnRenderer`) needs nothing here.

## Final builder knob list

`tooling: ToolSpecProvider?` (required), `resolver: OutcomeResolver?` (required), `userTurn: UserTurnRenderer` (default `UserTurnRenderer.standard()`), `clock: java.time.Clock` (default `Clock.systemDefaultZone()`), `forceTool: Boolean` (default `true`), `onNoToolCall: suspend (ModelResponse?) -> StrategyOutcome` (default `Escalate(NoToolCall)`), `onRefusal: suspend (ModelResponse?) -> StrategyOutcome` (default `Failed(Refusal)`). Failure codes added as `FailureReason.Other` values: `single_shot_tool_missing`, `unknown_model_result`, `unknown_resolution`.

## Task Commits

1. Task 1 (tracer): `59c3aad` feat(07-04): add SingleShotStrategy tracer with forced single-call request and gated resolution
2. Task 2: `d3c1e79` feat(07-04): map every provider result and resolution to a typed outcome with per-tier hooks
3. This summary: committed last.

plan_head_before: `e1f243d1afabbfa87a1297b6f4698cd53641c54c`; measured commit count 2 before this summary (`git rev-list --count e1f243d..HEAD`), 3 including it.

## Notes for 07-05

`execute` is a chain of private stage functions (`withModel`, `ask`, `answer`, `route`, `resolve`, `submitAll`), each with at most two returns. The pre-call ceiling check belongs between `withModel`'s refusal check and `ask` (an elvis link); the post-call check between `model.complete` and `decideResult` in `answer`.

## Verification

- Tracer gate re-run end to end after Task 1: `:core:test --tests '*SingleShotRequestTest' --tests '*SingleShotResolveTest' --tests '*ApiShapeTest'` green; `:core:detekt :core:scanBannedConstructs :core:verifyExplicitApiStrict` exit 0. Tracer verified, expansion proceeded.
- Task 2 was test-first: the behavior tests were written and run RED (24 of 31 failed against the interim mapping), then the mapping was implemented and all went green.
- Test counts: SingleShotRequestTest 8, SingleShotResolveTest 7, SingleShotOutcomeMappingTest 26, SingleShotTerminalTest 5, all passing.
- `./gradlew :core:check --offline -q` exit 0; `./gradlew check --offline` BUILD SUCCESSFUL across all modules including the OkHttp matrix legs.
- `scripts/review-api-surface.sh --expect-sealed-complete` prints `API SURFACE OK ... classes=177`.
- Acceptance greps: `StopReason.REFUSAL` in the outcomes file, `TraceCode.EXTRA_TOOL_CALLS_DROPPED` and `TerminalCall(` in the strategy, zero `catch`/`runCatching` in both main files, zero `public ` in the outcomes file, zero `recordTurn` / `.apply()` in the strategy.
- `git log --oneline e1f243d..HEAD -- providers/` prints nothing. No `api.txt`, no tag, no change to the contract, the ledger, STATE.md or ROADMAP.md; 07-VALIDATION.md untouched.

## Deviations from Plan

**1. [Minor shape] No separate `Builder.build(id)`**
- The plan sketched `internal fun build(id)` on the builder holding the `requireNotNull` checks. They sit in the strategy's constructor property initializers instead and the companion `invoke` calls the constructor directly. Behavior is the same: `IllegalArgumentException` with "SingleShotStrategy: tooling is required" / "...resolver is required"; tested. No public surface differs.

**2. [Test-only] helper `runShot` instead of `run`**
- A test helper named `run` collided with the stdlib `run { }` at a trailing-lambda call site; renamed. No effect on main source.

Otherwise none; Rules 1 to 4 were not triggered.

## Deferred Issues

None.

## Self-Check: PASSED

- Created files exist: the two main files and five test files listed in key-files.
- Commits `59c3aad` and `d3c1e79` exist on `worktree-agent-af81ca63fd2b54a30`.
