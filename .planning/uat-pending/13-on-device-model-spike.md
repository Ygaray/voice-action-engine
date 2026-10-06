### Phase 13 — on-device-model-spike (v1.1)

- **Status:** `pending`
- **Milestone:** v1.1 (Grammar, Plan, Router, Undo, Spike, Adapter)
- **Gate 1 self-UAT log:** [`.planning/phases/13-on-device-model-spike/13-SELF-UAT.md`](phases/13-on-device-model-spike/13-SELF-UAT.md) — Verdict: **ALL 4 criteria PASS, evidence-audited** (no fresh device drive: the one TESTER window R5CT10XNKQN, SM-S908U, was consumed 2026-10-06T05:52:07Z; harness APK md5 `445264d6594096eec71d08a47032e9c1` @ `08ada3366b`, verdict code `e362fb228b`, 2026-10-06). Outcome is the measured RED branch: small RED (measured fail), sb RED (unmeasured, 4 h time-box ended at screen trial 28/80); nothing ships; `v1.1.0` not blocked (L10). `scripts/verify-spike-verdict.sh --check` prints `SPIKE_VERDICT_CHECK: OK lines=4`; small aggregates independently recomputed from the raw committed rows.
- **Items covered (4 ROADMAP success criteria):**
  - **SC1 — spike measured on the TESTER in a time-box.** Gemma 4 E2B on LiteRT-LM 0.17.1; small: warm p50/p95 20278/28941 ms, cold 28182 ms, peak PSS 2268 MB, schema-valid 142/144, EN 43/55, ES 35/55, false writes 2/34 (screen 80, confirm 164 trials); APK cost `.so` 21.8 MB raw / 9.5 MB deflated, APK 30.77 MB.
  - **SC2 — verdict relayed.** Reproducible machine verdict; relay body in `13-VERDICT-MESSAGE.md` (handed to the milestone master, `relayed_at 2026-10-06T05:58:59Z`).
  - **SC3 — red branch.** `13-DISPOSITION.md` `branch: red`, `spike03: N/A-deferred`; `spike-ondevice/` removed; `verify-spike-disposition.sh removed` OK.
  - **SC4 — no ML dependency in `:core` / `:providers`.** `verify-repo-hygiene.sh` HYGIENE OK; `verify-ml-denial-controls.sh` OK plants=8; Gradle gates green per the orchestrator.
- **Owner how-to-verify (run at milestone completion; headless, no device needed):**
  1. Read the Gate-1 log above and `13-VERDICT.md`.
  2. Optionally run `bash scripts/verify-spike-verdict.sh --check` (expect `SPIKE_VERDICT_CHECK: OK lines=4`; it uses a low-memory Gradle run in a temp worktree, so do it when the host is quiet).
  3. Acknowledge (or object to) the RED outcome and that the SB envelope is "unmeasured by the time-box", i.e. SB 179 has no on-device latency or accuracy number; a re-measure (GPU SB cells, sustained, process deaths) would need a new TESTER window grant and is not planned.
  4. Confirm the orchestrator received the verdict relay (the repo record is a handoff, not an acknowledgement) and that REQUIREMENTS.md SPIKE-01 / SPIKE-02 get flipped at phase completion.
- **Note:** No physical or device-hardware step is deferred. The device window is consumed and the device was cleaned (cleanup line in `13-08-SUMMARY.md`); this Gate-1 drove no device and ran no adb. The sb `peak_pss_mb=525` / `thermal_max=none` in the machine block are not valid envelope peaks (see the 13-VERDICT.md caveat).
