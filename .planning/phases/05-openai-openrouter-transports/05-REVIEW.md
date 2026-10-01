---
phase: 05-openai-openrouter-transports
reviewed: 2026-10-01T00:00:00Z
depth: standard
files_reviewed: 44
files_reviewed_list:
  - providers/build.gradle.kts
  - providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/anthropic/AnthropicTransport.kt
  - providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ChatCompletionsAttemptObserver.kt
  - providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ChatCompletionsProvider.kt
  - providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ChatDecoder.kt
  - providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ChatEncoder.kt
  - providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ChatErrors.kt
  - providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ChatMessageEncoder.kt
  - providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ChatModels.kt
  - providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ChatResponseParts.kt
  - providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ChatSchemaStrip.kt
  - providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ChatStrict.kt
  - providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ChatTransport.kt
  - providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ChatVendor.kt
  - providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/chat/OpenAiModelRules.kt
  - providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/http/SafeFields.kt
  - providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/schema/OptionalProperties.kt
  - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/ProvidersApiShapeTest.kt
  - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ChatAbsentOptionalTest.kt
  - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ChatCanaryTest.kt
  - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ChatCancellationTest.kt
  - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ChatCaptureCallPlanTest.kt
  - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ChatCaptureRunTest.kt
  - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ChatCompletionsLiveCaptureTest.kt
  - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ChatDecoderTest.kt
  - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ChatEncoderTest.kt
  - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ChatErrorMapTest.kt
  - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ChatFixtures.kt
  - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ChatGoldenReplayTest.kt
  - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ChatGoldenSanitizer.kt
  - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ChatGoldenSanitizerTest.kt
  - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ChatMalformedKeyTest.kt
  - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ChatMessageEncoderTest.kt
  - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ChatModelsTest.kt
  - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ChatPipelineRetryTest.kt
  - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ChatRequestFixtures.kt
  - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ChatRetryTest.kt
  - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ChatStrictTest.kt
  - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ChatTimeoutTest.kt
  - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ChatTransportTest.kt
  - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/chat/OpenAiModelRulesTest.kt
  - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/http/SafeFieldsTest.kt
  - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/parity/TokenParityTest.kt
  - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/schema/OptionalPropertiesTest.kt
findings:
  critical: 0
  warning: 4
  info: 6
  total: 10
status: resolved
---

# Phase 5: Code Review Report

**Reviewed:** 2026-10-01
**Depth:** standard
**Files Reviewed:** 44
**Status:** issues_found

## Summary

The Chat Completions transport is in good shape on the stated priorities. I found no blocker.

- **Secrets:** Every internal type that holds server data has a redacting `toString`. Error bodies are reduced to a status, two validated identifiers and a refined reason. `isHeaderSafe` runs before OkHttp ever sees the key, and the derived client drops interceptors and redirects. The canary and malformed-key tests cover these paths.
- **OkHttp floor:** No `okhttp3.internal` or 5.x-only API appears in main. The matrix wiring in `build.gradle.kts` is sound.
- **Never-throw:** `BoundModel.complete` wraps `provider.complete` in `guarded(...)`, so an unexpected runtime exception from request building cannot escape the pipeline.

The real defects are in the error map, which was built from derived (invented) text and is now contradicted by the live capture, and in one unverified accounting assumption. Details follow.

No new `@Suppress` was introduced in the files reviewed.

## Warnings

### WR-01: OpenRouter "no endpoints" 404 decodes as model_not_found (live-confirmed, not fixed)

**File:** `providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ChatErrors.kt:41` (used at `:151`)

**Issue:** `NO_ENDPOINTS_MARKER = "No endpoints found that support"` does not match the live OpenRouter text "No endpoints found that can handle the requested parameters" (plan 05-12, probe R5). That 404 therefore falls through to the status table and becomes `FailureReason.ModelNotFound` instead of `ModelUnsupported`.

This is the exact failure `require_parameters` produces when a forced tool call cannot be routed. An app sees "model not found" and may blame its model id, not the unsupported parameter.

The unit test `ChatErrorMapTest.noEndpointsForAParameterIsModelUnsupportedButOtherNotFoundIsNot` (`ChatErrorMapTest.kt:107-111`) and the derived fixture `derived.json` `openrouter_404_no_endpoints` (line 673) both pin the invented "support the provided 'tool_choice' value" wording. They pass while production is wrong. The manifest row still says "cited ... not reproducible live".

Do not widen the marker to the bare prefix "No endpoints found". OpenRouter also answers an unknown model id with a 404 "No endpoints found for <model>", and that one must stay `model_not_found`.

**Fix:**
```kotlin
// Both live shapes ("that support ...", "that can handle ..."); "No endpoints found for <model>" stays not-found.
private const val NO_ENDPOINTS_MARKER = "No endpoints found that"
```
Prefer keying off `error.metadata.failed_routing_step` if the capture shows it. Replace the derived and test wording with the live text. The captured R5 body was not committed because it was a 404, so add a sanitized derived copy of the real text. Add a negative test for "No endpoints found for foo/bar".

### WR-02: The Responses-endpoint marker probably does not match OpenAI's real wording

**File:** `providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ChatErrors.kt:40,147-152`

**Issue:** The marker is the substring `"/v1/responses"` (with a leading slash). The only fixtures are derived, built from a research-table message about `gpt-6-astra`, and never captured live. OpenAI's documented Chat Completions refusal for Responses-only models reads, from memory, "This model is only supported in v1/responses and not in v1/chat/completions". That text has no slash before `v1`, so `contains("/v1/responses")` is false and the 400 decodes as `http_error`. I could not verify this offline, so confirm it.

This is the same class of bug as WR-01: the matcher was validated only against text the author wrote.

**Fix:**
```kotlin
private const val RESPONSES_ENDPOINT_MARKER = "v1/responses"   // matches "/v1/responses" and "in v1/responses"
```
Keep the 400/404 status guard. Add a test with the wording above. Ideally capture one real 400 in the Phase 10 smoke.

### WR-03: Cache-write accounting rests on an unverified assumption that the clamp can hide

**File:** `providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ChatResponseParts.kt:71-89`

**Issue:** `inputUncached = prompt_tokens - cached - cache_write` assumes `prompt_tokens` includes `cache_write_tokens`. Plan 05-12 records A5 as "not observed", so this is still open. `TokenParityTest` (header comment at `:51`) asserts the assumption as if it were fact.

If a router reports `prompt_tokens` without the write tokens (Anthropic's native `input_tokens` excludes them), the code does the following:
- It clamps `cacheWrite` to `prompt - cacheRead`. The reported total is then too low, and the clamp also silently discards the evidence of the mismatch.
- The effect lands in `tokensUsed` and therefore the token ceiling.

The Haiku route via OpenRouter is the most likely place for this to bite, and it reported `cache_write 0` in the capture, so nothing exercised it.

**Fix:** Before the tag, either capture a real Anthropic-via-OpenRouter cache-write response and pin it as a golden, or document the assumption in the public KDoc and the release notes. Consider adding a test that proves the clamp preserves the totals invariant `total <= prompt + completion`.

### WR-04: `openai/` prefix is treated as "an OpenAI model" for strict mode and caching

**File:** `providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ChatModels.kt:38-48,71`, `ChatEncoder.kt:93-98`

**Issue:** On OpenRouter, `family` is decided by the vendor prefix alone. Open-weight models also carry the `openai/` prefix (for example `openai/gpt-oss-*`) and match `GPT_PREFIX`. They receive:
- `strict: true` tool schemas (`routesToOpenAi`);
- `CachingMode.AUTOMATIC` with a 1024-token minimum;
- the OpenAI wire rules.

Combined with `provider.require_parameters = true` on forced calls (`ChatEncoder.kt:69`), a host that does not support strict tool schemas yields the very "No endpoints found" 404 from WR-01. The same 404 would then be mislabelled `model_not_found` under the current marker. This is inference from the code, not a captured case.

**Fix:** Restrict the OpenAI rules and strict mode to ids that match known OpenAI families (`GPT_PREFIX`/`O_SERIES` after the prefix), and let `openai/gpt-oss*` fall to `ModelCapabilities.UNKNOWN`. The app can still override through the capability table.

## Info

### IN-01: Reshape instruction attaches to the last user message, not the end of the conversation

**File:** `providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ChatEncoder.kt:133-141`

**Issue:** In a multi-turn tool loop the history ends with assistant and tool messages. `indexOfLast { role == user }` then appends "Call the X tool with your result." to an earlier user turn, so the closing instruction is no longer the last thing the model reads. This is documented as "closes the last user message", so it is a design edge, not a defect. It only matters for a routed model that cannot take a forced tool choice.

**Fix:** Either append the instruction to the final message regardless of role (tool results are strings), or add a note to the class KDoc that reshaping is a single-turn feature.

### IN-02: Near-duplicate transport plumbing between Anthropic and Chat transports

**File:** `ChatTransport.kt:37-41,112-118,127-135,170-175` and `AnthropicTransport.kt:42-47,124-130,161-203`

**Issue:** `refusalFor`, `notify`, `attempt`'s two catch arms and `ioFailure` are copied almost verbatim. The broad `catch (ignored: Exception)` in `notify` now exists twice. The project convention says the broad catch lives in one internal helper. The comment justifies it, and the `ignored` name keeps detekt quiet, but a third transport will copy it again.

**Fix:** Move `notify` (as `safeNotify(observer, block)`), `ioFailure` and the key vetting into `http/` and share them.

### IN-03: Public `@JvmInline value class` for the attempt kind freezes representation and mangles Java names

**File:** `providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ChatCompletionsAttemptObserver.kt:63-76`

**Issue:** A public value class fixes the underlying `String` forever. It also mangles the `kind` getter and the `INITIAL` and `TRANSIENT_RETRY` accessors for Java callers, such as `getKind-<hash>`. It mirrors `AnthropicAttemptKind`, so it is consistent. With the strictly-additive rule it is a one-way door, and Phase 10 should confirm it is acceptable before the Metalava `api.txt` is cut.

**Fix:** None required if the design is intentional. Otherwise use a regular class with a private constructor and `equals`/`hashCode`.

### IN-04: Orphaned derived case kept only because of test coupling

**File:** `providers/src/test/resources/golden/chat/responses/derived.json` (`openai_forced_log_food`) and `ChatGoldenReplayTest.kt:219-226`

**Issue:** The `line()` helper hard-codes `derived.json#openai_forced_log_food` against the real file, so the stale derived body had to stay after its manifest row was superseded (05-12 gap 5). It is dead data that could mislead.

**Fix:** Give the loader self-tests their own tiny derived object, then delete the orphan.

### IN-05: Wall-clock thresholds in cancellation tests

**File:** `providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ChatCancellationTest.kt:129-133,150-156,183-188`

**Issue:** The tests assert a join under 2 s, a deadline under 5 s and an idle wait under 2 s. They are real-time and can flake on a loaded CI, and the check runs the suite three times (the 4.12.0, 5.2.1 and 5.5.0 legs).

**Fix:** Widen the margins, or assert the structural outcome (cancellation seen, one request, zero running calls) and keep only the generous per-test `timeout`.

### IN-06: Sanitizer rewrites any `call_`, `gen-` or `chatcmpl-` token in message text, not just ids

**File:** `providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ChatGoldenSanitizer.kt:31,124-129`

**Issue:** `ID_IN_TEXT` is applied to every string value, so a user phrase or tool argument that starts a word with `gen-` or `call_` is rewritten to `...GOLDEN` in the committed golden. This is harmless for outcome replay, but the golden is no longer a faithful copy. Separately, the `sk-` and Bearer regexes are duplicated in `goldenHygieneViolations` (in `ChatGoldenReplayTest.kt`), so the two rule sets can drift.

**Fix:** Apply id rewriting only to the `id` fields and to `tool_call_id`, and share one regex holder between the sanitizer and the hygiene scan.

---

_Reviewed: 2026-10-01_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
