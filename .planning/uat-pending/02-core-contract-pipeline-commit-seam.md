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
