# Roadmap: voice-action-engine

## Milestones

- ✅ **v1.0 — Core Engine**: Phases 1-11, shipped 2026-10-02 (tags `v1.0.0`, patch `v1.0.1` 2026-10-04); closed 2026-10-05. Archive: [milestones/v1.0-ROADMAP.md](milestones/v1.0-ROADMAP.md)
- 📋 **v1.1 — Grammar, Plan, Router, Undo, Spike, Adapter**: Phases 12-20 (planned; contract §6.2 steps 8–13 + A18; cuts `v1.1.0`). Scope: R-v1.1 GO, see `.planning/cross-repo/RECONVENE-BRIEF-R-v1.1.md`

**Authority:** `CROSS-REPO-SCOPE-CONTRACT.md` §6.2 + §10 + §11 wins over this file. Sequencing goes through the orchestrator
(name in `xrepo/vae-bilingual/effort.json`).

## Phases

<details>
<summary>✅ v1.0 Core Engine (Phases 1-11): SHIPPED 2026-10-02</summary>

- [x] Phase 1: Scaffold & Publishing Proof (6 plans), completed 2026-09-30
- [x] Phase 2: Core Contract, Pipeline & Commit Seam, completed 2026-09-30
- [x] Phase 3: Transcript Types, ProviderRouter & On-Device Gate, completed 2026-09-30
- [x] Phase 4: Anthropic Transport & OkHttp Matrix, completed 2026-10-01
- [x] Phase 5: OpenAI & OpenRouter Transports, completed 2026-10-01
- [x] Phase 6: Keystore, completed 2026-10-01
- [x] Phase 7: SingleShot Strategy, completed 2026-10-01
- [x] Phase 8: Multi-turn Mappers, completed 2026-10-01
- [x] Phase 9: Agentic Loop Strategy, completed 2026-10-01
- [x] Phase 10: Sample Harness, Gate-1 & Docs, completed 2026-10-01
- [x] Phase 11: Cut v1.0.0, completed 2026-10-02

Full detail: [milestones/v1.0-ROADMAP.md](milestones/v1.0-ROADMAP.md). Phase artifacts: `milestones/v1.0-phases/`.

</details>

### 📋 v1.1 — Grammar, Plan, Router, Undo, Spike, Adapter (Phases 12-20)

**Milestone Goal:** Give consumers the cheap and offline tiers (grammar, plan), app-controlled start-tier selection and
run-level undo, and fold in the Wave-1 additive seams SB 176–179 and CT 75 are blocked on. Cuts one `v1.1.0` (A4).

v1.1 opens with the additive seams and the W04 fix (Phase 12), because every later tier builds on their shapes. The
on-device spike (Phase 13) runs alongside it, time-boxed, so its verdict reaches SB 179 / CT 75 before they plan. Then
come the new tiers: LocalGrammar (14), PlanThenExecute (15), and start-tier selection (16), whose grammar pre-pass needs 14.
`:undo` (17) and `:voice-adapter` (18) are standalone modules. Phase 19 proves the new tiers end to end on the TESTER and
documents them so an agent can wire them. Phase 20 cuts the tag.

**Rules for every phase:**

- Every public-API change is **strictly additive** against the v1.0.1 `api.txt` (Metalava
  `--check-compatibility:api:released`). Existing JVM constructors are kept explicitly; a Kotlin default argument alone
  never keeps one.
- **"Unblocks"** means the consumer can plan against the fixed API shape (and resolve a commit SHA on JitPack for an early
  try). Under A4's one tag, the binary lands at the `v1.1.0` cut in Phase 20.
- **Shape:** nine phases (more than standard granularity's 4–6) because this is the approved R-v1.1 list. Each phase is
  one contract step or one published module, and the split is what lets 12 ‖ 13 and 14 ‖ 15 ‖ 17 ‖ 18 run in parallel.
  Phases 18 and 20 carry one requirement each on purpose. 18 is the only module allowed to depend on another hub. 20 is
  the one-way tag door that waits for 19's green Gate-1 (the v1.0 Phase 10/11 ruling).

- [x] **Phase 12: Wave-1 Seams & W04 Fix** - additive consumer seams (`onFailed`, `ReasoningMode`, `claude-sonnet-5` row, `carryIn`, `cappedByPolicy`, call ids, opt-in `KeyAccess`), the three v1.0.1 doc stumbles, and the Responses-only `ModelUnsupported` fix (completed 2026-10-05)
- [x] **Phase 13: On-Device Model Spike** - §6.2 step 11: time-boxed bundled ~2B model measurement on the TESTER, verdict to the orchestrator early, ships `@Experimental` only if green (completed 2026-10-06)
- [ ] **Phase 14: LocalGrammar & Bilingual GrammarPack** - §6.2 step 8: a free, offline EN/ES grammar tier with typed slots, number words and a per-slot `normalize` hook
- [ ] **Phase 15: PlanThenExecute Strategy** - §6.2 step 9: one planning call, ordered gated steps, write-output step binding, at most one replan
- [ ] **Phase 16: Start-Tier Selection** - §6.2 step 10: `TierSelector.Custom(StartTierPicker)` + opt-in `TierSelector.Router`, grammar pre-pass, loud fallback to Linear
- [ ] **Phase 17: Run-Level Undo** - A18: standalone `:undo` journal (entity adapters, compensators, refuse-loudly check) + pipeline integration for "Undo all (N)"
- [ ] **Phase 18: Voice Adapter** - §6.2 step 12: `:stt` v0.7.0 final segment → `CommandInput`, `:core` still hub-free
- [ ] **Phase 19: Sample Gate-1 & Docs** - grammar, plan, router and undo-all proven end to end on the TESTER; docs an agent can wire from
- [ ] **Phase 20: Cut v1.1.0** - §6.2 step 13 / §11: gated release, JitPack for every published module, ledger row to the orchestrator

#### Dependencies & Parallelism

```
Critical path:  12 → 14 → 16 → 19 → 20
Start first:    12 (seams + W04)   ‖   13 (spike, time-boxed; verdict → orchestrator as early as possible)
After 12:       14 (grammar) → 16 (router; its real-grammar pre-pass proof needs 14)
                15 (plan) ........................................................ → 19
                17 (:undo; UNDO-04 pipeline integration needs 12, the module itself can start any time) → 19
Any time:       18 (:voice-adapter; :stt v0.7.0 is already in §11) ............... → 19
Gate:           19 needs 12, 14-18 and 13's verdict;  20 needs 19's green Gate-1
```

- Numeric order (12 → 20) already satisfies every dependency, so running the phases in series is always safe.
- 12 and 13 start first. 12 fixes the shapes that 14–17 build on (`onFailed`, `ReasoningMode`,
  `ExecutedAction.providerCallId`, `cappedByPolicy`). 13 is early so its verdict reaches SB 179 / CT 75 before they plan.
- After 12, Phases 14, 15 and 17 can run in parallel. Phase 16 can start after 12 against a fake `NO_PROVIDER` head tier;
  only its real-grammar pre-pass proof waits for 14. Phase 18 has no in-milestone dependency.
- **Shared build plumbing:** 17, 18 and (if green) 13 each add a published module. Each touches `settings.gradle.kts`, the
  `jitpack.yml` install list, the module-graph and `:core` classpath gates, and the ECOSYSTEM coordinates. If they run in
  parallel, Phase 17 owns the "add a published module" plumbing and lands it first; the others rebase onto it (the same
  rule as v1.0's Phase 4 owning the shared transport plumbing).
- **One TESTER:** Phase 12 (PROV-16 live smoke), Phase 13 (spike) and Phase 19 (Gate-1) all drive the wired TESTER
  (`…-s22-ultra-2`), so they never overlap on the device. Read `~/.claude/context/devices/common.md` first; always `adb -s`;
  never the personal phone.

#### Consumer phase map

| VAE phase | Consumer phase unblocked | What the consumer gets |
|---|---|---|
| 12 | SB 176–178 cleanup, SB 178, SB 173+ picker, CT 75 | `onFailed` replaces SB's `EscalatingSingleShot` (backlog 999.121); `carryIn` drops the PD-173-18 ledger; `cappedByPolicy` drops the D-04 inference; `providerCallId` for run-undo; `claude-sonnet-5` row; `KeyAccess` fake for unit tests |
| 13 | SB 179, CT 75 | On-device verdict; on-device SingleShot only if green (CT §6.5 conditional) |
| 14 | SB 176, CT 75 | Grammar tier + `GrammarPack`; CT's optional cross-language `normalize` map |
| 15 | SB 177 | PlanThenExecute with write-output binding |
| 16 | SB 177, CT 75 (settings) | `StartTierPicker` seam for SB's own Router; opt-in engine Router |
| 17 | SB 178, CT "Undo all (N)" | `:undo` entity adapters and compensators (SB `ReminderArmer` → compensator) |
| 18 | SB / CT optional glue | Segment → `CommandInput` in one call |
| 19 | (gate) | Gate-1 evidence + docs before the cut |
| 20 | SB 176 repin, CT 75 repin | The `v1.1.0` coordinates (§11 row via the orchestrator) |

#### Phase 12: Wave-1 Seams & W04 Fix

**Goal**: Consumers get every additive seam they are blocked on (a provider-failure hook, an explicit reasoning knob, carry / policy-cap / tool-call-id facts on the outcome, opt-in key access for tests), the v1.0.1 wiring stumbles are gone from the docs, and a direct OpenAI Responses-only model fails with the specific `ModelUnsupported` reason instead of a generic `http_error`.
**Contract step**: Wave-1 consumer asks XR-171-03(1), XR-172-02, XR-173-01 a–e, XR-175-02(f) (R-v1.1 brief §1); W04 (v1.0 Gate-2 carry, fixed here per Yahir 2026-10-05, no `v1.0.2`); §11 rule 2 (additive)
**Depends on**: Nothing in v1.1 (builds on `v1.0.1`, b32840e7eb)
**Requirements**: SEAM-01, SEAM-02, SEAM-03, SEAM-04, SEAM-05, SEAM-06, SEAM-07, PROV-14, PROV-15, PROV-16, DOC-01
**Unblocks**: SB 176–178 cleanup, SB 178 (`providerCallId`), SB 173+ curated picker (`claude-sonnet-5`), CT 75; SB/CT unit tests (`KeyAccess` fake)
**Success Criteria** (what must be TRUE):

  1. An app sets `SingleShotStrategy.Builder.onFailed` and turns a provider HTTP 400 into `Escalate` with no decorator. The hook never fires for a ceiling, a gate or `strategy_error`, and leaving it unset still yields `Failed(reason, details)`. `ModelRequest.reasoning` and `Builder.reasoning` (SingleShot, AgenticLoop) default to `ReasoningMode.OFF`, and request goldens show today's wire bytes unchanged on Anthropic, OpenAI and OpenRouter. `claude-sonnet-5` resolves to its own capability row (forced tool choice allowed, explicit breakpoints, Anthropic's documented minimum cacheable prefix), so the cache diagnostic is no longer silent for it.
  2. Every outcome reports the new facts. `TierAttempt.carryIn` says whether that tier received a carry, never its content. `Unhandled.cappedByPolicy` is true exactly when policy skipped at least one tier (`tier_skipped_policy`) and no tier handled the command, and its KDoc states it covers every `tier_skipped_policy` case, offline-only included (SB condition). `Extraction.callId` and `ExecutedAction.providerCallId` carry the provider's tool-call id and are null for zero-call tiers. All of it is strictly additive: Metalava compat against the v1.0.1 `api.txt` passes, and the 7-arg `ModelRequest` and 2-arg `Extraction` JVM constructors still exist.
  3. An app can construct `ApiKeyStore(dataStore, slots, keyAccess)` only with an explicit `@OptIn(DelicateKeyAccess::class)`; without it, compilation fails. The ~10-line software `KeyAccess` fake in INTEGRATION.md compiles and round-trips a key on the JVM. INTEGRATION.md also fixes the three v1.0.1 stumbles (§7: what `ProviderId` prints; §10: the `runTest`/JUnit imports; §5/6: SingleShot can't serve reads) and notes that a single-tool SingleShot prefix won't cache on Haiku/OpenAI.
  4. W04 is fixed and proven on the JVM. (a) An encoder golden shows that `gpt-6-astra`, and every `OpenAiModelRules` model that rejects `"none"`, never receives `reasoning_effort: "none"`. (b) A MockWebServer replay of the captured W04 400 body (`param=reasoning_effort`, `code=unsupported_value`) maps to `FailureReason.ModelUnsupported`, not `http_error`, on all three OkHttp legs.
  5. W04 is proven live: (c) a `:sample` smoke call to `gpt-6-astra` under the `supportsTools` override returns the specific typed outcome (`ModelUnsupported`, never `http_error`). The evidence line is logged, the call uses the spend-capped OpenAI test key, and the request count is bounded.

**Plans:** 8/8 plans complete

Plans:
**Wave 1**

- [x] 12-01-PLAN.md — W04 wire deny-list + reasoning_effort classifier backstop (3 OkHttp legs) + `claude-sonnet-5` row (PROV-14, PROV-15, SEAM-03)
- [x] 12-02-PLAN.md — `ReasoningMode` + `Builder.reasoning` (byte-identical wire) + SingleShot `onFailed` (SEAM-02, SEAM-01)
- [x] 12-03-PLAN.md — `TierAttempt.carryIn` + `Unhandled.cappedByPolicy` (SEAM-04, SEAM-05)
- [x] 12-04-PLAN.md — public `KeyAccess` + opt-in `ApiKeyStore(dataStore, slots, keyAccess)` + cross-module negative-compile proof (SEAM-07)
- [x] 12-05-PLAN.md — judged responses_probe leg, runner retarget to Phase 12, pending live-leg request (PROV-16 prep)

**Wave 2** *(blocked on Wave 1 completion)*

- [x] 12-06-PLAN.md — `Extraction.callId` + `ExecutedAction.providerCallId` internal plumbing (SEAM-06)

**Wave 3** *(blocked on Wave 2 completion)*

- [x] 12-07-PLAN.md — DOC-01 stumbles + cache note + tested `KeyAccess` fake + API.md names (DOC-01)

**Wave 4** *(blocked on Wave 3 completion)*

- [x] 12-08-PLAN.md — host Probe C + bounded live TESTER smoke, evidence committed (PROV-16; not autonomous)

**Evidence**: `.planning/releases/v1.0-close/W04-host-wording-check.txt` (Probe B reproduces the L7 capture). The fix is internal (`wireRules` and/or the error classifier); no API change.
**Device note**: the PROV-16 smoke runs only on the wired TESTER; keys go through the test-keys workflow.
**Research flag**: light. Open items: Anthropic's documented minimum cacheable prefix for `claude-sonnet-5`; the full list of `OpenAiModelRules` ids that reject `"none"`.

#### Phase 13: On-Device Model Spike

**Goal**: The orchestrator, SB and CT know early, with numbers, whether a bundled ~2B on-device model is good enough for SingleShot-shaped commands on the S22 TESTER. If it is, apps can opt into it as an experimental provider. If not, nothing ships and the tag isn't blocked.
**Contract step**: §6.2 step 11 (V11-04; L10, A5; LATER-06 Nano/AICore unchanged)
**Depends on**: Nothing in v1.1 (uses v1.0's `ON_DEVICE` slot and capability gate; runs alongside Phase 12)
**Requirements**: SPIKE-01, SPIKE-02, SPIKE-03
**Unblocks**: SB 179 (on-device verdict), CT 75 (on-device SingleShot, conditional per §6.5)
**Success Criteria** (what must be TRUE):

  1. Within a declared time-box, a bundled Gemma-2B-class model (MediaPipe / LiteRT) runs on the TESTER. A recorded measurement shows latency, peak RAM and strict-JSON reliability (schema-valid tool-call JSON over a fixed set of SingleShot-shaped prompts, with the trial count), plus the APK-size cost a consumer would pay.
  2. A green/red verdict with those numbers is messaged to the orchestrator before SB 179 and CT 75 plan. The phase delivers its value at that message, not at any shipped code.
  3. Green: an on-device provider ships `@Experimental` in its own published module behind the `ON_DEVICE` capability gate, and where the gate reports unavailable, the run falls back exactly as in v1.0 (declared fallback or a loud typed failure). Red: no module and no code ship, SPIKE-03 is dispositioned N/A-deferred, and `v1.1.0` isn't blocked (L10).
  4. Whatever the verdict, `:core` and `:providers` gain no on-device or ML dependency: the `:core` classpath allowlist and the no-on-device-implementation scan still pass. Agentic on-device stays out of scope.

**Plans:** 11/11 plans complete

Plans:
**Wave 1**

- [x] 13-01-PLAN.md — toolchain proof in the real build (D-02, 0.17.1 then 0.16.1), unpublished `:spike-ondevice` scaffold, APK cost + stdlib fallout rows, D-07 thresholds locked (SPIKE-01)
- [x] 13-02-PLAN.md — SC4 hardening (D-09): module-scoped `verifyNoMlArtifacts`, LiteRT/MediaPipe tokens in the `:core` scan, model/gold hygiene patterns, negative controls (SPIKE-03)

**Wave 2** *(blocked on Wave 1 completion)*

- [x] 13-03-PLAN.md — closed evidence grammar + filter, Wilson/percentile/schema scoring, per-envelope verdict rules, `verify-spike-verdict.sh` (SPIKE-01, SPIKE-02)
- [x] 13-04-PLAN.md — throwaway `ON_DEVICE` provider on the real SingleShot path, Route A / Route B, backend seam, `LiteRtBackend` (SPIKE-01)

**Wave 3** *(blocked on Wave 2 completion)*

- [x] 13-05-PLAN.md — trial path scored against gold labels, small committed EN/ES/negative set, private SB-sized labels (SPIKE-01)
- [x] 13-06-PLAN.md — guarded TESTER runner + fake-adb guard proof, window-grant gate, time-box, pinned model fetch (SPIKE-01)

**Wave 4** *(blocked on Wave 3 completion)*

- [x] 13-07-PLAN.md — on-device measurement ladder: engine rows (GPU, prefill, KV reuse, ResponseFormat), screen/confirm/sustained, early exits (SPIKE-01)

**Wave 5** *(blocked on Wave 4 completion)*

- [x] 13-08-PLAN.md — one granted TESTER window: measure, commit filtered evidence, clean the device (SPIKE-01; not autonomous)

**Wave 6** *(blocked on Wave 5 completion)*

- [x] 13-09-PLAN.md — verdict computed from evidence, relay message to the orchestrator (SPIKE-02; not autonomous: relay checkpoint)

**Wave 7** *(blocked on Wave 6 completion)*

- [x] 13-10-PLAN.md — disposition per D-08: red deletes the spike (SPIKE-03 N/A-deferred), green_defer requests 13.1, green_ship hands off (SPIKE-03)

**Wave 8** *(blocked on Wave 7 completion)*

- [x] 13-11-PLAN.md — conditional: `@Experimental` `:ondevice` on Phase 17 plumbing, only on branch green_ship; otherwise a recorded no-op (SPIKE-03)

**Note**: if the verdict is green and productizing the provider overruns the time-box, insert Phase 13.1 (`/gsd-phase --insert`) and move SPIKE-03 there. The verdict message (SPIKE-02) is never delayed for it.
**Device note**: TESTER only, never the personal phone. Don't overlap with Phase 12's live smoke or Phase 19's Gate-1.
**Research flag**: yes. Open items: MediaPipe LLM Inference vs LiteRT-LM; Gemma license terms for a bundled model; model delivery (APK asset vs download); S22 RAM headroom.

#### Phase 14: LocalGrammar & Bilingual GrammarPack

**Goal**: An app can put a free, offline grammar tier at the head of its ladder. Declared EN and ES phrasings resolve straight to the app's tool call with zero provider calls, and anything the grammar isn't sure of goes to the next tier untouched.
**Contract step**: §6.2 step 8 (V11-01; CT P75 cross-language mechanism, brief §2)
**Depends on**: Phase 12 (zero-call tiers report `providerCallId = null`; the offline-only no-match path ends `Unhandled(cappedByPolicy = true)`)
**Requirements**: GRAM-01, GRAM-02, GRAM-03, GRAM-04, GRAM-05
**Unblocks**: SB 176, CT 75 (grammar)
**Success Criteria** (what must be TRUE):

  1. An app declares a `GrammarPack` with EN and ES rules for one intent: per-language phrasing, typed slots, and number words in both languages. An EN transcript and its ES counterpart, each with a spoken number, resolve to the same tool call with the same typed slot values.
  2. A matching transcript completes with zero provider calls (no-network guard), and its write goes through the same gate → commit → sink path as every tier. A held grammar action is reported held, never success, and its `ExecutedAction.providerCallId` is null.
  3. A transcript no rule matches, or a slot the app's resolver rejects, ends `NoMatch`: the next tier gets the command with carry cleared, and the grammar tier never returns a best-guess match.
  4. A slot's optional `normalize: (raw, language) -> String?` hook sees the raw slot text and the language before resolution, so an app-supplied synonym map (for example CT's cross-language names) changes what resolves. Library code still names no domain (the CLN-02 scan stays green).
  5. The grammar tier declares `NO_PROVIDER` capabilities, so it runs under `offlineOnly` and under any `allowedProviders`. Under offline-only, a no-match ends `Unhandled` with `cappedByPolicy = true` after zero provider calls.

**Plans**: 3/10 plans executed (8 waves; 14-09 is non-autonomous, TESTER-window gated)

Plans:
**Wave 1**

- [x] 14-01-PLAN.md — D-01 StepSubmission move (commit 1), end-to-end tracer (GrammarPack -> LocalGrammarStrategy -> gate/commit/sink, zero provider calls), six grammar trace codes, resolver-rejection slice
- [x] 14-02-PLAN.md — D-12 prep at phase start: window-grant file + relay text, neutral STT prompt list, guarded capture runner + offline guard proof, RAE check of ES number rules (no Gradle, no device)

**Wave 2** *(blocked on Wave 1 completion)*

- [x] 14-03-PLAN.md — TDD: strict EN/ES number words and digit forms, digit-grouping matrix, independent round-trip 0..999,999
- [ ] 14-04-PLAN.md — Phrasing: D-11 text fold/tokenizer, D-03 template mini-syntax + sub-rules, anchored enumerate-all-parses matcher, fillers, template validation

**Wave 3** *(blocked on Wave 2 completion)*

- [ ] 14-05-PLAN.md — Typed slots (integer, decimal, choice, text) and slot validation; SC-1 bilingual proof

**Wave 4** *(blocked on Wave 3 completion)*

- [ ] 14-06-PLAN.md — Never guesses: ambiguity self-check, D-10 label table + tryOtherLanguage + agreement, derived cap, near-miss corpus, mutation property

**Wave 5** *(blocked on Wave 4 completion)*

- [ ] 14-07-PLAN.md — normalize hook (D-09), terminal intents (D-06), verdict pass-through + held (D-02, SC-2), policy proofs (SC-5), redaction sweep

**Wave 6** *(blocked on Wave 5 completion)*

- [ ] 14-08-PLAN.md — API.md rows, frozen-surface review vs the real Metalava dump, open items for the orchestrator, full phase gate

**Wave 7** *(blocked on Wave 6 completion)*

- [ ] 14-09-PLAN.md — D-12 TESTER capture under a relayed window (or A12 deferral) via an opt-in :sample androidTest tool

**Wave 8** *(blocked on Wave 7 completion)*

- [ ] 14-10-PLAN.md — Fold captured recognizer forms back into internal aliases with GrammarSttFixturesTest, or record the deferred obligation

**Research flag**: yes. Open items: the DSL shape; ES number words (compound forms such as "veintiuno", "ciento y"); how strict matching stays while still never guessing.

#### Phase 15: PlanThenExecute Strategy

**Goal**: An app can handle a lookup-free multi-step command with one planning call. The engine runs the planned steps in order through the gate, lets later steps use earlier steps' write results, replans at most once, and never escalates after something committed.
**Contract step**: §6.2 step 9 (V11-02; GATE-07; contract §4: Plan is for lookup-free commands)
**Depends on**: Phase 12 (`onFailed` hook shape, `ReasoningMode` knob, `ExecutedAction.providerCallId`)
**Requirements**: PLAN-01, PLAN-02, PLAN-03, PLAN-04, PLAN-05
**Unblocks**: SB 177 (PlanThenExecute + step-output binding)
**Success Criteria** (what must be TRUE):

  1. A `PlanThenExecuteStrategy` tier makes one model call that returns a plan of steps over the app's `ToolExecutor`, then runs the steps in order. Every mutating step goes through the gate, and a held step is reported held.
  2. A later step that references an earlier step's write output (`ExecutedAction.targetIds`, for example create then tag the new id) receives the real id. A binding that doesn't resolve fails that step.
  3. A failed step (an unresolved binding included) triggers at most one replan call, then `Escalate`. A step that needs a lookup result escalates instead of planning (contract §4), and the trace shows exactly how many model calls ran.
  4. Once any step has committed, a later failure or escalation ends as a partial `Completed` with `escalation_suppressed`, and no later tier runs. A Plan-specific test proves it (escalate after step 1 committed → suppressed).
  5. `PlanThenExecuteStrategy.Builder.onFailed` behaves exactly like SingleShot's from Phase 12: it fires for provider failures only and defaults to `Failed(reason, details)`.

**Plans**: TBD
**Research flag**: yes. Open items: the plan schema and binding syntax; how a step that "needs a lookup" is detected before any step runs.

#### Phase 16: Start-Tier Selection

**Goal**: An app can decide where each command's LLM walk starts, with its own picker or the engine's opt-in cheap-model Router. Grammar stays a free pre-pass, picker mistakes fall back to Linear loudly, and offline-only commands never pay for a router call.
**Contract step**: §6.2 step 10 (V11-03); brief §6 drift 3 (`TierSelector` is closed at v1.0.1, so the fix is the additive `TierSelector.Custom`)
**Depends on**: Phase 12 (trace codes, `cappedByPolicy`), Phase 14 (only for the real-grammar pre-pass proof; the rest can start against a fake `NO_PROVIDER` head tier)
**Requirements**: ROUT-01, ROUT-02, ROUT-03, ROUT-04, ROUT-05
**Unblocks**: SB 177 (SB's own Router on `StartTierPicker`), CT 75 (settings)
**Success Criteria** (what must be TRUE):

  1. An app passes `TierSelector.Custom(picker)` with its own `StartTierPicker`. The suspend picker sees the input and the eligible LLM tier ids, the walk starts at the tier it returns, and any model call it makes through `PickContext` appears in the trace and counts against the run's token budget.
  2. A zero-call tier at the ladder head (the grammar tier) always runs first as a free pre-pass. The picker runs only if that tier hands the command over, and its eligible list never contains a zero-call tier.
  3. A picker that returns null, returns an ineligible id or throws sends the walk down Linear with a `router_fallback` trace code. The command never fails because of the picker; cancellation still propagates.
  4. When policy leaves no eligible LLM tier (for example offline-only), the picker is never called and no router model call is made. The walk records `router_fallback` and proceeds as Linear (SB condition).
  5. `TierSelector.Router(...)` is built on the same seam and is off by default: an app that doesn't opt in walks exactly as v1.0's Linear. When it's on, telemetry reports the tiers it saved versus a Linear walk.

**Plans**: TBD
**Research flag**: medium. Open items: the Router's classifier prompt and default cheap model (from policy, never hard-coded); how "tiers saved versus Linear" is counted without running Linear.

#### Phase 17: Run-Level Undo

**Goal**: Any app, voice or not, can undo everything one command did. A standalone journal restores each touched entity safely and refuses loudly rather than clobber a later change, and the engine pipeline feeds it every command's commits so an app can offer "Undo all (N)".
**Contract step**: A18 (a new §6.2 step before `:voice-adapter`; E7 coordinate `voice-action-engine-undo`; the §6.2 renumber erratum goes through the orchestrator)
**Depends on**: Phase 12 (pipeline integration only; the standalone module, UNDO-01..03, has no dependency and can start any time)
**Requirements**: UNDO-01, UNDO-02, UNDO-03, UNDO-04
**Unblocks**: SB 178 (run-undo rework onto entity adapters; `ReminderArmer` becomes a compensator), CT "Undo all (N)"
**Success Criteria** (what must be TRUE):

  1. `:undo` publishes as `com.github.Ygaray.voice-action-engine:voice-action-engine-undo` with no dependency at all, not even `:core` (the module-graph gate proves it). A non-voice JVM test app journals and undoes a change using `:undo` alone.
  2. Before a mutation, the before-state of every touched entity is captured through one app adapter per entity type (read, write back, re-insert if deleted). Undo restores the snapshots in reverse order, and out-of-database side effects (alarms, notifications, files) are reversed through explicitly registered compensators.
  3. An unchanged-since-commit check runs before every restore. If an entity changed after the command, undo refuses loudly for it and never overwrites it. The result either reports complete or lists exactly what it couldn't restore, never a silent partial.
  4. Wired into the pipeline, every committed action of a command is journaled under its `runId`, so an app can offer "Undo all (N)" for the whole command, entangled actions included. Grouping follows A18: entity footprints decide which actions are isolated.

**Plans**: TBD
**Research flag**: yes. Open items: the journal/memento API; footprint and entanglement computation; where the pipeline hook journals (beside `CommitSink`); how `commitHeld` child runs (`parentRunId`) group under "Undo all".

#### Phase 18: Voice Adapter

**Goal**: An app that captures speech with `:stt` can turn a final transcript segment into a `CommandInput` with one call, and `:core` still never depends on another hub.
**Contract step**: §6.2 step 12 (V11-06; L7; `:stt` v0.7.0 is already in §11)
**Depends on**: Nothing in v1.1 (needs only v1.0's `CommandInput` and the ledgered `:stt` v0.7.0)
**Requirements**: ADPT-01
**Unblocks**: SB / CT optional glue (replaces each app's hand-written segment → `CommandInput` mapping)
**Success Criteria** (what must be TRUE):

  1. `:voice-adapter` publishes as `com.github.Ygaray.voice-action-engine:voice-action-engine-voice-adapter` and maps an `:stt` v0.7.0 final segment to `CommandInput`, carrying the transcript and the detected language (`en` / `es`, and `null` when stt detected neither; never a guess).
  2. `:core` still depends on no other hub: the module-graph and `:core` classpath-allowlist gates pass, only `:voice-adapter` depends on `:stt`, and an app that doesn't add `:voice-adapter` never pulls `:stt` in.

**Plans**: TBD
**Research flag**: light. Open items: the exact `:stt` v0.7.0 final-segment and language types; whether `:stt`'s published artifact forces `:voice-adapter` to be an Android library.

#### Phase 19: Sample Gate-1 & Docs

**Goal**: The new tiers are proven end to end on the TESTER, and an AI agent can wire every new tier, seam and module from the docs alone.
**Contract step**: v1.1 verification bar (two-gate UAT; A12); doc parity with v1.0's VER-04
**Depends on**: Phase 12, Phase 13 (verdict only), Phase 14, Phase 15, Phase 16, Phase 17, Phase 18
**Requirements**: VER-06, DOC-02
**Unblocks**: nothing directly; it is the gate before the cut every consumer repins to
**Success Criteria** (what must be TRUE):

  1. A `:sample` Gate-1 on the TESTER runs end to end, with a logged evidence line for each: a grammar command under offline-only with zero provider calls; a PlanThenExecute command whose second step uses the first step's new id; a router-chosen start tier visible in the trace; and "Undo all (N)" reverting a whole multi-action command.
  2. README, API.md, INTEGRATION.md and ECOSYSTEM.md cover every Phase 12–18 tier, seam and module (plus the on-device module if Phase 13 shipped one), with the new per-module coordinates, and the doc-coverage check passes.
  3. A fresh agent wires grammar, plan, the router and undo-all into a new app from the docs alone, and the isolated wiring test passes (re-run on the final SHA in Phase 20).

**Plans**: TBD
**Device note**: Gate-1 runs only on the wired TESTER, never the personal phone. Read `~/.claude/context/devices/common.md` first and always use `adb -s`.
**Keys note**: the plan and router legs make live calls with the spend-capped test keys (test-keys workflow, bounded request count).

#### Phase 20: Cut v1.1.0

**Goal**: `v1.1.0` exists as an immutable, JitPack-resolvable tag only because every §11 precondition held, and the orchestrator has the full ledger row so SB and CT can repin.
**Contract step**: §6.2 step 13 / §11 (A4 one tag, A9, A12, A14, E7)
**Depends on**: Phase 19 (its Gate-1 must be green before this phase starts), Phase 13 (verdict dispositioned)
**Requirements**: VER-07
**Unblocks**: SB 176 repin, CT 75 repin (every consumer phase above consumes this tag)
**Success Criteria** (what must be TRUE):

  1. The gated release script runs in order and passes. `./gradlew check` is green. The `apiDump` diff against v1.0.1 is `+`-only for core, providers and keystore, and new `api.txt` files exist for undo and voice-adapter (plus the on-device module if green). `apiCheck` is green, and the seams are checked against the contract (§5.1–5.2, A18). Then come a clean-clone JitPack dry run of the install list (never `:sample`), a leak scan, and a check that the declared version equals the tag. The isolated wiring test re-runs PASS on the final SHA. Only then is `v1.1.0` created and pushed.
  2. JitPack's build log for `v1.1.0` succeeds, and every published coordinate resolves from an empty Gradle cache: `voice-action-engine-{core,providers,keystore,undo,voice-adapter}`, plus the on-device module if green.
  3. The full §11 row is messaged to the orchestrator (A14) and never committed here. `git.create_tag` stays false, so GSD's milestone close creates no stray `v1.1` marker tag.

**Plans**: TBD
**Host note**: five v1.0.1 cut attempts were earlyoom-killed. Run the cut in a quiet window with swap headroom and a single-use Gradle daemon.

## Progress

**Execution Order:**
Phases execute in numeric order: 12 → 13 → 14 → 15 → 16 → 17 → 18 → 19 → 20 (see Dependencies & Parallelism for the safe parallel options). v1.0 rows: [milestones/v1.0-ROADMAP.md](milestones/v1.0-ROADMAP.md).

| Phase | Milestone | Plans Complete | Status | Completed |
|-------|-----------|----------------|--------|-----------|
| 12. Wave-1 Seams & W04 Fix | v1.1 | 8/8 | Complete    | 2026-10-05 |
| 13. On-Device Model Spike | v1.1 | 11/11 | Complete    | 2026-10-06 |
| 14. LocalGrammar & Bilingual GrammarPack | v1.1 | 3/10 | In Progress|  |
| 15. PlanThenExecute Strategy | v1.1 | 0/TBD | Not started | - |
| 16. Start-Tier Selection | v1.1 | 0/TBD | Not started | - |
| 17. Run-Level Undo | v1.1 | 0/TBD | Not started | - |
| 18. Voice Adapter | v1.1 | 0/TBD | Not started | - |
| 19. Sample Gate-1 & Docs | v1.1 | 0/TBD | Not started | - |
| 20. Cut v1.1.0 | v1.1 | 0/TBD | Not started | - |
