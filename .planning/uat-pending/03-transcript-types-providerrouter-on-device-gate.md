### Phase 3 — transcript-types-providerrouter-on-device-gate (v1.0)

- **Status:** `pending`
- **Milestone:** v1.0 (Core Engine)
- **Gate 1 self-UAT log:** [`.planning/phases/03-transcript-types-providerrouter-on-device-gate/03-SELF-UAT.md`](phases/03-transcript-types-providerrouter-on-device-gate/03-SELF-UAT.md) — Verdict: **ALL 5 criteria PASS** (headless pure-JVM `:core` harness, no device; core.jar md5 `aebc2ac0f8a1593cd116c5e5e66c9715` @ `d0970a5`, 2026-10-01). Forced re-run of `:core:test`: 422 tests, 0 failures, 0 skipped; `./gradlew check` green; API-surface review OK (seven sealed types); `:core` runtime classpath has no HTTP dependency.
- **Items covered (5 ROADMAP success criteria):**
  - **SC1 — Transcript types.** Messages, tool calls/results, system, usage, stop reason, cache directive and verbatim `NativeReplay` express both a single-shot request and a multi-turn tool conversation; `:core` runtime classpath is coroutines + serialization + stdlib only.
  - **SC2 — ProviderRouter.** Selection seam asked once per command (per tier, then frozen); provider switch applies to the next command only; missing key is `NotConfigured` after zero provider calls; one provider's key never reaches another.
  - **SC3 — ON_DEVICE gate.** Unavailable on-device selection is served only by the app-declared fallback with the fallback provider's own key; no declared fallback is a loud typed failure with zero calls and zero key lookups; no Nano/AICore code.
  - **SC4 — CacheNotEngaged.** Zero cache read/write above the model's minimum prefix raises exactly one event; the same result below the minimum is silent (boundary tests both sides).
  - **SC5 — No hard-coded constants.** Model ids and limits reach the engine only via `TierPolicy` defaults, the selection seam and the app-overridable capability table; independent greps and the non-vacuous scan test find no model id, limit literal or settings-storage access in library code.
- **Owner how-to-verify (run at milestone completion; headless, no device):**
  1. Read the Gate-1 log above for per-criterion evidence.
  2. Optionally re-run `./gradlew :core:test --rerun-tasks` and `./gradlew check` at milestone HEAD and confirm the JUnit XML shows 0 failures and 0 skipped.
  3. Real-provider transports, the real prompt cache and AICore on-device behaviour are NOT covered here; they belong to the provider and sample phases (Phase 10 asserts `cache_read > 0`) and the Phase 11 tag gate.
- **Note:** No physical or device-hardware step; nothing deferred. Registered for ledger completeness (same handling as Phases 1 and 2), so the owner can sign off without action. `api.txt` is still not committed; it is created at the v1.0.0 cut in Phase 11. The `evidence/` files in the phase dir predate the review fixes; the Gate-1 log supersedes them.
