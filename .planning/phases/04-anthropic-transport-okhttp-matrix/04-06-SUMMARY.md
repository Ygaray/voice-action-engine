---
phase: 04-anthropic-transport-okhttp-matrix
plan: 06
subsystem: providers
tags: [anthropic, forced-tool-choice, strict, reshape, no-tool-call, tracer, tdd]
requires: ["04-05"]
provides:
  - "internal encodeAnthropicRequest(call, reshape = false): auto tool choice, strict on eligible tools, closing instruction"
  - "internal decodeAnthropicResponse(body, requestIdHeader, model, toolRequired = false): NoToolCall"
  - "internal isAnthropicStrictEligible(schema) and MAX_STRICT_TOOLS = 20 (AnthropicStrict.kt)"
  - "internal hasOptionalProperties(schema) in providers.schema (OptionalProperties.kt), reusable by Phase 5"
  - "AnthropicTransport: table reshape, one reactive reshape on the forced-tool 400, shared three-request budget"
affects: [04-07, 04-08, phase-05, phase-07]
tech-stack:
  added: []
  patterns: ["the transport decides the reshape from ProviderRequest.capabilities only (no model-id set outside AnthropicModels)", "resend planning is one pure function (planResend) over the attempt and a small Progress value", "schema-aware (not key-blind) walks so a property named like a keyword is not mistaken for one"]
key-files:
  created:
    - providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/anthropic/AnthropicStrict.kt
    - providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/schema/OptionalProperties.kt
    - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/anthropic/AnthropicForcedToolTest.kt
    - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/anthropic/AnthropicStrictTest.kt
  modified:
    - providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/anthropic/AnthropicEncoder.kt
    - providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/anthropic/AnthropicTransport.kt
    - providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/anthropic/AnthropicDecoder.kt
key-decisions:
  - "The reactive reshape fires only when the request was forced, the status is 400 and AnthropicErrorInfo.mentionsToolChoice is true; after a reshape the request is no longer forced, so a second reshape is impossible by construction"
  - "NoToolCall is returned from the decoder (a 2xx outcome), so the transport's transient logic never sees it and never retries it"
  - "The strict walk descends only into properties values and items; combinator and reference keywords are rejected where they appear, so there is nothing below them to inspect"
  - "Explicit strict = true counts against the 20-tool limit before the engine adds any; an app that marks more than 20 is sent as asked"
requirements-completed: [PROV-07]
status: complete
commits: 5
plan_head_before: df64fa3923e53aaa56da49965755e7f2d5c4c8cc
actuals:
  tokens: 13000
  tasks: 3
  commits: 5
metrics:
  completed: 2026-10-01
---

# Phase 4 Plan 06: Forced tool choice, reshape, strict and NoToolCall Summary

A required tool now works on every Anthropic model: forced where the model accepts it, auto + strict + a closing instruction where the verified table (or the app) says it does not, one reactive reshape on the specific forced-tool 400 for ids the table does not know, and a typed `NoToolCall` when the model still answers in words. All green on OkHttp 4.12.0, 5.2.1 and 5.5.0 (`./gradlew check --offline`, which includes detekt at zero issues).

## What was built

- Reshape encoding (`AnthropicEncoder.kt`): `reshape = true` sends `tool_choice {"type":"auto"}`, adds `strict: true` to eligible tools whose `ToolSpec.strict` is null (sorted order, until 20 tools in the request are strict, explicit `true` counted first), and appends one text block to the end of the last user-role message, after any tool results. The `system` element and the tool definitions are otherwise byte-identical to the non-reshaped request (asserted in the tracer). `reshape = false` is unchanged (AnthropicEncoderTest passes untouched).
- Final instruction text: `Call the <tool> tool with your result.` (for the tracer, `Call the add_item tool with your result.`).
- Strict subset implemented (`isAnthropicStrictEligible`): root `type` is `object`; no optional property anywhere (shared walker); every object node (type `object`, or any node with `properties`) has `additionalProperties` exactly `false`; none of `$ref`, `$defs`, `definitions`, `anyOf`, `oneOf`, `allOf`, `minimum`, `maximum`, `exclusiveMinimum`, `exclusiveMaximum`, `multipleOf`, `minLength`, `maxLength` on any schema node; `minItems` absent, 0 or 1. `ToolSpec.clarification`'s schema is eligible.
- `hasOptionalProperties` (`providers.schema`, internal): walks the root, every value under `properties` and every `items`, true when an object's properties are not all in its `required`.
- Transport: the initial flag is `ToolChoice.Required && !capabilities.supportsForcedToolChoice` (app overrides already applied by the router), so the four rejecting models never waste a 400 and an app can flip any id. The reactive reshape needs all three of: forced request, status 400, `mentionsToolChoice`. The reshape and the transient retry are tracked separately (`Progress.reshape`, `Progress.retried`) under one `MAX_REQUESTS = 3` ceiling; the reshape does not wait and is reported as `forced_tool_reshape`. Nothing is remembered between calls; the error message is never stored.
- Decoder: with `toolRequired`, a 200 with no tool call and `stop_reason` `end_turn` is `Failure(NoToolCall)` with null details. Refusal and `max_tokens` stay `Success` with `REFUSAL` / `MAX_TOKENS`; an Auto request answered in text is a success.

## Budget combinations observed (all three legs: 4.12.0, 5.2.1, 5.5.0)

| Scenario (unknown id, Required) | Requests | Attempt kinds | Waits | Result |
|---|---|---|---|---|
| forced 400, 200 | 2 | initial, forced_tool_reshape | none | Success |
| forced 400, 529, 200 | 3 | initial, forced_tool_reshape, transient_retry | [500] | Success |
| 529, forced 400, 200 | 3 | initial, transient_retry, forced_tool_reshape | [500] | Success |
| forced 400, forced 400 | 2 | initial, forced_tool_reshape | none | http_error |
| 529, forced 400, 529 | 3 | (budget exhausted) | [500] | overloaded, never a fourth request |
| two consecutive calls, each forced 400 then 200 | 4 | no memo | none | both Success |
| forced 400 not mentioning tool_choice | 1 | initial | none | http_error |
| Auto request, 400 mentioning tool_choice | 1 | initial | none | http_error |
| claude-opus-5-5 (table-reshaped), 400 mentioning tool_choice | 1 | initial | none | http_error |
| forced 401 whose text mentions tool_choice | 1 | initial | none | auth |

## Tests

- `AnthropicForcedToolTest` (18): the tracer through `commandPipeline` (opus-5-5 table reshape with system byte-equality and a single `cache_control`; app override `supportsForcedToolChoice = true` gives `{"type":"tool","name":...}` with no instruction and no strict key; haiku natively forced), the ten reactive/budget rows, and the five NoToolCall / stop-reason rows plus a reshape-then-text case (2 requests, `no_tool_call`).
- `AnthropicStrictTest` (12): the predicate matrix (clarification and flat all-required eligible; optional root and nested property, missing and open `additionalProperties`, all 13 keywords at root and nested, `minItems` 2, non-object root ineligible), `hasOptionalProperties` inside array items, reshape strict cases (eligible marked, optional left alone, explicit false never, explicit true sent also when not reshaped, 21 eligible tools give exactly 20), and the omitted-optional leg (input_schema byte-identical to the app's, decoded arguments contain exactly `title`).

## TDD gate compliance

Tasks 2 and 3 ran RED then GREEN as separate commits: `0150890` (test, 6 of 13 failing as expected) then `352e9f5` (feat); `121b4e0` (test, exactly the 3 NoToolCall rows failing; the strict matrix already passed because the predicate shipped in the tracer) then `e5d1cc8` (feat). Task 1 is the tracer (`d764f86`), committed as a production slice and re-verified end to end before expansion.

## Deviations from Plan

None. One sequencing note: the reactive transport and the decoder `toolRequired` flag were each held back from the tracer commit so every commit contains only its own task's behavior; the decoder call site in the transport was wired in the final feat commit.

## Commits

- `d764f86` feat: reshape a required tool for models that reject forced tool choice (tracer)
- `0150890` test: reactive forced-tool 400, shared budget, no memo (RED)
- `352e9f5` feat: reshape once on the forced-tool 400 within a shared three-request budget (GREEN)
- `121b4e0` test: NoToolCall, strict matrix, omitted optionals (RED)
- `e5d1cc8` feat: a required tool the model did not call is a typed NoToolCall (GREEN)

No file was deleted. No `:core` change, no new `FailureReason`, no new dependency, no `api.txt`, no `@Suppress`, no tag.

## Self-Check: PASSED

- Created files exist: AnthropicStrict.kt, OptionalProperties.kt, AnthropicForcedToolTest.kt, AnthropicStrictTest.kt (verified present, compiled and run).
- Commits `d764f86`, `0150890`, `352e9f5`, `121b4e0`, `e5d1cc8` present on the worktree branch (`git rev-list --count df64fa3..HEAD` was 5 before this summary).
- `./gradlew check --offline` green in the worktree.
