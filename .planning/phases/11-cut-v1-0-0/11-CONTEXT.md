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
- **P11 waiver packet (orchestrator, 2026-10-01):** before the cut, collect a Yahir waiver for every uat-pending item that cannot be exercised in v1.0. So far: (C4) uat-pending/05 "cache-write via router" (not exercisable: no cache_control is sent to OpenRouter, LATER-02); (C5) G1-09 OpenRouter EDIT omitted-optional = INCONCLUSIVE(model_filled_optional), accepted by evidence by the orchestrator 2026-10-01 (the shared ChatCompletionsProvider/ChatVendor decoder passed the live OpenAI check, and host tests prove no default-filling). Record it as accepted, NOT as a PASS. Phase 10 Gate-1 = 13/14 pass + 1 accepted. Route the packet through the orchestrator.
- **Wiring test (from 10-09):** v1.0.0 must not be cut until 10-WIRING-TEST.md records `status: pass` against a SHA that is API-identical to the tag (any later change to README/INTEGRATION/API docs or a public signature means a rerun).
- **Wiring-test rerun isolation (orchestrator, 2026-10-01):** the first wiring run (338d85ffa3) was contaminated: an in-session subagent had this repo's `.claude/CLAUDE.md` auto-loaded and took the datastore version from it. The pre-cut rerun MUST run the fresh agent in a scratch dir outside the VAE repo, with no CLAUDE.md or .planning reachable: only the public docs plus the JitPack coordinate. A pass counts only if the build goes green from the docs alone. Mechanism: the master runs a separate headless process with cwd = the prepared workspace (`cd <wiring dir> && claude -p --no-session-persistence "<TASK.md>"`, ideally with `--bare` if API-key auth is available), NOT an in-session Agent, so project CLAUDE.md and auto-memory are not discovered. Check that its CONSULTED.md lists only workspace files, and that no answer cites anything outside the docs.
- **Ordering consequence (master, 2026-10-01):** the pre-dump API items (ToolSpec constructor shape, the public KeystoreCauseCodes constants and their UX KDoc) and the optional doc touch (3 wiring stumbles) all CHANGE the public API and/or docs. That voids the 36c578f464 wiring pass. Order: (1) make all API and doc changes; (2) `./gradlew check` green plus API-surface review; (3) push, JitPack-probe the new SHA, then run the ISOLATED wiring rerun on it (the master dispatches it; executors return needs_human with the SHA); (4) apiDump/Metalava baseline on the final SHA; (5) the waiver packet answered by Yahir via the orchestrator; (6) only then cut and push the `v1.0.0` tag, confirm JitPack builds every module at v1.0.0, and message the orchestrator the full §11 row (never commit it to §11). The tag SHA must be API-identical to the passing wiring SHA.
