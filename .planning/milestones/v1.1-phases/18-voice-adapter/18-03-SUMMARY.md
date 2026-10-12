---
phase: 18-voice-adapter
plan: 03
subsystem: voice-adapter
tags: [android-library, stt, mapper, redaction, api-surface]
requires:
  - phase: 18-voice-adapter
    provides: ":voice-adapter module scaffold, one-overload FinalSegment.toCommandInput and the closed-set normalizer (plan 01)"
provides:
  - "FinalSegment.toCommandInput (3 explicit overloads) in JVM facade FinalSegmentCommandInput"
  - "commandInputOf (3 explicit overloads) and normalizeSttLanguageLabel in the stt-type-free JVM facade SttLanguageLabels"
  - "RedactionTest sentinels and AdapterApiShapeTest reflection pin of the frozen public surface"
affects: [18-04, 18-07, 18-08]
actuals:
  tokens: 4500
  tasks: 3
  commits: 3
tech-stack:
  added: []
  patterns: ["explicit overloads, never default parameters, for additive-only API evolution", "stt-free facade in its own source file so no :stt type appears in its signatures", "reflection test pinning public JVM facade shape"]
key-files:
  created:
    - voice-adapter/src/test/kotlin/io/github/ygaray/voiceactionengine/voiceadapter/LanguageLabelsTest.kt
    - voice-adapter/src/test/kotlin/io/github/ygaray/voiceactionengine/voiceadapter/RedactionTest.kt
    - voice-adapter/src/test/kotlin/io/github/ygaray/voiceactionengine/voiceadapter/AdapterApiShapeTest.kt
  modified:
    - voice-adapter/src/main/kotlin/io/github/ygaray/voiceactionengine/voiceadapter/FinalSegmentMapping.kt
    - voice-adapter/src/main/kotlin/io/github/ygaray/voiceactionengine/voiceadapter/LanguageLabels.kt
    - voice-adapter/src/test/kotlin/io/github/ygaray/voiceactionengine/voiceadapter/FinalSegmentMappingTest.kt
key-decisions:
  - "PD-04 (planner pick, one-way at the v1.1.0 tag): public names toCommandInput (3 overloads), commandInputOf (3 overloads), normalizeSttLanguageLabel; JVM facades FinalSegmentCommandInput and SttLanguageLabels, explicit overloads only"
  - "Mapping is pure and total: text verbatim (same String instance), no trim, no validation, segmentId dropped, context and parentRunId passed by identity, label through the closed set (en, es, else null)"
requirements-completed: [ADPT-01]
status: complete
duration: 3 min
completed: 2026-10-07
plan_head_before: 46b636d236d993687886f504925861f913b73a07
commits: 3
coverage:
  - id: D1
    description: "FinalSegment.toCommandInput three overloads: verbatim text, segmentId dropped, normalised label, identity-passed context and parentRunId"
    requirement: ADPT-01
    verification:
      - kind: unit
        ref: "voice-adapter FinalSegmentMappingTest (10 tests)"
        status: pass
    human_judgment: false
  - id: D2
    description: "Stt-type-free commandInputOf (3 overloads) and closed-set normalizeSttLanguageLabel, agreeing with the segment path"
    requirement: ADPT-01
    verification:
      - kind: unit
        ref: "voice-adapter LanguageLabelsTest (10 tests)"
        status: pass
    human_judgment: false
  - id: D3
    description: "Transcript and unrecognised label never appear in the mapped input string form; mapping never throws on empty, huge or lone-surrogate input"
    requirement: ADPT-01
    verification:
      - kind: unit
        ref: "voice-adapter RedactionTest (5 tests)"
        status: pass
    human_judgment: false
  - id: D4
    description: "Frozen public surface pinned by reflection: 3 + 4 methods, no collection parameter, no :stt type in the stt-free facade; detekt, scanBannedConstructs and Metalava compatibility green"
    requirement: ADPT-01
    verification:
      - kind: unit
        ref: "voice-adapter AdapterApiShapeTest (5 tests)"
        status: pass
      - kind: other
        ref: "./gradlew :voice-adapter:detekt :voice-adapter:scanBannedConstructs :voice-adapter:metalavaCheckCompatibilityRelease (exit 0)"
        status: pass
    human_judgment: false
---

# Phase 18 Plan 03: Complete mapper and pinned public surface Summary

**The full `FinalSegment.toCommandInput` overload set, the `:stt`-free `commandInputOf` entry points and a closed-set (en, es, else null) normalizer, pinned by 30 JVM tests covering redaction sentinels and a reflection pin of the frozen API.**

## Performance

- **Duration:** 3 min
- **Started:** 2026-10-07T01:52:11Z
- **Completed:** 2026-10-07T01:55:48Z
- **Tasks:** 3
- **Files modified:** 6

## Accomplishments
- `FinalSegment.toCommandInput()`, `(context)` and `(context, parentRunId)`: text verbatim (same String instance), segmentId dropped, label normalised, context and parentRunId passed by identity; never throws.
- `commandInputOf` (three overloads) in a separate file/facade that names no `:stt` type, plus `normalizeSttLanguageLabel` pinned as the exact closed set (trim, lowercase, exact `en`/`es`, else null; idempotent; no Locale logic).
- Redaction sentinel tests (non-vacuous via `transcriptLength=`) and a reflection test proving no collection/Iterable/List parameter (no joiner) and no `:stt` type in `SttLanguageLabels`.

## Task Commits

1. **Task 1: Tracer, complete toCommandInput overload set** - `d9392d4` (feat)
2. **Task 2: stt-free entry points and label table** - `6748110` (feat)
3. **Task 3: Redaction sentinels and API-shape pin** - `9d40aa7` (test)

**Plan metadata:** committed separately (docs: complete plan)

## Files Created/Modified
- `FinalSegmentMapping.kt` - three `toCommandInput` overloads (the only main file naming an `:stt` type)
- `LanguageLabels.kt` - `normalizeSttLanguageLabel` and three `commandInputOf` overloads, no `:stt` import
- `FinalSegmentMappingTest.kt` (10), `LanguageLabelsTest.kt` (10), `RedactionTest.kt` (5), `AdapterApiShapeTest.kt` (5)

## Decisions Made
- PD-04 names and facade split as recorded in the plan; implemented unchanged. The tracer feedback gate (auto-mode, `<verify>` automated only) was satisfied by the Task 1 verify run passing end to end before expansion.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None. The Task 3 Gradle run started with 6730 MB available (below the 7000 MB host guideline) and completed with exit 0; no earlyoom kill occurred.

## Verification (plan-level only)

- Task 1 verify, Task 2 verify (tests + detekt) and Task 3 plan gate (all module tests, detekt, scanBannedConstructs, metalavaCheckCompatibilityRelease) all exit 0. All 30 module tests pass (10 + 10 + 5 + 5).
- All task `<acceptance_criteria>` re-run and passing; `voice-adapter/api.txt` and `core/api.txt` unchanged (seed stays header-only).
- No device or behavioral verification performed (out of lane); `verify-negative-controls.sh`, `verify-api-dump.sh` and `jitpack-dry-run.sh` not run (reserved for plan 18-08).

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness
- Public names for v1.1.0 are frozen in code and pinned by test; ready for the remaining voice-adapter plans (surface review in 18-07, API dump in 18-08).

## Self-Check: PASSED

Created and modified files exist; commits d9392d4, 6748110 and 9d40aa7 exist on gsd/phase-18-voice-adapter.

---
*Phase: 18-voice-adapter*
*Completed: 2026-10-07*
