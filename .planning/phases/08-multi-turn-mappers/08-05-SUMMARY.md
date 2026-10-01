---
phase: 08-multi-turn-mappers
plan: 05
subsystem: providers-test-conformance
tags: [conformance-suite, wire-dialect, anthropic, golden, byte-for-byte-replay, fail-loud]

requires:
  - phase: 08-multi-turn-mappers
    provides: "08-01 replay and coverage pre-flight, 08-02 call-ordered results, 08-04 golden infrastructure and ConversationScript"
provides:
  - "WireDialect seam, Anthropic binding and the shared replayConversation and verbatimViolations algorithms"
  - "MultiTurnConformanceSuite: the one abstract suite, six inherited tests"
  - "AnthropicMultiTurnConformanceTest and two derived conversation goldens"
affects: [08-06, 08-08, 08-09]

plan_head_before: 4e0785cb44b3c32ff557327bf4e5ecc96aa97452
commits: 3

actuals:
  tokens: 14000
  tasks: 3
  commits: 3

tech-stack:
  added: []
  patterns:
    - "the suite is internal and abstract; a subclass supplies only a WireDialect, so 08-06 binds Chat Completions without touching an assertion"
    - "replay violations are fixed phrases naming the turn (and request) only, never body text"
    - "byte-for-byte replay is checked as compact-text containment in the golden file and in every later request body, plus JsonElement equality at the assistant position"

key-files:
  created:
    - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/conformance/WireDialect.kt
    - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/conformance/MultiTurnConformanceSuite.kt
    - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/conformance/AnthropicMultiTurnConformanceTest.kt
    - providers/src/test/resources/golden/conversations/anthropic/derived_parallel_tools.json
    - providers/src/test/resources/golden/conversations/anthropic/derived_thinking.json
  modified:
    - providers/src/test/resources/golden/conversations/MANIFEST.tsv

key-decisions:
  - "The suite and its Anthropic subclass are internal (public in bytecode, so JUnit 4 still runs them) because WireDialect is internal and a public class cannot expose it"
  - "Result-message count is measured generically as the distance between an assistant wire message and the next one (or the end), so the same check serves a dialect whose results span several messages"
  - "Case-specific checks (four calls in turn 1, thinking block order, the tamper and broken-golden tests) live in the Anthropic subclass, not the shared suite"

requirements-completed: [XCR-01, XCR-02, XCR-03]

coverage:
  - id: D1
    description: "One abstract suite runs over every anthropic manifest row; replaying each conversation reproduces every turn's exact messages array, with four parallel calls, a zero-argument call and an error result, results batched in one user message in call order, is_error only on the error"
    requirement: "XCR-01"
    verification:
      - kind: unit
        ref: "providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/conformance/AnthropicMultiTurnConformanceTest.kt#everyFixtureConversationRoundTrips"
        status: pass
      - kind: unit
        ref: "providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/conformance/AnthropicMultiTurnConformanceTest.kt#toolResultsAreEncodedPerDialect"
        status: pass
      - kind: unit
        ref: "providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/conformance/AnthropicMultiTurnConformanceTest.kt#aBrokenGoldenIsReportedByTurn"
        status: pass
    human_judgment: false
  - id: D2
    description: "Every assistant turn's raw content array occurs byte-for-byte in the golden file and in every later request, including thinking with signatures, redacted_thinking and thinking between tool_use blocks; a changed signature fails the check for that turn"
    requirement: "XCR-02"
    verification:
      - kind: unit
        ref: "providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/conformance/AnthropicMultiTurnConformanceTest.kt#everyAssistantTurnIsReplayedByteForByte"
        status: pass
      - kind: unit
        ref: "providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/conformance/AnthropicMultiTurnConformanceTest.kt#theThinkingFixtureKeepsItsBlocksInOrderAndEveryLaterRequestRepeatsThem"
        status: pass
      - kind: unit
        ref: "providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/conformance/AnthropicMultiTurnConformanceTest.kt#aChangedSignatureFailsTheByteForByteCheckForThatTurn"
        status: pass
    human_judgment: false
  - id: D3
    description: "Through the real AnthropicProvider against MockWebServer, a turn stamped for another provider, model or raw shape fails with Other(replay_mismatch) and five coverage defects fail with their kind code, all with zero requests; the original stamp and an unstamped copy each send exactly one request"
    requirement: "XCR-02"
    verification:
      - kind: unit
        ref: "providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/conformance/AnthropicMultiTurnConformanceTest.kt#aStampForAnotherProviderOrModelFailsBeforeAnyRequest"
        status: pass
      - kind: unit
        ref: "providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/conformance/AnthropicMultiTurnConformanceTest.kt#toolCallCoverageIsEnforcedBeforeAnyRequest"
        status: pass
    human_judgment: false
  - id: D4
    description: "SC2's 'the real API accepts a replayed thinking turn' half: the thinking fixture's signatures are placeholders, so acceptance by the real API rests on the captured row from 08-09"
    requirement: "XCR-03"
    verification: []
    human_judgment: true
    rationale: "Derived fixture with placeholder signatures proves the engine's byte-for-byte replay only; real API acceptance needs a captured conversation (08-09)"

duration: 35min
completed: 2026-10-01
status: complete
---

# Phase 8 Plan 05: Multi-turn conformance suite on the Anthropic mapper Summary

**One abstract conformance suite, bound to the Anthropic mapper through a WireDialect adapter, replays two derived conversations through the production encoder, decoder and real provider and proves exact per-turn wire, call-ordered batched results, whole-history byte-for-byte replay (thinking included) and fail-loud stamp and coverage handling with zero requests.** Test sources and resources only; no main-source, Gradle or API change.

## Accomplishments

- `WireDialect.kt`: `WireDialect` (internal interface), `AnthropicWire`, `WireToolResult`, `ConversationReplay`, `replayConversation`, `verbatimViolations`, `sentMessages`, `FAKE_KEY`.
- `MultiTurnConformanceSuite.kt`: `internal abstract class` with six inherited tests (below).
- `AnthropicMultiTurnConformanceTest.kt`: binds `AnthropicWire` and adds four Anthropic-only tests (parallel fixture shape, thinking block order and repeat, signature tamper, broken golden).
- Two derived goldens and two manifest rows.

## Recorded contract details

- **plan_head_before:** `4e0785cb44b3c32ff557327bf4e5ecc96aa97452`.
- **WireDialect members:** `name`, `providerId`, `otherProviderId`, `call`, `encode`, `decode`, `storedReplay`, `expectedReplayWire`, `assistantWireIndices`, `assistantWire`, `toolResultWires`, `resultMessageCount`, `rawOfOtherShape`, `provider`, `okAnswer`.
- **Suite tests:** `theDialectHasAtLeastOneFixture`, `everyFixtureConversationRoundTrips`, `toolResultsAreEncodedPerDialect`, `everyAssistantTurnIsReplayedByteForByte`, `aStampForAnotherProviderOrModelFailsBeforeAnyRequest`, `toolCallCoverageIsEnforcedBeforeAnyRequest`.
- **Manifest rows:** `derived_parallel_tools` (anthropic, derived, `claude-haiku-4-5`, tags `parallel,zero_arg,error_result,interleaved_text`) and `derived_thinking` (anthropic, derived, `claude-sonnet-5-5`, tags `thinking,redacted_thinking,interleaved_text,zero_arg,error_result`).
- **Fixture facts:** parallel golden is two turns (four calls with a zero-argument `count_items` and an error `lookup_item`, then an end_turn text). Thinking golden is three turns: turn 1 raw is thinking, redacted_thinking, text, tool_use, thinking, tool_use; turn 2 raw is thinking then a zero-argument tool_use; turn 3 is text. Signatures are placeholders (`derived-placeholder-signature-N`). Goldens were hand-specified from the documented content-array shapes, not produced by the encoder under test, with integer usage counts and ids ending in GOLDEN.
- **Coverage variants proved with 0 requests:** other provider, other model, other shape (all `replay_mismatch`); missing result, extra result, dangling turn, results right after the prompt, duplicate call ids in an unstamped turn (`tool_result_missing`, `tool_result_unexpected` twice, `tool_call_unanswered`, `tool_call_id_duplicate`).

## Task Commits

1. **Task 1: suite, Anthropic binding, derived parallel golden** - `2d61989` (test)
2. **Task 2: derived thinking golden, whole-history byte-for-byte replay** - `998707e` (test)
3. **Task 3: stamp and coverage refusals through the real provider** - `4f1f365` (test)

## Deviations from Plan

None - plan executed as written. Two notes: the suite and its subclass are declared `internal` (needed because `WireDialect` is internal; the plan's `abstract class` grep still matches), and the fixtures were emitted by a throwaway script from hand-written structures purely to guarantee compact canonical text.

**Total deviations:** 0.

## Verification

- `./gradlew :providers:test --tests '*AnthropicMultiTurnConformanceTest' --tests '*ConversationGoldenTest' --offline -q`: green (10 + 17 tests, 0 failures); the manifest sweep accepts both new rows (canonical, hygienic, well formed).
- `./gradlew :providers:testOkhttp521 :providers:testOkhttp550 --offline` with the MultiTurn class: 10 tests, 0 failures on each leg (report XML checked per leg).
- `./gradlew :providers:detekt --offline -q`: zero issues.
- `./gradlew check --offline -q`: green.
- `git diff --stat 4e0785c -- providers/src/main core/ providers/build.gradle.kts` prints nothing.

## Threat model

T-08-15 mitigated (hand-specified goldens; the broken-golden and signature-tamper tests show the checks bite). T-08-16 mitigated (ConversationScript content only, hygiene sweep passes, assertion messages name case, request and turn only). T-08-17 mitigated (provenance `derived` in the manifest and note; SC2's real-API half deferred to 08-09 and marked human_judgment in coverage D4).

## Known Stubs

None.

## Self-Check: PASSED

- All five created files and the modified manifest exist on the worktree branch.
- Commits `2d61989`, `998707e` and `4f1f365` exist; `git rev-list --count 4e0785c..HEAD` was 3 before this SUMMARY commit.
- No tag, no push, no edit to STATE.md or ROADMAP.md, no planning-tool artifacts staged.
