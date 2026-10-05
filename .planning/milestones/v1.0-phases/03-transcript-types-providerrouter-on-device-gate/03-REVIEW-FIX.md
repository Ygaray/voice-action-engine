---
phase: 03-transcript-types-providerrouter-on-device-gate
fixed_at: 2026-10-01T04:29:18Z
review_path: .planning/phases/03-transcript-types-providerrouter-on-device-gate/03-REVIEW.md
iteration: 1
findings_in_scope: 11
fixed: 10
skipped: 1
status: resolved
---

# Phase 3: Code Review Fix Report

**Fixed at:** 2026-10-01T04:29:18Z
**Source review:** .planning/phases/03-transcript-types-providerrouter-on-device-gate/03-REVIEW.md
**Iteration:** 1

**Summary:**
- Findings in scope: 11 (0 critical, 8 warning, 3 info; `fix_scope: all`)
- Fixed: 10
- Skipped: 1 (WR-08, a documented acceptable-skip, see below)

**Verification:** every fix was verified in the main checkout (not an isolated worktree), because the orchestrator directed commits straight onto `main`. After each fix `./gradlew check` was green (detekt zero baseline, `scanBannedConstructs`, all JVM tests) and `scripts/review-api-surface.sh` printed `API SURFACE OK`. The final state was re-run after the last commit: `BUILD SUCCESSFUL`, `API SURFACE OK sealed=AssistantPart,CommandOutcome,GateDecision,Message,RunTermination,StrategyOutcome,ToolStep classes=161`. No tags, no push, contract section 11 untouched, and the pre-existing unrelated changes under `.planning/graphs/`, `.planning/config.json`, `.planning/v1.0-MILESTONE-RUN.md`, `.gsd/` etc. were never staged.

Findings marked "requires human verification" are behavior/logic changes: syntax and tests pass, but the semantic choice should be confirmed by a person.

## Fixed Issues

### WR-01: A foreign `CancellationException` from app code escapes `complete()` / `execute()`

**Status:** fixed: requires human verification
**Files modified:** `core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/internal/Guarded.kt`, `core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/GuardedTest.kt`, `core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/NeverThrowTest.kt`
**Commit:** 58014e1
**Applied fix:** `guardedCore` now rethrows a non-timeout `CancellationException` only when the caller's coroutine is actually inactive; otherwise it is a typed fault (`Unexpected("CancellationException")`). Tests that pinned the old behavior were updated in the same commit: `GuardedTest.plainCancellationExceptionWhileActiveIsAFault`, a new `...WhileTheCallerIsCancelledPropagates`, and `NeverThrowTest.aStrategyThrowingAForeignCancellationWhileActiveBecomesFailedAndClosesOnceAsFailed` (the real-cancel path stays covered by the existing `NeverThrowTest` case that cancels the caller's `async` mid-strategy and asserts one `RunTermination.Cancelled` close, plus the new `GuardedTest.plainCancellationExceptionWhileTheCallerIsCancelledPropagates`).

### WR-02: `ToolSpec` keeps Kotlin default arguments (constructor-freeze trap)

**Status:** fixed (guard and documentation; `ToolSpec` itself deliberately unchanged)
**Files modified:** `core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/ToolSpec.kt`, `core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/ApiShapeTest.kt`
**Commit:** 5d8c77d
**Applied fix:** Replacing `ToolSpec`'s defaults with explicit overloads would remove source-compatible call shapes Phase 2 shipped and its tests pin (`ToolSpec(..., terminal = true)` and `ToolSpec(..., strict = true)` with a single named argument cannot be expressed as JVM overloads), so it was not done (non-additive versus Phase 2 output). Instead: (1) the default-argument-stub lint in `ApiShapeTest` now sweeps every main class, not just `transcript` and `provider`; (2) an explicit, documented `STUB_EXCEPTIONS` allow-list names `ToolSpec` and `CommandInput` (public constructors, frozen) plus five internal-constructor/internal classes, and a second test fails if an entry goes stale; (3) the `ToolSpec` KDoc states the rule: future optional attributes arrive as separate members (a `with...` function), never as a further constructor parameter. This closes the unguarded growth path without a source break.

### WR-03: Automatic-caching diagnostic can raise a false `CacheNotEngaged` for concurrent first requests

**Status:** fixed: requires human verification
**Files modified:** `core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/provider/BoundModel.kt`, `core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/CacheNotEngagedTest.kt`
**Commit:** 6f90de0
**Applied fix:** `RoutedModel.complete` snapshots the successful-response count before sending and judges the response as turn `prior + 1`, so two overlapping first requests are both turn 1 and neither is flagged. New test `twoOverlappingFirstRequestsOnOneHandleAreBothTurnOneAndRaiseNothing` fails on the old code (confirmed by stashing the fix) and passes now.

### WR-04: Credential-source and capability faults mapped to misleading reasons

**Status:** fixed: requires human verification
**Files modified:** `core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/provider/ModelRouter.kt`, `core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/ModelRouterTest.kt`
**Commit:** d66af55
**Applied fix:** A throwing `CredentialSource` is now `Unexpected(errorClass)` (or `Timeout` on a leaked timeout) with the `CREDENTIAL_SOURCE_ERROR` trace code retained; `CredentialUnreadable` is produced only for `CredentialLookup.Unreadable`. A capability lookup fault goes through the same fault-to-reason mapping, so a leaked timeout there is `Timeout`. The old `source_error` cause constant was removed. `anUnreadableKeyAndAThrowingCredentialSourceAreDistinctFromMissing` was updated, and two leaked-timeout tests were added (credential source, capability lookup).

### WR-05: App-supplied `clock` called outside any guard and under the recorder lock

**Status:** fixed: requires human verification
**Files modified:** `core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/internal/Guarded.kt`, `core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/internal/GuardedClock.kt` (new), `core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/telemetry/RunRecorder.kt`, `core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/provider/BoundModel.kt`, `core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/provider/ModelRouter.kt`, `core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/pipeline/PipelineBuilder.kt`, `core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/GuardedClockTest.kt` (new), `.../CacheNotEngagedTest.kt`, `.../ModelRouterTest.kt`, `.../RunSetupGuardTest.kt`
**Commit:** 4d25842
**Applied fix:** Each run wraps the app clock in a new internal `GuardedClock`: the first reading passes through unchanged (so a clock broken from the start still yields the existing "run could not begin" `Failed(Unexpected)` with no sink call), and after one good reading a throwing clock answers with the last good reading (latencies read as zero) instead of escaping. `RunRecorder` now reads the clock before taking its lock in `tierStarted`, `tierFinished`, `flushInFlight` and `snapshot`, matching its header claim; `flushInFlight` still reads the clock only when a tier is in flight. `RoutedModel.complete` reads time through `recorder.runClock`, so it no longer needs a clock parameter (removed from `RoutedModel` and the internal `ModelRouter` constructor).
**Notes:** the repo bans `runCatching` (`scanBannedConstructs`), so the non-suspend guard is a new `guardedPlain` in `Guarded.kt`. To avoid adding a second `@Suppress` in `core/src/main`, the existing single `@Suppress("TooGenericExceptionCaught")` was moved from `guardedCore` to a file-level `@file:Suppress` in `Guarded.kt` (still one suppression, one file, same rule). The reviewer should confirm that form is acceptable.

### WR-06: Free-text escape hatches break the "reasons never carry free-form text" invariant

**Status:** fixed: requires human verification
**Files modified:** `core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/failure/ReasonSupport.kt`, `.../failure/FailureReason.kt`, `.../failure/FailureDetails.kt`, `.../transcript/ModelResponse.kt`, `.../internal/Guarded.kt`, `core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/FreeTextSlotsTest.kt` (new)
**Commit:** 98d9229
**Applied fix:** Construction now refuses non-identifiers: `FailureReason.Other.code` and `FailureDetails.providerErrorType` must match `[A-Za-z0-9_.:-]{1,64}`; `FailureDetails.requestId` and `ModelResponse.requestId` the same charset up to 128 characters; `FailureReason.Unexpected.errorClass` must be a class name (`[A-Za-z0-9_.$-]{1,128}`). `errorClassOf` now sanitizes and truncates engine-made class names so the engine's own `Unexpected(...)` construction can never throw inside the never-throw path. New `FreeTextSlotsTest` feeds `"sk-CANARY\nsecret"`, whitespace, quotes, over-length and empty text through every slot and asserts refusal without echoing the value. KDoc on each type documents the rule; the transports (Phases 4 and 5) must drop a server value that does not fit rather than pass it through. The limit constants are private and named so they do not trip `NoHardCodedConstantsTest` or the public-static-field lint.

### WR-07: Two public types named `ProviderCall` in one API

**Status:** fixed
**Files modified:** `core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/provider/ProviderRequest.kt` (renamed from `ProviderCall.kt`), `.../provider/AiProvider.kt`, `.../provider/BoundModel.kt`, `core/src/testFixtures/.../testing/FakeAiProvider.kt`, `core/src/test/.../ApiShapeTest.kt`, `FakeAiProviderTest.kt`, `ModelRouterTest.kt`, `RedactionCanaryTest.kt`
**Commit:** 94c5ed6
**Applied fix:** The new Phase 3 `provider.ProviderCall` was renamed `ProviderRequest` (the reviewer's cheap option). `PipelineEvent.ProviderCall` is Phase 2 output and keeps its name. The name is not a locked decision in `03-CONTEXT.md` or the contract; it appears only in plan and summary prose, which was left as the historical record. Downstream phase plans (04 onward) do not reference it yet.

### IN-01: "Exactly one trace code" is not true on two refusal paths, and a cancelled bind can double-record

**Status:** fixed
**Files modified:** `core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/provider/ModelRouter.kt`, `core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/OnDeviceGateTest.kt`
**Commit:** 0c19c44
**Applied fix:** `PROVIDER_FALLBACK` is recorded only after the fallback binding succeeds (so a cancelled or refused fallback bind no longer records it, and a re-bind cannot double-record). The class KDoc now says "one terminal trace code, preceded by the code of its cause when there is one". The test that pinned `["provider_fallback", "credential_missing"]` for a fallback whose provider has no key now expects `["credential_missing"]`. Note this drops the "a fallback was attempted" breadcrumb from the trace for that refusal path, as the review's fix prescribes; the refusal reason still names the fallback provider.

### IN-02: The fault-to-reason mapping is copied four times

**Status:** fixed
**Files modified:** `.../internal/Guarded.kt`, `.../pipeline/TierWalk.kt`, `.../pipeline/CommandPipeline.kt`, `.../pipeline/HeldCommit.kt`, `.../provider/BoundModel.kt`, `.../provider/ModelRouter.kt`
**Commit:** c403e42
**Applied fix:** Added `internal fun EngineFault.toReason(): FailureReason` next to `EngineFault` and used it at every site (five copies were found, including `HeldCommit`).

### IN-03: The surface script compares simple names and tolerates dropped files

**Status:** fixed
**Files modified:** `scripts/review-api-surface.sh`
**Commit:** fe4cff3
**Applied fix:** Sealed types are now reported with their package (taken from the dump's `package ... {` lines) and matched against a fully qualified allow-list; a negative check confirmed that a wrong-package `Message` is rejected. `tar --ignore-failed-read` is gone: the file list is filtered to files that exist, tar failures abort the run, and the extracted file count must equal the listed count or the script prints `API SURFACE FAIL`. The `API SURFACE OK sealed=...` line keeps its previous simple-name, alphabetical format.

## Skipped Issues

### WR-08: `Message` and `AssistantPart` are sealed, which freezes the conversation universe

**File:** `core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/transcript/Message.kt:13`, `transcript/AssistantPart.kt:11`; `scripts/review-api-surface.sh:15`
**Reason:** documented acceptable-skip. The closed sets are locked design, so opening them would contradict it rather than fix it: `AssistantPart` is closed by decision D-01 in `03-CONTEXT.md` ("neutral parts only Text + ToolCall; thinking lives only in raw"), and `Message` is closed by the architecture research (`ARCHITECTURE.md:493` lists it sealed), recorded as resolved in `03-RESEARCH.md` (Open Question 1, assumption A4: seal only these two, widen `ALLOWED_SEALED` in the same plan). Phases 4, 5 and 8 build exhaustive `when` mappers on that closedness. A new leaf (image or audio user content, server-tool parts) is therefore a deliberate contract change that goes through a section 10 amendment, not a patch-level addition. The surface script's allow-list entry for the two types is intentional and, after IN-03, now pinned by fully qualified name. Recommendation for the orchestrator: if a later amendment wants the open form, record it then; nothing in v1.0 requires it.
**Original issue:** A new `Message` or `AssistantPart` leaf is a source-breaking change for every exhaustive `when` in consumers and mappers, unlike the other open vocabularies in this phase, and the surface script was widened to bless the two sealed types.

---

_Fixed: 2026-10-01T04:29:18Z_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 1_
