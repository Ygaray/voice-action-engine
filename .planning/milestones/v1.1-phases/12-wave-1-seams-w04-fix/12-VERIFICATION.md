---
phase: 12-wave-1-seams-w04-fix
verified: 2026-10-05T23:59:00Z
status: passed
score: 5/5 must-haves verified
covered_files:
  - .planning/REQUIREMENTS.md
  - .planning/phases/12-wave-1-seams-w04-fix/12-01-PLAN.md
  - .planning/phases/12-wave-1-seams-w04-fix/12-01-SUMMARY.md
  - .planning/phases/12-wave-1-seams-w04-fix/12-02-PLAN.md
  - .planning/phases/12-wave-1-seams-w04-fix/12-02-SUMMARY.md
  - .planning/phases/12-wave-1-seams-w04-fix/12-03-PLAN.md
  - .planning/phases/12-wave-1-seams-w04-fix/12-03-SUMMARY.md
  - .planning/phases/12-wave-1-seams-w04-fix/12-04-PLAN.md
  - .planning/phases/12-wave-1-seams-w04-fix/12-04-SUMMARY.md
  - .planning/phases/12-wave-1-seams-w04-fix/12-05-PLAN.md
  - .planning/phases/12-wave-1-seams-w04-fix/12-05-SUMMARY.md
  - .planning/phases/12-wave-1-seams-w04-fix/12-06-PLAN.md
  - .planning/phases/12-wave-1-seams-w04-fix/12-06-SUMMARY.md
  - .planning/phases/12-wave-1-seams-w04-fix/12-07-PLAN.md
  - .planning/phases/12-wave-1-seams-w04-fix/12-07-SUMMARY.md
  - .planning/phases/12-wave-1-seams-w04-fix/12-08-PLAN.md
  - .planning/phases/12-wave-1-seams-w04-fix/12-08-SUMMARY.md
  - API.md
  - INTEGRATION.md
  - config/detekt/detekt.yml
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/commit/ActionLedger.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/commit/ApplyStep.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/commit/CommitCoordinator.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/commit/CommitSink.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/commit/HeldProposal.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/pipeline/CommandOutcome.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/pipeline/HeldCommit.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/pipeline/PolicyPreCheck.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/pipeline/RunSession.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/pipeline/TierWalk.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/CommandSession.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/OutcomeResolver.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/agentic/AgenticDispatch.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/agentic/AgenticLoopStrategy.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/singleshot/SingleShotOutcomes.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/singleshot/SingleShotStrategy.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/telemetry/CommandTrace.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/telemetry/RunRecorder.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/transcript/ModelRequest.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/transcript/ReasoningMode.kt
  - keystore/src/main/kotlin/io/github/ygaray/voiceactionengine/keystore/AndroidKeyStoreKeyAccess.kt
  - keystore/src/main/kotlin/io/github/ygaray/voiceactionengine/keystore/ApiKeyStore.kt
  - keystore/src/main/kotlin/io/github/ygaray/voiceactionengine/keystore/DelicateKeyAccess.kt
  - keystore/src/main/kotlin/io/github/ygaray/voiceactionengine/keystore/KeyAccess.kt
  - keystore/src/main/kotlin/io/github/ygaray/voiceactionengine/keystore/SecretReader.kt
  - providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/anthropic/AnthropicModels.kt
  - providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ChatErrors.kt
  - providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/chat/OpenAiModelRules.kt
  - sample/src/main/kotlin/io/github/ygaray/voiceactionengine/sample/legs/LegCatalog.kt
  - sample/src/main/kotlin/io/github/ygaray/voiceactionengine/sample/legs/LegRunner.kt
  - scripts/run-sample-gate1.sh
  - scripts/verify-docs-coverage.sh
  - scripts/verify-keyaccess-opt-in.sh
  - scripts/verify-negative-controls.sh
  - scripts/verify-sample-device-guard.sh
covered_digest: "v1:sha256:07510d621c0856f1eb614572b89840cb2d8357b7a027a112f0c6c748ee221878"
behavior_unverified: 0
overrides_applied: 0
re_verification: false
---

# Phase 12: Wave-1 Seams & W04 Fix Verification Report

**Phase Goal:** Consumers get every additive seam they are blocked on (provider-failure hook, explicit reasoning knob, carry / policy-cap / tool-call-id facts on the outcome, opt-in key access for tests), the v1.0.1 wiring stumbles are gone from the docs, and a direct OpenAI Responses-only model fails with the specific `ModelUnsupported` reason instead of a generic `http_error`.
**Verified:** 2026-10-05
**Status:** passed
**Re-verification:** No, initial verification (no prior VERIFICATION.md)

SUMMARY.md claims were not used as evidence. Every truth below was checked against the source, the tests, and a fresh
automated run in the main checkout (branch `main`, HEAD `b067654`, so the review-fix commits are included).

## Goal Achievement

### Observable Truths (ROADMAP success criteria, the contract)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | SC1: `onFailed` turns a provider failure into `Escalate`; never fires for ceiling / gate / `strategy_error`; unset still yields `Failed(reason, details)`. `ModelRequest.reasoning` + `Builder.reasoning` (SingleShot, AgenticLoop) default `OFF` with unchanged wire. `claude-sonnet-5` has its own capability row. | VERIFIED | `SingleShotOutcomes.kt:40` routes only the `else` arm of `failureOutcome` to `hooks.onFailed`; `NoToolCall`/`Refusal` have their own arms. `SingleShotStrategy.kt:235` Builder `onFailed` (default builds `Failed`). Tests in `SingleShotOutcomeMappingTest` (lines 401-580) cover: HTTP 400 -> Escalate, exact reason/details, unset default, not called for no-tool-call/refusal/answers, missing credential, token ceiling, gate hold, throwing resolver, throwing hook -> strategy_error. `ReasoningMode` is a value class with internal ctor, `OFF`/`PROVIDER_DEFAULT` on the companion; `ModelRequest.kt` keeps the 7-arg ctor (-> `ReasoningMode.OFF`); `Builder.reasoning = ReasoningMode.OFF` in `SingleShotStrategy.kt:206` and `AgenticLoopStrategy.kt:133`. Providers never read `request.reasoning` (grep of `providers/src/main`), so the wire is byte-identical by construction; `ReasoningWireParityTest` (providers) and `AgenticLoopReasoningTest` (core) pin it. `AnthropicModels.kt`: exact `claude-sonnet-5` row, forced choice allowed, `EXPLICIT_BREAKPOINTS`, `minCacheablePrefixTokens = 1024`; `AnthropicModelsTest` pins it and pins that dated/cased ids and `claude-sonnet-5-5` do not inherit it. |
| 2 | SC2: `TierAttempt.carryIn`, `Unhandled.cappedByPolicy`, `Extraction.callId`, `ExecutedAction.providerCallId`; strictly additive; 7-arg `ModelRequest` and 2-arg `Extraction` ctors survive. | VERIFIED | `TierWalk.runTier` calls `recorder.tierStarted(strategy.id, carry != null)` (boolean only; `CommandTrace.kt:62`). `PolicyPreCheck.check`: `cappedByPolicy = eligible.size < strategies.size` (covers `maxTier`, provider restriction and offline-only skips, all of which emit `tier_skipped_policy`); the all-skipped case returns `refused(...)` with `false` and fails with `NoEligibleTier`/`ProviderUnavailable`, never `Unhandled`; `TierWalk.run` passes `ladder.cappedByPolicy` to `Unhandled`. KDoc at `CommandOutcome.kt:128-137` states the coverage incl. offline-only (SB condition). `OutcomeResolver.kt`: `Extraction(toolName, arguments, callId: String?)` plus kept 2-arg ctor (callId null). `providerCallId` flows `SingleShotStrategy.submitAll`/`AgenticDispatch:100` -> `CommandSession.submit(step, providerCallId)` -> `CommitCoordinator` -> `ApplyStep`/`HeldProposal`/gate-fault/`finished` -> `ExecutedAction.providerCallId` (public, `CommitSink.kt:62`). Zero-call tiers never pass an id (null). `:core:apiCheck :providers:apiCheck :keystore:apiCheck` (Metalava compat vs committed v1.0.x `api.txt`) BUILD SUCCESSFUL; `api.txt` untouched since `c43c65e` (v1.0.0 baseline), `git diff v1.0.1 -- */api.txt` is empty. |
| 3 | SC3: `ApiKeyStore(dataStore, slots, keyAccess)` only with explicit `@OptIn(DelicateKeyAccess::class)`; INTEGRATION.md fake compiles and round-trips on the JVM; three v1.0.1 stumbles fixed; single-tool cache note. | VERIFIED | `KeyAccess.kt`: plain `public interface` annotated `@DelicateKeyAccess` (two members; `existingKey` returns null, `getOrCreateKey` is the creator). `DelicateKeyAccess.kt`: `@RequiresOptIn(level = ERROR)`. `ApiKeyStore.kt:60-62` public ctor carries `@DelicateKeyAccess`; module-wide opt-in was removed by review fix WR-02 (narrow `@OptIn` only). Negative-compile proof run by me: `scripts/verify-keyaccess-opt-in.sh` -> ctor without `@OptIn` went red with the marker error, `KeyAccess` implemented without `@OptIn` went red, positive control compiled, `failures: 0`. The fake (INTEGRATION.md "keystore-fake" snippet, 10 lines) is mirrored verbatim in `DocSnippetsTest.kt:356-373` and `theKeystoreFakeRoundTripsAKeyOnTheJvm` saves and reads a key through it (`sample:testDebugUnitTest` green; `verify-docs-coverage.sh` OK, 25 checks). DOC-01: INTEGRATION §7 line 417 (`ProviderId` toString prints the wire value), §10 imports now include `runTest` + JUnit and the dependency note (lines 648-660, also line 56-57 in §2), §5 line 98 (SingleShot cannot serve reads), Notes line 719 (single-tool prefix will not cache on Haiku 4,096 / OpenAI 1,024; sonnet-5 1,024; sonnet-5-5 512). |
| 4 | SC4: W04 fixed and JVM-proven. (a) encoder golden: `gpt-6-astra` and every `OpenAiModelRules` model that rejects `"none"` never gets `reasoning_effort: "none"`. (b) MockWebServer replay of the captured W04 400 body maps to `ModelUnsupported` on all three OkHttp legs. | VERIFIED | `OpenAiModelRules.wireRules`: `RESPONSES_ONLY` (`gpt-6-astra`, `gpt-6.1-sol`, dated/suffixed) direct -> no effort key; via router -> `low`; `takesEffortNone` excludes a direct `-pro`/`-codex` id so it also never receives `none`; `toolsOnChat` refuses these before any network call. `ChatEncoderTest.aDirectResponsesOnlyModelEncodesToTheGoldenBodyWithNoReasoningEffort` compares against golden `openai.json#astra_direct` (contains no `reasoning_effort`). `ChatErrors.refine`: `400 + param=reasoning_effort + code=unsupported_value` -> `ModelUnsupported` (after the quota arm; message never read). `ChatTransportTest.aDirectResponsesOnlyModelUnderAToolsOverrideSendsNoEffortAndTheW04AnswerIsModelUnsupported` replays the W04 body fields through MockWebServer, asserts the request carries no `reasoning_effort`, reason code `model_unsupported`, HTTP 400. I ran the providers suite on all three legs myself: `test` (4.12.0 floor) 609/609, `testOkhttp521` 609/609, `testOkhttp550` 609/609, 0 failures; `verifyOkHttpCompileFloor` green. |
| 5 | SC5: W04 proven live: `:sample` smoke call to `gpt-6-astra` under the `supportsTools` override returns the typed outcome (not `http_error`); evidence logged; spend-capped test key; bounded requests. | VERIFIED | `evidence/gate1-responses_probe.txt` (committed in `fcec123`): `VAE_ATTEMPT ... provider=openai n=1 kind=initial http=400`, `VAE_OUTCOME ... kind=failed reason=model_unsupported`, `VAE_VERDICT leg=responses_probe verdict=PASS reason=model_unsupported http=400 trigger=ui`, `VAE_BUDGET ... openai=1`, target `R5CT10XNKQN` (the TESTER). The leg is real code, not a stub: `LegRunner.kt:179` applies `capabilities(spec.provider, spec.model) { supportsTools = true }` for `RESPONSES_PROBE`, and the judge passes only on typed `model_unsupported` after a real provider status (`SmokeLegTest`, in the green `sample:testDebugUnitTest`). Approval and bounds recorded in `12-LIVE-LEG-DECISION.md` and `12-CONTEXT.md` RT-01 (ceiling 4 requests / USD 0.05; 1 host probe + 1 device request used). Device not touched by this verification. |

**Score:** 5/5 truths verified (0 present-but-behavior-unverified, 0 overrides).

Behavior-dependent truths (state/cancellation/ordering): the `onFailed` "never for ceiling/gate/strategy_error" invariant, the `carryIn` flush-on-deadline/cancel path, and `cappedByPolicy` "exactly when" are each exercised by named passing tests (`SingleShotOutcomeMappingTest`, `TierWalkTest`/`TraceTest` carryIn cases, `TierPolicyTest`), so none is left on presence alone.

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `core/.../transcript/ReasoningMode.kt` | open value class, `OFF`/`PROVIDER_DEFAULT` | VERIFIED | internal ctor, companion constants, wired into `ModelRequest`, both Builders |
| `core/.../strategy/singleshot/SingleShotOutcomes.kt` + `SingleShotStrategy.kt` | `onFailed` hook | VERIFIED | else-arm only, wired via `OutcomeHooks` |
| `core/.../pipeline/{TierWalk,PolicyPreCheck,CommandOutcome}.kt`, `telemetry/{CommandTrace,RunRecorder}.kt` | `carryIn`, `cappedByPolicy` | VERIFIED | computed from the walk's own carry and the pre-check's own skips; surfaced on public types |
| `core/.../strategy/OutcomeResolver.kt`, `commit/*`, `pipeline/{RunSession,HeldCommit}.kt` | call-id plumbing | VERIFIED | every write path (apply, hold, gate-fault, finished, held-commit replay, cancelled-apply journal) carries `providerCallId` |
| `keystore/.../{KeyAccess,DelicateKeyAccess,ApiKeyStore}.kt` | opt-in seam | VERIFIED | negative compile proven live |
| `providers/.../OpenAiModelRules.kt`, `ChatErrors.kt`, `AnthropicModels.kt` | W04 deny-list, classifier backstop, `claude-sonnet-5` row | VERIFIED | goldens + MockWebServer tests green on 3 legs |
| `INTEGRATION.md`, `API.md` | DOC-01 + fake + names | VERIFIED | gated by `verify-docs-coverage.sh` and `DocSnippetsTest` |
| `scripts/run-sample-gate1.sh`, `sample/.../legs/*` | judged live leg | VERIFIED | retargeted to the Phase 12 dir (env override per IN-01), guard test 33 scenarios OK |
| `evidence/gate1-responses_probe.txt` | PROV-16 live line | VERIFIED | PASS line present and consistent with the code's vocabulary |

### Key Link Verification

| From | To | Via | Status |
|------|----|-----|--------|
| `SingleShotStrategy` Builder `onFailed` | `OutcomeHooks` -> `failureOutcome` | `settings.onFailed` (line 75) | WIRED |
| `SingleShotStrategy.route/submitAll`, `AgenticDispatch` | `CommitCoordinator`/`ApplyStep` | `CommandSession.submit(step, providerCallId)` | WIRED |
| `HeldProposal.providerCallId` | held-commit apply | `HeldCommit.kt:71` `applyWithoutGate(mutations, child.held.providerCallId)` | WIRED |
| `PolicyPreCheck.Ladder.cappedByPolicy` | `CommandOutcome.Unhandled` | `TierWalk.run` | WIRED |
| `TierWalk.runTier` | `TierAttempt.carryIn` | `recorder.tierStarted(id, carry != null)` | WIRED |
| `ChatEncoder` | `OpenAiModelRules.wireRules` | `rules.reasoningEffortWithTools?.let { put(...) }` (`ChatEncoder.kt:72`) | WIRED |
| Chat error parse | `FailureReason.ModelUnsupported` | `ChatErrors.refine` | WIRED |
| `ApiKeyStore` public ctor | `SecretReader(keyAccess)` | `@OptIn`-scoped internals | WIRED |
| `LegRunner` RESPONSES_PROBE | `capabilities{supportsTools=true}` override | `LegRunner.kt:179` | WIRED |

### Data-Flow Trace (Level 4)

| Artifact | Data | Source | Real data | Status |
|----------|------|--------|-----------|--------|
| `ExecutedAction.providerCallId` | tool-call id | `AssistantPart.ToolCall.id` from the provider response, passed by value | yes, byte for byte; null for zero-call tiers | FLOWING |
| `Unhandled.cappedByPolicy` | policy skip | `PolicyPreCheck` computation over the real tier list | yes | FLOWING |
| `TierAttempt.carryIn` | carry presence | `TierWalk.carry` | yes (boolean only, never content; asserted in render forms) | FLOWING |
| PROV-16 evidence line | live model_unsupported | real OpenAI HTTP 400 on the TESTER | yes | FLOWING |

### Automated Verification Run (exact commands and results)

All run by this verifier in the main checkout, one Gradle invocation at a time, foreground, `--offline`. No OOM kill occurred, so no `--no-daemon` retry was needed.

| Command | Result |
|---------|--------|
| `./gradlew :core:test :providers:test :keystore:testDebugUnitTest :sample:testDebugUnitTest detekt --offline` | BUILD SUCCESSFUL in 34s. Result XMLs: core 694 tests, providers 1821 (3 legs x 609 aggregated), keystore 105, sample 147; 0 failures, 0 errors, 0 skipped. `detekt` (all library modules, zero baseline) green. |
| `./gradlew :providers:testOkhttp521 :providers:testOkhttp550 :providers:verifyOkHttpCompileFloor --offline` | BUILD SUCCESSFUL. Per-leg: `test` 609/0, `testOkhttp521` 609/0, `testOkhttp550` 609/0. |
| `./gradlew :core:apiCheck :providers:apiCheck :keystore:apiCheck --offline` | BUILD SUCCESSFUL (Metalava additive-only compat against the released `api.txt`). |
| `bash scripts/verify-docs-coverage.sh` | `DOC COVERAGE OK checks=25 types=100`, exit 0 |
| `bash scripts/verify-keyaccess-opt-in.sh` | failures 0, exit 0 (two plants went red for the opt-in reason, positive control compiled) |
| `bash scripts/verify-sample-device-guard.sh` (offline fake adb; no device touched) | `SAMPLE DEVICE GUARD OK scenarios=33`, exit 0 |
| `bash scripts/verify-keystore-device-guard.sh` | `DEVICE GUARD OK`, exit 0 |
| `bash scripts/verify-repo-hygiene.sh` | `HYGIENE OK`, exit 0 |
| `bash scripts/verify-negative-controls.sh` | exit 1, `negative-control failures: 3` (all in Part 1 `api.txt missing once released`); Parts 2, 3 and 4 (including the Phase 12 opt-in proof) pass. See Anti-Patterns / Warning W-1. Not a Phase 12 regression. |

Working tree after the runs: no plant files left behind (`git status` shows only pre-existing `.planning/` bookkeeping).

### Probe Execution

Phase declares no `probe-*.sh` probes (the live leg is the runner's `responses_probe`, evidenced above). Not applicable.

### Requirements Coverage

All 11 IDs from the phase (ROADMAP and plan frontmatter) are accounted for; no orphaned requirement maps to Phase 12 in REQUIREMENTS.md.

| Requirement | Plan | Description | Status | Evidence |
|-------------|------|-------------|--------|----------|
| SEAM-01 | 12-02 | `onFailed` hook, else-arm only | SATISFIED | SC1 evidence |
| SEAM-02 | 12-02 | `ReasoningMode`, Builder knobs, byte-identical wire | SATISFIED | SC1 evidence |
| SEAM-03 | 12-01 | `claude-sonnet-5` capability row | SATISFIED | `AnthropicModels.kt`, `AnthropicModelsTest` |
| SEAM-04 | 12-03 | `TierAttempt.carryIn` | SATISFIED | SC2 evidence |
| SEAM-05 | 12-03 | `Unhandled.cappedByPolicy` + KDoc | SATISFIED | SC2 evidence |
| SEAM-06 | 12-06 | `Extraction.callId`, `ExecutedAction.providerCallId` | SATISFIED | SC2 evidence |
| SEAM-07 | 12-04 | opt-in `KeyAccess` + `ApiKeyStore` ctor | SATISFIED | SC3 evidence (plain interface per binding D-07 correction) |
| PROV-14 | 12-01 | no `reasoning_effort: none` to Responses-only models, golden | SATISFIED | SC4 evidence |
| PROV-15 | 12-01 | W04 400 -> `ModelUnsupported`, MockWebServer | SATISFIED | SC4 evidence, 3 legs |
| PROV-16 | 12-05, 12-08 | live `:sample` smoke on `gpt-6-astra` | SATISFIED (code + evidence) | SC5 evidence. REQUIREMENTS.md still shows the box unchecked and the traceability row "Pending": see W-2 |
| DOC-01 | 12-07 | 3 stumbles + cache note | SATISFIED | SC3 evidence |

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| (modified source, docs, scripts) | - | `TBD`/`FIXME`/`XXX` scan over every file changed since `v1.0.1` | none found | no debt markers |
| `config/detekt/detekt.yml` | 11-14 | `LongParameterList.constructorThreshold` 8 -> 9 for every module to fit `ModelRequest` (review IN-04, skipped by the fixer as a documented policy call) | Info | gate loosened by one for all modules, with the required justification comment; not a goal blocker |
| `providers/.../ChatErrors.kt` | ~147 | effort-rejection backstop reports `ModelUnsupported` for an engine wire-table gap too, with no distinguishing trace code (review IN-03, skipped: a new public `TraceCode` is an api change D-02 forbids this phase) | Info | diagnosability only; `errorType` still carries `unsupported_value` |
| `SingleShotStrategy.kt` `onFailed` | - | also receives runtime failures of an on-device provider that bound successfully (review WR-01, resolved by KDoc + API.md wording, not a behavior change, per D-01) | Info | documented; app should branch on the reason |

### Warnings (non-blocking, for the orchestrator)

- **W-1: `scripts/verify-negative-controls.sh` Part 1 is stale since the v1.0.0 cut (3 failures).** The three plants `api.txt missing once released (core|providers|keystore)` expect `verifyApiDumpPresent -PvaeAssumeReleased` to go red, but the build task only fails when `api.txt` is absent, and `api.txt` has existed for all three modules since `c43c65e` (Phase 11 v1.0.0 baseline). The plant never removes the file, so the task stays green. Phase 12 did not touch `*/api.txt` or `verifyApiDumpPresent` (`git diff v1.0.1 -- */api.txt` empty); the Phase 12 change to this script (`4d8327e`, Part 4 opt-in proof) passes. The script is not part of `check`. Recommend a follow-up fix (make the plant delete/restore `api.txt`), tracked outside this phase; it does not affect any Phase 12 truth.
- **W-2: REQUIREMENTS.md bookkeeping.** PROV-16 is still `[ ]` / "Pending" (lines 30 and 117) although the live evidence exists and passes. The orchestrator should tick it when it commits this verification (I was told not to touch planning tracking files). ROADMAP.md's Phase 12 checkbox line is likewise unticked.
- **W-3: Review items open by decision, not by neglect.** `12-REVIEW-FIX.md`: 5 fixed, 2 skipped (IN-03, IN-04) with stated reasons; 0 critical, 0 open warnings. WR-01's fix is wording only and may merit a quick human read.
- **Phase 19 note (already in `12-LIVE-LEG-DECISION.md`).** PROV-16's only reachable live result for `gpt-6-astra` on Chat Completions is the typed `ModelUnsupported`; Phase 19's D-13 wording ("must succeed") should be restated to reuse this evidence line.

### Human Verification Required

None. The live leg is already captured and committed; every behavior-dependent truth has a passing named test. (Optional: a human glance at the WR-01 KDoc/API.md wording, which is documentation judgment, not a gate.)

### Gaps Summary

No gaps. Every ROADMAP success criterion is backed by source I read, tests that I re-ran green (including all three OkHttp legs and Metalava compat), a cross-module negative-compile proof I executed, and a committed live PASS line. The only red signal in the run (`verify-negative-controls.sh` Part 1) is a stale pre-existing script plant unrelated to Phase 12's contract, recorded as warning W-1.

---

_Verified: 2026-10-05_
_Verifier: Claude (gsd-verifier)_
