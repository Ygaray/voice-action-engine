# Phase 7: SingleShot Strategy - Context

**Gathered:** 2026-09-29
**Status:** Ready for planning (after orchestrator GO — A13)

<domain>
## Phase Boundary

A consumer can handle a command with one forced-tool extraction call that is resolved locally into a (possibly batch) proposal and committed through the gate. This covers CT's real confirm flows.

</domain>

<decisions>
## Implementation Decisions

### resolver-seam
- **D-01 [resolver-seam]:** Extraction wrapper (additively extensible for grammar intent+slots) → prepared ToolSteps or a verdict; the resolver never writes _(source: ai-auto)_ _(provisional — refresh at execution; depends on Phase 2)_

### multi-item
- **D-02 [multi-item]:** One tool call with an items[] array expanded to N mutations (cap/drop rules app-side); first call only; neutral single-call flag encoded as disable_parallel_tool_use:true (Anthropic) + parallel_tool_calls:false (Chat). Confirmed by caltracker-android-9a. _(source: human)_ _(provisional — refresh at execution; depends on Phase 4)_

### user-turn
- **D-03 [user-turn]:** App-controlled user-turn renderer hook with an engine default (neutral date-time + zone + transcript from an injected clock). SB requires byte-for-byte reproduction of its framing; CT needs only semantics: its dynamic "Today's date is <X>" goes through the hook, its static relative-date rule may move into the tool description (Phase 67 target_date behavior preserved). The cached prefix is never touched. Confirmed by secondbrain-2c + caltracker-android-9a. _(source: human)_

### signals
- **D-04 [signals]:** Opaque app context only (GATE-01); CT's per-item confidence / why-flagged ride in it; thresholds stay in CT's resolver+gate. Confirmed by caltracker-android-9a. _(source: human)_ _(provisional — refresh at execution; depends on Phase 2)_

### batch-gate
- **D-05 [batch-gate]:** Gate decides once per proposal: Hold only for a weak single or any 2+ batch per the app's policy; a high-confidence single is Admitted and commits in the ORIGINAL run (CT auto-log). Confirmed by caltracker-android-9a. _(source: human)_ _(provisional — refresh at execution; depends on Phase 2)_

### amend
- **D-06 [amend]:** Replacement mutation list (edited qty, swapped match, changed shared date, dropped rows); no re-gate; per-item isolation + per-item results; idempotent handle. Confirmed by caltracker-android-9a. _(source: human)_ _(provisional — refresh at execution; depends on Phase 2)_

### unmatched
- **D-07 [unmatched]:** Propose with an unresolved target (CT's ItemCorrectionDropdown recovery); still-unresolved rows skipped in the app's apply. Resolver returns NoMatch only when nothing is proposable (CT maps that onto its Unavailable); a model refusal stays a separate Failed(REFUSAL). Confirmed by caltracker-android-9a. _(source: human)_

### post-commit-edit
- **D-08 [post-commit-edit]:** Stays app-side in v1.0 (CT's updateWithItemSwap path), not through the engine. Confirmed by caltracker-android-9a. _(source: human)_

### schema-validate
- **D-09 [schema-validate]:** Pass through (keeps CT's "one bad item doesn't poison the batch"); engine-side validation only where PROV-12 requires it for non-strict calls, reported not fatal _(source: ai-auto)_

### fixtures
- **D-10 [fixtures]:** Neutral-named CT-shaped fixtures (items array + shared date, per-item confidence, catalog with scores and unmatched rows, a failing row, all-strong batch still held, defer-mode threshold gate, recording sink) run through the full pipeline _(source: ai-auto)_

### deferred-runid
- **D-11 [deferred-runid]:** Follow Phase 2's [held-runid] resolution (recommended: new linked run with parentRunId and its own close) _(source: ai-auto)_ _(provisional — refresh at execution; depends on Phase 2)_

### ext-single-call
- **D-12 [ext-single-call]:** Needs external research: disable_parallel_tool_use with forced tool_choice and with auto+strict on Anthropic (can auto still return >1 tool_use?); parallel_tool_calls:false with strict + forced + reasoning_effort none on OpenAI; whether require_parameters makes OpenRouter routing fail on parallel_tool_calls _(source: ai-auto)_


### a19-clarification
- **D-13 [a19-clarification]:** A19: in SingleShot, a (forced or auto) call to a terminal tool skips the OutcomeResolver and ends the run as `Completed(terminalCall = …)`. _(source: human — orchestrator, contract A19)_

### Claude's Discretion
Anything not listed above follows `.planning/research/SUMMARY.md` and the phase's own research; Source `ai-auto` decisions took research's recommendation (orchestrator accepted the auto-resolved remainder).

</decisions>

<canonical_refs>
## Canonical References

- `CROSS-REPO-SCOPE-CONTRACT.md` — §6.2, §5.1–5.2, §10 (A1–A18, E1–E7), §11 (authoritative; never edited here)
- `.planning/ROADMAP.md` — this phase's goal, requirements, success criteria
- `.planning/REQUIREMENTS.md` — requirement text
- `.planning/v1.0-DECISION-MAP.md` — § Phase 7 (source of these decisions)
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

- **resolver-seam (refreshed vs Phase 2):** CONFIRMED vs Phase 2. Map the extraction wrapper onto the existing `ToolStep` (core/commit/ToolStep.kt, sealed) and the `PendingMutation` lists the CommitCoordinator already applies. The resolver returns prepared ToolSteps or a verdict and NEVER writes, because writes go only through CommitCoordinator → PreApplyGate → apply. Keep the wrapper additively extensible (grammar intent+slots in v1.1).
- **multi-item (refreshed vs Phases 4/5):** REFINED vs Phases 4/5. No neutral single-call flag exists yet. Today ChatEncoder sends `parallel_tool_calls:false` only on forced/strict (ChatVendor.parallelToolCallsFalseOnForced: true for OpenAI, false for OpenRouter), and the Anthropic encoder sends no `disable_parallel_tool_use`. Phase 7 adds ONE neutral request flag in the provider request model. Encode it as Anthropic `tool_choice.disable_parallel_tool_use:true` and OpenAI Chat `parallel_tool_calls:false`. For OpenRouter, do NOT send `parallel_tool_calls` (the Phase 5 live probe got a 404, so it stays off). Enforce engine-side instead: take the first tool call only, and trace any extra calls dropped. The items[] array expands to N mutations, with cap/drop rules app-side. Confirmed by caltracker-android-9a.
- **signals (refreshed vs Phase 2):** CONFIRMED vs Phase 2. Opaque app context only (GATE-01): `CommitProposal`/`ToolStep.Finished.context: Any?` already carry it. CT per-item confidence and why-flagged ride in it, and thresholds stay in CT. Confirmed by caltracker-android-9a.
- **batch-gate (refreshed vs Phase 2):** CONFIRMED vs Phase 2. The existing `PreApplyGate` returns `GateDecision.Admit(amended?)` / `Hold(reason, appOutcomeToken?)` once per proposal (CommitCoordinator.kt:111). Hold covers a weak single or any 2+ batch per app policy. A high-confidence single is Admitted and commits in the ORIGINAL run (CT auto-log). The engine adds no batch logic. Confirmed by caltracker-android-9a.
- **amend (refreshed vs Phase 2):** CONFIRMED vs Phase 2. Use the existing `CommandPipeline.commitHeld(held, amended: List<PendingMutation>)` (CommandPipeline.kt:138): a replacement list, no re-gate, idempotent (a second call returns the first outcome). Per-item isolation plus per-item results come from the existing applyAll/ActionEvent path. Add no new amend API. Confirmed by caltracker-android-9a.
- **deferred-runid (refreshed vs Phase 2):** CONFIRMED vs Phase 2 (orchestrator-confirmed A4). `commitHeld` runs as a new linked run carrying the held run as parentRunId (HeldProposal.parentRunId, RunTermination.parentRunId, PipelineEvent.CommandStarted.parentRunId), with its own close; a cancel yields Failed(Other("commit_held_cancelled")). Phase 7 reuses this unchanged.
- **Limits precondition (orchestrator, carried from Phase 11):** wherever SingleShot loops or retries, it must enforce and TEST the 6 / 60000 / 4096 limits from `session.policy` / `session.tokensUsed`, because the v1.0.0 cut is blocked without these tests.
