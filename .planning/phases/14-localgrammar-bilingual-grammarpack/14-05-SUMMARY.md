---
phase: 14-localgrammar-bilingual-grammarpack
plan: 05
subsystem: grammar-slots
tags: [tdd, grammar, slots, numbers, choice, text, build-validation, bilingual]
status: complete

requires:
  - phase: 14-03
    provides: NumberWords.integer/decimal/longestPhrase (strict whole-span number parsing, EN and ES)
  - phase: 14-04
    provides: RuleMatcher anchored walk, GrammarText tokenizer with offsets, template expander, PackValidator
provides:
  - Four D-07 slot kinds as public IntentBuilder methods (integer, decimal, choice, text) over an internal sealed SlotSpec
  - RuleMatcher slot binding; results keyed on tool plus arguments; per-slot raw spoken span kept
  - Slot half of build-time validation (declarations, parity across languages, delimiting, choice coherence)
  - SC-1 proof test (GrammarBilingualTest)
affects: [14-06, 14-07, 14-08, 14-09, 14-10]

requirements-completed: []
requirements-contributed: [GRAM-02]

actuals:
  tokens: 12200
  tasks: 3
  commits: 5
plan_head_before: 87db4ed38b2ee6e63e85ee452a0fe26ace98dd30
commits: 5

tech-stack:
  added: []
  patterns:
    - "Slots are declared once per intent; the matcher resolves each {slot} through a per-tool name -> SlotSpec table, so EN and ES share one declaration"
    - "Each slot kind lists candidate spans at a token position; the anchored walk tries all of them, so two bindings with different values are two results (ambiguous)"
    - "Numbers: one JsonPrimitive(Long) for integer and one BigDecimal.toDouble() for decimal, so 2 and 2.0 never differ across languages"
    - "Slot validation is post-compile over the flat rules plus the parse tree (required = appears outside [ ]), so messages can name tool, slot and template text"

key-files:
  created:
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/grammar/Slots.kt
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/grammar/SlotValidator.kt
    - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/GrammarBilingualTest.kt
    - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/GrammarSlotsTest.kt
  modified:
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/grammar/GrammarPack.kt
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/grammar/RuleMatcher.kt
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/grammar/PackValidator.kt
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/grammar/TemplateExpander.kt
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/grammar/GrammarText.kt
    - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/GrammarPackValidationTest.kt
    - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/GrammarTemplateTest.kt

key-decisions:
  - "Same tool with different arguments from the two packs under a null label is Rejected(GRAMMAR_AMBIGUOUS); a different tool stays Rejected(no code) as in 14-04. Plan 14-06 owns the final cross-language agreement table and can harmonize"
  - "FlatRule gained a template field (the source text of its expansion) so post-compile slot faults name the template; RuleMatcher gained a slots table parameter and match now takes GrammarTokens"
  - "Slot validation lives in a new SlotValidator.kt (declarations and usage) plus validateRuleSlots in PackValidator.kt (where SLOTS_PER_TEMPLATE is a private const), to stay under detekt's per-file function cap"
  - "A choice option needs a synonym only in languages the intent has phrasings for; a blank synonym is refused in either"

patterns-established:
  - "RuleVerdict.One carries arguments and a per-slot SlotBinding(name, value, raw); plan 14-07's normalize hook consumes raw"
  - "Tests reach internals with FlatRule/RuleMatcher/SlotSpec directly when a state cannot be built through the public DSL"

coverage:
  - id: D1
    description: "ROADMAP SC-1: an EN transcript and its ES counterpart with spoken numbers (twenty one / veintiuno, two thousand five hundred / dos mil quinientos, two and a half / dos y medio) give the same toolName and == arguments"
    requirement: "GRAM-02"
    verification:
      - kind: unit
        ref: "core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/GrammarBilingualTest.kt (9 tests)"
        status: pass
    human_judgment: false
  - id: D2
    description: "Number slots: whole span only, min..max candidates only, language grouping (EN 1,000; ES 1.000 refused), decimals always Double, optional slot key omitted"
    requirement: "GRAM-02"
    verification:
      - kind: unit
        ref: "GrammarBilingualTest (range, grouping, label and optional rows)"
        status: pass
    human_judgment: false
  - id: D3
    description: "Choice slots bind the option id from per-language folded synonyms (multi-word too); text slots keep the spoken surface text, bounded by maxWords, never across a clause; raw span recorded for normalize"
    requirement: "GRAM-02"
    verification:
      - kind: unit
        ref: "core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/GrammarSlotsTest.kt (9 tests)"
        status: pass
    human_judgment: false
  - id: D4
    description: "Slot build-time validation: bad or duplicate names, bounds, maxWords, unused slots, required-slot parity across EN and ES, repeated slot, adjacent open spans, four-slot cap, choice coherence; optional and allowed shapes accepted"
    requirement: "GRAM-02"
    verification:
      - kind: unit
        ref: "GrammarPackValidationTest (27 new assertThrows rows plus 4 accept rows)"
        status: pass
    human_judgment: false
---

# Phase 14 Plan 05: Slots Summary

**Four slot kinds (integer, decimal, choice, text) declared once per intent and bound in the matched language, so an EN transcript and its ES counterpart with spoken numbers give the same tool call with equal typed arguments, with every slot authoring fault refused at build.**

## What was built

- `Slots.kt`: internal sealed `SlotSpec` (`IntegerSlot`, `DecimalSlot`, `ChoiceSlot`, `TextSlot`), each producing `SlotCandidate(end, JsonPrimitive)` spans at a token position; `ChoiceOption` and `Synonym` (folded with the same `tokenize` as transcripts); `SlotDecl`. Number candidates try every end up to `NumberWords.longestPhrase(language)` and keep only values inside `min..max`.
- `GrammarPack.kt`: public `IntentBuilder.integer/decimal/choice/text`, nested public `ChoiceBuilder.option` and `OptionBuilder.en/es`, all with KDoc. Pack init validates slot declarations, compiles both languages, then validates slot use across both. A null label with both packs matching now requires equal tool and equal arguments.
- `RuleMatcher.kt`: the walk is now a private `RuleWalk` that binds slots by trying every candidate; a hit is `RuleVerdict.One(rule, arguments, bindings)`; the result identity is tool plus argument object; a second distinct result aborts as ambiguous. `match` takes `GrammarTokens` (filler strip yields a slice), so text slots read the original surface.
- `SlotValidator.kt` and `PackValidator.kt`: declaration checks (name format, duplicates, integer/decimal bounds within 0..999,999 and finite, `maxWords >= 1`, choice options/ids/synonyms), and usage checks (unknown `{slot}`, unused slot, required slot missing from any expansion in either language, repeated slot, more than four slots, adjacent open-span slots). All through `require`; messages carry tool, slot and template text only.

## TDD commits

| Gate | Commit | Notes |
|------|--------|-------|
| RED 1 | 9bc85e2 `test(14-05): add failing bilingual integer and decimal slot tests` | compile failure: `integer`/`decimal` unresolved (not executed under Gradle: host memory) |
| GREEN 1 | 7e9fa0e `feat(14-05): integer and decimal slots bound in the matched language` | |
| RED 2 | 490c0c7 `test(14-05): add failing choice and text slot tests` | compile failure: `choice`/`text`/`SlotSpec.TextSlot` unresolved (not executed under Gradle) |
| GREEN 2 | 4b64f1f `feat(14-05): choice slots with per-language synonyms and bounded text slots` | |
| Task 3 | bacb5c4 `feat(14-05): slot half of build-time validation` | tests and implementation in one commit |

This plan is `type: execute`, not `type: tdd`, so no `check tdd-red-evidence` record. The two REDs are missing-symbol compile failures by construction; I did not spend a Gradle run on confirming them because the host is memory-constrained (one Gradle job at a time, earlyoom). Task 3's tests were written with its implementation in one commit rather than a separate RED.

## Deviations from the plan

1. **[Rule 3 - blocking] New file `SlotValidator.kt`** (not in the plan's file list). Putting all slot validation in `PackValidator.kt` would exceed detekt's per-file function cap; the per-rule check (`validateRuleSlots`) stays in `PackValidator.kt` next to the private `SLOTS_PER_TEMPLATE` const the plan's acceptance grep expects.
2. **[Rule 3] Signature changes the new behavior needed:** `FlatRule` has a fifth `template` field; `RuleMatcher` takes a `slots` table and `match(tokens: GrammarTokens)`; `GrammarTokens` gained `keys` and `slice`. `GrammarTemplateTest` was adjusted (helper `flat`, `matcherOf`, a keys-to-tokens `match` extension) and its "slot not supported yet" row became "undeclared slot fails".
3. **Detekt line-length fixes** in my own KDoc and one bad edit of the `validateFlat` KDoc (caught by `:core:detekt`); `config/detekt/detekt.yml` untouched.
4. **Cross-language disagreement:** a same-tool different-arguments result under a null label is `grammar_ambiguous` (see key-decisions); not specified by this plan.

## Verification (plan-level only)

- Wave gate in one invocation: `:core:test :core:detekt :core:scanBannedConstructs :core:metalavaCheckCompatibility` exit 0 (832 tests, 0 skipped, 0 failures; detekt zero issues; compat additive).
- Acceptance greps: `public fun integer(...)`, `public fun decimal(...)`, `public fun choice(...)`, `public fun text(...)`, `public class OptionBuilder internal constructor()` each 1 in GrammarPack.kt; `internal sealed class SlotSpec` 1; `longestPhrase` in Slots.kt 1; `assertEquals` in GrammarBilingualTest 24 (need 6); `SLOTS_PER_TEMPLATE` in PackValidator.kt 4; `assertThrows(IllegalArgumentException::class.java)` in GrammarPackValidationTest 27 (need 20).
- No device or behavioral verification was done or needed.

## Notes for downstream plans

- 14-06: the walk already aborts on a second distinct result and the self-check can feed examples through `RuleMatcher(rules, fillers, slots)`; the choice option/`text` examples need a sentinel the pack cannot equal. `agreed(en, es)` in `GrammarPack` is the one place for the cross-pack comparison.
- 14-07: `RuleVerdict.One.bindings` holds `SlotBinding(name, value, raw)` with the spoken text of each span; `GrammarMatch` does not carry it yet.
- Public API grew by `IntentBuilder.integer/decimal/choice/text`, `GrammarPack.ChoiceBuilder` (+`option`) and `GrammarPack.OptionBuilder` (+`en`/`es`); `api.txt` is regenerated at the next tag cut.
- GRAM-02 stays pending: plans 14-08, 14-09 and 14-10 still contribute to it.

## Self-Check: PASSED

Created files exist (Slots.kt, SlotValidator.kt, GrammarBilingualTest.kt, GrammarSlotsTest.kt); commits 9bc85e2, 7e9fa0e, 490c0c7, 4b64f1f and bacb5c4 exist on `gsd/phase-14-localgrammar-bilingual-grammarpack`; `commits: 5` measured from the persisted ledger (`git rev-list --count 87db4ed..HEAD` = 5 before this SUMMARY commit).
