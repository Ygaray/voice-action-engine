# Milestones

## v1.1 Grammar, Plan, Router, Undo, Spike, Adapter (Shipped: 2026-10-07 as `v1.1.0` @ `2e677a6`; closed 2026-10-11)

**Phases completed:** 9 phases (12-20), 88 plans, 79 tasks · **Requirements:** 36/36 (+ SPIKE-03 N/A-deferred, red verdict) · **Closeout:** override_closeout

**Delivered:** the cheap and offline tiers plus app-controlled routing and undo. A free, offline EN/ES LocalGrammar tier
heads the ladder with zero provider calls. PlanThenExecute does one planning call with ordered, gated, output-bound steps.
Start-tier selection is a custom picker or an opt-in engine Router. `:undo` gives run-level "Undo all (N)", and
`:voice-adapter` maps an `:stt` final segment to `CommandInput`. The Wave-1 seams SB 176-179 and CT 75 were blocked on are in.
The on-device spike measured red, so nothing ships. Five published modules on JitPack; SB and CT repinned to `v1.1.0`.

**Key accomplishments:**
1. Wave-1 additive seams (`onFailed`, `ReasoningMode`, `claude-sonnet-5` row, `carryIn`, `cappedByPolicy`, call ids, opt-in `KeyAccess`) and the W04 fix: a Responses-only model now fails as typed `ModelUnsupported`, proven live.
2. LocalGrammar + bilingual GrammarPack: typed slots, strict EN/ES number words (round trip 0..999,999), refuse-on-ambiguity, a per-slot `normalize` hook, 88/88 recognizer fixtures captured.
3. PlanThenExecute (one planning call, gated steps, write-output binding, at most one replan) and start-tier selection (`TierSelector.Custom` / opt-in `Router`, grammar pre-pass, loud fallback to Linear).
4. `:undo` (dependency-free journal, `compositeSink`, refuse-loudly `changed_since`) and `:voice-adapter` (`:stt` compileOnly, `:core` still hub-free).
5. On-device spike, Gemma 4 E2B on LiteRT-LM on the S22 TESTER: small RED (warm p50 20.3 s vs a 3 s bar), SB RED (unmeasured, time-boxed); verdict relayed early; ML denial is mechanical in `check`.
6. `:sample` Gate-1: all five legs PASS on the TESTER (grammar offline with zero calls, undo-all, plan, router, W04 probe); docs pass the isolated wiring test (13 checks) on the final SHA.

**Known verification overrides:** 0 newly acknowledged, 1 carried forward from a prior close (see STATE.md Deferred Items).
Phases 12-19 VERIFICATIONs read fingerprint-stale only because later phases edited shared files (seeded as S-034); all are
`status: passed` and superseded by the v1.1.0 cut gate (preflight 15/15, CUT OK). Gate-2 (2026-10-11): 12 and 18 signed-off;
13 signed-off-with-gap (SB envelope unmeasured); 19 signed-off-with-gap (real-speech grammar coverage untested; 3 wiring doc
stumbles deferred to a docs patch). Audit: `milestones/v1.1-MILESTONE-AUDIT.md` (tech_debt, 0 blockers).

<details><summary>Per-plan accomplishments (auto-extracted)</summary>

- litertlm-android 0.17.1 builds into a 16 KB-aligned arm64 debug APK on the repo's own pinned toolchain (no fallback, no pin moved), and the D-07 voice-usable bar is committed as machine-readable thresholds before any device step.
- SC4 ("no on-device or ML dependency in the published modules, whatever the verdict") is now mechanical: a module-scoped `verifyNoMlArtifacts` in `check`, ML tokens in the `:core` scan, and hygiene refusal of model weights, private gold labels and a spike line in `jitpack.yml`, each proven red-for-the-right-reason by plants.
- The spike verdict is now a pure Kotlin function of closed-grammar evidence and the locked thresholds, recomputable by one script (even after the module is deleted), with a host filter that cannot let a prompt, an SB tool name or a full digest into a committed file; every missing measurement reads red.
- A throwaway `ProviderId.ON_DEVICE` provider runs the real SingleShot, router and gate path over a backend seam with two measured routes (constrained JSON and native tool calls), and a single LiteRT-LM adapter that builds into the APK.
- One gold item through the real engine path is now a scored, filtered, parseable `VAE_SPIKE_TRIAL` line, with a committed small EN/ES/negative gold set and an SB-sized envelope whose fixture and labels live only in a private file outside the repository.
- The spike now has its own TESTER-only host runner: the sample runner's identity guard copied intact, a window-grant gate that refuses before any adb call, a D-07 time-box, digest-checked model and private-input pushes with a proving cleanup, all verified offline with a fake adb over 82 scenarios; both pinned Gemma 4 E2B files are on the host, sha256-verified, outside the repository.
- The on-device ladder is built and JVM-proven: one stage per process start, 13 stages in early-exit order, every stage ending with exactly one closed-grammar STAGE line, the D-10 rows and every D-07 metric measured as 13-THRESHOLDS.md defines them, and nothing but SpikeLine values leaving the app.
- One 4 h TESTER window measured Gemma 4 E2B on LiteRT-LM: the gating engine rows (GPU, prefill, KV reuse, ResponseFormat) and the full small envelope (screen and confirm) are committed filtered evidence; the SB envelope reached only its first 28 screen trials before the time-box expired, so the remaining SB and sustained stages were refused and read unmeasured. The TESTER is clean and cooled and the grant is consumed.
- The verdict is small RED (a measured fail on latency, memory, accuracy and false writes) and sb RED (unmeasured: the 4 h time-box ended at screen trial 28 of 80), reproduced by `scripts/verify-spike-verdict.sh --check` as `SPIKE_VERDICT_CHECK: OK lines=4`; the relay body is written and handed to the milestone master.
- Both envelopes red, so the computed branch is `red`: the spike module, its include, its LiteRT-LM catalog entries and its device-only scripts are removed, SC4 and hygiene still pass, and the verdict still reproduces from history as `SPIKE_VERDICT_CHECK: OK lines=4`.
- status: skipped (branch=red). The green_ship-only plan to ship a published `@Experimental` `:ondevice` module was a recorded no-op, with no code or doc file changed.
- LocalGrammarStrategy resolves a declared EN/ES GrammarPack phrase to the app's tool through the existing resolver, gate, commit and sink path with zero provider calls, plus six grammar trace codes and the resolver-rejection slice.
- Internal NumberWords facade that turns folded EN/ES token keys into exact `Long`/`BigDecimal` values or null, proven by an independent spell-and-parse round trip over 0..999,999 in both languages.
- D-11 text fold, the D-03 template mini-syntax with per-language sub-rules and fillers, and an anchored matcher that refuses on a second distinct reading, with every authoring fault failing at build.
- Four slot kinds (integer, decimal, choice, text) declared once per intent and bound in the matched language, so an EN transcript and its ES counterpart with spoken numbers give the same tool call with equal typed arguments, with every slot authoring fault refused at build.
- The grammar tier now refuses to guess: ambiguity ends with grammar_ambiguous, overlapping rules cannot be built, the language label table (with `tryOtherLanguage` and cross-pack agreement) is pinned, an input cap is derived from the pack, and near-miss, positive and mutation tests prove it.
- The grammar tier now has the per-slot `normalize` hook (null rejects, a throw is contained, agreement is compared after it), zero-commit terminal intents that end handled with a `TerminalCall`, and tests pinning resolver verdicts, held reporting, offline-only/provider policy and redaction across every grammar path.
- Opt-in TTS-to-on-device-recognizer androidTest tool, run once on the TESTER under the relayed D-12 window: 88 of 88 prompts recognized and committed as filtered `stt-fixtures.tsv` (synthetic-tts), device cleaned, window closed (`grant: consumed`).
- GrammarSttFixturesTest runs all 88 captured recognizer transcripts through a neutral EN/ES carrier pack: 45 of 77 value rows read exactly as spoken, 32 are listed refusals with reasons (28 recognizer mishears, 4 strict number forms), zero promotions, public API untouched.
- Ten whole-walk characterization tests pin the v1.0.1 Linear/Fixed walk (attempts, codes, events, usage, carry) on unmodified main code, plus a 64-character plan step id cap through the existing bad_id path.
- TierSelector.Custom(picker) runs the zero-call head, offers the picker only the eligible LLM tier ids, starts the walk where it points, and records the picker's turns in CommandTrace.selection so trace.usage matches what the run paid.
- An app can name its start-tier picker and its providers; the engine binds the picker's model from the app's own selection mapping for that id, rejects a picker id that shadows a tier at build time, and delivers the selection as a live `StartTierSelected` event.
- A stuck or faulty start-tier picker can no longer hang or fail a command: the engine cuts it off at `pickerTimeoutMillis` (default 2,000 ms) and every mistake becomes a Linear walk with exactly one `router_fallback`, while caller cancellation still propagates and a cut-off pick still reaches the trace with its tokens.
- An offline-only command never pays for a start-tier picker call: the picker is checked against the same `tierPermitted` rule as the tiers before it is called, and the zero-call head (the real P14 grammar tier included) is proven to run first as a free pre-pass with Linear-identical carry and suppression.
- `TierSelector.Router { tierDescriptions = ... }` makes one forced, enum-constrained `pick_start_tier` call to the app's own mapped router model after the zero-call head, starts the walk at the tier it names, and reports `tiersBypassed`; it is off by default, silent with one eligible model tier, and leaks nothing.
- An agent can now wire `TierSelector.Custom(picker)` (picker id mapped) or opt into `TierSelector.Router { tierDescriptions }` from INTEGRATION.md and API.md alone, the real Metalava dump shows the Phase 16 surface as +-only, and the full phase gate is green with every api.txt and every providers/keystore source byte-identical to 9c88961.
- A dependency-free `:undo` module (stdlib only, gated twice in `check`) with its first public type `UndoReason`, a single module manifest every script reads with a planted-module selftest, and a proven header-only Metalava seed.
- `ActionEvent.heldRunId` (set only by commitHeld, so a held child groups under the command that held it) and `compositeSink(...)` (journal beside the app sink, each child isolated from its siblings and from the run), both purely additive to `:core`.
- Hygiene, API-dump, surface-review, negative-control and ML-denial scripts now read `scripts/modules.list`, so `:undo` cannot be silently skipped, and three `:undo` zero-dependency plants are ready for the quiet-window run.
- The app-side `UndoCommitSink` behind `compositeSink(bridge, recording)`, with a stateful `ItemStore`, its adapter and three mutations, proven through the real pipeline for 8 grouping and failure scenarios (11 tests), with no engine or journal change.
- The bridge an agent copies from INTEGRATION.md is the compiled, executed, parity-tested bridge the sample proves, the undo docs and API.md are written from the real isolated dumps, and the whole phase gate is green with a quiet-window request ready for plan 17-10.
- The full `FinalSegment.toCommandInput` overload set, the `:stt`-free `commandInputOf` entry points and a closed-set (en, es, else null) normalizer, pinned by 30 JVM tests covering redaction sentinels and a reflection pin of the frozen API.
- Gate-1 runner now reads Phase 19's own decision file and evidence dir by default (env-overridable, never edited per run), checks dirty state from the module manifest, and refuses `consumed`; 41 offline guard scenarios prove it.
- The JitPack consumer probe gained the deferred `:adapteralone` project (adapter resolves with core and without `:stt`, omitting consumers never get `:stt`), and `release-cut.sh` now reads its wiring record and waiver packet from the archive-proof `.planning/releases/v1.1.0/`.
- The voice adapter now has exactly two forms per mapping (no-argument/short and full context + parentRunId), with a reflection test proving a three-argument string call has no context-only target.
- The free grammar tier answers an EN and an ES command and hands a near-miss on to Unhandled(cappedByPolicy=true), proven by provider_turns=0, attempts=0 and tripwire_calls=0 on every case, with the new VAE_TRACE line landing in all five vocabulary places in one commit.
- Two live legs are now JVM-proven with fakes: `plan_live` shows the second step of a PlanThenExecute command using the first step's new id on a stateful store, and `router_live` shows a router-chosen start tier (index 1, the plan tier) in the trace with the router's tokens counted; the in-app spend guard now stops at the requested 15 core / 16 total requests.
- An offline `undo_all` leg now shows "Undo all (3)" after a scripted three-action command (two creates, one rename of a seeded item) on a stateful store, undoes the whole command through `journal.undoAll` on the second press, proves a `changed_since` refusal after a later edit and a run-level undo of a PlanThenExecute partial (N = 2, one pending), and writes exactly one `VAE_VERDICT leg=undo_all`; `VAE_UNDO` carries counts, booleans and stable codes only.
- All five Phase 19 device legs (grammar_offline, undo_all, plan_live, router_live, responses_probe) passed on the TESTER R5CT10XNKQN in one window and one install, spending 4 of 16 requests (about USD 0.0044 of 0.05), with keys gone and the package uninstalled.
- One isolated Metalava dump of all five modules passes a per-module review (core seven sealed types, undo exactly UndoResult, others none), removals against the committed baselines are 0, and the verdict is "no shape fix needed".
- INTEGRATION.md now documents the grammar tier, the plan tier, the engine router, undo-all wiring and the voice adapter with five new Kotlin blocks, each byte-equal to a region that is compiled and executed (four in :sample's DocSnippetsTest, one in :voice-adapter's own tests), and the coverage gate reads both files.
- verify-docs-coverage.sh now requires every public type and top-level function of all five modules in API.md, runs 32 checks including a check per new tier, and proves itself with a ten-plant `--selftest`; README, API.md and ECOSYSTEM.md are in their final v1.1.0 state.
- The isolated wiring test now runs from stable paths, judges thirteen checks (grammar, plan, router, undo-all on top of v1.0's nine), proves itself with a cheap `selftest --local`, and can prepare a workspace against the unpushed tree's local publication.
- Every autonomous gate is green on HEAD `090fd8ec761178d5922523faaf24dc3ffb7b686b`, the wiring SHA candidate: one detekt MaxLineLength comment line in `DocSnippetAdapterTest.kt` was rewrapped (gap fix) after the first run was red on `39bde3e`, the Gate-1 build delta is `none`, and the window request, carry register and Gate-2 fragment are written.
- main merged (not rebased) with origin/main, scanned clean, and pushed to origin after the orchestrator's OK: `43768ea..9780113  main -> main`, remote head 97801136ae456ba6b0a8861ee1ea49932cc7bb75, no tag pushed.
- The five api.txt files are now the reviewed, committed released-API baseline (v1.0.1 is a strict subset for core, providers and keystore; undo and voice-adapter have real dumps), ActionEvent.toString has its stated rationale, and the P17 OI-1 "no store, no disk write" rule is pinned by a test.
- INTEGRATION.md and API.md now state the router's `pick_start_tier` answer shape, the `submit_plan` schema, what a SingleShot resolver does with a read call, the undo-bridge imports and the `store` shadowing trap, all taken from source, with no compiled doc-snippet region touched.
- The v1.1.0 waiver packet was proven against the real `release-cut.sh gate waiver` grammar in a throwaway clone, the 21 C7 defaults were frozen FINAL with verbatim orchestrator citations (no FALLBACK needed), and the packet was relayed to Yahir through orchestrator 3b, who answered "all as proposed" before any quiet window.
- Gate 12 now recognises undo and voice-adapter as new in v1.1.0 (directory-keyed, real committed dump required, patch releases can never add a module), gate 11 enforces the `:stt` confinement, five new controls prove both for the right reasons, and a tested javap descriptor-diff helper exists for D-02 before the wiring SHA.
- Waiver packet accepted; gates 10 and 12, api-baseline, `selftest all` (`RELEASE SELFTEST OK happy=1 negatives=40 positives=7`) and all 11 bash gates are green on the final tree; W (`wiring_sha`) is fixed at `4bdb663b4c7c1bf02d4588751705dfcc8b356ef1`. The first attempt (window 20-01) was red on a stale fixture and was superseded by window 20-07b after gap plan 20-13.
- W (4bdb663b4c) is pushed and live on JitPack with exactly the five manifest artifacts (C2 PASS); the D-02 core binary diff came back RED (removed=12) and was ruled a tool false positive by RT-12, then waived with a file:line mapping of every removal line to an internal constructor or a synthetic accessor; W is unchanged.
- A separate headless agent wired the engine from the four documents alone against the JitPack build of W (`4bdb663b4c`), and the empty-cache judge printed `WIRING TEST: PASS checks=13`; WIRING-RERUN.md is rewritten for the full 40-hex W, `gate wiring` is green, none of the five Phase 19 stumbles recurred, and the planning record is on origin/main (`ec0e2a7`).
- The 15-gate preflight passed once (exit 0), the orchestrator answered `tag ok v1.1.0 2e677a6...`, and `scripts/release-cut.sh cut` created and pushed only the annotated tag v1.1.0 at the approved HEAD.
- JitPack built the tag and all five coordinates (core, providers, keystore, undo, voice-adapter) resolve from an empty Gradle cache (`LIVE PROBE PASS ref=v1.1.0`, api line status ok, isTag true); the real strict docs gate printed `DOC COVERAGE OK checks=32 types=119` with no C23 note; the ledger row is written and matches the tag; window 20-03 is closed.
- Entry guard evaluated: the last `handoff:` line of evidence/cut-handoff.txt is `none` (from 20-11, outcome ok); the tag v1.1.0 is on origin, cut_result ok, jitpack_tag green. Scenario none: `rollback: not needed` is on record in evidence/rollback.txt. Tasks 2 and 3 (announcement revert, push, abandoned-cut relay) are skipped by design: nothing was changed, no file outside .planning, no window still open.
- The selftest's synthetic ledger row now lands inside the section 11 table (right after the last `ledger_range` row) instead of at end of file, with two fixtures for the table-last and trailing-erratum shapes; the stale H1 hand-off is superseded.

---

</details>

---

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
