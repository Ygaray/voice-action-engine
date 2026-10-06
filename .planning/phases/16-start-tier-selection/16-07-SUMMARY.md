---
phase: 16-start-tier-selection
plan: 07
subsystem: docs
tags: [start-tier, router, docs, surface-review, phase-gate]

requires:
  - phase: 16-start-tier-selection
    provides: Custom picker, picker policy gate, selection record, engine Router (plans 16-01 to 16-06)
provides:
  - INTEGRATION.md "Choosing where the model walk starts" and the policy, telemetry and gotcha lines
  - API.md rows for Router, pickerTimeoutMillis, the Router telemetry sentence and the builder-vars row
  - 16-SURFACE-REVIEW.md: real-dump review, member table, frozen model-facing strings, OI-1 .. OI-9, gate results
affects: [phase-19-docs-and-gate-1, phase-20-release-cut]

actuals:
  tokens: 9500
  tasks: 3
  commits: 3

plan_head_before: d44c687ed7340129711f4087d9b1de4b4a992047
commits: 3

key-files:
  created:
    - .planning/phases/16-start-tier-selection/16-SURFACE-REVIEW.md
  modified:
    - INTEGRATION.md
    - API.md

key-decisions:
  - "Docs are prose and table rows only; no Kotlin fence (Open Q3), Phase 19 DOC-02 owns the compiled snippet"
  - "Every Phase 16 name and model-facing string recorded as frozen; tiersBypassed recorded as one-way (D-09)"
  - "OI-1 .. OI-9 ship their plan-specified defaults, all pending for the orchestrator relay"

requirements-completed: [ROUT-01, ROUT-02, ROUT-03, ROUT-04, ROUT-05]

duration: 25min
completed: 2026-10-06
status: complete
---

# Phase 16 Plan 07: Start-tier docs, frozen-surface review and phase gate Summary

**An agent can now wire `TierSelector.Custom(picker)` (picker id mapped) or opt into `TierSelector.Router { tierDescriptions }` from INTEGRATION.md and API.md alone, the real Metalava dump shows the Phase 16 surface as +-only, and the full phase gate is green with every api.txt and every providers/keystore source byte-identical to 9c88961.**

## Accomplishments

- Task 1 (tracer, docs slice, commit 0013c36): INTEGRATION.md section 5 gained "Choosing where the model walk starts": the zero-call head always runs first, `Custom(picker)` and its builder form, the picker id (`start_tier_picker`) that must be mapped in `ProviderSelectionSource` or every command records `provider_not_selected` then `router_fallback`, the collision rule with tier ids, null / unknown id / throw / timeout all fall back and never fail or pay, offline-only means the picker is never called, the opt-in Router (`start_tier_router`, descriptions sent to the provider, never secrets), and `tiersBypassed` as an upper bound. Section 8 lists `pickerTimeoutMillis` (default 2,000 ms, engine-enforced, earlier deadline wins) and `trace.selection`; one gotcha line covers mapping the picker or Router id. API.md names `Router { }` in the `TierSelector` row, `pickerTimeoutMillis` in the `TierPolicy` row, the Router telemetry sentence, and one builder-vars row. `verify-docs-coverage.sh` stayed at `DOC COVERAGE OK checks=25 types=107`; no new Kotlin fence.
- Task 2 (commit 321fee6): `scripts/review-api-surface.sh` printed `API SURFACE OK ... classes=206` (Phase 15 closed at 196, so exactly ten new classes). The block diff of `TierSelector`, `TierPolicy`, `TierPolicy.Builder`, `CommandTrace`, `PipelineEvent` and `TraceCode.Companion` has zero removed lines; the whole-dump diff has zero removed and 252 added lines (Phase 14 and 15 additions included). `PickingSpec`, `StartTierPicking`, `RunPickContext`, `SelectionBook` and `RouterPicker` are absent. `16-SURFACE-REVIEW.md` has the member table, the frozen strings quoted from `RouterPicker.kt`, and OI-1 .. OI-9.
- Task 3 (commit 3694d50): `./gradlew --offline -q ... check` exit 0 in 144 s, docs coverage and repo hygiene green, all three `api.txt`, `providers/src/main`, `keystore/src/main`, `PlanSchema.kt` and `PlanParse.kt` byte-identical to 9c88961, and `core/src/main` changed in exactly the 15 expected files. Gate results appended to the review.

## Task Commits

1. `0013c36` docs(16-07): start-tier selection wiring
2. `321fee6` docs(16-07): frozen-surface review of start-tier selection and open items OI-1..OI-9
3. `3694d50` docs(16-07): record the full phase gate results

## Open items for the orchestrator relay

Recorded in `.planning/phases/16-start-tier-selection/16-SURFACE-REVIEW.md` under "## Open items for orchestrator" (all pending, defaults shipped):

- OI-1: Router skip with one eligible model tier records no code and no record (A3).
- OI-2: default ids `start_tier_picker` / `start_tier_router` (A4).
- OI-3: names `StartTierSelection`, `StartTierSelected` (renamed from TierPicked), `pickerTimeoutMillis`, `Router.Builder.tierDescriptions`, `Custom.Builder.capabilities` (A5).
- OI-4: router output cap is `policy.maxTokensPerTurn` (D-08).
- OI-5: no fallback cause token in v1.1 (Open Q2).
- OI-6: router wording MEDIUM confidence; live check is the Phase 19 Gate-1 router leg, needs a TESTER window and a relayed GO with a request/USD ceiling.
- OI-7: RT-01 64-char step-id cap shipped, P15 OI-1 DO NOT NARROW honored.
- OI-8: SB 177 must map its picker id (condition #6); every Router user must map `start_tier_router`.
- OI-9: D-05 under P13 RED is defensive and JVM-proven with a fake ON_DEVICE-only tier; no on-device picker path in v1.1.

## Deviations from Plan

None. No behavior, KDoc or signature changed; the KDoc review found no gaps. The scratch files (dump, block diff, check log) are in `/tmp`, writable, so the scratchpad fallback was not needed.

## Notes

- Phase invariants hold: api.txt files untouched, no Kotlin fence added, no section 11 doc or contract touched, no push, no tag, no on-device or live-provider call.
- One idle Gradle daemon from an earlier session (about 4 h old) was present on the host and left alone; the gate ran with `--no-daemon`-equivalent options and was not killed by earlyoom.
- `phase.complete` is left to the orchestrator.

## Self-Check: PASSED

- Commits 0013c36, 321fee6, 3694d50 exist on gsd/phase-16-start-tier-selection; `git rev-list --count` from the ledger base gives 3.
- `.planning/phases/16-start-tier-selection/16-SURFACE-REVIEW.md` exists with 9 `- **OI-n` entries, `pick_start_tier` x4, `tiersBypassed` x5, `start_tier_router` x5.
- `git status --porcelain -- core/api.txt providers/api.txt keystore/api.txt` prints nothing.
