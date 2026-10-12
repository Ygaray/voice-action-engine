---
phase: 20-cut-v1-1-0
plan: 04
subsystem: docs
tags: [ver-07, doc-02, c9, integration-md, api-md, wiring-stumbles]

requires:
  - phase: 20-02
    provides: final api.txt baselines (untouched here)
provides:
  - Router forced-call shape (pick_start_tier, one tier argument) in INTEGRATION.md and API.md
  - submit_plan argument schema with a worked two-step answer in INTEGRATION.md and API.md
  - SingleShot guidance for a first call that names a read tool
  - Imports text block above the undo-bridge region
  - Warning that the UndoJournal { } builder's store property shadows an outer store variable
affects: [20-09]

tech-stack:
  added: []
  patterns:
    - "doc facts taken from source constants, prose kept outside the byte-compared doc-snippet regions"

key-files:
  created: []
  modified:
    - INTEGRATION.md
    - API.md

key-decisions:
  - "SingleShot read call: the engine passes any non-terminal first call to the resolver, so the docs tell the resolver to return Resolution.Escalate(EscalationReason.Other(\"read_call\"), null) and to put an AgenticLoopStrategy after the tier; NoMatch is described as the weaker alternative (next tier starts fresh)"

requirements-completed: []  # VER-07 (cut v1.1.0) spans all 12 plans; not complete after 20-04

actuals:
  tokens: 4500
  tasks: 2
  commits: 2
plan_head_before: aec49fcf77d2a0a210e0778f06e4c7486ac10d2b

duration: n/a
completed: 2026-10-07
status: complete
---

# Phase 20 Plan 04: Fix the five C9 documentation stumbles Summary

**INTEGRATION.md and API.md now state the router's `pick_start_tier` answer shape, the `submit_plan` schema, what a SingleShot resolver does with a read call, the undo-bridge imports and the `store` shadowing trap, all taken from source, with no compiled doc-snippet region touched.**

## What was done

- Task 1 (tracer, bac4e8f): stumbles 1, 2 and 4.
- Task 2 (52eb5ac): stumbles 3 and 5, then the whole docs gate.

## Source trace per stumble

| Stumble | Wording taken from |
|---|---|
| 1 router answer | `core/.../pipeline/RouterPicker.kt` lines 22-23 (`ROUTER_TOOL = "pick_start_tier"`, `TIER_KEY = "tier"`), `routerToolSpec` (enum of eligible ids in ladder order), `decodePick` (any other answer is no pick, so `router_fallback`; refusal and max-token stops are untrusted) |
| 2 plan schema | `core/.../strategy/plan/PlanSchema.kt` (`submit_plan`; `steps` array with `maxItems`; item keys `id`, `tool` (enum of non-terminal tool names), `arguments`; `needs_lookup` boolean; only `steps` required) and `PlanBinding.kt` line 17/32 (`isStepId`: starts with a letter, letters, digits, `_`, `-`, cap 64; the cap is left to the existing section) |
| 4 SingleShot read call | `SingleShotStrategy.kt` `dispatch` (lines 135-142: unknown tool escalates MalformedExtraction, terminal tool completes, every other tool goes to `resolver.resolve`) and `resolutionOutcome` in `StepSubmission.kt` (Steps / NoMatch / Escalate / Failed); `SingleShotOutcomeMappingTest.anEscalationFromTheResolverHandsTheSameCarryToTheNextTier` proves an Escalate reaches the next tier |
| 3 bridge imports | `sample/.../undo/UndoCommitSink.kt` import list (eight identifiers) |
| 5 store shadowing | stumble text; `API.md` extension-points row `UndoJournal { store = ... }` |

## Acceptance results

- Task 1 verify: PASS (tool name, `"tier"`, schema field names in both documents, "read call" sentence, docs gate OK, wiring source selftest OK).
- Task 2 verify: PASS (all eight imports in the 22 lines above the `undo-bridge` marker, `shadow` in both documents, docs gate, docs selftest, wiring source selftest, README.md and ECOSYSTEM.md unchanged since 090fd8ec76).
- `bash scripts/verify-docs-coverage.sh`: `DOC COVERAGE OK checks=32 types=119` (C23 pre-tag note only); `--selftest`: `DOC COVERAGE SELFTEST OK plants=13`.
- C06 (doc-snippet regions byte-equal to the compiled tests) passes, so no fenced block after a marker changed. This plan changed only INTEGRATION.md and API.md.

## Deviations from Plan

None - plan executed exactly as written. (Small choice: the API.md shadowing warning lives in the `JournalStore` extension-point row, which is the "undo extension points" place the plan named.)

## Authentication Gates

None.

## Notes for 20-09

The wiring rerun on W must show the isolated agent no longer stumbles on these five points. Any later doc edit voids the wiring pass.

## Self-Check: PASSED

- Commits bac4e8f and 52eb5ac found in `git log`; `git rev-list --count aec49fcf..HEAD` measured 2 before this summary commit.
- Only INTEGRATION.md and API.md were changed by the task commits.
