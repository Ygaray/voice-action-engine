---
phase: "18"
slug: "voice-adapter"
status: verified
threats_open: 0
asvs_level: 1
audited_head: d4416fc340aeaf8bb2f8af5e96f13019a02e4ade
created: "2026-10-07"
---

# Phase 18 — Security

> Per-phase security contract: threat register, accepted risks, and audit trail. Audited by gsd-security-auditor against HEAD d4416fc (post code-review fixes). No Gradle was run for the audit; heavy-gate evidence is 18-QUIET-WINDOW.md (taken at e15bd36). Later commits changed only KDoc, README, one private refactor and one new test.

---

## Trust Boundaries

| Boundary | Description | Data Crossing |
|----------|-------------|---------------|
| JitPack host to Gradle resolution | exact-group exclusiveContent filter plus FAIL_ON_PROJECT_REPOS | :stt artifact coordinates |
| :stt types to the published adapter artifact | compileOnly, enforced by publication and confinement gates | stt types |
| Speech transcript to CommandInput | verbatim; string forms print lengths only | transcript (sensitive) |
| :stt FinalSegment to CommandInput | label goes through the closed en/es/null normalizer | language label |
| Public API to the v1.1.0 tag | frozen, additive-only, pinned by reflection test and dump review | public signatures |
| Build files and settings to dependency resolution | textual confinement gate | repo declarations |
| Published POM and module metadata to consumer resolution | no :stt in the published set | POM/module metadata |
| Engine events to app logs and telemetry | ActionEvent and ExecutedAction toString | ids, counts (no user text) |
| Heavy gates to the shared host | earlyoom, guarded by the relayed quiet window | host memory |
| Relay to grant line | only the orchestrator's answer opens a window | grant state |
| Public docs to integrators | consumer repo snippet, never-log-a-segment guidance | integration guidance |

---

## Threat Register

| Threat ID | Category | Component | Severity | Disposition | Mitigation / Evidence | Status |
|-----------|----------|-----------|----------|-------------|-----------------------|--------|
| T-18-01 | Tampering | :stt coordinate resolution | high | mitigate | settings.gradle.kts:16,20-23 FAIL_ON_PROJECT_REPOS + one exclusiveContent exact group; libs.versions.toml:13,29 pinned v0.7.0 | closed |
| T-18-02 | Elevation of privilege | :stt leaking to consumers or :core | high | mitigate | voice-adapter/build.gradle.kts:37,39 compileOnly + testImplementation; invariants.gradle.kts:277,341-375 verifySttConfined; negative control "adapter publishes the speech engine" red | closed |
| T-18-03 | Information disclosure | transcript via toString, logs, exceptions | high | mitigate | LanguageLabels.kt:54-65, FinalSegmentMapping.kt:36-38 no stringification; invariants banned rules; detekt ForbiddenImport android.util.Log; DI import (voice-adapter) plant red | closed |
| T-18-04 | Tampering | mislabeled language | medium | mitigate | LanguageLabels.kt:17-24 closed set else null, no default; LanguageLabelsTest | closed |
| T-18-SC | Tampering | external artifact install | high | mitigate | owner's JitPack mirror pinned to immutable tag, confined by exclusiveContent; no npm/pip/cargo manifest | closed |
| T-18-10 | Tampering | docs overstate language detection | medium | mitigate | INTEGRATION.md:896-905 labels pass through, null means unknown | closed |
| T-18-11 | Tampering | docs steer to unsafe repo or aggregator | high | mitigate | INTEGRATION.md:922-926,942; ECOSYSTEM.md:22; hygiene check green | closed |
| T-18-12 | Information disclosure | docs example logs a segment | high | mitigate | INTEGRATION.md:907 "Never log a segment"; grep clean | closed |
| T-18-13 | Repudiation | roadmap versus docs wording | low | accept | AR-18-01 | closed |
| T-18-20 | Information disclosure | transcript leak, both entry points | high | mitigate | RedactionTest.kt:19-46 sentinel tests with positive control | closed |
| T-18-21 | Tampering | guessed or defaulted label | medium | mitigate | LanguageLabels.kt:17-24; RedactionTest.kt:28-34; never-outside-en/es/null loop | closed |
| T-18-22 | Elevation of privilege | stt type in the stt-free facade | medium | mitigate | LanguageLabels.kt:5 imports only :core; AdapterApiShapeTest; SttFreeFacadeTest (added in review fix WR-03) | closed |
| T-18-23 | Repudiation | public names frozen wrongly | low | accept | AR-18-02 | closed |
| T-18-24 | Denial of service | mapper throws on odd input | low | mitigate | LanguageLabels.kt total; RedactionTest.kt:48-65 | closed |
| T-18-30 | Elevation of privilege | :stt wired into other modules | high | mitigate | verify-stt-confinement.sh checks 3 and 5; verifySttConfined + verifyModuleGraph; selftest plants | closed |
| T-18-31 | Tampering | plain repo or second includeGroup | high | mitigate | verify-stt-confinement.sh:47-67; selftest plants plainrepo/aggregator | closed |
| T-18-32 | Tampering | aggregator or branch/SNAPSHOT pin | high | mitigate | verify-stt-confinement.sh:69-96; selftest plants branchpin/secondentry | closed |
| T-18-33 | Information disclosure | stt code in stt-free entry file | medium | mitigate | verify-stt-confinement.sh:132-144; SttFreeFacadeTest bytecode proof | closed |
| T-18-34 | Tampering | gate passes vacuously | medium | mitigate | selftest cases=12, exact counts + needle checks; re-run at HEAD | closed |
| T-18-40 | Information disclosure | ActionEvent / ExecutedAction toString | high | mitigate | CommitSink.kt:44-46,82-84 ids/kind/flags/counts only; ActionEventTest sentinels with positive controls; core main diff is KDoc only | closed |
| T-18-41 | Tampering | later edit adds an accessor or interpolation | medium | mitigate | sentinel tests; core/api.txt unchanged since v1.0.0 | closed |
| T-18-50 | Elevation of privilege | :stt published through :voice-adapter | high | mitigate | voice-adapter/build.gradle.kts:49-113 POM/module/classpath scans; plant red in quiet window | closed |
| T-18-51 | Elevation of privilege | another module resolves the :stt group | high | mitigate | invariants.gradle.kts:341-375; STT NEGATIVE CONTROLS OK plants=7 | closed |
| T-18-52 | Tampering | vacuous gate | medium | mitigate | non-vacuity assertions; clean-tree controls green | closed |
| T-18-53 | Tampering | plant left in a build file | medium | mitigate | verify-stt-negative-controls.sh trap restore + cmp; tree clean at HEAD | closed |
| T-18-60 | Tampering | wrong public surface frozen | high | mitigate | 18-SURFACE-REVIEW.md isolated dump; AdapterApiShapeTest; API DUMP PROOF OK | closed |
| T-18-61 | Denial of service | heavy gate on a busy host | high | mitigate | free -h recorded first; heavy gates deferred to the plan-08 window | closed |
| T-18-62 | Elevation of privilege | self-authored grant (plan 07) | high | mitigate | grant: pending, relayed_by empty at 5e491b8 | closed |
| T-18-63 | Tampering | seed proof silently skipped for AAR | medium | mitigate | verify-api-seed.sh:25-31,76-78,89-92 | closed |
| T-18-70 | Denial of service | earlyoom kills a build | high | mitigate | relayed window, pre-checks, MemAvailable never below 8.0 GiB, one run at a time, no kill, no retry | closed |
| T-18-71 | Elevation of privilege | self-authored grant (plan 08) | high | mitigate | RT-02 relayed (3c58f34) before grant open (2620976); verbatim relay recorded | closed |
| T-18-72 | Elevation of privilege | :stt leak undetected, gates not run | high | mitigate | negative-control failures: 0; DRY RUN OK, five artifacts, no :stt; :adapteralone probe recorded as Phase 19 obligation | closed |

*Status: open · closed · open — below high threshold (non-blocking)*

---

## Accepted Risks Log

| Risk ID | Threat Ref | Rationale | Accepted By | Date |
|---------|------------|-----------|-------------|------|
| AR-18-01 | T-18-13 | Roadmap and docs state the same single-segment wording (D-05); plan 07's docs-versus-dump check reads the real API so drift is detectable. | gsd-security-auditor (low severity, below block threshold) | 2026-10-07 |
| AR-18-02 | T-18-23 | Frozen public names recorded in 18-SURFACE-REVIEW.md and pinned by AdapterApiShapeTest; after v1.1.0 the surface may only grow additively. | gsd-security-auditor (low severity, below block threshold) | 2026-10-07 |
| AR-18-03 | (advisory, review WR-01) | ActionEvent.toString and CommandInput.toString print runId/parentRunId verbatim; they are opaque app-supplied ids and the KDoc tells integrators to keep user text out of them. Transcript, token, target ids, provider call id and context text are not printed. Low severity; WR-01 design call flagged for the operator before the v1.1.0 tag. | gsd-security-auditor (low severity, below block threshold) | 2026-10-07 |

---

## Carry-forward advisories (non-blocking)

- WR-02 residual: scripts/verify-stt-confinement.sh (and its --selftest) is not wired into release-cut, hygiene or negative-controls; it is the only automated guard for T-18-31/32/33 against later drift. Phase 19's gate run must run it and its --selftest by hand; Phase 20 wires it in.
- The 33-test voice-adapter run at c45d3b9 is a recorded isolated-worktree run (18-REVIEW-FIX.md); the full negative-control suite and :voice-adapter:check were not re-run on the final SHA. Phase 19/20 re-run them.
- Stale gitignored build output voice-adapter/build/publications/release/pom-default.xml still holds the plant-1 POM; the next verifyAdapterSttCompileOnly regenerates it.
- IN-02 latent false positive (adapter DI/ML scans read the :stt compile tree) is an accepted skip with an owner note.
- WR-04 overload trap (String third argument becomes context) is mitigated by KDoc only; a contract decision before v1.1.0.

---

## Security Audit Trail

| Audit Date | Threats Total | Closed | Open | Run By |
|------------|---------------|--------|------|--------|
| 2026-10-07 | 32 | 32 | 0 | gsd-security-auditor (via execute-phase secure_phase_gate) |

---

## Sign-Off

- [x] All threats have a disposition (mitigate / accept / transfer)
- [x] Accepted risks documented in Accepted Risks Log
- [x] `threats_open: 0` confirmed
- [x] `status: verified` set in frontmatter

**Approval:** verified 2026-10-07
