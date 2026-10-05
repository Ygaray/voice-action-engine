---
status: complete
phase: 10-sample-harness-gate-1-docs
source: 10-01-SUMMARY.md 10-02-SUMMARY.md 10-03-SUMMARY.md 10-04-SUMMARY.md 10-05-SUMMARY.md 10-06-SUMMARY.md 10-07-SUMMARY.md 10-08-SUMMARY.md 10-09-SUMMARY.md 10-10-SUMMARY.md
started: 2026-10-05T07:42:46Z
updated: 2026-10-05T07:42:46Z
mode: gate2-per-phase-signoff (owner chose one sign-off per phase over per-test UAT; library phase, Gate-1 evidence)
---

## Current Test

[testing complete]

## Tests

### 1. VER-01 - on-device base (G1-01
expected: VER-01 - on-device base (G1-01..G1-05). Fixture absent is loud and refuses with no spend; fixture present and verified (prefix `ebd3ef4a`, tools=18 equals host); OkHttp 5.2.1 pin runs on the device; bring-your-own key save, relaunch and delete through `:keystore`; the three test keys imported through `:keystore` with the plaintext files deleted.
result: pass
source: gate1-evidence + owner sign-off

### 2. VER-02 - Anthropic agentic cold run (G1-06)
expected: VER-02 - Anthropic agentic cold run (G1-06). PASS: 2 turns, write 7016, read 7016, both 200, `min_cacheable=4096`, claude-haiku-4-5.
result: pass
source: gate1-evidence + owner sign-off

### 3. VER-03 - single-shot smokes and multi-turn (G1-07, G1-08, G1-09, G1-10, G1-11)
expected: VER-03 - single-shot smokes and multi-turn (G1-07, G1-08, G1-09, G1-10, G1-11). Anthropic and OpenAI smokes PASS; OpenAI Chat and OpenRouter multi-turn PASS; OpenRouter smoke INCONCLUSIVE (`model_filled_optional`) on both prompts.
result: pass
source: gate1-evidence + owner sign-off

### 4. VER-04 - clarification and partial on device (G1-12)
expected: VER-04 - clarification and partial on device (G1-12). Offline demos PASS: question and two options shown, follow-up completes after choosing, partial readout "Did 1 action(s), couldn't finish".
result: pass
source: gate1-evidence + owner sign-off

## Summary

total: 4
passed: 4
issues: 0
pending: 0
skipped: 0

## Sign-off

signed-off-with-gap by Yahir, in-session, 2026-10-05T07:42:46Z (v1.0 milestone Gate-2 via /gsd-verify-milestone).

## Gaps

- truth: "C1 Billing not exercised (unit-covered)"
  status: accepted
  severity: minor
  test: 0
- truth: "C3/W04 MISMATCH; fix in v1.1 phase 12"
  status: accepted
  severity: minor
  test: 0
- truth: "C4 LATER-02"
  status: accepted
  severity: minor
  test: 0
- truth: "C5/G1-09 OpenRouter omit-optionals INCONCLUSIVE (accepted by evidence, W02)"
  status: accepted
  severity: minor
  test: 0
