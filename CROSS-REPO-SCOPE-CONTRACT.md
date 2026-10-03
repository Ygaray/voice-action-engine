# Cross-Repo Scope Contract — Bilingual Voice Commands + `voice-action-engine`

**Status:** FROZEN v1.0 + amendments A1–A19 + errata E1–E7 (2026-09-29): amendments approved by Yahir. Errata are verified factual corrections recorded by the orchestrator.
**Orchestrator (A14):** the control plane, session `yahir-gsd-control-plane-f2`. State: `~/Projects/yahir-agentic-tools/yahir-gsd-control-plane/xrepo/vae-bilingual/`. Changes go through a numbered **Amendment** section at the end, never by silent edits.
**Home:** `~/Projects/Reusable/android/voice-action-engine/CROSS-REPO-SCOPE-CONTRACT.md`, the single source of truth. Every peer milestone cites this path.

---

## 1. Mission

Extract the AI/voice-command plumbing SecondBrain (SB) and CalTracker (CT) each built independently into ONE reusable hub, **`voice-action-engine`**, expand it from 2 execution approaches to a full **menu of approaches**, and make commands **bilingual EN/ES** (speak either language, no pre-selection, offline capture). Every future personal app reuses it.

Each consumer app **composes its own command pipeline**: it picks which approaches it uses, supplies the app-specific plug-ins (seams) for each, and orders them into **tiers** — cheap/offline first, escalating to more capable/expensive approaches only when needed.

## 2. Locked decisions

| # | Decision | Source |
|---|---|---|
| L1 | Bilingual = **Path B** (native, offline, `language="auto"`), not server routing | Yahir; proven by stt-engine plan-149 probe on S22 (5/6, the miss was intra-sentence code-switch) |
| L2 | Both existing strategies (agentic loop, single-shot) extracted as **pluggable** | Yahir |
| L3 | New hub for the engine; bilingual capture in `:stt`; shared AI-voice UI in YAT this milestone | Yahir |
| L4 | Hub name **`voice-action-engine`** (renamed from working name `command-engine`: specific and findable; carries the voice-commands→app-actions purpose) | Yahir (2026-09-29) |
| L5 | All additional approaches (local grammar, plan-then-execute, escalation, router, on-device) are **in scope in `voice-action-engine`**; consumers design a plug-in per approach and a **tier policy** that switches between them | Yahir (2026-09-29) |
| L6 | Coherent cross-repo scoping FIRST, then one GSD milestone per repo | Yahir |
| L8 | **Staged engine tags**: `v1.0.0` = contract + pipeline + providers + keystore + 2 ported strategies (unblocks Wave-1 migrations); `v1.1.0` = grammar / plan / router / on-device | Yahir (2026-09-29) |
| L10 | **On-device = Nano-ready by design.** The `ON_DEVICE` provider seam + runtime capability gate are shaped so a future version fully implements AICore/Gemini Nano, verified on a **Pixel 10**. This milestone: seam + gate (absent on S22s → falls back to cloud) + bundled-model spike in v1.1. No Nano implementation until a Nano-capable device is in hand. | Yahir (2026-09-29) |
| L9 | Create the engine repo now; the frozen contract lives at its root | Yahir (2026-09-29) |
| L7 | Dependency structure **Option A** — engine is STT-agnostic (takes a transcript + language label); the 3 hubs don't depend on each other; optional `:voice-adapter` bridges to `:stt` | Yahir (2026-09-29) |

## 3. Core model — two orthogonal axes

The key design idea: **"how a command is handled" and "where inference runs" are separate choices.**

- **Strategy** = *how* a command becomes an action (grammar match, one forced-tool call, plan-then-run, agentic loop).
- **Provider** = *where* the model runs (Anthropic, OpenAI, OpenRouter, **on-device**).

Any LLM-backed strategy runs on any provider. So "on-device model" is **not a strategy** — it is a provider that single-shot / plan-then-execute can sit on top of. And "escalation hybrid" is **not a strategy** — it is the pipeline itself. "Router" is a **tier selector**.

```
transcript + language  ─►  CommandPipeline
                             │  TierSelector (Linear | Router)  +  TierPolicy (caps)
                             ▼
             ┌──── Tier 0: LocalGrammarStrategy      (no LLM, offline, ~0 cost)
             ├──── Tier 1: SingleShotStrategy        ─┐
             ├──── Tier 2: PlanThenExecuteStrategy    ├─ each on a Provider:
             └──── Tier 3: AgenticLoopStrategy       ─┘  Anthropic | OpenAI | OpenRouter | OnDevice
                             │
             outcome: Completed | Escalate(reason) | NoMatch | Failed
                             ▼
             PreApplyGate ─► commit ─► CommitSink (undo)  ─►  app maps outcome → YAT sheet
```

## 4. The approaches (what `voice-action-engine` ships)

| Approach | Kind | LLM calls | Cost | Offline | Good for | Ported from / new |
|---|---|---|---|---|---|---|
| **LocalGrammar** | Strategy | 0 | ~0 | ✅ | High-frequency fixed-shape commands ("add X to Y", "log 2 eggs") | NEW |
| **SingleShot** | Strategy | 1 (forced tool) | Lowest LLM | via OnDevice | Pure extraction; app resolves locally | CT (port) |
| **PlanThenExecute** | Strategy | 1 (+1 optional replan) | Low | via OnDevice | Multi-step commands whose steps don't depend on lookup results | NEW |
| **AgenticLoop** | Strategy | N (3–10) | Highest; caching cuts repeated prefix | ❌ (for now) | Commands that must look things up before deciding | SB (port, generalized off Anthropic-only) |
| **Escalation** | Pipeline behavior | — | Pay for capability only when needed | — | Everything: cheap-first ladder | NEW |
| **Router** | TierSelector | 1 small/cheap | Adds a call; saves on skipped tiers | depends | Picking the start tier instead of climbing | NEW |
| **OnDevice** | Provider | local | $0 API | ✅ | Offline single-shot / plan | NEW — **spike-gated** (see §8) |

## 5. Shared contract — seams (the coherence anchor)

### 5.1 Engine-owned types (`:core`, pure Kotlin, JVM-testable)

- `CommandInput(transcript: String, language: String? /* "en" | "es" | null */, context: Map<String, Any?>)` — `language` comes from `FinalSegment.language`; lets prompts/grammar reply and write in the spoken language.
- `CommandStrategy { val id: StrategyId; suspend fun execute(input, session): StrategyOutcome }`
- `StrategyOutcome = Completed(result) | Escalate(reason, carry?) | NoMatch | Failed(error)` — `carry` passes partial work (e.g. extracted entities) up to the next tier so it isn't redone.
- `CommandPipeline` — built by the consumer: `commandPipeline { tier(grammar); tier(singleShot); tier(agentic); selector = Linear; policy = … }`. Walks tiers; `Escalate`/`NoMatch` → next tier; `Completed`/`Failed` → stop.
- `TierSelector` — `Linear` (default: climb from tier 0) | `Router(provider)` (cheap model picks the start tier) | `Fixed(tier)` (debug/testing).
- `TierPolicy` — runtime caps the app exposes as user settings: `offlineOnly`, `maxTier`, `allowedProviders`, per-command cost/turn ceiling. Offline-only ⇒ grammar + on-device only.
- `AiProvider` / `ProviderRouter` (multibinding: `ANTHROPIC | OPENAI | OPENROUTER | ON_DEVICE`) — CT's design is the base; **prompt caching** (SB) becomes a provider capability, used where supported.
- Provider-neutral request/result/mapper types.
- `PipelineTelemetry` — per command: which tier handled it, escalation reasons, tokens in/out, cache-read tokens, latency. Lets each app tune its ladder with evidence.

### 5.2 App-injected seams (plug-ins each consumer designs)

| Seam | Used by | SB plugs in | CT plugs in |
|---|---|---|---|
| `GrammarPack` (per-language rules → intent + slots; EN + ES) | LocalGrammar | SB command grammar | "log N food" grammar |
| `ToolSpecProvider` (schemas + system prompt) | SingleShot, Plan, Agentic, Router | 17-tool `ToolContract` | 1-tool `log_food` no-macro-fabrication schema |
| `ToolExecutor` (tool_use → tool_result) | Plan, Agentic | `RoomToolFacade` (`SecondBrainToolFacade`) | N/A unless CT adopts Plan/Agentic |
| `OutcomeResolver` (structured extraction → app action) | SingleShot, LocalGrammar | TBD by SB | `RepositoryToolFacade` (local Room resolution) |
| `PreApplyGate` (any confirm-before-commit policy; see A2) | all mutating paths | `MutationTierPolicy` (mutation risk) | weak-match / batch confirm (`VoiceResultSheet` Proposed / ProposedBatch; `WEAK_MATCH_THRESHOLD`, `PARSE_CONFIDENCE_FLOOR`) |
| `CommitSink` (commit + undo) | all | Undo Center | `VoiceLogViewModel` |
| `TierPolicy` defaults + settings binding | pipeline | SB settings | CT settings |

⚠ **Naming collision:** SB's `MutationTierPolicy` uses "tier" for mutation *risk*; pipeline tiers are *approach* levels. Engine names the pipeline type `CommandTier` / `TierPolicy`; SB's gate keeps its name behind `PreApplyGate`. Flag during planning.

### 5.3 Capture contract (`:stt`)

`SttConfig(language = "auto")` + `FinalSegment.language` (slots already exist). `:stt` makes `"auto"` real on native, additive, runtime-gated `SDK_INT >= 34`; minSdk stays 33 (below 34, `"auto"` falls back to the configured default language).

## 6. Per-repo milestone slices

### Wave 0 (parallel)

**6.1 stt-engine — bilingual native capture** → cuts `:stt` tag
- `language="auto"` single-utterance EN/ES detection, offline, label on `FinalSegment.language`.
- Label derived from `confidenceLevel == 3` (highly-confident) detection events only, not raw per-event (the raw stream is noisy while decoding is correct).
- Always pass `EXTRA_LANGUAGE_SWITCH_ALLOWED_LANGUAGES = [en-US, es-US]`.
- Gate-1 criterion: validate with **real speakers / real mic** (plan-149 probe was n=1 synthesized TTS).
- SDK_INT < 34 fallback is explicit and tested.

**6.2 voice-action-engine — NEW repo** (`~/Projects/Reusable/android/voice-action-engine`, `com.github.Ygaray:voice-action-engine`) → cuts **two** engine tags, as **two GSD milestones** (A4)
Modules: `:core` (pure Kotlin: interfaces + neutral types, no HTTP) · `:providers` (OkHttp transports, depends on `:core`; A7) · `:keystore` (Android AES/GCM BYO-key) · `:voice-adapter` (optional, depends on `:stt`).

*Engine milestone v1.0 → tag `v1.0.0`* (unblocks the Wave-1 migrations)
1. Repo scaffold, JitPack publishing, detekt zero-baseline, fake-provider test harness, control-plane registry entries.
2. `:core` contract types + `CommandPipeline` / `TierSelector.Linear` / `TierPolicy` / telemetry (escalation lives here) + **`PreApplyGate` / `CommitSink` hook types and suspend-before-commit behavior at pipeline level** (A6; strategies only exercise them).
3a. `:providers`: provider-neutral request/result types + Anthropic transport + prompt caching as a provider capability + the **A1 OkHttp 4.12/5.x CI matrix** (must-pass) + the **`ON_DEVICE` enum slot + runtime capability gate** (absent → cloud fallback; A5).
3b. `:providers`: OpenAI + OpenRouter transports (port CT `AiProvider` / `ProviderRouter`).
4. `:keystore`: generalize the shared `KeystoreCrypto` / `KeystoreCryptoSeam` shape (per-provider aliases, DataStore).
5. `SingleShotStrategy` (port CT).
6a. Neutral multi-turn transcript model + per-provider tool-call mappers (Anthropic tool_use/tool_result ↔ OpenAI tool_calls/tool-role; explicit cache_control vs automatic caching) (A8).
6b. `AgenticLoopStrategy` (port SB `AnthropicAgentLoop` / `AgentLoopRunner` onto 6a).
7. Tag `v1.0.0` (tag-cut gate **waived**, A9: cut on green verification).
   **v1.0 verification bar (A8, made precise by A10):** a debug-only, never-published `:sample` harness on the TESTER runs the agentic loop on Anthropic with a frozen snapshot of SB's real tool schemas + system prompt (~7k-token prefix) as a fixture; cache breakpoint placement matches SB; `cache_read_input_tokens > 0` on turn 2+. OpenAI/OpenRouter agentic runs are covered by JVM tests with the fake provider only. Full real-SB parity is proven in SB's Wave-1 Gate-1.

*Engine milestone v1.1 → tag `v1.1.0`*
8. `LocalGrammarStrategy` + bilingual `GrammarPack` DSL (EN/ES rules, slot extraction, number words in both languages).
9. `PlanThenExecuteStrategy` (plan schema, step runner over `ToolExecutor`, optional single replan on step failure → else `Escalate`).
10. `TierSelector.Router` (cheap-model classifier to pick the start tier).
11. Bundled on-device model **spike** (Gemma-2B-class via MediaPipe/LiteRT: latency, RAM, strict-JSON reliability); ships `@Experimental` only if green, otherwise defers without blocking the tag (L10).
12. `:voice-adapter`: `:stt` → `CommandInput` glue. Stays last because it needs the `:stt` tag.
13. Tag `v1.1.0` (gate waived, A9).

**6.3 yahirandroidtaste (YAT) — shared AI-voice UI** → cuts YAT tag
Generic presentational composables only; no OkHttp, no engine dependency; the app maps engine outcome → YAT props.
- Settings: provider/key card, model card.
- **Command-approach settings card** (NEW): tier ladder display, offline-only toggle, max-tier cap — driven by props.
- Outcome / failure sheet, including a **"handled by: tier/approach"** indicator and loud, visible failure states.
- Every new composable registered in `ComponentRegistry` (CATALOG-03). Strictly additive to existing public API. `MicButton` already exists.

### Wave 1 (parallel, after all 3 Wave-0 tags)

Each consumer **evaluates every approach** and either designs its plug-in or records a reasoned **N/A** in its milestone requirements. Every consumer ships a tier config + a settings surface for `TierPolicy`.

**6.4 SecondBrain**
- Repin `:stt`, `voice-action-engine`, YAT; **OkHttp stays 5.x**.
- Migrate `AnthropicAgentLoop` → `AgenticLoopStrategy`; `RoomToolFacade` → `ToolExecutor`; `MutationTierPolicy` → `PreApplyGate`; Undo Center → `CommitSink`. Keep prompt caching working (cache-read verified on-device as a Gate-1 criterion).
- Design plug-ins for: LocalGrammar (EN+ES `GrammarPack` for top commands), SingleShot (simple creates), PlanThenExecute, Router (optional).
- Default ladder proposal: `Grammar → SingleShot → Plan → Agentic`; tune from telemetry.
- Adopt bilingual (`language="auto"`); closes backlog **999.116**.
- Adopt the YAT settings/outcome UI.

**6.5 CalTracker**
- Repin `:stt`, `voice-action-engine` (both `v1.0.0` and `v1.1.0`: CT's v1.13 consumes both), YAT. **OkHttp 4.12.0 → 5.2.1 by owner choice (A11)**: catalog version + drop the `:stt` okhttp exclude. Gate-1 must show a real **barcode lookup** and a live **Drive backup/restore round-trip** on the device (clears the precompiled-AAR binary-compat question).
- On-device-backed SingleShot is **conditional** on the engine's v1.1 bundled-model spike (§6.2 step 11). Written into CT's requirements with an explicit fallback (spike red → requirement dispositioned N/A-deferred, never blocks CT's milestone).
- CT's weak-match / batch confirm plugs into `PreApplyGate` (A2); it must not be dropped in migration.
- Migrate its single-shot onto `SingleShotStrategy`; `log_food` → `ToolSpecProvider`; `RepositoryToolFacade` → `OutcomeResolver`; `VoiceLogViewModel` → `CommitSink`.
- Design plug-ins for: LocalGrammar ("log N food" EN+ES), OnDevice-backed SingleShot (offline logging) if the spike is green; decide Plan/Agentic (likely N/A, or queries like "what did I eat yesterday").
- Adopt bilingual. Adopt the YAT settings/outcome UI.

## 7. Sequencing & tag/repin order

```
freeze contract ─► Wave 0: stt-engine ║ voice-action-engine ║ YAT   (parallel)
                    └─ 3 tags cut ─► Wave 1: SecondBrain ║ CalTracker  (parallel repin + integrate)
```

Version deltas resolved by Wave-1 repins: OkHttp: engine floor stays 4.12 (A1); CT moves 4.12.0 → 5.2.1 by choice (A11), so all consumers converge on 5.2.1, MicButton / YAT (CT v2.1.0, SB v2.3.0 → new YAT tag), `:stt` (SB v0.5.0, CT v0.6.0 → new `:stt` tag).

## 8. Risks

- **voice-action-engine is the long pole** (≈12 phases vs. a narrower `:stt` slice). See open question Q2 on staged tags.
- **On-device inference** — device support (AICore / Gemini Nano on the S22) and tool-calling quality are unproven. Treat it like Path B: **probe first on the tester**, and never let it block the tag.
- **Bilingual grammar** — grammars are brittle across two languages; `NoMatch` must escalate cleanly (never guess). Grammar coverage is measured via telemetry, not assumed.
- **Plan-then-execute on lookup-dependent steps** — must detect the dependency and `Escalate` rather than execute a stale plan.
- **Router cost** — adds a call; only worth it if telemetry shows the Linear ladder wasting tiers. Ship it, default it off.

## 9. Open questions (Yahir)

- ~~Q1~~ → L7 (Option A). ~~Q2~~ → L8 (staged). ~~Q4~~ → L9 (create repo).
- ~~Q3~~ → L10 (Nano-ready seam now; full Nano on a Pixel 10 in a later version). Recon: stt-engine-46 confirmed AICore absent on the S22 (RED); bundled Gemma-2B is AMBER pending the v1.1 spike (≈1.3 GB download, ~1–1.5 GB RAM, strict-JSON reliability is the main risk).
- ~~Q5~~ → L4 (`voice-action-engine`).

## 10. Amendments

**A1 — OkHttp version floor (2026-09-29, Yahir; raised by caltracker-android-9a).** v1.0 said CT bumps OkHttp 4.12.0 → 5.x. That understated it: CT pins 4.12 on purpose (constraint LIB-01) because the OpenFoodFacts barcode lookup, the scan client and backup-engine all run on 4.x, and MockWebServer 4.12 breaks on 5.x. **Resolution:** the engine compiles against the OkHttp **4.12 API floor** and CI-tests against **both 4.12.x and 5.x**; Gradle resolves each app's own version (SB 5.2.1, CT 4.12.0). No forced migration this milestone. Must-pass in the engine's transport phase; if 5.x-compat fails, a new amendment restores the forced bump + CT's barcode Gate-1 criterion.

**A2 — PreApplyGate redefined (2026-09-29, Yahir; raised by caltracker-android-9a).** `PreApplyGate` = **any confirm-before-commit policy**, not only mutation-risk. The engine suspends before commit whenever the app's gate returns *needs-confirmation* (with a reason the app/YAT sheet renders). SB plugs `MutationTierPolicy` (risk tier); CT plugs its weak-match / batch confidence confirm (`VoiceResultSheet` Proposed / ProposedBatch). One seam, both apps; YAT's outcome sheet renders the confirm state generically.

**A3 — CT has no sub-34 fallback (2026-09-29; note only).** CT's `:app` minSdk is 35, so `language="auto"` is always native-live for CT; the SDK_INT < 34 fallback (§5.3, §6.1) is tested in `:stt` only, never in CT.

**A4 — Two engine milestones (2026-09-29, Yahir; raised by voice-action-engine-75).** L8's staged tags become two GSD milestones in the engine repo: v1.0 (§6.2 steps 1–7 → `v1.0.0`) and v1.1 (steps 8–13 → `v1.1.0`). v1.0 said there was one tag cut at the end, which contradicted L8.

**A5 — ON_DEVICE slot moves into v1.0 (2026-09-29, Yahir; raised by voice-action-engine-75).** v1.0 put the slot + capability gate at step 10, in v1.1, which contradicted L10. They now land in step 3a. Only the bundled-model spike stays in v1.1.

**A6 — PreApplyGate / CommitSink are pipeline-level (2026-09-29, Yahir; raised by voice-action-engine-75).** Follows from A2 (CT gates SingleShot). The hook types + suspend-before-commit move to step 2; the strategies exercise them.

**A7 — New `:providers` module (2026-09-29, Yahir; raised by voice-action-engine-75).** OkHttp transports live in `:providers` (depends on `:core`), so `:core` stays pure Kotlin with no HTTP dependency and JVM tests stay light. Consumers depend on `:core` + `:providers` (+ `:keystore`). This adds a module; nothing existing is removed. The provider step splits into 3a / 3b.

**A8 — Agentic sizing + v1.0 verification bar (2026-09-29, Yahir; raised by voice-action-engine-75).** Making the loop provider-neutral needs a neutral multi-turn transcript model + per-provider mappers (6a) before the port (6b). v1.0 bar: agentic **verified on-device on Anthropic** (SB cache-read parity); OpenAI/OpenRouter agentic is JVM-tested only.

**A9 — Tag-cut gate waived for voice-action-engine (2026-09-29, Yahir).** Both engine tags cut on green verification, with no human checkpoint. Replaces the conflicting "confirm with Yahir" in the kickoff brief. (Errata in the same revision: §6.2 coordinate `/` → `:`.)

**A10 — A8 proof mechanism (2026-09-29, Yahir; raised by voice-action-engine-75, answered by SB).** No consumer exists before `v1.0.0`, so the engine proves A8 with a **debug-only, never-published `:sample` harness**:
- It uses a **frozen snapshot of SB's real tool schemas + system prompt** as a fixture (~7k tokens; a tiny fake prompt could fall below Anthropic's minimum cacheable length and produce a false fail). The library itself stays domain-free.
- It has a fake `ToolExecutor` with canned results and a BYO-key field, which also exercises `:keystore` on-device.
- **Breakpoint parity:** the placement must at least match SB's `AnthropicAgentLoop.buildRequestBody`: one `cache_control: ephemeral` on the system block, caching tools + system; messages have no breakpoint. A moving message breakpoint may be added on top, but not in place of it.
- **Gate-1 on the TESTER:** 2+ turns; `cache_read_input_tokens > 0` on turn 2+, in SB's ballpark (Phase 165: 7,016).
- **Full real-SB parity** is proven in SB's Wave-1 Gate-1 (§6.4). A gap found there is fixed with a `v1.0.x` patch tag, not a Wave-0 blocker. Option (b), an early SB branch on the engine, is rejected.

**A11 — CalTracker bumps OkHttp to 5.2.1 in Wave 1 (2026-09-29, Yahir).** CT's cost assessment found 2 build-file lines, zero source/test changes, and green on 5.2.1: 1177/1177 relevant tests pass, and the one failure is an unrelated pre-existing flake. MockWebServer 5.x keeps the legacy `okhttp3.mockwebserver` API. `backup-engine` uses OkHttp directly. CT's v1.13 includes the bump; its Gate-1 adds a real barcode lookup + a Drive backup/restore round-trip, because `backup-engine` and `:stt` are AARs built against 4.12 and run on 5.x. SB already runs `backup-engine v1.2.1` on OkHttp 5.2.1 in production. A1's engine floor (4.12) is unchanged. CT's on-device plug-in is conditional on the v1.1 spike, with an explicit N/A-deferred fallback.

**A12 — All tag cuts waived across the whole effort; the agents own tag correctness (2026-09-29, Yahir).** This supersedes A9 and every per-repo human tag gate (including stt-engine's human-gated `:stt` doctrine and YAT's) for the tags this effort produces: `:stt`, YAT, `voice-action-engine` `v1.0.0` / `v1.1.0`, and any patch tags. Each repo's session cuts its tag on green verification. The waiver also covers creating and pushing to the public GitHub repos these tags need; `github.com/Ygaray/voice-action-engine` was created public on 2026-09-29. In exchange, the peers are **jointly responsible** for keeping every tag in sync and correct, via the protocol in §11.

**E1 — Erratum: SB's confirm seam is `MutationGate`, not `MutationTierPolicy` (2026-09-29; found by SB, verified by VAE).** Wherever §5.2, A2 or §6.4 say "SB plugs `MutationTierPolicy`", read: SB plugs **`MutationGate`** (`core/agent/MutationGate.kt:28`, `suspend fun admit(toolName: String, input: JsonObject?): MutationGateDecision`), implemented by `VoiceConfirmGate` (`VoiceConfirmGate.kt:52`, which captures a `PreMutationSnapshot` for undo). `MutationTierPolicy` only classifies risk; the gate consults it. `PreApplyGate` is modeled on the `MutationGate.admit → decision` shape, plus A2's generalization (needs-confirmation + a reason). SB's `CommitSink` source = `VoiceUndoOperations.kt` + `PreMutationSnapshot.kt`.

**E2 — Erratum: the A10 fixture source (2026-09-29; found by SB, verified by VAE).** The A10 fixture is the serialized **`AnthropicToolRegistry.toolDefinitions`** (`AnthropicToolRegistry.kt:150`) plus the **fully composed `SYSTEM_PROMPT`** (`AnthropicAgentLoop.kt:456`, composed with `TAG_DISAMBIGUATION_POLICY` at :443), not `ToolSchemas.kt` directly. SB produces it as a `{system, tools}` JSON file (SB-side JVM test/task) and hands it to VAE before v1.0's `:sample` step, routed through the orchestrator (A14).

**E3 — Erratum: A3 applies to SB too (2026-09-29).** SB's minSdk is also 35, so `language="auto"` is always native-live for SB as well. The SDK_INT < 34 fallback is tested in `:stt` only.

**E4 — Erratum: SB's tool contract has 18 tools, not 17 (2026-09-29; found by SB while producing the E2 fixture).** `find_tags` was added in SB Phase 167. Wherever §5.2 or §6.4 say "17-tool `ToolContract`", read 18. Nothing may hardcode the count.

**E5 — Erratum: the engine's JitPack coordinates are per-module (2026-09-29; found by VAE research).** A multi-module JitPack build publishes each module as `com.github.Ygaray.voice-action-engine:<artifactId>`. The bare `com.github.Ygaray:voice-action-engine` resolves only to an aggregate POM. This is the same behavior stt-engine documented at `b8c9fdb`. Wherever this contract or ECOSYSTEM.md cite `com.github.Ygaray:voice-action-engine`, read the per-module form; VAE's scaffold (step 1) fixes the exact artifactIds. §11 ledger rows list the per-module coordinates, and the orchestrator's JitPack check resolves each one.

**E6 — Erratum: A11's AAR premise is half wrong (2026-09-29; found by VAE research, verified by the orchestrator).** A11 says `backup-engine` and `:stt` are AARs "built against 4.12". `backup-engine` does pin OkHttp 4.12.0, but `:stt` pins **5.2.1** (`stt-engine/android/gradle/libs.versions.toml`). A11's decision and CT's Gate-1 criteria (barcode lookup + Drive backup/restore round-trip) are unchanged; the binary-compat risk is `backup-engine` only.

**E7 — Erratum: engine artifactIds are repo-prefixed (2026-09-30; VAE research, orchestrator ruling).** Under E5 the published modules are `com.github.Ygaray.voice-action-engine:voice-action-engine-{core,providers,keystore}`. In v1.1 they are `…:voice-action-engine-undo` and `…:voice-action-engine-voice-adapter`. This matches stt-engine's pattern (`voice-engine-android-*`) and how SB consumes it. A18's `…:undo` means `…:voice-action-engine-undo`.

**A13 — Reconvene protocol: two per wave (2026-09-29, Yahir).** Every milestone in this effort pauses for a cross-repo **reconvene** after research + discussion and **before planning**. The control plane runs it (A14).
- **Per-repo sequence:** `/gsd-new-milestone` (or `/gsd-new-project`) → `/gsd-research-milestone` → `/gsd-discuss-milestone` → **STOP**. Do **not** run the `/gsd-milestone` umbrella, and do not plan or execute, until the orchestrator sends GO.
- **Check-in:** at the STOP, the session writes `.planning/cross-repo/RECONVENE-BRIEF.md` in its own repo (template in its `HANDOFF.md`) and messages the orchestrator "R<n> ready: <path>". The brief file is the source of truth; the message is just the ping.
- **R1: all five repos, now.** Wave 0 (`:stt`, VAE v1.0, YAT) and Wave 1 (SB, CT) all check in. For Wave 1, R1 is a **scope-level** check (the engine API doesn't exist yet). After R1:
  - Wave 0 gets GO → plan + execute.
  - Wave 1 **parks**: no planning.
- **R2: Wave 1, when VAE `v1.0.0`, `:stt` and YAT tags are all in the §11 ledger.** SB and CT re-run `/gsd-discuss-milestone` against the real, tagged API, refresh their briefs, and get GO → plan. A CT/SB phase that needs `v1.1.0` is planned only once `v1.1.0` is in the ledger (per-phase gate, enforced by the orchestrator).
- **R-v1.1: VAE's second milestone.** Same stop + brief after its discussion; the orchestrator reviews it against the other repos' live state. It's a light check-in: no all-peer round unless it finds a conflict.
- **Orchestrator output per reconvene:** a report under `xrepo/vae-bilingual/reconvene/`, contract amendments for any accepted change (Yahir approves), and a per-repo verdict: `GO`, `GO-WITH-CHANGES <list>` (fold into CONTEXT before planning), or `HOLD <reason>`.
- **Authority:** Yahir designates the orchestrator in each session at kickoff. From then on, a GO is the sequencing signal. Scope decisions are still Yahir's. The orchestrator gets them during the reconvene and records them here.

**A14 — Control plane is the orchestrator and the sole §11 ledger writer (2026-09-29, Yahir).** The control plane owns: cross-repo sequencing and waves, the reconvenes (A13), contract amendments and errata (the orchestrator drafts, Yahir approves), the §11 ledger, broadcasts, and cross-repo routing. That includes the v1.1 on-device spike verdict (VAE → orchestrator → CT + SB) and the E2 fixture (SB → orchestrator → VAE).
- **§11 step 5 is replaced:** after steps 1–4, the tagging session **messages the orchestrator the full row** (repo, tag, commit, coordinate(s), contents, evidence path). The orchestrator re-checks that JitPack resolves the tag, commits the row, and broadcasts to every peer. Consumers message their repin rows the same way. **No peer commits to §11.**
- Peers may talk directly for technical Q&A (e.g. VAE asking SB about its code). Any outcome that touches the contract, a tag, or sequencing goes through the orchestrator.
- SB keeps §6.4 and answers SB-code questions. It no longer holds the contract.

**A15 — Long-form dictation also uses `language="auto"` (2026-09-29, Yahir in SB's session; feasibility confirmed by stt-engine at R1).** This extends §6.1 beyond single-utterance detection. Native `"auto"` must hold across a continuous, segmented dictation session, and `FinalSegment.language` is **per segment** and may change mid-session (same rule: confidence-3 only, bare `en`/`es`, or `null` = unknown). Evidence: plan-149 was itself one continuous 35.7 s segmented session that switched per segment. In `:stt` v3.1 this is **NBIL-09**, provisional, with implementation in Phase 18 and validation in Phase 19 on the real mic. **Drop rule:** if the real-mic run on the TESTER shows the label is unstable across segmented-session restarts (the device degrading to rung b/a re-initializes detection), NBIL-09 is dropped and does not block the `:stt` tag. stt-engine reports that result to the orchestrator, and SB's BILING-03 falls back to commands-only `"auto"` (SB's existing dictation language setting stays primary). §6.4 gains SB text-card dictation under the same condition.

**A16 — v1.0 verification bar tightened: one live smoke call per cloud provider (2026-09-29, Yahir, in VAE's session; raised by VAE after CT confirmed an OpenAI tool-shape bug that MockWebServer tests never caught).** A8's v1.0 bar adds **one live single-shot call per cloud provider** (Anthropic, OpenAI, OpenRouter) from `:sample` on the TESTER, using real BYO keys Yahir supplies. It proves the real wire shapes. OpenAI/OpenRouter *agentic* is still JVM-only in v1.0; SB's Gate-1 (PROV-03) is its first on-device proof. Yahir also approved these v1.0 engine details in the same session: HTTP-level retry only (`maxRetries = 1`, transport errors; **a retry never re-executes tools or re-commits**, see A17), a `CacheNotEngaged` telemetry event, and provider request ids on failures.

**A17 — CommitSink seam refinement + escalate-after-commit safety (2026-09-29, Yahir; raised by VAE, specified by SB from its code, brokered by the orchestrator).** It lands in **v1.0** (§6.2 steps 2/6b). §5.2 `CommitSink` becomes:
- **Per-action commit notification, sent as each action commits** (not at the end of the run), so actions committed before a cancel, budget stop or error are never lost from undo/audit.
- Every commit carries a **`runId`** and a per-action payload shaped like SB's `ExecutedToolCall`: tool name, mutating flag, outcome (`committed | held | preview | is_error`), target ids, and the pre-mutation snapshot captured at `PreApplyGate`.
- **`onRunClosed(runId, terminalOutcome)`** is sent on **every** exit path: done, cancelled, budget exceeded, provider error, escalation exhausted. Consumers decide their Undo offers at run close, because a later action can entangle an earlier one (SB's footprint-isolation rule, `VoiceUndoFootprint.isolatedIndices`).
- The run result keeps the full **ordered executed-action list**. Consumers use it for outcome classification and retry safety.
- **Engine rule, no duplicate writes:** a tier that has committed ≥1 action must not hand the command to another tier that would redo it. It either returns `Completed(partial)` / `Failed`, or escalates with `carry` holding the committed actions and the next tier is barred from redoing them (VAE picks the mechanism; it's tested explicitly). Consumers offer Retry only when nothing committed. *Clarification (2026-09-30):* a **HOLD** also makes a tier terminal, because a pending write counts as a commit for escalation safety. A later `commitHeld` opens a new run linked by `parentRunId`, with its own `onRunClosed`. A tier that committed and then asks to escalate ends as `Completed(partial = true)` with the suppressed reason in the trace, and consumers must render that as partial, never as full success. `ON_DEVICE` unavailable with no declared fallback → a loud `Failed(ProviderUnavailable)`, never a silent climb to a cloud tier. *Payload clarifications (R1, 2026-09-30):* each commit event carries the app's **verbatim `appOutcomeToken`** alongside the normalized `kind` (SB needs its exact held/`PREVIEW_` strings). Identity is the engine-assigned monotonic `position`, not a provider tool-use id. "Committed" = any admitted apply that ran, including one that errored. A high-confidence item admitted by the gate commits in the **original** run; only held proposals go through `commitHeld` → linked child run.

**A18 — Run-undo as a standalone, engine-independent `:undo` module (2026-09-29, Yahir).** "Undo everything this command did" becomes a reusable pattern that any app can use, **with or without the voice engine**. It lands in **VAE v1.1** (a new §6.2 step, before `:voice-adapter`).
- **Module:** `:undo`, pure Kotlin, depending on **nothing** (not even `:core`). Per E5 it has its own coordinate, `com.github.Ygaray.voice-action-engine:undo`, so a non-voice app can depend on it alone. This adds a module and removes nothing (like A7).
- **Model: a journal/memento.** Before a mutation, the before-state of every touched entity is captured. Undo restores the snapshots in reverse order. Apps supply one adapter **per entity type** (read, write back, re-insert if deleted), not a hand-written inverse per action. Out-of-database side effects (alarms, notifications, files, network calls) register an explicit **compensator**.
- **Safety:** an "unchanged since commit" check runs before every restore. If an entity changed after the command, undo **refuses loudly** and never clobbers. An undo either completes or reports exactly what it couldn't restore (it's never silently partial).
- **Grouping:** entries are grouped by `runId` (A17). The component computes isolation from the entity footprints: isolated actions get an individual Undo, and **every** run with commits gets an "Undo all (N)" for the whole command, including actions entangled with each other.
- **Adoption:** the VAE pipeline integrates `:undo` in v1.1. At the v1.0 migration SB keeps its per-action undo on the A17 seam, then adopts `:undo` (and gets Undo-all) in its v1.1 phases, porting `VoiceUndoFootprint` / `PreMutationSnapshot` / `VoiceUndoOperations` onto entity adapters (`ReminderArmer` becomes a compensator). CT adopts it in its v1.1 phases (multi-item "Confirm all (N)" → "Undo all (N)"). **YAT v2.4.0** ships a generic "Undo all (N)" affordance on the outcome sheet (props-driven) plus per-item undo, so the UI is ready early.
- **Later:** once a non-voice app uses `:undo`, extract it to its own hub (control-plane backlog BL-081). Don't create the repo now.

**A19 — Clarification via pressable options, not a spoken retry (2026-09-30, Yahir's UX decision in SB's session; mechanism proposed by VAE, recorded by the orchestrator).** When the model needs clarification ("Which list?"), the user taps an option instead of speaking again. It lands in **v1.0**, is additive, and adds no new outcome variant.
- **Engine (domain-free):** `ToolSpec.terminal: Boolean = false`, app-declared. A terminal tool must be non-mutating; declaring it mutating fails at build time. When the model calls a terminal tool, the engine sends no `tool_result` and starts no further turn. Earlier calls in the same turn dispatch normally, in order. The run ends as `Completed` with a new nullable `terminalCall = TerminalCall(toolName, arguments)`, and `reply` is null. Earlier commits and holds are carried as usual, and the tier is terminal. This works in AgenticLoop and SingleShot (for SingleShot, a terminal call skips the `OutcomeResolver`).
- **Shared shape:** `:core` ships a typed `Clarification(question, options: List<ClarificationOption(id, label)>)` with `ToolSpec.clarification(name = …)` and `TerminalCall.asClarification()`. `id` is opaque to the engine; CT, for example, maps it to a DB food row. SB and CT use it; an app may still declare its own terminal tool.
- **Follow-up:** `CommandInput.parentRunId: String? = null`. Tapping an option issues a NEW command linked by `parentRunId` (in the trace and in `CommitSink`/`onRunClosed`). The app's user-turn renderer puts the original transcript, the question and the chosen option into the first user message. The cached prefix is unchanged. v1.0 doesn't resume the prior transcript; that can be an additive v1.x option.
- **UI:** YAT v2.4.0 adds a generic, props-driven "clarification choices" component (question plus option buttons). Selecting an option completes the command, dismissing it cancels, and it is visually informational, never an error. A Material snackbar holds only one action, so this is a compact choice surface.
- **VAE mapping:** Phase 2 (`Completed.terminalCall`, `CommandInput.parentRunId`, `Clarification`), Phase 3 (`ToolSpec.terminal`), Phases 7 and 9 (strategy handling and tests), and the README.

## 11. Tag protocol & ledger (A12)

**Before cutting a tag, all must hold:**
1. The milestone's verification is green (Gate-1 where device-verifiable; full unit suite; detekt clean).
2. The public API change is strictly additive vs the previous tag (API dump / Metalava diff where available).
3. The tag honors this contract's seams for its slice; any deviation first goes through a §10 amendment.
4. The tagged commit is pushed and **JitPack builds it successfully** (check the build log / resolve the coordinate from a clean Gradle cache). A tag whose JitPack build fails is not "cut".

**After cutting:**
5. *(Replaced by A14.)* Message the orchestrator the full row: repo, tag, commit, coordinate(s), what it contains, verification evidence path. The orchestrator commits the row below and broadcasts it to every peer.
6. Tags are **immutable**. Never move, delete or re-point a tag. A defect gets a new patch tag (`vX.Y.Z+1`) + a ledger row that marks the old one superseded.
7. Consumers repin **only** to tags listed in the ledger, and message the repin row (consumer, from → to) to the orchestrator, who records it here.

| Date | Repo | Tag | Commit | Coordinate(s) | Contents | Evidence | Consumers repinned |
|---|---|---|---|---|---|---|---|
| 2026-10-01 | yahirandroidtaste | v2.4.0 | 8d197d5f01dde2d8f80d7a088915a2209a704edb | com.github.Ygaray:yahirandroidtaste:v2.4.0 | Additive 10th Voice Command family: ProviderKeyCard, ModelSelectCard, ApproachLadderCard, OutcomeSheet (handled-by, loud failure, undo groups VUNDO-01, needs-confirmation), ClarificationBar; domain-neutral, no engine dep | YAT .planning/phases/14-cut-v2-4-0/14-SHIP-GATE-EVIDENCE.md; JitPack pom+aar 200; full suite + apiCheck green @8d197d5 | CalTracker v2.1.0→v2.4.0; SecondBrain v2.3.0→v2.4.0 |
| 2026-10-01 | stt-engine | v0.7.0 | 0108c6d9601dc53261bf415545f46e97d81ee6c9 | com.github.Ygaray.voice-engine-android:voice-engine-android:v0.7.0, com.github.Ygaray.voice-engine-android:voice-engine-android-recorder:v0.7.0, com.github.Ygaray.voice-engine-android:voice-engine-android-voicenotes:v0.7.0, com.github.Ygaray.voice-engine-android:voice-engine-android-transcription:v0.7.0, com.github.Ygaray.voice-engine-android:voice-engine-android-corrections:v0.7.0 | Public mirror Ygaray/voice-engine-android. :stt native language="auto" EN/ES bilingual capture on SpeechRecognizer (API 34+, runtime-gated; <34 falls back to configured default); additive to v0.6.0 server-backed bilingual; :stt:apiCheck byte-identical to v0.6.0, non-auto configs unchanged (NBIL-07). NBIL-09 KEEP (long-form, real-mic validated). RD-01 known limitation (non-en/es pack could in principle be switched to; never observed) documented. recorder/voicenotes/transcription/corrections unchanged, re-published for shared tag. Do not pin plain com.github.Ygaray:voice-engine-android (aggregator of all 5). | stt-engine .planning/phases/20-cut-the-stt-tag/20-LEDGER-ROW.md, 20-JITPACK-VERIFY.log (5/5 build OK, 5/5 AAR HTTP 200), 20-REAL-MIC-EVIDENCE.md; RD-02 GREEN f0f1875; mirror tag via ls-remote | CalTracker v0.6.0→v0.7.0; SecondBrain v0.5.0→v0.7.0 |
| 2026-10-02 | voice-action-engine | v1.0.0 | efc060f8fe462b71af2e4586b75a97db119ebabd | com.github.Ygaray.voice-action-engine:voice-action-engine-core:v1.0.0, com.github.Ygaray.voice-action-engine:voice-action-engine-providers:v1.0.0, com.github.Ygaray.voice-action-engine:voice-action-engine-keystore:v1.0.0 | §6.2 steps 1-7: core contract + pipeline + 6/60000/4096 + PreApplyGate/CommitSink + ON_DEVICE seam; providers Anthropic (cache)/OpenAI/OpenRouter, OkHttp 4.12 floor green on 4.12.0/5.2.1/5.5.0; keystore AES/GCM+DataStore; SingleShot + AgenticLoop; Gate-1 13/14 + 1 accepted by evidence; Metalava baseline; isolated wiring PASS; OR anthropic/* uncached (LATER-02); no aggregator coord (E5). Waivers W01-W13 by Yahir 2026-10-02; W04 carried to milestone-close Gate-2 | voice-action-engine .planning/phases/11-cut-v1-0-0/11-LEDGER-ROW.md (+ evidence/11-JITPACK-VERIFY.log, cut-v1.0.0.txt, baseline.txt, 11-WIRING-RERUN.md); tag object 343fd3f286 | CalTracker none→v1.0.0; SecondBrain none→v1.0.0 |
