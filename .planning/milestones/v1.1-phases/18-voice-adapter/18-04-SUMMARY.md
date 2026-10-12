---
phase: 18-voice-adapter
plan: 04
subsystem: build-gates
tags: [bash, confinement, stt, selftest, jitpack]
requires:
  - phase: 18-voice-adapter
    provides: ":stt exclusiveContent repository, catalog pin and :voice-adapter build file (plan 01), documented minimum in INTEGRATION.md (plan 02), adapter sources (plans 01 and 03)"
provides:
  - "scripts/verify-stt-confinement.sh: Gradle-free gate with --root and --selftest"
  - "check ids repo, catalog, wiring, adapter-build, sources, docs-minimum"
affects: [18-06, 18-08]
actuals:
  tokens: 3300
  tasks: 2
  commits: 2
tech-stack:
  added: []
  patterns: ["gate()/violate/selftest structure copied from verify-module-manifest.sh", "selftest builds a pristine copy from git ls-files -co and plants one violation per case"]
key-files:
  created:
    - scripts/verify-stt-confinement.sh
  modified: []
key-decisions:
  - "PD-01 honoured: the cross-project classpath proof is the composition of this gate, verifySttConfined and verifyAdapterSttCompileOnly (plan 06); no sample classpath resolution was added"
  - "The :stt group is always matched as the full string com.github.Ygaray.voice-engine-android; the repository's own group com.github.Ygaray.voice-action-engine stays legal"
  - "Whole-line comments are ignored by every check so a comment cannot satisfy or trip a count"
requirements-completed: [ADPT-01]
status: complete
duration: 6 min
completed: 2026-10-07
plan_head_before: 2a8f32c754311dc87250661c6ede8e16bfad13b5
commits: 2
coverage:
  - id: D1
    description: "Bash-only gate proving the :stt repository, per-module immutable pin, wiring, adapter declaration, import confinement and documented minimum on the real tree"
    requirement: ADPT-01
    verification:
      - kind: command
        ref: "scripts/verify-stt-confinement.sh -> STT CONFINEMENT OK checks=6"
        status: pass
    human_judgment: false
  - id: D2
    description: "Selftest: clean copy green plus eleven planted violations each red naming its item, real tree unchanged"
    requirement: ADPT-01
    verification:
      - kind: command
        ref: "scripts/verify-stt-confinement.sh --selftest -> STT CONFINEMENT SELFTEST OK cases=12"
        status: pass
    human_judgment: false
---

# Phase 18 Plan 04: Stt Confinement Gate Summary

A Gradle-free bash gate that proves only `:voice-adapter` can reach the speech engine, with a planted-violation selftest.

## Accomplishments

- `scripts/verify-stt-confinement.sh` (mode 755, `--root <dir>`, `--selftest`) with six checks: `repo` (one
  exclusiveContent block, one JitPack host mention, one `includeGroup` equal to the exact `:stt` group, never the
  aggregator), `catalog` (one `com.github.Ygaray` library, per-module name, `version.ref stt-engine`, quoted immutable
  `vX.Y.Z`), `wiring` (no other build file names the alias, the coordinate or `project(":voice-adapter")`, with file and
  line), `adapter-build` (`compileOnly` + `testImplementation` only, android library plugin, `minSdk = 35`), `sources`
  (the `:stt` package only in `FinalSegmentMapping.kt` and adapter tests; `LanguageLabels.kt` named as the stt-free facade
  violation) and `docs-minimum` (INTEGRATION.md states `<catalog pin> or newer`).
- Selftest: clean copy green plus eleven plants (plain JitPack repo, aggregator in the filter, branch pin, second catalog
  entry, `:stt` in providers, adapter in sample, alias raised to `implementation`, minSdk 33, `:stt` import in a core
  source, in `LanguageLabels.kt`, stale docs minimum). Each must exit 1 with a FAIL line naming the item, and the real
  tree's `git status --porcelain` must be identical before and after.

## Task Commits

| Task | Name | Commit |
| ---- | ---- | ------ |
| 1 | Tracer: confinement gate green on the real tree | cbb8daa |
| 2 | Planted-violation selftest | 36b5796 |

## Verification

- `scripts/verify-stt-confinement.sh` -> `STT CONFINEMENT OK checks=6`
- `scripts/verify-stt-confinement.sh --selftest` -> `STT CONFINEMENT SELFTEST OK cases=12`
- `bash -n`, `test -x`, no `gradlew` in the script, `grep -c plant_` = 20 (at least 11), real tree status unchanged by the selftest.
- No Gradle run, no network access.

## Deviations from Plan

None - plan executed exactly as written.

## Authentication Gates

None.

## Self-Check: PASSED

- scripts/verify-stt-confinement.sh exists and is executable.
- Commits cbb8daa and 36b5796 exist on gsd/phase-18-voice-adapter; measured commit count 2.
