---
phase: 13-on-device-model-spike
plan: 06
subsystem: testing
tags: [device-guard, tester-window, fake-adb, time-box, model-integrity, on-device, spike]

requires:
  - phase: 13-on-device-model-spike
    provides: "scripts/spike-evidence-filter.sh and the closed VAE_SPIKE_ grammar (13-03); 13-THRESHOLDS.md (13-01)"
provides:
  - "scripts/run-spike-ondevice.sh: the only sanctioned host path to the TESTER for the spike (sample-runner guard copied intact, plus the window-grant gate and the D-07 time-box)"
  - "scripts/verify-spike-device-guard.sh: offline fake-adb proof of every refusal path, every device step and call addressing (82 scenarios)"
  - "13-WINDOW-GRANT.md: grant pending, with the settled grant inputs recorded"
  - "Both pinned Gemma 4 E2B model files on the host, outside the repository, sha256-verified"
affects: [13-07, 13-08, 13-09, 13-10]

actuals:
  tokens: 20900
  tasks: 3
  commits: 3

plan_head_before: 557d24d3edfe38970bb870f01057f6e99025f4f0
commits: 3

tech-stack:
  added: []
  patterns:
    - "Guard copied from run-sample-gate1.sh unchanged: USB first, wireless only after ro.serialno proof, personal phone refused, model and SDK identity, shared flock, -s on every call, no target knob"
    - "Window grant as an exact-line gate that runs before the lock and before any adb call; one window per grant; time-box stamp in the user cache"
    - "Offline proof by a fake adb, gradlew and curl per scenario in a throwaway git repo; pins, poll intervals and cool-down cap overridden by sed in the throwaway copy only (the real runner has no knob)"
    - "Digest checked at both ends: host sha256 against the pin before the push, device sha256sum after it; a mismatch deletes the device file"

key-files:
  created:
    - scripts/run-spike-ondevice.sh
    - scripts/verify-spike-device-guard.sh
    - .planning/phases/13-on-device-model-spike/13-WINDOW-GRANT.md
  modified: []

key-decisions:
  - "The grant file keeps machine-parsed values on their own lines (sb_labels: default, sb_fixture_sha8, gemma3_1b: skipped_gated); the master's settled inputs are recorded as those lines plus a prose section, so the runner can enforce them"
  - "The runner refuses push-model g3_1b whenever the grant's gemma3_1b value starts with skip, so the orchestrator ruling skipped_gated is honoured as written (the plan's own wording was the literal value skip)"
  - "Staging paths are registered in the parent shell (a global, not a command substitution), so every finish and the EXIT/signal traps remove them"
  - "A missing window stamp is reported as reason=timebox_expired detail=no_window_stamp, as the plan words it, with the detail naming the real cause"

requirements-completed: []
requirements-advanced: [SPIKE-01]

coverage:
  - id: D1
    description: "Every device subcommand refuses with exit 2 reason=window_not_granted, with zero adb calls and no lock taken, unless the grant file holds the exact line grant: open (pending, missing and look-alike lines all refuse)"
    requirement: SPIKE-01
    verification:
      - kind: other
        ref: "scripts/verify-spike-device-guard.sh: grant_pending, grant_missing, grant_lookalike, grant_pending_blocks_run (CALLS_EMPTY) -> SPIKE DEVICE GUARD OK scenarios=82"
        status: pass
    human_judgment: false
  - id: D2
    description: "The TESTER guard is intact and proven: foreign ANDROID_SERIAL, personal IP, offline, USB and wireless impostors, and a busy lock all refuse; every logged adb call is addressed -s to the TESTER (or is the single connect to its wireless address)"
    requirement: SPIKE-01
    verification:
      - kind: other
        ref: "scripts/verify-spike-device-guard.sh: foreign_android_serial_*, offline, usb_impostor*, wireless_impostor*, lock_busy plus the per-scenario addressing assertion; four runner mutations (grant inverted, -s dropped, identity check weakened, filter bypassed) each made the verifier fail"
        status: pass
    human_judgment: false
  - id: D3
    description: "D-07 time-box: window-start writes the first ENV line (8-hex thresholds_sha, 10-hex head, window_start, granted timebox_s at most 14400); after expiry push-model, push-private and run refuse with INFRA timebox_expired while preflight, pull-evidence, meminfo, cooldown and cleanup stay allowed; run is bounded by the remaining time-box"
    requirement: SPIKE-01
    verification:
      - kind: other
        ref: "scripts/verify-spike-device-guard.sh: window_start_*, timebox_expired_*, timebox_no_stamp_run, run_timebox_inflight"
        status: pass
    human_judgment: false
  - id: D4
    description: "Pinned models only: e2b_cpu and e2b_gpu are checked against the RESEARCH sha256 pins on the host before the push and by sha256sum on the device after it (mismatch removes the device file); g3_1b only as a file Yahir placed himself and never when the grant skips it; the A8 run-as fallback removes its staging file; no model file may sit inside the repository"
    requirement: SPIKE-01
    verification:
      - kind: other
        ref: "scripts/verify-spike-device-guard.sh: push_model_* (11 scenarios) and fetch_model_* (7 scenarios); sha256sum -c of both host files prints OK twice; git status --porcelain --ignored holds no litertlm path"
        status: pass
    human_judgment: false
  - id: D5
    description: "push-private moves the private SB fixture and gold labels into app-private storage only, refusing when the gold's fixture_sha256 differs from the fixture or from the grant's recorded prefix; evidence carries 8-hex prefixes and no content ever reaches an argv or the output"
    requirement: SPIKE-01
    verification:
      - kind: other
        ref: "scripts/verify-spike-device-guard.sh: push_private_* (8 scenarios, a planted fixture marker never reaches an argv or the output)"
        status: pass
    human_judgment: false
  - id: D6
    description: "pull-evidence passes the app's evidence file through the allow-list filter in a private temp dir (a rejected capture writes nothing); cleanup force-stops, removes private data, undoes the whitelist, uninstalls and proves the package, the external model directory and the staging files are gone (a leftover of any kind is FAIL)"
    requirement: SPIKE-01
    verification:
      - kind: other
        ref: "scripts/verify-spike-device-guard.sh: pull_evidence_* (4), cleanup_* (4), timebox_expired_allows_*"
        status: pass
    human_judgment: false
  - id: D7
    description: "The runner has only ever been exercised against a fake adb; the real TESTER was not touched and the grant is still pending. Real-device behaviour of these commands (run-as, dumpsys formats, adb push into /sdcard/Android/data on Android 15) is first exercised in plan 13-08 under a granted window"
    requirement: SPIKE-01
    human_judgment: true
    rationale: "Device behaviour cannot be judged offline; assumption A8 and the dumpsys parsing are confirmed or refuted only in the granted window (13-08)"

duration: 26min
completed: 2026-10-06
status: complete
---

# Phase 13 Plan 06: Guarded spike runner Summary

**The spike now has its own TESTER-only host runner: the sample runner's identity guard copied intact, a window-grant gate that refuses before any adb call, a D-07 time-box, digest-checked model and private-input pushes with a proving cleanup, all verified offline with a fake adb over 82 scenarios; both pinned Gemma 4 E2B files are on the host, sha256-verified, outside the repository.**

## Performance

- **Duration:** 26 min
- **Started:** 2026-10-06T00:54Z
- **Completed:** 2026-10-06T01:21Z
- **Tasks:** 3 (tracer + 2 auto)
- **Files:** 3 created, 0 modified (about 1,320 script lines)

## Accomplishments

- **Task 1 (tracer): the guard end to end.** `scripts/run-spike-ondevice.sh` copies the sample runner's preamble and helpers (constants, `adb_t`/`adbt` with fd 9 closed, `finish`, `in_list`, `refuse_foreign_serial`, `acquire_and_resolve`, `print_foreground`, `package_installed`) and adds the exact-line grant check, run order `refuse_foreign_serial`, `host_precheck`, `acquire_and_resolve`, dispatch. `PHASE_DIR` is retargetable only through `VAE_SPIKE_PHASE_DIR` and only to a relative path without `..`. `preflight` prints one closed-grammar `VAE_SPIKE_PREFLIGHT source=host ...` line (read-only shell calls) and `pull-evidence` runs the device file through the allow-list filter. The tracer gate re-ran the verifier after the commit (passed), then expansion continued.
- **Task 2: the measurement subcommands.** `window-start` (one window per grant, `timebox_s` numeric and at most 14400, thresholds file committed unchanged, first ENV line), `build-install` (dirty-tree refusal before Gradle, the low-memory recipe, `install -r`, deviceidle whitelist), `push-model` (host sha and size against the pin, device free space over size plus 1 GiB, external models directory must exist, direct push, readback, A8 fallback through staging plus `run-as`), `push-private`, `run` (force-stop, `am start`, 15 s polls of the stage-line count against a baseline so a stale line from an earlier run cannot satisfy it, `process_gone` and in-flight `timeout` host lines), `cooldown`, `meminfo` and `cleanup` (three proofs).
- **Task 3: fetch-model and the pre-fetch.** `fetch-model` is host only (no grant, no lock, no adb), revision-pinned URL, size and sha256 checked, `.part` file renamed only on success, `g3_1b` always `gated_human_download`. Real runs on the host, one at a time:
  - `SPIKE_ONDEVICE: OK sub=fetch-model id=e2b_cpu sha=18193810 bytes=2588147712 cached=no`
  - `SPIKE_ONDEVICE: OK sub=fetch-model id=e2b_gpu sha=a53a5900 bytes=2008432640 cached=no`
  - Files sit in `~/.cache/vae-spike/models/`; `sha256sum -c` prints OK twice; nothing model-shaped is in the working tree.
- **The verifier is not vacuous.** Besides the 82 scenarios, I mutated the runner in place fourteen ways (grant check inverted, `-s` dropped, identity check weakened, filter bypassed, time-box ignored, host digest unchecked, readback unchecked, cleanup residue ignored, staging not removed, private pin unchecked, g3 skip ignored, model dir in repo allowed, dirty tree ignored, process-gone ignored) and each one made the verifier fail (the process-gone mutation by hanging until the per-scenario timeout, which is why every scenario now runs under `timeout -k 5 120`). The runner was restored byte for byte after each.

## Task Commits

1. **Task 1 (tracer): spike runner, grant file and the first guard scenarios** - `164cab1` (feat)
2. **Task 2: time-box, model and private pushes, run, cleanup scenarios** - `2e1c16b` (feat)
3. **Task 3: fetch-model scenarios; both E2B files pre-fetched** - `7767c1e` (test)

## Decisions Made

See `key-decisions` above. The grant now reads:

```
grant: pending            (the only line anything may change, only via the relay)
relayed_by:
date:
timebox_s: 14400
sb_labels: default
sb_fixture_sha8: 8bc739ed
sb_fixture_tools: 19
gemma3_1b: skipped_gated  (source: orchestrator ruling, 2026-10-05)
```

## Deviations from Plan

**1. [Rule 1 - Bug] Staging registration lost in a subshell.** Found during: Task 2 | Issue: `new_staging` was called as `staging="$(new_staging ...)"`, so the `STAGINGS+=` registration happened in a subshell and the staging file would never have been removed by the traps (the verifier's A8 scenario caught it) | Fix: the helper now sets the global `NEW_STAGING` and registers in the parent shell | Files: `scripts/run-spike-ondevice.sh` | Verification: `push_model_a8_fallback`, `push_private_ok` and the "staging not removed" mutation | Commit `2e1c16b`.

**2. [Judgment] Runner was written complete in the Task 1 commit.** The plan has Task 1 answer `not_implemented` for the measurement subcommands until Task 2. I wrote the whole runner once (it is one file) and committed it with Task 1's verifier scenarios; Tasks 2 and 3 added the scenarios that prove those subcommands, plus the one staging fix above. The per-task commits therefore split the proof, not the code.

**3. [Judgment] Grant wording.** The plan's pending template says `gemma3_1b: skip` and `sb_labels: default`; the master settled `skipped_gated` and the re-pinned SB fixture. I wrote `gemma3_1b: skipped_gated` (the runner refuses any value starting with `skip`), kept `sb_labels: default`, and recorded the fixture pin as its own machine-read line `sb_fixture_sha8: 8bc739ed` (8-hex prefix only; `push-private` refuses a fixture that does not start with it). The grant stays `grant: pending`.

**4. [Judgment, stricter than the plan] Extra guards.** `build-install`'s clean-tree paths also cover `providers`, `keystore`, `build.gradle.kts` and `gradle.properties` (the APK is exactly HEAD for everything it builds from); `window-start` refuses unless `13-THRESHOLDS.md` is committed unchanged (so the digest in the first ENV line is of the committed file); `pull-evidence` with no grammar line writes nothing (`no_evidence_lines`); the verifier adds a check that the runner contains no token or authorization handling.

**5. [Judgment] `run` counts stage lines instead of grepping once.** It takes a baseline `grep -c` of `VAE_SPIKE_STAGE stage=<s> result=` before the start and waits for the count to rise, so a stale line from an earlier attempt at the same stage (the evidence file is append-only) cannot read as completion.

Otherwise as written. No `adb` command touched any device in this plan.

**Total deviations:** 1 auto-fixed bug, 4 judgment calls. **Impact:** none on scope; the runner is a little stricter than specified, in the safe direction.

## Issues Encountered

- My mutation test of the "process gone" check left an orphaned runner process polling under a fake adb for about two minutes, which held the shared lock file and made the verifier's own setup refuse once ("tester lock is held by a real run"). It was a verifier-only process against a fake adb; it exited by its own timeout and nothing real was touched. Every scenario now runs under `timeout -k 5 120`.
- One earlier background shell was killed by a `pkill -f` whose pattern matched its own command line; the runner file was left mutated by that interrupted check and was restored from the saved good copy before any commit (`git diff` confirmed only the intended staging fix).

## Authentication Gates

None. No API key and no Hugging Face token were read or needed (the E2B files are ungated, Gemma 3 1B was not touched).

## Verification

- Plan `<verification>`: `scripts/verify-spike-device-guard.sh` ends `SPIKE DEVICE GUARD OK scenarios=82` (at least 25 required); both E2B files verify against their pins (`sha256sum -c` OK twice); `git status --porcelain --ignored` holds 0 `litertlm` paths; `13-WINDOW-GRANT.md` reads `grant: pending` (`grep -cx 'grant: pending'` prints 1, `grep -cx 'grant: open'` prints 0).
- Task 1 acceptance: verifier at least 11 scenarios (82); the real-environment `scripts/run-spike-ondevice.sh preflight` ends `SPIKE_ONDEVICE: ERROR sub=preflight reason=window_not_granted` with exit 2 and touched no device; `git diff --quiet -- scripts/run-sample-gate1.sh scripts/verify-sample-device-guard.sh` exits 0.
- Task 2 acceptance: scenarios at least 22 (82); each pin's sha256 appears exactly once in the runner (count 1 and 1); the `STAGES="..."` line appears once with exactly the Records.Stage wire names.
- Task 3 acceptance: `scripts/run-spike-ondevice.sh fetch-model g3_1b` ends `SPIKE_ONDEVICE: ERROR sub=fetch-model reason=gated_human_download`, exit 2.
- `scripts/verify-repo-hygiene.sh` prints `HYGIENE OK`.
- Not run: any Gradle task (no code under `spike-ondevice` changed), any device or adb command (window not granted by design).

## Next Phase Readiness

Plan 13-07 should add the `STAGES` parity test against `Records.Stage` (the runner's list is the exact wire set today) and must keep these app contracts the runner relies on: `.SpikeActivity` accepting `--es stage <stage>`, evidence at `files/evidence/<stage>.txt` with a `VAE_SPIKE_STAGE stage=<s> result=<r>` line per finished stage, an external models directory `/sdcard/Android/data/<pkg>/files/models/` created by `run prepare` (the runner refuses `push-model` with `run_prepare_first` otherwise), and the private files read from `files/private/sb-fixture.json` and `sb-gold.json` (already the `SbEnvelope` constants). Plan 13-08 opens the window only from a relayed answer: set `grant: open`, then `window-start`, `preflight`, `build-install`, `run prepare`, `push-model`, `push-private`; at close run `cleanup`, then set `grant: consumed`. The host model files are ready; the first real exercise of `adb push` into `/sdcard/Android/data` (assumption A8) and of the `dumpsys` parsers happens in that window.

## Self-Check: PASSED

- Created files present: scripts/run-spike-ondevice.sh, scripts/verify-spike-device-guard.sh, .planning/phases/13-on-device-model-spike/13-WINDOW-GRANT.md.
- Commits present: 164cab1, 2e1c16b, 7767c1e.
- Model files present and verified: ~/.cache/vae-spike/models/gemma-4-E2B-it.litertlm and gemma-4-E2B-it-gpu.litertlm.
