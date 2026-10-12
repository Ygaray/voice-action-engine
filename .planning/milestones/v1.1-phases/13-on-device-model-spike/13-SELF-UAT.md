---
status: complete
result: all_pass
gate: 1
phase: 13-on-device-model-spike
source: [13-ROADMAP success criteria SC1-SC4]
device: none driven this run; behavior judged from the committed filtered evidence of the one consumed TESTER window (R5CT10XNKQN, SM-S908U, sdk 35)
apk: spike APK md5 445264d6594096eec71d08a47032e9c1 @ 08ada3366b (the harness that produced the evidence; verdict code e362fb228b)
run: 2026-10-06
---

# Self-UAT Log - Phase 13 (On-Device Model Spike), evidence-audited Gate-1

**Device:** none driven by this run. The phase's one-and-only TESTER window was granted, used (2026-10-06T01:51:40Z to 05:52:07Z) and consumed (`13-WINDOW-GRANT.md`: `grant: consumed`, `closed: 2026-10-06T05:52:07Z`). This run ran NO adb command (not even `adb devices`) and no Gradle task of its own. `scripts/run-spike-ondevice.sh` no longer exists (red-branch removal).
**Build identity:** evidence-producing harness `08ada3366b` (apk_md5 `445264d6594096eec71d08a47032e9c1`, dirty=0, from `13-08-SUMMARY.md` and `env.txt`); verdict code `e362fb228b`; this log written at HEAD `8d02c1f`.
**Platform / driver:** Android spike (pure measurement phase). Driver playbook not exercised: the Gate-1 record of this phase is, per plan 13-08's own text, the committed filtered evidence plus the host JVM verdict reproduction. Nothing in this run needed a fresh device drive, so no criterion is DEFERRED.
**Pre-flight:** n/a for the device. Host: `git worktree list` clean after the reproduction; `spike-ondevice/` absent from disk.
**Unit suite:** not re-run by this run. The orchestrator ran the `:core` / `:providers` / `:keystore` unit tests and the SC4 Gradle gates green after the review fixes (cited, not re-observed, host memory tight). The reproduction below does run the one `VerdictReproductionTest` through `verify-spike-verdict.sh --check` (its own low-memory Gradle recipe, in a temporary worktree at the code SHA).
**Coverage/Nyquist:** see `13-VALIDATION.md` / `13-VERIFICATION.md` (status passed, 4/4).
**Seed/fixture integrity:** the evidence ENV headers were checked first (`env.txt`): `thresholds_sha=ec4933fb` (equals the first 8 hex of the committed `13-THRESHOLDS.md`), `head=08ada3366b`, `timebox_s=14400`, `target=R5CT10XNKQN`, `device_model=SM-S908U`, `sdk=35`; private SB fixture `fixture_sha=8bc739ed` `match=1`. No programmatic seeding by this run (evidence audit only). Raw SB rows are host-private and were not read.

## Criteria

Posture: the red verdict is the measured outcome, not a phase failure. Each claim below was re-derived from the ROADMAP success criterion and re-observed from the committed evidence, not taken from a SUMMARY or the earlier verifier.

### 1. SC1 - Within a declared time-box a Gemma-2B-class model runs on the TESTER; recorded latency, peak RAM, strict-JSON reliability (with trial counts) and APK-size cost
result: passed
- **Rung:** 3 (headless data/log check over committed device evidence), plus 1 (the JVM `VerdictReproductionTest` via `--check`). No rung 4/5 (no UI claim exists in this criterion).
- **Target:** the committed evidence of the TESTER window (R5CT10XNKQN); no device touched by this run.
- **Expected:** a declared time-box; a Gemma-2B-class model actually executed on the TESTER; numbers for latency, peak RAM, schema-valid JSON reliability with trial count, and APK cost.
- **Arranged (seeded):** none (no setup needed by this run).
- **Did (drove):** nothing on a device. Read `evidence/env.txt`, `preflight.txt`, `toolchain.txt`, `screen_small.txt`, `confirm_small.txt`, `screen_sb.txt`, `screen_sb.host.txt`; recomputed the small-envelope aggregates from the raw `VAE_SPIKE_TRIAL` rows with an independent Python script (first row per distinct item = the gating model-chooses trial).
- **Observed (falsification attempt; criterion holds):**
  - Time-box declared and enforced: `timebox_s=14400` in the ENV header; `screen_sb.host.txt` = `VAE_SPIKE_STAGE stage=screen_sb result=timeout source=host`; grant consumed 05:52:07Z (4 h 0 min 27 s after the 01:51:40Z window-start; the box expired at 05:51:40Z).
  - Ran on the TESTER: ENV `target=R5CT10XNKQN device_model=SM-S908U sdk=35`; `VAE_SPIKE_INIT` and STAGE lines per stage; `13-08-SUMMARY.md` records `SPIKE_ONDEVICE: OK sub=cleanup target=R5CT10XNKQN` ("spike package, external model directory and staging files removed") and `OK sub=cooldown`; a later `preflight` is refused with `window_not_granted`. The model is Gemma 4 E2B on LiteRT-LM 0.17.1 (the D-01 resolution of "Gemma-2B-class"; MediaPipe LLM Inference is maintenance-only).
  - Trial counts in the evidence: `screen_small` 80/80, `confirm_small` 164/164 (144 distinct gating items + 20 forced-shape), `screen_sb` 28 of 80 planned. STAGE lines: `screen_small result=done trials=80 planned=80`, `confirm_small result=done trials=164 planned=164`.
  - Independent recomputation from raw `confirm_small.txt` rows (matches the verdict's `SPIKE_VERDICT envelope=small` line exactly): schema-valid **142/144**; EN **43/55**; ES **35/55**; false writes **2/34**; warm p50 **20278 ms**; warm p95 **28941 ms** (nearest-rank over 143 warm trials); `VAE_SPIKE_MEM ... peak_pss_mb=2268`.
  - APK cost in `toolchain.txt`: `so_bytes=21802960` (21.8 MB raw), `so_deflated_bytes=9497418` (9.5 MB), `so_stored=yes`, `apk_bytes=30771293`, `page=16k` OK; consumer stdlib uplift to 2.4.0 recorded (`uplift=yes`).
  - Honest limit (not a failure of SC1): the SB envelope has latency, reliability and accuracy **unmeasured** (28/80 screen trials, time-box). SC1's wording "a recorded measurement shows ..." is met by the small envelope in full; the SB gap is recorded as `unmeasured:*` red reasons per D-07 and is stated loudly in the verdict and the relay message.
- **Evidence:** `.planning/phases/13-on-device-model-spike/evidence/{env,preflight,toolchain,screen_small,confirm_small,screen_sb,screen_sb.host}.txt`; `13-VERDICT.md` small and SB tables; `13-WINDOW-GRANT.md`; `13-08-SUMMARY.md` (stage and cleanup table).

### 2. SC2 - A green/red verdict with numbers is messaged to the orchestrator before SB 179 and CT 75 plan
result: passed
- **Rung:** 1 (JVM reproduction) over rung 3 (file/evidence check).
- **Target:** headless host.
- **Expected:** a computed verdict with numbers, reproducible, and a relay record to the orchestrator.
- **Arranged (seeded):** none.
- **Did (drove):** `bash scripts/verify-spike-verdict.sh --check` (the script recomputes in a temporary git worktree at code SHA `e362fb228b` because `spike-ondevice/` is gone from the tree; first confirmed `git status --porcelain -- spike-ondevice/src` empty so the script does not refuse `dirty_harness`).
- **Observed:** final line `SPIKE_VERDICT_CHECK: OK lines=4`, exit 0; `git worktree list` shows only the main worktree afterwards (temp worktree removed). The four reproduced lines (small RED, sb RED, SPIKE_CONTROL g3_1b `skipped_gated`, META `thresholds_sha=ec4933fb harness_sha=08ada3366b`) equal the machine block in `13-VERDICT.md`; my independent Python recomputation (SC1) agrees with the small line, so the verdict is not just self-consistent but matches the raw evidence. `13-VERDICT-MESSAGE.md` holds the 9-line relay body with `relayed_to` (milestone master, for orchestrator yahir-gsd-control-plane-3b) and `relayed_at: 2026-10-06T05:58:59Z`, before SB 179 / CT 75 plan (those phases are not started in this repo). It is a **handoff record, not an orchestrator acknowledgement**: the launching agent states the relay was performed; no committed acknowledgement exists (carried as a Gate-2 note, not a blocker).
- **Evidence:** `13-VERDICT.md`, `13-VERDICT-MESSAGE.md`, `scripts/verify-spike-verdict.sh --check` output above.

### 3. SC3 - Green ships `@Experimental`; red: no module and no code ship, SPIKE-03 N/A-deferred, `v1.1.0` not blocked (L10)
result: passed
- **Rung:** 3 (headless scripts and tree checks).
- **Target:** headless host (the green clause is N/A by construction; the red clause is what applies).
- **Expected:** the branch is derived mechanically from the two verdict lines; on red nothing ships and the tag is not blocked.
- **Arranged (seeded):** none.
- **Did (drove):** `bash scripts/verify-spike-disposition.sh removed`; inspected `13-DISPOSITION.md`; `ls spike-ondevice`; grep of `core/build.gradle.kts`, `providers/build.gradle.kts`, `settings.gradle.kts`, `jitpack.yml`, `gradle/libs.versions.toml` for `spike|litert|mediapipe`.
- **Observed:** `SPIKE DISPOSITION OK mode=removed`. `13-DISPOSITION.md`: `branch: red`, `envelopes_green: none`, `spike03: N/A-deferred`; both input `SPIKE_VERDICT` lines read `verdict=red`, so the mechanical rule yields `red` (the rule text: either green would be `green_ship`/`green_defer`). `spike-ondevice/` does not exist; none of the five build/publish files names the spike, LiteRT or MediaPipe; no Phase 13.1 inserted and `v1.1.0` is stated unblocked (L10). Falsification attempt on the rule: the sb RED is unmeasured-by-time-box, but D-07 makes an unmeasured gating metric red, and even a green sb alone could not ship because the small envelope is a measured RED; the branch is therefore robust to the SB uncertainty. The green clause (provider ships `@Experimental` behind the `ON_DEVICE` gate) is not exercised and is N/A by design on red.
- **Evidence:** `13-DISPOSITION.md`; `scripts/verify-spike-disposition.sh removed` output.

### 4. SC4 - Whatever the verdict, `:core` and `:providers` gain no on-device or ML dependency; the `:core` allowlist and the no-on-device-implementation scan still pass
result: passed
- **Rung:** 3 (host gates) with the Gradle SC4 gates cited at rung 1.
- **Target:** headless host.
- **Expected:** `:core` / `:providers` classpaths carry no LiteRT / MediaPipe / TFLite artifact; the denial scans and their negative controls are live.
- **Arranged (seeded):** none (the controls plant their own violations in a scratch copy and require them to go red).
- **Did (drove):** `bash scripts/verify-repo-hygiene.sh`; `bash scripts/verify-ml-denial-controls.sh`; grep of the module build files above. Did NOT run Gradle for this criterion (instruction: host memory tight).
- **Observed:** `HYGIENE OK`; `ML DENIAL CONTROLS OK plants=8` (the `:core` on-device scan goes red on a planted ML token; hygiene goes red on planted `*.litertlm`, `*sb-gold.json`, `*sb-fixture.json` and a `jitpack.yml` naming the spike). `core/build.gradle.kts` and `providers/build.gradle.kts` contain no `litert|mediapipe|tflite|spike` reference. The Gradle gates (`:core` allowlist + `NoHardCodedConstantsTest`, `verifyNoMlArtifacts`, `:core`/`:providers`/`:keystore` unit tests) were run green by the orchestrator after the review fixes: **cited, not re-observed by this run**.
- **Evidence:** the two script outputs above; `13-VERIFICATION.md` SC4 row; `gradle/invariants.gradle.kts` `verifyNoMlArtifacts`.

## Summary

total: 4
passed: 4
partial: 0
failed: 0
infra: 0

## Notes / anomalies (for the Gate-2 reviewer)

- **Verdict is RED by measurement and by time-box, not a phase FAIL.** small RED is a measured fail (warm p50 20278 ms vs 3000, p95 28941 vs 5000, cold 28182 vs 20000, peak PSS 2268 MB vs 2000, EN LB 0.656 vs 0.85, ES LB 0.504 vs 0.80, false writes 2/34 vs 0; schema-valid 142/144 passes). sb RED is **unmeasured** (screen trial 28/80 when the 4 h box expired), "not shown to be voice-usable", not "measured and failed". SB 179 therefore has no on-device latency or accuracy number to plan against.
- **sb verdict cells to ignore:** the sb `peak_pss_mb=525` and `thermal_max=none` in the machine block come from a provisional winner's engine-init and partial screen, not a valid envelope peak (13-VERDICT.md caveat; the sampled sb CPU cell measured 4267 MB and prefill about 5.5 to 5.7 GB).
- **No fresh device drive was needed or authorized.** All device behavior is judged from committed evidence: ENV headers (target R5CT10XNKQN), the STAGE lines, the cleanup line in `13-08-SUMMARY.md`. A genuine fresh drive (e.g. GPU SB cells, sustained, process-death proof) would need a new orchestrator window grant; none is required for this phase's criteria.
- **Cited-not-re-observed items:** the SC4 Gradle gates and unit-test suites (orchestrator, after review fixes); the TESTER's current clean state (not probed; no adb allowed).
- **Process-cold only, no sustained / process-death numbers** for either envelope (`unmeasured:sustained`, `unmeasured:process_deaths` in the small line), a known limitation recorded in the verdict.
- **Bookkeeping carried from `13-VERIFICATION.md`:** W3 `REQUIREMENTS.md` still shows SPIKE-01 / SPIKE-02 pending (phase-complete should flip them); W4 the evidence filter has no automated test at HEAD (recoverable from history); W5 `verify-negative-controls.sh` Part 5 deliberately skipped.
- **Executor-deviation audit (13-08):** the rf_matrix wait cut by an executor wrapper timeout did not repeat or alter any stage (no data effect); the confirm_small dumpsys PSS cross-check could not run (runner lock), so the only dumpsys-vs-in-process pair on record is rf_matrix (2463 vs 2478 MB, within 1%). In-process PSS is the gating source and is consistent with it.

## Findings routed to gap-closure (if any)

None. No criterion FAILs; the red verdict is the phase's measured outcome.

## Verdict

All 4 criteria PASS (evidence-audited, no fresh device drive) -> Gate-1 complete; red branch, nothing ships, `v1.1.0` not blocked. Human Gate-2 (owner acknowledgement of the red verdict, the unmeasured SB envelope and the relay handoff) deferred to milestone completion and registered in `.planning/uat-pending/13-on-device-model-spike.md`.
