---
phase: 20-cut-v1-1-0
slug: cut-v1-1-0
status: secured
threats_total: 35
threats_closed: 35
threats_open: 0
threats_open_nonblocking: 0
review_hardening_open_nonblocking: 7
asvs_level: 1
block_on: high
audited: 2026-10-07
audited_head: 7f97e6ffcc8dee9cd4f6f3c01bf53fddeaa10462
tag_audited: v1.1.0
tag_commit: 2e677a604f472f10e11062509f933cd25e1be79c
wiring_sha: 4bdb663b4c7c1bf02d4588751705dfcc8b356ef1
---

# Phase 20: Cut v1.1.0 - Security Verification

Audit of the threat register declared in the `<threat_model>` blocks of plans 20-01 .. 20-12 (20-13 is a gap-closure plan
and declares none). Method: each declared mitigation was looked for in the code, scripts, git objects and release evidence,
not in the SUMMARY prose. ASVS level 1, `block_on: high`. Nothing was built, no Gradle ran, the tag was not touched, and the
only network use was read-only (`git ls-remote`, which the gates also use).

`threats_total` counts unique IDs: 34 numbered threats (T-20-01 .. T-20-34) plus T-20-SC, the "no dependency install"
accept that every plan repeats (12 rows, one ID). The register has 46 rows.

## Verdict

SECURED. 35 of 35 closed, 0 open (blocking), 0 open (non-blocking). The 7 review findings that 20-REVIEW-FIX.md skipped are
hardening gaps in release tooling and the sample app. None re-opens a declared threat or affects the shipped tag (triage below).

## Trust boundaries

| Boundary | Verified state |
|----------|----------------|
| local history -> public origin | one merge (no rebase), key-shape and fixture scans clean, every push preceded by a relayed OK |
| release-cut.sh gates -> immutable tag | 15 gates fail closed, no bypass lever, tag pushed by refname only, origin diffed afterwards |
| this repo -> section 11 ledger | no v1.1.0 row in CROSS-REPO-SCOPE-CONTRACT.md; row delivered by relay only (A14) |
| key material -> screen/evidence | six-hex SHA-256 tag on screen only; sample/evidence sinks tested never to carry it |
| isolated wiring agent -> workspace | JitPack-installed, throwaway config dir and workspace removed, no credential file left |

## Threat register

| ID | Category | Sev | Disposition | Status | Evidence |
|----|----------|-----|-------------|--------|----------|
| T-20-01 | Tampering (merge strategy) | high | mitigate | CLOSED | Merge commit 74a62a7 has parents 9c90763 and 43768ea. `git merge-base --is-ancestor` is true for 090fd8ec76, ec24a19786, 1869950dca and v1.0.1 against both HEAD and v1.1.0 (re-run by this audit). evidence/prepush-scan.txt `rebase-used: no`; the phase part of the main reflog holds only commits and fast-forward merges (the one "Reset to HEAD" entry is a 2026-10-06 move onto a descendant, before the phase). All pushes were plain `git push origin main` (relay-log.md Relays 1, 3, 5, 6). |
| T-20-02 | Info disclosure (history going public) | high | mitigate | CLOSED | prepush-scan.txt: `strict_key_shape_commits=0`, `unexplained=0`, `GATE OK leak`. Independent re-check: (a) gate_leak's three key-shape rules over all 1,403 tracked files at v1.1.0 leave one hit, the allow-listed transport-test constant in ChatCaptureRunTest.kt; (b) `git log -G` for the four key shapes over 43768ea..HEAD finds 0 commits each; (c) `gate leak` at HEAD prints GATE OK. The leak gate's fixture content check is skipped when the fixture is not in the repo (`content_check=skipped(no local fixture)` in every run, including the cut), so this audit ran it by hand: the first 64 characters of the A10 fixture system prompt (read from its copy in the SecondBrain repo, never printed) occur in 0 tracked files at v1.1.0 and HEAD and in 0 commits on any ref. |
| T-20-03 | EoP (push without authority) | high | mitigate | CLOSED | relay-log.md Relay 1: sent `pushing main 97801136ae...`, received `push ok ... relayed_by=yahir-gsd-control-plane-3b`, then `gate pushed` OK and origin tags still only v1.0.0 and v1.0.1. Relays 3, 5 and 6 repeat the pattern. Today `git ls-remote --tags origin` lists only v1.0.0, v1.0.1 and v1.1.0. |
| T-20-04 | Tampering (dump drops a released line) | high | mitigate | CLOSED | `comm -23` of v1.0.1 against v1.1.0 api.txt: core 0, providers 0, keystore 0 lines; providers api.txt is the same blob in both tags. `release-cut.sh gate api-baseline v1.1.0` re-run at HEAD: "additive only", GATE OK. Cut log: `GATE OK api-check` (metalavaCheckCompatibility executed for every module, enforced at release-cut.sh:576-579). |
| T-20-05 | Info disclosure (toString leak) | high | mitigate | CLOSED | CommitSink.kt:46-48 `ActionEvent.toString` prints runId, parentRunId, heldRunId as set/null and the action string; :84-86 `ExecutedAction.toString` prints position, kind, applied, toolName, a targetIds count, the context's class simple name and mutating. No outcome token, target id, call id, argument, utterance or key. The d261da4..v1.1.0 diff of core main is 6 KDoc lines only. Sentinel tests exist: ActionEventTest.kt:190 `anEventsStringFormNeverCarriesTheTokenTargetValuesOrContextText` and :223 `everyFieldThatCouldCarryUserTextIsAbsentFromBothStringForms`. KDoc records the RT-03 policy. |
| T-20-06 | Tampering (stale/partial dump) | medium | mitigate | CLOSED | scripts/api-dump-isolated.sh: copies the tree (:49-55 completeness count), `rm -f "$m/api.txt"` before dumping (:61), fails a module whose dump lacks its `package` line as vacuous (:68-69), prints per-module line counts (:80). Gate 10 `GATE OK api-dump` in the cut log compares the fresh dump with the committed file byte for byte (release-cut.sh:471-487). |
| T-20-07 | Info disclosure (key characters or fingerprint reach evidence/logs/UiState) | high | mitigate | CLOSED | No key-tail read in sample main (grep for takeLast/last/substring/drop finds only unrelated verdict and budget code). Fingerprint is produced in one place, `KeyUx.fingerprint` (KeyVault.kt:115-118), consumed only by `HeaderText.keyRow`/`KeyUx.label` for the row text; `KeyView.toString` prints provider and tone only (SampleViewModel.kt:68-70) and `ApiKeyStoreVault.toString` prints its type only. No script reads `key_state_*` (grep of scripts/ is empty). The no-echo test `SampleViewModelTest.savingAKeyNeverLogsIt` (:312) asserts the key, its tail and the fingerprint are absent from evidence lines, the rig sink and `UiState.toString()`. `UiTags.neverEchoed` (UiTags.kt:41) plus `UiTagsTest` (:49-55) and the runbook bullet (19-GATE1-RUNBOOK.md:24-26) are present. No `fp xxxxxx` token appears in releases/ or the phase directory except the documented test vector for "abc". Residual enforcement gap: review WR-04 (below, non-blocking). |
| T-20-08 | Info disclosure (brute force from 6-hex fingerprint) | low | accept | CLOSED (accepted) | `FINGERPRINT_BYTES = 3` (KeyVault.kt:109) caps the tag at six hex digits. See Accepted risks AR-1. |
| T-20-09 | Tampering (weak hash) | medium | mitigate | CLOSED | KeyVault.kt:116 `MessageDigest.getInstance("SHA-256")`, the only hash call in sample main. `KeyVaultTest.kt:60` pins the known vector `fingerprint("abc") == "ba7816"`. |
| T-20-10 | Tampering (docs drift from source/compiled regions) | high | mitigate | CLOSED | `VAE_DOCS_REQUIRE_PINNED_TAG=1 bash scripts/verify-docs-coverage.sh` re-run at HEAD (pure grep, no build): `DOC COVERAGE OK checks=32 types=119`, exit 0, C06 and C20 included. Isolated wiring record: `WIRING TEST: PASS checks=13` on W, and `release-cut.sh gate wiring <W>` prints GATE OK at HEAD. Docs diff since d261da4 is prose in API.md and INTEGRATION.md only. |
| T-20-11 | Info disclosure (key or private path in docs) | medium | mitigate | CLOSED | README.md, INTEGRATION.md, API.md, ECOSYSTEM.md at v1.1.0 hold 0 matches for `/home/` or `~/` (also gate C25). `gate leak` OK at the cut and at HEAD. |
| T-20-12 | Repudiation (self-authored waiver answers) | high | mitigate | CLOSED | WAIVER-PACKET.md as first committed (ce8377d) holds exactly ten `pending` lines. Only a77dc6c changed them, quoting relay-log.md Relay 2 (`answered_by: Yahir ... relayed by orchestrator yahir-gsd-control-plane-3b`, "all as proposed"). `gate waiver v1.1.0` prints GATE OK at HEAD. |
| T-20-13 | Tampering (fabricated ruling citation) | high | mitigate | CLOSED | All 21 rows of C7-DEFAULTS-FINAL.md carry a `file:line` plus quoted text; a script run by this audit resolved every cited file and found every quoted fragment in it (21 rows, 0 misses); 0 FALLBACK labels were needed. Spot reads of 14-CONTEXT.md:115-116, relay-log.md:6,10,16-21 and 20-CONTEXT.md:96,103 match. |
| T-20-14 | Info disclosure (packet on tailnet doc server) | low | accept | CLOSED (accepted) | Packet content scanned: no key-shape string, no fixture text. See AR-2. |
| T-20-15 | Tampering (new-module branch lets a gap through) | high | mitigate | CLOSED | release-cut.sh:512-541 `api_baseline_check`: newness keyed on the module directory (`git cat-file -e "$prior:$m"`), patch release introducing a module rejected (:530-531), header-only seed or missing package line rejected (:535-538). Controls `api-check-new-module-seed`, `-new-module-populated`, `-baseline-deleted`, `-new-module-patch` exist (:1620-1648). Recorded selftest `RELEASE SELFTEST OK happy=1 negatives=40 positives=7` (window 01b); CONTROL_ORDER holds 47 labels = 40 + 7. Cut log: "new in this release (no baseline): undo voice-adapter". |
| T-20-16 | EoP (:stt confinement not enforced) | high | mitigate | CLOSED | Gate 11 calls `verify-stt-confinement.sh` and requires `STT CONFINEMENT OK` (release-cut.sh:500-504); re-run at HEAD (bash only): `STT CONFINEMENT OK checks=6`. In build files only voice-adapter names the catalog alias, and only as compileOnly/testImplementation (`git grep` at v1.1.0). Control `hygiene-stt-confinement` plants `compileOnly(libs.stt.engine)` in core and requires the script's own marker (:1570-1577). |
| T-20-17 | Tampering (script edit after W / bypass lever) | high | mitigate | CLOSED | `git log -1 -- . ':!.planning'` at v1.1.0 is W (4bdb663); `git diff --name-only W v1.1.0` outside .planning is empty. No bypass flag or env lever (header comment :20 and grep of the script: only `KEEP_WORK` and test-only `SELFTEST_ONLY`, which ends in PARTIAL, never OK). `GATE_ORDER` is identical at d261da4, W, v1.1.0 and HEAD. The only `--force` in the script is the selftest's sandbox re-tag (:1613), preceded and followed by `assert_local_remote`, which insists on a local bare path and refuses network URLs. Gate 7 behaves as designed: at HEAD it is RED for the post-tag edit a8a6022 (`gate diff <W>` rc=1), proving the gate would refuse a script change after W. |
| T-20-18 | DoS (earlyoom kills a build) | high | mitigate | CLOSED | Five quiet-window files each hold `grant: consumed`, `relayed_by: yahir-gsd-control-plane-3b`, `heavy_gates: green`. Cut and preflight ran once each: single-use daemon flags (`-Dorg.gradle.daemon=false`, workers.max=2), no pipes, no Gradle process before, no kill, no retry (cut-v1.1.0.txt). Deviation noted: the strict swap-free check was red; the orchestrator's standing ruling (MemAvailable >= 8 GiB at open; 13.2 GiB at the preflight window open, 10.2 GiB at the cut) governed. |
| T-20-19 | EoP (self-authored grant or answers) | high | mitigate | CLOSED | Each window's grant line and every answer are quoted from relays with `relayed_by` and a date (relay-log.md Relays 1-7; QUIET-WINDOW-01 .. 03). Waiver answers come from Yahir's relayed words, not inferred from the Proposed column. |
| T-20-20 | Repudiation (W chosen wrongly / change after W) | high | mitigate | CLOSED | WIRING-RERUN.md `tested_sha` equals W, and W is the last non-.planning commit at the tag (see T-20-17). Gate 6 and gate 7 both GATE OK in the preflight and the cut. Wiring-test docs unchanged between W and HEAD. |
| T-20-21 | Tampering (JitPack builds something other than W) | high | mitigate | CLOSED | live-probe-W.txt: JitPack API `commit 4bdb663b4c7c...` for ref `4bdb663b4c`, `LIVE PROBE PASS`. WIRING-RERUN.md states the same ref and JitPack install path (not prepare-local). Gate 7 GATE OK before the push of W. |
| T-20-22 | Tampering (binary break invisible in api.txt) | high | mitigate | CLOSED | evidence/binary-diff.txt: helper run over JitPack-built v1.0.1 and W artifacts: providers `removed=0`, keystore `removed=0`, core `removed=12`. The 12 lines are all individually mapped in binary-diff-waiver.txt and were independently confirmed here: ActionEvent, ExecutedAction, HeldProposal, TierPolicy, CommandTrace, TierAttempt, CommandOutcome.Completed and .Unhandled all declare `internal constructor(` at both v1.0.1 and v1.1.0, core/api.txt lists no ctor for them, and the 12th line is the compiler accessor `access$submitAll`. Waived by orchestrator ruling RT-12 and Yahir's accepted packet; named in the ledger row. Residual: Java code could link to the public JVM `<init>` of an internal Kotlin constructor, which is unsupported; both consumers are Kotlin and recompile on repin. See review WR-01/WR-02 for tooling gaps. |
| T-20-23 | EoP (push without OK) | high | mitigate | CLOSED | Relay 3: `pushing main 26dcd10bb5...` then `push ok ... relayed_by=...`; origin tags unchanged afterwards. Same for Relays 5 and 6. |
| T-20-24 | Tampering (wiring pass for wrong SHA / local publication) | high | mitigate | CLOSED | WIRING-RERUN.md: `status: pass`, `tested_sha` = full W, `consulted_only_workspace: true`, `ancestors_clean: yes`, prepared with `prepare` (JitPack), `settings.gradle.kts` lists google, mavenCentral and jitpack only. `gate wiring <W>` GATE OK at HEAD. |
| T-20-25 | Info disclosure (credentials/host paths left behind) | high | mitigate | CLOSED | Dispatch facts: `cfg_removed=yes`, throwaway config dir deleted with plain rm. This audit: the workspace dir under /tmp is gone, no `wiring-cfg*` dir or `.credentials.json` found under /tmp, no credential-like file tracked in the repo, wiring-test/ holds 5 prose/reference files, consulted list names only workspace docs and build files, 0 `/home/` hits under .planning/releases. |
| T-20-26 | Repudiation (record edited to read pass) | high | mitigate | CLOSED | The record quotes `WIRING TEST: PASS checks=13` verbatim and gate 6 checks status, tested_sha, consulted_only_workspace and the PASS line against HEAD (release-cut.sh:259-272); re-run GATE OK. The corrected analysis of the core red was added as a separate waiver file, not by editing the earlier record (binary-diff.txt still shows its original wording). |
| T-20-27 | Tampering (tagging an unchecked tree) | critical | mitigate | CLOSED | `run_cut` (release-cut.sh:804-839) requires approvedCommit == HEAD, re-runs the full preflight, re-checks HEAD, creates an annotated tag, verifies it peels to the approved commit, pushes only `refs/tags/$tag` and diffs the origin tag list. cut-v1.1.0.txt: 15 `GATE OK`, `PREFLIGHT OK`, `CUT OK tag=v1.1.0 commit=2e677a60...`, exit 0, one attempt. Re-verified: local and origin tag object both 6ea5ede973, peeled commit 2e677a604f = origin/main at cut time. |
| T-20-28 | EoP (cut without OK) | high | mitigate | CLOSED | Relay 7: `tag ready v1.1.0 2e677a60...` answered `tag ok v1.1.0 2e677a60... relayed_by=yahir-gsd-control-plane-3b`; the cut ran after it (cut-v1.1.0.txt cites it). The OK came from the orchestrator under RT-10, which waived only Yahir's owner approval, not the orchestrator step. |
| T-20-29 | DoS (earlyoom kills the cut) | high | mitigate | CLOSED | Same controls as T-20-18; the cut ran once, no kill, no retry; the tag was created only after the cut's own preflight passed (`GATE OK version` precedes `git tag -a`). |
| T-20-30 | Repudiation (ledger row committed here) | high | mitigate | CLOSED | `git log 9c90763..HEAD --no-merges -- CROSS-REPO-SCOPE-CONTRACT.md` is empty. The contract's only change since d261da4 is 43768ea, the orchestrator's YAT v2.5.0 row, arrived by the origin merge (subject tagged "(xrepo)", dated 2026-10-05, before the phase). The contract has 7 mentions of v1.1.0 in d261da4, 9c90763, v1.1.0 and HEAD alike (planning text, no ledger row), and the table's voice-action-engine rows are v1.0.0 and v1.0.1 only. 20-11's commit 467ce26 touches .planning paths only. LEDGER-ROW.md says "never committed to section 11 here". HEAD's contract equals origin/main's. |
| T-20-31 | Tampering (red build hidden by retagging) | high | mitigate | CLOSED | Probe result recorded: live-probe-v1.1.0.txt `LIVE PROBE PASS ref=v1.1.0`, JitPack `status=ok isTag=true commit=2e677a604f...`, five modules. Tag object unchanged locally and on origin; no tag delete, force or re-create command appears in the evidence, summaries or quiet-window files; rollback.txt `scenario: none`. |
| T-20-32 | Info disclosure (key, transcript or home path in row/evidence) | medium | mitigate | CLOSED | LEDGER-ROW.md and every file under .planning/releases/v1.1.0: 0 key-shape matches, 0 `/home/` paths, no utterance or tool text. Evidence files are script-filtered output. See observation O-2 for home-path prose elsewhere in planning notes. |
| T-20-33 | Tampering (stale v1.1.0 announcement) | medium | mitigate | CLOSED | The abandon branch was not taken: tag exists on origin, docs name it, strict docs gate re-run at HEAD passes (T-20-10). Nothing stale exists to revert; rollback.txt records the no-op. |
| T-20-34 | Tampering (deleting/moving a pushed tag) | high | mitigate | CLOSED | rollback.txt: `scenario: none`, "tag is never moved, deleted or re-created". Tag object identical locally and on origin (6ea5ede973). Plan 20-12 touched no document and no tag. |
| T-20-SC | Tampering (dependency installs), recurs in plans 01-12 | low | accept | CLOSED (accepted) | `git diff d261da4 v1.1.0 --name-only` lists no build.gradle.kts, settings, version catalog, jitpack.yml or lockfile. See AR-3. |

## Accepted risks log

| ID | Threat | Risk accepted | Basis |
|----|--------|---------------|-------|
| AR-1 | T-20-08 | The on-screen tag is 24 bits of SHA-256(key); an attacker who sees it learns nothing practical about a high-entropy provider key. | Plan 20-03 threat model; tag length capped at six in code; screen-only; ID verified in this audit. |
| AR-2 | T-20-14 | The waiver packet is rendered on the tailnet-only doc server. | Plan 20-05; content is planning prose, scanned clean of key shapes and fixture text. |
| AR-3 | T-20-SC | No package installs happen in any Phase 20 plan, so no supply-chain exposure is added. | Verified: no dependency-bearing file changed in d261da4..v1.1.0. |
| AR-4 | T-20-22 (core removed=12) | The 12 JVM members the descriptor diff reports are internal constructors and one synthetic accessor, not Kotlin-callable API. | Orchestrator ruling RT-12 (2026-10-07) and Yahir's accepted packet; mapping in evidence/binary-diff-waiver.txt, confirmed in source here. |
| AR-5 | T-20-18 / T-20-29 | Quiet windows opened with swap nearly full, relying on MemAvailable >= 8 GiB. | Orchestrator standing ruling, restated verbatim in relays 3, 4 and 6; no kill occurred. |

## Review-finding triage (20-REVIEW.md, 7 skipped in 20-REVIEW-FIX.md)

Question asked: is any skipped finding a real blocking threat (high or critical, or a declared mitigation that is absent)?
Answer: no. IN-02 was fixed (a8a6022). The rest:

| Finding | Maps to | Severity | Why not blocking | Status |
|---------|---------|----------|------------------|--------|
| WR-01 binary-diff helper ignores class headers | T-20-22 | medium | Header changes are visible to the other control that already ran: api.txt carries modifiers and supertypes (e.g. `public final class X implements Y {`), so gate 12's `comm -23` and the executed metalavaCheckCompatibility catch them. For this release both are clean (0 removed or changed lines in core, providers, keystore). Hardening for future cuts only. | open, non-blocking, hardening (backlog) |
| WR-02 D-02 binary diff not enforced by a gate | T-20-22 | medium | The declared mitigation was an evidence step, and it was performed with a recorded, source-verified waiver; the orchestrator checked `binary-diff-waiver.txt` before the tag OK (Relay 7). Automation gap for later cuts. | open, non-blocking, hardening (backlog) |
| WR-03 gate 12 ignores a module dropped since the previous tag | none (new) | medium | Not triggered: v1.0.1 carried api.txt for core, keystore, providers and all three are in the v1.1.0 manifest and published. | open, non-blocking, hardening (backlog) |
| WR-04 RT-07 no-echo rule not mechanical; `neverEchoed` unused in main | T-20-07 | low | Declared mitigation (grep gate, no-echo test, tag list, runbook line) is present. No script reads `key_state_*`, the tag is a 24-bit hash (AR-1), the sample is never published, and Gate-1 for this delta was JVM-only (no tester read the field). | open, non-blocking, hardening (backlog) |
| IN-01 fingerprint falls back to plain green "Ready"; second decrypt | T-20-07 | low | Sample only; the library already hands the key to callers as a String. Loudness issue, not a leak. | open, non-blocking |
| IN-03 JournalStoreTest environment-sensitive | none | info | Test robustness, no security effect. | open, non-blocking |
| IN-04 inline `java.security.MessageDigest` | T-20-09 | info | Style; the SHA-256 call and known-vector test are present. | open, non-blocking |

Tracking note: only backlog 999.1 is in ROADMAP.md. The other skipped findings exist only as "suggested backlog entry" text in
20-REVIEW-FIX.md. Filing WR-01, WR-02 and WR-03 (release tooling) and WR-04/IN-01 (sample) as backlog items before the next
cut is recommended, so that the v1.2 release does not rely on the same manual steps.

## Unregistered flags

SUMMARY.md files carry no `## Threat Flags` section (none of the 13 summaries mentions a threat), so there are no executor-declared flags. Observations
made by this audit, none blocking:

| ID | Observation | Severity | Disposition |
|----|-------------|----------|-------------|
| O-1 | HEAD (7f97e6f) is 6 commits ahead of origin/main. One of them, a8a6022, changes `scripts/verify-binary-diff.sh` after the tag. It does not touch v1.1.0 (the tag's non-.planning tree equals W), but gate 7 is now RED at HEAD, so the next release needs a new wiring SHA and an isolated rerun. Reviewed the diff: it only removes dead locals and turns a javap or `--out` failure into exit 2 (fail-closed). | info | no action for v1.1.0 |
| O-2 | A host username path (the Android SDK location) appears in 20-RESEARCH.md and 20-QUIET-WINDOW-02b.md, and in older planning files and one contract line. The leak gate and docs gate C25 do not cover .planning prose. The username is already public in commit metadata. | low | accepted as is; none of it is in the ledger row or release evidence |
| O-3 | Relay 8 (ledger row, push of the planning record, "quiet done") is prepared in relay-log.md but its "Answers received" is empty at audit time, so the orchestrator may not yet hold the row. This is delivery, not a leak; T-20-30 requires only that the row is not committed to section 11. | info | master delivers |
| O-4 | binary-diff.txt keeps its original header "11 real public-constructor removals"; the correction is in binary-diff-waiver.txt. Left as written (records are not edited into agreement). | info | none |

## Independent checks run by this audit (read-only)

`git merge-base --is-ancestor` (5 refs); `git ls-remote --tags origin`; tag object and peel comparison local vs origin;
`comm -23` of api.txt baselines for the three released modules; key-shape scan of all tracked files at v1.1.0 and HEAD with
gate_leak's rules; `git log -G` key-shape pickaxe over 43768ea..HEAD; A10 fixture content scan of tree and history;
`scripts/release-cut.sh gate wiring | waiver | create-tag | api-baseline v1.1.0 | leak | diff`;
`VAE_DOCS_REQUIRE_PINNED_TAG=1 scripts/verify-docs-coverage.sh`; `scripts/verify-stt-confinement.sh`; contract history for any
v1.1.0 ledger row; citation resolution for the 21 C7 rows; source greps for `internal constructor(` at v1.0.1 and v1.1.0.
Not run (out of bounds): Gradle, the release-cut selftest, the binary-diff selftest, anything that writes to origin.

## Sign-off

threats_open: 0. Ready to hand back to the orchestrator.
