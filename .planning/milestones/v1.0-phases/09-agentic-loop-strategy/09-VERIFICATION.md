---
phase: 09-agentic-loop-strategy
verified: 2026-10-01T14:10:00Z
status: passed
score: 4/4 roadmap success criteria verified (plus PLAN must-haves and seam sign-off carries spot-checked against code)
covered_files:
  - ".planning/REQUIREMENTS.md"
  - ".planning/phases/09-agentic-loop-strategy/09-01-PLAN.md"
  - ".planning/phases/09-agentic-loop-strategy/09-01-SUMMARY.md"
  - ".planning/phases/09-agentic-loop-strategy/09-02-PLAN.md"
  - ".planning/phases/09-agentic-loop-strategy/09-02-SUMMARY.md"
  - ".planning/phases/09-agentic-loop-strategy/09-03-PLAN.md"
  - ".planning/phases/09-agentic-loop-strategy/09-03-SUMMARY.md"
  - ".planning/phases/09-agentic-loop-strategy/09-04-PLAN.md"
  - ".planning/phases/09-agentic-loop-strategy/09-04-SUMMARY.md"
  - ".planning/phases/09-agentic-loop-strategy/09-05-PLAN.md"
  - ".planning/phases/09-agentic-loop-strategy/09-05-SUMMARY.md"
  - ".planning/phases/09-agentic-loop-strategy/09-06-PLAN.md"
  - ".planning/phases/09-agentic-loop-strategy/09-06-SUMMARY.md"
  - ".planning/phases/09-agentic-loop-strategy/09-07-PLAN.md"
  - ".planning/phases/09-agentic-loop-strategy/09-07-SUMMARY.md"
  - ".planning/phases/09-agentic-loop-strategy/09-08-PLAN.md"
  - ".planning/phases/09-agentic-loop-strategy/09-08-SUMMARY.md"
  - ".planning/phases/09-agentic-loop-strategy/09-09-PLAN.md"
  - ".planning/phases/09-agentic-loop-strategy/09-09-SUMMARY.md"
  - "core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/commit/CommitCoordinator.kt"
  - "core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/StrategyLimits.kt"
  - "core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/ToolExecutor.kt"
  - "core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/agentic/AgenticDispatch.kt"
  - "core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/agentic/AgenticLoopStrategy.kt"
  - "core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/agentic/AgenticTurn.kt"
  - "core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/agentic/AgenticValidation.kt"
  - "gradle/invariants.gradle.kts"
covered_digest: "v1:sha256:48992df22c5c1ca98ab2f452181a516af32e6a131ae404538278b67344dc012f"
behavior_unverified: 0
overrides_applied: 0
---

# Phase 9: Agentic Loop Strategy Verification Report

**Phase Goal:** A consumer can run SB's bounded agentic loop on any cloud provider over its own tools. The engine gates every mutating step, and nothing committed is ever hidden behind a failure.
**Verified:** 2026-10-01
**Status:** passed
**Re-verification:** No, initial verification

I read the production code (`strategy/agentic/*`, `ToolExecutor`, `StrategyLimits`, `CommitCoordinator`), the loop tests and the scanner rules directly, and did not rely on the SUMMARY files. The current tree includes the nine review fixes (09-REVIEW-FIX.md, WR-01..03, IN-01..06, commits c89169e..44acbbe). At HEAD (9f59da2) I ran:

- `./gradlew check --offline -q`: exit 0.
- `./gradlew :core:test --offline --rerun` on the Agentic*, BatchCancellation, ToolExecutorSeam and NoHardCodedConstants suites: 0 failures. These are real executions, not a cache restore.
- `:core`/`:providers`/`:keystore` `scanBannedConstructs --rerun` and `:core:verifyInvariantScannerControls --rerun`: pass.
- `scripts/review-api-surface.sh --expect-sealed-complete`: API SURFACE OK (classes=181).
- `scripts/verify-repo-hygiene.sh`: HYGIENE OK.
- `git ls-files '*api.txt'` and `git tag`: both empty, as the phase requires.

JUnit XML from the tree at HEAD:

| Suite | Tests | Failures / errors / skipped |
|---|---|---|
| core | 646 | 0 / 0 / 0 |
| providers (4.12.0) | 587 | 0 / 0 / 0 |
| providers (5.2.1) | 587 | 0 / 0 / 0 |
| providers (5.5.0) | 587 | 0 / 0 / 0 |
| keystore | 96 | 0 / 0 / 0 |

`phase-gate.txt` records core 643, taken before the review fixes. The extra 3 are the tests WR-02 (2) and WR-03 (1) added. `AgenticLoopWireTest` ran 15 tests on each of the three OkHttp legs.

## Goal Achievement

### Observable Truths (ROADMAP success criteria)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | An `AgenticLoopStrategy` tier runs over the app's `ToolSpecProvider` and two-phase `ToolExecutor` (`prepare` -> `Finished \| Mutation`) on Anthropic, OpenAI and OpenRouter (JVM, fake provider). Every `Mutation` passes through the gate, and a held step feeds the model `{"applied":false,"status":"held_for_confirmation"}`. | VERIFIED | **Seam and tier:** `ToolExecutor` is a `fun interface` with `suspend fun prepare(call: Extraction, input: CommandInput): ToolStep`. `AgenticLoopStrategy` (builder requires `tooling` and `executor`) fetches the `ToolSpecProvider` snapshot once per command and runs `AgenticRun` with `ToolChoice.Auto()` and parallel calls allowed (`singleToolCall=false`). **Single write path:** `AgenticDispatch.settle` sends every prepared step through `context.session.submit(step)`, which is `CommitCoordinator.submit`, so the gate decides. `guardWrites` removes the only bypass: a `Mutation` returned for a non-`mutating` tool is rewritten to a typed error `Finished` (`READ_TOOL_MUTATION_REJECTED`) before the gate. Since WR-02 a read tool's PREVIEW or ERROR `Finished` is also normalized to a READ, so it cannot write into the executed list. **Held bytes:** `CommitCoordinator.hold` returns the fixed `HELD_FOR_CONFIRMATION` constant, and `dispatchCall` forwards `dispatch.contentForModel`. Tests: `aHeldCallFeedsTheModelTheFixedNoticeAndTheLoopContinues` and `aMutationPassesTheGateAndReachesTheSinkBeforeTheNextTurn` (core), `aReadToolReturningAMutationNeverReachesTheGate`, `aRejectedMutatingCallNeverReachesTheGate`. **Three providers:** `AgenticLoopProviderNeutralityTest` (the core loop is identical for the provider ids), and `AgenticLoopWireTest` drives the real transports. Anthropic: two-turn echo, SB-shaped first user message, identical system and tool bytes every turn, held-notice bytes, parallel `tool_use` answered in one message in order. OpenAI and OpenRouter: tool messages, first user message, identical prefix, held bytes (`chatHeldCallSendsTheFixedNoticeBytes`), `tool_choice` auto every turn, `parallel_tool_calls` rules, and a `finish_reason:stop` with `tool_calls` still dispatching. All 15 pass on the 4.12.0, 5.2.1 and 5.5.0 legs. |
| 2 | SB's guards pass as named tests: whole-turn validation, token-ceiling check before dispatch, final-iteration guard (no tool on the last permitted iteration), sequential dispatch, 2-strike tool-failure abort, unknown tool -> `is_error`, bounds from `TierPolicy`. | VERIFIED | **Guard order** (`AgenticRun.toolTurn`): `ceilingCrossed` -> `lastTurnGuard` -> `validated` -> `dispatchTurn`. `AgenticLoopGuardsTest.theGuardsRunInSbOrder` pins it. **Whole-turn validation:** `isAnswerable` requires at least one call and no id repeated within the turn, so a rejected turn is `Failed(MalformedResponse)` before any call runs. Tests `aToolCallIdRepeatedWithinATurnRejectsTheWholeTurn`, `aRejectedTurnNeverReachesTheResultsMessageConstructor`, `aToolUseStopWithoutCallsIsMalformed`. Per seam sign-off item 5, `aToolCallIdReusedFromAnEarlierTurnIsAccepted` allows cross-turn reuse. **Token ceiling before dispatch:** `AgenticLoopLimitsTest.aToolTurnThatCrossesTheCeilingFailsBeforeTheExecutorOrAnyWrite`, `AgenticLoopGuardsTest.theTokenCeilingStopsTheLoopBeforeDispatchingATool`, `theCeilingIsCheckedBeforeEveryIteration`. The pre-call check (`ceilingReached`, `>=`) and the post-turn check (`ceilingCrossed`, `>`) differ at the equality boundary on purpose (WR-01, documented in the KDoc and pinned by `aToolTurnLandingExactlyOnTheCeilingIsStillDispatched`). **Final-iteration guard:** `lastTurnGuard` returns `BudgetExceeded(ITERATIONS)` when `iteration >= maxIterations`, unless the first call is a terminal tool. Tests `theFinalIterationGuardNeverDispatchesTheLastPermittedTurn`, `theFinalIterationGuardHoldsAtMinimumIterations` and `...AtDefaultIterations`. **Sequential dispatch:** `dispatchCalls` maps the calls one at a time in order, with `ensureActive()` before each (WR-03). Tests `callsAreDispatchedOneAtATimeInCallOrder`, `aSuspendingGateKeepsDispatchSequential`, `aCancelBetweenTwoCallsOfOneTurnStopsBeforeThePrepareOfTheSecond`. **2-strike abort:** `DispatchContext.strike` counts per tool name over the whole command, `STRIKES_TO_ABORT = 2`, and the turn finishes before `ToolFailure` is returned. Tests `repeatedFailureOfTheSameToolAbortsButTheCommittedSiblingIsStillRecorded`, `strikesCountPerToolNameAcrossTurns`, `twoDifferentToolsFailingOnceEachDoNotAbort`, `aHeldCallIsNotAStrike`, `aPrepareFaultAndAnApplyErrorCountAsStrikes`. **Unknown tool -> `is_error`:** `unknownTool` returns `ToolResult(id, UNKNOWN_TOOL_CONTENT, isError=true)` without calling the executor and counts a strike. Tests `anUnknownToolIsAnsweredWithAnErrorAndTheExecutorIsNotCalled`, `anUnknownToolCountsAsAStrike`. **Bounds from `TierPolicy`:** all limits are read from `session.policy` (`maxIterations`, `tokenCeiling`, `maxTokensPerTurn`). `AgenticLoopLimitsTest.defaultsAreTheContractLimits` pins 6 / 60000 / 4096, and `aCustomCeilingIsReadFromTheSessionPolicy` and `customPerTurnLimitIsSentUnchanged` show custom values are honoured. **Signed-off precedence:** `whenTheTokenCeilingAndTheIterationCapTripOnTheSameTurnTokensWins` (Guards) and the same-turn tie inside `aTokenBudgetStopCarriesTheCommitsSoFar` (ExitPaths). Every stop reason and failure leaf is covered by `AgenticLoopStopLeavesTest` (15 tests). |
| 3 | On every exit path (done, budget, cancel, error), the outcome lists the executed actions and commits made so far, and `CommitSink` has already been told about each commit. | VERIFIED | `AgenticLoopExitPathsTest` (11 tests) drives each exit through the real pipeline with a `RecordingCommitSink`. The shared helper `assertActionsThenOneClose` asserts every sink action precedes the one close and the close happens exactly once. `assertSameEffects` asserts the outcome and the `RunTermination` the sink got list the same executed actions, commits and held proposals. Paths: done (`doneReportsEveryActionAndClosesOnce`); iteration budget (`anIterationBudgetStopCarriesCommitsHeldErroredAndPreviews`); token budget (`aTokenBudgetStopCarriesTheCommitsSoFar`); strike abort (`aStrikeAbortCarriesEveryExecutedAction`); provider error (`aProviderFailureAfterACommitStillReportsTheCommit`); malformed turn (`aMalformedTurnAfterACommitStillReportsTheCommit`); terminal call (`aTerminalExitReportsTheCommitsBeforeIt`); cancel at the provider call, the suspended gate and the apply (`aCancelDuringTheProviderCallPropagatesAndTheRunClosesCancelled`, `aCancelWhileTheGateIsSuspendedLeavesNoRecord`, `aCancelDuringApplyRecordsAnAppliedErrorAndPropagates`). A cancelled run propagates `CancellationException`, so there is no outcome object. The sink's `RunTermination.Cancelled` carries `commits` and `executed`, which is the cancel-path report, as in the earlier phases. **O-1 fix:** `CommitCoordinator.applyAll` now calls `currentCoroutineContext().ensureActive()` before each item, so a cancel between batch items leaves the unstarted item unrecorded and unseen by the sink, while the committed item stays recorded. `BatchCancellationTest` (3 tests) covers the gated path, the held path (`commitHeld`) and an uncancelled control. `o1-disposition.txt` records that the test failed before the change (item 1 `applyCount` was 1). Kinds stay distinct (`heldErroredAndPreviewedCallsKeepDistinctKinds`: COMMITTED, HELD, PREVIEW, IS_ERROR), which settles seam item 3 with no new public API. Carries: `AgenticLoopCarryTest` (5 tests). |
| 4 | With both ports landed, library code in `:core`, `:providers` and `:keystore` contains no app-domain types or prompts (`LogFood*`, `log_food`, SB `SYSTEM_PROMPT`, SB tool names, `MutationTier`) and hard-codes no tool count. | VERIFIED | **Rule:** `gradle/invariants.gradle.kts` `rawRules` are matched against the unmodified file text, so strings and comments are covered. The deny-list holds `LogFood*`, `log_food`, `MutationTier*`, `SYSTEM_PROMPT`, `TAG_DISAMBIGUATION*`, `find_tags`, `create_tag`, `edit_list_card`, `edit_text_card` (seam item 10), `SecondBrain` and `CalTracker`. The tool-count rule catches `TOOL_COUNT`-style names, `17|18 tools` and `<tools-ish>.size|count()` against 17 or 18 with any comparison operator, in either order or as two arguments (IN-05). The rule is applied by `scanBannedConstructs` in `:core`, `:providers` and `:keystore`. **Clean tree:** a grep of `core/src/main`, `providers/src/main` and `keystore/src/main` for `LogFood|log_food|SYSTEM_PROMPT|MutationTier|edit_text_card|TextCard|record_item` finds nothing. The three keystore KDoc lines that named the adopter apps are neutralized. **I planted** `"log_food"` and `tools.size == 18` in a `:core` main file. `:core:scanBannedConstructs` failed with both rules named (`[app-domain name in source]`, `[hard-coded tool count]`). I removed the plant and the scan is green again. **Controls:** `config/negative-controls/` holds `app-domain`, `tool-count`, `tool-count-operators`, `tool-count-number-first`, `tool-count-two-args` and a `clean` file with near-misses. `:core:verifyInvariantScannerControls` passes, and `phase-gate.txt` records `verify-negative-controls.sh` at 0 failures. I did not re-run that script myself, because it forces the OkHttp guard tests red. **No hard-coded count at runtime:** `AgenticLoopDispatchTest.theLoopRunsWithOneTwoOrTwentyFiveTools` and `NoHardCodedConstantsTest` (12 tests) both pass. |

**Score:** 4/4 roadmap truths verified. 0 present-but-behavior-unverified. The behavior-dependent truths (cancellation, ordering, gating, strike, held-notice, exit reporting) each have a passing behavioral test that I re-ran.

### PLAN must-haves and seam sign-off carries

`evidence/seam-signoff.txt` is `APPROVE-WITH-CHANGES`, `PLANS-AMENDED: yes`. Each carry was checked against the current code, not only the evidence.

| Carry | Requirement | Status | Evidence |
|---|---|---|---|
| 5 (-> 09-05) | Duplicate ids rejected within a turn only; cross-turn reuse allowed | VERIFIED | `isAnswerable` checks `calls.map { it.id }.toSet().size == calls.size` for the one turn only. Tests `aToolCallIdReusedFromAnEarlierTurnIsAccepted` and `aToolCallIdRepeatedWithinATurnRejectsTheWholeTurn`. |
| 3 (-> 09-06) | Held, errored and previewed stay distinguishable in `ExecutedAction` | VERIFIED | `ActionKind.HELD`, `PREVIEW` and `IS_ERROR` are preserved end to end. `heldErroredAndPreviewedCallsKeepDistinctKinds` asserts three distinct kinds, all `applied == false` and `mutating == false` for the preview and the error. No new public member was needed. |
| precedence (-> 09-04, 09-06) | Token ceiling and iteration cap on the same turn report `BudgetExceeded(TOKENS)`; KDoc'd; tested | VERIFIED | `toolTurn` evaluates `ceilingCrossed` before `lastTurnGuard`. The order is stated in the `AgenticLoopStrategy` KDoc. Tests: `whenTheTokenCeilingAndTheIterationCapTripOnTheSameTurnTokensWins` and the tie case in `aTokenBudgetStopCarriesTheCommitsSoFar`. |
| 10 (-> 09-08) | Add `edit_text_card` to the CLN-02 deny-list | VERIFIED | Present in `rawRules`. |
| O-1 (09-01) | Per-item cancellation in `CommitCoordinator.applyAll` on the gated and held paths | VERIFIED | `applyAll` calls `ensureActive()` per item. `BatchCancellationTest` passes. |
| Terminal tool / A19 (09-04) | The first terminal call ends the run, calls after it are dropped with `EXTRA_TOOL_CALLS_DROPPED` and the completion is partial, a strike beats a terminal call, and a terminal-first final turn completes instead of failing the budget | VERIFIED | `dispatchCalls` / `dispatchTurn` / `lastTurnGuard`. `AgenticLoopTerminalTest` has 8 tests, including `aStrikeAbortBeatsATerminalCallInTheSameTurn` and `aTerminalTurnOnTheFinalIterationCompletesInsteadOfBudgetExceeded`. |
| Phase 8 carries (09-06) | `ToolChoice.Auto`, replay untouched, history append-only | VERIFIED | `request()` uses `ToolChoice.Auto()`. `history` only grows and stores `response.message` as received. `AgenticLoopCarryTest` (5 tests), `theHistoryIsAppendOnlyAcrossTurns`, and the wire test `chatToolChoiceIsAutoOnEveryTurn`. |
| Review fixes WR-02, WR-03 (post-plan) | A read tool's PREVIEW or ERROR never writes into the executed list; cancellation is observed before each call | VERIFIED | `guardWrites` / `asRead` and `dispatchCall`'s `ensureActive()` are in the tree. The tests `aReadToolReportingAPreviewLeavesNoActionAndTheModelStillGetsItsContent`, `aReadToolReportingAnErrorLeavesNoActionStaysAnErrorAndStillStrikes` and `aCancelBetweenTwoCallsOfOneTurnStopsBeforeThePrepareOfTheSecond` pass. |

### Required Artifacts

| Artifact | Expected | Status | Details |
|---|---|---|---|
| `core/.../strategy/agentic/AgenticLoopStrategy.kt` | Tier, builder, run loop | VERIFIED | Substantive (224 lines), used by every loop test and the wire tests. |
| `core/.../strategy/agentic/AgenticDispatch.kt` | Sequential dispatch, gate routing, strikes, terminal handling | VERIFIED | 135 lines. `settle` -> `session.submit` is the only route to a write. |
| `core/.../strategy/agentic/AgenticTurn.kt` | Turn decision | VERIFIED | Refusal, max tokens, pause turn and context window are final even with tool calls. |
| `core/.../strategy/agentic/AgenticValidation.kt` | Whole-turn validation | VERIFIED | Within-turn id uniqueness plus a non-empty list. |
| `core/.../strategy/ToolExecutor.kt` | Two-phase seam | VERIFIED | Public `fun interface`, KDoc'd. |
| `core/.../strategy/StrategyLimits.kt` | Shared limit and stop helpers | VERIFIED | Used by both SingleShot and the loop. |
| `core/.../commit/CommitCoordinator.kt` | O-1 per-item cancellation | VERIFIED | `applyAll` calls `ensureActive()` per item. |
| `core/src/testFixtures` `ScriptedToolExecutor` | Fake executor | VERIFIED | Used by the loop tests. |
| `gradle/invariants.gradle.kts` + `config/negative-controls/*` | CLN-02 rules and controls | VERIFIED | Proven red by my plant and by `verifyInvariantScannerControls`. |
| 11 `AgenticLoop*Test` / `BatchCancellationTest` / `ToolExecutorSeamTest` classes in `:core`, `AgenticLoopWireTest` in `:providers` | Named tests for the success criteria | VERIFIED | All green (see counts above). |

### Key Link Verification

| From | To | Via | Status | Details |
|---|---|---|---|---|
| `AgenticRun.dispatchTurn` | `dispatchCalls` | direct call | WIRED | Results go back as `ToolResultsMessage(turn.results)`. |
| `AgenticDispatch.settle` | `CommitCoordinator.submit` | `session.submit(step)` | WIRED | The only write path. |
| `CommitCoordinator.applyAll` | cancellation | `ensureActive()` per item | WIRED | O-1. |
| `AgenticRun` | `TierPolicy` | `session.policy.{maxIterations,tokenCeiling,maxTokensPerTurn}` | WIRED | No literal limits in the loop. |
| `AgenticRun` | `ModelRequest` / `ToolChoice.Auto` | `request()` | WIRED | The same system and tools every turn (prefix identical in the wire test). |
| `AgenticLoopStrategy` | real transports | `AgenticLoopWireTest` | WIRED | Anthropic, OpenAI and OpenRouter on three OkHttp legs. |
| `scanBannedConstructs` | `rawRules` | `invariants.gradle.kts` in each published module | WIRED | The planted violation fails the build. |

### Data-Flow Trace (Level 4)

Not a UI phase. The data flow is model response -> `toolCalls` -> executor `prepare` -> `ToolStep` -> `session.submit` -> `DispatchResult.contentForModel` -> `ToolResultsMessage` -> next request. `AgenticLoopWireTest` follows it on the wire, and the content the model gets back is the coordinator's real content (applied text, the error content or the held notice), never a static fallback.

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|---|---|---|---|
| Whole build, detekt zero, scanners, 3 OkHttp legs | `./gradlew check --offline -q` | exit 0 | PASS |
| Loop, O-1, seam and constants suites re-executed | `./gradlew :core:test --offline --rerun --tests '*AgenticLoop*' --tests '*BatchCancellation*' --tests '*ToolExecutorSeam*' --tests '*NoHardCodedConstants*'` | 13 classes, 0 failures | PASS |
| Wire tests on each OkHttp leg | JUnit XML parse | 15 / 15 / 15 tests, 0 failures | PASS |
| Scanner rejects a planted violation | plant `"log_food"` and `tools.size == 18` in core main, `:core:scanBannedConstructs` | fails naming both rules; green after removal | PASS |
| Scanner controls | `:core:verifyInvariantScannerControls --rerun` | pass | PASS |
| API surface sealed and complete | `scripts/review-api-surface.sh --expect-sealed-complete` | API SURFACE OK, classes=181 | PASS |
| Repo hygiene | `scripts/verify-repo-hygiene.sh` | HYGIENE OK | PASS |
| No API snapshot or tag yet | `git ls-files '*api.txt'`, `git tag` | empty / empty | PASS |

### Probe Execution

Step 7c: SKIPPED. The phase declares no `probe-*.sh` scripts.

### Requirements Coverage

| Requirement | Source Plan(s) | Description | Status | Evidence |
|---|---|---|---|---|
| LOOP-01 | 09-02, 09-03, 09-04, 09-06, 09-07, 09-09 | `AgenticLoopStrategy` over `ToolSpecProvider` + two-phase `ToolExecutor` on any cloud provider; mutations enforced through the gate | SATISFIED | SC1 |
| LOOP-02 | 09-01, 09-03, 09-04, 09-05, 09-09 | SB's guards as named tests; bounds from `TierPolicy` | SATISFIED | SC2 |
| LOOP-03 | 09-01, 09-05, 09-06, 09-09 | Every exit path reports executed actions and commits; nothing committed is hidden | SATISFIED | SC3 |
| CLN-02 | 09-03, 09-08, 09-09 | No app-domain types or prompts and no hard-coded tool count in library code | SATISFIED | SC4 |

All four IDs appear in PLAN frontmatter (`requirements:` of 09-01 to 09-09) and in ROADMAP Phase 9. `REQUIREMENTS.md` maps exactly LOOP-01, 02, 03 and CLN-02 to Phase 9. No orphaned requirement.

**Bookkeeping, not a gap:** `.planning/REQUIREMENTS.md` still shows these four as `- [ ]` (lines 91-93, 98) and `Pending` in the traceability table (lines 207-209, 211). The orchestrator should tick them when it closes the phase.

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|---|---|---|---|---|
| `strategy/` (the agentic, commit, telemetry and testFixtures files this phase touched) | - | `TBD`/`FIXME`/`XXX` grep | none | No debt markers. |
| `AgenticLoopStrategy.kt` | 159 | `EXHAUSTED_CODE` fallback after the loop | info | Defensive and commented as unreachable, because the policy requires at least 2 iterations and the last one always returns (IN-02). Not a stub. |

The code review (`09-REVIEW.md`: 0 critical, 3 warnings, 6 info) is fully resolved in `09-REVIEW-FIX.md` (9 of 9). I confirmed the fixes are in the tree and that `check` is green after them. WR-01 and IN-04 are documentation-only by instruction: the behavior is the signed-off one and is pinned by tests.

### Notes (observations that do not change the verdict)

- **Cancel path has no outcome object.** SC3 reads "the outcome lists the executed actions" for cancel. A cancelled `execute` throws `CancellationException`, so the cancel-path report is the sink's `RunTermination.Cancelled` (`commits`, `executed`). This is the pipeline's established contract and is tested at three suspension points.
- **The deny-list is a subset of SB tool names.** It holds only names already tracked in planning docs (research Q7). `create_text_card` appears in the research list but not in the rule. The tree is clean of it today (grep over all three `src/main` roots). It is optional hardening if the orchestrator wants it.
- **The tool-count rule is a regex, not a type checker.** The rule's own comment says a count held under another name still gets through. The runtime test `theLoopRunsWithOneTwoOrTwentyFiveTools` covers behavior independently.
- **Carried to Phase 10 (README, VER-03):** the Anthropic agentic Gate-1 on the TESTER (turn-2 `cache_read_input_tokens > 0`) and the multi-turn Chat/OpenRouter device leg. The project config defers both to Phase 10, so they are not gaps here. README items: OpenAI strict-eligible tool sets get one call per turn, kinds `HELD`/`IS_ERROR`/`PREVIEW` tell non-applied calls apart, tool results are data, the within-turn-only duplicate-id rule, and the TOKENS precedence.

## Human Verification Required

None. This is a JVM-only library phase: every success criterion is proven by automated tests that I re-ran, and device-side checks are deferred to Phase 10 by the project config.

## Gaps Summary

No gaps. All four roadmap success criteria are met by code I read and tests I re-ran. All four requirement IDs (LOOP-01, LOOP-02, LOOP-03, CLN-02) are accounted for. Every carry in the APPROVE-WITH-CHANGES seam sign-off is implemented and tested, and the post-plan review fixes are in the tree and green.

---

_Verified: 2026-10-01_
_Verifier: Claude (gsd-verifier)_
