### Phase 4 — anthropic-transport-okhttp-matrix (v1.0)

- **Status:** `pending`
- **Milestone:** v1.0 (Core Engine)
- **Gate 1 self-UAT log:** [`.planning/phases/04-anthropic-transport-okhttp-matrix/04-SELF-UAT.md`](phases/04-anthropic-transport-okhttp-matrix/04-SELF-UAT.md) — Verdict: **ALL 5 criteria PASS** (headless pure-JVM `:providers` harness, no device; providers.jar md5 `6644cdcedd13e7cb458b1067a682dd16` @ `2088a8e`, 2026-10-01). Forced re-run on all three OkHttp legs (4.12.0, 5.2.1, 5.5.0): 144 tests each, 0 failures, 0 skipped; `./gradlew check` green; API-surface review OK; guard negative control proves a wrong-runtime leg fails.
- **Items covered (5 ROADMAP success criteria):**
  - **SC1 (A1) — OkHttp matrix.** Plain `api` 4.12.0 floor; identical compiled tests green on 4.12.0 / 5.2.1 / 5.5.0 inside `check`, each leg's guard proving the okhttp and mockwebserver jar versions it ran.
  - **SC2 — Cache-correct encoding.** One `cache_control: ephemeral` on the last system block, none on tools/messages; byte-identical prefix across runs and across language/date/transcript changes; usage in four buckets.
  - **SC3 — Forced-tool reshape and retry.** Only the specific forced-tool 400 reshapes (once); text-only reply is `NoToolCall`; transient statuses and timeouts retry at most once (never more than three requests); a retried call applies and commits exactly once.
  - **SC4 — Cancellation and client hygiene.** Cancel and engine deadline cancel the HTTP call and close a late response; no interceptors/logging; fixed HTTPS base URL with `anthropic-version 2023-06-01`; `body?.string()`.
  - **SC5 — No secret leakage.** Canary sweep over trace, events, every `toString()` and failure message; failures carry only status, `error.type`, request id.
- **Owner how-to-verify (run at milestone completion; headless, no device):**
  1. Read the Gate-1 log above for per-criterion evidence.
  2. Optionally re-run `./gradlew :providers:cleanTest :providers:test :providers:testOkhttp521 :providers:testOkhttp550 --rerun-tasks --offline` and `./gradlew check` at milestone HEAD; JUnit XML should show 144 tests, 0 failures, 0 skipped per leg.
  3. **Phase 10 live smoke (real key, real account), confirming the post-review-fix behaviors that the recorded live capture (commit `51d1c54`) predates:** (a) Anthropic's low-credit-balance 400 (message containing `credit balance is too low`) arrives as `Billing`, not `HttpError`; if exact live text differs, adjust the matcher (WR-03). (b) The accepted API-key character set (tab, space, visible ASCII) does not refuse a real key as `Auth` via the pre-send header-safety check (CR-01).
  4. Accept or reject the two function-level `@Suppress("TooGenericExceptionCaught")` entries (`AnthropicTransport.notify`, `CallAwait.onResponse`) that contradict the plans' "no new @Suppress" prohibition (see `04-VERIFICATION.md` Human Verification item 1; suggested override text is there).
- **Note:** No physical or device-hardware step. The real prompt-cache `cache_read > 0` assertion on the full sample belongs to Phase 10. `api.txt` is still not committed; it is created at the v1.0.0 cut in Phase 11. The Gate-1 run made no live API call and used no key.
