# Phase 13 verdict relay message (relay body: the 9 lines below)
VAE v1.1 Phase 13 on-device spike verdict (Gemma 4 E2B on LiteRT-LM, S22 TESTER): small=RED sb=RED
small (CT 75, 4 tools): RED. warm p50/p95 20278/28941 ms (bar 3000/5000), cold 28182 ms (bar 20000), peak PSS 2268 MB (bar 2000), schema-valid 142/144 (passes), EN 43/55 LB 0.656 (bar 0.85), ES 35/55 LB 0.504 (bar 0.80), false writes 2/34 (bar 0). First reason fail:warm_p50; sustained and process deaths unmeasured.
sb (SB 179, 19 tools, fixture 8bc739ed): RED because the 4 h time-box ran out at screen trial 28/80, so warm/cold/sustained/schema-valid/EN/ES/false-writes are unmeasured (first reason unmeasured:warm_p50), not measured-and-failed. Accuracy labels: VAE-authored labels, SB-reviewed, no objection. Partial facts: CPU is prefill-bound (~4850 prefill tokens, ~212 s to first token on route B) and the one sampled sb cell peaked at 4267 MB PSS (bar 2000).
D-10 rows: kv_reuse=not_reused; rf_enforced=ignored (flat and bounds schema features), rf_enum_nested=enforced; gpu_adreno730=gpu_ok (GPU prefill ~1240 tok/s vs 22-25 on CPU).
Cost and facts: liblitertlm_jni.so 21.8 MB raw / 9.5 MB deflated (APK on-disk delta ~21.8 MB, stored uncompressed); consumer runtime stdlib/reflect uplift to 2.4.0 (above 2.3.20); Play 16 KB rule starts 2027-02-01, vendor .so is aligned; Gemma 4 E2B is Apache-2.0, a consumer bundling the weights owes the 4(a)-(d) notices.
Meaning: CT 75 reads small (RED, a measured fail), SB 179 reads sb (RED, unmeasured: there is no SB on-device latency or accuracy number to plan against).
Disposition (D-08): red, so nothing ships, SPIKE-03 is N/A-deferred, and v1.1.0 is not blocked (L10).
Grading note (RT-03): plural-stem and list-item-article tolerance added to the host scorer; host re-score of the 28 raw SB rows changed 0 rows (small envelope untouched).
Details: 13-VERDICT.md; reproduce with scripts/verify-spike-verdict.sh --check (SPIKE_VERDICT_CHECK: OK, verdict code sha e362fb228b). No section 11 ledger row was written; the orchestrator owns it.
relayed_to: milestone master, for orchestrator yahir-gsd-control-plane-3b (relay performed by the milestone master from this stage's return notes)
relayed_at: 2026-10-06T05:58:59Z
note: HANDOFF, not a confirmed delivery. The executor cannot message the orchestrator; it handed this body to the milestone master in its stage return notes, and the master performs the relay. relayed_at is the handoff time.
