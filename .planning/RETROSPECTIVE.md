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

## Cross-Milestone Trends

### Process Evolution

| Milestone | Phases | Key Change |
|-----------|--------|------------|
| v1.0 | 11 | First multi-repo slice: A13 reconvene gates, §11 tag ledger via orchestrator, isolated wiring tests before tags |

### Cumulative Quality

| Milestone | Tests | Coverage | Zero-Dep Additions |
|-----------|-------|----------|-------------------|
| v1.0 | ~535 core + ~411 providers per OkHttp leg + 7 device | not measured | `:core` has only coroutines + serialization |

### Top Lessons (Verified Across Milestones)

1. (to be filled as lessons repeat across milestones)
