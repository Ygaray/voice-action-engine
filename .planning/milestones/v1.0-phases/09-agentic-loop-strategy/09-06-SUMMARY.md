---
phase: 09-agentic-loop-strategy
plan: 06
subsystem: core/strategy/agentic
tags: [agentic-loop, exit-paths, cancel-safety, phase-8-carries, provider-neutrality, redaction, tests]
requires: [09-05]
provides:
  - AgenticLoopExitPathsTest (one named test per exit path, incl. three cancel points)
  - AgenticLoopCarryTest (Phase 8 carries and the partial rule over a 17-path sweep)
  - AgenticLoopProviderNeutralityTest (three provider ids, no provider name in agentic sources)
  - noCanaryLeaksFromAnAgenticRun in RedactionCanaryTest
affects: [09-07, 09-08, 09-09]
tech-stack:
  added: []
  patterns: [Rig of shared log + RecordingCommitSink, cancelOnceSuspended helper, per-path sweep with a never-run tier behind the loop, constant names read from the type by reflection]
key-files:
  created:
    - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/AgenticLoopExitPathsTest.kt
    - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/AgenticLoopCarryTest.kt
    - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/AgenticLoopProviderNeutralityTest.kt
  modified:
    - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/RedactionCanaryTest.kt
key-decisions:
  - "Seam sign-off item 3 (decided before the v1.0.0 freeze): ExecutedAction.kind (open ActionKind with COMMITTED, HELD, PREVIEW, IS_ERROR) plus applied already express held, previewed and errored calls. The loop forwards the coordinator's kinds unchanged, so NO member was added to ActionKind or ExecutedAction."
  - "Precedence is pinned at the pipeline level too: a same-turn tie between the token ceiling and the iteration cap reports BudgetExceeded(TOKENS) with the turn-1 commit and one close."
requirements-completed: [LOOP-03, LOOP-01]
status: complete
plan_head_before: fa4d4ccbac616a22a29e2cda29a5826f97dd4e4e
commits: 3
actuals:
  tokens: 28000
  tasks: 3
  commits: 3
metrics:
  completed: 2026-10-01
---

# Phase 9 Plan 06: Exit Paths, Carries, Neutrality and Redaction Summary

Every way an agentic run can end now has a named test showing the outcome and the close list what ran, the sink hearing each action first and the run closing once; the Phase 8 carries, provider neutrality on three ids and the agentic canary sweep are pinned too.

## What was built

- `AgenticLoopExitPathsTest` (11 tests): done (commit + read, then held, then prose; `Done` carries the same lists), distinct kinds (COMMITTED, HELD, PREVIEW, IS_ERROR in order; pairwise distinct; preview and rejection both `mutating=false`, `applied=false` so only the kind separates them; the sink's events and the close list the same kinds), iteration budget stop carrying all four kinds, token budget stop plus the same-turn tie (TOKENS wins), strike abort with the committed sibling, provider failure after a commit, malformed turn (id repeated within a turn) after a commit, terminal exit after a commit, cancel during the provider call, cancel while the gate is suspended (no record, no held proposal, `applyCount` 0), cancel during apply (one IS_ERROR with `applied` true, `apply_cancelled` in the trace). Each asserts every sink action precedes the single close entry in the shared log.
- `AgenticLoopCarryTest` (5 tests): automatic choice with `singleToolCall` false on every request of a 17-path sweep; the assistant turn re-sent as the same `AssistantMessage` and same `NativeReplay`/raw instances; one provider id (OPENAI), one model and one credential provider across all requests; a never-run `ScriptedStrategy` behind the loop on all 17 paths with every outcome Completed or Failed and no `escalation_suppressed`; `partial` true only for the dropped-calls path (budget, strike and provider-failure paths are Failed carrying their commits).
- `AgenticLoopProviderNeutralityTest` (2 tests): one commit/read/hold/prose script gives an identical digest (outcome class, reply, kinds, tool names, held count, provider call count, per-request message kinds) on ANTHROPIC, OPENAI and OPENROUTER; the agentic sources contain no word-bounded `ProviderId` or any upper-case constant name read from the type's companion at test time.
- `RedactionCanaryTest` (+1 test, 5 total): `noCanaryLeaksFromAnAgenticRun` plants canaries in the transcript, context, system text, tool description and schema, tool arguments, executor read/commit/apply/rejection content, the thrown prepare message and key, the reply, the hold reason/token and a carry from an earlier tier; a completing run (commit, read, hold, reply) and a strike-aborted run; sweeps outcomes, traces, listener events, sink actions and closes, gate proposals, provider requests, the strategy and pipeline `toString`. The agentic strategy was also added to the built-type sweep.

## Main-source fixes forced by tests

None. Every test passed against the 09-05 loop; no main file changed. The coordinator and the pipeline were not touched, and no member was added to `ActionKind` or `ExecutedAction`.

## Verification

- `./gradlew check --offline` green (detekt zero issues, scanBannedConstructs, OkHttp matrix legs, all modules).
- XML counts: ExitPaths 11/0/0, Carry 5/0/0, ProviderNeutrality 2/0/0, RedactionCanary 5/0/0 (tests/failures/errors); `RunClosedPathsTest` still green.
- `git diff --stat fa4d4cc..HEAD` touches only the four test files above; nothing under `providers/`, `keystore/`, `commit/` or `pipeline/`.
- `scripts/review-api-surface.sh` not run: no public API changed. No `api.txt`, no tag, no ledger or contract edit.

## Deviations from Plan

None. One detekt MaxLineLength finding appeared three times in new test code and was fixed by wrapping the lines.

## Notes

- Cancel tests launch `execute` with `async` inside `runTest`, wait on a `CompletableDeferred` set by the suspended provider step, gate or apply, cancel, then read the sink.
- The `heldErroredAndPreviewedCallsKeepDistinctKinds` run uses one tool name for all four kinds on purpose: only the rejection is an error, so there is a single strike.

## Self-Check: PASSED

Created files exist (AgenticLoopExitPathsTest.kt, AgenticLoopCarryTest.kt, AgenticLoopProviderNeutralityTest.kt, this SUMMARY); commits 9f973e0, 46bb69b, c7f3993 exist.
