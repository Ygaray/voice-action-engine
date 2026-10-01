# Phase 8: Multi-turn Mappers - Research

**Researched:** 2026-10-01
**Domain:** Neutral transcript <-> Anthropic Messages / Chat Completions (OpenAI, OpenRouter) multi-turn tool conversations; verbatim replay, tool-result encoding, golden fixtures, opt-in live recording
**Confidence:** MEDIUM-HIGH (codebase facts and Anthropic rules are first-party and read this session; OpenAI/OpenRouter server tolerance of echoed response-only fields is documented-by-schema but not empirically proven until the Phase 8 capture runs)

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

- **D-01 [replay-content]:** Anthropic: the raw content array (SB precedent). Chat: an allowlist projection copied as raw sub-elements (role, content, tool_calls, refusal, reasoning_details) — pending research on whether OpenAI rejects response-only fields when echoed _(source: ai-auto)_ _(provisional — refresh at execution; depends on Phase 5)_
- **D-02 [byte-for-byte]:** Canonical re-encoding identity (keeps Phase 3's raw: JsonElement); goldens stored compact _(source: ai-auto)_ _(provisional — refresh at execution; depends on Phase 3)_
- **D-03 [replay-key]:** Snapshotted provider+model (PROV-03); mismatch fails loudly (XCR-02 supersedes ARCHITECTURE's "other provider → rebuild") _(source: ai-auto)_
- **D-04 [coverage]:** Enforce in the mapper (an engine-side invariant failure beats an opaque HttpError); order = emission order on both dialects _(source: ai-auto)_
- **D-05 [chat-error]:** {"error": …} JSON wrapper; non-error content (incl. SB's held_for_confirmation JSON) passes through unchanged _(source: ai-auto)_
- **D-06 [empty-args]:** Lenient empty-args normalization; ids never invented (they must match replayed raw); blank/duplicate ids left to Phase 9 whole-turn validation _(source: ai-auto)_
- **D-07 [goldens]:** Live synthetic captures as listed, never SB's fixture or SB/CT names; needs Yahir's Anthropic/OpenAI/OpenRouter keys at Phase 8 time Capture tasks are GATED on Yahir's key approval (pending, relayed by the orchestrator). _(source: ai-auto)_ _(provisional — refresh at execution; depends on Phase 5)_
- **D-08 [recorder]:** Host-JVM opt-in recorder (reuse Phase 5's capture task), consistent id renaming across turns, thinking text and signatures untouched _(source: ai-auto)_ _(provisional — refresh at execution; depends on Phase 5)_
- **D-09 [conformance]:** :providers-local abstract suite (no cross-module test sharing needed) _(source: ai-auto)_
- **D-10 [append-only]:** Both assertions (a mapper that rewrites an earlier turn would pass the prefix test but miss the messages cache); Chat sends no cache directive, incl. OpenRouter anthropic/* _(source: ai-auto)_
- **D-11 [ext-replay]:** Needs external research: whether OpenAI accepts the full echoed message (annotations, refusal:null, audio:null); OpenRouter reasoning/reasoning_details presence with effort none and whether it must be echoed (esp. anthropic/* with signatures); Anthropic preserved-thinking validation sensitivity to re-encoding, redacted_thinking, dropping older-turn thinking, interleaved text; OpenAI tool-message ordering and empty/null content; empty-args forms and blank/duplicate ids seen in practice; response model id vs requested id _(source: ai-auto)_
- **D-12 [r1-verdict]:** Multi-turn golden-capture tasks are KEY-GATED until the orchestrator relays Yahir's OK; the rest of the phase executes without them. _(source: human — orchestrator R1 GO-WITH-CHANGES)_

### Runtime Decisions (override the provisional decisions above)

- **replay-content (refreshed vs Phase 5):** CONFIRMED vs Phase 5, still pending research on one point. Anthropic replays the raw content array (SB precedent). Chat replays an allowlist projection copied as raw sub-elements: role, content, tool_calls, refusal, reasoning_details. Phase 8 research must settle whether OpenAI rejects response-only fields when they are echoed, ideally using the Phase 5 recorded bodies under providers/src/test/resources.
- **byte-for-byte (refreshed vs Phase 3):** CONFIRMED vs Phase 3. `NativeReplay.raw: JsonElement` exists (core/transcript/NativeReplay.kt:21), so identity means canonical re-encoding. Goldens are stored compact.
- **goldens (refreshed):** REFINED. Use live synthetic captures as listed, never SB fixtures and never SB/CT names. Key approval is NO LONGER pending: Yahir standing test-key policy (HANDOFF "Keys: UNBLOCKED 2026-09-30") covers it, and the orchestrator applied that same policy to the Phase 5 D-16 capture. Conditions are the same as D-16: opt-in and outside check, `with-test-keys` only (lands in usage.log), cheapest models (Haiku 4.5, gpt-5.4-mini, a cheap OpenRouter route), a bounded call count stated in the plan, recorded bodies redacted of keys/auth headers/org and request ids, fixtures under test resources and never in logs, and the actual request count plus estimated cost reported in the SUMMARY. A plan checkpoint for this capture should still route through the master, which relays to the orchestrator.
- **recorder (refreshed vs Phase 5):** CONFIRMED vs Phase 5. Reuse the existing opt-in Test tasks `liveAnthropicCapture` and `liveChatCompletionsCapture` (providers/build.gradle.kts:70,85). Extend them for multi-turn recording; do not fork them. Rename ids consistently across turns, and leave thinking text and signatures untouched.

### Claude's Discretion

Anything not listed above follows `.planning/research/SUMMARY.md` and the phase's own research; Source `ai-auto` decisions took research's recommendation (orchestrator accepted the auto-resolved remainder).

### Deferred Ideas (OUT OF SCOPE)

See REQUIREMENTS.md v2 / LATER items.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| XCR-01 | Anthropic <-> neutral and Chat Completions <-> neutral mappers round-trip multi-turn tool conversations; one conformance suite runs against both. | "Codebase Map" shows every encode/decode entry point already exists as `internal` in `:providers`; the suite is a test-side `WireDialect` adapter plus an abstract class with 3 subclasses (Anthropic, Chat x OpenAI, Chat x OpenRouter). "Conformance Suite Design". |
| XCR-02 | Assistant turns are replayed **verbatim** from `NativeReplay` to the same provider/model (golden fixture includes thinking blocks); transcripts never cross providers (`carry` is semantic only). | Both mappers currently *silently rebuild* on a stamp mismatch (verified); flip to a typed pre-flight failure. Anthropic rules on thinking preservation and prefix binding cited. "Replay Key and Fail-Loud Design", "D-11 Findings". |
| XCR-03 | Tool results are encoded per dialect — Anthropic batches all results for a turn in one user message with `is_error`; Chat Completions sends one `role:tool` message per call id — with coverage for parallel calls and empty arguments, and golden tests built from recorded, sanitized real response bodies. | Encoders already batch/split correctly; gaps are coverage enforcement, emission order, the `{"error":…}` wrapper (Chat sends errors "unchanged" today), empty-args decode leniency, and golden fixtures. "Gaps by Requirement", "Golden Capture Design". |
</phase_requirements>

## Summary

Phase 8 is mostly **hardening and proving** code that Phases 4 and 5 already wrote. Both mappers already (a) batch/split tool results per dialect, (b) replay `NativeReplay.raw` when the provider+model stamp matches, and (c) keep the cached prefix deterministic. What is missing is (1) **fail-loud** behavior on a stamp mismatch (today both mappers silently rebuild from neutral parts, and two existing tests pin that behavior), (2) **coverage enforcement** (every tool call answered exactly once, results in emission order, no dangling calls) done before any HTTP request, (3) the **Chat error wrapper** `{"error": …}` (today `is_error` is dropped on Chat), (4) **lenient empty-arguments decoding** (absent/null/blank), which reverses two Phase 5 test assertions, (5) a **conformance suite** and **conversation goldens** with a conversation-consistent sanitizer, and (6) a **multi-turn recorder** that extends the two existing opt-in live tasks. No `:core` change and no new public type is needed.

The decisive external finding is Anthropic's **preserved-thinking rule**: on current thinking models the API validates that a replayed thinking block's *entire preceding prefix* (`system`, `tools`, all earlier messages) is unchanged, enforced by default for accounts created on/after 2026-08-31. "Verbatim replay" therefore means replay of **every** assistant turn, append-only, with `tools`/`system` byte-stable — which the Anthropic mapper already does for the latest turn; the work is to prove it for the whole history and to forbid the one existing mutation path (the `Required`+reshape instruction line appended to the last user message). For OpenAI, the documented *input* schema for an assistant message does not include `annotations`, so the allowlist projection is the safe choice; nothing found suggests `refusal: null` or `annotations: []` are rejected (the official Python SDK echoes them), so the allowlist is a strict-superset-safe design, not a workaround.

Two capture facts shape the golden plan: the engine sends **no `thinking` parameter**, so **Haiku 4.5 never thinks** with the current encoder (it is extended-thinking-only and off by default). A thinking golden must come from a default-thinking model; the cheapest is **Claude Sonnet 5.5 ($2/$10 per MTok)**, which also rejects forced tool use, so the conversation uses `ToolChoice.Auto`. And on OpenAI, any strict-eligible tool switches `parallel_tool_calls:false` on, so a parallel-call capture must opt tools out with `strict = false`.

**Primary recommendation:** Land the phase as 8 sequential single-module plans: (01) `ConversationCheck` + fail-loud replay key, (02) per-dialect tool-result encoding, (03) lenient empty-args, (04) conversation sanitizer + manifest-driven derived fixtures, (05-06) abstract conformance suite incl. append-only/cache assertions, (07) recorder extension (code only, key-free tests), (08) the key-gated capture run as the **last** plan so everything else passes with derived fixtures if captures are deferred.

## Project Constraints (from CLAUDE.md)

Extracted from `.claude/CLAUDE.md` (project) and the global router; treated as locked:

- Public API grows **strictly additively**; Phase 8 should add **no new public type** (all new code `internal`, tests in `src/test`). New failure kinds use the existing open `FailureReason.Other(code)`.
- No default args on public constructors; no `data`/`sealed`/`enum` in new public types (n/a if nothing public is added).
- `:core` depends on no HTTP; no `:core` change is required here.
- detekt: zero baseline, `buildUponDefaultConfig`, plain `detekt` task only; `TooGenericExceptionCaught`/`SwallowedException` stay active (the single never-throw collapse is `core/internal/Guarded.kt`); `MagicNumber` active (tests excluded) — use named constants. `ForbiddenImport` bans `okhttp3.internal.*`, `mockwebserver3.*`, `android.util.Log`, DI packages.
- `scanBannedConstructs`: no `runCatching`, `println`/`print` in **library** source (tests may print `LIVE_CAPTURE` lines, as existing live tests do), no `printStackTrace`, no app planning ids (`T-xx-xx`, `WR-xx`, `Phase NN D-xx`) in comments.
- **Secrets:** API keys, transcripts, tool args/results never reach logs, telemetry, exceptions or `toString()`. Failure reasons carry codes only.
- OkHttp: compile floor 4.12; tests use legacy `okhttp3.mockwebserver` only; every new non-`Live` test runs on the 4.12.0 / 5.2.1 / 5.5.0 legs under `check`.
- Process: contract changes only via §10 amendments through the control plane (not applicable: this phase changes no contract text); never `/gsd-update`; never edit `CROSS-REPO-SCOPE-CONTRACT.md`; no `api.txt`, no git tag in this phase.
- Keys only through `with-test-keys` (never read key files, never paste keys; the test-keys dir is permission-denied to agents).
- Planning hygiene (from Phase 7 plan prohibitions, reuse): never stage `.planning/graphs/`, `graphify-out/`, `.gsd/`, `.planning/intel/`, `.planning/config.json`, `.planning/v1.0-MILESTONE-RUN.md`, `.planning/state.json`, `.gsd-stage-*.done.json`; never `git add -A` / `git add .` / `git commit -a`.

## Architectural Responsibility Map

This is a library, not a tiered app; "tier" = module/layer.

| Capability | Primary Layer | Secondary Layer | Rationale |
|------------|---------------|-----------------|-----------|
| Neutral transcript types (`Message`, `AssistantMessage`, `NativeReplay`, `ToolResultsMessage`) | `:core` (unchanged) | — | Already present and sufficient; `nativeReplay.provider/.model/.raw` are public, so a mapper can read the stamp. [VERIFIED: core/.../transcript/Message.kt:34-54, NativeReplay.kt] |
| Provider+model snapshot (PROV-03) | `:core` `RoutedModel`/`Binding` (unchanged) | — | One `Binding` per `BoundModel`; every `complete` builds `ProviderRequest(binding.model, …)`, so the replay key is constant for a command. [VERIFIED: core/.../provider/BoundModel.kt `RoutedModel.complete`] |
| Replay-key check, coverage check, tool-result ordering | `:providers` (new internal `ConversationCheck`) | transports call it pre-flight | Dialect mappers own replay (D-03/D-04); one shared pure function keeps both dialects identical. |
| Wire encoding (tool_result batch / `role:tool` messages, `{"error":…}` wrapper) | `:providers` `AnthropicMessageEncoder`, `ChatMessageEncoder` | — | Existing files; only edits. |
| Lenient empty-args decode | `:providers` `ChatResponseParts`, `AnthropicDecoder` | — | Decoders already own argument parsing. |
| Conformance suite, goldens, sanitizer | `:providers` **test** sources | — | D-09: no cross-module sharing. |
| Opt-in live recorder | `:providers` test sources + `providers/build.gradle.kts` tasks | host JVM | D-08: extend existing tasks. |

## Standard Stack

No new library, no new Gradle plugin, no new dependency. Everything below is already on the `:providers` classpath.

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| kotlinx-serialization-json | 1.11.0 | `JsonElement` tree for raw replay, canonical encoding, fixtures | Pinned project-wide; `NativeReplay.raw` is a `JsonElement`. Jar present in the Gradle cache (`kotlinx-serialization-json-jvm-1.11.0.jar`). [VERIFIED: gradle cache listing this session; CITED: .claude/CLAUDE.md stack table] |
| OkHttp | 4.12.0 compile floor (legs 5.2.1, 5.5.0) | Test-side HTTP for the recorder (via existing `cleanClient`, `OneShotJsonBody`, `await`) | A1 floor; never raise. [CITED: providers/build.gradle.kts, .claude/CLAUDE.md] |
| JUnit | 4.13.2 | Abstract suite + 3 subclasses | Existing. |
| okhttp3 legacy MockWebServer | tracks leg | Key-free recorder and transport pre-flight tests | Existing, only package allowed. |
| kotlinx-coroutines-test | 1.11.0 | `runTest` where needed | Existing. |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Typed pre-flight `ModelResult.Failure(Other(code))` | Throw an internal exception and let `RoutedModel`'s `guarded` map it to `Unexpected(errorClass)` | Throwing is also loud but loses the specific kind (`Unexpected` carries only the class name) and a custom provider gets no benefit either way. Typed codes are greppable in traces. **Use the typed failure.** |
| Check in `:providers` transports | Check once in `:core` `RoutedModel.complete` | A `:core` check would also protect third-party `AiProvider`s, but it touches `:core` (not needed for XCR) and duplicates the dialect knowledge of "tool-call coverage". Optional follow-up; see Open Question 2. |
| Full-request recorded goldens | Record only `messages` per turn | System prompt (up to 32 000 chars for the Haiku cache check) x 3 turns bloats the repo; `system` and `tools` are code-defined and synthetic. **Record `messages` + response body per turn.** |

**Installation:** none.

**Version verification:** n/a (no package installed). `npm view`/`pip index` do not apply to this JVM phase.

## Package Legitimacy Audit

No external packages are installed or recommended in this phase. **Packages removed due to [SLOP] verdict:** none. **Packages flagged as suspicious [SUS]:** none. (The `package-legitimacy` gate was not run because there is nothing to check.)

## Codebase Map: what exists, what gets extended, which seams are missing

All paths relative to `/home/yahir/Projects/Reusable/android/voice-action-engine`.

### Neutral types (read-only context; no change)
- `core/.../transcript/Message.kt` — `AssistantMessage(parts, nativeReplay)`, `toolCalls`, `nativeFor(provider, model)`; `ToolResultsMessage(results)` requires non-empty and distinct call ids; `ToolResult(callId, content, isError)`. [VERIFIED: Message.kt:34-54 — `public fun nativeFor(provider: ProviderId, model: String): JsonElement? = nativeReplay?.takeIf { it.provider == provider && it.model == model }?.raw`]
- `core/.../transcript/NativeReplay.kt` — `NativeReplay(provider, model, raw: JsonElement)`, `toString` prints provider/model only.
- `core/.../transcript/ModelRequest.kt` — carries `cache: CacheDirective` (`staticPrefix`, `conversationTail`); `ToolChoice.Auto/Required`.
- Doc-only follow-up (optional): `nativeFor` KDoc says "A null result means the mapper must rebuild this turn from the neutral parts" — after Phase 8 that is true **only when `nativeReplay == null`**; reword (comment change, no API change).

### Anthropic mapper (`:providers`)
| File | Today | Phase 8 change |
|------|-------|----------------|
| `anthropic/AnthropicMessageEncoder.kt` | `assistantMessage` = `put("content", message.nativeFor(ProviderId.ANTHROPIC, model) ?: rebuiltContent(message))` (lines 41-44) — **silent rebuild on a mismatched stamp**. `toolResultsMessage` emits one user message, results in `message.results` order, always writes `content`, `is_error` only when true. | After the pre-flight guard has verified the stamp, use `message.nativeReplay?.raw ?: rebuiltContent(message)`. Order results by the preceding turn's call emission order. Omit `content` when the result text is empty (the API allows omission: "`content` (optional)" and shows an "Example of empty tool result"). |
| `anthropic/AnthropicEncoder.kt` | `encodeAnthropicRequest(call, reshape)` — key order `model, max_tokens, tools, tool_choice, system, messages`; tools sorted by name; one ephemeral breakpoint on the last system block; `withInstruction` appends a text block to the **last user message** when reshaped. | No functional change. Add a KDoc/test note: `Required`+reshape is unsupported in a loop (it rewrites the previous last user message every iteration — see Pitfall 2). |
| `anthropic/AnthropicDecoder.kt` | `decodeResponse`: `NativeReplay(ProviderId.ANTHROPIC, model, content)` (line 64) — stamps the **requested** `model` and stores the whole `content` array; `decodePart` ignores non-text/tool_use blocks (they stay in raw); `decodeToolUse`: absent/non-object `input` -> `MalformedToolArgs` (line 89). | Make absent/null `input` decode as `{}` (D-06). Everything else stays. |
| `anthropic/AnthropicTransport.kt` | `send` checks credential then runs the retry loop. | Add the pre-flight `ConversationCheck` after the credential check and **before** the loop (so zero HTTP requests are sent, and the check runs once, not per attempt). |

### Chat Completions mapper (`:providers`)
| File | Today | Phase 8 change |
|------|-------|----------------|
| `chat/ChatMessageEncoder.kt` | `REPLAY_FIELDS = setOf("role", "content", "tool_calls", "refusal", "reasoning_details")` (line 26); `assistantMessage` rebuilds when `nativeFor` is null (lines 51-54) — silent rebuild on mismatch; `replayedFields` = `JsonObject(replay.filterKeys { it in REPLAY_FIELDS })` keeps the **stored key order**; `toolMessages` = one `role:tool` message per result, **content unchanged for errors**. | Guard-then-replay (no silent rebuild); `{"error":…}` wrapper for `isError`; emission-order output; repair-only normalization on replay (missing `role`, absent/null/blank `function.arguments` -> `"{}"`). Keep the allowlist and keep `tool_calls` entries verbatim. |
| `chat/ChatDecoder.kt` | Stamps `NativeReplay(vendor.providerId, model, message)` with the **whole `choices[0].message`** (line 99); the allowlist is applied at **encode** time, so the raw stays lossless. | None. (Recommendation: keep projection at encode — a future allowlist change needs no re-recording.) |
| `chat/ChatResponseParts.kt` | `decodeArguments`: object -> as is; string -> `parseArguments` (empty string -> `{}`); **else (absent/null) -> `MalformedToolArgs`** (lines 53-57); blank `" "` and the text `null` -> `MalformedToolArgs`. | D-06: absent, JSON `null`, blank string and the text `null` -> `{}`; everything else non-object -> `MalformedToolArgs`. **Reverses two Phase 5 test assertions** (`" "`, `"null"`, `arguments: null` in `ChatDecoderTest.badArgumentsAreMalformedToolArgs`). |
| `chat/ChatTransport.kt` | `send`: credential check then `sendWithRetry`. | Same pre-flight as Anthropic. |
| `chat/ChatEncoder.kt` | Key order `model, messages, tools, tool_choice, parallel_tool_calls, reasoning_effort, <token param>, provider`; `parallel_tool_calls:false` when `vendor.parallelToolCallsFalseOnForced && rules.acceptsParallelToolCalls && oneCall`, with `oneCall = required != null || strictNames.isNotEmpty() || request.singleToolCall` (lines 68-70). | No functional change. Capture implication: strict-eligible tools turn parallel calls off on OpenAI. |

### Router / snapshot (PROV-03)
`core/.../provider/BoundModel.kt` — `RoutedModel.complete` builds `ProviderRequest(binding.model, request, binding.credential, binding.capabilities)` on every call; `Binding` is frozen per `BoundModel`. So `(provider.id, model)` is the single replay key for a command. Both `AnthropicDecoder` and `ChatDecoder` stamp from `call.model` (requested id), never from the response body's `model`. No `:core` change needed.

### Existing :providers test layout (reuse, do not fork)
- Fixtures: `anthropic/AnthropicFixtures.kt` (`textBlock`, `thinkingBlock` with a fixed `"sig-1"`, `toolUseBlock`, `usageJson`, `successBody`, `anthropicRequest(model, request, key)`); `chat/ChatFixtures.kt` (`chatToolCall`, `chatMessage`, `chatUsage`, `chatBody`, `CHAT_GOLDEN_MODEL = "gpt-5.4-mini"`); `chat/ChatRequestFixtures.kt` (`logFoodTool`, `editListCardTool`, `chatCall(vendor, model, request, key)`, `goldenRequest`).
- Goldens: `providers/src/test/resources/golden/chat/{requests,responses}`; `responses/MANIFEST.tsv` (9 columns, `derived`|`captured` provenance, `derived.json#case` or `captured/<vendor>-<label>.json`), replayed by `chat/ChatGoldenReplayTest.kt`, which also hosts the shared hygiene scan `goldenHygieneViolations`. **There are no Anthropic goldens of any kind** and `AnthropicLiveCaptureTest` only prints, it saves nothing.
- Live machinery: `chat/ChatCompletionsLiveCaptureTest.kt` (`CapturePlan.all` = 11 single-request `PlannedCall`s, `CaptureRun` writes raw to `vae.raw.dir` and the sanitized copy to `vae.golden.dir`, ceilings `MAX_HTTP_REQUESTS = 12`, `MAX_REQUESTS_PER_VENDOR = 6`); key-free proofs `chat/ChatCaptureCallPlanTest.kt` (asserts exactly 11 calls) and `chat/ChatCaptureRunTest.kt` (loopback MockWebServer). `chat/ChatGoldenSanitizer.kt` numbers tool-call ids with a **per-body** `Pass` counter and pretty-prints.
- Gradle: `providers/build.gradle.kts` — `liveAnthropicCapture` (line 70; filter `*AnthropicLiveCaptureTest`; gate `VAE_LIVE_ANTHROPIC`; **no system properties, no output dir**) and `liveChatCompletionsCapture` (line 85; filter `*ChatCompletionsLiveCaptureTest`; gate `VAE_LIVE_CHAT`; `vae.golden.dir` = `src/test/resources/golden/chat/responses/captured`, `vae.raw.dir` = `build/live-chat/raw`). `test` and both matrix legs exclude `*Live*`: **a new key-free capture test class must not contain "Live" in its name or it will never run in `check`.**

### Gaps by requirement (the missing seams, exactly)

| Gap | Req | Where it lives |
|-----|-----|----------------|
| G1 No conversation-level validation (tool-call coverage, dangling calls, orphan results, within-turn duplicate ids) | XCR-03, D-04 | **NEW** `providers/src/main/kotlin/.../providers/transcript/ConversationCheck.kt` (internal) |
| G2 Replay-key mismatch silently rebuilds | XCR-02, D-03 | both `*MessageEncoder.kt` + both transports' pre-flight; flips `AnthropicEncoderTest.aMatchingNativeReplayIsSentVerbatimAndAnyOtherIsRebuiltFromParts` and `ChatMessageEncoderTest.aReplayFromAnotherVendorProviderOrModelIsRebuilt` |
| G3 Tool results not ordered by call emission | XCR-03, D-04 | both message encoders |
| G4 Chat drops `is_error` (content "sent unchanged"; test `eachToolResultIsItsOwnToolMessageInOrderAndErrorsAreSentUnchanged` asserts no `is_error`) | XCR-03, D-05 | `ChatMessageEncoder.toolMessages` |
| G5 Empty/absent/null arguments are `MalformedToolArgs` on both decoders | XCR-03, D-06 | `ChatResponseParts.decodeArguments`, `AnthropicDecoder.decodeToolUse` |
| G6 No multi-turn fixtures; no Anthropic goldens; sanitizer ids are per-body | XCR-01/03, D-07/08 | **NEW** test resources + `ConversationSanitizer` |
| G7 No conformance suite / `WireDialect` adapter | XCR-01, D-09 | **NEW** `providers/src/test/kotlin/.../providers/conformance/` |
| G8 No append-only / cache-directive proof across iterations | SC4, D-10 | conformance suite |
| G9 No multi-turn recorder; Anthropic recorder saves nothing | D-07/D-08 | extend the two live test classes + `build.gradle.kts` |

## Architecture Patterns

### System Architecture Diagram

```
 Strategy (Phase 9)                    :providers (this phase)                              wire
 ------------------                    ----------------------------------------------      ------
 ModelRequest(messages =            +-> transport.send(call)                                
   [User, Assistant(+NativeReplay),  |    1. credential check (existing)                    
    ToolResults, ...])               |    2. ConversationCheck(call)   <-- NEW, once        
        |                            |         - every Assistant.nativeReplay stamp ==      
        v                            |           (provider, call.model)  else Failure       
 BoundModel.complete  ---------------+         - every tool call answered exactly once,    
   (provider+model frozen, PROV-03)  |           results directly after their turn           
                                     |         - no dangling tool calls at the end            
                                     |       Failure(Other(code)) -> return, ZERO requests    
                                     |    3. encode request (existing, deterministic)         
                                     |         Anthropic: assistant = raw content array;      
                                     |           results = ONE user msg, emission order,      
                                     |           is_error, content omitted when empty         
                                     |         Chat: assistant = allowlist(raw) ;             
                                     |           results = ONE role:tool msg per call id,     
                                     |           isError -> {"error": content}                
                                     |    4. POST (existing retry loop)  ------------------>  Anthropic /
                                     |    5. decode (existing): parts + NativeReplay(raw)  <-- OpenAI /
                                     +--  6. lenient empty-args: absent|null|blank|"{}" -> {}   OpenRouter
```

### Recommended Project Structure
```
providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/
├── transcript/ConversationCheck.kt        # NEW internal: replay-key + coverage + order (pure, no I/O)
├── anthropic/AnthropicMessageEncoder.kt   # edit
├── anthropic/AnthropicDecoder.kt          # edit (empty input)
├── anthropic/AnthropicTransport.kt        # edit (pre-flight)
├── chat/ChatMessageEncoder.kt             # edit
├── chat/ChatResponseParts.kt              # edit (empty arguments)
└── chat/ChatTransport.kt                  # edit (pre-flight)

providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/
├── conformance/WireDialect.kt             # NEW: adapter interface + 3 impls
├── conformance/MultiTurnConformanceSuite.kt   # NEW abstract (not named *Test, so it is not run alone)
├── conformance/{Anthropic,OpenAi,OpenRouter}MultiTurnConformanceTest.kt   # NEW 3 subclasses
├── conformance/ConversationGolden.kt      # NEW manifest loader + hygiene
├── conformance/ConversationSanitizer.kt   # NEW shared-id-map sanitizer
├── conformance/ConversationCapture.kt     # NEW script/plan + runner (shared by both live methods)
├── conformance/ConversationCaptureRunTest.kt  # NEW key-free (no "Live" in the name)
├── ConversationCheckTest.kt               # NEW (or transcript/)
└── (edits to AnthropicEncoderTest, ChatMessageEncoderTest, ChatDecoderTest, AnthropicDecoderTest,
     AnthropicTransportTest, ChatTransportTest, AnthropicLiveCaptureTest, ChatCompletionsLiveCaptureTest)

providers/src/test/resources/golden/conversations/
├── MANIFEST.tsv                           # provenance derived|captured, per scenario
└── {anthropic,openai,openrouter}/<scenario>/turn-N.response.json + turn-N.messages.json   # compact, canonical
```

### Pattern 1: One pure pre-flight guard, shared by both dialects
**What:** `ConversationCheck` inspects `call.request.messages` + `call.model` + the dialect's `ProviderId` and returns a violation kind or null. Transports map a violation to `ModelResult.Failure(FailureReason.Other(code))` before any I/O. Codes are fixed stable tokens (`replay_mismatch`, `tool_result_missing`, `tool_result_unexpected`, `tool_call_id_duplicate`, `tool_call_unanswered`) — `Other` requires `[A-Za-z0-9_.:-]`, 1-64 chars. [VERIFIED: core/.../failure/FailureReason.kt:249-257 — "`public class Other(override val code: String) : FailureReason`" with `require(isSafeToken(code))`]
**When:** every `send`. **Rules (D-03/D-04):**
1. For each `AssistantMessage` with `nativeReplay != null`: `provider == dialect.providerId && model == call.model`, else `replay_mismatch`. `nativeReplay == null` is legal (hand-built / scripted turns rebuild from parts).
2. For each `AssistantMessage` with a non-empty `toolCalls`: the **next** message must be a `ToolResultsMessage` whose id set equals the call id set (as a multiset against the calls: a duplicated call id inside one turn is `tool_call_id_duplicate`, since only one result per id can exist). A trailing assistant turn with tool calls is `tool_call_unanswered`.
3. Every `ToolResultsMessage` must directly follow an `AssistantMessage` with tool calls; else `tool_result_unexpected`.
4. Output order = the assistant turn's call emission order (not the app's `results` order).
**Why not name the id (deviation from D-04's wording "naming the missing id"):** `FailureReason` values are deliberately free of model-controlled text (ids come from the model); `Other.code` must be a short safe token. Report the *kind*; the id is recoverable from the transcript the strategy holds. Flagged as Assumption A9.
```kotlin
// Sketch only; the codes are proposals, the FailureReason.Other contract is verified above.
internal fun conversationViolation(call: ProviderRequest, dialect: ProviderId): String? {
    val messages = call.request.messages
    messages.forEachIndexed { index, message ->
        if (message is AssistantMessage) {
            val stamp = message.nativeReplay
            if (stamp != null && (stamp.provider != dialect || stamp.model != call.model)) return "replay_mismatch"
            val next = messages.getOrNull(index + 1)
            val calls = message.toolCalls.map { it.id }
            if (calls.isNotEmpty() && next !is ToolResultsMessage) return "tool_call_unanswered"
            if (calls.size != calls.toSet().size) return "tool_call_id_duplicate"
            if (next is ToolResultsMessage && next.results.map { it.callId }.toSet() != calls.toSet()) return "tool_result_missing"
        } else if (message is ToolResultsMessage && messages.getOrNull(index - 1).let { it !is AssistantMessage || it.toolCalls.isEmpty() }) {
            return "tool_result_unexpected"
        }
    }
    return null
}
```

### Pattern 2: Chat tool-result encoding with the error wrapper (D-05)
Build the wrapper with a JSON builder (never string concatenation): `isError` -> `content = Json.encodeToString(JsonObject.serializer(), buildJsonObject { put("error", result.content) })`; non-error content (including a held-for-confirmation JSON string) passes through untouched. One `role:tool` message per call id, ordered by the assistant turn's emission order. Anthropic keeps `is_error: true` on the block (`"is_error": true` shown in the docs) and the existing rule "`tool_result` blocks first in the user content".

### Pattern 3: Canonical re-encoding identity (D-02)
"Byte-for-byte" = identity under the engine's own canonical encoding: `Json.encodeToString(JsonElement.serializer(), parse(x))` is **idempotent** and preserves key order, but is **not** the identity on exotic numbers/escapes. [VERIFIED: scratch run against `kotlinx-serialization-json-jvm-1.11.0.jar` this session: input `{"b":1.0,"a":[1e3,0.10,"é\/x"],…}` re-encoded to `{"b":1.0,"a":[1000.0,0.1,"é/x"],…}` and a second pass was identical to the first (`true`).] Consequences: store goldens **already canonical and compact** (D-02), and have `ConversationGoldenTest` assert `canonical(file) == file.trim()` so a hand-edited or live-sanitized fixture cannot drift. The replay assertion is `encode(request).assistantTurn == goldenTurn` as `JsonElement` equality **and** as compact string equality.

### Pattern 4: Append-only prefix proof (SC4, D-10)
For iterations 1..N of a fake loop built from fixture turns:
- **Anthropic:** `messages` is the last key, so `bodyN.removeSuffix("]}")` must be a **string prefix** of `bodyN+1`; additionally the bytes before `"messages":[` (model, max_tokens, tools, tool_choice, system) are identical, and exactly one `cache_control` exists, on the last system block.
- **Chat:** `messages` is the 2nd key and `tools`/`tool_choice`/`reasoning_effort`/token param follow it; assert (a) message array elements `0..k-1` of iteration N equal those of N+1 element-wise, (b) the trailing keys' bytes are identical across iterations, (c) the body contains **no** `cache_control` (OpenAI and OpenRouter, including `anthropic/*`).
- **Negative control (proves the tests bite):** a stub mapper that rewrites an earlier turn must fail both assertions.

### Anti-Patterns to Avoid
- **Rebuilding an assistant turn that has a `NativeReplay` for another provider/model** (today's behavior). It is exactly the thinking-signature/cache break the phase exists to prevent.
- **Stamping replay from the response body's `model`** (`gpt-5.4-mini-2026-03-17` vs requested `gpt-5.4-mini`): the stamp must stay the requested id.
- **Per-request ids/dates/parallel flags in `system` or `tools`** (cache + thinking-binding break).
- **String-concatenated JSON** for the error wrapper or fixtures.
- **Adding a case to `CapturePlan.all`** (breaks `ChatCaptureCallPlanTest`'s exact-11 assertion and the 12/6 ceilings that Phase 5 already consumed): add a separate conversation plan with its own ceilings.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| JSON canonicalization of fixtures | A custom pretty/compact printer or regex normalizer | `Json.encodeToString(JsonElement.serializer(), …)` (compact, order-preserving, idempotent) | The same function production replay uses; any other printer can differ on numbers/escapes. |
| Wrapper/body construction | `"{\"error\":\"" + text + "\"}"` | `buildJsonObject { put("error", …) }` | Tool text is arbitrary; quoting bugs are injection bugs. |
| Per-dialect conformance copies | Three copy-pasted test classes | One abstract suite + `WireDialect` adapter | D-09; guarantees the same assertions on every dialect. |
| HTTP in the recorder | New OkHttp wiring | `cleanClient`, `OneShotJsonBody`, `await` (as `CaptureRun` does) | Same hardening (no interceptors, no redirects) as production. |
| Id renaming | Per-body counters (`ChatGoldenSanitizer.Pass`) | One shared `idMap` per conversation across request and response bodies | Ids must line up between the turn-1 response, the turn-2 request and the turn-2 response. |
| Mocking providers in the conformance suite | MockK/fakes of the transport | Production `encode*Request`/`decode*Response` on fixture bodies | Fakes would test the fake. |

**Key insight:** every behavior Phase 8 needs already exists as an `internal` pure function; the phase's value is the *invariants and proofs around them*, not new machinery. New production code should be < ~150 lines (the guard, the wrapper, two decoder tweaks, two encoder tweaks).

## D-11 Findings (each with confidence and source)

| # | Question | Finding | Confidence | Source |
|---|----------|---------|------------|--------|
| 1 | Does OpenAI reject response-only fields when an assistant message is echoed (`annotations`, `refusal:null`, `audio:null`)? | **Settled for design: use the allowlist; the question is moot for correctness.** (a) The documented *input* assistant-message param has `audio, content, function_call, name, refusal, tool_calls` and **no `annotations`**. (b) The recorded OpenAI body has `"refusal": null` and `"annotations": []` on the message (openai-forced_log_food.json:22-23) and none of the Phase 5 bodies includes an echoed request, so they **cannot** show acceptance or rejection (stated honestly). (c) The official Python SDK serializes a pydantic message with `exclude_unset=True`, so `messages.append(completion.choices[0].message)` echoes exactly the fields the response set, including `annotations`/`refusal`; I found no report of that being rejected. (d) `refusal` (string-or-null) *is* a documented input field, so keeping `refusal` in the allowlist is schema-valid. | MEDIUM (schema documented; server tolerance inferred; no empirical echo recorded) | [CITED: developers.openai.com/api/reference/typescript/resources/chat/subresources/completions — ChatCompletionAssistantMessageParam vs ChatCompletionMessage (summarized by the fetch tool)]; [CITED: raw.githubusercontent.com/openai/openai-python/main/src/openai/_utils/_transform.py — `model_dump(data, exclude_unset=True, mode="json", …)`]; [VERIFIED: providers/src/test/resources/golden/chat/responses/captured/openai-forced_log_food.json:22-23] |
| 1b | Nested response-only fields inside `tool_calls` | OpenRouter's recorded tool calls carry `"index": 0` (openrouter-forced_log_food.json:22). The top-level allowlist does not touch it, and the replay keeps `tool_calls` verbatim (needed for provider extras such as Gemini's `thought_signature`, which strict endpoints require echoed). OpenRouter is a normalizing router and its docs show `index` on `reasoning_details`; acceptance of `tool_calls[].index` on input is **unproven** until the OpenRouter turn-2 request in the capture returns 200. **Contingency if it 400s:** strip `index` from each replayed tool call on the OpenRouter vendor only. | LOW-MEDIUM | [VERIFIED: openrouter-forced_log_food.json:22]; [CITED: langchain-ai/langchain#40563 — provider extras on tool calls must be echoed for Gemini 3]; [ASSUMED] for OpenRouter acceptance |
| 2 | OpenRouter `reasoning`/`reasoning_details` presence with effort none | In both recorded OpenRouter bodies (openai/gpt-5.4-mini and anthropic/claude-haiku-4.5 route) the message has `"reasoning": null` and **no `reasoning_details` key** (…forced_log_food.json:18, …haiku_route.json:18). The allowlist therefore passes nothing extra for non-reasoning turns. | HIGH for these two routes | [VERIFIED: golden/chat/responses/captured/openrouter-forced_log_food.json:17-18, openrouter-haiku_route.json:17-18] |
| 2b | Must `reasoning_details` be echoed; what about `reasoning` (string) | Yes for reasoning turns: "the entire sequence of consecutive reasoning blocks must match the outputs generated by the model … you cannot rearrange or modify the sequence"; modified sequences are rejected. The plain `reasoning` string is also accepted on input but carries no signature, so it is **not** needed when `reasoning_details` is present. Allowlist keeps `reasoning_details` verbatim as a whole array and drops `reasoning`. For `anthropic/*` routes the engine never sends a `reasoning` request parameter, so thinking blocks appear only if the upstream thinks by default (Sonnet 5.5 does); whatever `reasoning_details` come back are replayed verbatim. | MEDIUM-HIGH | [CITED: openrouter.ai/docs/use-cases/reasoning-tokens; openrouter.ai/docs/guides/best-practices/reasoning-tokens] |
| 3 | Anthropic preserved-thinking validation vs re-encoding | Required: "when you return tool results, you must pass the thinking blocks from the assistant message back to the API, complete and unmodified"; the 400 text contains "`thinking` or `redacted_thinking` blocks in the latest assistant message cannot be modified". On Fable 5.1 / Opus 5.5 / Sonnet 5.5 "the API accepts a replayed thinking block only while the `system` prompt, `tools`, and messages that preceded it are unchanged" (enforced by default for accounts created on/after 2026-08-31; otherwise only when `thinking.block_binding.prefix_mismatch_behavior` is set). Edits that invalidate: editing/reordering/deleting any earlier message, "change an earlier `tool_use` input", "clear or shorten an earlier `tool_result`", change `system`, add/remove/rename/edit a tool. Edits that do **not**: appending messages, removing thinking blocks from the start/end/all, changing `tool_choice`/`max_tokens`/`effort`, adding/moving `cache_control`. The docs do not address JSON whitespace/key order; every official SDK example re-serializes parsed blocks, and the engine re-encodes a parsed tree order-preservingly, so parse+canonical-encode is the same class of round trip. | HIGH for the rules; MEDIUM for "canonical re-encode is accepted" (docs silent; SDK round trip is the evidence) | [CITED: platform.claude.com/docs/en/build-with-claude/thinking (Preserving thinking blocks; Preserved thinking); …/preserved-thinking (What counts as an edit); …/thinking-troubleshooting (400 messages)] |
| 4 | `redacted_thinking` | Distinct block type with an opaque `data` field; must be passed back unchanged; filtering on `type == "thinking"` alone "silently drops `redacted_thinking` blocks and breaks the multi-turn protocol". The engine replays the whole `content` array, so it is preserved by construction; the decoder ignores it as a *part* (correct). Add a derived fixture containing one so a future type-filtering "optimization" fails a test. | HIGH | [CITED: …/thinking#redacted-thinking-blocks] |
| 5 | Dropping older-turn thinking | Allowed outside a tool-use turn ("Required: within a tool-use turn, pass thinking blocks back. Recommended: across turns, pass everything back. Allowed: outside tool use, omit prior turns' thinking"); the API strips for you on last-turn-only models (all Haiku through 4.5, earlier Opus/Sonnet) and keeps all prior turns on Opus 4.5+/Sonnet 4.6+/Fable/Mythos. Removing thinking from the *middle* while keeping later ones is invalid on prefix-bound models. **Engine rule: never drop or filter; replay every turn.** | HIGH | [CITED: …/thinking#preserving-thinking-blocks, #thinking-block-preservation-by-model] |
| 6 | Interleaved text | An assistant turn may be `[thinking, text, tool_use, …]` and, with interleaved thinking, thinking blocks can recur between tool calls; "the sequence of consecutive `thinking` blocks must match what the model generated". Raw array replay preserves order; the rebuild path (only when `nativeReplay == null`) emits text parts then tool_use in part order and cannot carry thinking — acceptable, because a null replay means no thinking exists to lose. | HIGH | [CITED: …/thinking-tool-workflows; …/thinking#preserving-thinking-blocks] |
| 7 | Thinking and cache | Changing thinking config/effort/budget between requests invalidates message-level cache breakpoints (and sometimes tool/system ones); thinking blocks are cached with tool results in a tool loop. Pin thinking config for a command (Phase 9) — the engine sends none, which is a constant. | HIGH | [CITED: …/thinking#thinking-and-prompt-caching] |
| 8 | OpenAI tool-message ordering | An assistant message with `tool_calls` must be followed by `tool` messages answering **each** `tool_call_id`, with no other message in between; errors seen in practice: "An assistant message with 'tool_calls' must be followed by tool messages responding to each 'tool_call_id'. The following tool_call_ids did not have response messages: …". Order among the contiguous `tool` messages: community reports say pairing, not position, matters; the engine emits call-emission order regardless. | MEDIUM (error text widely reported; ordering claim community-sourced) | [CITED: agno-agi/agno#3105, langchain4j discussion #1632, portkey error library (search results)]; order claim [ASSUMED] |
| 9 | Anthropic result placement | "Tool result blocks must immediately follow their corresponding tool use blocks"; "the tool_result blocks must come FIRST in the content array. Any text must come AFTER all tool results"; parallel results travel in one user message. Existing encoder matches; the reshape instruction block is appended *after* results (legal). | HIGH | [CITED: platform.claude.com/docs/en/agents-and-tools/tool-use/handle-tool-calls] |
| 10 | Empty/null/omitted content | Anthropic `tool_result.content` is optional and the docs show an empty result with no `content` key -> **omit when empty**. OpenAI assistant `content` is "required unless `tool_calls`" so tool-call-only turns carry `content: null` (the rebuild path already does). Empty `role:tool` content on OpenAI/OpenRouter: **not documented**; some routed-to-Anthropic paths reject empty text blocks. Default: send `""` and include one probe in the capture (optional call P2); revisit if it 400s. | MEDIUM (Anthropic), LOW (Chat empty tool content) | [CITED: …/handle-tool-calls ("`content` (optional)", "Example of empty tool result")]; Chat side [ASSUMED] |
| 11 | Empty-args forms seen in practice | Anthropic: `input` is always an object (`{}` for none). OpenAI-shaped: `"{}"`; `""` was only ever seen as a Phase 5 *derived* case, never in a real recorded body. Real-world reports of **empty `tool_calls[].id`** (Gemini via an OpenAI-compat layer) and **duplicate ids across turns** (providers that regenerate `call_0`, `call_1` per turn) exist. The decoders already reject blank ids (`MalformedResponse`), so empty ids fail loudly at decode; within-turn duplicates are caught by `ConversationCheck`; **conversation-wide** duplicate ids are left to Phase 9 whole-turn validation per D-06. | MEDIUM (reports, not primary docs) | [VERIFIED: ChatResponseParts.kt:47-49, AnthropicDecoder.kt:86-88]; [CITED: Google AI forum "function_response.name empty … empty id via OpenAI Compatibility Layer"; huggingface/smolagents#2683; openclaw#40897 (search results)] |
| 12 | Response `model` vs requested id | OpenAI returns the **dated** id (`"model": "gpt-5.4-mini-2026-03-17"`); OpenRouter echoes the requested id (`openai/gpt-5.4-mini`, `anthropic/claude-haiku-4.5`) while `provider` varies by route (`OpenAI`, `Amazon Bedrock`). Both decoders stamp the **requested** id from `ProviderRequest.model`, which PROV-03 freezes per command. Keep it that way; a stamp from the body would break the replay key on OpenAI. OpenRouter may pick a different upstream on a later turn (cache is per upstream); Anthropic signatures are "compatible across platforms". | HIGH | [VERIFIED: openai-forced_log_food.json:5; openrouter-forced_log_food.json:5; openrouter-haiku_route.json:5; ChatDecoder.kt:99; AnthropicDecoder.kt:64]; [CITED: …/thinking#thinking-encryption] |

### Recommendation for the Chat allowlist projection (D-01), final
1. Keep `REPLAY_FIELDS = setOf("role", "content", "tool_calls", "refusal", "reasoning_details")` [VERIFIED: ChatMessageEncoder.kt:26] and the stored-order filter (deterministic -> cache-stable).
2. Copy `tool_calls` and `reasoning_details` as raw sub-elements, entry-for-entry, no nested filtering (contingency for `index` only if the capture proves it necessary).
3. Drop `reasoning` (plain string), `annotations`, `audio`, `function_call`, and every unknown key.
4. **Repair-only normalization on the replayed copy** (invisible when the wire was valid): add `"role":"assistant"` if the key is absent; a tool call whose `function.arguments` is absent, `null` or blank becomes `"{}"`. Rationale: an invalid echo is an opaque provider 400; the repair is deterministic per raw, so the prefix stays byte-stable across iterations. Tag A4.
5. For `anthropic/*` OpenRouter routes: nothing special beyond (2) — replay `reasoning_details` whole and unmodified; the engine sends no `reasoning` request parameter and no cache directive (D-10).

## Runtime State Inventory

Not a rename/refactor/migration phase — section omitted by design.

## Common Pitfalls

### Pitfall 1: Two existing tests pin the opposite of XCR-02/D-05/D-06
**What goes wrong:** An executor "fixes" production code and then reds `AnthropicEncoderTest.aMatchingNativeReplayIsSentVerbatimAndAnyOtherIsRebuiltFromParts`, `ChatMessageEncoderTest.aReplayFromAnotherVendorProviderOrModelIsRebuilt`, `ChatMessageEncoderTest.eachToolResultIsItsOwnToolMessageInOrderAndErrorsAreSentUnchanged` (asserts `assertFalse(encoded.contains("is_error"))` and unchanged error content) and `ChatDecoderTest.badArgumentsAreMalformedToolArgs` (lists `" "`, `"null"` and `arguments: null` as malformed).
**Why:** Phase 4/5 intentionally pinned today's behavior; Phase 8 decisions supersede it.
**How to avoid:** Each plan lists the exact tests it flips and says so in its objective (deliberate reversal, not a regression). Keep the rest of those tests.
**Warning signs:** A plan that "adds tests" but leaves these four untouched while changing the encoders.

### Pitfall 2: The reshape instruction rewrites an earlier turn on every iteration
**What goes wrong:** With `ToolChoice.Required` on a model that cannot be forced (reshape), `withInstruction` appends the "Call the X tool…" line to the **last user-role message**. In a loop that message is the previous iteration's tool-result message, so iteration N+1 *removes* the line from it (it moves to the new last message) — an edit of an earlier turn: breaks append-only, the messages cache, and thinking-block binding on Opus/Sonnet 5.5.
**How to avoid:** The loop uses `ToolChoice.Auto` (Phase 9 must state this). Add an explicit test and KDoc: "reshape is single-turn only". Do not try to fix it in Phase 8.

### Pitfall 3: Strict-eligible tools silently disable parallel calls on OpenAI
**What goes wrong:** `oneCall = required != null || strictNames.isNotEmpty() || request.singleToolCall` puts `parallel_tool_calls:false` on OpenAI whenever any tool is strict-eligible (a zero-arg schema `{"type":"object","properties":{},"additionalProperties":false}` is eligible). A "parallel calls" capture then records single calls. [VERIFIED: ChatEncoder.kt:68-70; ChatStrict.kt `effectiveChatStrict = tool.strict != false && isChatStrictEligible(...)`]
**How to avoid:** Capture tools use `ToolSpec(..., strict = false)`. OpenRouter never sends the flag (`parallelToolCallsFalseOnForced = false`), so parallel calls are model-dependent there. Tell Phase 9 that OpenAI loops with strict tools are effectively sequential per turn.

### Pitfall 4: The existing sanitizer would corrupt what must stay untouched
**What goes wrong:** `ChatGoldenSanitizer.scrub` regex-replaces `call_…`, `gen-…`, `chatcmpl-…` inside **every** string, including `thinking` text, `signature`, `data`, `reasoning`; and numbers tool-call ids per body (`Pass`), so turn 1 and turn 2 both produce `call_GOLDEN1`.
**How to avoid:** New `ConversationSanitizer` with (a) one id map per conversation applied by exact match to request messages and response bodies, ids numbered in first-seen order per prefix (`call_GOLDEN<n>`, `toolu_GOLDEN<n>`, `msg_GOLDEN<n>`, `chatcmpl-GOLDEN`, `gen-GOLDEN`); (b) **exempt** values under keys `thinking`, `signature`, `data` (redacted_thinking), `reasoning`, `reasoning_details` from rewriting — they are only *scanned* by the hygiene rules and the whole capture is refused (loudly) if a key-shaped string or Bearer value appears; (c) canonical compact output (D-02). Reuse `KEY_IN_TEXT`, `BEARER_IN_TEXT`, `ID_IN_TEXT`, `GOLDEN_TAIL` so the two sanitizers cannot drift.

### Pitfall 5: Haiku 4.5 will not produce a thinking golden
**What goes wrong:** The Anthropic request has no `thinking` key. Haiku 4.5 is extended-thinking-only and off by default (`Claude Haiku 4.5 | Extended only | Off | "adaptive" rejected`), so a Haiku conversation has no thinking block to preserve.
**How to avoid:** Thinking scenario on `claude-sonnet-5-5` (default On, `disabled` rejected, forced tool choice rejected -> `ToolChoice.Auto`). Display defaults to omitted on newer models, so the `thinking` text may be empty with only `signature` populated — still a valid, signed block. Keep Haiku for the cheap no-thinking parallel/error/zero-arg/cache scenario.

### Pitfall 6: The capture machinery's existing tests pin its current size
**What goes wrong:** Appending conversations to `CapturePlan.all` breaks `ChatCaptureCallPlanTest.thePlanIsElevenCallsWithinTheCeilings` and the 12/6 ceilings.
**How to avoid:** Separate `ConversationPlan` data and separate ceilings; the existing single-request plan stays byte-identical. A new test class for the recorder must not contain "Live" in its name (the `test` task excludes `*Live*`).

### Pitfall 7: `liveAnthropicCapture` cannot see bodies through the provider
**What goes wrong:** `AnthropicProvider` derives a clean client that drops interceptors, so a recorder cannot tap the wire through the provider, and the existing test only prints decoded summaries.
**How to avoid:** Mirror `CaptureRun`: build bytes with `encodeAnthropicRequest(call)`, POST with `cleanClient` + `OneShotJsonBody` + `await`, decode with `decodeAnthropicResponse`. One request per call, no transport retry, so the ceiling is exact. Add `vae.golden.dir`/`vae.raw.dir` system properties to the Anthropic task (it has none today).

### Pitfall 8: Number/escape canonicalization is not identity
`1e3 -> 1000.0`, `0.10 -> 0.1`, `é -> é`. Fixtures must be stored canonical; a live capture is canonicalized by the sanitizer before commit; the hygiene test asserts idempotence. Impact on the API is negligible (semantic JSON equality; thinking signatures cover text, not tool-input number formatting) — Assumption A5.

### Pitfall 9: Decoder leniency must not change `decodeChatToolCalls` ids
Blank ids stay `MalformedResponse` (D-06 "ids never invented"). Only `arguments`/`input` become lenient.

## Code Examples

### Replay projection (Chat) — current shape to keep
```kotlin
// Source: providers/.../chat/ChatMessageEncoder.kt:26,56-57 (existing; verified this session)
private val REPLAY_FIELDS = setOf("role", "content", "tool_calls", "refusal", "reasoning_details")
private fun replayedFields(replay: JsonObject): JsonObject =
    JsonObject(replay.filterKeys { it in REPLAY_FIELDS })
```

### Error wrapper (Chat), per D-05
```kotlin
// Sketch. Non-error content is passed through unchanged; the wrapper is built, never concatenated.
private fun toolContent(result: ToolResult): String =
    if (result.isError) {
        Json.encodeToString(JsonObject.serializer(), buildJsonObject { put("error", result.content) })
    } else {
        result.content
    }
```

### Anthropic result block (existing shape + omit-when-empty)
```kotlin
// Existing writes: put(TYPE, "tool_result"); put("tool_use_id", result.callId); put("content", result.content);
// if (result.isError) put("is_error", true)   [file read in full this session: AnthropicMessageEncoder.kt, toolResultsMessage]
// Change: if (result.content.isNotEmpty()) put("content", result.content)
```

### Dialect adapter used by the abstract suite (test side)
```kotlin
// Sketch of the seam D-09 calls "WireDialect": no HTTP, production functions only.
internal interface WireDialect {
    val provider: ProviderId
    val model: String
    fun decode(body: String): ModelResult                 // decodeAnthropicResponse / decodeChatResponse
    fun encode(request: ModelRequest): JsonObject         // parse(encodeAnthropicRequest / encodeChatRequest)
    fun assistantWire(encoded: JsonObject, assistantOrdinal: Int): JsonElement   // content array | allowlisted message
    fun toolResultWires(encoded: JsonObject, afterAssistantOrdinal: Int): List<WireToolResult>  // (callId, content, isError)
    val hasCacheDirective: (String) -> Boolean            // body contains "cache_control"
}
```

### Append-only assertion (Anthropic)
```kotlin
// messages is the last top-level key: iteration N's body minus its closing "]}" must prefix iteration N+1's body.
assertTrue(bodyNext.startsWith(bodyPrev.removeSuffix("]}")))
```

## Golden Capture Design (D-07, D-08, D-12; Runtime Decisions)

**Principle:** extend the two existing opt-in Test tasks; do not fork. Everything below is designed and unit-testable **key-free** (loopback MockWebServer), and **no live call is made by research or by any plan except 08-08**.

### Task/gate wiring (`providers/build.gradle.kts`, edit lines ~70 and ~85)
- `liveAnthropicCapture`: keep `filter { includeTestsMatching("*AnthropicLiveCaptureTest") }`; extend `onlyIf` to `VAE_LIVE_ANTHROPIC == "1"` (existing bounded test) and add a second `@Test boundedMultiTurnCapture` that additionally requires `VAE_LIVE_ANTHROPIC_MULTITURN=1` and honors `VAE_LIVE_ANTHROPIC_CALLS` (conversation codes, like `VAE_LIVE_CHAT_CALLS`). Add system properties `vae.golden.dir` (= `src/test/resources/golden/conversations/anthropic`) and `vae.raw.dir` (= `build/live-anthropic/raw`, gitignored `build/`).
- `liveChatCompletionsCapture`: add a second `@Test boundedMultiTurnCapture` to `ChatCompletionsLiveCaptureTest` gated by `VAE_LIVE_CHAT_MULTITURN=1` (and the existing `VAE_LIVE_CHAT=1` task gate), selection by `VAE_LIVE_CHAT_CALLS` using conversation codes `M1..`; system property `vae.golden.conversations.dir`.
- Update both task `description` strings with the new bounded counts. Neither task becomes a dependency of `check`.

### Conversation script (shared, domain-free; never SB/CT names)
Tools (synthetic): `record_item(item: string)`, `count_items()` (**zero-arg**, schema `{"type":"object","properties":{},"additionalProperties":false}`), `lookup_item(name: string)` (the script answers it with `isError = true`). `ToolSpec.strict = false` on all three (parallel calls on OpenAI; see Pitfall 3). `ToolChoice.Auto`. The user turn asks for several things "in one step" to invite parallel calls. The runner loops: send -> decode -> if `TOOL_USE` build `ToolResultsMessage` from the script (record -> "ok", count -> "2", lookup -> error text) -> append `AssistantMessage` (with its `NativeReplay`) + results -> repeat, stop on `END_TURN` or the per-conversation request cap.

### Bounded request counts (hard ceilings; one HTTP request per call, no retries, count incremented *before* sending, as `CaptureRun` does)

| Task | Code | Route / model | Scenario | Max requests |
|------|------|---------------|----------|--------------|
| liveAnthropicCapture | A1 | `claude-haiku-4-5` ($1/$5) | parallel + zero-arg + `is_error`; long system (>= 4096 tokens, reuse the 32 000-char prefix) to observe `cache_read > 0` on turn >= 2 | 3 |
| liveAnthropicCapture | A2 | `claude-sonnet-5-5` ($2/$10) | **thinking round trip** (default-thinking model, `Auto`), `max_tokens` large enough for thinking (e.g. 2048), short system | 3 |
| | | | **Anthropic total** | **6** |
| liveChatCompletionsCapture | O1 | `gpt-5.4-mini` (OpenAI) | parallel + zero-arg + error | 3 |
| liveChatCompletionsCapture | O-P (optional probe) | `gpt-5.4-mini` | send turn 2 with the *unfiltered* assistant message echoed (annotations, refusal:null) to turn D-11.1 into a recorded fact | 1 |
| liveChatCompletionsCapture | R1 | `openai/gpt-5.4-mini` (OpenRouter) | same scenario; proves `tool_calls[].index` echo is accepted | 3 |
| liveChatCompletionsCapture | R2 | `openai/gpt-oss-120b` ($0.037/$0.17) | **reasoning route**: expect `reasoning` + `reasoning_details`; proves `reasoning_details` echo | 3 |
| liveChatCompletionsCapture | R3 | `anthropic/claude-sonnet-5.5` (OpenRouter, $2/$10) | signed reasoning via a router, if the upstream surfaces it with no `reasoning` request param (unknown: record whatever happens) | 3 |
| | | | **Chat total** | **13** (OpenAI 4, OpenRouter 9) |
| | | | **Phase total ceiling** | **19** |

Model ids/prices verified against OpenRouter's public `GET /api/v1/models` on 2026-10-01 (`anthropic/claude-sonnet-5.5` 2e-6/1e-5, `openai/gpt-5.4-mini` 7.5e-7/4.5e-6, `openai/gpt-oss-120b` 3.7e-8/1.7e-7, tools + reasoning supported) [VERIFIED: public models endpoint this session]; Anthropic prices [CITED: platform.claude.com/docs/en/about-claude/pricing]. Worst-case spend estimate **< USD 0.20** (Haiku long prefix x3 ~0.03; Sonnet 5.5 x3 ~0.05 direct + ~0.05 via OpenRouter; the rest cents) — an estimate for the SUMMARY to replace with the actual figure (A10).

### Redaction and recording
- Keys come only from env vars set by `with-test-keys` (`ANTHROPIC_API_KEY`, `OPENAI_API_KEY`, `OPENROUTER_API_KEY`); the Authorization header is never written. Raw bodies go only under `build/…`; only sanitized copies land under `src/test/resources`.
- Record per turn: `turn-N.response.json` (sanitized full body) and `turn-N.messages.json` (the `messages` array the engine sent, sanitized). `system`/`tools` are code-defined.
- Shared id map per conversation (Pitfall 4). Thinking text, `signature`, `data`, `reasoning*` untouched. Strip response header-only data (request ids live in headers, never in bodies; Anthropic error bodies carry `request_id` -> map to `req_GOLDEN`).
- Console output: only `LIVE_CAPTURE` lines with ids/statuses/counts/usage (as the existing tests); never a body, text or argument.
- After sanitizing, the recorder **re-runs the production decoder on the sanitized copy** and compares the outcome to the observed one (the `keep()` check in `CaptureRun`) and re-encodes the transcript to compare against the sanitized `messages` file; any mismatch is reported as `unmet`.
- Run commands (form used in Phase 5; agents never read key files):
  `with-test-keys --only anthropic -- env VAE_LIVE_ANTHROPIC=1 VAE_LIVE_ANTHROPIC_MULTITURN=1 ./gradlew :providers:liveAnthropicCapture --offline --no-daemon --console=plain`
  `with-test-keys --only openai,openrouter -- env VAE_LIVE_CHAT=1 VAE_LIVE_CHAT_MULTITURN=1 ./gradlew :providers:liveChatCompletionsCapture --offline --no-daemon --console=plain`
- Gate: the capture plan's first task is a `checkpoint:human-verify` that states the ceilings above and routes through the master to the orchestrator (Runtime Decision); the run reports actual request count and estimated cost in the SUMMARY (it lands in `usage.log` via the wrapper).

### Captured vs hand-built (so the phase passes if captures are deferred)

| Scenario | Source | Why |
|----------|--------|-----|
| Anthropic thinking + signature (A2) | **Capture only** (a derived fixture uses a placeholder signature and is labelled `derived`) | A real signature cannot be hand-built; the offline suite proves *our* round trip, the capture proves the *API accepts it*. |
| OpenRouter `reasoning_details` shape (R2/R3) | **Capture preferred**; derived fallback from the documented field list (`type`, `text`, `signature`, `id`, `format`, `index`) | Shape is documented; acceptance is empirical. |
| Parallel calls, zero-arg, error result (A1/O1/R1) | **Derived is sufficient**; capture adds confidence | Chat: mutate the real Phase 5 bodies (`openai-forced_log_food.json` gives a real tool-call turn; `openai-auto_prose.json` a real final answer) — add a second tool call / `"arguments":"{}"`; Anthropic: build from the documented content-array shape. Error results are *request-side*, so no response capture is needed. |
| Cache read on turn >= 2 (SC4 live half) | Capture (A1) | Observability only; SC4's byte-identity half is fully offline. |
| Chat `{"error":…}` / Anthropic `is_error` encoding | Offline unit + conformance | Pure encoding. |

Manifest columns (proposal): `case, dialect, provenance(derived|captured), turns, scenario_tags, note`; the conformance suite iterates **every** row regardless of provenance, so a later capture only adds rows. **SC3 ("recorded, sanitized real response bodies") is fully met only once each dialect has a `captured` row; with captures deferred it is met for Chat via Phase 5 real bodies used as turn 1, and is open for Anthropic (no real Anthropic body exists in the repo).** The verifier/plan must record this explicitly rather than silently passing.

## Conformance Suite Design (XCR-01, D-09)

Abstract `MultiTurnConformanceSuite` (not named `*Test`) driven by a `WireDialect`; subclasses `AnthropicMultiTurnConformanceTest`, `OpenAiMultiTurnConformanceTest` (Chat x `ChatVendor.OPENAI`, `gpt-5.4-mini`), `OpenRouterMultiTurnConformanceTest` (Chat x `ChatVendor.OPENROUTER`, `openai/gpt-5.4-mini`; a second fixture pass with `anthropic/claude-haiku-4.5` for the no-cache assertion). Common tests, each loops over manifest rows tagged for the dialect:
1. `roundTripsEveryFixtureTurn` — decode golden turn k -> neutral parts (+ids, +empty args as `{}`) -> encode the next request -> the assistant wire equals the golden (Anthropic: whole `content` array; Chat: allowlist of the stored message) -> tool results present per call id, parallel calls included.
2. `replayIsCanonicalIdentity` — `JsonElement` equality **and** compact-string equality of replayed vs golden turn (thinking, redacted_thinking, signatures, interleaved text).
3. `mismatchedStampFailsBeforeAnyRequest` — via the transport with MockWebServer: `Failure(Other("replay_mismatch"))`, `server.requestCount == 0`; each of provider/model mismatch; `nativeReplay == null` still rebuilds.
4. `coverageIsEnforced` — missing result, extra result, dangling trailing calls, within-turn duplicate ids, orphan results; outcome is a typed failure and zero requests.
5. `resultsFollowEmissionOrder`, `errorEncoding` (Anthropic `is_error`; Chat wrapper), `emptyArgsRoundTrip`.
6. `appendOnlyAcrossIterations`, `cacheDirectivePerDialect` (Pattern 4) + negative control.
7. `canaryNeverLeaks` — `toString()` of failures/requests never contains tool content (reuse the existing canary idiom).

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Thinking blocks validated by signature only | Prefix-bound validation (system + tools + prior messages), enforced by default for accounts created >= 2026-08-31 | 2026 (Fable 5.1 / Opus 5.5 / Sonnet 5.5) | Append-only is mandatory, not an optimization. |
| Manual `thinking: {type: "enabled", budget_tokens}` | Adaptive thinking; manual rejected with 400 on 4.7+; Haiku 4.5/Opus 4.5/Sonnet 4.5 stay manual-only | 2026 | Engine sends no `thinking` param -> only default-on models think. |
| Strip prior-turn thinking yourself | API filters per model (keep-all on Opus 4.5+/Sonnet 4.6+; last-turn on Haiku) | 2026 | Never prune client-side. |
| OpenAI `messages.append(response_message)` | Same pattern, but input schema documents a narrower message than the output | ongoing | Allowlist projection is the schema-faithful echo. |

**Deprecated/outdated:** ARCHITECTURE's "other provider -> rebuild" (superseded by XCR-02/D-03).

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | OpenAI tolerates (or the allowlist makes moot) an echoed `annotations: []`/`refusal: null`; no rejection report found | D-11 #1 | Low: allowlist drops `annotations`; only `refusal:null` is echoed and is schema-valid. Optional probe O-P would convert to VERIFIED. |
| A2 | OpenRouter accepts `tool_calls[].index` on an echoed assistant message | D-11 #1b | Medium: 400 on turn 2 of every OpenRouter loop. Proven/disproven by capture R1; contingency: strip `index` on the OpenRouter vendor. |
| A3 | `role:tool` with empty content `""` is accepted by OpenAI and by OpenRouter->Anthropic routes | D-11 #10 | Medium: a tool returning "" would 400 opaquely. Optional probe; fallback: encode empty as a single space or placeholder (needs product decision). |
| A4 | OpenAI would reject an echoed tool call with `arguments` absent/null/`""`; repair to `"{}"` on echo is safe | Allowlist rec. #4 | Low: repair only fires on already-invalid wire data. Keep if unconfirmed. |
| A5 | Canonical re-encoding (numbers `1e3`->`1000.0`, `é`->`é`) never invalidates Anthropic thinking binding or the cache | Pattern 3 / Pitfall 8 | Low: semantic JSON equality; server re-parses. |
| A6 | Order of contiguous `role:tool` messages is irrelevant to OpenAI (pairing matters); engine uses emission order anyway | D-11 #8 | None for correctness (emission order is the conservative choice). |
| A7 | Sonnet 5.5 via OpenRouter returns signed `reasoning_details` even though the engine sends no `reasoning` parameter | Capture R3 | Low: capture records whatever occurs; the scenario degrades to a plain multi-turn conversation. |
| A8 | Sonnet 5.5, called with the engine's request shape (no `thinking` key), returns `thinking` blocks (text possibly empty, signature present) | Pitfall 5 / A2 | Medium for the Anthropic thinking golden: if no block appears, the capture is `unmet` and the derived thinking fixture stays; consider a follow-up (additive `ModelRequest` field is out of scope here). Docs say default On / `disabled` rejected, but do not state the visible-block shape for 5.5 specifically. |
| A9 | D-04's "naming the missing id" is replaced by kind-only codes because `FailureReason.Other.code` must be a safe token and ids are model-controlled | Pattern 1 | Needs orchestrator/user confirmation as a deliberate deviation from D-04's wording. |
| A10 | Total spend < USD 0.20 for <= 19 requests | Capture Design | Low; the plan states ceilings, the SUMMARY reports actuals. |
| A11 | Gradle merges `--tests` CLI patterns with a task `filter` inclusively/exclusively in a way I did not verify | Capture wiring | Avoid relying on it: select with env vars (`*_CALLS`) as Phase 5 did. |
| A12 | Accounts older than 2026-08-31 may not enforce the prefix check | D-11 #3 | None: the design is append-only regardless, as Anthropic itself advises. |

## Open Questions

1. **D-04 "naming the missing id".**
   - What we know: reasons are free of model text by design; `Other.code` is a safe token <= 64 chars.
   - What's unclear: whether the orchestrator wants a lossy "kind" or a bounded safe-token id.
   - Recommendation: ship kinds only (A9); the app holds the transcript, so the id is recoverable.
2. **Should the replay-key/coverage check also live in `:core` `RoutedModel.complete` for third-party providers?**
   - Known: `:providers` mappers cover the three shipped providers; custom `AiProvider`s are unprotected.
   - Recommendation: not in Phase 8 (keeps `:core` untouched, avoids duplicating dialect rules); revisit in v1.x if an app ships its own provider.
3. **Chat empty `role:tool` content** (A3). Recommendation: optional probe P2 on OpenAI + OpenRouter->Anthropic inside the O1/R3 budget only if a spare request exists; otherwise leave `""` and record the risk in the SUMMARY.
4. **OpenRouter sticky routing (`session_id`) for loops.** PITFALLS suggests a stable `session_id` per command so the cache stays on one upstream. D-10 says Chat sends no cache directive; adding a request field is an encoder/ModelRequest change outside XCR scope. Recommendation: defer to Phase 9/10 and note it.
5. **Do we need a request-side `thinking` setting so Haiku can think?** Out of scope (public `ModelRequest` change). The thinking golden uses a default-thinking model.

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| JDK 17 | Gradle build | ✓ | OpenJDK 17.0.19 | — |
| Gradle wrapper | all tests | ✓ | 9.4.1 (`./gradlew --version`) | — |
| Offline dependency cache | `--offline` runs | ✓ | kotlinx-serialization-json 1.11.0 jars present | — |
| `with-test-keys` | capture plan 08-08 only | ✓ | `/home/yahir/.local/bin/with-test-keys` | If it refuses (key missing/too permissive): write `NOT RUN: <reason>` and stop, tell the orchestrator (Phase 5 precedent). Never touch the key store. |
| Test keys (anthropic/openai/openrouter) | 08-08 only | not inspected (agents are denied reads) | HANDOFF: "Keys: UNBLOCKED 2026-09-30" | Captures deferred; derived fixtures carry the phase. |
| Network (HTTPS) | capture; this research's web checks | ✓ (OpenRouter `/models` fetched) | — | — |
| MockWebServer (legacy) | key-free tests | ✓ | per OkHttp leg | — |

**Missing dependencies with no fallback:** none for plans 01-07. **With fallback:** keys/captures (plan 08).

## Validation Architecture

> `workflow.nyquist_validation` is `true` in `.planning/config.json` (read this session).

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 4.13.2 + kotlinx-coroutines-test 1.11.0 + legacy `okhttp3.mockwebserver` (module `:providers`; all Phase 8 tests live here, none in `:core`) |
| Config file | `providers/build.gradle.kts` (matrix legs `testOkhttp521`, `testOkhttp550`; live tasks), `config/detekt/detekt.yml` |
| Quick run command | `./gradlew :providers:test --tests '*<Class>' --offline -q` |
| Full suite command | `./gradlew check --offline` |
| Per-leg spot check | `./gradlew :providers:testOkhttp521 :providers:testOkhttp550 --tests '*MultiTurnConformanceTest' --offline` |
| Static gates | `./gradlew :providers:detekt :providers:scanBannedConstructs --offline -q` |

### Phase Requirements -> Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| XCR-02 | Stamp mismatch (provider or model) -> typed `Failure(Other("replay_mismatch"))`, zero HTTP requests; `nativeReplay == null` still rebuilds | unit + MockWebServer | `./gradlew :providers:test --tests '*ConversationCheckTest' --tests '*AnthropicTransportTest' --tests '*ChatTransportTest' --offline -q` | ❌ Wave 0 (new `ConversationCheckTest`; transport tests exist, extended) |
| XCR-02 | Replay is canonical identity, thinking/redacted/signature/interleaved survive | conformance | `./gradlew :providers:test --tests '*MultiTurnConformanceTest' --offline -q` | ❌ new |
| XCR-02 | Existing encoder tests flipped (no silent rebuild) | unit | `./gradlew :providers:test --tests '*AnthropicEncoderTest' --tests '*ChatMessageEncoderTest' --offline -q` | ✅ exist, edited |
| XCR-03 | Coverage: missing/extra/dangling/duplicate/orphan -> typed failure, zero requests | unit + conformance | `./gradlew :providers:test --tests '*ConversationCheckTest' --tests '*MultiTurnConformanceTest' --offline -q` | ❌ new |
| XCR-03 | Anthropic: one user message, emission order, `is_error`, empty content omitted; Chat: one `role:tool` per id, `{"error":…}` wrapper, pass-through otherwise | unit | `./gradlew :providers:test --tests '*AnthropicEncoderTest' --tests '*ChatMessageEncoderTest' --offline -q` | ✅ edited |
| XCR-03 | Empty/absent/null/blank arguments -> `{}` on both decoders; non-object stays `MalformedToolArgs` | unit | `./gradlew :providers:test --tests '*ChatDecoderTest' --tests '*AnthropicDecoderTest' --offline -q` | ✅ edited |
| XCR-03 | Goldens: manifest well-formed, canonical/idempotent, hygiene (no key/Bearer, ids GOLDEN-shaped, thinking untouched), every row replays | unit | `./gradlew :providers:test --tests '*ConversationGoldenTest' --tests '*ConversationSanitizerTest' --offline -q` | ❌ new |
| XCR-01 | One suite, three dialects, parallel calls + zero-arg + error round trip | conformance | `./gradlew :providers:test --tests '*MultiTurnConformanceTest' --offline -q` | ❌ new |
| XCR-01/SC4 | Append-only (messages + tools/system prefix) and cache directive per dialect; negative control fails | conformance | `./gradlew :providers:test --tests '*MultiTurnConformanceTest' --offline -q` | ❌ new |
| D-08 | Recorder: ceilings, shared id map, sanitize-then-replay check, nothing written on refusal | unit (key-free) | `./gradlew :providers:test --tests '*ConversationCaptureRunTest' --offline -q` | ❌ new (name has no "Live") |
| D-12 | Live capture (opt-in, outside `check`) | manual, key-gated | see Golden Capture Design run commands | n/a |

### Sampling Rate
- **Per task commit:** the quick command for the touched classes + `./gradlew :providers:detekt :providers:scanBannedConstructs --offline -q`
- **Per wave merge:** `./gradlew :providers:check :core:check --offline` (includes the 4.12.0 / 5.2.1 / 5.5.0 legs)
- **Phase gate:** `./gradlew check --offline` green, plus the repo scripts Phase 5 used (`scripts/review-api-surface.sh`, `scripts/verify-negative-controls.sh`, `scripts/verify-repo-hygiene.sh`, `scripts/verify-api-dump.sh` — present in `scripts/`) before `/gsd-verify-work`.
- **Contention rule (inherited):** one Gradle invocation per working tree at a time; one plan per wave.

### Wave 0 Gaps
- [ ] `ConversationCheckTest` — XCR-02/03 (created by plan 01 alongside the code, test-first)
- [ ] `golden/conversations/` dir + `MANIFEST.tsv` + loader/hygiene (plan 04)
- [ ] `conformance/` package (plans 05-06)
- Framework install: none.

## Security Domain

> `security_enforcement` is `true`, `security_asvs_level` 1, `security_block_on` high (config read this session).

### Applicable ASVS Categories
| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | no (no new auth) | — |
| V3 Session Management | no | — |
| V4 Access Control | no | — |
| V5 Input Validation | **yes** | Model output is untrusted: decoders validate shapes, ids non-blank, arguments object-or-lenient-empty; wrapper/fixtures built with JSON builders; `ConversationCheck` validates the engine-built transcript before sending |
| V6 Cryptography | no (signatures are opaque, never interpreted) | — |
| V7 Error Handling & Logging | **yes** | Failures are `Other(code)` tokens only; no ids/content/args in reasons, `toString`, traces; canary test |
| V8 Data Protection | **yes** | Secrets only via `with-test-keys`; raw bodies under `build/` only; sanitized fixtures hygiene-scanned (key/Bearer/ids); tool result content never logged |
| V12/V14 Files & Config | yes (small) | Capture tasks opt-in, outside `check`, never up-to-date; no key file reads |

### Known Threat Patterns for this stack
| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| API key or auth header written into a committed golden | Information disclosure | Never record headers; `KEY_IN_TEXT`/`BEARER_IN_TEXT` scan refuses the file; keys only in the live process env via wrapper |
| Model-controlled text (ids, tool args, thinking) echoed into an exception/trace | Information disclosure | Kind-only failure codes; `AssistantMessage/ToolResult/NativeReplay.toString` already redact |
| JSON injection via tool error text into the Chat wrapper | Tampering | `buildJsonObject`, never concatenation |
| Silent cross-provider transcript replay (wrong-model signature, cache poisoning, mixed history) | Tampering / Repudiation | `replay_mismatch` pre-flight, zero requests |
| Unbounded spend in the recorder | Denial of service (cost) | Per-conversation and per-task request ceilings counted before sending; no transport retry in the recorder |
| Thinking text / reasoning leakage into logs | Information disclosure | Recorder prints ids/counts only; thinking fields exempt from rewriting but never printed |

## Plan Size and Wave Guidance

Executors use worktrees; plans touching the same module contend on Gradle, so **one plan per wave**, strictly sequential, authoritative `wave:` frontmatter, `depends_on` the previous plan. Each plan <= 3 tasks and a focused file set. Convention (copy Phase 7): tracer-first, test-first, an explicit `prohibitions` list (no public API change, no goldens under `golden/chat` edited, no live HTTP, no `api.txt`, no tag, hygiene staging rules above).

| Wave | Plan | Objective | Main files | Tasks |
|------|------|-----------|------------|-------|
| 1 | 08-01 | `ConversationCheck` + fail-loud replay key + coverage pre-flight (XCR-02, D-03/D-04) | NEW `providers/transcript/ConversationCheck.kt`, `ConversationCheckTest`; edit both `*MessageEncoder.kt`, both `*Transport.kt`, `AnthropicEncoderTest`, `ChatMessageEncoderTest`, `AnthropicTransportTest`, `ChatTransportTest` | 3: guard + unit tests; wire into transports/encoders and flip the two pinned tests; MockWebServer zero-request + canary |
| 2 | 08-02 | Per-dialect tool-result encoding: emission order, Anthropic omit-empty-content, Chat `{"error":…}` wrapper (XCR-03, D-04/D-05) | both `*MessageEncoder.kt`, `AnthropicEncoderTest`, `ChatMessageEncoderTest` | 2 |
| 3 | 08-03 | Lenient empty-args on both decoders; Chat replay repair (role, arguments) (D-06) | `ChatResponseParts.kt`, `AnthropicDecoder.kt`, `ChatMessageEncoder.kt`, `ChatDecoderTest`, `AnthropicDecoderTest` | 2 (flip the pinned bad-args list deliberately; do **not** edit Phase 5 golden resources) |
| 4 | 08-04 | `ConversationSanitizer` (shared id map, thinking exempt, canonical compact) + manifest loader/hygiene + **derived** fixtures for 3 dialects (from Phase 5 real bodies + documented shapes, incl. parallel, zero-arg, redacted_thinking, interleaved text, `reasoning_details`) | NEW test kotlin `conformance/ConversationSanitizer.kt`, `ConversationGolden.kt`, tests; NEW `golden/conversations/**` | 3 |
| 5 | 08-05 | `WireDialect` adapters + abstract suite part 1: round trip, replay identity, stamp mismatch, coverage, order, error encoding, empty args; 3 subclasses (tracer: Anthropic first) | NEW `conformance/WireDialect.kt`, `MultiTurnConformanceSuite.kt`, 3 subclasses | 3 |
| 6 | 08-06 | Suite part 2: append-only across iterations (bytes), cache directive per dialect, negative control, reshape-is-single-turn note | `MultiTurnConformanceSuite.kt` (+ subclass hooks) | 2 |
| 7 | 08-07 | Recorder extension, **code only**: `ConversationScript/Plan`, runner, Anthropic direct-HTTP recorder method, Chat conversation method, Gradle gating/props, key-free `ConversationCaptureRunTest` | edit `providers/build.gradle.kts`, `AnthropicLiveCaptureTest`, `ChatCompletionsLiveCaptureTest`; NEW `conformance/ConversationCapture.kt`, `ConversationCaptureRunTest` | 3 |
| 8 | 08-08 | **Gated** capture run (`autonomous: false`): checkpoint via master -> orchestrator; run both tasks through `with-test-keys`; commit sanitized goldens + manifest rows + evidence; re-run the suite over captured rows | NEW `golden/conversations/**` captured rows, `evidence/live-multiturn-capture.txt` | 3 |

Notes: plans 01-03 all edit `ChatMessageEncoder.kt`/`AnthropicMessageEncoder.kt` — another reason for strict series. Plan 04 is the only plan with no main-source overlap, but still keep it serial (Gradle). Plans 01-07 must pass with **no keys**; only plan 08 needs them, so a deferred capture leaves the phase green with an explicit SC3-Anthropic "real half pending" note.

## Sources

### Primary (HIGH confidence)
- Codebase (read this session): `core/.../transcript/{Message,NativeReplay,ModelRequest,ModelResponse,AssistantPart}.kt`; `core/.../provider/{ProviderRequest,BoundModel,ModelResult,AiProvider,CachingMode,ModelCapabilities,ModelRouter}.kt`; `core/.../failure/FailureReason.kt`; `providers/.../anthropic/*`, `providers/.../chat/*`; `providers/build.gradle.kts`; all `golden/chat/responses/captured/*.json`; `ChatCompletionsLiveCaptureTest.kt`, `AnthropicLiveCaptureTest.kt`, `ChatGoldenSanitizer.kt`, `ChatGoldenReplayTest.kt`, `ChatCaptureRunTest.kt`, `ChatCaptureCallPlanTest.kt`; `05-12-SUMMARY.md`, `05-CONTEXT.md`, `05-VALIDATION.md`.
- Anthropic docs (first-party): platform.claude.com/docs/en/build-with-claude/{thinking, extended-thinking, preserved-thinking, thinking-tool-workflows, thinking-troubleshooting}; /agents-and-tools/tool-use/handle-tool-calls; /about-claude/pricing.
- OpenRouter public models endpoint `GET https://openrouter.ai/api/v1/models` (queried 2026-10-01).
- Local scratch verification of kotlinx `JsonElement` canonicalization against the project's pinned 1.11.0 jar.

### Secondary (MEDIUM confidence)
- OpenRouter docs: openrouter.ai/docs/use-cases/reasoning-tokens; openrouter.ai/docs/guides/best-practices/reasoning-tokens (via fetch summarizer).
- OpenAI API reference (TypeScript) `ChatCompletionAssistantMessageParam` vs `ChatCompletionMessage` (via fetch summarizer); openai-python `_utils/_transform.py`.
- langchain-ai/langchain#40563 (provider extras on tool calls).

### Tertiary (LOW confidence, flagged)
- Web-search summaries of community issues for OpenAI tool-message ordering errors, empty/duplicate tool-call ids (agno#3105, langchain4j#1632, portkey error library, Google AI forum thread, smolagents#2683, openclaw#40897). The seam's generic `webfetch`/`websearch` providers classify as LOW; the Anthropic/OpenRouter/OpenAI items above are first-party pages and are tagged `[CITED]` accordingly.

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — no change; versions read from the repo.
- Architecture / code map: HIGH — every claim traced to files read this session.
- Anthropic replay rules: HIGH — first-party docs.
- OpenAI/OpenRouter echo tolerance: MEDIUM — schema + SDK behavior, not an observed echo; the capture (R1/R2, optional O-P) closes it.
- Capture cost/ceilings: MEDIUM — estimates; ceilings are hard.
- Pitfalls: HIGH for the code-derived ones (1-4, 6-7), MEDIUM for 5/8.

**Research date:** 2026-10-01
**Valid until:** 2026-10-15 for Anthropic/OpenRouter facts (fast-moving: new models and thinking rules shipped weekly); codebase facts are valid until the next merge touching `providers/`.
