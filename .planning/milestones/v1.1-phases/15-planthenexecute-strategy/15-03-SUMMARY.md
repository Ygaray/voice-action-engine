---
phase: 15-planthenexecute-strategy
plan: 03
subsystem: core/strategy/plan, providers (tests, build)
status: complete
tags: [planthenexecute, replan, prefix-cache, wire-test, live-probe, d-04, d-08]

requires:
  - phase: 15-02
    provides: whole-plan validation verdicts, PLAN_REPLANNED declared, PlanRun.worked, outcomeOf
provides:
  - PlanReplan.kt - replanRequest (prefix-identical continuation) and rejectionDigest (fixed vocabulary)
  - PlanFlow in the strategy file - REPLAN_LIMIT = 1, the single canReplan predicate, second-answer path
  - PlanThenExecuteReplanTest (10 cases) and PlanThenExecuteWireTest (4 tests over 3 wires)
  - PlanBindingLiveProbeTest plus the livePlanProbe task (opt-in, at most 8 requests, outside check)
  - 15-LIVE-PROBE.md with decision pending, for the orchestrator relay
affects: [15-04, 15-05, 15-06, 15-07]

plan_head_before: 70af9d9070b4bf09c2a7e79bdf36cabbe6d46404
actuals:
  tokens: 21000
  tasks: 3
  commits: 3

tech-stack:
  added: []
  patterns:
    - "The per-command conversation lives in a private PlanFlow class (first request, one replan counter), keeping the strategy class under the detekt function limit"
    - "The replan reuses the first request's UserMessage instance, tool list instances, tool choice and settings; only the two messages after the user turn are new"
    - "Live-probe class names contain Live so test and both OkHttp legs exclude it; the task is onlyIf VAE_LIVE_PLAN == 1"

key-files:
  created:
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/plan/PlanReplan.kt
    - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/PlanThenExecuteReplanTest.kt
    - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/PlanThenExecuteWireTest.kt
    - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/PlanBindingLiveProbeTest.kt
    - .planning/phases/15-planthenexecute-strategy/15-LIVE-PROBE.md
  modified:
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/plan/PlanThenExecuteStrategy.kt
    - providers/build.gradle.kts

key-decisions:
  - "Replan predicate frozen as: replans used < REPLAN_LIMIT (1) AND nothing applied or held (PlanRun.worked) AND ceilingReached(session) == null"
  - "A replan request that cannot be built (no call, blank or repeated call id) ends Escalate(MalformedExtraction) with the carry, and plan_replanned is recorded only when the request was built"
  - "The digest is {status:plan_rejected, reason:<code|step_failed>, step_index:<n|null>}; every other call of the answer gets {status:error, reason:ignored_call}"

requirements-completed: [PLAN-03, PLAN-02]

coverage:
  - id: D1
    description: "SC3 / PLAN-03: a rejected plan or a first-step failure before anything was applied or held gets exactly one replan; a second rejection ends Escalate(MalformedExtraction) with the carry, a second pre-commit failure ends Escalate(Other(plan_step_failed)); never a third call"
    requirement: "PLAN-03"
    verification:
      - kind: unit
        ref: "PlanThenExecuteReplanTest (aRejectedPlanIsReplannedOnce..., aSecondRejection..., aFirstStepThatFails..., theSamePreCommitFailureTwice...)"
        status: pass
    human_judgment: false
  - id: D2
    description: "A failure after any commit never replans (one provider call, suppressed partial); no replan once the token ceiling is reached; a replan answer that needs a lookup escalates with nothing run"
    requirement: "PLAN-03"
    verification:
      - kind: unit
        ref: "PlanThenExecuteReplanTest (aFailureAfterACommit..., noReplanWhenTheTokenCeiling..., aReplanAnswerThatNeedsALookup...)"
        status: pass
    human_judgment: false
  - id: D3
    description: "D-08: the replan continues the same conversation with identical system, same ToolSpec instances, toolChoice and settings; messages[0] is the same UserMessage instance, messages[1] the first answer unchanged, messages[2] answers every call id in order with the fixed digest"
    requirement: "PLAN-02"
    verification:
      - kind: unit
        ref: "PlanThenExecuteReplanTest#theReplanContinuesTheConversationWithAnIdenticalPrefixAndTheFixedDigest, everyOtherCallOfTheRejectedAnswerIsAnsweredAsIgnoredInOrder, aDirectAppToolCallIsRejectedMalformedAndNeverPrepared"
        status: pass
    human_judgment: false
  - id: D4
    description: "D-08 byte test: system+tools and tool_choice bytes identical across the plan and replan requests on Anthropic, OpenAI and OpenRouter (forced) and on Anthropic reshape mode; request 2 answers the plan call id; submit_plan never strict"
    requirement: "PLAN-02"
    verification:
      - kind: unit
        ref: "PlanThenExecuteWireTest (4 tests)"
        status: pass
    human_judgment: false
  - id: D5
    description: "D-04: opt-in live probe harness exists (livePlanProbe, VAE_LIVE_PLAN=1, at most 8 requests, outside check); task skipped without opt-in; 15-LIVE-PROBE.md holds decision: pending. No live request was made."
    requirement: "PLAN-02"
    verification:
      - kind: command
        ref: "gradlew :providers:compileTestKotlin :providers:livePlanProbe (SKIPPED) and :providers:check --dry-run (probe absent, :providers:test present)"
        status: pass
    human_judgment: false
---

# Phase 15 Plan 03: The one replan, the wire proof and the live-probe harness Summary

A plan the engine rejects before anything runs, or a first step that fails before anything was applied or held, now gets exactly one more model call in the same conversation with a byte-identical cached prefix; the prefix identity is proven on all three real wires, and the opt-in D-04 live probe is built and waiting, unrun, behind a pending request.

**Duration:** about 35 min (2026-10-06). **Tasks:** 3. **Files:** 7 (5 created, 2 modified). **Commits:** 3.

## What was built

- `PlanReplan.kt`: `replanRequest(first, answer, digest)` copies system, tools (same instances), tool choice, max tokens, cache, single-call and reasoning from the first request, reuses `first.messages.first()` (same instance, never re-rendered), and appends the first answer's `AssistantMessage` plus a `ToolResultsMessage` answering every call id in order (digest for the first call, the fixed `ignored_call` notice for the rest). It returns null instead of throwing for an answer with no call or a blank or repeated call id. `rejectionDigest(reason, stepIndex)` is compact JSON with keys in the fixed order `status`, `reason`, `step_index`.
- Strategy: the per-command flow moved into a private `PlanFlow` (first request, replan counter). `REPLAN_LIMIT = 1`; `canReplan(worked)` is the single predicate. A `Rejected` verdict or a `RunStop.StepFailed` goes to `replan` when it holds; the second answer goes through the same decide / ceiling / parse path with a fresh `PlanRun`, so the binding map starts empty (whole-plan replacement). When it does not hold, a rejection ends `Escalate(MalformedExtraction)` and a step failure ends `Escalate(Other("plan_step_failed"))`, both with the incoming carry; TierWalk suppresses the latter only when something applied or was held.
- Tests: `PlanThenExecuteReplanTest` covers every behavior bullet plus the ceiling case (10 tests). `PlanThenExecuteWireTest` drives the real tier through `AnthropicProvider`, `ChatCompletionsProvider.openAi` and `.openRouter` against a MockWebServer.
- Probe: `PlanBindingLiveProbeTest` (S1 whole-value reference, S2 dictated `$5.00` and `$3.50`, on claude-haiku-4-5 and gpt-5.4-mini), the `livePlanProbe` task, and `15-LIVE-PROBE.md`.

## Task Commits

| Task | Name | Commit |
|---|---|---|
| 1 | One prefix-identical replan, fixed digest, single predicate | 2146c09 |
| 2 | Wire proof of identical cached prefix and tool_choice | b49377e |
| 3 | livePlanProbe harness and pending request | 8ea1a0d |

## Deviations from Plan

None needing a rule beyond one note:

**Wire finding (not a deviation):** on the OpenAI and OpenRouter wires the chat encoder has no error flag, so an `isError` tool result is sent as `{"error":"<content as a JSON string>"}`; the digest bytes are therefore wrapped there, while the Anthropic wire carries the exact digest with `is_error: true`. The plan asked for exact digest bytes only on Anthropic and only a `tool`-role message with the call id for chat, so the chat assertion pins the wrapped form. Consequence for plan 15-06's surface review: the digest vocabulary the model reads on chat wires is nested inside an `error` string; nothing in the engine needs to change, but the replan wording in the `submit_plan` description should not assume the model sees bare JSON on those wires.

**Test-only name clash (own fix):** a private `Rig` class in the new core test clashed with `Rig` in `PlanThenExecuteLookupTest` (same package); renamed to `ReplanRig`.

## Verification

- Task 1 gate: `:core:test --tests '*PlanThenExecute*' '*PlanParseTest*' '*PlanBindingTest*' '*NoHardCodedConstantsTest*'` exit 0 (ReplanTest 10/10).
- Task 2 gate: `:providers:test --tests '*PlanThenExecuteWireTest*' '*AgenticLoopWireTest*'` exit 0 (WireTest 4/4).
- Task 3 gate: `:providers:compileTestKotlin :providers:livePlanProbe` exit 0 with `VAE_LIVE_PLAN` unset (task SKIPPED); `:providers:check --dry-run` lists `:providers:test` and not the probe; `grep -qx 'decision: pending'` passes.
- Wave-end gate (one Gradle invocation, host-safe recipe): `:core:test :core:detekt :core:scanBannedConstructs :providers:test :providers:detekt` exit 0; `:core:metalavaCheckCompatibility` exit 0 (public API untouched).
- All task acceptance greps re-run: PASS.
- Threat mitigations: T-15-13 (one predicate, post-commit test asserts one call), T-15-14 (fixed-vocabulary digest, byte-pinned on core and wire), T-15-15 (REPLAN_LIMIT, never a third call asserted, ceiling test), T-15-16 (every call id answered in order; unbuildable continuation escalates), T-15-17 and T-15-18 (probe skipped by default, outside check, counts-only output, ceiling 8; no key read, no live request made in this plan).

## Notes for later plans

- RT-01 (hold semantics, `remainingStepIds`) remains plan 15-04's: the replan path only reads `PlanRun.worked`, which already covers applied and held, so a Held stop never replans. Plans 15-04/05/06 extend `outcomeOf`; `PlanFlow.runPlan` calls it after the one replan check.
- `PlanFlow` is private to `PlanThenExecuteStrategy.kt`; API.md has not yet been updated for the replan (plan 15-06 owns the surface review).

## Driver action

**Relay `15-LIVE-PROBE.md` to the orchestrator now.** It is a pending, bounded request (4 expected / 8 ceiling requests, ceiling USD 0.05, host only, keys only through `with-test-keys --only anthropic,openai`). Plan 15-07 needs the answer (approved or deferred) recorded verbatim before it may run `livePlanProbe`.

## Issues Encountered

None.

## Self-Check: PASSED

Files verified present: `PlanReplan.kt`, `PlanThenExecuteReplanTest.kt`, `PlanThenExecuteWireTest.kt`, `PlanBindingLiveProbeTest.kt`, `15-LIVE-PROBE.md`, and the two modified files. Commits 2146c09, b49377e, 8ea1a0d present in `git log`. `commits: 3` measured from `rev-list --count 70af9d9..HEAD`.
