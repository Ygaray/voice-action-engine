---
status: complete
result: all_pass
gate: 1
phase: 07-singleshot-strategy
source: [ROADMAP Phase 7 success criteria 1-3]
device: none (headless JVM; pure :core + :providers library phase, no adb, no device, no emulator, no live provider call)
apk: n/a (no artifact built or installed; HEAD bbf91a4f1e0fb2af1dfc8c63aaaa8b1f41bb860b)
run: 2026-10-01T16:24:00Z @ bbf91a4f1e0fb2af1dfc8c63aaaa8b1f41bb860b
---

<!--
BACKFILLED RECORD. Written on 2026-10-01 by the milestone master (a diagnose-only Gate-1 tester agent) after the
orchestrator flagged that Phase 7 had no SELF-UAT.md and no .planning/uat-pending/ fragment, unlike Phases 1-6.
Why the gap: the Phase 7 execute stage ruled Gate-1 N/A (`phase.has-uat-criteria` = false: JVM-only phase, no UI or
device-verifiable surface) and therefore wrote neither file. This log applies the same headless-JVM handling used for
Phases 1-6 (see 03-SELF-UAT.md). No source, test, config or build file was changed to produce it.
-->

# Self-UAT Log: Phase 7 (SingleShot Strategy), one log for the phase (backfilled)

**Target:** headless (Gradle JVM test harness over scripted fake providers plus the real Anthropic / OpenAI / OpenRouter transports against a local `MockWebServer`). No adb, no device, no emulator, no live provider, no key read.
**Build identity:** HEAD `bbf91a4f1e0fb2af1dfc8c63aaaa8b1f41bb860b`. `git status --short core providers config scripts` is empty (no uncommitted change to library, config or scripts).
**Pre-flight:** JDK 17, Gradle wrapper 9.4.1. Stale `core/build/test-results/test` and `providers/build/test-results` removed first, then `./gradlew :core:test :providers:test --offline --rerun-tasks --console=plain` -> `BUILD SUCCESSFUL in 1m 8s`, exit 0, `11 actionable tasks: 11 executed`. Every count below was produced by this run, not by a cache or by the older `evidence/` files.
**Unit suite (JUnit XML, parsed with a script):**

| Result dir | Suites | Tests | Failures | Errors | Skipped |
|---|---|---|---|---|---|
| `core/build/test-results/test` | 55 | 535 | 0 | 0 | 0 |
| `providers/build/test-results/test` (OkHttp 4.12.0 floor) | 48 | 572 | 0 | 0 | 0 |

**Wider gate:** `./gradlew check --offline` (no `--rerun-tasks`; 179 actionable tasks, 32 executed, 2 from cache, 145 up to date) -> `BUILD SUCCESSFUL`, exit 0. The two OkHttp matrix legs had their result dirs freshly produced by this `check` run: `testOkhttp521` 48 suites / 572 tests / 0 failures / 0 errors / 0 skipped, `testOkhttp550` 48 / 572 / 0 / 0 / 0. `keystore` `testDebugUnitTest` 15 / 96 / 0 / 0 / 0 (up to date, not re-executed). `scripts/review-api-surface.sh --expect-sealed-complete` -> `API SURFACE OK sealed=AssistantPart,CommandOutcome,GateDecision,Message,RunTermination,StrategyOutcome,ToolStep classes=177`. `scripts/verify-repo-hygiene.sh` -> `HYGIENE OK`. No `*Live*` class runs in `test` or either matrix leg (live tasks are opt-in and outside `check`).
**Seed/fixture integrity:** all fixtures are in-tree test fixtures (`FakeAiProvider`, scripted resolvers/gates/sinks in `SingleShotTestSupport.kt` / `SingleShotFixtures.kt`, `MockWebServer`); nothing external, nothing to restore.
**Prior verdicts audited, not relied on:** `07-VERIFICATION.md` (status passed, 3/3), `evidence/phase-gate.txt` (PHASE GATE: PASS at 35e8e92, core 526 tests then), `evidence/shot02-partial-drop.txt`, `evidence/seam-signoff.txt`. Coverage below is re-derived from the ROADMAP text by reading the test names and the load-bearing assertions in the sources. The test count has grown since the gate file (core 526 -> 535 after the partial-drop follow-up and review fixes), so the gate file's numbers are stale; this log supersedes them as the headless Gate-1 record for HEAD.

## Criteria

### 1. SC1: a `SingleShotStrategy` tier makes one forced-tool call using the app's `ToolSpecProvider`; the app's `OutcomeResolver` turns the result into a (possibly batch) proposal, committed only through the gate -> `CommitSink` path
result: passed
- **Rung:** 1 (unit tests plus a local-server wire test; no visual claim exists)
- **Target:** headless JVM harness
- **Expected:** exactly one provider call per command carrying the snapshot's tools and a forced single-tool choice; the resolver's steps become one proposal; nothing is applied except through the gate; the sink sees every action; held changes are not applied.
- **Arranged (seeded):** `FakeAiProvider` scripted answers, a scripted `OutcomeResolver`, a recording gate and a recording `CommitSink`, `MockWebServer` for the wire leg. No UI path exists, so nothing was driven by UI.
- **Did (drove):** forced re-run of both test tasks; read the test names and assertions of the classes below.
- **Observed (all 0 failures / 0 skips):** `SingleShotRequestTest` 10/10: `oneTranscriptBecomesOneForcedSingleCallRequest`, `theRequestCarriesTheSnapshotsToolInstancesUnchanged`, `forceToolFalseSendsAutoChoiceAndStillAsksForOneCall`, `aSnapshotWithoutAForcedToolFailsWithNoProviderCall`, `aMissingCredentialFailsWithoutAProviderCallAndNeverRunsTheNextTier`, plus builder-validation tests. `SingleShotResolveTest` 10/10: `theResolverReceivesTheFirstCallAndTheSameInput`, `aFinishedStepIsSubmittedBeforeTheMutationAndOnlyTheMutationReachesTheGate`, `twoMutationStepsGoOutAsOneProposalHoldingAllThreeInOrder`, `finishedOnlyStepsNeverAskTheGate`, `aHoldingGateLeavesEveryMutationUnappliedAndHoldsOneProposal`, `anAdmittingGateCommitsEveryMutationAndTheSinkSeesOneActionEach`, `aFailedApplyWithholdsTheReply`. `SingleShotLimitsTest` 10/10: `exactlyOneProviderCallAtMinimumIterations` and `...AtDefaultIterations` (one call per command whatever `maxIterations` is). `SingleShotSeamTypesTest` 15/15 and `SingleShotPlumbingTest` 6/6 cover the `ToolSpecProvider`/`ToolingSnapshot`, `OutcomeResolver`/`Resolution` and `UserTurnRenderer` seams. `SingleShotOutcomeMappingTest.aWriteIsNeverMadeOnAnyNonResolutionPath` pins that no non-resolution path applies anything. `SingleShotWireTest` 6/6 runs the real Anthropic and Chat transports on a local server, e.g. `anthropicForcedBodyAsksForOneToolAndCommits` and `openAiBodySendsParallelToolCallsFalseAndCommits` (asserts the forced `tool_choice` body and one commit). The seam sign-off (`evidence/seam-signoff.txt`) records `SIGNOFF: APPROVE` relayed from the orchestrator, with a Phase 9 carry (`AgenticLoopStrategy` must accept the same `UserTurnRenderer`) recorded in 09-CONTEXT.md; that is a process record, not a behaviour check.
- **Evidence:** `core/build/test-results/test/TEST-...SingleShot{Request,Resolve,Limits,SeamTypes,Plumbing,OutcomeMapping}Test.xml`, `providers/build/test-results/test/TEST-...SingleShotWireTest.xml`, `.planning/phases/07-singleshot-strategy/evidence/seam-signoff.txt`.

### 2. SC2: by default a reply with no tool call (or prose only) escalates with `NoToolCall` and a refusal fails with `REFUSAL`; both mappings are overridable per tier; Chat Completions requests carry `parallel_tool_calls: false`
result: passed
- **Rung:** 1
- **Target:** headless JVM harness plus loopback transports
- **Expected:** prose or no call -> escalate with `NoToolCall` (next tier runs, no carry); refusal -> `Failed(Refusal)` with no escalation and no resolver call; per-tier hooks replace each default without leaking to a second tier; OpenAI Chat bodies carry `parallel_tool_calls: false`.
- **Arranged (seeded):** `FakeAiProvider` prose, refusal and multi-call answers (helpers added in 07-01); two-tier ladders; `MockWebServer` bodies captured per vendor.
- **Did (drove):** forced re-run; read the outcome-mapping, terminal and wire assertions.
- **Observed:** `SingleShotOutcomeMappingTest` 26/26 including `noToolCallFailureEscalatesAndTheNextTierRunsOnceWithNoCarry`, `proseThatEndedTheTurnEscalatesWithNoToolCall`, `noToolCallFailureOnAOneTierLadderIsUnhandledWithTheReason`, `refusalFailureIsAFailedRefusalWithNoEscalation`, `theFakeProvidersRefusalAnswerFailsWithRefusal`, `aRefusalStopWithAToolCallPresentFailsBeforeTheCallIsRead`, `aNoToolCallHookReplacesTheDefaultAndGetsTheResponseOnASuccess`, `aRefusalHookReplacesTheDefaultAndTheNextTierRuns`, `oneTiersOverrideNeverChangesASecondSingleShotTier`, `aCallToAToolTheSnapshotNeverOfferedEscalatesWithMalformedExtraction`. `SingleShotAcceptanceTest.s7RefusalFailsAndProseEscalates` asserts `Failed` with `FailureReason.Refusal`, gate and resolver at 0 calls, and the prose case running the next tier once with a null carry. `SingleShotWireTest.openAiProseAnswerEscalatesToTheNextTier` and `anthropicRefusalFailsWithRefusal` repeat this through the real transports. **Chat `parallel_tool_calls: false`:** `SingleShotWireTest.openAiBodySendsParallelToolCallsFalseAndCommits` asserts `body["parallel_tool_calls"] == false` on the wire; `ChatEncoderTest.aForcedOpenAiRequestStillTurnsTheParallelSwitchOffWithoutTheFlag`, `aSingleToolCallRequestTurnsTheSwitchOffEvenWithAutomaticChoiceAndANonStrictTool`, `theKeyOrderHoldsWhenTheParallelSwitchIsPresent`; `OpenAiModelRulesTest.theOSeriesRejectsTheParallelSwitchAndEveryOtherFamilyTakesIt` and `ChatEncoderTest.theOSeriesNeverGetsTheParallelSwitch` (o-series gate); Anthropic equivalent `AnthropicEncoderTest.aSingleToolCallPutsTheParallelOffSwitchInsideToolChoiceInEveryShape` (`disable_parallel_tool_use`). **Honest scope note:** OpenRouter bodies deliberately omit `parallel_tool_calls` (`openRouterNeverCarriesTheParallelSwitchAndKeepsRequireParametersWhenForced`, `SingleShotWireTest.openRouterBodyOmitsParallelToolCallsAndUsesTheFirstCall`); the ROADMAP's "Chat Completions requests carry `parallel_tool_calls: false`" is therefore met for OpenAI, and for OpenRouter the engine instead takes the first call and flags the drop (below). That is the documented design (RESEARCH, 07-02), not a defect, but a Gate-2 reader should know the two Chat vendors differ.
  - **Partial-drop follow-up (orchestrator ruling, `evidence/shot02-partial-drop.txt`):** when more than one tool call comes back, only the first is applied, `EXTRA_TOOL_CALLS_DROPPED` is recorded in the trace and the outcome is `Completed(partial = true)`. Re-verified, not trusted: `SingleShotAcceptanceTest.twoToolCallsApplyTheFirstAndCompleteAsPartialWithTheDropInTheTrace`, `oneToolCallCompletesWithoutPartial`, `twoToolCallsWhereTheGateHoldsTheFirstCompleteAsPartialWithTheHeldProposal`, `s10OnlyTheFirstOfSeveralToolCallsIsResolved`; `SingleShotTerminalTest.aTerminalFirstCallFollowedByAnExtraCallStillRecordsTheDroppedCallsCode`; `SingleShotOutcomeMappingTest.extraToolCallsAreDroppedAndRecordedOnceWhileTheTurnStillListsAllOfThem` and `aSingleCallRecordsNoDroppedCallsCode`; all green.
- **Evidence:** `TEST-...{SingleShotOutcomeMappingTest,SingleShotAcceptanceTest,SingleShotTerminalTest,SingleShotWireTest,ChatEncoderTest,OpenAiModelRulesTest,AnthropicEncoderTest}.xml`; `evidence/shot02-partial-drop.txt`.

### 3. SC3: CT's confirm scenarios pass as acceptance tests: weak match held for confirmation, batch proposal, amended confirm, deferred `commitHeld`
result: passed
- **Rung:** 1
- **Target:** headless JVM harness (`SingleShotAcceptanceTest`, scenarios S1-S10 plus gate interplay)
- **Expected:** a weak single match is held and later committed via `commitHeld` as a linked child run; a batch is held with the gate asked once; an amended confirm applies only the replacement list; per-row failures do not poison siblings; the hold never reaches the next tier.
- **Arranged (seeded):** CT-shaped scripted resolver (`ENTRIES_TOOL`, items `item-a..item-d`, mutation objects counting `applyCount`), recording gate and sink.
- **Did (drove):** forced re-run; read the assertions.
- **Observed:** `SingleShotAcceptanceTest` 14/14. `s1StrongSingleAutoCommitsInTheOriginalRun` (one `COMMITTED` action, `applyCount == 1`, run closed `Done`). `s2WeakSingleIsHeldThenCommittedLaterAsALinkedRun` (`HELD` action, `applyCount == 0`, then `commitHeld(held)` yields a child with `parentRunId == original.runId`, `applyCount == 1`, a second `commitHeld` is idempotent, gate calls stay 1). `s3AllStrongBatchIsStillHeldAndTheGateIsAskedOnce` (gate calls 1, one proposal with 3 mutations, three `HELD`, all `applyCount == 0`). `s4AmendedConfirmAppliesOnlyTheReplacementList` (originals stay at 0, the two amended mutations at 1, commits are `item-a` and `item-c`). `s5AFailingRowDoesNotPoisonItsSiblings`, `s6UnmatchedRowIsProposedThenRecoveredAtAmend`, `s8GateAdmitWithAnAmendedListAppliesItInTheOriginalRun`, `s9ClarificationEndsTheTierWithoutTheResolver`, `aHeldProposalNeverReachesTheNextTier`. Canary: `RedactionCanaryTest` references SingleShot (grep-confirmed) and is green.
- **Honest scope note:** these are "CT-shaped" acceptance tests built on in-tree fakes that mirror CT's confirm contract (the master/orchestrator relayed CT's approval of the seam shapes in the sign-off). They are not CT's own tests running against CT's real resolver. Whether CT's real app flows work against the published library is a Phase 11 / consumer-migration question, not provable here.
- **Evidence:** `core/build/test-results/test/TEST-...SingleShotAcceptanceTest.xml`.

## Summary

total: 3
passed: 3
partial: 0
failed: 0
infra: 0

## Notes / anomalies (for the Gate-2 reviewer)

- **Why this log is a backfill:** the Phase 7 execute stage recorded Gate-1 as N/A (`has-uat-criteria` = false), so no SELF-UAT.md and no pending fragment were written. The orchestrator flagged the gap; the milestone master had this headless record produced on 2026-10-01 so the ledger matches Phases 1-6.
- **Not covered here (belongs to Phase 10, VER-02 / VER-03):** anything that needs a real provider or a device. Specifically the live check carried forward in `evidence/phase-gate.txt`: assert the real Anthropic smoke body contains `disable_parallel_tool_use` and returns 200, and (optionally, two cheap calls) `cache_read_input_tokens > 0` to retire RESEARCH A2. Phase 10's VER-03 is now extended (10-CONTEXT.md Runtime Decisions) to include Chat/OpenRouter multi-turn on device; SingleShot's real-vendor wire acceptance (OpenAI accepting `parallel_tool_calls:false` with a forced tool choice, OpenRouter behaviour when the model returns several calls) is only proven against local-server shapes in this phase. No live provider call was made by this run.
- Highest rung used: 1 for all three criteria. No visual rung applies; the phase has no UI.
- Test counts grew after the phase gate file: core 526 -> 535 and providers 430 -> 572 (the providers growth is mostly Phase 8, which sits above Phase 7 in the same module). `evidence/phase-gate.txt` and `07-VERIFICATION.md` therefore carry older numbers than this log.
- The gate-file `evidence/singleshot-surface-review.txt` (100 KB) was not re-read; the API-surface script was re-run instead (classes=177, seven sealed types, matching the gate file).
- No source edits, no tags. `api.txt` is still not committed; it is created at the v1.0.0 cut in Phase 11. Pre-existing dirty files under `.planning/` (graphs, config.json, v1.0-MILESTONE-RUN.md, state.json, stage markers, `.gsd/`, intel) belong to the orchestrator and were not touched.

## Findings routed to gap-closure (if any)

None.

## Verdict

All 3 criteria PASS on the headless JVM harness. Gate-1 (headless) complete; Gate-2 registered as a ledger-completeness fragment (`.planning/uat-pending/07-singleshot-strategy.md`). Nothing physical or device-bound is deferred from this phase; the live-provider halves are carried by Phase 10.
