---
status: complete
phase: 03-transcript-types-providerrouter-on-device-gate
source: 03-01-SUMMARY.md 03-02-SUMMARY.md 03-03-SUMMARY.md 03-04-SUMMARY.md 03-05-SUMMARY.md 03-06-SUMMARY.md 03-07-SUMMARY.md 03-08-SUMMARY.md 03-09-SUMMARY.md 03-10-SUMMARY.md
started: 2026-10-05T07:42:46Z
updated: 2026-10-05T07:42:46Z
mode: gate2-per-phase-signoff (owner chose one sign-off per phase over per-test UAT; library phase, Gate-1 evidence)
---

## Current Test

[testing complete]

## Tests

### 1. SC1 — Transcript types
expected: SC1 — Transcript types. Messages, tool calls/results, system, usage, stop reason, cache directive and verbatim `NativeReplay` express both a single-shot request and a multi-turn tool conversation; `:core` runtime classpath is coroutines + serialization + stdlib only.
result: pass
source: gate1-evidence + owner sign-off

### 2. SC2 — ProviderRouter
expected: SC2 — ProviderRouter. Selection seam asked once per command (per tier, then frozen); provider switch applies to the next command only; missing key is `NotConfigured` after zero provider calls; one provider's key never reaches another.
result: pass
source: gate1-evidence + owner sign-off

### 3. SC3 — ON_DEVICE gate
expected: SC3 — ON_DEVICE gate. Unavailable on-device selection is served only by the app-declared fallback with the fallback provider's own key; no declared fallback is a loud typed failure with zero calls and zero key lookups; no Nano/AICore code.
result: pass
source: gate1-evidence + owner sign-off

### 4. SC4 — CacheNotEngaged
expected: SC4 — CacheNotEngaged. Zero cache read/write above the model's minimum prefix raises exactly one event; the same result below the minimum is silent (boundary tests both sides).
result: pass
source: gate1-evidence + owner sign-off

### 5. SC5 — No hard-coded constants
expected: SC5 — No hard-coded constants. Model ids and limits reach the engine only via `TierPolicy` defaults, the selection seam and the app-overridable capability table; independent greps and the non-vacuous scan test find no model id, limit literal or settings-storage access in library code.
result: pass
source: gate1-evidence + owner sign-off

## Summary

total: 5
passed: 5
issues: 0
pending: 0
skipped: 0

## Sign-off

signed-off by Yahir, in-session, 2026-10-05T07:42:46Z (v1.0 milestone Gate-2 via /gsd-verify-milestone).

## Gaps

[none]
