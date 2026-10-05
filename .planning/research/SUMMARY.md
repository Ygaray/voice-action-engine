# Research Summary: voice-action-engine v1.1

**Project:** voice-action-engine (Android/JitPack multi-module library)
**Milestone:** v1.1
**Researched:** 2026-10-05
**Research period:** phases 12–20 (cuts `v1.1.0`)
**Files synthesized:** STACK.md, FEATURES.md, ARCHITECTURE.md, PITFALLS.md, ROADMAP.md

---

## Executive Summary

Voice-action-engine v1.1 adds a four-tier ladder (LocalGrammar → Plan-Then-Execute → Router → AgenticLoop) to the v1.0 engine, plus run-level undo and a speech adapter. The research validates a specific stack (LiteRT-LM 0.17.1 for the on-device spike, no new dependencies for grammar/plan/router, stdlib-only `:undo`, Android library `:voice-adapter` with `:stt` v0.7.0), concrete seam shapes for the new tiers, and a set of nine critical design rules that prevent silent failures and API-evolution hazards. The main risk is not algorithmic—it is **build plumbing** (15 scripts hard-code the three-module list, some fail loudly and some pass silently), **additive-API traps** (constructor defaults remove JVM overloads, Metalava does not catch them), and **undo timing** (capturing before-state at the wrong moment clobbers later work). All are resolvable with mechanical gates and explicit design choices recorded in each phase's CONTEXT.

---

## Key Findings

### From STACK.md

**Core decision (HIGH confidence):**
- **Phase 12:** Add `claude-sonnet-5` capability row with **1,024-token minimum cacheable prefix** (not 512 like Sonnet 5.5). Anthropic docs fetched 2026-10-05 confirm this; the cache diagnostic must use a separate constant, not conflate it with the 512 value.
- **Phase 12:** W04 fix has **two independent causes**. (a) `OpenAiModelRules.wireRules` tests `GPT_6_FAMILY` before the Responses-only case, so direct `gpt-6-astra` gets `"none"` (incorrect). (b) ChatErrors classifier only recognizes the `v1/responses` marker, not the exact captured 400 shape (`param=reasoning_effort`, `code=unsupported_value`). Full list of ids rejecting `"none"`: `gpt-6-astra`, `gpt-6.1-sol`, `gpt-5.4-pro`, `gpt-5.5-pro`, `gpt-5.2-codex`, `gpt-5.3-codex`. Allow-table must be positive (ids that *do* accept `"none"`) so future ids default to "omit" rather than "try `"none"`".
- **Phase 13:** LiteRT-LM 0.17.1 (not MediaPipe), Gemma 4 E2B `.litertlm` (Apache-2.0, ungated, ~2.6 GB). **Compatibility risk:** runtime built with Kotlin 2.4.0 metadata and Java 21 class major version; S22 running Kotlin 2.3.20 is at the edge of the supported metadata-read window. Measure `:ondevice` compilation in Phase 13 plan step 1.
- **Phase 14:** No new dependencies. Hand-roll EN/ES number-word parser and matcher in `:core`, stays within the allowlist (stdlib + annotations + coroutines-core + serialization-json).
- **Phase 17:** `:undo` is stdlib-only (no `:core`, no coroutines). Use `suspend` as a language feature, `synchronized` for short critical sections.
- **Phase 18:** `:voice-adapter` **must be an Android library (AAR)** because `:stt` v0.7.0 is an AAR with minSdk 33. Add content-filtered JitPack repo to `settings.gradle.kts`: `maven("https://jitpack.io") { content { includeGroup("com.github.Ygaray.voice-engine-android") } }`. Use `compileOnly` scope for `:stt` to avoid transitive pull of OkHttp 5.2.1 and other deps.

**Confidence: HIGH** for all stack decisions (vendor docs, local source reads).

### From FEATURES.md

**Table-stakes features by tier (HIGH confidence from source review):**

1. **LocalGrammar (Phase 14):** Exact whole-utterance match, no substring or fuzzy. Template DSL with literals, `[optional]`, `(alt|alt)`, `{slot}`, reusable rules. Per-slot `normalize: (raw, language) -> String?` hook. Language handling: `language` given → that pack only; `language == null` → try both packs; both match with different results → NoMatch (never guess). Typed slots (integer, decimal, closed list, bounded free text). **Number-word spec is the research flag.** Bilingual number parsing for integer spans 0–999,999, fractions (medio/media, cuarto, decimals), mixed digit/word forms, gender and apocope in Spanish (veintiuno / veintiún / veintiuna). No fuzzy matching, no built-in domain vocabularies.

2. **PlanThenExecute (Phase 15):** One planning call, steps returned as forced-tool JSON, validated before execution (unknown tools, read steps, empty plan → escalate with zero side effects). Binding syntax: whole-value string references to earlier **committed** step's `targetIds` (e.g., `"$a.noteId"`). Held step blocks dependents; **decide in phase discuss: stop at first hold vs continue independent steps** (recommend stop). One replan maximum, never after commit. Trace shows exactly how many model calls ran.

3. **TierSelector.Custom (Phase 16):** App provides `StartTierPicker` interface (suspend, sees input and eligible tier ids). Grammar runs free as pre-pass. **Skip the engine Router entirely when fewer than two LLM tiers are eligible** (free saving). Picker failure → Linear + `router_fallback` code (never failing the command). **Optional `TierSelector.Router`** off by default, built on same seam, picks a tier via a forced-tool classifier. Telemetry reports **`tiersSkipped`** (upper bound on avoided attempts), not "saved" (counterfactual claim).

4. **Run-level Undo (Phase 17):** Entity adapters (read before-state, write back, re-insert if deleted). Reverse-order restore within a run. Unchanged-since-commit atomic check (entity fingerprint vs live state). Refuse loudly rather than clobber. Footprint-based grouping (entangled actions undo together). Compensators for out-of-DB effects (idempotent re-run). Result is complete or exact refusal list.

5. **Voice Adapter (Phase 18):** `FinalSegment(text, segmentId, language: String?)` → `CommandInput(transcript, language, context, parentRunId)`. Language **normalization to en/es/null strictly, never a guess** including from the text. `segmentId` dropped (opaque, per-session, resets on server-to-native handoff). **No confidence field** (stt v0.7.0 has none). `language` is null for every non-auto stt session and for any low-confidence detection; **null is the normal case**.

**Confidence: MEDIUM to HIGH** (source-verified for stt contract; design recommendations for tier shapes based on prior art and trade-off analysis).

### From ARCHITECTURE.md

**Integration points (all verified in source code at HEAD, HIGH confidence):**

1. **`providerCallId` threading (headline 1):** Not a simple passthrough. The id must ride `session.submit(step, providerCallId)` as an **internal overload** (no public API change beyond the new `ExecutedAction.providerCallId` property). Path flows: `ToolCall.id` → `Extraction.callId` → `session.submit(step, id)` → `ActionDetails` → `ActionLedger` → `ExecutedAction`. For held proposals, id stays on `HeldProposal` (internal field) and is stamped on the later `commitHeld` action too.

2. **Picker in the trace (headline 2):** A `PickContext` model call must run inside a pseudo-attempt (`tierStarted(pickerId)` ... `tierFinished(pickerId, "picked" | "router_fallback")`), so the picker's turns land in `trace.attempts` and tokens count toward the run budget. Otherwise they vanish from the trace (tokens still counted, but trace requirement fails).

3. **`KeyAccess` as a plain interface (headline 3):** The brief said `fun interface KeyAccess`, but the source code shows two abstract members (`existingKey`, `getOrCreateKey`), so it must be a plain `interface`. Annotation is `@DelicateKeyAccess @RequiresOptIn(level = ERROR)`, and the new `@DelicateKeyAccess public constructor(dataStore, slots, keyAccess)` is the only entry point (existing 2- and 3-arg ctors point to the internal `AndroidKeyStoreKeyAccess` default).

4. **`carryIn` recording (section 3.5):** Record at tier *start*, not finish. `TierWalk.kt:75` calls `recorder.tierStarted(strategy.id, carryIn = carry != null)`. Store in `TierBook` and read on `close()`. This also covers the `flushInFlight` timeout path.

5. **`cappedByPolicy` semantics (section 3.6):** Set to true exactly when policy skipped at least one tier AND no tier handled the command. The `offline_unavailable` code (device offline) is different from `tier_skipped_policy` (policy removed tiers), so offline-only does not auto-set the flag unless policy also capped. SB condition: `Unhandled(cappedByPolicy = true)` covers every `tier_skipped_policy` case, including offline-only.

**New modules and their edges (section 9):**
- `:undo` (stdlib only) → no edges, module-graph gate proves it.
- `:voice-adapter` (Android lib) → `:core` (api), `:stt` (api, external).
- `:ondevice` (if green) → `:core` only (litertlm stays `implementation`, no public LiteRT types).

**Confidence: HIGH** for all integration points (source line citations).

### From PITFALLS.md

**Four critical findings that appear in every phase's checklist:**

1. **Pitfall 1 & 2: Constructor defaults and baseline rewriting.** Adding a parameter with a default value to an existing public class removes the old JVM constructor; Metalava does not catch it because `$default` synthetics are not in `api.txt`. Solution: explicit new overload, no defaults; existing constructor kept body-for-body intact. `apiDump` is never a "make green" button; per-phase verification must show zero `-` lines in baseline diffs.

2. **Pitfall 9: Hard-coded module list.** The literal strings `core providers keystore` appear in ~15 scripts. Some fail loudly (`jitpack-dry-run.sh` exact-set check, `allowedEdges.getValue` throws). Others stay silent: the API dump, hygiene scan, docs-coverage symbol scan, negative controls, agent wiring test simply skip new modules and stay green. Solution (P17 owner): introduce a proposed scripts modules-list file (to be created in P17) with `name packaging artifactId`, read by every script; consistency gate on the list.

3. **Pitfall 16 & 20: Timing and semantics of new seams.** `CommitSink.onAction` runs **after** apply is recorded, but `PendingMutation.context` is documented as "before the change". A held proposal can be committed much later (against state that moved), so capturing before-state at prepare time makes undo restore a stale version. Solution: capture inside `apply()` immediately before the write, persist write-ahead, then flip to COMMITTED on `onAction`. `carryIn`, `cappedByPolicy`, `providerCallId` semantics must be pinned via truth-table tests per seam.

4. **Pitfall 12 & 13: Grammar never guesses, bilingual STT text is messy.** Template grammars must match the *entire* normalized transcript (no substring, no score, no threshold). Ambiguity (two rules match, or EN and ES conflict) returns NoMatch, never a best-guess. Bilingual text has accents (ñ vs n), punctuation (commas in numbers), different number formats per language (1.000 EN vs ES), Spanish irregulars (quinientos, veintiuno/veintiun/veintiuna). Solution: build-time pack validation, nearest-miss test corpus, number-word round-trip for 0–999,999 both languages, no fuzzy matching.

**Confidence: HIGH** (direct repo reads, design principles for prevention).

### From ROADMAP.md

**Phase dependencies and success criteria summary:**

- Phase 12 is the foundation: every later tier depends on `onFailed`, `ReasoningMode`, `ExecutedAction.providerCallId`, `cappedByPolicy`. Start here, serially inside `:core`.
- Phase 13 (spike) runs in parallel with 12, TESTER-only, verdict early to orchestrator.
- Phases 14–18 are independent (with plumbing exception below) and can parallelize. Phase 14 unblocks 16's real-grammar proof.
- Phase 17 owns "add a published module" plumbing; phases 18 and (if green) 13 rebase onto it.
- Phase 19 is the gate: ends only when 12, 14–18 and 13's verdict are all ready, Gate-1 runs green on TESTER.
- Phase 20 cuts the tag only if 19 is green.

**Consumer milestones:**
- SB 176–178: `onFailed`, `carryIn`, `cappedByPolicy`, `providerCallId` cleanup + grammar tier.
- SB 177: PlanThenExecute + step binding.
- SB 177–178: Router seam for SB's own picker; run-undo with `:undo` adapters.
- SB 179: on-device verdict.
- CT 75: all above + grammar, plan, on-device conditional, optional adapter.

---

## Implications for Roadmap

### Phase-by-Phase Roadmap Structure and Critical Decisions

**Phase 12: Wave-1 Seams & W04 Fix**
- **What it delivers:** Consumer seams (onFailed, ReasoningMode, carryIn, cappedByPolicy, providerCallId, KeyAccess opt-in), three v1.0.1 doc fixes, W04 fix.
- **Research flags:** Anthropic `claude-sonnet-5` minimum cacheable prefix (open item: must fetch from docs at plan time); full OpenAI id list for allow-table (optional, but completeness matters for future models).
- **Plumbing touches:** None that are new; this phase just settles the shapes.
- **Gate requirement:** W04 proven on JVM (MockWebServer replay) + live smoke on the TESTER with spend-capped key, model-call count bounded.

**Phase 13: On-Device Model Spike**
- **What it delivers:** Go/no-go verdict for bundled ~2B model (LiteRT-LM 0.17.1 + Gemma 4 E2B) on S22 TESTER. If green: `@Experimental` module ships. If red: nothing shipped, `:core` untouched, v1.1.0 is not blocked.
- **Research flags (HIGH impact):** (1) Kotlin 2.4.0 metadata + Java 21 class format compatibility with compiler 2.3.20 (test in plan step 1); (2) S22 RAM headroom (1.7 GB on S26 per Google's card, measured cold/warm/sustained); (3) semantic accuracy with real prompts (not just schema-valid JSON); (4) APK/AAR size delta (runtime alone ~9.5 MB; weights are app-downloaded).
- **Critical success criterion:** Verdict message to orchestrator **before** SB 179 / CT 75 plan (the verdict itself is the v1.1 value; shipping code is secondary).

**Phase 14: LocalGrammar & Bilingual GrammarPack**
- **What it delivers:** Free, offline EN/ES grammar tier with anchored matching, typed slots, number words, normalize hook. Reuses OutcomeResolver / Resolution / gate path.
- **Research flags (MEDIUM):** ES number compound forms (veintiuno/veintiun, ciento y, quinientos irregulars); nearest-miss corpus from real S22 STT transcripts (accent/punctuation/number format variants).
- **Plumbing:** Phase 4.1 refactor (extract submitSteps / prepareGuarded) must land first or 14 and 15 will conflict.
- **Gate requirement:** Full-utterance matching test (no substring), ambiguity → NoMatch, number round-trip 0–999,999 both languages, app-supplied synonym `normalize` hook works.

**Phase 15: PlanThenExecute Strategy**
- **What it delivers:** One planning call, ordered gated steps, write-output binding to earlier committed `targetIds`, one replan maximum, escalation suppression after first commit.
- **Research flags (MEDIUM):** Plan schema and binding syntax (whole-value reference syntax must survive strict-mode schema validation); deterministic lookup detection (what makes a step "need a read", §4 rule).
- **Plumbing:** Needs Phase 4.1 refactor (shared helpers).
- **Decision needed:** Hold handling—stop at first hold (simpler, partial Completed with escalation_suppressed) or continue independent steps? Recommend stopping.
- **Gate requirement:** Model-call counts asserted (exactly 1 for success, exactly 2 on pre-commit failure + replan). Post-commit failure makes no replan call. `onFailed` fires for provider failures only.

**Phase 16: Start-Tier Selection (Custom + Router)**
- **What it delivers:** `TierSelector.Custom(StartTierPicker)` seam + opt-in `TierSelector.Router` (off by default, closes the v1.0 sealed `TierSelector`). Grammar pre-pass always free. No picker call when <2 LLM tiers eligible or offline-only.
- **Research flags (MEDIUM):** Router classifier prompt (app-supplied tier descriptions, EN/ES examples). Default cheap model (from policy/selection seam, never hard-coded). "Tiers bypassed" telemetry naming (not "saved"; Linear is never run).
- **Critical decision (D-ROUTER-SKIP):** Skip the router when only one LLM tier is eligible. Validate v1.0.1 Linear walk (characterization test) **before** touching `TierWalk.run` so any regression is caught immediately.
- **Gate requirement:** Picker error → Linear + `router_fallback` (command never fails because of picker). Offline-only never calls picker.

**Phase 17: Run-Level Undo (Module + Integration)**
- **What it delivers:** Standalone `:undo` module (stdlib only, no `:core`) + pipeline integration (app-side bridge, the module is self-contained).
- **Research flags (MEDIUM):** Journal/memento API shape (adapters per entity type, compensators for side effects, footprint grouping). Where the pipeline hook journals (beside `CommitSink`; recommendation: app-side glue in the sample + docs).
- **Decision needed (D-UNDO):** Bridge location (a) app/sample code (recommended, simplest, documents the pattern) vs (b) a 6th published module (needs contract amendment, extra install-list/script entries). Decide in plan, record in CONTEXT.
- **Plumbing (Phase 17 owns it):** `allowedEdges` add `:undo` + `:voice-adapter` + `:ondevice` (if green). `verifyUndoZeroDeps`, `verifyNoSttOutsideAdapter`, etc. (section 8 gates). Hard-coded module lists in jitpack.yml, release-cut.sh, scripts/.
- **Gate requirement:** Zero external dependencies (module-graph gate proves stdlib-only). Entity changed between commit and undo → refuse loudly for that entity. Hold then confirm → restore correctly with correct before-state (ActionEvent.heldRunId needed for grouping).

**Phase 18: Voice Adapter**
- **What it delivers:** `:voice-adapter` Android library (minSdk 33) mapping `:stt` v0.7.0 FinalSegment to `CommandInput` without `:core` depending on `:stt`.
- **Research flags (MEDIUM):** FinalSegment.language value set (does it carry region tags like `en-US`?). AAR artifact type forces adapter to be Android library (confirmed by source read).
- **Plumbing:** Needs Phase 17 plumbing first. Add JitPack repo to settings.gradle with content filter.
- **Decision needed (D-VOICE-SCOPE):** `compileOnly` (recommended, keeps `:stt` transitive off) vs `api` (re-exports `:stt`, forces version on apps). Recommend `compileOnly`.
- **Gate requirement:** `:core`/`:providers`/`:keystore` graphs contain no `voice-engine-android`. Adapter's POM does not either (compileOnly not published). Language mapping: `en`/`es` pass, `en-US` → `en`, unknown/`null` → `null`, never guess.

**Phase 19: Sample Gate-1 & Docs**
- **What it delivers:** End-to-end proof of grammar (zero-provider offline), plan (with write-output binding), router (trace tier pick), undo-all (full command reversal). Docs covering every new tier/seam/module.
- **Gate requirement:** Isolated wiring test (agent wires the new components from docs alone). Leak-scan clean on new types. Module graphs verified.

**Phase 20: Cut v1.1.0**
- **What it delivers:** Immutable v1.1.0 tag with all published modules, gated release script, JitPack resolution proof, ledger row to orchestrator.
- **Plumbing touches:** `apiDump` for new modules (undo, voice-adapter, ondevice-if-green). Verify additive only (zero `-` lines in diffs).
- **Host requirement:** Quiet window, free swap (v1.0.1 had 5 earlyoom kills), single-use Gradle daemon.

### Specific Decisions Required (Organized by Phase)

| Phase | Decision | Recommendation | Impact |
|-------|----------|-----------------|--------|
| 12 | D-REASON: `ReasoningMode.PROVIDER_DEFAULT` wire bytes | Both OFF and PROVIDER_DEFAULT emit identical wire bytes in v1.1 (knob for future); OpenAI needs `"none"` on gpt-5.4+, so omit only on unknown ids | Cache prefix alignment, no on-wire change v1.1 |
| 12 | D-KEY4: publish 3-arg KeyAccess ctor only | Yes (smallest frozen surface); JVM test under `runTest` with `Dispatchers.IO` | API surface |
| 15 | Hold handling: stop or continue? | **Stop at first hold** (simpler, reports clean partial Completed + escalation_suppressed) | Plan success criteria SC3/SC4 interaction |
| 16 | D-ROUTER-SKIP: skip when <2 LLM tiers | **Yes** (free saving, cost of router is otherwise unpaid) | Router gate requirement |
| 17 | D-UNDO: bridge location | **App/sample glue in INTEGRATION docs** (recommended; a 6th module needs contract amendment) | Undo integration shape |
| 18 | D-VOICE-SCOPE: `:stt` scope | **`compileOnly`** (keeps transitive OkHttp/deps off engine consumers) | Adapter POM purity |

---

## Research Flags & Gaps

| Flag | Phase | Research needed | Impact | Priority |
|------|-------|-----------------|--------|----------|
| Anthropic `claude-sonnet-5` cache minimum | 12 (plan) | Fetch from `platform.claude.com` docs at plan time (live value, not from memory) | Cache diagnostic accuracy | HIGH |
| OpenAI ids rejecting `"none"` complete list | 12 (plan) | Enumerate every id the allow-table must cover (for completeness, not correctness—omit-unknown handles unknown ids safely) | Robustness against future models | MEDIUM |
| Kotlin 2.4.0 metadata compatibility | 13 (plan step 1) | Compile `:ondevice` against litertlm-0.17.1 on the repo's Kotlin 2.3.20 toolchain; if it fails, pin 0.16.1 or defer the module | Spike go/no-go | HIGH |
| S22 RAM, cold/warm/sustained latency | 13 (spike run) | Measure on SM-S908U (Snapdragon 8 Gen 1, not S26 benchmarks). Peak PSS, thermal status, battery impact | Verdict accuracy | HIGH |
| ES number compound forms (STT reality) | 14 (corpus build) | Capture real STT output for "veintiuno", "ciento y", irregular hundreds. Test parser round-trip 0–999,999 | Pitch correctness | HIGH |
| Plan binding syntax strictness | 15 (research) | Does a whole-value reference like `{"$ref": {"step": 0, "key": "x"}}` survive strict-mode schema validation on OpenAI? Test with real schema strip | Binding safety | MEDIUM |
| Hold semantics in the flow | 15 (discuss/plan) | Decision on hold placement: stop at first hold (SC4 path) or continue independent steps? | Plan behavior contract | HIGH |
| FinalSegment.language region tags | 18 (plan) | Confirm whether stt v0.7.0 can emit `en-US`/`es-MX`; mapping rule → primary subtag only | Adapter language contract | MEDIUM |
| On-device cold start / thermal thresholds | 13 (plan) | Fix thresholds **before** the run (e.g., cold < N seconds, peak PSS < X MB, zero OOM); verdicts are numbers, not rationalizations | Spike success criteria | HIGH |
| Router default cheap model | 16 (research) | Does policy always supply a model id for the picker? Or is "no model" a fallback? Confirm seam shape | Router implementation | MEDIUM |
| :undo integration glue | 17 (discuss/plan) | Finalize whether the bridge is app code (simplest) or a 6th module (needs amendment) | Undo integration | HIGH |
| Module plumbing `modules.list` | 17 (plan) | Design the `modules.list` source of truth (format: name, packaging, artifactId) and consistency gates | Build hygiene | HIGH |

---

## Confidence Assessment

| Area | Confidence | Notes |
|------|-----------|-------|
| Stack (tech choices) | HIGH | Vendor docs, Maven metadata, local source reads. Kotlin 2.4 metadata risk is known and testable. |
| Features (tier shapes) | HIGH | Extracted directly from source code (`OutcomeResolver`, `ToolExecutor`, `TierWalk`). Prior art cross-checked (Rhasspy, ReWOO, RouteLLM). |
| Architecture (seams & integration) | HIGH | Every integration point verified in source with line citations. Internal threading paths traced. |
| Pitfalls (risks & prevention) | HIGH | Design rules based on v1.0 execution and current repo state. Mechanical gates enumerated (scripts to fix). |
| On-device spike metrics | MEDIUM | Vendor docs read but not measured on S22. Numbers are estimates; spike itself produces truth. |
| Grammar STT behavior | MEDIUM | Domain knowledge of Spanish morphology + prior STT projects, but not measured against this repo's recognizer. P14 corpus must capture real output. |
| Plan prompt/schema shape | MEDIUM | High-level semantics firm (whole-value binding, pre-validation); exact prompt syntax and schema strictness requires testing. |

---

## Known Unknowns (Open Questions for Phase Discussions)

1. **P15 hold semantics:** Does SB 177 need to gate the entire plan once (gate-per-plan) or gate per step (with hold potentially blocking the tail)? The roadmap recommends stopping at first hold, but confirm with orchestrator.
2. **P17 bridge placement:** Will an app-side glue implementation in INTEGRATION.md and sample code suffice, or does Yahir prefer a published `voice-action-engine-undo-bridge` module? The latter needs a contract amendment.
3. **P13 verdict decision:** Speed is more important than perfect numbers; thresholds should be set **before** the run starts, not rationalized from the numbers.
4. **P18 `:stt` compatibility:** Confirm `FinalSegment` constructor visibility (can `:voice-adapter` tests construct it?) and exact minSdk to avoid manifest-merge failures.

---

## Build Plumbing Checklist (Phase 17 Owns, P18/P13 Rebase)

Every item below is concrete and measurable. Missing one silently breaks the build or the cut:

1. **settings.gradle.kts:25** – Add `:undo`, `:voice-adapter`, `:sample` includes + JitPack repo for `:voice-adapter`
2. **Each new module's build script** (new) – Copy recipes (`:undo` from `:providers`, `:voice-adapter` from `:keystore`)
3. **jitpack.yml:6** – Append to single `./gradlew` line: `<module>:publishReleasePublicationToMavenLocal` for each new module
4. **gradle/invariants.gradle.kts:272** – `allowedEdges`: add `:undo` → `emptySet()`, `:voice-adapter` → `setOf(":core")`
5. **gradle/invariants.gradle.kts:273** – `sampleAllowedEdges += ":undo"`
6. **gradle/invariants.gradle.kts** (new tasks) – `verifyUndoZeroDeps`, `verifyNoSttOutsideAdapter`, `verifyAdapterHasStt`, `verifyApiDumpPresent` (new modules)
7. **scripts/release-cut.sh** – Every hard-coded module list (MODULES, gate 7/12/15, sandbox adds/rms, allowed-paths)
8. **scripts/jitpack-dry-run.sh** – Exact-set check (`core:jar`, `providers:jar`, `keystore:aar`, `undo:jar`, `voice-adapter:aar`)
9. **scripts/verify-api-dump.sh, api-dump-isolated.sh, etc.** – Iterate over new modules
10. **each new module's api.txt** – Commit seed `apiDump` output at phase scaffold time (regenerate at P20 cut)
11. **ECOSYSTEM.md** – Add two rows (three if P13 green), update "Planned for v1.1" sentence at cut time
12. **Metalava/detekt** – Auto-wired per module via `plugins` block; zero baseline on all

---

## Sources & Confidence Summary

| Source Category | Files Read | Confidence |
|-----------------|-----------|-----------|
| Vendor documentation | Anthropic prompt-cache docs, OpenAI model pages, Google LiteRT-LM / MediaPipe, HuggingFace API, Gemma Terms | HIGH (fetched 2026-10-05) |
| Repo source code | v1.0.1 HEAD (all `:core`, `:providers`, `:keystore` modules, build plumbing) | HIGH |
| Repo docs & contracts | PROJECT.md, ROADMAP.md, REQUIREMENTS.md, CROSS-REPO-SCOPE-CONTRACT.md, R-v1.1 brief | HIGH |
| External references | `:stt` source + jitpack.yml, OkHttp CHANGELOG, JitPack FAQ | HIGH |
| Prior art & domain knowledge | Rhasspy/hassil grammars, ReWOO/RouteLLM papers, Spanish morphology, STT quirks | MEDIUM (not measured on this project) |
| Spike measurements | On-device RAM, latency, APK size (on-device, not measured) | LOW until Phase 13 runs |

---

## Roadmap Implications Summary

**Sequence.** Phase 12 (seams) + Phase 13 (spike parallel) → Phase 14–18 (tiers/modules, 17 plumbing-first) → Phase 19 (gate) → Phase 20 (cut).

**Critical gates:**
- Phase 12: W04 live smoke + seam shape tests (onFailed, ReasoningMode, carryIn, cappedByPolicy, providerCallId).
- Phase 13: Verdict message (even if red) by day N, so SB 179 / CT 75 can plan.
- Phase 17: Plumbing lands; phases 18 and 13-if-green rebase onto it.
- Phase 19: Gate-1 on TESTER (grammar zero-provider, plan with binding, router trace, undo-all refusal case).
- Phase 20: v1.1.0 immutable tag on JitPack.

**Risks mitigated by design:**
- API evolution: explicit old constructors kept, new types internal ctors + builders.
- Module list: `modules.list` single source of truth, consistency gate, planted-module negative control.
- Undo timing: before-state captured inside `apply()` before the write.
- Grammar guessing: anchored match only, ambiguity → NoMatch, nearest-miss corpus.
- Plan binding: whole-value only, pre-validation, committed-only outputs.
- Router cost: skip when <2 LLM tiers, loud fallback, telemetry honest about upper bounds.

---

## Next Steps for Phases 12–20

1. **P12 plan:** Fetch Anthropic `claude-sonnet-5` minimum cacheable prefix and OpenAI id allow-table from live docs. Set up W04 MockWebServer replay and live-key smoke.
2. **P13 plan:** Lock Phase 13 spike thresholds (cold load < N s, peak PSS < X MB, semantic accuracy target). Book TESTER time before P12's live smoke (PROV-16).
3. **P14 plan:** Build EN/ES number-word golden generator (round-trip 0–999,999). Capture real STT transcripts (anonymized) as the near-miss corpus.
4. **P15/P16 plan:** Script the plan/replan prefix byte-comparison test. Router characterization test (v1.0.1 Linear trace equality).
5. **P17 plan:** Design `modules.list` format and read-consistency gates. Finalize `:undo` bridge location (app code vs 6th module).
6. **P18 plan:** Confirm FinalSegment API (constructor visibility, language type). Add JitPack repo to settings.gradle.
7. **P19 plan:** Compile new doc regions per feature phase; update isolation wiring test for new modules.
8. **P20 plan:** Verify `.gsd/` and `milestone.lock` handling in the cut's `clean` gate. Measure dry-run wall time (hedge against OOM).

---

*Research synthesis for: voice-action-engine v1.1 (Phases 12–20)*
*Synthesized: 2026-10-05*
*Research files: STACK.md, FEATURES.md, ARCHITECTURE.md, PITFALLS.md, ROADMAP.md*
