# Requirements: voice-action-engine

**Defined:** 2026-09-29
**Core Value:** A consumer app can hand the engine a transcript and get back a correct, typed outcome through a tier ladder it composed itself — with the cloud agentic path working on-device (Anthropic, prompt cache hitting) and every failure surfaced as a specific, loud reason, never a silent or opaque one.
**Authority:** `CROSS-REPO-SCOPE-CONTRACT.md` §6.2 (v1.0 = steps 1–7) + §5.1–5.2 + §10 (A1–A18, E1–E6) + §11. The contract wins over this file; drift goes to the orchestrator (`yahir-gsd-control-plane-f2`).
**Research:** `.planning/research/SUMMARY.md` (commit `64e6a8d`).

"Consumer" = an Android app (SB, CT, future) wiring the engine. Requirements are phrased as what a consumer (or the engine's own verification) can observe.

## v1 Requirements (milestone v1.0 → tag `v1.0.0`)

### Build, Publishing & Quality Gates (step 1)

- [x] **BLD-01**: Repo builds four modules: `:core` and `:providers` as pure Kotlin/JVM, `:keystore` as an Android library, `:sample` as an unpublished debug app; dependencies point one way (`:sample → {:providers, :keystore} → :core`); `:core` has no HTTP, Android, DI or other-hub dependency (L7, A7).
- [x] **BLD-02**: Toolchain matches consumers (Kotlin 2.3.20, AGP 9.2.1, Gradle 9.4.1, JDK 17 build) and every published module emits **JVM 11 bytecode**, so SB (Java 11) compiles against the DSL/inline surface.
- [x] **BLD-03**: A consumer can resolve each published module from JitPack at its per-module coordinate `com.github.Ygaray.voice-action-engine:voice-action-engine-{core,providers,keystore}` (E5; prefixed artifactIds per orchestrator ruling / E7) from an empty Gradle cache, with `:providers` pulling `:core` transitively; proven by commit SHA in step 1 and again at the tag. `jitpack.yml` has an explicit per-module install list and never builds/publishes `:sample`.
- [x] **BLD-04**: `./gradlew check` runs detekt (syntax mode, `maxIssues: 0`, no baseline file) clean on every library module, with invariant rules: forbidden imports for `okhttp3.internal.*`, `mockwebserver3.*`, `android.util.Log` and DI annotations in library code; `runCatching`, `println`, `printStackTrace` banned.
- [x] **BLD-05**: `explicitApi()` is on for every published module; Metalava is wired on all three published modules and its `api.txt` dumps are committed at the `v1.0.0` cut (additive-only enforcement from then on).
- [ ] **BLD-06 (A1 must-pass)**: `:providers` compiles against OkHttp **4.12.0** (plain `api` floor, no `strictly`/BOM) and the same compiled test classes run green on OkHttp **4.12.0, 5.2.1 and 5.5.0** runtime classpaths inside `./gradlew check` (okhttp + mockwebserver swapped together); a reflective runtime-version guard test proves each leg actually ran the version it claims.
- [x] **BLD-07**: Package root is `io.github.ygaray.voiceactionengine.*`, decided in step 1 and never changed.
- [x] **BLD-08**: `ECOSYSTEM.md` lists the engine with its per-module coordinates (E5); `graphify-out/` and the A10 fixture path are gitignored. (Control-plane registry/deps-index entries are the orchestrator's, written at the `v1.0.0` cut — LE-5.)
- [x] **BLD-09**: A fake-provider test harness (scripted `FakeAiProvider`, recording sinks) lives in `:core`'s **test sources only** (unpublished; orchestrator ruling), so strategies and the pipeline are JVM-testable with no network.

### Core Contract & Pipeline (step 2)

- [x] **CORE-01**: Consumer builds a pipeline with a DSL — `commandPipeline { tier(...); selector = Linear; policy = ... }` — and calls it with `CommandInput(transcript, language "en"|"es"|null, context)`.
- [x] **CORE-02**: Each strategy returns exactly one of `Completed | Escalate(reason, carry?) | NoMatch | Failed`; the pipeline climbs on `Escalate`/`NoMatch`, stops on `Completed`/`Failed`, and passes `carry` to the next tier.
- [x] **CORE-03**: `TierSelector.Linear` (default) and `TierSelector.Fixed(tier)` select the start tier.
- [x] **CORE-04**: `TierPolicy` is read per call from an app-supplied source and caps `offlineOnly`, `maxTier` (expressed as a stable public `StrategyId`, not a ladder index — consumers persist it and the ladder grows in v1.1), `allowedProviders`, max iterations (default 6), token ceiling (default 60,000) and max tokens per turn (default 4,096); `maxIterations < 2` is rejected. `offlineOnly` with no on-device provider available returns a loud `Failed` with **zero** HTTP calls; likewise an ON_DEVICE tier with no usable declared fallback returns loud `Failed(ProviderUnavailable)` and never silently climbs to a cloud tier (orchestrator ruling).
- [x] **CORE-05**: Every command outcome is returned, never thrown: any exception collapses to a typed outcome through one collapse helper, and `CancellationException` always propagates (rethrown before any broad catch; engine timeouts are `TIMEOUT`, distinct from `NETWORK`).
- [x] **CORE-06**: Failures carry a typed, open (`Other` leaf) `FailureReason` at least as fine as SB's: auth, billing, rate-limit, overloaded, timeout, network, malformed-response, malformed-tool-args, refusal, max-tokens, no-tool-call, budget-exceeded, tool-failure, not-configured, provider-unavailable — plus the provider request id when one exists. Never CT-style collapse to one opaque bucket.
- [x] **CORE-07**: Growing public taxonomies (failure/escalation/hold reasons, events) are open or carry an `Other` leaf, and growing public types are regular classes (not `data class`), so post-tag additions don't break consumers' exhaustive `when` or binary compat. `ProviderId` is a value class with `ANTHROPIC | OPENAI | OPENROUTER | ON_DEVICE` constants.

- [x] **CORE-08 (A19)**: An app can declare a non-mutating tool as `terminal` (declaring it mutating fails at build time). When the model calls it, the engine sends no tool_result and starts no further turn; earlier calls in that turn dispatch normally, in order; the run ends as `Completed` with a new nullable `terminalCall = TerminalCall(toolName, arguments: JsonObject)` field (`reply` null; commits/held carried; tier terminal). `:core` ships `Clarification(question, options: List<ClarificationOption(id, label)>)` (opaque app ids — CT food rows, SB list ids), a `ToolSpec.clarification(...)` builder and `TerminalCall.asClarification()`.
- [x] **CORE-09 (A19)**: `CommandInput` gains `parentRunId: String? = null`; a follow-up command (e.g. the chosen clarification option, rendered by the app's user-turn hook with the original transcript + question + choice) is a new run linked by `parentRunId` in the trace and in `CommitSink`/`onRunClosed`. No prior-transcript resumption in v1.0; the cached prefix is unchanged.

### Commit, Gate & Undo Seam (steps 2, 6b; A2, A6, A17, E1)

- [x] **GATE-01**: All writes go through one engine-owned path: strategy produces a prepared step (`Finished | Mutation`) → `PreApplyGate.admit(proposal)` (modeled on SB's `MutationGate.admit`, E1) → `Admit(amended?)` commits via `CommitSink`, or `Hold(reason?)` records a non-committing held proposal. Strategies never write directly. Each pending mutation carries an **app-owned opaque context object** that survives prepare → admit → apply; the gate may amend it (`Admit(amended)` = an amended context/proposal, e.g. SB's `PreMutationSnapshot` + merge authorization via `MutationDispatchContext`) and the engine never inspects it.
- [x] **GATE-02**: `PreApplyGate` supports **suspend mode** (SB: waits for the user inside `admit`; fail-closed on timeout/decline/error) via a shipped `AwaitingConfirmGate` helper (mutex, configurable timeout, default 120 s) and **defer mode** (CT: returns `Hold` immediately; the app later calls `commitHeld`, optionally with an amended batch — covers weak-match and "confirm all").
- [x] **GATE-03**: A `Hold` reason is optional and app-typed (the engine never requires or constructs one; A2's needs-confirmation + reason is carried when the app supplies it). A held action is never reported as success: inside the agent loop it yields the SB-byte-compatible `tool_result` `{"applied":false,"status":"held_for_confirmation"}`, and every outcome lists its held proposals.
- [x] **GATE-04 (A17)**: `CommitSink` is notified **per mutating action as it happens** (not at run end) — every mutating call, including held, `is_error` and dry-run preview, not just commits — with a `runId` and an `ExecutedToolCall`-shaped payload: tool name, mutating flag, outcome (`committed | held | preview | is_error`), target ids, and the pre-mutation snapshot captured at the gate (shape kept compatible with the v1.1 `:undo` journal, A18).
- [x] **GATE-05 (A17)**: `CommitSink.onRunClosed(runId, terminalOutcome)` fires exactly once on **every** exit path — done, cancelled, budget exceeded, provider error, escalation exhausted — proven by one test per path.
- [x] **GATE-06 (A17)**: Every outcome variant carries the full **ordered** executed-action list — every mutating call (committed, held, `is_error`, preview), not just commits — and the committed actions, so consumers can classify outcomes and offer Retry only when nothing committed.
- [x] **GATE-07 (A17, no duplicate writes)**: A tier that has committed ≥1 action (any admitted mutation whose apply ran, including errored applies) **or holds ≥1 pending proposal** cannot escalate (HOLD-terminal clarification of A17). A committed tier that asks to escalate becomes `Completed` with a public, non-defaultable `partial = true` field and the suppressed escalation reason in the trace; the agentic budget stop stays `Failed(BudgetExceeded)`. Explicit tests show a committing tier that asks to escalate never reaches the next tier and no write is repeated.

### Telemetry (step 2)

- [x] **TEL-01**: Every outcome carries a `CommandTrace`: per-tier attempts, escalation reasons, provider/model, tokens normalized as `{inputUncached, cacheRead, cacheWrite, output}` across providers (Anthropic `input_tokens` excludes cached tokens; OpenAI `prompt_tokens` includes them), and latency. A cross-provider parity test proves the CORE-04 token ceiling (SB's sum semantics) counts the same work identically on every provider.
- [x] **TEL-02**: Consumer can register an optional typed event callback that receives pipeline events live (tier started/finished, provider call, commit, hold, run closed, `CacheNotEngaged`).
- [x] **TEL-03**: A `CacheNotEngaged` event fires when a provider with caching declared returns zero cache read/write on a prefix above the model's minimum cacheable length (and stays silent below the minimum).
- [x] **TEL-04**: A canary test proves no API key, transcript, tool argument or tool_result content appears in the trace, events, any `toString()`, or any failure message; failures carry HTTP status and provider `error.type` only, never bodies.

### Providers (steps 3a, 3b)

- [x] **PROV-01**: Neutral multi-turn transcript types (messages, tool calls, tool results, system, usage, stop reason, cache directive, and a verbatim `NativeReplay` payload on assistant turns) live in `:core` from step 3a.
- [x] **PROV-02**: The engine asks the app per call, through a seam, for the active provider, model and key; a provider switch takes effect on the next command; a missing key returns `NotConfigured` before any network call; one provider's key is never used for another.
- [x] **PROV-03**: Provider/model are snapshotted **once per command** (never re-read per loop iteration), so a mid-command settings change can't switch models and break the cache.
- [x] **PROV-04**: `AnthropicProvider` sends tools + system with exactly one `cache_control: ephemeral` on the (last) system block and no breakpoint on messages (A10 parity with SB's `buildRequestBody`), uses `anthropic-version 2023-06-01`, HTTPS-only fixed base URL (overridable for tests only), and a cancellation-safe `Call.await()` that closes late responses.
- [x] **PROV-05**: The tools + system prefix is byte-identical across calls and iterations for the same command inputs (tools sorted by name, no timestamps); a unit test encodes the same request twice and compares bytes.
- [x] **PROV-06**: The detected language (`CommandInput.language`), date/time and transcript are placed only in the user/messages portion — never interpolated into the cached tools + system prefix; a test proves switching `en`↔`es` leaves the prefix bytes unchanged.
- [x] **PROV-07**: Forced tool choice uses a per-model capability table the app can override, shipping **verified** values: `claude-opus-5-5`, `claude-sonnet-5-5`, `claude-fable-5-1`, `claude-mythos-5-1` marked forced-tool-unsupported up front (Anthropic docs: 400 on every forced request; thinking can't be disabled), so they go straight to `auto` + instruction; SingleShot never enables manual extended thinking. For unknown ids, on the specific forced-tool 400 the provider retries once with `auto` + (strict only per PROV-12) + an instruction, and "no tool call" maps to `NoToolCall` (never a crash, never a fabricated parse).
- [x] **PROV-08**: `ChatCompletionsProvider` serves both OpenAI and OpenRouter over Chat Completions with the **nested** `{"type":"function","function":{...}}` tool shape, strict-mode keyword stripping, `reasoning_effort:"none"` when tools are present on models that require it, `max_completion_tokens` for reasoning models, `arguments` JSON-string decode → `MalformedToolArguments`, `finish_reason`/refusal mapping, OpenRouter HTTP-200 error bodies, and `provider.require_parameters: true` when forcing a tool. `reasoning_effort: "none"` is sent explicitly whenever tools are present (gpt-5.6-* default to medium). Models whose tools are Responses-API-only on OpenAI (GPT-6 Astra, GPT-6.1 Sol) are marked tools-unsupported in the capability table and fail loudly with a typed reason before any network call (orchestrator ruling: Option A, Responses as additive v1.x, LATER-03). The model capability table is **public API** so consumer model pickers can mark or filter tool-incapable models.
- [x] **PROV-09**: Transient HTTP failures (429, 5xx, timeouts) are retried at most once (`maxRetries = 1`) at the transport-call level only (A16): a retry never re-executes tools or re-commits actions — a test proves a retried call produces no duplicate tool execution or `CommitSink` commit.
- [x] **PROV-10**: `ON_DEVICE` exists as a provider slot with a runtime capability gate; on devices without on-device support (the S22s) it reports unavailable and the router uses only the app's declared fallback — no Nano/AICore implementation (L10, A5).
- [x] **PROV-11**: Provider HTTP clients carry no logging interceptors (derived via `newBuilder()` with interceptors stripped) and bodies are read with the 4.12-compatible API (`body?.string()`).
- [x] **PROV-12**: Strict tool schemas never wipe data: the engine sends `strict: true` only when a tool schema has **no optional properties**; otherwise the call is non-strict (optional params stay genuinely optional) with local schema validation. A per-provider contract test proves an omitted optional parameter arrives **absent** — never as `""`, `[]` or a filled default — since consumers (SB) treat `""`/`[]` as "clear".
- [x] **PROV-13**: The provider HTTP timeout is configurable per provider/strategy with a default of at least 60 s (SB's agentic calls need it); engine timeouts map to `TIMEOUT`.

### Keystore (step 4)

- [x] **KEY-01**: Consumer can store, read and delete a BYO API key per provider, encrypted with AndroidKeyStore AES/GCM and persisted in the app's own `DataStore<Preferences>`.
- [x] **KEY-02**: Keys map to storage through an **app-supplied explicit `KeySlot` table** (alias + DataStore key per provider), so SB and CT keep their existing aliases/keys and no existing user's key is stranded on migration; the engine never derives names by formula and never opens a second DataStore on the app's file.
- [x] **KEY-03**: Reads return typed states `NotConfigured | Ready | KeyMissing | Unreadable`; the decrypt path never creates a key (a restored backup without its Keystore key reports `KeyMissing`); encryption uses a synchronized get-or-create path and `java.util.Base64` NO_WRAP-compatible encoding.
- [x] **KEY-04**: A `KeystoreCredentialSource` adapter plugs `:keystore` into the provider seam (PROV-02); the round trip is verified by JVM tests via the crypto seam plus one instrumented test on the TESTER.

### SingleShot Strategy (step 5)

- [x] **SHOT-01**: Consumer can run a `SingleShotStrategy` that makes one forced-tool extraction call using the app's `ToolSpecProvider`, then resolves the result locally through the app's `OutcomeResolver` into a (possibly batch) commit proposal routed through the commit path (GATE-01).
- [x] **SHOT-02**: Default mapping: no tool call / prose → `Escalate(NoToolCall)`; refusal → `Failed(REFUSAL)`; both overridable per tier; `parallel_tool_calls: false` on Chat Completions.
- [x] **SHOT-03**: CT's confirm scenarios pass as acceptance tests: weak match held for confirmation, batch proposal, amended confirm, deferred `commitHeld`.

### Multi-turn Mappers (step 6a)

- [ ] **XCR-01**: Anthropic ↔ neutral and Chat Completions ↔ neutral mappers round-trip multi-turn tool conversations; one conformance suite runs against both.
- [ ] **XCR-02**: Assistant turns are replayed **verbatim** from `NativeReplay` to the same provider/model (golden fixture includes thinking blocks); transcripts never cross providers (`carry` is semantic only).
- [ ] **XCR-03**: Tool results are encoded per dialect — Anthropic batches all results for a turn in one user message with `is_error`; Chat Completions sends one `role:tool` message per call id — with coverage for parallel calls and empty arguments, and golden tests built from recorded, sanitized real response bodies.

### Agentic Loop Strategy (step 6b)

- [ ] **LOOP-01**: Consumer can run an `AgenticLoopStrategy` over its `ToolSpecProvider` + two-phase `ToolExecutor` (`prepare` → `Finished | Mutation`) on any cloud provider, with mutating steps enforced through the gate by the engine.
- [ ] **LOOP-02**: The loop keeps SB's guards as named tests: whole-turn validation, token-ceiling check before dispatch, final-iteration guard (no tool runs on the last permitted iteration), sequential dispatch, 2-strike tool-failure abort, unknown tool → `is_error`, bounds from `TierPolicy`.
- [ ] **LOOP-03**: Every exit path (done, budget, cancel, error) reports the executed actions and commits made so far; nothing committed is hidden behind a failure.

### Port Cleanups (leave-behinds)

- [x] **CLN-01**: Library code has no DI-framework annotations; everything is wired with plain constructors/builders/DSL.
- [ ] **CLN-02**: Library code contains no app-domain types or prompts (no `LogFood*`, `log_food`, SB `SYSTEM_PROMPT`, SB tool names, `MutationTier`) and hard-codes no tool count.
- [x] **CLN-03**: Limits and model ids come from policy/config defaults, not hard-coded constants.
- [x] **CLN-04**: The library never reads app settings storage directly (provider/model/key/policy arrive through seams).
- [x] **CLN-05**: Library comments carry no app planning ids (T-xx-xx, WR-xx, "Phase NN D-xx").

### Verification, Docs & Tag (step 7; A8, A10, A16, §11)

- [ ] **VER-01**: `:sample` loads the LE-1 fixture (`sb-a10-fixture.json`, sha256 `ebd3ef4a…af4ed3e`) from a **gitignored** path (LE-7), failing loudly at runtime/debug-task time — never at Gradle configuration time — if absent; it provides a fake `ToolExecutor` with canned results, a BYO-key field stored via `:keystore`, and pins OkHttp 5.2.1 so 4.12-compiled engine bytecode runs on 5.x.
- [ ] **VER-02 (A8/A10 Gate-1)**: On the TESTER (`…-s22-ultra-2`), the agentic loop on Anthropic runs ≥2 turns with turn-1 `cache_creation_input_tokens > 0` and turn-2+ `cache_read_input_tokens > 0` in SB's ballpark (~7,016), confirm gate in canned-admit mode; prefix size and the model's minimum cacheable length are logged.
- [ ] **VER-03 (A16)**: From `:sample`, one live single-shot smoke call each to Anthropic, OpenAI and OpenRouter (Yahir's real keys) returns a parsed tool call — catching wire-shape errors fakes can't (cf. CT's flat-shape bug). The OpenAI smoke deliberately uses a Chat-Completions-compatible model (`gpt-5.4-mini`, `reasoning_effort: "none"`), and each provider's smoke includes an EDIT-shaped call proving an omitted optional arrives absent (PROV-12).
- [ ] **VER-04**: README (plus integration doc) is good enough that an AI agent can wire the engine into a new app from it alone: per-module coordinates, a minimal pipeline, each seam, both gate modes, `else` branches on open taxonomies; renders `terminalCall`/`Clarification` as pressable options (A19); states explicitly that `Completed(partial = true)` must render as "did X, couldn't finish", never as full success; states which provider/model combos are **uncached in v1.0** (OpenRouter `anthropic/*` ids need explicit `cache_control`, deferred to LATER-02) and which OpenAI models can't use tools on Chat Completions; `:sample` is referenced as the working example.
- [ ] **VER-05 (§11)**: `v1.0.0` is cut only when verification is green, the API is additive (Metalava baseline committed), seams honor the contract, the tag is pushed and JitPack builds every module; the full ledger row is **messaged to the orchestrator** (A14), never committed to §11 here.

## v2 Requirements (deferred)

### v1.1 milestone (contract §6.2 steps 8–13 + A18)

- **V11-01**: `LocalGrammarStrategy` + bilingual EN/ES `GrammarPack` DSL.
- **V11-02**: `PlanThenExecuteStrategy` (respecting GATE-07).
- **V11-03**: `TierSelector.Router` (default off). Placement: grammar is always a free pre-pass; the Router picks only among the LLM tiers.
- **V11-04**: Bundled on-device model spike; verdict to the orchestrator.
- **V11-05**: Standalone `:undo` module (A18) and pipeline integration. `:undo` depends on NOTHING (not even `:core`), has its own coordinate `com.github.Ygaray.voice-action-engine:voice-action-engine-undo` (E7), and is a journal/memento design with per-entity adapters (read / write back / re-insert if deleted), explicit compensators for out-of-DB side effects, an unchanged-since-commit check that refuses loudly and never clobbers, never silently partial, grouped by `runId`, individual Undo for isolated actions and "Undo all (N)" per run.
- **V11-06**: `:voice-adapter` (needs the `:stt` tag).

### Later additive options

- **LATER-01**: Moving/tail cache breakpoint on message history (measure at Gate-1 first).
- **LATER-02**: OpenAI `prompt_cache_key`; OpenRouter `anthropic/*` cache_control passthrough.
- **LATER-03**: OpenAI Responses API dialect.
- **LATER-04**: Published `:testing` module with shared fakes (needs an A7-style amendment; fakes stay in `:core` test sources for v1.0).
- **LATER-05**: Separate/weighted token budgets (v1.0 keeps SB's sum for parity).
- **LATER-06**: Gemini Nano / AICore `ON_DEVICE` implementation (Pixel 10).

## Out of Scope

| Feature | Reason |
|---------|--------|
| Streaming responses | Commands are short; adds API surface and cancellation complexity with no consumer need |
| Parallel tool execution | Ordering + gate + undo correctness depend on sequential dispatch |
| Dollar-cost tables | Ceilings are in tokens; prices drift faster than tags |
| Reflection/annotation `@Tool` declarations | Adds kotlin-reflect; schemas come from the app's `ToolSpecProvider` |
| Cross-command memory | Each command is independent; apps own history |
| Automatic cross-provider failover | Would break the never-substitute-a-key rule; failures stay loud |
| DI framework integration | Libraries shouldn't force Hilt; apps wire plain constructors |
| Library UI | YAT owns presentational AI-voice UI |
| Retrying tool execution | Would duplicate writes |
| Vendor SDKs (anthropic-java, openai-java) | Drag Jackson/kotlin-reflect onto consumer APKs; ports already use OkHttp + kotlinx |
| Telemetry as a `Flow` API | Consumers can wrap the callback themselves |
| Model catalog fetching | Stays in CT; not in §6.2 |
| Committing the A10 fixture | SB repo is private, this repo is public (LE-7) |
| Early SB branch on the engine | Rejected in A10; real-SB parity is SB's Wave-1 Gate-1 |
| Forcing a consumer's OkHttp version | A1/A11 |

## Traceability

| Requirement | Phase | Status |
|-------------|-------|--------|
| BLD-01 | Phase 1 | Complete |
| BLD-02 | Phase 1 | Complete |
| BLD-03 | Phase 1 | Complete |
| BLD-04 | Phase 1 | Complete |
| BLD-05 | Phase 1 | Complete |
| BLD-06 | Phase 4 | Complete |
| BLD-07 | Phase 1 | Complete |
| BLD-08 | Phase 1 | Complete |
| BLD-09 | Phase 1 | Complete |
| CORE-01 | Phase 2 | Complete |
| CORE-02 | Phase 2 | Complete |
| CORE-03 | Phase 2 | Complete |
| CORE-04 | Phase 2 | Complete |
| CORE-05 | Phase 2 | Complete |
| CORE-06 | Phase 2 | Complete |
| CORE-07 | Phase 2 | Complete |
| CORE-08 | Phase 2 | Complete |
| CORE-09 | Phase 2 | Complete |
| GATE-01 | Phase 2 | Complete |
| GATE-02 | Phase 2 | Complete |
| GATE-03 | Phase 2 | Complete |
| GATE-04 | Phase 2 | Complete |
| GATE-05 | Phase 2 | Complete |
| GATE-06 | Phase 2 | Complete |
| GATE-07 | Phase 2 | Complete |
| TEL-01 | Phase 2 | Complete |
| TEL-02 | Phase 2 | Complete |
| TEL-03 | Phase 3 | Complete |
| TEL-04 | Phase 4 | Complete |
| PROV-01 | Phase 3 | Complete |
| PROV-02 | Phase 3 | Complete |
| PROV-03 | Phase 3 | Complete |
| PROV-04 | Phase 4 | Complete |
| PROV-05 | Phase 4 | Complete |
| PROV-06 | Phase 4 | Complete |
| PROV-07 | Phase 4 | Complete |
| PROV-08 | Phase 5 | Complete |
| PROV-09 | Phase 4 | Complete |
| PROV-10 | Phase 3 | Complete |
| PROV-11 | Phase 4 | Complete |
| PROV-12 | Phase 5 | Complete |
| PROV-13 | Phase 4 | Complete |
| KEY-01 | Phase 6 | Complete |
| KEY-02 | Phase 6 | Complete |
| KEY-03 | Phase 6 | Complete |
| KEY-04 | Phase 6 | Complete |
| SHOT-01 | Phase 7 | Complete |
| SHOT-02 | Phase 7 | Complete |
| SHOT-03 | Phase 7 | Complete |
| XCR-01 | Phase 8 | Pending |
| XCR-02 | Phase 8 | Pending |
| XCR-03 | Phase 8 | Pending |
| LOOP-01 | Phase 9 | Pending |
| LOOP-02 | Phase 9 | Pending |
| LOOP-03 | Phase 9 | Pending |
| CLN-01 | Phase 1 | Complete |
| CLN-02 | Phase 9 | Pending |
| CLN-03 | Phase 3 | Complete |
| CLN-04 | Phase 3 | Complete |
| CLN-05 | Phase 1 | Complete |
| VER-01 | Phase 10 | Pending |
| VER-02 | Phase 10 | Pending |
| VER-03 | Phase 10 | Pending |
| VER-04 | Phase 10 | Pending |
| VER-05 | Phase 11 | Pending |

**Coverage:**

- v1 requirements: 65 total
- Mapped to phases: 65
- Unmapped: 0 ✓

**Placement notes** (CLN-* and TEL-04 map to the first phase that makes them enforceable):

- CLN-01 and CLN-05 → Phase 1. The detekt invariants (DI-annotation imports, planning-id comments) gate every later phase.
- CLN-03 and CLN-04 → Phase 3. Model ids and settings first arrive through the selection seam and capability table.
- TEL-04 → Phase 4. This is the first phase where every secret carrier exists: API key header, transcript, tool args/results, provider error bodies.
- CLN-02 → Phase 9. Checked once both ports (CT SingleShot, SB loop) have landed.
- BLD-06 (A1) → Phase 4. Phase 1 builds the matrix plumbing and guard test; Phase 4 makes the real transport tests green on all three legs.
- The contract step-1 "fake-provider test harness" has no REQ-ID of its own. It is carried as a Phase 1 success criterion.

---
*Requirements defined: 2026-09-29*
*Last updated: 2026-09-29 after roadmap creation (traceability filled, 60/60)*
