# Project Retrospective

*A living document updated after each milestone. Lessons feed forward into future planning.*

## Milestone: v1.0 — Core Engine

**Shipped:** 2026-10-02 (`v1.0.0`); patch `v1.0.1` 2026-10-04; closed 2026-10-05
**Phases:** 11 | **Plans:** 97 | **Commits:** ~740 (2026-09-29 → 2026-10-05)

### What Was Built
- Three per-module JitPack artifacts (`core`, `providers`, `keystore`) under zero-baseline detekt and structural invariants.
- The `:core` pipeline: a tier-ladder DSL, a never-thrown typed outcome, a gate → commit → sink seam, and telemetry on every exit.
- Anthropic + OpenAI/OpenRouter transports, compiled against OkHttp 4.12 and green on 4.12.0 / 5.2.1 / 5.5.0.
- SingleShot (CT port) and a provider-neutral AgenticLoop (SB port) over lossless multi-turn mappers.
- The `:sample` A10 proof on the TESTER (prompt cache write/read 7016), and an agent-wireable README.

### What Worked
- Proving the riskiest pieces first (JitPack per-module publishing by SHA, the OkHttp matrix) in Phase 1 meant no publishing surprise at the cut.
- Planted negative controls for every invariant gate: each rule is proven to bite, not just configured.
- Isolated wiring tests (headless agent, throwaway config) caught real doc stumbles before each tag.
- Consumers (SB, CT) found real defects quickly in Wave-1 (XR-171-01, XR-171-03), and the patch-tag path (v1.0.1) absorbed them without API change.

### What Was Inefficient
- The v1.0.1 release cut was earlyoom-killed 5 times on a full-swap host before a swap reset plus a single-use daemon got it through.
- W04 (the Responses-only 400 wording) waited until Gate-2. The device run couldn't show the body (LE-7), but a 2-call host-side curl with the engine's real wire shape answered it in minutes.
- The milestone close hit tooling friction:
  - every VERIFICATION fingerprints the shared `REQUIREMENTS.md`, so any checkbox tick stales all phases (seeded to the technician);
  - the phase archive move silently dropped a gitignored `.log` from git (caught and restored).
- The per-test verify-work loop doesn't fit a UI-less library. Gate-2 was run as one sign-off per phase instead.

### Patterns Established
- Hub tags are cut only after an isolated wiring re-run on the exact tag SHA.
- Live-only checks that a device can't observe get a host-side probe under `with-test-keys`, never a waiver by default.
- Cross-repo asks flow as XR-ids through the orchestrator; hubs answer them in a reconvene brief with additive API sketches checked against `api.txt`.

### Key Lessons
1. For any "does the real provider say X" question, replay the engine's exact wire body against the live API from the host early. Don't wait for a device leg.
2. Release cuts on this host need swap headroom and a quiet window first. Never `./gradlew --stop` while another repo's daemon is live.
3. After a milestone archive, diff deleted vs re-added paths. Ignored file types (`*.log`) don't follow a `git add`.
4. In v1.1, treat the stage barrier as advisory after any needs_human pause (INC-2026-09-08-02).

### Cost Observations
- Model mix: not measured for this milestone.
- Notable: real-provider spend stayed within the approved budget (Phase 10 live legs about USD 0.04 expected; the W04 host check billed nothing).

---

## Milestone: v1.1 — Grammar, Plan, Router, Undo, Spike, Adapter

**Shipped:** 2026-10-07 (`v1.1.0` @ `2e677a6`); closed 2026-10-11
**Phases:** 9 (12-20) | **Plans:** 88 | **Commits:** ~1030 (2026-10-01 → 2026-10-11)

### What Was Built
- Wave-1 additive seams (`onFailed`, `ReasoningMode`, `carryIn`, `cappedByPolicy`, call ids, opt-in `KeyAccess`, `claude-sonnet-5` row) and the W04 `ModelUnsupported` fix, proven live.
- A free, offline EN/ES LocalGrammar tier (typed slots, strict number words, refuse-on-ambiguity, `normalize` hook) at the head of the ladder, with zero provider calls.
- PlanThenExecute (one planning call, gated steps, output binding, at most one replan) and start-tier selection (`Custom` picker / opt-in `Router`, loud fallback to Linear).
- Two new published modules: `:undo` (run-level "Undo all (N)") and `:voice-adapter` (`:stt` final segment → `CommandInput`, `:core` still hub-free).
- An on-device spike that measured red (Gemma 4 E2B, 20 s warm p50 on the S22), so nothing shipped, and ML denial became a mechanical `check` gate.

### What Worked
- Running the spike in parallel with Phase 12, time-boxed, got SB/CT a numeric verdict early. Its machine-computed, reproducible verdict (`verify-spike-verdict.sh --check`) made "red" uncontroversial.
- The carry register (Phase 19) and the pre-cut waiver packet (Phase 20) meant every deferral had an owner and an answer before the tag. Gate-2 found nothing new to decide except two honest "with gap" sign-offs.
- The per-phase Gate-2 as an evidence read-through (agent reads, verifies test names and results on disk, explains; operator decides) was fast and still caught real nuance (SB envelope unmeasured, no real-speech path in the sample).
- One release cut, no retry and no earlyoom kill: the v1.0 OOM lesson (quiet window, swap headroom, single-use daemon) held.

### What Was Inefficient
- Gate-2 hit two GSD tooling defects, both seeded to the technician:
  - the `api-coverage.verify-pre` gate enforces a 200-char COVERAGE.md reason cap only at verify time, so two phases planned 6 days earlier blocked Gate-2 (S-033);
  - VERIFICATION fingerprints shared files, so phases 12-19 read "stale" at close and forced an override closeout again, the same class as v1.0 (S-034).
- The sample has no free-text or mic input, so real-speech grammar coverage can't be exercised anywhere in this repo.
- Phase 20 needed a gap plan (20-13) after a stale `release-cut.sh` selftest fixture turned the first quiet window red.

### Patterns Established
- Every deferral goes into a numbered carry register with an owner; the waiver packet must be answered before any quiet window opens.
- New published modules are data (`scripts/modules.list`) read by every gate script, so no gate can silently skip one.
- Optional hubs (`:stt`) enter only through an adapter module with a `compileOnly` dependency and a POM gate that also fails when vacuous.

### Key Lessons
1. Run every verify-time gate (e.g. `api-coverage.verify-pre`) at plan time too, or Gate-2 inherits stale format failures.
2. A spike's "unmeasured" must stay distinct from "measured fail" all the way to sign-off; record it as a gap, not a pass.
3. When the sample can't exercise a user-facing path (real speech), say so in the Gate-2 fragment and route the proof to the first consumer.
4. Expect fingerprint-stale VERIFICATIONs on every multi-phase milestone until S-034 is fixed. Use an override closeout citing the cut gate; don't re-verify for the sake of it.

### Cost Observations
- Model mix: not measured for this milestone.
- Notable: live provider spend stayed tiny: Phase 19 Gate-1 used 4 of 16 requests (about USD 0.0044); the PROV-16 smoke used 2 of 4 requests.

---

## Cross-Milestone Trends

### Process Evolution

| Milestone | Phases | Key Change |
|-----------|--------|------------|
| v1.0 | 11 | First multi-repo slice: A13 reconvene gates, §11 tag ledger via orchestrator, isolated wiring tests before tags |
| v1.1 | 9 | Subagent-driven milestone run; carry register + pre-cut waiver packet; per-phase evidence Gate-2; parallel time-boxed spike |

### Cumulative Quality

| Milestone | Tests | Coverage | Zero-Dep Additions |
|-----------|-------|----------|-------------------|
| v1.0 | ~535 core + ~411 providers per OkHttp leg + 7 device | not measured | `:core` has only coroutines + serialization |
| v1.1 | ~1112 core + ~609 providers per OkHttp leg + 105 keystore + 33 voice-adapter + :undo/:sample suites + 5 device legs | not measured | `:undo` stdlib-only; `:voice-adapter` depends only on `:core` (`:stt` compileOnly) |

### Top Lessons (Verified Across Milestones)

1. Release cuts on this host need a quiet window, swap headroom and a single-use daemon (v1.0: 5 kills; v1.1: clean first try once applied).
2. GSD verification fingerprints over shared files make every multi-phase close an override closeout (v1.0 and v1.1; seeded as S-034).
3. Isolated wiring tests on the final SHA keep finding real doc stumbles (v1.0 and v1.1); budget a docs patch after each tag.
