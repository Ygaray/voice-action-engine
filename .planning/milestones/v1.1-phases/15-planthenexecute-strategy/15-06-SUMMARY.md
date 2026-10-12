---
phase: 15-planthenexecute-strategy
plan: 06
subsystem: core/strategy/plan (review only)
status: complete
tags: [planthenexecute, surface-review, metalava, frozen-strings, open-items, phase-gate, plan-06]

requires:
  - phase: 15-05
    provides: onFailed parity, truncation, limits and redaction proof; the complete plan tier
provides:
  - "15-SURFACE-REVIEW.md: real-dump review of the plan tier, remainingStepIds as a +-only addition, frozen model-facing strings, OI-1..OI-7, gate results"
  - "Green full phase gate on the phase branch: check, verify-docs-coverage, verify-repo-hygiene, review-api-surface"
affects: [15-07]

plan_head_before: b6b6086be5a1fb1248d2490704b1b4b8f93f82c9
actuals:
  tokens: 6500
  tasks: 2
  commits: 2

tech-stack:
  added: []
  patterns:
    - "Surface review mirrors 14-SURFACE-REVIEW: isolated-copy Metalava dump, block diff of the touched outcome classes, member table, open items"

key-files:
  created:
    - .planning/phases/15-planthenexecute-strategy/15-SURFACE-REVIEW.md
  modified: []

key-decisions:
  - "No KDoc or source change was needed: the review found no wording that contradicts behavior or uses a domain word, so this plan changed no source file"
  - "OI-3 is recorded as pending with the probe approved (decision: approved, relayed GO); 15-LIVE-PROBE.md was not touched, and plan 15-07 fills the final status"

requirements-completed: [PLAN-01, PLAN-02, PLAN-03, PLAN-04, PLAN-05]

coverage:
  - id: D1
    description: "The real Metalava dump of :core shows core.strategy.plan holding exactly PlanThenExecuteStrategy, Builder and Companion (final; no sealed type, enum, data shape, static field or default stub), the three PLAN_* TraceCodes and CommandOutcome.Completed.remainingStepIds, with no internal plan type present"
    requirement: "PLAN-01"
    verification:
      - kind: command
        ref: "scripts/review-api-surface.sh --out <scratch>/vae-15-core-dump.txt: API SURFACE OK sealed=AssistantPart,CommandOutcome,GateDecision,Message,RunTermination,StrategyOutcome,ToolStep classes=196; dump holds PlanThenExecuteStrategy and PLAN_BINDING_UNRESOLVED and no PlanStep/ParsedPlan/PlanRun/PlanVerdict class"
        status: pass
    human_judgment: false
  - id: D2
    description: "RT-01 point 4: remainingStepIds is a +-only addition. The block diff of CommandOutcome.Completed, StrategyOutcome.Completed and StrategyOutcome.Escalate (committed core/api.txt against the dump) has zero removed lines and exactly two added lines, the getter and the property"
    requirement: "PLAN-04"
    verification:
      - kind: command
        ref: "block diff in 15-SURFACE-REVIEW.md; no '<' line, only the getRemainingStepIds getter and the remainingStepIds property as '>' lines; StrategyOutcome.Completed (3 public ctors) and Escalate (2) show no difference"
        status: pass
    human_judgment: false
  - id: D3
    description: "15-SURFACE-REVIEW.md lists every new public member with the consumer-next-ask question, the frozen model-facing strings (submit_plan, schema bytes, description, D-04 grammar, digest vocabulary, escalation and failure codes) and Open items OI-1..OI-7 with OI-1 resolved by RT-01 and OI-2 accepted"
    requirement: "PLAN-02"
    verification:
      - kind: command
        ref: "acceptance greps: submit_plan 2, '^- \\*\\*OI-[1-7]' 7, OI-1 line names RT-01, OI-2 line says accepted, remainingStepIds 8, bad_reference 2; no api.txt in git status"
        status: pass
    human_judgment: false
  - id: D4
    description: "Full phase gate green on the phase branch and phase invariants intact: check (detekt, scanner, ApiShapeTest, NoHardCodedConstantsTest, Metalava compat, both OkHttp legs), verify-docs-coverage, verify-repo-hygiene; every api.txt, StepSubmission.kt and strategy/singleshot byte-identical to 21e9547; AgenticDispatch.kt the only agentic change"
    requirement: "PLAN-05"
    verification:
      - kind: command
        ref: "./gradlew --offline -q check exit 0 (175 s); DOC COVERAGE OK checks=25 types=104; HYGIENE OK; git diff --quiet 21e9547 on api.txt files, StepSubmission.kt and singleshot exits 0"
        status: pass
    human_judgment: false
---

# Phase 15 Plan 06: Frozen-surface review, model-facing strings, open items and full phase gate Summary

The real Metalava dump of `:core` shows the plan tier adding exactly `PlanThenExecuteStrategy` (+ Builder, Companion), three `PLAN_*` trace codes and one getter, `CommandOutcome.Completed.remainingStepIds`, with no removed line; every model-facing string the v1.1.0 tag will freeze is recorded, and the full phase gate is green.

**Tasks:** 2. **Files:** 1 created (the review); no source file changed. **Commits:** 2 (see Task Commits).

## What was built

- **Task 1: surface review.** `scripts/review-api-surface.sh` (isolated copy, host-safe recipe) printed `API SURFACE OK ... classes=196` (Phase 14 closed at 193, so +3 plan classes). The block diff of `CommandOutcome.Completed`, `StrategyOutcome.Completed` and `StrategyOutcome.Escalate` against the committed `core/api.txt` has zero removed lines and exactly two added lines (the `remainingStepIds` getter and property); both `StrategyOutcome` classes keep every public constructor and their internal primary constructors are absent. `15-SURFACE-REVIEW.md` holds the dump summary, the +-only paragraph with the diff and the binary-additivity argument (internal constructor, not a data class, no default argument, ids through internal constructors beside unchanged public ones), a member-by-member table (every row addable without removal), the frozen strings (the `submit_plan` name, the exact schema bytes for a sample snapshot, the description quoted from the code, the D-04 grammar, the digest `{"status":"plan_rejected","reason":...,"step_index":...}` with its nine reasons and the `ignored_call` notice, the four escalation codes, the two failure codes), and `## Open items for orchestrator` OI-1 .. OI-7.
- **Task 2: phase gate.** One Gradle invocation, `check`, exit 0 in 175 s with no earlyoom kill; `verify-docs-coverage.sh` `DOC COVERAGE OK checks=25 types=104`; `verify-repo-hygiene.sh` `HYGIENE OK`. Invariants against `21e9547`: all three `api.txt` files, `StepSubmission.kt` and `strategy/singleshot/` byte-identical; `AgenticDispatch.kt` the only changed file under `strategy/agentic/`. The review states honestly that a byte-identical `core/api.txt` means only that no dump was committed early: the surface did grow, and the diff in the review is the +-only proof until the v1.1.0 cut. Main files changed outside the plan package are the five expected ones (AgenticDispatch.kt from 15-01; StrategyOutcome.kt, CommandOutcome.kt, TierWalk.kt, HeldCommit.kt from 15-04), each with its reason in the review.

## Task Commits

| Task | Name | Commit |
|---|---|---|
| 1 | Frozen-surface review (real dump, strings, open items) | 5c26b69 |
| 2 | Gate results and phase invariants appended | abb19c8 |

## For the driver: relay to the orchestrator

- **OI-1 (resolved by RT-01) and OI-2 (accepted 2026-10-06)** are recorded for the record only, together with the `remainingStepIds` surface addition: a new public getter on `CommandOutcome.Completed` (one getter + one property in the dump, +-only), filled on every early stop that ends the command (held step not listed, unresolved-reference step listed, empty after `commitHeld`); narrowing the fill rule to holds only is a one-line change plus tests, before the tag only.
- **Relay OI-3 .. OI-7:** OI-3 live probe approved, runs in plan 15-07, which replaces that line with PASS, FAIL or the deferred obligation; OI-4 `Extraction.callId` is the planning call id for every step and the position is the step identity; OI-5 a first-step fault or error step before any write is replanned once; OI-6 a PREVIEW, READ or no-action result from a mutating step counts as a failed step; OI-7 the replan digest carries engine codes and an index only.
- No `15-LIVE-PROBE.md` change and no live request in this plan.

## Deviations from Plan

None - plan executed exactly as written. The KDoc review (action step 4) found nothing to fix, so there is no `docs(15-06)` KDoc commit and no source change.

## Verification

- Task 1's automated verify, run against the dump and block diff produced in the session scratch directory (the dump had been produced once by the script; the checks were re-run on its output rather than starting a second Gradle job): all conditions held (`API SURFACE OK`, `class PlanThenExecuteStrategy`, `PLAN_BINDING_UNRESOLVED`, `getRemainingStepIds`, no internal plan class, no `<` line in the diff, only the two expected `>` lines, review file has the open-items heading and `OI-7`).
- Task 1 acceptance greps all pass (counts above); `git status --porcelain -- core/api.txt providers/api.txt keystore/api.txt` is empty.
- Task 2 gate commands exit 0 as listed; its acceptance greps pass (the Gate results section mentions `check`; `git diff --quiet 21e9547` on the three `api.txt` files exits 0).
- Threat mitigations: T-15-27 (real dump checked for internal plan types, `API SURFACE OK`), T-15-28 (dump in an isolated copy, all `api.txt` unchanged), T-15-29 (seven open items with the one-line change if overturned), T-15-30 (review records engine-owned strings and codes only).

## Notes

- No device, live provider or test key was touched.

## Issues Encountered

None.

## Self-Check: PASSED

`15-SURFACE-REVIEW.md` present with `## Open items for orchestrator`, OI-1 .. OI-7 and `## Gate results`; commits 5c26b69 and abb19c8 present in `git log`; `plan_head_before..HEAD` measured 2 commits matching `actuals.commits`.
