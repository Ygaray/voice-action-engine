---
phase: "3"
slug: transcript-types-providerrouter-on-device-gate
status: secured
threats_open: 0
asvs_level: 1
audited_head: b59b1337b6f97ad72835574b0a409b42f82e5d3b
created: "2026-09-30"
---

# Phase 3 - Security

> Per-phase security contract: threat register, accepted risks, and audit trail.

---

## Trust Boundaries

| Boundary | Description | Data Crossing |
|----------|-------------|---------------|
| strategy / app -> transcript types | user transcript, system prompt and tool results enter the neutral model | transcript text, prompts, tool results |
| provider -> transcript types | model text, tool arguments and raw provider turns (thinking blocks) enter through ModelResponse and NativeReplay | untrusted model output, thinking text, signatures |
| engine -> logs / telemetry / exceptions | any toString, require message, trace, event or failure value may be logged or persisted by the app | must be ids, codes, counts, lengths and tool names only |
| repository -> frozen public API | sealed set, constructor shapes and engine-produced types freeze at the v1.0.0 cut | public signatures (Metalava dump) |
| provider defaults -> engine | per-model capability facts contributed by :providers (Phases 4-5) | ModelCapabilities |
| app overrides -> engine | the app patches capability facts for exact model ids | capability patches |
| library source -> consumers | anything hard-coded in :core ships to every app | model ids, limits, settings access |
| app credential store -> engine | API keys enter through CredentialSource | API key |
| app settings -> engine | provider, model and fallback enter through ProviderSelectionSource | ProviderSelection |
| device runtime -> engine | on-device availability enters through OnDeviceCapability, read by the pre-check and the router | availability status |
| engine -> provider | the key, the transcript and tool results cross into transport code through ProviderRequest | API key, prompt, messages |
| provider -> engine | responses and failures come back as ModelResult | untrusted usage numbers, request ids, error types |
| app seams -> router | selection, credentials, probe and capability overrides are app code that may throw or lie | arbitrary exceptions, foreign credentials |
| strategy -> handle | strategy code must never obtain the key | BoundModel (no credential member) |
| policy -> dynamic selection / fallback | static pre-check decisions must still hold for the provider chosen at run time, including the declared fallback | TierPolicy, allowedProviders, offlineOnly |
| credential source -> fallback provider | a second provider's key is fetched only on the fallback path | API key |
| app DSL -> pipeline | providers, seams and capability overrides declared by the app at build time | PipelineBuilder input |
| concurrent callers -> per-tier handle | several coroutines of one command, or several commands, may resolve at once | frozen BoundModel |
| engine -> listener | the cache diagnostic event is delivered to app code | ids only |
| gate scripts -> real tree | isolated-copy dumps must never land in module directories | api.txt |

---

## Threat Register

Built from the ten PLAN threat models (register authored at plan time, 46 threats: 14 high, 17 medium, 15 low; 45 mitigate, 1 transfer). Verified at ASVS L1 (mitigation present at the cited location, plus the named test traced) against the post-code-review-fix tree at HEAD b59b133. Beyond reading the code and tests, the auditor re-ran the gates on that tree (see Audit Trail) because the plan-time gate evidence under evidence/ predates the ten review-fix commits.

| Threat ID | Category | Component | Severity | Disposition | Mitigation (evidence) | Status |
|-----------|----------|-----------|----------|-------------|-----------------------|--------|
| T-03-01 | Information disclosure | transcript type toString / require messages | high | mitigate | Hand-written toString prints length/ids/names/counts only (Message.kt:24,57,80,105; AssistantPart.kt:19,41; ModelRequest.kt:54; ModelResponse.kt:33); require messages name rules, not values. TranscriptTypesTest.toStringNeverPrintsContent sweeps 9 types with a canary; assertRejects checks messages omit it | closed |
| T-03-02 | Information disclosure | NativeReplay.raw (thinking text) | high | mitigate | NativeReplay.toString (NativeReplay.kt:28) and AssistantMessage.toString print provider/model stamp only. NativeReplayTest.printingNeverRevealsTheRawTurn plants a canary inside raw | closed |
| T-03-03 | Tampering | replayed assistant turn rebuilt or mutated | medium | mitigate | raw held by reference; nativeFor returns it only on exact provider+model equality (Message.kt:54); AssistantPart is sealed to Text and ToolCall. NativeReplayTest: exactProviderAndModelStampReturnsTheSameRawInstance (assertSame), anythingElseReturnsNull..., rawIsStoredByReference... | closed |
| T-03-04 | Tampering | sealed set grown accidentally and frozen at the tag | medium | mitigate | scripts/review-api-surface.sh pins an allow-list of exactly seven sealed types by fully qualified name, --expect-sealed-complete, isolated-copy dump, real-tree status guard. Re-run at HEAD: API SURFACE OK sealed=AssistantPart,CommandOutcome,GateDecision,Message,RunTermination,StrategyOutcome,ToolStep. Residual: see AR-1 | closed |
| T-03-05 | Tampering | defaulted ctor params freezing a shape | medium | mitigate | Transcript constructors use explicit secondary constructors, no default arguments (ModelRequest.kt, Message.kt, ModelResponse.kt, CacheDirective). ApiShapeTest.noClassOutsideTheDocumentedExceptionsDeclaresADefaultArgumentConstructorStub sweeps every main class with a documented, staleness-checked exception list | closed |
| T-03-06 | Tampering | hard-coded model ids or limits in library code | medium | mitigate | NoHardCodedConstantsTest: noModelIdAppearsInCodeKdocOrComments, limitConstantsAreDeclaredOnlyByTheirOwners, tierPolicyDefaultValuesAppearOnlyInTierPolicy, each with positive controls (modelIdMatcherFlags..., limitConstantMatcherFlags..., scanFlagsAViolatingSyntheticFile...) and a non-vacuity check | closed |
| T-03-07 | Information disclosure | library reading settings/files/env for keys or models | high | mitigate | NoHardCodedConstantsTest.noFileEnvironmentPropertyOrPreferenceAccess scans core/src/main; settingsMatcherFlagsAccessAndPassesPlainCode is the positive control (File, getenv, getProperty, prefs, SharedPreferences, DataStore, android.*) | closed |
| T-03-08 | Denial of service | wrong capability (prefix match, replacement) | medium | mitigate | ModelCapabilityTable keys on exact (provider, model) and patches the provider default via toBuilder (ModelCapabilityTable.kt). ModelCapabilityTableTest: overrideKeysAreExactIdsNeverPrefixes, appOverrideForTheExactPairPatchesTheProviderDefault, anOverrideDoesNotReachTheSameIdUnderAnotherProvider | closed |
| T-03-09 | Tampering | invalid divisor/minimum making the diagnostic fire everywhere | low | mitigate | ModelCapabilities init requires a finite positive charsPerToken and a null-or-positive minimum (ModelCapabilities.kt). ModelCapabilityTableTest.invalidDivisorsAreRejectedNamingTheField and aNonPositiveMinimumPrefixIsRejectedNamingTheField | closed |
| T-03-10 | Information disclosure | CredentialLookup.Present toString | high | mitigate | Present.toString prints the provider only (CredentialSource.kt:29); Credential.toString prints the provider only. SeamTypesTest.presentPrintsTheProviderOnlyAndNeverTheKey | closed |
| T-03-11 | Information disclosure | free-text causes in Unreadable / CredentialUnreadable / Unavailable | medium | mitigate | isStableCode requires lower snake case at construction of CredentialLookup.Unreadable, FailureReason.CredentialUnreadable, FailureReason.ProviderUnavailable and OnDeviceAvailability.Unavailable. SeamTypesTest.anUnreadableCauseMustBeAStableCodeNotAMessage, FailureTaxonomyTest.aCredentialUnreadableCauseMustBeAStableCodeNotAMessage | closed |
| T-03-12 | Elevation of privilege | fallback used to route around policy | high | mitigate | ProviderSelection init: fallback only when provider is ON_DEVICE, fallback may not be ON_DEVICE, so chains are impossible (ProviderSelection.kt:53-54). Router re-applies providerGate to the fallback (ModelRouter.kt:155). SeamTypesTest.aFallbackHangsOffAnOnDeviceSelectionOnly and theFallbackCannotItselfBeOnDeviceOrChain | closed |
| T-03-13 | Spoofing | lost key reported as Missing | low | mitigate | Distinct Unreadable leaf and CredentialUnreadable failure (ModelRouter.kt lookUp). FailureTaxonomyTest.aUnreadableCredentialIsADistinctFailureFromNotConfigured; ModelRouterTest.anUnreadableKeyAndAThrowingCredentialSourceAreDistinctFromMissing | closed |
| T-03-14 | Denial of service | fake silently answering past its script | low | mitigate | ScriptedSelectionSource throws AssertionError on exhaustion (ScriptedSources.kt); guardedCore catches Exception/LinkageError only, so the Error escapes (Guarded.kt). SeamTypesTest.theScriptedSelectionSourceAnswersInOrderRecordsRequestsAndFailsLoudlyWhenExhausted | closed |
| T-03-15 | Repudiation | fallback or refusal leaving no trace | medium | mitigate | TurnRecord/TierAttempt/CommandTrace carry fallbackFrom; TraceCode defines the thirteen router codes (TraceCode.kt). TraceTest.aFallbackTurnFlowsToTheAttemptTheEventAndTheToString and theRouterCodesHaveTheirSnakeCaseWireValues; every refusal path asserts its code in ModelRouterTest and OnDeviceGateTest | closed |
| T-03-16 | Information disclosure | new trace fields carrying content | low | mitigate | fallbackFrom is a ProviderId (TurnRecord.kt); codes are fixed lower snake case literals; TraceTest.everyTraceCodeIsDistinctAndLowerSnakeCase | closed |
| T-03-17 | Tampering | growing TurnRecord breaking call sites / binary shape | low | mitigate | 6-argument secondary constructor kept, no default values (TurnRecord.kt); TraceTest.aSixArgumentTurnHasNoFallbackAndATierWithNoTurnsHasNone; full :core:test green (422/0 failures) | closed |
| T-03-18 | Information disclosure | ProviderRequest / ModelResult toString | high | mitigate | ProviderRequest.toString prints model, counts and credential provider only (ProviderRequest.kt:28); ModelResult.Success/Failure print response summary / reason+details only. FakeAiProviderTest.providerCallToStringCarriesCountsAndProviderButNoSecret (canary key, system, text), successToStringShowsTheSummaryWithoutTheReplyText | closed |
| T-03-19 | Tampering | ToolSpec growth removing its 5-arg JVM constructor | medium | mitigate | ToolSpec @JvmOverloads constructor (ToolSpec.kt) keeps the 5-argument shape and adds 3/4/6. ApiShapeTest.toolSpecKeepsItsFiveArgumentConstructorAndAddsTheOtherShapes | closed |
| T-03-20 | Tampering | later default-argument ctor in transcript/provider | medium | mitigate | ApiShapeTest growth rule (hasDefaultArgumentStub) over all main classes with positive control theGrowthRulePredicateFlagsADefaultArgumentClass and stale-exception check everyDocumentedStubExceptionStillDeclaresAStub | closed |
| T-03-21 | Denial of service | fake answering past its script | low | mitigate | FakeAiProvider.nextStep throws AssertionError on exhaustion (FakeAiProvider.kt); FakeAiProviderTest.aCallPastTheScriptThrowsAnAssertionErrorNamingExhaustion | closed |
| T-03-22 | Elevation of privilege | provider implementation throwing to crash the pipeline | medium | transfer | Contract documented in AiProvider KDoc (return ModelResult.Failure, never throw, never log); engine guards every call regardless: RoutedModel.complete wraps provider.complete in guarded and returns Failure(fault.toReason()) with PROVIDER_ERROR (BoundModel.kt). ModelRouterTest.aThrowingProviderBecomesATypedFailureAndAZeroUsageTurnNamingTheModel. Transfer recorded as TR-1 | closed |
| T-03-23 | Information disclosure | one provider's key sent to another | high | mitigate | Credential requested for the selected provider id only (ModelRouter.lookUp); present() refuses credential.provider != id (ModelRouter.kt:40) as credential_mismatch. ModelRouterTest.aCredentialStampedForAnotherProviderIsRefusedAndNeverSent; KeyIsolationTest.aHostileSourceHandingAnotherProvidersKeyIsRefusedAndTheKeyReachesNoCall, aTwoTierLadder..., twoCommandsWhoseSelectionSwitchesProviders... | closed |
| T-03-24 | Elevation of privilege | dynamic selection bypassing allowedProviders/offlineOnly/tier declaration | high | mitigate | Single providerGate (ModelRouter.kt:55) applied at bind for every selection and reused for the fallback. ModelRouterTest.aProviderTheTierDoesNotDeclareIsRefusedAsNotDeclared, aProviderThePolicyDoesNotAllowIsRefusedAsForbidden, anOfflineOnlyPolicyRefusesACloudProviderEvenWhenTheTierDeclaresIt | closed |
| T-03-25 | Tampering | mid-command provider/model switch | medium | mitigate | RunSession.model() uses Mutex-guarded lazy slot assigned only after bind returns (RunSession.kt:68). ProviderRouterTest: threeTurnsInOneTierAskTheSelectionOnce..., aSelectionThatChangesMidCommandDoesNotMoveTheCommand..., twoConcurrentModelCallsInOneTierResolveOnce..., aCancelledModelCallFreezesNothing... | closed |
| T-03-26 | Information disclosure | strategy obtaining the Credential through the handle | high | mitigate | BoundModel exposes provider/model/capabilities/fallbackFrom/refusal only, internal constructor, final toString of ids (BoundModel.kt:52); credential lives in the internal Binding held privately by RoutedModel. ModelRouterTest.theHandlePrintsIdsOnlyAndNeverAKeyOrContent; constructor audit shows no public BoundModel constructor | closed |
| T-03-27 | Denial of service | throwing selection/credential/probe/capability/provider code | medium | mitigate | Each app call goes through guarded in ModelRouter (select, onDeviceProbe, lookUp, capabilities) and RoutedModel.complete; real cancellation still propagates. ModelRouterTest: aThrowingSelectionSource..., aThrowingOnDeviceProbe..., aProviderWhoseCapabilityAnswerThrows..., cancellingTheCallerPropagatesItsOwnCancellation..., GuardedTest | closed |
| T-03-28 | Repudiation | refusal with no trace / failed call not attributable | low | mitigate | One terminal trace code per refusal (ModelRouter.bind records step.code); failed provider call records a zero-usage TurnRecord naming provider and model (BoundModel.kt turnOf). ModelRouterTest.aProviderFailureIsPassedThroughUnchangedWithAZeroUsageTurnAndNoErrorCode and the assertRefused code-list assertions | closed |
| T-03-29 | Information disclosure | offlineOnly transcript sent to a cloud fallback | high | mitigate | viaFallback runs providerGate on the fallback (offlineOnly admits ON_DEVICE only) and PolicyPreCheck.permits skips cloud-declaring tiers offline. OnDeviceGateTest.offlineOnlyWithAFlippingGateRefusesTheCloudFallbackAndAsksNoCloudKey, offlineOnlyWithATierThatAlsoDeclaresTheCloudIsSkippedByThePreCheck (under NoNetworkGuard) | closed |
| T-03-30 | Information disclosure | fallback path fetching/sending the wrong provider's key | high | mitigate | bound(fallback, ON_DEVICE) resolves the registered fallback provider and asks the credential source for that provider id only. KeyIsolationTest.theFallbackPathAsksForTheFallbackProvidersKeyAloneAndSendsOnlyThatKey | closed |
| T-03-31 | Elevation of privilege | silent climb to cloud when on-device is unavailable | high | mitigate | viaFallback: fallback == null returns onDeviceStop(ON_DEVICE_UNAVAILABLE) before any credential lookup; forbidden fallback returns fallback_refused. OnDeviceGateTest.noFallbackAndTheDefaultGateFailsLoudlyWithZeroCallsAndNoKeyAsked, aFallbackThePolicyForbidsIsRefusedWithZeroCallsAndNoKeyAsked; ProviderRouterTest.anOnDeviceSelectionWithoutAFallbackEndsLoudlyAndAsksForNoKey | closed |
| T-03-32 | Tampering | second on-device probe diverging from the pre-check's | medium | mitigate | One public hook (PipelineBuilder.onDevice) feeds one internal onDeviceAvailability lambda captured once in wiring() and handed to PolicyPreCheck and ModelRouter (PipelineBuilder.kt:85,157). OnDeviceGateTest.aCountingGateIsReadOncePerCheckByThePreCheckAndTheRouter | closed |
| T-03-33 | Denial of service | throwing or flapping gate crashing the run | low | mitigate | usable() and PolicyPreCheck.check wrap the probe in guarded, record on_device_probe_error and treat it as unavailable. ModelRouterTest.aThrowingOnDeviceProbeIsRecordedAndCountsAsUnavailable; OnDeviceGateTest.aGateWhoseSecondReadThrowsRecordsTheProbeErrorAndUsesTheDeclaredFallback, aGateThatFlipsFromAvailableToUnavailable... | closed |
| T-03-34 | Repudiation | false alarms from the cache diagnostic | low | mitigate | shouldFlagCacheMiss is silent on any unknown (minimum null, mode unknown, directive off); default divisor 4.0; estimate capped by billed prompt total (CacheDetector.kt). CacheNotEngagedTest boundary tests (explicitFiresAtTheMinimumAndNotOneTokenBelow, thePromptTotalCapFiresAtTheMinimumAndNotOneTokenBelow, anUnknownMinimumIsSilent, ...) | closed |
| T-03-35 | Denial of service | absurd usage values overflowing the estimate | low | mitigate | saturatedAdd for the prompt total, Long arithmetic for prefix chars (CacheDetector.kt, Usage.kt:76). CacheNotEngagedTest.maximumUsageInEveryBucketDoesNotOverflow | closed |
| T-03-36 | Information disclosure | prefix content reaching the event or trace | low | mitigate | PipelineEvent.CacheNotEngaged carries runId, strategy, provider, model only (PipelineEvent.kt:109-116); RunRecorder.cacheNotEngaged passes ids only. CacheNotEngagedTest.theEventNamesTheProviderAndModelOfTheHandleThatMadeTheCall | closed |
| T-03-37 | Tampering | second event kind / EngineCode doubling diagnostics | low | mitigate | Only RunRecorder.cacheNotEngaged is called. CacheNotEngagedTest.aLargeStaticPrefixThatTheCacheIgnoredRaisesExactlyOneEventAndNothingElse asserts zero EngineCode events and an empty trace code list | closed |
| T-03-38 | Information disclosure | key/transcript/tool args/replay text leaking on the routed path | high | mitigate | RedactionCanaryTest.noCanaryOrKeyAppearsAnywhereOnTheRoutedPathIncludingFallbackAndRefusals sweeps outcome, trace, events, recorded ProviderRequest calls, selection requests and every new type (30+ distinct values) with canary text, tool args, replay thinking/signature, two keys; plus the unreadable and foreign-credential refusal runs. Passes at HEAD | closed |
| T-03-39 | Tampering | engine-produced Phase 3 type frozen with a public constructor | medium | mitigate | Auditor re-generated the dump at HEAD and re-checked: SelectionRequest, BoundModel, ModelCapabilities (+Builder), ModelCapabilityTable and CachingMode have internal constructors (source), none appear as public-ctor owners in the dump. Plan-time evidence/api-surface-review.txt records the same verdict (PASSED) | closed |
| T-03-40 | Repudiation | Phase 1/2 gate silently regressed | medium | mitigate | Re-run at HEAD by the auditor: ./gradlew check exit 0, :core:cleanTest :core:test --no-build-cache 422 tests 0 failures, verify-negative-controls.sh 0 failures, verify-repo-hygiene.sh HYGIENE OK, verify-api-dump.sh API DUMP PROOF OK, review-api-surface.sh API SURFACE OK; single @Suppress in core/src/main (Guarded.kt file-level) | closed |
| T-03-41 | Tampering | api.txt left in a module directory arming the compat gate | medium | mitigate | review-api-surface.sh rejects --out ending api.txt or under core/providers/keystore and runs in an isolated copy with a real-tree status guard; auditor confirmed no core/api.txt, providers/api.txt or keystore/api.txt on disk and no tree change after the runs | closed |
| T-03-42 | Information disclosure | planning ids or secrets in published source | low | mitigate | scanBannedConstructs (invariants.gradle.kts:31,157) rejects T-n-n / WR-n / "Phase n D-n" in src/main comments under check; auditor grep of core/src/main and core/src/testFixtures for planning ids and key-shaped literals found none. See O-1 for the testFixtures scope note | closed |

*Status: open · closed · open - below high threshold (non-blocking)*
*Severity: critical > high > medium > low; only open threats at or above high count toward threats_open.*
*Disposition: mitigate (implementation required) · accept (documented risk) · transfer (third-party)*

---

## Accepted Risks Log

| Risk ID | Threat Ref | Rationale | Accepted By | Date |
|---------|------------|-----------|-------------|------|
| AR-1 | T-03-04 | Message and AssistantPart are intentionally sealed (decision D-01 and the architecture research); the surface script allow-list was widened to exactly seven on purpose and is now pinned by fully qualified name. A new leaf is a source-breaking change for exhaustive `when` in consumers and mappers, so it goes through a contract section 10 amendment rather than a patch tag. Code review WR-08 was a documented acceptable-skip | phase plan / code-review fix (WR-08) | 2026-09-30 |
| TR-1 | T-03-22 | Transfer: a provider implementation must return ModelResult.Failure rather than throw, per the AiProvider KDoc contract (the transports in :providers, Phases 4-5, own that obligation). The engine does not rely on it: every provider call is guarded and a throw collapses to a typed failure | phase plan (03-05) | 2026-09-30 |

*Carried from Phase 2 and still in force: AR-4 (JVM Errors propagate by design). The Phase 3 fakes deliberately throw AssertionError on script exhaustion (T-03-14, T-03-21) and rely on exactly that behavior.*

*Non-register observations (advisory, not blocking):*
- *O-1: scanBannedConstructs scans src/main only (invariants.gradle.kts, fileTree("src/main")). The planning-id rule over core/src/testFixtures was a gate-time check (evidence/phase-gate.txt step 7), not a persistent build gate. The auditor's grep found no hits today and testFixtures are kept out of the published component by an existing negative control, so exposure is low. Suggested hardening: add src/testFixtures to the scan.*
- *O-2: evidence/phase-gate.txt and evidence/api-surface-review.txt were produced before the ten code-review fix commits (WR-01 to WR-07, IN-01 to IN-03) and still show the pre-rename type name ProviderCall and 406 tests. The auditor re-ran the gates at HEAD (422 tests) so the verdict does not rest on stale evidence; refresh the files if they are kept as the phase record.*
- *O-3: scripts/verify-negative-controls.sh Part 2 edits tracked build files in place and restores them. It restored cleanly in this audit (tree status identical before and after), but it should not run concurrently with other work in the same checkout.*

---

## Unregistered Flags

None. No 03-*-SUMMARY.md contains a Threat Flags section, and no new attack surface without a threat mapping was found.

---

## Security Audit Trail

| Audit Date | Threats Total | Closed | Open | Run By |
|------------|---------------|--------|------|--------|
| 2026-09-30 | 46 | 46 | 0 | gsd-security-auditor (secure-phase, ASVS L1, block_on high) |

---

## Sign-Off

- [x] All threats have a disposition (mitigate / accept / transfer)
- [x] Accepted risks documented in Accepted Risks Log
- [x] `threats_open: 0` confirmed
- [x] `status: secured` set in frontmatter

**Approval:** verified 2026-09-30
