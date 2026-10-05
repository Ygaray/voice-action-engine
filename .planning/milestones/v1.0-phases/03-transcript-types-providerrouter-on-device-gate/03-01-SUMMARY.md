---
phase: 03-transcript-types-providerrouter-on-device-gate
plan: 01
subsystem: core
status: complete
tags: [kotlin, transcript, neutral-model, native-replay, sealed, redaction]

requires:
  - phase: 02-core-contract-pipeline-commit-seam
    provides: "ToolSpec, telemetry Usage, ProviderId, isStableCode, review-api-surface.sh gate"
provides:
  - "io.github.ygaray.voiceactionengine.core.transcript: sealed Message (UserMessage, AssistantMessage, ToolResultsMessage), ToolResult, sealed AssistantPart (Text, ToolCall), NativeReplay, ModelRequest, ToolChoice (Auto, Required), CacheDirective, ModelResponse, StopReason"
  - "AssistantMessage.nativeFor(provider, model): same raw instance only on an exact stamp, else null"
  - "review-api-surface.sh allow-list widened to exactly seven sealed types"
affects: [03-05, 03-06, Phase 4, Phase 5, Phase 7, Phase 8, Phase 9]

plan_head_before: 76c818f6fe5d5c5c74367fa8c0d7317523ff864f

actuals:
  tokens: 8000   # chars/4 over the added lines of the realized diff (32,113 chars)
  tasks: 3
  commits: 3

tech-stack:
  added: []
  patterns:
    - "No default argument values on any transcript constructor; convenience shapes are explicit secondary constructors"
    - "Hand-written redacted toString (ids, names, counts, lengths only); require messages name the rule, never a value"
    - "Open vocabularies (ToolChoice, StopReason) stay non-sealed; only Message and AssistantPart are contract-closed"

key-files:
  created:
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/transcript/Message.kt
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/transcript/AssistantPart.kt
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/transcript/NativeReplay.kt
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/transcript/ModelRequest.kt
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/transcript/ModelResponse.kt
    - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/TranscriptTypesTest.kt
    - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/NativeReplayTest.kt
  modified:
    - scripts/review-api-surface.sh

key-decisions:
  - "Sealed set: Message and AssistantPart sealed (contract-closed universes the two provider mappers switch over exhaustively); ToolChoice, StopReason and every later vocabulary stay open"
  - "UserMessage is text only; other content kinds would arrive as additive constructors"
  - "ModelRequest.maxTokens has no default and the request carries no model id (bound per command by the router)"
  - "Estimator divisor refinement recorded for 03-02/03-09: default 4.0 chars per token, per-model overridable, capped by the response's own prompt tokens (3.01 is the measured tool-JSON ratio, and erring silent needs the larger divisor)"

requirements-completed: [PROV-01]

coverage:
  - id: D1
    description: "Single-shot request and response are built from public constructors and read back field by field"
    requirement: "PROV-01"
    verification:
      - kind: unit
        ref: "core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/TranscriptTypesTest.kt#singleShotRequestReadsBackFieldByField"
        status: pass
    human_judgment: false
  - id: D2
    description: "Multi-turn tool conversation (parallel calls, NativeReplay, one result batch with an error) is expressible; lists are copied; invalid shapes rejected"
    requirement: "PROV-01"
    verification:
      - kind: unit
        ref: "TranscriptTypesTest (12 tests, 0 failures)"
        status: pass
    human_judgment: false
  - id: D3
    description: "Verbatim replay: exact provider-and-model stamp returns the same raw instance, everything else null; raw never printed"
    requirement: "PROV-01"
    verification:
      - kind: unit
        ref: "core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/NativeReplayTest.kt (5 tests, 0 failures)"
        status: pass
    human_judgment: false
  - id: D4
    description: "No toString prints user or assistant text, system prompt, tool arguments, result content or raw replay (canary sweep over nine types)"
    verification:
      - kind: unit
        ref: "TranscriptTypesTest#toStringNeverPrintsContent; NativeReplayTest#printingNeverRevealsTheRawTurn"
        status: pass
    human_judgment: false
  - id: D5
    description: "Isolated-copy Metalava review accepts exactly seven sealed types including Message and AssistantPart; full build green"
    verification:
      - kind: other
        ref: "scripts/review-api-surface.sh --expect-sealed-complete; ./gradlew check (exit 0)"
        status: pass
    human_judgment: false
---

# Phase 3 Plan 01: Transcript Conversation Model Summary

**Neutral, redaction-safe, growth-safe conversation model in `:core`: sealed `Message` and `AssistantPart`, verbatim `NativeReplay` with exact provider-and-model stamp semantics, and `ModelRequest` / `ModelResponse` with an open `StopReason`, with the surface gate widened to exactly seven sealed types.**

## Performance

- **Duration:** about 4 min of wall clock (start 2026-10-01T02:59:53Z, end 2026-10-01T03:03:08Z)
- **Tasks:** 3 (one tracer, two TDD)
- **Files:** 8 (5 main, 2 test, 1 script)

## Accomplishments

- A single-shot request (one `UserMessage`, one `ToolSpec`, `ToolChoice.Required`) and a multi-turn tool conversation (assistant text plus two parallel `ToolCall`s plus a `NativeReplay`, one `ToolResultsMessage` carrying a success and an error, a final assistant text) are both built from public constructors and read back field by field.
- `ModelResponse.usage` is the existing telemetry `Usage`; `ModelRequest.tools` is the existing `ToolSpec`; `StopReason` reuses `isStableCode`. No second usage type, no new dependency, `:core` still has no HTTP stack.
- Validation: empty messages, `maxTokens` below 1, duplicate tool names, a `Required` choice naming an absent tool, empty result batch, duplicate call ids, blank ids and names, and non-snake-case stop reasons all throw `IllegalArgumentException`; messages name the rule only.
- All lists are defensive copies (`parts`, `results`, `messages`, `tools`).
- No transcript constructor declares a default argument value; convenience shapes are explicit secondary constructors.
- `AssistantMessage.nativeFor(provider, model)` returns the very same `raw` instance (assertSame) only for an exact stamp.

## Verification evidence

- `scripts/review-api-surface.sh --expect-sealed-complete` printed:
  `API SURFACE OK sealed=AssistantPart,CommandOutcome,GateDecision,Message,RunTermination,StrategyOutcome,ToolStep classes=134`
- `./gradlew check` (whole build): BUILD SUCCESSFUL.
- `TranscriptTypesTest` 12 tests, `NativeReplayTest` 5 tests, `ApiShapeTest` 6 tests: 0 failures.
- No `api.txt` in `core/`, `providers/` or `keystore/`; no planning id in transcript sources.

## Task Commits

1. **Task 1 (tracer): types, allow-list, single-shot test** - `476d865`
2. **Task 2: multi-turn results, validation, copies, redaction** - `1a9f770`
3. **Task 3: verbatim native replay lookup** - `8f6e2d0`

## Decisions Made

Planning decisions carried from the plan (resolved in-plan, two-way until the v1.0.0 cut):

1. Sealed set: only `Message` and `AssistantPart`. Every other new vocabulary (`ToolChoice`, `StopReason`, `ModelResult`, `CredentialLookup`, `OnDeviceAvailability`, `CachingMode`) stays open. The three message kinds and two neutral part kinds are the universe by contract, and the two provider mappers switch over them exhaustively.
2. On-device cause code: `ProviderUnavailable(ON_DEVICE, "on_device_unavailable")` on both the pre-check and router paths.
3. D-10 known limitation: prefix drift with read 0 and write above 0 on turn 2 or later is not flagged (implemented as locked in 03-09).
4. Estimator divisor: default 4.0 characters per token, overridable per model, capped by the response's own prompt tokens. This refines CONTEXT's "about 3" hint.
5. `ToolSpec` grows by `strict: Boolean?` only, via `@JvmOverloads` (03-05).
6. `UserMessage` is text only.
7. New constructors in `transcript/` and `provider/` declare no default values (03-05 adds the mechanical ApiShapeTest rule).

## Deviations from Plan

None - plan executed exactly as written. Two cosmetic fixes during Task 2, both in code written in this plan: a `toString` line exceeded detekt's default `MaxLineLength` and was wrapped (the only detekt default rule that forced a code change); a test used an explicit type argument on `assertNotEquals` that JUnit's overloads reject, replaced with `assertFalse(a == b)`.

**Total deviations:** 0 plan deviations (2 in-task cosmetic fixes).

## Issues Encountered

None.

## Next Phase Readiness

Ready for 03-02. The transcript types are the input to the provider contract in 03-05, which adds `ToolSpec.strict` and an ApiShapeTest rule banning default argument values in `transcript/` and `provider/`.

## Self-Check: PASSED

All seven created files exist; commits 476d865, 1a9f770 and 8f6e2d0 exist; `./gradlew check` green; API SURFACE OK with seven sealed names.
