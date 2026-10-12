# Phase 19: Sample Gate-1 & Docs - Research

**Researched:** 2026-10-06
**Domain:** Kotlin/Android `:sample` device harness (Gate-1 legs, closed evidence vocabulary, guarded host runner), bash release tooling, agent-facing docs with compiled snippets, isolated fresh-agent wiring test
**Confidence:** HIGH for repo facts (every anchor below was read this session); MEDIUM for the live-leg design (router wording is unproven by design, OI-6) and for the quiet-window time budget

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

- **D-01 [legs]:** Plan + router live only, cheapest models, new decision file; v1.0 legs not re-run (P12's seam changes are JVM-proven). _(source: human)_ _(amended by D-13: W04 smoke added)_
- **D-13 [legs/w04-smoke]:** Add a live `gpt-6-astra` smoke for the W04 fix (P12 D-05) to P19's live legs, alongside plan + router. One bounded OpenAI call on the TESTER, re-proving PROV-16 at the P19 SHA. It must return the typed `ModelUnsupported`, never `http_error`. The W04 fix classifies astra's 400 and does not make it succeed. Reuse P12 plan 12-08's evidence line shape (`VAE_VERDICT leg=responses_probe verdict=PASS reason=model_unsupported …`) instead of minting a new one. Same rules as D-08: no key, no transcript, no tool args. Key via the test-keys workflow (`~/.claude/context/workflows/test-keys.md`): `push-test-key openai --device <TESTER> --package <sample appId>`, TESTER only. Never inline or paste a key. A missing key is a loud stop, not a skip. _(source: human — Yahir ruling via orchestrator 3b, 2026-10-05, control-plane 4146165)_
- **D-02 [router-leg]:** Live engine Router leg (classifier prompt, cheap-model selection, PickContext accounting run on a device before the immutable tag); ladder needs ≥2 LLM tiers or D-ROUTER-SKIP hides the picker. _(provisional — refresh at execution; depends on Phase 16)_ _(source: ai-auto)_
- **D-03 [plan-cache]:** Defer explicitly in the carry register unless a fixture-backed leg can extend LE-7 redaction cheaply; never drop silently (SB 177 wants the number). _(provisional — refresh at execution; depends on Phase 15)_ _(source: ai-auto)_
- **D-04 [store]:** Stateful store (CannedToolExecutor has no targetIds, so binding and undo would pass vacuously). _(source: ai-auto)_
- **D-05 [offline-proof]:** offlineOnly + explicit zero-attempt counts + a tripwire provider; no airplane mode needed. Grammar leg: one EN match, one ES match, one near-miss → `Unhandled(cappedByPolicy=true)` with zero calls. _(source: ai-auto)_
- **D-06 [undo-leg]:** Offline scripted command + refusal sub-case; N counts committed only. _(provisional — refresh at execution; depends on Phase 17)_ _(source: ai-auto)_
- **D-07 [paths]:** One shared stable per-release path; fix lands in P12 (first runner use, PROV-16) and P19/P20 inherit it. _(provisional — refresh at execution; depends on Phase 12)_ _(source: ai-auto)_
- **D-08 [evidence]:** New line types in lockstep; no tier ids or slot values (Pitfall 26). _(source: ai-auto)_
- **D-09 [docs-state]:** Final state at the wiring SHA (gate 7 forbids doc edits after it; v1.0.1 did the same). _(source: ai-auto)_
- **D-10 [snippets]:** All snippets in `:sample` DocSnippetsTest + generalized coverage gate from the module manifest (else C03 fails on undo coordinates and C20 passes vacuously). _(provisional — refresh at execution; depends on Phase 17)_ _(source: ai-auto)_
- **D-11 [wiring]:** Four surfaces + keystore; voice-adapter covered by docs coverage, not wiring. Re-confirm the CLI still supports the isolation flags. _(source: ai-auto)_
- **D-12 [api-review]:** P19 artifact before the wiring SHA (a shape fix at P20 forces a doc + wiring rerun; after the tag it's frozen). _(source: ai-auto)_

Operator-reviewed (source: human), treat as locked: [legs], [legs/w04-smoke].

**Runtime Decisions (binding, from the trailing section of 19-CONTEXT.md):**

- **RT-01 [ref-regex-doc]:** Accept the ASCII-only `\s` in the plan reference regex (a missed near-reference stays literal, the safe direction). Document this in API.md (the PlanThenExecute reference-syntax section) at the wiring SHA.
- **RT-02 [p17-jitpack-rerun] + pre-grant:** In the P19 gate run, re-run `scripts/jitpack-dry-run.sh` and `scripts/jitpack-live-probe.sh` (the `:undoalone` probe) on the final tree (P17 IN-05/IN-06/WR-08 edited them after the 17-10 window; only `bash -n` and manifest gates checked them). Quiet window ("quiet window 19-<plan>" handshake) is PRE-GRANTED on the same terms as 17-10: master messages the orchestrator first, orchestrator takes the VAE build lock and confirms, only then the run starts; master sends "quiet done" after. MemAvailable >= 5 GiB.
- **RT-03 [p18-carries]:** Before the wiring SHA: (a) add the clean-cache `:adapteralone` consumer probe (resolves voice-adapter with core and no `:stt`); (b) run `scripts/verify-stt-confinement.sh` with `--selftest` by hand; (c) re-run `verify-negative-controls.sh` and `:voice-adapter:check` on the final SHA. (a) and (c) are heavy and go in the same quiet window as RT-02. Also drain the P18 Gate-2 fragment (`uat-pending/18-voice-adapter.md`) at milestone Gate-2.
- **[router-leg] refreshed:** Live engine Router leg on the TESTER before the immutable tag (classifier prompt, cheap-model selection, PickContext accounting). Per P16 OI-1 the Router SKIPS with no call and selection=null when exactly one model tier is eligible, so the leg ladder needs >=2 LLM tiers. Closes P16 OI-6. Needs a TESTER window AND a relayed live-spend GO with a request/USD ceiling (both non-autonomous).
- **[plan-cache] refreshed:** P15's live probe (15-07) measured binding only. Keep the `submit_plan` cache-prefix measurement explicitly deferred in the carry register unless a fixture-backed leg can extend LE-7 redaction cheaply; never drop it silently.
- **[undo-leg] refreshed:** Offline scripted command plus a refusal sub-case. N counts APPLIED actions (`ActionKind.COMMITTED` plus `IS_ERROR` with `applied=true`; un-applied `IS_ERROR` excluded; pending held shown, not counted), not "committed only". Commit only the N counts as evidence. Include one PlanThenExecute partial (committed steps + `remainingStepIds`) to prove run-level undo covers it.
- **[paths] refreshed:** P12 did NOT build one shared per-release path. `scripts/run-sample-gate1.sh` takes `VAE_GATE1_PHASE_DIR` but HARD-CODES `DECISION_FILE=$PHASE_DIR/12-LIVE-LEG-DECISION.md`, which now reads `decision: consumed`. P19 must make the decision file configurable (e.g. `VAE_GATE1_DECISION_FILE`, or derive `<NN>-LIVE-LEG-DECISION.md` from the phase dir), point the runner at P19's own decision file and evidence dir, keep it an env var override (never an edit per run). P20 inherits it.
- **[snippets] refreshed:** All doc snippets compile in `:sample` DocSnippetsTest, plus a coverage gate generalized from `scripts/modules.list`, which NOW lists FIVE modules: core, providers, keystore, undo, voice-adapter.
- **RT-04 [wr-04-overloads]:** Before the docs freeze, DROP the context-only overloads `commandInputOf(transcript, label, context)` and `FinalSegment.toCommandInput(context)`. Keep the no-arg and full-arg forms (safe: nothing released). Add one test or api.txt-shape check proving `commandInputOf("yes","en","run-42")` no longer resolves to a context-only call. Update API.md. Also fix the `ActionEvent.toString` KDoc rationale per P18 WR-01: ids may be shown.

**Master notes (binding):** every one of D-13, RT-01, RT-02 (+pre-grant), RT-03, RT-04 maps to a plan task. Gating checkpoints (non-autonomous, master relays): (1) TESTER window(s) on R5CT10XNKQN; (2) live-spend GO with request/USD ceilings covering plan leg, router leg and D-13 W04 smoke; (3) quiet host window for heavy jitpack/negative-controls runs (MemAvailable >= 5 GiB). Group device legs into as few TESTER windows as possible and heavy host gates into one quiet window. D-09/gate 7: docs reach final state at the wiring SHA and are not edited after it; the API review (D-12) precedes the wiring SHA. One Gradle-running plan per wave (earlyoom); executors run serially on a phase branch. Keep every test before the security gate.

### Claude's Discretion

Areas marked `ai-auto` took research's recommendation without operator review; the planner may refine mechanics within the stated decision but must not reverse it without a new discuss pass.

### Deferred Ideas (OUT OF SCOPE)

- `submit_plan` cache-prefix measurement — defer explicitly in the carry register unless a fixture-backed leg is cheap (see [plan-cache]).
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| VER-06 | a `:sample` Gate-1 on the TESTER exercises grammar (offline, zero calls), plan, the router and undo-all end to end. | Sections "Gate-1 leg design", "Evidence vocabulary", "Runner changes", "TESTER window plan". D-13 rides the existing `responses_probe` leg. |
| DOC-02 | README, API.md, INTEGRATION.md and ECOSYSTEM.md cover every new tier, seam and module well enough that an agent can wire them from the docs alone (isolated wiring test PASS on the final SHA). | Sections "Docs gap inventory", "Coverage gate generalization", "Wiring test redesign", "Sequencing". |
</phase_requirements>

## Summary

Phases 12-18 already put most of the new surface into API.md (grammar, plan, router/picker, `:undo`, `:voice-adapter` rows all exist) and INTEGRATION.md has "Choosing where the model walk starts" (router/picker), section 11 (undo) and section 12 (adapter). What is MISSING for DOC-02 is: no INTEGRATION section for the grammar tier or the plan tier, no compiled snippet for any of grammar/plan/router, no README mention of grammar/plan/router/undo (README has no `voice-action-engine-undo` coordinate and still pins `v1.0.1`), INTEGRATION step 2's coordinate block lacks the undo/voice-adapter lines, RT-01 and RT-04 doc edits, and ECOSYSTEM status text ("v1.1 is planned"). The coverage gate `scripts/verify-docs-coverage.sh` is only half manifest-driven: C03 reads `modules.list`, but C01 hard-codes `core providers keystore`, `public_types()` scans only `core providers keystore` sources (C20 is therefore vacuous for `:undo` and `:voice-adapter`), and `REQUIRED_REGIONS` is a fixed 11-name list.

For VER-06 the `:sample` app has NO grammar/plan/router/undo leg yet. It already has the P17 glue (`UndoCommitSink`, `ItemStore`, `CreateItem/RenameItem/DeleteItem`, `ItemAdapter`, an `undo-bridge` doc region) but nothing in `LegId`/`LegCatalog`/`LegRunner`/`AppGraph` uses it. The v1.0 runner is not reusable as-is: `scripts/run-sample-gate1.sh` hard-codes the P12 decision file and `scripts/agent-wiring-test.sh` points at the archived Phase 10 directory (so its `selftest` is broken today). The wiring test's `prepare` also refuses an unpushed SHA, while the locked push decision (P20 D-06) keeps `main` unpushed through P19: P19 therefore needs a local-Maven wiring mode or the wiring pass cannot happen in P19.

**Primary recommendation:** Execute in this order: (1) host-only tooling/path plan (decision-file env, wiring assets to a stable `.planning/releases/v1.1.0/` dir, coverage-gate generalization) -> (2) RT-04 voice-adapter overload drop -> (3) sample legs + closed-vocabulary evidence lines in lockstep -> (4) ONE TESTER window carrying every device leg plus the D-13 smoke under ONE relayed spend GO -> (5) D-12 consolidated API review -> (6) docs to final state with compiled regions -> (7) ONE quiet window for RT-02/RT-03 + wiring selftest + the isolated agent run. Device legs go before the API/docs freeze so a router-wording or shape finding can still land before the wiring SHA.

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Grammar/plan/router/undo behavior | `:core` / `:undo` libraries (already shipped P14-P17) | — | P19 adds no engine behavior; it proves it. Only RT-04 touches a library (`:voice-adapter`). |
| Gate-1 legs, stateful store, tripwire provider, evidence lines | `:sample` app (debug harness, never published) | host scripts (filter, runner) | Evidence is emitted on device through `LogcatEvidenceSink`; the host filter only keeps lines of the closed grammar. |
| Spend ceiling and key custody | `:sample` `RequestBudget` (fail closed) + host runner decision file + `push-test-key` | orchestrator relay | The relayed GO is policy; the app budget and the runner `decision: approved` check are the mechanical enforcement. |
| Device targeting and TESTER guard | host runner `scripts/run-sample-gate1.sh` (+ offline guard verifier) | `~/.claude/context/devices/common.md` | Single sanctioned path to the TESTER; every adb call carries `-s`. |
| Doc truth (snippets compile, coverage) | `:sample` `DocSnippetsTest` + `scripts/verify-docs-coverage.sh` | isolated wiring test | Docs are byte-equal copies of compiled regions; the wiring test is the end-to-end proof. |
| Heavy gates (negative controls, dry run, `:adapteralone`) | host quiet window under orchestrator build lock | — | Memory-bound (earlyoom); never autonomous. |

## Standard Stack

No new external dependency is needed.

### Core (all already pinned in the repo)
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Kotlin / AGP / Gradle | 2.3.20 / 9.2.1 / 9.4.1 | build | project stack (`.claude/CLAUDE.md`) |
| JUnit | 4.13.2 | all tests | project stack |
| kotlinx-coroutines-test | 1.11.0 | `runTest` in `:sample` tests | `sample/build.gradle.kts` `testImplementation(libs.coroutines.test)` [VERIFIED: sample/build.gradle.kts] |
| `:core` testFixtures | in-repo | `FakeAiProvider`, `ScriptedGate`, `NoNetworkGuard`, `RecordingCommitSink`, `ScriptedPicker` for `:sample` JVM tests | `testImplementation(testFixtures(project(":core")))` in `sample/build.gradle.kts` |
| `:stt` (voice-engine-android) | v0.7.0 | only if an adapter `FinalSegment` snippet is compiled | `stt-engine = "v0.7.0"` and `stt-engine = { group = "com.github.Ygaray.voice-engine-android", ... }` [VERIFIED: gradle/libs.versions.toml:13,29]; JitPack exclusiveContent already in `settings.gradle.kts` |

### Supporting
| Item | Purpose | When to Use |
|------|---------|-------------|
| `with-test-keys`, `push-test-key`, `test-keys-usage` (in `~/.local/bin`) | spend-capped keys | live legs; host and device respectively [VERIFIED: `which` returned the three paths this session] |
| `claude -p` (v2.1.292) | the isolated wiring agent | flags `-p/--print`, `--model`, `--no-session-persistence`, `--permission-mode` (choices include `bypassPermissions`) are listed in `claude --help` this session [VERIFIED: claude --help]. `CLAUDE_CONFIG_DIR` throwaway-dir isolation is an env var not shown in `--help`: [ASSUMED] unchanged since the v1.0.1 run (2026-10-03); the isolation audit (no CLAUDE.md in ancestors, CONSULTED.md only workspace paths) re-proves it each run. A new `--bare` flag (skips hooks, auto-memory, CLAUDE.md auto-discovery, keychain reads) exists but its auth path is untested: do not adopt it unproven. |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| `:voice-adapter` as `implementation` in `:sample` | `testImplementation(project(":voice-adapter"))` + `testImplementation(libs.stt.engine)` | RECOMMENDED: the adapter is doc-snippet-only, so keep the APK unchanged and the blast radius to the unit-test classpath (`:voice-adapter` itself already resolves `:stt` at `testImplementation`). |
| Fixture-backed plan leg (D-03) | explicit carry-register deferral | RECOMMENDED: defer (see "Plan-cache decision"). |

**Installation:** none (no new packages).
**Version verification:** not applicable; the only external coordinate touched is `stt-engine` v0.7.0, already in the catalog and exercised by `:voice-adapter:test`.

## Package Legitimacy Audit

This phase installs no new external package (the `:stt` v0.7.0 coordinate is the owner's own JitPack artifact, already in `gradle/libs.versions.toml` and resolved by P18). Package Legitimacy Gate: nothing to check.

| Package | Registry | Age | Downloads | Source Repo | Verdict | Disposition |
|---------|----------|-----|-----------|-------------|---------|-------------|
| (none new) | — | — | — | — | — | — |

**Packages removed due to [SLOP] verdict:** none
**Packages flagged as suspicious [SUS]:** none

## Architecture Patterns

### System Architecture Diagram

```
 orchestrator/master relays: [TESTER window] [spend GO + ceilings] [quiet window]
        |                          |                                  |
        v                          v                                  v
 host runner (run-sample-gate1.sh, VAE_GATE1_DECISION_FILE -> 19-LIVE-LEG-DECISION.md)
   preflight -> build-install -> push-keys (decision: approved) -> [UI drive on R5CT10XNKQN]
        |                                                             |
        |                              :sample app (debug)            |
        |   tap run_<leg> --> LegRunner --> SampleEngine pipeline ----+--> providers (live: plan, router, astra probe)
        |                        |  grammar_offline: offlineOnly + TripwireProvider (0 calls)
        |                        |  undo_all: DemoProvider script -> UndoCommitSink -> UndoJournal(ItemAdapter)
        |                        v
        |             EvidenceLine (VAE_TRACE / VAE_UNDO / VAE_OUTCOME / VAE_VERDICT ...) -> logcat tag VaeSample
        v                                                             |
   capture-save <leg>  <-- adb logcat -d -s VaeSample:I --------------+
        |  sample-evidence-filter.sh (ALLOW_RE lockstep, key-shape scan, drops everything else)
        v
   <phase>/evidence/gate1-<leg>.txt  (committed)  --> carry register --> P20 waiver packet

 docs: DocSnippetsTest regions --byte-equal--> README/INTEGRATION/API  --> verify-docs-coverage.sh (manifest-driven)
        --> wiring SHA --> [quiet window] jitpack-dry-run (KEEP_WORK m2) --> wiring prepare-local --> isolated `claude -p` --> verify
```

### Recommended Project Structure (additions only)
```
sample/src/main/kotlin/.../sample/
├── legs/            # LegCatalog/LegRunner gain GRAMMAR, PLAN, ROUTER, UNDO kinds; TripwireProvider, scripted plan provider
├── undo/            # ItemStore/ItemMutations/UndoCommitSink already exist (P17); add tool specs + executor for the plan/undo legs
├── evidence/        # EvidenceLine: new types TRACE, UNDO (+ LegId entries); ALLOW_PATTERN updated
└── ui/              # UiTags: run_/status_ for new legs + undo_all button/label
sample/src/test/...  # GrammarLegTest, PlanLegTest, RouterLegTest, UndoLegTest, docs regions
scripts/             # run-sample-gate1.sh (decision file env, LEGS, dirty-path list), sample-evidence-filter.sh (ALLOW_RE),
                     # verify-sample-device-guard.sh (scenarios + counts), verify-docs-coverage.sh, agent-wiring-test.sh,
                     # review-api-surface.sh / api-dump-isolated.sh usage for D-12
.planning/releases/v1.1.0/wiring-test/   # AGENT-PROMPT.md + reference/ (stable, non-phase path; survives milestone archive)
.planning/phases/19-sample-gate-1-docs/  # 19-LIVE-LEG-DECISION.md, evidence/, 19-API-REVIEW.md, 19-GATE1-RUNBOOK.md, 19-QUIET-WINDOW.md
.planning/uat-pending/19-sample-gate-1-docs.md   # Gate-2 fragment
```

### Pattern 1: Closed-vocabulary evidence line, in lockstep
**What:** every new `VAE_<TYPE>` line is added in FIVE places together: `ALLOW_PATTERN` in `EvidenceLine.kt`, `ALLOW_RE` in `scripts/sample-evidence-filter.sh`, `sample/src/test/resources/evidence-lines.golden.txt`, the guard verifier's hard-coded counts, and `LEGS` in the runner (vs `LegId`).
**Anchors (quoted):**
- `internal const val ALLOW_PATTERN =` then `"^VAE_(ENV|FIXTURE|KEY|TURN|ATTEMPT|CACHE|SMOKE|OUTCOME|VERDICT|BUDGET|AUTORUN)"` [VERIFIED: EvidenceLine.kt:56-58]
- `ALLOW_RE='^VAE_(ENV|FIXTURE|KEY|TURN|ATTEMPT|CACHE|SMOKE|OUTCOME|VERDICT|BUDGET|AUTORUN)( [a-z0-9_]+=[][A-Za-z0-9_.:/,-]{0,96})+$'` [VERIFIED: scripts/sample-evidence-filter.sh:16]
- `LEGS="ver02 smoke_anthropic smoke_openai smoke_openrouter multi_openai multi_openrouter responses_probe demo_clarify demo_partial"` [VERIFIED: scripts/run-sample-gate1.sh:55]; parity is proven by guard scenario `leg_list_parity` (`runner_legs` vs the `LegId` wires) [VERIFIED: scripts/verify-sample-device-guard.sh:373-378]
- Guard hard-codes `kept=13 dropped=1` and `expected 13 evidence lines` for `capture_save_happy` (12 golden lines + 1 verdict) [VERIFIED: scripts/verify-sample-device-guard.sh:359,365]: adding N golden lines moves both numbers to 13+N.
- `LegId.fixtureBacked` is `this == LegId.VER02` [VERIFIED: EvidenceLine.kt:35] and the filter's LE-7 backstop greps `leg=ver02` only [VERIFIED: scripts/sample-evidence-filter.sh:25].

**Recommended new types (codes and counts only; no tier ids, no slot values, no plan args):**
- `VAE_TRACE leg=<leg> case=<n> kind=<completed|failed|unhandled> capped=<true|false> tiers_run=<n> provider_turns=<n> sel=<none|picked|router_fallback|...> eligible=<n> picked_index=<n|none> bypassed=<n> sel_turns=<n> codes=[<trace codes>]` - tier IDENTITY is expressed as an index/count, never an id (Pitfall 26). `TraceCode` values and `StartTierSelection.outcome` are stable `[a-z_]` tokens [VERIFIED: StartTierSelection.kt KDoc: "`picked` or `router_fallback`, or `cancelled`, `timeout` or `failed`"].
- `VAE_UNDO leg=<leg> case=<n> phase=<counted|undone|refused|partial> n=<count> pending=<n> withheld=<bool> result=<UndoResult.code> restored=<n> blockers=<n> reason=<UndoReason.value|none> store_ok=<bool>` - `UndoResult.code` is `complete|refused|partial|already_undone` and `UndoReason.value` e.g. `changed_since` [VERIFIED: undo/UndoResult.kt `get() = "complete"` etc.; undo/UndoReason.kt:18 `UndoReason("changed_since")`]. Extend the plan leg's `VAE_OUTCOME` consumers with `remaining` via `VAE_TRACE`/`VAE_UNDO` rather than editing `OUTCOME` (extending `OUTCOME` rewrites every golden line, per the DECISION-MAP option).

### Pattern 2: Offline proof of "zero provider calls" (D-05)
Run the grammar ladder `[grammar, singleShot]` under `TierPolicy { offlineOnly = true }` in an engine whose only provider is a `TripwireProvider` (counts `complete` calls, returns a typed failure) - the same separate-engine pattern `LegRunner` already uses for demos (`demoEngine = SampleEngine(listOf(demo), CredentialSource { CredentialLookup.Missing() }, demoSink, null)`) [VERIFIED: LegRunner.kt, "The demos run on their own engine"]. Evidence = `tripwire_calls=0` plus `provider_turns=0` from `EvidenceListener.turns` (it records `PipelineEvent.ProviderCall`) plus the tap's attempt count 0. The three cases: EN match, ES match (both `Completed`, `capped=false`) and one near-miss (`Unhandled`, `capped=true`, code `tier_skipped_policy`). `TierPolicy.offlineOnly` exists [VERIFIED: TierPolicy.kt:43]; `Unhandled.cappedByPolicy` covers offline-only [VERIFIED: API.md "Pipeline and outcomes"].

### Pattern 3: Plan leg uses a stateful store, with the key name stated in the tool description
`PlanThenExecuteStrategy` binds `$<stepId>.<key>` to the earlier step's committed `targetIds[key]`; "the key names are yours: say in each write tool's description which keys it returns" [VERIFIED: API.md "Strategies and tools"]. The sample's `CreateItem` already returns `StepResult("created", false, null, mapOf("id" to item.id))` [VERIFIED: sample/.../undo/ItemMutations.kt:29] and accepts `parentId`. So: step 1 `create_item(title)` -> id; step 2 `create_item(title, parent_id = "$s1.id")`. PASS (booleans/counts only) = `Completed`, not partial, `committed=2`, and the store shows the second item's `parentId` equal to the first item's id. `CannedToolExecutor` cannot do this (no targetIds) [CITED: 19-CONTEXT D-04].

### Pattern 4: Router leg needs >= 2 LLM tiers and a mapped router id
`TierSelector.Router` is built with `skipsSingleTier = true` [VERIFIED: core/.../pipeline/TierSelector.kt:112], so a one-model-tier ladder makes no call and records no selection. Ladder: `[grammar(NO_PROVIDER), single(ANY), plan(ANY)]` (both model tiers are one-call tiers, so spend stays ~2 calls). `start_tier_router` must map to the cheap model through the selection source; the sample's `SampleEngine.pipeline(selection=...)` returns ONE `ProviderSelection` for every asked id [VERIFIED: SampleEngine.kt `providerSelection = ProviderSelectionSource { selection }`], so the router id automatically gets the leg's model (Haiku). Pick a transcript that misses the grammar head and needs several dependent writes so the right start is the plan tier (`picked_index=1`, `bypassed=1`); `tierDescriptions` one line per tier, synthetic words only (they are sent to the provider). Verdict: PASS iff `sel=picked` and the first attempted model tier is the picked one (and `sel_turns=1`, router tokens counted in `trace.usage`); `sel=router_fallback` is a loud FAIL `router_fallback` (it means the wording or mapping failed: OI-6 is exactly what this leg tests; the engine falls back to Linear so the risk is cost, not correctness). `picked_index=0` is a valid but weaker proof: record it as INCONCLUSIVE `picked_first`, not PASS, unless the transcript was designed to pick the first. Router wording is engine-owned and may be tuned without an API change (INTEGRATION.md Notes), so a wording fix found here is allowed before the freeze.

### Pattern 5: Undo-all leg (offline) with three sub-cases
Offline = `DemoProvider` extended (provider id `demo`, scripted `submit_plan` / tool calls) so no key and no budget are involved (`reservation = 0`, `needsKey = false`, like `demo()` in `LegCatalog`). Wire exactly like the reference bridge: `commandSink = compositeSink(UndoCommitSink(journal), sink)` with the journal FIRST; journal built `UndoJournal { adapter(ItemAdapter(store)) }`; mutations are `CreateItem/RenameItem` with `journal.newTicket()` as context. Cases: (a) multi-action command (2 creates + 1 rename) -> `VAE_UNDO phase=counted n=3`, then `undoAll` -> `result=complete restored=3 store_ok=true` (store equals the pre-command snapshot); (b) refusal: run a command, then `store.edit(...)` (the later edit), `undoAll` -> `result=refused reason=changed_since blockers=1 store_ok=true` (store untouched); (c) PlanThenExecute partial: 3 steps, a gate that HOLDS the 3rd (a small sample-owned `PreApplyGate` that returns `GateDecision.Hold` for the Nth proposal; `SampleEngine` allows overriding `gate` through its `configure` lambda, which runs last [VERIFIED: SampleEngine.kt "[configure] runs last"]) -> `Completed(partial=true, remainingStepIds=1)`, `n=2 pending=1`, undo restores 2. N semantics: `UndoGroup.count` = applied actions not yet undone, incl. an errored apply and a nothing-written action; held-unconfirmed are shown apart via the bridge's `pendingHeld` and never counted [VERIFIED: INTEGRATION.md section 11 "Grouping and Undo all (N)"; UndoGroup.kt:36-38].

### Anti-Patterns to Avoid
- **Evidence from tier ids / slot values / plan args:** a tier id, transcript, slot text or tool argument must never be a field value; use indexes, counts, booleans, codes (D-08, Pitfall 26).
- **Reusing `CannedToolExecutor` for plan/undo legs:** binding/undo would pass vacuously (D-04).
- **Running the grammar leg with the real providers and judging by "no HTTP happened" alone:** add the tripwire plus explicit zero counts (D-05).
- **Editing a script per run:** the decision file and phase dir are env overrides, not edits (it is exactly what broke at the v1.0 archive).
- **Doc edit after the wiring pass:** any README/INTEGRATION/API/ECOSYSTEM edit voids the pass and forces a rerun (`10-WIRING-TEST.md` "Rerun rule", gate 7).

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| TESTER targeting, identity proof, lock, logcat capture | a new device script | `scripts/run-sample-gate1.sh` (extend with env override) | guard-verified; one lock shared with the keystore runner |
| Evidence leak scanning | ad-hoc grep | `scripts/sample-evidence-filter.sh` (allow-list + key-shape scan) | closed grammar, rejects the whole capture on a leak |
| Key transport | any inline/`.env`/adb-argv key | `push-test-key <provider> --device R5CT10XNKQN --package <appId>` (device), `with-test-keys` (host) | keys never in transcript/commit/log ([CITED: ~/.claude/context/workflows/test-keys.md]) |
| Offline-fake scaffolding in JVM tests | new fakes | `:core` testFixtures (`FakeAiProvider`, `ScriptedGate`, `NoNetworkGuard`) and `sample/.../undo/UndoTestRig.kt` | already used by 17-08 tests |
| Undo grouping bridge | a new CommitSink | `sample/.../undo/UndoCommitSink.kt` (the doc region `undo-bridge` is a byte copy of it) | reference wiring, covered by `UndoBridgeParityTest` |
| Module list in scripts | literals | `scripts/lib/modules.sh` (`vae_modules`, `vae_module_field`) | Pitfall 9: copy-pasted lists skip the next module silently |
| API dump | manual Metalava runs in the real tree | `scripts/api-dump-isolated.sh --out <dir>` (never rewrites committed `api.txt`) | isolated copy + real-tree-unchanged guard |

**Key insight:** every piece of P19 infrastructure exists; the work is retargeting stale paths, widening closed vocabularies in lockstep, and making hard-coded three-module lists manifest-driven.

## Common Pitfalls

### Pitfall 1: Hard-coded decision file
**What goes wrong:** `DECISION_FILE="$PHASE_DIR/12-LIVE-LEG-DECISION.md"` [VERIFIED: scripts/run-sample-gate1.sh:42] reads `decision: consumed` (P12) so `push-keys` refuses (`live_legs_not_approved`), and `capture-save` writes into the P12 evidence dir.
**How to avoid:** `DECISION_FILE="${VAE_GATE1_DECISION_FILE:-<derived>}"` where `<derived>` is `$PHASE_DIR/<leading digits of the phase dir name>-LIVE-LEG-DECISION.md` (see the Code Examples snippet; the env override wins); default `VAE_GATE1_PHASE_DIR` to the P19 directory; keep `PHASE_REL` and the `12-LIVE-LEG-DECISION.md` literal in `verify-sample-device-guard.sh` (lines 19 and 127) in step, and ADD scenarios: override wins; derived name; a `consumed` file refuses `push-keys` (the `deferred` case exists, `consumed` does not). P20 inherits by exporting the same two env vars. Also `do_build_install`'s dirty check lists `sample core providers keystore scripts gradle build.gradle.kts settings.gradle.kts` [VERIFIED: scripts/run-sample-gate1.sh:275] and misses `undo`/`voice-adapter`: derive it from the manifest.
**Warning signs:** `SAMPLE_GATE1: ERROR sub=push-keys reason=live_legs_not_approved`.

### Pitfall 2: Router leg proves nothing on a one-model-tier ladder
**What goes wrong:** exactly one eligible model tier -> no call, `selection=null`, `trace.selection` null (P16 OI-1, accepted).
**How to avoid:** the leg's verdict must FAIL (not pass) when `sel=none` for this leg, and the ladder needs >= 2 model tiers; keep the grammar head so the pre-pass path is also exercised.

### Pitfall 3: D-13 wording vs the reachable result
**What goes wrong:** older wording ("must succeed") cannot happen: on Chat Completions `gpt-6-astra` takes no function tools, so the only reachable live result is typed `ModelUnsupported` (HTTP 400). CONTEXT D-13 already states this correctly ("It must return the typed ModelUnsupported ... classifies astra's 400 and does not make it succeed").
**How to avoid:** no new leg or line: re-run the EXISTING `responses_probe` leg (judged PASS only on `model_unsupported` after a real HTTP answer) and capture `VAE_VERDICT leg=responses_probe verdict=PASS reason=model_unsupported http=400 trigger=ui` [VERIFIED: P12 evidence/gate1-responses_probe.txt and 12-08-SUMMARY]. It spends from the OPTIONAL pool (1 request per install; `PROBE_RESERVATION = 1` [VERIFIED: LegCatalog.kt:27]). A missing OpenAI key => `push-keys` ERROR => loud stop, never skip. Host "Probe C" was a P12-only pre-check; optional here (it would cost one more request against the ceiling).

### Pitfall 4: Per-install budget state
**What goes wrong:** `cleanup` uninstalls the app and wipes the budget file and keys (the in-app counter lives in app storage), and v1.0's runbook says never uninstall between legs. Running legs across two installs resets the counter and defeats the in-app ceiling.
**How to avoid:** ONE install, ONE `build-install`, all device legs (offline and live), then `verify-keys-gone` and `cleanup` at the end of the window. Do not use the autorun intent for a first run (`autorun_before_ui` refuses it by design).

### Pitfall 5: Reservation arithmetic can starve a leg
**What goes wrong:** `canStart(reservation)` requires `core + reservation <= 33 && core + optional + reservation <= 34` [VERIFIED: RequestBudget.kt:15,18 and `fits`], and `PER_CALL_WORST_CASE = 3` [VERIFIED: RequestBudget.kt:24]. If the planner lowers the ceiling to match the relayed GO, reservations must fit sequentially at worst case or the second leg is `REFUSED budget`.
**How to avoid:** recommended reservations: plan leg 6 (one plan call + one replan, x3 worst case), router leg 9 (router call + plan call + one replan, x3). Worst-case sum 15 <= core ceiling, typical spend ~4-5 requests. Recommended relayed ceiling to request: **<= 16 requests total (15 core + 1 optional for the astra probe), <= USD 0.05**, expected ~5-6 requests, ~USD 0.01. [ASSUMED: the USD figure follows `CostEstimate` prices: haiku 1.00 in / 5.00 out per M tokens, a plan or router call is a few thousand tokens; the master/orchestrator sets the real ceiling]. Either keep the 33/34 constants (the app cap is then looser than the relayed GO: rely on the decision file and leg-only driving) or lower them for P19; lowering touches `RequestBudgetTest`/`SampleViewModelTest` text (`requests 0/33`). Planner choice (Open Question 3).

### Pitfall 6: The docs coverage gate is partly vacuous for the new modules
**What goes wrong (verified):** C01 loops `for a in core providers keystore` [VERIFIED: scripts/verify-docs-coverage.sh:127]; `public_types()` scans only `core/src/main/kotlin providers/src/main/kotlin keystore/src/main/kotlin` [VERIFIED: :110] so C20 never demands `:undo`'s 12 types or the adapter functions (they happen to be in API.md today, but nothing enforces it); `REQUIRED_REGIONS` is a fixed list of 11 [VERIFIED: :41]; C03 already reads the manifest (`MODULE_ALT`). C03 does NOT fail on undo today (README names no undo coordinate line at all, so C03 passes vacuously there).
**How to avoid:** generalize C01 to `for a in $(vae_modules)`; make `public_types` iterate `vae_modules` (and add a function-name pass for modules whose public surface is top-level functions, i.e. `:voice-adapter`: `^public fun (Recv\.)?name(`, which also covers `commandPipeline`, `compositeSink`); add the new regions to `REQUIRED_REGIONS`; add new checks (C26+) greping each new tier/seam in INTEGRATION and API (see "Coverage gate generalization"); add a `--selftest`/negative control proving a planted missing `:undo` type or a missing undo coordinate in README turns C20/C01 red (the script has no selftest today).

### Pitfall 7: The wiring test is currently broken and cannot run unpushed
**What goes wrong:** `PHASE_DIR="$ROOT/.planning/phases/10-sample-harness-gate-1-docs"`, `PROMPT=.../wiring-test/AGENT-PROMPT.md`, `REFERENCE=.../wiring-test/reference` [VERIFIED: scripts/agent-wiring-test.sh:19-21] point at the archived v1.0 phase (now `.planning/milestones/v1.0-phases/10-...`), so `selftest` and `prepare` fail. `prepare` also requires `git merge-base --is-ancestor $sha origin/main` and a passing `jitpack-live-probe.sh` [VERIFIED: agent-wiring-test.sh `prepare`], but `main` is 457 commits ahead of `origin/main` [VERIFIED: `git rev-list --left-right --count origin/main...HEAD` = `0 457`] and the locked decision (20-CONTEXT D-06) pushes only at the start of P20.
**How to avoid:** (a) move the prompt + reference solution to `.planning/releases/v1.1.0/wiring-test/` (stable, non-phase) behind an env override (`VAE_WIRING_ASSET_DIR`); (b) add a `prepare-local <m2dir> <version>` (or `REPO_URL` + `--local`) mode that builds the workspace against the `file://` Maven repo the dry run leaves with `KEEP_WORK=1` (`make_workspace` already takes a `repo_url`, and `selftest` already does exactly this with `file://$m2`); (c) P20 re-runs the pushed-SHA path (`prepare`) as ROADMAP SC3 says "re-run on the final SHA in Phase 20".

### Pitfall 8: `checks=9` is hard-coded
The judge prints `WIRING TEST: PASS checks=9` and `selftest` greps `'^WIRING TEST: PASS checks=9$'` [VERIFIED: agent-wiring-test.sh verify/selftest]. Adding checks changes both. W3 already accepts every manifest module name (`MODULE_ALT`) but only REQUIRES core|providers in `:jvmconsumer` and keystore in `:app`.

### Pitfall 9: RT-04 touches tests and docs in four places
Drop `commandInputOf(String,String?,Any?)` (`LanguageLabels.kt:41-42`) and `FinalSegment.toCommandInput(Any?)` (`FinalSegmentMapping.kt:24`) [VERIFIED: voice-adapter sources read this session]. Update: `AdapterApiShapeTest` (it pins THREE overloads of each: `listOf("toCommandInput" x3)`, `commandInputOf` x3 [VERIFIED: AdapterApiShapeTest.kt]) to two each; `FinalSegmentMappingTest` line 61 (`toCommandInput(context)`) and `LanguageLabelsTest` line 67 (`commandInputOf("hello","en",context)`) [VERIFIED: grep output]; KDoc of the surviving overloads (the "A String passed here is a context object" warnings go away with the overloads); API.md lines 214-215 ("Three overloads") and INTEGRATION.md section 12 ("with two more overloads, `toCommandInput(context)` and ..."). The "no longer resolves" proof is a reflection check: assert no public static method of the label facade has parameter types `(String, String, Object)` and none of the segment facade has `(FinalSegment, Object)`; a compile-failure cannot be asserted in a unit test. The seed `voice-adapter/api.txt` is header-only (`// Signature format: 4.0`, 25 bytes) so Metalava compat is vacuous until the cut; the shape test is the real guard. **WR-01 KDoc is already aligned:** `ActionEvent.toString` KDoc now reads "Run ids are treated as opaque identifiers, so keep user text out of the ids your run-id seam hands out: a run id and a parent run id are printed verbatim. The held run id's value is not printed" [VERIFIED: core/.../commit/CommitSink.kt:37-45, commit 149e906]. Confirm it satisfies the orchestrator wording ("ids may be shown") and make a one-line edit only if the held-run-id sentence still implies a privacy rationale; do not change behavior or `HeldRunIdTest`/`ActionEventTest`.

### Pitfall 10: Heavy gates are an hours-long window, not the 1 h the earlier phases asked for
17-10 and 18-08 each used `timebox_s: 3600`; the negative-control suite alone took ~58 min and the 18-08 window overran to ~65 min [VERIFIED: 18-QUIET-WINDOW.md "Window observations"]. P19's window carries negative controls + `verify-api-dump.sh` + dry run (now with `:adapteralone`) + `:voice-adapter:check` + wiring selftest (a second dry run) + the isolated agent run (a Gradle build from an empty cache). Request **>= 2.5 h**, one Gradle process at a time, `MemAvailable` check before each step (stop at < 5 GiB), swap is full on this host (observed `Swap 1/1 GiB used` at research time, MemAvailable ~10 GiB) so a swap reset by Yahir is part of the ask.

### Pitfall 11: `jitpack-live-probe.sh` cannot run before the push
It polls `https://jitpack.io/api/builds/.../<ref>` and needs a built ref [VERIFIED: scripts/jitpack-live-probe.sh]. With `main` unpushed, the WR-08/IN-05 edits can only be exercised (a) by running it against an already-built ref with the env overrides the script provides, e.g. tag `v1.0.1` (JitPack API returns `"status" : "ok"` and `"modules" : [ core, keystore, providers ]` for it [VERIFIED: curl this session]) with `EXPECT_MODULES="voice-action-engine-core voice-action-engine-providers voice-action-engine-keystore" SKIP_CONSUMER=1` (the consumer probe's `:undoalone` project would fail at v1.0.1), and (b) for the real new-module path in P20 after the push. [ASSUMED: the v1.0.1 run is an adequate exercise of the edited manifest-classification code; it has not been run]. RT-02 must record that the live probe's undo/adapter path is owned by P20's pushed-SHA run, and surface this to the master as an Open Question rather than silently narrowing RT-02.

### Pitfall 12: sample unit tests and the `:stt` JitPack edge
Putting `:voice-adapter` + `:stt` on `:sample`'s TEST classpath only means the snippet compile needs `:stt` v0.7.0 from JitPack (already resolved/cached for `:voice-adapter:test`, `--offline` works once cached). An `implementation` edge would put the adapter in the APK for no benefit.

## Gate-1 leg design (VER-06)

| Leg (wire name) | Kind | Spends | Verdict | Evidence lines |
|---|---|---|---|---|
| `grammar_offline` | offline, tripwire provider, `offlineOnly` | 0 | PASS iff EN match + ES match `Completed` with 0 turns / 0 attempts / `tripwire_calls=0`, and near-miss = `Unhandled capped=true` | `VAE_TRACE` x3 (case 1..3), `VAE_OUTCOME`, `VAE_VERDICT` |
| `plan_live` | live, Anthropic `claude-haiku-4-5`, stateful store, `PlanThenExecuteStrategy` | <= 2 calls | PASS iff `Completed` not partial, 2 commits, child `parentId` == first id (store check) | `VAE_TURN`, `VAE_ATTEMPT`, `VAE_TRACE`, `VAE_OUTCOME`, `VAE_VERDICT`, `VAE_BUDGET` |
| `router_live` | live, Router + >=2 model tiers | router 1 + picked tier 1 (+ replan) | see Pattern 4 | + `sel_*` fields in `VAE_TRACE` |
| `undo_all` | offline scripted (DemoProvider) | 0 | PASS iff cases (a)(b)(c) behave per Pattern 5 | `VAE_UNDO` x(>=6), `VAE_VERDICT` |
| `responses_probe` (existing) | live, optional pool | 1 | PASS `reason=model_unsupported http=400` (D-13) | unchanged v1.0 shape |

Models: reuse `HAIKU = "claude-haiku-4-5"` and `GPT_MINI = "gpt-5.4-mini"` [VERIFIED: LegCatalog.kt:10-11]; P15's live probe already validated both models on `submit_plan` binding (4 requests, `ref_bound=true`, `literal_kept=true`) [VERIFIED: 15-LIVE-PROBE.md "Result"]. Use Haiku for plan and router (one provider, one key) unless a cost reason says otherwise. Each new live leg needs: a `LegId` entry, a `LegKind`, a `LegSpec` (prompts weakest-first, `reservation`, `needsKey`), a `run_<wire>`/`status_<wire>` tag pair (automatic via `UiTags.all`), and a verdict path in `LegRunner.judge`/`finish` that emits `VAE_VERDICT` (otherwise `capture-save` fails: it requires `VAE_VERDICT leg=<leg> ` [VERIFIED: run-sample-gate1.sh `do_capture_save`]).

UI for "Undo all (N)": add `undo_all` button and `undo_label` text (`Undo all (N)` from `journal.group(key)?.count`, pending shown apart) driven by the same glue an app would write, so the agentic tester presses the real control; the `VAE_UNDO phase=undone|refused` line is emitted from that handler. Cheaper fallback: run `undoAll` inside the leg and emit all lines (no UI tap); recommended only if UI time is short. [ASSUMED: UI tap is preferable because the criterion says "Undo all (N)"; judgment call for the planner].

## Plan-cache decision (D-03)

Recommendation: **defer explicitly** in `evidence/gate2-carry-register.txt` (the v1.0 carry-register shape: `C<n>` blocks with source / what phase does / disposition / evidence pointer) feeding the P20 waiver packet as a category-C row; never silent. Reasons (all verified): (1) the synthetic tool set cannot reach the Anthropic minimum cacheable prefix (`claude-haiku-4-5` needs 4,096 tokens per INTEGRATION.md Notes) so a synthetic leg can only ever show "not cached"; the number SB 177 wants needs SB-sized (~7k token) tools = the private A10 fixture; (2) a fixture-backed leg must extend LE-7: `fixtureBacked` is a one-liner but the host filter's `fixture_leak` is hard-wired to `leg=ver02` [VERIFIED: sample-evidence-filter.sh:25], the guard needs scenarios, `submit_plan` sits FIRST in the tools block so it will not share the agentic prefix cache (FEATURES.md note) and a cold two-call Anthropic run needs the 360 s warm-window discipline; (3) cost/complexity is not "cheap". If the master wants the number anyway it is a separate gap plan with its own GO.

## Docs gap inventory (what each file needs at the wiring SHA)

| File | Needs | Compiled region (new) |
|---|---|---|
| README.md | pin `v1.0.1` -> `v1.1.0` inside the `pin-version` markers (C24 requires `vX.Y.Z`; C23 is inert because `v1.0.0` exists); add `undo` coordinate line (+ `voice-adapter`) to the install block; one short "new tiers" paragraph (grammar, plan, router, undo-all, adapter) with links to INTEGRATION sections; update "Status" | `grammar-tier` OR keep README minimal and put new snippets in INTEGRATION (README must keep `minimal-pipeline`, C07) |
| INTEGRATION.md | step 2: add undo + voice-adapter coordinate lines to the kts block (C03 needs `:<version>`); NEW "grammar tier" subsection in step 5 (GrammarPack builder DSL, slots, `normalize`, ambiguity never guesses, `Extraction.matchedLanguage`); NEW "plan tier" subsection (submit_plan, binding, `needs_lookup`, hold = partial, `remainingStepIds`, how write tools declare returned keys); router subsection exists (add a compiled router snippet: `TierSelector.Router { tierDescriptions = ... }` plus mapping `start_tier_router` in the selection source); section 11 exists; section 12 update for RT-04; Notes unchanged | `grammar-tier`, `plan-tier`, `router-selector`, optional `undo-wiring` (journal + adapter + ticket use), optional `adapter-input` (`commandInputOf` only; a `FinalSegment` form needs `:stt` on the sample test classpath) |
| API.md | RT-01: one sentence in the PlanThenExecute reference-syntax bullet: the reference pattern's `\s`/`\S` is ASCII-only (Java regex default), so a near-reference with Unicode whitespace (for example NBSP) or a trailing newline is not a reference and is delivered to your executor as a literal string [VERIFIED: PlanBinding.kt:13 `Regex("[\$]($ID_FRAGMENT)\\.(\\S+)")`; 15-REVIEW IN-04]; RT-04: rows 214-215 (two overloads); `UndoResult.code` is public but undocumented (add to the undo section); nothing else missing | none required |
| ECOSYSTEM.md | status text (`v1.1 is planned`), the two "not yet published" sentences stay true until the tag but must not contradict the README pin; keep the repin-matrix block untouched (tooling-maintained) | none |

`DocSnippetsTest` already has regions: `scripted-provider, minimal-pipeline, register-providers, agentic-tier, gate-suspend, gate-defer, keystore-wiring, keystore-fake, fixed-credentials, render-outcome, clarification-follow-up, telemetry, undo-bridge` [VERIFIED: grep of `doc-snippet:start` in DocSnippetsTest.kt]. Rules (C06/C07): each region is a byte-equal copy after common-indent removal; every region must be used by some doc; a doc Kotlin fence needs a `<!-- doc-snippet: name -->` marker; imports go in a ```` ```text ```` block (not a kotlin fence); C21 forbids the words food/card and `*_note(s)/_card(s)/_food(s)/_meal(s)` tool names; C25 forbids `~/Projects`, `/home/x` paths. New regions go into `REQUIRED_REGIONS`.

## Coverage gate generalization (D-10)

Minimal set of changes to `scripts/verify-docs-coverage.sh`:
1. C01: `for a in $(vae_modules)`; also require the undo and voice-adapter coordinate in INTEGRATION; README must name every manifest coordinate (it names none for undo today).
2. C20: `public_types` loops the manifest modules; add a top-level-function pass (names only; for `:voice-adapter`: `toCommandInput`, `commandInputOf`, `normalizeSttLanguageLabel`).
3. C07: extend `REQUIRED_REGIONS` with the new names.
4. New checks (each a `need`): C26 grammar tier (`GrammarPack`, `LocalGrammarStrategy`, `matchedLanguage`, `normalize`, `grammar_ambiguous`, `cappedByPolicy`); C27 plan tier (`PlanThenExecuteStrategy`, `submit_plan`, `needs_lookup`, `remainingStepIds`, `plan_binding_unresolved`, `targetIds`); C28 router/picker (`TierSelector.Router`, `tierDescriptions`, `start_tier_router`, `router_fallback`, `PickContext`, `StartTierPicker`); C29 undo (`UndoJournal`, `undoAll`, `EntityAdapter`, `Compensator`, `UndoResult`, `UndoReason`, `Undo all (N)`, `compositeSink`, `heldRunId`); C30 adapter (`commandInputOf`, `toCommandInput`, `normalizeSttLanguageLabel`, `:stt`, `v0.7.0`, `compileOnly`-style "add :stt yourself" statement); C31 RT-01 (`ASCII`) and RT-04 (docs must NOT say "Three overloads" or list `toCommandInput(context)`); C32 seams from P12 (`onFailed`, `ReasoningMode`, `providerCallId`, `carryIn`).
5. A selftest (planted violation in a temp copy of the docs: drop `` `UndoGroup` `` from API.md and the undo coordinate from README; require C20 and C01 red) so the manifest generalization is proven non-vacuous.

## Wiring test redesign (D-11)

- Task prompt (`AGENT-PROMPT.md`, `{{VERSION}}` placeholder): `:jvmconsumer` wires a LocalGrammarStrategy (EN+ES, one tool) over a PlanThenExecuteStrategy and SingleShot ladder with `TierSelector.Router`, plus `UndoJournal` + an `EntityAdapter` + the bridge and one `undoAll`; `:app` wires keystore (unchanged). The agent's own tests use its own scripted `AiProvider` (as in v1.0) to assert: grammar completes with zero provider calls; plan binds the second step to the first id; router picks the second tier with a scripted router answer; undoAll restores the store. `voice-adapter` stays out of wiring (D-11).
- Judge: keep W1-W9 (W3 regex already accepts the five module names); add W10 `GrammarPack` and `LocalGrammarStrategy` in jvmconsumer main; W11 `PlanThenExecuteStrategy`; W12 `TierSelector.Router`; W13 `UndoJournal` + `undoAll` and a `voice-action-engine-undo:<version>` line; raise the W2 test floor; update the literal `checks=` and the planted-bad selftest (reference solution + a planted copy that fails W4/W5 and one of the new checks).
- Reference solution: relocate `reference/{Wire.kt,WireTest.kt,AppWire.kt}` from the archive to the stable dir and extend it; the judge `selftest` runs the dry run (heavy) so it belongs in the quiet window.
- Isolation re-confirmation: `claude --help` (v2.1.292) lists `-p/--print`, `--model`, `--no-session-persistence`, `--permission-mode` with `bypassPermissions`; v1.0.1's recipe was `claude -p --model sonnet --permission-mode bypassPermissions --no-session-persistence` with cwd = workspace and `CLAUDE_CONFIG_DIR` = a throwaway dir holding only `.credentials.json` (removed afterwards), prompt = `TASK.md` verbatim plus one working-directory line [CITED: 11-WIRING-RERUN.md "Isolation audit"]. The dispatch is the master's job (an executor has no Agent tool) [CITED: agent-wiring-test.sh header].
- Pass record: `.planning/releases/v1.1.0/` (stable) rather than a phase dir; any doc edit afterwards voids it (v1.0 rule); budget ONE rerun in the window.

## D-12 consolidated API review

`scripts/review-api-surface.sh` reviews ONLY `:core`'s dump (sealed allow-list of seven types, no `copy/componentN`, no enum, no public static field) [VERIFIED: its header: "It reviews core's dump; the other published modules' dumps are reviewed in their phase's SURFACE-REVIEW"]. `:undo` declares its own sealed `UndoResult` [VERIFIED: UndoResult.kt `public sealed class UndoResult`], which the core-only allow-list would reject if the script were pointed at it. Produce `19-API-REVIEW.md` from `scripts/api-dump-isolated.sh --out <dir>` (all five modules; expects the manifest package names, `voice-adapter` -> `voiceadapter`) plus the existing per-phase `*-SURFACE-REVIEW.md` open items (14: OI-1..OI-8; 15; 16: OI-1..OI-9; 17: OI-5 suppressions; 18: frozen names), and generalize `review-api-surface.sh` with a `--module <name>` flag whose allow-list for `undo` is `undo.UndoResult`. Run it AFTER RT-04 (the adapter surface changes) and BEFORE docs are finalized; a shape change after this review forces a docs + wiring rerun. P20 owns committing the final `api.txt` dumps and the new-module gate-12 branch (20-CONTEXT RT-02); do not create `api.txt` in P19 (gate 7 allows only `api.txt` and `.planning/` after the wiring SHA).

## Sequencing and checkpoints (D-09, gate 7, host memory)

Recommended plan order (one Gradle-running plan per wave, serial on a phase branch; all tests before the security gate):

1. **Tooling/paths (bash only):** `VAE_GATE1_DECISION_FILE` + derived default + `LEGS` + dirty-path list from manifest; guard scenarios; `agent-wiring-test.sh` asset dir + `prepare-local`; coverage-gate generalization incl. selftest. Gate: `bash -n`, `scripts/verify-sample-device-guard.sh`, `scripts/verify-docs-coverage.sh`, `scripts/verify-module-manifest.sh`, `scripts/verify-repo-hygiene.sh`.
2. **RT-04** (`:voice-adapter:check`, `:core:check` for the KDoc): overload drop + shape test + test updates.
3. **Evidence + legs in `:sample`** (closed vocabulary lockstep, grammar/plan/router/undo/tripwire/gate, UI, budget reservations): `:sample:testDebugUnitTest`.
4. **Doc regions in `DocSnippetsTest`** may land with 3 (they need the same Gradle run) but doc PROSE waits for step 6.
5. **Non-autonomous checkpoint A (master relays):** live-spend GO with ceilings + `19-LIVE-LEG-DECISION.md` (`decision: pending` -> `approved` only from a relayed answer, never self-authored) + TESTER window. ONE window: `preflight`, `build-install`, `push-keys`, in-app `import_test_keys`, `verify-keys-gone`, per-leg `capture-start` -> UI press -> `capture-save <leg>`, `verify-keys-gone`, `cleanup`. Plan/router/astra consume the GO; grammar/undo are free. Announce window open/close to the orchestrator ("device done tester").
6. **D-12 API review, then docs final** (README/INTEGRATION/API/ECOSYSTEM prose, RT-01, RT-04 docs, pin `v1.1.0`), full local gates green. This commit is the wiring SHA candidate: after it only `.planning/` and `api.txt` may change.
7. **Non-autonomous checkpoint B (quiet window, pre-granted terms; master asks, orchestrator holds the build lock):** RT-03(b) `verify-stt-confinement.sh --selftest` (bash only, can run any time), then in the window: RT-03(c) `verify-negative-controls.sh`, `:voice-adapter:check`, `verify-api-dump.sh`; RT-02 `jitpack-dry-run.sh` with the new `:adapteralone` project (RT-03a: add it to `jitpack-consumer-probe.sh`: a `kotlin.jvm` project depending on the adapter coordinate PLUS `:stt` v0.7.0, plus a negative assertion that a project WITHOUT the adapter does not resolve the `:stt` group; note the adapter is an AAR so use an Android app/library project like `:app`, not `kotlin.jvm`); wiring `selftest` + `prepare-local` + the isolated agent run + `verify`; live-probe exercise per Pitfall 11. Record everything in `19-QUIET-WINDOW.md` (copy the 17/18 shape: grant line, pre-check memory readings, verbatim results, window close).
8. **Close-out artifacts:** Gate-1 self-UAT/evidence index, `evidence/gate2-carry-register.txt`, `.planning/uat-pending/19-sample-gate-1-docs.md` (shape of `uat-pending/18-voice-adapter.md`: Status, Gate-1 log link, items covered, owner how-to-verify, Note), annotation that the P18 fragment's `:adapteralone` obligation is discharged with a pointer to `19-QUIET-WINDOW.md` (the P18 fragment itself is drained at milestone Gate-2 by `gsd-verify-milestone`, not a P19 task).

Gate-2 fragment content for P19's own behavior: nothing physical remains if the agentic tester drove every leg; list as human-optional: (a) read the Gate-1 evidence per criterion; (b) real-speech EN/ES grammar coverage (14-VERIFICATION D-12 caveat: recognizer fixtures are synthetic TTS, "Real-speech coverage belongs to Phase 19 Gate-1"; CONTEXT's D-05 scopes the leg to typed commands, so record it as an owner Gate-2 item, not a P19 task); (c) the deferred `submit_plan` cache-prefix number; (d) router wording quality judgement if the live leg PASSed with `picked_index` only.

## Code Examples

### Runner decision-file override (shape, not final)
```bash
# scripts/run-sample-gate1.sh (replace lines 41-43)
PHASE_DIR="${VAE_GATE1_PHASE_DIR:-.planning/phases/19-sample-gate-1-docs}"
_phase_name="${PHASE_DIR##*/}"
DECISION_FILE="${VAE_GATE1_DECISION_FILE:-$PHASE_DIR/${_phase_name%%-*}-LIVE-LEG-DECISION.md}"
EVIDENCE_DIR="${VAE_GATE1_EVIDENCE_DIR:-$PHASE_DIR/evidence}"
# push-keys check stays: grep -qx 'decision: approved' "$DECISION_FILE"
```
Source: derived from [VERIFIED: scripts/run-sample-gate1.sh:41-43,199]. Env names other than `VAE_GATE1_PHASE_DIR` are this research's proposal [ASSUMED].

### Router selector + mapping (the doc snippet to compile)
```kotlin
// INTEGRATION.md "Choosing where the model walk starts": opt-in engine router, two model tiers
val selector = TierSelector.Router {
    tierDescriptions = mapOf(
        StrategyId("single") to "One simple change to one item.",
        StrategyId("plan") to "Several dependent changes, for example create something and then add to it.",
    )
}
// builder: this.selector = selector
// selection source: map StrategyId("start_tier_router") to a small, fast model exactly like a tier id.
```
Source: API surface [VERIFIED: TierSelector.kt Router builder: `id`, `capabilities`, `tierDescriptions`; default id `start_tier_router` TierSelector.kt:5]. Final compiled version lives in `DocSnippetsTest` (imports `TierSelector`, `StrategyId`).

### Tripwire provider (sample main)
```kotlin
internal class TripwireProvider(override val id: ProviderId) : AiProvider {
    private val seen = java.util.concurrent.atomic.AtomicInteger()
    val calls: Int get() = seen.get()
    override suspend fun complete(call: ProviderRequest): ModelResult {
        seen.incrementAndGet()
        return ModelResult.Failure(FailureReason.Other("tripwire_called")) // loud, typed
    }
    override fun toString(): String = "TripwireProvider"
}
```
Source: `AiProvider` seam as implemented by `DemoProvider` [VERIFIED: legs/DemoProvider.kt] and `MyScriptedProvider` [VERIFIED: DocSnippetsTest scripted-provider region].

## State of the Art

| Old (v1.0) | Current (v1.1) | Impact |
|---|---|---|
| Gate-1 legs: fixture, smokes, multi-turn, probe, two demos | add grammar (offline), plan, router, undo-all; reuse probe for D-13 | new `LegId`s, two new evidence types |
| 3 published modules | 5 (`core providers keystore undo voice-adapter`) | every hard-coded list is a bug; `scripts/modules.list` is the source |
| wiring test = SingleShot + keystore | four surfaces + keystore | new prompt, reference, judge checks |
| README pin `v1.0.1` | `v1.1.0` at the wiring SHA (D-09), tag cut in P20 | docs name a not-yet-existing tag until P20 (C23 is inert: `v1.0.0` exists) |

**Deprecated/outdated in this repo:** P10 phase-dir paths in `agent-wiring-test.sh`; the P11 phase-dir `WIRING_RECORD`/`WAIVER_PACKET` in `release-cut.sh` (lines 79, 81: P20 `[tooling-paths]` owns them but they must be retargeted BEFORE the wiring SHA, so decide in the P19/P20 planning handshake which phase edits `release-cut.sh`).

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | `CLAUDE_CONFIG_DIR` throwaway-dir isolation still works as in the v1.0.1 run | Standard Stack / Wiring | the isolation audit would flag a CLAUDE.md leak; rerun with a different method |
| A2 | Relayed ceiling of <= 16 requests / USD 0.05 (expected ~5-6 requests, ~USD 0.01) is adequate | Pitfall 5 | master sets the real numbers; a too-low ceiling REFUSES a leg (loud), a too-high one costs cents |
| A3 | Running `jitpack-live-probe.sh` against tag v1.0.1 (3 modules, SKIP_CONSUMER) is an adequate pre-push exercise of the WR-08/IN-05 edits | Pitfall 11 | RT-02's live-probe half is only truly proven in P20; master may prefer a different arrangement |
| A4 | Quiet window needs >= 2.5 h | Pitfall 10 | too short -> a step is cut; ask more rather than less |
| A5 | UI-driven "Undo all (N)" tap is preferable to an in-leg `undoAll` call | Gate-1 leg design | extra UI work; the fallback still satisfies the evidence line |
| A6 | Router PASS rule (`sel=picked` + first attempted model tier equals the pick) is the right proof | Pattern 4 | a model that always picks tier 0 yields INCONCLUSIVE; planner may relax |
| A7 | Env names `VAE_GATE1_EVIDENCE_DIR`, `VAE_WIRING_ASSET_DIR` | Code Examples / Pitfall 7 | naming only |

## Open Questions

1. **Where does the live-probe half of RT-02 run, given no push before P20?**
   - Known: `jitpack-live-probe.sh` needs a built JitPack ref; `main` is unpushed (`0 457`); the five-module path is only provable after push.
   - Unclear: whether the master accepts the v1.0.1-ref exercise as RT-02's P19 half.
   - Recommendation: run the dry run (full five-module path, local maven) in P19, exercise the probe script on v1.0.1 with overrides, and carry "live probe on the pushed SHA" explicitly into P20's gate list. Ask the master before planning the quiet window.
2. **Who edits `release-cut.sh` (WIRING_RECORD/WAIVER_PACKET paths, lines 79/81) and when?** Gate 7 rejects `scripts/` changes after the wiring SHA, and P20 (after P19) owns the retarget per 20-CONTEXT D-03, which contradicts "land before the wiring SHA". Recommendation: P19's tooling plan retargets them (with `verify-release-manifest.sh` + selftest steps that read them) so P20 starts with consistent paths; confirm with the master.
3. **Lower the app request ceiling for this run?** Keep 33/34 (v1.0 approval constants) vs set to the relayed GO. Recommendation: keep constants, enforce by decision file + per-leg reservations + leg-only driving, and request the ceiling in the GO; lowering is cheap but touches several test strings.
4. **Router transcript choice and what "picked" must be** (index 1 vs any valid pick). Recommendation in Pattern 4; planner confirms.
5. **Adapter doc snippet:** `commandInputOf` only (no `:stt` on any classpath) vs also a `FinalSegment.toCommandInput()` snippet (needs `testImplementation(libs.stt.engine)` in `:sample`). Recommendation: compile both; the test edge is cheap and C06 then guards the exact signatures.
6. **Gate-2 real-speech grammar coverage** (P14 caveat) is outside CONTEXT D-05; record as a Gate-2 item unless the master wants a TESTER speech step.

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| TESTER R5CT10XNKQN | all device legs | yes (`adb devices` lists `R5CT10XNKQN device` and `100.118.21.106:1496 device`; read-only check, not driven) | SM-S908U, Android 15 | none (INFRA, never substitute) |
| JDK | Gradle | yes | OpenJDK 17.0.19 | — |
| `push-test-key`, `with-test-keys`, `test-keys-usage` | live legs | yes | `~/.local/bin` | missing key = loud stop, tell Yahir which file to create |
| `claude` CLI | isolated wiring agent | yes | 2.1.292 | — |
| `jq`, `flock`, `python3`, `curl` | scripts | yes | jq 1.7, util-linux 2.39.3, Python 3.12.3 | — |
| Android SDK | `:sample`/`:app` builds | yes (`~/Android/Sdk`: build-tools, cmake, cmdline-tools) | — | — |
| Host memory | all Gradle runs | tight: `MemAvailable` ~10.3 GB, swap 1/1 GiB used at research time; `earlyoom -m 15,8 -s 10,5` active | — | quiet window; stop at < 5 GiB |
| JitPack network | dry-run consumer probe, live probe, `:stt` v0.7.0 resolution | yes (curl to jitpack.io API succeeded) | — | `--offline` once cached |
| A10 fixture | NOT needed (D-03 deferred) | n/a | — | — |

**Missing dependencies with no fallback:** none found. TESTER availability must still be re-checked at window time (`common.md` protocol) and the personal phone is never touched.

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 4.13.2 + kotlinx-coroutines-test 1.11.0 (`:sample`, `:core`, `:voice-adapter`, `:undo`); bash gates for scripts |
| Config file | per-module `build.gradle.kts`; `config/detekt/detekt.yml`; scripts under `scripts/` |
| Quick run command | `GRADLE_OPTS="-Dorg.gradle.daemon=false -Dorg.gradle.workers.max=2 -Dorg.gradle.parallel=false -Dkotlin.compiler.execution.strategy=in-process -Dorg.gradle.jvmargs=-Xmx1536m" ./gradlew --offline -q -Dorg.gradle.workers.max=2 -Dorg.gradle.parallel=false :sample:testDebugUnitTest --tests '<pattern>'` [VERIFIED: the recipe used by 17-08/17-VALIDATION] |
| Full suite command | same flags: `./gradlew --offline :sample:testDebugUnitTest :voice-adapter:check :undo:check :core:check`, then `scripts/verify-sample-device-guard.sh`, `scripts/verify-docs-coverage.sh`, `scripts/verify-module-manifest.sh`, `scripts/verify-repo-hygiene.sh`, `scripts/verify-stt-confinement.sh --selftest` |

### Phase Requirements -> Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| VER-06 | grammar leg: EN/ES complete, near-miss `Unhandled capped=true`, 0 turns/attempts/tripwire calls | unit (JVM, fakes) | `:sample:testDebugUnitTest --tests '*GrammarLeg*'` | Wave 0 |
| VER-06 | plan leg binding + store state; verdict classifier | unit | `--tests '*PlanLeg*'` | Wave 0 |
| VER-06 | router leg: >=2 tiers, `sel=picked`, tokens in `trace.usage`; one-tier ladder FAILs | unit (ScriptedPicker/FakeAiProvider) | `--tests '*RouterLeg*'` | Wave 0 |
| VER-06 | undo-all cases (a) complete (b) refused (c) plan partial; N counts applied | unit | `--tests '*UndoLeg*' --tests '*UndoEndToEnd*'` | partial (17-08 tests exist) |
| VER-06 | new evidence types fit `ALLOW_PATTERN`, golden lockstep, no free text | unit + bash | `--tests '*EvidenceLine*'`, `scripts/verify-sample-device-guard.sh` | extend |
| VER-06 | decision file env/derivation, `consumed` refuses push-keys, LEGS parity | bash (fake adb) | `scripts/verify-sample-device-guard.sh` | extend |
| VER-06 | live TESTER evidence per leg + D-13 line | device (non-autonomous) | runner `capture-save` + `scripts/sample-evidence-filter.sh < file` | Wave 1 (checkpoint) |
| DOC-02 | every doc Kotlin block == compiled region | unit + bash | `:sample:testDebugUnitTest --tests '*DocSnippets*'`, `scripts/verify-docs-coverage.sh --only C06,C07` | extend |
| DOC-02 | five-module coordinate/type coverage, new-tier greps, RT-01/RT-04 wording | bash | `scripts/verify-docs-coverage.sh` (+ selftest) | extend |
| DOC-02 | RT-04 overloads gone | unit (reflection) | `:voice-adapter:test --tests '*AdapterApiShape*'` | extend |
| DOC-02 | fresh-agent wiring PASS | isolated agent + judge | `scripts/agent-wiring-test.sh selftest`, then `prepare-local` + `verify` | Wave 0 (rewrite) |
| RT-02/03 | dry run (5 artifacts, `:undoalone`, `:adapteralone`), negative controls, api-dump proof, `:voice-adapter:check` | heavy bash (quiet window) | `scripts/jitpack-dry-run.sh`, `scripts/verify-negative-controls.sh`, `scripts/verify-api-dump.sh` | checkpoint |

### Sampling Rate
- **Per task commit:** the one relevant Gradle test filter (flags above) or the one relevant bash gate; `bash -n` on every touched script.
- **Per wave merge:** `:sample:testDebugUnitTest` full + the four bash verifiers.
- **Phase gate:** full suite green before `/gsd-verify-work`; heavy gates only in the granted quiet window.

### Wave 0 Gaps
- [ ] `GrammarLegTest`, `PlanLegTest`, `RouterLegTest`, `UndoLegTest` (`sample/src/test/kotlin/.../sample/`) - VER-06
- [ ] `EvidenceLineTest` + golden file extensions; guard counts - VER-06
- [ ] guard scenarios for `VAE_GATE1_DECISION_FILE` / derived name / `consumed` - VER-06
- [ ] docs coverage selftest and new checks C26+ - DOC-02
- [ ] wiring assets relocation, new prompt/reference/judge checks, `prepare-local` - DOC-02
- [ ] `AdapterApiShapeTest` update + reflection proof for RT-04

## Security Domain

`security_enforcement` is enabled (absent/true in `.planning/config.json`, ASVS level 1, block on high).

### Applicable ASVS Categories
| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | no (no auth surface) | — |
| V3 Session Management | no | — |
| V4 Access Control | yes (device/key custody) | TESTER-only runner, identity proof, shared flock, `decision: approved` gate, `push-test-key` refusing the personal phone |
| V5 Input Validation | yes | evidence closed vocabulary (`ALLOW_PATTERN` + host `ALLOW_RE`), `invalid_token` for anything off-alphabet |
| V6 Cryptography | yes (keys) | `:keystore` AndroidKeyStore AES/GCM for the app key store (existing); never hand-roll; plaintext key files destroyed after import and proven gone |
| V8 Data Protection / V7 Logging | yes | keys, transcripts, tool args/results never in logs/evidence/`toString()`; new evidence fields are counts/codes/booleans |
| V14 Configuration | yes | no inline keys, no `.env`, evidence files `.txt`, no dumps/screenshots committed |

### Known Threat Patterns for this stack
| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Key or key-shaped string reaches an evidence file | Information disclosure | filter key-shape scan rejects the WHOLE capture; golden/guard scenarios plant `sk-` shapes (assembled from fragments, never literal) |
| Transcript/slot/plan-arg/tier-id in an evidence line | Information disclosure | no free-text field exists in the new line types; per-line tests with a canary string (`EvidenceLineTest.noFreeTextCanReachALine` pattern) |
| Spend overrun (mis-tap, retry storm) | Denial of service (wallet) | relayed ceiling, `RequestBudget` fail-closed reservations (worst case 3 per call), `decision: approved` before keys move, one install, no autorun first run |
| Wrong device (personal phone) | Tampering | `-s` on every adb call, identity proof by `ro.serialno`/model/sdk, wireless fallback only after proof |
| Plaintext key left on device/host | Information disclosure | `verify-keys-gone` positive proof before and after; `cleanup` uninstall proof; host probe dirs removed |
| Router `tierDescriptions` carrying secrets | Information disclosure | they are sent to the provider: synthetic words only (INTEGRATION.md already says so) |
| Heavy gate on a starved host | Denial of service | quiet window handshake, MemAvailable >= 5 GiB, one Gradle process, no `--stop` of foreign daemons |
| A doc edit after the wiring pass | Repudiation of the gate | gate 7 + rerun rule; keep docs final at the wiring SHA |

## Project Constraints (from CLAUDE.md)

Directives that bind the plans (from `~/.claude/CLAUDE.md` and `./.claude/CLAUDE.md`):
- Domain-free library and docs (C21 mechanizes it for the docs; `:sample` uses synthetic items).
- Secrets: keys/transcripts/tool args/results never reach logs, telemetry, exceptions or `toString()`.
- detekt zero baseline on library modules; no baseline XML; type-resolution tasks are not used; Metalava additive-only (`:voice-adapter` RT-04 is safe only because nothing is released; `api.txt` seeds are header-only until the P20 cut).
- Compat: `:core` has no HTTP/other-hub dependency; A1 OkHttp floor 4.12 (not touched by P19); do not add Hilt/DI/`android.util.Log` to libraries; `:sample` is never published and never in `jitpack.yml`.
- Process: contract changes only via the control plane; never commit §11; never run `/gsd-update`; tags are agent-owned under A12 but P19 creates none.
- Device rules: always `adb -s`; TESTER only; never the personal phone; never drive a device its owner is testing; read `~/.claude/context/devices/common.md` first; announce window open/close to the orchestrator.
- Never hand Yahir a bare `localhost` URL; documents for review go through `render-doc-for-review`.
- Host: one Gradle-running plan at a time; low-memory GRADLE_OPTS recipe; never `--stop` a foreign daemon; stop at MemAvailable < 5 GiB.
- Use GSD workflows for repo edits; a documentation artifact in `.planning` is written by the GSD agent roles.

## Sources

### Primary (HIGH confidence; all read this session)
- `.planning/phases/19-sample-gate-1-docs/19-CONTEXT.md`, `.planning/REQUIREMENTS.md`, `.planning/STATE.md`, `.planning/ROADMAP.md` (Phase 19, 12-18, 20), `.planning/v1.1-DECISION-MAP.md` (Phase 19, 20)
- `scripts/run-sample-gate1.sh`, `scripts/sample-evidence-filter.sh`, `scripts/verify-sample-device-guard.sh`, `scripts/verify-docs-coverage.sh`, `scripts/agent-wiring-test.sh`, `scripts/api-dump-isolated.sh`, `scripts/review-api-surface.sh`, `scripts/jitpack-dry-run.sh`, `scripts/jitpack-live-probe.sh`, `scripts/jitpack-consumer-probe.sh`, `scripts/release-cut.sh` (header, gates 6/7, paths), `scripts/lib/modules.sh`, `scripts/modules.list`, `scripts/verify-api-seed.sh`, `scripts/verify-stt-confinement.sh` (header), `scripts/verify-negative-controls.sh` (header)
- `sample/src/main/kotlin/**` (EvidenceLine, LegCatalog, LegRunner, DemoProvider, SampleEngine, AppGraph, SampleViewModel, RequestBudget, CostEstimate, UiTags, SyntheticTools, undo/*, ProviderFactory, DebugTools), `sample/src/test/**` (DocSnippetsTest regions, EvidenceLineTest, UndoTestRig, UndoEndToEndPlanTest, golden file), `sample/build.gradle.kts`
- `core/src/main/.../pipeline/TierSelector.kt`, `StartTierPicker.kt`, `telemetry/StartTierSelection.kt`, `commit/CommitSink.kt` (ActionEvent), `strategy/plan/PlanBinding.kt`, `strategy/grammar/GrammarPack.kt` (public DSL), `undo/src/main/**` (UndoResult, UndoReason, UndoGroup), `voice-adapter/src/**`, `voice-adapter/build.gradle.kts`, `settings.gradle.kts`, `jitpack.yml`, `gradle.properties`, root `build.gradle.kts`
- `README.md`, `API.md`, `INTEGRATION.md`, `ECOSYSTEM.md` (read in full or by section as cited)
- Phase artifacts: 12-08-PLAN/SUMMARY, 12-LIVE-LEG-DECISION, 12 evidence; 15-LIVE-PROBE, 15-SURFACE-REVIEW; 16-SURFACE-REVIEW (OI-1..OI-9); 17-QUIET-WINDOW, 17-10-SUMMARY, 17-VALIDATION, 17-VERIFICATION (deferred obligations); 18-QUIET-WINDOW, 18-REVIEW (WR-01/WR-04), 18-SURFACE-REVIEW, `uat-pending/12-*.md` and `18-voice-adapter.md`; 20-CONTEXT (RT-01..RT-06 and decisions); v1.0 archive: `10-WIRING-TEST.md`, `wiring-test/AGENT-PROMPT.md`, `11-WIRING-RERUN.md`, `GATE1-RUNBOOK.md`, `evidence/gate2-carry-register.txt`
- `~/.claude/context/workflows/test-keys.md`, `~/.claude/context/devices/common.md`, `~/.claude/context/workflows/two-gate-uat.md`
- Live checks this session: `claude --help` (v2.1.292), `adb devices` (read-only), `curl https://jitpack.io/api/builds/com.github.Ygaray/voice-action-engine/v1.0.1`, `git rev-list --left-right --count origin/main...HEAD`, `/proc/meminfo`, `free -g`, `which push-test-key with-test-keys test-keys-usage`

### Secondary / Tertiary
- None: no web research was needed; every finding is an in-repo or local-tool fact.

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH - nothing new; versions read from the catalog and build files.
- Architecture (legs, evidence, tooling changes): HIGH for anchors and lockstep points; MEDIUM for leg verdict rules (router PASS semantics, undo UI choice).
- Pitfalls: HIGH for the verified stale-path/vacuity/hard-coded-list findings; MEDIUM for quiet-window duration and the ceiling numbers (assumed, master decides).

**Research date:** 2026-10-06
**Valid until:** 2026-10-20 (the repo is moving fast: re-read `scripts/run-sample-gate1.sh`, `verify-docs-coverage.sh`, `agent-wiring-test.sh` and `modules.list` at plan time if any other phase lands scripts changes first)
