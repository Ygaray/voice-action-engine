---
status: complete
phase: 07-singleshot-strategy
source: 07-01-SUMMARY.md 07-02-SUMMARY.md 07-03-SUMMARY.md 07-04-SUMMARY.md 07-05-SUMMARY.md 07-06-SUMMARY.md 07-07-SUMMARY.md 07-08-SUMMARY.md
started: 2026-10-05T07:42:46Z
updated: 2026-10-05T07:42:46Z
mode: gate2-per-phase-signoff (owner chose one sign-off per phase over per-test UAT; library phase, Gate-1 evidence)
---

## Current Test

[testing complete]

## Tests

### 1. SC1 — One forced-tool call, local resolution, gated commit
expected: SC1 — One forced-tool call, local resolution, gated commit. `SingleShotStrategy` makes exactly one forced single-tool call from the app's `ToolSpecProvider`; the app's `OutcomeResolver` yields a (possibly batch) proposal that commits only through gate -> `CommitSink`.
result: pass
source: gate1-evidence + owner sign-off

### 2. SC2 — Default mappings and overrides
expected: SC2 — Default mappings and overrides. No tool call / prose escalates with `NoToolCall`; refusal fails with `REFUSAL`; both overridable per tier; OpenAI Chat bodies carry `parallel_tool_calls: false` (OpenRouter deliberately omits it and takes the first call); extra calls are dropped with `EXTRA_TOOL_CALLS_DROPPED` and the outcome is `Completed(partial = true)`.
result: pass
source: gate1-evidence + owner sign-off

### 3. SC3 — CT confirm scenarios
expected: SC3 — CT confirm scenarios. Weak match held then committed via `commitHeld` as a linked run, batch proposal held with one gate ask, amended confirm applying only the replacement list (S1-S10 CT-shaped acceptance tests on in-tree fakes).
result: pass
source: gate1-evidence + owner sign-off

## Summary

total: 3
passed: 3
issues: 0
pending: 0
skipped: 0

## Sign-off

signed-off by Yahir, in-session, 2026-10-05T07:42:46Z (v1.0 milestone Gate-2 via /gsd-verify-milestone).

## Gaps

[none]
