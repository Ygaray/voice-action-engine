---
phase: "11"
slug: "cut-v1-0-0"
status: validated
nyquist_compliant: true
wave_0_complete: true
created: "2026-10-02"
---

# Phase 11 — Validation Strategy

> Release-cut phase: the "tests" are shell gates and committed evidence, not unit tests. Tag v1.0.0 is cut and immutable; nothing here modifies tags or pushes.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | Bash gates (`scripts/release-cut.sh gate|selftest|preflight|cut`), `scripts/verify-*.sh`, Metalava `apiCheck` baselines |
| **Quick run command** | `scripts/release-cut.sh gate leak && scripts/release-cut.sh gate tags-absent v1.0.0` (tags-absent is now expected red, tag exists) |
| **Full suite command** | `./gradlew check apiCheck --offline && scripts/verify-repo-hygiene.sh && scripts/verify-docs-coverage.sh && scripts/release-cut.sh selftest negative` |
| **Estimated runtime** | long (selftest negative + gradle check); not re-run in this audit |

---

## Per-Task Verification Map

Audited 2026-10-02 at HEAD 361cd51. Re-checked live (cheap): tags, ls-remote, `gate leak`, api.txt vs reviewed signatures, Suppress scan. Long selftests relied on committed evidence.

| Plan | Requirement | Behavior | Evidence (automated) | Status |
|---|---|---|---|---|
| 11-01 | VER-05 | Release-cut gates (tag-format, tags-absent, create-tag, clean, pushed, wiring, diff, waiver, check, api-dump, hygiene, api-check, dry-run, leak, version) and sandbox happy path | `evidence/release-cut-happy.txt`: `RELEASE SELFTEST OK happy=1 negatives=0 positives=0` | green |
| 11-01/02 | VER-05 | Every gate goes red for the right reason (31 negatives, 2 positives, 0 FAIL, real-repo guard unchanged) | `evidence/release-cut-selftest.txt`: `RELEASE SELFTEST OK happy=1 negatives=31 positives=2` | green |
| 11-02 | VER-05 | Interim API reviewed and frozen | `11-API-REVIEW.md`, `evidence/interim-api/*.api.sig`; live: `core|providers|keystore/api.txt` tracked and byte-equal to reviewed sigs | green |
| 11-03 | VER-05 | Docs gate (wiring selftest, doc coverage, hygiene, API unchanged) | `evidence/docs-gate.txt` | green |
| 11-04 | VER-05 | Preconditions audit, single justified `@Suppress` | `evidence/preconditions-audit.txt`; live scan: only `core/.../internal/Guarded.kt` | green |
| 11-05 | VER-05 | Limits pin (`defaultsAreTheContractLimits`) | `evidence/limits-reverify.txt` | green |
| 11-06 | VER-05 | Waiver packet C-rows answered, C5 "NOT a PASS" | `11-WAIVER-PACKET.md` (accepted), `11-06-CHECKPOINT.md` | green |
| 11-07 | VER-05 | Baseline apiCheck + JitPack probe of baseline commit | `evidence/baseline.txt` (BASELINE: PASS), `evidence/baseline-jitpack.txt` | green |
| 11-08 | VER-05 | Preflight (15 gates) then annotated tag cut, peels to approved commit locally and on origin | `evidence/preflight-v1.0.0.txt`, `evidence/cut-v1.0.0.txt` (`CUT VERIFIED`); live: `v1.0.0` is sole tag, peels to efc060f8, matches origin | green |
| 11-09 | VER-05 | JitPack resolves all three coordinates at v1.0.0; ledger row draft | `evidence/11-JITPACK-VERIFY.log` (`JITPACK v1.0.0: VERIFIED commit=efc060f8`), `11-LEDGER-ROW.md` | green |
| wiring | VER-05 | Docs wiring rerun | `11-WIRING-RERUN.md` status pass, `evidence/wiring-sha-jitpack.txt` | green |

*Status: green = automated command or committed evidence verifies it*

---

## Wave 0 Requirements

- [x] `scripts/release-cut.sh` (gate/selftest/preflight/cut) with sandbox selftest
- [x] `scripts/api-dump-isolated.sh`, `scripts/review-api-surface.sh`
- [x] `scripts/verify-repo-hygiene.sh`, `scripts/verify-docs-coverage.sh`, `scripts/jitpack-dry-run.sh`

---

## Warnings (non-blocking)

- `gate leak` reports `content_check=skipped(no local fixture)`: the fixture-content leak scan did not run on this host; only the key-shape scan did. Not a gap for the cut (evidence from the cut-time run stands), but re-run where the fixture exists if a patch tag is ever cut.
- The `tags-absent` PLAN verify and "no tag exists" clauses in early PLAN commands are intentionally stale after the cut; they are pre-cut guards and cannot be re-run as written.
- Long selftests were not re-executed in this audit; green status rests on committed evidence plus the cheap live re-checks above.

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Waiver packet acceptance (C-rows incl. C4/C5) | VER-05 | Orchestrator/human decision | Read `11-WAIVER-PACKET.md`; `packet_status: accepted` |
| Ledger row landing in the control plane (§11) | VER-05 | Contract edits only via control plane relay | Orchestrator applies `11-LEDGER-ROW.md` |

---

## Validation Sign-Off

- [x] VER-05 has automated or committed-evidence coverage in every plan
- [x] No tag modified, nothing pushed by this audit
- [x] `nyquist_compliant: true`

**Approval:** validated 2026-10-02
