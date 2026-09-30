# Phase 9: Agentic Loop Strategy - Context

**Gathered:** 2026-09-29
**Status:** Ready for planning (after orchestrator GO — A13)

<domain>
## Phase Boundary

A consumer can run SB's bounded agentic loop on any cloud provider over its own tools. The engine gates every mutating step, and nothing committed is ever hidden behind a failure.

</domain>

<decisions>
## Implementation Decisions

### strike-mid-turn
- **D-01 [strike-mid-turn]:** Verbatim SB: the strike counter is PER TOOL NAME (abort once the SAME tool has is_error'd twice in the run); the remaining calls in that turn still dispatch, the batched results are appended, then the run returns Failed(TOOL_FAILURE) carrying every executed action. Confirmed/corrected by secondbrain-2c. _(source: human)_

### user-turn
- **D-02 [user-turn]:** Same renderer hook as Phase 7; SB requires byte-for-byte reproduction of `Current local date-time: ${localDateTime} (${zoneId})\n\nVoice command: ${transcript}` (SB-confirmed). _(source: human)_ _(provisional — refresh at execution; depends on Phase 7)_

### mutating-flag
- **D-03 [mutating-flag]:** Mutating flag on ToolSpec (enables GATE-04 preview reporting and blocks a read tool that wrongly returns a Mutation from ever reaching the gate); unknown tool → is_error without calling the executor; combine with Phase 2's [finished-kind] _(source: ai-auto)_ _(provisional — refresh at execution; depends on Phase 2)_

### state-home
- **D-04 [state-home]:** Coordinator-owned state; the loop never catches cancellation; a gate suspended at cancel leaves no record (SB contract) _(source: ai-auto)_ _(provisional — refresh at execution; depends on Phase 2)_

### executed-list
- **D-05 [executed-list]:** Mutating calls only; reads appear as tool names in the per-turn trace. Confirmed by secondbrain-2c (every SB consumer filters on `mutating`). _(source: human)_

### reply
- **D-06 [reply]:** First text block (also for a Done after tool rounds); prose-only run stays `Completed(reply)`; reply never in trace/events/toString. Confirmed by secondbrain-2c. _(source: human)_

### budget
- **D-07 [budget]:** Always `Failed(BudgetExceeded(MAX_ITERATIONS | TOKEN_CEILING))` carrying commits, held, errored and preview calls (SB retry-safety reads every mutating non-preview call). Confirmed by secondbrain-2c. _(source: human)_

### stop-leaves
- **D-08 [stop-leaves]:** Every SB reason stays distinguishable as a named leaf (HTTP error with HTTP status, network, malformed response, max-tokens, refusal, pause-turn, context-window-exceeded, unknown-stop, tool-failure); never collapse MAX_TOKENS or REFUSAL into unknown-stop. Confirmed by secondbrain-2c. _(source: human)_ _(provisional — refresh at execution; depends on Phase 2)_

### parallel
- **D-09 [parallel]:** Allow parallel emission with sequential dispatch (Anthropic parity; the confirm mutex assumes sequential dispatch); revisit if strict-with-parallel research says otherwise _(source: ai-auto)_

### turn-validate
- **D-10 [turn-validate]:** Reject the whole turn (SB invariant: no dispatch from a malformed turn) _(source: ai-auto)_

### dispatch-key
- **D-11 [dispatch-key]:** Neutral stop reason, with Phases 5/8 mappers deciding tool-turn-ness by tool_calls presence (consistent with Phase 5's [tool-turn]); a guard test for "stop + tool_calls" _(source: ai-auto)_ _(provisional — refresh at execution; depends on Phase 5)_

### ext-loop
- **D-12 [ext-loop]:** Needs external research: strict enforcement with parallel tool calls; finish_reason "stop" with populated tool_calls on routed upstreams; reasoning context across turns on OpenAI vs OpenRouter reasoning_details; whether a stable OpenRouter session_id is needed for sticky routing/caching _(source: ai-auto)_


### Claude's Discretion
Anything not listed above follows `.planning/research/SUMMARY.md` and the phase's own research; Source `ai-auto` decisions took research's recommendation (orchestrator accepted the auto-resolved remainder).

</decisions>

<canonical_refs>
## Canonical References

- `CROSS-REPO-SCOPE-CONTRACT.md` — §6.2, §5.1–5.2, §10 (A1–A18, E1–E7), §11 (authoritative; never edited here)
- `.planning/ROADMAP.md` — this phase's goal, requirements, success criteria
- `.planning/REQUIREMENTS.md` — requirement text
- `.planning/v1.0-DECISION-MAP.md` — § Phase 9 (source of these decisions)
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
