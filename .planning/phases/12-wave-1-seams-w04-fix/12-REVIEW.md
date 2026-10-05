---
phase: 12-wave-1-seams-w04-fix
reviewed: 2026-10-05T23:30:00Z
depth: standard
files_reviewed: 66
files_reviewed_list:
  - API.md
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
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/agentic/AgenticDispatch.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/agentic/AgenticLoopStrategy.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/CommandSession.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/OutcomeResolver.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/singleshot/SingleShotOutcomes.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/singleshot/SingleShotStrategy.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/telemetry/CommandTrace.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/telemetry/RunRecorder.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/transcript/ModelRequest.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/transcript/ReasoningMode.kt
  - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/AgenticLoopDispatchTest.kt
  - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/AgenticLoopReasoningTest.kt
  - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/ApiShapeTest.kt
  - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/CacheNotEngagedTest.kt
  - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/CommitPathTest.kt
  - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/HeldReportingTest.kt
  - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/InFlightTierTraceTest.kt
  - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/ModelRouterTest.kt
  - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/RedactionCanaryTest.kt
  - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/SingleShotOutcomeMappingTest.kt
  - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/SingleShotRequestTest.kt
  - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/SingleShotResolveTest.kt
  - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/TierPolicyTest.kt
  - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/TierWalkTest.kt
  - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/TraceTest.kt
  - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/TranscriptTypesTest.kt
  - INTEGRATION.md
  - keystore/build.gradle.kts
  - keystore/src/main/kotlin/io/github/ygaray/voiceactionengine/keystore/ApiKeyStore.kt
  - keystore/src/main/kotlin/io/github/ygaray/voiceactionengine/keystore/DelicateKeyAccess.kt
  - keystore/src/main/kotlin/io/github/ygaray/voiceactionengine/keystore/KeyAccess.kt
  - keystore/src/test/kotlin/io/github/ygaray/voiceactionengine/keystore/ApiKeyStoreKeyAccessTest.kt
  - keystore/src/test/kotlin/io/github/ygaray/voiceactionengine/keystore/KeystoreApiShapeTest.kt
  - providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/anthropic/AnthropicModels.kt
  - providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ChatErrors.kt
  - providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/chat/OpenAiModelRules.kt
  - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/anthropic/AnthropicForcedToolTest.kt
  - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/anthropic/AnthropicModelsTest.kt
  - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ChatEncoderTest.kt
  - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ChatErrorMapTest.kt
  - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ChatModelsTest.kt
  - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ChatTransportTest.kt
  - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/chat/OpenAiModelRulesTest.kt
  - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ReasoningWireParityTest.kt
  - providers/src/test/resources/golden/chat/requests/openai.json
  - sample/src/main/kotlin/io/github/ygaray/voiceactionengine/sample/legs/LegCatalog.kt
  - sample/src/main/kotlin/io/github/ygaray/voiceactionengine/sample/legs/LegRunner.kt
  - sample/src/test/kotlin/io/github/ygaray/voiceactionengine/sample/docs/DocSnippetsTest.kt
  - sample/src/test/kotlin/io/github/ygaray/voiceactionengine/sample/SmokeLegTest.kt
  - scripts/run-sample-gate1.sh
  - scripts/verify-docs-coverage.sh
  - scripts/verify-keyaccess-opt-in.sh
  - scripts/verify-negative-controls.sh
  - scripts/verify-sample-device-guard.sh
findings:
  critical: 0
  warning: 3
  info: 4
  total: 7
status: issues_found
---

# Phase 12: Code Review Report

**Reviewed:** 2026-10-05
**Depth:** standard
**Files Reviewed:** 66
**Status:** issues_found

## Summary

Reviewed the phase's diff (`55d087f..HEAD`) across `:core`, `:keystore`, `:providers`, `:sample`, the docs and the verify scripts. I traced the new `providerCallId` plumbing end to end (`Extraction` / `AgenticDispatch` / `SingleShotStrategy.submitAll` to `RunSession.submit` to `CommitCoordinator` to `ApplyStep` / `ActionLedger` / `HeldProposal` to `HeldCommit`). Every `ActionDetails(...)` and `submit(...)` call site passes the id, and the positional argument order is correct. I also checked `cappedByPolicy` against `PolicyPreCheck` and `TierWalk`, the `onFailed` routing against `BoundModel`/`RoutedModel`, the W04 deny-list and classifier, and the key-custody opt-in.

I found no data-loss, secret-leak or incorrect-behavior defects that block shipping. The id never reaches a `toString()`, trace or event, and a canary test covers that. The `onFailed` hook only sees post-bind failures, since `SingleShotStrategy.withModel` checks `model.refusal` first. The W04 regexes are anchored and correctly exclude `gpt-5.5`, `gpt-6-sol` and `gpt-6-astral`.

The three warnings are about guardrails: one design edge in `onFailed` (privacy-relevant), one weak spot in the opt-in enforcement, and one script signal-handling bug. The info items are maintainability notes.

## Warnings

### WR-01: `onFailed` also receives on-device provider runtime failures, which an app's broad `else -> Escalate` would send to the cloud

**File:** `core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/singleshot/SingleShotOutcomes.kt:36-41`, `core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/singleshot/SingleShotStrategy.kt:218-231`
**Issue:** D-01 keeps *binding* refusals out of the hook, and that holds. `RefusedModel` is intercepted by `model.refusal` before `complete()`. A failure that comes back from `RoutedModel.complete()` is different. That includes a provider fault (`guarded` maps it to `Failure(fault.toReason())`) and a `ProviderUnavailable(ON_DEVICE, ...)` returned by an on-device provider that was bound successfully. Both now go to `hooks.onFailed`. `BoundModel`'s own KDoc (lines 24-27) says an on-device refusal "ends the command loudly instead of escalating, so the command never climbs to a tier" that could leak the transcript. The `onFailed` KDoc lists "HTTP 400, a transport fault, or a pre-call capability refusal" but never says on-device runtime failures also arrive. An app that writes `onFailed = { r, d -> StrategyOutcome.Escalate(...) }` to rescue cloud 400s will silently also escalate an on-device failure to the next (cloud) tier, carrying the same transcript.
**Fix:** Say it in the KDoc and in the INTEGRATION.md/API.md `onFailed` note: "also receives runtime failures of an on-device provider; branch on `reason` (for example `is FailureReason.ProviderUnavailable` for `ProviderId.ON_DEVICE`) before escalating". If the contract is meant to hold, skip the hook for on-device failures and keep `Failed(reason, details)`:

```kotlin
else -> if (failure.isOnDeviceFailure()) StrategyOutcome.Failed(failure.reason, failure.details)
        else hooks.onFailed(failure.reason, failure.details)
```

### WR-02: The `DelicateKeyAccess` opt-in is switched off module-wide in `:keystore`, so the module cannot catch an accidental leak of the marked type, and the 4-argument constructor bypasses the gate in bytecode

**File:** `keystore/build.gradle.kts:32-33`, `keystore/src/main/kotlin/io/github/ygaray/voiceactionengine/keystore/ApiKeyStore.kt:36-41`
**Issue:** `optIn.add("...DelicateKeyAccess")` applies to every compilation of `:keystore` (main and test). The compiler's opt-in propagation check never runs on the library's own code. If a later change adds a public member that exposes `KeyAccess` (a getter, a factory, a second public constructor) and forgets `@DelicateKeyAccess`, nothing in this module goes red. The only check is the cross-module plant script, which covers exactly the two constructs it plants. Separately, the primary constructor `(DataStore, List, CoroutineDispatcher, KeyAccess)` is `internal` in Kotlin but public in bytecode (the new `KeystoreApiShapeTest` comment admits this). A Java caller, or anything using reflection, gets the seam with no opt-in. `RequiresOptIn` is not enforced for Java at all, so the gate is Kotlin-only by construction.
**Fix:** Drop the module-wide flag and put `@OptIn(DelicateKeyAccess::class)` on the few internal use sites (`ApiKeyStore`'s fields and constructors, `SecretReader`, `AndroidKeyStoreKeyAccess`). The compiler then enforces propagation for every future public member. Add a sentence to the `DelicateKeyAccess` KDoc that the gate is Kotlin-only.

### WR-03: `verify-keyaccess-opt-in.sh` keeps running after Ctrl-C because the INT/TERM trap does not exit

**File:** `scripts/verify-keyaccess-opt-in.sh:11-12`
**Issue:** `trap cleanup EXIT INT TERM` runs `cleanup` and then bash resumes the script. Pressing Ctrl-C therefore removes the plant and the log, then goes on to write the next plant and start another `./gradlew :sample:compileDebugKotlin`. Because `set -e` is not on, the aborted compile is counted as a failure ("went red for the WRONG reason" or "did not compile"). The script also recreates `$LOG` after `cleanup` deleted it. The header promises cleanup "including on Ctrl-C", and that holds for the files, but the run itself is not stopped. This is a script that is invoked from `verify-negative-controls.sh`, which then reports a bogus `FAIL [keyaccess opt-in]`.
**Fix:**

```bash
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
```

(`exit` fires the EXIT trap, so `cleanup` runs once.)

## Info

### IN-01: Phase directory is hard-coded in two scripts and will break when the phase is archived

**File:** `scripts/run-sample-gate1.sh:39-41`, `scripts/verify-sample-device-guard.sh:124-125,274`
**Issue:** The decision file and evidence directory are now pinned to `.planning/phases/12-wave-1-seams-w04-fix`. The previous phase hard-coded Phase 10 and this phase had to retarget it by hand. After milestone close the directory moves to `.planning/milestones/v1.1-phases/...` and both scripts will refuse to push keys ("decision file missing"). The device-guard test hard-codes the same path in three more places, so a retarget is a multi-file edit.
**Fix:** Take it from the environment with the current phase as the default (`PHASE_DIR="${VAE_GATE1_PHASE_DIR:-.planning/phases/12-wave-1-seams-w04-fix}"`) and let the guard test set the same variable.

### IN-02: The direct "Responses-only, no Chat tools" list covers only the `gpt-5`/`gpt-6` `-pro` and `-codex` ids

**File:** `providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/chat/OpenAiModelRules.kt:19-20`
**Issue:** Other OpenAI ids that are Responses-only (for example `o1-pro`, `o3-pro`, `o3-deep-research`) still pass `toolsOnChat` and reach the network, then depend on the endpoint-marker text match in `ChatErrors`. That is the old W04-style failure (a generic error if the marker wording changes). This is a gap in a "targeted deny-list" by design (D-05), not a regression, but the KDoc/INTEGRATION.md text "Direct OpenAI `-pro` and `-codex` ids are refused" reads as a general rule.
**Fix:** Either add an `o\d+-(?:pro|deep-research)` arm to `PRO_OR_CODEX`, or word the docs as "the `gpt-5`/`gpt-6` `-pro` and `-codex` ids" and note that the `capabilities(...)` override is the escape hatch for any other id.

### IN-03: The new reasoning-effort classifier reports `ModelUnsupported` for an engine wire-table gap

**File:** `providers/src/main/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ChatErrors.kt:147-150`
**Issue:** The fallback matches on 400 + `param=reasoning_effort` + `code=unsupported_value`. It fires whenever the endpoint rejects the effort the *engine* chose, including a model the table wrongly sends `none` to (the original W04 cause). The app then sees "model_unsupported" and may stop using a model that works, rather than a signal that the engine's table needs a row. The trace carries no hint of which. It is a deliberate backstop (D-05) and is tested, so this is only a diagnosability note.
**Fix:** Record a distinct `TraceCode` (for example `REASONING_EFFORT_REJECTED`) next to the reason, so the cause is visible without changing the public reason.

### IN-04: `LongParameterList.constructorThreshold` is raised for every module to fit one class

**File:** `config/detekt/detekt.yml:11-14`
**Issue:** The threshold went from 8 to 9 so `ModelRequest` (eight parameters) passes. detekt reports at the threshold, so the change loosens the gate for all constructors in `:core`, `:providers` and `:keystore`, not just `ModelRequest`. The justification comment is accurate, but the project rule is "tune rules with a justification", and a narrow `@Suppress("LongParameterList")` on the one class would keep the zero-baseline gate tight.
**Fix:** Revert to 8 and put one justified `@Suppress` on `ModelRequest` (and `TierAttempt`/`ExecutedAction` if they trip), or accept the global change knowingly.

---

_Reviewed: 2026-10-05_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
