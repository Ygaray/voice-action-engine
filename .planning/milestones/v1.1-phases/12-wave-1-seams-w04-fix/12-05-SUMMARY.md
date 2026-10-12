---
phase: 12-wave-1-seams-w04-fix
plan: 05
subsystem: sample-harness
tags: [sample, gate-1, responses-probe, live-leg, runner-guard, prov-16]
requires: []
provides:
  - "LegRunner.judge RESPONSES_PROBE arm: PASS only on typed model_unsupported after a real provider answer; FAIL otherwise"
  - "scripts/run-sample-gate1.sh and verify-sample-device-guard.sh retargeted to the Phase 12 directory"
  - "12-LIVE-LEG-DECISION.md: bounded PROV-16 live-leg request (decision: pending) with the relayed GO record for plan 12-08"
affects: [12-08, 19]
tech-stack:
  added: []
  patterns: ["live probe judged on the typed outcome plus presence of an HTTP status, so an engine pre-call refusal cannot pass as live proof"]
key-files:
  created:
    - .planning/phases/12-wave-1-seams-w04-fix/12-LIVE-LEG-DECISION.md
  modified:
    - sample/src/main/kotlin/io/github/ygaray/voiceactionengine/sample/legs/LegRunner.kt
    - sample/src/main/kotlin/io/github/ygaray/voiceactionengine/sample/legs/LegCatalog.kt
    - sample/src/test/kotlin/io/github/ygaray/voiceactionengine/sample/SmokeLegTest.kt
    - scripts/run-sample-gate1.sh
    - scripts/verify-sample-device-guard.sh
key-decisions:
  - "decision line stays 'decision: pending' (the runner's push-keys gate); the coordinator's GO line is recorded verbatim in an 'Approval record' section, and the separate TESTER window grant is noted as NOT yet granted. Plan 12-08 flips the decision line under that window."
requirements-completed: []  # PROV-16 stays open until the live run of plan 12-08
status: complete
plan_head_before: 60c4db90eaf5fe80ebe52cb05d4ed24452b5e5d3
commits: 3
metrics:
  completed: 2026-10-05
actuals:
  tokens: 9000
  tasks: 3
  commits: 3
---

# Phase 12 Plan 05: Judged responses_probe and retargeted live runner Summary

The `:sample` `responses_probe` leg now passes only on the typed `model_unsupported` after a real provider answer, the guarded TESTER runner reads Phase 12's decision file, and the bounded live-leg request exists with the relayed GO recorded. No device was touched.

## What changed

- **Judged probe (Task 1, tracer)**: `LegRunner.judge` RESPONSES_PROBE arm returns PASS `model_unsupported` when the reason is `model_unsupported` and an HTTP status exists, FAIL `no_provider_call` when the engine refused before any call, and FAIL with the outcome reason/kind otherwise. The `http` extra is unchanged and the `supportsTools = true` override is kept. `LegCatalog` comment updated. `VerdictKind.CAPTURED` stays in the enum. `SmokeLegTest` replaced the captured-only test with three cases: the exact line `VAE_VERDICT leg=responses_probe verdict=PASS reason=model_unsupported http=400 trigger=ui` (optional pool 1, core 0, second run REFUSED `budget`, fake saw one call), FAIL `http_error http=400`, and FAIL `no_provider_call`. Run: SmokeLegTest 9 tests, 0 failures.
- **Runner retarget (Task 2)**: `PHASE_DIR`, `DECISION_FILE` (and so `EVIDENCE_DIR`) now point at `.planning/phases/12-wave-1-seams-w04-fix`; header no longer says Phase 10. Device constants, identity checks and lock are untouched (the four `git diff -G` checks against v1.0.1 exit 0). `verify-sample-device-guard.sh` fixtures mirror the retarget: `SAMPLE DEVICE GUARD OK scenarios=33`.
- **Decision request (Task 3)**: `12-LIVE-LEG-DECISION.md` with `decision: pending`, the two-leg budget (expected 2, ceiling 4 requests, ceiling USD 0.05), TESTER-only conditions, the Phase 19 D-13 reconciliation note, the Relay section, and, per the coordinator, the GO line verbatim as the approval record for 12-08.

## Deviations from Plan

**1. [Coordinator instruction] Approval record added to the decision file**
- The plan wrote only `decision: pending`. The coordinator relayed the orchestrator's GO (spend approval) and asked for it verbatim in the file. It is recorded in a separate "Approval record" section; the exact `decision:` line stays `pending` so `grep -c '^decision:'` is 1 and the runner still refuses to push keys. The TESTER device window is explicitly noted as a separate, not-yet-granted step.

No other deviations. Out-of-scope observation: a few pre-existing lines in `LegRunner.kt` and `SmokeLegTest.kt` exceed 120 columns (`:sample` is not under detekt); left alone.

## Verification

- `./gradlew --offline -q :sample:testDebugUnitTest --tests '*SmokeLegTest*' --tests '*EvidenceLineTest*'` exit 0.
- `scripts/verify-sample-device-guard.sh` ends `SAMPLE DEVICE GUARD OK scenarios=33`.
- Decision file acceptance greps all pass. No adb command was run.

## Self-Check: PASSED

- Files present: LegRunner.kt, LegCatalog.kt, SmokeLegTest.kt, run-sample-gate1.sh, verify-sample-device-guard.sh, 12-LIVE-LEG-DECISION.md.
- Commits found: 42a15d2 (Task 1), a47d6c7 (Task 2), 661e96e (Task 3).
