---
phase: 17-run-level-undo
plan: 04
subsystem: release-tooling
tags: [jitpack, release-cut, module-manifest, bash-gates, python]

requires:
  - phase: 17-01
    provides: scripts/modules.list manifest, scripts/lib/modules.sh reader, verify-module-manifest.sh
provides:
  - "jitpack-dry-run.sh expects exactly the manifest's artifacts, with per-module packaging, read from the tree under test"
  - "jitpack-consumer-probe.sh third consumer :undoalone: fails when voice-action-engine-core or kotlinx-coroutines resolves"
  - "jitpack-live-probe.sh default modules and core-dependency rule driven by the manifest (dependsOnCore)"
  - "release-cut.sh MODULES from HEAD's manifest, gate 7 is_module_api allowlist, tag message and selftest step 4 list every module, gate_hygiene runs verify-module-manifest.sh"
  - "scripts/lib/published_versions.py: gate 15 honoring dependsOnCore both ways (UNDO-01 published-POM half)"
  - "scripts/verify-release-manifest.sh: temp-clone proof of gate 7 and fabricated maven-local proof of gate 15"
  - "C03 (verify-docs-coverage.sh) and W3 (agent-wiring-test.sh) accept every manifest module"
affects: [17-10, 19, 20]

actuals:
  tokens: 14000
  tasks: 3
  commits: 3
plan_head_before: 5d59d56c58c48275a7c44e4e59c30219f79698a1
commits: 3

tech-stack:
  added: []
  patterns:
    - "Publishing and release scripts take their module list from scripts/modules.list, never a literal list"
    - "Gate logic that needs real parsing lives in a stdlib python helper with a fixture proof"

key-files:
  created:
    - scripts/lib/published_versions.py
    - scripts/verify-release-manifest.sh
  modified:
    - scripts/jitpack-dry-run.sh
    - scripts/jitpack-consumer-probe.sh
    - scripts/jitpack-live-probe.sh
    - scripts/release-cut.sh
    - scripts/verify-docs-coverage.sh
    - scripts/agent-wiring-test.sh

key-decisions:
  - "release-cut.sh parses the manifest with a small awk helper (manifest_names, vae_artifact_of) over `git show HEAD:scripts/modules.list` instead of sourcing modules.sh, because the manifest must come from HEAD, not the working tree"
  - "published_versions.py also rejects a .module dependency on core for dependsOnCore=no modules, not only the POM"
  - "The temp-clone proof adds a fourth gate-7 case: an api.txt of a module outside the manifest (sample/api.txt) is rejected, showing the allowlist is exact paths and not a glob"

patterns-established:
  - "verify-release-manifest.sh prints RELEASE MANIFEST PROOF OK cases=<n> after proving that the real tree's git status is unchanged"

requirements-completed: [UNDO-01]

coverage:
  - id: D1
    description: "Gate 7 accepts exactly the manifest's <module>/api.txt (undo/api.txt included) and .planning/, rejects undo/build.gradle.kts and sample/api.txt"
    requirement: UNDO-01
    verification:
      - kind: other
        ref: "scripts/verify-release-manifest.sh part 1 (temp clone) -> RELEASE MANIFEST PROOF OK cases=8"
        status: pass
    human_judgment: false
  - id: D2
    description: "Gate 15 requires core at the tag for dependsOnCore=yes modules and no core at all for the others; green case plus three red cases"
    requirement: UNDO-01
    verification:
      - kind: other
        ref: "scripts/verify-release-manifest.sh part 2 (fabricated maven-local) -> RELEASE MANIFEST PROOF OK cases=8"
        status: pass
    human_judgment: false
  - id: D3
    description: "C03 accepts an undo coordinate line ending :<version> and rejects one with a concrete version"
    requirement: UNDO-01
    verification:
      - kind: other
        ref: "plan Task 3 automated verify (copy of the docs with an undo line appended) -> exit 0 then DOC COVERAGE FAIL: C03"
        status: pass
    human_judgment: false
  - id: D4
    description: ":undoalone consumer resolves voice-action-engine-undo alone with no :core and no coroutines (clean Gradle cache)"
    requirement: UNDO-01
    verification:
      - kind: other
        ref: "bash -n and grep acceptance only here; the dry run and probe execute in plan 17-10 (quiet window)"
        status: pending
    human_judgment: false
---

# Phase 17 Plan 04: Manifest-driven publishing and release tooling Summary

Every publishing and release script now reads `scripts/modules.list`, so `:undo` is dry-run, probed, gate-checked and tag-listed like the other modules, and its "depends on nothing" promise is checked on the published POM (gate 15) and in a clean consumer (`:undoalone`).

## What was done

1. **Tracer (commit b919b60).**
   - `jitpack-dry-run.sh` sources the reader from the clone and sets `VAE_MODULES_FILE` to the clone's manifest. The exact artifact set is `vae_artifacts_sorted`. The per-module artifact check loops over `vae_modules` with the packaging column.
   - `jitpack-consumer-probe.sh` gains `:undoalone` (kotlin.jvm, JVM 11, depends only on `voice-action-engine-undo`). It compiles `io.github.ygaray.voiceactionengine.undo.UndoJournal { }.newTicket()` in the same `./gradlew --no-daemon` call. It fails with `PROBE FAIL: <name> resolved on :undoalone runtimeClasspath (:undo must stand alone)` when `voice-action-engine-core` or `kotlinx-coroutines` appears on its runtime classpath. The existing two consumers are unchanged.
   - `jitpack-live-probe.sh` defaults `EXPECT_MODULES` to `vae_artifacts`. The core-dependency check follows `dependsOnCore`: yes requires the POM dependency, no (non-core) fails if the POM names core. `CHECK_CORE_DEP` is kept.
2. **release-cut.sh and gate 15 (commit f1f14ae).**
   - `MODULES` comes from `git show HEAD:scripts/modules.list` (status captured, exit 2 `RELEASE USAGE` when absent or empty).
   - Gate 7 uses `is_module_api` over exact `<m>/api.txt` paths.
   - Gate 15 delegates to `scripts/lib/published_versions.py` (argv m2 group tag manifest).
   - `gate_hygiene` also requires `MODULE MANIFEST OK`.
   - The tag message prints one coordinate line per manifest module. Selftest step 4 builds its api.txt list from the sandbox's own manifest. Headers for gates 7, 10 and 15 are updated.
   - `verify-release-manifest.sh` proves the behaviors with 8 cases: an `undo/api.txt` commit passes, a `.planning/` commit passes, a `sample/api.txt` commit fails, and an `undo/build.gradle.kts` commit fails and is named. Gate 15 has a green maven-local plus undo-names-core, keystore-lacks-core and undo-POM-missing red cases.
3. **C03 and W3 (commit 1892129).** `MODULE_ALT` is built from the manifest in both scripts. C03 and the W3 regex use it. The jvm/app requirement lines and C01/C20 are untouched (Phase 19).

## Verification

- `bash -n` on the five edited scripts: clean.
- `scripts/verify-release-manifest.sh` -> `RELEASE MANIFEST PROOF OK cases=8`.
- `scripts/verify-docs-coverage.sh` -> `DOC COVERAGE OK checks=25`.
- `scripts/verify-module-manifest.sh` -> `MODULE MANIFEST OK modules=core,providers,keystore,undo`.
- Task 3 isolated-copy proof: an undo `:<version>` line passes C03, a concrete-version line fails it.
- All plan acceptance greps pass.
- No Gradle, dry run, probe, live probe or release-cut selftest was run (per the plan and the host constraints). The `:undoalone` execution is deferred to 17-10.

## Deviations from Plan

None. The plan was executed as written. One small addition inside the plan's intent: published_versions.py checks the `.module` dependency on core for dependsOnCore=no modules, not only the POM, since the plan text says "no POM dependency and no .module dependency".

## Handoff to Phase 20

These are known and intentionally not fixed here (decision [new-module-baseline], depends on Phase 17):

- **Gate 10 (api-dump):** it compares a fresh dump with the committed api.txt byte for byte. `undo/api.txt` is a header-only seed until the cut regenerates it, so gate 10 differs for `undo` until the cut commits the real dump.
- **Gate 12 (api-check):** the loop now names `undo`. With no `undo/api.txt` in the previous release tag it goes red with "is not in the previous release ... so there is no baseline". Phase 20 must add the "new in this release, no baseline" branch.
- **Selftest step 4:** it now dumps and commits `undo/api.txt` for the sandbox, then the sandbox cut hits gate 12 and goes red loudly until the same branch lands. Loud red was chosen over a silent skip (T-17-13, accepted).
- **Gate 15 end-to-end:** the real run of `published_versions.py` against the dry run's maven-local happens at the first cut with `:undo`.

## Self-Check: PASSED

- scripts/lib/published_versions.py: FOUND
- scripts/verify-release-manifest.sh: FOUND
- Commits b919b60, f1f14ae, 1892129: FOUND
