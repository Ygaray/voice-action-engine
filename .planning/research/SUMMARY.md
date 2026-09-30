# Project Research Summary

**Project:** voice-action-engine
**Milestone:** v1.0
**Domain:** Android/Kotlin JitPack library. A multi-provider LLM "voice command → app action" engine (tier ladder, forced-tool SingleShot, provider-neutral agentic loop, confirm/commit/undo, BYO-key)
**Researched:** 2026-09-29
**Confidence:** HIGH for stack parity, module boundaries, seams and port behavior (read directly from SB/CT/sibling-hub sources and registries). MEDIUM for OpenAI/OpenRouter wire details and JitPack inter-module publishing. Those still have to be proven live.

> Authority: `CROSS-REPO-SCOPE-CONTRACT.md` §6.2 + §10 (A1–A14, errata E1–E6) wins over any research file. Coordinates are **settled by E5**: per-module `com.github.Ygaray.voice-action-engine:<artifactId>`. The A10 fixture is **kept out of git (LE-7)**.

## Executive Summary

This is a small, opinionated library with exactly two known Wave-1 consumers, SecondBrain (SB) and CalTracker (CT). It isn't a general LLM framework, and nearly every hard feature already exists in one of the two apps: SB's bounded Anthropic agentic loop with prompt caching and a suspend-until-tap confirm gate, and CT's 3-provider forced-tool extraction with deferred "Proposed" confirmation. The job is to lift both into one provider-neutral engine without losing their disciplines: never-throw typed outcomes, cancellation always propagating, keys never reaching any sink, verified undo, and a byte-stable cache prefix. None of the surveyed frameworks (LangChain4j, Koog, Spring AI, Vercel AI SDK, the vendor SDK runners) has the combination this engine needs: a cheap-first tier ladder, defer-mode confirmation, verified-undo commit sink, and typed never-thrown failures. So the design is ported, not copied.

Recommended approach:
- **Modules.** Four modules: `:core` and `:providers` as **pure Kotlin/JVM** modules, `:keystore` as the only Android library, and `:sample` as an unpublished debug app.
- **Transport.** Raw OkHttp plus kotlinx.serialization `JsonObject`. No vendor SDKs, because they drag Jackson and kotlin-reflect onto consumer APKs.
- **Versions.** Match consumers exactly: Kotlin 2.3.20, AGP 9.2.1, Gradle 9.4.1, **JVM 11 bytecode**, OkHttp **4.12.0 compile floor**.
- **Keystone design.** One engine-owned write path, "prepare → gate → commit" (`ToolStep` → `CommitCoordinator` → `PreApplyGate` → `CommitSink`). It serves both SB's suspending gate and CT's deferred gate.
- **Transcript.** A neutral transcript model whose assistant turns carry a **verbatim provider-native replay blob**. This keeps thinking blocks and the cache intact.

The main risks all fail silently:
1. **Silent cache miss.** Turn-2 `cache_read = 0` from prefix drift, a below-minimum prefix, or a model switch mid-loop. This kills the A10 Gate-1.
2. **Stale forced-tool assumptions.** Opus 5.5, Sonnet 5.5, Fable 5.1 and Mythos 5.1 return 400 on forced `tool_choice`. CT's guard list is already missing `claude-sonnet-5-5`.
3. **Lossy neutral transcript.** Dropping thinking blocks causes 400s on current Claude models.
4. **A1 matrix that proves nothing.** Recompiling the 5.x leg, or letting it silently run 4.12 again, gives a false green.
5. **Unproven JitPack publishing.** This is the first repo in the ecosystem with published inter-module dependencies.
6. **Sealed/enum growth after the tag.** It breaks consumers' exhaustive `when` even though the ABI check passes.

Every one of these has a concrete mechanical guard. The roadmap should front-load the build and publishing proof in step 1 and the outcome/gate type design in step 2, because both harden into the immutable, strictly additive `v1.0.0` API.

## Key Findings

### Recommended Stack

Match the consumers and don't chase the latest versions. SB, CT and YAT build today on Kotlin 2.3.20 / AGP 9.2.1 / Gradle 9.4.1 / coroutines 1.11.0 / serialization 1.11.0 / DataStore 1.2.1, so the library forces zero upgrades on them. Upstream is at Kotlin 2.4.20, AGP 9.4.1 and Gradle 9.8.0, but nothing in v1.0 needs those.

**Core technologies:**
- **Kotlin 2.3.20 + AGP 9.2.1 (built-in Kotlin; never apply `kotlin.android`) + Gradle 9.4.1, JDK 17 build:** exact consumer parity. YAT proves the AGP 9.2.1 + JitPack `openjdk17` combination.
- **JVM 11 bytecode on all published modules:** SB and YAT compile at 11. A JVM-17 library that exposes a public `inline`/DSL function (`commandPipeline { }`) fails to compile in SB.
- **OkHttp 4.12.0 as a plain `api` requirement** (no `strictly`, no BOM), tested at runtime against 4.12.0 / 5.2.1 / 5.5.0: this is A1. Consumers' 5.2.1 wins normal conflict resolution.
- **kotlinx.serialization-json 1.11.0 `JsonObject`:** used for tool schemas, arguments and wire bodies. Both ports already use it. It's an `api` dependency of `:core`, which is an accepted coupling.
- **kotlinx-coroutines-core 1.11.0** only (never `-android`) in `:core`/`:providers`.
- **AndroidKeyStore AES/GCM + DataStore 1.2.1** in `:keystore`. Not `EncryptedSharedPreferences`, which is deprecated.
- **Tooling:**
  - detekt 1.23.8, **syntax-only** (plain `detekt`, never `detektMain`), `buildUponDefaultConfig = true`, `maxIssues: 0`, **no baseline file**.
  - Metalava 0.5.1 on all three published modules.
  - `explicitApi()` everywhere.
  - Legacy `okhttp3.mockwebserver` + JUnit 4.13.2.
  - Hand-written fakes. No MockK, no Robolectric, no Turbine.

### Expected Features

**Must have (table stakes; a Wave-1 consumer can't migrate without these):**
- Pipeline spine: `CommandInput` → `CommandPipeline` → `StrategyOutcome` (Completed / Escalate(reason, carry?) / NoMatch / Failed). Also `TierSelector.Linear` (+`Fixed`) and `TierPolicy` (offlineOnly, maxTier, allowedProviders, iteration/token ceilings as **policy defaults**: 6 / 60k / 4096).
- Never-throw collapse with cancellation always rethrown. Committed work is reported on **every** outcome variant.
- Typed `FailureReason` at SB granularity or finer: auth / billing / rate-limit / overloaded / timeout / network / malformed / malformed-args / refusal / max-tokens / no-tool-call / budget / tool-failure / not-configured / provider-unavailable. It must also handle OpenRouter's errors that arrive with HTTP 200.
- `PreApplyGate` with **both** suspend mode (SB) and defer mode (CT, with an amended batch), plus `CommitSink` with verified undo. Held never counts as success. Fail-closed. The held `tool_result` must stay byte-compatible with SB (`{"applied":false,"status":"held_for_confirmation"}`).
- Two-phase `ToolExecutor` (`prepare` → `Finished | Mutation`) so the gate is engine-enforced inside the loop.
- Neutral `ToolSpec` with per-dialect encoders, OpenAI strict-mode keyword stripping, and deterministic name-sorted byte-identical serialization.
- Forced tool choice **with a reactive fallback**: on the specific 400, retry once with `auto` + `strict` + an instruction, then treat "no call" as `NoToolCall`.
- Anthropic/OpenAI/OpenRouter transports:
  - Caching as a provider capability. Anthropic gets one `cache_control` on the last system block (A10 parity). OpenAI caches automatically. OpenRouter passes it through.
  - Usage normalized to `{inputUncached, cacheRead, cacheWrite, output}`.
  - OpenRouter uses `provider.require_parameters: true` when forcing a tool.
- A neutral multi-turn transcript with verbatim raw replay, plus SB's loop guards: whole-turn validation, final-iteration guard, token ceiling, 2-strike tool-failure abort, unknown tool → `is_error`.
- A per-call provider/model/key seam. `NotConfigured` short-circuits before any network call. A provider's key is never substituted for another's. `ON_DEVICE` slot + capability gate.
- Trace on every result, plus an optional typed callback, with no secrets. `:keystore` with the typed `NotConfigured / Ready / KeyMissing / Unreadable` states.

**Should have (cheap enough to include in v1.0):**
- Transient retry at `maxRetries = 1`, for HTTP only and never tool execution.
- **Commit-time `CommitSink` notification.** This closes a real SB hole: undo is currently lost on cancel after a commit.
- A `CacheNotEngaged` diagnostic event.
- `requestId` on failures.
- An idempotent confirmation handle.

**Defer:**
- v1.x: a moving/top-level cache breakpoint, OpenAI `prompt_cache_key`, OpenRouter `anthropic/*` cache_control, model catalog (it stays in CT), and a schema-builder DSL.
- v2+: streaming, the Nano/AICore implementation, and more providers.
- Explicit anti-features: streaming, parallel tool execution, dollar-cost tables (ceilings are in tokens), reflection `@Tool`, cross-command memory, auto cross-provider failover, DI integration, library UI, logging interceptors, and retrying tool execution.

### Architecture Approach

Dependencies point one way: `:sample → {:providers, :keystore} → :core`. `:core` has no HTTP, no Android and no other hub. It holds the pipeline, both strategies, the neutral transcript, the router, the commit coordinator, telemetry and a small `testing/` package (`FakeAiProvider`, recording sinks) so SB and CT can test their own wiring. `:providers` holds the internal wire mappers behind a `WireDialect` seam. OpenAI **and** OpenRouter both use Chat Completions in v1.0, with the Responses API as a later additive option. The app supplies everything domain-specific through seams, bound by plain constructors and DSL, with no DI.

**Major components:**
1. **`CommandPipeline`** (core): per-call policy read, tier walk, escalation rules, and the **no-escalation-after-commit invariant**. Everything collapses to a typed outcome plus `CommandTrace`.
2. **`SingleShotStrategy` / `AgenticLoopStrategy`** (core): talk only to `AiProvider` through the router, and never write directly.
3. **`CommitCoordinator`** (core): the only write path, proposal → `PreApplyGate.admit` → `CommitSink.commit`, or a `HeldProposal`. It ships an `AwaitingConfirmGate` helper that ports SB's `VoiceConfirmGate` (mutex, 120 s, fail-closed).
4. **`ProviderRouter`** (core): per-call `ProviderSelectionSource.select()` → `credential(thatProvider)` → capability check → ON_DEVICE availability → declared fallback only.
5. **`AnthropicProvider` / `ChatCompletionsProvider.openAi()/.openRouter()`** (providers): clean client derived via `newBuilder()` with interceptors stripped, cancellation-safe `Call.await()`, HTTP → `FailureReason`, usage normalization, `NativeReplay`.
6. **`:keystore`**: `ApiKeyStore` over an **app-supplied explicit `KeySlot` table and the app's existing `DataStore`**, plus a `KeystoreCredentialSource` adapter.

Public API rules:
- Growing types are regular classes, not `data class`.
- Sealed is used only for contract-closed shapes (`StrategyOutcome`, `GateDecision`, `Message`, `ToolStep`).
- Growing taxonomies (`FailureReason`, `HoldReason`, `PipelineEvent`, `StopReason.Other`) are open or have an `Other` leaf.
- `ProviderId` is a value class with constants (see the open questions).

### Critical Pitfalls

1. **Silent cache invalidation (A10 Gate-1 false fail).**
   - Freeze tools and system once per command, in an ordered list.
   - Keep date, language and transcript only in the first user message.
   - Snapshot provider/model/key/effort **per command, not per iteration**.
   - Add a byte-identity unit test.
   - At Gate-1, assert turn-1 creation > 0 and turn-2 read > 0 with a prefix above the model minimum (Haiku 4.5 = 4,096).
   - Keep confirm gates in canned-admit mode, since the 5-minute TTL would otherwise expire.
2. **Forced tool_choice 400 on current Claude models.** Use a per-model capability table that apps can override, a reactive 400 → auto+strict retry, and "no tool call" → `Escalate(NoToolCall)`. Never port CT's `AnthropicKnownTool400Ids` verbatim.
3. **Lossy neutral transcript.** Replay `NativeReplay` verbatim for the same provider and model. Transcripts never cross providers, so `carry` is semantic only. Anthropic batches tool results in one user message; OpenAI sends one `role:tool` message per id. Add golden fixtures that include thinking blocks.
4. **Fake A1 matrix.**
   - Compile once against 4.12, then run the same test classes on 5.2.1 and 5.5.0 runtime classpaths.
   - Move okhttp and mockwebserver together.
   - Add a reflective `OkHttp.VERSION` guard test.
   - Use detekt `ForbiddenImport` for `okhttp3.internal.*` / `mockwebserver3.*`.
   - Always write `body?.string()`.
5. **Mutation correctness.**
   - A tier that committed can't escalate. It becomes terminal: `Completed` or `Failed` with its commits.
   - Held actions are first-class in the outcome.
   - One gate contract with two call sites, marked so nothing gets asked twice.
   - Validate `maxIterations >= 2`.
   - Ban `runCatching`. Rethrow `CancellationException` before any broad catch. Engine timeouts use `withTimeoutOrNull` → `TIMEOUT` (distinct from `NETWORK`). Close any `Response` delivered after cancel.
6. **Keystore stranding and secret leaks.**
   - No alias formula. SB and CT schemas are asymmetric, so slots come from an explicit table.
   - Never create a second DataStore on the app's file.
   - The decrypt path never creates keys (`KeyMissing`, not "corrupt").
   - Use `java.util.Base64` (NO_WRAP-compatible).
   - Add a reflective canary test proving that no key, transcript, argument or tool_result appears in the trace, events, `toString()` or failures.
   - Failures carry status and `error.type` only, never the body.

## Conflicts Reconciled

| Topic | Positions | Decision | Why |
|---|---|---|---|
| **AGP version** | ARCHITECTURE cites backup-engine's AGP 8.13 (and "match backup-engine" Kotlin). STACK says AGP 9.2.1. | **AGP 9.2.1 / Gradle 9.4.1** | Consumers (SB, CT, YAT) are on 9.2.1. AGP only affects `:keystore`/`:sample`, and matching the consumers is what matters. backup-engine is the *publishing* reference, not the version reference. YAT proves 9.2.1 on JitPack. |
| **Bytecode target** | PITFALLS 3 says JVM 17 (backup-engine style). STACK says JVM 11. | **JVM 11** | SB/YAT compile at 11, and the engine exposes a DSL/inline surface. JDK 17 still *runs* the build. No toolchain auto-provisioning. |
| **API-compat tool** | ARCHITECTURE: KGP `abiValidation` (+BCV fallback). PITFALLS 12: BCV/abiValidation for JVM + Metalava for `:keystore`. STACK: Metalava on all three. | **Metalava 0.5.1 on all three modules** | One tool, and YAT-proven on AGP 9. Its `--check-compatibility:api:released` allows additions and fails breaks, while BCV fails on *any* diff. KGP validation doesn't support Android library modules. Commit `api.txt` at the `v1.0.0` cut. Verify Metalava on JVM modules in step 1. |
| **JitPack install task** | ARCHITECTURE: `publishToMavenLocal`. STACK: `publishReleasePublicationToMavenLocal`. | **Name every publication `release`** (JVM: `from(components["java"])`; AAR: `singleVariant("release")`), with an explicit per-module install list | Uniform task names, and `:sample` is never in the list. |
| **Coordinates** | STACK F1 / PITFALLS 1 flagged the aggregator trap. | **Settled by E5**: `com.github.Ygaray.voice-action-engine:voice-action-engine-{core,providers,keystore}` | Set group/artifact/version explicitly at build time (`VERSION` env). Prove inter-module POM resolution by **commit SHA** from a clean cache, not with a throwaway tag. STACK prefers SHA; PITFALLS suggested a probe tag. PROJECT.md mentions a probe tag, and a SHA avoids burning an immutable tag. |
| **A10 fixture in git** | PITFALLS 2 says commit it. | **Out of git (LE-7)** at a gitignored `:sample` path | SB is private and this repo is public. PITFALLS 2 still applies, though: the absence check must be **lazy** (a debug build task or runtime error), **never at Gradle configuration time**, because JitPack configures `:sample`. |
| **OkHttp matrix mechanism** | ARCHITECTURE/PITFALLS: `-P` property switch. STACK: in-build `Test` tasks under `check`. | **In-build legs** (fallback: the `-P` switch + script) | There's no GitHub CI in this ecosystem. `./gradlew check` is the gate, so the matrix must live inside it. The guard test is mandatory either way. |
| **Where transcript types land** | ARCHITECTURE: the full multi-turn types in 3a. PITFALLS: the raw-replay decision in 6a. | **Types, including `NativeReplay`, in 3a; mappers and multi-turn conformance in 6a** | SingleShot is a 1-message transcript, so single-turn-only types would force a redesign at 6a. |
| **Package root** | STACK: `io.github.ygaray.voiceactionengine`. ARCHITECTURE: `io.github.ygaray.voiceaction`. | Recommend **`io.github.ygaray.voiceactionengine.*`** | Mirrors the artifact names. Decide in step 1, because changing it later is breaking. |
| **Tool count** | FEATURES/ARCHITECTURE say "17 tools". | **18** (E4) | Never hard-code a count anywhere. |

## Implications for Roadmap

The phases map 1:1 to contract §6.2 steps. Critical path: **1 → 2 → 3a → 6a → 6b → 7**. **4** runs in parallel after 2. **3b** runs in parallel with 3a and is needed before 6a. **5** runs after 3a, in parallel with 6a.

### Phase 1 (step 1): Scaffold, publishing proof and quality gates
**Rationale:** Every genuinely unproven stack piece lives here. Module types, the package root and artifactIds become one-way decisions once tagged.
**Delivers:**
- 4 modules: `:core`/`:providers` as `kotlin.jvm`, `:keystore` as an AGP-9 library, `:sample` as an app.
- JVM 11, `explicitApi()`, detekt zero-baseline with invariant rules (`ForbiddenImport` for okhttp internals / mockwebserver3 / `android.util.Log` / DI annotations; ban `runCatching`/`println`/`printStackTrace`).
- Metalava wired (enforcing only from the v1.0.0 cut).
- `jitpack.yml` with an explicit per-module install list.
- **The OkHttp matrix harness + guard test**, green with a trivial `Call.await` test.
- A JitPack clean-cache resolve of `:providers` → transitive `:core` **by commit SHA**.
- `core/testing` `FakeAiProvider` stub.
- Registry/`ECOSYSTEM.md` entries.
- A gitignored fixture path with a lazy absence check.

**Avoids:** Pitfalls 1, 2, 3, 4 (harness), 12 (tooling), 15.

### Phase 2 (step 2): Core contract, pipeline and commit seam (keystone)
**Rationale:** Everything later only exercises these types, and after `v1.0.0` they can only grow additively. This phase gets its own deep test suite using **scripted fake strategies with no LLM**.
**Delivers:**
- The §5.1 types, `commandPipeline {}` DSL, `TierSelector`, `TierPolicy(+Source)`, `CommandOutcome` with `commits` + `held` on every variant.
- An open `FailureReason`/`EscalationReason`/`HoldReason` taxonomy.
- `CommandTrace` + typed events.
- The **whole commit package**: `ToolStep`, `PendingMutation`, `PreApplyGate`/`GateDecision(Admit(amended?) | Hold)`, `CommitCoordinator`, `CommitSink`, `HeldProposal`, `commitHeld`/`undoAll`, `AwaitingConfirmGate`, commit-time sink notification.
- The no-escalate-after-commit invariant, policy resolved before routing (offlineOnly never leaks to cloud), the single outcome-collapse helper, cancellation tests, and the redaction canary test.

**Avoids:** Pitfalls 9, 10, 12 (design), 13, 14.

### Phase 3 (step 3a): Neutral transcript types, router, ON_DEVICE gate and Anthropic transport (A1 must-pass)
**Rationale:** This is the largest step. ARCHITECTURE suggests splitting it into **3a-i** (transcript types + router + capabilities + ON_DEVICE gate, all pure JVM) and **3a-ii** (Anthropic transport + A1 matrix green). The split is recommended.
**Delivers:**
- The full multi-turn `transcript/` package incl. `NativeReplay`, `Usage`, `StopReason`, `CacheDirective`.
- `AiProvider`, `ProviderRouter`, `ProviderSelectionSource`/`Credential`, and an overridable capabilities table.
- `UnavailableOnDeviceProvider` + declared fallback.
- `AnthropicProvider`:
  - clean derived client and per-strategy timeouts
  - `Call.await` that closes the response on cancel
  - system-block `cache_control`
  - usage normalization
  - forced-tool 400 classification
  - HTTP-only retry
- **A1 green on 4.12.0 / 5.2.1 / 5.5.0.**

**Avoids:** Pitfalls 4, 5 (classification), 6 (usage/caching), 8 (neutral types), 10, 13, 14.

### Phase 4 (step 3b): OpenAI + OpenRouter (Chat Completions)
**Rationale:** One dialect covers both providers. It's needed before 6a's conformance suite and can run in parallel with 3a-ii.
**Delivers:**
- `ChatCompletionsProvider.openAi()/.openRouter()` with the **nested** `function` tool shape. **Don't port CT's flat shape.**
- Strict-mode keyword stripping.
- `reasoning_effort:"none"` with tools on GPT-5.4+.
- `max_completion_tokens` for reasoning models.
- `arguments` double-decode → `MalformedToolArguments`.
- `finish_reason`/`message.refusal` mapping, OpenRouter HTTP-200 errors, vendor-prefixed ids, `require_parameters`, and a stable `session_id`.
- Golden tests on **recorded, sanitized real bodies**.

**Avoids:** Pitfalls 8, 14.

### Phase 5 (step 4): `:keystore`
**Rationale:** Depends only on `ProviderId`/`Credential` from step 2, so it's fully parallel.
**Delivers:**
- `KeystoreCrypto(+Seam)` generalized over an app-supplied `KeySlot` table and the app's `DataStore<Preferences>`.
- Separate `getKeyOrNull` (decrypt) and `getOrCreateKey` (encrypt, synchronized) paths.
- Typed read states and `java.util.Base64`.
- JVM tests via the seam, plus one androidTest on the TESTER.
- README guidance on Auto Backup.

**Avoids:** Pitfall 11.

### Phase 6 (step 5): SingleShotStrategy (CT port)
**Delivers:**
- One extraction call with forced tool or auto+strict fallback.
- `OutcomeResolver` → batch `CommitProposal` → coordinator.
- Default outcome mapping: no-call → Escalate, refusal → Failed.
- `parallel_tool_calls:false`.
- The CT gate scenarios as acceptance tests: weak match, batch, amended confirm, deferred `commitHeld`.

**Avoids:** Pitfalls 5 and 9.

### Phase 7 (step 6a): Multi-turn mappers + dialect conformance
**Delivers:**
- Anthropic ↔ neutral ↔ Chat round-trips.
- Verbatim native replay (fixture with thinking blocks).
- Tool-result batching vs one `role:tool` per id, `is_error` encoding, parallel calls, empty args.
- Per-dialect cache directive.
- Byte-determinism across iterations.
- One conformance suite run against both mappers.

**Avoids:** Pitfalls 6 and 7. Retrofitting after 6b is HIGH cost.

### Phase 8 (step 6b): AgenticLoopStrategy (SB port)
**Delivers:**
- The `AnthropicAgentLoop` port on 6a: whole-turn validation, token ceiling → final-iteration guard → dispatch order, sequential dispatch, 2-strike abort, the held `tool_result` byte-compatible with SB, effects on every outcome.
- Bounds from policy; `maxIterations >= 2` is validated.
- A per-command provider snapshot.
- SB's invariants as named tests.

**Avoids:** Pitfalls 6 (snapshot) and 9.

### Phase 9 (step 7): `:sample`, Gate-1, README, tag `v1.0.0`
**Delivers:**
- `:sample`:
  - loads the gitignored LE-1 fixture (sha256 `ebd3ef4a…af4ed3e`, 18 tools), with a loud error if it's absent
  - fake `ToolExecutor`
  - BYO key stored through `:keystore`, plus a legacy-format compat test
  - OkHttp 5.2.1 pinned, so the Android 5.x variant runs 4.12-compiled bytecode
- **Gate-1 on the TESTER** (`…-s22-ultra-2`): 2+ turns, turn-1 creation > 0, turn-2+ `cache_read_input_tokens > 0` around 7,016.
- A README good enough for an agent to wire the engine from it alone, using per-module coordinates and showing `else` branches.
- `api.txt` dumps committed.
- A JitPack clean-cache resolve of every module under E5 coordinates.
- The tag row messaged to the control plane (A14, single ledger writer).

**Avoids:** Pitfalls 1, 2, 6, 11, 12, 14.

### Phase Ordering Rationale

- Step 1 front-loads the only real stack risks (mixed JVM/AAR publishing, JitPack inter-module POMs, the matrix plumbing) while they're cheap.
- Step 2 is the keystone. Both gate modes, held-as-first-class and the closed/open hierarchy choices **must** be decided before any strategy exists, because retrofitting the second gate mode after the tag is a breaking change.
- Transcript types land in 3a, so 5 and 6a/6b build on one model. 6a is then small and focused on conformance.
- 4 (keystore) and 3b are off the critical path. Run them in parallel to shorten the milestone.
- The fixture is already delivered (LE-1), so step 7 has no external blocker unless SB's prompt or tools change. In that case, ask the orchestrator to regenerate the fixture.

### Research Flags

Phases that need `--research-phase` during planning:
- **Phase 1 (step 1):** Metalava on `kotlin.jvm` modules inside an AGP-9 build, JitPack's handling of inter-module POM/`.module` metadata (community reports only), and Gradle attribute plumbing for the OkHttp KMP variants.
- **Phase 4 (step 3b):** OpenAI `finish_reason` under forced tools, OpenRouter `reasoning_details` echo, the GPT-5.4+ `reasoning_effort` rule, and whether routed providers report `stop` with tool_calls. All MEDIUM/LOW web-sourced.
- **Phase 7 (step 6a):** preserved-thinking replay rules and OpenAI tool-message ordering edge cases. Build the golden fixtures from real responses.

Phases with standard patterns (research can be skipped):
- **Phase 2 (step 2):** a pure-Kotlin design fully derived from the SB/CT sources. It needs *discussion*, not research.
- **Phase 5 (step 4):** a direct generalization of two working `KeystoreCrypto` implementations.
- **Phase 6 (step 5)** and **Phase 8 (step 6b):** mechanical ports with named invariants.
- **Phase 9 (step 7):** process is defined by A10/§11. Device steps follow `~/.claude/context/devices/common.md` and use the TESTER only.

### Open Questions Requiring Yahir / Orchestrator Decisions

For the orchestrator (control-plane-f2) or peers:
1. **`ProviderId` as a value class instead of an enum.** The contract lists 4 ids but doesn't mandate an enum. Confirm that this needs no amendment.
2. **`testing/` package inside the `:core` main artifact** vs a new `:testing` module. The latter would be an A7-style amendment. Recommendation: keep it in `:core` for v1.0.
3. **Commit-time `CommitSink` notification** changes SB's end-of-run `executedTools` reconciliation. Confirm with secondbrain-2c that the Undo Center accepts per-action commits.
4. **CT's OpenAI flat tool shape** posted to `/chat/completions`: was OpenAI (not OpenRouter) ever device-verified? Ask caltracker-android-9a. The engine uses the nested shape regardless.
5. **CT's forced-tool list is missing `claude-sonnet-5-5`.** Tell caltracker-android-9a now, since it's a live bug in CT.
6. **A11 premise:** stt-engine is compiled at OkHttp 5.2.1, so backup-engine is the only true 4.12→5.x precedent. Per PROJECT.md, E6 covers this; confirm there are no further implications.

Discuss-phase decisions for Yahir (with recommendations):
7. **Model-side NoToolCall/prose in SingleShot:** escalate to the next tier by default, overridable per tier. **Recommendation: escalate.** Refusal → `Failed`.
8. **`offlineOnly` with ON_DEVICE absent:** `Failed(ProviderUnavailable/OFFLINE_UNAVAILABLE)`, loud, with zero HTTP calls. **Recommendation: Failed, not NoMatch.**
9. **Transport failures escalating to a tier on a different provider** (`escalateOnTransportFailure`). **Recommendation: default false** (loud).
10. **Token ceiling semantics:** keep SB's sum, where cache reads count at full weight, for parity. Add separate or weighted budgets later as an additive option. **Recommendation: parity default.**
11. **Held-outcome shape:** `Completed(held = …)` vs a dedicated variant. `StrategyOutcome` is contract-closed at 4 variants, so **recommendation: a `held` list on `CommandOutcome`**, with no new variant.
12. **`HeldProposal` expiry:** app-owned, or does the engine carry one? It ties into SB's Undo Center UX.
13. **Anthropic tail caching** (`conversationTail`): off in v1.0 (A10 needs only the system breakpoint). Measure at Gate-1 before turning it on.
14. **`JsonObject` in public seams** (a kotlinx.serialization 1.x coupling). **Recommendation: accept it**, since both consumers already use it.

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| Stack | HIGH | Versions read from registries on 2026-09-29, and consumer pins read from local repos. MEDIUM only for the in-build matrix attributes and JitPack inter-module metadata, which must be proven in step 1. |
| Features | HIGH (consumer needs) / MEDIUM (provider behavior) | Table stakes come straight from the SB/CT sources. The forced-tool 400 is corroborated by CT's live 2026-09-27 verification. OpenRouter/OpenAI quirks come from vendor docs and issue reports. |
| Architecture | HIGH | Seams, gate/commit unification and build order are derived from the contract and port sources read in full. Chat-Completions-vs-Responses is MEDIUM. |
| Pitfalls | HIGH | Mostly from sibling-repo evidence (stt-engine commit b8c9fdb (in the stt-engine repo) coordinates, YAT Metalava, backup-engine precedent) and port code. OkHttp/OpenAI details are MEDIUM. |

**Overall confidence:** HIGH for the plan shape. MEDIUM for three things that only live probes settle.

### Gaps to Address

- **JitPack inter-module POM groupId under E5:** resolve `:providers` with a transitive `:core` from an empty `GRADLE_USER_HOME` by commit SHA in step 1. Fallback: disable `.module` metadata on JitPack.
- **Metalava on JVM modules in an AGP-9 build:** verify in step 1. If it fails, fall back to BCV on the JVM modules only, with a documented additive-only review.
- **The `Call.await` coroutines-1.11 three-argument `resume(onCancellation)` overload:** verify in 3a.
- **OpenAI/OpenRouter live behavior:** A8 allows JVM-only testing, so fakes must replay recorded real bodies. Gather those in 3b, using a key only if Yahir provides one.
- **Explicit API mode through AGP-9 built-in Kotlin in `:keystore`:** fall back to `-Xexplicit-api=strict` if needed.
- **Cache minimum vs model at Gate-1:** Haiku 4.5 needs ≥ 4,096 tokens. The ~7k fixture clears it, but log the prefix size and `minCacheablePrefixTokens` in `:sample`.

## Sources

### Primary (HIGH confidence)
- `CROSS-REPO-SCOPE-CONTRACT.md` (§5, §6.2, §10 A1–A14, E1–E6, §11) and `.planning/PROJECT.md`.
- SB port sources: `AnthropicAgentLoop`, `AgentLoopResult`, `AnthropicToolRegistry`, `MutationGate`, `VoiceConfirmGate`, `MutationTierPolicy`, `MutationDispatchContext`, `PreMutationSnapshot`, `VoiceUndoOperations`, `KeystoreCrypto(Seam)`, `AnthropicApiKeyRepository`, `AgentModule`, manifest/backup rules.
- CT port sources: `AiProvider`, `ProviderRouter`, `BaseAiProvider`, `AnthropicProvider`, `OpenRouterProvider`, `LogFoodRequestBuilder`/`OpenAiLogFoodRequestBuilder`, `AnthropicKnownTool400Ids`, `ApiKeyRepository`, `KeystoreCrypto`, `RepositoryToolFacade`, `VoiceLogViewModel`.
- Sibling hubs: backup-engine (jitpack.yml, `singleVariant`, OkHttp 4.12), YAT (AGP 9.2.1 + JitPack + detekt 1.23.8 + Metalava, zero-ID baseline), stt-engine (multi-module install list, commit b8c9fdb (stt-engine repo) coordinate fix, OkHttp 5.2.1 pin).
- Registries (Maven Central / Google Maven / Gradle Plugin Portal), queried 2026-09-29. OkHttp CHANGELOG and `okhttp-5.5.0.module`.
- The bundled Claude API reference (cached 2026-09-25): caching minimums and breakpoints, forced tool_choice rejection, preserved thinking, usage fields.

### Secondary (MEDIUM confidence)
- OpenAI docs (Chat Completions, prompt caching) plus issue reports (LiteLLM, LibreChat, crush, Semantic Kernel, genkit, simonw/llm) on the `reasoning_effort` / nested-tool / `max_completion_tokens` rules.
- OpenRouter docs: prompt caching, errors (HTTP-200 errors), provider routing (`require_parameters`), `session_id` stickiness.
- JitPack `BUILDING.md` / docs (aggregator, env vars); metalava-gradle source; Kotlin ABI-validation docs; ML Kit GenAI Prompt API (beta).

### Tertiary (LOW confidence)
- jitpack#4112 / #4476 (inter-module POM rewriting, `.module` handling): the step-1 probe settles these.
- detekt#8865 / #6198 (type-resolution vs Kotlin 2.3 metadata).
- Competitor feature comparison (Vercel AI SDK, Koog, LangChain4j, Spring AI, OpenAI Agents SDK), used only for positioning.

---
*Research completed: 2026-09-29*
*Ready for roadmap: yes*
