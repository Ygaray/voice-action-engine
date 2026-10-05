---
phase: 05-openai-openrouter-transports
plan: 01
subsystem: providers
tags: [openai, chat-completions, strict-mode, json-schema, optional-detector]
requires: []
provides:
  - "hasOptionalProperties: deeper shared walk (anyOf, oneOf, allOf, $defs, definitions, prefixItems, array-form items, schema-valued additionalProperties)"
  - "isChatStrictEligible / effectiveChatStrict (internal, chat package)"
  - "stripForChatStrict (internal, chat package)"
affects: [05-04 encoder, 05-10 absent-optional contract]
tech-stack:
  added: []
  patterns: ["one shared optional detector", "data-driven keyword lists with source comment and check date"]
key-files:
  created:
    - providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ChatStrict.kt
    - providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ChatSchemaStrip.kt
    - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/schema/OptionalPropertiesTest.kt
    - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ChatStrictTest.kt
  modified:
    - providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/schema/OptionalProperties.kt
key-decisions:
  - "Nesting depth counts object nodes only, root as level 1, limit 10 (conservative reading of the vendor limit)"
  - "Size limits and unsupported keywords make a schema non-strict; nothing is ever rewritten for eligibility"
requirements-completed: [PROV-12]
status: complete
plan_head_before: f980b988837f2bbf931f735879fe2a65dbdec698
commits: 3
actuals:
  tokens: 8300
  tasks: 3
  commits: 3
---

# Phase 5 Plan 1: Chat strict decision Summary

The shared optional-property detector now sees optionals hidden in anyOf and $defs, and Chat Completions gets an engine-wins strict decision plus a data-driven strip for the strict wire copy.

## Tasks

| Task | Commit | What |
|------|--------|------|
| 1 (tracer) | 6bf8715 | Extended `hasOptionalProperties`; `isChatStrictEligible` (root object, no optional, objects closed); `effectiveChatStrict` (strict can only opt out) |
| 2 | 9976025 | Unsupported-keyword set and size limits in `ChatStrict.kt`; one test per keyword and limit |
| 3 | 8c3ffe6 | `stripForChatStrict` in `ChatSchemaStrip.kt` with name/value preservation tests |

## Recorded values

**childSchemas order (shared detector):** `properties` values; `items` (object, or each object entry of an array); entries of `prefixItems`, `anyOf`, `oneOf`, `allOf`; `$defs` values; `definitions` values; `additionalProperties` when an object. Detector rule for the node itself is unchanged (a `properties` key not named in `required`).

**Unsupported keywords (15, checked 2026-10-01 against the vendor structured-outputs guide and SDK strict transform):** allOf, oneOf, not, if, then, else, dependentRequired, dependentSchemas, dependencies, prefixItems, patternProperties, unevaluatedProperties, unevaluatedItems, $dynamicRef, $dynamicAnchor. anyOf, $defs, definitions and $ref stay allowed.

**Size limits:** 5 000 object properties, 10 object nesting levels, 1 000 enum values, 120 000 chars of property names plus string enum and const values. Boundary tests: at the limit eligible, one over not.

**Strip drop list (same sources, 2026-10-01):** uniqueItems, minProperties, maxProperties, contains, minContains, maxContains, propertyNames, contentEncoding, contentMediaType; root-only `$schema` and `$id`; `format` unless one of date-time, time, date, duration, email, hostname, ipv4, ipv6, uuid. Length, numeric, item-count, pattern, default, enum, const and description hints are kept.

**Detekt-driven split:** none beyond line-length reflows. `ChatStrict.kt` has 8 functions (under the 11 threshold) with a private `SchemaTotals` class for the one-pass size tally.

## Verification

- `./gradlew :providers:check --offline` green: detekt zero, scanner, explicit API, bytecode level, compile floor, and the 4.12.0, 5.2.1 and 5.5.0 legs. ChatStrictTest 24 tests and OptionalPropertiesTest 15 tests pass on all three legs.
- AnthropicStrictTest, AnthropicEncoderTest and AnthropicForcedToolTest pass unchanged; no file under `anthropic/` changed (empty git log vs the plan base).
- No planning ids in source comments; no new dependency, `@Suppress`, `runCatching` or public declaration.

## Deviations

None. One note: the plan's tracer task described `ChatStrict.kt` in two stages; the intermediate version it committed in Task 1 was replaced by the full version in Task 2 as planned.

## Self-Check: PASSED

All five plan files exist; commits 6bf8715, 9976025, 8c3ffe6 are on the worktree branch.
