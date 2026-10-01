---
phase: "3"
slug: "transcript-types-providerrouter-on-device-gate"
# status lifecycle: draft (seeded by plan-phase) -> validated (set by validate-phase §6)
status: draft
nyquist_compliant: false
wave_0_complete: false
created: "2026-09-30"
---

# Phase 3 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.
> Source of truth for the requirement-to-test map is the Validation Architecture section of 03-RESEARCH.md; the Per-Task Verification Map below is filled by the plans (task ids) and finalized post-execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 4.13.2 + kotlinx-coroutines-test 1.11.0 (`runTest`) |
| **Config file** | `core/build.gradle.kts`; detekt `config/detekt/detekt.yml` |
| **Quick run command** | `./gradlew :core:test --tests '*<ClassJustTouched>' -q` |
| **Full suite command** | `./gradlew check` plus `scripts/review-api-surface.sh --expect-sealed-complete` |
| **Estimated runtime** | ~120 seconds |

---

## Sampling Rate

- **After every task commit:** Run the quick command for the touched test class, plus `./gradlew :core:detekt :core:scanBannedConstructs -q`
- **After every plan wave:** Run `./gradlew :core:check -q`
- **Before `/gsd-verify-work`:** Full suite must be green (`./gradlew check`, API-surface script, `scripts/verify-negative-controls.sh && scripts/verify-repo-hygiene.sh && scripts/verify-api-dump.sh`)
- **Max feedback latency:** 150 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 3-xx-xx | filled by planner | — | PROV-01 | T-3 info-disclosure | transcript types / NativeReplay redacted `toString` | unit | `./gradlew :core:test --tests '*TranscriptTypesTest' --tests '*NativeReplayTest' -q` | ❌ W0 | ⬜ pending |
| 3-xx-xx | filled by planner | — | PROV-02, PROV-03 | T-3 key isolation | selection once per tier-run; NotConfigured with zero calls; key never crosses providers | unit | `./gradlew :core:test --tests '*ProviderRouterTest' --tests '*KeyIsolationTest' -q` | ❌ W0 | ⬜ pending |
| 3-xx-xx | filled by planner | — | PROV-10 | T-3 policy bypass | fallback re-checked vs offlineOnly/allowedProviders; one gate | unit | `./gradlew :core:test --tests '*OnDeviceGateTest' -q` | ❌ W0 | ⬜ pending |
| 3-xx-xx | filled by planner | — | TEL-03 | — | CacheNotEngaged matrix, silent below/unknown minimum | unit | `./gradlew :core:test --tests '*CacheNotEngagedTest' -q` | ❌ W0 | ⬜ pending |
| 3-xx-xx | filled by planner | — | CLN-03, CLN-04 | — | no model-id/limit literals; no settings reads | unit (source scan) | `./gradlew :core:test --tests '*NoHardCodedConstantsTest' -q` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `FakeAiProvider` (+ scripted credential/selection sources) in `core` testFixtures — ROADMAP SC5 harness
- [ ] Test classes: `TranscriptTypesTest`, `NativeReplayTest`, `ProviderRouterTest`, `KeyIsolationTest`, `OnDeviceGateTest`, `ModelCapabilityTableTest`, `CacheNotEngagedTest`, `NoHardCodedConstantsTest`, `FakeAiProviderTest`
- [ ] Edit `scripts/review-api-surface.sh` allowed-sealed list if `Message`/`AssistantPart` are sealed

*No framework install needed.*

---

## Manual-Only Verifications

All phase behaviors have automated verification (pure JVM, no HTTP, no device).

---

## Validation Sign-Off

> **Plan-time state is a DRAFT.** Leave frontmatter `status: draft` and `nyquist_compliant: false`.
> These are finalized ONLY post-execution by the Nyquist finalizer (the `verify:post` ->
> `validate-phase` hook, invoked by execute-phase `finalize_nyquist_validation` after Gate-1). Never
> set `nyquist_compliant: true` — or otherwise "sign off" compliance — at plan time, and do not let
> the plan-checker do so (INC-2026-07-27-01).

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 150s
- [ ] _(finalizer-only, post-execution)_ `nyquist_compliant` — leave `false` at plan time; the
      finalizer sets `true` iff its gap analysis finds zero gaps

**Approval:** pending — finalizer-owned, not set at plan time
