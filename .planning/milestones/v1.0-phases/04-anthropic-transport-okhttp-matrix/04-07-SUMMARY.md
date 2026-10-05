---
phase: 04-anthropic-transport-okhttp-matrix
plan: 07
subsystem: providers
tags: [anthropic, live-capture, opt-in, hermetic-check, tracer]
requires: ["04-06"]
provides:
  - "Gradle task :providers:liveAnthropicCapture (group verification): opt-in, never up to date, not a dependency of check"
  - "*Live* excluded from the default test task and from both OkHttp matrix legs"
  - "AnthropicLiveCaptureTest: bounded (6 requests), Haiku 4.5 only, prints ids/statuses/counts only"
  - "evidence/live-capture.txt: the real LIVE_CAPTURE lines from one run"
affects: [04-08, phase-10]
tech-stack:
  added: []
  patterns: ["live tests are named *Live* and excluded by pattern from every check-reachable Test task", "request ceiling enforced by counting AnthropicAttemptObserver callbacks and not starting a call whose worst case would exceed it"]
key-files:
  created:
    - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/anthropic/AnthropicLiveCaptureTest.kt
    - .planning/phases/04-anthropic-transport-okhttp-matrix/evidence/live-capture.txt
  modified:
    - providers/build.gradle.kts
key-decisions:
  - "The live task's source-set lookup is hoisted to project level (liveTestSet): inside a task configuration block the Kotlin DSL's the<SourceSetContainer>() resolves against the task and fails at task creation, which a --dry-run does not catch because registration is lazy"
  - "Live expectations are collected and asserted after all four calls have printed, so one failed expectation never hides the other calls' evidence"
requirements-completed: [BLD-06]
status: complete
commits: 2
plan_head_before: e41abc1c106509902bc58cad7829faccc1393336
actuals:
  tokens: 9000
  tasks: 2
  commits: 2
metrics:
  completed: 2026-10-01
---

# Phase 4 Plan 07: Opt-in bounded live Anthropic capture Summary

`./gradlew check` stays key-free and offline (`*Live*` excluded from `test` and both matrix legs, `liveAnthropicCapture` absent from check's task graph), and a single bounded run against claude-haiku-4-5 confirmed the wire shape for real: forced tool use, a real cache read, the reshape path, and the 401 shape.

## What was built

- `providers/build.gradle.kts`: `filter { excludeTestsMatching("*Live*") }` on the `test` task and on each leg task (the only leg change); new `liveAnthropicCapture` Test task (group verification, includes only `*AnthropicLiveCaptureTest`, `outputs.upToDateWhen { false }`, `onlyIf` `VAE_LIVE_ANTHROPIC == "1"`, standard streams shown). No `dependsOn` from `check`.
- `AnthropicLiveCaptureTest`: JUnit 4, skipped by `Assume` unless `VAE_LIVE_ANTHROPIC == "1"` and `ANTHROPIC_API_KEY` is non-blank. Four calls on `claude-haiku-4-5` with a 32 000-character fixed system prompt (clears Haiku's 4 096-token minimum cacheable prefix): forced `record_item`, the identical repeat, the reshape path (capabilities with `supportsForcedToolChoice = false`), and a deliberately invalid credential. A call is not started if observed requests plus its worst case (2, or 1 for the credential probe) would exceed 6. Output is `LIVE_CAPTURE` lines of label, result kind, status, error type, request-id presence, stop reason, tool names and the four usage numbers only.

## Live run: RAN

Command (from the worktree root): `with-test-keys --only anthropic -- env VAE_LIVE_ANTHROPIC=1 ./gradlew :providers:liveAnthropicCapture --offline --no-daemon --console=plain`. The wrapper accepted the key; no key material was read, printed or committed. 4 HTTP requests in total (ceiling 6). All four expectations were met.

| Call | Label | Outcome | Usage (uncached in / cache read / cache write / out) |
|---|---|---|---|
| 1 | forced | success, stop `tool_use`, tools `[record_item]`, request id present | 422 / 0 / 6330 / 33 |
| 2 | forced_repeat | success, `tool_use`, `[record_item]`, request id present | 422 / 6330 / 0 / 33 |
| 3 | reshaped (auto + strict + instruction) | success, `tool_use`, `[record_item]`, request id present | 336 / 6330 / 0 / 66 |
| 4 | invalid_credential | failure reason `auth`, HTTP 401, error type `authentication_error`, request id present | n/a |

Findings for Phase 10's smoke:
- The prompt cache hits on a real call: call 2 read all 6 330 tokens call 1 wrote, and the reshaped request (call 3) also read the same 6 330 tokens, so the reshape keeps the cached prefix byte-identical (system and tool definitions unchanged, only the tail differs).
- The reshaped request uses roughly twice the output tokens of the forced one (66 vs 33) for the same tool call; nothing wrong, just the cost of the auto path.
- The 401 response carries a request id and a parseable `authentication_error`, so the typed auth failure and request-id plumbing work against the real service.
- No library code was changed in this plan; no finding requires a fix.

## Verification

- `./gradlew :providers:test --tests '*AnthropicLiveCaptureTest'` and the same for `:providers:testOkhttp550` both report "No tests found for given includes".
- `./gradlew check --dry-run` lists `:providers:testOkhttp550` and does not mention `liveAnthropicCapture`.
- Without `VAE_LIVE_ANTHROPIC`, `:providers:liveAnthropicCapture` reports SKIPPED.
- `./gradlew check --offline` green in the worktree (detekt at zero issues, the 4.12.0 / 5.2.1 / 5.5.0 legs), with no live call.
- Acceptance greps: `excludeTestsMatching("*Live*")` appears twice; the live test names no opus, sonnet, fable or mythos model; the evidence file has no key-shaped string.

## Deviations from Plan

**1. [Rule 1 - Bug] Source-set lookup failed at task creation.** The first draft called `the<SourceSetContainer>()` inside the `liveAnthropicCapture` configuration block, where it resolves against the task. Fixed by hoisting it to project level; found by running the SKIPPED gate (the `--dry-run` gate cannot see it because task registration is lazy).

**2. [Rule 3 - Blocking] detekt MaxLineLength.** Five over-long lines in the new test; reflowed with a local `send(...)` helper.

**3. Plan commit ledger.** The per-plan head ledger under the worktree git dir could not be written (the harness blocks writes to `.git/worktrees/...`), so `plan_head_before` is the base commit the orchestrator supplied (`e41abc1`) and `commits` is measured with `git rev-list --count e41abc1..HEAD` before this SUMMARY commit.

## Deleted files

None.

## Self-Check: PASSED

- AnthropicLiveCaptureTest.kt, live-capture.txt and the build file change exist and are committed (90f5076, 51d1c54).
