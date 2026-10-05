---
phase: 08-multi-turn-mappers
verified: 2026-10-01T10:30:00Z
status: passed
score: 4/4 roadmap success criteria verified (plus PLAN must-haves spot-checked against code)
covered_files:
  - ".planning/REQUIREMENTS.md"
  - ".planning/phases/08-multi-turn-mappers/08-01-PLAN.md"
  - ".planning/phases/08-multi-turn-mappers/08-01-SUMMARY.md"
  - ".planning/phases/08-multi-turn-mappers/08-02-PLAN.md"
  - ".planning/phases/08-multi-turn-mappers/08-02-SUMMARY.md"
  - ".planning/phases/08-multi-turn-mappers/08-03-PLAN.md"
  - ".planning/phases/08-multi-turn-mappers/08-03-SUMMARY.md"
  - ".planning/phases/08-multi-turn-mappers/08-04-PLAN.md"
  - ".planning/phases/08-multi-turn-mappers/08-04-SUMMARY.md"
  - ".planning/phases/08-multi-turn-mappers/08-05-PLAN.md"
  - ".planning/phases/08-multi-turn-mappers/08-05-SUMMARY.md"
  - ".planning/phases/08-multi-turn-mappers/08-06-PLAN.md"
  - ".planning/phases/08-multi-turn-mappers/08-06-SUMMARY.md"
  - ".planning/phases/08-multi-turn-mappers/08-07-PLAN.md"
  - ".planning/phases/08-multi-turn-mappers/08-07-SUMMARY.md"
  - ".planning/phases/08-multi-turn-mappers/08-08-PLAN.md"
  - ".planning/phases/08-multi-turn-mappers/08-08-SUMMARY.md"
  - ".planning/phases/08-multi-turn-mappers/08-09-PLAN.md"
  - ".planning/phases/08-multi-turn-mappers/08-09-SUMMARY.md"
  - "core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/transcript/Message.kt"
  - "providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/anthropic/AnthropicDecoder.kt"
  - "providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/anthropic/AnthropicEncoder.kt"
  - "providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/anthropic/AnthropicMessageEncoder.kt"
  - "providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/anthropic/AnthropicTransport.kt"
  - "providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ChatDecoder.kt"
  - "providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ChatMessageEncoder.kt"
  - "providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ChatReplay.kt"
  - "providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ChatResponseParts.kt"
  - "providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ChatTransport.kt"
  - "providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/transcript/ConversationCheck.kt"
covered_digest: "v1:sha256:fb6907705a863e9de843877cd811aabf597d82078260ac344021c40674667f4b"
behavior_unverified: 0
overrides_applied: 0
---

# Phase 8: Multi-turn Mappers Verification Report

**Phase Goal:** A multi-turn tool conversation round-trips losslessly between the neutral transcript and each provider dialect, so the agentic loop can run on any cloud provider without breaking thinking blocks or the cache.
**Verified:** 2026-10-01
**Status:** passed
**Re-verification:** No, initial verification

I read the production code, the conformance suite, the golden files and the evidence files directly. I did not rely on the SUMMARY files. At HEAD (e18a888, which includes the WR-01..04 and IN-01..04 review fixes) I ran `./gradlew check --offline`. It exited 0. I also ran `scripts/verify-repo-hygiene.sh` (HYGIENE OK) and `scripts/review-api-surface.sh --expect-sealed-complete` (API SURFACE OK). JUnit XML from that tree:

| Suite | Tests | Failures / errors / skipped |
|---|---|---|
| core | 535 | 0 / 0 / 0 |
| providers (4.12.0) | 572 | 0 / 0 / 0 |
| providers (5.2.1) | 572 | 0 / 0 / 0 |
| providers (5.5.0) | 572 | 0 / 0 / 0 |
| keystore | 96 | 0 / 0 / 0 |

On each of the three OkHttp legs, `AnthropicMultiTurnConformanceTest` ran 14 tests, `OpenAiMultiTurnConformanceTest` 13, `OpenRouterMultiTurnConformanceTest` 11, `ConversationGoldenTest` 22, `ConversationCheckTest` 17, all green. No `*Live*` class runs under `check` (`providers/build.gradle.kts` excludes it). The phase gate in `evidence/phase-gate.txt` records 555 providers tests per leg; the extra 17 are the tests the review fixes added.

## Goal Achievement

### Observable Truths (ROADMAP success criteria)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | One conformance suite runs against both the Anthropic and the Chat Completions mapper and round-trips multi-turn tool conversations, including parallel calls and empty arguments. | VERIFIED | There is a single abstract `MultiTurnConformanceSuite` with 9 shared `@Test`s, subclassed by `AnthropicMultiTurnConformanceTest`, `OpenAiMultiTurnConformanceTest` and `OpenRouterMultiTurnConformanceTest`. Each subclass only supplies a `WireDialect`. Every test loops over every manifest row of that dialect. `everyFixtureConversationRoundTrips` replays each golden through the production encoder and decoder, asserts one request per turn, and asserts that rows tagged `parallel` have a turn with more than one call. The Anthropic fixture asserts four calls in one turn with a zero-argument call (`theParallelFixtureAnswersFourCallsInOneTurn`). The empty-argument forms (empty string, absent, object) are covered by `derived_empty_args_forms` (OpenAI), the decoder tests on both dialects, and the repair path in `ChatReplay.kt` and `AnthropicMessageEncoder.kt` (`input:{}`). Manifest: 11 rows (6 derived, 5 captured) across anthropic, openai and openrouter, all tagged. |
| 2 | Assistant turns replay verbatim from `NativeReplay` to the same provider and model: a golden fixture with thinking blocks survives byte-for-byte. A transcript never crosses providers; `carry` stays semantic only. | VERIFIED | Production: `AnthropicMessageEncoder.assistantMessage` sends `message.nativeFor(ANTHROPIC, model)` content as stored. The only change is `"input":{}` for a `tool_use` block with no object input. A stamped turn is never rebuilt, and the encoder fails with `checkNotNull` if a mismatched stamp reaches it. The Chat encoder does the same through `ChatReplay.repairedReplay`, which keeps the allowlist `role, content, tool_calls, refusal, reasoning_details` and repairs only a missing role or empty/object arguments. Pre-flight: `conversationRefusal` is wired into both `AnthropicTransport.kt:112` and `ChatTransport.kt:102`, runs before any request, and refuses with `replay_mismatch` for another provider, model or raw shape. Test proof: `everyAssistantTurnIsReplayedByteForByte` uses an expectation independent of the encoder (for Anthropic, the golden's own `response["content"]`; for Chat, a key projection of the stored message). It requires that text to appear in the golden file, in every later request body, and as the assistant element at its own position. `aStampForAnotherProviderOrModelFailsBeforeAnyRequest` shows other provider, other model and other shape each give `FailureReason.Other("replay_mismatch")` with 0 HTTP requests, while the original stamp and an unstamped copy send 1 request. Thinking blocks: `derived_thinking` has `thinking`, `redacted_thinking` and interleaved text, replayed in order on every later request. `captured_a2.json` (real, `claude-sonnet-5-5`) has a `thinking` block with a 1440-character signature in the turn-1 response, and turn 2's request carries that 5-block assistant content. The live log shows the real API accepted that replay (status 200). `carry`: `StrategyOutcome.carry` is documented as opaque and never inspected by the engine, and a cross-provider history is a hard refusal, so no transcript crosses providers. The refusal consequence for a mid-conversation escalation is documented in the `Message.kt` and `ConversationCheck.kt` KDoc (IN-04). |
| 3 | Tool results are encoded per dialect: Anthropic batches all of a turn's results into one user message with `is_error`, and Chat Completions sends one `role:tool` message per call id. Golden tests built from recorded, sanitized real response bodies prove it. | VERIFIED | Anthropic: `toolResultsMessage` emits one `user` message whose content holds one `tool_result` block per call, in call order (`resultsInCallOrder`). It sets `is_error:true` for errors and omits empty content. Chat: `toolMessages` emits one `{role:tool, tool_call_id, content}` per result in call order, and wraps an error as `{"error": ...}`. `toolResultsAreEncodedPerDialect` checks, per request and per assistant turn, `dialect.resultMessageCount(calls)` (1 for Anthropic, N for Chat), the ids in call order, the error flags and the contents. Recorded real bodies: five captured goldens exist, `anthropic/captured_a1` (haiku-4-5, 4 parallel calls, cache write 6754 then cache read 6754), `anthropic/captured_a2` (sonnet-5-5, signed thinking), `openai/captured_o1` (gpt-5.4-mini, 4 parallel calls), `openrouter/captured_r1` (openai route) and `openrouter/captured_r3` (anthropic route, `reasoning_details` with signature). All are manifest rows and all pass every suite test on all three OkHttp legs. I parsed them: each has 2 turns; the turn-2 request carries the assistant turn followed by 4 tool results (Anthropic: one `user` message with 4 blocks; Chat: 4 `tool` messages). A grep over the golden directory for `sk-`, `Bearer ` and `x-api-key` found nothing. `ConversationGoldenTest` (22 tests) includes tree-aware hygiene and a requirement that every `*.json` under `golden/conversations/` has a manifest row. |
| 4 | Each dialect applies its own cache directive (Anthropic keeps its single system breakpoint; Chat Completions relies on automatic caching), and the cached prefix bytes stay identical on every iteration of a multi-turn conversation. | VERIFIED | `AnthropicEncoder.kt:144` is the only `cache_control` emitter in the providers main source; the Chat package has none. `theCacheDirectiveIsTheDialectsOwnOnEveryIteration` requires, for every request of every row, exactly one `cache_control` on the last system block with `{"type":"ephemeral"}` (Anthropic), and none at all for Chat. It also injects a stray directive as a negative control and asserts that it is caught. `everyIterationOnlyAppendsToTheCachedPrefix` checks every consecutive request pair with `appendOnlyViolation` (the new body starts with the old head, up to `messages`, and the old message tail is unchanged). `aRewriteOfAnEarlierTurnOrOfTheToolsFailsTheAppendOnlyCheck` runs a control that passes, then proves that editing an earlier user message, rebuilding an earlier assistant turn, or changing a tool description each fail. `reshapeIsSingleTurnOnly` pins the one known prefix rewrite (the forced-tool reshape) as a single-turn path. Real evidence: `captured_a1` shows `cache_creation_input_tokens: 6754` on turn 1 and `cache_read_input_tokens: 6754` on turn 2 against the replayed body. |

**Score:** 4/4 roadmap truths verified. 0 present-but-behavior-unverified. The behavior-dependent truths (replay, ordering, pre-flight refusals, append-only prefix) each have a passing behavioral test, and I confirmed that the suite's expectations do not come from the code under test.

### PLAN must-haves (spot-checked against code, not SUMMARY)

| Plan | Must-have | Status | Evidence |
|---|---|---|---|
| 08-01 | `ConversationCheck` pre-flight (replay key, call-id coverage), kind-only reason codes, zero requests, encoders never rebuild a stamped turn | VERIFIED | `ConversationCheck.kt` returns `replay_mismatch`, `tool_call_unanswered`, `tool_call_id_duplicate`, `tool_result_missing`, `tool_result_unexpected`. These are fixed strings with no id, tool name or text. It is wired into both transports. `toolCallCoverageIsEnforcedBeforeAnyRequest` asserts the code and 0 requests for five violation shapes. Both encoders use `checkNotNull(...) { REPLAY_REFUSED }`. |
| 08-02 | Per-dialect results in call order; Anthropic `is_error` and empty content omitted; Chat `{"error": …}` wrapper | VERIFIED | `AnthropicMessageEncoder.toolResultsMessage` and `ChatMessageEncoder.toolMessages` / `toolContent`, as above. |
| 08-03 | Lenient empty arguments on both decoders; Chat allowlist replay with repair-only normalization | VERIFIED | `ChatReplay.kt` (`REPLAY_FIELDS`, `repairedReplay`, `isEmptyArgumentsForm`). Decoder tests are green. The review's WR-01 and WR-02 hardened this: a stored Chat turn holds only the calls the decoder took, and object-form arguments replay as compact text. |
| 08-04 | Strict manifest, canonical printer, tree-aware hygiene, shared-id-map sanitizer, synthetic `ConversationScript` | VERIFIED | `ConversationGolden.kt`, `ConversationSanitizer.kt`, `ConversationScript.kt`, and their tests (`ConversationGoldenTest` 22, `ConversationSanitizerTest`). The script names no app domain (`record_item`, `count_items`, `lookup_item`). |
| 08-05 / 08-06 | Suite and bindings for Anthropic, OpenAI, OpenRouter; derived goldens (parallel, empty-args forms, `index`, `reasoning_details`, thinking) | VERIFIED | Three binding classes plus 6 derived rows in `MANIFEST.tsv`. |
| 08-07 | Append-only prefix on every iteration, per-dialect cache directive, negative controls, reshape single-turn only | VERIFIED | See SC4. |
| 08-08 | Bounded recorder in the two opt-in live tasks (at most 19 requests), key-free proof, phase gate | VERIFIED | `providers/build.gradle.kts` live tasks exclude `*Live*` from `test` and both matrix legs. `ConversationCaptureRunTest` (21 tests) proves the recorder on loopback with no key. `evidence/phase-gate.txt` records the gate. I re-ran `check`, hygiene and API-surface myself. I did not re-run `verify-negative-controls.sh` (it deliberately forces guard tests red), so I rely on its recorded exit 0. |
| 08-09 | Gated live capture; captured goldens or an explicit NOT RUN; SC2/SC3 status stated; no code change; no `api.txt`, no tag | VERIFIED | The live capture ran after the approved gate (D-12): 14 requests of at most 19, estimated about USD 0.033. `git diff 035bc1f 476ffd9` shows only evidence, summaries, ROADMAP, the manifest and the five goldens, with no source or build change. `git ls-files '*api.txt'` and `git tag` are both empty. R2 (gpt-oss-120b) was unmet because the model asked for one call per turn through the 3-request cap. The plan allowed this ("every met conversation adds a row"), and the evidence states it, with no golden and no manifest row. |

### Required Artifacts

| Artifact | Expected | Status | Details |
|---|---|---|---|
| `providers/.../transcript/ConversationCheck.kt` | Pre-flight | VERIFIED | Substantive, wired into both transports. |
| `providers/.../anthropic/AnthropicMessageEncoder.kt` | Verbatim replay, batched results | VERIFIED | Substantive, used by `AnthropicEncoder`. |
| `providers/.../chat/ChatMessageEncoder.kt`, `ChatReplay.kt` | Allowlist replay, per-id tool messages | VERIFIED | Substantive, used by the Chat request encoder. |
| `providers/src/test/.../conformance/MultiTurnConformanceSuite.kt` and three bindings | One suite, three dialect bindings | VERIFIED | All run under `check` on three OkHttp legs. |
| `providers/src/test/resources/golden/conversations/` | 11 rows, 5 captured | VERIFIED | Every manifest row has a file and every file has a row (test-enforced). |
| `evidence/phase-gate.txt`, `evidence/live-multiturn-capture.txt` | Gate and live record | VERIFIED | Consistent with the code, the goldens and the manifest. |

### Key Link Verification

| From | To | Via | Status | Details |
|---|---|---|---|---|
| `AnthropicTransport` | `conversationRefusal` | pre-flight before encode | WIRED | `AnthropicTransport.kt:112`, shape predicate `anthropicReplayContent`, shared with the encoder (IN-01). |
| `ChatTransport` | `conversationRefusal` | pre-flight before encode | WIRED | `ChatTransport.kt:102`, shape predicate `chatReplayMessage`, shared with the encoder. |
| `ChatDecoder` | `replayMatching` | stored turn matches the decoded calls | WIRED | WR-01. Pre-flight and encoder see the same calls. |
| `MANIFEST.tsv` | `MultiTurnConformanceSuite` | `conversationRows()` filtered by dialect | WIRED | Captured rows replay in all three suites. |

### Data-Flow Trace (Level 4)

Not applicable in the UI sense. The data flow here is wire-to-neutral-to-wire. The suite decodes each golden response with the production decoder, builds the next request with the production encoder, and compares the assistant element with an expectation taken from the golden file. The data comes from recorded real bodies (5 rows) and documented-shape derived bodies (6 rows), so nothing is a static stub.

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|---|---|---|---|
| Whole build, detekt zero, scanners, 3 OkHttp legs | `./gradlew check --offline` | exit 0, BUILD SUCCESSFUL | PASS |
| Repo hygiene | `scripts/verify-repo-hygiene.sh` | HYGIENE OK | PASS |
| API surface sealed and complete | `scripts/review-api-surface.sh --expect-sealed-complete` | API SURFACE OK, classes=177 | PASS |
| Suite ran on each leg | JUnit XML parse | 14 / 13 / 11 conformance tests per leg, 0 failures | PASS |
| No API snapshot or tag yet | `git ls-files '*api.txt'`, `git tag` | empty / empty | PASS |
| No secrets in goldens | grep for `sk-`, `Bearer `, `x-api-key` | no hits | PASS |

### Probe Execution

Step 7c: SKIPPED. The phase declares no `probe-*.sh` scripts.

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|---|---|---|---|---|
| XCR-01 | 08-05, 08-06, 08-07 | Anthropic and Chat mappers round-trip multi-turn tool conversations; one conformance suite for both | SATISFIED | SC1. |
| XCR-02 | 08-01, 08-05, 08-06, 08-07, 08-08, 08-09 | Verbatim replay from `NativeReplay` to the same provider and model (golden includes thinking blocks); transcripts never cross providers | SATISFIED | SC2. |
| XCR-03 | 08-01 to 08-06, 08-08, 08-09 | Per-dialect tool-result encoding; parallel calls and empty arguments; golden tests from recorded, sanitized real bodies | SATISFIED | SC1 and SC3. |

All three IDs appear in PLAN frontmatter and in ROADMAP Phase 8. No orphaned requirement: `REQUIREMENTS.md` maps only XCR-01, 02 and 03 to Phase 8, and the plans claim all three.

**Bookkeeping, not a gap:** `.planning/REQUIREMENTS.md` still shows `- [ ] **XCR-01..03**` and `Pending` in the traceability table (lines 85-87 and 204-206). The orchestrator should tick these when it closes the phase.

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|---|---|---|---|---|
| (providers `src/main`, `src/test/kotlin`) | - | `TBD`/`FIXME`/`XXX` grep | none | No debt markers in the files this phase touched. |

The review (`08-REVIEW.md`: 0 critical, 4 warnings, 4 info) was fully resolved in `08-REVIEW-FIX.md` (8 of 8 fixed, commits ab933bd..336353d). I confirmed that those fixes are in the tree and that `check` is green after them. Every committed golden still replays under the stricter hygiene rules.

### Notes (observations that do not change the verdict)

- **Chat replay is an allowlist projection, not the whole message.** Response-only fields (`annotations`, `reasoning` text) are dropped on replay. "Verbatim" therefore holds for the fields the endpoint accepts as input, and every kept value is byte-identical. The live finding OP shows OpenAI also accepted the unfiltered echo, so the projection is conservative rather than lossy in any way the API cares about. This matches the plan (08-03) and the test expectation.
- **R2 is open.** There is no captured golden for a reasoning model that returns `reasoning_details` without a signature route (gpt-oss-120b). The roadmap criteria do not require it. A signed-reasoning Chat golden exists through R3 (OpenRouter's Anthropic route, signature 1611 characters). The live log shows R2's turn-1 and turn-3 `reasoning_details` echo was accepted (status 200 each turn), so no code gap results from R2.
- **The captured A2 `thinking` block has empty thinking text and a 1440-character signature** (the model did not display its reasoning). The signed block still replays byte-for-byte and the API accepted it. Thinking blocks with visible text, and `redacted_thinking`, are covered by `derived_thinking`.
- **Carried to Phase 9** (from `phase-gate.txt`): the loop must use `ToolChoice.Auto` (reshape is single-turn only), duplicate tool-call ids across turns need whole-turn validation, and a ladder escalating mid-conversation must pass its history with replays dropped (KDoc'd under IN-04).

## Human Verification Required

None. This is a JVM-only library phase. Every success criterion is proven by an automated test that I ran, backed by captured real provider bodies already committed. The live capture was a one-off with its result recorded in `evidence/live-multiturn-capture.txt`. Nothing in the phase needs a human judgment call.

## Gaps Summary

No gaps. All four roadmap success criteria are met by code I read and tests I ran. All three requirement IDs are accounted for. The one unmet live conversation (R2) is a model-behaviour miss that the plan explicitly allowed and that the evidence reports openly. It does not affect any success criterion.

---

_Verified: 2026-10-01_
_Verifier: Claude (gsd-verifier)_
