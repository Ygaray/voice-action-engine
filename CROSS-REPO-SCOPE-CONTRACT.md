# Cross-Repo Scope Contract — Bilingual Voice Commands + `voice-action-engine`

**Status:** FROZEN v1.0 + amendments A1–A9 (2026-09-29): approved by Yahir. Changes go through a numbered **Amendment** section at the end, never by silent edits.
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
   **v1.0 verification bar (A8):** the agentic loop is **verified on-device on Anthropic**, including SB's cache-read parity. OpenAI/OpenRouter agentic runs are covered by JVM tests with the fake provider only.

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
- Repin `:stt`, `voice-action-engine`, YAT; **OkHttp stays 4.12.0** (A1: the engine does not force a bump). If CT ever bumps to 5.x, it is an app-wide HTTP migration (OpenFoodFacts barcode lookup, scan client, backup-engine, MockWebServer); "barcode scan still resolves post-bump" is then a Gate-1 criterion.
- CT's weak-match / batch confirm plugs into `PreApplyGate` (A2); it must not be dropped in migration.
- Migrate its single-shot onto `SingleShotStrategy`; `log_food` → `ToolSpecProvider`; `RepositoryToolFacade` → `OutcomeResolver`; `VoiceLogViewModel` → `CommitSink`.
- Design plug-ins for: LocalGrammar ("log N food" EN+ES), OnDevice-backed SingleShot (offline logging) if the spike is green; decide Plan/Agentic (likely N/A, or queries like "what did I eat yesterday").
- Adopt bilingual. Adopt the YAT settings/outcome UI.

## 7. Sequencing & tag/repin order

```
freeze contract ─► Wave 0: stt-engine ║ voice-action-engine ║ YAT   (parallel)
                    └─ 3 tags cut ─► Wave 1: SecondBrain ║ CalTracker  (parallel repin + integrate)
```

Version deltas resolved by Wave-1 repins: OkHttp is **not** a Wave-1 delta (A1: engine floor 4.12, each app keeps its own; SB 5.2.1, CT 4.12.0), MicButton / YAT (CT v2.1.0, SB v2.3.0 → new YAT tag), `:stt` (SB v0.5.0, CT v0.6.0 → new `:stt` tag).

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
