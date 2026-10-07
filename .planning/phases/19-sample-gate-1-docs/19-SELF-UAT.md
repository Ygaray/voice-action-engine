---
status: complete
result: all_pass
gate: 1
phase: 19-sample-gate-1-docs
source: [19-ROADMAP success criteria SC1-SC3]
device: SM-S908U TESTER (R5CT10XNKQN) - committed evidence from the plan 19-07 window, NOT re-driven in this run
apk: app-debug.apk (md5 662317322d5b804ab0016958770a8927 @ 1869950dca)
run: 2026-10-07T18:55Z
---

<!--
Self-UAT log for gsd-verify-work-agentic (autonomous Gate-1 behavioral self-UAT), Phase 19.
The `---` frontmatter above is the first bytes of the file (GSD-native vocabulary for scanUatGaps / evaluateUatPassed).
-->

# Self-UAT Log - Phase 19 (Sample Gate-1 and Docs)

**Device:** SM-S908U (sdk 35), serial R5CT10XNKQN, as recorded in the evidence headers (`target=R5CT10XNKQN`) and `19-07-SUMMARY.md`
**APK:** app-debug.apk (md5 662317322d5b804ab0016958770a8927 @ 1869950dca), installed once for the whole plan 19-07 window
**Run:** 2026-10-07T18:55Z (this audit). Device window of record: 2026-10-07T16:03:52Z to 16:12:07Z (plan 19-07).
**Pre-flight:** not applicable in this run. By binding orchestrator ruling the device-side Gate-1 had ALREADY run (plan 19-07, one window, one install, `trigger=ui`, relayed live-spend GO consumed). This run touched no device or emulator (no adb), read or pushed no key, made no provider or network call, built or installed no APK, ran no Gradle and killed no process.
**Unit suite:** not run here (no Gradle, by ruling). The light static gates below were run instead.
**Coverage/Nyquist:** `DOC COVERAGE OK checks=32 types=119` (rung 2 for SC2).
**Seed/fixture integrity:** the legs seed their own world per run (a fresh `ItemStore` / `UndoWorld` per sub-case, scripted offline provider for undo, synthetic words only); no device fixture, no shared state. Seeding method for the undo and grammar legs: programmatic, in-app (leg code), not via the UI.

## Target and observation layer (honest statement)

- **Target:** committed TESTER evidence from the plan 19-07 window (device R5CT10XNKQN as recorded in the evidence headers). Not re-driven in this run.
- **Observation layer:** committed on-device evidence files (`evidence/gate1-*.txt`, closed-vocabulary `VAE_*` lines produced by the sample's own `EvidenceLine` writer and passed through the evidence filter) plus the light static gates. This is rung 3 (headless data/log checks) for SC1 and rung 2 to 3 for SC2 and SC3. No structure tree or screenshot was taken in this run, and none was needed: every SC1 claim is a data/trace claim, not a visual one.
- **Re-derivation:** expected behavior below was re-derived from `ROADMAP.md` Phase 19 success criteria, not from `19-VERIFICATION.md` or the SUMMARY claims. Each verdict line was then checked against the verdict code (`sample/src/main/kotlin/.../legs/GrammarLeg.kt`, `PlanLegs.kt`, `UndoLeg.kt`, `evidence/EvidenceLine.kt`) so that a `verdict=PASS` is shown to be earned by the printed numbers, not merely asserted.

## Build identity and delta audit

- Installed build: `head=1869950dca`, `apk_md5=662317322d5b804ab0016958770a8927`, `dirty=0` (19-07-SUMMARY.md).
- Evidence headers carry two heads: `1869950dca` (grammar_offline, undo_all) and `5e376f4ee6` (plan_live, router_live, responses_probe). `5e376f4ee6` is the checkout HEAD at capture time. Verified: `git diff --stat 1869950dca 5e376f4ee6` touches only three `.planning` files (the runbook and the two evidence files), so no code changed under the install between the two heads.
- Source delta from the installed build to the current HEAD (`7a276ffd44`) in `core providers keystore undo voice-adapter sample scripts`: `ItemToolExecutor.kt` (WR-04), `PlanLegTest.kt`, `DocSnippetsTest.kt`, `DocSnippetAdapterTest.kt`, and scripts (`agent-wiring-test.sh`, `review-api-surface.sh`, `run-sample-gate1.sh`, `verify-docs-coverage.sh`, `verify-sample-device-guard.sh`). Only `ItemToolExecutor.kt` is shipped sample code on the device path (see caveat C10).

## Static gates run in this run (all exit 0)

```text
bash scripts/verify-docs-coverage.sh            -> DOC COVERAGE OK checks=32 types=119
                                                   (NOTE C23: README pins v1.1.0, tag does not exist yet; pre-tag allowance)
bash scripts/verify-sample-device-guard.sh      -> SAMPLE DEVICE GUARD OK scenarios=43
bash scripts/verify-stt-confinement.sh          -> STT CONFINEMENT OK checks=6
bash scripts/verify-module-manifest.sh          -> MODULE MANIFEST OK modules=core,providers,keystore,undo,voice-adapter
bash scripts/agent-wiring-test.sh selftest-source -> WIRING SOURCE SELFTEST OK
```

## Criteria

### 1. SC1 - `:sample` Gate-1 on the TESTER runs grammar (offline, zero calls), plan (second step uses first step's new id), router (start tier visible in trace) and "Undo all (N)" end to end, each with a logged evidence line (VER-06)
result: passed
- **Rung:** 3 (committed on-device evidence, closed-vocabulary log lines; no visual claim, so no capture)
- **Target:** committed TESTER evidence, device R5CT10XNKQN, plan 19-07 window (not re-driven in this run)
- **Expected:** (a) a grammar command under offline-only completes with zero provider turns, zero HTTP attempts and zero tripwire calls, and a near miss is handed on and ends unhandled and capped by policy; (b) a PlanThenExecute command commits two steps where the second item is created under the first item's newly issued id; (c) a router-chosen start tier shows in the trace (a selection with at least two eligible model tiers and a usable pick at index >= 1 that the walk actually starts at); (d) "Undo all (N)" reverts a whole multi-action command (N = 3 actions, all restored), with the refusal and partial-completion sub-cases behaving loudly.
- **Arranged (seeded):** in-app, programmatic, by the leg code in plan 19-07's window: fresh `ItemStore` seeded with one item (`seed`) per undo sub-case; a scripted offline `UndoScriptProvider` for the undo leg; a tripwire provider registered in the grammar leg's engine; test keys pushed by the runner for the live plan and router legs and verified gone afterward (`verify-keys-gone keys_gone=yes`, per 19-07-SUMMARY.md). Nothing was seeded by this run.
- **Did (drove):** nothing in this run (ruling: no device). In the 19-07 window (recorded in `19-07-SUMMARY.md` and `19-GATE1-RUNBOOK.md`) the legs were driven through the real sample UI by resource id, `trigger=ui` on every verdict line. This run audited that evidence and the verdict logic.
- **Observed (falsification, per leg):**
  - **grammar_offline** (`evidence/gate1-grammar_offline.txt`): all three `VAE_TRACE` lines read `provider_turns=0 attempts=0 tripwire_calls=0 tiers_run=1`. Case 1 EN `kind=completed matched_lang=en`, case 2 ES `kind=completed matched_lang=es`, case 3 near miss `kind=unhandled capped=true`. The model tier was skipped by policy (`codes=[tier_skipped_policy]`) in all three, i.e. the offline-only policy, not luck, kept the provider out. Verdict `en=1 es=1 near_miss_capped=1 provider_turns=0 attempts=0 tripwire_calls=0`. Cross-check against `GrammarLeg.judge`: any non-zero turn, attempt or tripwire count returns FAIL (`provider_called`, `http_attempted`) before the language checks, and the near miss must be `Unhandled` with `cappedByPolicy`; the printed numbers satisfy every rule. Zero is proven three independent ways (listener turns, request-tap attempts, tripwire calls), and the leg did not use airplane mode.
  - **plan_live** (`evidence/gate1-plan_live.txt`): one live Anthropic round trip (`claude-haiku-4-5`, `tools=[submit_plan]`, `http=200`), outcome `completed partial=false executed=2 committed=2`, verdict `committed=2 bound=1 remaining=0 replanned=0`. `PlanLegs.judgePlan` derives `bound` from the STATE of the stateful store (`items.size == 2 && items[1].parentId == items[0].id`), not from a log claim, and fails first on `PLAN_BINDING_UNRESOLVED`; `bound=1` with no replan means the second step's reference resolved to the first step's newly issued id on the first plan.
  - **router_live** (`evidence/gate1-router_live.txt`): trace `sel=picked eligible=2 picked_index=1 first_model_index=1 bypassed=1 sel_turns=1 tiers_run=2 provider_turns=2`, the router turn `tools=[pick_start_tier]` (877 in, 24 out = 901 router tokens, matching `router_tokens=901`), then the plan tier ran (`submit_plan`) and committed 2. `judgeRouter` requires `eligible == 2`, `pickedIndex == 1` (an index-0 pick is INCONCLUSIVE `picked_first`), the first model tier that ran equals the pick, exactly one selection turn, and the router tokens are counted inside the trace usage (901 < total 2168). All hold. The router chose the plan tier (the multi-step description) and the single-shot tier was bypassed.
  - **undo_all** (`evidence/gate1-undo_all.txt`): case 1 `phase=counted n=3 committed=3 pending=0`, then `phase=undone result=complete restored=3 store_ok=true` (the store equals the pre-command snapshot). Case 2 `result=refused blockers=1 reason=changed_since store_ok=true` after an out-of-band edit of the seeded item (refuse loudly, store untouched). Case 3 PlanThenExecute partial `n=2 committed=2 pending=1 partial=true remaining=1`, then `result=complete restored=2 store_ok=true`; the held proposal is shown apart and is not in N. Verdict `n=3 restored=3 blockers=1 partial_n=2 pending=1`. `UndoVerdicts.judge` encodes exactly these numbers (`EXPECTED_WHOLE_N=3`, `EXPECTED_PARTIAL_N=2`, refusal must be `changed_since` with one blocker and an untouched store). N is read from `journal.group(key).count`, not from the UI string.
  - **responses_probe** (D-13 smoke, additional to the four ROADMAP legs): `kind=failed reason=model_unsupported http=400`, typed `ModelUnsupported`, not `http_error`; this re-confirms the Phase 12 W04 fix on the real device.
  - **Spend discipline:** `VAE_BUDGET` shows 4 live requests (plan 1, router 2, probe 1; est USD 0.00275 plus an unbilled HTTP 400) of the 16 request ceiling, and the grammar and undo legs made zero calls, as the relayed GO required.
- **Not independently evidenced in the committed files (audit note):** the on-screen text `Undo all (3)` of the `undo_label` is recorded only in `19-07-SUMMARY.md` (the runbook required reading it before the second press); the evidence files carry the count it is built from (`n=3`) but not the label string. Judged immaterial: the criterion's substance (a whole multi-action command reverted, N counted) is proven by `n=3 restored=3 store_ok=true`.
- **Evidence:** `.planning/phases/19-sample-gate-1-docs/evidence/gate1-grammar_offline.txt`, `gate1-undo_all.txt`, `gate1-plan_live.txt`, `gate1-router_live.txt`, `gate1-responses_probe.txt`; `19-07-SUMMARY.md`, `19-GATE1-RUNBOOK.md`, `19-LIVE-LEG-DECISION.md` (`decision: consumed`).

### 2. SC2 - README, API.md, INTEGRATION.md and ECOSYSTEM.md cover every Phase 12-18 tier, seam and module (plus the on-device module if Phase 13 shipped one), with the new per-module coordinates, and the doc-coverage check passes (DOC-02)
result: passed
- **Rung:** 2 (coverage gate run in this run) plus targeted text spot-checks
- **Target:** headless (repo working tree at HEAD `7a276ffd44`)
- **Expected:** every new tier (LocalGrammar + GrammarPack, PlanThenExecute, router/Custom start-tier selection), seam (`onFailed`, `ReasoningMode`, `carryIn`, `cappedByPolicy`, `providerCallId`, `DelicateKeyAccess`, `claude-sonnet-5`, `ModelUnsupported`) and module (`undo`, `voice-adapter`) is documented, the per-module coordinates are present, and the gate passes. Phase 13 shipped no module, so none is owed.
- **Arranged (seeded):** none
- **Did (drove):** ran `bash scripts/verify-docs-coverage.sh` (exit 0, `DOC COVERAGE OK checks=32 types=119`), `bash scripts/verify-module-manifest.sh` (exit 0, five modules), `bash scripts/verify-stt-confinement.sh` (exit 0, `checks=6`), `bash scripts/verify-sample-device-guard.sh` (exit 0, `scenarios=43`).
- **Observed (falsification):** independent of the gate, grep counts across the four docs show the terms are really present: `LocalGrammarStrategy` (API 3, INTEGRATION 3), `GrammarPack` (API 4, INTEGRATION 5), `PlanThenExecuteStrategy` (API 5, INTEGRATION 3), `TierSelector.Router` (API 2, INTEGRATION 3), `TierSelector.Custom` / `StartTierPicker` (API 2-3, INTEGRATION 1-2), `UndoJournal` (API 5, INTEGRATION 11), `onFailed` (API 5), `ReasoningMode` (API 2), `carryIn`, `cappedByPolicy`, `providerCallId`, `DelicateKeyAccess` (API 4, INTEGRATION 4), `claude-sonnet-5` (INTEGRATION 2), `ModelUnsupported`. The per-module coordinates `voice-action-engine-undo` and `voice-action-engine-voice-adapter` appear in README, API, INTEGRATION and ECOSYSTEM. README links the grammar tier, plan tier, router and "Undo all (N)". ECOSYSTEM.md states the on-device spike shipped no module, which matches the Phase 13 verdict (red branch, SC3 green clause N/A). Honest limit: this rung proves presence and gate coverage, not prose quality; quality is the SC3 judgment below. Note the gate carries an explicit pre-tag allowance (C23/C24: README and ECOSYSTEM announce v1.1.0 before the tag exists); Phase 20 must run it with `VAE_DOCS_REQUIRE_PINNED_TAG=1` after tagging (carry C11).
- **Evidence:** command output above (this run); `scripts/verify-docs-coverage.sh`; `19-10-SUMMARY.md` and `19-09-SUMMARY.md` (compiled, executing doc snippets in `DocSnippetsTest` and `DocSnippetAdapterTest`, not re-run here because no Gradle).

### 3. SC3 - A fresh agent wires grammar, plan, the router and undo-all into a new app from the docs alone, and the isolated wiring test passes (re-run on the final SHA in Phase 20) (DOC-02)
result: passed
- **Rung:** 3 (committed isolated-run record, source selftest re-run)
- **Target:** headless; the isolated fresh-agent run itself is the committed `.planning/releases/v1.1.0/WIRING-RERUN.md` (plan 19-14), not re-dispatched here
- **Expected:** an isolated agent given only README, API, INTEGRATION and ECOSYSTEM wires all four surfaces plus the keystore into a new app and the judge passes; the pass is not voided by later doc edits.
- **Arranged (seeded):** none in this run (the original run prepared a workspace under `/tmp/vae-wiring-19/ws` with docs, skeleton and `TASK.md` only, and a throwaway config dir).
- **Did (drove):** read `WIRING-RERUN.md`; re-ran `bash scripts/agent-wiring-test.sh selftest-source` (exit 0, `WIRING SOURCE SELFTEST OK`); checked `git diff --stat 090fd8ec76 HEAD -- README.md API.md INTEGRATION.md ECOSYSTEM.md` (empty) and `git status` on the four files (clean).
- **Observed (falsification):** record says `status: pass`, `tested_sha: 090fd8ec76` (the last commit that changes anything outside `.planning/`), `verify_line: "WIRING TEST: PASS checks=13"`, `consulted_only_workspace: true`, `ancestors_clean: yes`, `cfg_removed: yes`; the agent's own `:jvmconsumer:test :app:compileDebugKotlin` was green with six `WireTest` tests (grammar, plan, router, undo-all, keystore). The four docs have NO diff since the tested SHA, so the "any docs edit voids the pass" rule is not triggered. Stumbles are in the record, not hidden: five doc gaps (router answer tool/arg shape `pick_start_tier`/`{"tier":...}` guessed; `submit_plan` argument schema undocumented; undo-bridge block lists no imports; SingleShot read-tool intent unclear; `store` shadowed inside the `UndoJournal { }` builder). They did not stop the wiring, but they mean the docs are not frictionless; they are carried to Phase 20 as doc-fix input (C9) and fixing them voids this pass, so Phase 20 must re-test on its final SHA (C4), which is exactly what the criterion's parenthetical requires.
- **Caveat C11 (reported, not hidden):** the wiring judge (checks W5/W6/W10-W13) was tightened after the pass (WR-03, commits `9a47c71`, `2b694e8`). The `090fd8ec76` pass was judged by the PRE-fix judge and the agent workspace was already removed, so the agent's solution was not re-judged by the tightened judge. `selftest-source` (re-run now, passes) and the clean-clone selftest only prove the tightened judge is self-consistent against the reference solution, not that this particular agent output passes it. Judged not to withhold the Phase 19 PASS: the criterion defers the authoritative re-run to Phase 20's final SHA, which uses the tightened judge and is a hard gate (`release-cut.sh gate wiring`).
- **Evidence:** `.planning/releases/v1.1.0/WIRING-RERUN.md`, `.planning/releases/v1.1.0/evidence/wiring-stumbles.txt`, `.planning/releases/v1.1.0/evidence/wiring-consulted.txt`, `19-QUIET-WINDOW.md`; `bash scripts/agent-wiring-test.sh selftest-source` (this run).

## Summary

total: 3
passed: 3
partial: 0
failed: 0
infra: 0

## Notes / anomalies (for the Gate-2 reviewer)

- **Not re-driven:** by orchestrator ruling this run did not touch the TESTER. Every SC1 verdict rests on the committed plan 19-07 evidence plus code-level cross-checks of the verdict logic; nothing in this run was observed first-hand on a device.
- **C10 (WR-04 build delta):** review fix `d6647a1` (`ItemToolExecutor.kt`: an explicit `parent_id` JSON null is treated as absent) postdates the installed build `1869950dca`. It is a four-line source-only change that relaxes an input case (previously an explicit null would have been read as a bad parent). The evidenced plan, router and undo paths all passed a non-null step reference and committed `bound=1`, so none of them exercised the changed branch and the five PASS legs are unaffected. Accepted by the orchestrator, carried: the sample must be rebuilt and reinstalled from the final SHA before any further live TESTER window (Phase 20's Gate-1 delta check will show this file).
- **C11:** wiring judge tightened after the pass (detail under SC3).
- **Mixed `head=` in evidence headers** (`1869950dca` vs `5e376f4ee6`): explained above (capture-time checkout HEAD; only `.planning` files differ).
- **Gate-2 items (human-optional, already registered):** real EN/ES speech against the shipped grammar pack; router wording quality beyond one fixture (`picked_index=1`, 901 tokens); the C1 `submit_plan` cache-prefix number, explicitly DEFERRED with no measurement in v1.1 (never claimed as proven here). Registered in `.planning/uat-pending/19-sample-gate-1-docs.md`; the shared `HUMAN-UAT-PENDING.md` was not touched.
- **Other carries (not Gate-1 blockers):** C2 live JitPack probe of the pushed SHA, C4 wiring rerun on the final SHA, C5 `release-cut.sh` touched in Phase 19, C6 superseded voice-adapter overload counts, C7 open surface-review items, C9 wiring doc stumbles. All owned by Phase 20 or the orchestrator in `evidence/gate2-carry-register.txt`.
- No process was killed, no Gradle was run, no key was read, no network call was made, and no code was edited.

## Findings routed to gap-closure (if any)

- None. No genuine behavior FAIL was found; no INFRA condition arose.

## Verdict

All 3 criteria PASS at the observation layer permitted by the ruling (committed TESTER evidence from the 19-07 window plus the light static gates and the committed isolated wiring record). Gate-1 complete; human Gate-2 deferred to milestone completion (registered in `.planning/uat-pending/19-sample-gate-1-docs.md`). Caveats C10 and C11 are carried to Phase 20 and do not downgrade the verdict.
