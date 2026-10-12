# Phase 14: LocalGrammar & Bilingual GrammarPack - Context

**Gathered:** 2026-10-05
**Status:** Ready for planning
**Discussed via:** `/gsd-discuss-milestone` (mode: mixed)

<domain>
## Phase Boundary

An app can put a free, offline grammar tier at the head of its ladder. Declared EN and ES phrasings resolve straight to the app's tool call with zero provider calls, and anything the grammar isn't sure of goes to the next tier untouched.

**Requirements:** GRAM-01, GRAM-02, GRAM-03, GRAM-04, GRAM-05

</domain>

<decisions>
## Implementation Decisions

### submission — How much of the shared submit path moves into `strategy/StepSubmission.kt` as Phase 14's first commit?
- **D-01 [submission]:** Full move as commit 1 (`resolutionOutcome` + `submitAll` → `submitSteps(session, steps, providerCallId)` + `prepareGuarded`), so 14 ‖ 15 never edit the same functions and grammar inherits SingleShot's submit semantics exactly. _(provisional — refresh at execution; depends on Phase 12)_ _(source: ai-auto)_

### verdicts — Does the grammar tier pass resolver verdicts through unchanged, or collapse every non-Steps verdict to NoMatch?
- **D-02 [verdicts]:** Pass through unchanged, so SB's shared resolver means the same thing on every tier; SC-3's "rejected slot → NoMatch" holds for a `Resolution.NoMatch` rejection. _(source: ai-auto)_

### dsl — GrammarPack DSL shape: intent-centric or language-centric?
- **D-03 [dsl]:** Intent-centric: slots declared once per intent (same tool + typed values across languages by construction); template mini-syntax literals/[optional]/(a|b)/{slot}/sub-rules, no regex; internal ctors + builders, no data class/defaulted ctor params; build-time validation throws IllegalArgumentException (duplicate/ambiguous rules, unknown slots, unbounded text). — **Reversibility:** one-way — GrammarPack DSL is public API frozen at the v1.1.0 tag _(source: ai-auto)_

### testability — Does the pack expose a public pure `match(transcript, language)`?
- **D-04 [testability]:** Public pure `match(...)` (frozen surface, redacted toString). CommandSession's ctor is internal, so apps (SB D-04 zero-false-positive corpus) can't unit-test the tier otherwise. _(source: ai-auto)_

### language-out — How does the resolver learn which pack's language matched (null input label → ES may match)?
- **D-05 [language-out]:** Additive `Extraction.matchedLanguage: String?` (null for model tiers). SB SC-3 / D-06 need it; rewriting `input.language` silently changes its meaning for apps. _(source: ai-auto)_

### terminal — Terminal/navigation intents in the grammar DSL for v1.1?
- **D-06 [terminal]:** Add the per-intent `terminal` flag mirroring SingleShot's terminal tool path: SB G6/G7 search/browse navigation is in its grammar tranche, and adding it after v1.1.0 is a new frozen member anyway. Confirm with SB. _(source: ai-auto)_
  - **Consumer condition (binding):** SB condition (answer #2): a zero-commit `terminal` intent → `TerminalCall`; ends the walk as handled carrying intent + slots; NOT NoMatch; MUST work under offline-only.

### slots — Which slot types ship in v1.1?
- **D-07 [slots]:** The four types with min..max bounds only; no date/time/unit types and no SlotParser seam in v1.1 (each is one more frozen fun interface). Example packs live in src/test or :sample (CLN-02 deny-list). _(source: ai-auto)_

### numbers — Number-word scope.
- **D-08 [numbers]:** Full golden table with the round-trip test; ambiguous digit grouping → NoMatch. _(source: ai-auto)_

### normalize — `normalize(raw, language) -> String?` semantics: what does null mean?
- **D-09 [normalize]:** null = reject (PITFALLS; CT D-02 "a miss returns NoMatch"). Text/list slots only; runs after match, before Extraction; raw = original surface text; language = matched pack (never null); throw → NoMatch + trace code (guardedPlain), not strategy_error. _(source: ai-auto)_
  - **Consumer condition (binding):** CT condition (answer #7): normalize returns canonical English for in-map EN/ES tokens; null → NoMatch → cloud.

### label — When the input carries a language label, try only that pack or both?
- **D-10 [label]:** Labeled pack only by default + opt-in `tryOtherLanguage` flag; null label always tries both and requires agreement; labels other than exactly "en"/"es" → NoMatch + trace code. SB can flip the flag for D-04. _(source: ai-auto)_
  - **Consumer condition (binding):** SB condition (answer #1): a null label MUST try both packs by default; SB will set the try-both flag.

### text-norm — Accent folding for matching.
- **D-11 [text-norm]:** Fold vowel accents (keep ñ distinct), NFC + Locale.ROOT lowercase, strip punctuation except interior decimal sep, collapse whitespace, split hyphens, token cap → NoMatch; fillers declared per pack/language. Accent-only collisions fail safe as ambiguity → NoMatch. _(source: ai-auto)_

### stt-forms — Real STT output for numbers/accents on the TESTER (external research).
- **D-12 [stt-forms]:** Capture a small real-transcript fixture set on the TESTER as Phase 14's first device step (no device overlap with 12/13/19); verify ES morphology edge cases against RAE before freezing. _(source: ai-auto)_

### Claude's Discretion
Areas marked `ai-auto` took research's recommendation without operator review; the planner may refine mechanics within the stated decision but must not reverse it without a new discuss pass.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Milestone decisions
- `.planning/v1.1-DECISION-MAP.md` § Phase 14 — source of every decision above (options, recommendation, provisional flags)
- `.planning/cross-repo/R-v1.1-CONSUMER-ANSWERS.md` — SB/CT/stt/orchestrator answers and binding conditions

### Scope
- `.planning/ROADMAP.md` § Phase 14 — goal, success criteria
- `.planning/REQUIREMENTS.md` — GRAM-01, GRAM-02, GRAM-03, GRAM-04, GRAM-05
- `.planning/PROJECT.md` — constraints (domain-free, additive API, secrets, A1/A7)

### Research
- `.planning/research/SUMMARY.md`, `ARCHITECTURE.md`, `PITFALLS.md`, `FEATURES.md`, `STACK.md` (all under `.planning/research/`) — v1.1 milestone research

</canonical_refs>

<code_context>
## Existing Code Insights

Code-level assets, file:line anchors and integration points are cited inline in the decisions above and in `.planning/research/ARCHITECTURE.md`; the full scout happens at plan time.

### Cross-phase dependencies
Provisional decisions depend on Phase(s) 12 — refresh them against that phase's real output at execution.

</code_context>

<specifics>
## Specific Ideas

No specific requirements beyond the decisions above — open to standard approaches.

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 14-localgrammar-bilingual-grammarpack*

## Runtime Decisions

- **D-01 refreshed [submission] (2026-10-06, ai-auto, dependency Phase 12 complete):** Full move as commit 1, refreshed against P12 HEAD 6fa22cc. Move resolutionOutcome (singleshot/SingleShotOutcomes.kt:59) and SingleShotStrategy.submitAll (SingleShotStrategy.kt:154) into strategy/StepSubmission.kt as submitSteps(session, steps, providerCallId: String?). The parameter is NULLABLE: SingleShot passes call.id, grammar passes null, matching P12 CommitCoordinator.submit(step, providerCallId: String?). No function named prepareGuarded exists at HEAD, so the planner moves whatever guarded-prepare logic submitAll calls along with it, under its real name. 14 and 15 never edit the same functions, and grammar inherits SingleShot submit semantics exactly.

- **RT-01 [tester-window] (2026-10-06):** The TESTER R5CT10XNKQN window is CONFIRMED FREE and granted for 14-09, at most 3600 s, by orchestrator yahir-gsd-control-plane-3b. `adb -s` stays pinned, and there are no radio or airplane changes. The phone plays TTS aloud, so keep the volume moderate and restore it after. The master messages "device done tester" after cleanup.
- **RT-02 [open-items] (2026-10-06):** OI-1, OI-3, OI-4, OI-5, OI-7 and OI-8 are ACCEPTED as their shipped defaults. OI-2 and OI-6 are PROVISIONALLY accepted while CT and SB review them, objection-only. Any objection reaches the master before Phase 15 plans.
- **RT-03 [open-items-final + doc] (2026-10-06, relayed by orchestrator yahir-gsd-control-plane-3b):** OI-2 and OI-6 are now FINAL: neither CT nor SB objects, and CT's own pack will add an a/an/un/una to 1 slot itself, so there is no code change. One doc requirement for v1.1.0 from SB: the public KDoc of `matchedLanguage` (GrammarMatch and Extraction) and API.md wherever this phase touches it state that it can be null on a cross-pack agreement, so callers must fall back to their own locale (SB 176 keys reply templates off it). Done in this phase, outside the device window: KDoc in GrammarMatch.kt and OutcomeResolver.kt, and the three API.md mentions. No carry for Phase 19 is needed unless Phase 19 touches those API.md lines again.
