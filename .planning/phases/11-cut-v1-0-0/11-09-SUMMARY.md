---
phase: 11-cut-v1-0-0
plan: 09
subsystem: release
tags: [jitpack, ledger-row, ver-05, d-03]
status: complete
tag: v1.0.0
peeled_commit: efc060f8fe462b71af2e4586b75a97db119ebabd
requirements-completed: [VER-05]
---

# Phase 11 Plan 09: JitPack verification and ledger row

- `LIVE PROBE PASS ref=v1.0.0`: api `{"version":"v1.0.0","status":"ok","commit":"efc060f8...","isTag":true,"modules":[core,keystore,providers]}`, no sample. POM project version v1.0.0 for all three modules; providers and keystore depend on core v1.0.0; empty-Gradle-cache consumer resolved all three coordinates at v1.0.0 with no conflict arrow. Final line `JITPACK v1.0.0: VERIFIED commit=efc060f8fe462b71af2e4586b75a97db119ebabd` (evidence/11-JITPACK-VERIFY.log).
- Ledger row: 11-LEDGER-ROW.md and evidence/ledger-row.txt. §11 untouched; the master messages the row to the orchestrator (A14).

## message_to_orchestrator

See 11-LEDGER-ROW.md section `message_to_orchestrator` (verbatim) and evidence/ledger-row.txt (xrepo ledger-row args: repo, tag, peeled commit, per-module coords, contents, evidence).

## Follow-ups

- (a) git.create_tag stays false through milestone close.
- (b) Gate-2 at milestone close; W04 (OpenAI Responses-only 400 marker match) carried to it.
- (c) Consumer repin rows via the orchestrator.
- (d) Hygiene default mode tag-aware: v1.1 tooling candidate.
- (e) Six wiring stumbles become v1.0.x doc-patch follow-ups, ECOSYSTEM.md private-path mention first.
- (f) Any defect later: v1.0.1 + superseded row, never a moved tag.
