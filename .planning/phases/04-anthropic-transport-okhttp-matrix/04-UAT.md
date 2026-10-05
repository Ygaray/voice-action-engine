---
status: complete
phase: 04-anthropic-transport-okhttp-matrix
source: 04-01-SUMMARY.md 04-02-SUMMARY.md 04-03-SUMMARY.md 04-04-SUMMARY.md 04-05-SUMMARY.md 04-06-SUMMARY.md 04-07-SUMMARY.md 04-08-SUMMARY.md
started: 2026-10-05T07:42:46Z
updated: 2026-10-05T07:42:46Z
mode: gate2-per-phase-signoff (owner chose one sign-off per phase over per-test UAT; library phase, Gate-1 evidence)
---

## Current Test

[testing complete]

## Tests

### 1. SC1 (A1) — OkHttp matrix
expected: SC1 (A1) — OkHttp matrix. Plain `api` 4.12.0 floor; identical compiled tests green on 4.12.0 / 5.2.1 / 5.5.0 inside `check`, each leg's guard proving the okhttp and mockwebserver jar versions it ran.
result: pass
source: gate1-evidence + owner sign-off

### 2. SC2 — Cache-correct encoding
expected: SC2 — Cache-correct encoding. One `cache_control: ephemeral` on the last system block, none on tools/messages; byte-identical prefix across runs and across language/date/transcript changes; usage in four buckets.
result: pass
source: gate1-evidence + owner sign-off

### 3. SC3 — Forced-tool reshape and retry
expected: SC3 — Forced-tool reshape and retry. Only the specific forced-tool 400 reshapes (once); text-only reply is `NoToolCall`; transient statuses and timeouts retry at most once (never more than three requests); a retried call applies and commits exactly once.
result: pass
source: gate1-evidence + owner sign-off

### 4. SC4 — Cancellation and client hygiene
expected: SC4 — Cancellation and client hygiene. Cancel and engine deadline cancel the HTTP call and close a late response; no interceptors/logging; fixed HTTPS base URL with `anthropic-version 2023-06-01`; `body?.string()`.
result: pass
source: gate1-evidence + owner sign-off

### 5. SC5 — No secret leakage
expected: SC5 — No secret leakage. Canary sweep over trace, events, every `toString()` and failure message; failures carry only status, `error.type`, request id.
result: pass
source: gate1-evidence + owner sign-off

## Summary

total: 5
passed: 5
issues: 0
pending: 0
skipped: 0

## Sign-off

signed-off-with-gap by Yahir, in-session, 2026-10-05T07:42:46Z (v1.0 milestone Gate-2 via /gsd-verify-milestone).

## Gaps

- truth: "C1 low-credit 400 -> Billing not exercised live (unit-covered only)"
  status: accepted
  severity: minor
  test: 0
