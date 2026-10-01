---
phase: 04-anthropic-transport-okhttp-matrix
verified: 2026-10-01T02:00:00Z
status: passed
score: 5/5 must-haves verified
covered_files:
  - ".planning/REQUIREMENTS.md"
  - ".planning/phases/04-anthropic-transport-okhttp-matrix/04-01-PLAN.md"
  - ".planning/phases/04-anthropic-transport-okhttp-matrix/04-01-SUMMARY.md"
  - ".planning/phases/04-anthropic-transport-okhttp-matrix/04-02-PLAN.md"
  - ".planning/phases/04-anthropic-transport-okhttp-matrix/04-02-SUMMARY.md"
  - ".planning/phases/04-anthropic-transport-okhttp-matrix/04-03-PLAN.md"
  - ".planning/phases/04-anthropic-transport-okhttp-matrix/04-03-SUMMARY.md"
  - ".planning/phases/04-anthropic-transport-okhttp-matrix/04-04-PLAN.md"
  - ".planning/phases/04-anthropic-transport-okhttp-matrix/04-04-SUMMARY.md"
  - ".planning/phases/04-anthropic-transport-okhttp-matrix/04-05-PLAN.md"
  - ".planning/phases/04-anthropic-transport-okhttp-matrix/04-05-SUMMARY.md"
  - ".planning/phases/04-anthropic-transport-okhttp-matrix/04-06-PLAN.md"
  - ".planning/phases/04-anthropic-transport-okhttp-matrix/04-06-SUMMARY.md"
  - ".planning/phases/04-anthropic-transport-okhttp-matrix/04-07-PLAN.md"
  - ".planning/phases/04-anthropic-transport-okhttp-matrix/04-07-SUMMARY.md"
  - ".planning/phases/04-anthropic-transport-okhttp-matrix/04-08-PLAN.md"
  - ".planning/phases/04-anthropic-transport-okhttp-matrix/04-08-SUMMARY.md"
  - "core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/provider/ModelCapabilities.kt"
  - "core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/transcript/ModelRequest.kt"
  - "providers/build.gradle.kts"
  - "providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/anthropic/AnthropicAttemptObserver.kt"
  - "providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/anthropic/AnthropicDecoder.kt"
  - "providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/anthropic/AnthropicEncoder.kt"
  - "providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/anthropic/AnthropicErrors.kt"
  - "providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/anthropic/AnthropicMessageEncoder.kt"
  - "providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/anthropic/AnthropicModels.kt"
  - "providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/anthropic/AnthropicProvider.kt"
  - "providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/anthropic/AnthropicStrict.kt"
  - "providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/anthropic/AnthropicTransport.kt"
  - "providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/http/CallAwait.kt"
  - "providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/http/CleanClient.kt"
  - "providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/http/OneShotBody.kt"
  - "providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/http/RetryPolicy.kt"
  - "providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/http/SafeFields.kt"
  - "providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/schema/OptionalProperties.kt"
  - "providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/OkHttpVersionGuardTest.kt"
  - "providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/ProvidersApiShapeTest.kt"
  - "providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/anthropic/AnthropicCanaryTest.kt"
  - "providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/anthropic/AnthropicCancellationTest.kt"
  - "providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/anthropic/AnthropicDecoderTest.kt"
  - "providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/anthropic/AnthropicEncoderTest.kt"
  - "providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/anthropic/AnthropicErrorMapTest.kt"
  - "providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/anthropic/AnthropicFixtures.kt"
  - "providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/anthropic/AnthropicForcedToolTest.kt"
  - "providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/anthropic/AnthropicLiveCaptureTest.kt"
  - "providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/anthropic/AnthropicMalformedKeyTest.kt"
  - "providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/anthropic/AnthropicModelsTest.kt"
  - "providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/anthropic/AnthropicPipelineRetryTest.kt"
  - "providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/anthropic/AnthropicRetryTest.kt"
  - "providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/anthropic/AnthropicStrictTest.kt"
  - "providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/anthropic/AnthropicTimeoutTest.kt"
  - "providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/anthropic/AnthropicTransportTest.kt"
  - "providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/http/CallAwaitTest.kt"
  - "providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/http/CleanClientTest.kt"
  - "providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/http/RetryPolicyTest.kt"
  - "providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/http/SafeFieldsTest.kt"
covered_digest: "v1:sha256:2a48d2ca8b3ff9ffe077e07a6c1241a40ad3c2b511c6eba1ee094f7eaa730d7f"
behavior_unverified: 0
overrides_applied: 0
unverified_prohibitions:
  - statement: "No new @Suppress (plan 04-02, 04-04, 04-05, 04-07, 04-08 prohibitions); CLAUDE.md stack note says the never-throw collapse is the repo's only justified @Suppress"
    verification: judgment
    status: resolved
    note: "Post-verification (2026-10-01, commit a6def62): both review-fix @Suppress entries removed; the catch blocks use the ignored-name convention that detekt already allows, and ./gradlew check is green with zero @Suppress in providers/core main. No owner decision needed."
human_verification: []
deferred_gate2:
  - "Phase 10 live smoke: confirm WR-03 low-credit-balance 400 -> Billing and CR-01 accepted header-key character set against a real Anthropic account/key (registered in .planning/uat-pending/04-anthropic-transport-okhttp-matrix.md)"
---

# Phase 4: Anthropic Transport & OkHttp Matrix Verification Report

**Phase Goal:** A consumer's commands reach Anthropic over a cache-correct, cancellation-safe transport that runs green on the consumer's own OkHttp version (4.12 or 5.x) and leaks no secret anywhere.
**Verified:** 2026-10-01
**Status:** human_needed (all five roadmap success criteria VERIFIED; one flagged judgment-tier prohibition and one live-confirmation item for the owner; no gaps)
**Re-verification:** No, initial verification

## Goal Achievement

### Observable Truths (ROADMAP success criteria, the contract)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | (A1) `:providers` compiles against OkHttp 4.12.0 (plain `api` floor); the same compiled tests pass on 4.12.0 / 5.2.1 / 5.5.0 runtime classpaths inside `check`, okhttp + mockwebserver swapped together, guard proves each leg | VERIFIED | `providers/build.gradle.kts`: `api(libs.okhttp)` plain; legs `testOkhttp521`/`testOkhttp550` built from resolvable classpaths with explicit JVM attributes, `eachDependency` swaps `okhttp` and `mockwebserver` together, both `check.dependsOn`. I cleaned and re-ran the three test tasks (restored from build cache, identical inputs): 144 tests per leg, 0 failures, 0 skipped, all 19 classes present on each leg. Guard output per leg: `OKHTTP_RUNTIME=4.12.0 / 5.2.1 (okhttp-jvm) / 5.5.0 (okhttp-jvm)` and `MOCKWEBSERVER_RUNTIME=mockwebserver-<same version>.jar`. `verifyOkHttpCompileFloor` is in the `check` graph and passed. `verify-negative-controls.sh`: 0 failures, including "floor raised -> red" and guard-rejects-wrong-version on all three legs. |
| 2 | Exactly one `cache_control: ephemeral` on the last system block, none on messages; same command twice gives byte-identical tools+system prefix; en/es, date, transcript leave prefix bytes unchanged; usage maps to `{inputUncached, cacheRead, cacheWrite, output}` | VERIFIED | `AnthropicEncoder.kt`: fixed key order model, max_tokens, tools, tool_choice, system, messages; tools sorted by name, schema bytes untouched; breakpoint only on `encodeSystem` (or last tool when system blank); `conversationTail` ignored. `AnthropicEncoderTest` (11 tests) asserts count of `cache_control` == 1, byte-array equality for double-encode, `substringBefore("\"messages\":")` equality between es and en requests with different date/transcript, key order. `AnthropicDecoder.decodeUsage` maps the four buckets; `AnthropicDecoderTest.usageMapsToTheFourBucketsAndMissingCountsAreZero`. Real-world corroboration: `evidence/live-capture.txt` (Haiku 4.5, 4 calls): `cache_write:6330` then `cache_read:6330` on the repeat. |
| 3 | On the specific forced-tool 400 the transport retries once with auto + strict + instruction; no-tool-call reply maps to `NoToolCall`; transient 429/5xx/timeouts retry at most once; a retried call causes no duplicate tool execution or `CommitSink` commit | VERIFIED | `AnthropicTransport.rejectedForcedTool` requires forced + 400 + message mentions `tool_choice`; `planResend` enforces one shared budget of `MAX_REQUESTS = 3` and a single transient retry; reshape adds `auto`, strict on eligible tools, and the closing instruction (`AnthropicEncoder.withInstruction`). `decodeAnthropicResponse(toolRequired)` returns `NoToolCall` for end_turn without tool_use. Tests: `AnthropicForcedToolTest` (18, incl. "no combination sends a fourth request", "nothing remembered between calls"), `AnthropicRetryTest` (21: retry set, retry-after cap, spend-cap not retried, OkHttp never replays, positive control that a replayable body *would* be replayed). Duplicate-commit proof: `AnthropicPipelineRetryTest` runs `commandPipeline` with the real provider, 529 or disconnect-after-request then success, asserts `server.requestCount == 2`, `mutation.applyCount == 1`, `sink.actions.size == 1`, one turn. |
| 4 | Cancelling mid-call cancels the HTTP call and closes any late response; no logging interceptors; fixed HTTPS base URL (test-only override) with `anthropic-version 2023-06-01`; bodies read with `body?.string()` | VERIFIED | `CallAwait.kt`: `invokeOnCancellation { cancel() }`, late response returns early and `finally { body?.close() }`, `body?.string()`. `CleanClient.kt` clears interceptors and network interceptors, replaces event listener/authenticator/proxyAuthenticator/cookie jar, disables redirects. `AnthropicProvider`: `PRODUCTION_BASE_URL = https://api.anthropic.com/`, `baseUrl` is an `internal` builder member, `build()` rejects non-https unless loopback. `AnthropicTransport`: `API_VERSION = "2023-06-01"`. Tests: `CallAwaitTest` (8, late response closed unread, resumes nothing), `AnthropicCancellationTest.cancellingTheCommandWhileTheCallIsInFlight...` (real sockets: `cancelAndJoin` fast, dispatcher `runningCallsCount()` reaches 0, sink empty) and `anEngineDeadline...IsATimeoutAndCancelsTheCall`, `CleanClientTest` (app hooks never run, 307 not followed, other host sees nothing). |
| 5 | Canary through pipeline + Anthropic transport (known key, transcript, tool args, tool_result, provider error body): none appears in trace, events, any `toString()` or failure message; failures carry only HTTP status and `error.type` | VERIFIED | `AnthropicCanaryTest` (8 tests, on all three legs): positive controls assert the server received the key header and the transcript/system/tool-argument/tool-result canaries; sweep covers outcome, trace, attempts, turns, usage, pipeline events, commit sink, every `ProviderRequest`/`ModelResult`/`ModelResponse`/message/part/native replay/credential/provider/builder/attempt `toString`, requires >= 25 distinct swept values. Failure legs: echoing error bodies (400/401/429/500), hostile `error.type`/request id (dropped to null), non-JSON 2xx, non-object tool input, dropped connection. `AnthropicErrors.parseAnthropicError` stores only status, `safeToken(type)`, `safeRequestId`, and three booleans; body/message discarded. CR-01 fix: a header-illegal key is answered `Auth` before OkHttp can quote it (`AnthropicMalformedKeyTest`). |

**Score:** 5/5 truths verified (0 present-but-behavior-unverified, 0 overrides)

Behavior-dependent truths (cancellation, no-duplicate-commit, cleanup of late responses, single retry budget) each have a passing behavioral test that exercises the invariant, so none is left PRESENT_BEHAVIOR_UNVERIFIED.

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `core/.../provider/ModelCapabilities.kt` | additive `supportsForcedToolChoice` (default true) | VERIFIED | in constructor, `toBuilder`, equals, hashCode, toString, Builder |
| `providers/.../anthropic/AnthropicModels.kt` | verified table, exact-id | VERIFIED | four rejecting ids, Haiku 4.5 (4096), unknown default; no id set elsewhere; no model id in `core/src/main` |
| `providers/.../anthropic/AnthropicProvider.kt` | `AnthropicProvider { }` builder | VERIFIED | public builder: httpClient, call/read timeouts (60 s default, non-positive throws), attemptObserver; internal base URL, dispatcher, sleep, caps |
| `.../AnthropicEncoder.kt`, `AnthropicMessageEncoder.kt` | byte-stable encoder, tool_result/is_error, native replay | VERIFIED | |
| `.../AnthropicDecoder.kt` | usage, stop reasons, malformed, NoToolCall | VERIFIED | |
| `.../AnthropicErrors.kt` | status-first table, spend limits, parse-and-discard | VERIFIED | no new `FailureReason` leaf |
| `.../AnthropicTransport.kt` | retry loop, reshape, budget, observer | VERIFIED | |
| `.../AnthropicStrict.kt`, `schema/OptionalProperties.kt` | strict only without optional properties | VERIFIED | |
| `.../http/CallAwait.kt`, `CleanClient.kt`, `OneShotBody.kt`, `RetryPolicy.kt`, `SafeFields.kt` | shared plumbing | VERIFIED | all `internal` |
| `AnthropicAttemptObserver.kt` | ids-only public observer | VERIFIED | `fun interface`, value-class kind, no data class/enum |
| `providers/build.gradle.kts` | legs, `*Live*` exclusion, opt-in `liveAnthropicCapture` | VERIFIED | `liveAnthropicCapture` not a `check` dependency, `onlyIf VAE_LIVE_ANTHROPIC == 1` |

### Key Link Verification

| From | To | Via | Status |
|------|----|-----|--------|
| `AnthropicProvider.complete` | `AnthropicTransport.send` | direct call; `withContext(ioDispatcher)` | WIRED |
| `AnthropicTransport.attempt` | `cleanClient` -> `Call.await` | `client.newCall(request).await()` with `OneShotJsonBody(encodeAnthropicRequest(...))` | WIRED |
| `interpret` | `decodeAnthropicResponse` / `parseAnthropicError` | success / non-2xx branches | WIRED |
| `PipelineBuilder.capabilities` override | `ProviderRequest.capabilities.supportsForcedToolChoice` | `needsReshape`, `encodeToolChoice` read only the request's capabilities | WIRED |
| `commandPipeline { provider(AnthropicProvider{}) }` | strategy `session.model().complete` | exercised end to end in `AnthropicPipelineRetryTest`, `AnthropicCanaryTest`, `AnthropicCancellationTest` | WIRED |

### Data-Flow Trace (Level 4)

Not a UI phase. Request data flows `ModelRequest` -> encoder -> server (positive controls in the canary test read the received bytes); response flows server -> `HttpReply` -> decoder -> `ModelResponse` with real usage numbers. Live evidence shows real usage values (422/6330/33). FLOWING.

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| Whole build green | `./gradlew check --offline` | BUILD SUCCESSFUL, exit 0 (test tasks up to date from the same inputs) | PASS |
| Three-leg test run | `:providers:cleanTest* :providers:test :providers:testOkhttp521 :providers:testOkhttp550 --offline` | BUILD SUCCESSFUL (build-cache restore); XML: 144 tests, 0 fail, 0 skip per leg | PASS |
| API surface sealed | `scripts/review-api-surface.sh --expect-sealed-complete` | `API SURFACE OK ... classes=161` | PASS |
| Repo hygiene | `scripts/verify-repo-hygiene.sh` | `HYGIENE OK` | PASS |
| API dump proof | `scripts/verify-api-dump.sh` | `API DUMP PROOF OK`, real tree untouched | PASS |
| Negative controls | `scripts/verify-negative-controls.sh` | `negative-control failures: 0` | PASS |
| Live Anthropic wire shape | `evidence/live-capture.txt` (recorded 2026-10-01, Haiku 4.5, 4 of 6 allowed calls) | forced call ok with request id; repeat read 6330 cached tokens; reshaped (auto+strict+instruction) accepted; bad key -> auth / 401 / `authentication_error`. I did not re-run it (no live calls allowed). | PASS (recorded) |

### Probe Execution

Step 7c: no `probe-*.sh` declared by any Phase 4 plan and none under `scripts/*/tests`. SKIPPED.

### Requirements Coverage

Every ID in the phase's roadmap line appears in at least one PLAN `requirements:` field; no orphan.

| Requirement | Source Plan(s) | Status | Evidence |
|-------------|----------------|--------|----------|
| PROV-04 | 04-02, 04-03, 04-04 | SATISFIED | one breakpoint on last system block, `anthropic-version 2023-06-01`, fixed https URL, cancel-safe `Call.await` closing late responses |
| PROV-05 | 04-03 | SATISFIED | `encodingTheSameRequestTwiceOrAnEqualOneGivesIdenticalBytes`, tools sorted |
| PROV-06 | 04-03 | SATISFIED | `languageDateAndTranscriptNeverTouchTheBytesBeforeTheMessages` |
| PROV-07 | 04-01, 04-06 | SATISFIED | table with four rejecting ids, app override reaches the bound model, reactive 400, `NoToolCall`, no `thinking` key sent |
| PROV-09 | 04-05 | SATISFIED | one retry, <= 3 requests, no duplicate apply/commit (pipeline test) |
| PROV-11 | 04-02, 04-03 | SATISFIED | `cleanClient`, `body?.string()` |
| PROV-13 | 04-02, 04-03, 04-04 | SATISFIED | 60 s default, per-provider override, InterruptedIO -> Timeout, engine deadline -> Timeout. Per-strategy configuration lands with the strategies (Phases 7 and 9); the provider-level knob is what this phase owns |
| BLD-06 | 04-07, 04-08 | SATISFIED | see truth 1 |
| TEL-04 | 04-04, 04-08 | SATISFIED | see truth 5 |
| PROV-12 (Anthropic leg, mapped to Phase 5) | 04-06 | PROVEN for Anthropic | `AnthropicStrictTest` (12), including `anOmittedOptionalParameterIsSentUnchangedAndArrivesAbsent`; strict added only in the reshape and only for optional-free schemas in the Anthropic subset |

REQUIREMENTS.md still lists these nine rows as `Pending` and the ROADMAP Phase 4 line is still `[ ]`; that bookkeeping belongs to the orchestrator's phase-close step and is not a code gap.

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| `AnthropicTransport.kt` | 126 | `@Suppress("TooGenericExceptionCaught")` on `notify` | Warning (flagged prohibition) | Catches `Exception` around the app's observer so a throwing observer cannot discard a billed answer. Non-suspend, so no cancellation signal passes through. Justification in a comment; detekt passes. Contradicts the plans' "no new @Suppress". |
| `CallAwait.kt` | 45 | `@Suppress("TooGenericExceptionCaught")` on `onResponse` | Warning (flagged prohibition) | Catches `RuntimeException` so a non-IO failure while reading the body fails the awaiter instead of hanging it; message deliberately not copied. Same note. |
| (scan) | - | TBD/FIXME/XXX/TODO/HACK in `providers/src`, `core/src/main` | none found | - |
| (scan) | - | planning ids (T-xx-xx, WR-, D-xx, PROV-, Phase N) in source | none found | - |
| (scan) | - | `runCatching`, `println`, `android.util.Log`, `okhttp3.internal`, `mockwebserver3` in main | none (also enforced by `scanBannedConstructs`, passing) | - |

The `runCatching` in `AnthropicCanaryTest` is test code, outside the scanner's library scope.

### Notes (non-blocking)

- Post-plan `core` change: the review fix (`d5a1147`) edited the KDoc of `ModelRequest.kt` (`conversationTail` may be ignored by a provider). Documentation only, no signature change; it is outside plan 04-01's "one core field only" wording but is a legitimate review-driven doc fix.
- `04-VALIDATION.md` frontmatter still says `status: draft`, `nyquist_compliant: false`; the file states these are finalizer-owned, so they were not expected to be set at plan time.
- Metalava compatibility check is `SKIPPED` and no `api.txt` exists in the tree by design (dumps are committed only at the v1.0.0 cut; `verify-api-dump.sh` proves the mechanism on an isolated copy).
- Running the real 5.x `okhttp-android` variant on a device is the Phase 10 `:sample` Gate-1 job, not this phase.
- Deferred: none of the findings maps to a later phase except the live confirmation items, which are noted for Phase 10 above.

### Human Verification Required

None blocking. The two review-fix `@Suppress` entries were removed after verification (commit `a6def62`; detekt accepts the `ignored`-named catches, `./gradlew check` green, zero `@Suppress` in `providers`/`core` main), so the owner decision is moot. The live confirmation of WR-03 (low-credit 400 -> Billing) and CR-01 (header-key character set) is deferred to the Phase 10 live smoke via the Gate-2 ledger fragment; it is not part of the roadmap success criteria.

### Gaps Summary

No gaps. All five roadmap success criteria and all nine requirement IDs are backed by code I read and tests I observed passing on all three OkHttp legs, plus the gate scripts and the recorded live capture. Status is `passed`: the only flagged item (two review-fix `@Suppress` annotations) was resolved by removing them after verification, and the live confirmation of two review-fix behaviors is a Gate-2 obligation for Phase 10.

---

_Verified: 2026-10-01_
_Verifier: Claude (gsd-verifier)_
