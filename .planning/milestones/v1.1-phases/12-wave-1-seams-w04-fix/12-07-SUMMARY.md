---
phase: 12-wave-1-seams-w04-fix
plan: 07
subsystem: docs
tags: [docs, integration-md, api-md, doc-snippets, key-access, docs-gate]
requires: [12-02, 12-03, 12-04, 12-06]
provides:
  - "keystore-fake region in DocSnippetsTest, with a JVM round-trip test through ApiKeyStore(dataStore, slots, keyAccess)"
  - "INTEGRATION.md section 7: software KeyAccess fake block (byte-equal to the region), tests-only prose, ProviderId wire-value line"
  - "INTEGRATION.md section 5 SingleShot-cannot-serve-reads sentence, section 10 runTest/JUnit imports and test dependencies, Notes cache and pre-call-refusal sentences"
  - "API.md names ReasoningMode, KeyAccess, DelicateKeyAccess and the new members (callId, providerCallId, carryIn, reasoning, onFailed, cappedByPolicy)"
  - "scripts/verify-docs-coverage.sh requires the keystore-fake region"
affects: [19, 20]
tech-stack:
  added: []
  patterns: ["a doc fake is a tested region: compiled and run in DocSnippetsTest, compared byte for byte by the docs gate"]
key-files:
  created: []
  modified:
    - sample/src/test/kotlin/io/github/ygaray/voiceactionengine/sample/docs/DocSnippetsTest.kt
    - INTEGRATION.md
    - API.md
    - scripts/verify-docs-coverage.sh
key-decisions:
  - "Research A8 confirmed: PreferenceDataStoreFactory resolves on the :sample unit-test classpath through :keystore's api DataStore dependency, so no testImplementation line was added"
  - "The fake's region has no call counters; the 'reading never creates a key' count assertion stays in :keystore's ApiKeyStoreKeyAccessTest, and the doc test asserts NotConfigured before and after a save for another provider"
requirements-completed: [DOC-01, SEAM-07]
status: complete
plan_head_before: 60112b3543b337de8976b6c6364c840778b4b0e3
commits: 3
metrics:
  completed: 2026-10-05
actuals:
  tokens: 5700
  tasks: 3
  commits: 3
---

# Phase 12 Plan 07: Docs for the Phase 12 seams and the three v1.0.1 stumbles Summary

INTEGRATION.md now carries a compiled, JVM-tested ~10-line software `KeyAccess` fake (region `keystore-fake`), fixes the three v1.0.1 wiring stumbles, and states why a single-tool SingleShot prefix will not cache on Haiku or OpenAI; API.md names every new public type and member, and the full docs-coverage gate is green (25 checks, 100 types).

## What changed

- **Task 1 (tracer)**: `MySoftwareKeys` (`@OptIn(DelicateKeyAccess::class)`, a `ConcurrentHashMap`, `KeyGenerator` AES 256, `existingKey` never creating) and `testKeyStore(dataStore, slots)` form the new region (15 non-blank lines). `theKeystoreFakeRoundTripsAKeyOnTheJvm` builds a preferences DataStore on a temp file, saves a fixed non-secret test key and reads back `KeyState.Ready("wxyz")`; a provider never saved stays `NotConfigured`. INTEGRATION.md section 7 gets a "Tests: software keys" bullet (tests only, production keeps the two-argument constructor, a fake must keep read-never-creates, in-memory keys are not fit for key custody), a text fence of the extra imports, and the marked fence. The docs gate lists `keystore-fake` as required (header comment now says eleven).
- **Task 2**: section 5 says a SingleShot tier cannot serve reads (read tools are AgenticLoop-only); section 7 says what a `ProviderId` prints (its wire value); section 10 adds `runTest`, `assertEquals` and `Test` imports and names `kotlinx-coroutines-test` and `junit`; Notes add direct OpenAI `-pro`/`-codex` ids refused before any call when tools are present, and the cache minimums (Haiku 4,096, OpenAI 1,024, claude-sonnet-5 1,024, claude-sonnet-5-5 512; values checked against `AnthropicModels` and `OpenAiModelRules`).
- **Task 3**: package list, surface tables (rows for `ReasoningMode`, `KeyAccess`, `DelicateKeyAccess`), `Extraction.callId`, `ExecutedAction.providerCallId`, `TierAttempt.carryIn`, `ModelRequest.reasoning`, `Unhandled(lastReason, cappedByPolicy)` with its meaning, `onFailed` and `reasoning` in the strategy builder lists, the `ApiKeyStore(dataStore, slots, keyAccess)` opt-in sentence, and an `Extraction(toolName, arguments, callId)` row in the shapes table. No kotlin fences added.

## Verification

- `./gradlew --offline -q -Dorg.gradle.workers.max=2 -Dorg.gradle.parallel=false :sample:testDebugUnitTest --tests '*DocSnippetsTest*'`: exit 0, 13 tests, 0 failures, `theKeystoreFakeRoundTripsAKeyOnTheJvm` among them.
- `scripts/verify-docs-coverage.sh`: `DOC COVERAGE OK checks=25 types=100`; the Task 1 and Task 2 subsets (C06, C07, C10, C14, C21, C25) also pass.
- All task acceptance criteria re-run and pass (marker lines, grep phrases, region size 15 lines, `OptIn(DelicateKeyAccess::class)` count 3, no `/home/` or `~/` path in INTEGRATION.md).
- Not run: the full multi-module Gradle build (docs and one test region only); no device or network use.

## Deviations from Plan

None - plan executed exactly as written. (Wording choice: the `toString()` phrase is written without backticks so the acceptance grep `toString() prints its wire value` matches.)

## Threat model

T-12-18 mitigated (prose says tests only and in-memory; the opt-in annotation is visible in the snippet). T-12-19: no key literal in any doc or region; the doc test's key is a fixed non-secret string marked `secret-scan: allow`, outside the region. T-12-20: the fake uses `KeyGenerator` AES 256 only. T-12-SC: no packages installed.

## Commits

- `9f43ffc` docs(12-07): add tested software KeyAccess fake to INTEGRATION.md section 7
- `b61e3bf` docs(12-07): fix the three v1.0.1 wiring stumbles and add the single-tool cache note
- `e3b7835` docs(12-07): name the Phase 12 public types and members in API.md

## Self-Check: PASSED

Modified files exist with the changes; commits `9f43ffc`, `b61e3bf` and `e3b7835` are in `git log`; `scripts/verify-docs-coverage.sh` exits 0.
