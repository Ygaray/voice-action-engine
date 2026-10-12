---
phase: 20-cut-v1-1-0
plan: 08
subsystem: release-cut
tags: [ver-07, c2, d-02, rt-12, jitpack-live-probe, binary-diff, waiver]
status: complete

requires:
  - phase: 20-07
    provides: W = 4bdb663b4c7c1bf02d4588751705dfcc8b356ef1 fixed (window 20-07b green)
provides:
  - W pushed (origin/main 26dcd10) behind the "pushing main" handshake, GATE OK pushed
  - live-probe-W.txt, LIVE PROBE PASS on W10 4bdb663b4c (C2 discharged)
  - binary-diff.txt (D-02) with the red core result kept and a D-02 WAIVER section (RT-12)
  - binary-diff-waiver.txt, the file plans 20-09 and 20-11 cite
  - v1.2 backlog item 999.1 (binary-diff helper should read Kotlin metadata visibility)
affects: [20-09 (new window 20-02b), 20-11 (LEDGER-ROW notes and evidence list), 20-12]

actuals:
  tokens: 7500
  tasks: 3
  commits: 8
plan_head_before: 67ea3cb791061d56c8e69b0928ee2feb14058fdb

key-files:
  created:
    - .planning/releases/v1.1.0/evidence/live-probe-W.txt
    - .planning/releases/v1.1.0/evidence/binary-diff.txt
    - .planning/releases/v1.1.0/evidence/binary-diff-waiver.txt
    - .planning/phases/20-cut-v1-1-0/20-QUIET-WINDOW-02.md
    - .planning/phases/20-cut-v1-1-0/20-QUIET-WINDOW-02b.md
    - .planning/phases/999.1-binary-diff-helper-kotlin-metadata-visibility/.gitkeep
  modified:
    - .planning/releases/v1.1.0/evidence/cut-handoff.txt
    - .planning/phases/20-cut-v1-1-0/20-11-PLAN.md
    - .planning/ROADMAP.md

key-decisions:
  - "D-02 core removed=12 is waived per RT-12 (tool false positive): 11 members of internal constructors plus 1 synthetic accessor; W unchanged, no restart."
  - "Plan 20-09 does not reuse window 20-02: it runs in a new window request 'quiet window 20-09' (20-QUIET-WINDOW-02b.md)."

metrics:
  duration: 26min
  completed: 2026-10-08
---

# Phase 20 Plan 08: Push W, C2 live probe, D-02 binary diff Summary

**W (4bdb663b4c) is pushed and live on JitPack with exactly the five manifest artifacts (C2 PASS); the D-02 core binary diff came back RED (removed=12) and was ruled a tool false positive by RT-12, then waived with a file:line mapping of every removal line to an internal constructor or a synthetic accessor; W is unchanged.**

## Tasks and commits

| Task | What | Commit |
|------|------|--------|
| 1 | Request window 20-02, stage the push; after the orchestrator OK, push `26dcd10` and open the window | 26dcd10 (request), 109f633 (window open, GATE OK pushed) |
| 2 (tracer) | C2 `scripts/jitpack-live-probe.sh 4bdb663b4c`: LIVE PROBE PASS, JitPack status ok on commit W, five artifacts, consumer probe on an empty cache | 5e3d8c7 |
| 3 | D-02 `scripts/verify-binary-diff.sh` on v1.0.1 vs W10 artifacts: core RED (removed=12), providers OK (classes=14), keystore OK (classes=9) | 5247a85 (evidence), 782f999 (window 20-02 closed red, handoff red) |
| 3 (waiver) | RT-12 ruling applied: waiver recorded, handoff superseded to `none`, 20-11 draft updated, backlog 999.1 filed | a6191fa (orchestrator ruling, CONTEXT), 2354e90, d9ee4f5 |

## Task 3 outcome: D-02 waived per RT-12

The helper listed 12 removed JVM members in core. The ruling (orchestrator 3b) says they are a tool false positive; the executor verified it in source rather than trusting it:

- Core tree at HEAD equals W (`git diff --quiet 4bdb663b4c HEAD -- core`), so the W line numbers are the HEAD ones.
- All 12 lines map to a declaration (full table with file:line in `binary-diff-waiver.txt`): 5 declared `internal constructor(` (ActionEvent, HeldProposal, CommandOutcome.Completed, CommandOutcome.Unhandled, CommandTrace), 6 synthetic members of an internal constructor (ExecutedAction x2, TierPolicy, CommandTrace `$default`, TierAttempt x2), 1 compiler synthetic accessor (`access$submitAll` for `private suspend fun submitAll`, SingleShotStrategy.kt:149 at v1.0.1, absent from W source).
- The ExecutedAction, CommandTrace and TierAttempt pairs are a `$default` (mask) synthetic plus a DefaultConstructorMarker synthetic (or the declared ctor) of ONE internal constructor each.
- `core/api.txt` has 0 `ctor` lines in each of the eight class blocks, at HEAD and at v1.0.1. No anomaly: no public constructor is declared in source for any of them. The v1.0.1 source declared them `internal constructor(` too.
- The earlier executor analysis in binary-diff.txt ("11 real public-constructor removals") was wrong about visibility and is corrected in the waiver section.

C2 PASS stands. Window 20-02 stays closed red as recorded (header and step outputs untouched; one `d02: waived (RT-12)` line added before the Close section). The last `handoff:` line in `cut-handoff.txt` is `none` (superseded, from 20-08).

## Deviations from Plan

1. **[Rule 4 resolved by orchestrator ruling] Task 3 acceptance not met literally.** The plan wants three `BINARY DIFF OK` lines with `removed=0`; core printed `BINARY DIFF FAIL: removed=12`. The plan's automated verify (`grep -q "^BINARY DIFF FAIL"` must be absent, three OK lines) will therefore stay red on the file as recorded. The substitute acceptance is the RT-12 waiver (`binary-diff-waiver.txt`). Core's OK line does not exist and was not fabricated.
2. **Window not closed green by this plan.** Plan 20-08 was meant to leave window 20-02 open for plan 20-09. It was closed red at the master's instruction after the D-02 red; plan 20-09 needs its own window (`quiet window 20-09`, `20-QUIET-WINDOW-02b.md`). Plan 20-09's text still says "window 20-02 is consumed" at its end; that must be satisfied by the 02b file, handled like the 01/01b pattern. Window 02's `grant` and `heavy_gates` were NOT edited.
3. **ROADMAP.md edited for the backlog item** (new `## Backlog` section, phase dir 999.1) per the gsd add-backlog convention. No code, script or doc under W changed.

## Next

Plan 20-09 (C4 isolated fresh-agent wiring re-run on W10, empty-cache verify) runs in a new window: request text `quiet window 20-09`, file `.planning/phases/20-cut-v1-1-0/20-QUIET-WINDOW-02b.md` (`grant: pending`). Plan 20-11's LEDGER-ROW notes will name the D-02 waiver and cite `binary-diff-waiver.txt` in its evidence list. RT-12 also asks for the waiver in the ledger-row `contents` field; the 20-11 draft names it in `notes:` and `evidence:` as instructed, and `contents:` is left for the orchestrator to confirm.

## Self-Check: PASSED

- FOUND: .planning/releases/v1.1.0/evidence/binary-diff-waiver.txt, binary-diff.txt (section D-02 WAIVER), live-probe-W.txt, cut-handoff.txt (last handoff: none)
- FOUND commits: 26dcd10, 109f633, 5e3d8c7, 5247a85, 782f999, 2354e90, d9ee4f5
