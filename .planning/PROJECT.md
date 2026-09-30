# voice-action-engine

## What This Is

A generic, STT-agnostic Android/JitPack library (`com.github.Ygaray:voice-action-engine`) that turns a spoken command — `CommandInput(transcript, language "en"|"es"|null, context)` — into an app action. Each consumer app composes its own **tier ladder** of strategies (LocalGrammar / SingleShot / PlanThenExecute / AgenticLoop) running over pluggable providers (Anthropic / OpenAI / OpenRouter / on-device), cheap-first and escalating only when needed. It is the shared hub for Yahir's personal apps (SecondBrain, CalTracker, and every future one); it names no app domain.

**This milestone (v1.0 → tag `v1.0.0`)** = contract §6.2 steps 1–7: scaffold + contract types + pipeline + provider transports + `:keystore` + SingleShot + provider-neutral AgenticLoop + PreApplyGate/CommitSink hooks. It unblocks the Wave-1 migrations of SecondBrain and CalTracker.

## Core Value

A consumer app can hand the engine a transcript and get back a correct, typed outcome through a tier ladder it composed itself — with the cloud agentic path working on-device (Anthropic, prompt cache hitting) and every failure surfaced as a specific, loud reason, never a silent or opaque one.

## Source of Truth

**`CROSS-REPO-SCOPE-CONTRACT.md`** (repo root) — FROZEN v1.0 + amendments A1–A12. This slice is **§6.2** (v1.0 = steps 1–7); the engine seams are **§5.1–5.2**; the tag protocol and ledger are **§11**. It is never edited by this session: changes are proposed to the control plane (yahir-gsd-control-plane-f2, orchestrator) and recorded as numbered §10 amendments / errata. Other sessions commit contract-only changes here, so always `git pull --rebase` before committing.

## Requirements

### Validated

(None yet — ship to validate)

### Active

**Scaffold & publishing (step 1)**
- [ ] Multi-module Gradle project: `:core` (pure Kotlin, no HTTP), `:providers` (OkHttp transports, depends on `:core`; A7), `:keystore` (Android AES/GCM BYO-key), `:sample` (debug-only, never published; A10). `:voice-adapter` is v1.1.
- [ ] Mechanism-B JitPack publishing matching `~/Projects/Reusable/android/backup-engine` (`jitpack.yml`, maven-publish); publishable modules resolve from a clean Gradle cache.
- [ ] detekt with a genuinely clean zero baseline on every library module (tune rules, never bank debt).
- [ ] Fake-provider test harness so `:core` strategies/pipeline are JVM-testable with no network.
- [ ] Control-plane registry entries (ecosystem map / deps index) and `ECOSYSTEM.md` kept current.

**Core contract & pipeline (step 2)**
- [ ] §5.1 types: `CommandInput`, `CommandStrategy`, `StrategyOutcome` (`Completed | Escalate(reason, carry?) | NoMatch | Failed`), `CommandPipeline` DSL, `TierSelector.Linear` (+ `Fixed` for tests), `TierPolicy` (offlineOnly, maxTier, allowedProviders, cost/turn ceilings).
- [ ] Escalation: `Escalate`/`NoMatch` → next tier; `Completed`/`Failed` → stop; `carry` passes partial work upward.
- [ ] `PreApplyGate` modeled on SB's real seam `MutationGate` (`suspend fun admit(toolName, input) → decision`; erratum E1), generalized per A2 (any confirm-before-commit policy → proceed | needs-confirmation + reason) and `CommitSink` (commit + undo, shaped by SB `VoiceUndoOperations` + `PreMutationSnapshot`) as pipeline-level hooks with suspend-before-commit behavior (A6). A held call reports a non-committing result, never a success.
- [ ] Telemetry = **trace on every result** (per-tier attempts, escalation reasons, tokens in/out, cache-read/creation tokens, latency) **plus an optional live typed-event callback**. Never carries the API key, transcript, tool arguments or tool_result content.

**Providers (steps 3a/3b)**
- [ ] Provider-neutral request/result types; `AiProvider` / `ProviderRouter` with `ANTHROPIC | OPENAI | OPENROUTER | ON_DEVICE` (CT design, without Hilt).
- [ ] Anthropic transport with prompt caching as a provider capability (3a).
- [ ] **A1 must-pass:** compile against the OkHttp 4.12 API floor; CI matrix green on both 4.12.x and 5.x.
- [ ] `ON_DEVICE` slot + runtime capability gate — absent on the S22s → clean cloud fallback; Nano-ready by design, no Nano implementation (L10/A5).
- [ ] OpenAI + OpenRouter transports (3b).
- [ ] Engine asks the app per call for active provider / model / key via a seam (a switch takes effect on the next call); never substitutes one provider's key for another's.

**Keystore (step 4)**
- [ ] `:keystore` generalizes the shared `KeystoreCrypto` / `KeystoreCryptoSeam` shape: per-provider aliases, DataStore persistence.

**Strategies (steps 5, 6a, 6b)**
- [ ] `SingleShotStrategy` (port CT): one forced-tool call, app resolves locally via `OutcomeResolver`, uses `ToolSpecProvider`.
- [ ] Neutral multi-turn transcript model + per-provider tool-call mappers (Anthropic tool_use/tool_result ↔ OpenAI tool_calls/tool-role; explicit cache_control vs automatic caching) (6a).
- [ ] `AgenticLoopStrategy` (port SB `AnthropicAgentLoop` onto 6a) over `ToolExecutor`, bounded (iterations + token ceiling from policy).

**v1.0 verification bar & tag (step 7; A8 + A10 + §11)**
- [ ] `:sample` harness: frozen fixture JSON `{system, tools}` produced by SB (serialized `AnthropicToolRegistry.toolDefinitions` + fully composed `SYSTEM_PROMPT` incl. `TAG_DISAMBIGUATION_POLICY`; erratum E2; ~7k-token prefix), fake `ToolExecutor` with canned results, BYO-key field exercising `:keystore`.
- [ ] Breakpoint parity: at least SB's `buildRequestBody` placement — one `cache_control: ephemeral` on the system block (caches tools + system), no breakpoint on messages; a moving message breakpoint only as an addition.
- [ ] Gate-1 on the TESTER: agentic loop runs 2+ turns, `cache_read_input_tokens > 0` on turn 2+, in SB's ballpark (7,016). OpenAI/OpenRouter agentic is JVM-tested only.
- [ ] README integration guide good enough that **an AI agent can wire the engine into an app from the README alone**; `:sample` doubles as the reference wiring.
- [ ] Tag `v1.0.0` per §11: green verification, strictly additive API, contract-honoring, pushed + JitPack builds it; tag row messaged to the control plane (single ledger writer, A14) + broadcast to all peers.

**Port cleanups (leave-behinds become requirements)**
- [ ] No DI-framework annotations in library code (no Hilt `@Inject`/`@Qualifier`/`@IntoMap`); plain constructors/builders, apps wire their own DI.
- [ ] No app-domain types or prompts in library code (`LogFoodResult`, `log_food`, SB `SYSTEM_PROMPT`, `MutationTier` keyed by SB tool names) — all app-injected.
- [ ] Typed failure reasons end-to-end (SB `UnavailableReason`-style granularity: bad key vs network vs malformed vs refusal vs budget) — never CT-style collapse to one opaque `Unavailable`.
- [ ] Typed telemetry events, not free-text log lines.
- [ ] OkHttp calls use the 4.12-compatible API surface (e.g. `body?.string()`, not SB's 5.x-only `body.string()`).
- [ ] Limits and model IDs (max iterations, token ceilings, max tokens/turn, default models) come from policy/config with defaults, not hard-coded constants.
- [ ] The library never reads app settings storage directly.
- [ ] No app planning IDs (T-68-05, WR-02, "Phase 70 D-01") in library comments.

**Keep deliberately (good disciplines from both ports)**
- [ ] Never-throw outcome collapse (cancellation always propagates).
- [ ] No logging interceptor on provider HTTP clients; keys never reach any sink; redacted `toString()` on request types.
- [ ] Cancellation-safe OkHttp `Call.await()` bridge (CT); HTTPS-only fixed base URLs, overridable only for tests.

### Out of Scope (this milestone)

- `LocalGrammarStrategy` + bilingual `GrammarPack` DSL — v1.1 (§6.2 step 8).
- `PlanThenExecuteStrategy` — v1.1 (step 9).
- `TierSelector.Router` — v1.1 (step 10), default off.
- Bundled on-device model spike (Gemma-2B-class via MediaPipe/LiteRT) — v1.1 (step 11); verdict reported to the control plane (yahir-gsd-control-plane-f2) as soon as it lands.
- `:voice-adapter` (`:stt` → `CommandInput`) — v1.1 (step 12), needs the `:stt` tag.
- Gemini Nano / AICore implementation — later version on a Pixel 10 (L10); S22s have no AICore.
- Any dependency on `:stt` or YAT from `:core` (L7).
- UI of any kind in the library (YAT owns presentational AI-voice UI; apps map outcomes → YAT props).
- Telemetry as a `Flow` API — consumers can wrap the callback themselves.
- Real-SB parity proof / an early SB branch on the engine — SB's Wave-1 Gate-1 (A10; option (b) rejected).
- Forcing any consumer's OkHttp version (A1/A11).

## Context

- **Ecosystem:** hub + spokes (see `ECOSYSTEM.md`). Sibling hubs: `stt-engine` (`:stt`, bilingual capture, §6.1) and `yahirandroidtaste` (YAT, §6.3). Consumers in Wave 1: SecondBrain (§6.4, OkHttp 5.2.1) and CalTracker (§6.5, moving to OkHttp 5.2.1 per A11; its v1.13 consumes both engine tags).
- **Peers (cross-session):** **yahir-gsd-control-plane-f2 = orchestrator for this effort and the ONLY writer of the §11 ledger** (message it tag rows + the v1.1 spike verdict; never commit ledger rows here). secondbrain-2c (SB), stt-engine-46, yahirandroidtaste-99, caltracker-android-9a. Handoff file: `.planning/cross-repo/HANDOFF.md`.
- **Port sources (read-only):**
  - SB `~/Projects/AndroidApps/Personal/SecondBrain/app/src/main/java/com/example/secondbrain/core/agent/`: `AnthropicAgentLoop.kt` (loop, `buildRequestBody`, `SYSTEM_PROMPT`), `AgentLoopResult.kt` (typed `UnavailableReason`, `BudgetBound`), `MutationGate.kt` + `VoiceConfirmGate.kt` (→ PreApplyGate shape, E1; `MutationTierPolicy.kt` only classifies risk and is consulted by the gate), `VoiceUndoOperations.kt` + `PreMutationSnapshot.kt` (→ CommitSink), `SecondBrainToolFacade.kt` + `RoomToolFacade.kt` (→ ToolExecutor), `KeystoreCrypto.kt` + `KeystoreCryptoSeam.kt` (→ `:keystore`). The A10 fixture is NOT taken from `ToolSchemas.kt`; SB hands over `{system, tools}` JSON (E2).
  - CT `~/Projects/AndroidApps/Personal/CalTracker_Android/app/src/main/java/com/caltracker/app/`: `ai/AiProvider.kt`, `ai/ProviderRouter.kt`, `ai/AnthropicProvider.kt` (+ `Call.await`), `ai/LogFoodRequestBuilder.kt`, `ai/OpenAiLogFoodRequestBuilder.kt`, `mcp/RepositoryToolFacade.kt` (→ OutcomeResolver), `data/security/KeystoreCrypto*.kt`, `di/AiModule.kt`. Ask caltracker-android-9a for anything ambiguous.
- **Publishing reference:** `~/Projects/Reusable/android/backup-engine` (Mechanism B, `jitpack.yml` openjdk17 + `publishReleasePublicationToMavenLocal`).
- **Remote:** `github.com/Ygaray/voice-action-engine` (public), `main` tracks `origin/main`. Other peers append §11 ledger rows here → always `git pull --rebase` before committing.
- **Devices:** Gate-1 runs on the wired TESTER (`…-s22-ultra-2`); read `~/.claude/context/devices/common.md` before any adb work. Never the personal phone.

## Constraints

- **Dependency structure**: `:core` depends on no other hub and has no HTTP dependency (L7, A7) — keeps the engine STT-agnostic and JVM tests light.
- **Compatibility**: OkHttp compile floor 4.12, CI green on 4.12.x and 5.x (A1) — consumers keep their own OkHttp version.
- **API evolution**: public API grows strictly additively once tagged; tags are immutable, fixes = new patch tag + superseded ledger row (§11).
- **Domain-free**: the library names no note/card/food; all app knowledge enters through §5.2 seams.
- **Quality**: detekt zero baseline on library modules; two-gate UAT where device-verifiable; most of `:core` JVM-tested.
- **Secrets**: API keys, transcripts, tool args/results never reach logs, telemetry, exceptions or `toString()`.
- **Process**: contract changes only via §10 amendments through the control plane; tag cuts are agent-owned under A12 (Yahir confirmed push + tag authority for this effort, 2026-09-29).

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Two GSD milestones: v1.0 = steps 1–7, v1.1 = 8–13 (A4) | L8 staged tags; unblocks Wave-1 migrations early | — Pending |
| `:providers` module separate from `:core` (A7) | Keep `:core` pure Kotlin, no HTTP for consumers or JVM tests | — Pending |
| PreApplyGate/CommitSink at pipeline level (A6) | CT gates SingleShot, not just agentic | — Pending |
| ON_DEVICE slot + capability gate in v1.0 (A5) | L10 Nano-ready by design; S22s fall back to cloud | — Pending |
| Prove A8 via `:sample` with frozen SB fixture (A10) | No consumer before the tag; tiny prompts fall below the cache minimum | — Pending |
| Telemetry = trace on result + optional typed callback (no Flow) | Deterministic, testable, feeds YAT "handled by" indicator; callback for live sinks | — Pending |
| No DI framework in library; per-call provider/model/key seam | Libraries shouldn't force Hilt; engine shouldn't own app settings | — Pending |
| Typed failure reasons (SB-style), not opaque collapse (CT-style) | Loud, specific failure UI | — Pending |
| "Done" = functional + an AI agent can wire it from the README | Every future app is wired by an agent | — Pending |
| Tag cuts waived, agent-owned via §11 (A12) | Yahir waived; peers jointly own correctness | — Pending |

## Evolution

This document evolves at phase transitions and milestone boundaries.

**After each phase transition** (via `/gsd-transition`):
1. Requirements invalidated? → Move to Out of Scope with reason
2. Requirements validated? → Move to Validated with phase reference
3. New requirements emerged? → Add to Active
4. Decisions to log? → Add to Key Decisions
5. "What This Is" still accurate? → Update if drifted

**After each milestone** (via `/gsd-complete-milestone`):
1. Full review of all sections
2. Core Value check — still the right priority?
3. Audit Out of Scope — reasons still valid?
4. Update Context with current state

---
*Last updated: 2026-09-29 after initialization*
