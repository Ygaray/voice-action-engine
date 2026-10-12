---
phase: 14-localgrammar-bilingual-grammarpack
plan: 10
subsystem: d12-recognizer-fold-back
status: complete
tags: [d-12, stt-fixtures, grammar, number-forms, strict-table]

requires:
  - phase: 14-09
    provides: stt-fixtures.tsv (88 filtered synthetic-tts rows), 14-WINDOW-GRANT.md grant consumed
provides:
  - GrammarSttFixturesTest, a fixture-driven test asserting all 88 captured rows against a neutral carrier pack
  - README fold-back findings and the closing D-12 status line
affects: [phase-14-verification]

requirements-completed: []
requirements-contributed: [GRAM-02]

plan_head_before: c308b2a57d26bde1eae6d2488629c864ae798109
commits: 2

actuals:
  tokens: 9000
  tasks: 2
  commits: 2

key-files:
  created:
    - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/GrammarSttFixturesTest.kt
  modified:
    - core/src/test/resources/grammar/README.md

key-decisions:
  - "No recognizer form was promoted: the only number-form refusals (space-grouped 20 000, ES single-dot 21.000, ES digit+word 100 mil) each fail D-08 (a tokenizer-level widening, ambiguous, or a banned digit/word mix), so lexicon and digit rules are untouched"
  - "Expect-reject rows are asserted on the spoken stimulus (never matches) and separately on the recognized text, because the recognizer sometimes corrected a near-miss into a valid phrase (fourty -> 14, ten hundred -> 1000); those four are pinned in RECOGNIZER_NORMALIZED with the value they read"

duration: 25min
completed: 2026-10-06
---

# Phase 14 Plan 10: D-12 recognizer fold-back Summary

**GrammarSttFixturesTest runs all 88 captured recognizer transcripts through a neutral EN/ES carrier pack: 45 of 77 value rows read exactly as spoken, 32 are listed refusals with reasons (28 recognizer mishears, 4 strict number forms), zero promotions, public API untouched.**

## Accomplishments

- Task 1 (capture path, grant: consumed, no `deferred_obligation`): `GrammarSttFixturesTest` loads `/grammar/stt-fixtures.tsv` from the test classpath, matches each row under its language label against integer and decimal packs whose carriers mirror the 14-02 prompts, asserts the row count equals the data-row count and is non-zero, checks that every spoken stimulus reads as its expected value (proving the carriers), and holds `KNOWN_REJECTS` (id to reason) plus `RECOGNIZER_NORMALIZED`. A refusal that starts to read fails the test, so the map cannot go stale silently.
- README gained the fold-back findings (what read, what stayed strict and why, negative findings, no promotions).
- Task 2: wave gate `:core:test :core:detekt :core:scanBannedConstructs :core:metalavaCheckCompatibility` green, then `D-12 status: captured 2026-10-06, 88 rows, 0 aliases promoted` appended to the README.

## Findings

- 45 of 77 expect-value rows: recognized text reads as exactly the expected value, none as another value.
- 32 refusals: 28 are recognizer mishears (Spanish carrier `pon`/`ve a` heard as `con`, `un`, `Ponle`, `Mira`, `miren`; dropped or changed values such as `a hundred` to `200`, `1116` to `116`), so no number alias applies. 4 are number forms kept strict under D-08: `20 000` and `15 000` (space groups), `21.000` (ES single-dot group, ambiguous), `100 mil` (digit and word mix).
- 11 expect-reject rows: every spoken near-miss is refused; 4 recognized texts were self-corrected by the recognizer into valid phrases and read as that value only.
- Evidence is synthetic TTS: it shows what the recognizer can emit, not what people trigger.

## Task Commits

1. Task 1: `1645168` test(14-10): fold the D-12 recognizer capture into the grammar (88 rows asserted, no promotions)
2. Task 2: `471cf73` docs(14-10): close the D-12 record after the green :core wave gate

## Gate results

| Gate | Result |
|------|--------|
| `:core:test --tests *GrammarSttFixturesTest* *NumberGoldenEnTest* *NumberGoldenEsTest* *NumberRoundTripTest* *GrammarNearMissCorpusTest*` | green |
| `:core:test :core:detekt :core:scanBannedConstructs :core:metalavaCheckCompatibility` | green |
| `git diff --name-only f0e5e69 HEAD -- core/api.txt` | empty (public API unchanged) |

## Deviations from Plan

1. [Rule 1 - plan wording vs data] The plan says each expect-reject row never matches. Four recognized texts for reject rows (`fourty` heard as `14`, `ten hundred` as `1000`, `one thousand thousand` as `one thousand`, `ciento y cinco` as `105`) are valid phrases by themselves, so a literal assertion would be wrong. The test asserts the spoken near-miss stimulus never matches, and pins those four recognized texts to the value they read in `RECOGNIZER_NORMALIZED`.
2. [Rule 3 - blocking] A test-file `Row` class clashed with a private one in `GrammarNearMissCorpusTest`; renamed `FixtureRow`. detekt MaxLineLength fixed by wrapping.

No device was touched in this plan (no adb).

## Deferred obligations

None. The capture path was taken (grant: consumed, 88 ok rows); D-12 is closed with `D-12 status: captured`.

## Self-Check: PASSED

- Files exist: `GrammarSttFixturesTest.kt`, `core/src/test/resources/grammar/README.md` (D-12 status line present), this summary.
- Commits exist: `1645168`, `471cf73`.
- `commits: 2` measured from `git rev-list --count c308b2a..HEAD` before this summary commit.
