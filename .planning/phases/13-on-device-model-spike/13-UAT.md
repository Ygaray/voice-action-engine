---
status: complete
phase: 13-on-device-model-spike
source: [13-01-SUMMARY.md, 13-02-SUMMARY.md, 13-03-SUMMARY.md, 13-04-SUMMARY.md, 13-05-SUMMARY.md, 13-06-SUMMARY.md, 13-07-SUMMARY.md, 13-08-SUMMARY.md, 13-09-SUMMARY.md, 13-10-SUMMARY.md, 13-11-SUMMARY.md]
started: 2026-10-12T00:39:25Z
updated: 2026-10-12T00:39:25Z
---

## Current Test

[testing complete]

## Tests

### 1. Spike measured on the TESTER in a time-box (SC1)
expected: Gemma 4 E2B on LiteRT-LM 0.17.1 measured on the S22 TESTER. The small envelope ran to completion: warm p50 20278 ms, p95 28941 ms, cold 28182 ms, peak PSS 2268 MB, schema-valid 142/144, EN 43/55, ES 35/55, false writes 2/34.
result: pass

### 2. Verdict relayed early (SC2)
expected: A reproducible machine verdict in 13-VERDICT.md; the relay in 13-VERDICT-MESSAGE.md (relayed_at 2026-10-06T05:58:59Z); SPIKE-01/02 ticked in REQUIREMENTS.md.
result: pass

### 3. Red branch, nothing ships (SC3)
expected: 13-DISPOSITION.md says branch red; SPIKE-03 is N/A-deferred; spike-ondevice/ is removed.
result: pass

### 4. No ML dependency in :core / :providers (SC4)
expected: verifyNoMlArtifacts (gradle/invariants.gradle.kts) bans the ai.edge, mediapipe, tensorflow, mlkit, litert and tflite artifacts and is wired into check; hygiene and denial-control scripts OK.
result: pass

## Summary

total: 4
passed: 4
issues: 0
pending: 0
skipped: 0
blocked: 0

## Gaps

[none]

## Notes

- The operator accepted the RED verdict: no on-device tier ships in v1.1; offline coverage stays LocalGrammar-only; the ON_DEVICE seam in :core is kept for later.
- Signed off WITH GAP: the SB envelope (19 tools) is unmeasured. The 4 h time-box ended at screen trial 28/80, so it's red by rule, not measured. No re-measure is planned; any revisit (other hardware or model) is a new milestone.
- Reviewed via evidence read-through with the operator; verify-spike-verdict.sh --check not re-run at Gate-2 (host swap full).
