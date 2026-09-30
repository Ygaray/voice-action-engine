# Phase 2: Core Contract, Pipeline & Commit Seam - Context

**Gathered:** 2026-09-29
**Status:** Ready for planning (after orchestrator GO — A13)

<domain>
## Phase Boundary

A consumer can compose a tier ladder with the DSL and get back a typed outcome that is never thrown, with a full trace attached. Every write goes through one engine-owned gate → commit path, and held actions are reported honestly. All of it is proven with scripted fake strategies, no LLM. This is the keystone: after `v1.0.0` these types can only grow additively.

</domain>

<decisions>
## Implementation Decisions

### outcome
- **D-01 [outcome]:** Separate sealed CommandOutcome (Completed | Failed | Unhandled) whose sealed parent declares ordered executed actions, commits, held proposals and CommandTrace; regular-class leaves _(source: ai-auto)_

### run-close
- **D-02 [run-close]:** Dedicated closed RunTermination type carrying the executed actions; fired from finally under NonCancellable _(source: ai-auto)_

### taxonomy
- **D-03 [taxonomy]:** Non-sealed open interfaces with engine leaves + Other(code); Failed carries httpStatus, provider error.type, requestId, never bodies; engine timeouts via withTimeoutOrNull → TIMEOUT _(source: ai-auto)_

### generics
- **D-04 [generics]:** No generics on public pipeline types; opaque app-implemented objects or Any? (context, snapshot, Hold reason) the app downcasts; Completed carries reply: String? + effects _(source: ai-auto)_

### write-path
- **D-05 [write-path]:** pending.apply() does the write after Admit (SB's Mutate.apply model); CommitSink is the A17 notification/journal seam only _(source: ai-auto)_

### gate
- **D-06 [gate]:** fun interface with sealed GateDecision; proposal = ordered list of pending mutations; any non-cancellation throw → Hold; engine records its fail-closed cause as a trace code, never as a HoldReason (GATE-03) _(source: ai-auto)_

### confirm-helper
- **D-07 [confirm-helper]:** Full SB parity including a post-confirm amend hook (re-snapshot + merge authorization), so SB can move VoiceConfirmGate onto it _(source: ai-auto)_

### batch
- **D-08 [batch]:** Sequential, per-item isolation. A failed apply is reported as an `is_error` action EVENT to CommitSink (never a written placeholder row); siblings still apply; CT keeps its skip-and-count "Logged 2 of 3" UX by counting is_error events. Confirmed by caltracker-android-9a. _(source: human)_

### commit-held
- **D-09 [commit-held]:** Skip the gate, same coordinator path and events, idempotent; a second call is a no-op _(source: ai-auto)_

### held-runid
- **D-10 [held-runid]:** commitHeld opens a NEW runId linked via parentRunId with its own onRunClosed ("no commits after close" stays true); fits CT "Confirm all (N)" -> future "Undo all (N)". Confirmed by caltracker-android-9a. _(source: human)_

### sink-event
- **D-11 [sink-event]:** Per-action event = runId + engine-normalized `kind` (committed|held|preview|is_error) + `appOutcomeToken: String?` that is the app's VERBATIM outcome string (byte-identical, incl. SB's held token and full PREVIEW_ string; SB predicates key on it) + toolName + mutating + targetIds: Map<String,String> + opaque snapshot/context (gate may amend before apply) + engine-assigned position, monotonic across turns in dispatch order (identity; not the tool_use id). An is_error call is never also reported as committed. Confirmed by secondbrain-2c (maps 1:1 onto SB ExecutedToolCall). _(source: human)_

### finished-kind
- **D-12 [finished-kind]:** ToolStep.Finished carries an explicit kind so preview and is_error mutating calls are streamed (GATE-04) _(source: ai-auto)_

### sink-delivery
- **D-13 [sink-delivery]:** Suspend + in-order awaited; onRunClosed from finally under NonCancellable so the cancelled path is delivered exactly once _(source: ai-auto)_

### committed-def
- **D-14 [committed-def]:** Any admitted mutation whose apply ran, including errored applies (may have partially written); held/preview don't count; enforced by the pipeline from coordinator counts, not by trusting the strategy _(source: ai-auto)_

### suppressed-escalation
- **D-15 [suppressed-escalation]:** `Completed` with a public, non-defaultable `partial: Boolean` field (true here) and the suppressed escalation reason in the trace; README must say partial renders as "did X, couldn't finish", never full success. Orchestrator ruling (option A). _(source: human)_

### held-escalation
- **D-16 [held-escalation]:** A HOLD makes a tier terminal (pending proposal = future write, same as a commit for escalation safety). Orchestrator ruling: clarification of A17 / GATE-07, no amendment. _(source: human)_

### dsl
- **D-17 [dsl]:** Required gate and commitSink (no silent auto-commit defaults), build-time misconfiguration throws, only execute() is never-throw; Phase 3 adds providers/selection as optional members _(source: ai-auto)_

### policy
- **D-18 [policy]:** Pipeline pre-check per execute() with static per-tier capability declarations; offlineOnly with no eligible tier → Failed(ProviderUnavailable) with zero strategy executions; Phase 3's ON_DEVICE gate plugs in here _(source: ai-auto)_

### usage-trace
- **D-19 [usage-trace]:** Phase 2 defines Usage {inputUncached, cacheRead, cacheWrite, output} with SB's summed total, CommandSession hook, regular-class trace types with runId = command id; cross-provider parity test completes once Phases 4–5 land _(source: ai-auto)_

### listener
- **D-20 [listener]:** Non-suspending fun interface on the pipeline coroutine; listener throws caught via try/catch (no runCatching) and never abort the command; events carry ids/codes/counts/tool names only _(source: ai-auto)_



### r1-verdict
- **D-21 [r1-verdict]:** A17 payload clarifications are now contract text (1b064a0): verbatim appOutcomeToken alongside kind, monotonic position as identity, committed includes errored applies, high-confidence items commit in the original run. _(source: human — orchestrator R1 GO-WITH-CHANGES)_

### Claude's Discretion
Anything not listed above follows `.planning/research/SUMMARY.md` and the phase's own research; Source `ai-auto` decisions took research's recommendation (orchestrator accepted the auto-resolved remainder).

</decisions>

<canonical_refs>
## Canonical References

- `CROSS-REPO-SCOPE-CONTRACT.md` — §6.2, §5.1–5.2, §10 (A1–A18, E1–E7), §11 (authoritative; never edited here)
- `.planning/ROADMAP.md` — this phase's goal, requirements, success criteria
- `.planning/REQUIREMENTS.md` — requirement text
- `.planning/v1.0-DECISION-MAP.md` — § Phase 2 (source of these decisions)
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
