---
phase: 02-core-contract-pipeline-commit-seam
plan: 08
subsystem: core
status: complete
tags: [kotlin, telemetry, trace, usage, event-listener, redaction-canary, detekt]

requires:
  - phase: 02-core-contract-pipeline-commit-seam
    provides: "02-02 Usage, TraceCode (listener_error), CommandTrace; 02-04 TierWalk and run close; 02-05 ActionLedger single append point; 02-06 commitHeld child run and shared closeRun"
provides:
  - "TurnRecord plus CommandSession.recordTurn and tokensUsed: the hook strategies (Phases 3-9) use to report model turns and enforce the token ceiling"
  - "CommandTrace.usage and TierAttempt.turns/provider/model/usage"
  - "Open PipelineEvent set (nine leaves) and a non-suspending PipelineEventListener set through the DSL"
  - "Internal RunRecorder.cacheNotEngaged hook (Phase 3 calls it)"
  - "FakeClock and RecordingEventListener test fixtures"
  - "RedactionCanaryTest: a mechanical no-leak guard over every engine output"
affects: [02-09, Phase 3, Phase 4, Phase 5, Phase 7, Phase 9, Phase 10]

plan_head_before: 734ad9c13c02b2f5875530b0b0ac36d0b7752da2

actuals:
  tokens: 19300   # chars/4 over the realized diff of core/ (77,052 chars incl. diff headers)
  tasks: 3
  commits: 3

tech-stack:
  added: []
  patterns:
    - "Recorder state changes under a lock, listener called after the lock is released"
    - "One EventDispatch owns the guarded listener call and reports the first throw once"
    - "Trace usage, attempt usage, provider and model are derived from the turns, never stored twice"

key-files:
  created:
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/telemetry/TurnRecord.kt
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/telemetry/PipelineEvent.kt
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/telemetry/PipelineEventListener.kt
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/telemetry/EventDispatch.kt
    - core/src/testFixtures/kotlin/io/github/ygaray/voiceactionengine/core/testing/FakeClock.kt
    - core/src/testFixtures/kotlin/io/github/ygaray/voiceactionengine/core/testing/RecordingEventListener.kt
    - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/TraceTest.kt
    - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/EventsTest.kt
    - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/RedactionCanaryTest.kt
  modified:
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/telemetry/CommandTrace.kt
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/telemetry/RunRecorder.kt
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/CommandSession.kt
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/pipeline/RunSession.kt
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/pipeline/PipelineBuilder.kt
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/pipeline/CommandPipeline.kt
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/pipeline/HeldCommit.kt
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/pipeline/TierWalk.kt
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/pipeline/PolicyPreCheck.kt
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/commit/ActionLedger.kt
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/commit/CommitCoordinator.kt
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/commit/ApplyStep.kt

key-decisions:
  - "tokensUsed is run-cumulative across tiers (the recorder owns the total), so a later tier's token ceiling sees earlier tiers' spend"
  - "A throwing listener is recorded as listener_error once per run, not once per event, so a broken listener cannot flood the trace"
  - "TierAttempt and CommandTrace derive usage, provider and model from their turns instead of taking them as constructor arguments, keeping constructors under the detekt parameter limit with no tuning"

patterns-established:
  - "Events mirror the trace: TierFinished attempts are the trace's own attempt instances, and TierSkipped plus EngineCode codes equal trace.codes"

requirements-completed: [TEL-02]
requirements-partial: [TEL-01]

duration: ~10min
completed: 2026-10-01
---

# Phase 2 Plan 08: Telemetry (turn records, trace usage, live events, redaction canary) Summary

**Every outcome now carries per-attempt provider, model, normalized usage and clock latency; strategies report model turns through `session.recordTurn` and read the run's token total from `session.tokensUsed`; an optional non-suspending listener sees the whole run live; and a canary test proves no user content or key reaches any engine output.**

## Performance

- **Tasks:** 3 of 3 (Task 1 tracer, Tasks 2 and 3 TDD)
- **Commits:** c47f5f8 (task 1), 7f91e12 (task 2), 84e39f3 (task 3)
- **Files:** 9 created, 12 modified (21 files in the diff of `core/`)

## Accomplishments

### Event leaf list (the frozen taxonomy)

`PipelineEvent` is a public, non-sealed interface (`val runId`) with nine nested leaves, all with internal constructors, public getters and ids/codes/counts-only `toString`:

`CommandStarted(runId, parentRunId)`, `TierStarted(runId, strategy)`, `TierSkipped(runId, strategy, code)`, `ProviderCall(runId, strategy, turn)`, `ActionRecorded(runId, position, kind, toolName, applied)`, `EngineCode(runId, code)`, `CacheNotEngaged(runId, strategy, provider, model)`, `TierFinished(runId, attempt)`, `RunClosed(runId, terminationCode, executedCount, committedCount)`.

`ActionRecorded` never holds the action object (which carries the app's context); it copies position, kind, tool name and applied.

### Hooks and plumbing

- `TurnRecord(provider, model, stopReason, toolNames, usage, latencyMillis)`: public constructor, defensive copy of the tool names, toString prints all fields.
- `CommandSession.recordTurn(turn)` (suspend) and `tokensUsed`: `RunSession` forwards to the recorder, which attaches the turn to the current tier, adds `turn.usage.total` to a run-wide total and emits `ProviderCall`.
- `TierAttempt` gained `turns`, `provider`, `model` (the last turn's) and `usage` (sum of its turns); `CommandTrace` gained `usage` (sum over attempts). Constructors stay internal.
- `PipelineBuilder.listener` flows through `CommandPipeline` and `HeldCommit` into each run's recorder. `CommandStarted` is emitted first inside the run's `try` (so a cancelled start still closes once). `RunClosed` is emitted by the shared `closeRun` after the sink's `onRunClosed`, inside the same `NonCancellable` block, so both `execute` runs and `commitHeld` child runs get it.
- `ActionLedger.record` is now `suspend` and takes the recorder: it is the single append point and the single place `ActionRecorded` is emitted.
- Recorder emitting hooks are `suspend`; state changes happen under the lock and the listener is called after it is released. The listener call goes through the existing `guarded` helper (via the small internal `EventDispatch`), so no new broad catch and no new `@Suppress`.
- Internal hook for Phase 3: `RunRecorder.cacheNotEngaged(strategy, provider, model)` emits `CacheNotEngaged`. There is deliberately no public emission API.
- Fixtures: `FakeClock` (a `() -> Long` with `now` and `advanceBy`, so it can be assigned directly to the DSL `clock`), `RecordingEventListener` (ordered thread-safe `events`, optional throwing mode that records first).

### Tests

- `TraceTest` (7): the tracer scenario (provider, model, one turn, usage 1050 on attempt, trace and `session.tokensUsed`), no-turn tier has null provider/model and zero usage, fake-clock latency and run id equals command id, five-event live order, Anthropic vs OpenAI usage parity (equal `Usage`), escalation reason with clock latency, suppressed escalation with clock latency.
- `EventsTest` (7): the exact ten-event order for the two-tier run (preview then escalate, then turn, commit, hold, complete), events-equal-trace mapping (attempt instances, codes, executed list, turns), `tier_skipped_policy`, `gate_error`, the internal cache hook, a listener that throws on every event leaves outcome, executed list, sink calls and apply counts identical to a run with no listener and to a run with a normal listener (and is called exactly as many times, so no re-entry), and a run with no listener.
- `RedactionCanaryTest` (2): see below.

### Redaction canary (TEL-04 Phase-2 slice)

Canaries are planted in the transcript, a context object, a carry object, the reply slot, `StepResult` content, a mutation context/snapshot, target ids, a hold reason object, hold and result tokens, terminal-call arguments, clarification question, option ids and labels, an apply exception message, a strategy exception message, and `Credential(ANTHROPIC, "sk-CANARY-KEY")`. Two runs (a full two-tier run with escalation, commit, hold, terminal clarification; and a run whose apply and strategy throw) feed 25+ distinct `toString()` values: inputs, credential, pipeline, strategy outcomes, gate decisions, proposals, dispatch results, sessions, steps, executed actions, sink events and terminations, held proposals, outcomes, traces, attempts, turns, usage, every event, `FailureReason.Unexpected`, terminal call, clarification and options. None contains `CANARY` or the key. The trace codes and event identifiers are also asserted to be plain identifiers.

**Fixed `toString`s:** none. Every type already printed lengths, counts, ids or class names. As a negative control I temporarily made `HeldProposal.toString` print its reason object: the canary test failed, and I reverted it (verified with `git status`).

## Verification evidence

- `./gradlew :core:check :providers:check` (foreground, not piped for the exit code): `BUILD SUCCESSFUL`, exit 0. detekt zero issues, `scanBannedConstructs` clean, structural gates and the providers OkHttp 4.12.0 / 5.2.1 / 5.5.0 legs ran.
- `:core` tests: 209 total, 0 failures, 0 errors, 0 skipped (was 193; new: TraceTest 7, EventsTest 7, RedactionCanaryTest 2).
- Task verifies: `--tests '*TraceTest'`, `'*EventsTest' '*TraceTest'`, `'*RedactionCanaryTest'`, and `:core:detekt :core:scanBannedConstructs` all exit 0.
- `scripts/review-api-surface.sh --expect-sealed-complete`: `API SURFACE OK sealed=CommandOutcome,GateDecision,RunTermination,StrategyOutcome,ToolStep classes=117` (PipelineEvent is correctly not in the sealed list).
- `@Suppress` count in `core/src/main`: 1 (Guarded.kt only). No `api.txt` created. No planning ids in source.
- `git rev-list --count 734ad9c..HEAD` = 3 at the last task commit.

## Deviations from Plan

**1. [Rule 3 - Blocking] detekt `TooManyFunctions` forced an extra internal class**
- `RunRecorder` reached 12 functions (limit 11). Moved the guarded listener call and the "report the first fault once" logic into a new internal `EventDispatch` (one function), and dropped the private wrappers. No detekt tuning. The new file is beyond the plan's file list.

**2. [Rule 3 - Blocking] Task 1 touched `ActionLedger`, `CommitCoordinator` and `ApplyStep`**
- Making the recorder hooks `suspend` and wiring `ActionLedger` to the recorder (needed to compile and to emit `ActionRecorded`) landed in the Task 1 commit rather than Task 2. `ApplyStep.recordOutcome` became `suspend`; `TierWalk.handUp/startFresh/suppressed`, `PolicyPreCheck.nothingMayRun`, `CommandPipeline.timedOut/collapsed` also became `suspend`, as the plan allowed. Task 2 then added only tests. `GateStep.kt` needed no change.

**3. [Design choice] `tokensUsed` is run-wide, listener fault recorded once**
- See key decisions. The plan said "run-cumulative"; owning the total in the recorder (rather than per tier session) makes it truly cumulative across tiers.

## Known edges (not fixed)

- Turns reported by a tier that never reaches `tierFinished` (cancelled mid-tier) are counted in `tokensUsed` but not in any trace attempt.
- A listener that throws `CancellationException` propagates like any other cancellation (by the `guarded` contract).

## Hand-off notes

- **Phase 3:** call `RunRecorder.cacheNotEngaged(strategy, provider, model)` from the in-`:core` cache detection; it is already tested.
- **Phases 4-5:** the cross-provider parity test (TEL-01) is completed there with the real transports; this plan proves the `Usage` arithmetic and the plumbing (`TraceTest` parity example). Strategies must call `session.recordTurn` once per round trip and may read `session.tokensUsed` to enforce `policy.tokenCeiling`, `policy.maxIterations` and `policy.maxTokensPerTurn`.
- **Phase 10 README:** the listener cannot suspend and runs on the pipeline coroutine; a slow listener slows the run. A throwing listener never changes the command; the trace gets `listener_error`.

## Requirements bookkeeping

TEL-02 is fully delivered (event list including `CacheNotEngaged`, typed callback, live and in order). TEL-01 is partial: trace, attempts, reasons, provider/model, normalized usage, latency and the arithmetic parity example are done, but the cross-provider parity test that exercises the real transports completes in Phases 4-5, so TEL-01 is left Pending.

## Self-Check: PASSED

- All 9 created files exist; commits c47f5f8, 7f91e12 and 84e39f3 are present in `git log`; `git rev-list --count 734ad9c..HEAD` = 3.
