---
phase: 20-cut-v1-1-0
plan: 06
subsystem: release-tooling
tags: [ver-07, rt-02, rt-05, d-01, d-02, d-03, d-04, gate-12, gate-11, release-cut]

requires:
  - phase: 20-02
    provides: the five final api.txt dumps (undo and voice-adapter are real dumps, not seeds)
  - phase: 20-05
    provides: the v1.1.0 waiver packet that the waiver controls now run over
provides:
  - gate 12 baseline half that treats a module absent from the previous tag as new in this release (directory-keyed, real-dump test, never for a patch release)
  - diagnostic gate `api-baseline <tag>` (no Gradle, outside the preflight gate list)
  - scripts/verify-stt-confinement.sh enforced inside gate 11 (RT-05)
  - five new selftest controls, all red or green for the right reason
  - scripts/verify-binary-diff.sh, the D-02 javap descriptor-diff helper with a javac-built selftest
  - tooling-proof.txt with the final lines of every proof
affects: [20-07, 20-08, 20-09, 20-10]

actuals:
  tokens: 7650
  tasks: 3
  commits: 4
plan_head_before: 997b33d9e6beb9a9127218ae98b44dfec0e41134

tech-stack:
  added: []
  patterns:
    - "new-module baseline keyed on the module directory in the previous tag, with a real-dump test (more than the header line, own package line)"
    - "control helper retag_prior_without: moves the previous release tag in a control's own clone and local bare remote"

key-files:
  created:
    - scripts/verify-binary-diff.sh
    - .planning/releases/v1.1.0/evidence/tooling-proof.txt
  modified:
    - scripts/release-cut.sh

key-decisions:
  - "Newness is decided by `git cat-file -e <prior>:<module>` (the directory), so a released module whose api.txt vanished still fails with 'no baseline'"
  - "The package-line test matches fields exactly (`package io.github.ygaray.voiceactionengine.<kotlinPackage>`), because real dumps write the line with a trailing ` {`"
  - "The stt confinement check lives inside gate_hygiene; the 15-gate list, GATE_ORDER and the PREFLIGHT OK gates= line are untouched"

requirements-completed: []  # VER-07 (cut v1.1.0) spans all 12 plans; not complete after 20-06

duration: n/a
completed: 2026-10-07
status: complete
---

# Phase 20 Plan 06: Release tooling for the two new modules Summary

**Gate 12 now recognises undo and voice-adapter as new in v1.1.0 (directory-keyed, real committed dump required, patch releases can never add a module), gate 11 enforces the `:stt` confinement, five new controls prove both for the right reasons, and a tested javap descriptor-diff helper exists for D-02 before the wiring SHA.**

## Accomplishments

- **Tracer (Task 1, 740ec5d).** `scripts/release-cut.sh gate api-baseline v1.1.0` on the real repository exits 0 and prints `API NOTE: new in this release (no baseline): undo voice-adapter` then `GATE OK api-baseline`. Before the plan the same half failed with `undo/api.txt is not in the previous release v1.0.1, so there is no baseline`. `gate api-baseline v1.0.2` still fails (core/api.txt differs from v1.0.1, patch release). `vae_pkg_of` (column 4 of HEAD's manifest) sits beside `vae_artifact_of`; usage text and header list the new gate.
- **RT-05 and controls (Task 2, 19ad449).** `gate_hygiene` runs `verify-stt-confinement.sh` as a third check and fails on a non-zero exit or a missing `STT CONFINEMENT OK`. New controls: `api-check-new-module-seed`, `api-check-new-module-populated`, `api-check-baseline-deleted`, `api-check-new-module-patch`, `hygiene-stt-confinement`, sharing one helper `retag_prior_without`. The partial selftest (17 controls: the five new plus the waiver, prefreeze, api-check and hygiene ones) ended `RELEASE SELFTEST PARTIAL (SELFTEST_ONLY set) happy=0 negatives=14 positives=3`, exit 0 (read from the detached job, no pipe), no FAIL line.
- **D-02 helper (Task 3, 7f11d0e).** `scripts/verify-binary-diff.sh --old --new --api [--out]` plus `--selftest` (`BINARY DIFF SELFTEST OK cases=13`). Sanity runs old = new on the real built core jar (207 classes), keystore AAR (11) and undo jar (19) printed OK with removed=0 and no api/jar disagreement, which also proves the api.txt-to-binary-name mapping against real dumps.
- **D-03 and D-04 verified, not re-implemented.** No script names the archived Phase 11 directory (0 matches), `RELEASE_DIR=".planning/releases/v1.1.0"`, `verify-release-manifest.sh` ends `RELEASE MANIFEST PROOF OK cases=8`, `verify-module-manifest.sh` prints `MODULE MANIFEST OK modules=core,providers,keystore,undo,voice-adapter` (its selftest `MANIFEST SELFTEST OK cases=11`), `verify-stt-confinement.sh` prints `STT CONFINEMENT OK checks=6` (selftest `cases=12`), `gate hygiene` and `gate leak` print GATE OK. Read-through of the consumers: gate 7 (`is_module_api`), gate 15 (`published_versions.py` with the manifest), the tag message loop in `run_cut`, `jitpack-live-probe.sh`, `api-dump-isolated.sh`, `jitpack-dry-run.sh`, and the hygiene scripts all read `scripts/modules.list` or `scripts/lib/modules.sh`. The only hand-typed artifact names left are in `jitpack-consumer-probe.sh` and `agent-wiring-test.sh`, which assert a per-consumer dependency SHAPE (jar to jar, AAR to jar, undo alone, adapter alone), not a module list; they already cover all five modules and were left alone.

## Task Commits

1. **Task 1 (tracer): gate 12 new-module branch + gate api-baseline** - `740ec5d` (feat)
2. **Task 2: RT-05 in gate 11, five controls, D-03/D-04 proofs, partial selftest** - `19ad449` (feat)
3. **Task 3: scripts/verify-binary-diff.sh** - `7f11d0e` (feat)

**Plan metadata:** the docs commit that carries this SUMMARY, STATE.md and ROADMAP.md.

## Files Created/Modified

- `scripts/release-cut.sh` - `vae_pkg_of`, new-module branch in `api_baseline_check`, `gate_api_baseline`, stt confinement in `gate_hygiene`, `retag_prior_without`, five controls, `CONTROL_ORDER`, header and usage.
- `scripts/verify-binary-diff.sh` - new, executable; no Gradle, writes only under its own temp dir.
- `.planning/releases/v1.1.0/evidence/tooling-proof.txt` - final lines of every proof run.

## Decisions Made

See key-decisions. `api.txt` files were not touched (plan 20-02 owns them); `CROSS-REPO-SCOPE-CONTRACT.md` untouched; no push, no tag, no stage marker.

## Deviations from Plan

**1. [Rule 1 - Bug in the plan's wording] Package-line test matches the real dump shape**
- **Found during:** Task 1
- **Issue:** The plan describes "the line `package io.github.ygaray.voiceactionengine.<pkg>`", but real dumps write `package io.github.ygaray.voiceactionengine.undo {`. A whole-line match would reject a real dump.
- **Fix:** awk matches field 1 = `package` and field 2 = the exact package name, so `undo` cannot match `undoX` and `voiceadapter` is matched exactly.
- **Files modified:** scripts/release-cut.sh. **Committed in:** 740ec5d.

**2. [Rule 1 - Control precision] baseline-deleted marker made specific**
- **Found during:** Task 2
- **Issue:** the marker `is not in the previous release` is a substring of the new-module messages too (`<m> is not in the previous release <prior> and <tag> is a patch release`), so the control could have passed for the wrong reason.
- **Fix:** the marker is `undo/api.txt is not in the previous release v1.0.0, so there is no baseline`. The partial selftest was re-run after this change; the evidence file holds the second run.
- **Committed in:** 19ad449.

**3. [Acceptance wording] A header comment containing the gate-list variable name was reworded**
- The plan's acceptance grep `git diff -U0 | grep '^[-+].*GATE_ORDER'` must print nothing; my first comment on `gate_api_baseline` contained the name. Reworded before the Task 1 commit.

**4. [Process] Controls and automated verify blocks run as plain commands**
- Per the orchestrator's permission rule, the plan's `bash -c '...'` verify blocks were run as separate plain commands; the partial selftest was run as a detached `job` (output in a log, exit status read from the job, never piped).

**Total deviations:** 2 auto-fixed (Rule 1), 2 process/wording notes. **Impact:** none on scope; all changes are inside scripts/ and the evidence file.

## Issues Encountered

- **Foreign commit counted by the measure:** `git rev-list --count 997b33d..HEAD` is 4, not 3. `1d934fe docs(20): RT-10 tag cut waived from owner approval` (a one-file CONTEXT.md edit by the orchestrator) landed on the branch while this plan ran. My own commits are the three above. `actuals.commits` records the measured 4.
- **Host readings (R2, outside a quiet window, so a failing swap check is a warning):** before the first partial run MemAvailable 13,698,556 kB, SwapTotal 2,097,148 kB, SwapFree 884 kB; before the second run MemAvailable 11,706,768 kB, SwapFree 2,184 kB. The swap check failed in both: swap is essentially full even though the operator reset it earlier, which is the condition memory `vae-release-cut-host-oom` warns about. The 5 GiB memory floor was met, no other Gradle process was running, and each partial run (one `apiDump` in the sandbox) finished in about 2 minutes with no earlyoom kill. **The master should ask the operator for another swap reset before any quiet window (20-07 onwards).**
- The sandbox `status:` guard compares the real repo's non-.planning status before and after. A new script created while a selftest runs would trip it, so the helper was drafted in the scratchpad and installed before the second run.

## Next Phase Readiness

- All script work for the phase is in; nothing in `scripts/` should change after plan 20-07 fixes W (gate 7). Plans 20-07..20-12 reference only scripts that now exist (`release-cut.sh`, `verify-binary-diff.sh`, `verify-docs-coverage.sh`, `jitpack-live-probe.sh`, `agent-wiring-test.sh`, `verify-stt-confinement.sh`, `verify-module-manifest.sh`, `verify-release-manifest.sh`, `verify-repo-hygiene.sh`, `verify-sample-device-guard.sh`, `jitpack-consumer-probe.sh`); nothing is missing.
- The full `selftest all` is NOT run here; it belongs to quiet window 20-01 (plan 20-07). Its happy path (v1.0.1 patch over a v1.0.0 sandbox whose previous tag holds all five dumps) does not exercise the new branch; gate 11 now also runs the stt confinement in that sandbox, which the real-repo `gate hygiene` run shows green.
- Plan 20-08 calls `verify-binary-diff.sh --old <v1.0.1 artifact> --new <W artifact> --api <v1.0.1 api.txt> --out <file>`; exit 1 means removed members, exit 2 means an api/artifact disagreement or bad input. Members that are Kotlin-mangled (`$core`, `$providers`, `$keystore_release`, undo, voice_adapter) are reported as `INTERNAL-REMOVED` and must be explained one by one.
- `scripts/verify-api-seed.sh` is unused and exits 2 for the new modules, as the plan said; left alone.

## Known Stubs

None.

## Self-Check: PASSED

- Files found: `scripts/verify-binary-diff.sh`, `scripts/release-cut.sh`, `.planning/releases/v1.1.0/evidence/tooling-proof.txt`.
- Commits found: 740ec5d, 19ad449, 7f11d0e.
- Acceptance: `gate api-baseline v1.1.0` GATE OK with the NOTE; no `GATE_ORDER` line in the diff; 5 new `ctl_` functions; tooling-proof.txt has the PARTIAL line, the five `ok` lines and no `FAIL` line; `bash -n` clean; the helper selftest ends `BINARY DIFF SELFTEST OK cases=13`; `grep -c gradlew scripts/verify-binary-diff.sh` is 0; `git status --porcelain -- . ':!.planning' ':!graphify-out' ':!.gsd'` printed nothing after the Task 3 commit.
