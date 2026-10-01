---
phase: "5"
slug: openai-openrouter-transports
# status lifecycle: draft (seeded by plan-phase) -> validated (set by validate-phase)
status: draft
nyquist_compliant: false
wave_0_complete: false
created: "2026-10-01"
---

# Phase 5 - Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 4.13.2 + kotlinx-coroutines-test 1.11.0 + legacy `okhttp3.mockwebserver` |
| **Config file** | `providers/build.gradle.kts` (matrix legs, live task), `config/detekt/detekt.yml` |
| **Quick run command** | `./gradlew :providers:test --tests '*<Class>' --offline -q` |
| **Full suite command** | `./gradlew check --offline` |
| **Matrix spot check** | `./gradlew :providers:testOkhttp521 :providers:testOkhttp550 --tests '*<Class>' --offline` |
| **Estimated runtime** | ~120-240 seconds for the full suite |

---

## Sampling Rate

- **After every task commit:** the quick command for the touched test class plus `./gradlew :providers:detekt :providers:scanBannedConstructs --offline -q`
- **After every plan wave:** `./gradlew :providers:check :core:check --offline` (includes all three OkHttp legs)
- **Before `/gsd-verify-work`:** `./gradlew check --offline` green plus `scripts/review-api-surface.sh --expect-sealed-complete`, `scripts/verify-negative-controls.sh`, `scripts/verify-repo-hygiene.sh`, `scripts/verify-api-dump.sh`
- **Max feedback latency:** 240 seconds
- **Contention rule:** one Gradle invocation per working tree at a time; plans sharing a wave run in separate worktrees or are sequenced.

---

## Per-Task Verification Map

Seeded from 05-RESEARCH.md "Validation Architecture"; the plans' `<automated>` commands are authoritative per task.

| Requirement | Behavior | Test Type | Automated Command | File Exists | Status |
|-------------|----------|-----------|-------------------|-------------|--------|
| PROV-08 | nested tool shape, key order, reasoning_effort/token param per rules row, forced tool_choice, require_parameters on OpenRouter forced calls only | unit (golden request) | `./gradlew :providers:test --tests '*ChatEncoderTest' --offline -q` | W0 | pending |
| PROV-08 | capability rows, OpenRouter id normalization, GPT-6 Astra / 6.1 Sol tools-unsupported on OpenAI only | unit | `./gradlew :providers:test --tests '*ChatModelsTest' --offline -q` | W0 | pending |
| PROV-08 | GPT-6 Astra through pipeline gives ModelUnsupported with zero requests | integration | `./gradlew :providers:test --tests '*ChatTransportTest' --offline -q` | W0 | pending |
| PROV-08 | decode precedence, arguments decode, refusal, finish_reason, usage | unit (golden response) | `./gradlew :providers:test --tests '*ChatDecoderTest' --offline -q` | W0 | pending |
| PROV-08 | 200-envelope errors, finish_reason error | unit + MockWebServer | `./gradlew :providers:test --tests '*ChatErrorMapTest' --offline -q` | W0 | pending |
| PROV-12 | extended optional-property detector | unit | `./gradlew :providers:test --tests '*OptionalPropertiesTest' --offline -q` | W0 | pending |
| PROV-12 | strict eligibility, authority, strip data | unit | `./gradlew :providers:test --tests '*ChatStrictTest' --offline -q` | W0 | pending |
| PROV-12 | per-vendor omitted optional arrives absent | integration | `./gradlew :providers:test --tests '*ChatAbsentOptionalTest' --offline -q` | W0 | pending |
| PROV-09 | retry matrix; one tool execution and one commit under retry | MockWebServer + pipeline | `./gradlew :providers:test --tests '*ChatRetryTest' --tests '*ChatPipelineRetryTest' --offline -q` | W0 | pending |
| PROV-11 / PROV-13 | clean client reuse, body API, 60 s default, cancellation | MockWebServer | `./gradlew :providers:test --tests '*ChatTimeoutTest' --tests '*ChatCancellationTest' --offline -q` | W0 | pending |
| TEL-04 | canary through pipeline + Chat transport | integration (all legs) | `./gradlew :providers:test --tests '*ChatCanaryTest' --offline -q` | W0 | pending |
| TEL-01 | identical normalized Usage and ceiling accounting across Anthropic, OpenAI, OpenRouter | integration | `./gradlew :providers:test --tests '*TokenParityTest' --offline -q` | W0 | pending |
| BLD-06 | all of the above on 4.12.0 / 5.2.1 / 5.5.0 | matrix | `./gradlew :providers:test :providers:testOkhttp521 :providers:testOkhttp550 --offline` | automatic | pending |

*Status: pending / green / red / flaky*

---

## Wave 0 Requirements

- [ ] `schema/OptionalPropertiesTest.kt`, `chat/ChatStrictTest.kt`, `chat/ChatModelsTest.kt`
- [ ] `chat/ChatFixtures.kt`
- [ ] `chat/ChatEncoderTest.kt`, `chat/ChatDecoderTest.kt`, `chat/ChatErrorMapTest.kt`
- [ ] `chat/ChatTransportTest.kt`, `ChatRetryTest.kt`, `ChatPipelineRetryTest.kt`, `ChatTimeoutTest.kt`, `ChatCancellationTest.kt`, `ChatMalformedKeyTest.kt`
- [ ] `chat/ChatAbsentOptionalTest.kt`, `chat/ChatCanaryTest.kt`, `parity/TokenParityTest.kt`
- [ ] golden resources plus manifest (derivatives first); a replay test fails when a manifest entry marked captured has no file

No new framework install.

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Real sanitized OpenAI and OpenRouter response goldens | PROV-08 SC2, D-12/D-14 | Needs live keys, key-gated (D-16) on the orchestrator relaying Yahir's OK; opt-in outside `check` | `with-test-keys --only openai,openrouter -- env VAE_LIVE_CHAT=1 ./gradlew :providers:liveChatCompletionsCapture --offline --no-daemon --console=plain` (hard ceiling 12 HTTP requests, gpt-5.4-mini only) |
| Low-credit mapping and accepted key character set on OpenAI / OpenRouter | carry to Phase 10 | Needs live account state | Phase 10 Gate-2 smoke |

---

## Validation Sign-Off

> Plan-time state is a DRAFT. `status: draft` and `nyquist_compliant: false` are finalized post-execution by the Nyquist finalizer only.

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 240s
- [ ] _(finalizer-only, post-execution)_ `nyquist_compliant` stays `false` at plan time

**Approval:** pending (finalizer-owned)
