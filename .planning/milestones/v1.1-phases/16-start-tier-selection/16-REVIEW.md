---
phase: 16-start-tier-selection
reviewed: 2026-10-06T00:00:00Z
depth: standard
files_reviewed: 32
files_reviewed_list:
  - API.md
  - INTEGRATION.md
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
  - core/src/testFixtures/kotlin/io/github/ygaray/voiceactionengine/core/testing/ScriptedPicker.kt
  - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/PipelineBuilderTest.kt
  - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/PlanParseTest.kt
  - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/RouterRequestTest.kt
  - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/RouterSelectorTest.kt
  - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/StartTierFallbackTest.kt
  - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/StartTierPickerTest.kt
  - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/StartTierPolicyTest.kt
  - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/StartTierPrePassTest.kt
  - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/StartTierRedactionTest.kt
  - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/StartTierTestSupport.kt
  - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/TierPolicyTest.kt
  - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/TierSelectorTest.kt
  - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/TierWalkLinearCharacterizationTest.kt
  - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/TraceTest.kt
findings:
  critical: 0
  warning: 3
  info: 4
  total: 7
status: resolved
---

# Phase 16: Code Review Report

**Reviewed:** 2026-10-06
**Depth:** standard
**Files Reviewed:** 32
**Status:** issues_found

## Summary

The start-tier selection work is largely sound. I found no blockers.

- **Fallback paths** (null, unknown id, throw, timeout, foreign cancellation, leaked inner timeout) are all routed through `guarded` correctly, and real cancellation still propagates.
- **Policy rule:** `tierPermitted` is the single static rule for both tiers and the picker, and the bind-time gate backs it up.
- **Redaction:** `toString()` on `PickingSpec`, `RouterPicker`, `Router`, `Custom`, `StartTierSelection`, `PickContext` and `CommandTrace` prints ids and counts only. `StartTierRedactionTest` has a proper positive control.
- **Token accounting:** selection turns go to the `SelectionBook` only, so there is no double count in `trace.usage`.
- **Router decoding:** `decodePick` matches the model's answer against `eligible` by value and never builds an id from model text.
- **API surface:** the additions are additive. `core/api.txt` is not touched in this diff.

The defects are:

- one state-attribution hole that violates the documented "a context is valid only while the pick runs" contract,
- one documentation claim contradicted by `Fixed`,
- one silent bypass of free mid-ladder tiers.

The rest is quality and measurement-signal issues.

## Warnings

### WR-01: A picker turn recorded after the pick closed is attributed to the wrong tier

**File:** `core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/telemetry/RunRecorder.kt:79` (with `SelectionBook.kt:40-44`)
**Issue:** `turnRecorded` does `if (!selectionBook.take(strategy, turn)) book.turns.add(turn)`. `take` returns false once the selection is closed, even when `strategy` is the picker's id. A late turn from the picker's id therefore falls into the tier book. It is then attached to whichever tier is in flight, or is silently dropped by `TierBook.start`'s `turns.clear()`. Its tokens still go into `tokenTotal`, and a `ProviderCall` event is still emitted under the picker id.

This is reachable. `StartTierPicker` and `PickContext` KDoc say a context is valid only while the pick runs, but nothing enforces it:

- `RunPickContext.recordTurn` and the `BoundModel` returned by `model()` stay callable after a timeout or fallback.
- A picker that launches a background coroutine, or swallows `CancellationException` and keeps going, can record after `withTimeoutOrNull` has fired and `SelectionBook.finished` has closed the selection.

The result is a first-tier attempt whose `turns` and `usage` include a turn that tier never made, which breaks the "selection kept apart from attempts" guarantee.

**Fix:** Make `take` claim every turn that carries the picker's id, and drop late ones from the attempts. Keep counting the tokens, or deliberately refuse them in `RunPickContext`.
```kotlin
fun take(strategy: StrategyId, turn: TurnRecord): Boolean = synchronized(lock) {
    if (picker == null || strategy != picker) return@synchronized false
    if (closed == null) turns.add(turn)   // a late turn is counted in tokenTotal but never lands on a tier
    true
}
```
Alternatively, give `RunPickContext` a `closed` flag set in `ask`'s `finally`, and have `recordTurn` and `model()` refuse after it.

### WR-02: Docs say the zero-call head runs "whatever the selector", but `TierSelector.Fixed` skips it

**File:** `core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/pipeline/TierSelector.kt:12-13`; `INTEGRATION.md:96-97`
**Issue:** Both the `TierSelector` class KDoc and INTEGRATION say "The tiers that need no model at the head of the ladder always run first, whatever the selector". That is false for `Fixed`. `TierWalk.run` does `climb(ladder.tiers.drop(start), input)`, so `Fixed(llmTier)` never runs the grammar head. `Fixed`'s own KDoc ("the tiers below it never run") says the opposite.

An integrator who reads "always" and picks `Fixed` for a latency or cost reason will lose the free grammar match with no warning. The characterization test pins the current `Fixed` behavior, so the code is probably right and the docs are wrong.

**Fix:** Scope the sentence to the picking selectors:
```
/** ... For [Custom] and [Router] the tiers that need no model at the head of the ladder always run first;
 *  [Fixed] starts exactly at its tier and [Linear] runs the ladder in order. */
```
Apply the same wording in INTEGRATION.md, "Zero-call head first".

### WR-03: A zero-call tier that is not at the head is silently skipped when the pick lands past it, and the trace does not say so

**File:** `core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/pipeline/TierWalk.kt:54-60`; `StartTierPicking.kt:75,101`
**Issue:** `head` is `takeWhile { providers.isEmpty() }`. For a ladder `[grammar, single, localMatcher(no provider), agentic]`, a pick of `agentic` yields `rest.drop(2)`. The free `localMatcher` never runs, and nothing is recorded.

`tiersBypassed` counts model tiers only and `StartTierSelection` says zero-call tiers are never counted, so a trace consumer cannot see the skip. A Linear walk would have run that tier for free before paying for `agentic`. `StartTierFallbackTest.Ladder` builds exactly this shape (`local` mid-ladder) but only asserts the fallback path, not the picked-past-it path.

**Fix:** Either of these:
- Treat every zero-call tier before the picked tier as part of the head: run `rest.take(start).filter { zero-call }` through `climb` before the picked tier. This preserves the "free first" intent.
- Document the behavior ("a zero-call tier after a model tier is bypassed with the tiers the pick skips") and add a test that pins it.

## Info

### IN-01: `router_fallback` is recorded when no picker was ever called, conflating two signals

**File:** `core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/pipeline/StartTierPicking.kt:75-77`
**Issue:** `startIn` records `ROUTER_FALLBACK` when `llm.isEmpty()` or the picker is policy-forbidden. This runs even when the head merely returned NoMatch with nothing left to pick, as `noModelTierLeft` in `StartTierPolicyTest` shows. A dashboard that counts `router_fallback` as picker failure rate gets false positives from commands where the policy left no model tier, and `trace.selection` is null in those cases. The docs admit the overload, but it makes the code a weak signal.
**Fix:** Keep it as is if the contract is frozen. If not, record a distinct code (for example `start_tier_not_asked`) for the no-call cases and keep `router_fallback` for a picker that was actually asked.

### IN-02: A timed-out picker call is billed but never reaches `tokensUsed` or the trace

**File:** `core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/pipeline/StartTierPicking.kt:82-90`; `TierPolicy.kt` (`DEFAULT_PICKER_TIMEOUT_MILLIS = 2_000L`)
**Issue:** On timeout the in-flight `complete` is cancelled and no `TurnRecord` is produced, yet the provider may still bill the request. With a 2 s default, a cloud Router on a cold connection will plausibly time out often. Spend is then invisible to `trace.usage` and the token ceiling that later tiers read, and the only evidence is a `router_fallback` with zero turns.
**Fix:** Note this in the `pickerTimeoutMillis` KDoc and INTEGRATION, or consider a larger default for the Router. No code change is strictly required.

### IN-03: The step-id length cap is an unrelated behavior change on the frozen plan-parse surface

**File:** `core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/plan/PlanBinding.kt:16-32`
**Issue:** `isStepId` now rejects ids longer than 64 characters (commit 50247d4, RT-01). A plan with such an id was valid in v1.0.0 and is now `BAD_ID_CODE` and rejected. The cap is covered by `PlanParseTest`, but it is not mentioned in API.md or INTEGRATION and is outside the start-tier theme. Because `REFERENCE` still allows ids of any length, a `$<65+ chars>.key` reference parses and only fails later as unresolved.
**Fix:** Record it in the plan tier's documentation (a planner that emits long ids is rejected) and in the changelog or ledger as a deliberate tightening. Optionally bound the `REFERENCE` id fragment to `{0,63}` so both sides agree.

### IN-04: Minor duplication and readability issues

**File:** `core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/pipeline/TierSelector.kt:30,63,110`; `core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/pipeline/RouterPicker.kt:90-99`
**Issue:**
- `startIndex = if (eligible.isEmpty()) null else 0` is copied in `Linear`, `Custom` and `Router`.
- `routerRequest` passes unnamed positional literals (`CacheDirective(false), true, ReasoningMode.OFF`). The `true` is `singleToolCall`, which is easy to misread, and a future constructor reorder would silently change the request.
**Fix:** Hoist the shared lambda into a private helper such as `firstIfAny(eligible)`. Name the arguments in `routerRequest` (`singleToolCall = true`, `reasoning = ReasoningMode.OFF`).

---

_Reviewed: 2026-10-06_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
