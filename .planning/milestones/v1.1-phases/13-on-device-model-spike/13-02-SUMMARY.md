---
phase: 13-on-device-model-spike
plan: 02
subsystem: infra
tags: [sc4, ml-denial, gradle, detekt-adjacent, hygiene, negative-controls, on-device]

requires:
  - phase: 13-on-device-model-spike
    provides: ":spike-ondevice module (13-01) that introduces the first native ML runtime into the repo"
provides:
  - "verifyNoMlArtifacts: module-scoped (core/providers/keystore) classpath denial of ML artifacts, wired into check"
  - ":core on-device scan tokens litert, mediapipe, tflite, com.google.ai.edge with positive controls"
  - "Hygiene + .gitignore patterns for model weights and private gold labels; spike module refused in jitpack.yml"
  - "scripts/verify-ml-denial-controls.sh (7 plants) and Part 5 of verify-negative-controls.sh"
affects: [13-10, 13-11]

actuals:
  tokens: 4000
  tasks: 3
  commits: 3

plan_head_before: 3a17d2159e4135af1123bc140d40d8271d664801
commits: 3

tech-stack:
  added: []
  patterns:
    - "Denial scoped by module name, never in shared bannedRules/detekt.yml, so a later :ondevice module is not blocked (Pitfall 9)"
    - "Gate checks both resolved components and requested selectors, so it does not depend on the Gradle cache"
    - "Hygiene plants use a temporary GIT_INDEX_FILE copy; the real index is never touched"

key-files:
  created:
    - scripts/verify-ml-denial-controls.sh
  modified:
    - gradle/invariants.gradle.kts
    - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/NoHardCodedConstantsTest.kt
    - scripts/verify-repo-hygiene.sh
    - scripts/verify-negative-controls.sh
    - .gitignore

key-decisions:
  - "ML denial is a module-name-scoped task (core, providers, keystore) rather than a shared token list, per D-09 and Pitfall 9"
  - "Section a of hygiene tolerates the absent ondevice directory with || true (find's non-zero exit would otherwise kill the script under set -e/pipefail)"

requirements-completed: [SPIKE-03]

coverage:
  - id: D1
    description: "An ML dependency declared on :core, :providers or :keystore fails verifyNoMlArtifacts with 'resolves ML artifacts', even offline; the clean tree passes"
    requirement: SPIKE-03
    verification:
      - kind: other
        ref: "scripts/verify-ml-denial-controls.sh Part A (3 red plants, 3 green clean-tree runs)"
        status: pass
    human_judgment: false
  - id: D2
    description: ":core on-device scan flags litert/mediapipe/tflite/com.google.ai.edge on code lines, passes comments and the monotonic clock line"
    requirement: SPIKE-03
    verification:
      - kind: unit
        ref: "NoHardCodedConstantsTest.onDeviceMatcherFlagsImplementationCodeAndPassesTheMonotonicClock + noOnDeviceImplementationCode"
        status: pass
      - kind: other
        ref: "scripts/verify-ml-denial-controls.sh Part B (planted ZzMlPlant.kt turns noOnDeviceImplementationCode red)"
        status: pass
    human_judgment: false
  - id: D3
    description: "A model file, a private gold-label file, or a jitpack line naming the spike module fails repo hygiene; .gitignore ignores the patterns"
    requirement: SPIKE-03
    verification:
      - kind: other
        ref: "scripts/verify-ml-denial-controls.sh Part C (temporary-index plants) + scripts/verify-repo-hygiene.sh HYGIENE OK + git check-ignore"
        status: pass
    human_judgment: false

duration: 14min
completed: 2026-10-06
status: complete
---

# Phase 13 Plan 02: ML denial gates Summary

**SC4 ("no on-device or ML dependency in the published modules, whatever the verdict") is now mechanical: a module-scoped `verifyNoMlArtifacts` in `check`, ML tokens in the `:core` scan, and hygiene refusal of model weights, private gold labels and a spike line in `jitpack.yml`, each proven red-for-the-right-reason by plants.**

## Performance

- **Duration:** 14 min
- **Tasks:** 3
- **Files:** 6 (1 created, 5 modified)

## Accomplishments

- **Dependency denial (T-13-05).** `verifyNoMlArtifacts` in `gradle/invariants.gradle.kts`, guarded by `project.name in setOf("core", "providers", "keystore")`. It denies groups `com.google.ai.edge`, `com.google.mediapipe`, `org.tensorflow`, `com.google.mlkit` and names containing `litert`/`tflite`, on the same classpaths `verifyNoDiArtifacts` uses. It inspects resolved components and requested selectors, so an offline or unresolvable declaration still trips it. Nothing was added to `bannedRules`, `rawRules` or `config/detekt/detekt.yml` (T-13-07).
- **`:core` token scan.** `ScanRules.onDevice` gains `litert`, `mediapipe`, `tflite` and `com.google.ai.edge`; the assertion message is now `:core ships no on-device implementation (SC4)`. TDD: the new positive controls were added first and failed (1 of 12 red), then the regexes made them green. Comment lines and the `NANOS_PER_MILLI` clock line still pass.
- **Hygiene (T-13-06).** `.gitignore` and hygiene section c cover `*.litertlm`, `*.task`, `*.tflite`, `*.bin`, `*sb-gold*`; section a's package-root find includes `spike-ondevice` and `ondevice`; section b probes the ignore rules; section f refuses `jitpack.yml` naming `spike-ondevice`.
- **Controls (T-13-08).** `scripts/verify-ml-denial-controls.sh` ends `ML DENIAL CONTROLS OK plants=7`. Part A plants a `litertlm` dependency per module, Part B plants `ZzMlPlant.kt` in `:core`, Part C force-adds model and gold-label files into a temporary `GIT_INDEX_FILE` copy and appends a spike install line to `jitpack.yml`. A trap removes every plant and a `cmp` pass asserts the restores are byte-identical. `verify-negative-controls.sh` runs it as Part 5.

## Task Commits

1. **Task 1: verifyNoMlArtifacts and Part A controls (tracer)** - `8f6ce55` (feat)
2. **Task 2: LiteRT/MediaPipe/TFLite tokens in the :core scan, Part B** - `e728e6d` (test)
3. **Task 3: hygiene, .gitignore, Part C, negative-controls Part 5** - `94ced14` (chore)

## Decisions Made

See `key-decisions` above.

## Deviations from Plan

**1. [Rule 1 - Bug] Hygiene section a aborted when `ondevice/` is absent.** The plan said `find` tolerates missing directories through `2>/dev/null`, but under `set -euo pipefail` the non-zero find exit killed the script silently (exit 1, no output). Appended `|| true` to the `kt_files` pipeline. Found while running Task 3's own verify; fixed in the same commit.

**2. [Process] Plan commit ledger created after the first commit.** The `gsd-plan-head-before-13-02` ledger was written from `HEAD~1` right after the first task commit (value `3a17d21`, the true pre-plan HEAD), so the measured `commits: 3` is accurate.

Otherwise the plan was executed as written. The tracer gate was satisfied by re-running the Part A controls end to end (all green) before expansion.

## Issues Encountered

None. Host memory was tight; each Gradle invocation used the low-memory single-use recipe, one at a time, and no earlyoom kill occurred. No `./gradlew --stop`, no device or adb access.

## Authentication Gates

None.

## Verification

- `scripts/verify-ml-denial-controls.sh`: `ML DENIAL CONTROLS OK plants=7` (exit 0), including `ok    [providers gains an ML dependency] went red (resolves ML artifacts)` for core, providers and keystore.
- `scripts/verify-repo-hygiene.sh`: `HYGIENE OK`; `git check-ignore -q spike-ondevice/src/main/assets/x.litertlm` exits 0.
- `:core:test --tests '*NoHardCodedConstantsTest*' :core:verifyCoreDependencyAllowlist`: green; `:core:detekt :core:scanBannedConstructs`: green.
- After the run: build files and `jitpack.yml` unmodified, no `zz-plant`/`ZzMlPlant` file present, `git diff --cached` identical before and after (real index untouched), `config/detekt/detekt.yml` unmodified.
- Not run: the full `scripts/verify-negative-controls.sh` (long; only the Part 5 call site was added) and a full `check` on each module.

## Next Phase Readiness

Gates exist before the spike's native runtime can leak. Plan 13-10 (disposition) should re-run `scripts/verify-ml-denial-controls.sh` and confirm the spike module is gone on a red verdict; plan 13-11 can rely on `verifyNoMlArtifacts` when proving the `compile_leak` assumption on a published module.

## Self-Check: PASSED

- Created/modified files present: scripts/verify-ml-denial-controls.sh, gradle/invariants.gradle.kts, NoHardCodedConstantsTest.kt, scripts/verify-repo-hygiene.sh, scripts/verify-negative-controls.sh, .gitignore.
- Commits present: 8f6ce55, e728e6d, 94ced14.
