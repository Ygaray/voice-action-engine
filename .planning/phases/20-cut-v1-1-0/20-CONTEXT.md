# Phase 20: Cut v1.1.0 - Context

**Gathered:** 2026-10-05
**Status:** Ready for planning
**Discussed via:** `/gsd-discuss-milestone` (mode: mixed)

<domain>
## Phase Boundary

`v1.1.0` exists as an immutable, JitPack-resolvable tag only because every §11 precondition held, and the orchestrator has the full ledger row so SB and CT can repin.

**Requirements:** VER-07

</domain>

<decisions>
## Implementation Decisions

### new-module-baseline — How gate 12 treats modules new in v1.1 (undo, voice-adapter, ondevice-if-green).
- **D-01 [new-module-baseline]:** Seeded api.txt + a "new in this release" branch in gate 12; core/providers/keystore keep today's `comm -23` additive check vs v1.0.1 plus executed metalavaCheckCompatibility. _(provisional — refresh at execution; depends on Phase 17)_ _(source: ai-auto)_

### binary-diff — Where the `javap` descriptor diff (catches `$default` ctors Metalava misses) and the seam-conformance review live.
- **D-02 [binary-diff]:** Required pre-preflight evidence file (lighter on the OOM-prone host) + reviewed seam artifact; update the sealed-type allowlist for any v1.1 additions. _(source: ai-auto)_

### tooling-paths — Release tooling broken by the v1.0 archive.
- **D-03 [tooling-paths]:** Retarget to `.planning/releases/v1.1.0/` well before the wiring SHA (gate 7 rejects scripts/ changes after it). Fold into Phase 17's tooling work or an early Phase-20 plan. _(source: ai-auto)_

### module-list — Release scripts and new modules.
- **D-04 [module-list]:** Manifest with a `dependsOnCore` column (gate 15 and jitpack-live-probe require a core dep that `:undo` lacks; api-dump-isolated's package grep can't match `voice-adapter`; dry-run exact-set; gate 7 allowlist; tag message; hygiene loops). _(provisional — refresh at execution; depends on Phase 17)_ _(source: ai-auto)_

### waiver — v1.1.0 waiver packet.
- **D-05 [waiver]:** The packet (gate 8 rejects NO-WAIVERS for a minor); ask Yahir before the quiet window, not inside it. _(source: ai-auto)_

### push — When the local push hold lifts.
- **D-06 [push]:** Push main at the start of Phase 20 (gate 5 requires HEAD == origin/main; wiring rerun needs a JitPack SHA). This is "with v1.1.0" per Yahir's hold — confirm it explicitly. All code/docs/ECOSYSTEM/tooling + final apiDump committed at or before the wiring SHA; only .planning commits after; orchestrator §11 commits frozen during the quiet window. _(source: human)_

### host-oom — Running the cut on the memory-constrained host.
- **D-07 [host-oom]:** Preflight first, then cut (5–6 modules raise the load; v1.0.1 needed 6 attempts). Clean-tree gate already excludes .gsd/, milestone.lock and stage markers — no .gitignore change. _(source: ai-auto)_

### Claude's Discretion
Areas marked `ai-auto` took research's recommendation without operator review; the planner may refine mechanics within the stated decision but must not reverse it without a new discuss pass.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Milestone decisions
- `.planning/v1.1-DECISION-MAP.md` § Phase 20 — source of every decision above (options, recommendation, provisional flags)
- `.planning/cross-repo/R-v1.1-CONSUMER-ANSWERS.md` — SB/CT/stt/orchestrator answers and binding conditions

### Scope
- `.planning/ROADMAP.md` § Phase 20 — goal, success criteria
- `.planning/REQUIREMENTS.md` — VER-07
- `.planning/PROJECT.md` — constraints (domain-free, additive API, secrets, A1/A7)

### Research
- `.planning/research/SUMMARY.md`, `ARCHITECTURE.md`, `PITFALLS.md`, `FEATURES.md`, `STACK.md` (all under `.planning/research/`) — v1.1 milestone research

</canonical_refs>

<code_context>
## Existing Code Insights

Code-level assets, file:line anchors and integration points are cited inline in the decisions above and in `.planning/research/ARCHITECTURE.md`; the full scout happens at plan time.

### Cross-phase dependencies
Provisional decisions depend on Phase(s) 17 — refresh them against that phase's real output at execution.

</code_context>

<specifics>
## Specific Ideas

Operator-reviewed decision(s) here (source: human) — treat them as locked: [push].

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 20-cut-v1-1-0*

## Runtime Decisions

- **RT-01 [dedupe-constants] (2026-10-06, orchestrator 3b pre-tag ruling on P15 IN-02):** Before the cut, dedupe the frozen plan field-name constants duplicated across PlanParse.kt and PlanSchema.kt, but ONLY if it is a trivial one-line cleanup with no API or behavior change; otherwise leave it. It must land before the wiring SHA (D-09: no doc or code edits after it).

- **RT-02 [undo-seed-reds] (2026-10-06, orchestrator 3b):** Phase 20 OWNS the deliberate reds from P17 17-04: release-cut gates 10 and 12 plus selftest step 4 fail for the new undo seed until P20 adds its new-module branch (see 17-SURFACE-REVIEW.md). The P20 plan MUST include a task that adds that branch and a check that gates 10/12 and selftest step 4 are green again before the cut.

- **RT-03 [actionevent-tostring] (2026-10-06, master; P17 deferred obligation IN-04):** Before the tag, settle the ActionEvent.toString() redaction policy (whether parentRunId and heldRunId are printed or redacted) and record why in the KDoc. HeldRunIdTest pins the current behavior, so a policy change must update that test.
- **RT-04 [p17-api-reconcile] (2026-10-06, orchestrator 3b FYIs):** P20 reconciles the keystore api.txt re-dump (+9 KeyAccess lines from P12) and the 2 :undo suppressions (expected 1, P17 OI-5).

- **RT-03 RULED (2026-10-06, orchestrator 3b; IN-04):** REDACT by default. ActionEvent.toString() shows type, ids (runId, parentRunId and heldRunId are ids and may be shown), tier, status and counts. It NEVER shows arg values, utterance text, model output or key material; those render as `<redacted:N chars>` or similar. Any debug accessor must be explicit and opt-in, never toString. Add one test that a sentinel arg value never appears in toString(), and update HeldRunIdTest if the shape changes. This lands in P20, or in P18 if it fits there, before the wiring SHA.
