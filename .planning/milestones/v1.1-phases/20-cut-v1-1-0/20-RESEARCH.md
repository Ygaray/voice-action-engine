# Phase 20: Cut v1.1.0 - Research

**Researched:** 2026-10-07
**Domain:** release tooling for a multi-module JitPack library (bash gates, Metalava api.txt baselines, tag/ledger protocol). Almost no product code.
**Confidence:** HIGH on what the scripts do today (read in full this session); MEDIUM on the design of the few script edits and on RT-07; the cut itself is a host-memory risk, not a knowledge gap.

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

- **D-01 [new-module-baseline]:** Seeded api.txt + a "new in this release" branch in gate 12; core/providers/keystore keep today's `comm -23` additive check vs v1.0.1 plus executed metalavaCheckCompatibility. _(provisional — refresh at execution; depends on Phase 17)_ _(source: ai-auto)_
- **D-02 [binary-diff]:** Required pre-preflight evidence file (lighter on the OOM-prone host) + reviewed seam artifact; update the sealed-type allowlist for any v1.1 additions. _(source: ai-auto)_
- **D-03 [tooling-paths]:** Retarget to `.planning/releases/v1.1.0/` well before the wiring SHA (gate 7 rejects scripts/ changes after it). Fold into Phase 17's tooling work or an early Phase-20 plan. _(source: ai-auto)_
- **D-04 [module-list]:** Manifest with a `dependsOnCore` column (gate 15 and jitpack-live-probe require a core dep that `:undo` lacks; api-dump-isolated's package grep can't match `voice-adapter`; dry-run exact-set; gate 7 allowlist; tag message; hygiene loops). _(provisional — refresh at execution; depends on Phase 17)_ _(source: ai-auto)_
- **D-05 [waiver]:** The packet (gate 8 rejects NO-WAIVERS for a minor); ask Yahir before the quiet window, not inside it. _(source: ai-auto)_
- **D-06 [push]:** Push main at the start of Phase 20 (gate 5 requires HEAD == origin/main; wiring rerun needs a JitPack SHA). This is "with v1.1.0" per Yahir's hold — confirm it explicitly. All code/docs/ECOSYSTEM/tooling + final apiDump committed at or before the wiring SHA; only .planning commits after; orchestrator §11 commits frozen during the quiet window. _(source: human)_ **(locked — operator-reviewed)**
- **D-07 [host-oom]:** Preflight first, then cut (5–6 modules raise the load; v1.0.1 needed 6 attempts). Clean-tree gate already excludes .gsd/, milestone.lock and stage markers — no .gitignore change. _(source: ai-auto)_

### Runtime Decisions (binding; CONTEXT.md "## Runtime Decisions", verbatim)

- **RT-01 [dedupe-constants] (2026-10-06, orchestrator 3b pre-tag ruling on P15 IN-02):** Before the cut, dedupe the frozen plan field-name constants duplicated across PlanParse.kt and PlanSchema.kt, but ONLY if it is a trivial one-line cleanup with no API or behavior change; otherwise leave it. It must land before the wiring SHA (D-09: no doc or code edits after it).
- **RT-02 [undo-seed-reds] (2026-10-06, orchestrator 3b):** Phase 20 OWNS the deliberate reds from P17 17-04: release-cut gates 10 and 12 plus selftest step 4 fail for the new undo seed until P20 adds its new-module branch (see 17-SURFACE-REVIEW.md). The P20 plan MUST include a task that adds that branch and a check that gates 10/12 and selftest step 4 are green again before the cut.
- **RT-03 [actionevent-tostring] (2026-10-06, master; P17 deferred obligation IN-04):** Before the tag, settle the ActionEvent.toString() redaction policy (whether parentRunId and heldRunId are printed or redacted) and record why in the KDoc. HeldRunIdTest pins the current behavior, so a policy change must update that test.
- **RT-04 [p17-api-reconcile] (2026-10-06, orchestrator 3b FYIs):** P20 reconciles the keystore api.txt re-dump (+9 KeyAccess lines from P12) and the 2 :undo suppressions (expected 1, P17 OI-5).
- **RT-03 RULED (2026-10-06, orchestrator 3b; IN-04):** REDACT by default. ActionEvent.toString() shows type, ids (runId, parentRunId and heldRunId are ids and may be shown), tier, status and counts. It NEVER shows arg values, utterance text, model output or key material; those render as `<redacted:N chars>` or similar. Any debug accessor must be explicit and opt-in, never toString. Add one test that a sentinel arg value never appears in toString(), and update HeldRunIdTest if the shape changes. This lands in P20, or in P18 if it fits there, before the wiring SHA.
- **RT-05 [p18-carries] (2026-10-07, master):** Wire scripts/verify-stt-confinement.sh into release-cut.sh, next to the gates 10/12 + selftest step 4 new-module branch, which now covers BOTH :undo and :voice-adapter (RT-02).
- **RT-06 [p18-design-calls] (2026-10-07):** WR-01: the ActionEvent.toString KDoc privacy rationale contradicts printing runId/parentRunId. Per the orchestrator IN-04 ruling (RT-03), ids MAY be shown, so fix the KDoc wording to match; do not redact the ids. WR-04 (positional overload trap: a String passed as the one-arg context to commandInputOf / FinalSegment.toCommandInput silently becomes context and not parentRunId) was RULED option (b), drop the context-only overloads, by the orchestrator on 2026-10-07. It lands in P19 (19-CONTEXT RT-04), not P20.
- **RT-07 [key-fingerprint] (2026-10-07, orchestrator 3b):** Before the cut, the :sample key-import screen stops showing the last 4 key chars. It shows a non-reversible fingerprint (the first 6 hex of sha256(key)) plus Ready/Missing, and the Gate-1 UI helper must never echo that field. Add one test.
- **RT-08 [c7-defaults-final] (2026-10-07, orchestrator 3b):** At the cut, mark the P19 carry-register C7 items (P14-P17 open items with shipped defaults) FINAL, citing the orchestrator OI rulings, and list them in the Gate-2 packet so Yahir sees them once. Fix the stale STATE.md PD-04 overload counts: toCommandInput 2, commandInputOf 2.
- **[new-module-baseline] refreshed (2026-10-07, ai-auto):** Applies to TWO new modules, :undo (P17) and :voice-adapter (P18). Each keeps a seeded header-only api.txt until the cut dump, and gate 12 needs a "new in this release" branch for both: no v1.0.1 baseline, so the check is "the dump is reviewed and committed at the tag" (19-API-REVIEW.md covered all five modules with 0 removals). core/providers/keystore keep the comm -23 additive check vs v1.0.1 plus executed metalavaCheckCompatibility. keystore api.txt must be re-dumped first: +9 KeyAccess lines from P12 (RT-04). Gates 10/12 and selftest step 4 share this new-module branch (RT-02).
- **[module-list] refreshed (2026-10-07, ai-auto):** ALREADY SHIPPED in P17: scripts/modules.list (columns name packaging artifactId kotlinPackage dependsOnCore), read through scripts/lib/modules.sh, with 5 rows. P20 only switches the remaining release-cut consumers onto it: gate 15 and jitpack-live-probe skip the core-dep check where dependsOnCore=no; api-dump-isolated greps kotlinPackage (voiceadapter), not the module name; dry-run uses the exact 5-artifact set; gate 7 allowlist; tag message; hygiene loops. Plus RT-05: wire verify-stt-confinement.sh into release-cut.
- **RT-09 [cut-protocol] (2026-10-07, orchestrator 3b):** (1) PUSH main at phase start per Yahir P20 ruling (D-06): the master first messages the orchestrator "pushing main <sha>" and pushes ONLY after its OK. (2) TAG: once EVERY release-cut gate is green on the final SHA, including C4 (wiring re-run on the final SHA) and C11 (docs gate with VAE_DOCS_REQUIRE_PINNED_TAG=1), the master sends "tag ready v1.1.0 <sha>" with the gate evidence; the orchestrator OKs; the master pushes the tag; then the master sends the section-11 ledger-row relay (the orchestrator writes the ledger; NEVER commit section 11 here). (3) If the cut fails or is abandoned, REVERT the README/ECOSYSTEM v1.1.0 announcement before stopping. (4) The cut quiet window is pre-approved on the same terms: "quiet window 20-xx", and the orchestrator checks swap and memory and takes the lock. (5) Accepted: P19 calls (1) and (2), with C4 covering the post-wiring script fixes.

### Claude's Discretion
Areas marked `ai-auto` took research's recommendation without operator review; the planner may refine mechanics within the stated decision but must not reverse it without a new discuss pass.

### Deferred Ideas (OUT OF SCOPE)
None — discussion stayed within phase scope.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| VER-07 | `v1.1.0` is cut only on green verification: API strictly additive vs v1.0.1 (`apiDump` diff `+`-only); seams honor the contract; all published modules (core, providers, keystore, undo, voice-adapter) build on JitPack; the section 11 row is messaged to the orchestrator (REQUIREMENTS.md:78-82) | "What is already done vs still to do" (gate table), "Cut sequence", "Pitfalls", "Validation Architecture". ROADMAP SC1-SC3 map onto gates 1-15 + live probe + ledger relay. |
</phase_requirements>

## Summary

**Most of the "module-list" work D-04 describes is already shipped.** `scripts/release-cut.sh` reads HEAD's `scripts/modules.list` (L89-95), so gate 7's allowlist, gate 10's loop, gate 12's loop, the tag message, `api-dump-isolated.sh` (kotlinPackage grep), `jitpack-dry-run.sh` (exact artifact set), `jitpack-live-probe.sh` and gate 15 (`published_versions.py`, dependsOnCore-aware) are all manifest-driven already. D-03 (tooling paths) is also done: `RELEASE_DIR=".planning/releases/v1.1.0"` (L79) and `agent-wiring-test.sh` `ASSET_DIR` (L28) point at the stable directory, and no script still references `phases/11-cut-v1-0-0`. What genuinely remains in tooling is small: one new gate-12 branch (+ controls), the `verify-stt-confinement.sh` wiring (RT-05), and the data steps (commit the five `api.txt` dumps, write `WAIVER-PACKET.md`). The deliberate reds of RT-02 are therefore two different things: gate 10 and selftest step 4 are red only because the committed `undo/api.txt` and `voice-adapter/api.txt` are one-line seeds (and keystore lacks 9 lines); committing the cut dump turns both green with no script edit. Only gate 12 needs code.

**The cut is gated by ordering, not by knowledge.** Gate 7 rejects any change outside `<module>/api.txt` and `.planning/` after the wiring SHA, and the wiring test costs a live headless agent run. Every code, doc, sample and script edit (RT-01/03/06/07, five doc stumbles C9, gate-12 branch, RT-05) must be done, selftested and pushed before the new wiring SHA is chosen. That forces a first quiet window (selftest, dump, heavy local gates) before the wiring SHA and a second (preflight + cut) after it.

**Two traps in the push step.** `main` is 536 commits ahead of and 1 behind `origin/main` (`43768ea`, an orchestrator ledger row). A `git pull --rebase` would rewrite all 536 commits and orphan every recorded SHA (wiring SHA, `1869950dca` Gate-1 build head used by the C10 delta check). Merge instead (A1). And the strict docs gate C23 (`VAE_DOCS_REQUIRE_PINNED_TAG=1`) checks a LOCAL tag ref, which only exists after `cut` has already created it, so RT-09's "C11 green before tag-ready" needs a throwaway-clone tag simulation (A3).

**Primary recommendation:** Treat Phase 20 as five ordered blocks: (0) sync + push handshake, (1) all pre-W edits (RT-01 leave, RT-03/06 KDoc, RT-07 sample, C9 docs, gate-12 branch + RT-05 + selftest controls, waiver packet, dumps), (2) quiet window 20-01 (selftest all, dumps, javap evidence, local heavy gates), (3) push W, live-probe W, isolated wiring test on W, (4) quiet window 20-02: preflight, handshake, cut, live-probe the tag, strict docs gate, ledger relay.

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Release gating (15 gates, tag push) | `scripts/release-cut.sh` (host bash) | git remote (origin) | Gates read HEAD content only; the tag push is the one irreversible act. |
| Module/artifact list | `scripts/modules.list` + `scripts/lib/modules.sh` | consumers: every script | Single manifest; `verify-module-manifest.sh` proves it matches settings/jitpack/build files. |
| Public-API baseline | per-module `api.txt` (Metalava) | `core` ApiShapeTest etc. | Released baseline = committed dump; compat check runs in Gradle `apiCheck`. |
| JitPack build proof | JitPack (remote) | `jitpack-live-probe.sh`, `jitpack-dry-run.sh` | Dry run emulates; live probe confirms the real build by SHA then by tag. |
| Ledger row | orchestrator (sole writer, A14) | master relays | Never committed in this repo. |
| Key custody display (RT-07) | `:sample` UI | `:keystore` (unchanged API) | Fingerprint is a sample concern; `KeyState.Ready.last4` stays in the frozen `:keystore` API. |

## What is already done vs still to do (scouting, file:line)

All line numbers are `scripts/release-cut.sh` unless noted. [VERIFIED: read in full this session]

`GATE_ORDER=(tag-format tags-absent create-tag clean pushed wiring diff waiver check api-dump hygiene api-check dry-run leak version)` (L108). Numbering is that order.

| # | Gate | What it does | Module-list dependent? | Status for v1.1.0 |
|---|------|--------------|------------------------|-------------------|
| 1 | tag-format | strict `vX.Y.Z`, wiring SHA resolves (L195-202) | no | green |
| 2 | tags-absent | tag absent locally and on origin, strictly newer than every release tag (L206-220) | no | green (verified: `GATE OK tags-absent` for v1.1.0 now) |
| 3 | create-tag | `.planning/config.json` `git.create_tag` is exactly false (L224-234) | no | green (config shows `create_tag: False`) |
| 4 | clean | nothing staged; no dirty/untracked file outside `:!.planning :!graphify-out :!.gsd :!.claude/worktrees` (L103, L236-242) | no | green now; D-07 confirmed, no .gitignore change |
| 5 | pushed | `git fetch origin main`, HEAD must equal origin/main exactly (L244-251) | no | RED now: `ahead 536, behind 1` |
| 6 | wiring | wiring SHA is an ancestor of HEAD; `$RELEASE_DIR/WIRING-RERUN.md` in HEAD has `status: pass`, `tested_sha` == W, `consulted_only_workspace: true`, a `WIRING TEST: PASS` line (L253-266) | no | current record is for `090fd8ec76`; must be rewritten for the new W (C4) |
| 7 | diff | since W only paths under `.planning/`, ledger-only rows in `CROSS-REPO-SCOPE-CONTRACT.md`, and `is_module_api` files may change (L325-347; `is_module_api` L116 loops `$MODULES` = HEAD manifest, L89-95) | YES, already manifest-driven | no edit needed. Proven by `scripts/verify-release-manifest.sh` part 1 (undo/api.txt accepted, `sample/api.txt` and `undo/build.gradle.kts` rejected) |
| 8 | waiver | a PATCH may carry `NO-WAIVERS.md`; a minor/major (PATCH 0) never qualifies (L399-412). Otherwise `$RELEASE_DIR/WAIVER-PACKET.md` must have exactly one `## Answer block`, `packet_status: accepted`, `answered_by`/`answered_at` set, answered ids == table ids, every answer in {waive, accept, carry-to-gate-2, ok}, at least one category `C` row, every `C` row answered ok/accept/waive (L414-446) | no | RED: `WAIVER-PACKET.md` does not exist (`ls .planning/releases/v1.1.0` shows only `evidence WIRING-RERUN.md wiring-test`) |
| 9 | check | clean `git archive HEAD`, `./gradlew check --no-build-cache` (L451-463) | Gradle-side | heavy; green at 090fd8e/19-12 |
| 10 | api-dump | `api-dump-isolated.sh --head`, then every manifest module's fresh dump must equal `HEAD:<m>/api.txt` byte for byte (L465-481) | YES, already manifest-driven | RED until the five dumps are committed: `undo/api.txt` and `voice-adapter/api.txt` are the 1-line seed `// Signature format: 4.0`; `keystore/api.txt` is 67 lines vs a 76-line dump (the +9 `DelicateKeyAccess`/`KeyAccess` lines, 17-SURFACE-REVIEW:18, 19-API-REVIEW OI-8). No script edit needed. |
| 11 | hygiene | `PRE_RELEASE=0 verify-repo-hygiene.sh` + `verify-module-manifest.sh` (L483-495) | YES, already manifest-driven | green now (`HYGIENE OK`, `MODULE MANIFEST OK modules=core,providers,keystore,undo,voice-adapter`). **RT-05 adds `verify-stt-confinement.sh` here** (currently `STT CONFINEMENT OK checks=6`, not called by release-cut: grep over scripts finds it nowhere else). |
| 12 | api-check | `api_baseline_check` (L501-526): per module, `git cat-file -e "$prior:$m/api.txt"` else fail; same MAJOR.MINOR = byte-identical, else `comm -23` removal check; then Gradle `apiCheck` and each `^> Task :$m:metalavaCheckCompatibility[A-Za-z]*$` must appear un-suffixed (L541-544) | YES, loop is manifest-driven; **the baseline lookup is not new-module aware** | RED, verified by running it now: `RELEASE GATE FAIL api-check: undo/api.txt is not in the previous release v1.0.1, so there is no baseline`. Needs the new-module branch. |
| 13 | dry-run | `jitpack-dry-run.sh` with `DRYRUN_VERSION=<tag>`: runs jitpack.yml install list verbatim, asserts the published artifact set equals `vae_artifacts_sorted` (exact), no sample/test-fixtures artifact, then the 4-consumer probe (L549-563; dry-run L41-46) | YES, manifest-driven | green in P19 (`DRY RUN OK ... PROBE OK incl :undoalone and :adapteralone`) |
| 14 | leak | python scan of tracked HEAD content: A10 fixture name, key shapes (L568-681) | no | not touched by this phase |
| 15 | version | `version_static_checks` (root build reads `VERSION`, jitpack.yml never sets it) then `published_versions.py <m2> <group> <tag> <manifest>`: POM + `.module` carry the tag; `dependsOnCore=yes` modules depend on core at the tag; `dependsOnCore=no` non-core modules must NOT (L702-711; `scripts/lib/published_versions.py` L1-12, 38-60) | YES, already dependsOnCore-aware | NOT red. The CONTEXT text predates P17's fix (commit f1f14ae). Proven by `verify-release-manifest.sh` part 2 (undo POM naming core is rejected). It has never run against a real 5-module cut m2, so keep it in the evidence. |

Other consumers [VERIFIED by reading]:

| Script | State |
|--------|-------|
| `run_cut` tag message (L768-789) | manifest-driven: `for m in $MODULES; do coords=... vae_artifact_of "$m"` (L780-781). Emits one coordinate per module plus "Wiring-tested SHA". No edit. |
| selftest **step 4** (L902-915) | after `./gradlew apiDump` in the sandbox, `SB_APIS` comes from the sandbox manifest; if all are tracked it demands `git diff --quiet -- "${SB_APIS[@]}"` else `sf "apiDump changed an api.txt that is already tracked: the released baseline must not move"`. Red today because undo/voice-adapter seeds and keystore differ from the fresh dump. **Goes green when the five dumps are committed** (no tracked file then changes). The "none tracked" branch (`git add` + commit) stays for old HEADs. |
| selftest happy path / controls | `SELFTEST_PRIOR_TAG="v1.0.0"`, `SELFTEST_TAG="v1.0.1"` (L72-73) so the sandbox runs the PATCH (byte-identical) branch of gate 12, which already passes once step 4 is green: the sandbox prior tag is cut on the commit that holds all five api.txt. The new-module branch is therefore NOT exercised by the existing selftest; it needs its own controls (see Gate-12 design). |
| selftest **step 3** (L895-899) | `sed -i ... "$SB_CLONE/$WAIVER_PACKET"` runs on a file that does not exist yet; with `set -e` the whole selftest dies. **`WAIVER-PACKET.md` must exist (uncommitted is fine: it is overlaid from the working tree, L873-876) before `selftest` can run.** Controls also hard-code id `W07` (`ctl_waiver-id-missing`, L1346-1354), require a category `C` row, and use `gate waiver v1.1.0` for the minor-release control (L1396-1400). The new packet needs W01..W07+ and at least one `C` row, or those controls must be edited. |
| `scripts/jitpack-live-probe.sh` | manifest-driven: `EXPECT_MODULES` defaults to `vae_artifacts`; `needs_core="$(vae_module_field "$mod" dependsOnCore ...)"`; `yes` requires the core dependency under the served group, `no` forbids it; unknown module fails (WR-08). Exit codes 0/2/3/4/5. No edit. |
| `scripts/api-dump-isolated.sh` | manifest-driven: `pkg="$(vae_module_field "$m" kotlinPackage)"` then `grep -q "^package $PKG_ROOT\.$pkg"` (so `voiceadapter`, not `voice-adapter`). No edit. |
| `scripts/jitpack-consumer-probe.sh` | not manifest-driven but already explicit for all five (`:jvmconsumer`, `:app`, `:undoalone`, `:adapteralone`); no edit unless a sixth module is ever added. |
| `scripts/verify-repo-hygiene.sh`, `verify-module-manifest.sh`, `verify-negative-controls.sh`, `verify-docs-coverage.sh` (C01/C20), `agent-wiring-test.sh` (W3 `MODULE_ALT`) | manifest-driven. No edit. |
| `scripts/verify-api-seed.sh <module>` | asserts a header-only seed (exit 2 otherwise). It becomes obsolete for undo/voice-adapter once the dumps are committed; nothing in `check` calls it. |
| `scripts/review-api-surface.sh` | sealed allowlist already updated (`undo` allows exactly `undo.UndoResult`; others none, core the unchanged seven). D-02 "update the sealed-type allowlist" is done; no v1.1 addition to core's seven (19-API-REVIEW per-module table: "the unchanged seven in core"). |

### Why the seed reds exist and what removes each

| Red | Cause | Removal |
|-----|-------|---------|
| gate 10 | committed seeds/keystore stale vs fresh dump | commit `scripts/api-dump-isolated.sh --out <d>` output as `<m>/api.txt` for all five (undo=191 lines, voice-adapter=16, keystore=76, core=1956, providers=121 per 19-API-REVIEW:14-17; **re-dump, do not copy those numbers**) |
| selftest step 4 | same, in the sandbox | same commit |
| gate 12 | `git cat-file -e "$prior:$m/api.txt"` fails for modules absent from v1.0.1 (`v1.0.1` tree has no `undo` or `voice-adapter`; verified `git cat-file -e v1.0.1:undo` fatal) | new branch below |

### Gate-12 new-module branch (prescriptive design) [ASSUMED mechanics, within D-01]

Inside the `for m in $MODULES` loop of `api_baseline_check`, before the existing `git cat-file -e "$prior:$m/api.txt"` line:

1. Detect "new in this release" by **module directory absent from the prior tag**: `! git cat-file -e "$prior:$m" 2>/dev/null`. Key it on the directory, not on `api.txt`, so a released module whose baseline was deleted still fails ("no baseline"), exactly as today.
2. New module only allowed when `pmm != tmm` (a PATCH release can never introduce a module): otherwise `gate_fail api-check`.
3. Require `HEAD:$m/api.txt` tracked, longer than the one header line, and containing `package io.github.ygaray.voiceactionengine.<kotlinPackage>` (the same non-vacuity test `api-dump-isolated.sh` applies; read the column from HEAD's manifest with a `vae_pkg_of` twin of `vae_artifact_of`, L94). A still-seeded or empty file is a hard fail ("still the header-only seed").
4. Remember the module, `continue`, and print `API NOTE: new in this release (no baseline): <list>`.
5. Keep `comm -23` for the rest. The Gradle half is unchanged: `metalavaCheckCompatibility*` for a new module checks the source against its own just-committed dump, so it executes and is trivially green; the real additive guarantee starts at v1.1.0.

Add a diagnostic `gate api-baseline <tag>` (baseline half only, like `prefreeze` is a diagnostic) so the controls need no Gradle. Controls to add to `CONTROL_ORDER`: (a) new module still seeded -> red "header-only seed"; (b) new module with a populated dump -> green with the NOTE; (c) module present in prior but `api.txt` deleted -> red "no baseline" (guards the directory-keying); (d) a new module in a PATCH release -> red. In a throwaway control clone, build the "prior lacks the module" state by tagging a side commit that removes the module directory (each control owns its own bare remote, L966-980, so re-tagging there is safe).

### RT-05 wiring (prescriptive)

Add `scripts/verify-stt-confinement.sh` as a third check inside `gate_hygiene` (L490-493 pattern: run, require `STT CONFINEMENT OK`, else `gate_fail hygiene`). This keeps the 15-gate PREFLIGHT list and the `gates=` line unchanged. It reads the working tree, like its two siblings; gate 4 (clean) has already proven tree == HEAD. Add one control (plant `libs.stt.engine` or the `:stt` coordinate into a non-adapter build file in a control clone, expect `RELEASE GATE FAIL hygiene` and marker `STT CONFINEMENT FAIL`). Cheap pre-check: `bash scripts/verify-stt-confinement.sh --selftest`.

## RT items: current state and recommendation

### RT-01 PlanParse/PlanSchema constants: **NOT trivial; leave it (record why)**
- `PlanParse.kt:11-15` and `PlanSchema.kt:22-26` each declare `STEPS_FIELD`, `ID_FIELD`, `TOOL_FIELD`, `ARGUMENTS_FIELD`, `NEEDS_LOOKUP_FIELD` as `private const val` (read this session). Path: `core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/strategy/plan/`.
- 15-REVIEW-FIX.md:82-92 records that the fixer already tried the one-line dedupe and dropped it: "A top-level `internal const val` compiles to a public static field, which `ApiShapeTest.noMainClassLeaksAPublicStaticFieldBesidesInstanceAndCompanion` rejects ... A plain `internal val` avoids that but trips detekt `MayBeConst`".
- I confirmed the test: `core/src/test/.../core/ApiShapeTest.kt:75-84` sweeps every class in the main jar (internal included) and fails on any `Modifier.isPublic && isStatic` field other than `INSTANCE`/`Companion`. An `internal object X { const val ... }` also emits public static fields on `X` [ASSUMED: not compiled here], so it trips the same test.
- The only compliant routes are five `internal fun` accessors (precedent: `internal fun planToolName()` at `PlanSchema.kt:41`) which is not a one-line cleanup, or a drift test. **Recommendation: leave the code, record the RT-01 outcome ("not trivial, left per the ruling") in the plan summary, optionally add one test in `core/src/test/.../core/PlanSchemaTest.kt` that feeds `submitPlanSpec(...)`'s own schema keys into the parser** (cheap, test-only, no API effect). Tests today: `PlanSchemaTest.kt`, `PlanParseTest.kt` in `core/src/test/kotlin/io/github/ygaray/voiceactionengine/core/`.

### RT-03 / RT-06 ActionEvent.toString: **behavior already conforms; remaining work is KDoc wording only**
- Code (`core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/commit/CommitSink.kt:44-46`): `"ActionEvent(runId=$runId, parentRunId=$parentRunId, heldRunId=${if (heldRunId == null) "null" else "set"}, " + "action=$action)"`. `ExecutedAction.toString` (L82-84) prints position, kind, applied, toolName, `targetIds.size`, context class simple name, mutating; never an outcome token, target id, provider call id.
- KDoc already fixed by P18 WR-01 (commit `149e906`, 18-REVIEW-FIX.md:41-50): ids printed verbatim, "keep user text out of the ids your run-id seam hands out", held run id shown only as set/null. What is still missing against RT-03 is the stated **why** for printing two ids and reducing the third to set/null.
- Tests: `ActionEventTest.kt` (lines ~190-262) already has the sentinel tests: `SENTINEL` is placed in `appOutcomeToken`, `targetIds` (key and value), `context` (a class whose own `toString` is the sentinel), `providerCallId` and `heldRunId`, with positive controls, and asserts it never appears in `event.toString()` or `action.toString()`; it also pins `heldRunId=set` / `heldRunId=null`. **`HeldRunIdTest.kt` does NOT pin the toString shape** (it asserts `heldRunId` values only; the 17-REVIEW-FIX claim that it does is stale). The "one sentinel test" RT-03 demands is therefore already satisfied.
- **Recommendation (lowest regression risk; the ruling permits it):** keep the current output; add one KDoc sentence on `ActionEvent.toString` giving the rationale (run ids and parent run ids are engine-generated opaque ids and are the join keys a sink needs in logs; `heldRunId` is shown as set/null because it is always equal to the held proposal's `runId` or to a `parentRunId` already printed, so the value adds nothing; read it from the property). Changing `heldRunId` to print its value would force rewriting the sentinel test (it sets `heldRunId = SENTINEL`). A KDoc-only edit changes no `api.txt` line (Metalava dumps carry no KDoc), but it IS a core source edit, so it must land before W. Run `ActionEventTest`, `HeldRunIdTest`, `:core:detekt`.

### RT-04 api.txt reconciliation: **keystore +9 confirmed; :undo suppressions already 1**
- `grep -c KeyAccess keystore/api.txt` = 0 now; the fresh dump holds `DelicateKeyAccess`, `KeyAccess` and the opt-in `ApiKeyStore` constructor (19-API-REVIEW:57 table row "9 lines"). `git diff --stat 673eee0 HEAD -- '*/src/main'` for all five modules is empty, so the 19-08 dump is still current; re-dump anyway at the cut (it is the proof).
- Suppressions: `grep -rn "Suppress\|MaxLineLength" undo` finds exactly one, `undo/src/main/kotlin/io/github/ygaray/voiceactionengine/undo/internal/Guard.kt:1: @file:Suppress("TooGenericExceptionCaught")`. The second (`MaxLineLength` on `UndoJournal.record`) was removed by commit `e1b5653` (17-REVIEW-FIX IN-03, "`record(...)` is now formatted one parameter per line"). `core/.../internal/Guarded.kt:1` carries the repo's other justified one. **So "expected 1" holds; reconcile = write that down.** 19-API-REVIEW.md:184 ("OI-5 two suppressions in :undo ... carried") and 17-09-SUMMARY are stale; record the correction in the waiver packet / carry text, do not edit the closed review files.

### RT-07 key fingerprint in `:sample`
- Where last-4 is shown: `sample/src/main/kotlin/.../sample/keys/KeyVault.kt:110`, `is KeyState.Ready -> if (state.last4.isEmpty()) "Ready" else "Ready - ends in ${state.last4}"` inside `KeyUx.label`, reached via `HeaderText.keyRow` (`ui/HeaderText.kt:55-62`) from `SampleViewModel.refreshKeys` (L288-294, `HeaderText.keyRow(vault.read(provider))`) and rendered at `ui/SampleScreen.kt:133` with `testTag(UiTags.keyState(provider))` = `key_state_<provider>`.
- Constraint: `KeyState.Ready.last4` belongs to the frozen `:keystore` API (`keystore/.../KeyState.kt:21`, built in `SecretReader.kt:107`); do not touch it. The sample only has a `KeyState`, so a sha256 fingerprint needs the key itself. Two ways: (i) `ApiKeyStoreVault` gains `suspend fun fingerprint(provider)` that obtains the key through `KeystoreCredentialSource(store).credential(provider)` (public: `keystore/api.txt:63`; the sample already builds one, `keys/KeySlots.kt:44`) and returns `sha256(key).take(6 hex)` using `java.security.MessageDigest` (never hand-roll); (ii) compute at save/import time from the typed/imported text and hold it in view-model memory (lost on process death). (i) survives restarts and keeps `KeyView` derived from the store; prefer it [ASSUMED design]. `KeyUx.label`/`HeaderText.keyRow` then render `Ready - fp <6hex>` / `Key missing`, with no `last4`.
- "Gate-1 UI helper must never echo that field": the Gate-1 runbook has the tester agent read the screen by tag (`19-GATE1-RUNBOOK.md`: "read `import_status`"; tags in `ui/UiTags.kt`, `key_state_*`). I found no script that reads `key_state_*` [ASSUMED: this is the helper meant]. Enforce at the emitters: `ImportReport` / `EvidenceLine.key(report)` (`evidence/EvidenceLine.kt:257`) must stay free of the fingerprint (note the allow-list regex `scripts/sample-evidence-filter.sh` ALLOW_RE would PASS a 6-hex value, so the guard cannot rely on the filter), and add a runbook line "never copy `key_state_*` text into notes or evidence". 
- Tests to update/add: `sample/src/test/.../sample/KeyVaultTest.kt:48` asserts `KeyUx.label(KeyState.Ready("WXYZ")).contains("WXYZ")` and must flip; fakes at `SampleViewModelTest.kt:84` and `sample/src/testDebug/.../TestKeyImporterTest.kt:38` build `KeyState.Ready(it.takeLast(4))`. The "one test": label never contains the last 4 and contains the fingerprint; a second assertion that no `VAE_*` evidence line contains it.
- Gate-1 evidence caveat: the installed Gate-1 APK is `1869950dca`; `git diff --name-only 1869950dca HEAD -- . ':!.planning'` already lists `ItemToolExecutor.kt` (C10), `PlanLegTest.kt`, `DocSnippetsTest.kt`. RT-07 adds more `:sample` main files, all untested on device. The carry text requires "rebuild and reinstall from the final SHA before any further live TESTER window"; none is planned, so record the final delta (`git diff --stat 1869950dca <final> -- sample`) in the cut evidence and accept it as JVM-proven.

### RT-08 C7 finalisation, PD-04, Gate-2 packet
- STATE.md:226 still reads `PD-04: voice-adapter public names frozen: toCommandInput (3 overloads), commandInputOf (3), ...`. Truth (19-API-REVIEW:101-102): `toCommandInput` 2 (`(FinalSegment)` and `(FinalSegment, Object? context, String? parentRunId)`), `commandInputOf` 2 (`(String, String?)` and `(String, String?, Object?, String?)`). Edit that one line to "(2 overloads)" / "(2)".
- C7 lives in `.planning/phases/19-sample-gate-1-docs/evidence/gate2-carry-register.txt:47-52` (items P14 OI-1,2,3,4,6,7; P15 OI-4,5,6,7; P16 OI-2,3,4,5,7,9; P17 OI-1,3,5; P18 carry (a),(g)). Dispositions per item are in `19-API-REVIEW.md:130-199`.
- **Gap to surface to the orchestrator:** only Phase 14's rulings are recorded in-repo (14-CONTEXT.md RT-02/RT-03: OI-1,3,4,5,7,8 accepted; OI-2 and OI-6 FINAL). I found no recorded orchestrator ruling text for P15 OI-4..7, P16 OI-2..5,7,9 or P17 OI-1,3 (15/16/17 CONTEXT `RT-` lines cover other topics). RT-08 says to cite "the orchestrator OI rulings"; the plan needs a first task that asks the orchestrator for those citations (or states "shipped default, no objection received as of <date>") rather than inventing them (A11). P17 OI-1 (is a `JournalStore` mirror enough) still says "confirm with SB 178 and CT" (19-API-REVIEW:180). P17 OI-5 is now closed by the RT-04 finding above. P18 (g) is closed by RT-05.
- Gate-2 packet: there is no existing Gate-2 packet file; the closest artifacts are `.planning/releases/v1.1.0/WAIVER-PACKET.md` (to be written; the rows Yahir sees once) and the Gate-2 fragments in `.planning/uat-pending/` (`19-sample-gate-1-docs.md`, `18-voice-adapter.md`, drained by the milestone verifier). Put C7 as one block of the waiver packet (see below).

### C9 five doc stumbles (DOC-02) and C11
Source: `.planning/releases/v1.1.0/evidence/wiring-stumbles.txt`. Fix before W, then rerun the docs gate and the wiring judge:
1. router answer tool name/shape (`pick_start_tier`, `{"tier":...}`): INTEGRATION.md "Choosing where the model walk starts" (L103) and API.md "Telemetry and trace".
2. `submit_plan` argument schema (`steps` -> `id`, `tool`, `arguments`; `needs_lookup`): INTEGRATION.md "The plan tier" (L413) and API.md "Strategies and tools". Take the field names from `PlanSchema.kt:22-26` values quoted above.
3. section 11 undo-bridge block (`INTEGRATION.md:1027` marker `doc-snippet: undo-bridge`) lists no imports: needs `EntryRef` (undo), `ActionEvent`, `ActionKind`, `CommitSink`, `RunTermination` (core.commit), `java.util.concurrent.ConcurrentHashMap`. **The block is byte-compared with a compiled region** (`verify-docs-coverage.sh` C06; `DocSnippetsTest.kt` header L125-128; `UndoBridgeParityTest` compares the region to the sample's main bridge), so edit region and doc together, or add the imports in prose outside the fenced block.
4. SingleShot with a read tool in the snapshot (INTEGRATION.md section 5): state what a resolver should do with a read call.
5. `store` shadowing inside `UndoJournal { }`: one warning sentence in section 11 / API.md undo.
C11: C23/C24 state today: `README.md:37` `<!-- pin-version:begin -->`v1.1.0`<!-- pin-version:end -->`; running `bash scripts/verify-docs-coverage.sh` now gives `DOC COVERAGE OK checks=32 types=119` plus the C23 pre-tag NOTE, and with `VAE_DOCS_REQUIRE_PINNED_TAG=1` gives `DOC COVERAGE FAIL: C23: README.md pins v1.1.0 but that tag does not exist` (both run this session). C23 is `git rev-parse -q --verify "refs/tags/$pin"` (`verify-docs-coverage.sh:359-370`), i.e. a LOCAL tag ref.

### D-07 / announcement state (for the RT-09(3) revert task)
Docs already announce v1.1.0 pre-tag: `README.md:21` ("v1.1.0 is the current release"), `:37` (pin marker), `:55-58` (undo/voice-adapter "first published in v1.1.0"); `ECOSYSTEM.md:33`, `:58` ("v1.1.0 is released"), `:61-63`. ECOSYSTEM `:52-53` repin matrix still shows SB/CT at `v1.0.1`. If the cut is abandoned, the revert must restore README pin to `v1.0.1` (C24 needs `vX.Y.Z`, C23 needs the tag to exist; v1.0.1 does) and the "released" wording. Docs edits after W are blocked by gate 7, so the matrix refresh is a post-tag commit.

## Cut sequence (recommended; this is the plan skeleton)

```
Block 0  sync + push (D-06, RT-09(1))
  git merge origin/main   (NOT pull --rebase; A1)         -> "pushing main <sha>" to orchestrator -> OK -> git push origin main
Block 1  pre-W edits (all committed; unit tests via low-memory Gradle recipe, one process at a time)
  RT-01 record (leave) + optional drift test | RT-03/06 KDoc | RT-07 sample + tests | C9 docs (+DocSnippetsTest region sync)
  release-cut.sh: gate-12 branch + api-baseline diagnostic + RT-05 + controls | WAIVER-PACKET.md draft (selftest needs it)
  STATE.md PD-04 line | carry/C7 text | optional javap script
Block 2  QUIET WINDOW 20-01  (swap check, orchestrator lock)
  api-dump-isolated.sh --out D  -> copy 5 dumps to <m>/api.txt, commit ("final apiDump")
  scripts/release-cut.sh selftest all                      (needs waiver packet file + committed dumps)
  verify-negative-controls.sh (only if a gradle/invariants or build file changed; none expected)
  javap descriptor diff evidence (D-02) -> .planning/releases/v1.1.0/evidence/
Block 3  W = last non-.planning commit; push (handshake) -> JitPack builds W
  scripts/jitpack-live-probe.sh W        (C2: all five modules, :undoalone/:adapteralone consumers)
  scripts/agent-wiring-test.sh prepare W  -> orchestrator dispatches isolated agent -> verify -> rewrite WIRING-RERUN.md (status pass, tested_sha W, consulted_only_workspace true, "WIRING TEST: PASS")   (C4, C11 judge)
  waiver packet answered by Yahir via orchestrator (ask BEFORE the window, D-05); commit packet + wiring record (.planning only); push (handshake)
Block 4  QUIET WINDOW 20-02
  release-cut.sh preflight v1.1.0 W   -> strict docs gate in a throwaway clone with a local tag (A3)
  master: "tag ready v1.1.0 <sha>" + evidence -> orchestrator OK
  release-cut.sh cut v1.1.0 W <HEAD>  (re-runs all 15 gates itself, then tags + pushes ONLY refs/tags/v1.1.0)
  post: jitpack-live-probe.sh v1.1.0 ; VAE_DOCS_REQUIRE_PINNED_TAG=1 verify-docs-coverage.sh (real repo) ; evidence -> .planning/releases/v1.1.0/evidence/cut-v1.1.0.txt
  LEDGER-ROW.md (format below) -> relay to orchestrator; NEVER commit section 11
```

Why two windows: any `scripts/` bug found after W makes gate 7 red and voids the wiring pass, so selftest must pass before W. Both `preflight` and `cut` run the heavy gates (9, 12-Gradle, 13) in full: budget two complete gate runs in window 20-02, or stop after a green `preflight` if the window is short and re-request.

Wiring dependency: `agent-wiring-test.sh prepare <sha>` needs "a pushed, JitPack-built commit" (header comment), which is why D-06 pushes first and why the live probe of W (C2) and the wiring test share one SHA. `prepare-local <m2dir> <version>` exists for the unpushed case (used in P19) but is not acceptable for the final record (the real path uses JitPack).

Ledger row format to relay (from `.planning/releases/v1.0.1/LEDGER-ROW.md`): `repo, tag, commit, tag_object, date, coords (all 5, group com.github.Ygaray.voice-action-engine:<artifactId>:v1.1.0), supersedes (none; minor), contents, evidence, jitpack (api/builds status=ok isTag=true modules=...), notes`.

## Waiver packet (D-05, gate 8) [grammar read from `.planning/milestones/v1.0-phases/11-cut-v1-0-0/11-WAIVER-PACKET.md`]

Required shape: a table with rows `| W## | <category> | ... |` (categories A, AE, B, C, D), and one `## Answer block` containing `packet_status:`, `answered_by:`, `answered_at:` and `- W##: <answer>` lines (ids must equal the table ids). Gate rules: minor release => packet mandatory (a NO-WAIVERS file is rejected); at least one `C` row; every `C` row answered `ok|accept|waive`; others may also be `carry-to-gate-2`. Proposed rows (answers are Yahir's, relayed; never inferred):
- C rows (pre-freeze confirmations): the C7 finalised defaults as one or more `C` rows (FINAL per RT-08, cite rulings), the undo surface freeze (17-SURFACE-REVIEW "Frozen names"), voice-adapter names (19-API-REVIEW "Frozen names").
- Carry rows: C1 `submit_plan` cache-prefix measurement (category A/B, owner "separate gap plan or later milestone", SB 177), Gate-2 fragments (router wording quality, real-speech grammar check), C8.
- D/informational: C9 doc stumbles (fixed and re-proven by C4), RT-04 suppression correction, RT-01 "left" outcome.
Document it for Yahir with `python3 ~/.claude/skills/render-doc-for-review/publish_doc.py <file.md>` (global rule), and ask before the quiet window.

## Standard Stack

No external packages are installed in this phase (bash, git, python3 stdlib, Gradle wrapper 9.4.1 already pinned). RT-07 uses `java.security.MessageDigest` from the JDK. **Package Legitimacy Audit: not applicable (no new dependency).** 

| Tool | Version | Purpose |
|------|---------|---------|
| `scripts/release-cut.sh` | in repo | the gated cut, 15 gates + selftest |
| Metalava plugin `me.tylerbwong.gradle.metalava` | 0.5.1 (CLAUDE.md stack) | `apiDump` / `metalavaCheckCompatibility*` |
| detekt | 1.23.8 plain `detekt` task | zero-baseline lint on every library module |
| JDK | OpenJDK 17.0.19 (probed) | Gradle + `javap` |

## Architecture Patterns

### Pattern: manifest-driven script
Every gate loops the manifest (`$MODULES` from HEAD's `scripts/modules.list`, L89-95; helpers `vae_artifact_of`, `manifest_head_file`). New per-module facts (kotlinPackage) get a small awk twin, not a literal.
### Pattern: fail-closed controls
Each gate has a selftest control that must go red for the RIGHT reason (`run_case red <gate> <marker>`, L1009-1045). New behavior ships with controls in `CONTROL_ORDER` (L1571-1584) or it is not done.
### Pattern: diagnostic gates
`prefreeze` (L375) is a named gate outside `GATE_ORDER`; the proposed `api-baseline` follows it (add to `call_gate`, `run_gate`, `usage`).
### Anti-patterns
- Editing `scripts/`, docs, sample or any module source after W (gate 7 red, wiring pass void).
- `git pull --rebase` on the 536 unpushed commits.
- `./gradlew --stop` while another project's daemon is live (Gradle 9.4.1 daemons are shared across repos; memory note).

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| per-module facts in scripts | new literal module lists | `scripts/lib/modules.sh` / HEAD manifest awk | the P17 consistency gate exists |
| api dump for the cut | `./gradlew apiDump` in the real tree | `scripts/api-dump-isolated.sh --out <d>` then copy | never rewrites tracked files; non-vacuity check built in |
| sha256 fingerprint | custom hash | `java.security.MessageDigest.getInstance("SHA-256")` | ASVS V6: no hand-rolled crypto |
| JitPack verification | curl loops | `scripts/jitpack-live-probe.sh <sha-or-tag>` | idempotent, asserts module set, POMs, core dependency per manifest |
| descriptor diff | eyeballing Metalava | `javap -p -s` over v1.0.1 jars vs HEAD jars (D-02) | Metalava does not show synthetic `$default` constructors |

## Common Pitfalls

1. **Rebase rewrites 536 commits.** All recorded SHAs (`090fd8ec76`, `ec24a19`, Gate-1 `1869950dca`) stop being ancestors; C10's delta check and any `git diff <sha>` break. Merge `origin/main`. Warning sign: `git status -sb` shows `ahead 536, behind 1`. Origin may gain more ledger rows; each new one makes gate 5 red until synced, hence "§11 commits frozen during the quiet window" (D-06).
2. **Selftest needs the waiver packet file first** (step 3 `sed -i` on a missing file) and a committed final dump (step 4).
3. **Gate 7 timing.** Anything but `.planning/` and `<m>/api.txt` after W is red. The `api.txt` allowance exists, but D-06 wants dumps before W; do that (the dump needs no W, and it keeps the tested tree equal to the released tree).
4. **C23 strict needs a local tag**, which does not exist before `cut`. Simulate in a throwaway clone (`git clone . $T; git -C $T tag v1.1.0; (cd $T && VAE_DOCS_REQUIRE_PINNED_TAG=1 bash scripts/verify-docs-coverage.sh)`), then repeat for real after the tag. Do NOT create a throwaway tag in the real repo (gate 2 would go red and a tag is immutable on origin).
5. **Docs edits must keep C06 byte-equality** with `DocSnippetsTest` regions and C20 (every public type named in API.md); rerun `bash scripts/verify-docs-coverage.sh` after every doc edit (cheap, no Gradle).
6. **Wiring judge was tightened after the 090fd8ec76 pass** (WR-03: W5/W6/W10-W13). The final run uses the tightened judge; run `scripts/agent-wiring-test.sh selftest-source` (no Gradle) before dispatching.
7. **Earlyoom.** Swap is full right now (`Swap: 2.0Gi 2.0Gi used, 1.3Mi free` probed) and MemAvailable ~11 GiB; earlyoom runs with `-m 15,8 -s 10,5`. Never pipe the cut through `| tail` (capture the real exit status). If killed: stop and report, do not loop.
8. **Metalava dump warning, not a finding:** `ErrorType clock` for the `kotlin.time.Clock` builder property (19-API-REVIEW:49-52); do not "fix" it in a dump.
9. **`gate version` standalone** runs `version_static_checks`, then the dry run, then the check (L748); in preflight it relies on `DRY_M2` set by gate 13 (L560); do not reorder `GATE_ORDER`.
10. **RT-07 fingerprint and the evidence filter:** a 6-hex string matches `ALLOW_RE`; the guard must be at the emitter, not the filter.

## Code Examples

Gate-12 branch skeleton (new lines only) [ASSUMED mechanics]:
```bash
# in api_baseline_check, per module, before the "$prior:$m/api.txt" test
if ! git cat-file -e "$prior:$m" 2>/dev/null; then            # directory absent in the previous release: new module
  [ "$pmm" != "$tmm" ] || gate_fail api-check "$m is not in $prior but $tag is a patch release"
  git cat-file -e "HEAD:$m/api.txt" 2>/dev/null || gate_fail api-check "$m/api.txt is not tracked in HEAD"
  git show "HEAD:$m/api.txt" | grep -q "^package io.github.ygaray.voiceactionengine.$(vae_pkg_of "$m")" \
    || gate_fail api-check "$m is new in this release but its api.txt is still the header-only seed (or lacks the $m package)"
  NEW_MODULES="${NEW_MODULES:+$NEW_MODULES }$m"; continue
fi
```
Cut dump (Block 2):
```bash
export GRADLE_OPTS="-Dorg.gradle.daemon=false -Dorg.gradle.workers.max=2 -Dorg.gradle.parallel=false -Dkotlin.compiler.execution.strategy=in-process -Dorg.gradle.jvmargs=-Xmx1536m"
D="$(mktemp -d)"; scripts/api-dump-isolated.sh --out "$D"; echo "exit=$?"     # real exit status, no pipe
for m in $(. scripts/lib/modules.sh; vae_modules); do cp "$D/$m.api.sig" "$m/api.txt"; done
git diff --stat -- '*/api.txt'      # review: core/providers/keystore must be +-only
```
Descriptor diff (D-02; v1.0.1 jars via `curl` from `https://jitpack.io/com/github/Ygaray/voice-action-engine/voice-action-engine-<m>/v1.0.1/...` (core pom answered 200 this session), HEAD jars from the kept dry-run m2 so no extra Gradle run):
```bash
javap -p -s -cp old.jar $(classes) | sort -u > old.txt ; javap -p -s -cp new.jar $(classes) | sort -u > new.txt
comm -23 old.txt new.txt       # lines present in v1.0.1 and gone in v1.1: must be empty for public/protected members
```
Keystore is an AAR: use its `classes.jar`.

## State of the Art

| Old | Current | Impact |
|-----|---------|--------|
| v1.0.x: 3 hard-coded modules, patch-only gate 12 | 5-module manifest, gate 12 needs "new in this release" | one branch + controls |
| v1.0.1 NO-WAIVERS | minor needs a packet (gate 8 rejects NO-WAIVERS for PATCH 0) | write WAIVER-PACKET.md |
| release dir under archived Phase 11 | `.planning/releases/v1.1.0/` | already done |

## Runtime State Inventory
Not a rename/refactor phase. One related state fact: the pre-tag README/ECOSYSTEM announcement (see D-07 section) is "runtime state" for RT-09(3) revert.

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | Sync main with `git merge origin/main` (not rebase), and confirm in the "pushing main <sha>" handshake | Pitfall 1, Block 0 | rebase orphans recorded SHAs; merge adds one merge commit (gate 5 still passes after push) |
| A2 | An `internal object { const val }` also trips ApiShapeTest, so RT-01 stays "leave" | RT-01 | if it passes, a one-file object dedupe becomes viable (still needs `ApiShapeTest` + detekt green) |
| A3 | Strict C23 evidence before "tag ready" comes from a throwaway-clone local tag; real strict run after the tag | RT-09/C11 | orchestrator may want the check inside `cut`; then add a post-tag/pre-push hook in `run_cut` |
| A4 | RT-07 fingerprint obtained via `KeystoreCredentialSource(store).credential(provider)` | RT-07 | if the lookup shape differs, fall back to save/import-time computation |
| A5 | "Gate-1 UI helper" = the tester reading `key_state_*` by tag | RT-07 | guard the wrong emitter |
| A6 | Selftest all takes well over 30 min (3+ Gradle runs: apiDump, check/apiCheck/dry-run in preflight and again in cut, plus plant controls); no timing exists for it | Windows | undersized quiet window (P19 negative-controls alone took ~40 min: 17:30-18:10) |
| A7 | Each later push of main (W, then the .planning commits after the wiring record) needs the same "pushing main" handshake | Cut sequence | a push without OK violates RT-09(1) |
| A8 | Two quiet windows (before W, after W) | Cut sequence | one window cannot cover selftest + preflight + cut |
| A9 | Add `scripts/` descriptor-diff helper for D-02 (evidence file is required; the helper is optional) | Code Examples | a helper added after W would void the pass, so add before W or run inline |
| A10 | `verify-stt-confinement.sh` belongs inside `gate_hygiene` | RT-05 | alternative is a 16th gate; changes the `gates=` list the evidence greps for |
| A11 | Orchestrator OI-ruling text for P15-P17 exists outside this repo | RT-08 | cannot cite; fall back to "shipped default, no objection as of date" |
| A12 | Gate 12 new-module keyed on directory absence in the prior tag | Gate-12 design | keying on api.txt absence lets a deleted baseline pass |

## Open Questions

1. **Merge vs rebase for the D-06 push** (A1). Recommendation: merge; ask in the handshake.
2. **RT-08 citations.** Which orchestrator messages hold the P15/P16/P17 OI rulings? Ask the orchestrator for the text (and the SB 178 / CT answer for P17 OI-1) in the first plan task.
3. **C11 timing** (A3). Is the throwaway-clone simulation acceptable as "C11 green" before tag-ready?
4. **RT-07 on device.** Accept JVM-only proof, or ask for a short TESTER window? Default: JVM-only, recorded in the Gate-1 delta evidence.
5. **Waiver rows.** The exact category C list (see Waiver packet) needs Yahir's wording; draft early because D-05 says ask before the window.

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| JDK / javap | Gradle, D-02 | yes | OpenJDK 17.0.19 | none needed |
| git / gh | gates, push | yes | 2.43.0 / 2.45.0 | |
| jq, python3, curl, node | live probe, gate 14/15, config read | yes | 1.7 / 3.12.3 / 8.5.0 / 24.20.0 | |
| shellcheck | optional lint | no | | `bash -n` (all `scripts/*.sh` pass `bash -n`, probed) |
| Android SDK | `:keystore`, `:voice-adapter`, `:sample` | yes | `ANDROID_HOME=/home/yahir/Android/Sdk` (no `local.properties`, ignored by git) | |
| jitpack.io | live probe, v1.0.1 jars | reachable | v1.0.1 core pom HTTP 200 | |
| origin | push, gates 2/5 | reachable | `origin/main` = `43768ea` | |
| Gradle daemon / memory | all heavy gates | **tight** | MemAvailable ~11 GiB of 31, swap FULL (2.0Gi/2.0Gi), earlyoom `-m 15,8 -s 10,5` active, no Gradle daemon running | swap reset by Yahir (`sudo swapoff -a && sudo swapon -a`) or an orchestrator ruling like 19 RT-07 ("proceed without swap reset, stop below 5 GiB") |
| TESTER device | none planned in P20 | n/a | | |

**Host recipe (repo evidence, `19-QUIET-WINDOW.md:41-43` and the `vae-release-cut-host-oom` memory):**
`GRADLE_OPTS="-Dorg.gradle.daemon=false -Dorg.gradle.workers.max=2 -Dorg.gradle.parallel=false -Dkotlin.compiler.execution.strategy=in-process -Dorg.gradle.jvmargs=-Xmx1536m"`, one Gradle process at a time, guard before each heavy step: tree clean outside `.planning` (`git status --porcelain -- . ':!.planning' ':!graphify-out' ':!.gsd'` empty), `awk '/^MemAvailable:/{exit !($2>=5242880)}' /proc/meminfo`, and `! pgrep -f '[v]oice-action-engine/gradle/wrapper/gradle-wrapper.jar'`. Window precedent: 9000 s, stop below 5 GiB, one retry per earlyoom-killed step, never kill a running mempalace mine, never `./gradlew --stop` while another project's daemon lives. The release-cut gates inherit `GRADLE_OPTS` from the environment (the script only adds `--no-build-cache --console=plain`).

## Project Constraints (from CLAUDE.md)

- Detekt zero baseline on library modules; no new `@Suppress` beyond the justified helpers (`core/.../internal/Guarded.kt`, `undo/.../internal/Guard.kt`). RT-03 KDoc work must keep detekt green.
- Public API strictly additive once tagged; contract changes only via section 10 amendments through the control plane; **never commit section 11 here**; tag cuts are agent-owned under A12 with Yahir's push+tag authority, here sequenced by the RT-09 handshakes.
- Secrets: keys, transcripts, tool args/results never reach logs, telemetry, exceptions or `toString()` (RT-03, RT-07).
- Domain-free library; `:core` has no HTTP dependency; OkHttp compile floor 4.12 (untouched here).
- GSD workflow enforcement: edits go through a GSD workflow; `git.create_tag` stays false.
- Global: never run `/gsd-update`; never hand Yahir a `localhost` URL or a bare file path for a document (use `render-doc-for-review`); TESTER is `…-s22-ultra-2` only and not needed here.

## Validation Architecture

Framework: JUnit 4.13.2 + kotlinx-coroutines-test 1.11.0 (JVM tests), bash gate scripts with built-in selftests. Config: `config/detekt/detekt.yml`, `gradle/invariants.gradle.kts`. All Gradle commands below use the low-memory recipe above, one at a time. `nyquist_validation` is enabled in `.planning/config.json`.

### Phase Requirements -> Test Map

| Item | Behavior | Quick (no or light Gradle) | Full |
|------|----------|----------------------------|------|
| RT-01 | constants left; optional schema/parser agreement test | `./gradlew --offline -q :core:test --tests '*PlanSchemaTest' --tests '*PlanParseTest' --tests '*ApiShapeTest'` | `./gradlew --offline -q :core:check` |
| RT-03/06 | toString shape unchanged, sentinel never printed, KDoc rationale present | `./gradlew --offline -q :core:test --tests '*ActionEventTest' --tests '*HeldRunIdTest'`; `grep -n "opaque" core/src/main/kotlin/io/github/ygaray/voiceactionengine/core/commit/CommitSink.kt` | `:core:check` (detekt) |
| RT-04 | keystore +9 lines additive; undo suppressions = 1 | `grep -rn "Suppress" undo/src` (expect only `Guard.kt:1`); `git diff --stat -- '*/api.txt'` after the dump copy; `comm -23 <(git show v1.0.1:keystore/api.txt\|sort -u) <(sort -u keystore/api.txt)` empty | `scripts/release-cut.sh gate api-dump` |
| RT-02 gate 10 | fresh dump == committed for all 5 | none cheap | `scripts/release-cut.sh gate api-dump` |
| RT-02 gate 12 | new-module branch; removals still red | `scripts/release-cut.sh gate api-check v1.1.0` (fails fast in the baseline half before Gradle if anything is wrong; with the new `gate api-baseline v1.1.0` fully Gradle-free) | `scripts/release-cut.sh gate api-check v1.1.0` (includes `apiCheck`, ~heavy) |
| RT-02 selftest step 4 | green after committed dumps | `SELFTEST_ONLY="api-check-new-module-seed ..." scripts/release-cut.sh selftest negative` (prints PARTIAL, never OK) | `scripts/release-cut.sh selftest all` -> `RELEASE SELFTEST OK happy=1 negatives=<n> positives=<n>` |
| RT-05 | stt confinement in preflight | `bash scripts/verify-stt-confinement.sh` (`STT CONFINEMENT OK checks=6`), `... --selftest` | `scripts/release-cut.sh gate hygiene` + the new control |
| D-04 manifest | gates 7/15 manifest-driven | `bash scripts/verify-release-manifest.sh` (`RELEASE MANIFEST PROOF OK cases=8`), `bash scripts/verify-module-manifest.sh --selftest` | selftest all |
| RT-07 | no last-4, fingerprint = first 6 hex of sha256, no echo into evidence | `./gradlew --offline -q :sample:testDebugUnitTest --tests '*KeyVaultTest' --tests '*SampleViewModelTest' --tests '*TestKeyImporterTest' --tests '*EvidenceLineTest' --tests '*UiTagsTest'` | `./gradlew :sample:check` (lint + unit tests) |
| RT-08 | PD-04 counts fixed, C7 block present | `grep -n "PD-04" .planning/STATE.md` shows "(2 overloads)" and "(2)" | n/a |
| C9 / DOC-02 | docs fixed, byte-equal snippets | `bash scripts/verify-docs-coverage.sh` (`DOC COVERAGE OK checks=32`), `bash scripts/verify-docs-coverage.sh --selftest`, `bash scripts/agent-wiring-test.sh selftest-source` | `./gradlew :sample:testDebugUnitTest --tests '*DocSnippetsTest'`, `:voice-adapter:check`, `scripts/agent-wiring-test.sh selftest` |
| C4 wiring | isolated agent PASS on W | n/a | `scripts/agent-wiring-test.sh prepare W` + dispatch + `verify` -> `WIRING TEST: PASS checks=<n>`; `scripts/release-cut.sh gate wiring W` |
| C2 JitPack | all 5 coordinates build and resolve | `SKIP_CONSUMER=1 scripts/jitpack-live-probe.sh <ref>` | `scripts/jitpack-live-probe.sh W` then `... v1.1.0` -> `LIVE PROBE PASS` |
| D-05 waiver | packet accepted | `scripts/release-cut.sh gate waiver v1.1.0` | preflight |
| D-02 | descriptor diff evidence | the `comm -23` recipe above, empty output | evidence file committed |
| VER-07 end to end | all 15 gates | `scripts/release-cut.sh gate <name>` per cheap gate (tag-format, tags-absent v1.1.0, create-tag, clean, pushed, waiver, diff W, wiring W, hygiene, leak) | `scripts/release-cut.sh preflight v1.1.0 W` -> `PREFLIGHT OK ... gates=tag-format,...,version`, then `cut v1.1.0 W <HEAD>` -> `CUT OK tag=v1.1.0 ... pushed=refs/tags/v1.1.0` |
| C11 | strict docs gate | throwaway clone with local tag (A3) | real repo after the tag |

### Sampling Rate
- Per task commit: the Quick column for the touched item (bash-only ones first).
- Per wave: `bash scripts/verify-docs-coverage.sh`, `verify-module-manifest.sh`, `verify-stt-confinement.sh`, `verify-repo-hygiene.sh`, `verify-release-manifest.sh`, plus `:core:check` / `:sample:check` as touched.
- Phase gate: `selftest all` green (window 20-01), then `preflight` + `cut` (window 20-02).

### Wave 0 Gaps
- [ ] `.planning/releases/v1.1.0/WAIVER-PACKET.md` (selftest and gate 8 need it).
- [ ] New selftest controls for the gate-12 branch and the RT-05 hygiene plant (+ optional `api-baseline` diagnostic).
- [ ] `vae_pkg_of` helper in `release-cut.sh`.
- [ ] RT-07 tests (flip `KeyVaultTest.kt:48`; add fingerprint + no-echo tests).
- [ ] Final `<m>/api.txt` commits (five files).

## Security Domain

`security_enforcement` is on (ASVS level 1, block on high).

| ASVS Category | Applies | Control |
|---------------|---------|---------|
| V2/V3/V4 authn, session, access | no | n/a |
| V5 Input validation | yes (small) | tag/SHA regexes in gates (`SEMVER_RE`, `[0-9a-f]{10,40}` approvedCommit, L70, L770); packet grammar checked by gate 8 |
| V6 Cryptography | yes | `MessageDigest` SHA-256 only; fingerprint truncated to 24 bits, shown on screen only; never log or put in evidence |
| V7 Logging / data protection | yes | RT-03 toString redaction (existing sentinel tests), RT-07 no key chars in UI/evidence, gate 14 leak scan, `sample-evidence-filter.sh` |
| V10/V14 supply chain / config | yes | `verify-stt-confinement.sh` (RT-05): only `:voice-adapter` may reach `:stt`; JitPack tag immutability; `git.create_tag` false |

| Threat | STRIDE | Mitigation |
|--------|--------|------------|
| tag pushed from a non-gated tree | Tampering | `cut` re-runs all gates, pushes only `refs/tags/<tag>`, verifies origin lists exactly the earlier tags + the new one (L795-802) |
| key material in docs/evidence/UI | Information disclosure | RT-07, gate 14, filter, runbook line |
| 6-hex fingerprint brute force | Information disclosure | low risk for high-entropy provider keys (24 bits of a 160+ bit secret); do not extend beyond 6 |
| ledger row committed here | Repudiation/process | forbidden by RT-09; the orchestrator is the sole writer |

## Sources

### Primary (HIGH; read in this repo this session)
- `scripts/release-cut.sh` (all 1653 lines), `scripts/modules.list`, `scripts/lib/modules.sh`, `scripts/lib/published_versions.py`, `scripts/api-dump-isolated.sh`, `scripts/jitpack-dry-run.sh`, `scripts/jitpack-live-probe.sh`, `scripts/verify-repo-hygiene.sh`, `scripts/verify-module-manifest.sh`, `scripts/verify-release-manifest.sh`, head of `scripts/verify-stt-confinement.sh`, `scripts/verify-api-seed.sh`, `scripts/agent-wiring-test.sh` (header), `build.gradle.kts:44-85`, `jitpack.yml`, `gradle.properties`.
- `20-CONTEXT.md`, `STATE.md`, `ROADMAP.md` (Phase 20), `REQUIREMENTS.md` (VER-07), `gate2-carry-register.txt`, `17-SURFACE-REVIEW.md`, `19-API-REVIEW.md`, `15-REVIEW.md`/`15-REVIEW-FIX.md` (IN-02), `17-REVIEW-FIX.md`, `18-REVIEW-FIX.md`, `19-QUIET-WINDOW.md`, `.planning/releases/v1.1.0/*`, `.planning/releases/v1.0.1/*`, `.planning/milestones/v1.0-phases/11-cut-v1-0-0/11-WAIVER-PACKET.md`.
- Source: `PlanParse.kt`, `PlanSchema.kt`, `CommitSink.kt`, `ApiShapeTest.kt`, `ActionEventTest.kt`, `HeldRunIdTest.kt`, `KeyVault.kt`, `HeaderText.kt`, `UiTags.kt`.
- Commands run: `bash scripts/verify-{module-manifest,stt-confinement,repo-hygiene,docs-coverage}.sh`, `release-cut.sh gate api-check v1.1.0 | tags-absent | create-tag | clean | pushed`, `bash -n` over all scripts, `git diff --stat`, environment probes. No Gradle was run.
### Secondary / Tertiary
- None. No web research was needed (no new library).

## Metadata

**Confidence breakdown:**
- Script state and gate behavior: HIGH (read in full, several gates executed read-only).
- Gate-12 branch and RT-05 placement: MEDIUM (design, not yet compiled/run).
- RT-07 mechanics: MEDIUM (call chain verified, fingerprint source assumed).
- Window sizing: LOW (no selftest timing on record).

**Research date:** 2026-10-07. **Valid until:** the next commit that touches `scripts/release-cut.sh`, `scripts/modules.list` or any module `api.txt` (re-read before planning if main moved).
