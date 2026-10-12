---
phase: 13-on-device-model-spike
plan: 10
subsystem: build
tags: [spike-03, disposition, red-branch, on-device, jitpack, hygiene]

requires:
  - phase: 13-on-device-model-spike
    provides: "13-VERDICT.md with both SPIKE_VERDICT lines red (13-09); verdict reproduction script (13-03)"
provides:
  - "scripts/verify-spike-disposition.sh <removed|green_ship>: mechanical gate that no spike trace survives in the tracked build"
  - "13-DISPOSITION.md: branch red, spike03 N/A-deferred, harness_sha 08ada3366b, follow-ups for the driver"
  - "spike-ondevice/, its include, the litertlm catalog entries and the device-only scripts removed from the tree"
  - "SPIKE-03 traceability row N/A-deferred; v1.1.0 not blocked (L10)"
affects: [14, 15, 16, 17, 18, 19, 20]

tech-stack:
  added: []
  patterns: ["disposition gate with allow-listed token scan over tracked files"]

key-files:
  created:
    - scripts/verify-spike-disposition.sh
    - .planning/phases/13-on-device-model-spike/13-DISPOSITION.md
  modified:
    - settings.gradle.kts
    - gradle/libs.versions.toml
    - .planning/REQUIREMENTS.md
  deleted:
    - spike-ondevice/ (63 tracked files)
    - scripts/run-spike-ondevice.sh
    - scripts/verify-spike-device-guard.sh
    - scripts/verify-spike-evidence-filter.sh

key-decisions:
  - "Branch computed mechanically: greens=[] from the two SPIKE_VERDICT lines, so branch red (phase17_plumbing absent, which only matters for green)"
  - "CROSS-REPO-SCOPE-CONTRACT.md added to the disposition gate allow-list: it is the control-plane-owned contract whose L10 text names LiteRT and is changed only by section 10 amendments"
  - "The stale one-line comment above the spike include in settings.gradle.kts and the two catalog comments were removed with the entries; settings/catalog now match the pre-13-01 state"

patterns-established:
  - "A deleted module leaves no on-disk build output (spike-ondevice/build removed) so the verdict --check takes its temporary-worktree path"

requirements-completed: []
requirements-advanced: [SPIKE-03]

actuals:
  tokens: 40000
  tasks: 3
  commits: 3

plan_head_before: b00e24365bef02a4f549b8f121405a01d1719b30
commits: 3

duration: 20min
status: complete
completed: 2026-10-06
---

# Phase 13 Plan 10: Spike disposition Summary

**Both envelopes red, so the computed branch is `red`: the spike module, its include, its LiteRT-LM catalog entries and its device-only scripts are removed, SC4 and hygiene still pass, and the verdict still reproduces from history as `SPIKE_VERDICT_CHECK: OK lines=4`.**

## Branch computation

Both `SPIKE_VERDICT` lines in `13-VERDICT.md` read `verdict=red` (greens list empty), so the branch is `red`. Recorded in `13-DISPOSITION.md`: `branch: red`, `envelopes_green: none`, `spike03: N/A-deferred`, `harness_sha: 08ada3366b`, `phase17_plumbing: absent`. No Phase 13.1 request and no ROADMAP edit.

## Tasks

| Task | Commit | Result |
|---|---|---|
| 1 (tracer) disposition gate + branch | `22c793e` | Gate written and run while the spike existed: failed as required |
| 2 remove spike module and device scripts | `1fec77a` | 64 files deleted; SC4, ML denial controls, hygiene, disposition and `--check` all pass |
| 3 SPIKE-03 row and follow-ups | `deebfcf` | Traceability row N/A-deferred; requirement text unchanged; follow-ups section present |

## Verification evidence

- Task 1 (gate can go red): `scripts/verify-spike-disposition.sh removed` with the spike present printed `SPIKE DISPOSITION FAIL: spike-ondevice/ is present on disk; spike-ondevice/ has tracked files; settings.gradle.kts still includes a spike module; gradle/libs.versions.toml still has a litertlm key; spike-ondevice/build.gradle.kts declares the LiteRT-LM coordinate; ...` (exit 1).
- SC4, one Gradle process, low-memory recipe (`GRADLE_OPTS` no daemon, 2 workers, in-process Kotlin, `-Xmx1536m`, `--offline -q`): `:core:test --tests '*NoHardCodedConstantsTest*' :core:verifyCoreDependencyAllowlist :providers:verifyNoMlArtifacts :keystore:verifyNoMlArtifacts` exit 0.
- `scripts/verify-ml-denial-controls.sh`: `ML DENIAL CONTROLS OK plants=7`.
- `scripts/verify-repo-hygiene.sh`: `HYGIENE OK`.
- `scripts/verify-spike-disposition.sh removed`: `SPIKE DISPOSITION OK mode=removed`.
- `scripts/verify-spike-verdict.sh --check` (spike module gone, so it used its temporary worktree at code SHA `e362fb228b`; the worktree was cleaned up, `git worktree list` shows only the main one): `SPIKE_VERDICT_CHECK: OK lines=4`.
- `test ! -e spike-ondevice` exits 0; `grep -c 'include(":core", ":providers", ":keystore", ":sample")' settings.gradle.kts` prints 1; `git diff -U0 .planning/REQUIREMENTS.md` shows only the SPIKE-03 table row.
- Settings and catalog diffs are the exact inverse of the 13-01 additions (6 insertions then, 6 deletions now).

## Deviations from Plan

**1. [Rule 3 - blocking] `CROSS-REPO-SCOPE-CONTRACT.md` added to the gate's allow-list.** The plan's allow-list omitted it, but the file legitimately names "MediaPipe/LiteRT" in the L10 text and is control-plane-owned (changed only by section 10 amendments, never edited by a phase), so the gate would fail permanently on it. Listed with a comment in the script. No other allow-list changes.

**2. [Judgment] Leftover build output removed.** After `git rm` the ignored `spike-ondevice/build/` directory remained on disk, which would make the gate fail ("present on disk") and make `--check` take its in-place path. Removed with `rm -rf spike-ondevice` (a path-specific delete of ignored build output, not `git clean`).

**3. [Judgment] Stale comments removed with the entries.** The one-line comment above the spike include and the two comments above the catalog entries were removed together with the lines they described, so settings and catalog return to their pre-13-01 form.

## Authentication Gates

None. No device, network or spend; no adb.

## Notes for the driver

- Announce the red disposition with the verdict thread; no insertion needed. Phases 14-20 never configure the spike.
- Candidate follow-up only if the spike is ever re-run: the verdict code reads sb peak PSS on the winning cell only (13-09 deviation 4). Harness recoverable at `08ada3366b`.

## Self-Check: PASSED

- Files exist: `scripts/verify-spike-disposition.sh`, `13-DISPOSITION.md`, this SUMMARY; `spike-ondevice/` absent.
- Commits present: `22c793e`, `1fec77a`, `deebfcf` (measured `git rev-list --count b00e243..HEAD` = 3 before this summary).
