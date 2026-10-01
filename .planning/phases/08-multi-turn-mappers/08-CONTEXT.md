# Phase 8: Multi-turn Mappers - Context

**Gathered:** 2026-09-29
**Status:** Ready for planning (after orchestrator GO — A13)

<domain>
## Phase Boundary

A multi-turn tool conversation round-trips losslessly between the neutral transcript and each provider dialect, so the agentic loop can run on any cloud provider without breaking thinking blocks or the cache.

</domain>

<decisions>
## Implementation Decisions

### replay-content
- **D-01 [replay-content]:** Anthropic: the raw content array (SB precedent). Chat: an allowlist projection copied as raw sub-elements (role, content, tool_calls, refusal, reasoning_details) — pending research on whether OpenAI rejects response-only fields when echoed _(source: ai-auto)_ _(provisional — refresh at execution; depends on Phase 5)_

### byte-for-byte
- **D-02 [byte-for-byte]:** Canonical re-encoding identity (keeps Phase 3's raw: JsonElement); goldens stored compact _(source: ai-auto)_ _(provisional — refresh at execution; depends on Phase 3)_

### replay-key
- **D-03 [replay-key]:** Snapshotted provider+model (PROV-03); mismatch fails loudly (XCR-02 supersedes ARCHITECTURE's "other provider → rebuild") _(source: ai-auto)_

### coverage
- **D-04 [coverage]:** Enforce in the mapper (an engine-side invariant failure beats an opaque HttpError); order = emission order on both dialects _(source: ai-auto)_

### chat-error
- **D-05 [chat-error]:** {"error": …} JSON wrapper; non-error content (incl. SB's held_for_confirmation JSON) passes through unchanged _(source: ai-auto)_

### empty-args
- **D-06 [empty-args]:** Lenient empty-args normalization; ids never invented (they must match replayed raw); blank/duplicate ids left to Phase 9 whole-turn validation _(source: ai-auto)_

### goldens
- **D-07 [goldens]:** Live synthetic captures as listed, never SB's fixture or SB/CT names; needs Yahir's Anthropic/OpenAI/OpenRouter keys at Phase 8 time Capture tasks are GATED on Yahir's key approval (pending, relayed by the orchestrator). _(source: ai-auto)_ _(provisional — refresh at execution; depends on Phase 5)_

### recorder
- **D-08 [recorder]:** Host-JVM opt-in recorder (reuse Phase 5's capture task), consistent id renaming across turns, thinking text and signatures untouched _(source: ai-auto)_ _(provisional — refresh at execution; depends on Phase 5)_

### conformance
- **D-09 [conformance]:** :providers-local abstract suite (no cross-module test sharing needed) _(source: ai-auto)_

### append-only
- **D-10 [append-only]:** Both assertions (a mapper that rewrites an earlier turn would pass the prefix test but miss the messages cache); Chat sends no cache directive, incl. OpenRouter anthropic/* _(source: ai-auto)_

### ext-replay
- **D-11 [ext-replay]:** Needs external research: whether OpenAI accepts the full echoed message (annotations, refusal:null, audio:null); OpenRouter reasoning/reasoning_details presence with effort none and whether it must be echoed (esp. anthropic/* with signatures); Anthropic preserved-thinking validation sensitivity to re-encoding, redacted_thinking, dropping older-turn thinking, interleaved text; OpenAI tool-message ordering and empty/null content; empty-args forms and blank/duplicate ids seen in practice; response model id vs requested id _(source: ai-auto)_



### r1-verdict
- **D-12 [r1-verdict]:** Multi-turn golden-capture tasks are KEY-GATED until the orchestrator relays Yahir's OK; the rest of the phase executes without them. _(source: human — orchestrator R1 GO-WITH-CHANGES)_

### Claude's Discretion
Anything not listed above follows `.planning/research/SUMMARY.md` and the phase's own research; Source `ai-auto` decisions took research's recommendation (orchestrator accepted the auto-resolved remainder).

</decisions>

<canonical_refs>
## Canonical References

- `CROSS-REPO-SCOPE-CONTRACT.md` — §6.2, §5.1–5.2, §10 (A1–A18, E1–E7), §11 (authoritative; never edited here)
- `.planning/ROADMAP.md` — this phase's goal, requirements, success criteria
- `.planning/REQUIREMENTS.md` — requirement text
- `.planning/v1.0-DECISION-MAP.md` — § Phase 8 (source of these decisions)
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

- **replay-content (refreshed vs Phase 5):** CONFIRMED vs Phase 5, still pending research on one point. Anthropic replays the raw content array (SB precedent). Chat replays an allowlist projection copied as raw sub-elements: role, content, tool_calls, refusal, reasoning_details. Phase 8 research must settle whether OpenAI rejects response-only fields when they are echoed, ideally using the Phase 5 recorded bodies under providers/src/test/resources.
- **byte-for-byte (refreshed vs Phase 3):** CONFIRMED vs Phase 3. `NativeReplay.raw: JsonElement` exists (core/transcript/NativeReplay.kt:21), so identity means canonical re-encoding. Goldens are stored compact.
- **goldens (refreshed):** REFINED. Use live synthetic captures as listed, never SB fixtures and never SB/CT names. Key approval is NO LONGER pending: Yahir standing test-key policy (HANDOFF "Keys: UNBLOCKED 2026-09-30") covers it, and the orchestrator applied that same policy to the Phase 5 D-16 capture. Conditions are the same as D-16: opt-in and outside check, `with-test-keys` only (lands in usage.log), cheapest models (Haiku 4.5, gpt-5.4-mini, a cheap OpenRouter route), a bounded call count stated in the plan, recorded bodies redacted of keys/auth headers/org and request ids, fixtures under test resources and never in logs, and the actual request count plus estimated cost reported in the SUMMARY. A plan checkpoint for this capture should still route through the master, which relays to the orchestrator.
- **recorder (refreshed vs Phase 5):** CONFIRMED vs Phase 5. Reuse the existing opt-in Test tasks `liveAnthropicCapture` and `liveChatCompletionsCapture` (providers/build.gradle.kts:70,85). Extend them for multi-turn recording; do not fork them. Rename ids consistently across turns, and leave thinking text and signatures untouched.
