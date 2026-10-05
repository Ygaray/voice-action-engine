# Roadmap: voice-action-engine

## Milestones

- ✅ **v1.0 — Core Engine**: Phases 1-11, shipped 2026-10-02 (tags `v1.0.0`, patch `v1.0.1` 2026-10-04); closed 2026-10-05. Archive: [milestones/v1.0-ROADMAP.md](milestones/v1.0-ROADMAP.md)
- 📋 **v1.1 — Grammar, Plan, Router, Undo, Spike, Adapter**: Phases 12-20 (planned; contract §6.2 steps 8–13 + A18; cuts `v1.1.0`). Scope: R-v1.1 GO, see `.planning/cross-repo/RECONVENE-BRIEF-R-v1.1.md`

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

### 📋 v1.1 (planned, numbering continues at 12)

Defined by `/gsd-new-milestone`. The proposed phases (R-v1.1 brief §4) are:
- 12: additive seams + W04 fix
- 13: on-device spike
- 14: LocalGrammar
- 15: PlanThenExecute
- 16: Router / `TierSelector.Custom`
- 17: `:undo`
- 18: `:voice-adapter`
- 19: `:sample` Gate-1 + docs
- 20: cut `v1.1.0`
