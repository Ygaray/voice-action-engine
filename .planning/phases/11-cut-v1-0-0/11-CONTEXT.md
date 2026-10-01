# Phase 11: Cut v1.0.0 - Context

**Gathered:** 2026-09-29
**Status:** Ready for planning (after orchestrator GO — A13)

<domain>
## Phase Boundary

`v1.0.0` exists as an immutable, JitPack-resolvable tag only because every §11 precondition already held, and the orchestrator has the full ledger row.

</domain>

<decisions>
## Implementation Decisions

### release
- **D-01 [release]:** stt-engine-style gated release script; api.txt committed in the tagged commit; ledger row messaged to yahir-gsd-control-plane-f2, never committed _(source: ai-auto, moved from Phase 10)_

### marker-tag
- **D-02 [marker-tag]:** `git.create_tag: false` stays set through milestone close, so only `v1.0.0` exists (INC-2026-09-30-01). _(source: human — orchestrator ruling)_

### ledger
- **D-03 [ledger]:** After the push and a green JitPack build + clean-cache resolve of all three prefixed per-module coordinates, message the full row to `yahir-gsd-control-plane-f2` (A14); never commit §11; the orchestrator writes registries (LE-5). _(source: human — contract A14)_

### gate
- **D-04 [gate]:** This phase starts only after Phase 10's Gate-1 SELF-UAT is green and committed (§11 step 1). _(source: human — orchestrator ruling)_


### Claude's Discretion
Anything not listed above follows `.planning/research/SUMMARY.md` and the phase's own research; Source `ai-auto` decisions took research's recommendation (orchestrator accepted the auto-resolved remainder).

</decisions>

<canonical_refs>
## Canonical References

- `CROSS-REPO-SCOPE-CONTRACT.md` — §6.2, §5.1–5.2, §10 (A1–A18, E1–E7), §11 (authoritative; never edited here)
- `.planning/ROADMAP.md` — this phase's goal, requirements, success criteria
- `.planning/REQUIREMENTS.md` — requirement text
- `.planning/v1.0-DECISION-MAP.md` — § Phase 11 (source of these decisions)
- `.planning/research/SUMMARY.md` (+ STACK/FEATURES/ARCHITECTURE/PITFALLS.md)
- `.planning/cross-repo/HANDOFF.md` — cross-repo rules, orchestrator, devices

</canonical_refs>

<code_context>
## Existing Code Insights

Greenfield repo; port sources are read-only in SecondBrain (`app/src/main/java/com/example/secondbrain/core/agent/`) and CalTracker (`app/src/main/java/com/caltracker/app/ai/`, `mcp/`, `ui/voice/`). File:line evidence per decision is in the decision map's analyzer sources; concrete reuse is surfaced at plan time.

</code_context>

<specifics>
## Specific Ideas

None beyond the decisions above.

</specifics>

<deferred>
## Deferred Ideas

See REQUIREMENTS.md v2 / LATER items.

</deferred>

## Runtime Decisions

- **Precondition (orchestrator yahir-gsd-control-plane-f2, 2026-09-30, confirming Phase 2 assumption 16):** the v1.0.0 cut MUST NOT proceed until tests exist, and pass, proving the 6 / 60000 / 4096 limits (maxIterations / token ceiling / per-turn tokens) for EVERY looping strategy shipped in v1.0 (Phase 7 SingleShot where it loops, Phase 9 AgenticLoop). The pipeline only exposes these on `session.policy`, and the strategies enforce them. If any are missing, block the cut and report.
- `:keystore` minSdk 35 / compileSdk 36.1 CONFIRMED by the orchestrator (matches SB + CT exactly), so no pre-cut check is needed.
