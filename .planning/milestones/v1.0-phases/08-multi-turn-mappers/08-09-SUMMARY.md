---
phase: 08-multi-turn-mappers
plan: 09
subsystem: providers-test-live-capture
status: complete
tags: [live-capture, captured-goldens, signed-thinking, reasoning-details, d-12, d-11]

requires:
  - phase: 08-multi-turn-mappers
    provides: "08-08 bounded ConversationRecorder and the two opt-in live Gradle tasks"
provides:
  - "Five captured, sanitized multi-turn goldens that replay under check: anthropic A1, A2; openai O1; openrouter R1, R3"
  - "evidence/live-multiturn-capture.txt: LIVE_CAPTURE lines, request counts, cost estimate, findings, SC status"
affects: [phase-09, phase-10]

plan_head_before: 43be54e70985fae14c9219a8ac6a60bc821bca53
commits: 2

actuals:
  tokens: 20000
  tasks: 3
  commits: 2

key-files:
  created:
    - providers/src/test/resources/golden/conversations/anthropic/captured_a1.json
    - providers/src/test/resources/golden/conversations/anthropic/captured_a2.json
    - providers/src/test/resources/golden/conversations/openai/captured_o1.json
    - providers/src/test/resources/golden/conversations/openrouter/captured_r1.json
    - providers/src/test/resources/golden/conversations/openrouter/captured_r3.json
    - .planning/phases/08-multi-turn-mappers/evidence/live-multiturn-capture.txt
  modified:
    - providers/src/test/resources/golden/conversations/MANIFEST.tsv

key-decisions:
  - "R2 stays unmet: not edited, not re-run, no golden; recorded as a finding"

requirements-completed: [XCR-02, XCR-03]
---

# Phase 8 Plan 09: Live multi-turn capture Summary

Real Anthropic, OpenAI and OpenRouter conversations were recorded once each, sanitized and committed as five captured goldens (including a real signed-thinking golden accepted by the live API on replay); `:providers:check` is green. 14 of 19 requests, about USD 0.033 estimated.

## Gate decision (Task 1)

approve-capture, relayed via the milestone master from orchestrator yahir-gsd-control-plane-f2 on 2026-10-01 (at most 19 requests, under USD 0.20, through with-test-keys, redacted as in Phase 5; Sonnet 5.5 deviation approved for A2 and R3 only). Basis: Yahir's standing test-key policy (HANDOFF "Keys: UNBLOCKED 2026-09-30"). No key file was read, printed, copied or created.

## Orchestrator line: requests and cost

- Requests: 14 of at most 19. Anthropic 4 (A1 2, A2 2); OpenAI 3 (O1 2, OP 1); OpenRouter 7 (R1 2, R2 3, R3 2).
- Estimated actual cost: about USD 0.033 (Anthropic run about 0.021; Chat run about 0.012). Estimated from usage numbers at published per-MTok prices (Sonnet 5.5 assumed 3/15), not a billing figure.
- Each with-test-keys command ran exactly once, `--no-daemon`, outside check. No retry, no re-run.

## Outcomes

| Conv | Model | Outcome |
|---|---|---|
| A1 | claude-haiku-4-5 | met; 4 parallel calls; cache_write 6754 turn 1, cache_read 6754 turn 2 |
| A2 | claude-sonnet-5-5 | met; thinking block (empty text, 1440-char signature), no redacted_thinking, 4 parallel calls; replayed turn 1 accepted on turn 2 |
| O1 | gpt-5.4-mini | met; 4 parallel calls |
| OP | gpt-5.4-mini | finding only: unfiltered echo (annotations, refusal null) accepted, status 200 |
| R1 | openai/gpt-5.4-mini | met; tool_calls[].index echoed on turn 2 and accepted |
| R2 | openai/gpt-oss-120b | UNMET: still asking for calls after 3 turns; no golden, no manifest row (the Chat Gradle task ended red by design) |
| R3 | anthropic/claude-sonnet-5.5 | met; signed reasoning_details (signature 1611 chars) with no reasoning parameter; echo accepted |

## Findings (D-11)

- OpenRouter accepts an echoed tool_calls[].index (R1 turn 2, status 200).
- reasoning_details returned and accepted on echo for R2 and R3. R3 returned signed reasoning without any reasoning parameter (A7 yes).
- OpenAI accepted the unfiltered echoed message (A1 yes).
- Parallel calls: 4 on A1, A2, O1, R1, R3; R2 made one call per turn.
- Haiku cache_read on turn 2: 6754.
- Response model ids equal requested ids except O1 (dated id gpt-5.4-mini-2026-03-17 for gpt-5.4-mini); the golden keeps it and replays green.

## Gaps for the orchestrator

No code change is called for: index, reasoning_details and annotations were all accepted live, so the planned contingencies (stripping index on OpenRouter, revisiting A3) are not needed. R2 is a model-behaviour miss under the 3-request cap; a captured gpt-oss-120b golden would need a different model or script, which is a future capture decision, not a code gap.

## SC status

- SC2: real half MET. Real signed thinking (A2) survives byte-for-byte and the live API accepted the replay; R3 adds signed reasoning_details through OpenRouter.
- SC3: real half MET on all three dialects with recorded, sanitized real bodies (Anthropic A1/A2, OpenAI O1, OpenRouter R1/R3). The gpt-oss reasoning route (R2) has no captured golden.

## Deviations from Plan

None. R2 unmet was handled exactly per the plan's rule (keep only met goldens, no edit, no re-run). The Chat run's red Gradle exit was the intended loud behaviour.

## Verification

- `./gradlew :providers:test --tests '*MultiTurnConformanceTest' --tests '*ConversationGoldenTest' --offline -q` green after the Anthropic rows.
- `./gradlew :providers:check --offline -q` green with all five captured rows.
- Manifest: 5 captured rows equal 5 captured_*.json files. Evidence: 14 per-turn LIVE_CAPTURE conv= lines (at most 19), no key-shaped string, findings and SC3 lines present.
- No main-source, test-source or build-file change; no tag; no api.txt; contract section 11 untouched. Raw bodies stay under providers/build/.

## Commits

- 96c3cd9 test(08-09): real Anthropic conversations (A1, A2)
- db58d8c test(08-09): real OpenAI and OpenRouter conversations (O1, R1, R3), findings and SC status
- (this SUMMARY is committed separately after the count above, so `commits: 2` is measured before it)

## Self-Check: PASSED

All six created files exist on the worktree branch; commits 96c3cd9 and db58d8c exist.
