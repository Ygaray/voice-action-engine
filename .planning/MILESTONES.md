# Milestones

## v1.0 Core Engine (Shipped: 2026-10-02 as `v1.0.0`; patch `v1.0.1` 2026-10-04; closed 2026-10-05)

**Phases completed:** 11 phases, 97 plans, 59 tasks · **Requirements:** 65/65 · **Closeout:** override_closeout

**Delivered:** a domain-free voice-command engine on JitPack. Apps compose a tier ladder, get a typed outcome that is
never thrown, and every write goes through one gate → commit → sink path. The cloud agentic path runs on device with
the Anthropic prompt cache hitting (cache_read 7016).

**Key accomplishments:**
1. Three per-module JitPack artifacts (core jar, providers jar, keystore AAR) behind a zero-baseline detekt + structural-invariant `check`.
2. The `:core` pipeline: DSL ladder, TierPolicy, a never-throw outcome, a gate → commit → sink seam in suspend and defer modes, and full telemetry.
3. Anthropic + OpenAI/OpenRouter transports, compiled against OkHttp 4.12 and green on 4.12.0 / 5.2.1 / 5.5.0 (A1).
4. `:keystore`: AES/GCM BYO keys per provider, reading SB/CT legacy blobs in both directions; SB and CT cut over in Wave-1.
5. SingleShot (CT port) and the provider-neutral AgenticLoop (SB port) over lossless multi-turn mappers.
6. `:sample` A10 proof on the TESTER (cache write 7016 / read 7016) and a README an agent can wire from (isolated wiring test PASS).

**Known verification overrides:** 2 newly acknowledged, 0 carried forward from a prior close (see STATE.md Deferred Items).
The Phase 10 SELF-UAT is partial (C5, accepted by evidence). All 11 phase VERIFICATIONs are fingerprint-stale from
post-verification edits; they are superseded by the v1.0.1 cut gate @b32840e. Gate-2 gaps: C1, C4, C5, and W04
(fix in v1.1 phase 12). Audit: `milestones/v1.0-MILESTONE-AUDIT.md` (tech_debt).

<details><summary>Per-plan accomplishments (auto-extracted)</summary>


- Four-module Gradle build (core jar, providers jar, keystore aar, inert sample) that publishes exactly three artifacts through jitpack.yml under com.github.Ygaray.voice-action-engine, proven locally from a clean copy and an empty-cache consumer.
- Rung 0 passed: JitPack built the pushed skeleton by commit SHA 7f9db22944 and served all three per-module coordinates (core jar, providers jar, keystore aar) that an empty-cache consumer resolves, with no fallback and no change to coordinates or module shape.
- detekt with real invariant rules (imports, comments, tuned defaults, no baseline) plus a literal-blanking source scanner for what detekt cannot see syntax-only, each rule proven to bite by planted controls and all attached to `check` on every published module.
- Six structural gates (JVM 11 class files, strict explicit API, one-way module graph with required :sample edges, :core classpath allowlist, OkHttp 4.12.0 floor, DI-artifact denial) attached to `check` in the shared invariants script, plus an isolated-copy script proving Metalava dump/additive/removal behavior without ever writing an api.txt into the real tree.
- Unpublished generic :core test fixtures (ScriptedResponses, RecordingSink, NoNetworkGuard) plus an OkHttp 4.12.0 / 5.2.1 / 5.5.0 matrix in :providers with a reflective per-leg version guard, all inside `./gradlew check`
- End-to-end planted-violation proof of every gate (56 ok, 0 failures), per-module coordinates in ECOSYSTEM.md and README.md, a repo-hygiene gate, and a green phase gate whose final SHA 5fc723786b was re-proven live on JitPack
- Identity/input/failure/policy value types as frozen-shape public API, with an in-JVM and a Metalava-dump-based surface lint live before any bulk type lands.
- The A19 clarification types, open commit-kind and trace-code vocabularies, normalized `Usage`, and the engine's single cancellation-correct broad-catch helper, all proven by 28 new JVM tests.
- One real end-to-end path: `commandPipeline {}` to `execute` to strategy to `session.submit(Mutation)` to gate Admit to `apply()` to per-action `onAction` to typed `CommandOutcome` to exactly-once `onRunClosed`, with all five contract-closed sealed types now present.
- The ladder now starts where `TierSelector` says, drops tiers per `TierPolicy` from static capability declarations before any strategy runs, refuses loudly and specifically when nothing may run, and `execute()` returns a typed outcome for every non-cancellation throw while closing each run exactly once.
- The single write path is complete: a hold writes nothing and is reported per mutation with the app's verbatim token, a throwing gate fails closed with a trace code, every apply outcome (ok, isError, throw, cancel) is reported exactly once, and a batch applies item by item with isolation.
- A tier that committed or holds anything can no longer hand the command to another tier (it ends `Completed(partial = true)`), every run closes exactly once on all five exit paths and refuses writes afterwards, and held changes resolve through an idempotent, gate-skipping `commitHeld` into a child run linked by `parentRunId`.
- `AwaitingConfirmGate` waits inside `admit` for the user's `resolve(id, true)` (fail-closed on decline, 120 s timeout, policy and hook errors), with a post-confirm amend hook; terminal calls are proven to end the run as `Completed(terminalCall)` with earlier commits and holds carried and no later tier.
- Every outcome now carries per-attempt provider, model, normalized usage and clock latency; strategies report model turns through `session.recordTurn` and read the run's token total from `session.tokensUsed`; an optional non-suspending listener sees the whole run live; and a canary test proves no user content or key reaches any engine output.
- The merged Phase 2 tree passes the full build and every Phase 1 regression gate; the real `:core` Metalava dump has exactly the five sealed types and no engine-produced class with a public constructor; decisions D-01..D-23 and the hand-off notes for Phases 3-11 are traced here.
- Neutral, redaction-safe, growth-safe conversation model in `:core`: sealed `Message` and `AssistantPart`, verbatim `NativeReplay` with exact provider-and-model stamp semantics, and `ModelRequest` / `ModelResponse` with an open `StopReason`, with the surface gate widened to exactly seven sealed types.
- Per-model capability table keyed by (ProviderId, exact model id) with app overrides patched over provider defaults, plus a source-scan test that mechanically bans model ids, hidden limits, settings reads and on-device code in `:core`.
- Three app-implemented fun-interface seams (CredentialSource with a typed Present/Missing/Unreadable result, ProviderSelectionSource with a required model and a one-level on-device fallback, OnDeviceCapability with ML-Kit-shaped statuses), a distinct CredentialUnreadable failure leaf, and recording/scripted fakes for the router tests.
- TurnRecord and TierAttempt now say which provider a turn fell back from (additively, 6-argument constructor kept), and TraceCode gains the thirteen documented refusal and fallback codes the router will record.
- The AiProvider / ProviderCall / ModelResult contract and a scripted, recording, loud FakeAiProvider now exist in :core, and ToolSpec gained `strict` through @JvmOverloads with a mechanical growth rule guarding the transcript and provider packages.
- An internal ModelRouter resolves selection, tier/policy gate, on-device probe, registration, per-provider credential and capabilities into a credential-hiding BoundModel, refusing every failure with a typed reason, one trace code and zero provider calls.
- `session.model()` returns a per-tier, lazily bound, Mutex-guarded frozen handle resolved through the 03-06 router from seams declared in `commandPipeline { provider(...); providerSelection; credentials; capabilities(...) }`, with build-time override validation and one on-device probe shared by the pre-check and the router.
- `PipelineBuilder.onDevice` is the one public on-device gate (default `Unavailable("not_implemented")`) feeding the shared internal hook; the router serves an unusable on-device selection only from its declared, policy-permitted fallback with that provider's own key, and fails loudly with zero calls otherwise.
- After every successful routed response the handle decides, from a conservative prefix estimate capped by the billed prompt, whether a caching model should have hit its cache, and raises the existing `CacheNotEngaged` event (ids only) when it is sure.
- Phase 3 closes green on the merged tree: a routed-path canary proves no content or key escapes through the new carriers, `./gradlew check` and a forced 406-test `:core` run pass, the Phase 1 gates hold, the :core API has exactly the seven allowed sealed types, and only app- or provider-constructed types have public constructors.
- Status-first error reader for OpenAI and OpenRouter (non-2xx and HTTP-200 envelopes) keeping only status, error type and request id, plus a shared `isHeaderSafe`.
- A 35-row golden manifest replays every documented OpenAI and OpenRouter answer shape, 200 error bodies included, through the production decoder and error map, with a strict loader and a hygiene scan so no capture can be skipped or committed unsanitized.
- Retry, timeout, network, cancellation and malformed-key behavior of the Chat Completions transport is proven on OpenAI and OpenRouter and on OkHttp 4.12.0, 5.2.1 and 5.5.0 with 43 tests per leg and no change to production code.
- Cross-provider token parity test: the same work in Anthropic, OpenAI and OpenRouter wire formats normalizes to the same four usage buckets, the same `tokensUsed`, and the same token-ceiling answer.
- Two parameterized contract test classes prove omitted optionals reach the app absent and no secret escapes the Chat transport, on OpenAI and OpenRouter across all three OkHttp legs.
- Everything the key-gated Chat Completions capture needs is in place and proven key-free: an opt-in task outside `check`, a bounded runner that sends the production encoder's bytes, a sanitizer for committed goldens, and a green phase gate. No live call was made.
- Orchestrator line: 11 HTTP requests (OpenAI 5, OpenRouter 6; ceiling 12), estimated cost under USD 0.01, all through with-test-keys (so in usage.log). Eight real, sanitized bodies are now goldens and replay green in `./gradlew check`.
- Neutral `singleToolCall` flag on `ModelRequest`, an `extra_tool_calls_dropped` trace code with an internal `recordCode` session hook, and `refusal`/`toolCalls` scripted-result helpers on `FakeAiProvider`, each proven through a real routed pipeline.
- The neutral `singleToolCall` flag now reaches the wire: Anthropic gets `disable_parallel_tool_use: true` inside `tool_choice` (forced and reshaped), OpenAI gets `parallel_tool_calls: false`, OpenRouter and the o-series never get the switch.
- The app-facing seams a single-shot tier is built on (`ToolSpecProvider`/`ToolingSnapshot`, `OutcomeResolver`/`Extraction`/`Resolution`, `UserTurnRenderer`/`UserTurnContext`) exist, compose with the shipped gate and sink, leak nothing through `toString`, and were signed off APPROVE before any strategy depends on them.
- `SingleShotStrategy` is the engine's first real tier: one forced single-call extraction, local resolution through the app's resolver, every write through the gate as one merged proposal, a closed default outcome mapping with two per-tier overrides, first-call-only and terminal-tool routing.
- SingleShot now enforces the 6 / 60000 / 4096 limits from the session policy alone (pre-call and post-call token ceiling, per-turn max tokens, exactly one provider call), covered by ten named tests, and a second set of five tests proves per-command text never touches the cached prefix.
- One pure pre-flight check, run once per logical call before any HTTP request, makes both transports refuse a mis-stamped or badly answered conversation with a typed kind-only reason, and both encoders stop rebuilding stamped turns.
- Both dialects now send a turn's tool results in the order the model made the calls, Anthropic omits `content` when there is no text, and Chat marks a failed tool as `{"error": <text>}` so the model can tell it failed.
- A zero-argument tool call now decodes as `{}` however the provider spells "nothing" on both dialects, and the Chat replay is repaired only for a missing role or an empty arguments value.
- A strict, canonical, hygiene-checked home for conversation goldens, a sanitizer that keeps ids consistent across turns and never touches thinking or reasoning, and one synthetic script for every later plan.
- One abstract conformance suite, bound to the Anthropic mapper through a WireDialect adapter, replays two derived conversations through the production encoder, decoder and real provider and proves exact per-turn wire, call-ordered batched results, whole-history byte-for-byte replay (thinking included) and fail-loud stamp and coverage handling with zero requests.
- The one abstract conformance suite now also runs on OpenAI and OpenRouter Chat Completions through a `ChatWire` adapter that calls only production code, over four derived conversations built on real Phase 5 envelopes: parallel and zero-argument calls, call-ordered `role:tool` results with `{"error":...}` wrapping, allowlist replay (annotations and reasoning dropped, `index` and `reasoning_details` kept), and empty-arguments repair.
- The app's two-phase `ToolExecutor` seam, three additive loop trace codes and a scripted executor fixture, proven against the shipped gate and sink, with the relayed sign-off recorded as APPROVE-WITH-CHANGES.
- A bounded agentic tier over the app's ToolSpecProvider, ToolExecutor and UserTurnRenderer: one command runs a gated tool turn, feeds the results back and completes on the model's first text, with an invariant cached prefix and no hard-coded tool count.
- The agentic loop now answers the whole gate path (read-tool mutations dropped, held/rejected/previewed/faulty calls reported with fixed notices), aborts on a second error from the same tool after finishing the turn, and ends on a terminal tool call with its edge rules, without adding any public symbol.
- Cause codes are now public with their UX documented, ToolSpec freezes as a single constructor, and the whole three-module surface is dumped in isolation and reviewed in writing (INTERIM API REVIEW: INTENDED).

---

</details>
