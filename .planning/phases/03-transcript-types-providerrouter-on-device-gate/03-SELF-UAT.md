---
status: complete
result: all_pass
gate: 1
phase: 03-transcript-types-providerrouter-on-device-gate
source: [03-ROADMAP success criteria 1-5]
device: none (pure :core JVM library phase; target surface is the JVM test/fixture harness, no adb, no device, no emulator)
apk: n/a (artifact: core/build/libs/core.jar md5 aebc2ac0f8a1593cd116c5e5e66c9715 @ d0970a5)
run: 2026-10-01T05:10:00Z
---

<!--
Gate-1 self-UAT for a headless pure-JVM library phase. Driver: no project AGENT-*-TESTING.md exists and the phase has
no UI; the target is the Gradle JVM test harness over scripted fake providers/strategies (no LLM, no network). Same
handling as the Phase 1 and Phase 2 precedents (01-SELF-UAT.md, 02-SELF-UAT.md): no device playbook applies.
`phase.has-uat-criteria 3` reports no user-visible UI signal (5 criteria listed); the orchestrator directed the
headless Phase 2 handling, so all 5 ROADMAP criteria were driven on the JVM harness.
-->

# Self-UAT Log: Phase 3 (Transcript Types, ProviderRouter & On-Device Gate), one log for the phase

**Target:** headless (repo CLI, JVM test harness over scripted fake providers, `NoNetworkGuard`). No adb, no device, no emulator.
**Build identity:** HEAD `d0970a59eaa7d8bddb851e6dc8472695c8615857` (post-review-fix tree; `03-REVIEW-FIX.md` status resolved); `git status --short core config scripts` empty (no uncommitted change to library, config or scripts). `core.jar` md5 `aebc2ac0f8a1593cd116c5e5e66c9715`.
**Pre-flight:** JDK 17, Gradle wrapper 9.4.1. Stale results removed first (`rm -rf core/build/test-results/test`), then `./gradlew :core:cleanTest :core:test --rerun-tasks --console=plain` (foreground) -> `BUILD SUCCESSFUL in 11s`, exit 0, `7 actionable tasks: 7 executed`. Every count below was produced by this run, not by a cache or by the pre-fix `evidence/` files.
**Unit suite:** JUnit XML in `core/build/test-results/test/` parsed with a script: 46 classes, **422 tests, 0 failures, 0 errors, 0 skipped**.
**Wider gates:** `./gradlew check :core:jar` -> `BUILD SUCCESSFUL`, 137 actionable tasks (detekt, explicit-API, bytecode, module-graph, DI, OkHttp-floor verifiers green); `scripts/verify-repo-hygiene.sh` -> `HYGIENE OK`; `scripts/review-api-surface.sh` -> `API SURFACE OK sealed=AssistantPart,CommandOutcome,GateDecision,Message,RunTermination,StrategyOutcome,ToolStep classes=161` (seven sealed types, up from five at Phase 2, as the plan 03-01 allow-list widening says).
**Coverage/Nyquist:** `03-VALIDATION.md` and `03-VERIFICATION.md` (status passed, 5/5) exist; treated as claims. Coverage re-derived below by reading test names and the load-bearing assertions against the ROADMAP text, not against the SUMMARYs.
**Seed/fixture integrity:** fixtures are in-tree test fixtures (`FakeAiProvider`, `ScriptedStrategy`, `ScriptedSelectionSource`, `ScriptedCredentialSource`, `FakeClock`, `RecordingEventListener`, `NoNetworkGuard`); no external state, nothing to restore.
**Prior verdict audited:** `03-VERIFICATION.md` and `evidence/phase-gate.txt` / `evidence/api-surface-review.txt` (all predate the review-fix commits `0c19c44`, `fe4cff3`). Not relied on; every claim re-observed this run on the post-fix tree. No contradiction found.

## Criteria

### 1. SC1: neutral transcript types in :core (messages, tool calls, tool results, system, usage, stop reason, cache directive, verbatim NativeReplay); single-shot and multi-turn tool conversations expressible; :core has no HTTP dependency
result: passed
- **Rung:** 1/3 (unit tests plus a Gradle dependency-resolution check and source greps; no visual claim exists)
- **Target:** headless JVM harness
- **Expected:** `ModelRequest`/`ModelResponse`/`Message`/`AssistantPart`/`ToolResult`/`StopReason`/`CacheDirective`/`NativeReplay` exist and can express (a) one single-shot call and (b) a multi-turn assistant-tool_use / tool_result conversation; the verbatim native turn rides on assistant messages and is returned only to the exact provider and model; `:core` runtime classpath carries no HTTP library.
- **Arranged (seeded):** hand-built requests and responses in each test (no provider, no network).
- **Did (drove):** `:core:test --rerun-tasks`; parsed the XML; read `TranscriptTypesTest.multiTurnToolConversationIsExpressibleAndReadsBack` and `NativeReplayTest` assertions; `./gradlew :core:dependencies --configuration runtimeClasspath`; grepped `core/src/main` for HTTP/Android imports.
- **Observed (all 0 failures/skips):** `TranscriptTypesTest` 12/12 including `singleShotRequestReadsBackFieldByField`, `singleShotResponseReadsBackFieldByField`, `multiTurnToolConversationIsExpressibleAndReadsBack`, `stopReasonIsAnOpenSnakeCaseVocabulary`, `toStringNeverPrintsContent`, `listsAreCopiedOnConstruction`, `requestValidationRejectsBadShapes`. The multi-turn test builds a 4-message conversation (`UserMessage`, `AssistantMessage` with text plus two `ToolCall` parts and a `NativeReplay`, `ToolResultsMessage` with one ok and one `isError` result, final assistant turn) with two tools, `ToolChoice.Auto`, and `CacheDirective(staticPrefix, conversationTail)`, then asserts message order by identity, call ids, content and error flags, so a type that could not carry any of those would fail to compile or assert. `NativeReplayTest` 5/5: `exactProviderAndModelStampReturnsTheSameRawInstance` (assertSame), `anythingElseReturnsNullSoTheMapperRebuildsFromNeutralParts` (other provider, other model, unstamped all null), `rawIsStoredByReferenceAndPartsStayTextAndToolCallOnly`, `printingNeverRevealsTheRawTurn`, `blankModelIsRejected`. Usage is exercised through `ModelResponse` read-back and `CacheNotEngagedTest`. `ApiShapeTest` 10/10 (sealed set, no data classes, growth rule). **No HTTP dependency:** `:core:dependencies --configuration runtimeClasspath` resolves only `kotlinx-coroutines-core 1.11.0`, `kotlinx-serialization-json 1.11.0` and `kotlin-stdlib 2.3.20` (plus `org.jetbrains:annotations`); no okhttp, okio, ktor, retrofit. `grep -rnE "okhttp|java\.net|HttpURLConnection|android\.|retrofit|ktor" core/src/main` -> no hits. `check` module-graph verifier green.
- **Evidence:** `core/build/test-results/test/TEST-io.github.ygaray.voiceactionengine.core.{TranscriptTypesTest,NativeReplayTest,ApiShapeTest}.xml`; `core/src/main/kotlin/.../transcript/`; dependency-tree output above.

### 2. SC2: selection seam asked once per command; provider switch applies to the next command, not mid-command; missing key -> NotConfigured after zero provider calls; one provider's key never reaches another
result: passed
- **Rung:** 1
- **Target:** headless JVM harness over `FakeAiProvider`
- **Expected:** `ProviderSelectionSource` asked once per command (per tier, lazily, then frozen); mid-command change leaves the running command on its first choice and the next command uses the new one; no key -> `FailureReason.NotConfigured(provider)` with zero provider calls; a credential for provider X is only ever sent to provider X.
- **Arranged (seeded):** two echo `FakeAiProvider`s (ANTHROPIC `model-a`, OPENAI `model-z`), an `AtomicReference`-backed selection lambda, scripted credential sources including a hostile source that hands OpenAI's key when Anthropic's is asked.
- **Did (drove):** `:core:test --rerun-tasks`; read the load-bearing assertions.
- **Observed:** `ProviderRouterTest` 18/18. `aSelectionThatChangesMidCommandDoesNotMoveTheCommandButTheNextCommandUsesIt`: command one makes a model call, flips the selection to OPENAI/`model-z`, makes two more calls; asserts `asked == 1`, all three Anthropic calls on `model-a` and OpenAI fake `callCount == 0`; command two then asserts `asked == 2`, the OpenAI fake sees exactly `["model-z"]` and Anthropic stays at 3 calls. `twoCommandsRunningAtOnceOnOnePipelineResolveIndependently` and `threeTurnsInOneTierAskTheSelectionOnceAndAllGoToTheSameModel` cover concurrency and the freeze. `aMissingKeyEndsNotConfiguredForThatProviderWithNoCall`: asserts `FailureReason.NotConfigured(ANTHROPIC)`, trace code `credential_missing`, `fake.callCount == 0`. `noProviderSelectionEndsNotConfiguredWithNoCall` covers a missing selection. `ModelRouterTest` 30/30 adds `noCredentialSourceAndAMissingKeyBothRefuseNotConfigured`, `anUnreadableKeyAndAThrowingCredentialSourceAreDistinctFromMissing`, `aCredentialStampedForAnotherProviderIsRefusedAndNeverSent`, `aSelectedProviderWithNoRegisteredImplementationIsNotConfigured`, `aProviderThePolicyDoesNotAllowIsRefusedAsForbidden`, `aToolRequestOnAModelWithoutToolsIsRefusedBeforeAnyCallAndTheHandleStaysUsable`, `manyCompletesFromRealThreadsLoseNoTurn`. `KeyIsolationTest` 5/5: `aHostileSourceHandingAnotherProvidersKeyIsRefusedAndTheKeyReachesNoCall` (reason `NotConfigured(ANTHROPIC)`, code `credential_mismatch`, both fakes at 0 calls, `assertNoKeyCrossedProviders`), `twoCommandsWhoseSelectionSwitchesProvidersCarryOnlyTheirOwnProvidersKey`, `aTwoTierLadderAsksEachKeyOnceForItsOwnProviderAndNeverCrossesThem`, `aSourceThatFailsLoudlyForAnyOtherProviderIsNeverTrippedByARunThatSelectsOnlyOneProvider`. Interpretation note: the ROADMAP's "once per command" is implemented as "once per command per tier, then frozen" (documented decision D-03 in `03-CONTEXT.md`, so a cheap tier and a strong tier can pick different models); for the single-tier cases asserted here that is exactly once, and a mid-command change never moves the running command. Not a defect.
- **Evidence:** `TEST-...{ProviderRouterTest,ModelRouterTest,KeyIsolationTest,SeamTypesTest}.xml`; `core/src/main/kotlin/.../provider/ModelRouter.kt`.

### 3. SC3: ON_DEVICE unavailable -> only the app's declared fallback (no key/provider substitution, no Nano/AICore code); loud typed failure when no fallback declared
result: passed
- **Rung:** 1/3 (unit tests plus a source scan)
- **Target:** headless JVM harness
- **Expected:** an unavailable on-device selection is served only by the fallback the app declared on that selection, using that fallback provider's own key, and the policy still applies to it; with no declared fallback the run fails loudly with a typed reason, zero calls and zero key lookups; no on-device implementation code ships.
- **Arranged (seeded):** an `OnDeviceCapability` gate (default, counting, flipping, throwing, Available, Downloadable, Downloading), `FakeAiProvider`s for ON_DEVICE and ANTHROPIC, selections with and without a nested fallback, `TierPolicy { allowedProviders / offlineOnly }`.
- **Did (drove):** `:core:test --rerun-tasks`; read assertions; independent greps over `core/src/main`.
- **Observed:** `OnDeviceGateTest` 13/13. `anUnavailableOnDeviceSelectionIsServedByTheDeclaredFallbackWithItsOwnKey`: the cloud fake gets one call on `model-a` with `credential.provider == ANTHROPIC` and the exact key, `credentials.requested == [ANTHROPIC]` (no other key ever asked), `fallbackFrom == ON_DEVICE` on the attempt, code `provider_fallback`. `noFallbackAndTheDefaultGateFailsLoudlyWithZeroCallsAndNoKeyAsked`: failure `ProviderUnavailable(ON_DEVICE, "on_device_unavailable")`, local and cloud fakes at 0 calls, `credentials.requested.isEmpty()`; here the app also registered a cloud provider and holds a cloud key, so this proves no silent substitution. Also `aFallbackThePolicyForbidsIsRefusedWithZeroCallsAndNoKeyAsked`, `offlineOnlyWithAFlippingGateRefusesTheCloudFallbackAndAsksNoCloudKey`, `aPermittedFallbackWhoseProviderHasNoKeyFailsNotConfiguredForThatProviderWithZeroCalls`, `downloadableAndDownloadingEachCountAsUnavailableSoTheFallbackIsUsed`, `aGateWhoseSecondReadThrowsRecordsTheProbeErrorAndUsesTheDeclaredFallback`, `theDefaultGateReportsNotImplementedBecauseVersionOneShipsNoOnDeviceCode`. `ModelRouterTest.anOnDeviceSelectionThatIsNotReadyFailsLoudlyWithoutTouchingAKey`; `ProviderRouterTest.anOnDeviceSelectionWithoutAFallbackEndsLoudlyAndAsksForNoKey`; `SeamTypesTest.theFallbackCannotItselfBeOnDeviceOrChain` and `aFallbackHangsOffAnOnDeviceSelectionOnly` (one-level fallback only). `KeyIsolationTest.theFallbackPathAsksForTheFallbackProvidersKeyAloneAndSendsOnlyThatKey`. **No Nano/AICore code:** `NoHardCodedConstantsTest.noOnDeviceImplementationCode` green and independently confirmed: `grep -rniE "aicore|nano|mlkit|genai|com\.google\.mlkit" core/src/main` -> only `NANOS_PER_MILLI` / `System.nanoTime()` in `PipelineBuilder.kt` (the monotonic clock, which the scan's matcher test `onDeviceMatcherFlagsImplementationCodeAndPassesTheMonotonicClock` deliberately allows).
- **Evidence:** `TEST-...{OnDeviceGateTest,KeyIsolationTest,ModelRouterTest,ProviderRouterTest,SeamTypesTest,NoHardCodedConstantsTest}.xml`; `core/src/main/kotlin/.../provider/OnDeviceCapability.kt`.

### 4. SC4: a caching-declaring fake returning zero cache read/write on a prefix above the model's minimum raises CacheNotEngaged; the same result below the minimum stays silent
result: passed
- **Rung:** 1
- **Target:** headless JVM harness
- **Expected:** `PipelineEvent.CacheNotEngaged` raised for (prefix above minimum, caching declared, zero read, zero write); silent below the minimum, and for non-caching or unknown-minimum models.
- **Arranged (seeded):** `FakeAiProvider` declaring `EXPLICIT_BREAKPOINTS` with `minCacheablePrefixTokens = 1024`, `charsPerToken = 4.0`; `Usage(2100, 0, 0, 10)` returned; a system prompt of 8,000 characters (about 2,000 tokens, above the minimum) versus 2,000 characters (about 500 tokens, below it); `RecordingEventListener`.
- **Did (drove):** `:core:test --rerun-tasks`; read both end-to-end tests and the boundary tests, and checked the fixture constants so the below-minimum case is genuinely discriminating (same billed usage 2,100 in both runs, so only the prefix size differs).
- **Observed:** `CacheNotEngagedTest` 32/32. `aLargeStaticPrefixThatTheCacheIgnoredRaisesExactlyOneEventAndNothingElse`: exactly one `CacheNotEngaged` event naming strategy `tierId`, `ANTHROPIC`, `model-a`; no `EngineCode` event; trace codes empty (the event is a typed listener event, not a trace code); outcome still `Completed`. `aPrefixBelowTheMinimumStaysSilent`: same fake and same usage, 2,000-char prefix, zero `CacheNotEngaged` events. Boundary tests would fail under off-by-one: `explicitFiresAtTheMinimumAndNotOneTokenBelow`, `thePromptTotalCapFiresAtTheMinimumAndNotOneTokenBelow`, `theCharacterPathFiresAtFourTimesTheMinimumAndNotOneCharacterBelow`. Silent cases: `explicitIsSilentWhenTheCacheWasReadOrWritten`, `anUnknownMinimumIsSilent`, `aModelThatDoesNotCacheIsSilent`, `anUnknownCachingModeIsSilent`, `zeroUsageIsSilent`, `aRequestThatDidNotAskForTheStaticPrefixToBeCachedIsSilent`, `aProviderFailureRaisesNothingAndDoesNotCountAsATurn`, `aRefusedBindingRaisesNothingAndCallsNoProvider`, `aCapabilityRefusedRequestRaisesNothingAndCallsNoProvider`. Automatic mode: `automaticNeverFiresOnTheFirstTurn`, `automaticFiresOnTheSecondTurnWithNoCacheRead`, `automaticIsSilentOnTheSecondTurnWhenTheCacheWasRead`, `automaticCountsTurnsPerHandleAndANewCommandStartsAgainAtOne`. Documented known-silent case: `explicitPrefixDriftWithAWriteIsAKnownSilentCase` (a rewrite every turn is not flagged; the Phase 10 Gate-1 run owns `cache_read > 0`), consistent with the ROADMAP text, which only requires zero read AND zero write.
- **Evidence:** `TEST-...CacheNotEngagedTest.xml`; `core/src/main/kotlin/.../provider/CacheDetector.kt`.

### 5. SC5: limits and model ids reach the engine only through TierPolicy defaults, the selection seam and the app-overridable capability table; no hard-coded model-id or limit constant; no library code reads app settings storage
result: passed
- **Rung:** 1/3 (unit tests plus independent source greps)
- **Target:** headless JVM harness
- **Expected:** model ids and the three TierPolicy limits appear only where the contract allows; capability data comes from `ModelCapabilityTable` (override > provider default > unknown); no file, env, system-property or preferences access in `core/src/main`.
- **Arranged (seeded):** none for the scans (they read the real `core/src/main` tree, with a synthetic dirty/clean pair proving the matcher bites); capability-table tests build their own override blocks.
- **Did (drove):** `:core:test --rerun-tasks`; read `NoHardCodedConstantsTest`; ran my own greps over `core/src/main`.
- **Observed:** `NoHardCodedConstantsTest` 12/12 including `noModelIdAppearsInCodeKdocOrComments`, `limitConstantsAreDeclaredOnlyByTheirOwners`, `tierPolicyDefaultValuesAppearOnlyInTierPolicy`, `noFileEnvironmentPropertyOrPreferenceAccess`, `noOnDeviceImplementationCode`, plus the self-checks `scanFlagsAViolatingSyntheticFileAndPassesACleanOne`, `tierPolicyLiteralMatcherFlagsTheDefaultsInEitherSpelling`, `settingsMatcherFlagsAccessAndPassesPlainCode`, `limitConstantMatcherFlagsLimitNamesAndPassesOthers`, `modelIdMatcherFlagsIdsAndPassesProviderAndCompanyNames` and `scanIsNotVacuous` (asserts a minimum file count and that `TierPolicy.kt` was scanned), so the scan can neither pass by scanning nothing nor by a dead matcher. Independent greps on `core/src/main`: model ids (`claude-|gpt-N|gemini|haiku|sonnet|opus|llama`) -> no hits; `SharedPreferences|DataStore|System.getenv|System.getProperty|java.io.File|Settings.` -> no hits; the literals `6`, `60_000`, `4_096` appear only in `pipeline/TierPolicy.kt:6-8` (private `DEFAULT_*` constants) and its KDoc line 97. The only other default, `DEFAULT_CHARS_PER_TOKEN = 4.0` in `ModelCapabilities.kt:5`, is a capability-table default, an allowed channel and overridable per model. `ModelCapabilityTableTest` 10/10: `appOverrideForTheExactPairPatchesTheProviderDefault`, `anOverrideDoesNotReachTheSameIdUnderAnotherProvider`, `overrideKeysAreExactIdsNeverPrefixes`, `anIdWithNoOverrideOrDefaultFallsBackToUnknown`, `unknownAndEmptyBuilderShareTheDocumentedDefaults`, `invalidDivisorsAreRejectedNamingTheField`; `ProviderRouterTest.anOverridePatchesOnlyItsExactPairAndKeepsTheOtherFields`, `withoutOverridesTheTableReportsTheProviderDefaultAndUnknownForAnUnregisteredId`, `buildRejectsADuplicateOverrideForTheSamePair`, `buildRejectsABlankOverrideModel`, `ModelRouterTest.anAppOverrideForTheExactModelAlsoStopsAToolRequestBeforeTheCall`. `TierPolicyTest` 24/24 (defaults 6 / 60,000 / 4,096 re-confirmed from Phase 2). Redaction on the routed path: `RedactionCanaryTest` 3/3 (`noCanaryOrKeyAppearsAnywhereOnTheRoutedPathIncludingFallbackAndRefusals` sweeps fallback, unreadable-key and foreign-key runs plus every built type's `toString()` and asserts a minimum distinct-value count, so it is not vacuous).
- **Evidence:** `TEST-...{NoHardCodedConstantsTest,ModelCapabilityTableTest,TierPolicyTest,RedactionCanaryTest}.xml`; grep output above.

## Summary

total: 5
passed: 5
partial: 0
failed: 0
infra: 0

## Notes / anomalies (for the Gate-2 reviewer)

- Phase 1 carry-over discharged: the Phase 1 Gate-2 item owing a `FakeAiProvider` from Phase 3 is satisfied. `FakeAiProvider` (testFixtures) exists and `FakeAiProviderTest` 11/11 passes (scripted results in order, lambda steps seeing the call, exhaustion raises an `AssertionError`, declared capabilities and credential flag returned, secrets absent from `toString()`); it is the fake used by every criterion above.
- Highest rung used: 1 for SC2 and SC4; 1/3 for SC1, SC3 and SC5 (dependency-resolution and source-grep checks). No visual rung (rung 5) applies: the phase has no UI. No device was touched.
- Honest scope limit: the harness proves the router, seams, transcript types, on-device gate and cache detector against scripted fake providers, which is exactly what the ROADMAP goal states ("All pure JVM, no HTTP"). Real provider transports, the real Anthropic prompt cache, and AICore on a Pixel 10 belong to Phases 4 to 10 and the v1.1 spike, not here.
- Interpretation note on SC2: "once per command" is realized as once per command per tier (decision D-03). A multi-tier ladder therefore asks once per tier. Consistent with the ROADMAP intent (a mid-command change never moves a running tier) and with the cheap-then-strong tier design; flagged only so the Gate-2 reviewer is not surprised.
- SC4 known-silent case: a prefix that drifts so the cache is rewritten every turn (nonzero write, zero read) is not flagged; documented in `CacheNotEngagedTest.explicitPrefixDriftWithAWriteIsAKnownSilentCase` and deferred to the Phase 10 Gate-1 assertion `cache_read > 0`.
- The `evidence/` files in the phase dir predate the review fixes (`0c19c44`, `fe4cff3`); this log supersedes them as the Gate-1 record for the final tree.
- `api.txt` is still not committed; it is created at the v1.0.0 cut in Phase 11.
- Pre-existing dirty files under `.planning/` (graphs, config.json, v1.0-MILESTONE-RUN.md, milestone lock, state.json, stage markers, `.gsd/`, intel) belong to the orchestrator and were not touched. No source edits, no tags, no pushes.

## Findings routed to gap-closure (if any)

None.

## Verdict

All 5 criteria PASS. Gate-1 complete; Gate-2 registered as a ledger-completeness fragment (`.planning/uat-pending/03-transcript-types-providerrouter-on-device-gate.md`). Nothing physical or device-bound is deferred.
