---
phase: "9"
slug: agentic-loop-strategy
status: secured
threats_open: 0
asvs_level: 1
audited_head: 9f59da2af35057ecf397d0142e94592decefd4b6
created: "2026-10-01"
---

# Phase 9 - Security

> Per-phase security contract: threat register, accepted risks, and audit trail. Audited by gsd-security-auditor (ASVS 1, block_on high); the orchestrator wrote this file from the structured verdict. 45 threats (T-09-01..T-09-45), all `mitigate`, all CLOSED. Phase 2 finding O-1 is closed.

---

## Trust Boundaries

| Boundary | Description | Data Crossing |
|----------|-------------|---------------|
| model output -> app executor | tool names and arguments are untrusted and reach app code as an Extraction | tool names, arguments |
| app executor -> engine | the executor's ToolStep enters the one write path (gate, coordinator, sink) | ToolStep, results |
| engine -> telemetry | trace codes and toString can reach logs the app keeps | fixed codes, counts |
| engine -> provider wire | the loop's history replays through the Phase 8 mappers | request bodies |
| scanner -> library sources | CLN-02 raw-text rules keep app domain out of published modules | source text |

---

## Threat Register

Path key: CORE = core/src/main/kotlin/io/github/ygaray/voiceactionengine/core; CC = CORE/commit/CommitCoordinator.kt; DISP/LOOP/TURN/VALID = CORE/strategy/agentic/AgenticDispatch.kt / AgenticLoopStrategy.kt / AgenticTurn.kt / AgenticValidation.kt; LIM = CORE/strategy/StrategyLimits.kt; TE = CORE/strategy/ToolExecutor.kt; TC = CORE/telemetry/TraceCode.kt; INV = gradle/invariants.gradle.kts; T = core/src/test/.../core/; W = providers/src/test/.../providers/AgenticLoopWireTest.kt; EV = this phase's evidence/ directory.

| Threat ID | Category | Component | Disposition | Status | Evidence |
|-----------|----------|-----------|-------------|--------|----------|
| T-09-01 | Tampering | applyAll continuing after caller cancel (O-1) | mitigate | closed | CC:143 ensureActive per item; T/BatchCancellationTest (gated, held, uncancelled control) |
| T-09-02 | Repudiation | committed item hidden by cancel | mitigate | closed | BatchCancellationTest: sink heard item, Cancelled.commits lists it; AgenticLoopExitPathsTest actions-then-one-close |
| T-09-03 | Tampering | limit-helper move shifting SingleShot boundary | mitigate | closed | LIM:24-29 unchanged semantics (>= pre-call, > post-turn); no SingleShot test changed |
| T-09-04 | DoS | new check breaking an uncancelled batch | mitigate | closed | BatchCancellationTest.anUncancelledBatchStillAppliesEveryItem; BatchIsolationTest unchanged |
| T-09-05 | Tampering | executor writing before the gate | mitigate | closed | TE KDoc "must never write"; ToolExecutorSeamTest.aHoldingGateLeavesTheExecutorMutationUnapplied |
| T-09-06 | Info disclosure | trace codes carrying args/results/exception text | mitigate | closed | TC internal constructor, fixed literals; TraceTest pins wire values |
| T-09-07 | Elevation | unconfirmed public seam frozen at the cut | mitigate | closed | EV/seam-signoff.txt: SIGNOFF APPROVE-WITH-CHANGES, PLANS-AMENDED yes; sign-off commit precedes the first loop commit |
| T-09-08 | Repudiation | reads reported as writes; held reported as done | mitigate | closed | ToolExecutorSeamTest.aReadStepIsNeverRecorded; held bytes fixed in CC |
| T-09-09 | Tampering | cross-turn id replay confusing result matching | mitigate | closed | Superseded by sign-off item 5 (within-turn only); closed by per-turn pairing (LOOP:210-223); AgenticLoopGuardsTest.aToolCallIdReusedFromAnEarlierTurnIsAccepted |
| T-09-10 | Elevation | a write bypassing the gate | mitigate | closed | only write is session.submit (DISP:99); AgenticLoopGateTest, AgenticLoopDispatchTest tracer |
| T-09-11 | DoS | unbounded loop or token spend | mitigate | closed | LOOP bounds, LIM checks, TierPolicy maxIterations >= 2; Guards + Limits tests |
| T-09-12 | Spoofing | model calling an unoffered tool | mitigate | closed | DISP unknown_tool path, executor not called; AgenticLoopDispatchTest/GuardsTest |
| T-09-13 | Info disclosure | executor exception text to model/trace/logs | mitigate | closed | DISP guarded + fixed TOOL_ERROR_CONTENT; AgenticLoopGateTest marker sweep |
| T-09-14 | Tampering | per-command text in the cached prefix | mitigate | closed | LOOP request(): system/tools unchanged each iteration; UserTurnTest |
| T-09-15 | Tampering | truncated/refused answer dispatching calls | mitigate | closed | TURN decides stop leaves before tool calls; StopLeavesTest |
| T-09-16 | Tampering | concurrent dispatch breaking confirm mutex | mitigate | closed | sequential map in DISP; DispatchTest order, GateTest suspending gate |
| T-09-17 | Info disclosure | strategy toString carrying settings | mitigate | closed | LOOP toString prints id only; DispatchTest + RedactionCanaryTest |
| T-09-18 | Elevation | read tool smuggling a write | mitigate | closed | DISP guardWrites rewrites Mutation (and PREVIEW/ERROR, WR-02) for non-mutating specs; GateTest gate.calls == 0 |
| T-09-19 | Tampering | held change reported as done | mitigate | closed | DISP forwards coordinator bytes; GateTest exact held notice |
| T-09-20 | Info disclosure | exception text via model/trace | mitigate | closed | GateTest PREPARE-CANARY sweep |
| T-09-21 | DoS | model looping on a failing tool | mitigate | closed | per-name strike map, STRIKES_TO_ABORT 2; GuardsTest strike tests |
| T-09-22 | Repudiation | commits hidden by strike/terminal exit | mitigate | closed | outcomes carry executed/commits; GuardsTest, TerminalTest, ExitPathsTest |
| T-09-23 | Tampering | calls after a terminal call silently run | mitigate | closed | DISP stops at first terminal call, EXTRA_TOOL_CALLS_DROPPED, partial; TerminalTest |
| T-09-24 | Tampering | duplicate id within one turn | mitigate | closed | VALID within-turn check before dispatch; GuardsTest |
| T-09-25 | DoS | limits matrix 6 / 60000 / 4096 | mitigate | closed | from session.policy; 14 AgenticLoopLimitsTest methods; NoHardCodedConstantsTest |
| T-09-26 | Tampering | truncated/refused answer acted on | mitigate | closed | StopLeavesTest |
| T-09-27 | Repudiation | commit hidden behind a failure leaf | mitigate | closed | StopLeavesTest.everyLeafAfterACommitStillReportsTheCommit; ExitPathsTest |
| T-09-28 | DoS | rejected turn crashing the tier via message constructor | mitigate | closed | isAnswerable mirrors ToolResultsMessage invariants; GuardsTest (no strategy_error) |
| T-09-29 | Tampering | upstream regenerating ids failing runs | mitigate | closed | no run-wide id memory; GuardsTest cross-turn reuse accepted |
| T-09-30 | Repudiation | commit hidden behind failure, budget or cancel | mitigate | closed | AgenticLoopExitPathsTest one test per exit path, assertActionsThenOneClose |
| T-09-31 | Tampering | cancellation swallowed so writes continue | mitigate | closed | no catch in agentic sources; ensureActive per call (WR-03); ExitPathsTest cancel x3, BatchCancellationTest |
| T-09-32 | Info disclosure | transcript/args/results/reply leaking | mitigate | closed | RedactionCanaryTest.noCanaryLeaksFromAnAgenticRun; count-only printers |
| T-09-33 | Tampering | half-run conversation handed to another tier | mitigate | closed | no Escalate in agentic sources; AgenticLoopCarryTest.theLoopNeverHandsUpAConversation |
| T-09-34 | Spoofing | provider-specific branching | mitigate | closed | grep clean; AgenticLoopProviderNeutralityTest |
| T-09-35 | Tampering | held change shown as done on a real dialect | mitigate | closed | W held-bytes tests for Anthropic and Chat |
| T-09-36 | DoS | parallel_tool_calls sent where rejected | mitigate | closed | W parallel_tool_calls vendor cases |
| T-09-37 | Tampering | cached prefix changing between turns | mitigate | closed | W system/tools identical every turn |
| T-09-38 | Info disclosure | real key or network call in tests | mitigate | closed | fake key, MockWebServer only, no live test added |
| T-09-39 | Tampering | wire shape differing across OkHttp legs | mitigate | closed | 587 tests per leg on 4.12.0, 5.2.1, 5.5.0 |
| T-09-40 | Info disclosure | app prompts/tool names/app names in public library | mitigate | closed | INV rawRules incl. edit_text_card on main + testFixtures; negative controls |
| T-09-41 | Tampering | api.txt leaking before the cut | mitigate | closed | no api.txt, no tag; verify-repo-hygiene.sh |
| T-09-42 | Repudiation | hand-typed evidence | mitigate | closed | evidence derived from JUnit XML of a clean re-run |
| T-09-43 | Elevation | live call without approval | mitigate | closed | phase-gate records no live leg; no Live file in diff |
| T-09-44 | Tampering | scanner rule that silently matches nothing | mitigate | closed | verifyInvariantScannerControls + verify-negative-controls.sh (0 failures) |
| T-09-45 | Tampering | surface drift (eighth sealed type, enum, data class) | mitigate | closed | review-api-surface.sh --expect-sealed-complete: 7 sealed types, 181 classes |

*Status: open / closed. Disposition: mitigate / accept / transfer.*

---

## O-1 Closure (Phase 2 observation)

CommitCoordinator.applyAll now calls ensureActive() before each batch item (CC:141-145); the held path shares applyAll. BatchCancellationTest covers the gated, held and uncancelled-control cases (red before the fix, green after). evidence/o1-disposition.txt ends `O-1 DISPOSITION: FIXED`. No public API change.

---

## Accepted Risks Log

The register has no accept/transfer entries. By-design residuals, verified in code:

1. Cross-turn tool-call id reuse is allowed (sign-off item 5, SB parity); supersedes the original T-09-09 mitigation text in 09-02. A provider that rejects a reused id fails with its own typed failure.
2. Executor honesty (T-09-05): the engine gates everything returned as a ToolStep but cannot stop app code writing inside prepare; the ToolExecutor KDoc is the control.
3. Token-ceiling equality boundary (WR-01, sign-off item 9): a tool turn ending exactly on the ceiling still dispatches; the next model call is refused.
4. An unmapped stop reason that carries tool calls is a tool turn (D-11, IN-04); the four mapped reasons (refusal, max tokens, pause turn, context window) are always final.

Informational: evidence files are pinned to dd3c26e while the audit covers 9f59da2 (the ten review-fix commits in between, read directly); the 09-02 plan's T-09-09 wording was never amended to match the signed-off design.

---

## Security Audit Trail

| Audit Date | Threats Total | Closed | Open | Run By |
|------------|---------------|--------|------|--------|
| 2026-10-01 | 45 | 45 | 0 | gsd-security-auditor (ASVS 1, block_on high), audited_head 9f59da2af35057ecf397d0142e94592decefd4b6 |

---

## Sign-Off

- [x] All threats have a disposition (mitigate / accept / transfer)
- [x] Accepted risks documented in Accepted Risks Log
- [x] `threats_open: 0` confirmed
- [x] `status: secured` set in frontmatter

**Approval:** secured 2026-10-01
