---
phase: 17-run-level-undo
plan: 03
subsystem: build-infra
tags: [bash-gates, module-manifest, negative-controls, hygiene, api-dump]

requires:
  - phase: 17-01
    provides: "scripts/modules.list manifest, scripts/lib/modules.sh reader, verifyUndoZeroDeps and the :undo module"
provides:
  - "verify-repo-hygiene.sh reading the module manifest (package root, main source, ECOSYSTEM coordinate, tracked api.txt)"
  - "ECOSYSTEM.md :undo coordinate row, still marked unpublished until v1.1.0"
  - "api-dump-isolated.sh, verify-api-dump.sh, review-api-surface.sh looping over the manifest with kotlinPackage-based package checks and corrected headers"
  - "verify-negative-controls.sh and verify-ml-denial-controls.sh looping over the manifest, plus three :undo zero-dependency plants"
affects: [17-09, 17-10, 18, 20]

actuals:
  tokens: 5400
  tasks: 3
  commits: 3
plan_head_before: d9a09ae246c89ab21bd9b6e72659c6ec21be66c8
commits: 3

tech-stack:
  added: []
  patterns:
    - "Every module loop in the gate scripts is data (scripts/modules.list), never a literal list"
    - "Scripts that cd into a non-git isolated copy export VAE_MODULES_FILE from the real tree first"

key-files:
  created: []
  modified:
    - scripts/verify-repo-hygiene.sh
    - ECOSYSTEM.md
    - scripts/api-dump-isolated.sh
    - scripts/verify-api-dump.sh
    - scripts/review-api-surface.sh
    - scripts/verify-negative-controls.sh
    - scripts/verify-ml-denial-controls.sh

key-decisions:
  - "The api.txt-missing negative control plants the absence itself (backup, remove, expect red, restore) because every module has tracked its api.txt since v1.0.0"
  - "review-api-surface.sh stays a review of core's dump only; other modules are reviewed from api-dump-isolated.sh output in their phase's surface review"

patterns-established:
  - "Planted manifest row zz proves a new module cannot be silently skipped by hygiene"

requirements-completed: [UNDO-01]

coverage:
  - id: D1
    description: "Hygiene covers every manifest module; a planted manifest row zz goes red naming zz three times (no main source, ECOSYSTEM lacks the artifact, api.txt not tracked)"
    requirement: UNDO-01
    verification:
      - kind: other
        ref: "scripts/verify-repo-hygiene.sh -> HYGIENE OK; planted VAE_MODULES_FILE run exits 1 with the three zz violations; scripts/verify-docs-coverage.sh --only C02 -> OK"
        status: pass
    human_judgment: false
  - id: D2
    description: "Dump and review scripts read the manifest, refuse an --out under undo/, and no longer claim the real tree never holds an api.txt"
    requirement: UNDO-01
    verification:
      - kind: other
        ref: "bash -n on all three; --out undo/x refused by api-dump-isolated.sh and review-api-surface.sh with 'must not lie under undo/'; negative greps for the stale phrases print 0"
        status: pass
    human_judgment: false
  - id: D3
    description: "Negative and ML-denial controls loop over the manifest; three :undo plants (project edge, library dependency, core testFixtures) are in place and await execution in 17-10"
    requirement: UNDO-01
    verification:
      - kind: other
        ref: "bash -n on both scripts; greps for the three labels, the verifyUndoZeroDeps marker, kotlinPackage, compileReleaseKotlin and the planted api.txt backup all match; scripts/verify-module-manifest.sh -> MANIFEST OK"
        status: pass
    human_judgment: true

duration: 25min
completed: 2026-10-06
status: complete
---

# Phase 17 Plan 03: Manifest-driven gate scripts Summary

**Hygiene, API-dump, surface-review, negative-control and ML-denial scripts now read `scripts/modules.list`, so `:undo` cannot be silently skipped, and three `:undo` zero-dependency plants are ready for the quiet-window run.**

## Performance

- **Duration:** about 25 min
- **Completed:** 2026-10-06
- **Tasks:** 3
- **Files modified:** 7

## Accomplishments
- Tracer: `verify-repo-hygiene.sh` reads the manifest for all of its module loops. A planted row `zz` makes it exit 1 with exactly the three expected violations. ECOSYSTEM.md lists `:undo` and keeps the "not yet published, first tag v1.1.0" statement.
- The three dump and review scripts use `vae_modules` and the `kotlinPackage` column, print `<module>=<lines>` for every module, and their headers describe the post-v1.0.0 state (committed baselines, never rewritten).
- `verify-negative-controls.sh` loops every source plant and the api.txt-missing control over all manifest modules (aar modules keep `compileReleaseKotlin`, the JDK-16 API loop runs over jar modules only). Part 2 gains the three `:undo` plants with markers matching the gates' own messages.
- `verify-ml-denial-controls.sh` Part A loops over the manifest.
- No Gradle was run, per the plan.

## Task Commits

1. **Task 1: hygiene tracer and ECOSYSTEM row** - `0044946` (feat)
2. **Task 2: dump and surface-review scripts** - `a3366f5` (feat)
3. **Task 3: negative and ML-denial controls** - `cf95ccd` (feat)

**Plan metadata:** committed separately (docs: complete plan)

## Decisions Made
- The api.txt-missing control plants the absence for each module, since the old form (`-PvaeAssumeReleased` on a tree that tracks api.txt) would report "stayed GREEN".

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Hygiene died silently when a manifest module had no source directory**
- **Found during:** Task 1 (planted `zz` proof)
- **Issue:** `find zz/src/main/kotlin | wc -l` exits 1 under `set -o pipefail`, so `set -e` aborted the script with no output instead of reporting "zz has no main Kotlin source". The old literal list never hit this because its three directories always existed.
- **Fix:** wrapped the find as `{ find ... || true; } | wc -l`.
- **Files modified:** scripts/verify-repo-hygiene.sh
- **Verification:** the planted run now prints all three violations and exits 1; the real tree stays HYGIENE OK.
- **Committed in:** 0044946

**2. [Rule 3 - Blocking] The manifest reader cannot find the manifest from inside the isolated copy**
- **Found during:** Task 2
- **Issue:** `modules.sh` locates the manifest with `git rev-parse --show-toplevel`, but `api-dump-isolated.sh` and `verify-api-dump.sh` call `vae_module_field` after `cd "$COPY"`, which is not a git repository.
- **Fix:** both scripts export `VAE_MODULES_FILE` (defaulting to the real tree's manifest) before sourcing the reader. I checked the reader from a non-git directory with the variable set.
- **Committed in:** a3366f5

---

**Total deviations:** 2 auto-fixed (1 bug, 1 blocking). Impact: none on scope.

## Issues Encountered
None beyond the deviations above.

## Verification
- Plan-end command: `scripts/verify-repo-hygiene.sh && scripts/verify-module-manifest.sh && bash -n` on the five edited scripts, exit 0.
- Not run here by design: the edited Gradle-running scripts (`api-dump-isolated.sh`, `verify-api-dump.sh`, `review-api-surface.sh`, `verify-negative-controls.sh`, `verify-ml-denial-controls.sh`). They run in 17-09 (surface review) and the quiet-window plan 17-10. The marker text of the three `:undo` plants is taken verbatim from the gate messages in `gradle/invariants.gradle.kts`, but only an actual run proves it. Plant (c) in particular relies on `verifyModuleGraph` seeing a `testFixtures(project(":core"))` dependency as a project edge to `:core`.

## Self-Check: PASSED
All seven modified files exist; commits 0044946, a3366f5 and cf95ccd are present in the log.
