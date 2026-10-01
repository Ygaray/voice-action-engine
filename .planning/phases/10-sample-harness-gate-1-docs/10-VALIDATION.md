---
phase: "10"
slug: "sample-harness-gate-1-docs"
# status lifecycle: draft (seeded by plan-phase) → validated (set by validate-phase §6)
status: draft
nyquist_compliant: false
wave_0_complete: false
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

The per-task map is finalized post-execution by the Nyquist finalizer from the executed PLAN.md `<automated>` blocks. The requirement-level plan-time map:

| Requirement / Decision | Behavior | Test Type | Automated Command / Check | Where |
|------------------------|----------|-----------|---------------------------|-------|
| VER-01 fixture (D-05) | absent -> typed Absent (never at Gradle configuration); wrong sha -> ShaMismatch; good -> parse | unit + script | `:sample:testDebugUnitTest --tests '*FixtureLoaderTest'`; `scripts/jitpack-dry-run.sh` | host |
| VER-01 fake executor | read -> Finished(READ); mutating -> Mutation with canned apply | unit | `*CannedToolExecutorTest` | host |
| VER-01 key via :keystore | save/read/delete/Ready(last4); importer deletes plaintext; plaintext self-scan false | unit + device | `*TestKeyImporterTest`; Gate-1 UI step; `run-as ls files/test-keys` empty | host + device-hw |
| VER-01 OkHttp pin | runtime VERSION == 5.2.1 (reflective) | unit + device log | `*OkHttpPinTest`; env line `okhttp=5.2.1` | host + device-hw |
| VER-02 | CacheVerdict PASS/WARM/FAIL, band edges, reads == write | unit | `*CacheVerdictTest` | host |
| VER-02 | real cold Haiku 4.5 run, >=2 turns, prefix + min cacheable logged | live | Gate-1 L1 via UI -> `evidence/ver02.log` | device-hw |
| VER-03 | parsed tool + optional-absent / INCONCLUSIVE rules; Anthropic INITIAL 200, no reshape | unit + live | `*SmokeVerdictTest`; L2-L4 | host + device-hw |
| VER-03 ext. | >=2-turn tool-result replay on OpenAI Chat and OpenRouter | unit + live | `*MultiTurnLegTest`; L5, L6 | host + device-hw |
| D-02 evidence | closed-vocabulary lines, canary never appears, allow-list + key-shape scan | unit + shell | `*EvidenceLineTest`; redaction negative control | host |
| D-04 keys / device rules | TESTER-only guard, no key literal on argv | shell | `scripts/verify-sample-device-guard.sh` | host |
| D-03 cold | warm window refused, WARM classified INFRA | shell + unit | cold-stamp case in guard verifier; `*CacheVerdictTest` | host |
| VER-04 + D-14 | docs coverage checklist, Clarification flow | script + unit | `scripts/verify-docs-coverage.sh`; `*ClarificationFlowTest` | host |
| VER-04 wiring (D-07) | fresh agent wires the engine from 3 docs | script + agent | `scripts/agent-wiring-test.sh <sha>` | host + network |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `sample/build.gradle.kts` — Compose + `testImplementation(testFixtures(project(":core")))` + junit/coroutines-test
- [ ] `sample/src/test/resources/` synthetic (committed, non-SB) fixture
- [ ] `scripts/verify-sample-device-guard.sh` — fake-adb scenarios (mirror of the keystore verifier)
- [ ] `scripts/verify-docs-coverage.sh` — doc coverage grep

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Real AndroidKeyStore round trip, OkHttp 5.x variant, live cache hit on the TESTER | VER-01, VER-02 | Needs the device and live keys; Gate-1 is agentic (`gsd-agentic-tester`), not an executor task | GATE1-RUNBOOK.md; checkpoint approved/deferred via the master |
| Live single-shot + multi-turn smokes on three clouds | VER-03 | Live network + keys | GATE1-RUNBOOK.md legs L2-L6 |
| Low-credit 400 -> Billing, Responses-only 400 text, OpenRouter optional-absent | Gate-2 carry | Not triggerable without draining credit / model behavior | Registered in the uat-pending fragment |

---

## Validation Sign-Off

> **Plan-time state is a DRAFT.** Leave frontmatter `status: draft` and `nyquist_compliant: false`.
> These are finalized ONLY post-execution by the Nyquist finalizer (the `verify:post` →
> `validate-phase` hook, invoked by execute-phase `finalize_nyquist_validation` after Gate-1). Never
> set `nyquist_compliant: true` — or otherwise "sign off" compliance — at plan time, and do not let
> the plan-checker do so (INC-2026-07-27-01: a premature plan-time flip is what caused inconsistent
> COMPLIANT/PARTIAL milestone-audit states).

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 300s
- [ ] _(finalizer-only, post-execution)_ `nyquist_compliant` — leave `false` at plan time; the
      finalizer sets `true` iff its gap analysis finds zero gaps

**Approval:** pending — finalizer-owned, not set at plan time
