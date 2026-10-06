# Grammar fixtures (Phase 14, D-12)

## stt-prompts.tsv

The stimulus list for the one-off recognizer capture on the TESTER (plan 14-09). Tab separated, header
`id	lang	text	expect	class`:

- `id`: `en-NN` or `es-NN`, unique.
- `lang`: `en` or `es`.
- `text`: the spoken carrier phrase. Neutral nouns only (counter, timer, level, page, light, volume and their Spanish
  equivalents); no app-domain word anywhere.
- `expect`: the value the strict number tables should yield (`21`, `2.5`, `1000`), or `reject` where the strict table
  must refuse the phrase.
- `class`: `units`, `teens`, `tens`, `hundreds`, `thousands`, `fraction`, `decimal`, `accent`, `nearmiss`.

`expect` is the strict-table expectation, not a claim about what the recognizer emits. The capture records what the
recognizer actually emitted; the difference is the finding.

## stt-fixtures.tsv (written by the capture, plan 14-09)

Columns: `id	lang	text	expect	recognized	provenance`. `recognized` is the final text the platform recognizer
returned for the prompt. Every captured row is labeled `provenance=synthetic-tts`.

## Caveats

- Speech here is synthesized by the on-device text-to-speech engine. It exercises the recognizer's text formatting
  (digits versus words, accents, punctuation, decimal marks) but not human acoustic variation, homophone confusions
  or accents. A recognized form seen here is evidence that the recognizer can emit it, not that people trigger it.
- The files hold synthetic text only: no PII, no keys, no recordings.
- If no TESTER window arrives before Phase 14 closes, the strict golden table ships alone and the gap is recorded as a
  deferred obligation. The lexicon is internal, so a later finding only widens acceptance.
