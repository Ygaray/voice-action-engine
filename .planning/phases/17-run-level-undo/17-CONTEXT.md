# Phase 17: Run-Level Undo - Context

**Gathered:** 2026-10-05
**Status:** Ready for planning
**Discussed via:** `/gsd-discuss-milestone` (mode: mixed)

<domain>
## Phase Boundary

Any app, voice or not, can undo everything one command did. A standalone journal restores each touched entity safely and refuses loudly rather than clobber a later change, and the engine pipeline feeds it every command's commits so an app can offer "Undo all (N)".

**Requirements:** UNDO-01, UNDO-02, UNDO-03, UNDO-04

</domain>

<decisions>
## Implementation Decisions

### bridge — Where does the pipeline→`:undo` bridge (UNDO-04) live?
- **D-01 [bridge]:** App-side glue + the additive `compositeSink` helper; `:undo` has zero edges, `:core` gains none. Confirm with the orchestrator that this satisfies A18's "pipeline integrates :undo" (else SB and CT each hand-write the seal/group logic). _(source: ai-auto)_
  - **Consumer condition (binding):** FINAL (orchestrator + SB + CT, answer #8): app glue + sample + doc snippet + additive `compositeSink`; no 6th module. SB condition: `compositeSink` isolates a throwing child sink from its siblings and from the pipeline. A18 is met by the seam + an end-to-end sample proof.

### grouping — "Undo all (N)" grouping of commitHeld children and clarification replies.
- **D-02 [grouping]:** Additive `ActionEvent.heldRunId` (internal ctor, safe), lands in Phase 17; clarification replies form their own group. N counts applied actions (COMMITTED + IS_ERROR applied=true); pending held shown as pending, not counted. Confirm with SB 178. _(provisional — refresh at execution; depends on Phase 12)_ _(source: ai-auto)_
  - **Consumer condition (binding):** SB condition (answer #5): keep `parentRunId` on the clarification-reply group so a combined undo stays possible later.

### capture — When is the before-state captured?
- **D-03 [capture]:** Capture inside apply (held changes commit against state that moved on; gate-time capture restores stale data). SB's display-only PreMutationSnapshot can't be ported as-is as the memento. _(source: ai-auto)_

### footprint — Footprint source.
- **D-04 [footprint]:** App-declared at seal (SB's footprint has snapshot-only keys; created ids exist only after apply). _(source: ai-auto)_

### journal-scope — What is journaled and what if journaling fails?
- **D-05 [journal-scope]:** Every applied action; journal failure → "Undo all" withheld loudly, never silently N-1. _(source: ai-auto)_

### persistence — Journal persistence in v1.1.
- **D-06 [persistence]:** In-memory + optional store interface, documented "does not survive process death" (YAT UndoHistoryStore is session-scoped too). _(source: ai-auto)_

### refuse — Refuse-loudly check semantics.
- **D-07 [refuse]:** Atomic restoreIf + whole-group verify-then-restore (maps to YAT's `Refused` = nothing written). Single isolated action checks only its own footprint. _(source: ai-auto)_

### result — Result model and ordering.
- **D-08 [result]:** The closed set above (frozen at the tag, so get it right now), DB-first then compensators. Decide YAT `changedItem` mapping (first blocker vs joined list). — **Reversibility:** one-way — the undo result closed set is frozen at the v1.1.0 tag _(source: ai-auto)_

### api-seed — How `:undo` (and later modules) satisfy `verifyApiDumpPresent` before the cut.
- **D-09 [api-seed]:** Header-only seed (gate stays strict; additions stay compat-green); update release-cut.sh:315's api.txt path allowlist and the stale "real tree never receives an api.txt" script headers. Verify Metalava 0.5.1 accepts a header-only baseline on kotlin.jvm in the scaffold plan. _(source: ai-auto)_

### plumbing — Module list plumbing for new modules.
- **D-10 [plumbing]:** Manifest + consistency gate, landed with the `:undo` scaffold; Phases 18 and 13-if-green rebase onto it. `:undo` = kotlin.jvm, JVM 11, explicitApi, stdlib only (no coroutines), explicit artifactId `voice-action-engine-undo`. _(source: ai-auto)_

### Claude's Discretion
Areas marked `ai-auto` took research's recommendation without operator review; the planner may refine mechanics within the stated decision but must not reverse it without a new discuss pass.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Milestone decisions
- `.planning/v1.1-DECISION-MAP.md` § Phase 17 — source of every decision above (options, recommendation, provisional flags)
- `.planning/cross-repo/R-v1.1-CONSUMER-ANSWERS.md` — SB/CT/stt/orchestrator answers and binding conditions

### Scope
- `.planning/ROADMAP.md` § Phase 17 — goal, success criteria
- `.planning/REQUIREMENTS.md` — UNDO-01, UNDO-02, UNDO-03, UNDO-04
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

No specific requirements beyond the decisions above — open to standard approaches.

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 17-run-level-undo*
