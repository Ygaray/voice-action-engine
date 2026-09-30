# Phase 3: Transcript Types, ProviderRouter & On-Device Gate - Context

**Gathered:** 2026-09-29
**Status:** Ready for planning (after orchestrator GO — A13)

<domain>
## Phase Boundary

The engine picks the provider, model and key for each command through app seams, holds neutral multi-turn transcript types in `:core`, and offers an `ON_DEVICE` slot that falls back cleanly on devices without on-device support. All pure JVM, no HTTP.

</domain>

<decisions>
## Implementation Decisions

### replay
- **D-01 [replay]:** Nullable NativeReplay(provider: ProviderId, model, raw: JsonElement) with redacted toString; neutral parts only Text + ToolCall; thinking lives only in raw; rebuild from neutral parts only when null or stamped for a different provider/model _(source: ai-auto)_

### credential
- **D-02 [credential]:** Separate CredentialSource with a typed result, so a lost Keystore key surfaces as "re-enter your key", not "not configured"; KeystoreCredentialSource implements it _(source: ai-auto)_ _(provisional — refresh at execution; depends on Phase 2)_

### snapshot
- **D-03 [snapshot]:** Once per command per tier, lazily on first use, frozen in a bound handle (lets an app use a cheap model for SingleShot and a strong one for Agentic; mid-command settings changes don't apply) _(source: ai-auto)_

### defaults
- **D-04 [defaults]:** No default model id in the engine; README states it explicitly; model catalog/defaults stay app-owned _(source: ai-auto)_

### capabilities
- **D-05 [capabilities]:** Keyed by (ProviderId, exact id); each provider contributes defaults incl. an unknown-id default; app override layer; router checks request needs vs capabilities before any call; Phase 3 ships type/lookup/override/check with fake entries, verified entries land in Phases 4–5 _(source: ai-auto)_

### ondevice
- **D-06 [ondevice]:** Runtime availability query mirroring ML Kit statuses (v1.0 always Unavailable("not_implemented")); fallback carried in the per-command selection, itself subject to offlineOnly/allowedProviders; trace records fallbackFrom; the Phase 2 offlineOnly check calls this same gate _(source: ai-auto)_ _(provisional — refresh at execution; depends on Phase 2)_

### ondevice-fail
- **D-07 [ondevice-fail]:** Loud `Failed(ProviderUnavailable(ON_DEVICE, code))`; never a silent climb to a cloud tier that spends a key without a declared fallback. Orchestrator ruling (option A). _(source: human)_

### cache-detect
- **D-08 [cache-detect]:** In :core after each response; fires only if the directive's static prefix is on, caching mode ≠ NONE with a known minimum, cacheRead==0 && cacheWrite==0, and the estimated prefix ≥ minimum; unknown minimum = silent _(source: ai-auto)_ _(provisional — refresh at execution; depends on Phase 2)_

### cache-estimate
- **D-09 [cache-estimate]:** Characters of frozen system + canonical tools with a conservative ratio erring silent (A10 fixture ≈ 3 chars/token); calibrate ratio via research _(source: ai-auto)_

### cache-mode
- **D-10 [cache-mode]:** EXPLICIT_BREAKPOINTS fires on read+write==0 any turn; AUTOMATIC only on turn ≥2 of the same handle with cacheRead==0 (never on turn 1, OpenAI never reports writes) _(source: ai-auto)_


### a19-clarification
- **D-11 [a19-clarification]:** A19: `ToolSpec` carries `terminal: Boolean = false`; a terminal tool that is also declared mutating fails at build time. _(source: human — orchestrator, contract A19)_

### Claude's Discretion
Anything not listed above follows `.planning/research/SUMMARY.md` and the phase's own research; Source `ai-auto` decisions took research's recommendation (orchestrator accepted the auto-resolved remainder).

</decisions>

<canonical_refs>
## Canonical References

- `CROSS-REPO-SCOPE-CONTRACT.md` — §6.2, §5.1–5.2, §10 (A1–A18, E1–E7), §11 (authoritative; never edited here)
- `.planning/ROADMAP.md` — this phase's goal, requirements, success criteria
- `.planning/REQUIREMENTS.md` — requirement text
- `.planning/v1.0-DECISION-MAP.md` — § Phase 3 (source of these decisions)
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
