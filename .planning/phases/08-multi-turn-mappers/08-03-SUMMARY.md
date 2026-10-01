---
phase: 08-multi-turn-mappers
plan: 03
subsystem: providers
tags: [tool-arguments, empty-arguments, chat-completions, anthropic, replay-repair]

requires:
  - phase: 08-multi-turn-mappers
    provides: "08-02 call-ordered tool results and the Chat error wrapper"
provides:
  - "isEmptyArgumentsForm: the one internal predicate for 'no arguments' shared by the Chat decoder and the replay repair"
  - "Chat and Anthropic decoders turn every empty-arguments spelling into an empty object"
  - "Chat replay repairs only a missing role and an empty arguments value"
affects: [08-04, 09-agentic-loop]

plan_head_before: 16d254cb166df0593dbc17bf477d165520c5de8c
commits: 2

actuals:
  tokens: 9000
  tasks: 2
  commits: 2

tech-stack:
  added: []
  patterns:
    - "decoder and replay repair share one predicate, so what decodes as {} replays as \"{}\""
    - "repair is a pure function of the stored turn, so the cached prefix is identical across iterations"

key-files:
  created: []
  modified:
    - providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ChatResponseParts.kt
    - providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/anthropic/AnthropicDecoder.kt
    - providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ChatMessageEncoder.kt
    - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ChatDecoderTest.kt
    - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/anthropic/AnthropicDecoderTest.kt
    - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ChatMessageEncoderTest.kt

key-decisions:
  - "Research A4: the replayed Chat copy is repaired only for a missing role (added as first key) and an empty arguments form (becomes \"{}\" in place, appended last when absent); a valid replay is untouched and ids are never touched"
  - "The Anthropic replay is never repaired; the NativeReplay raw of both dialects is never rewritten by the decoder"

requirements-completed: [XCR-03]

coverage:
  - id: D1
    description: "Chat arguments that are absent, JSON null, blank, or the string null decode to an empty object; objects and '{}' still decode; real malformations stay malformed_tool_args without echoing their text"
    requirement: "XCR-03"
    verification:
      - kind: unit
        ref: "providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ChatDecoderTest.kt#emptyArgumentFormsDecodeAsAnEmptyObject"
        status: pass
      - kind: unit
        ref: "providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ChatDecoderTest.kt#badArgumentsAreMalformedToolArgs"
        status: pass
      - kind: unit
        ref: "providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ChatDecoderTest.kt#theReplayRawKeepsEmptyArgumentsExactlyAsReceived"
        status: pass
    human_judgment: false
  - id: D2
    description: "Anthropic tool_use input that is absent or null decodes to an empty object; an array, string or number input stays malformed_tool_args; the raw content array is unchanged"
    requirement: "XCR-03"
    verification:
      - kind: unit
        ref: "providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/anthropic/AnthropicDecoderTest.kt#anAbsentOrNullToolInputDecodesAsAnEmptyObject"
        status: pass
      - kind: unit
        ref: "providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/anthropic/AnthropicDecoderTest.kt#aToolInputThatIsNotAnObjectIsMalformedToolArgs"
        status: pass
    human_judgment: false
  - id: D3
    description: "The Chat replay stays the allowlist projection in stored order and is repaired only where it would be invalid input, deterministically"
    requirement: "XCR-03"
    verification:
      - kind: unit
        ref: "providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ChatMessageEncoderTest.kt#aReplayIsRepairedOnlyWhereItWouldBeInvalidInput"
        status: pass
      - kind: unit
        ref: "providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ChatMessageEncoderTest.kt#theRepairIsDeterministic"
        status: pass
      - kind: unit
        ref: "providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ChatMessageEncoderTest.kt#aMatchingReplayKeepsOnlyTheAllowedFieldsInStoredOrder"
        status: pass
    human_judgment: false

duration: 10min
completed: 2026-10-01
status: complete
---

# Phase 8 Plan 03: Empty arguments decode as {}, Chat replay repaired only where invalid Summary

**A zero-argument tool call now decodes as `{}` however the provider spells "nothing" on both dialects, and the Chat replay is repaired only for a missing role or an empty arguments value.**

## Accomplishments

- `isEmptyArgumentsForm(element)` (internal, in `ChatResponseParts.kt`) is true for an absent key, JSON null, and a string that is blank or reads `null` after trimming. `decodeArguments` checks it first; objects are used as is; any other string is parsed; a number, array or boolean stays `MalformedToolArgs`. The empty-string branch left `parseArguments`.
- Anthropic `decodeToolUse` takes an absent or null `input` as `{}`; an array, string or number input stays `MalformedToolArgs`. `decodeResponse` still stores the untouched content array as the replay.
- `ChatMessageEncoder.repaired` filters to the unchanged allowlist, adds `"role":"assistant"` as the first key when role is missing, and rewrites an empty-arguments form to `"{}"` at the same key position (appended last when absent). Every other entry, key and id is the same instance.

## Task Commits

1. **Task 1: empty-arguments forms decode as {} on both decoders** - `3cd526c` (feat)
2. **Task 2: Chat replay repairs only what would be invalid input** - `c141830` (feat)

## Tests narrowed on purpose

- `ChatDecoderTest.badArgumentsAreMalformedToolArgs`: removed `"null"`, `" "`, JSON null and the missing key (moved to `emptyArgumentFormsDecodeAsAnEmptyObject`); kept the malformed strings, with the canary asserted absent from the failure; added a JSON number, array and boolean given as non-strings.
- `AnthropicDecoderTest.aToolInputThatIsNotAnObjectIsMalformedToolArgs`: removed `JsonNull` (moved to `anAbsentOrNullToolInputDecodesAsAnEmptyObject`); added a number input.
- The golden/chat derived cases (`openai_args_invalid_json`, `_array`, `_scalar`) still replay as malformed; `providers/src/test/resources` is byte-identical to the plan base.

## Plan-level decision A4

Repair-only normalization, as written in the plan. A valid replay is returned unchanged, ids are never touched, the Anthropic array is never repaired, and the decoder never rewrites the stored raw (a test shows a turn whose arguments were `""` keeps `""` in the stored raw while the next request carries `"{}"`).

## Deviations from Plan

None - plan executed as written. Two detekt findings during Task 2 (file function count, return count) were resolved by folding the allowlist filter into `repaired` and shaping `repairedCall` as one expression; the allowlist set and stored-order filter are unchanged.

**Total deviations:** 0.

## Verification

- `./gradlew :providers:test --tests '*ChatDecoderTest' --tests '*AnthropicDecoderTest' --tests '*ChatGoldenReplayTest' --tests '*ChatAbsentOptionalTest' --offline -q` green.
- `./gradlew :providers:test --tests '*ChatMessageEncoderTest' --tests '*ChatTransportTest' --tests '*ChatCanaryTest' --offline -q` green; `./gradlew :providers:test --offline -q` green.
- `./gradlew :providers:detekt :providers:scanBannedConstructs --offline -q` green; `./gradlew check --offline -q` green.
- `git diff --stat 16d254cb166df0593dbc17bf477d165520c5de8c -- providers/src/test/resources core/` prints nothing.
- Acceptance greps for both tasks pass.

## Threat model

T-08-08 mitigated (one predicate with an explicit list; malformed-list tests kept). T-08-09 mitigated (canary asserted absent from the failure). T-08-10 mitigated (repair only on missing role and empty forms; byte-identity and determinism tests).

## Known Stubs

None.

## Self-Check: PASSED

- Commits `3cd526c` and `c141830` exist on the worktree branch; `git rev-list --count 16d254c..HEAD` is 2 before this SUMMARY commit.
- All six modified files exist; no files created apart from this SUMMARY.
