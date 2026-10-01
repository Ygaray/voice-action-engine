---
phase: 08-multi-turn-mappers
plan: 01
subsystem: providers
tags: [replay, tool-calls, preflight, anthropic, chat-completions, openai, openrouter]

requires:
  - phase: 07-singleshot-strategy
    provides: the shipped Anthropic and Chat Completions transports and encoders this plan hardens
provides:
  - a pure pre-flight conversation check shared by both dialects (replay stamp and tool-call coverage)
  - typed, kind-only refusals with zero HTTP requests on Anthropic, OpenAI and OpenRouter
  - encoders that never rebuild a stamped turn
affects: [08-02, 08-03, 09-agentic-loop]

plan_head_before: 27d211783fab6268c72ca67e87bc0c23f28c4593
commits: 3

actuals:
  tokens: 9000
  tasks: 3
  commits: 3

tech-stack:
  added: []
  patterns:
    - "pre-flight refusal: a transport returns a typed Failure before the first request is built, once per logical call"
    - "kind-only failure codes as file-private constants carried by FailureReason.Other"

key-files:
  created:
    - providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/transcript/ConversationCheck.kt
    - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/transcript/ConversationCheckTest.kt
  modified:
    - providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/anthropic/AnthropicTransport.kt
    - providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ChatTransport.kt
    - providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/anthropic/AnthropicMessageEncoder.kt
    - providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ChatMessageEncoder.kt
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/transcript/Message.kt
    - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/anthropic/AnthropicTransportTest.kt
    - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ChatTransportTest.kt
    - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/anthropic/AnthropicEncoderTest.kt
    - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ChatMessageEncoderTest.kt

key-decisions:
  - "Failure codes carry the violation kind only (research A9); call ids stay recoverable from the transcript the strategy holds"
  - "An encoder that is handed a mismatched stamp throws IllegalStateException with a fixed message instead of rebuilding; transports refuse first, so this is a backstop"
  - "The two replay-key inputs are the stamp's provider and the requested model id from ProviderRequest.model, never a model read from a response"

patterns-established:
  - "preflightRefusal(call) in each transport folds the credential refusal and the conversation refusal into one return so detekt ReturnCount stays at 2"

requirements-completed: [XCR-02, XCR-03]

coverage:
  - id: D1
    description: "A transcript stamped for another model or provider, or with a wrong-shape raw turn, is refused with Other(replay_mismatch) and zero requests by Anthropic, OpenAI and OpenRouter; matching and unstamped turns send exactly one request"
    requirement: "XCR-02"
    verification:
      - kind: integration
        ref: "providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/anthropic/AnthropicTransportTest.kt#aTurnStampedForAnotherModelOrProviderIsRefusedBeforeAnyRequest"
        status: pass
      - kind: integration
        ref: "providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ChatTransportTest.kt#aTurnStampedForAnotherVendorOrModelIsRefusedBeforeAnyRequest"
        status: pass
    human_judgment: false
  - id: D2
    description: "Tool-call coverage (unanswered, duplicate id, missing result, unexpected result) is enforced before any request with a fixed precedence and kind-only reasons"
    requirement: "XCR-03"
    verification:
      - kind: unit
        ref: "providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/transcript/ConversationCheckTest.kt"
        status: pass
      - kind: integration
        ref: "AnthropicTransportTest#aCallWithNoResultIsRefusedBeforeAnyRequest, ChatTransportTest#aTrailingTurnWithAnUnansweredToolCallIsRefusedBeforeAnyRequest"
        status: pass
    human_judgment: false
  - id: D3
    description: "Neither encoder rebuilds a stamped turn: a matching stamp is replayed, a null stamp is rebuilt, any other stamp throws a fixed-message IllegalStateException"
    requirement: "XCR-02"
    verification:
      - kind: unit
        ref: "AnthropicEncoderTest#aMatchingNativeReplayIsSentVerbatimANullOneIsRebuiltAndAnyOtherIsRefused, ChatMessageEncoderTest#aReplayFromAnotherVendorProviderOrModelIsRefusedAndANullOneIsRebuilt"
        status: pass
    human_judgment: false

duration: 10min
completed: 2026-10-01
status: complete
---

# Phase 8 Plan 01: Multi-turn replay and coverage pre-flight Summary

**One pure pre-flight check, run once per logical call before any HTTP request, makes both transports refuse a mis-stamped or badly answered conversation with a typed kind-only reason, and both encoders stop rebuilding stamped turns.**

## Performance

- **Duration:** about 10 min
- **Started:** 2026-10-01T14:52:00Z
- **Completed:** 2026-10-01T14:59:00Z
- **Tasks:** 3
- **Files modified:** 11 (2 created, 9 modified)

## Accomplishments

- `conversationViolation` / `conversationRefusal` (internal, `...providers.transcript`) walk the messages in order and return the first violation. Both transports call it through a private `preflightRefusal` that also holds the credential refusal, so the check runs once per call and never per retry or reshape.
- Replay key is (stamp provider, requested model id from `ProviderRequest.model`) plus a dialect shape predicate (Anthropic needs a `JsonArray`, Chat a `JsonObject`). A null stamp is always legal.
- Tool-call coverage is enforced in the mapper: unanswered, duplicate id, missing result, unexpected result, each a typed `Failure(Other(code))` with zero requests.
- Encoders: matching stamp is replayed (Anthropic raw array, Chat allowlist projection), null stamp is rebuilt, any other stamp throws `IllegalStateException("a replay stamped for another provider or model reached the encoder")`.
- `AssistantMessage.nativeFor` KDoc reworded (comment lines only; verified with a diff against the plan base).

## The five codes and the precedence order

Codes (file-private constants, no enum, no public API): `replay_mismatch`, `tool_call_unanswered`, `tool_call_id_duplicate`, `tool_result_missing`, `tool_result_unexpected`.

Inside one assistant turn: `replay_mismatch`, `tool_call_id_duplicate`, `tool_call_unanswered`, `tool_result_missing`, `tool_result_unexpected`. Across the conversation the first violation in message order wins. A `ToolResultsMessage` whose previous message is not an assistant turn with at least one call is `tool_result_unexpected`.

## Task Commits

1. **Task 1 (tracer): mismatched stamp refused before any request** - `4c54584` (feat)
2. **Task 2: tool-call coverage, precedence, kind-only reasons, canary** - `0bf9360` (feat)
3. **Task 3: encoders never rebuild a stamped turn; two tests reversed on purpose** - `16d1247` (feat)

## Tracer gate

The tracer's `<verify>` (the two transport test classes plus detekt and `scanBannedConstructs`) was green at commit time and again after expansion. Logged: tracer verified end to end, expanded.

## Pre-existing tests whose transcript had to be fixed

None. The full `./gradlew :providers:test` run stayed green after the guard and encoder changes, so no test transcript needed fixing and the guard was never weakened. The only intentional test changes are the two reversals named in the plan:

- `AnthropicEncoderTest.aMatchingNativeReplayIsSentVerbatimAndAnyOtherIsRebuiltFromParts` renamed to `aMatchingNativeReplayIsSentVerbatimANullOneIsRebuiltAndAnyOtherIsRefused`
- `ChatMessageEncoderTest.aReplayFromAnotherVendorProviderOrModelIsRebuilt` renamed to `aReplayFromAnotherVendorProviderOrModelIsRefusedAndANullOneIsRebuilt` (also gained a wrong-shape stamp case)

## Deviations from Plan

None - plan executed as written. Two small implementation notes, neither a deviation in behavior:

- To satisfy detekt `ReturnCount` (max 2) the transports route the credential refusal and the conversation refusal through one private `preflightRefusal(call)`, rather than a second early return in `send`. The acceptance greps (`conversationRefusal(call, ProviderId.ANTHROPIC` and `conversationRefusal(call, vendor.providerId`) still match.
- Two lines exceeded the 120-column limit on first write and were reflowed.

**Total deviations:** 0.

## Verification

- `./gradlew :providers:test --offline -q` green (floor leg); `./gradlew :core:detekt :providers:detekt :providers:scanBannedConstructs --offline -q` green.
- `./gradlew check --offline -q` exits 0 (all modules, all OkHttp matrix legs, detekt).
- `git diff --stat 27d211783fab6268c72ca67e87bc0c23f28c4593 -- providers/src/test/resources` prints nothing.
- `:core` change is KDoc-only (diff of `core/src/main` against the plan base shows comment lines only).
- Acceptance greps for all three tasks pass; `grep -cE '^(internal )?const val'` on `ConversationCheck.kt` prints 0.

## Threat model

T-08-01 mitigated (pre-flight plus encoder backstop), T-08-02 mitigated (kind-only codes, canary test on `toString`, `reason.code` and `reason.toString`), T-08-03 mitigated (coverage check runs before any request), T-08-04 mitigated (no guard weakened, no test transcript altered).

## Known Stubs

None.

## Self-Check: PASSED

- Created files exist: `ConversationCheck.kt`, `ConversationCheckTest.kt`.
- Commits `4c54584`, `0bf9360`, `16d1247` exist on the worktree branch; `git rev-list --count` from plan_head_before to HEAD is 3.
