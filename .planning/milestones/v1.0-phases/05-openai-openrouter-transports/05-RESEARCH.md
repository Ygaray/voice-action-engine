# Phase 5: OpenAI & OpenRouter Transports - Research

**Researched:** 2026-10-01
**Domain:** Kotlin/JVM OkHttp transport for OpenAI and OpenRouter Chat Completions (nested tool shape, strict-mode eligibility and stripping, reasoning/max-token parameter rules, 200-with-error envelopes, usage normalization, typed failures) inside the existing `:providers` module and A1 OkHttp matrix
**Confidence:** HIGH for repo state and every OpenAI doc/SDK fact (fetched from `developers.openai.com` `.md` pages and the `openai-node` source this session). MEDIUM for OpenRouter behavior (docs plus issue reports, no live call). Four behaviors can only be settled by the key-gated capture and are flagged in the Assumptions Log. No live API call was made.

<user_constraints>
## User Constraints (from 05-CONTEXT.md)

### Locked Decisions
(Copied verbatim from `## Implementation Decisions` and `## Runtime Decisions` of 05-CONTEXT.md. The Runtime Decisions override the provisional text above them.)

- **D-01 [vendor]:** Two public factories over an internal config (base URL, ProviderId, require_parameters on forced calls, request-id source: OpenAI x-request-id header vs OpenRouter body gen- id); Bearer auth for both
- **D-02 [or-normalize]:** Normalize for lookup (CT's OpenRouterModelCatalog rule) so openai/gpt-5.4-mini inherits reasoning flags and anthropic/claude-sonnet-5.5 inherits forced-unsupported; caching mode still keyed by ProviderId (OpenRouter anthropic/* uncached in v1.0) _(provisional — refresh at execution; depends on Phase 3)_
- **D-03 [reasoning-flags]:** Capability flags (requiresReasoningNoneWithTools, maxTokensParamName) resolved by family rules for unknown ids, never sent to models that reject them (e.g. gpt-4o-mini) _(provisional — refresh at execution; depends on Phase 3)_
- **D-04 [model-unsupported]:** Add a ModelUnsupported leaf to the open FailureReason taxonomy (Phase 2); scope the block to the OpenAI provider unless research shows OpenRouter also can't route these tools _(provisional — refresh at execution; depends on Phase 2)_
- **D-05 [optional-detect]:** Recursive detection at every depth; required+nullable stays strict-eligible (keeps CT's log_food strict, sends SB's edit tools non-strict — edit_list_card's nested item_id/completed_at would otherwise be wiped)
- **D-06 [strict-authority]:** Engine computes effective strict; ToolSpec.strict may only opt out; any object missing additionalProperties:false → non-strict (no silent rewrite)
- **D-07 [strip]:** Strip only on strict calls from the wire copy; non-strict calls keep minLength/pattern/format/default as hints; the strip list is data refreshed from current OpenAI docs
- **D-08 [shared-detector]:** One shared pure function in :providers built in Phase 4; decoders and the local validator never fill schema defaults, so an omitted optional stays absent _(provisional — refresh at execution; depends on Phase 4)_
- **D-09 [tool-turn]:** Presence of tool_calls decides (OpenAI has returned finish_reason "stop" for forced named functions); don't port CT's gate; add a trace note when finish_reason disagrees
- **D-10 [decode-order]:** The ordered precedence above; arguments "" decodes to {}; other decode failures → MalformedToolArguments  (precedence per the decision map: 200-error envelope → refusal → content_filter → length (MaxTokens wins over truncated args) → stop-without-call NoToolCall → unknown UnknownStopReason)
- **D-11 [http200-errors]:** Both vendors, every 200: top-level error object → mapped via the same status → FailureReason table; finish_reason "error" → failure with native_finish_reason as a typed hint; never retain error.message or metadata.raw (TEL-04)
- **D-12 [goldens]:** A key-gated capture task (outside ./gradlew check) with gpt-5.4-mini / openai/gpt-5.4-mini and synthetic log_food-shaped + EDIT-with-optionals tools (never SB's fixture); sanitized (ids, system_fingerprint, all headers stripped) and committed; needs Yahir's OpenAI + OpenRouter keys at Phase 5 time. Capture tasks are GATED on Yahir's key approval (pending, relayed by the orchestrator).
- **D-13 [golden-derivatives]:** Real envelopes for reproducible cases + documented minimal derivatives for the rest
- **D-14 [absent-proof]:** Per-vendor JVM test on the golden request + a recorded real response; live on-device EDIT in Phase 10 (VER-03)
- **D-15 [ext-openai-or]:** Needs external research: finish_reason under forced named tool on gpt-5.4-mini (and routed upstreams); current OpenAI strict keyword subset; OpenRouter reasoning_effort vs reasoning{effort}, max_completion_tokens acceptance, require_parameters hard-filtering; the exact 200-error envelope; GPT-6 Astra/6.1 Sol tools via OpenRouter; OpenRouter cached-token usage fields by default; strict on non-OpenAI upstreams
- **D-16 [r1-verdict]:** Golden-capture tasks are KEY-GATED until the orchestrator relays Yahir's OK; structure plans so everything else in the phase executes without them.

**Runtime Decisions (refresh, override the provisional text above):**
- **TEL-01 parity test owner:** Phase 5 owns the cross-provider token-parity JVM test. Ship a JVM test proving that the same work normalizes to identical `{inputUncached, cacheRead, cacheWrite, output}` and counts identically against the CORE-04 token ceiling (SB sum semantics) on every provider.
- **or-normalize:** CONFIRMED vs Phase 3. Lookup goes through the existing public `ModelCapabilityTable.lookup(provider, model)` and the internal ModelRouter. Phase 5 adds OpenRouter id normalization (strip the vendor prefix) as a lookup-key step, so `openai/gpt-5.4-mini` inherits the OpenAI flags and `anthropic/claude-sonnet-5.5` inherits forced-tool-choice-unsupported (the existing `supportsForcedToolChoice=false`). `CachingMode` stays keyed by ProviderId, so OpenRouter anthropic/* is uncached in v1.0.
- **reasoning-flags:** Put `requiresReasoningNoneWithTools` and `maxTokensParamName` in an internal OpenAI model-rules table inside `:providers` (mirroring `anthropic/AnthropicModels.kt`), resolved by family rules for unknown ids. Do NOT add them to public `:core` ModelCapabilities. Never send them to models that reject them (e.g. gpt-4o-mini).
- **model-unsupported:** ALREADY SATISFIED by Phase 2: `FailureReason.ModelUnsupported` already exists; add no new leaf. Phase 5 only maps to it. Scope the block to the OpenAI provider unless research shows OpenRouter also cannot route these tools.
- **shared-detector:** ALREADY SATISFIED by Phase 4: `internal fun hasOptionalProperties(schema: JsonObject)` in providers/schema/OptionalProperties.kt. Reuse, no copy. Decoders and the local validator never fill schema defaults.
- **PROV-12 (from Phase 4):** left Pending by Phase 4 and mapped to Phase 5, so cover it here.
- **Phase 10 Gate-2 carry (from Phase 4):** the live smoke must confirm that a low-credit HTTP 400 maps to Billing and that the accepted API-key character set holds. Apply the same checks to OpenAI/OpenRouter where applicable.

### Claude's Discretion
"Anything not listed above follows `.planning/research/SUMMARY.md` and the phase's own research; Source `ai-auto` decisions took research's recommendation (orchestrator accepted the auto-resolved remainder)."

### Deferred Ideas (OUT OF SCOPE)
"See REQUIREMENTS.md v2 / LATER items." (Responses API dialect = LATER-03; OpenAI `prompt_cache_key`; OpenRouter `anthropic/*` `cache_control`; OpenRouter `session_id`; model catalog; streaming; Phase 8 transcript conformance/goldens; SingleShot (Phase 7); AgenticLoop; `:keystore`; `:sample` live smoke.)
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| PROV-08 | `ChatCompletionsProvider` serves OpenAI and OpenRouter over Chat Completions: nested tool shape, strict-mode keyword stripping, `reasoning_effort:"none"` with tools where required, `max_completion_tokens` for reasoning models, `arguments` JSON-string decode, `finish_reason`/refusal mapping, OpenRouter 200-error bodies, `provider.require_parameters:true` when forcing; GPT-6 Astra / GPT-6.1 Sol tools-unsupported with a typed reason before any network call; model capability table public | Wire shape, rules table, decode order, error envelope, capability normalization (Architecture Patterns 1-6, Code Examples) |
| PROV-12 | `strict:true` only when the schema has no optional properties; otherwise non-strict with local validation; per-provider contract test proves an omitted optional arrives absent | Detector gap found (no `anyOf`/`oneOf`/`$defs` walk), eligibility + strip design, per-vendor contract test (Pattern 3, Pitfalls 1-2, Validation map) |
| TEL-01 (Runtime Decision) | Cross-provider parity: same work normalizes to identical `Usage` and counts identically against the CORE-04 ceiling | Usage mapping for three wire formats, pipeline-level parity test design (Pattern 5, Validation map) |
| PROV-09 / PROV-11 / PROV-13 / BLD-06 / TEL-04 (hold for Chat too, success criteria 3-4) | transport-only retry, clean client + `body?.string()`, timeouts, matrix legs, canary | Reuse Phase 4 `http/*` unchanged; new tests in `providers/src/test` run on all three legs automatically (Standard Stack, Validation map) |
| SHOT-02 (prerequisite only; Phase 7 owns it) | `parallel_tool_calls: false` on Chat Completions | Encoder rule + OpenRouter `require_parameters` hazard (Pattern 2, Pitfall 7, Open Question 3) |
| VER-03 (Phase 10, input only) | live smoke per cloud | Capture design leaves reusable request builders and the key-gating convention |
</phase_requirements>

## Summary

Phase 5 adds one new package, `chat/`, to `:providers`, plus three small edits to shared code. It reuses Phase 4's `http/` helpers unchanged (`cleanClient`, `Call.await()` returning an `HttpReply`, `OneShotJsonBody`, `transientWaitMillis`, `safeToken`/`safeRequestId`) and the pure schema walker in `schema/OptionalProperties.kt`. No new package is added, no `:core` change is required: OpenRouter id normalization lives in `ChatCompletionsProvider.capabilities(model)` (the provider-default hook that `commandPipeline` feeds to `ModelCapabilityTable`), and the wire-only flags stay in an internal `:providers` table. Baseline verified this session: `:providers:test`, `testOkhttp521`, `testOkhttp550`, `detekt`, `scanBannedConstructs`, `verifyBytecodeLevel`, `verifyExplicitApiStrict`, `verifyOkHttpCompileFloor` are all green with 144 tests per leg.

Four findings change the plan relative to the decision map. (1) **The Phase 4 detector is not recursive enough for OpenAI.** `hasOptionalProperties` walks the root, `properties` values and `items` only. OpenAI strict mode accepts `anyOf` and `$defs`, so a schema whose optional properties sit inside an `anyOf` branch or a definition would be judged "no optionals", sent `strict:true`, and either be rejected with a 400 or have its optional filled in. It must be extended before the Chat strict path uses it (additive: it only returns `true` in more cases; Anthropic already rejects `anyOf`/`$defs` as unsupported keywords). (2) **OpenAI's strict keyword subset is wider than CT assumed.** The current docs (fetched today) support `pattern`, `format` (nine named formats), `minimum`, `maximum`, `exclusiveMinimum`, `exclusiveMaximum`, `multipleOf`, `minItems`, `maxItems`; unsupported in strict are `allOf`, `not`, `dependentRequired`, `dependentSchemas`, `if`, `then`, `else` (docs) plus `oneOf`, `prefixItems`, `uniqueItems`, `contains`, `minProperties`, `maxProperties`, `patternProperties`, `propertyNames`, `unevaluated*` (the official SDK's transform, 2026-09-16). CT's removal of numeric bounds follows an older doc revision. (3) **GPT-6 Astra and GPT-6.1 Sol are Responses-only for tools on OpenAI, but OpenRouter advertises `tools`/`tool_choice` for both**, so the typed block is scoped to the OpenAI provider (D-04 holds). (4) **`parallel_tool_calls:false` plus `provider.require_parameters:true` is a hazard on OpenRouter**: `require_parameters` hard-filters endpoints on every request parameter, and OpenRouter's model pages do not list `parallel_tool_calls` for the models checked. Default it off on OpenRouter until the capture proves it safe.

**Primary recommendation:** Build `chat/` as a mirror of `anthropic/` (public `ChatCompletionsProvider` with `openAi {}` / `openRouter {}` factories over an internal `ChatVendor`, pure encoder, pure decoder with the D-10 precedence, status-first error map plus the 200-envelope path, a transient-retry-only transport), decide tool turns by `tool_calls` presence, send `strict:true` only for schemas that pass an extended detector and an `additionalProperties:false` check, and make every golden-dependent test pass on documented derivatives so the key-gated capture plan is last and optional.

## Architectural Responsibility Map

This is a JVM library, so the "tiers" are library layers.

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Wire-only parameter rules (`reasoning_effort`, `max_completion_tokens` vs `max_tokens`) | `:providers` internal table | — | Runtime Decision: wire details must not become frozen public `ModelCapabilities` fields |
| "Can this model take tools / forced tools" + caching facts | `:providers` provider default via `AiProvider.capabilities(model)` | `:core` `ModelCapabilityTable` (app overrides by exact id) | Public table lets model pickers filter tool-incapable models (PROV-08) |
| OpenRouter id normalization | `ChatCompletionsProvider.capabilities` | — | `commandPipeline` calls `byId[provider]?.capabilities(model)`; `:core` stays model-id-free |
| Tool-incapable refusal before any network call | `:core` `RoutedModel` (existing) | — | Existing behavior: `supportsTools=false` + tools present → `ModelUnsupported` + `capability_refused` |
| Strict eligibility, schema strip | `:providers` `chat/ChatStrict.kt` | `schema/OptionalProperties.kt` shared detector | One detector for all dialects |
| Request encode / response decode | `:providers` `chat/` (pure functions) | — | Pure, golden-testable without HTTP |
| HTTP, retry, timeouts, cancellation | `:providers` `http/` (Phase 4, unchanged) | `chat/ChatTransport.kt` loop | Roadmap: Phase 4 owns shared plumbing |
| Retry visibility / finish_reason disagreement note | `:providers` public attempt observer | `:core` trace (not reachable from a provider) | Same constraint Phase 4 hit; observer carries ids only |
| Token-ceiling parity | `:core` `Usage.total` + `CommandSession.tokensUsed` | `:providers` normalization | The normalization is the provider's job; the ceiling is already in core |

## Standard Stack

### Core (all already in the repo; Phase 5 adds NO external package)
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| `com.squareup.okhttp3:okhttp` | 4.12.0 compile floor (`api`) | HTTP | A1; `providers/build.gradle.kts` declares `api(libs.okhttp)` [VERIFIED: providers/build.gradle.kts read this session] |
| `com.squareup.okhttp3:mockwebserver` (legacy package) | follows the leg: 4.12.0 / 5.2.1 / 5.5.0 | JVM transport tests | `testImplementation(libs.okhttp.mockwebserver)` with the comment "Legacy okhttp3.mockwebserver package only" [VERIFIED: providers/build.gradle.kts] |
| `org.jetbrains.kotlinx:kotlinx-serialization-json` | 1.11.0 (transitive `api` from `:core`) | `JsonObject` tree for bodies, tool schemas, tool args | Phase 4 pattern |
| `org.jetbrains.kotlinx:kotlinx-coroutines-core` / `-test` | 1.11.0 | `withContext`, `runBlocking`, `runTest` | already declared |
| `junit:junit` | 4.13.2 | tests (including `Parameterized` for per-vendor contract tests) | already declared |

### Package Legitimacy Audit
No external package is added (every dependency is already in `gradle/libs.versions.toml` and resolved in the Gradle caches). The `package-legitimacy` seam was therefore not run. The OpenAI/Anthropic Java SDKs, Ktor, Jackson, and `logging-interceptor` stay forbidden (CLAUDE.md "What NOT to Use"); I read `openai-node` source only as a documentation oracle, it is not a dependency.

| Package | Registry | Age | Downloads | Source Repo | Verdict | Disposition |
|---------|----------|-----|-----------|-------------|---------|-------------|
| (none added) | — | — | — | — | — | n/a |

**Packages removed due to [SLOP] verdict:** none
**Packages flagged as suspicious [SUS]:** none

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Copy `AnthropicTransport` into a generic transport | Mirror its structure in `ChatTransport` (≤2 requests, no reshape-on-400) and leave `AnthropicTransport` untouched | Refactoring a green, reviewed Phase 4 class risks a regression for no Phase 5 gain; the only shared bit worth extracting is `isHeaderSafe` (Pattern 6) |
| Top-level `reasoning_effort` on OpenRouter | `reasoning:{effort}` | Both accepted by OpenRouter (Pattern 2); one code path wins; switch only if the capture shows a reject |
| Capture through the production transport with a body tap | Capture test builds the request with the internal encoder and POSTs with a plain `OkHttpClient` | No production seam, no change to `ChatTransport`; the raw body is available to write the sanitized golden (Capture design) |

**Installation:** none. **Version verification:** `./gradlew :providers:dependencies --offline` is unchanged from Phase 4; the OkHttp legs print `OKHTTP_RUNTIME=4.12.0 / 5.2.1 / 5.5.0`.

## Current Repo State (read this session)

**`:providers` main** (`providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/`):
- `http/CallAwait.kt`: `internal class HttpReply(val code: Int, val headers: Headers, val body: String?)` with `isSuccessful`; `internal suspend fun Call.await(): HttpReply` (body read and response closed inside the OkHttp callback; cancellation cancels the call). Reuse as is.
- `http/CleanClient.kt`: `internal fun cleanClient(app: OkHttpClient?, callTimeoutMillis: Long, readTimeoutMillis: Long): OkHttpClient` (interceptors, network interceptors, event listener, authenticator, proxy authenticator, cookie jar stripped; redirects off; connect timeout 10 s).
- `http/OneShotBody.kt`: `internal class OneShotJsonBody(private val bytes: ByteArray) : RequestBody()` with `isOneShot() = true`.
- `http/RetryPolicy.kt`: `isTransientStatus(code)` over `{408, 429, 500, 502, 503, 504, 529}`, `retryAfterSeconds(header)`, `transientWaitMillis(retryAfterSeconds, capMillis, backoffMillis)`.
- `http/SafeFields.kt`: `safeToken(value)` (≤64) and `safeRequestId(value)` (≤128) over `[A-Za-z0-9_.:-]+`.
- `schema/OptionalProperties.kt`: `internal fun hasOptionalProperties(schema: JsonObject): Boolean`. Verbatim body: `val properties = schema[PROPERTIES] as? JsonObject; return (properties != null && !requiredNames(schema).containsAll(properties.keys)) || properties.orEmpty().values.any { (it as? JsonObject)?.let(::hasOptionalProperties) == true } || (schema[ITEMS] as? JsonObject)?.let(::hasOptionalProperties) == true`. **It does not look at `anyOf`, `oneOf`, `allOf`, `$defs`, `definitions`, `additionalProperties` (as a schema) or `prefixItems`.**
- `anthropic/`: `AnthropicProvider` (public class, `internal constructor`, `Builder` + `companion operator fun invoke`, internal `baseUrl`/`ioDispatcher`/`sleep`/`retryAfterCapMillis`/`transientBackoffMillis` builder vars, defaults 60 000 ms timeouts, 5 000 ms retry-after cap, 500 ms backoff, HTTPS-or-loopback check), `AnthropicTransport` (attempt loop with `MAX_REQUESTS = 3`, `notify` observer wrapper, private `refusalFor` / `isHeaderSafe`), `AnthropicErrors.kt` (`parseAnthropicError` → `AnthropicErrorInfo`, `STATUS_REASONS` map, spend-limit and low-credit flags), `AnthropicDecoder.kt` (`decodeAnthropicResponse(body, requestIdHeader, model, toolRequired)`; refusal/max_tokens stay `Success`; `Required` + end_turn + no tool → `NoToolCall`), `AnthropicStrict.kt` (`isAnthropicStrictEligible`), `AnthropicModels.kt` (exact-id table), `AnthropicAttemptObserver.kt` (public `fun interface`, `AnthropicAttempt`, value-class `AnthropicAttemptKind` with `INITIAL`, `TRANSIENT_RETRY`, `FORCED_TOOL_RESHAPE`).
- `build.gradle.kts`: `okhttpLegs = mapOf("Okhttp521" to "5.2.1", "Okhttp550" to "5.5.0")`; `filter { excludeTestsMatching("*Live*") }` on `test` and each leg; opt-in task `liveAnthropicCapture` (includes only `*AnthropicLiveCaptureTest`, `outputs.upToDateWhen { false }`, `onlyIf { optIn.orNull == "1" }`, not a dependency of `check`).
- Tests (`providers/src/test/.../providers/`): `anthropic/*` (15 files incl. `AnthropicFixtures.kt` top-level JSON builders, `AnthropicPipelineRetryTest`, `AnthropicCanaryTest`, `AnthropicLiveCaptureTest`), `http/*` (4 files), `OkHttpVersionGuardTest`, `ProvidersApiShapeTest` (reflective sweep of every main class: no enums, no `copy`/`componentN`, no public static fields besides `INSTANCE`/`Companion`, no public default-argument constructor stubs).
- `config/detekt/detekt.yml`: `buildUponDefaultConfig`, `maxIssues: 0`, `TooManyFunctions.thresholdInClasses: 12`, `LongParameterList.constructorThreshold: 8` with `ignoreDefaultParameters`, `ForbiddenImport` (okhttp3.internal.*, mockwebserver3.*, okhttp3.coroutines.*, android.util.Log, DI), `ForbiddenComment` (TODO:/FIXME:/STOPSHIP: and `T-xx-xx`, `WR-xx`, `Phase NN D-xx` planning ids). Catch parameters named `ignored`/`expected` pass `SwallowedException` (04-REVIEW-FIX).

**`:core` seam** (read this session; exact values):
- `ProviderId`: `public val OPENAI: ProviderId = ProviderId("openai")`, `public val OPENROUTER: ProviderId = ProviderId("openrouter")`.
- `ModelCapabilities` fields: `supportsTools`, `supportsForcedToolChoice`, `caching`, `minCacheablePrefixTokens`, `charsPerToken`; `CachingMode.EXPLICIT_BREAKPOINTS`, `AUTOMATIC`, `NONE`. `UNKNOWN` = `Builder().build()` (tools allowed, forced allowed, `NONE`, null minimum, 4.0).
- `AiProvider.capabilities(model: String): ModelCapabilities = ModelCapabilities.UNKNOWN` and `PipelineBuilder` line 160: `{ provider, model -> byId[provider]?.capabilities(model) ?: ModelCapabilities.UNKNOWN }`.
- `RoutedModel.complete`: `if (request.tools.isNotEmpty() && !binding.capabilities.supportsTools) { recorder.recordCode(TraceCode.CAPABILITY_REFUSED); return ModelResult.Failure(FailureReason.ModelUnsupported()) }`; `TraceCode.CAPABILITY_REFUSED = TraceCode("capability_refused")`.
- `Usage(inputUncached, cacheRead, cacheWrite, output)`; `total` saturating sum of all four; KDoc already states the OpenAI mapping `inputUncached = prompt_tokens - cached_tokens`, `cacheRead = cached_tokens`, `cacheWrite = 0`, `output = completion_tokens`.
- `StopReason` constants: `END_TURN "end_turn"`, `TOOL_USE "tool_use"`, `MAX_TOKENS "max_tokens"`, `REFUSAL "refusal"`, `PAUSE_TURN`, `CONTEXT_WINDOW_EXCEEDED "context_window_exceeded"`, `OTHER "other"`.
- `FailureReason` leaves used here: `Auth`, `Billing`, `RateLimited`, `Overloaded`, `Timeout`, `Network`, `MalformedResponse`, `MalformedToolArgs`, `Refusal`, `NoToolCall`, `ModelUnsupported`, `ModelNotFound`, `ContextWindowExceeded`, `HttpError`, `NotConfigured(provider)`, `Other(code)`.
- `ModelRequest(system, messages, tools, toolChoice, maxTokens, cache)`; `ToolChoice.Auto()` / `ToolChoice.Required(toolName)`; `AssistantPart.Text` / `AssistantPart.ToolCall(id, name, arguments: JsonObject)`; `NativeReplay(provider, model, raw: JsonElement)`; `AssistantMessage.nativeFor(provider, model)`; `ToolResult(callId, content, isError)`; `ToolSpec(name, description, inputSchema, mutating, terminal, strict: Boolean?)` ("true asks the provider for strict schema adherence, false asks it not to, and null lets the engine decide").
- Token ceiling: `TierPolicy` default `DEFAULT_TOKEN_CEILING = 60_000L`; `CommandSession.tokensUsed` = "the total of every turn's usage reported with recordTurn, across tiers"; strategies compare it with `policy.tokenCeiling`.
- Test fixtures (`core/src/testFixtures`): `FakeAiProvider`, `FakeMutation`, `RecordingCommitSink`, `RecordingEventListener`, `ScriptedGate`, `ScriptedStrategy`, `ScriptedCredentialSource`, `ScriptedSelectionSource`; composition pattern in `AnthropicPipelineRetryTest` (`commandPipeline { tier(..); provider(..); providerSelection = ..; credentials = ..; gate = ..; commitSink = .. }`, strategy step `{ _, session -> ... session.model().complete(request) ... session.submit(ToolStep.Mutation(m)) }`).

**Port sources (read-only)**: CT `app/src/main/java/com/caltracker/app/ai/` (`BaseAiProvider.kt` collapses on `finish_reason != "tool_calls"` first, CT's gate we do not port; `OpenAiLogFoodRequestBuilder.kt` emits the flat shape and strips numeric bounds; `OpenRouterModelCatalog.kt` private `isKnownTool400AnthropicId` = strip `anthropic/` then `replace('.', '-')`; `OpenRouterProvider.PROD_BASE_URL = "https://openrouter.ai/api/v1/chat/completions"`, `DEFAULT_MODEL = "openai/gpt-4o-mini"`; `OpenAiModelAllowlist.SET = setOf("gpt-4o-mini")`). SB has no OpenAI/OpenRouter code.

## Architecture Patterns

### System Architecture Diagram

```
 strategy -- model().complete(ModelRequest) --> RoutedModel (core)
   |  supportsTools=false + tools  --> ModelUnsupported (capability_refused), zero network calls
   v
 ChatCompletionsProvider.complete(ProviderRequest)            [id = vendor.providerId]
   |  credential check (provider match, isHeaderSafe) -> NotConfigured / Auth (no request)
   |  withContext(ioDispatcher)
   v
 ChatEncoder (pure) --- OpenAiModelRules(model) ---> reasoning_effort? / max_completion_tokens|max_tokens
   |   tools sorted by name, nested {type:function,function:{...}}, strict per ChatStrict, strip on wire copy
   |   Required -> named tool_choice (or auto+instruction when forced unsupported)
   |   vendor extras: OpenRouter provider.require_parameters on forced calls
   v
 request loop (<= 2 HTTP requests): Authorization: Bearer, OneShotJsonBody, client.newCall().await()
   |-- IOException/timeout, 408/429(not quota)/5xx/529/524, 200-envelope with transient code --> ONE retry (wait via RetryPolicy)
   v
 HttpReply --> 2xx? --> top-level "error" object? --yes--> status table(code) -> Failure(reason, FailureDetails)
                  |no
                  v
              ChatDecoder (pure): choices[0] -> D-10 precedence
                finish_reason "error" ----------------------------> Failure
                message.refusal / content_filter -----------------> Success(REFUSAL)
                length -------------------------------------------> Success(MAX_TOKENS), args NOT decoded
                tool_calls present (any finish_reason) -----------> arguments string -> JsonObject ("" -> {}; bad -> MalformedToolArgs)
                no tool call + Required --------------------------> Failure(NoToolCall)
                no tool call + Auto ------------------------------> Success(END_TURN)
                unknown finish_reason ----------------------------> Success(OTHER)
              Usage: prompt - cached - cache_write, cached, cache_write, completion  (+ NativeReplay(raw message))
   |-- non-2xx --> parse-and-discard error body -> status-first table (+ in-memory message matchers) -> Failure
   v
 observer.onAttempt(number, kind, httpStatus, finishReason, toolCalls)   (ids and counts only)
```

### Recommended Project Structure
```
providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/
├── http/                      # Phase 4, reused unchanged (+ isHeaderSafe moved here from AnthropicTransport)
├── schema/OptionalProperties.kt   # EXTEND: walk anyOf/oneOf/allOf/$defs/definitions/additionalProperties/prefixItems
├── anthropic/                 # untouched except deleting the private isHeaderSafe copy
└── chat/
    ├── ChatCompletionsProvider.kt        # public; companion openAi{}/openRouter{}; Builder (internal ctor)
    ├── ChatCompletionsAttemptObserver.kt # public fun interface + ChatCompletionsAttempt + value-class AttemptKind
    ├── ChatVendor.kt                     # internal config: id, baseUrl, path, request-id source, flags
    ├── ChatModels.kt                     # capabilities(vendor, model) incl. OpenRouter normalization
    ├── OpenAiModelRules.kt               # internal wire rules by family
    ├── ChatEncoder.kt  ChatMessageEncoder.kt
    ├── ChatStrict.kt                     # eligibility + strip (data-driven)
    ├── ChatDecoder.kt  ChatErrors.kt
    └── ChatTransport.kt
providers/src/test/kotlin/.../providers/chat/      # ChatFixtures.kt (top-level builders), ChatModelsTest, ChatEncoderTest,
                                                   # ChatStrictTest, ChatDecoderTest, ChatErrorMapTest, ChatTransportTest,
                                                   # ChatRetryTest, ChatPipelineRetryTest, ChatAbsentOptionalTest (per vendor),
                                                   # ChatCanaryTest, ChatMalformedKeyTest, ChatCompletionsLiveCaptureTest (opt-in)
providers/src/test/kotlin/.../providers/parity/TokenParityTest.kt
providers/src/test/resources/golden/chat/{openai,openrouter}/*.json   # sanitized; derived vs captured tracked in a manifest
```
`ProvidersApiShapeTest` sweeps every main class automatically, so the new public types are checked for free; add `ChatCompletionsProvider` and the observer types to its `sweepIsNotVacuous` list.

### Pattern 1: Capability resolution and OpenRouter normalization (PROV-08, D-02, D-03, D-04)
**What:** `ChatCompletionsProvider.capabilities(model)` returns the public `ModelCapabilities`; a separate internal `OpenAiModelRules.rulesFor(normalizedId)` returns wire-only rules.
**Normalization for lookup** (OpenRouter only; the exact id the app passes, `openai/gpt-5.4-mini`, is still what goes on the wire and what app overrides key on):
1. strip a routing-variant suffix after `:` (`:free`, `:nitro`, `:online`, ...) [ASSUMED variants exist, harmless if not];
2. split at the first `/`: vendor `openai` → apply the OpenAI rules to the remainder; vendor `anthropic` → remainder with every `.` replaced by `-` (CT's rule, verified in CT source above) looked up in `AnthropicModels.capabilities` for `supportsForcedToolChoice`, but **`caching` forced to `NONE`** (no `cache_control` is sent through OpenRouter in v1.0, and `AUTOMATIC`/`EXPLICIT_BREAKPOINTS` would make the router's cache diagnostic fire on every call); any other vendor → `ModelCapabilities.UNKNOWN`.

**OpenAI rules table** (wire-only, internal; match on the normalized id with a regex, not an exact set, because OpenAI ids carry date suffixes):

| Normalized id | Tools on Chat | Reasoning param when tools present | Token param | Source |
|---|---|---|---|---|
| `gpt-6-astra`, `gpt-6.1-sol` | **OpenAI provider: `supportsTools=false`** ("Responses API required"); OpenRouter provider: allowed | OpenAI: n/a. OpenRouter: `reasoning_effort:"low"` (these two reject `none`) | `max_completion_tokens` | [CITED: developers.openai.com/api/docs/guides/latest-model.md, function-calling.md] |
| `gpt-6-sol`, `gpt-6-luna`, `gpt-5.<N>` with N ≥ 4 (incl. `gpt-5.4-mini`), `gpt-6*` otherwise | allowed | `reasoning_effort:"none"` | `max_completion_tokens` | latest-model.md: "GPT-6 Sol and GPT-6 Luna support function calling in Chat Completions only with `reasoning_effort: "none"`"; research/ARCHITECTURE.md for 5.4+ |
| `gpt-5`, `gpt-5-mini`, `gpt-5-nano`, `gpt-5.1`..`gpt-5.3`, `o<digit>...` | allowed | omit (reasoning with tools is allowed; `none` is rejected on original gpt-5 and o-series) | `max_completion_tokens` | [ASSUMED] boundary at 5.4 |
| `gpt-4*`, `gpt-3.5*` | allowed | omit (`gpt-4o-mini` has no such parameter) | `max_tokens` (CT-proven live on gpt-4o-mini) | CT BaseAiProvider/builder |
| any other OpenAI id | allowed | omit | `max_completion_tokens` | OpenAI marks `max_tokens` deprecated |
| OpenRouter non-`openai/` id | allowed | omit | `max_tokens`, clamped to ≥ 16 ("some providers enforce a minimum of 16") | [CITED: openrouter.ai chat API reference] |

Rules for `caching`: OpenAI ids and OpenRouter `openai/*`: `AUTOMATIC` with `minCacheablePrefixTokens = 1024` for `gpt-*` / `o<digit>` ids (automatic caching "≥1,024 tokens", research/FEATURES.md), null otherwise. All other OpenRouter vendors: `NONE`.

**Reactive typed refusals (parse-and-discard matchers, never stored):** a 400 or 404 whose parsed `error.message` contains `/v1/responses` → `ModelUnsupported` (covers the exact gpt-6-astra text `Function tools with reasoning_effort are not supported for gpt-6-astra in /v1/chat/completions. To use function tools, use /v1/responses or set reasoning_effort to 'none'.` [CITED: langchain issue #40346] and Responses-only models such as pro/codex ids without any guessed id list). On OpenRouter a 404 whose message contains `No endpoints found that support` → `ModelUnsupported` (exact text `No endpoints found that support the provided 'tool_choice' value. ...` is a 404 [CITED: zed issue #36094]); any other 404 → `ModelNotFound`.

### Pattern 2: Request encoding (golden request bodies, success criterion 1)
Nested tool shape and top-level keys in a fixed order: `model`, `messages`, `tools`, `tool_choice`, `parallel_tool_calls`, `reasoning_effort`, `max_completion_tokens` | `max_tokens`, `provider`.

```json
{"model":"gpt-5.4-mini",
 "messages":[{"role":"system","content":"..."},{"role":"user","content":"add milk"}],
 "tools":[{"type":"function","function":{"name":"log_food","description":"...","parameters":{...},"strict":true}}],
 "tool_choice":{"type":"function","function":{"name":"log_food"}},
 "parallel_tool_calls":false,
 "reasoning_effort":"none",
 "max_completion_tokens":1024}
```
OpenRouter forced call adds `"provider":{"require_parameters":true}`. Rules:
- Tools sorted by `name` (byte-stable prefix for automatic caching), `parameters` emitted as the app built it except the strict-mode strip (below), no per-request values anywhere in tools/system. No `stream`. No `temperature`/`top_p`.
- `system` becomes the first message `{"role":"system"}` only when non-blank. `CacheDirective` is ignored (OpenAI caches automatically; OpenRouter `anthropic/*` is uncached in v1.0).
- `tool_choice`: `Auto` → `"auto"` (only when tools non-empty); `Required(name)` with `supportsForcedToolChoice` → `{"type":"function","function":{"name":name}}` [CITED: openai-node `ChatCompletionNamedToolChoice`]; `Required` with forced unsupported (e.g. OpenRouter `anthropic/claude-sonnet-5.5`) → `"auto"` and the closing instruction `"\n\nCall the <name> tool with your result."` appended to the last user message's content (never the system message), and no `strict`, no `require_parameters`.
- `strict`: Chat Completions "remain non-strict by default" [CITED: function-calling.md], so the key must be sent explicitly and only when the effective strict is true; when false, **omit** the key. OpenRouter: only when the normalized vendor is `openai` (strict passthrough to other upstreams is unverified, [ASSUMED], so send non-strict there plus local validation).
- `reasoning_effort`: `"none"` (or `"low"` for the two Astra/Sol ids on OpenRouter) whenever `tools` is non-empty and the rule asks for it; otherwise omitted. Same top-level key on both vendors (OpenRouter documents it as "Shorthand for setting reasoning effort. Equivalent to setting reasoning.effort" and lists it for `openai/gpt-5.4-mini`) [CITED: openrouter.ai/docs/api/api-reference/chat/send-chat-completion-request, openrouter.ai/openai/gpt-5.4-mini].
- `parallel_tool_calls:false`: sent when `toolChoice` is `Required` (single-shot) **on OpenAI**; also whenever strict is used on OpenAI (OpenAI's guidance for strict is single calls; "`parallel_tool_calls` to `false`, which ensures exactly zero or one tool is called" [CITED: function-calling.md]). On OpenRouter the vendor flag defaults to **off** (Pitfall 7, Open Question 3). Auto agentic requests (Phase 9) omit it.
- Messages (exhaustive over the sealed `Message`): `UserMessage` → `{"role":"user","content":text}`; `AssistantMessage` → the stored raw message verbatim when `nativeFor(vendorProviderId, model)` is non-null, else rebuilt `{"role":"assistant","content":text|null,"tool_calls":[{"id","type":"function","function":{"name","arguments":"<compact JSON string>"}}]}`; `ToolResultsMessage` → **one `{"role":"tool","tool_call_id","content"}` message per result in order** (Chat has no `is_error` field; Phase 5 sends `content` unchanged and leaves any error marker to Phase 8's conformance work).

### Pattern 3: Strict eligibility, strip, and PROV-12 (D-05, D-06, D-07, D-08)
**Effective strict (engine decides, app may only opt out):** `strict = tool.strict != false && eligible(schema)`; `ToolSpec.strict == true` on an ineligible schema is still non-strict (the engine wins; this is the data-loss guard). `eligible(schema)` (new `chat/ChatStrict.kt`, pure):
1. root is `type: "object"` (root must not be `anyOf`) [CITED: structured-outputs.md "Root objects must not be `anyOf` and must be an object"];
2. `!hasOptionalProperties(schema)` using the **extended** detector;
3. every object node (any node with `properties` or `type` containing `object`) has `additionalProperties: false`;
4. no structural keyword the strict subset rejects: `allOf` (non-singleton), `oneOf`, `not`, `if`, `then`, `else`, `dependentRequired`, `dependentSchemas`, `dependencies`, `prefixItems`, `patternProperties`, `unevaluatedProperties`, `unevaluatedItems`, `$dynamicRef`/`$anchor`-class keywords;
5. size limits: ≤ 5 000 object properties, ≤ 10 nesting levels, total name/enum/const text ≤ 120 000 chars, ≤ 1 000 enum values [CITED: structured-outputs.md]. (Implementation may use a cheap conservative counter; failing a limit means "non-strict", never a 400.)

**Required-but-nullable stays eligible** (`type:["string","null"]` or `anyOf` with a `null` branch while still listed in `required`) because it is not an optional property by the detector's definition (it is named in `required`).

**Extended detector (Wave 1, additive):** keep the current signature; also recurse into `anyOf`, `oneOf`, `allOf` arrays, `$defs` and `definitions` values (all, reachable or not: conservative), `additionalProperties` when it is a schema object, and `prefixItems`. It then returns `true` in strictly more cases, which is safe for Anthropic (its `UNSUPPORTED_KEYWORDS` already makes any schema using those keywords ineligible). Tests: one case per newly walked keyword proving `true`, and a regression case proving a nullable required property is still `false`.

**Strip (data-driven, wire copy only, only when strict is being sent):** two constants with a source comment, no ids in comments:
- drop-always keywords (validation-only, the SDK throws on them): `uniqueItems`, `minProperties`, `maxProperties`, `contains`, `minContains`, `maxContains`, `propertyNames`, `contentEncoding`, `contentMediaType`, root `$schema`/`$id`;
- `format` is dropped unless its value is one of `date-time`, `time`, `date`, `duration`, `email`, `hostname`, `ipv4`, `ipv6`, `uuid` [CITED: structured-outputs.md "Supported `string` properties"].
Everything else (`minLength`, `maxLength`, `pattern`, `minimum`, `maximum`, `exclusiveMinimum`, `exclusiveMaximum`, `multipleOf`, `minItems`, `maxItems`, `default`, `enum`, `description`) is kept: current docs support them for non-fine-tuned models ("For fine-tuned models, we additionally do not support ..."). The walker must visit **schema positions only** (values of `properties`, `items`, `anyOf`, `$defs`, `definitions`), never property *names*: a property literally called `format` or `pattern` must survive (Pitfall 3). Non-strict calls send the schema untouched (D-07).

**PROV-12 contract (both vendors):** the encoder tests assert `log_food`-shaped tool → `"strict":true`, EDIT-shaped tool → no `strict` key and the schema byte-identical to input; the decode test feeds an `arguments` string that omits the optional keys and asserts the decoded `JsonObject` has exactly the keys the model sent (no `""`, `[]`, or default added). The decoder never consults the tool schema.

### Pattern 4: Decode order and typed outcomes (success criterion 2, D-09, D-10, D-11)
Input is the 2xx body; output `ModelResult`. Never throws, never copies body text anywhere (mirror `decodeAnthropicResponse`: a private exception carrying a reason, caught as `IllegalArgumentException`).

| Step | Condition | Result |
|---|---|---|
| 1 | not a JSON object / blank | `Failure(MalformedResponse)` |
| 2 | top-level `error` object present (even with `choices: []`) | `Failure(reason(code), FailureDetails(httpStatus = error.code if numeric, providerErrorType = safeToken(error.metadata.error_type), requestId = safeRequestId(id)))`; `message`, `metadata.raw`, `metadata.provider_name` read nowhere |
| 3 | `choices` missing/empty | `Failure(MalformedResponse)` |
| 4 | `finish_reason == "error"` | `Failure(HttpError)` with `providerErrorType = safeToken(native_finish_reason)` (discretion, Open Question 2) |
| 5 | `message.refusal` non-null, or `finish_reason == "content_filter"` | `Success`, `StopReason.REFUSAL`, no tool calls decoded, refusal text never copied into a part |
| 6 | `finish_reason == "length"` | `Success`, `StopReason.MAX_TOKENS`, tool-call arguments **not** decoded (truncated JSON must not become `MalformedToolArgs`) |
| 7 | `tool_calls` non-empty | decode each: id and name non-blank else `MalformedResponse`; `arguments` string `""` → `{}`; JSON object → used; anything else (invalid JSON, array, scalar, `null`) → `Failure(MalformedToolArgs)`; an `arguments` that is already a JSON object is accepted (some routed upstreams send it); calls with `type != "function"` are skipped. Result `Success`, `StopReason.TOOL_USE`. **Presence decides**; `finish_reason` other than `tool_calls` is reported to the observer, not treated as an error |
| 8 | no tool calls, `finish_reason` is `stop` | `Required` request → `Failure(NoToolCall)`; `Auto` → `Success(END_TURN)` |
| 9 | `finish_reason == "tool_calls"` but no tool calls | `Failure(MalformedResponse)` |
| 10 | any other / null `finish_reason` (OpenRouter can leak a raw upstream value) | `Success`, `StopReason.OTHER` |

`AssistantMessage(parts, NativeReplay(vendor.providerId, model, rawMessageObject))`; `Text` parts only for non-empty string `content`. Request id: OpenAI `x-request-id` response header; OpenRouter the body `id` (`gen-...`), both through `safeRequestId`.

**200-envelope status mapping** reuses the non-2xx table with `error.code` as the status. A 200 envelope whose code is transient (`isTransientStatus`, plus 524) goes through the one transient retry like a 5xx (field reports show OpenRouter delivering rate limits inside 200 bodies, gbrain #5473).

### Pattern 5: Usage normalization and TEL-01 (Runtime Decision)
| Wire | Mapping to `Usage(inputUncached, cacheRead, cacheWrite, output)` |
|---|---|
| Anthropic (Phase 4) | `input_tokens`, `cache_read_input_tokens`, `cache_creation_input_tokens`, `output_tokens` |
| OpenAI / OpenRouter | `inputUncached = max(0, prompt_tokens - cached_tokens - cache_write_tokens)`, `cacheRead = prompt_tokens_details.cached_tokens`, `cacheWrite = prompt_tokens_details.cache_write_tokens`, `output = completion_tokens` (reasoning tokens are already inside `completion_tokens` [VERIFIED: openai-node `CompletionTokensDetails` docs: "like reasoning tokens, these tokens are still counted in the total completion tokens"]) |
Every field optional/nullable; missing `usage` → `Usage.ZERO`; negative or non-numeric → 0 (`Usage` throws on negatives). `cache_write_tokens` exists on `PromptTokensDetails` in OpenAI's SDK ("The unadjusted number of prompt tokens written to cache") and on OpenRouter; whether `prompt_tokens` includes it is [ASSUMED]; the subtraction is clamped so total never exceeds `prompt_tokens + completion_tokens`.

**Parity test (providers/src/test/.../parity/TokenParityTest.kt):** one scripted logical call (uncached 120, cacheRead 1 800, cacheWrite 0, output 55; plus a cacheWrite variant 500) expressed as three MockWebServer bodies (Anthropic usage block; OpenAI `prompt_tokens=1920`, `cached_tokens=1800`, `completion_tokens=55`; OpenRouter same plus `cache_write_tokens` and a `cost` field). Assert (a) identical `Usage` from each decoder, (b) the real providers through `commandPipeline` leave `session.tokensUsed` equal, (c) for ceilings `total-1`, `total`, `total+1` the comparison `session.tokensUsed > ceiling` has the same outcome on all three (SB sum semantics = `Usage.total`).

### Pattern 6: Provider, transport, observer (PROV-09/11/13 for Chat)
- `public class ChatCompletionsProvider internal constructor(...) : AiProvider`; `companion` with `public fun openAi(block: Builder.() -> Unit): ChatCompletionsProvider` and `openRouter`. Builder public fields mirror Anthropic's: `httpClient`, `callTimeoutMillis` and `readTimeoutMillis` (default 60 000, must be positive), `attemptObserver`; internal `baseUrl`, `ioDispatcher`, `sleep`, `retryAfterCapMillis`, `transientBackoffMillis`. `toString` prints timeouts only. Production base URLs `https://api.openai.com/v1/` and `https://openrouter.ai/api/v1/`, path `chat/completions`; HTTPS-or-loopback check as in Anthropic.
- `ChatTransport`: structure of `AnthropicTransport` minus the forced-400 reshape: `MAX_REQUESTS = 2` (initial plus one transient retry), `ioFailure` honors cancellation (`currentCoroutineContext().ensureActive()`), `InterruptedIOException` → `Timeout`, `IOException` → `Network`, observer `notify` swallowing with a `catch (ignored: Exception)` carrying the same justification comment (no `@Suppress`; 04-REVIEW-FIX shows detekt accepts it).
- Header: `Authorization: Bearer <key>` and `Content-Type: application/json`; OpenRouter attribution headers (`HTTP-Referer`, `X-Title`) stay omitted (CT decision).
- **Move `isHeaderSafe` to `http/SafeFields.kt` as `internal`** and delete the private copy in `AnthropicTransport.kt`: the Bearer value must be vetted the same way (a pasted trailing newline makes OkHttp throw an exception whose message quotes the whole header value, the CR-01 leak). Credential refusal: provider mismatch or null → `NotConfigured(vendor.providerId)`; header-unsafe key → `Auth`, zero requests.
- Observer: `ChatCompletionsAttemptObserver` (`fun interface`), `ChatCompletionsAttempt(number, kind, httpStatus, finishReason: String?, toolCalls: Int)`, value-class `ChatCompletionsAttemptKind` (`INITIAL`, `TRANSIENT_RETRY`). `finishReason` passes `safeToken`. This is how D-09's "trace note when finish_reason disagrees" is delivered without a core change (Open Question 1).

### Anti-Patterns to Avoid
- Porting CT's `finish_reason != "tool_calls"` gate or its flat tool shape (`{type:function,name,parameters,strict}`): Chat Completions needs the nested shape (`Missing required parameter: 'tools[0].function'`, research/ARCHITECTURE.md).
- Adding `requiresReasoningNoneWithTools` / `maxTokensParamName` to public `ModelCapabilities`.
- `catch (e: Exception)` / `runCatching` in `:providers` outside the single observer wrapper.
- Stripping inside `enum`/`const`/`default` values or on property names.
- A generic "OpenAI-compatible" fallthrough that sends `strict:true` to non-OpenAI OpenRouter upstreams.
- Reading `error.message` into anything but the in-memory matchers; storing `metadata.raw`, `metadata.provider_name`, `reasoning`, refusal text.
- Using `mockwebserver3`, `okhttp3.internal.*`, `executeAsync`, `Response.body.string()`.
- Putting `PROV-08`/`D-07`-style ids in source comments (detekt `ForbiddenComment` + scanner).

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| HTTP, cancellation, late-response close | a new bridge | `Call.await()` + `cleanClient` + `OneShotJsonBody` from `http/` | Reviewed, matrix-tested, key-hygiene hardened (proxy auth, cookies, redirects) |
| Retry waits | a second backoff | `transientWaitMillis` / `retryAfterSeconds` / `isTransientStatus` | One budget, injectable sleep |
| Server-string hygiene | a new regex | `safeToken` / `safeRequestId` | `FailureDetails` and `ModelResponse` throw on odd strings |
| Optional-property detection | a Chat-specific walker | extend `hasOptionalProperties` | "the one place that decides what optional means" |
| Strict keyword list from memory | hard-coded old CT list | the data constants in `ChatStrict.kt` with the 2026-10-01 doc/SDK sources | The supported subset moved since CT's research |
| JSON bodies | string concatenation | `buildJsonObject` / `JsonObject` | escaping, stable insertion order |
| Test fakes | new fakes | `core` testFixtures (`ScriptedStrategy`, `RecordingCommitSink`, ...) | Already on the test classpath |

**Key insight:** the risk is not code volume but silent wire divergence (flat vs nested tools, a parameter a model rejects, a 200 that is really an error, optionals wiped by strict mode). Each has a mechanical test in the Validation map.

## Common Pitfalls

### Pitfall 1: The shared detector under-reports optionals
**What goes wrong:** `strict:true` is sent for a schema with an optional inside an `anyOf` branch or `$defs`; OpenAI either rejects it or the model fills the optional with `""`/`[]` (SB treats those as "clear").
**How to avoid:** extend `hasOptionalProperties` first (Wave 1) and test each newly walked keyword. **Warning signs:** a golden EDIT request with `"strict":true`.

### Pitfall 2: ToolSpec.strict == true overriding the engine
**What goes wrong:** an app sets `strict = true` on a tool with optionals and gets data loss. **How to avoid:** D-06: `strict` can only opt out. Test: `strict=true` + optional schema → no `strict` key.

### Pitfall 3: Strip touches names
**What goes wrong:** a property named `format`, `pattern` or `contains` is deleted from `properties`. **How to avoid:** walk schema positions only; test a tool with properties named after dropped keywords.

### Pitfall 4: reasoning parameters by model family
**What goes wrong:** omitting `reasoning_effort` on gpt-5.4+/5.6/GPT-6 Sol/Luna with tools → 400 `Function tools with reasoning_effort are not supported for <model> in /v1/chat/completions. To use function tools, use /v1/responses or set reasoning_effort to 'none'.` [CITED: OpenAI community thread 1386454, langchain #40346]; sending `reasoning_effort` to `gpt-4o-mini` or `none` to original gpt-5/o-series → 400. `max_tokens` on reasoning models → 400. **How to avoid:** the rules table + tests per row; the reactive `/v1/responses` matcher turns an unknown future id's 400 into `ModelUnsupported` instead of `HttpError`.

### Pitfall 5: `none` plus a token cap can still burn the budget
**What goes wrong:** April 2026 bug: `reasoning_effort:"none"` ignored with `max_completion_tokens`, all tokens spent on hidden reasoning, empty content, `finish_reason:"length"` (fixed 2026-04-20 per OpenAI Support, community thread 1378362). **How to avoid:** `length` → `MAX_TOKENS` with no tool call (decode step 6) and a derivative golden for it; the default `maxTokens` stays the policy's.

### Pitfall 6: finish_reason lies about tool turns
OpenAI has returned `stop` for forced named functions [CITED: community thread 731488], and OpenRouter routed providers return `stop` or raw upstream values [CITED: pydantic-ai #2844, OpenRouter docs]. Presence of `tool_calls` decides.

### Pitfall 7: `require_parameters` is a hard filter over every parameter
OpenRouter: "Only use providers that support all parameters in your request"; when none qualifies it answers 404 `No endpoints found that support the provided 'tool_choice' value` (or 503 per its error table). The `openai/gpt-5.4-mini` page lists `max_completion_tokens, max_tokens, reasoning, reasoning_effort, response_format, seed, tool_choice, tools` and **does not list `parallel_tool_calls`**. **How to avoid:** vendor flag `parallelToolCallsFalseOnForced`: OpenAI `true`, OpenRouter `false` until capture call R1 proves the full forced body is accepted.

### Pitfall 8: 429 is not always a rate limit
OpenAI answers quota problems with HTTP 429 and `error.code`/`type` `insufficient_quota` (credit balance exhausted, org/project spend limit); retrying cannot succeed. Map to `Billing`, non-transient. OpenRouter uses 402 for insufficient credits.

### Pitfall 9: A key with a newline reaches OkHttp
`Authorization: Bearer <key>` with a trailing `\n` throws an `IllegalArgumentException` quoting the value. Vet with the shared `isHeaderSafe` before building the request (CR-01).

### Pitfall 10: Cached tokens double-counted
`prompt_tokens` already includes `cached_tokens` on both vendors; summing raw fields would break the ceiling. The parity test is the guard.

### Pitfall 11: Goldens leak or collide with repo hygiene
Sanitize ids (`chatcmpl-*`, `gen-*`, `call_*`), `system_fingerprint`, `created`, any `user_id`, headers (never stored). Do not name a file `*sb-a10-fixture*`, `*baseline*.xml` or `*api.txt` (hygiene script forbids them); the global pre-commit scan blocks key-shaped strings (`sk-`, `sk-or-`, `sk-proj-`). Test keys in tests use the `sk-CANARY-KEY-BODY` style Phase 4 settled on.

### Pitfall 12: detekt on a branchy decoder
`ReturnCount`, `CyclomaticComplexMethod`, `NestedBlockDepth`, `TooManyFunctions` (12 per class), `MagicNumber` (HTTP statuses, 16, 1024) are all on. Split the decoder into one small private function per table row and keep constants named. Run `:providers:detekt` per task, not only at the end.

### Pitfall 13: Live tests reachable from `check`
Name every live class `*Live*` (the filter on `test` and both legs already excludes it) and register the task outside `check`'s graph.

### Pitfall 14: OpenAI tool name rule
Function names must be `a-z, A-Z, 0-9, underscores and dashes, max length 64` [VERIFIED: openai-node `FunctionDefinition.name` doc]. `ToolSpec` only requires non-blank; a violating name yields a 400 (`HttpError` with status 400). Document it in the provider KDoc rather than rewriting names.

## Code Examples

### Capability resolution with OpenRouter normalization (sketch)
```kotlin
// Source: pattern of providers/anthropic/AnthropicModels.kt + CT OpenRouterModelCatalog normalization rule
internal fun chatCapabilities(vendor: ChatVendor, model: String): ModelCapabilities {
    val (family, id) = vendor.normalize(model)          // "openai/gpt-5.4-mini:nitro" -> (OPENAI_FAMILY, "gpt-5.4-mini")
    return when (family) {
        Family.OPENAI -> OpenAiModelRules.capabilities(id, onOpenAiProvider = vendor.isOpenAi)
        Family.ANTHROPIC_VIA_ROUTER -> ModelCapabilities {
            supportsForcedToolChoice = AnthropicModels.capabilities(id.replace('.', '-')).supportsForcedToolChoice
            caching = CachingMode.NONE              // no cache_control is sent through the router in v1.0
        }
        Family.OTHER -> ModelCapabilities.UNKNOWN
    }
}
```

### Decode core (D-10 precedence, shape only)
```kotlin
// Source: mirrors providers/anthropic/AnthropicDecoder.kt structure; one small function per table row for detekt
internal fun decodeChatResponse(body: String?, requestId: String?, model: String, id: ProviderId, toolRequired: Boolean): ChatDecoded =
    try {
        val root = parseRoot(body)
        root.errorEnvelope()?.let { return ChatDecoded.failure(it) }          // step 2 (every 200)
        val choice = firstChoice(root)                                        // step 3
        when {
            choice.finishReason == "error" -> ChatDecoded.failure(errorFinish(choice, requestId))
            choice.refusal != null || choice.finishReason == "content_filter" -> success(StopReason.REFUSAL, ...)
            choice.finishReason == "length" -> success(StopReason.MAX_TOKENS, ...)       // args NOT decoded
            choice.toolCalls.isNotEmpty() -> success(StopReason.TOOL_USE, decodeCalls(choice))   // presence decides
            choice.finishReason == "stop" -> if (toolRequired) failure(NoToolCall()) else success(StopReason.END_TURN, ...)
            choice.finishReason == "tool_calls" -> failure(MalformedResponse())
            else -> success(StopReason.OTHER, ...)
        }
    } catch (e: IllegalArgumentException) {
        ChatDecoded.failure((e as? MalformedAnswer)?.reason ?: FailureReason.MalformedResponse())   // SerializationException is a subclass
    }
```

### Usage mapping
```kotlin
// Source: Usage.kt KDoc mapping + OpenAI CompletionUsage/PromptTokensDetails (openai-node completions.ts)
private fun decodeUsage(element: JsonElement?): Usage {
    val usage = element as? JsonObject ?: return Usage.ZERO
    val details = usage["prompt_tokens_details"] as? JsonObject
    val prompt = count(usage, "prompt_tokens")
    val read = count(details, "cached_tokens")
    val write = count(details, "cache_write_tokens")
    return Usage(
        inputUncached = (prompt - read - write).coerceAtLeast(0L),
        cacheRead = read, cacheWrite = write, output = count(usage, "completion_tokens"),
    )
}
```

### Error-map table (status first; `FailureDetails(status, errorType?, requestId?)` always via `safeToken`/`safeRequestId`)
| Condition | Reason |
|---|---|
| 401, 403 | `Auth` (OpenRouter 403 whose `error.metadata` has a `reasons` array, i.e. a moderation flag: `Refusal`, discretion) |
| 402 (OpenRouter) | `Billing` |
| 429 with `error.code` or `error.type` `insufficient_quota` | `Billing`, not retried |
| 429 otherwise | `RateLimited` (transient) |
| 400 with `error.code` / `error_type` `context_length_exceeded` | `ContextWindowExceeded` (discretion; typed leaf exists) |
| 400 or 404 mentioning `/v1/responses` (in memory) | `ModelUnsupported` |
| 404 OpenRouter `No endpoints found that support` | `ModelUnsupported`; other 404 → `ModelNotFound` |
| 408, 504 / `InterruptedIOException` | `Timeout` (408 transient) |
| 502, 503, 529, 524 | `Overloaded` for 503/529; 502/524 `HttpError`; all transient |
| other `IOException` | `Network` |
| 500 and any other non-2xx | `HttpError` (500 transient) |
| 2xx not parseable / no choices | `MalformedResponse` |
| `arguments` invalid or non-object | `MalformedToolArgs` |

### Sanitizer rules for committed goldens (capture plan)
Replace `"id":"chatcmpl-..."` and `"id":"gen-..."` with `...-GOLDEN`, each `call_...`/tool-call id with a stable `call_GOLDEN<n>`, `system_fingerprint` with `null`, `created` with `0`, drop `service_tier`, `cost`, `cost_details`, `user_id`; keep usage token counts, `finish_reason`, `native_finish_reason`, `message`, `refusal`, `reasoning_details` structure. Never store headers. Write the raw body only under `providers/build/` (gitignored); commit only the sanitized copy plus a one-line provenance header in the manifest (date, vendor, model, call label).

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Strict rejects numeric/length/array bounds | Strict supports `pattern`, `format` (9 values), `minimum`/`maximum`/exclusive, `multipleOf`, `minItems`/`maxItems`; rejects composition `allOf`/`not`/`if`/`then`/`else`/`dependent*` (+ SDK: `oneOf`, `uniqueItems`, ...) | docs fetched 2026-10-01; SDK commit 2026-09-04 "reject unsupported oneOf" | CT's Pitfall 2 strip list is stale; strip less, ineligible-out more |
| `max_tokens` | `max_completion_tokens` (`max_tokens` deprecated, "not compatible with o-series models") | 2024-2025 | rules table |
| gpt-5.x tools + reasoning on Chat | gpt-5.4+ and GPT-6 Sol/Luna: tools need `reasoning_effort:"none"`; GPT-6 Astra / 6.1 Sol: tools Responses-only | 2026 | `supportsTools=false` for two ids on OpenAI |
| OpenRouter errors only as 4xx/5xx | also 200 with `error` body (no `choices` or `choices: []`) | documented | decode step 2 on every 200 |
| `max_tokens` on OpenRouter | `max_completion_tokens` preferred, `max_tokens` "deprecated" | current | rules table; clamp ≥ 16 on legacy path |

**Deprecated/outdated:** `docs.anthropic.com` style redirects; `platform.openai.com/docs/...` now 301s to `developers.openai.com/api/docs/...` (append `.md` for a clean text version).

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | OpenAI accepts `reasoning_effort:"none"` together with a forced named `tool_choice` and returns the call on gpt-5.4-mini (docs say default effort is `none`) | Pattern 2 | Capture C1 shows a 400; fall back to `auto`+instruction for that family |
| A2 | `finish_reason` under a forced named tool on gpt-5.4-mini may be `stop` or `tool_calls`; presence-first decoding is correct either way | Pattern 4 | None for correctness; capture C1 records the real value |
| A3 | OpenRouter accepts the OpenAI-shaped body (`reasoning_effort`, `max_completion_tokens`, `provider.require_parameters`, nested tools, `strict`) for `openai/gpt-5.4-mini` | Pattern 2 | Capture R1; fallback `reasoning:{effort}` / `max_tokens` |
| A4 | OpenRouter `parallel_tool_calls:false` + `require_parameters:true` could 404 on models whose page omits `parallel_tool_calls` | Pitfall 7 | If safe, flip the vendor flag; if unsafe and we had sent it, forced calls would fail loudly |
| A5 | `prompt_tokens` includes `cache_write_tokens` on OpenAI/OpenRouter (so it is subtracted) | Pattern 5 | Under-count of `inputUncached` by the write amount; clamp keeps totals non-negative; parity test fixture documents the assumption |
| A6 | OpenRouter `openai/gpt-6-astra` and `gpt-6.1-sol` accept tools on Chat with `reasoning_effort:"low"` (OpenRouter advertises `tools`/`tool_choice` for both; effort "low" is OpenAI's stated minimum) | Pattern 1 | Wrong effort value → 400 from the router; those two ids are not in the capture set |
| A7 | Boundary for "reasoning + tools needs `none`" is gpt-5.4 (not 5.1-5.3) | Pattern 1 | A 5.1-5.3 model rejects `none`? Unlikely (5.1+ support it); worst case an extra parameter sent that is accepted |
| A8 | OpenRouter routing-variant suffixes (`:free`, `:nitro`, `:online`) exist and should be stripped for lookup | Pattern 1 | Lookup misses fall back to `UNKNOWN`, which is safe |
| A9 | OpenRouter 403 with `metadata.reasons` means a moderation flag (`Refusal`) | Error map | Mislabel only; `Auth` is the table default |
| A10 | OpenRouter passes `strict` through only usefully for OpenAI upstreams; omit elsewhere | Pattern 2 | None (non-strict is always valid) |
| A11 | `system` role is accepted for gpt-5.x on Chat Completions | Pattern 2 | Capture C1 uses it; fallback `developer` role |
| A12 | Quota-style 429 variants all carry `insufficient_quota` as `code` or `type` | Error map | A variant maps to `RateLimited` and gets one pointless retry |
| A13 | The 404 text "not a chat model ... v1/chat/completions" for legacy models exists | Pattern 1 | Matcher simply never fires |
| A14 | A transient retry of a 200-with-error-envelope never double-bills | Pattern 4 | Provider failed, so no output billed; low risk |

## Open Questions

1. **Trace note for D-09.** `AiProvider.complete` has no recorder, same as Phase 4. Recommendation: deliver the note through the new public `ChatCompletionsAttempt` (finish reason + tool-call count) and tell the orchestrator a true `TraceCode` would be an additive `:core` change; do not block.
2. **Reason for `finish_reason:"error"`.** No leaf fits exactly. Recommendation: `HttpError` with `FailureDetails(httpStatus = 200, providerErrorType = safeToken(native_finish_reason))`; confirm in plan-check (alternative: `Other("provider_error")`).
3. **`parallel_tool_calls` on OpenRouter.** Default off; capture call R1 sends the full forced body with it on to prove or disprove A4. If proven safe, a one-line flag flip in `ChatVendor`.
4. **Strip policy for numeric bounds.** Docs say supported; CT's research said unsupported. Recommendation: follow the docs (keep them) and let capture call C1/C3 include `minimum`/`maximum`/`exclusiveMinimum` on the log_food-shaped tool as live proof; the strip list is one constant if reality disagrees.
5. **Public observer duplication.** A second public observer family (Chat) next to Anthropic's. Not yet frozen (no `api.txt`), so merging them into one provider-neutral type is still possible; recommend keeping them separate to avoid churn in reviewed Phase 4 code.
6. **Key-gated capture timing (D-16).** See Environment Availability: both key names are present, but capture waits for the orchestrator's relay.

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| JDK 17, Gradle wrapper 9.4.1, offline caches (OkHttp 4.12.0 / 5.2.1 / 5.5.0 + mockwebserver) | build, matrix | yes (full providers gate green, 144 tests per leg) | OpenJDK 17 | — |
| `with-test-keys` | opt-in capture | yes (`/home/yahir/.local/bin/with-test-keys`) | — | none; capture is optional |
| `OPENAI_API_KEY` via `with-test-keys --only openai` | capture | **present** (checked by variable name only; value never read or printed) | — | tell Yahir the `install -m 600 /dev/stdin ~/.config/test-keys/openai.key` command if the wrapper later refuses |
| `OPENROUTER_API_KEY` via `with-test-keys --only openrouter` | capture | **present** (name only) | — | same, `openrouter.key` |
| Network to api.openai.com / openrouter.ai | capture only | not probed (no live calls in research) | — | `check` never needs it |

Keys being present does **not** authorize the capture: D-16 gates it on the orchestrator relaying Yahir's OK. `./gradlew check` stays key-free and offline.

**Missing dependencies with no fallback:** none for `check`.

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 4.13.2 + kotlinx-coroutines-test 1.11.0 + legacy `okhttp3.mockwebserver` |
| Config file | `providers/build.gradle.kts` (matrix legs, live task), `config/detekt/detekt.yml` |
| Quick run command | `./gradlew :providers:test --tests '*<Class>' --offline -q` |
| Matrix spot check | `./gradlew :providers:testOkhttp521 :providers:testOkhttp550 --tests '*<Class>' --offline -q` |
| Per-task hygiene | `./gradlew :providers:detekt :providers:scanBannedConstructs --offline -q` |
| Full suite | `./gradlew check --offline` |
| Phase gate adds | `scripts/review-api-surface.sh --expect-sealed-complete`, `scripts/verify-negative-controls.sh`, `scripts/verify-repo-hygiene.sh`, `scripts/verify-api-dump.sh` |

### Phase Requirements -> Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| PROV-08 | nested tool shape, sorted tools, key order, `reasoning_effort` and token param per rules row, forced `tool_choice`, `require_parameters` only on OpenRouter forced calls, no `stream` | unit (golden request) | `... --tests '*ChatEncoderTest'` | Wave 0 |
| PROV-08 | capability table rows, OpenRouter normalization (`openai/gpt-5.4-mini`, `anthropic/claude-sonnet-5.5` → forced=false + caching NONE, `:variant` strip, app override wins), GPT-6 Astra / 6.1 Sol `supportsTools=false` on OpenAI only | unit | `... --tests '*ChatModelsTest'` | Wave 0 |
| PROV-08 | GPT-6 Astra with tools through the real pipeline → `ModelUnsupported`, `capability_refused`, `server.requestCount == 0` | integration | `... --tests '*ChatTransportTest'` | Wave 0 |
| PROV-08 | decode precedence rows 1-10, arguments `""`/invalid/non-object/object, refusal, content_filter, length with truncated args, `finish_reason:"stop"` + tool_calls, OpenRouter raw finish value, usage mapping | unit (golden response, derivatives) | `... --tests '*ChatDecoderTest'` | Wave 0 |
| PROV-08 | 200-envelope (OpenRouter 429 inside 200, `choices: []` + error, OpenAI-shaped error on 200), `finish_reason:"error"` + `native_finish_reason` | unit + MockWebServer | `... --tests '*ChatErrorMapTest'` | Wave 0 |
| PROV-12 | extended detector: `anyOf`, `oneOf`, `allOf`, `$defs`, `definitions`, `additionalProperties`-schema, `prefixItems` hold optionals; nullable-required does not | unit | `... --tests '*OptionalPropertiesTest'` | Wave 0 (new; Phase 4 has none for the walker alone, check `AnthropicStrictTest`) |
| PROV-12 | eligibility, strict authority (`strict=true` + optionals → non-strict), strip data, names-not-stripped, SB-style EDIT stays non-strict, CT-style log_food stays strict | unit | `... --tests '*ChatStrictTest'` | Wave 0 |
| PROV-12 | per-vendor absent-optional contract: omitted optional absent from decoded args (OpenAI and OpenRouter, `Parameterized`) | integration | `... --tests '*ChatAbsentOptionalTest'` | Wave 0 |
| PROV-09 | retry matrix, quota-429 not retried, budget ≤ 2 requests, 200-envelope retry, no silent OkHttp replay | MockWebServer | `... --tests '*ChatRetryTest'` | Wave 0 |
| PROV-09 | retried call: one tool execution, one commit | pipeline integration | `... --tests '*ChatPipelineRetryTest'` | Wave 0 |
| PROV-11 / PROV-13 | derived client (reuse `CleanClientTest`), `body?.string()`, default 60 s, Timeout/Network mapping, cancellation closes the call | MockWebServer | `... --tests '*ChatTimeoutTest' --tests '*ChatCancellationTest'` | Wave 0 |
| Pitfall 9 | header-unsafe key → `Auth`, zero requests | MockWebServer | `... --tests '*ChatMalformedKeyTest'` | Wave 0 |
| TEL-04 | canary through pipeline + Chat transport (Authorization header, transcript, tool args, tool_result, `error.message`, `metadata.raw`, refusal text, `reasoning`) | integration (all legs) | `... --tests '*ChatCanaryTest'` | Wave 0 |
| TEL-01 | identical `Usage` and ceiling comparison on Anthropic, OpenAI, OpenRouter | integration | `... --tests '*TokenParityTest'` | Wave 0 |
| BLD-06 | everything above on 4.12.0 / 5.2.1 / 5.5.0 | matrix | `./gradlew :providers:test :providers:testOkhttp521 :providers:testOkhttp550 --offline` | automatic |
| hygiene | detekt zero, scanner, bytecode 55, explicit API | gradle | `./gradlew :providers:detekt :providers:scanBannedConstructs :providers:verifyBytecodeLevel :providers:verifyExplicitApiStrict --offline -q` | yes |
| hygiene | public API shape of the new classes (no enum, data, static field, default-argument stub) | unit | `./gradlew :providers:test --tests '*ProvidersApiShapeTest' --offline -q` | yes (extend sweep list) |

### Sampling Rate
- **Per task commit:** the quick command for the touched class plus the per-task hygiene command.
- **Per wave merge:** `./gradlew :providers:check :core:check --offline` (includes all three legs).
- **Phase gate:** `./gradlew check --offline` green plus the four scripts, before `/gsd-verify-work`.

### Wave 0 Gaps
- [ ] `schema/OptionalPropertiesTest.kt`, `chat/ChatStrictTest.kt`, `chat/ChatModelsTest.kt` (Wave 1 files)
- [ ] `chat/ChatFixtures.kt` (top-level JSON builders for OpenAI and OpenRouter answers; distinct names from `anthropic/AnthropicFixtures.kt` live in a different package, so no clash)
- [ ] `chat/ChatEncoderTest.kt`, `ChatDecoderTest.kt`, `ChatErrorMapTest.kt`
- [ ] `chat/ChatTransportTest.kt`, `ChatRetryTest.kt`, `ChatPipelineRetryTest.kt`, `ChatTimeoutTest.kt`, `ChatCancellationTest.kt`, `ChatMalformedKeyTest.kt`
- [ ] `chat/ChatAbsentOptionalTest.kt`, `chat/ChatCanaryTest.kt`, `parity/TokenParityTest.kt`
- [ ] `ProvidersApiShapeTest` sweep list gains `ChatCompletionsProvider` + observer types
- [ ] golden resources + manifest (derivatives first; real captures replace/add when the capture runs). A real-golden replay test must **fail** when a manifest entry marked `captured` has no file; it must not silently skip.
- No new framework install.

### Live capture (opt-in, outside `check`, KEY-GATED per D-16)
Convention copied from `liveAnthropicCapture`: Gradle `Test` task `liveChatCompletionsCapture` (group verification, includes only `*ChatCompletionsLiveCaptureTest`, `outputs.upToDateWhen { false }`, `onlyIf { VAE_LIVE_CHAT == "1" }`, not a dependency of `check`); `*Live*` is already excluded from `test` and both legs. Run only after the orchestrator relays Yahir's OK:
`with-test-keys --only openai,openrouter -- env VAE_LIVE_CHAT=1 ./gradlew :providers:liveChatCompletionsCapture --offline --no-daemon --console=plain`.
The test builds each request with the internal encoder and POSTs with a plain `OkHttpClient` (no production seam), writes the raw body under `providers/build/` and the sanitized body to `providers/src/test/resources/golden/chat/...`, prints only status, error type, header presence and usage numbers, and counts requests against a hard ceiling of **12 HTTP requests** (OpenAI ≤ 6, OpenRouter ≤ 6; no retries, so each call is exactly one request). Synthetic tools only: a `log_food`-shaped strict-eligible tool (array of items with `name`, `quantity` with `exclusiveMinimum`, nullable `unit`, `confidence` with `minimum`/`maximum`, `target_date` nullable; all required, `additionalProperties:false`) and an `edit_list_card`-shaped tool with optional and nested-optional properties (non-strict). Models: `gpt-5.4-mini` and `openai/gpt-5.4-mini` (pricing $0.75 / $0.075 cached / $4.50 per 1M tokens [CITED: developers.openai.com/api/docs/models/gpt-5.4-mini]); expected spend well under USD 0.05.

| # | Vendor | Call | Proves |
|---|---|---|---|
| C1 | OpenAI | forced `log_food`, strict, `reasoning_effort:"none"`, `parallel_tool_calls:false`, system prompt ≥ 1 100 tokens | A1, A2, A11, numeric bounds accepted, usage shape |
| C2 | OpenAI | identical repeat | `cached_tokens > 0` (automatic caching), prefix byte-stability |
| C3 | OpenAI | forced EDIT-shaped, non-strict, optional omitted | real absent-optional response (D-14) |
| C4 | OpenAI | `Auto` request that should not call a tool | real `stop` + prose shape |
| C5 | OpenAI | invalid key probe | 401 shape + `x-request-id` presence |
| R1 | OpenRouter | forced `log_food`, strict, `require_parameters`, all of C1's parameters (with `parallel_tool_calls:false` on) | A3, A4, finish_reason from the routed upstream, `gen-` id |
| R2 | OpenRouter | forced EDIT-shaped non-strict | absent-optional via the router |
| R3 | OpenRouter | repeat of R1 | cached-token fields and whether `prompt_tokens` includes them (A5) |
| R4 | OpenRouter | invalid key probe | 401 shape |
| R5 (optional) | OpenRouter | one cheap tool-capable non-OpenAI route chosen at capture time | forced-tool `finish_reason` on another upstream, strict omitted |

Non-capturable cases stay documented derivatives in the manifest (provenance line each): refusal, `content_filter`, `length` with truncated arguments, `finish_reason:"error"` with and without `native_finish_reason`, OpenRouter 200 `{error:{code:429,...}}`, `choices: []` + error, `arguments:""`, invalid/array/scalar `arguments`, `tool_calls` with `stop`, raw upstream finish value, `custom` tool-call type, quota 429, 402, GPT-6 Astra 400 text, OpenRouter 404 `No endpoints found`. Phase 10 Gate-2 carries: low-credit mapping (OpenAI 429 `insufficient_quota`, OpenRouter 402) and accepted key character set.

## Security Domain

### Applicable ASVS Categories (level 1, security_enforcement enabled)

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | partial (API key handling) | key only in `Credential` + the `Authorization` header; vetted by `isHeaderSafe`; never stringified; redirects off, authenticator and proxy authenticator replaced by `NONE` |
| V3 Session Management | no | — |
| V4 Access Control | no | provider/key isolation by the Phase 3 router |
| V5 Input Validation | yes | tolerant JSON decode, object checks on `arguments`, `safeToken`/`safeRequestId` before `FailureDetails`/`ModelResponse` |
| V6 Cryptography | no | TLS by OkHttp; no custom crypto |
| V7 Error handling/logging | yes | no logging interceptors; failures carry status + type + id only; observer carries ids and counts only; scanner bans `println`/`Log` |
| V9 Communications | yes | HTTPS-only base URL constants; override internal, loopback-only |

### Known Threat Patterns for this stack

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Key echoed via OkHttp `IllegalArgumentException` for a header with a newline | Information disclosure | pre-request `isHeaderSafe` → `Auth`, zero requests |
| Provider error echoes the prompt (`error.message`, OpenRouter `metadata.raw`) | Information disclosure | parse-and-discard; canary echo legs incl. a 200 envelope |
| Model reasoning / refusal text copied into traces | Information disclosure | refusal text never in parts; `NativeReplay.toString` prints provider/model only; canary includes both |
| Hostile `error.metadata.error_type` or `id` breaks `FailureDetails` | Tampering / DoS | `safeToken` / `safeRequestId`, null on mismatch |
| 200-with-error retry storm | Denial of service | one transient retry (≤ 2 requests), retry-after cap, engine deadline |
| Committed golden leaks identifiers | Information disclosure | sanitizer, no headers, secret-scan hook, synthetic prompts only |
| Cleartext base URL | Information disclosure | HTTPS-only constants; loopback exception is internal for tests |

## Sources

### Primary (HIGH confidence)
- Repo files read this session: `providers/build.gradle.kts`, `providers/src/main/.../{http,schema,anthropic}/*.kt`, `providers/src/test/.../{AnthropicLiveCaptureTest,AnthropicPipelineRetryTest,AnthropicFixtures,ProvidersApiShapeTest}.kt`, `config/detekt/detekt.yml`, `scripts/verify-repo-hygiene.sh`, `core/.../{ProviderId,telemetry/Usage,telemetry/TraceCode,failure/FailureReason,provider/*,transcript/*,strategy/ToolSpec,pipeline/PipelineBuilder}.kt`, Phase 4 RESEARCH/PATTERNS/SUMMARY/REVIEW-FIX/VERIFICATION, 05-CONTEXT, DECISION-MAP Phase 5, REQUIREMENTS, ROADMAP, research/{SUMMARY,FEATURES,ARCHITECTURE,PITFALLS}.md; CT sources `BaseAiProvider.kt`, `OpenAiLogFoodRequestBuilder.kt`, `OpenRouterModelCatalog.kt`, `OpenRouterProvider.kt`, `OpenAiModelAllowlist.kt`.
- Executed this session: `./gradlew :providers:test :providers:testOkhttp521 :providers:testOkhttp550 :providers:detekt :providers:scanBannedConstructs :providers:verifyBytecodeLevel :providers:verifyExplicitApiStrict :providers:verifyOkHttpCompileFloor --offline -q` (green; 144 tests per leg from the JUnit XML); `with-test-keys --only openai,openrouter -- bash -c '<name-presence check>'` (names only).
- https://developers.openai.com/api/docs/guides/structured-outputs.md (Supported schemas: types, supported properties, limits 5 000 properties / 10 levels / 120 000 chars / 1 000 enum values, `additionalProperties:false`, unsupported composition list, fine-tuned-only exclusions)
- https://developers.openai.com/api/docs/guides/function-calling.md (strict requirements; "Chat Completions requests remain non-strict by default"; `parallel_tool_calls` false → zero or one tool; GPT-6 Astra and GPT-6.1 Sol require Responses for tool calling)
- https://developers.openai.com/api/docs/guides/latest-model.md (GPT-6 model ids; `none` unsupported on Astra/6.1 Sol; Sol/Luna function calling in Chat only with `reasoning_effort:"none"`)
- `openai/openai-node` master: `src/lib/transform.ts` (unsupported strict keyword set; last commit 2026-09-16), `src/resources/chat/completions/completions.ts` (finish_reason enum, `max_completion_tokens`/`max_tokens` deprecation, `reasoning_effort`, named tool choice, `parallel_tool_calls`, message `refusal`, `tool_calls`), `src/resources/completions.ts` (`CompletionTokensDetails`, `PromptTokensDetails.cache_write_tokens`/`cached_tokens`), `src/resources/shared.ts` (`FunctionDefinition`, `ReasoningEffort`), README (`x-request-id`).
- https://developers.openai.com/api/reference/overview (`x-request-id` header), https://developers.openai.com/api/docs/guides/error-codes (401/429 quota vs rate/500/503), https://developers.openai.com/api/docs/models/gpt-5.4-mini (default effort `none`, 400k context, pricing).

### Secondary (MEDIUM confidence)
- OpenRouter docs: api-reference/errors (error shape, 200-with-error, status table, mid-stream shape), guides/best-practices/reasoning-tokens (`reasoning.effort` values incl. `none`, `reasoning_details` preservation rule), api/api-reference/chat/send-chat-completion-request (`reasoning_effort` shorthand, `max_completion_tokens`, `max_tokens` deprecated + minimum 16, `parallel_tool_calls`, `provider.require_parameters`, `session_id`, status codes 400/401/402/403/404/408/413/422/429/500/502/503/524/529), guides/best-practices/prompt-caching (`prompt_tokens` includes cached, `cached_tokens`, `cache_write_tokens`), guides/routing/provider-selection (`require_parameters`), model pages `openrouter.ai/openai/gpt-5.4-mini`, `.../gpt-6-astra/llms.txt`, `.../gpt-6.1-sol/llms.txt`, `.../anthropic/claude-sonnet-5.5/llms.txt` (advertised parameters).
- Issue reports: OpenAI community 1386454 (exact gpt-5.6-sol 400 text), 1378362 (none + max_completion_tokens bug, fixed 2026-04-20), 731488 (forced tool → `stop`); langchain #40346 (exact gpt-6-astra 400 text), #40364 and gbrain #5473 (OpenRouter 200 error bodies), zed #36094 (OpenRouter 404 text), pydantic-ai #2844 (OpenRouter `error` finish_reason), pydantic-ai #7315 (minLength/maxLength accepted).

### Tertiary (LOW confidence)
- Web-search summaries without a fetched primary page (OpenRouter `:variant` suffixes, quota 429 code variants, "not a chat model" 404 text). All carry an Assumptions Log row. The GSD `classify-confidence` seam rates `websearch`/`webfetch` providers LOW by default; the tags above reflect that the substantive OpenAI claims were read from the vendor's own `.md` docs and SDK source, which is why they are marked HIGH.

## Recommended Plan Decomposition

Five waves, at most two plans per wave, disjoint files inside a wave. **Same-module contention:** every plan edits `:providers`, so (a) run Gradle only task-scoped per plan (`:providers:test --tests ...`, `:providers:detekt`) and the full `check` only at wave merge; (b) never run two Gradle invocations in one working tree at the same time (they block on the project lock and on the `providers/build` outputs); parallel plans must use separate worktrees, as Phase 4 did; (c) `providers/build.gradle.kts` is edited by exactly one plan (the live task, last wave); (d) `http/SafeFields.kt` and `anthropic/AnthropicTransport.kt` are edited only by plan 05-01. Full `check` is cheap when cached but a cold run executes three test legs; budget accordingly.

| Plan | Wave | Goal | Files | Depends | Covers |
|---|---|---|---|---|---|
| 05-01 | 1 | Tracer: shared prerequisites. Extend `hasOptionalProperties` (+`OptionalPropertiesTest`); `ChatStrict.kt` eligibility + strip + tests; move `isHeaderSafe` into `http/SafeFields.kt` (+`SafeFieldsTest` case) and delete the private copy | `schema/OptionalProperties.kt`, `chat/ChatStrict.kt`, `http/SafeFields.kt`, `anthropic/AnthropicTransport.kt`, tests | — | PROV-12 core |
| 05-02 | 1 | Tracer: `ChatVendor`, `OpenAiModelRules`, `ChatModels` (normalization, caching, GPT-6 rows) + `ChatModelsTest` | `chat/ChatVendor.kt`, `OpenAiModelRules.kt`, `ChatModels.kt`, tests | — | PROV-08 (capability table) |
| 05-03 | 2 | Encoder + message encoder + golden request tests (derived goldens committed) | `chat/ChatEncoder.kt`, `ChatMessageEncoder.kt`, `ChatFixtures.kt`, tests, `golden/chat/*` request files | 05-01, 05-02 | PROV-08 SC1, PROV-12 encoder leg |
| 05-04 | 2 | Decoder + usage + error map + 200 envelope + derivative response goldens | `chat/ChatDecoder.kt`, `ChatErrors.kt`, tests, `golden/chat/*` response derivatives | 05-02 (`ChatVendor`) | PROV-08 SC2, PROV-12 decode leg |
| 05-05 | 3 | Tracer through the pipeline: `ChatCompletionsProvider` (`openAi{}` / `openRouter{}`), `ChatTransport`, observer, GPT-6 pre-network refusal test, end-to-end `ChatTransportTest` | `chat/ChatCompletionsProvider.kt`, `ChatTransport.kt`, `ChatCompletionsAttemptObserver.kt`, tests, `ProvidersApiShapeTest` list | 05-03, 05-04 | PROV-08 SC3, PROV-09/11/13 wiring |
| 05-06 | 4 | Contract suites on the real provider: retry matrix + pipeline no-duplicate commit, timeout/cancel/malformed-key, per-vendor absent-optional (`Parameterized`), canary | `ChatRetryTest`, `ChatPipelineRetryTest`, `ChatTimeoutTest`, `ChatCancellationTest`, `ChatMalformedKeyTest`, `ChatAbsentOptionalTest`, `ChatCanaryTest` | 05-05 | PROV-09/11/12/13, TEL-04, SC4-SC5 |
| 05-07 | 4 | TEL-01 parity test across Anthropic, OpenAI, OpenRouter | `parity/TokenParityTest.kt` | 05-05 | TEL-01 |
| 05-08 | 5 | **KEY-GATED, `autonomous: false`:** `liveChatCompletionsCapture` task + `ChatCompletionsLiveCaptureTest` + sanitizer + committed real goldens + manifest update + evidence file; phase gate scripts | `providers/build.gradle.kts`, live test, `golden/chat/*`, `.planning/phases/05-.../evidence/` | 05-06, 05-07, orchestrator OK | D-12/D-14, VER-03 input |

Everything except 05-08 executes with no key and no network. If the OK never arrives, 05-08's task registration and the capture test (skipping via `Assume`) can still land with the manifest honestly marking real goldens "not captured"; SC2's "recorded, sanitized real response bodies" is then satisfied only after the capture, which the verifier should be told up front.

## Project Constraints (from CLAUDE.md)

Extracted from `./.claude/CLAUDE.md` and the global `~/.claude/CLAUDE.md`; the planner must verify compliance:
- `:core` depends on no other hub and has no HTTP dependency; `:providers` may depend on `:core`, never the reverse; OkHttp **compile floor 4.12.0** (plain `api`, no `strictly`/BOM), CI green on 4.12.x and 5.x (A1).
- Domain-free: the library names no note/card/food; model ids appear only in `:providers`; synthetic `log_food`/EDIT shapes live in tests only.
- Quality: detekt zero baseline (plain `detekt` task only), explicit API strict, JVM 11 bytecode with `-Xjdk-release=11`; catch specific exceptions only (the observer wrapper is the single justified broad catch, without `@Suppress`).
- Public API strictly additive once tagged; no `api.txt` before the cut; no data classes, enums or public `const val`; no default arguments on public constructors (builder pattern, internal constructors).
- Secrets: API keys, transcripts, tool args/results never reach logs, telemetry, exceptions or `toString()`.
- Forbidden: `org.jetbrains.kotlin.android` plugin, Hilt/KSP/`javax.inject`, `logging-interceptor`, Anthropic/OpenAI Java SDKs, LangChain4j, Ktor, `org.json`, `mockwebserver3`, Robolectric/MockK/Turbine, `okhttp3.internal.*`, `okhttp-coroutines`, `android.util.Log`, `println` and `runCatching` in main source.
- Process: contract changes only via §10 amendments through the control plane; tag cuts are agent-owned under A12; work goes through GSD commands.
- Global: never hand Yahir a bare `localhost` URL; live tests use `with-test-keys -- <cmd>`, never read key files, never paste keys; no device step in this phase.

## Metadata

**Confidence breakdown:**
- Standard stack / reuse of Phase 4 plumbing: HIGH - read the code, ran the gate.
- OpenAI wire, strict subset, GPT-6 rules, usage fields: HIGH - vendor docs `.md` and SDK source fetched today.
- OpenRouter behavior (200 envelope shape, parameters, `require_parameters`): MEDIUM - docs plus field reports, no live call.
- Architecture / plan decomposition: HIGH for `:core` seams, MEDIUM for new `chat/` internals (design).
- Pitfalls: HIGH where tied to a quoted error text, MEDIUM otherwise.

**Research date:** 2026-10-01
**Valid until:** 2026-10-08 (OpenAI model ids and Chat-vs-Responses rules changed several times in 2026; re-read `latest-model.md` and `structured-outputs.md` at execution)
