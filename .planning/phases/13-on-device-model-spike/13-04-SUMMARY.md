---
phase: 13-on-device-model-spike
plan: 04
subsystem: spike
tags: [on-device, litertlm, ai-provider, single-shot, route-a, route-b, constrained-json, native-tools, spike]

requires:
  - phase: 13-on-device-model-spike
    provides: ":spike-ondevice module that builds litertlm-android 0.17.1 (13-01) and the LiteRtProbe this plan replaces"
provides:
  - "SpikeOnDeviceProvider: ProviderId.ON_DEVICE AiProvider (Route A constrained JSON forced and model-chooses, Route B native tool calls) driven by the real SingleShotStrategy, router and on-device gate"
  - "LlmBackend seam plus the single LiteRT-LM adapter LiteRtBackend (the only file importing com.google.ai.edge), compiled and dexed into the debug APK"
  - "SpikeOnDeviceCapability (model file, arm64-v8a, last init) and SchemaMap (JsonObject to/from plain maps with schema-driven number coercion)"
  - "21 JVM tests: SpikeProviderTest (5) and RouteMappingTest (16) over FakeLlmBackend"
affects: [13-06, 13-07, 13-08, 13-09, 13-10, 13-11]

actuals:
  tokens: 15700
  tasks: 3
  commits: 4

plan_head_before: 59eec8037082b3d7ebac1c1550193708822485a2
commits: 4

tech-stack:
  added: []
  patterns:
    - "Backend seam: Engine and Conversation are final JNI classes, so everything above LlmBackend is JVM-tested with a scripted fake"
    - "Failures are codes only: BackendFailure and ProviderUnavailable carry a [a-z0-9_]+ code, runtime messages are classified then dropped"
    - "Request-shape dispatch on (route, ToolChoice): Route A forced, Route A auto wrapper, Route B"

key-files:
  created:
    - spike-ondevice/src/main/kotlin/io/github/ygaray/voiceactionengine/spike/backend/LlmBackend.kt
    - spike-ondevice/src/main/kotlin/io/github/ygaray/voiceactionengine/spike/backend/LiteRtBackend.kt
    - spike-ondevice/src/main/kotlin/io/github/ygaray/voiceactionengine/spike/provider/SpikeOnDeviceProvider.kt
    - spike-ondevice/src/main/kotlin/io/github/ygaray/voiceactionengine/spike/provider/RouteA.kt
    - spike-ondevice/src/main/kotlin/io/github/ygaray/voiceactionengine/spike/provider/RouteB.kt
    - spike-ondevice/src/main/kotlin/io/github/ygaray/voiceactionengine/spike/provider/SchemaMap.kt
    - spike-ondevice/src/main/kotlin/io/github/ygaray/voiceactionengine/spike/gate/SpikeOnDeviceCapability.kt
    - spike-ondevice/src/test/kotlin/io/github/ygaray/voiceactionengine/spike/SpikeProviderTest.kt
    - spike-ondevice/src/test/kotlin/io/github/ygaray/voiceactionengine/spike/RouteMappingTest.kt
    - spike-ondevice/src/test/kotlin/io/github/ygaray/voiceactionengine/spike/FakeLlmBackend.kt
  modified: []
  deleted:
    - spike-ondevice/src/main/kotlin/io/github/ygaray/voiceactionengine/spike/backend/LiteRtProbe.kt

key-decisions:
  - "The engine's enableConversationConstrainedDecoding flag follows the constraint ON/OFF arm, set per conversation (the engine reads it at conversation creation), so the ON arm cannot silently measure an unconstrained model (RESEARCH Pitfall 1)"
  - "Route B keeps ToolChoice.Required as an offered-tools request (the runtime cannot force a call), so the forced shape is informational on Route B and Route A is the forced-shape route"
  - "A native context-length failure is recognised by a keyword heuristic over the runtime message (classified, then dropped); the ladder (13-07) validates it against the real runtime"
  - "No :core change: the spike uses the v1.0 ON_DEVICE seams as they are (git diff -- core is empty)"

requirements-completed: []

coverage:
  - id: D1
    description: "A transcript runs through the real SingleShotStrategy, router and on-device gate into SpikeOnDeviceProvider (Route A forced) and reaches the gate as the expected tool call with one Constrained backend call"
    requirement: SPIKE-01
    verification:
      - kind: other
        ref: "SpikeProviderTest#aTranscriptRunsThroughSingleShotTheRouterAndTheGateAsTheExpectedToolCall"
        status: pass
    human_judgment: false
  - id: D2
    description: "An unavailable gate (model_missing) fails an ON_DEVICE-only tier loudly with zero backend calls, zero gate reads and no credential lookup (assumption-delta invariant, OnDeviceGateTest semantics)"
    requirement: SPIKE-01
    verification:
      - kind: other
        ref: "SpikeProviderTest#anUnavailableModelFailsTheOnDeviceOnlyTierLoudlyWithNoBackendCall"
        status: pass
    human_judgment: false
  - id: D3
    description: "Route A auto wrapper (tool enum plus none, arguments object), decline as text-only END_TURN, Route B native calls with number coercion, schema_type_mismatch, first-call-only, and the same system/user/maxOutputTokens on both routes"
    requirement: SPIKE-01
    verification:
      - kind: other
        ref: "RouteMappingTest (16 tests)"
        status: pass
    human_judgment: false
  - id: D4
    description: "Every failure is a typed ProviderUnavailable(ON_DEVICE, code) with a stable code and no answer, prompt or exception text; cancellation is rethrown"
    requirement: SPIKE-01
    verification:
      - kind: other
        ref: "SpikeProviderTest#aMalformedAnswerIsTheTypedFailureAndNoAnswerTextLeaksIntoIt + RouteMappingTest#everyBackendCodeSurfacesAsTheSameStableCause, anUnexpectedExceptionBecomesNativeErrorWithoutItsMessage, cancellationIsRethrownNeverCollapsedToAFailure"
        status: pass
    human_judgment: false
  - id: D5
    description: "LiteRtBackend compiles against the pinned litertlm 0.17.1 API and dexes into the debug APK with greedy sampling, explicit maxNumTokens, thinking off, automatic tool calling off and BenchmarkInfo after every call; its actual JNI behavior is unverified until the TESTER run"
    requirement: SPIKE-01
    verification:
      - kind: other
        ref: "./gradlew :spike-ondevice:assembleDebug (exit 0) and LiteRtBackend classes found in the APK dex"
        status: pass
    human_judgment: true
    rationale: "The JNI path (engine init per backend, ResponseFormat enforcement, OpenApiTool calls, BenchmarkInfo values, context-overflow wording) can only be judged on the device; plan 13-08 owns the first real run"

duration: 10min
completed: 2026-10-06
status: complete
---

# Phase 13 Plan 04: ON_DEVICE spike provider Summary

**A throwaway `ProviderId.ON_DEVICE` provider runs the real SingleShot, router and gate path over a backend seam with two measured routes (constrained JSON and native tool calls), and a single LiteRT-LM adapter that builds into the APK.**

## Performance

- **Duration:** about 10 min
- **Started:** 2026-10-06T00:28Z
- **Completed:** 2026-10-06T00:38Z
- **Tasks:** 3 (1 tracer, 1 TDD, 1 auto)
- **Files:** 11 (10 created, 1 deleted)

## Accomplishments

- **Tracer green end to end (D-04).** `commandPipeline` with `SingleShotStrategy`, `ModelRouter`, `onDevice = SpikeOnDeviceCapability` and `ProviderSelection(ON_DEVICE, "e2b")` calls `SpikeOnDeviceProvider` (Route A forced). The transcript reaches the `ScriptedGate` as one proposal for the create tool, with exactly one `Constrained` backend call using that tool's own schema and no credential lookup. The tracer gate (end-of-phase, automated-only verify) re-ran green before expansion.
- **Both routes and both shapes.** Route A forced uses the tool's `inputSchema`. Route A model-chooses uses the wrapper `{tool: enum[offered..., "none"], arguments: object}` (both required); `none` maps to a text-only `AssistantMessage` with `StopReason.END_TURN`, so the model can decline. Route B offers every tool natively, takes the first call only, and coerces gson-decoded doubles against the schema (`2.0` becomes `2` for an integer field; `2.5` or `"two"` is `schema_type_mismatch`). Route A and Route B requests differ only in mode.
- **Failures are codes, never text.** Backend codes `model_missing`, `abi_unsupported`, `init_failed`, `gpu_init_failed`, `insufficient_memory`, `native_error`, `context_overflow` surface verbatim as `ProviderUnavailable(ON_DEVICE, code)`; `malformed_output` and `schema_type_mismatch` come from the routes. A non-stable code or any unexpected exception becomes `native_error` with its message dropped; `CancellationException` is rethrown. A canary in the model's answer never appears in the failure or the outcome's `toString()`.
- **Assumption-delta invariant held (no-change).** With `Unavailable("model_missing")` an ON_DEVICE-only tier fails loudly with `ProviderUnavailable(ON_DEVICE, ...)`, zero backend calls, zero gate reads, empty credential requests, the same semantics as v1.0's `OnDeviceGateTest`.
- **LiteRtBackend is the only `com.google.ai.edge` importer** and replaces `LiteRtProbe`. It builds `EngineConfig` with explicit `maxNumTokens` and `cacheDir` (GPU or CPU backend), maps any init fault to `gpu_init_failed` / `init_failed` / `insufficient_memory` / `model_missing` without the message, and per call sets `SamplerConfig(topK = 1, topP = 1.0, temperature = 0.0, seed = 0)`, thinking off, `automaticToolCalling = false`, `enableResponseFormat` only in constrained mode, `OpenApiTool` descriptions for Route B (execute never reached), and reads `BenchmarkInfo` after every call. `assembleDebug` exits 0 and the adapter and provider classes are present in the APK dex.
- **No `:core` change** (`git diff --stat -- core/` is empty).

## Task Commits

1. **Task 1 (tracer): engine path on the JVM** - `9a0da8f` (feat)
2. **Task 2 (TDD) RED: route mapping tests** - `d7206c3` (test; 9 of 16 failing as intended)
3. **Task 2 (TDD) GREEN: route A auto, route B, code map** - `e4e23ce` (feat; 16/16 + 5/5)
4. **Task 3: LiteRtBackend, probe deleted** - `615a603` (feat)

## Decisions Made

See `key-decisions` above. Notable: `ExperimentalFlags.enableConversationConstrainedDecoding` is read by `Engine.createConversation` and defaults to false; the plan did not mention it, but leaving it off would risk the ON arm of the rf_matrix silently running unconstrained (Pitfall 1), so it follows the arm. Plan 13-07's feature-probe matrix is where its real effect gets measured; if it turns out to be unrelated to `ResponseFormat`, nothing else changes.

## Deviations from Plan

**1. [Rule 2 - Missing critical functionality] Constrained-decoding flag follows the ON/OFF arm**
- **Found during:** Task 3
- **Issue:** The plan sets `enableResponseFormat` and the per-call `ResponseFormat`, but the engine also reads a separate global `enableConversationConstrainedDecoding` flag (bytecode: `Engine.createConversation` passes it to native) defaulting to false. Without setting it, a green "constraint ON" row could be unenforced.
- **Fix:** `LiteRtBackend.run` sets it to `constraintOn` in `Constrained` mode before creating each conversation.
- **Files modified:** `backend/LiteRtBackend.kt`
- **Verification:** compiles and dexes; behavior verified only on the TESTER (plan 13-07/13-08).
- **Commit:** `615a603`

**2. [Plan-shape note, no behavior change] Tracer scope**
- The plan's Task 1 listed only `LlmBackend`, provider, `SchemaMap`, capability and tests; `RouteA.kt` was created in Task 1 with the forced path (so the provider compiled with the same structure Task 2 completes) instead of moving code later. Task 2 then extended it. End state matches the plan's file list.

**Total deviations:** 1 auto-fixed (Rule 2), 1 sequencing note. **Impact:** none on scope; one extra line of risk reduction in the adapter.

## Issues Encountered

None. Host memory was tight (about 7 GB available) but the single-Gradle low-memory recipe finished every invocation without an earlyoom kill. A foreign idle Gradle daemon was present from another session and was left untouched; `./gradlew --stop` was never run.

## Authentication Gates

None.

## Verification

- `:spike-ondevice:testDebugUnitTest --tests '*SpikeProviderTest*'` exit 0 (5 tests).
- `... --tests '*RouteMappingTest*' --tests '*SpikeProviderTest*'` exit 0 (16 + 5 tests); RED run before implementation failed 9 of 16.
- `:spike-ondevice:assembleDebug :spike-ondevice:testDebugUnitTest` exit 0 (full module: 102 tests, 0 failures); `LiteRtProbe.kt` absent.
- Acceptance greps: `git diff --stat -- core/` empty; `com.google.ai.edge` appears only in `backend/LiteRtBackend.kt`; `"none"` count in `RouteA.kt` is 1; `temperature = 0.0` count 2 and `maxNumTokens` count 1 in `LiteRtBackend.kt`; `RouteMappingTest` has 59 assertion lines.
- `scripts/verify-repo-hygiene.sh`: `HYGIENE OK`.
- No device, adb or TESTER access was used. The adapter's JNI behavior is unverified by design (13-08 owns the first real run).

## Next Phase Readiness

Plan 13-06 (runner) can construct `SpikeOnDeviceProvider(LiteRtBackend(), route, SpikeOnDeviceProvider.newDispatcher())` and `SpikeOnDeviceCapability` from the model file, ABI list and last `InitOutcome`. `BackendConfig.prefillPrefaceOnInit` is ready for the kv_reuse probe in 13-07. `BenchFacts` supplies prefill/decode tokens, TTFT and tokens per second for the evidence lines (13-03 grammar). Open for 13-07/13-08: whether the constrained-decoding flag affects `ResponseFormat` enforcement, and the real wording of a native context-overflow error (the current classifier is a keyword heuristic).

## Self-Check: PASSED

- Created files present: all 10 listed in `key-files.created` exist; `LiteRtProbe.kt` absent.
- Commits present: `9a0da8f`, `d7206c3`, `e4e23ce`, `615a603`.
