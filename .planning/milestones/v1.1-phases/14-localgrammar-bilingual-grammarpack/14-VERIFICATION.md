---
phase: 14-localgrammar-bilingual-grammarpack
verified: 2026-10-06T18:00:00Z
status: passed
score: 5/5 must-haves verified
covered_files:
  - ".planning/phases/14-localgrammar-bilingual-grammarpack/14-01-PLAN.md"
  - ".planning/phases/14-localgrammar-bilingual-grammarpack/14-01-SUMMARY.md"
  - ".planning/phases/14-localgrammar-bilingual-grammarpack/14-02-PLAN.md"
  - ".planning/phases/14-localgrammar-bilingual-grammarpack/14-02-SUMMARY.md"
  - ".planning/phases/14-localgrammar-bilingual-grammarpack/14-03-PLAN.md"
  - ".planning/phases/14-localgrammar-bilingual-grammarpack/14-03-SUMMARY.md"
  - ".planning/phases/14-localgrammar-bilingual-grammarpack/14-04-PLAN.md"
  - ".planning/phases/14-localgrammar-bilingual-grammarpack/14-04-SUMMARY.md"
  - ".planning/phases/14-localgrammar-bilingual-grammarpack/14-05-PLAN.md"
  - ".planning/phases/14-localgrammar-bilingual-grammarpack/14-05-SUMMARY.md"
  - ".planning/phases/14-localgrammar-bilingual-grammarpack/14-06-PLAN.md"
  - ".planning/phases/14-localgrammar-bilingual-grammarpack/14-06-SUMMARY.md"
  - ".planning/phases/14-localgrammar-bilingual-grammarpack/14-07-PLAN.md"
  - ".planning/phases/14-localgrammar-bilingual-grammarpack/14-07-SUMMARY.md"
  - ".planning/phases/14-localgrammar-bilingual-grammarpack/14-08-PLAN.md"
  - ".planning/phases/14-localgrammar-bilingual-grammarpack/14-08-SUMMARY.md"
  - ".planning/phases/14-localgrammar-bilingual-grammarpack/14-09-PLAN.md"
  - ".planning/phases/14-localgrammar-bilingual-grammarpack/14-09-SUMMARY.md"
  - ".planning/phases/14-localgrammar-bilingual-grammarpack/14-10-PLAN.md"
  - ".planning/phases/14-localgrammar-bilingual-grammarpack/14-10-SUMMARY.md"
  - "API.md"
  - "core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/OutcomeResolver.kt"
  - "core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/StepSubmission.kt"
  - "core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/grammar/GrammarMatch.kt"
  - "core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/grammar/GrammarPack.kt"
  - "core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/grammar/GrammarText.kt"
  - "core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/grammar/LocalGrammarStrategy.kt"
  - "core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/grammar/OverlapExamples.kt"
  - "core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/grammar/PackValidator.kt"
  - "core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/grammar/RuleMatcher.kt"
  - "core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/grammar/SlotValidator.kt"
  - "core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/grammar/Slots.kt"
  - "core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/grammar/TemplateExpander.kt"
  - "core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/grammar/TemplateParser.kt"
  - "core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/grammar/number/DigitForms.kt"
  - "core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/grammar/number/EnglishNumbers.kt"
  - "core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/grammar/number/NumberWords.kt"
  - "core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/grammar/number/PointDecimals.kt"
  - "core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/grammar/number/SpanishNumbers.kt"
  - "core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/singleshot/SingleShotOutcomes.kt"
  - "core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/singleshot/SingleShotStrategy.kt"
  - "core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/telemetry/TraceCode.kt"
  - "core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/ApiShapeTest.kt"
  - "core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/DigitGroupingTest.kt"
  - "core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/GrammarAmbiguityTest.kt"
  - "core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/GrammarBilingualTest.kt"
  - "core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/GrammarFillersTest.kt"
  - "core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/GrammarLanguageLabelTest.kt"
  - "core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/GrammarNearMissCorpusTest.kt"
  - "core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/GrammarNeverGuessesTest.kt"
  - "core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/GrammarNormalizeTest.kt"
  - "core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/GrammarPackValidationTest.kt"
  - "core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/GrammarRedactionTest.kt"
  - "core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/GrammarSlotsTest.kt"
  - "core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/GrammarSttFixturesTest.kt"
  - "core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/GrammarTemplateTest.kt"
  - "core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/GrammarTextTest.kt"
  - "core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/LocalGrammarHeldTest.kt"
  - "core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/LocalGrammarPipelineTest.kt"
  - "core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/LocalGrammarPolicyTest.kt"
  - "core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/LocalGrammarVerdictsTest.kt"
  - "core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/NumberGoldenEnTest.kt"
  - "core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/NumberGoldenEsTest.kt"
  - "core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/NumberRoundTripTest.kt"
  - "core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/NumberSpellers.kt"
  - "core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/RedactionCanaryTest.kt"
  - "core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/TraceTest.kt"
  - "core/src/test/resources/grammar/README.md"
  - "core/src/test/resources/grammar/stt-fixtures.tsv"
  - "core/src/test/resources/grammar/stt-prompts.tsv"
covered_digest: "v1:sha256:4cbf7af04ee625c11042ade2220a68dd2075c24cbda6f240f923e51b49f012f6"
behavior_unverified: 0
overrides_applied: 0
---

# Phase 14: LocalGrammar & Bilingual GrammarPack Verification Report

**Phase Goal:** An app can put a free, offline grammar tier at the head of its ladder. Declared EN and ES phrasings resolve straight to the app's tool call with zero provider calls, and anything the grammar isn't sure of goes to the next tier untouched.
**Verified:** 2026-10-06
**Status:** passed
**Re-verification:** No, initial verification

Method: read the shipped code and tests directly (SUMMARY claims not relied on). Test evidence is the on-disk Gradle result set for `:core:test` (907 tests, 0 failures, 0 errors, 0 skipped), cross-checked as fresh: no file under `core/src` is newer than the result XMLs, and the last source commit (`bc0651d`) is the last change before the docs-only commits. Gradle was not re-run (host memory); the offline STT guard probe was run (fake adb only, no device touched).

## Goal Achievement

### Observable Truths (ROADMAP success criteria)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | One intent declares EN and ES rules (per-language phrasing, typed slots, number words); an EN transcript and its ES counterpart with a spoken number give the same tool call with the same typed slot values | VERIFIED | `GrammarPack.IntentBuilder` declares slots once per intent plus `en(...)`/`es(...)` templates (GrammarPack.kt:299-394). `GrammarBilingualTest.spokenIntegersGiveTheSameToolCallInBothLanguages` asserts `twenty one`/`veintiuno`, `two thousand five hundred`/`dos mil quinientos` produce equal `JsonObject` arguments and the same tool; decimals asserted as equal doubles. `NumberRoundTripTest` sweeps every n in 0..999,999 through an independent test-only speller in both languages (distinct spellings counted, so the sweep cannot pass vacuously). |
| 2 | A match completes with zero provider calls, writes through gate, commit and sink, a held action is reported held (never success), and `ExecutedAction.providerCallId` is null | VERIFIED | `LocalGrammarStrategy.resolveWith` builds `Extraction(tool, args, null, matchedLanguage)` then `submitSteps(session, it, null)` (LocalGrammarStrategy.kt:66-76); `submitSteps` calls `session.submit(step, providerCallId)` only (StepSubmission.kt:29-38), so there is no direct write path. `LocalGrammarPipelineTest` asserts `fake.callCount == 0` under `NoNetworkGuard`, `ActionKind.COMMITTED` in the sink, `providerCallId` null on both the executed list and the sink action, gate called once. `LocalGrammarHeldTest` asserts `ActionKind.HELD`, `applied == false`, zero apply calls, `held.size == 1`, null providerCallId, reply kept; an errored apply withholds the reply and records `IS_ERROR`. |
| 3 | An unmatched transcript, or a slot the app's resolver rejects, ends `NoMatch`; the next tier gets the command with carry cleared; the tier never returns a best guess | VERIFIED | `resolutionOutcome` passes `Resolution.NoMatch` to `StrategyOutcome.NoMatch` and the tier records `GRAMMAR_RESOLVER_REJECTED`; `TierWalk.startFresh` sets `carry = null` (TierWalk.kt:100-104). `LocalGrammarPipelineTest.aPhraseNoRuleMatches...` asserts the next tier ran once with `receivedCarries == [null]` and no sink action; `LocalGrammarVerdictsTest.aResolverRejectionEndsTheTierNoMatchWithACodeAndAClearedCarry`. Never-guesses: matcher is anchored whole-sequence with enumerate-all-parses (two distinct readings are ambiguous and match nothing); `GrammarAmbiguityTest`, `GrammarNearMissCorpusTest`, seeded mutation property in `GrammarNeverGuessesTest` (the review-fix WR-06 made these compare every field and assert non-vacuity). Build-time self-check refuses overlapping rules (`OverlapExamples`, `validateNoOverlap`). |
| 4 | An optional per-slot `normalize: (raw, language) -> String?` sees the raw slot text and the language before resolution so an app synonym map changes what resolves; library code names no domain (CLN-02 scan stays green) | VERIFIED | `IntentBuilder.normalize(slot, hook)` (GrammarPack.kt:331); `GrammarPack.normalized/ask` runs the hook per bound slot inside `decide`, before agreement, with raw spoken span (case/accents kept) and the matched pack language, null/blank is `GRAMMAR_SLOT_REJECTED`, a throw is contained as `GRAMMAR_NORMALIZE_ERROR` (no message kept). `GrammarNormalizeTest` proves a neutral EN/ES synonym map yields one canonical value, language is never null, out-of-map rejects and falls through to the next tier with null carry and zero provider calls, and the mapped value reaches the resolver. A grep of the grammar main sources for domain words (note/card/food/meal/recipe/search/browse) returns nothing; `scanBannedConstructs` was green in the last gate. |
| 5 | The tier declares `NO_PROVIDER`, so it runs under `offlineOnly` and any `allowedProviders`; under offline-only a no-match ends `Unhandled(cappedByPolicy = true)` after zero provider calls | VERIFIED | `override val capabilities = StrategyCapabilities.NO_PROVIDER` (LocalGrammarStrategy.kt:41). `LocalGrammarPolicyTest` runs a grammar tier ahead of a real SingleShot tier and a counting `FakeAiProvider` under offline-only, allowed = {OPENAI}, and allowed = empty: all commit with `callCount == 0`; the offline no-match case asserts `CommandOutcome.Unhandled.cappedByPolicy`, zero provider calls, zero resolver calls, no sink actions; a terminal intent ends handled under offline-only. |

**Score:** 5/5 truths verified (0 present, behavior-unverified).

Behavior-dependent truths (held reporting, carry clearing, ordering of the gate before write) each have a passing named test in the result set, so none is left PRESENT_BEHAVIOR_UNVERIFIED.

### Plan must-have prohibitions

| Prohibition | Tier | Status | Evidence |
|-------------|------|--------|----------|
| 14-01: no change under `strategy/agentic/` | test | OK | `git diff main...HEAD --name-only` lists no `strategy/agentic` path |
| 14-01: D-01 move not combined with a behavior change | judgment | OK (human review optional, non-authoritative) | D-01 landed alone as `cc5f4c9 refactor(14-01)`, grammar tracer in the next commit `c9e12a9` |
| 14-02/14-09: no device command without `grant: open`; TESTER only; no personal phone | test | OK | Offline probe `scripts/verify-stt-capture-guard.sh` ran here: `STT CAPTURE GUARD OK scenarios=57`; grant file shows `grant: consumed`, device R5CT10XNKQN |
| 14-09: no audio/logcat/screenshot/JSONL committed; only filtered `stt-fixtures.tsv` | test | OK | No media/log paths in the branch diff; `stt-fixtures.tsv` is 88 text rows, `synthetic-tts` provenance |
| 14-09: `:sample` main sources untouched | test | OK | No `sample/src/main` path in the branch diff |

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `strategy/StepSubmission.kt` | shared `resolutionOutcome` + `submitSteps(session, steps, providerCallId: String?)` | VERIFIED | 38 lines, substantive; used by both `SingleShotStrategy` and `LocalGrammarStrategy` (SingleShot's copies removed in the same diff) |
| `grammar/GrammarPack.kt` | public bilingual DSL + pure `match` | VERIFIED | 434 lines; full builder API, redacted `toString`, build-time validation chain |
| `grammar/LocalGrammarStrategy.kt` | the tier | VERIFIED | NO_PROVIDER, terminal path, resolver pass-through, redacted `toString` |
| `grammar/RuleMatcher.kt`, `TemplateParser/Expander.kt`, `GrammarText.kt`, `Slots.kt`, `PackValidator.kt`, `SlotValidator.kt`, `OverlapExamples.kt` | phrasing, slots, validation | VERIFIED | All substantive, all reachable from `GrammarPack.init` |
| `grammar/number/*` | strict EN/ES number words and digit grouping | VERIFIED | Wired through `Slots.kt`; golden, digit-grouping and 0..999,999 round-trip tests green |
| `grammar/GrammarMatch.kt` | public match value, `matchedLanguage` KDoc states null on cross-pack agreement (RT-03) | VERIFIED | KDoc present at GrammarMatch.kt:11 and OutcomeResolver.kt:32; API.md lines 85, 210, 303 repeat the null rule |
| `TraceCode.kt` six `GRAMMAR_*` codes | additive trace vocabulary | VERIFIED | `GRAMMAR_AMBIGUOUS`, `_LANGUAGE_UNSUPPORTED`, `_SLOT_REJECTED`, `_NORMALIZE_ERROR`, `_INPUT_TOO_LONG`, `_RESOLVER_REJECTED`; `TraceTest` extended |
| `core/src/test/resources/grammar/stt-fixtures.tsv` + `GrammarSttFixturesTest` | D-12 recognizer fold-back | VERIFIED | 88 rows asserted; README records findings (0 aliases promoted) |

### Key Link Verification

| From | To | Via | Status |
|------|----|-----|--------|
| `LocalGrammarStrategy.execute` | `GrammarPack.matchDetailed` | direct call | WIRED |
| `LocalGrammarStrategy.resolveWith` | `OutcomeResolver.resolve` | `Extraction` with null `callId`, `matchedLanguage` | WIRED |
| `resolutionOutcome` / `submitSteps` | `RunSession.submit` then `CommitCoordinator.submit(step, providerCallId = null)` | nullable providerCallId (P12 seam) | WIRED |
| `TierWalk` NoMatch | `startFresh` (carry cleared) | `StrategyOutcome.NoMatch` | WIRED |
| `LocalGrammarStrategy.capabilities` | tier eligibility under policy | `StrategyCapabilities.NO_PROVIDER` | WIRED (proved by `LocalGrammarPolicyTest` ladder with a real cloud tier behind it) |
| `GrammarPack.decide` | app normalize hooks | `normalized` then `agreed` | WIRED |

### Data-Flow Trace (Level 4)

| Artifact | Data | Source | Real data | Status |
|----------|------|--------|-----------|--------|
| `Extraction.arguments` at the resolver | slot values | `RuleMatcher` bindings from the transcript, normalized by app hooks | yes, asserted by value in tests | FLOWING |
| `ExecutedAction.providerCallId` | null | `submitSteps(..., null)` | yes, asserted null at outcome and sink | FLOWING |

### Behavioral Spot-Checks

| Behavior | Evidence | Status |
|----------|----------|--------|
| `:core` suite | on-disk results: 907 tests, 0 skipped, 0 failures, 0 errors, 20 grammar/number result files; newer than every `core/src` file | PASS |
| STT capture guard (offline, fake adb) | `bash scripts/verify-stt-capture-guard.sh` gives `STT CAPTURE GUARD OK scenarios=57`, exit 0 | PASS |
| Debt markers in changed non-planning files | grep for TBD/FIXME/XXX over the branch diff returns nothing | PASS |
| Last gate (`:core:test :core:detekt :core:scanBannedConstructs :core:metalavaCheckCompatibility`) | recorded green in 14-REVIEW-FIX.md and by the orchestrator; not re-run (memory) | ACCEPTED on the above freshness evidence |

### Probe Execution

| Probe | Command | Result | Status |
|-------|---------|--------|--------|
| `scripts/verify-stt-capture-guard.sh` | `bash scripts/verify-stt-capture-guard.sh` | `STT CAPTURE GUARD OK scenarios=57` | PASS |

### Requirements Coverage

All five IDs are declared in plan frontmatter and mapped to Phase 14 in REQUIREMENTS.md; no orphaned requirements.

| Requirement | Source Plans | Status | Evidence |
|-------------|--------------|--------|----------|
| GRAM-01 | 14-01, 14-07, 14-08 | SATISFIED | `LocalGrammarStrategy` resolves with zero provider calls and submits through the session (truth 2) |
| GRAM-02 | 14-02, 14-03, 14-04, 14-05, 14-08, 14-09, 14-10 | SATISFIED (judged on evidence, see note) | Bilingual intent-centric DSL with typed slots, number words, per-language phrasing (truth 1); strict number tables, round-trip sweep, D-12 recognizer fixtures asserted |
| GRAM-03 | 14-01, 14-04, 14-06, 14-07, 14-08 | SATISFIED | NoMatch with cleared carry, never guesses (truth 3) |
| GRAM-04 | 14-07, 14-08 | SATISFIED | `normalize` hook (truth 4) |
| GRAM-05 | 14-01, 14-07, 14-08 | SATISFIED | `NO_PROVIDER`, offline-only behavior (truth 5) |

**GRAM-02 note.** It was marked complete by `requirements.mark-complete` after being only "contributed" by 14-09/14-10 (the device-capture plans). Judged on code evidence, not on that bookkeeping: the DSL (14-01, 14-04, 14-05), the number words (14-03) and the SC-1 proof (`GrammarBilingualTest`, 14-05) are what deliver the requirement, and they exist and pass. 14-09/14-10 add recognizer-form evidence (D-12) that hardens it but did not gate it. The checkbox is correct.

### Anti-Patterns Found

None blocking. No TBD/FIXME/XXX, no `@Ignore`, no stubbed returns in the grammar sources; no default-argument constructor stubs (the ApiShapeTest guard is green after the WR-05 follow-up).

### Human Verification Required

None required for the phase goal. Informational, non-blocking:

- **WR-05 matcher change.** The review-fix report tags the filler/sentence-terminator fix (commit `90028db`) "requires human verification" because it changes matching semantics. It is covered by new tests (terminators beside fillers now match, terminators between kept words still refuse) inside the green 907-test run, so it is not counted as a gap. A reviewer may still want to skim it.
- **D-12 caveat.** The recognizer fixtures are synthetic TTS fed to the on-device recognizer, not human speech (stated in the fixtures README). The strict number table ships and only widens later, so this is a recorded limitation, not a gap. Real-speech coverage belongs to Phase 19 Gate-1.

### Gaps Summary

No gaps. Minor housekeeping, non-blocking: `14-VALIDATION.md` still reads `status: draft` and `nyquist_compliant: false` with an unticked Wave 0 list, although every test it lists now exists and passes. It is stale bookkeeping that the orchestrator may refresh; it does not affect goal achievement. `core/api.txt` is intentionally absent until the tag cut (CLAUDE.md strictly-additive rule), so Metalava compatibility is vacuous at this point; the frozen-surface review (`14-SURFACE-REVIEW.md`) covers the public members.

---

_Verified: 2026-10-06_
_Verifier: Claude (gsd-verifier)_
