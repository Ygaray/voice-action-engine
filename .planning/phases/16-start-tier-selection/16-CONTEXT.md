# Phase 16: Start-Tier Selection - Context

**Gathered:** 2026-10-05
**Status:** Ready for planning
**Discussed via:** `/gsd-discuss-milestone` (mode: mixed)

<domain>
## Phase Boundary

An app can decide where each command's LLM walk starts, with its own picker or the engine's opt-in cheap-model Router. Grammar stays a free pre-pass, picker mistakes fall back to Linear loudly, and offline-only commands never pay for a router call.

**Requirements:** ROUT-01, ROUT-02, ROUT-03, ROUT-04, ROUT-05

</domain>

<decisions>
## Implementation Decisions

### carry — When the picker jumps past the next tier, does the grammar head's escalation carry pass to the picked tier?
- **D-01 [carry]:** Pre-pass = the whole zero-call head run after policy, through the existing `runTier` (suppression/gate/trace unchanged); its carry passes unchanged to the picked tier. Linear/Fixed keep the v1.0.1 code path; Custom/Router share one internal `PickingSelector` branch; a characterization test pins the v1.0.1 Linear trace before TierWalk.run is edited. _(provisional — refresh at execution; depends on Phase 14)_ _(source: ai-auto)_

### trace — Where the picker's turns appear in the trace.
- **D-02 [trace]:** Separate additive `CommandTrace.selection` property (attempts keeps meaning "one per tier that ran", "handled by" UIs don't see a phantom id); usage sums both. Pseudo-attempt is the fallback if the additive property proves invasive. _(source: ai-auto)_

### pickcontext — PickContext shape and model binding.
- **D-03 [pickcontext]:** Abstract class + internal ctor, selection seam asked for the picker's own id (no default model), build-time id-collision check. SB 177 must map its picker id in its selection source. _(source: ai-auto)_
  - **Consumer condition (binding):** SB confirmed (answer #6): SB maps its picker StrategyId → `claude-haiku-4-5`.

### fallback-codes — When exactly `router_fallback` is recorded.
- **D-04 [fallback-codes]:** The first option (picker run under `guarded`, cancellation propagates). _(source: ai-auto)_

### eligible — Does an on-device-only tier count as an "eligible LLM tier" (offline-only + on-device ready)?
- **D-05 [eligible]:** Picker declares its own providers and is not called when policy forbids all of them — keeps SB's "offline-only never calls the picker" true once an on-device SingleShot exists (Phase 13 green). _(provisional — refresh at execution; depends on Phase 13)_ _(source: ai-auto)_

### single-tier — Exactly one eligible LLM tier.
- **D-06 [single-tier]:** Custom still called (the app may depend on it), engine Router skips (D-ROUTER-SKIP). _(source: ai-auto)_

### timeout — Picker timeout.
- **D-07 [timeout]:** Engine-imposed picker timeout as a new `TierPolicy.Builder` var with a modest default (~2 s), so a stuck picker can't hang a command under the default policy. _(source: ai-auto)_

### router — Engine Router request shape.
- **D-08 [router]:** Forced-enum tool via the selection seam; pin ReasoningMode.OFF (forced tool_choice vs thinking incompatibility); prompt wording researched at plan time. _(provisional — refresh at execution; depends on Phase 12)_ _(source: ai-auto)_

### telemetry — Name and surface of the "tiers saved vs Linear" metric (permanent once tagged).
- **D-09 [telemetry]:** `tiersBypassed` (avoid "skipped", which means policy-dropped and drives cappedByPolicy; avoid "saved", a counterfactual), on the trace selection record + an event. — **Reversibility:** one-way — the `tiersBypassed` name is permanent once tagged _(source: ai-auto)_

### Claude's Discretion
Areas marked `ai-auto` took research's recommendation without operator review; the planner may refine mechanics within the stated decision but must not reverse it without a new discuss pass.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Milestone decisions
- `.planning/v1.1-DECISION-MAP.md` § Phase 16 — source of every decision above (options, recommendation, provisional flags)
- `.planning/cross-repo/R-v1.1-CONSUMER-ANSWERS.md` — SB/CT/stt/orchestrator answers and binding conditions

### Scope
- `.planning/ROADMAP.md` § Phase 16 — goal, success criteria
- `.planning/REQUIREMENTS.md` — ROUT-01, ROUT-02, ROUT-03, ROUT-04, ROUT-05
- `.planning/PROJECT.md` — constraints (domain-free, additive API, secrets, A1/A7)

### Research
- `.planning/research/SUMMARY.md`, `ARCHITECTURE.md`, `PITFALLS.md`, `FEATURES.md`, `STACK.md` (all under `.planning/research/`) — v1.1 milestone research

</canonical_refs>

<code_context>
## Existing Code Insights

Code-level assets, file:line anchors and integration points are cited inline in the decisions above and in `.planning/research/ARCHITECTURE.md`; the full scout happens at plan time.

### Cross-phase dependencies
Provisional decisions depend on Phase(s) 12, 13, 14 — refresh them against that phase's real output at execution.

</code_context>

<specifics>
## Specific Ideas

No specific requirements beyond the decisions above — open to standard approaches.

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 16-start-tier-selection*

## Runtime Decisions

- **[carry] refreshed (2026-10-06, ai-auto; dependency P14 complete):** Unchanged against the real code: the pre-pass is the whole zero-call head (e.g. P14 LocalGrammarStrategy, core/strategy/grammar/) run after policy through the existing private TierWalk.runTier (pipeline/TierWalk.kt:71), with suppression, gate and trace unchanged, and its carry passes unchanged to the picked tier. Linear/Fixed keep the v1.0.1 code path; Custom/Router share one internal PickingSelector branch; a characterization test pins the v1.0.1 Linear trace before TierWalk.run is edited.
- **[eligible] refreshed (2026-10-06, ai-auto; dependency P13 complete, RED):** Keep the rule, but note that P13 came back RED (small and sb both red; nothing shipped, SPIKE-03 N/A-deferred), so no on-device SingleShot exists in v1.1. The picker still declares its own providers and is not called when policy forbids all of them. That is now a defensive guarantee (offline-only never calls the picker, which is SB requirement), tested with a fake ON_DEVICE-only policy rather than a real on-device provider.
- **[router] refreshed (2026-10-06, ai-auto; dependency P12 complete):** Forced-enum tool via the selection seam; pin ReasoningMode.OFF (P12 transcript/ReasoningMode.kt:21, the default) on the router call, because forced tool_choice is incompatible with thinking. Prompt wording researched at plan time.
- **RT-01 [step-id-cap] (2026-10-06, orchestrator 3b pre-tag ruling on P15 IN-05):** Cap PlanThenExecute step ids at 64 chars. A plan with a longer id is a parse failure and goes down the existing malformed-plan path (Escalate MalformedExtraction / NoMatch, as that path does today). Add one test. Fold it into P16 work, before the tag. Separately, P15 OI-1 follow-up is ruled DO NOT NARROW: remainingStepIds stays filled on any non-complete terminal partial (hold or failure).
