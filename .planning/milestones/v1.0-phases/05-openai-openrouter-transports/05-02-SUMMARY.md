---
phase: 05-openai-openrouter-transports
plan: 02
subsystem: providers
tags: [openai, openrouter, chat-completions, model-capabilities, wire-rules]
requires: [05-01]
provides:
  - "ChatVendor.OPENAI / ChatVendor.OPENROUTER (internal vendor config as data)"
  - "OpenAiModelRules: toolsOnChat, wireRules, minCacheablePrefixTokens, routedDefaultRules"
  - "ChatModels: key, capabilities, wireRules, routesToOpenAi (OpenRouter id normalization)"
affects: [05-03 reactive matcher, 05-04 encoder, 05-05 decoder, 05-06 provider factories]
tech-stack:
  added: []
  patterns: ["vendor differences as data on one config class", "family rules by anchored regex, never exact-id sets for OpenAI"]
key-files:
  created:
    - providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ChatVendor.kt
    - providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/chat/OpenAiModelRules.kt
    - providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ChatModels.kt
    - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ChatModelsTest.kt
    - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/chat/OpenAiModelRulesTest.kt
  modified: []
key-decisions:
  - "Router minimum of 16 output tokens applies only to the legacy max_tokens pair (viaRouter and gpt-4*/gpt-3.5*, or any non-OpenAI routed model); max_completion_tokens rows keep minimum 1"
  - "ChatModelKey family names are private string constants, not an enum (ProvidersApiShapeTest sweeps internal classes)"
requirements-completed: [PROV-08]
status: complete
plan_head_before: f526b920de55e58d3629dbc07a9186cd23402cf6
commits: 3
actuals:
  tokens: 7500
  tasks: 3
  commits: 3
---

# Phase 5 Plan 2: Chat model knowledge Summary

The Chat Completions dialect now knows its models: GPT-6 Astra and 6.1 Sol are refused with tools on OpenAI before any call, OpenRouter ids normalize to the facts of the model behind them, and wire-parameter rules resolve by family inside `:providers` only.

## Tasks

| Task | Commit | What |
|------|--------|------|
| 1 (tracer) | 0362107 | `ChatVendor` with two instances, `toolsOnChat` and cache minimum, OpenAI `ChatModels.capabilities`; pipeline proof of `model_unsupported` + `capability_refused` with zero provider calls |
| 2 | ba07271 | `ChatWireRules` and `wireRules(id, viaRouter)`, one assertion per rules-table row |
| 3 | 4d4acf0 | OpenRouter `key`, `capabilities`, `wireRules`, `routesToOpenAi`; routed Anthropic never caches |

## Recorded values

**Vendor flags.** OPENAI: providerId openai, base `https://api.openai.com/v1/`, requestIdHeader `x-request-id`, requestIdInBody false, routedModelIds false, requireParametersOnForced false, parallelToolCallsFalseOnForced true. OPENROUTER: providerId openrouter, base `https://openrouter.ai/api/v1/`, requestIdHeader null, requestIdInBody true, routedModelIds true, requireParametersOnForced true, parallelToolCallsFalseOnForced false.

**Wire-rule regexes, in match order** (all anchored, checked 2026-10-01):
1. `^gpt-6(?:-astra|\.1-sol)(?:-.*)?$` (RESPONSES_ONLY): via router only, effort `low`, `max_completion_tokens`, min 1. On OpenAI it also drives `toolsOnChat` false.
2. `^gpt-6(?:[.-].*)?$` or `^gpt-5\.(\d+)(?:[-.].*)?$` with N >= 4: effort `none`, `max_completion_tokens`, min 1.
3. `^gpt-5(?:-.*)?$`, `gpt-5.N` with N < 4, `^o\d.*$`: no effort, `max_completion_tokens`, min 1.
4. `^gpt-4(?:[-.o].*)?$`, `^gpt-3\.5(?:-.*)?$`: no effort, `max_tokens`, min 16 via router else 1.
5. Anything else: no effort, `max_completion_tokens`, min 1.
Cache minimum: 1 024 for `^gpt-.*$` or `^o\d.*$`, else null.

**Normalization (OpenRouter).** Drop everything from the first `:`; split at the first `/`; lowercase the prefix; `openai` -> OpenAI rules with viaRouter true; `anthropic` -> `AnthropicModels.capabilities(id with '.' -> '-').supportsForcedToolChoice`, caching NONE, no minimum; anything else or no slash -> `ModelCapabilities.UNKNOWN` and the router default wire rules (no effort, `max_tokens`, min 16). The wire id and the override key stay the exact id the app passed.

## Verification

- `./gradlew :providers:check --offline` green: detekt zero, scanner, explicit API, bytecode level, compile floor, and the 4.12.0, 5.2.1 and 5.5.0 legs. ChatModelsTest 15 tests, OpenAiModelRulesTest 9 tests; AnthropicModelsTest unchanged and green.
- `git log f526b92..HEAD -- core/` is empty; no `max_completion_tokens` or `reasoning_effort` under `core/src`.
- No planning ids in chat sources; no new dependency, `@Suppress`, enum, data class or public declaration.

## Deviations

None. One small addition beyond the Artifacts list: `OpenAiModelRules.routedDefaultRules()` so the router default shares the token-name constants with the rest of the table instead of duplicating them in `ChatModels`.

## Self-Check: PASSED

All five plan files exist; commits 0362107, ba07271, 4d4acf0 are on the worktree branch.
