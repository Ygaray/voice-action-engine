---
phase: 05-openai-openrouter-transports
fixed_at: 2026-10-01T00:00:00Z
review_path: .planning/phases/05-openai-openrouter-transports/05-REVIEW.md
iteration: 1
findings_in_scope: 10
fixed: 9
skipped: 1
status: resolved
---

# Phase 5: Code Review Fix Report

**Fixed at:** 2026-10-01
**Source review:** .planning/phases/05-openai-openrouter-transports/05-REVIEW.md
**Iteration:** 1

**Summary:**
- Findings in scope: 10 (0 critical, 4 warning, 6 info; fix_scope all)
- Fixed: 9
- Skipped: 1 (IN-03, a documented acceptable-skip)

**Verification:** per-fix `:providers:detekt` plus the targeted tests, then a final `./gradlew check --offline` in the main checkout (no worktree; fixes were committed directly on main as instructed). It was BUILD SUCCESSFUL: detekt, the default `test` leg (OkHttp 4.12.0) and the `testOkhttp521` and `testOkhttp550` matrix legs, and the sample lint. `metalavaCheckCompatibility` is SKIPPED because no `api.txt` exists before the v1.0.0 cut. No `@Suppress` was added.

## Fixed Issues

### WR-01: OpenRouter "no endpoints" 404 decodes as model_not_found

**Files modified:** `providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ChatErrors.kt`, `providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ChatErrorMapTest.kt`, `providers/src/test/resources/golden/chat/responses/derived.json`, `providers/src/test/resources/golden/chat/responses/MANIFEST.tsv`
**Commit:** dafe2e5
**Applied fix:** `NO_ENDPOINTS_MARKER` is now `"No endpoints found that"`, which matches both the live R5 wording ("... that can handle the requested parameters") and the older "... that support ..." wording. "No endpoints found for <model>" does not contain it and stays `model_not_found`. The unit test now asserts the live wording and the old wording as `model_unsupported`, and adds the negative case "No endpoints found for foo/bar." as `model_not_found`. The derived fixture `openrouter_404_no_endpoints` uses the live wording, and the manifest note says where it comes from. The real R5 body was never committed, so the fixture is a derived copy of the live message text only.
Status: fixed: requires human verification (this is a matching-logic change, and the live wording comes from the 05-12 summary, not from a replayable capture).

### WR-02: The Responses-endpoint marker probably does not match OpenAI's real wording

**Files modified:** `providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ChatErrors.kt`, `providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ChatErrorMapTest.kt`
**Commit:** e096bdc
**Applied fix:** `RESPONSES_ENDPOINT_MARKER` is now `"v1/responses"` (no leading slash). It still matches "use /v1/responses" and now also matches "only supported in v1/responses and not in v1/chat/completions". This is a strict widening, and the 400/404 status guard is unchanged, so a 500 with the same text stays `http_error`. A test covers the bare wording at 400 and 500. The wording is the reviewer's recollection of OpenAI's message and was not verified live (no keys, no live calls), so a real 400 should still be captured in the Phase 10 smoke.
Status: fixed: requires human verification.

### WR-03: Cache-write accounting rests on an unverified assumption

**Files modified:** `providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ChatResponseParts.kt`, `providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/parity/TokenParityTest.kt`
**Commit:** f1cb06b
**Applied fix:** No behavior change, because the true wire behavior cannot be observed offline. The `decodeChatUsage` KDoc and the `TokenParityTest` header now say that `prompt_tokens` including `cache_write_tokens` is an unverified assumption (the live capture never saw a non-zero write). They also say that the clamp would under-count the total and hide a mismatch if a router reports it differently. The reviewer's suggested invariant test (`total <= prompt + completion` under the clamp) already exists in `ChatDecoderTest` (the cached 3000 > prompt 1000 case), so no new test was added. Still open for Phase 10: pin a real Anthropic-via-OpenRouter cache-write golden.

### WR-04: `openai/` prefix is treated as "an OpenAI model" for strict mode and caching

**Files modified:** `providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ChatModels.kt`, `providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ChatModelsTest.kt`
**Commit:** 58c9907
**Applied fix:** On a routed vendor, `openai/gpt-oss*` ids (after dropping any `:variant`) now resolve to the "other" family. They get `ModelCapabilities.UNKNOWN`, no `strict` tool schemas, and the routed default wire rules. Every other `openai/` id is unchanged. This is the narrow fix: it removes the known open-weight case without guessing at other hosts. An app can still override capabilities by exact model id. A new test covers `openai/gpt-oss-120b` and `openai/gpt-oss-20b:free`, plus a regression assertion for `openai/gpt-5.4-mini`.
Status: fixed: requires human verification (a classification-logic change).

### IN-01: Reshape instruction attaches to the last user message

**Files modified:** `providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ChatEncoder.kt`
**Commit:** 20b660d
**Applied fix:** Took the reviewer's documentation option. The `encodeChatRequest` KDoc now states that the closing line is written for a single-turn call and, in a multi-turn tool loop, closes an earlier user turn. Encoder bytes are unchanged.

### IN-02: Near-duplicate transport plumbing

**Files modified:** `providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/http/SafeNotify.kt` (new), `providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ChatTransport.kt`, `providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/anthropic/AnthropicTransport.kt`
**Commit:** d8f0c42
**Applied fix:** The broad `catch (ignored: Exception)` for the observer callback now lives once, in `http/notifyQuietly`. Both transports call it from their `notify`. Behavior is identical. `ioFailure` and the credential vetting were deliberately left in place: they differ per vendor (the provider-id check, and the Anthropic-only reshape/`Attempted` fields), so sharing them would need a bigger abstraction than this fix warrants. This is a new internal file, listed here per the new-file rule.

### IN-04: Orphaned derived case kept only because of test coupling

**Files modified:** `providers/src/test/resources/golden/chat/responses/derived.json`, `providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ChatGoldenReplayTest.kt`
**Commit:** 49773e3
**Applied fix:** The loader self-test helper `line()` now points at the live manifest row's derived object `openai_forced_log_food_stop`, and the orphaned `openai_forced_log_food` object was deleted from `derived.json`. The golden replay tests pass.

### IN-05: Wall-clock thresholds in cancellation tests

**Files modified:** `providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ChatCancellationTest.kt`
**Commit:** 6247b08
**Applied fix:** The join and idle bounds went from 2 s to 10 s, and the deadline bound from 5 s to 15 s, with a comment. The 30 s per-test timeout stays, and the structural assertions (one cancellation, one request, nothing committed) are unchanged.

### IN-06: Sanitizer and hygiene scan can drift

**Files modified:** `providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ChatGoldenSanitizer.kt`, `providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ChatGoldenReplayTest.kt`
**Commit:** df02729
**Applied fix:** The second half of the finding is done. The `sk-`, Bearer, id and GOLDEN-tail regexes are now defined once (internal, in the sanitizer file) and `goldenHygieneViolations` uses them, so the two rule sets cannot drift. The first half is not applied; see the note below.

## Skipped Issues

### IN-03: Public `@JvmInline value class` for the attempt kind

**File:** `providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ChatCompletionsAttemptObserver.kt:63-76`
**Reason:** Documented acceptable-skip. The review itself says "None required if the design is intentional". The type mirrors `AnthropicAttemptKind` on purpose, and changing the public representation of one transport but not the other would break that consistency. The call is a Phase 10 decision: confirm it before the Metalava `api.txt` is cut. No code change is possible now without making the two transports diverge.
**Original issue:** A public value class fixes the underlying `String` forever and mangles the Java accessor names.

## Notes

- IN-06, first half (rewrite ids only in `id` and `tool_call_id` fields): deliberately not applied. The hygiene scan checks the whole file text, so an id-shaped token in message text would make the sanitizer refuse the body. Restricting the rewrite would trade a faithfulness nit for refused captures. Revisit only if a capture needs the exact text.
- Open follow-ups for Phase 10 (not fixable offline): a real OpenAI Responses-only 400 (WR-02), a real cache-write usage response through OpenRouter (WR-03), and the IN-03 public-shape confirmation.

---

_Fixed: 2026-10-01_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 1_
