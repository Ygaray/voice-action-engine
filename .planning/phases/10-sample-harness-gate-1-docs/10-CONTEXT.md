# Phase 10: Sample Harness, Gate-1 & Docs - Context

**Gathered:** 2026-09-29
**Status:** Ready for planning (after orchestrator GO — A13)

<domain>
## Phase Boundary

The engine is proven on a real device against SB's real prompt and live on all three cloud providers, and it is documented well enough for an AI agent to wire it from the README alone. (The tag itself is Phase 11, so the immutable cut happens only after this phase's Gate-1 is green — orchestrator ruling.)

</domain>

<decisions>
## Implementation Decisions

### gate1-drive
- **D-01 [gate1-drive]:** Agentic Gate-1 tester drives the real :sample UI (proves VER-01's key-through-:keystore path); executor plans build but never run on devices _(source: ai-auto)_

### evidence
- **D-02 [evidence]:** SB-format per-turn logcat lines plus the extra fields, committed as evidence; only tool names/counts/fingerprints (public repo — never prompt text, args or keys) _(source: ai-auto)_

### gate1-model
- **D-03 [gate1-model]:** Haiku 4.5 (the model behind SB's 7,016), cold-start run, device-hw: tagged criteria, a tolerance band (e.g. ±5%) around 7,016; warm re-runs within 5 min classified as infra re-runs _(source: ai-auto)_

### keys
- **D-04 [keys]:** Owner-only key files outside the repo, passed by reference so the literal never appears in transcripts/logs; verify last-4 and no plaintext on disk; Yahir provides the files before Phase 10 (and earlier for Phase 5/8 golden captures) Mechanism pending Yahir's approval via the orchestrator; plan as if approved, gate the key-dependent tasks. _(source: ai-auto)_

### fixture-load
- **D-05 [fixture-load]:** Manual copy + runtime sha256 check, never a Gradle/config-time check; build Gate-1 from the main checkout or copy the fixture into the worktree _(source: ai-auto)_

### smoke-shape
- **D-06 [smoke-shape]:** Use a small committed synthetic tool pair (reusable in public docs/tests, independent of SB's private fixture) with an optional-field EDIT case; OpenAI gpt-5.4-mini (effort none), OpenRouter openai/gpt-5.4-mini _(source: ai-auto)_

### agent-wire-test
- **D-07 [agent-wire-test]:** Fresh-subagent wiring test against the release SHA (reusing Phase 1's scratch consumer) before the tag; doc set = README + INTEGRATION + API (backup-engine layout) _(source: ai-auto)_

### tag-placement
- **D-08 [tag-placement]:** The v1.0.0 cut moves to a separate Phase 11 "Cut v1.0.0" after this phase's green Gate-1. Orchestrator ruling. _(source: human)_

### release-script
- **D-09 [release-script]:** stt-engine-style gated release script; api.txt committed in the tagged commit; ledger row messaged to yahir-gsd-control-plane-f2, never committed _(source: ai-auto)_

### gsd-marker-tag
- **D-10 [gsd-marker-tag]:** `git.create_tag: false` (set in config) so no stray `v1.0` marker tag (INC-2026-09-30-01). Orchestrator ruling; executed in Phase 11's scope. _(source: human)_

### ext-release
- **D-11 [ext-release]:** Needs external research: Haiku 4.5 minimum cacheable prefix + 5-min TTL + availability and 18-tool prefix ≈ 7,016; gpt-5.4-mini forced tool_choice with effort none; OpenRouter model/provider honoring require_parameters and absent optionals; JitPack build.log success marker, lazy-build timing, and whether per-module versions come from JitPack VERSION or the build file on tag builds _(source: ai-auto)_

### scope-change
- **D-12 [scope-change]:** The tag cut (VER-05, release script, `create_tag:false`) moved to Phase 11; this phase ends at green Gate-1 + docs + the agent-wiring test. _(source: human)_


### Claude's Discretion
Anything not listed above follows `.planning/research/SUMMARY.md` and the phase's own research; Source `ai-auto` decisions took research's recommendation (orchestrator accepted the auto-resolved remainder).

</decisions>

<canonical_refs>
## Canonical References

- `CROSS-REPO-SCOPE-CONTRACT.md` — §6.2, §5.1–5.2, §10 (A1–A18, E1–E7), §11 (authoritative; never edited here)
- `.planning/ROADMAP.md` — this phase's goal, requirements, success criteria
- `.planning/REQUIREMENTS.md` — requirement text
- `.planning/v1.0-DECISION-MAP.md` — § Phase 10 (source of these decisions)
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
