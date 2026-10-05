---
phase: 01-scaffold-publishing-proof
plan: 06
subsystem: testing
tags: [negative-controls, repo-hygiene, jitpack, phase-gate, ecosystem-docs]

requires:
  - phase: 01-02
    provides: "JitPack live probe script, jitpack.yml, passing rung-0 evidence"
  - phase: 01-03
    provides: "detekt gate, scanBannedConstructs, verifyNoDetektBaseline"
  - phase: 01-04
    provides: "structural gates (module graph, DI artifacts, explicit API, bytecode, core dependency allow-list), isolated-copy Metalava proof"
  - phase: 01-05
    provides: "testFixtures leak assertion, OkHttp matrix legs, vaeExpectedOkhttp lever"
provides:
  - "scripts/verify-negative-controls.sh: 56 planted-violation checks, each asserting the expected failure marker"
  - "scripts/verify-repo-hygiene.sh: BLD-07/BLD-08 and prohibition checks, prints HYGIENE OK"
  - "ECOSYSTEM.md and README.md document only the three per-module coordinates"
  - "evidence/phase-gate.txt and a rung=final live JitPack proof of 5fc723786b"
affects: [phase-02, phase-11]

plan_head_before: 85349d03fa198891fa90159a65425a46f1a7ff84

actuals:
  tokens: 10500
  tasks: 3
  commits: 3

tech-stack:
  added: []
  patterns:
    - "Every negative control asserts a marker string, so red-for-the-wrong-reason and stayed-green both fail"
    - "Plants are removed by a trap and touched build files are compared byte-for-byte to their backups"
    - "Hygiene script captures git output into variables and reads the staged set only, so unrelated dirty files never trip it"

key-files:
  created:
    - scripts/verify-negative-controls.sh
    - scripts/verify-repo-hygiene.sh
    - .planning/phases/01-scaffold-publishing-proof/evidence/phase-gate.txt
  modified:
    - ECOSYSTEM.md
    - README.md
    - .planning/phases/01-scaffold-publishing-proof/evidence/jitpack-probe.txt

key-decisions:
  - "The reference negative-controls script was used verbatim; it needed no change against the real tree (all task names and markers from plans 03-05 matched)"
  - "Only the probed SHA (5fc723786b) was pushed. The evidence, SUMMARY and state commits that follow stay local for the orchestrator, since they change no JitPack-built content"
  - "ECOSYSTEM.md cites the first-proven SHA 7f9db2294461 and points at the evidence file for the final SHA, because the docs commit necessarily precedes the final probe"

requirements-completed: [BLD-03, BLD-04, BLD-05, BLD-07, BLD-08, CLN-01, CLN-05]

coverage:
  - id: D1
    description: "Every gate (banned constructs, detekt, baseline, explicit API, core HTTP dependency, module graph, DI artifact, OkHttp floor, JVM 11 bytecode, testFixtures leak, per-leg OkHttp guard) goes red for the expected reason against a real planted violation; all plants are removed"
    requirement: CLN-05
    verification:
      - kind: integration
        ref: "scripts/verify-negative-controls.sh -> 'negative-control failures: 0', 56 ok lines, 0 FAIL lines; tree clean afterwards"
        status: pass
    human_judgment: false
  - id: D2
    description: "ECOSYSTEM.md and README.md list the per-module coordinates and never the two-segment aggregator coordinate"
    requirement: BLD-08
    verification:
      - kind: integration
        ref: "scripts/verify-repo-hygiene.sh -> HYGIENE OK; grep gate for the old coordinate is empty; planting the old coordinate makes the script fail"
        status: pass
    human_judgment: false
  - id: D3
    description: "Package root, ignore rules, no api.txt/fixture/baseline/tag, gradlew 100755, jitpack.yml not naming the app module, toolchain pins intact"
    requirement: BLD-07
    verification:
      - kind: integration
        ref: "scripts/verify-repo-hygiene.sh -> HYGIENE OK"
        status: pass
    human_judgment: false
  - id: D4
    description: "Phase gate green at the final commit including a live JitPack build of the final pushed SHA"
    requirement: BLD-03
    verification:
      - kind: integration
        ref: "./gradlew check (BUILD SUCCESSFUL); verify-api-dump.sh (API DUMP PROOF OK); jitpack-dry-run.sh (DRY RUN OK); jitpack-live-probe.sh 5fc723786b (LIVE PROBE PASS); recorded in evidence/phase-gate.txt"
        status: pass
    human_judgment: false

duration: 12min
completed: 2026-09-30
status: complete
---

# Phase 1 Plan 06: Negative Controls, Hygiene and Phase Gate Summary

**End-to-end planted-violation proof of every gate (56 ok, 0 failures), per-module coordinates in ECOSYSTEM.md and README.md, a repo-hygiene gate, and a green phase gate whose final SHA 5fc723786b was re-proven live on JitPack**

## Performance

- **Duration:** ~12 min (most of it the JitPack build wait and the 1-minute-per-run negative-control script)
- **Completed:** 2026-09-30T21:26Z
- **Tasks:** 3
- **Files modified:** 6 (3 created, 3 modified)

## Accomplishments
- `verify-negative-controls.sh` ran on the real tree: 15 source plants in each of core, providers and keystore (45), the baseline plant, 7 build-file plants and 3 matrix guard legs, for 56 `ok` lines and `negative-control failures: 0`. The explicit-API plant went red with "Visibility must be specified in explicit API mode" in all three modules. Matrix guard legs printed `OKHTTP_RUNTIME=4.12.0|5.2.1|5.5.0 expected=9.9.9`, proving both the gate and each leg's real runtime. Plants are gone and every touched build file is byte-identical afterwards.
- ECOSYSTEM.md now has a per-module coordinate table (core jar, providers jar with the OkHttp 4.12.0 floor, keystore aar), names the v1.1 `undo` and `voice-adapter` modules as unpublished, states the app module is never published, and records the Phase 1 proof SHA. "Current published tag: none" is kept. README.md drops "pre-scaffold" and lists the same coordinates.
- `verify-repo-hygiene.sh` machine-checks package root, ignore rules, forbidden files, tags, gradlew mode, jitpack.yml, toolchain pins and the staged set. Planting the old aggregator coordinate in README.md made it exit 1 with the specific message.
- Phase gate on commit `5fc723786b`: `./gradlew check` BUILD SUCCESSFUL, negative controls, `API DUMP PROOF OK`, `HYGIENE OK`, `DRY RUN OK`. Pushed and the live probe passed from an empty Gradle cache (`LIVE PROBE PASS ref=5fc723786b`, `rung=final` recorded), so the configuration-time gate scripts do not break JitPack.
- No tag, api.txt, fixture or baseline exists locally or on origin; nothing under graphify-out/ or .planning/graphs/ was committed since `506e394`; `01-VALIDATION.md` is unchanged.

## Task Commits

1. **Task 1: End-to-end negative controls** - `7bb6d09` (feat)
2. **Task 2: Per-module coordinates in docs plus repo-hygiene script** - `5fc7237` (docs)
3. **Task 3: Phase gate evidence and final live probe** - `ec8c2ce` (docs); the probed and pushed SHA is `5fc723786ba2fbe8161ff96e6643845d7af24558`

**Plan metadata:** recorded in the docs(01-06) commit that follows this file.

## Files Created/Modified
- `scripts/verify-negative-controls.sh` - copied unchanged from reference-assets; mode 100755
- `scripts/verify-repo-hygiene.sh` - new, mode 100755
- `ECOSYSTEM.md`, `README.md` - per-module coordinates, status text
- `.planning/phases/01-scaffold-publishing-proof/evidence/phase-gate.txt` - captured gate results
- `.planning/phases/01-scaffold-publishing-proof/evidence/jitpack-probe.txt` - appended live PASS and `rung=final`

## Decisions Made
- Reference script shipped as-is (no edit versus `reference-assets/verify-negative-controls.sh`): every task name and marker from plans 03-05 already matched.
- Only the code-bearing SHA was pushed. Later commits touch only `.planning/` evidence, SUMMARY and state, so they do not need a JitPack build.
- ECOSYSTEM.md cites the first-proven SHA `7f9db2294461...` and points to the evidence file for the final one (the docs commit precedes the final probe by construction).

## Deviations from Plan

None - plan executed exactly as written. The script edit versus reference assets is nil.

## Issues Encountered
None. The rung-0 fallback ladder and F2/F4/F5 were not needed; `jitpack.yml` carries no F2 flag so `EXPECT_MODULE_METADATA` stayed at its default.

## User Setup Required
None.

## Next Phase Readiness
- Phase 1 plans are all complete; the orchestrator owns verification and phase completion.
- **Reminder for the orchestrator (still open since plan 01):** `:keystore` uses `minSdk = 35` / compileSdk 36.1, copied from SB/YAT (RESEARCH A7) and never confirmed. Confirm before the v1.0.0 cut.
- Local commits after `5fc7237` (evidence, this SUMMARY, state/roadmap) are not pushed; push them with `git pull --rebase --autostash` first when convenient.
- Unrelated dirty files (.planning/graphs/, config.json, v1.0-MILESTONE-RUN.md, milestone.lock, state.json, .gsd-stage-plan.done.json) were left untouched and unstaged.

## Self-Check: PASSED

- Files exist: `scripts/verify-negative-controls.sh`, `scripts/verify-repo-hygiene.sh`, `evidence/phase-gate.txt`; commits `7bb6d09`, `5fc7237`, `ec8c2ce` found in `git log`.
- Acceptance criteria re-run: both scripts executable (100755) and `bash -n` clean; 56 ok lines and 0 FAIL; three "Visibility must be specified" lines; no `ZzPlant.kt`, no `config/detekt-baseline.xml`; `HYGIENE OK`; docs grep gate passes; contract and cross-repo untouched; `git tag --list` and remote tags empty; no forbidden files; probe last outcome `LIVE PROBE PASS ref=5fc723786b` after which `rung=final` is recorded and the SHA is an ancestor of origin/main.

---
*Phase: 01-scaffold-publishing-proof*
*Completed: 2026-09-30*
