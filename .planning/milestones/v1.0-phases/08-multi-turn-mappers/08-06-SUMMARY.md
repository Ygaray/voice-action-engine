---
phase: 08-multi-turn-mappers
plan: 06
subsystem: providers-test-conformance
tags: [conformance-suite, wire-dialect, chat-completions, openai, openrouter, golden, allowlist-replay, reasoning-details]

requires:
  - phase: 08-multi-turn-mappers
    provides: "08-03 Chat replay and empty-arguments repair, 08-04 golden infrastructure, 08-05 abstract suite and WireDialect seam"
provides:
  - "ChatWire: the Chat Completions binding of WireDialect for OpenAI and OpenRouter"
  - "OpenAiMultiTurnConformanceTest and OpenRouterMultiTurnConformanceTest: the one suite, no inherited assertion changed"
  - "four derived Chat conversation goldens and manifest rows"
  - "ConversationGoldenTest.everyDialectHasADerivedRow (manifest sweep no longer vacuous)"
affects: [08-07, 08-08, 08-09]

plan_head_before: 3b25fa368dbc3936e0c6f892819ba163649843b8
commits: 3

actuals:
  tokens: 30000
  tasks: 3
  commits: 3

tech-stack:
  added: []
  patterns:
    - "a dialect that repairs a replayed turn (Chat empty arguments) supplies an independently computed repairedReplayWire; the shared replay check then takes the golden's next-request assistant element as the source and compares the two"
    - "ChatWire.projection is the test's own five-key allowlist, never read from production code"
    - "fixtures are emitted by a throwaway script from hand-written structures, to guarantee compact canonical text"

key-files:
  created:
    - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/conformance/OpenAiMultiTurnConformanceTest.kt
    - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/conformance/OpenRouterMultiTurnConformanceTest.kt
    - providers/src/test/resources/golden/conversations/openai/derived_parallel_tools.json
    - providers/src/test/resources/golden/conversations/openai/derived_empty_args_forms.json
    - providers/src/test/resources/golden/conversations/openrouter/derived_parallel_tools.json
    - providers/src/test/resources/golden/conversations/openrouter/derived_reasoning_details.json
  modified:
    - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/conformance/WireDialect.kt
    - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/conformance/ConversationGolden.kt
    - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/conformance/ConversationGoldenTest.kt
    - providers/src/test/resources/golden/conversations/MANIFEST.tsv

key-decisions:
  - "A manifest case name is unique per dialect, not globally, so the same scenario name can be written once per wire dialect (the plan's rows and acceptance greps require derived_parallel_tools on three dialects)"
  - "The empty_args_forms branch lives in the shared verbatimViolations algorithm (WireDialect.kt) rather than as an edit to MultiTurnConformanceSuite, so the suite file is byte-identical to 08-05 and cannot be accused of per-dialect weakening"

requirements-completed: [XCR-01, XCR-02, XCR-03]

coverage:
  - id: D1
    description: "The same abstract suite, with no assertion changed, passes on OpenAI and OpenRouter over every openai and openrouter manifest row (parallel calls, zero-argument call, error result), alongside Anthropic"
    requirement: "XCR-01"
    verification:
      - kind: unit
        ref: "providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/conformance/OpenAiMultiTurnConformanceTest.kt#everyFixtureConversationRoundTrips"
        status: pass
      - kind: unit
        ref: "providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/conformance/OpenRouterMultiTurnConformanceTest.kt#everyFixtureConversationRoundTrips"
        status: pass
    human_judgment: false
  - id: D2
    description: "After a Chat turn with parallel and zero-argument calls, the next request carries one role:tool message per call id in call order, the lookup_item error as {\"error\":\"no item with that name\"}, other results unchanged"
    requirement: "XCR-03"
    verification:
      - kind: unit
        ref: "providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/conformance/OpenAiMultiTurnConformanceTest.kt#toolResultsAreEncodedPerDialect"
        status: pass
      - kind: unit
        ref: "providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/conformance/OpenRouterMultiTurnConformanceTest.kt#toolResultsAreEncodedPerDialect"
        status: pass
    human_judgment: false
  - id: D3
    description: "The replayed Chat assistant message equals the allowlist projection of the stored message (annotations and reasoning dropped, refusal kept, OpenRouter index kept, reasoning_details whole and in order) and its compact bytes occur verbatim in every later request"
    requirement: "XCR-02"
    verification:
      - kind: unit
        ref: "providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/conformance/OpenAiMultiTurnConformanceTest.kt#theParallelFixtureAnswersFourCallsAndReplaysTheAllowlistProjection"
        status: pass
      - kind: unit
        ref: "providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/conformance/OpenRouterMultiTurnConformanceTest.kt#theParallelFixtureKeepsEachCallsIndexAndKeyOrderAndDropsTheReasoningField"
        status: pass
      - kind: unit
        ref: "providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/conformance/OpenRouterMultiTurnConformanceTest.kt#theReasoningDetailsAreKeptWholeAndInOrderWhileTheReasoningStringIsDropped"
        status: pass
    human_judgment: false
  - id: D4
    description: "Empty-arguments forms (an empty string and an absent key) decode as {} and replay repaired to \"{}\" exactly as the hand-written golden says; the golden differs from the plain projection only in those values; an unrepaired golden is reported for its turn"
    requirement: "XCR-01"
    verification:
      - kind: unit
        ref: "providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/conformance/OpenAiMultiTurnConformanceTest.kt#emptyArgumentsFormsDecodeAsEmptyObjectsAndStayAsReceivedInTheStoredTurn"
        status: pass
      - kind: unit
        ref: "providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/conformance/OpenAiMultiTurnConformanceTest.kt#theRepairedGoldenDiffersFromThePlainProjectionOnlyInTheEmptyArguments"
        status: pass
      - kind: unit
        ref: "providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/conformance/OpenAiMultiTurnConformanceTest.kt#aGoldenThatDoesNotRepairTheEmptyArgumentsIsReportedForThatTurn"
        status: pass
    human_judgment: false
  - id: D5
    description: "Manifest non-vacuity: each of anthropic, openai and openrouter has at least one derived row; no SB or CT name appears under golden/conversations"
    requirement: "XCR-03"
    verification:
      - kind: unit
        ref: "providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/conformance/ConversationGoldenTest.kt#everyDialectHasADerivedRow"
        status: pass
      - kind: command
        ref: "! grep -rliE 'log_food|edit_list_card|secondbrain|caltracker' providers/src/test/resources/golden/conversations/"
        status: pass
    human_judgment: false
  - id: D6
    description: "That a real OpenAI or OpenRouter endpoint accepts a replayed Chat turn (reasoning_details with a real signature in particular) is not shown by these placeholder-signature derived fixtures"
    requirement: "XCR-03"
    verification: []
    human_judgment: true
    rationale: "Derived fixtures prove the engine's replay bytes only; real-API acceptance needs a captured conversation (08-09)"

duration: 40min
completed: 2026-10-01
status: complete
---

# Phase 8 Plan 06: Multi-turn conformance suite on the Chat Completions mapper Summary

**The one abstract conformance suite now also runs on OpenAI and OpenRouter Chat Completions through a `ChatWire` adapter that calls only production code, over four derived conversations built on real Phase 5 envelopes: parallel and zero-argument calls, call-ordered `role:tool` results with `{"error":...}` wrapping, allowlist replay (annotations and reasoning dropped, `index` and `reasoning_details` kept), and empty-arguments repair.** Test sources and resources only; no main-source, Gradle or API change.

## Accomplishments

- `ChatWire(vendor, name)` in `WireDialect.kt`: encodes with `encodeChatRequest`, decodes with `decodeChatResponse`, drives the real `ChatCompletionsProvider.openAi/openRouter`; `projection` is the test's own allowlist (role, content, tool_calls, refusal, reasoning_details in stored order).
- `OpenAiMultiTurnConformanceTest` and `OpenRouterMultiTurnConformanceTest`, each inheriting all six suite tests unchanged plus dialect-specific checks.
- Four goldens and four manifest rows (below); `ConversationGoldenTest.everyDialectHasADerivedRow`.

## Recorded contract details

- **plan_head_before:** `3b25fa368dbc3936e0c6f892819ba163649843b8`.
- **Manifest rows added:**
  - `derived_parallel_tools` / openai / `gpt-5.4-mini` / tags `parallel,zero_arg,error_result`
  - `derived_empty_args_forms` / openai / `gpt-5.4-mini` / tags `empty_args_forms,zero_arg,parallel`
  - `derived_parallel_tools` / openrouter / `openai/gpt-5.4-mini` / tags `parallel,zero_arg,error_result`
  - `derived_reasoning_details` / openrouter / `anthropic/claude-sonnet-5.5` / tags `reasoning_details,zero_arg,parallel`
- **Phase 5 envelope each fixture used (no file name appears under golden/conversations):**
  - OpenAI parallel: the captured OpenAI forced-tool body (turn 1) and the captured OpenAI auto-prose body (turn 2): key order id, object, created, dated model `gpt-5.4-mini-2026-03-17`, choices, usage with the two details objects, system_fingerprint; message keys role, content, tool_calls, refusal, annotations.
  - OpenAI empty-args: the same two OpenAI envelopes.
  - OpenRouter parallel: the captured OpenRouter forced-tool body (provider OpenAI, `native_finish_reason`, message keys role, content, refusal, reasoning, tool_calls with type, index, id, function).
  - OpenRouter reasoning_details: the captured OpenRouter anthropic-route body (provider Amazon Bedrock, `native_finish_reason` tool_use), with `reasoning_details` placed before `tool_calls`.
- **Fixture shapes:** OpenAI/OpenRouter parallel: two turns, four calls (record_item x2, zero-argument count_items, lookup_item error), then a final text. Empty-args: three calls (`""`, a normal call, an absent `arguments` key), results (2, ok, 2), repaired in place and appended last. Reasoning details: two calls, one `reasoning.text` detail with a placeholder signature.
- **Per-leg test counts (floor `test` / `testOkhttp521` / `testOkhttp550`, each leg identical):** Anthropic 10, OpenAI 10, OpenRouter 8; `ConversationGoldenTest` 19 on the floor leg. 0 failures, 0 skipped.

## Task Commits

1. **Task 1: suite on OpenAI, four-call parallel golden** - `b7fc4a2` (test)
2. **Task 2: empty-arguments forms repaired exactly as the golden says** - `49585e5` (test)
3. **Task 3: suite on OpenRouter, index and reasoning_details, non-vacuity check** - `ac3043c` (test)

## Deviations from Plan

**1. [Rule 3 - Blocking] Manifest case names are unique per dialect, not globally**
- **Found during:** Task 1
- **Issue:** `parseConversationManifest` (08-04) rejected a repeated case name across dialects, but the plan's rows and acceptance greps name `derived_parallel_tools` on anthropic, openai and openrouter.
- **Fix:** the duplicate check keys on `dialect/case` (the file path was already `<dialect>/<case>.json`); the existing same-dialect duplicate test still fails as before, and a new `theSameCaseNameIsAllowedOncePerDialect` test pins the rule.
- **Files modified:** `ConversationGolden.kt`, `ConversationGoldenTest.kt`
- **Commit:** `b7fc4a2`

**2. [Placement] The empty_args_forms branch is in `verbatimViolations`, not in `MultiTurnConformanceSuite`**
- **Issue:** the plan names the suite's byte-for-byte test as the place for the golden-as-source branch.
- **Fix:** the branch is in the shared algorithm in `WireDialect.kt` (a `repairedReplayWire` member with a null default on `WireDialect`, an independent implementation in `ChatWire`). `MultiTurnConformanceSuite.kt` is therefore unchanged from 08-05 (`git diff 3b25fa3` over it prints nothing), which is stronger than the plan's "only change is the empty_args_forms branch". Behavior is as specified: the golden's next-request assistant element is compared with an independently computed repair, and its compact text must occur in the golden and in every later request.
- **Commit:** `49585e5`

**Total deviations:** 2 (one Rule 3 blocking fix, one placement choice). **Impact:** none on scope; test sources and resources only.

## Verification

- `./gradlew :providers:test --tests '*MultiTurnConformanceTest' --tests '*ConversationGoldenTest' --offline -q`: green.
- `./gradlew :providers:testOkhttp521 :providers:testOkhttp550 --tests '*MultiTurnConformanceTest' --offline`: green on both legs (report XML checked per leg).
- `./gradlew :providers:detekt --offline -q`: zero issues.
- `./gradlew check --offline -q`: exit 0.
- Hygiene: `grep -rliE 'log_food|edit_list_card|secondbrain|caltracker' providers/src/test/resources/golden/conversations/` finds nothing; `\tderived\t` manifest rows = 6; fixture files = 6.
- `git diff --stat 3b25fa3 -- providers/src/main core/ providers/build.gradle.kts providers/src/test/resources/golden/chat` prints nothing.

## Threat model

T-08-18 mitigated (allowlist projection asserted byte-for-byte per vendor; index and reasoning_details kept, annotations and reasoning dropped). T-08-19 mitigated (ids GOLDEN-only, reasoning text synthetic, hygiene sweep passes). T-08-20 mitigated (`MultiTurnConformanceSuite.kt` unchanged; the extra Chat checks live in the subclasses). Real-API acceptance of replayed Chat turns is deferred to 08-09 (coverage D6, `human_judgment`).

## Known Stubs

None.

## Self-Check: PASSED

- All six created files and the four modified files exist on the worktree branch.
- Commits `b7fc4a2`, `49585e5` and `ac3043c` exist; `git rev-list --count 3b25fa3..HEAD` was 3 before this SUMMARY commit.
- No tag, no push, no STATE.md or ROADMAP.md edit, no planning-tool artifacts staged.
