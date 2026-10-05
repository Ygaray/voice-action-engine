---
phase: "11"
slug: cut-v1-0-0
status: secured
threats_open: 0
asvs_level: 1
audited_head: 361cd510ca097e26270cccad0b7514ffd8f45635
created: "2026-10-02"
---

# Phase 11 - Security

Audited by gsd-security-auditor (ASVS 1, block_on high); persisted by the phase orchestrator from the structured verdict. 42 threats (T-11-01..T-11-42): 41 `mitigate` all CLOSED, 1 `accept` (T-11-04) CLOSED and logged below. Read-only audit. Tag v1.0.0 (annotated, object 343fd3f28676f4bf4bfb76a1b6ad0cb23b59bfaa) is on efc060f8fe462b71af2e4586b75a97db119ebabd; the only ref pushed is refs/tags/v1.0.0.

## Independent re-checks by the auditor

- Live `git ls-remote --tags origin` lists exactly `refs/tags/v1.0.0` and its peel (efc060f).
- Tag is annotated and peels to efc060f.
- Tagged core/providers/keystore `api.txt` are byte-identical to the reviewed interim signatures; sha256 match `evidence/baseline.txt`.
- `git diff v1.0.0 HEAD` touches only `.planning/`; CROSS-REPO-SCOPE-CONTRACT.md is unchanged.
- `release-cut.sh gate` create-tag, leak, prefreeze, waiver, wiring, diff all GATE OK at HEAD; `PRE_RELEASE=0 verify-repo-hygiene.sh` HYGIENE OK; `verify-docs-coverage.sh` DOC COVERAGE OK checks=23.
- Key-shape scan of the tagged tree: only `secret-scan: allow` fake canaries. Fixture-content check against the cross-repo fixture copy: 0 hits over 828 files at the tag.

## Threat disposition summary

| Group | Threats | Status | Key evidence |
|-------|---------|--------|--------------|
| Preconditions and evidence hygiene | T-11-01..04 | CLOSED | counts-only evidence; leak gate green; config.json and contract untouched |
| API and docs freeze | T-11-05..12 | CLOSED | ApiShapeTest/review-api-surface.sh, isolated api dump, cause-code mapping match, docs coverage C06/C07/C16/C23, docs API-identical to 11-02 |
| Release script safety | T-11-13..18, 40, 42 | CLOSED | single refspec push at release-cut.sh:713, no --tags/--force/--delete forms, sandboxed selftest (31 red + 2 green controls) with real-repo guard unchanged, fragment-assembled plants |
| Wiring rerun isolation | T-11-19..21 | CLOSED | throwaway config dir removed (cfg_removed=yes, master-attested), ancestors_clean=yes, workspace-only consulted files |
| Baseline | T-11-22..27, 37, 38 | CLOSED | baseline commit adds exactly three api.txt; compat tasks executed; prefreeze/ledger-only contract guards proven by selftest |
| The cut | T-11-28..32, 39 | CLOSED | approved==HEAD before/after preflight; peel assertions local and remote; no delete/move path; create_tag false |
| JitPack and ledger | T-11-33..36 | CLOSED | LIVE PROBE PASS ref=v1.0.0 before the ledger-row commit; row only in phase dir; contract unchanged |
| Waiver records | T-11-41 | CLOSED | all 13 rows answered (Yahir via orchestrator, 2026-10-02T17:33:27Z) |

## Accepted risks log

| ID | Risk | Why acceptable |
|----|------|----------------|
| T-11-04 | a stray tag created during the plan 11-01 audit | no plan before 11-08 runs a tag command; the only tag is the authorized v1.0.0 |

## Unregistered flags (not counted)

- UF-1 (low): default-mode `verify-repo-hygiene.sh` (PRE_RELEASE=1) fails now that api.txt is tracked and v1.0.0 exists; release gates use PRE_RELEASE=0. v1.1 tooling candidate.
- UF-2 (info): `secret-scan: allow` lines skip the key scan; 12 such lines in the tag, all fake canaries.
- UF-3 (info): leak content check skipped on the executor host; preflight line annotated `content_check=ran(master)` (master cited 829 files, tag holds 828); auditor re-ran it: 0 hits.
- UF-4 (info): ECOSYSTEM.md names private host paths; v1.0.x doc patch first.
- UF-5 (info): uncommitted orchestrator keys in config.json; `git.create_tag` is false.
- UF-6 (info): no selftest control exercises the api-check "task did not execute" branch; consider in v1.1 tooling.

## Verdict

SECURED. threats_open: 0.
