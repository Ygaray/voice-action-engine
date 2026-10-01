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
| 03-01-T1..T3 | 03-01 | 1 | PROV-01 | T-03-01, T-03-02 | transcript types / NativeReplay redacted `toString`; sealed set of seven | unit + script | `./gradlew :core:test --tests '*TranscriptTypesTest' --tests '*NativeReplayTest' -q` | ❌ W0 | ⬜ pending |
| 03-02-T1..T2 | 03-02 | 1 | TEL-03 (inputs) | T-03-08 | capability lookup: override over provider default over unknown default, exact ids | unit | `./gradlew :core:test --tests '*ModelCapabilityTableTest' -q` | ❌ W0 | ⬜ pending |
| 03-02-T3 | 03-02 | 1 | CLN-03, CLN-04, PROV-10 | T-03-06, T-03-07, T-03-43 | no model-id/limit literals; no settings reads; no on-device implementation tokens (SC3); positive controls | unit (source scan) | `./gradlew :core:test --tests '*NoHardCodedConstantsTest' -q` | ❌ W0 | ⬜ pending |
| 03-03-T1..T3 | 03-03 | 1 | PROV-02, PROV-10, CLN-04 | T-03-10, T-03-12 | typed credential lookup, CredentialUnreadable, fallback only on ON_DEVICE, ML Kit statuses | unit | `./gradlew :core:test --tests '*SeamTypesTest' --tests '*FailureTaxonomyTest' -q` | ❌ W0 | ⬜ pending |
| 03-04-T1..T2 | 03-04 | 1 | PROV-10, PROV-02 | T-03-15 | fallbackFrom in TurnRecord/TierAttempt; thirteen router trace codes | unit | `./gradlew :core:test --tests '*TraceTest' -q` | ✅ extend | ⬜ pending |
| 03-05-T1..T3 | 03-05 | 2 | PROV-01, PROV-02 | T-03-18, T-03-19 | AiProvider contract, FakeAiProvider loud on exhaustion, ToolSpec.strict binary-compatible | unit | `./gradlew :core:test --tests '*FakeAiProviderTest' --tests '*ApiShapeTest' --tests '*ToolSpecClarificationTest' -q` | ❌ W0 | ⬜ pending |
| 03-06-T1..T3 | 03-06 | 3 | PROV-02, PROV-10 | T-03-23, T-03-24, T-03-26..T-03-28 | bind refusals with zero calls and one trace code; key mismatch refused; on-device without fallback loud; capability check before call; provider faults typed | unit | `./gradlew :core:test --tests '*ModelRouterTest' -q` | ❌ W0 | ⬜ pending |
| 03-07-T1..T3 | 03-07 | 4 | PROV-02, PROV-03, PROV-10, CLN-04 | T-03-25, T-03-44..T-03-46 | selection once per tier-run; frozen under turns/tiers/mid-command change/concurrency; one probe instance shared with the pre-check; build-time override validation | unit | `./gradlew :core:test --tests '*ProviderRouterTest' -q` | ❌ W0 | ⬜ pending |
| 03-08-T1..T2 | 03-08 | 5 | PROV-10 | T-03-29, T-03-31 | fallback re-checked vs offlineOnly/allowedProviders/declaration; one gate; no on-device implementation code | unit | `./gradlew :core:test --tests '*OnDeviceGateTest' --tests '*TierPolicyTest' --tests '*NoHardCodedConstantsTest' -q` | ❌ W0 | ⬜ pending |
| 03-08-T3 | 03-08 | 5 | PROV-02 | T-03-30 | key never crosses providers (tiers, commands, hostile source, fallback) | unit | `./gradlew :core:test --tests '*KeyIsolationTest' -q` | ❌ W0 | ⬜ pending |
| 03-09-T1..T3 | 03-09 | 5 | TEL-03 | T-03-34..T-03-37 | CacheNotEngaged matrix, boundaries, silent below/unknown minimum, one event per response | unit | `./gradlew :core:test --tests '*CacheNotEngagedTest' -q` | ❌ W0 | ⬜ pending |
| 03-10-T1..T3 | 03-10 | 6 | all seven | T-03-38..T-03-42 | routed-path canary; full gate; sealed-complete review; constructor audit | unit + gate | `./gradlew check` then `scripts/review-api-surface.sh --expect-sealed-complete` | ✅ extend | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `FakeAiProvider` (+ scripted credential/selection sources) in `core` testFixtures — ROADMAP SC5 harness
- [ ] Test classes: `TranscriptTypesTest`, `NativeReplayTest`, `ModelRouterTest`, `ProviderRouterTest`, `KeyIsolationTest`, `OnDeviceGateTest`, `ModelCapabilityTableTest`, `CacheNotEngagedTest`, `NoHardCodedConstantsTest`, `FakeAiProviderTest`, `SeamTypesTest` (each created by the plan that owns it; see the map above)
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
