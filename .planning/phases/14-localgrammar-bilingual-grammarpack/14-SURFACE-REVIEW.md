# Phase 14 frozen-surface review (D-03, one-way at the v1.1.0 tag)

Reviewed on branch `gsd/phase-14-localgrammar-bilingual-grammarpack` after plans 14-01 .. 14-07, against the real Metalava
dump of `:core` produced by `scripts/review-api-surface.sh` in an isolated copy of the tree (`API SURFACE OK
sealed=AssistantPart,CommandOutcome,GateDecision,Message,RunTermination,StrategyOutcome,ToolStep classes=193`). The dump
lives outside the repository; no `api.txt` was created or edited (the committed `core/api.txt` is still the v1.0.1
snapshot, and the compat gate in `check` runs against it).

Pitfall 5 question asked of every member: what will a consumer ask next, and can it be added without removing anything?

## What the dump shows

- Package `io.github.ygaray.voiceactionengine.core.strategy.grammar` holds exactly `GrammarPack` (+ `Builder`,
  `IntentBuilder`, `ChoiceBuilder`, `OptionBuilder`, `Companion`), `GrammarMatch` and `LocalGrammarStrategy` (+ `Builder`,
  `Companion`). Every class is `final`; there is no sealed type, enum, data shape (`copy`/`componentN`), default-argument
  stub or public static field other than the `Companion` holders.
- `Extraction` shows exactly two public constructors, `(String, JsonObject)` and `(String, JsonObject, String?)`; the
  4-argument constructor carrying `matchedLanguage` is internal. `getMatchedLanguage()` / `matchedLanguage` are the only
  additions to the class.
- `TraceCode.Companion` gained six properties: `GRAMMAR_AMBIGUOUS`, `GRAMMAR_INPUT_TOO_LONG`,
  `GRAMMAR_LANGUAGE_UNSUPPORTED`, `GRAMMAR_NORMALIZE_ERROR`, `GRAMMAR_RESOLVER_REJECTED`, `GRAMMAR_SLOT_REJECTED`.
- Internal-only (absent from the dump, as intended): `GrammarResult`, `matchDetailed`, `IntentSpec`, `SlotSpec`,
  `RuleMatcher`, `NumberWords` and the whole number lexicon, `StepSubmission.kt`.

## Member-by-member review

| Member | Decision | What a consumer asks next | Addable without removal? | Verdict |
|---|---|---|---|---|
| `GrammarPack { }` (`Companion.invoke`) | D-03 | "can I build a pack from data / merge two packs?" | yes: a new `Companion` function or a new builder method | keep |
| `GrammarPack.match(transcript, language): GrammarMatch?` | D-04 | "why did it not match?" (explain) | yes: a new function (`matchDetailed` already exists internally with the `TraceCode`); the existing one is untouched | keep |
| `GrammarPack.toString()` | D-04 | n/a | counts only; redaction pinned by tests | keep |
| `GrammarPack.Builder.tryOtherLanguage` | D-10 | "per-intent override?" | yes: a new `IntentBuilder` member | keep |
| `GrammarPack.Builder.intent(toolName, block)` | D-03 | "intent priority / explicit ordering?" | yes: a new overload or `IntentBuilder` member | keep |
| `GrammarPack.Builder.enRule(name, vararg)` / `esRule` | D-03 | "a third language?" | yes: a new `fr(...)`-style sibling; the two-language pair stays | keep |
| `GrammarPack.Builder.enFillers(vararg)` / `esFillers` | D-11 | "fillers in the middle of a sentence?" | yes: a new member; interior behavior is unchanged today | keep |
| `IntentBuilder.en(vararg)` / `es(vararg)` | D-03 | "more languages" | yes: new members; vararg, no defaults | keep |
| `IntentBuilder.terminal()` | D-06 | "a payload type for the terminal call?" | yes: a new overload | keep |
| `IntentBuilder.normalize(slot, hook: (String, String) -> String?)` | D-09 | "give the hook the intent name or the whole slot map" | partly: the function type's arity is fixed at 2 (A4); a richer hook is a new overload with a different name or a new function type | keep (see OI-1) |
| `IntentBuilder.integer(name, min, max)` | D-07 | "a bound-less or negative integer?" | yes: a new overload (bounds are `0..999,999` today) | keep |
| `IntentBuilder.decimal(name, min, max)` | D-07 | "currency or percent forms?" | yes: new slot kinds are new members | keep |
| `IntentBuilder.choice(name, block)` | D-07 | "multi-select, or a fallback option?" | yes: a new `ChoiceBuilder` member | keep |
| `IntentBuilder.text(name, maxWords)` | D-07 | "a charset or pattern constraint?" | yes: a new overload (no defaulted parameter was used) | keep |
| `ChoiceBuilder.option(id, block)` | D-07 | "an option label shown to the user?" | yes: a new member | keep |
| `OptionBuilder.en(vararg)` / `es(vararg)` | D-07 | "more languages" | yes: new members | keep |
| `GrammarMatch.toolName` | D-04 | n/a | n/a | keep |
| `GrammarMatch.arguments: JsonObject` | D-04 | "typed access" | yes: a new accessor extension or member | keep |
| `GrammarMatch.matchedLanguage: String?` | D-05 | "a definite language on cross-pack agreement" | the value rule is behavior, not shape: a new property can carry a second answer (see OI-2) | keep |
| `GrammarMatch.terminal: Boolean` | D-06 | n/a | yes | keep |
| `GrammarMatch.ruleId: String?` | D-04 | "which phrasing was it, structured?" | yes: a new property; `ruleId` stays opaque (see OI-3) | keep |
| `GrammarMatch.toString()` | D-04 | n/a | prints tool, language, argument count, flag, rule id; never values | keep |
| `GrammarMatch` constructor | D-03 | "can I build one in my tests?" | yes: constructor is internal, so a public factory is a new member | keep |
| `LocalGrammarStrategy(id) { }` (`Companion.invoke`) | D-03 | "inject a clock or a custom `capabilities`?" | yes: new builder members | keep |
| `LocalGrammarStrategy.Builder.pack` | D-03 | "several packs per tier?" | yes: a new member or a pack-merging function | keep |
| `LocalGrammarStrategy.Builder.resolver` | D-02 | n/a | yes | keep (see OI-4) |
| `LocalGrammarStrategy.id` / `capabilities` / `execute` / `toString()` | D-03, GRAM-05 | "is it really offline?" | `capabilities` is fixed `NO_PROVIDER`; nothing to remove | keep |
| `Extraction.matchedLanguage: String?` | D-05 | "the intent id / raw spoken slot text?" | yes: a new property and a new internal constructor shape; the public 2- and 3-argument constructors stay | keep |
| `Extraction` 2- and 3-argument public constructors | D-05 | n/a | unchanged signatures (compat gate and `ApiShapeTest` pin them); the 4-argument one is internal | keep |
| `TraceCode.GRAMMAR_AMBIGUOUS` | D-04 | n/a | open set, additive | keep |
| `TraceCode.GRAMMAR_LANGUAGE_UNSUPPORTED` | D-10 | n/a | open set, additive | keep |
| `TraceCode.GRAMMAR_SLOT_REJECTED` | D-09 | n/a | open set, additive | keep |
| `TraceCode.GRAMMAR_NORMALIZE_ERROR` | D-09 | n/a | open set, additive | keep |
| `TraceCode.GRAMMAR_INPUT_TOO_LONG` | D-11 | n/a | open set, additive | keep |
| `TraceCode.GRAMMAR_RESOLVER_REJECTED` | D-02 | n/a | open set, additive | keep |

No row is a "no": every member can grow by adding a member or an overload, and no signature in this phase needed a change
inside D-03's allowance.

Departures from the research sketch (shapes that differ from `14-RESEARCH.md` "Surface"): sub-rules are
`enRule` / `esRule` and fillers are `enFillers` / `esFillers` (explicit per-language methods, no `fillers(en =, es =)` or
`subRule(en =)` named-parameter form, which would have frozen default-argument stubs). Both are still additive.

## KDoc gaps found and fixed

None needed. Every public member in the three grammar files has KDoc that states its contract (slot kinds, label rules,
the build-time refusals, what is never logged), and `GrammarMatch` / `Extraction` document `matchedLanguage` and
`ruleId` including the null cases. No grammar source file was edited by this plan, so no signature changed and the
committed `api.txt` files are untouched.

## Docs

`API.md` now lists `core.strategy.grammar` in "Packages and imports", has surface-table rows for `LocalGrammarStrategy`,
`GrammarPack` and `GrammarMatch`, mentions `Extraction.matchedLanguage` in two places, has a Strategies-and-tools bullet
for the grammar tier (template syntax in one sentence, never-guesses rule, null label tries both packs), and names the
six grammar trace codes in "Telemetry and trace". No kotlin fence was added (the wiring snippet is DOC-02, Phase 19).

## Open items for orchestrator

Non-blocking; each ships with the default below. Changing one after the v1.1.0 tag is a behavior change for consumers who
already compiled against it, so overturn before the cut if at all. The driver relays these to the orchestrator.

| Id | Question | Shipped default | Cost of changing after the tag |
|---|---|---|---|
| OI-1 | Does D-09's "text/list slots" mean the `text` and the `choice` kinds? (Open Q1) | `normalize` is accepted on `text` and `choice` slots and rejected at build time on number slots. For a `choice` slot the hook receives the spoken synonym and its answer replaces the declared option id. | Narrowing it to `text` only breaks a pack that hooks a choice slot. Widening to number slots, or a new richer hook overload, is additive. |
| OI-2 | `matchedLanguage` and `ruleId` when both packs read the same call (A5; SB SC-3 / D-06) | The command's own label if one was given, else `null`; `ruleId` is `null`. A single-language match always carries "en" or "es" and its rule id. | Switching the unlabeled-agreement case to a definite language changes a value consumers may branch on; offering the second answer as a new property is additive. |
| OI-3 | Is `ruleId` opaque? (Open Q3) | Yes: an opaque string, null on cross-pack agreement, documented as not to be parsed. | The member cannot be removed after the tag; its format can change because it is documented opaque. A structured companion (intent, template text) is a new property. |
| OI-4 | Is the resolver optional when every intent is terminal? (Open Q4) | Optional only for an all-terminal pack: `LocalGrammarStrategy { pack }` builds; any non-terminal intent without a resolver is a build-time `IllegalArgumentException` naming the setting. | Requiring a resolver always would break the all-terminal consumers that rely on it. Relaxing further is additive. |
| OI-5 | Golden-table fallback when no TESTER window arrives before the phase closes (A12) | The strict golden number table ships; `14-WINDOW-GRANT.md` is `grant: pending` and no window is open. The deferral path of plan 14-09 records `grant: deferred` plus a deferred obligation (owner: milestone Gate-2). | Safe: the lexicon is internal and strict, so a later capture only widens accepted forms in a patch tag. |
| OI-6 | Are bare `a` / `an` / `un` / `una` numbers? (A3; SB, CT) | No. They count only inside compounds (`a hundred`, `a half`, `un cuarto`, `veintiun`, `treinta y una`). So "add a milk" or "agrega una leche" goes to the next tier for the cost of one cloud call. | Accepting them later turns some `NoMatch` results into matches (a recall gain, never a removal), but it would change what an existing pack returns, so decide before the tag. |
| OI-7 | Is rejecting an interior sentence terminator (`. ! ? ;`) between words acceptable? (A11) | Yes: a terminator between two words means more than one command, so the transcript matches nothing (stricter than the literal text of D-11). Edge punctuation is ignored. | Relaxing it later only raises recall; tightening it later would be the breaking direction. |
| OI-8 | Which mechanism captures real STT forms on the TESTER? (D-12) | Recognizer-direct, in plan 14-09: a debug-only instrumentation tool in `:sample` that synthesizes each prompt with on-device TTS and feeds the WAV to `SpeechRecognizer` through `EXTRA_AUDIO_SOURCE`; the stt demo file-fed path is the fallback. It runs only under a relayed `grant: open`; none exists yet. | None to the public API: the tool is unpublished, and its output only widens internal aliases. |

## Source audit (from 14-01, final statuses at this plan)

| Source | ID | Item | Plan(s) | Status |
|---|---|---|---|---|
| GOAL | n/a | Free offline EN/ES grammar tier at the head of the ladder; declared phrasings resolve with zero provider calls; anything unsure goes to the next tier untouched | 14-01, 14-04 .. 14-07 | DONE (JVM-proven; no device claim) |
| REQ | GRAM-01 | Zero-call resolution submitted through gate, commit, sink | 14-01, 14-07 | DONE |
| REQ | GRAM-02 | Bilingual DSL, typed slots, number words, per-language phrasing | 14-02 .. 14-05, 14-09, 14-10 | OPEN: DSL, slots and number words done; 14-09 (STT capture) and 14-10 (golden fixtures and round trip) pending, so the requirement stays unticked |
| REQ | GRAM-03 | No match or resolver rejection ends NoMatch, carry cleared, never guesses | 14-01, 14-06, 14-07 | DONE |
| REQ | GRAM-04 | Per-slot normalize hook, domain-free | 14-07, 14-08 | DONE (docs in 14-08) |
| REQ | GRAM-05 | NO_PROVIDER, offline-only and any provider policy | 14-01, 14-07 | DONE |
| CONTEXT | D-01 | StepSubmission move as commit 1, nullable providerCallId, agentic untouched | 14-01 | DONE |
| CONTEXT | D-02 | Resolver verdicts pass through unchanged | 14-01, 14-07 | DONE |
| CONTEXT | D-03 | Intent-centric DSL, mini-syntax, internal constructors and builders, build-time IAE | 14-01, 14-04 .. 14-06, 14-08 | DONE (this review) |
| CONTEXT | D-04 | Public pure match with redacted toString | 14-01, 14-06, 14-07 | DONE |
| CONTEXT | D-05 | Extraction.matchedLanguage | 14-01, 14-06 | DONE |
| CONTEXT | D-06 | Per-intent terminal flag to TerminalCall | 14-07 | DONE |
| CONTEXT | D-07 | Four slot kinds, bounds, no SlotParser seam | 14-05 | DONE |
| CONTEXT | D-08 | Full golden number table, round trip, ambiguous grouping to NoMatch | 14-03, 14-05, 14-10 | PARTIAL: tables and parsing done (14-03, 14-05); 14-10 pending |
| CONTEXT | D-09 | normalize null = reject, throw = NoMatch plus code | 14-07 | DONE |
| CONTEXT | D-10 | Labeled pack plus tryOtherLanguage; null label tries both | 14-01, 14-06 | DONE |
| CONTEXT | D-11 | Accent fold, NFC, lowercase, punctuation, hyphens, token cap, fillers | 14-04, 14-06 | DONE |
| CONTEXT | D-12 | TESTER STT fixture capture plus RAE check | 14-02, 14-09, 14-10 | PARTIAL: RAE check and the capture runner done (14-02); capture awaits a relayed window (OI-5) |
| RESEARCH | n/a | Six trace codes, API.md rows, redaction canary, surface review, Metalava compat | 14-01, 14-07, 14-08 | DONE |
| RESEARCH | n/a | Clause-terminator hardening (A11), derived word cap, ambiguity self-check, near-miss corpus | 14-04, 14-06 | DONE |
