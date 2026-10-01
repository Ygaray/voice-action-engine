# Phase 4: Anthropic Transport & OkHttp Matrix - Research

**Researched:** 2026-09-30
**Domain:** Kotlin/JVM OkHttp transport for the Anthropic Messages API (cache-correct encoding, typed failures, cancellation, retry, secret redaction) plus the OkHttp 4.12 / 5.2.1 / 5.5.0 test matrix inside `./gradlew check`
**Confidence:** HIGH for repo state, matrix plumbing, OkHttp retry/redirect semantics and Anthropic error/caching facts (read from source and official docs this session). MEDIUM for three behaviors that must be proven empirically at execution (flagged in the Assumptions Log). No live Anthropic call was made.

<user_constraints>
## User Constraints (from 04-CONTEXT.md)

### Locked Decisions
(Copied verbatim from the `## Implementation Decisions` block of 04-CONTEXT.md. The `## Runtime Decisions` block at the bottom of that file refreshes four of them against Phases 1-3; the refreshed text is reproduced after the list and overrides the provisional text above it.)

- **D-01 [floor-guard]:** Add an explicit compile-floor assertion task under check (placed with the Phase 1 matrix plumbing if Phase 1 is still open, else Phase 4); a catalog bump to 5.x must fail the build _(source: ai-auto)_ _(provisional — refresh at execution; depends on Phase 1)_
- **D-02 [io-dispatch]:** Inside the transport on an injectable IO dispatcher defaulting to Dispatchers.IO; an OkHttp Response never escapes (avoids NetworkOnMainThreadException from viewModelScope callers) _(source: ai-auto)_
- **D-03 [timeouts]:** newBuilder() from an optional app client, clear interceptors() and networkInterceptors(), set both callTimeout and readTimeout from per-provider/strategy config (default 60 s; SB parity) _(source: ai-auto)_
- **D-04 [timeout-map]:** OkHttp's own timeouts only (no coroutine withTimeout in the transport, which would surface as cancellation); InterruptedIOException → TIMEOUT, other IOException → NETWORK _(source: ai-auto)_
- **D-05 [retry-rules]:** One transient retry per logical call, shared with the forced-tool reshape (≤3 HTTP requests); never retry other 4xx; retry lives below the AiProvider seam and each retry is a trace attempt; verify whether retryOnConnectionFailure(false) is needed _(source: ai-auto)_
- **D-06 [forced-400]:** Three-condition match (forced + 400 + message mentions tool_choice), body parsed then discarded; retry with auto + (strict only without optionals) + instruction in the user turn; no memo in v1.0 (verified table covers known models) _(source: ai-auto)_
- **D-07 [no-tool-call]:** Transport returns typed NoToolCall (refusal stays REFUSAL); SingleShot (Phase 7) maps NoToolCall → Escalate _(source: ai-auto)_
- **D-08 [error-map]:** Status-first table (401/403 AUTH, 402 BILLING, 429 RATE_LIMIT, 529/503 OVERLOADED, 408/504/InterruptedIO TIMEOUT, IO NETWORK, bad 2xx MALFORMED_RESPONSE, non-object input MALFORMED_TOOL_ARGS) plus a ModelNotFound leaf (404) so "bad model id" is distinguishable; rest → Other; always httpStatus + error.type + request-id, never body _(source: ai-auto)_ _(provisional — refresh at execution; depends on Phase 2)_
- **D-09 [canary]:** Capture + explicit toString checks + error-body echo leg, in :providers/src/test so it runs on all three OkHttp legs; never stringify OkHttp Request/Headers/Response (they print x-api-key in clear on both lines) _(source: ai-auto)_
- **D-10 [test-fakes]:** java-test-fixtures on :core with the variant explicitly excluded from publication (verified by inspecting the published module/POM), falling back to copies if JitPack still exposes it; confirm "unpublished" satisfies the orchestrator's ruling _(source: ai-auto)_ _(provisional — refresh at execution; depends on Phase 1)_
- **D-11 [encoder-scope]:** Encode tool_result + is_error in Phase 4 (needed for TEL-04 SC5); Phase 8 owns round-trip conformance, batching semantics, verbatim replay and goldens _(source: ai-auto)_ _(provisional — refresh at execution; depends on Phase 3)_
- **D-12 [ext-anthropic-errors]:** Needs external research: exact error.type/message for forced tool_choice on the four models; retry-after presence/units on 429/529 and whether spend-cap 429 is distinguishable; request-id header on error responses; strict + auto acceptance on the four models (final proof = Phase 10 live smoke) _(source: ai-auto)_
- **D-13 [ext-okhttp]:** Needs external research: legacy mockwebserver surface and okhttp-jvm variant attributes on 5.5.0 (not cached locally); which failures retryOnConnectionFailure silently retries after a POST is sent _(source: ai-auto)_

**Runtime Decisions (refresh, override the provisional text above):**
- **floor-guard:** ALREADY SATISFIED by Phase 1: the `verifyOkHttpCompileFloor` task (gradle/invariants.gradle.kts:391) checks compileClasspath/testCompileClasspath, plus providers OkHttpVersionGuardTest and the libs.versions.toml A1 comment. Phase 4 adds NO new floor task. It must keep verifyOkHttpCompileFloor under check while it adds the 5.x matrix Test tasks, and the matrix legs must use their own resolvable configurations, never the compile classpath.
- **error-map:** CONFIRMED vs Phase 2: every leaf already exists in core/failure/FailureReason.kt. Phase 4 builds only the Anthropic status→leaf mapping table (401/403→Auth, 402→Billing, 429→RateLimited, 529/503→Overloaded, 408/504/InterruptedIOException→Timeout, IOException→Network, bad 2xx→MalformedResponse, non-object input→MalformedToolArgs, 404→ModelNotFound, rest→HttpError/Other). It always carries httpStatus + error.type + request-id via FailureDetails, never the body. Add no new FailureReason leaves unless one is truly missing.
- **test-fakes:** ALREADY SATISFIED by Phase 3: :core applies `java-test-fixtures`, and FakeAiProvider lives in core/src/testFixtures. Both testFixtures variants are skipped from the published component, and `verifyNoTestFixturesPublished` runs under check. :providers already uses testImplementation(testFixtures(project(":core"))). Phase 4 reuses these fixtures and needs no copies.
- **encoder-scope:** CONFIRMED vs Phase 3: core/transcript/Message.kt has ToolResultsMessage(List<ToolResult>) with ToolResult.isError. Phase 4 encodes tool_result + is_error into the Anthropic wire format. Phase 8 owns round-trip conformance, batching semantics, verbatim replay and goldens.

### Claude's Discretion
"Anything not listed above follows `.planning/research/SUMMARY.md` and the phase's own research; Source `ai-auto` decisions took research's recommendation (orchestrator accepted the auto-resolved remainder)."

### Deferred Ideas (OUT OF SCOPE)
"See REQUIREMENTS.md v2 / LATER items." (Streaming, moving/message cache breakpoint, OpenAI/OpenRouter transports, Phase 8 transcript conformance/goldens, SingleShot, AgenticLoop, `:keystore`, `:sample` live smoke are all other phases.)
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| PROV-04 | `AnthropicProvider`: tools + system, exactly one `cache_control: ephemeral` on last system block, none on messages, `anthropic-version 2023-06-01`, HTTPS-only fixed base URL (test-overridable), cancellation-safe `Call.await()` that closes late responses | Encoder layout, `Call.await` with `resume(value){close}`, internal base-URL seam (Architecture Patterns 1, 2, 5) |
| PROV-05 | Tools + system prefix byte-identical across calls (tools sorted by name, no timestamps) | Deterministic encoder rules, prefix-bytes test (Pattern 1, Validation map) |
| PROV-06 | Language/date/transcript only in the messages portion | Transport sees only `ModelRequest`; reshape instruction goes to the user turn (Pattern 3) |
| PROV-07 | Per-model forced-tool capability, app-overridable, verified entries for the four ids; reactive 400 fallback; `NoToolCall` | **Needs a new `ModelCapabilities` field in `:core`** (Pitfall 1), verified-table values, three-condition 400 matcher, reshape (Pattern 3) |
| PROV-09 | Transient retry at most once; no duplicate tool execution or `CommitSink` commit | Retry matrix, silent-POST-retry finding + one-shot body, pipeline-level no-duplicate test (Pattern 4, Pitfall 2) |
| PROV-11 | No logging interceptors; `body?.string()` | Clean-client derivation, floor-safe API list (Pattern 2, Code Examples) |
| PROV-13 | Per-provider timeout, default >= 60 s; engine timeouts map to TIMEOUT | Config + OkHttp-only timeouts + mapping (Pattern 2, Open Question 2) |
| BLD-06 | A1: compile 4.12.0, same compiled tests green on 4.12.0 / 5.2.1 / 5.5.0 | Phase 1 plumbing already works (verified by running it); Phase 4 adds guard strengthening only (Matrix section) |
| TEL-04 | Canary never in trace/events/toString/failures; failures carry status + error.type only | Canary design incl. positive controls and echo leg (Pattern 6) |
| PROV-12 (Anthropic leg) | Strict only when no optional properties; omitted optional arrives absent | Strict-eligibility predicate (Pattern 3); the per-provider contract test for PROV-12 itself is Phase 5 |
</phase_requirements>

## Summary

Phase 1 already built the whole OkHttp matrix; Phase 4 does not need new Gradle machinery for it. I ran `./gradlew :providers:test :providers:testOkhttp521 :providers:testOkhttp550 --offline --rerun-tasks` this session and it printed `OKHTTP_RUNTIME=4.12.0`, `5.2.1` and `5.5.0` with matching `expected=` values and `BUILD SUCCESSFUL`; `./gradlew check --offline` is green on the current tree. Every new test placed in `providers/src/test` automatically runs on all three legs because each leg re-runs `sourceSets["test"].output`. The only matrix work is (a) strengthening the guard so it also proves the legacy `mockwebserver` jar swapped with OkHttp, and (b) writing transport tests that use only the legacy `okhttp3.mockwebserver` surface (verified identical on 4.12.0 and 5.5.0 for everything this phase needs).

Three findings change the plan relative to the decision map. (1) **`ModelCapabilities` has no forced-tool field**, so PROV-07's "per-model capability table the app can override" needs one additive field in `:core` (builder-based, no `api.txt` yet, so non-breaking). (2) **OkHttp silently re-sends a POST** after the request was sent for most `IOException`s and for HTTP 408 whenever the body is not one-shot (the default for string bodies), and it **forwards the `x-api-key` header on a cross-host redirect** (only `Authorization` is stripped). The right fix for the first is a one-shot request body (keeps pre-send route failover, which `retryOnConnectionFailure(false)` would lose); the fix for the second is `followRedirects(false)` + `followSslRedirects(false)`. (3) **Retries and the forced-tool reshape happen inside the provider, below the seam, and the seam has no way to write a trace code** (`AiProvider.complete` returns only `ModelResult`), so D-05's "each retry is a trace attempt" cannot be met without either a small provider-local observer or an additive core change; see Open Question 1.

The Anthropic facts are now documented: forced `tool_choice` on `claude-opus-5-5`, `claude-sonnet-5-5`, `claude-fable-5-1`, `claude-mythos-5-1` returns HTTP 400 `invalid_request_error` with message `tool_choice: type "tool" and "any" are not supported for this model.`; every response carries a `request-id` header and error bodies carry `request_id`; `retry-after` is seconds on rate-limit 429s but a tier spend-cap 429 has **no** `retry-after` and carries `error.details.error_code = "enforced_spend_limit_reached"` (so it must not be retried); and a user-set spend limit is a **400** `invalid_request_error`, not a 429.

**Primary recommendation:** Build one internal `anthropic` encoder/decoder/error-mapper trio plus a generic internal `http` package (clean client, `Call.await`, one-shot body, retry loop) in `:providers`; add `supportsForcedToolChoice` to `ModelCapabilities`; put every new test in `providers/src/test` so the three legs run it unchanged; keep live capture opt-in via a dedicated Gradle task that `check` never runs.

## Architectural Responsibility Map

This is a JVM library, not a tiered web app; the "tiers" are the library layers.

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Forced-tool capability fact (data) | `:core` `ModelCapabilities` | `:providers` default table | App-overridable via `PipelineBuilder.capabilities(...)`; core stays model-id-free (NoHardCodedConstantsTest scans `core/src/main`) |
| Verified Anthropic model table (ids, min-cache, forced-tool) | `:providers` | app override via core | Model ids are allowed in `:providers` only |
| Wire encoding / decoding | `:providers` (internal) | — | Provider-specific JSON; never public |
| HTTP, retry, timeouts, cancellation | `:providers` internal `http` package | Phase 5 reuses | Roadmap: Phase 4 owns shared transport plumbing |
| Failure typing | `:providers` produces `ModelResult.Failure` | `:core` `FailureReason`/`FailureDetails` types | Leaves already exist; no new leaves needed |
| Trace/event emission of retries | `:core` recorder (not reachable from provider) | provider-local observer | See Open Question 1 |
| No-duplicate-commit guarantee | structural (retry below the seam, tools never run in transport) | pipeline-level test | Retry cannot reach `CommitCoordinator` by construction |
| Secret redaction | every type's `toString` + failure construction | canary test | Key lives only in `Credential` and one request header |

## Standard Stack

### Core (all already in the repo; Phase 4 adds NO new external package)
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| `com.squareup.okhttp3:okhttp` | 4.12.0 compile floor (`api`) | HTTP transport | A1; `libs.versions.toml` `okhttp = "4.12.0"` [VERIFIED: gradle/libs.versions.toml] |
| `com.squareup.okhttp3:mockwebserver` (legacy `okhttp3.mockwebserver`) | follows the leg (4.12.0 / 5.2.1 / 5.5.0) | JVM transport tests | Exists on every leg; `mockwebserver3` is 5.x-only [VERIFIED: javap of cached jars] |
| `org.jetbrains.kotlinx:kotlinx-serialization-json` | 1.11.0 (transitive `api` from `:core`) | `JsonObject` bodies, internal `@Serializable` DTOs | `:providers` already applies `plugin.serialization` |
| `org.jetbrains.kotlinx:kotlinx-coroutines-core` | 1.11.0 (transitive `api`) | `suspendCancellableCoroutine`, `withContext` | Same pin as consumers |
| `org.jetbrains.kotlinx:kotlinx-coroutines-test` | 1.11.0 | `runTest` | already `testImplementation` |
| `junit:junit` | 4.13.2 | tests | legacy mockwebserver depends on it |

### Package Legitimacy Audit
No external package is added in this phase (every dependency above is already declared in `gradle/libs.versions.toml` and resolved in `~/.gradle` caches; OkHttp 4.12.0, 5.2.1, 5.5.0 and mockwebserver of each were found there). The `gsd-tools package-legitimacy` seam was therefore not run.

| Package | Registry | Age | Downloads | Source Repo | Verdict | Disposition |
|---------|----------|-----|-----------|-------------|---------|-------------|
| (none added) | — | — | — | — | — | n/a |

**Packages removed due to [SLOP] verdict:** none
**Packages flagged as suspicious [SUS]:** none

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| One-shot request body | `retryOnConnectionFailure(false)` | Simpler, but also disables OkHttp's pre-send route fallback (next IP / IPv6->IPv4 after a connect failure), which `recover()` gates on the same flag; keep as the fallback if the one-shot approach misbehaves on a leg |
| Hand-rolled `Call.await` | `okhttp-coroutines` `executeAsync()` | 5.x-only; forbidden by detekt `ForbiddenImport` and the A1 floor |
| Raw OkHttp | Anthropic Java SDK | Rejected in STACK.md (Jackson, reflection, churn) |

**Installation:** none. **Version verification:** `./gradlew :providers:dependencies --offline` already resolves 4.12.0 on `compileClasspath`; the legs resolve 5.2.1 and 5.5.0 (jar paths printed by the guard test above).

## Current Repo State (what Phases 1-3 built, with the concrete hooks Phase 4 uses)

All items below were read in full this session [VERIFIED: file reads].

**`providers/` today**
- `providers/build.gradle.kts`: plugins `kotlin.jvm`, `kotlin.serialization`, `maven-publish`, detekt, metalava; `explicitApi()`; JVM 11 + `-Xjdk-release=11`; `api(project(":core"))`, `api(libs.okhttp)`, `testImplementation(libs.junit, libs.coroutines.test, libs.okhttp.mockwebserver, testFixtures(project(":core")))`.
- Matrix, verbatim: `val okhttpLegs = mapOf("Okhttp521" to "5.2.1", "Okhttp550" to "5.5.0")` (line 55); each leg creates `test${legName}RuntimeClasspath` extending `testRuntimeClasspath` with explicit JVM attributes (`USAGE=JAVA_RUNTIME`, `CATEGORY=LIBRARY`, `LIBRARY_ELEMENTS=JAR`, `BUNDLING=EXTERNAL`, `TARGET_JVM_ENVIRONMENT=STANDARD_JVM`) and `resolutionStrategy.eachDependency { if (requested.group == "com.squareup.okhttp3" && (requested.name == "okhttp" || requested.name == "mockwebserver")) useVersion(legVersion) }`; the Test task uses `testClassesDirs = sourceSets["test"].output.classesDirs` and `classpath = sourceSets["test"].output + sourceSets["main"].output + legClasspath`, sets `systemProperty("expected.okhttp", ...)`, and `check` depends on it. Negative-control lever `-PvaeExpectedOkhttp=<v>`.
- `providers/src/main/.../ProvidersModule.kt` is an `internal object ProvidersModule` marker (hygiene needs >= 1 main source; real code replaces the need for it).
- `providers/src/test/.../OkHttpVersionGuardTest.kt`: reads `expected.okhttp`, reflects `Class.forName("okhttp3.OkHttp").getField("VERSION")` (reflective because the constant is inlined at compile time), prints `OKHTTP_RUNTIME=...`, plus a MockWebServer round trip using `response.body?.string()`.
- `gradle/invariants.gradle.kts:390-391` `val floor = "4.12.0"` / `tasks.register("verifyOkHttpCompileFloor")` over `compileClasspath` + `testCompileClasspath` for `okhttp`, `okhttp-jvm`, `mockwebserver`; wired into `check`. Applied scripts: `scanBannedConstructs` (bans `runCatching`, `println`, `System.out/err`, `printStackTrace`, DI annotations, `okhttp3.internal`, `mockwebserver3`, `okhttp3.coroutines`, `android.util.Log`, planning ids in comments), `verifyBytecodeLevel` (major 55), `verifyExplicitApiStrict`, `verifyModuleGraph`, `verifyNoDiArtifacts`.
- `config/detekt/detekt.yml`: `buildUponDefaultConfig`, `maxIssues: 0`, **no baseline**; `TooManyFunctions.thresholdInClasses: 12`; `LongParameterList.constructorThreshold: 8` with `ignoreDefaultParameters`; `ForbiddenImport` list includes `okhttp3.internal.*`, `mockwebserver3.*`, `okhttp3.coroutines.*`. detekt `TooGenericExceptionCaught`/`SwallowedException`/`MagicNumber` are on by default; the only `@Suppress` in the repo is in `core/.../internal/Guarded.kt`, so **`:providers` code must catch specific exception types** and let anything else reach `RoutedModel`'s guard (which turns it into `FailureReason.Unexpected(errorClass)`).
- `gradle.properties`: `org.gradle.caching=true`, `org.gradle.configuration-cache=false`. Root `build.gradle.kts` applies detekt `source.setFrom("src/main/kotlin","src/test/kotlin","src/testFixtures/kotlin")` to every module (a new source set directory would NOT be linted unless added there).
- No `api.txt` exists (pre-release); `scripts/verify-repo-hygiene.sh` forbids `*api.txt` and `*baseline*.xml` before the cut. `scripts/review-api-surface.sh` runs `:core:apiDump` in an isolated copy and fails on a new sealed type outside the seven allowed, `copy(`/`componentN(`, enums, or public static fields; it inspects `:core` only, but the same shape rules (no data classes, no enums, no public `const val`) should be followed in `:providers` public API.

**`:core` seam (Phase 2/3), exact signatures**
- `AiProvider`: `public val id: ProviderId`; `public val requiresCredential: Boolean get() = true`; `public fun capabilities(model: String): ModelCapabilities = ModelCapabilities.UNKNOWN`; `public suspend fun complete(call: ProviderRequest): ModelResult`.
- `ProviderRequest(model: String, request: ModelRequest, credential: Credential?, capabilities: ModelCapabilities)` (`toString` prints counts only). `Credential(provider, apiKey)`; `toString` = `Credential(provider=$provider)`.
- `ModelResult` is an open class: `Success(response: ModelResponse)` and `Failure(reason: FailureReason, details: FailureDetails?)` (+ secondary `Failure(reason)`).
- `FailureDetails(httpStatus: Int?, providerErrorType: String?, requestId: String?)` and `ModelResponse(message, stopReason, usage, requestId)` **throw `IllegalArgumentException`** when `providerErrorType` is not `[A-Za-z0-9_.:-]{1,64}` or `requestId` is not `[A-Za-z0-9_.:-]{1,128}` (`isSafeToken`/`isRequestId` are `internal` to `:core`; the transport must replicate the regexes and **drop** a non-conforming server value instead of constructing with it).
- `FailureReason` leaves (verbatim codes): `Auth "auth"`, `Billing "billing"`, `RateLimited "rate_limited"`, `Overloaded "overloaded"`, `Timeout "timeout"`, `Network "network"`, `MalformedResponse "malformed_response"`, `MalformedToolArgs "malformed_tool_args"`, `Refusal "refusal"`, `MaxTokens "max_tokens"`, `NoToolCall "no_tool_call"`, `ModelUnsupported`, `ModelNotFound "model_not_found"`, `PauseTurn`, `ContextWindowExceeded`, `UnknownStop`, `HttpError "http_error"`, `Other(code)`, plus router leaves. All no-arg classes with value `equals` (construct with `FailureReason.Auth()`).
- Transcript: `ModelRequest(system, messages, tools, toolChoice, maxTokens, cache)`, `ToolChoice.Auto()` / `ToolChoice.Required(toolName)`, `CacheDirective(staticPrefix, conversationTail)`, `UserMessage(text)`, `AssistantMessage(parts, nativeReplay)` with `nativeFor(provider, model)`, `ToolResultsMessage(results)` (non-empty, distinct ids), `ToolResult(callId, content, isError)`, `AssistantPart.Text(text)` / `AssistantPart.ToolCall(id, name, arguments: JsonObject)`, `NativeReplay(provider, model, raw: JsonElement)`, `ModelResponse`, `StopReason` constants `END_TURN "end_turn"`, `TOOL_USE "tool_use"`, `MAX_TOKENS "max_tokens"`, `REFUSAL "refusal"`, `PAUSE_TURN "pause_turn"`, `CONTEXT_WINDOW_EXCEEDED "context_window_exceeded"`, `OTHER "other"` (the API's wire value for the last-but-one is `model_context_window_exceeded`, [CITED: platform.claude.com docs via claude-api skill, shared/model-migration.md]; the mapper must translate it).
- `ToolSpec(name, description, inputSchema: JsonObject, mutating=false, terminal=false, strict: Boolean?=null)`.
- `Usage(inputUncached, cacheRead, cacheWrite, output)` Longs, non-negative; KDoc already states the Anthropic mapping (`inputUncached = input_tokens`, `cacheRead = cache_read_input_tokens`, `cacheWrite = cache_creation_input_tokens`, `output = output_tokens`).
- `ModelCapabilities` (internal ctor, builder DSL `ModelCapabilities { ... }`): fields `supportsTools: Boolean = true`, `caching: CachingMode = NONE`, `minCacheablePrefixTokens: Int? = null`, `charsPerToken: Double = 4.0`. **No forced-tool field.** `CachingMode` constants `EXPLICIT_BREAKPOINTS`, `AUTOMATIC`, `NONE`. `PipelineBuilder.capabilities(provider, model) { ... }` patches exact ids; provider defaults come from `AiProvider.capabilities(model)` via `ModelCapabilityTable`.
- Routing: `RoutedModel.complete` refuses with `ModelUnsupported` when `tools` non-empty and `!supportsTools`, builds `ProviderRequest(binding.model, request, binding.credential, binding.capabilities)`, calls the provider inside `guarded(...)` (cancellation propagates; other exceptions become `FailureReason.Unexpected`), then records a `TurnRecord` and runs the `CacheNotEngaged` check from `Usage`.
- Test fixtures (`core/src/testFixtures/.../core/testing/`): `FakeAiProvider`, `FakeMutation`, `RecordingCommitSink`, `RecordingEventListener`, `RecordingSink`, `ScriptedGate` (`admitAll()`), `ScriptedStrategy`, `ScriptedCredentialSource.keys(...)`, `ScriptedSelectionSource.fixed(...)`, `NoNetworkGuard`, `FakeClock`. `commandPipeline { tier(..); provider(..); gate = ..; commitSink = ..; providerSelection = ..; credentials = ..; listener = .. }` is the public composition entry; `CommandSession.model()` returns the `BoundModel` a strategy calls.
- `TierPolicy` already has `commandTimeoutMillis` (engine deadline via `withTimeoutOrNull`, recorded as `engine_timeout`).

**Port sources (read-only; reuse the idea, not the code)**
- CalTracker `app/src/main/java/com/caltracker/app/ai/AnthropicProvider.kt`: `await()` bridge (its `onResponse` does `continuation.resume(response)` with no close if the continuation was already cancelled = the late-response leak PROV-04 forbids), `PROD_BASE_URL = "https://api.anthropic.com/"`, `ANTHROPIC_VERSION = "2023-06-01"`, `.header("x-api-key", apiKey)`, `resp.body?.string()`, branches on `stop_reason` first. `AnthropicKnownTool400Ids.kt`: `setOf("claude-opus-5-5", "claude-sonnet-5-5", "claude-fable-5-1", "claude-mythos-5-1")`. `LogFoodRequestBuilder.kt` (lines ~104-130, ~200-215): reshaped encoding = `tool_choice {"type":"auto"}`, `"strict": true` on the tool, and `"\n\nCall the $TOOL_NAME tool with your result."` appended to the **user** text, never to system.
- SecondBrain `core/agent/AnthropicAgentLoop.kt` `buildRequestBody` (line ~330): `model`, `max_tokens`, `tools`, `tool_choice {"type":"auto"}`, `system` = one text block with `"cache_control": {"type":"ephemeral"}`, `messages`; `toolResultBlock` = `{type: tool_result, tool_use_id, content, is_error: true only when error}`; usage DTO with the four Anthropic token fields; `AgentModule.provideAnthropicOkHttpClient` = `callTimeout(60s)`, `connectTimeout(10s)`, `readTimeout(60s)`, zero interceptors. SB's `response.body.string()` does not compile at the 4.12 floor (use `body?.string()`).

## OkHttp matrix: what Phase 4 actually has to do

1. **Keep** `verifyOkHttpCompileFloor`, the legs, and the `check` wiring untouched (Runtime Decision). New transport tests go in `providers/src/test`; nothing else is needed for them to run on all three legs. [VERIFIED: ran all three legs this session]
2. **Strengthen the guard test** (the Phase 1 guard proves only the OkHttp jar). Add assertions that, on each leg, the legacy `okhttp3.mockwebserver.MockWebServer` class comes from a `mockwebserver-<legVersion>.jar` and that `okhttp3.mockwebserver` is the legacy artifact (not `mockwebserver3`), reading the version the same way (jar file name via `protectionDomain.codeSource.location`). This satisfies the roadmap's "okhttp and mockwebserver swapped together" proof. On 5.x the legacy artifact's POM also pulls `mockwebserver3:<ver>`, `okhttp-jvm:<ver>`, `okio-jvm:3.18.1`, `junit:4.13.2`, `kotlin-stdlib:2.1.21` (5.5.0) [VERIFIED: cached `mockwebserver-5.5.0.pom`].
3. **Variant facts for 5.5.0** [VERIFIED: cached `okhttp-5.5.0.module`, `okhttp-jvm-5.5.0.module`]: the `okhttp` module metadata redirects (`available-at`) `jvmApiElements-published`/`jvmRuntimeElements-published` (attributes `org.gradle.jvm.environment=standard-jvm`, `org.gradle.libraryelements=jar`, `org.jetbrains.kotlin.platform.type=jvm`) to `okhttp-jvm:5.5.0`, and the `androidApiElements-published`/`androidRuntimeElements-published` variants (`org.gradle.jvm.environment=android`, `libraryelements=aar`) to `okhttp-android:5.5.0`. The explicit attributes in the existing leg configurations select the `jvm` variant; the `okhttp-android` variant is not exercisable on the JVM and is covered by `:sample` Gate-1 on the TESTER (Phase 10), as the roadmap says. (`okhttp-android` 5.2.1 is cached; 5.5.0 is not, and is not needed.)
4. **Test-code API discipline.** Legacy mockwebserver differences between 4.12.0 and 5.5.0 (javap diff this session): 5.x drops duplex/informational-response members and `RecordedRequest`'s extra ctor, and adds `MockWebServer.getDelegate()`/`SocketPolicy.getEntries()`. Everything this phase needs is identical on both: `MockWebServer.enqueue/start/url/takeRequest()/takeRequest(long,TimeUnit)/requestCount/setDispatcher/close`, `MockResponse.setResponseCode/setHeader/addHeader/setBody(String)/setSocketPolicy/setBodyDelay(long,TimeUnit)/setHeadersDelay(long,TimeUnit)`, `RecordedRequest.getHeader/getBody (okio.Buffer)/getPath/getMethod`, and `SocketPolicy` constants `NO_RESPONSE`, `DISCONNECT_AFTER_REQUEST`, `DISCONNECT_AT_START`, `DISCONNECT_DURING_RESPONSE_BODY`, `STALL_SOCKET_AT_START`, `KEEP_OPEN`. Do not use duplex APIs, `QueueDispatcher` subclassing, `mockwebserver3`, `okhttp3.internal.*`, or 5.x-only OkHttp APIs (`Request(url=...)`, `Call.tag(KClass)`, `executeAsync`).
5. **Do not subclass `okhttp3.Call` casually** in tests compiled at 4.12 (5.x may add members; an un-invoked abstract member is harmless, an invoked one is `AbstractMethodError`); if a fake `Call` is needed for the late-response test (below), implement only and call only `enqueue/cancel/isCanceled/request/execute/isExecuted/timeout/clone`.
6. **Optionally** make the legs' test tasks `filter.excludeTestsMatching("*Live*")` (see Live capture).

## Architecture Patterns

### System Architecture Diagram

```
 strategy ──model().complete(ModelRequest)──▶ RoutedModel (core) ──guarded──▶ AnthropicProvider.complete(ProviderRequest)
                                                                                   │ (withContext(ioDispatcher))
                                                                                   ▼
                              ProviderRequest ─▶ Encoder ─▶ EncodedRequest{bytes, reshaped?}
                                                                                   │
                       ┌──────────────── retry loop (≤3 HTTP requests total) ◀─────┘
                       │   attempt: build Request (x-api-key, anthropic-version, one-shot body)
                       │            client.newCall(req).await()   [cancel ⇒ Call.cancel(), late Response closed]
                       │            read body?.string() on IO, close Response
                       ▼
                 classify ──2xx──▶ Decoder ─▶ Success(ModelResponse{parts, stop, Usage, requestId, NativeReplay})
                       │                      └─▶ Required choice & no tool_use & end_turn ─▶ Failure(NoToolCall)
                       │                      └─▶ tool_use.input not an object ─▶ Failure(MalformedToolArgs)
                       │                      └─▶ unparseable 2xx ─▶ Failure(MalformedResponse)
                       ├──400 + forced + msg mentions tool_choice ──▶ ONE reshape (auto + strict? + user-turn instruction) ─▶ loop
                       ├──408/429(retry-after≤cap, not spend-cap)/500/502/503/504/529 or IOException/timeout ─▶ ONE transient retry (shared budget) ─▶ loop
                       └──other non-2xx ─▶ ErrorMapper ─▶ Failure(reason, FailureDetails(status, error.type, request-id)); body discarded
```

### Recommended Project Structure
```
providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/
├── http/                    # shared with Phase 5 (internal)
│   ├── CallAwait.kt         # Call.await(): cancels the call, closes a late Response
│   ├── CleanClient.kt       # derive from app client or default; strip interceptors; timeouts; no redirects
│   ├── OneShotBody.kt       # RequestBody with isOneShot() = true over a ByteArray
│   ├── RetryPolicy.kt       # budget, retry-after parsing, injectable sleep
│   └── SafeFields.kt        # replicas of core's isSafeToken/isRequestId; header+status extraction
└── anthropic/
    ├── AnthropicProvider.kt # public class (builder-style, no default ctor args), AiProvider impl
    ├── AnthropicModels.kt   # internal verified table -> ModelCapabilities
    ├── AnthropicEncoder.kt  # internal pure fun: ProviderRequest (+reshape flag) -> bytes
    ├── AnthropicDecoder.kt  # internal: body -> ModelResult
    ├── AnthropicErrors.kt   # internal: status/type/message -> reason + details; forced-400 matcher
    └── AnthropicTransport.kt# internal: attempt loop (takes Call.Factory + sleep + base URL)
providers/src/test/kotlin/.../providers/
├── OkHttpVersionGuardTest.kt (extend)         ├── anthropic/AnthropicEncoderTest.kt
├── anthropic/AnthropicTransportTest.kt        ├── anthropic/AnthropicRetryTest.kt
├── anthropic/AnthropicForcedToolTest.kt       ├── http/CallAwaitTest.kt
├── anthropic/AnthropicCanaryTest.kt           └── anthropic/AnthropicLiveCaptureTest.kt (opt-in)
```
Follow the repo's public-API style: the public type has an `internal` constructor and a builder (`AnthropicProvider { httpClient = ...; callTimeoutMillis = ... }` via `operator fun invoke`), exactly like `ModelCapabilities { }`, because Phase 3 froze "no default arguments on public constructors" so later fields stay additive [VERIFIED: 03-01 decision in STATE.md and ModelCapabilities.kt]. Base-URL override and the Call.Factory/sleep/dispatcher seams are `internal` (tests share the module), so the public API has **no** base-URL override.

### Pattern 1: Deterministic, cache-correct encoding (PROV-04/05/06)
**What:** One pure function `ProviderRequest -> ByteArray`. Layout (key order fixed): `model`, `max_tokens`, `tools` (sorted by `name`, each `{name, description, input_schema[, strict]}`), `tool_choice`, `system` (single text block carrying the only `cache_control`), `messages`.
**Rules:**
- `tools`: stable sort by `name`; `input_schema` emitted exactly as the app built it (do NOT re-sort schema keys: core documents "Key order is part of the contract" for `clarificationSchema`). No timestamps, ids or per-request values anywhere in tools/system.
- `system`: if `system.isNotEmpty()` and `request.cache.staticPrefix` and `capabilities.caching == CachingMode.EXPLICIT_BREAKPOINTS`: `[{"type":"text","text":"(the system prompt)","cache_control":{"type":"ephemeral"}}]`. Otherwise the same block without `cache_control`. **Never** a `cache_control` on a message block or a tool (`CacheDirective.conversationTail` is ignored in v1.0; moving tail breakpoint is deferred to v1.x). Blank system: Anthropic rejects empty text blocks, so omit `system`; then (only if caching applies) put the single `cache_control` on the last (sorted) tool as the nearest equivalent and document it [ASSUMED edge, see A6].
- Language, date, transcript live only in `UserMessage` text (the strategy builds them); the transport cannot interpolate them, so PROV-06's test is: two `ModelRequest`s with identical `system`/`tools` and different user messages (`"en"` vs `"es"` framing, different date, different transcript) encode to the same `tools`+`system` substring. The prefix test should compare the **serialized `tools` array and `system` array text**, not the whole body.
- `tool_choice`: `Auto` -> `{"type":"auto"}`; `Required(name)` on a model with forced-tool support -> `{"type":"tool","name":name}`; `Required` on a model flagged unsupported (or after the reactive 400) -> `{"type":"auto"}` (see Pattern 3). Changing `tool_choice` invalidates cached message blocks only; tools and system stay cached [CITED: platform.claude.com/docs/en/agents-and-tools/tool-use/define-tools].
- Messages: `UserMessage` -> `{"role":"user","content":[{"type":"text","text":...}]}`; `AssistantMessage` -> if `nativeFor(ProviderId.ANTHROPIC, model) != null` send that raw content array verbatim, else rebuild `text`/`tool_use` blocks from parts; `ToolResultsMessage` -> **one** user message whose content is all `tool_result` blocks `{type, tool_use_id, content, is_error: true only when true}`, tool_result blocks first.
- Minimum cacheable prefix is silent when short (no error, `cache_creation_input_tokens: 0`); the router's `CacheNotEngaged` diagnostic already handles that from `Usage` and `minCacheablePrefixTokens`.
**Example:** see Code Examples.

### Pattern 2: Clean client, I/O dispatch, cancellation, timeouts (PROV-04/11/13, D-02/03/04)
**Derive the client** once at provider build: `(appClient ?: OkHttpClient()).newBuilder()` then
`interceptors().clear()`, `networkInterceptors().clear()`, `eventListener(EventListener.NONE)` (an app `EventListener` sees request headers including `x-api-key`), `followRedirects(false)`, `followSslRedirects(false)`, `callTimeout(cfg.callTimeout)`, `readTimeout(cfg.readTimeout)`, `connectTimeout(10 s)`. `newBuilder()` keeps the app's connection pool and dispatcher. Leave `retryOnConnectionFailure` at its default `true` (see Pitfall 2). Defaults: call/read 60 s (SB parity, PROV-13).
**Redirects (security finding):** on a cross-host redirect OkHttp removes only `Authorization`, so `x-api-key` would be forwarded to the redirect target [CITED: OkHttp 4.12.0 `RetryAndFollowUpInterceptor` source: `if (!userResponse.request.url.canReuseConnectionFor(url)) { requestBuilder.removeHeader("Authorization") }`]. The fixed host never redirects a Messages call; disabling redirects is free insurance, and a 3xx then falls into the "other non-2xx" path (`HttpError`).
**I/O and body reading:** the whole attempt runs inside `withContext(ioDispatcher)` (default `Dispatchers.IO`, injectable); `await()` returns a `Response`, the body is read with `response.body?.string()` (null -> typed `MalformedResponse`) and the `Response` is closed with `use {}` in the same scope, so no `Response` ever escapes.
**Timeouts:** OkHttp's own only. `InterruptedIOException` (includes `SocketTimeoutException` and the call-timeout "timeout" exception) -> `FailureReason.Timeout()`; any other `IOException` -> `FailureReason.Network()`. No `withTimeout` in the transport. If the coroutine is no longer active when an `IOException("Canceled")` surfaces, rethrow `CancellationException` (do not report Network).
**Cancellation:** `suspendCancellableCoroutine` + `Call.enqueue`; `invokeOnCancellation { call.cancel() }`; and the success callback resumes with an `onCancellation` handler that closes the `Response` when the continuation was cancelled before delivery (kotlinx.coroutines 1.11.0 exposes `resume(value, (Throwable)->Unit)` [VERIFIED: javap of `CancellableContinuation` in the cached 1.11.0 jar]; it is annotated experimental in the 1.x API [ASSUMED: needs `@OptIn(ExperimentalCoroutinesApi::class)`, A1]). Retry sleeps use `delay` (cancellable).

### Pattern 3: Capability table, forced-tool reshape, NoToolCall, strict (PROV-07/12)
**New core field (required):** add `supportsForcedToolChoice: Boolean = true` to `ModelCapabilities` (constructor param, `Builder` var, `toBuilder`, `equals/hashCode/toString`, KDoc, `ModelCapabilityTableTest` + `ApiShapeTest` untouched). Constraints from `NoHardCodedConstantsTest` (scans `core/src/main`): the new KDoc/identifiers must not contain model-family words (`claude`, `sonnet`, `opus`, `haiku`, `gpt`, ...) or vendor-slash ids. Name the field neutrally.
**Verified table in `:providers` (`AnthropicProvider.capabilities(model)`, exact ids, no prefixes):**

| Model id | forced tool | caching | minCacheablePrefixTokens | Source |
|---|---|---|---|---|
| `claude-opus-5-5` | false | EXPLICIT_BREAKPOINTS | 512 | [CITED: platform.claude.com/docs/en/api/errors#forced-tool-use-not-supported; claude-api skill prompt-caching minimums] |
| `claude-sonnet-5-5` | false | EXPLICIT_BREAKPOINTS | 512 | same |
| `claude-fable-5-1` | false | EXPLICIT_BREAKPOINTS | 512 | same |
| `claude-mythos-5-1` | false | EXPLICIT_BREAKPOINTS | 512 | same |
| `claude-haiku-4-5` | true | EXPLICIT_BREAKPOINTS | 4096 | claude-api skill table (Haiku 4.5 = 4096); forced tool supported per CT note and docs (only manual extended thinking blocks it) |
| any other id | true (the reactive 400 covers drift) | EXPLICIT_BREAKPOINTS | null (silences the diagnostic) | design; `UNKNOWN` default is `caching = NONE`, so the Anthropic default must override it |

Per-field app overrides already work through `PipelineBuilder.capabilities(...)`. The 512-token minimum is flagged "check the prompt caching docs before relying on its value" in the skill table; leave `minCacheablePrefixTokens` at the table value but treat it as correctable by the Phase 10 live smoke.
**Reshape (applies when `toolChoice is Required` and either the table says unsupported or the reactive 400 fired):** send `tool_choice {"type":"auto"}`; per tool `strict` per the eligibility predicate below; append `"\n\nCall the <name> tool with your result."` as an extra text block at the end of the **last user message** (never the system block, which would break the cached prefix). Document the instruction text as a library constant in `:providers`.
**Reactive 400 matcher (three conditions, D-06):** request was forced (`Required` and not already reshaped) AND status 400 AND parsed `error.message` contains `tool_choice`. Parse, decide, discard the body. Documented message: `tool_choice: type "tool" and "any" are not supported for this model.` with `error.type` `invalid_request_error` [CITED: platform.claude.com/docs/en/api/errors]. Other 400s (including the user-set spend-limit 400 and ZDR 400s) must not match.
**Strict eligibility (PROV-12 Anthropic leg):** `strict: true` only when `ToolSpec.strict == true`, or when `ToolSpec.strict == null` in the reshape path AND the schema satisfies the documented grammar subset: every object has `additionalProperties: false` and `required` listing **all** properties (no optional properties), no `minimum/maximum/multipleOf/minLength/maxLength`, no recursion, `minItems` only 0/1, at most 20 strict tools and 24 optional parameters per request [CITED: platform.claude.com/docs/en/build-with-claude/structured-outputs "JSON Schema limitations"]. `ToolSpec.strict == false` -> no strict key. If the predicate fails, send non-strict plus the instruction. An omitted optional is then genuinely absent because the transport never fills defaults. The cross-provider "omitted optional arrives absent" contract test is Phase 5's; Phase 4 supplies the Anthropic leg (decode must pass `input` through untouched, never default missing keys).
**NoToolCall:** if the request's `toolChoice` is `Required` (native or reshaped), the HTTP-200 response has **no** `tool_use` block and `stop_reason == "end_turn"` -> `ModelResult.Failure(FailureReason.NoToolCall())`. `stop_reason == "refusal"` stays a `Success` with `StopReason.REFUSAL` (strategy maps to `Refusal`), `max_tokens` stays `Success` with `StopReason.MAX_TOKENS`, `pause_turn` -> `StopReason.PAUSE_TURN`. For `Auto` requests an end_turn text reply is normal and is a `Success`. [ASSUMED shape for refusal/max_tokens: A11 - Phase 7 consumes it.]
**Non-object tool input** (`tool_use.input` not a `JsonObject`) -> `Failure(MalformedToolArgs())`.

### Pattern 4: Retry budget, retry-after, silent POST retries (PROV-09, D-05, D-13)
**Budget:** one counter per logical `complete` call: at most 3 HTTP requests = initial + (reshape or transient retry, whichever fires, at most one of each). A reshape does not consume the transient retry and vice versa, but the sum is capped at 3 requests. Never retry 4xx other than 408/429 (below) and the forced-400 reshape; never retry a successful-but-malformed 2xx.
**Retry set (transient):** `IOException` (including call/read timeouts), HTTP 408, 429, 500, 502, 503, 504, 529.
**429 handling:** `retry-after` is **seconds** [CITED: platform.claude.com/docs/en/api/rate-limits "number of seconds"]; parse as a non-negative integer, ignore anything else. Do NOT retry a 429 when (a) there is no `retry-after` header AND the parsed error carries `error.details.error_code == "enforced_spend_limit_reached"` (tier spend cap: "has no `retry-after` header and keeps failing until access resumes") - map this one to `FailureReason.Billing()` rather than `RateLimited` so it is loud and non-transient [CITED: rate-limits#reaching-your-spend-cap; mapping choice is design], or (b) `retry-after` exceeds a configurable cap (recommended default 5 s) - fail fast with `RateLimited`. 529/5xx usually carry no `retry-after`; use a small fixed backoff (recommended default 500 ms), also capped. The wait goes through an injectable `suspend (Long) -> Unit` (default `delay`) so tests assert the requested wait without sleeping.
**Worst case:** 3 requests x 60 s call timeout = 180 s; the engine's `TierPolicy.commandTimeoutMillis` is the overall bound and cancels the transport cleanly.
**Silent OkHttp re-sends (answer to D-05/D-13).** With the default `retryOnConnectionFailure=true`, `RetryAndFollowUpInterceptor.recover()` returns true (re-sending the POST) when: not a one-shot body, or the failure happened before any send; the exception is not `ProtocolException`, not a non-socket `InterruptedIOException`, not a certificate `SSLHandshakeException`, not `SSLPeerUnverifiedException`; and `call.retryAfterFailure()` has another route or a same-route retry. A `SocketTimeoutException` after the send started is **not** retried. HTTP 408 is re-sent once when the body is not one-shot and `Retry-After` is absent/0; a 503 is re-sent only with `Retry-After: 0` [CITED: OkHttp 4.12.0 and 5.5.0 `RetryAndFollowUpInterceptor` sources, fetched this session]. `RouteException` (connect-phase) is always passed with `requestSendStarted = false`, which is what gives route failover (next IP, IPv6 -> IPv4). A string `RequestBody` is not one-shot by default.
**Recommendation (one-shot body):** build the request body from a custom `RequestBody` whose `isOneShot()` returns `true` (bytes precomputed, `contentLength()` known). Effects: connect-phase failures still fail over between routes (good on dual-stack mobile networks); any failure after bytes may have been sent surfaces to our loop instead of being silently replayed, so the single transient retry is the only replay and `requestCount <= 3` is provable; 408 and 307/308 are not replayed. Fallback if a leg misbehaves: `retryOnConnectionFailure(false)` (simpler, loses connect-phase route fallback; 503-with-`Retry-After: 0` and 421 are still replayed because those branches do not consult the flag).
**Proof tests:** MockWebServer `SocketPolicy.DISCONNECT_AFTER_REQUEST` on the first response then a 200: assert `server.requestCount == 2` on every leg. `[ASSUMED, A3]` that this is stable on 4.12.0, 5.2.1 and 5.5.0; verify at execution and keep the assertion if it is.
**No duplicate tool execution or commit (SC3):** by construction the transport has no reference to `CommitCoordinator`, tools or the sink. The proof test runs the real `AnthropicProvider` against MockWebServer (`529` then a `tool_use` 200) inside `commandPipeline { tier(ScriptedStrategy ...); provider(..); gate = ScriptedGate.admitAll(); commitSink = RecordingCommitSink(); credentials/providerSelection fixed }`; the strategy calls `session.model().complete(request)` once, then submits the returned call as a `ToolStep.Mutation(FakeMutation(...))`. Assert `server.requestCount == 2`, `FakeMutation.applyCount == 1`, `RecordingCommitSink.actions.size == 1`, and one `TurnRecord` for the call.

### Pattern 5: Error mapping (D-08) and FailureDetails hygiene
Table (status-first; details always `FailureDetails(status, errorType?, requestId?)`; body discarded after parsing):

| Condition | Reason |
|---|---|
| 401, 403 | `Auth()` |
| 402 | `Billing()` |
| 429 with `details.error_code == "enforced_spend_limit_reached"` | `Billing()` (not retried) |
| 429 otherwise | `RateLimited()` |
| 400 whose parsed message starts `You have reached your specified` (user-set spend limit) | `Billing()` (optional enhancement, message sniff, parse-and-discard, never retried) |
| 503, 529 | `Overloaded()` |
| 408, 504, `InterruptedIOException` | `Timeout()` |
| other `IOException` | `Network()` |
| 404 | `ModelNotFound()` (docs: unknown model and not-available-to-org are indistinguishable) |
| other non-2xx (400, 413, 500, 502, 3xx...) | `HttpError()` |
| 2xx unparseable / null body / missing `content` | `MalformedResponse()` |
| `tool_use.input` not an object | `MalformedToolArgs()` |

`error.type` documented values: `invalid_request_error`, `authentication_error`, `billing_error`, `permission_error`, `not_found_error`, `conflict_error`, `request_too_large`, `rate_limit_error`, `api_error`, `timeout_error`, `overloaded_error` (the set may grow) [CITED: platform.claude.com/docs/en/api/errors]. Request id: prefer the `request-id` response header (documented on every response), fall back to body `request_id`. Replicate core's regexes (`[A-Za-z0-9_.:-]{1,64}` type, `{1,128}` id) in `http/SafeFields.kt` and set the field to `null` when the server value does not match, instead of letting `FailureDetails`' `require` throw. Never read `error.message` into anything except the in-memory forced-400 and spend-limit matchers. Never put an exception message in a failure (`Network()`/`Timeout()` carry no text).

### Pattern 6: Canary (TEL-04, D-09)
In `providers/src/test` (runs on all three legs). Positive controls first (a canary test that cannot fail is worthless): the MockWebServer `RecordedRequest` must contain the key canary in header `x-api-key`, the transcript canary, a tool-argument canary (in an earlier `AssistantMessage` tool call) and a tool_result canary in the body. Then sweep `toString()` of: the `CommandOutcome`, `outcome.trace` and every attempt/turn, every event captured by `RecordingEventListener`, every `ModelResult`/`FailureReason`/`FailureDetails`, `ProviderRequest`, `Credential`, the `AnthropicProvider` instance and its builder/config, and any exception the transport or provider constructors throw. Legs: (1) success path where the server's `tool_use.input` echoes a canary (it is app data and appears only in the terminal call, whose `toString` is redacted by core); (2) HTTP 400/401/429/500 whose body is `{"type":"error","error":{"type":"invalid_request_error","message":"<canary>"},"request_id":"req_x"}` -> failure shows status + `invalid_request_error` + id only; (3) 2xx malformed body containing a canary; (4) IOException leg. Never `toString()` an OkHttp `Request`/`Headers`/`Response` in test or main code: `Headers.toString()` redacts only `Authorization`, `Cookie`, `Proxy-Authorization`, `Set-Cookie`-class names, so `x-api-key` prints in clear [ASSUMED for 5.x exactness: A2; decision map D-09 states it for both]. Use `server.takeRequest().getHeader("x-api-key")` to read a single header.

### Anti-Patterns to Avoid
- **`catch (e: Exception)` / `runCatching` in `:providers`:** detekt `TooGenericExceptionCaught` and the scanner reject them. Catch `IOException`, `SerializationException`, `IllegalArgumentException` explicitly; rethrow `CancellationException`.
- **A `withTimeout` in the transport:** surfaces as cancellation (D-04).
- **Re-sorting `input_schema` keys:** changes what the model sees and the core test pins key order.
- **Appending the reshape instruction to `system`:** busts the cached prefix.
- **Message `cache_control`, or `cache_control` when `caching != EXPLICIT_BREAKPOINTS`.**
- **Public base-URL override.**
- **Using `mockwebserver3`, `okhttp3.internal.*`, `okhttp-coroutines`, `Response.body.string()`.**
- **Putting model ids in `:core`** (fails `NoHardCodedConstantsTest`).

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| JSON wire bodies | string concatenation | `buildJsonObject`/`JsonObject` (kotlinx.serialization) | escaping, stable insertion order |
| Response DTOs | manual tree walking everywhere | small internal `@Serializable` DTOs with `ignoreUnknownKeys` + `JsonObject` for `content`/`input` | tolerant of new fields, keeps raw tool input untouched |
| HTTP | custom socket code | OkHttp `Call.enqueue` + `suspendCancellableCoroutine` | cancellation, pooling, TLS |
| Retry/backoff framework | a library | one ~40-line loop with an injectable sleep | the budget is tiny and must be provably <= 3 requests |
| Version matrix | shell loops | existing `test${Leg}` tasks | already proven this session |

**Key insight:** the risk is not code volume, it is silent behavior: OkHttp's own retry, redirects, EventListener, and `toString()` of its types. Each has a mechanical guard above.

## Common Pitfalls

### Pitfall 1: PROV-07 cannot be met with today's `ModelCapabilities`
**What goes wrong:** the verified table needs a forced-tool flag; `ModelCapabilities` only has `supportsTools`, `caching`, `minCacheablePrefixTokens`, `charsPerToken`. Encoding by a hard-coded `setOf(ids)` inside the provider would not be app-overridable (PROV-07) and would re-create CT's stale-set problem.
**How to avoid:** add the field in `:core` (Pattern 3) in the first plan, before the encoder. **Warning signs:** a `Set<String>` of model ids in the encoder.

### Pitfall 2: Silent POST replay and redirect key leak (see Patterns 2 and 4)
**Warning signs:** `requestCount` greater than the number of provider attempts; `x-api-key` appearing on a second host in a MockWebServer redirect test (write that test: 307 to a second MockWebServer, assert the second server received nothing/no key, and the result is `HttpError`).

### Pitfall 3: `FailureDetails` / `ModelResponse` throw on odd server strings
A hostile or unexpected `error.type`/`request-id` makes the constructor throw `IllegalArgumentException`, which `RoutedModel` would turn into `Unexpected` and hide the real failure. Validate with the replicated regexes and null the field.

### Pitfall 4: Stringifying OkHttp objects or exceptions
Any `"$request"`, `"$response"`, `"${e.message}"` is a leak path. Lint by review plus the canary.

### Pitfall 5: Cache silently not engaging
Short prefixes never cache (min 512 on the four new models, 4096 on Haiku 4.5), `tool_choice` changes drop only the message cache, and an unsorted tool list or a per-request value in tools/system drops everything. The prefix-bytes test and the `usage` mapping test are the guard; the live capture (opt-in) can show `cache_read_input_tokens > 0` on a second call with a >=4096-token prefix on Haiku 4.5.

### Pitfall 6: Coroutine test pitfalls
Real sockets need real time: do not wrap transport tests in `runTest` virtual time unless the dispatcher is real (`runBlocking` or `runTest` with an injected `Dispatchers.IO`); inject the `sleep` lambda so retry waits are asserted, not slept. MockWebServer timeouts: use `setHeadersDelay`/`NO_RESPONSE` with a small configured `callTimeout` (hundreds of ms).

### Pitfall 7: Trace attempts for retries (see Open Question 1)
`AiProvider.complete` has no recorder; a provider cannot add a `TierAttempt`/trace code.

### Pitfall 8: `body?.string()` returns null-able on 4.12
Treat null as `MalformedResponse`; on 5.x the `?.` is just redundant.

## Code Examples

### One-shot request body (floor-safe API: `RequestBody`, `MediaType`, okio `BufferedSink`)
```kotlin
// Source: OkHttp RequestBody.isOneShot() semantics, RetryAndFollowUpInterceptor (4.12.0 / 5.5.0)
internal class OneShotJsonBody(private val bytes: ByteArray) : RequestBody() {
    override fun contentType(): MediaType? = JSON_MEDIA_TYPE
    override fun contentLength(): Long = bytes.size.toLong()
    override fun writeTo(sink: BufferedSink) { sink.write(bytes) }
    // true: OkHttp must not silently replay this POST after bytes may have been sent; our loop owns replays.
    override fun isOneShot(): Boolean = true
}
```

### Cancellation-safe await with late-response close
```kotlin
// Source: pattern from SB/CT await() plus kotlinx.coroutines CancellableContinuation.resume(value, onCancellation)
@OptIn(ExperimentalCoroutinesApi::class)
internal suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
    enqueue(object : Callback {
        override fun onResponse(call: Call, response: Response) {
            cont.resume(response) { response.close() } // runs if cancelled before delivery: no leaked late response
        }
        override fun onFailure(call: Call, e: IOException) {
            if (!cont.isCancelled) cont.resumeWithException(e)
        }
    })
    cont.invokeOnCancellation { cancel() }
}
```

### Clean client derivation (all calls exist on 4.12 and 5.x)
```kotlin
internal fun cleanClient(app: OkHttpClient?, callMs: Long, readMs: Long): OkHttpClient =
    (app ?: OkHttpClient()).newBuilder().apply {
        interceptors().clear()
        networkInterceptors().clear()
        eventListener(EventListener.NONE)
        followRedirects(false)
        followSslRedirects(false)
        connectTimeout(CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        readTimeout(readMs, TimeUnit.MILLISECONDS)
        callTimeout(callMs, TimeUnit.MILLISECONDS)
    }.build()
```

### Request assembly
```kotlin
Request.Builder()
    .url(baseUrl.newBuilder().addPathSegments("v1/messages").build())
    .header("x-api-key", apiKey)              // the only place the key is used
    .header("anthropic-version", "2023-06-01") // [CITED: docs curl examples]
    .header("content-type", "application/json")
    .post(OneShotJsonBody(bytes))
    .build()
```

### Cache-correct system block
```kotlin
putJsonArray("system") {
    addJsonObject {
        put("type", "text"); put("text", system)
        if (cacheBreakpoint) putJsonObject("cache_control") { put("type", "ephemeral") } // exactly one, here only
    }
}
```

### Guard strengthening (reflective, version printed per leg)
```kotlin
val mws = Class.forName("okhttp3.mockwebserver.MockWebServer").protectionDomain.codeSource.location.toString()
assertTrue("mockwebserver jar must match the leg: $mws", mws.contains("mockwebserver-$expected.jar"))
```
(`expected` is the existing `expected.okhttp` property; this is the only matrix-plumbing code change, in the test, not Gradle.)

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Forced `tool_choice` everywhere | `auto` + `strict` + instruction on Opus 5.5 / Sonnet 5.5 / Fable 5.1 / Mythos 5.1 | models dated 2026-09-01 and 2026-09-22 (per CT notes) | per-model capability + reactive 400 |
| CT's hard-coded 4-id set | app-overridable capability table | this phase | PROV-07 |
| `Response.body` nullable | non-null on 5.x | OkHttp 5.0 | keep `body?.` at the 4.12 floor |
| Tier spend cap as plain 429 | 429 with `details.error_code = enforced_spend_limit_reached`, no `retry-after` | documented now | do not retry; map to Billing |

**Deprecated/outdated:** `docs.anthropic.com/en/api/errors` now 301-redirects to `platform.claude.com/docs/en/api/errors`.

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | `CancellableContinuation.resume(value, onCancellation)` requires `@OptIn(ExperimentalCoroutinesApi::class)` in 1.11.0 | Pattern 2, Code Examples | Compile warning/error only; fix by adding/removing the opt-in |
| A2 | OkHttp 5.x `Headers.toString()` redacts the same small header set as 4.12 (so `x-api-key` prints in clear) | Pattern 6 | None for correctness: the rule "never stringify OkHttp types" holds either way |
| A3 | `DISCONNECT_AFTER_REQUEST` + one-shot body yields exactly 2 server-side requests on 4.12.0, 5.2.1 and 5.5.0 | Pattern 4 | Test assertion needs adjusting per leg; fall back to `retryOnConnectionFailure(false)` and re-measure |
| A4 | A negative control with a non-one-shot body shows >2 requests (proves the mechanism) | Pattern 4 | Drop the negative control; keep the positive assertion |
| A5 | `strict: true` + `tool_choice: auto` is accepted by all four models (docs say it is Anthropic's recommended replacement) | Pattern 3 | Final proof is the Phase 10 live smoke; a 400 here would show as `HttpError` with status 400 |
| A6 | Blank-system edge: omit `system`, put the single `cache_control` on the last sorted tool | Pattern 1 | Cosmetic; no consumer sends a blank system (SB/CT both have prompts) |
| A7 | `retry-after` is an integer number of seconds on Anthropic (HTTP-date form not used) | Pattern 4 | Unparseable value is ignored and the default backoff is used |
| A8 | Hostile server `error.type` passing the safe-token regex is acceptable (Anthropic is the trusted peer) | Pattern 5 | Could switch to an allowlist of the documented types |
| A9 | Unknown Anthropic ids default to `caching = EXPLICIT_BREAKPOINTS` and `supportsForcedToolChoice = true` | Pattern 3 | A model without caching support would simply never report cache reads (diagnostic silent because min is null) |
| A10 | Haiku 4.5 min cacheable prefix 4096 and forced-tool support are taken from the claude-api skill tables and CT's notes, not re-verified live | Pattern 3 | Wrong min only affects the `CacheNotEngaged` diagnostic |
| A11 | Refusal / max_tokens / pause_turn return `Success` with the neutral `StopReason` (strategy maps), only Required-and-no-tool-call end_turn is a transport `Failure(NoToolCall)` | Pattern 3 | Phase 7 mapping adjusts; confirm in plan-check |

## Open Questions

1. **How do retries and the reshape reach the trace?** (D-05 says "each retry is a trace attempt".)
   - Known: `AiProvider.complete` returns only `ModelResult`; `TraceCode` has no retry/reshape code; `RunRecorder` is not reachable from a provider; `TierAttempt` is per tier.
   - Unclear: whether the orchestrator wants a core change.
   - Recommendation: Phase 4 ships a provider-local, ids-only `AnthropicAttemptObserver` (fun interface: attempt number, kind `initial|transient_retry|forced_tool_reshape`, HTTP status or null, no text) that the app can wire to its own telemetry and tests use to assert the sequence; raise the additive core option (a `ModelResponse`/`ModelResult` attempt count or a `TraceCode.PROVIDER_RETRIED`) with the orchestrator. Do not block the phase on it.
2. **PROV-13 says "per provider/strategy" timeouts.** `ProviderRequest` carries no timeout and a provider id can be registered once, so per-strategy timeouts are not expressible. Recommendation: provider-level config in v1.0 (builder fields `callTimeoutMillis`, `readTimeoutMillis`, default 60 000); tier-level bounds come from `TierPolicy.commandTimeoutMillis`. Flag to the orchestrator that "per strategy" is deferred.
3. **User-set spend-limit 400 and tier spend-cap 429 mapping to `Billing`.** Design choice beyond D-08's literal table; low risk, makes the cap loud and non-retried. Confirm in plan-check.
4. **Unknown-id reactive 400 costs one wasted request per command** (no memo in v1.0 by D-06). Acceptable; the verified table avoids it for known models.
5. **Live capture scope.** Instruction is Haiku 4.5, bounded. Capturing the forced-tool 400 shape needs a call to one of the four rejected models (pre-inference rejection, effectively free) which is outside "Haiku 4.5 only"; ask before including it. Otherwise the 400 shape stays [CITED] from docs.

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| JDK 17 | Gradle build | yes | openjdk 17.0.19 | — |
| Gradle wrapper 9.4.1 + offline caches (OkHttp 4.12.0/5.2.1/5.5.0, mockwebserver all three, kotlinx 1.11.0) | matrix, tests | yes (`check --offline` green) | — | — |
| `with-test-keys` | opt-in live capture | yes (`/home/yahir/.local/bin/with-test-keys`) | — | none; live capture is optional |
| `~/.config/test-keys/anthropic.key` | live capture | not probed (agents are denied that directory by policy) | — | tell Yahir the `install -m 600 /dev/stdin ...` command if the wrapper reports it missing |
| Network to api.anthropic.com | live capture only | not used in research | — | `check` never needs it |

**Missing dependencies with no fallback:** none for `check`.

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 4.13.2 + kotlinx-coroutines-test 1.11.0 + legacy `okhttp3.mockwebserver` |
| Config file | `providers/build.gradle.kts` (matrix legs), `config/detekt/detekt.yml` |
| Quick run command | `./gradlew :providers:test --tests '*<Class>' --offline -q` |
| Full suite command | `./gradlew check --offline` (runs `:providers:test`, `testOkhttp521`, `testOkhttp550`, detekt, scanner, bytecode, floor, module-graph, no-DI, core checks) |
| Matrix spot check | `./gradlew :providers:testOkhttp521 :providers:testOkhttp550 --tests '*<Class>' --offline` |
| Phase gate adds | `scripts/review-api-surface.sh --expect-sealed-complete`, `scripts/verify-negative-controls.sh`, `scripts/verify-repo-hygiene.sh`, `scripts/verify-api-dump.sh` (as in Phase 3's validation doc) |

### Phase Requirements -> Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| BLD-06 | compile floor 4.12.0 | gradle check | `./gradlew :providers:verifyOkHttpCompileFloor --offline` | yes |
| BLD-06 | each leg runs its version; mockwebserver swapped too | unit (all legs) | `./gradlew :providers:test :providers:testOkhttp521 :providers:testOkhttp550 --tests '*OkHttpVersionGuardTest' --offline` | yes (extend) |
| BLD-06 | transport tests green on all legs | unit (all legs) | `./gradlew :providers:test :providers:testOkhttp521 :providers:testOkhttp550 --offline` | Wave 0 |
| PROV-04 | exactly one `cache_control` on last system block; none on messages/tools; headers/version/URL | unit | `... --tests '*AnthropicEncoderTest'` | Wave 0 |
| PROV-04 | cancel cancels the call and closes a late response | unit | `... --tests '*CallAwaitTest' --tests '*AnthropicCancellationTest'` | Wave 0 |
| PROV-05 | encode twice -> identical tools+system bytes; tool order independent of input order | unit | `... --tests '*AnthropicEncoderTest'` | Wave 0 |
| PROV-06 | en/es, date, transcript change leaves prefix bytes unchanged; reshape instruction not in system | unit | `... --tests '*AnthropicEncoderTest'` | Wave 0 |
| PROV-07 | table values for the four ids + override; forced vs auto encoding; reactive 400 reshape (three conditions, negative cases); NoToolCall; strict predicate | unit + MockWebServer | `... --tests '*AnthropicForcedToolTest' --tests '*AnthropicModelsTest'` and `./gradlew :core:test --tests '*ModelCapabilityTableTest'` | Wave 0 |
| PROV-09 | retry matrix (which statuses retry), retry-after cap, spend-cap not retried, requestCount <= 3, no silent OkHttp replay | MockWebServer | `... --tests '*AnthropicRetryTest'` | Wave 0 |
| PROV-09 | retried call: one tool execution, one CommitSink action | pipeline integration | `... --tests '*AnthropicPipelineRetryTest'` | Wave 0 |
| PROV-11 | derived client has no interceptors/network interceptors/event listener, no redirects; 307 does not leak the key | unit + MockWebServer | `... --tests '*CleanClientTest'` | Wave 0 |
| PROV-13 | default 60 s, override honored; call timeout -> Timeout, read timeout -> Timeout, refused connect -> Network | MockWebServer | `... --tests '*AnthropicTimeoutTest'` | Wave 0 |
| PROV-12 (Anthropic) | omitted optional stays absent through decode; strict only for eligible schemas | unit | `... --tests '*AnthropicStrictTest'` | Wave 0 |
| TEL-04 | canary sweep incl. positive controls and error-body echo | integration (all legs) | `... --tests '*AnthropicCanaryTest'` | Wave 0 |
| usage | `{inputUncached, cacheRead, cacheWrite, output}` mapping incl. missing/null fields | unit | `... --tests '*AnthropicDecoderTest'` | Wave 0 |
| hygiene | no banned constructs, detekt zero, bytecode 55, explicit API | gradle | `./gradlew :providers:detekt :providers:scanBannedConstructs :providers:verifyBytecodeLevel :providers:verifyExplicitApiStrict --offline` | yes |
| core field | new capability field defaults/equality/override | unit | `./gradlew :core:test --tests '*ModelCapabilityTableTest' --tests '*NoHardCodedConstantsTest' --offline` | yes (extend) |

### Sampling Rate
- **Per task commit:** the quick command for the touched test class plus `./gradlew :providers:detekt :providers:scanBannedConstructs --offline -q`
- **Per wave merge:** `./gradlew :providers:check :core:check --offline` (includes all three legs)
- **Phase gate:** `./gradlew check --offline` green plus the four scripts above, before `/gsd-verify-work`

### Wave 0 Gaps
- [ ] `providers/src/test/.../anthropic/AnthropicEncoderTest.kt` (PROV-04/05/06)
- [ ] `.../anthropic/AnthropicTransportTest.kt`, `AnthropicRetryTest.kt`, `AnthropicForcedToolTest.kt`, `AnthropicTimeoutTest.kt`, `AnthropicDecoderTest.kt`, `AnthropicStrictTest.kt`
- [ ] `.../http/CallAwaitTest.kt`, `CleanClientTest.kt`
- [ ] `.../anthropic/AnthropicPipelineRetryTest.kt` (SC3 no-duplicate commit through `commandPipeline`)
- [ ] `.../anthropic/AnthropicCanaryTest.kt` (TEL-04)
- [ ] extend `OkHttpVersionGuardTest.kt` (mockwebserver jar version per leg)
- [ ] `ModelCapabilities` field + `ModelCapabilityTableTest` cases (core)
- [ ] test helper: tiny JSON fixture builders for Anthropic success/error bodies (private to the test source set)
- No new framework install.

### Live capture (opt-in, outside `check`)
Add a Gradle `Test` task (e.g. `liveAnthropicCapture`) that runs only classes matching `*Live*`, has `outputs.upToDateWhen { false }`, `onlyIf` the opt-in env var is set, and is not a dependency of `check`; make `test` and the `test${Leg}` tasks `filter.excludeTestsMatching("*Live*")` so keys in the environment never trigger live calls during `check` (Gradle test up-to-date/caching does not track environment variables, so a guard inside the test alone is not enough). Run as `with-test-keys --only anthropic -- env VAE_LIVE_ANTHROPIC=1 ./gradlew :providers:liveAnthropicCapture --offline`. Bound it (<= ~6 calls): Haiku 4.5 forced-tool success (usage fields, `request-id` header present, `anthropic-version` accepted), Haiku 4.5 `auto`+`strict`+instruction success, a second identical call with a >=4096-token prefix to see `cache_read_input_tokens > 0`, a 401 with a deliberately invalid key (shape of `authentication_error` + `request-id`). Print only status, `error.type`, header presence and usage numbers; never bodies or keys; do not commit raw captures. If Yahir approves, one forced call to `claude-sonnet-5-5` captures the 400 text pre-inference.

## Security Domain

### Applicable ASVS Categories (level 1, `security_enforcement: true`)

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | partial (API key handling) | key only in `Credential` + one request header; never logged/stringified; never forwarded cross-host (redirects off) |
| V3 Session Management | no | — |
| V4 Access Control | no | provider/key isolation already enforced by the router (Phase 3) |
| V5 Input Validation | yes | validate server strings before `FailureDetails`/`ModelResponse`; tolerant JSON decode; object check on `tool_use.input` |
| V6 Cryptography | no (TLS by OkHttp; no custom crypto) | HTTPS-only fixed base URL |
| V9 Communications | yes | HTTPS-only base URL (override internal, tests only); no cleartext in production path |
| V7 Error handling/logging | yes | no logging interceptors; failures carry status + type + id only; no `println`/Log (scanner enforces) |

### Known Threat Patterns for this stack

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Key echoed into logs/trace/exception via OkHttp `toString()` or exception message | Information disclosure | never stringify OkHttp types; no message in failures; canary test |
| Key forwarded to another host by a redirect | Information disclosure | `followRedirects(false)`/`followSslRedirects(false)`; 307 leak test |
| Event listener / interceptor on an app-supplied client observes the key | Information disclosure | strip interceptors, network interceptors and event listener in the derived client |
| Provider error body echoes the prompt | Information disclosure | parse-and-discard; echo-leg in canary |
| Server string breaks `FailureDetails` and hides the real error | Tampering / DoS | replicated regexes, drop on mismatch |
| Silent request replay doubles billing / duplicates side effects | Tampering | one-shot body; request-count assertions |
| Cleartext base URL | Information disclosure | HTTPS-only constant; scheme check; override is `internal` |
| Retry storm / unbounded wait | Denial of service | budget <= 3 requests, retry-after cap, engine deadline |

## Sources

### Primary (HIGH confidence)
- Repo files read this session (cited inline): `providers/build.gradle.kts`, `gradle/invariants.gradle.kts`, `gradle/libs.versions.toml`, `config/detekt/detekt.yml`, `build.gradle.kts`, `core/build.gradle.kts`, `core/src/main/.../provider/*`, `failure/*`, `transcript/*`, `telemetry/Usage.kt`, `TraceCode.kt`, `strategy/ToolSpec.kt`, `pipeline/PipelineBuilder.kt`, `internal/Guarded.kt`, `core/src/testFixtures/.../testing/*`, `core/src/test/.../NoHardCodedConstantsTest.kt`, `RedactionCanaryTest.kt`, `scripts/*`; SB `AnthropicAgentLoop.kt`, `AgentModule.kt`; CT `AnthropicProvider.kt`, `AnthropicKnownTool400Ids.kt`, `BaseAiProvider.kt`, `LogFoodRequestBuilder.kt`.
- Executed this session: `./gradlew :providers:test :providers:testOkhttp521 :providers:testOkhttp550 --offline --rerun-tasks` (three legs print their versions) and `./gradlew check --offline` (green).
- Cached Gradle artifacts: `okhttp-5.5.0.module`, `okhttp-jvm-5.5.0.module`, `mockwebserver-5.5.0.pom`, `mockwebserver-{4.12.0,5.5.0}.jar` (javap diffs), `kotlinx-coroutines-core-jvm-1.11.0.jar` (javap).
- https://platform.claude.com/docs/en/api/errors (statuses, error.type values, `request-id` header, `request_id` body field, forced-tool 400 text) 
- https://platform.claude.com/docs/en/api/rate-limits (`retry-after` seconds; spend-cap 429 with no `retry-after` and `enforced_spend_limit_reached`; user-set spend limit is a 400)
- https://platform.claude.com/docs/en/agents-and-tools/tool-use/define-tools (forced-tool restriction table, `tool_choice` change invalidates message cache only)
- https://platform.claude.com/docs/en/agents-and-tools/tool-use/strict-tool-use and /build-with-claude/structured-outputs (strict placement, schema subset, 20 strict tools / 24 optional params)
- OkHttp sources fetched via raw GitHub: `RetryAndFollowUpInterceptor.kt` at `parent-4.12.0` and `parent-5.5.0`.

### Secondary (MEDIUM confidence)
- claude-api bundled skill references (`shared/error-codes.md`, `shared/prompt-caching.md`, `shared/tool-use-concepts.md`, `shared/model-migration.md`; cached 2026-09-25): model list, cache minimums (512 / 1024 / 2048 / 4096), `model_context_window_exceeded`, forced-tool restriction list.
- `.planning/research/{SUMMARY,STACK,PITFALLS,ARCHITECTURE}.md`, `v1.0-DECISION-MAP.md`.

### Tertiary (LOW confidence)
- None relied on without a flag; see the Assumptions Log.

## Metadata

**Confidence breakdown:**
- Standard stack / matrix plumbing: HIGH - ran it.
- Architecture and API shapes: HIGH for `:core` seams (read in full); MEDIUM for the new `:providers` internal structure (design).
- Anthropic wire/error facts: HIGH for documented items, MEDIUM until the Phase 10 live smoke for strict+auto acceptance.
- OkHttp retry/redirect semantics: HIGH (source), MEDIUM for exact request counts per leg (A3).
- Pitfalls: HIGH.

**Research date:** 2026-09-30
**Valid until:** 2026-10-14 (Anthropic model/error docs are fast-moving; OkHttp/Gradle plumbing stable for 30 days)

## Project Constraints (from CLAUDE.md)

Extracted from `./.claude/CLAUDE.md` and the global `~/.claude/CLAUDE.md`; the planner must verify compliance:
- `:core` has no HTTP dependency (L7/A7); `:providers` may depend on `:core`, never the reverse; OkHttp **compile floor 4.12.0**, plain `api` (no `strictly`/BOM), CI green on 4.12.x and 5.x (A1).
- Domain-free: the library names no note/card/food; all app knowledge enters through seams. Model ids are allowed only in `:providers` (and are app-overridable).
- Quality: detekt zero baseline (`buildUponDefaultConfig`, plain `detekt` task only, never `detektMain`), explicit API strict, JVM 11 bytecode, `-Xjdk-release=11`.
- Public API strictly additive once tagged; no `api.txt` before the cut; no data classes/enums/public `const val` in public API; no default arguments on public constructors (builder pattern).
- Secrets: API keys, transcripts, tool args/results never reach logs, telemetry, exceptions or `toString()`.
- Forbidden: `org.jetbrains.kotlin.android` plugin, Hilt/KSP/`javax.inject`, `logging-interceptor`, Anthropic/OpenAI Java SDKs, Ktor, `org.json`, `mockwebserver3`, Robolectric/MockK/Turbine, `okhttp3.internal.*`, `okhttp-coroutines`, `android.util.Log`, `println`, `runCatching`, `printStackTrace`.
- Process: contract changes only via §10 amendments through the control plane; tag cuts are agent-owned under A12; GSD workflow enforcement (edit repo files only through a GSD command).
- Global: never hand Yahir a bare `localhost` URL; live tests use `with-test-keys -- <cmd>`, never read key files, never paste keys; device work rules do not apply to this phase (no device step).
