---
phase: 08-multi-turn-mappers
plan: 02
subsystem: providers
tags: [tool-results, call-order, anthropic, chat-completions, error-wrapper]

requires:
  - phase: 08-multi-turn-mappers
    provides: "08-01 pre-flight coverage check and ConversationCheck.kt (the file the shared helper joins)"
provides:
  - "resultsInCallOrder: one internal helper that puts a turn's tool results in the emission order of the calls they answer"
  - "Anthropic tool_result blocks in call order, with no content key when the text is empty"
  - "Chat role:tool messages in call order, with an error result wrapped as {\"error\": <text>}"
affects: [08-03, 09-agentic-loop]

plan_head_before: 4aa7a4a72394af50f7d102f4706cde47a3db33bc
commits: 2

actuals:
  tokens: 9000
  tasks: 2
  commits: 2

tech-stack:
  added: []
  patterns:
    - "the preceding AssistantMessage's tool calls are passed to the results encoder, which orders by them; with no such turn the helper returns the results unchanged"
    - "error wrapper built with buildJsonObject and Json.encodeToString, never concatenated"

key-files:
  created: []
  modified:
    - providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/transcript/ConversationCheck.kt
    - providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/anthropic/AnthropicMessageEncoder.kt
    - providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ChatMessageEncoder.kt
    - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/transcript/ConversationCheckTest.kt
    - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/anthropic/AnthropicEncoderTest.kt
    - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ChatMessageEncoderTest.kt

key-decisions:
  - "Research A3: an empty non-error Chat result is sent as an empty string with no placeholder"
  - "An error result on Chat is the compact object {\"error\":<text>}; every other result, including a held-for-confirmation JSON string, is sent byte for byte"

requirements-completed: [XCR-03]

coverage:
  - id: D1
    description: "Results of a turn are encoded in the emission order of the calls they answer on both dialects, whatever order the app returned them in"
    requirement: "XCR-03"
    verification:
      - kind: unit
        ref: "providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/transcript/ConversationCheckTest.kt#resultsFollowTheCallsEmissionOrder"
        status: pass
      - kind: unit
        ref: "providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/anthropic/AnthropicEncoderTest.kt#resultsGoOutInTheOrderOfTheCallsTheyAnswerInOneUserMessage"
        status: pass
      - kind: unit
        ref: "providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ChatMessageEncoderTest.kt#resultsAnsweredOutOfOrderAreSentInTheOrderOfTheCalls"
        status: pass
    human_judgment: false
  - id: D2
    description: "Anthropic: is_error only on errors, no content key for empty text (is_error kept), reshape instruction still last"
    requirement: "XCR-03"
    verification:
      - kind: unit
        ref: "providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/anthropic/AnthropicEncoderTest.kt#anEmptyResultCarriesNoContentKeyAndAnEmptyErrorKeepsIsError"
        status: pass
      - kind: unit
        ref: "providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/anthropic/AnthropicEncoderTest.kt#theReshapeInstructionStaysAfterTheOrderedResults"
        status: pass
    human_judgment: false
  - id: D3
    description: "Chat: an error is wrapped as {\"error\": text} with quote, backslash and newline surviving a parse back; non-error content passes through unchanged"
    requirement: "XCR-03"
    verification:
      - kind: unit
        ref: "providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ChatMessageEncoderTest.kt#anErrorTextWithQuoteBackslashAndNewlineParsesBackToTheOriginal"
        status: pass
      - kind: unit
        ref: "providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ChatMessageEncoderTest.kt#aHeldForConfirmationResultIsSentByteForByteUnchanged"
        status: pass
    human_judgment: false
  - id: D4
    description: "Empty Chat tool content is sent as an empty string; whether a given route accepts it is unproven against a live endpoint"
    requirement: "XCR-03"
    verification:
      - kind: unit
        ref: "providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ChatMessageEncoderTest.kt#anEmptyResultIsSentAsAnEmptyStringAndAnEmptyErrorIsStillWrapped"
        status: pass
    human_judgment: true
    rationale: "No test can show a real Chat route accepts empty role:tool content; only a captured live conversation can"

duration: 8min
completed: 2026-10-01
status: complete
---

# Phase 8 Plan 02: Tool results in call order, Chat error wrapper Summary

**Both dialects now send a turn's tool results in the order the model made the calls, Anthropic omits `content` when there is no text, and Chat marks a failed tool as `{"error": <text>}` so the model can tell it failed.**

## Performance

- **Duration:** about 8 min
- **Tasks:** 2
- **Files modified:** 6 (0 created)

## Accomplishments

- `resultsInCallOrder(results, calls)` (internal, in `ConversationCheck.kt`) sorts stably by the call-id position. A result for no call keeps its relative order after the matched ones, and an empty call list leaves the results unchanged, so a direct encoder caller with no preceding turn loses nothing.
- Anthropic: `toolResultsMessage` takes the preceding assistant turn's calls, orders by them, writes `content` only when the text is non-empty, and keeps `is_error` on errors. The reshape instruction is still the last block of the results message.
- Chat: `toolMessages` orders the same way. `toolContent` wraps an error as the compact object built with `buildJsonObject` and `Json.encodeToString`, and passes any other result through unchanged. There is no `is_error` key anywhere in Chat output.

## Task Commits

1. **Task 1: results follow the calls' emission order, Anthropic omits empty content** - `215737c` (feat)
2. **Task 2: Chat call order and `{"error": ...}` wrapper** - `a61fd0a` (feat)

## Test reversed on purpose

`ChatMessageEncoderTest.eachToolResultIsItsOwnToolMessageInOrderAndErrorsAreSentUnchanged` was renamed to `eachToolResultIsItsOwnToolMessageInCallOrderAndAnErrorIsWrapped`, and its assertion now expects the wrapped error content. This is the planned reversal (D-05 supersedes the earlier no-wrapper behavior), not a regression. Every other encoder test passed unchanged.

## Plan-level decision A3 and its risk

Research A3: an empty non-error Chat result is sent as `""`, with no placeholder or space. A placeholder would change what the model sees, which is a product decision this phase has no basis for.
Risk line: nothing documents a Chat route rejecting empty `role:tool` content, but it is unproven. If a captured Chat conversation ever answers 400 on empty tool content, it comes back as a gap. This is not a contract conflict. The plan did not list `COVERAGE.md` in its modified files, so the risk is recorded here only; the orchestrator can copy it into COVERAGE.md at phase close.

## Deviations from Plan

None - plan executed as written. The test helper `resultBlocks` in `AnthropicEncoderTest` lost an unused parameter before commit, and the long `encodeMessages` call line was split into a private `precedingCalls` helper to stay under 120 columns. Neither changes behavior.

**Total deviations:** 0.

## Verification

- `./gradlew :providers:test --tests '*ConversationCheckTest' --tests '*AnthropicEncoderTest' --tests '*AnthropicForcedToolTest' --offline -q` green.
- `./gradlew :providers:test --tests '*ChatMessageEncoderTest' --tests '*ChatEncoderTest' --tests '*ChatTransportTest' --offline -q` green; `./gradlew :providers:test --offline -q` green.
- `./gradlew :providers:detekt :providers:scanBannedConstructs --offline -q` green.
- `./gradlew check --offline -q` exits 0 (all modules, OkHttp matrix legs, detekt).
- `git diff --stat 4aa7a4a72394af50f7d102f4706cde47a3db33bc -- providers/src/test/resources core/` prints nothing.
- Acceptance greps for both tasks pass.

## Threat model

T-08-05 mitigated (builder plus `Json.encodeToString`, quote/backslash/newline parse-back test). T-08-06 mitigated (`resultsInCallOrder` on both dialects, reversed-order tests). T-08-07 mitigated (the error wrapper).

## Known Stubs

None.

## Self-Check: PASSED

- Commits `215737c` and `a61fd0a` exist on the worktree branch; `git rev-list --count 4aa7a4a..HEAD` is 2 before this SUMMARY commit.
- All six modified files exist; no files created.
