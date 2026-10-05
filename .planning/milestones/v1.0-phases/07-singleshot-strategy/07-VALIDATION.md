---
phase: "7"
slug: singleshot-strategy
# status lifecycle: draft (seeded by plan-phase) -> validated (set by validate-phase)
status: validated
nyquist_compliant: true
wave_0_complete: true
created: "2026-10-01"
---

# Phase 7 - Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 4.13.2 + kotlinx-coroutines-test 1.11.0 (`runTest`); `:providers` adds legacy `okhttp3.mockwebserver` |
| **Config file** | `core/build.gradle.kts`, `providers/build.gradle.kts`, `config/detekt/detekt.yml` (no new framework install) |
| **Quick run command** | `./gradlew :core:test --tests '*SingleShot*' --offline -q` |
| **Full suite command** | `./gradlew :core:check :providers:check --offline` |
| **Estimated runtime** | ~65 seconds cold (detekt, scanners, ApiShapeTest, NoHardCodedConstantsTest, three OkHttp matrix legs) |

---

## Sampling Rate

- **After every task commit:** the quick command for the touched test class(es), plus `./gradlew :<module>:detekt --offline -q` for the touched module
- **After every plan wave:** `./gradlew :core:check` (only `:core` touched) or `./gradlew :core:check :providers:check --offline` (flag/encoders touched)
- **Before `/gsd-verify-work`:** `./gradlew check --offline` green, `scripts/review-api-surface.sh --expect-sealed-complete` prints `API SURFACE OK`, `scripts/verify-repo-hygiene.sh` clean
- **Max feedback latency:** ~65 seconds
- **Contention rule:** plans that run the same module's gradle tasks are sequenced one plan per wave (Phase 5/6 precedent); `:providers` compiles `:core`, so no two plans share a wave.

---

## Per-Task Verification Map

Seeded from 07-RESEARCH.md "Validation Architecture"; per-task rows are bound to task ids by the plans' `<automated>` blocks.

| Requirement | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|-------------|-----------------|-----------|-------------------|-------------|--------|
| SHOT-01 | one forced-tool request: tools, `Required(extractionTool)`, `maxTokens` from policy, single user message, `singleToolCall=true` | unit (pipeline + FakeAiProvider) | `./gradlew :core:test --tests '*SingleShotRequestTest' --offline -q` | yes | green |
| SHOT-01 | resolver gets FIRST call's extraction; resolver never writes; one merged proposal through the gate | unit | `... --tests '*SingleShotResolveTest'` | yes | green |
| SHOT-01 | seam types: redacted `toString`, snapshot validation, no default-arg stubs | unit | `... --tests '*SingleShotSeamTypesTest' --tests '*ApiShapeTest'` | yes | green |
| SHOT-01 | cache-safe user turn: same `system` + `tools` across commands | unit | `... --tests '*SingleShotUserTurnTest'` | yes | green |
| SHOT-02 | outcome mapping table: NoToolCall escalates, refusal fails REFUSAL, MAX_TOKENS, overrides | unit | `... --tests '*SingleShotOutcomeMappingTest'` | yes | green |
| SHOT-02 | terminal call skips resolver (forced and auto), A19/D-13 | unit | `... --tests '*SingleShotTerminalTest'` | yes | green |
| SHOT-02 | `ModelRequest.singleToolCall` read-back, 6-arg ctor unchanged | unit | `... --tests '*TranscriptTypesTest'` | yes | green |
| SHOT-02 | Anthropic `disable_parallel_tool_use:true` in forced AND reshape `tool_choice`; omitted when false | unit | `./gradlew :providers:test --tests '*AnthropicEncoderTest' --offline -q` | yes | green |
| SHOT-02 | Chat `parallel_tool_calls:false` on OpenAI; absent on OpenRouter and o-series | unit | `... --tests '*ChatEncoderTest' --tests '*OpenAiModelRulesTest'` | yes | green |
| SHOT-02 | real wire via SingleShotStrategy to MockWebServer, three dialects, 4.12.0/5.2.1/5.5.0 legs | integration (JVM) | `./gradlew :providers:test --tests '*SingleShotWireTest' --offline` | yes | green |
| SHOT-03 | S1-S10 CT-shaped acceptance scenarios (weak hold, batch, amended confirm, deferred commitHeld) | integration (full pipeline, fakes) | `... --tests '*SingleShotAcceptanceTest'` | yes | green |
| Phase 11 blocker | 6 / 60000 / 4096 limits: `maxTokensPerTurn` pass-through, pre/post token ceiling, one provider call at `maxIterations` 2 and 6 | unit | `... --tests '*SingleShotLimitsTest'` | yes | green |
| TEL-04 regression | no canary in anything SingleShot returns, delivers or prints | unit | `... --tests '*RedactionCanaryTest'` | yes | green |

*Status: pending / green / red / flaky*

---

## Wave 0 Requirements

- [x] `core/src/test/.../SingleShotRequestTest.kt`, `SingleShotResolveTest.kt`, `SingleShotOutcomeMappingTest.kt`, `SingleShotTerminalTest.kt`, `SingleShotLimitsTest.kt`, `SingleShotUserTurnTest.kt`, `SingleShotSeamTypesTest.kt`, `SingleShotAcceptanceTest.kt`, shared `SingleShotFixtures.kt`
- [x] `core/src/testFixtures/.../FakeAiProvider.kt`: additive `refusal(usage)` / `toolCalls(...)` helpers
- [x] `providers/src/test/.../SingleShotWireTest.kt`
- [x] No framework install needed

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Anthropic body carries the new flag and returns 200 on the live API (and optional cache-read check) | SHOT-02 | Live leg is opt-in, outside `check`; deliberately carried to Phase 10 VER-03 smoke | Phase 10 live smoke via `with-test-keys -- <cmd>`; no live call in Phase 7 |

*Phase 7 has no device-verifiable surface (JVM-only); Gate-1 is recorded N/A.*

---

## Validation Sign-Off

> Finalized post-execution by the Nyquist finalizer (2026-10-01): status validated, nyquist_compliant true.

- [x] All tasks have `<automated>` verify or Wave 0 dependencies
- [x] Sampling continuity: no 3 consecutive tasks without automated verify
- [x] Wave 0 covers all MISSING references
- [x] No watch-mode flags
- [x] Feedback latency < 90s
- [x] `nyquist_compliant: true` set in frontmatter (post-execution only)

**Approval:** validated 2026-10-01

---

## Validation Audit 2026-10-01

| Metric | Count |
|--------|-------|
| Gaps found | 0 |
| Resolved | 0 |
| Escalated | 0 |

Every requirement row has an existing, green automated test (`./gradlew check --offline` exit 0 at HEAD c405245: core 532, providers 430 on each of the 4.12.0, 5.2.1 and 5.5.0 legs, 0 failures). The only Manual-Only item (live Anthropic flag/cache check) is deliberately carried to Phase 10 VER-03.
