# Phase 10 Gate-1 runbook (VER-01..VER-04)

Audience: `gsd-agentic-tester` only. One device tester at a time. The executor of plan 10-10 wrote this file and never touched a device.
Target: the TESTER `yahirs-s22-ultra-2`, USB serial `R5CT10XNKQN`, SM-S908U, Android 15.
App: `:sample` (debug build, never published), driven through its real UI.

## 0. Read first

1. `.planning/phases/10-sample-harness-gate-1-docs/10-LIVE-LEG-DECISION.md`. It says `decision: approved` or `decision: deferred`, and carries the bounded budget. `approved` means follow section 4 in full. `deferred` means follow section 5. If the file is missing or the line is anything else, treat it as deferred. The runner's `push-keys` enforces the same rule by itself.
2. `~/.claude/context/devices/common.md`, `~/.claude/context/devices/test-android.md` and `~/.claude/context/workflows/test-keys.md`. Also `~/.claude/context/workflows/two-gate-uat.md` for how Gate-1 and Gate-2 relate.
3. `.planning/phases/10-sample-harness-gate-1-docs/10-WIRING-TEST.md`. Its frontmatter `status` (currently `pending-rerun`, mechanical verdict PASS on the tested SHA, rerun on the final SHA still owed before Phase 11) goes into the SELF-UAT as a one-line note (section 6).
4. `.planning/phases/10-sample-harness-gate-1-docs/evidence/gate2-carry-register.txt` (carries C1-C7). Every carry gets an observed disposition in the fragment.

## 1. Hard rules

- TESTER only: USB `R5CT10XNKQN`. The wireless address `100.118.21.106:1496` is used only through the runner's own guard. The personal phone (`yahirs-s22-ultra`, `100.126.94.47`) is never touched.
- Every device step goes through `scripts/run-sample-gate1.sh <subcommand>`. The only exception is UI driving (`uiautomator dump`, `input tap`, `input text`, `screencap`), which always uses `adb -s R5CT10XNKQN`.
- There is no emulator fallback: every criterion here is `device-hw:` because the keys are TESTER-only by policy. If the TESTER is unreachable, wait about 1 minute for the adb watchdog, retry once, then record INFRA for every remaining criterion and stop. A TESTER outage is recorded as INFRA after one retry, the TESTER is not substituted: no emulator, no other phone.
- Never read `~/.config/test-keys`. Never put a key on any command line. Never type a real key with `input text`. The only text you type is the non-secret dummy string in G1-04.
- Never commit uiautomator dumps, screenshots that show model text, a real key's last 4, the fixture, or any prompt text.
- LE-7 evidence rules (master, 2026-10-01): evidence contains no fixture content, no fixture tool names and no fixture sha suffix. The fixture sha is quoted by its prefix `ebd3ef4a` only. Evidence uses the closed vocabulary of the runner's filter (counts, status words, reason codes, fingerprints).
- No airplane mode is needed. Never uninstall or clear app data between legs: the in-app request counter lives in app storage and is what enforces the spend ceiling.
- Evidence files are `.txt` (`*.log` is gitignored).
- Phase 10 creates no tag, no `api.txt`, no release script (D-08..D-12 belong to Phase 11).

## 2. UI driving

- Compose tags are exposed as uiautomator `resource-id` (`testTagsAsResourceId`). Find a node with `adb -s R5CT10XNKQN shell uiautomator dump /sdcard/vae-ui.xml` and `adb -s R5CT10XNKQN pull`, search by `resource-id`, tap the centre of its `bounds` with `input tap`. Keep dumps in the session scratch directory, never in the repository.
- Tags:

| Tag | What |
|-----|------|
| `title` | screen title |
| `okhttp_version` | `okhttp <runtime version>` |
| `fixture_state` | green `Fixture OK sha=ebd3ef4a tools=<n> source=<files\|asset>`; red `FIXTURE ABSENT`, `FIXTURE SHA MISMATCH <8hex>`, `FIXTURE MALFORMED <code>` |
| `budget_used` | `requests <core>/33 . optional <o>/1 . est USD <x>` |
| `warm_window` | `Anthropic warm window: wait <s> s` (present only while open) |
| `run_<leg>` | one button per leg: `run_ver02`, `run_smoke_anthropic`, `run_smoke_openai`, `run_smoke_openrouter`, `run_multi_openai`, `run_multi_openrouter`, `run_responses_probe`, `run_demo_clarify`, `run_demo_partial` |
| `status_<leg>` | one of IDLE, RUNNING, PASS, FAIL, WARM, OUT_OF_BAND, INCONCLUSIVE, CAPTURED, REFUSED, then ` reason=<code>` when there is one |
| `key_state_<p>` | `<p>: <state label>`; `p` is `anthropic`, `openai`, `openrouter`; Ready also shows the last 4 on screen only |
| `key_field_<p>` | masked single-line field |
| `key_save_<p>` | Save button (the field is cleared at once) |
| `key_delete_<p>` | Delete button |
| `import_test_keys` | debug build: import the pushed test keys through :keystore |
| `import_status` | debug build: `anthropic=Ready deleted=true in_datastore=false; ...` |
| `readout` | the last leg's headline |
| `failure_banner` | red `FAILED: <code>` (plus the keystore action for an unreadable key) |
| `clarify_question` | the clarification question |
| `clarify_option_<id>` | one button per option (`clarify_option_list-a`, `clarify_option_list-b` in the demo) |

- A leg is done when `status_<leg>` leaves RUNNING. Read the verdict from `status_<leg>` and from the captured `VAE_VERDICT` line (runner `capture-save`).
- Runner subcommands (`scripts/run-sample-gate1.sh <subcommand> [arg]`; last line is always `SAMPLE_GATE1: <OK|FAIL|INFRA|ERROR> sub=<subcommand> ...`; exit 0 OK, 1 FAIL, 2 ERROR, 3 INFRA offline/busy/warm window, 4 INFRA identity or refused serial): `preflight`, `build-install`, `push-fixture`, `push-keys`, `capture-start`, `capture-save <leg>`, `cold-stamp check|write`, `verify-keys-gone`, `cleanup`. Leg names for `capture-save` are exactly: `ver02 smoke_anthropic smoke_openai smoke_openrouter multi_openai multi_openrouter responses_probe demo_clarify demo_partial`.
- Key the verdict off the `SAMPLE_GATE1:` line and the exit code, not the earlier informational lines.

## 3. Arrange (host, main checkout)

- A1. `git status --short` is clean for code paths (`core/ providers/ keystore/ sample/ scripts/ docs/ gradle/`). Record `git rev-parse HEAD`.
- A2. The fixture is NOT yet in `sample/src/debug/assets/`. If a copy is there already, move it to the session scratch directory and restore it at A5.
- A3. `scripts/run-sample-gate1.sh preflight`.
- A4. `scripts/run-sample-gate1.sh build-install`. Record `apk_md5`, `head`, `dirty` and `asset_fixture=absent`.
- A5. Manual fixture copy: `cp` the LE-1 fixture from the SB path named in `10-CONTEXT.md` "Runtime Decisions" (Fixture source) to `sample/src/debug/assets/sb-a10-fixture.json` (gitignored; never reference the SB path in the build). Compute `sha256sum` of it and compare with the `FIXTURE_SHA256` constant in `sample/src/main/kotlin/io/github/ygaray/voiceactionengine/sample/fixture/FixtureLoader.kt`, comparing and recording by the 8-hex prefix `ebd3ef4a` only. A mismatch means: stop, ask the orchestrator to regenerate, record G1-02 and G1-06 as INFRA(fixture) and continue with the rest.
- Before the first device step, the dispatcher announces the TESTER window to the orchestrator, and announces again when it closes (master instruction, 2026-10-01).

## 4. Criteria (decision approved)

Result rules for every criterion: `result: passed` only when every Expected item is observed; `result: failed` for a genuine behavior failure (a gap); `result: partial` for INFRA, deferred, INCONCLUSIVE or OUT_OF_BAND outcomes, with the reason. Commit evidence by explicit path only.

### G1-01 device-hw: fixture absent is loud at run time
- **Requirement:** VER-01 / D-05.
- **Arranged:** A4 done, fixture not on the device (`asset_fixture=absent`).
- **Did:** launch the app; read `fixture_state`; `capture-start`; tap `run_ver02`; read `status_ver02`; `capture-save ver02`.
- **Expected:** `fixture_state` is red `FIXTURE ABSENT`; `status_ver02` is `REFUSED reason=fixture_absent`; the saved evidence shows a loud `VAE_FIXTURE` line with kind=absent and the REFUSED verdict. No request is spent.
- **Evidence:** `evidence/gate1-ver02.txt` (this refusal block; the later cold run appends its own block).

### G1-02 device-hw: fixture present and verified
- **Requirement:** VER-01 / D-05.
- **Arranged:** A5 done (sha prefix matches).
- **Did:** `push-fixture`; force-stop (`adb -s R5CT10XNKQN shell am force-stop io.github.ygaray.voiceactionengine.sample`) and relaunch; read `fixture_state`. Count the tools array of the host file with a one-line JSON count (for example `python3 -c 'import json,sys;print(len(json.load(open(sys.argv[1]))["tools"]))' <file>`; the count only is printed or recorded, never a name).
- **Expected:** `fixture_state` is green `Fixture OK`, sha prefix `ebd3ef4a`, and `tools=<n>` equals the host count.
- **Evidence:** the `SAMPLE_GATE1:` line of `push-fixture` (it prints `fixture_sha=<8-hex prefix>` only) and the screen text of `fixture_state` (prefix only). If any line or screen text ever shows more than the 8-hex prefix, do not paste it: report it instead.

### G1-03 device-hw: OkHttp pin runs on the device
- **Requirement:** VER-01.
- **Did:** read `okhttp_version`; the `VAE_ENV` line appears in the first captured block.
- **Expected:** `okhttp 5.2.1` on screen and `okhttp=5.2.1` in `VAE_ENV`.
- **Evidence:** `evidence/gate1-ver02.txt` (`VAE_ENV` line).

### G1-04 device-hw: bring-your-own key through :keystore with a dummy value
- **Requirement:** VER-01 / D-04.
- **Did:** type a non-secret dummy string (for example `dummy-value-1234`) into `key_field_openrouter` with `input text`; tap `key_save_openrouter`; read `key_state_openrouter`; force-stop and relaunch; read it again; tap `key_delete_openrouter`; read it again.
- **Expected:** Ready with the dummy's last 4 (screen only); still Ready after relaunch; Not configured after delete. Delete the dummy before G1-05 so the real key is not shadowed.
- **Evidence:** screen text of `key_state_openrouter` at the three points (state words only, no last 4 in the log).

### G1-05 device-hw: test keys travel through :keystore (approved only)
- **Requirement:** VER-01 / D-04 / D-13.
- **Arranged:** `decision: approved`. `push-keys` is refused by the runner otherwise.
- **Did:** `push-keys`; tap `import_test_keys`; read `import_status`; `verify-keys-gone`; force-stop and relaunch; read the three `key_state_<p>`.
- **Expected:** `import_status` shows Ready deleted=true in_datastore=false for all three providers; `verify-keys-gone` prints the test-keys directory empty; after relaunch all three are Ready. Record the word "Ready" only, never the last 4.
- **Evidence:** the `SAMPLE_GATE1:` lines of `push-keys` and `verify-keys-gone`.

### G1-06 device-hw: VER-02 Anthropic agentic cold run (L1)
- **Requirement:** VER-02 / D-03.
- **Arranged:** G1-02 and G1-05 passed. `scripts/run-sample-gate1.sh cold-stamp check` passes (exit 3 means the 6-minute warm window is open: wait, do not fail).
- **Did:** `cold-stamp check`; `capture-start`; tap `run_ver02` (the first run is always a UI press, `trigger=ui`); immediately `cold-stamp write`; wait until `status_ver02` leaves RUNNING; `capture-save ver02`.
- **Expected (PASS):** verdict=PASS: at least 2 turns; turn-1 `cache_creation_input_tokens` > 0 and within 7,016 +-5%; every later `cache_read_input_tokens` > 0 and equal to the write within 1%; every attempt 200; canned admit. `VAE_ENV` shows `min_cacheable=4096` and `prefix_chars`. Model: claude-haiku-4-5.
- **Other outcomes:** WARM is an infra re-run: wait until `cold-stamp check` passes (at least 6 minutes) and rerun once if `budget_used` has headroom. OUT_OF_BAND: record the measured value, `result: partial`, and escalate the value to the orchestrator for the band decision; never widen the band. FAIL `single_turn`: one rerun after the cold window (the app uses the stronger prompt). Any other FAIL: `result: failed` and a gap.
- **Evidence:** `evidence/gate1-ver02.txt`.

### G1-07 device-hw: VER-03 Anthropic single-shot smoke (L2)
- **Requirement:** VER-03 (and the 07-08 carry C6).
- **Did:** `capture-start`; tap `run_smoke_anthropic`; `capture-save smoke_anthropic`.
- **Expected:** verdict PASS with exactly one attempt kind=initial http=200, no reshape or retry, tool=edit_item, optional_absent=true. State in the log that the `disable_parallel_tool_use` request body is not observable on device and that the host goldens named in `evidence/phase-gate.txt` assert it (accepted by the orchestrator: host golden plus the live 200 on the first attempt is sufficient proof).
- **Evidence:** `evidence/gate1-smoke_anthropic.txt`.

### G1-08 device-hw: VER-03 OpenAI single-shot smoke (L3)
- **Requirement:** VER-03.
- **Did:** `capture-start`; tap `run_smoke_openai`; `capture-save smoke_openai`.
- **Expected:** PASS, optional_absent=true, model gpt-5.4-mini.
- **Evidence:** `evidence/gate1-smoke_openai.txt`.

### G1-09 device-hw: VER-03 OpenRouter single-shot smoke (L4)
- **Requirement:** VER-03 (carry C5).
- **Did:** `capture-start`; tap `run_smoke_openrouter`; `capture-save smoke_openrouter`.
- **Expected:** PASS (carry C5 closes), or INCONCLUSIVE `model_filled_optional`: one rerun (the app uses the stronger prompt); still INCONCLUSIVE means `result: partial` and C5 stays carried.
- **Evidence:** `evidence/gate1-smoke_openrouter.txt`.

### G1-10 device-hw: VER-03 extended multi-turn on OpenAI Chat (L5)
- **Requirement:** VER-03 (P8 Chat mapper on device).
- **Did:** `capture-start`; tap `run_multi_openai`; `capture-save multi_openai`.
- **Expected:** PASS: at least 2 calls, find_items on turn 1, tool result replayed, final answer, all attempts 200.
- **Evidence:** `evidence/gate1-multi_openai.txt`.

### G1-11 device-hw: VER-03 extended multi-turn on OpenRouter (L6)
- **Requirement:** VER-03 (P8 OpenRouter mapper on device).
- **Did:** `capture-start`; tap `run_multi_openrouter`; `capture-save multi_openrouter`.
- **Expected:** PASS; record `turn2_cache_read` (observed, not asserted). Cache-write accounting through OpenRouter is not exercised (carry C4): not exercisable in v1.0 (LATER-02); needs Yahir waiver; P11 waiver packet. It is not closed.
- **Evidence:** `evidence/gate1-multi_openrouter.txt`.

### G1-12 device-hw: VER-04 clarification and partial on device (offline, no key)
- **Requirement:** VER-04 / D-14.
- **Did:** tap `run_demo_clarify`; confirm `clarify_question` and two `clarify_option_*` buttons; tap `clarify_option_list-b`; read `readout`; tap `run_demo_partial`; read `readout`. `capture-save demo_clarify` and `demo_partial` after each.
- **Expected:** the follow-up completes after the option is chosen; the partial demo reads "Did 1 action(s), couldn't finish". No request is spent.
- **Evidence:** `evidence/gate1-demo_clarify.txt`, `evidence/gate1-demo_partial.txt`.

### G1-13 device-hw: bounded spend (Runtime Decision)
- **Requirement:** Runtime Decision (bounded call count).
- **Did:** after the live legs read `budget_used` and the last `VAE_BUDGET` line.
- **Expected:** core requests <= 33 and optional <= 1. Report to the master exactly one line: `GATE1 LIVE SPEND: requests=<total> (anthropic=<a> openai=<o> openrouter=<r>) est_cost_usd=<x> ceiling=33+1`.
- **Evidence:** the `VAE_BUDGET` line in the last gate1 evidence file and the spend line in SELF-UAT.

### G1-14 device-hw: cleanup
- **Requirement:** D-04 (no plaintext key survives).
- **Did:** `verify-keys-gone`; `cleanup`; `git status --short`.
- **Expected:** the keys directory is empty; `cleanup` prints `sample package removed`; `git status --short` shows only `evidence/gate1-*.txt` and the SELF-UAT files as new for this run (plus pre-existing untracked noise the orchestrator knows about). Restore a fixture copy moved at A2 only after this check. Remove `sample/src/debug/assets/sb-a10-fixture.json` from the working tree after `cleanup` (it is gitignored, but leave no fixture behind).
- **Evidence:** the `SAMPLE_GATE1:` lines of `verify-keys-gone` and `cleanup`.

### Optional L7 (not a criterion)
Only after G1-11, only if approved and the optional budget in `budget_used` is 0: tap `run_responses_probe`; `capture-save responses_probe`. Verdict CAPTURED; record http and reason in the carry register disposition for C3. A 400 bills nothing.

## 5. Deferred path (decision deferred)

Run G1-01..G1-04, G1-12 and G1-14. Mark G1-05..G1-11 and G1-13 `result: partial` with reason `INFRA(deferred: live legs not approved)` and register each as a Gate-2 item in the fragment (carry C7). Do not run `push-keys`. Phase 11 cannot start until the live legs pass.

## 6. Report and hand-off

1. Write `.planning/phases/10-sample-harness-gate-1-docs/10-SELF-UAT.md`. Frontmatter: `status`, `result`, `gate: 1`, `phase: 10-sample-harness-gate-1-docs`, `source: [ROADMAP Phase 10 SC1-SC4]`, `device: yahirs-s22-ultra-2 R5CT10XNKQN SM-S908U`, `apk: <md5> @ <sha>`, `run: <ts> @ <sha>`. Status rules: all criteria passed gives `complete` / `all_pass`; any failed gives `failed` / `has_fail`; only deferred or partial gives `partial` / `has_partial`. Body: one `### N. G1-NN <title>` block per criterion with a column-0 `result: passed|failed|partial`, then Arranged (seeded) and Did (drove) bullets, then the evidence path. Do not use a table for per-criterion verdicts. Add the spend line (G1-13) and one line citing `10-WIRING-TEST.md` status (`pending-rerun` unless it has changed).
2. Write the fragment `.planning/uat-pending/10-sample-harness-gate-1-docs.md` using the verify-work-agentic schema (`### Phase 10 - sample-harness-gate-1-docs (v1.0)`, Status `pending`, Milestone, Gate 1 self-UAT log link, Items covered, Owner how-to-verify, Note). List every carry C1-C7 from the register with its observed disposition. Include the `uat-pending/05` cache-write-via-router item as: "not exercisable in v1.0 (LATER-02); needs Yahir waiver; P11 waiver packet", not closed.
3. Regenerate the ledger: `gsd_run query milestone uat-ledger regen --raw` (the only writer of `HUMAN-UAT-PENDING.md`; never `git checkout` it or a sibling fragment).
4. Commit `evidence/gate1-*.txt`, `10-SELF-UAT.md`, the fragment and the regenerated `HUMAN-UAT-PENDING.md` by explicit path only. Never `git add -A`.
5. Report to the master: the `GATE1 LIVE SPEND` line, any escalation (OUT_OF_BAND value, INCONCLUSIVE, fixture mismatch) and the TESTER-window close announcement.

## 7. Dispositions of open questions

- Q1: an out-of-band VER-02 measurement is recorded with its value and escalated to the orchestrator; it is never silently widened.
- Q2: the debug autorun extra (`adb -s R5CT10XNKQN shell am start -n io.github.ygaray.voiceactionengine.sample/.MainActivity --es vae_autorun <leg>`, debug build only) is allowed only for a rerun; the verdict line of every leg's first run must show `trigger=ui`. The app enforces it: an autorun of a leg that has not been run from the screen is refused with `VAE_VERDICT ... verdict=REFUSED reason=autorun_before_ui` and sends nothing.
- Q4: if `README.md`, `INTEGRATION.md`, `API.md` or a public signature changes after the SHA recorded in `10-WIRING-TEST.md`, the wiring test reruns before Phase 11 cuts the tag. Today that status is `pending-rerun` (tested SHA 338d85ffa3); the rerun on the final SHA is a Phase 11 precondition.
- Phase 10 creates no tag (D-08, D-12), no `api.txt` (D-09) and no release script (D-10).
