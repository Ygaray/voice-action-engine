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
- **Pre-dump API review (milestone master, from Phase 3 verifier advisory):** before `apiDump` freezes v1.0.0, revisit `ToolSpec`'s public default-argument constructor (currently guarded only by an ApiShapeTest lint). Default args plus `@JvmOverloads` freeze the overload set forever under §11 rule 2, so decide its final shape before the dump. Also confirm that `Guarded.kt`'s file-level `@file:Suppress` is still the repo's only suppression.
- **Keystore cause-code KDoc (orchestrator, 2026-10-01; required before apiDump):** the 5 `CredentialLookup.Unreadable` cause codes are confirmed as named. Each code needs a KDoc line telling consumers which UX applies. Orchestrator's reading: `key_missing`, `decrypt_failed` and `stored_value_malformed` mean **re-enter key**; `keystore_unavailable` and `storage_unreadable` mean **transient, retry**. Check this mapping against the shipped Phase 6 implementation and report any mismatch to the orchestrator. SB and CT verify against the real API at R2.
  - *Master check at 22d7de5:* the mapping matches the implementation. `key_missing` is produced only by KeyState.KeyMissing (device key gone, e.g. a restore). `decrypt_failed` comes from AEADBadTag/wrong key. `stored_value_malformed` means a bad shape or blank value. `keystore_unavailable` means the KeyStore query failed. `storage_unreadable` means an IOException reading DataStore. However, `KeystoreCauses` (keystore/KeystoreCauses.kt) is **internal**, so its KDoc is invisible to consumers. Put the UX KDoc on a PUBLIC surface: the additive option is a public constants object (e.g. `KeystoreCauseCodes.KEY_MISSING` …), each constant carrying a "re-enter key" or "transient, retry" KDoc line. Make that decision before apiDump.
- **P11 waiver packet (orchestrator, 2026-10-01):** before the cut, collect a Yahir waiver for every uat-pending item that cannot be exercised in v1.0. So far: uat-pending/05 "cache-write via router" (not exercisable: no cache_control is sent to OpenRouter, LATER-02). Route the packet through the orchestrator.
- **Wiring test (from 10-09):** v1.0.0 must not be cut until 10-WIRING-TEST.md records `status: pass` against a SHA that is API-identical to the tag (any later change to README/INTEGRATION/API docs or a public signature means a rerun).
