---
phase: 01-scaffold-publishing-proof
plan: 04
subsystem: infra
tags: [gradle, invariants, bytecode, explicit-api, module-graph, okhttp-floor, metalava, zero-baseline]

requires:
  - phase: 01-03
    provides: "gradle/invariants.gradle.kts scanner half and the project.name == core block"
  - phase: 01-01
    provides: "Four-module build, guarded apiDump/apiCheck aliases, inert :sample with the three project edges"
provides:
  - "verifyBytecodeLevel: class-file major 55 on the jar and on the AAR's nested classes.jar, fails on zero classes"
  - "verifyExplicitApiStrict: reflection read of the Kotlin extension, expected Strict"
  - "verifyModuleGraph: published-module edge allowlist plus required :sample -> {:providers, :keystore} edges"
  - "verifyCoreDependencyAllowlist: :core compile and runtime classpath allowlist"
  - "verifyOkHttpCompileFloor: every okhttp/okhttp-jvm/mockwebserver component exactly 4.12.0 on :providers compile classpaths"
  - "verifyNoDiArtifacts: five DI groups denied on all three published modules"
  - "scripts/verify-api-dump.sh: isolated-copy Metalava proof (dump, additive passes, removal fails)"
affects: [01-05, 01-06, all later phases]

plan_head_before: 451cb7347a5ab7852ca2839dcf4aa29e77d12164

actuals:
  tokens: 5500
  tasks: 2
  commits: 2

tech-stack:
  added: []
  patterns:
    - "Structural gates read resolved classpaths and produced artifacts at execution time; no override knob exists that could lower a structural expectation"
    - "Name-specific sections (core, providers) follow the generic section in the one shared applied script"
    - "Metalava proof runs in a temp copy so the real tree never holds an api.txt before the v1.0.0 cut"

key-files:
  created:
    - scripts/verify-api-dump.sh
  modified:
    - gradle/invariants.gradle.kts

key-decisions:
  - "No allowlist entry had to be added: the :core classpath (kotlin-stdlib, annotations, coroutines-core, serialization) is fully covered by the plan's initial set"
  - "verify-api-dump.sh used verbatim from reference-assets; the run exposed no defect"

patterns-established:
  - "Plant-and-restore smokes back up the build file, plant a real violation, assert the marker text, restore, then cmp byte-for-byte"

requirements-completed: [BLD-01, BLD-02, BLD-05]

coverage:
  - id: D1
    description: "check enforces the one-way module graph: published-module edge allowlist and the required/allowed :sample edges, non-vacuous when :sample is missing"
    requirement: BLD-01
    verification:
      - kind: integration
        ref: "./gradlew verifyModuleGraph; plant of project(\":core\") in place of project(\":keystore\") in sample/build.gradle.kts failed with 'missing required project edges [:keystore]', file restored byte-for-byte"
        status: pass
    human_judgment: false
  - id: D2
    description: ":core classpath allowlist rejects an HTTP artifact"
    requirement: BLD-01
    verification:
      - kind: integration
        ref: "plant of api(okhttp 4.12.0) in core/build.gradle.kts failed :core:verifyCoreDependencyAllowlist with 'non-allow-listed' naming okhttp on compile and runtime classpaths, file restored byte-for-byte"
        status: pass
    human_judgment: false
  - id: D3
    description: "JVM 11 class files (major 55) asserted on core jar, providers jar and keystore AAR classes.jar"
    requirement: BLD-02
    verification:
      - kind: integration
        ref: "./gradlew verifyBytecodeLevel (all three modules pass; zero-class case throws vacuous)"
        status: pass
    human_judgment: false
  - id: D4
    description: "explicitApi Strict asserted on all three modules, OkHttp compile floor exactly 4.12.0, DI artifacts denied"
    requirement: BLD-05
    verification:
      - kind: integration
        ref: "./gradlew verifyExplicitApiStrict :providers:verifyOkHttpCompileFloor verifyNoDiArtifacts; full ./gradlew check BUILD SUCCESSFUL"
        status: pass
    human_judgment: false
  - id: D5
    description: "Metalava dumps all three modules, accepts an additive public class and rejects a removal with 'Removed', real tree has no api.txt"
    requirement: BLD-05
    verification:
      - kind: integration
        ref: "scripts/verify-api-dump.sh printed 'API DUMP PROOF OK'; git ls-files -co --exclude-standard -- '*api.txt' empty"
        status: pass
    human_judgment: false

duration: 9min
completed: 2026-09-30
status: complete
---

# Phase 1 Plan 04: Structural Invariant Gates and Metalava Proof Summary

**Six structural gates (JVM 11 class files, strict explicit API, one-way module graph with required :sample edges, :core classpath allowlist, OkHttp 4.12.0 floor, DI-artifact denial) attached to `check` in the shared invariants script, plus an isolated-copy script proving Metalava dump/additive/removal behavior without ever writing an api.txt into the real tree.**

## Performance

- **Duration:** about 9 min
- **Completed:** 2026-09-30
- **Tasks:** 2
- **Files:** 1 created, 1 modified

## Accomplishments

- `gradle/invariants.gradle.kts` now holds both halves. Generic section on all three published modules: `verifyBytecodeLevel` (`expectedMajor = 55`, jar or AAR `classes.jar`, skips `META-INF/versions/`, fails on zero classes), `verifyExplicitApiStrict` (reflection), `verifyModuleGraph`, `verifyNoDiArtifacts` (release classpaths on `:keystore`). `:core` block gained `verifyCoreDependencyAllowlist`; a new `providers` block holds `verifyOkHttpCompileFloor` (fails vacuous if no OkHttp component is found).
- `verifyModuleGraph` also reads `:sample`'s declared `implementation` project edges at execution time: requires `:providers` and `:keystore`, allows `:core`, and fails (not passes) if `:sample` or its configuration cannot be found. `:sample` itself never applies the script.
- No property-based override exists (`grep -En 'findProperty|gradleProperty'` returns nothing).
- `scripts/verify-api-dump.sh` (mode 100755) ran to `API DUMP PROOF OK`.

## Task Commits

1. **Task 1: Structural gates** - `094e479` (feat)
2. **Task 2: Metalava wiring proof script** - `a1861a0` (feat)

## Findings recorded per the plan output spec

- **Empty-surface dump worked.** Step (a) produced `core/api.txt`, `providers/api.txt` and `keystore/api.txt`, each exactly 1 line (the `Signature format` header). The "EMPTY-SURFACE DUMP FAILED" contingency did not trigger.
- **Allowlist entries added:** none beyond the plan's initial set; the resolved :core compile and runtime classpaths fit entirely inside it.
- Steps b, c, d passed: planted `ZzApiProbe` appeared in every module's dump, `apiCheck` was green after the dump and after adding `ZzApiAdded`, and deleting `ZzApiProbe` made `apiCheck` fail with `Removed`.

## Smoke results (plant and restore)

- Planted `api("com.squareup.okhttp3:okhttp:4.12.0")` in `core/build.gradle.kts`: `:core:verifyCoreDependencyAllowlist` exit 1, `non-allow-listed`, offenders listed for `compileClasspath` and `runtimeClasspath`. File restored, `cmp` identical.
- Replaced `project(":keystore")` with `project(":core")` in `sample/build.gradle.kts`: `verifyModuleGraph` exit 1, `:sample is missing required project edges [:keystore]`. File restored, `cmp` identical.

## Deviations from Plan

None - plan executed exactly as written. Small adaptation, not a deviation: the script-level `import` lines (`java.io.File`, `java.util.zip.ZipFile`, `java.util.zip.ZipInputStream`) were added at the top of the invariants script, and module path / classpath handles are captured into locals outside `doLast` where practical. The reference code's D-06/D-08 semantics are unchanged.

## Issues Encountered

None.

## Authentication Gates

None.

## Next Phase Readiness

- Plan 05 can rely on `:providers:verifyOkHttpCompileFloor`; plan 06's negative-control script can grep the marker texts "Non-JVM-11 class files", "expected Strict", "forbidden project dependencies", "non-allow-listed", "must be 4.12.0", "resolves DI artifacts".
- No api.txt exists in the tree; enforcement stays off until the Phase 11 cut commits the dumps. No tag, fixture or baseline was touched.
- Still open from earlier plans: `:keystore` minSdk 35 / compileSdk 36.1 needs confirmation before the v1.0.0 cut.

## Self-Check: PASSED

- `gradle/invariants.gradle.kts` and `scripts/verify-api-dump.sh` exist; the script is mode 100755.
- Commits `094e479` and `a1861a0` exist on main; `git rev-list --count` from the recorded base gives 2.
- Acceptance criteria re-run: six tasks registered and listed by `:core:check --dry-run` / `:providers:check --dry-run`; `sampleRequiredEdges`/`sampleAllowedEdges` and the `sample/build.gradle.kts` edges present; no lowering knob; both build files byte-identical after the smokes; `./gradlew check` BUILD SUCCESSFUL; `git show --name-only` of both commits lists only the task files; `git ls-files -co --exclude-standard -- '*api.txt'` empty.
