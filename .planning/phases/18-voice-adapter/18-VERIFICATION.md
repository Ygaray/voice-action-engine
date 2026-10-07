---
phase: 18-voice-adapter
verified: 2026-10-06T23:30:00Z
status: passed
score: 2/2 roadmap success criteria verified (plus 8/8 plan must-have groups)
covered_files:
  - ".planning/REQUIREMENTS.md"
  - ".planning/phases/18-voice-adapter/18-01-PLAN.md"
  - ".planning/phases/18-voice-adapter/18-01-SUMMARY.md"
  - ".planning/phases/18-voice-adapter/18-02-PLAN.md"
  - ".planning/phases/18-voice-adapter/18-02-SUMMARY.md"
  - ".planning/phases/18-voice-adapter/18-03-PLAN.md"
  - ".planning/phases/18-voice-adapter/18-03-SUMMARY.md"
  - ".planning/phases/18-voice-adapter/18-04-PLAN.md"
  - ".planning/phases/18-voice-adapter/18-04-SUMMARY.md"
  - ".planning/phases/18-voice-adapter/18-05-PLAN.md"
  - ".planning/phases/18-voice-adapter/18-05-SUMMARY.md"
  - ".planning/phases/18-voice-adapter/18-06-PLAN.md"
  - ".planning/phases/18-voice-adapter/18-06-SUMMARY.md"
  - ".planning/phases/18-voice-adapter/18-07-PLAN.md"
  - ".planning/phases/18-voice-adapter/18-07-SUMMARY.md"
  - ".planning/phases/18-voice-adapter/18-08-PLAN.md"
  - ".planning/phases/18-voice-adapter/18-08-SUMMARY.md"
  - "core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/commit/CommitSink.kt"
  - "gradle/invariants.gradle.kts"
  - "scripts/verify-stt-confinement.sh"
  - "scripts/verify-stt-negative-controls.sh"
  - "voice-adapter/api.txt"
  - "voice-adapter/build.gradle.kts"
  - "voice-adapter/src/main/kotlin/io/github/ygaray/voiceactionengine/voiceadapter/FinalSegmentMapping.kt"
  - "voice-adapter/src/main/kotlin/io/github/ygaray/voiceactionengine/voiceadapter/LanguageLabels.kt"
covered_digest: "v1:sha256:c219755772f6f686eb87d1512604896063d238a6f2284185f37ca46a8a112d2c"
behavior_unverified: 0
overrides_applied: 0
---

# Phase 18: Voice Adapter Verification Report

**Phase Goal:** An app that captures speech with `:stt` can turn a final transcript segment into a `CommandInput` with one call, and `:core` still never depends on another hub.
**Verified:** 2026-10-06
**Status:** passed
**Re-verification:** No (initial verification)

Gradle was not run by the verifier (host memory, window consumed). Heavy-gate evidence is the recorded 18-QUIET-WINDOW.md, cross-checked against the tree and the git history. Every cheap script was run live.

## Goal Achievement

### Observable Truths (roadmap contract)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | `:voice-adapter` publishes as `...:voice-action-engine-voice-adapter`, maps one `:stt` v0.7.0 final segment to `CommandInput` per call (no aggregation), carries transcript and language label (`en`/`es`, else `null`, never a guess), documented minimum `:stt` v0.7.0+ | VERIFIED | See below |
| 2 | `:core` depends on no other hub; module-graph and `:core` classpath-allowlist gates pass; only `:voice-adapter` depends on `:stt`; an app that does not add the adapter never pulls `:stt` | VERIFIED | See below |

**Truth 1 evidence**
- Module exists: `voice-adapter/build.gradle.kts` is a `com.android.library` (minSdk 35, JVM 11, explicitApi), `artifactId = "voice-action-engine-voice-adapter"`, groupId from `engineGroup`, `singleVariant("release")` publishing. Registered in `settings.gradle.kts` include, `scripts/modules.list` (`voice-adapter aar ...`), `jitpack.yml` install line, and `allowedEdges` (`:voice-adapter` -> `:core`). `scripts/verify-module-manifest.sh` run live: `MODULE MANIFEST OK modules=core,providers,keystore,undo,voice-adapter`.
- Publication proven: 18-QUIET-WINDOW.md dry run (clean clone, isolated maven-local) published exactly five artifacts including `voice-action-engine-voice-adapter-dryrun-e15bd36c2e.aar` + `.module`, `DRY RUN OK`, `PROBE OK`. This is the same publication path JitPack uses (jitpack.yml line 6 includes `:voice-adapter:publishReleasePublicationToMavenLocal`).
- Mapping is substantive, not a stub: `FinalSegmentMapping.kt` has exactly three `FinalSegment.toCommandInput` overloads; the full one delegates to `commandInputOf(text, language, context, parentRunId)` in `LanguageLabels.kt`, which builds `CommandInput(transcript, normalizeSttLanguageLabel(label), context, parentRunId)`. Text is verbatim, `segmentId` dropped. One segment per call; no collection/iterable parameter (dump from 18-SURFACE-REVIEW shows only 3 `toCommandInput`, 3 `commandInputOf`, 1 `normalizeSttLanguageLabel`; reflection test `AdapterApiShapeTest` pins the no-joiner rule).
- Label rule: `normalizeSttLanguageLabel` returns `"en"`/`"es"` after trim+lowercase, everything else `null` (no default anywhere in the code). Tests present: `aNullLabelGivesANullLanguage`, `labelsOutsideTheClosedSetGiveANullLanguage`, `aNullLabelMapsToNullNeverADefault`, `theResultIsAlwaysEnEsOrNull`, `normalisingANormalisedValueIsIdempotent`.
- Documented minimum: INTEGRATION.md section 12 ("`:stt` v0.7.0 or newer; the `language` property compiles against v0.6.0"), ECOSYSTEM.md row ("v0.7.0 or newer"), catalog `stt-engine = "v0.7.0"` (single pin). `scripts/verify-stt-confinement.sh` check c6 ties the docs minimum to the catalog pin. Server-path `en` fallback caveat documented (INTEGRATION.md line ~903). Roadmap SC1 wording says apps keep their own aggregation.
- Run live: `STT CONFINEMENT OK checks=6`, `STT CONFINEMENT SELFTEST OK cases=12`, `DOC COVERAGE OK checks=25 types=107`, `HYGIENE OK`.

**Truth 2 evidence**
- `:core`, `:providers`, `:keystore`, `:undo` and `:sample` build files name neither `:stt` nor `:voice-adapter` (grep clean); no Kotlin source outside `voice-adapter` imports `io.github.ygaray.sttengine`; within the adapter main source only `FinalSegmentMapping.kt` does (`LanguageLabels.kt` is `:stt`-free, additionally proven by `SttFreeFacadeTest`).
- `:stt` resolves only through the single `exclusiveContent` repository in `settings.gradle.kts`, filtered to exact group `com.github.Ygaray.voice-engine-android` (never the aggregator), pinned to the immutable tag `v0.7.0`; it is `compileOnly` + `testImplementation` of the adapter only.
- Gates exist and are non-vacuous: `verifyAdapterSttCompileOnly` (POM, module.json, release classpaths, each with a vacuity assertion) in `voice-adapter/build.gradle.kts`, wired into `check`; `verifySttConfined` registered for every other published module in `gradle/invariants.gradle.kts`; existing `verifyCoreDependencyAllowlist` and `verifyModuleGraph` cover the module edges.
- Gates red-for-the-right-reason: quiet-window run of `scripts/verify-negative-controls.sh` (exit 0, `negative-control failures: 0`, 151 ok lines): `:keystore`/`:core` gaining the speech engine go red (`resolves the :stt group`), adapter publishing `:stt` goes red (`must keep :stt compileOnly`), `:providers` gaining the adapter goes red (`forbidden project dependencies`), clean tree stays green, `STT NEGATIVE CONTROLS OK plants=7`. `API DUMP PROOF OK` also recorded.
- Composition argument for "an app that doesn't add `:voice-adapter` never pulls `:stt`" (PD-01): nobody but the adapter names `:stt` (c3 + `verifySttConfined`), and the adapter publishes no `:stt` edge (POM/module gate). This is sound: the dry-run set contains no `:stt` artifact, and no published module's metadata can carry the dependency.

### Required Artifacts

| Artifact | Status | Details |
|----------|--------|---------|
| `voice-adapter/build.gradle.kts` | VERIFIED | AAR recipe, compileOnly `:stt`, publication gate, wired into `check` |
| `voice-adapter/.../FinalSegmentMapping.kt` | VERIFIED | 3 overloads, delegating to shared mapper, wired |
| `voice-adapter/.../LanguageLabels.kt` | VERIFIED | closed-set normalizer + 3 `commandInputOf`, no `:stt` import |
| `voice-adapter/api.txt` | VERIFIED (by design) | header-only seed; the cut regenerates it (D-02/D-03). Seed behaviour proven by `verify-api-seed.sh` (`executed=yes removal=red`); `VAE_PRINT_TASK=1` live prints `:voice-adapter:metalavaCheckCompatibilityRelease` |
| 5 test files (37 test-file `fun`s; 33 tests) | VERIFIED | mapping, labels, API shape, redaction sentinels, stt-free facade |
| `scripts/verify-stt-confinement.sh`, `scripts/verify-stt-negative-controls.sh` | VERIFIED | run live (confinement + selftest green); controls exercised in the quiet window |
| `gradle/invariants.gradle.kts` (`verifySttConfined`, allowedEdges row) | VERIFIED | lines 277, 341, 379 |
| `ECOSYSTEM.md`, `INTEGRATION.md` s.12, `API.md`, `README.md` | VERIFIED | content grepped; doc-coverage gate green |
| `core/.../CommitSink.kt` + `ActionEventTest.kt` (RT-01 KDoc and sentinel tests) | VERIFIED | KDoc-only change in main; sentinel tests added (+83 lines) |
| `18-QUIET-WINDOW.md`, `18-SURFACE-REVIEW.md` | VERIFIED | grant consumed, results recorded |

### Key Link Verification

| From | To | Status |
|------|----|--------|
| `FinalSegment.toCommandInput` | `commandInputOf` -> `normalizeSttLanguageLabel` -> `CommandInput` | WIRED |
| `compileOnly(libs.stt.engine)` | catalog pin + `exclusiveContent` repo | WIRED |
| `scripts/modules.list` row | include, jitpack line, allowedEdges, `api.txt` (`verify-module-manifest.sh`) | WIRED |
| `verifyAdapterSttCompileOnly` | `check` | WIRED |
| `verify-stt-negative-controls.sh` | Part 6 of `verify-negative-controls.sh` | WIRED |

### Data-Flow Trace

The mapper is a pure function (text and label in, `CommandInput` out); no external data source. N/A beyond the delegation chain above.

### Behavioral Spot-Checks and Probes

| Behavior | Evidence | Status |
|----------|----------|--------|
| Negative controls (all parts incl. Part 6) | 18-QUIET-WINDOW.md: `negative-control failures: 0` | PASS (recorded) |
| API dump proof | `API DUMP PROOF OK` | PASS (recorded) |
| Clean-cache publish and consumer probe | `DRY RUN OK`, `PROBE OK`, five artifacts | PASS (recorded) |
| `:voice-adapter:check`, `:core:check` at 8ea90f1 | 18-SURFACE-REVIEW gate table, exit 0 | PASS (recorded) |
| `:voice-adapter:test` after last code change (HEAD c45d3b9) | 18-REVIEW-FIX: 33 tests, 0 failures; `:core` ActionEvent tests 7 green | PASS (recorded, worktree) |
| `verify-stt-confinement.sh` (+ `--selftest`), `verify-docs-coverage.sh`, `verify-repo-hygiene.sh`, `verify-module-manifest.sh`, `verify-api-seed.sh` task selection | run by the verifier | PASS (live) |

No probe-*.sh files are declared by the phase; Step 7c not applicable beyond the scripts above.

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|-------------|-------------|--------|----------|
| ADPT-01 | 18-01 .. 18-08 | `:voice-adapter` maps an `:stt` v0.7.0 final segment, with detected language, to `CommandInput`; `:core` depends on no other hub | SATISFIED | Truths 1 and 2; REQUIREMENTS.md line 71 and traceability row 140 mark it Complete. No orphaned Phase 18 requirement IDs. |

### Anti-Patterns Found

None blocking. `TBD|FIXME|XXX` grep over `voice-adapter/src`, the new scripts and the adapter build file returned nothing. No `return null`/empty stubs in main sources. No detekt baseline added.

### Behavior-dependent truths

The only runtime-behavior claims (null never defaulted, verbatim text, closed set, no joiner, transcript never in `toString`, `:stt`-free facade loads without `:stt`) each have a named JVM test (see list above, plus `RedactionTest`, `AdapterApiShapeTest`, `SttFreeFacadeTest`) that passed in recorded runs. None is left PRESENT_BEHAVIOR_UNVERIFIED.

## Carry-forward judgment (explicit)

| Item | Phase 18 must-have or SC requires it? | Verdict |
|------|----------------------------------------|---------|
| Clean-cache `:adapteralone` consumer probe (an app resolving the adapter AAR from an empty cache and adding `:stt` itself) | No. Neither SC1/SC2 nor any plan must-have requires it; 18-07 and the roadmap name it as deferred (PD-06: the phase proof is the publication gate plus dry-run artifact set, both green). The AAR is published and `.module` metadata present in the dry run. | Not a Phase 18 gap. Owner Phase 19 gate / Phase 20 dry run. |
| Wire `verify-stt-confinement.sh` (and `--selftest`) into `release-cut.sh` / negative controls (REVIEW WR-02) | No. Plan 04 must-haves require the script and its selftest to exist and pass, not an automated caller. | Not a Phase 18 gap. Owner Phase 19 (manual run) / Phase 20 (wiring). |
| `release-cut.sh` gates 10/12 new-module branches for `:undo`+`:voice-adapter`; `api.txt` regeneration at the cut | No. Plans explicitly prohibit editing `release-cut.sh` / `voice-adapter/api.txt`. | Not a Phase 18 gap. Phase 20. |
| Review IN-02 (adapter DI/ML gates scan `:stt` transitive compile tree) | No; latent, green today. | Accepted skip, owner is the next `:stt` repin. |

## Notes for the orchestrator (non-blocking)

1. ROADMAP.md line 61 still shows `- [ ] **Phase 18: Voice Adapter**` while the phase section lists 8/8 plans `[x]`; tick it when closing the phase.
2. Review fixes (commits 149e906..c45d3b9) landed after the quiet-window run (e15bd36). They are KDoc, one private-body refactor (segment path delegates to `commandInputOf`), one new test, and a README line; the public surface is unchanged (no `api.txt` impact). They were verified by `:voice-adapter:test`, detekt and the docs/confinement scripts per 18-REVIEW-FIX (worktree run, not reproducible from the main checkout) and by the live script runs here, but `:voice-adapter:check` and the full negative-control suite were not re-run on the final SHA. The Phase 19 gate run and Phase 20 cut re-run these on the final SHA, so this is covered downstream rather than a Phase 18 gap.
3. REVIEW WR-01 (KDoc says ids are printed verbatim) and WR-04 (a `String` passed to the context-only overload is a context, not a run id; the stronger option of dropping the overload was not taken) are design calls the human may want to revisit before the `v1.1.0` tag freezes the overload set. They do not affect any Phase 18 success criterion.
4. 18-QUIET-WINDOW.md shows the one-hour timebox was exceeded (about 65 minutes) with no incident; informational.

## Gaps Summary

No gaps. Both roadmap success criteria and ADPT-01 are met by what exists in the tree, backed by live script runs and recorded heavy-gate evidence from the quiet window.

---

_Verified: 2026-10-06_
_Verifier: Claude (gsd-verifier)_
