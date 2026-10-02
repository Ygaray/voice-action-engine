---
phase: 11-cut-v1-0-0
plan: 01
subsystem: release-preconditions
tags: [release, preconditions, limits, waiver-packet, v1.0.0]
requires:
  - phase: 10-sample-harness-gate-1-docs
    provides: Gate-1 SELF-UAT, evidence files, uat-pending fragments
provides:
  - fresh limits proof (6 / 60000 / 4096) for both shipped looping strategies
  - preconditions audit (D-04, D-02, suppressions, cause-code mapping, minSdk)
  - 11-WAIVER-PACKET.md with machine-readable Category column and pending answer block
affects: [11-02, 11-04, 11-06, 11-07, 11-08]
tech-stack:
  added: []
  patterns: [evidence parsed from JUnit XML by script]
key-files:
  created:
    - .planning/phases/11-cut-v1-0-0/evidence/limits-reverify.txt
    - .planning/phases/11-cut-v1-0-0/evidence/preconditions-audit.txt
    - .planning/phases/11-cut-v1-0-0/11-WAIVER-PACKET.md
  modified: []
key-decisions:
  - "No item added to the waiver packet beyond the 13 expected; the 11-02 pre-dump API items are orchestrator decisions, not Yahir waivers"
requirements-completed: [VER-05]
status: complete
plan_head_before: e2aa3e1bec723a902f21179a1a200ee5056c2d3b
commits: 3
metrics:
  completed: 2026-10-02
actuals:
  tasks: 3
  commits: 3
---

# Phase 11 Plan 01: Preconditions and Waiver Packet Summary

Every inherited section 11 precondition holds from the current tree, and the pre-cut waiver packet (13 rows, one pre-freeze) exists with a parseable pending answer block.

## Verdicts

- `LIMITS PRECONDITION: MET` (`evidence/limits-reverify.txt`). Fresh run `./gradlew :core:cleanTest :core:test --tests '*SingleShotLimitsTest' --tests '*AgenticLoopLimitsTest' --offline` (offline resolution worked). JUnit XML: SingleShotLimitsTest 10 tests, AgenticLoopLimitsTest 14 tests, 0 failures, 0 errors, 0 skipped, timestamps after the recorded start. All 11 required test names present and passed, each suite pinning `TierPolicy.DEFAULT` (6 / 60000 / 4096). Shipped strategy set is exactly SingleShotStrategy and AgenticLoopStrategy. Phase 9 mandate coverage: 127 PASSED lines, 0 FAILED/MISSING/ABSENT.
- `PRECONDITIONS AUDIT: PASS` with `CAUSE MAPPING: MATCH` (`evidence/preconditions-audit.txt`). D-04 met (10-SELF-UAT.md committed at 6073e61, verdict 13 of 14 PASS + 1 ACCEPTED BY EVIDENCE, 10-VERIFICATION.md `status: passed`). D-02 met (no local or origin tag; `create_tag` false in both the worktree and the main checkout config, read only). minSdk recorded as no action.

## Findings for the orchestrator

- Suppressions: the published-main-source reading holds. `core/.../internal/Guarded.kt:1` is the ONLY `@Suppress` under core, providers and keystore `src/main`. The literal repo-wide reading ("only suppression in the repo") is false: 3 other hits, none published (`IdentityTypesTest.kt:14` `unused`; `sample/src/release/.../DebugTools.kt:12,16` `UNUSED_PARAMETER`). The plan text says "four", but only three exist. The two Phase 04 review-fix suppressions were removed by `a6def62`, so the cross-repo packet's Phase 04 "accept the two @Suppress" line is stale. No detekt baseline file or property exists.
- Cause-code mapping matches the shipped code, no mismatch: `key_missing` (`KeystoreCredentialSource.kt:29`, `SecretReader.kt:62`), `decrypt_failed` (`SecretReader.kt:92-95`), `stored_value_malformed` (`SecretReader.kt:74-75, 78-83, 105`), `keystore_unavailable` (`SecretReader.kt:65-68, 96-99`), `storage_unreadable` (`ApiKeyStore.kt:126, 133, 140-146`). The first three mean re-enter key, the last two transient retry. `KeystoreCauses` is internal (`KeystoreCauses.kt:15`), so 11-02 must put the UX KDoc on a public constants object. Nuance: `storage_unreadable` also covers a corrupt preferences file, which a retry may not heal.

## Waiver packet W-ids

W01 C4 OpenRouter cache-write (A); W02 C5 G1-09 OpenRouter omitted-optional (AE, "ACCEPTED BY EVIDENCE, NOT a PASS"); W03 C1 Billing mapping live (A); W04 C3 Responses-only 400 wording (B); **W05 4b `ChatCompletionsAttemptKind` public value class (C, PRE-FREEZE)**; W06 Phase 06 composite device test (A); W07 Phase 07 CT scenarios vs CT's resolver (A); W08 Phase 08 R2 gpt-oss (A); W09 Phase 09 live parallel tool calls (A); W10 Phase 09 CLN-02 deny-list (A); W11 Phase 10 C7 (A); W12 Phase 10 Gate-1 tooling defects (A); W13 Phase 10 three doc stumbles (D). Category C ids: W05 only. Nothing added beyond the expected starting set. The packet reconciles against `aae93b3` (Phase 04 suppression line already satisfied, doc stumbles move to D, 4b becomes pre-freeze, duplicates mapped to W-ids) and states that human Gate-2 is not a tag precondition, citing `CROSS-REPO-SCOPE-CONTRACT.md` lines 210, 237, 271, 274, 277, 278.

## Deviations from Plan

**[Rule 1 - plan verify bug] Task 3 automated verify** The plan's command uses `tr -d '-: '`, which GNU `tr` reads as an option (`invalid option -- ':'`), so the chain short-circuits. I ran the same checks with `tr -d ': -'`; all pass (W-id sets equal, NOT a PASS present, contract citations present, no key-shaped string, every cited `.planning` and code path exists). The plan file was not edited.

No other deviations. No test or source was written; `git diff` vs the plan base touches only the three files under `.planning/phases/11-cut-v1-0-0/`. `.planning/config.json`, the cross-repo packet and `CROSS-REPO-SCOPE-CONTRACT.md` are unchanged. No tag created.

## Commits

- b87b285 docs(11-01): limits precondition re-verified from fresh JUnit XML (MET)
- ff4ffab docs(11-01): static preconditions audit passes
- 22b4634 docs(11-01): pre-cut waiver packet with category column and pending answer block

## Self-Check: PASSED

All three created files exist and the three commit hashes are present in the log.
