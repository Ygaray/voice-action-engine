---
phase: 09-agentic-loop-strategy
reviewed: 2026-10-01T00:00:00Z
depth: standard
files_reviewed: 36
files_reviewed_list:
  - config/detekt/detekt.yml
  - config/negative-controls/app-domain.kt.txt
  - config/negative-controls/clean.kt.txt
  - config/negative-controls/tool-count.kt.txt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/commit/CommitCoordinator.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/agentic/AgenticDispatch.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/agentic/AgenticLoopStrategy.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/agentic/AgenticTurn.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/agentic/AgenticValidation.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/singleshot/SingleShotOutcomes.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/singleshot/SingleShotStrategy.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/StrategyLimits.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/ToolExecutor.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/telemetry/TraceCode.kt
  - core/src/testFixtures/kotlin/io/github/ygaray/voiceactionengine/core/testing/ScriptedToolExecutor.kt
  - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/AgenticLoopCarryTest.kt
  - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/AgenticLoopDispatchTest.kt
  - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/AgenticLoopExitPathsTest.kt
  - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/AgenticLoopGateTest.kt
  - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/AgenticLoopGuardsTest.kt
  - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/AgenticLoopLimitsTest.kt
  - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/AgenticLoopProviderNeutralityTest.kt
  - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/AgenticLoopStopLeavesTest.kt
  - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/AgenticLoopTerminalTest.kt
  - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/AgenticLoopTestSupport.kt
  - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/AgenticLoopUserTurnTest.kt
  - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/BatchCancellationTest.kt
  - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/RedactionCanaryTest.kt
  - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/ToolExecutorSeamTest.kt
  - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/TraceTest.kt
  - gradle/invariants.gradle.kts
  - keystore/src/main/kotlin/io/github/ygaray/voiceactionengine/keystore/AesGcm.kt
  - keystore/src/main/kotlin/io/github/ygaray/voiceactionengine/keystore/AndroidKeyStoreKeyAccess.kt
  - keystore/src/main/kotlin/io/github/ygaray/voiceactionengine/keystore/ApiKeyStore.kt
  - providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/AgenticLoopWireTest.kt
  - scripts/verify-negative-controls.sh
findings:
  critical: 0
  warning: 3
  info: 6
  total: 9
status: issues_found
---

# Phase 9: Code Review Report

**Depth:** standard

## Summary

The loop matches the 09-CONTEXT.md decisions: strikes per tool name with the rest of the turn dispatched before Failed(ToolFailure); a strike abort beats a terminal call in the same turn; the ceiling check runs before the last-turn guard (TOKENS over ITERATIONS); duplicate call ids are rejected only within a turn; an unknown tool never reaches the executor; a read tool returning a Mutation is rewritten to an error before the gate; held, errored and previewed calls are distinguishable by ledger kind and the fixed held bytes. No loop path catches CancellationException. The O-1 fix in CommitCoordinator.applyAll is correct (ensureActive before each item, applied items stay recorded, sink delivery under NonCancellable). No secret leaks: TerminalCall, ToolResult, ToolStep and DispatchResult print counts only, executor exceptions become a fixed notice, RedactionCanaryTest covers the agentic path.

No Critical issues. The Warnings are boundary and consistency gaps, not wrong behavior on the decided paths.

## Warnings

### WR-01: A tool turn landing exactly on the token ceiling still dispatches (DISPOSITION: intentional, not changed)

**File:** `core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/StrategyLimits.kt:24-29`, `core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/agentic/AgenticLoopStrategy.kt:178-196`
**Issue:** The pre-call check `ceilingReached` uses `>=`; the post-turn check `ceilingCrossed` uses `>`. A tool turn ending with `tokensUsed == tokenCeiling` dispatches its calls, then the next iteration fails BudgetExceeded(TOKENS) without the model seeing the results.
**Disposition:** This is the relayed and signed-off behavior (seam sign-off item 9 / Q10, SB parity: "refuse before every model call when tokensUsed >= tokenCeiling; after a tool-use response, fail before any dispatch when tokensUsed > tokenCeiling"). Changing it would contradict a frozen sign-off item. Only the KDoc should state the equality boundary explicitly. Fixer: do NOT change the predicates or tests; only add/adjust KDoc on `AgenticLoopStrategy` stating the boundary (equality after a tool-use response still dispatches; the next model call is then refused).

### WR-02: A non-mutating tool can still write entries into the executed list through Finished(PREVIEW) or Finished(ERROR)

**File:** `core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/agentic/AgenticDispatch.kt:101-105`
**Issue:** `guardWrites` only rewrites a `ToolStep.Mutation` returned by a read tool. A read tool (`spec.mutating == false`) can still return `Finished(kind = PREVIEW)` or `Finished(kind = ERROR)`; `CommitCoordinator.finished` then records an `ActionKind.PREVIEW` or `IS_ERROR` action and delivers it to the sink. This breaks D-05 (executed list is mutating calls only; reads appear as tool names in the trace) and D-03.
**Fix:** Coerce the step for non-mutating specs:
```kotlin
private suspend fun guardWrites(context: DispatchContext, spec: ToolSpec, step: ToolStep): ToolStep = when {
    spec.mutating -> step
    step is ToolStep.Mutation -> { context.session.recordCode(TraceCode.READ_TOOL_MUTATION_REJECTED); readError() }
    step is ToolStep.Finished && step.kind != FinishedKind.READ ->
        ToolStep.Finished(spec.name, FinishedKind.READ, step.result)
    else -> step
}
```
Add a test where a read tool returns PREVIEW or ERROR and assert `executed` is empty. (An ERROR result from a read tool must still reach the model as is_error and still count as a strike; verify the coerced step preserves the error flag semantics already used for read-tool errors, or keep the Finished(ERROR) pass-through for the strike path but not for the executed list. Preserve the existing strike tests.)

### WR-03: Cancellation is observed only at submit, so prepare still runs for later calls in a cancelled turn

**File:** `core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/agentic/AgenticDispatch.kt:79-84, 109-113`
**Issue:** `dispatchCall` calls the app's `executor.prepare(...)` before `session.submit`, whose `admitCaller()` throws on cancellation. If the caller is cancelled between calls of one turn, the next call's `prepare` still runs. No ledger or sink damage results, but the ToolExecutor KDoc says "A cancellation always propagates".
**Fix:** Add `currentCoroutineContext().ensureActive()` at the top of `dispatchCall`, and add a test with a cancel between two calls of one turn asserting `prepare` is not called for the second.

## Info

### IN-01: `lastTurnGuard` calls `.first()` before whole-turn validation
**File:** `core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/agentic/AgenticLoopStrategy.kt:192-196`
**Issue:** `toolCalls.first()` is safe only via a `decideTurn` invariant in another file; the check order is deliberate and tested.
**Fix:** Use `firstOrNull()?.let { context.specOf(it.name) }` or add a one-line comment naming the invariant. Do not reorder.

### IN-02: `AgenticRun.run()` ends with an unreachable fallback; history is appended when struck out
**File:** `core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/agentic/AgenticLoopStrategy.kt:31, 147-153`
**Issue:** `TierPolicy` requires `maxIterations >= 2` and the last iteration always returns, so `EXHAUSTED_CODE` is unreachable; `dispatchTurn` appends history even when `struckOut`.
**Fix:** Comment the fallback as a defensive assertion (or `error("unreachable")`), and skip the history append when `struckOut`. Keep behavior otherwise identical.

### IN-03: Stale and incomplete public KDoc
**File:** `core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/telemetry/TraceCode.kt:101-105`, `core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/ToolExecutor.kt:20-21`
**Issue:** `EXTRA_TOOL_CALLS_DROPPED` says "only the first was used"; the agentic loop also records it for calls after a terminal call. The ToolExecutor KDoc omits that a terminal tool is never executed.
**Fix:** Reword both (no planning ids, no model/app names): e.g. "the model sent more calls than the strategy acts on (a single-shot tier's first call, or the calls after a terminal call)".

### IN-04: An OTHER stop reason with tool calls dispatches writes
**File:** `core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/agentic/AgenticTurn.kt:13-33`
**Issue:** Follows D-11 (tool-turn-ness comes from the presence of tool calls), but the KDoc promise that a refused/truncated/paused answer can never become a write holds only for the four mapped reasons.
**Fix:** No behavior change. Narrow the KDoc claim to the mapped stop reasons.

### IN-05: The tool-count scanner rule is easy to bypass
**File:** `gradle/invariants.gradle.kts:45-51`
**Issue:** Only matches `<tools-ish>.size|count() == 17|18`; misses `!=`, `>=`, `assertEquals(18, tools.size)`. Tests are out of scope of the raw rules (deliberate).
**Fix:** Broaden the comparison operators and allow the number-first form; note in the comment that src/test is deliberately out of scope. Keep the negative controls and clean.kt.txt near-misses green (`:core:verifyInvariantScannerControls`, `scripts/verify-negative-controls.sh`).

### IN-06: Test fragility and small coupling
**File:** `AgenticLoopStopLeavesTest.kt:27-28, 257-258`, `AgenticLoopProviderNeutralityTest.kt:24, 106`, `TraceTest.kt:367-368`
**Issue:** `LEAF_MAX_TOKENS = 4` / `LEAF_REFUSAL = 5` are positional indexes; `AGENTIC_SOURCES` is working-directory relative; `THIRTY_TWO` is a hand-maintained total.
**Fix:** Look leaves up by class instead of index; resolve the source root robustly; enumerate TraceCode.Companion reflectively as the neutrality test does for ProviderId.

---

_Reviewed: 2026-10-01_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
