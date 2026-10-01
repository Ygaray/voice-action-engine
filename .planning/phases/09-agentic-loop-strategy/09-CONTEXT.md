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


### a19-clarification
- **D-13 [a19-clarification]:** A19: in the agentic loop, a terminal-tool call ends the run after the turn's earlier calls dispatch in order (strike and gate rules unchanged); no tool_result is sent and no further turn starts; commits/held carried; the tier is terminal. Named tests for: terminal-only turn, terminal after a committing call, terminal alongside a held call. _(source: human — orchestrator, contract A19)_

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

## Runtime Decisions

- **userTurn carry (orchestrator, 2026-10-01; Phase 7 seam sign-off):** `AgenticLoopStrategy` MUST accept the same `userTurn: UserTurnRenderer` seam that SingleShot ships (core/strategy/UserTurn.kt: `UserTurnRenderer`, engine-built `UserTurnContext(input, dateTime, carry)`, `UserTurnRenderer.standard()`). SB's byte-exact user-turn framing need is on the agentic path, so Phase 9's plan must state this explicitly. It must also reuse `ToolSpecProvider`/`ToolingSnapshot` (with singleShotTool = null) and keep system and tools as the invariant cached prefix.
- **Limits precondition (orchestrator, carried from Phase 11):** AgenticLoop must enforce AND test the 6 / 60000 / 4096 limits (maxIterations / token ceiling / per-turn tokens) from `session.policy`/`session.tokensUsed`, mirroring Phase 7's SingleShotLimitsTest. The v1.0.0 cut is blocked without them.
- **Phase 2 security O-1 (carried):** `CommitCoordinator.applyAll` has no per-item cancellation check. Assess it here, because a multi-step agentic run is where a mid-batch cancel matters.

- **user-turn (refreshed vs Phase 7):** CONFIRMED vs Phase 7 (orchestrator carry, already in Runtime Decisions). AgenticLoopStrategy takes the same `userTurn: UserTurnRenderer` (core/strategy/UserTurn.kt). SB passes its own renderer that reproduces `Current local date-time: ${localDateTime} (${zoneId})\n\nVoice command: ${transcript}` byte-for-byte. Add a test with an SB-shaped renderer asserting exact bytes on the wire for both Anthropic and Chat.
- **mutating-flag (refreshed vs Phase 2):** CONFIRMED vs Phase 2. `ToolSpec.mutating` and `terminal` already exist (core/strategy/ToolSpec.kt; terminal+mutating is rejected), and so do `ToolStep.Finished(toolName, kind: FinishedKind, result)`. The loop uses them. A read tool returning a Mutation never reaches the gate (typed error). An unknown tool gives is_error without calling the executor. GATE-04 preview reporting reads `mutating`. Add no new ToolSpec member.
- **state-home (refreshed vs Phase 2):** CONFIRMED vs Phase 2 (orchestrator-confirmed O2). State is coordinator-owned (CommitCoordinator). The loop never catches CancellationException. A gate suspended at cancel leaves no record. A cancel during apply() records is_error with applied=true, then rethrows. Also assess Phase 2 security O-1 (applyAll has no per-item cancellation check), already in Runtime Decisions.
- **stop-leaves (refreshed vs Phase 2):** ALREADY SATISFIED by Phase 2: every leaf exists in core/failure/FailureReason.kt (HttpError, Network, MalformedResponse, MaxTokens, Refusal, PauseTurn, ContextWindowExceeded, UnknownStop, ToolFailure). The loop maps to each one distinctly and never collapses MAX_TOKENS or REFUSAL into UnknownStop. Add a test per leaf. Confirmed by secondbrain-2c.
- **dispatch-key (refreshed vs Phases 5/8):** CONFIRMED vs Phases 5/8. The neutral `StopReason` value class is in core/transcript/ModelResponse.kt (END_TURN, TOOL_USE, ...). Phase 5/8 mappers decide whether a turn is a tool turn by the presence of tool_calls. The loop dispatches on the neutral reason plus the presence of tool calls. Add a guard test for "stop + tool_calls".
- **Phase 8 carry-forwards (live-capture findings):** (1) the loop must use `ToolChoice.Auto` (Sonnet 5.5 rejects forced tool use, and the loop has to let the model choose); (2) validate the whole turn so duplicate tool-call ids across turns are caught; (3) a ladder that escalates to a different model must drop native replays (thinking/reasoning_details) from the history it hands up. Phase 8 live facts: OpenRouter accepts an echoed tool_calls[].index, reasoning_details echo is accepted, OpenAI accepts an unfiltered echo, Sonnet 5.5 returns signed reasoning with no thinking param, and Haiku reads its cache on turn 2.
- **StrategyOutcome.Completed.partial (post-Phase-7 fix 277a747):** reuse it. If the loop ends with work done but unfinished (for example the iteration cap after a commit), report partial=true; do not add a parallel flag.
