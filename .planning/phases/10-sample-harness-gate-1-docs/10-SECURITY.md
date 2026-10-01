---
phase: "10"
slug: sample-harness-gate-1-docs
status: secured
threats_open: 0
asvs_level: 1
audited_head: 8f6978744a59e9d369949766a86f0e7725c942af
created: "2026-10-01"
---

# Phase 10 - Security

> Audited by gsd-security-auditor (ASVS 1, block_on high); the orchestrator wrote this file from the structured verdict. 50 threats (T-10-01..T-10-49 plus T-10-SC), 49 `mitigate` all CLOSED, 1 `accept` (T-10-SC, Compose BOM / activity / lifecycle coordinates added to the version catalog, risk documented in 10-01-PLAN.md). Read-only audit: no device, no adb, no files written by the auditor. `verify-repo-hygiene.sh` HYGIENE OK and `verify-docs-coverage.sh` DOC COVERAGE OK during the audit.

## Trust Boundaries

| Boundary | Description | Data Crossing |
|----------|-------------|---------------|
| host scripts -> TESTER device | the guarded runner is the only path to adb; TESTER serial only | commands, keys by file reference, evidence |
| device app -> committed evidence | closed-vocabulary evidence lines pass an allow-list filter and key-shape scan | counts, codes, status words |
| private fixture -> repository | LE-1 fixture stays a gitignored asset, never content in evidence | fixture bytes (never committed) |
| repository -> JitPack/GitHub | pushes publish docs and sources; no tag before Phase 11 | docs, source |
| master -> executor/tester | only the relayed decision unlocks live spend | decision record |

## Threat Register (summary)

All T-10-01..T-10-49 and T-10-SC closed. Groups and primary evidence:

| Threats | Area | Evidence |
|---------|------|----------|
| T-10-01..04, SC | build/manifest/OkHttp pin | `sample/build.gradle.kts`, `gradle/invariants.gradle.kts` verifyOkHttpCompileFloor, OkHttpPinTest, manifest INTERNET only + allowBackup=false |
| T-10-05..09 | fixture integrity, no content echo, no strict flag | FixtureLoader (full SHA-256 compare), CannedToolExecutorTest, SyntheticToolsTest, `.gitignore:48` |
| T-10-10..14 | keys: test-key import, KeyVault, release gating | TestKeyImporterTest (debug), release DebugTools returns null, KeyVaultTest |
| T-10-15..28 | evidence lines, budget, verdicts, UI | EvidenceLine private constructor + golden, RequestBudget (+BudgetedProvider), CacheVerdict/SmokeVerdict tests, OutcomeText tests, SampleViewModel tests |
| T-10-29..35 | guarded runner: serial pinning, no argv keys, evidence filter, flock, cleanup, cold stamp | `scripts/run-sample-gate1.sh`, `scripts/verify-sample-device-guard.sh` (27 scenarios), `scripts/sample-evidence-filter.sh` |
| T-10-36..39 | docs: domain-free, compiled snippets, no concrete version claim, never-log wording | `scripts/verify-docs-coverage.sh` C06/C07/C11/C16/C20/C21/C23 |
| T-10-40..43 | push hygiene, wiring-test isolation and non-vacuity, tag/SHA rule | hygiene, `scripts/agent-wiring-test.sh selftest`, 10-WIRING-TEST.md |
| T-10-44..49 | live-leg authorization, TESTER-only runbook, budget, carries, no tag | 10-LIVE-LEG-DECISION.md, GATE1-RUNBOOK.md, gate2-carry-register.txt, `git tag --list` empty |

## Unregistered flags (not counted in threats_open) and dispositions

| Flag | Severity | Disposition |
|------|----------|-------------|
| UF-1 fixture tool names could reach committed ver02 evidence (same as review CR-01) | warning | fixed in the review-fix pass (see 10-REVIEW-FIX.md) before any evidence is captured |
| UF-2 SB tool-name literals in earlier-phase tracked files (CROSS-REPO-SCOPE-CONTRACT.md, negative controls, providers test goldens) | info | predates the evidence rule and already public; reported to the master, not changed here |
| UF-3 TestKeyImporter.saveThenRead catches a narrow exception set (= WR-07) | low | fixed in the review-fix pass |
| UF-4 corrupt budget file read as 0 (= WR-03) | low | fixed in the review-fix pass |
| UF-5 wiring-test subagent auto-loaded this repo's CLAUDE.md | info | recorded in 10-WIRING-TEST.md; rerun owed (pending-rerun) |
| UF-6 clarification question/options shown as on-screen text on live legs | info | runbook forbids committing dumps and screenshots |
| UF-7 secret pre-commit hook is host config, not repo-enforced | info | accepted; hygiene script and explicit-path commits are the repo-side controls |

## Verdict

SECURED. threats_open: 0.
