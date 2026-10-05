---
status: complete
phase: 08-multi-turn-mappers
source: 08-01-SUMMARY.md 08-02-SUMMARY.md 08-03-SUMMARY.md 08-04-SUMMARY.md 08-05-SUMMARY.md 08-06-SUMMARY.md 08-07-SUMMARY.md 08-08-SUMMARY.md 08-09-SUMMARY.md
started: 2026-10-05T07:42:46Z
updated: 2026-10-05T07:42:46Z
mode: gate2-per-phase-signoff (owner chose one sign-off per phase over per-test UAT; library phase, Gate-1 evidence)
---

## Current Test

[testing complete]

## Tests

### 1. SC1 — One conformance suite
expected: SC1 — One conformance suite. A single `MultiTurnConformanceSuite` bound to Anthropic, OpenAI and OpenRouter round-trips every fixture conversation, including parallel calls and empty-argument forms.
result: pass
source: gate1-evidence + owner sign-off

### 2. SC2 — Verbatim replay
expected: SC2 — Verbatim replay. Every assistant turn replays byte-for-byte from `NativeReplay` to the same provider and model (thinking goldens, derived and captured, survive); a stamp for another provider, model or shape fails with zero requests; `carry` is an opaque semantic value (no dedicated "carry cannot hold a transcript" test; rests on type shape plus the stamp refusal).
result: pass
source: gate1-evidence + owner sign-off

### 3. SC3 — Per-dialect tool results
expected: SC3 — Per-dialect tool results. Anthropic batches a turn's results in one user message with `is_error`; Chat sends one `role:tool` per call id (errors wrapped `{"error": ...}`); 5 captured, sanitized real-body goldens (Anthropic A1/A2, OpenAI O1, OpenRouter R1/R3) pass hygiene and replay green.
result: pass
source: gate1-evidence + owner sign-off

### 4. SC4 — Cache directive and stable prefix
expected: SC4 — Cache directive and stable prefix. Anthropic keeps one system breakpoint, Chat sends none; every iteration only appends to the cached prefix, with negative controls proving the check detects a rewrite.
result: pass
source: gate1-evidence + owner sign-off

## Summary

total: 4
passed: 4
issues: 0
pending: 0
skipped: 0

## Sign-off

signed-off by Yahir, in-session, 2026-10-05T07:42:46Z (v1.0 milestone Gate-2 via /gsd-verify-milestone).

## Gaps

[none]
