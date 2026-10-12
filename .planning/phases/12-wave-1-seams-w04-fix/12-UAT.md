---
status: complete
phase: 12-wave-1-seams-w04-fix
source: [12-01-SUMMARY.md, 12-02-SUMMARY.md, 12-03-SUMMARY.md, 12-04-SUMMARY.md, 12-05-SUMMARY.md, 12-06-SUMMARY.md, 12-07-SUMMARY.md, 12-08-SUMMARY.md]
started: 2026-10-12T00:30:30Z
updated: 2026-10-12T00:34:53Z
---

## Current Test

[testing complete]

## Tests

### 1. onFailed hook, ReasoningMode default, claude-sonnet-5 row (SC1)
expected: 12-SELF-UAT.md SC1 shows PASS: a provider HTTP 400 turns into Escalate through onFailed; the hook never fires for a ceiling, a gate hold, a missing credential or strategy_error; with no hook set the run ends Failed(reason, details). reasoning defaults to OFF and the v1.0.1 wire goldens stay byte-identical on Anthropic, OpenAI and OpenRouter. claude-sonnet-5 has its own capability row.
result: pass

### 2. New outcome facts are strictly additive (SC2)
expected: carryIn, cappedByPolicy, Extraction.callId and ExecutedAction.providerCallId exist (the call ids are null for zero-call tiers). Metalava compat against the v1.0.x api.txt is green, and the old 7-arg ModelRequest and 2-arg Extraction constructors still exist.
result: pass

### 3. KeyAccess is opt-in and the docs are fixed (SC3)
expected: "bash scripts/verify-keyaccess-opt-in.sh" prints "keyaccess opt-in failures: 0" (calling it without @OptIn(DelicateKeyAccess) fails to compile; the positive control compiles). "bash scripts/verify-docs-coverage.sh" prints "DOC COVERAGE OK". INTEGRATION.md covers the three v1.0.1 stumbles and the single-tool cache note.
result: pass

### 4. W04 is fixed on the JVM (SC4)
expected: No reasoning_effort "none" is sent for gpt-6-astra (encoder golden). The replayed W04 HTTP 400 maps to ModelUnsupported on the OkHttp 4.12.0, 5.2.1 and 5.5.0 legs. Optional re-run: ./gradlew :core:test :providers:test :providers:testOkhttp521 :providers:testOkhttp550 :keystore:testDebugUnitTest :sample:testDebugUnitTest --offline --rerun --no-build-cache gives 0 failures and 0 skipped.
result: pass

### 5. W04 proven live (SC5 / PROV-16)
expected: evidence/gate1-responses_probe.txt contains "VAE_VERDICT leg=responses_probe verdict=PASS reason=model_unsupported http=400 trigger=ui", one OpenAI request, and "FILTER OK kept=5 dropped=1". Audited from the committed evidence; no live re-drive needed.
result: pass

## Summary

total: 5
passed: 5
issues: 0
pending: 0
skipped: 0
blocked: 0

## Gaps

[none]

## Notes

- Reviewed via evidence read-through (12-SELF-UAT.md, test sources, fresh test-result XML 2026-10-07 at 0 failures) with the operator; tests not re-run at Gate-2.
- SC5 rests on the single committed live capture (2026-10-05, TESTER); not re-driven.
- Gate-2 was unblocked by e0c3936 (COVERAGE.md reason over the 200-char cap; technician seed S-033).
