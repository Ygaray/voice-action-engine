# Feature Research

**Domain:** Multi-provider LLM "command → app action" engine library for Android (tool calling, tier ladder, agent loop, confirm/commit/undo, BYO-key)
**Researched:** 2026-09-29
**Confidence:** HIGH for consumer needs (read directly from the SB and CT port sources). MEDIUM overall for the ecosystem and provider-behavior claims: the research seam rates every web and webfetch source LOW, but the load-bearing provider facts come from official vendor docs and two of them are independently corroborated by the port code. Details are under Sources.

---

## How to read this

Every feature is tagged with **who needs it**:
- **SB**: SecondBrain. A 17+1-tool Anthropic agentic loop with prompt caching, a suspend-until-tap confirm gate, and per-tool undo.
- **CT**: CalTracker. A single forced-tool extraction across 3 providers, local resolution, weak-match/batch "Proposed" confirm, and row undo.
- **ECO**: an ecosystem norm, meaning LangChain4j, Koog, Spring AI, the Vercel AI SDK, the Anthropic/OpenAI SDK tool runners, or the OpenAI Agents SDK has it.

"Table stakes" here means **a Wave-1 consumer cannot migrate without it, or the contract requires it**. Having it in every framework doesn't make something table stakes for this library. The consumers are two known apps, not a market.

---

## Feature Landscape

### Table Stakes (consumers can't migrate without these)

#### A. Pipeline & outcomes (`:core`, step 2)

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| `CommandInput` → `CommandPipeline` → `StrategyOutcome` (`Completed / Escalate(reason, carry?) / NoMatch / Failed`) | Contract §5.1 | MEDIUM | `Escalate`/`NoMatch` climb to the next tier; `Completed`/`Failed` stop. Pure Kotlin, and JVM-testable with the fake provider. |
| **Never-throw outcome collapse; cancellation always propagates** | SB `AgentLoopResult` KDoc, CT `AiProvider` KDoc. Both apps built on it. | LOW | Every path ends in a typed outcome. `CancellationException` is rethrown everywhere, including inside gate waits. CT relies on `withContext` re-checking. The engine should rethrow explicitly (SB style), not rely on that implicit check. |
| **Failure-reason → Escalate-vs-Failed mapping** | Tier ladder semantics. A bad key doesn't get better one tier up. | LOW | Recommended default: transport/auth/billing/config failures → `Failed` (stop). Model-side "couldn't do it" (no tool call, prose answer, refusal) → `Escalate`. Budget → `Failed`, carrying `executedTools`. Make the mapping overridable per tier in `TierPolicy`. |
| **Committed work reported on every outcome variant** | SB T-165-25: "a mutation is never hidden behind a failure outcome". `executedTools` rides on `Done`, `Unavailable` and `BudgetExceeded`. | LOW | `Failed` and budget outcomes still carry the list of committed/held actions. This is non-negotiable for undo correctness. |
| `TierSelector.Linear` (+ `Fixed` for tests) | Contract §5.1 | LOW | `Router` is v1.1. |
| `TierPolicy`: `offlineOnly`, `maxTier`, `allowedProviders`, per-command turn/token ceilings | Contract §5.1. Both apps expose these as settings (§6.4/§6.5). | LOW | **Express the "cost ceiling" in tokens, never dollars** (see Anti-Features). Default limits come from SB constants: 6 iterations, 60k cumulative tokens, 4096 max_tokens per turn. They are policy defaults, not constants. |
| `carry` on `Escalate` | Contract §5.1. Avoids redoing extraction one tier up. | LOW | Type it as an app-opaque payload plus optional neutral transcript fragments. v1.1 Plan/Router depend on this, so get the shape right in v1.0. |

#### B. Tool declaration & choice (steps 3a/3b/5)

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| **Neutral `ToolSpec` (name, description, JSON-Schema object) supplied by the app via `ToolSpecProvider`** | SB hands over `{system, tools}` JSON (E2). CT hand-writes a schema per provider. | LOW | Plain `JsonObject` schemas. SB's `anthropicInputSchema` already emits JSON, not annotations. |
| **Per-provider tool encoding** (Anthropic `tools[].input_schema` vs OpenAI `tools[].{type:function, name, parameters, strict}`) | CT `LogFoodRequestBuilder` vs `OpenAiLogFoodRequestBuilder` | MEDIUM | One mapper per dialect. OpenRouter uses the OpenAI dialect. |
| **Schema dialect sanitization for OpenAI strict mode** | CT strips `exclusiveMinimum`/`minimum`/`maximum` for OpenAI strict (Pitfall 2) but keeps them for Anthropic | MEDIUM | The engine strips keywords unsupported in strict mode for the OpenAI dialect, and lets the app override the schema per provider. Without this, CT's `ToolSpecProvider` has to return per-provider schemas, which is acceptable but leaks dialect knowledge into the app. Strict mode also needs `additionalProperties:false` plus all properties `required`. CT already complies. |
| **Deterministic, byte-identical tool + system serialization** | SB builds `toolDefinitions` once, sorted by name, "byte-identical on every request". Any byte drift kills the cache. | LOW | Sort by name, build once per spec set, and never re-serialize per call. The Vercel AI SDK added `toolOrder` for exactly this. |
| **Forced tool choice** (`tool_choice: {type:tool,name}` / OpenAI `{type:function,function:{name}}`) | CT SingleShot core mechanism | LOW | Needed for `SingleShotStrategy`. |
| **Forced-tool fallback for models that reject it** (→ `auto` + `strict:true` + inline "Call the X tool" instruction) | CT `AnthropicKnownTool400Ids`. Anthropic docs: Opus 5.5, **Sonnet 5.5**, Fable 5.1 and Mythos 5.1 return 400 `tool_choice: type "tool" and "any" are not supported for this model.` | MEDIUM | **CT's hardcoded set is already stale: it lacks `claude-sonnet-5-5`.** Recommended: a seed capability list, plus a **reactive fallback** that recognizes that specific 400 and retries once with the auto+strict encoding, then remembers the capability for the process. It applies through OpenRouter too (`anthropic/*` ids). |
| **`tool_choice: auto` with the full tool set** | SB agentic loop | LOW | |
| **Multiple tool calls in one turn handled sequentially, one batched `tool_result` message back** | SB `dispatchToolUseTurn`. The SB gate's mutex contract assumes sequential dispatch. | LOW | Parallel execution is an anti-feature (see below), but parallel *emission* by the model must be handled. |

#### C. Agentic loop (steps 6a/6b)

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| **Neutral multi-turn transcript model + per-provider mappers** (Anthropic `tool_use`/`tool_result` blocks ↔ OpenAI `tool_calls` + `role:tool`) | Contract A8/6a | HIGH | The hardest piece of v1.0. Validate round-trips with golden JSON fixtures per dialect. |
| **Opaque provider content preserved verbatim across turns** (thinking / redacted_thinking blocks with signatures) | SB echoes `rawContent` verbatim. Anthropic docs: "every `thinking` and `redacted_thinking` block from the assistant turn must be passed back exactly as received", or 400. Opus 5.5 and Sonnet 5.5 always think, and SB's Sonnet fallback runs adaptive thinking. | MEDIUM | **The neutral model must carry a provider-raw assistant payload and replay it byte-for-byte.** A model that re-serializes from neutral fields will break SB's Sonnet fallback and every newer Claude model. It must also stay append-only, because thinking blocks are bound to their prefix. |
| **Iteration bound + final-iteration guard** | SB: max 6. No tool ever runs on the last permitted iteration, because its result could never be read. | LOW | Port as-is. ECO equivalents: Vercel `stopWhen(stepCountIs)`, LangChain4j `maxSequentialToolsInvocations`, OpenAI Agents `max_turns`, Anthropic tool runner `max_iterations`. Every framework has a bound. |
| **Cumulative token ceiling, checked before dispatching a tool turn** | SB 60k, summing input + output + cache_creation + cache_read | LOW | Keep SB's sum for parity. The normalization note in section F applies. |
| **Whole-turn validation before any dispatch** (≥1 tool_use, non-blank id/name, object-or-null input, unique ids) | SB `isValidToolUseTurn` | LOW | An invalid turn → `MALFORMED_RESPONSE` with nothing executed. |
| **Unknown/hallucinated tool name → `is_error` tool_result, not a crash** | SB `unknownToolResult`. ECO: LangChain4j `hallucinatedToolNameStrategy`, Vercel `NoSuchToolError`. | LOW | Feed the error back to the model and let it recover. |
| **Repeated-tool-failure abort** (same tool fails validation twice → `TOOL_FAILURE`) | SB `registerFailureAndCheckExceeded` | LOW | Stops the loop before it burns the budget on a stuck tool. |
| **`ToolExecutor` seam, two-phase: `prepare` → `Finished(result)` or `Mutate(apply)`** | SB `ToolStep`. The gate is consulted **only** for validated, resolved mutations, never for reads or invalid calls. | MEDIUM | Recommended engine shape. It is the only way `PreApplyGate` can be engine-enforced inside the loop (A6) rather than left to each app's executor. |
| **Stop-reason taxonomy** | SB: `tool_use`, `end_turn`, `stop_sequence`, `max_tokens`, `refusal`, `pause_turn`, `model_context_window_exceeded`, null, unknown. OpenAI: `tool_calls`, `stop`, `length`, `content_filter`, plus `message.refusal`. | MEDIUM | Map both into one neutral enum (see section D). |

#### D. Typed failure reasons (the "loud, specific" Core Value)

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| **Neutral failure taxonomy at SB granularity or finer, never CT's opaque `Unavailable`** | PROJECT.md port cleanup. YAT renders specific failure states. | MEDIUM | Proposed reason set is below. |
| **`NotConfigured` / no key, short-circuited before any network call** | CT `hasConfiguredKey` → `VoiceLogUiState.NotConfigured` (AID-01) | LOW | Also expose a cheap `isConfigured()` pre-check so the app can gate the mic button. |
| **Never substitute another provider's key** | CT T-68-05, proven by test | LOW | A missing key for the active provider fails without trying any other provider. |

Proposed neutral `FailureReason` (each value carries `httpStatus?`, `providerErrorType?` and `requestId?`, never message bodies that could echo input):

| Group | Reasons | Maps from |
|---|---|---|
| Config | `NotConfigured`, `ProviderNotAllowed` (policy), `ProviderUnavailable` (ON_DEVICE absent under `offlineOnly`), `ModelUnsupported` (forced-tool 400 after the fallback also failed) | CT NotConfigured, contract A5 |
| Auth/account | `AuthInvalid` (401), `PermissionDenied` (403, incl. OpenRouter moderation), `BillingOrCredits` (Anthropic 402 `billing_error`, OpenRouter 402), `SpendCapReached` (Anthropic 429 with no `retry-after`, or 400 spend limit) | Anthropic and OpenRouter error docs |
| Transient | `RateLimited(retryAfter?)` (429), `Overloaded` (529/503), `ServerError` (5xx), `Timeout` (408/504/call timeout), `Network` (IOException) | SB `NETWORK`/`HTTP_ERROR`, split finer |
| Request | `BadRequest` (other 400), `RequestTooLarge` (413), `ContextWindowExceeded` | SB |
| Model-side | `Refusal` (stop_reason `refusal`, OpenAI `message.refusal`, `content_filter`), `MaxTokens` (`max_tokens`/`length`), `NoToolCall` (a forced/expected tool call came back as prose; **CT currently conflates this with Refused**), `PauseTurn`, `UnknownStopReason` | SB + CT |
| Parse | `MalformedResponse` (outer envelope), `MalformedToolArguments` (inner `arguments` JSON string, CT Pitfall 8), `SchemaViolation` (decoded but required fields missing) | CT BaseAiProvider |
| Loop | `BudgetExceeded(MAX_ITERATIONS / TOKEN_CEILING)`, `ToolFailure` | SB |
| OpenRouter quirk | Errors can arrive **with HTTP 200** in the body (`error` object / `finish_reason:"error"` + `native_finish_reason`) | OpenRouter error docs |

#### E. Prompt caching (step 3a + A10 verification bar)

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| **Anthropic explicit breakpoint: one `cache_control:{type:ephemeral}` on the system block, none on messages** | A10 breakpoint parity with SB `buildRequestBody`. Tools and system are cached together because the prefix order is tools→system→messages. | LOW | This is the Gate-1 path. Haiku 4.5's **minimum cacheable prefix is 4,096 tokens**. SB's ~7k fixture clears it. |
| **Caching as a provider capability, not a strategy concern** | Contract §5.1 | LOW | Strategies declare "this prefix is stable". Each provider encodes that its own way: Anthropic explicit markers, OpenAI automatic, OpenRouter passthrough. |
| **OpenAI: automatic caching with no markers; read `usage.prompt_tokens_details.cached_tokens`** | OpenAI docs: automatic ≥1,024 tokens; tool names/descriptions/schemas/order must be identical | LOW | Nothing to send. Only usage parsing. |
| **OpenRouter: pass `cache_control` through for `anthropic/*` models** (on the system message's text content part, OpenAI-format) and parse `prompt_tokens_details.cached_tokens` / `cache_write_tokens` | OpenRouter caching docs | MEDIUM | Automatic for OpenAI/DeepSeek/Gemini-backed ids. OpenRouter routes sticky to the same provider after a cached hit, for 10 min. JVM-tested only (A8). |
| **Byte-stable prefix discipline** | Any per-call variation before the breakpoint (date, language, transcript in the system prompt) silently disables the cache | LOW | Put per-call context (date-time, zone, language, transcript) in the **first user message**, never the system block. SB and CT already do this. |
| **Usage normalization across providers** | Anthropic `input_tokens` **excludes** cached tokens (total = input + cache_read + cache_creation). OpenAI `prompt_tokens` **includes** `cached_tokens`. | LOW | Normalize to `{uncachedInput, cacheRead, cacheWrite, output}` or the trace, the ceiling and YAT all disagree by provider. |

#### F. Telemetry (step 2)

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| **Trace on every result**: per-tier attempts, escalation reasons, per-iteration records (stop reason, tool names, normalized tokens incl. cache read/write), latency, which tier/strategy/provider/model handled it | Contract §5.1 `PipelineTelemetry`, SB `AgentIterationRecord`, YAT "handled by" indicator | MEDIUM | Deterministic and JVM-assertable. It is the evidence base for tuning ladders. |
| **Optional live typed-event callback** | Decided (PROJECT.md). Replaces SB's free-text `AgentLoopTelemetry.log(line)`. | LOW | Typed events, no Flow (app can wrap). |
| **Secrets never in telemetry**: no key, transcript, tool args or tool_result content | SB T-165-01, CT T-53-03 | LOW | Tool **names** and held-tool names are fine (SB logs them). |

#### G. Confirm-before-commit & undo (step 2 hooks; A2/A6/E1)

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| **`PreApplyGate.admit(action) → Proceed / NeedsConfirmation(reason, payload?)`** | SB `MutationGate.admit(toolName, input)`, CT weak-match gate, A2 | MEDIUM | The engine consults it only for validated mutations (see the two-phase `ToolExecutor` in section C). |
| **Two confirmation modes, both required** | **SB suspends**: `VoiceConfirmGate.admit` awaits a dialog tap *inside the running loop* (120 s timeout, mutex, one pending at a time). **CT defers**: it returns `Proposed`/`ProposedBatch` state and the user commits *later*, after optionally editing quantity, food or date. | HIGH | (1) *Suspend*: the gate awaits, and the engine continues with Admit or Hold. (2) *Defer*: the pipeline returns a **non-committing `NeedsConfirmation` outcome carrying the pending action(s)**, and the app commits later through `CommitSink`, possibly with an edited payload. A single suspend-only design breaks CT. A defer-only design breaks SB. |
| **Held ≠ success, ever** | SB `HELD_FOR_CONFIRMATION_CONTENT` = `{"applied":false,"status":"held_for_confirmation"}` fed back as a non-error tool_result. SB's system prompt depends on that exact string. | LOW | The default held content must be **byte-compatible with SB** and configurable. A held action appears in the trace and outcome as `held`, never `committed`. |
| **Fail-closed**: only an explicit admit proceeds. Timeout, decline, dismiss and snapshot failure → Hold. An unknown/unclassified action → confirm. | SB `VoiceConfirmGate` + `MutationTierPolicy` (null tier → confirm) | LOW | Cancellation while a confirm is pending propagates and is **never** converted into a decision. |
| **Per-dispatch gate → apply → result context** (the snapshot captured at gate time travels to the undo record) | SB `MutationDispatchContext`, one fresh instance per dispatch, never keyed by the model's `tool_use` id (ids may repeat across turns) | MEDIUM | Engine form: `NeedsConfirmation`/`Proceed` may carry an opaque app payload (the snapshot), which is attached to that action's executed-call record. Never key it by model-supplied ids. |
| **Batch proposals** | CT: 2+ items **always** propose (never auto-commit), with a per-row `needsAttention` flag | LOW | The gate sees the whole resolved outcome for SingleShot, not item-by-item. The payload is app-typed. |
| **`CommitSink.commit(action) → UndoHandle`; `undo(handle) → Boolean` (verified)** | SB `VoiceUndoOperations`: every inverse verifies it changed something and returns `false` otherwise ("Failed", never a false "Undone"). CT deletes by row id, with per-item batch undo. | MEDIUM | The engine defines the shape and carries undo handles on the outcome. The app owns the inverse logic and the undo UI (YAT `UndoHistoryStore`). |

#### H. Providers, keys & transport (steps 3a/3b/4)

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| `AiProvider` per `ANTHROPIC / OPENAI / OPENROUTER / ON_DEVICE` + `ProviderRouter` (a plain map, no Hilt) | CT design, contract §5.1 | LOW | |
| **Per-call provider/model/key resolution seam** (a switch takes effect on the next call) | CT `ProviderRouter` reads active provider, key and model fresh per call | LOW | `suspend fun resolve(): ProviderSelection?`. The library never reads app storage. |
| **`ON_DEVICE` slot + runtime capability gate** | A5/L10 | LOW | Absent on the S22s: skip that provider/tier cleanly. Under `offlineOnly` → `Failed(ProviderUnavailable)` or `NoMatch` (decide in discuss). |
| **`:keystore`: AES-256/GCM AndroidKeyStore, per-provider aliases, DataStore persistence** | SB/CT `KeystoreCrypto(Seam)`, SB `AnthropicApiKeyRepository` | MEDIUM | Carry over SB's key-state model: `NotConfigured / Configured(fingerprint) / Unreadable`. `Unreadable` is **not** auto-cleared. Serialize save/remove. Use `java.util.Base64` for JVM testability. |
| **Cancellation-safe `Call.await()`** (enqueue + `invokeOnCancellation { cancel() }`) | CT WR-03, ported verbatim into SB | LOW | Uses the 4.12 API surface (`body?.string()`) per A1. |
| **No logging interceptor; key only in headers; HTTPS fixed base URLs, overridable for tests only; redacted `toString()`** | SB + CT security invariants | LOW | |
| **Bounded call timeout** | CT: `callTimeout` guarantees no hang → `Timeout` | LOW | Default from policy/config. |
| **Per-provider headers**: Anthropic `x-api-key` + `anthropic-version: 2023-06-01`; OpenAI/OpenRouter `Authorization: Bearer` | Both ports | LOW | OpenRouter's optional `HTTP-Referer`/`X-Title` stay omitted (CT decision). |
| **OpenRouter: vendor-namespaced model ids** (`openai/gpt-4o-mini`) **+ `provider.require_parameters: true` when forcing a tool** | CT Pitfall 7. OpenRouter routing docs: `require_parameters` hard-filters providers that would silently ignore `tool_choice`. | LOW | Without it, forced tool calls can land on a backend that ignores them, or fail with 404 "No endpoints found that support the provided 'tool_choice' value". |

#### I. Strategies & context

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| `SingleShotStrategy`: one forced call → decoded tool input → app `OutcomeResolver` (local resolution) → gate → commit | CT port (step 5) | MEDIUM | CT's weak-match math (`PARSE_CONFIDENCE_FLOOR` 0.8, `WEAK_MATCH_THRESHOLD` 0.75, unit mismatch, non-positive quantity) stays in the app's gate/resolver. The engine only routes. |
| `AgenticLoopStrategy` over `ToolExecutor` on the neutral transcript | SB port (6b) | HIGH | See section C. |
| **Per-call context preamble in the first user message** (local date-time + zone, optionally language) | Both apps inject "Current local date-time … (zone)" / "Today's date is … (zone)" | LOW | Supply it through `CommandInput.context` + `ToolSpecProvider`, never the cached system block. A small helper is a nice-to-have. |
| **Tool results treated as data**: the model must not follow instructions inside them | SB system prompt clause | LOW | App-owned prompt text. The engine must not rewrite tool_result content (see Anti-Features). |

---

### Differentiators (valuable, not required to migrate)

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| **Conservative, cancellable retry/backoff for transient failures** | Neither port retries today. The official Anthropic SDKs retry twice by default on connection errors, 429, 5xx and 529, honoring `retry-after`. On a phone, 529/overloaded is the most common "it just failed". | LOW | Recommended for v1.0 at `maxRetries = 1`, set in policy. Retry only `Network`-before-response, 408, 429 **with** a `retry-after` ≤ a cap, 500/502/503/529. Never retry 400/401/402/403 or a spend-cap 429. **Retry HTTP requests only, never tool execution.** Each retry appears in the trace. |
| **Cache-miss diagnostics** | Anthropic silently skips caching below the model minimum, with no error: both cache fields are 0. That is exactly the A10 false-fail risk. | LOW | Typed event `CacheNotEngaged(turn, prefixSize)` when turn ≥2 has `cacheRead == 0` on a provider with a cache capability. SB already logs `prefix_chars` on iteration 1. Formalize it. Must stay quiet for CT's tiny SingleShot prefix, which can never cache on Haiku (4,096 min). |
| **Anthropic top-level automatic caching as the "moving message breakpoint" addition** | A10 allows a moving message breakpoint as an addition. Anthropic now supports a top-level `cache_control` that advances automatically. | LOW | Only **in addition to** the system-block breakpoint. Watch the budget: 4 explicit breakpoints + automatic returns 400. Useful for longer agentic runs (turns 3+ read prior turns from cache). |
| **OpenAI `prompt_cache_key`** (a stable key per app + tool-set hash) | Improves cache routing on OpenAI/OpenRouter for the agentic prefix | LOW | Cheap. Put it in the OpenAI mapper. |
| **`requestId` on failures** (Anthropic `request-id` header/body) | Makes "loud failure" actionable, e.g. a support ticket or a log correlation | LOW | Not secret. Safe to carry. |
| **Pending-confirmation handle with single-resolution semantics** | SB resolves by id (a stale id is a no-op). CT guards double-taps with `confirmProposedInFlight`. | LOW | The engine hands out a handle whose `resolve()` is idempotent. This removes a whole bug class from each app. |
| **Commit-time `CommitSink` notification (not end-of-run)** | If the command is cancelled after a mutation already committed mid-loop, a `CancellationException` discards `executedTools`, so the undo record is lost | MEDIUM | Call `CommitSink`/the event callback *as each action commits*, so the app holds the undo entry even if the run is later cancelled. **Strongly recommended.** It closes a real hole in the SB design. |
| **Tiny Kotlin schema builder** (`toolSpec("name") { string("food_query"); number("quantity", exclusiveMin = 0) … }`) emitting JSON Schema | Makes the README "an AI agent can wire it" goal easier. No reflection. | LOW | Optional sugar. Raw `JsonObject` must remain first-class, because SB's fixture is raw JSON. |
| **Model catalog fetch per provider** (CT `ModelCatalogProvider`: Anthropic/OpenAI with a key, OpenRouter public, tool-capable filter) | Feeds YAT's model card. CT already has it. | MEDIUM | **Not in §6.2.** Keep it app-side for v1.0 and flag it to CT/the orchestrator at reconvene as a v1.x candidate. |
| **Forced-tool capability memo** (learned from the 400 fallback, per process) | Avoids paying the failed 400 round-trip on every call | LOW | Pairs with the reactive fallback in B. |

---

### Anti-Features (deliberately NOT building)

| Feature | Why Requested | Why Problematic | Alternative |
|---------|---------------|-----------------|-------------|
| **Streaming (SSE) responses** | Every framework has it, and it is "more responsive" | The outputs are one tool call or one short sentence. The UI waits for an *outcome*, not tokens. Streaming triples the parsing surface (3 SSE dialects), forces partial tool-JSON assembly, adds mid-stream errors after an HTTP 200, and needs `okhttp-sse` (another artifact to hold at the 4.12 floor). Anthropic only recommends streaming for multi-minute requests. Ours are capped at 4096 tokens per turn. | Non-streaming calls with a bounded timeout. Live progress comes from the per-iteration telemetry callback ("looking up tags…"). Revisit only if a consumer shows a real latency problem. |
| **Parallel tool execution within a turn** | Speed. The OpenAI Agents SDK and Vercel AI SDK run tools concurrently. | SB's `VoiceConfirmGate` mutex contract explicitly assumes sequential dispatch. Concurrent mutations plus confirm dialogs invite a 2-minute silent block. Undo ordering gets ambiguous. | Handle multiple emitted calls **sequentially** and batch the results. Consider OpenAI `parallel_tool_calls:false` for SingleShot (verify in phase research). |
| **Dollar-cost accounting / price tables in the library** | "Per-command cost ceiling" in the contract | Prices drift monthly and vary by model and cache tier (Opus 5.5 reads are 0.05x, others 0.1x). The library would ship wrong numbers. | Ceilings in **tokens and turns**. Expose normalized usage so the app can multiply by its own price table if it wants dollars. |
| **Annotation/reflection tool declaration** (`@Tool` in LangChain4j/Spring AI/Koog) | Ergonomic | Reflection on Android is R8-hostile, the serialization order isn't byte-stable (it kills caching), and SB's A10 fixture is raw JSON | Raw `JsonObject` specs + the optional builder (differentiator). |
| **Conversation memory across commands / history compression** (LangChain4j `ChatMemory`, Koog compression) | "Context-aware follow-ups" | Every voice command is independent. Cross-command memory adds privacy surface (transcripts persisted), makes cache prefixes drift, and has no consumer ask. Loops are bounded at about 6 turns, so there is nothing to compress. | Stateless per command. `carry` handles intra-pipeline handoff. |
| **Engine-side structured-output mode** (`response_format: json_schema`, Anthropic `output_config.format`) as a second extraction path | "Proper" structured output | Forced tool calling already *is* the structured-output mechanism on all 3 providers and fits ON_DEVICE. A second path doubles the mappers and tests. | Forced tool + `strict` + the auto-strict fallback. |
| **MCP client / remote tools** (Koog, Spring AI) | Ecosystem trend | Tools are in-process Room/repository calls. Remote tools reintroduce network, auth and injection surface. | `ToolExecutor` seam. SB's MCP surface stays separate, app-side. |
| **DI-framework integration** (Hilt modules, `@Inject`) | Both apps use Hilt | Forces consumers onto a DI framework and version (port cleanup rule) | Plain constructors/builders. The README shows a Hilt wiring example *in docs only*. |
| **Automatic cross-provider fallback** (e.g. Anthropic down → try OpenAI) | Resilience | Needs a second key, which would breach CT's "never substitute a key" (T-68-05). The user chose a provider. Silent provider swaps make "handled by" lie. | A typed failure plus the app's own UI. The tier ladder handles *capability* escalation, not provider failover. |
| **Library-owned UI or confirm dialogs** | Convenience | Contract: YAT owns presentational UI; apps map outcomes to YAT props | The engine emits the `NeedsConfirmation` state/handle. The app renders it. |
| **Rewriting/wrapping tool_result content** (e.g. auto "treat as data" fences) | Prompt-injection hardening | Changes bytes the app's prompt was tuned against. SB's system prompt already carries the clause. | Leave it to the app prompt. Document it in the README. |
| **Logging interceptors / verbose HTTP debug mode** | Debugging | Leaks `x-api-key`/Bearer (SB T-165-01, CT T-53-03) | Typed telemetry + `requestId`. |
| **Retrying tool execution or re-running a committed turn** | "Make it robust" | Double-commits mutations. CT's `log_entry` WR-03 exists specifically to prevent duplicate diary rows. | Retry HTTP only. Report committed actions faithfully. |

---

## Feature Dependencies

```
Neutral ToolSpec + per-dialect encoder ──requires──> deterministic serialization
        │
        ├──> Forced tool choice ──requires──> forced-tool 400 fallback (auto+strict+instruction)
        │           └──> SingleShotStrategy ──requires──> OutcomeResolver seam
        │                                      └──requires──> PreApplyGate (defer mode) + CommitSink
        │
        └──> Neutral multi-turn transcript (6a) ──requires──> opaque raw-content replay (thinking blocks)
                    │                          └──requires──> per-provider tool_use/tool_calls mappers
                    └──> AgenticLoopStrategy (6b) ──requires──> two-phase ToolExecutor (prepare/apply)
                                                  ├──requires──> PreApplyGate (suspend mode) + held tool_result
                                                  ├──requires──> iteration/token bounds from TierPolicy
                                                  └──requires──> CommitSink + per-dispatch snapshot payload

Typed FailureReason ──required by──> every provider transport, both strategies, the Escalate/Failed mapping
Usage normalization ──required by──> token ceiling, trace, cache diagnostics
Provider capability: caching ──requires──> usage normalization; Anthropic breakpoint placement (A10 Gate-1)
Per-call ProviderSelection seam ──requires──> :keystore (for the sample) ──enables──> NotConfigured short-circuit
ON_DEVICE slot + capability gate ──requires──> TierPolicy.offlineOnly / allowedProviders

Retry policy ──enhances──> transports (never tool execution)
Cache diagnostics ──enhances──> A10 Gate-1 (turns a silent below-minimum into a loud event)
Commit-time CommitSink notification ──enhances──> undo correctness under cancellation

Streaming ──conflicts──> simple non-streaming error model + 4.12 API floor (deferred)
Parallel tool dispatch ──conflicts──> SB suspend-mode gate mutex contract
Auto cross-provider fallback ──conflicts──> never-substitute-a-key rule (T-68-05)

v1.1 dependencies rooted in v1.0:
  TierSelector.Router ──requires──> SingleShot forced-tool machinery (a cheap classification call)
  PlanThenExecute ──requires──> two-phase ToolExecutor + carry + Escalate
  LocalGrammar ──requires──> OutcomeResolver + NoMatch semantics
  :voice-adapter ──requires──> the :stt tag (external)
  Bundled on-device spike ──requires──> ON_DEVICE slot + capability gate
```

### Dependency Notes

- **The two-phase `ToolExecutor` must land before `AgenticLoopStrategy`, and ideally in step 2 with the gate types.** A6 puts the gate at pipeline level, but in SB the gate fires *per tool call mid-loop*. Only a `prepare → Mutate(apply)` split lets the engine enforce the gate instead of trusting each app's executor. v1.1's PlanThenExecute reuses it.
- **`PreApplyGate` needs both modes defined in step 2**, even though SingleShot (defer, CT) and Agentic (suspend, SB) exercise them in steps 5 and 6b. Retrofitting the second mode later would be a breaking change, and §11 requires strictly additive API after `v1.0.0`.
- **Opaque raw-content replay is part of 6a, not 6b.** If the neutral transcript model is designed without it, 6b fails on Sonnet 5.5, Opus 5.5 and SB's adaptive-thinking Sonnet fallback with 400s that no JVM test using the fake provider will catch. Add a fake-provider fixture containing thinking blocks.
- **Usage normalization is a prerequisite of the token ceiling.** Otherwise OpenAI runs double-count cached tokens relative to Anthropic and hit the 60k ceiling at a different point.
- **`FailureReason` is cross-cutting.** Define it in step 2 (`:core`). Transports in 3a/3b only map into it.

---

## MVP Definition

### Launch With (v1.0 → `v1.0.0`)

- [ ] Pipeline, outcomes, `TierPolicy`/`Linear`, `carry`, and the Escalate-vs-Failed mapping. This is the spine.
- [ ] Typed `FailureReason` + normalized usage + trace + typed callback: Core Value, "loud, specific".
- [ ] `PreApplyGate` (suspend **and** defer modes) + `CommitSink` (verified undo) + held-≠-success + fail-closed. Both consumers depend on it.
- [ ] Two-phase `ToolExecutor`, needed for an engine-enforced gate inside the agentic loop.
- [ ] Neutral `ToolSpec` + per-dialect encoding + strict-mode sanitization + deterministic serialization, for CT's 3 providers and SB's cache.
- [ ] Forced tool choice **with the reactive auto+strict fallback**. Without it, CT on Sonnet/Opus 5.5 fails today.
- [ ] Anthropic/OpenAI/OpenRouter transports with the cache capability (Anthropic system-block breakpoint, OpenAI automatic, OpenRouter passthrough + `require_parameters`).
- [ ] Neutral multi-turn transcript **with verbatim raw-content replay** + the agentic loop with SB's bounds, guards, validation and repeated-failure abort.
- [ ] Per-call provider/model/key seam, NotConfigured short-circuit, no key substitution, `ON_DEVICE` slot + gate.
- [ ] `:keystore` with the `NotConfigured / Configured(fingerprint) / Unreadable` state model.
- [ ] Differentiators cheap enough to include: `maxRetries=1` transient retry, cache-not-engaged event, `requestId` on failures, commit-time `CommitSink` notification, idempotent confirmation handle.

### Add After Validation (v1.x)

- [ ] Anthropic top-level automatic (moving) cache breakpoint. Add it if SB Wave-1 telemetry shows turns 3+ paying full price for earlier turns.
- [ ] OpenAI `prompt_cache_key`, once an OpenAI agentic consumer exists (none in Wave 1).
- [ ] Model catalog providers. Add them if CT/SB want catalog code out of the apps (raise at R2).
- [ ] Kotlin schema builder sugar. Add it if README-driven agent wiring stumbles on raw JSON.
- [ ] Forced-tool capability memo persisted across process restarts.

### Future Consideration (v2+)

- [ ] Streaming, only on a measured latency complaint.
- [ ] Gemini Nano / AICore `ON_DEVICE` implementation (L10, Pixel 10).
- [ ] Additional providers (Gemini direct, etc.) through the same `AiProvider` seam.

---

## Feature Prioritization Matrix

| Feature | User Value | Implementation Cost | Priority |
|---------|------------|---------------------|----------|
| Pipeline/outcomes/policy/carry | HIGH | MEDIUM | P1 |
| Typed FailureReason + usage normalization | HIGH | MEDIUM | P1 |
| PreApplyGate (2 modes) + CommitSink + held semantics | HIGH | HIGH | P1 |
| Two-phase ToolExecutor | HIGH | MEDIUM | P1 |
| Neutral ToolSpec + dialect encoders + sanitization | HIGH | MEDIUM | P1 |
| Forced tool + auto-strict fallback | HIGH | MEDIUM | P1 |
| Neutral transcript + raw-content replay | HIGH | HIGH | P1 |
| Agentic loop (bounds/guards/validation) | HIGH | HIGH | P1 |
| Anthropic caching (A10 parity) | HIGH | LOW | P1 |
| OpenAI/OpenRouter caching passthrough + usage | MEDIUM | LOW | P1 |
| Per-call provider seam + NotConfigured + no substitution | HIGH | LOW | P1 |
| `:keystore` | HIGH | MEDIUM | P1 |
| Trace + typed callback | HIGH | MEDIUM | P1 |
| Transient retry (maxRetries=1) | MEDIUM | LOW | P1 (cheap) |
| Commit-time CommitSink notification | HIGH | MEDIUM | P1 (closes a correctness hole) |
| Cache-not-engaged diagnostic event | MEDIUM | LOW | P2 |
| requestId on failures | MEDIUM | LOW | P2 |
| Idempotent confirmation handle | MEDIUM | LOW | P2 |
| Moving cache breakpoint | MEDIUM | LOW | P2 (v1.x) |
| prompt_cache_key | LOW | LOW | P3 |
| Model catalog | MEDIUM | MEDIUM | P3 (app-side for now) |
| Schema builder DSL | LOW | LOW | P3 |
| Streaming | LOW | HIGH | Won't (v1) |

---

## Competitor Feature Analysis

| Feature | LangChain4j | Koog (JetBrains) | Spring AI | Vercel AI SDK | Anthropic SDK tool runner | OpenAI Agents SDK | **voice-action-engine** |
|---|---|---|---|---|---|---|---|
| Tool declaration | `@Tool` reflection or specs | Kotlin DSL / annotations, MCP | `@Tool` / `ToolCallback` | `tool({inputSchema, execute, strict})` | JSON schema / helpers | function tools | Raw JSON `ToolSpec` via `ToolSpecProvider`, optional builder |
| Loop bound | `maxSequentialToolsInvocations` | strategy graph | internal loop | `stopWhen(stepCountIs(20))` | `max_iterations` | `max_turns` → `MaxTurnsExceeded` (thrown) | iterations + token ceiling → **typed outcome, never thrown** |
| Per-step control | – | graph nodes | – | `prepareStep`, `activeTools`, `toolOrder` | – | – | Not needed (fixed tool set; tiers replace per-step model switching) |
| Hallucinated tool | `hallucinatedToolNameStrategy` | – | exception processor | `NoSuchToolError` | – | – | `is_error` tool_result, model recovers; repeated failure aborts |
| Human approval | – | – | – | `needsApproval` (bool/fn) | – | `needs_approval` → interruptions → resume | `PreApplyGate` **suspend or defer**, fail-closed, held-≠-success fed back to the model |
| Undo | – | persistence/checkpoints (state, not domain undo) | – | – | – | – | `CommitSink` with **verified** inverse per action |
| Typed errors | exceptions | retries | `ToolExecutionException` | typed error classes | typed HTTP exceptions, 2 auto-retries | exceptions | One neutral `FailureReason` across 3 providers, never thrown |
| Caching | provider-specific | provider-specific | provider-specific | provider options + `toolOrder` | manual `cache_control` | automatic (OpenAI) | Provider **capability**; A10 breakpoint parity; normalized cache usage |
| Multi-provider | yes (many) | yes | yes | yes | Anthropic only | OpenAI-first | 3 cloud + ON_DEVICE slot, per-call switch, no key substitution |
| Tier escalation | – | planners | – | – | – | handoffs | **Core concept**: cheap-first ladder with `Escalate(carry)` |
| Android fit | JVM-heavy | KMP incl. Android | server | JS | Java SDK (own HTTP deps) | Python/JS | Pure-Kotlin `:core`, OkHttp 4.12 floor, no DI, AndroidKeyStore BYO-key |

Takeaway: every framework has loop bounds, typed tool errors and approval hooks, so those are table stakes and we match them. None has a **cheap-first tier ladder**, a **verified-undo commit sink**, **defer-mode confirmation** (return a correctable proposal and commit later), or **never-throw typed outcomes**. Those are the differentiation, and they come straight from what SB and CT already built and proved.

---

## Open Questions (for discuss-milestone / reconvene)

1. **Escalate vs Failed for model-side outcomes.** Should SingleShot's `NoToolCall`/`Refusal` escalate to Agentic by default, or be shown to the user (CT today shows `Refused`)? Recommendation: escalate by default, overridable per tier.
2. **`offlineOnly` with ON_DEVICE absent.** Return `Failed(ProviderUnavailable)` (loud) or `NoMatch`? Recommendation: `Failed`, per Core Value.
3. **Model catalog ownership.** Keep it in CT, or move it into `:providers` in v1.x? Raise with caltracker-android-9a at R1.
4. **Commit-time `CommitSink` notification.** It is a design change from SB's end-of-run `executedTools` reconciliation. Confirm with secondbrain-2c that SB's Undo Center can accept per-action commits.
5. **Token ceiling semantics.** SB counts cache-read tokens at full weight (7k × 6 turns ≈ 42k of the 60k budget). Keep that for parity (recommended). A weighted ceiling is a later, additive option.

---

## Sources

**Port sources (HIGH, read directly):**
- SB `core/agent/AnthropicAgentLoop.kt` (loop, `buildRequestBody`, bounds, stop-reason matrix), `AgentLoopResult.kt` (`UnavailableReason`, `BudgetBound`, `ExecutedToolCall`), `AnthropicToolRegistry.kt` (two-phase `ToolStep`, dispatch, byte-identical sorted definitions), `MutationGate.kt` (held content), `VoiceConfirmGate.kt` (suspend mode, 120 s timeout, fail-closed, cancellation contract), `MutationTierPolicy.kt`, `MutationDispatchContext.kt`, `PreMutationSnapshot.kt`, `VoiceUndoOperations.kt` (verified inverses), `AnthropicModelChoice.kt`, `AnthropicApiKeyRepository.kt` / `KeystoreCryptoSeam.kt`
- CT `ai/AiProvider.kt`, `ProviderRouter.kt` (per-call resolution, T-68-05), `BaseAiProvider.kt` (OpenAI-style collapse, inner-args Pitfall 8), `AnthropicProvider.kt` (+ `Call.await`), `LogFoodRequestBuilder.kt` / `OpenAiLogFoodRequestBuilder.kt` (dialect schema differences, forced-tool fallback encoding), `AnthropicKnownTool400Ids.kt`, `ModelCatalog*`, `mcp/RepositoryToolFacade.kt`, `ui/voice/VoiceLogViewModel.kt` (NotConfigured short-circuit, weak-match/batch Proposed, undo, generation guard)

**Official vendor docs (fetched 2026-09-29; the seam tiers webfetch as LOW. Cross-checked where noted):**
- [Anthropic prompt caching](https://platform.claude.com/docs/en/build-with-claude/prompt-caching): minimums (Haiku 4.5 = 4,096), 4 breakpoints, tools→system→messages order, the invalidation table, input_tokens semantics, automatic top-level caching, silent below-minimum behavior
- [Anthropic API errors](https://platform.claude.com/docs/en/api/errors): status/type list, SDK default 2 retries, the forced-tool-unsupported 400 (matches CT's `AnthropicKnownTool400Ids`, which **corroborates** it and shows CT's list is missing Sonnet 5.5), the thinking-block replay requirement
- [OpenAI prompt caching](https://developers.openai.com/api/docs/guides/prompt-caching)
- [OpenRouter prompt caching](https://openrouter.ai/docs/features/prompt-caching) · [OpenRouter errors](https://openrouter.ai/docs/api-reference/errors) · [OpenRouter provider routing](https://openrouter.ai/docs/guides/routing/provider-selection) (`require_parameters`)

**Ecosystem (websearch, LOW, used only for the competitor comparison):**
- [AI SDK tool calling](https://ai-sdk.dev/docs/ai-sdk-core/tools-and-tool-calling) · [AI SDK loop control](https://ai-sdk.dev/docs/agents/loop-control) · [AI SDK 6](https://vercel.com/blog/ai-sdk-6)
- [Koog](https://github.com/JetBrains/koog) · [Koog site](https://www.jetbrains.com/koog/)
- [LangChain4j tools](https://docs.langchain4j.dev/tutorials/tools/) · [LangChain4j AI Services](https://docs.langchain4j.dev/tutorials/ai-services/)
- [Spring AI tool calling](https://docs.spring.io/spring-ai/reference/api/tools.html)
- [Anthropic tool runner](https://platform.claude.com/docs/en/agents-and-tools/tool-use/tool-runner)
- [OpenAI Agents SDK HITL](https://openai.github.io/openai-agents-js/guides/human-in-the-loop/) · [Runner / max_turns](https://openai.github.io/openai-agents-python/ref/run/)
- [Zed issue: OpenRouter 404 on tool_choice](https://github.com/zed-industries/zed/issues/36094)

---
*Feature research for: multi-provider LLM command→action engine (Android library)*
*Researched: 2026-09-29*
