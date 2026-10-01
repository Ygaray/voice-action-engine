---
phase: 05-openai-openrouter-transports
plan: 12
subsystem: providers
tags: [chat-completions, openai, openrouter, live-capture, goldens]
requires:
  - phase: 05-11
    provides: liveChatCompletionsCapture task, sanitizer, call plan
provides:
  - eight sanitized real response bodies as captured goldens (5 OpenAI, 3 OpenRouter)
  - live answers to A1-A4, A11 and the cache-read checks, recorded as findings
affects: [05 verifier (SC2 real half), Phase 7, Phase 10 smoke]
key-files:
  created:
    - providers/src/test/resources/golden/chat/responses/captured/openai-invalid_key.json
    - providers/src/test/resources/golden/chat/responses/captured/openai-forced_log_food.json
    - providers/src/test/resources/golden/chat/responses/captured/openai-forced_log_food_repeat.json
    - providers/src/test/resources/golden/chat/responses/captured/openai-forced_edit.json
    - providers/src/test/resources/golden/chat/responses/captured/openai-auto_prose.json
    - providers/src/test/resources/golden/chat/responses/captured/openrouter-invalid_key.json
    - providers/src/test/resources/golden/chat/responses/captured/openrouter-forced_log_food.json
    - providers/src/test/resources/golden/chat/responses/captured/openrouter-haiku_route.json
    - .planning/phases/05-openai-openrouter-transports/evidence/live-chat-capture.txt
  modified:
    - providers/src/test/resources/golden/chat/responses/MANIFEST.tsv
    - providers/src/test/resources/golden/chat/responses/derived.json
requirements-completed: [PROV-08, PROV-12]
status: complete
commits: 2
plan_head_before: 1ae617e06764a4fe3786fa4eb6dc40e039a5abbd
actuals:
  tokens: 15000
  tasks: 3
  commits: 2
---

# Phase 5 Plan 12: Live Chat Completions capture Summary

**Orchestrator line: 11 HTTP requests (OpenAI 5, OpenRouter 6; ceiling 12), estimated cost under USD 0.01, all through with-test-keys (so in usage.log). Eight real, sanitized bodies are now goldens and replay green in `./gradlew check`.**

## Gate

Task 1 (D-16) was resolved before this run: **approve-capture**, relayed by the orchestrator yahir-gsd-control-plane-f2 through the milestone master on 2026-10-01. Basis: Yahir's standing test-key policy recorded in `.planning/cross-repo/HANDOFF.md` ("Keys: UNBLOCKED 2026-09-30": cheapest models, opt-in live legs, outside check, bounded stated call count). Conditions honoured: at most 11 requests, only through `with-test-keys`, bodies sanitized (no key, auth header, org or request id), fixtures under test resources only, nothing in logs. No key material was read, printed or committed.

## Calls

Run 1 (probes), run 2 (main), exactly the plan's two commands, `--no-daemon`, outside `check`. Run 1: 2 requests, no cost. Run 2: 9 requests; the Gradle task ended red because two intents were unmet (below), which is the intended loud behaviour, and the expectations were not edited.

| Call | Result | Golden |
|---|---|---|
| C5 OpenAI invalid key | 401 failure:auth, request id present | committed |
| R4 OpenRouter invalid key | 401 failure:auth, no request id | committed |
| C1 forced log_food | tool_calls, tool_use log_food | committed |
| C2 repeat | cached_tokens 1152 (cache read held) | committed |
| C3 forced EDIT | tool_use edit_list_card, title / item_id / completed_at all absent | committed |
| C4 auto prose | finish stop, end_turn | committed |
| R1 forced log_food | tool_calls | committed |
| R2 forced EDIT | model filled title, item_id, completed_at: intent NOT held | dropped, no row |
| R3 repeat | cached_tokens 0: intent NOT held | dropped, no row |
| R5 parallel_tool_calls probe | 404 No endpoints found | dropped, no row (not 2xx) |
| R6 Haiku route | tool_calls (native tool_use) | committed |

Manifest: 8 captured rows = 8 files. Superseded derived rows removed: openai_401, openrouter_401, openai_forced_log_food, openai_edit_absent_optional, openai_auto_prose, openrouter_forced_log_food. Kept as derived (capture did not replace them): openai_forced_log_food_stop (C1 ended with tool_calls, not stop), openrouter_edit_absent_optional (R2 filled the optionals). Their stale "replace on capture" notes were reworded.

## Findings (full list in evidence/live-chat-capture.txt)

- A1 held: reasoning_tokens 0 and no reasoning text with a forced tool on both vendors.
- A2: real finish_reason under a forced tool is `tool_calls` (OpenAI and OpenRouter).
- A3 held: OpenRouter accepts the OpenAI-shaped body, including routing to anthropic/claude-haiku-4.5.
- A4: OpenRouter does NOT accept parallel_tool_calls together with require_parameters for openai/gpt-5.4-mini: 404 "No endpoints found that can handle the requested parameters". Keep the flag off.
- A5: not observed (R6 reported cache_write 0). Still open.
- A11 held: system role accepted by both vendors and by the Haiku route.
- Numeric bounds in strict: not exercised (the encoder strips them before sending strict); the stripped schema was accepted.
- Cache read: OpenAI held on C2 (1152 of 1407 prompt tokens, decoded in:255 cache_read:1152). OpenRouter R3 got no hit on an immediate identical repeat.

## Gaps for the orchestrator (no code changed here)

1. **Error-map bug, found live.** `ChatErrors.kt` marker `NO_ENDPOINTS_MARKER = "No endpoints found that support"` does not match the live OpenRouter text "No endpoints found that can handle the requested parameters", so this 404 decodes as `model_not_found` instead of `model_unsupported`. Widen the marker (or key off `error.metadata.failed_routing_step`) and update the derived openrouter_404_no_endpoints wording.
2. A4: do not enable `parallelToolCallsFalseOnForced` for OpenRouter.
3. No real OpenRouter absent-optional EDIT body exists (R2 filled the optionals; the OpenAI one is committed). OpenRouter's per-vendor budget of 6 is used up, so a retry needs a new budget and a prompt or model that omits the optionals. `openrouter_edit_absent_optional` stays derived.
4. SC2 "real" half: met for OpenAI fully and for OpenRouter on success, invalid-key and Haiku-route envelopes; the OpenRouter EDIT real half is the open item in gap 3.
5. Test coupling: `ChatGoldenReplayTest` (loader self-tests, `line()` default) hard-codes the derived case `openai_forced_log_food` against the real derived.json. The plan said to remove that derived.json case, but the test-source prohibition forbids fixing the test, so the **derived.json case body was kept** (manifest row removed, so nothing replays it). Suggest the test use its own fixture; then the orphan case can be deleted.

Phase 10 carry, not exercised here: low-credit mapping (OpenAI insufficient_quota 429, OpenRouter 402) and the accepted key character set.

## Deviations from Plan

**1. [Rule 3 - Blocking] Kept derived.json case `openai_forced_log_food`** (see gap 5). Reason: removing it turned `ChatGoldenReplayTest.aGoodRowParsesAndACapturedRowWithItsFileParses` red, and the plan forbids test-source changes.

**2. Evidence line layout.** The runner's per-run total line is recorded as `run summary: requests=...` instead of starting with `LIVE_CAPTURE`, so the plan's `grep -c '^LIVE_CAPTURE' <= 12` check counts calls (11), not summaries.

No main-source, test-source or build-file change was made (`git diff 1ae617e..HEAD -- providers/src/main providers/src/test/kotlin providers/build.gradle.kts` is empty).

## Verification

- `./gradlew :providers:test` replay and absent-optional classes, then `./gradlew :providers:check --offline` and `./gradlew check --offline`: BUILD SUCCESSFUL (detekt, hygiene scan on the captured files, and the 4.12.0, 5.2.1 and 5.5.0 legs).
- Evidence checks: at most 12 LIVE_CAPTURE lines (11), no key-shaped string, findings section present; captured manifest rows (8) equal captured files (8).
- No behavioral/device verification was performed or claimed.

## Self-Check: PASSED
