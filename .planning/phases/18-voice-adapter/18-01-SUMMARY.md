---
phase: 18-voice-adapter
plan: 01
subsystem: voice-adapter
tags: [android-library, aar, stt, jitpack, metalava, detekt]
requires: []
provides:
  - ":voice-adapter AAR module (artifactId voice-action-engine-voice-adapter)"
  - "FinalSegment.toCommandInput() and normalizeSttLanguageLabel()"
  - "catalog entry stt-engine (v0.7.0) + group-filtered exclusiveContent repository"
affects: [18-02, 18-03]
tech-stack:
  added: ["com.github.Ygaray.voice-engine-android:voice-engine-android v0.7.0 (compileOnly + testImplementation)"]
  patterns: ["module built on the :keystore AAR recipe", "exclusiveContent repository filtered to one exact group"]
key-files:
  created:
    - voice-adapter/build.gradle.kts
    - voice-adapter/api.txt
    - voice-adapter/src/main/kotlin/io/github/ygaray/voiceactionengine/voiceadapter/FinalSegmentMapping.kt
    - voice-adapter/src/main/kotlin/io/github/ygaray/voiceactionengine/voiceadapter/LanguageLabels.kt
    - voice-adapter/src/test/kotlin/io/github/ygaray/voiceactionengine/voiceadapter/FinalSegmentMappingTest.kt
  modified:
    - settings.gradle.kts
    - gradle/libs.versions.toml
    - scripts/modules.list
    - gradle/invariants.gradle.kts
    - jitpack.yml
key-decisions:
  - "PD-02: :sample stays unwired from :voice-adapter and :stt; sampleRequiredEdges/sampleAllowedEdges untouched"
  - "PD-07: the research note about a locale-dependent sort in jitpack-dry-run.sh is stale; nothing changed"
requirements-completed: [ADPT-01]
status: complete
duration: resumed execution (prior executor killed by earlyoom mid-verify)
completed: 2026-10-06
plan_head_before: 5ac884d39948803d38a3a9c42fae23cd0ba09e8e
commits: 2
actuals:
  tokens: 5000
  tasks: 2
  commits: 2
---

# Phase 18 Plan 01: :voice-adapter scaffold and tracer Summary

New `:voice-adapter` AAR module maps an `:stt` v0.7.0 `FinalSegment` to a `CommandInput` through a closed-set (en/es, unknown is null) language normalizer, with `:stt` compileOnly and resolved from a single group-filtered JitPack repository.

## Tasks

| Task | Commit | Result |
|------|--------|--------|
| 1 Tracer: module, catalog, settings, mapper, test | 78fbb28 | `FinalSegmentMappingTest` 1/1 pass; generated POM lists `voice-action-engine-core`, not the `:stt` artifact |
| 2 Register in manifest plumbing | 6fc2a37 | manifest gate OK (`modules=core,providers,keystore,undo,voice-adapter`), selftest OK (11 cases), hygiene OK, `:voice-adapter:check` plus four `verifyModuleGraph` tasks exit 0 |

## Verification (plan-level only)

- Task 1 verify run online and the exit status read directly (0); the `:stt` AAR resolved from the filtered repository.
- Task 2 verify run offline, exit 0. Released `api.txt` files (core, providers, keystore, undo) and `sample/build.gradle.kts` are byte-unchanged.
- No device or behavioral verification performed (out of lane).

## Deviations from Plan

None. The Task 1 files were written by a prior executor and reviewed against the plan unchanged; the first Gradle run after resume passed.

## Deferred / Notes

- The permanent `:stt` publication and classpath gates are for a later plan (as planned).
- `scripts/verify-negative-controls.sh`, `verify-api-dump.sh` and `jitpack-dry-run.sh` were deliberately not run (reserved for plan 18-08).

## Self-Check: PASSED

All created files exist; commits 78fbb28 and 6fc2a37 exist on gsd/phase-18-voice-adapter.
