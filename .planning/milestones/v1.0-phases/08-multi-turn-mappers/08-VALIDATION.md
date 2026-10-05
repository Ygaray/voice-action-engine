---
phase: "08"
slug: "multi-turn-mappers"
status: validated
nyquist_compliant: true
wave_0_complete: true
created: "2026-10-01"
---

# Phase 8 - Validation Strategy

> Per-phase validation contract for feedback sampling during execution. Source: 08-RESEARCH.md "Validation Architecture".

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 4.13.2 + kotlinx-coroutines-test 1.11.0 + legacy okhttp3.mockwebserver (all Phase 8 tests live in `:providers`) |
| **Config file** | `providers/build.gradle.kts` (matrix legs `testOkhttp521`, `testOkhttp550`; live tasks), `config/detekt/detekt.yml` |
| **Quick run command** | `./gradlew :providers:test --tests '*<Class>' --offline -q` |
| **Full suite command** | `./gradlew check --offline` |
| **Estimated runtime** | quick ~60 s, full check ~5 min |

---

## Sampling Rate

- **After every task commit:** quick command for touched classes + `./gradlew :providers:detekt :providers:scanBannedConstructs --offline -q`
- **After every plan wave:** `./gradlew :providers:check :core:check --offline` (includes the 4.12.0 / 5.2.1 / 5.5.0 legs)
- **Before `/gsd-verify-work`:** `./gradlew check --offline` green plus the Phase 5 repo scripts under `scripts/`
- **Max feedback latency:** ~300 seconds
- **Contention rule:** one Gradle invocation per working tree at a time; one plan per wave

---

## Per-Task Verification Map

| Requirement | Behavior | Test Type | Automated Command | File Exists |
|-------------|----------|-----------|-------------------|-------------|
| XCR-02 | Stamp mismatch (provider or model) gives typed `Failure(Other("replay_mismatch"))`, zero HTTP requests; null replay still rebuilds | unit + MockWebServer | `./gradlew :providers:test --tests '*ConversationCheckTest' --tests '*AnthropicTransportTest' --tests '*ChatTransportTest' --offline -q` | W0 (new ConversationCheckTest) |
| XCR-02 | Replay is canonical identity; thinking/redacted/signature/interleaved survive | conformance | `./gradlew :providers:test --tests '*MultiTurnConformanceTest' --offline -q` | W0 |
| XCR-03 | Coverage: missing/extra/dangling/duplicate/orphan results give typed failure, zero requests | unit + conformance | `./gradlew :providers:test --tests '*ConversationCheckTest' --tests '*MultiTurnConformanceTest' --offline -q` | W0 |
| XCR-03 | Anthropic: one user message, emission order, `is_error`; Chat: one `role:tool` per id, `{"error":...}` wrapper | unit | `./gradlew :providers:test --tests '*AnthropicEncoderTest' --tests '*ChatMessageEncoderTest' --offline -q` | exists (edited) |
| XCR-03 | Empty/absent/null/blank arguments become `{}`; non-object stays `MalformedToolArgs` | unit | `./gradlew :providers:test --tests '*ChatDecoderTest' --tests '*AnthropicDecoderTest' --offline -q` | exists (edited) |
| XCR-03 | Goldens: manifest well-formed, canonical/idempotent, hygiene, every row replays | unit | `./gradlew :providers:test --tests '*ConversationGoldenTest' --tests '*ConversationSanitizerTest' --offline -q` | W0 |
| XCR-01 | One suite, three dialects, parallel + zero-arg + error round trip | conformance | `./gradlew :providers:test --tests '*MultiTurnConformanceTest' --offline -q` | W0 |
| XCR-01 | Append-only (messages and tools/system prefix) and per-dialect cache directive; negative control fails | conformance | `./gradlew :providers:test --tests '*MultiTurnConformanceTest' --offline -q` | W0 |
| D-08 | Recorder: ceilings, shared id map, sanitize-then-replay, nothing written on refusal | unit (key-free) | `./gradlew :providers:test --tests '*ConversationCaptureRunTest' --offline -q` | W0 |
| D-12 | Live capture (opt-in, outside `check`) | manual, key-gated | see plan 08 | n/a |

*Task-level rows are refined by the plans' own `<automated>` blocks.*

---

## Wave 0 Requirements

- [x] `ConversationCheckTest` - XCR-02/03 (created test-first with the code)
- [x] `golden/conversations/` dir + `MANIFEST.tsv` + loader/hygiene
- [x] `conformance/` package (abstract suite + three dialect subclasses)

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Live multi-turn golden capture | XCR-03 | Needs real provider keys (`with-test-keys`), opt-in, outside `check` | EXECUTED 2026-10-01 under approve-capture (14 requests, about USD 0.033); 5 captured goldens now replay under `check`. Re-run only to refresh. |

---

## Validation Sign-Off

> Finalized post-execution by the Nyquist finalizer (2026-10-01): every requirement row has an existing, green automated test (`./gradlew check --offline` exit 0; providers 572 tests per OkHttp leg, 0 failures).

- [x] All tasks have `<automated>` verify or Wave 0 dependencies
- [x] Sampling continuity: no 3 consecutive tasks without automated verify
- [x] Wave 0 covers all MISSING references
- [x] No watch-mode flags
- [x] Feedback latency < 300 s
- [x] `nyquist_compliant: true` set in frontmatter (post-execution only)

**Approval:** validated 2026-10-01

## Validation Audit 2026-10-01
| Metric | Count |
|--------|-------|
| Gaps found | 0 |
| Resolved | 0 |
| Escalated | 0 |
