---
phase: 14-localgrammar-bilingual-grammarpack
plan: 06
subsystem: grammar-never-guesses
tags: [grammar, ambiguity, language-label, input-cap, near-miss-corpus, mutation-property, build-validation]
status: complete

requires:
  - phase: 14-04
    provides: RuleMatcher anchored walk with a distinct-result set, tokenizer, PackValidator
  - phase: 14-05
    provides: slots (integer, decimal, choice, text), RuleVerdict.One with bindings, SlotSpec table
provides:
  - Build-time ambiguity self-check (each flat rule's own example through the real matcher)
  - Public GrammarPack.Builder.tryOtherLanguage and the full language-label table with cross-pack agreement
  - Input cap derived from the pack (RuleMatcher.span), grammar_input_too_long
  - Near-miss, positive and seeded mutation tests that pin "the grammar tier never guesses"
affects: [14-07, 14-08, 14-09, 14-10]

requirements-completed: []
requirements-contributed: [GRAM-03]

actuals:
  tokens: 11350
  tasks: 3
  commits: 3
plan_head_before: f174a95d51d84137629ac6309d21989861d2d323
commits: 3

tech-stack:
  added: []
  patterns:
    - "Build-time self-check: each flat rule yields examples from itself (number slots at min and at max, first choice synonym, a sentinel word for text slots) and RuleMatcher.readings lists every rule that reads them; any second distinct result refuses the pack naming both rules"
    - "One decision function (GrammarPack.decide) turns the per-language verdicts into a result: any ambiguous language ends it, one hit is the answer, several hits must be equal, and only then does TooLong or None apply"
    - "The input cap is computed, never configured: the larger of the two languages' longest rule span (literals plus each slot's longest span)"

key-files:
  created:
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/grammar/OverlapExamples.kt
    - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/GrammarAmbiguityTest.kt
    - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/GrammarLanguageLabelTest.kt
    - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/GrammarNearMissCorpusTest.kt
    - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/GrammarNeverGuessesTest.kt
  modified:
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/grammar/GrammarPack.kt
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/grammar/RuleMatcher.kt
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/grammar/PackValidator.kt
    - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/GrammarPackValidationTest.kt
    - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/GrammarSlotsTest.kt
    - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/GrammarTemplateTest.kt

key-decisions:
  - "Different tools from the two packs under a null label (or a tryOtherLanguage label) are now grammar_ambiguous, harmonizing 14-05's code-less rejection; match() returns null either way"
  - "A transcript with a sentence break is checked before the cap, so a two-command dictation keeps ending with no code (the 14-04 behavior) and only a single-clause overlong transcript reports grammar_input_too_long"
  - "The cap is one value for the pack: the larger of the English and Spanish longest rule spans; fillers are stripped (per candidate language) before counting"
  - "The self-check tries each rule's examples at the smallest and largest number-slot value and the first synonym of the first option that has one; text slots use a sentinel word grown until no declared word, synonym or filler equals it"
  - "Choice-synonym and number-word variants beyond the plan's examples are not enumerated; the check is exactly the plan's example set"

patterns-established:
  - "RuleMatcher.match(tokens, cap) returns RuleVerdict.TooLong; RuleMatcher.readings(tokens) is the build-time entry that lists every parse of every rule, fillers not stripped"
  - "Cross-pack comparison lives in GrammarPack.agreed, the single place plan 14-07 can put normalize before the equality check"

coverage:
  - id: D1
    description: "ROADMAP SC-3: two distinct readings (in one pack or across EN and ES) end NoMatch with grammar_ambiguous; no score, threshold, closest or first-match path (reflection check on the public classes)"
    requirement: "GRAM-03"
    verification:
      - kind: unit
        ref: "GrammarAmbiguityTest (5), GrammarLanguageLabelTest (13), GrammarNeverGuessesTest (4)"
        status: pass
    human_judgment: false
  - id: D2
    description: "Packs whose rules overlap with different results (remove all vs remove {item}, a choice synonym equal to another intent's literal, ES too) fail at build with IllegalArgumentException naming both rules"
    requirement: "GRAM-03"
    verification:
      - kind: unit
        ref: "GrammarPackValidationTest (44 total, 5 new overlap rows)"
        status: pass
    human_judgment: false
  - id: D3
    description: "Language label table: en/es own pack only unless tryOtherLanguage, null label tries both and needs agreement, any other label ends grammar_language_unsupported; matchedLanguage and ruleId follow A5"
    requirement: "GRAM-03"
    verification:
      - kind: unit
        ref: "GrammarLanguageLabelTest (13 tests, both flag values, 8 bad labels, digit-grouping rows, tier-level rows)"
        status: pass
    human_judgment: false
  - id: D4
    description: "Derived input cap: N+1 tokens after filler stripping end grammar_input_too_long, N does not, a 5,000-word transcript is rejected in under 1 s"
    requirement: "GRAM-03"
    verification:
      - kind: unit
        ref: "GrammarNearMissCorpusTest (8 tests)"
        status: pass
    human_judgment: false
  - id: D5
    description: "Near-miss corpus (23 rows incl. negation, extra clause, wrong-language word, accent near-miss, partial number, 5%, -5, 5-10, ES 1.000, bare un/a, ciento y cinco, twenty 1, dos con medio) all end NoMatch while 27 positive rows of the same pack match; 3,000 seeded mutations of 17 positive utterances never match unless another declared utterance"
    requirement: "GRAM-03"
    verification:
      - kind: unit
        ref: "GrammarNearMissCorpusTest and GrammarNeverGuessesTest"
        status: pass
    human_judgment: false
---

# Phase 14 Plan 06: Never Guesses Summary

**The grammar tier now refuses to guess: ambiguity ends with grammar_ambiguous, overlapping rules cannot be built, the language label table (with `tryOtherLanguage` and cross-pack agreement) is pinned, an input cap is derived from the pack, and near-miss, positive and mutation tests prove it.**

## What was built

- **Self-check (Task 1).** `validateNoOverlap` in `PackValidator.kt` runs each flat rule's own example(s) through `RuleMatcher.readings`; every distinct (tool, arguments) result other than the rule's own makes the build throw, naming both tools, templates and rule ids. Examples come from `OverlapExamples.kt` (number slots at min and max, first choice synonym, a sentinel for text slots). The runtime half was already in place from 14-04/14-05 (the walk aborts on a second distinct result); this plan pinned it with `GrammarAmbiguityTest` including the pipeline row (trace code `grammar_ambiguous`, next tier runs with carry null).
- **Label table (Task 2).** `Builder.tryOtherLanguage` (default false, KDoc states the table). `candidatesFor` maps the label to the languages to read (`null` both, `en`/`es` own pack, plus the other with the flag, anything else `grammar_language_unsupported` before tokenizing). `decide`/`agreed` combine the per-language verdicts: an ambiguous language ends it, one hit is the answer with its own language and rule id, two hits must be equal in tool, terminal and arguments and then carry the label (or null) and a null rule id, otherwise ambiguous.
- **Cap and corpora (Task 3).** `RuleMatcher.span` (literals plus each slot's longest span: number phrase, longest synonym, `maxWords`); `GrammarPack` keeps the larger of the two languages as `inputCap`; `RuleMatcher.match(tokens, cap)` returns `RuleVerdict.TooLong` before any walk. No limit-named constant. The two corpus tests and the mutation property follow the plan's rows.

## TDD note

Plan type is `execute`; the tasks carry `tdd="true"` but the host is memory-constrained (one Gradle run at a time), so tests and implementation were committed together per task after one green verification run each, not as separate RED commits. Task 1 and Task 2 were each verified at their own intermediate state (the implementation was staged in task order), Task 3 with the wave gate.

## Commits

| Task | Commit | Notes |
|------|--------|-------|
| 1 | f094004 `feat(14-06): refuse overlapping rules at build and pin runtime ambiguity` | `OverlapExamples.kt`, `PackValidator.validateNoOverlap`, `RuleMatcher.readings` |
| 2 | 19f64ee `feat(14-06): language label table with tryOtherLanguage and cross-pack agreement` | `GrammarPack` label table and agreement |
| 3 | 8175ab2 `feat(14-06): derived input cap, near-miss and positive corpora, never-guesses property` | `RuleMatcher.span`/`TooLong`, cap in `GrammarPack`, two corpus tests |

## Deviations from the plan

1. **[Rule 3 - blocking] New file `OverlapExamples.kt`** (not in the plan's file list). The example builder, sentinel and fault message would push `PackValidator.kt` past detekt's per-file function cap, so only the `validateNoOverlap` entry (which takes the `RuleMatcher`) stays in `PackValidator.kt`.
2. **[Rule 3] Internal signature change:** `RuleMatcher.match(tokens)` became `match(tokens, cap)`; `GrammarSlotsTest` and `GrammarTemplateTest` call sites now pass `matcher.span`. One 14-04 row (`theSameResultFromTwoRulesIsOneResult`) expected `None` for a transcript longer than any rule, which is now `TooLong` by design; the assertion was updated.
3. **Ordering choice:** the sentence-break check runs before the cap so 14-04's `aPackRejectsATranscriptWithAnExtraClause` (no code) still holds; the first draft checked the cap first and broke that test.
4. **Harmonized code:** different tools across the two packs now report `grammar_ambiguous` (14-05 left them code-less); see key-decisions.
5. **Test pack tweak:** the plan's ambiguity example used `text("label", 3)`, which allows only one parse of "add two and a half and more" (the first parse needs a four-word label); the test uses `maxWords` 5 so both readings exist.
6. **Mutation corpus** uses digit numbers and no text slot on purpose: dropping a word of a spoken number, or any word inside a text slot, yields another valid reading, which is correct behavior and not a guess.

## Verification (plan-level only)

- Task 1 verify (`GrammarAmbiguityTest`, `GrammarPackValidationTest`, `GrammarSlotsTest`, `GrammarBilingualTest`, `GrammarTemplateTest`): exit 0 (ambiguity 5, validation 44 tests).
- Task 2 verify (`GrammarLanguageLabelTest`, `GrammarAmbiguityTest`, `GrammarBilingualTest`, `LocalGrammarPipelineTest`, `LocalGrammarVerdictsTest`): exit 0.
- Wave gate in one invocation: `:core:test :core:detekt :core:scanBannedConstructs :core:metalavaCheckCompatibility` exit 0 (full core suite, detekt zero issues, compat additive). An earlier run of the same gate failed on the two assertions in deviations 2 and 3, fixed before the Task 3 commit.
- Acceptance greps: `GRAMMAR_AMBIGUOUS` in GrammarAmbiguityTest 2 and `grammar_ambiguous` at least 1; `remove all` in GrammarPackValidationTest 2; `public var tryOtherLanguage: Boolean = false` 1; `GRAMMAR_LANGUAGE_UNSUPPORTED` in GrammarPack.kt 1; `"en-US"|"fr"` in GrammarLanguageLabelTest 3; `GRAMMAR_INPUT_TOO_LONG` in GrammarPack.kt 1; `delete everything` in GrammarNearMissCorpusTest 3; `Random(<digits>` 1; limit-named `const val` in GrammarPack.kt 0.
- No device or behavioral verification was done or needed.

## Notes for downstream plans

- 14-07: put `normalize` before the equality check in `GrammarPack.agreed`; `RuleVerdict.One.bindings` still carries the raw spoken span per slot. Normalize changes values, so the build-time self-check (which runs on declared words only) is unaffected, but the cap and `readings` run before normalize.
- Public API grew by `GrammarPack.Builder.tryOtherLanguage` only; `api.txt` is regenerated at the next tag cut.
- GRAM-03 was already ticked in REQUIREMENTS.md by 14-01; plans 14-07 and 14-08 still list it, so this plan added no tick.
- The self-check only exercises the first synonym of the first option per choice slot (the plan's example set); a clash through a later option's synonym is caught at runtime as `grammar_ambiguous`, not at build.

## Self-Check: PASSED

Created files exist (OverlapExamples.kt, GrammarAmbiguityTest.kt, GrammarLanguageLabelTest.kt, GrammarNearMissCorpusTest.kt, GrammarNeverGuessesTest.kt); commits f094004, 19f64ee and 8175ab2 exist on `gsd/phase-14-localgrammar-bilingual-grammarpack`; `commits: 3` measured from the persisted ledger (`git rev-list --count f174a95..HEAD` = 3 before this SUMMARY commit).
