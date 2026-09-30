---
gsd_state_version: "1.0"
milestone: v1.0
milestone_name: Core Engine
status: planning
last_updated: "2026-09-29T00:00:00.000Z"
last_activity: 2026-09-29
progress:
  total_phases: 10
  completed_phases: 0
  total_plans: 0
  completed_plans: 0
  percent: 0
---

# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-09-29) · Roadmap: .planning/ROADMAP.md · Requirements: .planning/REQUIREMENTS.md (v1.0, 60 reqs)

**Core value:** A consumer app can hand the engine a transcript and get back a correct, typed outcome through a tier ladder it composed itself. The cloud agentic path works on-device (Anthropic, prompt cache hitting), and every failure is surfaced as a specific, loud reason, never a silent or opaque one.
**Current focus:** Milestone v1.0 (contract §6.2 steps 1–7 → tag `v1.0.0`). Roadmap created; next is `/gsd-research-milestone`. Phase 1 (Scaffold & Publishing Proof) is first once the orchestrator sends GO.

## Current Position

Phase: 1 of 10 (Scaffold & Publishing Proof)
Plan: — (not yet planned)
Status: Roadmap created. Next: `/gsd-research-milestone` → `/gsd-discuss-milestone` → STOP for R1 (A13); no planning before the orchestrator's GO
Last activity: 2026-09-29 — Roadmap created (10 phases, 60/60 v1 requirements mapped)

Progress: [░░░░░░░░░░] 0%

## Performance Metrics

**Velocity:**
- Total plans completed: 0
- Average duration: -
- Total execution time: 0.0 hours

**By Phase:**

| Phase | Plans | Total | Avg/Plan |
|-------|-------|-------|----------|
| - | - | - | - |

**Recent Trend:**
- Last 5 plans: -
- Trend: -

*Updated after each plan completion*

## Accumulated Context

### Decisions

Decisions are logged in PROJECT.md Key Decisions table.
Recent decisions affecting current work:

- [Roadmap]: The 10 phases mirror §6.2 steps 1–7. Step 3a is split into Phase 3 (3a-i: transcript types, router, `ON_DEVICE` gate; pure JVM) and Phase 4 (3a-ii: Anthropic transport, A1 must-pass). Critical path: 1→2→3→4→8→9→10. Side branches: 6 after 2, 5 after 3, 7 after 4.
- [Roadmap]: Enforcement-driven placement:
  - CLN-01 and CLN-05 → Phase 1 (detekt invariants gate every later phase).
  - CLN-03, CLN-04 and TEL-03 → Phase 3 (seams, capabilities).
  - TEL-04 → Phase 4 (the first phase where key, transcript, args and error bodies all exist).
  - CLN-02 → Phase 9 (once both ports have landed).
- [Roadmap]: JitPack coordinates are proven by commit SHA in Phase 1, not by a throwaway probe tag (BLD-03 supersedes PROJECT.md's "probe tag"). Tags are immutable.

### Pending Todos

None yet.

### Blockers/Concerns

- **A13 gate:** after research + discussion, write `.planning/cross-repo/RECONVENE-BRIEF.md`, message `yahir-gsd-control-plane-f2` "R1 ready: <path>", and wait for GO / GO-WITH-CHANGES / HOLD. Never run the `/gsd-milestone` umbrella.
- **Orchestrator rulings received (2026-09-29):** `ProviderId` = value class, no amendment; fakes in `:core` test sources only (BLD-09), `:testing` later via amendment; per-action CommitSink per A17; CT's flat OpenAI shape + missing `claude-sonnet-5-5` confirmed by caltracker-android-9a (CT fixes on its side); E6 recorded. Record these in the R1 brief.
- **Responses API ruled (orchestrator, Option A):** v1.0 stays Chat Completions; GPT-6 Astra / GPT-6.1 Sol with tools fail typed + loud up front via the PUBLIC capability table (PROV-08), documented in README (VER-04); Responses dialect is additive v1.x (LATER-03). No amendment.
- **Fixture (LE-1):** delivered, gitignored, never committed (LE-7). If SB's prompt or tools change before Phase 10, ask the orchestrator to regenerate it.
- **Phase 5 goldens** need recorded real OpenAI/OpenRouter bodies, which means Yahir's keys. A16's live smokes (Phase 10) need all three keys.
- **Keys (pending Yahir via orchestrator):** Phase 5/8 golden captures and Phase 10 need real keys via chmod-600 files outside the repo, passed by reference; capture tasks are gated on that approval.
- **Tag cut is Phase 11** (orchestrator ruling): Phase 10 = sample/Gate-1/docs; `git.create_tag` false.
- **Devices:** all device work (Phase 6 instrumented test, Phase 10 Gate-1 + smokes) runs on the TESTER `…-s22-ultra-2` only.
- **§11:** the tag row is messaged to the orchestrator (A14), never committed here. Always run `git pull --rebase` before committing, because peers commit contract changes to this repo.

## Deferred Items

Items acknowledged and deferred at milestone close, most recent first:

| Category | Item | Status | Deferred At | Milestone |
|----------|------|--------|-------------|-----------|
| *(none)* | | | | |

## Session Continuity

Last session: 2026-09-29
Stopped at: Roadmap + STATE created, REQUIREMENTS traceability filled (60/60)
Resume file: None
