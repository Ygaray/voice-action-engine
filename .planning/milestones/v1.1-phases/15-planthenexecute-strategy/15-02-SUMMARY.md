---
phase: 15-planthenexecute-strategy
plan: 02
subsystem: core/strategy/plan
status: complete
tags: [planthenexecute, binding, whole-plan-validation, needs-lookup, trace-codes, api-md]

requires:
  - phase: 15-01
    provides: PlanThenExecuteStrategy tracer, PlanRun step loop, tracer parsePlan, Plan test support
provides:
  - PlanBinding.kt - isStepId, referencedStepIds, bindArguments, mergeTargets (internal, pure, never throw)
  - Whole-plan static validation in parsePlan with the NeedsLookup verdict and a fixed rejection-code vocabulary
  - TraceCode.PLAN_REJECTED, PLAN_REPLANNED, PLAN_BINDING_UNRESOLVED
  - PlanRun binding from a committed-only results map, RunStop.BindingUnresolved, outcomeOf mapping
  - Needs-lookup escalation (Other(plan_needs_lookup)) with the incoming carry, nothing run
affects: [15-03, 15-04, 15-05, 15-06, 15-07]

plan_head_before: 1830f825cb6409f635efbc1e80309add0980e76a
actuals:
  tokens: 8000
  tasks: 3
  commits: 3

tech-stack:
  added: []
  patterns:
    - "Binding is whole-value, in parsed JsonObject space; a bound value is always a JSON string; no regex over serialized JSON"
    - "The results map is written only after an all-COMMITTED dispatch, so held, preview and error steps are never bindable"
    - "RunStop to StrategyOutcome mapping lives in a top-level outcomeOf in PlanRun.kt, keeping the strategy class under the 12-function detekt limit"

key-files:
  created:
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/plan/PlanBinding.kt
    - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/PlanBindingTest.kt
    - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/PlanParseTest.kt
    - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/PlanThenExecuteBindingTest.kt
    - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/PlanThenExecuteLookupTest.kt
  modified:
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/plan/PlanParse.kt
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/plan/PlanRun.kt
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/plan/PlanThenExecuteStrategy.kt
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/telemetry/TraceCode.kt
    - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/TraceTest.kt
    - API.md

key-decisions:
  - "Reference grammar and rejection vocabulary frozen as recorded below (D-04 mechanics, OI-2 accepted by the orchestrator)"
  - "outcomeOf(stop, carry) is a top-level internal function in PlanRun.kt; the strategy's rejection mapping is a top-level private function, to stay within TooManyFunctions (class limit 12)"
  - "PlanVerdict.Rejected gained an internal unknownTool property so the strategy can add the UNKNOWN_TOOL code without importing the private code constant"

requirements-completed: [PLAN-02, PLAN-03]

coverage:
  - id: D1
    description: "SC2 / PLAN-02: create then tag - the tag step's executor receives exactly {item_id: id-1, tag: red}; outcome Completed not partial; one provider call"
    requirement: "PLAN-02"
    verification:
      - kind: unit
        ref: "PlanThenExecuteBindingTest#aLaterStepReceivesTheRealIdOfTheStepBeforeIt"
        status: pass
    human_judgment: false
  - id: D2
    description: "Binding keys: pending-change targetIds bindable, StepResult wins on the same key; nested arrays and objects bound; literals, object keys, numbers and null unchanged"
    requirement: "PLAN-02"
    verification:
      - kind: unit
        ref: "PlanThenExecuteBindingTest, PlanBindingTest"
        status: pass
    human_judgment: false
  - id: D3
    description: "D-05/D-06: unresolved key or disagreeing actions -> step not prepared, plan_binding_unresolved, escalation_suppressed, next tier not run; preview, error and held steps are never bindable"
    requirement: "PLAN-02"
    verification:
      - kind: unit
        ref: "PlanThenExecuteBindingTest (six failure cases)"
        status: pass
    human_judgment: false
  - id: D4
    description: "D-02: whole-plan validation in one documented order before step 1; every code in the fixed vocabulary; a direct app-tool call is malformed and never dispatched"
    requirement: "PLAN-02"
    verification:
      - kind: unit
        ref: "PlanParseTest (12 tests)"
        status: pass
    human_judgment: false
  - id: D5
    description: "D-10/D-11 (PLAN-03 lookup half): needs_lookup or a read-tool step escalates with Other(plan_needs_lookup), one provider call, zero executor/gate/sink, carry forwarded"
    requirement: "PLAN-03"
    verification:
      - kind: unit
        ref: "PlanThenExecuteLookupTest (4 tests)"
        status: pass
    human_judgment: false
  - id: D6
    description: "Three additive TraceCodes with snake-case wire values; declared-code equality holds; API.md names binding, lookup and the codes"
    verification:
      - kind: unit
        ref: "TraceTest#thePlanCodesHaveTheirSnakeCaseWireValues"
        status: pass
      - kind: command
        ref: "scripts/verify-docs-coverage.sh --only C20,C21"
        status: pass
    human_judgment: false
---

# Phase 15 Plan 02: Binding, whole-plan validation and the lookup escape Summary

A later plan step now receives an earlier step's real committed id (`$s1.item_id` bound from `ExecutedAction.targetIds`), the whole plan is validated before step 1 in one documented order, and a plan that needs a lookup escalates after one provider call with nothing run and the carry forwarded.

**Duration:** about 25 min (2026-10-06). **Tasks:** 3. **Files:** 11 (5 created, 6 modified). **Commits:** 3.

## Final reference grammar (verbatim for plan 15-06's surface review)

A reference is a JSON string whose ENTIRE content is `$<stepId>.<key>`:

- Step id: `^[A-Za-z][A-Za-z0-9_-]*$` (letter-leading, so a dictated `$5.00` can never name a step).
- Key: one or more non-whitespace characters (dots allowed; key names are app-owned). Implemented as the anchored pattern `[$]([A-Za-z][A-Za-z0-9_-]*)\.(\S+)` with `matchEntire`.
- Literals, never touched: `$5.00`, `$`, `$s1`, `$s1.`, `$1a.b`, a reference inside a longer string (`price $s1.item_id`, with leading or trailing space), `$s1.item id`, `$$s1.x` (no escape syntax in v1.1), object KEYS shaped like a reference, numbers, booleans and JSON null.
- Arrays and nested objects are walked depth first; a bound value is always a JSON string; key order is kept.
- A reference to an id not declared by an EARLIER step (unknown, forward or self) is a static rejection (`bad_reference`). A key the earlier committed step did not return, or one two actions of that step disagree on, is a dynamic miss: the step is not prepared, `plan_binding_unresolved` is recorded and the tier escalates with `Other(plan_binding_unresolved)`, which TierWalk suppresses because step 1 committed.
- Only a step whose dispatch recorded COMMITTED actions only is bindable (the merged `targetIds`: mutation ids plus result ids, result wins; a conflicted key is dropped for good).

## Final rejection-code vocabulary (verbatim)

`malformed`, `too_many_steps`, `empty`, `unknown_tool`, `terminal_tool`, `bad_id`, `duplicate_id`, `bad_reference`. `stepIndex` is the engine's 0-based index, `null` when the whole plan is at fault.

Validation order (first failing check decides): (1) first call must be `submit_plan` (a direct app-tool call is `malformed`, never dispatched; calls after the first are ignored); (2) `needs_lookup` absent or a JSON boolean else `malformed`, `true` -> NeedsLookup whatever `steps` holds, `steps` must be an array else `malformed`; (3) `too_many_steps`; (4) per-step shape (object with string `id`, string `tool`, object `arguments`) else `malformed` with index; (5) a step naming a read tool -> NeedsLookup; (6) `empty`; (7) per step: `unknown_tool`, `terminal_tool`, `bad_id`, `duplicate_id`, `bad_reference`; (8) Valid.

Escalation reasons added: `Other("plan_needs_lookup")` (nothing ran, carry forwarded), `Other("plan_binding_unresolved")`; a rejected plan still escalates `MalformedExtraction` (plan 15-03 inserts the replan there). Trace codes: `plan_rejected` (plus `unknown_tool` when that rejection code), `plan_replanned` (declared, recorded from 15-03), `plan_binding_unresolved`.

## Task Commits

| Task | Name | Commit |
|---|---|---|
| 1 | D-04 reference grammar, whole-value binding, committed-target merge | 6e6145c |
| 2 | Whole-plan validation, lookup verdict, plan trace codes, binding in the step loop | e304a67 |
| 3 | SC2 binding and lookup end-to-end tests, API.md sentences | 5b8e8f0 |

## Deviations from Plan

**Task boundary shift (no rule needed):** the Task 3 `PlanRun` changes (commit-only results map, bind before prepare, `RunStop.BindingUnresolved`, `outcomeOf`) were committed with Task 2, because the Task 2 strategy mapping calls `outcomeOf` and the two files must compile together. Task 3 then contained only the end-to-end tests and API.md. Behavior and acceptance criteria are unchanged.

**[Rule 3 - Blocking] Strategy class function limit:** adding the rejection and run mapping as members would have exceeded detekt `TooManyFunctions` (class threshold 12; the strategy already had 11). The run-stop mapping moved to a top-level `outcomeOf` in `PlanRun.kt` and the rejection mapping to a top-level private function in the strategy file. No behavior change.

**[Rule 3 - Blocking] detekt MaxLineLength:** one over-long line in `PlanParseTest` found by the wave gate; split (committed with Task 3).

**Total deviations:** 2 auto-fixed lint/structure items, 1 task-boundary note. **Impact:** none on behavior or scope. RT-01 (hold semantics, `remainingStepIds`) belongs to plan 15-04 and is untouched here; a Held stop still maps to `Escalate(Other(plan_step_held))` as in the tracer.

## Verification

- Wave-end gate (one Gradle invocation, host-safe recipe): `:core:test :core:detekt :core:scanBannedConstructs :core:metalavaCheckCompatibility` exit 0. `core/api.txt` untouched.
- `scripts/verify-docs-coverage.sh --only C20,C21`: OK (types=104).
- All task acceptance greps re-run: PASS. Test counts: PlanBindingTest 9, PlanParseTest 12, PlanThenExecuteBindingTest 9, PlanThenExecuteLookupTest 4, TraceTest extended.
- Threat mitigations: T-15-07 (static earlier-only check; committed-only map; no guessed value), T-15-08 (letter-leading ids, whole-value grammar), T-15-09 (unknown/terminal rejected; read tools become a zero-side-effect lookup escalation), T-15-10 (map written only after all-COMMITTED; preview/error/held tests), T-15-11 (fixed-vocabulary codes only; rejection carries an engine index; `PlanStep` and `ParsedPlan` toString print counts and the tool name only).

## Notes for later plans

- Plan 15-03 inserts the one replan where `rejection()` currently escalates `MalformedExtraction`; `PLAN_REPLANNED` is declared but not yet recorded.
- Plan 15-04 owns RT-01: `RunStop.Held(last)` and `PlanRun.worked` are available; `outcomeOf` is the single place the stop-to-outcome mapping lives. A binding-unresolved stop should list the unprepared step ids in `remainingStepIds` (RT-01 mechanics (b)).
- `PlanVerdict.Rejected.unknownTool` is an internal convenience; the code constants stay private to `PlanParse.kt`.

## Issues Encountered

None.

## Self-Check: PASSED

Files verified present: `PlanBinding.kt`, `PlanBindingTest.kt`, `PlanParseTest.kt`, `PlanThenExecuteBindingTest.kt`, `PlanThenExecuteLookupTest.kt`, and the six modified files. Commits 6e6145c, e304a67, 5b8e8f0 present in `git log`. `commits: 3` measured from `rev-list --count 1830f82..HEAD`.
