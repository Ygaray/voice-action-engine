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
- **D-07 [hold]:** Stop at first hold; "worked" = any applied (incl. errored) or HELD, mirroring TierWalk.hasWorked, so a hold never replans. Per RT-01 (below): a hold with NOTHING committed yet returns Escalate(Other("plan_step_held")), which TierWalk records as escalation_suppressed (the held proposal counts as worked); a hold AFTER at least one committed step returns a terminal Completed with partial = true that keeps the committed steps (run-level Undo works) and the held proposal in outcome.held, and NEVER escalates (a later tier would re-run the committed steps); a hold on the last step is one of these two cases, not a separate carve-out; the ids of the steps that never ran are exposed on the outcome. SB 177 answered through RT-01. _(source: ai-auto; amended by RT-01, orchestrator binding ruling 2026-10-06)_
  - **Consumer condition (binding):** SB condition (answer #3): steps committed before the hold stay committed (never escalated after a commit, RT-01); the held step surfaces for the needs-confirmation sheet.

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

## Runtime Decisions

- **[call-id] refreshed (2026-10-06, ai-auto; dependency Phase 12 complete):** Each step carries the submit_plan call id as providerCallId; null only for zero-call tiers, per SEAM-06 as shipped in P12 CommitCoordinator.submit(step, providerCallId: String?). Each step ExecutedAction MUST keep a distinct ordinal/position. That is BINDING from SB (R-v1.1-CONSUMER-ANSWERS row 4): SB keys undo by runId + ordinal, not providerCallId, so the SB 178 undo-grouping question is closed.
- **[builder] refreshed (2026-10-06, ai-auto; dependency Phase 12 complete):** Minimal Builder that reuses P12 internal OutcomeHooks + decideResult (core/strategy/singleshot/SingleShotOutcomes.kt:17/27, internal, so same-module reuse is fine; move them to a shared strategy file only if PlanThenExecute is not in the singleshot package, and do it without touching P14 StepSubmission.kt functions). A truncated or unparseable plan returns StrategyOutcome.Escalate(EscalationReason.MalformedExtraction()), the same shape as SingleShotStrategy.kt:138, so AgenticLoop can still take it rather than failing the command.
- **[binding] mechanics (2026-10-06, ai-auto; plan-stage, inside the human-locked D-04 syntax):** Step ids match ^[A-Za-z][A-Za-z0-9_-]*$ (letter-leading, so a dictated "$5.00" stays literal); a reference is a string whose ENTIRE value is $<id>.<key>, key = one or more non-whitespace characters; object keys and non-strings are never touched; a reference-shaped string naming an id not declared by an earlier step is a static rejection (replan), not a literal; no $$ escape in v1.1. The syntax itself is unchanged. Relayed as OI-2 in 15-SURFACE-REVIEW.md; ACCEPTED as is by the orchestrator on 2026-10-06 (same relay as RT-01), so OI-2 is recorded as accepted, not pending. Plan 15-07's live probe still checks it on the cheap models.
- **[hold] last-step carve-out — SUPERSEDED by RT-01 (2026-10-06; was ai-auto, plan-stage, from the decision-map option text):** There is no last-step carve-out any more. A hold on the last step is RT-01 case 2 when an earlier step committed (terminal Completed, partial = true, commits kept, the held proposal in outcome.held, an empty remaining-step-ids list) or RT-01 case 1 when nothing committed (Escalate(Other("plan_step_held")), which TierWalk suppresses into a partial Completed). The superseded text (a last-step hold ends Completed(null), not partial; a hold with steps remaining escalates and TierWalk suppresses it) was relayed as OI-1; SB 177 objected and the orchestrator adopted SB's rule.
- **RT-01 [hold] binding ruling (2026-10-06; refines D-07, supersedes the [hold] last-step carve-out; source: orchestrator 3b binding ruling via master relay, 2026-10-06; SB 177 objection adopted):** recorded verbatim:
  > BINDING RULING (relayed from the orchestrator, 2026-10-06; SB objected to OI-1 and the orchestrator adopted SB's rule, which matches R-v1.1 Q3 "pre-hold commits stay"):
  > 1. Held step with NOTHING committed yet -> Escalate(Other("plan_step_held")). Unchanged.
  > 2. Held step AFTER >=1 committed step -> terminal Completed carrying the partial (the committed steps, so run-level Undo works) plus outcome.held. NEVER Escalate, because the next tier would re-run committed steps and duplicate side effects.
  > 3. A last-step hold is just case 2 (or case 1 if nothing is committed). It is no longer a separate carve-out.
  > 4. NEW: expose the un-run remaining step ids on the outcome, so a consumer UI can show what didn't run. This is a public surface addition: keep it additive, KDoc it, and include it in the api.txt / Metalava review (15-06's isolated dump and 15-SURFACE-REVIEW.md; also adjust 15-06's "byte-identical" invariants honestly: core/api.txt itself stays untouched until the tag cut, but the surface review must show the new member as a +-only addition; study how the existing outcome shape (CommandOutcome / StrategyOutcome, `held`, `partial`, commits) is declared in core/src/main to pick the minimal additive field, e.g. a defaulted constructor/ctor-compatible property; apply the repo's evolution rules in .claude/CLAUDE.md and the 14-SURFACE-REVIEW.md precedent: no data-class copy/componentN breakage, no default-arg stubs problems; if adding to an existing public class is not binary-additive, pick the safe form and justify).
- **[hold] RT-01 mechanics (2026-10-06, ai-auto; plan-stage, inside RT-01, implemented by plan 15-04):** (a) The field is `CommandOutcome.Completed.remainingStepIds: List<String>`, a new public val on a class whose constructor is already internal, so the only public change is one getter and one property (+-only in the dump; not a data class, no default argument, no stub). It is carried to the outcome through internal-only constructors on StrategyOutcome.Completed and StrategyOutcome.Escalate (the 14-SURFACE-REVIEW Extraction precedent: the public constructors keep their exact signatures, the longer primary constructor is internal); TierWalk passes it on when the tier ends the command (Completed, or a suppressed Escalate) and drops it when the tier hands up. (b) It lists, in plan order, the planned steps the tier never handed to the app's executor because it stopped early. The held step is NOT listed (it is the HeldProposal in outcome.held, and HeldProposal gets no step-id field); a failed step is not listed (it was attempted and is in executed); a step whose reference could not be resolved IS listed (it was never prepared). It is filled on every early stop that ends the command (a hold in either RT-01 case, and a failure after work that TierWalk suppresses), empty when the plan ran to its end, after commitHeld, and for every other tier. (c) Consumer view per case: case 1 (nothing committed) is Completed with partial = true, commits empty, held = [the proposal], the trace attempt escalation_suppressed with suppressedEscalation Other(plan_step_held) and TraceCode.ESCALATION_SUPPRESSED, remainingStepIds = the later steps; case 2 is Completed with partial = true, commits kept, held = [the proposal], the attempt completed (no suppression code), remainingStepIds = the later steps, empty for a last-step hold. (d) A single-step plan whose step is held is case 1, so it now ends Completed with partial = true and a suppressed escalation in the trace, not the clean SingleShot-like Completed(null) of the superseded carve-out. (e) Never logged: CommandOutcome.Completed.toString prints the count only. Relayed in 15-SURFACE-REVIEW.md as OI-1 (resolved); narrowing the fill rule to holds only is a one-line change plus tests, before the tag only.
