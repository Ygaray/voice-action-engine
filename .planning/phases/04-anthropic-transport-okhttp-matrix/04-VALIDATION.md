---
phase: "4"
slug: anthropic-transport-okhttp-matrix
# status lifecycle: draft (seeded by plan-phase) -> validated (set by validate-phase)
status: draft
nyquist_compliant: false
wave_0_complete: false
created: "2026-09-30"
---

# Phase 4 - Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 4.13.2 + kotlinx-coroutines-test 1.11.0 + legacy `okhttp3.mockwebserver` |
| **Config file** | `providers/build.gradle.kts` (matrix legs), `config/detekt/detekt.yml` |
| **Quick run command** | `./gradlew :providers:test --tests '*<Class>' --offline -q` |
| **Full suite command** | `./gradlew check --offline` (runs `:providers:test`, `testOkhttp521`, `testOkhttp550`, detekt, scanner, bytecode, floor, module-graph, no-DI, core checks) |
| **Matrix spot check** | `./gradlew :providers:testOkhttp521 :providers:testOkhttp550 --tests '*<Class>' --offline` |
| **Estimated runtime** | ~120-240 seconds for the full suite |

---

## Sampling Rate

- **After every task commit:** the quick command for the touched test class plus `./gradlew :providers:detekt :providers:scanBannedConstructs --offline -q`
- **After every plan wave:** `./gradlew :providers:check :core:check --offline` (includes all three OkHttp legs)
- **Before `/gsd-verify-work`:** `./gradlew check --offline` green plus `scripts/review-api-surface.sh --expect-sealed-complete`, `scripts/verify-negative-controls.sh`, `scripts/verify-repo-hygiene.sh`, `scripts/verify-api-dump.sh`
- **Max feedback latency:** 240 seconds

---

## Per-Task Verification Map

Requirement-level map (task IDs are bound by the PLAN.md files; each task's `<automated>` command is drawn from this table).

| Requirement | Secure Behavior | Test Type | Automated Command | File Exists |
|-------------|-----------------|-----------|-------------------|-------------|
| BLD-06 | compile floor stays 4.12.0; each leg proves its okhttp AND mockwebserver version | gradle + unit (all legs) | `./gradlew :providers:verifyOkHttpCompileFloor :providers:test :providers:testOkhttp521 :providers:testOkhttp550 --tests '*OkHttpVersionGuardTest' --offline` | extend |
| PROV-04 | exactly one `cache_control` on last system block, none on messages/tools; version header, fixed URL | unit | `./gradlew :providers:test --tests '*AnthropicEncoderTest' --offline` | W0 |
| PROV-04 | cancel cancels the HTTP call and closes a late response | unit | `./gradlew :providers:test --tests '*CallAwaitTest' --tests '*AnthropicCancellationTest' --offline` | W0 |
| PROV-05 | encode twice gives identical tools+system bytes | unit | `./gradlew :providers:test --tests '*AnthropicEncoderTest' --offline` | W0 |
| PROV-06 | en/es, date, transcript change leaves prefix bytes unchanged | unit | `./gradlew :providers:test --tests '*AnthropicEncoderTest' --offline` | W0 |
| PROV-07 | capability table + forced vs auto; reactive 400 reshape (three conditions); NoToolCall | unit + MockWebServer | `./gradlew :providers:test --tests '*AnthropicForcedToolTest' --tests '*AnthropicModelsTest' --offline` and `./gradlew :core:test --tests '*ModelCapabilityTableTest' --offline` | W0 |
| PROV-09 | retry matrix, retry-after cap, spend-cap not retried, <=3 HTTP requests | MockWebServer | `./gradlew :providers:test --tests '*AnthropicRetryTest' --offline` | W0 |
| PROV-09 | retried call: one tool execution, one CommitSink action | pipeline integration | `./gradlew :providers:test --tests '*AnthropicPipelineRetryTest' --offline` | W0 |
| PROV-11 | derived client has no interceptors, no redirects; 307 does not leak the key | unit + MockWebServer | `./gradlew :providers:test --tests '*CleanClientTest' --offline` | W0 |
| PROV-13 | default 60 s, override honored; timeouts map to Timeout / Network | MockWebServer | `./gradlew :providers:test --tests '*AnthropicTimeoutTest' --offline` | W0 |
| PROV-12 (Anthropic leg) | omitted optional stays absent; strict only for eligible schemas | unit | `./gradlew :providers:test --tests '*AnthropicStrictTest' --offline` | W0 |
| TEL-04 | canary never appears in trace/events/toString/failure messages | integration (all legs) | `./gradlew :providers:test :providers:testOkhttp521 :providers:testOkhttp550 --tests '*AnthropicCanaryTest' --offline` | W0 |
| usage | usage maps to {inputUncached, cacheRead, cacheWrite, output} | unit | `./gradlew :providers:test --tests '*AnthropicDecoderTest' --offline` | W0 |

---

## Wave 0 Requirements

- [ ] `providers/src/test/.../anthropic/AnthropicEncoderTest.kt` - PROV-04/05/06
- [ ] `AnthropicTransportTest`, `AnthropicRetryTest`, `AnthropicForcedToolTest`, `AnthropicTimeoutTest`, `AnthropicDecoderTest`, `AnthropicStrictTest`
- [ ] `.../http/CallAwaitTest`, `CleanClientTest`
- [ ] `.../anthropic/AnthropicPipelineRetryTest` (SC3 no-duplicate commit through the command pipeline)
- [ ] `.../anthropic/AnthropicCanaryTest` (TEL-04)
- [ ] extend `OkHttpVersionGuardTest` (mockwebserver jar version per leg)
- [ ] `ModelCapabilities` additive field + `ModelCapabilityTableTest` cases (core)
- [ ] test helper: JSON fixture builders for Anthropic success/error bodies (private to the test source set)

No new framework install.

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Live Anthropic capture (usage fields, `request-id`, cache read on a >=4096-token prefix, 401 shape) | PROV-04/05, PROV-07 | needs a real key and network; must stay outside `check` | `with-test-keys --only anthropic -- env VAE_LIVE_ANTHROPIC=1 ./gradlew :providers:liveAnthropicCapture --offline`; Haiku 4.5 only, at most 6 calls; opt-in, never a dependency of `check` |

---

## Validation Sign-Off

> **Plan-time state is a DRAFT.** Frontmatter stays `status: draft` and `nyquist_compliant: false`;
> they are finalized only post-execution by the Nyquist finalizer.

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 240s
- [ ] _(finalizer-only, post-execution)_ `nyquist_compliant` - leave `false` at plan time

**Approval:** pending (finalizer-owned, not set at plan time)
