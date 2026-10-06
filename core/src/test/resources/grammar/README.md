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

### Capture record (2026-10-06, plan 14-09)

- Date: 2026-10-06, window 16:18:54Z to 16:25:43Z, under the relayed D-12 grant (14-WINDOW-GRANT.md).
- Device: Samsung SM-S908U (TESTER R5CT10XNKQN), Android 15 (SDK 35).
- Recognizer: the on-device platform recognizer (`SpeechRecognizer.createOnDeviceSpeechRecognizer`, offline preferred,
  segmented session over `EXTRA_AUDIO_SOURCE`, 16 kHz mono PCM16). The device's configured recognition service
  (`voice_recognition_service`) is `com.google.android.tts/com.google.android.apps.speech.tts.googletts.service.GoogleTTSRecognitionService`.
- Speech: on-device Google TTS, en-US and es-US, one utterance at a time.
- Counts over the 88 prompts: ok=88, error=0, tts_unavailable=0 (the filter also dropped the 1 header line).
- Observed (not a claim about people): the recognizer emits digits for most numbers (67 of the 88 recognized rows contain a digit: `page 12`, `2.5`, es `2,5`),
  keeps sentence-initial capitalization, and on the Spanish TTS audio sometimes mishears carrier words
  (`pon` as `con` or `un`, `cero` as `serio`). Plan 14-10 reads the file for the exact forms.

### Fold-back findings (plan 14-10, `GrammarSttFixturesTest`)

Every row of `stt-fixtures.tsv` is asserted (88 of 88) against a neutral pack whose carriers mirror the prompts, under
the row's own language label. The test also checks that each spoken stimulus reads as its expected value, which proves
the carriers independently of the recognizer.

- Of the 77 expect-value rows, 45 recognized texts read as exactly the expected value (digits such as `page 12`, `2.5`
  and es `Pon el nivel en 31`, plus words that stayed words: en `one and a half`, es `tres cuartos`, `dos y medio`).
  None read as a different value.
- The other 32 are listed in `KNOWN_REJECTS` with a reason. 28 are recognizer mishears, not number forms: Spanish
  carrier words heard as `con`, `un`, `Ponle`, `Mira` or `miren` (the grammar reads `pon` and `ve a`), a dropped value
  (`es-19`, `es-23`), a changed value (`en-18` `a hundred` as `200`, `en-23` `a thousand` as `2000`, `es-40` `1116` as
  `116`). A different command was heard, so no number alias can help, and the carrier set is the app's, not ours.
- Four are number-form refusals, kept strict under D-08:
  - `en-27` `20 000` (and `en-28` `15 000`): the recognizer groups thousands with a space, two adjacent digit tokens.
    Reading that as one number is value-correct but it is a tokenizer-level widening, and adjacent digit tokens are
    refused today (`dos 1`). One synthetic sample is not enough; revisit with human-speech evidence.
  - `es-27` `21.000`: a single-dot group of exactly three digits in Spanish is a thousands mark or a decimal, so it
    stays ambiguous and is refused (as `NumberGoldenEsTest` already asserts).
  - `es-28` `100 mil`: digits mixed with a number word; the strict table never mixes them.
- Of the 11 expect-reject rows the spoken near-miss stimulus is refused in every case. Four recognized texts are a
  different, valid phrase because the recognizer corrected the near-miss itself (`fourty` heard as `14`, `ten hundred`
  as `1000`, `one thousand thousand` as `one thousand`, `ciento y cinco` as `105`; `treinta y cero` heard as `36` with
  a misheard carrier, so unread). The four are asserted in `RECOGNIZER_NORMALIZED` as the value they read, nothing
  else.
- Negative findings: the recognizer never emitted `ciento y`, `diez y seis`, `veinte y uno`, `treintaicinco`,
  `a hundred and`, a grouped `1,000` or a Spanish `1.000` for these prompts, so no alias for them was promoted or
  needed.
- Promotions: none. No recognized form met D-08 (unambiguous, value-correct, RAE-consistent) without a tokenizer
  change, so the lexicon and digit rules are untouched and the public API is unchanged.

## Caveats

- Speech here is synthesized by the on-device text-to-speech engine. It exercises the recognizer's text formatting
  (digits versus words, accents, punctuation, decimal marks) but not human acoustic variation, homophone confusions
  or accents. A recognized form seen here is evidence that the recognizer can emit it, not that people trigger it.
- The files hold synthetic text only: no PII, no keys, no recordings.
- If no TESTER window arrives before Phase 14 closes, the strict golden table ships alone and the gap is recorded as a
  deferred obligation. The lexicon is internal, so a later finding only widens acceptance.

D-12 status: captured 2026-10-06, 88 rows, 0 aliases promoted
