---
phase: 13-on-device-model-spike
plan: 05
subsystem: spike
tags: [on-device, trial-runner, gold-set, envelopes, scoring, private-labels, spike]

requires:
  - phase: 13-on-device-model-spike
    provides: "evidence grammar, TrialRecord, SchemaSubset, Scoring, filter (13-03); SpikeOnDeviceProvider, LlmBackend seam, FakeLlmBackend (13-04)"
provides:
  - "TrialRunner: one gold item through the real SingleShot + router + on-device gate + SpikeOnDeviceProvider path for one (cell, envelope), scored into a TrialRecord that renders to a VAE_SPIKE_TRIAL line the host filter keeps"
  - "GoldSet loader/validator (distinct ids and transcripts, known tools and args, schema-valid labels, forced subset, minima by bucket, stable error codes) and GoldMatcher (NFC/lowercase/whitespace strings, numeric equality, arrays as sets, extra keys ignored at every depth)"
  - "SmallEnvelope (4 domain-free tools) with the committed small-gold.json (55 EN + 55 ES positives, 34 negatives, 20 forced-subset items)"
  - "SbEnvelope: loads the private SB fixture and SB gold from app-private storage only, digest-pinned, loud on any unsupported schema keyword; absent fixture is SbState.Absent, never a silent green"
  - "Private SB gold authored outside the repository against the re-pinned 19-tool fixture (sha prefix 8bc739ed): 52 EN + 52 ES positives, 40 negatives, 20 forced-subset items"
affects: [13-06, 13-07, 13-08, 13-09, 13-10, 13-11]

actuals:
  tokens: 30000
  tasks: 3
  commits: 7

plan_head_before: 8cd686bfe024232f5a5b9d1576e3b7407fac9caf
commits: 7

tech-stack:
  added: []
  patterns:
    - "Scoring reads the model's own answer from a CapturingProvider (a decline is a normal answer the pipeline outcome cannot tell from other escalations)"
    - "A write is counted at the gate and never applied: NoOpMutation + RecordingGate (admit and record)"
    - "Private inputs: files outside the repo (dir 700, files 600), opaque item ids in all evidence, toString shows counts and 8-hex prefixes only"
    - "Loud loaders: stable error codes, never item text; unknown schema keyword fails the envelope load"

key-files:
  created:
    - spike-ondevice/src/main/kotlin/io/github/ygaray/voiceactionengine/spike/trial/TrialRunner.kt
    - spike-ondevice/src/main/kotlin/io/github/ygaray/voiceactionengine/spike/trial/MeteredBackend.kt
    - spike-ondevice/src/main/kotlin/io/github/ygaray/voiceactionengine/spike/trial/CapturingProvider.kt
    - spike-ondevice/src/main/kotlin/io/github/ygaray/voiceactionengine/spike/trial/SpikeResolver.kt
    - spike-ondevice/src/main/kotlin/io/github/ygaray/voiceactionengine/spike/trial/RecordingGate.kt
    - spike-ondevice/src/main/kotlin/io/github/ygaray/voiceactionengine/spike/gold/GoldSet.kt
    - spike-ondevice/src/main/kotlin/io/github/ygaray/voiceactionengine/spike/gold/GoldMatcher.kt
    - spike-ondevice/src/main/kotlin/io/github/ygaray/voiceactionengine/spike/envelope/EnvelopeSnapshot.kt
    - spike-ondevice/src/main/kotlin/io/github/ygaray/voiceactionengine/spike/envelope/SmallEnvelope.kt
    - spike-ondevice/src/main/kotlin/io/github/ygaray/voiceactionengine/spike/envelope/SbEnvelope.kt
    - spike-ondevice/src/main/assets/gold/small-gold.json
    - spike-ondevice/src/test/kotlin/io/github/ygaray/voiceactionengine/spike/TrialRunnerTest.kt
    - spike-ondevice/src/test/kotlin/io/github/ygaray/voiceactionengine/spike/GoldSetTest.kt
    - spike-ondevice/src/test/kotlin/io/github/ygaray/voiceactionengine/spike/GoldMatcherTest.kt
    - spike-ondevice/src/test/kotlin/io/github/ygaray/voiceactionengine/spike/SbEnvelopeTest.kt
  modified:
    - spike-ondevice/src/main/kotlin/io/github/ygaray/voiceactionengine/spike/verdict/SchemaSubset.kt
    - spike-ondevice/src/test/kotlin/io/github/ygaray/voiceactionengine/spike/SchemaSubsetTest.kt

key-decisions:
  - "SchemaSubset gained the keywords the real SB fixture uses (minLength, maxLength, minimum, maximum, maxItems, pattern, format uuid, default as annotation); each is enforced, not skipped, and every other keyword stays unknown and loud"
  - "SB gold positives cover only tools whose required arguments can be spoken (read, search and create tools); tools that need a UUID card or tag id are not labeled as positives, because a single-shot first call cannot know an id. The tool count (19) stays a reported dimension"
  - "GoldMatcher ignores extra predicted keys at every depth and matches arrays as exact-size sets, so a defaulted flag inside a list item does not turn a correct list into a miss"
  - "Any provider failure scores schema_valid=0 and a miss (tool_match=0), including on negatives: a failure to answer is never counted as a decline"
  - "TrialRunner.run takes an optional stage (default: the envelope's screen stage) because TrialRecord carries one; the plan's four-argument call still works"

requirements-completed: []

coverage:
  - id: D1
    description: "One gold item runs through the real pipeline (SingleShotStrategy, router, on-device gate, SpikeOnDeviceProvider) and becomes a scored TrialRecord whose VAE_SPIKE_TRIAL line parses back through SpikeLine and is kept by the host filter; false writes are counted at the gate and never applied"
    requirement: SPIKE-01
    verification:
      - kind: other
        ref: "TrialRunnerTest (8 tests) and scripts/spike-evidence-filter.sh keeps 2 of 2 TRIAL lines, exact closed key list, opaque item ids"
        status: pass
    human_judgment: false
  - id: D2
    description: "The committed small gold set has at least 50 distinct EN positives, 50 distinct ES positives, 30 negatives and exactly 20 forced-subset items, is domain-free, and is validated at load (duplicate ids or transcripts, unknown tool or arg, invalid label, wrong forced subset, below-minimum counts are rejected with a code and no item text)"
    requirement: SPIKE-01
    verification:
      - kind: other
        ref: "GoldSetTest (19) + GoldMatcherTest (11); small-gold.json counts 55/55/34/20; domain-word grep prints 0"
        status: pass
    human_judgment: false
  - id: D3
    description: "The SB-sized envelope loads only from app-private storage, is digest-pinned to the fixture (fixture_mismatch), fails loudly on an unsupported keyword (unknown_keyword:<kw>), and reports an absent fixture as SbState.Absent; nothing SB-derived is tracked, bundled in the APK or printed"
    requirement: SPIKE-01
    verification:
      - kind: other
        ref: "SbEnvelopeTest (14 tests, incl. the real private files loading through the real loader); SB GOLD OK check; APK listing has no SB file; git status --ignored shows no sb-gold or sb-fixture entry"
        status: pass
    human_judgment: false
  - id: D4
    description: "Whether the SB gold labels are right (EN and ES phrasing, negatives, expected arguments) as a measure of E2B accuracy is judged only by the device run"
    requirement: SPIKE-01
    verification: []
    human_judgment: true
    rationale: "Label quality and the real accuracy numbers appear only when E2B answers them on the TESTER (13-08); the labels are authored by this executor by default (RESEARCH Open Question 1) and may be replaced by SB's own file at the same private path before the device run"

duration: 13min
completed: 2026-10-06
status: complete
---

# Phase 13 Plan 05: Trial runner, gold sets and the two envelopes Summary

**One gold item through the real engine path is now a scored, filtered, parseable `VAE_SPIKE_TRIAL` line, with a committed small EN/ES/negative gold set and an SB-sized envelope whose fixture and labels live only in a private file outside the repository.**

## Performance

- **Duration:** about 13 min (00:39Z to 00:52Z)
- **Tasks:** 3 (1 tracer, 1 TDD, 1 auto)
- **Commits:** 7 (measured from the plan ledger)
- **Files:** 17 in the repository (15 created, 2 modified), plus 2 private files outside it

## Accomplishments

- **Tracer green end to end (D-04, D-06).** `TrialRunner` builds one pipeline per (cell, envelope): `SingleShotStrategy` (forced only for the forced shape), the router, the real on-device gate and `SpikeOnDeviceProvider`, wrapped in `CapturingProvider` and `MeteredBackend`. A trial yields a `TrialRecord` with `schema_valid`, `tool_match`, `args_match`, `false_write`, monotonic `latency_ms`, the runtime's own `ttft_ms` and token counts, and a stable `outcome`. The two rendered lines pass `scripts/spike-evidence-filter.sh` (`FILTER OK kept=2 dropped=0`), carry exactly the closed TRIAL key list in order, and use opaque ids (`s_en_001`, `s_neg_001`). The tracer gate (end-of-phase, automated-only verify) re-ran green before any expansion.
- **Writes are counted, never applied.** The resolver turns a mutating call into a `NoOpMutation`, a read into a finished read; `RecordingGate` records proposal tool names and admits. A negative item whose run hands the gate a mutating proposal is a false write (THRESHOLDS (j)).
- **Scoring semantics.** Positives need the gold tool and arguments; a negative is correct when the model declines (`none` / no call) or calls a non-mutating tool. Any provider failure scores `schema_valid=0` and a miss, so a failure to answer is never counted as a decline. Exceptions other than cancellation are a counted `harness_error`, never a throw, never a re-run.
- **Small envelope and gold (D-05, D-06).** `SmallEnvelope` mirrors the sample's four domain-free tools. `small-gold.json` holds 55 EN and 55 ES positives (create with title, tags or body; edit by id; find), 34 negatives (17 EN, 17 ES: negations, cancellations, questions, chit-chat, no-target edits, out-of-scope requests) and a 20-item forced subset. `GoldSet.load` rejects duplicate ids, repeated transcripts under a new id (so N cannot be inflated), unknown tools or arg keys, labels that break their tool's schema, a wrong forced subset and below-minimum counts, each with a stable code and no item text.
- **SB envelope and private labels (D-05, D-06).** `SbEnvelope` parses the fixture and gold from `files/private/` only, checks the gold's `fixture_sha256` (`fixture_mismatch`), scans every tool schema for unsupported keywords (`unknown_keyword:<kw>`), and reports an absent fixture as `SbState.Absent` (the ladder in 13-07 turns that into `early_exit:sb_fixture_absent`). Fixture re-pinned to SB's current surface (sha256 prefix `8bc739ed`, 19 tools, copied byte-for-byte from the read-only source). Private gold (prefix `8c80bafa`): 52 EN + 52 ES positives, 40 negatives (20 EN, 20 ES), 20 forced items. Directory mode 700, both files mode 600, outside the repository.
- **Nothing SB-derived leaks.** `SB GOLD OK {pos en 52, pos es 52, neg en 20, neg es 20}`; the debug APK listing has no SB file; `git status --porcelain --ignored` shows 0 sb-gold or sb-fixture entries; `verify-repo-hygiene.sh` prints `HYGIENE OK`; no SB tool name appears under `spike-ondevice/` or `scripts/`.

## Task Commits

1. **Task 1 (tracer): trial runner + decorators + resolver + gate + minimal matcher** - `da96025` (feat)
2. **Task 2 (TDD) RED: gold set and matcher rules, small envelope, loader stub** - `2ea7aea` (test; 22 of 30 failing as intended)
3. **Task 2 (TDD) GREEN: loader, full matcher, small-gold.json** - `2463217` (feat)
4. **Task 3 prerequisite: SchemaSubset supports the SB fixture's keywords** - `bf750a9` (fix)
5. **Task 3: SbEnvelope + SbEnvelopeTest** - `3626cb7` (feat)
6. **Matcher refinement for list items** - `633ca05` (fix)
7. **Guarded real-private-files test** - `2418263` (test)

## Decisions Made

See `key-decisions`. Two worth knowing for later plans: (1) the SB labels deliberately skip tools that need a UUID id (a single-shot first call cannot know one), so the SB positives measure read, search and create calls; the negatives still include ambiguous edit and delete requests, which is where a hallucinated id would show up as a false write. (2) The SB gold was authored by this executor (RESEARCH Open Question 1 default); an SB-supplied file at `~/.local/share/vae-spike/sb-gold.json` with the same fixture digest pin would drop in unchanged.

## Deviations from Plan

**1. [Rule 1 - Bug] SchemaSubset could not load the real SB fixture**
- **Found during:** Task 3
- **Issue:** The current SB fixture uses `minLength`, `maxLength`, `minimum`, `maximum`, `maxItems`, `pattern`, `format` (uuid) and `default`, none of which 13-03's `SchemaSubset` supported, so every SB tool schema would have been `unknown_keyword` and the SB envelope could never load (RESEARCH had flagged "enumerate at run time and fail loudly").
- **Fix:** Added those keywords to `SchemaSubset` and enforce each (not skip), with `default` as an annotation; an unknown format name or a pattern that does not compile is still reported unknown. Tests added for each bound, pattern and uuid format.
- **Files modified:** `verdict/SchemaSubset.kt`, `SchemaSubsetTest.kt`
- **Verification:** `SchemaSubsetTest` 16/16; the real private files load through `SbEnvelope.fromPrivateDir`
- **Commit:** `bf750a9`

**2. [Rule 1 - Bug] Matcher counted a defaulted list-item flag as a miss**
- **Found during:** Task 3 (authoring list labels against the fixture's list-item schema)
- **Issue:** Task 2's matcher compared nested objects exactly, so a correct list whose items carried `is_completed: false` would have been scored wrong.
- **Fix:** Extra predicted keys are ignored at every depth; arrays match as exact-size sets with a distinct predicted element per expected one. Tests added.
- **Files modified:** `gold/GoldMatcher.kt`, `GoldMatcherTest.kt`
- **Commit:** `633ca05`

**3. [Plan-shape notes, no behavior change]**
- `envelope/EnvelopeSnapshot.kt` (the neutral envelope value both envelopes produce) and the data model `GoldItem` were added in Task 1 because `TrialRunner.run` needs them before Tasks 2 and 3 exist; `GoldSet.kt` grew its loader in Task 2.
- `TrialRunner.run` has an optional fifth parameter `stage` (default: the envelope's screen stage), because `TrialRecord` carries a stage; the plan's four-argument call still works.
- A guarded host test (`theRealPrivateFilesLoadWhenTheyArePresent`) was added to `SbEnvelopeTest`; it skips when the private files are absent.

**Total deviations:** 2 auto-fixed (Rule 1), 3 plan-shape notes. **Impact:** both fixes were needed for the SB envelope to be usable at all; no scope change.

## Issues Encountered

None blocking. Host memory was tight but the single-Gradle low-memory recipe finished every invocation; no `./gradlew --stop`, no device or adb access. Full module run: 160 tests, 0 failures, 1 skipped (`VerdictReproductionTest`, which needs an evidence directory and is skipped by design).

## Authentication Gates

None.

## Verification

- `TrialRunnerTest` 8/8; `GoldSetTest` 19/19; `GoldMatcherTest` 11/11; `SbEnvelopeTest` 14/14 (including the real private files via the real loader); `:spike-ondevice:testDebugUnitTest` 160 tests, 0 failures.
- Tracer filter check: `scripts/spike-evidence-filter.sh < build/spike-trial-lines.txt` keeps 2 of 2 TRIAL lines; `KEYS OK`; both `item=` values match `^s_(en|es|neg)_[0-9]{3}$`.
- Small gold: counts `{('pos','en'): 55, ('pos','es'): 55, ('neg','en'): 17, ('neg','es'): 17}`, 20 forced; the domain-word grep prints 0.
- Private SB gold: `SB GOLD OK {('pos','en'): 52, ('pos','es'): 52, ('neg','en'): 20, ('neg','es'): 20} 8bc739ed`; `stat` prints 600 for the gold; directory 700; APK listing has no SB file (`assembleDebug` exit 0); `git status --porcelain --ignored` has 0 sb-gold or sb-fixture entries.
- `git diff --stat -- core/` is empty; `scripts/verify-repo-hygiene.sh` prints `HYGIENE OK`.
- No device, adb or TESTER access was used (host-only plan; behavioral verification belongs to the Gate-1 tester).

## Next Phase Readiness

- 13-06 (runner) pushes `~/.local/share/vae-spike/sb-fixture.json` and `sb-gold.json` into `files/private/` (mode guarded) and calls `SbEnvelope.fromPrivateDir(File(filesDir, "private"))`; `SbState.Absent` must end every sb stage `early_exit:sb_fixture_absent`, `SbState.Invalid(code)` ends it red with that code. The SB tool count is `Loaded.toolCount` (19 at this pin), a reported dimension, never an assumption.
- 13-07 builds the ladder on `TrialRunner.run(cell, envelope, item, firstInProcess, stage)`, `SmallEnvelope.envelope`, `GoldSet.load`/`forcedSubset`, and `Minima.DEFAULT` (50/50/30/20).
- 13-08's window request should tell the orchestrator the SB gold was authored by this executor against fixture prefix `8bc739ed` and can be replaced by an SB-supplied file with the same digest pin before the device run.

## Self-Check: PASSED

- Created files present: all 15 `key-files.created` paths exist; the two private files exist outside the repository with modes 600 (dir 700).
- Commits present: `da96025`, `2ea7aea`, `2463217`, `bf750a9`, `3626cb7`, `633ca05`, `2418263`; `git rev-list --count` from the ledger base measures 7.
