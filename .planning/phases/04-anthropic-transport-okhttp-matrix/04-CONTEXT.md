# Phase 4: Anthropic Transport & OkHttp Matrix - Context

**Gathered:** 2026-09-29
**Status:** Ready for planning (after orchestrator GO — A13)

<domain>
## Phase Boundary

A consumer's commands reach Anthropic over a cache-correct, cancellation-safe transport that runs green on the consumer's own OkHttp version (4.12 or 5.x) and leaks no secret anywhere.

</domain>

<decisions>
## Implementation Decisions

### floor-guard
- **D-01 [floor-guard]:** Add an explicit compile-floor assertion task under check (placed with the Phase 1 matrix plumbing if Phase 1 is still open, else Phase 4); a catalog bump to 5.x must fail the build _(source: ai-auto)_ _(provisional — refresh at execution; depends on Phase 1)_

### io-dispatch
- **D-02 [io-dispatch]:** Inside the transport on an injectable IO dispatcher defaulting to Dispatchers.IO; an OkHttp Response never escapes (avoids NetworkOnMainThreadException from viewModelScope callers) _(source: ai-auto)_

### timeouts
- **D-03 [timeouts]:** newBuilder() from an optional app client, clear interceptors() and networkInterceptors(), set both callTimeout and readTimeout from per-provider/strategy config (default 60 s; SB parity) _(source: ai-auto)_

### timeout-map
- **D-04 [timeout-map]:** OkHttp's own timeouts only (no coroutine withTimeout in the transport, which would surface as cancellation); InterruptedIOException → TIMEOUT, other IOException → NETWORK _(source: ai-auto)_

### retry-rules
- **D-05 [retry-rules]:** One transient retry per logical call, shared with the forced-tool reshape (≤3 HTTP requests); never retry other 4xx; retry lives below the AiProvider seam and each retry is a trace attempt; verify whether retryOnConnectionFailure(false) is needed _(source: ai-auto)_

### forced-400
- **D-06 [forced-400]:** Three-condition match (forced + 400 + message mentions tool_choice), body parsed then discarded; retry with auto + (strict only without optionals) + instruction in the user turn; no memo in v1.0 (verified table covers known models) _(source: ai-auto)_

### no-tool-call
- **D-07 [no-tool-call]:** Transport returns typed NoToolCall (refusal stays REFUSAL); SingleShot (Phase 7) maps NoToolCall → Escalate _(source: ai-auto)_

### error-map
- **D-08 [error-map]:** Status-first table (401/403 AUTH, 402 BILLING, 429 RATE_LIMIT, 529/503 OVERLOADED, 408/504/InterruptedIO TIMEOUT, IO NETWORK, bad 2xx MALFORMED_RESPONSE, non-object input MALFORMED_TOOL_ARGS) plus a ModelNotFound leaf (404) so "bad model id" is distinguishable; rest → Other; always httpStatus + error.type + request-id, never body _(source: ai-auto)_ _(provisional — refresh at execution; depends on Phase 2)_

### canary
- **D-09 [canary]:** Capture + explicit toString checks + error-body echo leg, in :providers/src/test so it runs on all three OkHttp legs; never stringify OkHttp Request/Headers/Response (they print x-api-key in clear on both lines) _(source: ai-auto)_

### test-fakes
- **D-10 [test-fakes]:** java-test-fixtures on :core with the variant explicitly excluded from publication (verified by inspecting the published module/POM), falling back to copies if JitPack still exposes it; confirm "unpublished" satisfies the orchestrator's ruling _(source: ai-auto)_ _(provisional — refresh at execution; depends on Phase 1)_

### encoder-scope
- **D-11 [encoder-scope]:** Encode tool_result + is_error in Phase 4 (needed for TEL-04 SC5); Phase 8 owns round-trip conformance, batching semantics, verbatim replay and goldens _(source: ai-auto)_ _(provisional — refresh at execution; depends on Phase 3)_

### ext-anthropic-errors
- **D-12 [ext-anthropic-errors]:** Needs external research: exact error.type/message for forced tool_choice on the four models; retry-after presence/units on 429/529 and whether spend-cap 429 is distinguishable; request-id header on error responses; strict + auto acceptance on the four models (final proof = Phase 10 live smoke) _(source: ai-auto)_

### ext-okhttp
- **D-13 [ext-okhttp]:** Needs external research: legacy mockwebserver surface and okhttp-jvm variant attributes on 5.5.0 (not cached locally); which failures retryOnConnectionFailure silently retries after a POST is sent _(source: ai-auto)_


### Claude's Discretion
Anything not listed above follows `.planning/research/SUMMARY.md` and the phase's own research; Source `ai-auto` decisions took research's recommendation (orchestrator accepted the auto-resolved remainder).

</decisions>

<canonical_refs>
## Canonical References

- `CROSS-REPO-SCOPE-CONTRACT.md` — §6.2, §5.1–5.2, §10 (A1–A18, E1–E7), §11 (authoritative; never edited here)
- `.planning/ROADMAP.md` — this phase's goal, requirements, success criteria
- `.planning/REQUIREMENTS.md` — requirement text
- `.planning/v1.0-DECISION-MAP.md` — § Phase 4 (source of these decisions)
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
