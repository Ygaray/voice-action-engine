---
phase: 13-on-device-model-spike
plan: 08
subsystem: testing
tags: [on-device, tester-window, litertlm, gemma-4-e2b, evidence, time-box, spike]

requires:
  - phase: 13-on-device-model-spike
    provides: "guarded runner and grant gate (13-06); measurement ladder and stage host app (13-07); locked thresholds (13-01); closed evidence grammar and filter (13-03)"
provides:
  - "Committed, filtered evidence of Gemma 4 E2B on the TESTER: env, models, preflight, init, prefill, kv_reuse, rf_matrix, screen_small, confirm_small, partial screen_sb, dumpsys PSS cross-checks"
  - "Host-private raw per-item sb answers (RT-02) for re-scoring without a device re-run"
  - "scripts/run-spike-ondevice.sh pull-private-raw and the app-side PrivateRawSink"
  - "13-WINDOW-GRANT.md relayed, then consumed; TESTER cleaned and cooled"
affects: [13-09, 13-10, 13-11, 19]

actuals:
  tokens: 70000
  tasks: 3
  commits: 12

plan_head_before: 2c078e814ecc4bb70de99133c7c23f567c203b1b
commits: 12

key-files:
  created:
    - spike-ondevice/src/main/kotlin/io/github/ygaray/voiceactionengine/spike/trial/RawItemSink.kt
    - .planning/phases/13-on-device-model-spike/evidence/env.txt
    - .planning/phases/13-on-device-model-spike/evidence/models.txt
    - .planning/phases/13-on-device-model-spike/evidence/preflight.host.txt
    - .planning/phases/13-on-device-model-spike/evidence/prepare.txt
    - .planning/phases/13-on-device-model-spike/evidence/preflight.txt
    - .planning/phases/13-on-device-model-spike/evidence/init.txt
    - .planning/phases/13-on-device-model-spike/evidence/prefill.txt
    - .planning/phases/13-on-device-model-spike/evidence/kv_reuse.txt
    - .planning/phases/13-on-device-model-spike/evidence/rf_matrix.txt
    - .planning/phases/13-on-device-model-spike/evidence/screen_small.txt
    - .planning/phases/13-on-device-model-spike/evidence/confirm_small.txt
    - .planning/phases/13-on-device-model-spike/evidence/screen_sb.txt
    - .planning/phases/13-on-device-model-spike/evidence/screen_sb.host.txt
    - .planning/phases/13-on-device-model-spike/evidence/meminfo.host.txt
  modified:
    - spike-ondevice/src/main/kotlin/io/github/ygaray/voiceactionengine/spike/trial/TrialRunner.kt
    - spike-ondevice/src/main/kotlin/io/github/ygaray/voiceactionengine/spike/trial/MeteredBackend.kt
    - spike-ondevice/src/main/kotlin/io/github/ygaray/voiceactionengine/spike/ladder/Ladder.kt
    - spike-ondevice/src/main/kotlin/io/github/ygaray/voiceactionengine/spike/ladder/TrialStages.kt
    - spike-ondevice/src/main/kotlin/io/github/ygaray/voiceactionengine/spike/SpikeActivity.kt
    - spike-ondevice/src/test/kotlin/io/github/ygaray/voiceactionengine/spike/TrialRunnerTest.kt
    - scripts/run-spike-ondevice.sh
    - scripts/verify-spike-device-guard.sh
    - .planning/phases/13-on-device-model-spike/13-WINDOW-GRANT.md

key-decisions:
  - "RT-02 raw answers: a minimal app and runner change before window-start (not a workaround at the evidence layer), host-private under ~/.local/share/vae-spike/raw/, never committed"
  - "The runner's time-box ran out inside screen_sb; per the plan nothing was re-run or extended and the remaining stages were refused by the runner"

requirements-completed: []
requirements-advanced: [SPIKE-01]

status: complete
completed: 2026-10-06
---

# Phase 13 Plan 08: TESTER window, Gemma 4 E2B measurement Summary

**One 4 h TESTER window measured Gemma 4 E2B on LiteRT-LM: the gating engine rows (GPU, prefill, KV reuse, ResponseFormat) and the full small envelope (screen and confirm) are committed filtered evidence; the SB envelope reached only its first 28 screen trials before the time-box expired, so the remaining SB and sustained stages were refused and read unmeasured. The TESTER is clean and cooled and the grant is consumed.**

## Window record

| Item | Value |
|---|---|
| Grant (task 1, relayed) | `grant: open`, `relayed_by: orchestrator yahir-gsd-control-plane-3b via the milestone master`, `date: 2026-10-05`, `timebox_s: 14400`, `sb_labels: default` (VAE-authored SB gold labels per RT-02, SB review pending), `gemma3_1b: skipped_gated` |
| Window open (runner `window-start`) | 2026-10-06T01:51:40Z (epoch 1791251500) |
| Window close (grant consumed) | 2026-10-06T05:52:07Z (time-box 14400 s reached at 05:51:40Z; cleanup and cooldown ran after, inside the same minute) |
| Device | TESTER R5CT10XNKQN (SM-S908U, sdk 35), USB; read-only `adb devices` listed it as `device` before the window |
| Thresholds | `thresholds_sha=ec4933fb` equals the first 8 hex of the committed `13-THRESHOLDS.md` (checked by the runner at window-start) |
| Build | HEAD `08ada3366b`, `apk_md5=445264d6594096eec71d08a47032e9c1`, dirty=0 |
| SB envelope | private fixture digest `8bc739ed`, 19 tools (reported dimension), gold digest `8c80bafa` |
| Driver announcement | The driver, not this executor, announces "device done tester" to the orchestrator |

The window was released after the 13-08 cleanup; plan 13-09 needs no device.

## Runner last lines and stages

| Step | Runner last line | Result |
|---|---|---|
| window-start | `OK sub=window-start window_start=1791251500 timebox_s=14400 thresholds_sha=ec4933fb` | |
| preflight (host) | `OK sub=preflight ... installed=no` | mem_total 11.47 GB, storage free 443 GB, opencl present |
| build-install | `OK sub=build-install ... head=08ada3366b dirty=0` | |
| run prepare, pull | `OK sub=run stage=prepare result=done` | |
| push-model e2b_cpu | `OK ... id=e2b_cpu sha=18193810 bytes=2588147712 path=external` | match=1 |
| push-model e2b_gpu | `OK ... id=e2b_gpu sha=a53a5900 bytes=2008432640 path=external` | match=1 |
| push-private | `OK sub=push-private private=sb fixture_sha=8bc739ed gold_sha=8c80bafa` | |
| run init | `OK sub=run stage=init result=done` | cpu ok 22.5 s, gpu first-ever 40.2 s, gpu repeat 39.3 s, `gpu_ok` (Adreno 730) |
| run prefill | `OK stage=prefill result=done` | cpu 22 to 25 tok/s; gpu 920 to 1270 tok/s |
| run kv_reuse | `OK stage=kv_reuse result=done` | `not_reused` for both preface_on_init 0 and 1 |
| run rf_matrix | see Deviations 2 | `STAGE result=done`; flat and bounds `ignored`, six features `enforced`, `constrained_flag=na` |
| run screen_small | `OK stage=screen_small result=done` | 80/80 trials, winner `e2b.cpu.b.auto`, routes a,b |
| run confirm_small | `OK stage=confirm_small result=done` | 164/164 trials (distinct items plus forced subset) |
| run screen_sb | `INFRA sub=run reason=timebox_expired stage=screen_sb` (rc 3) | time-box expired at trial 28 of 80 planned (cpu.a 20 trials, cpu.b 8 trials); host line `VAE_SPIKE_STAGE stage=screen_sb result=timeout source=host` |
| pull-evidence screen_sb | `OK stage=screen_sb kept=35 dropped=0` | partial evidence pulled once, as the plan prescribes |
| pull-private-raw | `OK sub=pull-private-raw files=1 bytes=12535` | RT-02 raw answers, host-private |
| run exit_reasons | `INFRA sub=run reason=timebox_expired` (rc 3, refused before any adb call) | refused after expiry |
| cleanup | `SPIKE_ONDEVICE: OK sub=cleanup target=R5CT10XNKQN` | "spike package, external model directory and staging files removed" |
| final cooldown | `OK sub=cooldown status=0 waited_s=0` | |
| preflight after consume | `ERROR sub=preflight reason=window_not_granted` | re-runs are refused |

Not run, because the time-box was spent: `confirm_sb`, `sustained_small`, `sustained_sb`, `exit_reasons` (all refused or never reached; no evidence files exist for them). No early exit fired (`init_failed_all` no, `sb_prefill_bound` no, `sb_fixture_absent` no). Gemma 3 1B: skipped_gated, never pushed.

## Verdict preview (13-09 owns the official verdict)

`scripts/verify-spike-verdict.sh --print`, HEAD `74ba3d3`:

```
SPIKE_VERDICT envelope=small verdict=red reasons=fail:warm_p50,fail:warm_p95,fail:cold,unmeasured:sustained,fail:peak_pss,unmeasured:process_deaths,fail:semantic_en,fail:semantic_es,fail:false_writes cell=e2b.cpu.b.auto warm_p50_ms=20278 warm_p95_ms=28941 cold_ms=28182 sustained_ratio=na thermal_max=moderate peak_pss_mb=2268 deaths=na schema_valid=142/144 en=43/55 en_lb=0.656 es=35/55 es_lb=0.504 false_writes=2/34 kv_reuse=not_reused rf_enforced=ignored rf_enum_nested=enforced gpu_adreno730=gpu_ok route_ab=a:8/40:28632,b:40/40:18887
SPIKE_VERDICT envelope=sb verdict=red reasons=unmeasured:warm_p50,unmeasured:warm_p95,unmeasured:cold,unmeasured:sustained,unmeasured:process_deaths,unmeasured:schema_valid,unmeasured:semantic_en,unmeasured:semantic_es,unmeasured:false_writes cell=e2b.cpu.b.auto warm_p50_ms=na warm_p95_ms=na cold_ms=na sustained_ratio=na thermal_max=none peak_pss_mb=525 deaths=na schema_valid=na en=na en_lb=na es=na es_lb=na false_writes=na kv_reuse=not_reused rf_enforced=ignored rf_enum_nested=enforced gpu_adreno730=gpu_ok route_ab=a:5/20:47432,b:8/8:225707
SPIKE_CONTROL envelope=small model=g3_1b status=skipped_gated cell=none schema_valid=na p50_ms=na peak_pss_mb=na
SPIKE_VERDICT_META thresholds_sha=ec4933fb harness_sha=08ada3366b toolchain=ok pin=0.17.1
SPIKE_VERDICT_CODE sha=74ba3d3f64
```

These are code-computed previews; this plan did not edit any evidence and did not judge them. Observations for 13-09 and 13-10 (facts from the evidence, not verdicts):

- The SB tool count (19) rode in the committed `preflight.txt` as `sb_tool_count=19` and is a reported dimension.
- On CPU the SB-sized prompt is prefill-bound: kv_reuse is `not_reused` (about 4852 prefill tokens, TTFT about 212 to 216 s), and the SB cells took 47 s (route a) and 226 s (route b) per trial. GPU prefill is about 1240 tok/s, but the SB screen walks its cells in the ladder's fixed order (cpu.a, cpu.b, gpu.a, gpu.b), so the time-box ended inside the second CPU cell. The `sb_prefill_bound` early exit did not fire, because the rule reads the best measured prefill speed, which was the GPU's.
- The small envelope alone (screen 80 trials plus confirm 164 trials) consumed about 2 h of the 4 h window at about 20 to 34 s per trial.

## Raw per-item sb answers (RT-02)

Private path: `~/.local/share/vae-spike/raw/screen_sb.jsonl` (mode 600, directory mode 700, outside the repository, never committed; 28 trial rows, 12,535 bytes). Each row holds the stage, cell, item id, the scored fields, the model's raw text, the raw native tool calls and the mapped first call with its arguments, so SB rows can be re-scored on the host against `~/.local/share/vae-spike/sb-gold.json` without a device re-run if SB objects. Cleanup removed the app-private copy. Only the screen_sb stage produced raw rows (the other sb stages never ran).

## Deviations from Plan

**1. [Rule 2 - Missing critical functionality, required by RT-02] Host-private raw per-item outputs.** Found at: reading the harness before window-start. Neither the app nor the runner saved raw per-item model outputs, and cleanup removes app-private data, so SB rows could not have been re-scored without a device re-run. Fix, made and host-tested before the first device stage, locked thresholds and the evidence grammar untouched:
- App: `PrivateRawSink` (`trial/RawItemSink.kt`) appends one JSON line per sb trial to `files/private/raw/<stage>.jsonl`; `MeteredBackend` keeps the last raw text and tool calls; `TrialRunner` takes an optional sink and feeds it sb trials only; `LadderEnv` and `SpikeActivity` wire it. New `TrialRunnerTest` case: an sb trial is kept, a small trial is not. `:spike-ondevice:testDebugUnitTest` and `assembleDebug` pass (9 `TrialRunnerTest` cases, 0 failures).
- Runner: new read-only subcommand `pull-private-raw` behind the same grant, shared lock and TESTER identity guard; copies `files/private/raw/*.jsonl` to `VAE_SPIKE_PRIVATE_DIR/raw`, refuses when that resolves inside the repository, never prints content. `scripts/verify-spike-device-guard.sh` gained 6 scenarios: `SPIKE DEVICE GUARD OK scenarios=88`; `verify-repo-hygiene.sh` prints `HYGIENE OK`.
- Commits `740942a` (app) and `f147e69` (runner), both before `window-start`; the APK was built from a clean tree at `08ada3366b`.

**2. [Executor error, no data effect] The host-side wait of `run rf_matrix` was cut short by my own wrapper timeout.** I wrapped the stage helper in a 590 s `timeout`, which killed the runner's wait (the on-device stage kept running and finished by itself). I did not re-run the stage (no `run` was re-issued). I then polled with `meminfo rf_matrix` three times (a read-only, informational command that appends one `VAE_SPIKE_MEM source=dumpsys stage=rf_matrix` line per call: 2278, 2463, then 90 MB once the engine had closed) and pulled the finished evidence once, so `rf_matrix.txt` has no duplicate lines. The three lines are in `meminfo.host.txt` and, as a bonus, cross-check the in-process peak (2478 MB) against dumpsys (2463 MB). A stage that ended `done` with its STAGE line; no stage was repeated and no number was chosen. Every later stage ran under the tool's own background mechanism with no inner timeout.

**3. [Limitation, no workaround] The 13-THRESHOLDS (f) dumpsys cross-check during `confirm_small` could not run.** `run` holds the shared lock for the whole stage, so `meminfo confirm_small` answered `INFRA sub=meminfo reason=tester_busy` (rc 3). I did not change the runner mid-window. The only dumpsys-versus-in-process comparison on record is the rf_matrix pair above (2463 vs 2478 MB, within 1%). `meminfo.host.txt` has no confirm_small line. For a future window, `meminfo` would have to be allowed to read without the lock, or the stage polled by the runner itself.

**4. [Plan outcome, not a deviation] Time-box expiry.** Per Task 3 step 2: the running stage was stopped by the runner at the box (`timeout` host line), its partial evidence was pulled once, `exit_reasons` was attempted and refused, and the window went to cleanup. No re-run, no extension, no threshold change.

**5. [Judgment] Mid-run recorded state change: none beyond the runner's.** No keyguard wake or swipe was needed (`foreground: unknown` throughout; the stages ran). No uiautomator, screenshot or raw logcat was taken. No `adb` command ran outside the runner except the read-only `adb devices` before the window.

**Total deviations:** 1 Rule 2 addition (RT-02), 1 executor error with no data effect, 1 limitation recorded. **Impact:** SB re-scoring is possible on the host; the SB envelope is unmeasured by the time-box, as the locked rule says.

## Authentication Gates

None. No API key, no Hugging Face token, no spend.

## Verification

- Plan Task 2 verify: `grant: open` was recorded before window-start; `env.txt` `thresholds_sha=ec4933fb` equals the committed thresholds file prefix; every gating stage (init, prefill, kv_reuse, rf_matrix) has its `VAE_SPIKE_STAGE ... result=done` line.
- Acceptance: both `VAE_SPIKE_MODEL ... match=1` lines present (e2b_cpu, e2b_gpu); `VAE_SPIKE_GPU disposition=gpu_ok` present; kv_reuse holds the two `VAE_SPIKE_KVREUSE` rows (preface_on_init 0 and 1); every committed evidence file re-passes `scripts/spike-evidence-filter.sh`.
- Plan Task 3 verify: all evidence files pass the filter, `grep -qx 'grant: consumed'` holds, the verdict preview yields exactly one line per envelope.
- Acceptance not met as written: `grep -c '^VAE_SPIKE_EXIT ' exit_reasons.txt` is not applicable (the stage was refused after expiry, recorded above); no `exit_reasons.txt`, `confirm_sb.txt`, `sustained_small.txt` or `sustained_sb.txt` exists.
- `git status --porcelain` lists no uiautomator dump, screenshot, raw logcat, model-shaped file, SB fixture or gold file; the raw jsonl lives only under `~/.local/share/vae-spike/raw/`.
- Not done by design: any behavioral judgment of the numbers (13-09 computes the verdict from the committed evidence).

## Self-Check: PASSED

- Evidence files present under `.planning/phases/13-on-device-model-spike/evidence/`: env, models, preflight.host, prepare, preflight, init, prefill, kv_reuse, rf_matrix, screen_small, confirm_small, screen_sb, screen_sb.host, meminfo.host.
- Commits present (`git rev-list --count 740942a^..HEAD` measured 12 before this summary): `740942a`, `f147e69`, `08ada33`, `da93d6e`, `941e79d`, `75de61a`, `da22d3e`, `b35b30c`, `c4749d5`, `264ff31`, `6f643de`, `74ba3d3`.
- Grant file reads `grant: consumed` with `closed: 2026-10-06T05:52:07Z`.
