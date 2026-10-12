---
phase: 19-sample-gate-1-docs
plan: 02
subsystem: testing
tags: [jitpack, consumer-probe, release-cut, bash, stt-confinement]

requires:
  - phase: 18-voice-adapter
    provides: voice-adapter module, deferred clean-cache :adapteralone obligation (P18 Gate-2 fragment item 4)
provides:
  - ":adapteralone clean-cache consumer project in scripts/jitpack-consumer-probe.sh with positive and negative :stt classpath assertions"
  - "release-cut.sh wiring record and waiver packet retargeted to .planning/releases/v1.1.0/"
affects: [19-13, 19-14, phase-20]

actuals:
  tokens: 9000
  tasks: 2
  commits: 2

plan_head_before: 4a69b40100bf27b271f8286491e1cc9b6a48076e
commits: 2

tech-stack:
  added: []
  patterns:
    - "Probe reads the :stt pin from gradle/libs.versions.toml (never hard-coded) and resolves it only through an exclusiveContent filter on its exact group"
    - "release-cut.sh names one RELEASE_DIR constant and derives both record paths from it; no env lever"

key-files:
  created: []
  modified:
    - scripts/jitpack-consumer-probe.sh
    - scripts/release-cut.sh

key-decisions:
  - "includeGroup literal kept in the settings heredoc (not via a variable) so the exact-group filter is greppable"
  - ":stt is compileOnly in :adapteralone so its debugRuntimeClasspath is a clean view of what the adapter brings"
  - "Selftest overlay of the v1.1.0 directory is conditional on the directory existing in the working tree; selftest stays red-by-design until Phase 20 writes the waiver packet"

requirements-completed: []  # DOC-02 is only enabled here (probe + release paths); docs plans satisfy it

status: complete
duration: 15min
completed: 2026-10-07
---

# Phase 19 Plan 02: adapteralone probe and v1.1.0 release paths Summary

**The JitPack consumer probe gained the deferred `:adapteralone` project (adapter resolves with core and without `:stt`, omitting consumers never get `:stt`), and `release-cut.sh` now reads its wiring record and waiver packet from the archive-proof `.planning/releases/v1.1.0/`.**

## Accomplishments
- Task 1 (tracer): `:adapteralone` is an AGP 9.2.1 library (minSdk 35, Java 11) depending on `voice-action-engine-voice-adapter` plus `compileOnly` of the `:stt` coordinate at the version read from `gradle/libs.versions.toml` (PROBE FAIL line if empty). `P.kt` compiles one `commandInputOf("hello", "en")` and one `FinalSegment.toCommandInput()`. After the compile it asserts `voice-action-engine-voice-adapter` and `voice-action-engine-core` are on `:adapteralone` `debugRuntimeClasspath`, and that `voice-engine-android` is absent from `:adapteralone`, `:app` and `:jvmconsumer`. `:stt` resolves only through an `exclusiveContent` repository filtered to `com.github.Ygaray.voice-engine-android`. PROBE FAIL assertions went from 4 to 9; final `PROBE OK` format unchanged.
- Task 2: `RELEASE_DIR=".planning/releases/v1.1.0"`, `WIRING_RECORD="$RELEASE_DIR/WIRING-RERUN.md"`, `WAIVER_PACKET="$RELEASE_DIR/WAIVER-PACKET.md"`. The selftest sandbox now overlays `scripts` first, drops `NO_WAIVER_DIR`, then overlays `RELEASE_DIR` (when present) so it is not removed again, and creates the record's parent directory before the synthetic wiring record is written. No reference to the archived Phase 11 directory remains.

## Task Commits
1. Task 1: `5af8932` feat(19-02): :adapteralone clean-cache consumer in the JitPack probe
2. Task 2: `99c890f` chore(19-02): point release-cut at the stable v1.1.0 release directory

## Verification (offline only)
- `bash -n` on both scripts: OK.
- Task 1 automated verify chain incl. `scripts/verify-stt-confinement.sh`: `STT CONFINEMENT OK checks=6`.
- Aggregator-coordinate grep: no match; `v0.7.0` count in the probe: 0; `compileOnly(` present; `git diff --exit-code` on `jitpack-dry-run.sh`, `settings.gradle.kts`, `libs.versions.toml`: clean.
- `scripts/release-cut.sh gate wiring HEAD`: exit 1 with `RELEASE GATE FAIL wiring: .planning/releases/v1.1.0/WIRING-RERUN.md is not in HEAD` (expected: plan 14 writes the record).
- `scripts/verify-release-manifest.sh`: `RELEASE MANIFEST PROOF OK cases=8`.
- NOT run (per plan and host constraints): the probe itself, `jitpack-dry-run.sh`, `release-cut.sh selftest`, any Gradle build. The probe's real execution is plan 13's clean-cache dry run.

## Release-cut diff review (Phase 20-owned file)
`git diff` on `scripts/release-cut.sh` (first task commit to HEAD): 20 insertions, 9 deletions, limited to the header comment on gate 6, the three path constants and their comments, the selftest overlay (tar/add/conditional release-dir overlay), the sandbox `mkdir`, and two selftest comment/commit-message strings. No gate logic, flag, env variable or skip was added. Phase 20 still edits this file (RT-02 new-module branch, RT-05), so its own wiring rerun on its final SHA remains mandatory.

## Deviations from Plan
None. The `includeGroup` filter uses the group literal rather than `$STT_GROUP` so the plan's literal grep verify holds.

## Issues Encountered
None.

## Self-Check: PASSED
- scripts/jitpack-consumer-probe.sh and scripts/release-cut.sh modified and committed; commits 5af8932 and 99c890f present; measured commit count 2.
