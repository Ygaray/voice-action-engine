---
phase: "09"
slug: "agentic-loop-strategy"
status: draft
nyquist_compliant: false
wave_0_complete: false
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
| LOOP-01 | `ToolExecutor` composes with the write path; holding gate leaves applyCount 0; seam types redact toString | unit | `./gradlew :core:test --tests '*ToolExecutorSeamTest' --offline -q` | W0 | pending |
| LOOP-01 | tool turn then prose; sequential dispatch; reply = first text block; reads absent from executed list | unit | `... --tests '*AgenticLoopDispatchTest'` | W0 | pending |
| LOOP-01 | gate path: held notice bytes, rejected/preview, unknown tool, read-returns-Mutation, prepare fault | unit | `... --tests '*AgenticLoopGateTest'` | W0 | pending |
| LOOP-01 | same scripted conversation on three ProviderIds gives identical outcomes | unit | `... --tests '*AgenticLoopProviderNeutralityTest'` | W0 | pending |
| LOOP-01 | real mappers: Anthropic/OpenAI/OpenRouter bodies, Auto choice, SB-shaped user-turn bytes, held bytes | integration | `./gradlew :providers:test --tests '*AgenticLoopWireTest' :providers:testOkhttp521 --tests '*AgenticLoopWireTest' :providers:testOkhttp550 --tests '*AgenticLoopWireTest' --offline` | W0 | pending |
| LOOP-02 | whole-turn validation incl. cross-turn dup ids; ceiling before dispatch; final-iteration guard; 2-strike per tool; stop-leaf matrix; stop+tool_calls guard | unit | `... --tests '*AgenticLoopGuardsTest'` | W0 | pending |
| LOOP-02 | 6 / 60000 / 4096 from session.policy / session.tokensUsed | unit | `... --tests '*AgenticLoopLimitsTest'` | W0 | pending |
| D-13 | terminal-only; terminal after commit; terminal alongside held; calls after terminal dropped | unit | `... --tests '*AgenticLoopTerminalTest'` | W0 | pending |
| LOOP-03 | every exit path lists executed/commits/held; sink precedes onRunClosed; one close | unit | `... --tests '*AgenticLoopExitPathsTest'` | W0 | pending |
| LOOP-03 / O-1 | cancel between batch items stops the rest | unit | `... --tests '*BatchIsolationTest' --tests '*BatchCancellationTest'` | partial | pending |
| carries | Auto choice every request; replay stamped same provider/model; loop never escalates | unit | `... --tests '*AgenticLoopCarryTest'` | W0 | pending |
| D-02 | SB-shaped renderer exact text, core and wire | unit+integration | `AgenticLoopUserTurnTest`, `AgenticLoopWireTest` | W0 | pending |
| TEL-04 | canary never in outcome/trace/events/toString/exceptions of an agentic run | unit | `... --tests '*RedactionCanaryTest'` | extend | pending |
| CLN-02 | banned app-domain words fail `scanBannedConstructs` in all three modules; negative control; no tool-count literal | build gate | `./gradlew :core:scanBannedConstructs :providers:scanBannedConstructs :keystore:scanBannedConstructs :core:verifyInvariantScannerControls --offline -q` | gate exists, rules W0 | pending |
| surface | no new sealed/enum/data/public static; no default-arg ctor stubs | unit | `... --tests '*ApiShapeTest' --tests '*NoHardCodedConstantsTest'` | exists | pending |

*Status: pending / green / red / flaky*

---

## Wave 0 Requirements

- [ ] `core/src/testFixtures/.../testing/ScriptedToolExecutor.kt`
- [ ] `core/src/test/.../AgenticLoopTestSupport.kt`
- [ ] test classes listed in the map above
- [ ] `providers/src/test/.../AgenticLoopWireTest.kt`

---

## Manual-Only Verifications

All phase behaviors have automated verification (JVM-only phase; Gate-1 device UAT is N/A, deferred to Phase 10 VER-03). The `ToolExecutor` seam-shape sign-off is an orchestrator checkpoint, not a manual test.

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 90s
- [ ] _(finalizer-only, post-execution)_ `nyquist_compliant` - leave `false` at plan time

**Approval:** pending
