# Phase 12: Wave-1 Seams & W04 Fix - Context

**Gathered:** 2026-10-05
**Status:** Ready for planning
**Discussed via:** `/gsd-discuss-milestone` (mode: mixed)

<domain>
## Phase Boundary

Consumers get every additive seam they are blocked on (a provider-failure hook, an explicit reasoning knob, carry / policy-cap / tool-call-id facts on the outcome, opt-in key access for tests), the v1.0.1 wiring stumbles are gone from the docs, and a direct OpenAI Responses-only model fails with the specific `ModelUnsupported` reason instead of a generic `http_error`.

**Requirements:** SEAM-01, SEAM-02, SEAM-03, SEAM-04, SEAM-05, SEAM-06, SEAM-07, PROV-14, PROV-15, PROV-16, DOC-01

</domain>

<decisions>
## Implementation Decisions

### onfailed — Which failures reach `SingleShotStrategy.Builder.onFailed`?
- **D-01 [onfailed]:** The `else` arm only. Binding refusals stay loud per the BoundModel contract (an app's broad `else -> Escalate` must not lift an on-device/NotConfigured refusal to the cloud). A throwing hook → strategy_error like the other hooks. _(source: ai-auto)_

### reasoning-wire — What do `ReasoningMode.OFF` / `PROVIDER_DEFAULT` emit in v1.1?
- **D-02 [reasoning-wire]:** Byte-identical in v1.1, KDoc defines OFF as "no engine-added reasoning request", never "the model does not think" (omitting reasoning_effort 400s gpt-5.4+/gpt-6 tool calls; thinking:disabled 400s Sonnet 5.5). Value class with internal ctor, OFF/PROVIDER_DEFAULT on the companion; explicit 7-arg ModelRequest ctor kept; no default args anywhere (also Extraction); no api.txt edits in Phase 12. _(source: ai-auto)_

### capped — `Unhandled.cappedByPolicy` semantics and KDoc.
- **D-03 [capped]:** The first option (code-backed; meets SB's condition as written). SUMMARY.md:59's "offline doesn't auto-set" is wrong for the code. _(source: ai-auto)_

### call-id — How the provider call id reaches `ExecutedAction.providerCallId`.
- **D-04 [call-id]:** Internal overload + HeldProposal field; one SingleShot call stamps all its actions. Correction to the brief: it is internal plumbing, not a pass-through at SingleShot:141/AgenticDispatch:131. `heldRunId` and other extras stay out of Phase 12. _(source: ai-auto)_
  - **Consumer condition (binding):** Orchestrator accepted the brief correction: providerCallId travels via internal `submit()` plumbing (answers file, correction b).

### w04-wire — W04 wire fix shape.
- **D-05 [w04-wire]:** Targeted deny-list (an allow-table regresses unlisted working ids to 400; existing goldens pin "none" for gpt-6/-sol/-luna). Plus the narrow classifier backstop: 400 + param=reasoning_effort + code=unsupported_value → ModelUnsupported (after quota), replayed via MockWebServer on all three OkHttp legs. Confirm whether gpt-5.x-pro/-codex take tools on Chat at all (maybe refuse pre-call instead). _(source: ai-auto)_
  - **Consumer condition (binding):** Orchestrator accepted that W04 has two internal causes (answers file, correction c).

### prov16 — How PROV-16's live smoke runs.
- **D-06 [prov16]:** Reuse the leg, judge it, and first retarget the runner's PHASE_DIR/decision path off the archived Phase 10 dir. TESTER only, never overlapping Phase 13. _(source: ai-auto)_

### keyaccess — KeyAccess shape and opt-in scope.
- **D-07 [keyaccess]:** Plain interface + opt-in on interface and the one new ctor + negative-compile proof. Correction to the brief/REQUIREMENTS SEAM-07 ("fun interface" can't compile; collapsing to one member breaks read-never-creates) — tell the orchestrator. _(source: ai-auto)_
  - **Consumer condition (binding):** Orchestrator accepted the correction: KeyAccess is a plain `interface`, still opt-in `@DelicateKeyAccess` (answers file, correction a); REQUIREMENTS SEAM-07 wording to follow.

### docs — Where/how DOC-01 lands.
- **D-08 [docs]:** The gated form (verify-docs-coverage.sh C06/C07/C20 would go red otherwise). _(source: ai-auto)_

### Claude's Discretion
Areas marked `ai-auto` took research's recommendation without operator review; the planner may refine mechanics within the stated decision but must not reverse it without a new discuss pass.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Milestone decisions
- `.planning/v1.1-DECISION-MAP.md` § Phase 12 — source of every decision above (options, recommendation, provisional flags)
- `.planning/cross-repo/R-v1.1-CONSUMER-ANSWERS.md` — SB/CT/stt/orchestrator answers and binding conditions

### Scope
- `.planning/ROADMAP.md` § Phase 12 — goal, success criteria
- `.planning/REQUIREMENTS.md` — SEAM-01, SEAM-02, SEAM-03, SEAM-04, SEAM-05, SEAM-06, SEAM-07, PROV-14, PROV-15, PROV-16, DOC-01
- `.planning/PROJECT.md` — constraints (domain-free, additive API, secrets, A1/A7)

### Research
- `.planning/research/SUMMARY.md`, `ARCHITECTURE.md`, `PITFALLS.md`, `FEATURES.md`, `STACK.md` (all under `.planning/research/`) — v1.1 milestone research

</canonical_refs>

<code_context>
## Existing Code Insights

Code-level assets, file:line anchors and integration points are cited inline in the decisions above and in `.planning/research/ARCHITECTURE.md`; the full scout happens at plan time.

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

*Phase: 12-wave-1-seams-w04-fix*
