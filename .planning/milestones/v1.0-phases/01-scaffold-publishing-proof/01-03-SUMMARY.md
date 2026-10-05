---
phase: 01-scaffold-publishing-proof
plan: 03
subsystem: infra
tags: [detekt, static-analysis, source-scan, negative-controls, gradle, zero-baseline]

requires:
  - phase: 01-01
    provides: "Four-module build with minimal detekt config and buildUponDefaultConfig wiring"
  - phase: 01-02
    provides: "Live publishing proof (D-12 ordering: gates come after the publishing shape is proven)"
provides:
  - "config/detekt/detekt.yml: maxIssues 0, 8 ForbiddenImport entries, 6 ForbiddenComment regexes, LongParameterList tuning"
  - "Plain detekt over src/main, src/test and src/testFixtures of every module (no baseline, no type-resolution task)"
  - ":core:detektNegativeControls + :core:verifyDetektControls: exact (line, rule) set proof for ForbiddenImport and ForbiddenComment"
  - "gradle/invariants.gradle.kts (scanner half): scanBannedConstructs on all three published modules, verifyNoDetektBaseline and verifyInvariantScannerControls on :core"
  - "Six scanner negative controls with EXPECT headers, including the clean false-positive control"
affects: [01-04 structural half of invariants script, 01-05, 01-06 phase-gate plant script, all later phases]

plan_head_before: 5ee1b4e630c3cb94266275f80f15137bbc357a57

actuals:
  tokens: 11000
  tasks: 2
  commits: 2

tech-stack:
  added: []
  patterns:
    - "Gate rules proven by planted controls whose expected result is derived from the control file itself, with a vacuous guard"
    - "Syntax-only detekt (ForbiddenImport, ForbiddenComment) plus a literal-blanking source scanner for call and annotation bans"
    - "Repo-wide checks hosted once on :core inside the shared applied script"

key-files:
  created:
    - gradle/invariants.gradle.kts
    - config/negative-controls/detekt/ForbiddenImports.kt
    - config/negative-controls/runCatching.kt.txt
    - config/negative-controls/print.kt.txt
    - config/negative-controls/di-fq.kt.txt
    - config/negative-controls/packages.kt.txt
    - config/negative-controls/planning-ids.kt.txt
    - config/negative-controls/clean.kt.txt
  modified:
    - config/detekt/detekt.yml
    - build.gradle.kts
    - core/build.gradle.kts
    - providers/build.gradle.kts
    - keystore/build.gradle.kts

key-decisions:
  - "Scanner, not detekt, is the gate for runCatching, println/print, System.out/err, printStackTrace and FQ DI annotations (detekt call/annotation bans are silent no-ops without type resolution)"
  - "detekt control compared as an exact (line, rule) set derived from the control file, not a raw finding count"
  - "Planning-id regexes cover app ids only (T-xx-xx, WR-xx, Phase NN D-xx); requirement ids like BLD-04 are not matched"

patterns-established:
  - "Typed plugin tasks (detekt Detekt) live in the module build file; applied scripts use no plugin classes"
  - "In module build scripts, use explicit Pair(...) and import java.io.File (the `java` extension shadows the java package)"

requirements-completed: [BLD-04, CLN-01, CLN-05]

coverage:
  - id: D1
    description: "Plain detekt runs clean on :core, :providers, :keystore with default rules on, maxIssues 0, no baseline; testFixtures included in the source set"
    requirement: BLD-04
    verification:
      - kind: integration
        ref: "./gradlew :core:detekt :providers:detekt :keystore:detekt (BUILD SUCCESSFUL); no baseline property or *baseline*.xml"
        status: pass
    human_judgment: false
  - id: D2
    description: "ForbiddenImport (8 entries) and ForbiddenComment (TODO/FIXME/STOPSHIP and app planning ids) bite, proven by exact (line, rule) set equality, with vacuous guards"
    requirement: CLN-01
    verification:
      - kind: integration
        ref: "./gradlew :core:verifyDetektControls; mutation run removing 'dagger.*' reported missing (4, ForbiddenImport)"
        status: pass
    human_judgment: false
  - id: D3
    description: "scanBannedConstructs flags runCatching, print/println, System.out/err, printStackTrace, FQ DI annotations, forbidden packages and planning ids in comments only, with no false positives on the clean control"
    requirement: CLN-05
    verification:
      - kind: integration
        ref: "./gradlew :core:verifyInvariantScannerControls (6 controls match their EXPECT headers)"
        status: pass
    human_judgment: false
  - id: D4
    description: "Planting runCatching in :core/src/main fails :core:scanBannedConstructs with 'Banned constructs in :core'; baseline file or baseline assignment fails verifyNoDetektBaseline"
    requirement: BLD-04
    verification:
      - kind: integration
        ref: "plant-and-remove smoke (SMOKE_OK); planted config/detekt-baseline.xml and a baseline assignment in providers/build.gradle.kts both rejected"
        status: pass
    human_judgment: false
  - id: D5
    description: "Full ./gradlew check green with detekt, detekt controls, scanner controls, baseline check and scan attached (dry-run lists all four :core tasks)"
    requirement: BLD-04
    verification:
      - kind: integration
        ref: "./gradlew check (BUILD SUCCESSFUL); ./gradlew :core:check --dry-run"
        status: pass
    human_judgment: false

duration: 10min
completed: 2026-09-30
status: complete
---

# Phase 1 Plan 03: Zero-Baseline Static Gates Summary

**detekt with real invariant rules (imports, comments, tuned defaults, no baseline) plus a literal-blanking source scanner for what detekt cannot see syntax-only, each rule proven to bite by planted controls and all attached to `check` on every published module.**

## Performance

- **Duration:** about 10 min
- **Completed:** 2026-09-30
- **Tasks:** 2
- **Files:** 8 created, 5 modified

## Accomplishments

- `config/detekt/detekt.yml` carries `maxIssues: 0`, ForbiddenImport for `okhttp3.internal.*`, `mockwebserver3.*`, `okhttp3.coroutines.*`, `android.util.Log`, `dagger.*`, `javax.inject.*`, `jakarta.inject.*`, `androidx.hilt.*`, ForbiddenComment for `TODO:`, `FIXME:`, `STOPSHIP:` and the three app planning-id regexes, and LongParameterList `constructorThreshold: 8` with `ignoreDefaultParameters: true`.
- Root build sets the detekt source set to `src/main/kotlin`, `src/test/kotlin`, `src/testFixtures/kotlin`; no baseline property anywhere.
- `verifyDetektControls` derives the expected (line, rule) pairs from the control file and requires set equality; it fails as vacuous if no expected pair exists or the XML is absent or empty. A mutation run (removing `dagger.*` from the config) produced `missing: [(4, ForbiddenImport)]`, so the control does bite.
- `scanBannedConstructs` runs under `check` on core, providers and keystore; `verifyInvariantScannerControls` and `verifyNoDetektBaseline` run once on core. All six scanner controls match their EXPECT headers, including `clean.kt.txt` (`(none)`).
- Smoke results: planting `runCatching` in `:core/src/main` failed with `Banned constructs in :core ... [runCatching]`; a planted `config/detekt-baseline.xml` and a `baseline =` assignment in `providers/build.gradle.kts` were each rejected by `verifyNoDetektBaseline`. The plant was removed and the tree left clean.

## Task Commits

1. **Task 1: detekt gate, testFixtures source set, exact-set detekt control** - `4c5b119` (feat)
2. **Task 2: scanBannedConstructs, scanner controls, no-baseline check** - `9342dab` (feat)

## Findings recorded per the plan output spec

- **detekt default rules needing fix or tuning:** none on existing files (the marker objects and build outputs produced zero findings). The only tuning is LongParameterList (CLAUDE.md prescription, justified in the file).
- **ForbiddenComment multiplicity:** one finding per offending comment line on the control (4 comment lines gave 4 ForbiddenComment findings, lines 9-12). The other control finding was `InvalidPackageDeclaration` on line 1 (default rule, deliberately ignored by the comparison, which tracks only ForbiddenImport and ForbiddenComment). The exact-set comparison is used instead of counts so this kind of incidental finding cannot break the gate.

## Deviations from Plan

None - plan executed exactly as written. Two small build-script fixes were needed while writing the detekt verify task (Rule 3, inside Task 1, no separate commit): in `core/build.gradle.kts` the `java` extension shadows the `java.io` package, so `File` is imported explicitly, and Pair construction is explicit to satisfy Kotlin script type inference.

Process note, not a deviation: per the spawn prompt, commits went directly on `main` (branching_strategy none).

The TDD order for Task 2 (controls first, then scanner) was followed within one working pass; the plan specifies a single commit for the task, so there is no separate RED commit.

## Issues Encountered

None.

## Authentication Gates

None.

## Next Phase Readiness

- Plan 04 can append the structural half (bytecode level, explicit API, module graph, core dependency allowlist) to `gradle/invariants.gradle.kts`; the scanner half and the `project.name == "core"` section are in place (append the structural tasks before or alongside the core block as needed).
- The optional DI-artifact dependency denial from the reference assets is not part of this plan and was not added.
- Still open from earlier plans: `:keystore` minSdk 35 / compileSdk 36.1 needs confirmation before the v1.0.0 cut.

## Self-Check: PASSED

- All created files exist on disk (gradle/invariants.gradle.kts, config/negative-controls/detekt/ForbiddenImports.kt, six .kt.txt controls).
- Commits `4c5b119` and `9342dab` exist on main; `git rev-list --count` from the recorded base gives 2.
- Both tasks' automated checks and acceptance criteria re-run and passing; `./gradlew check` green; `git show --name-only` of both commits lists only files from the task `<files>` lists (nothing under graphify-out or .planning/graphs); no tag, api.txt or fixture change; no leftover `ZzPlant.kt` or baseline file.

---
*Phase: 01-scaffold-publishing-proof*
*Completed: 2026-09-30*
