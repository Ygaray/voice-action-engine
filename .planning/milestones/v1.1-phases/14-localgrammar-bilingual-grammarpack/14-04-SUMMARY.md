---
phase: 14-localgrammar-bilingual-grammarpack
plan: 04
subsystem: grammar-phrasing-layer
tags: [tdd, grammar, text-fold, tokenizer, template-syntax, sub-rules, fillers, build-validation, internal]
status: complete

requires:
  - phase: 14-01
    provides: GrammarPack tracer (intent/en/es), GrammarMatch, LocalGrammarStrategy, grammar TraceCodes
provides:
  - GrammarText - D-11 fold and tokenizer with NFC offsets and a clause-break flag (internal)
  - TemplateParser / TemplateExpander - hand-written mini-syntax (words, [optional], (a|b), <rule>, {slot}) flattened to folded sequences
  - RuleMatcher - anchored, enumerate-all-parses matcher; two distinct results are ambiguous; per-language filler strip
  - PackValidator - template-level build checks (all require, IllegalArgumentException)
  - public Builder.enRule/esRule/enFillers/esFillers
affects: [14-05, 14-06, 14-07, 14-08, 14-10]

requirements-completed: []
requirements-contributed: [GRAM-02, GRAM-03]

actuals:
  tokens: 14000
  tasks: 3
  commits: 6
plan_head_before: 887410f7a0c42711d5a6e08cc53c70b1a07e1b73
commits: 6

tech-stack:
  added: []
  patterns:
    - "One fold for transcripts, template words and fillers: tokenize(); keys only compared, offsets kept for later slot surface text"
    - "Templates compile at build to flat rules (language, tool, folded words); nothing is parsed at match time"
    - "Result identity is a single function (resultKey) so 14-05 can extend it to tool + terminal + arguments"
    - "Detekt satisfied by structure (small functions, expression bodies); config/detekt/detekt.yml untouched"

key-files:
  created:
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/grammar/GrammarText.kt
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/grammar/TemplateParser.kt
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/grammar/TemplateExpander.kt
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/grammar/RuleMatcher.kt
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/grammar/PackValidator.kt
    - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/GrammarTextTest.kt
    - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/GrammarTemplateTest.kt
    - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/GrammarPackValidationTest.kt
    - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/GrammarFillersTest.kt
  modified:
    - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/grammar/GrammarPack.kt
    - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/LocalGrammarPipelineTest.kt

key-decisions:
  - "A slot inside a sub-rule is a build-time IllegalArgumentException (planner assumption kept): slots stay on intents so slot parity across languages remains checkable in 14-05"
  - "Template words are folded with tokenize() (not foldKey alone) so a hyphenated or punctuated template word splits exactly like a transcript"
  - "Sub-rules are expanded once at build even when unreferenced, so a cycle or slot in an unused rule still fails loudly"
  - "RuleMatcher.resultKey is the tool name for now; 14-05 widens it to tool, terminal flag and argument object"
  - "With a null language label, ambiguity in either pack is Rejected(grammar_ambiguous); two single matches for different tools stay Rejected(no code) as in the tracer"

patterns-established:
  - "Test-only construction hook: RuleMatcher(rules, fillers) is internal, so tests build FlatRules directly to reach states the build-time duplicate check forbids"

coverage:
  - id: D1
    description: "D-11 fold: case, vowel accents with n-tilde kept, apostrophes, hyphen split between letters only, edge punctuation only, symbols and inner separators kept, locale-invariant, NFC offsets"
    requirement: "GRAM-02"
    verification:
      - kind: unit
        ref: "core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/GrammarTextTest.kt (13 tests)"
        status: pass
    human_judgment: false
  - id: D2
    description: "A11 hardening: a terminator between two words rejects the transcript; comma and a trailing terminator do not"
    requirement: "GRAM-03"
    verification:
      - kind: unit
        ref: "GrammarTextTest (clause-break rows, pack-level rejection)"
        status: pass
    human_judgment: false
  - id: D3
    description: "D-03 template mini-syntax in EN and ES: optionals, groups, sub-rules (nested, per language), anchored whole-utterance matching, syntax/cycle/unknown-rule/slot-in-rule/no-literal/expansion-bound faults at build"
    requirement: "GRAM-02"
    verification:
      - kind: unit
        ref: "core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/GrammarTemplateTest.kt (15 tests)"
        status: pass
    human_judgment: false
  - id: D4
    description: "Two distinct results in one language are ambiguous (no first-match path); identical results from several rules are one"
    requirement: "GRAM-03"
    verification:
      - kind: unit
        ref: "GrammarTemplateTest (matcher hook rows)"
        status: pass
    human_judgment: false
  - id: D5
    description: "Per-language fillers strip at the edges only (longest first, repeated); filler at a template edge, blank filler, cross-tool duplicates, accent-only collisions refused at build; match never throws"
    requirement: "GRAM-02"
    verification:
      - kind: unit
        ref: "GrammarFillersTest (8 tests), GrammarPackValidationTest (12 tests)"
        status: pass
    human_judgment: false
---

# Phase 14 Plan 04: Phrasing layer Summary

**D-11 text fold, the D-03 template mini-syntax with per-language sub-rules and fillers, and an anchored matcher that refuses on a second distinct reading, with every authoring fault failing at build.**

## What was built

- `GrammarText.kt`: `tokenize` / `foldKey`. NFC, locale-invariant `lowercase()`, an explicit vowel-accent map (no NFD-and-strip, `ñ` kept), apostrophes dropped from the key only, hyphen splits only between two letters, only the listed edge punctuation stripped, tokens carry `[start,end)` into the NFC text, and a terminator (`. ! ? ;`) between two words sets `clauseBreak`.
- `TemplateParser.kt`: one-pass recursive-descent parser (no regex) into a sealed `TemplateNode`; `isTemplateName` validates slot and rule names.
- `TemplateExpander.kt`: flattens to `FlatRule` (folded `Word`/`Slot` elements), resolves `<rule>` against the same language's sub-rules, `EXPANSION_LIMIT = 256` checked by size before each union/product, plus `compileLanguage`, which drives it all and the validator.
- `RuleMatcher.kt`: filler strip (longest first, repeated, edges only), then a recursive walk over every rule anchored at both ends; results are a set keyed by `resultKey`, and the walk stops at the second distinct one. Verdicts: `None`, `One`, `Ambiguous`.
- `PackValidator.kt`: `validateIntents`, `validateTemplateText`, `foldFillers`, `validateFlat` (no slot yet, a literal word on every path, no filler at a template edge), `admitFlat` (cross-tool duplicate, which covers accent-only collisions). Ten `require` calls, no `throw`.
- `GrammarPack.kt`: public `Builder.enRule/esRule/enFillers/esFillers` with KDoc; the pack compiles per language at build and maps `Ambiguous` to `Rejected(TraceCode.GRAMMAR_AMBIGUOUS)`. The tracer's metacharacter rejection and the exact-list index are gone.

## TDD commits

| Gate | Commit | Notes |
|------|--------|-------|
| RED 1 | 00c3760 `test(14-04): add failing D-11 text fold tests` | compile failure: `tokenize`/`foldKey` unresolved |
| GREEN 1 | 3eb239e `feat(14-04): D-11 text fold and tokenizer wired into matching` | |
| RED 2 | 345b87b `test(14-04): add failing template mini-syntax and matcher tests` | compile failure: `FlatRule`, `RuleMatcher`, `enRule` unresolved |
| GREEN 2 | 9fff31c `feat(14-04): template mini-syntax, sub-rules and anchored matcher` | |
| RED 3 | 91379eb `test(14-04): add failing filler and template-validation tests` | compile failure: `enFillers`/`esFillers` unresolved |
| GREEN 3 | 87c4637 `feat(14-04): per-language fillers and template-level build validation` | |

Each RED was confirmed by a Gradle compile of the new test file before commit. All three REDs are compile failures against symbols that did not exist yet (no stub), so they fail for the right reason but are not assertion failures. This plan is `type: execute`, not `type: tdd`, so no `check tdd-red-evidence` record was produced. No REFACTOR commit.

## Deviations from the plan

1. **[Rule 1 - Bug] Existing tracer test updated.** `LocalGrammarPipelineTest` asserted that `en("go [now]")` throws (tracer rejected metacharacters). The plan removes that rejection, so the row now uses the genuinely malformed `en("go [now")`. Committed with GREEN 2.
2. **`PackValidator.kt` arrives in Task 3; Task 2 carried the same checks inline** in `TemplateExpander.kt` (`compileLanguage`/`admit`) so the tracer's duplicate-tool test and the "no literal word" and "slot not supported yet" rows pass at the end of Task 2. Task 3 moved them into `PackValidator` as the plan says. Task 2's file list did not include `PackValidator.kt`; this is why.
3. **Test hook is the internal `RuleMatcher(rules, fillers)` constructor** and `RuleVerdict` types (the plan said "an internal test hook"); `RuleMatcher` took only `rules` in Task 2 and gained `fillers` in Task 3 (no default argument).
4. **Detekt/line-length fixes** in tests (long lines split) and `TemplateParser` (`SEQUENCE_ENDS` constant to stay under `ComplexCondition`). `config/detekt/detekt.yml` was not touched.
5. **Process nit:** I used short Python scripts for multi-line source edits (string replace on existing files); every new file went through Write.

## Planner assumption recorded

No slots inside sub-rules (build-time IllegalArgumentException). Keeps slot parity per intent checkable in 14-05; relaxing it later is additive.

## Verification (plan-level only)

- Wave gate in one Gradle invocation: `:core:test :core:detekt :core:scanBannedConstructs :core:metalavaCheckCompatibility` exit 0 (787 tests, 0 failures; detekt zero issues, no baseline; compat additive). That run also covers `ApiShapeTest`, `NoHardCodedConstantsTest`, `TraceTest`.
- Acceptance greps: `Normalizer.Form.NFC` in GrammarText (2); no `Form.NFD`/`toLowerCase(`/`Regex(` there; `clauseBreak` in GrammarPack (1); `internal fun parseTemplate` (1); no `Regex(`/`.toRegex(`/`.startsWith(` in RuleMatcher or TemplateParser; `EXPANSION_LIMIT` in TemplateExpander (5); `enRule`, `enFillers`, `esFillers` public signatures present (1 each); `require(` in PackValidator 10; `throw IllegalArgumentException` in PackValidator 0.
- New tests: 48 (13 + 15 + 12 + 8). No device or behavioral verification was done or needed.

## Notes for downstream plans

- 14-05: slots plug in at `RuleMatcher.walk` (a `Slot` element currently never matches and the validator refuses it) and `resultKey`; relax `validateFlat`'s slot refusal there and add parity/delimiting checks to `PackValidator`.
- `GrammarTokens.surface(from, to)` is ready for text-slot raw spans.
- Public API grew by four `Builder` methods (`enRule`, `esRule`, `enFillers`, `esFillers`); `api.txt` is regenerated at the next tag cut, not here.

## Self-Check: PASSED

All nine created files exist; commits 00c3760, 3eb239e, 345b87b, 9fff31c, 91379eb and 87c4637 exist on `gsd/phase-14-localgrammar-bilingual-grammarpack`; `commits: 6` measured from the persisted ledger (`git rev-list --count 887410f..HEAD` = 6 before this SUMMARY commit).
