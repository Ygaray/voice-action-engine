---
status: complete
result: all_pass
gate: 1
phase: 12-wave-1-seams-w04-fix
source: [ROADMAP Phase 12 success criteria 1-5]
device: none (headless JVM library phase; no adb, no device, no emulator touched by this run. SC5 evidence is from the prior committed live TESTER run, not re-driven)
apk: n/a (no artifact built or installed; HEAD 2d5b57ba30fc2ec702df10a8ac71b416aa2a7b83)
run: 2026-10-05T22:34:00Z @ 2d5b57b
---

# Self-UAT Log: Phase 12 (Wave-1 Seams & W04 Fix), one log for the phase

**Target:** headless (Gradle JVM test harness: scripted fakes in `:core`, the real Anthropic and Chat encoders and error classifier over a local `MockWebServer` in `:providers`, `:keystore` JVM tests, `:sample` unit tests, plus the two shell proofs). Same headless handling as `01-SELF-UAT.md` and `09-SELF-UAT.md`. **Device: none this run.** The TESTER window (RT-01) was granted for plan 12-08 Task 3 only and is closed; this run made no adb call and no live provider call, and read no key. SC5 is audited from the evidence committed by that earlier live run (`fcec123`); it was NOT re-driven.
**Build identity:** HEAD `2d5b57ba30fc2ec702df10a8ac71b416aa2a7b83`. `git status --short core providers keystore sample config scripts gradle INTEGRATION.md API.md` is empty (0 lines) before the run, and the `verify-keyaccess-opt-in.sh` plant was removed by its trap (sample tree clean after).
**Pre-flight:** JDK 17, Gradle wrapper 9.4.1, host memory tight (swap full), so one foreground `--offline` Gradle invocation at a time, no OOM kill, no `--no-daemon` retry needed.
**Unit suite (forced fresh):** `rm -rf */build/test-results`, then `./gradlew --offline --no-build-cache :core:test --rerun :providers:test --rerun :providers:testOkhttp521 --rerun :providers:testOkhttp550 --rerun :keystore:testDebugUnitTest --rerun :sample:testDebugUnitTest --rerun detekt --rerun` -> `BUILD SUCCESSFUL in 1m 15s` (`9 executed`, none FROM-CACHE). A first invocation (`:core:test :providers:test ... detekt` plus `metalavaCheckCompatibility --rerun`) restored most tests from the build cache, so I discarded its test counts and forced the run above. JUnit XML parsed by script (`newest_ts` 2026-10-05T22:34-22:35Z, i.e. this run):

| Result dir | Suites | Tests | Failures | Errors | Skipped |
|---|---|---|---|---|---|
| `core/build/test-results/test` | 68 | 694 | 0 | 0 | 0 |
| `providers/build/test-results/test` (OkHttp 4.12.0 floor) | 50 | 609 | 0 | 0 | 0 |
| `providers/build/test-results/testOkhttp521` (5.2.1) | 50 | 609 | 0 | 0 | 0 |
| `providers/build/test-results/testOkhttp550` (5.5.0) | 50 | 609 | 0 | 0 | 0 |
| `keystore/build/test-results/testDebugUnitTest` | 16 | 105 | 0 | 0 | 0 |
| `sample/build/test-results/testDebugUnitTest` | 20 | 147 | 0 | 0 | 0 |

**Other gates, each run by me:** `./gradlew :providers:verifyOkHttpCompileFloor :core:metalavaCheckCompatibility --rerun :providers:metalavaCheckCompatibility --rerun :keystore:metalavaCheckCompatibilityRelease --rerun` -> `BUILD SUCCESSFUL` (the three Metalava tasks executed, not up-to-date); `detekt` on core/providers/keystore green (zero baseline). `bash scripts/verify-docs-coverage.sh` -> `DOC COVERAGE OK checks=25 types=100`. `bash scripts/verify-keyaccess-opt-in.sh` -> `keyaccess opt-in failures: 0`, exit 0.
**Coverage/Nyquist:** each ROADMAP criterion was mapped to named passing tests below, re-derived from the ROADMAP text and not from `12-VERIFICATION.md`, `12-SUMMARY`s or `COVERAGE.md`.
**Seed/fixture integrity:** fixtures are in-tree test doubles (`FakeAiProvider`, recording sinks, local `MockWebServer`, golden JSON files). Nothing external, nothing to restore. Goldens: `git diff v1.0.1 --stat -- providers/src/test/resources` shows one file changed, `golden/chat/requests/openai.json`, 72 insertions and 0 deletions, so every pre-existing golden is byte-identical to v1.0.1 and the astra case is purely added.
**Prior verdicts audited, not relied on:** `12-VERIFICATION.md` (status passed, 5/5), `12-REVIEW-FIX.md`, `12-08-SUMMARY.md`. Every count and claim used below was re-observed this run. No contradiction found. One stale script confirmed as a known non-Phase-12 issue (see Notes).

## Criteria

### 1. SC1: `onFailed` turns a provider HTTP 400 into `Escalate` and never fires for ceiling/gate/`strategy_error`; unset still yields `Failed(reason, details)`; `reasoning` defaults to `OFF` with wire bytes unchanged on Anthropic, OpenAI and OpenRouter; `claude-sonnet-5` has its own capability row
result: passed
- **Rung:** 1 (unit, golden and MockWebServer wire tests; no visual claim exists)
- **Target:** headless JVM harness
- **Expected:** a hook set on `SingleShotStrategy.Builder` converts a provider failure to `Escalate` and the next tier runs, with no decorator; the hook is not consulted for a token ceiling, a gate hold, a throwing resolver, a missing credential, or `strategy_error`; without the hook the same failure is `Failed(reason, details)`. `ModelRequest.reasoning` and both Builders default `OFF`; `OFF` and `PROVIDER_DEFAULT` leave every dialect's request bytes equal to v1.0.1. `claude-sonnet-5` is an exact row (forced tool choice allowed, explicit breakpoints, 1,024-token minimum prefix) that dated or re-cased ids do not inherit.
- **Arranged (seeded):** scripted provider results (HTTP 400 `Failed`, no-tool-call, refusal, token-ceiling stop, held gate, throwing resolver, throwing hook, absent credential), a two-tier ladder; request fixtures per dialect and the v1.0.1 golden files.
- **Did (drove):** forced re-run of `:core:test` and `:providers:test` (3 OkHttp legs); listed the test names from the fresh XML and read the load-bearing assertions.
- **Observed (all passing, fresh XML):**
  - Hook: `SingleShotOutcomeMappingTest` 34/34: `anOnFailedHookTurnsAProviderHttpErrorIntoEscalateAndTheNextTierRuns`, `theOnFailedHookReceivesTheExactReasonAndDetailsOfTheFailure`, `withoutAnOnFailedHookTheSameFailureStillEndsFailedWithReasonAndDetails`.
  - Falsifiers (hook must NOT fire): `theOnFailedHookIsNotCalledWhenTheTokenCeilingStopsTheTier`, `theOnFailedHookIsNotCalledForAGateHoldOrAThrowingResolver`, `theOnFailedHookIsNotCalledForAMissingCredentialAndTheNextTierNeverRuns`, `theOnFailedHookIsNotCalledForNoToolCallOrRefusalFailuresOrAnswers`; and `anOnFailedHookThatThrowsEndsTheTierAsAStrategyError`, `aWriteIsNeverMadeOnAnyNonResolutionPath`.
  - Reasoning default and wire parity: `AgenticLoopReasoningTest` 2/2 (`withoutReasoningEveryTurnSendsOff`, `theBuildersReasoningIsOnEveryTurn`). `ReasoningWireParityTest` 3/3 on each of the floor, 5.2.1 and 5.5.0 legs: `anthropicBytesAreIdenticalForEveryModeOnAForcedAndAReshapedModel`, `openAiOffAndProviderDefaultBothEncodeToTheV10Golden`, `openRouterOffAndProviderDefaultEncodeToTheV10GoldensForOpenAiAnthropicAndAstraModels`. Independent check: no existing golden file changed since `v1.0.1` (insertions only, see fixture note).
  - Capability row: `AnthropicModelsTest` 6/6 on each leg, including `sonnetFiveHasItsOwnRowThatAllowsForcingAndCachesFromAThousandTwentyFourTokens` and `aDatedOrDifferentlyCasedSonnetFiveIdGetsTheUnknownDefault` (the negative control).
- **Evidence:** `core/build/test-results/test/TEST-...{SingleShotOutcomeMappingTest,AgenticLoopReasoningTest}.xml`; `providers/build/test-results/{test,testOkhttp521,testOkhttp550}/TEST-...{ReasoningWireParityTest,AnthropicModelsTest}.xml`; `providers/src/test/resources/golden/chat/requests/openai.json` (git diff v1.0.1).

### 2. SC2: `TierAttempt.carryIn`, `Unhandled.cappedByPolicy`, `Extraction.callId` and `ExecutedAction.providerCallId` are reported; strictly additive (Metalava compat passes; the 7-arg `ModelRequest` and 2-arg `Extraction` JVM constructors remain)
result: passed
- **Rung:** 1 (unit + Metalava compat task)
- **Target:** headless JVM harness
- **Expected:** `carryIn` is a boolean that is true only for a tier that received a non-null carry and never exposes content; `cappedByPolicy` is true exactly when policy skipped at least one tier and no tier handled the command (`maxTier`, provider restriction and offline-only all count), false when nothing was skipped, and false when every tier is skipped (that is a loud refusal, not `Unhandled`); the provider's tool-call id reaches `ExecutedAction.providerCallId` byte for byte on every write path and is null for zero-call tiers; the old constructors still exist and Metalava additive-only compat passes.
- **Arranged (seeded):** ladders with a carry across tiers, `TierPolicy` with `maxTier`, `allowedProviders`, `offlineOnly` and an unavailable on-device hook, scripted tool calls with ids `call_7`, `a1`/`a2`, and a held-then-committed proposal.
- **Did (drove):** forced re-run; read the load-bearing assertions in `CommitPathTest`, `HeldReportingTest`, `AgenticLoopDispatchTest`; ran Metalava compat for all three modules.
- **Observed (all passing, fresh XML):**
  - carryIn: `TierWalkTest` 13/13 (`carryInIsTrueOnlyForATierThatReceivedANonNullCarry`, `carryInIsFalseForATierAfterANoMatchThatFollowedACarry`, `carryInIsFalseAfterANoMatchClearedTheCarry`, `carryInIsFalseForASingleTier`); `TraceTest` 14/14 including `carryInRendersAsABooleanAndNeverTheCarrysContent`.
  - cappedByPolicy: `TierPolicyTest` 32/32 including `maxTierCuttingTheLadderThenNoHandlerIsCapped`, `aProviderRestrictionSkippingATierThenNoHandlerIsCapped`, `offlineOnlyDroppingACloudTierThenNoHandlerIsCapped`, the negatives `nothingSkippedByPolicyIsNotCappedWhetherTheLastTierHandsUpOrFindsNoMatch`, `aTierSkippedByPolicyDoesNotMakeALaterCompletionUnhandled`, `aSelectorStartingPastEarlierTiersIsNotAPolicySkip`, `aLadderWithEveryTierSkippedByPolicyFailsInsteadOfBeingUnhandled`, and `unhandledToStringShowsTheFlag`. The KDoc wording (covers offline-only) is a doc claim: read in `CommandOutcome.kt` (`Unhandled.cappedByPolicy` KDoc).
  - Call ids: `CommitPathTest` asserts `providerCallId == "call_7"` on both the outcome and the sink action, and `assertNull(...providerCallId)` for a no-call path; `SingleShotResolveTest` asserts `List(4){CALL_ID}` across `executed` and sink actions, including the amended-proposal path; `AgenticLoopDispatchTest` asserts `listOf("a1","a2")`; `HeldReportingTest` asserts null for the held and then committed zero-call path; `RedactionCanaryTest` asserts the id equals on parent and child outcomes.
  - Additive: `:core:metalavaCheckCompatibility`, `:providers:metalavaCheckCompatibility`, `:keystore:metalavaCheckCompatibilityRelease` all executed (not up-to-date) and BUILD SUCCESSFUL against the v1.0.x `api.txt`. `git diff v1.0.1 -- */api.txt` is empty and `core/api.txt` still lists the 7-arg `ModelRequest` constructor and the 2-arg `Extraction(String, JsonObject)` constructor (lines 1593 and 1053), so compat is checked against a baseline that has them.
- **Evidence:** `core/build/test-results/test/TEST-...{TierWalkTest,TraceTest,TierPolicyTest,CommitPathTest,SingleShotResolveTest,AgenticLoopDispatchTest,HeldReportingTest,RedactionCanaryTest}.xml`; Gradle output of the Metalava tasks; `core/api.txt`.

### 3. SC3: `ApiKeyStore(dataStore, slots, keyAccess)` compiles only with an explicit `@OptIn(DelicateKeyAccess::class)`; the INTEGRATION.md software `KeyAccess` fake compiles and round-trips a key on the JVM; INTEGRATION.md fixes the three v1.0.1 stumbles and notes a single-tool SingleShot prefix will not cache on Haiku/OpenAI
result: passed
- **Rung:** 3 (negative-compile proof run as a script plus JVM tests plus doc-coverage script; no visual claim)
- **Target:** headless (Gradle compile of a plant inside `:sample`, JVM unit tests)
- **Expected:** a consumer in another module that omits the opt-in gets a compile error naming the marker; the same code with `@OptIn` compiles; implementing `KeyAccess` without opt-in also fails; the doc fake is the tested code; the three stumbles and the cache note are present.
- **Arranged (seeded):** `scripts/verify-keyaccess-opt-in.sh` writes plants into `:sample` (a module with no opt-in flag), and removes them on exit.
- **Did (drove):** `bash scripts/verify-keyaccess-opt-in.sh` (foreground, one Gradle compile per plant); `bash scripts/verify-docs-coverage.sh`; read `INTEGRATION.md` lines 98, 417, 446-460, 648-651, 719.
- **Observed:**
  - Script output: `ok [constructor without @OptIn] went red (opt-in error on ZzOptInPlant.kt)`; `ok [KeyAccess implemented without @OptIn] went red (opt-in error on ZzOptInPlant.kt)`; `ok [positive control: same code with @OptIn] compiled`; `keyaccess opt-in failures: 0`; exit 0. The positive control makes the red result attributable to the opt-in alone.
  - `ApiKeyStoreKeyAccessTest` 3/3 (`aSavedKeyRoundTripsThroughThePublicConstructor`, `aFreshStoreOverTheSameDataStoreAndKeysReadsTheKeyBack`, `readingAnAbsentProviderNeverCreatesAKey`). `DocSnippetsTest.theKeystoreFakeRoundTripsAKeyOnTheJvm` passes, and `verify-docs-coverage.sh` -> `DOC COVERAGE OK checks=25 types=100` ties the INTEGRATION.md `keystore-fake` snippet to the tested copy. The snippet is 10 code lines, annotated `@OptIn(DelicateKeyAccess::class)`.
  - Docs: line 417 states `ProviderId.toString()` prints its wire value (anthropic, openai, openrouter, on_device); lines 648-651 give `runTest` and JUnit imports; line 98 says a SingleShot tier cannot serve reads; line 719 says a single-tool SingleShot prefix is usually shorter than the provider's minimum cacheable prefix so it will not cache.
- **Evidence:** script stdout above; `keystore/build/test-results/testDebugUnitTest/TEST-...ApiKeyStoreKeyAccessTest.xml`; `sample/build/test-results/testDebugUnitTest/TEST-...DocSnippetsTest.xml`; `INTEGRATION.md`.

### 4. SC4: W04 fixed and proven on the JVM: (a) the encoder never sends `reasoning_effort: "none"` for `gpt-6-astra` or any `OpenAiModelRules` model that rejects it; (b) a MockWebServer replay of the captured W04 400 body maps to `ModelUnsupported`, not `http_error`, on all three OkHttp legs
result: passed
- **Rung:** 1 (encoder golden plus MockWebServer replay, run on each OkHttp leg)
- **Target:** headless JVM harness (real `ChatEncoder`, `ChatErrors`, `OpenAiChatProvider` over a local `MockWebServer`)
- **Expected:** a direct Responses-only model encodes to a golden body with no `reasoning_effort`; every direct id that rejects `none` sends no effort; the 400 with `param=reasoning_effort` and `code=unsupported_value` yields reason `model_unsupported` (not `http_error`) with HTTP 400; the classifier only fires when all three facts hold; no server text surfaces.
- **Arranged (seeded):** the W04 body fields as a MockWebServer answer; request fixtures for `gpt-6-astra` direct and routed, `-pro`/`-codex` ids, and earlier reasoning models.
- **Did (drove):** forced re-run on the floor, 5.2.1 and 5.5.0 legs (`test`, `testOkhttp521`, `testOkhttp550`); read the added golden case and the test names.
- **Observed (each class counts identical on all three legs, 0 failures):**
  - (a): `ChatEncoderTest` 29/29 incl. `aDirectResponsesOnlyModelEncodesToTheGoldenBodyWithNoReasoningEffort` and `aDirectProModelSendsNoReasoningEffort`; `OpenAiModelRulesTest` 15/15 incl. `everyDirectIdThatRejectsEffortNoneSendsNoEffort`, `theResponsesOnlyPairTakesEffortLowOnlyThroughARouter`, `directProAndCodexIdsAreRefusedForToolsBeforeAnyCall`, `everyIdThatWorkedBeforeKeepsEffortNone` (negative control: unaffected ids keep `none`), `laterGenerationsTakeReasoningEffortNoneWithTools`. The added golden case `astra_direct` (`"model": "gpt-6-astra"`) contains no `reasoning_effort` in any added line.
  - (b): `ChatTransportTest` 27/27 incl. `aDirectResponsesOnlyModelUnderAToolsOverrideSendsNoEffortAndTheW04AnswerIsModelUnsupported`, `aToolsCommandOnAResponsesOnlyModelIsRefusedWithZeroRequests`; `ChatErrorMapTest` 18/18 incl. `anUnsupportedReasoningEffortValueIsModelUnsupportedOnlyForAllThreeFacts` (guards against over-matching), `noServerTextSurfacesFromAnUnsupportedReasoningEffortAnswer`.
  - `:providers:verifyOkHttpCompileFloor` BUILD SUCCESSFUL (A1 compile floor intact).
- **Evidence:** `providers/build/test-results/{test,testOkhttp521,testOkhttp550}/TEST-...{ChatEncoderTest,OpenAiModelRulesTest,ChatTransportTest,ChatErrorMapTest}.xml`; `providers/src/test/resources/golden/chat/requests/openai.json`.

### 5. SC5: W04 proven live: a `:sample` smoke call to `gpt-6-astra` under the `supportsTools` override returns the typed `ModelUnsupported` (never `http_error`); evidence logged; spend-capped OpenAI test key; request count bounded
result: passed
- **Rung:** 3 (audit of committed live log evidence plus the leg code and the judge's JVM tests). **Device: none this run; this criterion was NOT re-driven.** The live call happened in the earlier plan 12-08 Task 3 run under RT-01 (TESTER window now closed); this run audits what that run left behind, and the leg and runner code are re-observed.
- **Target:** headless audit of committed evidence from a prior live TESTER run (`R5CT10XNKQN`, per the capture header)
- **Expected:** a committed evidence file whose lines show `reason=model_unsupported`, `http=400`, a provider call actually made (n=1), a PASS verdict, and a bounded budget (1 OpenAI request against a ceiling of 4 requests / USD 0.05); the filter still accepts the file; the leg really applies the `supportsTools` override and passes only on the typed reason after a real provider status.
- **Arranged (seeded):** none (read-only audit).
- **Did (drove):** `bash scripts/sample-evidence-filter.sh < evidence/gate1-responses_probe.txt` (no device, no adb); `git log` on the file; read `LegRunner.kt:176-179`; ran `SmokeLegTest`; compared main-source changes since the captured head.
- **Observed:**
  - Evidence file header: `# gate1 leg=responses_probe captured_utc=2026-10-05T22:03:10Z target=R5CT10XNKQN head=e3de426508`, lines: `VAE_ATTEMPT leg=responses_probe provider=openai n=1 kind=initial http=400 ...`, `VAE_TURN ... model=gpt-6-astra ... latency_ms=1142`, `VAE_OUTCOME ... kind=failed ... reason=model_unsupported executed=0 committed=0`, `VAE_VERDICT leg=responses_probe verdict=PASS reason=model_unsupported http=400 trigger=ui`, `VAE_BUDGET core=0 optional=1 anthropic=0 openai=1 openrouter=0 est_usd=unknown`. Not `http_error`; the call used one OpenAI request and none on the other providers.
  - Re-ran the filter on the committed file: `FILTER OK kept=5 dropped=1`, exit 0, and the kept output equals the file body (header excluded), so no un-allow-listed content is in the evidence. File committed in `fcec123`.
  - Leg code: `LegRunner.kt:179` `if (spec.kind == LegKind.RESPONSES_PROBE) capabilities(spec.provider, spec.model) { supportsTools = true }`. `SmokeLegTest` 9/9 including `theResponsesProbePassesOnlyOnTheTypedModelUnsupported`, `theResponsesProbeFailsOnAnyOtherAnswer`, `aModelUnsupportedWithNoProviderCallIsAFailNotLiveProof` (a refusal before any provider call cannot pass as live proof).
  - Staleness check: the captured head `e3de426508` is an ancestor of HEAD; `git diff e3de426508 HEAD -- core/src/main providers/src/main keystore/src/main sample/src/main` is 6 files, 17 insertions, 2 deletions, and the only `providers/` and `core/` lines are KDoc comment text (WR-01 and IN-02 wording). The keystore lines are the `@OptIn` and `@DelicateKeyAccess` annotation narrowing (WR-02). So the live-path code (encoder, rules, classifier) is the same as the code that produced the live line.
  - `12-08-SUMMARY.md` records `PROV16 LIVE SPEND: requests=2 (host_probe=1 device=1) est_cost_usd=0.00 ceiling=4`; the on-device keys were deleted and the package uninstalled. Not re-verifiable from here without a device (none was touched); taken as a claim.
- **Evidence:** `.planning/phases/12-wave-1-seams-w04-fix/evidence/gate1-responses_probe.txt`; `sample/build/test-results/testDebugUnitTest/TEST-...SmokeLegTest.xml`; `12-08-SUMMARY.md`.

## Summary

total: 5
passed: 5
partial: 0
failed: 0
infra: 0

## Notes / anomalies (for the Gate-2 reviewer)

- **SC5 is audit-only this run.** It rests on one committed live capture plus code and judge tests; the live request itself was not repeated. If the owner wants a re-drive, it needs a new TESTER window and the spend-capped key (ceiling spent: 1 host probe + 1 device request of 4).
- **Stale script, not Phase 12:** `scripts/verify-negative-controls.sh` Part 1 (three `api.txt missing once released` plants) has gone stale since the v1.0.0 cut, per `12-VERIFICATION.md` W-1. I did not re-run it (outside this phase's criteria); Phase 12's own proof, `verify-keyaccess-opt-in.sh`, passes on its own.
- **Bookkeeping left to the orchestrator (not touched by this run):** `REQUIREMENTS.md` PROV-16 box and the ROADMAP Phase 12 checkbox are still unticked per `12-VERIFICATION.md` W-2.
- **Accepted info items** (from `12-REVIEW-FIX.md`, not blockers): IN-03 (no distinct trace code for an engine wire-table gap vs a model rejecting effort) and IN-04 (`LongParameterList.constructorThreshold` 8 -> 9 for all modules) were skipped by decision, with a justification comment in `config/detekt/detekt.yml`.
- **Phase 19 note** (already in `12-LIVE-LEG-DECISION.md`): the only reachable live result for `gpt-6-astra` on Chat Completions is the typed `ModelUnsupported`; Phase 19 should reuse this line instead of expecting a success.
- No FAIL, no INFRA, no code changed by this run.

## Findings routed to gap-closure (if any)

None.

## Verdict

All 5 criteria PASS (SC1-SC4 re-observed on the JVM this run; SC5 from the committed prior live run, re-audited, not re-driven) -> Gate-1 complete; human Gate-2 deferred to milestone completion (registered in `.planning/uat-pending/12-wave-1-seams-w04-fix.md`; ledger regenerated).
