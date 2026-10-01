# Phase 3: Transcript Types, ProviderRouter & On-Device Gate - Research

**Researched:** 2026-09-30
**Domain:** Pure-JVM Kotlin library design (`:core` only): neutral multi-turn transcript types, per-command provider/model/key resolution, an `ON_DEVICE` availability gate, cache-miss diagnostics, and a strictly-additive public API under Metalava.
**Confidence:** HIGH on the in-repo hooks and the Metalava behavior (read the code, ran experiments). MEDIUM on cache minimums (official docs fetched, but they change per model release). The chars-per-token calibration is MEDIUM (one real fixture plus published rules of thumb).

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

- **D-01 [replay]:** Nullable NativeReplay(provider: ProviderId, model, raw: JsonElement) with redacted toString; neutral parts only Text + ToolCall; thinking lives only in raw; rebuild from neutral parts only when null or stamped for a different provider/model _(source: ai-auto)_
- **D-02 [credential]:** Separate CredentialSource with a typed result, so a lost Keystore key surfaces as "re-enter your key", not "not configured"; KeystoreCredentialSource implements it _(source: ai-auto)_ _(provisional — refresh at execution; depends on Phase 2)_
- **D-03 [snapshot]:** Once per command per tier, lazily on first use, frozen in a bound handle (lets an app use a cheap model for SingleShot and a strong one for Agentic; mid-command settings changes don't apply) _(source: ai-auto)_
- **D-04 [defaults]:** No default model id in the engine; README states it explicitly; model catalog/defaults stay app-owned _(source: ai-auto)_
- **D-05 [capabilities]:** Keyed by (ProviderId, exact id); each provider contributes defaults incl. an unknown-id default; app override layer; router checks request needs vs capabilities before any call; Phase 3 ships type/lookup/override/check with fake entries, verified entries land in Phases 4–5 _(source: ai-auto)_
- **D-06 [ondevice]:** Runtime availability query mirroring ML Kit statuses (v1.0 always Unavailable("not_implemented")); fallback carried in the per-command selection, itself subject to offlineOnly/allowedProviders; trace records fallbackFrom; the Phase 2 offlineOnly check calls this same gate _(source: ai-auto)_ _(provisional — refresh at execution; depends on Phase 2)_
- **D-07 [ondevice-fail]:** Loud `Failed(ProviderUnavailable(ON_DEVICE, code))`; never a silent climb to a cloud tier that spends a key without a declared fallback. Orchestrator ruling (option A). _(source: human)_
- **D-08 [cache-detect]:** In :core after each response; fires only if the directive's static prefix is on, caching mode ≠ NONE with a known minimum, cacheRead==0 && cacheWrite==0, and the estimated prefix ≥ minimum; unknown minimum = silent _(source: ai-auto)_ _(provisional — refresh at execution; depends on Phase 2)_
- **D-09 [cache-estimate]:** Characters of frozen system + canonical tools with a conservative ratio erring silent (A10 fixture ≈ 3 chars/token); calibrate ratio via research _(source: ai-auto)_
- **D-10 [cache-mode]:** EXPLICIT_BREAKPOINTS fires on read+write==0 any turn; AUTOMATIC only on turn ≥2 of the same handle with cacheRead==0 (never on turn 1, OpenAI never reports writes) _(source: ai-auto)_
- **D-11 [a19-clarification]:** A19: `ToolSpec` carries `terminal: Boolean = false`; a terminal tool that is also declared mutating fails at build time. _(source: human — orchestrator, contract A19)_

**Runtime Decisions (CONTEXT, refreshed against Phase 2 output):**

- **credential:** CONFIRMED vs Phase 2 output. Phase 2 shipped a plain public `Credential(provider: ProviderId, apiKey: String)` (core/Credential.kt) with no lookup seam. Phase 3 adds a separate `CredentialSource` returning a typed result (Present(Credential) / Missing / Unreadable(reason)), purely additive, without changing Credential. A lost Keystore key surfaces as "re-enter your key", not "not configured". KeystoreCredentialSource (Phase 6) implements it.
- **ondevice:** CONFIRMED vs Phase 2 output, with a concrete hook. Phase 2 has an INTERNAL `PipelineBuilder.onDeviceAvailability: suspend () -> Boolean = { false }`, consumed by `PolicyPreCheck` (guarded, so a probe fault records ON_DEVICE_PROBE_ERROR). Ladder.blockedOnDevice then yields Failed(ProviderUnavailable(ON_DEVICE, "on_device_unavailable")). Phase 3 adds the public typed gate (an OnDeviceCapability query mirroring ML Kit statuses; v1.0 always returns Unavailable("not_implemented")) and wires THAT gate into this existing internal hook, so there is one gate and no second path. The fallback is declared in the per-command selection, is subject to offlineOnly/allowedProviders, and the trace records fallbackFrom. Phase 2 behavior is preserved: an on-device-only tier never climbs to cloud (orchestrator-confirmed CORE-04 ruling).
- **cache-detect:** CONFIRMED vs Phase 2 output. Phase 2 already ships `PipelineEvent.CacheNotEngaged(runId, strategy, provider, model)` and `RunRecorder.cacheNotEngaged(...)`, but nothing calls it yet (its KDoc says only tests reach it). Phase 3 adds the detection in :core after each response and calls the existing RunRecorder.cacheNotEngaged. It fires only when the directive static prefix is on, caching mode != NONE with a known minimum, cacheRead==0 && cacheWrite==0, and the estimated prefix >= that minimum. An unknown minimum stays silent. Reuse the event; do not add a second one.
- **FakeAiProvider (carried from Phase 1, orchestrator-acknowledged):** Phase 1 shipped only generic fixtures; ROADMAP SC5 FakeAiProvider lands in Phase 3.

### Claude's Discretion

Anything not listed above follows `.planning/research/SUMMARY.md` and the phase's own research; Source `ai-auto` decisions took research's recommendation (orchestrator accepted the auto-resolved remainder).

### Deferred Ideas (OUT OF SCOPE)

See REQUIREMENTS.md v2 / LATER items. (Relevant here: LATER-01 moving cache breakpoint on message history, LATER-02 OpenAI `prompt_cache_key` / OpenRouter `anthropic/*` cache_control passthrough, LATER-03 OpenAI Responses dialect, LATER-06 Gemini Nano / AICore `ON_DEVICE` implementation. No Nano/AICore code in this phase; no HTTP in this phase.)
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| PROV-01 | Neutral multi-turn transcript types (messages, tool calls, tool results, system, usage, stop reason, cache directive, verbatim `NativeReplay` on assistant turns) in `:core` | "Recommended type inventory" (Transcript block); `Usage` already exists in `telemetry/Usage.kt` and must be reused, not redefined; `ToolSpec` already exists in `strategy/` |
| PROV-02 | Engine asks the app per call, through a seam, for provider, model and key; switch takes effect next command; missing key = `NotConfigured` before any call; one provider's key never used for another | "Hook inventory" (`CommandSession`, `RunSession`, `TierWalk.runTier`), router resolution order, `CredentialSource` result type, mismatch guard |
| PROV-03 | Provider/model snapshotted once per command (never per loop iteration) | Frozen-handle pattern (Pattern 2); handle lives on the per-tier `RunSession` |
| PROV-10 | `ON_DEVICE` slot with runtime capability gate; unavailable on S22s; router uses only the declared fallback; no Nano/AICore | Pattern 3 (one gate, wired into the existing internal hook), fallback rules, trace `fallbackFrom` |
| TEL-03 | `CacheNotEngaged` fires for caching-declared provider with zero cache read/write above the model minimum; silent below | Pattern 4 and "D-09 calibration" sections; hook = `RunRecorder.cacheNotEngaged` |
| CLN-03 | Limits and model ids come from policy/config defaults, not hard-coded constants | "No hard-coded constants" scan test; limits stay in `TierPolicy` (read `TierPolicy.kt:6-9`) |
| CLN-04 | Library never reads app settings storage; provider/model/key/policy arrive through seams | Seams are `fun interface`s the app implements; scan test bans `java.io.File`/`System.getenv`/`SharedPreferences`-style reads in `src/main` |
</phase_requirements>

## Summary

Phase 3 is almost entirely additive work inside `:core` on top of a very clean Phase 2. Four Phase 2 hooks are the load-bearing seams: the per-tier `RunSession` (where the frozen model handle lives), the internal `PipelineBuilder.onDeviceAvailability` boolean hook (where the public on-device gate plugs in), `RunRecorder.cacheNotEngaged` (the already-tested, never-called event emitter), and `TurnRecord` / `CommandSession.recordTurn` (how every model turn reaches the trace and the token ceiling). `ToolSpec` already carries `terminal` with the build-time terminal-vs-mutating check, so D-11 is done; only the `strict` preference (and, if wanted, a cache member) remains to be added. Nothing in Phase 3 needs HTTP, a new dependency or a Gradle-script change, except one deliberate allow-list edit if new sealed types are introduced (see Pitfall 4).

The one design risk that is easy to get wrong is **public API evolution under Metalava**. I ran the check: in an isolated copy with a dumped baseline, adding a defaulted constructor parameter to `ToolSpec` fails the gate with `Binary breaking change: Removed constructor ... [RemovedMethod]`. Two patterns stay green: `@JvmOverloads` on the primary constructor, and "internal primary constructor + public secondary constructor with the old shape + `withX()` copy functions". Adding a default member to a public interface also stays green and compiles to a real JVM default method. Every Phase 3 type that an app or `:providers` constructs (messages, request/response, selection, credential results, capabilities) must be designed with one of those patterns from the first line, because Phase 11's `apiDump` freezes whatever exists.

On D-09: the real A10 fixture measures **3.01 chars per token** (21,109 characters of system plus compact tool JSON, against SB's reported 7,016 tokens). That is the low (firing-side) end of the plausible range, not an "err silent" value: prose is about 4, and the newer Anthropic tokenizer produces up to about 1.35x more tokens, which pushes tool JSON toward 2.3 to 3. To genuinely err silent, estimate with a **larger** divisor (recommended default 4.0, per-model overridable in the capability entry) and **cap the estimate by the response's own total prompt tokens** (`inputUncached + cacheRead + cacheWrite`), which is a strict upper bound on the prefix size when nothing was cached. That cap makes the main false-positive path impossible at no cost.

**Primary recommendation:** Build the router as an internal class behind a new `CommandSession.model()` accessor; resolve selection then credential then capability check then on-device gate lazily and freeze the result in a per-tier handle that also records the `TurnRecord` and runs the cache detector after every response; design every app/provider-constructed public type with `@JvmOverloads` or the internal-primary pattern; keep the sealed set minimal (add `Message` and `AssistantPart` only, and extend the allow-list in `scripts/review-api-surface.sh` in the same plan).

## Project Constraints (from CLAUDE.md)

Extracted from `/home/yahir/Projects/Reusable/android/voice-action-engine/.claude/CLAUDE.md` (treated as locked):

- `:core` depends on no other hub and has **no HTTP dependency** (L7, A7); the classpath allow-list is enforced by `gradle/invariants.gradle.kts` (a new `:core` dependency must extend the allow-list deliberately).
- Public API grows **strictly additively** once tagged; contract changes only via §10 amendments through the control plane. Tag cut is Phase 11; no `api.txt` is committed before it.
- **Domain-free**: library names no note/card/food; all app knowledge enters through seams.
- Quality: detekt zero baseline (`maxIssues: 0`, no baseline file); most of `:core` JVM-tested; two-gate UAT where device-verifiable (Phase 3 is pure JVM, so Gate-1 is a self-UAT of tests).
- **Secrets**: API keys, transcripts, tool args/results never reach logs, telemetry, exceptions or `toString()`.
- Banned in library code: DI annotations, `android.util.Log`, `okhttp3.internal.*`, `mockwebserver3.*`, `runCatching`, `println`, `printStackTrace`, app planning ids in comments (scanner `scanBannedConstructs`; detekt `ForbiddenImport`/`ForbiddenComment`).
- Kotlin 2.3.20, JVM 11 bytecode (`-Xjdk-release=11`), `explicitApi()`, no `data class` for growing public types, no enums (enforced by `ApiShapeTest`).
- Process: work goes through a GSD workflow (plan, execute); do not edit the repo outside one. The A13 gate applies: the orchestrator `yahir-gsd-control-plane-f2` sends GO before planning proceeds (CONTEXT status: "Ready for planning (after orchestrator GO — A13)").
- No project skills directory exists.

## Architectural Responsibility Map

This is a library, so "tiers" are modules and the app.

| Capability | Primary Owner | Secondary | Rationale |
|------------|--------------|-----------|-----------|
| Neutral transcript + request/response types | `:core` (`transcript` package) | `:providers` (constructs responses), strategies (construct requests) | Contract step 3a-i; SingleShot is a one-message transcript so multi-turn types must exist now (SUMMARY.md line 142) |
| `AiProvider` interface, `ModelCapabilities` table type | `:core` | `:providers` (implements, contributes defaults in Phases 4-5) | Providers live in another module, so every type they build or implement must be public in `:core` |
| Provider/model selection, credential lookup | App (implements `ProviderSelectionSource`, `CredentialSource`) | `:keystore` (Phase 6 `KeystoreCredentialSource`) | CLN-04: the library never reads settings storage |
| Router: selection then credential then capability check then on-device gate then bound handle | `:core` (internal) | — | Needs `TierPolicy`, `RunRecorder`, `guarded`, all internal to `:core` |
| `ON_DEVICE` availability | App/future module (implements `OnDeviceCapability`) | `:core` default = Unavailable("not_implemented") | L10: no AICore code in v1.0 |
| Cache-miss detection | `:core` (inside the bound handle) | — | Only `:core` can call internal `RunRecorder.cacheNotEngaged`; transports cannot |
| Model-id knowledge (min prefix, forced-tool support, etc.) | `:providers` defaults (Phases 4-5) + app overrides | — | CLN-03: no model ids in `:core` library code |
| `FakeAiProvider` | `:core` testFixtures (`core/testing`) | consumed by `:core` tests; SB/CT via `testFixtures` dependency is out of scope (LATER-04) | BLD-09 orchestrator ruling: fakes in test sources only |

## Standard Stack

### Core

No new libraries. Everything needed is already on `:core`'s classpath. [VERIFIED: core/build.gradle.kts:31-37 read this session]

```kotlin
dependencies {
    api(libs.coroutines.core)
    api(libs.serialization.json)
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    testImplementation(testFixtures(project(":core")))
}
```

| Library | Version | Purpose | Why |
|---------|---------|---------|-----|
| kotlinx-coroutines-core | 1.11.0 | `suspend` seams, `runTest` | `[VERIFIED: gradle/libs.versions.toml: coroutines = "1.11.0"]` |
| kotlinx-serialization-json | 1.11.0 | `JsonObject`/`JsonElement` for tool args and `NativeReplay.raw` | same file: `serialization = "1.11.0"`. `:core` does not apply the serialization compiler plugin, so no `@Serializable` in `:core` |
| JUnit 4 | 4.13.2 | tests | `junit = "4.13.2"` |
| kotlinx-coroutines-test | 1.11.0 | `runTest` | as above |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Hand-written `FakeAiProvider` | MockK / Mockito | Rejected in STACK.md ("Hand-written fakes"); the fake is itself a deliverable |
| Char-count prefix estimate | A tokenizer library (`tiktoken`, etc.) | Banned by the dependency allow-list and wrong for Claude (undercounts about 15-20% per the Claude API token-counting guidance); no HTTP `count_tokens` call is possible in `:core` |

**Installation:** none.

## Package Legitimacy Audit

No external packages are installed in this phase. Skipped: no `package-legitimacy` run is applicable.

| Package | Registry | Age | Downloads | Source Repo | Verdict | Disposition |
|---------|----------|-----|-----------|-------------|---------|-------------|
| (none) | — | — | — | — | — | — |

**Packages removed due to [SLOP] verdict:** none
**Packages flagged as suspicious [SUS]:** none

## Architecture Patterns

### Hook inventory: the exact existing code Phase 3 reuses or extends

All of the following were read this session; line numbers are from those reads. Package root is `io.github.ygaray.voiceactionengine.core` (note: `ARCHITECTURE.md` still says `voiceaction` and is stale on this).

| # | Hook | File:lines | Verbatim | What Phase 3 does with it |
|---|------|-----------|----------|---------------------------|
| H1 | Credential value | `core/Credential.kt:10-17` | `public class Credential(public val provider: ProviderId, public val apiKey: String) {` ... `override fun toString(): String = "Credential(provider=$provider)"` | **Do not change.** `CredentialSource` returns it inside a lookup result. Router verifies `credential.provider == requested provider` |
| H2 | ProviderId | `core/ProviderId.kt:7-8` | `@JvmInline public value class ProviderId(public val value: String)` with companion `ANTHROPIC`, `OPENAI`, `OPENROUTER`, `ON_DEVICE` | Key the capability table and selections by it. `ApiShapeTest.providerIdCompanionDeclaresExactlyFourPublicGetters` pins exactly four getters: **do not add a fifth companion constant** |
| H3 | Internal on-device hook | `pipeline/PipelineBuilder.kt:53` | `internal var onDeviceAvailability: suspend () -> Boolean = { false }` | Keep this property (tests assign it: `TierPolicyTest.kt:122` `onDeviceAvailability = onDevice`). Change only its **default** to delegate to the public gate (`{ onDevice.availability() is OnDeviceAvailability.Available }`). Add a public `var onDevice` next to it |
| H4 | Pre-check consumer | `pipeline/PolicyPreCheck.kt:40-43` | `val onDevice = strategies.any { ProviderId.ON_DEVICE in it.capabilities.providers } && guarded(onFault = { recorder.recordCode(TraceCode.ON_DEVICE_PROBE_ERROR); false }) { onDeviceAvailability() }` | Unchanged. Probed **at most once per execute**, **only** when some tier declares ON_DEVICE |
| H5 | On-device failure | `pipeline/PolicyPreCheck.kt:11-12,22-25` | `private const val ON_DEVICE_CAUSE = "on_device_unavailable"`; `fun blockedOnDevice(tier: CommandStrategy): Boolean = tier.capabilities.onDeviceOnly && !onDeviceAvailable`; `FailureReason.ProviderUnavailable(ProviderId.ON_DEVICE, ON_DEVICE_CAUSE)` | An **on-device-only tier is blocked here, before any strategy (and therefore the router) runs**. A fallback can therefore only help a tier whose static `StrategyCapabilities` declares ON_DEVICE **plus** the fallback provider (see Pitfall 6) |
| H6 | Cache event emitter | `telemetry/RunRecorder.kt:121-123` | `suspend fun cacheNotEngaged(strategy: StrategyId, provider: ProviderId, model: String?) { dispatch.send(PipelineEvent.CacheNotEngaged(runId, strategy, provider, model)) }` | The **only** way to emit it (`PipelineEvent.CacheNotEngaged` has an `internal constructor`, `PipelineEvent.kt:109-117`). Call from the handle. Do not add a second event; do not also call `recordCode` (that emits an extra `EngineCode` event) |
| H7 | Turn reporting | `strategy/CommandSession.kt:46`, `pipeline/RunSession.kt:29-31` | `public abstract suspend fun recordTurn(turn: TurnRecord)` / `recorder.turnRecorded(strategy, turn)` | The handle calls `session.recordTurn` after each response; strategies must **not** also call it (double count against `tokenCeiling`) |
| H8 | TurnRecord | `telemetry/TurnRecord.kt:17-24` | `public class TurnRecord(public val provider: ProviderId?, public val model: String?, public val stopReason: String?, toolNames: List<String>, public val usage: Usage, public val latencyMillis: Long)` | Needs a `fallbackFrom: ProviderId?` for the trace (pre-tag, so add with the secondary-constructor pattern; `TierAttempt` then exposes a derived `fallbackFrom` like it does `provider`/`model`, `CommandTrace.kt:66-69`) |
| H9 | Usage | `telemetry/Usage.kt:20-25,33` | `public class Usage(public val inputUncached: Long, public val cacheRead: Long, public val cacheWrite: Long, public val output: Long)`; `public val total: Long` | **Reuse as the response's usage.** ARCHITECTURE.md shows an `Int` variant: stale, ignore it |
| H10 | Session creation | `pipeline/TierWalk.kt:72` | `val session = RunSession(runId, parentRunId, strategy.id, policy, carry, coordinator, recorder)` | Add the router (or a router-bound factory) as a new constructor argument; `TierWalk` is built at `CommandPipeline.kt:166` `TierWalk(ladder, policy, coordinator, recorder, runId, input.parentRunId)` and `CommandPipeline` at `PipelineBuilder.kt:70-79`. No test constructs these directly (grep: only `RunRecorder(` in `EventsTest.kt:222`) |
| H11 | Session API | `strategy/CommandSession.kt:13` | `public abstract class CommandSession internal constructor()` | Add one `public abstract suspend fun model(...)`. Safe: the internal constructor means only the engine subclasses it. Post-tag, further additions must be non-abstract `open` members |
| H12 | Policy | `pipeline/TierPolicy.kt:6-9,36-44` | `DEFAULT_MAX_ITERATIONS = 6`, `DEFAULT_TOKEN_CEILING = 60_000L`, `DEFAULT_MAX_TOKENS_PER_TURN = 4_096`, `MIN_ITERATIONS = 2`; `public class TierPolicy internal constructor(... offlineOnly, maxTier, allowedProviders, maxIterations, tokenCeiling, maxTokensPerTurn, commandTimeoutMillis)` | The router reads `session.policy.offlineOnly` and `allowedProviders` to gate the **dynamic** selection and the fallback. `maxTokensPerTurn` must flow into `ModelRequest.maxTokens` from the strategy, not from a constant |
| H13 | Capabilities (static) | `strategy/StrategyCapabilities.kt:11,14-15` | `public class StrategyCapabilities(providers: Set<ProviderId>)`; `internal val onDeviceOnly: Boolean get() = providers.size == 1 && ProviderId.ON_DEVICE in providers` | Unchanged. Do **not** confuse with the new per-model `ModelCapabilities` (name it differently; suggested `ModelCapabilities`) |
| H14 | Collapse helper | `internal/Guarded.kt:29` | `internal suspend inline fun <T> guarded(onFault: (EngineFault) -> T, block: () -> T): T` | Wrap **every** app/provider call the router makes (selection source, credential source, gate, `AiProvider.complete`). It rethrows real cancellation; a leaked timeout becomes a fault. This is the repo's only justified `@Suppress` site: do not add another |
| H15 | Failure leaves | `failure/FailureReason.kt:113,185,196-208` | `public class ModelUnsupported : FailureReason`; `public class NotConfigured(public val provider: ProviderId?)`; `public class ProviderUnavailable(public val provider: ProviderId, public val cause: String?)` with `require(cause == null \|\| isStableCode(cause))` | Reuse: missing selection/key = `NotConfigured`; capability refusal = `ModelUnsupported`; on-device = `ProviderUnavailable(ON_DEVICE, code)`. **Add one new leaf** for an unreadable key (see Pattern 1) |
| H16 | TraceCode | `telemetry/TraceCode.kt:10-11` | `@JvmInline public value class TraceCode internal constructor(public val value: String)` | Add codes with the same pattern, e.g. a provider-fallback code, selection-source and credential-source fault codes |
| H17 | Existing `ToolSpec` | `strategy/ToolSpec.kt:40-46` | `public class ToolSpec(public val name: String, public val description: String, public val inputSchema: JsonObject, public val mutating: Boolean = false, public val terminal: Boolean = false,)` with `require(!(terminal && mutating)) { "a terminal tool must be non-mutating" }` | **D-11 is already satisfied.** Phase 3 adds only `strict` (tri-state `Boolean?`, null = engine decides per PROV-12) and, only if a concrete need exists, a cache member. Use the evolution pattern in Pitfall 1, not a bare default parameter |

### System Architecture Diagram

```
Strategy (Phase 7/9, here: ScriptedStrategy in tests)
   │  session.model()                        (first call per tier-run resolves; later calls return the same frozen handle)
   ▼
RunSession (per tier-run, internal) ──────────────► ModelRouter (internal, one per pipeline)
                                                      │
        policy = session.policy                       │ 1. selection = ProviderSelectionSource.select(strategyId)   [guarded]
        (offlineOnly, allowedProviders)               │      null / throws ──► Refused(NotConfigured(null) | Unexpected)  ── zero provider calls
                                                      │ 2. policy gate on selection.provider (allowedProviders / offlineOnly)
                                                      │      violation ──► Refused(ProviderUnavailable(p, "policy_forbids_provider"))
                                                      │ 3. ON_DEVICE? ──► OnDeviceCapability.availability()          [same gate as the pre-check]
                                                      │      not Available:
                                                      │        selection.fallback declared AND policy permits ──► restart at step 2 with the fallback, fallbackFrom = ON_DEVICE
                                                      │        else ──► Refused(ProviderUnavailable(ON_DEVICE, code))   (D-07, loud, never a silent climb)
                                                      │ 4. provider = registered AiProvider for selection.provider (absent ──► Refused(NotConfigured(p)))
                                                      │ 5. cred = CredentialSource.credential(selection.provider ONLY)   [guarded]
                                                      │      Missing ──► NotConfigured(p); Unreadable ──► CredentialUnreadable(p, cause); mismatch ──► refused
                                                      │ 6. caps = ModelCapabilityTable.lookup(provider, model)   (override layer over provider defaults)
                                                      │      request needs tools but caps say no ──► Refused(ModelUnsupported)
                                                      ▼
                                          BoundModel (frozen: provider, model, credential, caps, fallbackFrom, turnCount)
                                                      │ complete(ModelRequest)
                                                      │   ──► guarded { AiProvider.complete(model, request, credential) }  (the ONLY provider call site)
                                                      │   ──► ModelResult.Success | Failure (typed, never thrown)
                                                      │   ──► session.recordTurn(TurnRecord(provider, model, stop, toolNames, usage, latency, fallbackFrom))
                                                      │   ──► CacheDetector.check(...) ──► RunRecorder.cacheNotEngaged(strategy, provider, model)  (at most once per response)
                                                      ▼
                                            back to the strategy (Phase 7/9 turn that result into a StrategyOutcome)
```

### Recommended Project Structure

New code, all in `core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/`:

```
transcript/    # Message (sealed), UserMessage, AssistantMessage, ToolResultsMessage, AssistantPart (sealed), Text, ToolCall,
               # ToolResult, ToolChoice, ModelRequest, ModelResponse, StopReason (value class), CacheDirective, NativeReplay
provider/      # AiProvider, ModelResult (+Success/Failure leaves), ProviderSelectionSource, ProviderSelection, SelectionRequest,
               # CredentialSource, CredentialLookup (+Present/Missing/Unreadable), OnDeviceCapability, OnDeviceAvailability,
               # ModelCapabilities, CachingMode (value class), ModelCapabilityTable, CapabilityOverrides, BoundModel / ModelBinding
               # internal: ModelRouter, BoundModelImpl, CacheDetector
strategy/      # CommandSession (+ model()), ToolSpec (+ strict)
pipeline/      # PipelineBuilder (+ providers/selection/credentials/onDevice/capabilityOverrides), TierWalk/RunSession wiring
failure/       # FailureReason (+ CredentialUnreadable)
telemetry/     # TurnRecord (+ fallbackFrom), TierAttempt (+ derived fallbackFrom), TraceCode (+ codes)
core/src/testFixtures/.../core/testing/   # FakeAiProvider, ScriptedCredentialSource, ScriptedSelectionSource (explicitApi applies here too)
```

### Pattern 1: Seams as `fun interface`s with typed results, never nulls-for-errors

**What:** `ProviderSelectionSource.select(...)` returns `ProviderSelection?` (null = not configured, the only legitimate null). `CredentialSource.credential(provider)` returns a `CredentialLookup` with three leaves: `Present(Credential)`, `Missing`, `Unreadable(cause: String)`; `cause` is validated with the existing `isStableCode` so free text can never reach a `toString`. A new `FailureReason.CredentialUnreadable(provider, cause)` carries "re-enter your key" to the UI, distinct from `NotConfigured`. A source that throws is treated as `Unreadable("source_error")` with a trace code. [ASSUMED: leaf and code names are recommendations, not locked.]

**Why a non-sealed base for `CredentialLookup`:** the statuses come from an external system (Keystore) and Phase 6 will map `KeyMissing` and `Unreadable` onto them; an open base with an `internal constructor` and public final leaves keeps the Phase 2 rule (open taxonomies) and avoids growing the sealed allow-list. The router treats an unknown subclass defensively.

**Defense in depth for "keys never cross providers":** the router asks for `selection.provider` only, then checks `lookup.credential.provider == selection.provider`. A mismatching credential is refused (not sent) and traced. The `Credential` constructor already makes the provider part of the value (`Credential.kt:10`).

### Pattern 2: Frozen per-tier handle (D-03)

`RunSession` is created once per tier-run (`TierWalk.runTier`, `TierWalk.kt:72`), so a lazy cache field on it gives "once per command per tier, lazily on first use" with no extra bookkeeping, and the handle also owns the turn counter D-10 needs ("turn ≥2 of the same handle"). A multi-tier ladder therefore asks the selection seam once per tier; the success criterion's "once per command" is satisfied per tier-run (test both a one-tier loop of N turns and a two-tier ladder).

The handle exposes read-only facts a strategy needs to build a request (`provider`, `model`, `capabilities`) and one `complete(request)`. It must **not** expose the `Credential` or the app's selection object. A refused resolution should still return a handle-shaped result so strategies have one code path: `val result = session.model().complete(request)` where a refused binding returns `ModelResult.Failure(reason)` with **zero provider calls**. [ASSUMED: exact accessor shape is the planner's call; the invariants are the requirement.]

### Pattern 3: One on-device gate (D-06/D-07)

- Public: `fun interface OnDeviceCapability { suspend fun availability(): OnDeviceAvailability }` with `OnDeviceAvailability` leaves `Available`, `Downloadable`, `Downloading`, `Unavailable(code)` mirroring ML Kit's four statuses. v1.0 default returns `Unavailable("not_implemented")`. [CITED: .planning/research/ARCHITECTURE.md:444-452, 456]
- Wiring: `PipelineBuilder.onDevice` (public) feeds the existing internal `onDeviceAvailability` default. The router calls the **same instance** for selection-time checks. Only `Available` counts as usable in v1.0.
- The probe can now run twice per command (pre-check, then router). Treat each read as independent and cheap; do not cache across, but do not assume equality (a `Downloading` to `Available` flip between reads is legal).
- Fallback: `ProviderSelection.fallback: ProviderSelection?` (one level, no chains). The fallback is re-run through the policy gate (`offlineOnly` always rejects a cloud fallback; `allowedProviders` must contain it) and gets **its own** credential lookup keyed by the fallback provider. Record `fallbackFrom = ON_DEVICE` on the `TurnRecord`.
- No `UnavailableOnDeviceProvider` in `:providers` this phase (that module has no provider yet and L10 forbids implementation code); an unregistered ON_DEVICE provider is treated as `Unavailable("not_implemented")` by the router.

### Pattern 4: Cache detection after each response (D-08/D-10)

```kotlin
// Source: design derived from CONTEXT D-08..D-10; estimate math validated in "D-09 calibration" below
internal fun shouldFlagCacheMiss(
    directive: CacheDirective, caps: ModelCapabilities, usage: Usage, turnIndex: Int, prefixChars: Int,
): Boolean {
    val min = caps.minCacheablePrefixTokens ?: return false        // unknown minimum = silent
    if (!directive.staticPrefix) return false                      // directive's static prefix off = silent
    val promptTokens = usage.inputUncached + usage.cacheRead + usage.cacheWrite
    val estimated = minOf(prefixChars / caps.charsPerToken, promptTokens.toDouble()) // never above what the provider billed
    if (estimated < min) return false
    return when (caps.caching) {
        CachingMode.EXPLICIT_BREAKPOINTS -> usage.cacheRead == 0L && usage.cacheWrite == 0L   // any turn
        CachingMode.AUTOMATIC -> turnIndex >= 2 && usage.cacheRead == 0L                        // never turn 1
        else -> false                                                                           // NONE / unknown mode
    }
}
```

Call once per **successful** response, after `recordTurn`, and only then call `recorder.cacheNotEngaged(strategy, provider, model)`. Failed results carry no usage: skip.

### Anti-Patterns to Avoid

- **Re-reading selection per turn:** breaks the cache (model switch mid-loop) and PROV-03. Never call the seam outside the lazy resolution.
- **Returning the `Credential` to the strategy:** strategies must never see keys (ARCHITECTURE.md:643). The handle keeps it private.
- **Fallback in the router that ignores `offlineOnly`/`allowedProviders`:** PITFALLS #13; it would send an offline user's transcript to the cloud. The fallback path re-uses the same gate function as the primary path.
- **A second on-device probe API alongside the internal boolean:** one gate (CONTEXT). The internal property remains only as the seam existing tests assign.
- **Putting model ids anywhere in `src/main`:** including KDoc examples and comments (the scan test covers comments).
- **Using `recordCode` for the cache event:** emits an `EngineCode` event too, giving two events per detection and violating "do not add a second one".
- **A public `const val` for any default:** leaks a public static field and fails both `ApiShapeTest` and `review-api-surface.sh` (check e). Use a private top-level `const val` and read it through an internal function, as `ApplyStep.kt` does (STATE.md: "APPLY_ERROR_CONTENT is file-private ... read via internal fun").

## Recommended type inventory (who constructs what decides constructor visibility)

The Phase 2 constructor audit (`02-09-PLAN.md:54`) lists allowed public-constructor owners: engine-produced types must have non-public constructors. **Providers live in another module, so everything they build must be public.** The audit list must be extended in the Phase 3 gate plan for the rows marked "public ctor".

| Type | Kind | Constructed by | Constructor | Growth pattern |
|------|------|---------------|-------------|----------------|
| `Message` | `sealed class` (UserMessage, AssistantMessage, ToolResultsMessage) | strategies, providers | leaves public | closed by contract (ARCHITECTURE.md:493 lists it sealed) |
| `AssistantPart` | `sealed class` (Text, ToolCall) | providers | leaves public | closed by D-01 ("neutral parts only Text + ToolCall") |
| `ToolResult(callId, content, isError)` | regular class | strategies | public | `@JvmOverloads` |
| `NativeReplay(provider, model, raw: JsonElement)` | regular class | providers | public | redacted `toString`; plus `AssistantMessage.nativeFor(provider, model)` helper that returns `raw` only on an exact stamp match |
| `ModelRequest(model-less: system, tools, messages, toolChoice, maxTokens, cache)` | regular class | strategies | public, `@JvmOverloads` | `maxTokens` has **no default** (comes from `session.policy.maxTokensPerTurn`) |
| `ModelResponse(parts/message, stopReason, usage, native, requestId?)` | regular class | providers | public, `@JvmOverloads` | |
| `ModelResult` (+ `Success`, `Failure(reason, details)`) | open abstract class, internal ctor | providers | leaves public | open (CORE-07) |
| `StopReason` | `@JvmInline value class` with constants | providers (known constants; unknown maps to an `OTHER` constant; raw string stays inside `NativeReplay`) | public ctor validated by `isStableCode` | |
| `CacheDirective(staticPrefix, conversationTail = false)` | regular class | strategies | public | |
| `ProviderSelection(provider, model, fallback?)` | regular class | **app** | public, `@JvmOverloads` | `model` must be non-blank (D-04: no default model) |
| `SelectionRequest(strategy)` | regular class | engine | internal | |
| `CredentialLookup` leaves | open abstract + public leaves | app / `:keystore` | leaves public | |
| `OnDeviceAvailability` leaves | open abstract + public leaves | app / future module | leaves public | open: ML Kit could add statuses |
| `ModelCapabilities` | regular class, `Builder` | providers, app overrides | `internal constructor` + `Builder` (the `TierPolicy` pattern) | new knobs are new builder properties |
| `CachingMode` | `@JvmInline value class` (`EXPLICIT_BREAKPOINTS`, `AUTOMATIC`, `NONE`) | providers | internal ctor (like `BudgetBound`) | no enum (ApiShapeTest) |
| `ModelCapabilityTable` | regular class | app or pipeline | public lookup (PROV-08: public so pickers can filter) | |
| `BoundModel` / `ModelBinding` | abstract, internal ctor | engine only | not public-constructible | |

**Sealed allow-list:** `scripts/review-api-surface.sh` line 14 pins `ALLOWED_SEALED="StrategyOutcome CommandOutcome RunTermination GateDecision ToolStep"` and `--expect-sealed-complete` requires all five. Adding `Message` and `AssistantPart` as sealed requires editing that list to seven in the same plan (and its header comment); otherwise the Phase 2 gate fails. If the planner prefers zero gate edits, make both open abstract classes with internal constructors and have mappers carry an explicit `else -> error`/typed-malformed branch. **Recommendation: seal only these two** (two mappers do the `when`; a new message kind needs a contract amendment anyway) and keep every status vocabulary open.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Never-throw collapse around seams and the provider | try/catch in the router | `internal/Guarded.kt` `guarded(...)` | One justified `@Suppress`; correct cancellation and leaked-timeout semantics; detekt `TooGenericExceptionCaught` stays clean |
| Stable-code validation | a new regex | `failure/ReasonSupport.kt` `isStableCode` (internal) | Same rule as `ProviderUnavailable.cause`; keeps free text out of `toString` |
| Value equality / hash | `data class` | hand-written `equals`/`hashCode` with `mixHash` | `data class` is banned by `ApiShapeTest.noMainClassIsDataShaped` |
| Redacted `toString` | default `toString` | the Phase 2 pattern: ids, counts, class names (`CommandInput.kt:20-22`, `Credential.kt:16`) | TEL-04 canary will sweep these in Phase 4; do it now |
| Usage normalization | a new usage type | `telemetry/Usage` | Already encodes the Anthropic/OpenAI semantics and a saturated `total` |
| Token counting for the estimate | tokenizer dependency | char count with an overridable divisor, capped by the response's own prompt total | no allowed dependency; wrong for Claude anyway |
| Test event capture / clock / ordered scripts | new helpers | `RecordingEventListener`, `FakeClock`, `ScriptedResponses`, `NoNetworkGuard`, `ScriptedStrategy` (all in `core/testing`) | Phase 1-2 fixtures already do it |

**Key insight:** the engine's value is the invariants (zero calls on refusal, key-per-provider, frozen snapshot, silent-when-unsure), not the plumbing; every piece of plumbing already exists in Phase 2.

## Runtime State Inventory

Not a rename/refactor/migration phase. Omitted.

## Common Pitfalls

### Pitfall 1: A defaulted constructor parameter is a binary-breaking change under Metalava
**What goes wrong:** After the Phase 11 dump, `ToolSpec(name, description, schema, mutating = false, terminal = false, strict = null)` fails the gate. **Verified by experiment** in an isolated copy (baseline dumped, then `strict: Boolean? = null` appended):
`api.txt:889: error: Binary breaking change: Removed constructor io.github.ygaray.voiceactionengine.core.strategy.ToolSpec(String,String,kotlinx.serialization.json.JsonObject,boolean,boolean) [RemovedMethod]`. [VERIFIED: ran `:core:apiDump` then `:core:apiCheck` in a scratch copy of the tree, 2026-09-30]
**Why it happens:** Kotlin generates one real constructor with all parameters; adding one removes the old JVM signature.
**How to avoid (both verified green in the same experiment):**
1. `@JvmOverloads` on the primary constructor of every app-constructed growing class, then append new parameters **at the end** with defaults.
2. Or: `internal constructor` for the full shape + a public secondary constructor that keeps the old parameter list + `fun withStrict(value: Boolean?): ToolSpec` copy functions (the existing `StrategyOutcome.Completed` secondary-constructor style, `StrategyOutcome.kt:16-21`).
Do this **now** for `ToolSpec`: it is the only Phase 2 type being grown here, and it has not been dumped yet, so shape it correctly before Phase 11 freezes it. The ARCHITECTURE.md line "@JvmOverloads isn't needed since consumers are Kotlin" is wrong for the gate.
**Warning signs:** any new `= default` parameter in a public constructor or public function signature.

### Pitfall 2: Adding an abstract member to a public interface breaks implementers; adding a default member does not
**What goes wrong:** `AiProvider`, `ProviderSelectionSource`, `CredentialSource`, `OnDeviceCapability` are implemented by apps, `:providers` and `:keystore`. A new abstract method later breaks them all.
**Verified:** adding `public val label: String get() = "x"` to `CommandStrategy` passed `apiCheck` and compiled as `public default java.lang.String getLabel();` plus a `DefaultImpls` class (javap), so default members are real JVM default methods in this build. [VERIFIED: scratch-copy experiment + `javap`, 2026-09-30]
**How to avoid:** each seam is a `fun interface` with exactly one abstract method; every later capability is a default member. `CommandSession` is an abstract class with an internal constructor: add its Phase 3 member as `abstract` now (pre-tag, only the engine subclasses it), but any member added after the tag must be `open`.

### Pitfall 3: "Err silent" with a 3 chars/token divisor does the opposite for prose
See "D-09 calibration". The estimate is `chars / divisor`; a **small** divisor inflates the estimate and makes the detector fire more. 3.0 is the measured value for tool-schema JSON, so using it as the divisor errs toward firing on prose-heavy prefixes. Use a larger default and the prompt-token cap.

### Pitfall 4: The sealed allow-list and constructor audit are Phase 2 gates that Phase 3 will trip
Adding any sealed type, or any public-constructor type outside the audit list, fails `scripts/review-api-surface.sh --expect-sealed-complete` or the plan-level constructor audit. Update `ALLOWED_SEALED` (and the "expected" loop) and the allowed-owner list in the **same plan** that introduces the types, and re-run the script, rather than discovering it at the phase gate.

### Pitfall 5: `public const val` and companion constants leak static fields
`ApiShapeTest.noMainClassLeaksAPublicStaticFieldBesidesInstanceAndCompanion` and script check (e) fail on a public static field. Defaults (for example the estimator divisor, or a default cause code) must be `private const val` read through internal code, or companion `val`s (non-const). Also `ProviderId.Companion` must keep exactly four public getters (H2).

### Pitfall 6: A fallback cannot rescue an on-device-only tier
`Ladder.blockedOnDevice` fails an `onDeviceOnly` tier before its strategy runs (H5), so the router and `ProviderSelection.fallback` are never reached. An app that wants "try on-device, else Anthropic" must declare `StrategyCapabilities(setOf(ON_DEVICE, ANTHROPIC))` on the tier. Under `offlineOnly`, `PolicyPreCheck.permits` (`PolicyPreCheck.kt:75-83`) skips any tier that declares a cloud provider, so a fallback tier is never reached offline, which is the intended privacy behavior. Test and document all three cases. The README (Phase 10) must say so.

### Pitfall 7: Two probes per command can disagree
Pre-check probes once; the router probes again at selection time. Do not cache the first answer into the router and do not treat a mismatch as an error. Only `Available` is usable.

### Pitfall 8: Thinking blocks and verbatim replay are structural-JSON, not byte, equality
`NativeReplay.raw` is a `JsonElement`. kotlinx keeps object key order and numeric literal text, but re-encoding normalizes whitespace and string escapes. SB's own echo has the same property (it re-sends a parsed `JsonArray`), and Anthropic validates thinking blocks by content/signature, not whitespace. Phase 8 owns the golden; Phase 3 must guarantee only: stored by reference, never rebuilt, never mutated, same instance returned by `nativeFor` on an exact `(provider, model)` stamp, `null` otherwise. [ASSUMED: Anthropic validates by content not whitespace; Phase 8 should confirm with a real golden.]

### Pitfall 9: Latency needs a clock the handle does not have
`RunRecorder.clock` is private (`RunRecorder.kt:26`). The handle needs the pipeline clock to fill `TurnRecord.latencyMillis` deterministically under `FakeClock`. Add an internal `fun now(): Long` on `RunRecorder` (or pass `clock` to `RunSession`) rather than calling `System.nanoTime()` in the handle.

### Pitfall 10: EXPLICIT detection does not see "prefix drift" (a limitation of D-08/D-10 as written)
When a prefix drifts (PITFALLS #6 causes 1-4), Anthropic returns `cache_read = 0` and `cache_creation > 0` on turn 2+, which the locked EXPLICIT rule (`read + write == 0`) does **not** flag. Flagging it would also false-positive after the 5-minute TTL lapses. This is the right conservative trade for an "err silent" diagnostic and Gate-1 (Phase 10) asserts `cache_read > 0` separately, but the planner should record it as a known limitation (Open Question 3).

### Pitfall 11: OpenAI's "never reports writes" is stale for newer models
Current OpenAI docs report `cache_write_tokens` (GPT-5.6 and later). The D-10 AUTOMATIC rule (`turn >= 2 && cacheRead == 0`, ignoring writes) stays correct and conservative because a new tail write can coexist with a read miss; only the parenthetical rationale is stale. See D-09 section for sources.

## D-09 calibration (design inputs only; no value below may become a library constant tied to a model id)

### Measured: the A10 fixture

The fixture is the gitignored SB export (`sb-a10-fixture.json`, sha256 prefix `ebd3ef4a`, matching Phase 10's recorded hash prefix). It holds `{system, tools}`. Measured with Python on the local copy (no content copied or committed):

| Quantity | Value |
|----------|-------|
| `system` characters | 1,614 |
| tools | 18 |
| tools as compact JSON (`separators=(',',':')`) | 19,495 chars |
| system + compact tools | **21,109 chars** |
| SB-reported prefix tokens | 7,016 (REQUIREMENTS.md VER-02: "~7,016") |
| **chars per token** | **3.01** (3.14 using Python's default spaced JSON, 22,028 chars) |

[VERIFIED: computed this session from the local fixture file]. That the 7,016 figure was produced from exactly this serialization, on the pre-4.7 tokenizer behind Haiku 4.5, is [ASSUMED] (SB's log, not re-measured here). Note `system` here is a string and tools are measured as compact JSON; the wire adds field names (`name`, `description`, `input_schema`), so the real prompt is a bit larger than the chars-of-parts estimate, which also errs silent.

### Published ratios

- Rule of thumb ~4 chars/token for English prose; ~3 for code/JSON; CJK ~1.5. [CITED: widely repeated; secondary sources found by web search (e.g. https://llmtest.io/blog/tokens-words-characters, https://clauderules.net/tools/token-counter), LOW-MEDIUM]
- Newer Anthropic tokenizer (Opus 4.7 and later, including the 5.x family) produces about 1.0x to 1.35x as many tokens as the older one. [CITED: bundled Claude API skill text, "the Opus 4.7 tokenizer uses ~1×-1.35× as many tokens"; secondary corroboration in the web search above ("roughly 30 percent more")]. So tool-JSON ratios on current models plausibly fall to about 2.2 to 3.0.
- Do not use `tiktoken`-style counts for Claude (undercount about 15-20%). [CITED: Claude API skill `shared/token-counting.md`]

### What "erring silent" means for the math

`estimatedTokens = chars / divisor`. The detector fires when `estimatedTokens >= minimum`. Silent = **under**-estimating tokens = a **larger** divisor.

| Divisor | On the A10 fixture (21,109 chars, real 7,016 tokens) | On a 12,000-char English-prose prefix (real ~3,000 tokens) | Verdict |
|---------|------------------------------------------------------|-----------------------------------------------------------|---------|
| 3.0 | 7,036 (about right) | 4,000 (over by 33%: can fire below a 4,096 minimum with 13k chars) | errs toward **firing** on prose |
| **4.0 (recommended)** | 5,277 (under, still above 4,096 and every Anthropic minimum) | 3,000 (right) | errs **silent** on JSON/code, right on prose |
| 4.0 on newer tokenizer, JSON | up to ~40% under | | silent: misses true positives in the window just above a minimum, which is acceptable |

**Recommendation:**
1. Make the divisor a per-model field on `ModelCapabilities` (`charsPerToken`, builder-overridable), because tokenizers differ per model family; default **4.0** (a private const in the capabilities builder, documented as an estimate, not a limit).
2. Always cap: `estimated = min(chars / divisor, inputUncached + cacheRead + cacheWrite)`. When nothing was cached, `input_tokens` is the whole prompt (Anthropic: `total_input_tokens = cache_read + cache_creation + input_tokens`), and the prefix is a subset of the prompt, so the cap is a hard upper bound. [CITED: https://platform.claude.com/docs/en/build-with-claude/prompt-caching, usage-field section, fetched 2026-09-30]
3. Estimate chars as `system.length + sum(tool.name.length + tool.description.length + tool.inputSchema.toString().length)` (kotlinx `JsonObject.toString()` is compact). Ignoring wire framing under-counts, which errs silent.
4. Test margins: assert "fires above" and "silent below" with a 2x margin around a fake minimum, plus one A10-shaped case (about 21,000 chars of synthetic text/JSON against a 4,096-token minimum fires; 6,000 chars does not).

### Published minimum cacheable prefix lengths (design inputs; verified entries land in Phases 4-5, never as `:core` constants)

Anthropic (explicit `cache_control`; shorter prompts are processed without caching and **no error**, both cache fields are 0): [CITED: https://platform.claude.com/docs/en/build-with-claude/prompt-caching, fetched 2026-09-30; also bundled `shared/prompt-caching.md`]

| Minimum | Models (as listed by Anthropic) |
|---------|---------------------------------|
| 512 | Fable 5.1, Mythos 5.1, Opus 5.5, Opus 5, Sonnet 5.5, Fable 5, Mythos 5 |
| 1,024 | Opus 4.8, Sonnet 5, Sonnet 4.6, Sonnet 4.5, Opus 4.1, Opus 4, Sonnet 4 |
| 2,048 | Mythos Preview, Opus 4.7, Haiku 3.5 |
| 4,096 | Opus 4.6, Opus 4.5, Haiku 4.5 |

The minimum is **not monotonic** across generations. All of tools + system + messages up to the breakpoint count toward it. These apply on every platform. The bundled skill flags the Sonnet 5.5 value as "check the docs"; the live page lists 512. Cache hierarchy order is tools, then system, then messages.

OpenAI (automatic prefix caching): [CITED: https://developers.openai.com/api/docs/guides/prompt-caching, fetched 2026-09-30]
- Minimum **1,024** tokens (stated for GPT-5.6 and later as "1,024 visible input tokens"); earlier models round the cached portion down to a multiple of 128; GPT-5.6+ reports the exact boundary.
- Cache must match the **entire rendered prefix**, which includes developer/system messages, tool definitions and history.
- Usage: `cached_tokens` (and, on GPT-5.6+, `cache_write_tokens`) under `usage.input_tokens_details` (Responses) or `usage.prompt_tokens_details` (Chat Completions). [CITED: same page + search result summaries; the Chat Completions field names for `cache_write_tokens` are corroborated by secondary sources only, MEDIUM]
- Implicit mode places a breakpoint at the end of the latest eligible message, so a first request at or above the minimum can report a write on GPT-5.6+.
- Cache hits are best-effort routing-dependent; a diagnostic event, not a failure, is the right shape.

OpenRouter (Chat Completions, provider-dependent): [CITED: https://openrouter.ai/docs/guides/best-practices/prompt-caching, fetched 2026-09-30]
- OpenAI models: automatic, min 1,024. Anthropic Claude: **requires `cache_control`** (so uncached in v1.0, LATER-02), model-dependent 1,024-4,096 as quoted there. Gemini: implicit on 2.5+, 1,024-4,096 by model. DeepSeek/Z.AI: automatic, minimum not specified (so `minCacheablePrefixTokens = null` = silent).
- `cached_tokens` read from `prompt_tokens_details`; `cache_write_tokens` also reported.

**Consequence for the capability table:** `minCacheablePrefixTokens` is `Int?`; `null` means unknown and is always silent. Phase 3 ships it with fake entries (`FakeAiProvider` declares its own minimum); Phases 4-5 add verified numbers from the table above through the provider's default and the app override layer. OpenRouter normalization of `openai/...` ids belongs to Phase 5 D-02.

## Code Examples

### Evolving a public class additively (verified pattern)

```kotlin
// Source: scratch-copy experiments, 2026-09-30 (both variants passed :core:apiCheck against a dumped baseline)
public class ToolSpec @JvmOverloads constructor(
    public val name: String,
    public val description: String,
    public val inputSchema: JsonObject,
    public val mutating: Boolean = false,
    public val terminal: Boolean = false,
    public val strict: Boolean? = null,   // appended last; null = engine decides (PROV-12)
) { /* init, toString unchanged: print name and flags only */ }
```

### Redacted value type (house style)

```kotlin
// Source: core/Credential.kt:15-16 and core/CommandInput.kt:19-22 (house pattern)
public class NativeReplay(public val provider: ProviderId, public val model: String, public val raw: JsonElement) {
    override fun toString(): String = "NativeReplay(provider=$provider, model=$model)"   // never raw: it holds thinking text
}
```

### Test shape (house style)

```kotlin
// Source: pattern of core/src/test/.../TierPolicyTest.kt and EventsTest.kt
@Test
fun aMissingKeyIsNotConfiguredAfterZeroProviderCalls() = runTest {
    NoNetworkGuard.during {
        val fake = FakeAiProvider(ProviderId.ANTHROPIC, script = emptyList())
        val pipeline = commandPipeline {
            tier(ScriptedStrategy(StrategyId("a"), { _, session -> /* session.model().complete(...) */ StrategyOutcome.NoMatch() }))
            gate = ScriptedGate.admitAll(); commitSink = RecordingCommitSink()
            // providers/selection/credentials wiring per the final DSL
        }
        // assert FailureReason.NotConfigured(ProviderId.ANTHROPIC) and fake.callCount == 0
    }
}
```

## State of the Art

| Old Approach (in milestone research) | Current Approach (Phase 2 reality) | Impact |
|--------------------------------------|-----------------------------------|--------|
| `Usage(Int ...)`, `Credential(CharArray)`, package root `voiceaction` (ARCHITECTURE.md) | `Usage(Long ...)` in `telemetry`, `Credential(provider, apiKey: String)`, root `io.github.ygaray.voiceactionengine.core` | Ignore those ARCHITECTURE snippets |
| `ProviderSelectionSource.credential(provider)` in one interface | Separate `CredentialSource` with a typed result (D-02) | Two seams |
| `Escalate(ProviderUnavailable(ON_DEVICE))` when no fallback | `Failed(ProviderUnavailable(ON_DEVICE, code))` (D-07, orchestrator) | Loud, never a climb |
| `OnDeviceRuntime` | `OnDeviceCapability` (CONTEXT Runtime Decisions) | Naming |
| `testing/` inside `:core` main | `core/src/testFixtures/.../core/testing/` (`java-test-fixtures`, excluded from publication by `verifyNoTestFixturesPublished`) | `FakeAiProvider` goes there |
| OpenAI "never reports cache writes" | GPT-5.6+ reports `cache_write_tokens` | D-10 rule still safe |
| `@JvmOverloads` "unneeded" | Needed (or the secondary-ctor pattern) for the Metalava gate | Pitfall 1 |

**Deprecated/outdated:** none of the Kotlin/Gradle toolchain choices; they are pinned by Phase 1.

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | Names such as `CredentialUnreadable`, `CredentialLookup`, `ModelBinding`, `OnDeviceCapability`, `charsPerToken` are recommendations, not locked | Patterns 1-3, inventory | Cosmetic; planner renames |
| A2 | The 7,016-token figure was produced from exactly the fixture serialization measured (compact JSON on the pre-4.7 tokenizer) | D-09 calibration | The measured 3.01 ratio could shift (±20%); the recommended 4.0 divisor plus the prompt-token cap still errs silent |
| A3 | Anthropic validates replayed thinking blocks by content/signature, not whitespace, so structural JSON equality suffices | Pitfall 8 | Phase 8 golden would catch it; Phase 3 types unaffected |
| A4 | Seal only `Message` and `AssistantPart`; all status vocabularies stay open | Type inventory | If the orchestrator wants a different sealed set, only the allow-list edit changes |
| A5 | A throwing `CredentialSource` maps to `Unreadable("source_error")`; a throwing selection source maps to `Unexpected(errorClass)` with a trace code | Pattern 1 | Different reason leaf; low impact |
| A6 | Chat Completions reports `cache_write_tokens` under `prompt_tokens_details` on GPT-5.6+ (docs page names it under `input_tokens_details`; Chat Completions form from secondary sources) | D-09 calibration | Phase 5 mapper reads the wrong key; Phase 3 unaffected |
| A7 | Router failure cause for unavailable on-device is the same `"on_device_unavailable"` as Phase 2 (vs. the gate's own code) | Open Question 2 | UI distinguishes fewer cases |

## Open Questions

1. **Sealed set and the Phase 2 gate scripts.**
   - What we know: `review-api-surface.sh` pins five sealed types; Message/AssistantPart are listed sealed in milestone research.
   - What's unclear: whether the orchestrator accepts widening the allow-list (it is gate tooling, not a contract §).
   - Recommendation: widen to seven in the same plan, or make both open. Decide at plan time; do not leave it to the phase gate.
2. **Failure cause code for an unavailable on-device gate at the router.**
   - What we know: the pre-check path emits `"on_device_unavailable"` (pinned by Phase 2 tests); the gate's own code would be `"not_implemented"`.
   - What's unclear: whether apps want to see the gate's code in the failure.
   - Recommendation: use `"on_device_unavailable"` on both paths for one consumer-visible code, and expose the gate's detail through the public `OnDeviceCapability` result and the trace. Revisit only if Phase 10 docs need it.
3. **Should EXPLICIT also flag "turn >= 2, read == 0, write > 0" (prefix drift)?**
   - What we know: D-10 as locked does not; it would false-positive after the 5-minute TTL.
   - Recommendation: implement D-10 as locked; record the limitation in the plan and rely on Gate-1's `cache_read > 0` assertion. Raise with the orchestrator only if desired.
4. **Estimator divisor default (4.0) differs from the CONTEXT hint "≈ 3".**
   - Why: 3.0 is the *measured* tool-JSON ratio, but "erring silent" requires the larger divisor (see Pitfall 3). Within Claude's discretion ("calibrate via research"). Recommend 4.0 plus the prompt-token cap; surface in the plan so the orchestrator can object.
5. **`ToolSpec` "cache" member.** CONTEXT lists "strict/cache members". A per-tool cache breakpoint is not used in v1.0 (A10 puts one breakpoint on the last system block; tools are cached implicitly by prefix). Recommendation: add `strict` only; add a cache member only if a concrete mapper need appears (Phase 4), via the verified additive pattern.

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| JDK | Gradle/Kotlin build | yes | OpenJDK 17.0.19 | — |
| Gradle wrapper | build/test | yes | 9.4.1 (wrapper; ran offline `--offline` in scratch copy) | — |
| Network | none (pure JVM, `NoNetworkGuard`) | not needed | — | — |
| Real provider keys / device | none this phase | not needed | — | — |

Baseline confirmed this session in a scratch copy of the tree: `./gradlew :core:test :core:detekt :core:scanBannedConstructs` green, **233 tests, 0 failures, 33 result files**. Commands return in seconds when the build cache is warm.

**Missing dependencies:** none.

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit 4.13.2 + kotlinx-coroutines-test 1.11.0 (`runTest`) |
| Config file | `core/build.gradle.kts` (no separate test config); detekt `config/detekt/detekt.yml` |
| Quick run command | `./gradlew :core:test --tests '*ProviderRouterTest'` (swap the class) |
| Full suite command | `./gradlew check` (detekt, scanner, all modules) |
| Surface gate | `scripts/review-api-surface.sh --expect-sealed-complete` (after the allow-list edit) |
| Regression gates | `scripts/verify-negative-controls.sh && scripts/verify-repo-hygiene.sh && scripts/verify-api-dump.sh` |

### Phase Requirements to Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| PROV-01 | Single-shot (1 user message) and multi-turn tool conversation (assistant with parallel calls, results message) are expressible; defensive list copies; `toString` leaks no text/args | unit | `./gradlew :core:test --tests '*TranscriptTypesTest'` | no, Wave 0 |
| PROV-01 | `nativeFor` returns the same `raw` instance only for an exact `(provider, model)` stamp; null otherwise; `NativeReplay.toString` has no raw | unit | `... --tests '*NativeReplayTest'` | no, Wave 0 |
| PROV-01 | `:core` still has no HTTP dependency | existing | `./gradlew :core:check` (classpath allow-list + `NoNetworkGuard.assertNoHttpStackOnClasspath`) | yes |
| PROV-02 | Selection asked once per tier-run regardless of turns; second command re-asks (switch takes effect next command); mid-command change ignored | unit | `... --tests '*ProviderRouterTest'` | no, Wave 0 |
| PROV-02 | Null selection, `Missing` key, throwing sources: `NotConfigured`/`CredentialUnreadable`/trace code, **fake call count 0** | unit | same | no, Wave 0 |
| PROV-02 | Key never crosses providers: spy `CredentialSource` sees only the selected provider id; a hostile source returning another provider's credential is refused; fallback fetches the fallback provider's key only | unit | `... --tests '*KeyIsolationTest'` | no, Wave 0 |
| PROV-03 | Two tiers: each resolves once; a one-tier N-turn loop resolves once | unit | `ProviderRouterTest` | no, Wave 0 |
| PROV-10 | Unavailable gate, no fallback: `Failed(ProviderUnavailable(ON_DEVICE, code))`, zero calls, no key requested; with fallback: fallback provider used, its key only, `fallbackFrom` in `TierAttempt`; `offlineOnly`/`allowedProviders` reject the fallback; on-device-only tier unchanged (Phase 2 behavior); pre-check and router consult the **same** gate instance (counting probe) | unit | `... --tests '*OnDeviceGateTest'` | no, Wave 0 |
| PROV-10 | Probe fault stays `ON_DEVICE_PROBE_ERROR` (Phase 2) | existing | `... --tests '*TierPolicyTest'` | yes |
| D-05 | Lookup order: app override over provider default over unknown-id default; unknown minimum is `null`; request needing tools on a tools-unsupported model is `ModelUnsupported` with zero calls | unit | `... --tests '*ModelCapabilityTableTest'` | no, Wave 0 |
| TEL-03 | Matrix: EXPLICIT zero/zero above min fires exactly once; below min silent; unknown min silent; directive off silent; mode NONE silent; read>0 silent; write>0 silent; AUTOMATIC turn 1 silent, turn 2 read==0 fires, turn 2 read>0 silent; prompt-token cap makes it silent when total prompt < min; A10-shaped synthetic prefix (about 21k chars vs 4,096) fires; event carries strategy/provider/model; one event, no `EngineCode` | unit | `... --tests '*CacheNotEngagedTest'` | no, Wave 0 |
| TEL-03 | Existing hook test still passes | existing | `... --tests '*EventsTest'` | yes |
| CLN-03 | No model-id literal (`claude-`, `gpt-`, `gemini`, `o1-`, `o3-`, vendor-prefixed ids) in `src/main` including comments; default limit literals (`6`, `60_000`, `4_096`) only in `TierPolicy.kt` | unit (source scan, same style as `ApiShapeTest` reading `src/main/kotlin`) | `... --tests '*NoHardCodedConstantsTest'` | no, Wave 0 |
| CLN-04 | No `java.io.File`/`System.getenv`/`System.getProperty`/preference-style reads in `src/main` | unit (source scan) | same | no, Wave 0 |
| Additive API | `ToolSpec` 5-arg constructor still resolvable by reflection after the `strict` addition; no enum/data-shaped/static-field leak; new sealed set matches the script | existing + script | `ApiShapeTest`, `scripts/review-api-surface.sh --expect-sealed-complete` | yes (extend) |
| Redaction | Canary sweep over transcript types, `NativeReplay`, `ProviderSelection`, `CredentialLookup`, `BoundModel`, new failure leaf | unit | extend `RedactionCanaryTest` | yes (extend) |
| Harness | `FakeAiProvider` scripted turns, call counter, recorded requests and credentials, throws-on-exhausted | unit | `... --tests '*FakeAiProviderTest'` | no, Wave 0 |

### Sampling Rate

- **Per task commit:** `./gradlew :core:test --tests '*<ClassJustTouched>'` plus `:core:detekt`
- **Per wave merge:** `./gradlew :core:check`
- **Phase gate:** full `./gradlew check`, `scripts/review-api-surface.sh --expect-sealed-complete`, the three Phase 1 regression scripts, and the constructor audit extended for the new public-constructor owners

### Wave 0 Gaps

- [ ] `core/src/testFixtures/.../testing/FakeAiProvider.kt` (+ `ScriptedCredentialSource`, `ScriptedSelectionSource`) — covers ROADMAP SC5 harness; must be public with KDoc (explicitApi) and fail loudly on an exhausted script (like `ScriptedResponses`)
- [ ] Test classes listed above (none exist yet)
- [ ] Edit `scripts/review-api-surface.sh` `ALLOWED_SEALED` if `Message`/`AssistantPart` are sealed (header comment too)
- No framework install needed.

## Security Domain

`security_enforcement` is enabled (absent in `.planning/config.json` workflow keys it is `true`: `"security_enforcement": true`, ASVS level 1, block on high). [VERIFIED: .planning/config.json read this session]

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|------------------|
| V2 Authentication | no (no user auth in the library) | — |
| V3 Session Management | no | — |
| V4 Access Control | yes (policy gate) | `offlineOnly` / `allowedProviders` enforced on the dynamic selection **and** the fallback, not only on static tier capabilities |
| V5 Input Validation | yes | `require(...)` in constructors (non-blank model, stable codes via `isStableCode`, non-negative `Usage`); seam results validated (credential provider match) |
| V6 Cryptography | no (Keystore is Phase 6) | never hand-roll; not in scope here |
| V7 Error handling & logging | yes | typed failures only; no free-text causes; `toString` redaction on every new type; no logging sink (banned imports) |
| V8 Data protection | yes | `Credential` kept private inside the frozen handle; transcripts/tool args/`NativeReplay.raw` never printed |
| V14 Configuration | yes | no settings reads in the library (CLN-04, scan test) |

### Known Threat Patterns for this stack

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| One provider's key sent to another (fallback or buggy source) | Information disclosure | Credential requested by the selected provider id only; provider-match check; fallback gets its own lookup; spy-based tests |
| Offline-only user's transcript sent to a cloud fallback | Information disclosure | Fallback re-checked against `offlineOnly`/`allowedProviders`; static pre-check already skips cloud-declaring tiers under `offlineOnly` |
| Secret/transcript in `toString`, trace, event or failure | Information disclosure | Redacted `toString`, stable-code causes, `RedactionCanaryTest` extension |
| Mid-command model/provider switch silently changing behavior (and cache) | Tampering | Frozen handle (D-03) |
| Throwing app seam crashing the pipeline | Denial of service | `guarded` around every seam; cancellation still propagates |
| Misconfigured hostile `CredentialSource` returns a different provider's key | Spoofing | Mismatch refusal (above) |

## Sources

### Primary (HIGH confidence)
- In-repo code read this session: `core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/{Credential,ProviderId,StrategyId,CommandInput}.kt`, `pipeline/{PipelineBuilder,PolicyPreCheck,TierPolicy,TierPolicySource,TierSelector,TierWalk,RunSession,CommandPipeline,CommandOutcome}.kt`, `strategy/{ToolSpec,CommandStrategy,CommandSession,StrategyCapabilities,StrategyOutcome,TerminalCall}.kt`, `failure/{FailureReason,FailureDetails,ReasonSupport,BudgetBound,EscalationReason}.kt`, `telemetry/{PipelineEvent,RunRecorder,CommandTrace,Usage,TraceCode,TurnRecord,EventDispatch}.kt`, `internal/Guarded.kt`, `commit/{ActionKind,ToolStep}.kt`; test fixtures (`ScriptedResponses`, `ScriptedStrategy`, `NoNetworkGuard`, `RecordingEventListener`, `FakeClock`); tests `ApiShapeTest`, `TierPolicyTest`, `RedactionCanaryTest`, `EventsTest`; `core/build.gradle.kts`, `config/detekt/detekt.yml`, `gradle/invariants.gradle.kts`, `gradle/libs.versions.toml`, `scripts/review-api-surface.sh`, `scripts/verify-api-dump.sh`
- `.planning/` : CONTEXT, ROADMAP, REQUIREMENTS, STATE, v1.0-DECISION-MAP (Phase 3), research/{ARCHITECTURE,PITFALLS,SUMMARY,FEATURES}.md, Phase 2 `02-09-SUMMARY.md`, `02-PATTERNS.md`, `02-09-PLAN.md`
- Experiments (scratch copy of the tree, offline Gradle): Metalava baseline dump, then (a) defaulted ctor param fails with `[RemovedMethod]`, (b) `@JvmOverloads` passes, (c) internal-primary + public secondary + `withStrict` passes, (d) default interface member passes and `javap` shows `public default` method; baseline `:core:test :core:detekt :core:scanBannedConstructs` = 233 tests green
- Local fixture measurement (A10): 1,614 + 19,495 = 21,109 chars, 18 tools

### Secondary (MEDIUM confidence)
- https://platform.claude.com/docs/en/build-with-claude/prompt-caching (fetched 2026-09-30): minimum cacheable lengths, no-error-below-minimum, usage field semantics, tools/system/messages order, 20-block lookback; corroborated by the bundled Claude API skill `shared/prompt-caching.md` (cached 2026-09-25)
- https://developers.openai.com/api/docs/guides/prompt-caching (fetched 2026-09-30): 1,024 minimum, 128-token rounding on earlier models, exact boundary and write reporting on GPT-5.6+, full-rendered-prefix matching
- https://openrouter.ai/docs/guides/best-practices/prompt-caching (fetched 2026-09-30): per-provider automatic vs `cache_control`, minimums

### Tertiary (LOW confidence)
- Chars-per-token rules of thumb (secondary blogs found via web search: llmtest.io, clauderules.net); newer-tokenizer inflation (bundled skill text + a blog); Chat Completions `cache_write_tokens` key name (search-result summaries). Validate in Phases 4-5 with real responses.

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH, no new dependencies; all versions read from the catalog.
- Architecture / hooks: HIGH, every hook read in source this session; the router design itself is a recommendation (MEDIUM).
- Metalava evolution rules: HIGH, reproduced experimentally.
- Cache minimums: MEDIUM, official docs fetched today but they change per release.
- D-09 ratio: MEDIUM, one real fixture; direction of "err silent" reasoned from the math.
- Pitfalls: HIGH for the Phase 2 gate interactions (sealed list, constructor audit, static-field leaks), MEDIUM for provider-behavior items.

**Research date:** 2026-09-30
**Valid until:** 2026-10-14 for the cache-minimum tables and OpenAI field names (fast-moving); the in-repo hook inventory is valid until Phase 3 execution changes those files.
