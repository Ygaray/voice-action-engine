---
phase: "13"
slug: on-device-model-spike
status: secured
threats_open: 0
asvs_level: 1
audited_head: b31bd704903455f1a7742d4112582a3ba1bf7c2a
created: "2026-10-06"
---

# Phase 13 - Security

> Per-phase security contract: threat register, accepted risks, and audit trail.

Read-only audit (ASVS 1, block_on high) of the Phase 13 on-device model spike, run non-interactively (`--auto`) on branch
`gsd/phase-13-on-device-model-spike`. The register is the union of the eleven PLAN `<threat_model>` blocks (13-01..13-11):
58 rows, of which 47 are numbered threats (T-13-01..T-13-47) and 11 are per-plan `T-13-SC` package-supply-chain rows
(one per plan; they share an ID, so they are keyed below by plan). 50 are `mitigate`, 8 are `accept`.
All 58 resolve to CLOSED, so `threats_open: 0`.

The spike module, its device-only scripts and the LiteRT-LM catalog entries were deliberately deleted by plan 13-10 (red
disposition, commit `1fec77a`). Mitigations that lived in the deleted harness were verified from history
(`git show 1fec77a^:<path>`, and `e362fb228b` for the verdict code); everything else was verified live at HEAD. The
Evidence column says which. No Gradle was run (host memory); the SC4 gates were run green by the driver beforehand.

## Independent re-checks by the auditor (all host-only, read-only)

- `scripts/verify-repo-hygiene.sh` -> `HYGIENE OK`; `scripts/verify-spike-disposition.sh removed` -> `SPIKE DISPOSITION OK mode=removed`.
- The deleted device-guard proof, extracted from `1fec77a^` into a scratch directory and run with a PATH tripwire shim in
  front of the real `adb`: `SPIKE DEVICE GUARD OK scenarios=88`; the tripwire log was never created (no real adb call).
- The deleted filter proof (`verify-spike-evidence-filter.sh`, from `1fec77a^`) run against the HEAD
  `scripts/spike-evidence-filter.sh`: `SPIKE FILTER OK cases=12`. The HEAD filter's `ALLOW_RE` is byte-identical to
  `ALLOW_PATTERN` in `SpikeEvidence.kt@e362fb228b`.
- All 15 committed evidence files pass the HEAD filter (`FILTER OK`); the only lines it would drop are the 10 `# spike ...`
  provenance headers. Every `target=` value is `R5CT10XNKQN`; no other serial, tailnet address, username, home path or hostname appears.
- Content leak scans (private copies built in the scratchpad, never printed or committed): 302 distinctive strings from the
  private SB gold labels and fixture, plus 34 strings from the host-private raw model answers, searched across HEAD tracked files,
  untracked-not-ignored files and every commit of every ref (885 commits). Hits: only generic JSON syntax fragments, and
  the pre-existing Phase 5/8/9 docs and tests (SB tool names / provider JSON shapes). The by-design committed small-envelope gold
  `small-gold.json` (deleted at 1fec77a) overlaps a few generic utterances and contains no SB tool name. No SB fixture, no SB gold
  file, no raw model answer and no model weights were ever committed. No key shapes in phase paths or evidence.
- The private inputs exist only on the host, outside the repo: `~/.local/share/vae-spike` mode 700, `sb-fixture.json` and
  `sb-gold.json` mode 600, `raw/` mode 700.
- TESTER-only: session transcripts of the window were scanned for adb use. The 13-08 executor made 24 runner calls and 0 direct
  adb calls. The only direct adb calls anywhere in the window are `adb devices` listings and read-only `-s R5CT10XNKQN`
  `get-state` / `pm list packages` / `ls /data/local/tmp` availability checks by the driver. No other serial and no wireless or personal address was addressed.
- Independent recomputation of the small envelope's headline numbers from `evidence/confirm_small.txt` matches
  `13-VERDICT.md` exactly (schema-valid 142/144, false writes 2/34, warm p50 20278 ms, p95 28941 ms, EN 43/55, ES 35/55).
- Time-box arithmetic: window start 1791251500 (2026-10-06T01:51:40Z); `grant: consumed` at 05:52:07Z (14427 s). The 27 s
  over the 14400 s box were `pull-evidence`, `meminfo` and `cleanup`, the subcommands the runner allows after expiry; no `run`
  or push started after expiry (the sb stage ended as host `result=timeout`, and the later `exit_reasons` run was refused).
- Net change of dependency declarations across the phase (`b4fff6d^..HEAD`): `gradle/libs.versions.toml`, `settings.gradle.kts`
  and `jitpack.yml` are identical to pre-phase; the only build-file changes are `.gitignore` and `gradle/invariants.gradle.kts`
  (ML denial). Published-module sources changed only by the `:core` test `NoHardCodedConstantsTest`.
- No §11 ledger row, no contract edit and no tag was written in the phase (`CROSS-REPO-SCOPE-CONTRACT.md` last touched 2026-10-04 by xrepo; tags are v1.0.0 and v1.0.1 only).
- Every committed evidence file has exactly one commit (no re-run overwrote or appended a stage) except `toolchain.txt`, written pre-window.

## Trust Boundaries

| Boundary | Description | Data Crossing |
|----------|-------------|---------------|
| Google Maven -> host build | third-party AAR with native code entered an unpublished module | dependency bytes |
| repository -> JitPack / v1.1.0 tag | JitPack configures every included project | build graph |
| working tree -> public git history | repo is public; model weights and SB private labels must never land | model files, SB fixture and gold |
| device logcat/files -> host evidence | model output, prompts and SB tool names exist on the device; only grammar lines may cross | closed-grammar lines |
| host -> TESTER | install, model push, private-input push, stage runs | APK, weights, SB private files |
| orchestrator relay -> grant file | only authority that opens a TESTER window | grant line |
| model output -> engine / write path | untrusted model text reaches SingleShot and the gate | tool calls |
| evidence + thresholds -> verdict | consumers act on the verdict | numbers |
| :ondevice -> consumers (13-11, not built) | published native ML runtime | n/a, module never created |

## Threat Register

Evidence column: `live` = verified at HEAD; `hist@<sha>` = verified from the deleted harness in git history.

| Threat ID | Category | Component | Severity | Disposition | Evidence | Status |
|-----------|----------|-----------|----------|-------------|----------|--------|
| T-13-01 | Tampering (supply chain) | litertlm-android dependency | medium | mitigate | hist@1fec77a^: catalog `litertlm = "0.17.1"` exact pin; 13-RESEARCH.md "Package Legitimacy Audit" (lines 138-151, Google-owned group, POM url google-ai-edge/LiteRT-LM). live: exposure removed, no LiteRT token in any build file/catalog/settings/jitpack, disposition gate OK. Advisory A7 (repo restriction clause) | closed |
| T-13-02 | Tampering | 13-THRESHOLDS.md | high | mitigate | live: single commit c793a3b (18:03 -06:00) precedes first device commit da93d6e (19:56) and grant 08ada33; `sha256sum` prefix `ec4933fb` = `thresholds_sha` in `evidence/env.txt`; `verify-spike-verdict.sh` `thresholds_mismatch` gate (line 106). hist: runner `host_precheck` refused uncommitted thresholds at `window-start` | closed |
| T-13-03 | Information disclosure | spike app | medium | mitigate | hist@1fec77a^: `AndroidManifest.xml` has no `uses-permission` (no INTERNET). live: `.gitignore` `*.apk *.aab *.litertlm *.task *.tflite *.bin`; hygiene (c) OK; no apk/aab/aar/model ever added in history | closed |
| T-13-04 | Denial of service | root build / JitPack | low | mitigate | hist@1fec77a^: spike `build.gradle.kts` reads only Gradle properties and path strings at configuration. live: module and include gone; `jitpack.yml` never touched in the phase; hygiene (f) OK | closed |
| T-13-SC (13-01) | Tampering | Maven install of litertlm-android | medium | mitigate | exact pin plus manual audit (see T-13-01); net catalog/settings/jitpack diff pre-phase..HEAD is empty | closed |
| T-13-05 | Tampering (supply chain) | :core/:providers/:keystore classpaths | high | mitigate | live: `gradle/invariants.gradle.kts` lines 331-366: `verifyNoMlArtifacts` scoped to `core`/`providers`/`keystore`, denies group prefixes and name tokens on compile+runtime classpaths (resolved and requested selectors), wired `check.dependsOn`; `verifyCoreDependencyAllowlist` (line 385) present; `verify-ml-denial-controls.sh` Part A plants all three modules. No ML coordinate in any module build file. SC4 gates green per driver | closed |
| T-13-06 | Information disclosure | git history (weights, SB gold) | high | mitigate | live: `.gitignore` `*sb-gold* *sb-fixture* sb-a10-fixture*.json` and weight patterns; `verify-repo-hygiene.sh` (c) checks tracked and untracked-not-ignored; temp-index plants in `verify-ml-denial-controls.sh` Part C (incl. sb-fixture, WR-01). Whole-history name scan: nothing | closed |
| T-13-07 | Denial of service | future :ondevice module | medium | mitigate | live: ML denial is `project.name in setOf("core","providers","keystore")` (invariants line 333); no ML token in `bannedRules` or `config/detekt/detekt.yml` | closed |
| T-13-08 | Tampering | git index during controls | low | mitigate | live: `verify-ml-denial-controls.sh` plants via a temporary `GIT_INDEX_FILE` copy, `trap` cleanup, byte-identical restore assertion | closed |
| T-13-SC (13-02) | Tampering | package installs | low | accept | No package installed (net dependency diff empty). See Accepted Risks | closed |
| T-13-09 | Information disclosure | evidence lines | high | mitigate | live: `scripts/spike-evidence-filter.sh` closed-alphabet `ALLOW_RE` (byte-identical to the Kotlin pattern at e362fb228b), whole-capture rejection on key shapes (Anthropic/OpenAI/OpenRouter/HF/Google/GitHub/AWS), credential words, 40+ char tokens, sb `tool/tools/tool_name/arg_keys` fields, digests over 8 hex; all 15 evidence files pass; old proof re-run 12/12. The Kotlin/bash parity test no longer exists (Advisory A5) | closed |
| T-13-10 | Tampering | verdict computation | high | mitigate | live: `verify-spike-verdict.sh --check` has `thresholds_mismatch`, `dirty_harness` and temp-worktree-at-recorded-SHA paths; code SHA `e362fb228b` exists and is an ancestor of HEAD; evidence/thresholds unchanged since the verdict commit. `--check` itself needs Gradle, so it was not re-run (13-10 recorded `SPIKE_VERDICT_CHECK: OK lines=4`) | closed |
| T-13-11 | Repudiation | green claim from partial data | high | mitigate | hist@e362fb228b `VerdictRules.kt` lines 174-176 (`unmeasured:<metric>`), 202 (`fail:incomplete_stage`), `inconsistent:winner`; the shipped verdict shows both envelopes red with unmeasured reasons. Residual edge WR-06 (Advisory A3) | closed |
| T-13-12 | Denial of service | stale Gradle results | low | mitigate | hist@1fec77a^ `spike-ondevice/build.gradle.kts`: `outputs.upToDateWhen { false }` and `outputs.cacheIf { false }` when the evidence property is set | closed |
| T-13-SC (13-03) | Tampering | package installs | low | accept | No new package. See Accepted Risks | closed |
| T-13-13 | Tampering / Elevation | model output to write path | high | mitigate | hist@1fec77a^: `RouteA.parseAuto` / `RouteB.parse` reject any tool name not offered (`malformed_output`) and bad types (`schema_type_mismatch`); `LiteRtBackend` `automaticToolCalling = false`; `RecordingGate` + `NoOpMutation` mean nothing is ever applied | closed |
| T-13-14 | Information disclosure | failures, toString | high | mitigate | hist@1fec77a^: `isStableCode` = `[a-z0-9_]+` enforced by `BackendFailure` and `failure()`; exception messages dropped (only classified); `toString` shows counts/route only | closed |
| T-13-15 | Denial of service | JNI init / OOM | medium | mitigate | hist@1fec77a^: `INIT_FAILED`/`GPU_INIT_FAILED`/`INSUFFICIENT_MEMORY` mapping incl. `OutOfMemoryError`; `SpikeOnDeviceCapability` returns Unavailable after a failed init; EXIT lines from `ApplicationExitInfo`; runner writes host `process_gone` | closed |
| T-13-16 | Spoofing | provider identity | low | accept | hist: `requiresCredential = false`, provider internal to the unpublished spike app (now deleted). See Accepted Risks | closed |
| T-13-SC (13-04) | Tampering | package installs | low | accept | No new package. See Accepted Risks | closed |
| T-13-17 | Information disclosure | SB fixture and gold labels | high | mitigate | live: host copies at mode 700/600 outside the repo; hygiene + `.gitignore` backstop; leak scans clean over HEAD and all history; evidence uses opaque ids and 8-hex prefixes (`fixture_sha=8bc739ed`). hist: `SbEnvelope` reads only `files/private` (assets hold only the synthetic small gold), redacted `toString`. Advisory A1 | closed |
| T-13-18 | Tampering | gold vs fixture drift | medium | mitigate | hist@1fec77a^ `SbEnvelope.kt:99` `fixture_mismatch`; runner `push-private` also refuses a digest mismatch (`fixture_mismatch`, `fixture_pin_mismatch`); `evidence/env.txt` `match=1` | closed |
| T-13-19 | Repudiation | inflated N | medium | mitigate | hist@1fec77a^ `GoldSet.kt`: `dup_id`, `below_minimum:<bucket>`, `Minima.DEFAULT` 50/50/30, forced subset 20; confirm evidence has 144 distinct items | closed |
| T-13-20 | Tampering / Elevation | write path | medium | mitigate | hist@1fec77a^ `SpikeResolver.kt` `NoOpMutation`, `RecordingGate.kt` records only; false writes counted (2/34), never applied | closed |
| T-13-SC (13-05) | Tampering | package installs | low | accept | No new package. See Accepted Risks | closed |
| T-13-21 | Tampering (wrong device) | every device subcommand | high | mitigate | hist@1fec77a^ `run-spike-ondevice.sh`: fixed `TESTER_USB`, `-s` on every call, USB first, `ro.serialno`+model+SDK identity proof, personal IP refused, foreign `ANDROID_SERIAL` refused, no target option. Re-ran the guard proof: 88 scenarios OK, tripwire clean. Evidence: all `target=R5CT10XNKQN`, `device_model=SM-S908U`; transcripts show no other serial | closed |
| T-13-22 | Elevation (unauthorised device use) | device subcommands | high | mitigate | hist@1fec77a^ `host_precheck`: exact `grant: open` required before the lock and any adb call; one window per grant (stamp); live: `13-WINDOW-GRANT.md` `grant: consumed`, `closed: 2026-10-06T05:52:07Z`; post-consume `preflight` returned `window_not_granted` | closed |
| T-13-23 | Tampering | model files | high | mitigate | hist: revision-pinned HF URL, size+sha256 checked on host before push and on device after, mismatch deletes the file. live: `evidence/models.txt` `sha=18193810`/`a53a5900` `match=1` equal the pins | closed |
| T-13-24 | Information disclosure | SB fixture/labels in transit | high | mitigate | hist: `push_private_file` stages under `/data/local/tmp`, copies via `run-as` into `files/private`, `rm_stagings` on every exit and signal, read-back digest; cleanup proves no `vae-spike-*` staging left; evidence shows 8-hex prefixes only | closed |
| T-13-25 | Information disclosure | evidence capture | medium | mitigate | hist: `do_pull_evidence` pipes through the filter in a private temp dir, rejected capture writes nothing; live: filter, evidence clean | closed |
| T-13-26 | Denial of service | TESTER state for Phase 19 | medium | mitigate | hist: `do_cleanup` with three positive proofs; transcript: `SPIKE_ONDEVICE: OK sub=cleanup` 05:52:03Z ("package, external model directory and staging files removed"), `cooldown status=0` 05:52:07Z | closed |
| T-13-SC (13-06) | Tampering | model downloads | medium | mitigate | hist: no package manager; weights pinned by revision and sha256; Gemma 3 1B is a human-only download and `fetch-model g3_1b` refuses | closed |
| T-13-27 | Elevation | exported SpikeActivity | medium | mitigate | hist@1fec77a^: reads only the `stage` extra via `Stage.fromWire` (closed list), unknown finishes; manifest has no intent filter and no permission; debug app uninstalled by cleanup | closed |
| T-13-28 | Information disclosure | EvidenceLog | high | mitigate | hist@1fec77a^ `EvidenceLog.kt`: only `SpikeLine` accepted (no string overload); sole `android.util.Log` user (git grep); host filter re-checks on pull | closed |
| T-13-29 | Repudiation | process death mid-stage | medium | mitigate | hist: append-only unbuffered per-line writes, `ApplicationExitInfo` EXIT lines, runner host `process_gone`; verdict flags incomplete stages. Residual edge WR-06 (Advisory A3) | closed |
| T-13-30 | Denial of service | thermal corruption | medium | mitigate | hist: in-app cooldown to light, `COOLDOWN_CAP_MS` 300 s, THERMAL lines; transcript shows 20+ runner `cooldown` OKs | closed |
| T-13-SC (13-07) | Tampering | package installs | low | accept | No new package. See Accepted Risks | closed |
| T-13-31 | Tampering (wrong device) | all device steps | high | mitigate | Runner-only (24 runner calls, 0 direct adb in the 13-08 executor transcript); guard proof re-run 88/88; all evidence `target=R5CT10XNKQN`; the only other adb use is read-only listings/availability checks addressed to the TESTER | closed |
| T-13-32 | Elevation | device time outside a window | high | mitigate | grant history: `grant: pending` (164cab1) -> `open` (08ada33, 01:51:38Z, relayed by orchestrator 3b) -> `consumed` (74ba3d3); window start 01:51:40Z is after the grant; box enforced (see time-box re-check); consumed grant refuses re-runs | closed |
| T-13-33 | Information disclosure | SB fixture and labels on the TESTER | high | mitigate | app-private storage only (`files/private`), removed by `cleanup` with a positive proof (T-13-26); nothing SB-derived in evidence (grammar-filtered) or history (scans) | closed |
| T-13-34 | Repudiation | numbers improved by re-runs | medium | mitigate | one commit per stage evidence file; no duplicate STAGE or header lines; `thresholds_sha` in ENV; verdict is code over the evidence and the headline numbers recompute exactly. Advisory A8 (post-window RT-03 matcher tolerance, zero rows changed) | closed |
| T-13-35 | Denial of service | Phase 19's TESTER | medium | mitigate | cleanup proof and final cooldown OK at window close; driver announced "device done tester" at 05:52:38Z (main-session transcript) | closed |
| T-13-SC (13-08) | Tampering | model weights | medium | mitigate | sha256 on host and device (`models.txt` match=1 x2); Gemma 3 1B `skipped_gated` in the grant, runner refuses `push-model g3_1b` while the grant value starts with `skip` | closed |
| T-13-36 | Tampering | 13-VERDICT.md numbers | high | mitigate | live: machine block carries `thresholds_sha=ec4933fb` and `SPIKE_VERDICT_CODE sha=e362fb228b`; headline numbers recompute from committed evidence; evidence/verdict/thresholds unchanged since f4f2d86; `--check` OK recorded at 13-10 | closed |
| T-13-37 | Repudiation | verdict relay | medium | mitigate | live: `13-VERDICT-MESSAGE.md` records `relayed_to` and `relayed_at` with an explicit "HANDOFF, not a confirmed delivery" note rather than claiming delivery | closed |
| T-13-38 | Information disclosure | verdict and message | medium | mitigate | live: aggregates only; zero `b_` item ids in `13-VERDICT.md`, `13-VERDICT-MESSAGE.md`, `13-DISPOSITION.md`, `13-THRESHOLDS.md`. Advisory A1 (one illustrative gold-derived word in 13-VERDICT.md) | closed |
| T-13-39 | Tampering | §11 ledger | medium | mitigate | live: no ledger text, contract edit or tag in the phase diff (STATE/ROADMAP/REQUIREMENTS changes contain no ledger lines) | closed |
| T-13-SC (13-09) | Tampering | package installs | low | accept | No package installed. See Accepted Risks | closed |
| T-13-40 | Tampering (supply chain) | build at the tag | high | mitigate | live: `verify-spike-disposition.sh removed` -> OK (module, include, litertlm keys, coordinate in build scripts, jitpack, allow-listed `litert` token scan); no `spike-ondevice/` or `ondevice/` on disk; `settings.gradle.kts:25` includes only `:core :providers :keystore :sample` | closed |
| T-13-41 | Repudiation | disposition choice | medium | mitigate | live: `13-DISPOSITION.md` branch `red` equals the mechanical rule over the two `verdict=red` lines in `13-VERDICT.md`; `harness_sha: 08ada3366b` and verdict code `e362fb228b` both recorded | closed |
| T-13-42 | Denial of service | lost reproducibility | medium | mitigate | live: `spike-evidence-filter.sh`, `verify-spike-verdict.sh`, evidence, verdict, thresholds kept; `--check` falls back to a temp worktree at the recorded SHA (fail-closed `code_sha_unavailable`). Advisory A4 (SHAs only on the unmerged phase branch) | closed |
| T-13-SC (13-10) | Tampering | package installs | low | accept | Removal only. See Accepted Risks | closed |
| T-13-43 | Tampering (supply chain) | consumers' classpaths | high | mitigate | moot: plan 13-11 skipped (branch=red), no `:ondevice` artifact exists; `verifyNoMlArtifacts` still guards the three published modules (T-13-05); no LiteRT token in published `api.txt`/docs. See Advisory A6 | closed |
| T-13-44 | Elevation | on-device writes | high | mitigate | moot: no on-device module or core change; `core/` main sources untouched by the phase | closed |
| T-13-45 | Information disclosure | logs | medium | mitigate | moot: no module; `android.util.Log` stays a detekt `ForbiddenImport` for library modules | closed |
| T-13-46 | Denial of service | consumer process | medium | mitigate | moot: no module published | closed |
| T-13-47 | Tampering | public API freeze | medium | mitigate | moot: no `ondevice/api.txt`, no LiteRT type in any `api.txt` | closed |
| T-13-SC (13-11) | Tampering | litertlm in a published module | medium | mitigate | moot: no coordinate in any published module; disposition gate and T-13-05 deny it | closed |

*Status: open · closed · open - below block_on threshold (non-blocking)*
*Severity: critical > high > medium > low; only open threats at or above `block_on: high` count toward `threats_open`.*

## Accepted Risks Log

| Risk ID | Threat Ref | Rationale | Accepted By | Date |
|---------|------------|-----------|-------------|------|
| AR-13-01 | T-13-SC (13-02, 13-03, 13-04, 13-05, 13-07, 13-09, 13-10) | Plans that install no package. Verified: net diff of `gradle/libs.versions.toml`, `settings.gradle.kts`, `jitpack.yml` and every module build file is empty apart from the ML-denial gate | PLAN threat registers (disposition accept); verified by gsd-security-auditor | 2026-10-06 |
| AR-13-02 | T-13-16 | Throwaway `ON_DEVICE` provider had `requiresCredential = false`, no key, and lived only in the unpublished spike app, now deleted | PLAN 13-04 register; verified by gsd-security-auditor | 2026-10-06 |

## Unregistered Flags and Advisories (non-blocking, not counted)

No `## Threat Flags` section exists in any Phase 13 SUMMARY, so there are no executor-reported flags. Auditor observations:

- **A1 (low)** Planning docs carry a little SB-derived detail: `13-CONTEXT.md` RT-03 names one SB tool name and five opaque `b_` item ids and gives one label-tolerance example; `13-VERDICT.md` repeats that example word; four docs name one SB tool name next to the fixture digest prefix and tool count (review IN-04). SB tool names already appear in pre-v1.1 docs, golden test fixtures and the contract's E4 erratum, so this is not newly sensitive, but it contradicts the "SB tool names never appear" wording. Consider generalising the wording.
- **A2 (info)** Evidence headers and `env.txt` carry the TESTER hardware serial and the planning docs carry tailnet addresses; this follows the repo's existing convention (review IN-03).
- **A3 (low)** Review WR-06 and WR-07 (skipped as report-only): the deleted verdict engine does not flag a host-synthesised `result=timeout` stage with no `trials=`/`planned=` as `fail:incomplete_stage`, and reads sb peak PSS on the winning cell only. Neither can produce a false green here (both envelopes are red from unmeasured gating metrics, and the verdict is pinned to `e362fb228b`). Relevant only if the spike is ever re-run.
- **A4 (low)** The recovery SHAs (`e362fb228b`, `08ada3366b`, `1fec77a^`) exist only on `gsd/phase-13-on-device-model-spike`, not on `main` or `origin`. If that branch is squash-merged or deleted, `verify-spike-verdict.sh --check` fails closed with `code_sha_unavailable`, and the deleted harness is lost. Prefer a merge that keeps the commits, or keep a ref to them.
- **A5 (low)** The evidence filter now has no automated test at HEAD (its Kotlin source, `EvidenceGrammarTest` and `verify-spike-evidence-filter.sh` were deleted by plan 13-10). The auditor re-ran the old proof against the HEAD filter (12 cases OK) and confirmed `ALLOW_RE` is byte-identical to the frozen Kotlin pattern.
- **A6 (info)** The six plan-13-11 threats are closed as moot. Their declared mitigations (opt-in marker, api.txt seed, fallback test, POM scope check, PendingMutation tagging docs) do not exist because plan 13-11 never ran. If a later phase ships an `:ondevice` module, T-13-43..47 and its T-13-SC must be re-verified against real code at that time.
- **A7 (info)** T-13-01's "resolved only through google()" clause: the content filter in `settings.gradle.kts` covers `pluginManagement` only; `dependencyResolutionManagement` lists unfiltered `google()` and `mavenCentral()` and no `exclusiveContent` rule existed. Exact pin and manual audit were present, and the dependency is now gone.
- **A8 (info)** The RT-03 grading tolerance in the matcher (commit `e362fb228b`) was added after the window closed, at SB's request. It is documented in `13-VERDICT.md`, gated by item id, and a host re-score changed 0 rows, so it did not improve any number; it was not a re-run.
- **A9 (info)** Host-private SB fixture, gold labels and raw model answers (`~/.local/share/vae-spike`, 700/600) remain on the host after a red disposition. They are outside the repo and were retained for a possible SB re-score; consider deleting them now that SB has no objection and nothing ships.
- **A10 (info)** Pre-existing and out of this register: `scripts/verify-negative-controls.sh` cannot exit green (3 Phase 12 `api.txt missing once released` plants, review WR-05), which hides its appended Phase 13 Part 5 signal. The Phase 13 ML-denial proof is the separate `verify-ml-denial-controls.sh`, unaffected.
- **A11 (info)** The driver ran read-only `-s R5CT10XNKQN` availability checks (`get-state`, `pm list packages`) at 01:45-01:47Z, a few minutes before `grant: open` was recorded. Read-only, TESTER-addressed, no state change.

## Security Audit Trail

| Audit Date | Threats Total | Closed | Open | Run By |
|------------|---------------|--------|------|--------|
| 2026-10-06 | 58 | 58 | 0 | gsd-security-auditor (ASVS 1, block_on high), persisted by the secure-phase run |

## Sign-Off

- [x] All threats have a disposition (mitigate / accept / transfer)
- [x] Accepted risks documented in Accepted Risks Log
- [x] `threats_open: 0` confirmed
- [x] `status: secured` set in frontmatter

**Approval:** verified 2026-10-06

## Verdict

SECURED. threats_open: 0.
