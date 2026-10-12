---
phase: 20-cut-v1-1-0
plan: 13
subsystem: release-cut
tags: [ver-07, rt-11, gap-closure, selftest, contract-ledger]
status: complete
gap_closure: true

requires:
  - phase: 20-07
    provides: the red record of quiet window 20-01 (selftest control contract-ledger-only)
provides:
  - contract_append_row inserts the synthetic ledger row directly after the last section 11 table row
  - controls contract-row-in-table-last and contract-row-in-table-prose
  - superseding "handoff: none" block in evidence/cut-handoff.txt
affects: [20-07 Task 3 re-run in quiet window 20-07b, 20-08, 20-12]

actuals:
  tokens: 5200
  tasks: 1
  commits: 2
plan_head_before: 146025ec84b1bbb7207be17489fb8e06a7508c5e

key-files:
  created:
    - .planning/phases/20-cut-v1-1-0/20-13-PLAN.md
  modified:
    - scripts/release-cut.sh
    - .planning/releases/v1.1.0/evidence/cut-handoff.txt

key-decisions:
  - "The fix reads the table end with the existing ledger_range helper, run in the clone on HEAD (the plant requires the contract to be unmodified, so the working file equals HEAD)."
  - "Fixtures assert placement and call contract_change_is_ledger_only against the reshaped commit, rather than running gate diff against W, because reshaping the contract is itself a non-ledger change relative to W."

metrics:
  duration: 14min
  completed: 2026-10-08
---

# Phase 20 Plan 13: contract_append_row in-table fix Summary

**The selftest's synthetic ledger row now lands inside the section 11 table (right after the last `ledger_range` row) instead of at end of file, with two fixtures for the table-last and trailing-erratum shapes; the stale H1 hand-off is superseded.**

## What changed

- `scripts/release-cut.sh`, `contract_append_row`: finds the last table row with `ledger_range HEAD` in the clone and inserts the row on the next line (awk), after asserting the contract is unmodified.
- New helper `contract_row_fixture` and controls `contract-row-in-table-last` (table is the last thing in the file) and `contract-row-in-table-prose` (blank line plus `- **Erratum** ...` bullet after the table, the real shape since eef5cb9), registered in `CONTROL_ORDER` after `contract-ledger-only`. Each asserts: the planted row is the new last table row, nothing displaced the trailing content, and `contract_change_is_ledger_only` accepts the change.
- `cut-handoff.txt`: old `handoff: 20-12` block kept; a new block `handoff: none` / `from: 20-07` / `outcome: superseded` appended, so the last `handoff:` line reads `none`.
- Nothing else in `release-cut.sh` changed (hunks: contract_append_row, two new functions, two new controls, one CONTROL_ORDER line). No api or dump path touched.

## Root cause (recap)

The old plant used `printf >>`, which since eef5cb9 appended after the trailing erratum bullet, outside the table. The ledger-only predicate rightly rejected it, so the positive control went red. The gate was correct, the fixture was stale.

## Proof (pure bash, no Gradle, no window)

The selftest framework builds a Gradle green sandbox (`build_green_sandbox` runs apiDump), so `selftest` and `SELFTEST_ONLY=...` both need Gradle and were NOT run. Instead, a scratch harness (session scratchpad, not committed) extracted the real function text of `ledger_range`, `contract_change_is_ledger_only`, `plant_path`, `assert_planted`, `ccommit`, `contract_row_fixture`, `ctl_done` and the two controls from `scripts/release-cut.sh` and ran them in clones of a scratch repo seeded with the real contract:

- OLD `contract_append_row`: `contract-row-in-table-prose` FAILED ("last row line 288 before, 288 after"), and a mirror of the `ctl_contract-ledger-only` sequence was REJECTED. This reproduces the window 20-01 red.
- NEW `contract_append_row`: both controls `ok ... stayed green`, 0 fails; the mirror of the ledger-only sequence was ACCEPTED (`DIFF NOTE: ledger-only contract change (3 row line(s) in section 11 ...)`), so gate diff would be green.
- Planted line index (real contract shape): table header 281, separator 282, old last row 288, planted row 289 (new last row), blank line 290, erratum bullet 291 in the prose fixture; in the table-last fixture the file ends at line 289.
- Also green: `bash -n scripts/release-cut.sh`, `scripts/verify-docs-coverage.sh` (DOC COVERAGE OK), `scripts/verify-repo-hygiene.sh` (HYGIENE OK), `scripts/verify-stt-confinement.sh` (OK). shellcheck is not installed.

## Hand-off check (20-12)

Plan 20-12 reads the LAST `^handoff: ` line (`grep "^handoff: " | tail -1`). That now reads `handoff: none`, so 20-12 is no longer entered as scenario A/B/C. Caveat for the orchestrator: 20-12 treats `handoff: none` as scenario none and requires the tag on origin, `cut_result ok` and `jitpack_tag green`; with no tag yet it will report that contradiction and change nothing (not the plain "not entered" no-op). Nothing was changed in 20-12.

## W

- Fix commit: `4bdb663b4c7c1bf02d4588751705dfcc8b356ef1`
- New W candidate (`git log -1 --format=%H -- . ':!.planning'`): `4bdb663b4c7c1bf02d4588751705dfcc8b356ef1`
- `git diff --name-only 7f11d0ecb87f5400202a76d24de56b475f5385b1 HEAD -- . ':!.planning'` lists only `scripts/release-cut.sh`.
- api/dump path touched: no. The gate 10 / gate 12 / api-baseline OKs from window 20-01 stand.

## Deviations from Plan

None. The plan is a gap plan written by this executor from the orchestrator's RT-11 instruction.

## Next

Master sends "quiet window 20-07b"; plan 20-07 Task 3 is re-run (selftest all, then the bash gates, then W is fixed, expected at `4bdb663b`).

## Self-Check: PASSED

- FOUND: scripts/release-cut.sh (with contract_row_fixture)
- FOUND: .planning/phases/20-cut-v1-1-0/20-13-PLAN.md
- FOUND commits: 4bdb663, 8f51a69
