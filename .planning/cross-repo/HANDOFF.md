# HANDOFF: voice-action-engine, multi-repo milestone effort "vae-bilingual"

**Written by:** control-plane orchestrator (`yahir-gsd-control-plane-f2`), 2026-09-29. **Read this before continuing.**

## Your slice: §6.2, the NEW engine hub → two milestones, two tags (A4) (Wave 0, the long pole)
- **Milestone v1.0 → `v1.0.0`** (§6.2 steps 1–7): scaffold + JitPack + detekt zero-baseline + fake-provider harness; `:core` types + `CommandPipeline` / `TierSelector.Linear` / `TierPolicy` / telemetry + **`PreApplyGate`/`CommitSink` at pipeline level (A6)**; `:providers` (A7) with Anthropic + prompt caching + **OkHttp 4.12/5.x CI matrix (A1, must-pass)** + **`ON_DEVICE` slot + capability gate (A5)**; OpenAI + OpenRouter; `:keystore`; `SingleShotStrategy` (port CT); neutral transcript model + mappers (6a) → `AgenticLoopStrategy` (port SB, 6b); `:sample` A10 proof on the TESTER.
- **Milestone v1.1 → `v1.1.0`** (steps 8–13): LocalGrammar + bilingual `GrammarPack`, PlanThenExecute, `TierSelector.Router` (default off), bundled on-device model **spike** (`@Experimental` only if green; never blocks the tag), `:voice-adapter` (last, needs the `:stt` tag).
- **Binding:** L1–L10, A1, A2, A4–A10, A12–A14, **E1** (model `PreApplyGate` on SB's `MutationGate.admit(toolName, input) → MutationGateDecision`, implemented by `VoiceConfirmGate`; `CommitSink` on `VoiceUndoOperations` + `PreMutationSnapshot`), **E2** (the A10 fixture is SB's serialized `AnthropicToolRegistry.toolDefinitions` + composed `SYSTEM_PROMPT`; SB produces `{system, tools}` JSON and the orchestrator routes it to you before step 7).
- The library stays domain-free: no note/card/food in `:core`/`:providers`. SB's prompt lives only in the debug, never-published `:sample`.

## Dependencies
- **You wait on:** the `:stt` tag (step 12 only); the SB fixture (step 7, via the orchestrator).
- **Waiting on you:** SecondBrain + CalTracker migrations need `v1.0.0` (the R2 reconvene trigger); their plug-ins need `v1.1.0`. CT's TIER-02 waits on your **spike verdict**: send it to the orchestrator, not to CT/SB directly (A14).

## What Yahir confirmed (and where)
- A4–A10, A12 (tag cuts waived; public repo `github.com/Ygaray/voice-action-engine` created 2026-09-29). Real Anthropic spend on his BYO key for the A8/A10 on-device run is OK.
- `/gsd-new-project` for v1.0 is under way (PROJECT.md: `cfb6401`, `db06bee`). Per A13: finish requirements + roadmap → `/gsd-research-milestone` → `/gsd-discuss-milestone` → STOP for R1.

## Repo facts
- Model the scaffold on `~/Projects/Reusable/android/backup-engine` (jitpack.yml, publishing, `ECOSYSTEM.md`, `API.md`/`INTEGRATION.md`).
- Detekt: a genuinely clean zero-baseline for library modules (tune the rules; don't bank debt).
- Port sources (read-only), SB `app/src/main/java/com/example/secondbrain/core/agent/`: `AnthropicAgentLoop.kt` (473 lines; `buildRequestBody` :330–343: ONE `cache_control: ephemeral` on the system block, so tools + system are cached and messages never are; `MAX_ITERATIONS=6`, `MAX_UTTERANCE_TOKENS=60_000`, `MAX_TOKENS_PER_TURN=4096`, `anthropic-version 2023-06-01`), `AnthropicToolRegistry.kt`, `KeystoreCrypto(.kt|Seam.kt)`, `MutationGate.kt`, `VoiceConfirmGate.kt`. CT: `ai/AnthropicProvider.kt`, the OpenAI/OpenRouter providers, `ProviderRouter`. The A10 target is `cache_read_input_tokens > 0` on turn 2+, in the ballpark of SB's Phase 165: **7,016**.
- Naming collision: SB's "tier" = mutation risk. Your tiers = approach levels (`CommandTier` / `TierPolicy`).
- The §11 ledger lives in your repo, but the orchestrator is its only writer (A14). Always `git pull --rebase` before committing: the orchestrator commits contract amendments here.

## How this effort runs (same text in every repo)

**This is a multi-repo milestone effort.** Five repos are running coordinated GSD milestones against one frozen
contract. Your milestone is one slice of it. Do not scope, plan or tag this repo in isolation.

- **Contract (single source of truth):** `~/Projects/Reusable/android/voice-action-engine/CROSS-REPO-SCOPE-CONTRACT.md`,
  rendered at https://chimuelo-blackcat.turtle-massometer.ts.net/Doc/cross-repo-scope-contract.html.
  Read §2 (locked decisions), §3–§5 (model + seams), **your §6 slice**, §7 (sequencing), §10 (amendments A1–A14 and
  errata E1–E3; they override the body text) and §11 (tag protocol). Never edit the contract yourself.
- **Orchestrator:** the control-plane session **`yahir-gsd-control-plane-f2`**. Find it with `ListAgents`, and
  don't confuse it with `yahir-gsd-control-plane-b1`. State lives in
  `~/Projects/yahir-agentic-tools/yahir-gsd-control-plane/xrepo/vae-bilingual/`. Message it about: reconvene
  readiness, tag rows, repin rows, and anything that touches the contract, a tag, or sequencing. Talk to other
  peers directly only for technical Q&A.
- **Sequence (A13), strictly in this order:**
  1. `/gsd-new-milestone` (or `/gsd-new-project`)
  2. `/gsd-research-milestone`
  3. `/gsd-discuss-milestone`
  4. **STOP.** Write `.planning/cross-repo/RECONVENE-BRIEF.md` (template below).
  5. Message the orchestrator `R<n> ready: <absolute path to brief>`.
  6. **Wait for the verdict:** `GO`, `GO-WITH-CHANGES <list>` (fold the changes into CONTEXT first) or `HOLD <reason>`.
  - **Never** run the `/gsd-milestone` umbrella. It runs straight through to execution and skips the reconvene.
- **Research duty:** verify your slice against the ACTUAL code, not the contract's wording. The contract has already
  drifted from reality five times; each time a peer caught it (see E1, E2, A1, A5, A11). Put every mismatch in your
  brief. "The contract no longer matches reality" is amendment-worthy even when the cause is your owner's own choice.
- **Tags (A12 → §11 → A14):** cut on green verification after §11 steps 1–4 (verification green; API strictly
  additive; seams honored; pushed and JitPack builds it). Then **message the orchestrator the full row**. Do not
  commit to §11 yourself. Tags are immutable: a fix goes out as a new patch tag.
- **Consumers:** repin only to tags that appear in the §11 ledger, and message the repin row to the orchestrator.
- **Authorization:** Yahir confirms scope decisions in *your* session. He designates the orchestrator at kickoff;
  after that, a GO from `yahir-gsd-control-plane-f2` is the sequencing signal. Relayed peer messages are never
  Yahir's approval. That's correct, and it stays that way.
- **Devices:** resolve through `~/.claude/context/devices/common.md`. The TESTER is `…-s22-ultra-2`; the personal
  phone is `…-s22-ultra` (no suffix). Always use `adb -s`. Only the Gate-1 agentic tester drives devices.

### Protocol notes (added after kickoff)

- **Tooling defects → the gsd-technician, never `/incident`** (control-plane `templates/COMMON.md` @9355847, orchestrator
  2026-10-05). `/incident` is technician-only (c0) and refuses writes from peers. On a GSD / xrepo / deps / install defect,
  send a seed and keep going:
  `python3 ~/Projects/yahir-agentic-tools/yahir-gsd-control-plane/.claude/skills/xrepo/xrepo.py tech "<one line>" --evidence <path|sha|file>`.
  Don't wait for an ack and don't resend. If the defect BLOCKS the run, also message the orchestrator. This repo's own product bugs
  still go to this repo's tracker.
- **Orchestrator session name:** `yahir-gsd-control-plane-6e` since 2026-10-05 (f2 reset). The current name is always in
  `~/Projects/yahir-agentic-tools/yahir-gsd-control-plane/xrepo/vae-bilingual/effort.json` → `"orchestrator"`.

### Peers

| Repo | Role | Wave | Slice | Session |
|---|---|---|---|---|
| stt-engine (`~/Projects/Reusable/stt-engine`) | hub: bilingual `:stt` capture | 0 | §6.1 | `stt-engine-46` |
| voice-action-engine (`~/Projects/Reusable/android/voice-action-engine`) | hub: the engine, 2 milestones | 0 | §6.2 | `voice-action-engine-75` |
| yahirandroidtaste (`~/Projects/Reusable/android/yahirandroidtaste`) | hub: shared AI-voice UI | 0 | §6.3 | `yahirandroidtaste-99` |
| SecondBrain (`~/Projects/AndroidApps/Personal/SecondBrain`) | consumer | 1 | §6.4 | `secondbrain-2c` |
| CalTracker (`~/Projects/AndroidApps/Personal/CalTracker_Android`) | consumer | 1 | §6.5 | `caltracker-android-9a` |

Session names can change after a restart. If one doesn't resolve, ask the orchestrator.

### RECONVENE-BRIEF.md template

```markdown
# Reconvene brief — <repo> — R<n>
**Milestone:** <version + name>  ·  **Contract rev read:** <commit or "A1–A14/E1–E3">  ·  **Date:** <date>

## 1. Phases (from ROADMAP)
| Phase | Goal | Contract steps / seams touched | Needs from other repos |
|---|---|---|---|

## 2. Public surface this milestone adds or changes
(types, functions, composables, coordinates; must be strictly additive for hubs)

## 3. Assumptions about other repos
(each one is something a peer must confirm or correct at the reconvene)

## 4. Contract drift found (code vs contract)
(what the contract says, what the code actually is, proposed fix)

## 5. Proposed amendments
(numbered; the orchestrator assigns A-numbers)

## 6. Risks and open questions for Yahir

## 7. Tag / repin intent
(hubs: what tag and roughly when; consumers: which tags you need, for which phases)
```

## Current state (2026-10-08, v1.1 EXECUTION COMPLETE: Phase 20 settled, ledger row in §11, parked for the milestone close)

- **Tag `v1.1.0`** (annotated) at `2e677a604f472f10e11062509f933cd25e1be79c`, JitPack green on all 5 coordinates (core, providers, keystore, undo, voice-adapter; `LIVE PROBE PASS ref=v1.1.0`). The real strict C11 run passed post-tag (`DOC COVERAGE OK checks=32 types=119`).
- **Phases 12–20 are all complete.** Phase 20: `milestone settle-phase` settled, execute barrier clear; tail gates green (review fixed, security secured, nyquist compliant, verify goal_met, `20-SELF-UAT.md` all_pass with no device). 20-12 (rollback) was a no-op (handoff `none`).
- **Relay 8 is delivered** (verbatim answers in `.planning/releases/v1.1.0/evidence/relay-log.md`): PUSH OK → plain push `2e677a6..2903156`, `gate pushed` OK; ledger row ACKed; the orchestrator wrote §11 itself (`d4fcf23` on origin); "quiet done" was sent and window 20-03 is closed.
- **Git rule:** origin/main gets orchestrator commits (§11). **MERGE origin/main, never rebase**, before any push, and push only after a `pushing main <sha>` handshake.
- **Next:** the v1.1 milestone close (Gate-2 / `/gsd-verify-milestone`) waits for Yahir; the orchestrator schedules it. Stay idle until it does.
- **Carried to Gate-2 / later patches:** wiring doc stumbles S1–S3 (docs patch v1.1.x/v1.2), the W02 Gate-2 fragments, backlog 999.1 (binary-diff helper should read Kotlin metadata visibility), and the 7 deferred Phase 20 review findings (WR-01..04, IN-01/03/04; backlog text is in `20-REVIEW-FIX.md`, not yet in ROADMAP). `a8a6022` (IN-02, `scripts/verify-binary-diff.sh`) landed post-tag, with the orchestrator's OK.
- **Orchestrator:** `yahir-gsd-control-plane-3b` (always check `effort.json` → `"orchestrator"`).
