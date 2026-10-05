---
phase: 04-anthropic-transport-okhttp-matrix
plan: 01
subsystem: providers
tags: [capabilities, anthropic, forced-tool-choice, prompt-caching]
requires: []
provides:
  - "ModelCapabilities.supportsForcedToolChoice (default true) and Builder var"
  - "internal AnthropicModels.capabilities(model): verified Anthropic table, exact-id lookup"
affects: [04-03, 04-06, 04-07]
tech-stack:
  added: []
  patterns: ["per-model facts live in ModelCapabilities, patched by PipelineBuilder.capabilities"]
key-files:
  created:
    - providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/anthropic/AnthropicModels.kt
    - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/anthropic/AnthropicModelsTest.kt
  modified:
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/provider/ModelCapabilities.kt
    - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/ModelCapabilityTableTest.kt
key-decisions:
  - "Field is the last (fifth) constructor parameter of the internal constructor, per the plan's type contract"
  - "Unknown ids get forced=true, EXPLICIT_BREAKPOINTS, minimum null"
requirements-completed: [PROV-07]
status: complete
commits: 2
plan_head_before: 238d94c8a758bbeeeb6dc31dd2b86034ce8a93c5
actuals:
  tokens: 6500
  tasks: 2
  commits: 2
metrics:
  completed: 2026-09-30
---

# Phase 4 Plan 01: Forced-tool capability field and Anthropic model table Summary

The forced-tool fact is now app-overridable data in `ModelCapabilities`, with Anthropic's verified per-model defaults in an internal `:providers` table.

## What was built

- `ModelCapabilities.supportsForcedToolChoice` (public val, default true) plus `Builder.supportsForcedToolChoice`, threaded through the constructor (fifth parameter), `toBuilder`, `equals`, `hashCode`, `toString` and `build()`. `UNKNOWN` and `ModelCapabilities { }` both report true. This is the only public API delta. No other core main file changed, and no model id or family word appears in `core/src/main`.
- `internal object AnthropicModels.capabilities(model)`, exact-id matching with named private consts:

| Id | supportsForcedToolChoice | caching | minCacheablePrefixTokens |
|----|----|----|----|
| claude-opus-5-5, claude-sonnet-5-5, claude-fable-5-1, claude-mythos-5-1 | false | EXPLICIT_BREAKPOINTS | 512 |
| claude-haiku-4-5 | true | EXPLICIT_BREAKPOINTS | 4096 |
| any other id (dated, prefix, different case, new) | true | EXPLICIT_BREAKPOINTS | null |

- Tests: core cases for the default, inequality, toString and single-field patching; providers cases for the table rows, exact-id matching, and the pipeline-level proof that `capabilities(ANTHROPIC, id) { supportsForcedToolChoice = true }` reaches `session.model().capabilities` while caching and minimum keep the table values.

## Commits

- `1126f8b` feat(04-01): add supportsForcedToolChoice to ModelCapabilities
- `f7d95b9` feat(04-01): add AnthropicModels capability table with exact-id matching

## Deviations

- [Prior work] The change was applied from a saved patch produced by an earlier run that could not commit. One review adjustment: the new constructor parameter was moved from second to last position to match the plan's "fifth constructor parameter" contract (internal constructor, built only by named arguments, so no caller impact).
- Commit split follows the dispatch instruction (core field + core test, then table + providers test) rather than the plan's single tracer commit.

## Verification

- `./gradlew check --offline` green across all modules (detekt, scans, the 4.12 / 5.2.1 / 5.5.0 OkHttp legs, NoHardCodedConstantsTest, ApiShapeTest).
- `scripts/review-api-surface.sh --expect-sealed-complete` prints `API SURFACE OK`.
- No api.txt, no tags, no dependency or build-file changes, STATE.md and ROADMAP.md untouched.

## Self-Check: PASSED

Both created files exist and both commits are on the branch.
