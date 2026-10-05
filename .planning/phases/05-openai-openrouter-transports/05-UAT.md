---
status: complete
phase: 05-openai-openrouter-transports
source: 05-01-SUMMARY.md 05-02-SUMMARY.md 05-03-SUMMARY.md 05-04-SUMMARY.md 05-05-SUMMARY.md 05-06-SUMMARY.md 05-07-SUMMARY.md 05-08-SUMMARY.md 05-09-SUMMARY.md 05-10-SUMMARY.md 05-11-SUMMARY.md 05-12-SUMMARY.md
started: 2026-10-05T07:42:46Z
updated: 2026-10-05T07:42:46Z
mode: gate2-per-phase-signoff (owner chose one sign-off per phase over per-test UAT; library phase, Gate-1 evidence)
---

## Current Test

[testing complete]

## Tests

### 1. SC1 — Wire shape
expected: SC1 — Wire shape. Golden request bodies for OpenAI and OpenRouter: nested function tools, strict copy stripped of strict-rejected keywords (non-strict copy untouched), `max_completion_tokens` / `reasoning_effort:"none"` for gpt-5.4+, legacy `max_tokens`, `provider.require_parameters:true` on a forced OpenRouter call.
result: pass
source: gate1-evidence + owner sign-off

### 2. SC2 — Typed outcomes from real and derived bodies
expected: SC2 — Typed outcomes from real and derived bodies. 8 sanitized real captures (5 OpenAI, 3 OpenRouter) plus 29 derived rows replay to their typed outcomes: bad/array/scalar arguments -> `malformed_tool_args`, refusal and content_filter, HTTP-200 error envelopes, quota/402/403/404 mappings. No key material in goldens or evidence.
result: pass
source: gate1-evidence + owner sign-off

### 3. SC3 — Matrix and usage
expected: SC3 — Matrix and usage. Same 411 compiled tests green on 4.12.0 / 5.2.1 / 5.5.0 with per-leg runtime guard; usage normalized to four buckets (cached tokens not double counted).
result: pass
source: gate1-evidence + owner sign-off

### 4. SC4 — Retry, cancel, hygiene
expected: SC4 — Retry, cancel, hygiene. At most one transport retry (two requests) for transient statuses, dropped connections and timeouts; one apply and one commit per logical call; cancel and engine deadline cancel the HTTP call; no logging interceptors, `body?.string()`, strict only without optionals, 60 s default timeouts.
result: pass
source: gate1-evidence + owner sign-off

### 5. SC5 — Absent optional
expected: SC5 — Absent optional. Parameterized contract test for both vendors; an omitted optional reaches the mutation absent (real OpenAI body; OpenRouter on a derived body).
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

- truth: "C5 OpenRouter omit-optionals INCONCLUSIVE (accepted by evidence, W02)"
  status: accepted
  severity: minor
  test: 0
- truth: "W04 Responses-only 400 wording MISMATCH (host check 2026-10-05); fix in v1.1 phase 12"
  status: accepted
  severity: minor
  test: 0
- truth: "C4 OpenRouter cache-write accounting not exercisable (LATER-02)"
  status: accepted
  severity: minor
  test: 0
