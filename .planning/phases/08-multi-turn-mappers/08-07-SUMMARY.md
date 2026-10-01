---
phase: 08-multi-turn-mappers
plan: 07
subsystem: providers-test-conformance
tags: [conformance-suite, cache-prefix, append-only, cache-directive, reshape]

requires:
  - phase: 08-multi-turn-mappers
    provides: "08-05 abstract suite and WireDialect seam, 08-06 ChatWire and the Chat goldens"
provides:
  - "appendOnlyViolation(previous, next): the byte-prefix and tail check, the same for every dialect"
  - "WireDialect.cacheDirectiveViolation: the per-dialect cache directive check"
  - "rowRequest(row, messages, tools): the one place a manifest row's tags become request settings"
  - "suite tests everyIterationOnlyAppendsToTheCachedPrefix, aRewriteOfAnEarlierTurnOrOfTheToolsFailsTheAppendOnlyCheck, theCacheDirectiveIsTheDialectsOwnOnEveryIteration"
  - "AnthropicMultiTurnConformanceTest.reshapeIsSingleTurnOnly and a KDoc paragraph on encodeAnthropicRequest"
affects: [08-08, 08-09, phase-09-agentic-loop]

plan_head_before: fb2fc949ce553d1618e887e154aa49459ee3b4dd
commits: 2

actuals:
  tokens: 14000
  tasks: 2
  commits: 2

tech-stack:
  added: []
  patterns:
    - "append-only is checked on bytes: head = body start through the end of the last message, tail = everything after the messages array"
    - "negative controls are built through ConversationScript/rowRequest and the production encoders, with an unchanged-history control proving only the mutation differs"

key-files:
  created: []
  modified:
    - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/conformance/WireDialect.kt
    - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/conformance/MultiTurnConformanceSuite.kt
    - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/conformance/AnthropicMultiTurnConformanceTest.kt
    - providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/anthropic/AnthropicEncoder.kt

key-decisions:
  - "The head stops one character short of the messages array's closing bracket, so a longer next array still starts with the previous head; the tail starts after the bracket"
  - "The earlier-turn rewrite controls compare the real last request of the row with a hypothetical next request (history plus one more user message), so they work for a two-turn row and need no third golden turn"
  - "Replayer now builds its requests through rowRequest, so the thinking token limit and long-system rules exist once"

requirements-completed: [XCR-01, XCR-02]

coverage:
  - id: D1
    description: "Every consecutive request pair of every manifest row on all three dialects only appends: the previous head is a prefix of the next body and the tail is identical"
    requirement: "XCR-01"
    verification:
      - kind: unit
        ref: "providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/conformance/MultiTurnConformanceSuite.kt#everyIterationOnlyAppendsToTheCachedPrefix"
        status: pass
  - id: D2
    description: "A rewrite of an earlier user message, an earlier assistant turn, or a tool description fails the check on all three dialects (the unchanged control passes)"
    requirement: "XCR-01"
    verification:
      - kind: unit
        ref: "providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/conformance/MultiTurnConformanceSuite.kt#aRewriteOfAnEarlierTurnOrOfTheToolsFailsTheAppendOnlyCheck"
        status: pass
  - id: D3
    description: "Anthropic carries exactly one ephemeral cache_control on the last system block on every iteration; OpenAI and OpenRouter (including the anthropic/* route) carry none; a stray directive is caught"
    requirement: "XCR-02"
    verification:
      - kind: unit
        ref: "providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/conformance/MultiTurnConformanceSuite.kt#theCacheDirectiveIsTheDialectsOwnOnEveryIteration"
        status: pass
  - id: D4
    description: "Reshape (forced tool on a model that cannot be forced) breaks append-only across two turns; Auto does not; the encoder KDoc says a multi-turn loop uses ToolChoice.Auto"
    requirement: "XCR-01"
    verification:
      - kind: unit
        ref: "providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/conformance/AnthropicMultiTurnConformanceTest.kt#reshapeIsSingleTurnOnly"
        status: pass
  - id: D5
    description: "./gradlew :providers:check is green on all OkHttp legs, with detekt and the scanners"
    verification:
      - kind: build
        ref: "./gradlew :providers:check --offline -q"
        status: pass
---

# Phase 8 Plan 07: Cache prefix and directive conformance Summary

Every iteration of every golden conversation, on Anthropic, OpenAI and OpenRouter, is now proven to only append to a byte-stable cached prefix, and each dialect is proven to carry exactly its own cache directive.

## Definition of head and tail (for reviewers)

`appendOnlyViolation(previous, next)` finds the messages array of each body as the compact text of the parsed `messages` value, preceded by the key, and requires exactly one occurrence.
- head = the body from its first byte through the last character of the last message (the array text minus its closing bracket);
- tail = the body after the array's closing bracket (Anthropic: the closing brace; Chat: tools, tool_choice and every later key).

`next` must start with the previous head ("prefix changed" otherwise) and the tails must be equal ("tail changed" otherwise). Violations are fixed phrases and never carry body text.

## Tasks

1. Append-only predicate, `rowRequest`, the every-iteration test and the three negative controls (earlier user message, earlier assistant turn as an unstamped edited copy, a tool description). Commit 529c274.
2. `cacheDirectiveViolation` on both bindings, the directive test (with a stray-directive control), `reshapeIsSingleTurnOnly`, and the KDoc paragraph. Commit 394cb32.

## Deviations from Plan

None to behaviour. Two small additions inside the plan's files: the directive test also checks that a stray directive is caught (so the check bites), and `Replayer` was moved onto the new `rowRequest` helper to avoid a second copy of the row-to-settings rules.

## Carry-forward for Phase 9

- The agentic loop must use `ToolChoice.Auto`. Reshape is single-turn only: with a forced tool on a model that cannot be forced it moves the instruction line to the newest user message every turn, rewriting the cached prefix (pinned by `reshapeIsSingleTurnOnly`).
- OpenAI loops whose tools are strict-eligible are effectively one call per turn (strict switches parallel calls off).

## Verification

- `./gradlew :providers:test --tests '*MultiTurnConformanceTest' --offline -q` and `./gradlew :providers:check --offline -q` both exit clean (detekt zero issues, all OkHttp legs).
- main-source diff against plan_head_before is comment lines only; no change under `providers/src/test/resources` or `core/`.

## Self-Check: PASSED

- Commits 529c274 and 394cb32 exist on the worktree branch.
- All four modified files exist and contain the symbols named above.
