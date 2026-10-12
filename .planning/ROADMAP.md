# Roadmap: voice-action-engine

## Milestones

- ✅ **v1.0 — Core Engine**: Phases 1-11, shipped 2026-10-02 (tags `v1.0.0`, patch `v1.0.1` 2026-10-04); closed 2026-10-05. Archive: [milestones/v1.0-ROADMAP.md](milestones/v1.0-ROADMAP.md)
- ✅ **v1.1 — Grammar, Plan, Router, Undo, Spike, Adapter**: Phases 12-20, shipped 2026-10-07 (tag `v1.1.0` @ `2e677a6`); closed 2026-10-11. Archive: [milestones/v1.1-ROADMAP.md](milestones/v1.1-ROADMAP.md)

**Authority:** `CROSS-REPO-SCOPE-CONTRACT.md` §6.2 + §10 + §11 wins over this file. Sequencing goes through the orchestrator
(name in `xrepo/vae-bilingual/effort.json`).

## Phases

<details>
<summary>✅ v1.0 Core Engine (Phases 1-11): SHIPPED 2026-10-02</summary>

- [x] Phase 1: Scaffold & Publishing Proof (6 plans), completed 2026-09-30
- [x] Phase 2: Core Contract, Pipeline & Commit Seam, completed 2026-09-30
- [x] Phase 3: Transcript Types, ProviderRouter & On-Device Gate, completed 2026-09-30
- [x] Phase 4: Anthropic Transport & OkHttp Matrix, completed 2026-10-01
- [x] Phase 5: OpenAI & OpenRouter Transports, completed 2026-10-01
- [x] Phase 6: Keystore, completed 2026-10-01
- [x] Phase 7: SingleShot Strategy, completed 2026-10-01
- [x] Phase 8: Multi-turn Mappers, completed 2026-10-01
- [x] Phase 9: Agentic Loop Strategy, completed 2026-10-01
- [x] Phase 10: Sample Harness, Gate-1 & Docs, completed 2026-10-01
- [x] Phase 11: Cut v1.0.0, completed 2026-10-02

Full detail: [milestones/v1.0-ROADMAP.md](milestones/v1.0-ROADMAP.md). Phase artifacts: `milestones/v1.0-phases/`.

</details>

<details>
<summary>✅ v1.1 Grammar, Plan, Router, Undo, Spike, Adapter (Phases 12-20): SHIPPED 2026-10-07</summary>

- [x] Phase 12: Wave-1 Seams & W04 Fix (8/8 plans), completed 2026-10-05
- [x] Phase 13: On-Device Model Spike (11/11 plans, red verdict, nothing shipped), completed 2026-10-06
- [x] Phase 14: LocalGrammar & Bilingual GrammarPack (10/10 plans), completed 2026-10-06
- [x] Phase 15: PlanThenExecute Strategy (7/7 plans), completed 2026-10-06
- [x] Phase 16: Start-Tier Selection (7/7 plans), completed 2026-10-06
- [x] Phase 17: Run-Level Undo (10/10 plans), completed 2026-10-06
- [x] Phase 18: Voice Adapter (8/8 plans), completed 2026-10-06
- [x] Phase 19: Sample Gate-1 & Docs (14/14 plans), completed 2026-10-07
- [x] Phase 20: Cut v1.1.0 (13/13 plans), completed 2026-10-07

Full detail: [milestones/v1.1-ROADMAP.md](milestones/v1.1-ROADMAP.md). Phase artifacts: `milestones/v1.1-phases/`.

</details>

## Backlog

### Phase 999.1: binary-diff helper should read Kotlin metadata visibility (skip internal ctors + synthetic access$ members) (BACKLOG)

**Goal:** Target milestone v1.2 (tooling only, no library code change). `scripts/verify-binary-diff.sh` lists JVM-public members of the v1.0.1 artifacts that are missing from the new artifacts, but it reads javap flags, not Kotlin metadata. Kotlin emits an `internal constructor(` as a public JVM `<init>` (plus its `$default` and DefaultConstructorMarker synthetics), and the compiler emits `access$...` synthetic accessors for private members, so a refactor that only touches internal constructors reads as a removal. At the v1.1.0 cut this made the core diff report `removed=12` (11 members of internal constructors + 1 synthetic accessor `access$submitAll`), waived by RT-12 (orchestrator 3b): see `.planning/releases/v1.1.0/evidence/binary-diff.txt` (section D-02 WAIVER) and `.planning/releases/v1.1.0/evidence/binary-diff-waiver.txt`. Wanted: the helper reads the `kotlin.Metadata` visibility (or skips ACC_SYNTHETIC members and constructors that Kotlin marks internal) and counts those under `internal_removed`-style reporting instead of `removed`, with selftest cases for an internal-ctor parameter change and a synthetic accessor, so a clean run needs no waiver. Not a code change in the engine; do not touch the helper before the v1.1.0 cut (W is frozen).
**Requirements:** TBD
**Plans:** 0 plans

Plans:

- [ ] TBD (promote with /gsd-review-backlog when ready)
