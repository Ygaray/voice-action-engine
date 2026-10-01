# Human UAT Pending

## Entries

### Phase 1 — scaffold-publishing-proof (v1.0)

- **Status:** `pending`
- **Milestone:** v1.0 (Core Engine)
- **Gate 1 self-UAT log:** [`.planning/phases/01-scaffold-publishing-proof/01-SELF-UAT.md`](phases/01-scaffold-publishing-proof/01-SELF-UAT.md) — Verdict: **ALL 5 criteria PASS** (headless CLI/build + live JitPack, no device; artifacts core.jar md5 `2080ab4a048088a3a2b89b4080463aba` @ `96c9c62`, live ref `a40f8319ca`, 2026-09-30). Live consumer resolution from an empty Gradle cache, green `check`, 69 negative controls, api-dump wiring proof, OkHttp matrix legs with runtime guard.
- **Items covered (5 ROADMAP success criteria):**
  - **SC1 — Per-module JitPack coordinates.** core, providers (pulls core), keystore resolve by SHA from an empty cache; `:sample` absent from the install command and module list; ECOSYSTEM.md lists the coordinates.
  - **SC2 — check green, JVM 11, clean :core classpath.** 137-task `check`, class-file major 55 in all three artifacts, no HTTP/Android/DI on `:core` runtime classpath.
  - **SC3 — Banned constructs fail check.** Script (69 plants) plus hand-planted planning-id comment, detekt maxIssues 0, no baseline.
  - **SC4 — explicitApi + Metalava.** Undeclared visibility rejected in all three modules; api.txt dumped in an isolated copy only; none in the real tree.
  - **SC5 — Harnesses.** OKHTTP_RUNTIME 4.12.0 / 5.2.1 / 5.5.0 legs green; `:core` harness test under NoNetworkGuard; graphify-out and A10 fixture gitignored.
- **Owner how-to-verify (run at milestone completion; headless, no device):**
  1. Read the Gate-1 log above for per-criterion evidence.
  2. Optionally re-run `scripts/jitpack-live-probe.sh <sha>` for the milestone HEAD and `./gradlew check`.
  3. Confirm Phase 3 delivers the `FakeAiProvider` owed by the accepted SC5 scope-reading (Phase 1 shipped the generic harness primitives only).
- **Note:** No physical or device-hardware step; nothing deferred. Registered for ledger completeness, so the owner can sign off without action. Live proof ref is a SHA; the tag-level proof belongs to Phase 11.

### Phase 2 — core-contract-pipeline-commit-seam (v1.0)

- **Status:** `pending`
- **Milestone:** v1.0 (Core Engine)
- **Gate 1 self-UAT log:** [`.planning/phases/02-core-contract-pipeline-commit-seam/02-SELF-UAT.md`](phases/02-core-contract-pipeline-commit-seam/02-SELF-UAT.md) — Verdict: **ALL 5 criteria PASS** (headless pure-JVM `:core` harness, no device; core.jar md5 `81a4651c80e81317de42a112551a07ba` @ `baf6fcc`, 2026-10-01). Forced re-run of `:core:test`: 233 tests, 0 failures, 0 skipped; `./gradlew check` green; API-surface review OK.
- **Items covered (5 ROADMAP success criteria):**
  - **SC1 — DSL ladder.** Escalate/NoMatch climb with carry by identity, Completed/Failed stop, `TierSelector.Fixed` starts mid-ladder.
  - **SC2 — TierPolicy.** Read per call; maxTier/allowedProviders enforced; 6 / 60,000 / 4,096 defaults; `maxIterations < 2` rejected; offlineOnly with no on-device provider is a loud Failed with zero executions.
  - **SC3 — Never-throw.** Single collapse helper, cancellation always propagates, engine deadline is TIMEOUT not NETWORK, open FailureReason taxonomy with request id, non-data public classes, `ProviderId` value class with four constants.
  - **SC4 — Commit seam.** prepare -> gate -> sink, suspend mode (120 s, fail-closed) and defer mode (Hold/commitHeld/amend), exact held JSON, `onRunClosed` exactly once on each of the five exit paths.
  - **SC5 — Escalation safety and trace.** No escalation after commit/hold, no repeated write, executed list and `CommandTrace` on every outcome, live typed listener equals the trace.
- **Owner how-to-verify (run at milestone completion; headless, no device):**
  1. Read the Gate-1 log above for per-criterion evidence.
  2. Optionally re-run `./gradlew :core:test --rerun-tasks` and `./gradlew check` at milestone HEAD and confirm the JUnit XML shows 0 failures and 0 skipped.
  3. Real-provider, prompt-cache and on-device behaviour are NOT covered here; they belong to the provider and sample phases and the Phase 11 tag gate.
- **Note:** No physical or device-hardware step; nothing deferred. Registered for ledger completeness (same handling as Phase 1), so the owner can sign off without action. `api.txt` is still not committed; it is created at the v1.0.0 cut in Phase 11.

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
