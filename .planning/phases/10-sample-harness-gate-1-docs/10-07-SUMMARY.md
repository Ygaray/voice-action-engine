---
phase: 10-sample-harness-gate-1-docs
plan: 07
subsystem: scripts
tags: [gate-1, adb, tester-guard, fake-adb, redaction, evidence, ver-01, ver-02, ver-03]
status: complete
requires: [10-06]
provides:
  - "scripts/run-sample-gate1.sh: the only sanctioned host path to the TESTER for Phase 10 (nine subcommands behind one guard)"
  - "scripts/sample-evidence-filter.sh: allow-list filter plus key-shape scan for captured logcat"
  - "scripts/verify-sample-device-guard.sh: offline proof of every refusal, key and redaction path (27 scenarios, fake adb, fake push-test-key)"
affects: [10-08, 10-09, 10-10]
tech-stack:
  added: []
  patterns: ["guard block copied verbatim from the keystore runner, not shared by import", "fake adb that logs argv and answers per scenario", "secret-shaped test tokens assembled from fragments at run time"]
key-files:
  created:
    - scripts/run-sample-gate1.sh
    - scripts/sample-evidence-filter.sh
    - scripts/verify-sample-device-guard.sh
  modified: []
key-decisions:
  - "Host-side checks (approval file, host fixture present, fixture digest) run BEFORE the lock and before any adb call, so a refusal on those paths provably makes zero adb calls"
  - "cold-stamp needs no device and no lock, so it runs before the foreign-serial check and the lock; it fails safe (INFRA stamp_invalid) on an unreadable stamp"
  - "Every device subcommand prints target/model/sdk and the foreground activity once after the identity check (T-10-33), then runs"
  - "The filter's key scan uses a boundary before the sk- shapes so a tool name such as task-... cannot false-positive; credential header words are matched case-insensitively anywhere"
requirements-completed: [VER-01, VER-02, VER-03]
commits: 3
plan_head_before: 22ffa3bc8027435f55915f4be994a52861ab2aab
actuals:
  tokens: 10100
  tasks: 3
  commits: 3
---

# Phase 10 Plan 07: Guarded Gate-1 host runner Summary

One TESTER-only runner (install, fixture push, key push, logcat capture with redaction, cold stamp, plaintext check, cleanup), an allow-list evidence filter, and an offline verifier that proves every refusal and redaction path with a fake adb. No device was touched.

## What was built

| File | Mode | Role |
|------|------|------|
| `scripts/run-sample-gate1.sh` | 100755 | the guarded runner |
| `scripts/sample-evidence-filter.sh` | 100755 | stdin to stdout, closed grammar plus leak scan |
| `scripts/verify-sample-device-guard.sh` | 100755 | 27 fake-adb scenarios |

## Subcommand table (for the runbook)

Usage: `scripts/run-sample-gate1.sh <subcommand> [arg]`. Last line is always `SAMPLE_GATE1: <OK|FAIL|INFRA|ERROR> sub=<subcommand> <key=value ...>`. Exit: 0 OK, 1 FAIL, 2 ERROR, 3 INFRA (offline, busy, warm window), 4 INFRA (identity or refused serial).

| Subcommand | Device? | What it does | Refusals and failure reasons |
|------------|---------|--------------|------------------------------|
| `preflight` | yes | target, model, sdk, foreground activity, `installed=yes\|no` | `tester_offline`, `tester_busy`, `identity_mismatch`, `refused_serial` |
| `build-install` | yes | `./gradlew :sample:assembleDebug --offline -q`; prints `apk_md5`, `head`, `dirty`, `asset_fixture`; `install -r`; best-effort `cmd deviceidle whitelist +pkg`; confirms the package | `build_failed`, `apk_missing`, `install_failed`, INFRA `tester_offline` |
| `push-fixture` | yes | host file `sample/src/debug/assets/sb-a10-fixture.json` digest vs the `FIXTURE_SHA256` constant in `FixtureLoader.kt`; push to `/data/local/tmp/vae-fx-<hex>`; `run-as` write to `files/fixture/`; read-back digest; staging always removed | ERROR `fixture_missing_on_host` ("copy the LE-1 fixture by hand per GATE1-RUNBOOK step A2"), FAIL `fixture_sha_mismatch` ("do not use; ask the orchestrator to regenerate"), `push_failed`, `run_as_failed`, FAIL `fixture_readback_mismatch` |
| `push-keys` | yes | only when `10-LIVE-LEG-DECISION.md` has a column-0 line `decision: approved`; `push-test-key <p> --device <TESTER> --package <pkg>` for anthropic, openai, openrouter | ERROR `live_legs_not_approved`, ERROR `push_key_failed provider=<p>` ("stop: tell Yahir which key file to (re)create, per test-keys.md; do not work around") |
| `capture-start` | yes | `logcat -c` | `logcat_clear_failed` |
| `capture-save <leg>` | yes | `logcat -d -v raw -s VaeSample:I` through the filter; requires a `VAE_VERDICT leg=<leg> ` line; appends header + kept lines to `.planning/phases/10-sample-harness-gate-1-docs/evidence/gate1-<leg>.txt` | FAIL `leak_scan_failed`, FAIL `no_verdict_line` (both write nothing), `logcat_failed` |
| `cold-stamp check\|write` | no | stamp `${XDG_CACHE_HOME:-$HOME/.cache}/vae-gate1/anthropic-agentic.ts`; `check` refuses within 360 s | INFRA `warm_window remaining=<s>`, INFRA `stamp_invalid` |
| `verify-keys-gone` | yes | `run-as pkg ls files/test-keys`; empty or no such dir is OK | FAIL `plaintext_keys_present count=<n>` (count only, never names), ERROR `run_as_failed` |
| `cleanup` | yes | force-stop, `run-as rm -rf files/test-keys files/fixture`, remove deviceidle whitelist, uninstall, fresh `pm list packages` must not list the package; prints `sample package removed` | FAIL `uninstall_failed` |

Legs (exactly `LegId.wire`): `ver02 smoke_anthropic smoke_openai smoke_openrouter multi_openai multi_openrouter responses_probe demo_clarify demo_partial`.

Constants: `TESTER_USB=R5CT10XNKQN`, `TESTER_WIFI=100.118.21.106:1496`, `PERSONAL_IP=100.126.94.47`, `EXPECTED_MODEL=SM-S908U`, `MIN_SDK=35`, lock `${XDG_RUNTIME_DIR:-/tmp}/vae-keystore-tester.lock` (shared with the keystore runner), warm window 360 s. `ADB` and `PUSH_TEST_KEY` can name a binary only; nothing changes the target.

## Verifier scenario list (27)

Task 1 (9): `happy_preflight`, `offline`, `usb_impostor`, `wireless_impostor`, `foreign_android_serial`, `extra_argument`, `unknown_subcommand`, `no_subcommand`, `lock_busy`.
Task 2 (10): `build_install_happy`, `push_fixture_missing`, `push_fixture_mismatch`, `push_fixture_happy`, `push_keys_not_approved`, `push_keys_happy`, `cold_stamp_warm`, `cold_stamp_cold`, `verify_keys_gone_present`, `cleanup_happy`.
Task 3 (8): `filter_keeps_golden`, `filter_drops_free_text`, `filter_rejects_key_shape`, `capture_save_leak`, `capture_save_bad_leg`, `capture_save_no_verdict`, `capture_save_happy`, `leg_list_parity`.
Plus one uncounted run, `capture_start_clears`, so `capture-start` is not untested.

Every scenario also asserts: each logged adb call is `connect 100.118.21.106:1496` or begins `-s R5CT10XNKQN ` / `-s 100.118.21.106:1496 `; no key-shaped token or credential header word on any adb argv, key-helper argv or output; refusal scenarios log no install/uninstall/push/run-as and never reach Gradle. Mutation spot checks (approval gate removed; leak scan removed) both made the verifier fail.

## Task commits

| Task | Commit | Content |
|------|--------|---------|
| 1 (tracer) | e932b68 | guard copied from the keystore runner, preflight, nine refusal scenarios |
| 2 | 84fd1c1 | build-install, push-fixture, push-keys, cold-stamp, verify-keys-gone, cleanup; 19 scenarios |
| 3 | 4856c65 | filter, capture-start/save, redaction negative controls, grammar and leg-list parity; 27 scenarios |

The tracer was re-verified end to end before expansion (verifier green at nine scenarios, Phase 6 guard green and its two scripts byte-identical to the plan base).

## Gate results

| Gate | Result |
|------|--------|
| `scripts/verify-sample-device-guard.sh` | `SAMPLE DEVICE GUARD OK scenarios=27` |
| `scripts/verify-keystore-device-guard.sh` | `DEVICE GUARD OK` |
| `scripts/verify-repo-hygiene.sh` | `HYGIENE OK` |
| filter on the golden evidence file | 11 of 11 lines kept unchanged, dropped=0 |
| `grep -nE 'config/test-keys\|\.key\b'` on the runner | no match |
| `git diff --stat 22ffa3b -- core providers keystore sample` | empty |
| `git diff 22ffa3b -- scripts/run-keystore-instrumented.sh scripts/verify-keystore-device-guard.sh` | empty |
| `./gradlew check --offline` | green |
| `bash -n` on all three scripts | clean (shellcheck is not installed on this host) |

## Deviations from Plan

None that change the contract. Scope details:

1. **Host checks before the lock.** The plan lists the approval, fixture-present and digest checks inside the subcommands; they run in a `host_precheck` step before the lock and before any adb call, which makes "no push on mismatch" and "no key push when not approved" provable as an empty adb log. Same exit codes and reasons.
2. **`capture_start_clears` uncounted scenario** added so the `capture-start` code path runs under the fake; the counted total stays at the plan's 27.
3. **`run-as` calls in `verify_keys_gone_present`** are read-only, so that scenario sets the verifier's mutating flag and asserts instead that no install, uninstall, push or `rm` call was logged.

## Authentication Gates

None. No device, real adb, push-test-key, key file or live provider call was used (D-01, D-13). The fake `push-test-key` only records argv.

## Notes for downstream plans (10-08 to 10-10)

- `push-fixture` and `push-keys` need the package installed first (`build-install`); `run-as` fails on a missing or non-debuggable package (`run_as_failed`).
- After `push-fixture`, force-stop and relaunch the app (10-06 note): the fixture is loaded once at process start.
- `push-keys` is gated on a column-0 `decision: approved` line in `10-LIVE-LEG-DECISION.md` (10-10 writes that file; until it exists the runner answers `live_legs_not_approved`).
- Call `cold-stamp check` before an Anthropic agentic start and `cold-stamp write` right after it starts; exit 3 means wait, not fail.
- `capture-save <leg>` appends; running it twice for a leg writes two blocks, each under its own `# gate1 ...` header. Evidence files are `.txt` because `*.log` is gitignored.
- The runner's `finish` always prints the `SAMPLE_GATE1:` line last; a tester should key its verdict off that line and the exit code, not off the earlier informational lines.

## Self-Check: PASSED

- All three scripts exist, mode 100755 in the index.
- Commits e932b68, 84fd1c1 and 4856c65 present; `commits: 3` measured with `git rev-list --count 22ffa3b..HEAD`.
- No change under core/, providers/, keystore/, sample/; the Phase 6 scripts untouched; no STATE.md or ROADMAP.md edit; nothing under .planning/graphs, .gsd, intel, config.json, state.json staged.
