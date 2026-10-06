---
phase: 17-run-level-undo
plan: 01
subsystem: build-infra
tags: [gradle, metalava, module-manifest, bash-gates, kotlin-jvm]

requires:
  - phase: 12
    provides: module graph + invariants gates this plan extends
provides:
  - ":undo module (kotlin.jvm, JVM 11, explicitApi, stdlib only) publishing voice-action-engine-undo"
  - "UndoReason open-set value class with twelve reason constants"
  - "verifyUndoZeroDeps gate and allowedEdges[:undo] = emptySet, both wired to check"
  - "scripts/modules.list manifest, scripts/lib/modules.sh reader, scripts/verify-module-manifest.sh gate with planted-module selftest"
  - "scripts/verify-api-seed.sh: proof that a header-only api.txt arms an executing, additions-only Metalava check"
  - ":sample required edge on :undo"
affects: [17-02, 17-03, 17-04, 17-05, 17-06, 17-07, 17-08, 17-09, 17-10, 18, 20]

actuals:
  tokens: 7500
  tasks: 3
  commits: 3
plan_head_before: b83aaaeeb34717f445ed058a9dc7fe217e737534
commits: 3

tech-stack:
  added: []
  patterns:
    - "One module manifest read by every script, with a consistency gate and a planted-module selftest"
    - "Header-only api.txt seed for an unreleased module, proven in an isolated copy"

key-files:
  created:
    - undo/build.gradle.kts
    - undo/api.txt
    - undo/src/main/kotlin/io/github/ygaray/voiceactionengine/undo/UndoReason.kt
    - scripts/modules.list
    - scripts/lib/modules.sh
    - scripts/verify-module-manifest.sh
    - scripts/verify-api-seed.sh
  modified:
    - settings.gradle.kts
    - gradle/invariants.gradle.kts
    - jitpack.yml
    - sample/build.gradle.kts

key-decisions:
  - "verifyUndoZeroDeps is registered ahead of the :core block in invariants.gradle.kts; coreAllowed and the :core block are untouched"
  - "Manifest gate adds a few checks beyond the plan's listed plants (bad header, allowedEdges entry, bad packaging / dependsOnCore value); selftest runs 11 cases"

patterns-established:
  - "Module plumbing is data (scripts/modules.list), not copy-pasted lists"

requirements-completed: [UNDO-01]

coverage:
  - id: D1
    description: ":undo module with zero dependencies beyond kotlin-stdlib, enforced by verifyModuleGraph and verifyUndoZeroDeps in check"
    requirement: UNDO-01
    verification:
      - kind: integration
        ref: "./gradlew :undo:check :undo:verifyUndoZeroDeps :undo:verifyModuleGraph :core:verifyCoreDependencyAllowlist (exit 0)"
        status: pass
    human_judgment: false
  - id: D2
    description: "Header-only api.txt seed: Metalava 0.5.1 executes the compat check against it and a removal goes red"
    requirement: UNDO-01
    verification:
      - kind: other
        ref: "scripts/verify-api-seed.sh undo -> API SEED OK module=undo executed=yes removal=red"
        status: pass
    human_judgment: false
  - id: D3
    description: "Module manifest + consistency gate; planted module, include, jitpack omission, missing api.txt and :core edge each go red by name"
    requirement: UNDO-01
    verification:
      - kind: other
        ref: "scripts/verify-module-manifest.sh --selftest -> MANIFEST SELFTEST OK cases=11"
        status: pass
    human_judgment: false

duration: 25min
completed: 2026-10-06
status: complete
---

# Phase 17 Plan 01: :undo scaffold, module manifest and header-only API seed Summary

**A dependency-free `:undo` module (stdlib only, gated twice in `check`) with its first public type `UndoReason`, a single module manifest every script reads with a planted-module selftest, and a proven header-only Metalava seed.**

## Performance

- **Duration:** about 25 min
- **Completed:** 2026-10-06
- **Tasks:** 3
- **Files modified:** 11 (7 created, 4 modified)

## Accomplishments
- `:undo` builds and passes `:undo:check` with nothing on its compile or runtime classpath but kotlin-stdlib and org.jetbrains:annotations. `verifyModuleGraph` (`allowedEdges[":undo"] = emptySet()`) and the new `verifyUndoZeroDeps` (rejects project components, non-allowlisted modules, and a vacuous classpath) both run under `check`.
- `UndoReason` ships as an open-set value class with the twelve snake_case wire constants.
- `scripts/modules.list` is the single module list; `verify-module-manifest.sh` cross-checks settings, build files, artifactIds, the jitpack install line, allowedEdges, dependsOnCore, tracked api.txt, packaging vs plugin, and the source dir. Its selftest plants 10 faults in an isolated copy and each goes red naming the item.
- D-09 stop rule did not trip: Metalava 0.5.1 accepts the header-only `undo/api.txt` on a kotlin.jvm module, the task executes (not skipped), and a baseline listing a class the source lacks fails with `Removed` (`verify-api-seed.sh undo`).
- `:sample` now requires an `:undo` edge, so the later end-to-end bridge proof cannot be silently dropped. `jitpack.yml` installs `:undo`.

## Task Commits

1. **Task 1: tracer, :undo module + UndoReason + zero-deps gate** - `24144c1` (feat)
2. **Task 2: module manifest, reader library, consistency gate + selftest, jitpack** - `9c22304` (feat)
3. **Task 3: header-only seed proof script, :sample :undo edge** - `74e1e84` (feat)

**Plan metadata:** committed separately (docs: complete plan)

## Files Created/Modified
- `undo/build.gradle.kts` - the `:undo` module, no dependencies except the JUnit test dependency
- `undo/api.txt` - header-only Metalava seed
- `undo/src/main/kotlin/io/github/ygaray/voiceactionengine/undo/UndoReason.kt` - open-set reason value class
- `gradle/invariants.gradle.kts` - `:undo` edge map entry, `verifyUndoZeroDeps`, ML-artifact gate extended to `undo`, `:sample` edge sets
- `settings.gradle.kts`, `jitpack.yml`, `sample/build.gradle.kts` - include, install line, sample edge
- `scripts/modules.list`, `scripts/lib/modules.sh`, `scripts/verify-module-manifest.sh`, `scripts/verify-api-seed.sh` - manifest tooling

## Decisions Made
- Placed the `verifyUndoZeroDeps` block immediately before the "Repo-wide checks, hosted on :core" block rather than after the ML block's closing brace; behavior is identical and `coreAllowed` stays byte-identical to 147a959.
- The manifest selftest exercises 11 cases (clean copy plus 10 plants) rather than the minimum 6, covering a bad api.txt header, a missing allowedEdges entry, and malformed rows (4 fields, bad dependsOnCore, bad packaging) with their manifest line number.

## Deviations from Plan

None - plan executed exactly as written. (Fixed one bug in my own new script during Task 2, an EXIT trap referencing a function-local variable; found and fixed before the Task 2 commit.)

## Issues Encountered
None blocking. `scripts/verify-repo-hygiene.sh` still hard-codes `core providers keystore` in its own module loop, so it does not yet cover `undo`; it passes, and wiring it to the manifest is outside this plan's file list.

## Verification
- `:undo:check :undo:verifyUndoZeroDeps :undo:verifyModuleGraph :core:verifyModuleGraph :providers:verifyModuleGraph :core:verifyCoreDependencyAllowlist` exit 0 (single low-memory Gradle invocation).
- `scripts/verify-module-manifest.sh` -> `MODULE MANIFEST OK modules=core,providers,keystore,undo`; `--selftest` -> `MANIFEST SELFTEST OK cases=11`.
- `scripts/verify-api-seed.sh undo` -> `API SEED OK module=undo executed=yes removal=red`; `verify-api-seed.sh core` exits 2.
- `scripts/verify-repo-hygiene.sh` -> `HYGIENE OK`.
- `core/api.txt`, `providers/api.txt`, `keystore/api.txt` and the `coreAllowed` block are byte-identical to 147a959.

## Known Stubs
None.

## Threat Flags
None.

## Self-Check: PASSED
