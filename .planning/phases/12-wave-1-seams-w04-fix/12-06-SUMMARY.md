---
phase: 12-wave-1-seams-w04-fix
plan: 06
subsystem: core
tags: [provider-call-id, extraction, executed-action, commit-path, held-proposal, additive-api]
requires: [12-02]
provides:
  - "Extraction.callId: 3-argument primary constructor, the 2-argument constructor kept as a public secondary (callId null), blank non-null callId rejected"
  - "ExecutedAction.providerCallId, inserted before the trailing mutating default (STUB_EXCEPTIONS unchanged)"
  - "internal CommandSession.submit(step, providerCallId) overload; the public submit(step) is unchanged and records null"
  - "the id reaches every ActionDetails the coordinator builds: finished, applied, held, gate fault, cancelled apply"
  - "HeldProposal.providerCallId (internal) so commitHeld, plain or amended, commits with the proposing call's id"
  - "SingleShot stamps its one call id on every action it submits; AgenticLoop stamps each call's own id"
affects: [17]
tech-stack:
  added: []
  patterns: ["internal abstract overload on a public abstract class (like recordCode) so only the engine's own strategies can stamp a provider id"]
key-files:
  created: []
  modified:
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/OutcomeResolver.kt
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/CommandSession.kt
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/pipeline/RunSession.kt
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/pipeline/HeldCommit.kt
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/commit/CommitCoordinator.kt
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/commit/ApplyStep.kt
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/commit/ActionLedger.kt
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/commit/CommitSink.kt
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/commit/HeldProposal.kt
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/singleshot/SingleShotStrategy.kt
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/agentic/AgenticDispatch.kt
    - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/CommitPathTest.kt
    - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/HeldReportingTest.kt
    - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/SingleShotResolveTest.kt
    - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/AgenticLoopDispatchTest.kt
    - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/RedactionCanaryTest.kt
    - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/ApiShapeTest.kt
key-decisions:
  - "the id travels only through internal plumbing (overload, ActionDetails, HeldProposal field); no coroutine-context element, no public channel, no toString change"
  - "CommitCoordinator keeps a single submit(step, providerCallId); the one-argument form was unused after RunSession switched over and would have tripped detekt TooManyFunctions"
requirements-completed: [SEAM-06]
status: complete
plan_head_before: 743dd5b2a614d60093d70688e1dcbefb6689c909
commits: 3
metrics:
  completed: 2026-10-05
actuals:
  tokens: 10600
  tasks: 3
  commits: 3
---

# Phase 12 Plan 06: Provider tool-call id on Extraction and ExecutedAction Summary

The provider's tool-call id now rides from `ToolCall.id` to `Extraction.callId` (what the app's resolver and executor see) and onto `ExecutedAction.providerCallId` (what the ledger, the sink and the outcome carry), through internal plumbing only; zero-call tiers report null. Strictly additive: `core/api.txt` is byte-identical to v1.0.1 and Metalava compat passes.

## What changed

- **Extraction**: `Extraction(toolName, arguments, callId)` is the primary; the 2-argument constructor stays as an explicit public secondary with the same JVM signature and yields `callId` null. A blank non-null id throws `IllegalArgumentException`. `toString` is unchanged (tool name and argument count).
- **ExecutedAction**: `providerCallId` sits before the trailing `mutating` default, so the default-argument stub, and with it `STUB_EXCEPTIONS`, is unchanged. `toString` is unchanged.
- **Plumbing**: `CommandSession` gets an internal abstract `submit(step, providerCallId)` (the public `submit(step)` signature is untouched and `RunSession` delegates it with null). `CommitCoordinator.submit` takes the id and threads it through `finished`, `submitMutation`, `hold`, `gateFault`, `applyAll` and `ApplyStep.run`, into every `ActionDetails` (`recordOutcome` and `journalCancelled` included). `ActionLedger.record` copies it onto the action.
- **Held changes**: `HeldProposal` stores the id in an internal field; `HeldCommit.run` passes `child.held.providerCallId` to `applyWithoutGate`, so a later `commitHeld` (with or without amended changes) commits with the proposing call's id.
- **Strategies**: SingleShot passes `call.id` into the `Extraction` and submits every finished step and the combined mutation with it. AgenticLoop passes each call's own id into its `Extraction` and `submit`; unknown tools and read steps still record nothing.

## Verification

- `:core:test :core:detekt :core:scanBannedConstructs :core:apiCheck` exit 0; `:providers:test :sample:testDebugUnitTest` exit 0.
- `git diff --exit-code v1.0.1 -- core/api.txt` exits 0 (no api.txt edit).
- New cases: SingleShot end to end (`call_7` reaches the resolver and the committed action and the sink event); one call stamping PREVIEW, IS_ERROR and two COMMITTED actions; HELD actions plus `commitHeld` plain and amended; gate fault; cancelled apply journaled with the id; AgenticLoop with calls a1, r1 (read), u1 (unknown), a2 (executor sees a1, r1, a2; actions carry a1, a2); public `submit` records null for the action and for a held proposal's later commit; reflection pins both `Extraction` constructors and the stub-exception set; a canary-bearing call id never appears in any outcome, trace, held proposal, sink action, event or `Extraction` string.

## Deviations from Plan

**1. [Rule 3 - Blocking] Task 1 commit also carries HeldProposal.kt and HeldCommit.kt**
The coordinator's `applyWithoutGate` and `hold` signature changes in Task 1 do not compile without the `HeldProposal` constructor parameter and the `HeldCommit` call site, so those two files landed in the Task 1 commit instead of Task 2. AgenticDispatch and the Task 2 tests are in the Task 2 commit as planned.

**2. [Rule 3 - Blocking] detekt TooManyFunctions on CommitCoordinator**
Adding the `submit(step, providerCallId)` overload put the class at 12 functions (threshold 12). The one-argument `submit(step)` had no remaining caller (RunSession uses the overload), so it was removed instead of raising the threshold or suppressing. Two over-long test lines were also wrapped. Both in the Task 3 commit.

No authentication gates, no deferred issues, no device or network use.

## Commits

- `8a36a97` feat(12-06): carry the provider tool-call id from Extraction to ExecutedAction
- `70b4d0d` feat(12-06): stamp each AgenticLoop call's id and prove every write path keeps it
- `afe63c5` test(12-06): zero-call null, constructor survival and redaction for the call id

## Self-Check: PASSED

All 17 listed files exist and carry the changes; commits `8a36a97`, `70b4d0d` and `afe63c5` are in `git log`; `core/api.txt` equals v1.0.1.
