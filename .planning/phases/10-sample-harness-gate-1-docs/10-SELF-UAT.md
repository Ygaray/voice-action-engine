---
status: partial
result: has_partial
g1_09_disposition: "INCONCLUSIVE(model_filled_optional), ACCEPTED BY EVIDENCE by the orchestrator (2026-10-01); not a PASS"
gate1_tally: "13/14 pass + 1 accepted-inconclusive (G1-09)"
gate: 1
phase: 10-sample-harness-gate-1-docs
source: [ROADMAP Phase 10 SC1-SC4]
device: yahirs-s22-ultra-2 R5CT10XNKQN SM-S908U
apk: sample-debug.apk (md5 4c6fc98c64485ce878e11e60fdf92559 @ 4a586ed7b8)
run: 2026-10-01T22:35Z-22:43Z @ 4a586ed7b8
---

# Self-UAT Log: Phase 10 (sample harness, Gate-1 live legs)

**Device:** TESTER yahirs-s22-ultra-2, USB R5CT10XNKQN, SM-S908U, Android 15 (sdk 35). The wireless address was never used by this run. No emulator, no other phone.
**Build:** `:sample` debug, built from HEAD `4a586ed7b8` (`dirty=0`, `asset_fixture=absent` at build time), apk md5 `4c6fc98c64485ce878e11e60fdf92559`.
**Decision file:** `10-LIVE-LEG-DECISION.md` says `decision: approved` (relayed 2026-10-01). Runbook section 4 followed in full, plus optional L7.
**Driver:** `scripts/run-sample-gate1.sh` (runner) for every non-UI device step; UI driving only via `adb -s R5CT10XNKQN` (`uiautomator dump`, `input tap`, `input text`). Dumps and the two screenshots stayed in the session scratch directory (not committed).
**Wiring test (D-07):** `10-WIRING-TEST.md` status is `pass` (isolated rerun on 36c578f464, `WIRING TEST: PASS checks=9`, 3 minor stumbles carried as an optional Phase 11 doc touch; first run on 338d85ffa3 also passed mechanically).
**Gate-1 tally (finalized 2026-10-01):** 13 of 14 criteria PASS + 1 ACCEPTED BY EVIDENCE (G1-09, INCONCLUSIVE `model_filled_optional`, not a PASS). C4 stays open for the Phase 11 waiver packet.
**Fixture integrity:** the LE-1 fixture was copied by hand from the path named in 10-CONTEXT.md Runtime Decisions to the gitignored `sample/src/debug/assets/sb-a10-fixture.json`. Host sha256 prefix `ebd3ef4a` equals the `FIXTURE_SHA256` constant prefix. Host tools count 18. The asset was removed again after cleanup; nothing from the fixture is in a committed file.
**Spend (G1-13):** `GATE1 LIVE SPEND: requests=11 (anthropic=3 openai=4 openrouter=4) est_cost_usd=0.014 ceiling=33+1` (10 core + 1 optional probe; the probe returned 400 and billed nothing; the cost is the sum of the per-leg `est_usd` in the `VAE_BUDGET` lines, the app's own total reads "unknown" because the probe has no price).

## Arrange notes

- A1 code paths clean (`git status --short core providers keystore sample scripts docs gradle` empty). A2 no pre-existing asset. A3 preflight OK.
- A4 `build-install`: the build and install succeeded, but the runner returned `ERROR reason=install_failed` on every run (see "Tooling defects" below). The APK was installed and the package confirmed present with `pm list packages`; the whitelist step ran inside the runner before its false failure. Build identity recorded above.
- A5 fixture copy done after A4, so G1-01 ran with the fixture genuinely absent from both the APK and the device.
- UI driving note (tester error, not a product defect): my first Save attempts for G1-04 were tapped under the on-screen keyboard (uiautomator bounds are layout bounds, not visible bounds) and one `input text` went to the Anthropic field. Nothing was saved (state stayed Not configured, field cleared with DEL keys), then the sequence was redone with the keyboard dismissed (BACK) before tapping Save.

## Criteria

### 1. G1-01 fixture absent is loud at run time (VER-01 / D-05)
result: passed
- **Rung:** 5 (one downscaled crop for the red colour) on top of 4 and 3
- **Target:** device
- **Expected:** red `FIXTURE ABSENT`; `status_ver02` `REFUSED reason=fixture_absent`; evidence shows a loud `VAE_FIXTURE kind=absent` and the REFUSED verdict; no request spent.
- **Arranged (seeded):** A4 done, no fixture on device (`asset_fixture=absent`, `files/fixture` empty).
- **Did (drove):** launched the app; read `fixture_state`; `capture-start`; tapped `run_ver02`; read `status_ver02`; `capture-save ver02`. Because `VAE_ENV`/`VAE_FIXTURE` are emitted at app start (before `capture-start` clears logcat), the first block holds only the verdict; I force-stopped, `capture-start`, relaunched, tapped `run_ver02` again and saved a second block with all three lines. Both blocks are in the evidence file.
- **Observed:** `fixture_state` = `FIXTURE ABSENT - push the LE-1 fixture (GATE1-RUNBOOK)`, red in the crop; `status_ver02` = `REFUSED reason=fixture_absent`; `budget_used` = `requests 0/33 · optional 0/1` afterwards; second block has `VAE_ENV okhttp=5.2.1 fixture_sha=none ...`, `VAE_FIXTURE kind=absent searched=[...]`, `VAE_VERDICT ... verdict=REFUSED reason=fixture_absent trigger=ui`.
- **Evidence:** `evidence/gate1-ver02.txt` (first two blocks).

### 2. G1-02 fixture present and verified (VER-01 / D-05)
result: passed
- **Rung:** 4
- **Target:** device
- **Expected:** green `Fixture OK`, sha prefix `ebd3ef4a`, `tools=<n>` equals the host count.
- **Arranged (seeded):** A5 (prefix matched); host tools count = 18 (count only).
- **Did (drove):** `push-fixture`; force-stop; relaunch; read `fixture_state`.
- **Observed:** `SAMPLE_GATE1: OK sub=push-fixture fixture_sha=ebd3ef4a target=R5CT10XNKQN`; `fixture_state` = `Fixture OK sha=ebd3ef4a tools=18 source=files` (green in the later screenshot). Nothing longer than the 8-hex prefix appeared.
- **Evidence:** the push-fixture `SAMPLE_GATE1` line above; screen text of `fixture_state`.

### 3. G1-03 OkHttp pin runs on the device (VER-01)
result: passed
- **Rung:** 4
- **Target:** device
- **Expected:** `okhttp 5.2.1` on screen and `okhttp=5.2.1` in `VAE_ENV`.
- **Arranged (seeded):** none.
- **Did (drove):** read `okhttp_version`; read the `VAE_ENV` line in the captured block.
- **Observed:** screen `okhttp 5.2.1`; `VAE_ENV okhttp=5.2.1` in the absent-fixture block and in the cold run block.
- **Evidence:** `evidence/gate1-ver02.txt` (`VAE_ENV` lines).

### 4. G1-04 bring-your-own key through :keystore with a dummy value (VER-01 / D-04)
result: passed
- **Rung:** 4
- **Target:** device
- **Expected:** Ready after Save; still Ready after relaunch; Not configured after Delete; dummy deleted before G1-05.
- **Arranged (seeded):** none (this is the SUT).
- **Did (drove):** typed the dummy string into `key_field_openrouter` (16 masked characters seen), dismissed the keyboard, tapped `key_save_openrouter`; read state; force-stop and relaunch; read state; tapped `key_delete_openrouter`; read state.
- **Observed:** `openrouter: Ready` (last 4 on screen only, not recorded) -> `openrouter: Ready` after relaunch -> `openrouter: Not configured` after Delete. The other two providers stayed Not configured throughout.
- **Evidence:** state words above (no last 4 recorded).

### 5. G1-05 test keys travel through :keystore (VER-01 / D-04 / D-13)
result: passed
- **Rung:** 3
- **Target:** device
- **Expected:** `import_status` Ready deleted=true in_datastore=false for all three; `verify-keys-gone` prints the dir empty; all three Ready after relaunch.
- **Arranged (seeded):** `push-keys` (runner, through `push-test-key`, by file reference; I never read a key, none on an argv).
- **Did (drove):** tapped `import_test_keys`; read `import_status`; `verify-keys-gone`; force-stop and relaunch; read the three `key_state_<p>`.
- **Observed:** `import_status` = `anthropic=Ready deleted=true in_datastore=false; openai=Ready deleted=true in_datastore=false; openrouter=Ready deleted=true in_datastore=false` (last 4 not recorded); `test-keys dir empty`; after relaunch `anthropic: Ready`, `openai: Ready`, `openrouter: Ready`.
- **Evidence:** `SAMPLE_GATE1: OK sub=push-keys providers=anthropic,openai,openrouter target=R5CT10XNKQN` and `SAMPLE_GATE1: OK sub=verify-keys-gone keys_gone=yes`.

### 6. G1-06 VER-02 Anthropic agentic cold run, L1 (VER-02 / D-03)
result: passed
- **Rung:** 3
- **Target:** device
- **Expected:** PASS with >= 2 turns; turn-1 write > 0 and within 7,016 +-5%; later reads equal the write within 1%; every attempt 200; `min_cacheable=4096` and `prefix_chars` in `VAE_ENV`; model claude-haiku-4-5.
- **Arranged (seeded):** G1-02 and G1-05 passed; `cold-stamp check` -> `cold=yes`.
- **Did (drove):** `capture-start`; tapped `run_ver02` (`trigger=ui`); immediately `cold-stamp write` (`epoch=1790894360`); waited for `status_ver02` to leave RUNNING (PASS); `capture-save ver02`.
- **Observed:** `VAE_ENV okhttp=5.2.1 fixture_sha=ebd3ef4a fixture_tools=18 min_cacheable=4096 prefix_chars=21109 est_prefix_tokens=5277`; turn 1 `cache_creation_input_tokens=7016 cache_read_input_tokens=0`; turn 2 `cache_creation_input_tokens=0 cache_read_input_tokens=7016`; both attempts `http=200`; `VAE_VERDICT ... verdict=PASS turn1_write=7016 min_read=7016 calls=2 key_charset=ok trigger=ui`; model claude-haiku-4-5. Write and read are equal (0 percent difference).
- **Evidence:** `evidence/gate1-ver02.txt` (third block).

### 7. G1-07 VER-03 Anthropic single-shot smoke, L2 (VER-03, carry C6)
result: passed
- **Rung:** 3
- **Target:** device
- **Expected:** PASS with exactly one attempt kind=initial http=200, no reshape or retry, tool=edit_item, optional_absent=true.
- **Arranged (seeded):** keys from G1-05.
- **Did (drove):** `capture-start`; tapped `run_smoke_anthropic`; `capture-save smoke_anthropic`.
- **Observed:** one `VAE_ATTEMPT ... n=1 kind=initial http=200`; `VAE_SMOKE tool=edit_item arg_keys=[body,id] optional_absent=true`; `VAE_VERDICT verdict=PASS key_charset=ok trigger=ui`. The `disable_parallel_tool_use` request body is not observable on device; the host goldens named in `evidence/phase-gate.txt` (`SingleShotWireTest.anthropicForcedBodyAsksForOneToolAndCommits`, `AnthropicEncoderTest.aSingleToolCallPutsTheParallelOffSwitchInsideToolChoiceInEveryShape`) assert it, and the orchestrator accepted host golden plus this live 200 on the first attempt as sufficient proof.
- **Evidence:** `evidence/gate1-smoke_anthropic.txt`.

### 8. G1-08 VER-03 OpenAI single-shot smoke, L3 (VER-03)
result: passed
- **Rung:** 3
- **Target:** device
- **Expected:** PASS, optional_absent=true, model gpt-5.4-mini.
- **Arranged (seeded):** keys from G1-05.
- **Did (drove):** `capture-start`; tapped `run_smoke_openai`; `capture-save smoke_openai`.
- **Observed:** `http=200`, model gpt-5.4-mini, `optional_absent=true`, `verdict=PASS key_charset=ok`.
- **Evidence:** `evidence/gate1-smoke_openai.txt`.

### 9. G1-09 VER-03 OpenRouter single-shot smoke, L4 (VER-03, carry C5)
result: partial
- **Rung:** 3
- **Target:** device
- **Expected:** PASS (C5 closes) or INCONCLUSIVE `model_filled_optional` with one rerun; still INCONCLUSIVE means partial and C5 stays carried.
- **Arranged (seeded):** keys from G1-05.
- **Did (drove):** `capture-start`; tapped `run_smoke_openrouter`; `capture-save`; reran once (the second run used `prompt_variant=1`, the stronger prompt); `capture-save` again.
- **Observed:** both runs: `http=200`, model openai/gpt-5.4-mini, `arg_keys=[body,id,tags,title]`, `optional_absent=inconclusive`, `verdict=INCONCLUSIVE reason=model_filled_optional key_charset=ok`. The transport, the key and the tool call all worked; the model filled the optional fields on both prompts, so the "optionals omitted" claim cannot be shown. This is the INCONCLUSIVE outcome the runbook maps to `partial`.
- **Disposition (orchestrator yahir-gsd-control-plane-f2, relayed by the milestone master, 2026-10-01): accept-C5.** Recorded as INCONCLUSIVE(model_filled_optional), ACCEPTED BY EVIDENCE, NOT a PASS. Evidence for acceptance: the shared `ChatCompletionsProvider`/`ChatVendor` decoder path passed the live OpenAI omitted-optional check (G1-08), and host tests prove no default-filling. The criterion `result` stays `partial` (it was not demonstrated live on OpenRouter); the acceptance is an orchestrator disposition, listed in the Phase 11 waiver packet next to C4.
- **Evidence:** `evidence/gate1-smoke_openrouter.txt` (two blocks).

### 10. G1-10 VER-03 extended multi-turn on OpenAI Chat, L5 (VER-03)
result: passed
- **Rung:** 3
- **Target:** device
- **Expected:** PASS: at least 2 calls, find_items on turn 1, tool result replayed, final answer, all attempts 200.
- **Arranged (seeded):** keys from G1-05.
- **Did (drove):** `capture-start`; tapped `run_multi_openai`; `capture-save multi_openai`.
- **Observed:** turn 1 `tools=[find_items] stop_reason=tool_use`, turn 2 `stop_reason=end_turn reply_len=39`, both `http=200`, `verdict=PASS calls=2 turn2_cache_read=0 key_charset=ok`.
- **Evidence:** `evidence/gate1-multi_openai.txt`.

### 11. G1-11 VER-03 extended multi-turn on OpenRouter, L6 (VER-03)
result: passed
- **Rung:** 3
- **Target:** device
- **Expected:** PASS; record `turn2_cache_read` (observed, not asserted). Cache-write accounting through OpenRouter (carry C4) is not exercised.
- **Arranged (seeded):** keys from G1-05.
- **Did (drove):** `capture-start`; tapped `run_multi_openrouter`; `capture-save multi_openrouter`.
- **Observed:** turn 1 `find_items`, turn 2 `end_turn`, both `http=200`, `verdict=PASS calls=2 turn2_cache_read=0 key_charset=ok`. `turn2_cache_read=0` (observed). Carry C4 (cache-write via router): not exercisable in v1.0 (LATER-02); needs Yahir waiver; P11 waiver packet. It is not closed.
- **Evidence:** `evidence/gate1-multi_openrouter.txt`.

### 12. G1-12 VER-04 clarification and partial on device, offline (VER-04 / D-14)
result: passed
- **Rung:** 4
- **Target:** device
- **Expected:** `clarify_question` and two `clarify_option_*` buttons; after choosing `list-b` the follow-up completes; the partial demo reads "Did 1 action(s), couldn't finish"; no request spent.
- **Arranged (seeded):** none (offline demo provider).
- **Did (drove):** tapped `run_demo_clarify`; confirmed `clarify_question` and `clarify_option_list-a` / `clarify_option_list-b`; tapped `clarify_option_list-b`; read `readout`; `capture-save demo_clarify`; `capture-start`; tapped `run_demo_partial`; read `readout`; `capture-save demo_partial`.
- **Observed:** readout "Needs your answer" with the question and both options; after the option: "Done: 1 committed - Added it to the chosen list."; partial demo readout "Did 1 action(s), couldn't finish". Evidence: clarify block has a terminal `ask_user` outcome then a follow-up `executed=1 committed=1` completed; partial block has `partial=true executed=1 committed=1`. `budget_used` unchanged by the demos.
- **Evidence:** `evidence/gate1-demo_clarify.txt`, `evidence/gate1-demo_partial.txt`.

### 13. G1-13 bounded spend (Runtime Decision)
result: passed
- **Rung:** 3
- **Target:** device
- **Expected:** core requests <= 33 and optional <= 1; one spend line reported.
- **Arranged (seeded):** none.
- **Did (drove):** read `budget_used` and the last `VAE_BUDGET` lines after the live legs.
- **Observed:** `budget_used` = `requests 10/33 · optional 1/1 · est USD unknown`; last `VAE_BUDGET core=10 optional=1 anthropic=3 openai=4 openrouter=4`. Summed per-leg `est_usd` = 0.014. `GATE1 LIVE SPEND: requests=11 (anthropic=3 openai=4 openrouter=4) est_cost_usd=0.014 ceiling=33+1`.
- **Evidence:** `VAE_BUDGET` line in `evidence/gate1-responses_probe.txt`.

### 14. G1-14 cleanup (D-04)
result: passed
- **Rung:** 3
- **Target:** device
- **Expected:** keys directory empty; `cleanup` prints `sample package removed`; `git status --short` shows only the expected new files; no fixture left behind.
- **Arranged (seeded):** none.
- **Did (drove):** `verify-keys-gone`; `cleanup`; removed the gitignored fixture asset; `git status --short`.
- **Observed:** `test-keys dir empty` / `SAMPLE_GATE1: OK sub=verify-keys-gone keys_gone=yes`; `sample package removed` / `SAMPLE_GATE1: OK sub=cleanup target=R5CT10XNKQN`; `sample/src/debug/assets/` removed; the only new tracked-area files are `evidence/gate1-*.txt` (9 files).
- **Evidence:** the two `SAMPLE_GATE1` lines above.

## Optional L7 (not a criterion): Responses-only probe
- Ran after G1-11 with optional budget 0. `status_responses_probe` = `CAPTURED reason=http_error`; evidence `VAE_ATTEMPT ... provider=openai ... http=400`, `VAE_VERDICT verdict=CAPTURED reason=http_error http=400`, model gpt-6-astra, no tokens billed. Carry C3 disposition: captured with http=400 and reason=http_error recorded. The 400 body text is not captured (LE-7), so the exact RESPONSES_ENDPOINT_MARKER wording is not visible on device; the reason code shows the generic `http_error` path.
- **Evidence:** `evidence/gate1-responses_probe.txt`.

## Tooling defects (diagnosed, not fixed; none changes a criterion verdict)

1. `scripts/run-sample-gate1.sh` `package_installed()` (line 177-179) is `adbt shell pm list packages 2>/dev/null | strip_cr | grep -qxF "package:$PKG"` under `set -o pipefail`. `grep -q` exits at the first match and adb gets SIGPIPE, so the pipeline returns 141 even when the package is present. Result: `build-install` always ends `ERROR reason=install_failed` after a successful install, and `preflight` reports `installed=no` when the package is installed (reproduced in a standalone copy of the three functions: 141 four times out of four, count of matches 1). `cleanup` is not affected (it captures the listing in a variable first). Fix belongs to gap-closure: capture the listing into a variable, then grep it (the form `cleanup` already uses).
2. Runbook G1-01 ordering: `VAE_ENV` and `VAE_FIXTURE` are emitted at app start, but the runbook says launch, then `capture-start`, which clears them. Workaround used: `capture-start` then a cold relaunch. Suggest the runbook say "capture-start, then force-stop and launch".
3. Minor: the runbook UI notes do not say that some `run_*` / `clarify_option_*` buttons sit under the gesture-nav bar; the centre tap hits the nav bar (Recents opened once). Scroll the page first.

## Gate-2 carry dispositions (C1-C7)

- C1 Billing mapping: NOT EXERCISED (not triggerable without draining credit); unit tests named in the register.
- C2 key charset: proven per provider by `key_charset=ok` after a 200: Anthropic (G1-06, G1-07), OpenAI (G1-08, G1-10), OpenRouter (G1-11).
- C3 Responses-only 400: L7 ran, CAPTURED http=400 reason=http_error recorded (wording not visible under LE-7).
- C4 OpenRouter cache-write: not exercisable in v1.0 (LATER-02); needs Yahir waiver; P11 waiver packet. Not closed. `turn2_cache_read=0` observed in G1-11.
- C5 OpenRouter EDIT-shaped call omitting optionals: INCONCLUSIVE `model_filled_optional` after one rerun (G1-09), ACCEPTED BY EVIDENCE by the orchestrator (2026-10-01), not a PASS. Listed in the Phase 11 waiver packet next to C4.
- C6 `disable_parallel_tool_use`: host golden plus live 200 on first attempt (G1-07), accepted by the orchestrator.
- C7: not applicable (decision approved).
