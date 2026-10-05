# Reconvene brief — voice-action-engine — R-v1.1

**Milestone:** v1.1 Grammar, Plan, Router, Undo, Spike, Adapter → tag `v1.1.0` (contract §6.2 steps 8–13 + A18)
**Contract rev read:** v1.0 + A1–A18 + E1–E7, §11 through `57e373d` · **Base:** `v1.0.1` (b32840e7eb) · **Date:** 2026-10-05
**Peer:** voice-action-engine-6e (formerly -75). Prep only: no new-milestone, plan or tag until the verdict.

Every API sketch below was checked against the code at HEAD (`api.txt` == the v1.0.1 baseline). Every one is **strictly
additive** under Metalava `--check-compatibility:api:released`. Wherever the sketch adds a constructor parameter, the old
JVM constructor is kept explicitly: a Kotlin default argument alone would remove it.

## 1. Requested items: disposition

| Item | Verdict | Additive API sketch (one line) | Serves |
|---|---|---|---|
| **XR-171-03(1)** provider tool-call id on Extraction | **ACCEPT** | `Extraction.callId: String?` (new 3-arg ctor; the 2-arg ctor is kept), plus `ExecutedAction.providerCallId: String?`, null for zero-call tiers (grammar). `AssistantPart.ToolCall.id` already exists, so both SingleShot `:141` and AgenticDispatch `:131` only pass it through. | SB 178 (run-undo / position rework) |
| **XR-172-02** `:keystore` test fixtures / KeyAccess seam | **ACCEPT (narrowed)** | A public `fun interface KeyAccess` + `ApiKeyStore(dataStore, slots, keyAccess)` ctor gated by `@RequiresOptIn(level = ERROR) @DelicateKeyAccess`. A ~10-line software fake goes in INTEGRATION.md instead of a published `:testing` artifact (that is LATER-04 and would need an A7-style amendment). | SB unit tests (non-blocking); CT the same |
| **XR-173-01(a)** SingleShot failure hook | **ACCEPT** | `SingleShotStrategy.Builder.onFailed: suspend (FailureReason, FailureDetails?) -> StrategyOutcome`, default `Failed(reason, details)`. It sits beside the existing `onNoToolCall`/`onRefusal` hooks and is called from `failureOutcome` (`SingleShotOutcomes.kt`) for provider failures only, never for a ceiling, a gate or `strategy_error`. With it, SB can delete `EscalatingSingleShot` and its backlog item 999.121. | SB 173 cleanup → 176/177; CT 75 |
| **XR-173-01(b)** per-strategy thinking knob | **ACCEPT** | A new `ModelRequest.reasoning: ReasoningMode` (`OFF` / `PROVIDER_DEFAULT`, open value class; 8-arg ctor, the 7-arg ctor kept with `OFF`), plus `Builder.reasoning` on SingleShot and Agentic, both defaulting to `OFF`. Encoders: Anthropic omits `thinking` (unchanged); Chat sends `reasoning_effort` per `OpenAiModelRules` (unchanged). The knob pins today's wire default as an explicit, tested contract. No on-wire change at v1.1.0. | SB 173 guard; CT SingleShot+thinking incompatibility (R1) |
| **XR-173-01(c)** `claude-sonnet-5` capability-table id | **ACCEPT** | No API change. Add an internal row to `AnthropicModels`: `claude-sonnet-5` → forced tool choice allowed (live OK 4/4 per SB Gate-1), explicit breakpoints, `minCacheablePrefixTokens` set from Anthropic's docs at plan time. Today the id falls into the unknown default, so the cache diagnostic stays silent for it. | SB curated picker (173+) |
| **XR-173-01(d)** SingleShot prefix below the Haiku cache minimum | **ACCEPT AS INFO** (no engine change) | None. Working as designed: Haiku's minimum is 4096 tokens, and `CacheDetector` already reads `minCacheablePrefixTokens`. One line goes in INTEGRATION.md: "a single-tool SingleShot prefix will not cache on Haiku/OpenAI; that is expected". | none |
| **XR-173-01(e)** TierAttempt carry-presence flag | **ACCEPT** | `TierAttempt.carryIn: Boolean` (presence only, never the content; the ctor is already internal). It is set from `RunSession.carry != null` in `TierWalk.runTier`. | SB 176/177 (lets SB drop the PD-173-18 ledger) |
| **SB-177 (new, BLOCKING)** app-implementable start-tier selection | **ACCEPT** | `fun interface StartTierPicker { suspend fun pick(input: CommandInput, eligible: List<StrategyId>, ctx: PickContext): StrategyId? }` + a new public subclass `TierSelector.Custom(picker)`. `TierSelector`'s ctor stays internal (its `startIndex` is internal and synchronous, so making the base public would not be implementable anyway). The engine's own `TierSelector.Router(...)` is built on the same seam. Shared behavior: (1) a zero-call tier at the ladder head (grammar, `NO_PROVIDER`) always runs first as a free pre-pass, and the picker chooses only among the remaining eligible LLM tiers (V11-03); (2) null, an ineligible id or a throw → Linear, plus a new trace code `router_fallback` (loud, never a failure); (3) a picker's model calls go through `PickContext` so they count in the run budget and the trace. | SB 177 (SB's own Router), CT 75 |
| **XR-175-02(f)** cap-refusal signal on Unhandled | **ACCEPT** | `CommandOutcome.Unhandled.cappedByPolicy: Boolean`, true when `PolicyPreCheck` skipped one or more tiers for `tier_skipped_policy` **and** the walk ended with no tier handling the command (the ctor is already internal). An additive property beats a new `CapRefused` subclass, which would break exhaustive `when` in consumers. | SB 175 cleanup (removes the D-04 three-fact inference); SB 177 router; CT 75 settings |

## 2. VAE's own v1.1 scope

Contract §6.2 steps 8–13 + A18, which REQUIREMENTS lists as V11-01..06:

- **V11-01** `LocalGrammarStrategy` + bilingual EN/ES `GrammarPack` DSL (slots, number words in both languages). → SB 176, CT 75.
- **V11-02** `PlanThenExecuteStrategy` (GATE-07; one optional replan, then `Escalate`). → SB 177; CT N/A or queries.
- **V11-03** `TierSelector.Router` (default off; grammar is a free pre-pass and the Router picks only among the LLM tiers). → SB 177.
- **V11-04** Bundled on-device model **spike** (Gemma-2B-class via MediaPipe/LiteRT on the TESTER: latency, RAM, strict JSON). The verdict goes to the orchestrator. Ships `@Experimental` only if green; it never blocks the tag (L10). → SB 179, CT 75.
- **V11-05** `:undo` module (A18/E7, `voice-action-engine-undo`, depends on nothing) + pipeline integration. → SB 178, CT multi-item "Undo all".
- **V11-06** `:voice-adapter` (`:stt` → `CommandInput`). It is unblocked now, since stt v0.7.0 is in §11.
- **Doc debt:** 3 open v1.0.1 wiring stumbles. These are INTEGRATION §7 (what `ProviderId.toString()` prints), §10 (the `runTest`/JUnit imports), and §5/6 (SingleShot cannot serve reads). The fourth, the ECOSYSTEM matrix, was fixed in `1591a8c`. The wiring test is re-run on the final tag SHA (isolated), as for v1.0.x.

**SB's BLOCKING v1.1 needs, mapped to VAE work:**

| SB need | Covered by | Note |
|---|---|---|
| 176: grammar + `GrammarPack` | phase 14 (V11-01) | |
| 177: PlanThenExecute | phase 15 (V11-02) | |
| 177: step-output binding | phase 15, **accepted** | Later steps may reference an earlier step's **write** output (`targetIds` from its `ExecutedAction`), e.g. create then tag the new id. A step that needs a **lookup** result still escalates (contract §4: Plan is for lookup-free commands). A binding that doesn't resolve fails the step → the one replan → else `Escalate`. |
| 177: "no Escalate after first commit" | **already true at v1.0.x, pipeline-level** | `TierWalk.hasWorked()` (applied + held > 0) turns any later Escalate/NoMatch into a partial `Completed` + `escalation_suppressed`. Plan inherits it with no new API; phase 15 adds a Plan-specific test (escalate after step 1 committed → suppressed). |
| 177: Router seam | phase 16 (`TierSelector.Custom` + `Router`, row above) | |
| 178: `:undo` + runId pipeline | phase 17 (V11-05/A18) | `runId`/`parentRunId` already flow through every `CommitSink` call and outcome (GATE-04/A17); `:undo` journals by runId. |
| 179: spike verdict | phase 13 (V11-04), run early | |

**CT P75 NoMatch→cloud escalation: confirmed covered, already at v1.0.x.** A tier returning `NoMatch` hands over to the next
tier with carry cleared (`TierWalk.startFresh`). A grammar resolver's `Resolution.NoMatch` therefore reaches the cloud SingleShot
with no new API. Two edges CT should know about: (1) under an offline-only / capped policy the cloud tier is skipped by
`tier_skipped_policy`, so the command ends `Unhandled` (with `cappedByPolicy = true` from v1.1); (2) if the grammar tier had
already applied something, the NoMatch is suppressed (above), never escalated.

**CT P75 cross-language mechanism, VAE's position (the owner decides):** the engine stays domain-free, so it ships
**escalate-on-no-match by default**. A grammar slot whose app resolver returns `Resolution.NoMatch` already hands up through the
existing NoMatch semantics, and the LLM tiers do cross-language matching naturally. The additive option is an optional
per-slot `normalize: (raw, language) -> String?` hook on `GrammarPack` slots. CT can plug in a bilingual synonym map
there, and the engine never learns "food". Both work with zero extra engine API beyond that hook. Recommendation: build the hook
(it's cheap), and CT chooses whether to supply a map.

## 3. W04 status

W04 is unchanged: **carry-to-Gate-2 at the v1.0 milestone close** (Yahir, 2026-10-02). The question is whether a real
OpenAI Responses-only 400 (`gpt-6-astra`) body matches `RESPONSES_ENDPOINT_MARKER`. L7 captured `http=400 reason=http_error`,
billed nothing, and the body text wasn't visible on the device (LE-7). The v1.0 close (certify + verify-milestone/Gate-2) **hasn't been
dispatched**. Proposal: run the v1.0 close (W04 plus the SB/CT-pending Gate-2 lines) **before** `/gsd-new-milestone v1.1`, so
v1.1 starts on a closed v1.0. If W04 shows a marker mismatch, the fix is a `v1.0.2` patch (the transport mapping only, no API change).

## 4. Proposed phase list (continuing the numbering)

| Phase | Goal | Items | Consumer phase unblocked |
|---|---|---|---|
| 12 | Wave-1 additive seams: SingleShot `onFailed`, `ReasoningMode` knob, `claude-sonnet-5` row, `TierAttempt.carryIn`, `Unhandled.cappedByPolicy`, `Extraction.callId`/`ExecutedAction.providerCallId`, `:keystore` opt-in `KeyAccess`, the 3 doc stumbles + the (d) note | XR-171-03(1), XR-172-02, XR-173-01 a–e, XR-175-02(f) | SB 176–178 cleanup; CT 75 |
| 13 | On-device model **spike**, time-boxed. It runs early so the verdict reaches SB 179 / CT 75 before they plan. | V11-04 | SB 179, CT 75 (on-device SingleShot) |
| 14 | `LocalGrammarStrategy` + bilingual `GrammarPack` DSL (+ the optional slot `normalize` hook) | V11-01, CT P75 mechanism | SB 176, CT 75 |
| 15 | `PlanThenExecuteStrategy` + write-output step binding + the post-commit suppression test | V11-02 | SB 177 |
| 16 | `TierSelector.Custom` / `StartTierPicker` seam + `TierSelector.Router` (default off) + grammar pre-pass + `router_fallback` + telemetry for the tiers it saves vs Linear | V11-03, SB-177 drift | SB 177, CT 75 settings |
| 17 | `:undo` standalone module + pipeline integration (journal/memento, entity adapters, compensators, refuse-loudly check) | V11-05 / A18 | SB 178, CT "Undo all (N)" |
| 18 | `:voice-adapter` (`:stt` v0.7.0 → `CommandInput`) | V11-06 | SB/CT optional glue |
| 19 | `:sample` Gate-1 for the new tiers on the TESTER + docs (API.md, INTEGRATION.md, ECOSYSTEM.md) | — | — |
| 20 | Cut `v1.1.0`: isolated wiring re-run on the final SHA, `apiDump` diff is `+`-only, JitPack for all 5 modules | §11 | SB 176 repin, CT 75 repin |

## 5. Assumptions about other repos (please confirm or correct)

1. **SB:** `ExecutedAction.providerCallId` (not only `Extraction.callId`) is what 178 needs for its run-undo rework.
2. **SB:** a `Boolean` `cappedByPolicy` is enough. You don't need *which* tier the cap blocked (that is already in the trace as `tier_skipped_policy` per tier).
3. **SB:** an `onFailed` hook that sees `FailureReason` + `FailureDetails` (incl. `httpStatus`) is enough to replace `EscalatingSingleShot` 1:1.
4. **CT:** escalate-on-no-match + the optional slot `normalize` hook covers P75. CT owns the synonym data (if any).
5. **YAT:** none of the VAE items need a YAT change. The YAT asks (XR-172-01, XR-175-02 a–e) are YAT v2.5 scope, independent of these.
6. **stt:** the v0.7.0 `FinalSegment` / language shape is stable for `:voice-adapter`. The v3.2 backlog (pack preflight, `lastFinal` accessor) changes no type `:voice-adapter` reads.

## 6. Contract drift found (code vs contract)

1. **§6.2's step list omits `:undo`.** A18 adds it ("a new §6.2 step, before `:voice-adapter`"), but the step body still lists only 8–13. Proposed fix: an erratum renumbering §6.2 v1.1 steps to include `:undo` (my phase 17).
2. **XR-172-02 overlaps LATER-04** (a published `:testing` module needs an A7-style amendment). The narrowed opt-in seam above avoids the amendment. If Yahir prefers a published fixture artifact instead, that is a new module/coordinate and needs an amendment.
3. **`TierSelector` is closed at v1.0.1** (found by SB): `TierSelector.kt:12` has an `internal constructor()` and an
   internal, synchronous `startIndex(eligible)`. So no app can implement a selector, and the contract's "Router =
   cheap-model classifier" can't be built on the current shape either: it needs `suspend`, the input and a model. The fix is
   the additive `TierSelector.Custom(StartTierPicker)` above. No existing type changes.
4. **§4 "AgenticLoop Offline ❌ (for now)" and the `ON_DEVICE` row:** the S22 TESTER has no AICore, so the spike uses a *bundled* model only (L10 / LATER-06 unchanged). This is not drift, just a reminder that a green spike gives a bundled-model SingleShot/Plan, never Agentic.

## 7. Proposed amendments

1. An erratum for §6.2 v1.1 steps: insert `:undo` (A18) before `:voice-adapter` and renumber.
2. (Only if Yahir picks a published fixture over the opt-in seam for XR-172-02) An A7-style amendment adding a `voice-action-engine-keystore-testing` coordinate.

## 8. Risks and open questions for Yahir

1. **XR-172-02 shape:** an opt-in public `KeyAccess` (recommended: no new module, compile-time opt-in stops accidental production use) or a published test-fixtures artifact (cleaner separation, needs an amendment, and JitPack AAR test-fixtures publishing is unproven)?
2. **Delivery granularity:** consumers wait for one `v1.1.0` (default, per A4). The alternative is a staged `v1.1.0` (seams + grammar + spike verdict), then `v1.2.0` (plan, router, undo, adapter), which would let SB 176 / CT 75 start sooner. The staged option changes A4's "two tags".
3. **Spike risk:** RAM/latency of a ~2B bundled model on the S22 TESTER, and the APK-size cost for consumers if it ships. A red verdict is an expected outcome, and it never blocks the tag.
4. **v1.0 close ordering:** I propose closing v1.0 (W04 Gate-2) before v1.1 starts. That needs Yahir for Gate-2.
5. **Release-cut host OOM:** 5 v1.0.1 cut attempts were earlyoom-killed. The v1.1.0 cut needs a quiet window + swap headroom. That is a scheduling ask, not a scope one.

## 9. Tag / repin intent

- **`v1.1.0`** at phase 20, on green verification after §11 steps 1–4. Five published modules (`core`, `providers`, `keystore`,
  `undo`, `voice-adapter`). The spike ships only as `@Experimental` if green. Rough order: phases 12–13 first (seams + spike
  verdict early), the cut last.
- **Possible `v1.0.2`** before v1.1 only if the W04 Gate-2 finds a marker mismatch.
- I consume nothing new except the already-ledgered `:stt` v0.7.0 for `:voice-adapter`.
