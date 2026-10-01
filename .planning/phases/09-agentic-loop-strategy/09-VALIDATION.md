---
phase: "09"
slug: "agentic-loop-strategy"
status: validated
nyquist_compliant: true
wave_0_complete: true
created: "2026-10-01"
---

# Phase 9 - Validation Strategy

> Per-phase validation contract for feedback sampling during execution. Source: 09-RESEARCH.md "Validation Architecture".

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 4.13.2 + kotlinx-coroutines-test 1.11.0 (`runTest`); hand-written fakes (no MockK); legacy `okhttp3.mockwebserver` in `:providers` |
| **Config file** | `core/build.gradle.kts`, `providers/build.gradle.kts` (legs `testOkhttp521`, `testOkhttp550`), `config/detekt/detekt.yml`, `gradle/invariants.gradle.kts` |
| **Quick run command** | `./gradlew :core:test --tests '*AgenticLoop*' --offline -q` |
| **Full suite command** | `./gradlew check --offline` |
| **Estimated runtime** | quick ~10 s; `:core:check` ~60 s; full `check` several minutes |

---

## Sampling Rate

- **After every task commit:** the plan's quick command plus `./gradlew :core:detekt :core:scanBannedConstructs --offline -q`
- **After every plan wave:** `./gradlew :core:check --offline` (core plans) or `./gradlew :providers:check --offline` (wire plan, all three OkHttp legs)
- **Before `/gsd-verify-work`:** `./gradlew check --offline` green, then `scripts/review-api-surface.sh --expect-sealed-complete`, `scripts/verify-repo-hygiene.sh`, `scripts/verify-negative-controls.sh`
- **Max feedback latency:** about 90 seconds per task

---

## Per-Task Verification Map

| Requirement | Behavior | Test Type | Automated Command | File Exists | Status |
|-------------|----------|-----------|-------------------|-------------|--------|
| LOOP-01 | `ToolExecutor` composes with the write path; holding gate leaves applyCount 0; seam types redact toString | unit | `./gradlew :core:test --tests '*ToolExecutorSeamTest' --offline -q` | yes | green |
| LOOP-01 | tool turn then prose; sequential dispatch; reply = first text block; reads absent from executed list | unit | `... --tests '*AgenticLoopDispatchTest'` | yes | green |
| LOOP-01 | gate path: held notice bytes, rejected/preview, unknown tool, read-returns-Mutation, prepare fault | unit | `... --tests '*AgenticLoopGateTest'` | yes | green |
| LOOP-01 | same scripted conversation on three ProviderIds gives identical outcomes | unit | `... --tests '*AgenticLoopProviderNeutralityTest'` | yes | green |
| LOOP-01 | real mappers: Anthropic/OpenAI/OpenRouter bodies, Auto choice, SB-shaped user-turn bytes, held bytes | integration | `./gradlew :providers:test --tests '*AgenticLoopWireTest' :providers:testOkhttp521 --tests '*AgenticLoopWireTest' :providers:testOkhttp550 --tests '*AgenticLoopWireTest' --offline` | yes | green |
| LOOP-02 | whole-turn validation (duplicate ids rejected within a turn only; an id reused from an earlier turn is accepted, per seam sign-off item 5); ceiling before dispatch; final-iteration guard; token ceiling beats the iteration cap on the same turn (BudgetExceeded(TOKENS)); 2-strike per tool; stop-leaf matrix; stop+tool_calls guard | unit | `... --tests '*AgenticLoopGuardsTest'` | yes | green |
| LOOP-02 | 6 / 60000 / 4096 from session.policy / session.tokensUsed | unit | `... --tests '*AgenticLoopLimitsTest'` | yes | green |
| D-13 | terminal-only; terminal after commit; terminal alongside held; calls after terminal dropped | unit | `... --tests '*AgenticLoopTerminalTest'` | yes | green |
| LOOP-03 | every exit path lists executed/commits/held; held, errored and previewed calls keep distinct ActionKind (HELD, IS_ERROR, PREVIEW; no new member, seam sign-off item 3); sink precedes onRunClosed; one close | unit | `... --tests '*AgenticLoopExitPathsTest'` | yes | green |
| LOOP-03 / O-1 | cancel between batch items stops the rest | unit | `... --tests '*BatchIsolationTest' --tests '*BatchCancellationTest'` | yes | green |
| carries | Auto choice every request; replay stamped same provider/model; loop never escalates | unit | `... --tests '*AgenticLoopCarryTest'` | yes | green |
| D-02 | SB-shaped renderer exact text, core and wire | unit+integration | `AgenticLoopUserTurnTest`, `AgenticLoopWireTest` | yes | green |
| TEL-04 | canary never in outcome/trace/events/toString/exceptions of an agentic run | unit | `... --tests '*RedactionCanaryTest'` | yes | green |
| CLN-02 | banned app-domain words fail `scanBannedConstructs` in all three modules; negative control; no tool-count literal | build gate | `./gradlew :core:scanBannedConstructs :providers:scanBannedConstructs :keystore:scanBannedConstructs :core:verifyInvariantScannerControls --offline -q` | yes | green |
| surface | no new sealed/enum/data/public static; no default-arg ctor stubs | unit | `... --tests '*ApiShapeTest' --tests '*NoHardCodedConstantsTest'` | yes | green |

*Status: pending / green / red / flaky*

---

## Wave 0 Requirements

- [x] `core/src/testFixtures/.../testing/ScriptedToolExecutor.kt`
- [x] `core/src/test/.../AgenticLoopTestSupport.kt`
- [x] test classes listed in the map above
- [x] `providers/src/test/.../AgenticLoopWireTest.kt`

---

## Manual-Only Verifications

All phase behaviors have automated verification (JVM-only phase; Gate-1 device UAT is N/A, deferred to Phase 10 VER-03). The `ToolExecutor` seam-shape sign-off is an orchestrator checkpoint, not a manual test.

---

## Validation Sign-Off

> Finalized post-execution by the Nyquist finalizer (2026-10-01): every requirement row has an existing, green automated test. Fresh JUnit XML: core 646 tests (all 15 mapped classes present, 0 failures), providers AgenticLoopWireTest 15/15 on all three OkHttp legs (4.12.0, 5.2.1, 5.5.0), keystore 96; `./gradlew check --offline` exit 0.

- [x] All tasks have `<automated>` verify or Wave 0 dependencies
- [x] Sampling continuity: no 3 consecutive tasks without automated verify
- [x] Wave 0 covers all MISSING references
- [x] No watch-mode flags
- [x] Feedback latency < 90s per task
- [x] `nyquist_compliant: true` set in frontmatter (post-execution only)

**Approval:** validated 2026-10-01

## Validation Audit 2026-10-01
| Metric | Count |
|--------|-------|
| Gaps found | 0 |
| Resolved | 0 |
| Escalated | 0 |
