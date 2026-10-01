---
phase: 05-openai-openrouter-transports
plan: 07
subsystem: providers-chat-golden-responses
tags: [golden, manifest, replay, hygiene, chat-completions, openai, openrouter]
requires:
  - phase: 05-03
    provides: decodeChatResponse and the D-09/D-10 outcome table
  - phase: 05-05
    provides: parseChatError and the error map
  - phase: 05-06
    provides: ChatVendor values and the Chat transport
provides:
  - golden/chat/responses/derived.json (35 documented derivative bodies)
  - golden/chat/responses/MANIFEST.tsv (the single list of golden responses with provenance and expected outcome)
  - ChatGoldenReplayTest (data-driven replay, strict manifest loader, golden hygiene scan)
  - internal fun goldenHygieneViolations(text) for 05-11's sanitizer test to reuse
affects: [05-11, 05-12]
tech-stack:
  added: []
  patterns: [manifest-driven golden replay, strict loader that never skips, sanitizer rules enforced as a test]
key-files:
  created:
    - providers/src/test/resources/golden/chat/responses/derived.json
    - providers/src/test/resources/golden/chat/responses/MANIFEST.tsv
    - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ChatGoldenReplayTest.kt
  modified: []
key-decisions:
  - "Expected outcomes were taken from the D-10 table in the plan, never from observed output; all 35 rows matched on the first replay, so there is no production deviation"
  - "Test helpers are grouped in private objects (GoldenManifest, GoldenReplay) to stay under detekt's TooManyFunctions file threshold"
requirements-completed: [PROV-08, PROV-12]
status: complete
plan_head_before: 2e62a621ba3018de8f599ff7eb037eaaf9ed69e0
commits: 3
actuals:
  tokens: 60000
  tasks: 3
  commits: 3
duration: 25min
completed: 2026-10-01
---

# Phase 5 Plan 07: Golden Responses and Replay Summary

**A 35-row golden manifest replays every documented OpenAI and OpenRouter answer shape, 200 error bodies included, through the production decoder and error map, with a strict loader and a hygiene scan so no capture can be skipped or committed unsanitized.**

## Accomplishments

- Tracer: one forced `log_food` row per vendor flowed file -> manifest -> `decodeChatResponse` -> asserted typed outcome.
- 35 derived rows (OpenAI 22, OpenRouter 13) covering D-09/D-10/D-11: tool call with finish stop, refusal, content_filter, truncated arguments on length, empty/invalid/array/scalar arguments, tool_calls finish without calls, skipped non-function call, unknown and raw upstream finish values, arguments already an object, 200 error object, OpenRouter 200 envelope 429, empty choices plus error, finish_reason error with and without native finish reason, and non-2xx 401, quota 429, rate-limit 429, Astra /v1/responses 400, context length 400, 500, 402, 404 No endpoints, other 404, 403 moderation.
- The EDIT rows for both vendors list `title,items.0.item_id,items.0.completed_at` in `absent_keys`; the replay fails if any resolves in the decoded arguments (PROV-12 derived half, D-14).
- Strict loader (`GoldenManifest.parse`): fails with the case name on a missing captured file, unknown provenance or vendor, wrong column count, duplicate case, source/provenance mismatch, `expected` outside the grammar, bad `absent_keys`, empty note, and tool_choice not required/auto on a 2xx row. Non-vacuity test requires at least 30 rows, both vendors and each of the five outcome kinds.
- `goldenHygieneViolations` (top-level `internal`, reusable by 05-11) checks key shape, Bearer value, chatcmpl-/gen-/call_ ids ending in GOLDEN(digits), system_fingerprint null, created 0 and no user_id; a test walks every file under `golden/chat` (requests, responses, and `captured/` once it exists) and requires at least 3 files. Messages name the rule and never echo the offending text.

## Manifest format (05-12 relies on it)

Nine tab-separated columns after `#` comment lines and the header: `case vendor provenance source http_status tool_choice expected absent_keys note`. Derived rows use `derived.json#<case>`; captured rows use `captured/<vendor>-<label>.json` under `golden/chat/responses/captured/`. `expected` is `success:<stop reason>:<tool names or ->` or `failure:<reason code>`. A derived note starts with `derived:`.

## Capture labels each derived row waits for

| Capture | Derived rows it replaces or confirms |
|---|---|
| C1 | openai_forced_log_food, openai_forced_log_food_stop |
| C3 | openai_edit_absent_optional |
| C4 | openai_auto_prose |
| C5 | openai_401 |
| R1 | openrouter_forced_log_food |
| R2 | openrouter_edit_absent_optional |
| R4 | openrouter_401 |

All other rows are noted "not reproducible live" and stay derived.

## Task Commits

1. Task 1 (tracer): b1d63ac
2. Task 2: 4eaeb3e
3. Task 3: 76511e4

## Deviations from Plan

- None for production code: `git log 2e62a62..HEAD -- providers/src/main` is empty.
- Task 3 is marked tdd, but the behavior tests and the helpers they exercise (loader, hygiene scan) live in the same test-only file, so the RED and GREEN states were not committed separately. The negative-control tests (missing captured file, eight columns, unknown provenance/vendor, duplicate, source mismatch, bad grammar, each unsanitized sample) were written alongside the helpers and all pass; no separate failing commit exists.
- Detekt (MaxLineLength, ReturnCount) flagged the test file on first `:providers:check`; fixed by reformatting, no rule was relaxed.

## Verification

- `./gradlew :providers:check --offline` green: detekt zero issues, banned-construct scan, and the 4.12 / 5.2.1 / 5.5.0 test legs. `ChatGoldenReplayTest` ran 10 tests, 0 skipped, 0 failures on each leg.
- Acceptance greps: 35 manifest rows, `items.0.completed_at` on 2 lines, 3 `failure:malformed_tool_args`, 2 `failure:model_unsupported`, every note starts with `derived:`, no `Assume` or `@Ignore`.
- No STATE.md or ROADMAP.md edits; nothing under `.planning/graphs/` staged.

## Self-Check: PASSED

- derived.json, MANIFEST.tsv, ChatGoldenReplayTest.kt exist.
- Commits b1d63ac, 4eaeb3e, 76511e4 exist on the branch.
