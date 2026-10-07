---
phase: 19-sample-gate-1-docs
plan: 08
subsystem: api-surface-review
tags: [d-12, metalava, frozen-surface, review-script, api-review]

requires:
  - phase: 19-sample-gate-1-docs
    provides: plan 03 (RT-04 two-plus-two-plus-one adapter surface), plan 07 (router_live PASS for P16 OI-6)
provides:
  - scripts/review-api-surface.sh --module/--dump with per-module sealed allow-lists
  - 19-API-REVIEW.md, the consolidated v1.1 frozen-surface review with verdict "no shape fix needed"
affects: [19-09, 19-10, 19-12, phase-20]

status: complete
actuals:
  tokens: 45000
  tasks: 2
  commits: 2
plan_head_before: 673eee00e1c371254d563bdccd67c4616118f52f
commits: 2

key-files:
  created:
    - .planning/phases/19-sample-gate-1-docs/19-API-REVIEW.md
  modified:
    - scripts/review-api-surface.sh

key-decisions:
  - "The review script keeps its old default (core, own isolated dump); --dump skips Gradle entirely and cannot be combined with --out or a committed api.txt"
  - "Dump directory is /tmp/vae-19-dump, outside the repository and not committed"
  - "Open items without a relayed answer are dispositioned 'orchestrator' or 'carried to Phase 20', never silently closed"

requirements-completed: [DOC-02]

duration: 25min
completed: 2026-10-07
---

# Phase 19 Plan 08: Consolidated frozen-surface review Summary

**One isolated Metalava dump of all five modules passes a per-module review (core seven sealed types, undo exactly UndoResult, others none), removals against the committed baselines are 0, and the verdict is "no shape fix needed".**

## What was done

- `scripts/review-api-surface.sh` gained `--module <name>` (validated against the manifest, default core) and `--dump <file>`
  (review a given dump, no Gradle). Package non-vacuity comes from the manifest package column; the OK line is now
  `API SURFACE OK module=<name> sealed=<list|none> classes=<n>`. Copy/componentN, enum and public-static-field checks apply
  to every module. The old no-argument invocation still dumps core in its own isolated copy.
- One `scripts/api-dump-isolated.sh --out /tmp/vae-19-dump` run (single-use daemon, low-memory GRADLE_OPTS, MemAvailable
  9.2 GiB before): `core=1956 providers=121 keystore=76 undo=191 voice-adapter=16`.
- `19-API-REVIEW.md` written with the six required sections.

## Evidence

```
API SURFACE OK module=core sealed=AssistantPart,CommandOutcome,GateDecision,Message,RunTermination,StrategyOutcome,ToolStep classes=207
API SURFACE OK module=providers sealed=none classes=14
API SURFACE OK module=keystore sealed=none classes=10
API SURFACE OK module=undo sealed=UndoResult classes=19
API SURFACE OK module=voice-adapter sealed=none classes=2
```

- Planted `public enum` in a copy of the voice-adapter dump: exit 1. Planted `public sealed class`: exit 1. The undo dump
  reviewed as core: exit 1. All committed `api.txt` files unchanged (`git diff --exit-code` exit 0).
- Removals vs committed baseline: core 0 (258 added), providers 0 (0 added), keystore 0 (9 added: `DelicateKeyAccess`,
  `KeyAccess`, the opt-in `ApiKeyStore` constructor). Committed baselines equal the `v1.0.1` tag.
- voice-adapter dump `grep -o` counts: `toCommandInput` 2, `commandInputOf` 2, `normalizeSttLanguageLabel` 1.
- P16 OI-6 closed from `evidence/gate1-router_live.txt` (`verdict=PASS eligible=2 picked_index=1 sel_turns=1`).

## Findings routed to later plans (not shape findings)

- Docs versus dump: `PipelineEvent.StartTierSelected` is named in neither API.md nor INTEGRATION.md; INTEGRATION.md has no
  grammar or plan subsections yet; `EntityKey`, `Compensator`, `Blocker`, `NotRestored`, `PickContext` are API.md-only.
  Input for plans 09 and 10.
- Open items awaiting a relayed orchestrator answer before the Phase 20 cut are listed with dispositions in
  `19-API-REVIEW.md` ("orchestrator" and "carried" rows). None blocks the docs.
- Metalava renders the `kotlin.time.Clock` builder property as `ErrorType clock` (seven occurrences, six already in the
  frozen baseline). Observation only.

## Task Commits

1. Task 1 (tracer): `ce7e457` - review script `--module/--dump` with per-module allow-lists
2. Task 2: `a5c5f99` - `19-API-REVIEW.md`

## Deviations from Plan

None. The dump directory is `/tmp/vae-19-dump` (the plan's `${TMPDIR:-/tmp}` default). The plan's single chained verify
command was run as its constituent steps (dump once, then the reviews, plants and `git diff`), so Gradle ran only once.

## Self-Check: PASSED

`scripts/review-api-surface.sh` and `19-API-REVIEW.md` exist; commits ce7e457 and a5c5f99 exist; the Task 2 verify greps
pass; `git diff --exit-code -- core providers keystore undo voice-adapter` exits 0; no api.txt changed.
