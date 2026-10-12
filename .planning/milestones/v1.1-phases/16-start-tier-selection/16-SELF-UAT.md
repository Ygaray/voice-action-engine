---
status: complete
result: all_pass
gate: 1
phase: 16-start-tier-selection
source: [ROADMAP Phase 16 success criteria 1-5]
device: none (headless JVM library phase; no adb, no device, no emulator touched by this run. No live provider call, no key read, no provider spend. The live router wording check is the Phase 19 Gate-1 router leg and is not re-driven here)
apk: n/a (no artifact built or installed; HEAD 2a537db041f0225f6a9f2c09db8e71d940ad515e on gsd/phase-16-start-tier-selection; code under core/ providers/ keystore/ sample/ config/ scripts/ gradle/ API.md INTEGRATION.md has 0 uncommitted changes)
run: 2026-10-06T21:20:00Z @ 2a537db
---

# Self-UAT Log: Phase 16 (Start-Tier Selection), one log for the phase

**Target:** headless (Gradle JVM test harness: scripted `ScriptedPicker`, scripted tiers, a `FakeAiProvider`, the real `CommandPipeline` / `TierWalk` / `PolicyPreCheck` / `RunRecorder` / `SelectionBook`, and the real Phase 14 grammar head for the pre-pass proof). Same headless handling as `15-SELF-UAT.md`. **Device: none.** No adb call, no live provider call, no key read; `env | grep -c VAE_LIVE` is 0. Every Phase 16 suite runs inside `NoNetworkGuard.during`, so a test that reached the network would fail.
**Build identity:** HEAD `2a537db041f0225f6a9f2c09db8e71d940ad515e`. `git status --short core providers keystore sample config scripts gradle INTEGRATION.md API.md` is empty (0 lines). `git diff 9c88961 HEAD --stat -- core/api.txt providers keystore sample gradle` is empty (frozen API and the other modules untouched since the phase base). The phase's `core/src/main` diff is 15 files, +648/-33, all under `pipeline/`, `telemetry/` and `strategy/plan/PlanBinding.kt`.
**Pre-flight:** JDK 17.0.19, Gradle wrapper 9.4.1. Host memory tight (swap 2046/2047 MB used, about 11.5 GB available), so one foreground `--no-daemon --max-workers=2 --offline` invocation, 600 s timeout, no daemon stop. No earlyoom kill, no retry.
**Unit suite (forced fresh):** `rm -rf core/build/test-results`, then
`./gradlew --offline --no-daemon -Dorg.gradle.daemon=false -Dorg.gradle.workers.max=2 --max-workers=2 -Dorg.gradle.parallel=false --no-build-cache --console=plain :core:test --rerun :core:detekt --rerun :core:metalavaCheckCompatibility --rerun` -> `BUILD SUCCESSFUL in 44s` (3 executed: `:core:test`, `:core:detekt`, `:core:metalavaCheckCompatibility`; none FROM-CACHE). JUnit XML parsed by script (mtimes 15:19 MDT, i.e. this run): 110 suites, **1091 tests, 0 failures, 0 errors, 0 skipped**. Delta against Phase 15's log: +73 core tests (1018 -> 1091), consistent with the Phase 16 additions (68 tests in the eight start-tier classes plus the RT-01 and picker-fixture extras).
**Other gates, each run by me:** detekt on `:core` green (zero baseline); `:core:metalavaCheckCompatibility` green against the committed v1.0.1 `api.txt` (strictly additive); `bash scripts/verify-docs-coverage.sh` -> `DOC COVERAGE OK checks=25 types=107` (104 at Phase 15, +3 new public types); `bash scripts/verify-repo-hygiene.sh` -> `HYGIENE OK`. `:providers` and `:keystore` were not re-run: their sources and `api.txt` have an empty diff against the phase base and Phase 16 added no provider surface (the router uses the existing `ModelRouter.bind` seam), so the Phase 15 matrix result stands for them.
**Coverage/Nyquist:** each ROADMAP criterion is mapped to named, passing tests below, re-derived from the ROADMAP text, not from `16-VERIFICATION.md`, the `16-0x-SUMMARY.md` files or `16-VALIDATION.md` (still a plan-time draft; the finalizer owns its sign-off, not this gate).
**Seed/fixture integrity:** fixtures are in-tree test doubles (`ScriptedPicker`, `ScriptedStrategy`, `zeroCallTier` / `llmTier` helpers, `FakeAiProvider`, `MappedSelection`, the real grammar head). Nothing external, nothing to restore. `*Live*` suites: 0 result files (the `excludeTestsMatching("*Live*")` filter holds).
**Prior verdicts audited, not relied on:** `16-VERIFICATION.md` (status passed, 5/5), the seven `16-0x-SUMMARY.md` files, `16-REVIEW-FIX.md`. Every count and claim below was re-observed this run and I read the load-bearing production code (`StartTierPicking.kt`, `TierWalk.walkPicked`, `PolicyPreCheck.tierPermitted`) and the load-bearing assertions. No contradiction found.

## Criteria

### 1. SC1: `TierSelector.Custom(picker)`: the suspend picker sees the input and the eligible LLM tier ids, the walk starts at the tier it returns, and a model call made through `PickContext` appears in the trace and counts against the run's token budget
result: passed
- **Rung:** 1 (pipeline-level JVM tests; no visual claim exists)
- **Target:** headless JVM harness
- **Expected:** the picker receives `(input, eligible ids)`; the picked tier is the first to execute (earlier LLM tiers bypassed, never run); a turn the picker records via `PickContext` lands in the trace and in the token total that later tiers see as `tokensUsed`; the picker's model is bound through the router so selection, credential and policy gates apply.
- **Arranged (seeded):** a zero-call head returning `NoMatch`, three LLM tiers with execution counters, a `ScriptedPicker`, a `FakeAiProvider`, a recorded 5-token picker turn.
- **Did (drove):** forced re-run of `:core:test`; listed `StartTierPickerTest` names from the fresh XML; read `StartTierPicking.ask` / `RunPickContext` and the assertions at `StartTierPickerTest` lines 32-64 and 124-126.
- **Observed (all passing, fresh XML):** `StartTierPickerTest` 9/9. `aCustomPickerChoosesWhereTheLlmWalkStarts`; `thePickerTurnIsInTheSelectionNotInAnyAttempt` (`attempts.sumOf { usage.total } == 0`, `trace.selection.usage.total == 5`, `trace.usage.total == 5`, so the picker's tokens are in the trace and in the run total but attributed to no tier); the next tier observes `session.tokensUsed` including the picker's tokens (budget accounting, line 32); `anAppNamesItsPickerAndTheSelectionSeamBindsItsModel` (the picker's model call is routed through the selection seam under the picker id); `pickingTheFirstLlmTierBypassesNothing`; `theSelectionIsDeliveredAsAnEventBetweenThePickAndThePickedTier` (`StartTierSelected` event ordering) and the control `aLinearRunDeliversNoStartTierSelectedEvent`. Code: `ask` calls `spec.picker.pick(input, llm, RunPickContext(...))`; `recordTurn` goes to `recorder.turnRecorded`, which adds to `tokenTotal`; `startIn` returns the index of the picked id so `climb(rest.drop(start))` begins there.
- **Evidence:** `core/build/test-results/test/TEST-io.github.ygaray.voiceactionengine.core.StartTierPickerTest.xml`; `core/src/main/kotlin/.../pipeline/StartTierPicking.kt`; `.../pipeline/TierWalk.kt`.

### 2. SC2: a zero-call tier at the ladder head (the grammar tier) always runs first as a free pre-pass; the picker runs only if that tier hands the command over, and its eligible list never contains a zero-call tier
result: passed
- **Rung:** 1 (pipeline tests, including the real Phase 14 grammar head)
- **Target:** headless JVM harness
- **Expected:** head tiers (`providers.isEmpty()`, taken while contiguous from the head) run first through the normal gate/suppression/trace path; a head that completes, fails, or has already written never reaches the picker; a head that hands over passes its carry to the picked tier by identity; the eligible list holds only model tiers, including when a zero-call tier sits mid-ladder.
- **Arranged (seeded):** a `NoMatch`/`Completed`/failed/escalating zero-call head, a mid-ladder zero-call tier, the real grammar head with a matching and a non-matching phrase, a recording picker.
- **Did (drove):** forced re-run; listed `StartTierPrePassTest` names; read `TierWalk.walkPicked` (`head = takeWhile { providers.isEmpty() }`, `climb(head)` first, picker only in the null-outcome branch) and `StartTierPicking.startIn` (`llm = rest.filter { providers.isNotEmpty() }`).
- **Observed (all passing, fresh XML):** `StartTierPrePassTest` 9/9. Falsifiers: `aHeadThatCompletesNeverCallsThePicker`, `aHeadThatFailsEndsTheCommandWithItsReasonAndNeverCallsThePicker`, `aHeadThatWroteThenEscalatesIsSuppressedAndThePickerIsNeverCalled`; the real-grammar proof the ROADMAP names: `aMatchingPhraseOnTheRealGrammarHeadCompletesWithNoPickerAndNoProviderCall` and `aPhraseTheRealGrammarHeadDoesNotKnowCallsThePickerWithOnlyTheModelTierIds`; `theHeadsEscalationCarryReachesThePickedTierByIdentity`; `everyZeroCallHeadTierRunsBeforeThePickerAndTheLastCarryIsPassedOn`; `aMidLadderZeroCallTierIsNeverOffered`; `aPickedTierThatWroteThenEscalatesIsSuppressedAndNoLaterTierRuns`. Also `StartTierFallbackTest.aMidLadderZeroCallIdFallsBackToLinear` / `theZeroCallHeadIdFallsBackToLinear` (a picker that names a zero-call id is refused).
- **Evidence:** `core/build/test-results/test/TEST-...StartTierPrePassTest.xml`, `...StartTierFallbackTest.xml`; `TierWalk.kt`, `StartTierPicking.kt`.

### 3. SC3: a picker that returns null, returns an ineligible id or throws sends the walk down Linear with a `router_fallback` trace code; the command never fails because of the picker; cancellation still propagates
result: passed
- **Rung:** 1 (pipeline tests)
- **Target:** headless JVM harness
- **Expected:** null, unknown id, zero-call id, thrown exception, and a hung picker (timeout) each yield `ROUTER_FALLBACK` plus a Linear walk from the first model tier, with a trace selection record and no leaked message/context; a foreign cancellation inside the picker is a fallback, but the caller's own cancellation (and the command deadline) still propagates and leaves a trace carrying the pick.
- **Arranged (seeded):** scripted pickers (null, unknown id, zero-call id, throwing, hanging, cancelling), a pipeline deadline, a caller-cancelled coroutine.
- **Did (drove):** forced re-run; listed `StartTierFallbackTest` names; read `StartTierPicking.ask` (`guarded(onFault = { null }) { withTimeoutOrNull(pickerTimeoutMillis) {...} }`, `picked = choice?.takeIf { it in llm }`).
- **Observed (all passing, fresh XML):** `StartTierFallbackTest` 15/15: `aNullAnswerFallsBackToLinear`, `anUnknownIdFallsBackToLinear`, `aMidLadderZeroCallIdFallsBackToLinear`, `theZeroCallHeadIdFallsBackToLinear`, `aPickerThatThrowsFallsBackToLinearAndLeaksNothing`, `aHungPickerUnderTheDefaultPolicyTimesOutToLinear`, `aLeakedInnerTimeoutFallsBackToLinear`, `aForeignCancellationWhileTheCallerIsActiveFallsBackToLinear`; the cancellation falsifiers `aCallerCancellingMidPickGetsTheCancellationAndATraceWithThePick`, `theCommandDeadlineMidPickIsATimeoutWithThePickOnTheTrace`, `theCommandDeadlineWinsWhenItIsEarlierThanThePickerTimeout`, `thePickerTimeoutWinsWhenItIsEarlierThanTheCommandDeadline`, `aPickerWithinItsTimeoutIsUsed`. Plus `StartTierPickerTest.aNullPickIsRecordedAsAFallbackSelection`, `aFallbackAfterAPickDeliversTheCodeThenTheSelection`, `anUnmappedPickerIsLoudButNeverFailsTheCommand` (an unmapped picker is loud, never a failed command), and `StartTierRedactionTest.aThrowingPickerLeaksNeitherItsMessageNorTheContext` 2/2.
- **Evidence:** `core/build/test-results/test/TEST-...{StartTierFallbackTest,StartTierPickerTest,StartTierRedactionTest}.xml`; `StartTierPicking.kt`; `core/src/main/kotlin/.../internal/guarded` (rethrows `CancellationException`).

### 4. SC4: when policy leaves no eligible LLM tier (for example offline-only), the picker is never called and no router model call is made; the walk records `router_fallback` and proceeds as Linear
result: passed
- **Rung:** 1 (pipeline tests; falsified by an arrange that would call the picker if the guard were missing)
- **Target:** headless JVM harness
- **Expected:** `PolicyPreCheck` has already filtered `ladder.tiers` by the one shared `tierPermitted` rule, so `rest` holds only permitted tiers; with no model tier left (offline-only with no ready on-device tier, empty `allowedProviders`, a `maxTier` at the head) the picker and the `FakeAiProvider` both see zero calls, the trace codes are the policy skips then `ROUTER_FALLBACK`, there is no selection record, and the outcome is `Unhandled(cappedByPolicy = true)`. A cloud picker is also not called offline even when a ready on-device tier exists.
- **Arranged (seeded):** a `NoMatch` head plus cloud tiers under `TierPolicy { offlineOnly = true }`, an empty allowed set, and a `maxTier` cap; a ready on-device capability for the mixed case; an empty-script `ScriptedPicker` with a call counter and a counting provider.
- **Did (drove):** forced re-run; read `PolicyPreCheck.kt` diff (`permits` became the shared `tierPermitted`, applied to tiers and to the picker's declared capabilities) and `StartTierPolicyTest` assertions (lines 40-80).
- **Observed (all passing, fresh XML):** `StartTierPolicyTest` 9/9: `offlineOnlyWithNoOnDeviceTierLeavesNoModelTierAndNeverCallsThePicker` (`picker.calls == 0`, `fake.callCount == 0`, codes `[TIER_SKIPPED_POLICY, TIER_SKIPPED_POLICY, ROUTER_FALLBACK]`, `selection == null`, `cappedByPolicy`), `anEmptyAllowedProviderSetLeavesNoModelTierAndNeverCallsThePicker`, `aMaxTierAtTheHeadLeavesNoModelTierAndNeverCallsThePicker`, `anOfflineCommandNeverCallsACloudPickerEvenWithAReadyOnDeviceTier` (offline command still completes on the on-device tier via Linear), `anOnDevicePickerRunsOfflineButTheBindTimeGateStillRefusesACloudModel`, `aPickerWhoseProvidersThePolicyExcludesIsNotCalledAndTheFirstModelTierRuns`, `aWholeLadderRefusalIsAPlainFailureWithNoRouterFallback` (control: a refused ladder is a plain failure, not a router fallback), `aHeadThatHandlesOrFailsEndsTheWalkWithNoRouterFallbackAndNoSelection` (control: no router_fallback noise when the head ends the command), `aCustomPickerIsStillCalledOnceWhenExactlyOneModelTierIsEligible`. Engine router: `RouterSelectorTest.theRouterUnderOfflineOnlyNeverCallsAndTheCommandIsCapped`, `noTokensLeftBeforeThePickMeansNoCallAndAFallback`, `exactlyOneModelTierMeansNoCallNoFallbackAndNoSelection`.
- **Evidence:** `core/build/test-results/test/TEST-...{StartTierPolicyTest,RouterSelectorTest}.xml`; `PolicyPreCheck.kt`, `StartTierPicking.kt` (`if (llm.isEmpty() || !tierPermitted(...)) { recordCode(ROUTER_FALLBACK); return 0 }`).

### 5. SC5: `TierSelector.Router(...)` is built on the same seam and is off by default (an app that does not opt in walks exactly as v1.0 Linear); when on, telemetry reports the tiers it saved versus a Linear walk
result: passed
- **Rung:** 1 (pipeline tests, byte-pinned request goldens, and a characterization suite written before any `TierWalk` edit)
- **Target:** headless JVM harness
- **Expected:** with no selector the Router is never constructed or called and the walk is v1.0's Linear (and `Fixed` is unchanged); the Router makes exactly one forced `pick_start_tier` call with reasoning OFF through the same `PickingSpec` seam; a garbled answer is a fallback not a failure; `trace.selection.tiersBypassed` equals the number of eligible model tiers the pick skipped versus Linear and is also in the `StartTierSelected` event; no transcript, context or description leaks.
- **Arranged (seeded):** head + three model tiers, a `FakeAiProvider` scripted to answer `pick_start_tier` (valid and garbled), a `MappedSelection` for the router id, per-tier execution counters.
- **Did (drove):** forced re-run; read `RouterSelectorTest.theRouterPicksTheStartTierWithOneForcedCall` assertions (lines 33-68) and listed `RouterRequestTest` / `TierWalkLinearCharacterizationTest` names.
- **Observed (all passing, fresh XML):** `RouterSelectorTest` 7/7: one call (`fake.callCount == 1`), `tools == [pick_start_tier]`, `ToolChoice.Required("pick_start_tier")`, `ReasoningMode.OFF`, the router model resolved through selection; `selection.picked == plan`, `eligible == [single, plan, agentic]`, `tiersBypassed == 1`, executions `[0, 1, 0]` (single bypassed, agentic never ran); `aPipelineWithNoSelectorNeverCallsTheRouter` (default off); `aGarbledAnswerIsAFallbackNotAFailure`; `anUnmappedRouterIdIsLoudAndTheWalkIsLinear`. `RouterRequestTest` 7/7 pins the schema byte for byte, the instruction/description text, the one-line cleaning of tier descriptions, the user-message layout, the `maxTokensPerTurn` limit from policy, and `everyGarbledAnswerYieldsNull`. `TierWalkLinearCharacterizationTest` 10/10 (the v1.0.1 Linear/Fixed walk pinned whole). `pickingTheFirstLlmTierBypassesNothing` (`tiersBypassed == 0`, the saved count is 0 when it equals Linear). `StartTierRedactionTest.aRouterRunLeaksNoTranscriptContextOrDescription`. Frozen surface: `git diff 9c88961 HEAD -- core/api.txt` empty and Metalava compat green.
- **Evidence:** `core/build/test-results/test/TEST-...{RouterSelectorTest,RouterRequestTest,TierWalkLinearCharacterizationTest,StartTierRedactionTest}.xml`; `core/src/main/kotlin/.../pipeline/RouterPicker.kt`, `TierSelector.kt`; `.../telemetry/StartTierSelection.kt`.

## Summary

total: 5
passed: 5
partial: 0
failed: 0
infra: 0

## Notes / anomalies (for the Gate-2 reviewer)

- **No Gate-2 obligation registered.** All five Phase 16 criteria are verifiable headlessly and were verified headlessly; no physical or device step is deferred, so no fragment was written under `.planning/uat-pending/` and `HUMAN-UAT-PENDING.md` was not touched (same handling as `15-SELF-UAT.md`). The one item that is genuinely not proven by this phase is the live router prompt wording (OI-6, MEDIUM confidence): the router's request bytes are pinned, but whether a real cheap model picks the right tier from the descriptions is the Phase 19 Gate-1 router leg on the TESTER, which needs a relayed orchestrator GO with a request/USD ceiling. This is a Phase 19 Gate-1 scope item, not a deferred Gate-2 human step for Phase 16. If the orchestrator wants a ledger row anyway, say so and a distinct-slug fragment can be added.
- **Advisory carried from `16-VERIFICATION.md` (re-read, not a FAIL).** (a) The WR-01 hardening in `SelectionBook.take` (a picker turn recorded after the pick closed is dropped from `trace.selection.turns` but still counted in `trace.usage`) has no test; code reading found it sound; a cheap JVM test in `StartTierPickerTest` is recommended and non-blocking. (b) `router_fallback` is also recorded when no picker was called (no model tier left, policy-forbidden picker). That is what SC4 specifies, so a dashboard counting it as the picker failure rate over-counts; documented in API.md.
- **Plan-time draft left as is.** `16-VALIDATION.md` still shows `status: draft`, `nyquist_compliant: false` (finalizer-owned); this gate does not edit it. All its Wave-0 test files exist and pass.
- **Matrix legs not re-run.** `:providers:test*` (OkHttp 4.12.0 / 5.2.1 / 5.5.0) and the keystore Metalava task were not re-run: Phase 16 touched no provider or keystore source or `api.txt` (empty diff against the phase base), and Phase 15's forced-fresh matrix result at the same provider sources stands. I flag this as the one scope narrowing in this run.
- **Memory:** swap stayed full throughout; no earlyoom kill and no retry were needed. Pre-existing modified/untracked files (graphs, `.gsd/`, `state.json`, milestone docs, `milestone.lock`, stage-done markers) were left alone.
- No FAIL, no INFRA, no source code changed by this run.

## Findings routed to gap-closure (if any)

None.

## Verdict

All 5 criteria PASS, re-observed on the JVM harness this run from a forced-fresh, uncached run on HEAD `2a537db` (core 1091 tests, 0 failures, 0 errors, 0 skipped; `:core:detekt`, `:core:metalavaCheckCompatibility`, docs-coverage and hygiene green). Gate-1 complete. No Gate-2 item is deferred for this phase, so no uat-pending fragment was registered; the live router check is owned by the Phase 19 Gate-1 router leg.
