---
phase: 18-voice-adapter
plan: 05
subsystem: core-redaction
tags: [redaction, tostring, sentinel-test, commit-sink, kdoc]
requires:
  - phase: 18-voice-adapter
    provides: "sequencing only (plan 03 kept one Gradle-running plan per wave); no code dependency"
provides:
  - "ActionEventTest sentinel tests: pipeline-built and direct-construction proofs that no token, target id, call id, held run id or context text reaches ActionEvent.toString() or ExecutedAction.toString()"
  - "KDoc redact-by-default policy on both toString members in CommitSink.kt (bodies unchanged)"
affects: [18-08]
actuals:
  tokens: 2400
  tasks: 2
  commits: 2
tech-stack:
  added: []
  patterns: ["sentinel-with-positive-controls: the test first proves the sentinel is carried by the field, then proves it is absent from the string form"]
key-files:
  created: []
  modified:
    - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/ActionEventTest.kt
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/commit/CommitSink.kt
key-decisions:
  - "PD-05 honoured: printed shape unchanged (held run id shown only as set or null), no placeholder format invented, HeldRunIdTest and core/api.txt byte-unchanged"
requirements-completed: [ADPT-01]
status: complete
duration: 12 min
completed: 2026-10-07
plan_head_before: 424d152eb576e9ec8f6437556e298335da6b99a2
commits: 2
coverage:
  - id: D1
    description: "A real pipeline run yields an event whose string form excludes the outcome token, target-id values and context text the event really carries"
    requirement: ADPT-01
    verification:
      - kind: test
        ref: "core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/ActionEventTest.kt#anEventsStringFormNeverCarriesTheTokenTargetValuesOrContextText"
        status: pass
    human_judgment: false
  - id: D2
    description: "Direct construction with the sentinel in every field (call id, target key and value, held run id, token, context): absent from both string forms, intended ids and counts present, heldRunId=null still printed"
    requirement: ADPT-01
    verification:
      - kind: test
        ref: "core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/ActionEventTest.kt#everyFieldThatCouldCarryUserTextIsAbsentFromBothStringForms"
        status: pass
    human_judgment: false
  - id: D3
    description: "Redact-by-default policy stated in KDoc on ActionEvent.toString and ExecutedAction.toString, no body, public symbol or api.txt change"
    requirement: ADPT-01
    verification:
      - kind: command
        ref: ":core:detekt :core:scanBannedConstructs :core:metalavaCheckCompatibility -> exit 0; git diff --exit-code core/api.txt -> 0"
        status: pass
    human_judgment: false
---

# Phase 18 Plan 05: Action Event String-Form Redaction Summary

Sentinel tests with positive controls pin that `ActionEvent` and `ExecutedAction` string forms print only ids, kind, flags and counts, and the policy is now written in KDoc.

## Accomplishments

- Pipeline-built test (`anEventsStringFormNeverCarriesTheTokenTargetValuesOrContextText`): a sentinel placed in the outcome
  token, a target-id value (both the mutation's own and the step result's) and a context whose own `toString()` returns the
  sentinel. Positive controls first, then absence from `event.toString()` and `event.action.toString()`, plus the run id and
  `toolName=delete_items` still printed.
- Direct-construction test (`everyFieldThatCouldCarryUserTextIsAbsentFromBothStringForms`): sentinel in the token, provider
  call id, target-id key and value, held run id and context; absence proved, intended fields and counts proved present,
  and an event with a null held run id prints `heldRunId=null`.
- KDoc on both `toString` members states the redact-by-default policy. Bodies are byte-for-byte unchanged.
- `HeldRunIdTest` and `core/api.txt` untouched.

## Task Commits

| Task | Commit | Description |
|------|--------|-------------|
| 1 (tracer) | 96a2afd | pipeline-built sentinel test |
| 2 | 7017248 | all-fields sentinel test and KDoc policy |

## Deviations from Plan

**[Rule 1 - Plan text error] Kind prints lowercase** - Found during: Task 2. The plan asked the test to assert
`kind=COMMITTED`, but `ActionKind.toString()` renders `committed` (the first run failed on exactly that assertion, no
production behaviour involved). The assertion now builds the expected text from `"kind=${ActionKind.COMMITTED}"`, so it
tracks the enum's own string form. Files: `ActionEventTest.kt`. Verified by the full Task 2 gate (exit 0), included in
commit 7017248.

**Total deviations:** 1 auto-fixed (1 test-expectation correction). **Impact:** none on scope or production code.

## Verification

- Task 1 gate: `:core:test --tests '*ActionEventTest*' --tests '*HeldRunIdTest*'` exit 0 (6 ActionEventTest cases ran).
- Task 2 gate (plan gate): the above plus `*RedactionCanaryTest*`, `*ApiShapeTest*`, `:core:detekt`,
  `:core:scanBannedConstructs`, `:core:metalavaCheckCompatibility` exit 0.
- Acceptance criteria: only KDoc lines changed in `CommitSink.kt` (pass), `core/api.txt` and `HeldRunIdTest.kt` unchanged
  (pass), `SENTINEL` count 21 (needs 14), `heldRunId=null` asserted (pass), `class SentinelContext` present (pass).
- An earlier Task 2 run failed once on the lowercase-kind assertion above; one fix, one re-run, no retry loop. No
  earlyoom kills.

## Threat Model

T-18-40 and T-18-41 mitigated: both string forms are pinned by tests with positive controls, and the unchanged
`core/api.txt` plus Metalava compatibility check rule out a new public accessor.

## Next

Ready for 18-06.

## Self-Check: PASSED

Files modified exist, commits 96a2afd and 7017248 found in `git log`, acceptance criteria re-run green.
