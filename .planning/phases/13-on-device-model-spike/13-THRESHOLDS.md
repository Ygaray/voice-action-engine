# Phase 13: On-Device Model Spike - Locked Thresholds (D-07)

**Locked:** 2026-10-06, committed by plan 13-01 before any device step. The D-07 numbers are human-locked and are never
changed. Only the definitions in section 3 (which D-07 leaves implicit) may be added to. The sha256 of this file is
written by the runner into the first evidence ENV line, and `verify-spike-verdict.sh` refuses evidence whose
`thresholds_sha` differs.

## 1. Scope

- "Gemma-2B-class" resolves to **Gemma 4 E2B on LiteRT-LM** (D-01). The E2B rows decide each envelope's verdict. The
  Gemma 3 1B rows are a control on the small envelope only and can never turn an envelope green.
- Two envelopes, each with its own verdict (D-05):
  - **small**: 4 domain-free synthetic tools, read by CT 75.
  - **sb**: the SB-sized envelope, read by SB 179. The tool count is a reported dimension (the original pin was 18 tools,
    SB's current surface is 19 tools including `ask_user_to_choose`), never a hard assumption. The fixture and its gold
    labels are private and never committed.
- If the SB fixture is unavailable, the sb envelope is red with `early_exit:sb_fixture_absent`. No surrogate is
  substituted, and nothing is green by default.

## 2. The bar (D-07, source: human)

> "voice-usable": warm p50 <=3.0 s / p95 <=5.0 s, cold <=20 s, sustained p50 <=1.5x warm, no SEVERE thermal, peak PSS
> <=2,000 MB, zero process deaths, schema-valid >=98% (N>=100), semantic Wilson LB >=85% EN / >=80% ES (N>=50 each),
> zero false writes in >=30 negative trials

Anchor: the v1.0 cloud SingleShot baseline on the same TESTER (Haiku 612-1,391 ms, gpt-5.4-mini 1,946 ms).

## 3. Definitions D-07 leaves implicit

These clarify D-07's wording and change no number.

- (a) schema-valid >= 98% is a **point estimate** over >= 100 confirm trials. A Wilson reading cannot be met at N=100
  (100/100 gives a 96.3% lower bound), so at N=100 the allowance is at most 2 failures.
- (b) Semantic accuracy uses the **Wilson 95% lower bound** with z=1.959964. At N=50 that needs >= 48/50 EN and >= 46/50 ES.
- (c) A trial is one distinct gold item. With seeded greedy decoding a repeat gives the same answer, so repeats never count
  toward N. A harness error is a counted failure, never re-run.
- (d) Latency is wall-clock ms on a monotonic clock, from the pipeline run call to the outcome. Warm means every confirm
  trial after the first in a process with the engine initialized.
- (e) Cold is **process-cold with the page cache warm**: force-stop, then start, then engine init plus the first trial's
  latency. A first-ever GPU init with an empty cacheDir is reported but does not gate. A boot-cold run is not done, and
  true cold (rootless page-cache eviction) is impossible. This is a known limitation, recorded as such.
- (f) PSS is in-process `Debug.getMemoryInfo` `totalPss`, sampled every 250 ms during init and generation, with the peak
  taken over the envelope's stages. It is cross-checked once against `adb shell dumpsys meminfo` TOTAL PSS
  (informational; a difference over 10% is flagged).
- (g) Thermal is `PowerManager.getCurrentThermalStatus`, sampled per trial block and every 10 s during sustained runs.
  SEVERE or worse is red.
- (h) Sustained means >= 60 consecutive trials over >= 120 s on the winning cell. Its p50 is compared with the warm p50,
  and sustained trials are excluded from the accuracy N.
- (i) Zero process deaths is proven from `ApplicationExitInfo` for the package since window start, not from missing logs.
- (j) A false write is a negative item whose run hands the gate a mutating proposal.
- (k) The gating shape is **model-chooses** (`forceTool=false`), so negatives can decline. The forced shape is measured
  on a 20-item subset as informational only.
- (l) The winning cell per envelope comes from the screen stage (N=20 per cell). Eligible cells have screen p95 <= the p95
  bar and PSS <= the PSS bar. Among them, pick the most semantic-correct, then the lowest p50. If no cell is eligible,
  pick the lowest p50 among the most semantic-correct (it will go red at confirm). Only E2B cells are eligible.
- (m) Per D-10, these measured rows must each carry a disposition, or the envelope is red with reason
  `unmeasured:<row>`: `rf_enforced`, `rf_enum_nested`, `gpu_adreno730` and `route_ab` for both envelopes, and `kv_reuse`
  for sb.
- (n) Toolchain: both litertlm pins failing to build is red for both envelopes (reason `toolchain`).

## 4. Time-box

One TESTER window of at most **4 hours** wall-clock, starting at the runner's window-start stamp. A shorter granted
window is recorded in the first evidence ENV line (`timebox_s`) and is the binding value. At expiry the runner refuses
further stages, and any gating metric with no evidence makes its envelope red with reason `unmeasured:<metric>`. A ladder
early exit makes the envelope red with reason `early_exit:<code>`.

## 5. Machine block

One line per value, parsed by the verdict code (`THRESHOLD key=value`).

THRESHOLD warm_p50_ms=3000
THRESHOLD warm_p95_ms=5000
THRESHOLD cold_ms=20000
THRESHOLD sustained_p50_ratio_max=1.5
THRESHOLD thermal_red_at=severe
THRESHOLD peak_pss_mb_max=2000
THRESHOLD process_deaths_max=0
THRESHOLD schema_valid_min=0.98
THRESHOLD schema_valid_n_min=100
THRESHOLD semantic_lb_en_min=0.85
THRESHOLD semantic_n_en_min=50
THRESHOLD semantic_lb_es_min=0.80
THRESHOLD semantic_n_es_min=50
THRESHOLD false_writes_max=0
THRESHOLD negatives_n_min=30
THRESHOLD wilson_z=1.959964
THRESHOLD timebox_hours=4
THRESHOLD pss_interval_ms=250
THRESHOLD thermal_interval_s=10
THRESHOLD sustained_min_trials=60
THRESHOLD sustained_min_seconds=120
THRESHOLD screen_n=20
THRESHOLD forced_subset_n=20
THRESHOLD pss_crosscheck_tolerance=0.10

## 6. Lock message (relayed by the driver with the window request in plan 13-08)

D-07 bar locked unchanged in 13-THRESHOLDS.md (committed before any device step); only implicit definitions added: schema-valid is a point estimate over N>=100 (a Wilson bound is unreachable at N=100), cold is process-cold with a warm page cache (true cold is impossible rootless), PSS is in-process totalPss at 250 ms with a one-time dumpsys cross-check.
Time-box: one TESTER window of at most 4 h; any gating metric unmeasured at expiry makes that envelope red (`unmeasured:<metric>`).
Open question 1: who supplies the SB gold labels? Default: the executor authors them privately from the re-pinned SB fixture (digest pinned at authoring, never committed).
Open question 2: does Yahir accept the Gemma 3 1B HF terms? Default: skip the control row, recorded `skipped_gated`.
