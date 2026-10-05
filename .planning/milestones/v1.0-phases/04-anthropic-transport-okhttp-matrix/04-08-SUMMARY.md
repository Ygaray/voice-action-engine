---
phase: 04-anthropic-transport-okhttp-matrix
plan: 08
subsystem: providers
tags: [canary, redaction, okhttp-matrix, mockwebserver, api-shape, phase-gate, tracer]
requires: ["04-06", "04-07"]
provides:
  - "AnthropicCanaryTest: 8 tests (1 tracer happy path + 7 failure/edge legs) sweeping everything the engine returns, delivers or prints, on all three OkHttp legs"
  - "OkHttpVersionGuardTest.mockWebServerRuntimeMatchesLeg: prints MOCKWEBSERVER_RUNTIME=<jar> expected=<version> and asserts the jar is mockwebserver-<leg version>.jar"
  - "ProvidersApiShapeTest: public-shape rules (enum, copy/componentN, public static field, default-argument stub) over every :providers main class, each with a positive control"
affects: [phase-05, phase-10]
tech-stack:
  added: []
  patterns: ["a recording AiProvider delegate (AiProvider by inner) wraps the real provider so the sweep sees every ProviderRequest and ModelResult", "positive controls (server saw the key and every canary) precede the sweep, and a distinct-string floor follows it"]
key-files:
  created:
    - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/anthropic/AnthropicCanaryTest.kt
    - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/ProvidersApiShapeTest.kt
  modified:
    - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/OkHttpVersionGuardTest.kt
    - providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/anthropic/AnthropicEncoder.kt
    - providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/anthropic/AnthropicMessageEncoder.kt
    - providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/anthropic/AnthropicStrict.kt
key-decisions:
  - "The canary sweep found no secret leak in the engine or transport; the only finding was the public-shape one below"
  - "Three internal top-level const vals (TYPE, TEXT in AnthropicMessageEncoder.kt, MAX_STRICT_TOOLS in AnthropicStrict.kt) compile to public static fields on their file facades; fixed at the source by making them private to the encoder files that use them (matching the private ROLE_USER duplication already there) instead of weakening the rule"
requirements-completed: [TEL-04, BLD-06]
status: complete
commits: 3
plan_head_before: 9bbab17fbe21781a57b689dd6ce268e213468fbd
actuals:
  tokens: 16000
  tasks: 3
  commits: 3
metrics:
  completed: 2026-10-01
---

# Phase 4 Plan 08: Canary, matrix guard and phase gate Summary

A canary command routed through `commandPipeline` and the real `AnthropicProvider` proves no key, transcript, system text, tool argument or tool_result reaches anything the engine returns, delivers or prints (success and seven failure shapes), the matrix guard now proves each leg really ran its own okhttp and mockwebserver jars, and the full phase gate is green.

## What was built

- `AnthropicCanaryTest` (Tasks 1 and 2). A recording `AiProvider by inner` delegate wraps the real provider, which is pointed at a `MockWebServer` through the internal `baseUrl` and a no-op `sleep`. Pipeline: `tier(strategy)`, fixed selection `anthropic/claude-haiku-4-5`, scripted credential `sk-CANARY-KEY`, `RecordingEventListener`, `ScriptedGate.admitAll()`, `RecordingCommitSink`, and an attempt observer. Core test fixtures only (D-10); no OkHttp object is ever stringified.
  - Tracer (happy path): two turns. Turn 1 sends a canary system text, the canary transcript and a `lookup` tool; the server answers a canary text block plus a tool_use with a canary argument. Turn 2 replays the conversation with a canary tool_result; the server answers a canary final text. Positive controls assert the `x-api-key` header equals the key (read with `getHeader`), request 1 holds the transcript and system canaries, request 2 holds the tool-argument and tool_result canaries. Then the sweep covers the outcome, trace, attempts, turns, usage, pipeline events, sink actions and closes, every recorded `ProviderRequest` (request, cache, tool choice, tools, messages, credential, capabilities), every recorded `ModelResult` (response, message, parts, native replay), the real provider, the Builder and every `AnthropicAttempt`.
  - Failure legs, each asserting the exact outcome reason and `FailureDetails` as well as the absence of the canary and key: 400/401/429/500 echo bodies (details `(status, invalid_request_error, req_canary_h)`, plus `AnthropicErrorInfo.toString`); hostile `error.type` and request ids with spaces (details `(400, null, null)`); a non-JSON 200 containing the canary (`MalformedResponse`); a string `tool_use.input` (`MalformedToolArgs`); `DISCONNECT_AFTER_REQUEST` on every attempt (`Network`, attempts `initial` then `transient_retry`); the reshape path on `claude-opus-5-5` with `ToolChoice.Required` (body has `tool_choice` type `auto` and the instruction "Call the lookup tool with your result."); and `callTimeoutMillis = -1` with an `httpClient` on the Builder (IAE message has neither canary nor key).
- `OkHttpVersionGuardTest`: new `mockWebServerRuntimeMatchesLeg`, loading `okhttp3.mockwebserver.MockWebServer` by name and asserting its code-source file name is `mockwebserver-<expected>.jar`. The existing test and its `OKHTTP_RUNTIME=` line are unchanged, so `scripts/verify-negative-controls.sh` Part 3 still matches. No change to `verifyOkHttpCompileFloor`, the leg configurations or the `check` wiring (D-01).
- `ProvidersApiShapeTest`: sweeps every class from the code source of `AnthropicProvider` under the providers root package; asserts at least 10 classes and the presence of `AnthropicProvider`, `AnthropicAttemptObserver`, `AnthropicAttempt` and `AnthropicAttemptKind`; fails on an enum, a `copy` or `componentN` method, a public static field other than `INSTANCE` or `Companion`, or a default-argument constructor stub on a JVM-public class. A synthetic enum, data class, `@JvmField` companion class and default-argument class prove each rule fires, and shipped types prove they stay quiet.

## Evidence

- Canary distinct strings swept (`CANARY_SWEPT distinct=N`, floor 25): happy path 47; echo legs 79 (78 on the 5.5.0 leg, same cases); hostile fields 29; non-JSON 200 28; non-object tool input 28; network loss 30; reshape 33. All eight tests pass on 4.12.0, 5.2.1 and 5.5.0.
- Leak found by the canary: none.
- Runtime lines (guard, one per leg):
  - `OKHTTP_RUNTIME=4.12.0 expected=4.12.0`, `MOCKWEBSERVER_RUNTIME=mockwebserver-4.12.0.jar expected=4.12.0`
  - `OKHTTP_RUNTIME=5.2.1 expected=5.2.1`, `MOCKWEBSERVER_RUNTIME=mockwebserver-5.2.1.jar expected=5.2.1`
  - `OKHTTP_RUNTIME=5.5.0 expected=5.5.0`, `MOCKWEBSERVER_RUNTIME=mockwebserver-5.5.0.jar expected=5.5.0`
- Phase gate, run on the committed tree in this worktree:
  - `./gradlew check --offline`: BUILD SUCCESSFUL (detekt zero issues, all modules, all three legs, sample lint).
  - `./gradlew check --dry-run --offline`: lists `:providers:verifyOkHttpCompileFloor`, `:providers:testOkhttp521` and `:providers:testOkhttp550`; no new floor task.
  - `scripts/review-api-surface.sh --expect-sealed-complete`: `API SURFACE OK sealed=AssistantPart,CommandOutcome,GateDecision,Message,RunTermination,StrategyOutcome,ToolStep classes=161`.
  - `scripts/verify-negative-controls.sh`: `negative-control failures: 0` (Part 3 guard controls red on all three legs with `expected=9.9.9`).
  - `scripts/verify-api-dump.sh`: `API DUMP PROOF OK (real tree untouched; copy removed on exit)`.
  - `scripts/verify-repo-hygiene.sh` (run last): `HYGIENE OK`; `git status --short` clean afterwards (no signature dump left behind).

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Internal top-level const vals compiled as public static fields**
- **Found during:** Task 3 (`ProvidersApiShapeTest.noMainClassLeaksAPublicStaticFieldBesidesInstanceAndCompanion`)
- **Issue:** `internal const val TYPE`/`TEXT` (AnthropicMessageEncoder.kt) and `MAX_STRICT_TOOLS` (AnthropicStrict.kt) are Kotlin-internal but become `public static final` fields on `AnthropicMessageEncoderKt` and `AnthropicStrictKt`, which the shape rule (and :core's identical rule) treats as frozen-API leaks.
- **Fix:** made them `private const val` in the files that use them (`TYPE`/`TEXT` stay private in AnthropicMessageEncoder.kt and are duplicated privately in AnthropicEncoder.kt; `MAX_STRICT_TOOLS` moved to AnthropicEncoder.kt, its only user). Values and wire bytes unchanged; the rule was not weakened. A `val` instead of `const val` was tried first and rejected by detekt (MayBeConst, MagicNumber).
- **Files:** AnthropicEncoder.kt, AnthropicMessageEncoder.kt, AnthropicStrict.kt
- **Commit:** a0a8ce9

The tracer feedback gate (auto re-run of the tracer's verify) passed before expansion.

## Self-Check: PASSED

- `AnthropicCanaryTest.kt`, `ProvidersApiShapeTest.kt`, `OkHttpVersionGuardTest.kt` exist; commits 72bf69b, ac09f7f and a0a8ce9 are on the branch.
- No files deleted. No tags created. STATE.md and ROADMAP.md untouched.
