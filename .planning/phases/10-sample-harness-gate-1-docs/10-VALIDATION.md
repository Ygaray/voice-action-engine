---
phase: "10"
slug: "sample-harness-gate-1-docs"
# status lifecycle: draft (seeded by plan-phase) → validated (set by validate-phase §6)
status: validated
nyquist_compliant: true
wave_0_complete: true
created: "2026-10-01"
---

# Phase 10 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 4.13.2 + kotlinx-coroutines-test, `:core` testFixtures (FakeAiProvider, ScriptedGate, RecordingCommitSink); host shell verifiers (`scripts/verify-*.sh`) |
| **Config file** | `sample/build.gradle.kts` (Wave 1 adds Compose + test deps); existing `scripts/verify-*.sh` pattern |
| **Quick run command** | `./gradlew :sample:testDebugUnitTest --offline -q` |
| **Full suite command** | `./gradlew check --offline && scripts/verify-repo-hygiene.sh && scripts/jitpack-dry-run.sh && scripts/verify-sample-device-guard.sh` |
| **Estimated runtime** | ~240 seconds (full), ~40 seconds (quick) |

---

## Sampling Rate

- **After every task commit:** Run the quick run command (plus the task's own verifier script when it adds one)
- **After every plan wave:** Run the full suite command (one plan per wave, so gradle never runs concurrently)
- **Before `/gsd-verify-work`:** Full suite green; Gate-1 (TESTER, `device-hw:` criteria) is run afterwards by the agentic tester
- **Max feedback latency:** 300 seconds

---

## Per-Task Verification Map

Audited 2026-10-01 at HEAD 6ee25b7. Host suite re-run: `:sample:testDebugUnitTest --offline` = 143 tests, 0 failures, 0 skipped (20 classes + docs/DocSnippetsTest); `verify-sample-device-guard.sh` OK scenarios=33; `verify-docs-coverage.sh` OK checks=23; `verify-repo-hygiene.sh` OK.

| Requirement / Decision | Behavior | Evidence (automated) | Device / live evidence | Status |
|---|---|---|---|---|
| VER-01 fixture (D-05) | Absent -> typed Absent at run time; wrong sha -> ShaMismatch; good -> parse | `FixtureLoaderTest`, `jitpack-dry-run.sh` | G1-01, G1-02 passed | green |
| VER-01 fake executor | read -> Finished(READ); mutating -> canned Mutation | `CannedToolExecutorTest`, `SyntheticToolsTest` | G1-12 | green |
| VER-01 key via :keystore | save/read/delete/last4; importer deletes plaintext | `KeyVaultTest`, `PlaintextScanTest`, `verify-sample-device-guard.sh` | G1-04, G1-05, G1-14 passed | green |
| VER-01 OkHttp pin | runtime VERSION == 5.2.1 | `OkHttpPinTest` | G1-03 passed | green |
| VER-02 | Verdict bands, reads == write | `CacheVerdictTest`, `AgenticLegTest` | G1-06 passed (`evidence/gate1-ver02.txt`) | green |
| VER-03 Anthropic / OpenAI | parsed tool + optional-absent rules | `SmokeVerdictTest`, `SmokeLegTest` | G1-07, G1-08 passed | green |
| VER-03 OpenRouter single-shot | Reported arg keys are exactly what the model returned (no default-filling); filled optional -> INCONCLUSIVE, not PASS | `SmokeVerdictTest.aFilledOptionalIsInconclusive`, `SmokeLegTest.aFilledOptionalIsInconclusiveAndTheRerunUsesTheStrongerPrompt` / `theAnthropicEditSmokePassesWithOptionalsAbsent`; shared decoder passed live OpenAI omitted-optional (G1-08) | G1-09 partial: INCONCLUSIVE `model_filled_optional` x2, ACCEPTED BY EVIDENCE by orchestrator (C5), NOT a PASS; Phase 11 waiver packet beside C4 | accepted-inconclusive (manual-only)
| VER-03 ext. multi-turn | tool-result replay OpenAI Chat / OpenRouter | `MultiTurnLegTest` | G1-10, G1-11 passed | green |
| D-02 evidence | closed vocabulary, canary absent, key-shape scan | `EvidenceLineTest`, `RequestBudgetTest`, `sample-evidence-filter.sh` | G1-13 spend bounded | green |
| D-03 / D-04 | cold stamp, TESTER-only guard, no key on argv | `verify-sample-device-guard.sh` (33 scenarios), `CacheVerdictTest` | G1-06 cold=yes, G1-14 | green |
| VER-04 docs + D-14 | coverage checklist, Clarification/partial rendering | `verify-docs-coverage.sh`, `DocSnippetsTest`, `ClarificationFlowTest`, `OutcomeTextTest`, `SampleViewModelTest` | G1-12 passed | green |
| VER-04 wiring (D-07) | fresh agent wires engine from the docs | `agent-wiring-test.sh` (PASS on 338d85ffa3; isolated rerun on 36c578f464: `WIRING TEST: PASS checks=9`) | `10-WIRING-TEST.md` status: pass; `evidence/wiring-rerun-stumbles.txt`, `evidence/wiring-rerun-consulted.txt` (3 minor stumbles). Any later doc edit voids it and needs a rerun | green

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [x] `sample/build.gradle.kts` — Compose + `testImplementation(testFixtures(project(":core")))` + junit/coroutines-test
- [x] `sample/src/test/resources/` synthetic (committed, non-SB) fixture
- [x] `scripts/verify-sample-device-guard.sh` — fake-adb scenarios (mirror of the keystore verifier)
- [x] `scripts/verify-docs-coverage.sh` — doc coverage grep

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Real AndroidKeyStore round trip, OkHttp 5.x variant, live cache hit on the TESTER | VER-01, VER-02 | Needs the device and live keys; Gate-1 is agentic (`gsd-agentic-tester`), not an executor task | GATE1-RUNBOOK.md; checkpoint approved/deferred via the master |
| Live single-shot + multi-turn smokes on three clouds | VER-03 | Live network + keys | GATE1-RUNBOOK.md legs L2-L6 |
| OpenRouter optional-absent (G1-09, carry C5) | VER-03 | Model-dependent; INCONCLUSIVE twice. Orchestrator accept-C5: INCONCLUSIVE(model_filled_optional) ACCEPTED BY EVIDENCE, not a PASS; in Phase 11 waiver packet beside C4 | Optional Gate-2 retry with another model/prompt |
| Agent wiring (D-07) | VER-04 | RESOLVED: isolated rerun passed on 36c578f464 | Rerun only if Phase 11 touches README/INTEGRATION/API/ECOSYSTEM |
| Low-credit 400 -> Billing, Responses-only 400 text, OpenRouter optional-absent | Gate-2 carry | Not triggerable without draining credit / model behavior | Registered in the uat-pending fragment |

---

## Validation Sign-Off

> **Plan-time state is a DRAFT.** Leave frontmatter `status: draft` and `nyquist_compliant: false`.
> These are finalized ONLY post-execution by the Nyquist finalizer (the `verify:post` →
> `validate-phase` hook, invoked by execute-phase `finalize_nyquist_validation` after Gate-1). Never
> set `nyquist_compliant: true` — or otherwise "sign off" compliance — at plan time, and do not let
> the plan-checker do so (INC-2026-07-27-01: a premature plan-time flip is what caused inconsistent
> COMPLIANT/PARTIAL milestone-audit states).

- [x] All tasks have `<automated>` verify or Wave 0 dependencies
- [x] Sampling continuity: no 3 consecutive tasks without automated verify
- [x] Wave 0 covers all MISSING references
- [x] No watch-mode flags
- [x] Feedback latency < 300s
- [x] _(finalizer-only, post-execution)_ `nyquist_compliant` — leave `false` at plan time; the
      finalizer sets `true` iff its gap analysis finds zero gaps

**Approval:** finalized 2026-10-01. `nyquist_compliant: true`: host automation has zero gaps; D-07 wiring rerun is PASS on 36c578f464; VER-03 OpenRouter optional-absent (G1-09, C5) is an orchestrator-accepted INCONCLUSIVE (documented, evidence-backed, NOT a PASS, listed in the Phase 11 waiver packet beside C4), treated as a resolved manual-only item. Correction (orchestrator spot-check): host tests DO drive the shared Chat decoder with omitted-optional responses on both vendors (OpenAI and OpenRouter): `providers/src/test/kotlin/io/github/ygaray/voiceactionengine/providers/chat/ChatAbsentOptionalTest.kt` (forced EDIT-shaped call through the pipeline and the real Chat provider, absent keys asserted from the response manifest) and `ChatGoldenReplayTest.omittedOptionalKeysStayAbsentInTheDecodedArguments`. Together with the verdict classifier tests and the live OpenAI pass, the no-default-filling claim is backed.
