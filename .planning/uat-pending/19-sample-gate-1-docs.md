### Phase 19 — sample-gate-1-docs (v1.1)

- **Status:** `pending`
- **Milestone:** v1.1 (Grammar, Plan, Router, Undo, Spike, Adapter)
- **Gate 1 self-UAT log:** [`.planning/phases/19-sample-gate-1-docs/19-SELF-UAT.md`](phases/19-sample-gate-1-docs/19-SELF-UAT.md) — Verdict: **ALL 3 criteria PASS** (device SM-S908U TESTER R5CT10XNKQN, APK md5 `662317322d5b804ab0016958770a8927` @ `1869950dca`, 2026-10-07). Observed from the committed plan 19-07 TESTER evidence plus the light static gates and the committed isolated wiring record; not re-driven in the audit run. Caveats C10 (WR-04 source delta) and C11 (wiring judge tightened) are carried to Phase 20.
- **Items covered (3 ROADMAP success criteria):**
  - **SC1 — `:sample` Gate-1 on the TESTER exercises grammar (offline, zero calls), plan, router and undo-all end to end (VER-06).** Five legs, all PASS in one window on the TESTER: `grammar_offline`, `undo_all`, `plan_live`, `router_live`, `responses_probe` (D-13 smoke).
  - **SC2 — README, API.md, INTEGRATION.md and ECOSYSTEM.md cover every new tier, seam and module (DOC-02).** Manifest-driven doc-coverage gate over all five modules; every snippet compiles and runs in `:sample` and `:voice-adapter` tests.
  - **SC3 — an isolated agent wires the library from the docs alone (DOC-02).** Wiring test on the wiring SHA recorded in `19-QUIET-WINDOW.md` (plan 19-14).
- **Owner how-to-verify (run at milestone completion; every item optional, no physical step is required):**
  1. Read each Gate-1 evidence file of plan 07 per criterion: `evidence/gate1-grammar_offline.txt`, `evidence/gate1-undo_all.txt`, `evidence/gate1-plan_live.txt`, `evidence/gate1-router_live.txt`, `evidence/gate1-responses_probe.txt` (each ends in a `VAE_VERDICT ... verdict=PASS` line; grammar and undo legs show `provider_turns=0 attempts=0 tripwire_calls=0`).
  2. Optional, real speech on the TESTER: say a few EN and ES commands against the shipped grammar pack and see whether they match. Gate-1 covered fixed fixtures only (88 of 88 recognized golden forms in Phase 14, plus the `grammar_offline` EN, ES and near-miss cases); real-speech coverage is the Phase 14 caveat and only a human can judge it.
  3. The deferred `submit_plan` cache-prefix number: not measured in v1.1 (see `evidence/gate2-carry-register.txt` C1; SB 177 wants it). Decide whether to commission the fixture-backed leg as a separate gap plan or a later milestone, or to waive it in Phase 20's packet.
  4. Router wording quality: the `router_live` leg passed on one fixture (`picked_index=1`, 901 router tokens). If the pick looks wrong for your real tier descriptions, adjust the descriptions or the wording, which is a behavior change that must happen before the v1.1.0 tag.
- **Note:** No physical or device-hardware step is required. Items 2 to 4 are human-optional judgment calls; the open surface-review items relayed with the cut are listed in `evidence/gate2-carry-register.txt` (C7).
