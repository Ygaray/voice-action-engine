---
phase: 14-localgrammar-bilingual-grammarpack
reviewed: 2026-10-06T00:00:00Z
depth: standard
files_reviewed: 55
files_reviewed_list:
  - API.md
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/OutcomeResolver.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/StepSubmission.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/grammar/GrammarMatch.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/grammar/GrammarPack.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/grammar/GrammarText.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/grammar/LocalGrammarStrategy.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/grammar/OverlapExamples.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/grammar/PackValidator.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/grammar/RuleMatcher.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/grammar/SlotValidator.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/grammar/Slots.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/grammar/TemplateExpander.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/grammar/TemplateParser.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/grammar/number/DigitForms.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/grammar/number/EnglishNumbers.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/grammar/number/NumberWords.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/grammar/number/PointDecimals.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/grammar/number/SpanishNumbers.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/singleshot/SingleShotOutcomes.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/singleshot/SingleShotStrategy.kt
  - core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/telemetry/TraceCode.kt
  - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/ApiShapeTest.kt
  - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/DigitGroupingTest.kt
  - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/GrammarAmbiguityTest.kt
  - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/GrammarBilingualTest.kt
  - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/GrammarFillersTest.kt
  - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/GrammarLanguageLabelTest.kt
  - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/GrammarNearMissCorpusTest.kt
  - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/GrammarNeverGuessesTest.kt
  - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/GrammarNormalizeTest.kt
  - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/GrammarPackValidationTest.kt
  - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/GrammarRedactionTest.kt
  - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/GrammarSlotsTest.kt
  - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/GrammarSttFixturesTest.kt
  - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/GrammarTemplateTest.kt
  - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/GrammarTextTest.kt
  - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/LocalGrammarHeldTest.kt
  - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/LocalGrammarPipelineTest.kt
  - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/LocalGrammarPolicyTest.kt
  - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/LocalGrammarVerdictsTest.kt
  - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/NumberGoldenEnTest.kt
  - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/NumberGoldenEsTest.kt
  - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/NumberRoundTripTest.kt
  - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/NumberSpellers.kt
  - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/RedactionCanaryTest.kt
  - core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/TraceTest.kt
  - core/src/test/resources/grammar/README.md
  - core/src/test/resources/grammar/stt-fixtures.tsv
  - core/src/test/resources/grammar/stt-prompts.tsv
  - sample/build.gradle.kts
  - sample/src/androidTest/kotlin/io/github/ygaray/voiceactionengine/sample/SttFormsCaptureTool.kt
  - sample/src/debug/AndroidManifest.xml
  - scripts/run-stt-capture.sh
  - scripts/verify-stt-capture-guard.sh
findings:
  critical: 0
  warning: 7
  info: 7
  total: 14
status: resolved
---

# Phase 14: Code Review Report

**Reviewed:** 2026-10-06
**Depth:** standard
**Files Reviewed:** 55 (the test-class bodies of GrammarBilingualTest, GrammarNormalizeTest, GrammarPackValidationTest, GrammarSlotsTest, GrammarTemplateTest, GrammarTextTest, LocalGrammarPipelineTest, LocalGrammarVerdictsTest, NumberGoldenEn/EsTest and DigitGroupingTest were sampled, not read line by line; priority went to `core/src/main`, the scripts and the androidTest tool)

## Summary

The grammar tier is well built. The matcher is anchored and ambiguity-refusing, the redaction rules hold (`toString()` on `Extraction`, `GrammarMatch`, `GrammarPack` and `LocalGrammarStrategy` print counts and ids only, and trace codes carry no payload), and the hook fault path keeps only a class name. The number lexicons trace correctly for the cases I walked through: `longestPhrase` covers the longest spelling, init order is safe, and the Spanish `uno`/`un` apocope rules are right. The `StepSubmission` refactor is behaviour-preserving, and the `Extraction` 3-arg constructor signature is still public, so it is additive.

No blocker was found. Seven warnings remain. One is a real silent-failure bug reachable through the public API (`text(maxWords)` integer overflow). Two are guard weaknesses in the runner script: an environment variable that bypasses the grant gate, and a timeout path that leaves instrumentation running after the lock is released. The others are an abort-on-first-exception capture tool, a filler/terminator interaction, and two weak test assertions. The info items are smaller gaps and stale text.

## Warnings

### WR-01: `text(name, maxWords)` has no upper bound, so a large value overflows `Int` and silently disables matching

**File:** `core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/grammar/Slots.kt:51` (also `RuleMatcher.kt:480-487`, `SlotValidator.kt:279`)
**Issue:** `SlotValidator` only requires `maxWords >= 1`. The public builder doc calls the slot "bounded", but nothing stops `text("note", Int.MAX_VALUE)` as an "unbounded" idiom. Two overflows follow:
- `TextSlot.candidates` computes `position + maxWords`. For any slot not at token position 0 that wraps negative. `minOf(size, negative)` then makes the range empty, so the slot never binds and the phrasing silently never matches.
- `RuleMatcher.span` does `rule.elements.sumOf { ... spec.maxWords ... }` in `Int`. With `maxWords` near `Int.MAX_VALUE` plus any literal word, the sum wraps negative. `inputCap = maxOf(en.span, es.span)` can then be negative, so `kept.tokens.size > cap` is true for every transcript and every command ends `GRAMMAR_INPUT_TOO_LONG`.

This is silent wrong behaviour, not a build error, which is the opposite of the pack's "authoring faults throw at build" contract.
**Fix:** Bound it at build time and use saturating arithmetic where the sum is taken:
```kotlin
private const val MAX_TEXT_WORDS = 64 // or another documented ceiling
is SlotSpec.TextSlot -> require(spec.maxWords in 1..MAX_TEXT_WORDS) {
    "GrammarPack: $where needs maxWords from 1 to $MAX_TEXT_WORDS"
}
```
Also compute `position + maxWords` as `minOf(tokens.tokens.size.toLong(), position.toLong() + maxWords).toInt()`.

### WR-02: `VAE_STT_PHASE_DIR` lets any caller point the window-grant gate at a file they control

**File:** `scripts/run-stt-capture.sh:42-43`
**Issue:** `PHASE_DIR="${VAE_STT_PHASE_DIR:-.planning/phases/14-...}"` and `GRANT_FILE="$PHASE_DIR/14-WINDOW-GRANT.md"`. The header says the runner has "no option, argument or environment variable that changes the target" and that only the relayed grant file may open the window. With this variable, `VAE_STT_PHASE_DIR=/some/dir` with a hand-written `grant: open` file passes `require_open_grant` and reaches the lock and the TESTER, bypassing the committed, audited grant file. The target is still pinned to the TESTER, but the human or orchestrator approval step is the control, and this removes it. `verify-stt-capture-guard.sh` never exercises the variable, because its scenarios use a skeleton repo with the default relative path, so the guard cannot catch it. `LOCK_FILE` has the same shape: an `XDG_RUNTIME_DIR` override moves the lock and silently defeats mutual exclusion with `run-keystore-instrumented.sh` and `run-sample-gate1.sh`.
**Fix:** Drop the override and hard-code `PHASE_DIR=".planning/phases/14-localgrammar-bilingual-grammarpack"`. Add a static check to the guard script that no `${VAE_` or `GRANT_FILE=` is assigned from the environment. For the lock, either hard-code `/tmp` or `/run/user/$UID`, or assert it equals the path the sibling runners use.

### WR-03: A timed-out `run` leaves the instrumentation running on the TESTER after the lock is released

**File:** `scripts/run-stt-capture.sh:318-332`
**Issue:** On `rc == 124` the runner prints `run_timeout` and exits. `timeout` only kills the local `adb` client. The on-device `am instrument` keeps driving TTS and the recognizer, and rewriting `stt-forms.jsonl`. The `flock` is released at exit, so another runner can take the TESTER while the capture is still active. That breaks the "one device tester at a time" claim and the "never drive a device its owner is using" rule.
**Fix:** On the timeout branch, before `finish`, run `adbt shell am force-stop "$APP_PKG" >/dev/null 2>&1 || true` and `adbt shell am force-stop "$TEST_PKG" ... || true`. Add a guard scenario that asserts a `force-stop` call is logged under `RUN_MODE=timeout`. The scenario currently sets `MUTATES=1` and asserts nothing about this.

### WR-04: The capture tool has no per-prompt fault containment; one exception aborts the whole run

**File:** `sample/src/androidTest/kotlin/io/github/ygaray/voiceactionengine/sample/SttFormsCaptureTool.kt:63-66, 116-145, 180-221`
**Issue:** `captureOne` only has `try/finally` for the WAV delete. Any unchecked exception from the platform or from the file parser ends the instrumentation with a failure and no row for the remaining prompts:
- `readWav` reads `buf.getShort(body + 14)` and `getInt(body + 4)` from a `fmt ` chunk with no check that the chunk fits in the file, so a truncated or odd TTS WAV throws `IndexOutOfBoundsException`.
- `createOnDeviceSpeechRecognizer` and `startListening` can throw `SecurityException` or `IllegalStateException`.

In `recognize`, if `runOnMainSync` throws before `feeder.start()`, the pipe's write end is never closed, a minor leak. Because the runner reports only `OK`/`FAIL` and the filter takes whatever rows exist, a partial capture is easy to mistake for a complete one.
**Fix:** Wrap each iteration in `try { captureOne(...) } catch (e: Exception) { row.put("status","error").put("code", e.javaClass.simpleName) }`. Never write the message, since it could echo prompt text. Bound-check the `fmt ` chunk (`body + 16 <= bytes.size`) before reading it, and close `writer` in the failure path.

### WR-05: A sentence terminator next to a filler vetoes the match, because `clauseBreak` is decided before filler stripping

**File:** `core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/grammar/GrammarText.kt:307-311` with `RuleMatcher.kt:494-501`
**Issue:** `clauseBreak` is set while tokenizing the whole transcript, and `slice()` copies it unchanged to the kept window. For `"Okay. Turn on the light"`, with `okay` declared as a filler, the period between the filler and the command sets `clauseBreak`. `match` returns `RuleVerdict.None()` before the filler is stripped, so the filler cannot do its job. On-device recognizers with auto-punctuation commonly emit exactly this shape, and the filler feature exists for discourse markers like it. The same applies to `"Turn on the light. Thanks."` with `thanks` declared. The comma case works; the terminator case does not, and the docs promise the edges are stripped.
**Fix:** Record the break position per token (for example a `breakBefore: Boolean` on `GrammarToken`) and evaluate "any `breakBefore` among kept tokens after the first" over the kept window in `match`, instead of the whole-transcript flag. Add a `GrammarFillersTest` case for `"okay. turn on the light"`.

### WR-06: Two tests assert determinism and "decision equals match" through `toString()`, which omits argument values and passes when both sides are null

**File:** `core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/GrammarNeverGuessesTest.kt:531-535` and `core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/GrammarLanguageLabelTest.kt:273-284`
**Issue:** `GrammarMatch.toString()` deliberately prints only the argument count. `theMatchDoesNotChangeBetweenCalls` therefore cannot detect two calls returning different argument values. It also passes if both calls return null: `null?.toString()` equals `null?.toString()`. `thePureMatchIsTheDecisionTheTierActsOn` has the same blind spot. These read as strong guarantees ("the match never changes", "the pure match is the decision the tier acts on") but check little.
**Fix:** Compare `toolName`, `arguments`, `matchedLanguage`, `terminal` and `ruleId` directly. In the determinism test, also `assertNotNull` for the corpus rows.

### WR-07: The capture tool reuses one utterance id for every TTS request, so a late callback can complete the next prompt early

**File:** `sample/src/androidTest/kotlin/io/github/ygaray/voiceactionengine/sample/SttFormsCaptureTool.kt:295-313`
**Issue:** `synthesizeToFile(text, null, out, "u")` uses the same id each time, and each call installs a new listener that counts down its own latch on any `onDone` without checking the id. If a previous synthesis timed out after 60 s and then finishes late, its `onDone("u")` counts down the next prompt's latch. The next prompt then reads a half-written WAV. That yields a wrong `error/wav` row, or truncated audio recognized as a plausible but wrong phrase and recorded as `ok`. Rare, but it corrupts exactly the evidence this tool exists to produce.
**Fix:** Use a unique id per call (`"u-${prompt.id}"`) and ignore callbacks whose `utteranceId` does not match. Alternatively, call `tts.stop()` after a timeout before moving to the next prompt.

## Info

### IN-01: The build-time filler check misses a filler that is longer than a rule's leading or trailing literal run

**File:** `core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/grammar/PackValidator.kt:45-50`
**Issue:** `leading` is only the run of `Word`s before the first slot, and the check is `leading.take(filler.size) != filler`. With filler `"turn on the"` and phrasing `"turn on {x}"`, `leading` is `[turn, on]`, so the check passes. The transcript `"turn on the light"` then has `"turn on the"` stripped and no longer matches, while `"turn on light"` does. The documented guarantee is that a phrasing which begins or ends with a filler is refused.
**Fix:** Also refuse when the filler's leading words equal the whole leading run and the next element is a slot (and the mirror for trailing).

### IN-02: The runner's `run` verdict cannot tell a skipped capture from a real one

**File:** `scripts/run-stt-capture.sh:327-330`
**Issue:** The tool uses `assumeTrue`, and `am instrument` reports an assumption skip as `OK (1 test)` as well. If `-e captureSttForms true` were ever dropped or misparsed, `STT_CAPTURE: OK sub=run` would print with no capture. The next step, `filter`, would catch it with `no_ok_rows`, but only after the pull.
**Fix:** Also require the absence of `INSTRUMENTATION_STATUS_CODE: -4` (assumption failure) in `$raw`, or have the tool fail instead of skip when run under the runner.

### IN-03: Relative `pull` and `filter` paths resolve against the repository root, not the caller's directory

**File:** `scripts/run-stt-capture.sh:372`
**Issue:** `cd "$ROOT"` runs before dispatch, so `scripts/run-stt-capture.sh filter raw.jsonl out.tsv` from another directory looks for `raw.jsonl` in the repo root. A relative `pull ./out` resolves inside the repo and is refused as `dest_in_repo`. That is safe but confusing.
**Fix:** Resolve `ARG1` and `ARG2` to absolute paths (`realpath -m`) before the `cd`, or document that they are repo-root relative.

### IN-04: A stale comment in the guard script, and brittle static checks

**File:** `scripts/verify-stt-capture-guard.sh:211`
**Issue:** The comment says "The committed grant file is pending today", but `14-WINDOW-GRANT.md` is now `grant: consumed`. The scenario still passes because the `sed` forces `pending`, so the comment and scenario name mislead. The static checks use `grep` on comment-stripped text, and a trailing inline comment is not stripped, so a regex that matches a word inside such a comment could trip or hide a violation.
**Fix:** Update the comment and rename the scenario `grant_real_layout_refuses_when_pending`. Strip trailing comments or use a small parser for the static checks.

### IN-05: `Extraction`'s 4-arg constructor is internal, so apps cannot unit-test a language-keyed resolver

**File:** `core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/OutcomeResolver.kt:38-49`
**Issue:** `matchedLanguage` is a public property, but the only public constructors leave it null. A consumer who keys a reply template off `matchedLanguage` can only exercise the `"es"` branch by running the whole pipeline over a real pack. `ApiShapeTest` pins the constructor as internal on purpose, and adding it later is additive. The gap is real, though, for the apps this milestone is meant to unblock.
**Fix:** Consider a public `Extraction(toolName, arguments, callId, matchedLanguage)` constructor, or a `testing` helper in the test-fixtures source set, in a later additive tag.

### IN-06: Test hygiene: unchecked fixture count, a wall-clock assertion, and a vacuous redaction check

**File:** `core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/GrammarSttFixturesTest.kt:179-187`, `GrammarNearMissCorpusTest.kt:325-333`, `GrammarRedactionTest.kt:124-129`
**Issue:**
- The README and plan claim "88 of 88 rows asserted", but nothing asserts that count. `theFileIsNotVacuousAndEveryRefusalHasAReason` only requires `ids.size >= 1`. A truncated fixture file would still pass if the dropped rows were not in the reject maps.
- `aDictatedParagraphIsRejectedPromptlyWithTheSameCode` asserts `elapsed < 1s` on wall-clock time. On this memory-tight host that can flake under load, and the work is trivial anyway.
- In `buildErrorsNameTemplatesSlotsAndToolsOnly`, `assertFalse(message.contains(SLOT_CANARY))` is vacuous, because no canary string is ever put into the pack declarations.

**Fix:** Assert `data.size == 88` against the file's recorded row count, or against the prompts file's count. Drop the timing assertion or widen it generously. Seed a canary into a template or filler and assert it does not leak, or remove the line.

### IN-07: `GrammarPack.match` documents "has no effect", but it runs the app's `normalize` hooks

**File:** `core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/grammar/GrammarPack.kt:127-128`
**Issue:** The doc says `match` "never logs, and has no effect". A pack with `normalize` hooks calls app code on every `match`, and `API.md` recommends `match` for corpus tests. The hooks can have side effects, they run for each matching language pack, and a hook that throws `CancellationException` is swallowed as a fault by `guardedPlain`.
**Fix:** Reword to "has no effect of its own; it calls any `normalize` hooks the pack declares".

---

_Reviewed: 2026-10-06_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
