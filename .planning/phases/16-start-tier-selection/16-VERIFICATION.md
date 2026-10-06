---
phase: 16-start-tier-selection
verified: 2026-10-06T21:30:00Z
status: passed
score: 5/5 must-haves verified
covered_files:
  - .planning/REQUIREMENTS.md
  - .planning/phases/16-start-tier-selection/16-01-PLAN.md
  - .planning/phases/16-start-tier-selection/16-01-SUMMARY.md
  - .planning/phases/16-start-tier-selection/16-02-PLAN.md
  - .planning/phases/16-start-tier-selection/16-02-SUMMARY.md
  - .planning/phases/16-start-tier-selection/16-03-PLAN.md
  - .planning/phases/16-start-tier-selection/16-03-SUMMARY.md
  - .planning/phases/16-start-tier-selection/16-04-PLAN.md
  - .planning/phases/16-start-tier-selection/16-04-SUMMARY.md
  - .planning/phases/16-start-tier-selection/16-05-PLAN.md
  - .planning/phases/16-start-tier-selection/16-05-SUMMARY.md
  - .planning/phases/16-start-tier-selection/16-06-PLAN.md
  - .planning/phases/16-start-tier-selection/16-06-SUMMARY.md
  - .planning/phases/16-start-tier-selection/16-07-PLAN.md
  - .planning/phases/16-start-tier-selection/16-07-SUMMARY.md
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/pipeline/PipelineBuilder.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/pipeline/PolicyPreCheck.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/pipeline/RouterPicker.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/pipeline/StartTierPicker.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/pipeline/StartTierPicking.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/pipeline/TierPolicy.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/pipeline/TierSelector.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/pipeline/TierWalk.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/plan/PlanBinding.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/telemetry/CommandTrace.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/telemetry/PipelineEvent.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/telemetry/RunRecorder.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/telemetry/SelectionBook.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/telemetry/StartTierSelection.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/telemetry/TraceCode.kt
covered_digest: "v1:sha256:2f39aff2f5a8fdc5e9f9359cd6e45d221479e36d13703b61426ce2f6789b06a4"
behavior_unverified: 0
overrides_applied: 0
advisory:
  - finding: "WR-01 hardening (SelectionBook.take drops a picker turn recorded after the pick closed) has no test that exercises a late picker turn"
    category: other
    reason: "Not a roadmap success criterion or PLAN must-have; code read as correct. A cheap JVM test is recommended (see Recommended Tests)."
    evidence_status: "code reading only; no test"
  - finding: "router_fallback is also recorded when no picker was called (no model tier left, policy-forbidden picker); a dashboard counting it as picker failure rate over-counts"
    category: other
    reason: "Review IN-01 skipped by design: a distinct code is a frozen-surface change and SC4 itself specifies router_fallback for the no-call case. Documented in API.md."
    evidence_status: "documented acceptable-skip"
---

# Phase 16: Start-Tier Selection Verification Report

**Phase Goal:** An app can decide where each command's LLM walk starts, with its own picker or the engine's opt-in cheap-model Router. Grammar stays a free pre-pass, picker mistakes fall back to Linear loudly, and offline-only commands never pay for a router call.
**Verified:** 2026-10-06
**Status:** passed
**Re-verification:** No, initial verification

Evidence base: source read directly (not SUMMARY claims), the committed Gradle test results in `core/build/test-results/test` (written 15:11:38, after the last code-review fix commit c94bd9d at 15:09; the later commits are KDoc-only), and the git diff against the phase base 9c88961. No Gradle run was started (host swap was full, 2047/2047 MB; the phase gate evidence was sufficient).

## Goal Achievement

### Observable Truths (ROADMAP success criteria)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | An app passes `TierSelector.Custom(picker)`; the suspend picker sees input and eligible LLM tier ids, the walk starts at the returned tier, and model calls through `PickContext` appear in the trace and count against the token budget | VERIFIED | `TierSelector.Custom` (public, with `invoke(picker){}` builder) sets `picking = PickingSpec(...)`. `TierWalk.walkPicked` calls `StartTierPicking.startIn`, which builds `llm` ids (providers non-empty only), invokes `spec.picker.pick(input, llm, RunPickContext(...))` and returns the index of the picked tier; `climb(rest.drop(start))` starts there. `RunPickContext.recordTurn` goes to `recorder.turnRecorded(picker, turn)`, which adds `tokenTotal` (counts toward `tokensUsed`) and `SelectionBook.take` places the turn in `trace.selection.turns`. `model()` binds through `scope.router.bind` under the picker id so selection, credential and policy gates apply. Tests: `StartTierPickerTest` 9/9 (`aCustomPickerChoosesWhereTheLlmWalkStarts`, `thePickerTurnIsInTheSelectionNotInAnyAttempt`, `anAppNamesItsPickerAndTheSelectionSeamBindsItsModel`), `StartTierPickerTest` asserts `tokensUsed` 5 seen by the next tier. |
| 2 | A zero-call tier at the ladder head always runs first as a free pre-pass; the picker runs only if it hands over; eligible list never contains a zero-call tier | VERIFIED | `walkPicked`: `head = tiers.takeWhile { providers.isEmpty() }`, `climb(head)` first, picker only in the `?: run {}` branch (null outcome). `startIn` filters `llm` to `providers.isNotEmpty()`. Head runs through `runTier`, so gate, suppression and trace apply; carry passes unchanged. Tests: `StartTierPrePassTest` 9/9, including the real P14 grammar head (`aMatchingPhraseOnTheRealGrammarHeadCompletesWithNoPickerAndNoProviderCall`, `aPhraseTheRealGrammarHeadDoesNotKnowCallsThePickerWithOnlyTheModelTierIds`), carry identity, `aMidLadderZeroCallTierIsNeverOffered`. |
| 3 | A picker returning null, an ineligible id or throwing sends the walk down Linear with `router_fallback`; the command never fails because of the picker; cancellation still propagates | VERIFIED | `ask`: `guarded(onFault = { null }) { withTimeoutOrNull(pickerTimeoutMillis) { pick } }`; `picked = choice?.takeIf { it in llm }`; null records `TraceCode.ROUTER_FALLBACK` and returns index 0 (Linear). `guarded` rethrows `CancellationException` (project's single never-throw helper). Caller cancel mid-pick and the command deadline flush the open selection via `flushInFlight` (`CommandPipeline` lines 110/189/203). Tests: `StartTierFallbackTest` 15/15 (null, unknown id, zero-call id, throw, hung picker timeout, foreign cancellation, leaked inner timeout, caller cancel gets cancellation plus trace, deadline vs picker timeout ordering). |
| 4 | When policy leaves no eligible LLM tier (e.g. offline-only), the picker is never called, no router model call is made; the walk records `router_fallback` and proceeds as Linear | VERIFIED | `startIn`: `if (llm.isEmpty() \|\| !tierPermitted(spec.capabilities, policy, onDeviceAvailable)) { recordCode(ROUTER_FALLBACK); return 0 }` before any `ask`. `tierPermitted` denies a cloud picker under `offlineOnly`, an empty/excluding `allowedProviders`. Tests: `StartTierPolicyTest` 9/9 (`offlineOnlyWithNoOnDeviceTierLeavesNoModelTierAndNeverCallsThePicker`, `anOfflineCommandNeverCallsACloudPickerEvenWithAReadyOnDeviceTier`, `anEmptyAllowedProviderSetLeavesNoModelTierAndNeverCallsThePicker`, `aPickerWhoseProvidersThePolicyExcludesIsNotCalled...`), `RouterSelectorTest.theRouterUnderOfflineOnlyNeverCallsAndTheCommandIsCapped`. |
| 5 | `TierSelector.Router(...)` is built on the same seam, off by default (no opt-in walks exactly as v1.0 Linear); when on, telemetry reports tiers saved vs Linear | VERIFIED | `TierSelector.Router` uses `PickingSpec(RouterPicker(descriptions), id, caps, skipsSingleTier = true)`; default selector stays `Linear` (`picking == null`, `climb(tiers.drop(start))`). `RouterPicker` makes one forced `pick_start_tier` call, `ReasoningMode.OFF`, `maxTokensPerTurn` limit, token-ceiling pre-check, never-throw decoder matching against eligible ids by value. `StartTierSelection.tiersBypassed = llm.indexOf(picked)` is on `trace.selection` and in `PipelineEvent.StartTierSelected`. Tests: `RouterSelectorTest` 7/7 (including `aPipelineWithNoSelectorNeverCallsTheRouter`), `RouterRequestTest` 7/7 (schema/instructions/user message byte-pinned), `StartTierRedactionTest` 2/2, and `TierWalkLinearCharacterizationTest` 10/10 written before any `TierWalk` edit (commits ad5ec91/c41e123 precede the 16-02 commits that change TierWalk) guarding the default-off Linear/Fixed walk. |

**Score:** 5/5 truths verified; 0 present-but-behavior-unverified.

Behavior-dependent truths 1 to 5 each have a named passing test (committed results: StartTierPicker 9, Fallback 15, Policy 9, PrePass 9, RouterSelector 7, RouterRequest 7, Redaction 2, Linear characterization 10; whole `:core` suite 1091 tests, 0 failures, 0 skipped, results timestamped after the final code change).

### Required Artifacts

| Artifact | Expected | Status | Details |
|---|---|---|---|
| `core/.../pipeline/StartTierPicker.kt` | public `fun interface StartTierPicker`, `PickContext` | VERIFIED | Substantive, KDoc'd, no second abstract method; no write path |
| `core/.../pipeline/TierSelector.kt` | `Custom`, `Router`, builders, `picking` hook | VERIFIED | Wired into `TierWalk.run` via `ladder.selector.picking` |
| `core/.../pipeline/StartTierPicking.kt` | pre-pass pick, policy gate, fallback, timeout | VERIFIED | Wired from `TierWalk.walkPicked` |
| `core/.../pipeline/RouterPicker.kt` | engine router, pinned bytes | VERIFIED | Wired through `Router.picking` |
| `core/.../pipeline/TierPolicy.kt` | `pickerTimeoutMillis` (default 2 s, `require > 0`) | VERIFIED | Enforced by `withTimeoutOrNull` in `ask` |
| `core/.../telemetry/SelectionBook.kt`, `StartTierSelection.kt` | separate selection record (D-02) | VERIFIED | `RunRecorder.selectionBook`, snapshot `selection = selectionBook.current` |
| `core/.../telemetry/PipelineEvent.kt`, `TraceCode.kt`, `CommandTrace.kt` | `StartTierSelected`, `ROUTER_FALLBACK`, `trace.selection` | VERIFIED | Event dispatched in `SelectionBook.finished`; code at TraceCode:155 |
| `core/.../strategy/plan/PlanBinding.kt` | RT-01 64-char step-id cap | VERIFIED | Pinned in 16-01; documented in API.md (IN-03) |
| API.md / INTEGRATION.md | docs rows and "Choosing where the model walk starts" | VERIFIED | Docs coverage check reported `DOC COVERAGE OK` in the 16-07 gate |

### Key Link Verification

| From | To | Via | Status |
|---|---|---|---|
| `TierWalk.run` | `StartTierPicking.startIn` | `ladder.selector.picking` -> `walkPicked` | WIRED |
| `StartTierPicking.ask` | `SelectionBook.started/finished` | `scope.recorder.selectionBook` | WIRED |
| `RunPickContext.recordTurn/model` | `RunRecorder.turnRecorded`, `ModelRouter.bind` | `RunScope` | WIRED |
| `RunRecorder.turnRecorded` | `SelectionBook.take` | partitions picker turns from tier attempts | WIRED |
| `CommandPipeline` exits | `RunRecorder.flushInFlight` | `selectionBook.flush(outcome)` then tier flush | WIRED |
| `SelectionBook.finished` | `PipelineEvent.StartTierSelected` | `dispatch.send` after lock release | WIRED |

### Data-Flow Trace (Level 4)

| Artifact | Data | Source | Real data | Status |
|---|---|---|---|---|
| `trace.selection` | picker id, outcome, picked, eligible, tiersBypassed, latency, turns | `SelectionBook.started/take/finished` driven by real `ask` result and real `turnRecorded` calls | Yes | FLOWING |
| Router pick | `StartTierSelection.picked` | `decodePick(model.complete(routerRequest(...)), eligible)` | Yes (fake provider in tests; live wording check deferred to Phase 19, OI-6) | FLOWING |

### Behavioral Spot-Checks

| Behavior | Evidence | Status |
|---|---|---|
| Phase-16 test classes pass | committed `core/build/test-results/test` XML: 68 tests across the 8 classes above, 0 failures | PASS |
| WR-03 test `aMidLadderZeroCallTierIsBypassedWhenThePickLandsPastIt` was added unrun by the fixer | present in `TEST-...StartTierFallbackTest.xml`, no failure | PASS |
| Full `:core` suite | 1091 tests, 0 skipped, 0 failed (XML timestamps 15:11:38, after c94bd9d) | PASS |
| Frozen API untouched | `git diff 9c88961 HEAD -- core/api.txt providers keystore` empty; Metalava compat green in the 16-07 gate and the review-fix re-run | PASS |

Step 7c (probes): no phase-declared probe scripts (JVM-only phase); skipped.

### Requirements Coverage

| Requirement | Source Plan(s) | Description | Status | Evidence |
|---|---|---|---|---|
| ROUT-01 | 16-02, 16-03, 16-07 | `StartTierPicker` + `TierSelector.Custom`, `PickContext` counted in budget and trace | SATISFIED | Truth 1 |
| ROUT-02 | 16-02, 16-05, 16-07 | Zero-call head runs first; picker chooses among remaining LLM tiers | SATISFIED | Truth 2 |
| ROUT-03 | 16-04, 16-07 | null / ineligible / throw -> Linear + `router_fallback`, never a failure | SATISFIED | Truth 3 |
| ROUT-04 | 16-05, 16-07 | No eligible LLM tier (offline-only) -> picker never called, no router call | SATISFIED | Truth 4 |
| ROUT-05 | 16-01, 16-03, 16-06, 16-07 | `TierSelector.Router`, default off, telemetry of tiers saved | SATISFIED | Truth 5 |

All five IDs appear in PLAN frontmatter and in REQUIREMENTS.md (marked Complete, mapped to Phase 16). No orphaned requirements: REQUIREMENTS.md maps no other ID to Phase 16.

### Anti-Patterns Found

None. No `TBD|FIXME|XXX|TODO|HACK` in any file changed under `core/src` since 9c88961. No stubs: every new class has a caller and test.

### Code-Review Fix Assessment

| Fix | Assessment |
|---|---|
| WR-01 (c94bd9d) | Code is correct by reading; see below. No test; recommended, non-blocking. |
| WR-02 (ab2872d) | KDoc/docs only; scopes the head-first claim to Custom/Router; `Fixed` behavior pinned by the characterization test. |
| WR-03 (45b92b3) | Docs plus a pinning test, present and passing in the committed results. |
| IN-02, IN-03, IN-04 | Docs and a behavior-neutral refactor (`firstIfAny`); suite green after. |
| IN-01 skipped | Acceptable: SC4 itself requires `router_fallback` for the no-call case; split code would be a frozen-surface change. Carried as an advisory. |

**WR-01 detail (SelectionBook.take late-picker-turn handling).** `take` runs under the recorder's lock (called from `turnRecorded`'s `synchronized(lock)`; `SelectionBook` shares that lock). It returns `mine = picker != null && strategy == picker`. A turn is appended only while `closed == null`; once the pick is closed, a turn under the picker id is claimed (returns true, so it is not added to `book.turns`) but dropped. Effects, all consistent with the intent: (a) the late turn cannot be attributed to the tier then in flight or silently cleared by `TierBook.start`; (b) its tokens still go to `tokenTotal` (the `saturatedAdd` is outside the `take` branch), so the budget stays honest; (c) the `ProviderCall` event is still emitted under the picker id, so a live listener sees it. Edge cases checked: before `started`, `picker == null` so nothing is claimed (a picker id cannot collide with a tier id, enforced at build in 16-03); a second `started` resets `turns` and `closed` (per-run recorder, so no cross-command bleed); `flush` closes through `finished`, so a cancelled pick's later turn is dropped the same way. The one residual trade-off is that a dropped late turn is absent from `trace.selection.turns` while counted in `trace.usage`/`tokensUsed`, which matches the IN-02 documentation for timed-out calls. Verdict: logic sound, no defect found.

### Recommended Tests (non-blocking gap, no public API change)

Add to `StartTierPickerTest` (JVM, no new surface; `PickContext.recordTurn` is already public):

1. A picker that captures its `PickContext` and returns a valid eligible id. The picked tier is a `strategy { }` fake whose `execute` calls the captured `ctx.recordTurn(TurnRecord(null, "m", "end_turn", emptyList(), Usage(0, 0, 0, 7), 1))` while running, and then completes with `Completed`. Assert: `trace.attempts.single { it.strategy == pickedTier }.turns.isEmpty()` (not attributed to the tier in flight), `trace.selection!!.turns` does not contain the late turn, and the final `tokensUsed`/`trace.usage.total` includes the 7 tokens. This pins the exact WR-01 invariant (pre-fix, the turn landed in the picked tier's attempt).
2. The in-pick counterpart already exists (`thePickerTurnIsInTheSelectionNotInAnyAttempt`), so this adds only the late path.

This is a recommended test gap, not a roadmap or PLAN must-have, so it does not change the status.

### Human Verification Required

None for the phase gate. Carried forward by design (not gaps): OI-6, the router prompt wording is MEDIUM confidence and its live check is the Phase 19 Gate-1 router leg on the TESTER (needs a relayed GO with a request/USD ceiling); OI-1 to OI-9 in `16-SURFACE-REVIEW.md` await the orchestrator relay with plan defaults shipped.

### Gaps Summary

No gaps. All five roadmap success criteria and all five requirement IDs are implemented, wired from the public selector to the trace and event surfaces, and backed by passing named tests; the frozen `api.txt` files and `providers`/`keystore` sources are untouched. The only open item is the recommended late-picker-turn test for WR-01.

---

_Verified: 2026-10-06_
_Verifier: Claude (gsd-verifier)_
