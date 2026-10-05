---
gsd_state_version: "1.0"
milestone: v1.1
milestone_name: Grammar, Plan, Router, Undo, Spike, Adapter
status: planning
last_updated: "2026-10-05T08:03:09.488Z"
last_activity: 2026-10-05
progress:
  total_phases: 0
  completed_phases: 0
  total_plans: 0
  completed_plans: 0
  percent: 0
---

# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-10-05) · Roadmap: .planning/ROADMAP.md · v1.0 archive: .planning/milestones/v1.0-*

**Core value:** A consumer app can hand the engine a transcript and get back a correct, typed outcome through a tier ladder it composed itself. The cloud agentic path works on-device (Anthropic, prompt cache hitting), and every failure is surfaced as a specific, loud reason, never a silent or opaque one.
**Current focus:** Planning next milestone: v1.1 (R-v1.1 GO 2026-10-05, `.planning/cross-repo/RECONVENE-BRIEF-R-v1.1.md`)

## Current Position

Phase: Not started (defining requirements)
Plan: —
Status: Defining requirements
Last activity: 2026-10-05 — Milestone v1.1 started

## Performance Metrics

**Velocity:**

- Total plans completed: 97
- Average duration: -
- Total execution time: 0.0 hours

**By Phase:**

| Phase | Plans | Total | Avg/Plan |
|-------|-------|-------|----------|
| 1 | 6 | - | - |
| 02 | 9 | - | - |
| 3 | 10 | - | - |
| 4 | 8 | - | - |
| 5 | 12 | - | - |
| 6 | 7 | - | - |
| 07 | 8 | - | - |
| 8 | 9 | - | - |
| 9 | 9 | - | - |
| 10 | 10 | - | - |
| 11 | 9 | - | - |

**Recent Trend:**

- Last 5 plans: -
- Trend: -

*Updated after each plan completion*
**Per-Plan Metrics:**

| Plan | Duration | Tasks | Files |
|------|----------|-------|-------|
| Phase 01 P01 | 20 min | 2 tasks | 21 files |
| Phase 01 P02 | 6 min | 2 tasks | 2 files |
| Phase 01 P03 | 10 min | 2 tasks | 13 files |
| Phase 01 P04 | 9 min | 2 tasks | 2 files |
| Phase 01 P05 | 3 min | 2 tasks | 7 files |
| Phase 01 P06 | 12 min | 3 tasks | 6 files |
| Phase 02 P01 | 35min | 3 tasks | 16 files |
| Phase 02 P02 | 25min | 3 tasks | 12 files |
| Phase 02 P03 | 45min | 2 tasks | 22 files |
| Phase 02 P04 | 40min | 3 tasks | 13 files |
| Phase 02 P05 | 40min | 3 tasks | 9 files |
| Phase 02 P06 | 45min | 3 tasks | 10 files |
| Phase 02 P07 | 25min | 3 tasks | 3 files |
| Phase 02 P08 | 10min | 3 tasks | 21 files |
| Phase 02 P09 | 25min | 2 tasks | 3 files |
| Phase 03 P01 | 4 min | 3 tasks | 8 files |
| Phase 03 P02 | 10 min | 3 tasks | 5 files |
| Phase 03 P03 | 15 min | 3 tasks | 7 files |
| Phase 03 P04 | 10min | 2 tasks | 4 files |
| Phase 03 P05 | 10 min | 3 tasks | 8 files |
| Phase 03 P06 | 7 min | 3 tasks | 3 files |
| Phase 03 P07 | 25 min | 3 tasks | 6 files |
| Phase 03 P08 | 5 min | 3 tasks | 4 files |
| Phase 03 P09 | 20 min | 3 tasks | 4 files |
| Phase 03 P10 | 20 min | 3 tasks | 4 files |

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
- [Phase 1]: explicitApi() DSL (not raw flag) on all published modules; engineGroup/engineVersion held as single values with VERSION env override
- [Phase 1]: ASSUMPTION: :keystore minSdk 35 / compileSdk 36.1 copied from SB/YAT; confirm with orchestrator before v1.0.0 cut
- [Phase 01]: Live JitPack probe passed at rung 0 by 10-char SHA 7f9db22944; no fallback (F1-F3) needed, coordinates and module shape unchanged
- [Phase 1]: Phase 1 plan 03: scanner (not detekt) gates runCatching/print/printStackTrace/FQ DI annotations; detekt control compared as exact (line, rule) set
- [Phase 01]: 01-04: structural gates have no override knob; negative controls plant real violations — A knob equal to a wrongly-compiled truth would pass a bad artifact
- [Phase 02]: 02-05: APPLY_ERROR_CONTENT is file-private in ApplyStep.kt, read via internal fun applyErrorContent() (internal const leaks a public static field)
- [Phase 03]: 03-01: Message and AssistantPart are the only new sealed types; ToolChoice, StopReason and later vocabularies stay open — Three message kinds and two part kinds are contract-closed and mapped exhaustively; surface gate allow-list widened to seven
- [Phase 03]: 03-01: transcript constructors declare no default arguments; maxTokens required, no model id on ModelRequest — Metalava freezes constructor shapes at the cut; secondary constructors keep growth binary-safe
- [Phase 03]: 03-02: charsPerToken default 4.0 per model overridable; ModelCapabilityTable keyed by exact (ProviderId, id) with patch-over-default overrides — Larger divisor under-estimates tokens so the cache diagnostic errs silent; exact keys prevent prefix overrides misfiring
- [Phase 03]: 03-03: ProviderSelection fallback allowed only when own provider is ON_DEVICE and fallback is not ON_DEVICE (chains structurally impossible); CredentialLookup.Unreadable maps to FailureReason.CredentialUnreadable, distinct from NotConfigured — Loud, structural no-substitution; lost key surfaces as re-enter your key
- [Phase 03]: 03-06: providerGate returns FailureReason? so the on-device fallback (03-08) reuses the same gate and maps its own trace code — One gate for primary and fallback; the static policy must hold for the provider a run-time selection picked
- [Phase 03]: 03-08: a permitted on-device fallback records provider_fallback only; refusals record on_device_unavailable then fallback_refused
- [Phase 03]: 03-10: phase gate green on merged tree with no main-source change; constructor audit passed (79 owners, none outside allowed list); evidence named api-surface-review.txt to stay clear of the pre-cut api.txt hygiene rule

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
| uat_gaps | 10/10-SELF-UAT.md (G1-09 C5 OpenRouter omit-optionals INCONCLUSIVE; accepted by evidence W02) | partial (acknowledged) | 2026-10-05 | v1.0 |
| verification_gaps | phases 1-11 VERIFICATION.md fingerprint-stale (post-verification edits incl. v1.0.1 patch + REQUIREMENTS.md ticks); superseded by v1.0.1 cut gate @b32840e; override_closeout by Yahir | stale (override) | 2026-10-05 | v1.0 |

## Session Continuity

Last session: 2026-10-01T03:58:49.053Z
Stopped at: Phase 11 complete — all phases complete
Resume file: None

## Operator Next Steps

- Start v1.1 with /gsd-new-milestone, then research-milestone, then discuss-milestone; message the orchestrator "VAE v1.1 discussed" once CONTEXT is written
