---
phase: "5"
slug: openai-openrouter-transports
status: secured
threats_open: 0
asvs_level: 1
audited_head: 89ea058d85b0b37c2ab8f2947aaa28e42c5c0080
created: "2026-10-01"
---

# Phase 5 - Security

> Per-phase security contract: threat register, accepted risks, and audit trail.

---

## Trust Boundaries

| Boundary | Description | Data Crossing |
|----------|-------------|---------------|
| app -> engine | App-authored tool schemas, model ids, app OkHttpClient, observer | schemas, model ids, hooks |
| engine -> api.openai.com / openrouter.ai (HTTPS) | Fixed https hosts over a cleaned OkHttp client | Bearer key, transcript, tool args and results |
| provider -> library | Untrusted 2xx and error bodies | error envelopes, metadata.raw, refusal and reasoning text, usage |
| library -> app | Failures, traces, events, toString, decoded tool args | typed reasons, tool args that drive mutations |
| network -> library | Drops, delays, retries | retry-after, transient failures |
| test resources -> repository | Committed and published goldens | sanitized bodies |
| key store -> test process, capture output -> repository, CI -> network | Opt-in live capture outside check | env keys via with-test-keys, raw and sanitized bodies |
| agent -> human approval | Gate for the live capture | D-16 relay |

---

## Threat Register

Audited 2026-10-01 by gsd-security-auditor against the post-review-fix tree (register authored at plan time from 05-01..05-12 threat models; T-05-01 to T-05-46, IDs unique across plans). All 46 CLOSED (45 mitigate, 1 accept). Paths: P = providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers, T = providers/src/test/kotlin/.../providers, R = providers/src/test/resources/golden/chat, B = providers/build.gradle.kts.

| Threat ID | Category | Component | Severity | Disposition | Mitigation / evidence | Status |
|-----------|----------|-----------|----------|-------------|-----------------------|--------|
| T-05-01 | Tampering | hasOptionalProperties walk | high | mitigate | P/schema/OptionalProperties.kt walks properties, items, prefixItems, anyOf, oneOf, allOf, $defs, definitions, additionalProperties; one test per keyword in OptionalPropertiesTest | closed |
| T-05-02 | Tampering | app strict=true override | high | mitigate | P/chat/ChatStrict.kt:71-72 engine wins; ChatStrictTest, ChatEncoderTest:148-155 | closed |
| T-05-03 | Tampering | schema strip | medium | mitigate | P/chat/ChatSchemaStrip.kt:189-210 schema positions only; ChatStrictTest keyword-named property and enum/const/default cases | closed |
| T-05-04 | DoS | huge or deep schema | low | accept | ChatStrict.kt size limits make oversized schema ineligible; AR-05-01 | closed |
| T-05-05 | DoS | tools request to Responses-only model | medium | mitigate | OpenAiModelRules.kt, ChatModels.kt:88 supportsTools=false; zero provider calls (ChatModelsTest, ChatTransportTest); reactive marker ChatErrors.kt | closed |
| T-05-06 | Tampering | hostile model id | low | mitigate | ChatModels.kt:43-54 string split with `other` fallback | closed |
| T-05-07 | Spoofing | normalization vs wire id | medium | mitigate | ChatEncoder.kt:60 sends call.model unchanged; override keyed on exact id | closed |
| T-05-08 | Info disclosure | error text or metadata.raw echo | high | mitigate | ChatErrors.kt ChatErrorInfo has no text field; ChatErrorMapTest, ChatCanaryTest | closed |
| T-05-09 | Tampering | hostile error type or request id | medium | mitigate | http/SafeFields.kt validators; SafeFieldsTest, ChatCanaryTest | closed |
| T-05-10 | DoS | quota 429 retried / 200 envelope as success | medium | mitigate | ChatErrors.kt quota exclusion and envelope check; ChatErrorMapTest, ChatRetryTest | closed |
| T-05-11 | Info disclosure | key with newline reaching OkHttp | high | mitigate | SafeFields.kt:26, ChatTransport.kt:38-42, shared with AnthropicTransport; ChatMalformedKeyTest | closed |
| T-05-12 | Tampering | strict on a schema with optionals | high | mitigate | ChatEncoder.kt:95-100 effectiveChatStrict; ChatEncoderTest, ChatAbsentOptionalTest | closed |
| T-05-13 | Info disclosure | identity or metadata field in body | medium | mitigate | ChatEncoder.kt:59-74 fixed key allow-list; ChatEncoderTest:255-278 | closed |
| T-05-14 | Info disclosure | goldens with real data | low | mitigate | synthetic hand-written goldens; hygiene scan in ChatGoldenReplayTest | closed |
| T-05-15 | Tampering | response-only fields replayed as input | low | mitigate | ChatMessageEncoder.kt replay allow-list; ChatMessageEncoderTest | closed |
| T-05-16 | Tampering | fabricated or truncated tool call | high | mitigate | ChatResponseParts.kt object-only args, blank id/name malformed; ChatDecoder.kt length handling; ChatDecoderTest | closed |
| T-05-17 | Info disclosure | refusal, reasoning or error text in parts or exceptions | high | mitigate | ChatDecoder.kt:123-133 refusal yields empty parts; constant messages; ChatDecoderTest, ChatCanaryTest | closed |
| T-05-18 | Tampering | 200 error accepted as success | medium | mitigate | ChatDecoder.kt:87-92 envelope before choices; ChatDecoderTest, ChatRetryTest | closed |
| T-05-19 | DoS | cached-token double count / negative count | medium | mitigate | ChatResponseParts.kt:80-98 clamps; ChatDecoderTest, TokenParityTest (see O-2) | closed |
| T-05-20 | Tampering | hostile native finish reason or body id | low | mitigate | ChatDecoder.kt safeToken / safeRequestId; ChatDecoderTest | closed |
| T-05-21 | Info disclosure | cleartext or attacker base URL | high | mitigate | ChatVendor.kt fixed https; ChatCompletionsProvider.kt:96 https or loopback only; ChatTransportTest | closed |
| T-05-22 | Spoofing | key for the wrong vendor | medium | mitigate | ChatTransport.kt:39; ChatTransportTest not_configured, zero requests | closed |
| T-05-23 | Info disclosure | app hooks seeing key or bodies | high | mitigate | http/CleanClient.kt:28-42 interceptors, listeners, authenticators, redirects, cookies cleared; CleanClientTest, ChatTransportTest | closed |
| T-05-24 | Info disclosure | OkHttp header exception quoting the key | high | mitigate | ChatTransport.kt:38-42,89-94 refuses before building request; ChatMalformedKeyTest | closed |
| T-05-25 | DoS | retry storm | medium | mitigate | ChatTransport.kt MAX_REQUESTS=2; RetryPolicy retry-after cap; ChatRetryTest | closed |
| T-05-26 | Repudiation | finish_reason disagreement invisible | low | mitigate | attempt observer carries finishReason and toolCalls; ChatTransportTest, ChatRetryTest | closed |
| T-05-27 | Info disclosure | key or real id in a committed golden | high | mitigate | ChatGoldenReplayTest hygiene scan walks all of golden/chat in check and extra legs; independent scan of 8 captured files clean | closed |
| T-05-28 | Repudiation | captured row without a file | medium | mitigate | ChatGoldenReplayTest manifest checks; all 8 captured rows resolve | closed |
| T-05-29 | Tampering | expectations edited to fit a bug | medium | mitigate | 05-07 prohibition and summary; WR-01/WR-02 changed marker and test together from live wording | closed |
| T-05-30 | Tampering | retry re-running a tool or commit | high | mitigate | retry only inside the transport; ChatPipelineRetryTest (2 requests, 1 apply, 1 commit), ChatRetryTest | closed |
| T-05-31 | DoS | unbounded retries or waits | medium | mitigate | MAX_REQUESTS and retry-after cap; ChatRetryTest, ChatTransportTest | closed |
| T-05-32 | Info disclosure | malformed key echoed | high | mitigate | same code as T-05-24; ChatMalformedKeyTest leak assertions | closed |
| T-05-33 | DoS | cancelled command leaves HTTP running | low | mitigate | http/CallAwait.kt invokeOnCancellation cancel; ChatCancellationTest | closed |
| T-05-34 | DoS | cached tokens double-counted | medium | mitigate | ChatResponseParts.kt; TokenParityTest (see O-2) | closed |
| T-05-35 | Elevation | cached tokens dropped | medium | mitigate | same evidence as T-05-34 | closed |
| T-05-36 | Tampering | omitted optional filled with defaults | high | mitigate | decoder never consults a schema; ChatAbsentOptionalTest parameterized over both vendors | closed |
| T-05-37 | Info disclosure | key, transcript, args, reasoning in any output | high | mitigate | ChatCanaryTest both vendors with positive controls and distinct-value floor; no print/log/@Suppress/okhttp3.internal in main | closed |
| T-05-38 | Repudiation | a vendor silently skipped | medium | mitigate | parameterized per vendor, loader fails on zero rows; 411 tests per leg, 0 skipped (see O-6) | closed |
| T-05-39 | Info disclosure | check making a live call | high | mitigate | B excludes *Live* from test and legs; live task gated on VAE_LIVE_CHAT=1, upToDateWhen false; Assume in live class; phase-gate evidence | closed |
| T-05-40 | Info disclosure | key or id committed in a captured golden | high | mitigate | ChatGoldenSanitizer refuses bodies failing the hygiene scan; raw only under git-ignored build/live-chat/raw | closed |
| T-05-41 | DoS | runaway live spend | medium | mitigate | ChatCompletionsLiveCaptureTest ceilings 12 total / 6 per vendor, count incremented before send; call plan tests | closed |
| T-05-42 | Info disclosure | key printed in capture output | high | mitigate | capture prints ids, status, counts only; with-test-keys redactor; evidence free of key shapes | closed |
| T-05-43 | Elevation | capture run without Yahir's OK | high | mitigate | blocking decision gate in 05-12 with defer default; resolution recorded in evidence and 05-12-SUMMARY; code gate in B and live class (see O-3) | closed |
| T-05-44 | Info disclosure | key or id in golden or evidence | high | mitigate | same controls as T-05-40; evidence prints request_id=present/absent only | closed |
| T-05-45 | DoS | runaway spend | medium | mitigate | three cheap models, 11 calls, 12 ceiling; evidence shows 11 requests over two runs | closed |
| T-05-46 | Tampering | expectations edited to fit a live surprise | medium | mitigate | capture commits touch only goldens and evidence; surprises returned as gaps; run 2 ended red on unmet R2/R3 instead of being edited away | closed |

*Status: open · closed · open - below high threshold (non-blocking)*

---

## Accepted Risks Log

| Risk ID | Threat Ref | Rationale | Accepted By | Date |
|---------|------------|-----------|-------------|------|
| AR-05-01 | T-05-04 | Tool schemas are app-authored constants. Strict-eligibility size limits (5,000 properties, depth 10, 1,000 enum values, 120,000 characters) make an oversized schema ineligible for strict rather than rejected; recursion depth is bounded by the schema the app wrote. | plan disposition (accept) | 2026-10-01 |

*Accepted risks do not resurface in future audit runs.*

---

## Non-blocking observations (Phase 10 carries)

- O-1: the `v1/responses` marker in ChatErrors.kt has not been checked against a real OpenAI Responses-only 400 (primary control `supportsTools=false` is verified).
- O-2: `prompt_tokens` is assumed to include `cache_write_tokens`; no live capture saw a non-zero write. Exposure is small (routed Anthropic gets CachingMode.NONE).
- O-3: T-05-43 basis is Yahir's standing test-key policy (HANDOFF.md "Keys: UNBLOCKED (2026-09-30)") relayed by the orchestrator yahir-gsd-control-plane-f2 via the milestone master on 2026-10-01; the relay text is not otherwise verifiable from repo artifacts.
- O-4: the hygiene scan has no org-id or request-id shape rule; that part rests on the sanitizer plus manual inspection (clean).
- O-5: `hasOptionalProperties` runs before the size-limit check (within AR-05-01).
- O-6: "zero skipped" is a recorded gate step, not an automated build assertion.

---

## Security Audit Trail

| Audit Date | Threats Total | Closed | Open | Run By |
|------------|---------------|--------|------|--------|
| 2026-10-01 | 46 | 46 | 0 | gsd-security-auditor |

---

## Sign-Off

- [x] All threats have a disposition (mitigate / accept / transfer)
- [x] Accepted risks documented in Accepted Risks Log
- [x] `threats_open: 0` confirmed
- [x] `status: secured` set in frontmatter

**Approval:** verified 2026-10-01
