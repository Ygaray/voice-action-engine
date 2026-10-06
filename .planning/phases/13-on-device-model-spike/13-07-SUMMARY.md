---
phase: 13-on-device-model-spike
plan: 07
subsystem: testing
tags: [on-device, measurement-ladder, litertlm, evidence, thermal, pss, early-exit, spike]

requires:
  - phase: 13-on-device-model-spike
    provides: "closed VAE_SPIKE_ grammar, CellSelector, VerdictRules (13-03); LlmBackend seam and LiteRtBackend (13-04); TrialRunner, gold sets and both envelopes (13-05); guarded runner and its STAGES list (13-06)"
provides:
  - "SpikeActivity: exported stage host (am start --es stage <wire>), keep-screen-on, one closed stage extra, one stage per process start"
  - "EvidenceLog: the only android.util.Log user, append-only files/evidence/<stage>.txt, accepts SpikeLine only"
  - "Ladder: dispatch of all 13 stages in early-exit order; every stage ends with exactly one VAE_SPIKE_STAGE line (done, skipped, early_exit or error with a stable code)"
  - "EngineStages: init (CPU, GPU first-ever and repeat, Gemma 3 1B CPU when placed), prefill 1000/4000/7000, kv_reuse with and without prefillPrefaceOnInit, rf_matrix (8 features, ON/OFF, adversarial prompts)"
  - "TrialStages: screen per cell with seeded items, CellSelector winner, confirm (all distinct items then the forced subset), sustained (>=60 trials over >=120 s), exit_reasons, in-app cooldown, per-block thermal, sb early exits"
  - "LadderRules: pure early-exit, disposition and planning rules; StateStore; AppFiles; Probes and AndroidProbes; PssSampler"
affects: [13-08, 13-09, 13-10, 13-11]

actuals:
  tokens: 34900
  tasks: 3
  commits: 6

plan_head_before: 1299e926955bf812a9f7cacfcd1e4b4a9baf5918
commits: 6

tech-stack:
  added: []
  patterns:
    - "One stage per process start; the ladder remembers only StateStore files and the append-only evidence files between starts"
    - "Every stage wrapped so a throw becomes STAGE result=error reason=<stable code>, cancellation writes reason=cancelled before rethrowing"
    - "Probes seam (PSS, thermal, exit reasons, device facts, monotonic clock, pause) so the whole ladder is JVM-tested with a fake clock"
    - "One engine at a time (the model is memory-mapped): open, measure, close; TrialRunner per cell so a cell's backend is closed before the next loads"
    - "Early exit to red as pure rules (init_failed_all, sb_prefill_bound, sb_fixture_absent) re-derivable from earlier stages' evidence"

key-files:
  created:
    - spike-ondevice/src/main/kotlin/io/github/ygaray/voiceactionengine/spike/SpikeActivity.kt
    - spike-ondevice/src/main/kotlin/io/github/ygaray/voiceactionengine/spike/evidence/EvidenceLog.kt
    - spike-ondevice/src/main/kotlin/io/github/ygaray/voiceactionengine/spike/ladder/Ladder.kt
    - spike-ondevice/src/main/kotlin/io/github/ygaray/voiceactionengine/spike/ladder/StateStore.kt
    - spike-ondevice/src/main/kotlin/io/github/ygaray/voiceactionengine/spike/ladder/AppFiles.kt
    - spike-ondevice/src/main/kotlin/io/github/ygaray/voiceactionengine/spike/ladder/LadderRules.kt
    - spike-ondevice/src/main/kotlin/io/github/ygaray/voiceactionengine/spike/ladder/Probes.kt
    - spike-ondevice/src/main/kotlin/io/github/ygaray/voiceactionengine/spike/ladder/AndroidProbes.kt
    - spike-ondevice/src/main/kotlin/io/github/ygaray/voiceactionengine/spike/ladder/EngineStages.kt
    - spike-ondevice/src/main/kotlin/io/github/ygaray/voiceactionengine/spike/ladder/RfProbes.kt
    - spike-ondevice/src/main/kotlin/io/github/ygaray/voiceactionengine/spike/ladder/TrialStages.kt
    - spike-ondevice/src/test/kotlin/io/github/ygaray/voiceactionengine/spike/LadderTest.kt
    - spike-ondevice/src/test/kotlin/io/github/ygaray/voiceactionengine/spike/LadderRulesTest.kt
    - spike-ondevice/src/test/kotlin/io/github/ygaray/voiceactionengine/spike/StageParityTest.kt
  modified:
    - spike-ondevice/src/main/AndroidManifest.xml
    - spike-ondevice/src/main/kotlin/io/github/ygaray/voiceactionengine/spike/backend/LlmBackend.kt
    - spike-ondevice/src/main/kotlin/io/github/ygaray/voiceactionengine/spike/backend/LiteRtBackend.kt

key-decisions:
  - "Only a measured not_reused KV disposition may end the sb envelope early (sb_prefill_bound); unobservable or missing evidence proceeds and measures"
  - "kv_reuse disposition: second prefill tokens or TTFT at most 50% of the first is reused; every measured pair within +-20% is not_reused; anything else (or no facts) is unobservable"
  - "BackendRequest gained an optional engineFlag so rf_matrix can run the flat probe with the constraint ON and the engine's constrained-decoding flag OFF; constrained_flag=1 means the flag was needed"
  - "A screen where no cell can start an engine is STAGE error (planned stays whole), never a quiet done 0/0"
  - "The sustained run is cut off at 5000 trials or 15 minutes so it cannot run away; a cut-off short of 60 trials or 120 s reads incomplete in the verdict"
  - "SB stages exit with the stable code of an invalid private fixture (SbState.Invalid) the same way as sb_fixture_absent, so the reason is visible in the verdict"

requirements-completed: []
requirements-advanced: [SPIKE-01]

coverage:
  - id: D1
    description: "The activity is exported, keeps the screen on, reads only a closed stage extra (unknown or missing emits nothing and finishes), and the runner's STAGES list equals the Stage wires in order; EvidenceLog is the only android.util.Log user and takes SpikeLine only; the manifest has one exported activity and no permission"
    requirement: SPIKE-01
    verification:
      - kind: other
        ref: "StageParityTest (2), LadderTest fileSink and stateStore cases, grep: exported=1, uses-permission=0, android.util.Log only in EvidenceLog.kt"
        status: pass
    human_judgment: false
  - id: D2
    description: "Every stage ends with exactly one VAE_SPIKE_STAGE line; a throwing stage ends as result=error with a stable code and no message text; a missing model or a screen with no startable cell is an error code, not a throw"
    requirement: SPIKE-01
    verification:
      - kind: other
        ref: "LadderTest prepareThenPreflightThenScreen..., aStageThatThrows..., aMissingModel... (MemorySink.stageLine asserts exactly one STAGE line per stage in every case)"
        status: pass
    human_judgment: false
  - id: D3
    description: "The D-10 rows are measured with defined dispositions: kv_reuse (reused, not_reused, unobservable), rf_matrix per feature with ON and OFF over five adversarial prompts (enforced, ignored, unproven, native_error) plus whether the engine flag is needed, and GPU first-ever and repeat init (gpu_ok, gpu_init_failed:<code>, gpu_slower)"
    requirement: SPIKE-01
    verification:
      - kind: other
        ref: "LadderRulesTest (12 rule cases), LadderTest init/prefill/kv_reuse/rf_matrix cases over recording fake backends (85 rf requests, 4 kv requests, GPU file then generic fallback)"
        status: pass
    human_judgment: false
  - id: D4
    description: "Screen picks 20 distinct seeded items covering EN, ES and negatives proportionally, the same for every cell; confirm runs every distinct item once then the 20-item forced subset on the stored winner; sustained runs at least 60 trials over at least 120 s with thermal every 10 s; a Gemma 3 1B cell never becomes a winner; a failed trial is counted and never re-run"
    requirement: SPIKE-01
    verification:
      - kind: other
        ref: "LadderRulesTest planning cases (screen cells 8/4, 20 items 8/8/2/2, confirm 110 of 90+20, sustained bound), LadderTest screen/confirm/sustained/g3/no-rerun cases"
        status: pass
    human_judgment: false
  - id: D5
    description: "Early exit to red: init_failed_all after a failed init on every E2B backend (and for every later stage), sb_prefill_bound for all sb stages when the extrapolated prefill is over 2x the warm p95 bar with kv_reuse not reused, sb_fixture_absent for an absent fixture; each with planned=0"
    requirement: SPIKE-01
    verification:
      - kind: other
        ref: "LadderRulesTest init and sb bound cases (incl. the exactly-2x boundary), LadderTest anAbsentSb..., aPrefillBoundSb..., everyEngineStageAfterInit..., everyTrialStageExits..."
        status: pass
    human_judgment: false
  - id: D6
    description: "The ladder's line shapes are the verdict reader's contract: an end-to-end fake run from prepare to exit_reasons, evaluated by VerdictRules, has no unmeasured metric, no inconsistent winner and no incomplete stage; the green fixture still prints two verdict=green lines and the 82-scenario device guard still passes"
    requirement: SPIKE-01
    verification:
      - kind: other
        ref: "LadderTest theLadderEvidenceIsReadByTheVerdictCode...; scripts/verify-spike-verdict.sh --print green fixture (2 green lines); scripts/verify-spike-device-guard.sh -> SPIKE DEVICE GUARD OK scenarios=82"
        status: pass
    human_judgment: false
  - id: D7
    description: "Whether the real activity starts from am start, whether AndroidProbes (Debug.getMemoryInfo, getCurrentThermalStatus, getHistoricalProcessExitReasons, StatFs) and LiteRtBackend behave as assumed on the TESTER, and the real numbers, are judged only by the device run"
    requirement: SPIKE-01
    human_judgment: true
    rationale: "Framework-bound and native code cannot run on the JVM; the first real exercise is plan 13-08 under a granted window. The window is still pending, so no device or adb was touched"

duration: 22min
completed: 2026-10-06
status: complete
---

# Phase 13 Plan 07: Measurement ladder Summary

**The on-device ladder is built and JVM-proven: one stage per process start, 13 stages in early-exit order, every stage ending with exactly one closed-grammar STAGE line, the D-10 rows and every D-07 metric measured as 13-THRESHOLDS.md defines them, and nothing but SpikeLine values leaving the app.**

## Performance

- **Duration:** 22 min (01:22Z to 01:43Z)
- **Tasks:** 3 (1 tracer, 2 TDD)
- **Commits:** 6 (measured from the plan ledger)
- **Files:** 17 in the repository (14 created, 3 modified), about 2,950 added lines (tests about 1,230)

## Accomplishments

- **Tracer (D-04).** `SpikeActivity` (exported, `FLAG_KEEP_SCREEN_ON`, closed `stage` extra only), `EvidenceLog` (sole `android.util.Log` user, append-only `files/evidence/<stage>.txt`, one unbuffered write per line so a process death keeps every finished line), `StateStore`, `AppFiles`, `Probes`/`AndroidProbes` and a `Ladder` that ran prepare, preflight and a minimal screen. `StageParityTest` fails if the runner's `STAGES` list and the `Stage` wires ever differ, or if the runner file cannot be found. The tracer gate re-ran green before expansion.
- **Engine stages (D-10).** init measures E2B on CPU and GPU (empty kernel cache for the first-ever GPU init, GPU file first then the generic file on the GPU, a repeat init, `gpu_ok` or `gpu_init_failed:<code>`), plus Gemma 3 1B on CPU when Yahir placed it. prefill measures 1000, 4000 and 7000 prompt tokens per initialized backend with the SB-sized context and refines the GPU row to `gpu_slower`. kv_reuse runs two identical-preface conversations with and without `prefillPrefaceOnInit` (SB preface when loaded, else the small envelope padded to about 7000 tokens, labeled `padded_small`). rf_matrix probes flat, required, enum, nested, array, anyof, bounds and addl_false with the constraint ON and OFF over five adversarial prompts each. Every engine stage writes one MEM line (250 ms sampling) and THERMAL at its start and end.
- **Trial stages (D-04, D-05, D-06).** screen runs the same 20 seeded, proportionally-bucketed items on every cell (E2B and, on small only, Gemma 3 1B across CPU/GPU and Route A/B), with an in-app cooldown to at most `light` (capped at 5 min) before each cell, a MEM line per cell and a THERMAL line per 10-trial block, then `CellSelector` picks and stores the E2B winner (a Gemma 3 1B cell can never win). confirm runs every distinct gold item once on the stored winner in a fresh process (so `INIT cold=process` is the cold row), then the 20-item forced subset on a separate `.forced` cell. sustained cycles the items until at least 60 trials and 120 s with THERMAL every 10 s on its own stage. exit_reasons reports `ApplicationExitInfo` counts since prepare.
- **Early exit to red (D-07).** `init_failed_all` ends init and every later stage; `sb_fixture_absent`, an invalid private fixture's own code, and `sb_prefill_bound` end screen_sb, confirm_sb and sustained_sb with `planned=0`. `sb_prefill_bound` is strictly over 2x the warm p95 bar at the best measured prefill speed and only on a measured `not_reused`.
- **Contract with the verdict reader.** An end-to-end fake run from prepare to exit_reasons, evaluated by `VerdictRules`, has every gating metric measured, the stored winner recomputes, and no stage reads incomplete. `scripts/verify-spike-verdict.sh --print` on the green fixture still prints two `verdict=green reasons=none` lines.

## Task Commits

1. **Task 1 (tracer): stage host, evidence sink, tracer ladder** - `3c74086` (feat)
2. **Task 2 RED: rules and engine-stage tests (22 of 31 failing as intended)** - `c08eca8` (test)
3. **Task 2 GREEN: engine stages, rf probes, rules** - `9c6b54b` (feat)
4. **Task 3 RED: trial-stage tests (22 of 53 failing as intended)** - `6700ca2` (test)
5. **Task 3 GREEN: trial stages and full dispatch** - `c1e9957` (feat)
6. **Ladder-to-verdict contract test and the sustained cap** - `824eb5c` (test)

## Decisions Made

See `key-decisions`. Two worth knowing for 13-08: (1) the prefill, kv_reuse and rf_matrix rows run one engine at a time on the first working E2B backend (CPU preferred), so the kv_reuse and rf rows are CPU readings when the CPU initialized; (2) Gemma 3 1B is only ever measured on the small envelope, never decides anything, and is absent from every stage when its file was not placed (the grant records `skipped_gated`).

## Deviations from Plan

**1. [Rule 2 - Missing critical functionality] The plan's way of learning whether the engine flag is needed cannot answer the question**
- **Found during:** Task 2 (design of rf_matrix)
- **Issue:** The plan says to record `constrained_flag` by "repeating the flat probe with the flag set when the first ON run was ignored". 13-04's backend already sets `enableConversationConstrainedDecoding` on every ON run, so the first ON run is never flagless and the repeat tells nothing; the row's own question (is the flag needed?) would always read 1.
- **Fix:** `BackendRequest.engineFlag` (optional, default null follows `constraintOn`, so every existing call is unchanged) and a one-line override in `LiteRtBackend`. The flat feature also runs the constraint ON with the flag explicitly OFF (`constraint=on_noflag` line); `constrained_flag=1` when the ON arm was enforced and the flagless arm was not, `0` when both were, `na` when the ON arm was not enforced. The `on_noflag` value never matches the verdict's `constraint=on` filter.
- **Files modified:** `backend/LlmBackend.kt`, `backend/LiteRtBackend.kt`, `ladder/EngineStages.kt`
- **Verification:** `LadderTest.rfMatrixRecordsWhetherTheEngineFlagWasNeeded`; `assembleDebug` compiles the override
- **Commits:** `c08eca8`, `9c6b54b`

**2. [Rule 1 - Bug] A screen with no startable engine would have read as a quiet `done 0/0`**
- **Found during:** Task 3 (the Task 1 missing-model test failed against the full screen)
- **Issue:** When every cell's engine failed to load, the full screen reduced `planned` per failed cell and ended `done trials=0 planned=0 winner=none`, which looks complete.
- **Fix:** if no cell started, the stage is `result=error` with the last init code and `planned` kept whole.
- **Files modified:** `ladder/TrialStages.kt`, `LadderTest.kt`
- **Commit:** `c1e9957`

**3. [Test-only bug] The fake model read the transcript from the wrong place**
- **Found during:** Task 3
- **Issue:** The engine's user turn is a date-time line, a blank line, then the transcript, so a fake that matched on the raw user text never saw its item markers.
- **Fix:** `transcriptOf(request)` in the test helpers. No production change.
- **Commit:** `c1e9957`

**4. [Plan-shape notes, no behavior change]**
- `AndroidProbes` was written in Task 1 (not Task 2) because the activity cannot be built without a `Probes` implementation. Task 2's acceptance greps (`getHistoricalProcessExitReasons`, `totalPss`) hold from that commit.
- Extra files beyond the plan's list: `ladder/AppFiles.kt` (directories and model file lookup) and `ladder/RfProbes.kt` (the eight feature probes). `Ladder` takes one `LadderEnv` value instead of a flat parameter list.
- Task 1's acceptance (`screen_small` with three TRIAL lines and `done trials=3 planned=3`) held at commit `3c74086`; Task 3 replaced the tracer's minimal screen with the full per-cell screen, so the same test now asserts two CPU cells of three items (6 trials) when the GPU init failed.
- `PssSampler.around` returns the block's value and exposes `reading`, so a stage that threw still reports the memory it reached.

**5. [Judgment calls]**
- A KV pair that is neither at most half nor within +-20% reads `unobservable` rather than guessing.
- `gpu_init_failed:<code>` can read `gpu_init_failed:gpu_init_failed` because the real backend's code for a GPU load failure is that word; it is reported as measured, not renamed.
- Sustained is cut off at 5000 trials or 15 minutes (raised from 1000 after the contract test showed a very fast fake model could not reach 120 s inside 1000 trials).

**Total deviations:** 2 auto-fixed (1 Rule 2, 1 Rule 1) plus 1 test-only fix, 3 groups of plan-shape and judgment notes. **Impact:** all within the plan's intent; the only production API change is one optional field.

## Issues Encountered

None blocking. The Gradle recipe (single invocation, no daemon, in-process compiler, `workers.max=2`) finished every run in 15 to 25 seconds; no `./gradlew --stop`. The window grant still reads `grant: pending` and no device or adb command was run.

## Authentication Gates

None.

## Verification

- `:spike-ondevice:testDebugUnitTest` and `:spike-ondevice:assembleDebug`: 216 tests, 0 failures, 1 skipped (`VerdictReproductionTest`, skipped by design without an evidence directory); `LadderTest` 36, `LadderRulesTest` 18 (24 assertions in the rule cases of Task 2 alone), `StageParityTest` 2.
- Task 1 acceptance: `android:exported="true"` count 1, `uses-permission` count 0, `grep -rl 'android.util.Log' spike-ondevice/src/main/kotlin` lists only `EvidenceLog.kt`.
- Task 2 acceptance: `getHistoricalProcessExitReasons` and `totalPss` each appear in `AndroidProbes.kt`; `LadderRulesTest` covers every behavior row.
- Task 3 acceptance: `grep -c not_implemented Ladder.kt` prints 0; `scripts/verify-spike-verdict.sh --print` on `evidence/green` prints two `verdict=green reasons=none` lines; `scripts/verify-spike-device-guard.sh` prints `SPIKE DEVICE GUARD OK scenarios=82`.
- Plan `<verification>`: all of the above plus `scripts/verify-repo-hygiene.sh` printing `HYGIENE OK`; the debug APK listing has no SB fixture or label file; `git diff --stat -- core/` is empty.
- Not run, by design: any device, adb or TESTER step (behavioral and on-device verification belongs to Gate-1 and the 13-08 window).

## Next Phase Readiness

- 13-08 can run the ladder under a granted window: `window-start`, `preflight`, `build-install`, `run prepare` (creates the evidence, private, state, engine-cache and external models directories and stamps `prepare_epoch_ms`), `push-model`, `push-private`, then the stages in `STAGES` order. Use `run init` first: its early exit and its state are what the later stages read. `pull-evidence <stage>` per stage, and `run exit_reasons` last so the process-death count covers the whole window.
- The app contracts 13-06 relied on hold: `.SpikeActivity` accepts `--es stage <wire>`, evidence is at `files/evidence/<stage>.txt` with a `VAE_SPIKE_STAGE stage=<s> result=...` line per finished stage, the models directory is created by `prepare`, and the private files are read from `files/private/sb-fixture.json` and `sb-gold.json`.
- Things the first real run will show: whether `Debug.getMemoryInfo` PSS stays under the 2,000 MB bar with a 7k context, whether `ExperimentalFlags.enableBenchmark` yields the prefill and TTFT counters the kv_reuse and prefill rows read (a null counter is recorded as `na`, and the row reads `unobservable`), and whether the exit-reason words map as expected. The SB tool count (19 at this pin) is reported by preflight as `sb_tool_count`.

## Self-Check: PASSED

- Created files present: all 11 main and 3 test files listed in `key-files.created` exist.
- Commits present: `3c74086`, `c08eca8`, `9c6b54b`, `6700ca2`, `c1e9957`, `824eb5c`; `git rev-list --count` from the ledger base measures 6.
