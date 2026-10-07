# Phase 19 Gate-1 runbook (VER-06, D-13): one window, one install

Audience: the plan 19-07 device driver only. One device tester at a time. This file was written at the start of Task 2,
before any device step, and the window is driven from it.
Target: the TESTER `yahirs-s22-ultra-2`, USB serial `R5CT10XNKQN`, SM-S908U, Android 15. The personal phone is never touched.
App: `:sample` (debug build, never published), driven through its real UI by resource id.

## 0. Read first

1. `19-LIVE-LEG-DECISION.md`: `decision: approved`, `relayed_by`, `date`, `window: open <UTC>`. Ceiling 16 requests / USD 0.05.
2. `~/.claude/context/devices/common.md`, `test-android.md`, `~/.claude/context/workflows/test-keys.md`.
3. Phase 10 runbook (`.planning/milestones/v1.0-phases/10-sample-harness-gate-1-docs/GATE1-RUNBOOK.md`) for the UI lessons.

## 1. Hard rules

- `adb -s R5CT10XNKQN` only. No airplane mode. No other device, ever (the wireless entry is not used for driving).
- Host steps go only through `scripts/run-sample-gate1.sh <subcommand>`; key every step off its LAST line
  (`SAMPLE_GATE1: <OK|FAIL|INFRA|ERROR> sub=...`) and the exit code. The runner refuses a foreign `ANDROID_SERIAL`, so run it
  as `env -u BASH_ENV -u ANDROID_SERIAL scripts/run-sample-gate1.sh ...`.
- Low-memory build: `GRADLE_OPTS="-Dorg.gradle.daemon=false -Dorg.gradle.workers.max=2 -Dorg.gradle.parallel=false
  -Dkotlin.compiler.execution.strategy=in-process -Dorg.gradle.jvmargs=-Xmx1536m"` exported before `build-install`. One
  Gradle job at a time. Never `./gradlew --stop`.
- Never read or print a key. Keys move only through `push-keys` (file reference). Never type a real key.
- uiautomator dumps and screenshots stay in the session scratchpad. Nothing but `evidence/gate1-*.txt` is committed.
- One install for the whole window (uninstall wipes the in-app budget counter). Every leg's first run is a UI press
  (`trigger=ui`); autorun is refused before a first UI run by design. No leg is pressed twice unless the stop rules allow it.
- Never press: `run_ver02`, `run_smoke_*`, `run_multi_*`, `run_demo_*` (the v1.0 legs, D-01).

## 2. Driving by resource id

Dump: `adb -s R5CT10XNKQN shell uiautomator dump /sdcard/vae-ui.xml` then `adb -s R5CT10XNKQN pull` into the scratchpad,
find the node by `resource-id`, `input tap` the centre of its `bounds`. Scroll the page before tapping controls that sit
under the gesture bar. Remove the on-device dump file afterwards.

| Tag | What |
|-----|------|
| `run_<leg>` | button; legs here: `grammar_offline`, `undo_all`, `plan_live`, `router_live`, `responses_probe` |
| `status_<leg>` | status word (IDLE, RUNNING, PASS, FAIL, ... , AWAITING_UNDO for the undo leg) then ` reason=<code>` |
| `undo_all` | the second-press button, shown only while the undo leg waits |
| `undo_label` | text of the button, must read `Undo all (3)` before the second press |
| `import_test_keys`, `import_status` | import the pushed keys through :keystore; `anthropic=Ready deleted=true in_datastore=false; ...` |
| `budget_used` | `requests <core>/15 . optional <o>/1 . est USD <x>` |
| `readout`, `failure_banner` | headline and loud failure |

A leg is done when `status_<leg>` leaves RUNNING. The verdict comes from the captured `VAE_VERDICT` line (`capture-save`).

## 3. Order of the window

1. `preflight`. Read-only host checks: `adb devices`, `free -h`, volume stream 3 snapshot.
2. `build-install` (low-memory GRADLE_OPTS). Record `apk_md5`, `head`, `dirty` (must be 0).
3. Offline legs, no key on the device:
   - `capture-start`; press `run_grammar_offline`; poll `status_grammar_offline`; `capture-save grammar_offline`.
     Every `VAE_TRACE leg=grammar_offline` line must carry `provider_turns=0 attempts=0 tripwire_calls=0`.
   - `capture-start`; press `run_undo_all`; poll `status_undo_all` until it reaches awaiting_undo; read `undo_label`
     (must read `Undo all (3)`; record the text); press `undo_all`; poll until the status leaves RUNNING/awaiting;
     `capture-save undo_all`.
   - If either offline leg would spend any API call (a `VAE_BUDGET` above zero, or any request shown in `budget_used`):
     STOP and return `NEEDS_HUMAN live_probe_finding`. Do not spend.
4. `push-keys`; press `import_test_keys`; read `import_status`; `verify-keys-gone`; read `budget_used` (expect 0/15, 0/1).
5. Live legs, in this order, each with `capture-start`, press `run_<leg>`, poll, `capture-save <leg>`:
   `plan_live`, `router_live`, `responses_probe`. Read `budget_used` after the probe.
6. `verify-keys-gone`; `cleanup` (last line `SAMPLE_GATE1: OK sub=cleanup`); restore and read back media stream 3
   (`cmd media_session volume --stream 3 --get`; set with `--set 15` if changed).

## 4. Stop rules

- A FAIL or REFUSED verdict is recorded with the runner's last line and is not re-run past the relayed ceiling. A
  `router_live` non-PASS is a FINDING: no re-run; record the verdict lines and continue to cleanup.
- Never re-run a FAIL caused by the model or the engine. A re-run is allowed only for transient INFRA, inside the ceiling.
- INFRA (device offline, identity mismatch, tester busy): stop, clean up if reachable, report.
- Spend never exceeds 16 requests / USD 0.05 in total. No retries past the ceiling.
- The capture filter rejecting a capture (LEAK SCAN FAIL) writes nothing: stop and report, do not hand-write evidence.

## 5. Report

The final report carries every `VAE_VERDICT` line, the `VAE_BUDGET` line(s), the line
`P19 LIVE SPEND: requests=<total> (plan=<a> router=<b> probe=<c>) est_cost_usd=<x> ceiling=16+0.05`, the window times, the
cleanup line, the verify-keys-gone result, the volume read-back, the build facts and the `undo_label` text.
