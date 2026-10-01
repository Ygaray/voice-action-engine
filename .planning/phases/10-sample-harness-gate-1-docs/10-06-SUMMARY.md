---
phase: 10-sample-harness-gate-1-docs
plan: 06
subsystem: sample
tags: [ui, compose, viewmodel, resource-ids, outcome-rendering, keys, ver-01, ver-04]
status: complete
requires: [10-05]
provides:
  - "UiTags: the stable testTag vocabulary the runbook and the Gate-1 tester drive the screen by"
  - "SampleViewModel: one leg at a time, verdict to status word, key save/delete/import, clarification follow-up, warm-window tick"
  - "OutcomeText / HeaderText: pure rendering rules (partial, failure banner with keystore action, clarification options, fixture banner)"
  - "AppGraph: the single production wiring of every sample dependency"
  - "DebugTools.autorunLeg: debug-only rerun intent extra (release answers null)"
affects: [10-07, 10-08, 10-10]
tech-stack:
  added: []
  patterns: ["pure rendering functions tested without a UI runtime", "ViewModel over the same LegRunner the host tests drive", "typed key text kept out of UiState so no printout can carry it"]
key-files:
  created:
    - sample/src/main/kotlin/io/github/ygaray/voiceactionengine/sample/SampleViewModel.kt
    - sample/src/main/kotlin/io/github/ygaray/voiceactionengine/sample/AppGraph.kt
    - sample/src/main/kotlin/io/github/ygaray/voiceactionengine/sample/ui/SampleScreen.kt
    - sample/src/main/kotlin/io/github/ygaray/voiceactionengine/sample/ui/UiTags.kt
    - sample/src/main/kotlin/io/github/ygaray/voiceactionengine/sample/ui/OutcomeText.kt
    - sample/src/main/kotlin/io/github/ygaray/voiceactionengine/sample/ui/HeaderText.kt
    - sample/src/test/kotlin/io/github/ygaray/voiceactionengine/sample/SampleViewModelTest.kt
    - sample/src/test/kotlin/io/github/ygaray/voiceactionengine/sample/OutcomeTextTest.kt
    - sample/src/test/kotlin/io/github/ygaray/voiceactionengine/sample/UiTagsTest.kt
  modified:
    - sample/src/main/kotlin/io/github/ygaray/voiceactionengine/sample/MainActivity.kt
    - sample/src/debug/kotlin/io/github/ygaray/voiceactionengine/sample/DebugTools.kt
    - sample/src/release/kotlin/io/github/ygaray/voiceactionengine/sample/DebugTools.kt
key-decisions:
  - "The failure_banner carries the keystore action (FAILED: credential_unreadable - re-enter key (decrypt_failed)); the readout headline stays a short Failed"
  - "Partial is checked before clarification and Done, so a partial outcome can never read as success even when it also carries a terminal call"
  - "Typed key text lives in its own StateFlow (keyFields), not in UiState, and UiState/KeyView toString print no wording"
  - "The warm-window countdown is driven by a LaunchedEffect in the screen (alive only while the window is open) calling tick(), not by a ViewModel ticker; a ViewModel ticker would never go idle under a manual test clock"
  - "est USD in the header is the sum of the est_usd values of the VAE_BUDGET lines seen since launch (CostTallySink wraps the Logcat sink), so no change to LegRunner was needed"
requirements-completed: [VER-01, VER-02, VER-03, VER-04]
commits: 3
plan_head_before: d4e76d9e2600347581731f0e5f0a242cc6c48cde
actuals:
  tokens: 17000
  tasks: 3
  commits: 3
duration: 70 min
completed: 2026-10-01
---

# Phase 10 Plan 06: Gate-1 Screen Summary

The Gate-1 tester now has the real UI to drive: one scrollable Compose screen whose every control and readout has a stable resource-id, loud red failures, outcomes rendered the way the docs will prescribe (partial as "couldn't finish", clarification as pressable options with a parentRunId follow-up), and the production wiring of everything built in 10-01..10-05. No device, adb, key or network was used.

## What was built

- **Task 1 (tracer, d8d4aa7):** `UiTags`, `SampleViewModel` (`runLeg`, one leg at a time), `AppGraph` (fixture loader, keystore vault and credential source, `FileBudgetStore`, `AttemptTap`, `ProviderFactory.create`, in-memory commit sink, `SampleEngine`, `LegRunner`, debug key importer), a first `SampleScreen` (legs list under `run_<leg>` / `status_<leg>`) and `MainActivity` wiring. A leg press goes ViewModel -> LegRunner -> verdict -> status text.
- **Task 2 (5701f02):** `OutcomeText` (+ `Tone`, `OutcomeView`), the readout section (`readout`, `failure_banner`, `clarify_question`, `clarify_option_<id>`), and `SampleViewModel.chooseOption` which starts the linked follow-up through `LegRunner.followUp`.
- **Task 3 (b8932d8):** `HeaderText`, header (`fixture_state`, `okhttp_version`, `budget_used`, `warm_window`), keys card (`key_state/field/save/delete_<p>`), debug-only `import_test_keys` / `import_status`, startup `VAE_ENV` and loud `VAE_FIXTURE`, key save/delete/import in the ViewModel, `DebugTools.autorunLeg` (debug reads `vae_autorun`, release returns null), and `MainActivity` handling of the extra in `onCreate` (first start only) and `onNewIntent`.

## Final tag list (for the runbook; copied from UiTags)

| Tag | What |
|-----|------|
| `title` | screen title |
| `okhttp_version` | "okhttp <runtime version>" |
| `fixture_state` | fixture banner: green `Fixture OK sha=ebd3ef4a…af4ed3e tools=<n> source=<files\|asset>`; red `FIXTURE ABSENT - push the LE-1 fixture (GATE1-RUNBOOK)`, `FIXTURE SHA MISMATCH <8hex> - do not use; ask the orchestrator to regenerate`, `FIXTURE MALFORMED <code>` |
| `budget_used` | `requests <core>/33 · optional <o>/1 · est USD <x>` |
| `warm_window` | `Anthropic warm window: wait <s> s` (present only while open, amber) |
| `run_<leg>` | button per leg: `run_ver02`, `run_smoke_anthropic`, `run_smoke_openai`, `run_smoke_openrouter`, `run_multi_openai`, `run_multi_openrouter`, `run_responses_probe`, `run_demo_clarify`, `run_demo_partial` |
| `status_<leg>` | text per leg: one of IDLE, RUNNING, PASS, FAIL, WARM, OUT_OF_BAND, INCONCLUSIVE, CAPTURED, REFUSED, then ` reason=<code>` when there is one |
| `key_state_<p>` | `<p>: <KeyUx label>`; `p` is `anthropic`, `openai`, `openrouter`; Ready shows `ends in <last4>` on screen only |
| `key_field_<p>` | masked single-line field |
| `key_save_<p>` | Save button (field is cleared at once) |
| `key_delete_<p>` | Delete button |
| `import_test_keys` | debug build only |
| `import_status` | debug build only: `anthropic=Ready deleted=true in_datastore=false; ...` |
| `readout` | the last leg's headline |
| `failure_banner` | red `FAILED: <code>` (plus the keystore action for an unreadable key) |
| `clarify_question` | the clarification question |
| `clarify_option_<id>` | one button per option (`clarify_option_list-a`, `clarify_option_list-b` in the demo) |

The root sets `testTagsAsResourceId`, so uiautomator exposes each tag as `resource-id`.

## Test counts

New in this plan: `SampleViewModelTest` 11, `OutcomeTextTest` 6, `UiTagsTest` 1 (18 tests, 0 failures). Whole `:sample:testDebugUnitTest`: 117 tests across the sample suites, 0 failures.

## Gate results

| Gate | Result |
|------|--------|
| `:sample:testDebugUnitTest` | green (117) |
| `:sample:compileReleaseKotlin`, `:sample:assembleDebug`, `:sample:assembleRelease`, `:sample:lintDebug` | green |
| `./gradlew check --offline` | green |
| `scripts/verify-repo-hygiene.sh` | HYGIENE OK |
| `git diff --stat d4e76d9 -- core providers keystore` | empty |
| `grep -c vae_autorun` debug / release DebugTools | 2 / 0 |
| `Log.x(` outside `evidence/EvidenceLine.kt` | none |

## Deviations from Plan

**1. [Rule 3 - Blocking] `:sample:testReleaseUnitTest` does not exist** (known since 10-02). Used `:sample:testDebugUnitTest` plus `:sample:compileReleaseKotlin` and `:sample:assembleRelease`. No build file changed.

**2. [Scope detail] One extra file, `ui/HeaderText.kt`.** The plan listed no file for the pure header, key-row and import wording; putting it in its own small object keeps it unit-tested (`theFixtureBannerNamesEveryStateLoudly`) and out of the Compose file. Not in `files_modified`, but inside `ui/`.

**3. [Scope detail] Failure wording moved from headline to banner.** Task 2 first put the keystore action in the readout headline; the plan's UI contract says the `failure_banner` carries it, so Task 3's commit moved it (`FAILED: credential_unreadable - re-enter key (decrypt_failed)`). The Task 2 test was updated with it.

**4. [Scope detail] `estUsd` constructor parameter.** `SampleViewModel` takes a defaulted `estUsd: () -> String` (before `nowSeconds`) so the header can show the running estimate; `AppGraph` supplies `CostTallySink.total`. This avoided touching `LegRunner`.

**Total deviations:** 4 (one Rule 3, three scope details). **Impact:** none on the contract.

## Authentication Gates

None. No device, adb, push-test-key, key file or live provider call was touched (D-01, D-13). Every test runs under `NoNetworkGuard` with obviously fake placeholders (`dummy-value-1234`, `placeholder-abcd`).

## Notes for downstream plans

- The fixture is loaded once at process start and the banner shows that load. After pushing the fixture, the runbook must force-stop and relaunch the app (`am force-stop`, then start), or the screen and runner keep the old state.
- The est USD figure is a sum since launch (per `VAE_BUDGET` line), while the request counts persist across restarts. After a restart est USD starts again at 0.00000; the counts do not.
- The debug autorun is `adb shell am start -n <pkg>/.MainActivity --es vae_autorun <leg wire name>`; it logs `VAE_AUTORUN leg=<leg>` and the verdict line carries `trigger=autorun`. Use it for reruns only; a leg's first Gate-1 run is a UI press (`trigger=ui`).
- `UiTags.all(legs, providers)` lists every static tag, so the runbook can be checked against code.
- A Compose UI test runtime was not added (plan prohibition): the screen itself is proven on the TESTER by the Gate-1 tester, and the logic behind it is covered by `SampleViewModelTest`, `OutcomeTextTest` and `UiTagsTest`.

## Self-Check: PASSED

- All nine created files and three modified files exist on disk.
- Commits d8d4aa7, b8932d8 and 5701f02 present; `commits: 3` measured with `git rev-list --count` against `plan_head_before`.
- No change under core/, providers/, keystore/; no STATE.md or ROADMAP.md edit; nothing under .planning/graphs, .gsd, intel, config.json, state.json staged.
