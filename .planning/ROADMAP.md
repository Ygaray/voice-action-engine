# Roadmap: voice-action-engine

## Milestones

- 🚧 **v1.0 — Core Engine** — Phases 1-10 (in progress; contract §6.2 steps 1–7; cuts `v1.0.0`)
- 📋 **v1.1 — Grammar, Plan, Router, Spike, Adapter** — contract §6.2 steps 8–13 (separate GSD milestone per A4; cuts `v1.1.0`)

## Overview

v1.0 turns contract §6.2 steps 1–7 into ten phases and ends at tag `v1.0.0`. It opens by proving the riskiest stack pieces (per-module JitPack publishing of mixed JVM/AAR modules, the OkHttp matrix plumbing) while they are still cheap. Next comes the keystone: the outcome, gate and commit types that harden into the immutable, additive-only API, all pinned down before any strategy exists. Providers follow in three steps: neutral transcript types and the ProviderRouter, then Anthropic with the A1 must-pass matrix, then OpenAI/OpenRouter. `:keystore` runs off to the side. After that come the two strategies: SingleShot (CT port), then the multi-turn mappers, then the provider-neutral AgenticLoop (SB port). The milestone closes with the `:sample` harness proving prompt-cache hits on the TESTER against SB's real prompt, a live smoke call on each of the three clouds, a README an AI agent can wire from, and the §11 tag cut.

**Authority:** `CROSS-REPO-SCOPE-CONTRACT.md` §6.2 + §10 (A1–A18, E1–E6) + §11 wins over this file. **Sequencing gate (A13):** no phase is planned until the orchestrator (`yahir-gsd-control-plane-f2`) sends GO after the R1 reconvene.

## Phases

**Phase Numbering:**

- Integer phases (1, 2, 3): Planned milestone work
- Decimal phases (2.1, 2.2): Urgent insertions (marked with INSERTED)

Decimal phases appear between their surrounding integers in numeric order.

### 🚧 v1.0 — Core Engine (Phases 1-11) — IN PROGRESS

- [x] **Phase 1: Scaffold & Publishing Proof** - §6.2 step 1: four modules, per-module JitPack coordinates proven by SHA, zero-baseline detekt invariants, fake-provider and OkHttp-matrix harnesses (completed 2026-09-30)
- [x] **Phase 2: Core Contract, Pipeline & Commit Seam** - §6.2 step 2 (keystone): pipeline DSL, typed never-thrown outcomes, one gate → commit path in both modes, telemetry (completed 2026-09-30)
- [x] **Phase 3: Transcript Types, ProviderRouter & On-Device Gate** - §6.2 step 3a-i: neutral multi-turn transcript, per-command provider/model/key seam, `ON_DEVICE` slot with clean fallback (pure JVM) (completed 2026-09-30)
- [x] **Phase 4: Anthropic Transport & OkHttp Matrix** - §6.2 step 3a-ii (A1 must-pass): cache-correct Anthropic transport green on OkHttp 4.12.0 / 5.2.1 / 5.5.0 (completed 2026-10-01)
- [ ] **Phase 5: OpenAI & OpenRouter Transports** - §6.2 step 3b: one Chat Completions transport for both, real wire shapes and quirks mapped to typed outcomes
- [ ] **Phase 6: Keystore** - §6.2 step 4: BYO keys encrypted per provider in the app's own DataStore, existing aliases preserved
- [ ] **Phase 7: SingleShot Strategy** - §6.2 step 5: one forced-tool extraction resolved locally and committed through the gate (CT port)
- [ ] **Phase 8: Multi-turn Mappers** - §6.2 step 6a: lossless neutral ↔ Anthropic / Chat Completions tool conversations with verbatim replay
- [ ] **Phase 9: Agentic Loop Strategy** - §6.2 step 6b: SB's bounded agentic loop on any cloud provider, gated and honest on every exit
- [ ] **Phase 10: Sample Harness, Gate-1 & Docs** - §6.2 step 7: `:sample` A10 proof on the TESTER, live smokes on 3 clouds, agent-ready README
- [ ] **Phase 11: Cut v1.0.0** - §6.2 step 7 / §11: gated release after Phase 10's green Gate-1; tag row messaged to the orchestrator

## Dependencies & Parallelism

```
Critical path:  1 → 2 → 3 → 4 → 8 → 9 → 10 → 11
Side branches:  2 → 6 (:keystore) ........................ → 10
                3 → 5 (OpenAI + OpenRouter) → 8               (8 needs both 4 and 5)
                4 → 7 (SingleShot) ....................... → 10   (SHOT-02's Chat Completions leg also needs 5)
```

- Numeric order (1 → 10) already satisfies every dependency, so running the phases in series is always safe.
- Parallel options: Phase 6 can start once Phase 2 is done. Phase 5 can start once Phase 3 is done, alongside Phase 4. Phase 7 can start once Phase 4 is done.
- Phases 4 and 5 share transport plumbing (the cancellation-safe `Call.await` bridge, clean-client derivation, HTTP-only retry). If they run in parallel, Phase 4 owns that plumbing so it lands once.
- `:keystore` (Phase 6) needs only `ProviderId` and the `Credential` type from Phase 2. Research places both in step 2, which is what lets Phase 6 run in parallel with 3–5.

## Phase Details

### Phase 1: Scaffold & Publishing Proof

**Goal**: A consumer can resolve every published engine module from JitPack at its per-module coordinate. From the first commit on, one `./gradlew check` enforces the library's structural invariants and provides the test harnesses every later phase builds on.
**Contract step**: §6.2 step 1 (E5 coordinates; LE-7 fixture path)
**Depends on**: Nothing (first phase)
**Requirements**: BLD-01, BLD-02, BLD-03, BLD-04, BLD-05, BLD-07, BLD-08, BLD-09, CLN-01, CLN-05
**Success Criteria** (what must be TRUE):

  1. From an empty Gradle cache, a throwaway consumer resolves `com.github.Ygaray.voice-action-engine:<artifactId>` for `:core`, `:providers` (pulling `:core` transitively) and `:keystore` by commit SHA. JitPack's log shows `:sample` was never built or published, and `ECOSYSTEM.md` lists the same per-module coordinates. Control-plane registry/deps-index entries are not written here; the orchestrator writes them at the `v1.0.0` cut (LE-5, single writer).
  2. `./gradlew check` passes on the four-module build: `:sample → {:providers, :keystore} → :core`, package root `io.github.ygaray.voiceactionengine`, Kotlin 2.3.20 / AGP 9.2.1 / Gradle 9.4.1 on JDK 17. Every published class is JVM 11 bytecode (class-file major 55), and `:core`'s runtime classpath has no HTTP, Android, DI or other-hub artifact.
  3. Planting any banned construct in library source makes `./gradlew check` fail. The banned list: a DI annotation, `android.util.Log`, `okhttp3.internal.*`, `mockwebserver3.*`, `runCatching`, `println`, `printStackTrace`, or an app planning id (T-xx-xx, WR-xx, "Phase NN D-xx") in a comment. detekt runs with `maxIssues: 0` and no baseline file.
  4. In each published module, `explicitApi()` rejects a public declaration that lacks explicit visibility. Metalava emits an `api.txt` dump for all three published modules; the dumps are committed only at the `v1.0.0` cut.
  5. `./gradlew check` runs the harnesses later phases rely on. A fake provider runs a `:core` pipeline test with zero network, and the OkHttp matrix runs a trivial test on the 4.12.0 / 5.2.1 / 5.5.0 legs, with a guard proving each leg's runtime version. `graphify-out/` and the A10 fixture path are gitignored.

**Plans**: 6/6 plans executed

Plans:
**Wave 1**

- [x] 01-01-PLAN.md — Tracer: publish-only four-module skeleton (wrapper, root build, `:core`/`:providers`/`:keystore`, inert `:sample`, `jitpack.yml`) + clean-copy dry run and empty-cache consumer probe

**Wave 2** *(blocked on Wave 1 completion)*

- [x] 01-02-PLAN.md — Live JitPack probe by commit SHA (jar→jar, AAR→jar, `:sample` absent), fallback ladder F1→F2→F3, blocking-human gate if F4/F5 is reached

**Wave 3** *(blocked on Wave 2 completion)*

- [x] 01-03-PLAN.md — Source gates: detekt zero-baseline rules, `scanBannedConstructs`, no-baseline check, negative controls

**Wave 4** *(blocked on Wave 3 completion)*

- [x] 01-04-PLAN.md — Structural gates: module graph, `:core` classpath allowlist, JVM 11 bytecode, explicitApi, OkHttp 4.12.0 floor, DI-artifact denial; Metalava wiring proof

**Wave 5** *(blocked on Wave 4 completion)*

- [x] 01-05-PLAN.md — Harnesses: `:core` testFixtures (ScriptedResponses, RecordingSink, NoNetworkGuard) + `:providers` OkHttp 4.12.0/5.2.1/5.5.0 matrix with reflective version guard

**Wave 6** *(blocked on Wave 5 completion)*

- [x] 01-06-PLAN.md — End-to-end negative controls, ECOSYSTEM.md/README.md coordinate fix, hygiene script, phase gate with final live JitPack probe

**Research flag**: yes. Open items: Metalava on `kotlin.jvm` modules inside an AGP-9 build; JitPack's handling of inter-module POM / `.module` metadata under E5 (fallback: disable `.module` metadata); Gradle attribute plumbing for OkHttp's KMP variants in the in-build matrix legs.

### Phase 2: Core Contract, Pipeline & Commit Seam

**Goal**: A consumer can compose a tier ladder with the DSL and get back a typed outcome that is never thrown, with a full trace attached. Every write goes through one engine-owned gate → commit path, and held actions are reported honestly. All of it is proven with scripted fake strategies, no LLM. This is the keystone: after `v1.0.0` these types can only grow additively.
**Contract step**: §6.2 step 2 (§5.1; A2, A6, A17; E1)
**Depends on**: Phase 1
**Requirements**: CORE-01, CORE-02, CORE-03, CORE-04, CORE-05, CORE-06, CORE-07, CORE-08, CORE-09, GATE-01, GATE-02, GATE-03, GATE-04, GATE-05, GATE-06, GATE-07, TEL-01, TEL-02
**Success Criteria** (what must be TRUE):

  1. A consumer composes `commandPipeline { tier(...); selector = Linear; policy = ... }` and invokes it with `CommandInput(transcript, "en"|"es"|null, context)`. Scripted fake strategies show the ladder climbing on `Escalate`/`NoMatch` (with `carry` handed to the next tier), stopping on `Completed`/`Failed`, and starting mid-ladder under `TierSelector.Fixed(tier)`.
  2. `TierPolicy` is read from the app's source on every call and enforces `maxTier`, `allowedProviders` and the 6 / 60,000 / 4,096 defaults. `maxIterations < 2` is rejected. `offlineOnly` with no on-device provider available returns a loud `Failed` after zero provider calls.
  3. No exception escapes the pipeline. Every throw collapses to a typed outcome through the single collapse helper, `CancellationException` always propagates, and an engine timeout reports `TIMEOUT` rather than `NETWORK`. Each failure carries a `FailureReason` from the open taxonomy (auth through provider-unavailable, plus an `Other` leaf) and the provider request id when one exists. Growing public types are regular classes, and `ProviderId` is a value class with the four constants.
  4. Every write goes prepare → `PreApplyGate.admit` → `CommitSink`, and both gate modes work: suspend mode (`AwaitingConfirmGate`, 120 s default, fail-closed on timeout/decline/error) and defer mode (`Hold`, then `commitHeld`, optionally amended). A held action is never reported as success and yields `{"applied":false,"status":"held_for_confirmation"}`. `CommitSink` hears each commit as it happens, with a `runId` and an `ExecutedToolCall`-shaped payload, and `onRunClosed` fires exactly once on each of the five exit paths (one test per path).
  5. A tier that has committed ≥1 action and then asks to escalate never reaches the next tier, and no write repeats. Every outcome carries the ordered executed-action list, its commits, its held proposals and a `CommandTrace` (per-tier attempts, escalation reasons, provider/model, normalized tokens, latency). An optional typed callback receives the same events live.

**Plans**: 9/9 plans executed

Plans:
**Wave 1**

- [x] 02-01-PLAN.md — Tracer: identities, CommandInput(+parentRunId), open FailureReason/EscalationReason taxonomies, FailureDetails, TierPolicy(+Source); ApiShapeTest + isolated-copy `review-api-surface.sh`; delete CoreModule
- [x] 02-02-PLAN.md — Tracer: ToolSpec.clarification -> TerminalCall -> Clarification; ActionKind/FinishedKind/Usage/TraceCode value types; the single `guarded` collapse helper

**Wave 2** *(blocked on Wave 1 completion)*

- [x] 02-03-PLAN.md — Tracer: the keystone spine end to end (DSL -> execute -> strategy -> session.submit -> gate -> apply -> CommitSink -> sealed CommandOutcome -> exactly-once onRunClosed) + scripted fixtures; build-time DSL validation

**Wave 3** *(blocked on Wave 2 completion)*

- [x] 02-04-PLAN.md — TierSelector (Linear/Fixed), per-call policy pre-check (maxTier, allowedProviders, offlineOnly, ON_DEVICE rule), never-throw collapse, engine deadline, cancellation
- [x] 02-05-PLAN.md — Commit coordinator: Hold + HeldProposal + held bytes, fail-closed gate step, Finished preview/error streaming, apply is_error/cancel semantics, sink isolation, batch per-item isolation, executed list

**Wave 4** *(blocked on Wave 3 completion)*

- [x] 02-06-PLAN.md — No escalation after commit/hold (Completed(partial)), five exit-path onRunClosed tests, no writes after close, idempotent commitHeld child runs, parentRunId linkage

**Wave 5** *(blocked on Wave 4 completion)*

- [x] 02-07-PLAN.md — AwaitingConfirmGate (suspend mode, 120 s, amend hook) + terminal-call pipeline behavior
- [x] 02-08-PLAN.md — TurnRecord/recordTurn, full CommandTrace, PipelineEvent + listener, redaction canary

**Wave 6** *(blocked on Wave 5 completion)*

- [x] 02-09-PLAN.md — Phase gate: full check, Phase 1 regression scripts, sealed-complete API surface review + constructor audit, hand-off notes and decision trace

### Phase 3: Transcript Types, ProviderRouter & On-Device Gate

**Goal**: The engine picks the provider, model and key for each command through app seams, holds neutral multi-turn transcript types in `:core`, and offers an `ON_DEVICE` slot that falls back cleanly on devices without on-device support. All pure JVM, no HTTP.
**Contract step**: §6.2 step 3a, part i (A5, L10)
**Depends on**: Phase 2
**Requirements**: PROV-01, PROV-02, PROV-03, PROV-10, TEL-03, CLN-03, CLN-04
**Success Criteria** (what must be TRUE):

  1. `:core` holds the neutral transcript types: messages, tool calls, tool results, system, usage, stop reason, cache directive, and a verbatim `NativeReplay` on assistant turns. Both a single-shot request and a multi-turn tool conversation can be expressed with them, and `:core` still has no HTTP dependency.
  2. The router asks the app's selection seam for provider, model and key once per command. A provider switch takes effect on the next command, and a mid-command settings change does not alter the running command. A missing key returns `NotConfigured` after zero provider calls, and one provider's key is never handed to another (fake-provider tests).
  3. When `ON_DEVICE` reports unavailable (as on the S22s), the router uses only the app's declared fallback: no key or provider substitution, no Nano/AICore code, and a loud typed failure when no fallback is declared.
  4. A fake provider that declares caching and returns zero cache read/write on a prefix above the model's minimum cacheable length raises `CacheNotEngaged`. The same result below the minimum stays silent.
  5. Limits and model ids reach the engine only through `TierPolicy` defaults, the selection seam and the app-overridable capability table. No hard-coded model-id or limit constant exists in library code, and no library code reads app settings storage.

**Plans**: 10/10 plans executed

Plans:
**Wave 1**

- [x] 03-01-PLAN.md — Tracer: neutral transcript types (sealed Message/AssistantPart, ToolResult, NativeReplay, ModelRequest/Response, ToolChoice, CacheDirective, StopReason); sealed allow-list widened to seven
- [x] 03-02-PLAN.md — Tracer: ModelCapabilities/CachingMode/ModelCapabilityTable (override > provider default > unknown); NoHardCodedConstantsTest (CLN-03/04 scan + no on-device implementation tokens)
- [x] 03-03-PLAN.md — Tracer: app seams (ProviderSelectionSource + one-level on-device fallback, CredentialSource + typed lookup, OnDeviceCapability); CredentialUnreadable; scripted source fakes
- [x] 03-04-PLAN.md — Tracer: TurnRecord/TierAttempt fallbackFrom; thirteen router trace codes

**Wave 2** *(blocked on Wave 1 completion)*

- [x] 03-05-PLAN.md — Tracer: AiProvider/ProviderCall/ModelResult contract + FakeAiProvider; ToolSpec.strict via @JvmOverloads; ApiShapeTest growth rule

**Wave 3** *(blocked on Wave 2 completion)*

- [x] 03-06-PLAN.md — Tracer: ModelRouter.bind (selection, policy gate, on-device probe, credential, capabilities) -> frozen BoundModel -> provider; every refusal typed, traced, call-free; capability check before call

**Wave 4** *(blocked on Wave 3 completion)*

- [x] 03-07-PLAN.md — Tracer: session.model() lazy frozen per-tier handle through commandPipeline { provider; providerSelection; credentials; capabilities }; capabilityTable; one probe instance; snapshot under turns, tiers and concurrency

**Wave 5** *(blocked on Wave 4 completion)*

- [x] 03-08-PLAN.md — Tracer: public ON_DEVICE gate behind the one internal hook; declared, policy-checked fallback with its own key; KeyIsolationTest
- [x] 03-09-PLAN.md — Tracer: CacheNotEngaged detection after each response (D-08..D-10, divisor 4.0 capped by billed prompt)

**Wave 6** *(blocked on Wave 5 completion)*

- [x] 03-10-PLAN.md — Tracer: routed-path redaction canary; phase gate, sealed-complete review, constructor audit, decision/edge/prohibition trace

### Phase 4: Anthropic Transport & OkHttp Matrix

**Goal**: A consumer's commands reach Anthropic over a cache-correct, cancellation-safe transport that runs green on the consumer's own OkHttp version (4.12 or 5.x) and leaks no secret anywhere.
**Contract step**: §6.2 step 3a, part ii. **A1 must-pass** (a 5.x-compat failure triggers a new amendment, not a workaround). A10 breakpoint parity; A16 retry.
**Depends on**: Phase 3
**Requirements**: PROV-04, PROV-05, PROV-06, PROV-07, PROV-09, PROV-11, PROV-13, BLD-06, TEL-04 (PROV-12's Anthropic leg is also proven here; PROV-12 maps to Phase 5)
**Success Criteria** (what must be TRUE):

  1. (A1) `:providers` compiles against OkHttp 4.12.0 (plain `api` floor). The same compiled Anthropic transport tests pass on the 4.12.0, 5.2.1 and 5.5.0 runtime classpaths inside `./gradlew check`, with okhttp and mockwebserver swapped together and each leg's guard test proving the version it ran.
  2. Anthropic requests carry exactly one `cache_control: ephemeral` on the last system block and none on messages. Encoding the same command twice yields a byte-identical tools + system prefix, and switching `en` ↔ `es` (or the date or transcript) leaves those prefix bytes unchanged. Usage maps to `{inputUncached, cacheRead, cacheWrite, output}`.
  3. On the specific forced-tool 400, the transport retries once with `auto` + strict + an instruction, and a reply with no tool call maps to `NoToolCall`. Transient 429 / 5xx / timeouts retry at most once, and a test proves a retried call causes no duplicate tool execution or `CommitSink` commit.
  4. Cancelling a command mid-call cancels the HTTP call and closes any late response. The client has no logging interceptors, targets the fixed HTTPS base URL (overridable in tests only) with `anthropic-version 2023-06-01`, and reads bodies with `body?.string()`.
  5. A canary runs through the pipeline and the Anthropic transport with a known key, transcript, tool arguments and tool_result, plus a provider error body. None of them appears in the trace, the events, any `toString()` or any failure message; failures carry only the HTTP status and the provider `error.type`.

**Plans**: 8/8 plans executed

Plans:
**Wave 1**

- [x] 04-01-PLAN.md — Tracer: ModelCapabilities.supportsForcedToolChoice (one additive core field) + verified AnthropicModels table (four rejecting ids, Haiku 4.5, unknown default); app override reaches the bound model
- [x] 04-02-PLAN.md — Tracer: shared http plumbing: cleanClient (no app hooks, no redirects, timeouts), OneShotJsonBody, callback-confined Call.await (cancel + late-response close), SafeFields

**Wave 2** *(blocked on Wave 1 completion)*

- [x] 04-03-PLAN.md — Tracer: AnthropicProvider { } end to end through commandPipeline; cache-correct byte-stable encoder (one breakpoint, en/es prefix identity, tool_result/is_error, native replay); full decoder (usage, stop reasons, malformed)

**Wave 3** *(blocked on Wave 2 completion)*

- [x] 04-04-PLAN.md — Tracer: typed failures: status-first error table with spend limits, parse-and-discard bodies; Timeout/Network mapping; app cancel and engine deadline cancel the HTTP call

**Wave 4** *(blocked on Wave 3 completion)*

- [x] 04-05-PLAN.md — Tracer: one transient retry below the seam (three-request budget, retry-after cap, no OkHttp replay); pipeline proof of one apply and one commit; ids-only AnthropicAttemptObserver

**Wave 5** *(blocked on Wave 4 completion)*

- [x] 04-06-PLAN.md — Tracer: forced-tool reshape (table and reactive 400, shared budget, no memo); NoToolCall; strict only without optional properties; omitted optional arrives absent

**Wave 6** *(blocked on Wave 5 completion)*

- [x] 04-07-PLAN.md — Tracer: opt-in liveAnthropicCapture outside check (*Live* excluded from test and legs), Haiku 4.5 only, at most 6 requests; run once if the key is installed

**Wave 7** *(blocked on Wave 6 completion)*

- [x] 04-08-PLAN.md — Tracer: TEL-04 canary through pipeline + transport on all legs (positive controls, echo legs); guard proves mockwebserver per leg; providers API shape; phase gate

### Phase 5: OpenAI & OpenRouter Transports

**Goal**: A consumer can point the same commands at OpenAI or OpenRouter through one Chat Completions transport. It speaks each provider's real wire shape and maps each provider's quirks to the engine's typed outcomes.
**Contract step**: §6.2 step 3b (port CT `AiProvider` / `ProviderRouter`, but not CT's flat tool shape)
**Depends on**: Phase 3 (can run in parallel with Phase 4; Phase 4 owns the shared transport plumbing)
**Requirements**: PROV-08, PROV-12 (primary; its Anthropic leg is proven in Phase 4)
**Success Criteria** (what must be TRUE):

  1. Golden request bodies show the correct wire shape: OpenAI and OpenRouter use the nested `{"type":"function","function":{...}}` tool shape with strict-mode keywords stripped, `max_completion_tokens` on reasoning models, `reasoning_effort:"none"` where tools require it, and, on OpenRouter, `provider.require_parameters: true` when a tool is forced.
  2. Golden tests built from recorded, sanitized real response bodies show these cases becoming typed outcomes rather than crashes or fabricated parses: `arguments` JSON-string decoding (bad JSON → `MalformedToolArguments`), `finish_reason` and `message.refusal` mapping, and OpenRouter's HTTP-200 error bodies.
  3. The Chat Completions tests run green on all three legs of the A1 OkHttp matrix, with usage normalized to `{inputUncached, cacheRead, cacheWrite, output}`.
  4. PROV-09/11/12/13 hold for the Chat Completions transport too: transport-only retry that never re-executes tools or re-commits, no logging interceptors + 4.12 body API, strict only when a schema has no optional properties, configurable timeout ≥ 60 s.
  5. The absent-optional contract test passes for **both OpenAI and OpenRouter**: an omitted optional parameter arrives absent, never as `""`, `[]` or a filled default.

**Plans**: TBD

- [x] 05-01-PLAN.md
- [x] 05-02-PLAN.md
- [x] 05-03-PLAN.md
- [x] 05-04-PLAN.md
- [x] 05-05-PLAN.md
- [x] 05-06-PLAN.md
- [x] 05-07-PLAN.md
- [x] 05-08-PLAN.md
- [x] 05-09-PLAN.md
- [x] 05-10-PLAN.md
- [x] 05-11-PLAN.md
- [ ] 05-12-PLAN.md

**Research flag**: yes. Open items: OpenAI `finish_reason` under forced tools; OpenRouter `reasoning_details` echo; the GPT-5.4+ `reasoning_effort` rule; whether routed providers report `stop` alongside tool_calls. Golden bodies must come from real responses (key only if Yahir supplies one).

### Phase 6: Keystore

**Goal**: A consumer can keep each provider's BYO API key encrypted on-device in its own DataStore, under its existing aliases so no current user's key is stranded, and feed that key straight into the provider seam.
**Contract step**: §6.2 step 4
**Depends on**: Phase 2 (`ProviderId` + `Credential`; runs in parallel with Phases 3–5)
**Requirements**: KEY-01, KEY-02, KEY-03, KEY-04
**Success Criteria** (what must be TRUE):

  1. A consumer can store, read and delete a key per provider. Keys are encrypted with AndroidKeyStore AES/GCM and persisted in the app's own `DataStore<Preferences>`, and the engine never opens a second DataStore on the app's file.
  2. Keys map through an app-supplied explicit `KeySlot` table. A key written under SB's or CT's existing alias and DataStore key reads back unchanged after migration (legacy-format compat test).
  3. Reads return `NotConfigured | Ready | KeyMissing | Unreadable`. Decrypting after the Keystore key is gone (a restored backup) reports `KeyMissing` and never creates a key. Encryption uses a synchronized get-or-create path and `java.util.Base64` NO_WRAP-compatible encoding.
  4. `KeystoreCredentialSource` plugs into the provider seam. JVM tests prove the round trip through the crypto seam, and one instrumented test passes on the TESTER.

**Plans**: TBD
**Device note**: the instrumented test runs only on the wired TESTER (`…-s22-ultra-2`), never the personal phone. Read `~/.claude/context/devices/common.md` first and always use `adb -s`.

### Phase 7: SingleShot Strategy

**Goal**: A consumer can handle a command with one forced-tool extraction call that is resolved locally into a (possibly batch) proposal and committed through the gate. This covers CT's real confirm flows.
**Contract step**: §6.2 step 5 (port CT; A2, A6)
**Depends on**: Phase 4 (Phase 5 for the Chat Completions leg of SHOT-02)
**Requirements**: SHOT-01, SHOT-02, SHOT-03
**Success Criteria** (what must be TRUE):

  1. A `SingleShotStrategy` tier makes one forced-tool call using the app's `ToolSpecProvider`. The app's `OutcomeResolver` turns the result into a (possibly batch) proposal, which commits only through the gate → `CommitSink` path.
  2. By default, a reply with no tool call (or prose only) escalates with `NoToolCall`, and a refusal fails with `REFUSAL`. Both mappings can be overridden per tier, and Chat Completions requests carry `parallel_tool_calls: false`.
  3. CT's confirm scenarios pass as acceptance tests: weak match held for confirmation, batch proposal, amended confirm, and deferred `commitHeld`.

**Plans**: TBD

### Phase 8: Multi-turn Mappers

**Goal**: A multi-turn tool conversation round-trips losslessly between the neutral transcript and each provider dialect, so the agentic loop can run on any cloud provider without breaking thinking blocks or the cache.
**Contract step**: §6.2 step 6a (A8)
**Depends on**: Phase 4, Phase 5
**Requirements**: XCR-01, XCR-02, XCR-03
**Success Criteria** (what must be TRUE):

  1. One conformance suite runs against both the Anthropic and the Chat Completions mapper and round-trips multi-turn tool conversations, including parallel calls and empty arguments.
  2. Assistant turns replay verbatim from `NativeReplay` to the same provider and model: a golden fixture with thinking blocks survives byte-for-byte. A transcript never crosses providers; `carry` stays semantic only.
  3. Tool results are encoded per dialect: Anthropic batches all of a turn's results into one user message with `is_error`, and Chat Completions sends one `role:tool` message per call id. Golden tests built from recorded, sanitized real response bodies prove it.
  4. Each dialect applies its own cache directive (Anthropic keeps its single system breakpoint; Chat Completions relies on automatic caching), and the cached prefix bytes stay identical on every iteration of a multi-turn conversation.

**Plans**: TBD
**Research flag**: yes. Open items: preserved-thinking replay rules and OpenAI tool-message ordering edge cases. Build the golden fixtures from real responses.

### Phase 9: Agentic Loop Strategy

**Goal**: A consumer can run SB's bounded agentic loop on any cloud provider over its own tools. The engine gates every mutating step, and nothing committed is ever hidden behind a failure.
**Contract step**: §6.2 step 6b (port SB `AnthropicAgentLoop` onto 6a; A17)
**Depends on**: Phase 8
**Requirements**: LOOP-01, LOOP-02, LOOP-03, CLN-02
**Success Criteria** (what must be TRUE):

  1. An `AgenticLoopStrategy` tier runs over the app's `ToolSpecProvider` and two-phase `ToolExecutor` (`prepare` → `Finished | Mutation`) on Anthropic, OpenAI and OpenRouter (JVM, fake provider). Every `Mutation` passes through the gate, and a held step feeds the model `{"applied":false,"status":"held_for_confirmation"}`.
  2. SB's guards pass as named tests: whole-turn validation, token-ceiling check before dispatch, final-iteration guard (no tool runs on the last permitted iteration), sequential dispatch, 2-strike tool-failure abort, unknown tool → `is_error`, and bounds taken from `TierPolicy`.
  3. On every exit path (done, budget, cancel, error), the outcome lists the executed actions and commits made so far, and `CommitSink` has already been told about each commit.
  4. With both ports now landed, library code in `:core`, `:providers` and `:keystore` contains no app-domain types or prompts (`LogFood*`, `log_food`, SB `SYSTEM_PROMPT`, SB tool names, `MutationTier`) and hard-codes no tool count.

**Plans**: TBD

### Phase 10: Sample Harness, Gate-1 & Docs

**Goal**: The engine is proven on a real device against SB's real prompt and live on all three cloud providers, and it is documented well enough for an AI agent to wire it from the README alone. (The tag itself is Phase 11, so the immutable cut happens only after this phase's Gate-1 is green — orchestrator ruling.)
**Contract step**: §6.2 step 7 (A8, A10, A12, A14, A16; §11; LE-1, LE-7)
**Depends on**: Phase 6, Phase 7, Phase 9
**Requirements**: VER-01, VER-02, VER-03, VER-04
**Success Criteria** (what must be TRUE):

  1. `:sample` loads the LE-1 fixture (`sb-a10-fixture.json`, sha256 `ebd3ef4a…af4ed3e`) from its gitignored path. When the fixture is absent, it fails loudly at debug-task or run time, never at Gradle configuration time. It runs a fake `ToolExecutor`, stores the BYO key through `:keystore`, and pins OkHttp 5.2.1 so the 4.12-compiled engine bytecode runs on 5.x.
  2. Gate-1 on the TESTER: the Anthropic agentic loop runs ≥2 turns with turn-1 `cache_creation_input_tokens > 0` and turn-2+ `cache_read_input_tokens > 0`, near SB's 7,016. The confirm gate runs in canned-admit mode, and the prefix size and the model's minimum cacheable length are logged.
  3. One live single-shot smoke call each to Anthropic, OpenAI and OpenRouter from `:sample` (Yahir's real keys) returns a parsed tool call.
  4. An AI agent can wire the engine into a new app from the README (plus the integration doc) alone. The docs cover the per-module coordinates, a minimal pipeline, every seam, both gate modes and the `else` branches on open taxonomies, and they point to `:sample` as the working example.

**Plans**: TBD
**Device note**: all device work (Gate-1, the live smokes, the `:keystore` round trip) runs only on the wired TESTER (`…-s22-ultra-2`), never the personal phone (`…-s22-ultra`). Read `~/.claude/context/devices/common.md` first and always use `adb -s`.
**Fixture note**: never commit the fixture or reference SB's path at build time. If SB's prompt or tools change before this phase, ask the orchestrator to regenerate it. Never hard-code the tool count (18, E4).

### Phase 11: Cut v1.0.0

**Goal**: `v1.0.0` exists as an immutable, JitPack-resolvable tag only because every §11 precondition already held, and the orchestrator has the full ledger row.
**Contract step**: §6.2 step 7 / §11 (A12, A14, E5, E7)
**Depends on**: Phase 10 (its Gate-1 SELF-UAT must be green before this phase starts)
**Requirements**: VER-05
**Success Criteria** (what must be TRUE):

  1. A gated release script runs, in order: `./gradlew check` green → `apiDump` writes the three Metalava `api.txt` files, committed in the commit being tagged → `apiCheck` green → clean-clone JitPack dry run using `jitpack.yml`'s install list (never `:sample`) → leak scan of tracked files for the fixture name and key-shaped strings → declared version equals the tag. Only then is `v1.0.0` created and pushed.
  2. JitPack's build log for `v1.0.0` succeeds, and all three per-module coordinates (`com.github.Ygaray.voice-action-engine:voice-action-engine-{core,providers,keystore}:v1.0.0`) resolve from an empty Gradle cache.
  3. The full ledger row (repo, tag, commit, coordinates, contents, evidence path) is **messaged to `yahir-gsd-control-plane-f2`** (A14) and never committed to §11 here. The orchestrator writes the control-plane registry/deps-index entries (LE-5).
  4. `git.create_tag` is false, so GSD's milestone close never creates a stray `v1.0` marker tag next to `v1.0.0` (INC-2026-09-30-01).

**Plans**: TBD

## Progress

**Execution Order:**
Phases execute in numeric order: 1 → 2 → 3 → 4 → 5 → 6 → 7 → 8 → 9 → 10 → 11 (see Dependencies & Parallelism for the safe parallel options)

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 1. Scaffold & Publishing Proof | 6/6 | Complete    | 2026-09-30 |
| 2. Core Contract, Pipeline & Commit Seam | 9/9 | Complete    | 2026-09-30 |
| 3. Transcript Types, ProviderRouter & On-Device Gate | 10/10 | Complete    | 2026-09-30 |
| 4. Anthropic Transport & OkHttp Matrix | 8/8 | Complete    | 2026-10-01 |
| 5. OpenAI & OpenRouter Transports | 11/12 | In Progress|  |
| 6. Keystore | 0/TBD | Not started | - |
| 7. SingleShot Strategy | 0/TBD | Not started | - |
| 8. Multi-turn Mappers | 0/TBD | Not started | - |
| 9. Agentic Loop Strategy | 0/TBD | Not started | - |
| 10. Sample Harness, Gate-1 & Docs | 0/TBD | Not started | - |
| 11. Cut v1.0.0 | 0/TBD | Not started | - |
