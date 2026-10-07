# Requirements: voice-action-engine

**Defined:** 2026-10-05
**Milestone:** v1.1 Grammar, Plan, Router, Undo, Spike, Adapter → tag `v1.1.0`
**Core Value:** A consumer app can hand the engine a transcript and get back a correct, typed outcome through a tier
ladder it composed itself, with every failure surfaced as a specific, loud reason.
**Scope source:** R-v1.1 GO (Yahir via orchestrator, 2026-10-05), `.planning/cross-repo/RECONVENE-BRIEF-R-v1.1.md`
@2b9f2eb + d9db332; contract §6.2 steps 8–13 + A18/E7 (+ E8 `:undo` step, orchestrator).

Every public-API change here is **strictly additive** against the v1.0.1 `api.txt` (Metalava
`--check-compatibility:api:released`). Existing JVM constructors are kept explicitly; never rely on a Kotlin default argument
alone to keep one.

## v1.1 Requirements

### Wave-1 additive seams (consumer asks)

- [x] **SEAM-01** (XR-173-01a): `SingleShotStrategy.Builder.onFailed: suspend (FailureReason, FailureDetails?) -> StrategyOutcome`, default `Failed(reason, details)`. It is called for provider failures only, never for a ceiling, gate or `strategy_error`. An app maps an HTTP 400 to `Escalate` without a decorator.
- [x] **SEAM-02** (XR-173-01b): there is an explicit per-strategy reasoning knob. `ModelRequest.reasoning: ReasoningMode` (open value class: `OFF`, `PROVIDER_DEFAULT`; the 7-arg ctor is kept, defaulting to `OFF`), plus `Builder.reasoning` on SingleShot and AgenticLoop, both defaulting to `OFF`. Today's wire defaults are pinned by goldens.
- [x] **SEAM-03** (XR-173-01c): the Anthropic capability table has an exact `claude-sonnet-5` row (forced tool choice allowed, explicit breakpoints, `minCacheablePrefixTokens` from Anthropic's docs). Internal only.
- [x] **SEAM-04** (XR-173-01e): `TierAttempt.carryIn: Boolean` reports whether the tier received a carry (presence only, never the content).
- [x] **SEAM-05** (XR-175-02f): `CommandOutcome.Unhandled.cappedByPolicy: Boolean` is true when policy skipped at least one tier (`tier_skipped_policy`, offline-only included) and no tier handled the command. Its KDoc states that coverage.
- [x] **SEAM-06** (XR-171-03(1)): `Extraction.callId: String?` (new 3-arg ctor; the 2-arg ctor is kept) and `ExecutedAction.providerCallId: String?` carry the provider tool-call id. Both are null for zero-call tiers.
- [x] **SEAM-07** (XR-172-02): a public plain interface `KeyAccess` (two members: `existingKey` never creates; `getOrCreateKey` is the only creator) plus an `ApiKeyStore(dataStore, slots, keyAccess)` ctor, both gated by `@RequiresOptIn(level = ERROR) @DelicateKeyAccess`. INTEGRATION.md shows a ~10-line software fake. No new published module.

### Provider fix (W04)

- [x] **PROV-14**: a direct OpenAI Responses-only model (`gpt-6-astra`, `gpt-6.1-sol` families, and every `OpenAiModelRules` model that rejects `"none"`) is never sent `reasoning_effort: "none"`. Proven by an encoder golden.
- [x] **PROV-15**: a 400 with `param=reasoning_effort` and `code=unsupported_value` (the captured W04 body, replayed through MockWebServer) maps to `FailureReason.ModelUnsupported`, not `http_error`.
- [x] **PROV-16**: a live `:sample` smoke call to `gpt-6-astra` under the `supportsTools` override returns the specific typed outcome (evidence logged, test key, bounded spend).

### LocalGrammar (§6.2 step 8, V11-01)

- [x] **GRAM-01**: `LocalGrammarStrategy` resolves a matching transcript to the app's tool call with zero provider calls, then submits it through the session (gate → commit → sink like every tier).
- [x] **GRAM-02**: a bilingual `GrammarPack` DSL lets an app declare EN and ES rules with typed slots, number words in both languages, and per-language phrasing for one intent.
- [x] **GRAM-03**: a transcript no rule matches, or a slot the app resolver rejects, ends `NoMatch`, so the ladder hands over to the next tier with carry cleared. A grammar tier never guesses.
- [x] **GRAM-04**: an optional per-slot `normalize: (raw, language) -> String?` hook lets the app plug in a synonym map (for example CT cross-language food names) without the engine naming any domain.
- [x] **GRAM-05**: the grammar tier declares `NO_PROVIDER` capabilities, so it runs offline-only and under any provider policy.

### PlanThenExecute (§6.2 step 9, V11-02)

- [x] **PLAN-01**: `PlanThenExecuteStrategy` makes one model call that returns a plan of steps over the app's `ToolExecutor`, then runs the steps in order through the gate.
- [x] **PLAN-02**: a later step can reference an earlier step's write output (`ExecutedAction.targetIds`). A binding that doesn't resolve fails that step.
- [x] **PLAN-03**: a failed step triggers at most one replan call, then `Escalate`. A step that needs a lookup result escalates instead of planning (contract §4).
- [x] **PLAN-04**: after the first commit, no escalation happens (`escalation_suppressed` → partial `Completed`). A Plan-specific test proves it.
- [x] **PLAN-05**: `PlanThenExecuteStrategy.Builder.onFailed` has the same hook as SEAM-01.

### Tier selection (§6.2 step 10, V11-03)

- [x] **ROUT-01**: `fun interface StartTierPicker` + a public `TierSelector.Custom(picker)` let an app choose the start tier (suspend, sees the input and the eligible LLM tiers). The picker's model calls go through a `PickContext` that counts toward the run budget and the trace.
- [x] **ROUT-02**: a zero-call tier at the ladder head (grammar) always runs first as a free pre-pass. The picker chooses only among the remaining eligible LLM tiers.
- [x] **ROUT-03**: if the picker returns null, returns an ineligible id or throws, the walk falls back to Linear and records a `router_fallback` trace code. That's never a failure.
- [x] **ROUT-04**: when policy leaves no eligible LLM tier (for example offline-only), the picker is never called and no router model call is made.
- [x] **ROUT-05**: `TierSelector.Router(...)` is the engine's cheap-model classifier, built on the same seam, default off. Telemetry shows the tiers it saved versus Linear.

### On-device spike (§6.2 step 11, V11-04)

- [x] **SPIKE-01**: a bundled Gemma-2B-class model (MediaPipe/LiteRT) is measured on the TESTER: latency, RAM and strict-JSON reliability on SingleShot-shaped prompts.
- [x] **SPIKE-02**: the verdict (green/red, with numbers) is messaged to the orchestrator early, before the SB 179 and CT 75 planning needs it.
- [ ] **SPIKE-03**: if green, the on-device provider ships `@Experimental` in its own module behind the `ON_DEVICE` capability gate. If red, nothing ships and the tag isn't blocked (L10).

### Run-level undo (A18/E7/E8, V11-05)

- [x] **UNDO-01**: a `:undo` module (`voice-action-engine-undo`) depends on nothing, not even `:core`, and a non-voice app can use it alone.
- [x] **UNDO-02**: a journal/memento design with per-entity adapters (read, write back, re-insert if deleted) and explicit compensators for out-of-DB side effects.
- [x] **UNDO-03**: an unchanged-since-commit check runs before every restore. A changed entity makes the undo refuse loudly and never clobber. An undo either completes or reports exactly what it couldn't restore.
- [x] **UNDO-04**: the pipeline journals each command's committed actions by `runId`, so an app can offer "Undo all (N)" for a whole command, entangled actions included.

### Voice adapter (§6.2 step 12, V11-06)

- [x] **ADPT-01**: the `:voice-adapter` module (`voice-action-engine-voice-adapter`) maps an `:stt` v0.7.0 final segment, including its detected language, to `CommandInput`. `:core` still depends on no other hub.

### Docs, sample and release

- [x] **DOC-01**: the 3 open v1.0.1 wiring stumbles are fixed: INTEGRATION §7 (what `ProviderId` prints), §10 (`runTest`/JUnit imports), §5/6 (SingleShot can't serve reads). Plus a note that a single-tool SingleShot prefix won't cache on Haiku/OpenAI.
- [ ] **DOC-02**: README, API.md, INTEGRATION.md and ECOSYSTEM.md cover every new tier, seam and module well enough that an agent can wire them from the docs alone (isolated wiring test PASS on the final SHA).
- [x] **VER-06**: a `:sample` Gate-1 on the TESTER exercises grammar (offline, zero calls), plan, the router and undo-all end to end.
- [ ] **VER-07**: `v1.1.0` is cut only on green verification:
  - the API is strictly additive vs v1.0.1 (`apiDump` diff is `+`-only);
  - seams honor the contract;
  - all published modules (core, providers, keystore, undo, voice-adapter, plus the on-device module if green) build on JitPack;
  - the §11 row is messaged to the orchestrator.

## Future Requirements

- **LATER-01**: moving/tail cache breakpoint on message history.
- **LATER-02**: OpenAI `prompt_cache_key`; OpenRouter `anthropic/*` cache_control passthrough.
- **LATER-03**: OpenAI Responses API dialect.
- **LATER-04**: published `:testing` module with shared fakes (needs an A7-style amendment; SEAM-07 covers the keystore case).
- **LATER-05**: separate/weighted token budgets.
- **LATER-06**: Gemini Nano / AICore `ON_DEVICE` implementation (Pixel 10).

## Out of Scope

| Feature | Reason |
|---------|--------|
| A staged `v1.1.0` + `v1.2.0` | Yahir kept A4's one tag |
| A published keystore test-fixtures artifact | Yahir chose the opt-in seam (SEAM-07) |
| A `CapRefused` outcome subclass | Would break consumers' exhaustive `when`; SEAM-05 is an additive flag |
| Agentic on-device | A green spike gives bundled SingleShot/Plan only (§4) |
| Domain synonym data in the engine | Domain-free; apps supply maps via GRAM-04 |
| Streaming, parallel tool execution, vendor SDKs, DI, library UI | Unchanged from v1.0 |

## Traceability

| Requirement | Phase | Status |
|-------------|-------|--------|
| SEAM-01 | Phase 12 | Complete |
| SEAM-02 | Phase 12 | Complete |
| SEAM-03 | Phase 12 | Complete |
| SEAM-04 | Phase 12 | Complete |
| SEAM-05 | Phase 12 | Complete |
| SEAM-06 | Phase 12 | Complete |
| SEAM-07 | Phase 12 | Complete |
| PROV-14 | Phase 12 | Complete |
| PROV-15 | Phase 12 | Complete |
| PROV-16 | Phase 12 | Complete |
| GRAM-01 | Phase 14 | Complete |
| GRAM-02 | Phase 14 | Complete |
| GRAM-03 | Phase 14 | Complete |
| GRAM-04 | Phase 14 | Complete |
| GRAM-05 | Phase 14 | Complete |
| PLAN-01 | Phase 15 | Complete |
| PLAN-02 | Phase 15 | Complete |
| PLAN-03 | Phase 15 | Complete |
| PLAN-04 | Phase 15 | Complete |
| PLAN-05 | Phase 15 | Complete |
| ROUT-01 | Phase 16 | Complete |
| ROUT-02 | Phase 16 | Complete |
| ROUT-03 | Phase 16 | Complete |
| ROUT-04 | Phase 16 | Complete |
| ROUT-05 | Phase 16 | Complete |
| SPIKE-01 | Phase 13 | Complete |
| SPIKE-02 | Phase 13 | Complete |
| SPIKE-03 | Phase 13 | N/A-deferred (red verdict, Phase 13; L10) |
| UNDO-01 | Phase 17 | Complete |
| UNDO-02 | Phase 17 | Complete |
| UNDO-03 | Phase 17 | Complete |
| UNDO-04 | Phase 17 | Complete |
| ADPT-01 | Phase 18 | Complete |
| DOC-01 | Phase 12 | Complete |
| DOC-02 | Phase 19 | Pending |
| VER-06 | Phase 19 | Complete |
| VER-07 | Phase 20 | Pending |

**Coverage:** 37/37 v1.1 requirements mapped to Phases 12-20 (no orphans, no duplicates).

---
*Requirements defined: 2026-10-05 · Traceability filled by the v1.1 roadmap: 2026-10-05*
