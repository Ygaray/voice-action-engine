# Phase 6: Keystore - Context

**Gathered:** 2026-09-29
**Status:** Ready for planning (after orchestrator GO — A13)

<domain>
## Phase Boundary

A consumer can keep each provider's BYO API key encrypted on-device in its own DataStore, under its existing aliases so no current user's key is stranded, and feed that key straight into the provider seam.

</domain>

<decisions>
## Implementation Decisions

### slot-validation
- **D-01 [slot-validation]:** KeySlot(provider, alias, ciphertextKey, ivKey) rows, no defaults, validated at construction — a copy-pasted duplicate alias would otherwise let one provider's save clobber another's key _(source: ai-auto)_

### datastore-scope
- **D-02 [datastore-scope]:** api scope — the constructor takes DataStore<Preferences>; :keystore never creates a DataStore on the app's file _(source: ai-auto)_

### unmapped
- **D-03 [unmapped]:** Unmapped read = NotConfigured (router never crashes); unmapped write throws loudly; CT's non-provider secrets (MCP token, OA key) stay in CT _(source: ai-auto)_

### fingerprint
- **D-04 [fingerprint]:** Ready(last4) with the ≤4-chars-reveals-nothing rule; provider display prefix stays in the app UI _(source: ai-auto)_

### decode
- **D-05 [decode]:** java.util.Base64 standard encoder (byte-identical to NO_WRAP), strict decoder with failures → Unreadable; save trims and rejects blank (SB behavior) _(source: ai-auto)_

### compat-test
- **D-06 [compat-test]:** Both legs in Phase 6 so a format regression is caught before SB/CT plan their migrations; the instrumented test uses the literal legacy aliases (app-private keystore, so safe) _(source: ai-auto)_

### unreadable
- **D-07 [unreadable]:** Never auto-clear, reads have no side effects; explicit catch chain (cancellation rethrown; GeneralSecurity/IllegalArgument/Runtime → Unreadable); CT rebuilds its auto-clear banner app-side if wanted _(source: ai-auto)_

### test-seam
- **D-08 [test-seam]:** Key-access seam with shared cipher/Base64/DataStore code so JVM tests exercise the real IV + tag layout with software keys; temp-file PreferenceDataStoreFactory in tests _(source: ai-auto)_

### credential-adapter
- **D-09 [credential-adapter]:** Typed result so a restored-backup user sees "re-enter your key" (aligns with Phase 3's [credential] recommendation) _(source: ai-auto)_ _(provisional — refresh at execution; depends on Phase 3)_

### ext-keystore
- **D-10 [ext-keystore]:** Needs external research: SunJCE AES/GCM random 12-byte IV via cipher.iv on JDK 17; Keystore2 (Android 14+/One UI) transient-failure behavior of key lookup (KeyMissing vs Unreadable) and exceptions for missing vs invalidated keys; how api(datastore) appears in the published POM / api.txt _(source: ai-auto)_


### sb-keystore
- **D-11 [sb-keystore]:** `:keystore` accepts an app-INJECTED `DataStore<Preferences>` (SB hoists `app_preferences` into one Hilt singleton), and a `KeySlot` maps SB's existing alias `secondbrain_anthropic_api_key_v1` plus its ciphertext/IV DataStore keys with no copy/migration; verify ct/IV layout against SB `core/agent/KeystoreCrypto.kt:98` during this phase. SB R1 item via orchestrator. _(source: human — orchestrator)_

### Claude's Discretion
Anything not listed above follows `.planning/research/SUMMARY.md` and the phase's own research; Source `ai-auto` decisions took research's recommendation (orchestrator accepted the auto-resolved remainder).

</decisions>

<canonical_refs>
## Canonical References

- `CROSS-REPO-SCOPE-CONTRACT.md` — §6.2, §5.1–5.2, §10 (A1–A18, E1–E7), §11 (authoritative; never edited here)
- `.planning/ROADMAP.md` — this phase's goal, requirements, success criteria
- `.planning/REQUIREMENTS.md` — requirement text
- `.planning/v1.0-DECISION-MAP.md` — § Phase 6 (source of these decisions)
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
