# Phase 5: OpenAI & OpenRouter Transports - Pattern Map

**Mapped:** 2026-10-01
**Files analyzed:** 33 (14 main, 18 test, 1 build; plus golden resources)
**Analogs found:** 31 / 33 (all analogs verified git-tracked via `git ls-files`)

All paths below are relative to `providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/` (MAIN) or `providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/` (TEST). Line numbers refer to the files as they exist today.

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match |
|---|---|---|---|---|
| MAIN `schema/OptionalProperties.kt` (modify) | utility | transform | itself (extend walk) | exact |
| MAIN `http/SafeFields.kt` (modify: add `isHeaderSafe`) | utility | transform | `anthropic/AnthropicTransport.kt` L48-49 (move) | exact |
| MAIN `anthropic/AnthropicTransport.kt` (modify: delete private copy) | service | request-response | itself | exact |
| MAIN `chat/ChatStrict.kt` | utility | transform | `anthropic/AnthropicStrict.kt` | exact |
| MAIN `chat/ChatVendor.kt` | config | n/a | `anthropic/AnthropicProvider.kt` consts L17-22 | partial |
| MAIN `chat/OpenAiModelRules.kt` | config | transform | `anthropic/AnthropicModels.kt` | role-match |
| MAIN `chat/ChatModels.kt` | model | transform | `anthropic/AnthropicModels.kt` | exact |
| MAIN `chat/ChatEncoder.kt` | service | transform | `anthropic/AnthropicEncoder.kt` | exact |
| MAIN `chat/ChatMessageEncoder.kt` | service | transform | `anthropic/AnthropicMessageEncoder.kt` | exact |
| MAIN `chat/ChatDecoder.kt` | service | transform | `anthropic/AnthropicDecoder.kt` | exact |
| MAIN `chat/ChatErrors.kt` | utility | transform | `anthropic/AnthropicErrors.kt` | exact |
| MAIN `chat/ChatTransport.kt` | service | request-response | `anthropic/AnthropicTransport.kt` | exact |
| MAIN `chat/ChatCompletionsProvider.kt` | provider | request-response | `anthropic/AnthropicProvider.kt` | exact |
| MAIN `chat/ChatCompletionsAttemptObserver.kt` | public API | event-driven | `anthropic/AnthropicAttemptObserver.kt` | exact |
| TEST `schema` cases for extended detector | test | transform | `anthropic/AnthropicStrictTest.kt` (only existing `hasOptionalProperties` tests; no `OptionalPropertiesTest` exists, create `schema/OptionalPropertiesTest.kt`) | role-match |
| TEST `chat/ChatStrictTest`, `ChatEncoderTest`, `ChatDecoderTest`, `ChatErrorMapTest`, `ChatModelsTest` | test | transform | `anthropic/Anthropic{Strict,Encoder,Decoder,ErrorMap,Models}Test.kt` | exact |
| TEST `chat/ChatFixtures.kt` | test util | transform | `anthropic/AnthropicFixtures.kt` | exact |
| TEST `chat/ChatTransportTest`, `ChatRetryTest`, `ChatTimeoutTest`, `ChatCancellationTest`, `ChatMalformedKeyTest`, `ChatCanaryTest` | test | request-response | same-named `Anthropic*Test.kt` | exact |
| TEST `chat/ChatPipelineRetryTest` | test | request-response | `anthropic/AnthropicPipelineRetryTest.kt` | exact |
| TEST `chat/ChatAbsentOptionalTest` (Parameterized per vendor) | test | request-response | `anthropic/AnthropicStrictTest.kt` (absent-optional part) | role-match |
| TEST `parity/TokenParityTest.kt` | test | batch | `anthropic/AnthropicPipelineRetryTest.kt` (pipeline composition) | partial |
| TEST `ProvidersApiShapeTest` (modify list) | test | n/a | itself L95-140 | exact |
| TEST `chat/ChatCompletionsLiveCaptureTest` | test | request-response | `anthropic/AnthropicLiveCaptureTest.kt` | exact |
| `providers/build.gradle.kts` (modify, plan 05-08 only) | config | n/a | `liveAnthropicCapture` block L68-81 | exact |
| `src/test/resources/golden/chat/{openai,openrouter}/*.json` | fixture | n/a | none (no committed golden resources exist) | none |
| `chat/` OpenRouter id normalization, 200-envelope path, usage `prompt - cached` mapping, `tool_calls` string-arguments decode | (logic inside files above) | transform | no analog for the logic; structure copies the Anthropic file | none |

## Pattern Assignments

### MAIN `chat/ChatCompletionsProvider.kt` (provider, request-response)

**Analog:** `anthropic/AnthropicProvider.kt` (117 lines). Copy structure whole: `internal constructor`, `Builder` with internal vars, `companion operator fun invoke`. Replace the single `invoke` with `openAi(block)` / `openRouter(block)` over a `ChatVendor`.

**Builder defaults and build-time validation** (L17-22, L83-111):
```kotlin
private const val DEFAULT_TIMEOUT_MILLIS = 60_000L
private const val DEFAULT_RETRY_AFTER_CAP_MILLIS = 5_000L
private const val DEFAULT_TRANSIENT_BACKOFF_MILLIS = 500L
private val LOOPBACK_HOSTS = setOf("localhost", "127.0.0.1", "::1")
...
internal fun build(): AnthropicProvider {
    require(callTimeoutMillis > 0) { "callTimeoutMillis must be positive" }
    ...
    require(baseUrl.isHttps || baseUrl.host in LOOPBACK_HOSTS) { "the base URL must use https" }
    val client = cleanClient(httpClient, callTimeoutMillis, readTimeoutMillis)
    val transport = AnthropicTransport(client, baseUrl, ioDispatcher, sleep, retryAfterCapMillis, transientBackoffMillis, attemptObserver)
    return AnthropicProvider(transport, callTimeoutMillis, readTimeoutMillis)
}
```
**Capability hook** (L53-57): `override val id = ...; override fun capabilities(model: String) = AnthropicModels.capabilities(model)` becomes `id = vendor.providerId` and `ChatModels.capabilities(vendor, model)`.
**toString** (L59-60): timeouts only, never key/URL/credential. `AnthropicProvider.Builder` KDoc style: public builder vars documented, internal vars carry a one-line comment (e.g. "The wait before the one transient retry; a suspend call, so cancelling the command ends it.").
Differences: base URLs `https://api.openai.com/v1/` and `https://openrouter.ai/api/v1/` with path `chat/completions` (Anthropic uses `v1/messages` appended via `addPathSegments`, so the base keeps a trailing slash).

---

### MAIN `chat/ChatTransport.kt` (service, request-response)

**Analog:** `anthropic/AnthropicTransport.kt` (206 lines). Keep the `Attempted` / `Progress` / `Resend` private-class loop, drop `FORCED_TOOL_RESHAPE` and `mentionsToolChoice` (Chat has no reshape-on-400), set `MAX_REQUESTS = 2`.

**Imports** (L3-23): `Credential`, `ProviderId`, `FailureReason`, `ModelResult`, `ProviderRequest`, `http.{HttpReply, OneShotJsonBody, await, isTransientStatus, retryAfterSeconds, transientWaitMillis}`, `CoroutineDispatcher`, `currentCoroutineContext`, `ensureActive`, `withContext`, `HttpUrl`, `OkHttpClient`, `Request`, `IOException`, `InterruptedIOException`.

**Credential refusal before any request** (L41-46; vendor-parameterize `ProviderId.ANTHROPIC` as `vendor.providerId`, and use the moved `isHeaderSafe` from `http/SafeFields.kt`):
```kotlin
private fun refusalFor(credential: Credential?): FailureReason? = when {
    credential == null || credential.provider != ProviderId.ANTHROPIC -> FailureReason.NotConfigured(ProviderId.ANTHROPIC)
    !isHeaderSafe(credential.apiKey) -> FailureReason.Auth()
    else -> null
}
```
**Send/retry skeleton** (L99-120, L126-132 observer wrapper with `catch (ignored: Exception)` and no `@Suppress`):
```kotlin
suspend fun send(call: ProviderRequest): ModelResult {
    ...
    return withContext(ioDispatcher) { sendWithRetry(call, credential, first) }
}
private suspend fun sendWithRetry(call, credential, progress): ModelResult {
    val attempted = attempt(call, credential, progress.reshape)
    notify(AnthropicAttempt(progress.requestsSent, progress.kind, attempted.status))
    val resend = planResend(call, attempted, progress) ?: return attempted.result
    resend.waitMillis?.let { sleep(it) }
    return sendWithRetry(call, credential, resend.progress)
}
```
**Request build + IO error mapping** (L163-179). Replace headers with `.header("Authorization", "Bearer ${credential.apiKey}")` and `content-type`; no version header:
```kotlin
return try {
    interpret(client.newCall(request).await(), call)
} catch (ignored: InterruptedIOException) {
    ioFailure(FailureReason.Timeout())
} catch (ignored: IOException) {
    ioFailure(FailureReason.Network())
}
```
**ioFailure** (L202-205): `currentCoroutineContext().ensureActive()` first so cancellation wins.
**interpret** (L181-198): difference to add: on `reply.isSuccessful` first look for a top-level `error` object (200-envelope) via `ChatErrors`, treat transient codes (`isTransientStatus` plus 524) as `transient = true`; request id comes from header `x-request-id` for OpenAI, body `id` for OpenRouter (decoder side). The spend-cap analog is OpenAI `insufficient_quota` on 429 (non-transient, `Billing`).

---

### MAIN `chat/ChatCompletionsAttemptObserver.kt` (public API, event-driven)

**Analog:** `anthropic/AnthropicAttemptObserver.kt` (62 lines). Copy verbatim shape: `public fun interface` + `public class ...Attempt internal constructor(number, kind, httpStatus)` with manual `equals`/`hashCode`/`toString` (no data class; `ProvidersApiShapeTest` forbids `copy`/`componentN`) + `@JvmInline public value class ...Kind internal constructor(val value: String)` with `companion` constants `INITIAL = Kind("initial")`, `TRANSIENT_RETRY = Kind("transient_retry")`. Add `finishReason: String?` (via `safeToken`) and `toolCalls: Int` to the Attempt class, include both in `equals`/`hashCode` (L34-38 pattern: `Objects.hash(number, kind.value, httpStatus)`). Do not add `FORCED_TOOL_RESHAPE`.

---

### MAIN `chat/ChatEncoder.kt` (service, transform)

**Analog:** `anthropic/AnthropicEncoder.kt` (136 lines).

**Top-level function: pure bytes** (L45-61):
```kotlin
internal fun encodeAnthropicRequest(call: ProviderRequest, reshape: Boolean = false): ByteArray {
    val request = call.request
    val tools = request.tools.sortedBy { it.name }
    val body = buildJsonObject {
        put("model", call.model)
        ...
        if (tools.isNotEmpty()) { put("tools", encodeTools(...)); put("tool_choice", encodeToolChoice(call, reshape)) }
        put("messages", encodeMessages(call).withInstruction(requiredToolInstruction(call).takeIf { reshape }))
    }
    return Json.encodeToString(JsonObject.serializer(), body).toByteArray(Charsets.UTF_8)
}
```
**Reuse as is** (L102-124): `requiredToolInstruction` text `"Call the <name> tool with your result."` and `withInstruction` / `withTrailingText` for the forced-unsupported fallback; adapt `withTrailingText` since Chat `content` is a string, not an array (append to the string). Key order is specified in RESEARCH Pattern 2 (`model, messages, tools, tool_choice, parallel_tool_calls, reasoning_effort, max_completion_tokens|max_tokens, provider`).
**Tool encoding** (L63-78): app schema object goes out untouched; the new nested shape is `{"type":"function","function":{name,description,parameters,strict?}}`. Strict/strip comes from `ChatStrict` and applies only to the wire copy.
**Strict-name decision** (L80-88) differs: Anthropic honors `strict == true`; Chat must compute `tool.strict != false && eligible(schema)` (engine wins, D-06).
**Const style** (L19-25): private top-level `const val` names for every JSON key used more than once.

---

### MAIN `chat/ChatMessageEncoder.kt` (service, transform)

**Analog:** `anthropic/AnthropicMessageEncoder.kt` (81 lines). Keep the exhaustive `when (message)` over the sealed `Message` (L233-243):
```kotlin
internal fun encodeMessages(call: ProviderRequest): JsonArray = buildJsonArray {
    call.request.messages.forEach { message ->
        add(when (message) {
            is UserMessage -> userMessage(message)
            is AssistantMessage -> assistantMessage(message, call.model)
            is ToolResultsMessage -> toolResultsMessage(message)
        })
    }
}
```
**Native replay** (L252-255): `message.nativeFor(ProviderId.ANTHROPIC, model) ?: rebuiltContent(message)` becomes `message.nativeFor(vendor.providerId, model)` returning the stored raw assistant message verbatim, else rebuild `tool_calls` with `arguments` as a compact JSON string. Difference: Anthropic packs all results into one user message (L272-287); Chat emits one `{"role":"tool","tool_call_id","content"}` message per result, so `encodeMessages` must flatten (use `forEach { add(...) }` per result). Prepend the `system` message in `ChatEncoder`.

---

### MAIN `chat/ChatDecoder.kt` (service, transform)

**Analog:** `anthropic/AnthropicDecoder.kt` (107 lines).

**Never-throw shape with private carrier exception** (L236-263):
```kotlin
private class MalformedAnswer(val reason: FailureReason) : IllegalArgumentException("unusable model answer")

internal fun decodeAnthropicResponse(body: String?, requestIdHeader: String?, model: String, toolRequired: Boolean = false): ModelResult =
    try {
        val response = decodeResponse(body, requestIdHeader, model)
        if (toolRequired && response.message.toolCalls.isEmpty() && response.stopReason == StopReason.END_TURN) {
            ModelResult.Failure(FailureReason.NoToolCall())
        } else ModelResult.Success(response)
    } catch (e: IllegalArgumentException) {   // also covers SerializationException
        ModelResult.Failure((e as? MalformedAnswer)?.reason ?: FailureReason.MalformedResponse())
    }
```
**Helpers to copy** (L277-280 `parseRoot`, L309-313 `count` clamped `coerceAtLeast(0L)` and `stringField`), `Usage.ZERO` on missing usage (L300), `NativeReplay(vendor.providerId, model, rawMessageObject)` (L270), `safeRequestId(...)` (L273).
**Stop-reason table** (L227-234): Chat version maps by D-10 precedence (see RESEARCH Pattern 4 table); `length` -> `MAX_TOKENS` without decoding arguments. Per Pitfall 12 (detekt `ReturnCount`/`CyclomaticComplexMethod`), write one small private function per table row.
Differences: arguments arrive as a JSON string (`""` -> `{}`; invalid -> `MalformedToolArgs`), Anthropic's `input` is already an object (L291-297 `decodeToolUse` is the nearest shape). Usage: `inputUncached = prompt - cached - cache_write` clamped at 0. No analog for the 200-envelope `error` branch: write new, reusing `ChatErrors`.

---

### MAIN `chat/ChatErrors.kt` (utility, transform)

**Analog:** `anthropic/AnthropicErrors.kt` (108 lines).

**Status table** (L341-352), `internal class ...ErrorInfo` with a `toString` of status + ids only (L363-374), parse-and-discard (L380-393), `parseObject`/`textField` helpers with `catch (expected: IllegalArgumentException)` (L408-421):
```kotlin
private val STATUS_REASONS: Map<Int, () -> FailureReason> = mapOf(
    STATUS_UNAUTHORIZED to { FailureReason.Auth() }, STATUS_FORBIDDEN to { FailureReason.Auth() },
    STATUS_PAYMENT_REQUIRED to { FailureReason.Billing() }, STATUS_TOO_MANY_REQUESTS to { FailureReason.RateLimited() },
    STATUS_NOT_FOUND to { FailureReason.ModelNotFound() }, STATUS_REQUEST_TIMEOUT to { FailureReason.Timeout() },
    STATUS_GATEWAY_TIMEOUT to { FailureReason.Timeout() }, STATUS_SERVICE_UNAVAILABLE to { FailureReason.Overloaded() },
    STATUS_OVERLOADED to { FailureReason.Overloaded() },
)
internal fun AnthropicErrorInfo.reason(): FailureReason = when {
    spendCapReached || userSpendLimit -> FailureReason.Billing()
    else -> STATUS_REASONS[status]?.invoke() ?: FailureReason.HttpError()
}
internal fun AnthropicErrorInfo.details(): FailureDetails = FailureDetails(status, errorType, requestId)
```
Differences: the status for a 200 envelope is `error.code` (numeric); OpenAI 429 + `insufficient_quota` -> `Billing` non-transient (analog: `spendCapReached`); in-memory message matchers (`/v1/responses` -> `ModelUnsupported`; OpenRouter 404 `No endpoints found that support` -> `ModelUnsupported`) mirror `mentionsToolChoice` (L391, boolean derived, message never stored). Add 524 to the table. Never read `metadata.raw` / `provider_name`.

---

### MAIN `chat/ChatModels.kt` and `chat/OpenAiModelRules.kt` (model/config, transform)

**Analog:** `anthropic/AnthropicModels.kt` (47 lines).
```kotlin
internal object AnthropicModels {
    private val rejectsForcedToolChoice = ModelCapabilities {
        supportsForcedToolChoice = false
        caching = CachingMode.EXPLICIT_BREAKPOINTS
        minCacheablePrefixTokens = STANDARD_MIN_CACHEABLE_PREFIX_TOKENS
    }
    private val unknownModel = ModelCapabilities { caching = CachingMode.EXPLICIT_BREAKPOINTS }
    fun capabilities(model: String): ModelCapabilities = when (model) {
        OPUS_5_5, SONNET_5_5, FABLE_5_1, MYTHOS_5_1 -> rejectsForcedToolChoice
        HAIKU_4_5 -> haiku
        else -> unknownModel
    }
}
```
Pattern: named private model-id consts at top, shared prebuilt `ModelCapabilities { }` vals, one `when`. `ChatModels` delegates OpenRouter `anthropic/<id>` to `AnthropicModels.capabilities(rest.replace('.', '-'))` for `supportsForcedToolChoice` and overrides `caching = NONE` (build via `ModelCapabilities { supportsForcedToolChoice = ...; caching = CachingMode.NONE }`). `OpenAiModelRules` is a different shape (regex family match on date-suffixed ids, returns an internal rules value class with reasoning param and token param name); there is no existing regex-family analog, so it follows RESEARCH Pattern 1 table. KDoc style: state check date and "app patches via `PipelineBuilder.capabilities`" (AnthropicModels L436-444). Doc date comments only, no planning ids (detekt `ForbiddenComment`).

---

### MAIN `chat/ChatStrict.kt` (utility, transform)

**Analog:** `anthropic/AnthropicStrict.kt` (75 lines). Reuse its walker helpers verbatim (L184-211): `withinSubset`, `isClosed` (`additionalProperties == false`), `isObjectNode`, `childSchemas`, `typeNames`. Entry shape (L181-182):
```kotlin
internal fun isAnthropicStrictEligible(schema: JsonObject): Boolean =
    typeNames(schema) == listOf(TYPE_OBJECT) && !hasOptionalProperties(schema) && withinSubset(schema)
```
Differences: `UNSUPPORTED_KEYWORDS` (L156-170) is replaced by the OpenAI unsupported set (`oneOf`, `not`, `if`/`then`/`else`, `dependent*`, `prefixItems`, ...; `anyOf`, `$defs` are supported), the `minItems` clamp is dropped, `childSchemas` must also walk `anyOf` and `$defs`. Add a second function `stripForStrict(schema): JsonObject` that visits schema positions only (never property names) with two data constants (drop-always keywords; allowed `format` set). No analog for the strip walk.

---

### MAIN `schema/OptionalProperties.kt` (modify, utility, transform)

**Analog:** itself (L505-510 in the combined listing; file is 31 lines). Current body:
```kotlin
internal fun hasOptionalProperties(schema: JsonObject): Boolean {
    val properties = schema[PROPERTIES] as? JsonObject
    return (properties != null && !requiredNames(schema).containsAll(properties.keys)) ||
        properties.orEmpty().values.any { (it as? JsonObject)?.let(::hasOptionalProperties) == true } ||
        (schema[ITEMS] as? JsonObject)?.let(::hasOptionalProperties) == true
}
```
Extend with additional child recursion (`anyOf`/`oneOf`/`allOf` arrays, `$defs`, `definitions`, schema-valued `additionalProperties`, `prefixItems`). Keep signature and the existing `private fun JsonObject?.orEmpty()` / `JsonArray?.orEmpty()` helpers (L512-517); to stay under detekt `ReturnCount`/complexity, extract a `childSchemas(schema): List<JsonObject>` helper and use `any`. Update the KDoc ("The walk covers ..."). Tests: the existing `hasOptionalProperties` imports in `anthropic/AnthropicStrictTest.kt` are the only coverage; add `schema/OptionalPropertiesTest.kt` with one case per newly walked keyword and a nullable-required regression case.

---

### MAIN `http/SafeFields.kt` (modify) + `anthropic/AnthropicTransport.kt` (modify)

Move `AnthropicTransport.kt` L48-49 to `SafeFields.kt` as `internal fun isHeaderSafe(value: String): Boolean = value.all { it == '\t' || it in ' '..'~' }` (keep the existing leading comment; style of the file: top comment + KDoc on each function, L11-18 of SafeFields). Delete the private copy and add the import. Extend `http/SafeFieldsTest.kt` with a header-safety case.

---

### TEST patterns

**`chat/ChatFixtures.kt`** - Analog `anthropic/AnthropicFixtures.kt` (81 lines): top-level public `fun` JSON builders (`buildJsonObject { put(...) }`), `putCount` helper that writes `JsonNull` for null; `successBody(...)` returns `String`. Copy for `choicesBody(message, finishReason, usage)`, `toolCall(id, name, argumentsString)`, `usageJson(prompt, completion, cached, cacheWrite)`, `errorEnvelope(code)`.

**Transport/MockWebServer tests** - Analog `anthropic/AnthropicTransportTest.kt` (L1-70): `MockWebServer` + `MockResponse().setResponseCode(200).setHeader("request-id", ...).setBody(...)`, provider built with `AnthropicProvider { baseUrl = server.url("/") ... }` (internal builder vars accessible from same-module tests), `runBlocking`. Imports use only `okhttp3.mockwebserver.*` (legacy). Keys in tests use the `sk-CANARY-KEY-BODY` style (see `AnthropicCanaryTest.kt`, 447 lines, for the secret-sweep structure).

**Capability tests via the real pipeline** - Analog `anthropic/AnthropicModelsTest.kt` L28-50: a `TableBackedProvider : AiProvider` object plus `commandPipeline { tier(...); provider(...); providerSelection = ...; credentials = ...; gate = ...; commitSink = ... }` with `RecordingSink<ModelCapabilities?>`. Reuse for `ChatModelsTest` (normalization, GPT-6 rows) and the pre-network GPT-6 refusal test (zero requests recorded by MockWebServer, `capability_refused` trace).

**Retry/pipeline no-duplicate-commit** - Analog `anthropic/AnthropicPipelineRetryTest.kt` (108 lines) and `AnthropicRetryTest.kt` (363 lines); drop the forced-tool-reshape cases (`AnthropicForcedToolTest.kt` has no Chat counterpart).

**Absent-optional contract (PROV-12)** - Analog `anthropic/AnthropicStrictTest.kt` (259 lines; imports `hasOptionalProperties`, MockWebServer, `ToolChoice`, `CacheDirective`). Make the new test `@RunWith(Parameterized::class)` over the two vendors (JUnit 4.13.2 is declared).

**Token parity** - No direct analog. Compose from `AnthropicPipelineRetryTest` (pipeline wiring) and `anthropic` + `chat` decoders; assert `session.tokensUsed` and the comparison against ceilings `total-1`, `total`, `total+1` (see RESEARCH Pattern 5).

**ProvidersApiShapeTest** - Modify near L95-140: `theRulesStayQuietOnTheShippedShapes` currently asserts on `AnthropicAttempt`, `AnthropicProvider`, `AnthropicAttemptKind`; add the same three assertion lines for `ChatCompletionsAttempt`, `ChatCompletionsProvider`, `ChatCompletionsAttemptKind` (RESEARCH says add to the `sweepIsNotVacuous` list; it sweeps all main classes automatically, `MIN_INSPECTED = 10` at L137).

**Live capture test** - Analog `anthropic/AnthropicLiveCaptureTest.kt` (L21-60): `OPT_IN_VAR` / `KEY_VAR` consts, `Assume` skips unless opted in, a hard `MAX_HTTP_REQUESTS` ceiling with `AtomicInteger`, prints ids/statuses/counts only. Class name must contain `Live` (the `test` filter excludes `*Live*`). Per RESEARCH the capture builds requests with the internal encoder and POSTs with a plain `OkHttpClient`, writes sanitized goldens (no analog for the sanitizer).

---

### `providers/build.gradle.kts` (modify; plan 05-08 only)

**Analog:** lines 64-81.
```kotlin
tasks.named<Test>("test") {
    ...
    filter { excludeTestsMatching("*Live*") }
}
val liveTestSet = the<SourceSetContainer>()["test"]
tasks.register<Test>("liveAnthropicCapture") {
    group = "verification"
    description = "Opt-in: ..."
    val testSet = liveTestSet
    testClassesDirs = testSet.output.classesDirs
    classpath = testSet.runtimeClasspath
    filter { includeTestsMatching("*AnthropicLiveCaptureTest") }
    outputs.upToDateWhen { false }
    val optIn = providers.environmentVariable("VAE_LIVE_ANTHROPIC")
    onlyIf { optIn.orNull == "1" }
    testLogging { showStandardStreams = true }
}
```
Register `liveChatCompletionsCapture` the same way (`*ChatCompletionsLiveCaptureTest`, env `VAE_LIVE_CHAT`; confirm var name in the plan). The leg tasks created by `okhttpLegs.forEach` (L83+) already exclude `*Live*`; do not add the live task to `check`.

## Shared Patterns

### No-throw, no-body-text discipline
**Source:** `anthropic/AnthropicDecoder.kt` L236-263 and `anthropic/AnthropicErrors.kt` L376-393.
**Apply to:** `ChatDecoder`, `ChatErrors`. Private carrier exception caught as `IllegalArgumentException`; server strings only through `safeToken` / `safeRequestId` (`http/SafeFields.kt`); error `message` read only into in-memory booleans; `FailureDetails(status, errorType, requestId)` is the only detail carrier.

### Narrow catches only
**Source:** `anthropic/AnthropicTransport.kt` L122-132, L173-178; `anthropic/AnthropicErrors.kt` L412.
**Apply to:** all `chat/` main files. Catch parameters named `ignored` / `expected`; only the observer `notify` catches `Exception`, no `@Suppress`; no `runCatching`.

### Keys never leak
**Source:** `anthropic/AnthropicTransport.kt` L38-49 (`refusalFor`, `isHeaderSafe`), `AnthropicProvider.kt` L59-60 (`toString`).
**Apply to:** `ChatTransport`, `ChatCompletionsProvider`. Vet the Bearer value before building the `Request`; `toString` prints timeouts only.

### Shared HTTP plumbing (reuse unchanged)
**Source:** `http/CleanClient.kt` (`cleanClient(app, callTimeoutMillis, readTimeoutMillis)`), `http/CallAwait.kt` (`Call.await(): HttpReply`, body via `body?.string()`), `http/OneShotBody.kt` (`OneShotJsonBody(bytes)`), `http/RetryPolicy.kt` (`isTransientStatus`, `retryAfterSeconds`, `transientWaitMillis`).
**Apply to:** `ChatTransport`, `ChatCompletionsProvider.Builder.build()`.

### Encoding purity
**Source:** `anthropic/AnthropicEncoder.kt` L30-61. Pure function of the request, fixed key order, tools sorted by name, `Json.encodeToString(JsonObject.serializer(), body).toByteArray(Charsets.UTF_8)`, app schema untouched when non-strict.
**Apply to:** `ChatEncoder`.

### Code-style constraints (detekt zero baseline, `config/detekt/detekt.yml`)
Named constants for every status code and magic number (STATUS_* consts, `AnthropicErrors.kt` L325-339); `TooManyFunctions` 12 per class; `LongParameterList` constructor threshold 8 with defaults ignored; explicit API (`public`/`internal`) on every declaration; no `okhttp3.internal.*`, `mockwebserver3`, `android.util.Log`, DI; no planning ids (`D-xx`, `PROV-xx`, `Phase N`) in comments; no `data class`, `enum`, or public `const val`; no default arguments on public constructors.

## No Analog Found

| File / Logic | Role | Data Flow | Reason |
|---|---|---|---|
| `golden/chat/{openai,openrouter}/*.json` and manifest | fixture | n/a | No committed golden resources exist in the repo (no `src/test/resources` golden files tracked); follow RESEARCH Pitfall 11 for naming and sanitizing |
| Strip walker in `chat/ChatStrict.kt` | utility | transform | No schema-rewriting code exists; `AnthropicStrict` only inspects |
| OpenRouter id normalization and `OpenAiModelRules` regex families | model | transform | `AnthropicModels` is an exact-id table; family rules are new (spec in RESEARCH Pattern 1) |
| 200-envelope `error` handling and string-`arguments` decode | decoder | transform | Anthropic has neither; build from RESEARCH Pattern 4 |
| Sanitizer for captured goldens | test util | file-I/O | No existing capture sanitizer; `AnthropicLiveCaptureTest` prints only, stores nothing |
| `TokenParityTest` assertions across three decoders | test | batch | No cross-provider test exists yet; pipeline wiring only is reusable |

## Metadata

**Analog search scope:** `providers/src/main/.../providers/{anthropic,http,schema}`, `providers/src/test/.../providers/**`, `providers/build.gradle.kts`, `core/src/testFixtures`.
**Files scanned:** 15 main files read in full (anthropic 9, http 5, schema 1), 6 test files read in part, 1 build file section.
**Tracked-source gate:** `git ls-files providers` listed every analog above as tracked; no gitignored mirror paths used.
**Pattern extraction date:** 2026-10-01
