# Phase 4: Anthropic Transport & OkHttp Matrix - Pattern Map

**Mapped:** 2026-09-30
**Files analyzed:** 22 (new/modified)
**Analogs found:** 17 / 22 (the other 5 have no in-repo analog; use RESEARCH.md Code Examples)

Repo state: `:providers` has one marker file plus one test, so most analogs live in `:core` (seam shapes, builders, fixtures, canary test) and in `providers/build.gradle.kts` (matrix). All analog paths below are git-tracked in this repo. Port sources (SB `AnthropicAgentLoop.kt`, CT `AnthropicProvider.kt`) are read-only idea sources, not copy targets.

Package root: `io.github.ygaray.voiceactionengine`. Paths below abbreviate `P = providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers`, `C = core/src/main/kotlin/io/github/ygaray/voiceactionengine/core`, `PT = providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers`.

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match |
|---|---|---|---|---|
| `C/provider/ModelCapabilities.kt` (MODIFY: add `supportsForcedToolChoice`) | model | transform | itself (`supportsTools` field, lines 23-91) | exact |
| `core/src/test/.../ModelCapabilityTableTest.kt` (MODIFY, add field case) | test | transform | itself | exact |
| `P/anthropic/AnthropicProvider.kt` | provider (public) | request-response | `core/testing/FakeAiProvider.kt` + `ModelCapabilities` builder `invoke` | role-match |
| `P/anthropic/AnthropicModels.kt` | config (internal table) | transform | `ModelCapabilities { }` builder (lines 82-92) | role-match |
| `P/anthropic/AnthropicEncoder.kt` | utility (pure) | transform | SB `AnthropicAgentLoop.buildRequestBody` (read-only idea) + `Message.kt` types | partial |
| `P/anthropic/AnthropicDecoder.kt` | utility | transform | `ModelResult.kt` / `ModelResponse.kt` constructors | role-match |
| `P/anthropic/AnthropicErrors.kt` | utility | transform | `failure/FailureReason.kt` + `FailureDetails.kt` | role-match |
| `P/anthropic/AnthropicTransport.kt` | service | request-response | `core/internal/Guarded.kt` (catch discipline) | partial |
| `P/http/CallAwait.kt` | utility | event-driven (callback to suspend) | CT `AnthropicProvider.await()` (read-only, has the leak) | partial |
| `P/http/CleanClient.kt` | utility | config | `providers/build.gradle.kts` leg config shape; RESEARCH example | no analog |
| `P/http/OneShotBody.kt` | utility | file-I/O-ish | none | no analog |
| `P/http/RetryPolicy.kt` | utility | request-response | none | no analog |
| `P/http/SafeFields.kt` | utility | transform | `C/failure/ReasonSupport.kt` lines 4-30 | exact (replicate) |
| `P/ProvidersModule.kt` (MODIFY: may delete once real code exists) | config | n/a | itself | exact |
| `PT/OkHttpVersionGuardTest.kt` (MODIFY: add mockwebserver jar assertion) | test | request-response | itself | exact |
| `PT/anthropic/AnthropicEncoderTest.kt` | test | transform | `core/.../ModelCapabilityTableTest.kt`, `FakeAiProviderTest.kt` | role-match |
| `PT/anthropic/AnthropicTransportTest.kt` | test | request-response | `OkHttpVersionGuardTest.trivialCallRoundTrips` | exact |
| `PT/anthropic/AnthropicRetryTest.kt` | test | request-response | same + `commandPipeline` composition in `RedactionCanaryTest` | role-match |
| `PT/anthropic/AnthropicForcedToolTest.kt` | test | request-response | `OkHttpVersionGuardTest` | role-match |
| `PT/anthropic/AnthropicCanaryTest.kt` | test | request-response | `core/src/test/.../RedactionCanaryTest.kt` | exact |
| `PT/http/CallAwaitTest.kt` | test | event-driven | none (hand-written fake `Call`) | no analog |
| `PT/anthropic/AnthropicLiveCaptureTest.kt` + opt-in Gradle task | test/config | request-response | `okhttpLegs` task registration in `providers/build.gradle.kts` | partial |

## Pattern Assignments

### `C/provider/ModelCapabilities.kt` (modify; model, transform)

Analog: itself. Add `supportsForcedToolChoice: Boolean` the same way `supportsTools` is threaded through five places: ctor param, `toBuilder`, `equals`, `hashCode`, `toString`, `Builder` var + `build()`.

Ctor + builder (lines 23-28, 61-79):
```kotlin
public class ModelCapabilities internal constructor(
    public val supportsTools: Boolean,
    public val caching: CachingMode,
    public val minCacheablePrefixTokens: Int?,
    public val charsPerToken: Double,
) { ...
    public class Builder internal constructor() {
        /** See [ModelCapabilities.supportsTools]. */
        public var supportsTools: Boolean = true
        internal fun build(): ModelCapabilities = ModelCapabilities(
            supportsTools = supportsTools, caching = caching, ...
```
Rules: internal ctor stays (no public default args); default `true`; `UNKNOWN` picks it up automatically via `Builder().build()`. KDoc and identifiers must not contain model-family words (`claude`, `sonnet`, `opus`, `gpt`...) because `NoHardCodedConstantsTest` scans `core/src/main`. Also grep `ModelCapabilityTable.kt` and `PipelineBuilder.capabilities(...)` for any place that copies fields by hand.

### `P/anthropic/AnthropicProvider.kt` (public provider, request-response)

Analog: `AiProvider` seam (`C/provider/AiProvider.kt` lines 18-37) and the builder-DSL idiom of `ModelCapabilities.invoke` (lines 87-91).

Seam to implement:
```kotlin
public interface AiProvider {
    public val id: ProviderId
    public val requiresCredential: Boolean get() = true
    public fun capabilities(model: String): ModelCapabilities = ModelCapabilities.UNKNOWN
    public suspend fun complete(call: ProviderRequest): ModelResult
}
```
Builder idiom to copy (internal ctor, `Builder` class, `companion operator fun invoke(block: Builder.() -> Unit)`), `explicitApi()` is strict so every public member needs `public` and an explicit return type. Use `ProviderId.ANTHROPIC` (`C/ProviderId.kt:19`). Never a data class, enum, or public `const val` (`scripts/review-api-surface.sh` shape rules). `toString` must print no key, no client, no config values that could echo secrets. Never keep per-call state (KDoc rule line 13 of AiProvider.kt). Base URL, `Call.Factory`, sleep lambda, and `ioDispatcher` are `internal` ctor params only. `complete` must return `ModelResult.Failure` for expected failures; rethrow `CancellationException`; let the rest reach `RoutedModel`'s `guarded` (see Shared Patterns).

### `P/anthropic/AnthropicModels.kt` (internal table, transform)

Analog: `ModelCapabilities { }` builder. Exact-id `when`/map, no prefixes; unknown id returns EXPLICIT_BREAKPOINTS with `minCacheablePrefixTokens = null` (UNKNOWN has `caching = NONE`, so it must be overridden):
```kotlin
ModelCapabilities { caching = CachingMode.EXPLICIT_BREAKPOINTS; minCacheablePrefixTokens = 512; supportsForcedToolChoice = false }
```
Values per RESEARCH Pattern 3 table (four 5.x ids false/512, `claude-haiku-4-5` true/4096). Model ids live ONLY in `:providers`. Do not write a `Set<String>` of ids inside the encoder (Pitfall 1).

### `P/anthropic/AnthropicEncoder.kt` (pure utility, transform)

Analog: no in-repo encoder. Types to consume are in `C/transcript/*`: `ModelRequest(system, messages, tools, toolChoice, maxTokens, cache)`, `ToolChoice.Auto()/Required(name)`, `UserMessage`, `AssistantMessage.nativeFor(provider, model)`, `ToolResultsMessage(results)`, `ToolResult(callId, content, isError)`, `ToolSpec(..., strict: Boolean?)`. Build with `buildJsonObject` (the same kotlinx.serialization builders `RedactionCanaryTest` imports: `JsonObject`, `buildJsonObject`, `put`). Key order fixed: model, max_tokens, tools (sorted by name), tool_choice, system, messages. Single `cache_control: {"type":"ephemeral"}` on the system block only when `cache.staticPrefix && capabilities.caching == EXPLICIT_BREAKPOINTS`. Do not re-sort `input_schema` keys. Idea sources (read-only): SB `AnthropicAgentLoop.kt` ~line 330 (`buildRequestBody`, `toolResultBlock` with `is_error` only when true); CT `LogFoodRequestBuilder.kt` ~104-130, 200-215 (reshape: auto + strict + instruction in the user turn). Detekt: `MagicNumber` on, so name constants; `TooManyFunctions` threshold 12 per class (split helpers into top-level `private fun`s or a second file).

### `P/anthropic/AnthropicDecoder.kt` (utility, transform)

Analog: constructors in `C/provider/ModelResult.kt` and `C/transcript/ModelResponse.kt` (lines 18-25):
```kotlin
public class ModelResponse(public val message: AssistantMessage, public val stopReason: StopReason,
    public val usage: Usage, public val requestId: String?)  // init: require(requestId == null || isRequestId(requestId))
```
Map `usage` to `Usage(inputUncached, cacheRead, cacheWrite, output)` per its KDoc. Use internal `@Serializable` DTOs with `Json { ignoreUnknownKeys = true }` for the envelope, raw `JsonObject` for `content`/`input` (pass input through untouched, never default missing keys). Translate wire `model_context_window_exceeded` to `StopReason.CONTEXT_WINDOW_EXCEEDED`. Catch `SerializationException` and `IllegalArgumentException` explicitly (never `Exception`). `requestId` must pass the replicated regex or be null (the `ModelResponse` ctor throws otherwise).

### `P/anthropic/AnthropicErrors.kt` (utility, transform)

Analog: `C/failure/FailureDetails.kt` lines 13-23 and `FailureReason` leaves. Construct with no-arg leaves (`FailureReason.Auth()`, `.Billing()`, `.RateLimited()`, `.Overloaded()`, `.Timeout()`, `.Network()`, `.MalformedResponse()`, `.MalformedToolArgs()`, `.NoToolCall()`, `.ModelNotFound()`, `.HttpError()`).
```kotlin
public class FailureDetails(val httpStatus: Int?, val providerErrorType: String?, val requestId: String?) {
    init {
        require(providerErrorType == null || isSafeToken(providerErrorType)) { ... }
        require(requestId == null || isRequestId(requestId)) { ... }
```
Always build details via the `SafeFields` helpers (null on non-conforming). Status-first `when` per RESEARCH Pattern 5 table. `error.message` read only into in-memory matchers (forced-400 three-condition, spend-limit); never into any failure. No exception text ever stored.

### `P/anthropic/AnthropicTransport.kt` (service, request-response)

Analog: catch discipline of `C/internal/Guarded.kt` (only repo `@Suppress("TooGenericExceptionCaught")` lives there, lines 1-40). `:providers` must use specific catches:
```kotlin
try { ... } catch (e: InterruptedIOException) { Timeout } catch (e: IOException) { if (!currentCoroutineContext().isActive) throw e-as-cancellation; Network }
```
Order matters: `InterruptedIOException` before `IOException`; `CancellationException` rethrown, never reported as `Network`. Loop with budget of 3 HTTP requests, injectable `suspend (Long) -> Unit` sleep, injectable `ioDispatcher` (`withContext(ioDispatcher)`), `response.body?.string()` inside `use {}`. Request headers: `x-api-key`, `anthropic-version: 2023-06-01`, `content-type`. No `runCatching`, no `println`, no `okhttp3.internal.*` (the `scanBannedConstructs` task fails the build; detekt `ForbiddenImport` too).

### `P/http/CallAwait.kt`, `CleanClient.kt`, `OneShotBody.kt`, `RetryPolicy.kt` (no direct analog)

Use RESEARCH.md "Code Examples" verbatim (OneShotJsonBody, `Call.await` with `cont.resume(response) { response.close() }` + `@OptIn(ExperimentalCoroutinesApi::class)`, `cleanClient`). Those use only floor-safe API. Keep them `internal` and Anthropic-agnostic (Phase 5 reuses `http/`). Do NOT copy CT's `await()` (resumes without close when the continuation is already cancelled = late-response leak).

### `P/http/SafeFields.kt` (utility, transform) - exact replicate

Analog: `C/failure/ReasonSupport.kt` lines 4-6, 27-30 (the `isSafeToken`/`isRequestId` are `internal` to `:core`, so replicate):
```kotlin
private val SAFE_TOKEN = Regex("[A-Za-z0-9_.:-]+")
internal fun isSafeToken(value: String): Boolean = value.length <= CODE_LENGTH && SAFE_TOKEN.matches(value)   // 64
internal fun isRequestId(value: String): Boolean = value.length <= ID_LENGTH && SAFE_TOKEN.matches(value)     // 128
```
Provider-side variants should return `String?` (value or null), e.g. `fun String?.asSafeToken(): String? = takeIf { it != null && it.length in 1..64 && SAFE_TOKEN.matches(it) }`. Name constants (detekt MagicNumber).

### `PT/OkHttpVersionGuardTest.kt` (modify; test)

Analog: itself. Extend with a sibling test reading the mockwebserver jar the same way:
```kotlin
val actual = Class.forName("okhttp3.OkHttp").getField("VERSION").get(null) as String
val jar = OkHttpClient::class.java.protectionDomain.codeSource.location
println("OKHTTP_RUNTIME=$actual expected=$expected jar=$jar")
```
Add `MockWebServer::class.java.protectionDomain.codeSource.location` and assert the file name starts with `mockwebserver-$expected`, and `Class.forName("okhttp3.mockwebserver.MockWebServer")` resolves (legacy, not `mockwebserver3`). `println` in tests is allowed (existing precedent; scanner covers `src/main` only). Reflective on purpose for `OkHttp.VERSION` (compile-time constant is inlined).

### `PT/anthropic/AnthropicTransportTest.kt`, `AnthropicRetryTest.kt`, `AnthropicForcedToolTest.kt` (tests, request-response)

Analog: `OkHttpVersionGuardTest.trivialCallRoundTrips` (lines 24-37):
```kotlin
MockWebServer().use { server ->
    server.enqueue(MockResponse().setBody("hi"))
    server.start()
    val call = OkHttpClient().newCall(Request.Builder().url(server.url("/")).build())
    call.execute().use { response -> sink.record(response.body?.string().orEmpty()) }
}
```
Rules: legacy `okhttp3.mockwebserver` only (`enqueue(MockResponse)`, `setResponseCode`, `setSocketPolicy(DISCONNECT_AFTER_REQUEST)`, `setHeadersDelay`, `takeRequest()`, `requestCount`); never `QueueDispatcher`, `mockwebserver3`, `okhttp3.internal`. Always `body?.string()`. Use `runBlocking`/real dispatcher (not `runTest` virtual time) for real sockets; inject the sleep lambda to assert waits. Base-URL seam is `internal`, set to `server.url("/")`. Place in `providers/src/test` so all three legs run unchanged.

Pipeline-level no-duplicate-commit test: copy composition from `core/src/test/.../RedactionCanaryTest.kt` (`commandPipeline { tier(..); provider(..); gate = ScriptedGate.admitAll(); commitSink = RecordingCommitSink(); credentials = ScriptedCredentialSource.keys(...); providerSelection = ScriptedSelectionSource.fixed(...); listener = RecordingEventListener() }`, `ScriptedStrategy`, `FakeMutation`, `ToolStep.Mutation`). Imports available from `testFixtures(project(":core"))` (already a `testImplementation`).

### `PT/anthropic/AnthropicCanaryTest.kt` (test) - exact analog

Analog: `core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/RedactionCanaryTest.kt` (lines 67-72 constants, `see(value)` sweep helper line 81, `sweepOutcome` line 109, `fullScriptedRun` line 173):
```kotlin
private const val CANARY = "CANARY"
private const val KEY = "sk-CANARY-KEY"
private const val OTHER_KEY = "sk-CANARY-OTHER-PROVIDER-KEY"
```
Add positive controls first (assert `RecordedRequest.getHeader("x-api-key") == KEY` and the transcript/tool-arg/tool_result canaries are in the body), then sweep `toString()` of outcome, trace, events, `ModelResult`, `FailureReason`, `FailureDetails`, `ProviderRequest`, `Credential`, provider instance + builder. Legs: success echo, 400/401/429/500 with canary in `error.message`, malformed 2xx, IOException. Never stringify OkHttp `Request`/`Headers`/`Response`.

### `providers/build.gradle.kts` (modify, config) and `PT/anthropic/AnthropicLiveCaptureTest.kt`

Analog: the `okhttpLegs.forEach` task registration (lines 55-100) for a new opt-in task:
```kotlin
val legTest = tasks.register<Test>("test$legName") {
    group = "verification"
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].output + sourceSets["main"].output + legClasspath
    ...
}
tasks.named("check") { dependsOn(legTest) }
```
Do NOT touch `verifyOkHttpCompileFloor` (gradle/invariants.gradle.kts:391), the leg configurations, or `check` wiring (Runtime Decision). Live capture: exclude `*Live*` via `filter.excludeTestsMatching("*Live*")` on `test` and each leg, and register a separate non-`check` task that sets a system property gate with the key from env; never print the key. Also add no new deps (`libs.versions.toml` unchanged). Root `build.gradle.kts` detekt `source.setFrom("src/main/kotlin","src/test/kotlin","src/testFixtures/kotlin")` only lints those dirs; keep new code there.

## Shared Patterns

### Specific-exception catches, never-throw collapse lives in core
**Source:** `C/internal/Guarded.kt` lines 1-40. `:providers` code catches `IOException` / `InterruptedIOException` / `SerializationException` / `IllegalArgumentException` only, rethrows `kotlin.coroutines.cancellation.CancellationException`. Anything else propagates to `RoutedModel`'s `guarded`, which yields `FailureReason.Unexpected(errorClass)`. **Apply to:** Transport, Decoder, Provider, CallAwait.

### Secret hygiene
**Source:** `C/Credential.kt` (`toString` = `Credential(provider=...)`), `C/failure/FailureDetails.kt` (docs: never a body), `ModelResult.Failure.toString`. Key lives only in `Credential.apiKey` and the single `x-api-key` header. No logging, no `"${e.message}"`, no `"$request"`/`"$response"`. Every new class's `toString` prints no secret-bearing field. **Apply to:** every main file.

### Banned constructs (build-failing)
**Source:** `gradle/invariants.gradle.kts` `scanBannedConstructs` (line 166) + `config/detekt/detekt.yml` ForbiddenImport. Bans: `runCatching`, `println`, `System.out/err`, `printStackTrace`, DI annotations, `okhttp3.internal`, `mockwebserver3`, `okhttp3.coroutines`, `android.util.Log`, planning ids in comments (no `D-05`/`PROV-04` in source comments). **Apply to:** all `src/main` files (and detekt to tests).

### Public API shape
**Source:** `ModelCapabilities.kt` (internal ctor + Builder + `invoke`), `FailureDetails.kt` (hand-written `equals/hashCode/toString`). Explicit `public`, no data classes/enums/public const, no default args on public ctors, KDoc on every public member. **Apply to:** `AnthropicProvider` and its builder only (everything else `internal`).

### Test fixtures
**Source:** `core/src/testFixtures/.../core/testing/` (`FakeAiProvider`, `RecordingSink`, `RecordingEventListener`, `RecordingCommitSink`, `ScriptedGate`, `ScriptedStrategy`, `ScriptedCredentialSource`, `ScriptedSelectionSource`, `FakeMutation`). Already wired via `testImplementation(testFixtures(project(":core")))`; add no copies.

### Floor-safe OkHttp API
`response.body?.string()`, `Call.enqueue`, `newBuilder()`, legacy mockwebserver. Avoid `Request(url=...)`, `Call.tag(KClass)`, `executeAsync`, `Response.body.string()`.

## No Analog Found

| File | Role | Data Flow | Reason |
|---|---|---|---|
| `P/http/OneShotBody.kt` | utility | request body | No HTTP code in repo; use RESEARCH Code Example |
| `P/http/CallAwait.kt` | utility | callback to suspend | Only port-source analog has the leak; use RESEARCH example |
| `P/http/CleanClient.kt` | utility | config | No client code in repo; use RESEARCH example |
| `P/http/RetryPolicy.kt` | utility | retry | No retry code in repo; ~40-line loop per RESEARCH Pattern 4 |
| `PT/http/CallAwaitTest.kt` | test | event-driven | No fake `Call` precedent; implement only `enqueue/cancel/isCanceled/request/execute/isExecuted/timeout/clone` |

## Metadata

**Analog search scope:** `core/src/main`, `core/src/test`, `core/src/testFixtures`, `providers/`, `gradle/invariants.gradle.kts`
**Files scanned:** about 110 tracked files listed, 12 read
**Pattern extraction date:** 2026-09-30
