---
phase: 08-multi-turn-mappers
plan: 04
subsystem: providers-test-infrastructure
tags: [golden, manifest, sanitizer, canonical-json, hygiene, conversation-script]

requires:
  - phase: 08-multi-turn-mappers
    provides: "08-03 empty-arguments decode and Chat replay repair"
provides:
  - "Strict golden/conversations manifest, loader and conversation shape check"
  - "Canonical printer (the engine's own Json encoder) and a tree-aware hygiene scan"
  - "ConversationSanitizer: one id map per conversation, reasoning keys never rewritten"
  - "ConversationScript: the one synthetic script (system text, prompt, tools, results, request builder)"
affects: [08-05, 08-06, 08-08, 08-09]

plan_head_before: aa3916c096913cc3f14094b82a558d10545b951c
commits: 2

actuals:
  tokens: 11500
  tasks: 2
  commits: 2

tech-stack:
  added: []
  patterns:
    - "one canonical printer: Json.encodeToString(JsonElement.serializer(), parsed); goldens are stored already canonical and the sweep asserts text.trim() == canonicalJson(text)"
    - "ids are renamed through one conversation-wide map by exact original value, so a turn-1 response id and the same id in the turn-2 messages line up"
    - "reasoning subtrees are copied untouched and only scanned for secrets (refuse, never rewrite)"

key-files:
  created:
    - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/conformance/ConversationGolden.kt
    - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/conformance/ConversationGoldenTest.kt
    - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/conformance/ConversationScript.kt
    - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/conformance/ConversationSanitizer.kt
    - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/conformance/ConversationSanitizerTest.kt
    - providers/src/test/resources/golden/conversations/MANIFEST.tsv
  modified: []

key-decisions:
  - "The Phase 5 sanitizer stays untouched; only its KEY_IN_TEXT, BEARER_IN_TEXT and GOLDEN_TAIL constants are imported"
  - "Conversation id numbering follows first appearance per prefix; ids already ending in GOLDEN are kept and not counted, as specified (so a mixed raw-plus-GOLDEN body could collide; the recorder only feeds raw bodies)"
  - "sanitize() refuses a root object whose error key is non-null; conversation() applies it to each turn's response, so tool arguments that happen to contain an error key inside messages are not refused"

requirements-completed: [XCR-03]

coverage:
  - id: D1
    description: "The manifest is parsed strictly (seven columns, dialect, provenance equal to the case prefix, file <dialect>/<case>.json, known tags, unique cases) and every failure names its line number"
    requirement: "XCR-03"
    verification:
      - kind: unit
        ref: "providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/conformance/ConversationGoldenTest.kt#badManifestsFailWithTheirLineNumber"
        status: pass
      - kind: unit
        ref: "providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/conformance/ConversationGoldenTest.kt#aWellFormedRowParsesAndTagsSplitOnCommas"
        status: pass
    human_judgment: false
  - id: D2
    description: "Goldens must be canonical compact JSON and parse into turns of exactly {messages, response}; the manifest sweep checks existence, canonical form, hygiene and shape per row"
    requirement: "XCR-03"
    verification:
      - kind: unit
        ref: "providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/conformance/ConversationGoldenTest.kt#canonicalJsonCompactsNormalizesNumbersAndIsIdempotent"
        status: pass
      - kind: unit
        ref: "providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/conformance/ConversationGoldenTest.kt#malformedConversationsAreRejected"
        status: pass
      - kind: unit
        ref: "providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/conformance/ConversationGoldenTest.kt#everyManifestRowIsPresentCanonicalHygienicAndWellFormed"
        status: pass
    human_judgment: false
  - id: D3
    description: "Hygiene is tree-aware: keys and Bearer values flagged everywhere, non-GOLDEN ids flagged everywhere except under the reasoning keys, and no message repeats the offending text"
    requirement: "XCR-03"
    verification:
      - kind: unit
        ref: "providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/conformance/ConversationGoldenTest.kt#hygieneFlagsKeysBearerValuesAndRealIds"
        status: pass
      - kind: unit
        ref: "providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/conformance/ConversationGoldenTest.kt#hygieneAcceptsGoldenIdsAndExemptReasoningText"
        status: pass
      - kind: unit
        ref: "providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/conformance/ConversationGoldenTest.kt#hygieneNeverRepeatsTheOffendingText"
        status: pass
    human_judgment: false
  - id: D4
    description: "The sanitizer renames ids consistently across every turn (Anthropic and Chat shapes), leaves thinking, signature, data, reasoning and reasoning_details byte-identical, redacts or refuses secrets, drops pricing and identity keys, refuses error bodies, and is stable under a second pass"
    requirement: "XCR-03"
    verification:
      - kind: unit
        ref: "providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/conformance/ConversationSanitizerTest.kt#anAnthropicConversationKeepsOneIdMapAcrossTurns"
        status: pass
      - kind: unit
        ref: "providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/conformance/ConversationSanitizerTest.kt#aChatConversationKeepsCallNumbersAndCleansTheEnvelope"
        status: pass
      - kind: unit
        ref: "providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/conformance/ConversationSanitizerTest.kt#reasoningValuesComeOutByteIdentical"
        status: pass
      - kind: unit
        ref: "providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/conformance/ConversationSanitizerTest.kt#aKeyOutsideReasoningIsRedactedButInsideItRefusesTheConversation"
        status: pass
      - kind: unit
        ref: "providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/conformance/ConversationSanitizerTest.kt#aResponseHoldingAnErrorObjectIsRefused"
        status: pass
      - kind: unit
        ref: "providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/conformance/ConversationSanitizerTest.kt#outputIsCanonicalHygienicAndStableUnderASecondPass"
        status: pass
    human_judgment: false
  - id: D5
    description: "ConversationScript is the single domain-free source of the system text, prompt, three non-strict tools and fixed results"
    requirement: "XCR-03"
    verification:
      - kind: unit
        ref: "providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/conformance/ConversationSanitizerTest.kt#theScriptHoldsThreeNonStrictDomainFreeTools"
        status: pass
      - kind: unit
        ref: "providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/conformance/ConversationSanitizerTest.kt#theScriptAnswersEachToolWithItsFixedResult"
        status: pass
      - kind: unit
        ref: "providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/conformance/ConversationSanitizerTest.kt#aRequestUsesAutoChoiceCachedPrefixAndTheGivenMaxTokens"
        status: pass
    human_judgment: false

duration: 30min
completed: 2026-10-01
status: complete
---

# Phase 8 Plan 04: Conversation golden infrastructure Summary

**A strict, canonical, hygiene-checked home for conversation goldens, a sanitizer that keeps ids consistent across turns and never touches thinking or reasoning, and one synthetic script for every later plan.** Test sources only; no main-source, Gradle or API change.

## Accomplishments

- `ConversationGolden.kt`: `parseConversationManifest`, `conversationRows`, `conversationText`, `parseConversation`, `canonicalJson`, `conversationHygieneViolations`, plus `ConversationRow` and `ConversationTurn` (plain classes whose `toString` prints only the case or the message count). The three Phase 5 regex constants are imported, not copied.
- `ConversationSanitizer.kt`: `ConversationSanitizer.sanitize(element)` and `.conversation(turns)`, and `RecordedTurn`. `conversation()` returns canonical compact `{"turns":[...]}` text and fails if the output breaks any hygiene rule.
- `ConversationScript.kt`: tools, fixed results, `request(...)`, `longSystem()` (32000+ characters, deterministic), `MAX_TOKENS` 1024, `THINKING_MAX_TOKENS` 2048.
- `MANIFEST.tsv`: comment lines and the header only; rows arrive in 08-05, 08-06 and 08-09.

## Recorded contract details

- **plan_head_before:** `aa3916c096913cc3f14094b82a558d10545b951c`.
- **Manifest columns (7, tab separated):** `case`, `dialect`, `provenance`, `model`, `file`, `tags`, `note`. Dialects: `anthropic`, `openai`, `openrouter`. Provenance `derived` or `captured`, and the case name starts with `<provenance>_`. `file` equals `<dialect>/<case>.json`. A tags value of `-` means none.
- **Tag set (closed):** `parallel`, `zero_arg`, `error_result`, `interleaved_text`, `thinking`, `redacted_thinking`, `reasoning_details`, `empty_args_forms`, `long_system`.
- **Exempt keys:** `thinking`, `signature`, `data`, `reasoning`, `reasoning_details`.
- **Dropped keys (any depth):** `service_tier`, `cost`, `cost_details`, `user_id`; `created` becomes 0, `system_fingerprint` becomes null.
- **Script tools (all `strict = false`):** `record_item(item)` with one required string; `count_items` with schema exactly `{"type":"object","properties":{},"additionalProperties":false}`; `lookup_item(name)` with one required string. Results: `record_item` -> "ok", `count_items` -> "2", `lookup_item` -> error "no item with that name", any other tool -> error "unknown tool". Requests use `ToolChoice.Auto()`, `CacheDirective(true)` and `singleToolCall = false`.
- **Golden layout:** `golden/conversations/<dialect>/<case>.json`, one canonical compact object per conversation.

## Task Commits

1. **Task 1: strict manifest, canonical printer, tree-aware hygiene** - `8b4d71d` (test)
2. **Task 2: shared script and conversation-wide sanitizer** - `c936610` (test)

## Deviations from Plan

None - plan executed as written. Two small self-corrections during Task 2: detekt flagged three over-long lines in the new test and script files (reflowed), and the domain-free test originally listed two banned names literally, which would have tripped the plan's own grep; it now checks the neutral substrings `food`, `note` and `card` only.

**Total deviations:** 0.

## Verification

- `./gradlew :providers:test --tests '*ConversationGoldenTest' --offline -q`: 17 tests, 0 failures.
- `./gradlew :providers:test --tests '*ConversationSanitizerTest' --tests '*ConversationGoldenTest' --offline -q`: 31 tests, 0 failures.
- `./gradlew :providers:compileTestKotlin --offline -q`, and `grep -rniE 'log_food|edit_list_card|secondbrain|caltracker'` over the conformance package and `golden/conversations` finds nothing.
- `./gradlew :providers:detekt --offline -q`: zero issues.
- `./gradlew check --offline -q`: green, including the `testOkhttp521` and `testOkhttp550` legs.
- `git diff --stat aa3916c -- providers/src/main core/ providers/build.gradle.kts providers/src/test/resources/golden/chat providers/src/test/kotlin/.../providers/chat` prints nothing.
- Acceptance greps for both tasks pass (`internal fun parseConversationManifest(`, the imported `KEY_IN_TEXT`, the manifest header line, `internal object ConversationScript`, `internal class ConversationSanitizer`, `strict = false` count of 3).

## Threat model

T-08-11 mitigated (secrets replaced outside exempt keys, refused inside them; hygiene runs on every sanitizer output and every manifest row). T-08-12 mitigated (shared-map GOLDEN renaming plus the id rule in hygiene). T-08-13 mitigated (exempt subtrees copied untouched, byte-identity test). T-08-14 mitigated (canonical check in the manifest sweep).

## Known Stubs

None. The manifest is intentionally header-only; the sweep over it is vacuous until 08-05 and the non-vacuity check lands in 08-06.

## Self-Check: PASSED

- All six created files exist on the worktree branch.
- Commits `8b4d71d` and `c936610` exist; `git rev-list --count aa3916c..HEAD` is 2 before this SUMMARY commit.
- No tag, no push, no edit to STATE.md or ROADMAP.md, no staged planning-tool artifacts.
