---
phase: 13-on-device-model-spike
verified: 2026-10-06T06:30:00Z
status: passed
score: 4/4 roadmap success criteria verified (red branch; SC3 green clause N/A by design)
covered_files:
  - .planning/REQUIREMENTS.md
  - .planning/phases/13-on-device-model-spike/13-01-PLAN.md
  - .planning/phases/13-on-device-model-spike/13-01-SUMMARY.md
  - .planning/phases/13-on-device-model-spike/13-02-PLAN.md
  - .planning/phases/13-on-device-model-spike/13-02-SUMMARY.md
  - .planning/phases/13-on-device-model-spike/13-03-PLAN.md
  - .planning/phases/13-on-device-model-spike/13-03-SUMMARY.md
  - .planning/phases/13-on-device-model-spike/13-04-PLAN.md
  - .planning/phases/13-on-device-model-spike/13-04-SUMMARY.md
  - .planning/phases/13-on-device-model-spike/13-05-PLAN.md
  - .planning/phases/13-on-device-model-spike/13-05-SUMMARY.md
  - .planning/phases/13-on-device-model-spike/13-06-PLAN.md
  - .planning/phases/13-on-device-model-spike/13-06-SUMMARY.md
  - .planning/phases/13-on-device-model-spike/13-07-PLAN.md
  - .planning/phases/13-on-device-model-spike/13-07-SUMMARY.md
  - .planning/phases/13-on-device-model-spike/13-08-PLAN.md
  - .planning/phases/13-on-device-model-spike/13-08-SUMMARY.md
  - .planning/phases/13-on-device-model-spike/13-09-PLAN.md
  - .planning/phases/13-on-device-model-spike/13-09-SUMMARY.md
  - .planning/phases/13-on-device-model-spike/13-10-PLAN.md
  - .planning/phases/13-on-device-model-spike/13-10-SUMMARY.md
  - .planning/phases/13-on-device-model-spike/13-11-PLAN.md
  - .planning/phases/13-on-device-model-spike/13-11-SUMMARY.md
  - .planning/phases/13-on-device-model-spike/13-DISPOSITION.md
  - .planning/phases/13-on-device-model-spike/13-VERDICT.md
  - gradle/invariants.gradle.kts
  - scripts/spike-evidence-filter.sh
  - scripts/verify-ml-denial-controls.sh
  - scripts/verify-repo-hygiene.sh
  - scripts/verify-spike-disposition.sh
  - scripts/verify-spike-verdict.sh
covered_digest: "v1:sha256:0743b48814cb0d65a8ce8c30b8813f4f5c376e57cda0a6497667e05e0b7a1cc7"
behavior_unverified: 0
overrides_applied: 0
re_verification: false
---

# Phase 13: On-Device Model Spike Verification Report

**Phase Goal:** Measure Gemma 4 E2B on LiteRT-LM on the TESTER within a time-box, produce a reproducible per-envelope verdict relayed to the orchestrator, disposition it mechanically; `:core`/`:providers` gain no on-device/ML dependency whatever the verdict.
**Verified:** 2026-10-06
**Status:** passed (with warnings, none blocking)
**Re-verification:** No, initial verification

Verdict being verified: small RED (measured fail), sb RED (unmeasured, 4 h time-box expired at screen_sb trial 28/80); disposition branch `red`.

## Goal Achievement

### Observable Truths (ROADMAP success criteria)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | Within a declared time-box a Gemma-2B-class model runs on the TESTER; a recorded measurement shows latency, peak RAM, strict-JSON reliability with trial count, plus APK cost | VERIFIED (small full; sb partial, see W1) | Committed evidence under `evidence/` (15 files, all re-pass `scripts/spike-evidence-filter.sh`): `screen_small.txt` 80/80 trials, `confirm_small.txt` 164/164 trials, plus init/prefill/kv_reuse/rf_matrix rows. 13-VERDICT.md small row: warm p50/p95 20,278/28,941 ms, peak PSS 2,268 MB, schema-valid 142/144, EN 43/55, ES 35/55, false writes 2/34. APK cost (21.8 MB raw / 9.5 MB deflated `.so`, 30.77 MB APK) in the toolchain table. Time-box 14,400 s recorded in `13-WINDOW-GRANT.md` (grant consumed, closed 05:52:07Z) and the `screen_sb.host.txt` line `result=timeout source=host`. |
| 2 | A green/red verdict with numbers is messaged to the orchestrator before SB 179 / CT 75 plan | VERIFIED (record), see W2 | `13-VERDICT.md` carries machine `SPIKE_VERDICT` lines per envelope. I ran `scripts/verify-spike-verdict.sh --check`: `SPIKE_VERDICT_CHECK: OK lines=4`, exit 0 (recomputed in a temp worktree at code SHA e362fb228b; worktree list is clean afterwards). `13-VERDICT-MESSAGE.md` holds the relay body plus `relayed_to`/`relayed_at: 2026-10-06T05:58:59Z`. The record says it is a handoff to the milestone master, not a confirmed delivery. |
| 3 | Green: provider ships `@Experimental`. Red: no module and no code ship, SPIKE-03 N/A-deferred, v1.1.0 not blocked | VERIFIED (red clause) | `13-DISPOSITION.md`: `branch: red`, `spike03: N/A-deferred`, computed from both verdict lines red. `spike-ondevice/` absent on disk and from `git ls-files`. No `spike`/`litert` in `settings.gradle.kts`, `jitpack.yml`, or `gradle/libs.versions.toml`. `scripts/run-spike-ondevice.sh`, `verify-spike-device-guard.sh`, `verify-spike-evidence-filter.sh` gone. `scripts/verify-spike-disposition.sh removed` prints `SPIKE DISPOSITION OK mode=removed` (exit 0). `REQUIREMENTS.md` traceability: `SPIKE-03 ... N/A-deferred (red verdict, Phase 13; L10)`. No Phase 13.1 inserted. Plan 13-11 recorded skipped (branch red, 0 files). |
| 4 | Whatever the verdict, `:core` and `:providers` gain no on-device/ML dependency; allowlist and no-on-device-implementation scan still pass | VERIFIED | `core/build.gradle.kts` deps: coroutines, serialization only; `providers`: `:core` + okhttp only. `verifyNoMlArtifacts` registered in `gradle/invariants.gradle.kts:336`; `NoHardCodedConstantsTest` carries litert/mediapipe/tflite tokens (lines 62-64). `scripts/verify-ml-denial-controls.sh` prints `ML DENIAL CONTROLS OK plants=8` (exit 0). `scripts/verify-repo-hygiene.sh` prints `HYGIENE OK`. Gradle gates (`:core` allowlist + NoHardCodedConstantsTest, `verifyNoMlArtifacts`, `:core/:providers/:keystore` unit tests) were run green (exit 0) by the orchestrator after the review fixes; cited, not re-run (host memory). |

### Plan must-haves judged against the red outcome

| Truth (plan) | Status | Evidence |
|--------------|--------|----------|
| 13-01 D-07 thresholds committed before any device step | VERIFIED | `13-THRESHOLDS.md` sha8 `ec4933fb` equals `thresholds_sha` in the verdict META and `--check` refuses on mismatch (passed). |
| 13-01 D-02 toolchain proof (0.17.1 builds, no fallback) | VERIFIED | `evidence/toolchain.txt`, verdict toolchain table (`compile ok`, `dex ok`, `page=16k`), pin 0.17.1. |
| 13-02 D-09 module-scoped ML denial, negative controls, hygiene patterns | VERIFIED | Controls run green (8 plants); hygiene green; WR-01 added `*sb-fixture*` gating and its plant. |
| 13-08 D-05 both envelopes measured with full gold sets | PARTIAL, accepted under 13-08's own time-box truth (W1) | small: full gold (164 confirm). sb: 28 of 80 screen trials, confirm/sustained/exit_reasons refused by the runner at the box. |
| 13-08 time-box expiry makes unmeasured gating metrics red | VERIFIED | sb verdict line `reasons=unmeasured:warm_p50,...` (9 unmeasured reasons); no re-run, no extension (grant consumed). |
| 13-08 device hygiene: TESTER only, cleanup OK, grant consumed | VERIFIED (by record) | `13-WINDOW-GRANT.md` `grant: consumed`, device `R5CT10XNKQN`; 13-08 summary cites `SPIKE_ONDEVICE: OK sub=cleanup`. Device state not re-checked (not touching the TESTER). |
| 13-09 verdict is machine-computed, per-envelope, thresholds beside numbers, D-10 rows, Gemma-2B resolved to E2B, control reported | VERIFIED | `13-VERDICT.md` sections present; SPIKE_CONTROL `skipped_gated`; `--check` OK. |
| 13-09 no ledger row written by executor | VERIFIED (judgment) | No section 11 edit in phase commits. |
| 13-10 evidence, verdict, filter and verdict script not deleted | VERIFIED | All present; `--check` still reproduces the verdict after deletion. |
| 13-10 no ROADMAP 13.1 insertion | VERIFIED | ROADMAP shows 11 plans, no 13.1. |
| 13-11 no-op unless green_ship | VERIFIED | Summary "skipped, branch=red"; no `ondevice/` in tree. |
| Evidence contains no transcript/model output/tool arg/SB tool name/key shape | VERIFIED | All 15 evidence files pass the filter (`FILTER OK`); grep for key shapes finds none; WR-02/WR-03 hardened the scan. |

### Requirements Coverage

| Requirement | Source Plans | Description | Status | Evidence |
|-------------|--------------|-------------|--------|----------|
| SPIKE-01 | 13-01, 13-03..13-08 | Bundled Gemma-2B-class model measured on TESTER: latency, RAM, strict-JSON reliability | SATISFIED | Small envelope fully measured; sb partial and red-by-D-07 (ROADMAP wording "green or red with numbers within a time-box" met). |
| SPIKE-02 | 13-03, 13-09 | Verdict messaged to orchestrator early | SATISFIED (record) | `13-VERDICT.md`, `13-VERDICT-MESSAGE.md`, reproducible check OK. See W2. |
| SPIKE-03 | 13-02, 13-10, 13-11 | Green: ships `@Experimental`; red: nothing ships, tag not blocked | SATISFIED as N/A-deferred | Red branch executed and mechanically gated. |

All three IDs appear in plan frontmatter and in REQUIREMENTS.md; no orphaned Phase 13 requirements.

### Anti-Patterns Found

None blocking. No `TBD`/`FIXME`/`XXX` in any phase-modified file. Code review: 0 critical, 7 warnings; WR-01..WR-04 and IN-05 fixed; WR-05..WR-07 documented acceptable-skips (review status fixed, fix report resolved).

### Human Verification Required

None required for the status. Advisory only, see W1 and W2.

## Warnings (non-blocking)

- **W1: SB envelope is unmeasured, not measured-and-failed.** 13-08's truth "both envelopes are measured with the full gold sets" is not literally met for sb (28/80 screen trials). The same plan's time-box truth and D-07 define this exact outcome as red-with-`unmeasured:*`, and the verdict says so openly, so I judge it a documented outcome, not a gap. Consumer implication: SB 179 has no on-device latency or accuracy number to plan against. The sb peak-PSS (525 MB) and thermal cells in the verdict are not valid envelope measurements (documented caveat in 13-VERDICT.md). If strict plan wording matters, accept it with an override:
  ```yaml
  overrides:
    - must_have: "Both envelopes are measured on the TESTER with the full gold sets"
      reason: "4 h time-box expired in screen_sb (28/80); D-07 makes unmeasured gating metrics red; documented in 13-VERDICT.md"
      accepted_by: "<name>"
      accepted_at: "<ISO timestamp>"
  ```
  I did not add this myself: overrides are for a human to accept.
- **W2: relay is a handoff record.** `13-VERDICT-MESSAGE.md` states the executor handed the body to the milestone master, which performs the relay (`relayed_at 2026-10-06T05:58:59Z`); there is no committed acknowledgement from the orchestrator. The plan only requires the relay time and recipient to be recorded, which they are. The launching agent states the relay happened.
- **W3: bookkeeping.** `REQUIREMENTS.md` still shows SPIKE-01 and SPIKE-02 as `[ ]` / `Pending` (SPIKE-03 is already N/A-deferred). The phase-complete step should flip them.
- **W4: filter has no automated test at HEAD** (grammar source and `verify-spike-evidence-filter.sh` deleted with the spike; recoverable from `1fec77a^`). Disclosed in the script header (WR-04).
- **W5: `verify-negative-controls.sh` Part 5 (WR-05)** was deliberately skipped per the fix report; not re-examined here.

## Gaps Summary

No gaps. The phase goal is achieved on its red branch: a reproducible, per-envelope verdict with measured small-envelope numbers exists and passes `--check`; the unmeasured sb envelope is a documented time-box outcome handled by the D-07 rule; the spike module and device scripts are gone and the disposition gate passes; `:core`/`:providers`/`:keystore` carry no ML dependency and the denial controls and hygiene checks pass.

---

_Verified: 2026-10-06_
_Verifier: Claude (gsd-verifier)_
