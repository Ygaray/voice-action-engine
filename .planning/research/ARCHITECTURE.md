# Architecture Research

**Domain:** Tiered, multi-provider LLM command pipeline library (Kotlin; pure-JVM core + Android keystore), consumed by SecondBrain (SB) and CalTracker (CT) through JitPack
**Researched:** 2026-09-29
**Confidence:** HIGH for module boundaries, seams, gate/commit design and build order (derived directly from the contract and the SB/CT port sources, read in full). MEDIUM for provider wire details (Anthropic facts come from the bundled Claude API reference, cached 2026-09-25; OpenAI and OpenRouter facts come from web search, with each claim cross-checked in at least two sources). LOW is marked inline where it applies.

---

## 0. The answer in one screen

1. **Four modules, dependencies point one way:** `:sample → {:providers, :keystore} → :core`. `:core` and `:providers` are **Kotlin/JVM** modules, not Android libraries. That makes JVM tests cheap, lets ABI validation work without the AGP-9 caveat, and means the OkHttp CI matrix doesn't need an emulator. `:keystore` is the only Android library. `:sample` is an Android app that's never published.
2. **One neutral transcript model lives in `:core`** (`UserMessage | AssistantMessage | ToolResultsMessage`, with `ToolCall` and `ToolResult` parts, normalized `Usage` and `StopReason`). Each `AssistantMessage` carries a **provider-native replay blob**, so a turn goes back to the same provider/model byte-for-byte. That keeps thinking blocks and the cache intact. The mappers are `internal` to `:providers`.
3. **For OpenAI, target Chat Completions in v1.0 for both OpenAI and OpenRouter.** One dialect covers both, and it's the stable OpenRouter path that CT has proven. Design the mapper behind an internal `WireDialect` seam so an OpenAI **Responses** dialect can be added later without touching the public API. The Chat Completions dialect **must** send `reasoning_effort: "none"` when tools are present on GPT-5.4+ reasoning models. Those models reject tools combined with reasoning on `/v1/chat/completions`.
4. **Caching is a request directive plus a provider capability.** The strategy says "cache the static prefix". The Anthropic mapper puts one `cache_control: ephemeral` on the **last system block**, which caches tools + system and matches SB/A10 parity. Top-level automatic caching can optionally be added for the growing tail. OpenAI caches automatically. `Usage` is normalized so cache reads/writes mean the same thing on every provider.
5. **PreApplyGate and CommitSink reconcile into one seam:** "prepare, then gate, then commit". Both strategies produce the same `ToolStep = Finished(result) | Mutation(PendingMutation)`. That's SB's own `ToolStep.Finished / ToolStep.Mutate` shape, lifted into the engine. SingleShot gets it from `OutcomeResolver`, AgenticLoop gets it from `ToolExecutor.prepare`, and every `Mutation` goes through a single engine-owned **`CommitCoordinator`**: `gate.admit(proposal)` → `Admit(amended?)` → `CommitSink.commit`, or `Hold(reason)` → a non-committing `HeldProposal`. The gate may **suspend** to await the user (SB's `VoiceConfirmGate`) or return `Hold` **immediately** for deferred confirmation (CT's Proposed/ProposedBatch sheet, committed later through `pipeline.commitHeld(...)`). Both modes use one seam.
6. **Build order (critical path):** 1 → 2 (with the commit seam tested against scripted fake strategies) → 3a (**the full neutral transcript types land here, not in 6a**) → 6a → 6b → 7. Step 4 (`:keystore`) and step 3b run in parallel with 3a. Step 5 runs in parallel with 6a. Ask SB for the E2 fixture during step 1 so it's never on the critical path.

---

## Standard Architecture

### System Overview

```
┌──────────────────────────────────────────────────────────────────────────────┐
│ CONSUMER APP (SB / CT)  — owns DI, settings storage, UI (via YAT), domain      │
│  implements seams: ToolSpecProvider · ToolExecutor · OutcomeResolver ·        │
│  PreApplyGate · CommitSink · ProviderSelectionSource · TierPolicySource       │
│  (+ optional PipelineEventListener)                                           │
└───────────────┬──────────────────────────────────────────────▲───────────────┘
                │ pipeline.execute(CommandInput)                │ CommandOutcome
                ▼                                               │ (+ CommandTrace, commits, held)
┌──────────────────────────────────────────────────────────────────────────────┐
│ :core  (pure Kotlin/JVM; kotlinx-coroutines + kotlinx-serialization-json)     │
│                                                                              │
│  CommandPipeline ── TierSelector(Linear|Fixed) ── TierPolicy (per-call)      │
│        │  walks CommandTier[]: Escalate/NoMatch → next; Completed/Failed stop │
│        ▼                                                                     │
│  CommandSession (per tier attempt) ── TraceRecorder ── EventDispatcher       │
│        │                                                                     │
│   ┌────┴──────────────┐                ┌──────────────────────────────────┐  │
│   │ SingleShotStrategy│                │ AgenticLoopStrategy              │  │
│   │ 1 extraction call │                │ N turns over ToolExecutor         │  │
│   │ → OutcomeResolver │                │ → ToolExecutor.prepare per call   │  │
│   └────────┬──────────┘                └──────────────┬───────────────────┘  │
│            │  ToolStep.Mutation(PendingMutation)      │                      │
│            └──────────────►  CommitCoordinator  ◄─────┘                      │
│                              gate.admit → CommitSink.commit / HeldProposal   │
│                                                                              │
│  ProviderRouter ── ProviderSelectionSource (per call) ── capability gate     │
│        │  neutral ModelRequest / ModelResponse / Usage / StopReason           │
│        ▼  AiProvider interface (ANTHROPIC | OPENAI | OPENROUTER | ON_DEVICE)  │
└────────┼─────────────────────────────────────────────────────────────────────┘
         │ implemented by
┌────────▼─────────────────────────────┐   ┌───────────────────────────────────┐
│ :providers (Kotlin/JVM; OkHttp ≥4.12)│   │ :keystore (Android library)       │
│  AnthropicProvider  (Messages API)   │   │  AndroidKeyStore AES/GCM          │
│  ChatCompletionsProvider             │   │  per-provider aliases + DataStore │
│    ├ OpenAI endpoint                 │   │  → KeystoreCredentialSource       │
│    └ OpenRouter endpoint             │   │    (adapter for the selection     │
│  internal WireDialect mappers        │   │     source's credential lookup)   │
│  Call.await bridge, clean-client     │   └───────────────────────────────────┘
│  derivation, error → FailureReason   │
│  UnavailableOnDeviceProvider (slot)  │   ┌───────────────────────────────────┐
└──────────────────────────────────────┘   │ :sample (debug app, never pub.)   │
                                           │  A10 fixture {system,tools} JSON  │
                                           │  fake ToolExecutor, BYO key field │
                                           └───────────────────────────────────┘
```

### Component Responsibilities

| Component | Module | Owns | Never does |
|---|---|---|---|
| `CommandPipeline` | core | Reads policy per call, picks the start tier, walks tiers, applies escalation rules, enforces "no escalation after a commit", collapses everything to `CommandOutcome` without throwing (except `CancellationException`), attaches `CommandTrace` | Call HTTP, read app storage, know any domain |
| `TierSelector` | core | Picks the start tier. `Linear` (0), `fixed(i)` (tests/debug); `Router` in v1.1 | Execute strategies |
| `TierPolicy` / `TierPolicySource` | core | Runtime caps: `offlineOnly`, `maxTier`, `allowedProviders`, per-command iteration/token/turn ceilings, and the ON_DEVICE→cloud fallback permission | Persist settings (the app binds them) |
| `CommandSession` | core | Per-tier-attempt context handed to a strategy: provider access (through the router), effective budget, carry from the previous tier, the `CommitCoordinator`, the trace recorder | Outlive one tier attempt |
| `SingleShotStrategy` | core | One extraction call (forced tool, or `auto`+`strict`+instruction when forced isn't supported) → `OutcomeResolver` → `ToolStep`s → coordinator | Commit directly |
| `AgenticLoopStrategy` | core | Bounded multi-turn loop: whole-turn validation, final-iteration guard, token ceiling, sequential tool dispatch, 2-strike tool-failure abort, held-call `tool_result` | Commit directly, dispatch tools concurrently |
| `CommitCoordinator` | core (internal, surfaced via session) | The **only** path to a write: proposal → gate → sink. Records receipts and held proposals, counts commits for the escalation guard | Decide policy (the gate does), perform writes (the sink / mutation does) |
| `ProviderRouter` | core | Per-call selection → credential for **that** provider only → capability check → ON_DEVICE availability gate → fallback when policy allows → `AiProvider.complete` | Substitute one provider's key for another's |
| `AiProvider` impls | providers | Wire mapping, HTTP, typed failure mapping, usage normalization | Log, keep state between calls, attach interceptors |
| `:keystore` | keystore | Encrypt/decrypt BYO keys per provider alias, DataStore persistence, `CredentialSource` adapter | Get read by `:core` directly. Apps wire it into their selection source |

---

## Recommended Project Structure

```
voice-action-engine/
├── settings.gradle.kts              # include(":core", ":providers", ":keystore", ":sample")
├── gradle/libs.versions.toml        # kotlin 2.3.x (match backup-engine), okhttp = "4.12.0" floor
├── jitpack.yml                      # openjdk17; publishToMavenLocal for core/providers/keystore
├── detekt/detekt.yml                # tuned rules, zero baseline
├── core/                            # kotlin("jvm") + `java-library` + maven-publish; explicitApi(); abiValidation
│   └── src/main/kotlin/io/github/ygaray/voiceaction/
│       ├── pipeline/    # CommandPipeline, commandPipeline{} DSL, CommandTier, TierSelector, TierPolicy(+Source), CommandOutcome
│       ├── strategy/    # CommandStrategy, StrategyId, StrategyOutcome, CommandSession, Carry
│       │   ├── singleshot/   # SingleShotStrategy, SingleShotConfig
│       │   └── agentic/      # AgenticLoopStrategy, AgenticLoopConfig (bounds, held content)
│       ├── transcript/  # Message, ContentPart, ToolCall, ToolResult, ToolSpec, ToolChoice,
│       │                #   ModelRequest, ModelResponse, Usage, StopReason, CacheDirective, NativeReplay
│       ├── provider/    # AiProvider, ProviderId, ProviderRouter, ProviderSelectionSource, Credential,
│       │                #   ProviderCapabilities, CapabilityOverrides, OnDeviceAvailability
│       ├── seam/        # ToolSpecProvider, ToolExecutor, OutcomeResolver, ToolStep
│       ├── commit/      # PreApplyGate, GateDecision, HoldReason, CommitProposal, PendingMutation,
│       │                #   CommitSink, CommitReceipt, UndoHandle, UndoOutcome, HeldProposal,
│       │                #   AwaitingConfirmGate (generic port of VoiceConfirmGate)
│       ├── failure/     # FailureReason (open), EscalationReason (open)
│       ├── telemetry/   # CommandTrace, TierAttempt, TurnRecord, PipelineEvent (open), PipelineEventListener
│       └── testing/     # FakeAiProvider (scripted turns), RecordingCommitSink, AdmitAllGate, HoldAllGate
├── providers/                       # kotlin("jvm"); api(project(":core")); api(okhttp 4.12.0)
│   └── src/main/kotlin/io/github/ygaray/voiceaction/providers/
│       ├── anthropic/   # AnthropicProvider, AnthropicCapabilities, internal AnthropicDialect (+DTOs)
│       ├── chat/        # ChatCompletionsProvider (openAi(), openRouter() factories), internal ChatDialect
│       ├── ondevice/    # UnavailableOnDeviceProvider (v1.0 slot)
│       └── http/        # internal Call.await, CleanClient (strip interceptors), HttpFailureMapper
├── keystore/                        # com.android.library; depends on :core (ProviderId, Credential)
│   └── src/main/kotlin/io/github/ygaray/voiceaction/keystore/
│       # KeystoreCrypto(+Seam), ApiKeyStore (per-provider alias, DataStore), KeystoreCredentialSource
└── sample/                          # com.android.application; debug only; not in jitpack.yml
    └── src/main/assets/sb-fixture.json   # E2: {system, tools} from SB
```

### Structure Rationale

- **`:core` and `:providers` as JVM modules.** Nothing in either module needs the Android SDK. The pipeline is coroutines + JSON, and OkHttp is a JVM library. That gives three benefits. (a) The fake-provider harness and MockWebServer tests run as plain `./gradlew test`. (b) KGP's built-in `abiValidation {}` (KGP ≥ 2.2) works directly, avoiding the reported AGP-9 built-in-Kotlin gap where ABI validation silently stops registering for `com.android.library`. (c) The A1 OkHttp 4.12/5.x matrix is just a `-PokhttpVersion=` switch on a JVM test task. Publishing uses maven-publish's `java` component. Note that the backup-engine Mechanism-B task name (`publishReleasePublicationToMavenLocal`) is the Android flavor, so `jitpack.yml` should call `publishToMavenLocal` for all three modules.
- **`testing/` package inside `:core` main, not a new module.** SB and CT need a `FakeAiProvider` to unit-test their own wiring during Wave-1. It's tiny and has no dependencies. Adding a `:testing` module would change the §6.2 module list (an A7-style amendment), so avoid it for v1.0. If the controversy is worth it, propose `:testing` as an additive amendment later.
- **`seam/` is separate from `strategy/`**, so the app-implemented interfaces are in one discoverable place. That matters for the "an AI agent can wire it from the README" bar.
- **`kotlinx-serialization-json` is an `api` dependency of `:core`.** `JsonObject` is the neutral type for tool schemas and tool arguments. Both consumers already use it, and it isn't an HTTP dependency, so L7/A7 still hold. The tradeoff is that the public API is coupled to kotlinx-serialization's major version. That's acceptable because it has been stable at 1.x for years.

---

## Architectural Patterns

### Pattern 1: Per-call resolution through app seams, with no DI and no storage reads

**What:** The engine never captures provider, model, key or policy at construction time. For every command it asks `TierPolicySource.current()`. For every tier attempt it asks `ProviderSelectionSource.select(...)`, then `credential(provider)` for **the selected provider only**. This ports CT's `ProviderRouter` per-call discipline without the Hilt multibinding. Providers get registered in the DSL instead of through `@IntoMap`.

**Why:** A provider switch takes effect on the next call. The library never touches DataStore. The contract's "never substitute one provider's key for another" rule becomes structurally impossible: the credential call is keyed by the selected provider, and a `null` returns `Failed(NoCredentials(provider))`.

```kotlin
public interface ProviderSelectionSource {
    /** Called once per tier attempt. `purpose` lets an app pin different providers per tier. */
    public suspend fun select(purpose: SelectionPurpose): ProviderSelection?     // provider + model (+ options)
    public suspend fun credential(provider: ProviderId): Credential?             // ONLY for the selected provider
}
public class Credential(private val secret: CharArray) { override fun toString(): String = "Credential(***)" }
```

`:keystore` ships `KeystoreCredentialSource`. An app composes it with its own "active provider/model" preference reads. Model IDs, max tokens per turn, iteration caps and token ceilings all come from `ProviderSelection` / `TierPolicy` defaults, never from constants inside strategies (a port cleanup requirement).

### Pattern 2: Neutral transcript with native replay

**What:** Strategies speak only `ModelRequest` / `ModelResponse`. Every `AssistantMessage` keeps an optional `NativeReplay(provider, model, raw: JsonElement)`. When the next request goes to the **same provider and model**, the mapper emits `raw` verbatim, exactly as SB echoes `rawContent`. Otherwise (for example, carry across an escalation to a different provider) it rebuilds the turn from the neutral parts.

**Why:** On current Anthropic models (Opus 5.5, Sonnet 5.5, Fable 5.1), thinking blocks are bound to the model and the conversation. Editing earlier turns invalidates them, and accounts created on or after 2026-08-31 get a **400** on edited history. Any byte change in the prefix also invalidates the cache. Rebuilding assistant turns from a lossy neutral model is the most likely way to break both A10 cache parity and newer-model correctness. *(HIGH: Claude API reference.)*

```kotlin
public sealed interface Message                    // closed by design: these three are the universe
public class UserMessage(public val parts: List<ContentPart>) : Message
public class AssistantMessage(public val parts: List<AssistantPart>, public val native: NativeReplay?) : Message
public class ToolResultsMessage(public val results: List<ToolResult>) : Message   // one batch per turn

public sealed interface AssistantPart { /* Text(text) | ToolCall(id, name, arguments: JsonObject) */ }
public class ToolResult(public val callId: String, public val content: String, public val isError: Boolean)
public class Usage(val inputUncached: Int, val output: Int, val cacheRead: Int, val cacheWrite: Int)
```

### Pattern 3: Prepare → Gate → Commit (the unified PreApplyGate/CommitSink seam)

**What:** Mutation is always two-phase. App code **prepares** (validates, resolves, reads) and returns a `PendingMutation` without writing. The engine's `CommitCoordinator` asks the `PreApplyGate`, then commits through the `CommitSink`. This is SB's `AnthropicToolSpec.prepare → ToolStep.Mutate(apply)` contract, where the gate is consulted only for a validated, resolved `Mutate`, made engine-wide so SingleShot and AgenticLoop share it. Details are in §3.

**When:** Every write, on every strategy. The v1.1 LocalGrammar and PlanThenExecute strategies inherit it for free.

**Trade-off:** App code has to split "resolve" from "apply". SB already works this way. CT's `resolveParsed` already separates the search from `logEntry`, and its `Proposed` path already defers the write, so the port is mechanical.

### Pattern 4: Capabilities are data, and apps can override them

**What:** `ProviderCapabilities` covers: native tool calling (yes/no/structured-output-only), forced tool choice supported, caching mode (`EXPLICIT_BREAKPOINTS | AUTOMATIC | NONE`) plus the minimum cacheable prefix, max input tokens, and whether `reasoning_effort:"none"` is required with tools. Providers return defaults per model family. The pipeline DSL accepts `capabilityOverrides { model("claude-x") { forcedToolChoice = false } }`.

**Why:** Model rules change faster than engine tags. Forced `tool_choice` `any`/`tool` now returns 400 on Opus 5.5, Sonnet 5.5 and Fable 5.1 (CT already hand-maintains `AnthropicKnownTool400Ids` for this). GPT-5.4+ rejects tools with reasoning on Chat Completions. The Anthropic minimum cacheable prefix ranges from 512 to 4096 tokens and isn't monotonic across model generations. When a new model ships between tags, apps need a patch-free escape hatch. *(HIGH for Anthropic; MEDIUM for OpenAI.)*

### Pattern 5: "Derive, don't trust" the app's OkHttpClient

**What:** Provider constructors accept the app's `OkHttpClient` so connection pools and dispatchers are shared. The engine immediately derives `client.newBuilder()`, **clears interceptors and network interceptors**, and sets its own `callTimeout`.

**Why:** If an app passes a client that has `HttpLoggingInterceptor` attached, the `x-api-key` / `Authorization` header goes to logcat. Both ports forbid logging interceptors, but only by convention. This makes it structural.

---

## 1. Module boundaries and dependency direction

```
            ┌──────────┐
            │ :sample  │ (app, debug, unpublished)
            └─┬──────┬─┘
              │      │
     ┌────────▼┐   ┌─▼─────────┐
     │:providers│   │ :keystore │   (Android lib)
     │ (JVM)    │   │           │
     └────┬─────┘   └─────┬─────┘
          │ api           │ api
          └──────►:core◄──┘        (JVM; no HTTP; no Android)
```

| Rule | Enforcement |
|---|---|
| `:core` has no OkHttp, no Android, no `:stt`, no YAT (L7, A7) | JVM module (Android classes won't resolve); a dependency-check test that fails if `okhttp3` appears on `:core`'s runtime classpath |
| `:providers` depends on `:core` only, and exposes OkHttp in constructors (`api`) | Compile floor 4.12.0. CI job re-runs tests with OkHttp forced to 5.x (A1 must-pass) |
| `:keystore` depends on `:core` only for `ProviderId` / `Credential` | No dependency on `:providers`; apps can use it without any transport |
| No module depends on `:sample` | It isn't included in `jitpack.yml` |
| A future `:provider-nano` (ML Kit GenAI) or `:provider-litert` (v1.1 Gemma spike) is a **separate optional module** | Keeps `:providers` OkHttp-only and keeps ML Kit's `play-services` weight off every consumer |

What lives where, per contract item:

| Contract item | Module | Package |
|---|---|---|
| §5.1 `CommandInput`, `CommandStrategy`, `StrategyOutcome`, pipeline DSL, `TierSelector`, `TierPolicy`, telemetry | core | `pipeline/`, `strategy/`, `telemetry/` |
| `AiProvider` / `ProviderRouter` / `ProviderId` / neutral request/result types | core (interfaces + router logic) | `provider/`, `transcript/` |
| Anthropic / OpenAI / OpenRouter transports, mappers | providers | `anthropic/`, `chat/` |
| ON_DEVICE slot + capability gate | core (gate in the router, `OnDeviceAvailability`); providers (`UnavailableOnDeviceProvider`) | `provider/`, `ondevice/` |
| §5.2 seams (`ToolSpecProvider`, `ToolExecutor`, `OutcomeResolver`, `PreApplyGate`, `CommitSink`, `TierPolicy` binding) | core | `seam/`, `commit/`, `pipeline/` |
| BYO-key crypto | keystore | root |
| SingleShot, AgenticLoop | core (they only talk to `AiProvider`) | `strategy/…` |

The strategies live in `:core`, not `:providers`. They're provider-neutral by construction and must be JVM-testable against `FakeAiProvider` (step-1 harness requirement).

---

## 2. Provider-neutral transcript model and per-provider mappers

### 2.1 Wire-shape comparison (what the mappers translate)

| Concept | Anthropic Messages (`POST /v1/messages`) | OpenAI / OpenRouter Chat Completions (`/chat/completions`) | OpenAI Responses (`/v1/responses`), for reference |
|---|---|---|---|
| System prompt | top-level `system: [{type:"text", text, cache_control?}]` | first message `{role:"system"}` (or `"developer"` for reasoning models) | `instructions` or a developer input item |
| Tool definition | `{name, description, input_schema, strict?}` | `{type:"function", function:{name, description, parameters, strict}}` (**nested**) | `{type:"function", name, description, parameters, strict}` (**flat**) |
| Tool choice | `{type:"auto"}` / `{type:"tool", name}` (**400 on Opus 5.5 / Sonnet 5.5 / Fable 5.1**) | `"auto"` / `{type:"function", function:{name}}` / `"required"` | `"auto"` / `{type:"function", name}` |
| Model asks for tools | assistant `content[]` with `{type:"tool_use", id, name, input:{…}}` (object) | assistant `tool_calls:[{id, type:"function", function:{name, arguments:"<json string>"}}]` | output items `{type:"function_call", call_id, name, arguments:"<json string>"}` |
| Tool results | **one** `role:"user"` message with N `{type:"tool_result", tool_use_id, content, is_error?}` | **N** `{role:"tool", tool_call_id, content}` messages; **no `is_error` field** | N `{type:"function_call_output", call_id, output}` items |
| Stop signal | `stop_reason`: `tool_use`, `end_turn`, `stop_sequence`, `max_tokens`, `refusal` (+`stop_details`), `pause_turn`, `model_context_window_exceeded` | `finish_reason`: `tool_calls`, `stop`, `length`, `content_filter` (+`message.refusal`) | `status` + output item types |
| Usage | `input_tokens` (**uncached remainder**), `output_tokens`, `cache_creation_input_tokens`, `cache_read_input_tokens` | `prompt_tokens` (**includes cached**), `completion_tokens`, `prompt_tokens_details.cached_tokens` | `input_tokens` (incl. cached), `input_tokens_details.cached_tokens` |
| Caching | explicit `cache_control` breakpoints (max 4; 5m default / 1h) **or** top-level automatic `cache_control` | automatic ≥1024 tokens, 128-token increments; optional `prompt_cache_key`. OpenRouter→Anthropic models need `cache_control` | automatic |
| Reasoning replay | replay `thinking` blocks verbatim (model- and conversation-bound) | n/a on Chat Completions (reasoning isn't returned) | `reasoning` items (`include: ["reasoning.encrypted_content"]` when `store:false`) |

### 2.2 Neutral → wire mapping rules (put these in `:providers` golden tests)

| Neutral | → Anthropic | → Chat Completions |
|---|---|---|
| `ModelRequest.system: String` (one frozen block) | `system:[{type:text, text, cache_control:{type:ephemeral}}]` when `CacheDirective.staticPrefix` | `messages[0] = {role: system, content}` |
| `ToolSpec(name, description, schema: JsonObject, strict)` | `{name, description, input_schema: schema}` (+`strict`) | `{type:function, function:{name, description, parameters: schema, strict}}` (strip unsupported keywords when `strict`: CT's `exclusiveMinimum`/`minimum`/`maximum` lesson) |
| `ToolChoice.Required(name)` | `{type:tool,name}` if `caps.forcedToolChoice`, else `{type:auto}` + `strict` + the instruction appended to the **user** message (never the system prompt, so the cached prefix stays stable) | `{type:function, function:{name}}` |
| `AssistantMessage(native = same provider+model)` | emit `native.raw` verbatim | emit `native.raw` verbatim (the message object incl. `tool_calls`) |
| `AssistantMessage(native = null or other provider)` | rebuild `content[]` from `Text`/`ToolCall` parts | rebuild `{role:assistant, content, tool_calls:[…arguments = args.toString()]}` |
| `ToolResultsMessage(results)` | **one** user message, all `tool_result` blocks, `is_error:true` on errors (splitting across messages trains the model away from parallel calls) | **one `role:tool` message per result**, same order. `isError` encoded as `{"error": …}` JSON content, since the wire has no flag |
| Response → `StopReason` | `tool_use→ToolUse`, `end_turn/stop_sequence→EndTurn`, `max_tokens→MaxTokens`, `refusal→Refusal(category)`, `pause_turn→PauseTurn`, `model_context_window_exceeded→ContextWindow`, else `Other(raw)` | `tool_calls→ToolUse`, `stop→EndTurn` (or `Refusal` if `message.refusal` is set), `length→MaxTokens`, `content_filter→Refusal(ContentFilter)` |
| Response → `Usage` | `inputUncached=input_tokens`, `cacheRead=cache_read_input_tokens`, `cacheWrite=cache_creation_input_tokens` | `inputUncached = prompt_tokens − cached_tokens`, `cacheRead = cached_tokens`, `cacheWrite = 0` |
| Response → `ToolCall.arguments` | `input` object (absent/`null` → `{}`; a non-object → malformed turn) | `JSON.parse(arguments)` with its **own** catch → `MalformedToolArguments` (CT Pitfall 8) |

**Usage normalization matters for budgets.** SB's token ceiling sums `input + output + cacheCreation + cacheRead`. With the rules above, `Usage.total = inputUncached + output + cacheRead + cacheWrite` means the same thing on both dialects. Summing OpenAI's raw `prompt_tokens + cached_tokens` double-counts.

### 2.3 Which OpenAI API to target: Chat Completions for OpenAI and OpenRouter in v1.0

- **For:** OpenRouter's stable, documented path is Chat Completions. Its Responses API is beta and stateless. One dialect serves both endpoints: OpenRouter is just a base URL (`https://openrouter.ai/api/v1/chat/completions`) plus a namespaced model id (`openai/gpt-…`, which CT already uses to pin the backend). CT's transport is already proven on this shape. OpenAI agentic runs are JVM-tested only in v1.0 (A8), so paying for a second dialect now buys no verified value. *(MEDIUM)*
- **Against, and the required mitigation:** Starting with GPT-5.4, `/v1/chat/completions` rejects function tools unless `reasoning_effort` is `"none"`. GPT-5.6 reasons by default, so omitting the field also 400s. The Chat dialect must send `reasoning_effort:"none"` whenever `tools` is non-empty and `caps.requiresReasoningNoneWithTools` is true (on by default for the `gpt-5.4+` family, and overridable). *(MEDIUM, confirmed across many public issue reports: LiteLLM, LibreChat, crush, SAP ai-sdk.)*
- **Additive later:** `internal interface WireDialect { encode(ModelRequest): JsonObject; decode(JsonObject): ModelResponse }`. An `OpenAiResponsesDialect` can later become a `ChatCompletionsProvider.openAi(dialect = Responses)` option, or a new `OpenAiResponsesProvider`. Either is purely additive to the public API.

**⚠ Port finding: verify with caltracker-android-9a.** CT's `OpenAiLogFoodRequestBuilder` emits the **flat** tool shape (`{type:"function", name, parameters, strict}`, which is Responses-style) but posts it to `/chat/completions` with a Chat-style `tool_choice`. Chat Completions requires `tools[0].function`. A known 400 message is `Missing required parameter: 'tools[0].function'`. OpenRouter may tolerate the flat shape; api.openai.com very likely doesn't. **Don't port that builder verbatim.** Build the Chat dialect from the documented nested shape and pin it with golden tests. *(MEDIUM. Confirmed from the CT source; the API's rejection is based on docs and issue reports, not a live call.)*

### 2.4 Where prompt caching lives

| Layer | Responsibility |
|---|---|
| Strategy (core) | Sets `CacheDirective(staticPrefix = true, conversationTail = policy.tailCaching)` on every request, and keeps the prefix **byte-stable**: system + tools come from a `ToolingSnapshot` taken **once per command** (and memoized per pipeline when the provider says it's stable). Dynamic content (date-time, zone, transcript, carry) goes only into the first user message, as SB does. |
| Capability (core) | `caps.caching`: `EXPLICIT_BREAKPOINTS` (Anthropic), `AUTOMATIC` (OpenAI; OpenRouter non-Anthropic), `EXPLICIT_VIA_ROUTER` (OpenRouter `anthropic/*`), `NONE` (ON_DEVICE); `minCacheablePrefixTokens` |
| Anthropic mapper | `staticPrefix` → exactly **one** `cache_control:{type:ephemeral}` on the **last system block**. That caches tools + system: SB `buildRequestBody` parity, **required by A10**. `conversationTail` (optional, off by default in v1.0) → **add** top-level automatic `cache_control` ("a moving message breakpoint only as an addition"). This is the documented "robust agent-loop combination" and uses 2 of the 4 breakpoint slots. |
| Chat mapper | Automatic, so no directive is needed. Optionally `prompt_cache_key` = a hash of tools+system (never the transcript; only useful on pre-GPT-5.6 models). For OpenRouter with `anthropic/*` models, putting `cache_control` on the system content part is an additive v1.x item. |
| Telemetry | `Usage.cacheRead` / `cacheWrite` per turn, so a zero `cacheRead` on turn 2+ is **visible**. That's the Gate-1 criterion. |

A10 sizing note: the SB fixture's ~7k-token prefix clears every Anthropic minimum (512–4096). A tiny test prompt below the model's minimum produces `cache_creation_input_tokens: 0` with **no error**. That's the "false fail" A10 guards against. `:sample` should also log `minCacheablePrefixTokens` against the estimated prefix size.

---

## 3. PreApplyGate + CommitSink: one seam for SingleShot and AgenticLoop

### 3.1 The two source shapes, and why they reconcile

| | SB (AgenticLoop) | CT (SingleShot) |
|---|---|---|
| Where the decision happens | `AnthropicToolRegistry.executeSpec` → `ToolStep.Mutate` → `gate.admit(toolName, input)` | `VoiceLogViewModel.resolveParsed`: parse confidence, local match score, unit match → `Proposed` / `ProposedBatch` |
| Blocking model | **Suspends** inside `admit` (Compose dialog, 120 s timeout, mutex, fail-closed → `Hold`) | **Deferred**: returns to the UI in the `Proposed` state, and the user edits/drops rows then taps Confirm (`onConfirmProposed` / `onConfirmAllProposed`) |
| Unit of confirmation | one tool call | one item **or a batch** (a 2+ item utterance never auto-logs) |
| What the user can change | yes/no | quantity, food match, date, drop rows (**amends** the proposal) |
| Held outcome | `tool_result {"applied":false,"status":"held_for_confirmation"}`, loop continues | nothing written; the sheet waits |
| Undo | per-tool verified reversal (`VoiceUndoOperations` returns `false` when nothing reversed); snapshot captured pre-apply | delete the exact row by id (`onUndo`, `onUndoBatchItem`) |

Both are "a resolved, not-yet-applied change; a policy decides; the user may need to confirm, possibly amending; then apply; keep enough to undo". The only real differences are **suspend vs defer** and **single vs batch with amendment**. Both fit one `admit` returning `Admit(amended?) | Hold(reason)`.

### 3.2 The engine types

```kotlin
/** App-implemented: a resolved change that has NOT been applied. Prepared by ToolExecutor/OutcomeResolver. */
public interface PendingMutation {
    public val name: String                       // tool name / app action id (telemetry-safe identifier)
    public suspend fun apply(): MutationOutcome   // the ONLY write; runs only after Admit
}
public class MutationOutcome(
    public val resultForModel: String,            // becomes tool_result content in agentic; ignored by SingleShot
    public val isError: Boolean,
    public val undo: UndoHandle?,                 // null = not undoable
)
public fun interface UndoHandle { public suspend fun revert(): Boolean }   // false = verified nothing reversed (SB rule)

/** What prepare returns: SB's ToolStep, lifted. */
public sealed interface ToolStep {
    public class Finished(public val result: ToolResultContent) : ToolStep       // read / preview / validation error
    public class Mutation(public val pending: PendingMutation) : ToolStep
}

public class CommitProposal(
    public val mutations: List<PendingMutation>,  // 1 for an agentic tool call; 1..N for a SingleShot batch
    public val origin: ProposalOrigin,            // strategy id, tier index, provider id (no args, no transcript)
    public val hints: Map<String, Any?>,          // resolver-supplied signals (e.g. CT confidence, match score)
)

public fun interface PreApplyGate {               // modeled on MutationGate.admit (E1) + A2 generalization
    public suspend fun admit(proposal: CommitProposal): GateDecision   // MAY suspend to await the user
}
public sealed interface GateDecision {            // closed: contract-level shape
    public class Admit(public val amended: CommitProposal? = null) : GateDecision   // user edits / dropped rows
    public class Hold(public val reason: HoldReason) : GateDecision
}
public interface HoldReason { public val code: String }   // open: NeedsConfirmation(code, subject), Declined, TimedOut, Blocked

public interface CommitSink {                     // commit + undo (SB Undo Center / CT VoiceLogViewModel)
    public suspend fun commit(mutation: PendingMutation, context: CommitContext): CommitReceipt
    public suspend fun undo(receipt: CommitReceipt): UndoOutcome
    public companion object { public val Direct: CommitSink = /* apply(); receipt carries UndoHandle */ }
}
```

### 3.3 The coordinator: the only code path that writes

```
CommitCoordinator.submit(proposal):
  decision = gate.admit(proposal)                  // CancellationException propagates (SB single cancellation contract)
  ├─ Admit(amended) → for m in (amended ?: proposal).mutations (sequential):
  │        receipt = sink.commit(m, ctx)           // exceptions → isError receipt, never thrown
  │        receipts += receipt ; emit MutationCommitted(m.name)
  └─ Hold(reason)   → held += HeldProposal(proposal, reason) ; emit MutationHeld(name, reason.code)
  returns SubmitResult(committed | held)           // Held is NON-COMMITTING, never success, never failure
```

**Fail-closed:** gate exceptions (other than cancellation) become `Hold(GateError)`, never `Admit`. This is SB AI-SPEC G2: only an explicit confirm admits.

### 3.4 How each strategy exercises it

**SingleShot (CT weak-match / batch confirm):**
```
extract (1 LLM call) → OutcomeResolver.resolve(extraction, input) →
  Resolution.Steps(List<ToolStep>, display) | NoMatch | Escalate(reason, carry) | Failed(reason)
Steps → Finished parts go to the result; all Mutation parts → ONE CommitProposal (batch) → coordinator
  CT gate, suspending mode: if hints say weak/batch → publish Proposed(Batch) UI, await the user's
     Confirm-all (with edits → Admit(amended)) or Dismiss (→ Hold(Declined))
  CT gate, deferred mode:   return Hold(NeedsConfirmation("weak_match", subject)) immediately →
     CommandOutcome.Completed(held=[…]) → the sheet renders → later pipeline.commitHeld(held, amended)
```

**AgenticLoop (SB per-tool gate):**
```
per tool_use turn, sequentially per call:
  step = ToolExecutor.prepare(call)                // SB spec.prepare (validation + resolution reads)
  Finished → tool_result(content, isError)
  Mutation → coordinator.submit(CommitProposal(listOf(pending)))
      committed → tool_result(outcome.resultForModel, outcome.isError)
      held      → tool_result(config.heldResultContent = {"applied":false,"status":"held_for_confirmation"}, isError=false)
```
The app's system prompt must tell the model that `held_for_confirmation` means "not done, don't retry". SB's prompt already says this. The README must say so, and `AgenticLoopConfig.heldResultContent` is overridable.

**Why deferred mode is safe for agentic too:** it's exactly SB's `InterimMutationGate` behavior. Held proposals come back in `CommandOutcome.held`. An app can offer "Confirm" after the loop, with `commitHeld` going straight to the sink because the gate is already satisfied by the user's action. **Caveat:** a deferred commit runs later against possibly-changed state, so `PendingMutation.apply()` must re-validate. That's SB's 166-05 fresh-snapshot / guarded-transaction lesson, and the README should state it.

### 3.5 Invariants the pipeline enforces (carried over from both ports)

1. **No escalation after a commit.** If `session.commits` isn't empty and a strategy returns `Escalate` / `NoMatch`, the pipeline stops and returns `Completed` with the effects plus an `EscalationSuppressed` trace note. Without this, tier 1 logs "2 eggs", escalates, and tier 2 logs them again.
2. **Effects are never hidden behind a failure** (SB T-165-25). `CommandOutcome.Failed` carries `commits` and `held` just like `Completed`.
3. **Sequential dispatch.** Tool calls within a turn are dispatched one at a time. A suspending gate plus concurrent dispatch would deadlock behind the confirm mutex (SB IN-01). Document it as a guarantee.
4. **Gate snapshots belong to the mutation, not a coroutine-context hack.** SB passes `PreMutationSnapshot` from gate to apply through `MutationDispatchContext`. In the engine, `PendingMutation` is the app's own object, so the gate can downcast and attach the snapshot (display + undo data) directly. That's simpler, and there's no hidden context element.
5. **Undo is verified.** `UndoHandle.revert()` returns `false` → `UndoOutcome.NotReversed`, which the app surfaces loudly (maps to YAT "Failed", not "Undone"). `pipeline.undoAll(outcome)` reverts receipts in reverse order.

### 3.6 The generic suspending gate helper (ports SB `VoiceConfirmGate`)

`AwaitingConfirmGate(policy: suspend (CommitProposal) -> ConfirmNeed?, timeout = 120.s)` exposes `pending: StateFlow<PendingConfirmation?>` and `resolve(id, answer: ConfirmAnswer /* Confirm(amended?) | Decline */)`. It serializes with a mutex and fails closed on timeout, dismiss, or policy error. `:core` depends on coroutines, so `StateFlow` is fine. This is the "app renders YAT sheet, taps confirm" path both apps need, and shipping it once keeps both from re-deriving the cancellation/timeout rules. SB's `MutationTierPolicy` becomes the `policy` lambda. CT's `PARSE_CONFIDENCE_FLOOR` / `WEAK_MATCH_THRESHOLD` checks become its `policy` lambda over `proposal.hints`. Naming note (§5.2 ⚠): the engine type is `CommandTier`; SB's `MutationTierPolicy` stays app-side behind the gate.

---

## 4. The §5.2 seam set (plus the resolver seams)

| Seam | Signature (sketch) | Used by | SB plugs | CT plugs |
|---|---|---|---|---|
| `ToolSpecProvider` | `suspend fun tooling(input: CommandInput): ToolingSnapshot(system: String, tools: List<ToolSpec>, singleShotTool: String?)` | SingleShot, Agentic (Plan, Router in v1.1) | `AnthropicToolRegistry.toolDefinitions` + `SYSTEM_PROMPT` | `log_food` spec + description |
| `ToolExecutor` | `suspend fun prepare(call: ToolCall, ctx: ToolContext): ToolStep` | Agentic (Plan v1.1) | `AnthropicToolRegistry`/`RoomToolFacade` specs (`prepare` → `Finished`/`Mutate`) | N/A in v1.0 |
| `OutcomeResolver` | `suspend fun resolve(extraction: JsonObject, input: CommandInput, ctx): Resolution` | SingleShot (LocalGrammar v1.1) | TBD | `RepositoryToolFacade.searchFood` + the confidence/match/unit checks → `ToolStep`s + hints |
| `PreApplyGate` | `suspend fun admit(proposal): GateDecision` | all mutating paths (via coordinator) | `VoiceConfirmGate` (+`MutationTierPolicy`) → `AwaitingConfirmGate` | weak-match / batch confirm (suspending or deferred) |
| `CommitSink` | `commit(mutation, ctx): CommitReceipt`; `undo(receipt): UndoOutcome` | all | Undo Center (`VoiceUndoOperations` as `UndoHandle`s) | `VoiceLogViewModel` write + delete-by-id |
| `TierPolicySource` | `suspend fun current(): TierPolicy` (+ `TierPolicySource.fixed(p)`) | pipeline | SB settings | CT settings |
| `ProviderSelectionSource` | `select(purpose)`, `credential(provider)` | router | settings + `:keystore` | settings + `:keystore` |
| `PipelineEventListener` (optional) | `fun onEvent(e: PipelineEvent)` (non-suspending, must not throw; engine catches) | pipeline | `Log.i` sink / debug overlay | same |

No seam carries a DI annotation. The DSL takes plain instances, and apps bind them with whatever DI they use.

---

## 5. Telemetry: a trace on every result, plus an optional callback

```kotlin
public class CommandTrace(
    public val commandId: String, public val startedAtMs: Long, public val durationMs: Long,
    public val language: String?, public val transcriptLength: Int,        // length only, never text
    public val attempts: List<TierAttempt>, public val handledBy: TierRef?, public val status: String,
)
public class TierAttempt(
    public val tierIndex: Int, public val strategy: StrategyId,
    public val provider: ProviderId?, public val model: String?,
    public val fallbackFrom: ProviderId?,                                   // ON_DEVICE → cloud fallback
    public val outcome: String, public val escalationReason: String?, public val failureCode: String?,
    public val latencyMs: Long, public val turns: List<TurnRecord>, public val usage: Usage,
)
public class TurnRecord(public val stopReason: String, public val toolNames: List<String>,
                        public val heldToolNames: List<String>, public val usage: Usage, public val latencyMs: Long)
```

- **Allowed:** tier/strategy/provider/model ids, stop reasons, **tool names** (schema identifiers, as SB logs them today), counts, token usage including cache read/write, latency, reason **codes**.
- **Never:** API key, transcript text, tool arguments, `tool_result` content, model text, gate subjects (titles/names), exception messages. Exceptions are recorded as `e::class.simpleName`, because messages can contain URLs, keys or user content.
- **Redaction test, as an architectural guard:** one JVM test runs a full scripted pipeline with canary values (`sk-CANARY-KEY`, `CANARY-TRANSCRIPT`, a canary arg, a canary tool_result), then serializes the trace, every emitted event, every `toString()` of public request/credential types, and every `FailureReason`, and asserts no canary appears. That makes the secrets constraint a failing test, not a code-review hope.
- Events (open interface, additive): `CommandStarted`, `TierStarted`, `TierSkipped(reason)`, `ProviderFallback(from, to, reason)`, `ModelTurnCompleted(turn)`, `ToolDispatched(name, mutating)`, `MutationHeld(name, code)`, `MutationCommitted(name)`, `TierFinished(outcome)`, `CommandFinished(trace)`. They're delivered synchronously on the pipeline coroutine. Wrapping them in a `Flow` is the app's job (it's out of scope).

---

## 6. ON_DEVICE slot + runtime capability gate (Nano-ready)

```kotlin
public interface OnDeviceRuntime {                     // implemented by a future :provider-nano / :provider-litert
    public suspend fun availability(): OnDeviceAvailability
}
public sealed interface OnDeviceAvailability {       // mirrors ML Kit FeatureStatus
    public object Available : OnDeviceAvailability
    public object Downloadable : OnDeviceAvailability
    public object Downloading : OnDeviceAvailability
    public class Unavailable(public val code: String) : OnDeviceAvailability   // e.g. "no_aicore", "not_implemented"
}
```

- **v1.0:** `ProviderId.ON_DEVICE` exists. `:providers` ships `UnavailableOnDeviceProvider` (always `Unavailable("not_implemented")`), and the router treats an unregistered ON_DEVICE the same way. On the S22s the effect is: selection says ON_DEVICE → router checks availability → not `Available` → if `policy.offlineOnly` is false **and** the app declared a cloud fallback selection (`ProviderSelection.fallback`), use it and record `fallbackFrom = ON_DEVICE`. Otherwise the tier returns `Escalate(ProviderUnavailable(ON_DEVICE))` and the pipeline moves on. When no tier can run, the outcome is `Failed(NoEligibleTier)`, loud and specific.
- **Nano-ready shape:** ML Kit's GenAI Prompt API (`com.google.mlkit:genai-prompt`, beta) exposes `checkStatus()` → `AVAILABLE | DOWNLOADABLE | DOWNLOADING | UNAVAILABLE`, `download()`, `warmup()`, and `generateContent(Stream)`. It needs AICore, API 26+ and a locked bootloader, and has an **input cap of about 4k tokens**. Native function calling isn't documented, but structured output is. So capabilities for ON_DEVICE must say `toolCalling = STRUCTURED_OUTPUT_ONLY`, `maxInputTokens ≈ 4000`, `caching = NONE`. **Design consequence:** SingleShot must depend on "structured extraction" (forced tool **or** JSON-schema output), not on tool calling specifically. AgenticLoop must declare `requires(nativeToolCalling, minInputTokens = prefix)`, so the router refuses ON_DEVICE for it with a typed reason. A ~7k-token SB prefix can never fit Nano. *(MEDIUM for the ML Kit facts, which are beta API; LOW for exact Nano token limits on a Pixel 10.)*
- **Placement:** the real Nano and LiteRT implementations go in separate optional modules later, so ML Kit / Play-services never lands on consumers that don't want it.

---

## 7. Public API shape and strictly-additive evolution

```kotlin
val pipeline: CommandPipeline = commandPipeline {
    providers {
        register(AnthropicProvider(okHttpClient))
        register(ChatCompletionsProvider.openAi(okHttpClient))
        register(ChatCompletionsProvider.openRouter(okHttpClient))
    }
    selection = appSelectionSource                 // per-call provider/model/key
    policy = appPolicySource                       // TierPolicySource; or policy(TierPolicy(maxTier = 1))
    gate = appGate                                 // PreApplyGate (pipeline-level, A6)
    commitSink = appSink                           // default CommitSink.Direct
    events = PipelineEventListener { e -> … }      // optional

    tier(SingleShotStrategy(tooling = ctToolSpecs, resolver = ctResolver))
    tier(AgenticLoopStrategy(tooling = sbToolSpecs, executor = sbExecutor)) {
        config { maxIterations = 6; maxCommandTokens = 60_000; maxTokensPerTurn = 4096 }
    }
    selector = TierSelector.Linear
}
val outcome: CommandOutcome = pipeline.execute(CommandInput(transcript, language = "es", context = emptyMap()))
// later: pipeline.commitHeld(outcome.held.first(), amended = …)  /  pipeline.undoAll(outcome)
```

Rules for keeping it strictly additive (these feed §11 step 2):

| Rule | Why |
|---|---|
| `explicitApi()` in `:core`, `:providers`, `:keystore` | No accidental public surface; everything public is deliberate |
| **ABI dumps checked in; CI fails on removal/change.** KGP ≥ 2.2 `abiValidation {}` on the JVM modules (backup-engine is on Kotlin 2.3.20, so it's available; it may need an experimental opt-in); for `:keystore` on AGP 8.x the same works via the kotlin-android plugin; fallback is the standalone `org.jetbrains.kotlinx.binary-compatibility-validator` plugin | Makes the "strictly additive" gate mechanical. Generate the baseline dump **at `v1.0.0`** |
| **No `data class` for public types that may grow.** Use regular classes with explicit `equals`/`hashCode`/`toString` | Adding a property to a data class changes `copy()`/`componentN` (binary-breaking), and default `toString()` leaks secrets |
| **Sealed only for contract-closed shapes:** `StrategyOutcome` (4 variants, contract §5.1), `GateDecision`, `Message`, `ToolStep`, `CommandOutcome`, `OnDeviceAvailability` | Exhaustive `when` is valuable here. A new variant needs a contract amendment anyway |
| **Open (non-sealed) interfaces or value-class codes for growing taxonomies:** `FailureReason`, `EscalationReason`, `HoldReason`, `PipelineEvent`, `StopReason.Other` | Adding a subclass to a sealed type passes ABI checks but **breaks consumers' exhaustive `when` at compile time**. BCV won't catch it, so the policy has to |
| `ProviderId` and `StrategyId` as `@JvmInline value class` with companion constants (`ProviderId.ANTHROPIC`…) instead of `enum` | Adding GEMINI later is additive. The contract lists the four ids but doesn't mandate an enum. **Flag this in discuss** |
| `TierSelector` is an interface with factories (`Linear`, `fixed(i)`) | `Router` lands in v1.1 without touching existing types |
| DSL builders use `@DslMarker` and only `var` properties or functions with defaults | New knobs are new optional members |
| Provider constructors: primary constructor + a companion `create(...)`; new params get defaults, and `@JvmOverloads` isn't needed since consumers are Kotlin | Adding parameters stays source-compatible |
| `vendorOptions: Map<ProviderId, JsonObject>` on `ProviderSelection` / tier config, merged into the body last (can't override `model`/`messages`/`tools`/`system`) | Per-model knobs (`output_config.effort`, `reasoning_effort`, `prompt_cache_key`) without API churn every time a vendor adds one |

---

## Data Flow

### Request flow (one command)

```
App: pipeline.execute(CommandInput)
  │
  ├─► TierPolicySource.current()  ──► effective TierPolicy
  ├─► TierSelector.startTier()    ──► i
  ▼
for tier i..maxTier:
  ├─ policy.permits(tier)? no → TierSkipped, continue
  ├─ session = CommandSession(tier, policy, carry, router, coordinator, trace)
  ├─ strategy.execute(input, session)  (exceptions → Failed(Internal(className)); cancellation rethrown)
  │     │
  │     ├─► session.model(purpose) → ProviderRouter
  │     │       ├─ ProviderSelectionSource.select() → provider, model
  │     │       ├─ ProviderSelectionSource.credential(provider)   (null → Failed(NoCredentials))
  │     │       ├─ capabilities ∩ strategy requirements; ON_DEVICE availability → fallback / Escalate
  │     │       └─► AiProvider.complete(ModelRequest, Credential) ─► HTTPS ─► ModelResponse(Usage)
  │     │
  │     ├─► OutcomeResolver / ToolExecutor.prepare  ──► ToolStep
  │     └─► CommitCoordinator.submit(proposal) ─► PreApplyGate.admit ─► CommitSink.commit | HeldProposal
  │
  ├─ Completed / Failed → stop
  └─ Escalate / NoMatch → (commits? stop : carry = outcome.carry; next tier)
  ▼
CommandOutcome.{Completed | Failed | Unhandled}(reply?, commits, held, failure?, trace)
  │  every event also → PipelineEventListener (redacted)
  ▼
App maps outcome → YAT sheet ("handled by", held-confirm state, loud failure)
```

### Key data flows

1. **SingleShot (CT):** transcript + date context in the user message → one request with `ToolChoice.Required("log_food")` (or `auto`+`strict`+instruction when unsupported) → `ToolCall.arguments` → `OutcomeResolver` (local Room search + confidence/match/unit hints) → a batch `CommitProposal` → gate (weak/batch → confirm, strong single → admit) → sink writes the diary row → `CommitReceipt(undo = delete-by-id)`.
2. **AgenticLoop (SB):** a frozen `ToolingSnapshot` (7k prefix, cached at the system breakpoint) + growing messages → per turn: validate the whole turn → budget checks → echo the assistant turn via `NativeReplay` → per call `prepare` → reads return `Finished`, mutations go through the coordinator → one batched `ToolResultsMessage` → repeat until `EndTurn` (the reply text) or a bound (`Failed(Budget(MAX_ITERATIONS|TOKEN_CEILING))`).
3. **Escalation with carry:** SingleShot returns `Escalate(ModelDeclined | MalformedExtraction | ResolverAmbiguous, carry = ExtractionCarry(json))` → the next tier's first user message gets the carry as a hint **after** the cached prefix → no prefix invalidation.
4. **ON_DEVICE fallback:** selection = ON_DEVICE → `Unavailable` → the policy allows cloud → fallback selection (its own key) → trace `fallbackFrom = ON_DEVICE`.

### Default outcome mapping (typed, never collapsed)

| Condition | SingleShot | AgenticLoop |
|---|---|---|
| Network / timeout | `Failed(Network)` | `Failed(Network)` + effects |
| 401/403 | `Failed(Auth(status))` | same |
| 429 / 5xx | `Failed(HttpError(status, retryAfter?))` | same |
| Malformed body | `Failed(MalformedResponse)` | same |
| Model answered prose, no tool call | `Escalate(ModelDeclined)` (CT treated this as Refused) | `Completed(reply)`: a clarifying question is a valid end |
| Tool args unparseable / resolver can't map | `Escalate(MalformedExtraction)` | whole-turn invalid → `Failed(MalformedResponse)` (SB) |
| `refusal` stop | `Failed(Refusal(category))` (escalating a safety refusal just re-asks) | same |
| `max_tokens` / context window / `pause_turn` | `Failed(MaxTokens / ContextWindow / PauseTurn)` | same |
| Resolver: nothing matches | `NoMatch` | — |
| Same tool fails twice | — | `Failed(ToolFailure(name))` + effects |
| Bounds hit | — | `Failed(Budget(bound))` + effects (SB `BudgetExceeded` is a normal outcome, loud in the UI) |

Whether network/auth failures should escalate to a tier on a **different** provider is a `TierPolicy.escalateOnTransportFailure` switch (default **false**, so failures stay loud). That's a discuss-phase decision.

---

## Scaling Considerations

This is a single-user on-device library, so "scale" means per-command cost and latency, not user count.

| Dimension | Small (CT: 1 tool, 1 call) | Medium (SB: 17 tools, ~7k prefix, 2–4 turns) | Large (future apps: 30+ tools, 6–10 turns) |
|---|---|---|---|
| Prefix cost | below cache minimum, so irrelevant | **cache must hit**: ~90%+ of input on turn 2+ comes from `cache_read` | explicit system breakpoint + automatic tail caching; watch the 20-block lookback on long turns |
| Latency | 1 RTT | N RTTs; sequential tool dispatch | consider a Router tier (v1.1) to skip cheaper tiers that will fail |
| Budget | `maxTokensPerTurn` | iterations 6 / 60k tokens (SB defaults, now policy) | per-command cost ceiling in `TierPolicy` |

**First bottleneck:** silent cache misses from a non-byte-stable prefix. Detect them through `TurnRecord.usage.cacheRead == 0` on turn 2+.
**Second bottleneck:** a wasted SingleShot → Agentic escalation (paying twice). Detect it from escalation-reason frequencies in the trace, then tune the ladder or add a Router.

---

## Anti-Patterns

### Anti-Pattern 1: Rebuilding assistant turns from the neutral model when replaying to the same model
**What people do:** Map the response to neutral parts, then re-serialize from them on the next turn.
**Why it's wrong:** It drops thinking blocks and signatures (400 on preserved-thinking models when history is edited) and changes bytes, so the cache misses.
**Do this instead:** Use `NativeReplay` verbatim for same provider+model; rebuild only across providers.

### Anti-Pattern 2: Anything dynamic in the system prompt or tool list
**What people do:** Put "today is …" in the system prompt, rebuild `JsonObject` tool schemas per turn in a different key order, or use a per-command tool subset.
**Why it's wrong:** `cache_read_input_tokens` stays 0 and A10 Gate-1 fails for no visible reason.
**Do this instead:** Use a `ToolingSnapshot` frozen per command (memoized per pipeline), put date-time in the first user message (SB pattern), and add a determinism unit test (encode twice → identical bytes).

### Anti-Pattern 3: A strategy that writes directly
**What people do:** A SingleShot resolver that calls `repo.insert` inside `resolve()`.
**Why it's wrong:** It bypasses the gate for that strategy, and CT's weak-match confirm silently disappears (the contract says it "must not be dropped").
**Do this instead:** Resolvers and executors only return `ToolStep`. The coordinator is the only writer. Enforce it in code review and README examples.

### Anti-Pattern 4: Escalating after committing
**What people do:** Tier 1 commits a partial result, returns `Escalate`, and tier 2 redoes everything.
**Why it's wrong:** Double writes (CT's own TOOL-03 double-logging concern).
**Do this instead:** The pipeline guard: commits exist → stop.

### Anti-Pattern 5: Treating Held as success or as failure
**What people do:** Map a held call to "Done ✓" or to an error sheet.
**Why it's wrong:** Both mislead the user. A2 requires a non-committing state with a reason.
**Do this instead:** `CommandOutcome.held` is non-empty → YAT's generic confirm state, rendered from `HoldReason`.

### Anti-Pattern 6: Cross-provider key fallback or silent provider substitution
**What people do:** If the OpenAI key is missing, use the Anthropic key "so it works".
**Why it's wrong:** It violates the contract and CT T-68-05.
**Do this instead:** Per-provider `credential(provider)`. The only fallback is the **declared** ON_DEVICE → cloud one, gated by policy and visible in the trace.

### Anti-Pattern 7: Trusting the app's OkHttpClient as-is
**What people do:** Use the injected client directly.
**Why it's wrong:** An app-level logging interceptor leaks keys.
**Do this instead:** Derive a clean client (Pattern 5).

### Anti-Pattern 8: Collapsing failures or growing public enums/data classes
**What people do:** Return CT-style `Unavailable` for everything, or use `enum class FailureReason`.
**Why it's wrong:** Opaque failures, and a public API that can't grow additively.
**Do this instead:** Use the typed open taxonomy from §7.

### Anti-Pattern 9: Summing OpenAI `prompt_tokens` + `cached_tokens`
**Why it's wrong:** It double-counts cached input, so token ceilings trip early on OpenAI/OpenRouter only.
**Do this instead:** Normalize `Usage` in the mapper (§2.2).

---

## Integration Points

### External services

| Service | Integration pattern | Notes |
|---|---|---|
| Anthropic Messages API | `POST https://api.anthropic.com/v1/messages`, `x-api-key`, `anthropic-version: 2023-06-01` | Forced `tool_choice` 400s on Opus 5.5 / Sonnet 5.5 / Fable 5.1 (capability). Thinking can't be disabled on Opus 5.5, so `max_tokens` must leave room (SB's 4096 rationale). Check `stop_reason` before running tools |
| OpenAI Chat Completions | `POST https://api.openai.com/v1/chat/completions`, Bearer | Nested `function` tool shape; `reasoning_effort:"none"` with tools on GPT-5.4+; automatic caching ≥1024 tokens |
| OpenRouter | `POST https://openrouter.ai/api/v1/chat/completions`, Bearer | Namespaced model ids; Responses API is beta (skip); `anthropic/*` caching needs `cache_control` (v1.x additive); `session_id` for sticky routing (optional) |
| AICore / ML Kit GenAI (future) | separate optional module implementing `AiProvider` + `OnDeviceRuntime` | Absent on the S22s; ~4k input cap; structured output, not native tools |
| JitPack | `jitpack.yml` openjdk17 → `publishToMavenLocal` for core/providers/keystore | Verify resolution from a clean Gradle cache (§11 step 4) |

### Internal boundaries

| Boundary | Communication | Notes |
|---|---|---|
| App ↔ pipeline | `execute()` / `commitHeld()` / `undoAll()` + seam callbacks | All suspend; the app picks the scope (`viewModelScope`) |
| Strategy ↔ provider | `CommandSession.model(...)` → `ProviderRouter` → `AiProvider.complete` | Strategies never see `Credential` or OkHttp |
| Strategy ↔ writes | `ToolStep` → `CommitCoordinator` | Single writer path |
| `:providers` ↔ `:core` | implements `AiProvider`; mappers `internal` | Wire DTOs never leak into the public API |
| `:keystore` ↔ app | `ApiKeyStore`, `KeystoreCredentialSource` | `:core` never calls it; the app composes it into `ProviderSelectionSource` |
| Engine ↔ YAT | none | The app maps `CommandOutcome` + `CommandTrace` → YAT props (L7) |

---

## Suggested Build Order (mapped to contract §6.2)

```
1 ──► 2 ──► 3a ──► 6a ──► 6b ──► 7
            │  └──► 3b ─────────┘ (3b needed by 6a's Chat mapper conformance)
            └──► 5 (after 3a; parallel with 6a)
      2 ──► 4 (parallel with 3a/3b)
E2 fixture requested from SB during 1 (via orchestrator, A14) → needed by 7
```

| Step | Delivers (architecture view) | Depends on | Notes / risks |
|---|---|---|---|
| **1** Scaffold | 4 modules (core/providers JVM, keystore Android lib, sample app); `explicitApi`; detekt zero baseline; `abiValidation` wired; `jitpack.yml` publishing three artifacts; CI skeleton incl. the OkHttp matrix job (placeholder); `core/testing` package stub (`FakeAiProvider` scripted-turn API); control-plane registry entries | — | Settle the package root (`io.github.ygaray.voiceaction`) and JVM-vs-Android module types **here**; changing them later is breaking. Request the E2 fixture now |
| **2** Core contract + pipeline + hooks | `CommandInput`, `CommandStrategy`, `StrategyOutcome`, `CommandSession`, `CommandPipeline` DSL, `TierSelector.Linear/fixed`, `TierPolicy(+Source)`, `CommandOutcome`, trace + events, `FailureReason` taxonomy, **the whole `commit/` package** (`ToolStep`, `PendingMutation`, `PreApplyGate`, `GateDecision`, `CommitCoordinator`, `CommitSink`, `HeldProposal`, `AwaitingConfirmGate`), no-escalate-after-commit guard, redaction canary test | 1 | Test with **scripted fake strategies**, no LLM at all: escalation walk, carry, policy caps, gate suspend vs defer, batch amendment, fail-closed, cancellation propagation. This is the highest-leverage step; everything later just exercises it (A6) |
| **3a** Providers: neutral types + Anthropic + ON_DEVICE | **All of `transcript/`** (multi-turn `Message` types, `ToolCall`/`ToolResult`, `NativeReplay`, `Usage`, `StopReason`, `CacheDirective`); `AiProvider`, `ProviderRouter`, `ProviderSelectionSource`, `Credential`, capabilities + overrides, `OnDeviceRuntime` + `UnavailableOnDeviceProvider` + fallback; `AnthropicProvider` (clean client, `Call.await`, typed error map, system-block `cache_control`, usage normalization); **A1 matrix green** | 2 | **Put the full multi-turn types here, not in 6a.** SingleShot is a 1-message transcript, so defining single-turn-only types now forces a redesign at 6a. 6a keeps its contract meaning (mappers + multi-turn semantics). Optional de-risk: a tiny `:sample` smoke that hits real Anthropic single-turn on the TESTER |
| **3b** OpenAI + OpenRouter | `ChatCompletionsProvider` (`openAi()`, `openRouter()`), internal `ChatDialect`, nested tool shape, `reasoning_effort:"none"` guard, `finish_reason` + refusal mapping, usage normalization | 3a | Don't port CT's flat tool shape (§2.3). Golden JSON tests against documented shapes |
| **4** `:keystore` | `KeystoreCrypto(+Seam)` generalized, per-provider aliases, DataStore, `KeystoreCredentialSource` | 2 (`ProviderId`, `Credential`) | Fully parallel with 3a/3b/5. Robolectric or instrumented tests for KeyStore |
| **5** SingleShot | `SingleShotStrategy` over `ToolSpecProvider` + `OutcomeResolver`; forced-vs-auto via capability; outcome mapping table; batch proposal → coordinator | 2, 3a (3b for OpenAI JVM tests) | CT port. The CT gate scenarios (weak match, batch, amended confirm, deferred `commitHeld`) are the acceptance tests |
| **6a** Multi-turn mappers | Anthropic ↔ neutral ↔ Chat round-trips: `tool_result` batching vs one `role:tool` per call, `is_error` encoding, native replay rules, parallel tool calls, argument-string double-decode, cache directive per dialect | 3a, 3b | One **dialect conformance suite** run against both mappers with recorded fixtures |
| **6b** AgenticLoop | Port of `AnthropicAgentLoop` onto 6a: whole-turn validation, token ceiling → final-iteration guard → validate → dispatch order, 2-strike abort, held `tool_result`, effects on every outcome, bounds from policy | 2, 6a | Carry SB's invariants over as named tests. The "unreachable `error()`" after the loop stays as a loud invariant |
| **7** Verify + tag | `:sample` (E2 fixture, fake `ToolExecutor`, BYO key via `:keystore`); Gate-1 on the TESTER (2+ turns, `cacheRead > 0` on turn 2+, ~7,016 ballpark); README integration guide; **ABI baseline dump**; JitPack clean-cache resolve; tag row to the orchestrator | all + E2 | The E2 fixture is the only external dependency. Keep it off the critical path by requesting it in step 1 |

**Phase-structure implications for the roadmap:** 2 is the architectural keystone and deserves its own phase with deep tests. 3a is the largest step (types + router + Anthropic + ON_DEVICE + CI matrix); consider splitting it into 3a-i (types + router + ON_DEVICE gate, all pure JVM) and 3a-ii (Anthropic transport + OkHttp matrix). 4 can be a parallel phase. 6a is small if 3a lands the types, and could merge with 3b.

---

## Open questions for discuss/planning

1. **`ProviderId` / `StrategyId` as a value class vs an enum.** The contract lists four provider ids but doesn't mandate an enum. A value class keeps additions additive. Confirm it doesn't conflict with contract wording (no amendment is needed if the orchestrator agrees).
2. **Should transport failures escalate to a tier on a different provider** (`escalateOnTransportFailure`, default false)?
3. **Is `core/testing` in the main artifact acceptable**, or should a `:testing` module be proposed as an amendment?
4. **CT OpenAI tool shape (§2.3):** ask caltracker-android-9a whether OpenAI (not OpenRouter) was ever device-verified with the flat tool shape.
5. **Tail caching (`conversationTail`) default:** off in v1.0 (A10 parity is only the system breakpoint), or on for Anthropic? Measure on the TESTER during step 7.
6. **Deferred-held lifetime:** should `HeldProposal` carry an expiry, or is it fully app-owned? This ties to the SB snackbar/Undo Center UX.

---

## Sources

- **Contract + project (HIGH):** `/home/yahir/Projects/Reusable/android/voice-action-engine/CROSS-REPO-SCOPE-CONTRACT.md` (§3, §5, §6.2, §10 A1–A14, E1–E3, §11), `/home/yahir/Projects/Reusable/android/voice-action-engine/.planning/PROJECT.md`
- **SB port sources, read in full (HIGH):** `…/SecondBrain/app/src/main/java/com/example/secondbrain/core/agent/{AnthropicAgentLoop,AgentLoopResult,MutationGate,VoiceConfirmGate,VoiceUndoOperations,PreMutationSnapshot}.kt`; `AnthropicToolRegistry.kt` (`ToolStep.Finished/Mutate`, `dispatch`/`executeSpec`)
- **CT port sources, read in full or in part (HIGH):** `…/CalTracker_Android/app/src/main/java/com/caltracker/app/ai/{AiProvider,ProviderRouter,AnthropicProvider,BaseAiProvider,OpenRouterProvider,OpenAiLogFoodRequestBuilder,ProviderId,ProviderKey}.kt`, `mcp/RepositoryToolFacade.kt`, `ui/voice/VoiceLogViewModel.kt` (resolve / resolveParsed / resolveBatch / onUndo / thresholds)
- **Anthropic API (HIGH, bundled Claude API reference cached 2026-09-25):** prompt caching (render order tools→system→messages, max 4 breakpoints, 5m/1h TTL, top-level automatic `cache_control`, per-model minimums of 512/1024/2048/4096, usage fields, 20-block lookback); forced `tool_choice` 400 on Opus 5.5 / Sonnet 5.5 / Fable 5.1; preserved thinking; stop reasons
- **OpenAI Chat Completions tools + reasoning restriction (MEDIUM):** [crush #2913](https://github.com/charmbracelet/crush/issues/2913), [LiteLLM #33221](https://github.com/BerriAI/litellm/issues/33221), [LibreChat #14355](https://github.com/danny-avila/LibreChat/issues/14355), [OpenAI community thread](https://community.openai.com/t/gpt-5-6-chat-completion-reasoning-effort-bug-behavior-change/1386454), [Migrate to the Responses API](https://developers.openai.com/api/docs/guides/migrate-to-responses)
- **Chat Completions nested tool shape (MEDIUM):** [Semantic Kernel #6825 ("Missing required parameter: 'tools[0].function'")](https://github.com/microsoft/semantic-kernel/issues/6825), [Responses vs Completions tool specs](https://community.openai.com/t/responses-api-function-calling-tool-specs-vs-completions-api/1324866), [Function calling guide](https://platform.openai.com/docs/guides/function-calling)
- **OpenAI caching (MEDIUM):** [Prompt caching | OpenAI API](https://developers.openai.com/api/docs/guides/prompt-caching), [Prompt Caching 201](https://developers.openai.com/cookbook/examples/prompt_caching_201)
- **OpenRouter (MEDIUM):** [Responses API Beta tool calling](https://openrouter.ai/docs/api/reference/responses/tool-calling), [Chat completion reference](https://openrouter.ai/docs/api/api-reference/chat/send-chat-completion-request), [Prompt caching guide](https://openrouter.ai/docs/guides/best-practices/prompt-caching), [What is OpenRouter (2026)](https://futureagi.com/blog/what-is-openrouter-2026/)
- **Kotlin ABI validation / compatibility (MEDIUM):** [Binary compatibility validation in the Kotlin Gradle plugin](https://kotlinlang.org/docs/gradle-binary-compatibility-validation.html), [Kotlin/binary-compatibility-validator](https://github.com/kotlin/binary-compatibility-validator), [Backward compatibility guidelines for library authors](https://kotlinlang.org/docs/api-guidelines-backward-compatibility.html), [AGP 9 built-in Kotlin ABI validation gap](https://medium.com/@tjokinen/binary-compatibility-validation-silently-stops-working-on-agp-9s-built-in-kotlin-974162978780)
- **Gemini Nano / ML Kit GenAI (MEDIUM, beta API):** [Get started with Prompt API](https://developers.google.com/ml-kit/genai/prompt/android/get-started), [Gemini Nano | Android Developers](https://developer.android.com/ai/gemini-nano), [ML Kit Prompt API blog](https://developer.android.com/blog/posts/ml-kit-s-prompt-api-unlock-custom-on-device-gemini-nano-experiences)
- **Publishing reference (HIGH):** `~/Projects/Reusable/android/backup-engine` (`jitpack.yml`, Kotlin 2.3.20, AGP 8.13.0, OkHttp 4.12.0)

---
*Architecture research for: voice-action-engine (tiered multi-provider LLM command pipeline library)*
*Researched: 2026-09-29*
