# Human UAT Pending

## Entries

### Phase 1 — scaffold-publishing-proof (v1.0)

- **Status:** `signed-off — Yahir, 2026-10-05`
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

- **Status:** `signed-off — Yahir, 2026-10-05`
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

- **Status:** `signed-off — Yahir, 2026-10-05`
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

### Phase 4 — anthropic-transport-okhttp-matrix (v1.0)

- **Status:** `signed-off-with-gap — Yahir, 2026-10-05`
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
- **Note:** No physical or device-hardware step. The real prompt-cache `cache_read > 0` assertion on the full sample belongs to Phase 10. `api.txt` is still not committed; it is created at the v1.0.0 cut in Phase 11. The Gate-1 run made no live API call and used no key.

### Phase 5 — openai-openrouter-transports (v1.0)

- **Status:** `signed-off-with-gap — Yahir, 2026-10-05`
- **Milestone:** v1.0 (Core Engine)
- **Gate 1 self-UAT log:** [`.planning/phases/05-openai-openrouter-transports/05-SELF-UAT.md`](phases/05-openai-openrouter-transports/05-SELF-UAT.md) — Verdict: **ALL 5 criteria PASS** (headless pure-JVM `:providers` harness, no device; providers.jar md5 `8e485979f64306ff703cf738b2c3203b` @ `bb2e85f`, 2026-10-01). Forced uncached re-run on all three OkHttp legs (4.12.0, 5.2.1, 5.5.0): 411 tests each (246 in `providers.chat`), 0 failures, 0 skipped; `./gradlew check` green; API-surface review OK; three negative controls (request golden, captured EDIT body, derived bad-JSON body) each made the right test fail and were restored.
- **Items covered (5 ROADMAP success criteria):**
  - **SC1 — Wire shape.** Golden request bodies for OpenAI and OpenRouter: nested function tools, strict copy stripped of strict-rejected keywords (non-strict copy untouched), `max_completion_tokens` / `reasoning_effort:"none"` for gpt-5.4+, legacy `max_tokens`, `provider.require_parameters:true` on a forced OpenRouter call.
  - **SC2 — Typed outcomes from real and derived bodies.** 8 sanitized real captures (5 OpenAI, 3 OpenRouter) plus 29 derived rows replay to their typed outcomes: bad/array/scalar arguments -> `malformed_tool_args`, refusal and content_filter, HTTP-200 error envelopes, quota/402/403/404 mappings. No key material in goldens or evidence.
  - **SC3 — Matrix and usage.** Same 411 compiled tests green on 4.12.0 / 5.2.1 / 5.5.0 with per-leg runtime guard; usage normalized to four buckets (cached tokens not double counted).
  - **SC4 — Retry, cancel, hygiene.** At most one transport retry (two requests) for transient statuses, dropped connections and timeouts; one apply and one commit per logical call; cancel and engine deadline cancel the HTTP call; no logging interceptors, `body?.string()`, strict only without optionals, 60 s default timeouts.
  - **SC5 — Absent optional.** Parameterized contract test for both vendors; an omitted optional reaches the mutation absent (real OpenAI body; OpenRouter on a derived body).
- **Owner how-to-verify (run at milestone completion; headless, no device):**
  1. Read the Gate-1 log above for per-criterion evidence.
  2. Optionally re-run `./gradlew :providers:cleanTest :providers:test :providers:testOkhttp521 :providers:testOkhttp550 --rerun-tasks --offline` and `./gradlew check` at milestone HEAD; JUnit XML should show 411 tests, 0 failures, 0 skipped per leg.
  3. **Phase 10 live obligations (real key, real account; these cannot be proven without a live provider and belong to VER-03 / the three-cloud smokes):** (a) a real EDIT-shaped call through OpenRouter whose output omits the optionals, replacing the derived `openrouter_edit_absent_optional` row (the 05-12 attempt R2 had the routed model fill every optional; needs a prompt or model that omits them, D-14); (b) capture a real OpenAI Responses-only 400 (gpt-6-astra / 6.1 Sol style) to confirm `RESPONSES_ENDPOINT_MARKER` wording; (c) a Haiku (or other cache-writing) call through OpenRouter with an explicit cache breakpoint to confirm `prompt_tokens` includes `cache_write_tokens` (assumption in `decodeChatUsage`), and re-check OpenRouter cache hit on an immediate repeat (R3 saw `cached_tokens` 0); (d) low-credit mapping live: OpenAI `insufficient_quota` 429 and OpenRouter 402 arrive as `Billing`, and the accepted API-key character set does not refuse a real key.
  4. Owner doc cleanups: correct `evidence/live-chat-capture.txt` "numeric bounds in strict mode" note (the encoder does not strip `minimum`/`maximum`/`exclusiveMinimum`; the live strict 200 therefore accepted them); confirm the public `@JvmInline value class` attempt kind (review IN-03) before the `api.txt` cut.
- **Note:** No physical or device-hardware step. The Gate-1 run made no live API call and used no key; the 05-12 live capture (11 requests, D-16 approved) was audited as a claim only. `api.txt` is still not committed; it is created at the v1.0.0 cut in Phase 11.

### Phase 6 — keystore (v1.0)

- **Status:** `signed-off — Yahir, 2026-10-05`
- **Milestone:** v1.0 (Core Engine)
- **Gate 1 self-UAT log:** [`.planning/phases/06-keystore/06-07-SELF-UAT.md`](phases/06-keystore/06-07-SELF-UAT.md) — Verdict: **ALL 4 criteria PASS** (device SM-S908U TESTER R5CT10XNKQN, androidTest APK md5 `6ba7a5a27c1bb3edc7b274197095390e` @ `8d9382d`, 2026-10-01). Guarded runner `scripts/run-keystore-instrumented.sh` -> `OK (7 tests)` on the real AndroidKeyStore with real AES/GCM and file DataStores, on the post-code-review HEAD; JVM `:keystore` suite 96 tests, 0 failures.
- **Items covered (4 ROADMAP success criteria):**
  - **SC1 — Store/read/delete per provider.** Real AES/GCM ciphertext persisted in a caller-owned DataStore (decrypted by the verbatim legacy code on-device; a flipped byte reads `decrypt_failed`). Delete leg is JVM-proven (`ApiKeyStoreTest`), not on device.
  - **SC2 — SB/CT legacy compat both ways.** SB (1 alias) and CT (3 aliases) legacy-written blobs read back unchanged; engine-written blobs decrypt with the legacy code; framework Base64 NO_WRAP equals `java.util.Base64`.
  - **SC3 — Read states / KeyMissing / one key.** Deleted device key reads `KeyMissing` and `getKey` stays null after both read paths; 8-way concurrent first use leaves every store reading its own key.
  - **SC4 — Provider seam.** `KeystoreCredentialSource` yields `Present` with the exact key on hardware and `Unreadable("key_missing")` when the key is gone; JVM round trip through the crypto seam passes.
- **Owner how-to-verify (run at milestone completion; no physical step):**
  1. Read the Gate-1 log above for per-criterion evidence and `evidence/keystore-instrumented-run-gate1.txt` for the verbatim runner output.
  2. Optionally re-run `bash scripts/run-keystore-instrumented.sh` at milestone HEAD with the TESTER idle; expect the last line `KEYSTORE_INSTRUMENTED: PASS tests=7 ...` and `test package removed`.
  3. Phase 10 live obligation (not Phase 6): a real saved BYO key reaching a live provider call from `:sample`.
- **Note:** Library phase, no UI and no physical step. One observed hardening opportunity, not a defect: no single device test does `ApiKeyStore.save` immediately followed by `KeystoreCredentialSource.credential` == Present (both halves are hardware-proven separately; the composite is JVM-proven). `api.txt` is still created at the v1.0.0 cut in Phase 11.

### Phase 7 — singleshot-strategy (v1.0)

- **Status:** `signed-off — Yahir, 2026-10-05`
- **Milestone:** v1.0 (Core Engine)
- **Gate 1 self-UAT log:** [`.planning/phases/07-singleshot-strategy/07-SELF-UAT.md`](phases/07-singleshot-strategy/07-SELF-UAT.md) — Verdict: **ALL 3 criteria PASS** (headless pure-JVM `:core` + `:providers` harness, no device; HEAD `bbf91a4`, 2026-10-01). Forced re-run of `:core:test :providers:test`: core 535 tests, providers 572 tests, 0 failures, 0 errors, 0 skipped; `./gradlew check --offline` green on all three OkHttp legs; API-surface review OK (seven sealed types, classes=177). Backfilled on 2026-10-01 by the milestone master after the orchestrator flagged the missing record (the execute stage ruled Gate-1 N/A, `has-uat-criteria=false`).
- **Items covered (3 ROADMAP success criteria):**
  - **SC1 — One forced-tool call, local resolution, gated commit.** `SingleShotStrategy` makes exactly one forced single-tool call from the app's `ToolSpecProvider`; the app's `OutcomeResolver` yields a (possibly batch) proposal that commits only through gate -> `CommitSink`.
  - **SC2 — Default mappings and overrides.** No tool call / prose escalates with `NoToolCall`; refusal fails with `REFUSAL`; both overridable per tier; OpenAI Chat bodies carry `parallel_tool_calls: false` (OpenRouter deliberately omits it and takes the first call); extra calls are dropped with `EXTRA_TOOL_CALLS_DROPPED` and the outcome is `Completed(partial = true)`.
  - **SC3 — CT confirm scenarios.** Weak match held then committed via `commitHeld` as a linked run, batch proposal held with one gate ask, amended confirm applying only the replacement list (S1-S10 CT-shaped acceptance tests on in-tree fakes).
- **Owner how-to-verify (run at milestone completion; headless, no device):**
  1. Read the Gate-1 log above for per-criterion evidence.
  2. Optionally re-run `./gradlew :core:test :providers:test --offline --rerun-tasks` and `./gradlew check --offline` at milestone HEAD and confirm the JUnit XML shows 0 failures and 0 skipped.
  3. Live-provider behaviour is NOT covered here. It belongs to Phase 10 (VER-02 / VER-03; VER-03 is extended per 10-CONTEXT.md Runtime Decisions to include Chat/OpenRouter multi-turn on device): assert the real Anthropic body carries `disable_parallel_tool_use` and returns 200, and optionally `cache_read_input_tokens > 0` on two cheap calls (RESEARCH A2).
- **Note:** No physical or device-hardware step; nothing deferred from this phase. Registered for ledger completeness (same handling as Phases 1-6), so the owner can sign off without action. The CT scenarios run against CT-shaped fakes, not CT's own resolver; real consumer flows are a Phase 11 / migration concern. `api.txt` is still not committed; it is created at the v1.0.0 cut in Phase 11.

### Phase 8 — multi-turn-mappers (v1.0)

- **Status:** `signed-off — Yahir, 2026-10-05`
- **Milestone:** v1.0 (Core Engine)
- **Gate 1 self-UAT log:** [`.planning/phases/08-multi-turn-mappers/08-SELF-UAT.md`](phases/08-multi-turn-mappers/08-SELF-UAT.md) — Verdict: **ALL 4 criteria PASS** (headless pure-JVM `:core` + `:providers` harness, no device, no live call by this run; HEAD `bbf91a4`, 2026-10-01). Forced re-run of `:core:test :providers:test`: core 535 tests, providers 572 tests, 0 failures, 0 errors, 0 skipped; `./gradlew check --offline` green with the conformance suite passing on OkHttp 4.12.0, 5.2.1 and 5.5.0. Backfilled on 2026-10-01 by the milestone master after the orchestrator flagged the missing record (the execute stage ruled Gate-1 N/A, `has-uat-criteria=false`).
- **Items covered (4 ROADMAP success criteria):**
  - **SC1 — One conformance suite.** A single `MultiTurnConformanceSuite` bound to Anthropic, OpenAI and OpenRouter round-trips every fixture conversation, including parallel calls and empty-argument forms.
  - **SC2 — Verbatim replay.** Every assistant turn replays byte-for-byte from `NativeReplay` to the same provider and model (thinking goldens, derived and captured, survive); a stamp for another provider, model or shape fails with zero requests; `carry` is an opaque semantic value (no dedicated "carry cannot hold a transcript" test; rests on type shape plus the stamp refusal).
  - **SC3 — Per-dialect tool results.** Anthropic batches a turn's results in one user message with `is_error`; Chat sends one `role:tool` per call id (errors wrapped `{"error": ...}`); 5 captured, sanitized real-body goldens (Anthropic A1/A2, OpenAI O1, OpenRouter R1/R3) pass hygiene and replay green.
  - **SC4 — Cache directive and stable prefix.** Anthropic keeps one system breakpoint, Chat sends none; every iteration only appends to the cached prefix, with negative controls proving the check detects a rewrite.
- **Owner how-to-verify (run at milestone completion; headless, no device):**
  1. Read the Gate-1 log above for per-criterion evidence.
  2. Optionally re-run `./gradlew :core:test :providers:test --offline --rerun-tasks` and `./gradlew check --offline` at milestone HEAD and confirm the JUnit XML shows 0 failures and 0 skipped.
  3. Real-provider and on-device multi-turn behaviour is NOT re-proved here. It belongs to Phase 10 (VER-02 / VER-03; VER-03 is extended per 10-CONTEXT.md Runtime Decisions to include Chat/OpenRouter multi-turn on device). The in-phase live capture (08-09, 14 requests, about USD 0.033 estimated) is audited from `evidence/live-multiturn-capture.txt` only.
- **Note:** No physical or device-hardware step; nothing deferred from this phase. Registered for ledger completeness (same handling as Phases 1-6), so the owner can sign off without action. Known gap, not a failure: live conversation R2 (OpenRouter `openai/gpt-oss-120b`) ended UNMET under the 3-request cap, so no gpt-oss golden exists; all R2 requests were accepted (200) and SC3's real half is met on all three dialects via A1, A2, O1, R1 and R3. `api.txt` is still not committed; it is created at the v1.0.0 cut in Phase 11.

### Phase 9 — agentic-loop-strategy (v1.0)

- **Status:** `signed-off — Yahir, 2026-10-05`
- **Milestone:** v1.0 (Core Engine)
- **Gate 1 self-UAT log:** [`.planning/phases/09-agentic-loop-strategy/09-SELF-UAT.md`](phases/09-agentic-loop-strategy/09-SELF-UAT.md) — Verdict: **ALL 4 criteria PASS** (headless pure-JVM `:core` + `:providers` harness, no device, no live call by this run; HEAD `b9c776f`, 2026-10-01). Forced re-run of `:core:test :providers:test`: core 646 tests, providers 587 tests, 0 failures, 0 errors, 0 skipped; `./gradlew check --offline` green; `AgenticLoopWireTest` 15/15 on OkHttp 4.12.0, 5.2.1 and 5.5.0 (the two 5.x legs forced fresh, not from cache); keystore 96/96; the CLN-02 `scanBannedConstructs` is green on all three modules and a planted `log_food` was shown to fail it, then removed (tree clean).
- **Items covered (4 ROADMAP success criteria):**
  - **SC1 — Loop over the app's seams on three providers.** `AgenticLoopStrategy` runs over `ToolSpecProvider` and the two-phase `ToolExecutor`; every `Mutation` reaches the gate and the sink before the next model call; a held step feeds the model the exact `{"applied":false,"status":"held_for_confirmation"}` bytes (asserted in core and on the Anthropic and Chat wires); one script gives an identical outcome on Anthropic, OpenAI and OpenRouter.
  - **SC2 — SB's guards as named tests.** Whole-turn validation, token ceiling before dispatch, final-iteration guard, sequential dispatch, 2-strike per-tool abort, unknown tool to `is_error`, bounds read from `TierPolicy`, SB guard order and tie precedence, and a typed leaf for every stop reason. Known equality boundary (WR-01, signed off): a tool turn ending exactly on the token ceiling still dispatches, then the next model call is refused.
  - **SC3 — Honest on every exit.** Done, budget (tokens and iterations), cancel (during provider call, during gate, during apply, between batch items) and error (provider failure, malformed turn, strike abort, terminal call) all report the executed actions and commits so far, and the sink has received each action before the single close.
  - **SC4 — No app-domain types, no hard-coded tool count (CLN-02).** Scanner green on `:core`, `:providers`, `:keystore`; scanner controls green; planted-violation proof performed and removed; the loop is shown working with 1, 2 and 25 tools.
- **Owner how-to-verify (run at milestone completion; headless, no device):**
  1. Read the Gate-1 log above for per-criterion evidence.
  2. Optionally re-run `./gradlew :core:test :providers:test --offline --rerun-tasks` and `./gradlew check --offline` at milestone HEAD and confirm the JUnit XML shows 0 failures and 0 skipped (`testOkhttp521` / `testOkhttp550` may restore from the build cache; add `--rerun` to force them).
  3. Optionally repeat the CLN-02 proof: add a temporary file under `core/src/main/` containing the string `log_food`, run `./gradlew :core:scanBannedConstructs --offline` (it must fail naming the file), delete the file, and re-run (it must pass).
  4. Real-provider and on-device agentic behaviour is NOT proved here. It belongs to Phase 10 (VER-02 / VER-03): the cloud agentic path on the TESTER with a real prompt-cache hit across iterations, and real-model parallel tool calls.
- **Note:** No physical or device-hardware step; nothing deferred from this phase. Registered so the owner can sign off without action. The WR-01 token-ceiling equality boundary is documented in `AgenticLoopStrategy` KDoc and `09-REVIEW.md` (disposition: intentional, SB parity, seam sign-off item 9 / Q10); it is a behaviour note, not a gap. The CLN-02 scanner is a deny-list plus pattern rule, so it cannot catch an app-domain concept under a name not on the list. `api.txt` is still not committed; it is created at the v1.0.0 cut in Phase 11.

### Phase 10 - sample-harness-gate-1-docs (v1.0)

- **Status:** `signed-off-with-gap — Yahir, 2026-10-05`
- **Milestone:** v1.0 (Core Engine)
- **Gate 1 self-UAT log:** [`.planning/phases/10-sample-harness-gate-1-docs/10-SELF-UAT.md`](phases/10-sample-harness-gate-1-docs/10-SELF-UAT.md) - Verdict: **13 of 14 criteria PASS + 1 ACCEPTED BY EVIDENCE (G1-09 INCONCLUSIVE `model_filled_optional` after one rerun, accepted by the orchestrator 2026-10-01, NOT a PASS)** (device yahirs-s22-ultra-2 SM-S908U, APK md5 `4c6fc98c64485ce878e11e60fdf92559` @ `4a586ed7b8`, 2026-10-01). Live legs L1-L6 and the optional L7 ran on the TESTER through the `:sample` UI; spend: 11 requests (anthropic=3 openai=4 openrouter=4), est USD 0.014.
- **Items covered (4 ROADMAP success criteria, 14 runbook criteria):**
  - **VER-01 - on-device base (G1-01..G1-05).** Fixture absent is loud and refuses with no spend; fixture present and verified (prefix `ebd3ef4a`, tools=18 equals host); OkHttp 5.2.1 pin runs on the device; bring-your-own key save, relaunch and delete through `:keystore`; the three test keys imported through `:keystore` with the plaintext files deleted.
  - **VER-02 - Anthropic agentic cold run (G1-06).** PASS: 2 turns, write 7016, read 7016, both 200, `min_cacheable=4096`, claude-haiku-4-5.
  - **VER-03 - single-shot smokes and multi-turn (G1-07, G1-08, G1-09, G1-10, G1-11).** Anthropic and OpenAI smokes PASS; OpenAI Chat and OpenRouter multi-turn PASS; OpenRouter smoke INCONCLUSIVE (`model_filled_optional`) on both prompts.
  - **VER-04 - clarification and partial on device (G1-12).** Offline demos PASS: question and two options shown, follow-up completes after choosing, partial readout "Did 1 action(s), couldn't finish".
  - **Spend and cleanup (G1-13, G1-14).** 10/33 core and 1/1 optional; keys directory empty; sample package removed; no fixture left on host.
- **Carry dispositions (register C1-C7):**
  - **C1 Billing mapping:** NOT EXERCISED (cannot be triggered without draining credit); covered by the named unit tests. Stays a Gate-2 item.
  - **C2 key charset:** proven per provider by `key_charset=ok` after a 200 (Anthropic, OpenAI, OpenRouter). Closed.
  - **C3 Responses-only 400:** L7 CAPTURED http=400 reason=http_error. Captured per the register; the exact marker wording is not visible under LE-7, so Yahir may accept or ask for a host-side wording check.
  - **C4 OpenRouter cache-write accounting (uat-pending/05 item 3(c)):** not exercisable in v1.0 (LATER-02); needs Yahir waiver; P11 waiver packet. NOT closed. `turn2_cache_read=0` observed in L6.
  - **C5 OpenRouter EDIT-shaped call omitting optionals:** INCONCLUSIVE(`model_filled_optional`) after one rerun (G1-09), **ACCEPTED BY EVIDENCE by the orchestrator (2026-10-01), not a PASS**. Evidence: the shared `ChatCompletionsProvider`/`ChatVendor` decoder path passed the live OpenAI omitted-optional check, and host tests prove no default-filling. Listed in the Phase 11 waiver packet next to C4 so Yahir sees both open items together.
  - **C6 `disable_parallel_tool_use`:** host golden plus live 200 on the first attempt (L2), accepted by the orchestrator. Closed.
  - **C7 deferred live legs:** not applicable (decision approved).
- **Owner how-to-verify (run at milestone completion):**
  1. Read the Gate-1 log above and the nine `evidence/gate1-*.txt` files.
  2. Decide the C4 waiver (cache-write via router, LATER-02) and confirm the C5 acceptance (already accepted by the orchestrator; Yahir may instead choose a different OpenRouter model for the optional-omission check).
  3. Optionally re-run L4 from the `:sample` screen on the TESTER with `scripts/run-sample-gate1.sh` (needs `push-keys` and a fresh install) to see whether a different model omits the optionals.
  4. `10-WIRING-TEST.md` is `status: pass` (isolated rerun on `36c578f464`, `WIRING TEST: PASS checks=9`). If Phase 11 edits README, INTEGRATION, API or ECOSYSTEM (the optional fix for the 3 minor stumbles), the isolated wiring test must be rerun on the new SHA before the tag.
- **Note:** `scripts/run-sample-gate1.sh` `package_installed` returns 141 under `pipefail` (grep -q SIGPIPE), so `build-install` reports `install_failed` after a good install and `preflight` reports `installed=no`; routed to gap-closure as a tooling defect. The runbook G1-01 step order should be capture-start, then a cold relaunch. The TESTER is cleaned (no package, no keys).
