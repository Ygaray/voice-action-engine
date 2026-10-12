---
phase: "19"
slug: "sample-gate-1-docs"
status: verified
threats_open: 0
asvs_level: 1
audited_head: 13b1354e63d53bd62390a4e7f9fdef37c8382338
created: "2026-10-07"
---

# Phase 19 - Security

> Per-phase security contract: threat register, accepted risks, and audit trail.

---

## Trust Boundaries

| Boundary | Description | Data Crossing |
|----------|-------------|---------------|
| host scripts -> TESTER device | Gate-1 runner drives one device by explicit serial, with a relayed decision file for key push | adb commands, test API keys (by file reference, wiped after) |
| sample app -> committed evidence | Closed-vocabulary VAE_* evidence lines pass a Kotlin and shell allow-list filter before commit | evidence lines (no transcripts, tool args, titles, tier ids, keys) |
| repo -> isolated wiring workspace | Fresh agent gets four docs, a skeleton and TASK.md only | public docs |
| heavy gates -> shared host | Relayed quiet window under the orchestrator's build lock | memory, swap |
| published modules -> consumers | `:stt` confinement, additive API | Maven artifacts |

---

## Threat Register

The register was authored at plan time (T-19-01 .. T-19-50 plus T-19-SC repeated in all 14 plans; 64 rows, 51 unique IDs). All 51 were verified CLOSED by the security auditor on `audited_head` (ASVS L1, block_on high). Per-threat evidence (file:line) is in the audit trail below and in the auditor's return recorded in the execute-stage notes.

| Group | Threat IDs | Disposition | Status |
|-------|-----------|-------------|--------|
| Device and key handling (wrong device, key residue, self-authored GO, decision file) | T-19-01, 02, 03, 04, 21, 22, 23, 25 | mitigate | CLOSED |
| `:stt` confinement and release-cut integrity | T-19-05, 06, 07, 44 | mitigate | CLOSED |
| Adapter API shape and redaction | T-19-08, 09, 10 | mitigate | CLOSED |
| Evidence disclosure and vocabulary drift | T-19-11, 13, 15, 16, 19, 24, 28, 30, 32 | mitigate | CLOSED |
| Wallet (live-leg budget) | T-19-12, 14 | mitigate | CLOSED |
| Vacuous or drifting proofs (binding, router, undo, doc coverage, judge) | T-19-17, 18, 20, 29, 31, 33, 36 | mitigate | CLOSED |
| API review and baseline integrity | T-19-26, 27 | mitigate | CLOSED |
| Wiring isolation and records | T-19-34, 35, 42, 46 | mitigate | CLOSED |
| Edits after the wiring SHA, deferrals | T-19-37, 38, 43, 47, 50 | mitigate | CLOSED |
| Host starvation and quiet window | T-19-39, 40, 41, 45, 48, 49 | mitigate | CLOSED |
| Dependency installs | T-19-SC | accept | CLOSED (accepted) |

---

## Accepted Risks Log

| Risk ID | Description | Rationale | Accepted By | Date |
|---------|-------------|-----------|-------------|------|
| T-19-SC | No package installed in any plan | gradle/, build files, catalog and jitpack.yml have an empty diff since the phase base; the `:stt` pin predates the phase | plan-declared, auditor-verified | 2026-10-07 |

Unregistered observations (informational, not counted in threats_open): the debug `vae_autorun` intent is unauthenticated (pre-existing Phase 10, budget-bounded, sample is never published); WR-01 residual (any `.planning/` file whose first `decision:` line is `approved` can be named via the env var); `VAE_GATE1_EVIDENCE_DIR` is not confined (content still filtered); docs announce v1.1.0 before the tag exists (carry C11); `voice-adapter/api.txt` is header-only (dump and review before tagging); release-cut gate 7 stays red until Phase 20 re-runs wiring (carries C4, C10, C11).

---

## Security Audit Trail

| Audit Date | Threats Total | Closed | Open | Run By |
|------------|---------------|--------|------|--------|
| 2026-10-07 | 51 | 51 | 0 | gsd-security-auditor (verdict SECURED) |

The four code-review fixes (WR-01 .. WR-04: `scripts/run-sample-gate1.sh`, `scripts/verify-sample-device-guard.sh`, `scripts/verify-docs-coverage.sh`, `scripts/agent-wiring-test.sh`, `sample/.../undo/ItemToolExecutor.kt`) were re-checked on `audited_head`; none weakened a registered mitigation.
