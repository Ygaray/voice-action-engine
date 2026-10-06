---
phase: 13-on-device-model-spike
plan: 01
subsystem: infra
tags: [litertlm, on-device, spike, agp9, toolchain, thresholds]

requires:
  - phase: 12-wave-1-seams-w04-fix
    provides: ON_DEVICE seam and the :sample-shaped inert app module this spike copies
provides:
  - ":spike-ondevice unpublished app module that builds litertlm-android 0.17.1 on the pinned toolchain"
  - "evidence/toolchain.txt: compile, dex, zipalign, stdlib-fallout and APK-cost rows in the closed VAE_SPIKE_TOOLCHAIN grammar"
  - "13-THRESHOLDS.md: the D-07 bar, definitions and time-box locked before any device step"
affects: [13-02, 13-03, 13-04, 13-06, 13-08, 13-09, 13-10, 13-11]

actuals:
  tokens: 4500
  tasks: 3
  commits: 3

plan_head_before: f65310df1a67da45bf04d79040bf6936b689a8bb
commits: 3

tech-stack:
  added: ["com.google.ai.edge.litertlm:litertlm-android 0.17.1 (spike module only)"]
  patterns:
    - "Spike module is inert: no invariants gate, no publishing, no INTERNET permission, no configuration-time file reads"
    - "Thresholds as one machine-readable THRESHOLD key=value line per value"

key-files:
  created:
    - spike-ondevice/build.gradle.kts
    - spike-ondevice/src/main/AndroidManifest.xml
    - spike-ondevice/src/main/kotlin/io/github/ygaray/voiceactionengine/spike/backend/LiteRtProbe.kt
    - .planning/phases/13-on-device-model-spike/evidence/toolchain.txt
    - .planning/phases/13-on-device-model-spike/13-THRESHOLDS.md
  modified:
    - gradle/libs.versions.toml
    - settings.gradle.kts

key-decisions:
  - "litertlm pinned exactly at 0.17.1; no D-02 fallback to 0.16.1 was needed, so TOOLCHAIN RED does not apply"
  - "13-THRESHOLDS.md locked at sha256 ec4933fbc8ca5ed7a8a01135120ed00d0b2e6133fb26364a704571f643456b15 (commit c793a3b)"
  - "compile_leak recorded as assumed_runtime_only: only a published library module can prove an implementation dependency stays off a consumer's compile classpath (plan 13-11)"

patterns-established:
  - "Low-memory Gradle recipe (no daemon, 2 workers, in-process Kotlin, -Xmx1536m) with the exit status captured directly"

requirements-completed: [SPIKE-01]

coverage:
  - id: D1
    description: "The pinned toolchain (AGP 9.2.1, Kotlin 2.3.20, JVM 11 bytecode, minSdk 35) compiles, dexes and packages litertlm-android 0.17.1 into a 16 KB-aligned arm64 debug APK"
    requirement: SPIKE-01
    verification:
      - kind: other
        ref: "./gradlew :spike-ondevice:assembleDebug (exit 0) + zipalign -v -c -P 16 4 (exit 0)"
        status: pass
    human_judgment: false
  - id: D2
    description: "Consumer stdlib fallout and APK cost recorded as closed-grammar rows (stdlib 2.4.0 uplift; .so 21,802,960 B raw / 9,497,418 B deflated / stored; APK 30,771,293 B)"
    requirement: SPIKE-01
    verification:
      - kind: other
        ref: "grep of kind=stdlib and kind=apk rows in evidence/toolchain.txt plus :spike-ondevice:check exit 0"
        status: pass
    human_judgment: false
  - id: D3
    description: "D-07 thresholds committed as 24 machine THRESHOLD lines with unchanged numbers, definitions and time-box, before any device step"
    requirement: SPIKE-01
    verification:
      - kind: other
        ref: "python3 THRESHOLDS OK check from plan Task 3 + git ls-files --error-unmatch"
        status: pass
    human_judgment: false

duration: 9min
completed: 2026-10-06
status: complete
---

# Phase 13 Plan 01: Toolchain proof and locked thresholds Summary

**litertlm-android 0.17.1 builds into a 16 KB-aligned arm64 debug APK on the repo's own pinned toolchain (no fallback, no pin moved), and the D-07 voice-usable bar is committed as machine-readable thresholds before any device step.**

## Performance

- **Duration:** 9 min
- **Started:** 2026-10-05T23:59:57Z
- **Completed:** 2026-10-06T00:09Z
- **Tasks:** 3
- **Files:** 7 (5 created, 2 modified)

## Accomplishments

- **D-02 answered: green on the first pin.** Kotlin 2.3.20 reads the AAR's Kotlin 2.4.0 metadata and AGP 9.2.1's own D8 dexes the Java-21 classes at minSdk 35 (assumption A3 confirmed). `:spike-ondevice:assembleDebug` exits 0; `zipalign -v -c -P 16 4` reports "Verification successful". Pin used: **0.17.1**. Nothing in Kotlin, AGP, Gradle or OkHttp moved.
- **D-03 module stood up.** `:spike-ondevice` is a separate `com.android.application` module depending on `:core` only, absent from `jitpack.yml`, with no `invariants.gradle.kts`, no publishing and no permission element. `:sample` and `run-sample-gate1.sh` are untouched. Test system properties (`vae.spike.evidenceDir`, `vae.spike.verdictOut`, `vae.spike.thresholdsFile`) are wired for later plans, with the evidence-dir path declared as an uncached, never-up-to-date input.
- **D-10 host rows recorded** (all in `evidence/toolchain.txt`):
  - Consumer stdlib fallout: `debugRuntimeClasspath` resolves kotlin-stdlib 2.4.0 and kotlin-reflect 2.4.0 (above the 2.3.20 pin, so `uplift=yes`); kotlinx-coroutines-android resolves to 1.11.0 by constraint.
  - APK cost: `liblitertlm_jni.so` is 21,802,960 B raw, 9,497,418 B gzip -9, stored uncompressed (so the on-disk APK delta is about 21.8 MB, the download delta nearer 9.5 MB); the whole debug APK is 30,771,293 B.
  - `compile_leak` is `assumed_runtime_only` (A2 stays unproven until the published-module check in plan 13-11).
- **D-07 locked.** `13-THRESHOLDS.md` carries the verbatim bar, the implicit definitions (point-estimate schema-valid, Wilson z=1.959964, process-cold, PSS, thermal, trial counting, winning-cell rule, unmeasured-row rule), the 4 h time-box, 24 `THRESHOLD` lines and the 5-line lock message for the window request. Wilson oracles re-computed: 48/50 EN gives 0.865, 47/50 gives 0.838; 46/50 ES gives 0.812, 45/50 gives 0.786. **sha256: `ec4933fbc8ca5ed7a8a01135120ed00d0b2e6133fb26364a704571f643456b15`.**
- `:spike-ondevice:check` (lint plus the not-yet-present tests) is green with no lint baseline and no `abortOnError` change.

## Task Commits

1. **Task 1: toolchain proof and spike module** - `16ef233` (feat)
2. **Task 2: stdlib fallout and APK cost** - `1fef79d` (docs)
3. **Task 3: lock D-07 thresholds** - `c793a3b` (docs)

## Decisions Made

See `key-decisions` above. The SB envelope in the thresholds file follows the orchestrator's `sb_labels` answer (13-SB-LABELS-ANSWER.md): the tool count is a reported dimension (18 in the old pin, 19 in the current SB surface), and the fixture stays private; no copy of it was made.

## Deviations from Plan

None - plan executed exactly as written. The plan's SB wording ("18-tool") was kept as the baseline and annotated with the re-pinned 19-tool surface, per the relayed answer; this changes no threshold.

## Issues Encountered

None. Host memory was tight (about 7 GB available, swap full) but the single-use low-memory Gradle recipe completed every invocation without an earlyoom kill.

## Authentication Gates

None.

## Verification

- Task 1/2 automated commands: exit 0 (`VERIFY_OK`; `:spike-ondevice:check` exit 0).
- Task 3: `THRESHOLDS OK {}`; 24 `THRESHOLD` lines; 3 `unmeasured:` mentions.
- `scripts/verify-repo-hygiene.sh`: `HYGIENE OK`.
- Acceptance greps: exact pin matches 1 line; include line count 1 and the original include line unchanged; toolchain.txt grammar violations 0; `uses-permission` count 0; `invariants.gradle.kts` mentions 0; no `*.apk`, baseline or lint XML tracked.

## Next Phase Readiness

Toolchain is green, so the driver proceeds with 13-02 .. 13-11 as planned (no TOOLCHAIN RED path). Plan 13-04 replaces `LiteRtProbe` with the real `LiteRtBackend`. The thresholds sha above is what the runner (13-06) writes into the first evidence ENV line. No TESTER or adb access was used.

## Self-Check: PASSED

- Created files present: spike-ondevice/build.gradle.kts, spike-ondevice/src/main/AndroidManifest.xml, LiteRtProbe.kt, evidence/toolchain.txt, 13-THRESHOLDS.md.
- Commits present: 16ef233, 1fef79d, c793a3b.
