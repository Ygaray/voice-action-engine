# Phase 15: PlanThenExecute Strategy - Context

**Gathered:** 2026-10-05
**Status:** Ready for planning
**Discussed via:** `/gsd-discuss-milestone` (mode: mixed)

<domain>
## Phase Boundary

An app can handle a lookup-free multi-step command with one planning call. The engine runs the planned steps in order through the gate, lets later steps use earlier steps' write results, replans at most once, and never escalates after something committed.

**Requirements:** PLAN-01, PLAN-02, PLAN-03, PLAN-04, PLAN-05

</domain>

<decisions>
## Implementation Decisions

### plan-call — Planning call shape.
- **D-01 [plan-call]:** Forced `submit_plan` + app tools in the request (model sees inputSchemas), SingleShot's request shape; a reshaped answer that calls an app tool directly is a malformed plan. _(source: ai-auto)_

### schema — `submit_plan` schema and reply.
- **D-02 [schema]:** `steps:[{id, tool, arguments}]` + an in-schema needs-lookup escape (see [lookup]), sent non-strict, whole plan validated before step 1, no model-written reply. _(source: ai-auto)_

### call-id — `ExecutedAction.providerCallId` for plan steps.
- **D-03 [call-id]:** The planning call's id (SEAM-06 says null only for zero-call tiers); `position` distinguishes steps. Confirm SB 178 undo grouping. _(provisional — refresh at execution; depends on Phase 12)_ _(source: ai-auto)_
  - **Consumer condition (binding):** SB condition (answer #4): each step's ExecutedAction MUST keep a distinct ordinal/step index (SB keys undo by runId + ordinal, not providerCallId).

### binding — Binding reference syntax (model-facing contract, frozen for consumers' prompts).
- **D-04 [binding]:** Whole-value string `"$<stepId>.<key>"` with stable model-assigned ids, no interpolation, resolved in JsonObject space before prepare; validate with a small bounded live probe on the cheap models. — **Reversibility:** one-way — the `$<stepId>.<key>` syntax is written into consumers' planning prompts and frozen at the v1.1.0 tag _(source: human)_

### bindable — What can be bound?
- **D-05 [bindable]:** COMMITTED only; HELD/PREVIEW/IS_ERROR never bindable; keys are app-owned. _(source: ai-auto)_

### ref-check — When are binding misses replanned?
- **D-06 [ref-check]:** Static → replan; dynamic → no replan (suppressed partial), so SC2/SC3 and SC4 both hold. _(source: ai-auto)_

### hold — What happens on a hold mid-plan, and when is a replan allowed?
- **D-07 [hold]:** Stop at first hold; "worked" = any applied (incl. errored) or HELD, mirroring TierWalk.hasWorked; return Escalate so TierWalk records escalation_suppressed. Confirm with SB 177 (destructive-step confirm). _(source: ai-auto)_
  - **Consumer condition (binding):** SB condition (answer #3): steps committed before the hold stay committed (escalation suppressed); the held step surfaces for the needs-confirmation sheet.

### replan — Replan request shape and limit.
- **D-08 [replan]:** Conversation continuation, identical prefix (prefix byte-comparison test), hard-coded 1. _(source: ai-auto)_

### builder — Plan Builder surface and truncation.
- **D-09 [builder]:** Minimal Builder reusing Phase 12's OutcomeHooks/decideResult; truncated plan → MalformedExtraction → escalate (Agentic can still handle it) rather than failing the command. _(provisional — refresh at execution; depends on Phase 12)_ _(source: ai-auto)_

### lookup — Lookup detection and escape.
- **D-10 [lookup]:** Static pre-execution check with read tools in the enum + in-schema needs-lookup flag; zero side effects before escalating; terminal tools excluded from the enum. _(source: ai-auto)_

### escalation — Escalation reason and carry.
- **D-11 [escalation]:** `Other("plan_needs_lookup")` / `MalformedExtraction`, forward `session.carry` unchanged so SingleShot's carry still reaches Agentic. _(source: ai-auto)_

### budget — Budgets and step cap.
- **D-12 [budget]:** Existing helpers + a Builder `maxSteps` (default ~8); model-call count from TierAttempt.turns. _(source: ai-auto)_

### Claude's Discretion
Areas marked `ai-auto` took research's recommendation without operator review; the planner may refine mechanics within the stated decision but must not reverse it without a new discuss pass.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Milestone decisions
- `.planning/v1.1-DECISION-MAP.md` § Phase 15 — source of every decision above (options, recommendation, provisional flags)
- `.planning/cross-repo/R-v1.1-CONSUMER-ANSWERS.md` — SB/CT/stt/orchestrator answers and binding conditions

### Scope
- `.planning/ROADMAP.md` § Phase 15 — goal, success criteria
- `.planning/REQUIREMENTS.md` — PLAN-01, PLAN-02, PLAN-03, PLAN-04, PLAN-05
- `.planning/PROJECT.md` — constraints (domain-free, additive API, secrets, A1/A7)

### Research
- `.planning/research/SUMMARY.md`, `ARCHITECTURE.md`, `PITFALLS.md`, `FEATURES.md`, `STACK.md` (all under `.planning/research/`) — v1.1 milestone research

</canonical_refs>

<code_context>
## Existing Code Insights

Code-level assets, file:line anchors and integration points are cited inline in the decisions above and in `.planning/research/ARCHITECTURE.md`; the full scout happens at plan time.

### Cross-phase dependencies
Provisional decisions depend on Phase(s) 12 — refresh them against that phase's real output at execution.

</code_context>

<specifics>
## Specific Ideas

Operator-reviewed decision(s) here (source: human) — treat them as locked: [binding].

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 15-planthenexecute-strategy*
