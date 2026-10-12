---
phase: 14-localgrammar-bilingual-grammarpack
plan: 09
subsystem: d12-stt-capture-window
status: complete
tags: [d-12, tester-window, stt-capture, androidTest, recognizer-forms, synthetic-tts]

requires:
  - phase: 14-02
    provides: stt-prompts.tsv (88 prompts), guarded runner scripts/run-stt-capture.sh, 14-WINDOW-GRANT.md
  - phase: 14-08
    provides: green JVM phase gate before the device window
provides:
  - sample androidTest SttFormsCaptureTool (opt-in TTS to on-device recognizer capture, never published)
  - 14-WINDOW-GRANT.md relayed then consumed (grant: consumed, closed 2026-10-06T16:25:43Z)
  - core/src/test/resources/grammar/stt-fixtures.tsv, 88 filtered rows, provenance synthetic-tts
affects: [14-10]

requirements-completed: []
requirements-contributed: [GRAM-02]

plan_head_before: 8eb9ce2ff9c6270ba432dc04bbd5b491812edce5
commits: 4

actuals:
  tokens: 9500
  tasks: 3
  commits: 4

key-files:
  created:
    - sample/src/androidTest/kotlin/io/github/ygaray/voiceactionengine/sample/SttFormsCaptureTool.kt
    - sample/src/debug/AndroidManifest.xml
    - core/src/test/resources/grammar/stt-fixtures.tsv
  modified:
    - sample/build.gradle.kts
    - scripts/run-stt-capture.sh
    - scripts/verify-stt-capture-guard.sh
    - core/src/test/resources/grammar/README.md
    - .planning/phases/14-localgrammar-bilingual-grammarpack/14-WINDOW-GRANT.md

key-decisions:
  - "The capture tool uses SpeechRecognizer.createOnDeviceSpeechRecognizer with EXTRA_PREFER_OFFLINE, a segmented session over EXTRA_AUDIO_SOURCE and 16 kHz mono PCM16, the same shape stt-engine proved on this device; the default (network-resolved) recognizer returned empty results for 22 kHz audio"
  - "Prompts and results live directly in the app-owned external files dir, not a subdirectory: an adb mkdir creates a shell-owned 2770 directory the app process cannot read through"
  - "JSONL status follows the runner filter contract (ok | error | tts_unavailable) with an extra code field on error rows"

patterns-established:
  - "Run the capture runner as env -u BASH_ENV -u ANDROID_SERIAL scripts/run-stt-capture.sh on this host (see Deviations 5)"

duration: 55min
completed: 2026-10-06
---

# Phase 14 Plan 09: D-12 TESTER recognizer capture Summary

**Opt-in TTS-to-on-device-recognizer androidTest tool, run once on the TESTER under the relayed D-12 window: 88 of 88 prompts recognized and committed as filtered `stt-fixtures.tsv` (synthetic-tts), device cleaned, window closed (`grant: consumed`).**

## Performance

- Duration: about 55 min wall clock for the plan; the device window itself was 6 min 49 s of the 3600 s time-box.
- Tasks: 3 (Task 2 satisfied by the relayed runtime authorization, no checkpoint returned)
- Commits: 4 measured (`git rev-list --count 8eb9ce2..HEAD` before this summary commit)

## Accomplishments

- Task 1: `SttFormsCaptureTool` (androidTest only, behind `-e captureSttForms true`), the `:sample` instrumentation runner and two test dependencies already in the catalog, and a debug-only manifest (RECORD_AUDIO plus service queries). Main sources untouched (`git diff f0e5e69 HEAD -- sample/src/main` empty). `scripts/run-stt-capture.sh build`, `verify-stt-capture-guard.sh` (54 scenarios) and `verify-sample-device-guard.sh` (33 scenarios) all OK.
- Task 2: relayed answer recorded verbatim (below) and in `14-WINDOW-GRANT.md` (`grant: open`, `relayed_by`, `date`, `timebox_s 3600`), committed alone before the first device subcommand. Host-side read-only `adb devices` listed `R5CT10XNKQN device` first.
- Task 3: preflight, install, push-prompts, run, pull, filter, cleanup through the runner; fixtures and README capture record committed; grant set to consumed.

## Relayed answer (verbatim, Task 2)

Master relay of 2026-10-06 from orchestrator yahir-gsd-control-plane-3b (recorded in 14-CONTEXT.md RT-01 and commit 65292ec):

> TESTER R5CT10XNKQN is CONFIRMED FREE and granted for 14-09, at most 3600 s, by orchestrator yahir-gsd-control-plane-3b. adb -s stays pinned, no radio or airplane changes. The phone plays TTS aloud, so keep the volume moderate and restore it after.

Task 2 treated as resumed with: "window open 2026-10-06T16:18:47Z timebox_s=3600 relayed_by=orchestrator yahir-gsd-control-plane-3b via the milestone master".

## Window facts

| Fact | Value |
|------|-------|
| Window start (first device call, read-only volume get) | 2026-10-06T16:18:54Z |
| Window end (after cleanup) | 2026-10-06T16:25:43Z |
| Device | R5CT10XNKQN, SM-S908U, SDK 35 (identity proven by the runner), `adb -s` pinned, no radio or airplane change |
| Last line of `scripts/run-stt-capture.sh cleanup` | `STT_CAPTURE: OK sub=cleanup target=R5CT10XNKQN` (preceded by `capture packages removed`; `pm list packages` shows 0 sample packages) |
| Final capture counts (filter) | ok=88 error=0 tts_unavailable=0 dropped=1 (the header line) |
| Media volume (stream 3) | original 15 of 15, set to 6 (40 percent) for the run, restored to 15 and verified by read-back (`volume is 15 in range [0..15]`) |
| Personal phone 100.126.94.47 | never touched |
| Fixtures | 88 rows, header `id lang text expect recognized provenance`, all `synthetic-tts`; no wav or jsonl tracked |

Capture attempts inside the window (only the third produced committed data):
1. Run 1: instrumentation failed (`prompt list missing`, see Deviations 3). No output.
2. Run 2: 87 empty results and 1 error 7 from the default recognizer, all off the filter's status contract; filter FAIL, nothing committed (Deviations 4).
3. Run 3: on-device recognizer, 16 kHz, segmented session: 88 ok.

## Task Commits

1. Task 1: `31c98b9` feat(14-09): opt-in TTS-to-recognizer capture tool as :sample androidTest
2. Task 2 (grant recorded alone, before any device subcommand): `7e66d93` docs(14-09): record the relayed TESTER window grant
3. Task 3 (in-window fix): `1f939fd` fix(14-09): capture tool reads prompts from the app-owned files dir and feeds the on-device recognizer 16 kHz audio
4. Task 3 (fixtures and close): `2ee4735` feat(14-09): commit filtered recognizer fixtures, close the TESTER window (grant: consumed)

## Deviations from Plan

1. [Rule 1 - contract mismatch] The plan names `stt-capture/results.jsonl`; the runner (14-02) pulls `<files>/stt-forms.jsonl`. The tool follows the runner.
2. [Rule 3 - blocking] The debug manifest also declares `<queries>` for TTS_SERVICE and RecognitionService (Android 11+ package visibility); the plan said RECORD_AUDIO only. Debug variant only.
3. [Rule 1 - bug in 14-02 runner] `push-prompts` made `files/stt-capture` with adb `mkdir -p`; the directory is shell-owned (2770, ext_data_rw) and the app process reported `prompt list missing`. The runner now pushes straight into the app-owned files dir (`REMOTE_DIR=.../files`), checks it exists, and cleanup removes the two files plus any legacy `stt-capture` directory; the guard verifier's fake adb and expected paths were updated (still 54 scenarios OK). Files outside the plan's `files_modified`: `scripts/run-stt-capture.sh`, `scripts/verify-stt-capture-guard.sh`.
4. [Rule 1 - bug in Task 1 tool] The first tool version used the default recognizer with the TTS engine's native 22 kHz audio and emitted `error:<code>` statuses; every prompt came back empty and the statuses were off the runner's `ok | error | tts_unavailable` contract. Fixed within the window (on-device recognizer, offline preferred, segmented session, 16 kHz resample with lead and tail silence, `status=error` plus `code`). The tool was rebuilt with the runner's `build` subcommand and the guards re-run before the reinstall.
5. [Environment] The shell's `BASH_ENV=/home/yahir/.config/test-device.env` exports `ANDROID_SERIAL=100.118.21.106:1496` into every non-interactive bash, including the runner, which correctly refuses any non-USB serial (`reason=refused_serial`). I invoked the runner as `env -u BASH_ENV -u ANDROID_SERIAL scripts/run-stt-capture.sh ...` (no change to what it targets). Worth a control-plane note: the guarded runners and that env file conflict; the next executor will hit the same refusal.
6. [Scope note] Beyond the runner I issued only read-only or authorized adb calls on R5CT10XNKQN: `cmd media_session volume --stream 3 --get/--set` (volume), `logcat -d` and `ls -la` of the app's own data directory to diagnose my own tool's failure in Deviations 3 and 4. No behavioral verdict was taken from the device; the committed result is the pulled, filtered recognizer text.

Total: 4 auto-fixed (Rules 1 and 3), 2 environment or scope notes. Impact: none on later plans; the runner now matches how the app reads the prompts.

## Findings for plan 14-10

`stt-fixtures.tsv` is the evidence: 67 of 88 recognized rows contain a digit (`page 12`, `2.5`, Spanish `2,5`), others come back as words (`dos y medio`), sentence-initial capitalization is kept, and the Spanish TTS audio sometimes mishears carrier words (`pon` as `con`, `cero` as `serio`). Synthetic TTS only: evidence the recognizer can emit a form, not that people trigger it (README caveats).

## Deferred obligations

None. The capture produced usable output (ok=88), so the D-12 obligation is discharged in this plan; the A12 deferral path was not used.

## Self-Check: PASSED

- Files exist: `SttFormsCaptureTool.kt`, `sample/src/debug/AndroidManifest.xml`, `stt-fixtures.tsv`, this summary.
- Commits exist: `31c98b9`, `7e66d93`, `1f939fd`, `2ee4735`.
- Plan Task 3 verify command: VERIFY-OK (grant not open, consumed, fixture header exact, 88 `synthetic-tts` rows, no tracked wav or jsonl).
