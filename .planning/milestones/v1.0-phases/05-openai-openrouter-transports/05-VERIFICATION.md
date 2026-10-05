---
phase: 05-openai-openrouter-transports
verified: 2026-10-01T05:00:00Z
status: passed
score: 5/5 must-haves verified
covered_files:
  - ".planning/REQUIREMENTS.md"
  - ".planning/phases/05-openai-openrouter-transports/05-01-PLAN.md"
  - ".planning/phases/05-openai-openrouter-transports/05-01-SUMMARY.md"
  - ".planning/phases/05-openai-openrouter-transports/05-02-PLAN.md"
  - ".planning/phases/05-openai-openrouter-transports/05-02-SUMMARY.md"
  - ".planning/phases/05-openai-openrouter-transports/05-03-PLAN.md"
  - ".planning/phases/05-openai-openrouter-transports/05-03-SUMMARY.md"
  - ".planning/phases/05-openai-openrouter-transports/05-04-PLAN.md"
  - ".planning/phases/05-openai-openrouter-transports/05-04-SUMMARY.md"
  - ".planning/phases/05-openai-openrouter-transports/05-05-PLAN.md"
  - ".planning/phases/05-openai-openrouter-transports/05-05-SUMMARY.md"
  - ".planning/phases/05-openai-openrouter-transports/05-06-PLAN.md"
  - ".planning/phases/05-openai-openrouter-transports/05-06-SUMMARY.md"
  - ".planning/phases/05-openai-openrouter-transports/05-07-PLAN.md"
  - ".planning/phases/05-openai-openrouter-transports/05-07-SUMMARY.md"
  - ".planning/phases/05-openai-openrouter-transports/05-08-PLAN.md"
  - ".planning/phases/05-openai-openrouter-transports/05-08-SUMMARY.md"
  - ".planning/phases/05-openai-openrouter-transports/05-09-PLAN.md"
  - ".planning/phases/05-openai-openrouter-transports/05-09-SUMMARY.md"
  - ".planning/phases/05-openai-openrouter-transports/05-10-PLAN.md"
  - ".planning/phases/05-openai-openrouter-transports/05-10-SUMMARY.md"
  - ".planning/phases/05-openai-openrouter-transports/05-11-PLAN.md"
  - ".planning/phases/05-openai-openrouter-transports/05-11-SUMMARY.md"
  - ".planning/phases/05-openai-openrouter-transports/05-12-PLAN.md"
  - ".planning/phases/05-openai-openrouter-transports/05-12-SUMMARY.md"
  - "providers/build.gradle.kts"
  - "providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ChatCompletionsAttemptObserver.kt"
  - "providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ChatCompletionsProvider.kt"
  - "providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ChatDecoder.kt"
  - "providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ChatEncoder.kt"
  - "providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ChatErrors.kt"
  - "providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ChatMessageEncoder.kt"
  - "providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ChatModels.kt"
  - "providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ChatResponseParts.kt"
  - "providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ChatSchemaStrip.kt"
  - "providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ChatStrict.kt"
  - "providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ChatTransport.kt"
  - "providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ChatVendor.kt"
  - "providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/chat/OpenAiModelRules.kt"
  - "providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/http/SafeNotify.kt"
  - "providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/schema/OptionalProperties.kt"
covered_digest: "v1:sha256:230a9eccadda14bc765dbaecb1f21102243a5ed056b6de8a1b208c82b551e87c"
behavior_unverified: 0
overrides_applied: 0
re_verification: false
deferred:
  - truth: "A real recorded OpenRouter EDIT body with omitted optionals (live absent-optional proof per vendor)"
    addressed_in: "Phase 10"
    evidence: "VER-03 (REQUIREMENTS.md): 'each provider's smoke includes an EDIT-shaped call proving an omitted optional arrives absent (PROV-12)'; D-14 'live on-device EDIT in Phase 10 (VER-03)'"
  - truth: "Live low-credit mapping (OpenAI insufficient_quota 429, OpenRouter 402) and accepted key character set"
    addressed_in: "Phase 10"
    evidence: "05-CONTEXT.md Runtime Decisions 'Phase 10 Gate-2 carry (from Phase 4)'; Phase 10 SC3 live smokes on all three clouds"
---

# Phase 5: OpenAI & OpenRouter Transports Verification Report

**Phase Goal:** A consumer can point the same commands at OpenAI or OpenRouter through one Chat Completions transport. It speaks each provider's real wire shape and maps each provider's quirks to the engine's typed outcomes.
**Verified:** 2026-10-01
**Status:** passed
**Re-verification:** No, initial verification

Starting hypothesis was "tasks done, goal missed". I read the shipped `chat/` sources, the golden request/response fixtures and the manifest, and re-ran the provider test legs. SUMMARY claims were used only as pointers. No live API calls were made.

## Goal Achievement

### Observable Truths (ROADMAP success criteria)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | Golden request bodies show the correct wire shape: nested `{"type":"function","function":{...}}` tools with strict-mode keywords stripped, `max_completion_tokens` on reasoning models, `reasoning_effort:"none"` where tools require it, `provider.require_parameters:true` on OpenRouter when a tool is forced | VERIFIED | `ChatEncoder.kt` builds the nested shape (`encodeTools`), strips only on the strict copy (`stripForChatStrict`, data-driven lists), picks the token param and effort from `OpenAiModelRules.wireRules`, and adds `provider.require_parameters` only when `vendor.requireParametersOnForced && named != null`. Committed goldens `requests/openai.json` and `requests/openrouter.json` decode to: gpt-5.4-mini forced = `reasoning_effort:none`, `max_completion_tokens`, `strict:true` on log_food only; gpt-4o-mini = `max_tokens`, no effort; openrouter forced = `provider:{require_parameters:true}`; routed Anthropic = reshaped to `auto`, no strict. `ChatEncoderTest.forcedOpenAiCallEncodesToTheGoldenBodyByteForByte` and siblings pass. |
| 2 | Golden tests from recorded, sanitized real bodies show arguments JSON-string decoding (bad JSON gives `MalformedToolArgs`), `finish_reason` / `message.refusal` mapping and OpenRouter HTTP-200 error bodies become typed outcomes | VERIFIED (scoped per D-13) | Decoder precedence is implemented in `ChatDecoder.kt` (rows 1-10) and `ChatResponseParts.kt` (`parseArguments`: "" gives `{}`, non-object or invalid JSON gives `MalformedToolArgs`); `chatEnvelopeError` maps a top-level `error` on any 2xx through the status table. `MANIFEST.tsv` holds 8 captured rows (OpenAI: invalid_key, forced_log_food, forced_log_food_repeat, forced_edit, auto_prose; OpenRouter: invalid_key, forced_log_food, haiku_route), each with a real file in `captured/`, replayed by `ChatGoldenReplayTest`. The malformed-JSON, refusal, content_filter, truncated-args and 200-error cases are derived minimal bodies (29 derived rows). See "Judgment: real vs derived" below. |
| 3 | Chat Completions tests run green on all three legs of the A1 OkHttp matrix, usage normalized to `{inputUncached, cacheRead, cacheWrite, output}` | VERIFIED | I re-ran `:providers:test`, `:providers:testOkhttp521`, `:providers:testOkhttp550` with `--rerun`: 411 tests, 0 failures, 0 errors, 0 skipped on each leg (19 `providers.chat` classes). `decodeChatUsage` computes `prompt - cached - cacheWrite`, clamped; real OpenAI capture C2 decodes to in:255 / cache_read:1152. `TokenParityTest` proves identical buckets and CORE-04 ceiling counting across Anthropic, OpenAI and OpenRouter (the TEL-01 parity owner assigned to this phase). |
| 4 | PROV-09/11/12/13 hold for the Chat transport: transport-only retry with no re-executed tools or re-commit, no logging interceptors + 4.12 body API, strict only when no optional properties, timeout >= 60 s | VERIFIED | Retry: `ChatTransport.sendWithRetry` is capped by `MAX_REQUESTS = 2` and lives below the provider seam; `ChatPipelineRetryTest` (5 scenarios across both vendors: 503, dropped connection, transient 200 envelope) asserts `server.requestCount == 2`, `mutation.applyCount == 1` and `sink.actions.size == 1`. Cancellation: `ChatCancellationTest` (4 cases, both vendors) asserts the in-flight call is cancelled and nothing is committed. Client: `cleanClient` (Phase 4) strips interceptors, no logging interceptor or `body.string()` anywhere in `src/main` (grep clean); `CallAwait.kt` uses `body?.string()`. Strict: `effectiveChatStrict = tool.strict != false && isChatStrictEligible(schema)` and `isChatStrictEligible` requires `!hasOptionalProperties` (the shared Phase 4 detector); `ChatStrictTest.anAppsStrictTrueCannotForceStrictOntoASchemaWithAnOptional` passes. Timeout: `DEFAULT_TIMEOUT_MILLIS = 60_000L` for call and read, both configurable and validated positive. |
| 5 | The absent-optional contract test passes for both OpenAI and OpenRouter: an omitted optional arrives absent, never `""`, `[]` or a filled default | VERIFIED | `ChatAbsentOptionalTest` is `@Parameterized` over both vendors and drives a forced EDIT call through the real pipeline, provider, MockWebServer and a recording mutation. It asserts the tool went out with no `strict` key and the app's schema byte-identical, that the arguments the app's mutation received equal exactly what the model sent, and that each manifest `absent_keys` path does not resolve. OpenAI runs against the real captured C3 body; OpenRouter runs against the derived body (see judgment below). Passes on all three matrix legs. |

**Score:** 5/5 truths verified (0 present-but-behavior-unverified)

### Judgment: "real recorded bodies" half of SC2 / D-14 on OpenRouter

The D-16 live capture (11 requests, approved under the standing test-key policy) produced 8 real goldens. Three intents were not met: R2 (OpenRouter EDIT omitted-optionals), R3 (OpenRouter cache read) and R5 (parallel_tool_calls probe, 404). These are model/router behavior findings recorded in `05-12-SUMMARY.md` and `evidence/live-chat-capture.txt`, not defects in the engine.

My ruling: this is a **Phase 10 / Gate-2 carry, not a blocker**.

- SC2 names the typed outcomes (arguments decode, finish_reason/refusal mapping, HTTP-200 errors). Most of those (bad JSON, refusal, content_filter, 200 envelope) cannot be provoked on demand from a live service, which is exactly the case D-13 covers ("real envelopes for reproducible cases + documented minimal derivatives for the rest"). Real envelopes now back the reproducible ones for both vendors (forced tool call, string-encoded arguments, prose end_turn, 401 auth, Haiku route).
- The one real gap is a real OpenRouter body with omitted optionals. The property under test (the decoder and pipeline never fill defaults) is vendor-independent decoder behavior, already proven on a real OpenAI body, and the OpenRouter request side is proven by the golden request (no strict on the optional schema). D-14 itself assigns the live per-vendor EDIT proof to Phase 10 via VER-03, whose text explicitly requires an EDIT-shaped call per provider. The derived `openrouter_edit_absent_optional` row stays and says so in its note.
- R3 and R5 are not must-haves. R5 produced a finding the code already honors (`ChatVendor.OPENROUTER.parallelToolCallsFalseOnForced = false`); R3 is a caching-effectiveness observation (OpenRouter prompt caching is not claimed in v1.0, routed models are `CachingMode.NONE`/AUTOMATIC-by-provider only).

The live-found error-map defect (OpenRouter 404 "No endpoints found that can handle the requested parameters" decoding as `model_not_found`) was fixed after capture (commit dafe2e5): `NO_ENDPOINTS_MARKER = "No endpoints found that"` in `ChatErrors.kt`, with `ChatErrorMapTest` asserting both live wordings give `model_unsupported` and "No endpoints found for foo/bar" stays `model_not_found`.

### Deferred Items

| # | Item | Addressed In | Evidence |
|---|------|-------------|----------|
| 1 | Real OpenRouter EDIT body with omitted optionals; live absent-optional per vendor | Phase 10 | VER-03 requires an EDIT-shaped call per provider; D-14 |
| 2 | Low-credit mapping (OpenAI insufficient_quota 429, OpenRouter 402), accepted key character set | Phase 10 | 05-CONTEXT.md Gate-2 carry; Phase 10 SC3 |

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `providers/.../chat/ChatCompletionsProvider.kt` | Public `openAi{}` / `openRouter{}` factories, 60 s defaults | VERIFIED | 119 lines, builder validates timeouts and https, wired to `ChatTransport` |
| `.../chat/ChatEncoder.kt`, `ChatMessageEncoder.kt` | Nested tools, stable prefix, require_parameters, effort, token param | VERIFIED | Substantive; used by `ChatTransport.request`; byte-exact golden test |
| `.../chat/ChatDecoder.kt`, `ChatResponseParts.kt` | Ordered decode, args decode, usage | VERIFIED | Used by `ChatTransport.interpret` |
| `.../chat/ChatErrors.kt` | Status-first error table, 200 envelopes, no body retention | VERIFIED | Wired from both the non-2xx path and `decodeAnswer` |
| `.../chat/ChatStrict.kt`, `ChatSchemaStrip.kt` | Eligibility + strip copy | VERIFIED | Reuses `schema/OptionalProperties.kt` (no copy of the detector) |
| `.../chat/ChatModels.kt`, `OpenAiModelRules.kt` | Family rules, routed id normalization, Astra / 6.1 Sol tools-unsupported | VERIFIED | `capabilities("gpt-6-astra").supportsTools == false` on OpenAI, true via OpenRouter (asserted in `ChatTransportTest`) |
| `.../chat/ChatVendor.kt`, `ChatTransport.kt`, `ChatCompletionsAttemptObserver.kt` | Vendor config, one-retry transport, observer | VERIFIED | Wired |
| `providers/src/test/resources/golden/chat/**` | Request goldens, MANIFEST.tsv, derived.json, 8 captured bodies | VERIFIED | Manifest captured rows (8) equal captured files (8); ids sanitized to `chatcmpl-GOLDEN` / `gen-GOLDEN`; no key-shaped strings |
| Opt-in `liveChatCompletionsCapture` task | Outside `check`, key-gated | VERIFIED | Absent from the `check` task graph; loopback self-test only (the `LIVE_CAPTURE` lines in the test log are from `ChatCaptureRunTest`, not live) |

### Key Link Verification

| From | To | Via | Status |
|------|----|-----|--------|
| `ChatCompletionsProvider.complete` | `ChatTransport.send` | direct call | WIRED |
| `ChatTransport.request` | `encodeChatRequest` | request body | WIRED |
| `ChatTransport.interpret` | `decodeChatResponse` / `parseChatError` | success vs error path | WIRED |
| `encodeChatRequest` | `effectiveChatStrict` -> `hasOptionalProperties` | strict decision | WIRED |
| `ChatModels.capabilities` | pipeline capability table (`supportsTools`, forced choice) | `AiProvider.capabilities` | WIRED |
| Transport retry | pipeline `CommitSink` | one logical call, 2 requests max | WIRED, behavior proven by `ChatPipelineRetryTest` |

### Data-Flow Trace (Level 4)

| Artifact | Data | Source | Real data | Status |
|----------|------|--------|-----------|--------|
| Decoder tool arguments | `ToolCall.arguments` | the model's `function.arguments` string / object, parsed, never schema-filled | yes (real captured C1/C3 replay) | FLOWING |
| Usage buckets | `Usage` | response `usage` + `prompt_tokens_details` | yes (real C2: cached 1152) | FLOWING |
| Request id | `FailureDetails.requestId` / `ModelResponse.requestId` | header (OpenAI) or body `id` (OpenRouter), validated | yes | FLOWING |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| Full gate | `./gradlew check --offline` | BUILD SUCCESSFUL (exit 0) | PASS |
| Chat tests on all 3 OkHttp legs | `./gradlew :providers:test --rerun :providers:testOkhttp521 --rerun :providers:testOkhttp550 --rerun --offline` | 411 / 0 failures / 0 errors / 0 skipped on each leg | PASS |
| Retry never double-commits | `ChatPipelineRetryTest` (in the legs above) | applyCount 1, commits 1, requestCount 2 | PASS |
| Cancel cancels the HTTP call | `ChatCancellationTest` (in the legs above) | pass | PASS |

No state-changing or network-calling check was run.

### Probe Execution

The phase declares no `probe-*.sh`. The phase-gate scripts (`review-api-surface.sh`, `verify-negative-controls.sh`, `verify-api-dump.sh`, `verify-repo-hygiene.sh`) were recorded green in `evidence/phase-gate.txt` at plan 05-11, and `./gradlew check` (which includes the hygiene and banned-construct scans) is green on HEAD. Not re-executed individually.

### Requirements Coverage

Requirement IDs declared across the 12 PLAN frontmatters: PROV-08, PROV-09, PROV-11, PROV-12, PROV-13, BLD-06, TEL-01, TEL-04. Phase brief IDs: PROV-08, PROV-12.

| Requirement | Source Plans | Status | Evidence |
|-------------|-------------|--------|----------|
| PROV-08 (ChatCompletionsProvider for OpenAI + OpenRouter, nested tools, strict strip, reasoning_effort none, max_completion_tokens, arguments decode, finish/refusal, 200 error bodies, require_parameters, Astra / 6.1 Sol tools-unsupported, public capability table) | 05-01..05-12 | SATISFIED | SC1, SC2, SC3 evidence above; `capabilities()` is on the public `AiProvider` and `ModelCapabilities` is public in `:core`; unsupported models are refused typed before any network call |
| PROV-12 (strict only with no optional properties; absent-optional contract per provider) | 05-01, 05-04, 05-10 and others | SATISFIED | SC4 and SC5 |
| PROV-09, PROV-11, PROV-13 | 05-08, 05-06 and others | SATISFIED for the Chat transport (Anthropic leg owned by Phase 4, already Complete) | SC4 |
| BLD-06 | 05-08, 05-10, 05-11 | SATISFIED for the Chat transport | SC3 (three legs) |
| TEL-04 | 05-03, 05-10 | SATISFIED for the Chat transport | `ChatCanaryTest` sweep on both vendors |
| TEL-01 | 05-09 | SATISFIED (cross-provider parity test owner) | `TokenParityTest` |

No orphaned requirements: REQUIREMENTS.md maps only PROV-08 and PROV-12 to Phase 5, and both are claimed by plans.

**Bookkeeping note (not a gap):** `REQUIREMENTS.md` still shows PROV-08 and PROV-12 unchecked with traceability "Pending" (lines 191, 195 and the checklist entries), and `ROADMAP.md` line 29 still shows Phase 5 unchecked. The orchestrator should flip these when it records phase completion.

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| (providers/src, scripts) | - | TBD / FIXME / XXX / TODO / HACK | none found | grep clean |
| repo | - | `api.txt` or baseline XML | none | correct (api dumps are cut at v1.0.0) |
| `ChatResponseParts.kt` `decodeChatUsage` | 606-612 | Unverified assumption: `prompt_tokens` includes `cache_write_tokens` | Info | Documented in KDoc and `TokenParityTest`; never observed non-zero live; Phase 10 should pin a real cache-write response |
| `ChatErrors.kt` `RESPONSES_ENDPOINT_MARKER` | 406 | Wording of OpenAI's Responses-only 400 not seen live | Info | Widened defensively; status-guarded; unit-tested; Phase 10 should capture a real 400 |
| `ChatCompletionsAttemptObserver.kt` | 63-76 | Public `@JvmInline value class` for attempt kind (review IN-03, accepted skip) | Info | Mirrors the Anthropic type; confirm before the api.txt cut at Phase 10/11 |

### Human Verification Required

None blocking. Every behavior-dependent truth (retry without re-commit, cancellation, no-default-fill) has a passing behavioral test. The remaining live-only confirmations (OpenRouter real EDIT body, OpenAI Responses-only 400 text, cache-write accounting via a router, OpenRouter cache hit, low-credit mapping) are carried by Phase 10's live smokes and listed under Deferred Items and Anti-Patterns above; none is a must-have of this phase.

### Gaps Summary

No gaps. The phase goal is achieved in the codebase: one Chat Completions transport serves both OpenAI and OpenRouter, encodes the real nested wire shape (confirmed accepted live by 11 captured requests), and maps the vendors' quirks to typed outcomes with behavioral tests on all three OkHttp legs. The post-capture review (4 warnings, 6 info) was resolved with fixes committed (9 fixed, IN-03 a documented skip), including the live-found 404 mapping defect. The open items are evidence-quality carries to Phase 10, not missing implementation.

---

_Verified: 2026-10-01_
_Verifier: Claude (gsd-verifier)_
