---
phase: 14-localgrammar-bilingual-grammarpack
plan: 08
subsystem: docs-and-quality-gate
tags: [api-docs, frozen-surface-review, metalava, open-items, phase-gate]
status: complete

requires:
  - phase: 14-07
    provides: normalize, terminal intents, policy and redaction proofs (the last public grammar members)
provides:
  - "API.md documents the grammar tier: package line, three surface rows, Extraction.matchedLanguage, grammar bullet, six trace codes"
  - "14-SURFACE-REVIEW.md: member-by-member frozen-surface review against the real Metalava dump, open items OI-1 .. OI-8, source audit, gate results"
  - "Full phase gate evidence on the phase branch: check, docs coverage, API surface, repo hygiene all green"
affects: [14-09, 14-10, 19, 20]

requirements-completed: [GRAM-04]
requirements-contributed: [GRAM-01, GRAM-02, GRAM-03, GRAM-05]

actuals:
  tokens: 5500
  tasks: 3
  commits: 3
plan_head_before: 8663f9aa0ab35acaa5cc70bd84b8c898f8e9c3c9
commits: 3

tech-stack:
  added: []
  patterns:
    - "Surface review runs against a Metalava dump produced in an isolated copy of the tree, so the real tree never receives an api.txt before the tag cut"

key-files:
  created:
    - .planning/phases/14-localgrammar-bilingual-grammarpack/14-SURFACE-REVIEW.md
  modified:
    - API.md

key-decisions:
  - "No public member needed a change: every grammar member can grow by adding a member or an overload, so no source file was edited and no KDoc gap was found"
  - "GRAM-04 ticked here (documentation was its last open contribution); GRAM-02 stays pending because 14-09 and 14-10 still list it"

coverage:
  - id: D1
    description: "API.md names every new public top-level type and Extraction.matchedLanguage with no domain wording"
    requirement: GRAM-04
    verification:
      - kind: other
        ref: "scripts/verify-docs-coverage.sh -> DOC COVERAGE OK checks=25 types=103"
        status: pass
  - id: D2
    description: "Frozen-surface review of the grammar package against the real Metalava dump; Extraction keeps exactly its 2- and 3-argument public constructors"
    requirement: GRAM-02
    verification:
      - kind: other
        ref: "scripts/review-api-surface.sh -> API SURFACE OK sealed=... classes=193"
        status: pass
  - id: D3
    description: "Full repository gate on the phase branch"
    verification:
      - kind: other
        ref: "./gradlew --offline -q check (exit 0); scripts/verify-repo-hygiene.sh -> HYGIENE OK"
        status: pass
---

# Phase 14 Plan 08: Surface docs, frozen-surface review and phase gate Summary

API.md now documents the grammar tier, the public grammar surface was reviewed member by member against the real Metalava dump (all additive-safe), the eight open items for the orchestrator are written down with defaults and post-tag costs, and the full repository gate is green on the phase branch.

## Driver action: relay OI-1 .. OI-8 to the orchestrator

Write-up with the full table: `/home/yahir/Projects/Reusable/android/voice-action-engine/.planning/phases/14-localgrammar-bilingual-grammarpack/14-SURFACE-REVIEW.md`, section `## Open items for orchestrator`. All are non-blocking and each ships with the default shown; overturn before the v1.1.0 cut or live with it.

| Id | Shipped default (one line) |
|---|---|
| OI-1 | `normalize` runs on `text` and `choice` slots (for choice, the answer replaces the option id); number slots refused at build time |
| OI-2 | On cross-pack agreement `matchedLanguage` is the command's label, or null with no label; `ruleId` is null |
| OI-3 | `ruleId` is an opaque string, documented as not to be parsed |
| OI-4 | The resolver is optional only when every intent is terminal |
| OI-5 | No TESTER window: the strict golden number table ships; 14-09 records `grant: deferred` plus a deferred obligation (owner: milestone Gate-2) unless a window is relayed |
| OI-6 | Bare `a`/`an`/`un`/`una` are not numbers (cost: one cloud call on "add a milk"); SB and CT to confirm |
| OI-7 | A sentence terminator (`. ! ? ;`) between two words rejects the transcript |
| OI-8 | D-12 capture is recognizer-direct (TTS to WAV to `SpeechRecognizer` via `EXTRA_AUDIO_SOURCE`) in 14-09, only under a relayed `grant: open` |

## What was done

1. **API.md** (commit `7f06be7`): added the `core.strategy.grammar` package line, rows for `LocalGrammarStrategy`, `GrammarPack` and `GrammarMatch`, `matchedLanguage` in the `Extraction` row and the signature table, a grammar bullet in "Strategies and tools" (template syntax in one sentence, never guesses, null label tries both packs, terminal intents), and the six grammar trace codes in "Telemetry and trace". No kotlin fence. `scripts/verify-docs-coverage.sh`: `DOC COVERAGE OK checks=25 types=103`.
2. **Frozen-surface review** (commit `a48f473`): `scripts/review-api-surface.sh` over an isolated copy gave `API SURFACE OK` with no sealed type outside the allowed seven, no enum, data shape, default-argument stub or static field in the grammar package, and `Extraction` showing exactly the 2- and 3-argument public constructors. The review table (34 rows) found no member that cannot be extended additively, so no source or KDoc edit was made. The committed `api.txt` files are untouched.
3. **Phase gate** (commit `3d9fa05`): `./gradlew --offline -q check` exit 0 in 160 s with the host-safe recipe (no earlyoom kill, no retry); `verify-docs-coverage` and `verify-repo-hygiene` green; results recorded in `## Gate results` of the review.

## Deviations from Plan

1. **Plan ledger created late.** The `gsd-plan-head-before-14-08` ledger file was written after the first task commit, from `HEAD~1` (the pre-task HEAD `8663f9a`), so `commits: 3` is still measured from the true plan base.
2. **GRAM-02 not ticked.** The plan frontmatter lists GRAM-01 .. GRAM-05, but 14-09 and 14-10 list GRAM-02 and their work (STT forms, golden fixtures) is still pending; only GRAM-04 was marked complete.
3. **Source audit statuses.** D-08 and D-12 are recorded as PARTIAL (14-10 and the capture are pending), not DONE.

4. **`state.advance-plan` ran twice by mistake** (the first output was truncated and I re-ran it to read it), which moved `current_plan` 8 to 10. I corrected `current_plan` and the "Current Plan" line in STATE.md back to 9 by hand; `completed_plans` is 27 (one advance counted once). Driver: STATE.md `current_plan: 9` is the intended value.

No auth gates, no device use, no architectural changes.

## Open issues

- OI-1 .. OI-8 above (orchestrator relay).
- `14-WINDOW-GRANT.md` is still `grant: pending`; plan 14-09 needs a relayed answer before any device step.
- `14-VALIDATION.md` was not touched (the Nyquist finalizer owns its sign-off).

## Verification

- `scripts/verify-docs-coverage.sh`: `DOC COVERAGE OK checks=25 types=103`.
- `scripts/review-api-surface.sh --out /tmp/vae-14-core-dump.txt` (run twice): `API SURFACE OK sealed=AssistantPart,CommandOutcome,GateDecision,Message,RunTermination,StrategyOutcome,ToolStep classes=193`.
- `./gradlew --offline -q check`: exit 0. `scripts/verify-repo-hygiene.sh`: `HYGIENE OK`.
- Acceptance greps: `LocalGrammarStrategy` 3, `GrammarMatch` 3, `matchedLanguage` 3, `grammar_resolver_rejected` 1 in API.md; 66 table rows and 13 `OI-n` mentions in the review; `git status --porcelain` on the three `api.txt` paths empty.
- No device or behavioral verification was done or needed.

## Self-Check: PASSED

API.md and 14-SURFACE-REVIEW.md exist; commits 7f06be7, a48f473 and 3d9fa05 exist on `gsd/phase-14-localgrammar-bilingual-grammarpack`; `commits: 3` measured from the persisted ledger (`git rev-list --count 8663f9a..HEAD` = 3 before this SUMMARY commit).
