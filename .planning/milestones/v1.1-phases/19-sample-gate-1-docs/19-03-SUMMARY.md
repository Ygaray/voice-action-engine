---
phase: 19-sample-gate-1-docs
plan: 03
subsystem: voice-adapter
tags: [voice-adapter, api-surface, overloads, reflection-test, docs]

requires:
  - phase: 18-voice-adapter
    provides: voice-adapter module with three-overload facades (WR-04 positional trap)
provides:
  - "Two-form adapter surface: commandInputOf(text, label) and (text, label, context, parentRunId); FinalSegment.toCommandInput() and (context, parentRunId)"
  - "Reflection proof that no (String, String, Object) or (FinalSegment, Object) public static method exists"
  - "API.md and INTEGRATION.md section 12 wording for the two forms"
affects: [19-08, 19-09, 19-10, phase-20]

actuals:
  tokens: 6000
  tasks: 3
  commits: 3

plan_head_before: 47edaae457cffbdce6ca4a6176f6f4673bf52f03
commits: 3

tech-stack:
  added: []
  patterns:
    - "Frozen overload sets are pinned by reflection over the JVM facade classes, including a negative assertion for the dropped signature"

key-files:
  created: []
  modified:
    - voice-adapter/src/main/kotlin/io/github/ygaray/voiceactionengine/voiceadapter/LanguageLabels.kt
    - voice-adapter/src/main/kotlin/io/github/ygaray/voiceactionengine/voiceadapter/FinalSegmentMapping.kt
    - voice-adapter/src/test/kotlin/io/github/ygaray/voiceactionengine/voiceadapter/AdapterApiShapeTest.kt
    - voice-adapter/src/test/kotlin/io/github/ygaray/voiceactionengine/voiceadapter/LanguageLabelsTest.kt
    - voice-adapter/src/test/kotlin/io/github/ygaray/voiceactionengine/voiceadapter/FinalSegmentMappingTest.kt
    - API.md
    - INTEGRATION.md

key-decisions:
  - "Both context-only overloads dropped (orchestrator ruling, option b); nothing is released for voice-adapter so removal is safe pre-tag"
  - "voice-adapter/api.txt stays the header-only seed; no core, scripts or other module file touched"

requirements-completed: []  # DOC-02 is advanced (adapter doc wording) but owned by the docs plans and the phase verifier

status: complete
---

# Phase 19 Plan 03: Drop the context-only adapter overloads Summary

**The voice adapter now has exactly two forms per mapping (no-argument/short and full context + parentRunId), with a reflection test proving a three-argument string call has no context-only target.**

## Performance

- 3 tasks, 3 commits, no deviations.

## Accomplishments

- Task 1 (tracer): removed `commandInputOf(transcript, languageLabel, context)`; rewrote the two surviving KDocs; the label-facade shape test now pins the signature set `{(String, String), (String, String, Object, String)}` and a new test `aThreeArgumentStringCallHasNoContextOnlyTarget` asserts no `(String, String, Object)` public static method exists (the `commandInputOf("yes", "en", "run-42")` call shape is named in its KDoc).
- Task 2: removed `FinalSegment.toCommandInput(context)`; the segment-facade test is renamed to `theSegmentFacadeExposesExactlyTheTwoToCommandInputForms` and asserts `{(FinalSegment), (FinalSegment, Object, String)}` plus no `(FinalSegment, Object)`. The two tests that used the dropped forms now call the full form with a null parentRunId (context by identity, parentRunId null). `RedactionTest` and `SttFreeFacadeTest` needed no change.
- Task 3: API.md voice-adapter rows and INTEGRATION.md section 12 describe the two forms, with a sentence that no context-only form exists so a run id can never be mistaken for a context. Section 12 still has no kotlin fences.

## Task Commits

1. Task 1: `be21516` feat(19-03): drop the context-only commandInputOf overload and pin it by reflection
2. Task 2: `23669e4` feat(19-03): drop the context-only toCommandInput overload and pin the two-form segment facade
3. Task 3: `1131a16` docs(19-03): describe the two-form voice adapter surface

## Verification

- `:voice-adapter:testDebugUnitTest --tests '*AdapterApiShapeTest*' --tests '*LanguageLabelsTest*'`: exit 0 (Task 1).
- `:voice-adapter:check`: exit 0 (detekt, explicit API, lint, tests, verifyAdapterSttCompileOnly, Metalava); `git diff --exit-code -- voice-adapter/api.txt` clean.
- `grep -c '^public fun commandInputOf('` = 2; `grep -c '^public fun FinalSegment.toCommandInput('` = 2; `grep -rn 'toCommandInput(context)' voice-adapter/src` empty.
- `scripts/verify-docs-coverage.sh`: `DOC COVERAGE OK checks=25 types=107`; `scripts/verify-stt-confinement.sh`: `STT CONFINEMENT OK checks=6`.
- `git diff --exit-code -- core` clean (no core file changed).

## ActionEvent.toString KDoc confirmation (no edit)

- `grep -q 'printed verbatim' core/.../commit/CommitSink.kt`: PASS (the KDoc says a run id and a parent run id are printed verbatim and are treated as opaque identifiers).
- `! grep -q 'may carry user text' core/.../commit/CommitSink.kt`: PASS (absent; the KDoc tells integrators to keep user text out of ids).

No gap to report for Phase 20.

## Deviations

None. Task 1 first compile of the new test line exceeded 120 columns; wrapped before commit (own change, not a deviation from the plan).

## Notes for the orchestrator

STATE.md's Phase 18 decision line ("toCommandInput (3 overloads), commandInputOf (3)") and 18-SURFACE-REVIEW.md "Frozen names" are superseded by this plan; plan 08's API review records the new frozen set.

## Self-Check: PASSED

All seven modified files exist; commits be21516, 23669e4, 1131a16 present on gsd/phase-19-sample-gate-1-docs; `commits:` measured from the plan-head ledger (3).
