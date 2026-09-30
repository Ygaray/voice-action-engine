# Reconvene brief — voice-action-engine — R1
**Milestone:** v1.0 "Core Engine" (§6.2 steps 1–7 → tag `v1.0.0`)  ·  **Contract rev read:** A1–A18, E1–E7 (HEAD after `278c6fe` + E7 pending record)  ·  **Date:** 2026-09-29

Artifacts: `.planning/PROJECT.md`, `.planning/REQUIREMENTS.md` (63 v1 reqs), `.planning/ROADMAP.md` (11 phases), `.planning/v1.0-DECISION-MAP.md` (125 gray areas, `status: discussed`), `.planning/phases/*/NN-CONTEXT.md` (all 11), `.planning/research/SUMMARY.md`.

## 1. Phases (from ROADMAP)

| Phase | Goal | Contract steps / seams touched | Needs from other repos |
|---|---|---|---|
| 1 Scaffold & Publishing Proof | 4 modules (`:core`/`:providers` pure JVM, `:keystore` AAR, `:sample` unpublished), JVM 11, detekt zero-baseline + invariant scans, Metalava wired, OkHttp matrix harness, per-module JitPack proof by commit SHA | step 1; E5/E7 coordinates; LE-7 gitignore | — |
| 2 Core Contract, Pipeline & Commit Seam (keystone) | §5.1 types, DSL, Linear/Fixed, TierPolicy, never-throw collapse, open taxonomies, PreApplyGate (suspend + defer), CommitSink (A17), commitHeld, trace + events | step 2; §5.1; §5.2 PreApplyGate/CommitSink; A2, A6, A17 (+HOLD-terminal clarification) | SB + CT confirmations (done, §3) |
| 3 Transcript Types, ProviderRouter & On-Device Gate | neutral multi-turn transcript incl. `NativeReplay`, selection + typed credential seam, per-command snapshot, public capability table, ON_DEVICE slot + gate, `CacheNotEngaged` | step 3a-i; A5, L10 | — |
| 4 Anthropic Transport & OkHttp Matrix | cache-correct cancellation-safe Anthropic transport; A1 green on 4.12.0 / 5.2.1 / 5.5.0; retry (transport-only); forced-tool capability + fallback; secret canary | step 3a-ii; **A1 must-pass**; A10 parity; A16 retry rule | — |
| 5 OpenAI & OpenRouter Transports | one Chat Completions transport, nested tool shape, strict-only-without-optionals, reasoning flags, HTTP-200 errors, recorded goldens | step 3b; Option A (Responses deferred) | **Yahir's OpenAI/OpenRouter keys** for golden capture (pending, §6) |
| 6 Keystore | BYO keys via app-supplied KeySlot table on the app's DataStore; legacy SB/CT byte-format compat (JVM + one TESTER test) | step 4 | — |
| 7 SingleShot Strategy | CT port: forced-tool extraction → OutcomeResolver → gate (defer) → commitHeld | step 5; §5.2 ToolSpecProvider/OutcomeResolver | CT confirmations (done, §3) |
| 8 Multi-turn Mappers | lossless neutral ↔ Anthropic / Chat mappers, verbatim replay, conformance suite, recorded multi-turn goldens | step 6a (A8) | **keys** for multi-turn golden capture (pending) |
| 9 Agentic Loop Strategy | SB port, provider-neutral, SB guards as named tests | step 6b; A17 | SB confirmations (done, §3) |
| 10 Sample Harness, Gate-1 & Docs | `:sample` with the gitignored LE-1 fixture; Gate-1 on TESTER (cache_read ≈ 7,016 on Haiku 4.5); A16 live smokes ×3; README/INTEGRATION/API + fresh-agent wiring test | step 7; A8, A10, A16; LE-1, LE-7 | fixture (delivered); **keys** on the TESTER (pending) |
| 11 Cut v1.0.0 | gated release script → tag → JitPack build + clean-cache resolve → **message ledger row** | §11; A12, A14; `create_tag:false` | orchestrator (ledger + registries, LE-5) |

Critical path 1→2→3→4→8→9→10→11; Phase 6 ∥ after 2; Phase 5 ∥ after 3; Phase 7 after 4 (+5).

## 2. Public surface this milestone adds (all new — first tag; additive-only from `v1.0.0`)

- **Coordinates:** `com.github.Ygaray.voice-action-engine:voice-action-engine-{core,providers,keystore}:v1.0.0` (E5/E7; `:sample` never published). Package root `io.github.ygaray.voiceactionengine.*`. JVM 11 bytecode.
- **`:core`:** `CommandInput`; `CommandStrategy`/`StrategyOutcome` (4 variants, contract-closed); `CommandOutcome` (Completed | Failed | Unhandled; effects on every variant; **`Completed.partial` public, non-defaultable**); `commandPipeline {}` DSL (gate + commitSink **required**); `TierSelector.Linear/Fixed`; `TierPolicy` (+source; `maxTier` as stable public `StrategyId`); open `FailureReason`/`EscalationReason`/`PipelineEvent` + `Other` leaves (incl. `ModelUnsupported`, SB-granularity leaves); `ProviderId` value class (ANTHROPIC/OPENAI/OPENROUTER/ON_DEVICE); `Credential`; `PreApplyGate` (`suspend admit → Admit(amended?) | Hold(reason?)`) + `AwaitingConfirmGate` helper; `CommitSink` (per-action event + `onRunClosed(runId, termination)`); `commitHeld(held, amended?)` → new linked run (`parentRunId`); `CommandTrace` + `PipelineEventListener`; neutral transcript types + `NativeReplay`; **public `ModelCapabilities` table** (+ app overrides); `OutcomeResolver`, `ToolSpecProvider` (+ per-tool `mutating` flag), two-phase `ToolExecutor`; user-turn renderer hook; `SingleShotStrategy`, `AgenticLoopStrategy`.
- **`:providers`:** `AnthropicProvider`, `ChatCompletionsProvider.openAi()/.openRouter()`; OkHttp **4.12 floor** (`api`), CI-green on 5.2.1 / 5.5.0.
- **`:keystore`:** `ApiKeyStore(KeySlot table, app DataStore<Preferences>)`, read states `NotConfigured | Ready(last4) | KeyMissing | Unreadable`, `KeystoreCredentialSource`; `api(datastore-preferences)`.

## 3. Assumptions about other repos (and peer Q&A outcomes)

**secondbrain-2c — confirmed (direct Q&A):**
- CommitSink payload maps 1:1 onto SB `ExecutedToolCall`; **`appOutcomeToken` must be SB's verbatim outcome string** (held token, full `PREVIEW_` string) — kept *in addition to* `kind`; an is_error call is never also committed; engine `position` (monotonic across turns, dispatch order) replaces `toolUseId` (nothing outside the loop reads it).
- Gate may amend the opaque context before apply (= `VoiceConfirmGate` snapshot + `authorizedMergeTargetId`).
- Loop parity: 2-strike is **per tool name** (correction accepted); remaining calls in the turn still dispatch; reply = first text block; budget → `Failed(BudgetExceeded(bound))` carrying commits + held + errored + preview; every SB reason distinguishable (HTTP status kept); executed list = mutating only (all SB consumers filter on `mutating`).
- User-turn renderer hook **required**; SB reproduces `Current local date-time: … (zone)\n\nVoice command: …` byte-for-byte (and adds its per-command language line + carry there in its Phase 174).

**caltracker-android-9a — confirmed (direct Q&A):**
- Defer → Hold → run closes → `commitHeld` opens a new linked run → sequential per-item isolation → own `onRunClosed` → double-tap no-op: fits "Confirm all (N)" → "Undo all (N)". **High-confidence single is Admitted and commits in the original run** (auto-log); Hold only for weak single / 2+ batch.
- Partial failure: CT keeps skip-and-count; the engine reports a failed apply as an `is_error` **event** (no placeholder row).
- Amended confirm = replacement mutation list, no re-gate. Unmatched items proposed with unresolved target; "nothing proposable" → engine `NoMatch` (CT maps to its `Unavailable`); refusal stays separate `Failed(REFUSAL)`.
- Post-commit correct-in-place stays app-side. Thresholds stay in CT's resolver/gate (signals in the opaque context).
- Wording: semantics only — dynamic "Today's date is …" via the renderer hook; static relative-date rule may move to the tool description (Phase 67 `target_date` behavior preserved).
- One tool call with `items[]`; engine adds `disable_parallel_tool_use` / `parallel_tool_calls:false` (no CT behavior change).
- CT's OpenAI flat-tool-shape bug and missing `claude-sonnet-5-5` entry are CT's to fix; the engine ports the nested shape and a verified capability table.

**Other assumptions:**
- `:stt` / YAT: no v1.0 dependency (L7). YAT's outcome sheet can render `partial` per-row results and a held/confirm state generically (A2).
- SB/CT pin each module separately (prefixed coordinates), never the aggregate.

## 4. Contract drift found (code vs contract)

1. **Coordinates** (§6.2 / ECOSYSTEM / README): aggregate `com.github.Ygaray:voice-action-engine` → per-module prefixed (E5 + **E7**; A18 `…:undo` → `…:voice-action-engine-undo`). Fixed in Phase 1.
2. **A11 premise:** `:stt` pins OkHttp 5.2.1 → backup-engine is the only 4.12-compiled-on-5.x precedent (**E6**, recorded).
3. **"17 tools" → 18** (`find_tags`) (**E4**, recorded).
4. **§5.2 SB PreApplyGate source:** `MutationGate`, not `MutationTierPolicy` (**E1**, recorded).
5. **CT OpenAI path** (§6.5 "migrate its single-shot"): CT's OpenAI request uses a flat Responses-style tool shape on `/chat/completions` (400) and was never verified live; CT's forced-tool list misses `claude-sonnet-5-5` — CT-side fixes, engine ports corrected behavior.
6. **Anthropic forced tool_choice:** Opus 5.5 / Sonnet 5.5 / Fable 5.1 / Mythos 5.1 reject forced tools on every request (docs-verified) → SingleShot uses auto + instruction on those (capability table), not "forced + thinking off".
7. **OpenAI Responses-only tools** (GPT-6 Astra / GPT-6.1 Sol): v1.0 fails them loudly (Option A ruling); Responses is additive v1.x (LATER-03).
8. **§6.2 step 7** bundles verification + tag; per the ruling the tag is its own Phase 11 so §11 step 1 truly precedes the immutable cut.

## 5. Proposed amendments

None new beyond what the orchestrator already recorded/ruled (E4–E7, A15–A18, Option A, HOLD-terminal clarification of A17, Phase 11 split). Candidate clarifications for the orchestrator to record if wanted:
- a. **A17 clarification (already ruled):** a pending HOLD makes a tier terminal; `Completed.partial` public non-defaultable; committed = any admitted apply that ran (incl. errored).
- b. **A17 payload clarification:** `appOutcomeToken` = the app's verbatim outcome string alongside the normalized `kind` (SB requirement).
- c. **§6.5 note:** CT's OpenAI tool-shape bug + capability list are CT-side Wave-1 fixes.

## 6. Risks and open questions for Yahir

1. **Keys (pending, relayed by the orchestrator):** Phase 5 + Phase 8 golden captures (host JVM, opt-in, outside `check`) and Phase 10 (TESTER) need real Anthropic / OpenAI / OpenRouter keys via chmod-600 files outside the repo, passed by reference. Capture tasks are gated on this.
2. **First-of-kind publishing:** jar→jar and AAR→jar inter-module JitPack resolution + in-build OkHttp matrix + Metalava on `kotlin.jvm` are unproven in the ecosystem — Phase 1 probes them first (fallbacks recorded).
3. **Cache Gate-1 sensitivity:** must run cold on Haiku 4.5 (5-min TTL); warm re-runs are infra re-runs, not FAILs.
4. **Dependency pre-approval** (discuss-milestone step 3b) skipped: its tooling covers npm/pypi/crates only; all v1.0 deps are Maven (Kotlin/AGP/OkHttp/kotlinx/DataStore/detekt/Metalava) matching consumers' pins.

## 7. Tag / repin intent

- **`v1.0.0`** in Phase 11 after Phase 10's green Gate-1: §11 steps 1–4 via a gated release script, then the full row is **messaged** to `yahir-gsd-control-plane-f2` (A14). `git.create_tag:false` → no stray `v1.0` marker.
- **`v1.1.0`** is the next milestone (§6.2 steps 8–13 + A18 `:undo`), R-v1.1 check-in after its discussion.
- No repins by this repo (hub).
