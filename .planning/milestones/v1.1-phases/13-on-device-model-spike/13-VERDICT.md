# Phase 13: On-Device Model Spike - Verdict (SPIKE-02)

Computed by code over the committed evidence under the locked thresholds (`13-THRESHOLDS.md`). Nothing here is hand-edited:
every value in the tables is copied from the `SPIKE_VERDICT` lines below, which `scripts/verify-spike-verdict.sh --check`
reproduces from scratch.

## Headline

- **small: RED.** Read by CT 75 (4 domain-free synthetic tools). First reason: `fail:warm_p50` (20,278 ms against a 3,000 ms bar).
- **sb: RED.** Read by SB 179 (19-tool SB-sized envelope, fixture sha8 `8bc739ed`). First reason: `unmeasured:warm_p50`. The SB
  envelope was cut by the 4 h time-box (see Provenance): it reached trial 28 of 80 planned screen trials, so its confirm,
  sustained and exit-reason stages were never run. Per D-07 an unmeasured gating metric is red, so this verdict means
  "not shown to be voice-usable", not "measured and failed". The small envelope, which did run to completion, is a measured fail on
  latency, memory, accuracy and false writes.
- Gemma-2B-class resolved to **Gemma 4 E2B on LiteRT-LM** (D-01; MediaPipe LLM Inference is maintenance-only). The Gemma 3 1B control was
  `skipped_gated` (orchestrator ruling) and can never turn an envelope green.

Accuracy labels for the SB envelope: **VAE-authored labels, SB-reviewed, no objection** (SB reviewed all 144 items; RT-03). The SB
tool count (19) is a reported dimension, not an assumption.

## Machine block (verbatim `scripts/verify-spike-verdict.sh --print`)

```text
SPIKE_VERDICT envelope=small verdict=red reasons=fail:warm_p50,fail:warm_p95,fail:cold,unmeasured:sustained,fail:peak_pss,unmeasured:process_deaths,fail:semantic_en,fail:semantic_es,fail:false_writes cell=e2b.cpu.b.auto warm_p50_ms=20278 warm_p95_ms=28941 cold_ms=28182 sustained_ratio=na thermal_max=moderate peak_pss_mb=2268 deaths=na schema_valid=142/144 en=43/55 en_lb=0.656 es=35/55 es_lb=0.504 false_writes=2/34 kv_reuse=not_reused rf_enforced=ignored rf_enum_nested=enforced gpu_adreno730=gpu_ok route_ab=a:8/40:28632,b:40/40:18887
SPIKE_VERDICT envelope=sb verdict=red reasons=unmeasured:warm_p50,unmeasured:warm_p95,unmeasured:cold,unmeasured:sustained,unmeasured:process_deaths,unmeasured:schema_valid,unmeasured:semantic_en,unmeasured:semantic_es,unmeasured:false_writes cell=e2b.cpu.b.auto warm_p50_ms=na warm_p95_ms=na cold_ms=na sustained_ratio=na thermal_max=none peak_pss_mb=525 deaths=na schema_valid=na en=na en_lb=na es=na es_lb=na false_writes=na kv_reuse=not_reused rf_enforced=ignored rf_enum_nested=enforced gpu_adreno730=gpu_ok route_ab=a:5/20:47432,b:8/8:225707
SPIKE_CONTROL envelope=small model=g3_1b status=skipped_gated cell=none schema_valid=na p50_ms=na peak_pss_mb=na
SPIKE_VERDICT_META thresholds_sha=ec4933fb harness_sha=08ada3366b toolchain=ok pin=0.17.1
SPIKE_VERDICT_CODE sha=e362fb228b
```

## Small envelope (CT 75): winning cell `e2b.cpu.b.auto`, RED

| Metric | Value | D-07 threshold | Result |
|---|---|---|---|
| warm p50 | 20,278 ms | <= 3,000 ms | fail |
| warm p95 | 28,941 ms | <= 5,000 ms | fail |
| cold (process-cold, page cache warm) | 28,182 ms | <= 20,000 ms | fail |
| sustained p50 ratio | na | <= 1.5x warm | unmeasured |
| thermal max | moderate | no SEVERE | pass |
| peak PSS | 2,268 MB | <= 2,000 MB | fail |
| process deaths | na | 0 | unmeasured |
| schema-valid | 142/144 (98.6%) | >= 98% (N >= 100) | pass |
| EN semantic (Wilson LB) | 43/55, LB 0.656 | LB >= 0.85 (N >= 50) | fail |
| ES semantic (Wilson LB) | 35/55, LB 0.504 | LB >= 0.80 (N >= 50) | fail |
| false writes | 2/34 negatives | 0 (>= 30 negatives) | fail |

The small envelope did run to completion for its gating stages (80 screen trials, 164 confirm trials), so its latency, memory,
accuracy and false-write failures are measured. `sustained` and `process_deaths` are unmeasured because those stages sat behind the
time-box.

## SB envelope (SB 179): cell `e2b.cpu.b.auto` (provisional), RED

SB-envelope accuracy is read against **VAE-authored labels, SB-reviewed, no objection**. 19 tools, fixture sha8 `8bc739ed`.

| Metric | Value | D-07 threshold | Result |
|---|---|---|---|
| warm p50 | na | <= 3,000 ms | unmeasured |
| warm p95 | na | <= 5,000 ms | unmeasured |
| cold | na | <= 20,000 ms | unmeasured |
| sustained p50 ratio | na | <= 1.5x warm | unmeasured |
| thermal max | none | no SEVERE | no reason raised (partial: screen trials only) |
| peak PSS | 525 MB | <= 2,000 MB | no reason raised, but see the caveat below (not a valid envelope peak) |
| process deaths | na | 0 | unmeasured |
| schema-valid | na | >= 98% (N >= 100) | unmeasured |
| EN semantic (Wilson LB) | na | LB >= 0.85 (N >= 50) | unmeasured |
| ES semantic (Wilson LB) | na | LB >= 0.80 (N >= 50) | unmeasured |
| false writes | na | 0 (>= 30 negatives) | unmeasured |
| kv_reuse (gating for sb) | not_reused | disposition required | pass (disposition recorded) |

Caveat on the two non-red cells in this table. They come only from the 28 partial screen trials and are not evidence of a pass:

- The verdict code reads peak PSS on the winning cell only. The provisional winner `e2b.cpu.b.auto` (8 of its 20 screen trials ran) never
  produced a memory-peak sample before the time-box, so the 525 MB is its engine-init reading. The other screen cell that did run
  (`e2b.cpu.a.auto`, 20 trials) measured a peak of 4,267 MB, and the prefill and kv_reuse stages measured about 5.5 to 5.7 GB. Read the SB
  memory as "above the 2,000 MB bar on every cell that was actually sampled", not as 525 MB. The verdict code was not changed to say so,
  because the verdict is a function of the committed evidence under the committed code.
- Thermal `none` is the screen-stage reading only.

Informational screen counts (not gating, not the confirm stage): of the 20 positive screen trials, 7 scored correct, identically on
the device and on the host re-score. Schema-valid by route: route A 5 of 20 (p50 47,432 ms), route B 8 of 8 (p50 225,707 ms). On CPU the
SB prompt is prefill-bound (about 4,850 prefill tokens, kv_reuse `not_reused`, time to first token about 212 to 216 s on route B).
GPU prefill is about 920 to 1,270 tokens/s against 22 to 25 tokens/s on CPU, but the ladder walks cells in the order cpu.a, cpu.b,
gpu.a, gpu.b, so the time-box ended inside the second CPU cell and the GPU SB cells were never measured.

## Grading tolerance (RT-03) and the host-side re-score

One grading tolerance was applied, as a tolerance on grading and not a relabel (the gold labels are unchanged):

- The tag-search tool does not fold plurals, so for five SB tag-search plural items a singular-stem query scores as equally correct as
  the plural (one trailing "s" folded on both sides).
- List-item articles ("a scarf" versus "scarf", and the Spanish equivalents) are acceptable either way on SB-envelope items.

Where it lives: `GoldMatcher` in `spike-ondevice` (matcher change commit `e362fb228bd5adc360efef03be6490676e68072d`, which is also the verdict
code SHA above), gated by item id: the plural rule names the five items, and the article rule applies only to ids starting `b_`. Every
other item, including every small-envelope item, is graded strictly as before. Host unit tests (`GoldMatcherTest`, two new cases) cover
the tolerance and its limits; `GoldMatcherTest`, `TrialRunnerTest`, `GoldSetTest` and `SbRescoreTest` pass (0 failures).

Effect, from a host re-score of the saved host-private raw rows with no device run (`SbRescoreTest`, aggregates only):

| Quantity | Value |
|---|---|
| raw SB rows re-scored | 28 (20 positive, 8 negative) |
| positive rows correct, device-scored | 7 |
| positive rows correct, host strict | 7 (host strict matches the device on every row) |
| positive rows correct, host with tolerance | 7 |
| rows changed by the tolerance | 0 |

None of the five plural items fell inside the 28 screen trials, and the article tolerance changed no row, so **no SB number changed**.
Mechanism chosen: because the re-score changes nothing the verdict reads, the verdict code stays over the committed evidence unchanged
(no filtered rescored evidence file is introduced) and the effect is recorded here as a host-side note with aggregates. The raw rows stay
host-private and uncommitted.

The small envelope was scored on the device by the pre-tolerance matcher. The tolerance does not touch it: the small gold uses its own id
space (no id starts with `b_`), has no tag-search tool and no list-item arrays, so every small item takes the strict path and the
small numbers above are exactly what the tolerant matcher would give.

## D-10 empirical rows

| Row | Disposition | What it means |
|---|---|---|
| `kv_reuse` | `not_reused` | A second conversation with an identical preface re-prefills it in full, so the SB-sized prefix pays its whole prefill every turn. |
| `rf_enforced` | `ignored` | `ResponseFormat` did not enforce the flat and bounds schema features (six other features were enforced), so the output schema cannot be trusted to hold. |
| `rf_enum_nested` | `enforced` | Enums and nested objects, the shapes the SB tool surface needs, were enforced. |
| `gpu_adreno730` | `gpu_ok` | The GPU backend initializes and runs on the S22 Ultra (first-ever init about 40 s, repeat about 39 s) and prefills about 40 to 50 times faster than CPU in the prefill stage, but no GPU cell won the small screen (the winner is a CPU cell) and the GPU small cells peaked at 2.5 to 2.7 GB PSS. |
| `route_ab` | small: A 8/40 schema-valid (p50 28,632 ms), B 40/40 (p50 18,887 ms); sb: A 5/20 (p50 47,432 ms), B 8/8 (p50 225,707 ms) | Route A (the model writes a JSON tool wrapper in text) against Route B (the runtime's native tool calls): Route B is the more reliable one on both envelopes and is the small winner; neither route is near the latency bar. |

The one dumpsys-versus-in-process PSS cross-check on record (rf_matrix stage) is 2,463 MB against 2,478 MB, within the 10% tolerance. The
confirm-stage cross-check could not run (the runner holds the lock during a stage); see the 13-08 summary.

## Toolchain and cost (LiteRT-LM 0.17.1)

| Item | Result |
|---|---|
| compile (Kotlin 2.3.20, AGP 9.2.1) | ok |
| dex (min API 35) | ok |
| zipalign, 16 KB page check | ok (`page=16k`) |
| runtime stdlib / reflect / coroutines-android | 2.4.0 / 2.4.0 / 1.11.0; consumer runtime uplift: **yes** (above the consumers' 2.3.20) |
| `liblitertlm_jni.so` arm64-v8a | 21,802,960 B raw, 9,497,418 B deflated, stored uncompressed (`so_stored=yes`) |
| total spike APK | 30,771,293 B |
| compile-classpath leak | `assumed_runtime_only` (confirmed only if 13-11 runs) |

AGP stores the `.so` uncompressed and page-aligned for minSdk >= 23, so the on-disk APK delta is about the raw `.so` size (about
21.8 MB); the compressed Play download delta is nearer the deflated size (about 9.5 MB). The weights are a separate on-device download
and add nothing to APK size.

## Docs-answerable facts (D-10)

- **License:** Gemma 4 E2B is Apache-2.0 and ungated (ai.google.dev/gemma/terms, ai.google.dev/gemma/apache_2). A consumer that
  bundles the converted weights owes the Apache-2.0 section 4(a)-(d) notices (license copy, a marker that the file is a converted
  derivative of `google/gemma-4-E2B-it`, kept attribution, any NOTICE). The Hugging Face repack ships no LICENSE or NOTICE file, so
  the consuming app supplies them. The library itself never ships weights. This is a documentation reading, not legal review.
- **Play 16 KB page-size rule:** updates targeting API 35+ must support 16 KB pages from **2027-02-01** (developer.android.com/guide/practices/page-sizes).
  The vendor `.so` is already aligned and the zipalign check above passed. The S22 is a 4 KB-page device, so this is a consumer
  distribution gate, not a TESTER runtime risk.
- **Consumer stdlib uplift:** yes, 2.4.0 on the consumers' runtime classpath (table above); stdlib is backward compatible, but it is a
  visible change to every consumer's dependency graph and must be stated in any green-path docs.
- **MediaPipe versus LiteRT-LM:** MediaPipe LLM Inference is maintenance-only; LiteRT-LM is the supported path and the only one that
  lists Gemma 4, so Gemma-2B-class resolved to Gemma 4 E2B on LiteRT-LM (D-01).

## Control row (Gemma 3 1B)

`SPIKE_CONTROL envelope=small model=g3_1b status=skipped_gated`: not downloaded or run (orchestrator ruling on the gated Hugging Face
terms). It could never change an envelope's verdict.

## Known limitations

- Cold is **process-cold with a warm page cache**; there is no boot-cold run and true cold (rootless page-cache eviction) is not possible.
- The S22 has no NPU prebuilt, so only CPU and GPU were candidates.
- The SB envelope is **unmeasured by the time-box, not measured-and-failed**: the small envelope took about 2 of the 4 hours, and the SB
  screen reached 28 of 80 trials before the box expired. Remaining SB stages (confirm, sustained, exit reasons) and the small
  sustained and exit-reason stages were refused by the runner; nothing was re-run or extended.
- The SB peak-PSS and thermal cells above come from a provisional winner on a partial screen (see the caveat).

## Provenance

| Item | Value |
|---|---|
| TESTER window | 2026-10-06T01:51:40Z to 2026-10-06T05:52:07Z (time-box 14,400 s; device R5CT10XNKQN, SM-S908U) |
| Thresholds sha | `ec4933fb` (first 8 hex of the committed `13-THRESHOLDS.md`) |
| Harness head that built the APK | `08ada3366b` |
| Verdict code SHA | `e362fb228b` (matcher change commit, last commit touching `spike-ondevice/src`) |
| Toolchain pin | LiteRT-LM 0.17.1 |
| SB envelope | fixture sha8 `8bc739ed`, 19 tools, VAE-authored labels, SB-reviewed, no objection |

Reproduce: `scripts/verify-spike-verdict.sh --check` (prints `SPIKE_VERDICT_CHECK: OK lines=<n>`; recomputes the verdict from
`.planning/phases/13-on-device-model-spike/evidence/` at the code SHA above).
