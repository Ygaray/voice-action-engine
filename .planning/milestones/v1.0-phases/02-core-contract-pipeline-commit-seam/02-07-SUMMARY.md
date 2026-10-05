---
phase: 02-core-contract-pipeline-commit-seam
plan: 07
subsystem: core
status: complete
tags: [kotlin, confirm-gate, suspend-mode, mutex, stateflow, terminal-call, clarification, detekt]

requires:
  - phase: 02-core-contract-pipeline-commit-seam
    provides: "02-05/02-06 PreApplyGate, GateStep fail-closed, HeldProposal, commitHeld; 02-02 TerminalCall, Clarification, ToolSpec.clarification; 02-04 TierWalk"
provides:
  - "AwaitingConfirmGate, ConfirmationPolicy, ConfirmAmendHook, PendingConfirmation (suspend-mode confirm helper, public API SB binds to)"
  - "Pipeline-level proof that a terminal call ends the run as Completed(terminalCall) with commits and held carried"
affects: [02-08, 02-09, Phase 3, Phase 10]

plan_head_before: d6fa032e8a98c5ee159a56adc872a965aff31838

actuals:
  tokens: 8000   # chars/4 over the realized diff of core/ (32,063 chars incl. diff headers)
  tasks: 3
  commits: 3

tech-stack:
  added: []
  patterns:
    - "Mutex held for the whole confirmation window; state cleared in finally"
    - "No catch in the gate: policy and hook errors propagate to the engine's single guarded gate step"

key-files:
  created:
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/commit/AwaitingConfirmGate.kt
    - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/AwaitingConfirmGateTest.kt
    - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/TerminalCallTest.kt
  modified: []

key-decisions:
  - "timeoutMillis is a private constructor property: the plan lists no public getter for it, so the frozen surface stays minimal"
  - "The amend hook runs after the pending state is cleared but still inside the mutex, so the UI stops showing the sheet once the user answered while concurrent admits stay serialized"

patterns-established:
  - "Suspend-mode tests drive the pipeline with launch + runCurrent and answer through resolve, all in virtual time"

requirements-completed: [GATE-02, CORE-08]

duration: ~25min
completed: 2026-09-30
---

# Phase 2 Plan 07: Suspend-mode confirm gate and terminal-call behavior Summary

**`AwaitingConfirmGate` waits inside `admit` for the user's `resolve(id, true)` (fail-closed on decline, 120 s timeout, policy and hook errors), with a post-confirm amend hook; terminal calls are proven to end the run as `Completed(terminalCall)` with earlier commits and holds carried and no later tier.**

## Accomplishments

### AwaitingConfirmGate API as built (SB binds to this)

Package `io.github.ygaray.voiceactionengine.core.commit`, one file, four public types:

- `fun interface ConfirmationPolicy { suspend fun subjectFor(proposal: CommitProposal): Any? }` - null means admit at once, else the app-typed subject for the confirm UI.
- `fun interface ConfirmAmendHook { suspend fun afterConfirm(proposal: CommitProposal, subject: Any?): List<PendingMutation>? }` - runs after a confirm, before Admit; a returned list is what the engine applies instead of the proposal, null applies the proposal unchanged.
- `class PendingConfirmation internal constructor(val id: Long, val subject: Any?, val proposal: CommitProposal)`; `toString` = `PendingConfirmation(id=<id>, subject=<simple class name|null>)`.
- `class AwaitingConfirmGate(policy, timeoutMillis, amendHook, heldOutcomeToken) : PreApplyGate`, plus secondaries `(policy)` and `(policy, timeoutMillis)`. Default window 120_000 ms as a top-level private const. `require(timeoutMillis > 0)`. `val pending: StateFlow<PendingConfirmation?>`, `fun resolve(confirmationId: Long, confirmed: Boolean): Boolean`. `toString` = `AwaitingConfirmGate(timeoutMillis=<n>, pending=<id|none>)`.

Behavior: a null subject returns `Admit()`. Otherwise the gate takes its mutex for the whole window, publishes a `PendingConfirmation` with a monotonic id, awaits a `CompletableDeferred<Boolean>` inside `withTimeoutOrNull`, and clears the active slot and `pending` in `finally`. `true` gives `Admit(amended)` (hook list or null); false or timeout gives `Hold(subject, heldOutcomeToken)`. `resolve` returns true only when it completed the active confirmation; stale ids and repeated answers return false. No `catch` and no `@Suppress` in the file; policy and hook errors reach the engine's gate step, which holds with trace code `gate_error`. A cancellation while pending propagates and is never turned into a decision.

### KDoc hand-off notes (for the Phase 10 README)

- Dispatch is sequential: the mutex is held for the whole confirmation window, so a second proposal waits.
- The engine adds no timeout around `admit` beyond the gate's own window. An app that sets `TierPolicy.commandTimeoutMillis` shorter than the window gets `Failed(Timeout)` with nothing recorded.
- Cancelling the caller while pending clears the confirmation and records nothing.

### Terminal calls (CORE-08, D-22)

No main-code change was needed; plans 02, 03 and 06 already behave as required. `TerminalCallTest` (4 tests) proves: commit then `Completed(null, call)` gives `Completed` with reply null, the same `TerminalCall` instance, partial false, the commit in `commits`, tier 2 executions 0 and the attempt outcome `completed`; hold then terminal call lists the held proposal and writes nothing; `asClarification()` over `ToolSpec.clarification` shaped arguments round-trips question, labels and opaque ids (including special characters); outcome, call and clarification `toString` carry no question, label or id.

## Verification evidence

- `./gradlew :core:check :providers:check -q` (foreground, not piped through head/tail for the exit code): exit 0. detekt zero issues, `scanBannedConstructs` clean, providers OkHttp matrix legs ran.
- `:core` tests: 193 total, 0 failures, 0 errors, 0 skipped. AwaitingConfirmGateTest: 17 tests. TerminalCallTest: 4 tests.
- Task verifies: `--tests '*AwaitingConfirmGateTest'`, `'*TerminalCallTest' '*ToolSpecClarificationTest'`, `:core:detekt :core:scanBannedConstructs` all exit 0.
- `scripts/review-api-surface.sh --expect-sealed-complete`: `API SURFACE OK sealed=CommandOutcome,GateDecision,RunTermination,StrategyOutcome,ToolStep classes=105` (four new public types).
- `@Suppress` count in `core/src/main`: 1 (Guarded.kt only). No `api.txt`. No planning ids in source.
- Timeout tests use `advanceTimeBy` on virtual time (119_999 pending, then hold; 4_999 with a 5_000 window). The D-23 test runs through `commandPipeline` with `TierPolicy.DEFAULT`, resolves at 119_000 ms virtual time, and the write applies.

## Deviations from Plan

None. The plan's files were the only files touched. All tests passed on first run, so the Task 2 gate-change allowance was not used.

## Issues Encountered

None.

## Hand-off notes

- Phase 10 README: document the KDoc notes above and that `PendingConfirmation.subject` is the app's own object (the engine never prints it).
- The gate calls the policy before taking the mutex, so two concurrent admits evaluate their policies concurrently and only the confirmation windows serialize. An app policy that is not thread-safe should guard itself.

## Requirements bookkeeping

GATE-02 (defer half from plan 06, suspend half here) and CORE-08 are fully delivered.

## Self-Check: PASSED

- AwaitingConfirmGate.kt, AwaitingConfirmGateTest.kt, TerminalCallTest.kt exist; commits 75fa61f, b61c655, 0e2a5c7 are in `git log`; `git rev-list --count d6fa032..HEAD` = 3.
