---
phase: 13-on-device-model-spike
plan: 11
subsystem: build
tags: [spike-03, on-device, green-ship, skipped, red-branch]

requires:
  - phase: 13-on-device-model-spike
    provides: "13-DISPOSITION.md with branch: red (13-10)"
provides:
  - "Recorded no-op: the conditional green_ship extraction of :ondevice did not run because the disposition is red"
affects: []

tech-stack:
  added: []
  patterns: []

key-files:
  created: []
  modified: []

key-decisions:
  - "Plan 13-11 is conditional on branch: green_ship; 13-DISPOSITION.md reads branch: red, so all three tasks record skipped and no code or doc file is touched"
  - "SPIKE-03 stays N/A-deferred (set by 13-10); it is not flipped to Complete because nothing shipped (L10: a red verdict does not block v1.1.0)"

requirements-completed: []
requirements-advanced: [SPIKE-03]

status: skipped
skipped_reason: "branch=red"
plan_head_before: e67f645d54e4f8f42917c802acfdfd3d6fdb60c6
actuals:
  tokens: 0
  tasks: 0
  commits: 0

duration: 5min
completed: 2026-10-06
---

# Phase 13 Plan 11: Conditional :ondevice Extraction Summary

**status: skipped (branch=red). The green_ship-only plan to ship a published `@Experimental` `:ondevice` module was a recorded no-op, with no code or doc file changed.**

## Precondition check

`13-DISPOSITION.md` reads `branch: red` (`envelopes_green: none`, `spike03: N/A-deferred`, `phase17_plumbing: absent`). Plan 13-11 only acts on `branch: green_ship`, so the precondition for every task is unmet and the plan's own guard applies: change no file and record the skip.

## Task results

| Task | Name | Result |
|------|------|--------|
| 1 | Branch guard, then (green_ship only) the `:ondevice` module | skipped (branch=red) |
| 2 | (green_ship only) fallback test, opt-in negative compile, api.txt seed, POM scope | skipped (branch=red) |
| 3 | (green_ship only) consumer docs, spike removal, disposition gate, SPIKE-03 status | skipped (branch=red) |

Task 1's second guard (Phase 17 manifest and consistency gate on main, else switch to `green_defer`) was not evaluated for action: it only applies after a green_ship read, and the disposition already records `phase17_plumbing: absent` for the red branch.

## Acceptance checks (non-green_ship rows)

- `test ! -e ondevice` exits 0 (no `:ondevice` module exists).
- `git status --porcelain -- README.md INTEGRATION.md ECOSYSTEM.md` prints nothing.
- No Task 2 file exists (`scripts/verify-ondevice-opt-in.sh`, `ondevice/api.txt`, `OnDeviceFallbackTest.kt`).
- The plan's `<automated>` verify commands exit 0 by design on a non-green_ship branch.

## Deviations from Plan

None. The plan's skip path was followed as written. No device or behavioral work was performed.

## Requirement status

SPIKE-03 remains `N/A-deferred (red verdict, Phase 13; L10)` in `.planning/REQUIREMENTS.md`, as set by 13-10. It was not marked Complete, since nothing shipped. `v1.1.0` is not blocked.

## Phase 13 state

This is the last plan of Phase 13 (11 of 11). The phase outcome is the red disposition: the spike module is removed, the harness is recoverable at `08ada3366b`, and no Phase 13.1 is requested.

## Self-Check: PASSED

- 13-11-SUMMARY.md written; `ondevice/` absent; docs untouched; commits measured as 0 with no code changes (docs-only, legitimate).
