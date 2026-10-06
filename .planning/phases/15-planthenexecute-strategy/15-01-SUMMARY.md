---
phase: 15-planthenexecute-strategy
plan: 01
subsystem: core/strategy/plan
status: complete
tags: [planthenexecute, submit-plan, tracer, prepare-guarded, builder-guards, api-md]

requires:
  - phase: 14
    provides: StepSubmission.kt shared submit path, internal CommandSession.submit(step, providerCallId), OutcomeHooks/decideResult in strategy.singleshot
provides:
  - PlanThenExecuteStrategy (public) with Builder (tooling, executor, capabilities, userTurn, clock, reasoning, maxSteps, onFailed) and companion invoke
  - Engine-owned submit_plan ToolSpec (byte-stable schema, frozen description)
  - PlanRun - strictly sequential per-step submit loop, one gate proposal per step, planning call id on every action
  - prepareGuarded - shared guarded executor prepare for the agentic and plan tiers
  - Plan test support fixtures reused by every later plan test
affects: [15-02, 15-03, 15-04, 15-05, 15-06, 15-07]

plan_head_before: c7e0d0d4ad76782e3b4658a6d7ccdfe01f39b9ec
actuals:
  tokens: 12700
  tasks: 3
  commits: 3

tech-stack:
  added: []
  patterns:
    - "Plan tier imports OutcomeHooks/decideResult in place from strategy.singleshot (internal is module-wide); no singleshot file changed"
    - "Every plan step goes through session.submit(prepared, callId) alone; never through the combined helper in StepSubmission.kt"
    - "Every stop after a step ran is an Escalate, never a Failed, so TierWalk suppresses it once anything applied or held"

key-files:
  created:
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/PreparedStep.kt
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/plan/PlanThenExecuteStrategy.kt
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/plan/PlanSchema.kt
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/plan/PlanParse.kt
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/plan/PlanRun.kt
    - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/PlanTestSupport.kt
    - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/PlanThenExecuteRunTest.kt
    - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/PlanSchemaTest.kt
  modified:
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/agentic/AgenticDispatch.kt
    - API.md

key-decisions:
  - "Tracer scope: a Held stop maps to Escalate(Other(plan_step_held)) for every step including the last; the last-step Completed(null) carve-out (resolved open question 1) is left to plan 15-04"
  - "Rejected plans escalate MalformedExtraction carrying session.carry; the replan insertion point is plan 15-03"
  - "Guard failures use FailureReason.Other with fixed codes plan_tool_name_taken and plan_no_tools, returned before binding the model"

requirements-completed: [PLAN-01, PLAN-05]

coverage:
  - id: D1
    description: "Tracer: one forced submit_plan call returns a two-step plan; both steps run in plan order, each its own executor -> gate -> apply -> sink proposal, outcome Completed(null, partial false), one provider call"
    requirement: "PLAN-01"
    verification:
      - kind: unit
        ref: "PlanThenExecuteRunTest#oneForcedPlanCallRunsBothStepsInOrderEachAsItsOwnProposal"
        status: pass
    human_judgment: false
  - id: D2
    description: "Every recorded action carries the submit_plan call id at positions 0 and 1; every Extraction carries callId equal to the planning call id"
    requirement: "PLAN-01"
    verification:
      - kind: unit
        ref: "PlanThenExecuteRunTest#oneForcedPlanCallRunsBothStepsInOrderEachAsItsOwnProposal"
        status: pass
    human_judgment: false
  - id: D3
    description: "submit_plan schema and description pinned byte for byte; request shape (tools, forced choice, maxTokens, cache, singleToolCall, reasoning, one user message) pinned"
    verification:
      - kind: unit
        ref: "PlanSchemaTest"
        status: pass
    human_judgment: false
  - id: D4
    description: "Builder guards: maxSteps below 1, missing tooling or executor throw IllegalArgumentException naming the setting; snapshot offering submit_plan or no non-terminal tool fails before any provider call"
    requirement: "PLAN-05"
    verification:
      - kind: unit
        ref: "PlanSchemaTest#missingSettingsAndAZeroStepLimitAreRejectedNamingTheSetting"
        status: pass
    human_judgment: false
  - id: D5
    description: "AgenticDispatch prepare delegates to shared prepareGuarded in its own commit; fault bytes exist once in main sources; agentic, seam and redaction suites green"
    verification:
      - kind: unit
        ref: "./gradlew :core:test --tests '*AgenticLoop*' --tests '*ToolExecutorSeamTest*' --tests '*RedactionCanaryTest*'"
        status: pass
    human_judgment: false
  - id: D6
    description: "API.md names PlanThenExecuteStrategy in the package table, type table, a Strategies bullet and the ToolExecutor extension-point row"
    verification:
      - kind: command
        ref: "scripts/verify-docs-coverage.sh --only C20,C21"
        status: pass
    human_judgment: false
---

# Phase 15 Plan 01: PlanThenExecuteStrategy tracer Summary

One forced `submit_plan` call returns a two-step plan and `PlanThenExecuteStrategy` runs both steps in order, each prepared by the app's `ToolExecutor` and submitted alone through the gate with the planning call id, plus the shared `prepareGuarded` dedupe and the pinned schema, Builder guards and API.md rows.

**Duration:** about 6 min (2026-10-06T17:57Z to 18:03Z). **Tasks:** 3. **Files:** 10 (8 created, 2 modified).

## Accomplishments

- Tracer (Task 1): an app builds `PlanThenExecuteStrategy(id) { tooling; executor }`; one request with `tools = [submit_plan] + snapshot tools`, `ToolChoice.Required(submit_plan)`, `singleToolCall` true and one cached prefix produces a plan; `PlanRun` prepares each step with `prepareGuarded`, then `session.submit(prepared, callId)`. The test asserts `callCount == 1`, one turn, two gate calls with one mutation each, log order prepare, gate, apply, sink per step, positions `[0, 1]`, both COMMITTED, `providerCallId == "plan-1"`, and `Extraction.callId == "plan-1"` with arguments unchanged.
- Dedupe (Task 2, own commit): `AgenticDispatch.prepare` delegates to `prepareGuarded`; the executor-fault bytes now exist exactly once (in `PreparedStep.kt`); the commit touches exactly one file.
- Pins (Task 3): the `submit_plan` schema string and description are asserted byte for byte; guards `plan_tool_name_taken` and `plan_no_tools` fire before any provider call; `maxSteps` defaults to 8 and 0 throws; `toString` prints id and step limit only; API.md rows added.

## Final submit_plan description (verbatim, frozen at the tag)

> Submit the whole plan for the command as ordered steps, each calling one of the listed tools with its arguments. Give every step a short id that starts with a letter and uses only letters, digits, _ or -. To use a value that an earlier step's tool returns, write the whole argument value as the string $<stepId>.<key>, with the key names given in that tool's description. A reference is the entire value, never part of a longer string, and only names a step listed earlier. Do not call any other tool. When the command needs information you do not have, for example something that must be looked up first, set needs_lookup to true and leave steps empty.

Schema (pinned in `PlanSchemaTest`, default `maxSteps` 8, snapshot create_item, find_item, ask_user, tag_item):

```json
{"type":"object","properties":{"steps":{"type":"array","maxItems":8,"items":{"type":"object","properties":{"id":{"type":"string"},"tool":{"type":"string","enum":["create_item","find_item","tag_item"]},"arguments":{"type":"object"}},"required":["id","tool","arguments"]}},"needs_lookup":{"type":"boolean"}},"required":["steps"]}
```

## Task Commits

| Task | Name | Commit |
|---|---|---|
| 1 | Tracer: one forced submit_plan call runs a two-step plan through the gate | eafa436 |
| 2 | Pure dedupe: AgenticDispatch prepare delegates to prepareGuarded | e80fa93 |
| 3 | Builder guards, schema and request pinned, API.md rows | 196a8f2 |

Tracer feedback gate: auto mode off, `human_verify_mode` end-of-phase, and the tracer `<verify>` is automated-only, so it was re-run end to end before expansion (green), then Tasks 2 and 3 followed.

## Deviations from Plan

**[Rule 3 - Blocking] PlanParse step check split to satisfy detekt ComplexCondition** - Found during: Task 3 wave-end gate | Issue: the four-clause condition in `stepOf` tripped `ComplexCondition` (threshold 4) in `:core:detekt` | Fix: replaced the condition with chained `let`s and an `isRunnable` helper; behavior unchanged | Files: `core/.../strategy/plan/PlanParse.kt` (a Task 1 file, committed with Task 3) | Verification: `:core:test :core:detekt :core:scanBannedConstructs :core:metalavaCheckCompatibility` exits 0 | Commit: 196a8f2.

**Minor, no rule needed:** the `AgenticDispatch.prepare` delegate is a two-line block body (an `Extraction` local plus the `prepareGuarded` call) rather than a single expression, to stay inside the 120-column line limit; acceptance grep `prepareGuarded(context.session` still matches.

**Total deviations:** 1 auto-fixed (1 blocking lint). **Impact:** none on behavior or scope.

## Verification

- Wave-end gate: `:core:test :core:detekt :core:scanBannedConstructs :core:metalavaCheckCompatibility` exit 0 (host-safe Gradle recipe, one invocation at a time).
- `scripts/verify-docs-coverage.sh --only C20,C21`: OK (types=104).
- Prohibitions: `git diff 21e9547 -- StepSubmission.kt strategy/singleshot core/api.txt` is empty; no plan step is combined with another; `core/api.txt` not regenerated.
- All task acceptance criteria re-run: PASS (Task 1 greps and diff guard; Task 2 fault-bytes count 1, single-file commit; Task 3 guard codes, `maxItems`, API.md counts).

## Notes for later plans

- `RunStop.Held(last)` already carries whether the held step was the last; plan 15-04 applies the Completed(null) carve-out (open question 1) and the held-with-steps-remaining Escalate.
- `PlanRun.worked` is tracked but not yet read by the strategy; plans 15-03/15-04 use it for the replan and suppression rules.
- `parsePlan` is the tracer subset (mutating, non-terminal steps only; every other shape `Rejected("malformed", null)`); plan 15-02 replaces it with whole-plan validation, read tools in steps and the lookup escape.

## Issues Encountered

None.

## Self-Check: PASSED

Files verified present: `PreparedStep.kt`, `plan/PlanThenExecuteStrategy.kt`, `plan/PlanSchema.kt`, `plan/PlanParse.kt`, `plan/PlanRun.kt`, `PlanTestSupport.kt`, `PlanThenExecuteRunTest.kt`, `PlanSchemaTest.kt`. Commits eafa436, e80fa93, 196a8f2 present in `git log`. `commits: 3` measured from `rev-list --count c7e0d0d..HEAD`.
